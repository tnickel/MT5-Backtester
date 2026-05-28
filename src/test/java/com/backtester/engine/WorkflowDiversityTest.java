package com.backtester.engine;

import com.backtester.report.OptimizationResult;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import com.backtester.database.DatabaseManager;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class WorkflowDiversityTest {

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

        tempDbFile = File.createTempFile("workflow_diversity_test_", ".db");
        tempDbFile.deleteOnExit();

        DatabaseManager db = DatabaseManager.getInstance();
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
    public void testArePassesSimilar_SameParamsAndTrades() {
        Pass p1 = new Pass();
        p1.setPassNumber(1);
        p1.setTotalTrades(100);
        p1.setProfit(500);
        p1.setParameter("tp", "50");
        p1.setParameter("sl", "100");

        Pass p2 = new Pass();
        p2.setPassNumber(2);
        p2.setTotalTrades(100);
        p2.setProfit(480);
        p2.setParameter("tp", "50");
        p2.setParameter("sl", "100");

        CombinedPass cp1 = new CombinedPass(p1, null, 80.0, 1.0, "");
        CombinedPass cp2 = new CombinedPass(p2, null, 78.0, 1.0, "");

        // Similar because trade count diff = 0% (< 15%) and different params = 0 (< 2)
        assertTrue(WorkflowEngine.arePassesSimilar(cp1, cp2, 0.10, 0.15, 2));
    }

    @Test
    public void testArePassesSimilar_DifferentParams() {
        Pass p1 = new Pass();
        p1.setPassNumber(1);
        p1.setTotalTrades(100);
        p1.setProfit(500);
        p1.setParameter("tp", "50");
        p1.setParameter("sl", "100");

        Pass p2 = new Pass();
        p2.setPassNumber(2);
        p2.setTotalTrades(100);
        p2.setProfit(480);
        p2.setParameter("tp", "60"); // differs by 20% (>= 10%)
        p2.setParameter("sl", "120"); // differs by 20% (>= 10%)

        CombinedPass cp1 = new CombinedPass(p1, null, 80.0, 1.0, "");
        CombinedPass cp2 = new CombinedPass(p2, null, 78.0, 1.0, "");

        // Not similar because 2 parameters are significantly different (>= 2)
        assertFalse(WorkflowEngine.arePassesSimilar(cp1, cp2, 0.10, 0.15, 2));
    }

    @Test
    public void testArePassesSimilar_DifferentTrades() {
        Pass p1 = new Pass();
        p1.setPassNumber(1);
        p1.setTotalTrades(100);
        p1.setProfit(500);
        p1.setParameter("tp", "50");
        p1.setParameter("sl", "100");

        Pass p2 = new Pass();
        p2.setPassNumber(2);
        p2.setTotalTrades(80); // differs by 20% (>= 15%)
        p2.setProfit(480);
        p2.setParameter("tp", "50");
        p2.setParameter("sl", "100");

        CombinedPass cp1 = new CombinedPass(p1, null, 80.0, 1.0, "");
        CombinedPass cp2 = new CombinedPass(p2, null, 78.0, 1.0, "");

        // Not similar because trade count differs significantly
        assertFalse(WorkflowEngine.arePassesSimilar(cp1, cp2, 0.10, 0.15, 2));
    }

    @Test
    public void testFilterDiversePasses() {
        WorkflowEngine engine = new WorkflowEngine(null);
        engine.setMinBtProfit(100);
        engine.setMinBtTrades(10);
        engine.setMaxBtDd(30.0);
        engine.setParamDiffPct(0.10);
        engine.setTradeDiffPct(0.15);
        engine.setMinDifferentParams(2);
        engine.setMaxStrategiesToSelect(3);

        List<CombinedPass> allPasses = new ArrayList<>();

        // Pass 1: Best score
        Pass p1 = new Pass();
        p1.setPassNumber(1);
        p1.setTotalTrades(150);
        p1.setProfit(1000);
        p1.setDrawdownPercent(10.0);
        p1.setParameter("tp", "50");
        p1.setParameter("sl", "100");
        allPasses.add(new CombinedPass(p1, null, 95.0, 1.0, ""));

        // Pass 2: Similar to Pass 1 (should be filtered out by diversity)
        Pass p2 = new Pass();
        p2.setPassNumber(2);
        p2.setTotalTrades(152);
        p2.setProfit(950);
        p2.setDrawdownPercent(11.0);
        p2.setParameter("tp", "51"); // only 2% difference
        p2.setParameter("sl", "100");
        allPasses.add(new CombinedPass(p2, null, 90.0, 1.0, ""));

        // Pass 3: Diverse from Pass 1, good score
        Pass p3 = new Pass();
        p3.setPassNumber(3);
        p3.setTotalTrades(120);
        p3.setProfit(800);
        p3.setDrawdownPercent(15.0);
        p3.setParameter("tp", "70"); // 40% difference
        p3.setParameter("sl", "130"); // 30% difference
        allPasses.add(new CombinedPass(p3, null, 85.0, 1.0, ""));

        // Pass 4: Low profit (should be filtered out by threshold)
        Pass p4 = new Pass();
        p4.setPassNumber(4);
        p4.setTotalTrades(100);
        p4.setProfit(50); // < 100 min profit
        p4.setDrawdownPercent(5.0);
        p4.setParameter("tp", "80");
        p4.setParameter("sl", "160");
        allPasses.add(new CombinedPass(p4, null, 80.0, 1.0, ""));

        // Pass 5: Diverse and valid
        Pass p5 = new Pass();
        p5.setPassNumber(5);
        p5.setTotalTrades(200);
        p5.setProfit(700);
        p5.setDrawdownPercent(12.0);
        p5.setParameter("tp", "30");
        p5.setParameter("sl", "60");
        allPasses.add(new CombinedPass(p5, null, 75.0, 1.0, ""));

        List<CombinedPass> diverse = engine.filterDiversePasses(allPasses);

        // Diverse should contain: Pass 1, Pass 3, Pass 5 (Pass 2 is similar, Pass 4 is below profit threshold)
        assertEquals(3, diverse.size());
        assertEquals(1, diverse.get(0).getPassNumber());
        assertEquals(3, diverse.get(1).getPassNumber());
        assertEquals(5, diverse.get(2).getPassNumber());
    }

    @Test
    public void testWorkflowStatePersistence() throws Exception {
        java.io.File tempDbFile = java.io.File.createTempFile("workflow_persist_test_", ".db");
        tempDbFile.deleteOnExit();

        com.backtester.database.DatabaseManager db = com.backtester.database.DatabaseManager.getInstance();
        java.lang.reflect.Field dbUrlField = com.backtester.database.DatabaseManager.class.getDeclaredField("dbUrl");
        dbUrlField.setAccessible(true);
        String originalDbUrl = (String) dbUrlField.get(db);
        dbUrlField.set(db, "jdbc:sqlite:" + tempDbFile.getAbsolutePath());

        java.lang.reflect.Method initMethod = com.backtester.database.DatabaseManager.class.getDeclaredMethod("initializeDatabase");
        initMethod.setAccessible(true);
        initMethod.invoke(db);

        try {
            WorkflowEngine engine1 = new WorkflowEngine(null);
            
            // Set some values
            engine1.setExpert("TestExpertAdvisor");
            engine1.setSymbol("USDJPY");
            engine1.setPeriod("M30");
            engine1.setDeposit(5000);
            engine1.setCurrency("EUR");
            engine1.setLeverage("1:500");
            engine1.setTickModel(2);
            engine1.setLastActiveStep(4);
            
            List<com.backtester.config.EaParameter> params = new java.util.ArrayList<>();
            com.backtester.config.EaParameter param = new com.backtester.config.EaParameter("InpTest", "42");
            param.setOptimizeEnabled(true);
            params.add(param);
            engine1.setEaParameters(params);
            
            // Save
            engine1.saveState();
            
            // Create new engine and verify loaded state
            WorkflowEngine engine2 = new WorkflowEngine(null);
            assertEquals("TestExpertAdvisor", engine2.getExpert());
            assertEquals("USDJPY", engine2.getSymbol());
            assertEquals("M30", engine2.getPeriod());
            assertEquals(5000, engine2.getDeposit());
            assertEquals("EUR", engine2.getCurrency());
            assertEquals("1:500", engine2.getLeverage());
            assertEquals(2, engine2.getTickModel());
            assertEquals(4, engine2.getLastActiveStep());
            assertEquals(1, engine2.getEaParameters().size());
            assertEquals("InpTest", engine2.getEaParameters().get(0).getName());
            assertEquals("42", engine2.getEaParameters().get(0).getValue());
            assertTrue(engine2.getEaParameters().get(0).isOptimizeEnabled());
        } finally {
            dbUrlField.set(db, originalDbUrl);
            if (tempDbFile.exists()) {
                tempDbFile.delete();
            }
        }
    }

    @Test
    public void testWorkflowStrategyConfigPersistence() throws Exception {
        java.io.File tempDbFile = java.io.File.createTempFile("workflow_strategy_persist_test_", ".db");
        tempDbFile.deleteOnExit();

        com.backtester.database.DatabaseManager db = com.backtester.database.DatabaseManager.getInstance();
        java.lang.reflect.Field dbUrlField = com.backtester.database.DatabaseManager.class.getDeclaredField("dbUrl");
        dbUrlField.setAccessible(true);
        String originalDbUrl = (String) dbUrlField.get(db);
        dbUrlField.set(db, "jdbc:sqlite:" + tempDbFile.getAbsolutePath());

        java.lang.reflect.Method initMethod = com.backtester.database.DatabaseManager.class.getDeclaredMethod("initializeDatabase");
        initMethod.setAccessible(true);
        initMethod.invoke(db);

        try {
            WorkflowEngine engine = new WorkflowEngine(null);
            
            // Set up config for ExpertA
            engine.changeExpert("ExpertA");
            engine.setSymbol("XAUUSD");
            engine.setDeposit(25000);
            engine.setLeverage("1:200");
            engine.setOptimizationMode(3); // customized value
            engine.setMinBtProfit(150.0);
            engine.saveStrategyConfig("ExpertA");
            
            // Switch to ExpertB
            engine.changeExpert("ExpertB");
            assertEquals("ExpertB", engine.getExpert());
            engine.setSymbol("EURGBP");
            engine.setDeposit(5000);
            engine.setLeverage("1:50");
            engine.setOptimizationMode(1);
            engine.setMinBtProfit(50.0);
            engine.saveStrategyConfig("ExpertB");
            
            // Switch back to ExpertA
            engine.changeExpert("ExpertA");
            assertEquals("ExpertA", engine.getExpert());
            assertEquals("XAUUSD", engine.getSymbol());
            assertEquals(25000, engine.getDeposit());
            assertEquals("1:200", engine.getLeverage());
            assertEquals(3, engine.getOptimizationMode());
            assertEquals(150.0, engine.getMinBtProfit(), 0.001);
            
            // Switch back to ExpertB
            engine.changeExpert("ExpertB");
            assertEquals("ExpertB", engine.getExpert());
            assertEquals("EURGBP", engine.getSymbol());
            assertEquals(5000, engine.getDeposit());
            assertEquals("1:50", engine.getLeverage());
            assertEquals(1, engine.getOptimizationMode());
            assertEquals(50.0, engine.getMinBtProfit(), 0.001);
        } finally {
            dbUrlField.set(db, originalDbUrl);
            if (tempDbFile.exists()) {
                tempDbFile.delete();
            }
        }
    }
}
