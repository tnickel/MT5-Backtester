package com.backtester.database;

public class EaDbConfig {
    private int id;
    private String expertName;
    private String configName; // acts as a short comment/title
    private String parametersJson;
    private long updatedAt;

    public EaDbConfig(int id, String expertName, String configName, String parametersJson, long updatedAt) {
        this.id = id;
        this.expertName = expertName;
        this.configName = configName;
        this.parametersJson = parametersJson;
        this.updatedAt = updatedAt;
    }

    public int getId() {
        return id;
    }

    public String getExpertName() {
        return expertName;
    }

    public String getConfigName() {
        return configName;
    }

    public void setConfigName(String configName) {
        this.configName = configName;
    }

    public String getParametersJson() {
        return parametersJson;
    }

    public void setParametersJson(String parametersJson) {
        this.parametersJson = parametersJson;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
