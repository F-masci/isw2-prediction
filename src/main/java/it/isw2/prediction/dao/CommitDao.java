package it.isw2.prediction.dao;

import it.isw2.prediction.model.Commit;

import java.util.List;

public interface CommitDao {

    /**
     * Ritorna tutti i commit
     * @return Commit associati
     */
    List<Commit> retrieveCommits();

    /**
     * Ritorna l'ultimo commit di un particolare branch.
     *
     * @param branchName il nome del branch di cui si vuole l'ultimo commit
     * @return il commit trovato o null se non esiste
     */
    Commit retriveLastCommitOfBranch(String branchName);

}
