package com.backtester.ui.javafx;

/**
 * UI-only context for rendering EA parameter cells (e.g. resolve PERIOD_CURRENT
 * against the active chart timeframe).
 */
public final class EaParameterUiContext {

    private static volatile String chartPeriod = "";

    private EaParameterUiContext() {
    }

    public static void setChartPeriod(String period) {
        chartPeriod = period != null ? period.trim() : "";
    }

    public static String getChartPeriod() {
        return chartPeriod;
    }
}
