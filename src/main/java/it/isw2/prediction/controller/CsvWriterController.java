package it.isw2.prediction.controller;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class CsvWriterController {

    private static final Logger LOGGER = Logger.getLogger(CsvWriterController.class.getName());

    protected void writeCsvFile(String csvFilePath, String header, List<String> lines) {

        try {
            Files.createDirectories(Paths.get(csvFilePath));
        } catch (IOException _) {
            LOGGER.log(Level.SEVERE, () -> "Impossibile creare la directory: " + csvFilePath);
            System.exit(1);
        }

        try (FileWriter csvWriter = new FileWriter(csvFilePath)) {
            // Intestazione CSV
            csvWriter.append(header).append("\n");
            // Scrivere i dati per ogni metodo
            for (String line : lines) csvWriter.append(line).append("\n");

            LOGGER.log(Level.INFO, "File CSV creato con successo: {0}", csvFilePath);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e, () -> "Errore durante la creazione del file CSV: " + csvFilePath);
        }
    }

}