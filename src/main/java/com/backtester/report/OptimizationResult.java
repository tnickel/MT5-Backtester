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
        
        public String getName() { return String.valueOf(passNumber); }

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
        private final String scoreDetails;

        public CombinedPass(Pass backtestPass, Pass forwardPass, double score, double consistency, String scoreDetails) {
            this.passNumber  = backtestPass.getPassNumber();
            this.backtestPass  = backtestPass;
            this.forwardPass   = forwardPass;
            this.score         = score;
            this.consistency   = consistency;
            this.scoreDetails  = scoreDetails;
        }

        public int    getPassNumber()   { return passNumber; }
        public Pass   getBacktestPass() { return backtestPass; }
        public Pass   getForwardPass()  { return forwardPass; }
        public double getScore()        { return score; }
        public double getConsistency()  { return consistency; }
        public String getScoreDetails() { return scoreDetails; }

        public String getName() {
            return String.valueOf(passNumber);
        }

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
        public double getBtExpectedPayoff() { return backtestPass.getExpectedPayoff(); }
        public double getFwExpectedPayoff() { return forwardPass != null ? forwardPass.getExpectedPayoff() : Double.NaN; }
    }

    private List<Pass> passes = new ArrayList<>();
    private List<Pass> forwardPasses = new ArrayList<>();
    private List<String> parameterNames = new ArrayList<>();
    private String expert = "";
    private String symbol = "";
    private String period = "";
    private String fromDate = "";
    private String toDate = "";
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

    public String getFromDate() { return fromDate; }
    public void setFromDate(String fromDate) { this.fromDate = fromDate; }

    public String getToDate() { return toDate; }
    public void setToDate(String toDate) { this.toDate = toDate; }

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
        public double wFwTrades    = 0.05;  // FW Trade Count (mehr = besser, kein Cap)
        public double wBtRec       = 0.025; // Backtest Recovery
        public double wFwRec       = 0.025; // Forward Recovery

        /** Returns a copy with default weights. */
        public static ScoreWeights defaults() { return new ScoreWeights(); }

        /** Sum of all weights (used for normalisation). */
        public double total() {
            return wBtProfit + wFwProfit + wConsistency + wBtPf + wFwPf + wBtDd + wFwDd + wFwTrades + wBtRec + wFwRec;
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

        // Precompute the FW-trade-count threshold for the soft penalty.
        // threshold = median(fwTrades) / 2 — passes below this are dampened,
        // but never below 50 % so other metrics still matter.
        double fwTradesThreshold = computeFwTradesThreshold(forwardPasses);

        List<CombinedPass> combined = new ArrayList<>();
        for (Pass bt : passes) {
            Pass fw = fwIndex.get(bt.getPassNumber());
            if (requireForward && fw == null) continue;

            double consistency = computeConsistency(bt, fw);
            StringBuilder debug = new StringBuilder();
            double score       = computeScore(bt, fw, consistency, passes, forwardPasses,
                                              fwTradesThreshold, weights, debug);
            combined.add(new CombinedPass(bt, fw, score, consistency, debug.toString()));
        }
        return combined;
    }

    /**
     * Threshold below which the soft trade-count penalty kicks in.
     * Returns {@code median(fwTrades) / 2}, or 0 when no forward passes exist
     * (meaning: no penalty will be applied).
     */
    private static double computeFwTradesThreshold(List<Pass> forwardPasses) {
        if (forwardPasses == null || forwardPasses.isEmpty()) return 0.0;
        int[] sorted = forwardPasses.stream()
                .mapToInt(Pass::getTotalTrades)
                .sorted()
                .toArray();
        if (sorted.length == 0) return 0.0;
        double median;
        int n = sorted.length;
        if (n % 2 == 0) {
            median = (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
        } else {
            median = sorted[n / 2];
        }
        return median / 2.0;
    }

    /** Convenience overload using default weights (backwards compat). */
    public List<CombinedPass> buildCombinedPasses(boolean requireForward) {
        return buildCombinedPasses(requireForward, ScoreWeights.defaults());
    }

    /** Ratio of forward profit and recovery factor to backtest counterparts, clamped to [0, 2]. */
    private static double computeConsistency(Pass bt, Pass fw) {
        if (fw == null) return 0.0;
        
        double profitRatio;
        if (bt.getProfit() <= 0) {
            profitRatio = fw.getProfit() > 0 ? 1.5 : 0.0;
        } else {
            profitRatio = fw.getProfit() / bt.getProfit();
        }
        
        double recRatio;
        if (bt.getRecoveryFactor() <= 0) {
            recRatio = fw.getRecoveryFactor() > 0 ? 1.5 : 0.0;
        } else {
            recRatio = fw.getRecoveryFactor() / bt.getRecoveryFactor();
        }
        
        if (Double.isNaN(profitRatio)) profitRatio = 0.0;
        if (Double.isNaN(recRatio)) recRatio = 0.0;
        
        double ratio = (profitRatio + recRatio) / 2.0;
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
     *
     * <p>Trade count enters the score in two ways:
     * <ol>
     *   <li><b>Reward</b> via {@code w.wFwTrades}: more trades → linearly
     *       higher contribution within this optimization (no upper cap, since
     *       normalisation maps {@code [min,max] → [0,1]}).</li>
     *   <li><b>Soft penalty</b> when the FW trade count is below
     *       {@code fwTradesThreshold} (= median/2): the final score is
     *       multiplied by {@code max(0.5, n / threshold)}, so a strategy with
     *       hardly any trades is dampened but not destroyed — the other metrics
     *       still carry weight.</li>
     * </ol>
     */
    private static double computeScore(Pass bt, Pass fw,
                                       double consistency,
                                       List<Pass> allBt, List<Pass> allFw,
                                       double fwTradesThreshold,
                                       ScoreWeights w,
                                       StringBuilder debug) {
        double total = w.total();
        if (total <= 0) total = 1.0;

        // Absolute normalisation of metrics instead of relative to max/min
        
        // 1. Profit as ROI (assuming 10k default deposit if not set or invalid)
        double deposit = bt.getBalance() - bt.getProfit();
        if (deposit <= 0) deposit = 10000.0;
        
        double btRoi = bt.getProfit() / deposit;
        double nBtProfit = Math.max(0.0, Math.min(1.0, btRoi / 0.30)); // 30% ROI = 1.0
        
        double fwRoi = fw != null ? (fw.getProfit() / deposit) : 0.0;
        double nFwProfit = Math.max(0.0, Math.min(1.0, fwRoi / 0.10)); // 10% ROI = 1.0
        
        // 2. Consistency: ratio in [0.2, 1.0]
        double nConsist = Math.max(0.0, Math.min(1.0, (consistency - 0.2) / 0.8));
        
        // 3. Profit Factor: PF in [1.0, 2.0]
        double nBtPf = Math.max(0.0, Math.min(1.0, (bt.getProfitFactor() - 1.0) / 1.0));
        double nFwPf = fw != null ? Math.max(0.0, Math.min(1.0, (fw.getProfitFactor() - 1.0) / 1.0)) : 0.0;
        
        // 4. Drawdown%: 5% or less = 1.0, 25% or more = 0.0
        double nBtDd = Math.max(0.0, Math.min(1.0, 1.0 - (bt.getDrawdownPercent() - 5.0) / 20.0));
        double nFwDd = fw != null ? Math.max(0.0, Math.min(1.0, 1.0 - (fw.getDrawdownPercent() - 5.0) / 20.0)) : 0.0;
        
        // 5. Forward Trades: 5 trades = 0.0, 30 trades = 1.0
        double nFwTrades = fw != null ? Math.max(0.0, Math.min(1.0, (fw.getTotalTrades() - 5.0) / 25.0)) : 0.0;

        // 6. Recovery Factor: RF in [1.0, 5.0]
        double nBtRec = Math.max(0.0, Math.min(1.0, (bt.getRecoveryFactor() - 1.0) / 4.0));
        double nFwRec = fw != null ? Math.max(0.0, Math.min(1.0, (fw.getRecoveryFactor() - 1.0) / 4.0)) : 0.0;

        double raw = (w.wBtProfit    * nBtProfit
                    + w.wFwProfit    * nFwProfit
                    + w.wConsistency * nConsist
                    + w.wBtPf        * nBtPf
                    + w.wFwPf        * nFwPf
                    + w.wBtDd        * nBtDd
                    + w.wFwDd        * nFwDd
                    + w.wFwTrades    * nFwTrades
                    + w.wBtRec       * nBtRec
                    + w.wFwRec       * nFwRec) / total;

        if (debug != null) {
            debug.append(String.format("Gewichte:\nBT Profit: %.0f%% | FW Profit: %.0f%% | Konsistenz: %.0f%% | BT PF: %.0f%% | FW PF: %.0f%% | BT DD: %.0f%% | FW DD: %.0f%% | FW Trades: %.0f%% | BT Rec: %.0f%% | FW Rec: %.0f%%\n\n",
                w.wBtProfit*100, w.wFwProfit*100, w.wConsistency*100, w.wBtPf*100, w.wFwPf*100, w.wBtDd*100, w.wFwDd*100, w.wFwTrades*100, w.wBtRec*100, w.wFwRec*100));
            debug.append("Absolute Teil-Scores (0.0 bis 1.0):\n");
            debug.append(String.format("- BT Profit (ROI):  %.2f (Raw: %.2f%%, Depot: %.0f) * %.2f\n", nBtProfit, btRoi*100.0, deposit, w.wBtProfit));
            debug.append(String.format("- FW Profit (ROI):  %.2f (Raw: %.2f%%) * %.2f\n", nFwProfit, fwRoi*100.0, w.wFwProfit));
            debug.append(String.format("- Konsistenz:       %.2f (Ratio: %.2f) * %.2f\n", nConsist, consistency, w.wConsistency));
            debug.append(String.format("- BT PF:            %.2f (Raw: %.2f) * %.2f\n", nBtPf, bt.getProfitFactor(), w.wBtPf));
            debug.append(String.format("- FW PF:            %.2f (Raw: %.2f) * %.2f\n", nFwPf, fw != null ? fw.getProfitFactor() : 0.0, w.wFwPf));
            debug.append(String.format("- BT DD:            %.2f (Raw: %.2f%%) * %.2f\n", nBtDd, bt.getDrawdownPercent(), w.wBtDd));
            debug.append(String.format("- FW DD:            %.2f (Raw: %.2f%%) * %.2f\n", nFwDd, fw != null ? fw.getDrawdownPercent() : 0.0, w.wFwDd));
            debug.append(String.format("- FW Trades:        %.2f (Raw: %d) * %.2f\n", nFwTrades, fw != null ? fw.getTotalTrades() : 0, w.wFwTrades));
            debug.append(String.format("- BT Erholung:      %.2f (Raw: %.2f) * %.2f\n", nBtRec, bt.getRecoveryFactor(), w.wBtRec));
            debug.append(String.format("- FW Erholung:      %.2f (Raw: %.2f) * %.2f\n", nFwRec, fw != null ? fw.getRecoveryFactor() : 0.0, w.wFwRec));
            debug.append(String.format("\n=> Summe (Raw Score): %.3f / %.2f = %.3f\n", raw * total, total, raw));
        }

        // Soft penalty for too-few-trades strategies. Floor at 0.5 so a high-
        // quality pass with few trades is dampened (-50 % at most) but not
        // wiped out — the other metrics still carry weight.
        if (fw != null && fwTradesThreshold > 0) {
            int n = fw.getTotalTrades();
            if (n < fwTradesThreshold) {
                double penalty = Math.max(0.5, (double) n / fwTradesThreshold);
                raw *= penalty;
                if (debug != null) {
                    debug.append(String.format("\nSTRAFE: Zu wenige FW Trades (%d < Median-Schwelle %.1f).\n", n, fwTradesThreshold));
                    debug.append(String.format("=> Score wird mit %.2f multipliziert!\n=> Neuer Raw Score: %.3f\n", penalty, raw));
                }
            }
        }

        double finalScore = Math.round(raw * 1000.0) / 10.0;  // 0.0 – 100.0
        if (debug != null) {
            debug.append(String.format("\nEndgültiger Score (Raw * 100): %.1f", finalScore));
        }
        return finalScore;
    }
}

