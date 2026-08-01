package com.backtester.workflow;

import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.engine.BacktestConfig;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Repräsentiert einen einzelnen modularen Task in einer Custom Project Pipeline (StrategyQuant-Stil).
 */
public class WorkflowTask {

    public static final int MODE_EVERY_TICK = 0;
    public static final int MODE_OHLC_M1 = 1;
    public static final int MODE_REAL_TICKS = 2;
    public static final int MODE_OPEN_PRICES = 3;

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
    /** Runtime cache; persisted databanks are the single source of truth. */
    private transient List<CombinedPass> outputPasses;
    private String lastExecutionLog;

    // --- StrategyQuant Databank & Retest / Ranking Settings ---
    private String sourceDatabank = "Results";
    private String targetDatabank = "Results";
    private String startDate = "";
    private String endDate = "";
    private String retestSymbol = "";
    private String retestPeriod = "";
    /**
     * UI-level modelling mode. This is deliberately not the raw MT5 model
     * number: MT5 uses 4 for real ticks and 2 for open prices.
     */
    private int executionMode = MODE_OHLC_M1;
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
        if (type == TaskType.LONGTERM_RETEST) {
            this.startDate = LocalDate.now().minusYears(7).toString();
            this.endDate = LocalDate.now().toString();
        } else if (type == TaskType.OPTIMIZER || type == TaskType.ROBUSTNESS_CV) {
            this.startDate = LocalDate.now().minusYears(2).toString();
            this.endDate = LocalDate.now().toString();
        }
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

    public List<CombinedPass> getOutputPasses() {
        if (outputPasses == null) outputPasses = new ArrayList<>();
        return outputPasses;
    }
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

    public int getExecutionMode() {
        return executionMode >= MODE_EVERY_TICK && executionMode <= MODE_OPEN_PRICES
                ? executionMode : MODE_OHLC_M1;
    }
    public void setExecutionMode(int executionMode) {
        if (executionMode < MODE_EVERY_TICK || executionMode > MODE_OPEN_PRICES) {
            throw new IllegalArgumentException("Unsupported workflow execution mode: " + executionMode);
        }
        this.executionMode = executionMode;
    }

    /** Maps the four workflow choices to the raw model IDs expected by MT5. */
    public int getMt5Model() {
        switch (getExecutionMode()) {
            case MODE_EVERY_TICK: return BacktestConfig.MODEL_EVERY_TICK;
            case MODE_REAL_TICKS: return BacktestConfig.MODEL_REAL_TICKS;
            case MODE_OPEN_PRICES: return BacktestConfig.MODEL_OPEN_PRICES;
            case MODE_OHLC_M1:
            default: return BacktestConfig.MODEL_OHLC_M1;
        }
    }

    public boolean isDeleteFailed() { return deleteFailed; }
    public void setDeleteFailed(boolean deleteFailed) { this.deleteFailed = deleteFailed; }

    public List<FilterCondition> getFilterConditions() {
        if (filterConditions == null) filterConditions = new ArrayList<>();
        filterConditions.removeIf(condition -> condition == null);
        return filterConditions;
    }
    public void setFilterConditions(List<FilterCondition> filterConditions) {
        this.filterConditions = filterConditions != null
                ? new ArrayList<>(filterConditions) : new ArrayList<>();
    }

    public void addFilterCondition(FilterCondition cond) {
        if (cond != null) getFilterConditions().add(cond);
    }

    /** Creates a detached copy without the transient, potentially large output cache. */
    public WorkflowTask copyForPersistence() {
        WorkflowTask copy = new WorkflowTask();
        copy.setId(id);
        copy.setName(name);
        copy.setType(type);
        copy.setEnabled(enabled);
        copy.setStatus(status);
        copy.setTaskConfigJson(taskConfigJson);
        copy.setLastExecutionLog(lastExecutionLog);
        copy.setSourceDatabank(sourceDatabank);
        copy.setTargetDatabank(targetDatabank);
        copy.setStartDate(startDate);
        copy.setEndDate(endDate);
        copy.setRetestSymbol(retestSymbol);
        copy.setRetestPeriod(retestPeriod);
        copy.setExecutionMode(getExecutionMode());
        copy.setDeleteFailed(deleteFailed);

        List<FilterCondition> conditionCopies = new ArrayList<>();
        for (FilterCondition condition : getFilterConditions()) {
            conditionCopies.add(condition.copyForPersistence());
        }
        copy.setFilterConditions(conditionCopies);
        return copy;
    }

    @Override
    public String toString() {
        return getName() + " (" + (type != null ? type.getDisplayName() : "Unbekannt") + ")";
    }
}
