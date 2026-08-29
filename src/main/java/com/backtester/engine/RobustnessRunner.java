package com.backtester.engine;

import com.backtester.config.AppConfig;
import com.backtester.config.EaParameter;
import com.backtester.config.EaParameterManager;
import com.backtester.report.OptimizationResult;
import com.backtester.report.RobustnessResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Runner that orchestrates the "Robustness Scanner" functionality.
 * It sweeps selected parameters individually using the Slow Complete Algorithm,
 * fixing all other parameters.
 */
public class RobustnessRunner {

    private static final Logger log = LoggerFactory.getLogger(RobustnessRunner.class);
    /**
     * Memory cap for the sweep result map: it keeps every period's full
     * {@link OptimizationResult} alive until the whole scan has finished, and a
     * 1D sweep can produce thousands of passes (each with parameter values and
     * archived .set lines). Consumers (HTML report, flat-parameter detection)
     * work on the individual passes, so stored results cannot be reduced to
     * aggregates — instead each period keeps only its best passes by the
     * configured optimization criterion. Full reports remain on disk in the
     * run's output directory.
     */
    private static final int MAX_PASSES_PER_SWEEP_PERIOD = 50;
    private final AppConfig config;
    private final EaParameterManager eaParamManager;
    private java.util.function.Consumer<String> logCallback;
    private java.util.function.Consumer<Integer> progressCallback;
    private java.util.function.BiConsumer<Integer, Integer> progressCountCallback;
    private java.util.function.Consumer<String> currentParamCallback;
    private java.util.function.BiConsumer<String, java.util.Map<String, com.backtester.report.OptimizationResult>> paramFinishCallback;
    private volatile OptimizationRunner currentOptRunner = null;
    private volatile boolean cancelled = false;

    public RobustnessRunner(AppConfig config) {
        this.config = config;
        this.eaParamManager = new EaParameterManager();
    }

    public void setLogCallback(java.util.function.Consumer<String> logCallback) {
        this.logCallback = logCallback;
    }
    
    public void setProgressCallback(java.util.function.Consumer<Integer> progressCallback) {
        this.progressCallback = progressCallback;
    }

    public void setProgressCountCallback(java.util.function.BiConsumer<Integer, Integer> progressCountCallback) {
        this.progressCountCallback = progressCountCallback;
    }
    
    public void setCurrentParamCallback(java.util.function.Consumer<String> paramCallback) {
        this.currentParamCallback = paramCallback;
    }

    public void setParamFinishCallback(java.util.function.BiConsumer<String, java.util.Map<String, com.backtester.report.OptimizationResult>> paramFinishCallback) {
        this.paramFinishCallback = paramFinishCallback;
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

    public RobustnessResult runRobustnessScan(OptimizationConfig baseConfig, List<EaParameter> allParameters, int shifts, int shiftDays) {
        RobustnessResult result = new RobustnessResult();
        cancelled = false;

        // Robustness scan must always use the Complete Algorithm (1) because we sweep parameters individually (1D search space).
        // The Genetic Algorithm (2) in MT5 requires a minimum of 1024/2048 passes and will fail/hang on small 1D sweeps.
        int optMode = 1;
        baseConfig.setOptimizationMode(optMode);

        // Find all parameters that user wants to sweep
        List<EaParameter> toSweep = allParameters.stream()
                .filter(EaParameter::isOptimizeEnabled)
                .collect(Collectors.toList());

        if (toSweep.isEmpty()) {
            result.setMessage("No parameters selected for scanning.");
            return result;
        }

        int totalOperations = toSweep.size() * (shifts + 1);
        logMessage("Starting Robustness Sweep for " + toSweep.size() + " parameters across " + (shifts + 1) + " periods (Total Runs: " + totalOperations + ")...");

        Path testerDir = config.getTesterProfilesDir(baseConfig.getExpert());
        
        int currentCount = 0;
        long scanStartTime = System.currentTimeMillis();

        for (EaParameter sweepParam : toSweep) {
            if (cancelled) break;
            
            if (currentParamCallback != null) {
                currentParamCallback.accept(sweepParam.getName());
            }
            
            // Create an isolated .set file for this specific sweep
            List<EaParameter> isolatedParams = new ArrayList<>();
            for (EaParameter p : allParameters) {
                EaParameter copy = new EaParameter();
                copy.setName(p.getName());
                copy.setValue(p.getValue());
                copy.setOptimizeStart(p.getOptimizeStart());
                copy.setOptimizeStep(p.getOptimizeStep());
                copy.setOptimizeEnd(p.getOptimizeEnd());
                copy.setStringType(p.isStringType());
                
                // Only enable optimization if it's the current parameter
                copy.setOptimizeEnabled(p.getName().equals(sweepParam.getName()));
                isolatedParams.add(copy);
            }
            
            // Name the preset specifically for this sweep
            String presetBaseName = "Sweep_" + sweepParam.getName() + ".set";
            Path presetFile = testerDir.resolve(presetBaseName);
            
            try {
                java.nio.file.Files.deleteIfExists(presetFile);
                eaParamManager.writeSetFile(presetFile, isolatedParams, baseConfig.getExpert());
                com.backtester.workflow.MasterStrategyLineageService
                        .verifyPresetWritten(presetFile, isolatedParams);
            } catch (Exception e) {
                logMessage("ERROR creating specialized preset for " + sweepParam.getName() + ": " + e.getMessage());
                continue;
            }

            Map<String, OptimizationResult> periodMap = new LinkedHashMap<>();

            try {
                for (int i = 0; i <= shifts; i++) {
                if (cancelled) break;
                currentCount++;
                
                if (progressCallback != null) {
                    int percent = (int) (((double) (currentCount - 1) / totalOperations) * 100);
                    progressCallback.accept(percent);
                }
                
                if (progressCountCallback != null) {
                    progressCountCallback.accept(currentCount, totalOperations);
                }
                
                long paramStartTime = System.currentTimeMillis();
                String etaString = "...";
                if (currentCount > 1) {
                    long totalElapsed = paramStartTime - scanStartTime;
                    long avgPerParam = totalElapsed / (currentCount - 1);
                    long remainingParams = totalOperations - currentCount + 1;
                    long etaMs = avgPerParam * remainingParams;
                    etaString = String.format("%02d:%02d min", (etaMs/1000)/60, (etaMs/1000)%60);
                }

                // Prepare local OptimizationConfig copy
                OptimizationConfig sweepConfig = new OptimizationConfig();
                // Clone values from baseConfig
                sweepConfig.setSymbol(baseConfig.getSymbol());
                sweepConfig.setPeriod(baseConfig.getPeriod());
                sweepConfig.setExpert(baseConfig.getExpert());
                sweepConfig.setModel(baseConfig.getModel());
                
                // Apply optional date shift
                java.time.LocalDate fromDate = baseConfig.getFromDate().minusDays((long) i * shiftDays);
                java.time.LocalDate toDate = baseConfig.getToDate().minusDays((long) i * shiftDays);
                sweepConfig.setFromDate(fromDate);
                sweepConfig.setToDate(toDate);
                
                sweepConfig.setDeposit(baseConfig.getDeposit());
                sweepConfig.setCurrency(baseConfig.getCurrency());
                sweepConfig.setLeverage(baseConfig.getLeverage());
                // Sweep specifics
                sweepConfig.setExpertParameters(presetBaseName); // MT5 will find it in Tester profile
                sweepConfig.setOptimizationMode(optMode);
                sweepConfig.setOptimizationCriterion(baseConfig.getOptimizationCriterion());
                sweepConfig.setForwardMode(baseConfig.getForwardMode());
                sweepConfig.setForwardDate(baseConfig.getForwardDate());
                sweepConfig.setUseLocal(baseConfig.isUseLocal());
                sweepConfig.setAutoKillMt5(baseConfig.isAutoKillMt5());
                
                String periodLabel = fromDate + " to " + toDate;
                if (i == 0) periodLabel += " (Base)";

                // Calculate expected steps for logging
                int expectedSteps = 0;
                try {
                    double pStart = Double.parseDouble(sweepParam.getOptimizeStart());
                    double pStep = Double.parseDouble(sweepParam.getOptimizeStep());
                    double pEnd = Double.parseDouble(sweepParam.getOptimizeEnd());
                    if (pStep != 0) {
                        expectedSteps = (int) Math.abs((pEnd - pStart) / pStep) + 1;
                    }
                } catch (Exception ignored) { }

                String currentStatus = String.format("Sweep %d/%d (%s) Period: %d/%d | ETA: %s | Expected: %d steps", 
                        currentCount, totalOperations, sweepParam.getName(), i + 1, shifts + 1, etaString, expectedSteps);
                logMessage(currentStatus);

                // Run
                currentOptRunner = new OptimizationRunner(config);
                currentOptRunner.setLogCallback(msg -> {
                    if (logCallback != null) {
                        logCallback.accept(currentStatus + " -> " + msg);
                    } else {
                        log.info(currentStatus + " -> " + msg);
                    }
                });
                
                OptimizationResult optResult = currentOptRunner.runOptimization(sweepConfig);
                if (optResult != null && optResult.isSuccess()) {
                    int actualPasses = optResult.getPasses().size();
                    logMessage(String.format("Finished sweep for %s (%s). Produced %d / %d passes.",
                            sweepParam.getName(), periodLabel, actualPasses, expectedSteps));
                    trimToTopPasses(optResult, baseConfig.getOptimizationCriterion());
                    if (actualPasses > MAX_PASSES_PER_SWEEP_PERIOD) {
                        logMessage(String.format(
                                "Memory guard: kept top %d of %d passes for %s (%s); the full reports remain on disk.",
                                MAX_PASSES_PER_SWEEP_PERIOD, actualPasses, sweepParam.getName(), periodLabel));
                    }
                    periodMap.put(periodLabel, optResult);
                } else if (!cancelled) {
                    logMessage("WARNING: Sweep for " + sweepParam.getName() + " on period " + periodLabel + " failed: " + (optResult != null ? optResult.getMessage() : "null result"));
                }
            } // end period shift loop
            } finally {
                // The sweep preset is an execution input of the shifts above only;
                // deleting it right after keeps the tester profile dir from
                // growing without bound across runs.
                try {
                    java.nio.file.Files.deleteIfExists(presetFile);
                } catch (Exception ignored) {
                }
            }

            result.addSweep(sweepParam.getName(), periodMap);
            
            if (paramFinishCallback != null) {
                paramFinishCallback.accept(sweepParam.getName(), periodMap);
            }
        }

        if (cancelled) {
            if (progressCallback != null) progressCallback.accept(0);
            result.setMessage("Scan cancelled by user.");
            result.setSuccess(false);
            return result;
        }

        if (result.getParameterSweeps().isEmpty()) {
            result.setMessage("Scan abgeschlossen, aber keine Sweeps haben Ergebnisse produziert.");
            result.setSuccess(true); // Don't block, just report no data
            logMessage("WARNING: No sweeps produced results.");
            return result;
        }

        result.setSuccess(true);
        result.setMessage("Successfully scanned " + result.getParameterSweeps().size() + " parameters across " + (shifts + 1) + " periods.");
        
        // Define an overall output directory for html report
        Map<String, OptimizationResult> firstParamMap = result.getParameterSweeps().values().iterator().next();
        if (!firstParamMap.isEmpty()) {
            OptimizationResult firstRes = firstParamMap.values().iterator().next();
            if (firstRes.getOutputDirectory() != null) {
                result.setOutputDirectory(firstRes.getOutputDirectory());
            }
        }

        if (progressCallback != null) progressCallback.accept(100);
        if (progressCountCallback != null) progressCountCallback.accept(totalOperations, totalOperations);
        logMessage("Robustness Sweep completely finished.");
        return result;
    }

    /**
     * Trims the stored pass lists of one sweep period to
     * {@link #MAX_PASSES_PER_SWEEP_PERIOD} entries, keeping the best passes by
     * the configured optimization criterion (same mapping as
     * {@link OptimizationResult#getBestByCriterion}). Pass order within a period
     * is irrelevant to all consumers: the HTML report re-sorts by the swept
     * parameter value and the flat-parameter detection compares profits
     * order-independently.
     */
    private static void trimToTopPasses(OptimizationResult optResult, int criterion) {
        trimList(optResult.getPasses(), criterion);
        trimList(optResult.getForwardPasses(), criterion);
    }

    private static void trimList(List<OptimizationResult.Pass> passes, int criterion) {
        if (passes == null || passes.size() <= MAX_PASSES_PER_SWEEP_PERIOD) {
            return;
        }
        Comparator<OptimizationResult.Pass> byScore = switch (criterion) {
            case 0 -> Comparator.comparingDouble(OptimizationResult.Pass::getBalance);
            case 1 -> Comparator.comparingDouble(OptimizationResult.Pass::getProfitFactor);
            case 2 -> Comparator.comparingDouble(OptimizationResult.Pass::getExpectedPayoff);
            case 3 -> Comparator.comparingDouble((OptimizationResult.Pass p) -> -p.getDrawdownPercent());
            case 4 -> Comparator.comparingDouble(OptimizationResult.Pass::getRecoveryFactor);
            case 5 -> Comparator.comparingDouble(OptimizationResult.Pass::getSharpeRatio);
            case 6 -> Comparator.comparingDouble(OptimizationResult.Pass::getCustomCriterion);
            default -> Comparator.comparingDouble(OptimizationResult.Pass::getProfit);
        };
        List<OptimizationResult.Pass> trimmed = passes.stream()
                .sorted(byScore.reversed())
                .limit(MAX_PASSES_PER_SWEEP_PERIOD)
                .collect(Collectors.toCollection(ArrayList::new));
        passes.clear();
        passes.addAll(trimmed);
    }
}
