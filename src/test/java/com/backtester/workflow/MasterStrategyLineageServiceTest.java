package com.backtester.workflow;

import com.backtester.config.EaParameter;
import com.backtester.engine.BacktestConfig;
import com.backtester.report.BacktestResult;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import com.google.gson.Gson;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MasterStrategyLineageServiceTest {

    @Test
    public void firstEntryHasNoReferenceToCompareAgainst() {
        CustomProject project = new CustomProject("P", "EA", "AUDCAD", "M5");

        MasterStrategyEntry entry = MasterStrategyLineageService.append(project, measured(1000, 250));

        assertEquals(1, entry.getSequence());
        assertEquals(MasterStrategyEntry.Verdict.UNBEKANNT, entry.getVerdict());
        assertEquals(-1, entry.getComparedToSequence());
        assertEquals(1, project.getMasterStrategyLineage().size());
    }

    @Test
    public void moreProfitPerDrawdownCountsAsImprovement() {
        CustomProject project = new CustomProject("P", "EA", "AUDCAD", "M5");
        confirm(project, MasterStrategyLineageService.append(project, measured(1000, 250)));

        MasterStrategyEntry second = MasterStrategyLineageService.append(project, measured(1500, 250));

        assertEquals(MasterStrategyEntry.Verdict.BESSER, second.getVerdict());
        assertEquals(1, second.getComparedToSequence());
        assertEquals(500.0, second.getDeltaProfit(), 1e-9);
        assertEquals(2.0, second.getDeltaReturnToDrawdown(), 1e-9);
    }

    @Test
    public void moreProfitBoughtWithMoreDrawdownIsNotAnImprovement() {
        CustomProject project = new CustomProject("P", "EA", "AUDCAD", "M5");
        confirm(project, MasterStrategyLineageService.append(project, measured(1000, 250)));

        // +50% profit, but the drawdown doubled: profit/DD drops from 4.0 to 3.0.
        MasterStrategyEntry second = MasterStrategyLineageService.append(project, measured(1500, 500));

        assertEquals(MasterStrategyEntry.Verdict.SCHLECHTER, second.getVerdict());
        assertTrue(second.getDeltaProfit() > 0);
    }

    @Test
    public void changesInsideTheToleranceBandAreReportedAsUnchanged() {
        CustomProject project = new CustomProject("P", "EA", "AUDCAD", "M5");
        confirm(project, MasterStrategyLineageService.append(project, measured(1000, 250)));

        MasterStrategyEntry second = MasterStrategyLineageService.append(project, measured(1009, 250));

        assertEquals(MasterStrategyEntry.Verdict.NEUTRAL, second.getVerdict());
    }

    @Test
    public void exactlyOnePercentAlreadyCountsAsAChange() {
        assertEquals(MasterStrategyEntry.Verdict.BESSER,
                MasterStrategyLineageService.judge(1.01, 1.0));
        assertEquals(MasterStrategyEntry.Verdict.SCHLECHTER,
                MasterStrategyLineageService.judge(0.99, 1.0));
        assertEquals(MasterStrategyEntry.Verdict.NEUTRAL,
                MasterStrategyLineageService.judge(1.009, 1.0));
        assertEquals(MasterStrategyEntry.Verdict.NEUTRAL,
                MasterStrategyLineageService.judge(0.991, 1.0));
    }

    @Test
    public void aBaselineOfZeroHasNoRelativeScale() {
        assertEquals(MasterStrategyEntry.Verdict.BESSER,
                MasterStrategyLineageService.judge(0.5, 0.0));
        assertEquals(MasterStrategyEntry.Verdict.SCHLECHTER,
                MasterStrategyLineageService.judge(-0.5, 0.0));
        assertEquals(MasterStrategyEntry.Verdict.NEUTRAL,
                MasterStrategyLineageService.judge(0.0, 0.0));
    }

    @Test
    public void negativeBaselinesKeepTheirDirection() {
        assertEquals(MasterStrategyEntry.Verdict.BESSER,
                MasterStrategyLineageService.judge(-0.5, -1.0));
        assertEquals(MasterStrategyEntry.Verdict.SCHLECHTER,
                MasterStrategyLineageService.judge(-1.5, -1.0));
    }

    @Test
    public void nonFiniteMetricsNeverProduceAVerdict() {
        assertEquals(MasterStrategyEntry.Verdict.UNBEKANNT,
                MasterStrategyLineageService.judge(Double.NaN, 1.0));
        assertEquals(MasterStrategyEntry.Verdict.UNBEKANNT,
                MasterStrategyLineageService.judge(1.0, Double.POSITIVE_INFINITY));
    }

    @Test
    public void everyEntryIsRatedAgainstTheConfirmedMasterNotTheLastOne() {
        CustomProject project = new CustomProject("P", "EA", "AUDCAD", "M5");
        confirm(project, MasterStrategyLineageService.append(project,
                measured(2000, 250)));                                      // #1, confirmed
        MasterStrategyLineageService.append(project, measured(1000, 250));   // #2, profit/DD 4.0

        // Better than the immediate predecessor, still far below the best entry.
        MasterStrategyEntry third = MasterStrategyLineageService.append(project, measured(1200, 250));

        assertEquals(1, third.getComparedToSequence());
        assertEquals(MasterStrategyEntry.Verdict.SCHLECHTER, third.getVerdict());
    }

    @Test
    public void failedReferenceBacktestsAreNeitherRatedNorUsedAsBaseline() {
        CustomProject project = new CustomProject("P", "EA", "AUDCAD", "M5");
        confirm(project, MasterStrategyLineageService.append(project, measured(1000, 250)));

        MasterStrategyEntry failed = new MasterStrategyEntry();
        failed.setBacktestSucceeded(false);
        failed.setFailureMessage("MetaTrader lieferte kein Ergebnis.");
        MasterStrategyLineageService.append(project, failed);

        assertEquals(MasterStrategyEntry.Verdict.UNBEKANNT, failed.getVerdict());
        assertEquals(1,
                MasterStrategyLineageService.bestEntry(project.getMasterStrategyLineage(), "")
                        .orElseThrow().getSequence());
    }

    @Test
    public void withoutADrawdownFigureNoVerdictIsInvented() {
        CustomProject project = new CustomProject("P", "EA", "AUDCAD", "M5");
        confirm(project, MasterStrategyLineageService.append(project, measured(1000, 250)));

        MasterStrategyEntry noDrawdown = measured(5000, 0);
        MasterStrategyLineageService.append(project, noDrawdown);

        assertFalse(Double.isFinite(noDrawdown.getReturnToDrawdown()));
        assertEquals(MasterStrategyEntry.Verdict.UNBEKANNT, noDrawdown.getVerdict());
    }

    @Test
    public void anUnratableEntryCannotBecomeTheBestOne() {
        CustomProject project = new CustomProject("P", "EA", "AUDCAD", "M5");
        MasterStrategyEntry noDrawdown = measured(5000, 0);
        MasterStrategyLineageService.append(project, noDrawdown);
        MasterStrategyEntry rated = measured(1000, 250);
        MasterStrategyLineageService.append(project, rated);

        assertEquals(2, MasterStrategyLineageService
                .bestEntry(project.getMasterStrategyLineage(), "").orElseThrow().getSequence());
    }

    @Test
    public void infiniteDrawdownDoesNotFakeAValidRatio() {
        assertFalse(Double.isFinite(MasterStrategyLineageService.returnToDrawdown(
                1000, Double.POSITIVE_INFINITY)));
        assertFalse(Double.isFinite(MasterStrategyLineageService.returnToDrawdown(
                Double.NaN, 250)));
    }

    @Test
    public void backtestResultIsMappedOntoTheEntry() {
        BacktestConfig config = referenceConfig("AUDCAD", "M5");
        BacktestResult result = new BacktestResult();
        result.setSuccess(true);
        result.setTotalProfit(1234.5);
        result.setProfitFactor(1.42);
        result.setMaxDrawdownPercent(12.5);
        result.setMaxDrawdownAbsolute(411.5);
        result.setTotalTrades(87);
        result.setEquityHistory(List.of(new double[]{0, 10000, 10000}, new double[]{1, 10120, 10090}));

        MasterStrategyEntry entry = MasterStrategyLineageService.toEntry(config, result);

        assertTrue(entry.isBacktestSucceeded());
        assertEquals(1234.5, entry.getProfit(), 1e-9);
        assertEquals(87, entry.getTotalTrades());
        assertEquals(1234.5 / 411.5, entry.getReturnToDrawdown(), 1e-9);
        assertEquals(2, entry.getEquityCurve().size());
        assertEquals(MasterStrategyLineageService.REFERENCE_FROM, entry.getFromDate());
        assertEquals("1 minute OHLC", entry.getTickModel());
        assertEquals(MasterStrategyLineageService.REFERENCE_DEPOSIT, entry.getDeposit());
        assertEquals(MasterStrategyLineageService.REFERENCE_CURRENCY, entry.getCurrency());
        assertEquals(MasterStrategyLineageService.REFERENCE_LEVERAGE, entry.getLeverage());
        assertEquals(MasterStrategyLineageService.contextKey(config), entry.contextKey());
    }

    @Test
    public void longEquityCurvesAreCappedButKeepStartAndEnd() {
        List<double[]> points = new ArrayList<>();
        for (int i = 0; i < 12_000; i++) {
            points.add(new double[]{i, 10_000 + i, 10_000 + i});
        }

        List<double[]> capped = MasterStrategyLineageService.capEquityPoints(points);

        assertEquals(MasterStrategyLineageService.MAX_EQUITY_POINTS, capped.size());
        assertEquals(0.0, capped.get(0)[0], 1e-9);
        assertEquals(11_999.0, capped.get(capped.size() - 1)[0], 1e-9);
    }

    @Test
    public void referenceConfigPinsTheComparableConditions() {
        assertEquals(ToTheMoon132GuidedWorkflowFactory.SEARCH_FROM,
                MasterStrategyLineageService.REFERENCE_FROM);
        CustomProject project = new CustomProject("P", "EA", "AUDCAD", "M5");

        BacktestConfig config = MasterStrategyLineageService.buildReferenceConfig(project, "preset.set");

        assertEquals("AUDCAD", config.getSymbol());
        assertEquals("M5", config.getPeriod());
        assertEquals(BacktestConfig.MODEL_OHLC_M1, config.getModel());
        assertEquals(MasterStrategyLineageService.REFERENCE_FROM, config.getFromDate().toString());
        assertEquals(MasterStrategyLineageService.REFERENCE_TO, config.getToDate().toString());
        assertEquals(MasterStrategyLineageService.REFERENCE_DEPOSIT, config.getDeposit());
        assertEquals(MasterStrategyLineageService.REFERENCE_CURRENCY, config.getCurrency());
        assertEquals(MasterStrategyLineageService.REFERENCE_LEVERAGE, config.getLeverage());
        assertEquals("preset.set", config.getExpertParameters());
    }

    @Test
    public void referenceConfigUsesThePersistedWorkflowSearchWindow() {
        CustomProject project = new CustomProject("P", "EA", "GBPJPY", "M15");
        WorkflowTask optimizer = new WorkflowTask("Search", WorkflowTask.TaskType.OPTIMIZER);
        optimizer.setStartDate("2022-08-01");
        optimizer.setEndDate("2025-08-01");
        optimizer.setRetestSymbol("GBPJPY");
        optimizer.setRetestPeriod("M15");
        optimizer.setExecutionMode(WorkflowTask.MODE_OHLC_M1);
        project.addTask(optimizer);

        BacktestConfig config = MasterStrategyLineageService.buildReferenceConfig(project, "preset.set");

        assertEquals("2022-08-01", config.getFromDate().toString());
        assertEquals("2025-08-01", config.getToDate().toString());
        assertEquals(BacktestConfig.MODEL_OHLC_M1, config.getModel());
    }

    @Test
    public void lineageSurvivesPersistenceEvenWhenDatabankContentsAreNotSaved() {
        CustomProject project = new CustomProject("P", "EA", "AUDCAD", "M5");
        project.setSaveDatabanksPersistently(false);
        MasterStrategyEntry entry = measured(1000, 250);
        entry.setAdoptedChanges(List.of("EnvelopePeriod: 10 → 18"));
        MasterStrategyLineageService.append(project, entry);

        CustomProject persisted = project.copyMetadataForPersistence();

        assertEquals(1, persisted.getMasterStrategyLineage().size());
        MasterStrategyEntry copy = persisted.getMasterStrategyLineage().get(0);
        assertEquals(1000.0, copy.getProfit(), 1e-9);
        assertEquals(List.of("EnvelopePeriod: 10 → 18"), copy.getAdoptedChanges());
        assertTrue(persisted.isReferenceBacktestEnabled());
    }

    @Test
    public void legacyProjectsWithoutTheNewFieldsKeepTheDefaults() {
        String legacyJson = "{\"id\":\"abc\",\"name\":\"Alt\",\"expert\":\"EA\","
                + "\"symbol\":\"AUDCAD\",\"period\":\"M5\",\"tasks\":[]}";

        CustomProject loaded = new Gson().fromJson(legacyJson, CustomProject.class);

        assertTrue(loaded.isReferenceBacktestEnabled());
        assertTrue(loaded.getMasterStrategyLineage().isEmpty());
        // The lazily created lock must work on a Gson-built instance too.
        MasterStrategyLineageService.append(loaded, measured(1000, 250));
        assertEquals(1, loaded.getMasterStrategyLineage().size());
    }

    @Test
    public void cloningKeepsTheReferenceBacktestSwitchOff() {
        CustomProject project = new CustomProject("P", "EA", "AUDCAD", "M5");
        project.setReferenceBacktestEnabled(false);

        CustomProject clone = project.cloneProject("Kopie", "EURUSD", "H1");

        assertFalse(clone.isReferenceBacktestEnabled());
        assertTrue(clone.getMasterStrategyLineage().isEmpty());
    }

    @Test
    public void theSameParameterBasisIsRecognizedRegardlessOfParameterOrder() {
        BacktestConfig context = referenceConfig("AUDCAD", "M5");
        String first = MasterStrategyLineageService.measurementSignature(context, List.of(
                parameter("EnvelopePeriod", "18"), parameter("AtrMult", "1.5")));
        String second = MasterStrategyLineageService.measurementSignature(context, List.of(
                parameter("AtrMult", "1.5"), parameter("EnvelopePeriod", "18")));
        String changed = MasterStrategyLineageService.measurementSignature(context, List.of(
                parameter("EnvelopePeriod", "19"), parameter("AtrMult", "1.5")));

        assertEquals(first, second);
        assertNotEquals(first, changed);
    }

    @Test
    public void separatorsInsideValuesCannotFakeAnIdenticalBasis() {
        BacktestConfig context = referenceConfig("AUDCAD", "M5");
        String packed = MasterStrategyLineageService.measurementSignature(context,
                List.of(parameter("a", "b;c=d")));
        String split = MasterStrategyLineageService.measurementSignature(context,
                List.of(parameter("a", "b"), parameter("c", "d")));

        assertNotEquals(packed, split);
    }

    @Test
    public void anEmptyParameterListStillYieldsAStableSignature() {
        BacktestConfig context = referenceConfig("AUDCAD", "M5");

        String signature = MasterStrategyLineageService.measurementSignature(context, List.of());

        assertFalse(signature.isBlank());
        assertEquals(signature, MasterStrategyLineageService.measurementSignature(context, List.of()));
    }

    @Test
    public void theSameParametersOnAnotherMarketAreADifferentMeasurement() {
        List<EaParameter> basis = List.of(parameter("EnvelopePeriod", "18"));

        String audcad = MasterStrategyLineageService.measurementSignature(
                referenceConfig("AUDCAD", "M5"), basis);
        String eurusd = MasterStrategyLineageService.measurementSignature(
                referenceConfig("EURUSD", "M5"), basis);
        String otherTimeframe = MasterStrategyLineageService.measurementSignature(
                referenceConfig("AUDCAD", "H1"), basis);

        assertNotEquals(audcad, eurusd);
        assertNotEquals(audcad, otherTimeframe);
    }

    @Test
    public void entriesFromAnotherMarketAreNotUsedAsBaseline() {
        CustomProject project = new CustomProject("P", "EA", "AUDCAD", "M5");
        MasterStrategyEntry audcad = measured(5000, 250);
        audcad.setContextKey(MasterStrategyLineageService.contextKey(referenceConfig("AUDCAD", "M5")));
        MasterStrategyLineageService.append(project, audcad);

        MasterStrategyEntry eurusd = measured(1000, 250);
        eurusd.setContextKey(MasterStrategyLineageService.contextKey(referenceConfig("EURUSD", "M5")));
        MasterStrategyLineageService.append(project, eurusd);

        // Far worse in absolute numbers, but it is the first measurement of its cohort.
        assertEquals(MasterStrategyEntry.Verdict.UNBEKANNT, eurusd.getVerdict());
        assertEquals(-1, eurusd.getComparedToSequence());
    }

    @Test
    public void restartingAfterAPauseDoesNotMeasureTheSameBasisTwice() {
        CustomProject project = new CustomProject("P", "EA", "AUDCAD", "M5");
        BacktestConfig context = referenceConfig("AUDCAD", "M5");
        List<EaParameter> basis = List.of(
                parameter("EnvelopePeriod", "18"), parameter("AtrMult", "1.5"));
        MasterStrategyEntry entry = measured(1000, 250);
        entry.setMeasurementSignature(MasterStrategyLineageService.measurementSignature(context, basis));
        MasterStrategyLineageService.append(project, entry);

        assertTrue(MasterStrategyLineageService.alreadyMeasured(
                project, MasterStrategyLineageService.measurementSignature(context, basis)));
        assertFalse(MasterStrategyLineageService.alreadyMeasured(
                project, MasterStrategyLineageService.measurementSignature(context,
                        List.of(parameter("EnvelopePeriod", "20"), parameter("AtrMult", "1.5")))));
    }

    @Test
    public void aReferenceRunRefusesToStartWhenThePresetWasNotWritten() throws Exception {
        // writeSetFile only logs an IOException. Without this check MT5 would run on the
        // previous run's preset — under the same file name — and the measurement would be
        // recorded as if it had tested the new basis.
        List<EaParameter> basis = List.of(
                parameter("GridStep", "23"), parameter("EnvelopePeriod", "30"));

        Path missing = Files.createTempDirectory("preset-check").resolve("absent.set");
        try {
            MasterStrategyLineageService.verifyPresetWritten(missing, basis);
            fail("Expected a missing preset to abort the reference run");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("nicht angelegt"));
        }

        Path preset = Files.createTempFile("preset-check", ".set");
        Files.write(preset, new byte[]{(byte) 0xFF, (byte) 0xFE});
        try {
            MasterStrategyLineageService.verifyPresetWritten(preset, basis);
            fail("Expected a preset without parameters to abort the reference run");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("leer"));
        }

        // A write that died halfway leaves a file that looks plausible but is short.
        writePreset(preset, "; header\r\nGridStep=23\r\n");
        try {
            MasterStrategyLineageService.verifyPresetWritten(preset, basis);
            fail("Expected a truncated preset to abort the reference run");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("EnvelopePeriod"));
        }

        writePreset(preset, "; header\r\nGridStep=23\r\nEnvelopePeriod=30||10||5||50||Y\r\n");
        MasterStrategyLineageService.verifyPresetWritten(preset, basis);
    }

    @Test
    public void aPresetWithTheWrongValuesIsRejectedEvenThoughEveryNameIsThere() {
        // A leftover file from another basis carries every parameter name and differs only
        // in the values — exactly the case this guard exists for. Checking the names alone
        // would wave it through and record the measurement under the new basis' signature.
        List<EaParameter> basis = List.of(parameter("GridStep", "23"));
        try {
            Path preset = Files.createTempFile("preset-values", ".set");
            writePreset(preset, "; header\r\nGridStep=999||10||5||50||Y\r\n");
            MasterStrategyLineageService.verifyPresetWritten(preset, basis);
            fail("Expected a preset holding another basis' values to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("999"));
        }
    }

    @Test
    public void aValueThatOnlyLooksDifferentIsStillAccepted() throws Exception {
        // The file is written through the value normalisation, so "23" and "23.0" are the
        // same value. Aborting on that would stop legitimate runs for a formatting detail.
        List<EaParameter> basis = List.of(parameter("GridStep", "23"));
        Path preset = Files.createTempFile("preset-equivalent", ".set");
        writePreset(preset, "GridStep=23.0\r\n");

        MasterStrategyLineageService.verifyPresetWritten(preset, basis);
    }

    @Test
    public void forgettingTheMasterRemovesTheLineageTheBasisAndTheFloorTogether() {
        // Leaving the confirmed basis behind after erasing its lineage would produce a
        // master with no measurement behind it, and the next candidate would count as the
        // first one and replace it without any comparison.
        CustomProject project = new CustomProject("Guided", "EA", "AUDCAD", "M5");
        MasterStrategyLineageService.append(project, measured(3000, 1000));
        project.setProvenMasterParameters(List.of(parameter("GridStep", "23")));
        project.setProvenMasterContextKey("EA|AUDCAD|M5");
        project.setMasterSelectionRatio(3.0);
        project.setConfirmedMasterSequence(1);

        int removed = MasterStrategyLineageService.clear(project);

        assertEquals(1, removed);
        assertTrue(project.getMasterStrategyLineage().isEmpty());
        assertFalse(project.hasProvenMaster());
        assertTrue(Double.isNaN(project.getMasterSelectionRatio()));
        assertEquals(-1, project.getConfirmedMasterSequence());
    }

    @Test
    public void aMasterMeasuredUnderOtherConditionsIsDroppedTogetherWithItsFloor() {
        // Its parameters prove nothing on another symbol, and its floor would reject every
        // candidate before a single one has been measured under the new conditions.
        CustomProject project = new CustomProject("Guided", "EA", "AUDCAD", "M5");
        project.setProvenMasterParameters(List.of(parameter("GridStep", "23")));
        project.setMasterSelectionRatio(3.0);
        project.setProvenMasterContextKey(MasterStrategyLineageService.currentContextKey(project));

        assertFalse(MasterStrategyLineageService.rebaselineOnContextChange(project));
        assertTrue(project.hasProvenMaster());

        project.setSymbol("EURUSD");

        assertTrue(MasterStrategyLineageService.rebaselineOnContextChange(project));
        assertFalse(project.hasProvenMaster());
        assertTrue(Double.isNaN(project.getMasterSelectionRatio()));
    }

    @Test
    public void aParameterIsNotAcceptedBecauseAnotherOneEndsWithItsName() {
        // "Period" must not be considered present just because "EnvelopePeriod=" is there.
        List<EaParameter> basis = List.of(parameter("Period", "30"));
        try {
            Path preset = Files.createTempFile("preset-suffix", ".set");
            writePreset(preset, "EnvelopePeriod=30\r\n");
            MasterStrategyLineageService.verifyPresetWritten(preset, basis);
            fail("Expected the suffix match to be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("unvollständig"));
        }
    }

    private static void writePreset(Path file, String body) throws IOException {
        byte[] text = body.getBytes(StandardCharsets.UTF_16LE);
        byte[] all = new byte[2 + text.length];
        all[0] = (byte) 0xFF;
        all[1] = (byte) 0xFE;
        System.arraycopy(text, 0, all, 2, text.length);
        Files.write(file, all);
    }

    @Test
    public void aRejectedBasisIsStillRejectedAfterARestart() {
        // A basis that was measured and found worse must not slip through on a restart as
        // "nothing left to measure" — the recorded verdict has to be reachable, not just
        // the fact that a measurement exists.
        CustomProject project = new CustomProject("P", "EA", "AUDCAD", "M5");
        BacktestConfig context = referenceConfig("AUDCAD", "M5");
        confirm(project, MasterStrategyLineageService.append(project, measured(2000, 250)));

        List<EaParameter> rejected = List.of(parameter("EnvelopePeriod", "18"));
        MasterStrategyEntry worse = measured(500, 250);
        worse.setMeasurementSignature(
                MasterStrategyLineageService.measurementSignature(context, rejected));
        MasterStrategyLineageService.append(project, worse);
        assertEquals(MasterStrategyEntry.Verdict.SCHLECHTER, worse.getVerdict());

        MasterStrategyEntry known = MasterStrategyLineageService.findLatestMeasurement(project,
                MasterStrategyLineageService.measurementSignature(context, rejected)).orElseThrow();
        assertEquals(MasterStrategyEntry.Verdict.SCHLECHTER, known.getVerdict());
    }

    @Test
    public void aRejectedMeasurementDoesNotBecomeTheComparisonAnchor() {
        // The rolled-back entry stays in the lineage as the record of what was tried, but
        // the next stage is still compared against the best proven master.
        CustomProject project = new CustomProject("P", "EA", "AUDCAD", "M5");
        confirm(project, MasterStrategyLineageService.append(project, measured(2000, 250)));
        MasterStrategyLineageService.append(project, measured(500, 250));

        MasterStrategyEntry next = MasterStrategyLineageService.append(project, measured(1500, 250));

        assertEquals(1, next.getComparedToSequence());
        assertEquals(MasterStrategyEntry.Verdict.SCHLECHTER, next.getVerdict());
    }

    @Test
    public void aFailedMeasurementDoesNotBlockTheRetry() {
        CustomProject project = new CustomProject("P", "EA", "AUDCAD", "M5");
        BacktestConfig context = referenceConfig("AUDCAD", "M5");
        List<EaParameter> basis = List.of(parameter("EnvelopePeriod", "18"));
        String signature = MasterStrategyLineageService.measurementSignature(context, basis);

        MasterStrategyEntry failed = new MasterStrategyEntry();
        failed.setBacktestSucceeded(false);
        failed.setMeasurementSignature(signature);
        MasterStrategyLineageService.append(project, failed);

        assertFalse(MasterStrategyLineageService.alreadyMeasured(project, signature));
    }

    @Test
    public void anOlderMeasurementDoesNotBlockANewOne() {
        CustomProject project = new CustomProject("P", "EA", "AUDCAD", "M5");
        BacktestConfig context = referenceConfig("AUDCAD", "M5");
        List<EaParameter> older = List.of(parameter("EnvelopePeriod", "18"));
        MasterStrategyEntry first = measured(1000, 250);
        first.setMeasurementSignature(MasterStrategyLineageService.measurementSignature(context, older));
        MasterStrategyLineageService.append(project, first);
        MasterStrategyEntry second = measured(1200, 250);
        second.setMeasurementSignature(MasterStrategyLineageService.measurementSignature(
                context, List.of(parameter("EnvelopePeriod", "22"))));
        MasterStrategyLineageService.append(project, second);

        // Only the newest basis counts as "already measured" — going back to an older
        // one is a real change of the master strategy and must be re-measured.
        assertFalse(MasterStrategyLineageService.alreadyMeasured(
                project, MasterStrategyLineageService.measurementSignature(context, older)));
    }

    @Test
    public void concurrentAppendsKeepEveryEntryAndNumberThemOnce() throws Exception {
        CustomProject project = new CustomProject("P", "EA", "AUDCAD", "M5");
        int writers = 8;
        int perWriter = 25;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(writers);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        for (int w = 0; w < writers; w++) {
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perWriter; i++) {
                        MasterStrategyLineageService.append(project, measured(1000 + i, 250));
                    }
                } catch (Throwable ex) {
                    failure.compareAndSet(null, ex);
                } finally {
                    done.countDown();
                }
            });
            thread.setDaemon(true);
            thread.start();
        }

        Thread reader = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < 400; i++) {
                    project.copyMetadataForPersistence();
                }
            } catch (Throwable ex) {
                failure.compareAndSet(null, ex);
            }
        });
        reader.setDaemon(true);
        reader.start();

        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        reader.join(TimeUnit.SECONDS.toMillis(30));

        assertNull(failure.get());
        List<MasterStrategyEntry> lineage = project.getMasterStrategyLineage();
        assertEquals(writers * perWriter, lineage.size());
        for (int i = 0; i < lineage.size(); i++) {
            assertEquals(i + 1, lineage.get(i).getSequence());
        }
    }

    @Test
    public void clearingRemovesTheMeasurementsAndTheProfitPerDrawdownFloor() {
        CustomProject project = new CustomProject("P", "EA", "AUDCAD", "M5");
        MasterStrategyLineageService.append(project, measured(1000, 250));
        MasterStrategyLineageService.append(project, measured(1200, 250));
        project.setMasterSelectionRatio(11.79);

        assertEquals(2, MasterStrategyLineageService.clear(project));

        assertTrue(project.getMasterStrategyLineage().isEmpty());
        assertFalse(Double.isFinite(project.getMasterSelectionRatio()));
        // Numbering starts over, otherwise the fresh run would look like a continuation.
        assertEquals(1, MasterStrategyLineageService.append(project, measured(900, 250)).getSequence());
        assertEquals(0, MasterStrategyLineageService.clear(null));
    }

    @Test
    public void clearKeepsTheLineageLockUntilConfirmationMetadataIsGone() throws Exception {
        BlockingClearProject project = new BlockingClearProject();
        MasterStrategyLineageService.append(project, measured(1000, 250));
        project.setProvenMasterParameters(List.of(parameter("GridStep", "23")));
        project.setProvenMasterContextKey(MasterStrategyLineageService.currentContextKey(project));
        project.setConfirmedMasterSequence(1);

        CountDownLatch clearFinished = new CountDownLatch(1);
        Thread clearer = new Thread(() -> {
            try {
                MasterStrategyLineageService.clear(project);
            } finally {
                clearFinished.countDown();
            }
        });
        clearer.setDaemon(true);
        clearer.start();
        assertTrue(project.clearEntered.await(5, TimeUnit.SECONDS));

        CountDownLatch appendStarted = new CountDownLatch(1);
        CountDownLatch appendFinished = new CountDownLatch(1);
        Thread appender = new Thread(() -> {
            appendStarted.countDown();
            try {
                MasterStrategyLineageService.append(project, measured(900, 250));
            } finally {
                appendFinished.countDown();
            }
        });
        appender.setDaemon(true);
        appender.start();
        assertTrue(appendStarted.await(5, TimeUnit.SECONDS));

        // clearProvenMaster is deliberately paused. An append may not pass the lineage
        // lock while the history is empty but still points at the old confirmation.
        assertFalse(appendFinished.await(200, TimeUnit.MILLISECONDS));
        project.allowClear.countDown();

        assertTrue(clearFinished.await(5, TimeUnit.SECONDS));
        assertTrue(appendFinished.await(5, TimeUnit.SECONDS));
        assertFalse(project.hasProvenMaster());
        assertEquals(-1, project.getConfirmedMasterSequence());
        assertEquals(1, project.getMasterStrategyLineage().size());
        assertEquals(MasterStrategyEntry.Verdict.UNBEKANNT,
                project.getMasterStrategyLineage().get(0).getVerdict());
    }

    @Test
    public void aWorkerFromBeforeClearCannotRecreateTheErasedLineage() {
        CustomProject project = new CustomProject("P", "EA", "AUDCAD", "M5");
        MasterStrategyLineageService.append(project, measured(1000, 250));
        long workerGeneration = MasterStrategyLineageService.captureLineageGeneration(project);

        MasterStrategyLineageService.clear(project);

        assertNull(MasterStrategyLineageService.appendIfGeneration(
                project, measured(1200, 250), workerGeneration));
        assertTrue(project.getMasterStrategyLineage().isEmpty());

        long freshGeneration = MasterStrategyLineageService.captureLineageGeneration(project);
        assertNotNull(MasterStrategyLineageService.appendIfGeneration(
                project, measured(900, 250), freshGeneration));
        assertEquals(1, project.getMasterStrategyLineage().size());
    }

    @Test
    public void theSummaryCarriesOptimizedParametersAndTheNextStagesGrid() {
        CustomProject project = new CustomProject("P", "EA", "AUDCAD", "M5");
        WorkflowTask stage1 = new WorkflowTask("Stufe 1", WorkflowTask.TaskType.OPTIMIZER);
        stage1.setTargetDatabank("stage1");
        stage1.setOptimizerTargetParameters(List.of("GridStep", "StepMultiplier"));
        WorkflowTask stage2 = new WorkflowTask("Stufe 2", WorkflowTask.TaskType.OPTIMIZER);
        stage2.setTargetDatabank("stage2");
        stage2.setOptimizerTargetParameters(List.of("EnvelopePeriod"));
        project.addTask(stage1);
        project.addTask(stage2);

        EaParameter gridStep = optimizable("GridStep", "15", "10", "1", "30");
        EaParameter multiplier = optimizable("StepMultiplier", "1.2", "1.0", "0.1", "2.0");
        EaParameter period = optimizable("EnvelopePeriod", "20", "5", "5", "40");

        Pass backtest = new Pass();
        backtest.setPassNumber(11);
        backtest.setParameterValues(new java.util.LinkedHashMap<>(java.util.Map.of(
                "GridStep", "23", "StepMultiplier", "1.2")));
        CombinedPass selected = new CombinedPass(backtest, null, 70.0, 0.0, "test");

        List<EaParameter> template = List.of(gridStep, multiplier, period);
        GuidedOptimizationService.AdoptionPreview preview =
                GuidedOptimizationService.previewPassAdoption(project, template, null, selected, "stage1");
        GuidedOptimizationService.AdoptionResult result =
                GuidedOptimizationService.adoptPassParameters(project, template, null, selected, "stage1");

        MasterStrategyLineageService.AdoptionSummary summary = MasterStrategyLineageService
                .summarize(preview, result.getNextOptimizer(), result.getParameters());

        assertEquals("Stufe 1", summary.getProducerStageName());
        assertEquals(2, summary.getOptimizedParameters().size());
        assertEquals("15", summary.getOptimizedParameters().get(0).getOldValue());
        assertEquals("23", summary.getOptimizedParameters().get(0).getNewValue());
        assertTrue(summary.getOptimizedParameters().get(0).isChanged());
        assertFalse(summary.getOptimizedParameters().get(1).isChanged());
        assertEquals(List.of("GridStep: 15 → 23"), summary.describeChangedParameters());

        assertEquals(1, summary.getNextStageTargets().size());
        MasterStrategyEntry.OptimizationTarget target = summary.getNextStageTargets().get(0);
        assertEquals("EnvelopePeriod", target.getName());
        assertEquals("20", target.getCurrentValue());
        assertEquals("5 … 40, Schritt 5", target.describeRange());
    }

    @Test
    public void aDifferentlyWrittenSameValueIsNotReportedAsAChange() {
        assertFalse(new MasterStrategyEntry.ParameterChange("StepMultiplier", "1.20", "1.2")
                .isChanged());
        assertTrue(new MasterStrategyEntry.ParameterChange("GridStep", "15", "23").isChanged());
    }

    @Test
    public void numericFormattingDoesNotCreateAnAdditionalDiffAgainstTheConfirmedMaster() {
        CustomProject project = new CustomProject("P", "EA", "AUDCAD", "M5");
        WorkflowTask producer = new WorkflowTask("Producer", WorkflowTask.TaskType.OPTIMIZER);
        producer.setTargetDatabank("producer");
        producer.setOptimizerTargetParameters(List.of("GridStep"));
        WorkflowTask consumer = new WorkflowTask("Consumer", WorkflowTask.TaskType.OPTIMIZER);
        consumer.setTargetDatabank("consumer");
        consumer.setOptimizerTargetParameters(List.of("NextTarget"));
        project.addTask(producer);
        project.addTask(consumer);

        List<EaParameter> confirmed = List.of(
                optimizable("GridStep", "15", "10", "1", "30"),
                parameter("FixedRisk", "1.0"),
                optimizable("NextTarget", "5", "1", "1", "10"));
        Pass backtest = new Pass();
        backtest.setPassNumber(3);
        backtest.setParameterValues(new java.util.LinkedHashMap<>(java.util.Map.of(
                "GridStep", "23")));
        CombinedPass selected = new CombinedPass(backtest, null, 70.0, 0.0, "test");

        GuidedOptimizationService.AdoptionPreview preview =
                GuidedOptimizationService.previewPassAdoption(
                        project, confirmed, null, selected, "producer");
        GuidedOptimizationService.AdoptionResult adoption =
                GuidedOptimizationService.adoptPassParameters(
                        project, confirmed, null, selected, "producer");
        List<EaParameter> effective = adoption.getParameters();
        effective.stream()
                .filter(parameter -> "FixedRisk".equals(parameter.getName()))
                .findFirst().orElseThrow().setValue("1");

        MasterStrategyLineageService.AdoptionSummary summary =
                MasterStrategyLineageService.summarize(
                        preview, adoption.getNextOptimizer(), effective, confirmed);

        assertTrue(summary.getAdditionalChanges().isEmpty());
    }

    @Test
    public void legacyMasterStrategyEntryWithMissingOrNullStructuredFieldsUsesFallback() {
        // Entries written before the structured fields existed, and entries whose lists
        // were serialised as explicit null, both have to stay readable.
        String missing = "{\"sequence\":1,\"profit\":1000.0,\"adoptedChanges\":[\"GridStep: 15 → 23\"]}";
        String explicitNull = "{\"sequence\":2,\"optimizedStageName\":null,"
                + "\"optimizedParameters\":null,\"additionalChanges\":null,\"nextStageTargets\":null}";

        for (String json : List.of(missing, explicitNull)) {
            MasterStrategyEntry entry = new Gson().fromJson(json, MasterStrategyEntry.class);
            assertEquals("", entry.getOptimizedStageName());
            assertTrue(entry.getOptimizedParameters().isEmpty());
            assertTrue(entry.getAdditionalChanges().isEmpty());
            assertTrue(entry.getNextStageTargets().isEmpty());
            assertNotNull(entry.getVerdict());
            // The window falls back to the old flat list, so nothing of the old entry is lost.
            assertNotNull(entry.getAdoptedChanges());
        }
    }

    @Test
    public void masterStrategyEntryCopyDeepCopiesNestedStructuredElements() {
        MasterStrategyEntry entry = measured(1000, 250);
        MasterStrategyEntry.ParameterChange optimized =
                new MasterStrategyEntry.ParameterChange("GridStep", "15", "23");
        MasterStrategyEntry.ParameterChange additional =
                new MasterStrategyEntry.ParameterChange("ADX", "25", "40");
        MasterStrategyEntry.OptimizationTarget target =
                new MasterStrategyEntry.OptimizationTarget("EnvelopePeriod", "20", "5", "5", "40");
        entry.setOptimizedParameters(List.of(optimized));
        entry.setAdditionalChanges(List.of(additional));
        entry.setNextStageTargets(List.of(target));

        MasterStrategyEntry copy = entry.copy();
        copy.getOptimizedParameters().get(0).setNewValue("99");
        copy.getAdditionalChanges().get(0).setNewValue("99");
        copy.getNextStageTargets().get(0).setCurrentValue("99");

        assertNotSame(optimized, copy.getOptimizedParameters().get(0));
        assertEquals("23", entry.getOptimizedParameters().get(0).getNewValue());
        assertEquals("40", entry.getAdditionalChanges().get(0).getNewValue());
        assertEquals("20", entry.getNextStageTargets().get(0).getCurrentValue());
    }

    @Test
    public void theStructuredChangesSurvivePersistence() {
        CustomProject project = new CustomProject("P", "EA", "AUDCAD", "M5");
        MasterStrategyEntry entry = measured(1000, 250);
        entry.setOptimizedStageName("Stufe 1");
        entry.setOptimizedParameters(List.of(
                new MasterStrategyEntry.ParameterChange("GridStep", "15", "23")));
        entry.setAdditionalChanges(List.of(
                new MasterStrategyEntry.ParameterChange("ADX", "25", "40")));
        entry.setNextStageTargets(List.of(
                new MasterStrategyEntry.OptimizationTarget("EnvelopePeriod", "20", "5", "5", "40")));
        MasterStrategyLineageService.append(project, entry);

        MasterStrategyEntry copy = project.copyMetadataForPersistence()
                .getMasterStrategyLineage().get(0);

        assertEquals("Stufe 1", copy.getOptimizedStageName());
        assertEquals("23", copy.getOptimizedParameters().get(0).getNewValue());
        assertEquals("ADX", copy.getAdditionalChanges().get(0).getName());
        assertEquals("5 … 40, Schritt 5", copy.getNextStageTargets().get(0).describeRange());
    }

    private static EaParameter optimizable(String name, String value,
                                           String start, String step, String end) {
        EaParameter parameter = parameter(name, value);
        parameter.setOptimizeStart(start);
        parameter.setOptimizeStep(step);
        parameter.setOptimizeEnd(end);
        return parameter;
    }

    private static final class BlockingClearProject extends CustomProject {
        private final CountDownLatch clearEntered = new CountDownLatch(1);
        private final CountDownLatch allowClear = new CountDownLatch(1);

        private BlockingClearProject() {
            super("P", "EA", "AUDCAD", "M5");
        }

        @Override
        public void clearProvenMaster() {
            clearEntered.countDown();
            try {
                if (!allowClear.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Timed out waiting to finish clear");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError(ex);
            }
            super.clearProvenMaster();
        }
    }

    private static BacktestConfig referenceConfig(String symbol, String period) {
        BacktestConfig config = new BacktestConfig();
        config.setExpert("EA");
        config.setSymbol(symbol);
        config.setPeriod(period);
        config.setModel(BacktestConfig.MODEL_OHLC_M1);
        config.setFromDate(LocalDate.parse(MasterStrategyLineageService.REFERENCE_FROM));
        config.setToDate(LocalDate.parse(MasterStrategyLineageService.REFERENCE_TO));
        config.setDeposit(MasterStrategyLineageService.REFERENCE_DEPOSIT);
        config.setCurrency(MasterStrategyLineageService.REFERENCE_CURRENCY);
        config.setLeverage(MasterStrategyLineageService.REFERENCE_LEVERAGE);
        return config;
    }

    @Test
    public void aProjectRecoversItsMasterOnlyFromTheConfirmedMeasurement() {
        CustomProject project = new CustomProject("Guided", "EA", "AUDCAD", "M5");
        String context = MasterStrategyLineageService.currentContextKey(project);

        MasterStrategyEntry weaker = measured(1000, 1000);
        weaker.setContextKey(context);
        weaker.setSetfileContent("GridStep=11\r\n");
        MasterStrategyLineageService.append(project, weaker);

        MasterStrategyEntry best = measured(5000, 1000);
        best.setContextKey(context);
        best.setSetfileContent("GridStep=23\r\nEnvelopePeriod=30\r\n");
        MasterStrategyLineageService.append(project, best);
        project.setConfirmedMasterSequence(best.getSequence());

        List<EaParameter> recovered = MasterStrategyLineageService
                .recoverProvenMasterFromLineage(project);

        assertEquals(2, recovered.size());
        assertEquals("GridStep", recovered.get(0).getName());
        assertEquals("23", recovered.get(0).getValue());
    }

    @Test
    public void anUnconfirmedHistoricalHighNeverBecomesTheComparisonAnchor() {
        CustomProject project = new CustomProject("P", "EA", "AUDCAD", "M5");
        MasterStrategyEntry confirmed = MasterStrategyLineageService.append(project, measured(1000, 250));
        confirm(project, confirmed); // ratio 4.0
        MasterStrategyEntry unconfirmedHigh = MasterStrategyLineageService.append(project, measured(2000, 250));
        assertEquals(MasterStrategyEntry.Verdict.BESSER, unconfirmedHigh.getVerdict());

        MasterStrategyEntry next = MasterStrategyLineageService.append(project, measured(1500, 250));

        assertEquals(confirmed.getSequence(), next.getComparedToSequence());
        assertEquals(MasterStrategyEntry.Verdict.BESSER, next.getVerdict());
    }

    @Test
    public void legacyMasterSequenceMigratesFromExactMeasurementSignature() {
        CustomProject project = new CustomProject("Legacy", "EA", "AUDCAD", "M5");
        String context = MasterStrategyLineageService.currentContextKey(project);
        List<EaParameter> basis = List.of(parameter("GridStep", "23"));
        MasterStrategyEntry measured = measured(1000, 250);
        measured.setContextKey(context);
        measured.setMeasurementSignature(MasterStrategyLineageService.measurementSignature(
                referenceConfig("AUDCAD", "M5"), basis));
        MasterStrategyLineageService.append(project, measured);
        project.setProvenMasterParameters(basis);
        project.setProvenMasterContextKey(context);
        project.setMasterSelectionRatio(99.0); // Signature, not a coincidental ratio, is decisive.

        assertEquals(measured.getSequence(), MasterStrategyLineageService
                .confirmedMasterEntry(project).orElseThrow().getSequence());
        assertEquals(measured.getSequence(), project.getConfirmedMasterSequence());
    }

    @Test
    public void legacyMasterSequenceMigratesOnlyFromAUniqueRatioAndContext() {
        CustomProject project = new CustomProject("Legacy", "EA", "AUDCAD", "M5");
        String context = MasterStrategyLineageService.currentContextKey(project);
        MasterStrategyEntry measured = measured(1000, 250);
        measured.setContextKey(context);
        MasterStrategyLineageService.append(project, measured);
        project.setProvenMasterParameters(List.of(parameter("GridStep", "23")));
        project.setProvenMasterContextKey(context);
        project.setMasterSelectionRatio(measured.getReturnToDrawdown());

        assertEquals(measured.getSequence(), MasterStrategyLineageService
                .confirmedMasterEntry(project).orElseThrow().getSequence());
        assertEquals(measured.getSequence(), project.getConfirmedMasterSequence());
    }

    @Test
    public void ambiguousLegacyRatioNeverFallsBackToTheBestUnconfirmedEntry() {
        CustomProject project = new CustomProject("Legacy", "EA", "AUDCAD", "M5");
        String context = MasterStrategyLineageService.currentContextKey(project);
        MasterStrategyEntry first = measured(1000, 250);
        first.setContextKey(context);
        MasterStrategyLineageService.append(project, first);
        MasterStrategyEntry second = measured(2000, 500); // same ratio, different basis
        second.setContextKey(context);
        MasterStrategyLineageService.append(project, second);
        project.setProvenMasterParameters(List.of(parameter("GridStep", "23")));
        project.setProvenMasterContextKey(context);
        project.setMasterSelectionRatio(4.0);

        assertTrue(MasterStrategyLineageService.confirmedMasterEntry(project).isEmpty());
        assertEquals(-1, project.getConfirmedMasterSequence());
        MasterStrategyEntry candidate = measured(3000, 500);
        candidate.setContextKey(context);
        MasterStrategyLineageService.append(project, candidate);
        assertEquals(MasterStrategyEntry.Verdict.UNBEKANNT, candidate.getVerdict());
        assertEquals(-1, candidate.getComparedToSequence());
    }

    @Test
    public void aHistoryWithoutUsablePresetsRecoversNothingInsteadOfGuessing() {
        CustomProject empty = new CustomProject("Guided", "EA", "AUDCAD", "M5");
        assertTrue(MasterStrategyLineageService.recoverProvenMasterFromLineage(empty).isEmpty());

        MasterStrategyEntry withoutPreset = measured(5000, 1000);
        withoutPreset.setContextKey(MasterStrategyLineageService.currentContextKey(empty));
        MasterStrategyLineageService.append(empty, withoutPreset);

        assertTrue(MasterStrategyLineageService.recoverProvenMasterFromLineage(empty).isEmpty());
    }

    private static EaParameter parameter(String name, String value) {
        EaParameter parameter = new EaParameter();
        parameter.setName(name);
        parameter.setValue(value);
        return parameter;
    }

    private static void confirm(CustomProject project, MasterStrategyEntry entry) {
        project.setProvenMasterParameters(List.of(
                parameter("ConfirmedMeasurement", Integer.toString(entry.getSequence()))));
        project.setProvenMasterContextKey(entry.contextKey());
        project.setMasterSelectionRatio(entry.getReturnToDrawdown());
        project.setConfirmedMasterSequence(entry.getSequence());
    }

    private static MasterStrategyEntry measured(double profit, double drawdownAbsolute) {
        MasterStrategyEntry entry = new MasterStrategyEntry();
        entry.setBacktestSucceeded(true);
        entry.setProfit(profit);
        entry.setMaxDrawdownAbsolute(drawdownAbsolute);
        entry.setMaxDrawdownPercent(drawdownAbsolute / 100.0);
        entry.setReturnToDrawdown(
                MasterStrategyLineageService.returnToDrawdown(profit, drawdownAbsolute));
        return entry;
    }
}
