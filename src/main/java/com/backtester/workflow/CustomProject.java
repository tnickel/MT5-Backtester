package com.backtester.workflow;

import com.backtester.report.OptimizationResult.CombinedPass;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Repräsentiert ein benutzerdefiniertes Workflow-Projekt (StrategyQuant Custom Project Paradigm).
 * Enthält grundlegende Marktdaten (Symbol, Period, Expert) und eine dynamische Kette von WorkflowTask-Elementen.
 */
public class CustomProject {

    private String id;
    private String name;
    private String expert = "";
    private String symbol = "EURUSD";
    private String period = "H1";
    private long createdTimestamp;
    private long lastRunTimestamp;
    private List<WorkflowTask> tasks;
    private boolean saveDatabanksPersistently = true;
    private Map<String, List<CombinedPass>> databanks = new HashMap<>();

    public CustomProject() {
        this.id = UUID.randomUUID().toString();
        this.createdTimestamp = System.currentTimeMillis();
        this.lastRunTimestamp = 0;
        this.tasks = new ArrayList<>();
        this.databanks = new HashMap<>();
    }

    public CustomProject(String name, String expert, String symbol, String period) {
        this();
        this.name = name;
        this.expert = expert;
        this.symbol = symbol;
        this.period = period;
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

    public List<WorkflowTask> getTasks() { return tasks != null ? tasks : new ArrayList<>(); }
    public void setTasks(List<WorkflowTask> tasks) { this.tasks = tasks; }

    public boolean isSaveDatabanksPersistently() { return saveDatabanksPersistently; }
    public void setSaveDatabanksPersistently(boolean saveDatabanksPersistently) { this.saveDatabanksPersistently = saveDatabanksPersistently; }

    public Map<String, List<CombinedPass>> getDatabanks() {
        if (databanks == null) databanks = new HashMap<>();
        return databanks;
    }
    public void setDatabanks(Map<String, List<CombinedPass>> databanks) { this.databanks = databanks; }

    public void addTask(WorkflowTask task) {
        if (this.tasks == null) this.tasks = new ArrayList<>();
        this.tasks.add(task);
    }

    public void removeTask(WorkflowTask task) {
        if (this.tasks != null) {
            this.tasks.remove(task);
        }
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
            if (t.isEnabled()) count++;
        }
        return count;
    }

    /**
     * Erstellt ein Standard-Projekt mit vorbereiteten Beispiel-Tasks (falls neu erstellt).
     */
    public static CustomProject createDefaultTemplate(String name, String expert, String symbol, String period) {
        CustomProject proj = new CustomProject(name, expert, symbol, period);
        
        WorkflowTask t1 = new WorkflowTask("1. Strategie-Auswahl", WorkflowTask.TaskType.STRATEGY_SELECTION);
        WorkflowTask t2 = new WorkflowTask("2. MT5 Optimizer", WorkflowTask.TaskType.OPTIMIZER);
        WorkflowTask t3 = new WorkflowTask("3. Retest", WorkflowTask.TaskType.LONGTERM_RETEST);
        WorkflowTask t4 = new WorkflowTask("4. Kurzzeit-Vorauswahl", WorkflowTask.TaskType.PRE_FILTER);
        WorkflowTask t5 = new WorkflowTask("5. Dual- & Diversitäts-Filter", WorkflowTask.TaskType.DIVERSITY_FILTER);
        WorkflowTask t6 = new WorkflowTask("6. Robustness Test (CV)", WorkflowTask.TaskType.ROBUSTNESS_CV);
        WorkflowTask t7 = new WorkflowTask("7. KI-Bewertung", WorkflowTask.TaskType.KI_EVALUATION);
        WorkflowTask t8 = new WorkflowTask("8. Portfolio Export", WorkflowTask.TaskType.PORTFOLIO_EXPORT);
        WorkflowTask t9 = new WorkflowTask("9. Validierung (OOS)", WorkflowTask.TaskType.OOS_VALIDATION);

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
