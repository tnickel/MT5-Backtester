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
    private final AppConfig config;
    private final EaParameterManager eaParamManager;
    private java.util.function.Consumer<String> logCallback;
    private java.util.function.Consumer<Integer> progressCallback;
    private java.util.function.Consumer<String> currentParamCallback;
    private java.util.function.BiConsumer<String, java.util.Map<String, com.backtester.report.OptimizationResult>> paramFinishCallback;
    private OptimizationRunner currentOptRunner = null;
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

        // Force complete algorithm
        baseConfig.setOptimizationMode(1); // 1 = Slow Complete Algorithm in MT5

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

        Path baseMt5Path = config.getMt5InstallDir();
        Path testerDir = baseMt5Path.resolve("MQL5").resolve("Profiles").resolve("Tester");
        
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
                
                // Only enable optimization if it's the current parameter
                copy.setOptimizeEnabled(p.getName().equals(sweepParam.getName()));
                isolatedParams.add(copy);
            }
            
            // Name the preset specifically for this sweep
            String presetBaseName = "Sweep_" + sweepParam.getName() + ".set";
            Path presetFile = testerDir.resolve(presetBaseName);
            
            try {
                eaParamManager.writeSetFile(presetFile, isolatedParams, baseConfig.getExpert());
            } catch (Exception e) {
                logMessage("ERROR creating specialized preset for " + sweepParam.getName() + ": " + e.getMessage());
                continue;
            }

            Map<String, OptimizationResult> periodMap = new LinkedHashMap<>();

            for (int i = 0; i <= shifts; i++) {
                if (cancelled) break;
                currentCount++;
                
                if (progressCallback != null) {
                    int percent = (int) (((double) (currentCount - 1) / totalOperations) * 100);
                    progressCallback.accept(percent);
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
                sweepConfig.setOptimizationMode(1); // 1 = Complete 
                sweepConfig.setOptimizationCriterion(baseConfig.getOptimizationCriterion());
                sweepConfig.setForwardMode(baseConfig.getForwardMode());
                sweepConfig.setForwardDate(baseConfig.getForwardDate());
                sweepConfig.setUseLocal(baseConfig.isUseLocal());
                
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
                    periodMap.put(periodLabel, optResult);
                } else if (!cancelled) {
                    logMessage("WARNING: Sweep for " + sweepParam.getName() + " on period " + periodLabel + " failed: " + (optResult != null ? optResult.getMessage() : "null result"));
                }
            } // end period shift loop

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
        logMessage("Robustness Sweep completely finished.");
        return result;
    }
}
