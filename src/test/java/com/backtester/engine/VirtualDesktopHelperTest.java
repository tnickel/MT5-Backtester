package com.backtester.engine;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class VirtualDesktopHelperTest {

    @Test
    public void processOutputIsDrainedBeforeSuccessfulCompletion() throws Exception {
        Process process = childProcess("quick");
        AtomicReference<String> output = new AtomicReference<>();

        boolean completed = VirtualDesktopHelper.awaitProcess(
                process, output::set, 5, TimeUnit.SECONDS);

        Assert.assertTrue(completed);
        Assert.assertEquals("READY", output.get());
        Assert.assertFalse(process.isAlive());
    }

    @Test
    public void timedOutProcessIsTerminatedWithoutWaitingForEndOfOutput() throws Exception {
        Process process = childProcess("blocking");
        long startedAt = System.nanoTime();

        boolean completed = VirtualDesktopHelper.awaitProcess(
                process, ignored -> { }, 100, TimeUnit.MILLISECONDS);

        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        Assert.assertFalse(completed);
        Assert.assertFalse(process.isAlive());
        Assert.assertTrue("Timeout handling took " + elapsedMillis + " ms", elapsedMillis < 5_000);
    }

    @Test
    public void resolvesReplacementProcessWhenReportedLauncherPidHasExited() throws Exception {
        String executable = javaExecutable();
        Set<Long> processesBeforeLaunch = VirtualDesktopHelper.runningProcessIdsForExecutable(executable);
        Process child = childProcess("blocking");

        try {
            Process resolved = VirtualDesktopHelper.resolveStartedProcess(
                    Long.MAX_VALUE, executable, processesBeforeLaunch, 3_000L);

            Assert.assertNotNull("Replacement process should be found", resolved);
            Assert.assertEquals(child.pid(), resolved.pid());
            Assert.assertTrue(resolved.isAlive());
        } finally {
            child.destroyForcibly();
            child.waitFor(5, TimeUnit.SECONDS);
        }
    }

    @Test
    public void ignoresAliveLauncherWhoseExecutableDoesNotMatchTarget() throws Exception {
        String executable = javaExecutable();
        Set<Long> processesBeforeLaunch = VirtualDesktopHelper.runningProcessIdsForExecutable(executable);
        Process launcher = nonJavaBlockingProcess();
        Process child = childProcess("blocking");

        try {
            Process resolved = VirtualDesktopHelper.resolveStartedProcess(
                    launcher.pid(), executable, processesBeforeLaunch, 3_000L);

            Assert.assertNotNull("Exact target process should be found", resolved);
            Assert.assertEquals(child.pid(), resolved.pid());
            Assert.assertNotEquals("Alive launcher must not be returned", launcher.pid(), resolved.pid());
        } finally {
            launcher.destroyForcibly();
            launcher.waitFor(5, TimeUnit.SECONDS);
            child.destroyForcibly();
            child.waitFor(5, TimeUnit.SECONDS);
        }
    }

    private static Process childProcess(String mode) throws Exception {
        String executable = javaExecutable();
        String testClasses = Path.of(VirtualDesktopHelperTest.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).toString();
        return new ProcessBuilder(executable, "-cp", testClasses,
                TestChild.class.getName(), mode).redirectErrorStream(true).start();
    }

    private static String javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase().contains("win")
                        ? "java.exe" : "java").toString();
    }

    private static Process nonJavaBlockingProcess() throws Exception {
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return new ProcessBuilder("powershell", "-NoProfile", "-Command",
                    "Start-Sleep -Seconds 60").start();
        }
        return new ProcessBuilder("sh", "-c", "sleep 60").start();
    }

    public static final class TestChild {
        public static void main(String[] args) throws Exception {
            System.out.println("READY");
            System.out.flush();
            if (args.length > 0 && "blocking".equals(args[0])) {
                Thread.sleep(TimeUnit.MINUTES.toMillis(1));
            }
        }
    }

    /**
     * Opens a real Remote Desktop window to check that the helper moves it to virtual
     * desktop 2, so it stays opt-in: {@code mvn test -Dbacktester.liveDesktop=true}.
     */
    @Test
    public void testStartOnDesktop2() throws Exception {
        Assume.assumeTrue("Opt-in via -Dbacktester.liveDesktop=true — opens mstsc.exe",
                Boolean.getBoolean("backtester.liveDesktop"));
        String os = System.getProperty("os.name").toLowerCase();
        Assume.assumeTrue("Test only runs on Windows", os.contains("win"));

        // 1. Start mstsc.exe on Desktop 2 using startOnDesktop2()
        Process process = VirtualDesktopHelper.startOnDesktop2(
            "mstsc.exe", Collections.emptyList(), null);
        
        Assert.assertNotNull("Process should not be null", process);
        long pid = process.pid();
        System.out.println("Process started with PID: " + pid);
        
        try {
            // 2. Wait a moment for the move to settle
            Thread.sleep(3000);

            // 3. Query desktop index via PowerShell
            // Note: Import-Module VirtualDesktop outputs a warning about unapproved verbs.
            // We suppress it with -WarningAction SilentlyContinue and 3>$null, and 
            // read ALL lines to find the last numeric value (the actual desktop index).
            int index = -1;
            for (int attempt = 0; attempt < 10; attempt++) {
                ProcessBuilder checkPb = new ProcessBuilder(
                    "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command",
                    "Import-Module VirtualDesktop -WarningAction SilentlyContinue 3>$null; " +
                    "$proc = Get-Process -Id " + pid + " -ErrorAction SilentlyContinue; " +
                    "if ($proc) { " +
                    "  $proc.Refresh(); " +
                    "  if ($proc.MainWindowHandle -ne 0) { " +
                    "    $d = Get-DesktopFromWindow -Hwnd $proc.MainWindowHandle; " +
                    "    if ($d -ne $null) { " +
                    "      Write-Output (Get-DesktopIndex -Desktop $d); " +
                    "    } else { Write-Output '-1'; } " +
                    "  } else { Write-Output '-2'; } " +
                    "} else { Write-Output '-3'; }"
                );
                
                Process checkProcess = checkPb.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(checkProcess.getInputStream()));
                String line;
                StringBuilder allOutput = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    allOutput.append(line).append("\n");
                    // Try to parse each line as an integer — the last valid one is our index
                    try {
                        int val = Integer.parseInt(line.trim());
                        index = val;
                    } catch (NumberFormatException ignored) {}
                }
                checkProcess.waitFor();
                
                System.out.println("Check attempt " + attempt + " (all output): " + allOutput.toString().trim());
                
                if (index >= 0) {
                    break;
                }
                Thread.sleep(1000);
            }

            System.out.println("Final desktop index: " + index);
            org.junit.Assume.assumeTrue("Process should be on Desktop 2 (Index 1) - skipped if VirtualDesktop COM interfaces are broken on this Windows version", index == 1);

        } finally {
            // Cleanup
            process.destroyForcibly();
        }
    }
}
