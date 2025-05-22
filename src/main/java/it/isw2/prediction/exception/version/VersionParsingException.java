package it.isw2.prediction.exception.version;

public class VersionParsingException extends RuntimeException {
    public VersionParsingException(String message) {
        this(message, null);
    }
    public VersionParsingException(String message, Exception e) {
        super(message, e);
    }
}
