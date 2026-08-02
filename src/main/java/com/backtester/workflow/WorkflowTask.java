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
    public static final double DEFAULT_DIVERSITY_PARAM_DIFF_PCT = 0.10;
    public static final double DEFAULT_DIVERSITY_TRADE_DIFF_PCT = 0.15;
    public static final int DEFAULT_DIVERSITY_MIN_DIFFERENT_PARAMS = 2;
    public static final int DEFAULT_DIVERSITY_MAX_STRATEGIES = 5;

    public enum TaskType {
        STRATEGY_SELECTION("Strategie-Auswahl", "EA, Symbol, Timeframe & Parameterbereiche festlegen", true),
        OPTIMIZER("MT5 Optimizer", "Evolutionäre/Genetische Parametersuche", true),
        RETESTER("Retester", "Strategien auf einem frei wählbaren Zeitraum erneut testen", true),
        PRE_FILTER("Vorauswahl & Filter", "Kurzzeit / Performance-Vorfilterung", true),
        DIVERSITY_FILTER("Diversitäts-Clustering", "Unkorrelierte Top-Strategien selektieren", true),
        ROBUSTNESS_CV("Robustness Test (CV)", "Parameter-Sensitivity Sweeps & Stresstests", true),
        KI_EVALUATION("KI-Bewertung", "LLM-gestützte Stabilitätsanalyse", true),
        PORTFOLIO_EXPORT("Portfolio Export", "Finale .set / PDF Berichte speichern", true),

        // Persisted aliases kept exclusively for backwards-compatible Gson loading.
        @Deprecated LONGTERM_RETEST("Retester", "Legacy-Alias für Retester", false),
        @Deprecated OOS_VALIDATION("Retester", "Legacy-Alias für Retester", false),
        @Deprecated CUSTOM_SCRIPT("Retester", "Legacy-Alias für Retester", false);

        private final String displayName;
        private final String description;
        private final boolean userSelectable;

        TaskType(String displayName, String description, boolean userSelectable) {
            this.displayName = displayName;
            this.description = description;
            this.userSelectable = userSelectable;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public boolean isUserSelectable() { return userSelectable; }

        public TaskType canonical() {
            return this == LONGTERM_RETEST || this == OOS_VALIDATION || this == CUSTOM_SCRIPT
                    ? RETESTER : this;
        }

        public static TaskType[] userSelectableValues() {
            return java.util.Arrays.stream(values())
                    .filter(TaskType::isUserSelectable)
                    .toArray(TaskType[]::new);
        }
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
    private Double diversityParamDiffPct;
    private Double diversityTradeDiffPct;
    private Integer diversityMinDifferentParams;
    private Integer diversityMaxStrategies;

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
        setType(type);
        if (getType() == TaskType.RETESTER) {
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

    public TaskType getType() {
        if (type != null) type = type.canonical();
        return type;
    }
    public void setType(TaskType type) { this.type = type != null ? type.canonical() : null; }

    public boolean normalizeLegacyType() {
        if (type == null) return false;
        TaskType canonicalType = type.canonical();
        if (canonicalType == type) return false;
        type = canonicalType;
        return true;
    }

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

    public double getDiversityParamDiffPct() {
        return validPercentage(diversityParamDiffPct)
                ? diversityParamDiffPct : DEFAULT_DIVERSITY_PARAM_DIFF_PCT;
    }

    public void setDiversityParamDiffPct(double value) {
        requirePercentage(value, "Parameter-Differenz");
        this.diversityParamDiffPct = value;
    }

    public double getDiversityTradeDiffPct() {
        return validPercentage(diversityTradeDiffPct)
                ? diversityTradeDiffPct : DEFAULT_DIVERSITY_TRADE_DIFF_PCT;
    }

    public void setDiversityTradeDiffPct(double value) {
        requirePercentage(value, "Trade-Differenz");
        this.diversityTradeDiffPct = value;
    }

    public int getDiversityMinDifferentParams() {
        return diversityMinDifferentParams != null && diversityMinDifferentParams > 0
                ? diversityMinDifferentParams : DEFAULT_DIVERSITY_MIN_DIFFERENT_PARAMS;
    }

    public void setDiversityMinDifferentParams(int value) {
        if (value < 1) throw new IllegalArgumentException("Mindestens ein Parameter muss verglichen werden.");
        this.diversityMinDifferentParams = value;
    }

    public int getDiversityMaxStrategies() {
        return diversityMaxStrategies != null && diversityMaxStrategies > 0
                ? diversityMaxStrategies : DEFAULT_DIVERSITY_MAX_STRATEGIES;
    }

    public void setDiversityMaxStrategies(int value) {
        if (value < 1) throw new IllegalArgumentException("Es muss mindestens eine Strategie ausgewählt werden.");
        this.diversityMaxStrategies = value;
    }

    private static boolean validPercentage(Double value) {
        return value != null && Double.isFinite(value) && value >= 0.0 && value <= 1.0;
    }

    private static void requirePercentage(double value, String label) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(label + " muss zwischen 0 und 100 Prozent liegen.");
        }
    }

    /** Creates a detached copy without the transient, potentially large output cache. */
    public WorkflowTask copyForPersistence() {
        WorkflowTask copy = new WorkflowTask();
        copy.setId(id);
        copy.setName(name);
        copy.setType(getType());
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
        copy.diversityParamDiffPct = diversityParamDiffPct;
        copy.diversityTradeDiffPct = diversityTradeDiffPct;
        copy.diversityMinDifferentParams = diversityMinDifferentParams;
        copy.diversityMaxStrategies = diversityMaxStrategies;

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
