package it.isw2.prediction;

import it.isw2.prediction.dao.TicketDao;
import it.isw2.prediction.dao.VersionDao;
import it.isw2.prediction.exception.ticket.TicketRetrievalException;
import it.isw2.prediction.exception.version.VersionRetrievalException;
import it.isw2.prediction.factory.TicketDaoFactory;
import it.isw2.prediction.factory.VersionDaoFactory;
import it.isw2.prediction.model.Ticket;
import it.isw2.prediction.model.Version;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatasetCreation {

    private static final Logger logger = Logger.getLogger(DatasetCreation.class.getName());

    public static void main(String[] args) {

        try {

            // Recupero e stampa delle versioni
            logger.log(Level.INFO, "=== RECUPERO DELLE VERSIONI ===");
            retrieveAndLogVersions();

            // Recupero e stampa dei ticket
            logger.log(Level.INFO, "=== RECUPERO DEI TICKET ===");
            retrieveAndLogTickets();

            // Recupero e stampa dei ticket
            logger.log(Level.INFO, "=== DATI RECUPERATI ===");

        } catch(Exception e) {
            logger.log(Level.SEVERE, "Errore durante la creazione del dataset", e);
            System.exit(1);
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

