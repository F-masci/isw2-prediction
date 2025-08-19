package it.isw2.prediction.dao;

import it.isw2.prediction.config.ApplicationConfig;
import it.isw2.prediction.config.GitApiConfig;
import it.isw2.prediction.factory.CommitFactory;
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

    @Override
    public List<Commit> retrieveCommits() {
        List<Commit> commits = new ArrayList<>();

        try {
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

                // Recupero solo i commit sul branch master
                Iterable<RevCommit> revCommits = git.log()
                        .add(repository.resolve("refs/heads/master"))
                        .call();

                CommitFactory commitFactory = CommitFactory.getInstance();

                // Converto i RevCommit in oggetti Commit
                for (RevCommit revCommit : revCommits) {
                    Commit commit = commitFactory.createCommit(revCommit);
                    if(commit.getVersion() != null) commits.add(commit);
                }

                LOGGER.log(Level.FINE, "Recuperati {0} commit dal branch master.", commits.size());
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Errore nell'apertura del repository Git", e);
        } catch (GitAPIException e) {
            LOGGER.log(Level.SEVERE, "Errore nell'esecuzione del comando Git", e);
        }

        return commits;
    }

    @Override
    public Commit retriveLastCommitOfBranch(String branchName) {
        try {
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

                // Recupero solo i commit sul branch master
                Iterable<RevCommit> revCommits = git.log()
                        .add(repository.resolve(branchName))
                        .call();

                CommitFactory commitFactory = CommitFactory.getInstance();

                // Converto i RevCommit in oggetti Commit
                return commitFactory.createCommit(revCommits.iterator().next());

            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Errore nell'apertura del repository Git", e);
        } catch (GitAPIException e) {
            LOGGER.log(Level.SEVERE, "Errore nell'esecuzione del comando Git", e);
        }
        return null;
    }

}
