package com.backtester.report;

import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import com.backtester.ui.javafx.StrategyEvaluatorDialog;
import com.backtester.ui.javafx.StrategyEvaluatorDialog.Evaluation;
import org.junit.Test;
import static org.junit.Assert.*;

public class StrategyEvaluatorTest {

    private Pass createPass(int passNo, double profit, int trades, double pf, double ddPercent, double expectedPayoff, double recovery, double sharpe) {
        Pass p = new Pass();
        p.setPassNumber(passNo);
        p.setProfit(profit);
        p.setTotalTrades(trades);
        p.setProfitFactor(pf);
        p.setDrawdownPercent(ddPercent);
        p.setExpectedPayoff(expectedPayoff);
        p.setRecoveryFactor(recovery);
        p.setSharpeRatio(sharpe);
        return p;
    }

    @Test
    public void testEvaluatePass_StatisticallyIrrelevant() {
        Pass bt = createPass(1, 500.0, 5, 2.0, 10.0, 5.0, 4.0, 2.0); // 5 trades (too low)
        Pass fw = createPass(1, 400.0, 12, 1.8, 8.0, 4.5, 3.5, 1.8);
        CombinedPass cp = new CombinedPass(bt, fw, 80.0, 0.8, "");
        
        Evaluation eval = StrategyEvaluatorDialog.evaluatePass(cp);
        assertEquals("BAD", eval.rating);
        assertTrue(eval.remark.contains("zu wenig Trades"));
    }

    @Test
    public void testEvaluatePass_HighDrawdown() {
        Pass bt = createPass(1, 1000.0, 120, 1.8, 55.0, 5.0, 4.0, 2.0); // 55% drawdown
        Pass fw = createPass(1, 500.0, 80, 1.5, 10.0, 4.5, 3.5, 1.8);
        CombinedPass cp = new CombinedPass(bt, fw, 50.0, 0.5, "");
        
        Evaluation eval = StrategyEvaluatorDialog.evaluatePass(cp);
        assertEquals("BAD", eval.rating);
        assertTrue(eval.remark.contains("Extrem hoher Drawdown"));
    }

    @Test
    public void testEvaluatePass_NotProfitable() {
        Pass bt = createPass(1, -100.0, 50, 0.8, 10.0, -2.0, 0.0, -0.5); // negative profit
        Pass fw = createPass(1, 200.0, 40, 1.5, 5.0, 2.5, 2.0, 1.0);
        CombinedPass cp = new CombinedPass(bt, fw, 20.0, 0.0, "");
        
        Evaluation eval = StrategyEvaluatorDialog.evaluatePass(cp);
        assertEquals("BAD", eval.rating);
        assertTrue(eval.remark.contains("Nicht profitabel"));
    }

    @Test
    public void testEvaluatePass_ModerateTradesWarning() {
        Pass bt = createPass(1, 300.0, 25, 2.1, 8.0, 4.0, 3.0, 1.5); // 25 trades
        Pass fw = createPass(1, 400.0, 30, 2.2, 5.0, 4.5, 3.5, 1.8);
        CombinedPass cp = new CombinedPass(bt, fw, 70.0, 1.33, "");
        
        Evaluation eval = StrategyEvaluatorDialog.evaluatePass(cp);
        assertEquals("WARNING", eval.rating);
        assertTrue(eval.remark.contains("geringe Tradeanzahl"));
    }

    @Test
    public void testEvaluatePass_LowConsistencyWarning() {
        Pass bt = createPass(1, 2000.0, 80, 2.5, 12.0, 8.0, 6.0, 2.5);
        Pass fw = createPass(1, 400.0, 50, 1.1, 14.0, 1.5, 1.2, 0.8); // profit drops from 2000 to 400 (consistency = 0.2)
        CombinedPass cp = new CombinedPass(bt, fw, 45.0, 0.2, "");
        
        Evaluation eval = StrategyEvaluatorDialog.evaluatePass(cp);
        assertEquals("WARNING", eval.rating);
        assertTrue(eval.remark.contains("Leistungseinbruch"));
    }

    @Test
    public void testEvaluatePass_ExcellentStrategy() {
        Pass bt = createPass(1, 1500.0, 100, 2.2, 8.0, 6.0, 5.0, 2.2);
        Pass fw = createPass(1, 1500.0, 90, 2.1, 7.0, 5.5, 4.8, 2.0); // excellent stats and high consistency
        CombinedPass cp = new CombinedPass(bt, fw, 95.0, 1.0, "");
        
        Evaluation eval = StrategyEvaluatorDialog.evaluatePass(cp);
        assertEquals("EXCELLENT", eval.rating);
        assertTrue(eval.remark.contains("Exzellent"));
    }

    @Test
    public void testEvaluatePass_SolidStrategy() {
        Pass bt = createPass(1, 800.0, 60, 1.7, 18.0, 4.0, 3.5, 1.6);
        Pass fw = createPass(1, 700.0, 55, 1.6, 16.0, 3.8, 3.2, 1.4); // solid stats
        CombinedPass cp = new CombinedPass(bt, fw, 75.0, 0.875, "");
        
        Evaluation eval = StrategyEvaluatorDialog.evaluatePass(cp);
        assertEquals("GOOD", eval.rating);
        assertTrue(eval.remark.contains("Solide & robuste Strategie"));
    }

    @Test
    public void testCalculateRobustnessIndex() {
        // Test case 1: High trades, excellent recovery factor, perfect forward
        Pass bt = createPass(1, 1000.0, 200, 2.0, 10.0, 5.0, 3.0, 2.0); // 200 trades, 3.0 recovery factor
        Pass fw = createPass(1, 1000.0, 150, 2.0, 10.0, 5.0, 3.5, 2.0); // 3.5 recovery factor (better)
        CombinedPass cp = new CombinedPass(bt, fw, 90.0, 1.0, "");
        
        double ri = StrategyEvaluatorDialog.calculateRobustnessIndex(cp);
        // Trades factor = 1 - e^(-200/80) = 0.9179
        // Consistency factor = 1.0 (since fw recovery >= bt recovery)
        // Recovery factor bt = 3.0
        // Expected RI = 3.0 * 0.9179 * 1.0 = 2.75
        assertEquals(2.75, ri, 0.05);

        // Test case 2: Low trades (only 5 trades)
        Pass btLow = createPass(2, 200.0, 5, 2.0, 10.0, 5.0, 3.0, 2.0); // 5 trades, 3.0 recovery
        CombinedPass cpLow = new CombinedPass(btLow, null, 90.0, 1.0, "");
        double riLow = StrategyEvaluatorDialog.calculateRobustnessIndex(cpLow);
        // Trades factor = 1 - e^(-5/80) = 0.0606
        // Consistency factor = 0.7 (no forward test)
        // Recovery factor bt = 3.0
        // Expected RI = 3.0 * 0.0606 * 0.7 = 0.127
        assertEquals(0.127, riLow, 0.01);
    }
}
