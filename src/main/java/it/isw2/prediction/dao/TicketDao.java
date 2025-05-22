package it.isw2.prediction.dao;

import it.isw2.prediction.Project;
import it.isw2.prediction.exception.ticket.TicketRetrievalException;
import it.isw2.prediction.model.Ticket;

import java.util.List;

public interface TicketDao {

    /**
     * Ritorna tutti i ticket
     * @return Ticket associati
     * @throws TicketRetrievalException Se non è possibile cercare i ticket
     */
    List<Ticket> retrieveTickets() throws TicketRetrievalException;

    /**
     * Ritorna i ticket richiesti
     * @param startAt Indice del primo ticket da restituire
     * @param maxResults Numero massimo di ticket da restituire
     * @return Ticket associati
     * @throws TicketRetrievalException Se non è possibile cercare i ticket
     */
    List<Ticket> retrieveTickets(int startAt, int maxResults) throws TicketRetrievalException;

}
