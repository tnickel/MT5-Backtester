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
    /**
     * Unified score weights covering ALL quality dimensions:
     * performance (BT+FW), consistency, risk, equity-stability,
     * sample size, symmetry, tail-risk, trade count, and recovery.
     *
     * Values are relative — the sum is normalised automatically.
     */
    public static class ScoreWeights {
        public double wBtProfit      = 10;  // BT Profitabilität (ROI + PF)
        public double wFwProfit      = 15;  // FW Profitabilität (ROI + PF)
        public double wConsistency   = 15;  // FW/BT Konsistenz (Profit-Verhältnis)
        public double wRisk          = 15;  // Risiko-Verhältnis (Return/DD, Calmar)
        public double wEquityConsist = 10;  // Equity-Konsistenz (R² Stability, SQN)
        public double wSampleSize    = 10;  // Stichprobengröße (Trades, Jahre)
        public double wSymmetry      =  5;  // Symmetrie (L/S Balance)
        public double wTailRisk      = 10;  // Tail-Risk (MaxLoss/AvgLoss, MaxLoss/NetProfit)
        public double wFwTrades      =  5;  // FW Trade Count
        public double wRecovery      =  5;  // Erholungsfaktor (BT+FW Recovery)
        public double recoveryMin    = 1.0; // Min threshold for recovery scaling
        public double recoveryMax    = 5.0; // Max threshold for recovery scaling

        /** Returns a copy with default weights. */
        public static ScoreWeights defaults() { return new ScoreWeights(); }

        /** Sum of all weights (used for normalisation). */
        public double total() {
            return wBtProfit + wFwProfit + wConsistency + wRisk
                 + wEquityConsist + wSampleSize + wSymmetry + wTailRisk
                 + wFwTrades + wRecovery;
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

        // ── 1. BT Profitabilität (ROI + PF) ──
        double deposit = bt.getBalance() - bt.getProfit();
        if (deposit <= 0) deposit = 10000.0;
        double btRoi = bt.getProfit() / deposit;
        double nBtRoi = pwl(btRoi, new double[][]{{0, 0}, {0.10, 50}, {0.30, 100}});
        double nBtPf = pwl(bt.getProfitFactor(), new double[][]{{1.0, 0}, {1.3, 50}, {2.0, 100}});
        double sBtProfit = avg(nBtRoi, nBtPf) / 100.0;

        // ── 2. FW Profitabilität (ROI + PF) ──
        double fwRoi = fw != null ? (fw.getProfit() / deposit) : 0.0;
        double nFwRoi = pwl(fwRoi, new double[][]{{0, 0}, {0.03, 50}, {0.10, 100}});
        double nFwPf = fw != null ? pwl(fw.getProfitFactor(), new double[][]{{1.0, 0}, {1.3, 50}, {2.0, 100}}) : 0.0;
        double sFwProfit = avg(nFwRoi, nFwPf) / 100.0;

        // ── 3. FW/BT Konsistenz ──
        double sConsist = Math.max(0.0, Math.min(1.0, (consistency - 0.2) / 0.8));

        // ── 4. Risiko-Verhältnis (Return/DD, Calmar) ──
        double rdd = bt.getRecoveryFactor();
        if (Double.isNaN(rdd) || rdd <= 0) rdd = 0.1;
        double nRdd = pwl(rdd, new double[][]{{1.0, 0}, {3.0, 50}, {8.0, 100}});
        // Calmar = annualised return / max DD — approximate with years=3 if unknown
        double calmar = rdd / 3.0;
        double nCalmar = pwl(calmar, new double[][]{{0, 0}, {1.0, 50}, {3.0, 100}});
        double sRisk = avg(nRdd, nCalmar) / 100.0;

        // ── 5. Equity-Konsistenz (R² Stability, SQN) ──
        double pf = bt.getProfitFactor();
        if (Double.isNaN(pf) || pf <= 1.0) pf = 1.05;
        double sqn = computeSqn(bt.getProfit(), bt.getTotalTrades(), pf);
        double stability = computeStability(bt.getProfit(), bt.getTotalTrades(), pf, bt.getPassNumber());
        double nStab = pwl(stability, new double[][]{{0, 0}, {0.9, 100}});
        double nSqn = pwl(sqn, new double[][]{{1.0, 0}, {2.5, 50}, {5.0, 100}});
        double sEquityConsist = avg(nStab, nSqn) / 100.0;

        // ── 6. Stichprobengröße (Trades, Jahre ≈ 3 default) ──
        double nTrades = pwl(bt.getTotalTrades(), new double[][]{{100, 0}, {300, 50}, {1000, 100}});
        double nYears = pwl(3.0, new double[][]{{1, 0}, {3, 50}, {7, 100}});  // 3 years default
        double sSample = avg(nTrades, nYears) / 100.0;
        if (bt.getTotalTrades() < 100) {
            sSample = Math.min(sSample, 0.30); // Cap at 30% for small samples
        }

        // ── 7. Symmetrie (L/S) — estimated at 0.80 since MT5 doesn't report this ──
        double symmetryVal = 0.80;
        double sSymmetry = pwl(symmetryVal, new double[][]{{0, 0}, {0.5, 50}, {1.0, 100}}) / 100.0;

        // ── 8. Tail-Risk (MaxLoss/AvgLoss, MaxLoss/NetProfit) ──
        double grossLoss = bt.getProfit() / Math.max(pf - 1.0, 0.01);
        int losses = Math.max(1, (int) Math.round(bt.getTotalTrades() * 0.45));
        double avgLoss = grossLoss / losses;
        double maxLoss = avgLoss * 2.8;
        double mlAlRatio = Math.abs(maxLoss / Math.max(Math.abs(avgLoss), 0.01));
        double mlNpRatio = Math.abs(maxLoss) / Math.max(Math.abs(bt.getProfit()), 0.01);
        double nMlAl = pwl(mlAlRatio, new double[][]{{2.0, 100}, {5.0, 50}, {10.0, 0}});
        double nMlNp = pwl(mlNpRatio, new double[][]{{0.05, 100}, {0.25, 50}, {0.5, 0}});
        double sTailRisk = avg(nMlAl, nMlNp) / 100.0;

        // ── 9. FW Trade Count ──
        double sFwTrades = fw != null
                ? Math.max(0.0, Math.min(1.0, (fw.getTotalTrades() - 5.0) / 25.0))
                : 0.0;

        // ── 10. Erholungsfaktor (BT + FW Recovery) ──
        double recRange = w.recoveryMax - w.recoveryMin;
        if (recRange <= 0.0) recRange = 1.0;
        double nBtRec = Math.max(0.0, Math.min(1.0, (bt.getRecoveryFactor() - w.recoveryMin) / recRange));
        double nFwRec = fw != null ? Math.max(0.0, Math.min(1.0, (fw.getRecoveryFactor() - w.recoveryMin) / recRange)) : 0.0;
        double sRecovery = (nBtRec + nFwRec) / 2.0;

        // ── Weighted combination ──
        double raw = (w.wBtProfit      * sBtProfit
                    + w.wFwProfit      * sFwProfit
                    + w.wConsistency   * sConsist
                    + w.wRisk          * sRisk
                    + w.wEquityConsist * sEquityConsist
                    + w.wSampleSize    * sSample
                    + w.wSymmetry      * sSymmetry
                    + w.wTailRisk      * sTailRisk
                    + w.wFwTrades      * sFwTrades
                    + w.wRecovery      * sRecovery) / total;

        if (debug != null) {
            debug.append("=== UNIFIED SCORE (10 Säulen) ===\n\n");
            debug.append(String.format("Gewichte: BT-Profit=%.0f | FW-Profit=%.0f | Konsistenz=%.0f | Risiko=%.0f | " +
                    "Equity-Konsist=%.0f | Stichprobe=%.0f | Symmetrie=%.0f | Tail-Risk=%.0f | FW-Trades=%.0f | Recovery=%.0f\n\n",
                    w.wBtProfit, w.wFwProfit, w.wConsistency, w.wRisk, w.wEquityConsist,
                    w.wSampleSize, w.wSymmetry, w.wTailRisk, w.wFwTrades, w.wRecovery));
            debug.append("Teil-Scores (0.0 – 1.0):\n");
            debug.append(String.format("- BT Profitabilität: %.2f (ROI=%.2f%%, PF=%.2f) * %.0f\n", sBtProfit, btRoi*100, bt.getProfitFactor(), w.wBtProfit));
            debug.append(String.format("- FW Profitabilität: %.2f (ROI=%.2f%%, PF=%.2f) * %.0f\n", sFwProfit, fwRoi*100, fw != null ? fw.getProfitFactor() : 0, w.wFwProfit));
            debug.append(String.format("- FW/BT Konsistenz:  %.2f (Ratio=%.2f) * %.0f\n", sConsist, consistency, w.wConsistency));
            debug.append(String.format("- Risiko-Verhältnis: %.2f (Return/DD=%.2f, Calmar=%.2f) * %.0f\n", sRisk, rdd, calmar, w.wRisk));
            debug.append(String.format("- Equity-Konsistenz: %.2f (R²=%.2f, SQN=%.2f) * %.0f\n", sEquityConsist, stability, sqn, w.wEquityConsist));
            debug.append(String.format("- Stichprobengröße:  %.2f (Trades=%d) * %.0f\n", sSample, bt.getTotalTrades(), w.wSampleSize));
            debug.append(String.format("- Symmetrie:         %.2f (L/S=%.2f) * %.0f\n", sSymmetry, symmetryVal, w.wSymmetry));
            debug.append(String.format("- Tail-Risk:         %.2f (ML/AL=%.1f, ML/NP=%.1f%%) * %.0f\n", sTailRisk, mlAlRatio, mlNpRatio*100, w.wTailRisk));
            debug.append(String.format("- FW Trades:         %.2f (Raw=%d) * %.0f\n", sFwTrades, fw != null ? fw.getTotalTrades() : 0, w.wFwTrades));
            debug.append(String.format("- Erholungsfaktor:   %.2f (BT=%.2f, FW=%.2f) * %.0f\n", sRecovery, bt.getRecoveryFactor(), fw != null ? fw.getRecoveryFactor() : 0, w.wRecovery));
            debug.append(String.format("\n=> Raw Score: %.3f\n", raw));
        }

        // Soft penalty for too-few-trades strategies
        if (fw != null && fwTradesThreshold > 0) {
            int n = fw.getTotalTrades();
            if (n < fwTradesThreshold) {
                double penalty = Math.max(0.5, (double) n / fwTradesThreshold);
                raw *= penalty;
                if (debug != null) {
                    debug.append(String.format("\nSTRAFE: Zu wenige FW Trades (%d < Median-Schwelle %.1f).\n", n, fwTradesThreshold));
                    debug.append(String.format("=> Score wird mit %.2f multipliziert => %.3f\n", penalty, raw));
                }
            }
        }

        double finalScore = Math.round(raw * 1000.0) / 10.0;  // 0.0 – 100.0
        if (debug != null) {
            debug.append(String.format("\nEndgültiger Unified Score: %.1f / 100", finalScore));
        }
        return finalScore;
    }

    // ── Scoring helper methods ──────────────────────────────────────────────

    /** Piecewise-linear normalisation: points = {{x0,y0},{x1,y1},...}, ascending x, y in 0..100 */
    private static double pwl(double x, double[][] points) {
        if (x <= points[0][0]) return points[0][1];
        if (x >= points[points.length - 1][0]) return points[points.length - 1][1];
        for (int i = 1; i < points.length; i++) {
            if (x <= points[i][0]) {
                double t = (x - points[i-1][0]) / (points[i][0] - points[i-1][0]);
                return points[i-1][1] + t * (points[i][1] - points[i-1][1]);
            }
        }
        return points[points.length - 1][1];
    }

    /** Average of two values */
    private static double avg(double a, double b) {
        return (a + b) / 2.0;
    }

    /** SQN = (mean trade profit / stddev) * sqrt(trades), capped at 10 */
    private static double computeSqn(double profit, int trades, double pf) {
        if (trades < 2 || pf <= 1.0) return 0.0;
        double grossLoss = profit / (pf - 1.0);
        double grossWin = profit + grossLoss;
        int wins = (int) Math.round(trades * 0.55);
        int losses = trades - wins;
        if (wins < 1 || losses < 1) return 0.0;
        double avgWin = grossWin / wins;
        double avgLoss = grossLoss / losses;
        double mean = profit / trades;
        double varSum = wins * Math.pow(avgWin - mean, 2) + losses * Math.pow(-avgLoss - mean, 2);
        double stdDev = Math.sqrt(varSum / trades);
        if (stdDev < 1e-9) return 0.0;
        return Math.min(10.0, (mean / stdDev) * Math.sqrt(trades));
    }

    /** R² stability of a synthetic equity curve (0 = chaotic, 1 = perfect line) */
    private static double computeStability(double profit, int trades, double pf, int passNumber) {
        if (trades < 5) return 0.0;
        double startBalance = 10000.0;
        // Generate synthetic equity curve
        double grossLoss = profit / Math.max(pf - 1.0, 0.01);
        double grossWin = profit + grossLoss;
        int wins = (int) Math.round(trades * 0.55);
        int losses = trades - wins;
        if (wins < 1 || losses < 1) return 0.5;
        double avgWin = grossWin / wins;
        double avgLoss = grossLoss / losses;
        List<Double> curve = new ArrayList<>();
        curve.add(startBalance);
        Random rng = new Random(passNumber * 31L + trades);
        double balance = startBalance;
        for (int i = 0; i < trades; i++) {
            boolean isWin = rng.nextDouble() < 0.55;
            balance += isWin ? avgWin : -avgLoss;
            curve.add(balance);
        }
        // R² via linear regression
        int n = curve.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0, sumY2 = 0;
        for (int i = 0; i < n; i++) {
            sumX += i; sumY += curve.get(i);
            sumXY += i * curve.get(i); sumX2 += (double) i * i;
            sumY2 += curve.get(i) * curve.get(i);
        }
        double denom = (n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY);
        if (denom <= 0) return 0.5;
        double r = (n * sumXY - sumX * sumY) / Math.sqrt(denom);
        return Math.max(0.0, Math.min(1.0, r * r));
    }
}

