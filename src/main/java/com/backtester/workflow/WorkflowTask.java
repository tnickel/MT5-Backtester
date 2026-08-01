package com.backtester.workflow;

import com.backtester.report.OptimizationResult.CombinedPass;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Repräsentiert einen einzelnen modularen Task in einer Custom Project Pipeline (StrategyQuant-Stil).
 */
public class WorkflowTask {

    public enum TaskType {
        STRATEGY_SELECTION("Strategie-Auswahl", "EA, Symbol, Timeframe & Parameterbereiche festlegen"),
        OPTIMIZER("MT5 Optimizer", "Evolutionäre/Genetische Parametersuche"),
        LONGTERM_RETEST("Retest", "Backtest / OOS / Multi-Year Retest von Strategien"),
        PRE_FILTER("Vorauswahl & Filter", "Kurzzeit / Performance-Vorfilterung"),
        DIVERSITY_FILTER("Diversitäts-Clustering", "Unkorrelierte Top-Strategien selektieren"),
        ROBUSTNESS_CV("Robustness Test (CV)", "Parameter-Sensitivity Sweeps & Stresstests"),
        KI_EVALUATION("KI-Bewertung", "LLM-gestützte Stabilitätsanalyse"),
        PORTFOLIO_EXPORT("Portfolio Export", "Finale .set / PDF Berichte speichern"),
        OOS_VALIDATION("OOS Validierung", "Test auf unberührten historischen Daten"),
        CUSTOM_SCRIPT("Custom Task", "Benutzerdefinierte Analyse oder Skript");

        private final String displayName;
        private final String description;

        TaskType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    public enum TaskStatus {
        PENDING("WARTEND"),
        RUNNING("LÄUFT"),
        COMPLETED("FERTIG"),
        FAILED("FEHLER"),
        DISABLED("DEAKTIVIERT");

        private final String label;
        TaskStatus(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    private String id;
    private String name;
    private TaskType type;
    private boolean enabled;
    private TaskStatus status;
    private String taskConfigJson;
    private List<CombinedPass> outputPasses;
    private String lastExecutionLog;

    // --- StrategyQuant Databank & Retest / Ranking Settings ---
    private String sourceDatabank = "Results";
    private String targetDatabank = "Results";
    private String startDate = "";
    private String endDate = "";
    private String retestSymbol = "";
    private String retestPeriod = "";
    private int executionMode = 1; // 1 = OHLC M1, 0 = Every tick (Ticksimulation), 2 = Every tick based on real ticks (Realtick), 3 = Open prices only
    private boolean deleteFailed = true;
    private List<FilterCondition> filterConditions;

    public WorkflowTask() {
        this.id = UUID.randomUUID().toString();
        this.enabled = true;
        this.status = TaskStatus.PENDING;
        this.outputPasses = new ArrayList<>();
        this.lastExecutionLog = "";
        this.sourceDatabank = "Results";
        this.targetDatabank = "Results";
        this.deleteFailed = true;
        this.filterConditions = new ArrayList<>();
    }

    public WorkflowTask(String name, TaskType type) {
        this();
        this.name = name;
        this.type = type;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name != null ? name : (type != null ? type.getDisplayName() : "Unbenannter Task"); }
    public void setName(String name) { this.name = name; }

    public TaskType getType() { return type; }
    public void setType(TaskType type) { this.type = type; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }

    public String getTaskConfigJson() { return taskConfigJson != null ? taskConfigJson : "{}"; }
    public void setTaskConfigJson(String taskConfigJson) { this.taskConfigJson = taskConfigJson; }

    public List<CombinedPass> getOutputPasses() { return outputPasses != null ? outputPasses : new ArrayList<>(); }
    public void setOutputPasses(List<CombinedPass> outputPasses) { this.outputPasses = outputPasses; }

    public String getLastExecutionLog() { return lastExecutionLog; }
    public void setLastExecutionLog(String lastExecutionLog) { this.lastExecutionLog = lastExecutionLog; }

    public String getSourceDatabank() { return sourceDatabank != null ? sourceDatabank : "Results"; }
    public void setSourceDatabank(String sourceDatabank) { this.sourceDatabank = sourceDatabank; }

    public String getTargetDatabank() { return targetDatabank != null ? targetDatabank : "Results"; }
    public void setTargetDatabank(String targetDatabank) { this.targetDatabank = targetDatabank; }

    public String getStartDate() { return startDate != null ? startDate : ""; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getEndDate() { return endDate != null ? endDate : ""; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    public String getRetestSymbol() { return retestSymbol != null ? retestSymbol : ""; }
    public void setRetestSymbol(String retestSymbol) { this.retestSymbol = retestSymbol; }

    public String getRetestPeriod() { return retestPeriod != null ? retestPeriod : ""; }
    public void setRetestPeriod(String retestPeriod) { this.retestPeriod = retestPeriod; }

    public int getExecutionMode() { return executionMode; }
    public void setExecutionMode(int executionMode) { this.executionMode = executionMode; }

    public boolean isDeleteFailed() { return deleteFailed; }
    public void setDeleteFailed(boolean deleteFailed) { this.deleteFailed = deleteFailed; }

    public List<FilterCondition> getFilterConditions() {
        if (filterConditions == null) filterConditions = new ArrayList<>();
        return filterConditions;
    }
    public void setFilterConditions(List<FilterCondition> filterConditions) {
        this.filterConditions = filterConditions;
    }

    public void addFilterCondition(FilterCondition cond) {
        getFilterConditions().add(cond);
    }

    @Override
    public String toString() {
        return getName() + " (" + (type != null ? type.getDisplayName() : "Unbekannt") + ")";
    }
}
