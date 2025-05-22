package it.isw2.prediction.exception.ticket;

import it.isw2.prediction.exception.RetrievalException;

public class TicketRetrievalException extends RetrievalException {
    public TicketRetrievalException(String s) {
        super(s, null);
    }
    public TicketRetrievalException(String s, Exception e) {
        super(s, e);
    }
}
