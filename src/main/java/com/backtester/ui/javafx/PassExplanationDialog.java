package com.backtester.ui.javafx;

import com.backtester.report.OptimizationResult.CombinedPass;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.Locale;

/**
 * Dialog explaining consistency/score and metrics for a combined optimization pass.
 */
public final class PassExplanationDialog {

    private PassExplanationDialog() {}

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

    public static void show(javafx.stage.Window owner, CombinedPass sel, String dateSubtitleOptional) {
        if (sel == null) return;
        javafx.stage.Stage dialog = new javafx.stage.Stage();
        dialog.setTitle("Auswertung: Pass #" + sel.getPassNumber());
        dialog.initModality(javafx.stage.Modality.NONE);
        if (owner != null) {
            dialog.initOwner(owner);
        }

        VBox rootBox = new VBox(20);
        rootBox.setPadding(new Insets(25));
        rootBox.setStyle("-fx-background-color: #0d0f17;");

        // ── Title ──
        Label title = new Label("🔬 Analyse der Strategie (Pass #" + sel.getPassNumber() + ")");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        title.setTextFill(Color.web("#00e5ff"));

        // ── Left Column: Explanations ──
        // Consistency Explanation
        double cons = sel.getConsistency();
        String consLevel;
        Color consColor;
        String consDesc;

        if (cons < 0.3) {
            consLevel = "Ungenügend / Gefährlich";
            consColor = Color.web("#ff3b30");
            consDesc = "Die Strategie macht im Forward-Test (unbekannte Daten) fast gar keinen Gewinn mehr im Vergleich zum Backtest.\n\n" +
                       "Ursache: Höchstwahrscheinlich 'Curve Fitting' (Überoptimierung). Parameter extrem an Vergangenheit angepasst. Hände weg!";
        } else if (cons < 0.6) {
            consLevel = "Schwach";
            consColor = Color.web("#ff9500");
            consDesc = "Der Forward-Test ist profitabel, erreicht aber nur einen Bruchteil des Backtest-Profits.\n\n" +
                       "Bedeutung: Strategie hat etwas Robustheit, verliert in unbekannten Märkten aber deutlich an Leistung. Vorsichtig agieren.";
        } else if (cons < 0.9) {
            consLevel = "Gut";
            consColor = Color.web("#4cd964");
            consDesc = "Sehr solide! Die Strategie erzielt im Forward-Test fast genauso viel Profit wie im Backtest.\n\n" +
                       "Bedeutung: Parameter sind robust. Strategie hat echte Marktineffizienzen gefunden und nicht nur Historie auswendig gelernt.";
        } else if (cons <= 1.2) {
            consLevel = "Hervorragend (Perfekt)";
            consColor = Color.web("#00e676");
            consDesc = "Die Performance im Forward-Test entspricht exakt dem Backtest oder ist sogar leicht besser.\n\n" +
                       "Bedeutung: Perfekte Konsistenz. Dies ist ein hochgradig robustes Setup.";
        } else {
            consLevel = "Ungewöhnlich Hoch";
            consColor = Color.web("#ffd740");
            consDesc = "Der Forward-Test hat deutlich MEHR Profit gemacht als der Backtest.\n\n" +
                       "Bedeutung: Positiv, kann aber bedeuten, dass die Forward-Phase zufällig sehr günstig war. Dennoch besser als Verlust!";
        }

        Label consTitle = new Label("Konsistenz: " + String.format("%.2f", cons) + " (" + consLevel + ")");
        consTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        consTitle.setTextFill(consColor);

        String consCalc = "Berechnung: Forward Profit / Backtest Profit\n" +
                          String.format("=> %.2f / %.2f = %.2f\n", (sel.getForwardPass() != null ? sel.getFwProfit() : 0.0), sel.getBtProfit(), cons) +
                          "(Wert 1.0 bedeutet 100% identische Performance).\n\n" + consDesc;

        Label consText = new Label(consCalc);
        consText.setWrapText(true);
        consText.setStyle("-fx-text-fill: #b4bac8; -fx-font-size: 13px;");

        VBox consBox = new VBox(8, consTitle, consText);
        consBox.setStyle("-fx-background-color: #171b26; -fx-padding: 15; -fx-background-radius: 8; -fx-border-color: #2e3543; -fx-border-width: 1px; -fx-border-radius: 8;");

        // Score Explanation
        double score = sel.getScore();
        String scoreLevel;
        Color scoreColor;
        String scoreDesc;

        if (score >= 80) {
            scoreLevel = "Exzellent (Top Tier)";
            scoreColor = Color.web("#00e676");
            scoreDesc = "Diese Strategie gehört zu den besten der Optimierung. Sie kombiniert hohen Profit mit exzellenter Konsistenz und moderatem Drawdown.";
        } else if (score >= 60) {
            scoreLevel = "Solide (Gute Wahl)";
            scoreColor = Color.web("#4cd964");
            scoreDesc = "Gute, brauchbare Strategie. Kleine Schwächen bei Drawdown oder Konsistenz, aber vielversprechend.";
        } else if (score >= 40) {
            scoreLevel = "Mittelmäßig";
            scoreColor = Color.web("#ffd740");
            scoreDesc = "Potenzial vorhanden, aber deutliche Schwächen (z.B. wenige Trades oder hohe Drawdowns).";
        } else {
            scoreLevel = "Mangelhaft (Ausschuss)";
            scoreColor = Color.web("#ff3b30");
            scoreDesc = "Durchgefallen. Profit zu gering, Drawdowns zu hoch oder Konsistenz eingebrochen.";
        }

        Label scoreTitle = new Label("Gesamt-Score: " + String.format(Locale.US, "%.1f", score) + " / 100 (" + scoreLevel + ")");
        scoreTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        scoreTitle.setTextFill(scoreColor);

        String scoreCalc = "Was bedeutet dieser Wert?\n" +
                           "Der Gesamt-Score (0-100) bewertet auf einen Blick, wie ausgewogen und robust eine Strategie im Vergleich zu allen anderen Durchgängen dieser Optimierung abschneidet. Ein hoher Score zeigt, dass die Strategie nicht nur hohen Profit erzielt, sondern auch ein gesundes Verhältnis von Risiko (geringer Drawdown), Stabilität (hoher Profit Factor) und Konsistenz zwischen Backtest und Forward-Phase aufweist. Er schützt vor Überoptimierung, indem er reine Gewinn-Ausreißer abwertet, wenn diese bei Marktveränderungen einbrechen. Kurz gesagt: Er filtert die stabilsten Allrounder heraus.\n\n" +
                           "⚠️ HINWEIS ZUR KENNLINIE:\n" +
                           "Dieser Gesamt-Score bewertet ausschließlich die endgültigen Kennzahlen am Schluss. Er betrachtet NICHT den Verlauf der Kennlinie (Equity-Kurve). Nur der Robustness Score analysiert den tatsächlichen Verlauf der Kennlinie per linearer Regression (R²-Stabilität), um Glückstreffer oder instabile Verläufe aufzudecken.\n\n" +
                           "Berechnung & Details:\n" +
                           "Er bewertet Profit, Drawdown, PF und Konsistenz gemeinsam basierend auf deinen Filter-Gewichtungen.\n\n" + scoreDesc + "\n\n" +
                           "--- GENAUE BERECHNUNG ---\n" + sel.getScoreDetails();

        Label scoreText = new Label(scoreCalc);
        scoreText.setWrapText(true);
        scoreText.setStyle("-fx-text-fill: #b4bac8; -fx-font-size: 13px; -fx-font-family: monospace;");

        VBox scoreBox = new VBox(8, scoreTitle, scoreText);
        scoreBox.setStyle("-fx-background-color: #171b26; -fx-padding: 15; -fx-background-radius: 8; -fx-border-color: #2e3543; -fx-border-width: 1px; -fx-border-radius: 8;");

        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(scoreBox);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #0d0f17; -fx-border-color: transparent;");
        scroll.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox leftPane = new VBox(15, consBox, scroll);
        leftPane.setPrefWidth(550);
        leftPane.setMinWidth(450);
        HBox.setHgrow(leftPane, Priority.ALWAYS);

        // ── Right Column: Performance Data, Chart, Parameters ──
        // 1. Backtest Card
        VBox btCard = new VBox(10);
        btCard.setPadding(new Insets(12));
        btCard.setStyle("-fx-background-color: #171b26; -fx-border-color: #00e676; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px;");
        HBox.setHgrow(btCard, Priority.ALWAYS);
        Label btTitle = new Label("◀ BACKTEST METRIKEN");
        btTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        btTitle.setTextFill(Color.web("#00e676"));
        GridPane btGrid = new GridPane();
        btGrid.setHgap(15);
        btGrid.setVgap(6);
        addMetricRow(btGrid, 0, "Nettoprofit:", String.format(java.util.Locale.US, "%.2f", sel.getBtProfit()));
        addMetricRow(btGrid, 1, "Trades:", String.valueOf(sel.getBtTrades()));
        addMetricRow(btGrid, 2, "Profit Factor:", String.format(java.util.Locale.US, "%.2f", sel.getBtPf()));
        addMetricRow(btGrid, 3, "Max. Drawdown:", String.format(java.util.Locale.US, "%.2f%%", sel.getBtDd()));
        addMetricRow(btGrid, 4, "Recovery Factor:", String.format(java.util.Locale.US, "%.2f", sel.getBtRecovery()));
        addMetricRow(btGrid, 5, "Sharpe Ratio:", Double.isNaN(sel.getBtSharpe()) ? "—" : String.format(java.util.Locale.US, "%.2f", sel.getBtSharpe()));
        addMetricRow(btGrid, 6, "Expected Payoff:", String.format(java.util.Locale.US, "%.2f", sel.getBtExpectedPayoff()));
        btCard.getChildren().addAll(btTitle, btGrid);

        // 2. Forward Card
        VBox fwCard = new VBox(10);
        fwCard.setPadding(new Insets(12));
        fwCard.setStyle("-fx-background-color: #171b26; -fx-border-color: #00e5ff; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px;");
        HBox.setHgrow(fwCard, Priority.ALWAYS);
        Label fwTitle = new Label("FORWARD METRIKEN ▶");
        fwTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        fwTitle.setTextFill(Color.web("#00e5ff"));
        GridPane fwGrid = new GridPane();
        fwGrid.setHgap(15);
        fwGrid.setVgap(6);

        if (sel.getForwardPass() != null) {
            addMetricRow(fwGrid, 0, "Nettoprofit:", String.format(java.util.Locale.US, "%.2f", sel.getFwProfit()));
            addMetricRow(fwGrid, 1, "Trades:", String.valueOf(sel.getFwTrades()));
            addMetricRow(fwGrid, 2, "Profit Factor:", Double.isNaN(sel.getFwPf()) ? "—" : String.format(java.util.Locale.US, "%.2f", sel.getFwPf()));
            addMetricRow(fwGrid, 3, "Max. Drawdown:", Double.isNaN(sel.getFwDd()) ? "—" : String.format(java.util.Locale.US, "%.2f%%", sel.getFwDd()));
            addMetricRow(fwGrid, 4, "Recovery Factor:", Double.isNaN(sel.getFwRecovery()) ? "—" : String.format(java.util.Locale.US, "%.2f", sel.getFwRecovery()));
            addMetricRow(fwGrid, 5, "Sharpe Ratio:", Double.isNaN(sel.getFwSharpe()) ? "—" : String.format(java.util.Locale.US, "%.2f", sel.getFwSharpe()));
            addMetricRow(fwGrid, 6, "Expected Payoff:", Double.isNaN(sel.getFwExpectedPayoff()) ? "—" : String.format(java.util.Locale.US, "%.2f", sel.getFwExpectedPayoff()));
            fwCard.getChildren().addAll(fwTitle, fwGrid);
        } else {
            Label noFwLabel = new Label("Kein Forward-Test\ndurchgeführt.");
            noFwLabel.setTextFill(Color.web("#7e889a"));
            noFwLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
            noFwLabel.setAlignment(Pos.CENTER);
            VBox.setVgrow(noFwLabel, Priority.ALWAYS);
            fwCard.getChildren().addAll(fwTitle, noFwLabel);
        }

        HBox metricsBox = new HBox(12, btCard, fwCard);
        metricsBox.setAlignment(Pos.TOP_LEFT);

        // Equity Chart
        Label chartTitleLabel = new Label("EQUITY-KURVE (KAPITALVERLAUF)");
        chartTitleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        chartTitleLabel.setTextFill(Color.web("#00e5ff"));

        javafx.scene.chart.NumberAxis xAxis = new javafx.scene.chart.NumberAxis();
        xAxis.setLabel("Trades");
        xAxis.setTickLabelFill(Color.web("#7e889a"));
        xAxis.setMinorTickVisible(false);

        javafx.scene.chart.NumberAxis yAxis = new javafx.scene.chart.NumberAxis();
        yAxis.setLabel("Equity");
        yAxis.setTickLabelFill(Color.web("#7e889a"));
        yAxis.setForceZeroInRange(false);

        javafx.scene.chart.LineChart<Number, Number> equityChart = new javafx.scene.chart.LineChart<>(xAxis, yAxis);
        equityChart.setCreateSymbols(false);
        equityChart.setPrefHeight(200);
        equityChart.setMinHeight(200);
        equityChart.setMaxHeight(200);
        equityChart.setAnimated(false);
        equityChart.setStyle("-fx-background-color: transparent;");
        equityChart.setHorizontalGridLinesVisible(true);
        equityChart.setVerticalGridLinesVisible(false);

        // Generate curves
        double btEndBalance = sel.getBacktestPass().getBalance();
        double btStartBalance = btEndBalance - sel.getBtProfit();
        if (btStartBalance <= 0) {
            btStartBalance = 10000.0;
        }

        java.util.List<Double> btCurve = StrategyEvaluatorDialog.generateSyntheticEquityCurve(btStartBalance, sel.getBtProfit(), sel.getBtTrades(), sel.getBtPf(), sel.getPassNumber());
        javafx.scene.chart.XYChart.Series<Number, Number> backtestSeries = new javafx.scene.chart.XYChart.Series<>();
        backtestSeries.setName("Backtest");
        for (int i = 0; i < btCurve.size(); i++) {
            backtestSeries.getData().add(new javafx.scene.chart.XYChart.Data<>(i, btCurve.get(i)));
        }
        equityChart.getData().add(backtestSeries);

        final javafx.scene.chart.XYChart.Series<Number, Number> forwardSeriesRef;
        if (sel.getForwardPass() != null) {
            double fwStartBalance = btCurve.get(btCurve.size() - 1);
            java.util.List<Double> fwCurve = StrategyEvaluatorDialog.generateSyntheticEquityCurve(fwStartBalance, sel.getFwProfit(), sel.getFwTrades(), sel.getFwPf(), sel.getPassNumber() + 999);
            javafx.scene.chart.XYChart.Series<Number, Number> forwardSeries = new javafx.scene.chart.XYChart.Series<>();
            forwardSeries.setName("Forward");

            // Connect seamlessly
            int offset = btCurve.size() - 1;
            forwardSeries.getData().add(new javafx.scene.chart.XYChart.Data<>(offset, fwStartBalance));
            for (int j = 1; j < fwCurve.size(); j++) {
                forwardSeries.getData().add(new javafx.scene.chart.XYChart.Data<>(offset + j, fwCurve.get(j)));
            }
            equityChart.getData().add(forwardSeries);
            forwardSeriesRef = forwardSeries;
        } else {
            forwardSeriesRef = null;
        }

        // Parameters Table
        Label paramTitle = new Label("STRATEGIE-PARAMETER");
        paramTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        paramTitle.setTextFill(Color.web("#00e5ff"));

        TableView<StrategyEvaluatorDialog.ParameterRow> paramTable = new TableView<>();
        paramTable.setStyle("-fx-background-color: transparent;");
        paramTable.setPrefHeight(200);
        VBox.setVgrow(paramTable, Priority.ALWAYS);

        TableColumn<StrategyEvaluatorDialog.ParameterRow, String> nameCol = new TableColumn<>("Parameter");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(250);

        TableColumn<StrategyEvaluatorDialog.ParameterRow, String> valCol = new TableColumn<>("Wert");
        valCol.setCellValueFactory(new PropertyValueFactory<>("value"));
        valCol.setPrefWidth(250);

        paramTable.getColumns().addAll(nameCol, valCol);

        java.util.List<StrategyEvaluatorDialog.ParameterRow> paramList = new java.util.ArrayList<>();
        java.util.Map<String, String> params = sel.getBacktestPass().getParameterValues();
        for (java.util.Map.Entry<String, String> entry : params.entrySet()) {
            paramList.add(new StrategyEvaluatorDialog.ParameterRow(entry.getKey(), entry.getValue()));
        }
        paramTable.setItems(FXCollections.observableArrayList(paramList));

        VBox rightPane = new VBox(12, metricsBox, chartTitleLabel, equityChart, paramTitle, paramTable);
        rightPane.setPrefWidth(650);
        rightPane.setMinWidth(550);
        HBox.setHgrow(rightPane, Priority.ALWAYS);

        // ── Horizontal Content Panel ──
        HBox contentBox = new HBox(25, leftPane, rightPane);
        contentBox.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(contentBox, Priority.ALWAYS);

        // ── Close Button ──
        Button closeBtn = new Button("Schließen");
        closeBtn.setStyle("-fx-background-color: #00e5ff; -fx-text-fill: #0d0f17; -fx-font-weight: bold; -fx-padding: 8 20; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> dialog.close());

        HBox btnBox = new HBox(closeBtn);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        String datePart = (dateSubtitleOptional != null && !dateSubtitleOptional.isEmpty()) ? dateSubtitleOptional : "Unbekannt";
        Label subtitle = new Label("Zeitraum: " + datePart);
        subtitle.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 13));
        subtitle.setTextFill(Color.web("#7e889a"));

        rootBox.getChildren().addAll(title, subtitle, contentBox, btnBox);

        javafx.scene.Scene scene = new javafx.scene.Scene(rootBox, 1200, 865);
        try {
            scene.getStylesheets().add(PassExplanationDialog.class.getResource("/css/antigravity.css").toExternalForm());
        } catch (Exception ignored) {}
        dialog.setScene(scene);

        // Style chart after elements are shown
        dialog.setOnShown(e -> {
            if (backtestSeries.getNode() != null) {
                backtestSeries.getNode().setStyle("-fx-stroke: #00e676; -fx-stroke-width: 3px;");
            }
            if (forwardSeriesRef != null && forwardSeriesRef.getNode() != null) {
                forwardSeriesRef.getNode().setStyle("-fx-stroke: #00e5ff; -fx-stroke-width: 3px;");
            }
            javafx.scene.Node plotBg = equityChart.lookup(".chart-plot-background");
            if (plotBg != null) {
                plotBg.setStyle("-fx-background-color: #171b26; -fx-border-color: #3e4555; -fx-border-width: 1px;");
            }
        });

        dialog.show();
    }

}
