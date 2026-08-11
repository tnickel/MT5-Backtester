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
 * Step 6: robustness scorecard dialog.
 */
public final class WorkflowStep6ConfigDialog {

    private WorkflowStep6ConfigDialog() {}

    public static void showStep6Dialog(WorkflowEngine engine, Window owner) {
        Stage stage = new Stage();
        stage.setTitle("Schritt 6: Finales Portfolio & Export");

        VBox layout = new VBox(15);
        layout.setStyle("-fx-background-color: #0b0d13; -fx-padding: 20;");
        layout.setPrefWidth(850);
        layout.setPrefHeight(540);

        Label title = new Label("PORTFOLIO DER 3-5 BESTEN STRATEGIEN");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#00e5ff"));
        layout.getChildren().add(title);

        TableView<CombinedPass> table = new TableView<>();
        table.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<CombinedPass, Integer> passCol = new TableColumn<>("Pass");
        passCol.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getPassNumber()));
        passCol.setPrefWidth(65);
        passCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<CombinedPass, Double> scoreCol = new TableColumn<>();
        scoreCol.setGraphic(DocHelper.createHeaderWithTooltip("Score",
            "Unified Score (0-100):\nGewichteter Gesamtwert aus 8 Säulen echter MT5-Messdaten (Profit, DD, PF, Sharpe etc.). Konfigurierbar über das Regler-Symbol. Zeigt die beste Gesamtperformance."));
        scoreCol.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getScore()));
        scoreCol.setPrefWidth(100);
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
            "Robustness Scorecard (0-100):\nGewichteter Score aus 8 Säulen echter MT5-Messdaten: Profitabilität (BT+FW), FW/BT-Konsistenz, Risiko-Verhältnis, Sharpe Ratio, Stichprobengröße, FW-Trades und Erholungsfaktor."));
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

        TableColumn<CombinedPass, String> kiScoreCol = new TableColumn<>("KI-Stabilität");
        kiScoreCol.setCellValueFactory(c -> {
            int score = engine.getKiScoreForPass(c.getValue());
            return new javafx.beans.property.SimpleStringProperty(score >= 0 ? String.valueOf(score) : "—");
        });
        kiScoreCol.setStyle("-fx-alignment: CENTER;");
        kiScoreCol.setPrefWidth(90);

        TableColumn<CombinedPass, String> weightedScoreCol = new TableColumn<>("Gesamtwert");
        weightedScoreCol.setCellValueFactory(c -> {
            CombinedPass cp = c.getValue();
            double perfScore = cp.getScore();
            int kiScore = engine.getKiScoreForPass(cp);
            if (kiScore < 0) {
                return new javafx.beans.property.SimpleStringProperty(String.format(Locale.US, "%.1f", perfScore));
            }
            double wScore = engine.getPerformanceWeight() * perfScore + engine.getStabilityWeight() * kiScore;
            return new javafx.beans.property.SimpleStringProperty(String.format(Locale.US, "%.1f", wScore));
        });
        weightedScoreCol.setStyle("-fx-alignment: CENTER; -fx-font-weight: bold; -fx-text-fill: #00e5ff;");
        weightedScoreCol.setPrefWidth(95);

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
        btTr.setPrefWidth(80);
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

        TableColumn<CombinedPass, Double> btDd = new TableColumn<>("BT DD%");
        btDd.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getBtDd()));
        btDd.setPrefWidth(85);
        btDd.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || Double.isNaN(item)) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.format(Locale.US, "%.2f %%", item));
                    if (item > 25) {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #ff5252;");
                    } else if (item > 15) {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #ffd740;");
                    } else {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #00e676;");
                    }
                }
            }
        });

        TableColumn<CombinedPass, Double> fwProf = new TableColumn<>("FW Profit");
        fwProf.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getFwProfit()));
        fwProf.setPrefWidth(95);
        fwProf.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
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

        TableColumn<CombinedPass, Integer> fwTr = new TableColumn<>("FW Trades");
        fwTr.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getFwTrades()));
        fwTr.setPrefWidth(80);
        fwTr.setCellFactory(col -> new TableCell<CombinedPass, Integer>() {
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

        table.getColumns().addAll(passCol, scoreCol, robScoreCol, kiScoreCol, weightedScoreCol, btProf, btTr, btDd, fwProf, fwTr);

        List<CombinedPass> results = engine.getFinalSelectedPasses();
        table.getItems().setAll(results);
        layout.getChildren().add(table);

        // Export Directory Configuration Row
        GridPane exportGrid = new GridPane();
        exportGrid.setHgap(10);
        exportGrid.setVgap(5);
        exportGrid.getStyleClass().add("sci-fi-panel");
        exportGrid.setPadding(new Insets(10));

        exportGrid.add(new Label("Export-Verzeichnis:"), 0, 0);
        TextField exportDirField = new TextField(AppConfig.getInstance().getExportDirectory().toString());
        exportDirField.setPrefWidth(550);

        Button browseBtn = new Button("Durchsuchen...");
        browseBtn.getStyleClass().add("button");
        browseBtn.setOnAction(evt -> {
            javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
            chooser.setTitle("Export-Verzeichnis wählen");
            File dir = new File(exportDirField.getText());
            if (dir.exists()) {
                chooser.setInitialDirectory(dir);
            }
            File selected = chooser.showDialog(stage);
            if (selected != null) {
                exportDirField.setText(selected.getAbsolutePath());
                AppConfig.getInstance().setExportDirectory(selected.getAbsolutePath());
                AppConfig.getInstance().save();
            }
        });

        // Listen for manual path edits
        exportDirField.textProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && !newV.trim().isEmpty()) {
                AppConfig.getInstance().setExportDirectory(newV.trim());
                AppConfig.getInstance().save();
            }
        });

        HBox pathBox = new HBox(10, exportDirField, browseBtn);
        HBox.setHgrow(exportDirField, Priority.ALWAYS);
        exportGrid.add(pathBox, 1, 0);

        exportGrid.add(new Label("Sammelordner (gute Str.):"), 0, 1);
        TextField bestDirField = new TextField(AppConfig.getInstance().getBestExportDirectory().toString());
        bestDirField.setPrefWidth(550);

        Button browseBestBtn = new Button("Durchsuchen...");
        browseBestBtn.getStyleClass().add("button");
        browseBestBtn.setOnAction(evt -> {
            javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
            chooser.setTitle("Sammelordner für gute Strategien wählen");
            File dir = new File(bestDirField.getText());
            if (dir.exists()) {
                chooser.setInitialDirectory(dir);
            }
            File selected = chooser.showDialog(stage);
            if (selected != null) {
                bestDirField.setText(selected.getAbsolutePath());
                AppConfig.getInstance().setBestExportDirectory(selected.getAbsolutePath());
                AppConfig.getInstance().save();
            }
        });

        // Listen for manual path edits
        bestDirField.textProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && !newV.trim().isEmpty()) {
                AppConfig.getInstance().setBestExportDirectory(newV.trim());
                AppConfig.getInstance().save();
            }
        });

        HBox bestPathBox = new HBox(10, bestDirField, browseBestBtn);
        HBox.setHgrow(bestDirField, Priority.ALWAYS);
        exportGrid.add(bestPathBox, 1, 1);

        layout.getChildren().add(exportGrid);

        HBox actionsRow = new HBox(15);
        actionsRow.setAlignment(Pos.CENTER_LEFT);
        
        Button exportBtn = new Button("💾 Portfolio exportieren");
        exportBtn.getStyleClass().add("button-start");
        exportBtn.setOnAction(e -> {
            try {
                String expDir = exportDirField.getText().trim();
                String bestDir = bestDirField.getText().trim();
                if (expDir.isEmpty()) {
                    new Alert(Alert.AlertType.WARNING, "Bitte gib ein gültiges Export-Verzeichnis an.").show();
                    return;
                }
                if (bestDir.isEmpty()) {
                    new Alert(Alert.AlertType.WARNING, "Bitte gib ein gültiges Verzeichnis für gute Strategien an.").show();
                    return;
                }
                engine.exportPortfolio(expDir, bestDir);
                new Alert(Alert.AlertType.INFORMATION, "Portfolio erfolgreich exportiert!\nPreset-Dateien (.set) und PDF-Reports wurden erstellt.").show();
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Fehler beim Exportieren des Portfolios:\n" + ex.getMessage()).show();
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("Schließen");
        closeBtn.getStyleClass().add("button");
        closeBtn.setOnAction(e -> stage.close());

        Button mainInfoBtn = DocHelper.createThickCircularInfoButton("Erklärung aller Indizes und Kennzahlen", () -> {
            DocHelper.showAllIndicesDocDialog(closeBtn.getScene() != null ? closeBtn.getScene().getWindow() : null);
        });

        actionsRow.getChildren().addAll(exportBtn, mainInfoBtn, spacer, closeBtn);
        layout.getChildren().add(actionsRow);

        stage.setScene(new Scene(layout));
        WorkflowConfigDialogSupport.applyTheme(stage, owner);
        stage.showAndWait();
    }

}
