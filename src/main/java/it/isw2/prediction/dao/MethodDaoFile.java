package it.isw2.prediction.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.isw2.prediction.config.ApplicationConfig;
import it.isw2.prediction.exception.method.MethodRetrievalException;
import it.isw2.prediction.exception.method.MethodSaveException;
import it.isw2.prediction.factory.CommitRepositoryFactory;
import it.isw2.prediction.factory.VersionRepositoryFactory;
import it.isw2.prediction.model.Commit;
import it.isw2.prediction.model.Method;
import it.isw2.prediction.model.Version;
import it.isw2.prediction.repository.CommitRepository;
import it.isw2.prediction.repository.VersionRepository;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class MethodDaoFile implements MethodDao {

    private final String selectedProject;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final Logger LOGGER = Logger.getLogger(MethodDaoFile.class.getName());

    public MethodDaoFile() {
        ApplicationConfig config = new ApplicationConfig();
        this.selectedProject = config.getSelectedProject().name();
    }

    /**
     * Salva i metodi nel filesystem nel path cache/{selectedProject}/methods/{className}/{methodName}/{commitHash}/
     * e salva in file separati le varie mappe delle features per commit.
     */
    public void saveMethods(List<Method> methods) throws MethodSaveException {
        for (Method method : methods) {
            String methodPath = Paths.get("cache", selectedProject, "methods", method.getPackageName(), method.getClassName(), method.getMethodName()).toString();

            try {
                Files.createDirectories(Paths.get(methodPath));

                Path methodInfoFile = Paths.get(methodPath, "info.json");
                Map<String, Object> basicInfo = Map.of(
                        "className", method.getClassName(),
                        "packageName", method.getPackageName(),
                        "methodName", method.getMethodName(),
                        "fullName", method.getFullName()
                );
                Files.writeString(methodInfoFile, mapper.writeValueAsString(basicInfo),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                Map<String, Object> allMaps = new HashMap<>();
                allMaps.put("versions", method.getVersions().stream().map(Version::getName).toList());

                // Funzione generica per tutte le mappe commit->valore
                addCommitMetricMap("locPerCommit", method.getLocPerCommit(), allMaps);
                addCommitMetricMap("statementPerCommit", method.getStatementPerCommit(), allMaps);
                addCommitMetricMap("cyclomaticComplexityPerCommit", method.getCyclomaticComplexityPerCommit(), allMaps);
                addCommitMetricMap("cognitiveComplexityPerCommit", method.getCognitiveComplexityPerCommit(), allMaps);
                addCommitMetricMap("churnPerCommit", method.getChurnPerCommit(), allMaps);
                addCommitMetricMap("addedLinesPerCommit", method.getAddedLinesPerCommit(), allMaps);
                addCommitMetricMap("deletedLinesPerCommit", method.getDeletedLinesPerCommit(), allMaps);
                addCommitMetricMap("branchPointsPerCommit", method.getBranchPointsPerCommit(), allMaps);
                addCommitMetricMap("nestingDepthPerCommit", method.getNestingDepthPerCommit(), allMaps);
                addCommitMetricMap("parametersCountPerCommit", method.getParametersCountPerCommit(), allMaps);

                // Mappe version->valore
                allMaps.put("methodHistoriesPerVersion", method.getVersions().stream()
                        .collect(Collectors.toMap(Version::getName, method::getMethodHistories)));
                allMaps.put("buggyPerVersion", method.getVersions().stream()
                        .collect(Collectors.toMap(Version::getName, method::isBuggy)));

                // methodInfoPerCommit
                Map<String, Object> methodInfoMap = new HashMap<>();
                for (Map.Entry<Commit, Method.MethodInfo> entry : method.getMethodInfoPerCommit().entrySet()) {
                    Method.MethodInfo info = entry.getValue();
                    if (info != null) {
                        methodInfoMap.put(entry.getKey().getId(), Map.of(
                                "beginLine", info.getBeginLine(),
                                "endLine", info.getEndLine()
                        ));
                    }
                }
                allMaps.put("methodInfoPerCommit", methodInfoMap);

                Path mapsFile = Paths.get(methodPath, "maps.json");
                Files.writeString(mapsFile, mapper.writeValueAsString(allMaps),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                LOGGER.info("Metodo salvato in cache: " + method.getFullName());

            } catch (IOException e) {
                throw new MethodSaveException("Errore durante il salvataggio del metodo: " + method.getFullName(), e);
            }
        }

        LOGGER.info(() -> "Cache dei metodi creata/aggiornata su filesystem per il progetto: " + selectedProject);
    }

    // Funzione generica per aggiungere una mappa commit->valore
    private <T> void addCommitMetricMap(String mapName, Map<Commit, T> metricMap, Map<String, Object> allMaps) {
        Map<String, T> out = new HashMap<>();
        for (Map.Entry<Commit, T> entry : metricMap.entrySet()) {
            out.put(entry.getKey().getId(), entry.getValue());
        }
        allMaps.put(mapName, out);
    }

    /**
     * Recupera tutti i metodi dal filesystem dal path cache/{selectedProject}/methods/{className}/{methodName}
     */
    @Override
    public List<Method> retrieveMethods() throws MethodRetrievalException {
        List<Method> methods = new ArrayList<>();
        String basePath = "cache/" + selectedProject + "/methods";
        Path baseDir = Paths.get(basePath);
        if (!Files.exists(baseDir) || !Files.isDirectory(baseDir)) {
            throw new MethodRetrievalException("Directory cache non trovata: " + basePath);
        }
        CommitRepository commitRepository = CommitRepositoryFactory.getInstance().getCommitRepository();
        VersionRepository versionRepository = VersionRepositoryFactory.getInstance().getVersionRepository();

        try (DirectoryStream<Path> packageDirs = Files.newDirectoryStream(baseDir)) {
            for (Path packageDir : packageDirs) {
                if (!Files.isDirectory(packageDir)) continue;
                try (DirectoryStream<Path> classDirs = Files.newDirectoryStream(packageDir)) {
                    for (Path classDir : classDirs) {
                        if (!Files.isDirectory(classDir)) continue;
                        try (DirectoryStream<Path> methodDirs = Files.newDirectoryStream(classDir)) {
                            for (Path methodDir : methodDirs) {
                                if (!Files.isDirectory(methodDir)) continue;
                                Path infoFile = methodDir.resolve("info.json");
                                Path mapsFile = methodDir.resolve("maps.json");
                                if (!Files.exists(infoFile) || !Files.exists(mapsFile)) continue;
                                Method method = retrieveSingleMethod(infoFile, mapsFile, commitRepository, versionRepository);
                                if (method != null) methods.add(method);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new MethodRetrievalException("Errore durante la lettura della directory dei metodi", e);
        }
        LOGGER.info(() -> "Cache dei metodi letta da filesystem per il progetto: " + selectedProject);
        return methods;
    }

    private Method retrieveSingleMethod(Path infoFile, Path mapsFile, CommitRepository commitRepository, VersionRepository versionRepository) throws MethodRetrievalException {
        try {
            Map<String, Object> info = mapper.readValue(Files.readString(infoFile), Map.class);
            String className = (String) info.get("className");
            String packageName = (String) info.get("packageName");
            String methodName = (String) info.get("methodName");
            Method method = new Method(className, packageName, methodName);

            Map<String, Object> maps = mapper.readValue(Files.readString(mapsFile), Map.class);

            // Ricostruisci versions (usa NOME)
            List<String> versionNames = (List<String>) maps.get("versions");
            List<Version> versions = new ArrayList<>();
            for (String vName : versionNames) {
                Version v = versionRepository.retrieveVersionByName(vName);
                if (v != null) versions.add(v);
            }
            method.getVersions().addAll(versions);

            // Mappe commit→Integer
            setCommitIntMap(maps, "locPerCommit", method::setLocPerCommit, commitRepository);
            setCommitIntMap(maps, "statementPerCommit", method::setStatementPerCommit, commitRepository);
            setCommitIntMap(maps, "cyclomaticComplexityPerCommit", method::setCyclomaticComplexityPerCommit, commitRepository);
            setCommitIntMap(maps, "cognitiveComplexityPerCommit", method::setCognitiveComplexityPerCommit, commitRepository);
            setCommitIntMap(maps, "churnPerCommit", method::setChurnPerCommit, commitRepository);
            setCommitIntMap(maps, "addedLinesPerCommit", method::setAddedLinesPerCommit, commitRepository);
            setCommitIntMap(maps, "deletedLinesPerCommit", method::setDeletedLinesPerCommit, commitRepository);
            setCommitIntMap(maps, "branchPointsPerCommit", method::setBranchPointsPerCommit, commitRepository);
            setCommitIntMap(maps, "nestingDepthPerCommit", method::setNestingDepthPerCommit, commitRepository);
            setCommitIntMap(maps, "parametersCountPerCommit", method::setParametersCountPerCommit, commitRepository);

            // Mappe version→Integer
            setVersionIntMap(maps, "methodHistoriesPerVersion", method::setMethodHistoriesPerVersion, versionRepository);

            // Mappe version→Boolean
            setVersionBoolMap(maps, "buggyPerVersion", method::setBuggyPerVersion, versionRepository);

            // methodInfoPerCommit
            setMethodInfoMap(maps, "methodInfoPerCommit", method, commitRepository);

            return method;
        } catch (IOException e) {
            throw new MethodRetrievalException("Errore durante il recupero del metodo: " + infoFile.getParent().getFileName(), e);
        }
    }

    // Funzione generica per mappe commit→Integer
    private void setCommitIntMap(Map<String, Object> maps, String mapName, Consumer<Map<Commit, Integer>> setter, CommitRepository commitRepository) {
        Map<String, Integer> map = (Map<String, Integer>) maps.get(mapName);
        if (map == null) return;
        Map<Commit, Integer> result = new HashMap<>();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            Commit commit = commitRepository.retrieveCommitById(entry.getKey());
            if (commit != null) result.put(commit, entry.getValue());
        }
        setter.accept(result);
    }

    // Funzione generica per mappe version→Integer
    private void setVersionIntMap(Map<String, Object> maps, String mapName, Consumer<Map<Version, Integer>> setter, VersionRepository versionRepository) {
        Map<String, Integer> map = (Map<String, Integer>) maps.get(mapName);
        if (map == null) return;
        Map<Version, Integer> result = new HashMap<>();
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            Version v = versionRepository.retrieveVersionByName(entry.getKey());
            if (v != null) result.put(v, entry.getValue());
        }
        setter.accept(result);
    }

    // Funzione generica per mappe version→Boolean
    private void setVersionBoolMap(Map<String, Object> maps, String mapName, Consumer<Map<Version, Boolean>> setter, VersionRepository versionRepository) {
        Map<String, Boolean> map = (Map<String, Boolean>) maps.get(mapName);
        if (map == null) return;
        Map<Version, Boolean> result = new HashMap<>();
        for (Map.Entry<String, Boolean> entry : map.entrySet()) {
            Version v = versionRepository.retrieveVersionByName(entry.getKey());
            if (v != null) result.put(v, entry.getValue());
        }
        setter.accept(result);
    }

    // Funzione per methodInfoPerCommit
    private void setMethodInfoMap(Map<String, Object> maps, String mapName, Method method, CommitRepository commitRepository) {
        Map<String, Map<String, Object>> methodInfoMap = (Map<String, Map<String, Object>>) maps.get(mapName);
        if (methodInfoMap == null) return;
        for (Map.Entry<String, Map<String, Object>> entry : methodInfoMap.entrySet()) {
            Commit commit = commitRepository.retrieveCommitById(entry.getKey());
            if (commit != null) {
                Map<String, Object> infoMap = entry.getValue();
                int beginLine = (Integer) infoMap.get("beginLine");
                int endLine = (Integer) infoMap.get("endLine");
                method.getMethodInfoPerCommit().put(commit, new Method.MethodInfo(beginLine, endLine));
            }
        }
    }

}
