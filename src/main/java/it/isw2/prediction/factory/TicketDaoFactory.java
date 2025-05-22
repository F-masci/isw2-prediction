package it.isw2.prediction.factory;

import it.isw2.prediction.dao.TicketDao;
import it.isw2.prediction.dao.TicketDaoRest;

public class TicketDaoFactory {

    private static TicketDaoFactory instance = null;

    public static TicketDaoFactory getInstance() {
        if (instance == null) instance = new TicketDaoFactory();
        return instance;
    }

    private TicketDaoFactory() {}

    public TicketDao getTicketDao() {
        return new TicketDaoRest();
    }

}
