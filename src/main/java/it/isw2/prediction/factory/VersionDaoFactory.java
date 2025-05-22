package it.isw2.prediction.factory;

import it.isw2.prediction.Project;
import it.isw2.prediction.dao.VersionDao;
import it.isw2.prediction.dao.VersionDaoRest;

public class VersionDaoFactory {

    private static VersionDaoFactory instance = null;

    public static VersionDaoFactory getInstance() {
        if (instance == null) instance = new VersionDaoFactory();
        return instance;
    }

    private VersionDaoFactory() {}

    public VersionDao getVersionDao() {
        return new VersionDaoRest();
    }

}
