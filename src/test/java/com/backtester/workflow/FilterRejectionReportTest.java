package com.backtester.workflow;

import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FilterRejectionReportTest {

    @Test
    public void namesTheScoreLeaderThatTheFilterRemovedAndItsReplacement() {
        WorkflowTask filter = qualityFilter();
        CombinedPass leader = pass(1, 80.0, 1.14);
        CombinedPass survivor = pass(2, 70.0, 1.30);
        List<CombinedPass> candidates = List.of(leader, survivor);

        String note = FilterRejectionReport.describeDroppedLeader(
                filter, candidates, List.of(survivor));

        assertTrue(note, note.contains("Pass #1"));
        assertTrue(note, note.contains("80.0"));
        assertTrue(note, note.contains("1.14"));
        assertTrue(note, note.contains("Pass #2"));
    }

    @Test
    public void staysSilentWhenTheLeaderSurvives() {
        WorkflowTask filter = qualityFilter();
        CombinedPass leader = pass(1, 80.0, 1.40);
        CombinedPass other = pass(2, 70.0, 1.30);

        assertEquals("", FilterRejectionReport.describeDroppedLeader(
                filter, List.of(leader, other), List.of(leader, other)));
    }

    @Test
    public void saysSoWhenNothingSurvivedAtAll() {
        WorkflowTask filter = qualityFilter();
        CombinedPass leader = pass(1, 80.0, 1.01);

        String note = FilterRejectionReport.describeDroppedLeader(
                filter, List.of(leader), List.of());

        assertTrue(note, note.contains("kein Pass"));
    }

    @Test
    public void routingStampsTheNoteOnTheTask() {
        WorkflowTask filter = qualityFilter();
        filter.setSourceDatabank("g03_raw");
        filter.setTargetDatabank("g03_pick");
        filter.setDeleteFailed(true);
        DatabankManager manager = new DatabankManager();

        manager.processTaskDatabanks(filter, List.of(pass(1, 80.0, 1.14), pass(2, 70.0, 1.30)));

        assertTrue(filter.getFilterRejectionNote(),
                filter.getFilterRejectionNote().contains("Pass #1"));
    }

    private static WorkflowTask qualityFilter() {
        WorkflowTask filter = new WorkflowTask("03 Qualitätsfilter", WorkflowTask.TaskType.PRE_FILTER);
        filter.setFilterConditions(List.of(new FilterCondition(
                FilterCondition.Metric.FW_PROFIT_FACTOR,
                FilterCondition.Operator.GREATER_EQUAL, 1.15)));
        return filter;
    }

    private static CombinedPass pass(int number, double score, double forwardProfitFactor) {
        Pass backtest = new Pass();
        backtest.setPassNumber(number);
        backtest.setProfit(1000);
        backtest.setTotalTrades(900);
        Pass forward = new Pass();
        forward.setPassNumber(number);
        forward.setProfit(300);
        forward.setTotalTrades(400);
        forward.setProfitFactor(forwardProfitFactor);
        CombinedPass combined = new CombinedPass(backtest, forward, score, 1.0, "");
        combined.setStrategyName("Strat " + number);
        return combined;
    }
}
