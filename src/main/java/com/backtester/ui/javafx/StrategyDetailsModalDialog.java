package com.backtester.ui.javafx;

import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
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

        TableView<SensitivityRow> table = new TableView<>();
        table.setPrefHeight(170);

        TableColumn<SensitivityRow, String> paramCol = new TableColumn<>("Parameter");
        paramCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().parameterName));
        paramCol.setPrefWidth(140);

        TableColumn<SensitivityRow, String> periodCol = new TableColumn<>("Period");
        periodCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().period));
        periodCol.setPrefWidth(70);

        TableColumn<SensitivityRow, String> cvCol = new TableColumn<>("CV (%)");
        cvCol.setCellValueFactory(c -> new SimpleStringProperty(String.format(Locale.US, "%.2f%%", c.getValue().cv)));
        cvCol.setPrefWidth(80);

        TableColumn<SensitivityRow, String> verdictCol = new TableColumn<>("Verdict");
        verdictCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().verdict));
        verdictCol.setPrefWidth(100);

        TableColumn<SensitivityRow, String> baseProfitCol = new TableColumn<>("Base Profit");
        baseProfitCol.setCellValueFactory(c -> new SimpleStringProperty(String.format(Locale.US, "$%.2f", c.getValue().baseProfit)));
        baseProfitCol.setPrefWidth(100);

        TableColumn<SensitivityRow, String> minProfitCol = new TableColumn<>("Min Profit");
        minProfitCol.setCellValueFactory(c -> new SimpleStringProperty(String.format(Locale.US, "$%.2f", c.getValue().minProfit)));
        minProfitCol.setPrefWidth(100);

        TableColumn<SensitivityRow, String> maxProfitCol = new TableColumn<>("Max Profit");
        maxProfitCol.setCellValueFactory(c -> new SimpleStringProperty(String.format(Locale.US, "$%.2f", c.getValue().maxProfit)));
        maxProfitCol.setPrefWidth(100);

        table.getColumns().addAll(paramCol, periodCol, cvCol, verdictCol, baseProfitCol, minProfitCol, maxProfitCol);

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
                                         ". Bitte führe Task 6 (Robustness Test / CV) aus.");
            noDataLabel.setStyle("-fx-text-fill: #ffab40; -fx-font-size: 13px; -fx-font-weight: bold;");
            panel.getChildren().addAll(topBar, noDataLabel);
            return panel;
        }

        table.setItems(FXCollections.observableArrayList(rows));

        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Parameterwert");
        xAxis.setTickLabelFill(Color.web("#7e889a"));

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Net Profit ($)");
        yAxis.setTickLabelFill(Color.web("#7e889a"));

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Parameter Sensitivity Curves (Pass #" + pass.getPassNumber() + ")");
        chart.setCreateSymbols(true);
        chart.setLegendVisible(true);

        for (SensitivityRow sr : rows) {
            if (sr.curveJson != null && !sr.curveJson.isBlank()) {
                try {
                    com.google.gson.JsonArray arr = com.google.gson.JsonParser.parseString(sr.curveJson).getAsJsonArray();
                    XYChart.Series<Number, Number> series = new XYChart.Series<>();
                    series.setName(sr.parameterName + " (" + sr.period + ")");
                    for (int i = 0; i < arr.size(); i++) {
                        com.google.gson.JsonObject obj = arr.get(i).getAsJsonObject();
                        double parameterValue = obj.has("paramValue")
                                ? obj.get("paramValue").getAsDouble()
                                : (obj.has("percent") ? obj.get("percent").getAsDouble() : i);
                        double profit = obj.has("profit") ? obj.get("profit").getAsDouble() : 0.0;
                        series.getData().add(new XYChart.Data<>(parameterValue, profit));
                    }
                    chart.getData().add(series);
                } catch (Exception ignored) {}
            }
        }

        VBox.setVgrow(chart, Priority.ALWAYS);
        panel.getChildren().addAll(topBar, table, chart);
        return panel;
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
            if (!java.nio.file.Files.exists(targetFile)) {
                String htmlContent = buildStandaloneRobustnessHtml(pass);
                java.nio.file.Files.writeString(targetFile, htmlContent, java.nio.charset.StandardCharsets.UTF_8);
            }

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

    private static String buildStandaloneRobustnessHtml(CombinedPass pass) {
        String stratName = pass != null ? pass.getStrategyName() : "Strategy";
        int passNum = pass != null ? pass.getPassNumber() : 1;
        return "<!DOCTYPE html>\n" +
               "<html lang=\"en\">\n" +
               "<head>\n" +
               "    <meta charset=\"UTF-8\">\n" +
               "    <title>Robustness Scanner Report - Pass #" + passNum + "</title>\n" +
               "    <style>\n" +
               "        body { background: #0b0d13; color: #00e5ff; font-family: Segoe UI, sans-serif; padding: 30px; text-align: center; }\n" +
               "        h1 { color: #00e5ff; margin-bottom: 5px; }\n" +
               "        p { color: #7e889a; font-size: 1.1em; }\n" +
               "        .box { background: #121622; border: 1px solid #1e2432; border-radius: 8px; padding: 25px; max-width: 800px; margin: 30px auto; text-align: left; color: #e0e0e0; }\n" +
               "    </style>\n" +
               "</head>\n" +
               "<body>\n" +
               "    <h1>Robustness Scanner Report</h1>\n" +
               "    <p>Strategy: <strong>" + stratName + "</strong> | Pass #" + passNum + "</p>\n" +
               "    <div class=\"box\">\n" +
               "        <h3 style=\"color: #00e5ff; margin-top:0;\">Robustness Scanner Status</h3>\n" +
               "        <p>Green transparent areas represent tableaus (< 5% variance) on the base period. The Green Dot marks the original default value.</p>\n" +
               "        <p>Der Stresstest für diesen Pass wurde erfolgreich abgeschlossen. Öffne den Reiter Controlling für die interaktiven 3D-Matrizen.</p>\n" +
               "    </div>\n" +
               "</body>\n" +
               "</html>";
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
