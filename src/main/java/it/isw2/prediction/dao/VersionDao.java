package it.isw2.prediction.dao;

import it.isw2.prediction.exception.version.VersionRetrievalException;
import it.isw2.prediction.model.Version;

import java.util.List;

public interface VersionDao {

    /**
     * Ritorna tutte le versioni
     * @return Versioni associate
     * @throws VersionRetrievalException Se non è possibile cercare le versioni
     */
    List<Version> retrieveVersions() throws VersionRetrievalException;

}
