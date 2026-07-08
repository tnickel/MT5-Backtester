package com.backtester.engine;

import java.time.LocalDate;

/**
 * Computes how MT5 splits an optimization date range into a backtest
 * (in-sample) and a forward (out-of-sample) window.
 *
 * <p><b>Warum eine eigene Klasse:</b> Die Sensitivitätsanalyse (Schritt 4)
 * misst die Parameter-Robustheit getrennt auf dem BT- und dem FW-Fenster.
 * Dafür muss sie MT5s internen Datums-Split <i>exakt</i> spiegeln — driftet
 * diese Logik, wird die FW-Sensitivität auf dem falschen Zeitfenster gemessen
 * und die gesamte Anti-Curve-Fitting-Aussage ist wertlos, ohne dass es
 * jemand merkt. Deshalb ist die Logik hier isoliert und durch
 * {@code ForwardSplitTest} abgesichert.
 *
 * <p>MT5 ForwardMode-Werte:
 * <ul>
 *   <li>0 = kein Forward (gesamter Zeitraum ist Backtest)</li>
 *   <li>1 = 1/2 (Forward = letzte Hälfte)</li>
 *   <li>2 = 1/3 (Forward = letztes Drittel)</li>
 *   <li>3 = 1/4 (Forward = letztes Viertel)</li>
 *   <li>4 = Custom (ForwardDate bestimmt den Beginn des Forward-Fensters)</li>
 * </ul>
 */
public final class ForwardSplit {

    private ForwardSplit() {
    }

    /**
     * End date of the backtest (in-sample) portion of the range.
     * Returns {@code to} when there is no forward window.
     */
    public static LocalDate computeBacktestEndDate(LocalDate from, LocalDate to, int forwardMode, LocalDate forwardDate) {
        if (forwardMode == 0 || from == null || to == null) {
            return to;
        }
        if (forwardMode == 4) {
            if (forwardDate != null && forwardDate.isAfter(from) && forwardDate.isBefore(to)) {
                return forwardDate.minusDays(1);
            }
            return to;
        }

        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(from, to);
        long backtestDays;
        switch (forwardMode) {
            case 1: backtestDays = totalDays / 2;       break; // forward = last 1/2
            case 2: backtestDays = (totalDays * 2) / 3; break; // forward = last 1/3
            case 3: backtestDays = (totalDays * 3) / 4; break; // forward = last 1/4
            default: return to;
        }
        if (backtestDays <= 0) return to;
        return from.plusDays(backtestDays);
    }

    /**
     * Start date of the forward (out-of-sample) window, or {@code null}
     * when the configuration produces no forward window.
     */
    public static LocalDate computeForwardStartDate(LocalDate from, LocalDate to, int forwardMode, LocalDate forwardDate) {
        if (forwardMode == 0 || from == null || to == null) return null;
        if (forwardMode == 4) {
            if (forwardDate != null && forwardDate.isAfter(from) && forwardDate.isBefore(to)) return forwardDate;
            return null;
        }

        LocalDate btEnd = computeBacktestEndDate(from, to, forwardMode, forwardDate);
        if (btEnd == null || !btEnd.isBefore(to)) return null;
        return btEnd.plusDays(1);
    }
}
