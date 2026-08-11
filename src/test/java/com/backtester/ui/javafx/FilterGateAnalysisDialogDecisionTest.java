package com.backtester.ui.javafx;

import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import com.backtester.workflow.FilterGateAnalysisService;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class FilterGateAnalysisDialogDecisionTest {

    @Test
    public void recommendsOffWhenOffMedianClearlyBetter() {
        List<CombinedPass> passes = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            passes.add(scored(i + 1, 50 - i, "true"));
        }
        for (int i = 0; i < 6; i++) {
            passes.add(scored(100 + i, 90 - i, "false"));
        }
        FilterGateAnalysisService.FilterGateAnalysis analysis = FilterGateAnalysisService.analyze(
                passes, "Inp_Use_X",
                FilterGateAnalysisService.DataSource.OPTIMIZER_REPORT,
                "path", "",
                5, 10, 0.10, List.of(), List.of("Inp_Use_X"));

        FilterGateAnalysisDialog.NextStepDecision decision =
                FilterGateAnalysisDialog.decideNextStepFilter(analysis);
        assertTrue(decision.badgeText().contains("FILTER AUS"));
    }

    @Test
    public void recommendsUnclearWhenClose() {
        List<CombinedPass> passes = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            passes.add(scored(i + 1, 80, "true"));
            passes.add(scored(100 + i, 79, "false"));
        }
        FilterGateAnalysisService.FilterGateAnalysis analysis = FilterGateAnalysisService.analyze(
                passes, "Inp_Use_X",
                FilterGateAnalysisService.DataSource.OPTIMIZER_REPORT,
                "path", "",
                5, 10, 0.10, List.of(), List.of("Inp_Use_X"));

        FilterGateAnalysisDialog.NextStepDecision decision =
                FilterGateAnalysisDialog.decideNextStepFilter(analysis);
        assertTrue(decision.badgeText().contains("UNKLAR"));
    }

    private static CombinedPass scored(int passNumber, double score, String gate) {
        Pass bt = new Pass();
        bt.setPassNumber(passNumber);
        bt.setProfit(score * 10);
        bt.setTotalTrades(100);
        bt.setDrawdownPercent(10);
        bt.setParameter("Inp_Use_X", gate);
        return new CombinedPass(bt, null, score, 1.0, "t");
    }
}