package it.isw2.prediction.repository;

import it.isw2.prediction.model.Commit;
import it.isw2.prediction.model.Method;

import java.util.List;

public interface MethodRepository {

    public List<Method> retrieveMethods();

    public List<Method> retrieveModifiedMethods(Commit commit);

    public Method retrieveMethodByFullName(String fullName);

}
