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
 * Dedicated diversity clustering config dialog.
 */
public final class WorkflowDiversityConfigDialog {

    private WorkflowDiversityConfigDialog() {}

    /** Dedicated, single-databank settings dialog for a custom-project clustering task. */
    public static void showDiversityClusteringDialog(WorkflowTask task,
                                                      List<String> databankNames,
                                                      Window owner,
                                                      Runnable onSave) {
        if (task == null) throw new IllegalArgumentException("Kein Clustering-Task ausgewählt.");
        Stage stage = new Stage();
        stage.setTitle("Diversitäts-Clustering konfigurieren");
        stage.setResizable(false);

        VBox layout = new VBox(16);
        layout.setStyle("-fx-background-color: #0b0d13; -fx-padding: 24;");
        layout.setPrefWidth(720);

        Label title = new Label("DIVERSITÄTS-CLUSTERING");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 19));
        title.setTextFill(Color.web("#00e5ff"));

        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);
        Button infoButton = DocHelper.createThickCircularCyanInfoButton(
                "Ausführliche Erklärung der Diversitätsfilterung",
                () -> DocHelper.showCustomProjectDiversityDocDialog(stage));
        HBox titleRow = new HBox(12, title, titleSpacer, infoButton);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label explanation = new Label(
                "Dieses Modul clustert ausschließlich die ausgewählte Quell-Databank. " +
                "Für Langzeit-Ergebnisse wird ein eigener Clustering-Task hinter dem Retester angelegt."
        );
        explanation.setWrapText(true);
        explanation.setStyle("-fx-text-fill: #9aa4b5; -fx-font-size: 12px;");

        LinkedHashSet<String> availableDatabanks = new LinkedHashSet<>();
        if (databankNames != null) {
            for (String name : databankNames) {
                if (name != null && !name.isBlank()) availableDatabanks.add(name.trim());
            }
        }
        availableDatabanks.add(task.getSourceDatabank());
        availableDatabanks.add(task.getTargetDatabank());

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(14);
        grid.getStyleClass().add("sci-fi-panel");

        TextField moduleNameField = new TextField(task.getName());
        moduleNameField.setPrefWidth(480);
        grid.add(new Label("Modulname:"), 0, 0);
        grid.add(moduleNameField, 1, 0, 3, 1);

        ComboBox<String> sourceCombo = new ComboBox<>(FXCollections.observableArrayList(availableDatabanks));
        sourceCombo.setValue(task.getSourceDatabank());
        sourceCombo.setPrefWidth(230);
        grid.add(new Label("Quell-Databank:"), 0, 1);
        grid.add(sourceCombo, 1, 1);

        ComboBox<String> targetCombo = new ComboBox<>(FXCollections.observableArrayList(availableDatabanks));
        targetCombo.setValue(task.getTargetDatabank());
        targetCombo.setEditable(true);
        targetCombo.setPrefWidth(230);
        grid.add(new Label("Ziel-Databank:"), 2, 1);
        grid.add(targetCombo, 3, 1);

        TextField parameterDifferenceField = new TextField(
                String.format(Locale.US, "%.0f", task.getDiversityParamDiffPct() * 100));
        grid.add(new Label("Parameter-Differenz %:"), 0, 2);
        grid.add(parameterDifferenceField, 1, 2);

        TextField tradeDifferenceField = new TextField(
                String.format(Locale.US, "%.0f", task.getDiversityTradeDiffPct() * 100));
        grid.add(new Label("Trade-Differenz %:"), 2, 2);
        grid.add(tradeDifferenceField, 3, 2);

        TextField minimumDifferentParametersField = new TextField(
                String.valueOf(task.getDiversityMinDifferentParams()));
        grid.add(new Label("Min. differente Parameter:"), 0, 3);
        grid.add(minimumDifferentParametersField, 1, 3);

        TextField maximumStrategiesField = new TextField(
                String.valueOf(task.getDiversityMaxStrategies()));
        grid.add(new Label("Max. Strategien (Ziel):"), 2, 3);
        grid.add(maximumStrategiesField, 3, 3);

        CheckBox rankByScoreCheckBox = new CheckBox("Vor dem Clustering nach Score absteigend sortieren");
        rankByScoreCheckBox.setSelected(task.isDiversityRankByScore());
        rankByScoreCheckBox.setTooltip(new Tooltip(
                "Nur endliche Scores werden berücksichtigt; Score-Gleichstände werden über die Passnummer aufgelöst."));
        grid.add(rankByScoreCheckBox, 0, 4, 4, 1);

        Label routingHint = new Label(
                "Ohne Score-Sortierung bestimmt die Reihenfolge in der Quell-Databank die Priorität. " +
                "Performance-Filter und Retests werden separat im Workflow konfiguriert."
        );
        routingHint.setWrapText(true);
        routingHint.setStyle("-fx-text-fill: #ffd740; -fx-font-size: 12px;");

        Button cancel = new Button("Abbrechen");
        cancel.getStyleClass().add("button-cancel");
        cancel.setOnAction(e -> stage.close());

        Button save = new Button("Speichern");
        save.getStyleClass().add("button-start");
        save.setDefaultButton(true);
        save.setOnAction(e -> {
            String targetName = targetCombo.isEditable()
                    ? targetCombo.getEditor().getText() : targetCombo.getValue();
            try {
                WorkflowConfigDialogSupport.applyDiversityTaskSettings(
                        task,
                        moduleNameField.getText(),
                        sourceCombo.getValue(),
                        targetName,
                        parameterDifferenceField.getText(),
                        tradeDifferenceField.getText(),
                        minimumDifferentParametersField.getText(),
                        maximumStrategiesField.getText(),
                        rankByScoreCheckBox.isSelected());
                if (onSave != null) onSave.run();
                stage.close();
            } catch (IllegalArgumentException ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.initOwner(stage);
                alert.setTitle("Ungültige Clustering-Einstellung");
                alert.setHeaderText("Die Einstellungen konnten nicht gespeichert werden.");
                alert.setContentText(ex.getMessage());
                alert.showAndWait();
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(12, spacer, cancel, save);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        layout.getChildren().addAll(titleRow, explanation, grid, routingHint, new Separator(), buttons);
        stage.setScene(new Scene(layout));
        WorkflowConfigDialogSupport.applyTheme(stage, owner);
        stage.showAndWait();
    }

}
