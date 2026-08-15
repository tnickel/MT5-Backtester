package com.backtester.workflow;

import com.backtester.config.EaParameter;
import com.backtester.engine.BacktestConfig;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class ToTheMoon132TickKillWorkflowFactoryTest {

    @Test
    public void searchStaysOnOhlcAndTickFunnelWidensOnlyAfterKills() {
        CustomProject project = ToTheMoon132TickKillWorkflowFactory.create(
                "Tick-Kill", "GBPJPY", "M5", completeSyntheticPreset(), null);

        assertEquals("ToTheMoon_KI_v132", project.getExpert());
        assertEquals("GBPJPY", project.getSymbol());
        assertEquals("M5", project.getPeriod());
        assertEquals(46, project.getTasks().size());

        int optimizerCount = 0;
        int masterReferenceCount = 0;
        Set<String> targets = new HashSet<>();
        for (WorkflowTask task : project.getTasks()) {
            if (task.getType() == WorkflowTask.TaskType.OPTIMIZER) {
                optimizerCount++;
                assertEquals(WorkflowTask.MODE_OHLC_M1, task.getExecutionMode());
                assertEquals(BacktestConfig.MODEL_OHLC_M1, task.getMt5Model());
                assertEquals(ToTheMoon132TickKillWorkflowFactory.DEVELOPMENT_FROM, task.getStartDate());
                assertEquals(ToTheMoon132TickKillWorkflowFactory.DEVELOPMENT_TO, task.getEndDate());
            }
            if (task.getType() == WorkflowTask.TaskType.MASTER_REFERENCE) {
                masterReferenceCount++;
                assertEquals(WorkflowTask.MODE_OHLC_M1, task.getExecutionMode());
                assertEquals(BacktestConfig.MODEL_OHLC_M1, task.getMt5Model());
                assertEquals(ToTheMoon132TickKillWorkflowFactory.DEVELOPMENT_FROM, task.getStartDate());
                assertEquals(ToTheMoon132TickKillWorkflowFactory.DEVELOPMENT_TO, task.getEndDate());
                assertTrue(task.getSourceDatabank().endsWith("_pick"));
                assertTrue(task.getTargetDatabank().endsWith("_master"));
                assertTrue(task.getName().contains("Master-Referenz"));
            }
            if (task.getType() != WorkflowTask.TaskType.STRATEGY_SELECTION) {
                assertTrue("Duplicate databank target: " + task.getTargetDatabank(),
                        targets.add(task.getTargetDatabank()));
            }
        }
        assertEquals(11, optimizerCount);
        assertEquals(11, masterReferenceCount);

        WorkflowTask shortlist = findByTarget(project, ToTheMoon132GuidedWorkflowFactory.GRID_SHORTLIST_DATABANK);
        assertEquals(ToTheMoon132GuidedWorkflowFactory.GRID_SHORTLIST_MAX_STRATEGIES,
                shortlist.getDiversityMaxStrategies());
        assertFalse(shortlist.isDiversityStampClusterIds());
        WorkflowTask tickGate = findByTarget(project, ToTheMoon132GuidedWorkflowFactory.GRID_TICK_DATABANK);
        assertEquals(WorkflowTask.MODE_EVERY_TICK, tickGate.getExecutionMode());
        assertTrue(tickGate.hasExplicitForwardSplit());

        WorkflowTask gridCluster = project.getTasks().stream()
                .filter(task -> task.getType() == WorkflowTask.TaskType.DIVERSITY_FILTER)
                .filter(task -> ToTheMoon132GuidedWorkflowFactory.GRID_CLUSTER_DATABANK
                        .equals(task.getTargetDatabank()))
                .findFirst().orElseThrow();
        assertEquals(ToTheMoon132GuidedWorkflowFactory.GRID_TICK_DATABANK, gridCluster.getSourceDatabank());
        assertEquals(ClusterIdentity.MAX_CLUSTERS, gridCluster.getDiversityMaxStrategies());
        assertFalse(gridCluster.getDiversityParameterSnapshot().isEmpty());
        assertTrue(gridCluster.getDiversityParameterSnapshot().stream()
                .anyMatch(p -> "Inp_Grid_Step".equals(p.getName()) && p.isOptimizeEnabled()));
        assertTrue(gridCluster.getDiversityParameterSnapshot().stream()
                .anyMatch(p -> "Inp_TakeProfit".equals(p.getName()) && p.isOptimizeEnabled()));

        int g01Index = project.getTasks().indexOf(gridCluster);
        WorkflowTask g02Optimizer = project.getTasks().stream()
                .filter(task -> task.getType() == WorkflowTask.TaskType.OPTIMIZER)
                .filter(task -> task.getName() != null && task.getName().startsWith("02 "))
                .findFirst().orElseThrow();
        assertTrue(g01Index < project.getTasks().indexOf(g02Optimizer));
        assertEquals(ToTheMoon132GuidedWorkflowFactory.GRID_CLUSTER_DATABANK, g02Optimizer.getSourceDatabank());

        WorkflowTask k12 = findByTarget(project, ToTheMoon132TickKillWorkflowFactory.TOP20_DATABANK);
        assertEquals(WorkflowTask.TaskType.DIVERSITY_FILTER, k12.getType());
        assertEquals(ClusterIdentity.MAX_CLUSTERS, k12.getDiversityMaxStrategies());
        assertEquals("g11_safety_pick", k12.getSourceDatabank());
        assertTrue(k12.getDiversityParameterSnapshot().stream()
                .anyMatch(p -> "Inp_Grid_Step".equals(p.getName()) && p.isOptimizeEnabled()));
        assertTrue(k12.getDiversityParameterSnapshot().stream()
                .anyMatch(p -> "Inp_TakeProfit".equals(p.getName()) && p.isOptimizeEnabled()));
        assertTrue(k12.getDiversityParameterSnapshot().stream()
                .noneMatch(p -> "Inp_Use_ADX_Filter".equals(p.getName()) && p.isOptimizeEnabled()));

        WorkflowTask gridOptimizer = project.getTasks().stream()
                .filter(task -> task.getType() == WorkflowTask.TaskType.OPTIMIZER)
                .filter(task -> task.getName() != null && task.getName().startsWith("01 Grid-Fundament"))
                .findFirst().orElseThrow();
        assertEquals(2, gridOptimizer.getOptimizerMode());
        assertTrue(gridOptimizer.getOptimizerTargetParameters().contains("Inp_Envelopes_Deviation"));
        assertTrue(gridOptimizer.getOptimizerTargetParameters().contains("Inp_Envelopes_Deviation_Lower"));
        assertFalse(gridOptimizer.getOptimizerTargetParameters().contains("TimeFrame_Envelopes"));

        WorkflowTask gridFilter = project.getTasks().stream()
                .filter(task -> task.getType() == WorkflowTask.TaskType.PRE_FILTER)
                .filter(task -> task.getName() != null && task.getName().startsWith("01 Grid-Fundament"))
                .findFirst().orElseThrow();
        assertTrue(gridFilter.getFilterConditions().isEmpty());
        assertEquals(7, gridOptimizer.getOptimizerCriterion());
        assertEquals(1, gridOptimizer.getOptimizerForwardMode());
        assertTrue(gridOptimizer.getOptimizerTargetParameters().contains("Inp_Use_Trend_Filter"));
        assertTrue(gridOptimizer.getOptimizerTargetParameters().contains("Inp_Use_ADX_Filter"));

        WorkflowTask safetyFilter = project.getTasks().stream()
                .filter(task -> task.getType() == WorkflowTask.TaskType.PRE_FILTER)
                .filter(task -> task.getName() != null && task.getName().startsWith("11 Adaptive"))
                .findFirst().orElseThrow();
        assertTrue(safetyFilter.getFilterConditions().isEmpty());

        WorkflowTask smoke = findByTarget(project, ToTheMoon132TickKillWorkflowFactory.SMOKE_DATABANK);
        assertEquals(ToTheMoon132TickKillWorkflowFactory.TOP20_DATABANK, smoke.getSourceDatabank());
        assertEquals(WorkflowTask.MODE_EVERY_TICK, smoke.getExecutionMode());
        assertFalse(smoke.hasExplicitForwardSplit());
        assertEquals(BacktestConfig.MODEL_EVERY_TICK, smoke.getMt5Model());
        assertEquals("2025-05-01", smoke.getStartDate());
        assertEquals(ToTheMoon132TickKillWorkflowFactory.DEVELOPMENT_TO, smoke.getEndDate());
        assertEquals(2, smoke.getFilterConditions().size());
        assertTrue(smoke.getFilterConditions().stream()
                .noneMatch(c -> c.getMetric() == FilterCondition.Metric.LT_NET_PROFIT));

        WorkflowTask kill1y = findByTarget(project, ToTheMoon132TickKillWorkflowFactory.KILL_1Y_DATABANK);
        assertEquals(ToTheMoon132TickKillWorkflowFactory.SMOKE_DATABANK, kill1y.getSourceDatabank());
        assertEquals("2024-08-01", kill1y.getStartDate());
        assertEquals(ToTheMoon132TickKillWorkflowFactory.DEVELOPMENT_TO, kill1y.getEndDate());
        assertEquals(WorkflowTask.MODE_EVERY_TICK, kill1y.getExecutionMode());
        assertTrue(kill1y.getName().contains("kein Ranking"));

        WorkflowTask devTick = findByTarget(project, ToTheMoon132TickKillWorkflowFactory.DEV_TICK_DATABANK);
        assertEquals(ToTheMoon132TickKillWorkflowFactory.KILL_1Y_DATABANK, devTick.getSourceDatabank());
        assertEquals(ToTheMoon132TickKillWorkflowFactory.DEVELOPMENT_FROM, devTick.getStartDate());
        assertEquals(ToTheMoon132TickKillWorkflowFactory.DEVELOPMENT_TO, devTick.getEndDate());

        WorkflowConfigurationValidator.validateDatabankExecutionOrder(project.getTasks(), List.of(
                DatabankManager.RESULTS,
                DatabankManager.EXISTING_PORTFOLIO,
                DatabankManager.FINAL));
    }

    @Test
    public void oosIsTheOnlySelectionGateAndReportsDoNotReFilter() {
        CustomProject project = ToTheMoon132TickKillWorkflowFactory.create(
                "Tick-Kill", "AUDCAD", "M15", completeSyntheticPreset(), null);

        WorkflowTask oos = findByTarget(project, ToTheMoon132TickKillWorkflowFactory.OOS_DATABANK);
        WorkflowTask fourYears = findByTarget(project, ToTheMoon132TickKillWorkflowFactory.FOUR_YEAR_DATABANK);
        WorkflowTask realTicks = findByTarget(project, ToTheMoon132TickKillWorkflowFactory.REAL_TICKS_DATABANK);
        WorkflowTask publication = findByTarget(project, DatabankManager.FINAL);

        assertEquals(4, oos.getFilterConditions().size());
        assertTrue(oos.getName().contains("Selektionsgate"));
        assertEquals(ToTheMoon132TickKillWorkflowFactory.DEV_TICK_DATABANK, oos.getSourceDatabank());
        assertEquals(ToTheMoon132TickKillWorkflowFactory.OOS_FROM, oos.getStartDate());
        assertEquals(ToTheMoon132TickKillWorkflowFactory.FINAL_TO, oos.getEndDate());
        assertEquals(WorkflowTask.MODE_EVERY_TICK, oos.getExecutionMode());

        assertTrue(fourYears.getFilterConditions().isEmpty());
        assertTrue(fourYears.getName().contains("kein Gate"));
        assertEquals(WorkflowTask.MODE_EVERY_TICK, fourYears.getExecutionMode());
        assertEquals(BacktestConfig.MODEL_EVERY_TICK, fourYears.getMt5Model());
        assertEquals(ToTheMoon132TickKillWorkflowFactory.DEVELOPMENT_FROM, fourYears.getStartDate());
        assertEquals(ToTheMoon132TickKillWorkflowFactory.FINAL_TO, fourYears.getEndDate());

        assertTrue(realTicks.getFilterConditions().isEmpty());
        assertEquals(WorkflowTask.MODE_REAL_TICKS, realTicks.getExecutionMode());
        assertEquals(BacktestConfig.MODEL_REAL_TICKS, realTicks.getMt5Model());
        assertEquals(ToTheMoon132TickKillWorkflowFactory.FOUR_YEAR_DATABANK, realTicks.getSourceDatabank());
        assertEquals("AUDCAD", realTicks.getRetestSymbol());
        assertEquals("M15", realTicks.getRetestPeriod());

        assertTrue(publication.getFilterConditions().isEmpty());
        assertEquals(ToTheMoon132TickKillWorkflowFactory.REAL_TICKS_DATABANK, publication.getSourceDatabank());
        assertEquals(WorkflowTask.MODE_REAL_TICKS, publication.getExecutionMode());

        long gateNamed = project.getTasks().stream()
                .filter(task -> task.getName() != null && task.getName().contains("Selektionsgate"))
                .count();
        assertEquals(1, gateNamed);
    }

    @Test
    public void shippedV132MasterStaysInsideEverySearchStage() {
        List<EaParameter> preset = ToTheMoon132GuidedWorkflowFactory.loadProvenPresetFromDisk(
                "ToTheMoon_KI_v132");
        assertFalse("Das ausgelieferte v132-Preset fehlt.", preset.isEmpty());
        CustomProject project = ToTheMoon132TickKillWorkflowFactory.create(
                "Tick-Kill", "GBPJPY", "M5", preset, null);

        List<MasterSearchSpaceValidator.Issue> issues = MasterSearchSpaceValidator.validateProject(
                project.getTasks(), project.getProvenMasterParameters(), project.getPeriod());
        assertTrue(issues.stream().map(MasterSearchSpaceValidator.Issue::describe)
                .reduce("", (left, right) -> left + "\n" + right), issues.isEmpty());
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
        addFixed(result, "TimeFrame_Envelopes", "1");
        addFixed(result, "TimeFrame_Envelopes_Lower", "1");
        addFixed(result, "Values_Envelopes_Lower", "1");
        addFixed(result, "Inp_Use_Trend_Filter", "false");
        addFixed(result, "Inp_Trend_EMA_Period", "450");
        return result;
    }

    private static void addFixed(List<EaParameter> result, String name, String value) {
        if (result.stream().anyMatch(existing -> name.equals(existing.getName()))) return;
        EaParameter parameter = new EaParameter(name, value);
        parameter.setStringType(false);
        result.add(parameter);
    }

    private static WorkflowTask findByTarget(CustomProject project, String targetDatabank) {
        return project.getTasks().stream()
                .filter(task -> targetDatabank.equals(task.getTargetDatabank()))
                .findFirst()
                .orElseThrow();
    }
}
