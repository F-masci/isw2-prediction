package it.isw2.prediction.dao;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import it.isw2.prediction.config.ApplicationConfig;
import it.isw2.prediction.config.GitApiConfig;
import it.isw2.prediction.exception.ticket.TicketRetrievalException;
import it.isw2.prediction.factory.CommitRepositoryFactory;
import it.isw2.prediction.factory.VersionRepositoryFactory;
import it.isw2.prediction.model.Commit;
import it.isw2.prediction.model.Method;
import it.isw2.prediction.model.Version;
import it.isw2.prediction.repository.CommitRepository;
import it.isw2.prediction.utils.Utils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
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

public class MethodDaoJgit implements MethodDao {

    private static final Logger LOGGER = Logger.getLogger(MethodDaoJgit.class.getName());

    @Override
    public List<Method> retrieveMethods() {
        HashMap<String, Method> methods = new HashMap<>();

        try {

            // Recupero l'ultimo commit per ogni versione
            CommitRepository commitRepository = CommitRepositoryFactory.getInstance().getCommitRepository();
            List<Commit> commits = commitRepository.retrieveCommits();

            commits = commits.stream()
                    .sorted(Comparator.comparing(Commit::getDate))
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
                for (Commit commit : commits) {
                    LOGGER.log(Level.INFO, "Analizzo il commit {0} ({1})", new Object[]{commit.getId(), commit.getDate()});
                    addMethodsFromCommit(repository, methods, commit);
                }

                LOGGER.log(Level.INFO, "Recuperati {0} metodi unici dal progetto.", methods.size());
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Errore nell'apertura del repository Git", e);
        }

        ApplicationConfig appConfig = new ApplicationConfig();
        if(appConfig.isMethodAllVersionEnabled()) {
            // Se l'opzione per tutte le versioni di metodo è abilitata, aggiungo l'ultima versione rilasciata alle versioni dei metodi non eliminati
            Version lastVersion = VersionRepositoryFactory.getInstance()
                    .getVersionRepository()
                    .retrieveLastReleasedVersion();

            methods.values().forEach(m -> {
                // Se il metodo non è stato eliminato, aggiungo la versione corrente come versione del metodo
                if (m.getDeleteCommit() == null) m.addVersion(lastVersion);
            });
        }

        return new ArrayList<>(methods.values());
    }

    /**
     * Aggiunge i metodi presenti in un commit al repository dei metodi.
     *
     * @param repository il repository Git
     * @param methods    la mappa dei metodi da aggiornare
     * @param commit     il commit corrente da analizzare
     * @throws IOException se si verifica un errore durante l'accesso al repository
     */
    private void addMethodsFromCommit(Repository repository, Map<String, Method> methods, Commit commit) throws IOException {
        RevCommit parent = commit.getParent();
        if (parent == null) return;

        try (Git git = new Git(repository)) {
            List<DiffEntry> diffs = computeDiffs(repository, git, parent, commit);

            diffs.stream()
                    .filter(diff -> !isTestOrNonJavaFile(diff))
                    .map(diff -> createDiffContext(repository, diff))
                    .filter(Objects::nonNull)
                    .forEach(ctx -> {
                        try {
                            processDiffContext(ctx, methods, commit, repository);
                        } catch (TicketRetrievalException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e, () -> "Errore nell'analisi del commit: " + e.getMessage());
        }
    }

    private DiffContext createDiffContext(Repository repository, DiffEntry diff) {
        try {
            String oldCode = getCode(repository, diff.getOldId(), diff.getChangeType() != DiffEntry.ChangeType.ADD);
            String newCode = getCode(repository, diff.getNewId(), diff.getChangeType() != DiffEntry.ChangeType.DELETE);

            if (diff.getChangeType() != DiffEntry.ChangeType.DELETE && newCode.isEmpty()) return null;

            CompilationUnit oldCu = tryParse(diff.getOldPath(), oldCode, true);
            CompilationUnit newCu = diff.getChangeType() == DiffEntry.ChangeType.DELETE ? null : tryParse(diff.getNewPath(), newCode, false);
            if (diff.getChangeType() != DiffEntry.ChangeType.DELETE && newCu == null) return null;

            String packageName = extractPackageName(newCu, oldCu);
            if (packageName == null) return null;

            return new DiffContext(diff, oldCu, newCu, packageName, oldCode, newCode);
        } catch (Exception _) {
            return null;
        }
    }

    private void processDiffContext(DiffContext ctx, Map<String, Method> methods, Commit commit, Repository repository) throws TicketRetrievalException {
        List<MethodDeclaration> oldMethods = ctx.oldCu != null ? ctx.oldCu.findAll(MethodDeclaration.class) : new ArrayList<>();
        List<MethodDeclaration> newMethods = ctx.newCu != null ? ctx.newCu.findAll(MethodDeclaration.class) : new ArrayList<>();

        if (ctx.diff.getChangeType() == DiffEntry.ChangeType.DELETE) {
            processDeletedMethods(oldMethods, methods, ctx.packageName, commit, ctx.diff, repository);
        } else {
            processNewOrChangedMethods(newMethods, oldMethods, methods, ctx.packageName, commit, ctx.diff, repository);
        }
    }

    /**
     * Calcola le differenze tra due commit utilizzando JGit.
     *
     * @param repository il repository Git
     * @param git        l'istanza di Git
     * @param parent     il commit genitore
     * @param commit     il commit corrente
     * @return la lista delle differenze tra i due commit
     * @throws IOException se si verifica un errore durante l'accesso al repository
     * @throws GitAPIException se si verifica un errore durante l'esecuzione del comando diff
     */
    private List<DiffEntry> computeDiffs(Repository repository, Git git, RevCommit parent, Commit commit) throws IOException, GitAPIException {
        ObjectReader reader = repository.newObjectReader();

        CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
        oldTreeIter.reset(reader, parent.getTree());

        CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
        newTreeIter.reset(reader, commit.getTree());

        return git.diff()
                .setOldTree(oldTreeIter)
                .setNewTree(newTreeIter)
                .call();
    }

    /**
     * Verifica se il file è un test o non è un file Java.
     *
     * @param diff l'oggetto DiffEntry che rappresenta la modifica
     * @return true se il file è un test o non è un file Java, false altrimenti
     */
    private boolean isTestOrNonJavaFile(DiffEntry diff) {
        boolean isDeleted = diff.getChangeType() == DiffEntry.ChangeType.DELETE;
        String path = isDeleted ? diff.getOldPath() : diff.getNewPath();
        return !path.endsWith(".java") || path.contains("/test/");
    }

    /**
     * Recupera il codice sorgente da un file nel repository Git.
     *
     * @param repo il repository Git
     * @param id   l'ID abbreviato dell'oggetto (blob)
     * @param shouldRead indica se leggere il blob o meno
     * @return il contenuto del blob come stringa, o una stringa vuota in caso di errore
     */
    private String getCode(Repository repo, AbbreviatedObjectId id, boolean shouldRead) {
        if (!shouldRead || id == null) return "";
        try {
            return Utils.readBlobAsString(repo, id.toObjectId());
        } catch (Exception _) {
            return "";
        }
    }

    /**
     * Tenta di analizzare il codice sorgente in un CompilationUnit.
     *
     * @param path il percorso del file
     * @param code il codice sorgente da analizzare
     * @param isOld indica se si tratta della versione precedente del file
     * @return il CompilationUnit risultante, o null in caso di errore
     */
    private CompilationUnit tryParse(String path, String code, boolean isOld) {
        if (code.isEmpty()) return null;
        try {
            JavaParser parser = new JavaParser();
            return parser.parse(code).getResult().orElse(null);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Errore nel parsing del file {0} {1}: {2}",
                    new Object[]{path, isOld ? "(versione precedente)" : "", e.getMessage()});
            return null;
        }
    }

    /**
     * Estrae il nome del package da una CompilationUnit, preferendo la nuova versione se disponibile.
     *
     * @param newCu la CompilationUnit della nuova versione del file
     * @param oldCu la CompilationUnit della vecchia versione del file
     * @return il nome del package come stringa, o null se non presente
     */
    private String extractPackageName(CompilationUnit newCu, CompilationUnit oldCu) {
        if (newCu != null) {
            Optional<PackageDeclaration> pkg = newCu.getPackageDeclaration();
            if (pkg.isPresent()) {
                return pkg.get().getNameAsString();
            }
        }

        if (oldCu != null) {
            Optional<PackageDeclaration> pkg = oldCu.getPackageDeclaration();
            if (pkg.isPresent()) {
                return pkg.get().getNameAsString();
            }
        }

        return null;
    }

    /**
     * Processa i metodi eliminati rispetto alla versione precedente.
     *
     * @param oldMethods  la lista dei metodi nella versione precedente
     * @param methods     la mappa dei metodi da aggiornare
     * @param packageName il nome del package del file
     * @param commit      il commit corrente
     * @param diff        l'oggetto DiffEntry che rappresenta la modifica
     * @param repo        il repository Git
     */
    private void processDeletedMethods(List<MethodDeclaration> oldMethods, Map<String, Method> methods,
                                       String packageName, Commit commit, DiffEntry diff, Repository repo) throws TicketRetrievalException {
        for (MethodDeclaration oldMethod : oldMethods) {
            String methodName = oldMethod.getNameAsString();
            String className = oldMethod.findAncestor(ClassOrInterfaceDeclaration.class)
                    .map(ClassOrInterfaceDeclaration::getNameAsString)
                    .orElse("UnknownClass");

            // Costruisce una chiave unica per il metodo
            String key = packageName + "." + className + "#" + methodName;

            // Aggiunge il metodo alla mappa se non presente
            methods.computeIfAbsent(key, k -> new Method(className, packageName, methodName));
            Method method = methods.get(key);

            // Registra il fatto che il metodo è stato rimosso (newMethod = null)
            method.parseMethodDeclaration(commit, null);
            method.parseDiffEntry(repo, commit, diff);
        }
    }

    /**
     * Processa i metodi nuovi o modificati rispetto alla versione precedente.
     *
     * @param newMethods   la lista dei metodi nella nuova versione
     * @param oldMethods   la lista dei metodi nella versione precedente
     * @param methods      la mappa dei metodi da aggiornare
     * @param packageName  il nome del package del file
     * @param commit       il commit corrente
     * @param diff         l'oggetto DiffEntry che rappresenta la modifica
     * @param repo         il repository Git
     */
    private void processNewOrChangedMethods(List<MethodDeclaration> newMethods, List<MethodDeclaration> oldMethods,
                                            Map<String, Method> methods, String packageName,
                                            Commit commit, DiffEntry diff, Repository repo) throws TicketRetrievalException {
        for (MethodDeclaration newMethod : newMethods) {
            String methodName = newMethod.getNameAsString();
            String className = newMethod.findAncestor(ClassOrInterfaceDeclaration.class)
                    .map(ClassOrInterfaceDeclaration::getNameAsString)
                    .orElse("UnknownClass");

            // Costruisce una chiave unica per il metodo
            String key = packageName + "." + className + "#" + methodName;

            // Cerca un metodo con lo stesso nome e numero di parametri nella versione precedente
            Optional<MethodDeclaration> oldOpt = oldMethods.stream()
                    .filter(m -> m.getNameAsString().equals(methodName)
                            && m.getParameters().size() == newMethod.getParameters().size())
                    .findFirst();

            // Confronta il corpo del metodo per vedere se è cambiato
            boolean changed = oldOpt.isEmpty() || !Objects.equals(
                    oldOpt.get().getBody().map(Object::toString).orElse(""),
                    newMethod.getBody().map(Object::toString).orElse("")
            );

            if (changed) {
                // Aggiunge o aggiorna il metodo modificato
                methods.computeIfAbsent(key, k -> new Method(className, packageName, methodName));
                Method method = methods.get(key);

                method.parseMethodDeclaration(commit, newMethod);
                method.parseDiffEntry(repo, commit, diff);
            }
        }
    }



    private static class DiffContext {
        DiffEntry diff;
        CompilationUnit oldCu;
        CompilationUnit newCu;
        String packageName;
        String oldCode;
        String newCode;

        DiffContext(DiffEntry diff, CompilationUnit oldCu, CompilationUnit newCu, String packageName, String oldCode, String newCode) {
            this.diff = diff;
            this.oldCu = oldCu;
            this.newCu = newCu;
            this.packageName = packageName;
            this.oldCode = oldCode;
            this.newCode = newCode;
        }
    }

}