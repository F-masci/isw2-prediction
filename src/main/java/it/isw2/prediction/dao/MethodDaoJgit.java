package it.isw2.prediction.dao;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import it.isw2.prediction.config.ApplicationConfig;
import it.isw2.prediction.config.GitApiConfig;
import it.isw2.prediction.factory.CommitRepositoryFactory;
import it.isw2.prediction.model.Commit;
import it.isw2.prediction.model.Method;
import it.isw2.prediction.repository.CommitRepository;
import it.isw2.prediction.utils.Utils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class MethodDaoJgit implements MethodDao {

    private static final Logger LOGGER = Logger.getLogger(MethodDaoJgit.class.getName());

    @Override
    public List<Method> retrieveMethods() {
        HashMap<String, Method> methods = new HashMap<>();

        try {

            // Recupero l'ultimo commit per ogni versione
            CommitRepository commitRepository = CommitRepositoryFactory.getInstance().getCommitRepository();
            List<Commit> commits = commitRepository.retrieveCommits();

            // FIXME: per ora prendo solo un piccolo campione di commit
            commits = commits.stream()
                    .sorted(Comparator.comparing(Commit::getDate))
                    // .limit(100)
                    .toList();

            // Apro il repository Git
            ApplicationConfig appConfig = new ApplicationConfig();
            String repoPath = GitApiConfig.getProjectsPath(appConfig.getSelectedProject());
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            File gitDir = new File(repoPath + "/.git");

            try (Repository repository = builder.setGitDir(gitDir)
                    .readEnvironment()
                    .findGitDir()
                    .build()) {

                // Per ogni commit, recupero i file Java ed estraggo i metodi
                for (Commit commit : commits) addMethodsFromCommit(repository, methods, commit);

                LOGGER.log(Level.INFO, "Recuperati {0} metodi unici dal progetto.", methods.size());
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Errore nell'apertura del repository Git", e);
        }

        return new ArrayList<>(methods.values());
    }

    private void addMethodsFromCommit(Repository repository, Map<String, Method> methods, Commit commit) throws IOException {
        RevCommit parent = commit.getParent();
        if (parent == null) return; // Se non c'è parent, non posso confrontare

        try (Git git = new Git(repository)) {
            ObjectReader reader = repository.newObjectReader();
            CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
            oldTreeIter.reset(reader, parent.getTree());
            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
            newTreeIter.reset(reader, commit.getTree());

            List<DiffEntry> diffs = git.diff()
                    .setOldTree(oldTreeIter)
                    .setNewTree(newTreeIter)
                    .call();

            for (DiffEntry diff : diffs) {
                boolean isDeleted = diff.getChangeType() == DiffEntry.ChangeType.DELETE;
                if (!diff.getNewPath().endsWith(".java") && !isDeleted) continue;
                if ((diff.getNewPath().contains("/test/") || (isDeleted && diff.getOldPath().contains("/test/")))) continue;

                String oldCode = "";
                if (diff.getChangeType() != DiffEntry.ChangeType.ADD) {
                    oldCode = Utils.readBlobAsString(repository, diff.getOldId().toObjectId());
                }
                String newCode = isDeleted ? "" : Utils.readBlobAsString(repository, diff.getNewId().toObjectId());
                if (!isDeleted && newCode.isEmpty()) continue;

                JavaParser parser = new JavaParser();
                CompilationUnit oldCu = null;
                if (!oldCode.isEmpty()) {
                    try {
                        oldCu = parser.parse(oldCode).getResult().orElse(null);
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Errore nel parsing del file {0} (versione precedente): {1}",
                                new Object[]{diff.getOldPath(), e.getMessage()});
                        continue;
                    }
                }
                CompilationUnit newCu = null;
                if (!isDeleted) {
                    try {
                        newCu = parser.parse(newCode).getResult().orElse(null);
                        if (newCu == null) {
                            LOGGER.log(Level.WARNING, "Parsing fallito per il file {0}", diff.getNewPath());
                            continue;
                        }
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Errore nel parsing del file {0}: {1}",
                                new Object[]{diff.getNewPath(), e.getMessage()});
                        continue;
                    }
                }

                String packageName = null;
                if (!isDeleted && newCu.getPackageDeclaration().isPresent()) {
                    packageName = newCu.getPackageDeclaration().get().getNameAsString();
                } else if (isDeleted && oldCu != null && oldCu.getPackageDeclaration().isPresent()) {
                    packageName = oldCu.getPackageDeclaration().get().getNameAsString();
                }
                if (packageName == null) continue;

                List<MethodDeclaration> oldMethods = oldCu != null ? oldCu.findAll(MethodDeclaration.class) : new ArrayList<>();
                List<MethodDeclaration> newMethods = !isDeleted && newCu != null ? newCu.findAll(MethodDeclaration.class) : new ArrayList<>();

                // Processa i metodi cancellati
                if (isDeleted) {
                    for (MethodDeclaration oldMethod : oldMethods) {
                        String methodName = oldMethod.getNameAsString();
                        String className = oldMethod.findAncestor(ClassOrInterfaceDeclaration.class)
                                .map(c -> c.getNameAsString())
                                .orElse("UnknownClass");
                        String uniqueKey = packageName + "." + className + "#" + methodName;
                        Method method;
                        if (!methods.containsKey(uniqueKey))
                            methods.put(uniqueKey, new Method(className, packageName, methodName));
                        method = methods.get(uniqueKey);
                        method.parseMethodDeclaration(commit, oldMethod);
                        method.parseDiffEntry(repository, commit, diff);
                    }
                }

                for (MethodDeclaration newMethod : newMethods) {
                    String methodName = newMethod.getNameAsString();
                    String className = newMethod.findAncestor(ClassOrInterfaceDeclaration.class)
                            .map(c -> c.getNameAsString())
                            .orElse("UnknownClass");
                    String uniqueKey = packageName + "." + className + "#" + methodName;

                    // Cerca il metodo corrispondente nel vecchio codice
                    Optional<MethodDeclaration> oldOpt = oldMethods.stream()
                            .filter(m -> m.getNameAsString().equals(methodName)
                                    && m.getParameters().size() == newMethod.getParameters().size())
                            .findFirst();

                    boolean changed = oldOpt.isEmpty() || !Objects.equals(
                            oldOpt.get().getBody().map(Object::toString).orElse(""),
                            newMethod.getBody().map(Object::toString).orElse("")
                    );

                    if (changed) {
                        Method method;
                        if (!methods.containsKey(uniqueKey))
                            methods.put(uniqueKey, new Method(className, packageName, methodName));
                        method = methods.get(uniqueKey);
                        method.parseMethodDeclaration(commit, newMethod);
                        method.parseDiffEntry(repository, commit, diff);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, () -> "Errore nell'analisi del commit: " + e.getMessage());
        }
    }

}