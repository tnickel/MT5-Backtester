package com.backtester.report;

/**
 * Result of the Step-7 out-of-sample validation backtest for one final
 * portfolio candidate.
 *
 * <p><b>Why this exists (Anti-Curve-Fitting):</b> The forward window of the
 * optimization is already consumed by the selection process (Steps 3–6 rank
 * and filter by forward metrics). After selecting the best of thousands of
 * passes, the forward period is no longer out-of-sample — some passes look
 * good there purely by chance (multiple-testing bias). Step 7 therefore runs
 * a plain backtest of each final candidate on a <i>validation window</i> that
 * was never touched by optimization or selection. Only this result is a real
 * out-of-sample estimate.
 */
public class ValidationResult {

    /** Verdict constants. */
    public static final String PASSED = "PASSED";
    public static final String FAILED = "FAILED";
    public static final String INSUFFICIENT_EVIDENCE = "INSUFFICIENT_EVIDENCE";
    public static final String NO_TRADES = "NO_TRADES";
    public static final String ERROR = "ERROR";

    /** Transparent minimum requirements for the untouched validation window. */
    public static final int MIN_VALIDATION_TRADES = 10;
    public static final double MIN_RECOVERY_FACTOR = 1.0;

    private int passNumber;
    private String validationFrom = "";
    private String validationTo = "";
    private double profit;
    private int trades;
    private double profitFactor;
    private double drawdownPercent;
    private double recoveryFactor;
    private String verdict = ERROR;
    private String message = "";

    /** Reference values from the selection phase, for comparison in reports. */
    private double btProfit;
    private double fwProfit;

    public ValidationResult() {
    }

    public ValidationResult(int passNumber) {
        this.passNumber = passNumber;
    }

    /**
     * Applies the default verdict rules. A strategy only passes when it is
     * profitable, has enough trades and recovers at least as much as its
     * maximum drawdown.
     */
    public void computeVerdict() {
        computeVerdict(MIN_VALIDATION_TRADES, MIN_RECOVERY_FACTOR);
    }

    /**
     * Applies explicit verdict thresholds. Deliberately simple and transparent:
     * <ul>
     *   <li>{@link #NO_TRADES} - the EA never traded in the window</li>
     *   <li>{@link #FAILED} - no positive finite profit or insufficient recovery</li>
     *   <li>{@link #INSUFFICIENT_EVIDENCE} - profitable, but too few trades</li>
     *   <li>{@link #PASSED} - all three requirements are met</li>
     * </ul>
     */
    public void computeVerdict(int minimumTrades, double minimumRecoveryFactor) {
        int requiredTrades = Math.max(1, minimumTrades);
        double requiredRecovery = Double.isFinite(minimumRecoveryFactor)
                ? Math.max(0.0, minimumRecoveryFactor)
                : MIN_RECOVERY_FACTOR;
        message = "";

        if (trades <= 0) {
            verdict = NO_TRADES;
            message = "Keine Trades im Validierungsfenster.";
        } else if (!Double.isFinite(profit) || profit <= 0.0) {
            verdict = FAILED;
            message = "Validierungsprofit ist nicht positiv.";
        } else if (trades < requiredTrades) {
            verdict = INSUFFICIENT_EVIDENCE;
            message = "Nur " + trades + " von mindestens " + requiredTrades
                    + " erforderlichen Trades - unzureichende Evidenz.";
        } else if (Double.isNaN(recoveryFactor) || recoveryFactor < requiredRecovery) {
            verdict = FAILED;
            message = String.format(java.util.Locale.US,
                    "Recovery Factor %.2f liegt unter dem Minimum %.2f.",
                    recoveryFactor, requiredRecovery);
        } else {
            verdict = PASSED;
        }
    }

    public boolean isPassed() { return PASSED.equals(verdict); }

    // --- Getters & Setters ---

    public int getPassNumber() { return passNumber; }
    public void setPassNumber(int passNumber) { this.passNumber = passNumber; }

    public String getValidationFrom() { return validationFrom; }
    public void setValidationFrom(String validationFrom) { this.validationFrom = validationFrom; }

    public String getValidationTo() { return validationTo; }
    public void setValidationTo(String validationTo) { this.validationTo = validationTo; }

    public double getProfit() { return profit; }
    public void setProfit(double profit) { this.profit = profit; }

    public int getTrades() { return trades; }
    public void setTrades(int trades) { this.trades = trades; }

    public double getProfitFactor() { return profitFactor; }
    public void setProfitFactor(double profitFactor) { this.profitFactor = profitFactor; }

    public double getDrawdownPercent() { return drawdownPercent; }
    public void setDrawdownPercent(double drawdownPercent) { this.drawdownPercent = drawdownPercent; }

    public double getRecoveryFactor() { return recoveryFactor; }
    public void setRecoveryFactor(double recoveryFactor) { this.recoveryFactor = recoveryFactor; }

    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public double getBtProfit() { return btProfit; }
    public void setBtProfit(double btProfit) { this.btProfit = btProfit; }

    public double getFwProfit() { return fwProfit; }
    public void setFwProfit(double fwProfit) { this.fwProfit = fwProfit; }

    /** One-line summary for reports and logs. */
    public String toSummaryLine() {
        return String.format(java.util.Locale.US,
                "Pass %d [%s]: Profit=%.2f | Trades=%d | PF=%.2f | RF=%.2f | DD=%.2f%% | (BT=%.2f, FW=%.2f) %s",
                passNumber, verdict, profit, trades, profitFactor, recoveryFactor, drawdownPercent,
                btProfit, fwProfit, message == null ? "" : message);
    }
}
