package com.backtester.report;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class OptimizationResultMoreTest {

    @Test
    public void testCombinedPassGetName() {
        OptimizationResult.Pass bt = new OptimizationResult.Pass();
        bt.setPassNumber(42);
        OptimizationResult.CombinedPass cp = new OptimizationResult.CombinedPass(bt, null, 80.0, 1.0, "details");
        assertEquals("42", cp.getName());
    }

    @Test
    public void testCombinedPassBtConvenienceGetters() {
        OptimizationResult.Pass bt = new OptimizationResult.Pass();
        bt.setPassNumber(100);
        bt.setProfit(150.50);
        bt.setTotalTrades(25);
        bt.setProfitFactor(2.1);
        bt.setDrawdownPercent(8.5);
        bt.setSharpeRatio(1.2);
        bt.setRecoveryFactor(3.5);
        bt.setExpectedPayoff(6.02);

        OptimizationResult.CombinedPass cp = new OptimizationResult.CombinedPass(bt, null, 90.0, 1.2, "details");
        assertEquals(150.50, cp.getBtProfit(), 0.001);
        assertEquals(25, cp.getBtTrades());
        assertEquals(2.1, cp.getBtPf(), 0.001);
        assertEquals(8.5, cp.getBtDd(), 0.001);
        assertEquals(1.2, cp.getBtSharpe(), 0.001);
        assertEquals(3.5, cp.getBtRecovery(), 0.001);
        assertEquals(6.02, cp.getBtExpectedPayoff(), 0.001);
    }

    @Test
    public void testCombinedPassFwConvenienceGettersNull() {
        OptimizationResult.Pass bt = new OptimizationResult.Pass();
        OptimizationResult.CombinedPass cp = new OptimizationResult.CombinedPass(bt, null, 90.0, 1.2, "details");
        assertTrue(Double.isNaN(cp.getFwProfit()));
        assertEquals(0, cp.getFwTrades());
        assertTrue(Double.isNaN(cp.getFwPf()));
        assertTrue(Double.isNaN(cp.getFwDd()));
        assertTrue(Double.isNaN(cp.getFwSharpe()));
        assertTrue(Double.isNaN(cp.getFwRecovery()));
        assertTrue(Double.isNaN(cp.getFwExpectedPayoff()));
    }

    @Test
    public void testCombinedPassFwConvenienceGettersWithFw() {
        OptimizationResult.Pass bt = new OptimizationResult.Pass();
        OptimizationResult.Pass fw = new OptimizationResult.Pass();
        fw.setProfit(200.0);
        fw.setTotalTrades(30);
        fw.setProfitFactor(2.5);
        fw.setDrawdownPercent(12.0);
        fw.setSharpeRatio(1.5);
        fw.setRecoveryFactor(4.2);
        fw.setExpectedPayoff(6.67);

        OptimizationResult.CombinedPass cp = new OptimizationResult.CombinedPass(bt, fw, 90.0, 1.2, "details");
        assertEquals(200.0, cp.getFwProfit(), 0.001);
        assertEquals(30, cp.getFwTrades());
        assertEquals(2.5, cp.getFwPf(), 0.001);
        assertEquals(12.0, cp.getFwDd(), 0.001);
        assertEquals(1.5, cp.getFwSharpe(), 0.001);
        assertEquals(4.2, cp.getFwRecovery(), 0.001);
        assertEquals(6.67, cp.getFwExpectedPayoff(), 0.001);
    }

    @Test
    public void testCombinedPassConsistencyZeroProfit() {
        OptimizationResult.Pass bt = new OptimizationResult.Pass();
        bt.setProfit(-100.0);
        bt.setRecoveryFactor(2.0);

        OptimizationResult.Pass fw = new OptimizationResult.Pass();
        fw.setProfit(50.0);
        fw.setRecoveryFactor(2.0);

        OptimizationResult result = new OptimizationResult();
        result.addPass(bt);
        result.addForwardPass(fw);

        List<OptimizationResult.CombinedPass> combined = result.buildCombinedPasses(true);
        assertEquals(1, combined.size());
        // profitRatio = 1.5 because btProfit <= 0 and fwProfit > 0
        // recRatio = 2.0 / 2.0 = 1.0
        // consistency = (1.5 + 1.0) / 2 = 1.25
        assertEquals(1.25, combined.get(0).getConsistency(), 0.001);
    }

    @Test
    public void testCombinedPassConsistencyZeroRecovery() {
        OptimizationResult.Pass bt = new OptimizationResult.Pass();
        bt.setProfit(100.0);
        bt.setRecoveryFactor(-1.0);

        OptimizationResult.Pass fw = new OptimizationResult.Pass();
        fw.setProfit(100.0);
        fw.setRecoveryFactor(2.0);

        OptimizationResult result = new OptimizationResult();
        result.addPass(bt);
        result.addForwardPass(fw);

        List<OptimizationResult.CombinedPass> combined = result.buildCombinedPasses(true);
        assertEquals(1, combined.size());
        // profitRatio = 100 / 100 = 1.0
        // recRatio = 1.5 because btRecovery <= 0 and fwRecovery > 0
        // consistency = (1.0 + 1.5) / 2 = 1.25
        assertEquals(1.25, combined.get(0).getConsistency(), 0.001);
    }

    @Test
    public void testOptimizationResultEmptyBestProfit() {
        OptimizationResult r = new OptimizationResult();
        assertNull(r.getBestByProfit());
    }

    @Test
    public void testOptimizationResultEmptyBestCriterion() {
        OptimizationResult r = new OptimizationResult();
        assertNull(r.getBestByCriterion(0));
    }

    @Test
    public void testOptimizationResultParameterNames() {
        OptimizationResult r = new OptimizationResult();
        List<String> names = new ArrayList<>();
        names.add("p1");
        names.add("p2");
        r.setParameterNames(names);
        assertEquals(names, r.getParameterNames());
    }

    @Test
    public void testOptimizationResultExpert() {
        OptimizationResult r = new OptimizationResult();
        r.setExpert("ExpertA");
        assertEquals("ExpertA", r.getExpert());
    }

    @Test
    public void testOptimizationResultSymbol() {
        OptimizationResult r = new OptimizationResult();
        r.setSymbol("EURUSD");
        assertEquals("EURUSD", r.getSymbol());
    }

    @Test
    public void testOptimizationResultPeriod() {
        OptimizationResult r = new OptimizationResult();
        r.setPeriod("H1");
        assertEquals("H1", r.getPeriod());
    }

    @Test
    public void testOptimizationResultFromDate() {
        OptimizationResult r = new OptimizationResult();
        r.setFromDate("2024-01-01");
        assertEquals("2024-01-01", r.getFromDate());
    }

    @Test
    public void testOptimizationResultToDate() {
        OptimizationResult r = new OptimizationResult();
        r.setToDate("2024-12-31");
        assertEquals("2024-12-31", r.getToDate());
    }

    @Test
    public void testOptimizationResultOutputDirectory() {
        OptimizationResult r = new OptimizationResult();
        r.setOutputDirectory("/tmp/dir");
        assertEquals("/tmp/dir", r.getOutputDirectory());
    }

    @Test
    public void testOptimizationResultSuccessState() {
        OptimizationResult r = new OptimizationResult();
        assertFalse(r.isSuccess());
        r.setSuccess(true);
        assertTrue(r.isSuccess());
        r.setMessage("Done");
        assertEquals("Done", r.getMessage());
    }

    @Test
    public void testOptimizationResultForwardPassesList() {
        OptimizationResult r = new OptimizationResult();
        assertFalse(r.hasForwardResults());
        
        List<OptimizationResult.Pass> fwList = new ArrayList<>();
        OptimizationResult.Pass p = new OptimizationResult.Pass();
        fwList.add(p);
        
        r.setForwardPasses(fwList);
        assertTrue(r.hasForwardResults());
        assertEquals(fwList, r.getForwardPasses());
    }

    @Test
    public void testOptimizationResultPassesList() {
        OptimizationResult r = new OptimizationResult();
        List<OptimizationResult.Pass> pList = new ArrayList<>();
        OptimizationResult.Pass p = new OptimizationResult.Pass();
        pList.add(p);
        
        r.setPasses(pList);
        assertEquals(pList, r.getPasses());
    }

    @Test
    public void testScoreWeightsDefaults() {
        OptimizationResult.ScoreWeights w = OptimizationResult.ScoreWeights.defaults();
        assertNotNull(w);
        assertEquals(7.0, w.wBtProfit, 0.001);
        assertEquals(7.0, w.wFwProfit, 0.001);
        assertEquals(6.0, w.wConsistency, 0.001);
    }

    @Test
    public void testCombinedPassConsistencyNaNPending() {
        OptimizationResult.Pass bt = new OptimizationResult.Pass();
        bt.setProfit(Double.NaN);
        bt.setRecoveryFactor(Double.NaN);
        
        OptimizationResult.Pass fw = new OptimizationResult.Pass();
        fw.setProfit(Double.NaN);
        fw.setRecoveryFactor(Double.NaN);

        OptimizationResult result = new OptimizationResult();
        result.addPass(bt);
        result.addForwardPass(fw);

        List<OptimizationResult.CombinedPass> combined = result.buildCombinedPasses(true);
        assertEquals(1, combined.size());
        assertEquals(0.0, combined.get(0).getConsistency(), 0.001);
    }

    @Test
    public void testOptimizationResultScoreWeightsTotal() {
        OptimizationResult.ScoreWeights w = new OptimizationResult.ScoreWeights();
        w.wBtProfit = 10;
        w.wFwProfit = 10;
        w.wConsistency = 10;
        w.wRisk = 10;
        w.wEquityConsist = 10;
        w.wSampleSize = 10;
        w.wFwTrades = 10;
        w.wRecovery = 10;
        assertEquals(80.0, w.total(), 0.001);
    }
}
