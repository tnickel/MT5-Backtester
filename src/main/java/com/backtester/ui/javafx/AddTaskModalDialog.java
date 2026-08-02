package com.backtester.ui.javafx;

import com.backtester.workflow.WorkflowTask;
import com.backtester.workflow.WorkflowTask.TaskType;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * StrategyQuant-Style "Add new task" Modal Dialog.
 * Enables users to pick task categories to insert into their Custom Project pipeline.
 */
public class AddTaskModalDialog {

    private WorkflowTask selectedTask = null;

    public static WorkflowTask show(Window owner) {
        AddTaskModalDialog dialog = new AddTaskModalDialog();
        return dialog.display(owner);
    }

    private WorkflowTask display(Window owner) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) stage.initOwner(owner);
        stage.setTitle("Add new task");

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: #0b0d13;");
        root.setPrefWidth(650);

        Label header = new Label("Choose task type");
        header.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        header.setTextFill(Color.web("#00e5ff"));

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setAlignment(Pos.CENTER);

        TaskType[] types = TaskType.userSelectableValues();
        int col = 0;
        int row = 0;

        for (TaskType type : types) {
            Button btn = new Button(type.getDisplayName());
            btn.setPrefWidth(280);
            btn.setPrefHeight(45);
            btn.setStyle(
                "-fx-background-color: rgba(30, 36, 50, 0.9); " +
                "-fx-text-fill: #e6e9f0; " +
                "-fx-font-weight: bold; " +
                "-fx-font-size: 13px; " +
                "-fx-border-color: #3e4555; " +
                "-fx-border-radius: 6; " +
                "-fx-background-radius: 6; " +
                "-fx-cursor: hand;"
            );

            btn.setTooltip(new Tooltip(type.getDescription()));

            btn.setOnMouseEntered(e -> btn.setStyle(
                "-fx-background-color: rgba(0, 229, 255, 0.15); " +
                "-fx-text-fill: #00e5ff; " +
                "-fx-font-weight: bold; " +
                "-fx-font-size: 13px; " +
                "-fx-border-color: #00e5ff; " +
                "-fx-border-radius: 6; " +
                "-fx-background-radius: 6; " +
                "-fx-cursor: hand;"
            ));

            btn.setOnMouseExited(e -> btn.setStyle(
                "-fx-background-color: rgba(30, 36, 50, 0.9); " +
                "-fx-text-fill: #e6e9f0; " +
                "-fx-font-weight: bold; " +
                "-fx-font-size: 13px; " +
                "-fx-border-color: #3e4555; " +
                "-fx-border-radius: 6; " +
                "-fx-background-radius: 6; " +
                "-fx-cursor: hand;"
            ));

            btn.setOnAction(e -> {
                this.selectedTask = new WorkflowTask(type.getDisplayName(), type);
                stage.close();
            });

            grid.add(btn, col, row);
            col++;
            if (col > 1) {
                col = 0;
                row++;
            }
        }

        HBox bottomBox = new HBox(15);
        bottomBox.setAlignment(Pos.CENTER_RIGHT);
        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("button-cancel");
        closeBtn.setOnAction(e -> stage.close());
        bottomBox.getChildren().add(closeBtn);

        root.getChildren().addAll(header, grid, new Separator(), bottomBox);

        Scene scene = new Scene(root);
        if (owner != null && owner.getScene() != null && !owner.getScene().getStylesheets().isEmpty()) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        stage.setScene(scene);
        stage.showAndWait();

        return selectedTask;
    }
}
