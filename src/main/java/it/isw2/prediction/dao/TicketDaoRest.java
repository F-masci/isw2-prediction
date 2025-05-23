package it.isw2.prediction.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.isw2.prediction.Project;
import it.isw2.prediction.builder.TicketBuilder;
import it.isw2.prediction.config.ApplicationConfig;
import it.isw2.prediction.exception.RetrievalException;
import it.isw2.prediction.exception.ticket.TicketRetrievalException;
import it.isw2.prediction.exception.version.VersionRetrievalException;
import it.isw2.prediction.factory.VersionDaoFactory;
import it.isw2.prediction.model.Ticket;
import it.isw2.prediction.exception.ticket.TicketParsingException;
import it.isw2.prediction.config.JiraRestApiConfig;
import it.isw2.prediction.model.Version;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TicketDaoRest extends DaoRest implements TicketDao {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = Logger.getLogger(TicketDaoRest.class.getName());

    private static HashMap<String, Ticket> tickets = null;

    @Override
    public List<Ticket> retrieveTickets() throws TicketRetrievalException {
        this.loadTicketsCache();
        return new ArrayList<>(tickets.values());
    }

    @Override
    public List<Ticket> retrieveTickets(int startAt, int maxResults) throws TicketRetrievalException {
        this.loadTicketsCache();

        // Controllo dei parametri
        if(startAt < 0) throw new IllegalArgumentException("Il numero di partenza deve essere maggiore o uguale a 0");
        if(maxResults < 0) throw new IllegalArgumentException("Il numero massimo di risultati deve essere maggiore o uguale a 0");

        // Controllo se startAt è maggiore del numero di ticket
        List<Ticket> ticketList = new ArrayList<>(tickets.values());
        if(startAt >= ticketList.size()) return new ArrayList<>();

        return ticketList.subList(startAt, Math.min(startAt + maxResults, ticketList.size()));
    }

    /**
     * Carica la cache dei ticket
     * @throws TicketRetrievalException Se non è possibile cercare i ticket
     * @throws TicketParsingException Se non è possibile parsare i ticket
     */
    private void loadTicketsCache() throws TicketRetrievalException, TicketParsingException {
        if (tickets == null) {

            tickets = new HashMap<>();

            int startAt = 0;
            int maxResults = 1000;
            int results = 0;

            try {
                do {

                    // Recupero del progetto
                    ApplicationConfig config = new ApplicationConfig();
                    Project project = config.getSelectedProject();

                    logger.log(Level.FINE, () -> "Retrieving tickets of " + project.getKey());

                    // Costruzione della query JQL
                    String jql = "project=" + project.getId() + " AND issueType = 'Bug' AND (status = 'closed' OR status = 'resolved') AND resolution = 'fixed'";
                    String encodedJql = URLEncoder.encode(jql, StandardCharsets.UTF_8);

                    // Costruzione dell'endpoint
                    String fields = "key,resolutiondate,versions,fixVersions,created,updated";
                    String endpoint = String.format("%s/search?jql=%s&fields=%s&startAt=%s", JiraRestApiConfig.getBaseUrl(), encodedJql, fields, startAt);

                    // Aggiunta dei parametri di paginazione
                    if(maxResults > 0) endpoint += "&maxResults=" + maxResults;

                    logger.log(Level.FINE, "Endpoint: {0}", endpoint);

                    // Esecuzione della richiesta GET
                    HttpResponse<String> response = executeGetRequest(endpoint);

                    // Parsing della risposta JSON
                    JsonNode rootNode = objectMapper.readTree(response.body());
                    JsonNode issues = rootNode.get("issues");

                    // Controllo se issues è un array e parsing dei ticket
                    if (issues.isArray()) {
                        // Itera su ogni ticket
                        for (JsonNode issue : issues) {
                            // Parsing del ticket
                            Ticket ticket = parseTicket(issue);
                            tickets.put(ticket.getKey(), ticket);
                            results++;
                        }
                    }

                    startAt += results;
                } while(results == maxResults);

            } catch (RetrievalException e) {
                throw new TicketRetrievalException("Errore durante il recupero dei ticket", e);
            } catch (Exception e) {
                throw new TicketParsingException("Errore durante il parsing della risposta JSON", e);
            }
        }
    }

    /**
     * Parsing del ticket da un nodo JSON
     * @param issueNode Nodo JSON del ticket
     * @return Ticket
     */
    private Ticket parseTicket(JsonNode issueNode) {

        // Formato della data
        final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

        // Campi del ticket
        final String idField = "id";
        final String keyField = "key";
        final String fieldsField = "fields";
        final String createdDateField = "created";
        final String resolutionDateField = "resolutiondate";
        final String updatedField = "updated";

        final String affectedVersionField = "version";
        final String fixedVersionField = "fixVersions";

        // Parsing dei campi
        String id = issueNode.get(idField).asText();
        String key = issueNode.get(keyField).asText();
        JsonNode fields = issueNode.get(fieldsField);

        LocalDateTime creationDate = LocalDateTime.parse(fields.get(createdDateField).asText(), dateFormatter);
        LocalDateTime updateDate = LocalDateTime.parse(fields.get(updatedField).asText(), dateFormatter);
        LocalDateTime resolutionDate = fields.has(resolutionDateField) && !fields.get(resolutionDateField).isNull()
                ? LocalDateTime.parse(fields.get(resolutionDateField).asText(), dateFormatter)
                : null;

        // Creazione del ticket
        TicketBuilder builder = new TicketBuilder(Integer.parseInt(id), key, creationDate, resolutionDate, updateDate);

        VersionDaoFactory versionDaoFactory = VersionDaoFactory.getInstance();
        VersionDao versionDao = versionDaoFactory.getVersionDao();

        // Parsing delle affected versioni
        if (fields.has(affectedVersionField) && fields.get(affectedVersionField).isArray()) {
            for (JsonNode versionNode : fields.get(affectedVersionField)) {
                try {
                    // Parsing della versione
                    String versionId = versionNode.get("id").asText();
                    Version affectedVersion = versionDao.retrieveVersionById(Integer.parseInt(versionId));
                    builder.withAffectedVersion(affectedVersion);
                } catch (VersionRetrievalException e) {
                    logger.log(Level.WARNING, "Errore durante il recupero della versione: {0}", e.getMessage());
                }
            }
        }

        // Parsing delle affected versioni
        if (fields.has(fixedVersionField) && fields.get(fixedVersionField).isArray()) {
            for (JsonNode versionNode : fields.get(fixedVersionField)) {
                try {
                    // Parsing della versione
                    String versionId = versionNode.get("id").asText();
                    Version fixedVersion = versionDao.retrieveVersionById(Integer.parseInt(versionId));
                    builder.addFixedVersion(fixedVersion);
                } catch (VersionRetrievalException e) {
                    logger.log(Level.WARNING, "Errore durante il recupero della versione: {0}", e.getMessage());
                }
            }
        }

        try {
            Version openingVersion = versionDao.retrievePreviousVersionByDate(creationDate.toLocalDate());
            builder.withOpeningVersion(openingVersion);
        } catch (VersionRetrievalException e) {
            logger.log(Level.WARNING, "Errore durante il recupero della versione: {0}", e.getMessage());
        }

        return builder.build();
    }
}
