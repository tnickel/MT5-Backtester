package com.backtester.ui.javafx;

import com.backtester.config.EaParameter;
import com.backtester.ui.javafx.OptimizerSettingsHighlightDialog.ChangeHighlights;
import com.backtester.workflow.CustomProject;
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
        assertEquals(5, names.size());
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

    @Test
    public void withoutAdoptedBasisNoYellowOrOrange() {
        CustomProject project = chainProject();
        WorkflowTask stage2 = project.getTasks().get(2);
        Set<String> green = OptimizerSettingsHighlightDialog.resolveHighlightNames(stage2, null);
        ChangeHighlights changes = OptimizerSettingsHighlightDialog.resolveChangeHighlights(
                project, stage2, green);
        assertTrue(changes.latest().isEmpty());
        assertTrue(changes.prior().isEmpty());
    }

    @Test
    public void newestAdoptionIsYellowOlderStagesAreOrange() {
        CustomProject project = chainProject();
        WorkflowTask stage1 = project.getTasks().get(0);
        WorkflowTask stage2 = project.getTasks().get(2);
        WorkflowTask stage3 = project.getTasks().get(4);

        stage2.setOptimizerParameterBasisAdopted(true);
        Set<String> green2 = OptimizerSettingsHighlightDialog.resolveHighlightNames(stage2, null);
        ChangeHighlights afterFirst = OptimizerSettingsHighlightDialog.resolveChangeHighlights(
                project, stage2, green2);
        assertTrue(afterFirst.latest().contains("GridStep"));
        assertTrue(afterFirst.latest().contains("StepMultiplier"));
        assertTrue(afterFirst.prior().isEmpty());
        assertFalse(afterFirst.latest().contains("EnvelopePeriod"));

        stage3.setOptimizerParameterBasisAdopted(true);
        Set<String> green3 = OptimizerSettingsHighlightDialog.resolveHighlightNames(stage3, null);
        ChangeHighlights afterSecond = OptimizerSettingsHighlightDialog.resolveChangeHighlights(
                project, stage3, green3);
        assertTrue(afterSecond.latest().contains("EnvelopePeriod"));
        assertTrue(afterSecond.latest().contains("EnvelopeDeviation"));
        assertTrue(afterSecond.prior().contains("GridStep"));
        assertTrue(afterSecond.prior().contains("StepMultiplier"));
        assertFalse(afterSecond.latest().contains("GridStep"));
        assertFalse(afterSecond.prior().contains("ADXPeriod"));
        assertFalse(afterSecond.latest().contains("ADXPeriod"));

        // Stage 1 targets must not collide with stage 1's own green when viewing stage1.
        stage1.setOptimizerParameterBasisAdopted(false);
        Set<String> green1 = OptimizerSettingsHighlightDialog.resolveHighlightNames(stage1, null);
        assertTrue(green1.contains("GridStep"));
        ChangeHighlights root = OptimizerSettingsHighlightDialog.resolveChangeHighlights(
                project, stage1, green1);
        assertTrue(root.latest().isEmpty());
        assertTrue(root.prior().isEmpty());
    }

    @Test
    public void forcedGateJoinsYellowUnlessAlreadyGreen() {
        CustomProject project = chainProject();
        WorkflowTask stage2 = project.getTasks().get(2);
        stage2.setOptimizerParameterBasisAdopted(true);
        stage2.recordAdoptedFilterGate("Inp_Use_ADX_Filter", "FILTER_ON_BETTER", "true",
                true, 1.0, 0.5, "forced");

        Set<String> green = OptimizerSettingsHighlightDialog.resolveHighlightNames(stage2, null);
        ChangeHighlights changes = OptimizerSettingsHighlightDialog.resolveChangeHighlights(
                project, stage2, green);
        assertTrue(changes.latest().contains("Inp_Use_ADX_Filter"));
    }

    private static CustomProject chainProject() {
        CustomProject project = new CustomProject("Guided", "EA.ex5", "AUDCAD", "M5");
        WorkflowTask stage1 = new WorkflowTask("Stage 1", WorkflowTask.TaskType.OPTIMIZER);
        stage1.setOptimizerTargetParameters(List.of("GridStep", "StepMultiplier"));
        WorkflowTask filter1 = new WorkflowTask("Filter 1", WorkflowTask.TaskType.PRE_FILTER);
        WorkflowTask stage2 = new WorkflowTask("Stage 2", WorkflowTask.TaskType.OPTIMIZER);
        stage2.setOptimizerTargetParameters(List.of("EnvelopePeriod", "EnvelopeDeviation"));
        WorkflowTask filter2 = new WorkflowTask("Filter 2", WorkflowTask.TaskType.PRE_FILTER);
        WorkflowTask stage3 = new WorkflowTask("Stage 3", WorkflowTask.TaskType.OPTIMIZER);
        stage3.setOptimizerTargetParameters(List.of("ADXPeriod"));
        project.addTask(stage1);
        project.addTask(filter1);
        project.addTask(stage2);
        project.addTask(filter2);
        project.addTask(stage3);
        return project;
    }

    private static EaParameter find(List<EaParameter> params, String name) {
        return params.stream().filter(p -> name.equals(p.getName())).findFirst().orElseThrow();
    }
}
