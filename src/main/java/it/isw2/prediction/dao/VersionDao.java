package it.isw2.prediction.dao;

import it.isw2.prediction.Project;
import it.isw2.prediction.exception.version.VersionRetrievalException;
import it.isw2.prediction.model.Version;

import java.util.List;

public interface VersionDao {

    /**
     * Ritorna tutte le versioni associate a un progetto
     * @param project Progetto
     * @return Versioni associate
     * @throws VersionRetrievalException Se non è possibile cercare le versioni
     */
    List<Version> retrieveVersionsByProject(Project project) throws VersionRetrievalException;

    /**
     * Ritorna le versioni associate a un progetto
     * @param project Progetto
     * @param startAt Indice della prima versione da restituire
     * @param maxResults Numero massimo di versioni da restituire
     * @return Versioni associate
     * @throws VersionRetrievalException Se non è possibile cercare le versioni
     */
    List<Version> retrieveVersionsByProject(Project project, int startAt, int maxResults) throws VersionRetrievalException;

}
