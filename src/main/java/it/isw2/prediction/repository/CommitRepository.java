package it.isw2.prediction.repository;

import it.isw2.prediction.model.Commit;

import java.util.List;

/**
 * Interfaccia per il Repository dei commit.
 * Definisce le operazioni di base per accedere ai commit.
 */
public interface CommitRepository {

    /**
     * Recupera tutti i commit dal branch master.
     *
     * @return lista dei commit nel branch master
     */
    List<Commit> retrieveCommits();

    /**
     * Recupera un commit specifico per ID.
     *
     * @param commitId l'ID del commit da recuperare
     * @return il commit trovato o null se non esiste
     */
    Commit retrieveCommitById(String commitId);

    /**
     * Ritorna l'ultimo commit di un particolare branch.
     *
     * @param branchName il nome del branch di cui si vuole l'ultimo commit
     * @return il commit trovato o null se non esiste
     */
    Commit retriveLastCommitOfBranch(String branchName);

}
