package com.backtester.cli;

import com.backtester.config.AppConfig;
import com.backtester.engine.BacktestConfig;
import com.backtester.engine.BacktestRunner;
import com.backtester.report.BacktestResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Headless CLI Batch Backtesting Runner.
 * Parses a JSON configuration file, prepares executables/presets,
 * runs tests sequentially using BacktestRunner, and outputs a summary.
 */
public class CliRunner {

    private static final Logger log = LoggerFactory.getLogger(CliRunner.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void run(String configFilePath) {
        log.info("Starting CLI execution using config file: {}", configFilePath);

        Path configPath = Paths.get(configFilePath);
        if (!Files.exists(configPath)) {
            log.error("Configuration file does not exist: {}", configFilePath);
            throw new IllegalArgumentException("Configuration file does not exist: " + configFilePath);
        }

        // 1. Parse JSON Config
        BatchConfig batchConfig;
        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            batchConfig = gson.fromJson(reader, BatchConfig.class);
        } catch (Exception e) {
            log.error("Failed to parse batch configuration JSON", e);
            throw new IllegalStateException("Failed to parse batch configuration JSON: " + configFilePath, e);
        }

        if (batchConfig == null || batchConfig.runs == null || batchConfig.runs.isEmpty()) {
            log.error("No backtest runs found in configuration.");
            throw new IllegalArgumentException("No backtest runs found in configuration: " + configFilePath);
        }

        // 2. Validate environment config
        AppConfig appConfig = AppConfig.getInstance();

        // 3. Resolve output directory
        Path outputDir;
        if (batchConfig.output_directory != null && !batchConfig.output_directory.trim().isEmpty()) {
            outputDir = Paths.get(batchConfig.output_directory);
        } else {
            outputDir = appConfig.getReportsDirectory().resolve("batch_runs");
        }

        try {
            Files.createDirectories(outputDir);
            log.info("Output directory for results: {}", outputDir);
        } catch (IOException e) {
            log.error("Failed to create output directory: {}", outputDir, e);
            throw new IllegalStateException("Failed to create output directory: " + outputDir, e);
        }

        // 4. Resolve dates
        LocalDate fromDate = LocalDate.now().minusYears(1);
        LocalDate toDate = LocalDate.now();
        BatchSettings settings = batchConfig.settings != null ? batchConfig.settings : new BatchSettings();

        if (settings.from_date != null) {
            try {
                fromDate = LocalDate.parse(settings.from_date);
            } catch (DateTimeParseException e) {
                log.warn("Invalid from_date format: {}, using default: {}", settings.from_date, fromDate);
            }
        }
        if (settings.to_date != null) {
            try {
                toDate = LocalDate.parse(settings.to_date);
            } catch (DateTimeParseException e) {
                log.warn("Invalid to_date format: {}, using default: {}", settings.to_date, toDate);
            }
        }

        log.info("Batch configuration settings: Period=[{} to {}], Deposit={} {}, Leverage={}, Model={}",
                fromDate, toDate, settings.deposit, settings.currency, settings.leverage, settings.model);

        List<BatchRunResult> results = new ArrayList<>();
        int total = batchConfig.runs.size();
        int successCount = 0;

        // 5. Sequential Execution Loop
        for (int i = 0; i < total; i++) {
            RunItem run = batchConfig.runs.get(i);
            log.info("=========================================");
            log.info("Running test {}/{} - EA: {}, Symbol: {}, Period: {}", i + 1, total, run.expert_name, run.symbol, run.period);
            log.info("=========================================");

            Path srcEx = Paths.get(run.expert_path);
            if (!Files.exists(srcEx)) {
                log.error("Expert Advisor executable not found: {}", run.expert_path);
                results.add(new BatchRunResult(run, false, "Executable not found on host"));
                continue;
            }

            Path mtInstallDir = appConfig.getMtInstallDir(run.expert_path);
            Path expertsDir = appConfig.getExpertsDir(run.expert_path);
            Path scraperTempDir = expertsDir.resolve("ScraperTemp");
            try {
                Files.createDirectories(scraperTempDir);
            } catch (IOException e) {
                log.error("Failed to create temporary experts directory: {}", scraperTempDir, e);
                results.add(new BatchRunResult(run, false, "Failed to create temp directory"));
                continue;
            }

            String cleanFileName = sanitizeFileName(srcEx.getFileName().toString());
            Path destEx = scraperTempDir.resolve(cleanFileName);
            Path destSet = null;

            try {
                // Copy EA to Experts directory
                log.info("Copying EA to Experts folder: {} -> {}", srcEx, destEx);
                Files.copy(srcEx, destEx, StandardCopyOption.REPLACE_EXISTING);

                // Copy SET file if specified
                String parametersParam = "";
                if (run.set_file_path != null && !run.set_file_path.trim().isEmpty()) {
                    Path srcSet = Paths.get(run.set_file_path);
                    if (Files.exists(srcSet)) {
                        destSet = mtInstallDir.resolve("scraper_temp.set");
                        log.info("Copying SET parameter file: {} -> {}", srcSet, destSet);
                        Files.copy(srcSet, destSet, StandardCopyOption.REPLACE_EXISTING);

                        // Platform-aware presets dir: MT5 → MQL5/Profiles/Tester, MT4 → tester/
                        Path profilesSet = appConfig.getTesterProfilesDir(run.expert_path).resolve("scraper_temp.set");
                        try {
                            Files.createDirectories(profilesSet.getParent());
                            Files.copy(srcSet, profilesSet, StandardCopyOption.REPLACE_EXISTING);
                            log.info("Copying SET parameter file to profiles: {}", profilesSet);
                        } catch (IOException e) {
                            log.warn("Failed to copy SET file to profiles directory: " + e.getMessage());
                        }
                        
                        parametersParam = "scraper_temp.set";
                    } else {
                        log.warn("Parameter SET file specified but not found: {}", run.set_file_path);
                    }
                }

                boolean isOpt = settings.optimization > 0;

                if (isOpt) {
                    // Prepare OptimizationConfig
                    com.backtester.engine.OptimizationConfig optConfig = new com.backtester.engine.OptimizationConfig();
                    optConfig.setExpert("ScraperTemp\\" + cleanFileName);
                    optConfig.setExpertParameters(parametersParam);
                    optConfig.setSymbol(run.symbol != null ? run.symbol : "EURUSD");
                    optConfig.setPeriod(run.period != null ? run.period : "H1");
                    optConfig.setFromDate(fromDate);
                    optConfig.setToDate(toDate);
                    optConfig.setDeposit(settings.deposit);
                    optConfig.setCurrency(settings.currency);
                    optConfig.setLeverage(settings.leverage);
                    optConfig.setModel(settings.model);
                    optConfig.setShutdownTerminal(settings.auto_kill_mt5);
                    optConfig.setOptimizationMode(settings.optimization);
                    optConfig.setOptimizationCriterion(4); // Recovery Factor max

                    // Run Optimization
                    com.backtester.engine.OptimizationRunner runner = new com.backtester.engine.OptimizationRunner(appConfig);
                    runner.setLogCallback(msg -> System.out.println("[Runner] " + msg));
                    com.backtester.report.OptimizationResult result = runner.runOptimization(optConfig);

                    if (result != null && result.isSuccess()) {
                        log.info("Optimization finished successfully. Passes found: {}", result.getPasses().size());
                        results.add(new BatchRunResult(run, result));
                        successCount++;
                    } else {
                        String reason = (result != null) ? result.getMessage() : "Unknown runner failure";
                        log.warn("Optimization failed: {}", reason);
                        results.add(new BatchRunResult(run, false, reason));
                    }
                } else {
                    // Prepare BacktestConfig
                    BacktestConfig btConfig = new BacktestConfig();
                    btConfig.setExpert("ScraperTemp\\" + cleanFileName);
                    btConfig.setExpertParameters(parametersParam);
                    btConfig.setSymbol(run.symbol != null ? run.symbol : "EURUSD");
                    btConfig.setPeriod(run.period != null ? run.period : "H1");
                    btConfig.setFromDate(fromDate);
                    btConfig.setToDate(toDate);
                    btConfig.setDeposit(settings.deposit);
                    btConfig.setCurrency(settings.currency);
                    btConfig.setLeverage(settings.leverage);
                    btConfig.setModel(settings.model);
                    btConfig.setUseVirtualDesktop(settings.use_virtual_desktop);
                    btConfig.setAutoKillMt5(settings.auto_kill_mt5);
                    btConfig.setShutdownTerminal(settings.auto_kill_mt5);
                    btConfig.setReplaceReport(true);

                    // Run Backtest
                    BacktestRunner runner = new BacktestRunner();
                    runner.setLogCallback(msg -> System.out.println("[Runner] " + msg));
                    BacktestResult result = runner.runBacktest(btConfig);

                    if (result != null && result.isSuccess()) {
                        log.info("Test finished successfully. Profit: {}, Drawdown: {}%, Trades: {}",
                                result.getTotalProfit(), result.getMaxDrawdown(), result.getTotalTrades());
                        results.add(new BatchRunResult(run, result));
                        successCount++;
                    } else {
                        String reason = (result != null) ? result.getMessage() : "Unknown runner failure";
                        log.warn("Test failed: {}", reason);
                        results.add(new BatchRunResult(run, false, reason));
                    }
                }

            } catch (Exception e) {
                log.error("Exception during backtest/optimization run execution", e);
                results.add(new BatchRunResult(run, false, "Exception: " + e.getMessage()));
            } finally {
                // Clean up copied files to keep MT directory clean
                try {
                    Files.deleteIfExists(destEx);
                    if (destSet != null) {
                        Files.deleteIfExists(destSet);
                        try {
                            Files.deleteIfExists(mtInstallDir.resolve("MQL5").resolve("Profiles").resolve("Tester").resolve("scraper_temp.set"));
                        } catch (Exception ignored) {}
                    }
                    Files.deleteIfExists(mtInstallDir.resolve("tester_backtest.ini"));
                    Files.deleteIfExists(mtInstallDir.resolve("tester_optimization.ini"));
                    Files.deleteIfExists(scraperTempDir);
                } catch (IOException e) {
                    log.warn("Failed to clean up temporary MT files: {}", e.getMessage());
                }
            }
        }

        // 6. Write final batch_results.json
        Path resultsJsonPath = outputDir.resolve("batch_results.json");
        BatchSummary summary = new BatchSummary(successCount, total - successCount, results);
        try (Writer writer = Files.newBufferedWriter(resultsJsonPath, StandardCharsets.UTF_8)) {
            gson.toJson(summary, writer);
            log.info("Consolidated batch results written to: {}", resultsJsonPath);
        } catch (IOException e) {
            log.error("Failed to write consolidated batch results JSON", e);
        }

        log.info("=== Batch execution completed. Total: {}, Succeeded: {}, Failed: {} ===", total, successCount, total - successCount);
    }

    private static String sanitizeFileName(String fileName) {
        if (fileName == null) return null;
        int dotIdx = fileName.lastIndexOf('.');
        if (dotIdx > 0) {
            String base = fileName.substring(0, dotIdx).trim();
            String ext = fileName.substring(dotIdx).trim(); // includes the dot
            return base + ext;
        }
        return fileName.trim();
    }

    // --- JSON Model Mappings ---

    public static class BatchConfig {
        public String output_directory;
        public BatchSettings settings;
        public List<RunItem> runs;
    }

    public static class BatchSettings {
        public String from_date;
        public String to_date;
        public int deposit = 10000;
        public String currency = "USD";
        public String leverage = "1:100";
        public int model = 1; // Default 1 minute OHLC
        public int optimization = 0; // Default 0 (disabled), 2 (genetic)
        public boolean use_virtual_desktop = true;
        public boolean auto_kill_mt5 = true;
    }

    public static class RunItem {
        public String expert_name;
        public String expert_path;
        public String symbol;
        public String period;
        public String set_file_path;
    }

    public static class BatchSummary {
        public int succeeded;
        public int failed;
        public long timestamp = System.currentTimeMillis();
        public List<BatchRunResult> runs;

        public BatchSummary(int succeeded, int failed, List<BatchRunResult> runs) {
            this.succeeded = succeeded;
            this.failed = failed;
            this.runs = runs;
        }
    }

    public static class BatchRunResult {
        public String expert_name;
        public String symbol;
        public String period;
        public boolean success;
        public String error_message;
        public double profit;
        public double drawdown;
        public int trades;
        public double win_rate;
        public double profit_factor;
        public double sharpe_ratio;
        public String report_path;
        public List<com.backtester.report.OptimizationResult.Pass> optimization_passes;

        public BatchRunResult(RunItem item, boolean success, String errorMessage) {
            this.expert_name = item.expert_name;
            this.symbol = item.symbol;
            this.period = item.period;
            this.success = success;
            this.error_message = errorMessage;
        }

        public BatchRunResult(RunItem item, BacktestResult result) {
            this.expert_name = item.expert_name;
            this.symbol = item.symbol;
            this.period = item.period;
            this.success = true;
            this.profit = result.getTotalProfit();
            this.drawdown = result.getMaxDrawdown();
            this.trades = result.getTotalTrades();
            this.win_rate = result.getWinRate();
            this.profit_factor = result.getProfitFactor();
            this.sharpe_ratio = result.getSharpeRatio();
            this.report_path = result.getOutputDirectory();
        }

        public BatchRunResult(RunItem item, com.backtester.report.OptimizationResult result) {
            this.expert_name = item.expert_name;
            this.symbol = item.symbol;
            this.period = item.period;
            this.success = true;
            this.report_path = result.getOutputDirectory();
            this.optimization_passes = result.getPasses();

            if (result.getPasses() != null && !result.getPasses().isEmpty()) {
                com.backtester.report.OptimizationResult.Pass best = result.getBestByProfit();
                if (best != null) {
                    this.profit = best.getProfit();
                    this.drawdown = best.getDrawdownPercent();
                    this.trades = best.getTotalTrades();
                    this.profit_factor = best.getProfitFactor();
                    this.sharpe_ratio = best.getSharpeRatio();
                }
            }
        }
    }
}
