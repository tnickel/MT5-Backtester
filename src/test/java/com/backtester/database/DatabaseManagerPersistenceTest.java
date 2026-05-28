package com.backtester.database;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for DatabaseManager persistence methods:
 * - MULTI_BACKTEST_BATCHES (saveBatch, getAllBatches, deleteBatch, clearBatches)
 * - getRunsByType filtered queries
 * - HISTORY_RUNS round-trip for backtest results
 * - OPTIMIZATION_STATE round-trip
 * - KI_REPORTS CRUD
 */
public class DatabaseManagerPersistenceTest {

    private DatabaseManager db;
    private File tempDbFile;

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

        // Create a temporary SQLite DB for isolated testing
        tempDbFile = File.createTempFile("backtester_test_", ".db");
        tempDbFile.deleteOnExit();

        // Use reflection to create a fresh DatabaseManager pointing to our temp DB
        db = DatabaseManager.getInstance();
        Field dbUrlField = DatabaseManager.class.getDeclaredField("dbUrl");
        dbUrlField.setAccessible(true);
        dbUrlField.set(db, "jdbc:sqlite:" + tempDbFile.getAbsolutePath());

        // Re-initialize the database tables
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

    // ====================================================================
    // MULTI_BACKTEST_BATCHES Tests
    // ====================================================================

    @Test
    public void testSaveBatch_and_getAllBatches() {
        db.saveBatch("TestBatch1", 1000L, "/path/to/report.html", "[{\"expert\":\"EA1\"}]");
        db.saveBatch("TestBatch2", 2000L, "/path/to/report2.html", "[{\"expert\":\"EA2\"}]");

        List<Object[]> batches = db.getAllBatches();
        assertEquals("Should have 2 batches", 2, batches.size());

        // First batch should be TestBatch2 (newer timestamp, ordered DESC)
        assertEquals("TestBatch2", batches.get(0)[1]);
        assertEquals(2000L, batches.get(0)[2]);
        assertEquals("/path/to/report2.html", batches.get(0)[3]);
        assertEquals("[{\"expert\":\"EA2\"}]", batches.get(0)[4]);

        // Second batch should be TestBatch1
        assertEquals("TestBatch1", batches.get(1)[1]);
    }

    @Test
    public void testDeleteBatch() {
        db.saveBatch("ToDelete", 1000L, "", "[]");
        List<Object[]> before = db.getAllBatches();
        assertEquals(1, before.size());

        int id = (int) before.get(0)[0];
        db.deleteBatch(id);

        List<Object[]> after = db.getAllBatches();
        assertEquals("Batch should be deleted", 0, after.size());
    }

    @Test
    public void testClearBatches() {
        db.saveBatch("Batch1", 1000L, "", "[]");
        db.saveBatch("Batch2", 2000L, "", "[]");
        db.saveBatch("Batch3", 3000L, "", "[]");

        assertEquals(3, db.getAllBatches().size());

        db.clearBatches();

        assertEquals("All batches should be cleared", 0, db.getAllBatches().size());
    }

    @Test
    public void testGetAllBatches_empty() {
        List<Object[]> batches = db.getAllBatches();
        assertNotNull(batches);
        assertEquals(0, batches.size());
    }

    @Test
    public void testSaveBatch_withLargeJson() {
        // Simulate a large JSON payload (like a real batch with many results)
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 100; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"expert\":\"EA").append(i).append("\",\"profit\":").append(i * 100).append("}");
        }
        sb.append("]");

        db.saveBatch("LargeBatch", 5000L, "/reports/large.html", sb.toString());

        List<Object[]> batches = db.getAllBatches();
        assertEquals(1, batches.size());
        assertEquals(sb.toString(), batches.get(0)[4]);
    }

    @Test
    public void testSaveBatch_withNullFields() {
        db.saveBatch("NullBatch", 1000L, null, null);

        List<Object[]> batches = db.getAllBatches();
        assertEquals(1, batches.size());
        assertNull(batches.get(0)[3]); // html path
        assertNull(batches.get(0)[4]); // results json
    }

    // ====================================================================
    // HISTORY_RUNS & getRunsByType Tests
    // ====================================================================

    @Test
    public void testSaveRun_and_getRunsByType() {
        db.saveRun("BACKTEST", "TestEA", 1000L, "{\"profit\":100}", "/path/to/report");
        db.saveRun("BACKTEST", "TestEA", 2000L, "{\"profit\":200}", "/path/to/report2");
        db.saveRun("OPTIMIZATION", "TestEA", 3000L, "{\"passes\":10}", "/path/opt");
        db.saveRun("ROBUSTNESS", "TestEA", 4000L, "{\"shifts\":5}", "/path/robust");

        List<HistoryRun> backtests = db.getRunsByType("BACKTEST");
        assertEquals("Should have 2 backtests", 2, backtests.size());
        // Ordered DESC by timestamp
        assertEquals(2000L, backtests.get(0).getTimestamp());
        assertEquals(1000L, backtests.get(1).getTimestamp());

        List<HistoryRun> optimizations = db.getRunsByType("OPTIMIZATION");
        assertEquals("Should have 1 optimization", 1, optimizations.size());
        assertEquals("{\"passes\":10}", optimizations.get(0).getResultJson());

        List<HistoryRun> robustness = db.getRunsByType("ROBUSTNESS");
        assertEquals("Should have 1 robustness", 1, robustness.size());

        List<HistoryRun> empty = db.getRunsByType("NONEXISTENT");
        assertEquals("Should have 0 results for unknown type", 0, empty.size());
    }

    @Test
    public void testGetRunsByType_empty() {
        List<HistoryRun> runs = db.getRunsByType("BACKTEST");
        assertNotNull(runs);
        assertEquals(0, runs.size());
    }

    @Test
    public void testHistoryRun_fullRoundTrip() {
        String json = "{\"expert\":\"TestEA\",\"symbol\":\"EURUSD\",\"totalProfit\":500.50}";
        db.saveRun("BACKTEST", "TestEA", 12345L, json, "/output/dir");

        List<HistoryRun> runs = db.getRunsByType("BACKTEST");
        assertEquals(1, runs.size());

        HistoryRun run = runs.get(0);
        assertEquals("BACKTEST", run.getRunType());
        assertEquals("TestEA", run.getExpertName());
        assertEquals(12345L, run.getTimestamp());
        assertEquals(json, run.getResultJson());
        assertEquals("/output/dir", run.getHtmlPath());
        assertTrue("ID should be positive", run.getId() > 0);
    }

    @Test
    public void testGetAllRuns_mixedTypes() {
        db.saveRun("BACKTEST", "EA1", 1000L, "{}", "");
        db.saveRun("MULTI_BACKTEST", "EA2", 2000L, "{}", "");
        db.saveRun("ROBUSTNESS", "EA3", 3000L, "{}", "");

        List<HistoryRun> all = db.getAllRuns();
        assertEquals("Should have 3 runs of all types", 3, all.size());
    }

    @Test
    public void testDeleteRun() {
        db.saveRun("BACKTEST", "EA1", 1000L, "{}", "");
        List<HistoryRun> runs = db.getAllRuns();
        assertEquals(1, runs.size());

        db.deleteRun(runs.get(0).getId());

        assertEquals("Run should be deleted", 0, db.getAllRuns().size());
    }

    // ====================================================================
    // OPTIMIZATION_STATE Tests
    // ====================================================================

    @Test
    public void testOptimizationState_roundTrip() {
        String optJson = "{\"passes\":[{\"passNumber\":1,\"profit\":100}]}";
        String selectedJson = "[1,2,3]";
        String sensitivityJson = "[{\"cv\":15.5}]";

        db.saveOptimizationState(optJson, selectedJson, sensitivityJson);

        String[] state = db.getOptimizationState();
        assertNotNull("State should not be null", state);
        assertEquals(optJson, state[0]);
        assertEquals(selectedJson, state[1]);
        assertEquals(sensitivityJson, state[2]);
    }

    @Test
    public void testOptimizationState_overwrite() {
        db.saveOptimizationState("first", "first_sel", "first_sen");
        db.saveOptimizationState("second", "second_sel", "second_sen");

        String[] state = db.getOptimizationState();
        assertNotNull(state);
        assertEquals("second", state[0]);
        assertEquals("second_sel", state[1]);
        assertEquals("second_sen", state[2]);
    }

    @Test
    public void testOptimizationState_clear() {
        db.saveOptimizationState("data", "sel", "sen");
        assertNotNull(db.getOptimizationState());

        db.clearOptimizationState();

        assertNull("State should be null after clear", db.getOptimizationState());
    }

    // ====================================================================
    // KI_REPORTS Tests
    // ====================================================================

    @Test
    public void testKiReports_saveAndRetrieve() {
        db.saveKiReport(System.currentTimeMillis(), "TestEA", "EURUSD", "H1", "# Test Report\n\nThis is a test.");

        java.util.List<com.backtester.engine.KiReport> reports = db.getAllKiReports();
        assertEquals(1, reports.size());
        assertEquals("TestEA", reports.get(0).getExpertName());
        assertEquals("EURUSD", reports.get(0).getSymbol());
        assertEquals("# Test Report\n\nThis is a test.", reports.get(0).getReportMarkdown());
    }

    @Test
    public void testKiReports_clear() {
        db.saveKiReport(System.currentTimeMillis(), "EA1", "GBPUSD", "M15", "Report content");
        assertEquals(1, db.getAllKiReports().size());

        db.clearKiReports();
        assertEquals("All KI reports should be cleared", 0, db.getAllKiReports().size());
    }

    // ====================================================================
    // APP_SETTINGS Tests
    // ====================================================================

    @Test
    public void testSettings_saveAndGet() {
        db.saveSetting("test.key", "test_value");
        assertEquals("test_value", db.getSetting("test.key"));
    }

    @Test
    public void testSettings_getDefault() {
        String val = db.getSetting("nonexistent.key", "default_val");
        assertEquals("default_val", val);
    }

    @Test
    public void testSettings_overwrite() {
        db.saveSetting("key1", "value1");
        db.saveSetting("key1", "value2");
        assertEquals("value2", db.getSetting("key1"));
    }

    @Test
    public void testDeleteKiReport() {
        db.saveKiReport(1000L, "TestEA", "EURUSD", "H1", "Report Markdown");
        java.util.List<com.backtester.engine.KiReport> reports = db.getAllKiReports();
        assertEquals(1, reports.size());

        db.deleteKiReport(reports.get(0).getId());
        assertEquals(0, db.getAllKiReports().size());
    }

    @Test
    public void testEaSavedConfigs_CRUD() {
        String expert = "SuperEA";
        db.insertEaConfig(expert, "LowRisk", "{\"param\":1}");
        db.insertEaConfig(expert, "HighRisk", "{\"param\":2}");

        List<EaDbConfig> configs = db.getEaConfigsList(expert);
        assertEquals(2, configs.size());

        EaDbConfig toUpdate = configs.get(0);
        db.updateEaConfig(toUpdate.getId(), "RiskOptimized", "{\"param\":3}");

        List<EaDbConfig> updatedConfigs = db.getEaConfigsList(expert);
        boolean foundUpdated = false;
        for (EaDbConfig cfg : updatedConfigs) {
            if (cfg.getId() == toUpdate.getId()) {
                assertEquals("RiskOptimized", cfg.getConfigName());
                assertEquals("{\"param\":3}", cfg.getParametersJson());
                foundUpdated = true;
            }
        }
        assertTrue(foundUpdated);

        db.deleteEaConfig(toUpdate.getId());
        assertEquals(1, db.getEaConfigsList(expert).size());
    }

    @Test
    public void testSensitivityDetails_CRUD() throws Exception {
        long timestamp = 123456789L;
        db.saveSensitivityDetail(
                timestamp, 5, "pass5", "MyEA", "EURUSD",
                "TrailingStop", "15", "BT", 100.5, 95.0, 5.0, 5.26,
                80.0, 110.0, 11, "10", "1", "20", "{}", "ROBUST"
        );

        // Verify it was written using direct SQL
        try (java.sql.Connection conn = db.getConnection();
             java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery("SELECT * FROM SENSITIVITY_DETAIL WHERE run_timestamp = " + timestamp)) {
            assertTrue(rs.next());
            assertEquals("pass5", rs.getString("pass_name"));
            assertEquals("MyEA", rs.getString("expert_name"));
            assertEquals("TrailingStop", rs.getString("parameter_name"));
            assertEquals("ROBUST", rs.getString("verdict"));
            assertEquals(100.5, rs.getDouble("base_profit"), 0.0001);
            assertEquals(11, rs.getInt("num_variants"));
            assertFalse(rs.next());
        }

        // Test clearSensitivityDetails (single run)
        db.clearSensitivityDetails(timestamp);
        try (java.sql.Connection conn = db.getConnection();
             java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery("SELECT count(*) FROM SENSITIVITY_DETAIL WHERE run_timestamp = " + timestamp)) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        }

        // Add two more
        db.saveSensitivityDetail(timestamp, 1, "p1", "EA", "US", "P", "1", "BT", 10, 10, 0, 0, 10, 10, 1, "1", "1", "1", "{}", "ROBUST");
        db.saveSensitivityDetail(timestamp + 1, 2, "p2", "EA", "US", "P", "1", "BT", 10, 10, 0, 0, 10, 10, 1, "1", "1", "1", "{}", "ROBUST");

        // Clear all
        db.clearAllSensitivityDetails();
        try (java.sql.Connection conn = db.getConnection();
             java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery("SELECT count(*) FROM SENSITIVITY_DETAIL")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        }
    }

    @Test
    public void testDeleteAllRuns() {
        db.saveRun("BACKTEST", "EA1", 1000L, "{}", "");
        db.saveRun("OPTIMIZATION", "EA2", 2000L, "{}", "");
        assertEquals(2, db.getAllRuns().size());

        db.deleteAllRuns();
        assertEquals(0, db.getAllRuns().size());
    }

    @Test
    public void testSensitivityDetailSchemaMigration() throws Exception {
        // Drop the SENSITIVITY_DETAIL table
        try (java.sql.Connection conn = db.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS SENSITIVITY_DETAIL");
            // Re-create old table without 'verdict' column
            stmt.execute("CREATE TABLE SENSITIVITY_DETAIL (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "run_timestamp INTEGER," +
                    "expert_name TEXT" +
                    ")");
        }

        // Call initializeDatabase via reflection to trigger migration
        java.lang.reflect.Method initMethod = DatabaseManager.class.getDeclaredMethod("initializeDatabase");
        initMethod.setAccessible(true);
        initMethod.invoke(db);

        // Verify 'verdict' column now exists
        try (java.sql.Connection conn = db.getConnection();
             java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery("PRAGMA table_info(SENSITIVITY_DETAIL)")) {
            boolean hasVerdict = false;
            while (rs.next()) {
                if ("verdict".equals(rs.getString("name"))) {
                    hasVerdict = true;
                    break;
                }
            }
            assertTrue("verdict column should be added by schema migration", hasVerdict);
        }
    }

    @Test
    public void testEaConfigsTableMigration() throws Exception {
        // Drop EA_SAVED_CONFIGS and recreate old EA_CONFIGS table
        try (java.sql.Connection conn = db.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS EA_SAVED_CONFIGS");
            stmt.execute("DROP TABLE IF EXISTS EA_CONFIGS");
            stmt.execute("CREATE TABLE EA_CONFIGS (" +
                    "expert_name TEXT," +
                    "parameters_json TEXT," +
                    "updated_at INTEGER" +
                    ")");
            stmt.execute("INSERT INTO EA_CONFIGS (expert_name, parameters_json, updated_at) VALUES (" +
                    "'OldEA', '{\"some\":\"config\"}', 111111)");
        }

        // Call initializeDatabase via reflection to trigger migration
        java.lang.reflect.Method initMethod = DatabaseManager.class.getDeclaredMethod("initializeDatabase");
        initMethod.setAccessible(true);
        initMethod.invoke(db);

        // Verify EA_SAVED_CONFIGS table has the migrated record
        List<EaDbConfig> configs = db.getEaConfigsList("OldEA");
        assertEquals(1, configs.size());
        assertEquals("Default Config", configs.get(0).getConfigName());
        assertEquals("{\"some\":\"config\"}", configs.get(0).getParametersJson());

        // Verify old table EA_CONFIGS was dropped
        try (java.sql.Connection conn = db.getConnection();
             java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery(
                     "SELECT count(*) FROM sqlite_master WHERE type='table' AND name='EA_CONFIGS'")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        }
    }
}
