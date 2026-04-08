package com.backtester.engine;

import com.backtester.config.AppConfig;
import com.backtester.report.OptimizationReportParser;
import com.backtester.report.OptimizationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Executes a single optimization run in MetaTrader 5.
 */
public class OptimizationRunner {
    private static final Logger log = LoggerFactory.getLogger(OptimizationRunner.class);
    private static final String REPORT_FILENAME = "OptimizationReport";

    private final AppConfig config;
    private final IniGenerator iniGenerator;
    private final OptimizationReportParser parser;
    private Process currentProcess;
    private boolean cancelled = false;
    private Consumer<String> logCallback;

    public OptimizationRunner(AppConfig config) {
        this.config = config;
        this.iniGenerator = new IniGenerator();
        this.parser = new OptimizationReportParser();
    }

    public void setLogCallback(Consumer<String> logCallback) {
        this.logCallback = logCallback;
    }

    private void logMessage(String msg) {
        log.info(msg);
        if (logCallback != null) {
            logCallback.accept(msg);
        }
    }

    public OptimizationResult runOptimization(OptimizationConfig optConfig) {
        cancelled = false;
        OptimizationResult result = new OptimizationResult();
        result.setExpert(optConfig.getExpert());
        result.setSymbol(optConfig.getSymbol());
        result.setPeriod(optConfig.getPeriod());

        try {
            // 1. Setup paths
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String outputFolderName = optConfig.toDirectoryName() + "_" + timestamp;
            Path outputDir = Paths.get("backtest_reports", outputFolderName).toAbsolutePath();
            Files.createDirectories(outputDir);
            result.setOutputDirectory(outputDir.toString());
            logMessage("Created output directory: " + outputDir);

            // 2. Cleanup old reports
            String terminalPath = config.getMt5TerminalPath();
            Path mt5Dir = Paths.get(terminalPath).getParent();
            cleanupOldReports(mt5Dir, REPORT_FILENAME);

            // 3. Generate tester.ini
            Path iniPath = outputDir.resolve("tester.ini");
            iniGenerator.generateForOptimization(optConfig, iniPath, REPORT_FILENAME);
            logMessage("Generated optimization tester.ini");

            // 4. Copy tester.ini to MT5 directory to avoid path-with-spaces issues
            // (Java's ProcessBuilder quotes the entire /config: argument when the path has spaces,
            //  which MT5 doesn't parse correctly)
            Path mt5TesterIni = mt5Dir.resolve("tester_optimization.ini");
            Files.copy(iniPath, mt5TesterIni, StandardCopyOption.REPLACE_EXISTING);
            logMessage("Copied tester.ini to MT5 directory: " + mt5TesterIni);

            // 5. Build and start process
            ProcessBuilder pb;
            if (config.isPortableMode()) {
                pb = new ProcessBuilder(terminalPath, "/portable", "/config:" + mt5TesterIni.toAbsolutePath().toString());
            } else {
                pb = new ProcessBuilder(terminalPath, "/config:" + mt5TesterIni.toAbsolutePath().toString());
            }

            logMessage("Starting MT5 optimization...");
            currentProcess = pb.start();

            // Wait for completion
            int exitCode = currentProcess.waitFor();
            logMessage("MT5 process exited with code " + exitCode);

            if (cancelled) {
                result.setMessage("Optimization was cancelled.");
                return result;
            }

            // 5. Find and parse report
            // MT5 optimization reports are typically .xml files
            Path reportXml = mt5Dir.resolve(REPORT_FILENAME + ".xml");
            if (Files.exists(reportXml)) {
                Path destXml = outputDir.resolve("optimization_report.xml");
                Files.copy(reportXml, destXml, StandardCopyOption.REPLACE_EXISTING);
                
                // Parse it
                try {
                    parser.parse(destXml, result);
                    result.setSuccess(true);
                    logMessage("Parsed optimization results: " + result.getPasses().size() + " passes found.");
                } catch (Exception e) {
                    log.error("Failed to parse optimization XML", e);
                    result.setMessage("Failed to parse optimization report: " + e.getMessage());
                }
            } else {
                logMessage("Warning: Optimization report XML not found at " + reportXml + " — no passes produced.");
                result.setMessage("Keine Daten — Optimierung hat keine Ergebnisse produziert.");
                result.setSuccess(true); // Treat as success with 0 passes, don't block workflow
            }

            // Look for forward test report if enabled
            if (optConfig.getForwardMode() > 0) {
                Path forwardXml = mt5Dir.resolve(REPORT_FILENAME + ".forward.xml");
                if (Files.exists(forwardXml)) {
                    Path destForwardXml = outputDir.resolve("optimization_forward.xml");
                    Files.copy(forwardXml, destForwardXml, StandardCopyOption.REPLACE_EXISTING);
                    try {
                        parser.parseForward(destForwardXml, result);
                        logMessage("Parsed forward test results: " + result.getForwardPasses().size() + " passes found.");
                    } catch (Exception e) {
                         log.warn("Failed to parse forward test XML", e);
                    }
                }
            }

        } catch (InterruptedException e) {
            logMessage("Optimization interrupted.");
            Thread.currentThread().interrupt();
            result.setMessage("Interrupted");
        } catch (Exception e) {
            log.error("Error running optimization", e);
            result.setMessage("Error: " + e.getMessage());
        } finally {
            if (currentProcess != null && currentProcess.isAlive()) {
                currentProcess.destroyForcibly();
            }
        }

        return result;
    }

    private void cleanupOldReports(Path mt5Dir, String reportBaseName) {
        try (Stream<Path> files = Files.list(mt5Dir)) {
            files.filter(p -> {
                String name = p.getFileName().toString();
                return name.startsWith(reportBaseName) && name.endsWith(".xml") && !Files.isDirectory(p);
            }).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    log.warn("Could not delete old report file: " + p, e);
                }
            });
        } catch (IOException e) {
            log.warn("Could not clean up old reports in " + mt5Dir, e);
        }
    }

    public void cancel() {
        cancelled = true;
        if (currentProcess != null && currentProcess.isAlive()) {
            currentProcess.destroyForcibly();
            logMessage("Optimization process terminated.");
        }
    }
}
