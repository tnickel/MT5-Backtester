package com.backtester.report;

import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import org.junit.Test;
import static org.junit.Assert.*;

public class RobustnessScorecardGeneratorTest {

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
    public void testGenerateHtmlContainsVueAndData() {
        Pass bt = createPass(3751, 1500.0, 100, 2.2, 8.0, 6.0, 5.0, 2.2);
        Pass fw = createPass(3751, 1500.0, 90, 2.1, 7.0, 5.5, 4.8, 2.0);
        CombinedPass cp = new CombinedPass(bt, fw, 95.0, 1.0, "");

        String html = RobustnessScorecardGenerator.generateHtml(
            cp, "TestEA", "EURUSD", "H1", "2026-01-01", "2026-04-26"
        );

        assertNotNull("Generated HTML should not be null", html);
        assertTrue("Generated HTML should contain doctype declaration", html.contains("<!DOCTYPE html>"));
        assertTrue("Generated HTML should contain createApp inlining", html.contains("createApp("));
        assertTrue("Generated HTML should inline the Vue framework script", html.contains("Vue"));
        assertTrue("Generated HTML should contain injected strategy variable", html.contains("window.INJECTED_STRATEGY = {"));
        assertTrue("Generated HTML should contain injected stats variable", html.contains("window.INJECTED_STATS = {"));
        assertTrue("Generated HTML should contain the project name TestEA", html.contains("projectName: 'TestEA'"));
        assertTrue("Generated HTML should contain the symbol name EURUSD", html.contains("EURUSD"));
        assertTrue("Generated HTML should contain the period H1", html.contains("H1"));
        assertTrue("Generated HTML should contain the pass number 3751", html.contains("Pass #3751"));
    }

    @Test
    public void testGenerateHtmlMissingDates() {
        Pass bt = createPass(1, 100.0, 10, 1.5, 5.0, 1.0, 2.0, 1.0);
        CombinedPass cp = new CombinedPass(bt, null, 50.0, 0.5, "");

        String html = RobustnessScorecardGenerator.generateHtml(
            cp, "TestEA_v2", "GBPUSD", "M15", null, ""
        );

        assertNotNull(html);
        assertTrue(html.contains("projectName: 'TestEA_v2'"));
        assertTrue(html.contains("GBPUSD | M15"));
    }
}
