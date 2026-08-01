package com.backtester.ui.javafx;

import com.backtester.database.DatabaseManager;
import com.backtester.workflow.CustomProject;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;
import java.util.function.Consumer;

/**
 * StrategyQuant-Style Custom Projects Overview Dashboard.
 * Displays the list of all defined custom project workflows.
 */
public class CustomProjectsOverviewView {

    private final VBox root;
    private final VBox projectsListBox;
    private final ObservableList<CustomProject> projectsList;
    private Consumer<CustomProject> onOpenProjectCallback;

    public CustomProjectsOverviewView() {
        this.projectsList = FXCollections.observableArrayList();

        root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: transparent;");

        // Top Header Row
        HBox headerRow = new HBox(15);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Custom projects");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        title.setTextFill(Color.web("#e6e9f0"));

        Button refreshBtn = new Button("🔄");
        refreshBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #00e5ff; -fx-font-size: 16px; -fx-cursor: hand;");
        refreshBtn.setOnAction(e -> reloadProjects());

        headerRow.getChildren().addAll(title, refreshBtn);
        root.getChildren().add(headerRow);

        // Projects Container Scrollable Box
        projectsListBox = new VBox(10);
        projectsListBox.setPadding(new Insets(5));
        
        ScrollPane scrollPane = new ScrollPane(projectsListBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        root.getChildren().add(scrollPane);

        // Bottom Action Bar
        HBox actionsRow = new HBox(15);
        actionsRow.setAlignment(Pos.CENTER);

        Button createBtn = new Button("➕ Create new project");
        createBtn.getStyleClass().add("button-start");
        createBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        createBtn.setOnAction(e -> createNewProject());

        actionsRow.getChildren().addAll(createBtn);
        root.getChildren().add(actionsRow);

        reloadProjects();
    }

    public VBox getView() {
        return root;
    }

    public void setOnOpenProjectCallback(Consumer<CustomProject> callback) {
        this.onOpenProjectCallback = callback;
    }

    public void reloadProjects() {
        projectsListBox.getChildren().clear();
        List<CustomProject> loaded = DatabaseManager.getInstance().getAllCustomProjects();
        
        if (loaded.isEmpty()) {
            // Seed a default project template if DB is empty
            CustomProject defaultProj = CustomProject.createDefaultTemplate("EURUSD Breakout H1 - StrategyQuant Flow", "", "EURUSD", "H1");
            DatabaseManager.getInstance().saveCustomProject(defaultProj);
            loaded.add(defaultProj);
        }

        projectsList.setAll(loaded);

        for (CustomProject proj : projectsList) {
            projectsListBox.getChildren().add(createProjectRowCard(proj));
        }
    }

    private HBox createProjectRowCard(CustomProject proj) {
        HBox row = new HBox(15);
        row.setPadding(new Insets(12, 18, 12, 18));
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("sci-fi-panel");
        row.setStyle(
            "-fx-background-color: rgba(26, 30, 40, 0.85); " +
            "-fx-border-color: #2e3545; " +
            "-fx-border-width: 1.5; " +
            "-fx-border-radius: 6; " +
            "-fx-background-radius: 6;"
        );

        // Name & Symbol
        VBox titleBox = new VBox(3);
        titleBox.setPrefWidth(300);
        Label nameLbl = new Label(proj.getName());
        nameLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        nameLbl.setTextFill(Color.web("#e6e9f0"));

        Label detailsLbl = new Label(proj.getSymbol() + " (" + proj.getPeriod() + ") • " + (proj.getExpert().isEmpty() ? "Kein EA" : proj.getExpert()));
        detailsLbl.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 11px;");
        titleBox.getChildren().addAll(nameLbl, detailsLbl);

        // Links / Shortcuts: [ Tasks (X) ] [ Engine ] [ Results ]
        HBox shortcutsBox = new HBox(10);
        shortcutsBox.setAlignment(Pos.CENTER_LEFT);

        Hyperlink tasksLink = new Hyperlink("[ Tasks (" + proj.getTasks().size() + ") ]");
        tasksLink.setStyle("-fx-text-fill: #00e5ff; -fx-font-weight: bold;");
        tasksLink.setOnAction(e -> {
            if (onOpenProjectCallback != null) {
                onOpenProjectCallback.accept(proj);
            }
        });

        Hyperlink resultsLink = new Hyperlink("[ Results ]");
        resultsLink.setStyle("-fx-text-fill: #ffd740; -fx-font-weight: bold;");
        resultsLink.setOnAction(e -> {
            if (onOpenProjectCallback != null) {
                onOpenProjectCallback.accept(proj);
            }
        });

        shortcutsBox.getChildren().addAll(tasksLink, resultsLink);

        // Progress bar placeholder
        ProgressBar pb = new ProgressBar(0);
        pb.setPrefWidth(180);
        HBox.setHgrow(pb, Priority.ALWAYS);

        // Execution buttons
        HBox runBox = new HBox(8);
        runBox.setAlignment(Pos.CENTER_RIGHT);

        Button startBtn = new Button("▶ Start");
        startBtn.getStyleClass().add("button-start");
        startBtn.setOnAction(e -> {
            if (onOpenProjectCallback != null) {
                onOpenProjectCallback.accept(proj);
            }
        });

        Button deleteBtn = new Button("🗑");
        deleteBtn.getStyleClass().add("button-cancel");
        deleteBtn.setTooltip(new Tooltip("Projekt löschen"));
        deleteBtn.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Möchten Sie das Projekt \"" + proj.getName() + "\" wirklich löschen?", ButtonType.YES, ButtonType.NO);
            alert.initOwner(root.getScene() != null ? root.getScene().getWindow() : null);
            alert.showAndWait().ifPresent(res -> {
                if (res == ButtonType.YES) {
                    DatabaseManager.getInstance().deleteCustomProject(proj.getId());
                    reloadProjects();
                }
            });
        });

        runBox.getChildren().addAll(startBtn, deleteBtn);

        row.getChildren().addAll(titleBox, shortcutsBox, pb, runBox);
        return row;
    }

    private void createNewProject() {
        TextInputDialog dialog = new TextInputDialog("Neues Custom Projekt " + (projectsList.size() + 1));
        dialog.setTitle("Neues Projekt erstellen");
        dialog.setHeaderText("Geben Sie einen Namen für das neue Projekt ein:");
        dialog.setContentText("Name:");

        dialog.getDialogPane().setStyle("-fx-background-color: #0b0d13;");
        if (root.getScene() != null && !root.getScene().getStylesheets().isEmpty()) {
            dialog.getDialogPane().getStylesheets().addAll(root.getScene().getStylesheets());
        }

        dialog.showAndWait().ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                CustomProject newProj = CustomProject.createDefaultTemplate(name.trim(), "", "EURUSD", "H1");
                DatabaseManager.getInstance().saveCustomProject(newProj);
                reloadProjects();
                if (onOpenProjectCallback != null) {
                    onOpenProjectCallback.accept(newProj);
                }
            }
        });
    }
}
