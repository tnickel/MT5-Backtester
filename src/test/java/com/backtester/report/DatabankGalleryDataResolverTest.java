package com.backtester.report;

import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class DatabankGalleryDataResolverTest {

    @Test
    public void combinesShortTermMetricsWithGalleryLongtermResult() {
        Pass shortBt = metricPass(7, 1200.0, 4.25);
        Pass shortFw = metricPass(7, 350.0, 6.75);
        CombinedPass shortTerm = new CombinedPass(shortBt, shortFw, 50.0, 1.0, "");
        shortTerm.setStrategyName("Strategy A");
        shortTerm.setSymbol("AUDCAD");
        CombinedPass gallery = pass(7, "Strategy A", 9999.0, 99.0, longterm(7, 8.57));

        DatabankGalleryDataResolver.Resolution result =
                DatabankGalleryDataResolver.resolve(List.of(gallery), List.of(shortTerm));

        assertTrue(result.isComplete());
        assertEquals(1, result.passes().size());
        CombinedPass resolved = result.passes().get(0);
        assertEquals(1200.0, resolved.getBtProfit(), 0.001);
        assertEquals(4.25, resolved.getBtDd(), 0.001);
        assertEquals(350.0, resolved.getFwProfit(), 0.001);
        assertEquals(6.75, resolved.getFwDd(), 0.001);
        assertEquals(8.57, resolved.getLtDd(), 0.001);
    }

    @Test
    public void reportsMissingShortTermStrategyInsteadOfUsingWrongMetrics() {
        CombinedPass gallery = pass(7, "Strategy A", 9999.0, 99.0, longterm(7, 8.57));

        DatabankGalleryDataResolver.Resolution result =
                DatabankGalleryDataResolver.resolve(List.of(gallery), List.of());

        assertFalse(result.isComplete());
        assertTrue(result.passes().isEmpty());
        assertEquals(7, result.missingShortTermStrategies().get(0).passNumber());
    }

    @Test
    public void refusesAmbiguousPassNumberFallback() {
        CombinedPass gallery = pass(7, "Renamed", 0.0, 0.0, longterm(7, 8.57));
        CombinedPass first = pass(7, "Strategy A", 10.0, 1.0, null);
        CombinedPass second = pass(7, "Strategy B", 20.0, 2.0, null);

        DatabankGalleryDataResolver.Resolution result =
                DatabankGalleryDataResolver.resolve(List.of(gallery), List.of(first, second));

        assertFalse(result.isComplete());
        assertTrue(result.passes().isEmpty());
    }

    private static CombinedPass pass(int number, String name, double profit, double drawdown, Pass longterm) {
        Pass bt = metricPass(number, profit, drawdown);
        CombinedPass combined = new CombinedPass(bt, null, longterm, 50.0, 1.0, "");
        combined.setStrategyName(name);
        combined.setSymbol("AUDCAD");
        return combined;
    }

    private static Pass metricPass(int number, double profit, double drawdown) {
        Pass pass = new Pass();
        pass.setPassNumber(number);
        pass.setProfit(profit);
        pass.setDrawdownPercent(drawdown);
        return pass;
    }

    private static Pass longterm(int number, double drawdown) {
        Pass pass = new Pass();
        pass.setPassNumber(number);
        pass.setDrawdownPercent(drawdown);
        return pass;
    }
}
