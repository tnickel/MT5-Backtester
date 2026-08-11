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
 * Step 4: sensitivity configuration dialog.
 */
public final class WorkflowStep4ConfigDialog {

    private WorkflowStep4ConfigDialog() {}

    // ─── Step 4: Sensitivity Sweep ─────────────────────────────────────────────
    public static void showStep4Dialog(WorkflowEngine engine, Window owner) {
        Stage stage = new Stage();
        stage.setTitle("Schritt 4: Sensitivitäts-Analyse");

        VBox layout = new VBox(15);
        layout.setStyle("-fx-background-color: #0b0d13; -fx-padding: 20;");
        layout.setPrefWidth(850);
        layout.setPrefHeight(650);

        Label title = new Label("SENSITIVITÄT & ROBUSTHEITS-ERGEBNISSE");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#00e5ff"));
        layout.getChildren().add(title);

        VBox infoBox = new VBox(5);
        infoBox.getStyleClass().add("sci-fi-panel");
        infoBox.getChildren().add(new Label("Info: Für jede der in Schritt 3 selektierten Strategien"));
        infoBox.getChildren().add(new Label("wird eine Parameterverschiebung (Sweep) durchgeführt."));
        layout.getChildren().add(infoBox);

        // Parameters Table for verification
        Label paramLabel = new Label("Eingestellte Parameter & Suchräume (aus Schritt 1):");
        paramLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        paramLabel.setTextFill(Color.web("#80d8ff"));
        layout.getChildren().add(paramLabel);

        TableView<EaParameter> paramTable = new TableView<>();
        paramTable.setEditable(false);
        paramTable.setPrefHeight(180);
        paramTable.setStyle("-fx-background-color: transparent;");

        TableColumn<EaParameter, Boolean> optCol = new TableColumn<>("Opt");
        optCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleBooleanProperty(cellData.getValue().isOptimizeEnabled()));
        optCol.setCellFactory(tc -> new TableCell<EaParameter, Boolean>() {
            private final CheckBox checkBox = new CheckBox();
            {
                checkBox.setDisable(true);
                setAlignment(Pos.CENTER);
            }
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    checkBox.setSelected(item);
                    setGraphic(checkBox);
                }
            }
        });
        optCol.setPrefWidth(40);

        TableColumn<EaParameter, String> nameCol = new TableColumn<>("Variable");
        nameCol.setCellValueFactory(cellData -> {
            EaParameter param = cellData.getValue();
            String display = param.getDisplayName();
            if (display == null || display.trim().isEmpty()) {
                display = param.getName();
            }
            return new javafx.beans.property.SimpleStringProperty(display);
        });
        nameCol.setCellFactory(column -> new TableCell<EaParameter, String>() {
            private final Tooltip tooltip = new Tooltip();
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item);
                    EaParameter param = getTableRow() != null ? getTableRow().getItem() : null;
                    if (param != null) {
                        tooltip.setText("Variable: " + param.getName());
                        setTooltip(tooltip);
                    } else {
                        setTooltip(null);
                    }
                }
            }
        });
        nameCol.setPrefWidth(220);

        TableColumn<EaParameter, String> valCol = new TableColumn<>("Wert");
        valCol.setCellValueFactory(new PropertyValueFactory<>("value"));
        valCol.setPrefWidth(100);

        TableColumn<EaParameter, String> startCol = new TableColumn<>("Start");
        startCol.setCellValueFactory(new PropertyValueFactory<>("optimizeStart"));
        startCol.setPrefWidth(90);

        TableColumn<EaParameter, String> stepCol = new TableColumn<>("Schritt");
        stepCol.setCellValueFactory(new PropertyValueFactory<>("optimizeStep"));
        stepCol.setPrefWidth(90);

        TableColumn<EaParameter, String> stopCol = new TableColumn<>("Stopp");
        stopCol.setCellValueFactory(new PropertyValueFactory<>("optimizeEnd"));
        stopCol.setPrefWidth(90);

        paramTable.getColumns().addAll(optCol, nameCol, valCol, startCol, stepCol, stopCol);
        EaParameterTableHelper.configureTable(paramTable, optCol, nameCol, valCol, startCol, stepCol, stopCol);
        if (engine.getEaParameters() != null) {
            paramTable.getItems().setAll(engine.getEaParameters());
        }
        layout.getChildren().add(paramTable);

        // Results Section
        Label resultsLabel = new Label("Robustheits-Ergebnisse (Sensitivitäts-Sweeps):");
        resultsLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        resultsLabel.setTextFill(Color.web("#80d8ff"));
        layout.getChildren().add(resultsLabel);

        // Display current sweep results if any
        if (engine.getSensitivityResults() != null && !engine.getSensitivityResults().isEmpty()) {
            TableView<SensitivityResult> table = new TableView<>();
            table.setPrefHeight(180);

            TableColumn<SensitivityResult, Integer> passCol = new TableColumn<>("Pass");
            passCol.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getOriginalPass().getPassNumber()).asObject());
            passCol.setPrefWidth(80);
            
            TableColumn<SensitivityResult, String> btCvCol = new TableColumn<>();
            HBox btCvHeader = new HBox(3);
            btCvHeader.setAlignment(Pos.CENTER_LEFT);
            Label btCvLabel = new Label("BT CV (worst)");
            btCvLabel.setTooltip(new Tooltip("Backtest Variationskoeffizient"));
            Button btCvInfo = new Button("ℹ");
            btCvInfo.setTooltip(new Tooltip("Klicken für Erklärung des BT CV (worst)"));
            btCvInfo.setStyle("-fx-background-color: transparent; -fx-text-fill: #ffd740; -fx-padding: 0; -fx-font-weight: bold; -fx-cursor: hand;");
            btCvInfo.setOnAction(e -> {
                e.consume();
                WorkflowCvExplanationDialog.showCvExplanationDialog(btCvInfo.getScene() != null ? btCvInfo.getScene().getWindow() : null, false);
            });
            btCvHeader.getChildren().addAll(btCvLabel, btCvInfo);
            btCvCol.setGraphic(btCvHeader);
            btCvCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(String.format("%.2f %%", c.getValue().getOverallCV())));
            btCvCol.setPrefWidth(130);

            TableColumn<SensitivityResult, String> fwCvCol = new TableColumn<>();
            HBox fwCvHeader = new HBox(3);
            fwCvHeader.setAlignment(Pos.CENTER_LEFT);
            Label fwCvLabel = new Label("FW CV (worst)");
            fwCvLabel.setTooltip(new Tooltip("Forward Variationskoeffizient"));
            Button fwCvInfo = new Button("ℹ");
            fwCvInfo.setTooltip(new Tooltip("Klicken für Erklärung des FW CV (worst)"));
            fwCvInfo.setStyle("-fx-background-color: transparent; -fx-text-fill: #ffd740; -fx-padding: 0; -fx-font-weight: bold; -fx-cursor: hand;");
            fwCvInfo.setOnAction(e -> {
                e.consume();
                WorkflowCvExplanationDialog.showCvExplanationDialog(fwCvInfo.getScene() != null ? fwCvInfo.getScene().getWindow() : null, true);
            });
            fwCvHeader.getChildren().addAll(fwCvLabel, fwCvInfo);
            fwCvCol.setGraphic(fwCvHeader);
            fwCvCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().hasForwardCV() ? String.format("%.2f %%", c.getValue().getOverallCVFw()) : "-"));
            fwCvCol.setPrefWidth(130);

            TableColumn<SensitivityResult, String> statusCol = new TableColumn<>("Status");
            statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
            statusCol.setPrefWidth(150);

            table.getColumns().addAll(passCol, btCvCol, fwCvCol, statusCol);
            table.getItems().setAll(engine.getSensitivityResults());
            layout.getChildren().add(table);
        } else {
            Label noRes = new Label("Noch keine Sensitivitätsergebnisse vorhanden.");
            noRes.setStyle("-fx-text-fill: #7e889a;");
            layout.getChildren().add(noRes);
        }

        HBox bottomRow = new HBox(15);
        bottomRow.setAlignment(Pos.CENTER_RIGHT);
        Button ok = new Button("Schließen");
        ok.getStyleClass().add("button-start");
        ok.setOnAction(e -> stage.close());
        bottomRow.getChildren().add(ok);
        layout.getChildren().add(bottomRow);

        stage.setScene(new Scene(layout));
        WorkflowConfigDialogSupport.applyTheme(stage, owner);
        stage.showAndWait();
    }

}
