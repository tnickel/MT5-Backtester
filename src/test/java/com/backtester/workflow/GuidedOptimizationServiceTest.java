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

    @Test
    public void applyFilterGateRecommendationForcesOnIntoConsumerSnapshot() {
        CustomProject project = stagedProject();
        WorkflowTask producer = project.getTasks().get(0);
        producer.setOptimizerTargetParameters(List.of("Inp_Use_ADX_Filter", "Inp_ADX_Period"));
        producer.setTargetDatabank("stage1");

        WorkflowTask consumer = project.getTasks().get(3);
        consumer.setOptimizerTargetParameters(List.of("Inp_Use_RSI_Filter"));
        consumer.setOptimizerParameterBasisAdopted(true);
        consumer.setOptimizerParameterSnapshot(List.of(
                parameter("Inp_Use_ADX_Filter", "false", false),
                parameter("Inp_ADX_Period", "14", false),
                parameter("Inp_Use_RSI_Filter", "false", true)));

        List<CombinedPass> passes = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            passes.add(scoredGatePass(i + 1, 90 - i, "Inp_Use_ADX_Filter", "true"));
        }
        for (int i = 0; i < 6; i++) {
            passes.add(scoredGatePass(100 + i, 20 - i, "Inp_Use_ADX_Filter", "false"));
        }
        project.getDatabanks().put("stage1", passes);

        DatabankManager manager = new DatabankManager();
        manager.loadFromProject(project);

        GuidedOptimizationService.FilterGateForceResult result =
                GuidedOptimizationService.applyFilterGateRecommendation(
                        producer, consumer, "C:/missing", manager);

        assertTrue(result.isForced());
        assertEquals(Boolean.TRUE, result.getForcedOn());
        assertEquals("true", find(consumer.getOptimizerParameterSnapshot(), "Inp_Use_ADX_Filter").getValue());
        assertFalse(find(consumer.getOptimizerParameterSnapshot(), "Inp_Use_ADX_Filter").isOptimizeEnabled());
        assertTrue(consumer.isAdoptedFilterGateForced());
        assertEquals("Inp_Use_ADX_Filter", consumer.getAdoptedFilterGateParameter());
        assertEquals("true", consumer.getAdoptedFilterGateForcedValue());
    }

    @Test
    public void applyFilterGateRecommendationForcesAllStageTargetGates() {
        CustomProject project = stagedProject();
        WorkflowTask producer = project.getTasks().get(0);
        producer.setOptimizerTargetParameters(List.of(
                "Inp_Use_Vol_Filter", "Inp_Vol_ATR_Period", "Inp_Use_Correlation_Filter"));
        producer.setTargetDatabank("stage1");

        WorkflowTask consumer = project.getTasks().get(3);
        consumer.setOptimizerTargetParameters(List.of("Inp_Use_RSI_Filter"));
        consumer.setOptimizerParameterBasisAdopted(true);
        consumer.setOptimizerParameterSnapshot(List.of(
                parameter("Inp_Use_Vol_Filter", "false", false),
                parameter("Inp_Use_Correlation_Filter", "false", false),
                parameter("Inp_Use_RSI_Filter", "false", true)));

        List<CombinedPass> passes = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            CombinedPass on = scoredGatePass(i + 1, 90 - i, "Inp_Use_Vol_Filter", "true");
            on.getBacktestPass().setParameter("Inp_Use_Correlation_Filter", "true");
            passes.add(on);
        }
        for (int i = 0; i < 6; i++) {
            CombinedPass off = scoredGatePass(100 + i, 20 - i, "Inp_Use_Vol_Filter", "false");
            off.getBacktestPass().setParameter("Inp_Use_Correlation_Filter", "false");
            passes.add(off);
        }
        project.getDatabanks().put("stage1", passes);

        DatabankManager manager = new DatabankManager();
        manager.loadFromProject(project);

        GuidedOptimizationService.FilterGateForceResult result =
                GuidedOptimizationService.applyFilterGateRecommendation(
                        producer, consumer, "C:/missing", manager);

        assertTrue(result.isForced());
        assertEquals("true", find(consumer.getOptimizerParameterSnapshot(), "Inp_Use_Vol_Filter").getValue());
        assertEquals("true", find(consumer.getOptimizerParameterSnapshot(), "Inp_Use_Correlation_Filter").getValue());
        assertTrue(consumer.getAdoptedFilterGateParameter().contains("Inp_Use_Vol_Filter"));
        assertTrue(consumer.getAdoptedFilterGateParameter().contains("Inp_Use_Correlation_Filter"));
        assertEquals("MULTI_GATE", consumer.getAdoptedFilterGateVerdict());
        assertEquals("true, true", consumer.getAdoptedFilterGateForcedValue());
        assertEquals("MULTI_GATE", result.getVerdict());
    }

    @Test
    public void applyFilterGateRecommendationKeepsForcedVerdictWhenLaterGateUnclear() {
        CustomProject project = stagedProject();
        WorkflowTask producer = project.getTasks().get(0);
        producer.setOptimizerTargetParameters(List.of("Inp_Use_Vol_Filter", "Inp_Use_Correlation_Filter"));
        producer.setTargetDatabank("stage1");

        WorkflowTask consumer = project.getTasks().get(3);
        consumer.setOptimizerTargetParameters(List.of("Inp_Use_RSI_Filter"));
        consumer.setOptimizerParameterBasisAdopted(true);
        consumer.setOptimizerParameterSnapshot(List.of(
                parameter("Inp_Use_Vol_Filter", "false", false),
                parameter("Inp_Use_Correlation_Filter", "false", false),
                parameter("Inp_Use_RSI_Filter", "false", true)));

        List<CombinedPass> passes = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            CombinedPass on = scoredGatePass(i + 1, 90 - i, "Inp_Use_Vol_Filter", "true");
            // Correlation constant → no ON/OFF contrast → unclear / insufficient
            on.getBacktestPass().setParameter("Inp_Use_Correlation_Filter", "true");
            passes.add(on);
        }
        for (int i = 0; i < 6; i++) {
            CombinedPass off = scoredGatePass(100 + i, 20 - i, "Inp_Use_Vol_Filter", "false");
            off.getBacktestPass().setParameter("Inp_Use_Correlation_Filter", "true");
            passes.add(off);
        }
        project.getDatabanks().put("stage1", passes);
        DatabankManager manager = new DatabankManager();
        manager.loadFromProject(project);

        GuidedOptimizationService.FilterGateForceResult result =
                GuidedOptimizationService.applyFilterGateRecommendation(
                        producer, consumer, "C:/missing", manager);

        assertTrue(result.isForced());
        assertEquals("true", find(consumer.getOptimizerParameterSnapshot(), "Inp_Use_Vol_Filter").getValue());
        assertEquals("FILTER_ON_BETTER", consumer.getAdoptedFilterGateVerdict());
        assertEquals("Inp_Use_Vol_Filter", consumer.getAdoptedFilterGateParameter());
    }

    @Test
    public void selectGatesForAnalysisIgnoresReportOnlyUseGates() {
        WorkflowTask producer = new WorkflowTask("01 Grid", WorkflowTask.TaskType.OPTIMIZER);
        producer.setOptimizerTargetParameters(List.of("Inp_Grid_Step", "Inp_Step_Multiplier"));
        List<String> candidates = List.of("Inp_Use_ADX_Filter", "Inp_Use_RSI_Filter");
        assertTrue(FilterGateAnalysisService.selectGatesForAnalysis(producer, candidates).isEmpty());
    }

    @Test
    public void applyFilterGateRecommendationSoftFailsWhenGateMissingInSnapshot() {
        CustomProject project = stagedProject();
        WorkflowTask producer = project.getTasks().get(0);
        producer.setOptimizerTargetParameters(List.of("Inp_Use_ADX_Filter"));
        producer.setTargetDatabank("stage1");

        WorkflowTask consumer = project.getTasks().get(3);
        consumer.setOptimizerTargetParameters(List.of("Inp_Use_RSI_Filter"));
        consumer.setOptimizerParameterBasisAdopted(true);
        // Snapshot deliberately omits the producer gate.
        consumer.setOptimizerParameterSnapshot(List.of(
                parameter("Inp_Use_RSI_Filter", "false", true)));

        List<CombinedPass> passes = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            passes.add(scoredGatePass(i + 1, 90 - i, "Inp_Use_ADX_Filter", "true"));
        }
        for (int i = 0; i < 6; i++) {
            passes.add(scoredGatePass(100 + i, 20 - i, "Inp_Use_ADX_Filter", "false"));
        }
        project.getDatabanks().put("stage1", passes);
        DatabankManager manager = new DatabankManager();
        manager.loadFromProject(project);

        GuidedOptimizationService.FilterGateForceResult result =
                GuidedOptimizationService.applyFilterGateRecommendation(
                        producer, consumer, "C:/missing", manager);

        assertFalse(result.isForced());
        assertTrue(result.getNote().toLowerCase(java.util.Locale.ROOT).contains("nicht geschrieben")
                || result.getNote().toLowerCase(java.util.Locale.ROOT).contains("fehlt"));
    }

    @Test
    public void databankWipeResetsCompletedTaskStatusToPending() {
        CustomProject project = stagedProject();
        WorkflowTask stage1 = project.getTasks().get(0);
        WorkflowTask retest = new WorkflowTask("OOS", WorkflowTask.TaskType.RETESTER);
        retest.setSourceDatabank("stage2");
        retest.setTargetDatabank("oos");
        retest.setStatus(WorkflowTask.TaskStatus.COMPLETED);
        project.addTask(retest);
        stage1.setStatus(WorkflowTask.TaskStatus.COMPLETED);
        stage1.setTargetDatabank("stage1");

        int reset = GuidedOptimizationService.resetTaskStatusesAfterDatabankWipe(project, true, null);
        assertTrue(reset >= 2);
        assertEquals(WorkflowTask.TaskStatus.PENDING, stage1.getStatus());
        assertEquals(WorkflowTask.TaskStatus.PENDING, retest.getStatus());
    }

    @Test
    public void isFollowUpOptimizerIgnoresExistingAdoptionFlag() {
        CustomProject project = stagedProject();
        WorkflowTask next = project.getTasks().get(3);
        next.setOptimizerTargetParameters(List.of("EnvelopePeriod"));
        next.setOptimizerParameterBasisAdopted(true);
        assertTrue(GuidedOptimizationService.isFollowUpOptimizer(project, next));
        assertFalse(GuidedOptimizationService.requiresAdoptedBasis(project, next));
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

    private static CombinedPass scoredGatePass(int number, double score, String gate, String value) {
        Pass backtest = new Pass();
        backtest.setPassNumber(number);
        backtest.setProfit(score * 10);
        backtest.setTotalTrades(100);
        backtest.setDrawdownPercent(10);
        backtest.setParameter(gate, value);
        return new CombinedPass(backtest, null, score, 1.0, "test");
    }

    private static EaParameter find(List<EaParameter> parameters, String name) {
        return parameters.stream().filter(p -> name.equals(p.getName())).findFirst().orElseThrow();
    }
}
