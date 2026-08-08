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
    public void shortTradesWonMapsToShortPositionsNotTotalWon() throws IOException {
        String html = "<table>"
                + "<tr><td>Profit Trades:</td><td><b>241 (77.00%)</b></td></tr>"
                + "<tr><td>Short Trades Won:</td><td><b>110 (70.00%)</b></td></tr>"
                + "</table>";

        BacktestResult res = parseHtml(html);
        assertEquals(241, res.getProfitTrades());
        assertEquals(110, res.getShortPositions());
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

    @Test
    public void testParseMt4Report() throws IOException {
        String html = "<table>"
                + "<tr><td align=left>Initial deposit</td><td align=right>10000.00</td></tr>"
                + "<tr><td align=left>Total net profit</td><td align=right><b>450.75</b></td></tr>"
                + "<tr><td align=left>Profit factor</td><td align=right>1.85</td></tr>"
                + "<tr><td align=left>Maximal drawdown</td><td align=right>350.00 (3.50%)</td></tr>"
                + "<tr><td align=left>Trades gesamt</td><td align=right>120</td></tr>"
                + "</table>";

        BacktestResult res = parseHtml(html);
        assertEquals(10000.0, res.getInitialDeposit(), 0.001);
        assertEquals(450.75, res.getTotalProfit(), 0.001);
        assertEquals(1.85, res.getProfitFactor(), 0.001);
        assertEquals(3.50, res.getMaxDrawdown(), 0.001);
        assertEquals(120, res.getTotalTrades());
    }

    @Test
    public void testParseRelativeEquityDrawdownSeparatelyFromMaximalDrawdown() throws IOException {
        String html = "<table>"
                + "<tr><td>Rückgang Equity maximal</td><td>13 709.48 (24.94%)</td></tr>"
                + "<tr><td>Rückgang Equity relativ</td><td>26.89% (7 707.20)</td></tr>"
                + "</table>";

        BacktestResult result = parseHtml(html);

        assertEquals(24.94, result.getMaxDrawdown(), 0.001);
        assertEquals(13709.48, result.getMaxDrawdownAbsolute(), 0.001);
        assertEquals(26.89, result.getMaxDrawdownPercent(), 0.001);
    }

    @Test
    public void testPreservesUnknownStatisticsFromResultsSection() throws IOException {
        String html = "<table>"
                + "<tr><td colspan=\"6\">Ergebnisse</td></tr>"
                + "<tr><td>Qualität der Historie:</td><td>99%</td>"
                + "<td>Balken:</td><td>298651</td><td>Ticks:</td><td>104559972</td></tr>"
                + "<tr><td>Z-Score:</td><td>8.17 (99.74%)</td>"
                + "<td>LR Korrelation:</td><td>0.96</td></tr>"
                + "<tr><td colspan=\"6\">Orders</td></tr>"
                + "<tr><td>2026.08.04 12:00</td><td>must not be captured</td></tr>"
                + "</table>";

        BacktestResult result = parseHtml(html);

        assertEquals("99%", result.getRawStatistics().get("Qualität der Historie"));
        assertEquals("298651", result.getRawStatistics().get("Balken"));
        assertEquals("104559972", result.getRawStatistics().get("Ticks"));
        assertEquals("8.17 (99.74%)", result.getRawStatistics().get("Z-Score"));
        assertEquals("0.96", result.getRawStatistics().get("LR Korrelation"));
        assertFalse(result.getRawStatistics().containsValue("must not be captured"));
    }

    @Test
    public void testParseMt4TradeHistory() throws IOException {
        String html = "<table>"
                + "<tr><td>Initial deposit:</td><td>10000.00</td></tr>"
                + "</table>"
                + "<div>Closed Transactions</div>"
                + "<table>"
                + "<tr>"
                + "  <td>2025.05.15 12:30:45</td>"
                + "  <td>buy</td>"
                + "  <td>1</td>"
                + "  <td>0.10</td>"
                + "  <td>1.0850</td>"
                + "  <td>0</td>"
                + "  <td>0</td>"
                + "  <td>1.0900</td>"
                + "  <td>100.00</td>"
                + "  <td>10100.00</td>"
                + "</tr>"
                + "<tr>"
                + "  <td>2025.05.16 14:20:10</td>"
                + "  <td>sell</td>"
                + "  <td>2</td>"
                + "  <td>0.10</td>"
                + "  <td>1.0920</td>"
                + "  <td>0</td>"
                + "  <td>0</td>"
                + "  <td>1.0870</td>"
                + "  <td>150.00</td>"
                + "  <td>10250.00</td>"
                + "</tr>"
                + "</table>";

        BacktestResult res = parseHtml(html);
        List<double[]> history = res.getEquityHistory();
        assertEquals(3, history.size());
        assertEquals(10000.0, history.get(0)[1], 0.001);
        assertEquals(10100.0, history.get(1)[1], 0.001);
        assertEquals(10250.0, history.get(2)[1], 0.001);
    }

    @Test
    public void testParseGermanMt4ReportWithWindows1252Umlauts() throws IOException {
        String html = "<table>"
                + "<tr><td>Urspr\u00FCngliche Einzahlung</td><td>10000.00</td></tr>"
                + "<tr><td>Nettoprofit gesamt</td><td>-31.92</td></tr>"
                + "<tr><td>Bruttoprofit</td><td>117.77</td></tr>"
                + "<tr><td>Bruttoverlust</td><td>-149.68</td></tr>"
                + "<tr><td>Profitfaktor</td><td>0.79</td></tr>"
                + "<tr><td>Maximaler R\u00FCckgang</td><td>117.65 (1.17%)</td></tr>"
                + "<tr><td>Anzahl an Trades</td><td>64</td></tr>"
                + "<tr><td>Gewonne Trades (in % von Gesamt)</td><td>61 (95.31%)</td></tr>"
                + "<tr><td>Verlorene Trades (in % von Gesamt)</td><td>3 (4.69%)</td></tr>"
                + "</table>";

        BacktestResult res = parseHtml(html, java.nio.charset.Charset.forName("windows-1252"));
        
        assertEquals(10000.0, res.getInitialDeposit(), 0.001);
        assertEquals(-31.92, res.getTotalProfit(), 0.001);
        assertEquals(117.77, res.getGrossProfit(), 0.001);
        assertEquals(-149.68, res.getGrossLoss(), 0.001);
        assertEquals(0.79, res.getProfitFactor(), 0.001);
        assertEquals(1.17, res.getMaxDrawdown(), 0.001);
        assertEquals(64, res.getTotalTrades());
        assertEquals(61, res.getProfitTrades());
        assertEquals(95.31, res.getWinRate(), 0.001);
        assertEquals(3, res.getLossTrades());
    }

    @Test
    public void testParseMt4TradeHistoryWithBgColorFallback() throws IOException {
        String html = "<table>"
                + "<tr><td>Urspr\u00FCngliche Einzahlung</td><td>10000.00</td></tr>"
                + "</table>"
                + "<img src=\"BacktestReport.gif\"><br>"
                + "<table cellspacing=1 cellpadding=3 border=0>"
                + "<tr bgcolor=\"#C0C0C0\" align=right><td>Nr.</td><td>Zeit</td><td>Typ</td><td>Ordernummer</td><td>Volumen</td><td>Preis</td><td>S / L</td><td>T/P</td><td>Gewinn</td><td>Kontostand</td></tr>"
                + "<tr align=right><td>1</td><td>2026.01.13 21:00</td><td>sell</td><td>21</td><td>0.01</td><td>4594.12</td><td>0.00</td><td>0.00</td><td>0.00</td><td>10000.00</td></tr>"
                + "<tr align=right><td>2</td><td>2026.01.13 21:01</td><td>t/p</td><td>21</td><td>0.01</td><td>4592.12</td><td>4644.12</td><td>4592.12</td><td>1.95</td><td>10001.95</td></tr>"
                + "</table>";

        BacktestResult res = parseHtml(html, java.nio.charset.Charset.forName("windows-1252"));
        List<double[]> history = res.getEquityHistory();
        
        assertEquals(3, history.size());
        assertEquals(10000.0, history.get(0)[1], 0.001);
        assertEquals(10000.0, history.get(1)[1], 0.001);
        assertEquals(10001.95, history.get(2)[1], 0.001);
    }
}
