package it.isw2.prediction.controller;

import it.isw2.prediction.FeatureSelection;
import it.isw2.prediction.config.ApplicationConfig;
import weka.attributeSelection.*;
import weka.classifiers.Classifier;
import weka.classifiers.trees.RandomForest;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.Evaluation;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.StringToNominal;
import weka.core.SelectedTag;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.text.MessageFormat.*;

public class PredictionController extends CsvController {

    private static final Logger LOGGER = Logger.getLogger(PredictionController.class.getName());
    private static final String SEPARATOR = ";";

    private final ApplicationConfig config = new ApplicationConfig();
    private final String projectName = config.getSelectedProject().getKey();
    private final String filteredDatasetPath;
    private final String outputDir;

    public PredictionController() {
        this.filteredDatasetPath = Paths.get(config.getDatasetPath(), projectName + "_filtered.csv").toString();
        this.outputDir = config.getOutputPath();

        String whatIfPath = Paths.get(outputDir, projectName + "_whatif.csv").toString();
        File whatIfFile = new File(whatIfPath);
        if (whatIfFile.exists()) {
            if (!whatIfFile.delete()) {
                LOGGER.log(Level.WARNING, "Impossibile cancellare il file whatif esistente: " + whatIfPath);
            }
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

            // Carica il dataset originale UNA SOLA VOLTA per tutti i modelli/feature selection
            CSVLoader loader = new CSVLoader();
            loader.setSource(new File(filteredDatasetPath));
            loader.setFieldSeparator(SEPARATOR);
            Instances originalData = loader.getDataSet();
            StringToNominal filter = new StringToNominal();
            filter.setAttributeRange("first-last");
            filter.setInputFormat(originalData);
            originalData = Filter.useFilter(originalData, filter);

            printMemoryUsage("Memoria dopo del caricamento del dataset");

            for (FeatureSelection featureSelection : featureSelections) {

                Instances infoGainData = null;
                if (featureSelection == FeatureSelection.INFO_GAIN) {
                    infoGainData = selectFeaturesWithInfoGainRanker(originalData);
                }

                Classifier[] models = new Classifier[]{
                        new RandomForest(),
                        new NaiveBayes(),
                        new IBk()
                };
                String[] modelNames = {
                        "RandomForest",
                        "NaiveBayes",
                        "IBk"
                };

                for (int m = 0; m < models.length; m++) {
                    Classifier model = models[m];
                    String modelName = modelNames[m];
                    Instances trainData = featureSelection == FeatureSelection.INFO_GAIN ? new Instances(infoGainData) : new Instances(originalData);

                    LOGGER.log(Level.INFO, "Selezione feature con {0}", featureSelection.getName());

                    switch (featureSelection) {
                        case FeatureSelection.FORWARD, FeatureSelection.BACKWARD:
                            trainData = selectFeaturesWithSearchWrapper(trainData, model, featureSelection);
                            break;
                        case FeatureSelection.INFO_GAIN:
                            // Già calcolato sopra
                            break;
                        case FeatureSelection.NONE:
                            break;
                        default:
                            LOGGER.log(Level.WARNING, () -> "Feature selection non supportata: " + featureSelection);
                    }

                    printMemoryUsage("Memoria dopo feature selection " + featureSelection.getName());
                    LOGGER.log(Level.INFO, "Addestramento modello {0} con {1} feature", new Object[]{modelName, trainData.numAttributes() - 1});

                    int classIndex = trainData.attribute("Buggy").index();
                    trainData.setClassIndex(classIndex);
                    Evaluation eval = new Evaluation(trainData);

                    int iterations = config.getValidationIterations();
                    double precision = 0;
                    double recall = 0;
                    double auc = 0;
                    double kappa = 0;

                    for (int i = 0; i < iterations; i++) {
                        printMemoryUsage("Memoria evaluation iterazione " + (i + 1));
                        eval.crossValidateModel(model, trainData, config.getValidationFolds(), new Random(config.getRandomSeed()));
                        precision += eval.weightedPrecision();
                        recall += eval.weightedRecall();
                        auc += eval.weightedAreaUnderROC();
                        kappa += eval.kappa();
                    }

                    precision = precision / iterations;
                    recall = recall / iterations;
                    auc = auc / iterations;
                    kappa = kappa / iterations;

                    String message = format(
                            "{0} - Precision: {1}, Recall: {2}, AUC: {3}, Kappa: {4}",
                            modelName, precision, recall, auc, kappa
                    );
                    LOGGER.log(Level.INFO, message);

                    String line = String.join(SEPARATOR,
                            modelName,
                            featureSelection.getName(),
                            String.valueOf(trainData.numAttributes()-1),
                            String.valueOf(precision),
                            String.valueOf(recall),
                            String.valueOf(auc),
                            String.valueOf(kappa)
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
            filter.setAttributeRange("first-last");
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
            int classIndex = reducedData.attribute("Buggy").index();
            reducedData.setClassIndex(classIndex);
            model.buildClassifier(reducedData);

            LOGGER.log(Level.INFO, "Modello addestrato: {0}", selectedModel);

            // A = istanze completa
            Instances A = new Instances(reducedData);

            // B+ = istanze con actionable > 0
            Instances Bplus = new Instances(reducedData, 0);
            for (int i = 0; i < reducedData.numInstances(); i++) {
                Instance inst = reducedData.instance(i);
                if (inst.value(actionableIdx) > 0) {
                    Bplus.add(inst);
                }
            }

            // C = istanze con actionable == 0
            Instances C = new Instances(reducedData, 0);
            for (int i = 0; i < reducedData.numInstances(); i++) {
                Instance inst = reducedData.instance(i);
                if (inst.value(actionableIdx) == 0) {
                    C.add(inst);
                }
            }

            // B = copia di B+ con actionable settato a 0
            Instances B = new Instances(Bplus);
            for (int i = 0; i < B.numInstances(); i++) {
                B.instance(i).setValue(actionableIdx, 0);
            }

            LOGGER.log(Level.INFO, "Dataset creati: {0} istanze in B+, {1} in C, {2} in B", new Object[]{Bplus.numInstances(), C.numInstances(), B.numInstances()});

            predictDataset(data, A, model, "A");
            predictDataset(data, Bplus, model, "Bplus");
            predictDataset(data, C, model, "C");
            predictDataset(data, B, model, "B");

            LOGGER.log(Level.INFO, "Predizione completata");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Errore durante la predizione del dataset", e);
            System.exit(1);
        }
    }

    // Metodo di utilità per predire e scrivere su file
    private void predictDataset(Instances originalData, Instances reducedData, Classifier model, String suffix) throws Exception {

        // Limita a massimo 50 istanze
        // int predictionLimit = 50;
        // if (predictionLimit > 0) {
        //     data = new Instances(data, 0, Math.min(predictionLimit, data.numInstances()));
        //}

        LOGGER.log(Level.INFO, "Predizione su {0} istanze ({1})", new Object[]{reducedData.numInstances(), suffix});

        String header = String.join(SEPARATOR, "Project", "Package", "Class", "Method", "Version", "Actual", "Predicted");
        List<String> lines = new ArrayList<>();

        int projectIdx = originalData.attribute("Project").index();
        int packageIdx = originalData.attribute("Package").index();
        int classIdx = originalData.attribute("Class").index();
        int methodIdx = originalData.attribute("Method").index();
        int versionIdx = originalData.attribute("Version").index();

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
            if (!datasetName.equals("B")) {
                if (cols[actualIdx].equalsIgnoreCase("True") || cols[actualIdx].equalsIgnoreCase("Yes")) realBuggy++;
            }
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
            LOGGER.log(Level.WARNING, "Errore scrittura stats su file: " + outputPath, e);
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
        filter.setAttributeRange("first-last");
        filter.setInputFormat(data);
        data = Filter.useFilter(data, filter);

        // Imposta la classe "Buggy" come target
        int classIndex = data.attribute("Buggy").index();
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

            // Spearman
            double[] x = new double[data.numInstances()];
            double[] y = new double[data.numInstances()];
            for (int j = 0; j < data.numInstances(); j++) {
                x[j] = data.instance(j).value(i);
                y[j] = data.instance(j).classValue();
            }
            double spearman = spearmanCorrelation(x, y);

            // Stampa a schermo
            LOGGER.log(Level.INFO, String.format("%s: Pearson=%.6f, Spearman=%.6f", data.attribute(i).name(), pearson, spearman));

            // Scrivi su CSV
            lines.add(String.format("%s;%.6f;%.6f", data.attribute(i).name(), pearson, spearman));
        }

        // Scrivi file CSV
        String correlationCsvPath = Paths.get(outputDir, projectName + "_correlation.csv").toString();
        writeCsvFile(correlationCsvPath, header, lines.subList(1, lines.size())); // header già incluso
    }

    // Calcolo della correlazione di Spearman
    private double spearmanCorrelation(double[] x, double[] y) {
        double[] rx = rank(x);
        double[] ry = rank(y);
        double meanRx = Arrays.stream(rx).average().orElse(0);
        double meanRy = Arrays.stream(ry).average().orElse(0);
        double num = 0, denX = 0, denY = 0;
        for (int i = 0; i < x.length; i++) {
            num += (rx[i] - meanRx) * (ry[i] - meanRy);
            denX += Math.pow(rx[i] - meanRx, 2);
            denY += Math.pow(ry[i] - meanRy, 2);
        }
        return num / Math.sqrt(denX * denY + 1e-10);
    }

    // Funzione per calcolare i ranghi
    private double[] rank(double[] values) {
        Integer[] idx = new Integer[values.length];
        for (int i = 0; i < values.length; i++) idx[i] = i;
        Arrays.sort(idx, Comparator.comparingDouble(i -> values[i]));
        double[] ranks = new double[values.length];
        for (int i = 0; i < values.length; i++) ranks[idx[i]] = i + 1;
        return ranks;
    }

    private void printMemoryUsage(String message) {
        long freeMemory = Runtime.getRuntime().freeMemory() / (1024 * 1024);
        long totalMemory = Runtime.getRuntime().totalMemory() / (1024 * 1024);
        long maxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        LOGGER.log(Level.INFO, "{0} - Free: {1} MB, Total: {2} MB, Max: {3} MB", new Object[]{message, freeMemory, totalMemory, maxMemory});
    }
}
