package com.backtester.report;

import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
        LocalDate split = resolveForwardStart(from, to, forwardMode, configuredForwardDate);
        for (CombinedPass pass : passes) {
            if (pass == null) continue;
            setRange(pass.getBacktestPass(), from, split != null ? split : to);
            setRange(pass.getForwardPass(), split != null ? split : from, to);
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
        if (forwardMode == 4) {
            return isInside(configuredForwardDate, from, to) ? configuredForwardDate : null;
        }

        long days = ChronoUnit.DAYS.between(from, to);
        LocalDate calculated = switch (forwardMode) {
            case 1 -> from.plusDays(days / 2);
            case 2 -> from.plusDays((days * 2) / 3);
            case 3 -> from.plusDays((days * 3) / 4);
            default -> null;
        };
        return isInside(calculated, from, to) ? calculated : null;
    }

    private static void applyPassLists(List<Pass> backtests, List<Pass> forwards,
                                       LocalDate from, LocalDate to,
                                       int forwardMode, LocalDate configuredForwardDate) {
        LocalDate split = resolveForwardStart(from, to, forwardMode, configuredForwardDate);
        if (backtests != null) {
            for (Pass pass : backtests) setRange(pass, from, split != null ? split : to);
        }
        if (forwards != null) {
            for (Pass pass : forwards) setRange(pass, split != null ? split : from, to);
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
