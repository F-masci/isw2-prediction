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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
     * Salva i metodi nel filesystem nel path cache/{selectedProject}/methods/{className}/{methodName}/{commitHash}/
     * e salva in file separati le varie mappe delle features per commit.
     */
    public void saveMethods(List<Method> methods) throws MethodSaveException {
        for (Method method : methods) {
            String methodPath = "cache/" + selectedProject + "/methods/" + method.getPackageName() + "/" + method.getClassName() + "/" + method.getMethodName();

            try {
                // Crea la directory del metodo
                Files.createDirectories(Paths.get(methodPath));

                // Salva le informazioni di base del metodo
                Path methodInfoFile = Paths.get(methodPath, "info.json");
                Map<String, Object> basicInfo = Map.of(
                        "className", method.getClassName(),
                        "packageName", method.getPackageName(),
                        "methodName", method.getMethodName(),
                        "fullName", method.getFullName()
                );
                Files.writeString(methodInfoFile, mapper.writeValueAsString(basicInfo),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                // Crea un oggetto JSON che contiene tutte le mappe
                Map<String, Object> allMaps = new HashMap<>();

                // Aggiungi mappa delle versioni (usa NOME)
                List<String> versionNames = method.getVersions().stream()
                        .map(Version::getName)
                        .toList();
                allMaps.put("versions", versionNames);

                // Aggiungi mappa LOC per commit
                Map<String, Integer> locMap = new HashMap<>();
                for (Map.Entry<Commit, Integer> entry : method.getLocPerCommit().entrySet()) {
                    locMap.put(entry.getKey().getId(), entry.getValue());
                }
                allMaps.put("locPerCommit", locMap);

                // Aggiungi mappa complessità ciclomatica per commit
                Map<String, Integer> ccMap = new HashMap<>();
                for (Map.Entry<Commit, Integer> entry : method.getCyclomaticComplexityPerCommit().entrySet()) {
                    ccMap.put(entry.getKey().getId(), entry.getValue());
                }
                allMaps.put("cyclomaticComplexityPerCommit", ccMap);

                // Aggiungi mappa complessità cognitiva per commit
                Map<String, Integer> cogMap = new HashMap<>();
                for (Map.Entry<Commit, Integer> entry : method.getCognitiveComplexityPerCommit().entrySet()) {
                    cogMap.put(entry.getKey().getId(), entry.getValue());
                }
                allMaps.put("cognitiveComplexityPerCommit", cogMap);

                // Aggiungi mappa histories per versione (usa NOME)
                Map<String, Integer> historiesMap = new HashMap<>();
                for (Version version : method.getVersions()) {
                    historiesMap.put(version.getName(), method.getMethodHistories(version));
                }
                allMaps.put("methodHistoriesPerVersion", historiesMap);

                // Aggiungi mappa buggy per versione (usa NOME)
                Map<String, Boolean> buggyMap = new HashMap<>();
                for (Version version : method.getVersions()) {
                    buggyMap.put(version.getName(), method.isBuggy(version));
                }
                allMaps.put("buggyPerVersion", buggyMap);

                // Aggiungi mappa MethodInfo per commit
                Map<String, Object> methodInfoMap = new HashMap<>();
                for (Map.Entry<Commit, Method.MethodInfo> entry : method.getMethodInfoPerCommit().entrySet()) {
                    Method.MethodInfo info = entry.getValue();
                    methodInfoMap.put(entry.getKey().getId(), Map.of(
                            "beginLine", info.getBeginLine(),
                            "endLine", info.getEndLine()
                    ));
                }
                allMaps.put("methodInfoPerCommit", methodInfoMap);

                // --- NUOVE MAPPE ---
                // churnPerCommit
                Map<String, Integer> churnMap = new HashMap<>();
                for (Map.Entry<Commit, Integer> entry : method.getChurnPerCommit().entrySet()) {
                    churnMap.put(entry.getKey().getId(), entry.getValue());
                }
                allMaps.put("churnPerCommit", churnMap);

                // addedLinesPerCommit
                Map<String, Integer> addedLinesMap = new HashMap<>();
                for (Map.Entry<Commit, Integer> entry : method.getAddedLinesPerCommit().entrySet()) {
                    addedLinesMap.put(entry.getKey().getId(), entry.getValue());
                }
                allMaps.put("addedLinesPerCommit", addedLinesMap);

                // deletedLinesPerCommit
                Map<String, Integer> deletedLinesMap = new HashMap<>();
                for (Map.Entry<Commit, Integer> entry : method.getDeletedLinesPerCommit().entrySet()) {
                    deletedLinesMap.put(entry.getKey().getId(), entry.getValue());
                }
                allMaps.put("deletedLinesPerCommit", deletedLinesMap);

                // Salva tutte le mappe in un unico file JSON
                Path mapsFile = Paths.get(methodPath, "maps.json");
                Files.writeString(mapsFile, mapper.writeValueAsString(allMaps),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                LOGGER.info("Metodo salvato in cache: " + method.getFullName());

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
                                try {
                                    // Leggi info.json
                                    Map<String, Object> info = mapper.readValue(Files.readString(infoFile), Map.class);
                                    String className = (String) info.get("className");
                                    String packageName = (String) info.get("packageName");
                                    String methodName = (String) info.get("methodName");
                                    Method method = new Method(className, packageName, methodName);

                                    // Leggi maps.json
                                    Map<String, Object> maps = mapper.readValue(Files.readString(mapsFile), Map.class);

                                    // Ricostruisci versions (usa NOME)
                                    List<String> versionNames = (List<String>) maps.get("versions");
                                    List<Version> versions = new java.util.ArrayList<>();
                                    for (String vName : versionNames) {
                                        Version v = versionRepository.retrieveVersionByName(vName);
                                        if (v != null) versions.add(v);
                                    }
                                    method.getVersions().addAll(versions);

                                    // Ricostruisci locPerCommit
                                    Map<String, Integer> locMap = (Map<String, Integer>) maps.get("locPerCommit");
                                    Map<Commit, Integer> locPerCommit = new HashMap<>();
                                    if (locMap != null) {
                                        for (Map.Entry<String, Integer> entry : locMap.entrySet()) {
                                            Commit commit = commitRepository.retrieveCommitById(entry.getKey());
                                            if (commit != null) locPerCommit.put(commit, entry.getValue());
                                        }
                                        method.setLocPerCommit(locPerCommit);
                                    }

                                    // Ricostruisci cyclomaticComplexityPerCommit
                                    Map<String, Integer> ccMap = (Map<String, Integer>) maps.get("cyclomaticComplexityPerCommit");
                                    Map<Commit, Integer> ccPerCommit = new HashMap<>();
                                    if (ccMap != null) {
                                        for (Map.Entry<String, Integer> entry : ccMap.entrySet()) {
                                            Commit commit = commitRepository.retrieveCommitById(entry.getKey());
                                            if (commit != null) ccPerCommit.put(commit, entry.getValue());
                                        }
                                        method.setCyclomaticComplexityPerCommit(ccPerCommit);
                                    }

                                    // Ricostruisci cognitiveComplexityPerCommit
                                    Map<String, Integer> cogMap = (Map<String, Integer>) maps.get("cognitiveComplexityPerCommit");
                                    Map<Commit, Integer> cogPerCommit = new HashMap<>();
                                    if (cogMap != null) {
                                        for (Map.Entry<String, Integer> entry : cogMap.entrySet()) {
                                            Commit commit = commitRepository.retrieveCommitById(entry.getKey());
                                            if (commit != null) cogPerCommit.put(commit, entry.getValue());
                                        }
                                        method.setCognitiveComplexityPerCommit(cogPerCommit);
                                    }

                                    // Ricostruisci methodHistoriesPerVersion (usa NOME)
                                    Map<String, Integer> historiesMap = (Map<String, Integer>) maps.get("methodHistoriesPerVersion");
                                    Map<Version, Integer> historiesPerVersion = new HashMap<>();
                                    if (historiesMap != null) {
                                        for (Map.Entry<String, Integer> entry : historiesMap.entrySet()) {
                                            Version v = versionRepository.retrieveVersionByName(entry.getKey());
                                            if (v != null) historiesPerVersion.put(v, entry.getValue());
                                        }
                                        method.setMethodHistoriesPerVersion(historiesPerVersion);
                                    }

                                    // Ricostruisci buggyPerVersion (usa NOME)
                                    Map<String, Boolean> buggyMap = (Map<String, Boolean>) maps.get("buggyPerVersion");
                                    Map<Version, Boolean> buggyPerVersion = new HashMap<>();
                                    if (buggyMap != null) {
                                        for (Map.Entry<String, Boolean> entry : buggyMap.entrySet()) {
                                            Version v = versionRepository.retrieveVersionByName(entry.getKey());
                                            if (v != null) buggyPerVersion.put(v, entry.getValue());
                                        }
                                        method.setBuggyPerVersion(buggyPerVersion);
                                    }

                                    // Ricostruisci methodInfoPerCommit
                                    Map<String, Map<String, Object>> methodInfoMap = (Map<String, Map<String, Object>>) maps.get("methodInfoPerCommit");
                                    if (methodInfoMap != null) {
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

                                    // --- NUOVE MAPPE ---
                                    // churnPerCommit
                                    Map<String, Integer> churnMap = (Map<String, Integer>) maps.get("churnPerCommit");
                                    Map<Commit, Integer> churnPerCommit = new HashMap<>();
                                    if (churnMap != null) {
                                        for (Map.Entry<String, Integer> entry : churnMap.entrySet()) {
                                            Commit commit = commitRepository.retrieveCommitById(entry.getKey());
                                            if (commit != null) churnPerCommit.put(commit, entry.getValue());
                                        }
                                        method.setChurnPerCommit(churnPerCommit);
                                    }

                                    // addedLinesPerCommit
                                    Map<String, Integer> addedLinesMap = (Map<String, Integer>) maps.get("addedLinesPerCommit");
                                    Map<Commit, Integer> addedLinesPerCommit = new HashMap<>();
                                    if (addedLinesMap != null) {
                                        for (Map.Entry<String, Integer> entry : addedLinesMap.entrySet()) {
                                            Commit commit = commitRepository.retrieveCommitById(entry.getKey());
                                            if (commit != null) addedLinesPerCommit.put(commit, entry.getValue());
                                        }
                                        method.setAddedLinesPerCommit(addedLinesPerCommit);
                                    }

                                    // deletedLinesPerCommit
                                    Map<String, Integer> deletedLinesMap = (Map<String, Integer>) maps.get("deletedLinesPerCommit");
                                    Map<Commit, Integer> deletedLinesPerCommit = new HashMap<>();
                                    if (deletedLinesMap != null) {
                                        for (Map.Entry<String, Integer> entry : deletedLinesMap.entrySet()) {
                                            Commit commit = commitRepository.retrieveCommitById(entry.getKey());
                                            if (commit != null) deletedLinesPerCommit.put(commit, entry.getValue());
                                        }
                                        method.setDeletedLinesPerCommit(deletedLinesPerCommit);
                                    }

                                    methods.add(method);
                                } catch (IOException e) {
                                    throw new MethodRetrievalException("Errore durante il recupero del metodo: " + methodDir.getFileName(), e);
                                }
                            }
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
