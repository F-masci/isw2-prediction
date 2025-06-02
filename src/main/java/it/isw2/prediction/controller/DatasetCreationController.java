package it.isw2.prediction.controller;

import it.isw2.prediction.Project;
import it.isw2.prediction.config.ApplicationConfig;
import it.isw2.prediction.factory.MethodRepositoryFactory;
import it.isw2.prediction.model.Method;
import it.isw2.prediction.model.Version;
import it.isw2.prediction.repository.MethodRepository;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatasetCreationController {

    private static final Logger LOGGER = Logger.getLogger(DatasetCreationController.class.getName());
    private static final String SEPARATOR = ";";

    private static final String CSV_HEADER = "Project;Package;Class;Method;Version;LOC;Cyclomatic;Cognitive;MethodHistories;AddedLines;DeletedLines;Churn;BranchPoints;NestingDepth;ParametersCount;Buggy";
    private final List<String> csvRecords = new ArrayList<>();

    public void createDataset() {
        try {
            MethodRepository methodRepository = MethodRepositoryFactory.getInstance().getMethodRepository();
            List<Method> methods = methodRepository.retrieveMethods();

            for(Method method : methods) {
                List<Version> versions = method.getVersions();
                for (Version version : versions) {
                    String csvRecord = getProjectName() + SEPARATOR +
                            method.getPackageName() + SEPARATOR +
                            method.getClassName() + SEPARATOR +
                            method.getMethodName() + SEPARATOR +
                            version.getName() + SEPARATOR +
                            method.getLOC(version) + SEPARATOR +
                            method.getCyclomaticComplexity(version) + SEPARATOR +
                            method.getCognitiveComplexity(version) + SEPARATOR +
                            method.getMethodHistories(version) + SEPARATOR +
                            method.getAddedLines(version) + SEPARATOR +
                            method.getDeletedLines(version) + SEPARATOR +
                            method.getChurn(version) + SEPARATOR +
                            method.getBranchPoints(version) + SEPARATOR +
                            method.getNestingDepth(version) + SEPARATOR +
                            method.getParametersCount(version) + SEPARATOR +
                            method.isBuggy(version);

                    // Aggiungere il record alla lista
                    csvRecords.add(csvRecord);
                }
            }

            // Scrivere il file CSV
            writeCSVFile();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Errore durante la creazione del dataset", e);
            System.exit(1);
        }

    }

    private void writeCSVFile() {

        String projectName = getProjectName();

        // Creare la directory dataset/{projectName} se non esiste
        ApplicationConfig config = new ApplicationConfig();
        String csvFilePath = Paths.get(config.getDatasetPath(), projectName + ".csv").toString();
        try {
            Files.createDirectories(Paths.get(config.getDatasetPath()));
        } catch (IOException _) {
            LOGGER.log(Level.SEVERE, () -> "Impossibile creare la directory: " + csvFilePath);
            System.exit(1);
        }

        try (FileWriter csvWriter = new FileWriter(csvFilePath)) {
            // Intestazione CSV
            csvWriter.append(CSV_HEADER + "\n");
            // Scrivere i dati per ogni metodo
            for (String csvRecord : csvRecords) csvWriter.append(csvRecord + "\n");

            LOGGER.log(Level.INFO, "File CSV creato con successo: {0}", csvFilePath);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e, () -> "Errore durante la creazione del file CSV: " + csvFilePath);
        }
    }

    private String getProjectName() {
        // Utilizzo ApplicationConfig per ottenere il progetto selezionato
        ApplicationConfig config = new ApplicationConfig();
        Project project = config.getSelectedProject();
        if (project == null) {
            LOGGER.log(Level.WARNING, "Progetto non trovato nella configurazione. Usando default: UNKNOWN");
            return "UNKNOWN";
        }
        return project.getKey();
    }
}
