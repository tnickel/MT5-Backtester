package com.backtester.engine;

import java.time.LocalDate;

/**
 * Configuration POJO for an MT5 optimization run.
 * Extends the basic backtest parameters with optimization-specific settings.
 */
public class OptimizationConfig {

    // --- Basic Backtest Settings (same as BacktestConfig) ---
    private String expert = "";
    private String expertParameters = "";
    private String symbol = "EURUSD";
    private String period = "H1";
    private int model = 1; // OHLC
    private int executionMode = 0;
    private LocalDate fromDate = LocalDate.now().minusYears(1);
    private LocalDate toDate = LocalDate.now();
    private int deposit = 10000;
    private String currency = "USD";
    private String leverage = "1:100";

    // --- Optimization Settings ---

    /** Optimization mode names for UI display */
    public static final String[] OPTIMIZATION_MODES = {
        "Slow Complete Algorithm", "Fast Genetic Algorithm"
    };
    /** Optimization mode values for INI: 1=Complete, 2=Genetic */
    public static final int[] OPTIMIZATION_MODE_VALUES = {1, 2};

    /** Optimization criterion names */
    public static final String[] OPTIMIZATION_CRITERIA = {
        "Balance max",
        "Profit Factor max",
        "Expected Payoff max",
        "Drawdown min",
        "Recovery Factor max",
        "Sharpe Ratio max",
        "Custom (OnTester)",
        "Complex Criterion max"
    };

    /** Tick model names */
    public static final String[] MODEL_NAMES = {
        "Every tick", "1 minute OHLC", "Open price only",
        "Math calculations", "Every tick (real ticks)"
    };

    /** Forward testing mode names */
    public static final String[] FORWARD_MODES = {
        "Off", "1/2 period", "1/3 period", "1/4 period", "Custom date"
    };

    /**
     * Optimization mode: 1 = Slow Complete, 2 = Fast Genetic.
     */
    private int optimizationMode = 2;

    /**
     * Optimization criterion (0-7).
     */
    private int optimizationCriterion = 0;

    /**
     * Forward testing mode (0=off, 1=1/2, 2=1/3, 3=1/4, 4=custom).
     */
    private int forwardMode = 0;

    /**
     * Forward testing start date (only used if forwardMode == 4).
     */
    private LocalDate forwardDate = null;

    /**
     * Whether to use local testing agents.
     */
    private boolean useLocal = true;

    /**
     * Whether to use remote agents (disabled for now).
     */
    private boolean useRemote = false;

    /**
     * Whether to use MQL5 Cloud Network (disabled for now).
     */
    private boolean useCloud = false;

    // --- Getters & Setters ---

    public String getExpert() { return expert; }
    public void setExpert(String expert) { this.expert = expert; }

    public String getExpertParameters() { return expertParameters; }
    public void setExpertParameters(String expertParameters) { this.expertParameters = expertParameters; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public int getModel() { return model; }
    public void setModel(int model) { this.model = model; }

    public int getExecutionMode() { return executionMode; }
    public void setExecutionMode(int executionMode) { this.executionMode = executionMode; }

    public LocalDate getFromDate() { return fromDate; }
    public void setFromDate(LocalDate fromDate) { this.fromDate = fromDate; }

    public LocalDate getToDate() { return toDate; }
    public void setToDate(LocalDate toDate) { this.toDate = toDate; }

    public int getDeposit() { return deposit; }
    public void setDeposit(int deposit) { this.deposit = deposit; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getLeverage() { return leverage; }
    public void setLeverage(String leverage) { this.leverage = leverage; }

    public int getOptimizationMode() { return optimizationMode; }
    public void setOptimizationMode(int optimizationMode) { this.optimizationMode = optimizationMode; }

    public int getOptimizationCriterion() { return optimizationCriterion; }
    public void setOptimizationCriterion(int optimizationCriterion) { this.optimizationCriterion = optimizationCriterion; }

    public int getForwardMode() { return forwardMode; }
    public void setForwardMode(int forwardMode) { this.forwardMode = forwardMode; }

    public LocalDate getForwardDate() { return forwardDate; }
    public void setForwardDate(LocalDate forwardDate) { this.forwardDate = forwardDate; }

    public boolean isUseLocal() { return useLocal; }
    public void setUseLocal(boolean useLocal) { this.useLocal = useLocal; }

    public boolean isUseRemote() { return useRemote; }
    public void setUseRemote(boolean useRemote) { this.useRemote = useRemote; }

    public boolean isUseCloud() { return useCloud; }
    public void setUseCloud(boolean useCloud) { this.useCloud = useCloud; }

    public boolean isForwardEnabled() { return forwardMode > 0; }

    /**
     * Creates a directory name for the optimization output.
     */
    public String toDirectoryName() {
        String expertName = expert.contains("\\") ? expert.substring(expert.lastIndexOf('\\') + 1) : expert;
        expertName = expertName.contains("/") ? expertName.substring(expertName.lastIndexOf('/') + 1) : expertName;
        return "OPT_" + expertName.replace(" ", "_") + "_" + period + "_" + symbol;
    }
}
