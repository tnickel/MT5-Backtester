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
        private String fromDate = "";
        private String toDate = "";
        private String tickModel = "";
        private Map<String, String> parameterValues = new LinkedHashMap<>();

        public String getFromDate() { return fromDate; }
        public void setFromDate(String fromDate) { this.fromDate = fromDate != null ? fromDate : ""; }
        public String getToDate() { return toDate; }
        public void setToDate(String toDate) { this.toDate = toDate != null ? toDate : ""; }

        public String getTickModel() { return tickModel != null ? tickModel : ""; }
        public void setTickModel(String tickModel) { this.tickModel = tickModel != null ? tickModel : ""; }

        public String getDateRange() {
            if (!fromDate.isEmpty() && !toDate.isEmpty()) {
                return fromDate + " - " + toDate;
            }
            if (!fromDate.isEmpty()) return fromDate;
            return "";
        }
        private List<double[]> equityHistory = new ArrayList<>();

        public List<double[]> getEquityHistory() { return equityHistory; }
        public void setEquityHistory(List<double[]> equityHistory) {
            this.equityHistory = deepCopyEquityHistory(equityHistory);
        }

        private static List<double[]> deepCopyEquityHistory(List<double[]> source) {
            List<double[]> copy = new ArrayList<>();
            if (source != null) {
                for (double[] point : source) {
                    copy.add(point != null ? point.clone() : null);
                }
            }
            return copy;
        }

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

        private String reportDirectory = "";

        public String getReportDirectory() { return reportDirectory != null ? reportDirectory : ""; }
        public void setReportDirectory(String reportDirectory) { this.reportDirectory = reportDirectory != null ? reportDirectory : ""; }

        public Pass copy() {
            Pass copy = new Pass();
            copy.setPassNumber(passNumber);
            copy.setProfit(profit);
            copy.setTotalTrades(totalTrades);
            copy.setProfitFactor(profitFactor);
            copy.setExpectedPayoff(expectedPayoff);
            copy.setDrawdown(drawdown);
            copy.setDrawdownPercent(drawdownPercent);
            copy.setRecoveryFactor(recoveryFactor);
            copy.setSharpeRatio(sharpeRatio);
            copy.setCustomCriterion(customCriterion);
            copy.setBalance(balance);
            copy.setFromDate(fromDate);
            copy.setToDate(toDate);
            copy.setTickModel(tickModel);
            copy.setReportDirectory(reportDirectory);
            copy.setParameterValues(parameterValues != null
                    ? new LinkedHashMap<>(parameterValues) : new LinkedHashMap<>());
            copy.setEquityHistory(equityHistory);
            return copy;
        }
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

        private Double cachedOverallScore = null;
        private String cachedFromDate = null;
        private String cachedToDate = null;

        private Pass longtermPass;
        private String strategyName;
        private String symbol;
        private String reportDirectory;

        public String getReportDirectory() {
            if (reportDirectory != null && !reportDirectory.isEmpty()) return reportDirectory;
            if (longtermPass != null && longtermPass.getReportDirectory() != null && !longtermPass.getReportDirectory().isEmpty()) {
                return longtermPass.getReportDirectory();
            }
            if (backtestPass != null && backtestPass.getReportDirectory() != null && !backtestPass.getReportDirectory().isEmpty()) {
                return backtestPass.getReportDirectory();
            }
            return "";
        }

        public void setReportDirectory(String reportDirectory) {
            this.reportDirectory = reportDirectory;
        }

        public String getSymbol() {
            return symbol;
        }

        public void setSymbol(String symbol) {
            this.symbol = symbol;
        }

        public String getStrategyName() {
            if (strategyName == null || strategyName.trim().isEmpty()) {
                strategyName = "Strat " + passNumber;
            }
            return strategyName;
        }

        public void setStrategyName(String strategyName) {
            this.strategyName = strategyName;
        }

        public CombinedPass(Pass backtestPass, Pass forwardPass, double score, double consistency, String scoreDetails) {
            this(backtestPass, forwardPass, null, score, consistency, scoreDetails);
        }

        public CombinedPass(Pass backtestPass, Pass forwardPass, Pass longtermPass, double score, double consistency, String scoreDetails) {
            this.passNumber  = backtestPass.getPassNumber();
            this.backtestPass  = backtestPass;
            this.forwardPass   = forwardPass;
            this.longtermPass  = longtermPass;
            this.score         = score;
            this.consistency   = consistency;
            this.scoreDetails  = scoreDetails;
        }

        public synchronized double getCachedOverallScore(String fromDate, String toDate) {
            if (cachedOverallScore != null 
                    && java.util.Objects.equals(cachedFromDate, fromDate) 
                    && java.util.Objects.equals(cachedToDate, toDate)) {
                return cachedOverallScore;
            }
            cachedOverallScore = com.backtester.report.RobustnessScorecardGenerator.calculateOverallScore(this, fromDate, toDate);
            cachedFromDate = fromDate;
            cachedToDate = toDate;
            return cachedOverallScore;
        }

        public int    getPassNumber()   { return passNumber; }
        public Pass   getBacktestPass() { return backtestPass; }
        public Pass   getForwardPass()  { return forwardPass; }
        public Pass   getLongtermPass() { return longtermPass; }
        public void   setLongtermPass(Pass longtermPass) { this.longtermPass = longtermPass; }

        public double getScore()        { return score; }
        public double getConsistency()  { return consistency; }
        public String getScoreDetails() { return scoreDetails; }

        public String getName() {
            return String.valueOf(passNumber);
        }

        public String getBtDateRange() {
            if (backtestPass != null && !backtestPass.getDateRange().isEmpty()) {
                return backtestPass.getDateRange();
            }
            return "-";
        }

        public String getFwDateRange() {
            if (forwardPass != null && !forwardPass.getDateRange().isEmpty()) {
                return forwardPass.getDateRange();
            }
            return "-";
        }

        public String getLtDateRange() {
            if (longtermPass != null && !longtermPass.getDateRange().isEmpty()) {
                return longtermPass.getDateRange();
            }
            return "-";
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
        public double getLtProfit()       { return longtermPass != null ? longtermPass.getProfit()          : Double.NaN; }
        public int    getLtTrades()       { return longtermPass != null ? longtermPass.getTotalTrades()     : 0; }
        public double getLtPf()           { return longtermPass != null ? longtermPass.getProfitFactor()    : Double.NaN; }
        public double getLtDd()           { return longtermPass != null ? longtermPass.getDrawdownPercent() : Double.NaN; }
        public double getLtSharpe()       { return longtermPass != null ? longtermPass.getSharpeRatio()     : Double.NaN; }
        public double getLtRecovery()     { return longtermPass != null ? longtermPass.getRecoveryFactor()  : Double.NaN; }
        public double getLtExpectedPayoff() { return longtermPass != null ? longtermPass.getExpectedPayoff() : Double.NaN; }

        /** Returns an object-isolated copy suitable for another workflow databank. */
        public CombinedPass copy() {
            CombinedPass copy = new CombinedPass(
                    backtestPass.copy(),
                    forwardPass != null ? forwardPass.copy() : null,
                    longtermPass != null ? longtermPass.copy() : null,
                    score, consistency, scoreDetails);
            copy.strategyName = strategyName;
            copy.symbol = symbol;
            copy.reportDirectory = reportDirectory;
            return copy;
        }
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
    public void setPasses(List<Pass> passes) {
        this.passes = passes;
        if (passes != null && outputDirectory != null && !outputDirectory.isEmpty()) {
            for (Pass p : passes) {
                if (p != null && (p.getReportDirectory() == null || p.getReportDirectory().isEmpty())) {
                    p.setReportDirectory(outputDirectory);
                }
            }
        }
    }
    public void addPass(Pass pass) {
        if (pass != null && outputDirectory != null && !outputDirectory.isEmpty() && pass.getReportDirectory().isEmpty()) {
            pass.setReportDirectory(outputDirectory);
        }
        this.passes.add(pass);
    }

    public List<Pass> getForwardPasses() { return forwardPasses; }
    public void setForwardPasses(List<Pass> forwardPasses) {
        this.forwardPasses = forwardPasses;
        if (forwardPasses != null && outputDirectory != null && !outputDirectory.isEmpty()) {
            for (Pass p : forwardPasses) {
                if (p != null && (p.getReportDirectory() == null || p.getReportDirectory().isEmpty())) {
                    p.setReportDirectory(outputDirectory);
                }
            }
        }
    }
    public void addForwardPass(Pass pass) {
        if (pass != null && outputDirectory != null && !outputDirectory.isEmpty() && pass.getReportDirectory().isEmpty()) {
            pass.setReportDirectory(outputDirectory);
        }
        this.forwardPasses.add(pass);
    }
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
    public void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory;
        linkPassesToOutputDirectory();
    }

    /**
     * Points every pass without a report directory at this run's output directory.
     *
     * <p>Must be called again once parsing has finished: the output directory is
     * known before the report exists, so the back-fill inside the setter runs over
     * an empty list. Without the link a pass cannot find the preset archived with
     * its run, and parameter reconstruction silently falls back to the mutable EA
     * configuration.
     */
    public void linkPassesToOutputDirectory() {
        if (outputDirectory == null || outputDirectory.isEmpty()) return;
        linkAll(passes);
        linkAll(forwardPasses);
    }

    private void linkAll(List<Pass> target) {
        if (target == null) return;
        for (Pass p : target) {
            if (p != null && (p.getReportDirectory() == null || p.getReportDirectory().isEmpty())) {
                p.setReportDirectory(outputDirectory);
            }
        }
    }

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
     * Unified score weights covering all quality dimensions that can be derived
     * from REAL MetaTrader report data: performance (BT+FW), consistency, risk,
     * Sharpe ratio, sample size, trade count, and recovery.
     *
     * <p>Values are relative — the sum is normalised automatically.
     *
     * <p><b>History:</b> The former pillars "Symmetrie" (hardcoded 0.80),
     * "Tail-Risk" (synthetically derived from an assumed loss distribution) and
     * the R²/SQN-based "Equity-Konsistenz" (computed on a randomly generated
     * equity curve) were removed because they were not based on measured data
     * and added noise or dead weight to the ranking. The Equity pillar now uses
     * the real Sharpe ratio reported by MetaTrader per pass.
     *
     * <p>This class is the <b>single source of truth</b> for the default weights.
     * All DB fallbacks (workflow ranking, scorecard, UI dialogs) must go through
     * {@link #loadFromDatabase()} / {@link #defaults()} — never hardcode defaults
     * elsewhere.
     */
    public static class ScoreWeights {
        public double wBtProfit      = 7;   // BT Profitabilität (ROI + PF)
        public double wFwProfit      = 7;   // FW Profitabilität (ROI + PF)
        public double wConsistency   = 6;   // FW/BT Konsistenz (Profit-Verhältnis)
        public double wRisk          = 3;   // Risiko-Verhältnis (Return/DD, Calmar)
        public double wEquityConsist = 3;   // Sharpe Ratio (BT+FW, echte MT5-Kennzahl)
        public double wSampleSize    = 23;  // Stichprobengröße (Trades, Jahre)
        public double wFwTrades      = 30;  // FW Trade Count
        public double wRecovery      = 21;  // Erholungsfaktor (BT+FW Recovery)
        public double recoveryMin    = 1.0; // Min threshold for recovery scaling
        public double recoveryMax    = 5.0; // Max threshold for recovery scaling

        /** Returns a copy with default weights. */
        public static ScoreWeights defaults() { return new ScoreWeights(); }

        /** Sum of all weights (used for normalisation). */
        public double total() {
            return wBtProfit + wFwProfit + wConsistency + wRisk
                 + wEquityConsist + wSampleSize
                 + wFwTrades + wRecovery;
        }

        /**
         * Loads the weights from the settings database. Missing or unparseable
         * entries fall back to the field defaults of this class, per key —
         * one bad entry does not reset the others.
         *
         * <p>This is the only place that maps DB keys to weight fields; the
         * workflow ranking (Step 3), the robustness scorecard and the UI
         * dialogs all read through here so ranking and display can never
         * disagree on defaults again.
         */
        public static ScoreWeights loadFromDatabase() {
            com.backtester.database.DatabaseManager db = com.backtester.database.DatabaseManager.getInstance();
            ScoreWeights d = defaults();
            ScoreWeights w = new ScoreWeights();
            w.wBtProfit      = readSetting(db, "opt.weight.btProfit",      d.wBtProfit);
            w.wFwProfit      = readSetting(db, "opt.weight.fwProfit",      d.wFwProfit);
            w.wConsistency   = readSetting(db, "opt.weight.consistency",   d.wConsistency);
            w.wRisk          = readSetting(db, "opt.weight.risk",          d.wRisk);
            w.wEquityConsist = readSetting(db, "opt.weight.equityConsist", d.wEquityConsist);
            w.wSampleSize    = readSetting(db, "opt.weight.sampleSize",    d.wSampleSize);
            w.wFwTrades      = readSetting(db, "opt.weight.fwTrades",      d.wFwTrades);
            w.wRecovery      = readSetting(db, "opt.weight.recovery",      d.wRecovery);
            w.recoveryMin    = readSetting(db, "opt.weight.recovery.min",  d.recoveryMin);
            w.recoveryMax    = readSetting(db, "opt.weight.recovery.max",  d.recoveryMax);
            return w;
        }

        private static double readSetting(com.backtester.database.DatabaseManager db, String key, double def) {
            try {
                String v = db.getSetting(key, String.valueOf(def));
                return Double.parseDouble(v);
            } catch (Exception e) {
                return def;
            }
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

        // Real test-range length in years (full optimization range incl. forward
        // window; falls back to 3.0 when the dates are missing/unparseable).
        double years = yearsBetween(fromDate, toDate);

        List<CombinedPass> combined = new ArrayList<>();
        for (Pass bt : passes) {
            Pass fw = fwIndex.get(bt.getPassNumber());
            if (requireForward && fw == null) continue;

            double consistency = computeConsistency(bt, fw);
            StringBuilder debug = new StringBuilder();
            double score       = computeScore(bt, fw, consistency, passes, forwardPasses,
                                              fwTradesThreshold, years, weights, debug);
            CombinedPass cp = new CombinedPass(bt, fw, score, consistency, debug.toString());
            cp.setSymbol(this.symbol);
            combined.add(cp);
        }
        return combined;
    }

    /**
     * Length of the test range in years, computed from ISO dates.
     * Returns 3.0 as a documented fallback when either date is missing or
     * unparseable (matches the historic hardcoded assumption).
     */
    public static double yearsBetween(String fromDate, String toDate) {
        if (fromDate != null && toDate != null && !fromDate.isEmpty() && !toDate.isEmpty()) {
            try {
                java.time.LocalDate start = java.time.LocalDate.parse(fromDate);
                java.time.LocalDate end = java.time.LocalDate.parse(toDate);
                long days = java.time.temporal.ChronoUnit.DAYS.between(start, end);
                if (days > 0) {
                    return Math.max(0.1, days / 365.25);
                }
            } catch (Exception ignored) {}
        }
        return 3.0;
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
     * Score = weighted sum of normalised metrics (weights normalised to sum=1).
     *
     * <p>Every pillar is derived from REAL report data (profit, trades, PF,
     * recovery, Sharpe, dates). No pillar is estimated from assumed win rates
     * or synthetic equity curves — see {@link ScoreWeights} for the history.
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
     *
     * @param years real length of the test range in years (see {@link #yearsBetween})
     */
    private static double computeScore(Pass bt, Pass fw,
                                       double consistency,
                                       List<Pass> allBt, List<Pass> allFw,
                                       double fwTradesThreshold,
                                       double years,
                                       ScoreWeights w,
                                       StringBuilder debug) {
        double total = w.total();
        if (total <= 0) total = 1.0;
        if (years <= 0) years = 3.0;

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
        // Calmar ≈ Recovery-Faktor annualisiert über die reale Testdauer
        double calmar = rdd / years;
        double nCalmar = pwl(calmar, new double[][]{{0, 0}, {1.0, 50}, {3.0, 100}});
        double sRisk = avg(nRdd, nCalmar) / 100.0;

        // ── 5. Sharpe Ratio (BT + FW) — echte MT5-Kennzahl pro Pass ──
        // Absolute Skala, damit Ranking (Step 3) und Scorecard identisch rechnen.
        double nBtSharpe = pwl(safeMetric(bt.getSharpeRatio()), SHARPE_PWL);
        double sEquityConsist;
        if (fw != null) {
            double nFwSharpe = pwl(safeMetric(fw.getSharpeRatio()), SHARPE_PWL);
            sEquityConsist = avg(nBtSharpe, nFwSharpe) / 100.0;
        } else {
            sEquityConsist = nBtSharpe / 100.0;
        }

        // ── 6. Stichprobengröße (Trades, reale Jahre) ──
        double nTrades = pwl(bt.getTotalTrades(), new double[][]{{100, 0}, {300, 50}, {1000, 100}});
        double nYears = pwl(years, new double[][]{{1, 0}, {3, 50}, {7, 100}});
        double sSample = avg(nTrades, nYears) / 100.0;
        if (bt.getTotalTrades() < 100) {
            sSample = Math.min(sSample, 0.30); // Cap at 30% for small samples
        }

        // ── 7. FW Trade Count ──
        double sFwTrades = fw != null
                ? pwl(fw.getTotalTrades(), new double[][]{{100, 0}, {300, 50}, {1000, 100}}) / 100.0
                : 0.0;

        // ── 8. Erholungsfaktor (BT + FW Recovery) ──
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
                    + w.wFwTrades      * sFwTrades
                    + w.wRecovery      * sRecovery) / total;

        if (debug != null) {
            debug.append("=== UNIFIED SCORE (8 Säulen, nur echte Messdaten) ===\n\n");
            debug.append(String.format("Gewichte: BT-Profit=%.0f | FW-Profit=%.0f | Konsistenz=%.0f | Risiko=%.0f | " +
                    "Sharpe=%.0f | Stichprobe=%.0f | FW-Trades=%.0f | Recovery=%.0f\n\n",
                    w.wBtProfit, w.wFwProfit, w.wConsistency, w.wRisk, w.wEquityConsist,
                    w.wSampleSize, w.wFwTrades, w.wRecovery));
            debug.append("Teil-Scores (0.0 – 1.0):\n");
            debug.append(String.format("- BT Profitabilität: %.2f (ROI=%.2f%%, PF=%.2f) * %.0f\n", sBtProfit, btRoi*100, bt.getProfitFactor(), w.wBtProfit));
            debug.append(String.format("- FW Profitabilität: %.2f (ROI=%.2f%%, PF=%.2f) * %.0f\n", sFwProfit, fwRoi*100, fw != null ? fw.getProfitFactor() : 0, w.wFwProfit));
            debug.append(String.format("- FW/BT Konsistenz:  %.2f (Ratio=%.2f) * %.0f\n", sConsist, consistency, w.wConsistency));
            debug.append(String.format("- Risiko-Verhältnis: %.2f (Return/DD=%.2f, Calmar=%.2f, Jahre=%.1f) * %.0f\n", sRisk, rdd, calmar, years, w.wRisk));
            debug.append(String.format("- Sharpe Ratio:      %.2f (BT=%.2f, FW=%.2f) * %.0f\n", sEquityConsist, bt.getSharpeRatio(), fw != null ? fw.getSharpeRatio() : 0, w.wEquityConsist));
            debug.append(String.format("- Stichprobengröße:  %.2f (Trades=%d, Jahre=%.1f) * %.0f\n", sSample, bt.getTotalTrades(), years, w.wSampleSize));
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

    /**
     * Piecewise-linear mapping for the MT5 Sharpe ratio → 0..100.
     * Shared by the ranking score and the robustness scorecard so both
     * display the same pillar value. Absolute scale: 0 → 0, 0.5 → 50, 2.0 → 100.
     */
    public static final double[][] SHARPE_PWL = {{0, 0}, {0.5, 50}, {2.0, 100}};

    /** Maps NaN metrics to 0 so they contribute nothing instead of poisoning the sum. */
    private static double safeMetric(double v) {
        return Double.isNaN(v) ? 0.0 : v;
    }

    /** Piecewise-linear normalisation: points = {{x0,y0},{x1,y1},...}, ascending x, y in 0..100 */
    public static double pwl(double x, double[][] points) {
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
}
