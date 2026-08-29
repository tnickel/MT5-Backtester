package com.backtester;

import com.backtester.config.AppConfig;
import com.backtester.engine.Mt5ProcessGuard;
import com.formdev.flatlaf.FlatDarkLaf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Application entry point.
 * Initializes FlatLaf dark theme and launches the main window.
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        // Suppress JavaFX VirtualFlow warnings about "index exceeds maxCellCount" to prevent log file bloat
        try {
            java.util.logging.Logger.getLogger("javafx.scene.control.skin").setLevel(java.util.logging.Level.WARNING);
            java.util.logging.Logger.getLogger("com.sun.javafx.scene.control.skin").setLevel(java.util.logging.Level.WARNING);
        } catch (Exception ignored) {}

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            log.error("Uncaught exception in thread: " + thread.getName(), throwable);
        });

        log.info("=== MT5 Backtester Starting ===");

        // Intercept --cli argument for headless batch backtesting
        if (args.length >= 2 && "--cli".equalsIgnoreCase(args[0])) {
            System.setProperty("backtester.cli", "true");
            log.info("CLI Mode: Starting batch execution with config: {}", args[1]);
            try {
                AppConfig.getInstance();
                com.backtester.cli.CliRunner.run(args[1]);
            } catch (Exception e) {
                log.error("Fatal error during CLI execution", e);
                System.exit(1);
            }
            log.info("=== CLI Execution Finished Successfully ===");
            System.exit(0);
        }

        // Initialize configuration first so the leftover-process cleanup can be
        // scoped to the configured MetaTrader installation.
        AppConfig.getInstance();

        // Kill any leftover MetaTrader instances of the configured installation
        // from previous runs. When the Backtester stops abruptly, MT5 may remain
        // running (invisibly on Desktop 2).
        killLeftoverMt5Processes();

        // Launch JavaFX UI
        com.backtester.ui.javafx.AppLauncher.main(args);
    }

    /**
     * Kills leftover MetaTrader terminal processes belonging to the configured backtester
     * installation (parent directory of {@code mt5.terminal.path}) plus any leftover tester
     * agents ({@code metatester64.exe}), which are never live-trading processes.
     * <p>
     * Terminals are only ever killed inside the configured install directory — other
     * MetaTrader installations on this machine (e.g. a separate live-trading terminal) are
     * never touched, and terminals are never killed machine-wide by image name. The user is
     * asked for confirmation before anything is terminated (CLI/headless runs confirm
     * automatically). If no terminal path is configured, no process is killed at all
     * (tester agents are scoped to the same install directory).
     * </p>
     */
    private static void killLeftoverMt5Processes() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return;
        }

        Path installDir = resolveConfiguredInstallDir();
        if (installDir == null) {
            log.warn("No MetaTrader terminal path configured — skipping leftover terminal and tester agent cleanup.");
            killLeftoverTesterAgents();
            return;
        }

        List<Long> terminalPids = Mt5ProcessGuard.findTerminalPidsForInstall(installDir);
        if (terminalPids.isEmpty()) {
            log.info("No leftover MetaTrader processes found for install: {}", installDir);
            return;
        }

        log.info("Found running MetaTrader process(es) for install {}: {}. Asking user for confirmation before killing...",
            installDir, terminalPids);
        if (!confirmKillTerminals(installDir, terminalPids)) {
            log.info("User declined to kill MetaTrader process. Keeping running instance intact.");
            return;
        }

        log.info("Killing MetaTrader process(es) of install {} ...", installDir);
        destroyTerminalProcesses(terminalPids);

        killLeftoverTesterAgents();

        // Show notification to user for 3 seconds
        showMt5KillNotification();
    }

    /** Returns the parent directory of the configured terminal executable, or null if unavailable. */
    private static Path resolveConfiguredInstallDir() {
        try {
            String terminalPath = AppConfig.getInstance().getMt5TerminalPath();
            if (terminalPath == null || terminalPath.isBlank()) {
                return null;
            }
            return Path.of(terminalPath).toAbsolutePath().normalize().getParent();
        } catch (Exception e) {
            log.warn("Could not determine the configured MetaTrader installation directory: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Asks the user (on the EDT) whether the leftover MetaTrader processes of the configured
     * installation may be killed. Defaults to "no" when the dialog cannot be shown.
     */
    private static boolean confirmKillTerminals(Path installDir, List<Long> terminalPids) {
        boolean isCli = "true".equals(System.getProperty("backtester.cli")) || java.awt.GraphicsEnvironment.isHeadless();
        if (isCli) {
            log.info("CLI Mode: MetaTrader process running, auto-terminating for batch processing...");
            return true;
        }

        AtomicInteger choice = new AtomicInteger(JOptionPane.CLOSED_OPTION);
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    FlatDarkLaf.setup();
                } catch (Exception ignored) {}

                choice.set(JOptionPane.showConfirmDialog(
                    null,
                    "Es wurde eine laufende MetaTrader-Instanz Ihrer Backtester-Installation gefunden:\n\n"
                    + installDir + "\nAktive Prozess-IDs: " + terminalPids + "\n\n"
                    + "Möchten Sie den MetaTrader-Prozess beenden?\n\n"
                    + "⚠️ ACHTUNG: Falls im MetaTrader aktuell eine Optimierung, ein Backtest oder Trading läuft,\n"
                    + "gehen ungespeicherte Ergebnisse verloren!",
                    "MetaTrader 5 — Prozess beenden?",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
                ));
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false; // default to "no"
        } catch (InvocationTargetException e) {
            log.warn("MetaTrader confirmation dialog failed: {}", e.getCause());
            return false; // default to "no"
        }
        return choice.get() == JOptionPane.YES_OPTION;
    }

    /** Destroys the given MetaTrader processes via ProcessHandle and waits up to 10 seconds each for exit. */
    private static void destroyTerminalProcesses(List<Long> pids) {
        for (long pid : pids) {
            ProcessHandle.of(pid).ifPresent(handle -> {
                try {
                    if (!handle.isAlive()) {
                        return;
                    }
                    handle.destroyForcibly();
                    handle.onExit().get(10, TimeUnit.SECONDS);
                    log.info("MetaTrader PID {} beendet.", pid);
                } catch (TimeoutException e) {
                    log.warn("MetaTrader PID {} ist nach 10 Sekunden noch aktiv.", pid);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Warten auf MetaTrader PID {} unterbrochen.", pid);
                } catch (ExecutionException e) {
                    log.info("MetaTrader PID {} wurde bereits beendet.", pid);
                }
            });
        }
    }

    /**
     * Kills leftover tester agents ({@code metatester64.exe}) under the configured
     * backtester installation — backtest agents are never live-trading processes.
     * Like the terminal cleanup, the kill is scoped to the configured install
     * directory: agents of other MetaTrader installations are never touched and
     * agents are never killed machine-wide by image name. Processes whose
     * executable path is not readable are skipped.
     */
    private static void killLeftoverTesterAgents() {
        Path installDir = resolveConfiguredInstallDir();
        if (installDir == null) {
            log.info("No MetaTrader terminal path configured — skipping tester agent cleanup.");
            return;
        }
        List<Long> agentPids = Mt5ProcessGuard.findMetatesterPidsForInstall(installDir);
        if (agentPids.isEmpty()) {
            log.info("No leftover tester agents found for install: {}", installDir);
            return;
        }
        log.info("Found leftover tester agent process(es) for install {}: {} — killing...",
            installDir, agentPids);
        destroyTerminalProcesses(agentPids);
    }

    /**
     * Shows a Swing dialog for 3 seconds informing the user that leftover MT5 processes were killed.
     * The dialog is created and shown on the EDT; a Swing timer closes it after 3 seconds.
     */
    private static void showMt5KillNotification() {
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> {
                try {
                    // Use FlatLaf dark theme for the dialog
                    FlatDarkLaf.setup();
                } catch (Exception ignored) {}

                JDialog dialog = new JDialog((Frame) null, "MetaTrader 5 — Cleanup", false);
                dialog.setUndecorated(false);
                dialog.setAlwaysOnTop(true);
                dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

                JPanel panel = new JPanel();
                panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
                panel.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));
                panel.setBackground(new Color(26, 30, 40));

                JLabel titleLabel = new JLabel("⚠  Alte MetaTrader-Instanz beendet");
                titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
                titleLabel.setForeground(new Color(255, 179, 0));
                titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

                JLabel infoLabel = new JLabel("<html><center>Ein MetaTrader 5 Prozess aus einer vorherigen<br>Session wurde gefunden und beendet.</center></html>");
                infoLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                infoLabel.setForeground(new Color(180, 186, 200));
                infoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

                JLabel countdownLabel = new JLabel("Backtester startet in 3 Sekunden...");
                countdownLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
                countdownLabel.setForeground(new Color(126, 136, 154));
                countdownLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

                panel.add(titleLabel);
                panel.add(Box.createVerticalStrut(12));
                panel.add(infoLabel);
                panel.add(Box.createVerticalStrut(8));
                panel.add(countdownLabel);

                dialog.setContentPane(panel);
                dialog.pack();
                dialog.setLocationRelativeTo(null);
                dialog.setVisible(true);

                // Auto-close after 3 seconds (fires on the EDT, no Thread.sleep needed)
                javax.swing.Timer autoClose = new javax.swing.Timer(3000, e -> dialog.dispose());
                autoClose.setRepeats(false);
                autoClose.start();
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("MT5 cleanup notification interrupted");
        } catch (InvocationTargetException e) {
            log.warn("Failed to show MT5 cleanup notification: {}", e.getCause());
        }
    }
}
