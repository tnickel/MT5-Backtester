package com.backtester.ui.javafx;

import com.backtester.report.OptimizationResult;
import com.backtester.report.OptimizationResult.Pass;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * A dialog showing statistics and visualizations of an optimization run.
 * Contains two charts:
 * 1. Profit vs. Trade count (Scatter plot)
 * 2. Profit distribution (Spike chart with thin lines)
 */
public class OptimizationStatsDialog extends Stage {

    public OptimizationStatsDialog(OptimizationResult optResult) {
        if (optResult == null) {
            return;
        }

        setTitle("📊 Optimierungs-Statistiken & Gewinnverteilung");
        initModality(Modality.NONE);
        setMinWidth(1150);
        setMinHeight(750);

        List<Pass> passes = optResult.getPasses();

        // --- Compute Summary Stats ---
        int totalPasses = passes.size();
        double maxProfit = passes.stream().mapToDouble(Pass::getProfit).max().orElse(0.0);
        double minProfit = passes.stream().mapToDouble(Pass::getProfit).min().orElse(0.0);
        double avgProfit = passes.stream().mapToDouble(Pass::getProfit).average().orElse(0.0);
        int maxTrades = passes.stream().mapToInt(Pass::getTotalTrades).max().orElse(0);
        int minTrades = passes.stream().mapToInt(Pass::getTotalTrades).min().orElse(0);
        double avgTrades = passes.stream().mapToDouble(Pass::getTotalTrades).average().orElse(0.0);

        // --- Downsample passes for performance ---
        List<Pass> sampledPasses;
        if (passes.size() > 1000) {
            sampledPasses = new ArrayList<>();
            double step = (double) passes.size() / 1000.0;
            for (int i = 0; i < 1000; i++) {
                int index = (int) (i * step);
                if (index < passes.size()) {
                    sampledPasses.add(passes.get(index));
                }
            }
        } else {
            sampledPasses = passes;
        }

        // --- Title and Info Header ---
        VBox root = new VBox(20);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #0d0f17;");

        Label title = new Label("📊 Optimierungs-Statistiken & Gewinnverteilung");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        title.setTextFill(Color.web("#00e5ff"));

        // Meta grid
        GridPane metaGrid = new GridPane();
        metaGrid.setHgap(25);
        metaGrid.setVgap(8);
        metaGrid.setStyle("-fx-background-color: #171b26; -fx-padding: 15; -fx-background-radius: 8; -fx-border-color: #2e3543; -fx-border-width: 1px; -fx-border-radius: 8;");

        String eaName = optResult.getExpert() != null ? optResult.getExpert().substring(optResult.getExpert().lastIndexOf("\\") + 1) : "-";
        
        addMetaRow(metaGrid, 0, 0, "Expert Advisor:", eaName, "#e6e9f0");
        addMetaRow(metaGrid, 0, 1, "Symbol / TF:", optResult.getSymbol() + " (" + optResult.getPeriod() + ")", "#e6e9f0");
        addMetaRow(metaGrid, 0, 2, "Zeitraum:", optResult.getFromDate() + " bis " + optResult.getToDate(), "#e6e9f0");
        
        addMetaRow(metaGrid, 1, 0, "Anzahl Durchgänge (Passes):", String.valueOf(totalPasses), "#00e5ff");
        addMetaRow(metaGrid, 1, 1, "Profit-Bereich:", String.format(Locale.US, "%.2f bis %.2f", minProfit, maxProfit), "#00e5ff");
        addMetaRow(metaGrid, 1, 2, "Ø Profit pro Pass:", String.format(Locale.US, "%.2f", avgProfit), "#00e5ff");

        addMetaRow(metaGrid, 2, 0, "Trades-Bereich:", minTrades + " bis " + maxTrades, "#ffd740");
        addMetaRow(metaGrid, 2, 1, "Ø Trades pro Pass:", String.format(Locale.US, "%.1f", avgTrades), "#ffd740");

        // --- Charts Row ---
        HBox chartsRow = new HBox(20);
        VBox.setVgrow(chartsRow, Priority.ALWAYS);

        // Chart 1: Scatter Chart (Profit vs Trades)
        VBox scatterBox = new VBox(10);
        HBox.setHgrow(scatterBox, Priority.ALWAYS);
        scatterBox.setStyle("-fx-background-color: #171b26; -fx-padding: 12; -fx-background-radius: 8; -fx-border-color: #2e3543; -fx-border-width: 1px; -fx-border-radius: 8;");
        
        Label scatterTitle = new Label("Gewinnverteilung (Scatter-Plot) - Profit nach Tradeanzahl");
        scatterTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        scatterTitle.setTextFill(Color.web("#00e5ff"));

        NumberAxis sXAxis = new NumberAxis();
        sXAxis.setLabel("Anzahl Trades");
        sXAxis.setTickLabelFill(Color.web("#7e889a"));
        sXAxis.setForceZeroInRange(false);

        NumberAxis sYAxis = new NumberAxis();
        sYAxis.setLabel("Profit");
        sYAxis.setTickLabelFill(Color.web("#7e889a"));
        sYAxis.setForceZeroInRange(false);

        ScatterChart<Number, Number> scatterChart = new ScatterChart<>(sXAxis, sYAxis);
        scatterChart.setLegendVisible(false);
        scatterChart.setAnimated(false);
        scatterChart.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(scatterChart, Priority.ALWAYS);

        XYChart.Series<Number, Number> scatterSeries = new XYChart.Series<>();
        for (Pass p : sampledPasses) {
            scatterSeries.getData().add(new XYChart.Data<>(p.getTotalTrades(), p.getProfit()));
        }
        scatterChart.getData().add(scatterSeries);
        scatterBox.getChildren().addAll(scatterTitle, scatterChart);

        // Chart 2: Line/Spike Chart (Thin vertical lines for distribution)
        VBox spikeBox = new VBox(10);
        HBox.setHgrow(spikeBox, Priority.ALWAYS);
        spikeBox.setStyle("-fx-background-color: #171b26; -fx-padding: 12; -fx-background-radius: 8; -fx-border-color: #2e3543; -fx-border-width: 1px; -fx-border-radius: 8;");

        Label spikeTitle = new Label("Gewinnverteilung (Balkendiagramm) - Gewinne & Verluste pro Tradeanzahl");
        spikeTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        spikeTitle.setTextFill(Color.web("#00e676"));

        NumberAxis lXAxis = new NumberAxis();
        lXAxis.setLabel("Anzahl Trades");
        lXAxis.setTickLabelFill(Color.web("#7e889a"));
        lXAxis.setForceZeroInRange(false);

        NumberAxis lYAxis = new NumberAxis();
        lYAxis.setLabel("Profit");
        lYAxis.setTickLabelFill(Color.web("#7e889a"));
        lYAxis.setForceZeroInRange(false);

        LineChart<Number, Number> spikeChart = new LineChart<>(lXAxis, lYAxis);
        spikeChart.setLegendVisible(true);
        spikeChart.setCreateSymbols(false);
        spikeChart.setAnimated(false);
        spikeChart.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(spikeChart, Priority.ALWAYS);

        XYChart.Series<Number, Number> posSeries = new XYChart.Series<>();
        posSeries.setName("Gewinn (Profit >= 0)");
        XYChart.Series<Number, Number> negSeries = new XYChart.Series<>();
        negSeries.setName("Verlust (Profit < 0)");

        List<Pass> sortedSampledPasses = sampledPasses.stream()
                .sorted(Comparator.comparingInt(Pass::getTotalTrades))
                .collect(Collectors.toList());

        for (Pass p : sortedSampledPasses) {
            int trades = p.getTotalTrades();
            double profit = p.getProfit();
            if (profit >= 0) {
                posSeries.getData().add(new XYChart.Data<>(trades, 0));
                posSeries.getData().add(new XYChart.Data<>(trades, profit));
                posSeries.getData().add(new XYChart.Data<>(trades, 0));
            } else {
                negSeries.getData().add(new XYChart.Data<>(trades, 0));
                negSeries.getData().add(new XYChart.Data<>(trades, profit));
                negSeries.getData().add(new XYChart.Data<>(trades, 0));
            }
        }

        spikeChart.getData().addAll(posSeries, negSeries);
        spikeBox.getChildren().addAll(spikeTitle, spikeChart);

        // Apply colors and stroke sizes to the lines safely
        applySeriesLineStyle(posSeries, "#00e676");
        applySeriesLineStyle(negSeries, "#ff5252");

        chartsRow.getChildren().addAll(scatterBox, spikeBox);

        // --- Bottom Button Row ---
        HBox btnRow = new HBox();
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        btnRow.setPadding(new Insets(10, 0, 0, 0));

        Button closeBtn = new Button("Schließen");
        closeBtn.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #b4bac8; -fx-border-color: #444; -fx-border-width: 1; -fx-font-size: 13px; -fx-font-weight: bold;");
        closeBtn.setOnAction(e -> close());
        btnRow.getChildren().add(closeBtn);

        root.getChildren().addAll(title, metaGrid, chartsRow, btnRow);

        ScrollPane scrollPane = new ScrollPane(root);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background: #0d0f17; -fx-border-color: transparent;");

        Scene scene = new Scene(scrollPane, 1150, 750);
        try {
            scene.getStylesheets().add(getClass().getResource("/css/antigravity.css").toExternalForm());
        } catch (Exception e) {
            // Fallback in case resource loading fails in tests
        }
        setScene(scene);
    }

    private void applySeriesLineStyle(XYChart.Series<Number, Number> series, String color) {
        Runnable styleRunnable = () -> {
            Node node = series.getNode();
            if (node != null) {
                Node line = node.lookup(".chart-series-line");
                if (line != null) {
                    line.setStyle("-fx-stroke: " + color + "; -fx-stroke-width: 1.2px;");
                }
            }
        };

        if (series.getNode() != null) {
            Platform.runLater(styleRunnable);
        } else {
            series.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null) {
                    Platform.runLater(styleRunnable);
                }
            });
        }
    }

    private void addMetaRow(GridPane grid, int col, int row, String label, String value, String textHexColor) {
        Label l = new Label(label);
        l.setStyle("-fx-text-fill: #7e889a; -fx-font-weight: bold; -fx-font-size: 12px;");
        l.setMinWidth(170);

        Label v = new Label(value);
        v.setStyle("-fx-text-fill: " + textHexColor + "; -fx-font-size: 12px; -fx-font-weight: bold;");

        grid.add(l, col * 2, row);
        grid.add(v, col * 2 + 1, row);
    }
}
