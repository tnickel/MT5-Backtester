package com.backtester.engine;

import com.backtester.config.AppConfig;
import com.backtester.report.BacktestResult;
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
 * Orchestrates the execution of a single MT5 backtest:
 * 1. Creates the output subdirectory
 * 2. Generates the tester.ini
 * 3. Launches terminal64.exe via ProcessBuilder
 * 4. Consumes stdout/stderr to prevent 64KB deadlock
 * 5. Waits for process completion
 * 6. Searches for and copies the report from MT5 directory
 * 7. Parses the resulting XML report
 */
public class BacktestRunner {

    private static final Logger log = LoggerFactory.getLogger(BacktestRunner.class);
    private final AppConfig config;
    private final IniGenerator iniGenerator;
    private final ReportParser reportParser;
    private Consumer<String> logCallback;
    private volatile boolean cancelled = false;
    private Process currentProcess;

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

        String terminalPath = config.getMt5TerminalPath();
        if (!Files.exists(Paths.get(terminalPath))) {
            logMessage("ERROR: MT5 terminal not found at: " + terminalPath);
            return null;
        }

        Path mt5Dir = Paths.get(terminalPath).getParent();
        Mt5LogTailer tailer = null;

        // Pre-flight: check for stale MT5 processes from previous runs
        if (!Mt5ProcessGuard.ensureNoStaleProcesses(null, this::logMessage)) {
            logMessage("Backtest aborted: user declined to kill stale MT5 process.");
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
            // IMPORTANT: MT5's Report= value is a BASE name. MT5 appends .htm automatically.
            // So Report=BacktestReport results in BacktestReport.htm + BacktestReport.png etc.
            String reportName = REPORT_FILENAME;
            Path iniPath = outputDir.resolve("tester.ini");
            iniGenerator.generate(btConfig, iniPath, reportName);
            logMessage("Generated tester.ini (Report=" + reportName + ")");

            // 2b. Clean up old report files from MT5 directory to avoid stale data
            cleanupOldReports(mt5Dir, reportName);

            // 2c. Check for existing MT5 process and ask user before killing
            // MT5 in portable mode only supports ONE instance per directory.
            // If one is already running, the new launch delegates to the existing instance
            // and the launcher exits immediately — breaking our waitFor() logic.
            if (!checkAndKillExistingMt5(mt5Dir)) {
                logMessage("Backtest aborted: User chose not to terminate existing MT5 instance.");
                return null;
            }

            // 3. Copy tester.ini to MT5 directory to avoid path-with-spaces issues.
            // Java's ProcessBuilder quotes arguments containing spaces, producing:
            //   "/config:D:\path with spaces\tester.ini"
            // But MT5 expects: /config:"D:\path\tester.ini" (quotes around path only).
            // Copying to the MT5 dir (which typically has no spaces) avoids this entirely.
            Path mt5TesterIni = mt5Dir.resolve("tester_backtest.ini");
            Files.copy(iniPath, mt5TesterIni, StandardCopyOption.REPLACE_EXISTING);
            logMessage("Copied tester.ini to MT5 directory: " + mt5TesterIni);

            // 4. Build process command
            ProcessBuilder pb;
            if (config.isPortableMode()) {
                pb = new ProcessBuilder(
                    terminalPath,
                    "/portable",
                    "/config:tester_backtest.ini"
                );
            } else {
                pb = new ProcessBuilder(
                    terminalPath,
                    "/config:tester_backtest.ini"
                );
            }

            // CRITICAL: Merge stderr into stdout to prevent 64KB deadlock
            pb.redirectErrorStream(true);
            pb.directory(mt5Dir.toFile());

            logMessage("Starting MT5: " + String.join(" ", pb.command()));

            // 4. Start process and log tailer
            tailer = new Mt5LogTailer(mt5Dir, this::logMessage);
            tailer.start();

            currentProcess = pb.start();
            Mt5ProcessGuard.registerProcess(currentProcess);

            // 5. Asynchronous stream consumer (prevents 64KB buffer deadlock)
            Thread outputConsumer = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(currentProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logMessage("[MT5] " + line);
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
                logMessage("Waiting for MT5 backtest to complete and close...");
                long startTime = System.currentTimeMillis();
                boolean finished = currentProcess.waitFor(4, TimeUnit.HOURS);

                if (!finished) {
                    logMessage("WARNING: Backtest timed out after 4 hours, terminating...");
                    currentProcess.destroyForcibly();
                    return null;
                }

                if (cancelled) {
                    logMessage("Backtest was cancelled.");
                    return null;
                }

                int exitCode = currentProcess.exitValue();
                long elapsedMs = System.currentTimeMillis() - startTime;
                logMessage("MT5 terminated with exit code: " + exitCode + " (after " + (elapsedMs / 1000) + "s)");

                // If MT5 exited suspiciously fast (< 10 seconds), it may have delegated
                // to an already-running instance. Wait for the actual MT5 process and report.
                if (elapsedMs < 10_000) {
                    logMessage("WARNING: MT5 exited very quickly (" + (elapsedMs / 1000) + "s) - possible delegation to existing instance.");
                    logMessage("Waiting for actual MT5 process and report file...");
                    boolean reportAppeared = waitForReportFile(mt5Dir, reportName, 4 * 60 * 60);
                    if (!reportAppeared && !cancelled) {
                        logMessage("ERROR: MT5 process delegation detected but no report was produced.");
                        logMessage("TIP: Make sure no other MT5 instance is running before starting a backtest.");
                    }
                }
            } else {
                logMessage("Visual mode: MT5 will remain open. Waiting for report file...");
                boolean reportAppeared = waitForReportFile(mt5Dir, reportName, 4 * 60 * 60);
                if (!reportAppeared && !cancelled) {
                    logMessage("ERROR: No report was produced within 4 hours.");
                }
                if (cancelled) {
                    logMessage("Backtest was cancelled.");
                    return null;
                }
            }

            // 7. Search for the report file
            // MT5 creates Report.htm, Report.png, Report-hst.png, Report-mfemae.png, Report-holding.png
            Path reportInOutput = outputDir.resolve("report.htm");
            boolean reportFound = findAndCopyReport(mt5Dir, reportName, reportInOutput);

            BacktestResult result = new BacktestResult();
            result.setSymbol(btConfig.getSymbol());
            result.setPeriod(btConfig.getPeriod());
            result.setExpert(btConfig.getExpert());
            result.setOutputDirectory(outputDir.toString());

            if (reportFound) {
                result = reportParser.parse(reportInOutput);
                result.setSymbol(btConfig.getSymbol());
                result.setPeriod(btConfig.getPeriod());
                result.setExpert(btConfig.getExpert());
                result.setOutputDirectory(outputDir.toString());
                result.setSuccess(true);
                logMessage("Backtest completed successfully!");
                logMessage("Results: Profit=" + result.getTotalProfit() +
                          ", Trades=" + result.getTotalTrades() +
                          ", Drawdown=" + result.getMaxDrawdown() + "%");
            } else {
                logMessage("WARNING: Report file not found in MT5 directory.");
                logMessage("Searched in: " + mt5Dir);
                logMessage("Looked for: " + reportName + " and variants (.htm, .html)");
                logMessage("The backtest may have failed, or the EA produced no trades.");
                result.setSuccess(false);
                result.setMessage("Report file not found - check MT5 logs");
            }

            // ALWAYS write summary (so the results table shows something)
            writeSummary(outputDir, result, btConfig);
            
            if (result.isSuccess()) {
                try {
                    com.google.gson.JsonObject metrics = new com.google.gson.JsonObject();
                    metrics.addProperty("profit", result.getTotalProfit());
                    metrics.addProperty("drawdown", result.getMaxDrawdown());
                    metrics.addProperty("trades", result.getTotalTrades());
                    metrics.addProperty("winRate", result.getWinRate());
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
            if (currentProcess != null) {
                Mt5ProcessGuard.unregisterProcess(currentProcess);
            }
            if (tailer != null) {
                tailer.stop();
            }
        }
    }

    private boolean findAndCopyReport(Path mt5Dir, String reportBaseName, Path destination) {
        // MT5 creates the report as <baseName>.htm with associated image files
        String[] possibleNames = {
            reportBaseName + ".htm",        // BacktestReport.htm (main)
            reportBaseName + ".html",       // BacktestReport.html
            reportBaseName + ".xml",        // BacktestReport.xml (legacy)
            reportBaseName + ".xml.htm",    // BacktestReport.xml.htm (old bug compat)
        };

        // Possible directories where MT5 might place the report
        Path[] searchDirs = {
            mt5Dir,                                    // MT5 root
            mt5Dir.resolve("Reports"),                 // Reports subdirectory
            mt5Dir.resolve("Tester"),                  // Tester subdirectory
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
        logMessage("Searching recursively in MT5 directory for report...");
        try (Stream<Path> walker = Files.walk(mt5Dir, 3)) {
            Path found = walker
                .filter(p -> {
                    String fname = p.getFileName().toString().toLowerCase();
                    return fname.startsWith(reportBaseName.toLowerCase()) &&
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
     * Removes old report files from the MT5 directory before a new test.
     * This prevents stale files (especially images from previous runs) from contaminating new results.
     * Also cleans up legacy BacktestReport.xml.* files from the old naming bug.
     */
    private void cleanupOldReports(Path mt5Dir, String reportBaseName) {
        try (Stream<Path> files = Files.list(mt5Dir)) {
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
            log.warn("Could not clean up old reports in " + mt5Dir, e);
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
    private boolean checkAndKillExistingMt5(Path mt5Dir) {
        try {
            logMessage("Checking for existing MT5 processes...");

            String searchPath = mt5Dir.toAbsolutePath().toString().replace("'", "''");

            // First: Check if any MT5 process is running from this directory
            ProcessBuilder checkPb = new ProcessBuilder(
                "powershell", "-NoProfile", "-Command",
                "$procs = Get-Process terminal64 -ErrorAction SilentlyContinue | " +
                "Where-Object { $_.Path -and $_.Path.StartsWith('" + searchPath + "') }; " +
                "if ($procs) { $procs | ForEach-Object { Write-Output $_.Id } } else { Write-Output 'NONE' }"
            );
            checkPb.redirectErrorStream(true);
            Process checkProc = checkPb.start();
            String output = new String(checkProc.getInputStream().readAllBytes()).trim();
            checkProc.waitFor(10, TimeUnit.SECONDS);

            if ("NONE".equals(output) || output.isEmpty()) {
                logMessage("No existing MT5 process found.");
                return true;
            }

            // MT5 is running — ask the user for confirmation
            logMessage("Found running MT5 process(es): PID " + output.replace("\n", ", "));

            AtomicBoolean userConfirmed = new AtomicBoolean(false);
            try {
                SwingUtilities.invokeAndWait(() -> {
                    int choice = JOptionPane.showConfirmDialog(
                        null,
                        "MetaTrader 5 is already running (PID: " + output.replace("\n", ", ").replace("\r", "") + ").\n\n" +
                        "MT5 supports only one instance per directory in portable mode.\n" +
                        "The existing instance must be closed before starting a backtest.\n\n" +
                        "Terminate the running MT5 instance?",
                        "MT5 Already Running",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                    );
                    userConfirmed.set(choice == JOptionPane.YES_OPTION);
                });
            } catch (Exception e) {
                log.error("Error showing confirmation dialog", e);
                return false;
            }

            if (!userConfirmed.get()) {
                logMessage("User chose not to terminate MT5. Backtest will not start.");
                return false;
            }

            // User confirmed — kill the process
            logMessage("Terminating existing MT5 processes...");
            ProcessBuilder killPb = new ProcessBuilder(
                "powershell", "-NoProfile", "-Command",
                "$procs = Get-Process terminal64 -ErrorAction SilentlyContinue | " +
                "Where-Object { $_.Path -and $_.Path.StartsWith('" + searchPath + "') }; " +
                "$procs | ForEach-Object { Write-Output \"Terminating MT5 (PID $($_.Id))\"; " +
                "Stop-Process -Id $_.Id -Force }; Start-Sleep -Seconds 3; Write-Output 'MT5 terminated successfully'"
            );
            killPb.redirectErrorStream(true);
            Process killProc = killPb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(killProc.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logMessage("[cleanup] " + line);
                }
            }

            boolean done = killProc.waitFor(15, TimeUnit.SECONDS);
            if (!done) {
                killProc.destroyForcibly();
            }

            logMessage("MT5 cleanup complete.");
            return true;
        } catch (Exception e) {
            logMessage("MT5 cleanup note: " + e.getMessage());
            return true; // Proceed with backtest on error
        }
    }

    /**
     * Waits for a report file to appear in the MT5 directory.
     * Used as a fallback when MT5 delegates to an existing instance.
     * Also waits for the terminal64.exe process to finish.
     *
     * @param mt5Dir         the MT5 installation directory
     * @param reportBaseName the report base name (e.g. "BacktestReport")
     * @param timeoutSeconds maximum wait time in seconds
     * @return true if the report file appeared
     */
    private boolean waitForReportFile(Path mt5Dir, String reportBaseName, int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        String[] extensions = { ".htm", ".html" };
        Path[] searchDirs = {
            mt5Dir,
            mt5Dir.resolve("Reports"),
            mt5Dir.resolve("Tester"),
            mt5Dir.resolve("MQL5").resolve("Reports"),
        };

        while (System.currentTimeMillis() < deadline && !cancelled) {
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

            // Check if MT5 is still running
            try {
                ProcessBuilder pb = new ProcessBuilder(
                    "powershell", "-NoProfile", "-Command",
                    "(Get-Process terminal64 -ErrorAction SilentlyContinue | Measure-Object).Count"
                );
                pb.redirectErrorStream(true);
                Process check = pb.start();
                String output = new String(check.getInputStream().readAllBytes()).trim();
                check.waitFor(5, TimeUnit.SECONDS);

                if ("0".equals(output)) {
                    logMessage("MT5 process has exited. Checking for report one last time...");
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
                log.warn("Error checking MT5 process status", e);
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
            writer.write("=== MT5 Backtest Summary ===\n");
            writer.write("Expert: " + result.getExpert() + "\n");
            writer.write("Symbol: " + result.getSymbol() + "\n");
            writer.write("Period: " + result.getPeriod() + "\n");
            writer.write("From: " + btConfig.getFromDate() + "\n");
            writer.write("To: " + btConfig.getToDate() + "\n");
            writer.write("Model: " + BacktestConfig.MODEL_NAMES[btConfig.getModel()] + "\n");
            writer.write("Deposit: " + btConfig.getDeposit() + " " + btConfig.getCurrency() + "\n");
            writer.write("Leverage: " + btConfig.getLeverage() + "\n");
            writer.write("ExpertParameters: " + (btConfig.getExpertParameters() != null && !btConfig.getExpertParameters().isEmpty() ? btConfig.getExpertParameters() : "none (compiled defaults)") + "\n");
            writer.write("Config: " + result.getConfigInfo() + "\n");
            writer.write("Status: " + (result.isSuccess() ? "SUCCESS" : "FAILED - " + result.getMessage()) + "\n");
            writer.write("\n--- Results ---\n");
            writer.write("Total Profit: " + result.getTotalProfit() + "\n");
            writer.write("Total Trades: " + result.getTotalTrades() + "\n");
            writer.write("Win Rate: " + result.getWinRate() + "%\n");
            writer.write("Max Drawdown: " + result.getMaxDrawdown() + "%\n");
            writer.write("Profit Factor: " + result.getProfitFactor() + "\n");
            writer.write("Sharpe Ratio: " + result.getSharpeRatio() + "\n");
            writer.write("Output: " + outputDir + "\n");
            log.info("Summary written to {}", summaryFile);
        } catch (IOException e) {
            log.error("Failed to write summary", e);
        }
    }

    private void logMessage(String message) {
        String timestamped = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("HH:mm:ss")) + " " + message;
        log.info(message);
        if (logCallback != null) {
            logCallback.accept(timestamped);
        }
    }
}
