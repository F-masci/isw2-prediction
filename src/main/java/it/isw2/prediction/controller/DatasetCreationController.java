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

public class DatasetCreationController extends CsvWriterController {

    private static final Logger LOGGER = Logger.getLogger(DatasetCreationController.class.getName());
    private static final String SEPARATOR = ";";

    private final String projectName;
    private final String datasetPath;

    public DatasetCreationController() {
        ApplicationConfig config = new ApplicationConfig();
        this.projectName = config.getSelectedProject().getKey();
        this.datasetPath = Paths.get(config.getDatasetPath(), projectName + ".csv").toString();
    }

    public void createDataset() {
        try {
            MethodRepository methodRepository = MethodRepositoryFactory.getInstance().getMethodRepository();
            List<Method> methods = methodRepository.retrieveMethods();

            String header = String.join(SEPARATOR, "Project", "Package", "Class", "Method", "Version", "LOC", "Cyclomatic", "Cognitive", "MethodHistories", "AddedLines", "DeletedLines", "Churn", "BranchPoints", "NestingDepth", "ParametersCount", "Buggy");
            List<String> lines = new ArrayList<>();

            for(Method method : methods) {
                List<Version> versions = method.getVersions();
                for (Version version : versions) {
                    String line = projectName + SEPARATOR +
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
                    lines.add(line);
                }
            }

            writeCsvFile(datasetPath, header, lines);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Errore durante la creazione del dataset", e);
            System.exit(1);
        }

    }
}
