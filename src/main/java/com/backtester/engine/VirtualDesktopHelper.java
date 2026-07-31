package com.backtester.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
            log.info("Starting terminal in HEADLESS mode (with background window hider)...");
            List<String> mutableArgs = new ArrayList<>(args);
            if (!mutableArgs.contains("/hide")) {
                mutableArgs.add("/hide");
            }
            Process p = startNormally(executable, mutableArgs, workingDir);
            startHidingLoop(executable);
            return p;
        } else if ("NORMAL".equalsIgnoreCase(launchMode) || !allowVirtualDesktop) {
            log.info("Starting terminal in NORMAL mode...");
            return startNormally(executable, args, workingDir);
        } else {
            log.info("Starting terminal in VIRTUAL_DESKTOP mode...");
            return startOnDesktop2(executable, args, workingDir);
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
                p.waitFor(20, TimeUnit.SECONDS);
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

            long targetPid = -1;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(psProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    log.debug("[VD-PS-Hide] {}", line);
                    if (line.startsWith("STARTED_PID:")) {
                        try {
                            targetPid = Long.parseLong(line.substring("STARTED_PID:".length()).trim());
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }

            psProcess.waitFor(30, TimeUnit.SECONDS);

            if (targetPid > 0) {
                log.info("Process started hidden with PID: {}", targetPid);
                final long finalPid = targetPid;
                return ProcessHandle.of(finalPid)
                    .map(ph -> (Process) new PidProcess(ph))
                    .orElse(null);
            } else {
                log.warn("Could not determine PID from PowerShell output, falling back to normal start");
                return startNormally(executable, args, workingDir);
            }

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
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("win")) {
            // Fallback: just start normally on non-Windows
            return startNormally(executable, args, workingDir);
        }

        try {
            // Build the argument string for Start-Process
            StringBuilder argString = new StringBuilder();
            if (args != null && !args.isEmpty()) {
                for (int i = 0; i < args.size(); i++) {
                    if (i > 0) argString.append(" ");
                    argString.append(args.get(i));
                }
            }

            // PowerShell script that:
            // 1. Imports VirtualDesktop module
            // 2. Ensures Desktop 2 exists
            // 3. Starts the process with Start-Process -PassThru
            // 4. Waits for the window handle
            // 5. Moves the window to Desktop 2
            // 6. Outputs the PID so Java can track it
            String psScript = buildPowerShellScript(executable, argString.toString());

            ProcessBuilder pb = new ProcessBuilder(
                "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", psScript
            );
            pb.redirectErrorStream(true);
            if (workingDir != null) {
                pb.directory(workingDir.toFile());
            }

            log.info("Starting process on Desktop 2: {} {}", executable, argString);
            Process psProcess = pb.start();

            // Read the output to get the PID
            long targetPid = -1;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(psProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    log.debug("[VD-PS] {}", line);
                    if (line.startsWith("STARTED_PID:")) {
                        try {
                            targetPid = Long.parseLong(line.substring("STARTED_PID:".length()).trim());
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }

            // Wait for PowerShell to finish (it should be quick - the target process continues running)
            psProcess.waitFor(30, TimeUnit.SECONDS);

            if (targetPid > 0) {
                log.info("Process started on Desktop 2 with PID: {}", targetPid);
                // Return a handle to the actual target process via its PID
                return ProcessHandle.of(targetPid)
                    .map(ph -> {
                        // Create a Process wrapper that delegates to the ProcessHandle
                        return new PidProcess(ph);
                    })
                    .orElse(null);
            } else {
                log.warn("Could not determine PID from PowerShell output, falling back to normal start");
                return startNormally(executable, args, workingDir);
            }

        } catch (Exception e) {
            log.error("Failed to start process on Desktop 2, falling back to normal start", e);
            return startNormally(executable, args, workingDir);
        }
    }

    /**
     * Moves an already-running process to Desktop 2 using a background thread.
     * This is a best-effort approach for processes already started from Java.
     */
    public static void moveProcessToDesktop2(Process process) {
        if (process == null) return;
        String os = System.getProperty("os.name").toLowerCase();
        if (!os.contains("win")) return;

        long pid = process.pid();
        new Thread(() -> {
            try {
                log.info("Attempting to move PID {} to Virtual Desktop 2...", pid);

                String psScript =
                    "Import-Module VirtualDesktop -WarningAction SilentlyContinue 3>$null; " +
                    "$count = Get-DesktopCount; " +
                    "if ($count -lt 2) { New-Desktop | Out-Null; } " +
                    "$curD = Get-CurrentDesktop; " +
                    "$d2 = Get-Desktop 1; " +
                    "$hwnd = 0; " +
                    "for ($i = 0; $i -lt 40; $i++) { " +
                    "  Start-Sleep -Milliseconds 500; " +
                    "  $procs = Get-Process | Where-Object { $_.Id -eq " + pid + " }; " +
                    "  if (-not $procs) { Write-Host 'Process gone'; break; } " +
                    "  foreach ($p in $procs) { " +
                    "    try { $p.Refresh(); } catch {} " +
                    "    if ($p.MainWindowHandle -ne 0) { $hwnd = $p.MainWindowHandle; break; } " +
                    "  } " +
                    "  if ($hwnd -ne 0) { break; } " +
                    "} " +
                    "if ($hwnd -ne 0) { " +
                    "  try { " +
                    "    Move-Window -Desktop $d2 -Hwnd $hwnd; " +
                    "    Write-Host 'MOVED'; " +
                    "  } catch { Write-Host 'MOVE_FAILED'; } " +
                    "} else { Write-Host 'NO_HWND'; }";

                ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", psScript);
                pb.redirectErrorStream(true);
                Process psProc = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(psProc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.debug("[VD-Move] {}", line);
                    }
                }
                psProc.waitFor(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Failed to move process {} to Desktop 2", pid, e);
            }
        }, "VirtualDesktop-Move-Thread-" + pid).start();
    }

    private static String buildPowerShellScript(String executable, String arguments) {
        // Escape single quotes in paths
        String escapedExe = executable.replace("'", "''");
        String escapedArgs = arguments.replace("'", "''");

        return "Import-Module VirtualDesktop -WarningAction SilentlyContinue 3>$null; " +
            "$count = Get-DesktopCount; " +
            "if ($count -lt 2) { New-Desktop | Out-Null; } " +
            "$d2 = Get-Desktop 1; " +
            (escapedArgs.isEmpty()
                ? "$app = Start-Process -FilePath '" + escapedExe + "' -PassThru; "
                : "$app = Start-Process -FilePath '" + escapedExe + "' -ArgumentList '" + escapedArgs + "' -PassThru; ") +
            "$spid = $app.Id; " +
            "Write-Host \"STARTED_PID:$spid\"; " +
            "for ($i = 0; $i -lt 30; $i++) { " +
            "  Start-Sleep -Milliseconds 150; " +
            "  $p = Get-Process -Id $spid -ErrorAction SilentlyContinue; " +
            "  if ($p) { " +
            "    $p.Refresh(); " +
            "    if ($p.MainWindowHandle -ne 0) { " +
            "      try { Move-Window -Desktop $d2 -Hwnd $p.MainWindowHandle; } catch {} " +
            "      break; " +
            "    } " +
            "  } " +
            "} " +
            "Write-Host 'MOVE_OK';";
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
            return 0;
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
            return 0;
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
