package com.backtester.report;

import java.util.ArrayList;
import java.util.List;

/**
 * POJO holding parsed backtest results.
 */
public class BacktestResult {

    private String expert = "";
    private String symbol = "";
    private String period = "";
    private String outputDirectory = "";
    private boolean success = false;
    private String message = "";

    /** Whether the EA used its compiled default config (no custom .set file) */
    private boolean usedDefaultConfig = true;
    /** Description of which config was used (e.g. "Custom (3 modified)" or "Default") */
    private String configInfo = "Default";

    // Financial metrics
    private double totalProfit = 0;
    private double grossProfit = 0;
    private double grossLoss = 0;
    private int totalTrades = 0;
    private double winRate = 0;
    private double maxDrawdown = 0;          // percentage
    private double maxDrawdownAbsolute = 0;  // absolute value
    private double maxDrawdownPercent = 0;
    private double profitFactor = 0;
    private double sharpeRatio = 0;
    private double recoveryFactor = 0;
    private double expectedPayoff = 0;
    private int shortPositions = 0;
    private int longPositions = 0;
    private int profitTrades = 0;
    private int lossTrades = 0;
    private double initialDeposit = 0;
    private double finalBalance = 0;
    private double largestWin = 0;
    private double largestLoss = 0;
    private double averageWin = 0;
    private double averageLoss = 0;

    // Equity history: each entry is [tradeNumber, balance, equity]
    private List<double[]> equityHistory = new ArrayList<>();

    // Getters & Setters

    public String getExpert() { return expert; }
    public void setExpert(String expert) { this.expert = expert; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public String getOutputDirectory() { return outputDirectory; }
    public void setOutputDirectory(String outputDirectory) { this.outputDirectory = outputDirectory; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public boolean isUsedDefaultConfig() { return usedDefaultConfig; }
    public void setUsedDefaultConfig(boolean usedDefaultConfig) { this.usedDefaultConfig = usedDefaultConfig; }

    public String getConfigInfo() { return configInfo; }
    public void setConfigInfo(String configInfo) { this.configInfo = configInfo; }

    public double getTotalProfit() { return totalProfit; }
    public void setTotalProfit(double totalProfit) { this.totalProfit = totalProfit; }

    public double getGrossProfit() { return grossProfit; }
    public void setGrossProfit(double grossProfit) { this.grossProfit = grossProfit; }

    public double getGrossLoss() { return grossLoss; }
    public void setGrossLoss(double grossLoss) { this.grossLoss = grossLoss; }

    public int getTotalTrades() { return totalTrades; }
    public void setTotalTrades(int totalTrades) { this.totalTrades = totalTrades; }

    public double getWinRate() { return winRate; }
    public void setWinRate(double winRate) { this.winRate = winRate; }

    public double getMaxDrawdown() { return maxDrawdown; }
    public void setMaxDrawdown(double maxDrawdown) { this.maxDrawdown = maxDrawdown; }

    public double getMaxDrawdownAbsolute() { return maxDrawdownAbsolute; }
    public void setMaxDrawdownAbsolute(double v) { this.maxDrawdownAbsolute = v; }

    public double getMaxDrawdownPercent() { return maxDrawdownPercent; }
    public void setMaxDrawdownPercent(double v) { this.maxDrawdownPercent = v; }

    public double getProfitFactor() { return profitFactor; }
    public void setProfitFactor(double profitFactor) { this.profitFactor = profitFactor; }

    public double getSharpeRatio() { return sharpeRatio; }
    public void setSharpeRatio(double sharpeRatio) { this.sharpeRatio = sharpeRatio; }

    public double getRecoveryFactor() { return recoveryFactor; }
    public void setRecoveryFactor(double recoveryFactor) { this.recoveryFactor = recoveryFactor; }

    public double getExpectedPayoff() { return expectedPayoff; }
    public void setExpectedPayoff(double expectedPayoff) { this.expectedPayoff = expectedPayoff; }

    public int getShortPositions() { return shortPositions; }
    public void setShortPositions(int shortPositions) { this.shortPositions = shortPositions; }

    public int getLongPositions() { return longPositions; }
    public void setLongPositions(int longPositions) { this.longPositions = longPositions; }

    public int getProfitTrades() { return profitTrades; }
    public void setProfitTrades(int profitTrades) { this.profitTrades = profitTrades; }

    public int getLossTrades() { return lossTrades; }
    public void setLossTrades(int lossTrades) { this.lossTrades = lossTrades; }

    public double getInitialDeposit() { return initialDeposit; }
    public void setInitialDeposit(double initialDeposit) { this.initialDeposit = initialDeposit; }

    public double getFinalBalance() { return finalBalance; }
    public void setFinalBalance(double finalBalance) { this.finalBalance = finalBalance; }

    public double getLargestWin() { return largestWin; }
    public void setLargestWin(double largestWin) { this.largestWin = largestWin; }

    public double getLargestLoss() { return largestLoss; }
    public void setLargestLoss(double largestLoss) { this.largestLoss = largestLoss; }

    public double getAverageWin() { return averageWin; }
    public void setAverageWin(double averageWin) { this.averageWin = averageWin; }

    public double getAverageLoss() { return averageLoss; }
    public void setAverageLoss(double averageLoss) { this.averageLoss = averageLoss; }

    public List<double[]> getEquityHistory() { return equityHistory; }
    public void setEquityHistory(List<double[]> equityHistory) { this.equityHistory = equityHistory; }

    @Override
    public String toString() {
        return String.format("BacktestResult{expert='%s', symbol='%s', period='%s', " +
                "profit=%.2f, trades=%d, winRate=%.1f%%, drawdown=%.2f%%, pf=%.2f}",
                expert, symbol, period, totalProfit, totalTrades, winRate, maxDrawdown, profitFactor);
    }
}
