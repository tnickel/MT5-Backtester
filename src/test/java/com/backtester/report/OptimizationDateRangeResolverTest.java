package com.backtester.report;

import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class OptimizationDateRangeResolverTest {

    @Test
    public void appliesHalfSplitToBacktestAndForwardPasses() {
        Pass bt = new Pass();
        bt.setPassNumber(1);
        Pass fw = new Pass();
        fw.setPassNumber(1);
        CombinedPass combined = new CombinedPass(bt, fw, 0.0, 0.0, "");

        OptimizationDateRangeResolver.apply(List.of(combined),
                LocalDate.of(2025, 7, 28), LocalDate.of(2026, 7, 28), 1, null);

        assertEquals("2025-07-28", bt.getFromDate());
        assertEquals("2026-01-26", bt.getToDate());
        assertEquals("2026-01-27", fw.getFromDate());
        assertEquals("2026-07-28", fw.getToDate());
    }

    @Test
    public void customModeUsesConfiguredSplit() {
        OptimizationResult result = new OptimizationResult();
        Pass bt = new Pass();
        Pass fw = new Pass();
        result.setPasses(List.of(bt));
        result.setForwardPasses(List.of(fw));

        OptimizationDateRangeResolver.apply(result,
                LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1),
                4, LocalDate.of(2024, 10, 1));

        assertEquals("2024-09-30", bt.getToDate());
        assertEquals("2024-10-01", fw.getFromDate());
    }

    @Test
    public void appliesRetesterRangeToPersistedLongtermPass() {
        Pass bt = new Pass();
        Pass lt = new Pass();
        CombinedPass combined = new CombinedPass(bt, null, lt, 0.0, 0.0, "");

        OptimizationDateRangeResolver.applyLongterm(List.of(combined),
                LocalDate.of(2022, 8, 1), LocalDate.of(2026, 8, 1));

        assertEquals("2022-08-01", lt.getFromDate());
        assertEquals("2026-08-01", lt.getToDate());
    }
}
