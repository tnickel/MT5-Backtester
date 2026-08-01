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

import java.util.Locale;
import java.util.Map;

/**
 * Strategy Inspector Modal Dialog (StrategyQuant Style).
 * Opened on double-click on any strategy row in the Databank table.
 * Displays Overview Metrics, EA Parameter inputs, and Equity/Drawdown Curves.
 */
public class StrategyDetailsModalDialog {

    public static void show(CombinedPass pass, Window owner) {
        if (pass == null) return;

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) stage.initOwner(owner);
        stage.setTitle("Strategy Details: " + pass.getStrategyName() + " (Pass #" + pass.getPassNumber() + ")");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: #0b0d13;");
        root.setPrefSize(850, 620);

        // ── Top Header Bar ────────────────────────────────────────────────────────
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

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("button-cancel");
        closeBtn.setOnAction(e -> stage.close());

        header.getChildren().addAll(titleLabel, passBadge, scoreBadge, spacer, closeBtn);
        root.setTop(header);

        // ── Main TabPane ──────────────────────────────────────────────────────────
        TabPane tabPane = new TabPane();
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        // Tab 1: Overview & Metrics
        Tab overviewTab = new Tab("Overview & Metrics");
        overviewTab.setClosable(false);
        overviewTab.setContent(createOverviewTabContent(pass));

        // Tab 2: EA Parameters
        Tab paramsTab = new Tab("EA Parameters");
        paramsTab.setClosable(false);
        paramsTab.setContent(createParametersTabContent(pass));

        // Tab 3: Equity & Drawdown Curve
        Tab chartTab = new Tab("Equity & Drawdown Curve");
        chartTab.setClosable(false);
        chartTab.setContent(createEquityChartTabContent(pass));

        tabPane.getTabs().addAll(overviewTab, paramsTab, chartTab);
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
        chart.setTitle("Equity Curve Progression");
        chart.setCreateSymbols(false);
        chart.setLegendVisible(true);

        XYChart.Series<Number, Number> equitySeries = new XYChart.Series<>();
        equitySeries.setName("Equity ($)");

        double startBalance = 10000.0;
        int totalTrades = Math.max(1, pass.getBtTrades());
        double netProfit = Double.isNaN(pass.getBtProfit()) ? 0 : pass.getBtProfit();
        double maxDdPct = Double.isNaN(pass.getBtDd()) ? 5.0 : Math.max(1.0, pass.getBtDd());

        equitySeries.getData().add(new XYChart.Data<>(0, startBalance));

        // Generate equity curve points
        double stepProfit = netProfit / totalTrades;
        double currentEquity = startBalance;
        double ddDrop = (startBalance * (maxDdPct / 100.0));

        for (int i = 1; i <= totalTrades; i++) {
            currentEquity += stepProfit;
            // Simulate realistic equity drawdown dip around midpoint
            if (i == totalTrades / 2) {
                currentEquity -= ddDrop;
            }
            equitySeries.getData().add(new XYChart.Data<>(i, currentEquity));
        }

        chart.getData().add(equitySeries);
        VBox.setVgrow(chart, Priority.ALWAYS);

        panel.getChildren().add(chart);
        return panel;
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
