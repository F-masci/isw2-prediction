package it.isw2.prediction.repository;

import it.isw2.prediction.dao.VersionDao;
import it.isw2.prediction.exception.version.VersionRetrievalException;
import it.isw2.prediction.factory.VersionDaoFactory;
import it.isw2.prediction.model.Version;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementazione concreta del repository per le versioni.
 * Utilizza un VersionDao per accedere ai dati.
 */
public class VersionRepositoryImpl implements VersionRepository {
    private static final Logger LOGGER = Logger.getLogger(VersionRepositoryImpl.class.getName());
    private final VersionDao versionDao;

    /**
     * Costruttore che utilizza il DAO predefinito.
     */
    public VersionRepositoryImpl() {
        this.versionDao = VersionDaoFactory.getInstance().getVersionDao();
    }

    /**
     * Costruttore che accetta un'istanza specifica di VersionDao.
     * Utile per i test o per personalizzazioni avanzate.
     *
     * @param versionDao l'istanza di VersionDao da utilizzare
     */
    public VersionRepositoryImpl(VersionDao versionDao) {
        this.versionDao = versionDao;
    }

    @Override
    public List<Version> retrieveVersions() {
        try {
            return versionDao.retrieveVersions().stream().sorted(Comparator.comparing(Version::getReleaseDate)).toList();
        } catch (VersionRetrievalException e) {
            LOGGER.log(Level.SEVERE, "Errore nel recupero delle versioni", e);
            return new ArrayList<>();
        }
    }

    @Override
    public Version retrieveVersionById(int versionId) {
        try {
            return retrieveVersions().stream()
                    .filter(version -> version.getId() == versionId)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e, ()-> "Errore nel recupero della versione con ID: " + versionId);
            return null;
        }
    }

    @Override
    public Version retrieveVersionByName(String versionName) {
        try {
            return retrieveVersions().stream()
                    .filter(version -> version.getName().equals(versionName))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e, () -> "Errore nel recupero della versione con nome: " + versionName);
            return null;
        }
    }

    @Override
    public Version retrieveNextVersionByDate(Date date) {
        try {
            return retrieveVersions().stream()
                    .filter(version -> version.getReleaseDate() != null && version.getReleaseDate().after(date))
                    .min(Comparator.comparing(Version::getReleaseDate))
                    .orElse(null);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e, () -> "Errore nel recupero della versione successiva alla data: " + date);
            return null;
        }
    }

    @Override
    public Version retrievePreviousVersionByDate(Date date) {
        try {
            return retrieveVersions().stream()
                    .filter(version -> version.getReleaseDate() != null && version.getReleaseDate().before(date))
                    .max(Comparator.comparing(Version::getReleaseDate))
                    .orElseGet(() -> retrieveVersions().stream()
                            .filter(version -> version.getReleaseDate() != null)
                            .min(Comparator.comparing(Version::getReleaseDate))
                            .orElse(null));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e, () -> "Errore nel recupero della versione precedente alla data: " + date);
            return null;
        }
    }

    @Override
    public List<Version> retrieveVersionsBetweenDates(Date startDate, Date endDate) {
        try {
            return retrieveVersions().stream()
                    .filter(version -> version.getReleaseDate() != null &&
                            !version.getReleaseDate().before(startDate) &&
                            !version.getReleaseDate().after(endDate))
                    .sorted(Comparator.comparing(Version::getReleaseDate))
                    .toList();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e, () -> "Errore nel recupero della versione tra le date: " + startDate + " e " + endDate);
            return Collections.emptyList();
        }
    }

    @Override
    public Version retrieveLastReleasedVersion() {
        try {
            return retrieveVersions().stream()
                    .filter(version -> version.getReleaseDate() != null)
                    .max(Comparator.comparing(Version::getReleaseDate))
                    .orElse(null);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e, () -> "Errore nel recupero dell'ultima versione rilasciata");
            return null;
        }
    }
}
