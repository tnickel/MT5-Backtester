package com.backtester.workflow;

import com.backtester.config.EaParameter;
import com.backtester.report.OptimizationResult.CombinedPass;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Repräsentiert ein benutzerdefiniertes Workflow-Projekt (StrategyQuant Custom
 * Project Paradigm).
 * Enthält grundlegende Marktdaten (Symbol, Period, Expert) und eine dynamische
 * Kette von WorkflowTask-Elementen.
 */
public class CustomProject {

    private static final Pattern LEGACY_NUMBERED_TASK_NAME = Pattern.compile("^\\s*(\\d+)\\.\\s*(.+)$");

    private String id;
    private String name;
    private String expert = "";
    private String symbol = "EURUSD";
    private String period = "H1";
    private long createdTimestamp;
    private long lastRunTimestamp;
    private int sortOrder = 0;
    private List<WorkflowTask> tasks;
    private boolean saveDatabanksPersistently = true;
    /**
     * Enables the project-wide guided automatic workflow. Legacy projects default
     * to manual mode.
     */
    private boolean automaticModeEnabled = false;
    private Map<String, List<CombinedPass>> databanks = new HashMap<>();
    /** Global per-strategy backtest history keyed by passNumber+strategyName. */
    private Map<String, StrategyBacktestArchive> strategyArchives = new LinkedHashMap<>();
    /**
     * Append-only reference backtests of the master strategy, one per hand-pick.
     */
    private List<MasterStrategyEntry> masterStrategyLineage = new ArrayList<>();
    private transient Object lineageLock = new Object();
    /**
     * Run a reference backtest after every adoption so progress stays measurable.
     */
    private boolean referenceBacktestEnabled = true;
    /**
     * Profit/drawdown measured for the currently confirmed master basis under the
     * fixed reference conditions. Floor for the next adoption.
     *
     * <p>
     * Nullable instead of NaN: plain Gson rejects NaN, and "never adopted" is the
     * normal state of a fresh project.
     */
    private Double masterSelectionRatio;
    /**
     * The parameter basis of the last reference measurement that confirmed an
     * improvement —
     * the master strategy itself. Written only after a measurement has proven it,
     * so a
     * crash during the minutes-long reference run cannot leave an unconfirmed
     * candidate
     * behind as the master.
     *
     * <p>
     * This is the single source of truth for "what do we fall back to". Stage
     * snapshots
     * cannot serve that purpose: the guided factory pre-seeds every optimizer with
     * the
     * original preset, so a stage that has not been adopted into yet still carries
     * the
     * values the chain started from, not the ones it has since proven.
     */
    private List<EaParameter> provenMasterParameters = new ArrayList<>();
    /**
     * The reference conditions the master was measured under. A measurement only
     * means
     * something together with the symbol, period and expert it ran on, so basis,
     * floor
     * and this key form one unit: when the conditions change, the other two stop
     * being
     * evidence and have to go with it.
     */
    private String provenMasterContextKey = "";
    /**
     * Lineage sequence of the measurement that confirmed {@link #provenMasterParameters}.
     * The persisted anchor keeps later verdicts tied to the master that was actually
     * committed instead of whichever historical measurement happens to score best.
     * Missing legacy JSON fields deserialize as {@code 0}; the accessor normalizes that
     * to {@code -1} (unknown).
     */
    private int confirmedMasterSequence = -1;

    public CustomProject() {
        this.id = UUID.randomUUID().toString();
        this.createdTimestamp = System.currentTimeMillis();
        this.lastRunTimestamp = 0;
        this.tasks = new ArrayList<>();
        this.databanks = new HashMap<>();
        this.strategyArchives = new LinkedHashMap<>();
        this.masterStrategyLineage = new ArrayList<>();
    }

    public CustomProject(String name, String expert, String symbol, String period) {
        this();
        this.name = name;
        this.expert = expert;
        this.symbol = symbol;
        this.period = period;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public CustomProject cloneProject(String newName, String newSymbol, String newPeriod) {
        CustomProject clone = new CustomProject();
        String targetSymbol = newSymbol != null && !newSymbol.isBlank() ? newSymbol.trim() : getSymbol();
        String targetPeriod = newPeriod != null && !newPeriod.isBlank() ? newPeriod.trim() : getPeriod();

        clone.setName(newName != null && !newName.isBlank() ? newName.trim() : getName() + " (Kopie)");
        clone.setExpert(getExpert());
        clone.setSymbol(targetSymbol);
        clone.setPeriod(targetPeriod);
        clone.setSaveDatabanksPersistently(isSaveDatabanksPersistently());
        clone.setAutomaticModeEnabled(isAutomaticModeEnabled());
        clone.setReferenceBacktestEnabled(isReferenceBacktestEnabled());

        // Task snapshots keep the source values as a starting point. The confirmed
        // floor does not: it was measured under the source symbol/period.
        clone.clearProvenMaster();

        List<WorkflowTask> clonedTasks = new ArrayList<>();
        if (tasks != null) {
            for (WorkflowTask t : tasks) {
                if (t != null) {
                    WorkflowTask taskCopy = t.copyForPersistence();
                    taskCopy.setStatus(WorkflowTask.TaskStatus.PENDING);
                    taskCopy.setLastExecutionLog("");
                    taskCopy.setFilterRejectionNote("");
                    taskCopy.setRetestSymbol(targetSymbol);
                    taskCopy.setRetestPeriod(targetPeriod);
                    taskCopy.setOptimizerParameterBasisAdopted(false);
                    taskCopy.setOptimizerParameterBasisPassNumber(-1);
                    taskCopy.setOptimizerParameterBasisDatabank("");
                    taskCopy.setAdoptedFilterGateParameter("");
                    taskCopy.setAdoptedFilterGateVerdict("");
                    taskCopy.setAdoptedFilterGateForcedValue("");
                    taskCopy.setAdoptedFilterGateForced(false);
                    taskCopy.setAdoptedFilterGateOnMedianScore(Double.NaN);
                    taskCopy.setAdoptedFilterGateOffMedianScore(Double.NaN);
                    taskCopy.setAdoptedFilterGateNote("");
                    clonedTasks.add(taskCopy);
                }
            }
        }
        clone.setTasks(clonedTasks);
        clone.retargetTasksForClone();

        return clone;
    }

    /**
     * Retargets the copied task definitions to the clone's market. This must only run
     * while creating a clone: ordinary project reads are not migrations and must never
     * rewrite intentionally different task symbols, labels or output directories.
     */
    private void retargetTasksForClone() {
        if (tasks == null || tasks.isEmpty())
            return;
        String currentSymbol = getSymbol();
        String currentPeriod = getPeriod();
        if (currentSymbol == null || currentSymbol.isBlank())
            return;

        List<String> knownSymbols = List.of(
                "AUDCAD", "EURUSD", "GBPUSD", "USDJPY", "GBPJPY", "AUDUSD", "NZDUSD",
                "USDCAD", "USDCHF", "EURGBP", "EURJPY", "EURCAD", "EURAUD", "GBPAUD",
                "GBPCAD", "XAUUSD", "BTCUSD");

        for (WorkflowTask task : tasks) {
            if (task == null)
                continue;

            // 1. Ensure retestSymbol matches project symbol
            if (!currentSymbol.equalsIgnoreCase(task.getRetestSymbol())) {
                task.setRetestSymbol(currentSymbol);
            }

            // 2. Ensure retestPeriod matches project period
            if (currentPeriod != null && !currentPeriod.isBlank()
                    && !currentPeriod.equalsIgnoreCase(task.getRetestPeriod())) {
                task.setRetestPeriod(currentPeriod);
            }

            // 3. Update task title if it contains an outdated symbol
            String name = task.getName();
            if (name != null && !name.isBlank()) {
                for (String sym : knownSymbols) {
                    if (!sym.equalsIgnoreCase(currentSymbol) && name.toUpperCase().contains(sym.toUpperCase())) {
                        name = name.replaceAll("(?i)" + java.util.regex.Pattern.quote(sym),
                                java.util.regex.Matcher.quoteReplacement(currentSymbol));
                        task.setName(name);
                    }
                }
            }

            // 4. Update output directory if it references an outdated symbol
            String outDir = task.getOptimizerOutputDirectory();
            if (outDir != null && !outDir.isBlank()) {
                for (String sym : knownSymbols) {
                    if (!sym.equalsIgnoreCase(currentSymbol) && outDir.toUpperCase().contains(sym.toUpperCase())) {
                        outDir = outDir.replaceAll("(?i)" + java.util.regex.Pattern.quote(sym),
                                java.util.regex.Matcher.quoteReplacement(currentSymbol));
                        task.setOptimizerOutputDirectory(outDir);
                    }
                }
            }
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name != null ? name : "Unbenanntes Projekt";
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExpert() {
        return expert != null ? expert : "";
    }

    public void setExpert(String expert) {
        this.expert = expert;
    }

    public String getSymbol() {
        return symbol != null ? symbol : "EURUSD";
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getPeriod() {
        return period != null ? period : "H1";
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public long getCreatedTimestamp() {
        return createdTimestamp;
    }

    public void setCreatedTimestamp(long createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }

    public long getLastRunTimestamp() {
        return lastRunTimestamp;
    }

    public void setLastRunTimestamp(long lastRunTimestamp) {
        this.lastRunTimestamp = lastRunTimestamp;
    }

    public List<WorkflowTask> getTasks() {
        if (tasks == null)
            tasks = new ArrayList<>();
        tasks.removeIf(task -> task == null);
        return tasks;
    }

    public void setTasks(List<WorkflowTask> tasks) {
        this.tasks = tasks != null ? new ArrayList<>(tasks) : new ArrayList<>();
    }

    public boolean isSaveDatabanksPersistently() {
        return saveDatabanksPersistently;
    }

    public void setSaveDatabanksPersistently(boolean saveDatabanksPersistently) {
        this.saveDatabanksPersistently = saveDatabanksPersistently;
    }

    public boolean isAutomaticModeEnabled() {
        return automaticModeEnabled;
    }

    public void setAutomaticModeEnabled(boolean automaticModeEnabled) {
        this.automaticModeEnabled = automaticModeEnabled;
    }

    public Map<String, List<CombinedPass>> getDatabanks() {
        if (databanks == null)
            databanks = new HashMap<>();
        return databanks;
    }

    public void setDatabanks(Map<String, List<CombinedPass>> databanks) {
        this.databanks = databanks;
    }

    public Map<String, StrategyBacktestArchive> getStrategyArchives() {
        if (strategyArchives == null)
            strategyArchives = new LinkedHashMap<>();
        return strategyArchives;
    }

    public void setStrategyArchives(Map<String, StrategyBacktestArchive> strategyArchives) {
        this.strategyArchives = strategyArchives != null
                ? new LinkedHashMap<>(strategyArchives)
                : new LinkedHashMap<>();
    }

    /**
     * Snapshot of the lineage, safe to iterate: the reference backtest appends from
     * a
     * background thread while the UI and the save coordinator read.
     */
    public List<MasterStrategyEntry> getMasterStrategyLineage() {
        synchronized (lineageLock()) {
            if (masterStrategyLineage == null)
                masterStrategyLineage = new ArrayList<>();
            List<MasterStrategyEntry> snapshot = new ArrayList<>(masterStrategyLineage.size());
            for (MasterStrategyEntry entry : masterStrategyLineage) {
                // Damaged JSON can carry nulls; they must not blow up every reader.
                if (entry != null)
                    snapshot.add(entry);
            }
            return Collections.unmodifiableList(snapshot);
        }
    }

    /**
     * Runs {@code action} on the live lineage under the project lock.
     * Read-modify-write
     * sequences (append with sequence numbering and rating) must go through here,
     * otherwise two writers can hand out the same sequence or lose an entry.
     */
    public <T> T withMasterStrategyLineage(Function<List<MasterStrategyEntry>, T> action) {
        synchronized (lineageLock()) {
            if (masterStrategyLineage == null)
                masterStrategyLineage = new ArrayList<>();
            return action.apply(masterStrategyLineage);
        }
    }

    public void setMasterStrategyLineage(List<MasterStrategyEntry> masterStrategyLineage) {
        synchronized (lineageLock()) {
            this.masterStrategyLineage = masterStrategyLineage != null
                    ? new ArrayList<>(masterStrategyLineage)
                    : new ArrayList<>();
        }
    }

    /**
     * Lazily created so a Gson instance built without the constructor still works.
     */
    private Object lineageLock() {
        synchronized (this) {
            if (lineageLock == null)
                lineageLock = new Object();
            return lineageLock;
        }
    }

    public boolean isReferenceBacktestEnabled() {
        return referenceBacktestEnabled;
    }

    public void setReferenceBacktestEnabled(boolean referenceBacktestEnabled) {
        this.referenceBacktestEnabled = referenceBacktestEnabled;
    }

    /** NaN when no basis has been adopted yet. */
    public double getMasterSelectionRatio() {
        return masterSelectionRatio != null ? masterSelectionRatio : Double.NaN;
    }

    public void setMasterSelectionRatio(double masterSelectionRatio) {
        this.masterSelectionRatio = Double.isFinite(masterSelectionRatio)
                ? masterSelectionRatio
                : null;
    }

    /** Empty while no measurement has confirmed a basis yet. */
    public List<EaParameter> getProvenMasterParameters() {
        List<EaParameter> copy = new ArrayList<>();
        if (provenMasterParameters != null) {
            for (EaParameter parameter : provenMasterParameters) {
                if (parameter != null)
                    copy.add(parameter.copy());
            }
        }
        return copy;
    }

    public void setProvenMasterParameters(List<EaParameter> parameters) {
        this.provenMasterParameters = new ArrayList<>();
        if (parameters == null)
            return;
        for (EaParameter parameter : parameters) {
            if (parameter != null)
                this.provenMasterParameters.add(parameter.copy());
        }
    }

    /**
     * True once a measurement has confirmed a basis; the fallback exists from then
     * on.
     */
    public boolean hasProvenMaster() {
        return provenMasterParameters != null && !provenMasterParameters.isEmpty();
    }

    public String getProvenMasterContextKey() {
        return provenMasterContextKey != null ? provenMasterContextKey : "";
    }

    public void setProvenMasterContextKey(String contextKey) {
        this.provenMasterContextKey = contextKey != null ? contextKey : "";
    }

    public int getConfirmedMasterSequence() {
        return confirmedMasterSequence > 0 ? confirmedMasterSequence : -1;
    }

    public void setConfirmedMasterSequence(int sequence) {
        this.confirmedMasterSequence = sequence > 0 ? sequence : -1;
    }

    /**
     * Drops the confirmed master together with the floor derived from it. The floor
     * is
     * only meaningful as the value of that exact basis under those exact conditions
     * —
     * keeping it alone would block every candidate against a strategy that is gone.
     */
    public void clearProvenMaster() {
        this.provenMasterParameters = new ArrayList<>();
        this.provenMasterContextKey = "";
        this.confirmedMasterSequence = -1;
        setMasterSelectionRatio(Double.NaN);
    }

    public void addTask(WorkflowTask task) {
        if (this.tasks == null)
            this.tasks = new ArrayList<>();
        this.tasks.add(task);
    }

    /**
     * Inserts {@code task} immediately below the task at {@code index}.
     *
     * @return true if inserted
     */
    public boolean insertTaskBelow(int index, WorkflowTask task) {
        if (task == null)
            return false;
        List<WorkflowTask> list = getTasks();
        if (index < 0 || index >= list.size())
            return false;
        list.add(index + 1, task);
        return true;
    }

    public void removeTask(WorkflowTask task) {
        if (this.tasks != null) {
            this.tasks.remove(task);
        }
    }

    public WorkflowTask findOriginTaskForDatabank(String dbName) {
        if (dbName == null || dbName.isBlank() || tasks == null || tasks.isEmpty())
            return null;
        String currentDb = dbName.trim();
        int maxDepth = 20;
        while (currentDb != null && !currentDb.isBlank() && maxDepth-- > 0) {
            String targetToMatch = currentDb;
            WorkflowTask matchingTask = null;
            for (WorkflowTask t : tasks) {
                if (t != null && t.getTargetDatabank() != null
                        && t.getTargetDatabank().equalsIgnoreCase(targetToMatch)) {
                    matchingTask = t;
                    break;
                }
            }
            if (matchingTask == null)
                break;
            if (matchingTask.getType() == WorkflowTask.TaskType.OPTIMIZER
                    || matchingTask.getType() == WorkflowTask.TaskType.RETESTER) {
                return matchingTask;
            }
            String source = matchingTask.getSourceDatabank();
            if (source == null || source.isBlank() || source.equalsIgnoreCase(currentDb)) {
                return matchingTask;
            }
            currentDb = source.trim();
        }
        return null;
    }

    public boolean moveTaskUp(int index) {
        if (this.tasks == null || index <= 0 || index >= this.tasks.size())
            return false;
        WorkflowTask temp = this.tasks.get(index - 1);
        this.tasks.set(index - 1, this.tasks.get(index));
        this.tasks.set(index, temp);
        return true;
    }

    public boolean moveTaskDown(int index) {
        if (this.tasks == null || index < 0 || index >= this.tasks.size() - 1)
            return false;
        WorkflowTask temp = this.tasks.get(index + 1);
        this.tasks.set(index + 1, this.tasks.get(index));
        this.tasks.set(index, temp);
        return true;
    }

    public int getEnabledTaskCount() {
        if (tasks == null)
            return 0;
        int count = 0;
        for (WorkflowTask t : tasks) {
            if (t != null && t.isEnabled())
                count++;
        }
        return count;
    }

    /**
     * Optional one-off upgrade for old JSON. Load, save, backup and restore never
     * call this.
     */
    public boolean migrateLegacyTaskDefinitions() {
        List<WorkflowTask> projectTasks = getTasks();
        boolean changed = false;
        int legacyNumberedNames = 0;

        for (WorkflowTask task : projectTasks) {
            changed |= task.normalizeLegacyType();
            if (isLegacyNumberedName(task.getName(), projectTasks.size()))
                legacyNumberedNames++;
        }

        if (legacyNumberedNames > 0 && legacyNumberedNames * 2 >= projectTasks.size()) {
            for (WorkflowTask task : projectTasks) {
                Matcher matcher = LEGACY_NUMBERED_TASK_NAME.matcher(task.getName());
                if (matcher.matches() && isLegacyNumberedName(task.getName(), projectTasks.size())) {
                    task.setName(matcher.group(2).trim());
                    changed = true;
                }
            }
        }
        for (WorkflowTask task : projectTasks) {
            if (task.getType() == WorkflowTask.TaskType.DIVERSITY_FILTER
                    && "Dual- & Diversitäts-Filter".equals(task.getName())) {
                task.setName("Diversitäts-Clustering");
                changed = true;
            }
        }
        return changed;
    }

    private static boolean isLegacyNumberedName(String name, int taskCount) {
        Matcher matcher = LEGACY_NUMBERED_TASK_NAME.matcher(name != null ? name : "");
        if (!matcher.matches())
            return false;
        try {
            int storedPosition = Integer.parseInt(matcher.group(1));
            return storedPosition >= 1 && storedPosition <= taskCount;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    /**
     * Copies the small mutable project metadata on the caller thread. Databank
     * contents are intentionally attached later by the asynchronous writer.
     */
    public CustomProject copyMetadataForPersistence() {
        CustomProject copy = new CustomProject();
        copy.setId(id);
        copy.setName(name);
        copy.setExpert(expert);
        copy.setSymbol(symbol);
        copy.setPeriod(period);
        copy.setCreatedTimestamp(createdTimestamp);
        copy.setLastRunTimestamp(lastRunTimestamp);
        copy.setSortOrder(sortOrder);
        copy.setSaveDatabanksPersistently(saveDatabanksPersistently);
        copy.setAutomaticModeEnabled(automaticModeEnabled);
        copy.setReferenceBacktestEnabled(referenceBacktestEnabled);
        copy.setMasterSelectionRatio(getMasterSelectionRatio());
        // Basis, floor, context and confirmed sequence travel together: persisting the floor without the
        // parameters it belongs to survives a restart as a limit no strategy can meet,
        // and leaves the chain without anything to fall back to.
        copy.setProvenMasterParameters(getProvenMasterParameters());
        copy.setProvenMasterContextKey(getProvenMasterContextKey());
        copy.setConfirmedMasterSequence(getConfirmedMasterSequence());
        // The lineage is the only record of whether the chain improves — always kept.
        copy.setMasterStrategyLineage(withMasterStrategyLineage(lineage -> {
            List<MasterStrategyEntry> lineageCopy = new ArrayList<>(lineage.size());
            for (MasterStrategyEntry entry : lineage) {
                if (entry != null)
                    lineageCopy.add(entry.copy());
            }
            return lineageCopy;
        }));

        List<WorkflowTask> taskCopies = new ArrayList<>();
        for (WorkflowTask task : getTasks()) {
            taskCopies.add(task.copyForPersistence());
        }
        copy.setTasks(taskCopies);
        copy.setDatabanks(new LinkedHashMap<>());
        // Same persistence flag as databanks: keep history only when contents are
        // saved.
        if (saveDatabanksPersistently) {
            copy.setStrategyArchives(StrategyBacktestArchiveStore.copyArchives(strategyArchives));
        } else {
            copy.setStrategyArchives(new LinkedHashMap<>());
        }
        return copy;
    }

    /**
     * Erstellt ein Standard-Projekt mit vorbereiteten Beispiel-Tasks (falls neu
     * erstellt).
     */
    public static CustomProject createDefaultTemplate(String name, String expert, String symbol, String period) {
        CustomProject proj = new CustomProject(name, expert, symbol, period);

        WorkflowTask t1 = new WorkflowTask("Strategie-Auswahl", WorkflowTask.TaskType.STRATEGY_SELECTION);
        WorkflowTask t2 = new WorkflowTask("MT5 Optimizer", WorkflowTask.TaskType.OPTIMIZER);
        WorkflowTask t3 = new WorkflowTask("Kurzzeit-Vorauswahl", WorkflowTask.TaskType.PRE_FILTER);
        WorkflowTask t4 = new WorkflowTask("Langzeittest (5-10 Jahre)", WorkflowTask.TaskType.RETESTER);
        WorkflowTask t5 = new WorkflowTask("Diversitäts-Clustering", WorkflowTask.TaskType.DIVERSITY_FILTER);
        WorkflowTask t6 = new WorkflowTask("Robustness Test (CV)", WorkflowTask.TaskType.ROBUSTNESS_CV);
        WorkflowTask t7 = new WorkflowTask("KI-Bewertung", WorkflowTask.TaskType.KI_EVALUATION);
        WorkflowTask t8 = new WorkflowTask("Validierung (OOS)", WorkflowTask.TaskType.RETESTER);
        WorkflowTask t9 = new WorkflowTask("Portfolio Export", WorkflowTask.TaskType.PORTFOLIO_EXPORT);
        java.time.LocalDate validationCutoff = java.time.LocalDate.now().minusMonths(3);
        t2.setStartDate(java.time.LocalDate.now().minusYears(2).toString());
        t2.setEndDate(validationCutoff.toString());
        t4.setStartDate(java.time.LocalDate.now().minusYears(7).toString());
        t4.setEndDate(validationCutoff.toString());
        t6.setStartDate(java.time.LocalDate.now().minusYears(2).toString());
        t6.setEndDate(validationCutoff.toString());
        t8.setStartDate(validationCutoff.plusDays(1).toString());
        t8.setEndDate(java.time.LocalDate.now().toString());
        t8.setSourceDatabank("Results");
        t8.setTargetDatabank("Final");
        t9.setSourceDatabank("Final");
        t9.setTargetDatabank("Final");

        proj.addTask(t1);
        proj.addTask(t2);
        proj.addTask(t3);
        proj.addTask(t4);
        proj.addTask(t5);
        proj.addTask(t6);
        proj.addTask(t7);
        proj.addTask(t8);
        proj.addTask(t9);

        return proj;
    }
}
