package com.backtester.ui.javafx;

import com.backtester.report.OptimizationResult.CombinedPass;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * A dialog that shows Score and Consistency distribution histograms,
 * statistical summaries, and recommends optimal filter thresholds.
 */
public class DistributionAnalysisDialog extends Stage {

    private final List<CombinedPass> allPasses;
    private final BiConsumer<Double, Double> applyFilterCallback; // (minScore, minConsistency)

    private double recommendedScore;
    private double recommendedConsistency;

    public DistributionAnalysisDialog(List<CombinedPass> allPasses,
                                      BiConsumer<Double, Double> applyFilterCallback) {
        this.allPasses = allPasses;
        this.applyFilterCallback = applyFilterCallback;

        setTitle("📊 Verteilungsanalyse – Score & Konsistenz");
        initModality(Modality.NONE);
        setMinWidth(1100);
        setMinHeight(820);

        // ── Compute statistics ──
        double[] scores = allPasses.stream().mapToDouble(CombinedPass::getScore).toArray();
        double[] consistencies = allPasses.stream().mapToDouble(CombinedPass::getConsistency).toArray();

        Stats scoreStats = computeStats(scores);
        Stats consistStats = computeStats(consistencies);

        recommendedScore = computeRecommendedScore(scores, scoreStats);
        recommendedConsistency = computeRecommendedConsistency(consistencies, consistStats);

        // ── Build UI ──
        VBox root = new VBox(16);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #0d0f17;");

        // Title
        Label title = new Label("📊 Verteilungsanalyse – Score & Konsistenz");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        title.setTextFill(Color.web("#b388ff"));

        Label subtitle = new Label(allPasses.size() + " Strategien analysiert");
        subtitle.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 12px;");

        // ── Charts Row ──
        HBox chartsRow = new HBox(20);
        chartsRow.setAlignment(Pos.TOP_CENTER);
        HBox.setHgrow(chartsRow, Priority.ALWAYS);

        VBox scoreChartBox = buildHistogramPanel(
                "SCORE-VERTEILUNG (0–100)", scores, scoreStats,
                recommendedScore, 0, 100,
                "#b388ff", "Score",
                val -> {
                    if (val < 30) return "#ff5252";
                    if (val < 60) return "#ffd740";
                    return "#00e676";
                });
        HBox.setHgrow(scoreChartBox, Priority.ALWAYS);

        VBox consistChartBox = buildHistogramPanel(
                "KONSISTENZ-VERTEILUNG (0–2)", consistencies, consistStats,
                recommendedConsistency, 0, 2,
                "#00e5ff", "Konsistenz",
                val -> {
                    if (val < 0.5) return "#ff5252";
                    if (val < 0.8) return "#ffd740";
                    return "#00e676";
                });
        HBox.setHgrow(consistChartBox, Priority.ALWAYS);

        chartsRow.getChildren().addAll(scoreChartBox, consistChartBox);

        // ── Statistics Panels ──
        HBox statsRow = new HBox(20);
        statsRow.setAlignment(Pos.TOP_CENTER);

        VBox scoreStatsBox = buildStatsPanel("SCORE-STATISTIK", scoreStats, scores,
                recommendedScore, "Score ≥", "#b388ff");
        HBox.setHgrow(scoreStatsBox, Priority.ALWAYS);

        VBox consistStatsBox = buildStatsPanel("KONSISTENZ-STATISTIK", consistStats, consistencies,
                recommendedConsistency, "Konsistenz ≥", "#00e5ff");
        HBox.setHgrow(consistStatsBox, Priority.ALWAYS);

        statsRow.getChildren().addAll(scoreStatsBox, consistStatsBox);

        // ── Explanations (TitledPanes) ──
        VBox explanationsBox = buildExplanationsSection();

        // ── Buttons ──
        HBox btnRow = buildButtonRow();

        ScrollPane scrollContent = new ScrollPane();
        VBox scrollInner = new VBox(16, chartsRow, statsRow, explanationsBox);
        scrollInner.setPadding(new Insets(0));
        scrollContent.setContent(scrollInner);
        scrollContent.setFitToWidth(true);
        scrollContent.setStyle("-fx-background: #0d0f17; -fx-border-color: transparent;");
        scrollContent.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scrollContent, Priority.ALWAYS);

        root.getChildren().addAll(title, subtitle, scrollContent, btnRow);

        Scene scene = new Scene(root, 1150, 850);
        setScene(scene);
    }

    // ── Histogram Panel ──────────────────────────────────────────────────────

    @FunctionalInterface
    private interface ColorMapper {
        String map(double binMidpoint);
    }

    private VBox buildHistogramPanel(String titleText, double[] values, Stats stats,
                                      double recommendedThreshold,
                                      double rangeMin, double rangeMax,
                                      String accentColor, String metricName,
                                      ColorMapper colorMapper) {
        VBox panel = new VBox(8);
        panel.setStyle("-fx-background-color: #171b26; -fx-padding: 12; -fx-background-radius: 8; " +
                "-fx-border-color: #2e3543; -fx-border-width: 1px; -fx-border-radius: 8;");

        Label panelTitle = new Label(titleText);
        panelTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        panelTitle.setTextFill(Color.web(accentColor));

        // Compute bins
        int binCount = Math.min(25, Math.max(10, values.length / 20));
        double dataMin = stats.min;
        double dataMax = stats.max;
        if (dataMin == dataMax) { dataMin -= 1; dataMax += 1; }
        double binWidth = (dataMax - dataMin) / binCount;

        int[] bins = new int[binCount];
        for (double v : values) {
            int idx = (int) ((v - dataMin) / binWidth);
            if (idx < 0) idx = 0;
            if (idx >= binCount) idx = binCount - 1;
            bins[idx]++;
        }

        // Build chart
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel(metricName);
        xAxis.setTickLabelFill(Color.web("#7e889a"));
        xAxis.setTickLabelFont(Font.font("Segoe UI", 9));
        xAxis.setTickLabelRotation(-45);

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Anzahl");
        yAxis.setTickLabelFill(Color.web("#7e889a"));
        yAxis.setMinorTickVisible(false);

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setCategoryGap(1);
        chart.setBarGap(0);
        chart.setPrefHeight(280);
        chart.setMinHeight(250);
        chart.setStyle("-fx-background-color: transparent;");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        String[] binLabels = new String[binCount];

        for (int i = 0; i < binCount; i++) {
            double lo = dataMin + i * binWidth;
            double hi = lo + binWidth;
            String label;
            if (rangeMax <= 2.0) {
                label = String.format(Locale.US, "%.2f", lo);
            } else {
                label = String.format(Locale.US, "%.0f", lo);
            }
            binLabels[i] = label;
            series.getData().add(new XYChart.Data<>(label, bins[i]));
        }
        chart.getData().add(series);

        // Capture for lambda (must be effectively final)
        final double fDataMin = dataMin;
        final double fBinWidth = binWidth;

        // Color the bars after rendering
        javafx.application.Platform.runLater(() -> {
            for (int i = 0; i < series.getData().size(); i++) {
                XYChart.Data<String, Number> d = series.getData().get(i);
                double binMid = fDataMin + i * fBinWidth + fBinWidth / 2.0;
                String color = colorMapper.map(binMid);

                // Highlight the recommended threshold bin
                boolean isRecommendedBin = (binMid >= recommendedThreshold - fBinWidth / 2.0)
                        && (binMid < recommendedThreshold + fBinWidth / 2.0);

                if (d.getNode() != null) {
                    if (isRecommendedBin) {
                        d.getNode().setStyle("-fx-bar-fill: #ffffff; -fx-border-color: " + accentColor +
                                "; -fx-border-width: 2px; -fx-effect: dropshadow(three-pass-box, " + accentColor + ", 8, 0, 0, 0);");
                    } else {
                        d.getNode().setStyle("-fx-bar-fill: " + color + ";");
                    }
                }
            }
        });

        // Recommendation label
        String recText;
        long countAbove = Arrays.stream(values).filter(v -> v >= recommendedThreshold).count();
        double pctAbove = values.length > 0 ? (countAbove * 100.0 / values.length) : 0;

        if (rangeMax <= 2.0) {
            recText = String.format(Locale.US,
                    "▶ Empfohlener Filter: %s ≥ %.2f  →  %d von %d Passes (%.0f%%)",
                    metricName, recommendedThreshold, countAbove, values.length, pctAbove);
        } else {
            recText = String.format(Locale.US,
                    "▶ Empfohlener Filter: %s ≥ %.1f  →  %d von %d Passes (%.0f%%)",
                    metricName, recommendedThreshold, countAbove, values.length, pctAbove);
        }

        Label recLabel = new Label(recText);
        recLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        recLabel.setTextFill(Color.web(accentColor));
        recLabel.setWrapText(true);

        Label legendLabel = new Label("█ Weißer Balken = empfohlener Schwellwert");
        legendLabel.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 10px;");

        panel.getChildren().addAll(panelTitle, chart, recLabel, legendLabel);
        return panel;
    }

    // ── Statistics Panel ─────────────────────────────────────────────────────

    private VBox buildStatsPanel(String titleText, Stats stats, double[] values,
                                  double recommended, String filterLabel, String accentColor) {
        VBox panel = new VBox(6);
        panel.setStyle("-fx-background-color: #171b26; -fx-padding: 12; -fx-background-radius: 8; " +
                "-fx-border-color: #2e3543; -fx-border-width: 1px; -fx-border-radius: 8;");

        Label panelTitle = new Label(titleText);
        panelTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        panelTitle.setTextFill(Color.web(accentColor));

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(4);

        boolean isConsistency = stats.max <= 2.5;
        String fmt = isConsistency ? "%.3f" : "%.1f";

        addStatRow(grid, 0, "Minimum:", String.format(Locale.US, fmt, stats.min));
        addStatRow(grid, 1, "Maximum:", String.format(Locale.US, fmt, stats.max));
        addStatRow(grid, 2, "Median:", String.format(Locale.US, fmt, stats.median));
        addStatRow(grid, 3, "Mittelwert:", String.format(Locale.US, fmt, stats.mean));
        addStatRow(grid, 4, "Std. Abweichung:", String.format(Locale.US, fmt, stats.stdDev));
        addStatRow(grid, 5, "25. Perzentil:", String.format(Locale.US, fmt, stats.p25));
        addStatRow(grid, 6, "75. Perzentil:", String.format(Locale.US, fmt, stats.p75));
        addStatRow(grid, 7, "Anzahl:", String.valueOf(values.length));

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #2e3543;");

        long countAbove = Arrays.stream(values).filter(v -> v >= recommended).count();
        String recFmt = isConsistency ? "%.2f" : "%.1f";

        Label recLabel = new Label(String.format(Locale.US,
                "Empfohlen: %s %s  →  %d von %d Passes verbleiben",
                filterLabel, String.format(Locale.US, recFmt, recommended), countAbove, values.length));
        recLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        recLabel.setTextFill(Color.web(accentColor));
        recLabel.setWrapText(true);

        panel.getChildren().addAll(panelTitle, grid, sep, recLabel);
        return panel;
    }

    private void addStatRow(GridPane grid, int row, String label, String value) {
        Label l = new Label(label);
        l.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 12px;");
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        l.setMinWidth(130);

        Label v = new Label(value);
        v.setStyle("-fx-text-fill: #e6e9f0; -fx-font-size: 12px;");
        v.setFont(Font.font("Segoe UI", 12));

        grid.add(l, 0, row);
        grid.add(v, 1, row);
    }

    // ── Explanations Section ─────────────────────────────────────────────────

    private VBox buildExplanationsSection() {
        VBox box = new VBox(6);

        Label sectionTitle = new Label("ℹ️ ERKLÄRUNGEN");
        sectionTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        sectionTitle.setTextFill(Color.web("#ffd740"));

        TitledPane scoreExpl = createExplanationPane(
                "Was ist der Score? (0–100)",
                "Der Score bewertet auf einen Blick, wie ausgewogen und robust eine Strategie " +
                "im Vergleich zu allen anderen Durchgängen dieser Optimierung abschneidet.\n\n" +
                "Berechnung:\n" +
                "Der Score ist ein gewichteter Durchschnitt aus 8 normalisierten Metriken:\n" +
                "• BT Profit (Standard: 25%)\n" +
                "• FW Profit (Standard: 35%) – Höchste Priorität\n" +
                "• Konsistenz FW/BT (Standard: 20%)\n" +
                "• FW Profit Factor (Standard: 10%)\n" +
                "• Drawdown-Strafe BT+FW (Standard: 5%)\n" +
                "• FW Trade Count (Standard: 5%)\n\n" +
                "Jede Metrik wird per Min/Max-Normalisierung auf den Bereich [0, 1] skaliert, " +
                "relativ zu allen Passes dieser Optimierung. Das bedeutet:\n" +
                "• Der beste Pass erhält in jeder Kategorie den Wert 1.0\n" +
                "• Der schlechteste Pass erhält 0.0\n" +
                "• Alle anderen liegen dazwischen\n\n" +
                "Wichtig: Der Score ist RELATIV – ein Score von 80 bedeutet nicht automatisch, " +
                "dass die Strategie absolut gut ist, sondern dass sie zu den besten 20% dieser " +
                "konkreten Optimierung gehört.\n\n" +
                "Zusätzlich gibt es eine automatische Soft-Penalty: Strategien mit sehr wenigen " +
                "Forward-Trades (unter der Hälfte des Medians) werden um bis zu 50% abgestraft, " +
                "da zu wenige Trades keine statistische Aussagekraft haben."
        );

        TitledPane consistExpl = createExplanationPane(
                "Was ist die Konsistenz? (0.0–2.0)",
                "Die Konsistenz misst, ob eine Strategie im Forward-Test (= unbekannte, zukünftige Daten) " +
                "ähnlich gut abschneidet wie im Backtest (= bekannte, historische Daten).\n\n" +
                "Formel: Konsistenz = Forward-Profit / Backtest-Profit\n\n" +
                "Interpretation:\n" +
                "• 0.0: Kein Forward-Profit – die Strategie funktioniert nur auf historischen Daten (Curve Fitting!)\n" +
                "• 0.3: Nur 30% des Backtest-Profits im Forward – stark überoptimiert\n" +
                "• 0.6: 60% des Profits reproduziert – akzeptabel, aber mit Vorsicht zu genießen\n" +
                "• 0.8–1.0: Sehr gute Reproduzierbarkeit – die Strategie ist robust\n" +
                "• 1.0: Perfekte Konsistenz – Forward = Backtest\n" +
                "• >1.0: Forward besser als Backtest – kann Zufall sein, ist aber positiv\n" +
                "• 2.0: Maximum (geclampt) – Forward hat doppelt so viel verdient\n\n" +
                "Warum ist die Konsistenz so wichtig?\n" +
                "Eine Strategie mit hohem Backtest-Profit aber niedriger Konsistenz (<0.5) ist " +
                "höchstwahrscheinlich überoptimiert ('Curve Fitted'). Sie hat Muster in den " +
                "historischen Daten gelernt, die in der Zukunft nicht mehr auftreten.\n\n" +
                "Richtwerte:\n" +
                "• < 0.5: Gefährlich – Überoptimierung wahrscheinlich\n" +
                "• 0.5–0.8: Akzeptabel – leichte Einbußen im Forward\n" +
                "• > 0.8: Gut bis hervorragend – robuste Strategie"
        );

        TitledPane filterExpl = createExplanationPane(
                "Wie wird der optimale Filter berechnet?",
                "Score-Filtervorschlag:\n" +
                "Der empfohlene Score-Schwellwert basiert auf einer statistischen Methode:\n" +
                "1. Berechne den Median und die Standardabweichung aller Scores\n" +
                "2. Vorschlag = max(Median − 0.5 × Standardabweichung, 25. Perzentil)\n" +
                "3. Ziel: Das untere Viertel bis Drittel herausfiltern, ohne zu viele gute Passes zu verlieren\n\n" +
                "Die Idee: Da der Score relativ ist, gibt es immer eine breite Streuung. " +
                "Die Methode findet einen Punkt, der offensichtlich schwache Strategien eliminiert, " +
                "aber die Mitte und Spitze bewahrt.\n\n" +
                "Konsistenz-Filtervorschlag:\n" +
                "Der Konsistenz-Filter basiert auf bewährten Erfahrungswerten:\n" +
                "• Standard-Schwelle: 0.60 (unter 0.6 gilt als 'deutlicher Leistungseinbruch')\n" +
                "• Wenn >80% aller Passes über 0.6 liegen: Schwelle wird auf 0.70 angehoben\n" +
                "• Wenn <30% über 0.6 liegen: Schwelle wird auf 0.40 gesenkt\n\n" +
                "Anpassung: Der Konsistenz-Filter passt sich an die Qualität der Optimierung an – " +
                "bei sehr guten Optimierungen wird er strenger, bei schwächeren milder.\n\n" +
                "Tipp: Die empfohlenen Werte sind ein Ausgangspunkt. Für Live-Trading empfehlen " +
                "wir zusätzlich mindestens 50 BT-Trades und einen Drawdown unter 20%."
        );

        box.getChildren().addAll(sectionTitle, scoreExpl, consistExpl, filterExpl);
        return box;
    }

    private TitledPane createExplanationPane(String title, String content) {
        Label text = new Label(content);
        text.setWrapText(true);
        text.setStyle("-fx-text-fill: #b4bac8; -fx-font-size: 12px; -fx-line-spacing: 2;");

        VBox inner = new VBox(text);
        inner.setPadding(new Insets(10));
        inner.setStyle("-fx-background-color: #171b26;");

        TitledPane pane = new TitledPane(title, inner);
        pane.setExpanded(false);
        pane.setAnimated(true);
        pane.setStyle(
                "-fx-text-fill: #e6e9f0; " +
                "-fx-font-size: 12px; " +
                "-fx-font-weight: bold;"
        );
        // Style the title pane header
        pane.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        return pane;
    }

    // ── Button Row ───────────────────────────────────────────────────────────

    private HBox buildButtonRow() {
        Button closeBtn = new Button("Schließen");
        closeBtn.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #b4bac8; -fx-border-color: #444; -fx-border-width: 1; -fx-font-size: 13px;");
        closeBtn.setOnAction(e -> close());

        Button applyBtn = new Button("✔ Empfohlene Filter anwenden");
        applyBtn.setStyle("-fx-background-color: #b388ff; -fx-text-fill: #0d0f17; -fx-font-weight: bold; -fx-font-size: 13px;");
        applyBtn.setOnAction(e -> {
            if (applyFilterCallback != null) {
                applyFilterCallback.accept(recommendedScore, recommendedConsistency);
            }
            close();
        });

        Label applyHint = new Label(String.format(Locale.US,
                "(Score ≥ %.1f, Konsistenz ≥ %.2f)", recommendedScore, recommendedConsistency));
        applyHint.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 11px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(10, closeBtn, spacer, applyHint, applyBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 0, 0, 0));
        return row;
    }

    // ── Statistics Computation ────────────────────────────────────────────────

    static class Stats {
        double min, max, mean, median, stdDev, p25, p75;
    }

    private Stats computeStats(double[] values) {
        Stats s = new Stats();
        if (values.length == 0) return s;

        double[] sorted = values.clone();
        Arrays.sort(sorted);

        s.min = sorted[0];
        s.max = sorted[sorted.length - 1];
        s.median = percentile(sorted, 50);
        s.p25 = percentile(sorted, 25);
        s.p75 = percentile(sorted, 75);

        double sum = 0;
        for (double v : sorted) sum += v;
        s.mean = sum / sorted.length;

        double sumSq = 0;
        for (double v : sorted) sumSq += (v - s.mean) * (v - s.mean);
        s.stdDev = Math.sqrt(sumSq / sorted.length);

        return s;
    }

    private double percentile(double[] sorted, double pct) {
        if (sorted.length == 0) return 0;
        if (sorted.length == 1) return sorted[0];
        double idx = (pct / 100.0) * (sorted.length - 1);
        int lo = (int) Math.floor(idx);
        int hi = (int) Math.ceil(idx);
        if (lo == hi) return sorted[lo];
        double frac = idx - lo;
        return sorted[lo] * (1 - frac) + sorted[hi] * frac;
    }

    // ── Filter Recommendation Algorithms ─────────────────────────────────────

    /**
     * Score recommendation: max(median - 0.5*stddev, p25)
     * This filters out the bottom quarter while preserving solid strategies.
     */
    private double computeRecommendedScore(double[] scores, Stats stats) {
        if (scores.length == 0) return 0;
        double candidate = stats.median - 0.5 * stats.stdDev;
        double result = Math.max(candidate, stats.p25);
        // Round to one decimal
        return Math.round(result * 10.0) / 10.0;
    }

    /**
     * Consistency recommendation: 0.60 baseline, adjusted by data quality.
     * If >80% are above 0.6 → raise to 0.70
     * If <30% are above 0.6 → lower to 0.40
     */
    private double computeRecommendedConsistency(double[] consistencies, Stats stats) {
        if (consistencies.length == 0) return 0.6;

        long above06 = Arrays.stream(consistencies).filter(v -> v >= 0.6).count();
        double pctAbove = (above06 * 100.0) / consistencies.length;

        if (pctAbove > 80) return 0.70;
        if (pctAbove < 30) return 0.40;
        return 0.60;
    }
}
