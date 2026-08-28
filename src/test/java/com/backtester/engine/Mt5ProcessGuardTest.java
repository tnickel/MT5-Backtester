package com.backtester.engine;

import org.junit.Test;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Safety tests: process-guard helpers must stay read-only unless the confirm API is used.
 * These tests never spawn or kill real MetaTrader processes.
 */
public class Mt5ProcessGuardTest {

    @Test
    public void findTerminalPidsForNullInstallReturnsEmpty() {
        assertTrue(Mt5ProcessGuard.findTerminalPidsForInstall(null).isEmpty());
    }

    @Test
    public void killAllTerminalsForMissingInstallReturnsZero() {
        // Non-existent path: must not throw and must not kill anything.
        assertEquals(0, Mt5ProcessGuard.killAllTerminalsForInstall(
                Path.of("C:/definitely/not/a/real/mt5/install_" + System.nanoTime()),
                msg -> {}));
    }

    @Test
    public void confirmKillWithNoTerminalsReturnsZero() {
        assertEquals(0, Mt5ProcessGuard.confirmKillAllTerminalsForInstall(
                Path.of("C:/definitely/not/a/real/mt5/install_" + System.nanoTime()),
                null,
                msg -> {},
                false));
    }

    @Test
    public void trackedProcessIsRemovedByOriginalProcessIdentityAfterExit() throws Exception {
        Process child = blockingChildProcess();
        try {
            Mt5ProcessGuard.registerProcess(child);
            assertTrue(Mt5ProcessGuard.getAliveOurProcesses().contains(child.pid()));

            child.destroyForcibly();
            assertTrue(child.waitFor(5, TimeUnit.SECONDS));
            assertFalse(Mt5ProcessGuard.getAliveOurProcesses().contains(child.pid()));
        } finally {
            child.destroyForcibly();
            child.waitFor(5, TimeUnit.SECONDS);
            Mt5ProcessGuard.unregisterProcess(child);
        }
    }

    @Test
    public void awaitExitTimesOutForRunningProcessAndObservesLaterExit() throws Exception {
        Process child = blockingChildProcess();
        try {
            assertFalse(Mt5ProcessGuard.awaitExit(child.toHandle(), 25, TimeUnit.MILLISECONDS));
            child.destroyForcibly();
            assertTrue(Mt5ProcessGuard.awaitExit(child.toHandle(), 5, TimeUnit.SECONDS));
        } finally {
            child.destroyForcibly();
            child.waitFor(5, TimeUnit.SECONDS);
        }
    }

    private static Process blockingChildProcess() throws Exception {
        String executable = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")
                        ? "java.exe" : "java").toString();
        String testClasses = Path.of(Mt5ProcessGuardTest.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).toString();
        return new ProcessBuilder(executable, "-cp", testClasses,
                VirtualDesktopHelperTest.TestChild.class.getName(), "blocking")
                .redirectErrorStream(true)
                .start();
    }
}
