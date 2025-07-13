package it.isw2.prediction.factory;

import it.isw2.prediction.repository.CachedMethodRepository;
import it.isw2.prediction.repository.MethodRepository;
import it.isw2.prediction.repository.MethodRepositoryImpl;

public class MethodRepositoryFactory {

    private static MethodRepositoryFactory instance = null;

    private final MethodRepository cachedRepository;

    private MethodRepositoryFactory() {
        this.cachedRepository = new CachedMethodRepository(new MethodRepositoryImpl());
    }

    public static MethodRepositoryFactory getInstance() {
        if (instance == null) instance = new MethodRepositoryFactory();
        return instance;
    }

    public MethodRepository getMethodRepository() {
        return cachedRepository;
    }

}
