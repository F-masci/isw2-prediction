package it.isw2.prediction.builder;

import it.isw2.prediction.factory.VersionRepositoryFactory;
import it.isw2.prediction.model.Commit;
import it.isw2.prediction.model.Ticket;
import it.isw2.prediction.model.Version;
import it.isw2.prediction.repository.VersionRepository;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TicketBuilder {

    private final Ticket ticket;

    private Version affectedVersion = null;
    private Version openingVersion = null;
    private Version fixedVersion = null;
    private boolean isProportionalVersion = false;

    // Variabile statica per la proporzione con sliding window
    private static double proportionValue = 0.5; // Valore di default
    private static final int WINDOW_SIZE = 5; // Dimensione della finestra scorrevole
    private static final List<Double> recentProportions = new ArrayList<>(WINDOW_SIZE);

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

    public Ticket build() {
        if(this.openingVersion != null) ticket.setOpeningVersion(openingVersion);
        if(this.fixedVersion != null) ticket.setFixedVersion(fixedVersion);
        if (this.affectedVersion != null) {
            ticket.setBaseAffectedVersion(affectedVersion, isProportionalVersion);

            // Se è stata usata la proporzione, memorizza il risultato effettivo
            if (isProportionalVersion) {
                // Il ticket potrebbe avere più affected versions, prendiamo la prima come indicatore
                List<Version> affectedVersions = ticket.getAffectedVersions();
                if (affectedVersions != null && !affectedVersions.isEmpty()) {
                    Version firstAffected = affectedVersions.get(0);
                    List<Version> allVersions = versionRepository.retrieveVersionsBetweenDates(
                            openingVersion.getReleaseDate(),
                            fixedVersion.getReleaseDate()
                    );

                    // Calcola la proporzione effettiva
                    int totalVersions = allVersions.size();
                    int affectedIndex = allVersions.indexOf(firstAffected);
                    if (totalVersions > 0 && affectedIndex >= 0) {
                        double actualProportion = (double) affectedIndex / totalVersions;
                        updateProportionValue(actualProportion);
                    }
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
        List<Version> intermediateVersions = versionRepository.retrieveVersionsBetweenDates(
                openingVersion.getReleaseDate(),
                fixedVersion.getReleaseDate()
        );

        if (intermediateVersions.isEmpty()) return openingVersion;

        // Calcola l'indice proporzionale
        int proportionalIndex = (int)(intermediateVersions.size() * proportionValue);
        if (proportionalIndex >= intermediateVersions.size()) {
            proportionalIndex = intermediateVersions.size() - 1;
        }

        return intermediateVersions.get(proportionalIndex);
    }

    /**
     * Aggiorna il valore di proporzione dopo ogni utilizzo e calcola la media mobile
     * @param actualProportion il valore di proporzione effettivamente osservato
     */
    public static void updateProportionValue(double actualProportion) {
        // Aggiungi il nuovo valore alla lista delle proporzioni recenti
        if (recentProportions.size() >= WINDOW_SIZE) {
            recentProportions.remove(0);
        }
        recentProportions.add(actualProportion);

        // Calcola la media mobile delle proporzioni
        double sum = 0;
        for (Double value : recentProportions) {
            sum += value;
        }

        proportionValue = sum / recentProportions.size();
    }

    /**
     * Ottiene il valore corrente della proporzione
     * @return il valore della proporzione
     */
    public static double getProportionValue() {
        return proportionValue;
    }

    /**
     * Imposta manualmente il valore della proporzione
     * @param value il nuovo valore di proporzione (tra 0 e 1)
     */
    public static void setProportionValue(double value) {
        if (value < 0) value = 0;
        if (value > 1) value = 1;
        proportionValue = value;
    }

    /**
     * Resetta lo stato della sliding window
     */
    public static void resetSlidingWindow() {
        recentProportions.clear();
        proportionValue = 0.5; // Ripristina il valore predefinito
    }

}
