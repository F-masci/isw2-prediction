package it.isw2.prediction.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.isw2.prediction.config.ApplicationConfig;
import it.isw2.prediction.exception.method.MethodRetrievalException;
import it.isw2.prediction.exception.method.MethodSaveException;
import it.isw2.prediction.factory.CommitRepositoryFactory;
import it.isw2.prediction.model.Commit;
import it.isw2.prediction.model.Method;
import it.isw2.prediction.model.Version;
import it.isw2.prediction.repository.CommitRepository;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.logging.Logger;

public class MethodDaoFile implements MethodDao {

    private final String selectedProject;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final Logger LOGGER = Logger.getLogger(MethodDaoFile.class.getName());

    public MethodDaoFile() {
        ApplicationConfig config = new ApplicationConfig();
        this.selectedProject = config.getSelectedProject().name();
    }

    /**
     * Salva i metodi nel filesystem nel path cache/{selectedProject}/methods/{className}/{methodName}
     */
    public void saveMethods(List<Method> methods) throws MethodSaveException {
        for (Method method : methods) {
            String classPath = "cache/" + selectedProject + "/methods/" + method.getClassName();
            String methodFilePath = classPath + "/" + method.getMethodName();
            Path methodFile = Paths.get(methodFilePath);
            try {
                Files.createDirectories(Paths.get(classPath));
                String json = mapper.writeValueAsString(method);
                Files.writeString(methodFile, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                LOGGER.info("Metodo salvato in cache: " + methodFilePath);
            } catch (IOException e) {
                throw new MethodSaveException("Errore durante il salvataggio del metodo: " + method.getFullName(), e);
            }
        }
        LOGGER.info("Cache dei metodi creata/aggiornata su filesystem per il progetto: " + selectedProject);
    }

    /**
     * Recupera tutti i metodi dal filesystem dal path cache/{selectedProject}/methods/{className}/{methodName}
     */
    @Override
    public List<Method> retrieveMethods() throws MethodRetrievalException {
        List<Method> methods = new java.util.ArrayList<>();
        String basePath = "cache/" + selectedProject + "/methods";
        Path baseDir = Paths.get(basePath);
        if (!Files.exists(baseDir) || !Files.isDirectory(baseDir)) {
            throw new MethodRetrievalException("Directory cache non trovata: " + basePath);
        }
        try (DirectoryStream<Path> classDirs = Files.newDirectoryStream(baseDir)) {
            for (Path classDir : classDirs) {
                if (!Files.isDirectory(classDir)) continue;
                try (DirectoryStream<Path> methodFiles = Files.newDirectoryStream(classDir)) {
                    for (Path methodFile : methodFiles) {
                        if (!Files.isRegularFile(methodFile)) continue;
                        try {
                            String json = Files.readString(methodFile);
                            Method method = mapper.readValue(json, Method.class);
                            methods.add(method);
                        } catch (IOException e) {
                            throw new MethodRetrievalException("Errore durante il recupero del metodo: " + methodFile.getFileName(), e);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new MethodRetrievalException("Errore durante la lettura della directory dei metodi", e);
        }
        LOGGER.info("Cache dei metodi letta da filesystem per il progetto: " + selectedProject);
        return methods;
    }

}
