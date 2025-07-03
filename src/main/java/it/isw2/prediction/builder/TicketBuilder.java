package it.isw2.prediction.builder;

import it.isw2.prediction.config.ApplicationConfig;
import it.isw2.prediction.exception.ticket.TicketRetrievalException;
import it.isw2.prediction.factory.VersionRepositoryFactory;
import it.isw2.prediction.model.Commit;
import it.isw2.prediction.model.Ticket;
import it.isw2.prediction.model.Version;
import it.isw2.prediction.repository.VersionRepository;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TicketBuilder {

    private static final Logger LOGGER = Logger.getLogger(TicketBuilder.class.getName());
    private final Ticket ticket;

    private Version affectedVersion = null;
    private Version openingVersion = null;
    private Version fixedVersion = null;
    private boolean isProportionalVersion = false;

    // Variabile statica per la proporzione con sliding window
    private static double proportionValue; // Valore di proportion
    private static final double WINDOW_SIZE_PERCENTAGE; // Dimensione della finestra scorrevole
    private static final List<Double> recentProportions = new ArrayList<>();
    private static int expectedTotal = -1; // Numero totale di ticket attesi per il calcolo della proporzione
    private static int totalCounter = 0; // Contatore totale per il calcolo della dimensione della finestra
    private static int proportionCounter = 0;

    private final VersionRepository versionRepository;

    static {
        // Inizializza la configurazione della proporzione
        ApplicationConfig config = new ApplicationConfig();
        proportionValue = config.getStartProportionValue();
        WINDOW_SIZE_PERCENTAGE = config.getProportionWindowSize();
    }

    public static void setExpectedTotal(int expectedTotal) {
        TicketBuilder.expectedTotal = expectedTotal;
    }

    public TicketBuilder (int id, String key, Date creationDate, Date resolutionDate, Date updateDate) {
        this.ticket = new Ticket(id, key, creationDate, resolutionDate, updateDate);
        this.versionRepository = VersionRepositoryFactory.getInstance().getVersionRepository();

        // Calcola la versione di apertura in base alla data di creazione del ticket
        openingVersion = versionRepository.retrievePreviousVersionByDate(ticket.getCreationDate());

        // Calcola la versione di chiusura in base alla data di chiusura dell'ultimo commit
        Commit lastCommit = ticket.getLastCommit();
        if (lastCommit != null)
            fixedVersion = versionRepository.retrieveNextVersionByDate(lastCommit.getDate());

    }

    public TicketBuilder withAffectedVersion(int affectedVersionId) {

        // Controlla se la versione è precedente alla versione di apertura
        Version version = versionRepository.retrieveVersionById(affectedVersionId);
        if(version == null) return this;

        // Se la versione è maggiore della versione di apertura, ignorala
        if(version.getReleaseDate().after(openingVersion.getReleaseDate())) return this;

        // Se la versione è precedente alla versione di apertura, la setta come affectedVersion
        this.affectedVersion = version;
        this.isProportionalVersion = false;
        return this;
    }

    public TicketBuilder withOpeningVersion(int openingVersionId) {
        this.openingVersion = versionRepository.retrieveVersionById(openingVersionId);
        return this;
    }

    public TicketBuilder withFixedVersion(int fixedVersionId) {
        this.fixedVersion = versionRepository.retrieveVersionById(fixedVersionId);
        return this;
    }

    public Ticket build() throws TicketRetrievalException {

        if(openingVersion == null || fixedVersion == null)
            throw new TicketRetrievalException("Impossibile costruire il ticket: openingVersion o fixedVersion non sono stati impostati correttamente");

        ticket.setOpeningVersion(openingVersion);
        ticket.setFixedVersion(fixedVersion);

        // Se affectedVersion non è già stata impostata, calcolare la versione proporzionale
        if (this.affectedVersion == null) {
            TicketBuilder.proportionCounter++;
            LOGGER.log(Level.INFO, () -> "Utilizzo di proportion sul ticket " + ticket.getKey() + " (contatore: " + proportionCounter + ", valore proporzionale: " + proportionValue + ")");

            Version proportionalVersion = computeProportionalVersion();
            if(proportionalVersion == null) throw new TicketRetrievalException("Impossibile calcolare la versione proporzionale per il ticket: " + ticket.getKey());

            this.affectedVersion = proportionalVersion;
            this.isProportionalVersion = true;
        }

        // Imposta la versione affected
        ticket.setBaseAffectedVersion(affectedVersion, isProportionalVersion);

        TicketBuilder.totalCounter++;

        if (!isProportionalVersion) updateProportionValue();

        return ticket;
    }

    /* --- PROPORIONAL VERSION --- */

    /**
     * Calcola la versione proporzionale tra la versione di apertura e quella di fix
     * @return la versione proporzionale calcolata
     */
    private Version computeProportionalVersion() {
        if (openingVersion == null || fixedVersion == null) return null;

        // Ottieni tutte le versioni tra opening e fixed
        List<Version> versions = versionRepository.retrieveVersions();

        if (versions.isEmpty()) return openingVersion;

        int fixedIndex = versions.indexOf(fixedVersion);
        int openingIndex = versions.indexOf(openingVersion);

        // Calcola l'indice proporzionale
        int proportionalIndex = (int) (fixedIndex - (fixedIndex - openingIndex) * proportionValue);
        return versions.get(proportionalIndex);
    }

    /**
     * Aggiorna il valore di proporzione dopo ogni utilizzo e calcola la media mobile
     */
    public void updateProportionValue() {

        // Se non è stata usata la proporzione, memorizza il risultato effettivo
        List<Version> versions = versionRepository.retrieveVersions();

        // Calcola la proporzione effettiva
        int fixedIndex = versions.indexOf(fixedVersion);
        int openingIndex = versions.indexOf(openingVersion);
        int injectedIndex = versions.indexOf(affectedVersion);

        double actualProportion = fixedIndex - openingIndex > 0 ? (double) (fixedIndex - injectedIndex) / (fixedIndex - openingIndex) : 0;

        // Calcola la dimensione della finestra scorrevole
        int windowSize = (int) Math.max(1, Math.ceil(WINDOW_SIZE_PERCENTAGE * TicketBuilder.totalCounter));;
        if(TicketBuilder.expectedTotal >= 0) windowSize = (int) Math.max(1, Math.ceil(WINDOW_SIZE_PERCENTAGE * TicketBuilder.expectedTotal));

        // Aggiungi il nuovo valore alla lista delle proporzioni
        if (recentProportions.size() >= windowSize) recentProportions.removeFirst();

        recentProportions.add(actualProportion);

        // Calcola la media mobile delle proporzioni
        double sum = 0;
        for (Double value : recentProportions) {
            sum += value;
        }

        TicketBuilder.proportionValue = sum / recentProportions.size();
    }

}
