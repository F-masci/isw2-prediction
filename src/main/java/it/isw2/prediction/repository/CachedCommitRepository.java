package it.isw2.prediction.repository;

import it.isw2.prediction.model.Commit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Implementazione del pattern Decorator per aggiungere funzionalità di cache
 * al CommitRepository. Questa classe decora un CommitRepository esistente
 * aggiungendo la cache senza modificare la sua implementazione originale.
 */
public class CachedCommitRepository implements CommitRepository {
    private static final Logger LOGGER = Logger.getLogger(CachedCommitRepository.class.getName());

    // Componente decorato
    private final CommitRepository repository;

    // Cache per i commit
    private Map<String, Commit> commitCache = null;

    /**
     * Costruttore del decorator.
     *
     * @param repository il CommitRepository da decorare
     */
    public CachedCommitRepository(CommitRepository repository) {
        this.repository = repository;
    }

    /**
     * Sovrascrive il metodo originale aggiungendo la funzionalità di cache.
     */
    @Override
    public List<Commit> retrieveCommits() {
        loadCommitsCache();
        return new ArrayList<>(commitCache.values());
    }

    /**
     * Sovrascrive il metodo originale aggiungendo la funzionalità di cache.
     */
    @Override
    public Commit retrieveCommitById(String commitId) {
        loadCommitsCache();
        return commitCache.get(commitId);
    }

    /**
     * Carica la cache dei commit se non è già stata caricata.
     * Se la cache è già presente, non fa nulla.
     */
    public void loadCommitsCache() {
        if (commitCache == null) {
            LOGGER.info("Cache dei commit non inizializzata, creazione della cache");
            commitCache = new HashMap<>();

            LOGGER.info("Caricamento della cache dei commit");
            List<Commit> commits = repository.retrieveCommits();

            for (Commit commit : commits) {
                try {
                    commitCache.put(commit.getId(), commit);
                } catch (NullPointerException e) {
                    LOGGER.warning("Errore durante il caricamento del commit");
                }
            }

            LOGGER.info(() -> "Cache dei commit caricata con " + commitCache.size() + " commits");
        }
    }

    /**
     * Metodo aggiuntivo specifico del decorator.
     * Invalida la cache, forzando il repository a ricaricare i dati alla prossima richiesta.
     */
    public void invalidateCache() {
        LOGGER.info("Invalidazione della cache");
        commitCache.clear();
    }
}

