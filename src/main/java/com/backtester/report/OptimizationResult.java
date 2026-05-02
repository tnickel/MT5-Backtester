package com.backtester.report;

import java.util.*;
import java.util.stream.Collectors;

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

    /**
     * A merged view of a backtest pass and its corresponding forward pass.
     * Passes are matched by pass number.
     */
    public static class CombinedPass {
        private final int passNumber;
        private final Pass backtestPass;
        private final Pass forwardPass;
        private final double score;
        private final double consistency;

        public CombinedPass(Pass backtestPass, Pass forwardPass, double score, double consistency) {
            this.passNumber  = backtestPass.getPassNumber();
            this.backtestPass  = backtestPass;
            this.forwardPass   = forwardPass;
            this.score         = score;
            this.consistency   = consistency;
        }

        public int    getPassNumber()   { return passNumber; }
        public Pass   getBacktestPass() { return backtestPass; }
        public Pass   getForwardPass()  { return forwardPass; }
        public double getScore()        { return score; }
        public double getConsistency()  { return consistency; }

        // Convenience getters used by the table columns
        public double getBtProfit()       { return backtestPass.getProfit(); }
        public int    getBtTrades()       { return backtestPass.getTotalTrades(); }
        public double getBtPf()           { return backtestPass.getProfitFactor(); }
        public double getBtDd()           { return backtestPass.getDrawdownPercent(); }
        public double getBtSharpe()       { return backtestPass.getSharpeRatio(); }
        public double getBtRecovery()     { return backtestPass.getRecoveryFactor(); }
        public double getFwProfit()       { return forwardPass != null ? forwardPass.getProfit()          : Double.NaN; }
        public int    getFwTrades()       { return forwardPass != null ? forwardPass.getTotalTrades()     : 0; }
        public double getFwPf()           { return forwardPass != null ? forwardPass.getProfitFactor()    : Double.NaN; }
        public double getFwDd()           { return forwardPass != null ? forwardPass.getDrawdownPercent() : Double.NaN; }
        public double getFwSharpe()       { return forwardPass != null ? forwardPass.getSharpeRatio()     : Double.NaN; }
        public double getFwRecovery()     { return forwardPass != null ? forwardPass.getRecoveryFactor()  : Double.NaN; }
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
            case 3: comp = Comparator.comparingDouble(p -> -p.getDrawdownPercent()); break;
            case 4: comp = Comparator.comparingDouble(Pass::getRecoveryFactor); break;
            case 5: comp = Comparator.comparingDouble(Pass::getSharpeRatio); break;
            case 6: comp = Comparator.comparingDouble(Pass::getCustomCriterion); break;
            default: comp = Comparator.comparingDouble(Pass::getProfit); break;
        }
        return passes.stream().max(comp).orElse(null);
    }

    // ─── Combined Analysis ────────────────────────────────────────────────────

    /**
     * Configurable score weights for the Combined Analysis tab.
     * All values are fractions (0.0–1.0). They are normalised internally
     * so they don't need to sum to exactly 1.
     */
    public static class ScoreWeights {
        public double wBtProfit    = 0.25;  // Backtest Profit
        public double wFwProfit    = 0.35;  // Forward Profit
        public double wConsistency = 0.20;  // FW/BT Konsistenz
        public double wBtPf        = 0.05;  // Backtest Profit Factor
        public double wFwPf        = 0.10;  // Forward Profit Factor
        public double wBtDd        = 0.025; // Backtest Drawdown (Straf-Faktor)
        public double wFwDd        = 0.025; // Forward Drawdown  (Straf-Faktor)

        /** Returns a copy with default weights. */
        public static ScoreWeights defaults() { return new ScoreWeights(); }

        /** Sum of all weights (used for normalisation). */
        public double total() {
            return wBtProfit + wFwProfit + wConsistency + wBtPf + wFwPf + wBtDd + wFwDd;
        }
    }

    /**
     * Merges the main passes and forward passes by pass number and computes
     * a combined robustness score (0–100) for each matched pair.
     *
     * @param requireForward if true, only passes that also appear in the forward list are included
     * @param weights        score weights; pass null to use defaults
     */
    public List<CombinedPass> buildCombinedPasses(boolean requireForward, ScoreWeights weights) {
        if (weights == null) weights = ScoreWeights.defaults();
        // Index forward passes by pass number for O(1) lookup
        Map<Integer, Pass> fwIndex = forwardPasses.stream()
                .collect(Collectors.toMap(Pass::getPassNumber, p -> p, (a, b) -> a));

        List<CombinedPass> combined = new ArrayList<>();
        for (Pass bt : passes) {
            Pass fw = fwIndex.get(bt.getPassNumber());
            if (requireForward && fw == null) continue;

            double consistency = computeConsistency(bt, fw);
            double score       = computeScore(bt, fw, consistency, passes, forwardPasses, weights);
            combined.add(new CombinedPass(bt, fw, score, consistency));
        }
        return combined;
    }

    /** Convenience overload using default weights (backwards compat). */
    public List<CombinedPass> buildCombinedPasses(boolean requireForward) {
        return buildCombinedPasses(requireForward, ScoreWeights.defaults());
    }

    /** Ratio of forward profit to backtest profit, clamped to [0, 2]. */
    private static double computeConsistency(Pass bt, Pass fw) {
        if (fw == null) return 0.0;
        if (bt.getProfit() <= 0) return fw.getProfit() > 0 ? 1.5 : 0.0;
        double ratio = fw.getProfit() / bt.getProfit();
        return Math.max(0.0, Math.min(2.0, ratio));
    }

    /**
     * Normalises a value within the range [min, max] to [0, 1].
     * Returns 0 if the range is zero.
     */
    private static double norm(double value, double min, double max) {
        if (max == min) return 0.5;
        return Math.max(0.0, Math.min(1.0, (value - min) / (max - min)));
    }

    /**
     * Score = weighted sum of normalised metrics (weights normalised to sum=1).
     */
    private static double computeScore(Pass bt, Pass fw,
                                       double consistency,
                                       List<Pass> allBt, List<Pass> allFw,
                                       ScoreWeights w) {
        double total = w.total();
        if (total <= 0) total = 1.0;

        // Compute min/max across all passes for normalisation
        double btProfMin = allBt.stream().mapToDouble(Pass::getProfit).min().orElse(0);
        double btProfMax = allBt.stream().mapToDouble(Pass::getProfit).max().orElse(1);
        double fwProfMin = allFw.stream().mapToDouble(Pass::getProfit).min().orElse(0);
        double fwProfMax = allFw.stream().mapToDouble(Pass::getProfit).max().orElse(1);
        double btPfMin   = allBt.stream().mapToDouble(Pass::getProfitFactor).min().orElse(0);
        double btPfMax   = allBt.stream().mapToDouble(Pass::getProfitFactor).max().orElse(2);
        double fwPfMin   = allFw.stream().mapToDouble(Pass::getProfitFactor).min().orElse(0);
        double fwPfMax   = allFw.stream().mapToDouble(Pass::getProfitFactor).max().orElse(2);
        double btDdMin   = allBt.stream().mapToDouble(Pass::getDrawdownPercent).min().orElse(0);
        double btDdMax   = allBt.stream().mapToDouble(Pass::getDrawdownPercent).max().orElse(100);
        double fwDdMin   = allFw.stream().mapToDouble(Pass::getDrawdownPercent).min().orElse(0);
        double fwDdMax   = allFw.stream().mapToDouble(Pass::getDrawdownPercent).max().orElse(100);

        double nBtProfit = norm(bt.getProfit(), btProfMin, btProfMax);
        double nFwProfit = fw != null ? norm(fw.getProfit(), fwProfMin, fwProfMax) : 0.0;
        double nConsist  = consistency / 2.0;   // [0,2] → [0,1]
        double nBtPf     = norm(bt.getProfitFactor(), btPfMin, btPfMax);
        double nFwPf     = fw != null ? norm(fw.getProfitFactor(), fwPfMin, fwPfMax) : 0.0;
        double nBtDd     = 1.0 - norm(bt.getDrawdownPercent(), btDdMin, btDdMax);
        double nFwDd     = fw != null ? (1.0 - norm(fw.getDrawdownPercent(), fwDdMin, fwDdMax)) : 0.5;

        double raw = (w.wBtProfit    * nBtProfit
                    + w.wFwProfit    * nFwProfit
                    + w.wConsistency * nConsist
                    + w.wBtPf        * nBtPf
                    + w.wFwPf        * nFwPf
                    + w.wBtDd        * nBtDd
                    + w.wFwDd        * nFwDd) / total;

        return Math.round(raw * 1000.0) / 10.0;  // 0.0 – 100.0
    }
}

