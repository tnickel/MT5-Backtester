package com.backtester;

import com.backtester.config.AppConfig;
import com.backtester.ui.MainFrame;
import com.formdev.flatlaf.FlatDarkLaf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;

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

        // Kill any leftover MetaTrader instances from previous runs.
        // When the Backtester stops abruptly, MT5 may remain running (invisibly on Desktop 2).
        killLeftoverMt5Processes();

        // Initialize configuration
        AppConfig.getInstance();

        // Launch JavaFX UI
        com.backtester.ui.javafx.AppLauncher.main(args);
    }

    /**
     * Kills any leftover terminal64.exe (MetaTrader 5) processes from previous runs.
     * This prevents invisible MT5 instances from lingering on Desktop 2.
     */
    private static void killLeftoverMt5Processes() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return;
        }
        try {
            boolean hasTerminal64 = false;
            boolean hasTerminal = false;

            // Check if any terminal64.exe processes are running
            Process check64 = new ProcessBuilder("tasklist", "/FI", "IMAGENAME eq terminal64.exe", "/NH")
                .redirectErrorStream(true).start();
            String output64 = new String(check64.getInputStream().readAllBytes()).trim();
            check64.waitFor();
            if (output64.contains("terminal64.exe")) {
                hasTerminal64 = true;
            }

            // Check if any terminal.exe processes are running
            Process check = new ProcessBuilder("tasklist", "/FI", "IMAGENAME eq terminal.exe", "/NH")
                .redirectErrorStream(true).start();
            String output = new String(check.getInputStream().readAllBytes()).trim();
            check.waitFor();
            if (output.contains("terminal.exe")) {
                hasTerminal = true;
            }

            if (hasTerminal64 || hasTerminal) {
                log.info("Found running MetaTrader process(es). Asking user for confirmation before killing...");
                boolean isCli = "true".equals(System.getProperty("backtester.cli")) || java.awt.GraphicsEnvironment.isHeadless();
                boolean shouldKill = false;

                if (isCli) {
                    log.info("CLI Mode: MetaTrader process running, auto-terminating for batch processing...");
                    shouldKill = true;
                } else {
                    try {
                        FlatDarkLaf.setup();
                    } catch (Exception ignored) {}

                    int choice = JOptionPane.showConfirmDialog(
                        null,
                        "Es wurde eine laufende MetaTrader-Instanz auf Ihrem System gefunden (terminal64.exe / terminal.exe).\n\n" +
                        "Möchten Sie den MetaTrader-Prozess beenden?\n\n" +
                        "⚠️ ACHTUNG: Falls im MetaTrader aktuell eine Optimierung, ein Backtest oder Trading läuft,\n" +
                        "gehen ungespeicherte Ergebnisse verloren!",
                        "MetaTrader 5 — Prozess beenden?",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                    );
                    shouldKill = (choice == JOptionPane.YES_OPTION);
                }

                if (shouldKill) {
                    log.info("User confirmed process termination. Killing MetaTrader process(es)...");
                    if (hasTerminal64) {
                        Process kill64 = new ProcessBuilder("taskkill", "/F", "/IM", "terminal64.exe")
                            .redirectErrorStream(true).start();
                        String killOutput64 = new String(kill64.getInputStream().readAllBytes()).trim();
                        kill64.waitFor();
                        log.info("MT5 cleanup: {}", killOutput64);

                        // Also kill any leftover metatester64.exe processes
                        try {
                            Process killAgents = new ProcessBuilder("taskkill", "/F", "/IM", "metatester64.exe")
                                .redirectErrorStream(true).start();
                            killAgents.waitFor();
                            log.info("Tester agents cleanup completed.");
                        } catch (Exception ex) {
                            log.warn("Failed to kill leftover tester agents: {}", ex.getMessage());
                        }
                    }
                    if (hasTerminal) {
                        Process kill = new ProcessBuilder("taskkill", "/F", "/IM", "terminal.exe")
                            .redirectErrorStream(true).start();
                        String killOutput = new String(kill.getInputStream().readAllBytes()).trim();
                        kill.waitFor();
                        log.info("MT4 cleanup: {}", killOutput);
                    }

                    // Show notification to user for 3 seconds
                    showMt5KillNotification();
                } else {
                    log.info("User declined to kill MetaTrader process. Keeping running instance intact.");
                }
            } else {
                log.info("No leftover MetaTrader processes found.");
            }
        } catch (Exception e) {
            log.warn("Failed to check/kill leftover MetaTrader processes: {}", e.getMessage());
        }
    }

    /**
     * Shows a Swing dialog for 3 seconds informing the user that leftover MT5 processes were killed.
     */
    private static void showMt5KillNotification() {
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

        // Auto-close after 3 seconds (blocking)
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ignored) {}
        dialog.dispose();
    }
}
