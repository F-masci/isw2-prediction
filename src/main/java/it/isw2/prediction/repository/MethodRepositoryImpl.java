package it.isw2.prediction.repository;

import it.isw2.prediction.dao.MethodDao;
import it.isw2.prediction.dao.MethodDaoJgit;
import it.isw2.prediction.dao.TicketDao;
import it.isw2.prediction.exception.ticket.TicketRetrievalException;
import it.isw2.prediction.factory.MethodDaoFactory;
import it.isw2.prediction.factory.TicketDaoFactory;
import it.isw2.prediction.model.Commit;
import it.isw2.prediction.model.Method;
import it.isw2.prediction.model.Ticket;

import java.util.List;

public class MethodRepositoryImpl implements MethodRepository {

    private final MethodDao methodDao;

    /**
     * Costruttore che utilizza il DAO predefinito.
     */
    public MethodRepositoryImpl() {
        this.methodDao = MethodDaoFactory.getInstance().getMethodDao();
    }

    /**
     * Costruttore che accetta un'istanza specifica di MethodDao.
     * Utile per i test o per personalizzazioni avanzate.
     *
     * @param methodDao l'istanza di MethodDao da utilizzare
     */
    public MethodRepositoryImpl(MethodDao methodDao) {
        this.methodDao = methodDao;
    }

    @Override
    public List<Method> retrieveMethods() {
        return methodDao.retrieveMethods();
    }

    @Override
    public Method retrieveMethodByFullName(String fullName) {
        return methodDao.retrieveMethods().stream()
                .filter(ticket -> ticket.getFullName().equals(fullName))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<Method> retrieveModifiedMethods(Commit commit) {
        return methodDao.retrieveModifiedMethods(commit);
    }

}
