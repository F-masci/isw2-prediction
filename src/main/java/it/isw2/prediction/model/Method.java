package it.isw2.prediction.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.javaparser.ast.body.MethodDeclaration;
import it.isw2.prediction.exception.ticket.TicketRetrievalException;

public class Method {

    private final String methodName;
    private final String className;
    private final String packageName;

    private List<Version> versions = new ArrayList<>();
    private Map<Commit, Integer> locPerCommit = new HashMap<>();
    private Map<Commit, Integer> cyclomaticComplexityPerCommit = new HashMap<>();
    private Map<Commit, Integer> cognitiveComplexityPerCommit = new HashMap<>();
    private Map<Version, Integer> methodHistoriesPerVersion = new HashMap<>();

    private Map<Version, Boolean> buggyPerVersion = new HashMap<>();

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

    /* --- BODY --- */

    public void parseMethodDeclaration(Commit commit, MethodDeclaration methodDeclaration) throws TicketRetrievalException {
        this.versions.add(commit.getVersion());
        this.locPerCommit.put(commit, computeLOC(methodDeclaration));
        this.cyclomaticComplexityPerCommit.put(commit, computeCyclomaticComplexity(methodDeclaration));
        this.cognitiveComplexityPerCommit.put(commit, computeCognitiveComplexity(methodDeclaration));
        this.methodHistoriesPerVersion.put(commit.getVersion(), this.methodHistoriesPerVersion.getOrDefault(commit.getVersion(), 0) + 1);
        this.computeIfBuggy(commit);
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


    private int computeLOC(MethodDeclaration methodDeclaration) {
        return methodDeclaration.getEnd().get().line - methodDeclaration.getBegin().get().line + 1;
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

    private void computeIfBuggy(Commit commit) throws TicketRetrievalException {
        List<Ticket> tickets = commit.getLinkedTickets();
        for (Ticket ticket : tickets) {
            List<Version> affectedVersions = ticket.getAffectedVersions();
            if(affectedVersions == null) continue;
            for (Version affectedVersion : affectedVersions) buggyPerVersion.put(affectedVersion, true);
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
