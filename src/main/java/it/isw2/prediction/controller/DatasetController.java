package it.isw2.prediction.controller;

import it.isw2.prediction.config.ApplicationConfig;
import it.isw2.prediction.exception.version.VersionFilteringException;
import it.isw2.prediction.factory.MethodRepositoryFactory;
import it.isw2.prediction.factory.VersionRepositoryFactory;
import it.isw2.prediction.model.Method;
import it.isw2.prediction.model.Version;
import it.isw2.prediction.repository.MethodRepository;
import it.isw2.prediction.repository.VersionRepository;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatasetController extends CsvController {

    private static final Logger LOGGER = Logger.getLogger(DatasetController.class.getName());
    private static final String SEPARATOR = ";";
    private static final int VERSION_IDX = 4;

    private final String projectName;
    private final String datasetPath;
    private final String filteredDatasetPath;

    public DatasetController() {
        ApplicationConfig config = new ApplicationConfig();
        this.projectName = config.getSelectedProject().getKey();
        this.datasetPath = Paths.get(config.getDatasetPath(), projectName + ".csv").toString();
        this.filteredDatasetPath = Paths.get(config.getDatasetPath(), projectName + "_filtered.csv").toString();
    }

    public void createDataset() {
        try {
            MethodRepository methodRepository = MethodRepositoryFactory.getInstance().getMethodRepository();
            List<Method> methods = methodRepository.retrieveMethods();

            String header = String.join(SEPARATOR,
                    "Project",
                    "Package",
                    "Class",
                    "Method",
                    "Version",
                    "LOC",
                    "Statement",
                    "Cyclomatic",
                    "Cognitive",
                    "MethodHistories",
                    "AddedLines",
                    "MaxAddedLines",
                    "AvgAddedLines",
                    "DeletedLines",
                    "MaxDeletedLines",
                    "AvgDeletedLines",
                    "Churn",
                    "MaxChurn",
                    "AvgChurn",
                    "BranchPoints",
                    "NestingDepth",
                    "ParametersCount",
                    "Buggy"
            );
            List<String> lines = new ArrayList<>();

            for (Method method : methods) {
                List<Version> versions = method.getVersions();
                for (Version version : versions) {
                    String line = projectName + SEPARATOR +
                            method.getPackageName() + SEPARATOR +
                            method.getClassName() + SEPARATOR +
                            method.getMethodName() + SEPARATOR +
                            version.getName() + SEPARATOR +
                            method.getLOC(version) + SEPARATOR +
                            method.getStatement(version) + SEPARATOR +
                            method.getCyclomaticComplexity(version) + SEPARATOR +
                            method.getCognitiveComplexity(version) + SEPARATOR +
                            method.getMethodHistories(version) + SEPARATOR +
                            method.getAddedLines(version) + SEPARATOR +
                            method.getMaxAddedLines(version) + SEPARATOR +
                            String.format("%.2f", method.getAvgAddedLines(version)) + SEPARATOR +
                            method.getDeletedLines(version) + SEPARATOR +
                            method.getMaxDeletedLines(version) + SEPARATOR +
                            String.format("%.2f", method.getAvgDeletedLines(version)) + SEPARATOR +
                            method.getChurn(version) + SEPARATOR +
                            method.getMaxChurn(version) + SEPARATOR +
                            String.format("%.2f", method.getAvgChurn(version)) + SEPARATOR +
                            method.getBranchPoints(version) + SEPARATOR +
                            method.getNestingDepth(version) + SEPARATOR +
                            method.getParametersCount(version) + SEPARATOR +
                            method.isBuggy(version);
                    lines.add(line);
                }
            }

            writeCsvFile(datasetPath, header, lines);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Errore durante la creazione del dataset", e);
            System.exit(1);
        }

    }

    public void filterDataset() {
        try {

            ApplicationConfig config = new ApplicationConfig();
            int versionsPercentage = config.getVersionsPercentage();

            VersionRepository versionRepository = VersionRepositoryFactory.getInstance().getVersionRepository();
            List<Version> versions = versionRepository.retrieveVersions().stream().sorted(Comparator.comparing(Version::getReleaseDate)).toList();
            int totVersions = versions.size();
            int filteredVersions = (int) Math.ceil(totVersions * versionsPercentage / 100.0);

            if(filteredVersions <= 0) throw new VersionFilteringException("Numero di versioni filtrate non valido: " + filteredVersions);

            LOGGER.log(Level.INFO, "Filtrando il dataset per le prime {0} versioni su {1} totali (percentuale di {2}%)", new Object[]{filteredVersions, totVersions, versionsPercentage});

            // Leggi il dataset esistente e filtra i record per le versioni selezionate
            File dataset = new File(datasetPath);
            if (!dataset.exists()) throw new VersionFilteringException("Impossibile trovare dataset: " + datasetPath);

            List<String[]> allLines = readCsvFile(datasetPath, SEPARATOR);
            if( allLines.isEmpty() || allLines.size() <= 1) throw new VersionFilteringException("Impossibile caricare dataset: " + datasetPath);

            String header = String.join(SEPARATOR, allLines.getFirst());
            List<String> filteredVersionNames = versions.subList(0, filteredVersions).stream().map(Version::getName).toList();

            List<String> filteredLines = new ArrayList<>();
            for (String[] fields : allLines.subList(1, allLines.size())) {
                // Filtra le righe in base al nome della versione
                if (filteredVersionNames.contains(fields[VERSION_IDX])) filteredLines.add(String.join(SEPARATOR, fields));
            }

            writeCsvFile(filteredDatasetPath, header, filteredLines);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Errore durante il filtraggio del dataset", e);
            System.exit(1);
        }
    }

}
