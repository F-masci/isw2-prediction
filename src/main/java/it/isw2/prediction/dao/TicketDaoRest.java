package it.isw2.prediction.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.isw2.prediction.Project;
import it.isw2.prediction.builder.TicketBuilder;
import it.isw2.prediction.config.ApplicationConfig;
import it.isw2.prediction.exception.RetrievalException;
import it.isw2.prediction.exception.ticket.TicketRetrievalException;
import it.isw2.prediction.model.Ticket;
import it.isw2.prediction.exception.ticket.TicketParsingException;
import it.isw2.prediction.config.JiraApiConfig;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TicketDaoRest extends DaoRest implements TicketDao {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger LOGGER = Logger.getLogger(TicketDaoRest.class.getName());
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");


    @Override
    public List<Ticket> retrieveTickets() throws TicketRetrievalException {

        List<Ticket> tickets = new ArrayList<>();

        int startAt = 0;
        int maxResults = 1000;
        int results = 0;

        try {
            do {

                // Recupero del progetto
                ApplicationConfig config = new ApplicationConfig();
                Project project = config.getSelectedProject();

                LOGGER.log(Level.FINE, () -> "Retrieving tickets of " + project.getKey());

                // Costruzione della query JQL
                String jql = "project=" + project.getId() + " AND issueType = 'Bug' AND (status = 'closed' OR status = 'resolved') AND resolution = 'fixed'";
                String encodedJql = URLEncoder.encode(jql, StandardCharsets.UTF_8);

                // Costruzione dell'endpoint
                String fields = "key,resolutiondate,versions,fixVersions,created,updated";
                String endpoint = String.format("%s/search?jql=%s&fields=%s&startAt=%s", JiraApiConfig.getBaseUrl(), encodedJql, fields, startAt);

                // Aggiunta dei parametri di paginazione
                if(maxResults > 0) endpoint += "&maxResults=" + maxResults;

                LOGGER.log(Level.FINE, "Endpoint: {0}", endpoint);

                // Esecuzione della richiesta GET
                HttpResponse<String> response = executeGetRequest(endpoint);

                // Parsing della risposta JSON
                JsonNode rootNode = OBJECT_MAPPER.readTree(response.body());
                JsonNode issues = rootNode.get("issues");

                // Controllo se issues è un array e parsing dei ticket
                if (issues.isArray()) {
                    // Itera su ogni ticket
                    for (JsonNode issue : issues) {
                        // Parsing del ticket
                        tickets.add(parseTicket(issue));
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

        return tickets;

    }

    /**
     * Parsing del ticket da un nodo JSON
     * @param issueNode Nodo JSON del ticket
     * @return Ticket
     */
    private Ticket parseTicket(JsonNode issueNode) {

        // Campi del ticket
        final String idField = "id";
        final String keyField = "key";
        final String fieldsField = "fields";
        final String createdDateField = "created";
        final String resolutionDateField = "resolutiondate";
        final String updatedField = "updated";

        final String affectedVersionField = "versions";
        // final String fixedVersionField = "fixVersions";

        // Parsing dei campi
        String id = issueNode.get(idField).asText();
        String key = issueNode.get(keyField).asText();
        JsonNode fields = issueNode.get(fieldsField);

        Date creationDate = null;
        Date updateDate = null;
        Date resolutionDate = null;
        try {

            dateFormat.setLenient(true);

            creationDate = dateFormat.parse(fields.get(createdDateField).asText());
            updateDate = dateFormat.parse(fields.get(updatedField).asText());
            resolutionDate = fields.has(resolutionDateField) && !fields.get(resolutionDateField).isNull()
                    ? dateFormat.parse(fields.get(resolutionDateField).asText())
                    : null;
        } catch (ParseException e) {
            LOGGER.log(Level.SEVERE, "Errore durante il parsing delle date", e);
        }

        // Creazione del ticket
        TicketBuilder builder = new TicketBuilder(Integer.parseInt(id), key, creationDate, resolutionDate, updateDate);

        // Parsing delle affected versioni
        boolean affectedVersion = false;
        if (fields.has(affectedVersionField) && fields.get(affectedVersionField).isArray()) {
            for (JsonNode versionNode : fields.get(affectedVersionField)) {
                // Parsing della versione
                int versionId = versionNode.get("id").asInt();
                builder.withAffectedVersion(versionId);
                affectedVersion = true;
            }
        }
        if(!affectedVersion) {
            LOGGER.log(Level.INFO, "Utilizzo di proportion sul ticket {0}", key);
            builder.withProportionalAffectedVersion();
        }

        return builder.build();
    }
}
