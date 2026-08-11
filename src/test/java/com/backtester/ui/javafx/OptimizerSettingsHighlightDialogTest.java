package com.backtester.ui.javafx;

import com.backtester.config.EaParameter;
import com.backtester.workflow.WorkflowTask;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OptimizerSettingsHighlightDialogTest {

    @Test
    public void guidedTaskNameResolvesStageTargetsEvenWhenStoredTargetsAreWrong() {
        WorkflowTask task = new WorkflowTask("04 Envelopes unten — Optimizer", WorkflowTask.TaskType.OPTIMIZER);
        // Stale/wrong stored targets from a later stage (the bug the user hit).
        task.setOptimizerTargetParameters(List.of(
                "Inp_Use_Adaptive_Spacing", "Inp_Use_Escalation_Block"));

        Set<String> names = OptimizerSettingsHighlightDialog.resolveHighlightNames(task, null);
        assertTrue(names.contains("Inp_Envelopes_Period_Lower"));
        assertTrue(names.contains("Inp_Envelopes_Deviation_Lower"));
        assertFalse(names.contains("Inp_Use_Adaptive_Spacing"));
        assertEquals(4, names.size());
    }

    @Test
    public void displayClearsStaleOptimizeFlagsFromLiveEa() {
        WorkflowTask task = new WorkflowTask("03 Envelopes oben — Optimizer", WorkflowTask.TaskType.OPTIMIZER);
        EaParameter adaptive = new EaParameter("Inp_Use_Adaptive_Spacing", "false");
        adaptive.setOptimizeEnabled(true);
        EaParameter period = new EaParameter("Inp_Envelopes_Period", "5");
        period.setOptimizeEnabled(false);

        List<EaParameter> display = OptimizerSettingsHighlightDialog.resolveDisplayParameters(
                task, List.of(adaptive, period));
        assertFalse(find(display, "Inp_Use_Adaptive_Spacing").isOptimizeEnabled());
        assertTrue(find(display, "Inp_Envelopes_Period").isOptimizeEnabled());
    }

    @Test
    public void nonOptimizerHasNoHighlights() {
        WorkflowTask task = new WorkflowTask("Filter", WorkflowTask.TaskType.PRE_FILTER);
        task.setOptimizerTargetParameters(List.of("Inp_Use_ADX_Filter"));
        assertTrue(OptimizerSettingsHighlightDialog.resolveHighlightNames(task, List.of()).isEmpty());
    }

    @Test
    public void displayPrefersSnapshotCopy() {
        WorkflowTask task = new WorkflowTask("Opt", WorkflowTask.TaskType.OPTIMIZER);
        EaParameter snap = new EaParameter("Foo", "1");
        task.setOptimizerParameterSnapshot(List.of(snap));
        EaParameter fallback = new EaParameter("Bar", "2");

        List<EaParameter> display = OptimizerSettingsHighlightDialog.resolveDisplayParameters(
                task, List.of(fallback));
        assertEquals(1, display.size());
        assertEquals("Foo", display.get(0).getName());
        display.get(0).setValue("changed");
        assertEquals("1", task.getOptimizerParameterSnapshot().get(0).getValue());
    }

    private static EaParameter find(List<EaParameter> params, String name) {
        return params.stream().filter(p -> name.equals(p.getName())).findFirst().orElseThrow();
    }
}