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
                assertEquals(4, task.getOptimizerForwardMode());
                assertEquals(ToTheMoon132GuidedWorkflowFactory.FORWARD_FROM, task.getOptimizerForwardDate());
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
    public void upgradesLegacyGuidedProjectWithoutDiscardingG11Results() {
        CustomProject project = ToTheMoon132GuidedWorkflowFactory.create(
                "Guided", completeSyntheticPreset(), null);
        WorkflowTask top20 = project.getTasks().stream()
                .filter(task -> ToTheMoon132GuidedWorkflowFactory.DEVELOPMENT_TOP20_DATABANK
                        .equals(task.getTargetDatabank()))
                .findFirst().orElseThrow();
        project.getTasks().remove(top20);
        WorkflowTask developmentRetest = project.getTasks().stream()
                .filter(task -> "g12_dev_tick".equals(task.getTargetDatabank()))
                .findFirst().orElseThrow();
        developmentRetest.setName("12 Development-Retest — Every Tick (3 Jahre)");
        developmentRetest.setSourceDatabank("g11_safety_pick");
        developmentRetest.setStatus(WorkflowTask.TaskStatus.FAILED);
        project.getDatabanks().put("g11_safety_pick", new ArrayList<>());
        project.getDatabanks().put("g12_dev_tick", new ArrayList<>());
        StrategyBacktestArchive archive = new StrategyBacktestArchive("1|Strat 1", "Strat 1", 1);
        StrategyBacktestRun obsoleteDevelopmentRun = new StrategyBacktestRun();
        obsoleteDevelopmentRun.setTabName("g12_dev_tick");
        archive.upsert(obsoleteDevelopmentRun);
        StrategyBacktestRun retainedUpstreamRun = new StrategyBacktestRun();
        retainedUpstreamRun.setTabName("g11_safety_pick");
        archive.upsert(retainedUpstreamRun);
        project.getStrategyArchives().put(archive.getStrategyKey(), archive);

        assertTrue(ToTheMoon132GuidedWorkflowFactory.ensureDevelopmentTop20Selection(project));
        assertFalse(ToTheMoon132GuidedWorkflowFactory.ensureDevelopmentTop20Selection(project));

        assertEquals(28, project.getTasks().size());
        assertEquals("g11_safety_pick", project.getTasks().get(23).getSourceDatabank());
        assertEquals(ToTheMoon132GuidedWorkflowFactory.DEVELOPMENT_TOP20_DATABANK,
                developmentRetest.getSourceDatabank());
        assertEquals(WorkflowTask.TaskStatus.PENDING, developmentRetest.getStatus());
        assertTrue(project.getDatabanks().containsKey("g11_safety_pick"));
        assertNull(project.getStrategyArchives().get(archive.getStrategyKey()).getRun("g12_dev_tick"));
        assertNotNull(project.getStrategyArchives().get(archive.getStrategyKey()).getRun("g11_safety_pick"));
    }

    @Test
    public void repairsWrongStageSearchSpaceOnGridFundament() {
        List<EaParameter> preset = completeSyntheticPreset();
        CustomProject project = ToTheMoon132GuidedWorkflowFactory.create(
                "Guided", preset, Path.of("build", "guided-reports"));

        WorkflowTask grid = project.getTasks().stream()
                .filter(task -> task.getName().startsWith("01 Grid-Fundament"))
                .findFirst().orElseThrow();
        WorkflowTask safety = project.getTasks().stream()
                .filter(task -> task.getName().startsWith("11 Adaptive"))
                .findFirst().orElseThrow();

        // Corrupt stage 01 to use stage 11 search space (the bug seen in production).
        grid.setOptimizerTargetParameters(safety.getOptimizerTargetParameters());
        grid.setOptimizerParameterSnapshot(safety.getOptimizerParameterSnapshot());
        grid.setStatus(WorkflowTask.TaskStatus.COMPLETED);
        project.getDatabanks().put("g01_grid_raw", new ArrayList<>(List.of()));

        assertTrue(ToTheMoon132GuidedWorkflowFactory.repairStageOptimizerSearchSpaces(project));
        assertFalse(ToTheMoon132GuidedWorkflowFactory.repairStageOptimizerSearchSpaces(project));

        assertEquals(List.of("Inp_Grid_Step", "Inp_Step_Multiplier", "Inp_Next_Lot_Multiplier"),
                grid.getOptimizerTargetParameters());
        Set<String> enabled = new HashSet<>();
        for (EaParameter parameter : grid.getOptimizerParameterSnapshot()) {
            if (parameter.isOptimizeEnabled()) enabled.add(parameter.getName());
        }
        assertEquals(Set.of("Inp_Grid_Step", "Inp_Step_Multiplier", "Inp_Next_Lot_Multiplier"), enabled);
        assertEquals("550", find(grid, "Inp_Grid_Step").getOptimizeStart());
        assertEquals("900", find(grid, "Inp_Grid_Step").getOptimizeEnd());
        assertEquals(WorkflowTask.TaskStatus.PENDING, grid.getStatus());
        assertTrue(project.getDatabanks().get("g01_grid_raw").isEmpty());
    }

    @Test
    public void repairsEmptyPersistedSnapshotsFromDiskPreset() {
        // Simulate the production Guided 4Y project: stage names present, snapshots empty.
        CustomProject project = new CustomProject("Guided", "ToTheMoon_KI_v132", "AUDCAD", "M5");
        WorkflowTask grid = new WorkflowTask("01 Grid-Fundament — Optimizer", WorkflowTask.TaskType.OPTIMIZER);
        grid.setTargetDatabank("g01_grid_raw");
        project.addTask(grid);

        assertTrue(ToTheMoon132GuidedWorkflowFactory.repairStageOptimizerSearchSpaces(project));
        assertEquals(List.of("Inp_Grid_Step", "Inp_Step_Multiplier", "Inp_Next_Lot_Multiplier"),
                grid.getOptimizerTargetParameters());
        assertFalse(grid.getOptimizerParameterSnapshot().isEmpty());
        Set<String> enabled = new HashSet<>();
        for (EaParameter parameter : grid.getOptimizerParameterSnapshot()) {
            if (parameter.isOptimizeEnabled()) enabled.add(parameter.getName());
        }
        assertEquals(Set.of("Inp_Grid_Step", "Inp_Step_Multiplier", "Inp_Next_Lot_Multiplier"), enabled);
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

    private static EaParameter find(WorkflowTask task, String name) {
        return task.getOptimizerParameterSnapshot().stream()
                .filter(parameter -> name.equals(parameter.getName())).findFirst().orElseThrow();
    }
}
