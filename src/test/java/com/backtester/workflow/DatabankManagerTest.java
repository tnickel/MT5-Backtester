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

        WorkflowTask task = new WorkflowTask("Retest", WorkflowTask.TaskType.LONGTERM_RETEST);
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

        WorkflowTask task = new WorkflowTask("Empty retest", WorkflowTask.TaskType.LONGTERM_RETEST);
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
