package com.backtester.workflow;

import com.backtester.config.EaParameter;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class MasterSearchSpaceValidatorTest {

    @Test
    public void acceptsExactDecimalMasterGridPointWithoutMutatingSnapshot() {
        WorkflowTask task = optimizer("Envelopes oben",
                parameter("Inp_Envelopes_Deviation", "0.01", "0.01", "0.01", "1.70"));
        List<EaParameter> master = List.of(parameter("Inp_Envelopes_Deviation", "1.47",
                "0", "1", "2"));
        List<EaParameter> before = task.getOptimizerParameterSnapshot();

        assertTrue(MasterSearchSpaceValidator.validateTask(task, master, "M5").isEmpty());
        assertEquals(before.get(0).getOptimizeStart(),
                task.getOptimizerParameterSnapshot().get(0).getOptimizeStart());
        assertEquals(before.get(0).getOptimizeEnd(),
                task.getOptimizerParameterSnapshot().get(0).getOptimizeEnd());
    }

    @Test
    public void rejectsMasterOutsideBandBeforeMt5CanStart() {
        WorkflowTask task = optimizer("Envelopes oben",
                parameter("Inp_Envelopes_Deviation", "0.01", "0.005", "0.005", "0.030"));
        List<EaParameter> master = List.of(parameter("Inp_Envelopes_Deviation", "1.47",
                "0", "1", "2"));

        List<MasterSearchSpaceValidator.Issue> issues =
                MasterSearchSpaceValidator.validateTask(task, master, "M5");

        assertEquals(1, issues.size());
        assertTrue(issues.get(0).reason().contains("außerhalb"));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> MasterSearchSpaceValidator.requireTask(task, master, "M5"));
        assertTrue(error.getMessage().contains("MT5 wurde nicht gestartet"));
        assertTrue(error.getMessage().contains("1.47"));
    }

    @Test
    public void acceptsMasterBetweenGridPointsWhenInsideBounds() {
        WorkflowTask task = optimizer("Raster",
                parameter("Grid", "1.05", "1.00", "0.10", "2.00"));

        assertTrue(MasterSearchSpaceValidator.validateTask(
                task, List.of(parameter("Grid", "1.05", "0", "1", "2")), "M5").isEmpty());
    }

    @Test
    public void periodCurrentUsesProjectPeriodAsSemanticEquivalent() {
        WorkflowTask task = optimizer("Timeframe",
                parameter("Inp_ADX_Timeframe", "0", "1", "1", "16385"));

        assertTrue(MasterSearchSpaceValidator.validateTask(task,
                List.of(parameter("Inp_ADX_Timeframe", "0", "0", "1", "1")), "M5").isEmpty());
        assertFalse(MasterSearchSpaceValidator.validateTask(task,
                List.of(parameter("Inp_ADX_Timeframe", "0", "0", "1", "1")), "UNKNOWN").isEmpty());
    }

    @Test
    public void validatesBooleanCoverageAndAppliedPriceDomain() {
        WorkflowTask gate = optimizer("Gate",
                parameter("Inp_Use_ADX_Filter", "false", "false", "1", "true"));
        assertTrue(MasterSearchSpaceValidator.validateTask(gate,
                List.of(parameter("Inp_Use_ADX_Filter", "1", "0", "1", "1")), "M5").isEmpty());

        WorkflowTask fixedFalse = optimizer("Gate",
                parameter("Inp_Use_ADX_Filter", "false", "false", "1", "false"));
        assertFalse(MasterSearchSpaceValidator.validateTask(fixedFalse,
                List.of(parameter("Inp_Use_ADX_Filter", "true", "0", "1", "1")), "M5").isEmpty());

        WorkflowTask price = optimizer("Price",
                parameter("Envelopes_Price", "1", "0", "1", "7"));
        List<MasterSearchSpaceValidator.Issue> priceIssues = MasterSearchSpaceValidator.validateTask(
                price, List.of(parameter("Envelopes_Price", "1", "0", "1", "7")), "M5");
        assertEquals(1, priceIssues.size());
        assertTrue(priceIssues.get(0).reason().contains("Enum-Domain"));
    }

    @Test
    public void projectCheckAggregatesEnabledGuidedOptimizersOnly() {
        EaParameter masterParameter = parameter("Grid", "10", "0", "1", "20");
        WorkflowTask good = optimizer("Good", parameter("Grid", "10", "0", "5", "20"));
        WorkflowTask bad = optimizer("Bad", parameter("Grid", "10", "0", "5", "8"));
        WorkflowTask disabled = optimizer("Disabled", parameter("Grid", "10", "0", "6", "20"));
        disabled.setEnabled(false);
        WorkflowTask retester = new WorkflowTask("Retest", WorkflowTask.TaskType.RETESTER);

        List<MasterSearchSpaceValidator.Issue> issues = MasterSearchSpaceValidator.validateProject(
                List.of(good, bad, disabled, retester), List.of(masterParameter), "M5");

        assertEquals(1, issues.size());
        assertEquals("Bad", issues.get(0).taskName());
    }

    @Test
    public void runtimeCheckRejectsDriftInFixedParametersBeforeCheckingTargetCoverage() {
        WorkflowTask task = optimizer("Runtime",
                parameter("Grid", "10", "0", "5", "20"));
        List<EaParameter> snapshot = new ArrayList<>(task.getOptimizerParameterSnapshot());
        EaParameter fixed = parameter("FixedRisk", "2", "0", "1", "5");
        fixed.setOptimizeEnabled(false);
        snapshot.add(fixed);
        task.setOptimizerParameterSnapshot(snapshot);

        List<MasterSearchSpaceValidator.Issue> issues =
                MasterSearchSpaceValidator.validateRuntimeTask(task, List.of(
                        parameter("Grid", "10", "0", "1", "20"),
                        parameter("FixedRisk", "3", "0", "1", "5")), "M5");

        assertEquals(1, issues.size());
        assertEquals("FixedRisk", issues.get(0).parameterName());
        assertTrue(issues.get(0).reason().contains("weicht"));
    }

    @Test
    public void runtimeCheckReportsMissingParametersInBothDirections() {
        WorkflowTask task = optimizer("Runtime",
                parameter("Grid", "10", "0", "5", "20"));
        List<EaParameter> snapshot = new ArrayList<>(task.getOptimizerParameterSnapshot());
        EaParameter snapshotOnly = parameter("SnapshotOnly", "7", "0", "1", "10");
        snapshotOnly.setOptimizeEnabled(false);
        snapshot.add(snapshotOnly);
        task.setOptimizerParameterSnapshot(snapshot);

        List<MasterSearchSpaceValidator.Issue> issues =
                MasterSearchSpaceValidator.validateRuntimeTask(task, List.of(
                        parameter("Grid", "10", "0", "1", "20"),
                        parameter("ExpectedOnly", "8", "0", "1", "10")), "M5");

        assertEquals(2, issues.size());
        assertTrue(issues.stream().anyMatch(issue -> issue.parameterName().equals("ExpectedOnly")
                && issue.reason().contains("fehlt im")));
        assertTrue(issues.stream().anyMatch(issue -> issue.parameterName().equals("SnapshotOnly")
                && issue.reason().contains("nicht in der erwarteten")));
    }

    private static WorkflowTask optimizer(String name, EaParameter... parameters) {
        WorkflowTask task = new WorkflowTask(name, WorkflowTask.TaskType.OPTIMIZER);
        List<EaParameter> snapshot = new ArrayList<>();
        List<String> targets = new ArrayList<>();
        for (EaParameter parameter : parameters) {
            parameter.setOptimizeEnabled(true);
            snapshot.add(parameter);
            targets.add(parameter.getName());
        }
        task.setOptimizerParameterSnapshot(snapshot);
        task.setOptimizerTargetParameters(targets);
        return task;
    }

    private static EaParameter parameter(String name, String value,
                                         String start, String step, String end) {
        EaParameter parameter = new EaParameter(name, value);
        parameter.setOptimizeStart(start);
        parameter.setOptimizeStep(step);
        parameter.setOptimizeEnd(end);
        parameter.setStringType(false);
        return parameter;
    }
}
