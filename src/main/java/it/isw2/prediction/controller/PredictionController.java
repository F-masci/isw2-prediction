package it.isw2.prediction.controller;

import it.isw2.prediction.config.ApplicationConfig;
import weka.classifiers.Classifier;
import weka.classifiers.trees.J48;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.StringToNominal;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PredictionController extends CsvWriterController {

    private static final Logger LOGGER = Logger.getLogger(PredictionController.class.getName());
    private static final String SEPARATOR = ";";
    
    private final ApplicationConfig config;
    private final String datasetPath;
    private final String outputPath;
    
    public PredictionController() {
        this.config = new ApplicationConfig();
        String projectName = config.getSelectedProject().getKey();
        this.datasetPath = Paths.get(config.getDatasetPath(), projectName + ".csv").toString();
        this.outputPath = Paths.get(config.getPredictionPath(), projectName + ".csv").toString();
    }

    public void runPrediction() throws Exception {
        try {
            // Caricamento CSV
            CSVLoader loader = new CSVLoader();
            loader.setSource(new File(datasetPath));
            loader.setFieldSeparator(SEPARATOR);
            Instances data = loader.getDataSet();

            // Converte stringhe in nominali (es: Project, Package, Class, Method, Version, Buggy)
            StringToNominal filter = new StringToNominal();
            filter.setAttributeRange("first-last");
            filter.setInputFormat(data);
            data = Filter.useFilter(data, filter);

            // Imposta la colonna 'Buggy' come target
            int classIndex = data.attribute("Buggy").index();
            data.setClassIndex(classIndex);

            // Limita a massimo 50 istanze
            int predictionLimit = config.getPredictionLimit();
            if (predictionLimit > 0) {
                data = new Instances(data, 0, Math.min(predictionLimit, data.numInstances()));
            }

            // Addestra un classificatore (J48)
            Classifier classifier = new J48();
            classifier.buildClassifier(data);

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
            for (int i = 0; i < data.numInstances(); i++) {
                Instance inst = data.instance(i);
                double actualClass = inst.classValue();
                double predictedClass = classifier.classifyInstance(inst);

                String actual = data.classAttribute().value((int) actualClass);
                String predicted = data.classAttribute().value((int) predictedClass);

                String line = inst.stringValue(projectIdx) + SEPARATOR +
                        inst.stringValue(packageIdx) + SEPARATOR +
                        inst.stringValue(classIdx) + SEPARATOR +
                        inst.stringValue(methodIdx) + SEPARATOR +
                        inst.stringValue(versionIdx) + SEPARATOR +
                        actual + SEPARATOR +
                        predicted;

                lines.add(line);
            }

            writeCsvFile(outputPath, header, lines);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Errore durante la predizione del dataset", e);
            System.exit(1);
        }
    }
}
