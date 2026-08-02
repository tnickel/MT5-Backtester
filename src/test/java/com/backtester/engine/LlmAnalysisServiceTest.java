package com.backtester.engine;

import com.backtester.database.DatabaseManager;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LlmAnalysisServiceTest {

    private DatabaseManager database;
    private File tempDatabase;

    @Before
    public void setUp() throws Exception {
        resetDatabaseSingleton();
        tempDatabase = File.createTempFile("llm_identity_", ".db");
        tempDatabase.deleteOnExit();
        database = DatabaseManager.getInstance();
        Field dbUrl = DatabaseManager.class.getDeclaredField("dbUrl");
        dbUrl.setAccessible(true);
        dbUrl.set(database, "jdbc:sqlite:" + tempDatabase.getAbsolutePath());
        Method initialize = DatabaseManager.class.getDeclaredMethod("initializeDatabase");
        initialize.setAccessible(true);
        initialize.invoke(database);
    }

    @After
    public void tearDown() throws Exception {
        resetDatabaseSingleton();
        if (tempDatabase != null) tempDatabase.delete();
    }

    @Test
    public void sensitivityPromptKeepsSameNumberStrategiesSeparate() throws Exception {
        long timestamp = 123_456L;
        saveSensitivity(timestamp, "Strategy A", 5.0);
        saveSensitivity(timestamp, "Strategy B", 55.0);

        LlmAnalysisService service = new LlmAnalysisService();
        List<LlmAnalysisService.AnalysisCandidate> candidates = List.of(
                candidate(101, "Strategy A", 1000.0),
                candidate(102, "Strategy B", 2000.0));

        Method load = LlmAnalysisService.class.getDeclaredMethod(
                "loadSensitivityData", List.class, String.class, String.class, long.class);
        load.setAccessible(true);
        String promptData = (String) load.invoke(service, candidates, "EA", "EURUSD", timestamp);

        assertTrue(promptData.contains("Analyse-ID 101 | Pass 7 (Strategy A): avg_cv=5.00%"));
        assertTrue(promptData.contains("Analyse-ID 102 | Pass 7 (Strategy B): avg_cv=55.00%"));
        assertTrue(promptData.contains("Analyse-ID 101 | Pass 7 | Strategie Strategy A"));
        assertTrue(promptData.contains("Analyse-ID 102 | Pass 7 | Strategie Strategy B"));
        assertFalse(promptData.contains("avg_cv=30.00%"));
    }

    private void saveSensitivity(long timestamp, String strategyName, double cv) {
        database.saveSensitivityDetail(timestamp, 7, strategyName, "EA", "EURUSD",
                "Risk", "1", "BT", 100, 95, 5, cv,
                80, 110, 3, "0", "1", "2", "[]", "ROBUST");
    }

    private static LlmAnalysisService.AnalysisCandidate candidate(int analysisId,
                                                                   String strategyName,
                                                                   double profit) {
        Pass pass = new Pass();
        pass.setPassNumber(7);
        pass.setProfit(profit);
        pass.setTotalTrades(100);
        CombinedPass combined = new CombinedPass(pass, null, 80.0, 1.0, "");
        combined.setStrategyName(strategyName);
        return new LlmAnalysisService.AnalysisCandidate(
                analysisId, 7, strategyName, new LlmAnalysisService.PassPerformance(combined));
    }

    private static void resetDatabaseSingleton() throws Exception {
        Field instance = DatabaseManager.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);
    }
}
