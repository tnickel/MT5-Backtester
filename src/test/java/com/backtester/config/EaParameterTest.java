package com.backtester.config;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class EaParameterTest {

    @Test
    public void testDefaultState() {
        EaParameter param = new EaParameter();
        assertNull(param.getName());
        assertNull(param.getValue());
        assertNull(param.getDefaultValue());
        assertNull(param.getSection());
        assertEquals("", param.getOptimizeStart());
        assertEquals("", param.getOptimizeStep());
        assertEquals("", param.getOptimizeEnd());
        assertFalse(param.isOptimizeEnabled());
        assertFalse(param.isStringType());
        assertEquals("", param.getRawLine());
    }

    @Test
    public void testConstructorAndGettersSetters() {
        EaParameter param = new EaParameter("InpADRPeriod", "14");
        assertEquals("InpADRPeriod", param.getName());
        assertEquals("14", param.getValue());
        assertEquals("14", param.getDefaultValue());

        param.setOptimizeStart("10");
        param.setOptimizeStep("2");
        param.setOptimizeEnd("20");
        param.setOptimizeEnabled(true);
        param.setSection("Indicator settings");
        param.setStringType(false);
        param.setRawLine("InpADRPeriod=14||10||2||20||Y");

        assertEquals("10", param.getOptimizeStart());
        assertEquals("2", param.getOptimizeStep());
        assertEquals("20", param.getOptimizeEnd());
        assertTrue(param.isOptimizeEnabled());
        assertEquals("Indicator settings", param.getSection());
        assertFalse(param.isStringType());
        assertEquals("InpADRPeriod=14||10||2||20||Y", param.getRawLine());
    }

    @Test
    public void testIsModified() {
        EaParameter param = new EaParameter("InpGridStep", "20");
        assertFalse(param.isModified());

        param.setValue("25");
        assertTrue(param.isModified());

        param.resetToDefault();
        assertFalse(param.isModified());
        assertEquals("20", param.getValue());
    }

    @Test
    public void testToSetFileLine() {
        EaParameter p1 = new EaParameter("InpGridStep", "20");
        p1.setOptimizeStart("10");
        p1.setOptimizeStep("5");
        p1.setOptimizeEnd("50");
        p1.setOptimizeEnabled(true);
        assertEquals("InpGridStep=20||10||5||50||Y", p1.toSetFileLine());

        p1.setOptimizeEnabled(false);
        assertEquals("InpGridStep=20||10||5||50||N", p1.toSetFileLine());

        EaParameter p2 = new EaParameter("InpEAComment", "CC ADR EA");
        p2.setStringType(true);
        assertEquals("InpEAComment=CC ADR EA", p2.toSetFileLine());
    }

    @Test
    public void testToggleOptimizeEnabled() {
        EaParameter param = new EaParameter("InpStochK", "5");
        assertFalse(param.isOptimizeEnabled());

        param.setOptimizeEnabled(true);
        assertTrue(param.isOptimizeEnabled());

        param.setOptimizeEnabled(false);
        assertFalse(param.isOptimizeEnabled());
    }

    @Test
    public void testSectionHeaderDetectionAndFormatting() {
        EaParameter sectionParam = new EaParameter();
        sectionParam.setSectionHeader(true);
        sectionParam.setSection("MONEY MANAGE");
        assertTrue(sectionParam.isSectionHeader());
        assertEquals("📁  ---- MONEY MANAGE ----", sectionParam.getFormattedSectionTitle());

        EaParameter stringHeader = new EaParameter("Inp_Header_1", "--- GRID MODE ----");
        assertTrue(stringHeader.isSectionHeader());
        assertEquals("📁  ---- GRID MODE ----", stringHeader.getFormattedSectionTitle());

        EaParameter regularParam = new EaParameter("Inp_Initial_Lot", "0.01");
        assertFalse(regularParam.isSectionHeader());
    }

    @Test
    public void timeframeDisplayUsesMt5Labels() {
        assertTrue(EaParameter.isTimeframeParameterName("Inp_ATR_Timeframe"));
        assertTrue(EaParameter.isTimeframeParameterName("TimeFrame_Envelopes"));
        assertFalse(EaParameter.isTimeframeParameterName("Inp_ATR_Period"));

        assertEquals("Chart", EaParameter.toTimeframeDisplay("0"));
        assertEquals("Chart/M5", EaParameter.toTimeframeDisplay("0", "M5"));
        assertEquals("Chart/M5", EaParameter.toTimeframeDisplay("current", "M5"));
        assertEquals("M1", EaParameter.toTimeframeDisplay("1"));
        assertEquals("M15", EaParameter.toTimeframeDisplay("15"));
        assertEquals("H1", EaParameter.toTimeframeDisplay("16385"));
        assertEquals("H4", EaParameter.toTimeframeDisplay("16388"));
        assertEquals("MN1", EaParameter.toTimeframeDisplay("49153"));
        assertEquals("M5", EaParameter.toTimeframeDisplay("PERIOD_M5"));
        assertEquals("H1", EaParameter.toTimeframeDisplay("h1"));

        assertEquals("0", EaParameter.fromTimeframeDisplay("current"));
        assertEquals("0", EaParameter.fromTimeframeDisplay("Chart/M5"));
        assertEquals("0", EaParameter.fromTimeframeDisplay("Chart"));
        assertEquals("15", EaParameter.fromTimeframeDisplay("M15"));
        assertEquals("16385", EaParameter.fromTimeframeDisplay("H1"));
        assertEquals("16388", EaParameter.fromTimeframeDisplay("PERIOD_H4"));
        assertEquals("5", EaParameter.fromTimeframeDisplay("5"));

        assertEquals("Chart/M5", EaParameter.timeframeDisplayOptions("M5").get(0));
        assertTrue(EaParameter.timeframeDisplayOptions("M5").contains("H1"));
    }

    @Test
    public void officialMql5TimeframeAndAppliedPriceCodesAreUsed() {
        // ENUM_TIMEFRAMES: minute periods carry their minute count, hour periods are
        // 0x4000 + hours, week/month use their own high bits.
        Map<String, Integer> hourPeriods = Map.of(
                "PERIOD_H1", 1, "PERIOD_H2", 2, "PERIOD_H3", 3, "PERIOD_H4", 4,
                "PERIOD_H6", 6, "PERIOD_H8", 8, "PERIOD_H12", 12, "PERIOD_D1", 24);
        for (Map.Entry<String, Integer> entry : hourPeriods.entrySet()) {
            String expected = String.valueOf(0x4000 + entry.getValue());
            assertEquals(entry.getKey(), expected, EaParameter.normalizeMql5Value(entry.getKey()));
            assertEquals(entry.getKey(), expected, EaParameter.fromTimeframeDisplay(entry.getKey()));
        }
        assertEquals("30", EaParameter.normalizeMql5Value("PERIOD_M30"));
        assertEquals("32769", EaParameter.normalizeMql5Value("PERIOD_W1"));
        assertEquals("49153", EaParameter.normalizeMql5Value("PERIOD_MN1"));

        // ENUM_APPLIED_PRICE starts at 1, so every member maps to a distinct code 1..7.
        List<String> appliedPrices = List.of("PRICE_CLOSE", "PRICE_OPEN", "PRICE_HIGH",
                "PRICE_LOW", "PRICE_MEDIAN", "PRICE_TYPICAL", "PRICE_WEIGHTED");
        for (int i = 0; i < appliedPrices.size(); i++) {
            assertEquals(appliedPrices.get(i), String.valueOf(i + 1),
                    EaParameter.normalizeMql5Value(appliedPrices.get(i)));
        }
    }

    @Test
    public void requireValidOptimizeStepsRejectsZeroStepWhenOptEnabled() {
        EaParameter method = new EaParameter("Envelopes_Method", "0");
        method.setOptimizeStart("0");
        method.setOptimizeStep("0");
        method.setOptimizeEnd("3");
        method.setOptimizeEnabled(true);

        EaParameter ok = new EaParameter("Inp_Envelopes_Period", "5");
        ok.setOptimizeStart("5");
        ok.setOptimizeStep("1");
        ok.setOptimizeEnd("20");
        ok.setOptimizeEnabled(true);

        assertTrue(method.hasInvalidOptimizeStep());
        assertFalse(ok.hasInvalidOptimizeStep());
        assertEquals(List.of("Envelopes_Method"), EaParameter.findInvalidOptimizeSteps(List.of(method, ok)));

        try {
            EaParameter.requireValidOptimizeSteps(List.of(method, ok));
            fail("expected IllegalStateException");
        } catch (IllegalStateException ex) {
            assertTrue(ex.getMessage().contains("Envelopes_Method"));
            assertTrue(ex.getMessage().contains("Schritt"));
        }

        method.setOptimizeEnabled(false);
        EaParameter.requireValidOptimizeSteps(List.of(method, ok));
    }

    @Test
    public void normalizeBooleanOptimizeBandFixesZeroStep() {
        EaParameter bad = new EaParameter("Inp_Use_Session_Filter", "false");
        bad.setOptimizeStart("false");
        bad.setOptimizeStep("0");
        bad.setOptimizeEnd("true");
        assertTrue(EaParameter.normalizeBooleanOptimizeBand(bad));
        assertEquals("false", bad.getOptimizeStart());
        assertEquals("1", bad.getOptimizeStep());
        assertEquals("true", bad.getOptimizeEnd());

        EaParameter alreadyOk = new EaParameter("Inp_Use_ADX_Filter", "true");
        alreadyOk.setOptimizeStart("false");
        alreadyOk.setOptimizeStep("1");
        alreadyOk.setOptimizeEnd("true");
        assertFalse(EaParameter.normalizeBooleanOptimizeBand(alreadyOk));

        EaParameter numeric = new EaParameter("Inp_ADX_Period", "14");
        numeric.setOptimizeStart("9");
        numeric.setOptimizeStep("0");
        numeric.setOptimizeEnd("31");
        assertFalse(EaParameter.normalizeBooleanOptimizeBand(numeric));
        assertEquals("0", numeric.getOptimizeStep());
    }

    @Test
    public void timeframeSetFileLineUsesNumericEnumCodesNeverCurrentLabel() {
        EaParameter adx = new EaParameter("Inp_ADX_Timeframe", "current");
        adx.setOptimizeStart("Chart/M5");
        adx.setOptimizeStep("0");
        adx.setOptimizeEnd("H1");
        adx.setOptimizeEnabled(true);

        // Opt=Y: PERIOD_CURRENT start is coerced to M1 (1), step blank/0 → 1.
        assertEquals("Inp_ADX_Timeframe=0||1||1||16385||Y", adx.toSetFileLine());

        EaParameter atr = new EaParameter("Inp_ATR_Timeframe", "M15");
        atr.setOptimizeStart("0");
        atr.setOptimizeStep("1");
        atr.setOptimizeEnd("16385");
        atr.setOptimizeEnabled(false);
        // Opt=N may keep Start=0 (current) as a fixed-band edge.
        assertEquals("Inp_ATR_Timeframe=15||0||1||16385||N", atr.toSetFileLine());

        assertTrue(EaParameter.sanitizeTimeframeFieldsForSetFile(adx));
        assertEquals("0", adx.getValue());
        assertEquals("1", adx.getOptimizeStart());
        assertEquals("1", adx.getOptimizeStep());
        assertEquals("16385", adx.getOptimizeEnd());
    }

    @Test
    public void normalizeTimeframeOptimizeBandFixesLegacyMn1StopAndZeroStep() {
        EaParameter bad = new EaParameter("Inp_ATR_Timeframe", "0");
        bad.setOptimizeStart("0");
        bad.setOptimizeStep("0");
        bad.setOptimizeEnd("49153");
        assertTrue(EaParameter.normalizeTimeframeOptimizeBand(bad));
        assertEquals("1", bad.getOptimizeStart());
        assertEquals("1", bad.getOptimizeStep());
        assertEquals("16385", bad.getOptimizeEnd());

        EaParameter alreadyOk = new EaParameter("TimeFrame_Envelopes", "1");
        alreadyOk.setOptimizeStart("1");
        alreadyOk.setOptimizeStep("1");
        alreadyOk.setOptimizeEnd("16385");
        assertFalse(EaParameter.normalizeTimeframeOptimizeBand(alreadyOk));

        EaParameter fromCurrent = new EaParameter("TimeFrame_Envelopes", "1");
        fromCurrent.setOptimizeStart("0");
        fromCurrent.setOptimizeStep("1");
        fromCurrent.setOptimizeEnd("16385");
        assertTrue(EaParameter.normalizeTimeframeOptimizeBand(fromCurrent));
        assertEquals("1", fromCurrent.getOptimizeStart());

        EaParameter keepM15Stop = new EaParameter("Inp_ADX_Timeframe", "0");
        keepM15Stop.setOptimizeStart("0");
        keepM15Stop.setOptimizeStep("0");
        keepM15Stop.setOptimizeEnd("15");
        assertTrue(EaParameter.normalizeTimeframeOptimizeBand(keepM15Stop));
        assertEquals("1", keepM15Stop.getOptimizeStart());
        assertEquals("1", keepM15Stop.getOptimizeStep());
        assertEquals("15", keepM15Stop.getOptimizeEnd());

        EaParameter nonTf = new EaParameter("Inp_ATR_Period", "14");
        nonTf.setOptimizeStart("10");
        nonTf.setOptimizeStep("0");
        nonTf.setOptimizeEnd("20");
        assertFalse(EaParameter.normalizeTimeframeOptimizeBand(nonTf));
        assertEquals("0", nonTf.getOptimizeStep());
    }

    @Test
    public void testSearchSpaceValidationDetectsOutOfRangeTimeframeAndNumericErrors() {
        // 1. Timeframe out of range (user screenshot: Inp_ATR_Timeframe = 16408 (D1) with search space 1..16385 (M1..H1))
        EaParameter tfOutOfRange = new EaParameter("Inp_ATR_Timeframe", "16408");
        tfOutOfRange.setOptimizeStart("1");
        tfOutOfRange.setOptimizeStep("1");
        tfOutOfRange.setOptimizeEnd("16385");
        tfOutOfRange.setOptimizeEnabled(true);
        assertTrue(tfOutOfRange.hasInvalidSearchSpace());
        assertNotNull(tfOutOfRange.getSearchSpaceValidationError(null));
        assertTrue(tfOutOfRange.getSearchSpaceValidationError(null).contains("außerhalb des Suchraums"));

        // Valid timeframe
        EaParameter tfValid = new EaParameter("Inp_ATR_Timeframe", "16385");
        tfValid.setOptimizeStart("1");
        tfValid.setOptimizeStep("1");
        tfValid.setOptimizeEnd("16385");
        tfValid.setOptimizeEnabled(true);
        assertFalse(tfValid.hasInvalidSearchSpace());
        assertNull(tfValid.getSearchSpaceValidationError(null));

        // 2. Numeric stop < start
        EaParameter numBadStop = new EaParameter("Inp_Lot_Multiplier", "1.5");
        numBadStop.setOptimizeStart("2.0");
        numBadStop.setOptimizeStep("0.1");
        numBadStop.setOptimizeEnd("1.0");
        numBadStop.setOptimizeEnabled(true);
        assertTrue(numBadStop.hasInvalidSearchSpace());
        assertTrue(numBadStop.getSearchSpaceValidationError(null).contains("kleiner als Start"));

        // 3. Numeric value outside range
        EaParameter numOutOfRange = new EaParameter("Inp_Lot_Multiplier", "0.5");
        numOutOfRange.setOptimizeStart("1.0");
        numOutOfRange.setOptimizeStep("0.1");
        numOutOfRange.setOptimizeEnd("2.0");
        numOutOfRange.setOptimizeEnabled(true);
        assertTrue(numOutOfRange.hasInvalidSearchSpace());
        assertTrue(numOutOfRange.getSearchSpaceValidationError(null).contains("außerhalb des Suchraums"));

        // 4. Invalid step (step = 0)
        EaParameter badStep = new EaParameter("Inp_Lot_Multiplier", "1.5");
        badStep.setOptimizeStart("1.0");
        badStep.setOptimizeStep("0");
        badStep.setOptimizeEnd("2.0");
        badStep.setOptimizeEnabled(true);
        assertTrue(badStep.hasInvalidSearchSpace());
        assertTrue(badStep.getSearchSpaceValidationError(null).contains("Schritt"));
    }

    @Test
    public void applyOptimizeFlagsEnablesOnlyNamedTargets() {
        EaParameter grid = new EaParameter("Inp_Grid_Step", "550");
        grid.setOptimizeEnabled(false);
        EaParameter risk = new EaParameter("Inp_Max_Grid_Levels", "8");
        risk.setOptimizeEnabled(true);
        EaParameter header = new EaParameter("; Section", "");
        header.setSectionHeader(true);

        List<EaParameter> synced = EaParameter.applyOptimizeFlags(
                List.of(grid, risk, header), List.of("Inp_Grid_Step"));

        assertTrue(synced.get(0).isOptimizeEnabled());
        assertFalse(synced.get(1).isOptimizeEnabled());
        assertFalse(synced.get(2).isOptimizeEnabled());
        assertFalse(grid.isOptimizeEnabled());
    }
}
