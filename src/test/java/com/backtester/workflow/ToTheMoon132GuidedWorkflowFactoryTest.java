package com.backtester.workflow;

import com.backtester.config.EaParameter;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class ToTheMoon132GuidedWorkflowFactoryTest {

    @Test
    public void createsElevenGuidedStagesAndFourValidationTasks() {
        List<EaParameter> preset = completeSyntheticPreset();
        CustomProject project = ToTheMoon132GuidedWorkflowFactory.create(
                "Guided", preset, Path.of("build", "guided-reports"));

        assertEquals("ToTheMoon_KI_v132", project.getExpert());
        assertEquals("AUDCAD", project.getSymbol());
        assertEquals("M5", project.getPeriod());
        assertEquals(28, project.getTasks().size());
        assertEquals(WorkflowTask.TaskType.STRATEGY_SELECTION, project.getTasks().get(0).getType());

        Set<String> taskIds = new HashSet<>();
        Set<String> databankTargets = new HashSet<>();
        int optimizerCount = 0;
        for (WorkflowTask task : project.getTasks()) {
            assertTrue(taskIds.add(task.getId()));
            assertEquals(WorkflowTask.TaskStatus.PENDING, task.getStatus());
            if (task.getType() == WorkflowTask.TaskType.OPTIMIZER) {
                optimizerCount++;
                assertEquals("AUDCAD", task.getRetestSymbol());
                assertEquals("M5", task.getRetestPeriod());
                assertEquals(ToTheMoon132GuidedWorkflowFactory.DEVELOPMENT_FROM, task.getStartDate());
                assertEquals(ToTheMoon132GuidedWorkflowFactory.DEVELOPMENT_TO, task.getEndDate());
                assertEquals(1, task.getOptimizerForwardMode());
                assertEquals("2024-02-01", task.getOptimizerForwardDate());
                assertFalse(task.getOptimizerTargetParameters().isEmpty());
                assertFalse(task.getOptimizerParameterSnapshot().isEmpty());
                assertFalse(task.isOptimizerParameterBasisAdopted());

                Set<String> enabled = new HashSet<>();
                for (EaParameter parameter : task.getOptimizerParameterSnapshot()) {
                    if (parameter.isOptimizeEnabled()) enabled.add(parameter.getName());
                }
                assertEquals(new HashSet<>(task.getOptimizerTargetParameters()), enabled);
            }
            if (task.getType() != WorkflowTask.TaskType.STRATEGY_SELECTION) {
                assertTrue("Databank targets must stay unambiguous: " + task.getTargetDatabank(),
                        databankTargets.add(task.getTargetDatabank()));
            }
        }
        assertEquals(11, optimizerCount);
        WorkflowTask top20 = project.getTasks().stream()
                .filter(task -> ToTheMoon132GuidedWorkflowFactory.DEVELOPMENT_TOP20_DATABANK
                        .equals(task.getTargetDatabank()))
                .findFirst().orElseThrow();
        assertEquals(WorkflowTask.TaskType.DIVERSITY_FILTER, top20.getType());
        assertEquals("g11_safety_pick", top20.getSourceDatabank());
        assertTrue(top20.isDiversityRankByScore());
        assertTrue(top20.isDiversityDeduplicateEffectiveV132());
        assertTrue(top20.copyForPersistence().isDiversityDeduplicateEffectiveV132());
        assertEquals(WorkflowTask.DEFAULT_DIVERSITY_PARAM_DIFF_PCT,
                top20.getDiversityParamDiffPct(), 0.0);
        assertEquals(WorkflowTask.DEFAULT_DIVERSITY_TRADE_DIFF_PCT,
                top20.getDiversityTradeDiffPct(), 0.0);
        assertEquals(2, top20.getDiversityMinDifferentParams());
        assertEquals(WorkflowTask.DEFAULT_DIVERSITY_MAX_STRATEGIES,
                top20.getDiversityMaxStrategies());
        WorkflowTask developmentRetest = project.getTasks().stream()
                .filter(task -> "g12_dev_tick".equals(task.getTargetDatabank()))
                .findFirst().orElseThrow();
        assertEquals(ToTheMoon132GuidedWorkflowFactory.DEVELOPMENT_TOP20_DATABANK,
                developmentRetest.getSourceDatabank());
        assertEquals(DatabankManager.FINAL,
                project.getTasks().get(project.getTasks().size() - 1).getTargetDatabank());
        WorkflowConfigurationValidator.validateDatabankExecutionOrder(project.getTasks(), List.of(
                DatabankManager.RESULTS,
                DatabankManager.EXISTING_PORTFOLIO,
                DatabankManager.FINAL));
    }

    @Test
    public void oosIsTheOnlyFinalSelectionGateAndAllSuccessfulFourYearRunsArePublished() {
        CustomProject project = ToTheMoon132GuidedWorkflowFactory.create(
                "Guided", "GBPJPY", "M15", completeSyntheticPreset(), null);

        WorkflowTask oos = findByTarget(project, "g13_oos_tick");
        WorkflowTask fourYears = findByTarget(project, "g14_final_4y");
        WorkflowTask publication = findByTarget(project, DatabankManager.FINAL);

        assertEquals(WorkflowTask.TaskType.RETESTER, oos.getType());
        assertEquals("g12_dev_tick", oos.getSourceDatabank());
        assertEquals(4, oos.getFilterConditions().size());
        assertTrue(oos.getName().contains("Selektionsgate"));

        assertEquals(WorkflowTask.TaskType.RETESTER, fourYears.getType());
        assertEquals("g13_oos_tick", fourYears.getSourceDatabank());
        assertTrue(fourYears.getFilterConditions().isEmpty());
        assertTrue(fourYears.getName().contains("DD/Report"));
        assertEquals(ToTheMoon132GuidedWorkflowFactory.DEVELOPMENT_FROM, fourYears.getStartDate());
        assertEquals(ToTheMoon132GuidedWorkflowFactory.FINAL_TO, fourYears.getEndDate());
        assertEquals("GBPJPY", fourYears.getRetestSymbol());
        assertEquals("M15", fourYears.getRetestPeriod());

        assertEquals(WorkflowTask.TaskType.PRE_FILTER, publication.getType());
        assertEquals("g14_final_4y", publication.getSourceDatabank());
        assertTrue(publication.getFilterConditions().isEmpty());
        assertTrue(publication.isDeleteFailed());
        assertTrue(publication.getName().contains("alle erfolgreichen 4Y-Runs"));
        assertEquals("GBPJPY", publication.getRetestSymbol());
        assertEquals("M15", publication.getRetestPeriod());
    }

    @Test
    public void provenValuesRemainFixedOutsideCurrentStage() {
        List<EaParameter> preset = completeSyntheticPreset();
        EaParameter known = preset.stream()
                .filter(p -> "Inp_Grid_Step".equals(p.getName())).findFirst().orElseThrow();
        known.setValue("725");
        CustomProject project = ToTheMoon132GuidedWorkflowFactory.create("Guided", preset, null);

        WorkflowTask firstOptimizer = project.getTasks().get(1);
        WorkflowTask secondOptimizer = project.getTasks().get(3);
        assertTrue(find(firstOptimizer, "Inp_Grid_Step").isOptimizeEnabled());
        assertEquals("725", find(firstOptimizer, "Inp_Grid_Step").getValue());
        assertFalse(find(secondOptimizer, "Inp_Grid_Step").isOptimizeEnabled());
        assertEquals("725", find(secondOptimizer, "Inp_Grid_Step").getValue());
    }

    @Test
    public void trailingStageDoesNotMixMutuallyExclusiveExitBranches() {
        List<String> exitTargets = ToTheMoon132GuidedWorkflowFactory.stageTargetParameters()
                .get("10 Exit-Management");

        assertEquals(List.of("Inp_Trail_Start_Points", "Inp_Trail_Step_Points"), exitTargets);
        assertFalse(exitTargets.contains("Inp_Use_ATR_TP"));
        assertFalse(exitTargets.contains("Inp_Use_Midline_TP"));
        assertFalse(exitTargets.contains("Inp_TakeProfit"));
    }

    @Test
    public void indicatorStagesOptimizeEnumTimeframeBandThroughH1() {
        Map<String, List<String>> stages = ToTheMoon132GuidedWorkflowFactory.stageTargetParameters();
        assertTrue(stages.get("03 Envelopes oben").contains("TimeFrame_Envelopes"));
        assertTrue(stages.get("04 Envelopes unten").contains("TimeFrame_Envelopes_Lower"));
        assertTrue(stages.get("05 ADX-Regime").contains("Inp_ADX_Timeframe"));
        assertTrue(stages.get("06 ATR-Gridabstand").contains("Inp_ATR_Timeframe"));
        assertTrue(stages.get("07 Volatilität & Richtung").contains("Inp_Vol_ATR_Timeframe"));

        CustomProject project = ToTheMoon132GuidedWorkflowFactory.create(
                "Guided", completeSyntheticPreset(), null);

        assertTimeframeBand(findOptimizer(project, "03 Envelopes oben"), "TimeFrame_Envelopes");
        assertTimeframeBand(findOptimizer(project, "04 Envelopes unten"), "TimeFrame_Envelopes_Lower");
        assertTimeframeBand(findOptimizer(project, "05 ADX-Regime"), "Inp_ADX_Timeframe");
        assertTimeframeBand(findOptimizer(project, "06 ATR-Gridabstand"), "Inp_ATR_Timeframe");
        assertTimeframeBand(findOptimizer(project, "07 Volatilität & Richtung"), "Inp_Vol_ATR_Timeframe");
    }

    @Test
    public void envelopesUseV132PercentageDomainsAndGeneticSearch() {
        CustomProject project = ToTheMoon132GuidedWorkflowFactory.create(
                "Guided", completeSyntheticPreset(), null);
        WorkflowTask upper = findOptimizer(project, "03 Envelopes oben");
        WorkflowTask lower = findOptimizer(project, "04 Envelopes unten");

        assertBand(find(upper, "Inp_Envelopes_Deviation"), "0.01", "0.01", "1.70");
        assertBand(find(lower, "Inp_Envelopes_Deviation_Lower"), "0.01", "0.01", "2.00");
        assertBand(find(upper, "Envelopes_Price"), "1", "1", "7");
        assertBand(find(lower, "Envelopes_Price_Lower"), "1", "1", "7");
        assertEquals(2, upper.getOptimizerMode());
        assertEquals(2, lower.getOptimizerMode());
    }

    @Test
    public void shippedV132MasterIsReproducibleInEveryStageBeforeStart() {
        List<EaParameter> preset = ToTheMoon132GuidedWorkflowFactory.loadProvenPresetFromDisk(
                "ToTheMoon_KI_v132");
        assertFalse("Das ausgelieferte v132-Preset fehlt.", preset.isEmpty());
        CustomProject project = ToTheMoon132GuidedWorkflowFactory.create(
                "Guided", "GBPJPY", "M5", preset, null);

        List<MasterSearchSpaceValidator.Issue> issues = MasterSearchSpaceValidator.validateProject(
                project.getTasks(), preset, project.getPeriod());

        assertTrue(issues.stream().map(MasterSearchSpaceValidator.Issue::describe)
                .reduce("", (left, right) -> left + "\n" + right), issues.isEmpty());
    }

    @Test
    public void safetyStageOptimizesSessionFilterBooleanGate() {
        List<String> safety = ToTheMoon132GuidedWorkflowFactory.stageTargetParameters()
                .get("11 Adaptive & Safety-Gates");
        assertTrue(safety.contains("Inp_Use_Session_Filter"));
        assertTrue(safety.contains("Inp_Use_Escalation_Block"));

        CustomProject project = ToTheMoon132GuidedWorkflowFactory.create(
                "Guided", completeSyntheticPreset(), null);
        WorkflowTask optimizer = findOptimizer(project, "11 Adaptive & Safety-Gates");
        EaParameter session = find(optimizer, "Inp_Use_Session_Filter");
        assertTrue(session.isOptimizeEnabled());
        assertEquals("false", session.getOptimizeStart());
        assertEquals("1", session.getOptimizeStep());
        assertEquals("true", session.getOptimizeEnd());
    }

    private static List<EaParameter> completeSyntheticPreset() {
        List<EaParameter> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> stage
                : ToTheMoon132GuidedWorkflowFactory.stageTargetParameters().entrySet()) {
            for (String name : stage.getValue()) {
                if (result.stream().anyMatch(existing -> name.equals(existing.getName()))) continue;
                EaParameter parameter = new EaParameter(name, "1");
                parameter.setOptimizeStart("1");
                parameter.setOptimizeStep("1");
                parameter.setOptimizeEnd("2");
                parameter.setStringType(false);
                result.add(parameter);
            }
        }
        EaParameter fixed = new EaParameter("Inp_Use_RSI_Filter", "false");
        fixed.setStringType(false);
        fixed.setOptimizeStart("false");
        fixed.setOptimizeStep("1");
        fixed.setOptimizeEnd("true");
        result.add(fixed);
        return result;
    }

    private static WorkflowTask findOptimizer(CustomProject project, String stagePrefix) {
        return project.getTasks().stream()
                .filter(task -> task.getType() == WorkflowTask.TaskType.OPTIMIZER)
                .filter(task -> task.getName().startsWith(stagePrefix))
                .findFirst()
                .orElseThrow();
    }

    private static WorkflowTask findByTarget(CustomProject project, String targetDatabank) {
        return project.getTasks().stream()
                .filter(task -> targetDatabank.equals(task.getTargetDatabank()))
                .findFirst()
                .orElseThrow();
    }

    private static void assertTimeframeBand(WorkflowTask optimizer, String parameterName) {
        assertTrue(optimizer.getOptimizerTargetParameters().contains(parameterName));
        EaParameter parameter = find(optimizer, parameterName);
        assertTrue(parameter.isOptimizeEnabled());
        assertEquals("1", parameter.getOptimizeStart());
        assertEquals("1", parameter.getOptimizeStep());
        assertEquals("16385", parameter.getOptimizeEnd());
    }

    private static void assertBand(EaParameter parameter, String start, String step, String end) {
        assertEquals(start, parameter.getOptimizeStart());
        assertEquals(step, parameter.getOptimizeStep());
        assertEquals(end, parameter.getOptimizeEnd());
    }

    private static EaParameter find(WorkflowTask task, String name) {
        return task.getOptimizerParameterSnapshot().stream()
                .filter(parameter -> name.equals(parameter.getName())).findFirst().orElseThrow();
    }
}
