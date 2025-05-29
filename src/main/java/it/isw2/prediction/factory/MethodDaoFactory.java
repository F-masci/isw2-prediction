package it.isw2.prediction.factory;

import it.isw2.prediction.dao.MethodDao;
import it.isw2.prediction.dao.MethodDaoFile;
import it.isw2.prediction.dao.MethodDaoJgit;

public class MethodDaoFactory {

    private static MethodDaoFactory instance = null;

    public static MethodDaoFactory getInstance() {
        if (instance == null) instance = new MethodDaoFactory();
        return instance;
    }

    private MethodDaoFactory() {}

    public MethodDao getMethodDao() {
        return new MethodDaoJgit();
    }
    public MethodDaoFile getFileMethodDao() {
        return new MethodDaoFile();
    }

}
