package it.isw2.prediction.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.isw2.prediction.Project;
import it.isw2.prediction.config.ApplicationConfig;
import it.isw2.prediction.config.JiraApiConfig;
import it.isw2.prediction.exception.RetrievalException;
import it.isw2.prediction.exception.version.VersionParsingException;
import it.isw2.prediction.exception.version.VersionRetrievalException;
import it.isw2.prediction.model.Version;

import java.net.http.HttpResponse;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class VersionDaoRest extends DaoRest implements VersionDao {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = Logger.getLogger(VersionDaoRest.class.getName());

    @Override
    public List<Version> retrieveVersions() throws VersionRetrievalException {

        List<Version> versions = new ArrayList<>();

        int startAt = 0;
        int maxResults = 1000;
        int results = 0;

        try {
            do {

                // Recupero del progetto
                ApplicationConfig config = new ApplicationConfig();
                Project project = config.getSelectedProject();

                // Costruzione dell'endpoint
                String endpoint = String.format("%s/project/%s/version?startAt=%s&maxResults=%s", JiraApiConfig.getBaseUrl(), project.getId(), startAt, maxResults);

                logger.log(Level.FINE, "Endpoint: {0}", endpoint);

                // Esecuzione della richiesta GET
                HttpResponse<String> response = executeGetRequest(endpoint);

                // Parsing della risposta JSON
                JsonNode rootNode = objectMapper.readTree(response.body());
                JsonNode values = rootNode.get("values");

                // Controllo se values è un array e parsing delle versioni
                if (values.isArray()) {
                    // Itera su ogni versione
                    for (JsonNode versionNode : values) {
                        // Parsing della versione
                        Version version = parseVersion(versionNode);
                        if(version == null) {
                            logger.log(Level.WARNING, "Versione non valida trovata: {0}", versionNode);
                            continue; // Salta le versioni non valide
                        }
                        versions.add(version);
                        results++;
                    }
                }

                startAt += results;
            } while(results == maxResults);

        } catch (RetrievalException e) {
            throw new VersionRetrievalException("Errore durante il recupero delle versioni", e);
        } catch (Exception e) {
            throw new VersionParsingException("Errore durante il parsing della risposta JSON", e);
        }

        return versions;

    }
    
    /**
     * Parsing della versione da un nodo JSON
     * @param versionNode Nodo JSON della versione
     * @return Version
     */
    private Version parseVersion(JsonNode versionNode) {
        // Formato della data
        final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");

        // Campi della versione
        final String idField = "id";
        final String nameField = "name";
        final String releaseDateField = "releaseDate";

        // Parsing dei campi
        String id = versionNode.get(idField).asText();
        String name = versionNode.get(nameField).asText();

        Date releaseDate = null;
        if (versionNode.has(releaseDateField) && !versionNode.get(releaseDateField).isNull()) {
            try {
                releaseDate = dateFormatter.parse(versionNode.get(releaseDateField).asText());
            } catch (ParseException e) {
                logger.log(Level.WARNING, "Errore durante il parsing della data di rilascio: {0}", e.getMessage());
            }
        } else {
            logger.log(Level.WARNING, "Data di rilascio non valida per la versione: {0}", name);
            return null;
        }

        // Creazione della versione
        return new Version(Integer.parseInt(id), name, releaseDate);
    }
}
