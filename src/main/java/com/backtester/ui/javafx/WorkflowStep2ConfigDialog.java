package com.backtester.ui.javafx;

import com.backtester.config.EaParameter;
import com.backtester.config.AppConfig;
import com.backtester.config.Preset;
import com.backtester.config.PresetManager;
import javafx.scene.web.WebView;
import com.backtester.config.EaParameterManager;
import com.backtester.engine.BacktestConfig;
import com.backtester.engine.ForwardSplit;
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
 * Step 2: optimizer settings dialog.
 */
public final class WorkflowStep2ConfigDialog {

    private WorkflowStep2ConfigDialog() {}

    // ─── Step 2: Optimizer Settings ───────────────────────────────────────────
    public static void showStep2Dialog(WorkflowEngine engine, Window owner) {
        showStep2Dialog(engine, owner, null);
    }

    public static void showStep2Dialog(WorkflowEngine engine, Window owner, Runnable onSave) {
        Stage stage = new Stage();
        stage.setTitle("Schritt 2: Optimizer-Konfiguration");

        VBox layout = new VBox(15);
        layout.setStyle("-fx-background-color: #0b0d13; -fx-padding: 20;");
        layout.setPrefWidth(450);

        Label title = new Label("OPTIMIERUNGS-METRIKEN & MODUS");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#00e5ff"));
        layout.getChildren().add(title);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(15);
        grid.getStyleClass().add("sci-fi-panel");

        grid.add(new Label("Algorithmus:"), 0, 0);
        ComboBox<String> optModeCombo = new ComboBox<>(FXCollections.observableArrayList(OptimizationConfig.OPTIMIZATION_MODES));
        int currentMode = engine.getOptimizationMode();
        int selectedIndex = 1; // Default to genetic (index 1)
        if (currentMode == 1) {
            selectedIndex = 0; // Complete
        } else if (currentMode == 2) {
            selectedIndex = 1; // Genetic
        }
        optModeCombo.getSelectionModel().select(selectedIndex);
        grid.add(optModeCombo, 1, 0);

        grid.add(new Label("Optimierungsziel:"), 0, 1);
        ComboBox<String> criterionCombo = new ComboBox<>(FXCollections.observableArrayList(OptimizationConfig.OPTIMIZATION_CRITERIA));
        if (engine.getOptimizationCriterion() >= 0 && engine.getOptimizationCriterion() < criterionCombo.getItems().size()) {
            criterionCombo.getSelectionModel().select(engine.getOptimizationCriterion());
        } else {
            criterionCombo.getSelectionModel().select(4); // Recovery factor
        }
        grid.add(criterionCombo, 1, 1);

        grid.add(new Label("Forward-Test:"), 0, 2);
        ComboBox<String> forwardCombo = new ComboBox<>(FXCollections.observableArrayList(OptimizationConfig.FORWARD_MODES));
        if (engine.getForwardMode() >= 0 && engine.getForwardMode() < forwardCombo.getItems().size()) {
            forwardCombo.getSelectionModel().select(engine.getForwardMode());
        } else {
            forwardCombo.getSelectionModel().select(1); // 1/2
        }
        grid.add(forwardCombo, 1, 2);

        grid.add(new Label("Forward Datum:"), 0, 3);
        DatePicker forwardDatePicker = new DatePicker(engine.getForwardDate());
        forwardDatePicker.setConverter(WorkflowConfigDialogSupport.createDateConverter());

        Runnable updateFwdDate = () -> {
            int fMode = forwardCombo.getSelectionModel().getSelectedIndex();
            boolean isCustom = (fMode == 4);
            forwardDatePicker.setDisable(!isCustom && fMode > 0);
            if (!isCustom && fMode > 0 && engine.getFromDate() != null && engine.getToDate() != null && engine.getToDate().isAfter(engine.getFromDate())) {
                forwardDatePicker.setValue(ForwardSplit.computeForwardStartDate(
                        engine.getFromDate(), engine.getToDate(), fMode, null));
            }
        };
        forwardCombo.setOnAction(e -> updateFwdDate.run());
        updateFwdDate.run();
        grid.add(forwardDatePicker, 1, 3);

        layout.getChildren().add(grid);

        HBox bottomRow = new HBox(15);
        bottomRow.setAlignment(Pos.CENTER_RIGHT);
        Button cancel = new Button("Abbrechen");
        cancel.getStyleClass().add("button-cancel");
        cancel.setOnAction(e -> stage.close());
        Button save = new Button("Speichern");
        save.getStyleClass().add("button-start");
        save.setOnAction(e -> {
            int selectedIdx = optModeCombo.getSelectionModel().getSelectedIndex();
            int optMode = (selectedIdx == 0) ? 1 : 2; // 0 -> Complete (1), 1 -> Genetic (2)
            engine.setOptimizationMode(optMode);
            engine.setOptimizationCriterion(criterionCombo.getSelectionModel().getSelectedIndex());
            engine.setForwardMode(forwardCombo.getSelectionModel().getSelectedIndex());
            engine.setForwardDate(forwardDatePicker.getValue());
            engine.saveState();
            if (onSave != null) onSave.run();
            stage.close();
        });

        bottomRow.getChildren().addAll(cancel, save);
        layout.getChildren().add(bottomRow);

        stage.setScene(new Scene(layout));
        WorkflowConfigDialogSupport.applyTheme(stage, owner);
        stage.showAndWait();
    }

}
