package com.backtester.report;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stores the results of a Robustness / 1D Parameter Sweep scan.
 */
public class RobustnessResult {

    private boolean success = false;
    private String message = "Not initialized";
    private String outputDirectory;
    
    // Maps the name of the swept parameter to a map of (Period Label -> OptimizationResult)
    private final Map<String, Map<String, OptimizationResult>> parameterSweeps = new LinkedHashMap<>();

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public void addSweep(String parameterName, Map<String, OptimizationResult> results) {
        parameterSweeps.put(parameterName, results);
    }
    
    public Map<String, Map<String, OptimizationResult>> getParameterSweeps() {
        return parameterSweeps;
    }
}
