package com.backtester.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Launches a process on Windows Virtual Desktop 2 using the PSVirtualDesktop PowerShell module.
 * <p>
 * The key insight is that we must start the target process FROM PowerShell using Start-Process -PassThru,
 * then immediately call .Refresh() to get the MainWindowHandle, and then Move-Window.
 * Starting the process from Java and trying to look up the HWND from a separate PowerShell context
 * does NOT work reliably because Get-Process returns MainWindowHandle=0 for foreign processes.
 * </p>
 */
public class VirtualDesktopHelper {
    private static final Logger log = LoggerFactory.getLogger(VirtualDesktopHelper.class);

    /**
     * Starts the terminal using the launch mode configured in AppConfig.
     * Supports VIRTUAL_DESKTOP, NORMAL, and HEADLESS (/hide) modes.
     */
    public static Process startTerminal(String executable, List<String> args, Path workingDir, boolean allowVirtualDesktop) {
        String launchMode = com.backtester.config.AppConfig.getInstance().get("mt5.launch.mode", "HEADLESS");

        if ("HEADLESS".equalsIgnoreCase(launchMode)) {
            log.info("Starting terminal in HEADLESS mode (hidden process launch)...");
            List<String> mutableArgs = new ArrayList<>(args != null ? args : java.util.Collections.emptyList());
            if (!mutableArgs.contains("/hide")) {
                mutableArgs.add("/hide");
            }
            return startHidden(executable, mutableArgs, workingDir);
        } else if ("NORMAL".equalsIgnoreCase(launchMode) || !allowVirtualDesktop) {
            log.info("Starting terminal in NORMAL mode...");
            return startNormally(executable, args, workingDir);
        } else {
            int desktopIndex = 1;
            if ("VIRTUAL_DESKTOP_1".equalsIgnoreCase(launchMode)) {
                desktopIndex = 0;
            } else if ("VIRTUAL_DESKTOP_3".equalsIgnoreCase(launchMode)) {
                desktopIndex = 2;
            }
            log.info("Starting terminal on Virtual Desktop {} mode...", desktopIndex + 1);
            return startOnDesktop(executable, args, workingDir, desktopIndex);
        }
    }

    /** Spawns a background thread that hides the window of the specified executable path as soon as it appears. */
    public static void startHidingLoop(String executable) {
        final String escapedExe = executable.replace("/", "\\");

        Thread thread = new Thread(() -> {
            log.info("Starting background window hiding loop for: {}", escapedExe);
            String targetPath = psQuote(escapedExe);
            String psScript =
                "Add-Type -TypeDefinition @\"\n" +
                "using System;\n" +
                "using System.Runtime.InteropServices;\n" +
                "public class WindowHider {\n" +
                "    [DllImport(\"user32.dll\")] private static extern bool EnumWindows(EnumWindowsProc lpEnumFunc, IntPtr lParam);\n" +
                "    [DllImport(\"user32.dll\")] private static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);\n" +
                "    [DllImport(\"user32.dll\")] private static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);\n" +
                "    private delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);\n" +
                "    public static void HideWindowsForPid(uint targetPid) {\n" +
                "        EnumWindows((hWnd, lParam) => {\n" +
                "            uint pid;\n" +
                "            GetWindowThreadProcessId(hWnd, out pid);\n" +
                "            if (pid == targetPid) {\n" +
                "                ShowWindow(hWnd, 0);\n" +
                "            }\n" +
                "            return true;\n" +
                "        }, IntPtr.Zero);\n" +
                "    }\n" +
                "}\n" +
                "\"@;\n" +
                "$targetPath = '" + targetPath + "';\n" +
                "for ($i = 0; $i -lt 600; $i++) {\n" + // 15 seconds total (600 * 25ms)
                "    $procs = Get-Process -Name terminal64, terminal -ErrorAction SilentlyContinue | Where-Object { $_.Path -eq $targetPath };\n" +
                "    foreach ($p in $procs) {\n" +
                "        [WindowHider]::HideWindowsForPid($p.Id);\n" +
                "    }\n" +
                "    Start-Sleep -Milliseconds 25;\n" +
                "}";

            try {
                String encodedScript = java.util.Base64.getEncoder().encodeToString(
                    psScript.getBytes(java.nio.charset.StandardCharsets.UTF_16LE)
                );
                ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encodedScript);
                pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                pb.redirectError(ProcessBuilder.Redirect.DISCARD);
                Process p = pb.start();
                if (!awaitProcess(p, null, 20, TimeUnit.SECONDS)) {
                    log.warn("PowerShell window-hider timed out after 20 seconds");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("PowerShell window-hider interrupted");
            } catch (Exception e) {
                log.error("Error in background window hider process", e);
            }
            log.info("Finished background window hiding loop for: {}", escapedExe);
        }, "MT5-Window-Hider-" + System.currentTimeMillis());
        thread.setDaemon(true);
        thread.start();
    }

    /** Starts a process in the background and hides its main window immediately using Win32 API ShowWindow. */
    public static Process startHidden(String executable, List<String> args, Path workingDir) {
        String os = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT);
        if (!os.contains("win")) {
            return startNormally(executable, args, workingDir);
        }

        AtomicLong targetPid = new AtomicLong(-1L);
        try {
            Set<Long> processesBeforeLaunch = runningProcessIdsForExecutable(executable);
            StringBuilder argString = new StringBuilder();
            if (args != null && !args.isEmpty()) {
                for (int i = 0; i < args.size(); i++) {
                    if (i > 0) argString.append(" ");
                    argString.append(args.get(i));
                }
            }

            String psScript = buildHiddenPowerShellScript(executable, args);

            log.info("Starting process hidden: {} {}", executable, argString);

            boolean completed = executePowerShellScript(psScript, line -> {
                log.debug("[VD-PS-Hide] {}", line);
                if (line.startsWith("STARTED_PID:")) {
                    try {
                        targetPid.set(Long.parseLong(line.substring("STARTED_PID:".length()).trim()));
                    } catch (NumberFormatException ignored) {}
                }
            }, workingDir);

            if (!completed) {
                destroyStartedTarget(targetPid.get());
                log.warn("PowerShell hidden launch timed out, falling back to normal start");
                return startNormally(executable, args, workingDir);
            }

            if (targetPid.get() > 0) {
                log.info("Process started hidden with PID: {}", targetPid.get());
                // MetaTrader can hand the launch to its LiveUpdate executable and only
                // start the requested terminal after the updater has finished.  Do not
                // return the short-lived launcher; wait for the exact terminal binary.
                return resolveStartedProcess(targetPid.get(), executable, processesBeforeLaunch, 90_000L);
            } else {
                log.warn("Could not determine PID from PowerShell output, falling back to normal start");
                return startNormally(executable, args, workingDir);
            }

        } catch (InterruptedException e) {
            destroyStartedTarget(targetPid.get());
            Thread.currentThread().interrupt();
            log.warn("PowerShell hidden launch interrupted");
            return null;
        } catch (Exception e) {
            log.error("Failed to start process hidden, falling back to normal start", e);
            return startNormally(executable, args, workingDir);
        }
    }

    static Set<Long> runningProcessIdsForExecutable(String executable) {
        Set<Long> processIds = new HashSet<>();
        ProcessHandle.allProcesses().forEach(handle -> {
            if (handle.isAlive() && executableMatches(handle, executable)) {
                processIds.add(handle.pid());
            }
        });
        return processIds;
    }

    /**
     * Snapshot of alive processes for {@code executable} that were not running in
     * {@code processesBeforeLaunch}. Bounded to two results so callers can treat
     * "exactly one" as safe and anything more as ambiguous.
     */
    private static List<ProcessHandle> newProcessesForExecutable(String executable, Set<Long> processesBeforeLaunch) {
        Set<Long> excluded = processesBeforeLaunch != null ? processesBeforeLaunch : Set.of();
        return ProcessHandle.allProcesses()
            .filter(ProcessHandle::isAlive)
            .filter(handle -> !excluded.contains(handle.pid()))
            .filter(handle -> executableMatches(handle, executable))
            .limit(2)
            .toList();
    }

    static Process resolveStartedProcess(long reportedPid,
                                         String executable,
                                         Set<Long> processesBeforeLaunch,
                                         long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeoutMillis));
        Set<Long> excluded = processesBeforeLaunch != null ? processesBeforeLaunch : Set.of();

        do {
            ProcessHandle reported = ProcessHandle.of(reportedPid).orElse(null);
            if (reported != null && reported.isAlive() && executableMatches(reported, executable)) {
                return new PidProcess(reported);
            }

            List<ProcessHandle> replacements = ProcessHandle.allProcesses()
                .filter(ProcessHandle::isAlive)
                .filter(handle -> !excluded.contains(handle.pid()))
                .filter(handle -> executableMatches(handle, executable))
                .limit(2)
                .toList();
            if (replacements.size() == 1) {
                ProcessHandle replacement = replacements.getFirst();
                log.info("MT5 launcher PID {} was replaced; tracking actual process PID {}.",
                    reportedPid, replacement.pid());
                return new PidProcess(replacement);
            }
            if (replacements.size() > 1) {
                log.error("Cannot safely track MT5 launcher PID {}: multiple new processes match {}.",
                    reportedPid, executable);
                return null;
            }

            if (System.nanoTime() >= deadline) {
                return null;
            }
            Thread.sleep(100L);
        } while (true);
    }

    private static boolean executableMatches(ProcessHandle handle, String executable) {
        if (handle == null || executable == null || executable.isBlank()) {
            return false;
        }
        return handle.info().command()
            .map(command -> normalizedExecutable(command).equals(normalizedExecutable(executable)))
            .orElse(false);
    }

    private static String normalizedExecutable(String executable) {
        try {
            return Path.of(executable).toAbsolutePath().normalize().toString().toLowerCase(java.util.Locale.ROOT);
        } catch (RuntimeException ignored) {
            return executable.replace('/', '\\').toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Overload of startTerminal allowing virtual desktop by default. */
    public static Process startTerminal(String executable, List<String> args, Path workingDir) {
        return startTerminal(executable, args, workingDir, true);
    }

    /**
     * Starts a process and moves its window to Virtual Desktop 2.
     * This replaces the normal Java ProcessBuilder.start() flow.
     * The returned Process object is a wrapper that tracks the actual spawned process by PID.
     *
     * @param executable  Full path to the executable (e.g. terminal64.exe)
     * @param args        Arguments to pass to the executable
     * @param workingDir  Working directory for the process
     * @return The spawned Process (tracked by PID), or null if launch failed
     */
    public static Process startOnDesktop2(String executable, List<String> args, Path workingDir) {
        return startOnDesktop(executable, args, workingDir, 1);
    }

    public static Process startOnDesktop(String executable, List<String> args, Path workingDir, int desktopIndex) {
        String os = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT);
        if (!os.contains("win")) {
            return startNormally(executable, args, workingDir);
        }

        AtomicLong targetPid = new AtomicLong(-1L);
        try {
            Set<Long> processesBeforeLaunch = runningProcessIdsForExecutable(executable);
            StringBuilder argString = new StringBuilder();
            if (args != null && !args.isEmpty()) {
                for (int i = 0; i < args.size(); i++) {
                    if (i > 0) argString.append(" ");
                    argString.append(args.get(i));
                }
            }

            int desktopNum = desktopIndex + 1;
            String psScript = buildPowerShellScript(executable, args, desktopIndex);

            log.info("Starting process on Virtual Desktop {} (move-window): {} {}", desktopNum, executable, argString);

            executePowerShellScript(psScript, line -> {
                log.info("[VD-PS] {}", line);
                if (line.startsWith("STARTED_PID:")) {
                    try {
                        targetPid.set(Long.parseLong(line.substring("STARTED_PID:".length()).trim()));
                    } catch (NumberFormatException ignored) {}
                }
            }, workingDir);

            if (targetPid.get() > 0) {
                log.info("Process successfully launched on Virtual Desktop {} with PID: {}", desktopNum, targetPid.get());
                return resolveStartedProcess(targetPid.get(), executable, processesBeforeLaunch, 90_000L);
            } else {
                // No STARTED_PID from PowerShell. The terminal may still have been
                // launched (e.g. the desktop module failed only after Start-Process) —
                // a blind fallback to startNormally would then spawn a SECOND MT5
                // instance. Wait briefly for exactly one NEW process of the target
                // executable (compared against the pre-launch snapshot) before giving up.
                Process newcomer = null;
                boolean ambiguous = false;
                long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(5_000L);
                do {
                    List<ProcessHandle> newcomers = newProcessesForExecutable(executable, processesBeforeLaunch);
                    if (newcomers.size() == 1) {
                        newcomer = new PidProcess(newcomers.getFirst());
                        break;
                    }
                    if (newcomers.size() > 1) {
                        ambiguous = true;
                        break;
                    }
                    if (System.nanoTime() >= deadline) break;
                    Thread.sleep(250L);
                } while (true);
                if (newcomer != null) {
                    log.info("Virtual-desktop launch: STARTED_PID missing, but new process appeared (PID {}). Tracking it.",
                            newcomer.pid());
                    return newcomer;
                }
                if (ambiguous) {
                    log.error("Virtual-desktop launch: multiple new processes match {} — cannot track one safely, skipping normal-start fallback.",
                            executable);
                    return null;
                }
                log.warn("Could not determine PID from PowerShell output, falling back to normal start");
                return startNormally(executable, args, workingDir);
            }

        } catch (InterruptedException e) {
            destroyStartedTarget(targetPid.get());
            Thread.currentThread().interrupt();
            log.warn("Virtual-desktop launch interrupted");
            return null;
        } catch (Exception e) {
            log.error("Failed to start process on Virtual Desktop {}, falling back to normal start", desktopIndex + 1, e);
            return startNormally(executable, args, workingDir);
        }
    }

    public static void moveProcessToDesktop(Process process, int desktopIndex) {
        if (process == null) return;
        String os = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT);
        if (!os.contains("win")) return;

        long pid = process.pid();
        int desktopNum = desktopIndex + 1;
        new Thread(() -> {
            try {
                log.info("Moving process PID {} to Desktop {} without switching desktop...", pid, desktopNum);
                String psScript =
                    "Import-Module VirtualDesktop -WarningAction SilentlyContinue 3>$null; " +
                    "$count = Get-DesktopCount; " +
                    "if ($count -lt " + desktopNum + ") { " +
                    "    for ($i = $count; $i -lt " + desktopNum + "; $i++) { " +
                    "        New-Desktop | Out-Null; " +
                    "    } " +
                    "} " +
                    "$targetDesktop = Get-Desktop " + desktopIndex + "; " +
                    "$hwnd = 0; " +
                    "for ($i = 0; $i -lt 40; $i++) { " +
                    "    Start-Sleep -Milliseconds 150; " +
                    "    $p = Get-Process -Id " + pid + " -ErrorAction SilentlyContinue; " +
                    "    if ($p) { $p.Refresh(); if ($p.MainWindowHandle -ne 0) { $hwnd = $p.MainWindowHandle; break; } } " +
                    "} " +
                    "if ($hwnd -ne 0) { " +
                    "    try { Move-Window -Desktop $targetDesktop -Hwnd $hwnd; Write-Host 'MOVED'; } catch { Write-Host \"MOVE_ERROR: $_\"; } " +
                    "}";
                executePowerShellScript(psScript, line -> log.info("[VD-Move] {}", line), null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Moving process {} to Desktop {} interrupted", pid, desktopNum);
            } catch (Exception e) {
                log.error("Failed to move process {} to Desktop {}", pid, desktopNum, e);
            }
        }, "VirtualDesktop-Move-Thread-" + pid).start();
    }

    public static void moveProcessToDesktop2(Process process) {
        moveProcessToDesktop(process, 1);
    }

    /**
     * Runs the script via {@code -EncodedCommand} so no shell layer can re-split or interpolate it.
     *
     * @return true if the PowerShell child completed within the timeout
     * @throws InterruptedException when the calling thread is interrupted — the child is
     *         terminated and the interruption propagates so callers can bail out without
     *         running any fallback launch
     */
    private static boolean executePowerShellScript(String psScript, Consumer<String> lineConsumer, Path workingDir) throws InterruptedException {
        Process ps = null;
        try {
            byte[] bytes = psScript.getBytes(StandardCharsets.UTF_16LE);
            String encoded = Base64.getEncoder().encodeToString(bytes);

            ProcessBuilder pb = new ProcessBuilder(
                "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded
            );
            pb.redirectErrorStream(true);
            if (workingDir != null) {
                pb.directory(workingDir.toFile());
            }
            ps = pb.start();
            boolean completed = awaitProcess(ps, lineConsumer, 30, TimeUnit.SECONDS);
            if (!completed) {
                log.warn("PowerShell command timed out after 30 seconds");
            }
            return completed;
        } catch (IOException e) {
            log.error("Failed to execute PowerShell script", e);
            return false;
        } finally {
            if (ps != null && ps.isAlive()) terminateProcess(ps);
        }
    }

    /**
     * Drains output concurrently so a silent or stuck child cannot block before the timeout.
     * Package visibility permits a platform-independent lifecycle regression test.
     */
    static boolean awaitProcess(Process process,
                                Consumer<String> lineConsumer,
                                long timeout,
                                TimeUnit unit) throws InterruptedException {
        if (process == null) return false;
        Consumer<String> sink = lineConsumer != null ? lineConsumer : ignored -> { };
        Thread outputReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) sink.accept(line);
                }
            } catch (IOException e) {
                if (process.isAlive()) log.debug("PowerShell output stream ended unexpectedly", e);
            } catch (RuntimeException e) {
                log.warn("PowerShell output consumer failed", e);
            }
        }, "VirtualDesktop-PowerShell-Output");
        outputReader.setDaemon(true);
        outputReader.start();

        boolean completed = false;
        try {
            completed = process.waitFor(timeout, unit);
            if (!completed) terminateProcess(process);
            return completed;
        } finally {
            if (!completed && process.isAlive()) terminateProcess(process);
            closeQuietly(process.getOutputStream());
            if (completed) outputReader.join(TimeUnit.SECONDS.toMillis(1));
            closeQuietly(process.getInputStream());
            closeQuietly(process.getErrorStream());
            outputReader.join(TimeUnit.SECONDS.toMillis(1));
        }
    }

    private static void terminateProcess(Process process) {
        if (process == null || !process.isAlive()) return;
        process.destroy();
        try {
            if (!process.waitFor(1, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(1, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private static void destroyStartedTarget(long pid) {
        if (pid <= 0) return;
        ProcessHandle.of(pid).ifPresent(handle -> {
            handle.destroy();
            if (handle.isAlive()) handle.destroyForcibly();
        });
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (IOException ignored) {
            // Best effort during process cleanup.
        }
    }

    /** Escapes a PowerShell string literal by doubling embedded single quotes. */
    private static String psQuote(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    /** Renders the arguments as a quoted PowerShell array ({@code @('a','b')}), or an empty string when there are none. */
    private static String toPowerShellArgumentArray(List<String> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return "";
        }
        StringBuilder array = new StringBuilder("@(");
        for (int i = 0; i < arguments.size(); i++) {
            if (i > 0) array.append(",");
            array.append("'").append(psQuote(arguments.get(i))).append("'");
        }
        return array.append(")").toString();
    }

    private static String buildPowerShellScript(String executable, List<String> arguments, int desktopIndex) {
        String escapedExe = psQuote(executable);
        String argumentList = toPowerShellArgumentArray(arguments);
        int targetNum = desktopIndex + 1;

        return "Import-Module VirtualDesktop -WarningAction SilentlyContinue 3>$null; " +
            "$count = Get-DesktopCount; " +
            "Write-Host \"VD_STATUS: Current Virtual Desktop count = $count\"; " +
            "if ($count -lt " + targetNum + ") { " +
            "    for ($i = $count; $i -lt " + targetNum + "; $i++) { " +
            "        New-Desktop | Out-Null; " +
            "    } " +
            "    Write-Host 'VD_STATUS: Created Virtual Desktop " + targetNum + "'; " +
            "} " +
            "$targetDesktop = Get-Desktop " + desktopIndex + "; " +
            "Write-Host \"VD_STATUS: Target desktop = Desktop " + targetNum + " ($targetDesktop)\"; " +
            (argumentList.isEmpty()
                ? "$app = Start-Process -FilePath '" + escapedExe + "' -PassThru; "
                : "$app = Start-Process -FilePath '" + escapedExe + "' -ArgumentList " + argumentList + " -PassThru; ") +
            "$spid = $app.Id; " +
            "Write-Host \"STARTED_PID:$spid\"; " +
            "$hwnd = [IntPtr]::Zero; " +
            "for ($i = 0; $i -lt 50; $i++) { " +
            "  Start-Sleep -Milliseconds 200; " +
            "  $p = Get-Process -Id $spid -ErrorAction SilentlyContinue; " +
            "  if ($p) { " +
            "    $p.Refresh(); " +
            "    if ($p.MainWindowHandle -ne [IntPtr]::Zero -and $p.MainWindowHandle -ne 0) { " +
            "      $hwnd = $p.MainWindowHandle; " +
            "      break; " +
            "    } " +
            "  } " +
            "} " +
            "if ($hwnd -ne [IntPtr]::Zero -and $hwnd -ne 0) { " +
            "  try { " +
            "    Move-Window -Desktop $targetDesktop -Hwnd $hwnd; " +
            "    Write-Host \"VD_STATUS: Successfully moved MT5 window ($hwnd) to Desktop " + targetNum + "\"; " +
            "    Write-Host 'MOVE_OK'; " +
            "  } catch { Write-Host \"MOVE_ERROR: $_\"; } " +
            "} else { Write-Host 'NO_HWND_FOUND'; }";
    }

    private static String buildHiddenPowerShellScript(String executable, List<String> arguments) {
        String escapedExe = psQuote(executable);
        List<String> argList = arguments == null ? new ArrayList<>() : new ArrayList<>(arguments);
        if (!argList.contains("/hide")) {
            argList.add("/hide");
        }
        String argumentList = toPowerShellArgumentArray(argList);

        return "$showWindow = Add-Type -MemberDefinition ' " +
            "[DllImport(\"user32.dll\")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow); " +
            "' -Name 'Win32ShowWindow' -Namespace 'Win32' -PassThru; " +
            (argumentList.isEmpty()
                ? "$app = Start-Process -FilePath '" + escapedExe + "' -WindowStyle Hidden -PassThru; "
                : "$app = Start-Process -FilePath '" + escapedExe + "' -ArgumentList " + argumentList + " -WindowStyle Hidden -PassThru; ") +
            "$spid = $app.Id; " +
            "Write-Host \"STARTED_PID:$spid\"; " +
            "$hwnd = 0; " +
            "for ($i = 0; $i -lt 40; $i++) { " +
            "  Start-Sleep -Milliseconds 500; " +
            "  $p = Get-Process -Id $spid -ErrorAction SilentlyContinue; " +
            "  if ($p) { " +
            "    $p.Refresh(); " +
            "    if ($p.MainWindowHandle -ne 0) { " +
            "      $hwnd = $p.MainWindowHandle; " +
            "      $showWindow::ShowWindow($hwnd, 0) | Out-Null; " +
            "      break; " +
            "    } " +
            "  } " +
            "} " +
            "if ($hwnd -ne 0) { Write-Host 'HIDE_OK'; } else { Write-Host 'NO_HWND_FOUND'; }";
    }

    public static Process startNormally(String executable, List<String> args, Path workingDir) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(executable);
            if (args != null) cmd.addAll(args);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            if (workingDir != null) pb.directory(workingDir.toFile());
            return pb.start();
        } catch (Exception e) {
            log.error("Failed to start process normally", e);
            return null;
        }
    }

    /**
     * A minimal Process wrapper around a ProcessHandle (for processes started externally by PowerShell).
     * Supports isAlive(), pid(), waitFor(), destroy(), destroyForcibly().
     */
    static class PidProcess extends Process {
        private final ProcessHandle handle;
        private final long pid;

        PidProcess(ProcessHandle handle) {
            this.handle = handle;
            this.pid = handle.pid();
        }

        @Override
        public long pid() {
            return pid;
        }

        @Override
        public boolean isAlive() {
            return handle.isAlive();
        }

        @Override
        public int waitFor() throws InterruptedException {
            handle.onExit().join();
            // ProcessHandle does not expose OS exit codes — report unknown, not fake success
            return -1;
        }

        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            try {
                handle.onExit().get(timeout, unit);
                return true;
            } catch (java.util.concurrent.TimeoutException e) {
                return false;
            } catch (java.util.concurrent.ExecutionException e) {
                return true;
            }
        }

        @Override
        public int exitValue() {
            if (handle.isAlive()) throw new IllegalThreadStateException("Process is still running");
            // ProcessHandle does not expose OS exit codes — report unknown, not fake success
            return -1;
        }

        @Override
        public void destroy() {
            handle.destroy();
        }

        @Override
        public Process destroyForcibly() {
            handle.destroyForcibly();
            return this;
        }

        @Override
        public java.io.InputStream getInputStream() {
            return java.io.InputStream.nullInputStream();
        }

        @Override
        public java.io.InputStream getErrorStream() {
            return java.io.InputStream.nullInputStream();
        }

        @Override
        public java.io.OutputStream getOutputStream() {
            return java.io.OutputStream.nullOutputStream();
        }
    }
}
