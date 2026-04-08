package com.backtester.database;

public class HistoryRun {
    private int id;
    private String runType; // BACKTEST, OPTIMIZATION, ROBUSTNESS
    private String expertName;
    private long timestamp;
    private String resultJson; // Serialized metrics
    private String htmlPath;

    public HistoryRun() {
    }

    public HistoryRun(int id, String runType, String expertName, long timestamp, String resultJson, String htmlPath) {
        this.id = id;
        this.runType = runType;
        this.expertName = expertName;
        this.timestamp = timestamp;
        this.resultJson = resultJson;
        this.htmlPath = htmlPath;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getRunType() { return runType; }
    public void setRunType(String runType) { this.runType = runType; }

    public String getExpertName() { return expertName; }
    public void setExpertName(String expertName) { this.expertName = expertName; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }

    public String getHtmlPath() { return htmlPath; }
    public void setHtmlPath(String htmlPath) { this.htmlPath = htmlPath; }
}
