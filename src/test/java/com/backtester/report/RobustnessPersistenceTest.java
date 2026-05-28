package com.backtester.report;

import com.backtester.database.DatabaseManager;
import com.backtester.database.HistoryRun;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for robustness result persistence:
 * - Verifies results are saved to HISTORY_RUNS with type "ROBUSTNESS"
 * - Verifies results can be loaded and reconstructed for the ResultsList
 * - Verifies correct filtering by type
 */
public class RobustnessPersistenceTest {

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

        tempDbFile = File.createTempFile("robust_persist_test_", ".db");
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

    @Test
    public void testRobustnessRun_saveAndRetrieve() {
        JsonObject metrics = new JsonObject();
        metrics.addProperty("targetMetric", "Profit");
        metrics.addProperty("shifts", 10);
        metrics.addProperty("shiftDays", 7);
        metrics.addProperty("strategyName", "Pass_12345");

        db.saveRun("ROBUSTNESS", "TestEA", 1000L, metrics.toString(), "/reports/robustness_report.html");

        List<HistoryRun> runs = db.getRunsByType("ROBUSTNESS");
        assertEquals(1, runs.size());

        HistoryRun run = runs.get(0);
        assertEquals("ROBUSTNESS", run.getRunType());
        assertEquals("TestEA", run.getExpertName());
        assertEquals(1000L, run.getTimestamp());
        assertEquals("/reports/robustness_report.html", run.getHtmlPath());

        // Parse the JSON metrics
        JsonObject loaded = gson.fromJson(run.getResultJson(), JsonObject.class);
        assertEquals("Profit", loaded.get("targetMetric").getAsString());
        assertEquals(10, loaded.get("shifts").getAsInt());
        assertEquals(7, loaded.get("shiftDays").getAsInt());
        assertEquals("Pass_12345", loaded.get("strategyName").getAsString());
    }

    @Test
    public void testRobustnessRun_reconstructLabel() {
        // Simulate what RobustnessView.loadResultsFromDb does
        JsonObject metrics = new JsonObject();
        metrics.addProperty("targetMetric", "Sharpe Ratio");
        metrics.addProperty("strategyName", "Strategy_A");

        db.saveRun("ROBUSTNESS", "MyEA", 2000L, metrics.toString(), "");

        List<HistoryRun> runs = db.getRunsByType("ROBUSTNESS");
        assertEquals(1, runs.size());

        HistoryRun run = runs.get(0);
        JsonObject json = gson.fromJson(run.getResultJson(), JsonObject.class);
        String stratName = json.has("strategyName") ? json.get("strategyName").getAsString() : "";
        String metric = json.has("targetMetric") ? json.get("targetMetric").getAsString() : "";
        String label = "SUCCESS: " + (stratName.isEmpty() ? run.getExpertName() : stratName) + " - " + metric;

        assertEquals("SUCCESS: Strategy_A - Sharpe Ratio", label);
    }

    @Test
    public void testRobustnessRun_multipleRuns() {
        for (int i = 1; i <= 5; i++) {
            JsonObject m = new JsonObject();
            m.addProperty("strategyName", "Strat_" + i);
            m.addProperty("shifts", i * 5);
            db.saveRun("ROBUSTNESS", "EA_" + i, i * 1000L, m.toString(), "/report_" + i);
        }

        List<HistoryRun> runs = db.getRunsByType("ROBUSTNESS");
        assertEquals(5, runs.size());

        // Verify descending order
        assertTrue(runs.get(0).getTimestamp() > runs.get(1).getTimestamp());
    }

    @Test
    public void testRobustnessRun_doesNotInterfereWithOtherTypes() {
        db.saveRun("ROBUSTNESS", "EA1", 1000L, "{}", "");
        db.saveRun("BACKTEST", "EA2", 2000L, "{}", "");
        db.saveRun("OPTIMIZATION", "EA3", 3000L, "{}", "");

        assertEquals(1, db.getRunsByType("ROBUSTNESS").size());
        assertEquals(1, db.getRunsByType("BACKTEST").size());
        assertEquals(1, db.getRunsByType("OPTIMIZATION").size());
    }

    @Test
    public void testRobustnessRun_withMissingFields() {
        // Simulate an old-format entry with no strategyName
        db.saveRun("ROBUSTNESS", "OldEA", 1000L, "{\"shifts\":5}", "");

        List<HistoryRun> runs = db.getRunsByType("ROBUSTNESS");
        HistoryRun run = runs.get(0);
        JsonObject json = gson.fromJson(run.getResultJson(), JsonObject.class);

        String stratName = json.has("strategyName") ? json.get("strategyName").getAsString() : "";
        assertTrue("strategyName should be empty for old format", stratName.isEmpty());

        // Fall back to expert name
        String label = "SUCCESS: " + (stratName.isEmpty() ? run.getExpertName() : stratName);
        assertEquals("SUCCESS: OldEA", label);
    }

    @Test
    public void testRobustnessRun_deleteAndVerify() {
        db.saveRun("ROBUSTNESS", "EA1", 1000L, "{}", "");
        List<HistoryRun> before = db.getRunsByType("ROBUSTNESS");
        assertEquals(1, before.size());

        db.deleteRun(before.get(0).getId());

        assertEquals(0, db.getRunsByType("ROBUSTNESS").size());
    }
}
