package com.backtester.workflow;

import com.backtester.report.OptimizationResult;
import com.backtester.report.OptimizationResult.CombinedPass;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class DatabankManagerTest {

    @Test
    public void routingCopiesStrategySetfileWithoutSharingMutableState() {
        DatabankManager manager = new DatabankManager();
        CombinedPass source = pass(42, 100.0);
        source.getBacktestPass().setParameterSetLines(List.of("Inp_Grid_Step=350||||||N"));
        WorkflowTask task = new WorkflowTask("Copy", WorkflowTask.TaskType.PRE_FILTER);
        task.setSourceDatabank("raw");
        task.setTargetDatabank("quality");

        manager.processTaskDatabanks(task, List.of(source));
        CombinedPass routed = manager.getDatabank("quality").get(0);

        assertEquals(List.of("Inp_Grid_Step=350||||||N"),
                routed.getBacktestPass().getParameterSetLines());
        source.getBacktestPass().getParameterSetLines().set(0, "Inp_Grid_Step=999||||||N");
        assertEquals("Inp_Grid_Step=350||||||N",
                manager.getDatabank("quality").get(0).getBacktestPass().getParameterSetLines().get(0));
    }

    @Test
    public void explicitTaskOutputWinsOverNonEmptySource() {
        DatabankManager manager = new DatabankManager();
        CombinedPass stale = pass(1, 100.0);
        CombinedPass fresh = pass(2, 200.0);
        manager.setDatabankContent("Results", List.of(stale));

        WorkflowTask task = new WorkflowTask("Retest", WorkflowTask.TaskType.RETESTER);
        task.setSourceDatabank("Results");
        task.setTargetDatabank("Final");

        manager.processTaskDatabanks(task, List.of(fresh));

        assertEquals(1, manager.getDatabank("Results").size());
        assertEquals(2, manager.getDatabank("Final").get(0).getPassNumber());
    }

    @Test
    public void explicitEmptyOutputDoesNotResurrectSourceOrPreviousTarget() {
        DatabankManager manager = new DatabankManager();
        manager.setDatabankContent("Results", List.of(pass(1, 100.0)));
        manager.setDatabankContent("Final", List.of(pass(9, 900.0)));

        WorkflowTask task = new WorkflowTask("Empty retest", WorkflowTask.TaskType.RETESTER);
        task.setSourceDatabank("Results");
        task.setTargetDatabank("Final");

        assertTrue(manager.processTaskDatabanks(task, List.of()).isEmpty());
        assertTrue(manager.getDatabank("Final").isEmpty());
        assertEquals(1, manager.getDatabank("Results").size());
    }

    @Test
    public void sameSourceAndTargetHonoursDeleteFailed() {
        DatabankManager manager = new DatabankManager();
        CombinedPass good = pass(1, 100.0);
        CombinedPass bad = pass(2, -10.0);
        manager.setDatabankContent("Results", Arrays.asList(good, bad));

        WorkflowTask task = new WorkflowTask("Filter", WorkflowTask.TaskType.PRE_FILTER);
        task.setSourceDatabank("Results");
        task.setTargetDatabank("Results");
        task.addFilterCondition(new FilterCondition(FilterCondition.Metric.BT_NET_PROFIT,
                FilterCondition.Operator.GREATER_THAN, 0.0));

        task.setDeleteFailed(false);
        assertEquals(1, manager.processTaskDatabanks(task, Arrays.asList(good, bad)).size());
        assertEquals(2, manager.getDatabank("Results").size());

        task.setDeleteFailed(true);
        manager.processTaskDatabanks(task, Arrays.asList(good, bad));
        assertEquals(1, manager.getDatabank("Results").size());
        assertEquals(1, manager.getDatabank("Results").get(0).getPassNumber());
    }

    @Test
    public void preFilterEmptyUnclusteredStaysEmptyInsteadOfPromotingScoreLeader() {
        DatabankManager manager = new DatabankManager();
        CombinedPass leader = pass(7, 80.0);
        CombinedPass weaker = pass(3, 10.0);
        WorkflowTask filter = new WorkflowTask("01 Grid — Filter", WorkflowTask.TaskType.PRE_FILTER);
        filter.setSourceDatabank("g01_raw");
        filter.setTargetDatabank("g01_pick");
        filter.setDeleteFailed(true);
        filter.setFilterConditions(List.of(new FilterCondition(
                FilterCondition.Metric.BT_PROFIT_FACTOR,
                FilterCondition.Operator.GREATER_EQUAL, 9.0)));

        List<CombinedPass> routed = manager.processTaskDatabanks(filter, List.of(leader, weaker));

        assertTrue(routed.isEmpty());
        assertTrue(manager.getDatabank("g01_pick").isEmpty());
        assertTrue(DatabankManager.shouldHaltChainAfterPreFilter(filter, routed));
        assertTrue(filter.getFilterRejectionNote(),
                filter.getFilterRejectionNote().contains("Pass #7"));
        assertTrue(filter.getFilterRejectionNote(),
                filter.getFilterRejectionNote().contains("angehalten"));
        assertFalse(filter.getFilterRejectionNote(),
                filter.getFilterRejectionNote().contains("Kette läuft mit Score-Bestem"));
    }

    @Test
    public void fullyEmptyClusteredPreFilterKeepsEachLinesLeader() {
        DatabankManager manager = new DatabankManager();
        CombinedPass b1 = pass(1, 10.0);
        b1.setClusterId("B1");
        CombinedPass b2 = pass(2, 20.0);
        b2.setClusterId("B2");
        WorkflowTask filter = new WorkflowTask("05 Filter", WorkflowTask.TaskType.PRE_FILTER);
        filter.setSourceDatabank("g05_raw");
        filter.setTargetDatabank("g05_pick");
        filter.setDeleteFailed(true);
        filter.setFilterConditions(List.of(new FilterCondition(
                FilterCondition.Metric.BT_PROFIT_FACTOR,
                FilterCondition.Operator.GREATER_EQUAL, 9.0)));

        List<CombinedPass> routed = manager.processTaskDatabanks(filter, List.of(b1, b2));

        assertEquals(2, routed.size());
        assertEquals("B1", routed.get(0).getClusterId());
        assertEquals("B2", routed.get(1).getClusterId());
        assertFalse(DatabankManager.shouldHaltChainAfterPreFilter(filter, routed));
    }

    @Test
    public void retesterEmptyOutputStaysEmpty() {
        DatabankManager manager = new DatabankManager();
        WorkflowTask retest = new WorkflowTask("Smoke-Kill", WorkflowTask.TaskType.RETESTER);
        retest.setSourceDatabank("k12");
        retest.setTargetDatabank("k13");
        retest.setDeleteFailed(true);
        retest.setFilterConditions(List.of(new FilterCondition(
                FilterCondition.Metric.LT_NET_PROFIT,
                FilterCondition.Operator.GREATER_THAN, 0)));

        assertTrue(manager.processTaskDatabanks(retest, List.of(pass(1, -50.0))).isEmpty());
        assertTrue(manager.getDatabank("k13").isEmpty());
    }

    @Test
    public void projectSwitchClearsCustomDatabanksAndSnapshotsAreIsolated() {
        DatabankManager manager = new DatabankManager();
        assertTrue(manager.createDatabank("data1"));
        manager.setDatabankContent("data1", List.of(pass(1, 1.0)));

        CustomProject emptyProject = new CustomProject("Other", "EA.ex5", "EURUSD", "H1");
        manager.loadFromProject(emptyProject);

        assertFalse(manager.getDatabankNames().contains("data1"));
        List<CombinedPass> snapshot = manager.getDatabank("Results");
        snapshot.add(pass(5, 5.0));
        assertTrue(manager.getDatabank("Results").isEmpty());
    }

    @Test
    public void loadingProjectRepairsLegacyOptimizerDateRanges() {
        CustomProject project = new CustomProject("Imported", "EA.ex5", "AUDCAD", "M5");
        WorkflowTask optimizer = new WorkflowTask("Optimizer", WorkflowTask.TaskType.OPTIMIZER);
        optimizer.setTargetDatabank("raw");
        optimizer.setStartDate("2022-08-01");
        optimizer.setEndDate("2025-08-01");
        optimizer.setOptimizerForwardMode(1);
        optimizer.setOptimizerForwardDate("2024-02-01");
        WorkflowTask quality = new WorkflowTask("Quality", WorkflowTask.TaskType.PRE_FILTER);
        quality.setSourceDatabank("raw");
        quality.setTargetDatabank("quality");
        WorkflowTask isOos = new WorkflowTask("IS/OOS", WorkflowTask.TaskType.PRE_FILTER);
        isOos.setSourceDatabank("quality");
        isOos.setTargetDatabank("is_oos");
        WorkflowTask shortlist = new WorkflowTask("Shortlist", WorkflowTask.TaskType.DIVERSITY_FILTER);
        shortlist.setSourceDatabank("is_oos");
        shortlist.setTargetDatabank("shortlist");
        project.setTasks(List.of(optimizer, quality, isOos, shortlist));

        OptimizationResult.Pass bt = new OptimizationResult.Pass();
        bt.setPassNumber(2059);
        bt.setFromDate("2022.08.01");
        bt.setToDate("2025.08.01");
        bt.setProfit(223.98);
        OptimizationResult.Pass fw = new OptimizationResult.Pass();
        fw.setPassNumber(2059);
        fw.setFromDate("2022.08.01");
        fw.setToDate("2025.08.01");
        fw.setProfit(212.19);
        CombinedPass imported = new CombinedPass(bt, fw, 33.6, 0.79, "imported");
        project.setDatabanks(Map.of(
                "raw", List.of(imported),
                "quality", List.of(imported.copy()),
                "is_oos", List.of(imported.copy()),
                "shortlist", List.of(imported.copy())));

        DatabankManager manager = new DatabankManager();
        manager.loadFromProject(project);

        CombinedPass repaired = manager.getDatabank("raw").get(0);
        assertEquals("2022-08-01 - 2024-01-31", repaired.getBtDateRange());
        assertEquals("2024-02-01 - 2025-08-01", repaired.getFwDateRange());
        assertEquals("2022-08-01 - 2024-01-31", manager.getDatabank("quality").get(0).getBtDateRange());
        assertEquals("2024-02-01 - 2025-08-01", manager.getDatabank("quality").get(0).getFwDateRange());
        assertEquals("2022-08-01 - 2024-01-31", manager.getDatabank("is_oos").get(0).getBtDateRange());
        assertEquals("2024-02-01 - 2025-08-01", manager.getDatabank("is_oos").get(0).getFwDateRange());
        assertEquals("2022-08-01 - 2024-01-31", manager.getDatabank("shortlist").get(0).getBtDateRange());
        assertEquals("2024-02-01 - 2025-08-01", manager.getDatabank("shortlist").get(0).getFwDateRange());
    }

    @Test
    public void databankSnapshotsDeepCopyMutablePasses() {
        DatabankManager manager = new DatabankManager();
        manager.setDatabankContent("Results", List.of(pass(1, 100.0)));

        List<CombinedPass> snapshot = manager.getDatabank("Results");
        snapshot.get(0).getBacktestPass().setProfit(999.0);
        OptimizationResult.Pass longterm = new OptimizationResult.Pass();
        longterm.setPassNumber(1);
        longterm.setProfit(777.0);
        snapshot.get(0).setLongtermPass(longterm);

        CombinedPass stored = manager.getDatabank("Results").get(0);
        assertEquals(100.0, stored.getBtProfit(), 0.0);
        assertNull(stored.getLongtermPass());
    }

    @Test
    public void databankSnapshotsDeepCopyEquityHistoryArrays() {
        DatabankManager manager = new DatabankManager();
        CombinedPass original = pass(1, 100.0);
        double[] sourcePoint = new double[]{1.0, 10_000.0, 10_000.0};
        original.getBacktestPass().setEquityHistory(List.of(sourcePoint));
        manager.setDatabankContent("Results", List.of(original));

        sourcePoint[1] = -1.0;
        List<CombinedPass> snapshot = manager.getDatabank("Results");
        snapshot.get(0).getBacktestPass().getEquityHistory().get(0)[1] = 99_999.0;

        assertEquals(10_000.0,
                manager.getDatabank("Results").get(0).getBacktestPass()
                        .getEquityHistory().get(0)[1],
                0.0);
    }

    @Test
    public void separateTargetAndReturnedOutputAreObjectIsolated() {
        DatabankManager manager = new DatabankManager();
        manager.setDatabankContent("Results", List.of(pass(1, 100.0)));
        WorkflowTask task = new WorkflowTask("Copy", WorkflowTask.TaskType.RETESTER);
        task.setSourceDatabank("Results");
        task.setTargetDatabank("Final");

        List<CombinedPass> output = manager.processTaskDatabanks(task, manager.getDatabank("Results"));
        output.get(0).getBacktestPass().setProfit(555.0);

        assertEquals(100.0, manager.getDatabank("Results").get(0).getBtProfit(), 0.0);
        assertEquals(100.0, manager.getDatabank("Final").get(0).getBtProfit(), 0.0);
    }

    @Test
    public void filterPassesIsPureAndReturnsCopies() {
        DatabankManager manager = new DatabankManager();
        manager.setDatabankContent("Results", Arrays.asList(pass(1, 100.0), pass(2, -5.0)));
        WorkflowTask task = new WorkflowTask("Export filter", WorkflowTask.TaskType.PORTFOLIO_EXPORT);
        task.addFilterCondition(new FilterCondition(FilterCondition.Metric.BT_NET_PROFIT,
                FilterCondition.Operator.GREATER_THAN, 0.0));

        List<CombinedPass> filtered = manager.filterPasses(task, manager.getDatabank("Results"));
        assertEquals(1, filtered.size());
        filtered.get(0).getBacktestPass().setProfit(999.0);

        assertEquals(2, manager.getDatabank("Results").size());
        assertEquals(100.0, manager.getDatabank("Results").get(0).getBtProfit(), 0.0);
    }

    @Test
    public void preFilterEmptyingOneClusterDoesNotStealAnotherChampion() {
        DatabankManager manager = new DatabankManager();
        CombinedPass clusterB1 = pass(1, -20.0);
        clusterB1.setClusterId("B1");
        CombinedPass clusterB2 = pass(2, 80.0);
        clusterB2.setClusterId("B2");

        WorkflowTask filter = new WorkflowTask("09 Entry — Filter", WorkflowTask.TaskType.PRE_FILTER);
        filter.setSourceDatabank("g09_raw");
        filter.setTargetDatabank("g09_pick");
        filter.setDeleteFailed(true);
        filter.setFilterConditions(List.of(new FilterCondition(
                FilterCondition.Metric.BT_NET_PROFIT,
                FilterCondition.Operator.GREATER_THAN, 0.0)));

        List<CombinedPass> routed = manager.processTaskDatabanks(filter, List.of(clusterB1, clusterB2));

        assertEquals(1, routed.size());
        assertEquals(2, routed.get(0).getPassNumber());
        assertEquals("B2", routed.get(0).getClusterId());
        assertEquals(1, manager.getDatabank("g09_pick").size());
        assertEquals("B2", manager.getDatabank("g09_pick").get(0).getClusterId());
    }

    @Test
    public void diversityAboveClusterCapDoesNotStampEvenWhenFlagDefaultsOn() {
        DatabankManager manager = new DatabankManager();
        List<CombinedPass> survivors = new java.util.ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            survivors.add(pass(i, i * 10.0));
        }
        WorkflowTask shortlist = new WorkflowTask("Shortlist", WorkflowTask.TaskType.DIVERSITY_FILTER);
        shortlist.setSourceDatabank("g01_grid_is_oos");
        shortlist.setTargetDatabank("g01_grid_shortlist");
        shortlist.setDiversityMaxStrategies(100);
        shortlist.setDeleteFailed(true);

        List<CombinedPass> routed = manager.processTaskDatabanks(shortlist, survivors);

        assertEquals(12, routed.size());
        assertTrue(routed.stream().allMatch(candidate -> candidate.getClusterId() == null
                || candidate.getClusterId().isBlank()));
    }

    @Test
    public void wideDiversityShortlistDoesNotStampClusterIds() {
        DatabankManager manager = new DatabankManager();
        List<CombinedPass> survivors = new java.util.ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            survivors.add(pass(i, i * 10.0));
        }
        WorkflowTask shortlist = new WorkflowTask("01 Grid-Fundament — Diversität (Shortlist 100)",
                WorkflowTask.TaskType.DIVERSITY_FILTER);
        shortlist.setSourceDatabank("g01_grid_is_oos");
        shortlist.setTargetDatabank("g01_grid_shortlist");
        shortlist.setDiversityMaxStrategies(100);
        shortlist.setDiversityStampClusterIds(false);
        shortlist.setDeleteFailed(true);

        List<CombinedPass> routed = manager.processTaskDatabanks(shortlist, survivors);

        assertEquals(12, routed.size());
        assertTrue(routed.stream().allMatch(candidate -> candidate.getClusterId() == null
                || candidate.getClusterId().isBlank()));
        assertEquals(12, manager.getDatabank("g01_grid_shortlist").size());
    }

    @Test
    public void qualityPreFilterDoesNotCapOrStampBeforeFormDiversity() {
        DatabankManager manager = new DatabankManager();
        List<CombinedPass> survivors = new java.util.ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            survivors.add(pass(i, i * 10.0));
        }
        WorkflowTask filter = new WorkflowTask("01 Grid-Fundament — Trade/Qualitätsfilter",
                WorkflowTask.TaskType.PRE_FILTER);
        filter.setSourceDatabank("g01_grid_raw");
        filter.setTargetDatabank("g01_grid_quality");
        filter.setDeleteFailed(true);
        filter.setFilterConditions(List.of());

        List<CombinedPass> routed = manager.processTaskDatabanks(filter, survivors);

        assertEquals(12, routed.size());
        assertTrue(routed.stream().allMatch(pass -> pass.getClusterId() == null
                || pass.getClusterId().isBlank()));
        assertEquals(12, manager.getDatabank("g01_grid_quality").size());
    }

    @Test
    public void retesterCanMarkAClusterDeadWhenItIsEmptied() {
        DatabankManager manager = new DatabankManager();
        CombinedPass live = pass(1, 100.0);
        live.setClusterId("B1");
        CombinedPass dying = pass(2, -40.0);
        dying.setClusterId("B2");
        manager.setDatabankContent("k12", List.of(live, dying));

        WorkflowTask retest = new WorkflowTask("Smoke-Kill", WorkflowTask.TaskType.RETESTER);
        retest.setSourceDatabank("k12");
        retest.setTargetDatabank("k13");
        retest.setDeleteFailed(true);
        retest.setFilterConditions(List.of(new FilterCondition(
                FilterCondition.Metric.BT_NET_PROFIT,
                FilterCondition.Operator.GREATER_THAN, 0.0)));

        manager.processTaskDatabanks(retest, List.of(live, dying));

        CustomProject project = new CustomProject("Tick-Kill", "EA.ex5", "GBPJPY", "M5");
        project.addTask(retest);
        manager.saveToProject(project, true);

        ClusterCensus.ClusterLine dead = project.getClusterCensus().findLine("B2");
        assertNotNull(dead);
        assertEquals(ClusterCensus.ClusterStatus.DEAD, dead.getStatus());
        assertEquals("Smoke-Kill", dead.getDiedAtStage());
        ClusterCensus.ClusterStageSnapshot k13 = dead.getPerStage().stream()
                .filter(snapshot -> "k13".equals(snapshot.getDatabankName()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, k13.getLiveCount());
        assertEquals(-1, k13.getChampionPassNumber());
        assertEquals(ClusterCensus.StageVerdict.DIED, k13.getVerdict());

        ClusterCensus.ClusterLine stillLive = project.getClusterCensus().findLine("B1");
        assertEquals(ClusterCensus.ClusterStatus.LIVE, stillLive.getStatus());
        assertNull(stillLive.getDiedAtStage());
    }

    @Test
    public void structureCanPersistWithoutDatabankContents() {
        DatabankManager manager = new DatabankManager();
        manager.createDatabank("data1");
        manager.setDatabankContent("data1", List.of(pass(1, 1.0)));
        CustomProject project = new CustomProject("P", "EA.ex5", "EURUSD", "H1");

        manager.saveToProject(project, false);

        assertTrue(project.getDatabanks().containsKey("data1"));
        assertTrue(project.getDatabanks().get("data1").isEmpty());
    }

    @Test
    public void filtersFailClosedForInvalidNumbersAndZeroDivisors() {
        CombinedPass candidate = pass(1, 100.0);
        candidate.getBacktestPass().setProfitFactor(Double.POSITIVE_INFINITY);
        candidate.getBacktestPass().setDrawdownPercent(0.0);

        FilterCondition infiniteMetric = new FilterCondition(FilterCondition.Metric.BT_PROFIT_FACTOR,
                FilterCondition.Operator.GREATER_THAN, 1.0);
        assertFalse(infiniteMetric.evaluate(candidate));

        FilterCondition zeroDivisor = new FilterCondition(FilterCondition.Metric.BT_RET_DD_RATIO,
                FilterCondition.Operator.GREATER_THAN, 1.0);
        assertFalse(zeroDivisor.evaluate(candidate));

        FilterCondition invalidThreshold = new FilterCondition(FilterCondition.Metric.BT_NET_PROFIT,
                FilterCondition.Operator.GREATER_THAN, Double.NaN);
        assertFalse(invalidThreshold.evaluate(candidate));
        assertFalse(invalidThreshold.evaluate(null));
    }

    @Test
    public void clearAllRetainsCustomDatabankTabsAndEmptiesContent() {
        DatabankManager manager = new DatabankManager();
        manager.createDatabank("CustomDB");
        manager.setDatabankContent("Results", List.of(pass(1, 100.0)));
        manager.setDatabankContent("CustomDB", List.of(pass(2, 200.0)));

        manager.clearAll();

        assertTrue("CustomDB tab must be retained", manager.getDatabankNames().contains("CustomDB"));
        assertTrue("Results must be empty", manager.getDatabank("Results").isEmpty());
        assertTrue("CustomDB must be empty", manager.getDatabank("CustomDB").isEmpty());
    }

    @Test
    public void galleryLookupCopiesOnlyMatchingPassNumbers() {
        DatabankManager manager = new DatabankManager();
        CombinedPass first = pass(1, 10.0);
        CombinedPass second = pass(2, 20.0);
        CombinedPass third = pass(3, 30.0);
        manager.setDatabankContent("data0", List.of(first, second, third));

        CombinedPass renamedReference = pass(2, 999.0);
        renamedReference.setStrategyName("Renamed strategy");
        List<CombinedPass> matches = manager.getDatabankMatches("data0", List.of(renamedReference));

        assertEquals(1, matches.size());
        assertEquals(2, matches.get(0).getPassNumber());
        assertEquals(20.0, matches.get(0).getBtProfit(), 0.001);
        assertNotSame(second, matches.get(0));
    }

    @Test
    public void sequentialClusterRunsKeepOverlappingPassNumbers() {
        DatabankManager manager = new DatabankManager();
        CombinedPass b1 = pass(5, 10.0);
        b1.setStrategyName("Strat 5");
        b1.setClusterId("B1");
        CombinedPass b7 = pass(5, 99.0);
        b7.setStrategyName("Strat 5");
        b7.setClusterId("B7");

        WorkflowTask task = new WorkflowTask("02 Optimizer", WorkflowTask.TaskType.OPTIMIZER);
        task.setSourceDatabank("g01_pick");
        task.setTargetDatabank("g02_pick");

        List<CombinedPass> routed = manager.processTaskDatabanks(task, List.of(b1, b7));
        assertEquals(2, routed.size());
        assertEquals(2, manager.getDatabank("g02_pick").size());
        assertEquals("B1", routed.get(0).getClusterId());
        assertEquals("B7", routed.get(1).getClusterId());

        CombinedPass refB1 = pass(5, 0.0);
        refB1.setStrategyName("Strat 5");
        refB1.setClusterId("B1");
        List<CombinedPass> matches = manager.getDatabankMatches("g02_pick", List.of(refB1));
        assertEquals(1, matches.size());
        assertEquals("B1", matches.get(0).getClusterId());
        assertEquals(10.0, matches.get(0).getBtProfit(), 0.001);
    }

    private static CombinedPass pass(int number, double profit) {
        OptimizationResult.Pass bt = new OptimizationResult.Pass();
        bt.setPassNumber(number);
        bt.setProfit(profit);
        bt.setTotalTrades(100);
        bt.setProfitFactor(1.5);
        bt.setDrawdownPercent(10.0);
        bt.setRecoveryFactor(2.0);
        return new CombinedPass(bt, null, profit, 1.0, "test");
    }
}
