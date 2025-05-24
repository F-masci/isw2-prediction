package it.isw2.prediction.repository;

import it.isw2.prediction.exception.ticket.TicketRetrievalException;
import it.isw2.prediction.model.Ticket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Implementazione del pattern Decorator per aggiungere funzionalità di cache
 * al TicketRepository. Questa classe decora un TicketRepository esistente
 * aggiungendo la cache senza modificare la sua implementazione originale.
 */
public class CachedTicketRepository implements TicketRepository {
    private static final Logger LOGGER = Logger.getLogger(CachedTicketRepository.class.getName());

    // Componente decorato
    private final TicketRepository repository;

    // Cache per i ticket
    private Map<Integer, Ticket> ticketCache = null;

    /**
     * Costruttore del decorator.
     *
     * @param repository il TicketRepository da decorare
     */
    public CachedTicketRepository(TicketRepository repository) {
        this.repository = repository;
    }

    /**
     * Sovrascrive il metodo originale aggiungendo la funzionalità di cache.
     */
    @Override
    public List<Ticket> retrieveTickets() throws TicketRetrievalException {
        loadTicketsCache();
        return new ArrayList<>(ticketCache.values());
    }

    /**
     * Sovrascrive il metodo originale aggiungendo la funzionalità di cache.
     */
    @Override
    public Ticket retrieveTicketById(int ticketId) throws TicketRetrievalException {
        loadTicketsCache();
        return ticketCache.get(ticketId);
    }

    /**
     * Sovrascrive il metodo originale aggiungendo la funzionalità di cache.
     */
    @Override
    public Ticket retrieveTicketByKey(String key) throws TicketRetrievalException {
        loadTicketsCache();
        return ticketCache.values().stream()
                .filter(ticket -> ticket.getKey().equals(key))
                .findFirst()
                .orElse(null);
    }

    /**
     * Carica la cache dei ticket se non è già stata caricata.
     * Se la cache è già presente, non fa nulla.
     */
    private void loadTicketsCache() throws TicketRetrievalException {
        if (ticketCache == null) {
            LOGGER.info("Cache dei ticket non inizializzata, creazione della cache");
            ticketCache = new HashMap<>();

            LOGGER.info("Caricamento della cache dei ticket");
            List<Ticket> tickets = repository.retrieveTickets();

            for (Ticket ticket : tickets) {
                try {
                    ticketCache.put(ticket.getId(), ticket);
                } catch (NullPointerException e) {
                    LOGGER.warning("Errore durante il caricamento del ticket");
                }
            }

            LOGGER.info(() -> "Cache dei ticket caricata con " + ticketCache.size() + " tickets");
        }
    }

    /**
     * Metodo aggiuntivo specifico del decorator.
     * Invalida la cache, forzando il repository a ricaricare i dati alla prossima richiesta.
     */
    public void invalidateCache() {
        LOGGER.info("Invalidazione della cache dei ticket");
        ticketCache = null;
    }
}
