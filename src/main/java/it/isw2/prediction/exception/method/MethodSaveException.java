package it.isw2.prediction.exception.method;

import it.isw2.prediction.exception.RetrievalException;

public class MethodSaveException extends Exception {
    public MethodSaveException(String s) {
        super(s, null);
    }
    public MethodSaveException(String s, Exception e) {
        super(s, e);
    }
}