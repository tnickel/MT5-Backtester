package com.backtester.report;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

public class OptimizationReportParserTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testParseHtmlOptimization() throws Exception {
        String html = "<html><body>"
                + "<table>"
                + "  <tr class=header><td>Pass</td><td>Profit</td><td>Trades</td><td>Profit Factor</td><td>Expected Payoff</td><td>Drawdown $</td><td>Drawdown %</td><td>Inputs</td></tr>"
                + "  <tr align=right><td>1</td><td>500.50</td><td>15</td><td>1.75</td><td>33.37</td><td>200.00</td><td>2.00%</td><td>lot=0.10; takeProfit=50; stopLoss=30;</td></tr>"
                + "  <tr align=right><td>2</td><td>-120.30</td><td>8</td><td>0.65</td><td>-15.04</td><td>350.00</td><td>3.50%</td><td>lot=0.10; takeProfit=40; stopLoss=40;</td></tr>"
                + "</table>"
                + "</body></html>";

        Path file = tempFolder.newFile("opt_report.htm").toPath();
        Files.writeString(file, html);

        OptimizationReportParser parser = new OptimizationReportParser();
        OptimizationResult result = new OptimizationResult();
        parser.parseHtml(file, result);

        assertEquals(2, result.getPasses().size());
        
        OptimizationResult.Pass pass1 = result.getPasses().get(0);
        assertEquals(1, pass1.getPassNumber());
        assertEquals(500.50, pass1.getProfit(), 0.001);
        assertEquals(15, pass1.getTotalTrades());
        assertEquals(1.75, pass1.getProfitFactor(), 0.001);
        assertEquals(33.37, pass1.getExpectedPayoff(), 0.001);
        assertEquals(200.0, pass1.getDrawdown(), 0.001);
        assertEquals(2.0, pass1.getDrawdownPercent(), 0.001);
        assertEquals("0.10", pass1.getParameterValues().get("lot"));
        assertEquals("50", pass1.getParameterValues().get("takeProfit"));
        assertEquals("30", pass1.getParameterValues().get("stopLoss"));

        OptimizationResult.Pass pass2 = result.getPasses().get(1);
        assertEquals(2, pass2.getPassNumber());
        assertEquals(-120.30, pass2.getProfit(), 0.001);
        assertEquals(8, pass2.getTotalTrades());
        assertEquals("40", pass2.getParameterValues().get("takeProfit"));
    }
}
