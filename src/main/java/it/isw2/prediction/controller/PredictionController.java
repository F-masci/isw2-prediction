package it.isw2.prediction.controller;

import it.isw2.prediction.FeatureSelection;
import it.isw2.prediction.config.ApplicationConfig;
import it.isw2.prediction.factory.VersionRepositoryFactory;
import it.isw2.prediction.model.Version;
import it.isw2.prediction.repository.VersionRepository;
import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;
import weka.attributeSelection.*;
import weka.classifiers.AbstractClassifier;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.trees.RandomForest;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SelectedTag;
import weka.core.converters.CSVLoader;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.StringToNominal;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.text.MessageFormat.format;

public class PredictionController extends CsvController {

    private static final Logger LOGGER = Logger.getLogger(PredictionController.class.getName());
    private static final String SEPARATOR = ";";
    private static final String ATTRIBUTE_RANGE = "first-last";
    private static final String BUGGY_ATTRIBUTE = "Buggy";
    private static final String VERSION_ATTRIBUTE = "Version";

    private final ApplicationConfig config = new ApplicationConfig();
    private final String projectName = config.getSelectedProject().getKey();
    private final String filteredDatasetPath;
    private final String outputDir;

    public PredictionController() {
        this.filteredDatasetPath = Paths.get(config.getDatasetPath(), projectName + "_filtered.csv").toString();
        this.outputDir = config.getOutputPath();

        String whatIfPath = Paths.get(outputDir, projectName + "_whatif.csv").toString();
        File whatIfFile = new File(whatIfPath);
        if (whatIfFile.exists() && !whatIfFile.delete()) {
            LOGGER.log(Level.WARNING, "Impossibile cancellare il file whatif esistente: {0}", whatIfPath);
        }

    }

    public void evaluateModels() throws Exception {
        try {
            List<FeatureSelection> featureSelections = config.getValidationFeatureSelectionMethos();
            if (featureSelections.isEmpty()) {
                LOGGER.log(Level.WARNING, "Nessuna feature selection configurata.");
                featureSelections.add(FeatureSelection.NONE);
            }

            String header = String.join(SEPARATOR, "Model", "Feature Selection", "Features Number", "Precision", "Recall", "AUC", "Kappa");
            List<String> lines = new ArrayList<>();

            printMemoryUsage("Memoria prima del caricamento del dataset");

            CSVLoader loader = new CSVLoader();
            loader.setSource(new File(filteredDatasetPath));
            loader.setFieldSeparator(SEPARATOR);
            Instances originalData = loader.getDataSet();
            StringToNominal filter = new StringToNominal();
            filter.setAttributeRange(ATTRIBUTE_RANGE);
            filter.setInputFormat(originalData);
            originalData = Filter.useFilter(originalData, filter);

            VersionRepository versionRepository = VersionRepositoryFactory.getInstance().getVersionRepository();
            List<Version> versions = versionRepository.retrieveVersions().stream().sorted(Comparator.comparing(Version::getReleaseDate)).toList();

            printMemoryUsage("Memoria dopo del caricamento del dataset");

            Classifier[] models = { new RandomForest(), new NaiveBayes(), new IBk() };
            String[] modelNames = { "RandomForest", "NaiveBayes", "IBk" };

            for (FeatureSelection featureSelection : featureSelections) {
                Instances baseData = featureSelection == FeatureSelection.INFO_GAIN
                        ? selectFeaturesWithInfoGainRanker(originalData)
                        : new Instances(originalData);

                for (int m = 0; m < models.length; m++) {
                    Classifier model = models[m];
                    String modelName = modelNames[m];
                    Instances trainData = applyFeatureSelection(baseData, model, featureSelection);

                    printMemoryUsage("Memoria dopo feature selection " + featureSelection.getName());
                    LOGGER.log(Level.INFO, "Addestramento modello {0} con {1} feature", new Object[]{modelName, trainData.numAttributes() - 1});

                    int classIndex = trainData.attribute(BUGGY_ATTRIBUTE).index();
                    trainData.setClassIndex(classIndex);

                    ModelMetrics metrics = evaluateModelOnVersions(trainData, originalData, model, versions, versionRepository, classIndex);

                    String message = format(
                            "{0} - Precision: {1}, Recall: {2}, AUC: {3}, Kappa: {4}",
                            modelName, metrics.precision, metrics.recall, metrics.auc, metrics.kappa
                    );
                    LOGGER.log(Level.INFO, message);

                    String line = String.join(SEPARATOR,
                            modelName,
                            featureSelection.getName(),
                            String.valueOf(trainData.numAttributes() - 1),
                            String.valueOf(metrics.precision),
                            String.valueOf(metrics.recall),
                            String.valueOf(metrics.auc),
                            String.valueOf(metrics.kappa)
                    );
                    lines.add(line);

                    printMemoryUsage("Memoria dopo evaluation");
                }
            }

            String metricsCsvPath = Paths.get(outputDir, projectName + "_metrics.csv").toString();
            writeCsvFile(metricsCsvPath, header, lines);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Errore durante la predizione del dataset", e);
            System.exit(1);
        }
    }

    private Instances applyFeatureSelection(Instances data, Classifier model, FeatureSelection featureSelection) throws Exception {
        switch (featureSelection) {
            case FORWARD, BACKWARD:
                return selectFeaturesWithSearchWrapper(data, model, featureSelection);
            case INFO_GAIN, NONE:
                return data;
            default:
                LOGGER.log(Level.WARNING, () -> "Feature selection non supportata: " + featureSelection);
                return data;
        }
    }

    private ModelMetrics evaluateModelOnVersions(
            Instances trainData,
            Instances originalData,
            Classifier model,
            List<Version> versions,
            VersionRepository versionRepository,
            int classIndex
    ) throws Exception {
        int versionInFolds = config.getNumberOfVersionInValidationFolds();
        List<Double> precisions = new ArrayList<>();
        List<Double> recalls = new ArrayList<>();
        List<Double> aucs = new ArrayList<>();
        List<Double> kappas = new ArrayList<>();

        for (int i = versionInFolds; i < versions.size(); i++) {
            List<Version> trainVersions = versions.subList(0, i);
            List<Version> testVersions = versions.subList(i, Math.min(i + versionInFolds, versions.size()));

            Instances trainSet = new Instances(trainData, 0);
            Instances testSet = new Instances(trainData, 0);

            for (int j = 0; j < trainData.numInstances(); j++) {
                String versionName = originalData.instance(j).stringValue(originalData.attribute(VERSION_ATTRIBUTE));
                Version version = versionRepository.retrieveVersionByName(versionName);

                if (trainVersions.contains(version)) trainSet.add(trainData.instance(j));
                else if (testVersions.contains(version)) testSet.add(trainData.instance(j));
            }

            if (trainSet.numInstances() == 0 || testSet.numInstances() == 0) {
                LOGGER.log(Level.WARNING, "Nessuna istanza disponibile per il training o il test nel fold che termina con versione {0}.", versions.get(i).getName());
                continue;
            }

            trainSet.setClassIndex(classIndex);
            testSet.setClassIndex(classIndex);

            Classifier modelCopy = AbstractClassifier.makeCopy(model);
            modelCopy.buildClassifier(trainSet);

            Evaluation eval = new Evaluation(trainSet);
            eval.evaluateModel(modelCopy, testSet);

            precisions.add(eval.precision(1));
            recalls.add(eval.recall(1));
            aucs.add(eval.areaUnderROC(1));
            kappas.add(eval.kappa());
        }

        return new ModelMetrics(
                precisions.stream().mapToDouble(Double::doubleValue).average().orElse(0),
                recalls.stream().mapToDouble(Double::doubleValue).average().orElse(0),
                aucs.stream().mapToDouble(Double::doubleValue).average().orElse(0),
                kappas.stream().mapToDouble(Double::doubleValue).average().orElse(0)
        );
    }

    public void runPrediction() throws Exception {
        try {

            // Addestra un classificatore
            String selectedModel = config.getInferenceClassifier();
            Classifier model = switch (selectedModel) {
                case "RandomForest" -> new RandomForest();
                case "NaiveBayes" -> new NaiveBayes();
                case "IBk" -> new IBk();
                default -> throw new IllegalArgumentException("Modello di classificazione non supportato: " + selectedModel);
            };

            // Caricamento CSV
            CSVLoader loader = new CSVLoader();
            loader.setSource(new File(filteredDatasetPath));
            loader.setFieldSeparator(SEPARATOR);
            Instances data = loader.getDataSet();

            // Converte stringhe in nominali (es: Project, Package, Class, Method, Version, Buggy)
            StringToNominal filter = new StringToNominal();
            filter.setAttributeRange(ATTRIBUTE_RANGE);
            filter.setInputFormat(data);
            data = Filter.useFilter(data, filter);

            FeatureSelection featureSelection = config.getInferenceFeatureSelectionMethod();
            LOGGER.log(Level.INFO, "Selezione feature con {0}", featureSelection.getName());

            Instances reducedData = data;
            switch (featureSelection) {
                case FeatureSelection.FORWARD, FeatureSelection.BACKWARD:
                    reducedData = selectFeaturesWithSearchWrapper(data, model, featureSelection);
                    break;
                case FeatureSelection.INFO_GAIN:
                    reducedData = selectFeaturesWithInfoGainRanker(data);
                    break;
                case FeatureSelection.NONE:
                    break;
                default:
                    LOGGER.log(Level.WARNING, () -> "Feature selection non supportata: " + featureSelection);
            }

            String actionableFeature = config.getInferenceActionableFeature();
            if(reducedData.attribute(actionableFeature) == null) throw new IllegalArgumentException("Actionable feature non trovata: " + actionableFeature);
            int actionableIdx = reducedData.attribute(actionableFeature).index();

            // Imposta la colonna 'Buggy' come target
            int classIndex = reducedData.attribute(BUGGY_ATTRIBUTE).index();
            reducedData.setClassIndex(classIndex);
            model.buildClassifier(reducedData);

            LOGGER.log(Level.INFO, "Modello addestrato: {0}", selectedModel);

            // A = istanze completa
            Instances instanceA = new Instances(reducedData);

            // B+ = istanze con actionable > 0
            Instances instanceBplus = new Instances(reducedData, 0);
            for (int i = 0; i < reducedData.numInstances(); i++) {
                Instance inst = reducedData.instance(i);
                if (inst.value(actionableIdx) > 0) {
                    instanceBplus.add(inst);
                }
            }

            // C = istanze con actionable == 0
            Instances instanceC = new Instances(reducedData, 0);
            for (int i = 0; i < reducedData.numInstances(); i++) {
                Instance inst = reducedData.instance(i);
                if (inst.value(actionableIdx) == 0) {
                    instanceC.add(inst);
                }
            }

            // B = copia di B+ con actionable settato a 0
            Instances instanceB = new Instances(instanceBplus);
            for (int i = 0; i < instanceB.numInstances(); i++) {
                instanceB.instance(i).setValue(actionableIdx, 0);
            }

            LOGGER.log(Level.INFO, "Dataset creati: {0} istanze in B+, {1} in C, {2} in B", new Object[]{instanceBplus.numInstances(), instanceC.numInstances(), instanceB.numInstances()});

            predictDataset(data, instanceA, model, "A");
            predictDataset(data, instanceBplus, model, "Bplus");
            predictDataset(data, instanceC, model, "C");
            predictDataset(data, instanceB, model, "B");

            LOGGER.log(Level.INFO, "Predizione completata");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Errore durante la predizione del dataset", e);
            System.exit(1);
        }
    }

    // Metodo di utilità per predire e scrivere su file
    private void predictDataset(Instances originalData, Instances reducedData, Classifier model, String suffix) throws Exception {

        LOGGER.log(Level.INFO, "Predizione su {0} istanze ({1})", new Object[]{reducedData.numInstances(), suffix});

        String header = String.join(SEPARATOR, "Project", "Package", "Class", "Method", VERSION_ATTRIBUTE, "Actual", "Predicted");
        List<String> lines = new ArrayList<>();

        int projectIdx = originalData.attribute("Project").index();
        int packageIdx = originalData.attribute("Package").index();
        int classIdx = originalData.attribute("Class").index();
        int methodIdx = originalData.attribute("Method").index();
        int versionIdx = originalData.attribute(VERSION_ATTRIBUTE).index();

        for (int i = 0; i < reducedData.numInstances(); i++) {
            LOGGER.log(Level.INFO, "Predizione istanza {0}/{1}", new Object[]{i + 1, reducedData.numInstances()});
            Instance inst = reducedData.instance(i);
            double actualClass = inst.classValue();
            double predictedClass = model.classifyInstance(inst);

            String actual = reducedData.classAttribute().value((int) actualClass);
            String predicted = reducedData.classAttribute().value((int) predictedClass);

            String line = originalData.instance(i).stringValue(projectIdx) + SEPARATOR +
                    originalData.instance(i).stringValue(packageIdx) + SEPARATOR +
                    originalData.instance(i).stringValue(classIdx) + SEPARATOR +
                    originalData.instance(i).stringValue(methodIdx) + SEPARATOR +
                    originalData.instance(i).stringValue(versionIdx) + SEPARATOR +
                    actual + SEPARATOR +
                    predicted;

            lines.add(line);
        }

        LOGGER.log(Level.INFO, "Predizione con {0} completata", suffix);

        logBuggyStats(lines, suffix);

        String predictionCsvPath = Paths.get(outputDir, projectName + "_" + suffix + "_prediction.csv").toString();
        writeCsvFile(predictionCsvPath, header, lines);
    }

    private void logBuggyStats(List<String> lines, String datasetName) {
        int actualIdx = 5;
        int predictedIdx = 6;
        long realBuggy = 0;
        long predictedBuggy = 0;

        for (String line : lines) {
            String[] cols = line.split(SEPARATOR, -1);
            if (cols.length < 7) continue;
            if (!datasetName.equals("B") && (cols[actualIdx].equalsIgnoreCase("True") || cols[actualIdx].equalsIgnoreCase("Yes"))) realBuggy++;
            if (cols[predictedIdx].equalsIgnoreCase("True") || cols[predictedIdx].equalsIgnoreCase("Yes")) predictedBuggy++;
        }

        String summary;
        if (!datasetName.equals("B")) {
            LOGGER.log(Level.INFO, "Dataset {0}: Metodi buggy reali = {1}, Metodi predetti buggy = {2}", new Object[]{datasetName, realBuggy, predictedBuggy});
            summary = datasetName + SEPARATOR + realBuggy + SEPARATOR + predictedBuggy;
        } else {
            LOGGER.log(Level.INFO, "Dataset {0}: Metodi predetti buggy = {1}", new Object[]{datasetName, predictedBuggy});
            summary = datasetName + SEPARATOR + "-" + SEPARATOR + predictedBuggy;
        }

        // Scrivi intestazione solo se il file non esiste o è vuoto
        String outputPath = Paths.get(outputDir, projectName + "_whatif.csv").toString();
        File file = new File(outputPath);
        boolean writeHeader = !file.exists() || file.length() == 0;
        try (FileWriter fw = new FileWriter(outputPath, true)) {
            if (writeHeader) {
                fw.write("Dataset" + SEPARATOR + "Metodi buggy reali" + SEPARATOR + "Metodi predetti buggy" + System.lineSeparator());
            }
            fw.write(summary + System.lineSeparator());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, e, () -> "Errore scrittura stats su file: " + outputPath);
        }
    }

    /**
     * Seleziona le feature usando WrapperSubsetEval + GreedyStepwise (Forward o Backward).
     *
     * @param data     il dataset su cui applicare la selezione
     * @param model    classificatore da usare nel wrapper
     * @param strategy strategia di selezione delle feature (FORWARD o BACKWARD)
     * @return il dataset ridotto alle feature selezionate
     */
    public Instances selectFeaturesWithSearchWrapper(Instances data, Classifier model, FeatureSelection strategy) throws Exception {
        if(strategy != FeatureSelection.FORWARD && strategy != FeatureSelection.BACKWARD)
            throw new IllegalArgumentException("Strategia di selezione non supportata: " + strategy);

        LOGGER.info("Durante la selezione delle feature è normale che appaiano messaggi 'only class attribute present' — sono causati dall'esplorazione di subset vuoti.");

        AttributeSelection selector = new AttributeSelection();

        WrapperSubsetEval evaluator = new WrapperSubsetEval();
        evaluator.setClassifier(model);
        evaluator.setFolds(config.getValidationFolds());
        evaluator.setSeed(config.getRandomSeed());
        evaluator.setEvaluationMeasure(new SelectedTag(
                WrapperSubsetEval.EVAL_ACCURACY, WrapperSubsetEval.TAGS_EVALUATION
        ));

        GreedyStepwise search = new GreedyStepwise();
        search.setSearchBackwards(strategy == FeatureSelection.BACKWARD);

        selector.setEvaluator(evaluator);
        selector.setSearch(search);
        selector.SelectAttributes(data);

        int[] selected = selector.selectedAttributes();
        LOGGER.log(Level.INFO, "Attributi selezionati (wrapper search): {0}", Arrays.toString(selected));

        Instances reduced = selector.reduceDimensionality(data);

        // Se rimane solo la classe restituisci il dataset originale
        if (reduced.numAttributes() <= 1) {
            LOGGER.warning("La selezione delle feature ha rimosso tutte le feature predittive! Uso il dataset originale.");
            return data;
        }

        return reduced;
    }

    /**
     * Seleziona le feature usando Information Gain (ranking delle feature).
     * @param data il dataset su cui applicare la selezione
     * @return il dataset ridotto alle feature selezionate
     */
    public Instances selectFeaturesWithInfoGainRanker(Instances data) throws Exception {
        AttributeSelection selector = new AttributeSelection();
        InfoGainAttributeEval evaluator = new InfoGainAttributeEval();

        Ranker search = new Ranker();
        double threshold = config.getValidationFeatureSelectionThreshold();
        int maxFeatures = config.getValidationFeatureSelectionFeatures();
        if(threshold > 0) search.setThreshold(threshold);
        if(maxFeatures > 0) search.setNumToSelect(maxFeatures);

        selector.setEvaluator(evaluator);
        selector.setSearch(search);
        selector.SelectAttributes(data);

        int[] selected = selector.selectedAttributes();
        LOGGER.log(Level.INFO, "Attributi selezionati (info gain): {0}", Arrays.toString(selected));
        return selector.reduceDimensionality(data);
    }

    public void computeCorrelation() throws Exception {
        // Carica il dataset filtrato
        CSVLoader loader = new CSVLoader();
        loader.setSource(new File(filteredDatasetPath));
        loader.setFieldSeparator(SEPARATOR);
        Instances data = loader.getDataSet();

        // Converte stringhe in nominali
        StringToNominal filter = new StringToNominal();
        filter.setAttributeRange(ATTRIBUTE_RANGE);
        filter.setInputFormat(data);
        data = Filter.useFilter(data, filter);

        // Imposta la classe BUGGY_ATTRIBUTE come target
        int classIndex = data.attribute(BUGGY_ATTRIBUTE).index();
        data.setClassIndex(classIndex);

        // Pearson con Weka
        CorrelationAttributeEval pearsonEval = new CorrelationAttributeEval();
        pearsonEval.buildEvaluator(data);

        // Prepara CSV
        String header = "Attributo;Pearson;Spearman";
        List<String> lines = new ArrayList<>();
        lines.add(header);

        for (int i = 0; i < data.numAttributes(); i++) {
            if (i == classIndex) continue;

            // Pearson
            double pearson = pearsonEval.evaluateAttribute(i);

            // Spearman con Apache Commons Math
            double[] x = new double[data.numInstances()];
            double[] y = new double[data.numInstances()];
            for (int j = 0; j < data.numInstances(); j++) {
                x[j] = data.instance(j).value(i);
                y[j] = data.instance(j).classValue();
            }
            SpearmansCorrelation corr = new SpearmansCorrelation();
            double spearman = corr.correlation(x, y);

            lines.add(String.format("%s;%.6f;%.6f", data.attribute(i).name(), pearson, spearman));
        }

        // Scrivi file CSV
        String correlationCsvPath = Paths.get(outputDir, projectName + "_correlation.csv").toString();
        writeCsvFile(correlationCsvPath, header, lines.subList(1, lines.size())); // header già incluso
    }

    private void printMemoryUsage(String message) {
        long freeMemory = Runtime.getRuntime().freeMemory() / (1024 * 1024);
        long totalMemory = Runtime.getRuntime().totalMemory() / (1024 * 1024);
        long maxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        LOGGER.log(Level.INFO, "{0} - Free: {1} MB, Total: {2} MB, Max: {3} MB", new Object[]{message, freeMemory, totalMemory, maxMemory});
    }

    // Classe di supporto per le metriche
    private static class ModelMetrics {

        final double precision;
        final double recall;
        final double auc;
        final double kappa;

        ModelMetrics(double precision, double recall, double auc, double kappa) {
            this.precision = precision;
            this.recall = recall;
            this.auc = auc;
            this.kappa = kappa;
        }
    }

}
