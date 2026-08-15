package com.backtester.ui.javafx;

import com.backtester.config.EaParameter;
import com.backtester.database.DatabaseManager;
import com.backtester.workflow.CustomProject;
import com.backtester.workflow.ToTheMoon132GuidedWorkflowFactory;
import com.backtester.workflow.ToTheMoon132TickKillWorkflowFactory;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * StrategyQuant-Style Custom Projects Overview Dashboard.
 * Displays the list of all defined custom project workflows with rename, reorder, and cloning capabilities.
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
        List<CustomProject> loaded;
        try {
            loaded = DatabaseManager.getInstance().getAllCustomProjects();
        } catch (DatabaseManager.DatabaseAccessException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Database error");
            alert.setHeaderText("Custom projects could not be loaded");
            alert.setContentText("The project database is currently unavailable. No default project was created.");
            alert.show();
            return;
        }

        projectsListBox.getChildren().clear();

        if (loaded.isEmpty()) {
            // Seed a default project template if DB is empty
            CustomProject defaultProj = CustomProject.createDefaultTemplate("EURUSD Breakout H1 - StrategyQuant Flow", "", "EURUSD", "H1");
            DatabaseManager.getInstance().saveCustomProject(defaultProj);
            loaded.add(defaultProj);
        }

        projectsList.setAll(loaded);

        for (int i = 0; i < projectsList.size(); i++) {
            CustomProject proj = projectsList.get(i);
            projectsListBox.getChildren().add(createProjectRowCard(proj, i));
        }
    }

    private HBox createProjectRowCard(CustomProject proj, int index) {
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

        // Reorder buttons (Move Up / Move Down)
        VBox orderBox = new VBox(2);
        orderBox.setAlignment(Pos.CENTER);

        Button upBtn = new Button("▲");
        upBtn.setStyle("-fx-background-color: #1e2432; -fx-text-fill: #00e5ff; -fx-font-size: 10px; -fx-padding: 2 6; -fx-cursor: hand; -fx-border-color: #2e3545; -fx-border-radius: 3;");
        upBtn.setTooltip(new Tooltip("Workflow nach oben verschieben"));
        upBtn.setDisable(index <= 0);
        upBtn.setOnAction(e -> moveProjectUp(index));

        Button downBtn = new Button("▼");
        downBtn.setStyle("-fx-background-color: #1e2432; -fx-text-fill: #00e5ff; -fx-font-size: 10px; -fx-padding: 2 6; -fx-cursor: hand; -fx-border-color: #2e3545; -fx-border-radius: 3;");
        downBtn.setTooltip(new Tooltip("Workflow nach unten verschieben"));
        downBtn.setDisable(index >= projectsList.size() - 1);
        downBtn.setOnAction(e -> moveProjectDown(index));

        orderBox.getChildren().addAll(upBtn, downBtn);

        // Name & Symbol with Rename Pencil Button
        VBox titleBox = new VBox(3);
        titleBox.setPrefWidth(300);

        HBox nameHeader = new HBox(6);
        nameHeader.setAlignment(Pos.CENTER_LEFT);

        Label nameLbl = new Label(proj.getName());
        nameLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        nameLbl.setTextFill(Color.web("#e6e9f0"));
        nameLbl.setStyle("-fx-cursor: hand;");
        nameLbl.setTooltip(new Tooltip("Doppelklick zum Umbenennen"));
        nameLbl.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) renameProject(proj);
        });

        Button renameBtn = new Button("✏");
        renameBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-font-size: 13px; -fx-cursor: hand; -fx-padding: 0 4;");
        renameBtn.setTooltip(new Tooltip("Workflow umbenennen"));
        renameBtn.setOnAction(e -> renameProject(proj));

        nameHeader.getChildren().addAll(nameLbl, renameBtn);

        Label detailsLbl = new Label(proj.getSymbol() + " (" + proj.getPeriod() + ") • " + (proj.getExpert().isEmpty() ? "Kein EA" : proj.getExpert()));
        detailsLbl.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 11px;");
        titleBox.getChildren().addAll(nameHeader, detailsLbl);

        // Links / Shortcuts: [ Tasks (X) ] [ Results ]
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

        Button cloneBtn = new Button("📋 Clone");
        cloneBtn.setStyle("-fx-background-color: #1e2432; -fx-text-fill: #00e5ff; -fx-font-weight: bold; -fx-cursor: hand; -fx-border-color: #00e5ff; -fx-border-radius: 4;");
        cloneBtn.setTooltip(new Tooltip("Workflow duplizieren (für ein neues Währungspaar & Timeframe)"));
        cloneBtn.setOnAction(e -> showCloneWorkflowDialog(proj));

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

        runBox.getChildren().addAll(startBtn, cloneBtn, deleteBtn);

        row.getChildren().addAll(orderBox, titleBox, shortcutsBox, pb, runBox);
        return row;
    }

    private void moveProjectUp(int index) {
        if (index > 0 && index < projectsList.size()) {
            Collections.swap(projectsList, index, index - 1);
            saveAllProjectsOrder();
            reloadProjects();
        }
    }

    private void moveProjectDown(int index) {
        if (index >= 0 && index < projectsList.size() - 1) {
            Collections.swap(projectsList, index, index + 1);
            saveAllProjectsOrder();
            reloadProjects();
        }
    }

    private void saveAllProjectsOrder() {
        for (int i = 0; i < projectsList.size(); i++) {
            CustomProject p = projectsList.get(i);
            p.setSortOrder(i);
            DatabaseManager.getInstance().saveCustomProject(p);
        }
    }

    private void renameProject(CustomProject proj) {
        TextInputDialog dialog = new TextInputDialog(proj.getName());
        dialog.setTitle("Workflow umbenennen");
        dialog.setHeaderText("Geben Sie einen neuen Namen für das Workflow-Projekt ein:");
        dialog.setContentText("Neuer Name:");

        dialog.getDialogPane().setStyle("-fx-background-color: #0b0d13;");
        if (root.getScene() != null && !root.getScene().getStylesheets().isEmpty()) {
            dialog.getDialogPane().getStylesheets().addAll(root.getScene().getStylesheets());
        }

        dialog.showAndWait().ifPresent(newName -> {
            if (!newName.trim().isEmpty() && !newName.trim().equals(proj.getName())) {
                proj.setName(newName.trim());
                DatabaseManager.getInstance().saveCustomProject(proj);
                reloadProjects();
            }
        });
    }

    public void showCloneWorkflowDialog(CustomProject sourceProj) {
        if (sourceProj == null) return;

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        Window owner = root.getScene() != null ? root.getScene().getWindow() : null;
        if (owner != null) stage.initOwner(owner);
        stage.setTitle("Workflow duplizieren / clonen: " + sourceProj.getName());

        VBox rootBox = new VBox(15);
        rootBox.setPadding(new Insets(20));
        rootBox.setStyle("-fx-background-color: #0b0d13;");
        rootBox.setPrefWidth(540);

        Label headerLbl = new Label("📋 Workflow duplizieren");
        headerLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        headerLbl.setTextFill(Color.web("#00e5ff"));

        Label subLbl = new Label("Erstellt eine exakte Kopie des Workflows mit allen Tasks & Einstellungen für ein neues Währungspaar und Timeframe.");
        subLbl.setWrapText(true);
        subLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(14);

        // 1. Symbol / Währungspaar
        Label symbolLbl = new Label("Neues Währungspaar / Symbol:");
        symbolLbl.setStyle("-fx-text-fill: #e6e9f0; -fx-font-weight: bold;");
        ComboBox<String> symbolCombo = new ComboBox<>(FXCollections.observableArrayList(
                "EURUSD", "GBPUSD", "USDJPY", "AUDCAD", "AUDUSD", "NZDUSD", "USDCAD", "USDCHF",
                "EURGBP", "EURJPY", "GBPJPY", "EURCAD", "EURAUD", "GBPAUD", "GBPCAD", "XAUUSD", "BTCUSD"
        ));
        symbolCombo.setEditable(true);
        symbolCombo.getSelectionModel().select(sourceProj.getSymbol());
        symbolCombo.setMaxWidth(Double.MAX_VALUE);
        symbolCombo.setStyle("-fx-background-color: #141822; -fx-text-fill: #00e5ff; -fx-border-color: #232a3b;");
        GridPane.setHgrow(symbolCombo, Priority.ALWAYS);

        // 2. Timeframe / Period
        Label periodLbl = new Label("Neuer Timeframe / Period:");
        periodLbl.setStyle("-fx-text-fill: #e6e9f0; -fx-font-weight: bold;");
        ComboBox<String> periodCombo = new ComboBox<>(FXCollections.observableArrayList(
                "M1", "M5", "M15", "M30", "H1", "H2", "H4", "D1"
        ));
        periodCombo.setEditable(true);
        periodCombo.getSelectionModel().select(sourceProj.getPeriod());
        periodCombo.setMaxWidth(Double.MAX_VALUE);
        periodCombo.setStyle("-fx-background-color: #141822; -fx-text-fill: #00e5ff; -fx-border-color: #232a3b;");
        GridPane.setHgrow(periodCombo, Priority.ALWAYS);

        // 3. New Name
        String defaultClonedName = buildDefaultClonedName(sourceProj, sourceProj.getSymbol(), sourceProj.getPeriod());
        Label nameLbl = new Label("Neuer Workflow-Name:");
        nameLbl.setStyle("-fx-text-fill: #e6e9f0; -fx-font-weight: bold;");
        TextField nameField = new TextField(defaultClonedName);
        nameField.setStyle("-fx-background-color: #141822; -fx-text-fill: #00e5ff; -fx-border-color: #232a3b; -fx-border-radius: 4;");
        GridPane.setHgrow(nameField, Priority.ALWAYS);

        // Auto update name field when symbol or period changes
        symbolCombo.valueProperty().addListener((obs, oldV, newV) -> {
            nameField.setText(buildDefaultClonedName(sourceProj, newV, periodCombo.getValue()));
        });
        periodCombo.valueProperty().addListener((obs, oldV, newV) -> {
            nameField.setText(buildDefaultClonedName(sourceProj, symbolCombo.getValue(), newV));
        });

        grid.add(symbolLbl, 0, 0);
        grid.add(symbolCombo, 1, 0);

        grid.add(periodLbl, 0, 1);
        grid.add(periodCombo, 1, 1);

        grid.add(nameLbl, 0, 2);
        grid.add(nameField, 1, 2);

        // Action Buttons
        HBox buttonBar = new HBox(12);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);

        Button cloneBtn = new Button("📋 Workflow clonen & öffnen");
        cloneBtn.setStyle("-fx-background-color: #1e293b; -fx-text-fill: #00e5ff; -fx-border-color: #00e5ff; -fx-border-radius: 4; -fx-cursor: hand; -fx-font-weight: bold; -fx-padding: 6 14;");
        cloneBtn.setOnAction(e -> {
            String newName = nameField.getText().trim();
            String newSymbol = symbolCombo.getValue() != null ? symbolCombo.getValue().trim() : sourceProj.getSymbol();
            String newPeriod = periodCombo.getValue() != null ? periodCombo.getValue().trim() : sourceProj.getPeriod();

            if (newName.isEmpty()) {
                nameField.setStyle("-fx-border-color: #ff5252;");
                return;
            }

            CustomProject clonedProj = sourceProj.cloneProject(newName, newSymbol, newPeriod);
            DatabaseManager.getInstance().saveCustomProject(clonedProj);
            stage.close();
            reloadProjects();

            if (onOpenProjectCallback != null) {
                onOpenProjectCallback.accept(clonedProj);
            }
        });

        Button cancelBtn = new Button("Abbrechen");
        cancelBtn.setStyle("-fx-background-color: #2e3545; -fx-text-fill: white; -fx-cursor: hand; -fx-padding: 6 14;");
        cancelBtn.setOnAction(e -> stage.close());

        buttonBar.getChildren().addAll(cloneBtn, cancelBtn);

        rootBox.getChildren().addAll(headerLbl, subLbl, grid, buttonBar);

        Scene scene = new Scene(rootBox);
        if (owner != null && owner.getScene() != null && !owner.getScene().getStylesheets().isEmpty()) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        stage.setScene(scene);
        stage.show();
    }

    private String buildDefaultClonedName(CustomProject sourceProj, String newSymbol, String newPeriod) {
        String sym = newSymbol != null ? newSymbol.trim() : sourceProj.getSymbol();
        String per = newPeriod != null ? newPeriod.trim() : sourceProj.getPeriod();
        String oldSym = sourceProj.getSymbol();
        String oldPer = sourceProj.getPeriod();

        String name = sourceProj.getName();
        if (name.contains(oldSym)) {
            name = name.replace(oldSym, sym);
        }
        if (name.contains(oldPer)) {
            name = name.replace(oldPer, per);
        }
        if (name.equals(sourceProj.getName())) {
            name = name + " (" + sym + " " + per + ")";
        }
        return name;
    }

    private void createNewProject() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        Window owner = root.getScene() != null ? root.getScene().getWindow() : null;
        if (owner != null) stage.initOwner(owner);
        stage.setTitle("Neues Projekt erstellen");

        VBox rootBox = new VBox(15);
        rootBox.setPadding(new Insets(20));
        rootBox.setStyle("-fx-background-color: #0b0d13;");
        rootBox.setPrefWidth(620);

        Label headerLbl = new Label("Neues Projekt");
        headerLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        headerLbl.setTextFill(Color.web("#00e5ff"));

        Label subLbl = new Label(
                "Tick-Kill sucht auf M1-OHLC und tötet danach auf kurzen Every-Tick-Fenstern. "
                        + "4 Jahre Tick sind nur noch Report.");
        subLbl.setWrapText(true);
        subLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(14);

        Label templateLbl = new Label("Vorlage:");
        templateLbl.setStyle("-fx-text-fill: #e6e9f0; -fx-font-weight: bold;");
        ComboBox<ProjectTemplate> templateCombo = new ComboBox<>(FXCollections.observableArrayList(
                ProjectTemplate.TICK_KILL, ProjectTemplate.GUIDED, ProjectTemplate.STANDARD));
        templateCombo.getSelectionModel().select(ProjectTemplate.TICK_KILL);
        templateCombo.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(templateCombo, Priority.ALWAYS);

        Label symbolLbl = new Label("Währungspaar / Symbol:");
        symbolLbl.setStyle("-fx-text-fill: #e6e9f0; -fx-font-weight: bold;");
        ComboBox<String> symbolCombo = new ComboBox<>(FXCollections.observableArrayList(
                "EURUSD", "GBPUSD", "USDJPY", "AUDCAD", "AUDUSD", "NZDUSD", "USDCAD", "USDCHF",
                "EURGBP", "EURJPY", "GBPJPY", "EURCAD", "EURAUD", "GBPAUD", "GBPCAD", "XAUUSD", "BTCUSD"
        ));
        symbolCombo.setEditable(true);
        symbolCombo.getSelectionModel().select("GBPJPY");
        symbolCombo.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(symbolCombo, Priority.ALWAYS);

        Label periodLbl = new Label("Timeframe / Period:");
        periodLbl.setStyle("-fx-text-fill: #e6e9f0; -fx-font-weight: bold;");
        ComboBox<String> periodCombo = new ComboBox<>(FXCollections.observableArrayList(
                "M1", "M5", "M15", "M30", "H1", "H2", "H4", "D1"
        ));
        periodCombo.setEditable(true);
        periodCombo.getSelectionModel().select("M5");
        periodCombo.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(periodCombo, Priority.ALWAYS);

        Label nameLbl = new Label("Projektname:");
        nameLbl.setStyle("-fx-text-fill: #e6e9f0; -fx-font-weight: bold;");
        TextField nameField = new TextField();
        nameField.setStyle("-fx-background-color: #141822; -fx-text-fill: #00e5ff; -fx-border-color: #232a3b; -fx-border-radius: 4;");
        GridPane.setHgrow(nameField, Priority.ALWAYS);

        Runnable refreshDefaultName = () -> {
            ProjectTemplate template = templateCombo.getValue();
            String symbol = comboValue(symbolCombo, "GBPJPY");
            String period = comboValue(periodCombo, "M5");
            if (template == ProjectTemplate.STANDARD) {
                nameField.setText("Neues Custom Projekt " + (projectsList.size() + 1));
            } else {
                nameField.setText(template.defaultName(symbol, period));
            }
        };
        templateCombo.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV == ProjectTemplate.GUIDED) {
                symbolCombo.getSelectionModel().select("AUDCAD");
                periodCombo.getSelectionModel().select("M5");
            } else if (newV == ProjectTemplate.TICK_KILL) {
                symbolCombo.getSelectionModel().select("GBPJPY");
                periodCombo.getSelectionModel().select("M5");
            } else {
                symbolCombo.getSelectionModel().select("EURUSD");
                periodCombo.getSelectionModel().select("H1");
            }
            refreshDefaultName.run();
        });
        symbolCombo.valueProperty().addListener((obs, oldV, newV) -> refreshDefaultName.run());
        periodCombo.valueProperty().addListener((obs, oldV, newV) -> refreshDefaultName.run());
        refreshDefaultName.run();

        grid.add(templateLbl, 0, 0);
        grid.add(templateCombo, 1, 0);
        grid.add(symbolLbl, 0, 1);
        grid.add(symbolCombo, 1, 1);
        grid.add(periodLbl, 0, 2);
        grid.add(periodCombo, 1, 2);
        grid.add(nameLbl, 0, 3);
        grid.add(nameField, 1, 3);

        HBox buttonBar = new HBox(12);
        buttonBar.setAlignment(Pos.CENTER_RIGHT);

        Button createBtn = new Button("Projekt erstellen & öffnen");
        createBtn.setStyle("-fx-background-color: #1e293b; -fx-text-fill: #00e5ff; -fx-border-color: #00e5ff; -fx-border-radius: 4; -fx-cursor: hand; -fx-font-weight: bold; -fx-padding: 6 14;");
        createBtn.setOnAction(e -> {
            String newName = nameField.getText() != null ? nameField.getText().trim() : "";
            if (newName.isEmpty()) {
                nameField.setStyle("-fx-border-color: #ff5252;");
                return;
            }
            ProjectTemplate template = templateCombo.getValue() != null
                    ? templateCombo.getValue() : ProjectTemplate.TICK_KILL;
            String symbol = comboValue(symbolCombo, template == ProjectTemplate.STANDARD ? "EURUSD" : "GBPJPY");
            String period = comboValue(periodCombo, template == ProjectTemplate.STANDARD ? "H1" : "M5");
            try {
                CustomProject newProj = buildProject(template, newName, symbol, period);
                newProj.setSortOrder(projectsList.size());
                DatabaseManager.getInstance().saveCustomProject(newProj);
                stage.close();
                reloadProjects();
                if (onOpenProjectCallback != null) {
                    onOpenProjectCallback.accept(newProj);
                }
            } catch (RuntimeException ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Projekt konnte nicht erstellt werden");
                alert.setHeaderText(template.label);
                alert.setContentText(ex.getMessage() != null ? ex.getMessage() : ex.toString());
                alert.show();
            }
        });

        Button cancelBtn = new Button("Abbrechen");
        cancelBtn.setStyle("-fx-background-color: #2e3545; -fx-text-fill: white; -fx-cursor: hand; -fx-padding: 6 14;");
        cancelBtn.setOnAction(e -> stage.close());
        buttonBar.getChildren().addAll(createBtn, cancelBtn);

        rootBox.getChildren().addAll(headerLbl, subLbl, grid, buttonBar);
        Scene scene = new Scene(rootBox);
        if (owner != null && owner.getScene() != null && !owner.getScene().getStylesheets().isEmpty()) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        stage.setScene(scene);
        stage.show();
    }

    private CustomProject buildProject(ProjectTemplate template, String name, String symbol, String period) {
        if (template == ProjectTemplate.STANDARD) {
            return CustomProject.createDefaultTemplate(name, "", symbol, period);
        }
        List<EaParameter> preset = ToTheMoon132GuidedWorkflowFactory.loadProvenPresetFromDisk("ToTheMoon_KI_v132");
        if (preset == null || preset.isEmpty()) {
            throw new IllegalStateException("Das ToTheMoon132-Preset fehlt. Ohne Preset kann die Suche nicht gebaut werden.");
        }
        Path reportsRoot = Path.of("backtest_reports", sanitizePathSegment(name));
        if (template == ProjectTemplate.GUIDED) {
            return ToTheMoon132GuidedWorkflowFactory.create(name, symbol, period, preset, reportsRoot);
        }
        return ToTheMoon132TickKillWorkflowFactory.create(name, symbol, period, preset, reportsRoot);
    }

    private static String comboValue(ComboBox<String> combo, String fallback) {
        String value = combo.getValue();
        if (value == null || value.isBlank()) return fallback;
        return value.trim();
    }

    private static String sanitizePathSegment(String name) {
        if (name == null || name.isBlank()) return "project";
        String cleaned = name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return cleaned.isBlank() ? "project" : cleaned;
    }

    private enum ProjectTemplate {
        TICK_KILL("ToTheMoon132 Tick-Kill (OHLC-Suche → kurze Tick-Kills → 4J Report)"),
        GUIDED("ToTheMoon132 Guided (OHLC-Suche → 3J Tick, ohne Smoke/1J-Kill)"),
        STANDARD("Standard Custom Project");

        private final String label;

        ProjectTemplate(String label) {
            this.label = label;
        }

        String defaultName(String symbol, String period) {
            if (this == TICK_KILL) {
                return "ToTheMoon132 Tick-Kill (" + symbol + " " + period + ")";
            }
            if (this == GUIDED) {
                return "ToTheMoon132 Guided (" + symbol + " " + period + ")";
            }
            return "Neues Custom Projekt";
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
