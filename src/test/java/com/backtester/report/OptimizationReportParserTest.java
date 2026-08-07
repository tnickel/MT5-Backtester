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

    /**
     * The forward report replaces the "Result" column with "Forward Result" and
     * "Back Result". Both are MT5 metrics and must not end up in the parameter map,
     * where they would look like EA inputs.
     */
    @Test
    public void forwardMetricColumnsAreNotStoredAsEaParameters() throws Exception {
        Path file = tempFolder.newFile("opt_forward.xml").toPath();
        Files.writeString(file, spreadsheet(
                row("Pass", "Forward Result", "Back Result", "Profit", "Equity DD %", "Trades", "Inp_Grid_Step"),
                row("9704", "1.83", "1.93", "1392.19", "6.7633", "314", "350")));

        OptimizationReportParser parser = new OptimizationReportParser();
        OptimizationResult result = new OptimizationResult();
        parser.parseForward(file, result);

        assertEquals(1, result.getForwardPasses().size());
        OptimizationResult.Pass pass = result.getForwardPasses().get(0);
        assertEquals(9704, pass.getPassNumber());
        assertEquals(1392.19, pass.getProfit(), 0.001);
        assertEquals(314, pass.getTotalTrades());
        assertEquals(6.7633, pass.getDrawdownPercent(), 0.001);
        assertEquals("350", pass.getParameterValues().get("Inp_Grid_Step"));
        assertEquals(1, pass.getParameterValues().size());
        assertFalse(pass.getParameterValues().containsKey("Forward Result"));
        assertFalse(pass.getParameterValues().containsKey("Back Result"));
    }

    @Test
    public void unreportedMetricColumnsStayOutOfParameterNames() throws Exception {
        Path file = tempFolder.newFile("opt_metrics.xml").toPath();
        Files.writeString(file, spreadsheet(
                row("Pass", "Result", "Profit", "Equity DD $", "Equity DD %", "Trades", "Inp_Grid_Step"),
                row("1", "1.93", "1652.58", "712.71", "7.1271", "350", "350")));

        OptimizationReportParser parser = new OptimizationReportParser();
        OptimizationResult result = new OptimizationResult();
        parser.parse(file, result);

        assertEquals(List.of("Inp_Grid_Step"), result.getParameterNames());
        assertEquals(712.71, result.getPasses().get(0).getDrawdown(), 0.001);
    }

    /**
     * Without this link a pass cannot find the preset archived with its run, and
     * parameter reconstruction falls back to the mutable EA configuration.
     */
    @Test
    public void parsedPassesAreLinkedToTheRunOutputDirectory() throws Exception {
        Path file = tempFolder.newFile("opt_linked.xml").toPath();
        Files.writeString(file, spreadsheet(
                row("Pass", "Result", "Profit", "Equity DD %", "Trades", "Inp_Grid_Step"),
                row("9704", "1.93", "1652.58", "7.1271", "350", "350")));

        OptimizationResult result = new OptimizationResult();
        result.setOutputDirectory("C:/reports/OPT_run");
        new OptimizationReportParser().parse(file, result);

        assertEquals("C:/reports/OPT_run", result.getPasses().get(0).getReportDirectory());
    }

    @Test
    public void parsedForwardPassesAreLinkedToTheRunOutputDirectory() throws Exception {
        Path file = tempFolder.newFile("fwd_linked.xml").toPath();
        Files.writeString(file, spreadsheet(
                row("Pass", "Forward Result", "Back Result", "Profit", "Trades", "Inp_Grid_Step"),
                row("9704", "1.83", "1.93", "1392.19", "314", "350")));

        OptimizationResult result = new OptimizationResult();
        result.setOutputDirectory("C:/reports/OPT_run");
        new OptimizationReportParser().parseForward(file, result);

        assertEquals("C:/reports/OPT_run", result.getForwardPasses().get(0).getReportDirectory());
    }

    private static String row(String... cells) {
        StringBuilder sb = new StringBuilder("<Row>");
        for (String cell : cells) {
            sb.append("<Cell><Data ss:Type=\"String\">").append(cell).append("</Data></Cell>");
        }
        return sb.append("</Row>").toString();
    }

    private static String spreadsheet(String... rows) {
        return "<?xml version=\"1.0\"?><Workbook xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">"
                + "<Worksheet><Table>" + String.join("", rows) + "</Table></Worksheet></Workbook>";
    }
}
