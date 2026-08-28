package com.backtester.report;

import com.backtester.engine.ForwardSplit;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;

import java.time.LocalDate;
import java.util.List;

/** Assigns the real IS/OOS ranges because MT5 uses the full range in both report titles. */
public final class OptimizationDateRangeResolver {

    private OptimizationDateRangeResolver() {
    }

    public static void apply(OptimizationResult result,
                             LocalDate from, LocalDate to,
                             int forwardMode, LocalDate configuredForwardDate) {
        if (result == null) return;
        applyPassLists(result.getPasses(), result.getForwardPasses(),
                from, to, forwardMode, configuredForwardDate);
    }

    public static void apply(List<CombinedPass> passes,
                             LocalDate from, LocalDate to,
                             int forwardMode, LocalDate configuredForwardDate) {
        if (passes == null) return;
        LocalDate forwardStart = resolveForwardStart(from, to, forwardMode, configuredForwardDate);
        LocalDate backtestEnd = resolveBacktestEnd(from, to, forwardMode, configuredForwardDate);
        for (CombinedPass pass : passes) {
            if (pass == null) continue;
            setRange(pass.getBacktestPass(), from, backtestEnd);
            setRange(pass.getForwardPass(), forwardStart != null ? forwardStart : from, to);
        }
    }

    /** Supplies the configured retest range to legacy persisted long-term passes. */
    public static void applyLongterm(List<CombinedPass> passes, LocalDate from, LocalDate to) {
        if (passes == null) return;
        for (CombinedPass pass : passes) {
            if (pass != null) setRange(pass.getLongtermPass(), from, to);
        }
    }

    static LocalDate resolveForwardStart(LocalDate from, LocalDate to,
                                         int forwardMode, LocalDate configuredForwardDate) {
        if (forwardMode <= 0 || from == null || to == null || !from.isBefore(to)) return null;
        LocalDate calculated = ForwardSplit.computeForwardStartDate(
                from, to, forwardMode, configuredForwardDate);
        return isInside(calculated, from, to) ? calculated : null;
    }

    private static LocalDate resolveBacktestEnd(LocalDate from, LocalDate to,
                                                int forwardMode, LocalDate configuredForwardDate) {
        if (from == null || to == null || !from.isBefore(to)) return to;
        LocalDate calculated = ForwardSplit.computeBacktestEndDate(
                from, to, forwardMode, configuredForwardDate);
        return calculated != null ? calculated : to;
    }

    private static void applyPassLists(List<Pass> backtests, List<Pass> forwards,
                                       LocalDate from, LocalDate to,
                                       int forwardMode, LocalDate configuredForwardDate) {
        LocalDate forwardStart = resolveForwardStart(from, to, forwardMode, configuredForwardDate);
        LocalDate backtestEnd = resolveBacktestEnd(from, to, forwardMode, configuredForwardDate);
        if (backtests != null) {
            for (Pass pass : backtests) setRange(pass, from, backtestEnd);
        }
        if (forwards != null) {
            for (Pass pass : forwards) setRange(pass, forwardStart != null ? forwardStart : from, to);
        }
    }

    private static void setRange(Pass pass, LocalDate from, LocalDate to) {
        if (pass == null) return;
        pass.setFromDate(from != null ? from.toString() : "");
        pass.setToDate(to != null ? to.toString() : "");
    }

    private static boolean isInside(LocalDate value, LocalDate from, LocalDate to) {
        return value != null && value.isAfter(from) && value.isBefore(to);
    }
}
