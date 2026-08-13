package com.backtester.ui.javafx;

import com.backtester.config.EaParameter;
import com.backtester.config.AppConfig;
import com.backtester.config.Preset;
import com.backtester.config.PresetManager;
import javafx.scene.web.WebView;
import com.backtester.config.EaParameterManager;
import com.backtester.engine.BacktestConfig;
import com.backtester.engine.OptimizationConfig;
import com.backtester.engine.WorkflowEngine;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.SensitivityResult;
import com.backtester.workflow.WorkflowTask;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Workflow score-weights configuration dialog.
 */
public final class WorkflowScoreWeightsConfigDialog {

    private WorkflowScoreWeightsConfigDialog() {}

    public static void showScoreWeightsDialog(Window owner) {
        Stage dialog = new Stage();
        dialog.setTitle("Score-Gewichtung konfigurieren");
        dialog.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setResizable(false);

        VBox root = new VBox(18);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: #1a1d27;");

        Label title = new Label("⚙️  Unified Score-Gewichtung");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        title.setTextFill(Color.web("#ffd740"));

        Label hint = new Label(
            "Hier konfigurierst du den UNIFIED SCORE (Spalte 'Score' in den Tabellen).\n" +
            "Dieser Score bewertet gewichtet die Endergebnisse der Backtest- und Forward-Phase.\n" +
            "• Er unterscheidet sich vom 'Rob. Scorecard' (Stresstests der Equity-Kurve) und dem 'RI' (mathematisch starrer Index).\n" +
            "• Die Schieberegler bestimmen das relative Gewicht (die Summe wird automatisch normalisiert)."
        );
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 11px;");

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(10);

        String[] names = {
            "BT Profitabilität", "FW Profitabilität", "Konsistenz FW/BT", "Risiko-Verhältnis",
            "Sharpe Ratio", "Stichprobengröße",
            "FW Trade Count", "Erholungsfaktor"
        };
        String[] dbKeys = {
            "opt.weight.btProfit", "opt.weight.fwProfit", "opt.weight.consistency", "opt.weight.risk",
            "opt.weight.equityConsist", "opt.weight.sampleSize",
            "opt.weight.fwTrades", "opt.weight.recovery"
        };
        // Defaults aus der einzigen Quelle ScoreWeights.defaults()
        com.backtester.report.OptimizationResult.ScoreWeights wDef =
                com.backtester.report.OptimizationResult.ScoreWeights.defaults();
        String[] defaults = {
            String.valueOf((int) wDef.wBtProfit), String.valueOf((int) wDef.wFwProfit),
            String.valueOf((int) wDef.wConsistency), String.valueOf((int) wDef.wRisk),
            String.valueOf((int) wDef.wEquityConsist), String.valueOf((int) wDef.wSampleSize),
            String.valueOf((int) wDef.wFwTrades), String.valueOf((int) wDef.wRecovery)
        };
        String[] tooltips = {
            "Backtest ROI + Profit Factor — Wie profitabel ist die Strategie im In-Sample?",
            "Forward ROI + Profit Factor — Wie profitabel ist die Strategie Out-of-Sample?",
            "Verhältnis FW/BT: 1.0 = perfekte Reproduzierbarkeit der Ergebnisse",
            "Return/Drawdown + Calmar Ratio — Gewinn im Verhältnis zum Risiko",
            "Von MT5 gemessene Sharpe Ratio (BT + FW gemittelt) — echte Kennzahl statt geschätzter Equity-Stabilität",
            "Anzahl Trades + reale Testjahre — Statistische Signifikanz der Ergebnisse",
            "Mehr FW-Trades = statistisch belastbarer. Zusätzlich automatische Strafe wenn FW-Trades < median/2.",
            "Recovery Factor: Net Profit / Max Drawdown (BT und FW gemittelt)"
        };

        com.backtester.database.DatabaseManager db = com.backtester.database.DatabaseManager.getInstance();
        int N = names.length;
        Slider[] sliders = new Slider[N];
        Label[] valLabels = new Label[N];

        TextField tfMin = new TextField(db.getSetting("opt.weight.recovery.min", "1.0"));
        tfMin.setPrefWidth(50);
        tfMin.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #fff; -fx-border-color: #444; -fx-border-width: 1; -fx-font-size: 11px;");

        TextField tfMax = new TextField(db.getSetting("opt.weight.recovery.max", "5.0"));
        tfMax.setPrefWidth(50);
        tfMax.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #fff; -fx-border-color: #444; -fx-border-width: 1; -fx-font-size: 11px;");

        for (int i = 0; i < N; i++) {
            Label label = new Label(names[i]);
            label.setMinWidth(140);
            label.setStyle("-fx-text-fill: #b4bac8; -fx-font-size: 12px;");
            label.setTooltip(new Tooltip(tooltips[i]));

            int currentVal = Integer.parseInt(db.getSetting(dbKeys[i], defaults[i]));
            Slider sl = new Slider(0, 100, currentVal);
            sl.setMajorTickUnit(25);
            sl.setMinorTickCount(4);
            sl.setShowTickMarks(true);
            sl.setPrefWidth(260);
            sl.setStyle("-fx-control-inner-background: #2a2d3a;");
            sliders[i] = sl;

            Label vl = new Label(currentVal + "%");
            vl.setMinWidth(36);
            vl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
            vl.setTextFill(Color.web("#00e5ff"));
            valLabels[i] = vl;

            final int idx = i;
            sl.valueProperty().addListener((o, a, b) -> {
                int v = (int) Math.round(b.doubleValue());
                sl.setValue(v);
                valLabels[idx].setText(v + "%");
            });

            grid.add(label, 0, i);
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
                grid.add(sl, 1, i);
            }
            grid.add(vl, 2, i);
        }

        Label sumLabel = new Label();
        sumLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        Runnable refreshSum = () -> {
            int s = 0;
            for (Slider sl : sliders) s += (int) sl.getValue();
            sumLabel.setText("Σ = " + s + (s == 100 ? "  ✓ optimal" : "  (wird normalisiert)"));
            sumLabel.setTextFill(s == 100 ? Color.web("#00e676") : Color.web("#ffd740"));
        };
        for (Slider sl : sliders) {
            sl.valueProperty().addListener((o, a, b) -> refreshSum.run());
        }
        refreshSum.run();

        Label autoPenaltyHint = new Label(
            "Automatische Schutzschwelle: FW-Trades unter median/2 erhalten zusätzlich " +
            "eine multiplikative Strafe (max. −50 %).\n" +
            "Alle 8 Säulen basieren auf echten MT5-Messwerten (keine geschätzten Kennzahlen).");
        autoPenaltyHint.setWrapText(true);
        autoPenaltyHint.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 10px; -fx-font-style: italic;");

        Button resetBtn = new Button("↺ Zurücksetzen");
        resetBtn.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #b4bac8; -fx-border-color: #444; -fx-border-width: 1;");
        resetBtn.setOnAction(e -> {
            for (int i = 0; i < N; i++) {
                sliders[i].setValue(Integer.parseInt(defaults[i]));
            }
            tfMin.setText("1.0");
            tfMax.setText("5.0");
        });

        Button applyBtn = new Button("✔ Übernehmen & Schließen");
        applyBtn.setStyle("-fx-background-color: #00e5ff; -fx-text-fill: #0d0f17; -fx-font-weight: bold;");
        applyBtn.setOnAction(e -> {
            Double rMin = parseRecoveryThreshold(tfMin, "Min", dialog);
            if (rMin == null) return;

            Double rMax = parseRecoveryThreshold(tfMax, "Max", dialog);
            if (rMax == null) return;

            if (rMax <= rMin) {
                showRecoveryValidationError(dialog,
                        "Der Max-Wert muss gr\u00f6\u00dfer als der Min-Wert sein.");
                tfMax.requestFocus();
                tfMax.selectAll();
                return;
            }

            db.saveSetting("opt.weight.recovery.min", String.valueOf(rMin));
            db.saveSetting("opt.weight.recovery.max", String.valueOf(rMax));

            for (int i = 0; i < N; i++) {
                db.saveSetting(dbKeys[i], String.valueOf((int) sliders[i].getValue()));
            }
            dialog.close();
        });

        Button cancelBtn = new Button("Abbrechen");
        cancelBtn.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #b4bac8; -fx-border-color: #444; -fx-border-width: 1;");
        cancelBtn.setOnAction(e -> dialog.close());

        Button btnPresetLow = new Button("Low / Zahm");
        btnPresetLow.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #a7f3d0; -fx-border-color: #10b981; -fx-border-width: 1; -fx-cursor: hand;");
        btnPresetLow.setOnAction(e -> {
            int[] lowWeights = {15, 15, 10, 10, 5, 15, 20, 15};
            for (int i = 0; i < N; i++) sliders[i].setValue(lowWeights[i]);
        });

        Button btnPresetMed = new Button("Med / Ausgewogen");
        btnPresetMed.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #fde047; -fx-border-color: #eab308; -fx-border-width: 1; -fx-cursor: hand;");
        btnPresetMed.setOnAction(e -> {
            int[] medWeights = {10, 15, 15, 15, 10, 25, 30, 25};
            for (int i = 0; i < N; i++) sliders[i].setValue(medWeights[i]);
        });

        Button btnPresetHigh = new Button("High / Streng");
        btnPresetHigh.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #fca5a5; -fx-border-color: #ef4444; -fx-border-width: 1; -fx-cursor: hand;");
        btnPresetHigh.setOnAction(e -> {
            int[] highWeights = {5, 10, 15, 15, 15, 25, 35, 30};
            for (int i = 0; i < N; i++) sliders[i].setValue(highWeights[i]);
        });

        Button btnPresetGrid = new Button("Grid / High-Trade");
        btnPresetGrid.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #38bdf8; -fx-border-color: #0284c7; -fx-border-width: 1; -fx-cursor: hand;");
        btnPresetGrid.setOnAction(e -> {
            int[] gridWeights = {7, 7, 6, 3, 3, 23, 30, 21};
            for (int i = 0; i < N; i++) sliders[i].setValue(gridWeights[i]);
        });

        HBox presetRow = new HBox(8, new Label("Voreinstellungen:"), btnPresetLow, btnPresetMed, btnPresetHigh, btnPresetGrid);
        presetRow.setAlignment(Pos.CENTER_LEFT);
        presetRow.getChildren().get(0).setStyle("-fx-text-fill: #b4bac8;");

        Button mainInfoBtn = DocHelper.createThickCircularInfoButton("Erklärung aller Indizes und Kennzahlen", () -> {
            DocHelper.showAllIndicesDocDialog(dialog);
        });
        HBox btnRow = new HBox(10, resetBtn, mainInfoBtn, new Region(), applyBtn, cancelBtn);
        HBox.setHgrow(btnRow.getChildren().get(2), Priority.ALWAYS);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #2a2d3a;");
        Separator sep2 = new Separator();
        sep2.setStyle("-fx-background-color: #2a2d3a;");

        root.getChildren().addAll(title, hint, grid, sep, sumLabel, autoPenaltyHint, presetRow, sep2, btnRow);

        Scene scene = new Scene(root, 540, 710);
        try {
            java.net.URL css = DocHelper.class.getResource("/css/antigravity.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
        } catch (Exception ignored) {}
        dialog.setScene(scene);
        WorkflowConfigDialogSupport.applyTheme(dialog, owner);
        dialog.showAndWait();
    }

    private static Double parseRecoveryThreshold(TextField field, String name, Window owner) {
        try {
            double value = Double.parseDouble(field.getText().trim().replace(',', '.'));
            if (!Double.isFinite(value)) {
                throw new NumberFormatException("non-finite value");
            }
            return value;
        } catch (NumberFormatException ex) {
            showRecoveryValidationError(owner,
                    name + " muss eine endliche Zahl sein (z. B. 1,0 oder 5,0).");
            field.requestFocus();
            field.selectAll();
            return null;
        }
    }

    private static void showRecoveryValidationError(Window owner, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ung\u00fcltige Erholungsfaktor-Skalierung");
        alert.setHeaderText("Die Einstellungen wurden nicht gespeichert.");
        alert.setContentText(message);
        alert.initOwner(owner);
        alert.showAndWait();
    }

}
