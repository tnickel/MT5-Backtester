package com.backtester.engine;

import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.*;

/**
 * Sichert die Datums-Split-Logik ab, mit der die Sensitivitätsanalyse
 * (Schritt 4) MT5s Forward-Split spiegelt.
 *
 * <p>Anti-Curve-Fitting-Relevanz: Driftet dieser Split gegenüber MT5, wird
 * die Forward-Sensitivität auf dem falschen Zeitfenster gemessen — die
 * BT/FW-Robustheitsaussage wäre dann stillschweigend wertlos. Diese Tests
 * frieren die Semantik ein.
 */
public class ForwardSplitTest {

    private static final LocalDate FROM = LocalDate.of(2025, 1, 1);
    private static final LocalDate TO = LocalDate.of(2025, 12, 31); // 364 Tage Spanne

    // --- ForwardMode 0: kein Forward ---

    @Test
    public void testNoForward_FullRangeIsBacktest() {
        assertEquals(TO, ForwardSplit.computeBacktestEndDate(FROM, TO, 0, null));
        assertNull(ForwardSplit.computeForwardStartDate(FROM, TO, 0, null));
    }

    // --- ForwardMode 1: Forward = letzte Hälfte ---

    @Test
    public void testHalfSplit_BacktestEndsAtMidpoint() {
        LocalDate btEnd = ForwardSplit.computeBacktestEndDate(FROM, TO, 1, null);
        // 364 Tage / 2 = 182 → BT-Ende = 01.01. + 182 Tage
        assertEquals(FROM.plusDays(182), btEnd);
    }

    @Test
    public void testHalfSplit_ForwardStartsDayAfterBacktestEnd() {
        LocalDate btEnd = ForwardSplit.computeBacktestEndDate(FROM, TO, 1, null);
        LocalDate fwStart = ForwardSplit.computeForwardStartDate(FROM, TO, 1, null);
        assertNotNull(fwStart);
        assertEquals(btEnd.plusDays(1), fwStart);
    }

    // --- ForwardMode 2: Forward = letztes Drittel ---

    @Test
    public void testThirdSplit() {
        LocalDate btEnd = ForwardSplit.computeBacktestEndDate(FROM, TO, 2, null);
        // BT = 2/3 der Spanne: 364 * 2 / 3 = 242 (Integer-Division)
        assertEquals(FROM.plusDays(242), btEnd);
        assertEquals(btEnd.plusDays(1), ForwardSplit.computeForwardStartDate(FROM, TO, 2, null));
    }

    // --- ForwardMode 3: Forward = letztes Viertel ---

    @Test
    public void testQuarterSplit() {
        LocalDate btEnd = ForwardSplit.computeBacktestEndDate(FROM, TO, 3, null);
        // BT = 3/4 der Spanne: 364 * 3 / 4 = 273
        assertEquals(FROM.plusDays(273), btEnd);
        assertEquals(btEnd.plusDays(1), ForwardSplit.computeForwardStartDate(FROM, TO, 3, null));
    }

    // --- ForwardMode 4: Custom Datum ---

    @Test
    public void testCustomDate_ValidInsideRange() {
        LocalDate custom = LocalDate.of(2025, 10, 1);
        assertEquals(custom.minusDays(1), ForwardSplit.computeBacktestEndDate(FROM, TO, 4, custom));
        assertEquals(custom, ForwardSplit.computeForwardStartDate(FROM, TO, 4, custom));
    }

    @Test
    public void testCustomDate_OutsideRangeMeansNoForward() {
        LocalDate before = FROM.minusDays(10);
        assertEquals(TO, ForwardSplit.computeBacktestEndDate(FROM, TO, 4, before));
        assertNull(ForwardSplit.computeForwardStartDate(FROM, TO, 4, before));

        LocalDate after = TO.plusDays(10);
        assertEquals(TO, ForwardSplit.computeBacktestEndDate(FROM, TO, 4, after));
        assertNull(ForwardSplit.computeForwardStartDate(FROM, TO, 4, after));
    }

    @Test
    public void testCustomDate_NullMeansNoForward() {
        assertEquals(TO, ForwardSplit.computeBacktestEndDate(FROM, TO, 4, null));
        assertNull(ForwardSplit.computeForwardStartDate(FROM, TO, 4, null));
    }

    // --- Invarianten über alle Modi ---

    @Test
    public void testWindowsNeverOverlap() {
        // BT-Fenster und FW-Fenster dürfen sich in keinem Modus überlappen —
        // sonst wäre die "Out-of-Sample"-Sensitivität teilweise in-sample.
        for (int mode = 1; mode <= 3; mode++) {
            LocalDate btEnd = ForwardSplit.computeBacktestEndDate(FROM, TO, mode, null);
            LocalDate fwStart = ForwardSplit.computeForwardStartDate(FROM, TO, mode, null);
            assertNotNull("Mode " + mode + " muss ein FW-Fenster liefern", fwStart);
            assertTrue("Mode " + mode + ": FW-Start muss nach BT-Ende liegen", fwStart.isAfter(btEnd));
            assertTrue("Mode " + mode + ": FW-Start darf toDate nicht überschreiten", !fwStart.isAfter(TO));
        }
    }

    @Test
    public void testDegenerateRange_NoForward() {
        // Spanne von 1 Tag: kein sinnvoller Split möglich
        LocalDate to = FROM.plusDays(1);
        assertEquals(to, ForwardSplit.computeBacktestEndDate(FROM, to, 1, null));
        assertNull(ForwardSplit.computeForwardStartDate(FROM, to, 1, null));
    }

    @Test
    public void testNullDates_NoForward() {
        assertNull(ForwardSplit.computeForwardStartDate(null, TO, 1, null));
        assertNull(ForwardSplit.computeForwardStartDate(FROM, null, 1, null));
        assertEquals(TO, ForwardSplit.computeBacktestEndDate(null, TO, 1, null));
    }
}
