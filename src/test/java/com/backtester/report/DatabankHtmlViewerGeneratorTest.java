package com.backtester.report;

import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class DatabankHtmlViewerGeneratorTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testGenerateWithEmptyPasses() throws IOException {
        Path outDir = tempFolder.newFolder("reports").toPath();
        Path report = DatabankHtmlViewerGenerator.generate("data2", new ArrayList<>(), outDir, "TestEA", "AUDCAD", "M5");

        assertNotNull(report);
        assertTrue(Files.exists(report));
        String content = Files.readString(report);
        assertTrue(content.contains("Databank Report: data2"));
        assertTrue(content.contains("TestEA"));
        assertTrue(content.contains("AUDCAD"));
        assertTrue(content.contains("Strategien: <strong>0</strong>"));
    }

    @Test
    public void testGenerateDoesNotInventSyntheticChartWhenImageIsMissing() throws IOException {
        Path outDir = tempFolder.newFolder("reports").toPath();

        List<CombinedPass> passes = new ArrayList<>();

        // Pass 1
        Pass bt1 = new Pass();
        bt1.setPassNumber(101);
        bt1.setProfit(1250.50);
        bt1.setTotalTrades(150);
        bt1.setProfitFactor(2.35);
        bt1.setDrawdownPercent(4.5);
        bt1.setSharpeRatio(1.8);
        bt1.setFromDate("2025.01.01");
        bt1.setToDate("2025.12.31");
        bt1.setParameter("StopLoss", "30");
        bt1.setParameter("TakeProfit", "60");

        List<double[]> btEquity = new ArrayList<>();
        btEquity.add(new double[]{0, 10000.0, 10000.0, 0});
        btEquity.add(new double[]{1, 10500.0, 10500.0, 1000});
        btEquity.add(new double[]{2, 11250.50, 11250.50, 2000});
        bt1.setEquityHistory(btEquity);

        Pass fw1 = new Pass();
        fw1.setPassNumber(101);
        fw1.setProfit(420.00);
        fw1.setTotalTrades(45);
        fw1.setProfitFactor(1.95);
        fw1.setDrawdownPercent(2.1);
        fw1.setSharpeRatio(2.1);
        fw1.setFromDate("2026.01.01");
        fw1.setToDate("2026.06.01");

        List<double[]> fwEquity = new ArrayList<>();
        fwEquity.add(new double[]{0, 11250.50, 11250.50, 2100});
        fwEquity.add(new double[]{1, 11670.50, 11670.50, 3000});
        fw1.setEquityHistory(fwEquity);

        CombinedPass cp1 = new CombinedPass(bt1, fw1, 85.5, 92.0, "High Performance");
        cp1.setStrategyName("Strat 101");
        cp1.setSymbol("EURUSD");
        passes.add(cp1);

        Path report = DatabankHtmlViewerGenerator.generate("data2", passes, outDir, "ToTheMoon_KI_v132", "EURUSD", "M5");

        assertNotNull(report);
        assertTrue(Files.exists(report));
        String html = Files.readString(report);

        assertTrue(html.contains("Databank Report: data2"));
        assertTrue(html.contains("Strat 101"));
        assertTrue(html.contains("Score: 85.5"));
        assertTrue(html.contains("$1250.50"));
        assertTrue(html.contains("$420.00"));
        assertTrue(html.contains("StopLoss"));
        assertTrue(html.contains("30"));
        assertTrue(html.contains("TakeProfit"));
        assertTrue(html.contains("60"));
        assertTrue(html.contains("Kein eindeutig passendes MT5-Bild gefunden"));
        assertFalse(html.contains("chart_1"));
        assertFalse(html.toLowerCase().contains("chart.js"));
    }

    @Test
    public void testGenerateSelectsNewestMatchingImageFromTesterIni() throws IOException {
        Path outDir = tempFolder.newFolder("artifact-reports").toPath();
        Pass bt = new Pass();
        bt.setPassNumber(101);
        bt.setProfit(100.0);
        Pass lt = new Pass();
        lt.setPassNumber(101);
        lt.setFromDate("2022-08-01");
        lt.setToDate("2026-08-01");
        lt.setDrawdownPercent(8.57);
        CombinedPass pass = new CombinedPass(bt, null, lt, 50.0, 1.0, "");
        pass.setStrategyName("Strat 101");
        pass.setSymbol("AUDCAD");

        writeRun(outDir.resolve("lt-run"),
                "Longterm_Pass101.set", "2022.08.01", "2026.08.01", 8.57, 1_000L);
        writeRun(outDir.resolve("verify-run"),
                "Backtester_TestEA_Verify_Pass101.set", "2022.08.01", "2026.08.01", 99.99, 2_000L);
        Files.writeString(outDir.resolve("verify-run").resolve(BacktestStatisticsArtifact.FILE_NAME),
                "{\"schemaVersion\":1,\"relativeEquityDrawdownPercent\":27.54}");
        writeRun(outDir.resolve("wrong-range-run"),
                "Backtester_TestEA_Verify_Pass101.set", "2025.01.01", "2026.08.01", 3.21, 3_000L);

        Path report = DatabankHtmlViewerGenerator.generate(
                "ticktest", "data0", List.of(pass), outDir,
                "TestEA", "AUDCAD", "M5", "gallery-token");
        String html = Files.readString(report);

        assertTrue(html.contains("Kurzzeitdaten: <strong>data0</strong>"));
        assertTrue(html.contains("verify-run/BacktestReport.png"));
        assertTrue(html.contains("data-artifact=\"verify-run\""));
        assertFalse(html.contains("lt-run/BacktestReport.png"));
        assertFalse(html.contains("wrong-range-run/BacktestReport.png"));
        assertTrue(html.contains("gallery-token"));
        assertTrue(html.contains("LT: 27.54%"));
        assertTrue(html.contains("Verifikation ·"));
        assertTrue(html.contains("· DD 27.54%"));
    }

    @Test
    public void testGenerateBackfillsStructuredStatisticsForLegacyReport() throws IOException {
        Path outDir = tempFolder.newFolder("legacy-artifact-reports").toPath();
        Pass bt = new Pass();
        bt.setPassNumber(202);
        Pass lt = new Pass();
        lt.setPassNumber(202);
        lt.setFromDate("2022-08-01");
        lt.setToDate("2026-08-01");
        CombinedPass pass = new CombinedPass(bt, null, lt, 50.0, 1.0, "");
        pass.setStrategyName("Strat 202");
        pass.setSymbol("AUDCAD");

        Path run = outDir.resolve("legacy-run");
        writeRun(run, "Longterm_Pass202.set", "2022.08.01", "2026.08.01", 99.99, 1_000L);
        Files.writeString(run.resolve("report.htm"), "<table>"
                + "<tr><td colspan=\"6\">Ergebnisse</td></tr>"
                + "<tr><td>Rückgang Equity maximal:</td><td>1000.00 (10.00%)</td>"
                + "<td>Rückgang Equity relativ:</td><td>12.50% (900.00)</td></tr>"
                + "<tr><td>Ticks:</td><td>123456</td></tr>"
                + "<tr><td colspan=\"6\">Orders</td></tr></table>");

        DatabankHtmlViewerGenerator.generate("legacy", "data0", List.of(pass), outDir,
                "TestEA", "AUDCAD", "M5", "token");

        Path statistics = run.resolve(BacktestStatisticsArtifact.FILE_NAME);
        assertTrue(Files.isRegularFile(statistics));
        String json = Files.readString(statistics);
        assertTrue(json.contains("\"relativeEquityDrawdownPercent\": 12.5"));
        assertTrue(json.contains("\"Ticks\": \"123456\""));
    }

    private static void writeRun(Path directory, String preset, String from, String to,
                                 double drawdown, long modifiedMillis) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("tester.ini"),
                "Expert=TestEA\n"
                        + "ExpertParameters=" + preset + "\n"
                        + "Symbol=AUDCAD\n"
                        + "Period=M5\n"
                        + "Model=0\n"
                        + "ExecutionMode=0\n"
                        + "FromDate=" + from + "\n"
                        + "ToDate=" + to + "\n"
                        + "Deposit=10000\n"
                        + "Currency=USD\n"
                        + "Leverage=1:100\n");
        Path image = directory.resolve("BacktestReport.png");
        Files.write(image, new byte[]{1, 2, 3});
        Files.setLastModifiedTime(image, FileTime.fromMillis(modifiedMillis));
        Files.writeString(directory.resolve("summary.txt"), "Max Drawdown: " + drawdown + "%\n");
    }
}
