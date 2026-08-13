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
import java.util.List;
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
            String targetPath = escapedExe.replace("'", "''");
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
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("win")) {
            return startNormally(executable, args, workingDir);
        }

        try {
            StringBuilder argString = new StringBuilder();
            if (args != null && !args.isEmpty()) {
                for (int i = 0; i < args.size(); i++) {
                    if (i > 0) argString.append(" ");
                    argString.append(args.get(i));
                }
            }

            String psScript = buildHiddenPowerShellScript(executable, argString.toString());

            ProcessBuilder pb = new ProcessBuilder(
                "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", psScript
            );
            pb.redirectErrorStream(true);
            if (workingDir != null) {
                pb.directory(workingDir.toFile());
            }

            log.info("Starting process hidden: {} {}", executable, argString);
            Process psProcess = pb.start();

            AtomicLong targetPid = new AtomicLong(-1L);
            boolean completed = awaitProcess(psProcess, line -> {
                log.debug("[VD-PS-Hide] {}", line);
                if (line.startsWith("STARTED_PID:")) {
                    try {
                        targetPid.set(Long.parseLong(line.substring("STARTED_PID:".length()).trim()));
                    } catch (NumberFormatException ignored) {}
                }
            }, 30, TimeUnit.SECONDS);

            if (!completed) {
                destroyStartedTarget(targetPid.get());
                log.warn("PowerShell hidden launch timed out, falling back to normal start");
                return startNormally(executable, args, workingDir);
            }

            if (targetPid.get() > 0) {
                log.info("Process started hidden with PID: {}", targetPid.get());
                final long finalPid = targetPid.get();
                return ProcessHandle.of(finalPid)
                    .map(ph -> (Process) new PidProcess(ph))
                    .orElse(null);
            } else {
                log.warn("Could not determine PID from PowerShell output, falling back to normal start");
                return startNormally(executable, args, workingDir);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("PowerShell hidden launch interrupted");
            return null;
        } catch (Exception e) {
            log.error("Failed to start process hidden, falling back to normal start", e);
            return startNormally(executable, args, workingDir);
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
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("win")) {
            return startNormally(executable, args, workingDir);
        }

        try {
            StringBuilder argString = new StringBuilder();
            if (args != null && !args.isEmpty()) {
                for (int i = 0; i < args.size(); i++) {
                    if (i > 0) argString.append(" ");
                    argString.append(args.get(i));
                }
            }

            int desktopNum = desktopIndex + 1;
            String psScript = buildPowerShellScript(executable, argString.toString(), desktopIndex);

            log.info("Starting process on Virtual Desktop {} (move-window): {} {}", desktopNum, executable, argString);

            final long[] targetPid = new long[]{-1};
            executePowerShellScript(psScript, line -> {
                log.info("[VD-PS] {}", line);
                if (line.startsWith("STARTED_PID:")) {
                    try {
                        targetPid[0] = Long.parseLong(line.substring("STARTED_PID:".length()).trim());
                    } catch (NumberFormatException ignored) {}
                }
            }, workingDir);

            if (targetPid[0] > 0) {
                log.info("Process successfully launched on Virtual Desktop {} with PID: {}", desktopNum, targetPid[0]);
                return ProcessHandle.of(targetPid[0])
                    .map(ph -> (Process) new PidProcess(ph))
                    .orElse(null);
            } else {
                log.warn("Could not determine PID from PowerShell output, falling back to normal start");
                return startNormally(executable, args, workingDir);
            }

        } catch (Exception e) {
            log.error("Failed to start process on Virtual Desktop {}, falling back to normal start", desktopIndex + 1, e);
            return startNormally(executable, args, workingDir);
        }
    }

    public static void moveProcessToDesktop(Process process, int desktopIndex) {
        if (process == null) return;
        String os = System.getProperty("os.name").toLowerCase();
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
            } catch (Exception e) {
                log.error("Failed to move process {} to Desktop {}", pid, desktopNum, e);
            }
        }, "VirtualDesktop-Move-Thread-" + pid).start();
    }

    public static void moveProcessToDesktop2(Process process) {
        moveProcessToDesktop(process, 1);
    }

    private static void executePowerShellScript(String psScript, Consumer<String> lineConsumer, Path workingDir) {
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
            if (!awaitProcess(ps, lineConsumer, 30, TimeUnit.SECONDS)) {
                log.warn("PowerShell virtual-desktop command timed out after 30 seconds");
            }
        } catch (InterruptedException e) {
            if (ps != null) terminateProcess(ps);
            Thread.currentThread().interrupt();
            log.warn("PowerShell virtual-desktop command interrupted");
        } catch (Exception e) {
            log.error("Failed to execute PowerShell script", e);
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

    private static String buildPowerShellScript(String executable, String arguments, int desktopIndex) {
        String escapedExe = executable.replace("'", "''");
        String escapedArgs = arguments.replace("'", "''");
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
            (escapedArgs.isEmpty()
                ? "$app = Start-Process -FilePath '" + escapedExe + "' -PassThru; "
                : "$app = Start-Process -FilePath '" + escapedExe + "' -ArgumentList '" + escapedArgs + "' -PassThru; ") +
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

    private static String buildHiddenPowerShellScript(String executable, String arguments) {
        String escapedExe = executable.replace("'", "''");
        String escapedArgs = arguments.replace("'", "''");
        if (!escapedArgs.contains("/hide")) {
            escapedArgs = (escapedArgs + " /hide").trim();
        }

        return "$showWindow = Add-Type -MemberDefinition ' " +
            "[DllImport(\"user32.dll\")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow); " +
            "' -Name 'Win32ShowWindow' -Namespace 'Win32' -PassThru; " +
            (escapedArgs.isEmpty()
                ? "$app = Start-Process -FilePath '" + escapedExe + "' -WindowStyle Hidden -PassThru; "
                : "$app = Start-Process -FilePath '" + escapedExe + "' -ArgumentList '" + escapedArgs + "' -WindowStyle Hidden -PassThru; ") +
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
