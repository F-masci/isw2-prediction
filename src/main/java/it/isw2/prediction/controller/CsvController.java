package it.isw2.prediction.controller;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class CsvController {

    private static final Logger LOGGER = Logger.getLogger(CsvController.class.getName());

    protected void writeCsvFile(String csvFilePath, String header, List<String> lines) {

        try {
            Path parentDir = Paths.get(csvFilePath).getParent();
            if (parentDir != null && !Files.exists(parentDir)) Files.createDirectories(parentDir);
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

    public List<String[]> readCsvFile(String csvFilePath, String separator) throws IOException {
        List<String[]> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(csvFilePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line.split(separator, -1));
            }
        }
        return lines;
    }

}