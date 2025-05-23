package it.isw2.prediction.repository;

import it.isw2.prediction.model.Commit;
import it.isw2.prediction.model.Method;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class CachedMethodRepository implements MethodRepository {
    private static final Logger LOGGER = Logger.getLogger(CachedMethodRepository.class.getName());

    // Componente decorato
    private final MethodRepository repository;

    // Cache per i metodi
    private Map<String, Method> methodCache = null;

    public CachedMethodRepository(MethodRepository methodRepository) {
        this.repository = methodRepository;
    }

    @Override
    public List<Method> retrieveMethods() {
        loadMethodsCache();
        return new ArrayList<>(repository.retrieveMethods());
    }

    @Override
    public Method retrieveMethodByFullName(String fullName) {
        loadMethodsCache();
        return methodCache.get(fullName);
    }

    @Override
    public List<Method> retrieveModifiedMethods(Commit commit) {
        return repository.retrieveModifiedMethods(commit);
    }

    /**
     * Carica la cache dei metodi se non è già stata caricata.
     * Se la cache è già presente, non fa nulla.
     */
    private void loadMethodsCache() {
        if (methodCache == null) {
            LOGGER.info("Cache dei metodi non inizializzata, creazione della cache");
            methodCache = new HashMap<>();
            LOGGER.info("Caricamento della cache dei metodi");
            List<Method> methods = repository.retrieveMethods();
            for (Method method : methods) {
                try {
                    methodCache.put(method.getFullName(), method);
                } catch (NullPointerException e) {
                    LOGGER.warning("Errore durante il caricamento del metodo");
                }
            }
        }
    }
    
}
