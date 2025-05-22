package it.isw2.prediction.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.isw2.prediction.Project;
import it.isw2.prediction.exception.RetrievalException;
import it.isw2.prediction.exception.version.VersionParsingException;
import it.isw2.prediction.exception.version.VersionRetrievalException;
import it.isw2.prediction.model.Version;
import it.isw2.prediction.config.JiraRestApiConfig;

import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class VersionDaoRest extends DaoRest implements VersionDao {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = Logger.getLogger(VersionDaoRest.class.getName());

    @Override
    public List<Version> retrieveVersionsByProject(Project project) throws VersionRetrievalException {
        return this.retrieveVersionsByProject(project, 0, 0);
    }

    @Override
    public List<Version> retrieveVersionsByProject(Project project, int startAt, int maxResults) throws VersionRetrievalException {

        // Controllo dei parametri
        if(startAt < 0) throw new IllegalArgumentException("Il numero di partenza deve essere maggiore o uguale a 0");
        if(maxResults < 0) throw new IllegalArgumentException("Il numero massimo di risultati deve essere maggiore o uguale a 0");

        logger.log(Level.FINE, () -> "Retrieving versions of " + project.getKey() + " from " + startAt + " to " + maxResults);

        // Costruzione dell'endpoint
        String endpoint = String.format("%s/project/%s/version?startAt=%s", JiraRestApiConfig.getBaseUrl(), project.getId(), startAt);

        // Aggiunta dei parametri di paginazione
        if(maxResults > 0) endpoint += "&maxResults=" + maxResults;

        logger.log(Level.FINE, "Endpoint: {0}", endpoint);

        try {
            // Esecuzione della richiesta GET
            HttpResponse<String> response = executeGetRequest(endpoint);

            // Parsing della risposta JSON
            JsonNode rootNode = objectMapper.readTree(response.body());
            JsonNode values = rootNode.get("values");
            List<Version> versions = new ArrayList<>();

            // Controllo se values è un array e parsing delle versioni
            if (values.isArray()) {
                // Itera su ogni versione
                for (JsonNode versionNode : values) {
                    // Parsing della versione
                    versions.add(parseVersion(versionNode));
                }
            }

            return versions;

        } catch (RetrievalException e) {
            throw new VersionRetrievalException("Errore durante il recupero delle versioni", e);
        } catch (Exception e) {
            throw new VersionParsingException("Errore durante il parsing della risposta JSON", e);
        }
    }

    /**
     * Parsing della versione da un nodo JSON
     * @param versionNode Nodo JSON della versione
     * @return Version
     */
    private Version parseVersion(JsonNode versionNode) {
        // Formato della data
        final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // Campi della versione
        final String idField = "id";
        final String nameField = "name";
        final String releaseDateField = "releaseDate";

        // Parsing dei campi
        String id = versionNode.get(idField).asText();
        String name = versionNode.get(nameField).asText();

        LocalDate releaseDate = versionNode.has(releaseDateField) && !versionNode.get(releaseDateField).isNull()
                ? LocalDate.parse(versionNode.get(releaseDateField).asText(), dateFormatter)
                : null;

        // Creazione della versione
        return new Version(Integer.parseInt(id), name, releaseDate);
    }
}
