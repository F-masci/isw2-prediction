package it.isw2.prediction.repository;

import it.isw2.prediction.model.Version;

import java.util.Date;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        return new ArrayList<>(versionCache.values());
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
                .orElse(null);
    }

    /**
     * Sovrascrive il metodo originale aggiungendo la funzionalità di cache.
     */
    @Override
    public List<Version> retrieveVersions(int startAt, int maxResults) {
        loadVersionsCache();

        List<Version> allVersions = new ArrayList<>(versionCache.values());
        int endIndex = Math.min(startAt + maxResults, allVersions.size());

        if (startAt >= allVersions.size()) {
            return new ArrayList<>();
        }

        return allVersions.subList(startAt, endIndex);
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
                } catch (NullPointerException e) {
                    LOGGER.warning("Errore durante il caricamento della versione");
                }
            }

            LOGGER.info("Cache delle versioni caricata con " + versionCache.size() + " versioni");
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
