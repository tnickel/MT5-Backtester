package com.backtester.report;

import java.util.*;

/**
 * Holds the results of an MT5 optimization run.
 * Contains a list of optimization passes, each with parameter values and performance metrics.
 */
public class OptimizationResult {

    /** A single optimization pass (one parameter combination tested) */
    public static class Pass {
        private int passNumber;
        private double profit;
        private int totalTrades;
        private double profitFactor;
        private double expectedPayoff;
        private double drawdown;
        private double drawdownPercent;
        private double recoveryFactor;
        private double sharpeRatio;
        private double customCriterion;
        private double balance;
        private Map<String, String> parameterValues = new LinkedHashMap<>();

        public int getPassNumber() { return passNumber; }
        public void setPassNumber(int passNumber) { this.passNumber = passNumber; }

        public double getProfit() { return profit; }
        public void setProfit(double profit) { this.profit = profit; }

        public int getTotalTrades() { return totalTrades; }
        public void setTotalTrades(int totalTrades) { this.totalTrades = totalTrades; }

        public double getProfitFactor() { return profitFactor; }
        public void setProfitFactor(double profitFactor) { this.profitFactor = profitFactor; }

        public double getExpectedPayoff() { return expectedPayoff; }
        public void setExpectedPayoff(double expectedPayoff) { this.expectedPayoff = expectedPayoff; }

        public double getDrawdown() { return drawdown; }
        public void setDrawdown(double drawdown) { this.drawdown = drawdown; }

        public double getDrawdownPercent() { return drawdownPercent; }
        public void setDrawdownPercent(double drawdownPercent) { this.drawdownPercent = drawdownPercent; }

        public double getRecoveryFactor() { return recoveryFactor; }
        public void setRecoveryFactor(double recoveryFactor) { this.recoveryFactor = recoveryFactor; }

        public double getSharpeRatio() { return sharpeRatio; }
        public void setSharpeRatio(double sharpeRatio) { this.sharpeRatio = sharpeRatio; }

        public double getCustomCriterion() { return customCriterion; }
        public void setCustomCriterion(double customCriterion) { this.customCriterion = customCriterion; }

        public double getBalance() { return balance; }
        public void setBalance(double balance) { this.balance = balance; }

        public Map<String, String> getParameterValues() { return parameterValues; }
        public void setParameterValues(Map<String, String> parameterValues) { this.parameterValues = parameterValues; }

        public void setParameter(String name, String value) { parameterValues.put(name, value); }
        public String getParameter(String name) { return parameterValues.getOrDefault(name, ""); }
    }

    private List<Pass> passes = new ArrayList<>();
    private List<Pass> forwardPasses = new ArrayList<>();
    private List<String> parameterNames = new ArrayList<>();
    private String expert = "";
    private String symbol = "";
    private String period = "";
    private boolean success = false;
    private String message = "";
    private String outputDirectory = "";

    public List<Pass> getPasses() { return passes; }
    public void setPasses(List<Pass> passes) { this.passes = passes; }
    public void addPass(Pass pass) { this.passes.add(pass); }

    public List<Pass> getForwardPasses() { return forwardPasses; }
    public void setForwardPasses(List<Pass> forwardPasses) { this.forwardPasses = forwardPasses; }
    public void addForwardPass(Pass pass) { this.forwardPasses.add(pass); }
    public boolean hasForwardResults() { return !forwardPasses.isEmpty(); }

    public List<String> getParameterNames() { return parameterNames; }
    public void setParameterNames(List<String> parameterNames) { this.parameterNames = parameterNames; }

    public String getExpert() { return expert; }
    public void setExpert(String expert) { this.expert = expert; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getOutputDirectory() { return outputDirectory; }
    public void setOutputDirectory(String outputDirectory) { this.outputDirectory = outputDirectory; }

    /**
     * Returns the pass with the highest profit.
     */
    public Pass getBestByProfit() {
        return passes.stream()
                .max(Comparator.comparingDouble(Pass::getProfit))
                .orElse(null);
    }

    /**
     * Returns the pass with the best value for the given criterion.
     */
    public Pass getBestByCriterion(int criterion) {
        Comparator<Pass> comp;
        switch (criterion) {
            case 0: comp = Comparator.comparingDouble(Pass::getBalance); break;
            case 1: comp = Comparator.comparingDouble(Pass::getProfitFactor); break;
            case 2: comp = Comparator.comparingDouble(Pass::getExpectedPayoff); break;
            case 3: comp = Comparator.comparingDouble(p -> -p.getDrawdownPercent()); break; // lower is better
            case 4: comp = Comparator.comparingDouble(Pass::getRecoveryFactor); break;
            case 5: comp = Comparator.comparingDouble(Pass::getSharpeRatio); break;
            case 6: comp = Comparator.comparingDouble(Pass::getCustomCriterion); break;
            default: comp = Comparator.comparingDouble(Pass::getProfit); break;
        }
        return passes.stream().max(comp).orElse(null);
    }
}
