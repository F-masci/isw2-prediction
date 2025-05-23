package it.isw2.prediction.factory;

import it.isw2.prediction.repository.CachedCommitRepository;
import it.isw2.prediction.repository.CommitRepository;
import it.isw2.prediction.repository.CommitRepositoryImpl;

public class CommitRepositoryFactory {

    private static CommitRepositoryFactory instance;

    private final CommitRepository cachedRepository;

    private CommitRepositoryFactory() {
        this.cachedRepository = new CachedCommitRepository(new CommitRepositoryImpl());
    }

    public static CommitRepositoryFactory getInstance() {
        if (instance == null) instance = new CommitRepositoryFactory();
        return instance;
    }

    public CommitRepository getCommitRepository() {
        return cachedRepository;
    }

}
