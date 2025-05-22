package it.isw2.prediction.exception.version;

import it.isw2.prediction.exception.RetrievalException;

public class VersionRetrievalException extends RetrievalException {
    public VersionRetrievalException(String s) {
        super(s, null);
    }
    public VersionRetrievalException(String s, Exception e) {
        super(s, e);
    }
}