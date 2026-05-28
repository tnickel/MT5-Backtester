package com.backtester.report;

import com.backtester.database.DatabaseManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for the existing Optimization persistence (OPTIMIZATION_STATE table):
 * - OptimizationResult (lastOptResult) save/load
 * - Selected passes save/load
 * - Sensitivity results save/load
 * - Clear/overwrite behavior
 */
public class OptimizationPersistenceTest {

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

        tempDbFile = File.createTempFile("opt_persist_test_", ".db");
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
    public void testOptState_saveAndLoad() {
        String optJson = "{\"passes\":[{\"passNumber\":1,\"profit\":100.5},{\"passNumber\":2,\"profit\":200.3}]}";
        String selectedJson = "[1, 2]";
        String sensitivityJson = "[{\"overallCV\":15.5,\"status\":\"DONE\"}]";

        db.saveOptimizationState(optJson, selectedJson, sensitivityJson);

        String[] state = db.getOptimizationState();
        assertNotNull("State should not be null after save", state);
        assertEquals(3, state.length);
        assertEquals(optJson, state[0]);
        assertEquals(selectedJson, state[1]);
        assertEquals(sensitivityJson, state[2]);
    }

    @Test
    public void testOptState_overwritePreservesPreviousData() {
        // Save first state
        db.saveOptimizationState("first_opt", "first_sel", "first_sen");

        // Verify first state
        String[] first = db.getOptimizationState();
        assertEquals("first_opt", first[0]);

        // Overwrite with second state
        db.saveOptimizationState("second_opt", "second_sel", "second_sen");

        // Verify only second state exists
        String[] second = db.getOptimizationState();
        assertEquals("second_opt", second[0]);
        assertEquals("second_sel", second[1]);
        assertEquals("second_sen", second[2]);
    }

    @Test
    public void testOptState_clearAndVerify() {
        db.saveOptimizationState("data", "sel", "sen");
        assertNotNull(db.getOptimizationState());

        db.clearOptimizationState();
        assertNull("State should be null after clear", db.getOptimizationState());
    }

    @Test
    public void testOptState_emptyInitial() {
        assertNull("State should be null when never saved", db.getOptimizationState());
    }

    @Test
    public void testOptState_withNullFields() {
        db.saveOptimizationState(null, null, null);
        String[] state = db.getOptimizationState();
        // Should still be retrievable (even with nulls)
        assertNotNull(state);
    }

    @Test
    public void testOptState_withLargeOptResult() {
        // Simulate a large optimization result (100 passes)
        StringBuilder sb = new StringBuilder("{\"passes\":[");
        for (int i = 0; i < 100; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"passNumber\":").append(i).append(",\"profit\":").append(i * 50.0).append(",\"totalTrades\":").append(i + 10).append("}");
        }
        sb.append("]}");

        String largeOpt = sb.toString();
        db.saveOptimizationState(largeOpt, "[1,2,3,4,5]", "[]");

        String[] state = db.getOptimizationState();
        assertEquals(largeOpt, state[0]);
    }

    @Test
    public void testOptState_selectedPassesRoundTrip() {
        List<Integer> selectedIds = Arrays.asList(10, 25, 42, 77, 99);
        String selectedJson = gson.toJson(selectedIds);

        db.saveOptimizationState("{}", selectedJson, "[]");

        String[] state = db.getOptimizationState();
        Type listType = new TypeToken<List<Integer>>(){}.getType();
        List<Integer> loaded = gson.fromJson(state[1], listType);

        assertEquals(5, loaded.size());
        assertTrue(loaded.contains(42));
        assertTrue(loaded.contains(99));
    }

    @Test
    public void testOptState_persistsAcrossMultipleSaveCycles() {
        // Simulate the pattern that OptimizationView uses
        for (int cycle = 0; cycle < 5; cycle++) {
            String optJson = "{\"cycle\":" + cycle + "}";
            db.saveOptimizationState(optJson, "[" + cycle + "]", "[]");
        }

        String[] state = db.getOptimizationState();
        assertEquals("{\"cycle\":4}", state[0]);
    }

    @Test
    public void testOptState_alsoSavesRunToHistory() {
        // The OptimizationView saves both to OPTIMIZATION_STATE and HISTORY_RUNS
        db.saveOptimizationState("{\"passes\":[]}", "[]", "[]");
        db.saveRun("OPTIMIZATION", "TestEA", 1000L, "{\"passes\":5}", "/output/dir");

        // Verify both storage mechanisms
        assertNotNull(db.getOptimizationState());
        assertEquals(1, db.getRunsByType("OPTIMIZATION").size());
    }

    @Test
    public void testOptState_kiReportsIndependent() {
        // Save optimization state
        db.saveOptimizationState("{}", "[]", "[]");

        // Save KI report
        db.saveKiReport(System.currentTimeMillis(), "EA1", "EURUSD", "H1", "# Analysis");

        // Clear optimization state should NOT affect KI reports
        db.clearOptimizationState();
        assertNull(db.getOptimizationState());
        assertEquals("KI reports should survive opt state clear", 1, db.getAllKiReports().size());

        // Clear KI reports should NOT affect optimization state
        db.saveOptimizationState("{}", "[]", "[]");
        db.clearKiReports();
        assertNotNull("Opt state should survive KI clear", db.getOptimizationState());
        assertEquals(0, db.getAllKiReports().size());
    }
}
