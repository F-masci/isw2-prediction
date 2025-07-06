package it.isw2.prediction.model;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import it.isw2.prediction.config.ApplicationConfig;
import it.isw2.prediction.exception.ticket.TicketRetrievalException;
import it.isw2.prediction.factory.VersionRepositoryFactory;
import it.isw2.prediction.repository.VersionRepository;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Method {

    private static final Logger LOGGER = Logger.getLogger(Method.class.getName());

    private final String methodName;
    private final String className;
    private final String packageName;

    private final List<Version> versions = new ArrayList<>();
    private Commit deleteCommit = null; // Commit in cui il metodo è stato eliminato

    private Map<Commit, Integer> locPerCommit = new HashMap<>();
    private Map<Commit, Integer> statementPerCommit = new HashMap<>();
    private Map<Commit, Integer> cyclomaticComplexityPerCommit = new HashMap<>();
    private Map<Commit, Integer> cognitiveComplexityPerCommit = new HashMap<>();
    private Map<Version, Integer> methodHistoriesPerVersion = new HashMap<>();

    private Map<Commit, Integer> churnPerCommit = new HashMap<>();
    private Map<Commit, Integer> addedLinesPerCommit = new HashMap<>();
    private Map<Commit, Integer> deletedLinesPerCommit = new HashMap<>();

    private Map<Version, Boolean> buggyPerVersion = new HashMap<>();

    private Map<Commit, MethodInfo> methodInfoPerCommit = new HashMap<>();

    // Nuove mappe per branch/decision points, nesting depth e numero di parametri
    private Map<Commit, Integer> branchPointsPerCommit = new HashMap<>();
    private Map<Commit, Integer> nestingDepthPerCommit = new HashMap<>();
    private Map<Commit, Integer> parametersCountPerCommit = new HashMap<>();

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

    public Commit getDeleteCommit() {
        return deleteCommit;
    }

    /* --- VERSIONS --- */

    public boolean isInVersion(Version version) {
        return this.versions.contains(version);
    }

    public List<Version> getVersions() {
        return versions;
    }

    /**
     * Aggiunge una versione al metodo.
     * Se la versione è già presente, non fa nulla.
     * Se non ci sono versioni precedenti, aggiunge solo la nuova versione.
     * Se la nuova versione è precedente all'ultima, aggiunge solo la nuova versione.
     * Altrimenti, aggiunge tutte le versioni intermedie fino alla nuova versione.
     *
     * @param version La versione da aggiungere
     */
    public void addVersion(Version version) {

        // Se la versione è già presente, non fare nulla
        if (this.versions.contains(version)) return;

        ApplicationConfig applicationConfig = new ApplicationConfig();

        // Se non ci sono versioni o se la funzionalità di tutte le versioni è disabilitata, aggiungi solamente la versione
        if (this.versions.isEmpty() || !applicationConfig.isMethodAllVersionEnabled()) {
            this.versions.add(version);
            return;
        }

        Version lastVersion = findLastVersion();

        if (version.getReleaseDate().before(lastVersion.getReleaseDate())) {
            this.versions.add(version);
        } else {
            addIntermediateVersions(lastVersion, version);
        }
    }

    /**
     * Trova l'ultima versione (più recente) tra quelle attualmente presenti.
     *
     * @return l'ultima versione per data di rilascio
     */
    private Version findLastVersion() {
        Version lastVersion = null;
        for (Version v : this.versions) {
            if (lastVersion == null || v.getReleaseDate().after(lastVersion.getReleaseDate())) {
                lastVersion = v;
            }
        }
        return lastVersion;
    }

    /**
     * Aggiunge tutte le versioni intermedie tra lastVersion e targetVersion.
     *
     * @param lastVersion la versione di partenza (già presente)
     * @param targetVersion la versione di destinazione da aggiungere
     */
    private void addIntermediateVersions(Version lastVersion, Version targetVersion) {
        // Ottieni repository delle versioni
        VersionRepository versionRepository = VersionRepositoryFactory.getInstance().getVersionRepository();
        List<Version> allVersions = versionRepository.retrieveVersions();

        // Ordina tutte le versioni per data
        allVersions.sort(Comparator.comparing(Version::getReleaseDate));

        // Prepara le variabili per l'elaborazione
        boolean startAdding = false;
        boolean targetReached = false;

        // Aggiungi tutte le versioni intermedie
        for (Version v : allVersions) {
            if (!startAdding && v.equals(lastVersion)) {
                startAdding = true;
            } else if (startAdding && !targetReached) {
                boolean isVersionToAdd = v.getReleaseDate().before(targetVersion.getReleaseDate()) || v.equals(targetVersion);

                if (isVersionToAdd && !this.versions.contains(v)) {
                    this.versions.add(v);
                }

                targetReached = v.equals(targetVersion);
            }
        }
    }

    /* --- PARSING --- */

    public void parseMethodDeclaration(Commit commit, MethodDeclaration methodDeclaration) throws TicketRetrievalException {
        this.addVersion(commit.getVersion());
        boolean deleted = false;
        if(methodDeclaration == null) {
            deleted = true;
            this.deleteCommit = commit;

            // Se il metodo è stato eliminato, calcola il churn come le loc del metodo
            Version version = commit.getVersion();
            int loc = this.getLOC(version);
            deletedLinesPerCommit.put(commit, loc + deletedLinesPerCommit.getOrDefault(commit, 0));
            churnPerCommit.put(commit, loc + churnPerCommit.getOrDefault(commit, 0));

        }
        this.methodInfoPerCommit.put(commit, deleted ? null : computeMethodInfo(methodDeclaration));
        this.locPerCommit.put(commit, deleted ? 0 : computeLOC(methodDeclaration));
        this.statementPerCommit.put(commit, deleted ? 0 : computeStatement(methodDeclaration));
        this.cyclomaticComplexityPerCommit.put(commit, deleted ? 0 : computeCyclomaticComplexity(methodDeclaration));
        this.cognitiveComplexityPerCommit.put(commit, deleted ? 0 : computeCognitiveComplexity(methodDeclaration));
        this.branchPointsPerCommit.put(commit, deleted ? 0 : computeBranchPoints(methodDeclaration));
        this.nestingDepthPerCommit.put(commit, deleted ? 0 : computeNestingDepth(methodDeclaration));
        this.parametersCountPerCommit.put(commit, deleted ? 0 : computeParametersCount(methodDeclaration));
        this.methodHistoriesPerVersion.put(commit.getVersion(), computeMethodHistories(commit.getVersion()));
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

    public Map<Commit, Integer> getStatementPerCommit() {
        return statementPerCommit;
    }

    public void setStatementPerCommit(Map<Commit, Integer> statementPerCommit) {
        this.statementPerCommit = statementPerCommit;
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

    public Map<Commit, Integer> getBranchPointsPerCommit() {
        return branchPointsPerCommit;
    }

    public void setBranchPointsPerCommit(Map<Commit, Integer> branchPointsPerCommit) {
        this.branchPointsPerCommit = branchPointsPerCommit;
    }

    public Map<Commit, Integer> getNestingDepthPerCommit() {
        return nestingDepthPerCommit;
    }

    public void setNestingDepthPerCommit(Map<Commit, Integer> nestingDepthPerCommit) {
        this.nestingDepthPerCommit = nestingDepthPerCommit;
    }

    public Map<Commit, Integer> getParametersCountPerCommit() {
        return parametersCountPerCommit;
    }

    public void setParametersCountPerCommit(Map<Commit, Integer> parametersCountPerCommit) {
        this.parametersCountPerCommit = parametersCountPerCommit;
    }

    /* --- FEATURES --- */

    public int getLOC(Version version) {
        return getMetricForVersion(locPerCommit, version);
    }

    public int getStatement(Version version) {
        return getMetricForVersion(statementPerCommit, version);
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

    public int getBranchPoints(Version version) {
        return getMetricForVersion(branchPointsPerCommit, version);
    }

    public int getNestingDepth(Version version) {
        return getMetricForVersion(nestingDepthPerCommit, version);
    }

    public int getParametersCount(Version version) {
        return getMetricForVersion(parametersCountPerCommit, version);
    }

    public int getMaxChurn(Version version) {
        return getMaxForVersion(churnPerCommit, version);
    }

    public double getAvgChurn(Version version) {
        return getAvgForVersion(churnPerCommit, version);
    }

    public int getMaxAddedLines(Version version) {
        return getMaxForVersion(addedLinesPerCommit, version);
    }

    public double getAvgAddedLines(Version version) {
        return getAvgForVersion(addedLinesPerCommit, version);
    }

    public int getMaxDeletedLines(Version version) {
        return getMaxForVersion(deletedLinesPerCommit, version);
    }

    public double getAvgDeletedLines(Version version) {
        return getAvgForVersion(deletedLinesPerCommit, version);
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

    private int getMaxForVersion(Map<Commit, Integer> metricMap, Version version) {
        int max = 0;
        for (Map.Entry<Commit, Integer> entry : metricMap.entrySet()) {
            if (entry.getKey().getVersion().equals(version)) max = Math.max(max, entry.getValue());
        }
        return max;
    }

    private double getAvgForVersion(Map<Commit, Integer> metricMap, Version version) {
        int sum = 0, count = 0;
        for (Map.Entry<Commit, Integer> entry : metricMap.entrySet()) {
            if (entry.getKey().getVersion().equals(version)) {
                sum += entry.getValue();
                count++;
            }
        }
        return count > 0 ? (double) sum / count : 0.0;
    }

    public boolean isBuggy(Version version) {
        return buggyPerVersion.getOrDefault(version, false);
    }


    private int getMetricForVersion(Map<Commit, Integer> metricMap, Version version) {
        Commit lastCommit = null;

        // Trova l'ultimo commit che precede la data di rilascio della versione
        for (Commit commit : metricMap.keySet()) {
            if (commit.getDate().before(version.getReleaseDate()) && (lastCommit == null || commit.getDate().after(lastCommit.getDate()))) {
                lastCommit = commit;
            }
        }

        if (lastCommit != null) return metricMap.get(lastCommit);
        return 0;
    }

    private MethodInfo computeMethodInfo(MethodDeclaration methodDeclaration) {
        if (methodDeclaration.getBegin().isEmpty() || methodDeclaration.getEnd().isEmpty()) return null; // Non posso calcolare le linee se non ho i dati di inizio e fine
        int beginLine = methodDeclaration.getBegin().get().line;
        int endLine = methodDeclaration.getEnd().get().line;
        return new MethodInfo(beginLine, endLine);
    }

    private int computeLOC(MethodDeclaration methodDeclaration) {
        return computePureLOC(methodDeclaration);
    }

    /**
     * Calcola le linee di codice complete (LOC) per il metodo.
     * Include tutte le linee tra l'inizio e la fine del metodo, indipendentemente da commenti o spazi vuoti.
     */
    private int computeCompleteLOC(MethodDeclaration methodDeclaration) {
        return methodDeclaration.getEnd().get().line - methodDeclaration.getBegin().get().line;
    }

    /**
     * Calcola le linee di codice "pure" (LOC) per il metodo.
     * Esclude commenti e spazi vuoti, ma include le linee di codice effettivo.
     */
    private int computePureLOC(MethodDeclaration methodDeclaration) {
        if (methodDeclaration.getBody().isEmpty()) return 0;

        BlockStmt body = methodDeclaration.getBody().get();
        String[] lines = body.toString().split("\\R"); // supporta \n e \r\n
        int count = 0;
        boolean inBlockComment = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (inBlockComment) {
                if (trimmed.contains("*/")) inBlockComment = false;
                continue;
            }

            if (trimmed.isEmpty()
                    || trimmed.startsWith("//")
                    || trimmed.startsWith("*")) {
                continue;
            }

            if (trimmed.startsWith("/*")) {
                if (!trimmed.contains("*/")) inBlockComment = true;
                continue;
            }

            // Salta le righe che contengono solo una parentesi graffa
            if (trimmed.equals("{") || trimmed.equals("}")) continue;

            count++;
        }

        return count;
    }

    /**
     * Conta il numero totale di istruzioni nel metodo.
     */
    public int computeStatement(MethodDeclaration methodDeclaration) {
        if (methodDeclaration.getBody().isEmpty()) return 0;

        BlockStmt body = methodDeclaration.getBody().get();

        // Contiamo solo gli Statement diretti o annidati reali
        List<Statement> statements = body.findAll(Statement.class).stream()
                .filter(stmt -> !(stmt instanceof BlockStmt)) // Escludi blocchi vuoti
                .collect(Collectors.toList());

        return statements.size();
    }

    /**
     * Calcola la complessità ciclomatica per il metodo.
     * Conta le strutture di controllo come if, for, while, switch e operatori logici.
     */
    private int computeCyclomaticComplexity(MethodDeclaration methodDeclaration) {
        CyclomaticComplexityVisitor visitor = new CyclomaticComplexityVisitor();
        visitor.visit(methodDeclaration, null);
        return visitor.getComplexity();
    }

    private static class CyclomaticComplexityVisitor extends VoidVisitorAdapter<Void> {
        private int complexity = 1; // base

        public int getComplexity() {
            return complexity;
        }

        @Override public void visit(IfStmt n, Void arg) { complexity++; super.visit(n, arg); }
        @Override public void visit(ForStmt n, Void arg) { complexity++; super.visit(n, arg); }
        @Override public void visit(ForEachStmt n, Void arg) { complexity++; super.visit(n, arg); }
        @Override public void visit(WhileStmt n, Void arg) { complexity++; super.visit(n, arg); }
        @Override public void visit(DoStmt n, Void arg) { complexity++; super.visit(n, arg); }
        @Override public void visit(CatchClause n, Void arg) { complexity++; super.visit(n, arg); }
        @Override public void visit(SwitchEntry n, Void arg) {
            if (!n.getLabels().isEmpty()) complexity++;
            super.visit(n, arg);
        }
        @Override public void visit(BinaryExpr n, Void arg) {
            if (n.getOperator() == BinaryExpr.Operator.AND || n.getOperator() == BinaryExpr.Operator.OR) {
                complexity++;
            }
            super.visit(n, arg);
        }
    }

    /**
     * Calcola la complessità cognitiva per il metodo.
     * Conta i livelli di annidamento di strutture di controllo come if, for, while, switch e case.
     */
    private int computeCognitiveComplexity(MethodDeclaration methodDeclaration) {
        return computeCognitiveComplexity(methodDeclaration.getBody().orElse(null), 0, 0);
    }

    private int computeCognitiveComplexity(Node node, int nesting, int complexity) {
        if (node == null) return complexity;

        for (Node child : node.getChildNodes()) {
            int localAdd = 0;

            if (child instanceof IfStmt || child instanceof ForStmt || child instanceof ForEachStmt ||
                    child instanceof WhileStmt || child instanceof DoStmt || child instanceof CatchClause ||
                    child instanceof SwitchStmt || child instanceof SwitchEntry) {
                localAdd = 1 + nesting;
                complexity += localAdd;
                complexity = computeCognitiveComplexity(child, nesting + 1, complexity);
            } else if (child instanceof BinaryExpr) {
                BinaryExpr be = (BinaryExpr) child;
                if (be.getOperator() == BinaryExpr.Operator.AND || be.getOperator() == BinaryExpr.Operator.OR) {
                    complexity += 1;
                }
                complexity = computeCognitiveComplexity(child, nesting, complexity);
            } else {
                complexity = computeCognitiveComplexity(child, nesting, complexity);
            }
        }

        return complexity;
    }

    /**
     * Aggiorna le storie del metodo per la versione specificata.
     */
    private int computeMethodHistories(Version version) {
        return this.methodHistoriesPerVersion.getOrDefault(version, 0) + 1;
    }

    /**
     * Calcola i punti di ramificazione (branch points) per il metodo.
     * Conta le strutture di controllo come if, for, while, switch e operatori logici.
     */
    private int computeBranchPoints(MethodDeclaration methodDeclaration) {
        BranchPointVisitor visitor = new BranchPointVisitor();
        visitor.visit(methodDeclaration, null);
        return visitor.getBranchPoints();
    }

    private static class BranchPointVisitor extends VoidVisitorAdapter<Void> {
        private int count = 0;

        public int getBranchPoints() {
            return count;
        }

        @Override public void visit(IfStmt n, Void arg) { count++; super.visit(n, arg); }
        @Override public void visit(ForStmt n, Void arg) { count++; super.visit(n, arg); }
        @Override public void visit(ForEachStmt n, Void arg) { count++; super.visit(n, arg); }
        @Override public void visit(WhileStmt n, Void arg) { count++; super.visit(n, arg); }
        @Override public void visit(DoStmt n, Void arg) { count++; super.visit(n, arg); }
        @Override public void visit(CatchClause n, Void arg) { count++; super.visit(n, arg); }
        @Override public void visit(SwitchEntry n, Void arg) {
            if (!n.getLabels().isEmpty()) count++;
            super.visit(n, arg);
        }
        @Override public void visit(BinaryExpr n, Void arg) {
            if (n.getOperator() == BinaryExpr.Operator.AND || n.getOperator() == BinaryExpr.Operator.OR) {
                count++;
            }
            super.visit(n, arg);
        }
    }

    /**
     * Calcola la profondità di annidamento per il metodo.
     * Conta il numero massimo di strutture di controllo annidate.
     */
    /**
     * Calcola la profondità di annidamento per il metodo.
     * Conta il numero massimo di strutture di controllo annidate.
     */
    private int computeNestingDepth(MethodDeclaration methodDeclaration) {
        return computeNestingDepth(methodDeclaration.getBody().orElse(null), 0);
    }

    private int computeNestingDepth(Node node, int currentDepth) {
        if (node == null) return currentDepth;

        int maxDepth = currentDepth;

        for (Node child : node.getChildNodes()) {
            if (child instanceof Statement) {
                int childDepth = computeNestingDepth(child, currentDepth + 1);
                maxDepth = Math.max(maxDepth, childDepth);
            } else {
                // continua a visitare anche se non è uno Statement, perché ci potrebbero essere blocchi dentro ad espressioni
                int childDepth = computeNestingDepth(child, currentDepth);
                maxDepth = Math.max(maxDepth, childDepth);
            }
        }

        return maxDepth;
    }

    /**
     * Calcola il numero di parametri del metodo.
     * Conta i parametri definiti nella dichiarazione del metodo.
     */
    private int computeParametersCount(MethodDeclaration methodDeclaration) {
        return methodDeclaration.getParameters().size();
    }

    /**
     * Conta le occorrenze di un'espressione regolare in un testo.
     *
     * @param text  Il testo in cui cercare
     * @param regex L'espressione regolare da cercare
     * @return Il numero di occorrenze trovate
     */
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
            addedLinesPerCommit.put(commit, addedLines + addedLinesPerCommit.getOrDefault(commit, 0));
            deletedLinesPerCommit.put(commit, deletedLines + deletedLinesPerCommit.getOrDefault(commit, 0));
            churnPerCommit.put(commit, addedLines + deletedLines + churnPerCommit.getOrDefault(commit, 0));

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, e, () -> "Errore nel calcolo del churn per il metodo " + getFullName());
        }
    }

    /**
     * Verifica se due intervalli di linee si sovrappongono
     */
    private boolean rangesOverlap(int start1, int end1, int start2, int end2) {
        return start1 <= end2 && end1 >= start2;
    }

    /**
     * Controlla se il commit è associato a ticket che hanno versioni affette.
     * Se sì, segna la versione come "buggy".
     *
     * @param commit Commit da analizzare
     * @throws TicketRetrievalException Se si verifica un errore durante il recupero dei ticket
     */
    private void computeIfBuggy(Commit commit) throws TicketRetrievalException {
        List<Ticket> tickets = commit.getLinkedTickets();
        for (Ticket ticket : tickets) {
            List<Version> affectedVersions = ticket.getAffectedVersions();
            if (affectedVersions == null) continue;
            for (Version affectedVersion : affectedVersions) buggyPerVersion.put(affectedVersion, true);
        }
    }

    /**
     * Classe interna per rappresentare le informazioni su un metodo.
     * Contiene le linee di inizio e fine del metodo.
     */
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

