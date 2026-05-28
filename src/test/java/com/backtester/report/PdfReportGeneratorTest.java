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
}
