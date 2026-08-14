package com.backtester.report;

import org.junit.Before;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class OptimizationResultTest {

    private OptimizationResult result;

    @Before
    public void setUp() {
        result = new OptimizationResult();
    }

    @Test
    public void testGetBestByProfit() {
        OptimizationResult.Pass p1 = new OptimizationResult.Pass();
        p1.setPassNumber(1);
        p1.setProfit(100.0);

        OptimizationResult.Pass p2 = new OptimizationResult.Pass();
        p2.setPassNumber(2);
        p2.setProfit(500.0);

        OptimizationResult.Pass p3 = new OptimizationResult.Pass();
        p3.setPassNumber(3);
        p3.setProfit(-50.0);

        result.addPass(p1);
        result.addPass(p2);
        result.addPass(p3);

        OptimizationResult.Pass best = result.getBestByProfit();
        assertNotNull(best);
        assertEquals(2, best.getPassNumber());
        assertEquals(500.0, best.getProfit(), 0.001);
    }

    @Test
    public void testGetBestByCriterion() {
        OptimizationResult.Pass p1 = new OptimizationResult.Pass();
        p1.setPassNumber(1);
        p1.setBalance(1000);
        p1.setProfitFactor(1.5);
        p1.setDrawdownPercent(10.0); // Less drawdown is better for criterion 3 (-drawdown)

        OptimizationResult.Pass p2 = new OptimizationResult.Pass();
        p2.setPassNumber(2);
        p2.setBalance(1200);
        p2.setProfitFactor(1.2);
        p2.setDrawdownPercent(25.0);

        result.addPass(p1);
        result.addPass(p2);

        // 0 = Balance
        assertEquals(2, result.getBestByCriterion(0).getPassNumber());
        
        // 1 = ProfitFactor
        assertEquals(1, result.getBestByCriterion(1).getPassNumber());
        
        // 3 = DrawdownPercent (lower is better, the comparator uses -drawdownPercent)
        assertEquals(1, result.getBestByCriterion(3).getPassNumber());
    }

    @Test
    public void testBuildCombinedPassesEmptyForward() {
        OptimizationResult.Pass btPass = new OptimizationResult.Pass();
        btPass.setPassNumber(1);
        btPass.setProfit(100.0);
        result.addPass(btPass);

        // requireForward = true -> should be empty
        List<OptimizationResult.CombinedPass> combinedTrue = result.buildCombinedPasses(true);
        assertTrue(combinedTrue.isEmpty());

        // requireForward = false -> should have 1 pass
        List<OptimizationResult.CombinedPass> combinedFalse = result.buildCombinedPasses(false);
        assertEquals(1, combinedFalse.size());
        assertEquals(1, combinedFalse.get(0).getPassNumber());
        assertNull(combinedFalse.get(0).getForwardPass());
    }

    @Test
    public void testBuildCombinedPassesWithForward() {
        OptimizationResult.Pass btPass = new OptimizationResult.Pass();
        btPass.setPassNumber(1);
        btPass.setProfit(100.0);
        btPass.setTotalTrades(100);
        btPass.setRecoveryFactor(4.0);
        result.addPass(btPass);

        OptimizationResult.Pass fwPass = new OptimizationResult.Pass();
        fwPass.setPassNumber(1);
        fwPass.setProfit(120.0);
        fwPass.setTotalTrades(50);
        fwPass.setRecoveryFactor(4.8);
        result.addForwardPass(fwPass);

        List<OptimizationResult.CombinedPass> combined = result.buildCombinedPasses(true);
        assertEquals(1, combined.size());
        OptimizationResult.CombinedPass cp = combined.get(0);
        
        assertEquals(1, cp.getPassNumber());
        assertEquals(100.0, cp.getBtProfit(), 0.001);
        assertEquals(120.0, cp.getFwProfit(), 0.001);
        
        // Profit & Recovery consistency = (120/100 + 4.8/4.0) / 2 = 1.2
        assertEquals(1.2, cp.getConsistency(), 0.001);
        
        // The score should be calculated correctly, > 0
        assertTrue(cp.getScore() > 0.0);
    }

    @Test
    public void testPassParameters() {
        OptimizationResult.Pass pass = new OptimizationResult.Pass();
        pass.setParameter("TakeProfit", "50");
        pass.setParameter("StopLoss", "30");
        
        assertEquals("50", pass.getParameter("TakeProfit"));
        assertEquals("30", pass.getParameter("StopLoss"));
        assertEquals("", pass.getParameter("MissingParam"));
    }

    @Test
    public void testContinuousFwTradeScoring() {
        // Backtest pass
        OptimizationResult.Pass btPass = new OptimizationResult.Pass();
        btPass.setPassNumber(1);
        btPass.setProfit(1000.0);
        btPass.setTotalTrades(300);
        btPass.setProfitFactor(2.0);
        btPass.setRecoveryFactor(3.0);
        btPass.setDrawdownPercent(10.0);
        btPass.setBalance(11000.0);
        
        // Scenario 1: Forward pass with 20 trades
        OptimizationResult.Pass fwPassLow = new OptimizationResult.Pass();
        fwPassLow.setPassNumber(1);
        fwPassLow.setProfit(1000.0);
        fwPassLow.setTotalTrades(20);
        fwPassLow.setProfitFactor(2.0);
        fwPassLow.setRecoveryFactor(3.0);
        fwPassLow.setDrawdownPercent(10.0);

        // Scenario 2: Forward pass with 150 trades
        OptimizationResult.Pass fwPassMed = new OptimizationResult.Pass();
        fwPassMed.setPassNumber(1);
        fwPassMed.setProfit(1000.0);
        fwPassMed.setTotalTrades(150);
        fwPassMed.setProfitFactor(2.0);
        fwPassMed.setRecoveryFactor(3.0);
        fwPassMed.setDrawdownPercent(10.0);

        // Scenario 3: Forward pass with 600 trades
        OptimizationResult.Pass fwPassHigh = new OptimizationResult.Pass();
        fwPassHigh.setPassNumber(1);
        fwPassHigh.setProfit(1000.0);
        fwPassHigh.setTotalTrades(600);
        fwPassHigh.setProfitFactor(2.0);
        fwPassHigh.setRecoveryFactor(3.0);
        fwPassHigh.setDrawdownPercent(10.0);

        // Let's measure the scores.
        OptimizationResult rLow = new OptimizationResult();
        rLow.addPass(btPass);
        rLow.addForwardPass(fwPassLow);
        double scoreLow = rLow.buildCombinedPasses(true).get(0).getScore();

        OptimizationResult rMed = new OptimizationResult();
        rMed.addPass(btPass);
        rMed.addForwardPass(fwPassMed);
        double scoreMed = rMed.buildCombinedPasses(true).get(0).getScore();

        OptimizationResult rHigh = new OptimizationResult();
        rHigh.addPass(btPass);
        rHigh.addForwardPass(fwPassHigh);
        double scoreHigh = rHigh.buildCombinedPasses(true).get(0).getScore();

        // Assertions: Score should increase with more trades
        assertTrue("Score with 150 trades should be higher than with 20 trades", scoreMed > scoreLow);
        assertTrue("Score with 600 trades should be higher than with 150 trades", scoreHigh > scoreMed);
    }

    @Test
    public void testScoreWithGridPresetWeights() {
        OptimizationResult.Pass btPass = new OptimizationResult.Pass();
        btPass.setPassNumber(1);
        btPass.setProfit(1000.0);
        btPass.setTotalTrades(600); // Many trades!
        btPass.setProfitFactor(2.5);
        btPass.setRecoveryFactor(4.0);
        btPass.setDrawdownPercent(10.0);
        btPass.setBalance(11000.0);

        OptimizationResult.Pass fwPass = new OptimizationResult.Pass();
        fwPass.setPassNumber(1);
        fwPass.setProfit(1200.0);
        fwPass.setTotalTrades(500); // Many trades!
        fwPass.setProfitFactor(2.8);
        fwPass.setRecoveryFactor(4.5);
        fwPass.setDrawdownPercent(10.0);

        result.addPass(btPass);
        result.addForwardPass(fwPass);

        // Grid Preset Weights:
        OptimizationResult.ScoreWeights gridWeights = new OptimizationResult.ScoreWeights();
        gridWeights.wBtProfit = 7.0;
        gridWeights.wFwProfit = 7.0;
        gridWeights.wConsistency = 6.0;
        gridWeights.wRisk = 3.0;
        gridWeights.wEquityConsist = 3.0;
        gridWeights.wSampleSize = 23.0; // High weight on trades!
        gridWeights.wFwTrades = 30.0;  // Highest direct weight: trades first
        gridWeights.wRecovery = 21.0;  // Recovery remains the second priority
        gridWeights.recoveryMin = 1.0;
        gridWeights.recoveryMax = 5.0;

        List<OptimizationResult.CombinedPass> combined = result.buildCombinedPasses(true, gridWeights);
        assertEquals(1, combined.size());
        assertTrue("Unified score should be very high due to many trades and recovery", combined.get(0).getScore() > 65.0);
    }

    @Test
    public void testDefaultProfilePrefersManyTradesOverRecoveryPeak() {
        OptimizationResult manyTrades = new OptimizationResult();
        OptimizationResult.Pass manyBt = createRankingPass(1, 1000.0, 1000, 2.0);
        OptimizationResult.Pass manyFw = createRankingPass(1, 500.0, 1000, 2.0);
        manyTrades.addPass(manyBt);
        manyTrades.addForwardPass(manyFw);

        OptimizationResult recoveryPeak = new OptimizationResult();
        OptimizationResult.Pass peakBt = createRankingPass(2, 1000.0, 100, 5.0);
        OptimizationResult.Pass peakFw = createRankingPass(2, 500.0, 100, 5.0);
        recoveryPeak.addPass(peakBt);
        recoveryPeak.addForwardPass(peakFw);

        double manyTradesScore = manyTrades.buildCombinedPasses(true).get(0).getScore();
        double recoveryPeakScore = recoveryPeak.buildCombinedPasses(true).get(0).getScore();

        assertTrue("Trade-first defaults should prefer a broad sample over a recovery peak",
                manyTradesScore > recoveryPeakScore);
    }

    @Test
    public void testReportDirectoryPropagationAndCopy() {
        OptimizationResult optResult = new OptimizationResult();
        optResult.setOutputDirectory("backtest_reports/MyRunFolder_2026");

        OptimizationResult.Pass pass1 = new OptimizationResult.Pass();
        pass1.setPassNumber(1);
        optResult.addPass(pass1);

        assertEquals("backtest_reports/MyRunFolder_2026", pass1.getReportDirectory());

        List<OptimizationResult.CombinedPass> combined = optResult.buildCombinedPasses(false);
        assertEquals(1, combined.size());
        assertEquals("backtest_reports/MyRunFolder_2026", combined.get(0).getReportDirectory());

        OptimizationResult.CombinedPass copiedCombined = combined.get(0).copy();
        assertEquals("backtest_reports/MyRunFolder_2026", copiedCombined.getReportDirectory());
    }

    @Test
    public void buildCombinedPassesStampsSymbolPeriodAndCopyPreservesThem() {
        OptimizationResult optResult = new OptimizationResult();
        optResult.setSymbol("AUDCAD");
        optResult.setPeriod("M5");

        OptimizationResult.Pass pass1 = new OptimizationResult.Pass();
        pass1.setPassNumber(6319);
        pass1.setTickModel("1 minute OHLC");
        optResult.addPass(pass1);

        List<OptimizationResult.CombinedPass> combined = optResult.buildCombinedPasses(false);
        assertEquals("AUDCAD", combined.get(0).getSymbol());
        assertEquals("M5", combined.get(0).getPeriod());
        assertEquals("1 minute OHLC", combined.get(0).getTickModel());

        OptimizationResult.CombinedPass copied = combined.get(0).copy();
        assertEquals("AUDCAD", copied.getSymbol());
        assertEquals("M5", copied.getPeriod());
        assertEquals("1 minute OHLC", copied.getTickModel());
    }

    @Test
    public void stampMarketIfBlankFillsBlankSymbolPeriodAndTickModel() {
        OptimizationResult.Pass backtest = new OptimizationResult.Pass();
        backtest.setPassNumber(0);
        OptimizationResult.Pass forward = new OptimizationResult.Pass();
        forward.setPassNumber(0);
        OptimizationResult.CombinedPass pass = new OptimizationResult.CombinedPass(
                backtest, forward, 1.0, 1.0, "");

        pass.stampMarketIfBlank("GBPJPY", "M5", "Every tick");

        assertEquals("GBPJPY", pass.getSymbol());
        assertEquals("M5", pass.getPeriod());
        assertEquals("Every tick", pass.getTickModel());
        assertEquals("Every tick", backtest.getTickModel());
        assertEquals("Every tick", forward.getTickModel());

        pass.stampMarketIfBlank("EURUSD", "H1", "1 minute OHLC");
        assertEquals("GBPJPY", pass.getSymbol());
        assertEquals("M5", pass.getPeriod());
        assertEquals("1 minute OHLC", backtest.getTickModel());
    }

    private static OptimizationResult.Pass createRankingPass(int passNumber, double profit,
                                                               int trades, double recovery) {
        OptimizationResult.Pass pass = new OptimizationResult.Pass();
        pass.setPassNumber(passNumber);
        pass.setProfit(profit);
        pass.setBalance(10000.0 + profit);
        pass.setTotalTrades(trades);
        pass.setProfitFactor(1.8);
        pass.setRecoveryFactor(recovery);
        pass.setSharpeRatio(1.0);
        pass.setDrawdownPercent(10.0);
        return pass;
    }
}
