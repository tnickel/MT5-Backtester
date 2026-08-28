package com.backtester.ui.javafx;

/**
 * UI-only context for rendering EA parameter cells (e.g. resolve PERIOD_CURRENT
 * against the active chart timeframe).
 */
public final class EaParameterUiContext {

    /**
     * TableView#properties key under which a view can pin its own current chart
     * period. When present it wins over the global value below, so tables in
     * different tabs no longer cross-talk.
     */
    public static final String CHART_PERIOD_TABLE_KEY = "ea.chartPeriod";

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
