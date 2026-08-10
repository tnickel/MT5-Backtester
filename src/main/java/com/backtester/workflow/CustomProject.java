package com.backtester.workflow;

import com.backtester.report.OptimizationResult.CombinedPass;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Repräsentiert ein benutzerdefiniertes Workflow-Projekt (StrategyQuant Custom Project Paradigm).
 * Enthält grundlegende Marktdaten (Symbol, Period, Expert) und eine dynamische Kette von WorkflowTask-Elementen.
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
    /** Enables the project-wide guided automatic workflow. Legacy projects default to manual mode. */
    private boolean automaticModeEnabled = false;
    private Map<String, List<CombinedPass>> databanks = new HashMap<>();
    /** Global per-strategy backtest history keyed by passNumber+strategyName. */
    private Map<String, StrategyBacktestArchive> strategyArchives = new LinkedHashMap<>();

    public CustomProject() {
        this.id = UUID.randomUUID().toString();
        this.createdTimestamp = System.currentTimeMillis();
        this.lastRunTimestamp = 0;
        this.tasks = new ArrayList<>();
        this.databanks = new HashMap<>();
        this.strategyArchives = new LinkedHashMap<>();
    }

    public CustomProject(String name, String expert, String symbol, String period) {
        this();
        this.name = name;
        this.expert = expert;
        this.symbol = symbol;
        this.period = period;
    }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public CustomProject cloneProject(String newName, String newSymbol, String newPeriod) {
        CustomProject clone = new CustomProject();
        clone.setName(newName != null && !newName.isBlank() ? newName : getName() + " (Kopie)");
        clone.setExpert(getExpert());
        clone.setSymbol(newSymbol != null && !newSymbol.isBlank() ? newSymbol : getSymbol());
        clone.setPeriod(newPeriod != null && !newPeriod.isBlank() ? newPeriod : getPeriod());
        clone.setSaveDatabanksPersistently(isSaveDatabanksPersistently());
        clone.setAutomaticModeEnabled(isAutomaticModeEnabled());
        
        List<WorkflowTask> clonedTasks = new ArrayList<>();
        if (tasks != null) {
            for (WorkflowTask t : tasks) {
                if (t != null) {
                    clonedTasks.add(t.copyForPersistence());
                }
            }
        }
        clone.setTasks(clonedTasks);
        return clone;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name != null ? name : "Unbenanntes Projekt"; }
    public void setName(String name) { this.name = name; }

    public String getExpert() { return expert != null ? expert : ""; }
    public void setExpert(String expert) { this.expert = expert; }

    public String getSymbol() { return symbol != null ? symbol : "EURUSD"; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getPeriod() { return period != null ? period : "H1"; }
    public void setPeriod(String period) { this.period = period; }

    public long getCreatedTimestamp() { return createdTimestamp; }
    public void setCreatedTimestamp(long createdTimestamp) { this.createdTimestamp = createdTimestamp; }

    public long getLastRunTimestamp() { return lastRunTimestamp; }
    public void setLastRunTimestamp(long lastRunTimestamp) { this.lastRunTimestamp = lastRunTimestamp; }

    public List<WorkflowTask> getTasks() {
        if (tasks == null) tasks = new ArrayList<>();
        tasks.removeIf(task -> task == null);
        return tasks;
    }
    public void setTasks(List<WorkflowTask> tasks) {
        this.tasks = tasks != null ? new ArrayList<>(tasks) : new ArrayList<>();
    }

    public boolean isSaveDatabanksPersistently() { return saveDatabanksPersistently; }
    public void setSaveDatabanksPersistently(boolean saveDatabanksPersistently) { this.saveDatabanksPersistently = saveDatabanksPersistently; }

    public boolean isAutomaticModeEnabled() { return automaticModeEnabled; }
    public void setAutomaticModeEnabled(boolean automaticModeEnabled) {
        this.automaticModeEnabled = automaticModeEnabled;
    }

    public Map<String, List<CombinedPass>> getDatabanks() {
        if (databanks == null) databanks = new HashMap<>();
        return databanks;
    }
    public void setDatabanks(Map<String, List<CombinedPass>> databanks) { this.databanks = databanks; }

    public Map<String, StrategyBacktestArchive> getStrategyArchives() {
        if (strategyArchives == null) strategyArchives = new LinkedHashMap<>();
        return strategyArchives;
    }

    public void setStrategyArchives(Map<String, StrategyBacktestArchive> strategyArchives) {
        this.strategyArchives = strategyArchives != null
                ? new LinkedHashMap<>(strategyArchives) : new LinkedHashMap<>();
    }

    public void addTask(WorkflowTask task) {
        if (this.tasks == null) this.tasks = new ArrayList<>();
        this.tasks.add(task);
    }

    /**
     * Inserts {@code task} immediately below the task at {@code index}.
     * @return true if inserted
     */
    public boolean insertTaskBelow(int index, WorkflowTask task) {
        if (task == null) return false;
        List<WorkflowTask> list = getTasks();
        if (index < 0 || index >= list.size()) return false;
        list.add(index + 1, task);
        return true;
    }

    public void removeTask(WorkflowTask task) {
        if (this.tasks != null) {
            this.tasks.remove(task);
        }
    }

    public WorkflowTask findOriginTaskForDatabank(String dbName) {
        if (dbName == null || dbName.isBlank() || tasks == null || tasks.isEmpty()) return null;
        String currentDb = dbName.trim();
        int maxDepth = 20;
        while (currentDb != null && !currentDb.isBlank() && maxDepth-- > 0) {
            String targetToMatch = currentDb;
            WorkflowTask matchingTask = null;
            for (WorkflowTask t : tasks) {
                if (t != null && t.getTargetDatabank() != null && t.getTargetDatabank().equalsIgnoreCase(targetToMatch)) {
                    matchingTask = t;
                    break;
                }
            }
            if (matchingTask == null) break;
            if (matchingTask.getType() == WorkflowTask.TaskType.OPTIMIZER || matchingTask.getType() == WorkflowTask.TaskType.RETESTER) {
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
        if (this.tasks == null || index <= 0 || index >= this.tasks.size()) return false;
        WorkflowTask temp = this.tasks.get(index - 1);
        this.tasks.set(index - 1, this.tasks.get(index));
        this.tasks.set(index, temp);
        return true;
    }

    public boolean moveTaskDown(int index) {
        if (this.tasks == null || index < 0 || index >= this.tasks.size() - 1) return false;
        WorkflowTask temp = this.tasks.get(index + 1);
        this.tasks.set(index + 1, this.tasks.get(index));
        this.tasks.set(index, temp);
        return true;
    }

    public int getEnabledTaskCount() {
        if (tasks == null) return 0;
        int count = 0;
        for (WorkflowTask t : tasks) {
            if (t != null && t.isEnabled()) count++;
        }
        return count;
    }

    /**
     * Upgrades old projects where Retest, OOS validation and Custom Task were
     * modelled as separate types and task names redundantly stored positions.
     */
    public boolean migrateLegacyTaskDefinitions() {
        List<WorkflowTask> projectTasks = getTasks();
        boolean changed = false;
        int legacyNumberedNames = 0;

        for (WorkflowTask task : projectTasks) {
            changed |= task.normalizeLegacyType();
            if (isLegacyNumberedName(task.getName(), projectTasks.size())) legacyNumberedNames++;
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
        if (!matcher.matches()) return false;
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
        migrateLegacyTaskDefinitions();
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

        List<WorkflowTask> taskCopies = new ArrayList<>();
        for (WorkflowTask task : getTasks()) {
            taskCopies.add(task.copyForPersistence());
        }
        copy.setTasks(taskCopies);
        copy.setDatabanks(new LinkedHashMap<>());
        // Same persistence flag as databanks: keep history only when contents are saved.
        if (saveDatabanksPersistently) {
            copy.setStrategyArchives(StrategyBacktestArchiveStore.copyArchives(strategyArchives));
        } else {
            copy.setStrategyArchives(new LinkedHashMap<>());
        }
        return copy;
    }

    /**
     * Erstellt ein Standard-Projekt mit vorbereiteten Beispiel-Tasks (falls neu erstellt).
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
