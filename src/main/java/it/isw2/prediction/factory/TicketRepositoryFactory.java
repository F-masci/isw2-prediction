package it.isw2.prediction.factory;

import it.isw2.prediction.repository.CachedTicketRepository;
import it.isw2.prediction.repository.TicketRepository;
import it.isw2.prediction.repository.TicketRepositoryImpl;

/**
 * Factory per la creazione di istanze di TicketRepository.
 * Implementa il pattern Singleton per garantire l'esistenza di una sola istanza.
 */
public class TicketRepositoryFactory {

    private static TicketRepositoryFactory instance;

    private final TicketRepository cachedRepository;

    private TicketRepositoryFactory() {
        this.cachedRepository = new CachedTicketRepository(new TicketRepositoryImpl());
    }

    public static TicketRepositoryFactory getInstance() {
        if (instance == null) instance = new TicketRepositoryFactory();
        return instance;
    }

    public TicketRepository getTicketRepository() {
        return cachedRepository;
    }
}
