package it.isw2.prediction.repository;

import it.isw2.prediction.exception.method.MethodRetrievalException;
import it.isw2.prediction.exception.method.MethodSaveException;
import it.isw2.prediction.model.Commit;
import it.isw2.prediction.model.Method;

import java.util.List;

public interface MethodRepository {

    public List<Method> retrieveMethods() throws MethodRetrievalException, MethodSaveException;

    public Method retrieveMethodByFullName(String fullName) throws MethodRetrievalException, MethodSaveException;

}
