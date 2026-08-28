package com.backtester.ui.javafx;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.util.List;
import java.util.function.Supplier;

/**
 * Floating always-on-top status Stage shown while a workflow or single-step run is active.
 */
public class WorkflowExecutionStatusWindow {

    private final Supplier<Window> ownerWindow;
    private final Supplier<List<String>> stylesheets;

    private Stage executionStatusStage;
    private Label executionStatusTitleLabel;
    private Label executionStatusTaskLabel;
    private ProgressBar executionStatusBar;
    private Label executionStatusPercentLabel;
    private Label executionStatusDetailLabel;

    public WorkflowExecutionStatusWindow(Supplier<Window> ownerWindow,
                                         Supplier<List<String>> stylesheets) {
        this.ownerWindow = ownerWindow;
        this.stylesheets = stylesheets;
    }

    public void show(String modeTitle, String taskName, String detail) {
        Runnable show = () -> {
            ensureCreated();
            if (executionStatusTitleLabel != null) {
                executionStatusTitleLabel.setText("Workflow läuft — " + modeTitle);
            }
            update(taskName, 0.0, "0%", detail);
            if (executionStatusStage != null && !executionStatusStage.isShowing()) {
                Window owner = ownerWindow != null ? ownerWindow.get() : null;
                if (owner != null) {
                    executionStatusStage.setX(owner.getX() + Math.max(40, owner.getWidth() - 460));
                    executionStatusStage.setY(owner.getY() + 90);
                }
                executionStatusStage.show();
            }
        };
        if (Platform.isFxApplicationThread()) show.run();
        else Platform.runLater(show);
    }

    public void update(String taskName, double progress, String percentText, String detail) {
        if (executionStatusStage == null) return;
        if (executionStatusTaskLabel != null) {
            executionStatusTaskLabel.setText(
                    taskName == null || taskName.isBlank() ? "Aktueller Task: —" : taskName);
        }
        if (executionStatusBar != null) executionStatusBar.setProgress(progress);
        if (executionStatusPercentLabel != null) executionStatusPercentLabel.setText(percentText);
        if (executionStatusDetailLabel != null) {
            executionStatusDetailLabel.setText(detail == null ? "" : detail);
        }
    }

    public void hide() {
        if (executionStatusStage != null && executionStatusStage.isShowing()) {
            executionStatusStage.hide();
        }
    }

    private void ensureCreated() {
        if (executionStatusStage != null) return;

        executionStatusTitleLabel = new Label("Workflow läuft");
        executionStatusTitleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        executionStatusTitleLabel.setTextFill(Color.web("#e6e9f0"));

        executionStatusTaskLabel = new Label("Aktueller Task: —");
        executionStatusTaskLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        executionStatusTaskLabel.setTextFill(Color.web("#00e676"));
        executionStatusTaskLabel.setWrapText(true);

        executionStatusBar = new ProgressBar(0);
        executionStatusBar.setMaxWidth(Double.MAX_VALUE);
        executionStatusBar.setPrefHeight(16);
        HBox.setHgrow(executionStatusBar, Priority.ALWAYS);

        executionStatusPercentLabel = new Label("0%");
        executionStatusPercentLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        executionStatusPercentLabel.setTextFill(Color.web("#00e676"));
        executionStatusPercentLabel.setMinWidth(48);
        executionStatusPercentLabel.setAlignment(Pos.CENTER_RIGHT);

        HBox barRow = new HBox(10, executionStatusBar, executionStatusPercentLabel);
        barRow.setAlignment(Pos.CENTER_LEFT);

        executionStatusDetailLabel = new Label("—");
        executionStatusDetailLabel.setFont(Font.font("Segoe UI", 11));
        executionStatusDetailLabel.setTextFill(Color.web("#94a3b8"));
        executionStatusDetailLabel.setWrapText(true);

        VBox content = new VBox(10,
                executionStatusTitleLabel,
                executionStatusTaskLabel,
                barRow,
                executionStatusDetailLabel
        );
        content.setPadding(new Insets(16));
        content.setPrefWidth(420);
        content.setMinHeight(190);
        content.setStyle(
                "-fx-background-color: #121722; -fx-border-color: #00e676; -fx-border-width: 2; "
                        + "-fx-border-radius: 8; -fx-background-radius: 8;"
        );

        executionStatusStage = new Stage(StageStyle.UTILITY);
        executionStatusStage.setTitle("Workflow-Status");
        executionStatusStage.initModality(Modality.NONE);
        executionStatusStage.setAlwaysOnTop(true);
        executionStatusStage.setResizable(false);

        Window owner = ownerWindow != null ? ownerWindow.get() : null;
        List<String> sheets = stylesheets != null ? stylesheets.get() : null;
        if (owner != null) {
            executionStatusStage.initOwner(owner);
        }
        if (sheets != null && !sheets.isEmpty()) {
            javafx.scene.Scene statusScene = new javafx.scene.Scene(content);
            statusScene.getStylesheets().addAll(sheets);
            statusScene.setFill(Color.web("#121722"));
            executionStatusStage.setScene(statusScene);
        } else {
            executionStatusStage.setScene(new javafx.scene.Scene(content));
        }
        executionStatusStage.setOnCloseRequest(e -> {
            // Closing only hides the floating window; the run continues.
            // Progress tab still shows the live status.
        });
    }
}
