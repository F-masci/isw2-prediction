package it.isw2.prediction.repository;

import it.isw2.prediction.dao.CommitDao;
import it.isw2.prediction.factory.CommitDaoFactory;
import it.isw2.prediction.model.Commit;

import java.util.List;
import java.util.logging.Logger;

/**
 * Implementazione concreta del repository per i commit.
 * Utilizza un CommitDao per accedere ai dati.
 */
public class CommitRepositoryImpl implements CommitRepository {
    private static final Logger LOGGER = Logger.getLogger(CommitRepositoryImpl.class.getName());
    private final CommitDao commitDao;

    /**
     * Costruttore che utilizza il DAO predefinito.
     */
    public CommitRepositoryImpl() {
        this.commitDao = CommitDaoFactory.getInstance().getCommitDao();
    }

    /**
     * Costruttore che accetta un'istanza specifica di CommitDao.
     * Utile per i test o per personalizzazioni avanzate.
     *
     * @param commitDao l'istanza di CommitDao da utilizzare
     */
    public CommitRepositoryImpl(CommitDao commitDao) {
        this.commitDao = commitDao;
    }

    @Override
    public List<Commit> retrieveCommits() {
        LOGGER.info("Recupero di tutti i commit dal repository");
        return commitDao.retrieveCommits();
    }

    @Override
    public Commit retrieveCommitById(String commitId) {
        LOGGER.info(() -> "Ricerca del commit con ID: " + commitId);
        return commitDao.retrieveCommits().stream()
                .filter(commit -> commit.getId().equals(commitId))
                .findFirst()
                .orElse(null);
    }

    @Override
    public Commit retriveLastCommitOfBranch(String branchName) {
        LOGGER.info(() -> "Recupero dell'ultimo commit del branch: " + branchName);
        return commitDao.retriveLastCommitOfBranch(branchName);
    }

}
