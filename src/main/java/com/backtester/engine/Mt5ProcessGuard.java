package com.backtester.engine;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks MetaTrader processes (MT4/MT5) started by this application and provides a pre-flight check
 * to detect and optionally kill stale processes before launching a new one.
 * 
 * Uses Java's ProcessHandle API for fast, native process lookups (no shell commands).
 * Only kills processes that were started by this application instance.
 */
public class Mt5ProcessGuard {

    // Thread-safe set of PIDs we have launched
    private static final Set<Long> ourPids = ConcurrentHashMap.newKeySet();

    /**
     * Register a process we just started so we can track it.
     */
    public static void registerProcess(Process process) {
        ourPids.add(process.pid());
    }

    /**
     * Unregister a process (e.g. after it has exited cleanly).
     */
    public static void unregisterProcess(Process process) {
        ourPids.remove(process.pid());
    }

    /**
     * Quick check: are any of our previously started MetaTrader processes still alive?
     * Uses ProcessHandle.of(pid).isPresent() which is a fast native call.
     * 
     * @return set of still-alive PIDs that we started
     */
    public static Set<Long> getAliveOurProcesses() {
        Set<Long> alive = ConcurrentHashMap.newKeySet();
        for (Long pid : ourPids) {
            ProcessHandle.of(pid).ifPresent(ph -> {
                if (ph.isAlive()) {
                    alive.add(pid);
                }
            });
        }
        // Clean up dead PIDs from our tracking set
        ourPids.retainAll(alive);
        return alive;
    }

    /**
     * Pre-flight check before starting a new MetaTrader process.
     * If any of our previously started MetaTrader processes are still alive,
     * shows a dialog asking the user whether to kill them.
     * 
     * @param parentComponent the parent component for the dialog (can be null)
     * @param logCallback optional callback for log messages (can be null)
     * @return true if it's safe to proceed (no stale processes, or user chose to kill them),
     *         false if user cancelled
     */
    public static boolean ensureNoStaleProcesses(Component parentComponent, java.util.function.Consumer<String> logCallback) {
        return ensureNoStaleProcesses(parentComponent, logCallback, false);
    }

    public static boolean ensureNoStaleProcesses(Component parentComponent, java.util.function.Consumer<String> logCallback, boolean autoKill) {
        Set<Long> alive = getAliveOurProcesses();

        if (alive.isEmpty()) {
            return true; // All clear
        }

        if (autoKill) {
            for (Long pid : alive) {
                ProcessHandle.of(pid).ifPresent(ph -> {
                    if (logCallback != null) {
                        logCallback.accept("Beende alten MetaTrader-Prozess (PID " + pid + ")...");
                    }
                    ph.destroyForcibly();
                });
            }
            ourPids.removeAll(alive);
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            if (logCallback != null) {
                logCallback.accept("Alte MetaTrader-Prozesse wurden automatisch beendet.");
            }
            return true;
        }

        boolean isCli = "true".equals(System.getProperty("backtester.cli")) || java.awt.GraphicsEnvironment.isHeadless();
        if (isCli) {
            if (logCallback != null) {
                logCallback.accept("CLI Mode: Stale MetaTrader process(es) detected (PIDs: " + alive + "), but autoKill is false. Proceeding without killing.");
            }
            return true;
        }

        // Build message
        String message = "Es läuft noch ein MetaTrader Prozess aus einem vorherigen Lauf.\n\n"
                + "Aktive Prozess-IDs: " + alive + "\n\n"
                + "Soll der alte Prozess beendet werden, bevor ein neuer gestartet wird?";

        int choice = JOptionPane.showConfirmDialog(
                parentComponent,
                message,
                "MetaTrader läuft noch",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (choice == JOptionPane.OK_OPTION) {
            for (Long pid : alive) {
                ProcessHandle.of(pid).ifPresent(ph -> {
                    if (logCallback != null) {
                        logCallback.accept("Beende alten MetaTrader-Prozess (PID " + pid + ")...");
                    }
                    ph.destroyForcibly();
                });
            }
            ourPids.removeAll(alive);

            // Brief wait to let OS release the process
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}

            if (logCallback != null) {
                logCallback.accept("Alte MetaTrader-Prozesse wurden beendet.");
            }
            return true;
        } else {
            if (logCallback != null) {
                logCallback.accept("Abbruch: Benutzer hat das Beenden des alten MetaTrader-Prozesses abgelehnt.");
            }
            return false;
        }
    }

    /**
     * Kills every {@code terminal64.exe}/{@code terminal.exe} whose path belongs to
     * {@code mtInstallDir}. Needed because a leftover terminal (not started by this
     * JVM) makes the next {@code /config:…} launch exit immediately with
     * "delegated execution" and skip Forward reports.
     *
     * @return number of processes destroyed
     */
    public static int killAllTerminalsForInstall(Path mtInstallDir,
                                                 java.util.function.Consumer<String> logCallback) {
        if (mtInstallDir == null) {
            return 0;
        }
        Path normalizedInstall;
        try {
            normalizedInstall = mtInstallDir.toAbsolutePath().normalize();
        } catch (Exception ex) {
            return 0;
        }
        int killed = 0;
        for (ProcessHandle ph : ProcessHandle.allProcesses().toList()) {
            try {
                var info = ph.info();
                String cmd = info.command().orElse("");
                if (cmd.isBlank()) continue;
                Path exe;
                try {
                    exe = Path.of(cmd).toAbsolutePath().normalize();
                } catch (Exception ex) {
                    continue;
                }
                String fileName = exe.getFileName() != null
                        ? exe.getFileName().toString().toLowerCase() : "";
                if (!fileName.equals("terminal64.exe") && !fileName.equals("terminal.exe")) {
                    continue;
                }
                Path parent = exe.getParent();
                if (parent == null || !parent.equals(normalizedInstall)) {
                    continue;
                }
                if (!ph.isAlive()) continue;
                if (logCallback != null) {
                    logCallback.accept("Beende MetaTrader an Installationspfad (PID " + ph.pid() + ")...");
                }
                ph.destroyForcibly();
                ourPids.remove(ph.pid());
                killed++;
            } catch (Exception ignored) {
                // Process may have exited while iterating.
            }
        }
        if (killed > 0) {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        return killed;
    }
}
