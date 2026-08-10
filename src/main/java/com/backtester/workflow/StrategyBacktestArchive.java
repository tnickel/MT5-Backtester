package com.backtester.workflow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All tab-keyed backtest runs for one strategy identity ({@code passNumber + strategyName}).
 */
public class StrategyBacktestArchive {

    private String strategyKey = "";
    private String strategyName = "";
    private int passNumber;
    private LinkedHashMap<String, StrategyBacktestRun> runsByTab = new LinkedHashMap<>();

    public StrategyBacktestArchive() {
    }

    public StrategyBacktestArchive(String strategyKey, String strategyName, int passNumber) {
        this.strategyKey = strategyKey != null ? strategyKey : "";
        this.strategyName = strategyName != null ? strategyName : "";
        this.passNumber = passNumber;
    }

    public String getStrategyKey() { return strategyKey != null ? strategyKey : ""; }
    public void setStrategyKey(String strategyKey) { this.strategyKey = strategyKey != null ? strategyKey : ""; }

    public String getStrategyName() { return strategyName != null ? strategyName : ""; }
    public void setStrategyName(String strategyName) {
        this.strategyName = strategyName != null ? strategyName : "";
    }

    public int getPassNumber() { return passNumber; }
    public void setPassNumber(int passNumber) { this.passNumber = passNumber; }

    public Map<String, StrategyBacktestRun> getRunsByTab() {
        if (runsByTab == null) runsByTab = new LinkedHashMap<>();
        return runsByTab;
    }

    public void setRunsByTab(LinkedHashMap<String, StrategyBacktestRun> runsByTab) {
        this.runsByTab = runsByTab != null ? runsByTab : new LinkedHashMap<>();
    }

    /**
     * Inserts or replaces the run for {@code run.tabName}. Same tab overwrites; other tabs accumulate.
     */
    public void upsert(StrategyBacktestRun run) {
        if (run == null) return;
        String tab = run.getTabName();
        if (tab == null || tab.isBlank()) {
            tab = DatabankManager.RESULTS;
            run.setTabName(tab);
        }
        getRunsByTab().put(tab, run);
    }

    public StrategyBacktestRun getRun(String tabName) {
        if (tabName == null) return null;
        return getRunsByTab().get(tabName);
    }

    public List<StrategyBacktestRun> getAllRuns() {
        return new ArrayList<>(getRunsByTab().values());
    }

    public StrategyBacktestArchive copy() {
        StrategyBacktestArchive copy = new StrategyBacktestArchive(strategyKey, strategyName, passNumber);
        LinkedHashMap<String, StrategyBacktestRun> runs = new LinkedHashMap<>();
        for (Map.Entry<String, StrategyBacktestRun> entry : getRunsByTab().entrySet()) {
            StrategyBacktestRun run = entry.getValue();
            runs.put(entry.getKey(), run != null ? run.copy() : null);
        }
        copy.setRunsByTab(runs);
        return copy;
    }
}
