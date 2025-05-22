package it.isw2.prediction.dao;

import it.isw2.prediction.Project;
import it.isw2.prediction.exception.version.VersionRetrievalException;
import it.isw2.prediction.model.Version;

import java.util.List;

public interface VersionDao {

    /**
     * Ritorna una versione in base al suo id
     * @param id Id della versione
     * @return Versione
     * @throws VersionRetrievalException Se non è possibile cercare la versione
     */
    Version retrieveVersionById(int id) throws VersionRetrievalException;

    /**
     * Ritorna tutte le versioni
     * @return Versioni associate
     * @throws VersionRetrievalException Se non è possibile cercare le versioni
     */
    List<Version> retrieveVersions() throws VersionRetrievalException;

    /**
     * Ritorna le versioni richieste
     * @param startAt Indice della prima versione da restituire
     * @param maxResults Numero massimo di versioni da restituire
     * @return Versioni associate
     * @throws VersionRetrievalException Se non è possibile cercare le versioni
     */
    List<Version> retrieveVersions(int startAt, int maxResults) throws VersionRetrievalException;

}
