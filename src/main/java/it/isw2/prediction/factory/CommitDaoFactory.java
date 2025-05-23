package it.isw2.prediction.factory;

import it.isw2.prediction.dao.CommitDao;
import it.isw2.prediction.dao.CommitDaoJgit;

public class CommitDaoFactory {

    private static CommitDaoFactory instance = null;

    public static CommitDaoFactory getInstance() {
        if (instance == null) instance = new CommitDaoFactory();
        return instance;
    }

    private CommitDaoFactory() {}

    public CommitDao getCommitDao() {
        return new CommitDaoJgit();
    }

}
