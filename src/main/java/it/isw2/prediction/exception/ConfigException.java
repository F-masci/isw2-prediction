package it.isw2.prediction.exception;

public class ConfigException extends Exception {
    public ConfigException(String s) {
        super(s, null);
    }
    public ConfigException(String s, Exception e) {
        super(s, e);
    }
}
