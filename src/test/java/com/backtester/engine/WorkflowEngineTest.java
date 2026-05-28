package com.backtester.engine;

import com.backtester.database.DatabaseManager;
import com.backtester.database.HistoryRun;
import com.backtester.config.EaParameter;
import com.backtester.report.OptimizationResult;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import com.backtester.report.SensitivityResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class WorkflowEngineTest {

    private File tempDbFile;
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

        tempDbFile = File.createTempFile("workflow_engine_test_", ".db");
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
    public void testWorkflowSaveAndRestoreRoundTrip() throws Exception {
        WorkflowEngine engine = new WorkflowEngine(null);

        // 1. Populate the engine state with test data
        engine.changeExpert("CC_ADR_Stoch_Grid_with_Buttons_indi");
        engine.setSymbol("EURUSD");
        engine.setPeriod("H1");
        engine.setFromDate(LocalDate.of(2026, 1, 1));
        engine.setToDate(LocalDate.of(2026, 4, 30));
        engine.setDeposit(10000);
        engine.setCurrency("USD");
        engine.setLeverage("1:100");
        engine.setTickModel(2);
        engine.setLastActiveStep(6);

        // EA Parameters
        List<EaParameter> params = new ArrayList<>();
        EaParameter p1 = new EaParameter("InpLots", "0.01");
        p1.setOptimizeEnabled(true);
        p1.setOptimizeStart("0.01");
        p1.setOptimizeStep("0.01");
        p1.setOptimizeEnd("0.05");
        params.add(p1);
        engine.setEaParameters(params);

        // Optimization result
        OptimizationResult optRes = new OptimizationResult();
        optRes.setExpert("CC_ADR_Stoch_Grid_with_Buttons_indi");
        optRes.setSymbol("EURUSD");
        optRes.setPeriod("H1");
        optRes.setFromDate("2026-01-01");
        optRes.setToDate("2026-04-30");

        Pass pass1 = new Pass();
        pass1.setPassNumber(18578);
        pass1.setProfit(1500.50);
        pass1.setTotalTrades(120);
        pass1.setProfitFactor(2.10);
        pass1.setDrawdownPercent(8.5);
        pass1.setParameter("InpLots", "0.02");
        optRes.getPasses().add(pass1);
        engine.setOptResult(optRes);

        // Diverse passes
        List<CombinedPass> diverse = new ArrayList<>();
        CombinedPass cp = new CombinedPass(pass1, null, 85.0, 1.0, "");
        diverse.add(cp);
        engine.setSelectedDiversePasses(diverse);

        // Sensitivity results
        List<SensitivityResult> sensitivity = new ArrayList<>();
        SensitivityResult sr = new SensitivityResult(cp);
        sr.setStatus("Completed");
        sr.addParameterCV("InpLots", 12.5);
        sr.setKiResult("90");
        sensitivity.add(sr);
        engine.setSensitivityResults(sensitivity);

        // KI Report Text
        engine.setKiReportText("# KI Analyse Bericht\nSieht gut aus.");

        // Final selected passes
        List<CombinedPass> finalPasses = new ArrayList<>();
        finalPasses.add(cp);
        engine.setFinalSelectedPasses(finalPasses);

        // 2. Save workflow state to history
        engine.saveWorkflowToHistory();

        // 3. Fetch from DB and verify
        List<HistoryRun> runs = db.getRunsByType("Workflow");
        assertEquals("Should have exactly 1 saved workflow run", 1, runs.size());

        HistoryRun run = runs.get(0);
        assertEquals("Workflow", run.getRunType());
        assertEquals("CC_ADR_Stoch_Grid_with_Buttons_indi", run.getExpertName());
        assertNotNull(run.getResultJson());
        assertFalse(run.getResultJson().isEmpty());

        // 4. Restore state into a new WorkflowEngine instance
        WorkflowEngine restoredEngine = new WorkflowEngine(null);
        restoredEngine.restoreWorkflowState(run.getResultJson());

        // 5. Assert all properties were successfully restored
        assertEquals("CC_ADR_Stoch_Grid_with_Buttons_indi", restoredEngine.getExpert());
        assertEquals("EURUSD", restoredEngine.getSymbol());
        assertEquals("H1", restoredEngine.getPeriod());
        assertEquals(LocalDate.of(2026, 1, 1), restoredEngine.getFromDate());
        assertEquals(LocalDate.of(2026, 4, 30), restoredEngine.getToDate());
        assertEquals(10000, restoredEngine.getDeposit());
        assertEquals("USD", restoredEngine.getCurrency());
        assertEquals("1:100", restoredEngine.getLeverage());
        assertEquals(2, restoredEngine.getTickModel());
        assertEquals(6, restoredEngine.getLastActiveStep());

        // Assert list structures
        assertNotNull(restoredEngine.getEaParameters());
        assertEquals(1, restoredEngine.getEaParameters().size());
        assertEquals("InpLots", restoredEngine.getEaParameters().get(0).getName());
        assertEquals("0.01", restoredEngine.getEaParameters().get(0).getValue());

        assertNotNull(restoredEngine.getOptResult());
        assertEquals(1, restoredEngine.getOptResult().getPasses().size());
        assertEquals(18578, restoredEngine.getOptResult().getPasses().get(0).getPassNumber());
        assertEquals(1500.50, restoredEngine.getOptResult().getPasses().get(0).getProfit(), 0.001);

        assertNotNull(restoredEngine.getSelectedDiversePasses());
        assertEquals(1, restoredEngine.getSelectedDiversePasses().size());
        assertEquals(18578, restoredEngine.getSelectedDiversePasses().get(0).getPassNumber());

        assertNotNull(restoredEngine.getSensitivityResults());
        assertEquals(1, restoredEngine.getSensitivityResults().size());
        assertEquals("Completed", restoredEngine.getSensitivityResults().get(0).getStatus());
        assertEquals(12.5, restoredEngine.getSensitivityResults().get(0).getOverallCV(), 0.001);

        assertEquals("# KI Analyse Bericht\nSieht gut aus.", restoredEngine.getKiReportText());

        assertNotNull(restoredEngine.getFinalSelectedPasses());
        assertEquals(1, restoredEngine.getFinalSelectedPasses().size());
        assertEquals(18578, restoredEngine.getFinalSelectedPasses().get(0).getPassNumber());
    }

    @Test
    public void testRestoreWorkflowStateWithEmptyJson() {
        WorkflowEngine engine = new WorkflowEngine(null);
        // Should handle empty json gracefully without throwing exceptions
        engine.restoreWorkflowState("{}");
        assertNull(engine.getExpert());
        assertNull(engine.getSymbol());
        assertNull(engine.getPeriod());
        assertEquals(10000, engine.getDeposit()); // Default initialized value
        assertEquals(LocalDate.now().minusMonths(6), engine.getFromDate()); // Default initialized value
        assertEquals(LocalDate.now(), engine.getToDate()); // Default initialized value
        assertNotNull(engine.getEaParameters());
        assertTrue(engine.getEaParameters().isEmpty());
    }

    @Test
    public void testRestoreWorkflowStateWithPartialJson() {
        WorkflowEngine engine = new WorkflowEngine(null);
        String partialJson = "{\"expert_name\":\"PartialEA\",\"symbol\":\"GBPUSD\",\"deposit\":5000}";
        engine.restoreWorkflowState(partialJson);
        assertEquals("PartialEA", engine.getExpert());
        assertEquals("GBPUSD", engine.getSymbol());
        assertEquals(5000, engine.getDeposit());
        assertNull(engine.getPeriod());
        assertEquals(LocalDate.now().minusMonths(6), engine.getFromDate());
        assertEquals(LocalDate.now(), engine.getToDate());
        assertNotNull(engine.getEaParameters());
        assertTrue(engine.getEaParameters().isEmpty());
    }

    @Test
    public void testSaveAndLoadActiveState() {
        WorkflowEngine engine = new WorkflowEngine(null);
        engine.changeExpert("ActiveEA");
        engine.setSymbol("XAUUSD");
        engine.setPeriod("M15");
        engine.setDeposit(2500);
        engine.setLastActiveStep(3);

        // Save current active state to WORKFLOW_STATE
        engine.saveState();

        // Create new engine and load it
        WorkflowEngine otherEngine = new WorkflowEngine(null);
        otherEngine.loadState();

        assertEquals("ActiveEA", otherEngine.getExpert());
        assertEquals("XAUUSD", otherEngine.getSymbol());
        assertEquals("M15", otherEngine.getPeriod());
        assertEquals(2500, otherEngine.getDeposit());
        assertEquals(3, otherEngine.getLastActiveStep());
    }

    @Test
    public void testClearWorkflowResults() {
        WorkflowEngine engine = new WorkflowEngine(null);
        engine.changeExpert("KeepEA");
        engine.setSymbol("EURUSD");

        // Mock results
        engine.setOptResult(new OptimizationResult());
        List<CombinedPass> diverse = new ArrayList<>();
        diverse.add(new CombinedPass(new Pass(), null, 90.0, 1.0, ""));
        engine.setSelectedDiversePasses(diverse);
        engine.setKiReportText("Test Report");
        engine.setLastActiveStep(5);

        // Call clearResults
        engine.clearResults();

        // Configuration should be kept
        assertEquals("KeepEA", engine.getExpert());
        assertEquals("EURUSD", engine.getSymbol());

        // Results should be cleared
        assertNull(engine.getOptResult());
        assertTrue(engine.getSelectedDiversePasses().isEmpty());
        assertTrue(engine.getKiReportText().isEmpty());
        assertEquals(0, engine.getLastActiveStep());
    }

    @Test
    public void testSaveWorkflowToHistoryWithNullFields() {
        WorkflowEngine engine = new WorkflowEngine(null);
        engine.changeExpert("NullFieldsEA");
        // OptResult, diverse passes, sensitivity, etc. are null or empty
        engine.setOptResult(null);
        engine.setSelectedDiversePasses(new ArrayList<>());
        engine.setSensitivityResults(new ArrayList<>());
        engine.setKiReportText(null);
        engine.setFinalSelectedPasses(new ArrayList<>());

        // Should not throw NPE and save successfully
        engine.saveWorkflowToHistory();

        List<HistoryRun> runs = db.getRunsByType("Workflow");
        assertEquals(1, runs.size());
        assertEquals("NullFieldsEA", runs.get(0).getExpertName());
    }

    @Test
    public void testRestoreWorkflowStateNullHandling() {
        WorkflowEngine engine = new WorkflowEngine(null);
        // JSON containing explicit null values
        String nullJson = "{\n" +
                "  \"expert_name\": null,\n" +
                "  \"symbol\": null,\n" +
                "  \"period\": null,\n" +
                "  \"from_date\": null,\n" +
                "  \"to_date\": null,\n" +
                "  \"ea_parameters_json\": null,\n" +
                "  \"opt_result_json\": null,\n" +
                "  \"selected_diverse_passes_json\": null,\n" +
                "  \"sensitivity_results_json\": null,\n" +
                "  \"ki_report_text\": null,\n" +
                "  \"final_selected_passes_json\": null\n" +
                "}";

        engine.restoreWorkflowState(nullJson);
        assertNull(engine.getExpert());
        assertNull(engine.getSymbol());
        assertNull(engine.getPeriod());
        assertEquals(LocalDate.now().minusMonths(6), engine.getFromDate()); // default value
        assertEquals(LocalDate.now(), engine.getToDate()); // default value

        // Collection types should be initialized to empty lists rather than remaining null
        assertNotNull(engine.getEaParameters());
        assertTrue(engine.getEaParameters().isEmpty());
        assertNull(engine.getOptResult());
        assertNotNull(engine.getSelectedDiversePasses());
        assertTrue(engine.getSelectedDiversePasses().isEmpty());
        assertNotNull(engine.getSensitivityResults());
        assertTrue(engine.getSensitivityResults().isEmpty());
        assertEquals("", engine.getKiReportText());
        assertNotNull(engine.getFinalSelectedPasses());
        assertTrue(engine.getFinalSelectedPasses().isEmpty());
    }

    @Test
    public void testChangeExpertClearsOldState() {
        WorkflowEngine engine = new WorkflowEngine(null);
        engine.changeExpert("ExpertA");
        engine.setSymbol("EURUSD");
        engine.setOptResult(new OptimizationResult());
        engine.setLastActiveStep(3);

        // Switch to ExpertB
        engine.changeExpert("ExpertB");
        assertEquals("ExpertB", engine.getExpert());

        // Old results should be cleared
        assertNull(engine.getOptResult());
        assertEquals(0, engine.getLastActiveStep());
    }

    @Test
    public void testPreferencesRoundTrip() {
        WorkflowEngine engine = new WorkflowEngine(null);

        // Save preferences to DB using correct settings keys
        DatabaseManager.getInstance().saveSetting(LlmAnalysisService.SETTING_API_KEY, "my-test-api-key");
        DatabaseManager.getInstance().saveSetting(LlmAnalysisService.SETTING_MODEL, "my-test-model");
        DatabaseManager.getInstance().saveSetting(LlmAnalysisService.SETTING_PROMPT, "my-test-prompt");

        // Load preferences in engine
        engine.loadPreferences();

        assertEquals("my-test-api-key", engine.getOpenRouterApiKey());
        assertEquals("my-test-model", engine.getOpenRouterModel());
        assertEquals("my-test-prompt", engine.getOpenRouterPrompt());
    }

    @Test
    public void testSimulationSettingsGettersSetters() {
        WorkflowEngine engine = new WorkflowEngine(null);
        engine.setSymbol("USDCHF");
        engine.setPeriod("M5");
        engine.setDeposit(7500);
        engine.setCurrency("CHF");
        engine.setLeverage("1:200");
        engine.setTickModel(0);

        assertEquals("USDCHF", engine.getSymbol());
        assertEquals("M5", engine.getPeriod());
        assertEquals(7500, engine.getDeposit());
        assertEquals("CHF", engine.getCurrency());
        assertEquals("1:200", engine.getLeverage());
        assertEquals(0, engine.getTickModel());
    }

    @Test
    public void testClearState() {
        WorkflowEngine engine = new WorkflowEngine(null);
        engine.changeExpert("ClearMeEA");
        engine.setSymbol("EURUSD");
        engine.setOptResult(new OptimizationResult());
        engine.setLastActiveStep(4);

        // Save state so it exists in DB
        engine.saveState();
        assertNotNull(DatabaseManager.getInstance().getWorkflowState());

        // Clear state
        engine.clearState();

        // Memory variables should be reset
        assertEquals("", engine.getExpert());
        assertEquals("EURUSD", engine.getSymbol());
        assertNull(engine.getOptResult());
        assertEquals(0, engine.getLastActiveStep());

        // DB state should be cleared
        assertNull(DatabaseManager.getInstance().getWorkflowState());
    }

    @Test
    public void testEaParametersHandling() {
        WorkflowEngine engine = new WorkflowEngine(null);
        List<EaParameter> params = new ArrayList<>();
        params.add(new EaParameter("ParamA", "100"));
        params.add(new EaParameter("ParamB", "false"));

        engine.setEaParameters(params);

        assertEquals(2, engine.getEaParameters().size());
        assertEquals("ParamA", engine.getEaParameters().get(0).getName());
        assertEquals("100", engine.getEaParameters().get(0).getValue());
        assertEquals("ParamB", engine.getEaParameters().get(1).getName());
        assertEquals("false", engine.getEaParameters().get(1).getValue());
    }

    @Test
    public void testRunStep1ValidationExceptions() {
        WorkflowEngine engine = new WorkflowEngine(null);
        // Expect IllegalArgumentException when expert is empty
        try {
            engine.runStep1();
            fail("Should have thrown IllegalArgumentException for empty expert");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Expert Advisor"));
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        }

        // Set expert but parameters still empty
        engine.changeExpert("SomeExpert");
        try {
            engine.runStep1();
            fail("Should have thrown IllegalArgumentException for empty parameters");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("EA Parameter"));
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testRunStep3ValidationException() {
        WorkflowEngine engine = new WorkflowEngine(null);
        engine.runStep3();
    }

    @Test(expected = IllegalStateException.class)
    public void testRunStep6ValidationException() {
        WorkflowEngine engine = new WorkflowEngine(null);
        engine.runStep6();
    }

    @Test
    public void testRunStep6SortingAndFiltering() {
        WorkflowEngine engine = new WorkflowEngine(null);
        
        List<CombinedPass> diverse = new ArrayList<>();
        
        Pass p1 = new Pass();
        p1.setPassNumber(1);
        p1.setProfit(1000);
        p1.setTotalTrades(50);
        p1.setProfitFactor(2.0);
        p1.setDrawdownPercent(10.0);
        
        Pass p2 = new Pass();
        p2.setPassNumber(2);
        p2.setProfit(2000);
        p2.setTotalTrades(60);
        p2.setProfitFactor(2.5);
        p2.setDrawdownPercent(15.0);
        
        Pass p3 = new Pass();
        p3.setPassNumber(3);
        p3.setProfit(500);
        p3.setTotalTrades(30);
        p3.setProfitFactor(1.5);
        p3.setDrawdownPercent(8.0);

        diverse.add(new CombinedPass(p1, null, 80.0, 1.0, ""));
        diverse.add(new CombinedPass(p2, null, 90.0, 1.0, ""));
        diverse.add(new CombinedPass(p3, null, 60.0, 1.0, ""));
        
        engine.setSelectedDiversePasses(diverse);
        
        List<SensitivityResult> sensitivity = new ArrayList<>();
        SensitivityResult sr1 = new SensitivityResult(diverse.get(0));
        sr1.setKiResult("80");
        
        SensitivityResult sr2 = new SensitivityResult(diverse.get(1));
        sr2.setKiResult("10"); // fragile, should be filtered out
        
        SensitivityResult sr3 = new SensitivityResult(diverse.get(2));
        sr3.setKiResult("70");
        
        sensitivity.add(sr1);
        sensitivity.add(sr2);
        sensitivity.add(sr3);
        
        engine.setSensitivityResults(sensitivity);
        
        List<CombinedPass> finalSelected = engine.runStep6();
        
        assertNotNull(finalSelected);
        assertEquals(2, finalSelected.size());
        assertEquals(1, finalSelected.get(0).getPassNumber());
        assertEquals(3, finalSelected.get(1).getPassNumber());
    }
}
