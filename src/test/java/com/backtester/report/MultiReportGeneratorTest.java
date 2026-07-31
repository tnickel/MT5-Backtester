package com.backtester.report;

import com.backtester.engine.MultiBacktestConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class MultiReportGeneratorTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testGenerateReport() throws IOException {
        // Setup config
        MultiBacktestConfig config = new MultiBacktestConfig();
        config.setFromDate(LocalDate.of(2025, 1, 1));
        config.setToDate(LocalDate.of(2025, 12, 31));
        config.setDeposit(10000);

        // Setup results
        List<BacktestResult> results = new ArrayList<>();

        // Result 1: Success with profit
        BacktestResult r1 = new BacktestResult();
        r1.setExpert("TestRobot1");
        r1.setSymbol("EURUSD");
        r1.setPeriod("M5");
        r1.setSuccess(true);
        r1.setInitialDeposit(10000.0);
        r1.setTotalProfit(500.0);
        r1.setTotalTrades(3);
        r1.setMaxDrawdown(15.0);
        r1.setProfitFactor(2.8);

        List<double[]> hist1 = new ArrayList<>();
        // double[] format: [index, balance, equity, timestamp]
        // 2025-05-15: 1747267200000 ms
        hist1.add(new double[]{0, 10000.0, 10000.0, 1747267200000L});
        hist1.add(new double[]{1, 10200.0, 10200.0, 1747267200000L + 86400000L});
        hist1.add(new double[]{2, 10500.0, 10500.0, 1747267200000L + 2 * 86400000L});
        r1.setEquityHistory(hist1);
        results.add(r1);

        // Result 2: Success with loss
        BacktestResult r2 = new BacktestResult();
        r2.setExpert("TestRobot2");
        r2.setSymbol("GBPUSD");
        r2.setPeriod("M5");
        r2.setSuccess(true);
        r2.setInitialDeposit(10000.0);
        r2.setTotalProfit(-200.0);
        r2.setTotalTrades(2);
        r2.setMaxDrawdown(45.0);
        r2.setProfitFactor(0.65);

        List<double[]> hist2 = new ArrayList<>();
        // 2025-06-10: 1749513600000 ms
        hist2.add(new double[]{0, 10000.0, 10000.0, 1749513600000L});
        hist2.add(new double[]{1, 9800.0, 9800.0, 1749513600000L + 86400000L});
        r2.setEquityHistory(hist2);
        results.add(r2);

        // Result 3: Failed run
        BacktestResult r3 = new BacktestResult();
        r3.setExpert("TestRobot1");
        r3.setSymbol("USDJPY");
        r3.setPeriod("H1");
        r3.setSuccess(false);
        r3.setMessage("Metatrader did not launch");
        results.add(r3);

        // Generate report
        Path reportsDir = tempFolder.newFolder("reports").toPath();
        Path reportFile = MultiReportGenerator.generate(config, results, reportsDir);

        assertNotNull(reportFile);
        assertTrue(Files.exists(reportFile));

        // Read report content
        String htmlContent = new String(Files.readAllBytes(reportFile));

        // Verify summary values
        assertTrue(htmlContent.contains("Total Net Profit"));
        // Total net profit = 500 - 200 = 300 (could be formatted as +300.00 or +300,00 depending on locale)
        assertTrue(htmlContent.contains("300"));
        assertTrue(htmlContent.contains("+"));
        // Total trades = 3 + 2 = 5
        assertTrue(htmlContent.contains("Total Trades"));
        assertTrue(htmlContent.contains("5"));

        // Verify Date Range
        assertTrue(htmlContent.contains("2025-01-01 to 2025-12-31"));

        // Verify new cards for Portfolio Max Drawdown and Portfolio Recovery Factor
        assertTrue(htmlContent.contains("Portfolio Max Drawdown"));
        assertTrue(htmlContent.contains("Portfolio Recovery Factor"));
        assertTrue(htmlContent.contains("Simulation Model"));

        // Verify sections and charts
        assertTrue(htmlContent.contains("Portfolio Summary Analysis"));
        assertTrue(htmlContent.contains("Combined Equity Curve"));
        assertTrue(htmlContent.contains("Monthly Net Profit"));
        assertTrue(htmlContent.contains("Monthly Max Drawdown (Euros & %)"));
        assertTrue(htmlContent.contains("Monthly Recovery Factor"));
        assertTrue(htmlContent.contains("Detailed Runs"));

        // Verify individual runs list
        assertTrue(htmlContent.contains("TestRobot1"));
        assertTrue(htmlContent.contains("TestRobot2"));
        assertTrue(htmlContent.contains("FAILED: Metatrader did not launch"));

        // Verify Drawdown formatting colors and styles are outputted
        assertTrue(htmlContent.contains(".dd-low { color: #12b886; font-weight: bold; }"));
        assertTrue(htmlContent.contains(".dd-medium { color: #fab005; font-weight: bold; }"));
        assertTrue(htmlContent.contains(".dd-high { color: #fd7e14; font-weight: bold; }"));
        assertTrue(htmlContent.contains(".dd-critical { color: #fa5252; font-weight: bold; }"));
        assertTrue(htmlContent.contains(".neg-profit { color: #fa5252; font-weight: bold; }"));
        assertTrue(htmlContent.contains(".low-pf { color: #fa5252; font-weight: bold; }"));
        assertTrue(htmlContent.contains("class='dd-low'"));
        assertTrue(htmlContent.contains("class='dd-medium'"));
        assertTrue(htmlContent.contains("class='neg-profit'"));
        assertTrue(htmlContent.contains("class='low-pf'"));

        // Verify Export to PDF button and styles are present
        assertTrue(htmlContent.contains("class='export-btn no-print'"));
        assertTrue(htmlContent.contains("@media print"));
    }

    @Test
    public void testGenerateReportEmptyResults() throws IOException {
        MultiBacktestConfig config = new MultiBacktestConfig();
        List<BacktestResult> results = new ArrayList<>();
        Path reportsDir = tempFolder.newFolder("reports_empty").toPath();
        Path reportFile = MultiReportGenerator.generate(config, results, reportsDir);
        assertNotNull(reportFile);
        assertTrue(Files.exists(reportFile));
        String html = new String(Files.readAllBytes(reportFile));
        assertTrue(html.contains("Total Net Profit"));
    }

    @Test
    public void testGenerateReportAllFailed() throws IOException {
        MultiBacktestConfig config = new MultiBacktestConfig();
        List<BacktestResult> results = new ArrayList<>();
        BacktestResult r = new BacktestResult();
        r.setSuccess(false);
        r.setMessage("Crash");
        results.add(r);
        Path reportsDir = tempFolder.newFolder("reports_failed").toPath();
        Path reportFile = MultiReportGenerator.generate(config, results, reportsDir);
        assertNotNull(reportFile);
        String html = new String(Files.readAllBytes(reportFile));
        assertTrue(html.contains("FAILED: Crash"));
    }

    @Test
    public void testGenerateReportSingleResult() throws IOException {
        MultiBacktestConfig config = new MultiBacktestConfig();
        List<BacktestResult> results = new ArrayList<>();
        BacktestResult r = new BacktestResult();
        r.setSuccess(true);
        r.setTotalProfit(100.0);
        r.setTotalTrades(10);
        results.add(r);
        Path reportsDir = tempFolder.newFolder("reports_single").toPath();
        Path reportFile = MultiReportGenerator.generate(config, results, reportsDir);
        assertNotNull(reportFile);
        String html = new String(Files.readAllBytes(reportFile));
        assertTrue(html.contains("10"));
    }

    @Test
    public void testGenerateReportDifferentBaseDeposits() throws IOException {
        MultiBacktestConfig config = new MultiBacktestConfig();
        List<BacktestResult> results = new ArrayList<>();
        BacktestResult r1 = new BacktestResult();
        r1.setSuccess(true);
        r1.setInitialDeposit(5000.0);
        results.add(r1);
        BacktestResult r2 = new BacktestResult();
        r2.setSuccess(true);
        r2.setInitialDeposit(10000.0);
        results.add(r2);
        Path reportsDir = tempFolder.newFolder("reports_dep").toPath();
        Path reportFile = MultiReportGenerator.generate(config, results, reportsDir);
        assertNotNull(reportFile);
        assertTrue(Files.exists(reportFile));
    }

    @Test
    public void testGenerateReportTradesOutOfChronologicalOrder() throws IOException {
        MultiBacktestConfig config = new MultiBacktestConfig();
        List<BacktestResult> results = new ArrayList<>();
        
        BacktestResult r = new BacktestResult();
        r.setSuccess(true);
        r.setInitialDeposit(10000.0);
        
        List<double[]> hist = new ArrayList<>();
        hist.add(new double[]{0, 10000.0, 10000.0, 1740000000000L});
        hist.add(new double[]{1, 10100.0, 10100.0, 1740000000000L + 2000L});
        hist.add(new double[]{2, 10200.0, 10200.0, 1740000000000L + 1000L});
        r.setEquityHistory(hist);
        results.add(r);
        
        Path reportsDir = tempFolder.newFolder("reports_chrono").toPath();
        Path reportFile = MultiReportGenerator.generate(config, results, reportsDir);
        assertNotNull(reportFile);
    }

    @Test
    public void testGenerateReportMultipleMonths() throws IOException {
        MultiBacktestConfig config = new MultiBacktestConfig();
        List<BacktestResult> results = new ArrayList<>();
        
        BacktestResult r = new BacktestResult();
        r.setSuccess(true);
        r.setInitialDeposit(10000.0);
        
        List<double[]> hist = new ArrayList<>();
        hist.add(new double[]{0, 10000.0, 10000.0, 1747267200000L});
        hist.add(new double[]{1, 10500.0, 10500.0, 1747267200000L}); // May
        hist.add(new double[]{2, 10800.0, 10800.0, 1749945600000L}); // June
        hist.add(new double[]{3, 10700.0, 10700.0, 1752537600000L}); // July
        r.setEquityHistory(hist);
        results.add(r);
        
        Path reportsDir = tempFolder.newFolder("reports_months").toPath();
        Path reportFile = MultiReportGenerator.generate(config, results, reportsDir);
        assertNotNull(reportFile);
        String html = new String(Files.readAllBytes(reportFile));
        assertTrue(html.contains("Portfolio Summary Analysis"));
    }

    @Test
    public void testGenerateReportNoTimestamps() throws IOException {
        MultiBacktestConfig config = new MultiBacktestConfig();
        List<BacktestResult> results = new ArrayList<>();
        
        BacktestResult r = new BacktestResult();
        r.setSuccess(true);
        r.setInitialDeposit(10000.0);
        
        List<double[]> hist = new ArrayList<>();
        hist.add(new double[]{0, 10000.0, 10000.0, 0});
        hist.add(new double[]{1, 10100.0, 10100.0, 0});
        r.setEquityHistory(hist);
        results.add(r);
        
        Path reportsDir = tempFolder.newFolder("reports_no_ts").toPath();
        Path reportFile = MultiReportGenerator.generate(config, results, reportsDir);
        assertNotNull(reportFile);
    }
}
