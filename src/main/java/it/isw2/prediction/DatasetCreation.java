package it.isw2.prediction;

import it.isw2.prediction.controller.DatasetCreationController;
import it.isw2.prediction.dao.TicketDao;
import it.isw2.prediction.dao.VersionDao;
import it.isw2.prediction.exception.ticket.TicketRetrievalException;
import it.isw2.prediction.exception.version.VersionRetrievalException;
import it.isw2.prediction.factory.CommitRepositoryFactory;
import it.isw2.prediction.factory.TicketDaoFactory;
import it.isw2.prediction.factory.VersionDaoFactory;
import it.isw2.prediction.model.Commit;
import it.isw2.prediction.model.Method;
import it.isw2.prediction.model.Ticket;
import it.isw2.prediction.model.Version;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import it.isw2.prediction.repository.CommitRepository;
import it.isw2.prediction.repository.MethodRepository;
import it.isw2.prediction.repository.MethodRepositoryImpl;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;

public class DatasetCreation {

    private static final Logger logger = Logger.getLogger(DatasetCreation.class.getName());

    public static void main(String[] args) {
        try {

            DatasetCreationController controller = new DatasetCreationController();
            controller.createDataset();

        } catch(Exception e) {
            logger.log(Level.SEVERE, "Errore durante la creazione del dataset", e);
            System.exit(1);
        }
    }
}


