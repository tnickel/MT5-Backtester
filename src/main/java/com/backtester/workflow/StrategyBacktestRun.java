package com.backtester.workflow;

import com.backtester.report.OptimizationResult.Pass;

/**
 * One completed retest/backtest for a strategy, keyed by databank tab name.
 */
public class StrategyBacktestRun {

    private String tabName = "";
    private String taskName = "";
    private String symbol = "";
    private String period = "";
    private String tickModel = "";
    private String fromDate = "";
    private String toDate = "";
    private long completedAt;
    private String setfileContent = "";
    private Pass result;

    public StrategyBacktestRun() {
    }

    public String getTabName() { return tabName != null ? tabName : ""; }
    public void setTabName(String tabName) { this.tabName = tabName != null ? tabName : ""; }

    public String getTaskName() { return taskName != null ? taskName : ""; }
    public void setTaskName(String taskName) { this.taskName = taskName != null ? taskName : ""; }

    public String getSymbol() { return symbol != null ? symbol : ""; }
    public void setSymbol(String symbol) { this.symbol = symbol != null ? symbol : ""; }

    public String getPeriod() { return period != null ? period : ""; }
    public void setPeriod(String period) { this.period = period != null ? period : ""; }

    public String getTickModel() { return tickModel != null ? tickModel : ""; }
    public void setTickModel(String tickModel) { this.tickModel = tickModel != null ? tickModel : ""; }

    public String getFromDate() { return fromDate != null ? fromDate : ""; }
    public void setFromDate(String fromDate) { this.fromDate = fromDate != null ? fromDate : ""; }

    public String getToDate() { return toDate != null ? toDate : ""; }
    public void setToDate(String toDate) { this.toDate = toDate != null ? toDate : ""; }

    public long getCompletedAt() { return completedAt; }
    public void setCompletedAt(long completedAt) { this.completedAt = completedAt; }

    public String getSetfileContent() { return setfileContent != null ? setfileContent : ""; }
    public void setSetfileContent(String setfileContent) {
        this.setfileContent = setfileContent != null ? setfileContent : "";
    }

    public Pass getResult() { return result; }
    public void setResult(Pass result) { this.result = result; }

    public String getDateRange() {
        if (!getFromDate().isEmpty() && !getToDate().isEmpty()) {
            return getFromDate() + " - " + getToDate();
        }
        if (!getFromDate().isEmpty()) return getFromDate();
        if (result != null && !result.getDateRange().isEmpty()) return result.getDateRange();
        return "";
    }

    public StrategyBacktestRun copy() {
        StrategyBacktestRun copy = new StrategyBacktestRun();
        copy.tabName = tabName;
        copy.taskName = taskName;
        copy.symbol = symbol;
        copy.period = period;
        copy.tickModel = tickModel;
        copy.fromDate = fromDate;
        copy.toDate = toDate;
        copy.completedAt = completedAt;
        copy.setfileContent = setfileContent;
        copy.result = result != null ? result.copy() : null;
        return copy;
    }
}
