package com.backtester.workflow;

import com.backtester.report.OptimizationResult;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WorkflowDownstreamInvalidationTest {

    @Test
    public void changingFilterClearsThisAndLaterTargets() {
        CustomProject project = new CustomProject("P", "EA", "GBPJPY", "M5");
        WorkflowTask first = new WorkflowTask("Filter", WorkflowTask.TaskType.PRE_FILTER);
        first.setSourceDatabank("raw");
        first.setTargetDatabank("pick");
        first.setStatus(WorkflowTask.TaskStatus.COMPLETED);
        WorkflowTask second = new WorkflowTask("Retest", WorkflowTask.TaskType.RETESTER);
        second.setSourceDatabank("pick");
        second.setTargetDatabank("tick");
        second.setStatus(WorkflowTask.TaskStatus.COMPLETED);
        project.addTask(first);
        project.addTask(second);

        DatabankManager manager = new DatabankManager();
        manager.setDatabankContent("pick", java.util.List.of(samplePass(1)));
        manager.setDatabankContent("tick", java.util.List.of(samplePass(2)));

        first.getFilterConditions().add(new FilterCondition(
                FilterCondition.Metric.BT_NET_PROFIT,
                FilterCondition.Operator.GREATER_THAN,
                0.0));

        int cleared = WorkflowDownstreamInvalidation.invalidateFromTask(project, manager, first);

        assertEquals(2, cleared);
        assertTrue(manager.getDatabank("pick").isEmpty());
        assertTrue(manager.getDatabank("tick").isEmpty());
        assertEquals(WorkflowTask.TaskStatus.PENDING, first.getStatus());
        assertEquals(WorkflowTask.TaskStatus.PENDING, second.getStatus());
    }

    @Test
    public void executionSignatureChangesWhenDatesChange() {
        WorkflowTask task = new WorkflowTask("Retest", WorkflowTask.TaskType.RETESTER);
        task.setStartDate("2024-08-01");
        String before = WorkflowDownstreamInvalidation.executionSignature(task);
        task.setStartDate("2024-09-01");
        String after = WorkflowDownstreamInvalidation.executionSignature(task);
        assertTrue(!before.equals(after));
    }

    private static CombinedPass samplePass(int number) {
        Pass bt = new OptimizationResult.Pass();
        bt.setPassNumber(number);
        bt.setProfit(100.0);
        bt.setTotalTrades(100);
        bt.setProfitFactor(1.5);
        bt.setDrawdownPercent(10.0);
        bt.setRecoveryFactor(2.0);
        return new CombinedPass(bt, null, number * 10.0, 1.0, "test");
    }
}
