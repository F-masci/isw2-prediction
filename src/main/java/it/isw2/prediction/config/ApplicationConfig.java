package it.isw2.prediction.config;

import it.isw2.prediction.FeatureSelection;
import it.isw2.prediction.Project;
import it.isw2.prediction.exception.ConfigException;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
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
    public String getOutputPath() {
        return this.get("output.path");
    }

    public int getVersionsPercentage() {
        int perc = Integer.parseInt(this.get("prediction.versions.percentage"));
        return Math.clamp(perc, 0, 100);
    }

    public boolean isMethodCacheEnabled() {
        return Boolean.parseBoolean(this.get("method.cache"));
    }
    public boolean isMethodAllVersionEnabled() {
        return Boolean.parseBoolean(this.get("method.allVersion"));
    }

    public double getProportionWindowSize() {
        double size = Double.parseDouble(this.get("ticket.proportion.window.size"));
        return Math.clamp(size, 0, 1);
    }
    public int getStartProportionValue() {
        return Integer.parseInt(this.get("ticket.proportion.start.value"));
    }

    public int getRandomSeed() {
        return Integer.parseInt(this.get("random.seed"));
    }

    public int getValidationFolds() {
        return Math.max(2, Integer.parseInt(this.get("prediction.validation.crossfold")));
    }
    public int getValidationIterations() {
        return Math.max(1, Integer.parseInt(this.get("prediction.validation.iterations")));
    }
    public List<FeatureSelection> getValidationFeatureSelectionMethos() {
        String value = this.get("prediction.validation.feature.selection.method");
        return Arrays.stream(value.split(";"))
                .map(String::trim)
                .map(FeatureSelection::getByConfig)
                .filter(fs -> fs != null)
                .toList();
    }

    public double getValidationFeatureSelectionThreshold() {
        return Double.parseDouble(this.get("prediction.validation.feature.info-gain.threshold"));
    }
    public int getValidationFeatureSelectionFeatures() {
        return Integer.parseInt(this.get("prediction.validation.feature.info-gain.features"));
    }

    public FeatureSelection getInferenceFeatureSelectionMethod() {
        return FeatureSelection.getByConfig(this.get("prediction.inference.feature.selection.method"));
    }
    public String getInferenceClassifier() {
        return this.get("prediction.inference.classifier");
    }
    public String getInferenceActionableFeature() {
        return this.get("prediction.inference.actionable.feature");
    }

}
