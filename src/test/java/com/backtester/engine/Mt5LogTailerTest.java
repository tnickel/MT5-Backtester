package com.backtester.engine;

import org.junit.Test;
import static org.junit.Assert.*;

public class Mt5LogTailerTest {

    @Test
    public void testShouldForwardToUiWithWarningsAndErrors() {
        assertTrue(Mt5LogTailer.shouldForwardToUi("2026.05.25 18:00:00 [Tester] ERROR: connection lost"));
        assertTrue(Mt5LogTailer.shouldForwardToUi("[Tester] failed to load indicator"));
        assertTrue(Mt5LogTailer.shouldForwardToUi("[Terminal] critical exception at 0x00FF4E2D"));
        assertTrue(Mt5LogTailer.shouldForwardToUi("warning: invalid parameter combination"));
        assertTrue(Mt5LogTailer.shouldForwardToUi("[Tester] test aborted by user"));
        assertTrue(Mt5LogTailer.shouldForwardToUi("timeout waiting for agent response"));
    }

    @Test
    public void testShouldForwardToUiWithLifecycleEvents() {
        assertTrue(Mt5LogTailer.shouldForwardToUi("Terminal exit with code 0"));
        assertTrue(Mt5LogTailer.shouldForwardToUi("stopped with 0"));
        assertTrue(Mt5LogTailer.shouldForwardToUi("shutdown with 0"));
    }

    @Test
    public void testShouldForwardToUiWithConnectionEvents() {
        assertTrue(Mt5LogTailer.shouldForwardToUi("connected to Tickmill-Live"));
        assertTrue(Mt5LogTailer.shouldForwardToUi("disconnected from server"));
        assertTrue(Mt5LogTailer.shouldForwardToUi("login success"));
    }

    @Test
    public void testShouldForwardToUiWithSummaryStatistics() {
        assertTrue(Mt5LogTailer.shouldForwardToUi("final balance 10140.74 USD"));
        assertTrue(Mt5LogTailer.shouldForwardToUi("automatical testing finished"));
        assertTrue(Mt5LogTailer.shouldForwardToUi("test passed in 0:00:01.001"));
        assertTrue(Mt5LogTailer.shouldForwardToUi("GBPUSD,M5: 716137 ticks, 36498 bars generated."));
        assertTrue(Mt5LogTailer.shouldForwardToUi("2144011 total ticks for all symbols"));
    }

    @Test
    public void testShouldNotForwardNoisyLogs() {
        assertFalse(Mt5LogTailer.shouldForwardToUi("FXSSI OK: GBPCHF SELL (Buy:83% Sell:17%)"));
        assertFalse(Mt5LogTailer.shouldForwardToUi("ENTRY DEBUG: FXSSI Signal=SELL BlockActive=true | Filter=BOTH"));
        assertFalse(Mt5LogTailer.shouldForwardToUi("ENTRY DEBUG: ADR Check - DayRange:0.00262 NeededRange:0.00314 ADR:0.00523   NICHT ERFUELLT"));
        assertFalse(Mt5LogTailer.shouldForwardToUi("Core 01: history synchronized"));
        assertFalse(Mt5LogTailer.shouldForwardToUi("172 Mb memory used including 11 Mb of history data"));
    }

    @Test
    public void testShouldForwardNullAndEmptyGracefully() {
        assertFalse(Mt5LogTailer.shouldForwardToUi(null));
        assertFalse(Mt5LogTailer.shouldForwardToUi(""));
    }
}
