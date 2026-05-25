package com.backtester.report;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

public class ReportParserTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private BacktestResult parseHtml(String html) throws IOException {
        return parseHtml(html, StandardCharsets.UTF_8);
    }

    private BacktestResult parseHtml(String html, java.nio.charset.Charset charset) throws IOException {
        Path file = tempFolder.newFile("report.htm").toPath();
        Files.write(file, html.getBytes(charset));
        ReportParser parser = new ReportParser();
        BacktestResult result = parser.parse(file);
        Files.delete(file);
        return result;
    }

    @Test
    public void testParseEnglishNumberFormat() throws IOException {
        String html = "<table>"
                + "<tr><td>Initial Deposit:</td><td><b>10 000.00</b></td></tr>"
                + "<tr><td>Total Net Profit:</td><td><b>500.50</b></td></tr>"
                + "<tr><td>Gross Profit:</td><td><b>1 200.00</b></td></tr>"
                + "<tr><td>Gross Loss:</td><td><b>-699.50</b></td></tr>"
                + "<tr><td>Profit Factor:</td><td><b>1.72</b></td></tr>"
                + "</table>";

        BacktestResult res = parseHtml(html);
        assertEquals(10000.0, res.getInitialDeposit(), 0.001);
        assertEquals(500.50, res.getTotalProfit(), 0.001);
        assertEquals(1200.00, res.getGrossProfit(), 0.001);
        assertEquals(-699.50, res.getGrossLoss(), 0.001);
        assertEquals(1.72, res.getProfitFactor(), 0.001);
    }

    @Test
    public void testParseGermanNumberFormat() throws IOException {
        String html = "<table>"
                + "<tr><td>Ersteinlage:</td><td><b>10.000,00</b></td></tr>"
                + "<tr><td>Nettogewinn gesamt:</td><td><b>500,50</b></td></tr>"
                + "<tr><td>Bruttogewinn:</td><td><b>1.200,00</b></td></tr>"
                + "<tr><td>Bruttoverlust:</td><td><b>-699,50</b></td></tr>"
                + "<tr><td>Profitfaktor:</td><td><b>1,72</b></td></tr>"
                + "</table>";

        BacktestResult res = parseHtml(html);
        assertEquals(10000.0, res.getInitialDeposit(), 0.001);
        assertEquals(500.50, res.getTotalProfit(), 0.001);
        assertEquals(1200.00, res.getGrossProfit(), 0.001);
        assertEquals(-699.50, res.getGrossLoss(), 0.001);
        assertEquals(1.72, res.getProfitFactor(), 0.001);
    }

    @Test
    public void testParseWonLostTrades() throws IOException {
        String html = "<table>"
                + "<tr><td>Won Trades:</td><td><b>150 (75.00%)</b></td></tr>"
                + "<tr><td>Lost Trades:</td><td><b>50 (25.00%)</b></td></tr>"
                + "</table>";

        BacktestResult res = parseHtml(html);
        assertEquals(150, res.getProfitTrades());
        assertEquals(50, res.getLossTrades());
        assertEquals(75.0, res.getWinRate(), 0.001);
    }

    @Test
    public void testParsePercentageDrawdown() throws IOException {
        String html = "<table>"
                + "<tr><td>Maximal Equity Drawdown:</td><td><b>450.00 (4.50%)</b></td></tr>"
                + "<tr><td>Balance Drawdown Maximal:</td><td><b>350.00 (3.50%)</b></td></tr>"
                + "</table>";

        BacktestResult res = parseHtml(html);
        assertEquals(4.50, res.getMaxDrawdown(), 0.001);
        assertEquals(3.50, res.getBalanceDrawdown(), 0.001);
    }

    @Test
    public void testParseEmptyHTML() throws IOException {
        BacktestResult res = parseHtml("");
        assertFalse(res.isSuccess());
        assertEquals(0.0, res.getTotalProfit(), 0.001);
        assertEquals(0, res.getTotalTrades());
    }

    @Test
    public void testParseMissingFields() throws IOException {
        String html = "<table>"
                + "<tr><td>SomeRandomLabel:</td><td><b>12345</b></td></tr>"
                + "</table>";
        BacktestResult res = parseHtml(html);
        assertEquals(0.0, res.getTotalProfit(), 0.001);
        assertEquals(0, res.getInitialDeposit(), 0.001);
    }

    @Test
    public void testParseShortLongPositions() throws IOException {
        String html = "<table>"
                + "<tr><td>Short Positions:</td><td><b>110 (70.00%)</b></td></tr>"
                + "<tr><td>Long Positions:</td><td><b>90 (80.00%)</b></td></tr>"
                + "</table>";

        BacktestResult res = parseHtml(html);
        assertEquals(110, res.getShortPositions());
        assertEquals(90, res.getLongPositions());
    }

    @Test
    public void testParseLargestAvgWinLoss() throws IOException {
        String html = "<table>"
                + "<tr><td>Largest profit trade:</td><td><b>150.00</b></td></tr>"
                + "<tr><td>Largest loss trade:</td><td><b>-90.00</b></td></tr>"
                + "<tr><td>Average profit trade:</td><td><b>8.00</b></td></tr>"
                + "<tr><td>Average loss trade:</td><td><b>-14.00</b></td></tr>"
                + "</table>";

        BacktestResult res = parseHtml(html);
        assertEquals(150.00, res.getLargestWin(), 0.001);
        assertEquals(-90.00, res.getLargestLoss(), 0.001);
        assertEquals(8.00, res.getAverageWin(), 0.001);
        assertEquals(-14.00, res.getAverageLoss(), 0.001);
    }

    @Test
    public void testParseTradeHistoryDeals() throws IOException {
        String html = "<table>"
                + "<tr><td>Initial Deposit:</td><td><b>10000.00</b></td></tr>"
                + "</table>"
                + "<div>Trades</div>"
                + "<table>"
                + "<tr bgcolor=\"#FFFFFF\">"
                + "  <td>2025.05.15 12:30:45</td>"
                + "  <td>buy</td>"
                + "  <td>0.10</td>"
                + "  <td>EURUSD</td>"
                + "  <td>1.0850</td>"
                + "  <td>0</td>"
                + "  <td>0</td>"
                + "  <td>1.0900</td>"
                + "  <td>0</td>"
                + "  <td>0</td>"
                + "  <td>0</td>"
                + "  <td>10050.00</td>"
                + "  <td>100.00</td>"
                + "</tr>"
                + "<tr bgcolor=\"#FFFFFF\">"
                + "  <td>2025.05.16 14:20:10</td>"
                + "  <td>sell</td>"
                + "  <td>0.10</td>"
                + "  <td>EURUSD</td>"
                + "  <td>1.0920</td>"
                + "  <td>0</td>"
                + "  <td>0</td>"
                + "  <td>1.0870</td>"
                + "  <td>0</td>"
                + "  <td>0</td>"
                + "  <td>0</td>"
                + "  <td>10150.00</td>"
                + "  <td>100.00</td>"
                + "</tr>"
                + "</table>";

        BacktestResult res = parseHtml(html);
        List<double[]> history = res.getEquityHistory();
        // Index 0 is the initial deposit placeholder, then 2 trades -> total 3 points
        assertEquals(3, history.size());
        assertEquals(10000.0, history.get(0)[1], 0.001);
        assertEquals(10050.0, history.get(1)[1], 0.001);
        assertEquals(10150.0, history.get(2)[1], 0.001);
        assertTrue(history.get(1)[3] > 0);
    }

    @Test
    public void testParseEncodingBOMUtf16le() throws IOException {
        String html = "<table><tr><td>Total Net Profit:</td><td><b>250.00</b></td></tr></table>";
        byte[] bom = new byte[]{(byte) 0xFF, (byte) 0xFE};
        byte[] content = html.getBytes(StandardCharsets.UTF_16LE);
        byte[] total = new byte[bom.length + content.length];
        System.arraycopy(bom, 0, total, 0, bom.length);
        System.arraycopy(content, 0, total, bom.length, content.length);

        Path file = tempFolder.newFile("report_utf16le.htm").toPath();
        Files.write(file, total);

        ReportParser parser = new ReportParser();
        BacktestResult res = parser.parse(file);
        Files.delete(file);

        assertEquals(250.00, res.getTotalProfit(), 0.001);
    }

    @Test
    public void testParseEncodingBOMUtf16be() throws IOException {
        String html = "<table><tr><td>Total Net Profit:</td><td><b>250.00</b></td></tr></table>";
        byte[] bom = new byte[]{(byte) 0xFE, (byte) 0xFF};
        byte[] content = html.getBytes(StandardCharsets.UTF_16BE);
        byte[] total = new byte[bom.length + content.length];
        System.arraycopy(bom, 0, total, 0, bom.length);
        System.arraycopy(content, 0, total, bom.length, content.length);

        Path file = tempFolder.newFile("report_utf16be.htm").toPath();
        Files.write(file, total);

        ReportParser parser = new ReportParser();
        BacktestResult res = parser.parse(file);
        Files.delete(file);

        assertEquals(250.00, res.getTotalProfit(), 0.001);
    }

    @Test
    public void testParseEncodingNoBOMUtf16le() throws IOException {
        // UTF-16LE without BOM - heuristic checks for 0x00 bytes
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append(" ");
        }
        sb.append("<table><tr><td>Total Net Profit:</td><td><b>250.00</b></td></tr></table>");
        String html = sb.toString();
        byte[] total = html.getBytes(StandardCharsets.UTF_16LE);

        Path file = tempFolder.newFile("report_utf16le_nobom.htm").toPath();
        Files.write(file, total);

        ReportParser parser = new ReportParser();
        BacktestResult res = parser.parse(file);
        Files.delete(file);

        assertEquals(250.00, res.getTotalProfit(), 0.001);
    }
}
