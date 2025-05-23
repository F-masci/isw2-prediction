package it.isw2.prediction.dao;

import it.isw2.prediction.model.Commit;

import java.util.List;

public interface CommitDao {

    public List<Commit> retrieveCommits();

}
