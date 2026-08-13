package com.backtester.workflow;

import com.backtester.config.EaParameter;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
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
    public void migratesExistingTop20AndFinalTailThenInvalidatesOnlyAffectedBranch() {
        CustomProject project = ToTheMoon132GuidedWorkflowFactory.create(
                "Guided", completeSyntheticPreset(), null);
        WorkflowTask top20 = findByTarget(project, "g11_dev_top20");
        WorkflowTask oos = findByTarget(project, "g13_oos_tick");
        WorkflowTask fourYears = findByTarget(project, "g14_final_4y");
        WorkflowTask publication = findByTarget(project, DatabankManager.FINAL);

        top20.setDiversityDeduplicateEffectiveV132(false);
        oos.setName("14 OOS-Retest — Every Tick (unberührtes Jahr)");
        fourYears.setName("15 Finaler Every-Tick-Retest — volle 4 Jahre");
        publication.setName("16 Finale Auswahl — viele Trades, PF und niedriger DD");
        publication.setFilterConditions(List.of(new FilterCondition(
                FilterCondition.Metric.LT_MAX_DD_PERCENT,
                FilterCondition.Operator.LESS_EQUAL, 8)));

        CombinedPass sentinel = pass(42);
        project.getDatabanks().put("g11_safety_pick", new ArrayList<>(List.of(sentinel)));
        for (WorkflowTask task : project.getTasks()) {
            task.setStatus(WorkflowTask.TaskStatus.COMPLETED);
            if (project.getTasks().indexOf(task) >= project.getTasks().indexOf(top20)) {
                project.getDatabanks().put(task.getTargetDatabank(),
                        new ArrayList<>(List.of(sentinel)));
            }
        }
        StrategyBacktestArchive archive = archiveWithTabs(
                "g11_safety_pick", "g11_dev_top20", "g12_dev_tick",
                "g13_oos_tick", "g14_final_4y", DatabankManager.FINAL);
        project.getStrategyArchives().put(archive.getStrategyKey(), archive);

        assertTrue(ToTheMoon132GuidedWorkflowFactory.ensureDevelopmentTop20Selection(project));
        assertFalse(ToTheMoon132GuidedWorkflowFactory.ensureDevelopmentTop20Selection(project));

        assertTrue(top20.isDiversityDeduplicateEffectiveV132());
        assertTrue(oos.getName().contains("einziges finales Selektionsgate"));
        assertTrue(fourYears.getName().contains("kein Gate"));
        assertTrue(publication.getName().contains("keine Zusatzfilter"));
        assertTrue(publication.getFilterConditions().isEmpty());
        assertFalse(project.getDatabanks().get("g11_safety_pick").isEmpty());
        assertNotNull(project.getStrategyArchives().get(archive.getStrategyKey())
                .getRun("g11_safety_pick"));
        for (int i = project.getTasks().indexOf(top20); i < project.getTasks().size(); i++) {
            WorkflowTask task = project.getTasks().get(i);
            assertEquals(WorkflowTask.TaskStatus.PENDING, task.getStatus());
            assertTrue(project.getDatabanks().get(task.getTargetDatabank()).isEmpty());
            assertNull(project.getStrategyArchives().get(archive.getStrategyKey())
                    .getRun(task.getTargetDatabank()));
        }
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
    public void searchSpaceRepairInvalidatesEveryTaskFromEarliestChangedOptimizer() {
        CustomProject project = ToTheMoon132GuidedWorkflowFactory.create(
                "Guided", completeSyntheticPreset(), null);
        WorkflowTask upperEnvelopes = findOptimizer(project, "03 Envelopes oben");
        WorkflowTask risk = findOptimizer(project, "08 Grid-Risiko");
        WorkflowTask upstream = findByTarget(project, "g02_cadence_pick");
        int firstChangedIndex = project.getTasks().indexOf(upperEnvelopes);
        CombinedPass sentinel = pass(7);

        upperEnvelopes.setOptimizerMode(1); // expected genetic
        risk.setOptimizerMode(2);           // expected complete
        for (WorkflowTask task : project.getTasks()) {
            task.setStatus(WorkflowTask.TaskStatus.COMPLETED);
            if (!DatabankManager.RESULTS.equals(task.getTargetDatabank())) {
                project.getDatabanks().put(task.getTargetDatabank(),
                        new ArrayList<>(List.of(sentinel)));
            }
        }
        StrategyBacktestArchive archive = new StrategyBacktestArchive("7|Strat 7", "Strat 7", 7);
        for (WorkflowTask task : project.getTasks()) {
            if (!DatabankManager.RESULTS.equals(task.getTargetDatabank())) {
                StrategyBacktestRun run = new StrategyBacktestRun();
                run.setTabName(task.getTargetDatabank());
                archive.upsert(run);
            }
        }
        project.getStrategyArchives().put(archive.getStrategyKey(), archive);

        assertTrue(ToTheMoon132GuidedWorkflowFactory.repairStageOptimizerSearchSpaces(project));
        assertFalse(ToTheMoon132GuidedWorkflowFactory.repairStageOptimizerSearchSpaces(project));

        assertEquals(WorkflowTask.TaskStatus.COMPLETED, upstream.getStatus());
        assertFalse(project.getDatabanks().get(upstream.getTargetDatabank()).isEmpty());
        assertNotNull(project.getStrategyArchives().get(archive.getStrategyKey())
                .getRun(upstream.getTargetDatabank()));
        for (int i = firstChangedIndex; i < project.getTasks().size(); i++) {
            WorkflowTask task = project.getTasks().get(i);
            assertEquals("stale status at " + task.getName(),
                    WorkflowTask.TaskStatus.PENDING, task.getStatus());
            assertTrue("stale databank " + task.getTargetDatabank(),
                    project.getDatabanks().get(task.getTargetDatabank()).isEmpty());
            assertNull("stale archive " + task.getTargetDatabank(),
                    project.getStrategyArchives().get(archive.getStrategyKey())
                            .getRun(task.getTargetDatabank()));
        }
    }

    @Test
    public void repairScrubsLegacyTimeframeBandsWithoutRebuildingCorrectStage() {
        CustomProject project = ToTheMoon132GuidedWorkflowFactory.create(
                "Guided", completeSyntheticPreset(), Path.of("build", "guided-reports"));
        WorkflowTask grid = findOptimizer(project, "01 Grid-Fundament");

        List<EaParameter> snapshot = grid.getOptimizerParameterSnapshot();
        EaParameter adxTf = snapshot.stream()
                .filter(parameter -> "Inp_ADX_Timeframe".equals(parameter.getName()))
                .findFirst().orElseThrow();
        adxTf.setOptimizeStart("0");
        adxTf.setOptimizeStep("0");
        adxTf.setOptimizeEnd("49153");
        adxTf.setOptimizeEnabled(false);
        grid.setOptimizerParameterSnapshot(snapshot);

        assertTrue(ToTheMoon132GuidedWorkflowFactory.repairStageOptimizerSearchSpaces(project));
        assertFalse(ToTheMoon132GuidedWorkflowFactory.repairStageOptimizerSearchSpaces(project));

        assertEquals("1", find(grid, "Inp_ADX_Timeframe").getOptimizeStart());
        assertEquals("1", find(grid, "Inp_ADX_Timeframe").getOptimizeStep());
        assertEquals("16385", find(grid, "Inp_ADX_Timeframe").getOptimizeEnd());
        assertFalse(find(grid, "Inp_ADX_Timeframe").isOptimizeEnabled());
        assertEquals(List.of("Inp_Grid_Step", "Inp_Step_Multiplier", "Inp_Next_Lot_Multiplier"),
                grid.getOptimizerTargetParameters());
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

    private static CombinedPass pass(int passNumber) {
        Pass pass = new Pass();
        pass.setPassNumber(passNumber);
        pass.setProfit(100);
        pass.setTotalTrades(100);
        return new CombinedPass(pass, null, 50, 1, "");
    }

    private static StrategyBacktestArchive archiveWithTabs(String... tabs) {
        StrategyBacktestArchive archive = new StrategyBacktestArchive("42|Strat 42", "Strat 42", 42);
        for (String tab : tabs) {
            StrategyBacktestRun run = new StrategyBacktestRun();
            run.setTabName(tab);
            archive.upsert(run);
        }
        return archive;
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
