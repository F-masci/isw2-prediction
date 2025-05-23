package it.isw2.prediction.repository;

import it.isw2.prediction.model.Version;
import java.util.Date;
import java.util.List;

/**
 * Interfaccia per il Repository delle versioni.
 * Definisce le operazioni di base per accedere alle versioni.
 */
public interface VersionRepository {

    /**
     * Recupera tutte le versioni.
     *
     * @return lista di tutte le versioni
     */
    List<Version> retrieveVersions();

    /**
     * Recupera una versione specifica per ID.
     *
     * @param versionId l'ID della versione da recuperare
     * @return la versione trovata o null se non esiste
     */
    Version retrieveVersionById(int versionId);

    /**
     * Recupera una versione specifica per nome.
     *
     * @param versionName il nome della versione da recuperare
     * @return la versione trovata o null se non esiste
     */
    Version retrieveVersionByName(String versionName);

    /**
     * Recupera la versione successiva alla data fornita.
     *
     * @param date data di riferimento
     * @return la versione successiva alla data o null se non esiste
     */
    Version retrieveNextVersionByDate(Date date);

    /**
     * Recupera la versione precedente alla data fornita.
     *
     * @param date data di riferimento
     * @return la versione precedente alla data o null se non esiste
     */
    Version retrievePreviousVersionByDate(Date date);

    /**
     * Recupera un sottoinsieme di versioni.
     *
     * @param startAt indice della prima versione da restituire
     * @param maxResults numero massimo di versioni da restituire
     * @return lista delle versioni richieste
     */
    List<Version> retrieveVersions(int startAt, int maxResults);
}
