package it.isw2.prediction.controller;

import it.isw2.prediction.FeatureSelection;
import it.isw2.prediction.config.ApplicationConfig;
import weka.attributeSelection.*;
import weka.classifiers.Classifier;
import weka.classifiers.trees.J48;
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
import weka.core.SelectedTag;

import java.io.File;
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
    }

    public void evaluateModels() throws Exception {
        try {

            List<FeatureSelection> featureSelections = config.getValidationFeatureSelectionMethos();
            if (featureSelections.isEmpty()) {
                LOGGER.log(Level.WARNING, "Nessuna feature selection configurata.");
                featureSelections.add(FeatureSelection.NONE);
            }

            String header = String.join(SEPARATOR, "Model", "Feature Selection", "Features Number", "Precision", "Recall", "AUC", "Kappa", "NPofB20");
            List<String> lines = new ArrayList<>();

            for (FeatureSelection featureSelection : featureSelections) {

                // Addestramento e validazione modelli
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

                    // Caricamento dataset filtrato per addestramento/validazione
                    CSVLoader loader = new CSVLoader();
                    loader.setSource(new File(filteredDatasetPath));
                    loader.setFieldSeparator(SEPARATOR);
                    Instances trainData = loader.getDataSet();

                    StringToNominal filter = new StringToNominal();
                    filter.setAttributeRange("first-last");
                    filter.setInputFormat(trainData);
                    trainData = Filter.useFilter(trainData, filter);

                    LOGGER.log(Level.INFO, "Selezione feature con {0}", featureSelection.getName());

                    switch (featureSelection) {
                        case FeatureSelection.FORWARD, FeatureSelection.BACKWARD:
                            trainData = selectFeaturesWithSearchWrapper(trainData, model, featureSelection);
                            break;
                        case FeatureSelection.INFO_GAIN:
                            trainData = selectFeaturesWithInfoGainRanker(trainData);
                            break;
                        case FeatureSelection.NONE:
                            break;
                        default:
                            LOGGER.log(Level.WARNING, () -> "Feature selection non supportata: " + featureSelection);
                    }

                    LOGGER.log(Level.INFO, "Addestramento modello {0} con {1} feature", new Object[]{modelName, trainData.numAttributes() - 1});

                    int classIndex = trainData.attribute("Buggy").index();
                    trainData.setClassIndex(classIndex);

                    Evaluation eval = new Evaluation(trainData);
                    model.buildClassifier(trainData);

                    int iterations = config.getCrossValidationIterations();
                    double precision = 0;
                    double recall = 0;
                    double auc = 0;
                    double kappa = 0;

                    for (int i = 0; i < iterations; i++) {
                        eval.crossValidateModel(model, trainData, config.getCrossValidationFolds(), new Random(config.getRandomSeed()));
                        precision += eval.weightedPrecision();
                        recall += eval.weightedRecall();
                        auc += eval.weightedAreaUnderROC();
                        kappa += eval.kappa();
                    }

                    precision = precision / iterations;
                    recall = recall / iterations;
                    auc = auc / iterations;
                    kappa = kappa / iterations;

                    // Calcolo NPofB20 per la classe "buggy" (assumendo "Buggy" sia binaria e la classe positiva sia l'indice 1)
                    int buggyIdx = trainData.classAttribute().indexOfValue("True");
                    if (buggyIdx == -1) buggyIdx = 1; // fallback se non trova "True"
                    double npofb20 = eval.numTruePositives(buggyIdx) /
                            (eval.numTruePositives(buggyIdx) + eval.numFalseNegatives(buggyIdx) + 1e-10);

                    String message = format(
                            "{0} - Precision: {1}, Recall: {2}, AUC: {3}, Kappa: {4}, NPofB20: {5}",
                            modelName, precision, recall, auc, kappa, npofb20
                    );
                    LOGGER.log(Level.INFO, message);

                    String line = String.join(SEPARATOR,
                            modelName,
                            featureSelection.getName(),
                            String.valueOf(trainData.numAttributes()-1),
                            String.valueOf(precision),
                            String.valueOf(recall),
                            String.valueOf(auc),
                            String.valueOf(kappa),
                            String.valueOf(npofb20)
                    );
                    lines.add(line);
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

            // Imposta la colonna 'Buggy' come target
            int classIndex = reducedData.attribute("Buggy").index();
            reducedData.setClassIndex(classIndex);
            model.buildClassifier(reducedData);

            // Limita a massimo 50 istanze
            int predictionLimit = 50;
            if (predictionLimit > 0) {
                reducedData = new Instances(reducedData, 0, Math.min(predictionLimit, reducedData.numInstances()));
            }

            // Prepara intestazione CSV
            String header = String.join(SEPARATOR, "Project", "Package", "Class", "Method", "Version", "Actual", "Predicted");
            List<String> lines = new ArrayList<>();

            // Indici delle colonne testuali da riportare
            int projectIdx = data.attribute("Project").index();
            int packageIdx = data.attribute("Package").index();
            int classIdx = data.attribute("Class").index();
            int methodIdx = data.attribute("Method").index();
            int versionIdx = data.attribute("Version").index();

            // Predizione e scrittura righe
            for (int i = 0; i < reducedData.numInstances(); i++) {
                Instance inst = reducedData.instance(i);
                double actualClass = inst.classValue();
                double predictedClass = model.classifyInstance(inst);

                String actual = reducedData.classAttribute().value((int) actualClass);
                String predicted = reducedData.classAttribute().value((int) predictedClass);

                String line = inst.stringValue(projectIdx) + SEPARATOR +
                        data.instance(i).stringValue(packageIdx) + SEPARATOR +
                        data.instance(i).stringValue(classIdx) + SEPARATOR +
                        data.instance(i).stringValue(methodIdx) + SEPARATOR +
                        data.instance(i).stringValue(versionIdx) + SEPARATOR +
                        actual + SEPARATOR +
                        predicted;

                lines.add(line);
            }

            String predictionCsvPath = Paths.get(outputDir, projectName + "_prediction.csv").toString();
            writeCsvFile(predictionCsvPath, header, lines);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Errore durante la predizione del dataset", e);
            System.exit(1);
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
        evaluator.setFolds(config.getCrossValidationFolds());
        evaluator.setSeed(config.getRandomSeed());
        evaluator.setEvaluationMeasure(new SelectedTag(
                WrapperSubsetEval.EVAL_ACCURACY, WrapperSubsetEval.TAGS_EVALUATION
        ));

        GreedyStepwise search = new GreedyStepwise();
        search.setSearchBackwards(strategy == FeatureSelection.BACKWARD);

        selector.setEvaluator(evaluator);
        selector.setSearch(search);
        selector.SelectAttributes(data);

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
}
