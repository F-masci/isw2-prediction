package it.isw2.prediction.dao;

import it.isw2.prediction.model.Commit;
import it.isw2.prediction.model.Method;

import java.util.List;

public interface MethodDao {

    /**
     * Recupera tutti i metodi unici presenti nel progetto
     *
     * @return Lista di tutti i metodi trovati
     */
    public List<Method> retrieveMethods();

    /**
     * Recupera i metodi modificati in un commit specifico
     *
     * @param commit Il commit da analizzare
     * @return Lista di metodi modificati nel commit
     */
    List<Method> retrieveModifiedMethods(Commit commit);
}
