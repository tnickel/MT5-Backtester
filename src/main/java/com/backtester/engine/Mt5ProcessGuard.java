package com.backtester.engine;

import javax.swing.*;
import java.awt.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks MT5 processes started by this application and provides a pre-flight check
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
     * Quick check: are any of our previously started MT5 processes still alive?
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
     * Pre-flight check before starting a new MT5 process.
     * If any of our previously started MT5 processes are still alive,
     * shows a dialog asking the user whether to kill them.
     * 
     * @param parentComponent the parent component for the dialog (can be null)
     * @param logCallback optional callback for log messages (can be null)
     * @return true if it's safe to proceed (no stale processes, or user chose to kill them),
     *         false if user cancelled
     */
    public static boolean ensureNoStaleProcesses(Component parentComponent, java.util.function.Consumer<String> logCallback) {
        Set<Long> alive = getAliveOurProcesses();

        if (alive.isEmpty()) {
            return true; // All clear
        }

        // Build message
        String message = "Es läuft noch ein MetaTrader 5 Prozess aus einem vorherigen Lauf.\n\n"
                + "Aktive Prozess-IDs: " + alive + "\n\n"
                + "Soll der alte Prozess beendet werden, bevor ein neuer gestartet wird?";

        int choice = JOptionPane.showConfirmDialog(
                parentComponent,
                message,
                "MetaTrader 5 läuft noch",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (choice == JOptionPane.OK_OPTION) {
            for (Long pid : alive) {
                ProcessHandle.of(pid).ifPresent(ph -> {
                    if (logCallback != null) {
                        logCallback.accept("Beende alten MT5-Prozess (PID " + pid + ")...");
                    }
                    ph.destroyForcibly();
                });
            }
            ourPids.removeAll(alive);

            // Brief wait to let OS release the process
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}

            if (logCallback != null) {
                logCallback.accept("Alte MT5-Prozesse wurden beendet.");
            }
            return true;
        } else {
            if (logCallback != null) {
                logCallback.accept("Abbruch: Benutzer hat das Beenden des alten Prozesses abgelehnt.");
            }
            return false;
        }
    }
}
