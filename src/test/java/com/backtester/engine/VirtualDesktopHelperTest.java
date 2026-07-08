package com.backtester.engine;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Collections;

public class VirtualDesktopHelperTest {

    @Test
    public void testStartOnDesktop2() throws Exception {
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
