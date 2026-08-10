package com.backtester.workflow;

import com.backtester.config.EaParameter;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class GuidedOptimizationServiceTest {

    @Test
    public void automaticSelectionUsesHighestScoreThenLowestPassNumber() {
        CombinedPass lowerScore = scoredPass(1, 79.9);
        CombinedPass tiedHigherPass = scoredPass(42, 80.0);
        CombinedPass tiedLowerPass = scoredPass(7, 80.0);

        assertSame(tiedLowerPass, GuidedOptimizationService.selectBestPass(
                List.of(tiedHigherPass, lowerScore, tiedLowerPass)).orElseThrow());
        assertSame(tiedLowerPass, GuidedOptimizationService.selectBestPass(
                List.of(tiedLowerPass, lowerScore, tiedHigherPass)).orElseThrow());
    }

    @Test
    public void automaticSelectionIgnoresNonFiniteScores() {
        CombinedPass finite = scoredPass(9, -5.0);

        assertSame(finite, GuidedOptimizationService.selectBestPass(Arrays.asList(
                scoredPass(1, Double.NaN),
                scoredPass(2, Double.POSITIVE_INFINITY),
                null,
                finite,
                scoredPass(3, Double.NEGATIVE_INFINITY))).orElseThrow());
        assertTrue(GuidedOptimizationService.selectBestPass(List.of(
                scoredPass(1, Double.NaN), scoredPass(2, Double.POSITIVE_INFINITY))).isEmpty());
        assertTrue(GuidedOptimizationService.selectBestPass(null).isEmpty());
    }

    @Test
    public void adoptsSelectedValuesAndEnablesOnlyNextStageTargets() {
        CustomProject project = stagedProject();
        WorkflowTask nextOptimizer = project.getTasks().get(3);
        nextOptimizer.setOptimizerTargetParameters(List.of("EnvelopePeriod", "EnvelopeDeviation"));

        EaParameter gridStep = parameter("GridStep", "15", true);
        EaParameter multiplier = parameter("StepMultiplier", "1.2", true);
        EaParameter period = parameter("EnvelopePeriod", "20", false);
        EaParameter deviation = parameter("EnvelopeDeviation", "0.1", false);
        EaParameter unrelated = parameter("ADX", "25", true);

        CombinedPass selected = pass(2380, Map.of(
                "GridStep", "23",
                "StepMultiplier", "1.1"));

        GuidedOptimizationService.AdoptionResult result = GuidedOptimizationService.adoptPassParameters(
                project, List.of(gridStep, multiplier, period, deviation, unrelated), selected, "stage1-picked");

        assertSame(nextOptimizer, result.getNextOptimizer());
        assertEquals(2380, result.getPassNumber());
        assertEquals(2, result.getAdoptedParameterCount());
        assertEquals("23", find(result.getParameters(), "GridStep").getValue());
        assertEquals("1.1", find(result.getParameters(), "StepMultiplier").getValue());
        assertFalse(find(result.getParameters(), "GridStep").isOptimizeEnabled());
        assertFalse(find(result.getParameters(), "StepMultiplier").isOptimizeEnabled());
        assertTrue(find(result.getParameters(), "EnvelopePeriod").isOptimizeEnabled());
        assertTrue(find(result.getParameters(), "EnvelopeDeviation").isOptimizeEnabled());
        assertFalse(find(result.getParameters(), "ADX").isOptimizeEnabled());

        List<EaParameter> persistedSnapshot = nextOptimizer.getOptimizerParameterSnapshot();
        assertEquals("23", find(persistedSnapshot, "GridStep").getValue());
        assertTrue(find(persistedSnapshot, "EnvelopePeriod").isOptimizeEnabled());
        assertTrue(nextOptimizer.isOptimizerParameterBasisAdopted());
        assertEquals(2380, nextOptimizer.getOptimizerParameterBasisPassNumber());
        assertEquals("stage1-picked", nextOptimizer.getOptimizerParameterBasisDatabank());

        // Caller-owned inputs stay untouched.
        assertEquals("15", gridStep.getValue());
        assertTrue(gridStep.isOptimizeEnabled());
    }

    @Test
    public void ignoresDisabledOptimizerAndUsesNextEnabledOne() {
        CustomProject project = stagedProject();
        project.getTasks().get(3).setEnabled(false);
        WorkflowTask enabled = new WorkflowTask("Stage 3", WorkflowTask.TaskType.OPTIMIZER);
        enabled.setTargetDatabank("stage3");
        project.addTask(enabled);

        assertSame(enabled, GuidedOptimizationService.findNextActiveOptimizer(project, "stage1-picked").orElseThrow());
    }

    @Test
    public void followUpOptimizerWaitsUntilBasisWasAdopted() {
        CustomProject project = stagedProject();
        WorkflowTask first = project.getTasks().get(0);
        WorkflowTask followUp = project.getTasks().get(3);
        first.setOptimizerTargetParameters(List.of("GridStep"));
        followUp.setOptimizerTargetParameters(List.of("EnvelopePeriod"));

        assertFalse(GuidedOptimizationService.requiresAdoptedBasis(project, first));
        assertTrue(GuidedOptimizationService.requiresAdoptedBasis(project, followUp));

        followUp.setOptimizerParameterBasisAdopted(true);
        assertFalse(GuidedOptimizationService.requiresAdoptedBasis(project, followUp));
    }

    @Test
    public void resetClearsStaleAdoptedBasisLineage() {
        CustomProject project = stagedProject();
        WorkflowTask followUp = project.getTasks().get(3);
        followUp.setOptimizerTargetParameters(List.of("EnvelopePeriod"));
        followUp.setOptimizerParameterBasisAdopted(true);
        followUp.setOptimizerParameterBasisPassNumber(2380);
        followUp.setOptimizerParameterBasisDatabank("stage1-picked");

        assertEquals(1, GuidedOptimizationService.clearAdoptedBasesForRestart(project));
        assertFalse(followUp.isOptimizerParameterBasisAdopted());
        assertEquals(-1, followUp.getOptimizerParameterBasisPassNumber());
        assertEquals("", followUp.getOptimizerParameterBasisDatabank());
        assertTrue(GuidedOptimizationService.requiresAdoptedBasis(project, followUp));
    }

    @Test
    public void rejectsStageWithoutExplicitTargets() {
        CustomProject project = stagedProject();
        try {
            GuidedOptimizationService.adoptPassParameters(project,
                    List.of(parameter("GridStep", "15", true)),
                    pass(1, Map.of("GridStep", "20")), "stage1-picked");
            fail("Expected missing target validation");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("keine Ziel-Parameter"));
        }
    }

    @Test
    public void rejectsPassFromDifferentExpert() {
        CustomProject project = stagedProject();
        project.getTasks().get(3).setOptimizerTargetParameters(List.of("GridStep"));
        try {
            GuidedOptimizationService.adoptPassParameters(project,
                    List.of(parameter("GridStep", "15", true)),
                    pass(1, Map.of("UnknownParameter", "7")), "stage1-picked");
            fail("Expected EA mismatch validation");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("passen nicht zusammen"));
        }
    }

    @Test
    public void taskSnapshotIsDeepCopied() {
        WorkflowTask task = new WorkflowTask("Stage 2", WorkflowTask.TaskType.OPTIMIZER);
        EaParameter original = parameter("GridStep", "20", false);
        task.setOptimizerParameterSnapshot(List.of(original));
        task.setOptimizerParameterBasisAdopted(true);
        task.setOptimizerParameterBasisPassNumber(42);
        task.setOptimizerParameterBasisDatabank("stage1-picked");
        original.setValue("99");

        List<EaParameter> firstRead = task.getOptimizerParameterSnapshot();
        assertEquals("20", firstRead.get(0).getValue());
        firstRead.get(0).setValue("77");
        assertEquals("20", task.getOptimizerParameterSnapshot().get(0).getValue());
        assertEquals("20", task.copyForPersistence().getOptimizerParameterSnapshot().get(0).getValue());

        String json = new com.google.gson.Gson().toJson(task.copyForPersistence());
        WorkflowTask restored = new com.google.gson.Gson().fromJson(json, WorkflowTask.class);
        assertEquals("20", restored.getOptimizerParameterSnapshot().get(0).getValue());
        assertTrue(restored.isOptimizerParameterBasisAdopted());
        assertEquals(42, restored.getOptimizerParameterBasisPassNumber());
        assertEquals("stage1-picked", restored.getOptimizerParameterBasisDatabank());
    }

    @Test
    public void reconstructedPassValuesDoNotOverwriteNextStageRanges() {
        CustomProject project = stagedProject();
        WorkflowTask nextOptimizer = project.getTasks().get(3);
        nextOptimizer.setOptimizerTargetParameters(List.of("EnvelopePeriod"));

        EaParameter gridTemplate = parameter("GridStep", "999", false);
        EaParameter periodTemplate = parameter("EnvelopePeriod", "20", false);
        periodTemplate.setOptimizeStart("10");
        periodTemplate.setOptimizeStep("5");
        periodTemplate.setOptimizeEnd("50");

        EaParameter resolvedGrid = parameter("GridStep", "23", false);
        EaParameter resolvedPeriod = parameter("EnvelopePeriod", "18", false);
        GuidedOptimizationService.AdoptionResult result = GuidedOptimizationService.adoptPassParameters(
                project,
                List.of(gridTemplate, periodTemplate),
                List.of(resolvedGrid, resolvedPeriod),
                pass(2380, Map.of("GridStep", "23")),
                "stage1-picked");

        EaParameter period = find(result.getParameters(), "EnvelopePeriod");
        assertEquals("18", period.getValue());
        assertEquals("10", period.getOptimizeStart());
        assertEquals("5", period.getOptimizeStep());
        assertEquals("50", period.getOptimizeEnd());
        assertTrue(period.isOptimizeEnabled());
    }

    @Test
    public void rejectsTargetWithoutOptimizationRange() {
        CustomProject project = stagedProject();
        project.getTasks().get(3).setOptimizerTargetParameters(List.of("EnvelopePeriod"));
        EaParameter target = new EaParameter("EnvelopePeriod", "18");

        try {
            GuidedOptimizationService.adoptPassParameters(project,
                    List.of(target), pass(1, Map.of("EnvelopePeriod", "18")), "stage1-picked");
            fail("Expected invalid range validation");
        } catch (IllegalArgumentException ex) {
            assertTrue(ex.getMessage().contains("Suchraum"));
        }
    }

    private static CustomProject stagedProject() {
        CustomProject project = new CustomProject("Guided", "EA.ex5", "AUDCAD", "M5");
        WorkflowTask stage1 = new WorkflowTask("Stage 1", WorkflowTask.TaskType.OPTIMIZER);
        stage1.setTargetDatabank("stage1");
        WorkflowTask filter = new WorkflowTask("Hand candidates", WorkflowTask.TaskType.PRE_FILTER);
        filter.setSourceDatabank("stage1");
        filter.setTargetDatabank("stage1-picked");
        WorkflowTask disabled = new WorkflowTask("Disabled", WorkflowTask.TaskType.OPTIMIZER);
        disabled.setEnabled(false);
        WorkflowTask stage2 = new WorkflowTask("Stage 2", WorkflowTask.TaskType.OPTIMIZER);
        stage2.setTargetDatabank("stage2");
        project.addTask(stage1);
        project.addTask(filter);
        project.addTask(disabled);
        project.addTask(stage2);
        return project;
    }

    private static EaParameter parameter(String name, String value, boolean optimize) {
        EaParameter parameter = new EaParameter(name, value);
        parameter.setOptimizeStart(value);
        parameter.setOptimizeStep("1");
        parameter.setOptimizeEnd(value);
        parameter.setOptimizeEnabled(optimize);
        return parameter;
    }

    private static CombinedPass pass(int number, Map<String, String> values) {
        Pass backtest = new Pass();
        backtest.setPassNumber(number);
        backtest.setParameterValues(new LinkedHashMap<>(values));
        return new CombinedPass(backtest, null, 0.0, 0.0, "test");
    }

    private static CombinedPass scoredPass(int number, double score) {
        Pass backtest = new Pass();
        backtest.setPassNumber(number);
        return new CombinedPass(backtest, null, score, 0.0, "test");
    }

    private static EaParameter find(List<EaParameter> parameters, String name) {
        return parameters.stream().filter(p -> name.equals(p.getName())).findFirst().orElseThrow();
    }
}
