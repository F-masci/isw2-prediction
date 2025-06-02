package it.isw2.prediction.repository;

import it.isw2.prediction.exception.method.MethodRetrievalException;
import it.isw2.prediction.exception.method.MethodSaveException;
import it.isw2.prediction.model.Method;

import java.util.List;

public interface MethodRepository {

    List<Method> retrieveMethods() throws MethodRetrievalException, MethodSaveException;

    Method retrieveMethodByFullName(String fullName) throws MethodRetrievalException, MethodSaveException;

}
