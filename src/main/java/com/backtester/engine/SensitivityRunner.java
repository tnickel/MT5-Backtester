package com.backtester.engine;

import com.backtester.config.AppConfig;
import com.backtester.config.EaParameter;
import com.backtester.config.EaParameterManager;
import com.backtester.report.OptimizationResult;
import com.backtester.report.SensitivityResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class SensitivityRunner {
    private static final Logger log = LoggerFactory.getLogger(SensitivityRunner.class);
    private final AppConfig config;
    private final EaParameterManager eaParamManager;
    private Consumer<String> logCallback;
    private Consumer<Integer> progressCallback;
    private Consumer<SensitivityResult> resultUpdateCallback;
    private OptimizationRunner currentOptRunner = null;
    private volatile boolean cancelled = false;
    private long lastRunTimestamp;

    public SensitivityRunner(AppConfig config) {
        this.config = config;
        this.eaParamManager = new EaParameterManager();
    }

    public void setLogCallback(Consumer<String> logCallback) {
        this.logCallback = logCallback;
    }

    public void setProgressCallback(Consumer<Integer> progressCallback) {
        this.progressCallback = progressCallback;
    }

    public void setResultUpdateCallback(Consumer<SensitivityResult> resultUpdateCallback) {
        this.resultUpdateCallback = resultUpdateCallback;
    }

    private void logMessage(String msg) {
        log.info(msg);
        if (logCallback != null) {
            logCallback.accept(msg);
        }
    }

    public void cancel() {
        cancelled = true;
        if (currentOptRunner != null) {
            currentOptRunner.cancel();
        }
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public long getLastRunTimestamp() { return lastRunTimestamp; }

    /**
     * End date of the backtest portion, mirroring MT5's forward split.
     * Logic lives in {@link ForwardSplit} so it is unit-testable — see there
     * for why exactness matters.
     */
    private java.time.LocalDate computeBacktestEndDate(OptimizationConfig cfg) {
        return ForwardSplit.computeBacktestEndDate(cfg.getFromDate(), cfg.getToDate(), cfg.getForwardMode(), cfg.getForwardDate());
    }

    /** Returns the start date of the forward window, or null if there is no forward. */
    private java.time.LocalDate computeForwardStartDate(OptimizationConfig cfg) {
        return ForwardSplit.computeForwardStartDate(cfg.getFromDate(), cfg.getToDate(), cfg.getForwardMode(), cfg.getForwardDate());
    }

    /**
     * Runs a single parameter sweep on the given period, computes Mean / StdDev / CV
     * over the variants and stores the result into the SensitivityResult.
     *
     * @param isForward true = write into FW maps, false = write into BT maps
     */
    private void runSweepForPeriod(SensitivityResult target,
                                   String paramName,
                                   String presetBaseName,
                                   OptimizationConfig baseConfig,
                                   java.time.LocalDate sweepFrom,
                                   java.time.LocalDate sweepTo,
                                   String label,
                                   boolean isForward,
                                   long runTimestamp,
                                   String sweepStart,
                                   String sweepStep,
                                   String sweepEnd) {
        OptimizationConfig sweepConfig = new OptimizationConfig();
        sweepConfig.setSymbol(baseConfig.getSymbol());
        sweepConfig.setPeriod(baseConfig.getPeriod());
        sweepConfig.setExpert(baseConfig.getExpert());
        sweepConfig.setModel(baseConfig.getModel());
        sweepConfig.setFromDate(sweepFrom);
        sweepConfig.setToDate(sweepTo);
        sweepConfig.setDeposit(baseConfig.getDeposit());
        sweepConfig.setCurrency(baseConfig.getCurrency());
        sweepConfig.setLeverage(baseConfig.getLeverage());
        sweepConfig.setExpertParameters(presetBaseName);
        sweepConfig.setOptimizationMode(1); // Complete algorithm
        sweepConfig.setOptimizationCriterion(baseConfig.getOptimizationCriterion());
        sweepConfig.setForwardMode(0);
        sweepConfig.setUseLocal(baseConfig.isUseLocal());

        currentOptRunner = new OptimizationRunner(config);
        currentOptRunner.setLogCallback(msg -> log.info("Sensitivity Runner [" + label + "]: " + msg));
        OptimizationResult optResult = currentOptRunner.runOptimization(sweepConfig);

        if (optResult == null || !optResult.isSuccess()) {
            if (!cancelled) {
                logMessage("WARNING: Sensitivity " + label + " sweep failed for parameter " + paramName);
            }
            return;
        }

        List<OptimizationResult.Pass> variants = optResult.getPasses();
        if (variants.isEmpty()) {
            logMessage("WARNING: Sensitivity " + label + " sweep returned no passes for " + paramName);
            return;
        }

        double sum = 0;
        double minProfit = Double.MAX_VALUE;
        double maxProfit = -Double.MAX_VALUE;
        for (OptimizationResult.Pass v : variants) {
            double p = v.getProfit();
            sum += p;
            if (p < minProfit) minProfit = p;
            if (p > maxProfit) maxProfit = p;
        }
        double mean = sum / variants.size();

        double varianceSum = 0;
        for (OptimizationResult.Pass v : variants) {
            varianceSum += Math.pow(v.getProfit() - mean, 2);
        }
        double stdDev = Math.sqrt(varianceSum / variants.size());

        // --- CV calculation ---
        // Use the base profit of the original strategy for this period as the denominator.
        // Floor at $1 to avoid division by zero and to always compute a real CV
        // (the old code assigned arbitrary categorical values when baseProfit ≤ $100,
        //  which especially penalized forward periods with naturally smaller profits).
        double baseProfit = isForward
                ? (target.getOriginalPass().getForwardPass() != null
                        ? target.getOriginalPass().getFwProfit() : 0.0)
                : target.getOriginalPass().getBtProfit();
        double absBase = Math.abs(baseProfit);

        double cv;
        if (stdDev < 1e-9) {
            cv = 0.0;
        } else {
            double denominator = Math.max(absBase, 1.0); // Floor at $1 for numerical stability
            cv = (stdDev / denominator) * 100.0;
        }
        // Cap at 200%. Above 100% already means "variation > base profit" =
        // extremely fragile.  Whether it's 300% or 800% changes nothing
        // about the conclusion, so we cap for cleaner display & comparison.
        cv = Math.min(cv, 200.0);

        // Determine verdict for LLM (relaxed thresholds)
        String verdict;
        if (cv < 30.0) verdict = "ROBUST";
        else if (cv <= 60.0) verdict = "ACCEPTABLE";
        else verdict = "FRAGILE";

        List<SensitivityResult.DataPoint> curve = new ArrayList<>();
        for (OptimizationResult.Pass v : variants) {
            try {
                String valStr = v.getParameterValues().get(paramName);
                if (valStr != null) {
                    double pVal = Double.parseDouble(valStr);
                    curve.add(new SensitivityResult.DataPoint(pVal, v.getProfit()));
                }
            } catch (Exception ignored) {}
        }
        curve.sort(java.util.Comparator.comparingDouble(p -> p.paramValue));

        if (isForward) {
            target.addParameterCurveFw(paramName, curve);
            target.addParameterCVFw(paramName, cv);
        } else {
            target.addParameterCurve(paramName, curve);
            target.addParameterCV(paramName, cv);
        }

        // --- Persist to normalized SENSITIVITY_DETAIL table ---
        try {
            // Build curve JSON
            StringBuilder curveJsonBuilder = new StringBuilder("[");
            for (int i = 0; i < curve.size(); i++) {
                if (i > 0) curveJsonBuilder.append(",");
                curveJsonBuilder.append(String.format(java.util.Locale.US,
                        "{\"paramValue\":%.5f,\"profit\":%.2f}",
                        curve.get(i).paramValue, curve.get(i).profit));
            }
            curveJsonBuilder.append("]");

            String baseValue = target.getOriginalPass().getBacktestPass()
                    .getParameterValues().get(paramName);

            com.backtester.database.DatabaseManager.getInstance().saveSensitivityDetail(
                    runTimestamp,
                    target.getOriginalPass().getPassNumber(),
                    target.getOriginalPass().getStrategyName(),
                    baseConfig.getExpert(),
                    baseConfig.getSymbol(),
                    paramName,
                    baseValue != null ? baseValue : "",
                    isForward ? "FW" : "BT",
                    baseProfit, mean, stdDev, cv,
                    minProfit, maxProfit, variants.size(),
                    sweepStart != null ? sweepStart : "",
                    sweepStep != null ? sweepStep : "",
                    sweepEnd != null ? sweepEnd : "",
                    curveJsonBuilder.toString(),
                    verdict
            );
        } catch (Exception e) {
            log.warn("Failed to persist sensitivity detail to DB: {}", e.getMessage());
        }

        if (resultUpdateCallback != null) resultUpdateCallback.accept(target);
        logMessage(String.format("Sensitivity %s [%s] -> Mean: %.2f, StdDev: %.2f, BaseProfit: %.2f, CV: %.2f%% [%s]",
                paramName, label, mean, stdDev, baseProfit, cv, verdict));
    }

    public void runSensitivityScan(List<SensitivityResult> targets, OptimizationConfig baseConfig, List<EaParameter> allEaParams) {
        runSensitivityScan(targets, baseConfig, allEaParams, null, null, null, null, null);
    }

    public void runSensitivityScan(List<SensitivityResult> targets, OptimizationConfig baseConfig, List<EaParameter> allEaParams,
                                   Double customSweepPct, Integer customSteps, Integer customTimeShifts, Integer customShiftDays, String excludedParamsStr) {
        cancelled = false;
        lastRunTimestamp = 0L;
        if (targets.isEmpty()) {
            logMessage("No passes selected for sensitivity analysis.");
            return;
        }

        Path testerDir = config.getTesterProfilesDir(baseConfig.getExpert());
        long runTimestamp = System.currentTimeMillis();
        lastRunTimestamp = runTimestamp;

        int totalPasses = targets.size();
        int currentPassCount = 0;

        java.util.Set<String> excludedSet = new java.util.HashSet<>();
        if (excludedParamsStr != null && !excludedParamsStr.isBlank()) {
            for (String token : excludedParamsStr.split("[,;\\s]+")) {
                if (!token.isBlank()) {
                    excludedSet.add(token.trim().toLowerCase(java.util.Locale.US));
                }
            }
        }

        for (SensitivityResult target : targets) {
            if (cancelled) break;
            target.setRunTimestamp(runTimestamp);
            currentPassCount++;
            
            target.setStatus("Running...");
            if (resultUpdateCallback != null) resultUpdateCallback.accept(target);

            OptimizationResult.Pass pass = target.getOriginalPass().getBacktestPass();
            Map<String, String> optimizedValues = pass.getParameterValues();
            
            List<String> optimizedParamNames = new ArrayList<>(optimizedValues.keySet());
            if (optimizedParamNames.isEmpty()) {
                target.setStatus("Skipped (No optimized params)");
                if (resultUpdateCallback != null) resultUpdateCallback.accept(target);
                continue;
            }

            for (String paramName : optimizedParamNames) {
                if (cancelled) break;

                if (excludedSet.contains(paramName.toLowerCase(java.util.Locale.US))) {
                    logMessage("Pass " + pass.getPassNumber() + ": Skipping excluded parameter '" + paramName + "' per task configuration.");
                    continue;
                }

                // ----- Find the matching EaParameter definition -----
                EaParameter originalParam = null;
                for (EaParameter p : allEaParams) {
                    if (p.getName().equals(paramName)) { originalParam = p; break; }
                }

                // Skip string/enum parameters
                if (originalParam != null && originalParam.isStringType()) {
                    logMessage("Pass " + pass.getPassNumber() + ": Skipping enum/string parameter '" + paramName + "' (not suitable for sensitivity sweep).");
                    continue;
                }

                // Detect boolean-like / tiny-enum integer parameters
                if (originalParam != null && !originalParam.isStringType()) {
                    try {
                        String optStartStr = originalParam.getOptimizeStart();
                        String optEndStr   = originalParam.getOptimizeEnd();
                        String optStepStr  = originalParam.getOptimizeStep();
                        if (optStartStr != null && !optStartStr.isEmpty() && optEndStr != null && !optEndStr.isEmpty() && optStepStr != null && !optStepStr.isEmpty()) {
                            double oStart = Double.parseDouble(optStartStr);
                            double oEnd   = Double.parseDouble(optEndStr);
                            double oStep  = Double.parseDouble(optStepStr);
                            if (oStep > 0) {
                                long distinctValues = (long) ((oEnd - oStart) / oStep) + 1;
                                if (distinctValues <= 3) {
                                    logMessage("Pass " + pass.getPassNumber() + ": Skipping parameter '" + paramName
                                            + "' (only " + distinctValues + " distinct values in optimization range "
                                            + optStartStr + ".." + optEndStr + " step " + optStepStr + " → effectively enum/boolean).");
                                    continue;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // Heuristic for categorical / boolean parameters even if they lack optimization bounds
                String lowerParam = paramName.toLowerCase();
                if (lowerParam.startsWith("type") || lowerParam.startsWith("mode") || lowerParam.startsWith("use") 
                    || lowerParam.startsWith("enable") || lowerParam.startsWith("is") || lowerParam.startsWith("has")
                    || lowerParam.contains("boolean") || lowerParam.contains("enum")) {
                    logMessage("Pass " + pass.getPassNumber() + ": Skipping parameter '" + paramName + "' (name implies categorical/boolean).");
                    continue;
                }
                
                String valCheck = optimizedValues.get(paramName);
                if ("true".equalsIgnoreCase(valCheck) || "false".equalsIgnoreCase(valCheck)) {
                    logMessage("Pass " + pass.getPassNumber() + ": Skipping parameter '" + paramName + "' (value is boolean).");
                    continue;
                }

                logMessage("Pass " + pass.getPassNumber() + ": Sweeping parameter " + paramName);

                // Prepare specialized parameters
                List<EaParameter> isolatedParams = new ArrayList<>();
                String sweepStartStr = null, sweepStepStr = null, sweepEndStr = null;
                for (EaParameter p : allEaParams) {
                    EaParameter copy = new EaParameter();
                    copy.setName(p.getName());
                    copy.setStringType(p.isStringType());
                    
                    if (optimizedValues.containsKey(p.getName())) {
                        copy.setValue(optimizedValues.get(p.getName()));
                    } else {
                        copy.setValue(p.getValue());
                    }
                    // Crucial: Only the target paramName being swept must be optimizeEnabled = true in MT5 preset!
                    copy.setOptimizeEnabled(false);

                    if (p.getName().equals(paramName)) {
                        try {
                            double baseVal = Double.parseDouble(copy.getValue());
                            if (baseVal == 0.0) baseVal = 0.0001;

                            boolean isInteger = p.getOptimizeStep() != null && !p.getOptimizeStep().contains(".");

                            double origStep = 0, origStart = 0, origEnd = 0;
                            boolean hasOrigRange = false;
                            try {
                                if (p.getOptimizeStep() != null && p.getOptimizeStart() != null && p.getOptimizeEnd() != null) {
                                    origStep  = Double.parseDouble(p.getOptimizeStep());
                                    origStart = Double.parseDouble(p.getOptimizeStart());
                                    origEnd   = Double.parseDouble(p.getOptimizeEnd());
                                    hasOrigRange = origStep > 0;
                                }
                            } catch (Exception ignored) {}

                            double start, end, step;
                            double effectiveSweepPct = (customSweepPct != null && customSweepPct > 0.0) ? customSweepPct : 0.05;
                            int effectiveSteps = (customSteps != null && customSteps > 0) ? customSteps : 10;

                            if (hasOrigRange && (customSweepPct == null || customSweepPct <= 0.0)) {
                                step  = origStep;
                                start = baseVal - 2 * step;
                                end   = baseVal + 2 * step;
                                start = Math.max(start, origStart);
                                end   = Math.min(end, origEnd);
                            } else {
                                start = baseVal * (1.0 - effectiveSweepPct);
                                end   = baseVal * (1.0 + effectiveSweepPct);
                                step  = (end - start) / (double) effectiveSteps;
                            }

                            if (start > end) {
                                double tmp = start; start = end; end = tmp;
                            }

                            if (isInteger) {
                                long startInt = Math.round(start);
                                long endInt = Math.round(end);
                                long stepInt = Math.max(1, Math.round(step));
                                if (startInt == endInt) {
                                    startInt = Math.round(baseVal) - 2;
                                    endInt = Math.round(baseVal) + 2;
                                    stepInt = 1;
                                }
                                long distinctCount = (endInt - startInt) / stepInt + 1;
                                if (distinctCount < 3) {
                                    startInt = Math.round(baseVal) - 3 * stepInt;
                                    endInt   = Math.round(baseVal) + 3 * stepInt;
                                }
                                copy.setOptimizeStart(String.valueOf(startInt));
                                copy.setOptimizeStep(String.valueOf(stepInt));
                                copy.setOptimizeEnd(String.valueOf(endInt));
                            } else {
                                copy.setOptimizeStart(String.format(java.util.Locale.US, "%.5f", start));
                                copy.setOptimizeStep(String.format(java.util.Locale.US, "%.5f", step));
                                copy.setOptimizeEnd(String.format(java.util.Locale.US, "%.5f", end));
                            }
                            copy.setOptimizeEnabled(true);
                            // Capture sweep range for DB persistence
                            sweepStartStr = copy.getOptimizeStart();
                            sweepStepStr  = copy.getOptimizeStep();
                            sweepEndStr   = copy.getOptimizeEnd();
                            logMessage(String.format("  Range: %s .. %s (step %s)",
                                    sweepStartStr, sweepEndStr, sweepStepStr));
                        } catch (Exception ex) {
                            logMessage("Could not calculate ranges for " + paramName + ": " + ex.getMessage());
                            copy.setOptimizeEnabled(false);
                        }
                    }
                    isolatedParams.add(copy);
                }

                boolean anyEnabled = isolatedParams.stream().anyMatch(EaParameter::isOptimizeEnabled);
                if (!anyEnabled) {
                    logMessage("No variation possible for " + paramName + ". Skipping.");
                    continue;
                }

                String presetBaseName = "Sensitivity_" + pass.getPassNumber() + "_" + paramName + ".set";
                Path presetFile = testerDir.resolve(presetBaseName);

                try {
                    eaParamManager.writeSetFile(presetFile, isolatedParams, baseConfig.getExpert());
                } catch (Exception e) {
                    logMessage("ERROR creating specialized preset for " + paramName + ": " + e.getMessage());
                    continue;
                }

                // ---- Backtest period sweep (in-sample) ----
                java.time.LocalDate btFrom = baseConfig.getFromDate();
                java.time.LocalDate btTo = computeBacktestEndDate(baseConfig);
                runSweepForPeriod(target, paramName, presetBaseName, baseConfig,
                        btFrom, btTo, "BT", false,
                        runTimestamp, sweepStartStr, sweepStepStr, sweepEndStr);

                if (cancelled) break;

                // ---- Forward period sweep (out-of-sample) ----
                java.time.LocalDate fwFrom = computeForwardStartDate(baseConfig);
                java.time.LocalDate fwTo = baseConfig.getToDate();
                if (fwFrom != null && fwTo != null && fwFrom.isBefore(fwTo)) {
                    runSweepForPeriod(target, paramName, presetBaseName, baseConfig,
                            fwFrom, fwTo, "FW", true,
                            runTimestamp, sweepStartStr, sweepStepStr, sweepEndStr);
                } else {
                    logMessage("Pass " + pass.getPassNumber() + " has no forward period - skipping FW sweep for " + paramName);
                }
            } // end parameter loop

            if (cancelled) break;
            target.setStatus("Completed");
            if (resultUpdateCallback != null) resultUpdateCallback.accept(target);
            
            if (progressCallback != null) {
                progressCallback.accept((int) (((double) currentPassCount / totalPasses) * 100));
            }
        } // end targets loop

        if (cancelled) {
            logMessage("Sensitivity Analysis cancelled.");
            if (progressCallback != null) progressCallback.accept(0);
        } else {
            logMessage("Sensitivity Analysis completed.");
            if (progressCallback != null) progressCallback.accept(100);
        }
    }
}
