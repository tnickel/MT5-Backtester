package com.backtester.engine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * POJO holding parameters for a multi-backtest run (batch execution).
 */
public class MultiBacktestConfig {

    private List<String> experts = new ArrayList<>();
    private List<String> symbols = new ArrayList<>();
    private List<String> periods = new ArrayList<>();

    private int model = 0;
    private int executionMode = 0;

    private LocalDate fromDate = LocalDate.now().minusYears(1);
    private LocalDate toDate = LocalDate.now();

    private int deposit = 10000;
    private String currency = "USD";
    private String leverage = "1:100";

    /** Maps expert path -> .set filename for ExpertParameters */
    private Map<String, String> expertParametersMap = new HashMap<>();

    // --- Getters & Setters ---
    public List<String> getExperts() { return experts; }
    public void setExperts(List<String> experts) { this.experts = experts; }

    public List<String> getSymbols() { return symbols; }
    public void setSymbols(List<String> symbols) { this.symbols = symbols; }

    public List<String> getPeriods() { return periods; }
    public void setPeriods(List<String> periods) { this.periods = periods; }

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

    public void setExpertParameters(String expertPath, String setFileName) {
        expertParametersMap.put(expertPath, setFileName);
    }

    public String getExpertParameters(String expertPath) {
        return expertParametersMap.get(expertPath);
    }

    public Map<String, String> getExpertParametersMap() {
        return expertParametersMap;
    }

    /**
     * Calculates the total number of combination runs expected.
     */
    public int getTotalCombinations() {
        return Math.max(1, experts.size()) * Math.max(1, symbols.size()) * Math.max(1, periods.size());
    }

    /**
     * Generates individual BacktestConfig objects for each combination.
     */
    public List<BacktestConfig> generateSingleConfigs() {
        List<BacktestConfig> configs = new ArrayList<>();
        
        List<String> exps = experts.isEmpty() ? List.of("") : experts;
        List<String> syms = symbols.isEmpty() ? List.of("EURUSD") : symbols;
        List<String> pers = periods.isEmpty() ? List.of("H1") : periods;

        for (String exp : exps) {
            for (String sym : syms) {
                for (String per : pers) {
                    BacktestConfig cfg = new BacktestConfig();
                    cfg.setExpert(exp);
                    cfg.setSymbol(sym);
                    cfg.setPeriod(per);
                    cfg.setModel(model);
                    cfg.setExecutionMode(executionMode);
                    cfg.setFromDate(fromDate);
                    cfg.setToDate(toDate);
                    cfg.setDeposit(deposit);
                    cfg.setCurrency(currency);
                    cfg.setLeverage(leverage);
                    // Set EA-specific parameters if available
                    String setFile = expertParametersMap.get(exp);
                    if (setFile != null) {
                        cfg.setExpertParameters(setFile);
                    }
                    configs.add(cfg);
                }
            }
        }
        return configs;
    }
}
