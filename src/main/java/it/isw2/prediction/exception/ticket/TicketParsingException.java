package it.isw2.prediction.exception.ticket;

public class TicketParsingException extends RuntimeException {
    public TicketParsingException(String message) {
        this(message, null);
    }
    public TicketParsingException(String message, Exception e) {
        super(message, e);
    }
}
