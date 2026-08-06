package com.backtester.ui.javafx;

import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Strategy Inspector Modal Dialog (StrategyQuant Style).
 * Opened on double-click or right-click context menu on any strategy row in the Databank table.
 * Displays Overview Metrics, EA Parameter inputs, Equity/Drawdown Curves, and Sensitivity Curves.
 */
public class StrategyDetailsModalDialog {
    private static final Logger log = LoggerFactory.getLogger(StrategyDetailsModalDialog.class);

    public static void show(CombinedPass pass, Window owner) {
        show(pass, null, null, owner, 0, 0L);
    }

    public static void show(CombinedPass pass, Window owner, int selectTab) {
        show(pass, null, null, owner, selectTab, 0L);
    }

    public static void show(CombinedPass pass, Window owner, int selectTab, long sensitivityRunTimestamp) {
        show(pass, null, null, owner, selectTab, sensitivityRunTimestamp);
    }

    public static void show(CombinedPass pass, String dbName, com.backtester.workflow.CustomProject project, Window owner, int selectTab) {
        show(pass, dbName, project, owner, selectTab, 0L);
    }

    public static void show(CombinedPass pass, String dbName, com.backtester.workflow.CustomProject project, Window owner, int selectTab, long sensitivityRunTimestamp) {
        if (pass == null) return;
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) stage.initOwner(owner);
        stage.setTitle("Strategy Details: " + pass.getStrategyName() + " (Pass #" + pass.getPassNumber() + ")");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: #0b0d13;");
        root.setPrefSize(880, 650);

        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 15, 0));

        Label titleLabel = new Label(pass.getStrategyName());
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        titleLabel.setTextFill(Color.web("#00e5ff"));

        Label passBadge = new Label("Pass #" + pass.getPassNumber());
        passBadge.setStyle("-fx-background-color: #1e2432; -fx-text-fill: #ffd740; -fx-font-weight: bold; -fx-padding: 4 10 4 10; -fx-background-radius: 4;");

        Label scoreBadge = new Label(String.format(Locale.US, "Score: %.1f", pass.getScore()));
        scoreBadge.setStyle("-fx-background-color: #1e2432; -fx-text-fill: #00e676; -fx-font-weight: bold; -fx-padding: 4 10 4 10; -fx-background-radius: 4;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button runBtBtn = new Button("▶ Einzel-Backtest im MetaTrader (Terminal bleibt offen)");
        runBtBtn.setStyle("-fx-background-color: #00bcd4; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        runBtBtn.setOnAction(e -> SingleBacktestHelper.runSingleBacktestInMetaTrader(pass, dbName, project, stage));

        Button closeBtn = new Button("Schließen");
        closeBtn.getStyleClass().add("button-cancel");
        closeBtn.setOnAction(e -> stage.close());

        header.getChildren().addAll(titleLabel, passBadge, scoreBadge, spacer, runBtBtn, closeBtn);
        root.setTop(header);

        TabPane tabPane = new TabPane();
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        Tab overviewTab = new Tab("Overview & Metrics");
        overviewTab.setClosable(false);
        overviewTab.setContent(createOverviewTabContent(pass));

        Tab paramsTab = new Tab("EA Parameters");
        paramsTab.setClosable(false);
        paramsTab.setContent(createParametersTabContent(pass));

        Tab chartTab = new Tab("Equity & Drawdown Curve");
        chartTab.setClosable(false);
        chartTab.setContent(createEquityChartTabContent(pass));

        tabPane.getTabs().addAll(overviewTab, paramsTab, chartTab);
        if (com.backtester.database.DatabaseManager.getInstance().hasSensitivityDetails(
                sensitivityRunTimestamp, pass.getPassNumber(), pass.getStrategyName())) {
            Tab sensitivityTab = new Tab("Sensitivitäts-Kennlinien");
            sensitivityTab.setClosable(false);
            sensitivityTab.setContent(createSensitivityTabContent(pass, sensitivityRunTimestamp));
            tabPane.getTabs().add(sensitivityTab);
        }
        if (selectTab >= 0 && selectTab < tabPane.getTabs().size()) {
            tabPane.getSelectionModel().select(selectTab);
        }
        root.setCenter(tabPane);

        Scene scene = new Scene(root);
        if (owner != null && owner.getScene() != null && !owner.getScene().getStylesheets().isEmpty()) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        stage.setScene(scene);
        stage.show();
    }

    private static VBox createOverviewTabContent(CombinedPass pass) {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));

        Label heading = new Label("Performance Metrics Comparison Across Datasets");
        heading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        heading.setTextFill(Color.web("#00e5ff"));

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(12);

        grid.add(createHeaderLabel("Metric"), 0, 0);
        grid.add(createHeaderLabel("Backtest (IS)"), 1, 0);
        grid.add(createHeaderLabel("Forward (OOS)"), 2, 0);
        grid.add(createHeaderLabel("Longterm (5-10Y)"), 3, 0);

        int row = 1;
        grid.add(new Label("Test Period:"), 0, row);
        Label btDateLbl = new Label(pass.getBtDateRange());
        btDateLbl.setStyle("-fx-text-fill: #ffd740; -fx-font-weight: bold;");
        Label fwDateLbl = new Label(pass.getFwDateRange());
        fwDateLbl.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");
        Label ltDateLbl = new Label(pass.getLtDateRange());
        ltDateLbl.setStyle("-fx-text-fill: #a78bfa; -fx-font-weight: bold;");
        grid.add(btDateLbl, 1, row);
        grid.add(fwDateLbl, 2, row);
        grid.add(ltDateLbl, 3, row);
        row++;

        grid.add(new Label("Net Profit:"), 0, row);
        grid.add(createValueLabel(pass.getBtProfit(), "$%.2f"), 1, row);
        grid.add(createValueLabel(pass.getFwProfit(), "$%.2f"), 2, row);
        grid.add(createValueLabel(pass.getLtProfit(), "$%.2f"), 3, row);
        row++;

        grid.add(new Label("Total Trades:"), 0, row);
        grid.add(new Label(String.valueOf(pass.getBtTrades())), 1, row);
        grid.add(new Label(pass.getFwTrades() > 0 ? String.valueOf(pass.getFwTrades()) : "-"), 2, row);
        grid.add(new Label(pass.getLtTrades() > 0 ? String.valueOf(pass.getLtTrades()) : "-"), 3, row);
        row++;

        grid.add(new Label("Profit Factor:"), 0, row);
        grid.add(createValueLabel(pass.getBtPf(), "%.2f"), 1, row);
        grid.add(createValueLabel(pass.getFwPf(), "%.2f"), 2, row);
        grid.add(createValueLabel(pass.getLtPf(), "%.2f"), 3, row);
        row++;

        grid.add(new Label("Max Drawdown %:"), 0, row);
        grid.add(createValueLabel(pass.getBtDd(), "%.2f%%"), 1, row);
        grid.add(createValueLabel(pass.getFwDd(), "%.2f%%"), 2, row);
        grid.add(createValueLabel(pass.getLtDd(), "%.2f%%"), 3, row);
        row++;

        grid.add(new Label("Recovery Factor:"), 0, row);
        grid.add(createValueLabel(pass.getBtRecovery(), "%.2f"), 1, row);
        grid.add(createValueLabel(pass.getFwRecovery(), "%.2f"), 2, row);
        grid.add(createValueLabel(pass.getLtRecovery(), "%.2f"), 3, row);
        row++;

        grid.add(new Label("Sharpe Ratio:"), 0, row);
        grid.add(createValueLabel(pass.getBtSharpe(), "%.2f"), 1, row);
        grid.add(createValueLabel(pass.getFwSharpe(), "%.2f"), 2, row);
        grid.add(createValueLabel(pass.getLtSharpe(), "%.2f"), 3, row);
        row++;

        grid.add(new Label("Expected Payoff:"), 0, row);
        grid.add(createValueLabel(pass.getBtExpectedPayoff(), "$%.2f"), 1, row);
        grid.add(createValueLabel(pass.getFwExpectedPayoff(), "$%.2f"), 2, row);
        grid.add(createValueLabel(pass.getLtExpectedPayoff(), "$%.2f"), 3, row);

        panel.getChildren().addAll(heading, grid);
        return panel;
    }

    private static VBox createParametersTabContent(CombinedPass pass) {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(15));

        Label heading = new Label("Expert Advisor Input Parameters");
        heading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        heading.setTextFill(Color.web("#00e5ff"));

        TableView<Map.Entry<String, String>> paramTable = new TableView<>();
        paramTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<Map.Entry<String, String>, String> keyCol = new TableColumn<>("Parameter Variable");
        keyCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getKey()));
        keyCol.setPrefWidth(250);

        TableColumn<Map.Entry<String, String>, String> valCol = new TableColumn<>("Configured Value");
        valCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getValue()));
        valCol.setPrefWidth(250);
        valCol.setStyle("-fx-alignment: CENTER-LEFT; -fx-font-weight: bold; -fx-text-fill: #ffd740;");

        paramTable.getColumns().addAll(keyCol, valCol);

        Pass btPass = pass.getBacktestPass();
        if (btPass != null && btPass.getParameterValues() != null) {
            paramTable.setItems(FXCollections.observableArrayList(btPass.getParameterValues().entrySet()));
        }

        VBox.setVgrow(paramTable, Priority.ALWAYS);
        panel.getChildren().addAll(heading, paramTable);
        return panel;
    }

    private static VBox createEquityChartTabContent(CombinedPass pass) {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(15));

        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Trades / Progress");
        xAxis.setTickLabelFill(Color.web("#7e889a"));

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Equity ($)");
        yAxis.setTickLabelFill(Color.web("#7e889a"));

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setCreateSymbols(false);
        chart.setLegendVisible(true);

        XYChart.Series<Number, Number> equitySeries = new XYChart.Series<>();

        List<double[]> history = null;
        if (pass.getLongtermPass() != null && pass.getLongtermPass().getEquityHistory() != null && !pass.getLongtermPass().getEquityHistory().isEmpty()) {
            history = pass.getLongtermPass().getEquityHistory();
        } else if (pass.getForwardPass() != null && pass.getForwardPass().getEquityHistory() != null && !pass.getForwardPass().getEquityHistory().isEmpty()) {
            history = pass.getForwardPass().getEquityHistory();
        } else if (pass.getBacktestPass() != null && pass.getBacktestPass().getEquityHistory() != null && !pass.getBacktestPass().getEquityHistory().isEmpty()) {
            history = pass.getBacktestPass().getEquityHistory();
        }

        if (history != null && !history.isEmpty()) {
            chart.setTitle("Echte Trade-Equity Kurve (" + history.size() + " Einzel-Trades)");
            equitySeries.setName("Reale Equity ($)");
            for (double[] pt : history) {
                if (pt != null && pt.length >= 2) {
                    equitySeries.getData().add(new XYChart.Data<>(pt[0], pt[1]));
                }
            }
        } else {
            chart.setTitle("Synthetische Verlaufsvorschau (Reale Trade-Historie ausstehend)");
            equitySeries.setName("Equity ($)");

            double startBalance = 10000.0;
            int totalTrades = Math.max(1, pass.getBtTrades());
            double netProfit = Double.isNaN(pass.getBtProfit()) ? 0 : pass.getBtProfit();

            equitySeries.getData().add(new XYChart.Data<>(0, startBalance));
            double stepProfit = netProfit / totalTrades;
            double currentEquity = startBalance;
            for (int i = 1; i <= totalTrades; i++) {
                currentEquity += stepProfit;
                equitySeries.getData().add(new XYChart.Data<>(i, currentEquity));
            }

            Label infoNote = new Label("ℹ️ Hinweis: Dies ist ein Batch-Optimizer Pass. Batch-Optimierungen in MT5 erfassen noch keine Trade-für-Trade Kurven. Klicke oben auf '▶ Einzel-Backtest im MetaTrader' oder führe den Retest-Schritt aus, um die exakten realen Einzel-Trades zu generieren.");
            infoNote.setWrapText(true);
            infoNote.setStyle("-fx-text-fill: #00e5ff; -fx-background-color: rgba(0, 229, 255, 0.1); -fx-padding: 8; -fx-border-color: #00e5ff; -fx-border-radius: 4; -fx-background-radius: 4; -fx-font-size: 11px;");
            panel.getChildren().add(infoNote);
        }

        chart.getData().add(equitySeries);
        VBox.setVgrow(chart, Priority.ALWAYS);

        panel.getChildren().add(0, chart);
        return panel;
    }

    private static VBox createSensitivityTabContent(CombinedPass pass, long sensitivityRunTimestamp) {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(12));

        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);

        Label heading = new Label("📈 Parameter-Sensitivität & Kennlinien aus Stresstest (Pass #" + pass.getPassNumber() + ")");
        heading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        heading.setTextFill(Color.web("#00e5ff"));

        Region flexSpacer = new Region();
        HBox.setHgrow(flexSpacer, Priority.ALWAYS);

        Button openHtmlReportBtn = new Button("🌐 HTML Scanner Report im Browser öffnen");
        openHtmlReportBtn.setStyle("-fx-background-color: #00e5ff; -fx-text-fill: #0b0d13; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 6 14; -fx-background-radius: 4;");
        openHtmlReportBtn.setOnAction(e -> openRobustnessHtmlReport(pass, sensitivityRunTimestamp));

        topBar.getChildren().addAll(heading, flexSpacer, openHtmlReportBtn);

        java.util.List<SensitivityRow> rows = new java.util.ArrayList<>();
        try {
            com.backtester.database.DatabaseManager db = com.backtester.database.DatabaseManager.getInstance();
            String sql = "SELECT parameter_name, period, cv, verdict, base_value, base_profit, mean_profit, min_profit, max_profit, curve_json " +
                         "FROM SENSITIVITY_DETAIL WHERE run_timestamp = ? AND pass_number = ? AND pass_name = ? " +
                         "ORDER BY parameter_name, period";
            try (java.sql.Connection conn = db.getConnection();
                 java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, sensitivityRunTimestamp);
                pstmt.setInt(2, pass.getPassNumber());
                pstmt.setString(3, pass.getStrategyName());
                try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new SensitivityRow(
                            rs.getString("parameter_name"),
                            rs.getString("period"),
                            rs.getDouble("cv"),
                            rs.getString("verdict"),
                            rs.getDouble("base_value"),
                            rs.getDouble("base_profit"),
                            rs.getDouble("mean_profit"),
                            rs.getDouble("min_profit"),
                            rs.getDouble("max_profit"),
                            rs.getString("curve_json")
                        ));
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Failed to load sensitivity details for pass #" + pass.getPassNumber(), ex);
        }

        if (rows.isEmpty()) {
            Label noDataLabel = new Label("Hinweis: Noch keine Sensitivitäts-Kennlinien in DB für Pass #" + pass.getPassNumber() +
                                         ". Bitte führe Task (Robustness Test / CV) aus.");
            noDataLabel.setStyle("-fx-text-fill: #ffab40; -fx-font-size: 13px; -fx-font-weight: bold;");
            panel.getChildren().addAll(topBar, noDataLabel);
            return panel;
        }

        // Summary Bar with Counters & Ampeln
        int robustCount = 0, acceptableCount = 0, fragileCount = 0;
        for (SensitivityRow r : rows) {
            String vUpper = r.verdict != null ? r.verdict.toUpperCase(Locale.US) : "";
            if ("ROBUST".equals(vUpper)) robustCount++;
            else if ("ACCEPTABLE".equals(vUpper)) acceptableCount++;
            else fragileCount++;
        }

        HBox summaryBar = new HBox(16);
        summaryBar.setAlignment(Pos.CENTER_LEFT);
        summaryBar.setStyle("-fx-background-color: #121622; -fx-border-color: #1e2432; -fx-padding: 8 14; -fx-background-radius: 6;");

        Label totalLabel = new Label("Gesamt Parametersweeps: " + rows.size());
        totalLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        totalLabel.setTextFill(Color.web("#e0e0e0"));

        Label robustBadge = new Label("🟢 Robust: " + robustCount);
        robustBadge.setStyle("-fx-background-color: #143820; -fx-text-fill: #00e676; -fx-padding: 3 8; -fx-background-radius: 10; -fx-font-weight: bold;");

        Label accBadge = new Label("🟡 Acceptable: " + acceptableCount);
        accBadge.setStyle("-fx-background-color: #3d2b00; -fx-text-fill: #ffb300; -fx-padding: 3 8; -fx-background-radius: 10; -fx-font-weight: bold;");

        Label fragileBadge = new Label("🔴 Fragile: " + fragileCount);
        fragileBadge.setStyle("-fx-background-color: #3d0c11; -fx-text-fill: #ff1744; -fx-padding: 3 8; -fx-background-radius: 10; -fx-font-weight: bold;");

        summaryBar.getChildren().addAll(totalLabel, robustBadge, accBadge, fragileBadge);

        // Scrollable List of Parameter Cards
        VBox cardsContainer = new VBox(14);
        cardsContainer.setPadding(new Insets(4));

        for (SensitivityRow row : rows) {
            HBox card = new HBox(16);
            card.setAlignment(Pos.CENTER_LEFT);
            card.setStyle("-fx-background-color: #121622; -fx-border-color: #1e2432; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 12;");

            // --- LEFT SIDE: 1/4 Size Dedicated Graphic / LineChart ---
            NumberAxis xAxis = new NumberAxis();
            xAxis.setLabel("Parameterwert");
            xAxis.setTickLabelFill(Color.web("#7e889a"));
            xAxis.setTickLabelFont(Font.font("Segoe UI", 9));
            xAxis.setForceZeroInRange(false);
            xAxis.setAutoRanging(true);

            NumberAxis yAxis = new NumberAxis();
            yAxis.setLabel("Profit ($)");
            yAxis.setTickLabelFill(Color.web("#7e889a"));
            yAxis.setTickLabelFont(Font.font("Segoe UI", 9));
            yAxis.setForceZeroInRange(false);
            yAxis.setAutoRanging(true);

            LineChart<Number, Number> miniChart = new LineChart<>(xAxis, yAxis);
            miniChart.setPrefSize(340, 190);
            miniChart.setMinSize(320, 180);
            miniChart.setMaxSize(360, 200);
            miniChart.setCreateSymbols(true);
            miniChart.setLegendVisible(false);
            miniChart.setAnimated(false);
            miniChart.setStyle("-fx-background-color: transparent;");

            if (row.curveJson != null && !row.curveJson.isBlank()) {
                try {
                    com.google.gson.JsonArray arr = com.google.gson.JsonParser.parseString(row.curveJson).getAsJsonArray();
                    XYChart.Series<Number, Number> series = new XYChart.Series<>();

                    for (int i = 0; i < arr.size(); i++) {
                        com.google.gson.JsonObject obj = arr.get(i).getAsJsonObject();
                        double parameterValue = obj.has("paramValue")
                                ? obj.get("paramValue").getAsDouble()
                                : (obj.has("percent") ? obj.get("percent").getAsDouble() : i);
                        double profit = obj.has("profit") ? obj.get("profit").getAsDouble() : 0.0;
                        series.getData().add(new XYChart.Data<>(parameterValue, profit));
                    }
                    miniChart.getData().add(series);
                } catch (Exception ignored) {}
            }

            // --- RIGHT SIDE: Values & Ampel Assessment Card ---
            VBox detailsBox = new VBox(10);
            HBox.setHgrow(detailsBox, Priority.ALWAYS);

            // Title Line (Parameter Name + Period + Verdict Ampel Badge)
            HBox cardHeader = new HBox(12);
            cardHeader.setAlignment(Pos.CENTER_LEFT);

            Label paramNameLabel = new Label(row.parameterName);
            paramNameLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
            paramNameLabel.setTextFill(Color.web("#00e5ff"));

            Label periodTag = new Label("[" + row.period + "]");
            periodTag.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
            periodTag.setStyle("FW".equalsIgnoreCase(row.period)
                    ? "-fx-background-color: #3b164c; -fx-text-fill: #ab47bc; -fx-padding: 2 6; -fx-background-radius: 4;"
                    : "-fx-background-color: #0b3547; -fx-text-fill: #00e5ff; -fx-padding: 2 6; -fx-background-radius: 4;");

            Label verdictBadge = new Label();
            verdictBadge.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
            String vUpper = row.verdict != null ? row.verdict.toUpperCase(Locale.US) : "";
            if ("ROBUST".equals(vUpper)) {
                verdictBadge.setText("🟢 ROBUST");
                verdictBadge.setStyle("-fx-background-color: #143820; -fx-text-fill: #00e676; -fx-padding: 3 10; -fx-background-radius: 10; -fx-font-weight: bold;");
            } else if ("ACCEPTABLE".equals(vUpper)) {
                verdictBadge.setText("🟡 ACCEPTABLE");
                verdictBadge.setStyle("-fx-background-color: #3d2b00; -fx-text-fill: #ffb300; -fx-padding: 3 10; -fx-background-radius: 10; -fx-font-weight: bold;");
            } else {
                verdictBadge.setText("🔴 FRAGILE");
                verdictBadge.setStyle("-fx-background-color: #3d0c11; -fx-text-fill: #ff1744; -fx-padding: 3 10; -fx-background-radius: 10; -fx-font-weight: bold;");
            }

            cardHeader.getChildren().addAll(paramNameLabel, periodTag, verdictBadge);

            // Metrics Grid
            GridPane metricsGrid = new GridPane();
            metricsGrid.setHgap(24);
            metricsGrid.setVgap(6);

            // Row 0
            metricsGrid.add(createMetricCell("Variationskoeffizient (CV):", String.format(Locale.US, "%.2f%%", row.cv),
                    row.cv < 30.0 ? "#00e676" : (row.cv <= 60.0 ? "#ffb300" : "#ff1744")), 0, 0);
            metricsGrid.add(createMetricCell("Optimierter Basis-Wert:", String.format(Locale.US, "%.4f", row.baseValue), "#e0e0e0"), 1, 0);

            // Row 1
            metricsGrid.add(createMetricCell("Basis-Gewinn (Base Profit):", String.format(Locale.US, "$%.2f", row.baseProfit), "#00e5ff"), 0, 1);
            metricsGrid.add(createMetricCell("Minimaler Gewinn (Min):", String.format(Locale.US, "$%.2f", row.minProfit), row.minProfit >= 0 ? "#00e676" : "#ff5252"), 1, 1);

            // Row 2
            metricsGrid.add(createMetricCell("Durchschnittsgewinn (Mean):", String.format(Locale.US, "$%.2f", row.meanProfit), "#e0e0e0"), 0, 2);
            metricsGrid.add(createMetricCell("Maximaler Gewinn (Max):", String.format(Locale.US, "$%.2f", row.maxProfit), "#00e676"), 1, 2);

            detailsBox.getChildren().addAll(cardHeader, metricsGrid);
            card.getChildren().addAll(miniChart, detailsBox);
            cardsContainer.getChildren().add(card);
        }

        ScrollPane scrollPane = new ScrollPane(cardsContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #0b0d13; -fx-background-color: transparent; -fx-viewport-border-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        panel.getChildren().addAll(topBar, summaryBar, scrollPane);
        return panel;
    }

    private static HBox createMetricCell(String label, String value, String colorHex) {
        HBox cell = new HBox(6);
        cell.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(label);
        lbl.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 11));
        lbl.setTextFill(Color.web("#7e889a"));

        Label val = new Label(value);
        val.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        val.setTextFill(Color.web(colorHex));

        cell.getChildren().addAll(lbl, val);
        return cell;
    }

    public static void openRobustnessHtmlReport(CombinedPass pass, long sensitivityRunTimestamp) {
        try {
            if (pass == null || !com.backtester.database.DatabaseManager.getInstance().hasSensitivityDetails(
                    sensitivityRunTimestamp, pass.getPassNumber(), pass.getStrategyName())) {
                return;
            }

            java.nio.file.Path reportsDir = java.nio.file.Paths.get("backtest_reports");
            java.nio.file.Files.createDirectories(reportsDir);
            java.nio.file.Path targetFile = reportsDir.resolve(
                    "robustness_report_" + sensitivityRunTimestamp + "_Pass" + pass.getPassNumber() + ".html");
            
            // Build fresh HTML report with full sensitivity tables and charts!
            String htmlContent = buildStandaloneRobustnessHtml(pass, sensitivityRunTimestamp);
            java.nio.file.Files.writeString(targetFile, htmlContent, java.nio.charset.StandardCharsets.UTF_8);

            openHtmlFileInBrowser(targetFile);
        } catch (Exception ex) {
            log.error("Failed to open robustness HTML report", ex);
        }
    }

    private static void openHtmlFileInBrowser(java.nio.file.Path htmlPath) {
        if (htmlPath == null || !java.nio.file.Files.exists(htmlPath)) return;
        try {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(htmlPath.toUri());
            } else {
                new ProcessBuilder("cmd", "/c", "start", "", htmlPath.toAbsolutePath().toString()).start();
            }
        } catch (Exception ex) {
            try {
                new ProcessBuilder("cmd", "/c", "start", "", htmlPath.toAbsolutePath().toString()).start();
            } catch (Exception ignored) {}
        }
    }

    private static String buildStandaloneRobustnessHtml(CombinedPass pass, long sensitivityRunTimestamp) {
        String stratName = pass != null ? pass.getStrategyName() : "Strategy";
        int passNum = pass != null ? pass.getPassNumber() : 1;

        java.util.List<SensitivityRow> rows = new java.util.ArrayList<>();
        try {
            com.backtester.database.DatabaseManager db = com.backtester.database.DatabaseManager.getInstance();
            String sql = "SELECT parameter_name, period, cv, verdict, base_value, base_profit, mean_profit, min_profit, max_profit, curve_json " +
                         "FROM SENSITIVITY_DETAIL WHERE run_timestamp = ? AND pass_number = ? AND pass_name = ? " +
                         "ORDER BY parameter_name, period";
            try (java.sql.Connection conn = db.getConnection();
                 java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, sensitivityRunTimestamp);
                pstmt.setInt(2, passNum);
                pstmt.setString(3, stratName);
                try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new SensitivityRow(
                            rs.getString("parameter_name"),
                            rs.getString("period"),
                            rs.getDouble("cv"),
                            rs.getString("verdict"),
                            rs.getDouble("base_value"),
                            rs.getDouble("base_profit"),
                            rs.getDouble("mean_profit"),
                            rs.getDouble("min_profit"),
                            rs.getDouble("max_profit"),
                            rs.getString("curve_json")
                        ));
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Failed to load sensitivity details for HTML report, pass #" + passNum, ex);
        }

        int robustCount = 0, accCount = 0, fragileCount = 0;
        for (SensitivityRow r : rows) {
            String v = r.verdict != null ? r.verdict.toUpperCase(Locale.US) : "";
            if ("ROBUST".equals(v)) robustCount++;
            else if ("ACCEPTABLE".equals(v)) accCount++;
            else fragileCount++;
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"de\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n<title>Robustness Scanner Report - ").append(stratName).append(" (Pass #").append(passNum).append(")</title>\n");
        html.append("<script src=\"https://cdn.jsdelivr.net/npm/chart.js\"></script>\n");
        html.append("<style>\n");
        html.append("  body { background-color: #0b0d13; color: #e0e0e0; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 24px; }\n");
        html.append("  h1 { color: #00e5ff; margin-bottom: 4px; }\n");
        html.append("  .subtitle { color: #7e889a; font-size: 1.1em; margin-bottom: 20px; }\n");
        html.append("  .summary-bar { background: #121622; border: 1px solid #1e2432; padding: 12px 20px; border-radius: 8px; margin-bottom: 24px; display: flex; gap: 16px; align-items: center; }\n");
        html.append("  .badge { padding: 4px 10px; border-radius: 12px; font-weight: bold; font-size: 0.9em; }\n");
        html.append("  .badge-robust { background: #143820; color: #00e676; }\n");
        html.append("  .badge-acceptable { background: #3d2b00; color: #ffb300; }\n");
        html.append("  .badge-fragile { background: #3d0c11; color: #ff1744; }\n");
        html.append("  table { width: 100%; border-collapse: collapse; background: #121622; border: 1px solid #1e2432; border-radius: 8px; overflow: hidden; margin-bottom: 30px; }\n");
        html.append("  th, td { padding: 10px 14px; text-align: left; border-bottom: 1px solid #1e2432; font-size: 0.95em; }\n");
        html.append("  th { background: #1a202c; color: #00e5ff; font-weight: bold; }\n");
        html.append("  tr:hover { background: #181e2b; }\n");
        html.append("  .card { background: #121622; border: 1px solid #1e2432; border-radius: 8px; padding: 16px; margin-bottom: 16px; display: flex; gap: 20px; align-items: center; }\n");
        html.append("  .chart-box { width: 360px; height: 200px; flex-shrink: 0; }\n");
        html.append("  .details-box { flex-grow: 1; }\n");
        html.append("  .param-title { font-size: 1.2em; font-weight: bold; color: #00e5ff; margin-bottom: 8px; display: flex; align-items: center; gap: 12px; }\n");
        html.append("  .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px 24px; font-size: 0.9em; margin-top: 10px; }\n");
        html.append("  .metric-lbl { color: #7e889a; }\n");
        html.append("  .metric-val { font-weight: bold; color: #ffffff; }\n");
        html.append("</style>\n</head>\n<body>\n");

        html.append("<h1>📈 Robustness Scanner Report</h1>\n");
        html.append("<div class=\"subtitle\">Strategie: <strong>").append(stratName).append("</strong> | Pass #").append(passNum).append("</div>\n");

        html.append("<div class=\"summary-bar\">\n");
        html.append("  <span>Gesamt Parametersweeps: <strong>").append(rows.size()).append("</strong></span>\n");
        html.append("  <span class=\"badge badge-robust\">🟢 Robust: ").append(robustCount).append("</span>\n");
        html.append("  <span class=\"badge badge-acceptable\">🟡 Acceptable: ").append(accCount).append("</span>\n");
        html.append("  <span class=\"badge badge-fragile\">🔴 Fragile: ").append(fragileCount).append("</span>\n");
        html.append("</div>\n");

        // Summary Table
        html.append("<h2>📋 Parameter-Sensitivität Übersicht</h2>\n");
        html.append("<table>\n<thead><tr><th>Parameter</th><th>Period</th><th>CV (%)</th><th>Verdict</th><th>Base Value</th><th>Base Profit</th><th>Min Profit</th><th>Max Profit</th></tr></thead>\n<tbody>\n");

        for (SensitivityRow r : rows) {
            String vUpper = r.verdict != null ? r.verdict.toUpperCase(Locale.US) : "";
            String badgeCls = "ROBUST".equals(vUpper) ? "badge-robust" : ("ACCEPTABLE".equals(vUpper) ? "badge-acceptable" : "badge-fragile");
            String symbol = "ROBUST".equals(vUpper) ? "🟢" : ("ACCEPTABLE".equals(vUpper) ? "🟡" : "🔴");
            html.append("<tr>")
                .append("<td><strong>").append(r.parameterName).append("</strong></td>")
                .append("<td>").append(r.period).append("</td>")
                .append("<td style=\"font-weight:bold; color:").append(r.cv < 30 ? "#00e676" : (r.cv <= 60 ? "#ffb300" : "#ff1744")).append(";\">").append(String.format(Locale.US, "%.2f%%", r.cv)).append("</td>")
                .append("<td><span class=\"badge ").append(badgeCls).append("\">").append(symbol).append(" ").append(r.verdict).append("</span></td>")
                .append("<td>").append(String.format(Locale.US, "%.4f", r.baseValue)).append("</td>")
                .append("<td>$").append(String.format(Locale.US, "%.2f", r.baseProfit)).append("</td>")
                .append("<td>$").append(String.format(Locale.US, "%.2f", r.minProfit)).append("</td>")
                .append("<td>$").append(String.format(Locale.US, "%.2f", r.maxProfit)).append("</td>")
                .append("</tr>\n");
        }
        html.append("</tbody>\n</table>\n");

        // Parameter Sweep Cards with Charts
        html.append("<h2>📊 Einzelne Parameter-Kennlinien</h2>\n");

        for (int idx = 0; idx < rows.size(); idx++) {
            SensitivityRow r = rows.get(idx);
            String canvasId = "chart_" + idx;
            String vUpper = r.verdict != null ? r.verdict.toUpperCase(Locale.US) : "";
            String badgeCls = "ROBUST".equals(vUpper) ? "badge-robust" : ("ACCEPTABLE".equals(vUpper) ? "badge-acceptable" : "badge-fragile");
            String symbol = "ROBUST".equals(vUpper) ? "🟢" : ("ACCEPTABLE".equals(vUpper) ? "🟡" : "🔴");

            html.append("<div class=\"card\">\n");
            html.append("  <div class=\"chart-box\"><canvas id=\"").append(canvasId).append("\"></canvas></div>\n");
            html.append("  <div class=\"details-box\">\n");
            html.append("    <div class=\"param-title\">").append(r.parameterName).append(" <span style=\"color:#ab47bc; font-size:0.8em;\">[").append(r.period).append("]</span> <span class=\"badge ").append(badgeCls).append("\">").append(symbol).append(" ").append(r.verdict).append("</span></div>\n");
            html.append("    <div class=\"grid\">\n");
            html.append("      <div><span class=\"metric-lbl\">Variationskoeffizient (CV):</span> <span class=\"metric-val\" style=\"color:").append(r.cv < 30 ? "#00e676" : (r.cv <= 60 ? "#ffb300" : "#ff1744")).append(";\">").append(String.format(Locale.US, "%.2f%%", r.cv)).append("</span></div>\n");
            html.append("      <div><span class=\"metric-lbl\">Optimierter Basis-Wert:</span> <span class=\"metric-val\">").append(String.format(Locale.US, "%.4f", r.baseValue)).append("</span></div>\n");
            html.append("      <div><span class=\"metric-lbl\">Basis-Gewinn:</span> <span class=\"metric-val\" style=\"color:#00e5ff;\">$").append(String.format(Locale.US, "%.2f", r.baseProfit)).append("</span></div>\n");
            html.append("      <div><span class=\"metric-lbl\">Minimaler Gewinn:</span> <span class=\"metric-val\">$").append(String.format(Locale.US, "%.2f", r.minProfit)).append("</span></div>\n");
            html.append("      <div><span class=\"metric-lbl\">Durchschnittsgewinn:</span> <span class=\"metric-val\">$").append(String.format(Locale.US, "%.2f", r.meanProfit)).append("</span></div>\n");
            html.append("      <div><span class=\"metric-lbl\">Maximaler Gewinn:</span> <span class=\"metric-val\">$").append(String.format(Locale.US, "%.2f", r.maxProfit)).append("</span></div>\n");
            html.append("    </div>\n");
            html.append("  </div>\n");
            html.append("</div>\n");
        }

        // Script to render Chart.js curves
        html.append("<script>\n");
        for (int idx = 0; idx < rows.size(); idx++) {
            SensitivityRow r = rows.get(idx);
            String canvasId = "chart_" + idx;
            html.append("{\n");
            html.append("  const ctx = document.getElementById('").append(canvasId).append("').getContext('2d');\n");
            html.append("  let labels = []; let data = [];\n");
            if (r.curveJson != null && !r.curveJson.isBlank()) {
                try {
                    com.google.gson.JsonArray arr = com.google.gson.JsonParser.parseString(r.curveJson).getAsJsonArray();
                    html.append("  labels = [");
                    for (int i = 0; i < arr.size(); i++) {
                        com.google.gson.JsonObject obj = arr.get(i).getAsJsonObject();
                        double pv = obj.has("paramValue") ? obj.get("paramValue").getAsDouble() : (obj.has("percent") ? obj.get("percent").getAsDouble() : i);
                        html.append(String.format(Locale.US, "%.4f", pv)).append(i < arr.size() - 1 ? "," : "");
                    }
                    html.append("];\n  data = [");
                    for (int i = 0; i < arr.size(); i++) {
                        com.google.gson.JsonObject obj = arr.get(i).getAsJsonObject();
                        double pr = obj.has("profit") ? obj.get("profit").getAsDouble() : 0.0;
                        html.append(String.format(Locale.US, "%.2f", pr)).append(i < arr.size() - 1 ? "," : "");
                    }
                    html.append("];\n");
                } catch (Exception ignored) {}
            }
            html.append("  new Chart(ctx, {\n");
            html.append("    type: 'line',\n");
            html.append("    data: {\n");
            html.append("      labels: labels,\n");
            html.append("      datasets: [{ label: 'Profit ($)', data: data, borderColor: '#ff9800', backgroundColor: 'rgba(255, 152, 0, 0.1)', borderWidth: 2, pointRadius: 4, pointBackgroundColor: '#ffffff' }]\n");
            html.append("    },\n");
            html.append("    options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } }, scales: { x: { ticks: { color: '#7e889a' } }, y: { ticks: { color: '#7e889a' } } } }\n");
            html.append("  });\n");
            html.append("}\n");
        }
        html.append("</script>\n</body>\n</html>");

        return html.toString();
    }

    public static class SensitivityRow {
        public final String parameterName;
        public final String period;
        public final double cv;
        public final String verdict;
        public final double baseValue;
        public final double baseProfit;
        public final double meanProfit;
        public final double minProfit;
        public final double maxProfit;
        public final String curveJson;

        public SensitivityRow(String parameterName, String period, double cv, String verdict,
                              double baseValue, double baseProfit, double meanProfit,
                              double minProfit, double maxProfit, String curveJson) {
            this.parameterName = parameterName;
            this.period = period;
            this.cv = cv;
            this.verdict = verdict;
            this.baseValue = baseValue;
            this.baseProfit = baseProfit;
            this.meanProfit = meanProfit;
            this.minProfit = minProfit;
            this.maxProfit = maxProfit;
            this.curveJson = curveJson;
        }
    }

    private static Label createHeaderLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        l.setTextFill(Color.web("#00e5ff"));
        return l;
    }

    private static Label createValueLabel(double val, String format) {
        if (Double.isNaN(val)) return new Label("-");
        Label l = new Label(String.format(Locale.US, format, val));
        if (val > 0) l.setTextFill(Color.web("#00e676"));
        else if (val < 0) l.setTextFill(Color.web("#ff5252"));
        return l;
    }
}
