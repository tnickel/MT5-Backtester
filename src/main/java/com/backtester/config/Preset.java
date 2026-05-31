package com.backtester.config;

public class Preset {
    private String name;
    private String eaName;
    private String symbols;
    private String period;
    private java.util.List<EaParameter> eaParameters;

    public Preset() {}

    public Preset(String name, String eaName, String symbols, String period) {
        this.name = name;
        this.eaName = eaName;
        this.symbols = symbols;
        this.period = period;
    }

    public Preset(String name, String eaName, String symbols, String period, java.util.List<EaParameter> eaParameters) {
        this.name = name;
        this.eaName = eaName;
        this.symbols = symbols;
        this.period = period;
        this.eaParameters = eaParameters;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEaName() { return eaName; }
    public void setEaName(String eaName) { this.eaName = eaName; }

    public String getSymbols() { return symbols; }
    public void setSymbols(String symbols) { this.symbols = symbols; }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public java.util.List<EaParameter> getEaParameters() { return eaParameters; }
    public void setEaParameters(java.util.List<EaParameter> eaParameters) { this.eaParameters = eaParameters; }

    @Override
    public String toString() { return name; }
}
