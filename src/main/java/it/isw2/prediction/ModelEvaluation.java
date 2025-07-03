package it.isw2.prediction;

import it.isw2.prediction.controller.PredictionController;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ModelEvaluation {

    private static final Logger logger = Logger.getLogger(ModelEvaluation.class.getName());

    public static void main(String[] args) {
        try {

            PredictionController controller = new PredictionController();
            controller.evaluateModels();

        } catch(Exception e) {
            logger.log(Level.SEVERE, "Errore durante la predizione", e);
            System.exit(1);
        }
    }
}


