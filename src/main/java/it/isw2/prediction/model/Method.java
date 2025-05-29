package it.isw2.prediction.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.javaparser.ast.body.MethodDeclaration;
import it.isw2.prediction.exception.ticket.TicketRetrievalException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.util.io.DisabledOutputStream;

public class Method {

    private final String methodName;
    private final String className;
    private final String packageName;

    private final List<Version> versions = new ArrayList<>();
    private Map<Commit, Integer> locPerCommit = new HashMap<>();
    private Map<Commit, Integer> cyclomaticComplexityPerCommit = new HashMap<>();
    private Map<Commit, Integer> cognitiveComplexityPerCommit = new HashMap<>();
    private Map<Version, Integer> methodHistoriesPerVersion = new HashMap<>();

    private Map<Commit, Integer> churnPerCommit = new HashMap<>();
    private Map<Commit, Integer> addedLinesPerCommit = new HashMap<>();
    private Map<Commit, Integer> deletedLinesPerCommit = new HashMap<>();

    private Map<Version, Boolean> buggyPerVersion = new HashMap<>();

    private Map<Commit, MethodInfo> methodInfoPerCommit = new HashMap<>();

    public Method(String className, String packageName, String methodName) {
        this.className = className;
        this.packageName = packageName;
        this.methodName = methodName;
    }

    public String getFullName() {
        return packageName + "." + className + "." + methodName;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getClassName() {
        return className;
    }

    public String getPackageName() {
        return packageName;
    }

    /* --- VERSIONS --- */

    public boolean isInVersion(Version version) {
        return this.versions.contains(version);
    }

    public List<Version> getVersions() {
        return versions;
    }

    /* --- PARSING --- */

    public void parseMethodDeclaration(Commit commit, MethodDeclaration methodDeclaration) throws TicketRetrievalException {
        this.versions.add(commit.getVersion());
        this.methodInfoPerCommit.put(commit, computeMethodInfo(methodDeclaration));
        this.locPerCommit.put(commit, computeLOC(methodDeclaration));
        this.cyclomaticComplexityPerCommit.put(commit, computeCyclomaticComplexity(methodDeclaration));
        this.cognitiveComplexityPerCommit.put(commit, computeCognitiveComplexity(methodDeclaration));
        this.methodHistoriesPerVersion.put(commit.getVersion(), this.methodHistoriesPerVersion.getOrDefault(commit.getVersion(), 0) + 1);
        this.computeIfBuggy(commit);
    }

    public void parseDiffEntry(Repository repository, Commit commit, DiffEntry diffEntry) {
        this.computeChurn(repository, commit, diffEntry);
    }

    /* --- MAPS --- */

    public Map<Commit, Integer> getLocPerCommit() {
        return locPerCommit;
    }

    public void setLocPerCommit(Map<Commit, Integer> locPerCommit) {
        this.locPerCommit = locPerCommit;
    }

    public Map<Commit, Integer> getCyclomaticComplexityPerCommit() {
        return cyclomaticComplexityPerCommit;
    }

    public void setCyclomaticComplexityPerCommit(Map<Commit, Integer> cyclomaticComplexityPerCommit) {
        this.cyclomaticComplexityPerCommit = cyclomaticComplexityPerCommit;
    }

    public Map<Commit, Integer> getCognitiveComplexityPerCommit() {
        return cognitiveComplexityPerCommit;
    }

    public void setCognitiveComplexityPerCommit(Map<Commit, Integer> cognitiveComplexityPerCommit) {
        this.cognitiveComplexityPerCommit = cognitiveComplexityPerCommit;
    }

    public Map<Version, Integer> getMethodHistoriesPerVersion() {
        return methodHistoriesPerVersion;
    }

    public void setMethodHistoriesPerVersion(Map<Version, Integer> methodHistoriesPerVersion) {
        this.methodHistoriesPerVersion = methodHistoriesPerVersion;
    }


    public Map<Commit, Integer> getChurnPerCommit() {
        return churnPerCommit;
    }

    public void setChurnPerCommit(Map<Commit, Integer> churnPerCommit) {
        this.churnPerCommit = churnPerCommit;
    }

    public Map<Commit, Integer> getAddedLinesPerCommit() {
        return addedLinesPerCommit;
    }

    public void setAddedLinesPerCommit(Map<Commit, Integer> addedLinesPerCommit) {
        this.addedLinesPerCommit = addedLinesPerCommit;
    }

    public Map<Commit, Integer> getDeletedLinesPerCommit() {
        return deletedLinesPerCommit;
    }

    public void setDeletedLinesPerCommit(Map<Commit, Integer> deletedLinesPerCommit) {
        this.deletedLinesPerCommit = deletedLinesPerCommit;
    }


    public Map<Version, Boolean> getBuggyPerVersion() {
        return buggyPerVersion;
    }

    public void setBuggyPerVersion(Map<Version, Boolean> buggyPerVersion) {
        this.buggyPerVersion = buggyPerVersion;
    }

    public Map<Commit, MethodInfo> getMethodInfoPerCommit() {
        return methodInfoPerCommit;
    }

    public void setMethodInfoPerCommit(Map<Commit, MethodInfo> methodInfoPerCommit) {
        this.methodInfoPerCommit = methodInfoPerCommit;
    }

    /* --- FEATURES --- */

    public int getLOC(Version version) {
        return getMetricForVersion(locPerCommit, version);
    }

    public int getCyclomaticComplexity(Version version) {
        return getMetricForVersion(cyclomaticComplexityPerCommit, version);
    }

    public int getCognitiveComplexity(Version version) {
        return getMetricForVersion(cognitiveComplexityPerCommit, version);
    }

    public int getMethodHistories(Version version) {
        return methodHistoriesPerVersion.getOrDefault(version, 0);
    }


    public int getChurn(Version version) {
        return getSumForVersion(churnPerCommit, version);
    }

    public int getAddedLines(Version version) {
        return getSumForVersion(addedLinesPerCommit, version);
    }

    public int getDeletedLines(Version version) {
        return getSumForVersion(deletedLinesPerCommit, version);
    }

    private int getSumForVersion(Map<Commit, Integer> metricMap, Version version) {
        int sum = 0;
        for (Map.Entry<Commit, Integer> entry : metricMap.entrySet()) {
            if (entry.getKey().getVersion().equals(version)) {
                sum += entry.getValue();
            }
        }
        return sum;
    }

    public boolean isBuggy(Version version) {
        return buggyPerVersion.getOrDefault(version, false);
    }


    private int getMetricForVersion(Map<Commit, Integer> metricMap, Version version) {
        Commit lastCommit = null;
        for (Commit commit : metricMap.keySet()) {
            if (commit.getVersion().equals(version)) {
                if (lastCommit == null || commit.getDate().after(lastCommit.getDate())) {
                    lastCommit = commit;
                }
            }
        }
        if (lastCommit != null) {
            return metricMap.get(lastCommit);
        }
        return 0;
    }

    private MethodInfo computeMethodInfo(MethodDeclaration methodDeclaration) {
        if (methodDeclaration.getBegin().isEmpty() || methodDeclaration.getEnd().isEmpty()) return null; // Non posso calcolare le linee se non ho i dati di inizio e fine
        int beginLine = methodDeclaration.getBegin().get().line;
        int endLine = methodDeclaration.getEnd().get().line;
        return new MethodInfo(beginLine, endLine);
    }

    private int computeLOC(MethodDeclaration methodDeclaration) {
        return methodDeclaration.getEnd().get().line - methodDeclaration.getBegin().get().line;
    }

    private int computeCyclomaticComplexity(MethodDeclaration methodDeclaration) {
        // Conta i nodi di decisione: if, for, while, case, catch, &&, ||
        int complexity = 1;
        String body = methodDeclaration.toString();
        complexity += countRegex(body, "\\bif\\b|\\bfor\\b|\\bwhile\\b|\\bcase\\b|\\bcatch\\b|&&|\\|\\|");
        return complexity;
    }

    private int computeCognitiveComplexity(MethodDeclaration methodDeclaration) {
        // Semplice: conta i livelli di annidamento di if, for, while, switch
        int complexity = 0;
        int nesting = 0;
        String[] lines = methodDeclaration.toString().split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.matches("(if|for|while|switch).*\\(.*\\).*") || trimmed.startsWith("case ")) {
                complexity += 1 + nesting;
                nesting++;
            }
            if (trimmed.equals("}")) {
                if (nesting > 0) nesting--;
            }
        }
        return complexity;
    }

    private int countRegex(String text, String regex) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher m = p.matcher(text);
        int count = 0;
        while (m.find()) count++;
        return count;
    }


    /**
     * Calcola il churn (linee aggiunte + rimosse) per un metodo in un commit specifico.
     *
     * @param repository Repository Git
     * @param commit     Commit in cui è avvenuta la modifica
     * @param diffEntry  DiffEntry che rappresenta le modifiche al file
     */
    public void computeChurn(Repository repository, Commit commit, DiffEntry diffEntry) {
        int addedLines = 0;
        int deletedLines = 0;

        // Recupera le informazioni sulle linee del metodo
        MethodInfo methodInfo = methodInfoPerCommit.get(commit);
        if (methodInfo == null) {
            // Se non ho le informazioni sul metodo per questo commit, non posso calcolare il churn
            return;
        }
        int beginLine = methodInfo.getBeginLine();
        int endLine = methodInfo.getEndLine();

        // Crea un formatter per analizzare le modifiche
        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            diffFormatter.setRepository(repository);
            diffFormatter.setContext(0); // Nessun contesto per ridurre il rumore

            // Ottieni il FileHeader per il DiffEntry
            FileHeader fileHeader = diffFormatter.toFileHeader(diffEntry);
            EditList edits = fileHeader.toEditList();

            // Filtra le modifiche rilevanti per il metodo
            for (Edit edit : edits) {
                // Calcolo linee aggiunte (nuova versione)
                if (rangesOverlap(edit.getBeginB(), edit.getEndB(), beginLine, endLine)) {
                    int overlapStart = Math.max(edit.getBeginB(), beginLine);
                    int overlapEnd = Math.min(edit.getEndB(), endLine);
                    addedLines += Math.max(0, overlapEnd - overlapStart);
                }

                // Calcolo linee eliminate (vecchia versione)
                if (rangesOverlap(edit.getBeginA(), edit.getEndA(), beginLine, endLine)) {
                    int overlapStart = Math.max(edit.getBeginA(), beginLine);
                    int overlapEnd = Math.min(edit.getEndA(), endLine);
                    deletedLines += Math.max(0, overlapEnd - overlapStart);
                }
            }

            // Salva i risultati nelle mappe
            addedLinesPerCommit.put(commit, addedLines);
            deletedLinesPerCommit.put(commit, deletedLines);
            churnPerCommit.put(commit, addedLines + deletedLines);

        } catch (IOException e) {
            // Log dell'errore
            java.util.logging.Logger.getLogger(Method.class.getName()).log(
                    java.util.logging.Level.SEVERE,
                    "Errore nel calcolo del churn per il metodo " + getFullName(), e);
        }
    }

    /**
     * Verifica se due intervalli di linee si sovrappongono
     */
    private boolean rangesOverlap(int start1, int end1, int start2, int end2) {
        return start1 <= end2 && end1 >= start2;
    }


    private void computeIfBuggy(Commit commit) throws TicketRetrievalException {
        List<Ticket> tickets = commit.getLinkedTickets();
        for (Ticket ticket : tickets) {
            List<Version> affectedVersions = ticket.getAffectedVersions();
            if (affectedVersions == null) continue;
            for (Version affectedVersion : affectedVersions) buggyPerVersion.put(affectedVersion, true);
        }
    }

    public record MethodInfo(int beginLine, int endLine) {
        public Integer getBeginLine() {
            return beginLine;
        }

        public Integer getEndLine() {
            return endLine;
        }
    }

    /* --- FORMATTER --- */

    @Override
    public String toString() {
        return "Method: " + getFullName();
    }

    /* --- EQUALS & HASHCODE --- */

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Method method = (Method) obj;
        return getFullName().equals(method.getFullName());
    }

    @Override
    public int hashCode() {
        return getFullName().hashCode();
    }
}

