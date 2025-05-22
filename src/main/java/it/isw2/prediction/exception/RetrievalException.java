package it.isw2.prediction.exception;

public class RetrievalException extends Exception {
    public RetrievalException(String s) {
        super(s, null);
    }
    public RetrievalException(String s, Exception e) {
        super(s, e);
    }
}
