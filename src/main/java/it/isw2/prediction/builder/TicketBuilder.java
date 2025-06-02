package it.isw2.prediction.builder;

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
    private static double proportionValue = 1; // Valore di default
    private static final int WINDOW_SIZE = 5; // Dimensione della finestra scorrevole
    private static final List<Double> recentProportions = new ArrayList<>(WINDOW_SIZE);
    private static int proportionCounter = 0;

    private final VersionRepository versionRepository;

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

        // Se la versione è maggiore della versione di apertura, usa la proporzione
        if(version.getReleaseDate().after(openingVersion.getReleaseDate())) return withProportionalAffectedVersion();

        // Se la versione è precedente alla versione di apertura, la setta come affectedVersion
        this.affectedVersion = version;
        this.isProportionalVersion = false;
        return this;
    }

    /**
     * Imposta la versione affetta utilizzando il metodo della proporzione
     * @return this builder (per method chaining)
     */
    public TicketBuilder withProportionalAffectedVersion() {
        if (openingVersion == null || fixedVersion == null) return this;

        TicketBuilder.proportionCounter++;
        LOGGER.log(Level.INFO, () -> "Utilizzo di proportion sul ticket " + ticket.getKey() + " (contatore: " + proportionCounter + ", valore proporzionale: " + proportionValue + ")");

        Version proportionalVersion = computeProportionalVersion();
        if (proportionalVersion != null) {
            this.affectedVersion = proportionalVersion;
            this.isProportionalVersion = true;
        }
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

        if(openingVersion == null || fixedVersion == null) throw new TicketRetrievalException("Impossibile costruire il ticket: openingVersion o fixedVersion non sono stati impostati correttamente");

        if(this.openingVersion != null) ticket.setOpeningVersion(openingVersion);
        if(this.fixedVersion != null) ticket.setFixedVersion(fixedVersion);
        if (this.affectedVersion != null) {
            ticket.setBaseAffectedVersion(affectedVersion, isProportionalVersion);

            // Se non è stata usata la proporzione, memorizza il risultato effettivo
            if (!isProportionalVersion) {
                List<Version> versions = versionRepository.retrieveVersions();

                // Calcola la proporzione effettiva
                int fixedIndex = versions.indexOf(fixedVersion);
                int openingIndex = versions.indexOf(openingVersion);
                int injectedIndex = versions.indexOf(affectedVersion);

                if (fixedIndex - openingIndex > 0) {
                    double actualProportion = (double) (fixedIndex - injectedIndex) / (fixedIndex - openingIndex);
                    updateProportionValue(actualProportion);
                }
            }
        }
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
     * @param actualProportion il valore di proporzione effettivamente osservato
     */
    public static void updateProportionValue(double actualProportion) {
        // Aggiungi il nuovo valore alla lista delle proporzioni recenti
        if (recentProportions.size() >= WINDOW_SIZE) {
            recentProportions.removeFirst();
        }
        recentProportions.add(actualProportion);

        // Calcola la media mobile delle proporzioni
        double sum = 0;
        for (Double value : recentProportions) {
            sum += value;
        }

        proportionValue = sum / recentProportions.size();
    }

}
