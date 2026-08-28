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
        assertEquals("2024-08-01", ToTheMoon132GuidedWorkflowFactory.SEARCH_FROM);
        assertEquals("2025-01-31", ToTheMoon132GuidedWorkflowFactory.FORWARD_FROM);
        assertEquals("2024-08-01", ToTheMoon132GuidedWorkflowFactory.GRID_TICK_FROM);
        assertEquals(33, project.getTasks().size());
        assertEquals(WorkflowTask.TaskType.STRATEGY_SELECTION, project.getTasks().get(0).getType());

        Set<String> taskIds = new HashSet<>();
        Set<String> databankTargets = new HashSet<>();
        int optimizerCount = 0;
        int masterReferenceCount = 0;
        for (WorkflowTask task : project.getTasks()) {
            assertTrue(taskIds.add(task.getId()));
            assertEquals(WorkflowTask.TaskStatus.PENDING, task.getStatus());
            if (task.getType() == WorkflowTask.TaskType.OPTIMIZER) {
                optimizerCount++;
                assertEquals("AUDCAD", task.getRetestSymbol());
                assertEquals("M5", task.getRetestPeriod());
                assertEquals(ToTheMoon132GuidedWorkflowFactory.SEARCH_FROM, task.getStartDate());
                assertEquals(ToTheMoon132GuidedWorkflowFactory.DEVELOPMENT_TO, task.getEndDate());
                assertEquals(1, task.getOptimizerForwardMode());
                assertEquals("2025-01-31", task.getOptimizerForwardDate());
                assertFalse(task.getOptimizerTargetParameters().isEmpty());
                assertFalse(task.getOptimizerParameterSnapshot().isEmpty());
                assertFalse(task.isOptimizerParameterBasisAdopted());

                Set<String> enabled = new HashSet<>();
                for (EaParameter parameter : task.getOptimizerParameterSnapshot()) {
                    if (parameter.isOptimizeEnabled()) enabled.add(parameter.getName());
                }
                assertEquals(new HashSet<>(task.getOptimizerTargetParameters()), enabled);
            }
            if (task.getType() == WorkflowTask.TaskType.MASTER_REFERENCE) {
                masterReferenceCount++;
            }
            if (task.getType() != WorkflowTask.TaskType.STRATEGY_SELECTION) {
                assertTrue("Databank targets must stay unambiguous: " + task.getTargetDatabank(),
                        databankTargets.add(task.getTargetDatabank()));
            }
        }
        assertEquals(11, optimizerCount);
        assertEquals(0, masterReferenceCount);

        WorkflowTask isOos = findByTarget(project, ToTheMoon132GuidedWorkflowFactory.GRID_IS_OOS_DATABANK);
        assertEquals(WorkflowTask.TaskType.PRE_FILTER, isOos.getType());
        assertEquals(ToTheMoon132GuidedWorkflowFactory.GRID_QUALITY_DATABANK, isOos.getSourceDatabank());
        assertEquals(4, isOos.getFilterConditions().size());
        assertTrue(isOos.getFilterConditions().stream()
                .anyMatch(c -> c.getMetric() == FilterCondition.Metric.BT_NET_PROFIT
                        && c.getValue() == ToTheMoon132GuidedWorkflowFactory.GRID_HALF_MIN_PROFIT));
        assertTrue(isOos.getFilterConditions().stream()
                .anyMatch(c -> c.getMetric() == FilterCondition.Metric.FW_TOTAL_TRADES
                        && c.getValue() == ToTheMoon132GuidedWorkflowFactory.GRID_HALF_MIN_TRADES));

        WorkflowTask shortlist = findByTarget(project, ToTheMoon132GuidedWorkflowFactory.GRID_SHORTLIST_DATABANK);
        assertEquals(WorkflowTask.TaskType.DIVERSITY_FILTER, shortlist.getType());
        assertEquals(ToTheMoon132GuidedWorkflowFactory.GRID_IS_OOS_DATABANK, shortlist.getSourceDatabank());
        assertEquals(ToTheMoon132GuidedWorkflowFactory.GRID_SHORTLIST_MAX_STRATEGIES,
                shortlist.getDiversityMaxStrategies());
        assertFalse(shortlist.isDiversityStampClusterIds());
        assertTrue(shortlist.isDiversityRankByScore());
        assertFalse(shortlist.isDiversityRankByActivity());
        assertFalse(shortlist.copyForPersistence().isDiversityStampClusterIds());

        WorkflowTask tickGate = findByTarget(project, ToTheMoon132GuidedWorkflowFactory.GRID_TICK_DATABANK);
        assertEquals(WorkflowTask.TaskType.RETESTER, tickGate.getType());
        assertEquals(ToTheMoon132GuidedWorkflowFactory.GRID_SHORTLIST_DATABANK, tickGate.getSourceDatabank());
        assertEquals(ToTheMoon132GuidedWorkflowFactory.GRID_TICK_FROM, tickGate.getStartDate());
        assertEquals(ToTheMoon132GuidedWorkflowFactory.DEVELOPMENT_TO, tickGate.getEndDate());
        assertEquals(WorkflowTask.MODE_EVERY_TICK, tickGate.getExecutionMode());
        assertTrue(tickGate.hasExplicitForwardSplit());
        assertEquals(1, tickGate.getOptimizerForwardMode());
        assertEquals("2025-01-31", tickGate.getOptimizerForwardDate());
        assertEquals(2, tickGate.getFilterConditions().size());
        assertTrue(tickGate.getFilterConditions().stream()
                .anyMatch(c -> c.getMetric() == FilterCondition.Metric.LT_NET_PROFIT
                        && c.getValue() == ToTheMoon132GuidedWorkflowFactory.GRID_TICK_MIN_PROFIT));
        assertTrue(tickGate.getFilterConditions().stream()
                .anyMatch(c -> c.getMetric() == FilterCondition.Metric.LT_TOTAL_TRADES
                        && c.getValue() == ToTheMoon132GuidedWorkflowFactory.GRID_TICK_MIN_TRADES));

        WorkflowTask gridCluster = project.getTasks().stream()
                .filter(task -> task.getType() == WorkflowTask.TaskType.DIVERSITY_FILTER)
                .filter(task -> ToTheMoon132GuidedWorkflowFactory.GRID_CLUSTER_DATABANK
                        .equals(task.getTargetDatabank()))
                .findFirst().orElseThrow();
        assertEquals(ToTheMoon132GuidedWorkflowFactory.GRID_TICK_DATABANK, gridCluster.getSourceDatabank());
        assertTrue(project.getTasks().indexOf(isOos) < project.getTasks().indexOf(shortlist));
        assertTrue(project.getTasks().indexOf(shortlist) < project.getTasks().indexOf(tickGate));
        assertTrue(project.getTasks().indexOf(tickGate) < project.getTasks().indexOf(gridCluster));
        WorkflowTask threeYearOhlc = findByTarget(project,
                ToTheMoon132GuidedWorkflowFactory.GRID_3Y_OHLC_DATABANK);
        assertEquals(WorkflowTask.TaskType.RETESTER, threeYearOhlc.getType());
        assertEquals(ToTheMoon132GuidedWorkflowFactory.GRID_CLUSTER_DATABANK, threeYearOhlc.getSourceDatabank());
        assertEquals(ToTheMoon132GuidedWorkflowFactory.DEVELOPMENT_FROM, threeYearOhlc.getStartDate());
        assertEquals(ToTheMoon132GuidedWorkflowFactory.DEVELOPMENT_TO, threeYearOhlc.getEndDate());
        assertEquals(WorkflowTask.MODE_OHLC_M1, threeYearOhlc.getExecutionMode());
        assertEquals(4, threeYearOhlc.getFilterConditions().size());

        WorkflowTask g02Optimizer = project.getTasks().stream()
                .filter(task -> task.getType() == WorkflowTask.TaskType.OPTIMIZER)
                .filter(task -> task.getName() != null && task.getName().startsWith("02 "))
                .findFirst().orElseThrow();
        assertTrue(project.getTasks().indexOf(gridCluster) < project.getTasks().indexOf(threeYearOhlc));
        assertTrue(project.getTasks().indexOf(threeYearOhlc) < project.getTasks().indexOf(g02Optimizer));
        assertEquals(ToTheMoon132GuidedWorkflowFactory.GRID_3Y_OHLC_DATABANK, g02Optimizer.getSourceDatabank());

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
        assertEquals(ClusterIdentity.MAX_POOLED_STRATEGIES, top20.getDiversityMaxStrategies());
        assertTrue(top20.isDiversityStampClusterIds());
        assertTrue(top20.getName().contains("100"));
        assertTrue(top20.getDiversityParameterSnapshot().stream()
                .anyMatch(p -> "Inp_Grid_Step".equals(p.getName()) && p.isOptimizeEnabled()));
        assertTrue(top20.getDiversityParameterSnapshot().stream()
                .anyMatch(p -> "Inp_TakeProfit".equals(p.getName()) && p.isOptimizeEnabled()));
        assertTrue(top20.getDiversityParameterSnapshot().stream()
                .anyMatch(p -> "Inp_Envelopes_Deviation".equals(p.getName()) && p.isOptimizeEnabled()));
        assertTrue(top20.getDiversityParameterSnapshot().stream()
                .noneMatch(p -> "Inp_Use_ADX_Filter".equals(p.getName()) && p.isOptimizeEnabled()));
        assertTrue(gridCluster.getDiversityParameterSnapshot().stream()
                .anyMatch(p -> "Inp_TakeProfit".equals(p.getName()) && p.isOptimizeEnabled()));
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

        WorkflowTask firstOptimizer = findOptimizer(project, "01 Grid-Fundament");
        WorkflowTask secondOptimizer = findOptimizer(project, "02 Order-Taktung");
        assertTrue(find(firstOptimizer, "Inp_Grid_Step").isOptimizeEnabled());
        assertEquals("725", find(firstOptimizer, "Inp_Grid_Step").getValue());
        assertFalse(find(secondOptimizer, "Inp_Grid_Step").isOptimizeEnabled());
        assertEquals("725", find(secondOptimizer, "Inp_Grid_Step").getValue());
        assertTrue(find(firstOptimizer, "Inp_TakeProfit").isOptimizeEnabled());
        assertFalse(find(secondOptimizer, "Inp_TakeProfit").isOptimizeEnabled());
    }

    @Test
    public void firstStageSearchesEveryTradingKnobGenetically() {
        List<String> gridTargets = ToTheMoon132GuidedWorkflowFactory.stageTargetParameters()
                .get("01 Grid-Fundament");
        assertTrue(gridTargets.containsAll(List.of(
                "Inp_Grid_Step",
                "Inp_Step_Multiplier",
                "Inp_Next_Lot_Multiplier",
                "Inp_TakeProfit",
                "Inp_Envelopes_Period",
                "Inp_Envelopes_Deviation",
                "Inp_Envelopes_Period_Lower",
                "Inp_Envelopes_Deviation_Lower",
                "Inp_Use_Trend_Filter",
                "Min_Profit",
                "Inp_Use_ADX_Filter",
                "Inp_Use_Session_Filter")));
        assertFalse(gridTargets.contains("TimeFrame_Envelopes"));
        assertTrue(gridTargets.size() > 40);

        List<EaParameter> preset = completeSyntheticPreset();
        EaParameter takeProfit = preset.stream()
                .filter(p -> "Inp_TakeProfit".equals(p.getName())).findFirst().orElseThrow();
        takeProfit.setValue("65");
        CustomProject project = ToTheMoon132GuidedWorkflowFactory.create("Guided", preset, null);
        WorkflowTask optimizer = findOptimizer(project, "01 Grid-Fundament");
        assertEquals(2, optimizer.getOptimizerMode());
        assertEquals(7, optimizer.getOptimizerCriterion());
        assertEquals(1, optimizer.getOptimizerForwardMode());
        assertBand(find(optimizer, "Inp_TakeProfit"), "40", "5", "80");
        assertBand(find(optimizer, "Inp_Grid_Step"), "400", "25", "900");
        assertBand(find(optimizer, "Inp_Envelopes_Deviation"), "0.08", "0.01", "0.40");
        assertBand(find(optimizer, "Inp_Envelopes_Deviation_Lower"), "0.10", "0.01", "0.50");
        assertTrue(find(optimizer, "Inp_TakeProfit").isOptimizeEnabled());
        assertTrue(find(optimizer, "Inp_Envelopes_Period").isOptimizeEnabled());
        assertTrue(find(optimizer, "Inp_Use_Trend_Filter").isOptimizeEnabled());
        assertTrue(find(optimizer, "Inp_Use_ADX_Filter").isOptimizeEnabled());
        assertFalse(optimizer.getOptimizerTargetParameters().contains("TimeFrame_Envelopes"));
    }

    @Test
    public void searchStagesDoNotAddExtraQualityFilters() {
        CustomProject project = ToTheMoon132GuidedWorkflowFactory.create(
                "Guided", completeSyntheticPreset(), null);
        long filtered = project.getTasks().stream()
                .filter(task -> task.getType() == WorkflowTask.TaskType.PRE_FILTER)
                .filter(task -> task.getName() != null && task.getName().contains("Optimizer")
                        || (task.getName() != null && task.getName().contains("Qualitätsfilter")))
                .filter(task -> !task.getFilterConditions().isEmpty())
                .count();
        assertEquals(0, filtered);
    }

    @Test
    public void jpyPairsUseAWiderGridBandInTheFirstStage() {
        CustomProject project = ToTheMoon132GuidedWorkflowFactory.create(
                "Guided", "GBPJPY", "M5", completeSyntheticPreset(), null);
        WorkflowTask optimizer = findOptimizer(project, "01 Grid-Fundament");
        assertBand(find(optimizer, "Inp_Grid_Step"), "1200", "100", "2500");
        assertBand(find(optimizer, "Inp_Step_Multiplier"), "1.10", "0.05", "1.40");
        assertBand(find(optimizer, "Inp_Next_Lot_Multiplier"), "1.20", "0.05", "1.60");
        assertEquals("2000", find(optimizer, "Inp_Grid_Step").getValue());
        assertEquals("0.3", find(optimizer, "Inp_Envelopes_Deviation").getValue());
    }

    @Test
    public void architectureBaselineFreezesH1EnvelopesAndTrendFilter() {
        List<EaParameter> preset = completeSyntheticPreset();
        CustomProject project = ToTheMoon132GuidedWorkflowFactory.create("Guided", preset, null);
        WorkflowTask optimizer = findOptimizer(project, "01 Grid-Fundament");

        assertEquals("16385", find(optimizer, "TimeFrame_Envelopes").getValue());
        assertEquals("16385", find(optimizer, "TimeFrame_Envelopes_Lower").getValue());
        assertFalse(find(optimizer, "TimeFrame_Envelopes").isOptimizeEnabled());
        assertEquals("true", find(optimizer, "Inp_Use_Trend_Filter").getValue());
        assertTrue(find(optimizer, "Inp_Use_Trend_Filter").isOptimizeEnabled());
        assertEquals("250", find(optimizer, "Inp_Trend_EMA_Period").getValue());
        assertEquals("4", find(optimizer, "Envelopes_Price_Lower").getValue());
        assertEquals("0.17", find(optimizer, "Inp_Envelopes_Deviation").getValue());
        assertEquals("0.29", find(optimizer, "Inp_Envelopes_Deviation_Lower").getValue());
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
        assertFalse(stages.get("03 Envelopes oben").contains("TimeFrame_Envelopes"));
        assertFalse(stages.get("04 Envelopes unten").contains("TimeFrame_Envelopes_Lower"));
        assertTrue(stages.get("05 ADX-Regime").contains("Inp_ADX_Timeframe"));
        assertTrue(stages.get("06 ATR-Gridabstand").contains("Inp_ATR_Timeframe"));
        assertTrue(stages.get("07 Volatilität & Richtung").contains("Inp_Vol_ATR_Timeframe"));

        CustomProject project = ToTheMoon132GuidedWorkflowFactory.create(
                "Guided", completeSyntheticPreset(), null);

        assertTimeframeBand(findOptimizer(project, "05 ADX-Regime"), "Inp_ADX_Timeframe");
        assertTimeframeBand(findOptimizer(project, "06 ATR-Gridabstand"), "Inp_ATR_Timeframe");
        assertTimeframeBand(findOptimizer(project, "07 Volatilität & Richtung"), "Inp_Vol_ATR_Timeframe");
    }

    @Test
    public void envelopesKeepSanePercentageDomainsAndGeneticSearch() {
        CustomProject project = ToTheMoon132GuidedWorkflowFactory.create(
                "Guided", completeSyntheticPreset(), null);
        WorkflowTask upper = findOptimizer(project, "03 Envelopes oben");
        WorkflowTask lower = findOptimizer(project, "04 Envelopes unten");

        assertBand(find(upper, "Inp_Envelopes_Deviation"), "0.08", "0.01", "0.40");
        assertBand(find(lower, "Inp_Envelopes_Deviation_Lower"), "0.10", "0.01", "0.50");
        assertBand(find(upper, "Envelopes_Price"), "1", "1", "7");
        assertBand(find(lower, "Envelopes_Price_Lower"), "1", "1", "7");
        assertFalse(upper.getOptimizerTargetParameters().contains("TimeFrame_Envelopes"));
        assertFalse(lower.getOptimizerTargetParameters().contains("TimeFrame_Envelopes_Lower"));
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
                project.getTasks(), project.getProvenMasterParameters(), project.getPeriod());

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
