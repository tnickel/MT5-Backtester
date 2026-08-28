package com.backtester.report;

import com.backtester.database.DatabaseManager;
import com.backtester.engine.WorkflowEngine;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class PdfReportGeneratorTest {

    private File tempDbFile;
    private File tempPdfFile;
    private DatabaseManager db;

    @Before
    public void setUp() throws Exception {
        // Reset the singleton instance using reflection so we start with a clean DatabaseManager
        try {
            Field instanceField = DatabaseManager.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            instanceField.set(null, null);
        } catch (Exception e) {
            // Ignore
        }

        tempDbFile = File.createTempFile("pdf_test_db_", ".db");
        tempDbFile.deleteOnExit();

        db = DatabaseManager.getInstance();
        Field dbUrlField = DatabaseManager.class.getDeclaredField("dbUrl");
        dbUrlField.setAccessible(true);
        dbUrlField.set(db, "jdbc:sqlite:" + tempDbFile.getAbsolutePath());

        java.lang.reflect.Method initMethod = DatabaseManager.class.getDeclaredMethod("initializeDatabase");
        initMethod.setAccessible(true);
        initMethod.invoke(db);

        tempPdfFile = File.createTempFile("strategy_report_test_", ".pdf");
        tempPdfFile.deleteOnExit();
    }

    @After
    public void tearDown() {
        if (tempDbFile != null && tempDbFile.exists()) {
            tempDbFile.delete();
        }
        if (tempPdfFile != null && tempPdfFile.exists()) {
            tempPdfFile.delete();
        }
        // Reset the singleton instance so it doesn't leak the temporary dbUrl to other tests
        try {
            Field instanceField = DatabaseManager.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            instanceField.set(null, null);
        } catch (Exception e) {
            // Ignore
        }
    }

    @Test
    public void testGenerateReport_withSensitivitySweeps() throws Exception {
        // 1. Setup workflow engine mock state
        WorkflowEngine engine = new WorkflowEngine(null);
        engine.changeExpert("CC_ADR_Stoch_Grid");
        engine.setSymbol("EURUSD");
        engine.setPeriod("H1");
        engine.setFromDate(LocalDate.of(2026, 1, 1));
        engine.setToDate(LocalDate.of(2026, 4, 30));
        engine.setDeposit(10000);
        engine.setCurrency("USD");
        engine.setLeverage("1:100");
        engine.setTickModel(2);

        // 2. Setup mock CombinedPass
        Pass btPass = new Pass();
        btPass.setPassNumber(12345);
        btPass.setProfit(1500.0);
        btPass.setTotalTrades(120);
        btPass.setProfitFactor(2.1);
        btPass.setDrawdownPercent(8.5);
        btPass.setRecoveryFactor(5.2);
        btPass.setSharpeRatio(2.3);
        
        Map<String, String> params = new HashMap<>();
        params.put("InpLots", "0.02");
        params.put("InpADRPeriod", "26");
        btPass.setParameterValues(params);

        Pass fwPass = new Pass();
        fwPass.setPassNumber(12345);
        fwPass.setProfit(500.0);
        fwPass.setTotalTrades(35);
        fwPass.setProfitFactor(1.8);
        fwPass.setDrawdownPercent(4.2);
        fwPass.setRecoveryFactor(3.1);
        fwPass.setSharpeRatio(1.9);

        CombinedPass cp = new CombinedPass(btPass, fwPass, 85.0, 1.0, "");

        // 3. Save sensitivity details to DB so PdfReportGenerator will find sweeps
        long timestamp = System.currentTimeMillis();
        String curveJson = "[" +
                "{\"paramValue\":24.0,\"profit\":70.15}," +
                "{\"paramValue\":25.0,\"profit\":80.50}," +
                "{\"paramValue\":26.0,\"profit\":83.91}," +
                "{\"paramValue\":27.0,\"profit\":85.00}," +
                "{\"paramValue\":28.0,\"profit\":82.10}" +
                "]";

        db.saveSensitivityDetail(
                timestamp, 12345, "Pass 12345", "CC_ADR_Stoch_Grid", "EURUSD",
                "InpADRPeriod", "26.0", "FW", 83.91, 70.15, 5.2, 0.95,
                24.0, 28.0, 5, "1.0", "24.0", "28.0", curveJson, "ROBUST"
        );

        // 4. Generate report
        PdfReportGenerator.generateReport(engine, cp, tempPdfFile);

        // 5. Verify file was created and has content
        assertTrue("PDF file should exist", tempPdfFile.exists());
        assertTrue("PDF file should not be empty", tempPdfFile.length() > 0);
    }

    @Test
    public void failedGenerationRemovesPartialPdf() throws Exception {
        WorkflowEngine engine = new WorkflowEngine(null);
        try {
            PdfReportGenerator.generateReport(engine, null, tempPdfFile);
            fail("Expected invalid report input to fail");
        } catch (Exception expected) {
            // The important contract is that the opened output is closed and the partial file removed.
        }
        assertFalse("Partial PDF must not remain after generation failure", tempPdfFile.exists());
    }

    @Test
    public void testGeneratePortfolioReport() throws Exception {
        // 1. Setup workflow engine mock state
        WorkflowEngine engine = new WorkflowEngine(null);
        engine.changeExpert("MyPortfolioEA");
        engine.setSymbol("GBPUSD");
        engine.setPeriod("M15");
        engine.setFromDate(LocalDate.of(2026, 1, 1));
        engine.setToDate(LocalDate.of(2026, 5, 20));
        engine.setDeposit(5000);
        engine.setCurrency("EUR");
        engine.setLeverage("1:200");

        // 2. Setup list of diverse passes
        List<CombinedPass> passes = new ArrayList<>();
        
        Pass bt1 = new Pass();
        bt1.setPassNumber(1);
        bt1.setProfit(800.0);
        bt1.setTotalTrades(80);
        bt1.setProfitFactor(1.9);
        bt1.setDrawdownPercent(12.0);
        passes.add(new CombinedPass(bt1, null, 75.0, 1.0, ""));

        Pass bt2 = new Pass();
        bt2.setPassNumber(2);
        bt2.setProfit(1200.0);
        bt2.setTotalTrades(95);
        bt2.setProfitFactor(2.2);
        bt2.setDrawdownPercent(9.0);
        passes.add(new CombinedPass(bt2, null, 88.0, 1.0, ""));

        // 3. Generate report
        PdfReportGenerator.generatePortfolioReport(engine, passes, tempPdfFile);

        // 4. Verify file was created and has content
        assertTrue("Portfolio PDF file should exist", tempPdfFile.exists());
        assertTrue("Portfolio PDF file should not be empty", tempPdfFile.length() > 0);
    }

    @Test
    public void testGenerateReport_ZeroTrades() throws Exception {
        // Setup workflow engine mock state
        WorkflowEngine engine = new WorkflowEngine(null);
        engine.changeExpert("CC_ADR_Stoch_Grid");
        engine.setSymbol("EURUSD");
        engine.setPeriod("H1");
        engine.setFromDate(LocalDate.of(2026, 1, 1));
        engine.setToDate(LocalDate.of(2026, 4, 30));
        engine.setDeposit(10000);
        engine.setCurrency("USD");
        engine.setLeverage("1:100");
        engine.setTickModel(2);

        // Setup mock CombinedPass with 0 trades
        Pass btPass = new Pass();
        btPass.setPassNumber(999);
        btPass.setProfit(0.0);
        btPass.setTotalTrades(0);
        btPass.setProfitFactor(1.0);
        btPass.setDrawdownPercent(0.0);
        btPass.setRecoveryFactor(0.0);
        btPass.setSharpeRatio(0.0);
        
        Map<String, String> params = new HashMap<>();
        params.put("InpLots", "0.01");
        btPass.setParameterValues(params);

        CombinedPass cp = new CombinedPass(btPass, null, 50.0, 1.0, "");

        // Generate report - should not crash with ArithmeticException
        PdfReportGenerator.generateReport(engine, cp, tempPdfFile);

        assertTrue("PDF file should exist", tempPdfFile.exists());
        assertTrue("PDF file should not be empty", tempPdfFile.length() > 0);
    }

    @Test
    public void testGeneratePortfolioReport_WithForwardResults() throws Exception {
        WorkflowEngine engine = new WorkflowEngine(null);
        engine.changeExpert("MyPortfolioEA");
        engine.setSymbol("GBPUSD");
        engine.setPeriod("M15");
        engine.setFromDate(LocalDate.of(2026, 1, 1));
        engine.setToDate(LocalDate.of(2026, 5, 20));
        engine.setDeposit(5000);
        engine.setCurrency("EUR");
        engine.setLeverage("1:200");

        List<CombinedPass> passes = new ArrayList<>();
        
        Pass bt1 = new Pass();
        bt1.setPassNumber(1);
        bt1.setProfit(800.0);
        bt1.setTotalTrades(80);
        bt1.setProfitFactor(1.9);
        bt1.setDrawdownPercent(12.0);

        Pass fw1 = new Pass();
        fw1.setPassNumber(1);
        fw1.setProfit(200.0);
        fw1.setTotalTrades(25);
        fw1.setProfitFactor(1.5);
        fw1.setDrawdownPercent(5.0);

        passes.add(new CombinedPass(bt1, fw1, 82.0, 1.0, ""));

        // Generate report
        PdfReportGenerator.generatePortfolioReport(engine, passes, tempPdfFile);

        assertTrue("Portfolio PDF file should exist", tempPdfFile.exists());
        assertTrue("Portfolio PDF file should not be empty", tempPdfFile.length() > 0);
    }

    @Test
    public void testGenerateReport_WithKiReport() throws Exception {
        WorkflowEngine engine = new WorkflowEngine(null);
        engine.changeExpert("CC_ADR_Stoch_Grid");
        engine.setSymbol("EURUSD");
        engine.setPeriod("H1");
        engine.setFromDate(LocalDate.of(2026, 1, 1));
        engine.setToDate(LocalDate.of(2026, 4, 30));
        engine.setDeposit(10000);
        engine.setCurrency("USD");
        engine.setLeverage("1:100");
        engine.setTickModel(2);

        Pass btPass = new Pass();
        btPass.setPassNumber(574);
        btPass.setProfit(1500.0);
        btPass.setTotalTrades(120);
        btPass.setProfitFactor(2.1);
        btPass.setDrawdownPercent(8.5);
        btPass.setRecoveryFactor(5.2);
        btPass.setSharpeRatio(2.3);
        
        Map<String, String> params = new HashMap<>();
        params.put("InpLots", "0.02");
        btPass.setParameterValues(params);

        CombinedPass cp = new CombinedPass(btPass, null, 85.0, 1.0, "");

        String kiReportText = "TEIL 1:\n" +
                "| Pass | Status | Score | Profit (BT/FW) | Trades (BT/FW) | CV worst | Fragile | Kurvenform | Fazit |\n" +
                "|---|---|---|---|---|---|---|---|---|\n" +
                "| 574 | Fragil | 45 | 1214 / 1149 | 303 / 310 | 200.00% | 3 | Chaotisch | Inkonsistent |\n\n" +
                "TEIL 2:\n" +
                "STABILITY_SCORE|574|45\n\n" +
                "TEIL 3:\n" +
                "**Pass 574 (45):** Der StopLoss zeigt ein chaotisches Verhalten.";
        engine.setKiReportText(kiReportText);

        PdfReportGenerator.generateReport(engine, cp, tempPdfFile);

        assertTrue("PDF file should exist", tempPdfFile.exists());
        assertTrue("PDF file should not be empty", tempPdfFile.length() > 0);
    }

    @Test
    public void testParseKiTableResult() throws Exception {
        // Test parsing with valid markdown table
        String kiReportText = "TEIL 1:\n" +
                "| Pass | Status | Score | Profit (BT/FW) | Trades (BT/FW) | CV worst | Fragile | Kurvenform | Fazit |\n" +
                "|---|---|---|---|---|---|---|---|---|\n" +
                "| 101 | Robust | 85 | 1000 / 800 | 50 / 40 | 12.50% | 0 | Plateau | Sehr gut |\n" +
                "| 102 | Fragil | 40 | 500 / -100 | 30 / 10 | 45.20% | 2 | Peak | Riskant |\n";

        // Parse stable pass
        java.lang.reflect.Method method = PdfReportGenerator.class.getDeclaredMethod("parseKiTableResult", String.class, int.class);
        method.setAccessible(true);
        PdfReportGenerator.KiTableResult r1 = (PdfReportGenerator.KiTableResult) method.invoke(null, kiReportText, 101);
        assertEquals("Robust", r1.status);
        assertEquals("85", r1.score);
        assertEquals("Plateau", r1.kurvenform);
        assertEquals("0", r1.fragile);
        assertEquals("Sehr gut", r1.fazit);

        // Parse fragile pass
        PdfReportGenerator.KiTableResult r2 = (PdfReportGenerator.KiTableResult) method.invoke(null, kiReportText, 102);
        assertEquals("Fragil", r2.status);
        assertEquals("40", r2.score);
        assertEquals("Peak", r2.kurvenform);
        assertEquals("2", r2.fragile);
        assertEquals("Riskant", r2.fazit);

        // Parse non-existent pass
        PdfReportGenerator.KiTableResult r3 = (PdfReportGenerator.KiTableResult) method.invoke(null, kiReportText, 999);
        assertEquals("-", r3.status);
        assertEquals("-", r3.score);
        assertEquals("-", r3.kurvenform);
    }

    @Test
    public void testExtractPassKiReport() throws Exception {
        String kiReportText = "TEIL 3:\n" +
                "**Pass 101 (85):** Das ist die ausführliche Begründung.\n\n" +
                "**Pass 102 (40):** Und das ist ein anderes Urteil.\n";

        java.lang.reflect.Method method = PdfReportGenerator.class.getDeclaredMethod("extractPassKiReport", String.class, int.class);
        method.setAccessible(true);

        String review1 = (String) method.invoke(null, kiReportText, 101);
        assertTrue(review1.contains("Das ist die ausführliche Begründung"));

        String review2 = (String) method.invoke(null, kiReportText, 102);
        assertTrue(review2.contains("Und das ist ein anderes Urteil"));

        String review3 = (String) method.invoke(null, kiReportText, 999);
        assertTrue(review3.contains("Keine detaillierte KI-Begründung"));
    }
}
