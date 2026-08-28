package com.backtester.engine;

import com.backtester.config.AppConfig;
import com.backtester.report.BacktestResult;
import com.backtester.report.MultiReportGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Orchestrates a batch of MT5 backtests, running them sequentially on a daemon
 * thread. Callbacks are invoked on that worker thread — UI callers marshal to
 * the FX thread themselves.
 */
public class MultiBacktestRunner {

    private static final Logger log = LoggerFactory.getLogger(MultiBacktestRunner.class);

    private final MultiBacktestConfig batchConfig;
    private final Consumer<String> logCallback;
    private final java.util.function.BiConsumer<Integer, Integer> progressCallback;
    private final Consumer<BacktestResult> singleResultCallback;

    private volatile BacktestRunner currentSingleRunner;
    private volatile boolean cancelled = false;
    private volatile Thread workerThread;
    private Path generatedReportPath;

    public MultiBacktestRunner(MultiBacktestConfig batchConfig,
                               Consumer<String> logCallback,
                               java.util.function.BiConsumer<Integer, Integer> progressCallback,
                               Consumer<BacktestResult> singleResultCallback) {
        this.batchConfig = batchConfig;
        this.logCallback = logCallback;
        this.progressCallback = progressCallback;
        this.singleResultCallback = singleResultCallback;
    }

    /**
     * Starts the batch on a daemon thread; {@link #done()} is invoked on that
     * thread after the batch finishes (successfully, cancelled or failed).
     */
    public void execute() {
        Thread thread = new Thread(() -> {
            try {
                runBatch();
            } finally {
                done();
            }
        }, "multi-backtest-runner");
        thread.setDaemon(true);
        workerThread = thread;
        thread.start();
    }

    public List<BacktestResult> runBatch() {
        List<BacktestConfig> combinations = batchConfig.generateSingleConfigs();
        int total = combinations.size();
        List<BacktestResult> allResults = new ArrayList<>();

        logMessage("Starting batch run of " + total + " backtests...");

        for (int i = 0; i < total; i++) {
            if (cancelled || Thread.currentThread().isInterrupted()) {
                logMessage("Batch run was cancelled. Stopping.");
                break;
            }

            BacktestConfig singleConfig = combinations.get(i);
            logMessage("=============================================");
            logMessage("Running test " + (i + 1) + " of " + total);
            logMessage("EA: " + singleConfig.getExpert() + " | Symbol: " + singleConfig.getSymbol() + " | Period: " + singleConfig.getPeriod());
            logMessage("=============================================");

            if (progressCallback != null) {
                progressCallback.accept(i + 1, total);
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
            progressCallback.accept(total, total);
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

    /** Override to react to batch completion; invoked on the worker thread. */
    protected void done() {
    }

    public Path getGeneratedReportPath() {
        return generatedReportPath;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel(boolean mayInterruptIfRunning) {
        this.cancelled = true;
        if (currentSingleRunner != null) {
            currentSingleRunner.cancel();
        }
        if (mayInterruptIfRunning) {
            Thread thread = workerThread;
            if (thread != null) {
                thread.interrupt();
            }
        }
    }

    private void logMessage(String msg) {
        log.info(msg);
        if (logCallback != null) {
            logCallback.accept(msg);
        }
    }
}
