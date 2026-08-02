package com.backtester.report;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Holds the sensitivity/robustness analysis result for a single optimized strategy (Pass).
 *
 * <p>Robustness is measured separately on the original backtest window (in-sample)
 * and the forward window (out-of-sample). The "BT" fields cover the backtest part,
 * the "Fw" fields cover the forward part.
 */
public class SensitivityResult {
    private final OptimizationResult.CombinedPass originalPass;

    // NOTE: these maps are NOT final on purpose. Gson populates fields via
    // reflection and bypasses field initializers, so legacy JSON entries
    // (saved before the BT/FW split) deserialize with parameterCVsFw == null.
    // The accessors below lazy-initialise to keep the rest of the code simple.

    // --- Backtest period (in-sample) ---
    private Map<String, Double> parameterCVs = new LinkedHashMap<>();
    private Map<String, List<DataPoint>> parameterCurves = new LinkedHashMap<>();
    private double overallCV = 0.0;

    // --- Forward period (out-of-sample) ---
    private Map<String, Double> parameterCVsFw = new LinkedHashMap<>();
    private Map<String, List<DataPoint>> parameterCurvesFw = new LinkedHashMap<>();
    private double overallCVFw = 0.0;

    private String status = "Pending";
    private final StringProperty kiResult = new SimpleStringProperty("");
    /** Exact SENSITIVITY_DETAIL run this result belongs to. */
    private long runTimestamp;

    public SensitivityResult(OptimizationResult.CombinedPass originalPass) {
        this.originalPass = originalPass;
    }

    public OptimizationResult.CombinedPass getOriginalPass() {
        return originalPass;
    }

    // ====================================================================
    // Backtest (in-sample) accessors
    // ====================================================================

    public Map<String, Double> getParameterCVs() {
        if (parameterCVs == null) parameterCVs = new LinkedHashMap<>();
        return parameterCVs;
    }

    public void addParameterCV(String paramName, double cv) {
        getParameterCVs().put(paramName, cv);
        overallCV = computeWorstCase(parameterCVs);
    }

    public Map<String, List<DataPoint>> getParameterCurves() {
        if (parameterCurves == null) parameterCurves = new LinkedHashMap<>();
        return parameterCurves;
    }

    public void addParameterCurve(String paramName, List<DataPoint> curve) {
        getParameterCurves().put(paramName, curve);
    }

    public double getOverallCV() {
        return overallCV;
    }

    // ====================================================================
    // Forward (out-of-sample) accessors
    // ====================================================================

    public Map<String, Double> getParameterCVsFw() {
        if (parameterCVsFw == null) parameterCVsFw = new LinkedHashMap<>();
        return parameterCVsFw;
    }

    public void addParameterCVFw(String paramName, double cv) {
        getParameterCVsFw().put(paramName, cv);
        overallCVFw = computeWorstCase(parameterCVsFw);
    }

    public Map<String, List<DataPoint>> getParameterCurvesFw() {
        if (parameterCurvesFw == null) parameterCurvesFw = new LinkedHashMap<>();
        return parameterCurvesFw;
    }

    public void addParameterCurveFw(String paramName, List<DataPoint> curve) {
        getParameterCurvesFw().put(paramName, curve);
    }

    public double getOverallCVFw() {
        return overallCVFw;
    }

    public boolean hasForwardCV() {
        return parameterCVsFw != null && !parameterCVsFw.isEmpty();
    }

    // ====================================================================

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getKiResult() {
        return kiResult.get();
    }

    public void setKiResult(String value) {
        this.kiResult.set(value);
    }

    public StringProperty kiResultProperty() {
        return kiResult;
    }

    public long getRunTimestamp() { return runTimestamp; }
    public void setRunTimestamp(long runTimestamp) { this.runTimestamp = Math.max(0L, runTimestamp); }

    /**
     * Returns the worst-case (max) CV across all parameters. A strategy is
     * only as robust as its weakest parameter, so taking the max prevents a
     * single fragile parameter from being smoothed out by stable ones.
     */
    private static double computeWorstCase(Map<String, Double> cvMap) {
        if (cvMap == null || cvMap.isEmpty()) return 0.0;
        double max = 0.0;
        for (double cv : cvMap.values()) {
            if (cv > max) max = cv;
        }
        return max;
    }

    public static class DataPoint {
        public final double paramValue;
        public final double profit;
        public DataPoint(double paramValue, double profit) {
            this.paramValue = paramValue;
            this.profit = profit;
        }
    }
}
