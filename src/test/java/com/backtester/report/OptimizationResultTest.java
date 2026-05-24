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
}
