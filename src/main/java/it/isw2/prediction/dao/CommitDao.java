package it.isw2.prediction.dao;

import it.isw2.prediction.model.Commit;

import java.util.List;

public interface CommitDao {

    /**
     * Ritorna tutti i commit
     * @return Commit associati
     */
    public List<Commit> retrieveCommits();

}
