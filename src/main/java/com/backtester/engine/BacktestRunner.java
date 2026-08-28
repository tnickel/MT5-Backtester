package com.backtester.engine;

import com.backtester.config.AppConfig;
import com.backtester.config.MetaTraderPlatform;
import com.backtester.report.BacktestResult;
import com.backtester.report.BacktestArtifactReplayResolver;
import com.backtester.report.BacktestStatisticsArtifact;
import com.backtester.report.ReportParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Orchestrates the execution of a single MetaTrader backtest (MT4 or MT5):
 * 1. Creates the output subdirectory
 * 2. Generates the tester.ini
 * 3. Launches terminal.exe/terminal64.exe via ProcessBuilder
 * 4. Consumes stdout/stderr to prevent 64KB deadlock
 * 5. Waits for process completion
 * 6. Searches for and copies the report from MetaTrader directory
 * 7. Parses the resulting report (HTM or XML)
 */
public class BacktestRunner {

    private static final Logger log = LoggerFactory.getLogger(BacktestRunner.class);
    private final AppConfig config;
    private final IniGenerator iniGenerator;
    private final ReportParser reportParser;
    private Consumer<String> logCallback;
    private volatile boolean cancelled = false;
    private volatile Process currentProcess;

    /** The report filename used in the INI (without path) */
    private static final String REPORT_FILENAME = "BacktestReport";

    public BacktestRunner() {
        this.config = AppConfig.getInstance();
        this.iniGenerator = new IniGenerator();
        this.reportParser = new ReportParser();
    }

    /**
     * Set a callback to receive log messages (for GUI display).
     */
    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
    }

    /**
     * Runs a backtest with the given configuration.
     * This is a BLOCKING call — run it in a background thread (SwingWorker).
     *
     * @param btConfig the backtest parameters
     * @return the parsed result, or null on failure
     */
    public BacktestResult runBacktest(BacktestConfig btConfig) {
        cancelled = false;

        MetaTraderPlatform platform = config.getPlatform(btConfig.getExpert());
        String terminalPath = config.getTerminalPath(btConfig.getExpert());
        if (!Files.exists(Paths.get(terminalPath))) {
            logMessage("ERROR: MetaTrader terminal not found at: " + terminalPath);
            return null;
        }

        Path mt5Dir = Paths.get(terminalPath).getParent();
        String platformName = platform.getName();
        Mt5LogTailer tailer = null;

        // Pre-flight: check for stale MetaTrader processes from previous runs
        if (!Mt5ProcessGuard.ensureNoStaleProcesses(null, this::logMessage, btConfig.isAutoKillMt5())) {
            logMessage("Backtest aborted: user declined to kill stale " + platformName + " process.");
            return null;
        }

        try {
            // 1. Create output directory
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String dirName = btConfig.toDirectoryName() + "_" + timestamp;
            Path outputDir = config.getReportsDirectory().resolve(dirName);
            Files.createDirectories(outputDir);
            logMessage("Created output directory: " + outputDir);

            // 2. Generate tester.ini
            // IMPORTANT: Report= value is a BASE name. MetaTrader appends .htm automatically.
            // So Report=BacktestReport results in BacktestReport.htm + BacktestReport.png etc.
            String reportName = REPORT_FILENAME;
            Path iniPath = outputDir.resolve("tester.ini");
            iniGenerator.generate(btConfig, iniPath, reportName);
            logMessage("Generated tester.ini (Report=" + reportName + ")");

            // Preserve the exact effective preset with the report artifact. A
            // later gallery replay must not depend on mutable EA defaults or a
            // same-named preset that may have been overwritten meanwhile.
            snapshotExpertParameters(btConfig, outputDir);

            // 2b. Clean up old report files from MetaTrader directory to avoid stale data
            cleanupOldReports(mt5Dir, reportName);

            // 2c. Check for existing MT5 process and ask user before killing
            // MT5 in portable mode only supports ONE instance per directory.
            // If one is already running, the new launch delegates to the existing instance
            // and the launcher exits immediately — breaking our waitFor() logic.
            if (!checkAndKillExistingMt5(mt5Dir, btConfig.isAutoKillMt5(), platform)) {
                logMessage("Backtest aborted: User chose not to terminate existing MetaTrader instance.");
                return null;
            }

            // 3. Copy tester.ini to MetaTrader directory to avoid path-with-spaces issues.
            // Java's ProcessBuilder quotes arguments containing spaces, producing:
            //   "/config:D:\path with spaces\tester.ini"
            // But MetaTrader expects: /config:"D:\path\tester.ini" (quotes around path only).
            // Copying to the MT dir (which typically has no spaces) avoids this entirely.
            Path mt5TesterIni = mt5Dir.resolve("tester_backtest.ini");
            Files.copy(iniPath, mt5TesterIni, StandardCopyOption.REPLACE_EXISTING);
            logMessage("Copied tester.ini to " + platformName + " directory: " + mt5TesterIni);

            // MT4 looks for relative config paths inside the /config directory by default.
            // Copying it to the config/ folder guarantees MT4 will find it.
            Path mt4TesterIni = mt5Dir.resolve("config").resolve("tester_backtest.ini");
            try {
                Files.createDirectories(mt4TesterIni.getParent());
                Files.copy(iniPath, mt4TesterIni, StandardCopyOption.REPLACE_EXISTING);
                logMessage("Copied tester.ini to " + platformName + " config directory: " + mt4TesterIni);
            } catch (IOException e) {
                log.warn("Failed to copy tester.ini to config/ directory", e);
            }

            // 4. Build process arguments
            java.util.List<String> mt5Args = new java.util.ArrayList<>();
            if (platform == MetaTraderPlatform.MT5) {
                String skipUpdateToken = config.get("mt5.skip.update.token", "").trim();
                if (skipUpdateToken.matches("(?i)[0-9a-f]{32}")) {
                    mt5Args.add("/skipupdate:" + skipUpdateToken);
                    logMessage("Using configured MT5 skip-update token to avoid a blocking LiveUpdate retry.");
                } else if (!skipUpdateToken.isEmpty()) {
                    logMessage("WARNING: Ignoring invalid mt5.skip.update.token (expected 32 hexadecimal characters).");
                }
            }
            if (config.isPortableMode()) {
                mt5Args.add("/portable");
            }
            if (platform == MetaTraderPlatform.MT4) {
                mt5Args.add("config\\tester_backtest.ini");
            } else {
                mt5Args.add("/config:tester_backtest.ini");
            }

            logMessage("Starting " + platformName + ": " + terminalPath + " " + String.join(" ", mt5Args));

            // 4. Start process on Desktop 2 and log tailer
            tailer = new Mt5LogTailer(mt5Dir, platform, this::logMessage);
            tailer.start();

            currentProcess = VirtualDesktopHelper.startTerminal(terminalPath, mt5Args, mt5Dir, btConfig.isUseVirtualDesktop());
            if (currentProcess != null) {
                Mt5ProcessGuard.registerProcess(currentProcess);
            } else {
                logMessage("ERROR: Failed to start " + platformName + " process.");
                return null;
            }

            // 5. Asynchronous stream consumer (prevents 64KB buffer deadlock)
            Thread outputConsumer = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(currentProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logMessage("[" + platformName + "] " + line);
                    }
                } catch (IOException e) {
                    if (!cancelled) {
                        log.error("Error reading MT5 output", e);
                    }
                }
            }, "MT5-Output-Consumer");
            outputConsumer.setDaemon(true);
            outputConsumer.start();

            // 6. Wait for process to finish
            if (btConfig.isShutdownTerminal()) {
                logMessage("Waiting for " + platformName + " backtest to complete and close...");
                long startTime = System.currentTimeMillis();
                boolean finished = false;
                long timeoutMs = config.getBacktestTimeoutMinutes() * 60 * 1000L;
                while (System.currentTimeMillis() - startTime < timeoutMs && !cancelled) {
                    finished = currentProcess.waitFor(1, TimeUnit.SECONDS);
                    if (finished) {
                        break;
                    }
                    if (tailer.hasCriticalFailure()) {
                        logMessage("Critical MetaTrader startup/initialization failure detected in logs. Forcibly terminating terminal process...");
                        currentProcess.destroyForcibly();
                        break;
                    }
                }

                if (cancelled) {
                    logMessage("Backtest was cancelled.");
                    return null;
                }

                if (!finished) {
                    if (!tailer.hasCriticalFailure()) {
                        logMessage("WARNING: Backtest timed out after " + config.getBacktestTimeoutMinutes() + " minutes (freeze protection), terminating...");
                        currentProcess.destroyForcibly();
                    }
                    // Salvage: report may already exist even after timeout/critical kill
                    logMessage("Attempting to salvage report after incomplete " + platformName + " run...");
                } else {
                    int exitCode = currentProcess.exitValue();
                    long elapsedMs = System.currentTimeMillis() - startTime;
                    logMessage(platformName + " terminated with exit code: " + exitCode + " (after " + (elapsedMs / 1000) + "s)");

                    // If MT5 exited suspiciously fast (< 10 seconds), it may have delegated
                    // to an already-running instance. Wait for the actual MT5 process and report.
                    if (elapsedMs < 10_000) {
                        logMessage("WARNING: " + platformName + " exited very quickly (" + (elapsedMs / 1000) + "s) - possible delegation to existing instance.");
                        logMessage("Waiting for actual " + platformName + " process and report file...");
                        boolean reportAppeared = waitForReportFile(mt5Dir, reportName, config.getBacktestTimeoutMinutes() * 60, platform, tailer);
                        if (!reportAppeared && !cancelled) {
                            logMessage("ERROR: " + platformName + " process delegation detected but no report was produced.");
                            logMessage("TIP: Make sure no other " + platformName + " instance is running before starting a backtest.");
                        }
                    }
                }
            } else {
                logMessage("Visual mode: " + platformName + " will remain open. Waiting for report file...");
                boolean reportAppeared = waitForReportFile(mt5Dir, reportName, config.getBacktestTimeoutMinutes() * 60, platform, tailer);
                if (!reportAppeared && !cancelled) {
                    logMessage("ERROR: No report was produced within " + config.getBacktestTimeoutMinutes() + " minutes.");
                }
                if (cancelled) {
                    logMessage("Backtest was cancelled.");
                    return null;
                }
            }

            // 7. Search for the report file
            // MetaTrader creates Report.htm, Report.png, Report-hst.png, Report-mfemae.png, Report-holding.png
            Path reportInOutput = outputDir.resolve("report.htm");
            boolean reportFound = findAndCopyReport(mt5Dir, reportName, reportInOutput);

            BacktestResult result = new BacktestResult();
            result.setSymbol(btConfig.getSymbol());
            result.setPeriod(btConfig.getPeriod());
            result.setExpert(btConfig.getExpert());
            result.setOutputDirectory(outputDir.toString());
            if (btConfig.getModel() >= 0 && btConfig.getModel() < BacktestConfig.MODEL_NAMES.length) {
                result.setTickModel(BacktestConfig.MODEL_NAMES[btConfig.getModel()]);
            }

            if (reportFound) {
                result = reportParser.parse(reportInOutput);
                result.setSymbol(btConfig.getSymbol());
                result.setPeriod(btConfig.getPeriod());
                result.setExpert(btConfig.getExpert());
                result.setOutputDirectory(outputDir.toString());
                if (btConfig.getModel() >= 0 && btConfig.getModel() < BacktestConfig.MODEL_NAMES.length) {
                    result.setTickModel(BacktestConfig.MODEL_NAMES[btConfig.getModel()]);
                }
                result.setSuccess(true);
                logMessage("Backtest completed successfully!");
                logMessage("Results: Profit=" + result.getTotalProfit() +
                          ", Trades=" + result.getTotalTrades() +
                          ", Drawdown=" + result.getMaxDrawdown() + "%");
            } else {
                logMessage("WARNING: Report file not found in " + platformName + " directory.");
                logMessage("Searched in: " + mt5Dir);
                logMessage("Looked for: " + reportName + " and variants (.htm, .html)");
                logMessage("The backtest may have failed, or the EA produced no trades.");
                result.setSuccess(false);
                result.setMessage("Report file not found - check " + platformName + " logs");
            }

            // ALWAYS write summary (so the results table shows something)
            writeSummary(outputDir, result, btConfig);
            try {
                BacktestStatisticsArtifact.write(outputDir, result, btConfig);
                logMessage("Archived complete backtest statistics: "
                        + BacktestStatisticsArtifact.FILE_NAME);
            } catch (IOException ex) {
                log.error("Failed to write structured backtest statistics", ex);
            }
            
            if (result.isSuccess()) {
                try {
                    com.google.gson.JsonObject metrics =
                            BacktestStatisticsArtifact.create(result, btConfig);
                    com.backtester.database.DatabaseManager.getInstance().saveRun(
                        "BACKTEST", 
                        result.getExpert(), 
                        System.currentTimeMillis(), 
                        metrics.toString(), 
                        reportInOutput.toAbsolutePath().toString()
                    );
                } catch (Exception e) {
                    log.error("Failed to save to DB", e);
                }
            }
            
            return result;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logMessage("Backtest interrupted");
            return null;
        } catch (Exception e) {
            logMessage("ERROR: " + e.getMessage());
            log.error("Backtest execution failed", e);
            return null;
        } finally {
            if (cancelled || Thread.currentThread().isInterrupted()) {
                if (currentProcess != null && currentProcess.isAlive()) {
                    currentProcess.destroyForcibly();
                }
            }
            if (currentProcess != null) {
                Mt5ProcessGuard.unregisterProcess(currentProcess);
            }
            if (tailer != null) {
                tailer.stop();
            }
        }
    }

    private boolean findAndCopyReport(Path mt5Dir, String reportBaseName, Path destination) {
        // MetaTrader creates the report as <baseName>.htm with associated image files
        String[] possibleNames = {
            reportBaseName + ".htm",        // BacktestReport.htm (main)
            reportBaseName + ".html",       // BacktestReport.html
            reportBaseName + ".xml",        // BacktestReport.xml (legacy)
            reportBaseName + ".xml.htm",    // BacktestReport.xml.htm (old bug compat)
        };

        // Possible directories where MT5/MT4 might place the report
        Path[] searchDirs = {
            mt5Dir,                                    // MT root
            mt5Dir.resolve("Reports"),                 // Reports subdirectory
            mt5Dir.resolve("Tester"),                  // Tester subdirectory
            mt5Dir.resolve("tester"),                  // MT4 tester subdirectory
            mt5Dir.resolve("MQL5").resolve("Reports"), // MQL5/Reports
        };

        for (Path dir : searchDirs) {
            if (!Files.exists(dir)) continue;
            for (String name : possibleNames) {
                Path candidate = dir.resolve(name);
                if (Files.exists(candidate)) {
                    try {
                        logMessage("Found report: " + candidate);
                        Files.copy(candidate, destination, StandardCopyOption.REPLACE_EXISTING);
                        logMessage("Report copied to: " + destination);

                        // Copy all associated files (images: .png, -hst.png, -mfemae.png, -holding.png)
                        copyAssociatedFiles(candidate, destination.getParent());

                        return true;
                    } catch (IOException e) {
                        log.error("Failed to copy report", e);
                    }
                }
            }
        }

        // Fallback: Search recursively for any file matching the report name
        logMessage("Searching recursively in MetaTrader directory for report...");
        try (Stream<Path> walker = Files.walk(mt5Dir, 3)) {
            Path found = walker
                .filter(p -> {
                    String fname = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    return fname.startsWith(reportBaseName.toLowerCase(java.util.Locale.ROOT)) &&
                           (fname.endsWith(".htm") || fname.endsWith(".html") || fname.endsWith(".xml"));
                })
                .findFirst()
                .orElse(null);

            if (found != null) {
                logMessage("Found report via recursive search: " + found);
                Files.copy(found, destination, StandardCopyOption.REPLACE_EXISTING);
                copyAssociatedFiles(found, destination.getParent());
                return true;
            }
        } catch (IOException e) {
            log.error("Error searching for report", e);
        }

        return false;
    }

    /**
     * Copies all associated files (images, etc.) that belong to a report.
     * MT5 generates files like: Report.png, Report-hst.png, Report-mfemae.png, Report-holding.png
     * All share the same base prefix as the report file.
     */
    private void copyAssociatedFiles(Path reportFile, Path destDir) {
        Path dir = reportFile.getParent();
        // Get the base name for matching (e.g. "BacktestReport" from "BacktestReport.htm")
        String reportFileName = reportFile.getFileName().toString();
        // Strip extension to get the root base name
        String baseName = reportFileName;
        // Remove all extensions (e.g. "BacktestReport.xml.htm" -> "BacktestReport")
        int firstDot = baseName.indexOf('.');
        if (firstDot > 0) {
            baseName = baseName.substring(0, firstDot);
        }
        
        final String matchPrefix = baseName;
        logMessage("Searching for associated files with prefix: " + matchPrefix);
        
        try (Stream<Path> siblingFiles = Files.list(dir)) {
            siblingFiles.filter(p -> {
                String fName = p.getFileName().toString();
                // Match files starting with the base name that are NOT the report itself
                return fName.startsWith(matchPrefix) && !fName.equals(reportFileName)
                        && !Files.isDirectory(p);
            }).forEach(p -> {
                try {
                    Path associatedDest = destDir.resolve(p.getFileName().toString());
                    Files.copy(p, associatedDest, StandardCopyOption.REPLACE_EXISTING);
                    logMessage("Copied associated file: " + p.getFileName());
                } catch (IOException ex) {
                    log.warn("Failed to copy associated file: " + p, ex);
                }
            });
        } catch (IOException ex) {
            log.warn("Could not list sibling files in " + dir, ex);
        }
    }

    /**
     * Removes old report files from the MetaTrader directory before a new test.
     * This prevents stale files (especially images from previous runs) from contaminating new results.
     * Also cleans up legacy BacktestReport.xml.* files from the old naming bug.
     * Searches in root dir and tester/ subdirectory (MT4 stores reports there).
     */
    private void cleanupOldReports(Path mt5Dir, String reportBaseName) {
        cleanupDirReports(mt5Dir, reportBaseName);
        // MT4 stores reports in the tester/ subdirectory
        cleanupDirReports(mt5Dir.resolve("tester"), reportBaseName);
        cleanupDirReports(mt5Dir.resolve("Tester"), reportBaseName);
    }

    private void cleanupDirReports(Path dir, String reportBaseName) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> {
                String name = p.getFileName().toString();
                // Match both new format (BacktestReport.*) and old format (BacktestReport.xml.*)
                return (name.startsWith(reportBaseName + ".") || name.startsWith(reportBaseName + ".xml"))
                        && !Files.isDirectory(p);
            }).forEach(p -> {
                try {
                    Files.delete(p);
                    logMessage("Cleaned up old report file: " + p.getFileName());
                } catch (IOException e) {
                    log.warn("Could not delete old report file: " + p, e);
                }
            });
        } catch (IOException e) {
            log.warn("Could not clean up old reports in " + dir, e);
        }
    }

    /**
     * Checks if an MT5 terminal instance is already running from the same directory.
     * If one is found, shows a confirmation dialog to the user before terminating it.
     * This is critical because MT5 in portable mode only supports one instance per directory.
     *
     * @param mt5Dir the MT5 installation directory
     * @return true if no MT5 was running or user confirmed termination; false if user declined
     */
    private boolean checkAndKillExistingMt5(Path mt5Dir, boolean autoKill, MetaTraderPlatform platform) {
        try {
            String platformName = platform.getName();
            logMessage("Checking for existing " + platformName + " processes...");

            java.util.List<Long> existingPids = Mt5ProcessGuard.findTerminalPidsForInstall(mt5Dir);
            if (existingPids.isEmpty()) {
                logMessage("No existing " + platformName + " process found.");
                return true;
            }
            String output = existingPids.stream()
                    .map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(System.lineSeparator()));

            // Terminal is running — ask the user for confirmation (or auto-kill)
            logMessage("Found running " + platformName + " process(es): PID " + output.replace("\n", ", "));

            AtomicBoolean userConfirmed = new AtomicBoolean(false);
            if (autoKill) {
                userConfirmed.set(true);
            } else {
                boolean isCli = "true".equals(System.getProperty("backtester.cli")) || java.awt.GraphicsEnvironment.isHeadless();
                if (isCli) {
                    logMessage("CLI Mode: Existing " + platformName + " process found, but auto_kill_mt5 is false. Proceeding without terminating to allow delegation.");
                    return true;
                }
                try {
                    if (SwingUtilities.isEventDispatchThread()) {
                        int choice = JOptionPane.showConfirmDialog(
                            null,
                            platformName + " is already running (PID: " + output.replace("\n", ", ").replace("\r", "") + ").\n\n" +
                            platformName + " supports only one instance per directory in portable mode.\n" +
                            "The existing instance must be closed before starting a backtest.\n\n" +
                            "Terminate the running " + platformName + " instance?",
                            platformName + " Already Running",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE
                        );
                        userConfirmed.set(choice == JOptionPane.YES_OPTION);
                    } else {
                        SwingUtilities.invokeAndWait(() -> {
                            int choice = JOptionPane.showConfirmDialog(
                                null,
                                platformName + " is already running (PID: " + output.replace("\n", ", ").replace("\r", "") + ").\n\n" +
                                platformName + " supports only one instance per directory in portable mode.\n" +
                                "The existing instance must be closed before starting a backtest.\n\n" +
                                "Terminate the running " + platformName + " instance?",
                                platformName + " Already Running",
                                JOptionPane.YES_NO_OPTION,
                                JOptionPane.WARNING_MESSAGE
                            );
                            userConfirmed.set(choice == JOptionPane.YES_OPTION);
                        });
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Interrupted while waiting for MetaTrader cleanup confirmation", e);
                    return false;
                } catch (Exception e) {
                    log.error("Error showing confirmation dialog", e);
                    return false;
                }
            }

            if (!userConfirmed.get()) {
                logMessage("User chose not to terminate " + platformName + ". Backtest will not start.");
                return false;
            }

            // User confirmed — kill the process
            logMessage("Terminating existing " + platformName + " processes...");
            int killed = Mt5ProcessGuard.killAllTerminalsForInstall(mt5Dir, this::logMessage);
            if (!Mt5ProcessGuard.findTerminalPidsForInstall(mt5Dir).isEmpty()) {
                logMessage(platformName + " cleanup failed: one or more processes are still alive.");
                return false;
            }

            logMessage(platformName + " cleanup complete (" + killed + " process(es) stopped).");
            return true;
        } catch (Exception e) {
            logMessage("MetaTrader cleanup note: " + e.getMessage());
            return true; // Proceed with backtest on error
        }
    }

    /**
     * Waits for a report file to appear in the MetaTrader directory.
     * Used as a fallback when MetaTrader delegates to an existing instance.
     * Also waits for the MetaTrader process to finish.
     *
     * @param mt5Dir         the MT5 installation directory
     * @param reportBaseName the report base name (e.g. "BacktestReport")
     * @param timeoutSeconds maximum wait time in seconds
     * @return true if the report file appeared
     */
    private boolean waitForReportFile(Path mt5Dir, String reportBaseName, int timeoutSeconds, MetaTraderPlatform platform, Mt5LogTailer tailer) {
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        String[] extensions = { ".htm", ".html" };
        Path[] searchDirs = {
            mt5Dir,
            mt5Dir.resolve("Reports"),
            mt5Dir.resolve("Tester"),
            mt5Dir.resolve(platform.getPresetsFolderName()),
            mt5Dir.resolve(platform.getMqlFolderName()).resolve("Reports"),
        };

        String platformName = platform.getName();

        while (System.currentTimeMillis() < deadline && !cancelled) {
            // Check if tailer detected a critical load/init failure
            if (tailer != null && tailer.hasCriticalFailure()) {
                logMessage("Critical MetaTrader startup/initialization failure detected. Stopping wait.");
                return false;
            }

            // Check if report file appeared
            for (Path dir : searchDirs) {
                if (!Files.exists(dir)) continue;
                for (String ext : extensions) {
                    if (Files.exists(dir.resolve(reportBaseName + ext))) {
                        logMessage("Report file detected: " + dir.resolve(reportBaseName + ext));
                        return true;
                    }
                }
            }

            // Check if MetaTrader is still running from this directory
            try {
                if (Mt5ProcessGuard.findTerminalPidsForInstall(mt5Dir).isEmpty()) {
                    logMessage(platformName + " process has exited. Checking for report one last time...");
                    // One final check
                    for (Path dir : searchDirs) {
                        if (!Files.exists(dir)) continue;
                        for (String ext : extensions) {
                            if (Files.exists(dir.resolve(reportBaseName + ext))) {
                                return true;
                            }
                        }
                    }
                    return false;
                }
            } catch (Exception e) {
                log.warn("Error checking " + platformName + " process status", e);
            }

            try {
                Thread.sleep(5000); // Poll every 5 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * Cancel the currently running backtest.
     */
    public void cancel() {
        cancelled = true;
        if (currentProcess != null && currentProcess.isAlive()) {
            currentProcess.destroyForcibly();
            logMessage("Backtest process terminated.");
        }
    }

    /**
     * Write a summary file that contains both result data and configuration info.
     * This is ALWAYS written, even if the report wasn't found.
     */
    private void writeSummary(Path outputDir, BacktestResult result, BacktestConfig btConfig) {
        Path summaryFile = outputDir.resolve("summary.txt");
        try (Writer writer = Files.newBufferedWriter(summaryFile)) {
            writer.write("=== MetaTrader Backtest Summary ===\n");
            writer.write("Expert: " + result.getExpert() + "\n");
            writer.write("Symbol: " + result.getSymbol() + "\n");
            writer.write("Period: " + result.getPeriod() + "\n");
            writer.write("From: " + btConfig.getFromDate() + "\n");
            writer.write("To: " + btConfig.getToDate() + "\n");
            writer.write("Model: " + btConfig.getModelName() + "\n");
            writer.write("Deposit: " + btConfig.getDeposit() + " " + btConfig.getCurrency() + "\n");
            writer.write("Leverage: " + btConfig.getLeverage() + "\n");
            writer.write("ExpertParameters: " + (btConfig.getExpertParameters() != null && !btConfig.getExpertParameters().isEmpty() ? btConfig.getExpertParameters() : "none (compiled defaults)") + "\n");
            writer.write("Config: " + result.getConfigInfo() + "\n");
            writer.write("Status: " + (result.isSuccess() ? "SUCCESS" : "FAILED - " + result.getMessage()) + "\n");
            writer.write("\n--- Results ---\n");
            writer.write("Total Profit: " + result.getTotalProfit() + "\n");
            writer.write("Gross Profit: " + result.getGrossProfit() + "\n");
            writer.write("Gross Loss: " + result.getGrossLoss() + "\n");
            writer.write("Total Trades: " + result.getTotalTrades() + "\n");
            writer.write("Profit Trades: " + result.getProfitTrades() + "\n");
            writer.write("Loss Trades: " + result.getLossTrades() + "\n");
            writer.write("Short Positions: " + result.getShortPositions() + "\n");
            writer.write("Long Positions: " + result.getLongPositions() + "\n");
            writer.write("Win Rate: " + result.getWinRate() + "%\n");
            writer.write("Max Drawdown: " + result.getMaxDrawdownPercent() + "%\n");
            writer.write("Maximal Equity Drawdown: " + result.getMaxDrawdown() + "%\n");
            writer.write("Maximal Equity Drawdown Absolute: " + result.getMaxDrawdownAbsolute() + "\n");
            writer.write("Maximal Balance Drawdown: " + result.getBalanceDrawdown() + "%\n");
            writer.write("Maximal Balance Drawdown Absolute: " + result.getBalanceDrawdownAbsolute() + "\n");
            writer.write("Profit Factor: " + result.getProfitFactor() + "\n");
            writer.write("Sharpe Ratio: " + result.getSharpeRatio() + "\n");
            writer.write("Recovery Factor: " + result.getRecoveryFactor() + "\n");
            writer.write("Expected Payoff: " + result.getExpectedPayoff() + "\n");
            writer.write("Initial Deposit: " + result.getInitialDeposit() + "\n");
            writer.write("Final Balance: " + result.getFinalBalance() + "\n");
            writer.write("Largest Win: " + result.getLargestWin() + "\n");
            writer.write("Largest Loss: " + result.getLargestLoss() + "\n");
            writer.write("Average Win: " + result.getAverageWin() + "\n");
            writer.write("Average Loss: " + result.getAverageLoss() + "\n");
            writer.write("Equity History Points: "
                    + (result.getEquityHistory() != null ? result.getEquityHistory().size() : 0) + "\n");
            writer.write("Output: " + outputDir + "\n");
            log.info("Summary written to {}", summaryFile);
        } catch (IOException e) {
            log.error("Failed to write summary", e);
        }
    }

    private void snapshotExpertParameters(BacktestConfig btConfig, Path outputDir) throws IOException {
        String presetName = btConfig.getExpertParameters();
        if (presetName == null || presetName.isBlank()) return;
        Path leaf = Path.of(presetName.trim());
        if (leaf.getNameCount() != 1) {
            throw new IOException("Invalid ExpertParameters filename: " + presetName);
        }
        Path profilesDir = config.getTesterProfilesDir(btConfig.getExpert()).toAbsolutePath().normalize();
        Path source = profilesDir.resolve(leaf).normalize();
        if (!source.getParent().equals(profilesDir) || !Files.isRegularFile(source)) {
            throw new IOException("ExpertParameters preset not found: " + source);
        }
        Path snapshot = outputDir.resolve(BacktestArtifactReplayResolver.PRESET_SNAPSHOT_FILE);
        Files.copy(source, snapshot, StandardCopyOption.REPLACE_EXISTING);
        logMessage("Archived exact ExpertParameters preset: " + snapshot.getFileName());
    }

    private void logMessage(String message) {
        String timestamped = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("HH:mm:ss")) + " " + message;

        boolean isTerminalLog = message.startsWith("[Terminal]") || message.startsWith("[Tester]") || 
                               message.contains("[MT4]") || message.contains("[MT5]") ||
                               message.startsWith("[cleanup]");

        if (isTerminalLog) {
            String lower = message.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("error") || lower.contains("failed") || 
                lower.contains("cannot") || lower.contains("critical") || 
                lower.contains("❌")) {
                log.info(message);
            } else {
                log.debug(message);
            }
        } else {
            log.info(message);
        }

        if (logCallback != null) {
            if (message.startsWith("[MT5] ") || message.startsWith("[MT4] ")) {
                int bracketEnd = message.indexOf("] ");
                String stripped = message.substring(bracketEnd + 2).trim();
                if (!com.backtester.engine.Mt5LogTailer.shouldForwardToUi(stripped)) {
                    return; // skip forwarding to UI
                }
            }

            if (isTerminalLog) {
                String lower = message.toLowerCase(java.util.Locale.ROOT);
                if (lower.contains("error") || lower.contains("failed") || 
                    lower.contains("cannot") || lower.contains("critical") || 
                    lower.contains("❌")) {
                    logCallback.accept(timestamped);
                }
            } else {
                logCallback.accept(timestamped);
            }
        }
    }
}
