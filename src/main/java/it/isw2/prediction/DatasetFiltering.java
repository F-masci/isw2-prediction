package it.isw2.prediction;

import it.isw2.prediction.controller.DatasetController;

import java.util.logging.Level;
import java.util.logging.Logger;

public class DatasetFiltering {

    private static final Logger logger = Logger.getLogger(DatasetFiltering.class.getName());

    public static void main(String[] args) {
        try {

            DatasetController controller = new DatasetController();
            controller.filterDataset();

        } catch(Exception e) {
            logger.log(Level.SEVERE, "Errore durante la creazione del dataset", e);
            System.exit(1);
        }
    }

}
