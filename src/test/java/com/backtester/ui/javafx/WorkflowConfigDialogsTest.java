package com.backtester.ui.javafx;

import com.backtester.workflow.WorkflowTask;
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

    @Test
    public void appliesDedicatedDiversityTaskSettings() {
        WorkflowTask task = new WorkflowTask("Cluster", WorkflowTask.TaskType.DIVERSITY_FILTER);

        WorkflowConfigDialogs.applyDiversityTaskSettings(
                task, "Langzeit-Clustering", "Langzeit-Retest", "Langzeit-Cluster",
                "22,5", "31", "4", "17");

        assertEquals("Langzeit-Clustering", task.getName());
        assertEquals("Langzeit-Retest", task.getSourceDatabank());
        assertEquals("Langzeit-Cluster", task.getTargetDatabank());
        assertEquals(0.225, task.getDiversityParamDiffPct(), 0.0);
        assertEquals(0.31, task.getDiversityTradeDiffPct(), 0.0);
        assertEquals(4, task.getDiversityMinDifferentParams());
        assertEquals(17, task.getDiversityMaxStrategies());
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
