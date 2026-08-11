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
 * Step 5: validation configuration dialog.
 */
public final class WorkflowStep5ConfigDialog {

    private WorkflowStep5ConfigDialog() {}

    // ─── Step 5: KI Analysis Setup ──────────────────────────────────────────────
    public static void showStep5Dialog(WorkflowEngine engine, Window owner) {
        Stage stage = new Stage();
        stage.setTitle("Schritt 5: KI Bewertung & OpenRouter Setup");

        VBox layout = new VBox(15);
        layout.setStyle("-fx-background-color: #0b0d13; -fx-padding: 20;");
        layout.setPrefWidth(650);
        layout.setPrefHeight(640);

        Label title = new Label("KI STABILITÄTS-ANALYSE EINSTELLUNGEN");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#00e5ff"));
        layout.getChildren().add(title);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.getStyleClass().add("sci-fi-panel");

        grid.add(new Label("OpenRouter API Key:"), 0, 0);
        PasswordField keyField = new PasswordField();
        keyField.setText(engine.getOpenRouterApiKey());
        keyField.setPrefWidth(350);
        grid.add(keyField, 1, 0);

        grid.add(new Label("LLM Modell:"), 0, 1);
        ComboBox<String> modelCombo = new ComboBox<>();
        modelCombo.getItems().addAll(
            "openai/gpt-4o-mini",
            "moonshotai/kimi-k2.6",
            "anthropic/claude-3-haiku",
            "google/gemini-2.5-flash",
            "google/gemini-3-flash-preview"
        );
        modelCombo.setEditable(true);
        modelCombo.setValue(engine.getOpenRouterModel());
        grid.add(modelCombo, 1, 1);

        grid.add(new Label("Gewichtung Performance (0.0 - 1.0):"), 0, 2);
        TextField perfWeightField = new TextField(String.valueOf(engine.getPerformanceWeight()));
        perfWeightField.setStyle("-fx-control-inner-background: #1a1d27; -fx-text-fill: white; -fx-font-size: 14px;");
        grid.add(perfWeightField, 1, 2);

        grid.add(new Label("Gewichtung Stabilität (0.0 - 1.0):"), 0, 3);
        TextField stabWeightField = new TextField(String.valueOf(engine.getStabilityWeight()));
        stabWeightField.setStyle("-fx-control-inner-background: #1a1d27; -fx-text-fill: white; -fx-font-size: 14px;");
        grid.add(stabWeightField, 1, 3);

        layout.getChildren().add(grid);

        VBox promptBox = new VBox(5);
        VBox.setVgrow(promptBox, Priority.ALWAYS);
        promptBox.getChildren().add(new Label("Custom System Prompt:"));
        TextArea promptArea = new TextArea(engine.getOpenRouterPrompt());
        promptArea.setFont(Font.font("Consolas", 12));
        promptArea.setWrapText(true);
        VBox.setVgrow(promptArea, Priority.ALWAYS);
        promptBox.getChildren().add(promptArea);
        layout.getChildren().add(promptBox);

        HBox bottomRow = new HBox(15);
        bottomRow.setAlignment(Pos.CENTER_RIGHT);
        Button cancel = new Button("Abbrechen");
        cancel.getStyleClass().add("button-cancel");
        cancel.setOnAction(e -> stage.close());
        Button save = new Button("Speichern");
        save.getStyleClass().add("button-start");
        save.setOnAction(e -> {
            engine.setOpenRouterApiKey(keyField.getText().trim());
            engine.setOpenRouterModel(modelCombo.getValue());
            engine.setOpenRouterPrompt(promptArea.getText().trim());
            try {
                engine.setPerformanceWeight(Double.parseDouble(perfWeightField.getText().trim()));
            } catch (Exception ignored) {}
            try {
                engine.setStabilityWeight(Double.parseDouble(stabWeightField.getText().trim()));
            } catch (Exception ignored) {}
            engine.savePreferences();
            engine.saveState();
            stage.close();
        });

        bottomRow.getChildren().addAll(cancel, save);
        layout.getChildren().add(bottomRow);

        stage.setScene(new Scene(layout));
        WorkflowConfigDialogSupport.applyTheme(stage, owner);
        stage.showAndWait();
    }

}
