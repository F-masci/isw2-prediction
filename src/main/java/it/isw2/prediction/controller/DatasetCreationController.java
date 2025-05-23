package it.isw2.prediction.controller;

import it.isw2.prediction.Project;
import it.isw2.prediction.config.ApplicationConfig;
import it.isw2.prediction.factory.CommitRepositoryFactory;
import it.isw2.prediction.factory.MethodRepositoryFactory;
import it.isw2.prediction.factory.TicketRepositoryFactory;
import it.isw2.prediction.model.Method;
import it.isw2.prediction.model.Version;
import it.isw2.prediction.repository.CommitRepository;
import it.isw2.prediction.repository.MethodRepository;
import it.isw2.prediction.repository.TicketRepository;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatasetCreationController {

    private static final Logger LOGGER = Logger.getLogger(DatasetCreationController.class.getName());
    private final String SEPARATOR = ";";


    private String csvHeader = "Package;Classe;Metodo;Versione;LOC;Cyclomatic;Cognitive;MethodHistories;Buggy\n";
    private HashMap<String, String> csvRecords = new HashMap<>();

    public void createDataset() {
        MethodRepository methodRepository = MethodRepositoryFactory.getInstance().getMethodRepository();
        List<Method> methods = methodRepository.retrieveMethods();

        for(Method method : methods) {
            List<Version> versions = method.getVersions();
            for (Version version : versions) {
                StringBuilder record = new StringBuilder();
                record.append(method.getPackageName()).append(SEPARATOR)
                        .append(method.getClassName()).append(SEPARATOR)
                        .append(method.getMethodName()).append(SEPARATOR)
                        .append(version.getName()).append(SEPARATOR)
                        .append(method.getLOC(version)).append(SEPARATOR)
                        .append(method.getCyclomaticComplexity(version)).append(SEPARATOR)
                        .append(method.getCognitiveComplexity(version)).append(SEPARATOR)
                        .append(method.getMethodHistories(version)).append(SEPARATOR)
                        .append(method.isBuggy(version));

                String recordKey = computeCsvRecordKey(method, version);
                csvRecords.put(recordKey, record.toString() + "\n");
            }
        }

        // Scrivere il file CSV
        writeCSVFile();

    }

    private void writeCSVFile() {

        String projectName = getProjectName();

        // Creare la directory dataset/{projectName} se non esiste
        ApplicationConfig config = new ApplicationConfig();
        String csvFilePath = config.getDatasetPath() + "/" + projectName + ".csv";
        try {
            Files.createDirectories(Paths.get(config.getDatasetPath()));
        } catch (IOException _) {
            LOGGER.log(Level.SEVERE, () -> "Impossibile creare la directory: " + csvFilePath);
            System.exit(1);
        }

        try (FileWriter csvWriter = new FileWriter(csvFilePath)) {
            // Intestazione CSV
            csvWriter.append(csvHeader);
            // Scrivere i dati per ogni metodo
            for (String record : csvRecords.values()) csvWriter.append(record);

            LOGGER.log(Level.INFO, "File CSV creato con successo: " + csvFilePath);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Errore durante la creazione del file CSV: " + csvFilePath, e);
        }
    }

    private String computeCsvRecordKey(Method method, Version version) {
        final String separator = ";";
        return method.getFullName() + separator
                + version.getName();
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
