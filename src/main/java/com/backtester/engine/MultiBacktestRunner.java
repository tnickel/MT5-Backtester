package com.backtester.engine;

import com.backtester.config.AppConfig;
import com.backtester.report.BacktestResult;
import com.backtester.report.MultiReportGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Orchestrates a batch of MT5 backtests, running them sequentially.
 */
public class MultiBacktestRunner extends SwingWorker<List<BacktestResult>, String> {

    private static final Logger log = LoggerFactory.getLogger(MultiBacktestRunner.class);

    private final MultiBacktestConfig batchConfig;
    private final Consumer<String> logCallback;
    private final Consumer<Integer> progressCallback;
    private final Consumer<BacktestResult> singleResultCallback;

    private BacktestRunner currentSingleRunner;
    private boolean cancelled = false;
    private Path generatedReportPath;

    public MultiBacktestRunner(MultiBacktestConfig batchConfig,
                               Consumer<String> logCallback,
                               Consumer<Integer> progressCallback,
                               Consumer<BacktestResult> singleResultCallback) {
        this.batchConfig = batchConfig;
        this.logCallback = logCallback;
        this.progressCallback = progressCallback;
        this.singleResultCallback = singleResultCallback;
    }

    @Override
    protected List<BacktestResult> doInBackground() {
        List<BacktestConfig> combinations = batchConfig.generateSingleConfigs();
        int total = combinations.size();
        List<BacktestResult> allResults = new ArrayList<>();

        logMessage("Starting batch run of " + total + " backtests...");

        for (int i = 0; i < total; i++) {
            if (isCancelled() || cancelled) {
                logMessage("Batch run was cancelled. Stopping.");
                break;
            }

            BacktestConfig singleConfig = combinations.get(i);
            logMessage("=============================================");
            logMessage("Running test " + (i + 1) + " of " + total);
            logMessage("EA: " + singleConfig.getExpert() + " | Symbol: " + singleConfig.getSymbol() + " | Period: " + singleConfig.getPeriod());
            logMessage("=============================================");

            if (progressCallback != null) {
                int percent = (int) (((double) i / total) * 100);
                progressCallback.accept(percent);
            }

            currentSingleRunner = new BacktestRunner();
            currentSingleRunner.setLogCallback(this::logMessage);

            BacktestResult result = null;
            try {
                // RUN THE BACKTEST SYNCHRONOUSLY
                result = currentSingleRunner.runBacktest(singleConfig);
                if (result == null) {
                    // It returned null. Maybe timeout, MT5 start error, or cancelled.
                    result = new BacktestResult();
                    result.setSuccess(false);
                    result.setMessage("Test failed or aborted (no result returned)");
                    result.setExpert(singleConfig.getExpert());
                    result.setSymbol(singleConfig.getSymbol());
                    result.setPeriod(singleConfig.getPeriod());
                    logMessage("WARNING: Test " + (i + 1) + " failed. Moving to next.");
                }
            } catch (Exception e) {
                log.error("Exception in multi backtester loop", e);
                result = new BacktestResult();
                result.setSuccess(false);
                result.setMessage("Exception: " + e.getMessage());
                result.setExpert(singleConfig.getExpert());
                result.setSymbol(singleConfig.getSymbol());
                result.setPeriod(singleConfig.getPeriod());
                logMessage("ERROR: Test " + (i + 1) + " threw an exception. Moving to next.");
            }

            // Publish this intermediate result to the UI table via the callback
            allResults.add(result);
            if (singleResultCallback != null) {
                singleResultCallback.accept(result);
            }
        }

        if (progressCallback != null) {
            progressCallback.accept(100);
        }

        // Generate the combined HTML report
        if (!allResults.isEmpty()) {
            logMessage("Generating Multi-Backtest HTML Report...");
            Path reportPath = MultiReportGenerator.generate(batchConfig, allResults, AppConfig.getInstance().getReportsDirectory());
            if (reportPath != null) {
                this.generatedReportPath = reportPath;
                logMessage("Report saved: " + reportPath.toString());
            } else {
                logMessage("Failed to generate HTML report.");
            }
        }

        return allResults;
    }

    public Path getGeneratedReportPath() {
        return generatedReportPath;
    }

    public void cancelBatch() {
        this.cancelled = true;
        if (currentSingleRunner != null) {
            currentSingleRunner.cancel();
        }
        cancel(true);
    }

    private void logMessage(String msg) {
        log.info(msg);
        if (logCallback != null) {
            // Forward safely to UI
            SwingUtilities.invokeLater(() -> logCallback.accept(msg));
        }
    }

    private void openHtmlReport(Path path) {
        try {
            Desktop.getDesktop().browse(path.toUri());
        } catch (Exception e) {
            log.warn("Could not auto-open HTML multi report", e);
        }
    }
}
