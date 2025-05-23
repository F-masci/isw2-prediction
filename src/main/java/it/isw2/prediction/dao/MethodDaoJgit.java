package it.isw2.prediction.dao;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import it.isw2.prediction.config.ApplicationConfig;
import it.isw2.prediction.config.GitApiConfig;
import it.isw2.prediction.factory.MethodRepositoryFactory;
import it.isw2.prediction.factory.VersionRepositoryFactory;
import it.isw2.prediction.model.Commit;
import it.isw2.prediction.model.Method;
import it.isw2.prediction.model.Version;
import it.isw2.prediction.repository.MethodRepository;
import it.isw2.prediction.repository.VersionRepository;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.AbstractMap;

public class MethodDaoJgit implements MethodDao {

    private static final Logger LOGGER = Logger.getLogger(MethodDaoJgit.class.getName());
    private static final Pattern METHOD_PATTERN = Pattern.compile(
            "(?:public|protected|private|static|\\s)\\s+[\\w\\<\\>\\[\\]]+\\s+(\\w+)\\s*\\([^\\)]*\\)\\s*\\{");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("package\\s+([\\w\\.]+)\\s*;");
    private static final Pattern CLASS_PATTERN = Pattern.compile("(?:public|private|protected|\\s)\\s+(?:class|interface|enum)\\s+(\\w+)");

    @Override
    public List<Method> retrieveMethods() {
        HashMap<String, Method> methods = new HashMap<>();

        try {

            // Recupero l'ultimo commit per ogni versione
            VersionRepository versionRepository = VersionRepositoryFactory.getInstance().getVersionRepository();
            List<Version> versions = versionRepository.retrieveVersions();

            List<Commit> lastCommits = new ArrayList<>();
            for(Version version : versions) {
                Commit lastCommit = version.getLastCommit();
                if(lastCommit != null) lastCommits.add(version.getLastCommit());
            }

            Set<Version> uniqueVersion = new HashSet<>();
            for (Commit commit : lastCommits) {
                Version version = commit.getVersion();
                if (version != null) uniqueVersion.add(version);
            }

            List<Version> difference = new ArrayList<>();
            for (Version version : versions) {
                if (!uniqueVersion.contains(version)) {
                    difference.add(version);
                }
            }

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
                for (Commit commit : lastCommits) addMethodsFromCommit(repository, methods, commit);

                LOGGER.log(Level.INFO, "Recuperati {0} metodi unici dal progetto.", methods.size());
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Errore nell'apertura del repository Git", e);
        }

        return new ArrayList<>(methods.values());
    }

    @Override
    public List<Method> retrieveModifiedMethods(Commit commit) {
        List<Method> modifiedMethods = new ArrayList<>();
        MethodRepository methodRepository = MethodRepositoryFactory.getInstance().getMethodRepository();

        RevCommit parent = commit.getParent();
        if(parent == null) return modifiedMethods;

        // Apro il repository Git
        ApplicationConfig appConfig = new ApplicationConfig();
        String repoPath = GitApiConfig.getProjectsPath(appConfig.getSelectedProject());
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        File gitDir = new File(repoPath + "/.git");

        try (Repository repository = builder.setGitDir(gitDir)
                .readEnvironment()
                .findGitDir()
                .build();
             Git git = new Git(repository)) {

            ObjectReader reader = repository.newObjectReader();
            CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
            oldTreeIter.reset(reader, parent.getTree());
            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
            newTreeIter.reset(reader, commit.getTree());

            List<DiffEntry> diffs = git.diff()
                    .setOldTree(oldTreeIter)
                    .setNewTree(newTreeIter)
                    .call();

            DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
            df.setRepository(repository);
            df.setContext(0);

            for (DiffEntry diff : diffs) {
                if (!diff.getNewPath().endsWith(".java")) continue;

                // Gestisco i diversi tipi di modifica (ADD, MODIFY, DELETE, RENAME, COPY)
                if (diff.getChangeType() == DiffEntry.ChangeType.DELETE) {
                    // Ignoro i file eliminati
                    continue;
                }

                FileHeader fileHeader = df.toFileHeader(diff);
                EditList edits = fileHeader.toEditList();

                // Leggo il codice vecchio e nuovo
                String oldCode = "";
                if (diff.getChangeType() != DiffEntry.ChangeType.ADD) {
                    oldCode = readBlobAsString(repository, diff.getOldId().toObjectId());
                }

                String newCode = readBlobAsString(repository, diff.getNewId().toObjectId());
                if (newCode.isEmpty()) continue;

                // Parsing del codice con JavaParser
                JavaParser parser = new JavaParser();

                CompilationUnit oldCu = null;
                if (!oldCode.isEmpty()) {
                    try {
                        oldCu = parser.parse(oldCode)
                                .getResult().orElse(null);
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Errore nel parsing del file {0} (versione precedente): {1}",
                                new Object[]{diff.getOldPath(), e.getMessage()});
                        continue;
                    }
                }

                CompilationUnit newCu;
                try {
                    newCu = parser.parse(newCode)
                            .getResult().orElse(null);

                    if (newCu == null) {
                        LOGGER.log(Level.WARNING, "Parsing fallito per il file {0}", diff.getNewPath());
                        continue;
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Errore nel parsing del file {0}: {1}",
                            new Object[]{diff.getNewPath(), e.getMessage()});
                    continue;
                }

                // Estraggo informazioni di package e classe
                String packageName = null;
                if (newCu.getPackageDeclaration().isPresent()) {
                    packageName = newCu.getPackageDeclaration().get().getNameAsString();
                }

                // Raccolgo tutte le classi nel file
                List<String> classNames = new ArrayList<>();
                newCu.findAll(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class).forEach(
                        c -> classNames.add(c.getNameAsString())
                );

                if (packageName == null || classNames.isEmpty()) {
                    LOGGER.log(Level.WARNING, "Non posso determinare package o classe per {0}", diff.getNewPath());
                    continue;
                }

                // Raccolgo i metodi vecchi e nuovi
                List<MethodDeclaration> oldMethods = new ArrayList<>();
                if (oldCu != null) {
                    oldMethods = oldCu.findAll(MethodDeclaration.class);
                }

                List<MethodDeclaration> newMethods = newCu.findAll(MethodDeclaration.class);

                // Per ogni metodo nuovo, verifico se è stato modificato
                for (MethodDeclaration newMethod : newMethods) {
                    String methodName = newMethod.getNameAsString();

                    // Cerco il metodo corrispondente nel vecchio codice
                    Optional<MethodDeclaration> oldOpt = oldMethods.stream()
                            .filter(m -> m.getNameAsString().equals(methodName) &&
                                        m.getParameters().size() == newMethod.getParameters().size())
                            .findFirst();

                    boolean changed = oldOpt.isEmpty() || !Objects.equals(
                            oldOpt.get().getBody().map(Object::toString).orElse(""),
                            newMethod.getBody().map(Object::toString).orElse("")
                    );

                    if (changed) {
                        // Per ogni classe nel file, provo a trovare il metodo
                        for (String className : classNames) {
                            Method method = methodRepository.retrieveMethodByFullName(methodName);

                            // Se non esiste, lo creo al volo
                            if (method == null) {
                                method = new Method(className, packageName, methodName);
                            }

                            if (!modifiedMethods.contains(method)) {
                                modifiedMethods.add(method);
                                LOGGER.log(Level.FINE, "Metodo modificato: {0}.{1}.{2}",
                                        new Object[]{packageName, className, methodName});
                            }
                        }
                    }
                }
            }
        } catch(GitAPIException | IOException e){
            LOGGER.log(Level.SEVERE, "Errore nell'analisi del commit: {0}", e.getMessage());
        }

        return modifiedMethods;
    }

    public void addMethodsFromCommit(Repository repository, Map<String, Method> methods, Commit commit) throws IOException {
        RevTree tree = commit.getTree();

        try (TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);
            treeWalk.setFilter(PathSuffixFilter.create(".java"));

            while (treeWalk.next()) {
                String path = treeWalk.getPathString();
                ObjectId objectId = treeWalk.getObjectId(0);
                ObjectReader reader = repository.newObjectReader();

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                reader.open(objectId).copyTo(outputStream);
                String content = outputStream.toString();

                String packageName = extractPackageName(content);

                // La mappa è <className#methodName, loc>
                Map<String, Integer> methodsWithLoc = extractMethodsWithLOC(content);

                for (Map.Entry<String, Integer> entry : methodsWithLoc.entrySet()) {
                    String key = entry.getKey(); // className#methodName
                    int loc = entry.getValue();

                    String[] parts = key.split("#", 2);
                    String className = parts[0];
                    String methodName = parts[1];

                    String uniqueKey = packageName + "." + className + "#" + methodName;
                    Method method;
                    if (!methods.containsKey(uniqueKey))
                        methods.put(uniqueKey, new Method(className, packageName, methodName));
                    method = methods.get(uniqueKey);
                    method.addVersion(commit.getVersion(), loc);
                }
            }
        }
    }

    /**
     * Estrae i nomi dei metodi associati alla rispettiva classe e conta le LOC effettive (senza commenti né righe vuote),
     * includendo le righe della firma e la riga di chiusura della parentesi graffa se presenti.
     * @param content Contenuto del file
     * @return Mappa <className#methodName, loc>
     */
    private Map<String, Integer> extractMethodsWithLOC(String content) {
        Map<String, Integer> methodsWithLoc = new HashMap<>();

        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(content).getResult().orElse(null);

            if (cu == null) {
                LOGGER.log(Level.WARNING, "Parsing fallito per il contenuto fornito");
                return methodsWithLoc;
            }

            String[] lines = content.split("\\r?\\n");

            cu.findAll(MethodDeclaration.class).forEach(method -> {
                String methodName = method.getNameAsString();
                String className = method.findAncestor(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class)
                        .map(c -> c.getNameAsString())
                        .orElse("UnknownClass");

                int loc = 0;
                if (method.getRange().isPresent()) {
                    int begin = method.getRange().get().begin.line;
                    int end = method.getRange().get().end.line;
                    if (begin > 0 && end <= lines.length) {
                        boolean inBlockComment = false;
                        for (int i = begin - 1; i < end; i++) {
                            String trimmed = lines[i].trim();
                            if (trimmed.isEmpty()) continue;
                            if (trimmed.startsWith("/*")) inBlockComment = true;
                            if (!inBlockComment && !trimmed.startsWith("//")) loc++;
                            if (inBlockComment && trimmed.endsWith("*/")) inBlockComment = false;
                        }
                    }
                }
                String key = className + "#" + methodName;
                methodsWithLoc.put(key, loc);
            });
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Errore nell'estrazione dei metodi: {0}", e.getMessage());
        }
        return methodsWithLoc;
    }

    private String extractPackageName(String content) {
        Matcher matcher = PACKAGE_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String extractClassName(String content) {
        Matcher matcher = CLASS_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private List<String> extractMethodNames(String content) {
        List<String> methodNames = new ArrayList<>();
        Matcher matcher = METHOD_PATTERN.matcher(content);

        while (matcher.find()) {
            methodNames.add(matcher.group(1));
        }

        return methodNames;
    }

    private String readBlobAsString(Repository repo, ObjectId blobId) throws IOException {
        if (blobId == null || blobId.equals(ObjectId.zeroId())) return "";
        try (ObjectReader reader = repo.newObjectReader()) {
            ObjectLoader loader = reader.open(blobId);
            return new String(loader.getBytes(), "UTF-8");
        }
    }

}
