package it.isw2.prediction;

import it.isw2.prediction.config.ApplicationConfig;
import it.isw2.prediction.dao.MethodDaoJgit;
import it.isw2.prediction.model.Method;
import it.isw2.prediction.model.Version;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RefactorEvaluation {

    private static final Logger LOGGER = Logger.getLogger(RefactorEvaluation.class.getName());
    private static final String SEPARATOR = "\t";

    public static void main(String[] args) {
        try {

            MethodDaoJgit dao = new MethodDaoJgit();
            List<Method> methods = dao.retrieveMethods(true);

            String projectName = new ApplicationConfig().getSelectedProject().getKey();

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
                    LOGGER.info(line);
                }
            }

        } catch(Exception e) {
            LOGGER.log(Level.SEVERE, "Errore durante il calcolo delle metriche dopo il refactoring", e);
            System.exit(1);
        }
    }
}


