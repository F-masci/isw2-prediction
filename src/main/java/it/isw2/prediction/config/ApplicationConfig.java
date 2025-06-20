package it.isw2.prediction.config;

import it.isw2.prediction.Project;
import it.isw2.prediction.exception.ConfigException;

import java.io.InputStream;
import java.util.Properties;

public class ApplicationConfig {

    private static final String FILENAME = "application.properties";

    private final Properties properties = new Properties();

    public ApplicationConfig() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(FILENAME)) {
            if (input == null) throw new ConfigException("File di configurazione non trovato: " + FILENAME);
            properties.load(input);
        } catch (Exception _) {
            System.exit(1);
        }
    }

    private String get(String property) {
        return properties.getProperty(property);
    }

    public Project getSelectedProject() {
        return Project.getByKey(this.get("project"));
    }

    public String getDatasetPath() {
        return this.get("dataset.path");
    }

    public boolean isMethodCacheEnabled() {
        return Boolean.parseBoolean(this.get("method.cache"));
    }
    public boolean isMethodAllVersionEnabled() {
        return Boolean.parseBoolean(this.get("method.allVersion"));
    }

}
