package com.backtester.engine;

import java.time.LocalDate;

/**
 * POJO holding all parameters for a single backtest run.
 */
public class BacktestConfig {

    /** The Expert Advisor path relative to MQL5/Experts/ */
    private String expert = "";

    /** EA parameter file (optional, .set file) */
    private String expertParameters = "";

    /** Trading symbol (e.g. EURUSD, EURUSD_Duka) */
    private String symbol = "EURUSD";

    /** Chart period (M1, M5, M15, M30, H1, H4, D1, W1, MN1) */
    private String period = "H1";

    /**
     * Tick generation model:
     * 0 = Every tick
     * 1 = 1 minute OHLC
     * 2 = Open price only
     * 3 = Math calculations
     * 4 = Every tick based on real ticks
     */
    private int model = 0;

    /** Execution mode: 0=Normal, -1=Random delay, >0=delay ms */
    private int executionMode = 0;

    /** Start date of the backtest */
    private LocalDate fromDate = LocalDate.now().minusYears(1);

    /** End date of the backtest */
    private LocalDate toDate = LocalDate.now();

    /** Initial deposit */
    private int deposit = 10000;

    /** Account currency */
    private String currency = "USD";

    /** Leverage (e.g. "1:100") */
    private String leverage = "1:100";

    /** Optimization mode: 0=disabled */
    private int optimization = 0;

    /** Report file name (relative to MT5 dir) */
    private String reportFileName = "backtest_report.xml";

    /** Whether to replace existing report */
    private boolean replaceReport = true;

    /** Shutdown terminal after test */
    private boolean shutdownTerminal = true;

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

    public int getOptimization() { return optimization; }
    public void setOptimization(int optimization) { this.optimization = optimization; }

    public String getReportFileName() { return reportFileName; }
    public void setReportFileName(String reportFileName) { this.reportFileName = reportFileName; }

    public boolean isReplaceReport() { return replaceReport; }
    public void setReplaceReport(boolean replaceReport) { this.replaceReport = replaceReport; }

    public boolean isShutdownTerminal() { return shutdownTerminal; }
    public void setShutdownTerminal(boolean shutdownTerminal) { this.shutdownTerminal = shutdownTerminal; }

    /**
     * Creates a directory-safe name for this backtest configuration.
     * Format: Expert_Period_Symbol_FromDate
     */
    public String toDirectoryName() {
        String expertName = expert;
        // Extract just the filename without path
        if (expertName.contains("\\")) {
            expertName = expertName.substring(expertName.lastIndexOf('\\') + 1);
        }
        if (expertName.contains("/")) {
            expertName = expertName.substring(expertName.lastIndexOf('/') + 1);
        }
        // Remove .ex5 extension if present
        if (expertName.toLowerCase().endsWith(".ex5")) {
            expertName = expertName.substring(0, expertName.length() - 4);
        }
        return String.format("%s_%s_%s_%s", expertName, period, symbol,
                fromDate.toString().replace("-", ""));
    }

    /** Available timeframes */
    public static final String[] TIMEFRAMES = {
        "M1", "M5", "M15", "M30", "H1", "H4", "D1", "W1", "MN1"
    };

    /** Available tick models */
    public static final String[] MODEL_NAMES = {
        "Every tick",
        "1 minute OHLC",
        "Open price only",
        "Math calculations",
        "Every tick (real ticks)"
    };

    /** Major currency pairs */
    public static final String[] SYMBOLS = {
        "EURUSD", "GBPUSD", "USDJPY", "USDCHF", "AUDUSD",
        "NZDUSD", "USDCAD", "EURGBP", "EURJPY", "GBPJPY",
        "XAUUSD", "XAGUSD"
    };
}
