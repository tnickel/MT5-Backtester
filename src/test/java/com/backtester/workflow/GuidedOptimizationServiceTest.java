package com.backtester.workflow;

import com.backtester.config.EaParameter;
import com.backtester.engine.BacktestConfig;
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
    public void previewShowsOldAndNewValuesWithoutMutatingTask() {
        CustomProject project = stagedProject();
        WorkflowTask nextOptimizer = project.getTasks().get(3);
        nextOptimizer.setOptimizerTargetParameters(List.of("EnvelopePeriod", "EnvelopeDeviation"));

        EaParameter gridStep = parameter("GridStep", "15", true);
        EaParameter multiplier = parameter("StepMultiplier", "1.2", true);
        EaParameter period = parameter("EnvelopePeriod", "20", false);
        EaParameter deviation = parameter("EnvelopeDeviation", "0.1", false);

        CombinedPass selected = pass(99, Map.of(
                "GridStep", "23",
                "StepMultiplier", "1.1"));

        GuidedOptimizationService.AdoptionPreview preview = GuidedOptimizationService.previewPassAdoption(
                project, List.of(gridStep, multiplier, period, deviation), null, selected, "stage1-picked");

        assertEquals(99, preview.getPassNumber());
        assertSame(nextOptimizer, preview.getNextOptimizer());
        assertFalse(nextOptimizer.isOptimizerParameterBasisAdopted());
        assertTrue(nextOptimizer.getOptimizerParameterSnapshot().isEmpty());

        assertEquals(2, preview.getValueChanges().size());
        GuidedOptimizationService.ParameterValueChange grid = preview.getValueChanges().stream()
                .filter(c -> "GridStep".equals(c.getName())).findFirst().orElseThrow();
        assertEquals("15", grid.getOldValue());
        assertEquals("23", grid.getNewValue());
        GuidedOptimizationService.ParameterValueChange step = preview.getValueChanges().stream()
                .filter(c -> "StepMultiplier".equals(c.getName())).findFirst().orElseThrow();
        assertEquals("1.2", step.getOldValue());
        assertEquals("1.1", step.getNewValue());
        assertEquals(0, preview.getOtherBasisChangeCount());
    }

    @Test
    public void previewListsOnlyPassParamsNotFullPresetNoise() {
        CustomProject project = stagedProject();
        WorkflowTask stage1 = project.getTasks().get(0);
        stage1.setOptimizerTargetParameters(List.of("GridStep", "StepMultiplier"));
        WorkflowTask nextOptimizer = project.getTasks().get(3);
        nextOptimizer.setOptimizerTargetParameters(List.of("EnvelopePeriod"));

        EaParameter gridStep = parameter("GridStep", "15", true);
        EaParameter multiplier = parameter("StepMultiplier", "1.2", true);
        EaParameter period = parameter("EnvelopePeriod", "20", false);
        EaParameter adx = parameter("ADX", "25", false);

        // Full reconstructed preset also rewrites ADX — that must not dominate the dialog.
        EaParameter resolvedGrid = parameter("GridStep", "23", false);
        EaParameter resolvedMult = parameter("StepMultiplier", "1.1", false);
        EaParameter resolvedAdx = parameter("ADX", "40", false);

        CombinedPass selected = pass(7, Map.of(
                "GridStep", "23",
                "StepMultiplier", "1.1"));

        GuidedOptimizationService.AdoptionPreview preview = GuidedOptimizationService.previewPassAdoption(
                project,
                List.of(gridStep, multiplier, period, adx),
                List.of(resolvedGrid, resolvedMult, resolvedAdx, period),
                selected,
                "stage1-picked");

        assertEquals(2, preview.getPassValueChanges().size());
        assertTrue(preview.getPassValueChanges().stream().anyMatch(c -> "GridStep".equals(c.getName())));
        assertTrue(preview.getPassValueChanges().stream().anyMatch(c -> "StepMultiplier".equals(c.getName())));
        assertFalse(preview.getPassValueChanges().stream().anyMatch(c -> "ADX".equals(c.getName())));
        assertEquals(1, preview.getOtherBasisChangeCount());
        assertEquals("ADX", preview.getOtherBasisValueChanges().get(0).getName());
        assertEquals("25", preview.getOtherBasisValueChanges().get(0).getOldValue());
        assertEquals("40", preview.getOtherBasisValueChanges().get(0).getNewValue());
    }

    @Test
    public void previewKeepsOptimizedParametersThePassLeftUnchanged() {
        CustomProject project = stagedProject();
        WorkflowTask stage1 = project.getTasks().get(0);
        stage1.setOptimizerTargetParameters(List.of("GridStep", "StepMultiplier"));
        WorkflowTask nextOptimizer = project.getTasks().get(3);
        nextOptimizer.setOptimizerTargetParameters(List.of("EnvelopePeriod"));

        EaParameter gridStep = parameter("GridStep", "15", true);
        EaParameter multiplier = parameter("StepMultiplier", "1.2", true);
        EaParameter period = parameter("EnvelopePeriod", "20", false);

        // The optimizer kept StepMultiplier where it was — that is a result, not a gap.
        CombinedPass selected = pass(11, Map.of(
                "GridStep", "23",
                "StepMultiplier", "1.2"));

        GuidedOptimizationService.AdoptionPreview preview = GuidedOptimizationService.previewPassAdoption(
                project, List.of(gridStep, multiplier, period), null, selected, "stage1-picked");

        assertEquals("Stage 1", preview.getProducerStageName());
        assertEquals(List.of("GridStep", "StepMultiplier"),
                preview.getPassParameters().stream()
                        .map(GuidedOptimizationService.ParameterValueChange::getName)
                        .collect(java.util.stream.Collectors.toList()));
        GuidedOptimizationService.ParameterValueChange kept = preview.getPassParameters().get(1);
        assertEquals("1.2", kept.getOldValue());
        assertEquals("1.2", kept.getNewValue());
        // Only the actual change stays in the diff list the hand-pick dialog shows.
        assertEquals(1, preview.getValueChanges().size());
        assertEquals("GridStep", preview.getValueChanges().get(0).getName());
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
    public void isAdoptedBasisPassMatchesDatabankAndPassNumber() {
        CustomProject project = stagedProject();
        WorkflowTask followUp = project.getTasks().get(3);
        followUp.setOptimizerParameterBasisAdopted(true);
        followUp.setOptimizerParameterBasisPassNumber(42);
        followUp.setOptimizerParameterBasisDatabank("stage1-picked");

        CombinedPass adopted = pass(42, Map.of("GridStep", "20"));
        CombinedPass other = pass(7, Map.of("GridStep", "10"));

        assertTrue(GuidedOptimizationService.isAdoptedBasisPass(project, "stage1-picked", adopted));
        assertTrue(GuidedOptimizationService.isAdoptedBasisPass(project, "STAGE1-PICKED", adopted));
        assertFalse(GuidedOptimizationService.isAdoptedBasisPass(project, "stage1-picked", other));
        assertFalse(GuidedOptimizationService.isAdoptedBasisPass(project, "other-db", adopted));
        assertEquals(List.of(followUp.getName()),
                GuidedOptimizationService.adoptedBasisConsumerNames(project, "stage1-picked", adopted));
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
    public void carryOverKeepsTheProvenValuesInsteadOfThePreSeededStageTemplate() {
        // The guided factory seeds every optimizer with the original preset, so a stage that
        // has not been adopted into yet still carries the values the chain started from.
        // Carrying the proven basis forward must overwrite those, otherwise a stage that
        // finds no improvement silently resets the master to its starting point.
        CustomProject project = stagedProject();
        WorkflowTask consumer = project.getTasks().get(3);
        consumer.setOptimizerTargetParameters(List.of("EnvelopePeriod"));

        EaParameter staleGrid = parameter("GridStep", "15", false);
        EaParameter stalePeriod = parameter("EnvelopePeriod", "20", false);
        stalePeriod.setOptimizeStart("10");
        stalePeriod.setOptimizeStep("5");
        stalePeriod.setOptimizeEnd("50");
        consumer.setOptimizerParameterSnapshot(List.of(staleGrid, stalePeriod));

        EaParameter provenGrid = parameter("GridStep", "23", false);
        EaParameter provenPeriod = parameter("EnvelopePeriod", "30", false);

        GuidedOptimizationService.AdoptionResult result =
                GuidedOptimizationService.carryBasisToNextOptimizer(
                        project, List.of(provenGrid, provenPeriod), "stage1-picked");

        assertEquals("23", find(result.getParameters(), "GridStep").getValue());
        EaParameter carriedPeriod = find(result.getParameters(), "EnvelopePeriod");
        assertEquals("30", carriedPeriod.getValue());
        // The stage keeps its own search band; only the values come from the proven master.
        assertEquals("5", carriedPeriod.getOptimizeStep());
        assertEquals("50", carriedPeriod.getOptimizeEnd());
        assertTrue(carriedPeriod.isOptimizeEnabled());
        assertEquals("23", find(consumer.getOptimizerParameterSnapshot(), "GridStep").getValue());
    }

    @Test
    public void aRollbackMustNotWriteTheMasterOverTheSnapshotBeforeCarryingIt() {
        // Documents why the rollback hands the proven basis to the carry instead of putting
        // it into the snapshot first: the carry takes the snapshot as its template, so the
        // master would supply the search bands as well. A measured basis carries the band
        // it was measured at — a single value — and the stage would come out of the
        // rollback searching that one point instead of its own range.
        CustomProject project = stagedProject();
        WorkflowTask consumer = project.getTasks().get(3);
        consumer.setOptimizerTargetParameters(List.of("EnvelopePeriod"));

        EaParameter stagePeriod = parameter("EnvelopePeriod", "20", false);
        stagePeriod.setOptimizeStart("10");
        stagePeriod.setOptimizeStep("5");
        stagePeriod.setOptimizeEnd("50");
        List<EaParameter> stageTemplate = List.of(
                parameter("GridStep", "15", false), stagePeriod);
        List<EaParameter> proven = List.of(
                parameter("GridStep", "23", false), parameter("EnvelopePeriod", "30", false));

        // The wrong order — what the rollback used to do.
        consumer.setOptimizerParameterSnapshot(proven);
        EaParameter collapsed = find(GuidedOptimizationService
                .carryBasisToNextOptimizer(project, proven, "stage1-picked")
                .getParameters(), "EnvelopePeriod");
        assertEquals("30", collapsed.getOptimizeStart());
        assertEquals("30", collapsed.getOptimizeEnd());

        // The order the rollback uses now: the stage's own template stays the template.
        consumer.setOptimizerParameterSnapshot(stageTemplate);
        EaParameter kept = find(GuidedOptimizationService
                .carryBasisToNextOptimizer(project, proven, "stage1-picked")
                .getParameters(), "EnvelopePeriod");
        assertEquals("30", kept.getValue());
        assertEquals("10", kept.getOptimizeStart());
        assertEquals("50", kept.getOptimizeEnd());
    }

    @Test
    public void aCarryOverReportsWhichParametersTheMasterAndTheStageDoNotShare() {
        // The overlay can only transfer values whose name exists on both sides. What is left
        // over comes from the stage template instead of the confirmed master, so the stage
        // runs on a mixture that was never measured — that must not happen quietly.
        CustomProject project = stagedProject();
        WorkflowTask consumer = project.getTasks().get(3);
        consumer.setOptimizerTargetParameters(List.of("EnvelopePeriod"));
        consumer.setOptimizerParameterSnapshot(List.of(
                parameter("EnvelopePeriod", "20", false),
                parameter("NewStageOnlyFilter", "1", false)));
        List<EaParameter> proven = List.of(
                parameter("EnvelopePeriod", "30", false),
                parameter("RetiredMasterOnlySwitch", "7", false));

        GuidedOptimizationService.BasisSchemaDrift drift = GuidedOptimizationService
                .carryBasisToNextOptimizer(project, proven, "stage1-picked").getSchemaDrift();

        assertFalse(drift.isEmpty());
        assertEquals(List.of("RetiredMasterOnlySwitch"), drift.getMissingInStage());
        assertEquals(List.of("NewStageOnlyFilter"), drift.getMissingInBasis());
        String message = drift.describe();
        assertTrue(message.contains("NewStageOnlyFilter"));
        assertTrue(message.contains("RetiredMasterOnlySwitch"));
        assertTrue(message.contains("Vorlage der Stufe"));
    }

    @Test
    public void aCarryOverWithAMatchingSchemaReportsNothingAtAll() {
        // The normal case has to stay silent, otherwise the warning accompanies every
        // hand-off and stops being read.
        CustomProject project = stagedProject();
        WorkflowTask consumer = project.getTasks().get(3);
        consumer.setOptimizerTargetParameters(List.of("EnvelopePeriod"));
        consumer.setOptimizerParameterSnapshot(List.of(
                parameter("GridStep", "15", false), parameter("EnvelopePeriod", "20", false)));
        List<EaParameter> proven = List.of(
                parameter("GridStep", "23", false), parameter("EnvelopePeriod", "30", false));

        GuidedOptimizationService.BasisSchemaDrift drift = GuidedOptimizationService
                .carryBasisToNextOptimizer(project, proven, "stage1-picked").getSchemaDrift();

        assertTrue(drift.isEmpty());
        assertEquals("", drift.describe());
    }

    @Test
    public void aNormalAdoptionIsNotReportedAsDriftBecauseItsPresetIsPartialByDesign() {
        // A pass preset covers the parameters the run varied, not the whole basis. Treating
        // that as drift would raise the warning on every single adoption.
        CustomProject project = stagedProject();
        WorkflowTask consumer = project.getTasks().get(3);
        consumer.setOptimizerTargetParameters(List.of("EnvelopePeriod"));
        consumer.setOptimizerParameterSnapshot(List.of(
                parameter("GridStep", "15", false), parameter("EnvelopePeriod", "20", false)));

        GuidedOptimizationService.AdoptionResult result = GuidedOptimizationService.adoptPassParameters(
                project, alignmentTemplate(), List.of(parameter("GridStep", "23", false)),
                pass(7, Map.of("GridStep", "23")), "stage1-picked");

        assertTrue(result.getSchemaDrift().isEmpty());
    }

    @Test
    public void carryOverPreviewAgreesWithWhatTheCarryThenWrites() {
        CustomProject project = stagedProject();
        WorkflowTask consumer = project.getTasks().get(3);
        consumer.setOptimizerTargetParameters(List.of("EnvelopePeriod"));
        consumer.setOptimizerParameterSnapshot(List.of(
                parameter("GridStep", "15", false), alignmentTemplate().get(1)));
        List<EaParameter> proven = List.of(
                parameter("GridStep", "23", false), parameter("EnvelopePeriod", "30", false));

        GuidedOptimizationService.AdoptionPreview preview =
                GuidedOptimizationService.previewBasisCarryOver(project, proven, "stage1-picked");
        GuidedOptimizationService.AdoptionResult result =
                GuidedOptimizationService.carryBasisToNextOptimizer(project, proven, "stage1-picked");

        assertEquals(preview.getAdoptedParameterCount(), result.getAdoptedParameterCount());
        assertEquals(preview.getSearchSpaceAdjustments().size(),
                result.getSearchSpaceAdjustments().size());
    }

    @Test
    public void anAdoptionDoesNotTouchABasisThatWasReadBeforehand() {
        // The automatic chain remembers the proven master before adopting, so it can put it
        // back when the reference measurement does not confirm the pick. That only works as
        // long as the adoption cannot reach the parameter objects handed out earlier — the
        // stage snapshot is exactly the template the adoption builds on.
        CustomProject project = stagedProject();
        WorkflowTask consumer = project.getTasks().get(3);
        consumer.setOptimizerTargetParameters(List.of("EnvelopePeriod"));

        EaParameter gridStep = parameter("GridStep", "15", false);
        EaParameter period = parameter("EnvelopePeriod", "20", false);
        period.setOptimizeStart("10");
        period.setOptimizeStep("5");
        period.setOptimizeEnd("50");
        consumer.setOptimizerParameterSnapshot(List.of(gridStep, period));

        List<EaParameter> provenBasis = consumer.getOptimizerParameterSnapshot();

        GuidedOptimizationService.adoptPassParameters(project, List.of(gridStep, period), null,
                pass(2380, Map.of("GridStep", "23")), "stage1-picked");

        // Guard: without a real change the rest of the test would prove nothing.
        List<EaParameter> adopted = consumer.getOptimizerParameterSnapshot();
        assertEquals("23", find(adopted, "GridStep").getValue());
        assertTrue(find(adopted, "EnvelopePeriod").isOptimizeEnabled());

        assertEquals("15", find(provenBasis, "GridStep").getValue());
        assertFalse(find(provenBasis, "GridStep").isOptimizeEnabled());
        EaParameter rememberedPeriod = find(provenBasis, "EnvelopePeriod");
        assertEquals("20", rememberedPeriod.getValue());
        assertFalse(rememberedPeriod.isOptimizeEnabled());
        assertEquals("10", rememberedPeriod.getOptimizeStart());
        assertEquals("5", rememberedPeriod.getOptimizeStep());
        assertEquals("50", rememberedPeriod.getOptimizeEnd());
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
        // The resolved preset's own band (18/1/18) must not replace the stage range; step and
        // stop stay untouched. Start drops by less than one step so 18 is a pass MT5 walks.
        assertEquals("8", period.getOptimizeStart());
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
    public void forcedGateIsIncludedInReferenceParametersAndMeasurementSignature() {
        CustomProject project = stagedProject();
        WorkflowTask producer = project.getTasks().get(0);
        producer.setOptimizerTargetParameters(List.of("Inp_Use_ADX_Filter", "Inp_ADX_Period"));
        producer.setTargetDatabank("stage1");
        WorkflowTask consumer = project.getTasks().get(3);
        consumer.setOptimizerTargetParameters(List.of("EnvelopePeriod"));

        EaParameter gate = parameter("Inp_Use_ADX_Filter", "false", false);
        EaParameter adx = parameter("Inp_ADX_Period", "14", false);
        EaParameter period = parameter("EnvelopePeriod", "20", false);
        period.setOptimizeStart("5");
        period.setOptimizeStep("5");
        period.setOptimizeEnd("40");
        List<EaParameter> template = List.of(gate, adx, period);
        CombinedPass selected = pass(7, Map.of("Inp_Use_ADX_Filter", "false", "Inp_ADX_Period", "21"));

        GuidedOptimizationService.AdoptionPreview preview = GuidedOptimizationService.previewPassAdoption(
                project, template, null, selected, "stage1-picked");
        GuidedOptimizationService.AdoptionResult result = GuidedOptimizationService.adoptPassParameters(
                project, template, null, selected, "stage1-picked");

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

        assertTrue(GuidedOptimizationService.applyFilterGateRecommendation(
                producer, consumer, "C:/missing", manager).isForced());

        // The adoption's own copy predates the gate force, so it must not be what gets
        // measured — the consumer snapshot is the strategy the next optimizer runs.
        List<EaParameter> effectiveBasis = consumer.getOptimizerParameterSnapshot();
        assertEquals("false", find(result.getParameters(), "Inp_Use_ADX_Filter").getValue());
        assertEquals("true", find(effectiveBasis, "Inp_Use_ADX_Filter").getValue());

        BacktestConfig config = MasterStrategyLineageService.buildReferenceConfig(project, "");
        assertNotEquals(
                MasterStrategyLineageService.measurementSignature(config, result.getParameters()),
                MasterStrategyLineageService.measurementSignature(config, effectiveBasis));

        MasterStrategyLineageService.AdoptionSummary summary =
                MasterStrategyLineageService.summarize(preview, consumer, effectiveBasis);
        MasterStrategyEntry.ParameterChange gateChange = summary.getOptimizedParameters().stream()
                .filter(change -> "Inp_Use_ADX_Filter".equals(change.getName()))
                .findFirst().orElseThrow();
        assertEquals("true", gateChange.getNewValue());
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

    @Test
    public void adoptionWidensTheNextStageBandSoTheAdoptedValueStaysReachable() {
        CustomProject project = stagedProject();
        WorkflowTask nextOptimizer = project.getTasks().get(3);
        nextOptimizer.setOptimizerTargetParameters(List.of("EnvelopePeriod"));

        EaParameter gridStep = parameter("GridStep", "15", true);
        EaParameter period = parameter("EnvelopePeriod", "20", false);
        period.setOptimizeStart("3");
        period.setOptimizeStep("3");
        period.setOptimizeEnd("15");

        EaParameter inheritedPeriod = parameter("EnvelopePeriod", "5", false);

        GuidedOptimizationService.AdoptionResult result = GuidedOptimizationService.adoptPassParameters(
                project, List.of(gridStep, period), List.of(inheritedPeriod),
                pass(7, Map.of("GridStep", "23")), "stage1-picked");

        EaParameter adopted = find(result.getParameters(), "EnvelopePeriod");
        assertTrue(adopted.isOptimizeEnabled());
        assertEquals("5", adopted.getValue());
        assertEquals("2", adopted.getOptimizeStart());
        assertEquals("3", adopted.getOptimizeStep());
        assertEquals("15", adopted.getOptimizeEnd());

        assertEquals(1, result.getSearchSpaceAdjustments().size());
        assertEquals(ChampionSearchSpaceAligner.Outcome.GRID_SHIFTED,
                result.getSearchSpaceAdjustments().get(0).getOutcome());
    }

    @Test
    public void staleDownstreamDatabanksStartAtTheConsumerAndSpareEverythingAbove() {
        CustomProject project = stagedProject();
        WorkflowTask consumer = project.getTasks().get(3);
        WorkflowTask retest = new WorkflowTask("OOS", WorkflowTask.TaskType.RETESTER);
        retest.setSourceDatabank("stage2");
        retest.setTargetDatabank("oos");
        project.addTask(retest);

        List<String> stale = GuidedOptimizationService.listStaleDownstreamDatabanks(
                project, consumer, "stage1-picked");

        assertEquals(List.of("stage2", "oos"), stale);
    }

    @Test
    public void staleDownstreamListNeverContainsTheDatabankThePickCameFrom() {
        CustomProject project = stagedProject();
        WorkflowTask consumer = project.getTasks().get(3);
        consumer.setTargetDatabank("stage1-picked");

        assertTrue(GuidedOptimizationService.listStaleDownstreamDatabanks(
                project, consumer, "stage1-picked").isEmpty());
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

    @Test
    public void profitPerDrawdownIsDerivedFromTheRecoveryFactorWhenNoDrawdownColumnExists() {
        CombinedPass pass = ratioPass(1, 70.0, 4000, 8.0, 2000, 4.0);

        // BT drawdown 500, FW drawdown 500 → 6000 profit against the larger drawdown.
        assertEquals(12.0, GuidedOptimizationService.estimatedReturnToDrawdown(pass).getAsDouble(), 1e-9);
    }

    @Test
    public void drawdownsOfBothSegmentsAreNotAddedUp() {
        CombinedPass pass = ratioPass(1, 70.0, 4000, 4.0, 2000, 10.0);

        // BT drawdown 1000, FW drawdown 200 → the larger one counts.
        assertEquals(6.0, GuidedOptimizationService.estimatedReturnToDrawdown(pass).getAsDouble(), 1e-9);
    }

    @Test
    public void aPassWithoutAnyDrawdownInformationHasNoRatio() {
        Pass backtest = new Pass();
        backtest.setPassNumber(1);
        backtest.setProfit(4000);

        assertTrue(GuidedOptimizationService.estimatedReturnToDrawdown(
                new CombinedPass(backtest, null, 70.0, 0.0, "test")).isEmpty());
        assertTrue(GuidedOptimizationService.estimatedReturnToDrawdown(null).isEmpty());
    }

    @Test
    public void insideTheScoreShortlistTheBestProfitPerDrawdownWins() {
        CombinedPass scoreLeader = ratioPass(10, 80.0, 3000, 6.0, 1000, 6.0);   // ratio 8.0
        CombinedPass runnerUp = ratioPass(11, 79.0, 4000, 8.0, 2000, 8.0);      // ratio 12.0

        GuidedOptimizationService.AdoptionChoice choice = GuidedOptimizationService.chooseAdoptionPass(
                List.of(scoreLeader, runnerUp), 10, Double.NaN);

        assertSame(runnerUp, choice.getSelected().orElseThrow());
        assertEquals(12.0, choice.getSelectedRatio(), 1e-9);
        assertSame(scoreLeader, choice.getScoreLeader());
        assertTrue(choice.getNote().contains("übergangen"));
    }

    @Test
    public void aBetterRatioOutsideTheShortlistDoesNotWin() {
        CombinedPass first = ratioPass(1, 80.0, 3000, 6.0, 1000, 6.0);          // ratio 8.0
        CombinedPass second = ratioPass(2, 79.0, 3000, 6.0, 1000, 6.0);         // ratio 8.0
        CombinedPass farBehind = ratioPass(3, 10.0, 9000, 90.0, 1000, 90.0);    // ratio 100.0

        GuidedOptimizationService.AdoptionChoice choice = GuidedOptimizationService.chooseAdoptionPass(
                List.of(first, second, farBehind), 2, Double.NaN);

        assertSame(first, choice.getSelected().orElseThrow());
        assertEquals(2, choice.getShortlistSize());
    }

    @Test
    public void nothingIsAdoptedWhenNoCandidateReachesTheCurrentMasterBasis() {
        CombinedPass weaker = ratioPass(67, 69.2, 4000, 5.0, 2000, 4.0);        // ratio 7.5

        GuidedOptimizationService.AdoptionChoice choice = GuidedOptimizationService.chooseAdoptionPass(
                List.of(weaker), 10, 11.79);

        assertTrue(choice.getSelected().isEmpty());
        assertEquals(7.5, choice.getBestAvailableRatio(), 1e-9);
        assertTrue(choice.getNote().contains("Master-Basis"));
    }

    @Test
    public void theBlockedPassStaysNameableEvenThoughItIsNotAdopted() {
        CombinedPass weaker = ratioPass(67, 69.2, 4000, 5.0, 2000, 4.0);        // ratio 7.5

        GuidedOptimizationService.AdoptionChoice choice = GuidedOptimizationService.chooseAdoptionPass(
                List.of(weaker), 10, 11.79);

        // Reachable so the log can say which pass was rejected — not so it can be adopted.
        assertTrue(choice.isBlockedByMasterFloor());
        assertSame(weaker, choice.getBestAvailable().orElseThrow());
        assertEquals(7.5, choice.getSelectedRatio(), 1e-9);
    }

    @Test
    public void automaticFallbackWithoutRatioIsMarkedAsUnchecked() {
        // No drawdown anywhere: the score decides, but an existing master floor stays
        // unverified and must not be reported as met.
        GuidedOptimizationService.AdoptionChoice unverified = GuidedOptimizationService.chooseAdoptionPass(
                List.of(scoredPass(4, 60.0), scoredPass(3, 80.0)), 10, 11.79);
        assertEquals(3, unverified.getSelected().orElseThrow().getPassNumber());
        assertTrue(unverified.isMasterFloorUnverified());
        assertFalse(unverified.isBlockedByMasterFloor());
        assertTrue(unverified.getNote().contains("nicht überprüfbar"));

        // Without a master basis there is no floor to verify in the first place.
        GuidedOptimizationService.AdoptionChoice noFloor = GuidedOptimizationService.chooseAdoptionPass(
                List.of(scoredPass(3, 80.0)), 10, Double.NaN);
        assertFalse(noFloor.isMasterFloorUnverified());

        // A comparable pass above the floor is checked, not merely unblocked.
        GuidedOptimizationService.AdoptionChoice checked = GuidedOptimizationService.chooseAdoptionPass(
                List.of(ratioPass(5, 70.0, 4000, 8.0, 2000, 8.0)), 10, 5.0);
        assertFalse(checked.isMasterFloorUnverified());
    }

    @Test
    public void automaticMultiStageRegressionDoesNotSilentlyResetBestEverAuditFloor() {
        CustomProject project = stagedProject();
        project.setMasterSelectionRatio(11.79);
        WorkflowTask consumer = project.getTasks().get(3);
        consumer.setOptimizerTargetParameters(List.of("EnvelopePeriod"));

        EaParameter grid = parameter("GridStep", "23", false);
        EaParameter period = parameter("EnvelopePeriod", "20", false);
        period.setOptimizeStart("5");
        period.setOptimizeStep("5");
        period.setOptimizeEnd("40");
        List<EaParameter> basis = List.of(grid, period);

        // The stage's best pass stays below the floor, so nothing of it is adopted.
        GuidedOptimizationService.AdoptionChoice choice = GuidedOptimizationService.chooseAdoptionPass(
                List.of(ratioPass(67, 69.2, 4000, 5.0, 2000, 4.0)), 10, 11.79);
        assertTrue(choice.isBlockedByMasterFloor());

        GuidedOptimizationService.AdoptionResult result =
                GuidedOptimizationService.carryBasisToNextOptimizer(project, basis, "stage1-picked");

        assertSame(consumer, result.getNextOptimizer());
        assertEquals(GuidedOptimizationService.CARRIED_BASIS_PASS_NUMBER, result.getPassNumber());
        assertEquals(0, result.getAdoptedParameterCount());
        // Values are handed on untouched; only this stage's own target is opened.
        assertEquals("23", find(result.getParameters(), "GridStep").getValue());
        assertEquals("20", find(result.getParameters(), "EnvelopePeriod").getValue());
        assertFalse(find(result.getParameters(), "GridStep").isOptimizeEnabled());
        assertTrue(find(result.getParameters(), "EnvelopePeriod").isOptimizeEnabled());
        assertTrue(consumer.isOptimizerParameterBasisAdopted());
        // The floor belongs to the still-current basis and must survive the rejected stage.
        assertEquals(11.79, project.getMasterSelectionRatio(), 1e-9);
    }

    @Test
    public void theCarriedBasisReportsTheProducerTargetsAsUnchanged() {
        CustomProject project = stagedProject();
        WorkflowTask producer = project.getTasks().get(0);
        producer.setOptimizerTargetParameters(List.of("GridStep"));
        WorkflowTask consumer = project.getTasks().get(3);
        consumer.setOptimizerTargetParameters(List.of("EnvelopePeriod"));

        EaParameter grid = parameter("GridStep", "23", false);
        EaParameter period = parameter("EnvelopePeriod", "20", false);
        period.setOptimizeStart("5");
        period.setOptimizeStep("5");
        period.setOptimizeEnd("40");

        GuidedOptimizationService.AdoptionPreview preview = GuidedOptimizationService
                .previewBasisCarryOver(project, List.of(grid, period), "stage1-picked");

        assertEquals("Stage 1", preview.getProducerStageName());
        assertTrue(preview.getPassValueChanges().isEmpty());
        assertTrue(preview.getOtherBasisValueChanges().isEmpty());
        assertEquals(1, preview.getPassParameters().size());
        assertEquals("GridStep", preview.getPassParameters().get(0).getName());
        assertEquals("23", preview.getPassParameters().get(0).getOldValue());
        assertEquals("23", preview.getPassParameters().get(0).getNewValue());
    }

    @Test
    public void previewAndAdoptionProduceIdenticalAlignedParametersAndAdjustments() {
        // Production runs both against the same project, one after the other: whatever the
        // confirmation dialog showed has to be what the adoption then writes.
        CustomProject project = stagedProject();
        project.getTasks().get(3).setOptimizerTargetParameters(List.of("EnvelopePeriod"));
        CombinedPass selected = pass(2380, Map.of("GridStep", "23"));

        GuidedOptimizationService.AdoptionPreview preview = GuidedOptimizationService.previewPassAdoption(
                project, alignmentTemplate(), null, selected, "stage1-picked");
        GuidedOptimizationService.AdoptionResult result = GuidedOptimizationService.adoptPassParameters(
                project, alignmentTemplate(), null, selected, "stage1-picked");

        assertEquals(preview.getAdoptedParameterCount(), result.getAdoptedParameterCount());
        assertEquals(preview.getSearchSpaceAdjustments().size(),
                result.getSearchSpaceAdjustments().size());
        for (int i = 0; i < preview.getSearchSpaceAdjustments().size(); i++) {
            assertEquals(preview.getSearchSpaceAdjustments().get(i).describe(),
                    result.getSearchSpaceAdjustments().get(i).describe());
        }
        for (GuidedOptimizationService.ParameterValueChange change : preview.getPassValueChanges()) {
            assertEquals(change.getNewValue(),
                    find(result.getParameters(), change.getName()).getValue());
        }
        // The band the preview announced is the band the stage will walk.
        EaParameter period = find(result.getParameters(), "EnvelopePeriod");
        assertTrue(period.isOptimizeEnabled());
        assertEquals("8", period.getOptimizeStart());
        assertEquals("5", period.getOptimizeStep());
        assertEquals("50", period.getOptimizeEnd());
    }

    private static List<EaParameter> alignmentTemplate() {
        EaParameter grid = parameter("GridStep", "15", false);
        EaParameter period = parameter("EnvelopePeriod", "20", false);
        // 18 is not on the 10/5/50 grid, so the aligner has to move the band.
        period.setValue("18");
        period.setOptimizeStart("10");
        period.setOptimizeStep("5");
        period.setOptimizeEnd("50");
        return List.of(grid, period);
    }

    @Test
    public void anAcceptedPassIsAlsoTheBestAvailableOne() {
        CombinedPass better = ratioPass(5, 70.0, 4000, 8.0, 2000, 8.0);         // ratio 12.0

        GuidedOptimizationService.AdoptionChoice choice = GuidedOptimizationService.chooseAdoptionPass(
                List.of(better), 10, 5.0);

        assertFalse(choice.isBlockedByMasterFloor());
        assertSame(better, choice.getBestAvailable().orElseThrow());
    }

    @Test
    public void withoutAnyRatedCandidateThereIsNothingToFallBackTo() {
        GuidedOptimizationService.AdoptionChoice choice =
                GuidedOptimizationService.chooseAdoptionPass(List.of(), 10, 5.0);

        assertTrue(choice.getBestAvailable().isEmpty());
        assertFalse(choice.isBlockedByMasterFloor());
    }

    @Test
    public void matchingTheCurrentMasterBasisIsStillAccepted() {
        CombinedPass equal = ratioPass(5, 70.0, 4000, 8.0, 2000, 8.0);          // ratio 12.0

        assertSame(equal, GuidedOptimizationService.chooseAdoptionPass(
                List.of(equal), 10, 12.0).getSelected().orElseThrow());
    }

    @Test
    public void withoutUsableDrawdownsTheScoreStaysTheDecidingCriterion() {
        GuidedOptimizationService.AdoptionChoice choice = GuidedOptimizationService.chooseAdoptionPass(
                List.of(scoredPass(4, 60.0), scoredPass(3, 80.0)), 10, 5.0);

        assertEquals(3, choice.getSelected().orElseThrow().getPassNumber());
        assertTrue(choice.getNote().contains("Score"));
    }

    @Test
    public void anEmptyCandidateListYieldsNoChoiceAndNoRatio() {
        GuidedOptimizationService.AdoptionChoice choice =
                GuidedOptimizationService.chooseAdoptionPass(List.of(), 10, 5.0);

        assertTrue(choice.getSelected().isEmpty());
        assertTrue(Double.isNaN(choice.getBestAvailableRatio()));
    }

    private static CombinedPass ratioPass(int number, double score,
                                          double backtestProfit, double backtestRecovery,
                                          double forwardProfit, double forwardRecovery) {
        Pass backtest = new Pass();
        backtest.setPassNumber(number);
        backtest.setProfit(backtestProfit);
        backtest.setRecoveryFactor(backtestRecovery);
        Pass forward = new Pass();
        forward.setPassNumber(number);
        forward.setProfit(forwardProfit);
        forward.setRecoveryFactor(forwardRecovery);
        return new CombinedPass(backtest, forward, score, 1.0, "test");
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
