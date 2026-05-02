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

    public void runSensitivityScan(List<SensitivityResult> targets, OptimizationConfig baseConfig, List<EaParameter> allEaParams) {
        cancelled = false;
        if (targets.isEmpty()) {
            logMessage("No passes selected for sensitivity analysis.");
            return;
        }

        Path testerDir = config.getMt5InstallDir().resolve("MQL5").resolve("Profiles").resolve("Tester");

        int totalPasses = targets.size();
        int currentPassCount = 0;

        for (SensitivityResult target : targets) {
            if (cancelled) break;
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
                
                logMessage("Pass " + pass.getPassNumber() + ": Sweeping parameter " + paramName);

                // Prepare specialized parameters
                List<EaParameter> isolatedParams = new ArrayList<>();
                for (EaParameter p : allEaParams) {
                    EaParameter copy = new EaParameter();
                    copy.setName(p.getName());
                    copy.setStringType(p.isStringType());
                    
                    // If this parameter was optimized, freeze it at its optimized value, EXCEPT the one we're sweeping
                    if (optimizedValues.containsKey(p.getName())) {
                        copy.setValue(optimizedValues.get(p.getName()));
                        copy.setOptimizeEnabled(false);
                    } else {
                        copy.setValue(p.getValue());
                        copy.setOptimizeEnabled(p.isOptimizeEnabled());
                    }

                    if (p.getName().equals(paramName)) {
                        // Calculate ranges
                        try {
                            double baseVal = Double.parseDouble(copy.getValue());
                            // Fallback if baseVal is 0
                            if (baseVal == 0.0) baseVal = 0.0001;

                            boolean isInteger = p.getOptimizeStep() != null && !p.getOptimizeStep().contains(".");
                            
                            double start = baseVal * 0.9;
                            double end = baseVal * 1.1;
                            
                            // Make sure end is always > start
                            if (start > end) {
                                double tmp = start;
                                start = end;
                                end = tmp;
                            }

                            double step = (end - start) / 10.0;
                            
                            if (isInteger) {
                                long startInt = Math.round(start);
                                long endInt = Math.round(end);
                                long stepInt = Math.max(1, Math.round(step));
                                
                                // Ensure variation
                                if (startInt == endInt) {
                                    startInt = Math.round(baseVal) - 2;
                                    endInt = Math.round(baseVal) + 2;
                                    stepInt = 1;
                                }

                                copy.setOptimizeStart(String.valueOf(startInt));
                                copy.setOptimizeStep(String.valueOf(stepInt));
                                copy.setOptimizeEnd(String.valueOf(endInt));
                            } else {
                                // For doubles, format to standard decimals to avoid precision issues
                                copy.setOptimizeStart(String.format(java.util.Locale.US, "%.5f", start));
                                copy.setOptimizeStep(String.format(java.util.Locale.US, "%.5f", step));
                                copy.setOptimizeEnd(String.format(java.util.Locale.US, "%.5f", end));
                            }
                            copy.setOptimizeEnabled(true);
                        } catch (Exception ex) {
                            logMessage("Could not calculate ranges for " + paramName + ": " + ex.getMessage());
                            copy.setOptimizeEnabled(false);
                        }
                    }
                    isolatedParams.add(copy);
                }

                // Ensure at least one parameter is enabled for optimization to prevent MT5 from running single test
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

                // Run Optimization
                OptimizationConfig sweepConfig = new OptimizationConfig();
                sweepConfig.setSymbol(baseConfig.getSymbol());
                sweepConfig.setPeriod(baseConfig.getPeriod());
                sweepConfig.setExpert(baseConfig.getExpert());
                sweepConfig.setModel(baseConfig.getModel());
                sweepConfig.setFromDate(baseConfig.getFromDate());
                sweepConfig.setToDate(baseConfig.getToDate());
                sweepConfig.setDeposit(baseConfig.getDeposit());
                sweepConfig.setCurrency(baseConfig.getCurrency());
                sweepConfig.setLeverage(baseConfig.getLeverage());
                sweepConfig.setExpertParameters(presetBaseName);
                sweepConfig.setOptimizationMode(1); // 1 = Complete algorithm
                sweepConfig.setOptimizationCriterion(baseConfig.getOptimizationCriterion());
                sweepConfig.setForwardMode(0); // No forward testing during sensitivity
                sweepConfig.setUseLocal(baseConfig.isUseLocal());

                currentOptRunner = new OptimizationRunner(config);
                currentOptRunner.setLogCallback(msg -> log.info("Sensitivity Runner: " + msg)); // Keep console clean
                OptimizationResult optResult = currentOptRunner.runOptimization(sweepConfig);

                if (optResult != null && optResult.isSuccess()) {
                    List<OptimizationResult.Pass> variants = optResult.getPasses();
                    if (!variants.isEmpty()) {
                        double sum = 0;
                        for (OptimizationResult.Pass variant : variants) {
                            sum += variant.getProfit();
                        }
                        double mean = sum / variants.size();

                        double varianceSum = 0;
                        for (OptimizationResult.Pass variant : variants) {
                            varianceSum += Math.pow(variant.getProfit() - mean, 2);
                        }
                        double variance = varianceSum / variants.size();
                        double stdDev = Math.sqrt(variance);

                        // Coefficient of Variation (CV) = StdDev / |Mean|
                        double cv = mean == 0 ? 0 : (stdDev / Math.abs(mean)) * 100.0;
                        
                        target.addParameterCV(paramName, cv);
                        
                        List<SensitivityResult.DataPoint> curve = new ArrayList<>();
                        for (OptimizationResult.Pass variant : variants) {
                            try {
                                String valStr = variant.getParameterValues().get(paramName);
                                if (valStr != null) {
                                    double pVal = Double.parseDouble(valStr);
                                    curve.add(new SensitivityResult.DataPoint(pVal, variant.getProfit()));
                                }
                            } catch (Exception ignored) {}
                        }
                        curve.sort(java.util.Comparator.comparingDouble(p -> p.paramValue));
                        target.addParameterCurve(paramName, curve);
                        
                        if (resultUpdateCallback != null) resultUpdateCallback.accept(target);
                        logMessage(String.format("Sensitivity %s -> Mean: %.2f, StdDev: %.2f, CV: %.2f%%", paramName, mean, stdDev, cv));
                    }
                } else if (!cancelled) {
                    logMessage("WARNING: Sensitivity scan failed for parameter " + paramName);
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
