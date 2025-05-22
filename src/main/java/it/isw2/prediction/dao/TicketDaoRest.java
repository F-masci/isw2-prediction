package it.isw2.prediction.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.isw2.prediction.Project;
import it.isw2.prediction.exception.RetrievalException;
import it.isw2.prediction.exception.ticket.TicketRetrievalException;
import it.isw2.prediction.model.Ticket;
import it.isw2.prediction.exception.ticket.TicketParsingException;
import it.isw2.prediction.config.JiraRestApiConfig;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TicketDaoRest extends DaoRest implements TicketDao {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = Logger.getLogger(TicketDaoRest.class.getName());

    @Override
    public List<Ticket> retrieveTicketsByProject(Project project) throws TicketRetrievalException {
        return this.retrieveTicketsByProject(project, 0, 0);
    }

    @Override
    public List<Ticket> retrieveTicketsByProject(Project project, int startAt, int maxResults) throws TicketRetrievalException {

        // Controllo dei parametri
        if(startAt < 0) throw new IllegalArgumentException("Il numero di partenza deve essere maggiore o uguale a 0");
        if(maxResults < 0) throw new IllegalArgumentException("Il numero massimo di risultati deve essere maggiore o uguale a 0");

        logger.log(Level.FINE, () -> "Retrieving tickets of " + project.getKey() + " by " + startAt + " to " +maxResults);

        // Costruzione della query JQL
        String jql = "project=" + project.getId() + " AND issueType = 'Bug' AND (status = 'closed' OR status = 'resolved') AND resolution = 'fixed'";
        String encodedJql = URLEncoder.encode(jql, StandardCharsets.UTF_8);

        // Costruzione dell'endpoint
        String fields = "key,resolutiondate,versions,created";
        String endpoint = String.format("%s/search?jql=%s&fields=%s&startAt=%s", JiraRestApiConfig.getBaseUrl(), encodedJql, fields, startAt);

        // Aggiunta dei parametri di paginazione
        if(maxResults > 0) endpoint += "&maxResults=" + maxResults;

        logger.log(Level.FINE, "Endpoint: {0}", endpoint);

        try {

            // Esecuzione della richiesta GET
            HttpResponse<String> response = executeGetRequest(endpoint);

            // Controllo dello stato della risposta
            if (response.statusCode() != 200) {
                logger.log(Level.SEVERE, "Risposta non valida da JIRA. Status code: {0}", response.statusCode());
                return Collections.emptyList();
            }

            logger.log(Level.FINE, "JIRA connection successfully");

            // Parsing della risposta JSON
            JsonNode rootNode = objectMapper.readTree(response.body());
            JsonNode issues = rootNode.get("issues");
            List<Ticket> tickets = new ArrayList<>();

            // Controllo se issues è un array e parsing dei ticket
            if (issues.isArray()) {
                // Itera su ogni ticket
                for (JsonNode issue : issues) {
                    // Parsing del ticket
                    tickets.add(parseTicket(issue));
                }
            }

            return tickets;

        } catch (RetrievalException e) {
            throw new TicketRetrievalException("Errore durante il recupero dei ticket", e);
        } catch (Exception e) {
            throw new TicketParsingException("Errore durante il parsing della risposta JSON", e);
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
        final String createdField = "created";
        final String resolutionDateField = "resolutiondate";

        // Parsing dei campi
        String id = issueNode.get(idField).asText();
        String key = issueNode.get(keyField).asText();
        JsonNode fields = issueNode.get(fieldsField);

        LocalDateTime creationDate = LocalDateTime.parse(fields.get(createdField).asText(), dateFormatter);
        LocalDateTime resolutionDate = fields.has(resolutionDateField) && !fields.get(resolutionDateField).isNull()
                ? LocalDateTime.parse(fields.get(resolutionDateField).asText(), dateFormatter)
                : null;

        // Creazione del ticket
        return new Ticket(Integer.parseInt(id), key, resolutionDate, creationDate);
    }
}