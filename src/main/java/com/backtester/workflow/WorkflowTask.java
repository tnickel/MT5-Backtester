package com.backtester.workflow;

import com.backtester.config.EaParameter;
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
    public static final double DEFAULT_DIVERSITY_PARAM_DIFF_PCT = 0.15;
    public static final double DEFAULT_DIVERSITY_TRADE_DIFF_PCT = 0.10;
    public static final int DEFAULT_DIVERSITY_MIN_DIFFERENT_PARAMS = 3;
    public static final int DEFAULT_DIVERSITY_MAX_STRATEGIES = 20;
    public static final int DEFAULT_OPTIMIZER_MODE = 2;
    public static final int DEFAULT_OPTIMIZER_CRITERION = 4;
    public static final int DEFAULT_OPTIMIZER_FORWARD_MODE = 1;
    public static final double DEFAULT_ROBUSTNESS_SWEEP_PCT = 0.05;
    public static final int DEFAULT_ROBUSTNESS_STEPS = 10;
    public static final int DEFAULT_ROBUSTNESS_TIME_SHIFTS = 0;
    public static final int DEFAULT_ROBUSTNESS_SHIFT_DAYS = 7;

    public enum TaskType {
        STRATEGY_SELECTION("Strategie-Auswahl", "EA, Symbol, Timeframe & Parameterbereiche festlegen", true),
        OPTIMIZER("MT5 Optimizer", "Evolutionäre/Genetische Parametersuche", true),
        RETESTER("Retester", "Strategien auf einem frei wählbaren Zeitraum erneut testen", true),
        PRE_FILTER("Vorauswahl & Filter", "Kurzzeit / Performance-Vorfilterung", true),
        DIVERSITY_FILTER("Diversitäts-Clustering", "Unkorrelierte Top-Strategien selektieren", true),
        ROBUSTNESS_CV("Robustness Test (CV)", "Parameter-Sensitivity Sweeps & Stresstests", true),
        KI_EVALUATION("KI-Bewertung", "LLM-gestützte Stabilitätsanalyse", true),
        MASTER_REFERENCE("Master-Referenz", "Referenz-Backtest der Stufen-Master unter festen Bedingungen", false),
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
    /** Set when a quality filter removed the score leader — see FilterRejectionReport. */
    private String filterRejectionNote = "";

    // --- StrategyQuant Databank & Retest / Ranking Settings ---
    private String sourceDatabank = "Results";
    private String targetDatabank = "Results";
    private String startDate = "";
    private String endDate = "";
    private String retestSymbol = "";
    private String retestPeriod = "";
    private String optimizerOutputDirectory = "";
    private Integer optimizerMode;
    private Integer optimizerCriterion;
    private Integer optimizerForwardMode;
    private String optimizerForwardDate = "";
    private List<String> optimizerTargetParameters = new ArrayList<>();
    /**
     * Complete, detached EA parameter configuration prepared for this optimizer
     * stage by the guided hand-pick workflow. Keeping the snapshot on the task
     * makes the hand-pick project-local and reproducible after an application
     * restart; the global expert configuration is not sufficient for that.
     */
    private List<EaParameter> optimizerParameterSnapshot = new ArrayList<>();
    private boolean optimizerParameterBasisAdopted;
    private int optimizerParameterBasisPassNumber = -1;
    private String optimizerParameterBasisDatabank = "";
    /**
     * Audit of the filter-gate decision applied when this optimizer received its
     * parameter basis (automatic or manual hand-off). Empty when no Use_* gate
     * was forced for this stage.
     */
    private String adoptedFilterGateParameter = "";
    private String adoptedFilterGateVerdict = "";
    private String adoptedFilterGateForcedValue = "";
    private boolean adoptedFilterGateForced;
    private Double adoptedFilterGateOnMedianScore;
    private Double adoptedFilterGateOffMedianScore;
    private String adoptedFilterGateNote = "";
    private long sensitivityRunTimestamp;
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
    private boolean diversityRankByScore;
    /** Prefer higher OHLC trade count, then profit, before diversity. */
    private boolean diversityRankByActivity;
    /** Guided v132-only opt-in: collapse parameter rows that are behaviorally identical. */
    private boolean diversityDeduplicateEffectiveV132;
    /**
     * Null/true stamps B1–B10 after a diversity task. Wide shortlists set
     * this false so 100 survivors are not collapsed to ten cluster ids.
     */
    private Boolean diversityStampClusterIds;
    private List<EaParameter> diversityParameterSnapshot = new ArrayList<>();
    private Double robustnessSweepPct;
    private Integer robustnessSteps;
    private Integer robustnessTimeShifts;
    private Integer robustnessShiftDays;
    private String robustnessExcludedParams = "";

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

    public String getFilterRejectionNote() { return filterRejectionNote != null ? filterRejectionNote : ""; }
    public void setFilterRejectionNote(String filterRejectionNote) {
        this.filterRejectionNote = filterRejectionNote != null ? filterRejectionNote : "";
    }

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
    public String getSymbol() { return getRetestSymbol(); }

    public String getRetestPeriod() { return retestPeriod != null ? retestPeriod : ""; }
    public void setRetestPeriod(String retestPeriod) { this.retestPeriod = retestPeriod; }
    public String getPeriod() { return getRetestPeriod(); }

    public String getOptimizerOutputDirectory() {
        return optimizerOutputDirectory != null ? optimizerOutputDirectory : "";
    }
    public void setOptimizerOutputDirectory(String optimizerOutputDirectory) {
        this.optimizerOutputDirectory = optimizerOutputDirectory != null
                ? optimizerOutputDirectory.trim() : "";
    }

    public long getSensitivityRunTimestamp() { return Math.max(0L, sensitivityRunTimestamp); }
    public void setSensitivityRunTimestamp(long sensitivityRunTimestamp) {
        this.sensitivityRunTimestamp = Math.max(0L, sensitivityRunTimestamp);
    }

    public int getOptimizerMode() {
        return optimizerMode != null && (optimizerMode == 1 || optimizerMode == 2)
                ? optimizerMode : DEFAULT_OPTIMIZER_MODE;
    }
    public void setOptimizerMode(int optimizerMode) {
        if (optimizerMode != 1 && optimizerMode != 2) {
            throw new IllegalArgumentException("Optimizer-Modus muss 1 (Complete) oder 2 (Genetic) sein.");
        }
        this.optimizerMode = optimizerMode;
    }

    public int getOptimizerCriterion() {
        return optimizerCriterion != null && optimizerCriterion >= 0 && optimizerCriterion <= 7
                ? optimizerCriterion : DEFAULT_OPTIMIZER_CRITERION;
    }
    public void setOptimizerCriterion(int optimizerCriterion) {
        if (optimizerCriterion < 0 || optimizerCriterion > 7) {
            throw new IllegalArgumentException("Optimierungskriterium muss zwischen 0 und 7 liegen.");
        }
        this.optimizerCriterion = optimizerCriterion;
    }

    public int getOptimizerForwardMode() {
        return optimizerForwardMode != null && optimizerForwardMode >= 0 && optimizerForwardMode <= 4
                ? optimizerForwardMode : DEFAULT_OPTIMIZER_FORWARD_MODE;
    }
    public void setOptimizerForwardMode(int optimizerForwardMode) {
        if (optimizerForwardMode < 0 || optimizerForwardMode > 4) {
            throw new IllegalArgumentException("Forward-Modus muss zwischen 0 und 4 liegen.");
        }
        this.optimizerForwardMode = optimizerForwardMode;
    }

    public String getOptimizerForwardDate() {
        return optimizerForwardDate != null ? optimizerForwardDate : "";
    }
    public void setOptimizerForwardDate(String optimizerForwardDate) {
        this.optimizerForwardDate = optimizerForwardDate != null ? optimizerForwardDate.trim() : "";
    }

    /**
     * Retesters only split 1:1 when both mode and date were set explicitly.
     * The getter default (mode 1) must not turn every existing kill-gate
     * into two MetaTrader runs.
     */
    public boolean hasExplicitForwardSplit() {
        return optimizerForwardMode != null && optimizerForwardMode > 0
                && optimizerForwardDate != null && !optimizerForwardDate.isBlank();
    }

    /**
     * Parameter names targeted by this optimizer stage. A missing field in
     * legacy project JSON is treated as an empty selection.
     */
    public List<String> getOptimizerTargetParameters() {
        List<String> copy = new ArrayList<>();
        if (optimizerTargetParameters != null) {
            for (String parameterName : optimizerTargetParameters) {
                if (parameterName != null && !parameterName.isBlank()) {
                    copy.add(parameterName.trim());
                }
            }
        }
        return copy;
    }

    public void setOptimizerTargetParameters(List<String> optimizerTargetParameters) {
        this.optimizerTargetParameters = new ArrayList<>();
        if (optimizerTargetParameters == null) return;
        for (String parameterName : optimizerTargetParameters) {
            if (parameterName != null && !parameterName.isBlank()) {
                this.optimizerTargetParameters.add(parameterName.trim());
            }
        }
    }

    public List<EaParameter> getOptimizerParameterSnapshot() {
        List<EaParameter> copy = new ArrayList<>();
        if (optimizerParameterSnapshot != null) {
            for (EaParameter parameter : optimizerParameterSnapshot) {
                if (parameter != null) copy.add(parameter.copy());
            }
        }
        return copy;
    }

    public void setOptimizerParameterSnapshot(List<EaParameter> optimizerParameterSnapshot) {
        this.optimizerParameterSnapshot = new ArrayList<>();
        if (optimizerParameterSnapshot == null) return;
        for (EaParameter parameter : optimizerParameterSnapshot) {
            if (parameter != null) this.optimizerParameterSnapshot.add(parameter.copy());
        }
    }

    public boolean isOptimizerParameterBasisAdopted() { return optimizerParameterBasisAdopted; }
    public void setOptimizerParameterBasisAdopted(boolean optimizerParameterBasisAdopted) {
        this.optimizerParameterBasisAdopted = optimizerParameterBasisAdopted;
    }
    public int getOptimizerParameterBasisPassNumber() { return optimizerParameterBasisPassNumber; }
    public void setOptimizerParameterBasisPassNumber(int passNumber) {
        this.optimizerParameterBasisPassNumber = passNumber;
    }
    public String getOptimizerParameterBasisDatabank() {
        return optimizerParameterBasisDatabank != null ? optimizerParameterBasisDatabank : "";
    }
    public void setOptimizerParameterBasisDatabank(String databank) {
        this.optimizerParameterBasisDatabank = databank != null ? databank.trim() : "";
    }

    public String getAdoptedFilterGateParameter() {
        return adoptedFilterGateParameter != null ? adoptedFilterGateParameter : "";
    }

    public void setAdoptedFilterGateParameter(String adoptedFilterGateParameter) {
        this.adoptedFilterGateParameter = adoptedFilterGateParameter != null
                ? adoptedFilterGateParameter.trim() : "";
    }

    public String getAdoptedFilterGateVerdict() {
        return adoptedFilterGateVerdict != null ? adoptedFilterGateVerdict : "";
    }

    public void setAdoptedFilterGateVerdict(String adoptedFilterGateVerdict) {
        this.adoptedFilterGateVerdict = adoptedFilterGateVerdict != null
                ? adoptedFilterGateVerdict.trim() : "";
    }

    public String getAdoptedFilterGateForcedValue() {
        return adoptedFilterGateForcedValue != null ? adoptedFilterGateForcedValue : "";
    }

    public void setAdoptedFilterGateForcedValue(String adoptedFilterGateForcedValue) {
        this.adoptedFilterGateForcedValue = adoptedFilterGateForcedValue != null
                ? adoptedFilterGateForcedValue.trim() : "";
    }

    public boolean isAdoptedFilterGateForced() { return adoptedFilterGateForced; }

    public void setAdoptedFilterGateForced(boolean adoptedFilterGateForced) {
        this.adoptedFilterGateForced = adoptedFilterGateForced;
    }

    public double getAdoptedFilterGateOnMedianScore() {
        return adoptedFilterGateOnMedianScore != null ? adoptedFilterGateOnMedianScore : Double.NaN;
    }

    public void setAdoptedFilterGateOnMedianScore(double adoptedFilterGateOnMedianScore) {
        this.adoptedFilterGateOnMedianScore = Double.isFinite(adoptedFilterGateOnMedianScore)
                ? adoptedFilterGateOnMedianScore : null;
    }

    public double getAdoptedFilterGateOffMedianScore() {
        return adoptedFilterGateOffMedianScore != null ? adoptedFilterGateOffMedianScore : Double.NaN;
    }

    public void setAdoptedFilterGateOffMedianScore(double adoptedFilterGateOffMedianScore) {
        this.adoptedFilterGateOffMedianScore = Double.isFinite(adoptedFilterGateOffMedianScore)
                ? adoptedFilterGateOffMedianScore : null;
    }

    public String getAdoptedFilterGateNote() {
        return adoptedFilterGateNote != null ? adoptedFilterGateNote : "";
    }

    public void setAdoptedFilterGateNote(String adoptedFilterGateNote) {
        this.adoptedFilterGateNote = adoptedFilterGateNote != null ? adoptedFilterGateNote.trim() : "";
    }

    public void clearAdoptedFilterGateAudit() {
        adoptedFilterGateParameter = "";
        adoptedFilterGateVerdict = "";
        adoptedFilterGateForcedValue = "";
        adoptedFilterGateForced = false;
        adoptedFilterGateOnMedianScore = null;
        adoptedFilterGateOffMedianScore = null;
        adoptedFilterGateNote = "";
    }

    public void recordAdoptedFilterGate(String parameter,
                                        String verdict,
                                        String forcedValue,
                                        boolean forced,
                                        double onMedianScore,
                                        double offMedianScore,
                                        String note) {
        setAdoptedFilterGateParameter(parameter);
        setAdoptedFilterGateVerdict(verdict);
        setAdoptedFilterGateForcedValue(forcedValue);
        setAdoptedFilterGateForced(forced);
        setAdoptedFilterGateOnMedianScore(onMedianScore);
        setAdoptedFilterGateOffMedianScore(offMedianScore);
        setAdoptedFilterGateNote(note);
    }

    public void clearOptimizerParameterBasis() {
        optimizerParameterBasisAdopted = false;
        optimizerParameterBasisPassNumber = -1;
        optimizerParameterBasisDatabank = "";
        optimizerParameterSnapshot = new ArrayList<>();
        clearAdoptedFilterGateAudit();
    }

    public boolean initializeOptimizerSettings(int mode, int criterion, int forwardMode, LocalDate forwardDate) {
        if (optimizerMode != null && optimizerCriterion != null && optimizerForwardMode != null) return false;
        setOptimizerMode(mode == 1 ? 1 : 2);
        setOptimizerCriterion(criterion >= 0 && criterion <= 7 ? criterion : DEFAULT_OPTIMIZER_CRITERION);
        setOptimizerForwardMode(forwardMode >= 0 && forwardMode <= 4
                ? forwardMode : DEFAULT_OPTIMIZER_FORWARD_MODE);
        setOptimizerForwardDate(forwardDate != null ? forwardDate.toString() : "");
        return true;
    }

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

    public boolean isDiversityStampClusterIds() {
        return diversityStampClusterIds == null || diversityStampClusterIds;
    }

    public void setDiversityStampClusterIds(boolean diversityStampClusterIds) {
        this.diversityStampClusterIds = diversityStampClusterIds;
    }

    public boolean isDiversityRankByScore() { return diversityRankByScore; }
    public void setDiversityRankByScore(boolean diversityRankByScore) {
        this.diversityRankByScore = diversityRankByScore;
    }

    public boolean isDiversityRankByActivity() { return diversityRankByActivity; }
    public void setDiversityRankByActivity(boolean diversityRankByActivity) {
        this.diversityRankByActivity = diversityRankByActivity;
    }

    public boolean isDiversityDeduplicateEffectiveV132() {
        return diversityDeduplicateEffectiveV132;
    }

    public void setDiversityDeduplicateEffectiveV132(boolean diversityDeduplicateEffectiveV132) {
        this.diversityDeduplicateEffectiveV132 = diversityDeduplicateEffectiveV132;
    }

    public List<EaParameter> getDiversityParameterSnapshot() {
        List<EaParameter> copy = new ArrayList<>();
        if (diversityParameterSnapshot != null) {
            for (EaParameter parameter : diversityParameterSnapshot) {
                if (parameter != null) copy.add(parameter.copy());
            }
        }
        return copy;
    }

    public void setDiversityParameterSnapshot(List<EaParameter> diversityParameterSnapshot) {
        this.diversityParameterSnapshot = new ArrayList<>();
        if (diversityParameterSnapshot == null) return;
        for (EaParameter parameter : diversityParameterSnapshot) {
            if (parameter != null) this.diversityParameterSnapshot.add(parameter.copy());
        }
    }

    public double getRobustnessSweepPct() {
        return robustnessSweepPct != null && validPercentage(robustnessSweepPct)
                ? robustnessSweepPct : DEFAULT_ROBUSTNESS_SWEEP_PCT;
    }

    public void setRobustnessSweepPct(double value) {
        requirePercentage(value, "Robustness Sweep Abweichung %");
        this.robustnessSweepPct = value;
    }

    public int getRobustnessSteps() {
        return robustnessSteps != null && robustnessSteps > 0
                ? robustnessSteps : DEFAULT_ROBUSTNESS_STEPS;
    }

    public void setRobustnessSteps(int value) {
        if (value < 1) throw new IllegalArgumentException("Mindestens 1 Sweep-Schritt ist erforderlich.");
        this.robustnessSteps = value;
    }

    public int getRobustnessTimeShifts() {
        return robustnessTimeShifts != null && robustnessTimeShifts >= 0
                ? robustnessTimeShifts : DEFAULT_ROBUSTNESS_TIME_SHIFTS;
    }

    public void setRobustnessTimeShifts(int value) {
        if (value < 0) throw new IllegalArgumentException("Time Shifts dürfen nicht negativ sein.");
        this.robustnessTimeShifts = value;
    }

    public int getRobustnessShiftDays() {
        return robustnessShiftDays != null && robustnessShiftDays > 0
                ? robustnessShiftDays : DEFAULT_ROBUSTNESS_SHIFT_DAYS;
    }

    public void setRobustnessShiftDays(int value) {
        if (value < 1) throw new IllegalArgumentException("Shift Tage müssen mindestens 1 betragen.");
        this.robustnessShiftDays = value;
    }

    public String getRobustnessExcludedParams() {
        return robustnessExcludedParams != null ? robustnessExcludedParams : "";
    }

    public void setRobustnessExcludedParams(String value) {
        this.robustnessExcludedParams = value != null ? value.trim() : "";
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
        copy.setFilterRejectionNote(filterRejectionNote);
        copy.setSourceDatabank(sourceDatabank);
        copy.setTargetDatabank(targetDatabank);
        copy.setStartDate(startDate);
        copy.setEndDate(endDate);
        copy.setRetestSymbol(retestSymbol);
        copy.setRetestPeriod(retestPeriod);
        copy.setOptimizerOutputDirectory(optimizerOutputDirectory);
        copy.optimizerMode = optimizerMode;
        copy.optimizerCriterion = optimizerCriterion;
        copy.optimizerForwardMode = optimizerForwardMode;
        copy.setOptimizerForwardDate(optimizerForwardDate);
        copy.setOptimizerTargetParameters(getOptimizerTargetParameters());
        copy.setOptimizerParameterSnapshot(getOptimizerParameterSnapshot());
        copy.setOptimizerParameterBasisAdopted(optimizerParameterBasisAdopted);
        copy.setOptimizerParameterBasisPassNumber(optimizerParameterBasisPassNumber);
        copy.setOptimizerParameterBasisDatabank(optimizerParameterBasisDatabank);
        copy.setAdoptedFilterGateParameter(adoptedFilterGateParameter);
        copy.setAdoptedFilterGateVerdict(adoptedFilterGateVerdict);
        copy.setAdoptedFilterGateForcedValue(adoptedFilterGateForcedValue);
        copy.setAdoptedFilterGateForced(adoptedFilterGateForced);
        copy.setAdoptedFilterGateOnMedianScore(getAdoptedFilterGateOnMedianScore());
        copy.setAdoptedFilterGateOffMedianScore(getAdoptedFilterGateOffMedianScore());
        copy.setAdoptedFilterGateNote(adoptedFilterGateNote);
        copy.setSensitivityRunTimestamp(sensitivityRunTimestamp);
        copy.setExecutionMode(getExecutionMode());
        copy.setDeleteFailed(deleteFailed);
        copy.diversityParamDiffPct = diversityParamDiffPct;
        copy.diversityTradeDiffPct = diversityTradeDiffPct;
        copy.diversityMinDifferentParams = diversityMinDifferentParams;
        copy.diversityMaxStrategies = diversityMaxStrategies;
        copy.diversityRankByScore = diversityRankByScore;
        copy.diversityRankByActivity = diversityRankByActivity;
        copy.diversityDeduplicateEffectiveV132 = diversityDeduplicateEffectiveV132;
        copy.diversityStampClusterIds = diversityStampClusterIds;
        copy.setDiversityParameterSnapshot(getDiversityParameterSnapshot());
        copy.robustnessSweepPct = robustnessSweepPct;
        copy.robustnessSteps = robustnessSteps;
        copy.robustnessTimeShifts = robustnessTimeShifts;
        copy.robustnessShiftDays = robustnessShiftDays;
        copy.robustnessExcludedParams = robustnessExcludedParams;

        List<FilterCondition> conditionCopies = new ArrayList<>();
        for (FilterCondition condition : getFilterConditions()) {
            conditionCopies.add(condition.copyForPersistence());
        }
        copy.setFilterConditions(conditionCopies);
        return copy;
    }

    /**
     * Full settings clone for "clone below" in the workflow UI.
     * New id, PENDING status, no execution log / output cache.
     */
    public WorkflowTask cloneWithSettings() {
        WorkflowTask copy = copyForPersistence();
        copy.setId(UUID.randomUUID().toString());
        copy.setStatus(TaskStatus.PENDING);
        copy.setLastExecutionLog("");
        copy.setFilterRejectionNote("");
        copy.setSensitivityRunTimestamp(0);
        copy.setOutputPasses(new ArrayList<>());
        String baseName = getName() != null ? getName().trim() : getType().getDisplayName();
        if (!baseName.toLowerCase(java.util.Locale.ROOT).endsWith(" (copy)")) {
            copy.setName(baseName + " (copy)");
        } else {
            copy.setName(baseName);
        }
        return copy;
    }

    @Override
    public String toString() {
        return getName() + " (" + (type != null ? type.getDisplayName() : "Unbekannt") + ")";
    }
}
