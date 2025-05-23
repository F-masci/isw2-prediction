package it.isw2.prediction.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.isw2.prediction.Project;
import it.isw2.prediction.config.ApplicationConfig;
import it.isw2.prediction.exception.RetrievalException;
import it.isw2.prediction.exception.version.VersionParsingException;
import it.isw2.prediction.exception.version.VersionRetrievalException;
import it.isw2.prediction.model.Version;
import it.isw2.prediction.config.JiraRestApiConfig;

import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class VersionDaoRest extends DaoRest implements VersionDao {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = Logger.getLogger(VersionDaoRest.class.getName());

    private static HashMap<Integer, Version> versions = null;

    @Override
    public List<Version> retrieveVersions() throws VersionRetrievalException {
        this.loadVersionsCache();
        return new ArrayList<>(versions.values());
    }

    @Override
    public List<Version> retrieveVersions(int startAt, int maxResults) throws VersionRetrievalException {

        this.loadVersionsCache();
        
        // Controllo dei parametri
        if(startAt < 0) throw new IllegalArgumentException("Il numero di partenza deve essere maggiore o uguale a 0");
        if(maxResults < 0) throw new IllegalArgumentException("Il numero massimo di risultati deve essere maggiore o uguale a 0");
        
        // Controllo se startAt è maggiore del numero di versioni
        if(startAt >= versions.size()) return new ArrayList<>();
        
        return new ArrayList<>(versions.values()).subList(startAt, Math.min(startAt + maxResults, versions.size()));
        
    }

    @Override
    public Version retrieveVersionById(int id) throws VersionRetrievalException {
        this.loadVersionsCache();
        return versions.get(id);
    }

    @Override
    public Version retrieveNextVersionByDate(LocalDate date) throws VersionRetrievalException {
        this.loadVersionsCache();

        // Ordinamento delle versioni in base alla data di rilascio
        List<Version> orderedVersions = new ArrayList<>(versions.values());
        orderedVersions.sort(Comparator.comparing(Version::getReleaseDate, Comparator.nullsLast(Comparator.naturalOrder())));

        // Trova la prima versione con data di rilascio successiva alla data fornita
        for (Version version : orderedVersions) {
            if (version.getReleaseDate() != null && version.getReleaseDate().isAfter(date)) {
                return version;
            }
        }
        return null;
    }

    @Override
    public Version retrievePreviousVersionByDate(LocalDate date) throws VersionRetrievalException {
        this.loadVersionsCache();

        // Ordinamento delle versioni in base alla data di rilascio
        List<Version> orderedVersions = new ArrayList<>(versions.values());
        orderedVersions.sort(Comparator.comparing(Version::getReleaseDate, Comparator.nullsLast(Comparator.reverseOrder())));

        // Trova la prima versione con data di rilascio precedente alla data fornita
        for (Version version : orderedVersions) {
            if (version.getReleaseDate() != null && version.getReleaseDate().isBefore(date)) {
                return version;
            }
        }
        return null;
    }

    /**
     * Carica la cache delle versioni
     * @throws VersionRetrievalException Se non è possibile cercare le versioni
     * @throws VersionParsingException Se non è possibile parsare le versioni
     */
    private void loadVersionsCache() throws VersionRetrievalException, VersionParsingException {
        if (versions == null) {
            
            versions = new HashMap<>();
            
            int startAt = 0;
            int maxResults = 1000;
            int results = 0;

            try {
                do {
    
                    // Recupero del progetto
                    ApplicationConfig config = new ApplicationConfig();
                    Project project = config.getSelectedProject();
                    
                    // Costruzione dell'endpoint
                    String endpoint = String.format("%s/project/%s/version?startAt=%s", JiraRestApiConfig.getBaseUrl(), project.getId(), startAt);
    
                    // Aggiunta dei parametri di paginazione
                    if(maxResults > 0) endpoint += "&maxResults=" + maxResults;

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
                            versions.put(version.getId(), version);
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
