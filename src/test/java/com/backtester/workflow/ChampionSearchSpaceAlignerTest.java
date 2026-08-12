package com.backtester.workflow;

import com.backtester.config.EaParameter;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChampionSearchSpaceAlignerTest {

    @Test
    public void offGridChampionShiftsThePhaseSoItBecomesAPass() {
        EaParameter deviation = optimized("Inp_Envelopes_Deviation", "0.007", "0.005", "0.005", "0.030");

        ChampionSearchSpaceAligner.Adjustment adjustment =
                ChampionSearchSpaceAligner.align(deviation).orElseThrow();

        assertEquals(ChampionSearchSpaceAligner.Outcome.GRID_SHIFTED, adjustment.getOutcome());
        assertEquals("0.002", deviation.getOptimizeStart());
        assertEquals("0.005", deviation.getOptimizeStep());
        assertEquals("0.030", deviation.getOptimizeEnd());
        assertTrue(onGrid(deviation));
    }

    @Test
    public void championBelowTheBandExtendsTheStart() {
        EaParameter level = optimized("Inp_ADX_Max_Level", "20", "30", "2.5", "50");

        ChampionSearchSpaceAligner.Adjustment adjustment =
                ChampionSearchSpaceAligner.align(level).orElseThrow();

        assertEquals(ChampionSearchSpaceAligner.Outcome.RANGE_EXTENDED, adjustment.getOutcome());
        assertEquals("20.0", level.getOptimizeStart());
        assertEquals("50.0", level.getOptimizeEnd());
        assertTrue(onGrid(level));
    }

    @Test
    public void championAboveTheBandExtendsTheStop() {
        EaParameter level = optimized("Inp_ADX_Max_Level", "55", "30", "2.5", "50");

        ChampionSearchSpaceAligner.Adjustment adjustment =
                ChampionSearchSpaceAligner.align(level).orElseThrow();

        assertEquals(ChampionSearchSpaceAligner.Outcome.RANGE_EXTENDED, adjustment.getOutcome());
        assertEquals("30.0", level.getOptimizeStart());
        assertEquals("55.0", level.getOptimizeEnd());
        assertTrue(onGrid(level));
    }

    @Test
    public void championAlreadyOnTheGridIsLeftAlone() {
        EaParameter level = optimized("Inp_ADX_Max_Level", "35", "30", "2.5", "50");

        assertTrue(ChampionSearchSpaceAligner.align(level).isEmpty());
        assertEquals("30", level.getOptimizeStart());
        assertEquals("50", level.getOptimizeEnd());
    }

    @Test
    public void booleanBandsAndFixedRowsAreNotTouched() {
        EaParameter gate = optimized("Inp_Use_ADX_Filter", "true", "false", "1", "true");
        assertTrue(ChampionSearchSpaceAligner.align(gate).isEmpty());

        EaParameter fixed = optimized("Inp_Grid_Step", "573", "550", "25", "900");
        fixed.setOptimizeEnabled(false);
        assertTrue(ChampionSearchSpaceAligner.align(fixed).isEmpty());
        assertEquals("550", fixed.getOptimizeStart());
    }

    @Test
    public void timeframeBandsAreWidenedAlongTheEnumOrderNotTheNumericCode() {
        EaParameter timeframe = optimized("Inp_ADX_Timeframe", "5", "16385", "1", "16385");

        ChampionSearchSpaceAligner.Adjustment adjustment =
                ChampionSearchSpaceAligner.align(timeframe).orElseThrow();

        assertEquals(ChampionSearchSpaceAligner.Outcome.RANGE_EXTENDED, adjustment.getOutcome());
        assertEquals("5", timeframe.getOptimizeStart());
        assertEquals("16385", timeframe.getOptimizeEnd());
    }

    @Test
    public void periodCurrentChampionIsReportedAsUnreachableInsteadOfCorruptingTheBand() {
        EaParameter timeframe = optimized("TimeFrame_Envelopes", "0", "1", "1", "16385");

        ChampionSearchSpaceAligner.Adjustment adjustment =
                ChampionSearchSpaceAligner.align(timeframe).orElseThrow();

        assertEquals(ChampionSearchSpaceAligner.Outcome.SKIPPED_UNREACHABLE, adjustment.getOutcome());
        assertFalse(adjustment.isApplied());
        assertEquals("1", timeframe.getOptimizeStart());
        assertEquals("16385", timeframe.getOptimizeEnd());
    }

    @Test
    public void championFarOutsideTheBandIsReportedInsteadOfStretchingTheExperiment() {
        EaParameter gridStep = optimized("Inp_Grid_Step", "1", "550", "25", "900");

        ChampionSearchSpaceAligner.Adjustment adjustment =
                ChampionSearchSpaceAligner.align(gridStep).orElseThrow();

        assertEquals(ChampionSearchSpaceAligner.Outcome.SKIPPED_TOO_FAR, adjustment.getOutcome());
        assertFalse(adjustment.isApplied());
        assertEquals("550", gridStep.getOptimizeStart());
        assertEquals("900", gridStep.getOptimizeEnd());
    }

    @Test
    public void aBandCutSlightlyTooTightIsStillRepaired() {
        EaParameter level = optimized("Inp_ADX_Max_Level", "25", "30", "2.5", "50");

        ChampionSearchSpaceAligner.Adjustment adjustment =
                ChampionSearchSpaceAligner.align(level).orElseThrow();

        assertEquals(ChampionSearchSpaceAligner.Outcome.RANGE_EXTENDED, adjustment.getOutcome());
        assertEquals("25.0", level.getOptimizeStart());
        assertTrue(onGrid(level));
    }

    @Test
    public void listVariantReportsOnlyTheRowsThatNeededWork() {
        EaParameter offGrid = optimized("Inp_Envelopes_Period", "5", "3", "3", "15");
        EaParameter onGrid = optimized("Inp_Max_Grid_Levels", "8", "8", "1", "16");

        List<ChampionSearchSpaceAligner.Adjustment> adjustments =
                ChampionSearchSpaceAligner.align(List.of(offGrid, onGrid));

        assertEquals(1, adjustments.size());
        assertEquals("Inp_Envelopes_Period", adjustments.get(0).getParameterName());
        assertEquals("2", offGrid.getOptimizeStart());
        assertEquals("8", onGrid.getOptimizeStart());
    }

    private static EaParameter optimized(String name, String value,
                                         String start, String step, String end) {
        EaParameter parameter = new EaParameter(name, value);
        parameter.setOptimizeStart(start);
        parameter.setOptimizeStep(step);
        parameter.setOptimizeEnd(end);
        parameter.setOptimizeEnabled(true);
        return parameter;
    }

    /** The champion must be exactly {@code start + n * step} for some whole n within the band. */
    private static boolean onGrid(EaParameter parameter) {
        BigDecimal value = new BigDecimal(parameter.getValue());
        BigDecimal start = new BigDecimal(parameter.getOptimizeStart());
        BigDecimal step = new BigDecimal(parameter.getOptimizeStep());
        BigDecimal end = new BigDecimal(parameter.getOptimizeEnd());
        if (value.compareTo(start) < 0 || value.compareTo(end) > 0) return false;
        BigDecimal steps = value.subtract(start).divide(step, java.math.MathContext.DECIMAL64);
        return steps.stripTrailingZeros().scale() <= 0;
    }

    @Test
    public void optionalIsEmptyForRowsWithoutAUsableBand() {
        EaParameter noBand = new EaParameter("Inp_Something", "5");
        noBand.setOptimizeEnabled(true);
        Optional<ChampionSearchSpaceAligner.Adjustment> adjustment =
                ChampionSearchSpaceAligner.align(noBand);
        assertTrue(adjustment.isEmpty());
    }
}
