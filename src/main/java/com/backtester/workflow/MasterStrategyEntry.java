package com.backtester.workflow;

import java.util.ArrayList;
import java.util.List;

/**
 * One measured state of the master strategy: the parameter set in force after a
 * hand-pick, re-run as a backtest under fixed reference conditions.
 *
 * <p>Stage scores are not comparable across stages — the unified score is computed
 * relative to each optimization's own population. These entries are, because every
 * one of them is produced with the same symbol, timeframe, period and modelling.
 * Only they answer "is the master strategy actually getting better".
 */
public class MasterStrategyEntry {

    /** One parameter of the adopted basis with the value it replaced. */
    public static final class ParameterChange {
        private String name = "";
        private String oldValue = "";
        private String newValue = "";

        /** Gson. */
        public ParameterChange() {
        }

        public ParameterChange(String name, String oldValue, String newValue) {
            this.name = safe(name);
            this.oldValue = safe(oldValue);
            this.newValue = safe(newValue);
        }

        public String getName() { return safe(name); }
        public void setName(String name) { this.name = safe(name); }

        public String getOldValue() { return safe(oldValue); }
        public void setOldValue(String oldValue) { this.oldValue = safe(oldValue); }

        public String getNewValue() { return safe(newValue); }
        public void setNewValue(String newValue) { this.newValue = safe(newValue); }

        public boolean isChanged() {
            return !GuidedOptimizationService.valuesEquivalent(getOldValue(), getNewValue());
        }

        public ParameterChange copy() {
            return new ParameterChange(name, oldValue, newValue);
        }
    }

    /** A parameter the upcoming stage varies, with the grid it walks. */
    public static final class OptimizationTarget {
        private String name = "";
        private String currentValue = "";
        private String start = "";
        private String step = "";
        private String end = "";

        /** Gson. */
        public OptimizationTarget() {
        }

        public OptimizationTarget(String name, String currentValue,
                                  String start, String step, String end) {
            this.name = safe(name);
            this.currentValue = safe(currentValue);
            this.start = safe(start);
            this.step = safe(step);
            this.end = safe(end);
        }

        public String getName() { return safe(name); }
        public void setName(String name) { this.name = safe(name); }

        public String getCurrentValue() { return safe(currentValue); }
        public void setCurrentValue(String currentValue) { this.currentValue = safe(currentValue); }

        public String getStart() { return safe(start); }
        public void setStart(String start) { this.start = safe(start); }

        public String getStep() { return safe(step); }
        public void setStep(String step) { this.step = safe(step); }

        public String getEnd() { return safe(end); }
        public void setEnd(String end) { this.end = safe(end); }

        /** {@code "5 … 30, Schritt 5"} — empty when the task carries no usable grid. */
        public String describeRange() {
            if (getStart().isEmpty() && getEnd().isEmpty()) return "";
            String range = getStart() + " … " + getEnd();
            return getStep().isEmpty() ? range : range + ", Schritt " + getStep();
        }

        public OptimizationTarget copy() {
            return new OptimizationTarget(name, currentValue, start, step, end);
        }
    }

    public enum Verdict {
        /** Better than the best entry so far. */
        BESSER,
        /** Within the tolerance band around the best entry so far. */
        NEUTRAL,
        /** Worse than the best entry so far. */
        SCHLECHTER,
        /** First entry, or the reference backtest did not produce usable numbers. */
        UNBEKANNT
    }

    private int sequence;
    private long createdAt;
    private String stageTaskName = "";
    private String sourceDatabank = "";
    private int sourcePassNumber = -1;

    private String expert = "";
    private String symbol = "";
    private String period = "";
    private String fromDate = "";
    private String toDate = "";
    private String tickModel = "";
    private int model = -1;
    private int deposit;
    private String currency = "";
    private String leverage = "";
    /** Reference conditions of this measurement; only equal keys are comparable. */
    private String contextKey = "";

    private boolean backtestSucceeded;
    private String failureMessage = "";

    private double profit;
    private double profitFactor;
    private double maxDrawdownPercent;
    private double maxDrawdownAbsolute;
    private int totalTrades;
    private double recoveryFactor;
    private double sharpeRatio;
    private double expectedPayoff;
    private double finalBalance;

    /** Profit per unit of drawdown — the figure the verdict is based on. */
    private double returnToDrawdown;

    /** Fingerprint of parameters plus reference context — identifies a repeat. */
    private String measurementSignature = "";
    private String reportDirectory = "";
    private String equityImagePath = "";
    private List<double[]> equityCurve = new ArrayList<>();
    private String setfileContent = "";

    /** Preformatted "Name: alt → neu" lines for what this pick changed. */
    private List<String> adoptedChanges = new ArrayList<>();

    /** Stage that produced the picked pass — the one that optimized the parameters below. */
    private String optimizedStageName = "";
    /** Every parameter the producing stage varied, changed or not. */
    private List<ParameterChange> optimizedParameters = new ArrayList<>();
    /** Values the run preset carried over besides the optimized ones. */
    private List<ParameterChange> additionalChanges = new ArrayList<>();
    /** What the stage this pick feeds is going to vary next. */
    private List<OptimizationTarget> nextStageTargets = new ArrayList<>();

    private Verdict verdict = Verdict.UNBEKANNT;
    /** Sequence number of the entry this one was compared against; -1 when none. */
    private int comparedToSequence = -1;
    private double deltaReturnToDrawdown;
    private double deltaProfit;
    private double deltaMaxDrawdownPercent;

    public int getSequence() { return sequence; }
    public void setSequence(int sequence) { this.sequence = sequence; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public String getStageTaskName() { return safe(stageTaskName); }
    public void setStageTaskName(String stageTaskName) { this.stageTaskName = safe(stageTaskName); }

    public String getSourceDatabank() { return safe(sourceDatabank); }
    public void setSourceDatabank(String sourceDatabank) { this.sourceDatabank = safe(sourceDatabank); }

    public int getSourcePassNumber() { return sourcePassNumber; }
    public void setSourcePassNumber(int sourcePassNumber) { this.sourcePassNumber = sourcePassNumber; }

    public String getExpert() { return safe(expert); }
    public void setExpert(String expert) { this.expert = safe(expert); }

    public int getModel() { return model; }
    public void setModel(int model) { this.model = model; }

    public int getDeposit() { return deposit; }
    public void setDeposit(int deposit) { this.deposit = deposit; }

    public String getCurrency() { return safe(currency); }
    public void setCurrency(String currency) { this.currency = safe(currency); }

    public String getLeverage() { return safe(leverage); }
    public void setLeverage(String leverage) { this.leverage = safe(leverage); }

    public String contextKey() { return safe(contextKey); }
    public void setContextKey(String contextKey) { this.contextKey = safe(contextKey); }

    public String getSymbol() { return safe(symbol); }
    public void setSymbol(String symbol) { this.symbol = safe(symbol); }

    public String getPeriod() { return safe(period); }
    public void setPeriod(String period) { this.period = safe(period); }

    public String getFromDate() { return safe(fromDate); }
    public void setFromDate(String fromDate) { this.fromDate = safe(fromDate); }

    public String getToDate() { return safe(toDate); }
    public void setToDate(String toDate) { this.toDate = safe(toDate); }

    public String getTickModel() { return safe(tickModel); }
    public void setTickModel(String tickModel) { this.tickModel = safe(tickModel); }

    public boolean isBacktestSucceeded() { return backtestSucceeded; }
    public void setBacktestSucceeded(boolean backtestSucceeded) { this.backtestSucceeded = backtestSucceeded; }

    public String getFailureMessage() { return safe(failureMessage); }
    public void setFailureMessage(String failureMessage) { this.failureMessage = safe(failureMessage); }

    public double getProfit() { return profit; }
    public void setProfit(double profit) { this.profit = profit; }

    public double getProfitFactor() { return profitFactor; }
    public void setProfitFactor(double profitFactor) { this.profitFactor = profitFactor; }

    public double getMaxDrawdownPercent() { return maxDrawdownPercent; }
    public void setMaxDrawdownPercent(double maxDrawdownPercent) { this.maxDrawdownPercent = maxDrawdownPercent; }

    public double getMaxDrawdownAbsolute() { return maxDrawdownAbsolute; }
    public void setMaxDrawdownAbsolute(double maxDrawdownAbsolute) { this.maxDrawdownAbsolute = maxDrawdownAbsolute; }

    public int getTotalTrades() { return totalTrades; }
    public void setTotalTrades(int totalTrades) { this.totalTrades = totalTrades; }

    public double getRecoveryFactor() { return recoveryFactor; }
    public void setRecoveryFactor(double recoveryFactor) { this.recoveryFactor = recoveryFactor; }

    public double getSharpeRatio() { return sharpeRatio; }
    public void setSharpeRatio(double sharpeRatio) { this.sharpeRatio = sharpeRatio; }

    public double getExpectedPayoff() { return expectedPayoff; }
    public void setExpectedPayoff(double expectedPayoff) { this.expectedPayoff = expectedPayoff; }

    public double getFinalBalance() { return finalBalance; }
    public void setFinalBalance(double finalBalance) { this.finalBalance = finalBalance; }

    public double getReturnToDrawdown() { return returnToDrawdown; }
    public void setReturnToDrawdown(double returnToDrawdown) { this.returnToDrawdown = returnToDrawdown; }

    public String getMeasurementSignature() { return safe(measurementSignature); }
    public void setMeasurementSignature(String measurementSignature) {
        this.measurementSignature = safe(measurementSignature);
    }

    public String getReportDirectory() { return safe(reportDirectory); }
    public void setReportDirectory(String reportDirectory) { this.reportDirectory = safe(reportDirectory); }

    public String getEquityImagePath() { return safe(equityImagePath); }
    public void setEquityImagePath(String equityImagePath) { this.equityImagePath = safe(equityImagePath); }

    public List<double[]> getEquityCurve() {
        if (equityCurve == null) equityCurve = new ArrayList<>();
        return equityCurve;
    }
    public void setEquityCurve(List<double[]> equityCurve) {
        this.equityCurve = equityCurve != null ? new ArrayList<>(equityCurve) : new ArrayList<>();
    }

    public String getSetfileContent() { return safe(setfileContent); }
    public void setSetfileContent(String setfileContent) { this.setfileContent = safe(setfileContent); }

    public List<String> getAdoptedChanges() {
        if (adoptedChanges == null) adoptedChanges = new ArrayList<>();
        return adoptedChanges;
    }
    public void setAdoptedChanges(List<String> adoptedChanges) {
        this.adoptedChanges = adoptedChanges != null ? new ArrayList<>(adoptedChanges) : new ArrayList<>();
    }

    public String getOptimizedStageName() { return safe(optimizedStageName); }
    public void setOptimizedStageName(String optimizedStageName) {
        this.optimizedStageName = safe(optimizedStageName);
    }

    public List<ParameterChange> getOptimizedParameters() {
        if (optimizedParameters == null) optimizedParameters = new ArrayList<>();
        return optimizedParameters;
    }
    public void setOptimizedParameters(List<ParameterChange> optimizedParameters) {
        this.optimizedParameters = optimizedParameters != null
                ? new ArrayList<>(optimizedParameters) : new ArrayList<>();
    }

    public List<ParameterChange> getAdditionalChanges() {
        if (additionalChanges == null) additionalChanges = new ArrayList<>();
        return additionalChanges;
    }
    public void setAdditionalChanges(List<ParameterChange> additionalChanges) {
        this.additionalChanges = additionalChanges != null
                ? new ArrayList<>(additionalChanges) : new ArrayList<>();
    }

    public List<OptimizationTarget> getNextStageTargets() {
        if (nextStageTargets == null) nextStageTargets = new ArrayList<>();
        return nextStageTargets;
    }
    public void setNextStageTargets(List<OptimizationTarget> nextStageTargets) {
        this.nextStageTargets = nextStageTargets != null
                ? new ArrayList<>(nextStageTargets) : new ArrayList<>();
    }

    public Verdict getVerdict() { return verdict != null ? verdict : Verdict.UNBEKANNT; }
    public void setVerdict(Verdict verdict) { this.verdict = verdict != null ? verdict : Verdict.UNBEKANNT; }

    public int getComparedToSequence() { return comparedToSequence; }
    public void setComparedToSequence(int comparedToSequence) { this.comparedToSequence = comparedToSequence; }

    public double getDeltaReturnToDrawdown() { return deltaReturnToDrawdown; }
    public void setDeltaReturnToDrawdown(double delta) { this.deltaReturnToDrawdown = delta; }

    public double getDeltaProfit() { return deltaProfit; }
    public void setDeltaProfit(double deltaProfit) { this.deltaProfit = deltaProfit; }

    public double getDeltaMaxDrawdownPercent() { return deltaMaxDrawdownPercent; }
    public void setDeltaMaxDrawdownPercent(double delta) { this.deltaMaxDrawdownPercent = delta; }

    /** Short label for the list column, e.g. {@code "#03 g03_env_upper_pick"}. */
    public String getShortLabel() {
        return String.format("#%02d %s", sequence,
                getSourceDatabank().isEmpty() ? getStageTaskName() : getSourceDatabank());
    }

    public MasterStrategyEntry copy() {
        MasterStrategyEntry copy = new MasterStrategyEntry();
        copy.sequence = sequence;
        copy.createdAt = createdAt;
        copy.stageTaskName = stageTaskName;
        copy.sourceDatabank = sourceDatabank;
        copy.sourcePassNumber = sourcePassNumber;
        copy.expert = expert;
        copy.symbol = symbol;
        copy.period = period;
        copy.fromDate = fromDate;
        copy.toDate = toDate;
        copy.tickModel = tickModel;
        copy.model = model;
        copy.deposit = deposit;
        copy.currency = currency;
        copy.leverage = leverage;
        copy.contextKey = contextKey;
        copy.backtestSucceeded = backtestSucceeded;
        copy.failureMessage = failureMessage;
        copy.profit = profit;
        copy.profitFactor = profitFactor;
        copy.maxDrawdownPercent = maxDrawdownPercent;
        copy.maxDrawdownAbsolute = maxDrawdownAbsolute;
        copy.totalTrades = totalTrades;
        copy.recoveryFactor = recoveryFactor;
        copy.sharpeRatio = sharpeRatio;
        copy.expectedPayoff = expectedPayoff;
        copy.finalBalance = finalBalance;
        copy.returnToDrawdown = returnToDrawdown;
        copy.measurementSignature = measurementSignature;
        copy.reportDirectory = reportDirectory;
        copy.equityImagePath = equityImagePath;
        copy.setEquityCurve(equityCurve);
        copy.setfileContent = setfileContent;
        copy.setAdoptedChanges(adoptedChanges);
        copy.optimizedStageName = optimizedStageName;
        copy.setOptimizedParameters(copyChanges(optimizedParameters));
        copy.setAdditionalChanges(copyChanges(additionalChanges));
        copy.setNextStageTargets(copyTargets(nextStageTargets));
        copy.verdict = getVerdict();
        copy.comparedToSequence = comparedToSequence;
        copy.deltaReturnToDrawdown = deltaReturnToDrawdown;
        copy.deltaProfit = deltaProfit;
        copy.deltaMaxDrawdownPercent = deltaMaxDrawdownPercent;
        return copy;
    }

    private static List<ParameterChange> copyChanges(List<ParameterChange> source) {
        List<ParameterChange> copies = new ArrayList<>();
        if (source == null) return copies;
        for (ParameterChange change : source) {
            if (change != null) copies.add(change.copy());
        }
        return copies;
    }

    private static List<OptimizationTarget> copyTargets(List<OptimizationTarget> source) {
        List<OptimizationTarget> copies = new ArrayList<>();
        if (source == null) return copies;
        for (OptimizationTarget target : source) {
            if (target != null) copies.add(target.copy());
        }
        return copies;
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
