package com.backtester.report;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the sensitivity/robustness analysis result for a single optimized strategy (Pass).
 */
public class SensitivityResult {
    private final OptimizationResult.CombinedPass originalPass;
    private final Map<String, Double> parameterCVs = new LinkedHashMap<>();
    private double overallCV = 0.0;
    private String status = "Pending";

    public SensitivityResult(OptimizationResult.CombinedPass originalPass) {
        this.originalPass = originalPass;
    }

    public OptimizationResult.CombinedPass getOriginalPass() {
        return originalPass;
    }

    public Map<String, Double> getParameterCVs() {
        return parameterCVs;
    }

    public void addParameterCV(String paramName, double cv) {
        parameterCVs.put(paramName, cv);
        recalculateOverallCV();
    }

    private void recalculateOverallCV() {
        if (parameterCVs.isEmpty()) {
            overallCV = 0.0;
            return;
        }
        double sum = 0.0;
        for (double cv : parameterCVs.values()) {
            sum += cv;
        }
        overallCV = sum / parameterCVs.size();
    }

    public double getOverallCV() {
        return overallCV;
    }

    public String getStatus() {
        return status;
    }

    public static class DataPoint {
        public final double paramValue;
        public final double profit;
        public DataPoint(double paramValue, double profit) {
            this.paramValue = paramValue;
            this.profit = profit;
        }
    }

    private final Map<String, List<DataPoint>> parameterCurves = new LinkedHashMap<>();

    public void addParameterCurve(String paramName, List<DataPoint> curve) {
        parameterCurves.put(paramName, curve);
    }

    public Map<String, List<DataPoint>> getParameterCurves() {
        return parameterCurves;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
