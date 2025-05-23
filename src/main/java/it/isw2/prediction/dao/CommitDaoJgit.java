package it.isw2.prediction.dao;

import it.isw2.prediction.model.Commit;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CommitDaoJgit implements CommitDao {
    private static final Logger LOGGER = Logger.getLogger(CommitDaoJgit.class.getName());
    private static final String REPOSITORY_PATH = "projects/bookkeeper";

    @Override
    public List<Commit> retrieveCommits() {
        List<Commit> commits = new ArrayList<>();

        try {
            // Apro il repository Git
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            File gitDir = new File(REPOSITORY_PATH + "/.git");

            try (Repository repository = builder.setGitDir(gitDir)
                    .readEnvironment()
                    .findGitDir()
                    .build();
                 Git git = new Git(repository)) {

                // Recupero solo i commit sul branch master
                Iterable<RevCommit> revCommits = git.log()
                        .add(repository.resolve("refs/heads/master"))
                        .call();

                // Converto i RevCommit in oggetti Commit
                for (RevCommit revCommit : revCommits) {
                    Commit commit = new Commit(revCommit);
                    commits.add(commit);
                }

                LOGGER.info("Recuperati " + commits.size() + " commit dal branch master.");
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Errore nell'apertura del repository Git", e);
        } catch (GitAPIException e) {
            LOGGER.log(Level.SEVERE, "Errore nell'esecuzione del comando Git", e);
        }

        return commits;
    }
}
