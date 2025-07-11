package it.isw2.prediction.repository;

import it.isw2.prediction.model.Version;

import java.util.*;
import java.util.logging.Logger;

/**
 * Implementazione del pattern Decorator per aggiungere funzionalità di cache
 * al VersionRepository. Questa classe decora un VersionRepository esistente
 * aggiungendo la cache senza modificare la sua implementazione originale.
 */
public class CachedVersionRepository implements VersionRepository {
    private static final Logger LOGGER = Logger.getLogger(CachedVersionRepository.class.getName());

    // Componente decorato
    private final VersionRepository repository;

    // Cache per le versioni - mappa tra ID versione e versione stessa
    private Map<Integer, Version> versionCache = null;

    /**
     * Costruttore del decorator.
     *
     * @param repository il VersionRepository da decorare
     */
    public CachedVersionRepository(VersionRepository repository) {
        this.repository = repository;
    }

    /**
     * Sovrascrive il metodo originale aggiungendo la funzionalità di cache.
     */
    @Override
    public List<Version> retrieveVersions() {
        loadVersionsCache();
        return new ArrayList<>(versionCache.values().stream()
                .sorted(Comparator.comparing(Version::getReleaseDate))
                .toList());
    }

    /**
     * Sovrascrive il metodo originale aggiungendo la funzionalità di cache.
     */
    @Override
    public Version retrieveVersionById(int versionId) {
        loadVersionsCache();
        return versionCache.get(versionId);
    }

    /**
     * Sovrascrive il metodo originale aggiungendo la funzionalità di cache.
     */
    @Override
    public Version retrieveVersionByName(String versionName) {
        loadVersionsCache();
        return versionCache.values().stream()
                .filter(version -> version.getName().equals(versionName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Sovrascrive il metodo originale aggiungendo la funzionalità di cache.
     */
    @Override
    public Version retrieveNextVersionByDate(Date date) {
        loadVersionsCache();

        return versionCache.values().stream()
                .filter(version -> version.getReleaseDate() != null && version.getReleaseDate().after(date))
                .min(Comparator.comparing(Version::getReleaseDate))
                .orElse(null);
    }

    /**
     * Sovrascrive il metodo originale aggiungendo la funzionalità di cache.
     */
    @Override
    public Version retrievePreviousVersionByDate(Date date) {
        loadVersionsCache();

        return versionCache.values().stream()
                .filter(version -> version.getReleaseDate() != null && version.getReleaseDate().before(date))
                .max(Comparator.comparing(Version::getReleaseDate))
                .orElseGet(() -> versionCache.values().stream()
                        .filter(version -> version.getReleaseDate() != null)
                        .min(Comparator.comparing(Version::getReleaseDate))
                        .orElse(null));
    }

    /**
     * Sovrascrive il metodo originale aggiungendo la funzionalità di cache.
     */
    @Override
    public List<Version> retrieveVersionsBetweenDates(Date startDate, Date endDate) {
        loadVersionsCache();

        return versionCache.values().stream()
                .filter(version -> version.getReleaseDate() != null &&
                        !version.getReleaseDate().before(startDate) &&
                        !version.getReleaseDate().after(endDate))
                .sorted(Comparator.comparing(Version::getReleaseDate))
                .toList();
    }

    /**
     * Sovrascrive il metodo originale aggiungendo la funzionalità di cache.
     */
    @Override
    public Version retrieveLastReleasedVersion() {
        loadVersionsCache();

        return versionCache.values().stream()
                .filter(version -> version.getReleaseDate() != null)
                .max(Comparator.comparing(Version::getReleaseDate))
                .orElse(null);
    }

    /**
     * Carica la cache delle versioni se non è già stata caricata.
     * Se la cache è già presente, non fa nulla.
     */
    private void loadVersionsCache() {
        if (versionCache == null) {
            LOGGER.info("Cache delle versioni non inizializzata, creazione della cache");
            versionCache = new HashMap<>();

            LOGGER.info("Caricamento della cache delle versioni");
            List<Version> versions = repository.retrieveVersions();

            for (Version version : versions) {
                try {
                    versionCache.put(version.getId(), version);
                } catch (NullPointerException _) {
                    LOGGER.warning("Errore durante il caricamento della versione");
                }
            }

            LOGGER.info(() -> "Cache delle versioni caricata con " + versionCache.size() + " versioni");
        }
    }

    /**
     * Metodo aggiuntivo specifico del decorator.
     * Invalida la cache, forzando il repository a ricaricare i dati alla prossima richiesta.
     */
    public void invalidateCache() {
        LOGGER.info("Invalidazione della cache delle versioni");
        versionCache = null;
    }
}
