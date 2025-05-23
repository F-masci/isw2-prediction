package it.isw2.prediction.factory;

import it.isw2.prediction.repository.*;

/**
 * Factory per la creazione di istanze di VersionRepository.
 * Implementa il pattern Singleton per garantire l'esistenza di una sola istanza.
 */
public class VersionRepositoryFactory {

    private static VersionRepositoryFactory instance;

    private final VersionRepository cachedRepository;

    private VersionRepositoryFactory() {
        this.cachedRepository = new CachedVersionRepository(new VersionRepositoryImpl());
    }

    public static VersionRepositoryFactory getInstance() {
        if (instance == null) instance = new VersionRepositoryFactory();
        return instance;
    }

    public VersionRepository getVersionRepository() {
        return cachedRepository;
    }
}
