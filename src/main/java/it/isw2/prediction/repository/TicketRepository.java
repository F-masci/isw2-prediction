package it.isw2.prediction.repository;

import it.isw2.prediction.exception.ticket.TicketRetrievalException;
import it.isw2.prediction.model.Ticket;

import java.util.List;

/**
 * Interfaccia per il Repository dei ticket.
 * Definisce le operazioni di base per accedere ai ticket.
 */
public interface TicketRepository {

    /**
     * Recupera tutti i ticket.
     *
     * @return lista di tutti i ticket
     */
    List<Ticket> retrieveTickets() throws TicketRetrievalException;

    /**
     * Recupera un ticket specifico per ID.
     *
     * @param ticketId l'ID del ticket da recuperare
     * @return il ticket trovato o null se non esiste
     */
    Ticket retrieveTicketById(int ticketId) throws TicketRetrievalException;

    /**
     * Recupera un ticket specifico per chiave.
     *
     * @param key la chiave del ticket da recuperare
     * @return il ticket trovato o null se non esiste
     */
    Ticket retrieveTicketByKey(String key) throws TicketRetrievalException;

}
