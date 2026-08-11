package com.backtester.ui.javafx;

import com.backtester.engine.WorkflowEngine;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.SensitivityResult;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Strategy detail dialog for the Express Workflow results table / KI report bridge.
 */
public final class ExpressStrategyDetailDialog {
    private static final Logger log = LoggerFactory.getLogger(ExpressStrategyDetailDialog.class);

    private ExpressStrategyDetailDialog() {}

    public static void show(Window owner, CombinedPass cp, WorkflowEngine engine,
                            List<CombinedPass> allPasses, Collection<String> stylesheets) {
        if (cp == null || engine == null) return;

        Stage stage = new Stage();
        stage.setTitle("Strategie-Details - Pass " + cp.getPassNumber());
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }

        VBox box = new VBox(15);
        box.setPadding(new Insets(20));
        box.setStyle("-fx-background-color: #0b0d13;"); // Ensure dark background

        // --- Header ---
        Label titleLabel = new Label("STRATEGIE-DETAILS (PASS #" + cp.getPassNumber() + ")");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        titleLabel.setTextFill(Color.web("#00e5ff"));

        String fromDateStr = engine.getFromDate() != null ? engine.getFromDate().toString() : "Unbekannt";
        String toDateStr = engine.getToDate() != null ? engine.getToDate().toString() : "Unbekannt";
        Label subtitleLabel = new Label("Zeitraum: " + fromDateStr + " bis " + toDateStr);
        subtitleLabel.setFont(Font.font("Segoe UI", 16));
        subtitleLabel.setTextFill(Color.web("#7e889a"));

        box.getChildren().addAll(titleLabel, subtitleLabel);

        // --- Calculate Reference Values ---
        List<CombinedPass> allPassesLocal = allPasses != null ? new ArrayList<>(allPasses) : new ArrayList<>();
        int refTrades = 80;
        double refProfit = 500.0;
        if (!allPassesLocal.isEmpty()) {
            List<Integer> tradesList = allPassesLocal.stream()
                .map(CombinedPass::getBtTrades)
                .sorted()
                .collect(Collectors.toList());
            int size = tradesList.size();
            if (size % 2 == 0) {
                refTrades = (tradesList.get(size / 2 - 1) + tradesList.get(size / 2)) / 2;
            } else {
                refTrades = tradesList.get(size / 2);
            }
            refTrades = Math.max(30, refTrades);

            List<Double> profitList = allPassesLocal.stream()
                .map(CombinedPass::getBtProfit)
                .sorted()
                .collect(Collectors.toList());
            if (!profitList.isEmpty()) {
                int pSize = profitList.size();
                if (pSize % 2 == 0) {
                    refProfit = (profitList.get(pSize / 2 - 1) + profitList.get(pSize / 2)) / 2.0;
                } else {
                    refProfit = profitList.get(pSize / 2);
                }
            }
            refProfit = Math.max(100.0, refProfit);
        }

        // --- Cards Pane ---
        HBox cardsBox = new HBox(12);
        cardsBox.setAlignment(Pos.TOP_LEFT);

        // 1. Backtest Card
        VBox btCard = new VBox(10);
        btCard.setPadding(new Insets(12));
        btCard.setStyle("-fx-background-color: #171b26; -fx-border-color: #00e676; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px;");
        HBox.setHgrow(btCard, Priority.ALWAYS);
        Label btTitle = new Label("◀ BACKTEST METRIKEN");
        btTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        btTitle.setTextFill(Color.web("#00e676"));
        GridPane btGrid = new GridPane();
        btGrid.setHgap(15);
        btGrid.setVgap(6);
        addDetailMetricRow(btGrid, 0, "Nettoprofit:", String.format(Locale.US, "%.2f %s", cp.getBtProfit(), engine.getCurrency()));
        addDetailMetricRow(btGrid, 1, "Trades:", String.valueOf(cp.getBtTrades()));
        addDetailMetricRow(btGrid, 2, "Profit Factor:", String.format(Locale.US, "%.2f", cp.getBtPf()));
        addDetailMetricRow(btGrid, 3, "Max. Drawdown:", String.format(Locale.US, "%.2f%%", cp.getBtDd()));
        addDetailMetricRow(btGrid, 4, "Recovery Factor:", String.format(Locale.US, "%.2f", cp.getBtRecovery()));
        addDetailMetricRow(btGrid, 5, "Sharpe Ratio:", Double.isNaN(cp.getBtSharpe()) ? "—" : String.format(Locale.US, "%.2f", cp.getBtSharpe()));
        addDetailMetricRow(btGrid, 6, "Expected Payoff:", String.format(Locale.US, "%.2f", cp.getBtExpectedPayoff()));
        btCard.getChildren().addAll(btTitle, btGrid);

        // 2. Forward Card
        VBox fwCard = new VBox(10);
        fwCard.setPadding(new Insets(12));
        fwCard.setStyle("-fx-background-color: #171b26; -fx-border-color: #00e5ff; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px;");
        HBox.setHgrow(fwCard, Priority.ALWAYS);
        Label fwTitle = new Label("FORWARD METRIKEN ▶");
        fwTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        fwTitle.setTextFill(Color.web("#00e5ff"));
        GridPane fwGrid = new GridPane();
        fwGrid.setHgap(15);
        fwGrid.setVgap(6);

        if (cp.getForwardPass() != null) {
            addDetailMetricRow(fwGrid, 0, "Nettoprofit:", String.format(Locale.US, "%.2f %s", cp.getFwProfit(), engine.getCurrency()));
            addDetailMetricRow(fwGrid, 1, "Trades:", String.valueOf(cp.getFwTrades()));
            addDetailMetricRow(fwGrid, 2, "Profit Factor:", Double.isNaN(cp.getFwPf()) ? "—" : String.format(Locale.US, "%.2f", cp.getFwPf()));
            addDetailMetricRow(fwGrid, 3, "Max. Drawdown:", Double.isNaN(cp.getFwDd()) ? "—" : String.format(Locale.US, "%.2f%%", cp.getFwDd()));
            addDetailMetricRow(fwGrid, 4, "Recovery Factor:", Double.isNaN(cp.getFwRecovery()) ? "—" : String.format(Locale.US, "%.2f", cp.getFwRecovery()));
            addDetailMetricRow(fwGrid, 5, "Sharpe Ratio:", Double.isNaN(cp.getFwSharpe()) ? "—" : String.format(Locale.US, "%.2f", cp.getFwSharpe()));
            addDetailMetricRow(fwGrid, 6, "Expected Payoff:", Double.isNaN(cp.getFwExpectedPayoff()) ? "—" : String.format(Locale.US, "%.2f", cp.getFwExpectedPayoff()));
            fwCard.getChildren().addAll(fwTitle, fwGrid);
        } else {
            Label noFwLabel = new Label("Kein Forward-Test\ndurchgeführt.");
            noFwLabel.setTextFill(Color.web("#7e889a"));
            noFwLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
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
        evalTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        evalTitle.setTextFill(Color.web("#ffd740"));
        GridPane evalGrid = new GridPane();
        evalGrid.setHgap(15);
        evalGrid.setVgap(6);

        StrategyEvaluatorDialog.Evaluation eval = StrategyEvaluatorDialog.evaluatePass(cp, refTrades, refProfit);
        addDetailMetricRow(evalGrid, 0, "Score (Gewichtung):", String.format(Locale.US, "%.2f", cp.getScore()));
        addDetailMetricRow(evalGrid, 1, "Robustness-Index (RI):", String.format(Locale.US, "%.2f", StrategyEvaluatorDialog.calculateRobustnessIndex(cp, refTrades)));
        addDetailMetricRow(evalGrid, 2, "Forward-Konsistenz:", String.format(Locale.US, "%.2f", cp.getConsistency()));
        Label verdictVal = addDetailMetricRow(evalGrid, 3, "Analyse-Urteil:", eval.remark);
        verdictVal.setTextFill(Color.web(eval.color));
        verdictVal.setStyle("-fx-font-weight: bold;");
        verdictVal.setWrapText(true);
        verdictVal.setMaxWidth(220);
        
        evalCard.getChildren().addAll(evalTitle, evalGrid);

        cardsBox.getChildren().addAll(btCard, fwCard, evalCard);
        box.getChildren().add(cardsBox);

        // --- Equity Chart ---
        Label chartTitleLabel = new Label("EQUITY-KURVE (KAPITALVERLAUF)");
        chartTitleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17));
        chartTitleLabel.setTextFill(Color.web("#00e5ff"));
        box.getChildren().add(chartTitleLabel);

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
        equityChart.setPrefHeight(260);
        equityChart.setMinHeight(260);
        equityChart.setMaxHeight(260);
        equityChart.setAnimated(false);
        equityChart.setStyle("-fx-background-color: transparent;");
        equityChart.setHorizontalGridLinesVisible(true);
        equityChart.setVerticalGridLinesVisible(false);

        double btEndBalance = cp.getBacktestPass().getBalance();
        double btStartBalance = btEndBalance - cp.getBtProfit();
        if (btStartBalance <= 0) {
            btStartBalance = 10000.0;
        }

        List<Double> btCurve = StrategyEvaluatorDialog.generateSyntheticEquityCurve(btStartBalance, cp.getBtProfit(), cp.getBtTrades(), cp.getBtPf(), cp.getPassNumber());
        XYChart.Series<Number, Number> backtestSeries = new XYChart.Series<>();
        backtestSeries.setName("Backtest");
        for (int i = 0; i < btCurve.size(); i++) {
            backtestSeries.getData().add(new XYChart.Data<>(i, btCurve.get(i)));
        }
        equityChart.getData().add(backtestSeries);

        XYChart.Series<Number, Number> forwardSeries = null;
        if (cp.getForwardPass() != null) {
            double fwStartBalance = btCurve.get(btCurve.size() - 1);
            List<Double> fwCurve = StrategyEvaluatorDialog.generateSyntheticEquityCurve(fwStartBalance, cp.getFwProfit(), cp.getFwTrades(), cp.getFwPf(), cp.getPassNumber() + 999);
            forwardSeries = new XYChart.Series<>();
            forwardSeries.setName("Forward");
            
            int offset = btCurve.size() - 1;
            forwardSeries.getData().add(new XYChart.Data<>(offset, fwStartBalance));
            for (int j = 1; j < fwCurve.size(); j++) {
                forwardSeries.getData().add(new XYChart.Data<>(offset + j, fwCurve.get(j)));
            }
            equityChart.getData().add(forwardSeries);
        }
        box.getChildren().add(equityChart);

        // --- Robustness Test breakdown with line charts ---
        SensitivityResult match = null;
        if (engine.getSensitivityResults() != null) {
            for (SensitivityResult sr : engine.getSensitivityResults()) {
                if (sr.getOriginalPass() != null && sr.getOriginalPass().getPassNumber() == cp.getPassNumber()) {
                    match = sr;
                    break;
                }
            }
        }

        if (match == null) {
            Label noStabilityLbl = new Label("🛡️ PARAMETER-SENSITIVITÄT & KINNLINIEN");
            noStabilityLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17));
            noStabilityLbl.setTextFill(Color.web("#00e5ff"));
            
            Label noStabilityDesc = new Label("Robustheits-Kennlinien sind erst nach Durchführung von Schritt 4 (Robustness Test (CV)) im Workflow verfügbar.");
            noStabilityDesc.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 15px; -fx-padding: 0 0 15 0;");
            
            box.getChildren().addAll(noStabilityLbl, noStabilityDesc);
        } else {
            Label btCvLabel = new Label("🛡️ Backtest (in-sample) Parameter-Robustheit & Kennlinien:");
            btCvLabel.setTextFill(Color.WHITE);
            btCvLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17));
            
            TableView<Map.Entry<String, Double>> btCvTable = buildCvBreakdownTable(
                    match.getParameterCVs(),
                    match.getParameterCurves(),
                    match.getOriginalPass().getBacktestPass().getParameterValues(),
                    "#00e5ff");
            
            box.getChildren().addAll(btCvLabel, btCvTable);

            if (match.hasForwardCV()) {
                Label fwCvLabel = new Label("🛡️ Forward (out-of-sample) Parameter-Robustheit & Kennlinien:");
                fwCvLabel.setTextFill(Color.WHITE);
                fwCvLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17));
                
                TableView<Map.Entry<String, Double>> fwCvTable = buildCvBreakdownTable(
                        match.getParameterCVsFw(),
                        match.getParameterCurvesFw(),
                        match.getOriginalPass().getBacktestPass().getParameterValues(),
                        "#ff9100");
                
                box.getChildren().addAll(fwCvLabel, fwCvTable);
            }

            // Detailed interpretation and verdict box
            Label explanationTitle = new Label("Ausführliche Erklärung zur Interpretation");
            explanationTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
            explanationTitle.setTextFill(Color.web("#00e5ff"));
            
            String interpretationText = 
                "Die Sensitivitätsanalyse testet, wie 'zerbrechlich' deine Strategie ist.\n" +
                "Dazu wird jeder Parameter (z.B. StopLoss, Takeprofit) in kleinen Schritten um seinen optimierten Wert herum verschoben. " +
                "Anschließend messen wir, wie stark sich der Profit durch diese kleinen Änderungen verändert.\n\n" +
                "Der CV-Wert (Coefficient of Variation) ist das Maß für diese Schwankung:\n" +
                "• Unter 30% (Grün): Der Parameter ist extrem stabil. Wenn der Markt sich leicht ändert, bleibt dein Profit weitgehend gleich.\n" +
                "• 30% bis 60% (Gelb): Normale Schwankung. Die Strategie bleibt vermutlich noch profitabel.\n" +
                "• Über 60% (Rot): Gefahr! Die Strategie ist ein 'One-Hit-Wonder'. Ein winziger Unterschied im Markt, und die Strategie stürzt ab (Curve-Fitted).\n\n";
                
            double worstCv = match.getOverallCV();
            if (match.hasForwardCV() && match.getOverallCVFw() > worstCv) {
                worstCv = match.getOverallCVFw();
            }
            
            String passVerdict = "";
            Color verdictColor = Color.WHITE;
            if (worstCv < 30.0) {
                passVerdict = String.format("FAZIT ZU DIESEM PASS:\nDein schlechtester CV liegt bei %.2f %%. Dies ist ein exzellenter Wert! Die Parameter sind extrem robust.", worstCv);
                verdictColor = Color.web("#00e676");
            } else if (worstCv <= 60.0) {
                passVerdict = String.format("FAZIT ZU DIESEM PASS:\nDein schlechtester CV liegt bei %.2f %%. Das ist solide. Die Strategie wird nicht sofort zusammenbrechen, wenn sich die Marktbedingungen leicht ändern.", worstCv);
                verdictColor = Color.web("#ffd740");
            } else {
                passVerdict = String.format("FAZIT ZU DIESEM PASS:\nACHTUNG! Der schlechteste CV liegt bei gigantischen %.2f %%! Dieser Pass ist zu stark überoptimiert (Curve-Fitted). Im Live-Handel wird er höchstwahrscheinlich Verluste einfahren.", worstCv);
                verdictColor = Color.web("#ff3b30");
            }
            
            Label interpretationLabel = new Label(interpretationText);
            interpretationLabel.setWrapText(true);
            interpretationLabel.setStyle("-fx-text-fill: #b4bac8; -fx-font-size: 15px;");
            
            Label verdictLabel = new Label(passVerdict);
            verdictLabel.setWrapText(true);
            verdictLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17));
            verdictLabel.setTextFill(verdictColor);
            
            VBox expBox = new VBox(10, explanationTitle, interpretationLabel, verdictLabel);
            expBox.setStyle("-fx-background-color: #1a1d27; -fx-padding: 15; -fx-background-radius: 8; -fx-border-color: #2a2d3a; -fx-border-width: 1; -fx-border-radius: 8;");
            
            box.getChildren().add(expBox);
        }

        // --- Optimized Strategy Settings ---
        Label paramLabel = new Label("⚙️ STRATEGIE-PARAMETER (EINSTELLUNGEN)");
        paramLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17));
        paramLabel.setTextFill(Color.web("#00e5ff"));
        box.getChildren().add(paramLabel);

        TableView<Map.Entry<String, String>> paramTable = new TableView<>();
        paramTable.setStyle("-fx-background-color: transparent; -fx-font-size: 14px;");
        paramTable.setPrefHeight(280);

        TableColumn<Map.Entry<String, String>, String> sParamCol = new TableColumn<>("Parameter");
        sParamCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getKey()));
        sParamCol.setPrefWidth(350);

        TableColumn<Map.Entry<String, String>, String> sValCol = new TableColumn<>("Wert");
        sValCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getValue()));
        sValCol.setPrefWidth(350);

        paramTable.getColumns().addAll(sParamCol, sValCol);
        if (cp.getBacktestPass() != null && cp.getBacktestPass().getParameterValues() != null) {
            paramTable.getItems().addAll(cp.getBacktestPass().getParameterValues().entrySet());
        }
        paramTable.setSelectionModel(null);
        box.getChildren().add(paramTable);

        // --- Bottom bar ---
        Button closeBtn = new Button("Schließen");
        closeBtn.getStyleClass().add("button");
        closeBtn.setOnAction(e -> stage.close());

        HBox btnBox = new HBox(10, closeBtn);
        btnBox.setAlignment(Pos.CENTER_RIGHT);
        box.getChildren().add(btnBox);

        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(box);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: #0b0d13; -fx-box-border: transparent;");

        // --- Scorecard WebView (Right Side) ---
        String symbolStr = engine.getSymbol() != null ? engine.getSymbol() : "EURUSD";
        String periodStr = engine.getPeriod() != null ? engine.getPeriod() : "H1";
        String expertStr = engine.getExpert() != null ? engine.getExpert() : "";

        // --- Compute Sensitiv + KI Scores for the scorecard circles ---
        double sensitivScoreVal = -1.0;
        double kiScoreVal = -1.0;
        if (match != null) {
            double worstCv = Math.max(match.getOverallCV(), match.getOverallCVFw());
            sensitivScoreVal = Math.max(0, Math.min(100, 100.0 - worstCv));
            String kiText = match.getKiResult();
            if (kiText != null && !kiText.isEmpty()) {
                try {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,3})\\s*/\\s*100").matcher(kiText);
                    if (m.find()) {
                        kiScoreVal = Double.parseDouble(m.group(1));
                    } else {
                        kiScoreVal = Double.parseDouble(kiText.trim());
                    }
                } catch (Exception ignored) {}
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
        rightPane.setStyle("-fx-background-color: #0b0d13;");

        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(scroll, rightPane);
        splitPane.setDividerPositions(0.55);
        splitPane.setStyle("-fx-background-color: #0b0d13; -fx-box-border: transparent;");

        Scene scene = new Scene(splitPane, 1500, 950);
        stage.setScene(scene);

        if (stylesheets != null && !stylesheets.isEmpty()) {
            scene.getStylesheets().addAll(stylesheets);
        }

        // Style chart after elements are shown
        final XYChart.Series<Number, Number> finalFwSeries = forwardSeries;
        stage.setOnShown(e -> {
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

        stage.showAndWait();
    }

    public static void showRobustnessScorecardWebView(Window owner, CombinedPass cp, WorkflowEngine engine) {
        if (cp == null || engine == null) return;
        Stage stage = new Stage();
        stage.setTitle("🛡️ Robustness Scorecard: Pass #" + cp.getPassNumber());
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.APPLICATION_MODAL);

        String fromDateStr = engine.getFromDate() != null ? engine.getFromDate().toString() : "Unbekannt";
        String toDateStr = engine.getToDate() != null ? engine.getToDate().toString() : "Unbekannt";
        String symbolStr = engine.getSymbol() != null ? engine.getSymbol() : "EURUSD";
        String periodStr = engine.getPeriod() != null ? engine.getPeriod() : "H1";
        String expertStr = engine.getExpert() != null ? engine.getExpert() : "";

        // --- Compute Sensitiv + KI Scores for the scorecard circles ---
        double sensitivScoreVal2 = -1.0;
        double kiScoreVal2 = -1.0;
        if (engine.getSensitivityResults() != null) {
            for (SensitivityResult sr : engine.getSensitivityResults()) {
                if (sr.getOriginalPass() != null && sr.getOriginalPass().getPassNumber() == cp.getPassNumber()) {
                    double worstCv = Math.max(sr.getOverallCV(), sr.getOverallCVFw());
                    sensitivScoreVal2 = Math.max(0, Math.min(100, 100.0 - worstCv));
                    String kiText = sr.getKiResult();
                    if (kiText != null && !kiText.isEmpty()) {
                        try {
                            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,3})\\s*/\\s*100").matcher(kiText);
                            if (m.find()) {
                                kiScoreVal2 = Double.parseDouble(m.group(1));
                            } else {
                                kiScoreVal2 = Double.parseDouble(kiText.trim());
                            }
                        } catch (Exception ignored) {}
                    }
                    break;
                }
            }
        }

        String htmlContent = com.backtester.report.RobustnessScorecardGenerator.generateHtml(
            cp, expertStr, symbolStr, periodStr, fromDateStr, toDateStr, sensitivScoreVal2, kiScoreVal2
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

    private static Label addDetailMetricRow(GridPane grid, int row, String labelText, String valueText) {
        Label label = new Label(labelText);
        label.setTextFill(Color.web("#7e889a"));
        label.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        
        Label value = new Label(valueText);
        value.setTextFill(Color.web("#e6e9f0"));
        value.setFont(Font.font("Segoe UI", 15));
        
        grid.add(label, 0, row);
        grid.add(value, 1, row);
        return value;
    }

    @SuppressWarnings("unchecked")
    private static TableView<Map.Entry<String, Double>> buildCvBreakdownTable(
            Map<String, Double> cvMap,
            Map<String, List<com.backtester.report.SensitivityResult.DataPoint>> curves,
            Map<String, String> baseValues,
            String accentColor) {

        TableView<Map.Entry<String, Double>> cvTable = new TableView<>();
        TableColumn<Map.Entry<String, Double>, String> paramCol = new TableColumn<>("Parameter");
        paramCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getKey()));

        TableColumn<Map.Entry<String, Double>, VBox> valCol = new TableColumn<>("CV (%)");
        valCol.setCellValueFactory(c -> {
            String pName = c.getValue().getKey();
            double cv = c.getValue().getValue();
            List<com.backtester.report.SensitivityResult.DataPoint> curveData =
                    curves != null ? curves.get(pName) : null;

            VBox calcBox = new VBox(5);
            calcBox.setAlignment(Pos.CENTER_LEFT);
            calcBox.setPadding(new Insets(0, 0, 0, 10));

            Label cvValueLabel = new Label(String.format("%.2f %%", cv));
            cvValueLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17));
            cvValueLabel.setTextFill(Color.web(accentColor));

            Button infoBtn = new Button("ℹ");
            infoBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + accentColor +
                    "; -fx-cursor: hand; -fx-border-color: " + accentColor +
                    "; -fx-border-radius: 15px; -fx-font-weight: bold; -fx-padding: 0 5 0 5;");

            HBox topBox = new HBox(10, cvValueLabel, infoBtn);
            topBox.setAlignment(Pos.CENTER_LEFT);

            if (curveData != null && !curveData.isEmpty()) {
                double sum = curveData.stream().mapToDouble(d -> d.profit).sum();
                double mean = sum / curveData.size();
                double varianceSum = 0;
                for (com.backtester.report.SensitivityResult.DataPoint dp : curveData) {
                    varianceSum += Math.pow(dp.profit - mean, 2);
                }
                double stdDev = Math.sqrt(varianceSum / curveData.size());

                infoBtn.setOnAction(e -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Erklärung: Parameter Robustness");
                    alert.setHeaderText("Was bedeutet der CV-Wert für " + pName + "?");

                    String explanation = String.format(Locale.US,
                        "Der CV-Wert (Coefficient of Variation) zeigt an, wie stark der Profit schwankt, wenn sich der Parameter '%s' leicht ändert.\n\n" +
                        "Ein kleiner CV-Wert bedeutet, dass die Strategie sehr stabil (robust) ist.\n" +
                        "Ein hoher Wert zeigt an, dass schon winzige Änderungen am Parameter den Profit massiv einbrechen lassen können – die Strategie ist hier anfällig und überoptimiert!\n\n" +
                        "--- BERECHNUNG ---\n\n" +
                        "1. Durchschnittlicher Profit der Varianten (Mean):\n" +
                        "In unseren Tests lag der Profit für diesen Parameter im Schnitt bei %.2f USD.\n\n" +
                        "2. Schwankung (Standardabweichung / StdDev):\n" +
                        "Der Profit schwankte im Schnitt um %.2f USD.\n\n" +
                        "3. Die Formel (CV):\n" +
                        "Wir teilen die Schwankung durch den ORIGINALEN Basis-Profit der optimierten Strategie und rechnen mal 100:\n" +
                        "CV = (StdDev / |Basis-Profit|) * 100\n" +
                        "CV = (%.2f / %.2f) * 100 = %.2f %%\n\n" +
                        "Hinweis: Wir verwenden den Basis-Profit statt des Durchschnitts, weil der klassische CV bei Profiten nahe Null (wo positive und negative Ergebnisse gemischt werden) unsinnig hohe Werte liefert.\n\n" +
                        "Faustregel:\n" +
                        "• Unter 20%: Sehr robust. Der Parameter ist stabil.\n" +
                        "• 20% - 50%: Akzeptabel. Es gibt Schwankungen, aber im Rahmen.\n" +
                        "• Über 50%: Gefährlich! Die Strategie ist hier eine 'Klippe' und extrem riskant.",
                        pName, mean, stdDev, stdDev, Math.abs(mean) > 0.01 ? Math.abs(mean) : 1.0, cv
                    );

                    Label expLabel = new Label(explanation);
                    expLabel.setWrapText(true);
                    expLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13px;");
                    alert.getDialogPane().setContent(expLabel);
                    alert.getDialogPane().setPrefWidth(550);
                    alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
                    try {
                        if (cvTable.getScene() != null && !cvTable.getScene().getStylesheets().isEmpty()) {
                            alert.getDialogPane().getStylesheets().addAll(cvTable.getScene().getStylesheets());
                        }
                    } catch (Exception ignored) {}
                    alert.getDialogPane().setStyle("-fx-base: #11141d; -fx-background-color: #11141d; -fx-text-fill: white;");
                    alert.showAndWait();
                });

                Label formulaLabel = new Label("CV = (StdDev / |Basis-Profit|) * 100");
                formulaLabel.setFont(Font.font("Segoe UI", 12));
                formulaLabel.setTextFill(Color.web("#8093a5"));

                Label calcLabel = new Label(String.format(Locale.US,
                        "= (%.2f / |Basis-Profit|) * 100 = %.2f%%", stdDev, cv));
                calcLabel.setFont(Font.font("Segoe UI", 12));
                calcLabel.setTextFill(Color.web("#8093a5"));

                calcBox.getChildren().addAll(topBox, formulaLabel, calcLabel);
            } else {
                calcBox.getChildren().add(topBox);
            }
            return new SimpleObjectProperty<>(calcBox);
        });
        valCol.setPrefWidth(200);

        TableColumn<Map.Entry<String, Double>, VBox> chartCol = new TableColumn<>("Curve");
        chartCol.setCellValueFactory(c -> {
            String pName = c.getValue().getKey();
            List<com.backtester.report.SensitivityResult.DataPoint> curveData =
                    curves != null ? curves.get(pName) : null;
            if (curveData == null || curveData.isEmpty()) {
                return new SimpleObjectProperty<>(null);
            }

            String baseValueStr = baseValues != null ? baseValues.get(pName) : null;
            double baseValue = 0;
            try { if (baseValueStr != null) baseValue = Double.parseDouble(baseValueStr); } catch (Exception ignored) {}
            final double finalBaseValue = baseValue;

            double minX = curveData.get(0).paramValue;
            double maxX = curveData.get(curveData.size() - 1).paramValue;
            double xPadding = (maxX - minX) * 0.05;
            if (xPadding == 0) xPadding = 1;

            NumberAxis xAxis = new NumberAxis();
            xAxis.setTickLabelsVisible(true); xAxis.setOpacity(1);
            xAxis.setTickMarkVisible(true); xAxis.setMinorTickVisible(false);
            xAxis.setTickLabelFill(Color.WHITE);
            xAxis.setAutoRanging(false);
            xAxis.setLowerBound(minX - xPadding);
            xAxis.setUpperBound(maxX + xPadding);

            NumberAxis yAxis = new NumberAxis();
            yAxis.setTickLabelsVisible(false); yAxis.setOpacity(0);
            yAxis.setTickMarkVisible(false); yAxis.setMinorTickVisible(false);

            double minY = curveData.stream().mapToDouble(d -> d.profit).min().orElse(0);
            double maxY = curveData.stream().mapToDouble(d -> d.profit).max().orElse(1);
            double yPadding = (maxY - minY) * 0.1;
            if (yPadding == 0) yPadding = 1;
            yAxis.setAutoRanging(false);
            yAxis.setLowerBound(minY - yPadding);
            yAxis.setUpperBound(maxY + yPadding);

            LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
            chart.setCreateSymbols(true);
            chart.setLegendVisible(false);
            chart.setAnimated(false);
            chart.setPrefHeight(130); chart.setMinHeight(130); chart.setMaxHeight(130);
            chart.setPrefWidth(400);
            chart.setHorizontalGridLinesVisible(false);
            chart.setVerticalGridLinesVisible(false);

            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            com.backtester.report.SensitivityResult.DataPoint closestToBase = null;
            double minDiff = Double.MAX_VALUE;

            for (com.backtester.report.SensitivityResult.DataPoint dp : curveData) {
                series.getData().add(new XYChart.Data<>(dp.paramValue, dp.profit));
                double diff = Math.abs(dp.paramValue - finalBaseValue);
                if (diff < minDiff) { minDiff = diff; closestToBase = dp; }
            }
            chart.getData().add(series);
            final com.backtester.report.SensitivityResult.DataPoint finalClosest = closestToBase;

            chart.lookup(".chart-plot-background").setStyle("-fx-background-color: transparent;");
            chart.setStyle("-fx-padding: 0; -fx-background-color: transparent;");

            Platform.runLater(() -> {
                if (series.getNode() != null) {
                    series.getNode().setStyle("-fx-stroke: " + accentColor + "; -fx-stroke-width: 4px;");
                }
                for (XYChart.Data<Number, Number> data : series.getData()) {
                    if (data.getNode() != null) {
                        boolean isBase = finalClosest != null && data.getXValue().doubleValue() == finalClosest.paramValue;
                        if (isBase) {
                            data.getNode().setStyle("-fx-background-color: #ff3d00, white; -fx-background-insets: 0, 2; -fx-background-radius: 8px; -fx-padding: 6px;");
                        } else {
                            data.getNode().setStyle("-fx-background-color: " + accentColor + ", #0b0d13; -fx-background-insets: 0, 2; -fx-background-radius: 4px; -fx-padding: 3px;");
                        }
                    }
                }
            });

            double stepVal = curveData.size() > 1 ? (maxX - minX) / (curveData.size() - 1) : 0;
            String infoTxt = String.format(Locale.US, "Start: %.4f | Step: %.4f | End: %.4f", minX, stepVal, maxX)
                                   .replaceAll("0+ \\|", " |").replaceAll("\\. \\|", " |");
            Label infoLabel = new Label(infoTxt);
            infoLabel.setTextFill(Color.web("#8093a5"));
            infoLabel.setFont(Font.font("Segoe UI", 13));

            VBox chartBox = new VBox(5, chart, infoLabel);
            chartBox.setAlignment(Pos.CENTER);
            return new SimpleObjectProperty<>(chartBox);
        });

        chartCol.setCellFactory(col -> new TableCell<Map.Entry<String, Double>, VBox>() {
            @Override
            protected void updateItem(VBox item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    setGraphic(item);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        cvTable.getColumns().addAll(paramCol, valCol, chartCol);
        if (cvMap != null) {
            for (Map.Entry<String, Double> entry : cvMap.entrySet()) {
                cvTable.getItems().add(new java.util.AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
            }
        }
        cvTable.setStyle("-fx-background-color: transparent; -fx-font-size: 14px;");
        cvTable.setPrefHeight(380);
        cvTable.setFixedCellSize(160);
        cvTable.setSelectionModel(null);
        cvTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        return cvTable;
    }

    public static CombinedPass findPassByNumber(WorkflowEngine engine, int passNum) {
        // 1. Check selected diverse passes (steps 3-5)
        for (CombinedPass cp : engine.getSelectedDiversePasses()) {
            if (cp.getPassNumber() == passNum) {
                return cp;
            }
        }
        // 2. Check final selected passes (step 6)
        for (CombinedPass cp : engine.getFinalSelectedPasses()) {
            if (cp.getPassNumber() == passNum) {
                return cp;
            }
        }
        // 3. Check all optimization passes (step 2)
        if (engine.getOptResult() != null) {
            List<CombinedPass> allPasses = engine.getOptResult().buildCombinedPasses(engine.getForwardMode() > 0, engine.loadScoreWeightsFromDb());
            for (CombinedPass cp : allPasses) {
                if (cp.getPassNumber() == passNum) {
                    return cp;
                }
            }
        }
        return null;
    }


    /**
     * JS bridge for KI-report WebView click handling. Does not hold a WorkflowView reference.
     */
    public static class JavaBridge {
        private final WorkflowEngine engine;
        private final Supplier<Window> ownerSupplier;
        private final Supplier<List<CombinedPass>> allPassesSupplier;
        private final Supplier<Collection<String>> stylesheetsSupplier;
        private final Consumer<String> missingPassLogger;

        public JavaBridge(WorkflowEngine engine,
                          Supplier<Window> ownerSupplier,
                          Supplier<List<CombinedPass>> allPassesSupplier,
                          Supplier<Collection<String>> stylesheetsSupplier,
                          Consumer<String> missingPassLogger) {
            this.engine = engine;
            this.ownerSupplier = ownerSupplier;
            this.allPassesSupplier = allPassesSupplier;
            this.stylesheetsSupplier = stylesheetsSupplier;
            this.missingPassLogger = missingPassLogger;
        }

        public void showPass(int passNum) {
            Platform.runLater(() -> {
                CombinedPass cp = findPassByNumber(engine, passNum);
                if (cp != null) {
                    Window owner = ownerSupplier != null ? ownerSupplier.get() : null;
                    List<CombinedPass> passes = allPassesSupplier != null ? allPassesSupplier.get() : List.of();
                    Collection<String> sheets = stylesheetsSupplier != null ? stylesheetsSupplier.get() : List.of();
                    ExpressStrategyDetailDialog.show(owner, cp, engine, passes, sheets);
                } else if (missingPassLogger != null) {
                    missingPassLogger.accept("Pass " + passNum + " nicht in den aktuellen Workflow-Ergebnissen gefunden.");
                }
            });
        }
    }

    public static class ConsoleLoggerBridge {
        public void log(String text) { log.info("JS CONSOLE: " + text); }
        public void error(String text) { log.error("JS ERROR: " + text); }
    }
}
