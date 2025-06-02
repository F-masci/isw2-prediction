package it.isw2.prediction;

import it.isw2.prediction.controller.DatasetCreationController;
import it.isw2.prediction.controller.PredictionController;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Prediction {

    private static final Logger logger = Logger.getLogger(Prediction.class.getName());

    public static void main(String[] args) {
        try {

            PredictionController controller = new PredictionController();
            controller.runPrediction();

        } catch(Exception e) {
            logger.log(Level.SEVERE, "Errore durante la predizione", e);
            System.exit(1);
        }
    }
}


