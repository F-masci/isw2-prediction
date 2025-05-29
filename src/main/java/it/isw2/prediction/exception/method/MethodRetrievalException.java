package it.isw2.prediction.exception.method;

import it.isw2.prediction.exception.RetrievalException;

public class MethodRetrievalException extends RetrievalException {
    public MethodRetrievalException(String s) {
        super(s, null);
    }
    public MethodRetrievalException(String s, Exception e) {
        super(s, e);
    }
}