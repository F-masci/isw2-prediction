package it.isw2.prediction.dao;

import it.isw2.prediction.exception.method.MethodRetrievalException;
import it.isw2.prediction.model.Method;

import java.util.List;

public interface MethodDao {

    /**
     * Recupera tutti i metodi unici presenti nel progetto
     *
     * @return Lista di tutti i metodi trovati
     */
    List<Method> retrieveMethods() throws MethodRetrievalException;

}
