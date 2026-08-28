package com.backtester.engine;

import com.backtester.config.AppConfig;
import com.backtester.config.MetaTraderPlatform;
import com.backtester.report.BacktestArtifactReplayResolver;
import com.backtester.report.OptimizationReportParser;
import com.backtester.report.OptimizationResult;
import com.backtester.report.OptimizationDateRangeResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
 * Executes a single optimization run in MetaTrader (MT4 or MT5).
 */
public class OptimizationRunner {
    private static final Logger log = LoggerFactory.getLogger(OptimizationRunner.class);
    private static final String REPORT_FILENAME = "OptimizationReport";

    private final AppConfig config;
    private final IniGenerator iniGenerator;
    private final OptimizationReportParser parser;
    // volatile: cancel() is called from the UI thread while runOptimization()
    // polls these fields in its worker loop — without volatile the loop may
    // never see the cancellation.
    private volatile Process currentProcess;
    private volatile boolean cancelled = false;
    private volatile Mt5LogTailer tailer;
    private Consumer<String> logCallback;
    private java.util.function.BiConsumer<Integer, Integer> progressCallback;
    private long totalPasses = 1;

    public OptimizationRunner(AppConfig config) {
        this.config = config;
        this.iniGenerator = new IniGenerator();
        this.parser = new OptimizationReportParser();
    }

    public void setLogCallback(Consumer<String> logCallback) {
        this.logCallback = logCallback;
    }

    public void setProgressCallback(java.util.function.BiConsumer<Integer, Integer> progressCallback) {
        this.progressCallback = progressCallback;
    }

    public void setTotalPasses(long totalPasses) {
        this.totalPasses = totalPasses > 0 ? totalPasses : 1;
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
        if (optConfig.getFromDate() != null) result.setFromDate(optConfig.getFromDate().toString());
        if (optConfig.getToDate() != null) result.setToDate(optConfig.getToDate().toString());

        tailer = null;
        MetaTraderPlatform platform = config.getPlatform(optConfig.getExpert());

        // Pre-flight: check for stale MetaTrader processes from previous runs.
        // autoKillMt5=true terminates them WITHOUT asking (unattended runs);
        // false shows a confirmation dialog (GUI) or proceeds without killing (CLI).
        if (!Mt5ProcessGuard.ensureNoStaleProcesses(null, this::logMessage, optConfig.isAutoKillMt5())) {
            logMessage("Optimization aborted: stale MetaTrader process remains running"
                    + " (kill declined, failed, or autoKillMt5=false in CLI mode).");
            result.setMessage("Aborted: stale MetaTrader process");
            return result;
        }

        try {
            // 1. Setup paths
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String outputFolderName = optConfig.toDirectoryName() + "_" + timestamp;
            Path outputDir = resolveOutputDirectory(optConfig, config.getReportsDirectory(), outputFolderName);
            Files.createDirectories(outputDir);
            result.setOutputDirectory(outputDir.toString());
            logMessage("Created output directory: " + outputDir);

            // 2. Cleanup leftover OptimizationReport* only (NOT Tester/cache/*.opt).
            // Wiping .opt on every run would force every guided stage to recompute from
            // scratch. Cache is cleared only via explicit Clear-all.
            // autoKillMt5=true kills terminals of THIS install without asking (used by
            // unattended workflow/automation runs); false asks first (GUI) — a manual
            // multi-day optimization must never be destroyed silently. In CLI mode a
            // declined/absent kill aborts the run instead of asking.
            // Note: optConfig.isShutdownTerminal() only controls whether OUR launched
            // MT closes itself after the run (ini ShutdownTerminal=1). It must NOT
            // silently kill a manually started MetaTrader.
            String terminalPath = config.getTerminalPath(optConfig.getExpert());
            Path mt5Dir = Paths.get(terminalPath).getParent();
            int killed = Mt5ProcessGuard.confirmKillAllTerminalsForInstall(
                    mt5Dir, null, this::logMessage, optConfig.isAutoKillMt5());
            if (killed < 0) {
                result.setMessage("Aborted: MetaTrader still running at install path"
                        + " (kill declined or autoKillMt5=false in CLI mode)");
                logMessage("Optimization aborted: MetaTrader remains running at install path.");
                return result;
            }
            if (killed > 0) {
                logMessage("Beendet " + killed + " alte terminal64-Prozesse für diese MT5-Installation.");
            }
            cleanupOldReports(mt5Dir, REPORT_FILENAME);

            // 3. Generate tester.ini
            Path iniPath = outputDir.resolve("tester.ini");
            iniGenerator.generateForOptimization(optConfig, iniPath, REPORT_FILENAME);
            logMessage("Generated optimization tester.ini");

            // 3b. Archive the preset this run is about to use. The shared
            // Backtester_<EA>.set is overwritten by the next optimization, and the
            // report only lists parameters that actually varied — without this
            // copy a pass can never be reproduced.
            snapshotExpertParameters(optConfig, outputDir);

            // 4. Copy tester.ini to MetaTrader directory to avoid path-with-spaces issues
            // (Java's ProcessBuilder quotes the entire /config: argument when the path has spaces,
            //  which MetaTrader doesn't parse correctly)
            Path mt5TesterIni = mt5Dir.resolve("tester_optimization.ini");
            Files.copy(iniPath, mt5TesterIni, StandardCopyOption.REPLACE_EXISTING);
            logMessage("Copied tester.ini to " + platform.getName() + " directory: " + mt5TesterIni);

            // MT4 looks for relative config paths inside the /config directory by default.
            // Copying it to the config/ folder guarantees MT4 will find it.
            Path mt4TesterIni = mt5Dir.resolve("config").resolve("tester_optimization.ini");
            try {
                Files.createDirectories(mt4TesterIni.getParent());
                Files.copy(iniPath, mt4TesterIni, StandardCopyOption.REPLACE_EXISTING);
                logMessage("Copied tester.ini to " + platform.getName() + " config directory: " + mt4TesterIni);
            } catch (IOException e) {
                log.warn("Failed to copy tester.ini to config/ directory", e);
            }

            // 5. Build and start process on Desktop 2
            java.util.List<String> mt5Args = new java.util.ArrayList<>();
            if (config.isPortableMode()) {
                mt5Args.add("/portable");
            }
            if (platform == MetaTraderPlatform.MT4) {
                mt5Args.add("config\\tester_optimization.ini");
            } else {
                mt5Args.add("/config:tester_optimization.ini");
            }

            final java.util.concurrent.atomic.AtomicInteger currentProgressVal = new java.util.concurrent.atomic.AtomicInteger(0);
            tailer = new Mt5LogTailer(mt5Dir, platform, this::logMessage);
            tailer.setProgressCallback((current, total) -> {
                currentProgressVal.set(current);
                if (progressCallback != null) {
                    int effectiveTotal = total;
                    if (effectiveTotal <= 0) {
                        if (optConfig.getOptimizationMode() == 1 && current <= totalPasses) {
                            effectiveTotal = (int) totalPasses;
                        } else {
                            effectiveTotal = -1;
                        }
                    }
                    progressCallback.accept(current, effectiveTotal);
                }
            });
            tailer.start();

            logMessage("Starting " + platform.getName() + " optimization...");
            currentProcess = VirtualDesktopHelper.startTerminal(terminalPath, mt5Args, mt5Dir);
            if (currentProcess != null) {
                Mt5ProcessGuard.registerProcess(currentProcess);
            } else {
                logMessage("ERROR: Failed to launch MT5 process or it exited immediately (likely another instance is running or lock folder is locked).");
                result.setMessage("Failed to launch MT5 process");
                return result;
            }

            // Asynchronous stream consumer (prevents 64KB buffer deadlock on piped IO)
            String platformName = platform.getName();
            Thread outputConsumer = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(currentProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logMessage("[" + platformName + "] " + line);
                    }
                } catch (IOException e) {
                    if (!cancelled) {
                        log.error("Error reading " + platformName + " output", e);
                    }
                }
            }, "MT-Opt-Output-Consumer");
            outputConsumer.setDaemon(true);
            outputConsumer.start();

            // Wait for completion
            Path reportFile = getOptimizationReportPath(mt5Dir, platform);
            if (optConfig.isShutdownTerminal()) {
                boolean finished = false;
                long lastProgressTime = System.currentTimeMillis();
                int lastProgress = 0;
                
                while (!finished && !cancelled && isProcessRunning(currentProcess, mt5Dir)) {
                    Thread.sleep(2000);
                    int currentProgress = currentProgressVal.get();
                    long now = System.currentTimeMillis();
                    
                    long lastActivity = tailer.getLastActivityTime();
                    if (currentProgress > lastProgress) {
                        lastProgress = currentProgress;
                        lastProgressTime = now;
                    } else if (now - lastActivity < 600_000) {
                        // Reset watchdog timer if there has been any log file activity (new bytes written) recently
                        lastProgressTime = now;
                    } else if (now - lastProgressTime > 600_000) { // 10 minutes without progress or activity
                        if (lastProgress == 0 && now - lastProgressTime > 900_000) { // 15 minutes to start up
                            logMessage("ERROR: " + platformName + " terminal hung during startup (no progress or log activity for 15 minutes). Terminating process...");
                            if (currentProcess != null && currentProcess.isAlive()) currentProcess.destroyForcibly();
                            cancelled = true;
                            result.setMessage("Hung during startup");
                            break;
                        } else if (lastProgress > 0) {
                            logMessage("ERROR: No optimization progress or log activity detected for 10 minutes. Terminating process...");
                            if (currentProcess != null && currentProcess.isAlive()) currentProcess.destroyForcibly();
                            cancelled = true;
                            result.setMessage("Progress timeout");
                            break;
                        }
                    }
                    
                    // Refresh reportFile path check — with ForwardMode wait for .forward.xml too
                    // (same rule as keep-open mode; otherwise we treat the run as done too early).
                    reportFile = getOptimizationReportPath(mt5Dir, platform);
                    if (areOptimizationReportsReady(mt5Dir, platform, optConfig, reportFile)) {
                        finished = true;
                        if (platform == MetaTraderPlatform.MT5 && optConfig.getForwardMode() > 0) {
                            logMessage("Optimization and Forward Test finished (detected report files).");
                        } else {
                            logMessage("Optimization finished (detected report file).");
                        }
                    }
                }
                
                if (!cancelled) {
                    if (currentProcess != null && currentProcess.isAlive()) {
                        logMessage("Waiting for " + platformName + " process to exit...");
                        try {
                            if (!currentProcess.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
                                logMessage("WARNING: " + platformName + " process did not exit within 30 seconds after report completion. Forcibly terminating process...");
                                currentProcess.destroyForcibly();
                            }
                        } catch (InterruptedException e) {
                            logMessage("Interrupted while waiting for " + platformName + " process to exit.");
                            Thread.currentThread().interrupt();
                        }
                        int exitCode = currentProcess.isAlive() ? -1 : currentProcess.exitValue();
                        logMessage(platformName + " process exited with code " + exitCode);
                    } else {
                        logMessage(platformName + " process delegated execution to existing running terminal instance.");
                    }
                    // Brief grace window: MT5 may flush .forward.xml just around process exit.
                    awaitForwardReportAfterExit(mt5Dir, platform, optConfig);
                }
            } else {
                logMessage("Waiting for optimization to finish (" + platformName + " will remain open)...");
                boolean finished = false;
                // Watchdog: same inactivity limits as the shutdown branch. Without
                // this, a terminal that never writes a report would keep this loop
                // spinning forever (the process stays alive because the terminal
                // remains open by design in this mode).
                long lastActivityDeadline = System.currentTimeMillis();
                while (!finished && !cancelled && isProcessRunning(currentProcess, mt5Dir)) {
                    Thread.sleep(2000);
                    long now = System.currentTimeMillis();
                    if (now - tailer.getLastActivityTime() < 600_000) {
                        lastActivityDeadline = now;
                    } else if (now - lastActivityDeadline > 600_000) {
                        logMessage("ERROR: No optimization log activity for 10 minutes (terminal stays open mode). Giving up waiting for the report.");
                        result.setMessage("Progress timeout (terminal remains open)");
                        break;
                    }
                    reportFile = getOptimizationReportPath(mt5Dir, platform);
                    if (Files.exists(reportFile)) {
                        // Let terminal finish writing to the file completely
                        Thread.sleep(2000);
                        if (areOptimizationReportsReady(mt5Dir, platform, optConfig, reportFile)) {
                            finished = true;
                            if (platform == MetaTraderPlatform.MT5 && optConfig.getForwardMode() > 0) {
                                logMessage("Optimization and Forward Test finished (detected report files).");
                            } else {
                                logMessage("Optimization finished (detected report file).");
                            }
                        }
                    }
                }
                if (!finished && !cancelled && !currentProcess.isAlive()) {
                    logMessage(platformName + " process exited unexpectedly before writing report.");
                    awaitForwardReportAfterExit(mt5Dir, platform, optConfig);
                }
            }

            if (cancelled) {
                result.setMessage("Optimization was cancelled.");
                return result;
            }

            // 5. Find and parse report
            reportFile = getOptimizationReportPath(mt5Dir, platform);
            if (Files.exists(reportFile)) {
                if (platform == MetaTraderPlatform.MT4) {
                    Path destHtm = outputDir.resolve("optimization_report.htm");
                    Files.copy(reportFile, destHtm, StandardCopyOption.REPLACE_EXISTING);
                    
                    try {
                        parser.parseHtml(destHtm, result);
                        result.setSuccess(true);
                        logMessage("Parsed MT4 optimization results: " + result.getPasses().size() + " passes found.");
                    } catch (Exception e) {
                        log.error("Failed to parse optimization HTML", e);
                        result.setMessage("Failed to parse MT4 optimization report: " + e.getMessage());
                    }
                } else {
                    Path destXml = outputDir.resolve("optimization_report.xml");
                    Files.copy(reportFile, destXml, StandardCopyOption.REPLACE_EXISTING);
                    
                    try {
                        parser.parse(destXml, result);
                        result.setSuccess(true);
                        logMessage("Parsed optimization results: " + result.getPasses().size() + " passes found.");
                    } catch (Exception e) {
                        log.error("Failed to parse optimization XML", e);
                        result.setMessage("Failed to parse optimization report: " + e.getMessage());
                    }
                }
            } else {
                logMessage("Warning: Optimization report not found at " + reportFile + " — no passes produced.");
                result.setMessage("Optimization report file not found: " + reportFile);
            }

            // Look for forward test report if enabled (only for MT5)
            if (platform == MetaTraderPlatform.MT5 && optConfig.getForwardMode() > 0) {
                Path forwardXml = forwardReportPath(mt5Dir);
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

            result.linkPassesToOutputDirectory();

            String modelName = (optConfig.getModel() >= 0 && optConfig.getModel() < OptimizationConfig.MODEL_NAMES.length)
                    ? OptimizationConfig.MODEL_NAMES[optConfig.getModel()]
                    : String.valueOf(optConfig.getModel());

            if (result.getPasses() != null) {
                for (OptimizationResult.Pass p : result.getPasses()) {
                    if (p.getTickModel() == null || p.getTickModel().isEmpty()) {
                        p.setTickModel(modelName);
                    }
                }
            }
            if (result.getForwardPasses() != null) {
                for (OptimizationResult.Pass p : result.getForwardPasses()) {
                    if (p.getTickModel() == null || p.getTickModel().isEmpty()) {
                        p.setTickModel(modelName);
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
            if (currentProcess != null) {
                Mt5ProcessGuard.unregisterProcess(currentProcess);
                if (currentProcess.isAlive()) {
                    if (optConfig.isShutdownTerminal() || cancelled) {
                        currentProcess.destroyForcibly();
                    } else {
                        logMessage(platform.getName() + " process remains open in background.");
                    }
                }
            }
            if (tailer != null) {
                tailer.stop();
            }
        }

        OptimizationDateRangeResolver.apply(result,
                optConfig.getFromDate(), optConfig.getToDate(),
                optConfig.getForwardMode(), optConfig.getForwardDate());
        return result;
    }

    /**
     * Copies the preset the optimization is started with into its report
     * directory, so the run stays reproducible after the shared preset file has
     * been overwritten by a later run.
     *
     * <p>Passes are NOT bulk-embedded with concrete .set lines anymore (one full
     * setfile per pass was an unbounded memory spike on large optimizations).
     * Instead, {@link com.backtester.report.PassPresetResolver} resolves each
     * pass from this archived preset plus its report columns at execution time
     * and embeds the concrete lines lazily into the few passes that are actually
     * retested, exported or inspected.
     */
    private Path snapshotExpertParameters(OptimizationConfig optConfig, Path outputDir) {
        String presetName = optConfig.getExpertParameters();
        if (presetName == null || presetName.isBlank()) {
            logMessage("Warning: optimization has no ExpertParameters preset — passes will not be reproducible.");
            return null;
        }
        try {
            Path leaf = Path.of(presetName.trim());
            if (leaf.getNameCount() != 1) {
                throw new IOException("Invalid ExpertParameters filename: " + presetName);
            }
            Path profilesDir = config.getTesterProfilesDir(optConfig.getExpert()).toAbsolutePath().normalize();
            Path source = profilesDir.resolve(leaf).normalize();
            if (!source.getParent().equals(profilesDir) || !Files.isRegularFile(source)) {
                throw new IOException("ExpertParameters preset not found: " + source);
            }
            Path snapshot = outputDir.resolve(BacktestArtifactReplayResolver.PRESET_SNAPSHOT_FILE);
            Files.copy(source, snapshot, StandardCopyOption.REPLACE_EXISTING);
            logMessage("Archived optimization preset: " + snapshot.getFileName());
            return snapshot;
        } catch (Exception e) {
            log.error("Failed to archive optimization preset", e);
            logMessage("ERROR: could not archive the optimization preset — passes cannot be "
                    + "reconstructed from an archived preset and later retests may fall back "
                    + "to a shared EA config: " + e.getMessage());
            return null;
        }
    }

    static Path resolveOutputDirectory(OptimizationConfig optConfig, Path defaultBaseDirectory,
                                       String outputFolderName) {
        String configuredBase = optConfig != null ? optConfig.getOutputBaseDirectory() : "";
        Path baseDirectory = configuredBase != null && !configuredBase.isBlank()
                ? Paths.get(configuredBase.trim())
                : defaultBaseDirectory;
        if (baseDirectory == null) {
            baseDirectory = Paths.get("backtest_reports");
        }
        return baseDirectory.toAbsolutePath().normalize().resolve(outputFolderName).normalize();
    }

    private void cleanupOldReports(Path mt5Dir, String reportBaseName) {
        try {
            cleanupDirReports(mt5Dir, reportBaseName);
            cleanupDirReports(mt5Dir.resolve("tester"), reportBaseName);
            cleanupDirReports(mt5Dir.resolve("Tester"), reportBaseName);
        } catch (Exception e) {
            log.warn("Could not clean up old reports: " + e.getMessage());
        }
    }

    private void cleanupDirReports(Path dir, String reportBaseName) {
        if (!Files.exists(dir)) return;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> {
                String name = p.getFileName().toString();
                return name.startsWith(reportBaseName) && (name.endsWith(".xml") || name.endsWith(".htm") || name.endsWith(".html")) && !Files.isDirectory(p);
            }).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    log.warn("Could not delete old report file: " + p, e);
                }
            });
        } catch (IOException e) {
            log.warn("Could not clean up old reports in " + dir, e);
        }
    }

    /**
     * True when the main optimization report exists and — if MT5 ForwardMode is on —
     * the companion {@code *.forward.xml} exists as well.
     */
    static boolean areOptimizationReportsReady(Path mt5Dir,
                                               MetaTraderPlatform platform,
                                               OptimizationConfig optConfig,
                                               Path reportFile) {
        if (reportFile == null || !Files.exists(reportFile)) {
            return false;
        }
        if (platform == MetaTraderPlatform.MT5 && optConfig != null && optConfig.getForwardMode() > 0) {
            return Files.exists(forwardReportPath(mt5Dir));
        }
        return true;
    }

    static Path forwardReportPath(Path mt5Dir) {
        return mt5Dir.resolve(REPORT_FILENAME + ".forward.xml");
    }

    /**
     * After the terminal process has already exited, give MT5 a short window to
     * flush {@code *.forward.xml} to disk when ForwardMode is enabled.
     */
    private void awaitForwardReportAfterExit(Path mt5Dir,
                                             MetaTraderPlatform platform,
                                             OptimizationConfig optConfig) throws InterruptedException {
        if (cancelled || platform != MetaTraderPlatform.MT5 || optConfig == null || optConfig.getForwardMode() <= 0) {
            return;
        }
        Path forwardXml = forwardReportPath(mt5Dir);
        if (Files.exists(forwardXml)) {
            return;
        }
        logMessage("Waiting up to 15s for Forward report after terminal exit: " + forwardXml.getFileName());
        long deadline = System.currentTimeMillis() + 15_000;
        while (!cancelled && System.currentTimeMillis() < deadline) {
            if (Files.exists(forwardXml)) {
                logMessage("Forward report appeared after terminal exit.");
                return;
            }
            Thread.sleep(500);
        }
        if (!Files.exists(forwardXml)) {
            logMessage("WARNING: ForwardMode is on but " + forwardXml.getFileName() + " was not written.");
        }
    }

    private Path getOptimizationReportPath(Path mt5Dir, MetaTraderPlatform platform) {
        String reportExt = platform.getReportFileExtension();
        if (platform == MetaTraderPlatform.MT4) {
            Path testerPath = mt5Dir.resolve("tester").resolve(REPORT_FILENAME + reportExt);
            if (Files.exists(testerPath)) {
                return testerPath;
            }
            Path rootPath = mt5Dir.resolve(REPORT_FILENAME + reportExt);
            if (Files.exists(rootPath)) {
                return rootPath;
            }
            return testerPath;
        } else {
            return mt5Dir.resolve(REPORT_FILENAME + reportExt);
        }
    }

    public void cancel() {
        cancelled = true;
        if (currentProcess != null && currentProcess.isAlive()) {
            currentProcess.destroyForcibly();
            logMessage("Optimization process terminated.");
        }
    }

    private boolean isProcessRunning(Process process, Path mt5Dir) {
        if (process != null && process.isAlive()) {
            return true;
        }
        if (tailer != null && (System.currentTimeMillis() - tailer.getLastActivityTime()) < 30_000) {
            return true;
        }
        return isMt5ProcessRunning(mt5Dir);
    }

    /**
     * True only if a terminal/terminal64 process is still alive for this install path.
     * Avoids treating unrelated MetaTrader instances on the machine as "still running".
     */
    private boolean isMt5ProcessRunning(Path mt5Dir) {
        if (mt5Dir == null) return false;
        try {
            String prefix = mt5Dir.toAbsolutePath().normalize().toString().toLowerCase(java.util.Locale.ROOT);
            return ProcessHandle.allProcesses()
                .anyMatch(ph -> {
                    if (!ph.isAlive()) return false;
                    java.util.Optional<String> cmd = ph.info().command();
                    if (cmd.isEmpty()) return false;
                    String path = cmd.get().toLowerCase(java.util.Locale.ROOT).replace('/', '\\');
                    boolean isTerminal = path.endsWith("\\terminal64.exe")
                            || path.endsWith("\\terminal.exe")
                            || path.endsWith("terminal64.exe")
                            || path.endsWith("terminal.exe");
                    return isTerminal && path.startsWith(prefix);
                });
        } catch (Exception e) {
            return false;
        }
    }
}
