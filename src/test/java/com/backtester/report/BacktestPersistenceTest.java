package com.backtester.report;

import com.backtester.database.DatabaseManager;
import com.backtester.database.HistoryRun;
import com.google.gson.Gson;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests that BacktestResult objects survive a full round-trip through the database:
 * serialize → save → load → deserialize → compare.
 */
public class BacktestPersistenceTest {

    private DatabaseManager db;
    private File tempDbFile;
    private final Gson gson = new Gson();

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

        tempDbFile = File.createTempFile("bt_persist_test_", ".db");
        tempDbFile.deleteOnExit();

        db = DatabaseManager.getInstance();
        Field dbUrlField = DatabaseManager.class.getDeclaredField("dbUrl");
        dbUrlField.setAccessible(true);
        dbUrlField.set(db, "jdbc:sqlite:" + tempDbFile.getAbsolutePath());

        java.lang.reflect.Method initMethod = DatabaseManager.class.getDeclaredMethod("initializeDatabase");
        initMethod.setAccessible(true);
        initMethod.invoke(db);
    }

    @After
    public void tearDown() {
        if (tempDbFile != null && tempDbFile.exists()) {
            tempDbFile.delete();
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

    private BacktestResult createSampleResult() {
        BacktestResult result = new BacktestResult();
        result.setExpert("TestEA_v2");
        result.setSymbol("EURUSD");
        result.setPeriod("H1");
        result.setSuccess(true);
        result.setTotalProfit(1234.56);
        result.setGrossProfit(5000.0);
        result.setGrossLoss(-3765.44);
        result.setTotalTrades(150);
        result.setWinRate(65.5);
        result.setMaxDrawdown(12.34);
        result.setMaxDrawdownAbsolute(500.0);
        result.setMaxDrawdownPercent(12.34);
        result.setProfitFactor(1.33);
        result.setSharpeRatio(2.15);
        result.setRecoveryFactor(4.5);
        result.setExpectedPayoff(8.23);
        result.setShortPositions(75);
        result.setLongPositions(75);
        result.setProfitTrades(98);
        result.setLossTrades(52);
        result.setInitialDeposit(10000.0);
        result.setFinalBalance(11234.56);
        result.setLargestWin(250.0);
        result.setLargestLoss(-180.0);
        result.setAverageWin(51.02);
        result.setAverageLoss(-72.41);
        result.setOutputDirectory("d:/output/test");
        result.setMessage("Test completed");
        result.setConfigInfo("Custom (3 modified)");
        result.setUsedDefaultConfig(false);
        return result;
    }

    @Test
    public void testBacktestResult_fullRoundTrip() {
        BacktestResult original = createSampleResult();

        // Serialize and save
        String json = gson.toJson(original);
        db.saveRun("BACKTEST", original.getExpert(), System.currentTimeMillis(), json, original.getOutputDirectory());

        // Load and deserialize
        List<HistoryRun> runs = db.getRunsByType("BACKTEST");
        assertEquals(1, runs.size());

        BacktestResult loaded = gson.fromJson(runs.get(0).getResultJson(), BacktestResult.class);
        assertNotNull("Loaded result should not be null", loaded);

        // Verify all fields survived the round-trip
        assertEquals(original.getExpert(), loaded.getExpert());
        assertEquals(original.getSymbol(), loaded.getSymbol());
        assertEquals(original.getPeriod(), loaded.getPeriod());
        assertEquals(original.isSuccess(), loaded.isSuccess());
        assertEquals(original.getTotalProfit(), loaded.getTotalProfit(), 0.001);
        assertEquals(original.getGrossProfit(), loaded.getGrossProfit(), 0.001);
        assertEquals(original.getGrossLoss(), loaded.getGrossLoss(), 0.001);
        assertEquals(original.getTotalTrades(), loaded.getTotalTrades());
        assertEquals(original.getWinRate(), loaded.getWinRate(), 0.001);
        assertEquals(original.getMaxDrawdown(), loaded.getMaxDrawdown(), 0.001);
        assertEquals(original.getMaxDrawdownAbsolute(), loaded.getMaxDrawdownAbsolute(), 0.001);
        assertEquals(original.getMaxDrawdownPercent(), loaded.getMaxDrawdownPercent(), 0.001);
        assertEquals(original.getProfitFactor(), loaded.getProfitFactor(), 0.001);
        assertEquals(original.getSharpeRatio(), loaded.getSharpeRatio(), 0.001);
        assertEquals(original.getRecoveryFactor(), loaded.getRecoveryFactor(), 0.001);
        assertEquals(original.getExpectedPayoff(), loaded.getExpectedPayoff(), 0.001);
        assertEquals(original.getShortPositions(), loaded.getShortPositions());
        assertEquals(original.getLongPositions(), loaded.getLongPositions());
        assertEquals(original.getProfitTrades(), loaded.getProfitTrades());
        assertEquals(original.getLossTrades(), loaded.getLossTrades());
        assertEquals(original.getInitialDeposit(), loaded.getInitialDeposit(), 0.001);
        assertEquals(original.getFinalBalance(), loaded.getFinalBalance(), 0.001);
        assertEquals(original.getLargestWin(), loaded.getLargestWin(), 0.001);
        assertEquals(original.getLargestLoss(), loaded.getLargestLoss(), 0.001);
        assertEquals(original.getAverageWin(), loaded.getAverageWin(), 0.001);
        assertEquals(original.getAverageLoss(), loaded.getAverageLoss(), 0.001);
        assertEquals(original.getOutputDirectory(), loaded.getOutputDirectory());
        assertEquals(original.getMessage(), loaded.getMessage());
        assertEquals(original.getConfigInfo(), loaded.getConfigInfo());
        assertEquals(original.isUsedDefaultConfig(), loaded.isUsedDefaultConfig());
    }

    @Test
    public void testBacktestResult_multipleResults() {
        BacktestResult r1 = createSampleResult();
        r1.setSymbol("EURUSD");
        r1.setTotalProfit(100.0);

        BacktestResult r2 = createSampleResult();
        r2.setSymbol("GBPUSD");
        r2.setTotalProfit(200.0);

        BacktestResult r3 = createSampleResult();
        r3.setSymbol("USDJPY");
        r3.setTotalProfit(-50.0);

        db.saveRun("BACKTEST", r1.getExpert(), 1000L, gson.toJson(r1), r1.getOutputDirectory());
        db.saveRun("BACKTEST", r2.getExpert(), 2000L, gson.toJson(r2), r2.getOutputDirectory());
        db.saveRun("BACKTEST", r3.getExpert(), 3000L, gson.toJson(r3), r3.getOutputDirectory());

        List<HistoryRun> runs = db.getRunsByType("BACKTEST");
        assertEquals(3, runs.size());

        // Verify order (DESC by timestamp)
        BacktestResult loaded1 = gson.fromJson(runs.get(0).getResultJson(), BacktestResult.class);
        BacktestResult loaded2 = gson.fromJson(runs.get(1).getResultJson(), BacktestResult.class);
        BacktestResult loaded3 = gson.fromJson(runs.get(2).getResultJson(), BacktestResult.class);

        assertEquals("USDJPY", loaded1.getSymbol()); // newest
        assertEquals("GBPUSD", loaded2.getSymbol());
        assertEquals("EURUSD", loaded3.getSymbol()); // oldest
    }

    @Test
    public void testBacktestResult_doesNotInterfereWithOtherTypes() {
        BacktestResult bt = createSampleResult();
        db.saveRun("BACKTEST", bt.getExpert(), 1000L, gson.toJson(bt), "");
        db.saveRun("OPTIMIZATION", "OtherEA", 2000L, "{\"passes\":5}", "");
        db.saveRun("ROBUSTNESS", "RobustEA", 3000L, "{\"shifts\":10}", "");

        List<HistoryRun> backtestOnly = db.getRunsByType("BACKTEST");
        assertEquals("Only backtest runs", 1, backtestOnly.size());

        BacktestResult loaded = gson.fromJson(backtestOnly.get(0).getResultJson(), BacktestResult.class);
        assertEquals(bt.getExpert(), loaded.getExpert());
    }

    @Test
    public void testBacktestResult_withEquityHistory() {
        BacktestResult result = createSampleResult();
        java.util.List<double[]> equity = new java.util.ArrayList<>();
        equity.add(new double[]{1, 10000, 10000});
        equity.add(new double[]{2, 10100, 10050});
        equity.add(new double[]{3, 10250, 10200});
        result.setEquityHistory(equity);

        String json = gson.toJson(result);
        db.saveRun("BACKTEST", result.getExpert(), 1000L, json, "");

        List<HistoryRun> runs = db.getRunsByType("BACKTEST");
        BacktestResult loaded = gson.fromJson(runs.get(0).getResultJson(), BacktestResult.class);

        assertNotNull(loaded.getEquityHistory());
        assertEquals(3, loaded.getEquityHistory().size());
        assertEquals(10250, loaded.getEquityHistory().get(2)[1], 0.001);
    }

    @Test
    public void testBacktestResult_withZeroValues() {
        BacktestResult result = new BacktestResult();
        // All defaults (zeros)
        result.setExpert("EmptyEA");
        result.setSuccess(false);

        String json = gson.toJson(result);
        db.saveRun("BACKTEST", result.getExpert(), 1000L, json, "");

        List<HistoryRun> runs = db.getRunsByType("BACKTEST");
        BacktestResult loaded = gson.fromJson(runs.get(0).getResultJson(), BacktestResult.class);

        assertEquals("EmptyEA", loaded.getExpert());
        assertFalse(loaded.isSuccess());
        assertEquals(0.0, loaded.getTotalProfit(), 0.001);
        assertEquals(0, loaded.getTotalTrades());
    }
}
