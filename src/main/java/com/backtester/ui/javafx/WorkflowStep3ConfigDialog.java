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
 * Step 3: filter / diversity settings dialog.
 */
public final class WorkflowStep3ConfigDialog {

    private WorkflowStep3ConfigDialog() {}

    // ─── Step 3: Diversity Filter Settings ──────────────────────────────────────
    public static void showStep3Dialog(WorkflowEngine engine, Window owner) {
        Stage stage = new Stage();
        stage.setTitle("Schritt 3: Top-5 Diversitäts-Filter");

        VBox layout = new VBox(15);
        layout.setStyle("-fx-background-color: #0b0d13; -fx-padding: 20;");
        layout.setPrefWidth(550);

        Label title = new Label("STRATEGIE-FILTER & DIVERSITÄT");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#00e5ff"));
        layout.getChildren().add(title);

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(12);
        grid.getStyleClass().add("sci-fi-panel");

        // Filter thresholds
        grid.add(new Label("Min. Backtest Profit:"), 0, 0);
        TextField minBtProfitField = new TextField(String.valueOf(engine.getMinBtProfit()));
        grid.add(minBtProfitField, 1, 0);

        grid.add(new Label("Min. Forward Profit:"), 2, 0);
        TextField minFwProfitField = new TextField(String.valueOf(engine.getMinFwProfit()));
        grid.add(minFwProfitField, 3, 0);

        grid.add(new Label("Min. Backtest Trades:"), 0, 1);
        TextField minBtTradesField = new TextField(String.valueOf(engine.getMinBtTrades()));
        grid.add(minBtTradesField, 1, 1);

        grid.add(new Label("Min. Forward Trades:"), 2, 1);
        TextField minFwTradesField = new TextField(String.valueOf(engine.getMinFwTrades()));
        grid.add(minFwTradesField, 3, 1);

        grid.add(new Label("Min. Backtest Recovery:"), 0, 2);
        TextField minBtRecoveryField = new TextField(String.valueOf(engine.getMinBtRecovery()));
        grid.add(minBtRecoveryField, 1, 2);

        grid.add(new Label("Min. Forward Recovery:"), 2, 2);
        TextField minFwRecoveryField = new TextField(String.valueOf(engine.getMinFwRecovery()));
        grid.add(minFwRecoveryField, 3, 2);

        grid.add(new Label("Max. Backtest DD %:"), 0, 3);
        TextField maxBtDdField = new TextField(String.valueOf(engine.getMaxBtDd()));
        grid.add(maxBtDdField, 1, 3);

        grid.add(new Label("Max. Forward DD %:"), 2, 3);
        TextField maxFwDdField = new TextField(String.valueOf(engine.getMaxFwDd()));
        grid.add(maxFwDdField, 3, 3);

        // Longterm section header
        Label ltSepLabel = new Label("LANGZEITTEST & DUAL-FILTER EINSTELLUNGEN (5-10 JAHRE)");
        ltSepLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        ltSepLabel.setTextFill(Color.web("#00e5ff"));
        grid.add(ltSepLabel, 0, 4, 4, 1);

        grid.add(new Label("Langzeit Von:"), 0, 5);
        DatePicker ltFromPicker = new DatePicker(engine.getEffectiveLongtermFromDate());
        ltFromPicker.setConverter(WorkflowConfigDialogSupport.createDateConverter());
        grid.add(ltFromPicker, 1, 5);

        grid.add(new Label("Langzeit Bis:"), 2, 5);
        DatePicker ltToPicker = new DatePicker(engine.getEffectiveLongtermToDate());
        ltToPicker.setConverter(WorkflowConfigDialogSupport.createDateConverter());
        grid.add(ltToPicker, 3, 5);

        grid.add(new Label("Max. LT Kandidaten:"), 0, 6);
        Spinner<Integer> maxLtCandSpin = new Spinner<>(1, 100, engine.getMaxLongtermCandidates(), 1);
        grid.add(maxLtCandSpin, 1, 6);

        grid.add(new Label("Min. LT Profit Factor:"), 2, 6);
        TextField minLtPfField = new TextField(String.valueOf(engine.getMinLtPf()));
        grid.add(minLtPfField, 3, 6);

        grid.add(new Label("Min. LT Profit:"), 0, 7);
        TextField minLtProfitField = new TextField(String.valueOf(engine.getMinLtProfit()));
        grid.add(minLtProfitField, 1, 7);

        grid.add(new Label("Min. LT Trades:"), 2, 7);
        TextField minLtTradesField = new TextField(String.valueOf(engine.getMinLtTrades()));
        grid.add(minLtTradesField, 3, 7);

        grid.add(new Label("Min. LT Recovery:"), 0, 8);
        TextField minLtRecoveryField = new TextField(String.valueOf(engine.getMinLtRecovery()));
        grid.add(minLtRecoveryField, 1, 8);

        grid.add(new Label("Max. LT DD %:"), 2, 8);
        TextField maxLtDdField = new TextField(String.valueOf(engine.getMaxLtDd()));
        grid.add(maxLtDdField, 3, 8);

        // Diversity delta thresholds
        HBox sepBox = new HBox(10);
        sepBox.setAlignment(Pos.CENTER_LEFT);
        Label sepLabel = new Label("DIVERSITÄTS-METRIKEN (ÄHNLICHKEITS-SCHWELLWERTE)");
        sepLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        sepLabel.setTextFill(Color.web("#ffd740"));

        Button defaultBtn = new Button("Standards setzen");
        defaultBtn.setStyle("-fx-background-color: #1e293b; -fx-text-fill: #ffd740; -fx-border-color: #ffd740; -fx-border-radius: 4; -fx-cursor: hand; -fx-font-size: 11px;");
        
        sepBox.getChildren().addAll(sepLabel, defaultBtn);
        grid.add(sepBox, 0, 9, 4, 1);

        grid.add(new Label("Param Differenz %:"), 0, 10);
        TextField paramDiffField = new TextField(String.format(Locale.US, "%.0f", engine.getParamDiffPct() * 100));
        grid.add(paramDiffField, 1, 10);

        grid.add(new Label("Trades Differenz %:"), 2, 10);
        TextField tradeDiffField = new TextField(String.format(Locale.US, "%.0f", engine.getTradeDiffPct() * 100));
        grid.add(tradeDiffField, 3, 10);

        grid.add(new Label("Min. differente Params:"), 0, 11);
        Spinner<Integer> minDiffParamsSpin = new Spinner<>(1, 10, engine.getMinDifferentParams(), 1);
        grid.add(minDiffParamsSpin, 1, 11);

        grid.add(new Label("Max. Strategien (Ziel):"), 2, 11);
        Spinner<Integer> maxStratsSpin = new Spinner<>(1, 10000, engine.getMaxStrategiesToSelect(), 1);
        grid.add(maxStratsSpin, 3, 11);

        layout.getChildren().add(grid);

        // Display currently selected passes in Step 3 if any
        if (engine.getSelectedDiversePasses() != null && !engine.getSelectedDiversePasses().isEmpty()) {
            VBox resultsBox = new VBox(5);
            resultsBox.getChildren().add(new Label("Aktuell selektierte diverse Durchgänge:"));
            TableView<CombinedPass> table = new TableView<>();
            table.setPrefHeight(150);
            
            TableColumn<CombinedPass, Integer> passCol = new TableColumn<>("Pass");
            passCol.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getPassNumber()));
            passCol.setPrefWidth(65);
            passCol.setStyle("-fx-alignment: CENTER;");
            
            TableColumn<CombinedPass, Double> scoreCol = new TableColumn<>();
            scoreCol.setGraphic(DocHelper.createHeaderWithTooltip("Score", 
                "Unified Score (0-100):\nGewichteter Gesamtwert aus 8 Säulen. Standardmäßig zählen viele Trades am stärksten, danach Recovery und positiver Profit. Konfigurierbar über 'Score-Gewichtung'."));
            scoreCol.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getScore()));
            scoreCol.setPrefWidth(75);
            scoreCol.setStyle("-fx-alignment: CENTER;");
            scoreCol.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
                @Override
                protected void updateItem(Double item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("-fx-alignment: CENTER;");
                    } else {
                        setText(String.format(Locale.US, "%.1f", item));
                        setStyle("-fx-alignment: CENTER;");
                    }
                }
            });
            
            TableColumn<CombinedPass, String> robScoreCol = new TableColumn<>();
            robScoreCol.setGraphic(DocHelper.createHeaderWithTooltip("Rob. Scorecard", 
                "Robustness Scorecard (0-100):\nErgebnis des Monte-Carlo-Stresstests und systematischen Parameter-Shifting. Simuliert Rauschen (Slippage, Spread, Execution) und bewertet die Geradlinigkeit (R²-Stabilität) der Equity-Kurve."));
            robScoreCol.setCellValueFactory(c -> {
                String fromDateStr = engine.getFromDate() != null ? engine.getFromDate().toString() : "Unbekannt";
                String toDateStr = engine.getToDate() != null ? engine.getToDate().toString() : "Unbekannt";
                double score = c.getValue().getCachedOverallScore(fromDateStr, toDateStr);
                return new javafx.beans.property.SimpleStringProperty(String.format(Locale.US, "%.0f", score));
            });
            robScoreCol.setStyle("-fx-alignment: CENTER;");
            robScoreCol.setCellFactory(col -> new TableCell<CombinedPass, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("-fx-alignment: CENTER;");
                    } else {
                        setText(item);
                        try {
                            double score = Double.parseDouble(item);
                            String color;
                            if (score >= 70) {
                                color = "#00e676"; // Green
                            } else if (score >= 55) {
                                color = "#ffd740"; // Yellow
                            } else {
                                color = "#ff5252"; // Red
                            }
                            setStyle("-fx-alignment: CENTER; -fx-text-fill: " + color + "; -fx-font-weight: bold;");
                        } catch (Exception e) {
                            setStyle("-fx-alignment: CENTER;");
                        }
                    }
                }
            });
            robScoreCol.setPrefWidth(115);

            TableColumn<CombinedPass, Double> btProf = new TableColumn<>("BT Profit");
            btProf.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getBtProfit()));
            btProf.setPrefWidth(95);
            btProf.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
                @Override
                protected void updateItem(Double item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null || Double.isNaN(item)) {
                        setText("-");
                        setStyle("-fx-alignment: CENTER;");
                    } else {
                        setText(String.format(Locale.US, "%.2f", item));
                        if (item >= 0) {
                            setStyle("-fx-alignment: CENTER; -fx-text-fill: #00e676; -fx-font-weight: bold;");
                        } else {
                            setStyle("-fx-alignment: CENTER; -fx-text-fill: #ff5252; -fx-font-weight: bold;");
                        }
                    }
                }
            });

            TableColumn<CombinedPass, Integer> btTr = new TableColumn<>("BT Trades");
            btTr.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getBtTrades()));
            btTr.setPrefWidth(85);
            btTr.setCellFactory(col -> new TableCell<CombinedPass, Integer>() {
                @Override
                protected void updateItem(Integer item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText("-");
                        setStyle("-fx-alignment: CENTER;");
                    } else {
                        setText(String.valueOf(item));
                        setStyle("-fx-alignment: CENTER;");
                    }
                }
            });

            table.getColumns().addAll(passCol, scoreCol, robScoreCol, btProf, btTr);
            table.getItems().setAll(engine.getSelectedDiversePasses());
            resultsBox.getChildren().add(table);
            layout.getChildren().add(resultsBox);
        }

        HBox bottomRow = new HBox(15);
        bottomRow.setAlignment(Pos.CENTER_RIGHT);
        Button cancel = new Button("Abbrechen");
        cancel.getStyleClass().add("button-cancel");
        cancel.setOnAction(e -> stage.close());
        Button save = new Button("Speichern");
        save.getStyleClass().add("button-start");
        save.setOnAction(e -> {
            final double minBtProfit;
            final double minFwProfit;
            final int minBtTrades;
            final int minFwTrades;
            final double minBtRecovery;
            final double minFwRecovery;
            final double maxBtDd;
            final double maxFwDd;
            final double minLtProfit;
            final int minLtTrades;
            final double minLtRecovery;
            final double maxLtDd;
            final double minLtPf;
            final double paramDiffPct;
            final double tradeDiffPct;
            try {
                minBtProfit = WorkflowConfigDialogSupport.parseFiniteDecimal(minBtProfitField.getText(), "Min. Backtest Profit", 0.0, Double.MAX_VALUE);
                minFwProfit = WorkflowConfigDialogSupport.parseFiniteDecimal(minFwProfitField.getText(), "Min. Forward Profit", 0.0, Double.MAX_VALUE);
                minBtTrades = WorkflowConfigDialogSupport.parsePositiveInteger(minBtTradesField.getText(), "Min. Backtest Trades");
                minFwTrades = WorkflowConfigDialogSupport.parsePositiveInteger(minFwTradesField.getText(), "Min. Forward Trades");
                minBtRecovery = WorkflowConfigDialogSupport.parseFiniteDecimal(minBtRecoveryField.getText(), "Min. Backtest Recovery", 0.0, Double.MAX_VALUE);
                minFwRecovery = WorkflowConfigDialogSupport.parseFiniteDecimal(minFwRecoveryField.getText(), "Min. Forward Recovery", 0.0, Double.MAX_VALUE);
                maxBtDd = WorkflowConfigDialogSupport.parseFiniteDecimal(maxBtDdField.getText(), "Max. Backtest DD", 0.0, 100.0);
                maxFwDd = WorkflowConfigDialogSupport.parseFiniteDecimal(maxFwDdField.getText(), "Max. Forward DD", 0.0, 100.0);

                minLtProfit = WorkflowConfigDialogSupport.parseFiniteDecimal(minLtProfitField.getText(), "Min. Langzeit Profit", 0.0, Double.MAX_VALUE);
                minLtTrades = WorkflowConfigDialogSupport.parsePositiveInteger(minLtTradesField.getText(), "Min. Langzeit Trades");
                minLtRecovery = WorkflowConfigDialogSupport.parseFiniteDecimal(minLtRecoveryField.getText(), "Min. Langzeit Recovery", 0.0, Double.MAX_VALUE);
                maxLtDd = WorkflowConfigDialogSupport.parseFiniteDecimal(maxLtDdField.getText(), "Max. Langzeit DD", 0.0, 100.0);
                minLtPf = WorkflowConfigDialogSupport.parseFiniteDecimal(minLtPfField.getText(), "Min. Langzeit Profit Factor", 0.0, Double.MAX_VALUE);

                paramDiffPct = WorkflowConfigDialogSupport.parseFiniteDecimal(paramDiffField.getText(), "Parameter-Differenz", 0.0, 100.0) / 100.0;
                tradeDiffPct = WorkflowConfigDialogSupport.parseFiniteDecimal(tradeDiffField.getText(), "Trade-Differenz", 0.0, 100.0) / 100.0;
            } catch (IllegalArgumentException ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.initOwner(stage);
                alert.setTitle("Ungültige Filtereinstellung");
                alert.setHeaderText("Die Filterwerte konnten nicht gespeichert werden.");
                alert.setContentText(ex.getMessage());
                alert.showAndWait();
                return;
            }

            engine.setMinBtProfit(minBtProfit);
            engine.setMinFwProfit(minFwProfit);
            engine.setMinBtTrades(minBtTrades);
            engine.setMinFwTrades(minFwTrades);
            engine.setMinBtRecovery(minBtRecovery);
            engine.setMinFwRecovery(minFwRecovery);
            engine.setMaxBtDd(maxBtDd);
            engine.setMaxFwDd(maxFwDd);

            engine.setLongtermFromDate(ltFromPicker.getValue());
            engine.setLongtermToDate(ltToPicker.getValue());
            engine.setMaxLongtermCandidates(maxLtCandSpin.getValue());
            engine.setMinLtProfit(minLtProfit);
            engine.setMinLtTrades(minLtTrades);
            engine.setMinLtRecovery(minLtRecovery);
            engine.setMaxLtDd(maxLtDd);
            engine.setMinLtPf(minLtPf);

            engine.setParamDiffPct(paramDiffPct);
            engine.setTradeDiffPct(tradeDiffPct);
            engine.setMinDifferentParams(minDiffParamsSpin.getValue());
            engine.setMaxStrategiesToSelect(maxStratsSpin.getValue());
            engine.saveState();
            stage.close();
        });

        Button mainInfoBtn = DocHelper.createThickCircularInfoButton("Erklärung aller Indizes und Kennzahlen", () -> {
            DocHelper.showAllIndicesDocDialog(cancel.getScene() != null ? cancel.getScene().getWindow() : null);
        });
        Button diversityInfoBtn = DocHelper.createThickCircularCyanInfoButton("Erklärung des Diversitäts-Filters & Strategie-Auswahl", () -> {
            DocHelper.showDiversityDocDialog(cancel.getScene() != null ? cancel.getScene().getWindow() : null);
        });
        Button weightsBtn = new Button("Score-Gewichtung...");
        weightsBtn.getStyleClass().add("button");
        weightsBtn.setOnAction(e -> {
            WorkflowScoreWeightsConfigDialog.showScoreWeightsDialog(stage);
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bottomRow.getChildren().addAll(mainInfoBtn, diversityInfoBtn, weightsBtn, spacer, cancel, save);
        layout.getChildren().add(bottomRow);

        stage.setScene(new Scene(layout));
        WorkflowConfigDialogSupport.applyTheme(stage, owner);
        stage.showAndWait();
    }

}
