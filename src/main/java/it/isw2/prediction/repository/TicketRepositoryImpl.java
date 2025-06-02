package it.isw2.prediction.repository;

import it.isw2.prediction.dao.TicketDao;
import it.isw2.prediction.exception.ticket.TicketRetrievalException;
import it.isw2.prediction.factory.TicketDaoFactory;
import it.isw2.prediction.model.Ticket;

import java.util.List;
import java.util.logging.Logger;

/**
 * Implementazione concreta del repository per i ticket.
 * Utilizza un TicketDao per accedere ai dati.
 */
public class TicketRepositoryImpl implements TicketRepository {
    private static final Logger LOGGER = Logger.getLogger(TicketRepositoryImpl.class.getName());
    private final TicketDao ticketDao;

    /**
     * Costruttore che utilizza il DAO predefinito.
     */
    public TicketRepositoryImpl() {
        this.ticketDao = TicketDaoFactory.getInstance().getTicketDao();
    }

    /**
     * Costruttore che accetta un'istanza specifica di TicketDao.
     * Utile per i test o per personalizzazioni avanzate.
     *
     * @param ticketDao l'istanza di TicketDao da utilizzare
     */
    public TicketRepositoryImpl(TicketDao ticketDao) {
        this.ticketDao = ticketDao;
    }

    @Override
    public List<Ticket> retrieveTickets() throws TicketRetrievalException {
        LOGGER.info("Recupero di tutti i ticket dal repository");
        return ticketDao.retrieveTickets();
    }

    @Override
    public Ticket retrieveTicketById(int ticketId) throws TicketRetrievalException {
        LOGGER.info(()-> "Ricerca del ticket con ID: " + ticketId);
        return ticketDao.retrieveTickets().stream()
                .filter(ticket -> ticket.getId() == ticketId)
                .findFirst()
                .orElse(null);
    }

    @Override
    public Ticket retrieveTicketByKey(String key) throws TicketRetrievalException {
        LOGGER.info(()-> "Ricerca del ticket con chiave: " + key);
        return ticketDao.retrieveTickets().stream()
                .filter(ticket -> ticket.getKey().equals(key))
                .findFirst()
                .orElse(null);
    }
}
