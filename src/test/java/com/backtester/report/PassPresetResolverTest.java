package com.backtester.report;

import com.backtester.config.EaParameter;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PassPresetResolverTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private static Map<String, String> values(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static EaParameter param(String name, String value, String start, String step,
                                     String end, boolean optimize) {
        EaParameter p = new EaParameter(name, value);
        p.setOptimizeStart(start);
        p.setOptimizeStep(step);
        p.setOptimizeEnd(end);
        p.setOptimizeEnabled(optimize);
        return p;
    }

    private static String valueOf(List<EaParameter> params, String name) {
        return params.stream()
                .filter(p -> p.getName().equals(name))
                .map(EaParameter::getValue)
                .findFirst()
                .orElse(null);
    }

    // ==================== MT5 optimize semantics ====================

    @Test
    public void optimizedParameterTakesOptimizeStartNotValueField() {
        // MT5 ignores the value field when the optimize flag is set. A range that
        // collapses to one value therefore fixes the parameter at "start", and MT5
        // emits no report column for it.
        EaParameter atrTimeframe = param("Inp_ATR_Timeframe", "0", "15", "0", "15", true);
        assertEquals("15", PassPresetResolver.effectiveBaseValue(atrTimeframe));
    }

    @Test
    public void fixedParameterKeepsValueField() {
        EaParameter spread = param("Inp_Spread_Max", "40", "4", "1", "400", false);
        assertEquals("40", PassPresetResolver.effectiveBaseValue(spread));
    }

    @Test
    public void stringParameterIsNeverTakenFromOptimizeStart() {
        EaParameter symbol = param("Inp_VIX_Symbol", "VIX", "", "", "", true);
        symbol.setStringType(true);
        assertEquals("VIX", PassPresetResolver.effectiveBaseValue(symbol));
    }

    @Test
    public void appliesReportColumnsAndRecoversUnreportedOptimizedParameters() {
        List<EaParameter> base = List.of(
                param("Inp_Grid_Step", "500", "1", "1", "6000", true),
                param("Inp_ATR_Timeframe", "0", "15", "0", "15", true),
                param("TimeFrame_Envelopes", "16385", "16385", "0", "16385", false),
                param("Inp_Spread_Max", "40", "4", "1", "400", false));

        List<EaParameter> resolved = PassPresetResolver.applyPassValues(
                base, values("Inp_Grid_Step", "350"), true, 9704, 7.1271);

        assertEquals("350", valueOf(resolved, "Inp_Grid_Step"));
        // Optimized but not reported: must come from the optimize start, not "0".
        assertEquals("15", valueOf(resolved, "Inp_ATR_Timeframe"));
        // Not optimized: the value field of the preset is authoritative.
        assertEquals("16385", valueOf(resolved, "TimeFrame_Envelopes"));
        assertEquals("40", valueOf(resolved, "Inp_Spread_Max"));
        assertTrue(resolved.stream().noneMatch(EaParameter::isOptimizeEnabled));
    }

    @Test
    public void doesNotRewriteValuesWhenBaseIsNotAnOptimizationPreset() {
        List<EaParameter> base = List.of(param("Inp_ATR_Timeframe", "0", "15", "0", "15", true));

        List<EaParameter> resolved = PassPresetResolver.applyPassValues(
                base, values(), false, 1, 0.0);

        assertEquals("0", valueOf(resolved, "Inp_ATR_Timeframe"));
    }

    @Test
    public void injectsMagicNumberAndOrderComment() {
        List<EaParameter> base = List.of(
                param("Inp_Magic_Number", "1", "1", "1", "1", false),
                param("Inp_Order_Comment", "irrelevant", "", "", "", false));

        List<EaParameter> resolved = PassPresetResolver.applyPassValues(
                base, values(), true, 9704, 7.1271);

        assertEquals("9704", valueOf(resolved, "Inp_Magic_Number"));
        assertEquals("7proz_Pass9704", valueOf(resolved, "Inp_Order_Comment"));
    }

    @Test
    public void keepsReportColumnsThatAreMissingFromTheBasePreset() {
        List<EaParameter> base = List.of(param("Inp_Grid_Step", "500", "1", "1", "6000", true));

        List<EaParameter> resolved = PassPresetResolver.applyPassValues(
                base, values("Inp_Grid_Step", "350", "Inp_New_Input", "7"), true, 1, 0.0);

        assertEquals("7", valueOf(resolved, "Inp_New_Input"));
    }

    // ==================== snapshot classification ====================

    @Test
    public void classifiesOptimizationRunFromTesterIni() throws IOException {
        Path dir = tempFolder.newFolder("opt-run").toPath();
        Files.writeString(dir.resolve("tester.ini"),
                "[Tester]\nExpert=TestEA\nModel=0\nOptimization=2\n", StandardCharsets.UTF_8);
        writePreset(dir, "Inp_Grid_Step=500||1||1||6000||Y\n");

        PassPresetResolver.Snapshot snapshot = PassPresetResolver.findSnapshot(dir.toString());

        assertNotNull(snapshot);
        assertTrue(snapshot.fromOptimization());
        assertEquals(0, PassPresetResolver.readTesterModel(dir.toString()));
    }

    @Test
    public void classifiesSingleRunFromTesterIni() throws IOException {
        Path dir = tempFolder.newFolder("single-run").toPath();
        Files.writeString(dir.resolve("tester.ini"),
                "[Tester]\nExpert=TestEA\nModel=1\nOptimization=0\n", StandardCharsets.UTF_8);
        writePreset(dir, "Inp_Grid_Step=350||1||1||6000||N\n");

        PassPresetResolver.Snapshot snapshot = PassPresetResolver.findSnapshot(dir.toString());

        assertNotNull(snapshot);
        assertFalse(snapshot.fromOptimization());
        assertEquals(1, PassPresetResolver.readTesterModel(dir.toString()));
    }

    @Test
    public void fallsBackToOptimizeFlagsWhenTesterIniIsMissing() throws IOException {
        Path dir = tempFolder.newFolder("no-ini").toPath();
        writePreset(dir, "Inp_Grid_Step=500||1||1||6000||Y\n");

        PassPresetResolver.Snapshot snapshot = PassPresetResolver.findSnapshot(dir.toString());

        assertNotNull(snapshot);
        assertTrue(snapshot.fromOptimization());
        assertEquals(-1, PassPresetResolver.readTesterModel(dir.toString()));
    }

    @Test
    public void returnsNoSnapshotWhenPresetIsAbsent() throws IOException {
        Path dir = tempFolder.newFolder("empty-run").toPath();
        Files.writeString(dir.resolve("tester.ini"), "Optimization=0\n", StandardCharsets.UTF_8);

        assertNull(PassPresetResolver.findSnapshot(dir.toString()));
        assertNull(PassPresetResolver.findSnapshot(""));
        assertNull(PassPresetResolver.findSnapshot(null));
    }

    // ==================== end to end ====================

    @Test
    public void rebuildsPassFromArchivedOptimizationPreset() throws IOException {
        Path dir = tempFolder.newFolder("opt-e2e").toPath();
        Files.writeString(dir.resolve("tester.ini"),
                "[Tester]\nExpert=TestEA\nModel=0\nOptimization=2\n", StandardCharsets.UTF_8);
        writePreset(dir,
                "Inp_Grid_Step=500||1||1||6000||Y\n"
                        + "Inp_ATR_Timeframe=0||15||0||15||Y\n"
                        + "TimeFrame_Envelopes=16385||16385||0||16385||N\n");

        Pass backtestPass = new Pass();
        backtestPass.setPassNumber(9704);
        backtestPass.setDrawdownPercent(7.1271);
        backtestPass.setReportDirectory(dir.toString());
        backtestPass.setParameter("Inp_Grid_Step", "350");
        CombinedPass combined = new CombinedPass(backtestPass, null, 0.0, 0.0, "");

        PassPresetResolver.Resolution resolution = PassPresetResolver.resolve(combined, "TestEA");

        assertEquals(PassPresetResolver.Fidelity.OPTIMIZATION_BASE, resolution.fidelity());
        assertNull(resolution.warning());
        assertEquals("350", valueOf(resolution.parameters(), "Inp_Grid_Step"));
        assertEquals("15", valueOf(resolution.parameters(), "Inp_ATR_Timeframe"));
        assertEquals("16385", valueOf(resolution.parameters(), "TimeFrame_Envelopes"));
    }

    @Test
    public void prefersExactSingleRunPresetOverOptimizationPreset() throws IOException {
        Path optDir = tempFolder.newFolder("prefers-opt").toPath();
        Files.writeString(optDir.resolve("tester.ini"),
                "Expert=TestEA\nOptimization=2\n", StandardCharsets.UTF_8);
        writePreset(optDir, "Inp_Grid_Step=500||1||1||6000||Y\n");

        Path runDir = tempFolder.newFolder("prefers-single").toPath();
        Files.writeString(runDir.resolve("tester.ini"),
                "Expert=TestEA\nOptimization=0\n", StandardCharsets.UTF_8);
        writePreset(runDir, "Inp_Grid_Step=350||1||1||6000||N\n");

        Pass backtestPass = new Pass();
        backtestPass.setPassNumber(9704);
        backtestPass.setReportDirectory(optDir.toString());
        Pass longtermPass = new Pass();
        longtermPass.setPassNumber(9704);
        longtermPass.setReportDirectory(runDir.toString());
        CombinedPass combined = new CombinedPass(backtestPass, null, longtermPass, 0.0, 0.0, "");
        combined.setReportDirectory(runDir.toString());

        PassPresetResolver.Resolution resolution = PassPresetResolver.resolve(combined, "TestEA");

        assertEquals(PassPresetResolver.Fidelity.EXACT_SNAPSHOT, resolution.fidelity());
        assertEquals("350", valueOf(resolution.parameters(), "Inp_Grid_Step"));
    }

    @Test
    public void warnsWhenNoArchivedPresetExists() {
        Pass backtestPass = new Pass();
        backtestPass.setPassNumber(4711);
        backtestPass.setParameter("Inp_Grid_Step", "350");
        CombinedPass combined = new CombinedPass(backtestPass, null, 0.0, 0.0, "");

        PassPresetResolver.Resolution resolution =
                PassPresetResolver.resolve(combined, "__no_such_expert__");

        assertEquals(PassPresetResolver.Fidelity.CURRENT_CONFIG, resolution.fidelity());
        assertNotNull(resolution.warning());
        assertEquals("350", valueOf(resolution.parameters(), "Inp_Grid_Step"));
    }

    private static void writePreset(Path dir, String content) throws IOException {
        Files.writeString(dir.resolve(BacktestArtifactReplayResolver.PRESET_SNAPSHOT_FILE),
                content, StandardCharsets.UTF_8);
    }
}
