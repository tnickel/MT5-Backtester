package com.backtester.engine;

import com.backtester.config.EaParameter;
import com.backtester.database.DatabaseManager;
import com.backtester.database.HistoryRun;
import com.backtester.report.OptimizationResult;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import com.backtester.report.SensitivityResult;
import com.backtester.report.ValidationResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests für die Anti-Curve-Fitting-Kette der WorkflowEngine:
 * KI-Gate-Sichtbarkeit (Schritt 6), Out-of-Sample-Validierungsfenster
 * (Schritt 7), Pass-Zahl-Berechnung und Jahresberechnung des Scores.
 */
public class WorkflowValidationAndGateTest {

    private File tempDbFile;

    @Before
    public void setUp() throws Exception {
        try {
            Field instanceField = DatabaseManager.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            instanceField.set(null, null);
        } catch (Exception ignored) {
        }

        tempDbFile = File.createTempFile("workflow_validation_test_", ".db");
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
        try {
            Field instanceField = DatabaseManager.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            instanceField.set(null, null);
        } catch (Exception ignored) {
        }
    }

    // --- Helpers ---

    private CombinedPass makePass(int passNumber, double profit, int trades) {
        Pass bt = new Pass();
        bt.setPassNumber(passNumber);
        bt.setProfit(profit);
        bt.setTotalTrades(trades);
        bt.setProfitFactor(1.8);
        bt.setRecoveryFactor(3.0);
        bt.setDrawdownPercent(10.0);
        return new CombinedPass(bt, null, 75.0, 1.0, "");
    }

    private SensitivityResult makeSensitivity(CombinedPass cp, int kiScore) {
        SensitivityResult sr = new SensitivityResult(cp);
        sr.setKiResult(String.valueOf(kiScore));
        return sr;
    }

    private boolean canExportToBest(WorkflowEngine engine, int passNumber) throws Exception {
        java.lang.reflect.Method m = WorkflowEngine.class.getDeclaredMethod("isValidationPassedOrPending", int.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(engine, passNumber);
    }

    // --- Schritt 6: KI-Gate ---

    @Test
    public void testKiGateBypassedWhenAllCandidatesFragile() {
        WorkflowEngine engine = new WorkflowEngine(null);
        CombinedPass p1 = makePass(1, 1000, 200);
        CombinedPass p2 = makePass(2, 800, 150);

        List<CombinedPass> diverse = new ArrayList<>();
        diverse.add(p1);
        diverse.add(p2);
        engine.setSelectedDiversePasses(diverse);

        List<SensitivityResult> sens = new ArrayList<>();
        sens.add(makeSensitivity(p1, 10)); // fragil
        sens.add(makeSensitivity(p2, 20)); // fragil
        engine.setSensitivityResults(sens);

        List<CombinedPass> result = engine.runStep6();

        // Fallback greift, aber sichtbar: Flag muss gesetzt sein
        assertTrue("KI-Gate-Bypass muss geflaggt werden", engine.isKiGateBypassed());
        assertEquals(2, result.size());
    }

    @Test
    public void testKiGateNotBypassedWhenOneCandidateStable() {
        WorkflowEngine engine = new WorkflowEngine(null);
        CombinedPass p1 = makePass(1, 1000, 200);
        CombinedPass p2 = makePass(2, 800, 150);

        List<CombinedPass> diverse = new ArrayList<>();
        diverse.add(p1);
        diverse.add(p2);
        engine.setSelectedDiversePasses(diverse);

        List<SensitivityResult> sens = new ArrayList<>();
        sens.add(makeSensitivity(p1, 10)); // fragil → raus
        sens.add(makeSensitivity(p2, 80)); // stabil → bleibt
        engine.setSensitivityResults(sens);

        List<CombinedPass> result = engine.runStep6();

        assertFalse("Gate funktioniert normal — kein Bypass-Flag", engine.isKiGateBypassed());
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getPassNumber());
    }

    @Test
    public void testRunStep6ResetsOldValidationResults() {
        WorkflowEngine engine = new WorkflowEngine(null);
        CombinedPass p1 = makePass(1, 1000, 200);
        List<CombinedPass> diverse = new ArrayList<>();
        diverse.add(p1);
        engine.setSelectedDiversePasses(diverse);
        engine.setSensitivityResults(new ArrayList<>());

        List<ValidationResult> stale = new ArrayList<>();
        stale.add(new ValidationResult(99));
        engine.setValidationResults(stale);

        engine.runStep6();

        // Eine neue finale Auswahl macht alte Validierungsergebnisse ungültig
        assertTrue(engine.getValidationResults().isEmpty());
    }

    // --- Schritt 7: Validierungsfenster ---

    @Test
    public void testEffectiveValidationWindowDefaults() {
        WorkflowEngine engine = new WorkflowEngine(null);
        engine.setToDate(LocalDate.of(2026, 4, 7));

        // Default: einen Tag nach Optimierungsende bis heute
        assertEquals(LocalDate.of(2026, 4, 8), engine.getEffectiveValidationFromDate());
        assertEquals(LocalDate.now(), engine.getEffectiveValidationToDate());
    }

    @Test
    public void testHasUsableValidationWindow() {
        WorkflowEngine engine = new WorkflowEngine(null);

        // Optimierung endete vor 90 Tagen → 90 Tage unberührte Daten
        engine.setToDate(LocalDate.now().minusDays(90));
        assertTrue(engine.hasUsableValidationWindow(14));

        // Optimierung endet heute → kein unberührtes Fenster
        engine.setToDate(LocalDate.now());
        assertFalse(engine.hasUsableValidationWindow(14));

        // Nur 5 Tage übrig, aber 14 gefordert
        engine.setToDate(LocalDate.now().minusDays(5));
        assertFalse(engine.hasUsableValidationWindow(14));
    }

    @Test
    public void testRunStep7RejectsOverlappingWindow() {
        WorkflowEngine engine = new WorkflowEngine(null);
        engine.setToDate(LocalDate.now().minusDays(90));

        List<CombinedPass> finals = new ArrayList<>();
        finals.add(makePass(1, 1000, 200));
        engine.setFinalSelectedPasses(finals);

        // Fenster beginnt VOR dem Optimierungsende → Überlappung → Abbruch,
        // denn das wäre keine echte Out-of-Sample-Validierung.
        engine.setValidationFromDate(LocalDate.now().minusDays(120));
        engine.setValidationToDate(LocalDate.now());

        try {
            engine.runStep7(null, null);
            fail("Überlappendes Validierungsfenster muss abgelehnt werden");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("überlappt"));
        } catch (Exception e) {
            fail("Erwartet IllegalStateException, war: " + e);
        }
    }

    @Test
    public void testRunStep7RequiresFinalSelection() {
        WorkflowEngine engine = new WorkflowEngine(null);
        engine.setToDate(LocalDate.now().minusDays(90));
        engine.setFinalSelectedPasses(new ArrayList<>());

        try {
            engine.runStep7(null, null);
            fail("Ohne finale Strategien darf Schritt 7 nicht laufen");
        } catch (IllegalStateException expected) {
            // ok
        } catch (Exception e) {
            fail("Erwartet IllegalStateException, war: " + e);
        }
    }

    @Test
    public void testValidationResultVerdicts() {
        ValidationResult vr = new ValidationResult(1);

        vr.setTrades(0);
        vr.computeVerdict();
        assertEquals(ValidationResult.NO_TRADES, vr.getVerdict());
        assertFalse(vr.isPassed());

        vr.setTrades(50);
        vr.setProfit(120.0);
        vr.setRecoveryFactor(1.5);
        vr.computeVerdict();
        assertEquals(ValidationResult.PASSED, vr.getVerdict());
        assertTrue(vr.isPassed());

        vr.setProfit(-80.0);
        vr.computeVerdict();
        assertEquals(ValidationResult.FAILED, vr.getVerdict());
        assertFalse(vr.isPassed());
    }

    @Test
    public void testSelectionThresholdSettersNormalizeInvalidValues() {
        WorkflowEngine engine = new WorkflowEngine(null);

        engine.setMinBtTrades(-10);
        engine.setMinFwTrades(0);
        engine.setMinBtProfit(Double.NaN);
        engine.setMinFwProfit(Double.NEGATIVE_INFINITY);
        engine.setMinBtRecovery(Double.NaN);
        engine.setMinFwRecovery(-2.0);

        assertEquals(1, engine.getMinBtTrades());
        assertEquals(1, engine.getMinFwTrades());
        assertEquals(0.01, engine.getMinBtProfit(), 0.0);
        assertEquals(0.01, engine.getMinFwProfit(), 0.0);
        assertEquals(0.0, engine.getMinBtRecovery(), 0.0);
        assertEquals(0.0, engine.getMinFwRecovery(), 0.0);
    }

    @Test
    public void testValidationResultsPersistedInWorkflowState() {
        WorkflowEngine engine1 = new WorkflowEngine(null);
        engine1.setExpert("PersistEA");

        ValidationResult vr = new ValidationResult(7);
        vr.setProfit(250.0);
        vr.setTrades(40);
        vr.setRecoveryFactor(1.5);
        vr.computeVerdict();
        vr.setValidationFrom("2026-04-08");
        vr.setValidationTo("2026-07-08");
        List<ValidationResult> results = new ArrayList<>();
        results.add(vr);
        engine1.setValidationResults(results);
        engine1.saveState();

        WorkflowEngine engine2 = new WorkflowEngine(null);
        assertEquals(1, engine2.getValidationResults().size());
        ValidationResult loaded = engine2.getValidationResults().get(0);
        assertEquals(7, loaded.getPassNumber());
        assertEquals(ValidationResult.PASSED, loaded.getVerdict());
        assertEquals("2026-04-08", loaded.getValidationFrom());
    }

    @Test
    public void testLegacyPassedValidationIsReevaluatedWhenLoaded() {
        WorkflowEngine engine1 = new WorkflowEngine(null);
        ValidationResult legacy = new ValidationResult(8);
        legacy.setProfit(100.0);
        legacy.setTrades(3);
        legacy.setRecoveryFactor(2.0);
        legacy.setVerdict(ValidationResult.PASSED);
        engine1.setValidationResults(java.util.Collections.singletonList(legacy));
        engine1.saveState();

        WorkflowEngine engine2 = new WorkflowEngine(null);
        ValidationResult loaded = engine2.getValidationResults().get(0);
        assertEquals(ValidationResult.INSUFFICIENT_EVIDENCE, loaded.getVerdict());
        assertFalse(loaded.isPassed());
    }

    @Test
    public void testBestExportAllowedOnlyForPassedOrPendingValidation() throws Exception {
        WorkflowEngine engine = new WorkflowEngine(null);

        // Before Step 7 exists, Step 6 may still export pending strategies.
        assertTrue(canExportToBest(engine, 1));

        ValidationResult passed = new ValidationResult(1);
        passed.setVerdict(ValidationResult.PASSED);
        ValidationResult failed = new ValidationResult(2);
        failed.setVerdict(ValidationResult.FAILED);
        ValidationResult error = new ValidationResult(3);
        error.setVerdict(ValidationResult.ERROR);
        ValidationResult noTrades = new ValidationResult(4);
        noTrades.setVerdict(ValidationResult.NO_TRADES);
        ValidationResult insufficient = new ValidationResult(5);
        insufficient.setVerdict(ValidationResult.INSUFFICIENT_EVIDENCE);

        List<ValidationResult> results = new ArrayList<>();
        results.add(passed);
        results.add(failed);
        results.add(error);
        results.add(noTrades);
        results.add(insufficient);
        engine.setValidationResults(results);

        assertTrue("PASSED darf in den Best-Ordner", canExportToBest(engine, 1));
        assertFalse("FAILED darf nicht in den Best-Ordner", canExportToBest(engine, 2));
        assertFalse("ERROR darf nicht in den Best-Ordner", canExportToBest(engine, 3));
        assertFalse("NO_TRADES darf nicht in den Best-Ordner", canExportToBest(engine, 4));
        assertFalse("INSUFFICIENT_EVIDENCE darf nicht in den Best-Ordner", canExportToBest(engine, 5));
        assertFalse("Fehlendes Ergebnis darf bei vorhandener Validierung nicht als bestanden gelten",
                canExportToBest(engine, 99));
    }

    @Test
    public void testStep7StateSavedToWorkflowHistory() {
        WorkflowEngine engine = new WorkflowEngine(null);
        engine.setExpert("HistoryEA");
        engine.setLastActiveStep(7);

        ValidationResult vr = new ValidationResult(7);
        vr.setProfit(250.0);
        vr.setTrades(40);
        vr.setRecoveryFactor(1.5);
        vr.computeVerdict();
        vr.setValidationFrom("2026-04-08");
        vr.setValidationTo("2026-07-08");
        List<ValidationResult> results = new ArrayList<>();
        results.add(vr);
        engine.setValidationResults(results);

        engine.saveWorkflowToHistory();

        List<HistoryRun> runs = DatabaseManager.getInstance().getRunsByType("Workflow");
        assertFalse(runs.isEmpty());
        String json = runs.get(0).getResultJson();
        assertTrue(json.contains("\"last_active_step\":7"));
        assertTrue(json.contains("validation_results_json"));
        assertTrue(json.contains("\\\"verdict\\\":\\\"PASSED\\\""));
    }

    // --- Pass-Zahl-Berechnung (ersetzt hart kodierte 1000) ---

    @Test
    public void testComputeTotalPasses() {
        List<EaParameter> params = new ArrayList<>();

        EaParameter p1 = new EaParameter("A", "10");
        p1.setOptimizeEnabled(true);
        p1.setOptimizeStart("10");
        p1.setOptimizeStep("10");
        p1.setOptimizeEnd("50"); // 10,20,30,40,50 → 5 Werte
        params.add(p1);

        EaParameter p2 = new EaParameter("B", "1");
        p2.setOptimizeEnabled(true);
        p2.setOptimizeStart("1");
        p2.setOptimizeStep("1");
        p2.setOptimizeEnd("4"); // 4 Werte
        params.add(p2);

        EaParameter disabled = new EaParameter("C", "7");
        disabled.setOptimizeEnabled(false);
        params.add(disabled);

        assertEquals(20, WorkflowEngine.computeTotalPasses(params));
    }

    @Test
    public void testComputeTotalPassesNoEnabledParams() {
        List<EaParameter> params = new ArrayList<>();
        EaParameter p = new EaParameter("A", "10");
        p.setOptimizeEnabled(false);
        params.add(p);
        assertEquals(1, WorkflowEngine.computeTotalPasses(params));
        assertEquals(1, WorkflowEngine.computeTotalPasses(null));
    }

    @Test
    public void testComputeTotalPassesOverflowSafe() {
        // Ein einzelner Parameter mit absurd großem Suchraum: der double-Wert
        // würde beim long-Cast sättigen/überlaufen — muss sauber cappen.
        List<EaParameter> huge = new ArrayList<>();
        EaParameter p = new EaParameter("A", "0");
        p.setOptimizeEnabled(true);
        p.setOptimizeStart("0");
        p.setOptimizeStep("0.0000001");
        p.setOptimizeEnd("1000000000000");
        huge.add(p);
        assertEquals(Integer.MAX_VALUE, WorkflowEngine.computeTotalPasses(huge));

        // Mehrere große Parameter: Multiplikations-Wrap-around darf NIE zu
        // kleinen positiven oder negativen Werten führen.
        List<EaParameter> multi = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            EaParameter q = new EaParameter("P" + i, "0");
            q.setOptimizeEnabled(true);
            q.setOptimizeStart("0");
            q.setOptimizeStep("1");
            q.setOptimizeEnd("100000"); // 100001 Werte, ^4 >> Long.MAX
            multi.add(q);
        }
        assertEquals(Integer.MAX_VALUE, WorkflowEngine.computeTotalPasses(multi));
    }

    @Test
    public void testKiGateBypassedSurvivesRestart() {
        // Nach App-Neustart muss die Schritt-6-Kachel weiterhin WARNUNG zeigen —
        // der Bypass-Zustand wird in WORKFLOW_STATE persistiert.
        WorkflowEngine engine1 = new WorkflowEngine(null);
        CombinedPass p1 = makePass(1, 1000, 200);
        List<CombinedPass> diverse = new ArrayList<>();
        diverse.add(p1);
        engine1.setSelectedDiversePasses(diverse);
        List<SensitivityResult> sens = new ArrayList<>();
        sens.add(makeSensitivity(p1, 10)); // fragil → Gate-Bypass
        engine1.setSensitivityResults(sens);

        engine1.runStep6();
        assertTrue(engine1.isKiGateBypassed());

        WorkflowEngine engine2 = new WorkflowEngine(null);
        assertTrue("KI-Gate-Bypass muss einen Neustart überleben", engine2.isKiGateBypassed());
    }

    @Test
    public void testValidationVerdictRequiresEnoughTradesAndRecovery() {
        ValidationResult vr = new ValidationResult(1);
        vr.setTrades(3);
        vr.setProfit(50.0);
        vr.setRecoveryFactor(2.0);
        vr.computeVerdict();
        assertEquals(ValidationResult.INSUFFICIENT_EVIDENCE, vr.getVerdict());
        assertFalse(vr.isPassed());
        assertTrue("Wenige Trades müssen als unzureichende Evidenz markiert werden",
                vr.getMessage().contains("unzureichende Evidenz"));

        ValidationResult weakRecovery = new ValidationResult(2);
        weakRecovery.setTrades(50);
        weakRecovery.setProfit(50.0);
        weakRecovery.setRecoveryFactor(0.8);
        weakRecovery.computeVerdict();
        assertEquals(ValidationResult.FAILED, weakRecovery.getVerdict());
        assertTrue(weakRecovery.getMessage().contains("Recovery Factor"));

        ValidationResult solid = new ValidationResult(3);
        solid.setTrades(50);
        solid.setProfit(50.0);
        solid.setRecoveryFactor(1.2);
        solid.computeVerdict();
        assertEquals(ValidationResult.PASSED, solid.getVerdict());
        assertTrue(solid.getMessage() == null || solid.getMessage().isEmpty());
    }

    // --- Jahresberechnung des Scores (ersetzt hart kodierte 3.0) ---

    @Test
    public void testYearsBetween() {
        assertEquals(3.0, OptimizationResult.yearsBetween("2023-01-01", "2026-01-01"), 0.01);
        assertEquals(0.5, OptimizationResult.yearsBetween("2025-01-01", "2025-07-02"), 0.01);
        // Fallback bei fehlenden/kaputten Daten: dokumentierte 3.0
        assertEquals(3.0, OptimizationResult.yearsBetween(null, "2026-01-01"), 0.001);
        assertEquals(3.0, OptimizationResult.yearsBetween("kaputt", "2026-01-01"), 0.001);
        assertEquals(3.0, OptimizationResult.yearsBetween("2026-01-01", "2025-01-01"), 0.001);
    }

    // --- Gewichts-Defaults: eine einzige Quelle ---

    @Test
    public void testScoreWeightsLoadFromDatabaseUsesClassDefaults() {
        // Frische DB ohne gespeicherte Settings → loadFromDatabase muss exakt
        // die Klassen-Defaults liefern (früher gab es 3 divergierende Sätze).
        OptimizationResult.ScoreWeights fromDb = OptimizationResult.ScoreWeights.loadFromDatabase();
        OptimizationResult.ScoreWeights def = OptimizationResult.ScoreWeights.defaults();

        assertEquals(def.wBtProfit, fromDb.wBtProfit, 0.001);
        assertEquals(def.wFwProfit, fromDb.wFwProfit, 0.001);
        assertEquals(def.wConsistency, fromDb.wConsistency, 0.001);
        assertEquals(def.wRisk, fromDb.wRisk, 0.001);
        assertEquals(def.wEquityConsist, fromDb.wEquityConsist, 0.001);
        assertEquals(def.wSampleSize, fromDb.wSampleSize, 0.001);
        assertEquals(def.wFwTrades, fromDb.wFwTrades, 0.001);
        assertEquals(def.wRecovery, fromDb.wRecovery, 0.001);
        assertEquals(def.recoveryMin, fromDb.recoveryMin, 0.001);
        assertEquals(def.recoveryMax, fromDb.recoveryMax, 0.001);
        assertEquals(def.total(), fromDb.total(), 0.001);
    }

    @Test
    public void testScoreWeightsLoadFromDatabaseReadsStoredValues() {
        DatabaseManager db = DatabaseManager.getInstance();
        db.saveSetting("opt.weight.fwTrades", "42");

        OptimizationResult.ScoreWeights w = OptimizationResult.ScoreWeights.loadFromDatabase();
        assertEquals(42.0, w.wFwTrades, 0.001);
        // Nicht gesetzte Keys bleiben auf Klassen-Default
        assertEquals(OptimizationResult.ScoreWeights.defaults().wRecovery, w.wRecovery, 0.001);
    }
}
