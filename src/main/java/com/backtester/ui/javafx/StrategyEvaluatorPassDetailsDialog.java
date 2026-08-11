package com.backtester.ui.javafx;

import com.backtester.report.OptimizationResult.CombinedPass;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pass-details and robustness-scorecard WebView UI extracted from
 * {@link StrategyEvaluatorDialog}.
 */
public final class StrategyEvaluatorPassDetailsDialog {
    private static final Logger log = LoggerFactory.getLogger(StrategyEvaluatorPassDetailsDialog.class);

    private StrategyEvaluatorPassDetailsDialog() {}

    public static void show(Window owner, CombinedPass cp, OptimizationView parentView,
                            int referenceTrades, double referenceProfit) {
        Stage detailStage = new Stage();
        if (owner != null) {
            detailStage.initOwner(owner);
        }
        detailStage.initModality(Modality.APPLICATION_MODAL);
        detailStage.setTitle("Strategie-Details: Pass #" + cp.getPassNumber());

        VBox layout = new VBox(15);
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color: #11141d; -fx-border-color: #00e5ff; -fx-border-width: 1px; -fx-border-radius: 5px;");

        // --- Header ---
        Label titleLabel = new Label("STRATEGIE-DETAILS (PASS #" + cp.getPassNumber() + ")");
        titleLabel.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 18));
        titleLabel.setTextFill(Color.web("#00e5ff"));

        String fromDateStr = "Unbekannt";
        String toDateStr = "Unbekannt";
        if (parentView != null && parentView.getLastOptResult() != null) {
            if (parentView.getLastOptResult().getFromDate() != null && !parentView.getLastOptResult().getFromDate().isEmpty()) {
                fromDateStr = parentView.getLastOptResult().getFromDate();
            }
            if (parentView.getLastOptResult().getToDate() != null && !parentView.getLastOptResult().getToDate().isEmpty()) {
                toDateStr = parentView.getLastOptResult().getToDate();
            }
        }
        Label subtitleLabel = new Label("Zeitraum: " + fromDateStr + " bis " + toDateStr);
        subtitleLabel.setFont(javafx.scene.text.Font.font("Segoe UI", 13));
        subtitleLabel.setTextFill(Color.web("#7e889a"));

        layout.getChildren().addAll(titleLabel, subtitleLabel);

        // --- Cards Pane ---
        HBox cardsBox = new HBox(12);
        cardsBox.setAlignment(Pos.TOP_LEFT);

        // 1. Backtest Card
        VBox btCard = new VBox(10);
        btCard.setPadding(new Insets(12));
        btCard.setStyle("-fx-background-color: #171b26; -fx-border-color: #00e676; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px;");
        HBox.setHgrow(btCard, Priority.ALWAYS);
        Label btTitle = new Label("◀ BACKTEST METRIKEN");
        btTitle.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 13));
        btTitle.setTextFill(Color.web("#00e676"));
        GridPane btGrid = new GridPane();
        btGrid.setHgap(15);
        btGrid.setVgap(6);
        addMetricRow(btGrid, 0, "Nettoprofit:", String.format(Locale.US, "%.2f", cp.getBtProfit()));
        addMetricRow(btGrid, 1, "Trades:", String.valueOf(cp.getBtTrades()));
        addMetricRow(btGrid, 2, "Profit Factor:", String.format(Locale.US, "%.2f", cp.getBtPf()));
        addMetricRow(btGrid, 3, "Max. Drawdown:", String.format(Locale.US, "%.2f%%", cp.getBtDd()));
        addMetricRow(btGrid, 4, "Recovery Factor:", String.format(Locale.US, "%.2f", cp.getBtRecovery()));
        addMetricRow(btGrid, 5, "Sharpe Ratio:", Double.isNaN(cp.getBtSharpe()) ? "—" : String.format(Locale.US, "%.2f", cp.getBtSharpe()));
        addMetricRow(btGrid, 6, "Expected Payoff:", String.format(Locale.US, "%.2f", cp.getBtExpectedPayoff()));
        btCard.getChildren().addAll(btTitle, btGrid);

        // 2. Forward Card
        VBox fwCard = new VBox(10);
        fwCard.setPadding(new Insets(12));
        fwCard.setStyle("-fx-background-color: #171b26; -fx-border-color: #00e5ff; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px;");
        HBox.setHgrow(fwCard, Priority.ALWAYS);
        Label fwTitle = new Label("FORWARD METRIKEN ▶");
        fwTitle.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 13));
        fwTitle.setTextFill(Color.web("#00e5ff"));
        GridPane fwGrid = new GridPane();
        fwGrid.setHgap(15);
        fwGrid.setVgap(6);

        if (cp.getForwardPass() != null) {
            addMetricRow(fwGrid, 0, "Nettoprofit:", String.format(Locale.US, "%.2f", cp.getFwProfit()));
            addMetricRow(fwGrid, 1, "Trades:", String.valueOf(cp.getFwTrades()));
            addMetricRow(fwGrid, 2, "Profit Factor:", Double.isNaN(cp.getFwPf()) ? "—" : String.format(Locale.US, "%.2f", cp.getFwPf()));
            addMetricRow(fwGrid, 3, "Max. Drawdown:", Double.isNaN(cp.getFwDd()) ? "—" : String.format(Locale.US, "%.2f%%", cp.getFwDd()));
            addMetricRow(fwGrid, 4, "Recovery Factor:", Double.isNaN(cp.getFwRecovery()) ? "—" : String.format(Locale.US, "%.2f", cp.getFwRecovery()));
            addMetricRow(fwGrid, 5, "Sharpe Ratio:", Double.isNaN(cp.getFwSharpe()) ? "—" : String.format(Locale.US, "%.2f", cp.getFwSharpe()));
            addMetricRow(fwGrid, 6, "Expected Payoff:", Double.isNaN(cp.getFwExpectedPayoff()) ? "—" : String.format(Locale.US, "%.2f", cp.getFwExpectedPayoff()));
            fwCard.getChildren().addAll(fwTitle, fwGrid);
        } else {
            Label noFwLabel = new Label("Kein Forward-Test\ndurchgeführt.");
            noFwLabel.setTextFill(Color.web("#7e889a"));
            noFwLabel.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 12));
            noFwLabel.setAlignment(Pos.CENTER);
            VBox.setVgrow(noFwLabel, Priority.ALWAYS);
            fwCard.getChildren().addAll(fwTitle, noFwLabel);
        }

        // 3. Evaluation Card
        VBox evalCard = new VBox(10);
        evalCard.setPadding(new Insets(12));
        evalCard.setStyle("-fx-background-color: #171b26; -fx-border-color: #ffd740; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px;");
        HBox.setHgrow(evalCard, Priority.ALWAYS);
        Label evalTitle = new Label("BEWERTUNG & ROBUSTHEIT");
        evalTitle.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 13));
        evalTitle.setTextFill(Color.web("#ffd740"));
        GridPane evalGrid = new GridPane();
        evalGrid.setHgap(15);
        evalGrid.setVgap(6);

        StrategyEvaluatorDialog.Evaluation eval = StrategyEvaluatorMetrics.evaluatePass(cp, referenceTrades, referenceProfit);
        addMetricRow(evalGrid, 0, "Score (Gewichtung):", String.format(Locale.US, "%.2f", cp.getScore()));
        addMetricRow(evalGrid, 1, "Robustness-Index (RI):", String.format(Locale.US, "%.2f", StrategyEvaluatorMetrics.calculateRobustnessIndex(cp, referenceTrades)));
        addMetricRow(evalGrid, 2, "Forward-Konsistenz:", String.format(Locale.US, "%.2f", cp.getConsistency()));
        Label verdictVal = addMetricRow(evalGrid, 3, "Analyse-Urteil:", eval.remark);
        verdictVal.setTextFill(Color.web(eval.color));
        verdictVal.setStyle("-fx-font-weight: bold;");
        verdictVal.setWrapText(true);
        verdictVal.setMaxWidth(160);

        evalCard.getChildren().addAll(evalTitle, evalGrid);

        cardsBox.getChildren().addAll(btCard, fwCard, evalCard);
        layout.getChildren().add(cardsBox);

        // --- Equity Chart ---
        Label chartTitleLabel = new Label("EQUITY-KURVE (KAPITALVERLAUF)");
        chartTitleLabel.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 14));
        chartTitleLabel.setTextFill(Color.web("#00e5ff"));
        layout.getChildren().add(chartTitleLabel);

        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Trades");
        xAxis.setTickLabelFill(Color.web("#7e889a"));
        xAxis.setMinorTickVisible(false);

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Equity");
        yAxis.setTickLabelFill(Color.web("#7e889a"));
        yAxis.setForceZeroInRange(false);

        LineChart<Number, Number> equityChart = new LineChart<>(xAxis, yAxis);
        equityChart.setCreateSymbols(false);
        equityChart.setPrefHeight(200);
        equityChart.setMinHeight(200);
        equityChart.setMaxHeight(200);
        equityChart.setAnimated(false);
        equityChart.setStyle("-fx-background-color: transparent;");
        equityChart.setHorizontalGridLinesVisible(true);
        equityChart.setVerticalGridLinesVisible(false);
        VBox.setVgrow(equityChart, Priority.ALWAYS);

        // Generate curves
        double btEndBalance = cp.getBacktestPass().getBalance();
        double btStartBalance = btEndBalance - cp.getBtProfit();
        if (btStartBalance <= 0) {
            btStartBalance = 10000.0;
        }

        List<Double> btCurve = StrategyEvaluatorMetrics.generateSyntheticEquityCurve(btStartBalance, cp.getBtProfit(), cp.getBtTrades(), cp.getBtPf(), cp.getPassNumber());
        XYChart.Series<Number, Number> backtestSeries = new XYChart.Series<>();
        backtestSeries.setName("Backtest");
        for (int i = 0; i < btCurve.size(); i++) {
            backtestSeries.getData().add(new XYChart.Data<>(i, btCurve.get(i)));
        }
        equityChart.getData().add(backtestSeries);

        XYChart.Series<Number, Number> forwardSeries = null;
        if (cp.getForwardPass() != null) {
            double fwStartBalance = btCurve.get(btCurve.size() - 1);
            List<Double> fwCurve = StrategyEvaluatorMetrics.generateSyntheticEquityCurve(fwStartBalance, cp.getFwProfit(), cp.getFwTrades(), cp.getFwPf(), cp.getPassNumber() + 999);
            forwardSeries = new XYChart.Series<>();
            forwardSeries.setName("Forward");

            // Connect seamlessly
            int offset = btCurve.size() - 1;
            forwardSeries.getData().add(new XYChart.Data<>(offset, fwStartBalance));
            for (int j = 1; j < fwCurve.size(); j++) {
                forwardSeries.getData().add(new XYChart.Data<>(offset + j, fwCurve.get(j)));
            }
            equityChart.getData().add(forwardSeries);
        }

        layout.getChildren().add(equityChart);

        // --- Parameter section ---
        Label paramTitle = new Label("STRATEGIE-PARAMETER");
        paramTitle.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 14));
        paramTitle.setTextFill(Color.web("#00e5ff"));
        layout.getChildren().add(paramTitle);

        TableView<StrategyEvaluatorDialog.ParameterRow> paramTable = new TableView<>();
        paramTable.setStyle("-fx-background-color: transparent;");
        paramTable.setPrefHeight(200);
        VBox.setVgrow(paramTable, Priority.ALWAYS);

        TableColumn<StrategyEvaluatorDialog.ParameterRow, String> nameCol = new TableColumn<>("Parameter");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(350);

        TableColumn<StrategyEvaluatorDialog.ParameterRow, String> valCol = new TableColumn<>("Wert");
        valCol.setCellValueFactory(new PropertyValueFactory<>("value"));
        valCol.setPrefWidth(350);

        paramTable.getColumns().addAll(nameCol, valCol);

        List<StrategyEvaluatorDialog.ParameterRow> paramList = new ArrayList<>();
        Map<String, String> params = cp.getBacktestPass().getParameterValues();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            paramList.add(new StrategyEvaluatorDialog.ParameterRow(entry.getKey(), entry.getValue()));
        }
        paramTable.setItems(FXCollections.observableArrayList(paramList));
        layout.getChildren().add(paramTable);

        // --- Bottom bar ---
        Button closeBtn = new Button("Schließen");
        closeBtn.getStyleClass().add("button");
        closeBtn.setOnAction(e -> detailStage.close());

        HBox btnBox = new HBox(10, closeBtn);
        btnBox.setAlignment(Pos.CENTER_RIGHT);
        layout.getChildren().add(btnBox);

        // --- Scorecard WebView (Right Side) ---
        String symbolStr = parentView != null && parentView.getLastOptResult() != null ? parentView.getLastOptResult().getSymbol() : "EURUSD";
        String periodStr = parentView != null && parentView.getLastOptResult() != null ? parentView.getLastOptResult().getPeriod() : "H1";
        String expertStr = parentView != null && parentView.getLastOptResult() != null ? parentView.getLastOptResult().getExpert() : "";

        double sensitivScoreVal = -1.0;
        double kiScoreVal = -1.0;
        if (parentView != null && parentView.getSensitivityResults() != null) {
            for (com.backtester.report.SensitivityResult sr : parentView.getSensitivityResults()) {
                if (sr.getOriginalPass() != null && sr.getOriginalPass().getPassNumber() == cp.getPassNumber()) {
                    sensitivScoreVal = 100.0 - Math.max(sr.getOverallCV(), sr.getOverallCVFw());
                    String kiRes = sr.getKiResult();
                    if (kiRes != null && !kiRes.isEmpty()) {
                        try {
                            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,3})\\s*/\\s*100").matcher(kiRes);
                            if (m.find()) {
                                kiScoreVal = Double.parseDouble(m.group(1));
                            } else {
                                kiScoreVal = Double.parseDouble(kiRes.trim());
                            }
                        } catch (Exception ignored) {}
                    }
                    break;
                }
            }
        }

        String htmlContent = com.backtester.report.RobustnessScorecardGenerator.generateHtml(
            cp, expertStr, symbolStr, periodStr, fromDateStr, toDateStr, sensitivScoreVal, kiScoreVal
        );

        javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
        webView.getEngine().setOnAlert(event -> log.info("JS ALERT: " + event.getData()));
        webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.RUNNING || newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                try {
                    netscape.javascript.JSObject window = (netscape.javascript.JSObject) webView.getEngine().executeScript("window");
                    window.setMember("consoleBridge", new ConsoleLoggerBridge());
                } catch (Exception ex) {
                    // Ignore
                }
            }
        });
        webView.getEngine().getLoadWorker().exceptionProperty().addListener((obs, oldExc, newExc) -> {
            if (newExc != null) {
                log.error("WebView LoadWorker Exception: ", newExc);
            }
        });
        webView.getEngine().loadContent(htmlContent);

        VBox rightPane = new VBox(webView);
        VBox.setVgrow(webView, Priority.ALWAYS);
        rightPane.setStyle("-fx-background-color: #11141d;");

        ScrollPane leftScroll = new ScrollPane(layout);
        leftScroll.setFitToWidth(true);
        leftScroll.setStyle("-fx-background-color: transparent; -fx-background: #11141d; -fx-box-border: transparent;");

        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(leftScroll, rightPane);
        splitPane.setDividerPositions(0.55);
        splitPane.setStyle("-fx-background-color: #11141d; -fx-box-border: transparent;");

        Scene scene = new Scene(splitPane, 1500, 850);
        try {
            scene.getStylesheets().add(StrategyEvaluatorPassDetailsDialog.class.getResource("/css/antigravity.css").toExternalForm());
        } catch (Exception e) {
            // Ignore
        }
        detailStage.setScene(scene);

        // Style chart after elements are shown
        final XYChart.Series<Number, Number> finalFwSeries = forwardSeries;
        detailStage.setOnShown(e -> {
            if (backtestSeries.getNode() != null) {
                backtestSeries.getNode().setStyle("-fx-stroke: #00e676; -fx-stroke-width: 3px;");
            }
            if (finalFwSeries != null && finalFwSeries.getNode() != null) {
                finalFwSeries.getNode().setStyle("-fx-stroke: #00e5ff; -fx-stroke-width: 3px;");
            }
            javafx.scene.Node plotBg = equityChart.lookup(".chart-plot-background");
            if (plotBg != null) {
                plotBg.setStyle("-fx-background-color: #171b26; -fx-border-color: #3e4555; -fx-border-width: 1px;");
            }
        });

        detailStage.showAndWait();
    }

    public static void showRobustnessScorecardWebView(Window owner, CombinedPass cp, OptimizationView parentView) {
        Stage stage = new Stage();
        stage.setTitle("🛡️ Robustness Scorecard: Pass #" + cp.getPassNumber());
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.APPLICATION_MODAL);

        String fromDateStr = "Unbekannt";
        String toDateStr = "Unbekannt";
        if (parentView != null && parentView.getLastOptResult() != null) {
            fromDateStr = parentView.getLastOptResult().getFromDate();
            toDateStr = parentView.getLastOptResult().getToDate();
        }

        String symbolStr = parentView != null && parentView.getLastOptResult() != null ? parentView.getLastOptResult().getSymbol() : "EURUSD";
        String periodStr = parentView != null && parentView.getLastOptResult() != null ? parentView.getLastOptResult().getPeriod() : "H1";
        String expertStr = parentView != null && parentView.getLastOptResult() != null ? parentView.getLastOptResult().getExpert() : "";

        double sensitivScoreVal = -1.0;
        double kiScoreVal = -1.0;
        if (parentView != null && parentView.getSensitivityResults() != null) {
            for (com.backtester.report.SensitivityResult sr : parentView.getSensitivityResults()) {
                if (sr.getOriginalPass() != null && sr.getOriginalPass().getPassNumber() == cp.getPassNumber()) {
                    sensitivScoreVal = 100.0 - Math.max(sr.getOverallCV(), sr.getOverallCVFw());
                    String kiRes = sr.getKiResult();
                    if (kiRes != null && !kiRes.isEmpty()) {
                        try {
                            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,3})\\s*/\\s*100").matcher(kiRes);
                            if (m.find()) {
                                kiScoreVal = Double.parseDouble(m.group(1));
                            } else {
                                kiScoreVal = Double.parseDouble(kiRes.trim());
                            }
                        } catch (Exception ignored) {}
                    }
                    break;
                }
            }
        }

        String htmlContent = com.backtester.report.RobustnessScorecardGenerator.generateHtml(
            cp, expertStr, symbolStr, periodStr, fromDateStr, toDateStr, sensitivScoreVal, kiScoreVal
        );

        javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
        webView.getEngine().setOnAlert(event -> log.info("JS ALERT: " + event.getData()));
        webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.RUNNING || newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                try {
                    netscape.javascript.JSObject window = (netscape.javascript.JSObject) webView.getEngine().executeScript("window");
                    window.setMember("consoleBridge", new ConsoleLoggerBridge());
                } catch (Exception e) {
                    // Ignore if window is not ready yet
                }
            }
        });
        webView.getEngine().getLoadWorker().exceptionProperty().addListener((obs, oldExc, newExc) -> {
            if (newExc != null) {
                log.error("WebView LoadWorker Exception: ", newExc);
            }
        });
        webView.getEngine().loadContent(htmlContent);

        VBox box = new VBox(webView);
        VBox.setVgrow(webView, Priority.ALWAYS);

        Scene scene = new Scene(box, 750, 750);
        stage.setScene(scene);
        stage.show();
    }

    private static Label addMetricRow(GridPane grid, int row, String labelText, String valueText) {
        Label label = new Label(labelText);
        label.setTextFill(Color.web("#7e889a"));
        label.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 12));

        Label value = new Label(valueText);
        value.setTextFill(Color.web("#e6e9f0"));
        value.setFont(javafx.scene.text.Font.font("Segoe UI", 12));

        grid.add(label, 0, row);
        grid.add(value, 1, row);
        return value;
    }

    public static class ConsoleLoggerBridge {
        public void log(String text) { StrategyEvaluatorPassDetailsDialog.log.info("JS CONSOLE: " + text); }
        public void error(String text) { StrategyEvaluatorPassDetailsDialog.log.error("JS ERROR: " + text); }
    }
}
