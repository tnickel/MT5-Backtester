package com.backtester.report;

import com.backtester.database.DatabaseManager;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for multi-backtest batch persistence:
 * - Save a batch with multiple BacktestResult objects
 * - Load and reconstruct BatchRun objects
 * - Delete batches
 * - Round-trip verification of all result fields
 */
public class MultiBatchPersistenceTest {

    private DatabaseManager db;
    private File tempDbFile;
    private final Gson gson = new Gson();
    private final Type resultListType = new TypeToken<List<BacktestResult>>(){}.getType();

    @Before
    public void setUp() throws Exception {
        tempDbFile = File.createTempFile("multi_batch_test_", ".db");
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
    }

    private BacktestResult createResult(String symbol, String period, double profit) {
        BacktestResult r = new BacktestResult();
        r.setExpert("TestEA");
        r.setSymbol(symbol);
        r.setPeriod(period);
        r.setSuccess(true);
        r.setTotalProfit(profit);
        r.setTotalTrades(100);
        r.setWinRate(60.0);
        r.setMaxDrawdown(10.0);
        r.setProfitFactor(1.5);
        r.setOutputDirectory("d:/output/" + symbol + "_" + period);
        return r;
    }

    @Test
    public void testBatchRoundTrip_singleBatch() {
        List<BacktestResult> results = new ArrayList<>();
        results.add(createResult("EURUSD", "H1", 500.0));
        results.add(createResult("GBPUSD", "H1", -100.0));
        results.add(createResult("USDJPY", "H4", 300.0));

        String resultsJson = gson.toJson(results);
        db.saveBatch("Batch 12:30:00 (3 Tasks)", 1000L, "d:/reports/batch.html", resultsJson);

        // Load back
        List<Object[]> batches = db.getAllBatches();
        assertEquals(1, batches.size());
        assertEquals("Batch 12:30:00 (3 Tasks)", batches.get(0)[1]);

        // Deserialize results
        String loadedJson = (String) batches.get(0)[4];
        List<BacktestResult> loadedResults = gson.fromJson(loadedJson, resultListType);
        assertNotNull(loadedResults);
        assertEquals(3, loadedResults.size());

        assertEquals("EURUSD", loadedResults.get(0).getSymbol());
        assertEquals(500.0, loadedResults.get(0).getTotalProfit(), 0.001);

        assertEquals("GBPUSD", loadedResults.get(1).getSymbol());
        assertEquals(-100.0, loadedResults.get(1).getTotalProfit(), 0.001);

        assertEquals("USDJPY", loadedResults.get(2).getSymbol());
        assertEquals("H4", loadedResults.get(2).getPeriod());
    }

    @Test
    public void testBatchRoundTrip_multipleBatches() {
        List<BacktestResult> batch1Results = new ArrayList<>();
        batch1Results.add(createResult("EURUSD", "H1", 100.0));
        db.saveBatch("Morning Batch", 1000L, "", gson.toJson(batch1Results));

        List<BacktestResult> batch2Results = new ArrayList<>();
        batch2Results.add(createResult("GBPUSD", "M5", 200.0));
        batch2Results.add(createResult("XAUUSD", "D1", 1500.0));
        db.saveBatch("Evening Batch", 2000L, "", gson.toJson(batch2Results));

        List<Object[]> batches = db.getAllBatches();
        assertEquals(2, batches.size());

        // First = newest
        assertEquals("Evening Batch", batches.get(0)[1]);
        List<BacktestResult> loaded2 = gson.fromJson((String) batches.get(0)[4], resultListType);
        assertEquals(2, loaded2.size());

        // Second = oldest
        assertEquals("Morning Batch", batches.get(1)[1]);
        List<BacktestResult> loaded1 = gson.fromJson((String) batches.get(1)[4], resultListType);
        assertEquals(1, loaded1.size());
    }

    @Test
    public void testBatchDelete_specificBatch() {
        db.saveBatch("Keep", 1000L, "", "[]");
        db.saveBatch("Delete", 2000L, "", "[]");

        List<Object[]> before = db.getAllBatches();
        assertEquals(2, before.size());

        // Delete the "Delete" batch
        int deleteId = -1;
        for (Object[] row : before) {
            if ("Delete".equals(row[1])) {
                deleteId = (int) row[0];
                break;
            }
        }
        assertTrue("Should find batch to delete", deleteId > 0);
        db.deleteBatch(deleteId);

        List<Object[]> after = db.getAllBatches();
        assertEquals(1, after.size());
        assertEquals("Keep", after.get(0)[1]);
    }

    @Test
    public void testBatch_emptyResults() {
        db.saveBatch("EmptyBatch", 1000L, "", "[]");

        List<Object[]> batches = db.getAllBatches();
        assertEquals(1, batches.size());

        List<BacktestResult> loaded = gson.fromJson((String) batches.get(0)[4], resultListType);
        assertNotNull(loaded);
        assertEquals(0, loaded.size());
    }

    @Test
    public void testBatch_resultsFieldCompleteness() {
        BacktestResult original = createResult("EURUSD", "H1", 999.99);
        original.setSharpeRatio(3.14);
        original.setRecoveryFactor(7.77);
        original.setExpectedPayoff(12.34);
        original.setInitialDeposit(50000.0);
        original.setFinalBalance(50999.99);

        List<BacktestResult> results = new ArrayList<>();
        results.add(original);
        db.saveBatch("DetailedBatch", 1000L, "", gson.toJson(results));

        List<Object[]> batches = db.getAllBatches();
        List<BacktestResult> loaded = gson.fromJson((String) batches.get(0)[4], resultListType);

        BacktestResult l = loaded.get(0);
        assertEquals(original.getSharpeRatio(), l.getSharpeRatio(), 0.001);
        assertEquals(original.getRecoveryFactor(), l.getRecoveryFactor(), 0.001);
        assertEquals(original.getExpectedPayoff(), l.getExpectedPayoff(), 0.001);
        assertEquals(original.getInitialDeposit(), l.getInitialDeposit(), 0.001);
        assertEquals(original.getFinalBalance(), l.getFinalBalance(), 0.001);
        assertEquals(original.getOutputDirectory(), l.getOutputDirectory());
    }

    @Test
    public void testBatch_alsoSavesToHistoryRuns() {
        // Simulate what MultiBacktestView does: save batch + individual runs
        BacktestResult r = createResult("XAUUSD", "D1", 2000.0);
        List<BacktestResult> results = new ArrayList<>();
        results.add(r);

        db.saveBatch("GoldBatch", 1000L, "", gson.toJson(results));

        // Also save individual run like the view does
        db.saveRun("MULTI_BACKTEST", r.getExpert(), 1000L, gson.toJson(r), r.getOutputDirectory());

        // Verify both tables have data
        assertEquals(1, db.getAllBatches().size());

        List<com.backtester.database.HistoryRun> multiRuns = db.getRunsByType("MULTI_BACKTEST");
        assertEquals(1, multiRuns.size());

        BacktestResult fromHistory = gson.fromJson(multiRuns.get(0).getResultJson(), BacktestResult.class);
        assertEquals("XAUUSD", fromHistory.getSymbol());
        assertEquals(2000.0, fromHistory.getTotalProfit(), 0.001);
    }

    @Test
    public void testBatch_largeBatch() {
        // Simulate a large batch (15 symbols × 6 timeframes = 90 results)
        List<BacktestResult> results = new ArrayList<>();
        String[] symbols = {"EURUSD", "GBPUSD", "USDJPY", "USDCHF", "AUDUSD",
                            "NZDUSD", "USDCAD", "EURGBP", "EURJPY", "GBPJPY",
                            "AUDCAD", "AUDNZD", "XAUUSD", "XAGUSD", "XTIUSD"};
        String[] periods = {"M1", "M5", "M15", "H1", "H4", "D1"};

        for (String sym : symbols) {
            for (String per : periods) {
                results.add(createResult(sym, per, Math.random() * 2000 - 500));
            }
        }

        assertEquals(90, results.size());

        String json = gson.toJson(results);
        db.saveBatch("MegaBatch", 1000L, "", json);

        List<Object[]> batches = db.getAllBatches();
        assertEquals(1, batches.size());

        List<BacktestResult> loaded = gson.fromJson((String) batches.get(0)[4], resultListType);
        assertEquals(90, loaded.size());
    }
}
