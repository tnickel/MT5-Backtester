package com.backtester.ui.javafx;

import com.backtester.report.OptimizationResult;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Filter and score-weight configuration dialogs extracted from OptimizationView.
 */
public final class OptimizationFilterDialogs {

    private OptimizationFilterDialogs() {}

    /** Mutable filter thresholds shared with OptimizationView. */
    public static class OptimizationFilterState {
        public double filterMinBtProfit = 0.01;
        public double filterMinFwProfit = 0.01;
        public int filterMinBtTrades = 100;
        public int filterMinFwTrades = 50;
        public double filterMaxBtDd = 100.0;
        public double filterMaxFwDd = 100.0;
        public double filterMinBtPayoff = 0.0;
        public double filterMinFwPayoff = 0.0;
        public double filterMinBtSharpe = 0.0;
        public double filterMinFwSharpe = 0.0;
        public double filterMinBtRecovery = 1.0;
        public double filterMinFwRecovery = 1.0;
        public double filterMinScore = 0.0;
        public double filterMinConsistency = 0.0;
    }

    @SuppressWarnings("unchecked")
    public static void showScoreWeightsDialog(javafx.scene.Node owner,
                                              Spinner<Integer>[] weightSpinnersInOrder,
                                              Runnable applyCombinedFilter) {
        javafx.stage.Stage dialog = new javafx.stage.Stage();
        dialog.setTitle("Score-Gewichtung konfigurieren");
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        if (owner.getScene() != null && owner.getScene().getWindow() != null) {
            dialog.initOwner(owner.getScene().getWindow());
        }
        dialog.setResizable(false);

        VBox root = new VBox(18);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: #1a1d27;");

        // ── Title
        Label title = new Label("\u2699\ufe0f  Unified Score-Gewichtung");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        title.setTextFill(Color.web("#ffd740"));

        Label hint = new Label("Jeder Parameter wird relativ zum anderen gewichtet.\n" +
                "Die Summe muss nicht genau 100 ergeben \u2014 sie wird automatisch normalisiert.\n" +
                "Unified Score = Performance + Robustheit in einem Score.");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 11px;");

        // \u2500\u2500 Slider rows (8 S\u00e4ulen \u2014 nur echte Messdaten)
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(10);

        Label[] labels = {
            dialogLabel("BT Profitabilit\u00e4t"),
            dialogLabel("FW Profitabilit\u00e4t"),
            dialogLabel("Konsistenz FW/BT"),
            dialogLabel("Risiko-Verh\u00e4ltnis"),
            dialogLabel("Sharpe Ratio"),
            dialogLabel("Stichprobengr\u00f6\u00dfe"),
            dialogLabel("FW Trade Count"),
            dialogLabel("Erholungsfaktor")
        };
        String[] tooltips = {
            "Backtest ROI + Profit Factor \u2014 Wie profitabel ist die Strategie im In-Sample?",
            "Forward ROI + Profit Factor \u2014 Wie profitabel ist die Strategie Out-of-Sample?",
            "Verh\u00e4ltnis FW/BT: 1.0 = perfekte Reproduzierbarkeit der Ergebnisse",
            "Return/Drawdown + Calmar Ratio \u2014 Gewinn im Verh\u00e4ltnis zum Risiko",
            "Von MT5 gemessene Sharpe Ratio (BT + FW gemittelt) \u2014 echte Kennzahl statt gesch\u00e4tzter Equity-Stabilit\u00e4t",
            "Anzahl Trades + reale Testjahre \u2014 Statistische Signifikanz der Ergebnisse",
            "Mehr FW-Trades = statistisch belastbarer. Zus\u00e4tzlich automatische Strafe wenn FW-Trades < median/2.",
            "Recovery Factor: Net Profit / Max Drawdown (BT und FW gemittelt)"
        };
        Spinner<Integer>[] spinners = weightSpinnersInOrder;

        final int N = spinners.length;
        Slider[] sliders = new Slider[N];
        Label[] valLabels = new Label[N];

        com.backtester.database.DatabaseManager db = com.backtester.database.DatabaseManager.getInstance();
        TextField tfMin = new TextField(db.getSetting("opt.weight.recovery.min", "1.0"));
        tfMin.setPrefWidth(50);
        tfMin.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #fff; -fx-border-color: #444; -fx-border-width: 1; -fx-font-size: 11px;");

        TextField tfMax = new TextField(db.getSetting("opt.weight.recovery.max", "5.0"));
        tfMax.setPrefWidth(50);
        tfMax.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #fff; -fx-border-color: #444; -fx-border-width: 1; -fx-font-size: 11px;");

        for (int i = 0; i < N; i++) {
            Slider sl = new Slider(0, 100, spinners[i].getValue());
            sl.setMajorTickUnit(25);
            sl.setMinorTickCount(4);
            sl.setShowTickMarks(true);
            sl.setSnapToTicks(false);
            sl.setPrefWidth(260);
            sl.setStyle("-fx-control-inner-background: #2a2d3a;");
            sliders[i] = sl;

            Label vl = new Label(spinners[i].getValue() + "%");
            vl.setMinWidth(36);
            vl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
            vl.setTextFill(Color.web("#00e5ff"));
            valLabels[i] = vl;

            final int idx = i;
            sl.valueProperty().addListener((o, a, b) -> {
                int v = (int) Math.round(b.doubleValue());
                sl.setValue(v);
                valLabels[idx].setText(v + "%");
                spinners[idx].getValueFactory().setValue(v);
            });

            labels[i].setTooltip(new Tooltip(tooltips[i]));
            grid.add(labels[i],  0, i);

            if (i == 7) {
                HBox scaleBox = new HBox(6);
                scaleBox.setAlignment(Pos.CENTER_LEFT);
                scaleBox.setPadding(new Insets(4, 0, 0, 0));

                Label scaleLabel = new Label("Skalierung: Min");
                scaleLabel.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 11px;");

                Label scaleToLabel = new Label("bis Max");
                scaleToLabel.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 11px;");

                Button infoBtn = new Button("ℹ");
                infoBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ffd740; -fx-cursor: hand; -fx-font-size: 12px; -fx-padding: 0 4 0 4;");
                Tooltip infoTooltip = new Tooltip(
                    "Grenzwerte für die lineare Skalierung des Erholungsfaktors auf 0-100 Punkte.\n" +
                    "Werte unter Min geben 0 Punkte, über Max geben 100 Punkte."
                );
                infoTooltip.setShowDelay(javafx.util.Duration.millis(100));
                Tooltip.install(infoBtn, infoTooltip);
                infoBtn.setOnAction(evt -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Erholungsfaktor Skalierung");
                    alert.setHeaderText("Wie funktioniert die Skalierung des Erholungsfaktors?");
                    alert.setContentText(
                        "Der Erholungsfaktor (Net Profit / Max Drawdown) wird anhand dieser Grenzwerte linear auf 0-100 Punkte skaliert.\n\n" +
                        "• Ein Wert kleiner oder gleich Min erhält 0 Punkte.\n" +
                        "• Ein Wert größer oder gleich Max erhält 100 Punkte.\n" +
                        "• Dazwischen wird linear interpoliert.\n\n" +
                        "Wenn Sie z.B. Min=1.0 und Max=2.0 einstellen, hat eine Strategie mit Recovery Factor = 1.5 genau 50 Punkte."
                    );
                    alert.getDialogPane().setStyle("-fx-background-color: #1a1d27;");
                    alert.getDialogPane().lookup(".content.label").setStyle("-fx-text-fill: #b4bac8;");
                    alert.getDialogPane().lookup(".header-panel").setStyle("-fx-background-color: #1a1d27;");
                    if (alert.getDialogPane().lookup(".header-panel").lookup(".label") != null) {
                        alert.getDialogPane().lookup(".header-panel").lookup(".label").setStyle("-fx-text-fill: #ffd740;");
                    }
                    alert.initOwner(dialog);
                    alert.showAndWait();
                });

                scaleBox.getChildren().addAll(scaleLabel, tfMin, scaleToLabel, tfMax, infoBtn);

                VBox sliderContainer = new VBox(4);
                sliderContainer.getChildren().addAll(sl, scaleBox);
                grid.add(sliderContainer, 1, i);
            } else {
                grid.add(sl,             1, i);
            }
            grid.add(vl,         2, i);
        }

        // \u2500\u2500 Live sum display
        Label sumLabel = new Label();
        sumLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        Runnable refreshSum = () -> {
            int s = 0;
            for (Spinner<Integer> sp : spinners) s += sp.getValue();
            sumLabel.setText("\u03a3 = " + s + (s == 100 ? "  \u2713 optimal" : "  (wird normalisiert)"));
            sumLabel.setTextFill(s == 100 ? Color.web("#00e676") : Color.web("#ffd740"));
        };
        for (int i = 0; i < N; i++) {
            sliders[i].valueProperty().addListener((o, a, b) -> refreshSum.run());
        }
        refreshSum.run();

        Label autoPenaltyHint = new Label(
            "Automatische Schutzschwelle: FW-Trades unter median/2 erhalten zus\u00e4tzlich " +
            "eine multiplikative Strafe (max. \u221250 %).\n" +
            "Alle 8 S\u00e4ulen basieren auf echten MT5-Messwerten (keine gesch\u00e4tzten Kennzahlen).");
        autoPenaltyHint.setWrapText(true);
        autoPenaltyHint.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 10px; -fx-font-style: italic;");

        // \u2500\u2500 Buttons
        Button resetBtn = new Button("\u21ba Zur\u00fccksetzen");
        resetBtn.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #b4bac8; -fx-border-color: #444; -fx-border-width: 1;");
        resetBtn.setOnAction(e -> {
            // Einzige Quelle f\u00fcr Defaults: ScoreWeights.defaults()
            OptimizationResult.ScoreWeights d = OptimizationResult.ScoreWeights.defaults();
            double[] defaults = {d.wBtProfit, d.wFwProfit, d.wConsistency, d.wRisk,
                    d.wEquityConsist, d.wSampleSize, d.wFwTrades, d.wRecovery};
            for (int i = 0; i < N; i++) {
                sliders[i].setValue(defaults[i]);
            }
            tfMin.setText(String.valueOf(d.recoveryMin));
            tfMax.setText(String.valueOf(d.recoveryMax));
        });

        boolean[] applied = {false};
        Button applyBtn = new Button("\u2714 \u00dcbernehmen & Schlie\u00dfen");
        applyBtn.setStyle("-fx-background-color: #00e5ff; -fx-text-fill: #0d0f17; -fx-font-weight: bold;");
        applyBtn.setOnAction(e -> {
            applied[0] = true;
            dialog.close();
        });

        Button cancelBtn2 = new Button("Abbrechen");
        cancelBtn2.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #b4bac8; -fx-border-color: #444; -fx-border-width: 1;");
        cancelBtn2.setOnAction(e -> {
            for (int i = 0; i < N; i++) {
                sliders[i].setValue(spinners[i].getValue());
            }
            dialog.close();
        });

        // Presets: Low = Performance-fokussiert, Med = ausgewogen, High = Robustheit-fokussiert
        Button btnPresetLow = new Button("Low / Zahm");
        btnPresetLow.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #a7f3d0; -fx-border-color: #10b981; -fx-border-width: 1; -fx-cursor: hand;");
        btnPresetLow.setOnAction(e -> {
            int[] lowWeights = {15, 15, 10, 10, 5, 15, 3, 5, 20, 15};
            for (int i = 0; i < N; i++) sliders[i].setValue(lowWeights[i]);
        });

        Button btnPresetMed = new Button("Med / Ausgewogen");
        btnPresetMed.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #fde047; -fx-border-color: #eab308; -fx-border-width: 1; -fx-cursor: hand;");
        btnPresetMed.setOnAction(e -> {
            int[] medWeights = {10, 15, 15, 15, 10, 25, 5, 10, 30, 25};
            for (int i = 0; i < N; i++) sliders[i].setValue(medWeights[i]);
        });

        Button btnPresetHigh = new Button("High / Streng");
        btnPresetHigh.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #fca5a5; -fx-border-color: #ef4444; -fx-border-width: 1; -fx-cursor: hand;");
        btnPresetHigh.setOnAction(e -> {
            int[] highWeights = {5, 10, 15, 15, 15, 25, 5, 15, 35, 30};
            for (int i = 0; i < N; i++) sliders[i].setValue(highWeights[i]);
        });

        Button btnPresetGrid = new Button("Grid / High-Trade");
        btnPresetGrid.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #38bdf8; -fx-border-color: #0284c7; -fx-border-width: 1; -fx-cursor: hand;");
        btnPresetGrid.setOnAction(e -> {
            int[] gridWeights = {5, 5, 15, 5, 5, 35, 5, 5, 35, 40};
            for (int i = 0; i < N; i++) sliders[i].setValue(gridWeights[i]);
        });

        HBox presetRow = new HBox(8, styledLabel("Voreinstellungen:"), btnPresetLow, btnPresetMed, btnPresetHigh, btnPresetGrid);
        presetRow.setAlignment(Pos.CENTER_LEFT);

        Button mainInfoBtn = DocHelper.createThickCircularInfoButton("Erklärung aller Indizes und Kennzahlen", () -> {
            DocHelper.showAllIndicesDocDialog(dialog);
        });
        HBox btnRow = new HBox(10, resetBtn, mainInfoBtn, new Region(), applyBtn, cancelBtn2);
        HBox.setHgrow(btnRow.getChildren().get(1), Priority.ALWAYS);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        javafx.scene.control.Separator sep = new javafx.scene.control.Separator();
        sep.setStyle("-fx-background-color: #2a2d3a;");
        javafx.scene.control.Separator sep2 = new javafx.scene.control.Separator();
        sep2.setStyle("-fx-background-color: #2a2d3a;");

        root.getChildren().addAll(title, hint, grid, sep, sumLabel, autoPenaltyHint, presetRow, sep2, btnRow);

        javafx.scene.Scene scene = new javafx.scene.Scene(root, 540, 710);
        dialog.setScene(scene);
        dialog.showAndWait();

        if (applied[0]) {
            double rMin = 1.0;
            double rMax = 5.0;
            try {
                rMin = Double.parseDouble(tfMin.getText().trim().replace(',', '.'));
            } catch (Exception ex) {}
            try {
                rMax = Double.parseDouble(tfMax.getText().trim().replace(',', '.'));
            } catch (Exception ex) {}
            db.saveSetting("opt.weight.recovery.min", String.valueOf(rMin));
            db.saveSetting("opt.weight.recovery.max", String.valueOf(rMax));

            db.saveSetting("opt.weight.btProfit", String.valueOf(spinners[0].getValue()));
            db.saveSetting("opt.weight.fwProfit", String.valueOf(spinners[1].getValue()));
            db.saveSetting("opt.weight.consistency", String.valueOf(spinners[2].getValue()));
            db.saveSetting("opt.weight.risk", String.valueOf(spinners[3].getValue()));
            db.saveSetting("opt.weight.equityConsist", String.valueOf(spinners[4].getValue()));
            db.saveSetting("opt.weight.sampleSize", String.valueOf(spinners[5].getValue()));
            db.saveSetting("opt.weight.fwTrades", String.valueOf(spinners[6].getValue()));
            db.saveSetting("opt.weight.recovery", String.valueOf(spinners[7].getValue()));
            applyCombinedFilter.run();
        }
    }

    public static void showFilterDialog(javafx.scene.Node owner,
                                        OptimizationFilterState criteria,
                                        CheckBox filterEnabledCheck,
                                        Runnable applyCombinedFilter) {
        javafx.stage.Stage dialog = new javafx.stage.Stage();
        dialog.setTitle("Filter-Kriterien konfigurieren");
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        if (owner.getScene() != null && owner.getScene().getWindow() != null) {
            dialog.initOwner(owner.getScene().getWindow());
        }
        dialog.setResizable(false);

        VBox root = new VBox(18);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: #1a1d27;");

        Label title = new Label("🔍  Filter-Kriterien");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        title.setTextFill(Color.web("#00e5ff"));

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(10);

        TextField tfBtProfit    = makeFilterField(String.valueOf(criteria.filterMinBtProfit));
        TextField tfFwProfit    = makeFilterField(String.valueOf(criteria.filterMinFwProfit));
        TextField tfMinBtTrades = makeFilterField(String.valueOf(criteria.filterMinBtTrades));
        TextField tfMinFwTrades = makeFilterField(String.valueOf(criteria.filterMinFwTrades));
        TextField tfMaxBtDd     = makeFilterField(String.valueOf(criteria.filterMaxBtDd));
        TextField tfMaxFwDd     = makeFilterField(String.valueOf(criteria.filterMaxFwDd));
        TextField tfBtPayoff    = makeFilterField(String.valueOf(criteria.filterMinBtPayoff));
        TextField tfFwPayoff    = makeFilterField(String.valueOf(criteria.filterMinFwPayoff));
        TextField tfBtSharpe    = makeFilterField(String.valueOf(criteria.filterMinBtSharpe));
        TextField tfFwSharpe    = makeFilterField(String.valueOf(criteria.filterMinFwSharpe));
        TextField tfBtRecovery  = makeFilterField(String.valueOf(criteria.filterMinBtRecovery));
        TextField tfFwRecovery  = makeFilterField(String.valueOf(criteria.filterMinFwRecovery));
        TextField tfMinScore    = makeFilterField(String.valueOf(criteria.filterMinScore));
        TextField tfMinConsist  = makeFilterField(String.valueOf(criteria.filterMinConsistency));

        grid.add(styledLabel("BT Profit ≥"),        0, 0); grid.add(tfBtProfit,    1, 0);
        grid.add(styledLabel("FW Profit ≥"),        2, 0); grid.add(tfFwProfit,    3, 0);
        grid.add(styledLabel("Min BT Trades ≥"),    0, 1); grid.add(tfMinBtTrades, 1, 1);
        grid.add(styledLabel("Min FW Trades ≥"),    2, 1); grid.add(tfMinFwTrades, 3, 1);
        grid.add(styledLabel("Max BT Drawdown% ≤"), 0, 2); grid.add(tfMaxBtDd,     1, 2);
        grid.add(styledLabel("Max FW Drawdown% ≤"), 2, 2); grid.add(tfMaxFwDd,     3, 2);
        grid.add(styledLabel("BT Exp. Payoff ≥"),   0, 3); grid.add(tfBtPayoff,    1, 3);
        grid.add(styledLabel("FW Exp. Payoff ≥"),   2, 3); grid.add(tfFwPayoff,    3, 3);
        grid.add(styledLabel("BT Sharpe Ratio ≥"),  0, 4); grid.add(tfBtSharpe,    1, 4);
        grid.add(styledLabel("FW Sharpe Ratio ≥"),  2, 4); grid.add(tfFwSharpe,    3, 4);
        grid.add(styledLabel("BT Recovery Factor ≥"),0, 5); grid.add(tfBtRecovery,  1, 5);
        grid.add(styledLabel("FW Recovery Factor ≥"),2, 5); grid.add(tfFwRecovery,  3, 5);
        grid.add(styledLabel("Mindest-Score ≥"),    0, 6); grid.add(tfMinScore,    1, 6);
        grid.add(styledLabel("Mindest-Konsistenz ≥"),2, 6); grid.add(tfMinConsist,  3, 6);

        Button applyBtn = new Button("✔ Anwenden & Schließen");
        applyBtn.setStyle("-fx-background-color: #00e5ff; -fx-text-fill: #0d0f17; -fx-font-weight: bold;");
        applyBtn.setOnAction(e -> {
            criteria.filterMinBtProfit = parseFilterDouble(tfBtProfit, 0.0);
            criteria.filterMinFwProfit = parseFilterDouble(tfFwProfit, 0.0);
            criteria.filterMinBtTrades = parseFilterInt(tfMinBtTrades, 0);
            criteria.filterMinFwTrades = parseFilterInt(tfMinFwTrades, 0);
            criteria.filterMaxBtDd     = parseFilterDouble(tfMaxBtDd, 100.0);
            criteria.filterMaxFwDd     = parseFilterDouble(tfMaxFwDd, 100.0);
            criteria.filterMinBtPayoff = parseFilterDouble(tfBtPayoff, 0.0);
            criteria.filterMinFwPayoff = parseFilterDouble(tfFwPayoff, 0.0);
            criteria.filterMinBtSharpe = parseFilterDouble(tfBtSharpe, 0.0);
            criteria.filterMinFwSharpe = parseFilterDouble(tfFwSharpe, 0.0);
            criteria.filterMinBtRecovery = parseFilterDouble(tfBtRecovery, 0.0);
            criteria.filterMinFwRecovery = parseFilterDouble(tfFwRecovery, 0.0);
            criteria.filterMinScore      = parseFilterDouble(tfMinScore, 0.0);
            criteria.filterMinConsistency = parseFilterDouble(tfMinConsist, 0.0);

            com.backtester.database.DatabaseManager db = com.backtester.database.DatabaseManager.getInstance();
            db.saveSetting("opt.filter.minBtProfit", String.valueOf(criteria.filterMinBtProfit));
            db.saveSetting("opt.filter.minFwProfit", String.valueOf(criteria.filterMinFwProfit));
            db.saveSetting("opt.filter.minBtTrades", String.valueOf(criteria.filterMinBtTrades));
            db.saveSetting("opt.filter.minFwTrades", String.valueOf(criteria.filterMinFwTrades));
            db.saveSetting("opt.filter.maxBtDd", String.valueOf(criteria.filterMaxBtDd));
            db.saveSetting("opt.filter.maxFwDd", String.valueOf(criteria.filterMaxFwDd));
            db.saveSetting("opt.filter.minBtPayoff", String.valueOf(criteria.filterMinBtPayoff));
            db.saveSetting("opt.filter.minFwPayoff", String.valueOf(criteria.filterMinFwPayoff));
            db.saveSetting("opt.filter.minBtSharpe", String.valueOf(criteria.filterMinBtSharpe));
            db.saveSetting("opt.filter.minFwSharpe", String.valueOf(criteria.filterMinFwSharpe));
            db.saveSetting("opt.filter.minBtRecovery", String.valueOf(criteria.filterMinBtRecovery));
            db.saveSetting("opt.filter.minFwRecovery", String.valueOf(criteria.filterMinFwRecovery));
            db.saveSetting("opt.filter.minScore", String.valueOf(criteria.filterMinScore));
            db.saveSetting("opt.filter.minConsistency", String.valueOf(criteria.filterMinConsistency));

            // Auto-enable filter and save state
            if (filterEnabledCheck != null) {
                filterEnabledCheck.setSelected(true);
            }
            db.saveSetting("opt.filter.enabled", "true");

            dialog.close();
            applyCombinedFilter.run();
        });

        Button resetBtn = new Button("↺ Zurücksetzen");
        resetBtn.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #b4bac8; -fx-border-color: #444; -fx-border-width: 1;");
        resetBtn.setOnAction(e -> {
            tfBtProfit.setText("0.0");
            tfFwProfit.setText("0.0");
            tfMinBtTrades.setText("1");
            tfMinFwTrades.setText("0");
            tfMaxBtDd.setText("100.0");
            tfMaxFwDd.setText("100.0");
            tfBtPayoff.setText("0.0");
            tfFwPayoff.setText("0.0");
            tfBtSharpe.setText("0.0");
            tfFwSharpe.setText("0.0");
            tfBtRecovery.setText("0.0");
            tfFwRecovery.setText("0.0");
            tfMinScore.setText("0.0");
            tfMinConsist.setText("0.0");
        });

        HBox btnRow = new HBox(10, resetBtn, new Region(), applyBtn);
        HBox.setHgrow(btnRow.getChildren().get(1), Priority.ALWAYS);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        javafx.scene.control.Separator sep = new javafx.scene.control.Separator();
        sep.setStyle("-fx-background-color: #2a2d3a;");

        root.getChildren().addAll(title, grid, sep, btnRow);

        javafx.scene.Scene scene = new javafx.scene.Scene(root, 580, 440);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private static Label dialogLabel(String text) {
        Label l = new Label(text);
        l.setMinWidth(140);
        l.setStyle("-fx-text-fill: #b4bac8; -fx-font-size: 12px;");
        return l;
    }

    private static Label styledLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #b4bac8;");
        return l;
    }

    private static TextField makeFilterField(String defaultVal) {
        TextField tf = new TextField(defaultVal);
        tf.getStyleClass().add("text-input");
        tf.setPrefWidth(70);
        return tf;
    }

    private static double parseFilterDouble(TextField tf, double fallback) {
        try { return Double.parseDouble(tf.getText().trim().replace(",", ".")); }
        catch (NumberFormatException e) { return fallback; }
    }

    private static int parseFilterInt(TextField tf, int fallback) {
        try { return Integer.parseInt(tf.getText().trim()); }
        catch (NumberFormatException e) { return fallback; }
    }

}
