package it.isw2.prediction.repository;

import it.isw2.prediction.dao.MethodDao;
import it.isw2.prediction.dao.MethodDaoFile;
import it.isw2.prediction.exception.method.MethodRetrievalException;
import it.isw2.prediction.exception.method.MethodSaveException;
import it.isw2.prediction.factory.MethodDaoFactory;
import it.isw2.prediction.model.Commit;
import it.isw2.prediction.model.Method;

import java.util.List;

public class MethodRepositoryImpl implements MethodRepository {

    private final MethodDao methodDao;
    private final MethodDaoFile fileMethodDao;

    /**
     * Costruttore che utilizza il DAO predefinito.
     */
    public MethodRepositoryImpl() {
        this.methodDao = MethodDaoFactory.getInstance().getMethodDao();
        this.fileMethodDao = MethodDaoFactory.getInstance().getFileMethodDao();
    }

    /**
     * Costruttore che accetta un'istanza specifica di MethodDao.
     * Utile per i test o per personalizzazioni avanzate.
     *
     * @param methodDao l'istanza di MethodDao da utilizzare
     */
    public MethodRepositoryImpl(MethodDao methodDao) {
        this.methodDao = methodDao;
        this.fileMethodDao = MethodDaoFactory.getInstance().getFileMethodDao();
    }

    @Override
    public List<Method> retrieveMethods() throws MethodRetrievalException, MethodSaveException {
        try {
            return fileMethodDao.retrieveMethods();
        } catch(MethodRetrievalException _) {
            List<Method> result = methodDao.retrieveMethods();
            fileMethodDao.saveMethods(result);
            return result;
        }
    }

    @Override
    public Method retrieveMethodByFullName(String fullName) throws MethodRetrievalException, MethodSaveException {
        return this.retrieveMethods().stream()
                .filter(ticket -> ticket.getFullName().equals(fullName))
                .findFirst()
                .orElse(null);
    }

}
