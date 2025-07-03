package it.isw2.prediction.exception.version;

public class VersionFilteringException extends RuntimeException {
    public VersionFilteringException(String message) {
        this(message, null);
    }
    public VersionFilteringException(String message, Exception e) {
        super(message, e);
    }
}
