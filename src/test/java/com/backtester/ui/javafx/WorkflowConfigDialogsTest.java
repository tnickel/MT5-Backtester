package com.backtester.ui.javafx;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class WorkflowConfigDialogsTest {

    @Test
    public void testParseFiniteDecimalAcceptsGermanDecimalComma() {
        assertEquals(1.25,
                WorkflowConfigDialogs.parseFiniteDecimal("1,25", "Recovery", 0.0, 10.0),
                0.0);
    }

    @Test
    public void testParseFiniteDecimalRejectsNaNAndNegativeValues() {
        assertInvalidDecimal("NaN");
        assertInvalidDecimal("-1,0");
    }

    @Test
    public void testParsePositiveIntegerRejectsZeroAndNegativeValues() {
        assertInvalidInteger("0");
        assertInvalidInteger("-5");
        assertEquals(50, WorkflowConfigDialogs.parsePositiveInteger("50", "Trades"));
    }

    private static void assertInvalidDecimal(String value) {
        try {
            WorkflowConfigDialogs.parseFiniteDecimal(value, "Testwert", 0.0, 100.0);
            fail("Ungültiger Dezimalwert wurde akzeptiert: " + value);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void assertInvalidInteger(String value) {
        try {
            WorkflowConfigDialogs.parsePositiveInteger(value, "Trades");
            fail("Ungültige Trade-Anzahl wurde akzeptiert: " + value);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
