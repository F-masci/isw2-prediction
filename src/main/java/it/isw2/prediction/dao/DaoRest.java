package it.isw2.prediction.dao;

import it.isw2.prediction.exception.RetrievalException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class DaoRest {

    private static final HttpClient httpClient = HttpClient.newHttpClient();

    protected HttpResponse<String> executeGetRequest(String endpoint) throws RetrievalException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // Controllo dello stato della risposta
            if (response.statusCode() != 200) throw new RetrievalException("Risposta non valida dalle API. Status code: " + response.statusCode());

            return response;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RetrievalException("Errore durante la richiesta alle API", e);
        }
    }

}
