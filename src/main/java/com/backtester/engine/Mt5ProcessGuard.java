package com.backtester.engine;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks MetaTrader processes (MT4/MT5) started by this application and provides a pre-flight check
 * to detect and optionally kill stale processes before launching a new one.
 * 
 * Uses Java's ProcessHandle API for fast, native process lookups (no shell commands).
 * Only kills processes that were started by this application instance.
 */
public class Mt5ProcessGuard {

    // Keep the original Process object, not only its PID. A numeric PID may be
    // reused after MetaTrader exits; the Process object keeps the original OS identity.
    private static final ConcurrentMap<Long, Process> ourProcesses = new ConcurrentHashMap<>();

    /**
     * Register a process we just started so we can track it.
     */
    public static void registerProcess(Process process) {
        if (process != null) {
            ourProcesses.put(process.pid(), process);
        }
    }

    /**
     * Unregister a process (e.g. after it has exited cleanly).
     */
    public static void unregisterProcess(Process process) {
        if (process != null) {
            ourProcesses.remove(process.pid(), process);
        }
    }

    /**
     * Quick check: are any of our previously started MetaTrader processes still alive?
     * Uses ProcessHandle.of(pid).isPresent() which is a fast native call.
     * 
     * @return set of still-alive PIDs that we started
     */
    public static Set<Long> getAliveOurProcesses() {
        Set<Long> alive = ConcurrentHashMap.newKeySet();
        for (var entry : ourProcesses.entrySet()) {
            Long pid = entry.getKey();
            Process process = entry.getValue();
            if (process.isAlive()) {
                alive.add(pid);
            } else {
                // Conditional removal cannot delete a newly registered process that
                // happened to receive the same PID while this scan was running.
                ourProcesses.remove(pid, process);
            }
        }
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
            boolean allStopped = true;
            for (Long pid : alive) {
                if (logCallback != null) {
                    logCallback.accept("Beende alten MetaTrader-Prozess (PID " + pid + ")...");
                }
                allStopped &= terminateTrackedProcess(pid, 10, TimeUnit.SECONDS);
            }
            if (logCallback != null) {
                logCallback.accept(allStopped
                        ? "Alte MetaTrader-Prozesse wurden automatisch beendet."
                        : "Mindestens ein alter MetaTrader-Prozess konnte nicht sicher beendet werden.");
            }
            return allStopped;
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

        int choice;
        try {
            choice = showConfirmDialogOnEdt(parentComponent, message, "MetaTrader läuft noch",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (InvocationTargetException e) {
            if (logCallback != null) logCallback.accept("MetaTrader-Rückfrage fehlgeschlagen: " + e.getCause());
            return false;
        }

        if (choice == JOptionPane.OK_OPTION) {
            boolean allStopped = true;
            for (Long pid : alive) {
                if (logCallback != null) {
                    logCallback.accept("Beende alten MetaTrader-Prozess (PID " + pid + ")...");
                }
                allStopped &= terminateTrackedProcess(pid, 10, TimeUnit.SECONDS);
            }

            if (logCallback != null) {
                logCallback.accept(allStopped
                        ? "Alte MetaTrader-Prozesse wurden beendet."
                        : "Mindestens ein alter MetaTrader-Prozess konnte nicht sicher beendet werden.");
            }
            return allStopped;
        } else {
            if (logCallback != null) {
                logCallback.accept("Abbruch: Benutzer hat das Beenden des alten MetaTrader-Prozesses abgelehnt.");
            }
            return false;
        }
    }

    /**
     * Lists live {@code terminal64.exe}/{@code terminal.exe} processes whose
     * executable lives under {@code mtInstallDir}. Read-only — never kills.
     */
    public static java.util.List<Long> findTerminalPidsForInstall(Path mtInstallDir) {
        java.util.ArrayList<Long> pids = new java.util.ArrayList<>();
        if (mtInstallDir == null) {
            return pids;
        }
        Path normalizedInstall;
        try {
            normalizedInstall = mtInstallDir.toAbsolutePath().normalize();
        } catch (Exception ex) {
            return pids;
        }
        for (ProcessHandle ph : ProcessHandle.allProcesses().toList()) {
            try {
                if (!isTerminalForInstall(ph, normalizedInstall)) {
                    continue;
                }
                if (ph.isAlive()) {
                    pids.add(ph.pid());
                }
            } catch (Exception ignored) {
                // Process may have exited while iterating.
            }
        }
        return pids;
    }

    /**
     * Lists live {@code metatester64.exe} processes whose executable lives under
     * {@code mtInstallDir} (any subdirectory — tester agents run from nested
     * "Tester Agent" folders). Read-only — never kills. Processes whose executable
     * path is not readable are skipped, never matched by image name alone.
     */
    public static java.util.List<Long> findMetatesterPidsForInstall(Path mtInstallDir) {
        java.util.ArrayList<Long> pids = new java.util.ArrayList<>();
        if (mtInstallDir == null) {
            return pids;
        }
        Path normalizedInstall;
        try {
            normalizedInstall = mtInstallDir.toAbsolutePath().normalize();
        } catch (Exception ex) {
            return pids;
        }
        for (ProcessHandle ph : ProcessHandle.allProcesses().toList()) {
            try {
                if (!isMetatesterForInstall(ph, normalizedInstall)) {
                    continue;
                }
                if (ph.isAlive()) {
                    pids.add(ph.pid());
                }
            } catch (Exception ignored) {
                // Process may have exited while iterating.
            }
        }
        return pids;
    }

    private static boolean isMetatesterForInstall(ProcessHandle ph, Path normalizedInstall) {
        var info = ph.info();
        String cmd = info.command().orElse("");
        if (cmd.isBlank()) return false;
        Path exe;
        try {
            exe = Path.of(cmd).toAbsolutePath().normalize();
        } catch (Exception ex) {
            return false;
        }
        String fileName = exe.getFileName() != null
                ? exe.getFileName().toString().toLowerCase(java.util.Locale.ROOT) : "";
        if (!fileName.equals("metatester64.exe")) {
            return false;
        }
        return exe.startsWith(normalizedInstall);
    }

    /**
     * Asks before killing terminals for this install. Never kills without
     * confirmation unless {@code autoKill} is true (CLI / explicit opt-in).
     *
     * @return number killed, or {@code -1} if the user declined (caller must abort)
     */
    public static int confirmKillAllTerminalsForInstall(Path mtInstallDir,
                                                        Component parentComponent,
                                                        java.util.function.Consumer<String> logCallback,
                                                        boolean autoKill) {
        java.util.List<Long> alive = findTerminalPidsForInstall(mtInstallDir);
        if (alive.isEmpty()) {
            return 0;
        }

        if (autoKill) {
            int killed = killAllTerminalsForInstall(mtInstallDir, logCallback);
            return findTerminalPidsForInstall(mtInstallDir).isEmpty() ? killed : -1;
        }

        boolean isCli = "true".equals(System.getProperty("backtester.cli"))
                || GraphicsEnvironment.isHeadless();
        if (isCli) {
            if (logCallback != null) {
                logCallback.accept("CLI Mode: MetaTrader an dieser Installation läuft (PIDs: "
                        + alive + ") — ohne autoKill wird nichts beendet. Abbruch.");
            }
            return -1;
        }

        String message = "MetaTrader läuft noch in dieser Installation.\n\n"
                + "Pfad: " + mtInstallDir + "\n"
                + "Aktive Prozess-IDs: " + alive + "\n\n"
                + "Soll MetaTrader jetzt beendet werden?\n\n"
                + "⚠️ ACHTUNG: Eine laufende Optimierung, ein Backtest oder Trading\n"
                + "wird abgebrochen — ungespeicherte Ergebnisse können verloren gehen!\n\n"
                + "Wenn gerade eine lange Optimierung läuft: NEIN wählen.";

        int choice;
        try {
            choice = showConfirmDialogOnEdt(parentComponent, message, "MetaTrader beenden?",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        } catch (InvocationTargetException e) {
            if (logCallback != null) logCallback.accept("MetaTrader-Rückfrage fehlgeschlagen: " + e.getCause());
            return -1;
        }

        if (choice != JOptionPane.YES_OPTION) {
            if (logCallback != null) {
                logCallback.accept("Abbruch: Benutzer hat das Beenden von MetaTrader abgelehnt.");
            }
            return -1;
        }
        int killed = killAllTerminalsForInstall(mtInstallDir, logCallback);
        return findTerminalPidsForInstall(mtInstallDir).isEmpty() ? killed : -1;
    }

    /**
     * Kills every {@code terminal64.exe}/{@code terminal.exe} whose path belongs to
     * {@code mtInstallDir}. Prefer {@link #confirmKillAllTerminalsForInstall} so the
     * user can refuse when a valuable optimization is running.
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
        List<ProcessHandle> targets = new ArrayList<>();
        for (ProcessHandle ph : ProcessHandle.allProcesses().toList()) {
            try {
                if (!isTerminalForInstall(ph, normalizedInstall)) {
                    continue;
                }
                if (!ph.isAlive()) continue;
                if (logCallback != null) {
                    logCallback.accept("Beende MetaTrader an Installationspfad (PID " + ph.pid() + ")...");
                }
                targets.add(ph);
            } catch (Exception ignored) {
                // Process may have exited while iterating.
            }
        }

        for (ProcessHandle target : targets) {
            try {
                if (target.isAlive()) target.destroyForcibly();
            } catch (RuntimeException ex) {
                if (logCallback != null) {
                    logCallback.accept("MetaTrader PID " + target.pid() + " konnte nicht beendet werden: " + ex.getMessage());
                }
            }
        }

        int killed = 0;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        for (ProcessHandle target : targets) {
            long remaining = deadline - System.nanoTime();
            boolean stopped = !target.isAlive();
            if (!stopped && remaining > 0L) {
                stopped = awaitExit(target, remaining, TimeUnit.NANOSECONDS);
            }
            if (stopped) {
                ourProcesses.computeIfPresent(target.pid(), (pid, process) -> process.isAlive() ? process : null);
                killed++;
            } else if (logCallback != null) {
                logCallback.accept("MetaTrader PID " + target.pid() + " ist nach 10 Sekunden noch aktiv.");
            }
        }
        return killed;
    }

    private static boolean terminateTrackedProcess(long pid, long timeout, TimeUnit unit) {
        Process process = ourProcesses.get(pid);
        if (process == null) return true;
        if (!process.isAlive()) {
            ourProcesses.remove(pid, process);
            return true;
        }
        try {
            process.destroyForcibly();
        } catch (RuntimeException e) {
            return false;
        }
        try {
            boolean stopped = process.waitFor(timeout, unit);
            if (stopped) ourProcesses.remove(pid, process);
            return stopped;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    static boolean awaitExit(ProcessHandle process, long timeout, TimeUnit unit) {
        if (process == null || !process.isAlive()) return true;
        try {
            process.onExit().get(timeout, unit);
            return !process.isAlive();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (TimeoutException | java.util.concurrent.ExecutionException e) {
            return !process.isAlive();
        }
    }

    private static int showConfirmDialogOnEdt(Component parentComponent,
                                              String message,
                                              String title,
                                              int optionType,
                                              int messageType)
            throws InterruptedException, InvocationTargetException {
        if (SwingUtilities.isEventDispatchThread()) {
            return JOptionPane.showConfirmDialog(parentComponent, message, title, optionType, messageType);
        }
        AtomicInteger choice = new AtomicInteger(JOptionPane.CLOSED_OPTION);
        SwingUtilities.invokeAndWait(() -> choice.set(JOptionPane.showConfirmDialog(
                parentComponent, message, title, optionType, messageType)));
        return choice.get();
    }

    private static boolean isTerminalForInstall(ProcessHandle ph, Path normalizedInstall) {
        var info = ph.info();
        String cmd = info.command().orElse("");
        if (cmd.isBlank()) return false;
        Path exe;
        try {
            exe = Path.of(cmd).toAbsolutePath().normalize();
        } catch (Exception ex) {
            return false;
        }
        String fileName = exe.getFileName() != null
                ? exe.getFileName().toString().toLowerCase(java.util.Locale.ROOT) : "";
        if (!fileName.equals("terminal64.exe") && !fileName.equals("terminal.exe")) {
            return false;
        }
        Path parent = exe.getParent();
        return parent != null && parent.equals(normalizedInstall);
    }
}
