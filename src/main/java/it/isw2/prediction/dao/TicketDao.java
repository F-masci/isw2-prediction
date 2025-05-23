package it.isw2.prediction.dao;

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

}
