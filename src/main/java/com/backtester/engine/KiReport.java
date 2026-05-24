package com.backtester.engine;

public class KiReport {
    private final int id;
    private final long runTimestamp;
    private final String expertName;
    private final String symbol;
    private final String period;
    private final String createdAt;
    private final String reportMarkdown;

    public KiReport(int id, long runTimestamp, String expertName, String symbol, String period, String createdAt, String reportMarkdown) {
        this.id = id;
        this.runTimestamp = runTimestamp;
        this.expertName = expertName;
        this.symbol = symbol;
        this.period = period;
        this.createdAt = createdAt;
        this.reportMarkdown = reportMarkdown;
    }

    public int getId() {
        return id;
    }

    public long getRunTimestamp() {
        return runTimestamp;
    }

    public String getExpertName() {
        return expertName;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getPeriod() {
        return period;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getReportMarkdown() {
        return reportMarkdown;
    }
}
