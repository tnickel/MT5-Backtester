package com.backtester.workflow;

import com.backtester.report.OptimizationResult;
import com.backtester.report.OptimizationResult.CombinedPass;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class DatabankManagerTest {

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
