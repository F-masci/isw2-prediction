package it.isw2.prediction;

import it.isw2.prediction.dao.TicketDao;
import it.isw2.prediction.dao.VersionDao;
import it.isw2.prediction.exception.ticket.TicketRetrievalException;
import it.isw2.prediction.exception.version.VersionRetrievalException;
import it.isw2.prediction.factory.CommitRepositoryFactory;
import it.isw2.prediction.factory.TicketDaoFactory;
import it.isw2.prediction.factory.VersionDaoFactory;
import it.isw2.prediction.model.Commit;
import it.isw2.prediction.model.Ticket;
import it.isw2.prediction.model.Version;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import it.isw2.prediction.repository.CommitRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;

public class DatasetCreation {

    private static final Logger logger = Logger.getLogger(DatasetCreation.class.getName());

    public static void main(String[] args) {

        try {

            /*// Recupero e stampa delle versioni
            logger.log(Level.INFO, "=== RECUPERO DELLE VERSIONI ===");
            retrieveAndLogVersions();

            // Recupero e stampa dei ticket
            logger.log(Level.INFO, "=== RECUPERO DEI TICKET ===");
            retrieveAndLogTickets();*/

            /*String repoPath = "projects/bookkeeper";
            String commitSha = "0816a3c";

            File repoDir = new File(repoPath);
            try (Repository repository = new FileRepositoryBuilder()
                    .setGitDir(new File(repoDir, ".git"))
                    .build()) {

                try (Git git = new Git(repository)) {
                    RevWalk revWalk = new RevWalk(repository);

                    ObjectId commitId = repository.resolve(commitSha);
                    if (commitId == null) {
                        throw new IllegalArgumentException("Commit SHA non trovato: " + commitSha);
                    }

                    RevCommit commit = revWalk.parseCommit(commitId);
                    if (commit == null) {
                        throw new IllegalArgumentException("Commit non valido: " + commitSha);
                    }

                    if (commit.getParentCount() == 0) {
                        throw new IllegalArgumentException("Il commit non ha genitori: impossibile fare diff");
                    }

                    RevCommit parent = revWalk.parseCommit(commit.getParent(0).getId());

                    if (parent == null) {
                        System.out.println("Commit has no parent.");
                        return;
                    }

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

                        FileHeader fileHeader = df.toFileHeader(diff);
                        EditList edits = fileHeader.toEditList();

                        String oldCode = readBlobAsString(repository, diff.getOldId().toObjectId());
                        String newCode = readBlobAsString(repository, diff.getNewId().toObjectId());

                        JavaParser parser = new JavaParser();

                        CompilationUnit oldCu = parser.parse(oldCode)
                                .getResult().orElseThrow(() -> new RuntimeException("Errore nel parsing oldCode"));

                        CompilationUnit newCu = parser.parse(newCode)
                                .getResult().orElseThrow(() -> new RuntimeException("Errore nel parsing newCode"));

                        List<MethodDeclaration> oldMethods = oldCu.findAll(MethodDeclaration.class);
                        List<MethodDeclaration> newMethods = newCu.findAll(MethodDeclaration.class);

                        System.out.println("==> File: " + diff.getNewPath());
                        for (MethodDeclaration newMethod : newMethods) {
                            Optional<MethodDeclaration> oldOpt = oldMethods.stream()
                                    .filter(m -> m.getSignature().equals(newMethod.getSignature()))
                                    .findFirst();

                            boolean changed = oldOpt.isEmpty() || !Objects.equals(
                                    oldOpt.get().getBody().map(Object::toString).orElse(""),
                                    newMethod.getBody().map(Object::toString).orElse("")
                            );

                            if (changed) {
                                System.out.println("   -> Metodo modificato: " + newMethod.getSignature());
                            }
                        }
                    }
                }
            }*/

            CommitRepository commitRepository = CommitRepositoryFactory.getInstance().getCommitRepository();
            List<Commit> commits = commitRepository.retrieveCommits();
            commits = commits.subList(0, 10); // Limitiamo a 10 commit per la stampa

            for (Commit commit : commits) {
                logger.log(Level.INFO, "Commit ID: {0}", commit.getId());
                logger.log(Level.INFO, "Commit Message: {0}", commit.getMessage());
                logger.log(Level.INFO, "Commit Author: {0}", commit.getAuthorName());
                logger.log(Level.INFO, "Commit Date: {0}", commit.getDate());
            }

            // Recupero e stampa dei ticket
            logger.log(Level.INFO, "=== DATI RECUPERATI ===");

        } catch(Exception e) {
            logger.log(Level.SEVERE, "Errore durante la creazione del dataset", e);
            System.exit(1);
        }
    }

    private static String readBlobAsString(Repository repo, ObjectId blobId) throws IOException {
        if (blobId == null || blobId.equals(ObjectId.zeroId())) return "";
        try (ObjectReader reader = repo.newObjectReader()) {
            ObjectLoader loader = reader.open(blobId);
            return new String(loader.getBytes(), "UTF-8");
        }
    }

    private static void retrieveAndLogTickets() throws TicketRetrievalException {
        TicketDaoFactory daoFactory = TicketDaoFactory.getInstance();
        TicketDao dao = daoFactory.getTicketDao();
        List<Ticket> tickets = dao.retrieveTickets();
        for (Ticket t : tickets) logger.log(Level.INFO, "{0}", t);
        logger.log(Level.INFO,"=== END TICKETS ===");
        logger.log(Level.INFO,"Totale ticket: {0}", tickets.size());
    }

    private static void retrieveAndLogVersions() throws VersionRetrievalException {
        VersionDaoFactory daoFactory = VersionDaoFactory.getInstance();
        VersionDao dao = daoFactory.getVersionDao();
        List<Version> versions = dao.retrieveVersions();
        for (Version v : versions) logger.log(Level.INFO, "{0}", v);
        logger.log(Level.INFO,"=== END VERSIONS ===");
        logger.log(Level.INFO,"Totale versioni: {0}", versions.size());
    }
}

