package com.backtester.ui.javafx;

import com.backtester.config.AppConfig;
import com.backtester.database.CustomProjectSaveCoordinator;
import com.backtester.database.DatabaseManager;
import com.backtester.engine.OptimizationConfig;
import com.backtester.engine.WorkflowEngine;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.workflow.CustomProject;
import com.backtester.workflow.DatabankManager;
import com.backtester.workflow.FilterCondition;
import com.backtester.workflow.WorkflowConfigurationValidator;
import com.backtester.workflow.WorkflowConfigurationValidator.RetesterOverwriteRisk;
import com.backtester.workflow.WorkflowTask;
import java.util.Set;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import com.backtester.config.EaParameterManager;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.converter.DoubleStringConverter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

/**
 * StrategyQuant-Style Custom Project Workflow Editor & Executor View (1:1 Layout Match).
 * Matching StrategyQuant Screenshots 1-5:
 * - Left column: Tasks Chain List (1. Build strategies, 2. OOS 1, 3. OOS 2, etc.)
 * - Center main region: Main Tabs (Progress | Full settings | Results) displaying task settings/logs
 * - Bottom region: Fixed Databanks Panel across the bottom (Results, Existing portfolio, Final)
 */
public class ProjectWorkflowEditorView {

    private static final long PROJECT_SAVE_DEBOUNCE_MILLIS = 500;
    private static final Duration PROJECT_SAVE_FLUSH_TIMEOUT = Duration.ofMinutes(2);

    private final BorderPane root;
    private CustomProject project;
    private final WorkflowEngine engine;
    private final DatabankManager databankManager;
    private final EaParameterManager eaParamManager = new EaParameterManager();
    private final CustomProjectSaveCoordinator projectSaveCoordinator;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ProjectWorkflowEditorView.class);
    private Runnable onBackToOverviewCallback;

    // Top Header & Controls
    private Label projectTitleLabel;
    private Button startBtn;
    private Button stopBtn;
    private Button resetBtn;

    // Main Center Tabs (Progress | Full settings | Results)
    private TabPane centerMainTabPane;
    private Tab progressTab;
    private Tab fullSettingsTab;
    private Tab resultsTab;

    // Left Panel: Tasks Chain List
    private VBox taskChainListBox;
    private WorkflowTask selectedTask;

    // Progress Tab Components
    private ProgressBar progressBar;
    private Label progressLabel;
    private TextArea consoleLog;

    // Full Settings Sub-Tab Components
    private TabPane fullSettingsSubTabPane;
    private Tab strategySelectionTab;
    private Tab optimizerSettingsTab;
    private Tab retestSubTab;
    private Tab dataSubTab;
    private Tab rankingSubTab;
    private Tab diversitySubTab;

    private ComboBox<String> sourceDatabankCombo;
    private ComboBox<String> targetDatabankCombo;
    private Label rankingSourceLabel;
    private ComboBox<String> rankingSourceCombo;
    private Label rankingTargetLabel;
    private ComboBox<String> rankingTargetCombo;
    private DatePicker startDatePicker;
    private DatePicker endDatePicker;
    private ComboBox<String> timeframeCombo;
    private ComboBox<String> symbolCombo;
    private ComboBox<String> execModeCombo;
    private Label dataSettingsHeading;
    private HBox optimizerOutputDirectoryRow;
    private TextField optimizerOutputDirectoryField;
    private ComboBox<String> optimizerAlgorithmCombo;
    private ComboBox<String> optimizerCriterionCombo;
    private ComboBox<String> optimizerForwardModeCombo;
    private DatePicker optimizerForwardDatePicker;
    private boolean updatingOptimizerControls;
    private CheckBox deleteFailedCheckBox;
    private TableView<FilterCondition> filterConditionsTable;
    private TextField expertField;
    private TextField taskNameField;
    private TextField diversityParamDiffField;
    private TextField diversityTradeDiffField;
    private Spinner<Integer> diversityMinDiffParamsSpinner;
    private Spinner<Integer> diversityMaxStrategiesSpinner;
    private boolean updatingDiversityControls;
    private Tab robustnessSubTab;
    private Tab robustnessParamsSubTab;
    private TableView<SweptParamInfo> robustnessParamsTable;
    private TextField robustnessSweepPctField;
    private Spinner<Integer> robustnessStepsSpinner;
    private Spinner<Integer> robustnessTimeShiftsSpinner;
    private Spinner<Integer> robustnessShiftDaysSpinner;
    private TextField robustnessExcludedParamsField;
    private boolean updatingRobustnessControls;
    private Label currentTaskSettingsHeader;

    // Bottom Fixed Databank Panel Components
    private TabPane bottomDatabankTabPane;
    private CheckBox persistDatabanksCheckBox;
    private Pane databankToolbar;

    // Execution state
    private Task<Void> activeProjectTask;

    public ProjectWorkflowEditorView() {
        this.engine = new WorkflowEngine(AppConfig.getInstance());
        this.databankManager = new DatabankManager();
        this.projectSaveCoordinator = new CustomProjectSaveCoordinator(
                DatabaseManager.getInstance(), PROJECT_SAVE_DEBOUNCE_MILLIS,
                message -> logToConsole("DB", message));

        root = new BorderPane();
        root.setPadding(new Insets(10));
        root.setStyle("-fx-background-color: transparent;");

        // Top Navigation Header
        HBox topBar = createTopBar();
        root.setTop(topBar);

        // Center SplitPane: Left Tasks Chain + Center Content (Progress / Settings / Results)
        SplitPane centerSplit = new SplitPane();
        centerSplit.setStyle("-fx-background-color: transparent;");

        // Left Panel: Task Chain Builder (Width ~ 320px)
        VBox leftTaskPanel = createLeftTaskPanel();

        // Center Main Tabs (Progress | Full settings | Results)
        centerMainTabPane = createCenterMainTabPane();

        centerSplit.getItems().addAll(leftTaskPanel, centerMainTabPane);
        centerSplit.setDividerPositions(0.33);

        // Vertical SplitPane for Center Content + Bottom Databanks Panel
        SplitPane verticalSplit = new SplitPane();
        verticalSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
        verticalSplit.setStyle("-fx-background-color: transparent;");

        VBox bottomDatabankPanel = createBottomDatabankPanel();

        verticalSplit.getItems().addAll(centerSplit, bottomDatabankPanel);
        verticalSplit.setDividerPositions(0.68);

        root.setCenter(verticalSplit);
    }

    public BorderPane getView() {
        return root;
    }

    public void setOnBackToOverviewCallback(Runnable callback) {
        this.onBackToOverviewCallback = callback;
    }

    public void loadProject(CustomProject proj) {
        if (this.project != null && this.project != proj && selectedTask != null) {
            applySelectedTaskName();
        }
        this.project = proj;
        this.selectedTask = null;
        if (proj != null) {
            boolean projectChanged = proj.migrateLegacyTaskDefinitions();
            engine.resetTransientResults();
            projectTitleLabel.setText("/ " + proj.getName());
            databankManager.loadFromProject(proj);
            if (persistDatabanksCheckBox != null) {
                persistDatabanksCheckBox.setSelected(proj.isSaveDatabanksPersistently());
            }

            // Ensure Task 1 is Strategie-Auswahl
            if (proj.getTasks() != null) {
                boolean hasSelection = false;
                for (WorkflowTask t : proj.getTasks()) {
                    if (t.getType() == WorkflowTask.TaskType.STRATEGY_SELECTION) {
                        hasSelection = true;
                        break;
                    }
                }
                if (!hasSelection) {
                    WorkflowTask selTask = new WorkflowTask("Strategie-Auswahl", WorkflowTask.TaskType.STRATEGY_SELECTION);
                    proj.getTasks().add(0, selTask);
                    projectChanged = true;
                }
            }

            if (projectChanged) saveProject();

            engine.changeExpert(proj.getExpert());
            engine.setSymbol(proj.getSymbol());
            engine.setPeriod(proj.getPeriod());
        }
        refreshTaskChain();
        refreshDatabanksUI();
        if (project != null && project.getTasks() != null && !project.getTasks().isEmpty()) {
            selectTask(project.getTasks().get(0));
        } else {
            selectTask(null);
        }
    }

    // ─── UI Construction Methods ──────────────────────────────────────────────

    private HBox createTopBar() {
        HBox bar = new HBox(15);
        bar.setPadding(new Insets(5, 5, 10, 5));
        bar.setAlignment(Pos.CENTER_LEFT);

        Button backBtn = new Button("⬅ Custom Projects");
        backBtn.setStyle("-fx-background-color: #1e2432; -fx-text-fill: #00e5ff; -fx-font-weight: bold; -fx-cursor: hand;");
        backBtn.setOnAction(e -> {
            backBtn.setDisable(true);
            flushProjectSaveAsync(() -> {
                backBtn.setDisable(false);
                if (onBackToOverviewCallback != null) {
                    onBackToOverviewCallback.run();
                }
            });
        });

        projectTitleLabel = new Label("");
        projectTitleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        projectTitleLabel.setTextFill(Color.web("#e6e9f0"));
        projectTitleLabel.setStyle("-fx-cursor: hand;");
        projectTitleLabel.setTooltip(new Tooltip("Doppelklick zum Umbenennen"));
        projectTitleLabel.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) renameCurrentProject();
        });

        Button renameTitleBtn = new Button("✏");
        renameTitleBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #94a3b8; -fx-font-size: 14px; -fx-cursor: hand; -fx-padding: 0 4;");
        renameTitleBtn.setTooltip(new Tooltip("Workflow umbenennen"));
        renameTitleBtn.setOnAction(e -> renameCurrentProject());

        HBox titleBox = new HBox(6, projectTitleLabel, renameTitleBtn);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        startBtn = new Button("▶ Start");
        startBtn.getStyleClass().add("button-start");
        startBtn.setOnAction(e -> startProjectExecution());

        stopBtn = new Button("⏹ Stop");
        stopBtn.getStyleClass().add("button-cancel");
        stopBtn.setDisable(true);
        stopBtn.setOnAction(e -> stopProjectExecution());

        resetBtn = new Button("🔄 Reset");
        resetBtn.getStyleClass().add("button");
        resetBtn.setOnAction(e -> resetProjectExecution());

        Button saveBtn = new Button("💾 Save");
        saveBtn.getStyleClass().add("button");
        saveBtn.setOnAction(e -> {
            saveBtn.setDisable(true);
            flushProjectSaveAsync(() -> {
                saveBtn.setDisable(false);
            });
        });

        Button cloneBtn = new Button("📋 Clone");
        cloneBtn.setStyle("-fx-background-color: #1e2432; -fx-text-fill: #00e5ff; -fx-font-weight: bold; -fx-cursor: hand; -fx-border-color: #00e5ff; -fx-border-radius: 4;");
        cloneBtn.setTooltip(new Tooltip("Diesen Workflow duplizieren (mit neuem Währungspaar & Timeframe)"));
        cloneBtn.setOnAction(e -> {
            CustomProjectsOverviewView overviewView = new CustomProjectsOverviewView();
            overviewView.setOnOpenProjectCallback(newProj -> loadProject(newProj));
            overviewView.showCloneWorkflowDialog(project);
        });

        bar.getChildren().addAll(backBtn, titleBox, spacer, startBtn, stopBtn, resetBtn, saveBtn, cloneBtn);
        return bar;
    }

    private void renameCurrentProject() {
        if (project == null) return;
        TextInputDialog dialog = new TextInputDialog(project.getName());
        dialog.setTitle("Workflow umbenennen");
        dialog.setHeaderText("Geben Sie einen neuen Namen für den aktuellen Workflow ein:");
        dialog.setContentText("Neuer Name:");

        dialog.getDialogPane().setStyle("-fx-background-color: #0b0d13;");
        if (root.getScene() != null && !root.getScene().getStylesheets().isEmpty()) {
            dialog.getDialogPane().getStylesheets().addAll(root.getScene().getStylesheets());
        }

        dialog.showAndWait().ifPresent(newName -> {
            if (!newName.trim().isEmpty() && !newName.trim().equals(project.getName())) {
                project.setName(newName.trim());
                projectTitleLabel.setText("/ " + project.getName());
                saveProject();
            }
        });
    }

    private VBox createLeftTaskPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        panel.getStyleClass().add("sci-fi-panel");
        panel.setMinWidth(330);

        Button addTaskBtn = new Button("➕ Add new task");
        addTaskBtn.getStyleClass().add("button-start");
        addTaskBtn.setMaxWidth(Double.MAX_VALUE);
        addTaskBtn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        addTaskBtn.setOnAction(e -> {
            WorkflowTask newTask = AddTaskModalDialog.show(root.getScene() != null ? root.getScene().getWindow() : null);
            if (newTask != null && project != null) {
                project.addTask(newTask);
                saveProject();
                refreshTaskChain();
                selectTask(newTask);
            }
        });

        taskChainListBox = new VBox(8);
        taskChainListBox.setPadding(new Insets(5));
        ScrollPane chainScroll = new ScrollPane(taskChainListBox);
        chainScroll.setFitToWidth(true);
        chainScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(chainScroll, Priority.ALWAYS);

        panel.getChildren().addAll(addTaskBtn, chainScroll);
        return panel;
    }

    private TabPane createCenterMainTabPane() {
        TabPane tabPane = new TabPane();
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        progressTab = new Tab("Progress");
        progressTab.setClosable(false);
        progressTab.setContent(createProgressTabContent());

        fullSettingsTab = new Tab("Full settings");
        fullSettingsTab.setClosable(false);
        fullSettingsTab.setContent(createFullSettingsTabContent());

        resultsTab = new Tab("Results");
        resultsTab.setClosable(false);
        resultsTab.setContent(createResultsTabContent());

        tabPane.getTabs().addAll(progressTab, fullSettingsTab, resultsTab);
        return tabPane;
    }

    private VBox createProgressTabContent() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));

        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setPrefHeight(18);

        progressLabel = new Label("Bereit");
        progressLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        progressLabel.setTextFill(Color.web("#cbd5e1"));

        consoleLog = new TextArea();
        consoleLog.setEditable(false);
        consoleLog.setFont(Font.font("Consolas", 12));
        consoleLog.getStyleClass().add("text-area");
        VBox.setVgrow(consoleLog, Priority.ALWAYS);

        panel.getChildren().addAll(progressBar, progressLabel, consoleLog);
        return panel;
    }

    private VBox createFullSettingsTabContent() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));

        currentTaskSettingsHeader = new Label("Advanced settings for task");
        currentTaskSettingsHeader.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        currentTaskSettingsHeader.setTextFill(Color.web("#00e5ff"));

        HBox taskIdentityRow = new HBox(10);
        taskIdentityRow.setAlignment(Pos.CENTER_LEFT);
        Label taskNameLabel = new Label("Modulname:");
        taskNameLabel.setStyle("-fx-font-weight: bold;");
        taskNameField = new TextField();
        taskNameField.setPromptText("Individueller Name, z. B. Validierung (OOS)");
        taskNameField.setDisable(true);
        taskNameField.setOnAction(e -> applySelectedTaskName());
        taskNameField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (wasFocused && !isFocused) applySelectedTaskName();
        });
        HBox.setHgrow(taskNameField, Priority.ALWAYS);
        taskIdentityRow.getChildren().addAll(taskNameLabel, taskNameField);

        fullSettingsSubTabPane = new TabPane();
        VBox.setVgrow(fullSettingsSubTabPane, Priority.ALWAYS);

        // Sub-Tab 1: Strategy & EA Selection
        strategySelectionTab = new Tab("Strategy & EA Selection");
        strategySelectionTab.setClosable(false);
        strategySelectionTab.setContent(createStrategySelectionSubTab());

        // Sub-Tab 2: Optimizer Settings
        optimizerSettingsTab = new Tab("Optimizer Settings");
        optimizerSettingsTab.setClosable(false);
        optimizerSettingsTab.setContent(createOptimizerSettingsSubTab());

        // Sub-Tab 3: Databank routing
        retestSubTab = new Tab("Databank routing");
        retestSubTab.setClosable(false);
        retestSubTab.setContent(createWhatToRetestSubTab());

        // Sub-Tab 4: Data
        dataSubTab = new Tab("Data");
        dataSubTab.setClosable(false);
        dataSubTab.setContent(createDataSubTab());

        // Sub-Tab 5: Ranking & Filtering
        rankingSubTab = new Tab("Ranking & Filtering");
        rankingSubTab.setClosable(false);
        rankingSubTab.setContent(createRankingSubTab());

        // Sub-Tab 6: Diversity Clustering
        diversitySubTab = new Tab("Diversity Clustering");
        diversitySubTab.setClosable(false);
        diversitySubTab.setContent(createDiversitySubTab());

        // Sub-Tab 7: Robustness Settings
        robustnessSubTab = new Tab("Robustness Settings");
        robustnessSubTab.setClosable(false);
        robustnessSubTab.setContent(createRobustnessSubTab());

        // Sub-Tab 8: Swept Parameters Overview
        robustnessParamsSubTab = new Tab("Swept Parameters");
        robustnessParamsSubTab.setClosable(false);
        robustnessParamsSubTab.setContent(createRobustnessParamsSubTab());

        fullSettingsSubTabPane.getTabs().addAll(
            strategySelectionTab, optimizerSettingsTab, retestSubTab, dataSubTab, rankingSubTab, diversitySubTab, robustnessSubTab, robustnessParamsSubTab
        );
        box.getChildren().addAll(currentTaskSettingsHeader, taskIdentityRow, fullSettingsSubTabPane);

        return box;
    }

    private VBox createWhatToRetestSubTab() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color: rgba(26, 30, 40, 0.85); -fx-border-color: #2e3545; -fx-border-radius: 6;");

        Label heading = new Label("Databank routing settings");
        heading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        heading.setTextFill(Color.web("#00e5ff"));

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);

        grid.add(new Label("Read strategies from databank:"), 0, 0);
        sourceDatabankCombo = new ComboBox<>(FXCollections.observableArrayList("Results", "Existing portfolio", "Final"));
        sourceDatabankCombo.setValue("Results");
        sourceDatabankCombo.setOnAction(e -> {
            if (selectedTask != null && sourceDatabankCombo.getValue() != null) {
                selectedTask.setSourceDatabank(sourceDatabankCombo.getValue());
                saveProject();
            }
        });
        grid.add(sourceDatabankCombo, 1, 0);

        grid.add(new Label("Store task results in databank:"), 0, 1);
        targetDatabankCombo = new ComboBox<>(FXCollections.observableArrayList("Results", "Existing portfolio", "Final"));
        targetDatabankCombo.setValue("Results");
        targetDatabankCombo.setOnAction(e -> {
            if (selectedTask != null && targetDatabankCombo.getValue() != null) {
                selectedTask.setTargetDatabank(targetDatabankCombo.getValue());
                saveProject();
            }
        });
        grid.add(targetDatabankCombo, 1, 1);

        Label helpText = new Label(
            "This task processes only the selected source databank. Choose a separate target to keep the source " +
            "unchanged and route the task results into a new databank."
        );
        helpText.setWrapText(true);
        helpText.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 12px;");

        panel.getChildren().addAll(heading, grid, new Separator(), helpText);
        return panel;
    }

    private VBox createDataSubTab() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color: rgba(26, 30, 40, 0.85); -fx-border-color: #2e3545; -fx-border-radius: 6;");

        dataSettingsHeading = new Label("Backtest Data Settings (Retester)");
        dataSettingsHeading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        dataSettingsHeading.setTextFill(Color.web("#00e5ff"));

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);

        grid.add(new Label("Symbol:"), 0, 0);
        symbolCombo = new ComboBox<>(FXCollections.observableArrayList("EURUSD", "GBPUSD", "USDJPY", "AUDCAD", "XAUUSD", "GBPJPY_M1_dukas"));
        symbolCombo.setValue("EURUSD");
        symbolCombo.setOnAction(e -> {
            if (selectedTask != null && symbolCombo.getValue() != null) {
                selectedTask.setRetestSymbol(symbolCombo.getValue());
                if (project != null) project.setSymbol(symbolCombo.getValue());
                engine.setSymbol(symbolCombo.getValue());
                saveProject();
            }
        });
        grid.add(symbolCombo, 1, 0);

        grid.add(new Label("Timeframe:"), 0, 1);
        timeframeCombo = new ComboBox<>(FXCollections.observableArrayList("M1", "M5", "M15", "M30", "H1", "H4", "D1"));
        timeframeCombo.setValue("H1");
        timeframeCombo.setOnAction(e -> {
            if (selectedTask != null && timeframeCombo.getValue() != null) {
                selectedTask.setRetestPeriod(timeframeCombo.getValue());
                if (project != null) project.setPeriod(timeframeCombo.getValue());
                engine.setPeriod(timeframeCombo.getValue());
                saveProject();
            }
        });
        grid.add(timeframeCombo, 1, 1);

        grid.add(new Label("Execution Mode (Modellierung):"), 0, 2);
        execModeCombo = new ComboBox<>(FXCollections.observableArrayList(
            "OHLC M1 (Every tick based on OHLC M1)",
            "Every Tick (Ticksimulation)",
            "Every Tick based on Real Ticks (Realtick)",
            "Open Prices Only"
        ));
        execModeCombo.setValue("OHLC M1 (Every tick based on OHLC M1)");
        execModeCombo.setOnAction(e -> {
            if (selectedTask != null && execModeCombo.getValue() != null) {
                int mode = WorkflowTask.MODE_OHLC_M1;
                String val = execModeCombo.getValue();
                if (val.contains("Every Tick (Ticksimulation)")) mode = WorkflowTask.MODE_EVERY_TICK;
                else if (val.contains("OHLC M1")) mode = WorkflowTask.MODE_OHLC_M1;
                else if (val.contains("Real Ticks")) mode = WorkflowTask.MODE_REAL_TICKS;
                else if (val.contains("Open Prices")) mode = WorkflowTask.MODE_OPEN_PRICES;
                selectedTask.setExecutionMode(mode);
                saveProject();
            }
        });
        grid.add(execModeCombo, 1, 2);

        grid.add(new Label("Start day (Test From):"), 0, 3);
        startDatePicker = new DatePicker();
        startDatePicker.setOnAction(e -> {
            commitDatePicker(startDatePicker);
            if (selectedTask != null && startDatePicker.getValue() != null) {
                selectedTask.setStartDate(startDatePicker.getValue().toString());
                recalculateForwardDate();
                saveProject();
            }
        });
        startDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (selectedTask != null && newVal != null) {
                selectedTask.setStartDate(newVal.toString());
                recalculateForwardDate();
                saveProject();
            }
        });
        startDatePicker.getEditor().focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                commitDatePicker(startDatePicker);
                if (selectedTask != null && startDatePicker.getValue() != null) {
                    selectedTask.setStartDate(startDatePicker.getValue().toString());
                    recalculateForwardDate();
                    saveProject();
                }
            }
        });
        startDatePicker.getEditor().textProperty().addListener((obs, oldText, newText) -> {
            commitDatePicker(startDatePicker);
            if (selectedTask != null && startDatePicker.getValue() != null) {
                selectedTask.setStartDate(startDatePicker.getValue().toString());
                recalculateForwardDate();
                saveProject();
            }
        });
        grid.add(startDatePicker, 1, 3);

        grid.add(new Label("End day (Test To):"), 0, 4);
        endDatePicker = new DatePicker();
        endDatePicker.setOnAction(e -> {
            commitDatePicker(endDatePicker);
            if (selectedTask != null && endDatePicker.getValue() != null) {
                selectedTask.setEndDate(endDatePicker.getValue().toString());
                recalculateForwardDate();
                saveProject();
            }
        });
        endDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (selectedTask != null && newVal != null) {
                selectedTask.setEndDate(newVal.toString());
                recalculateForwardDate();
                saveProject();
            }
        });
        endDatePicker.getEditor().focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                commitDatePicker(endDatePicker);
                if (selectedTask != null && endDatePicker.getValue() != null) {
                    selectedTask.setEndDate(endDatePicker.getValue().toString());
                    recalculateForwardDate();
                    saveProject();
                }
            }
        });
        endDatePicker.getEditor().textProperty().addListener((obs, oldText, newText) -> {
            commitDatePicker(endDatePicker);
            if (selectedTask != null && endDatePicker.getValue() != null) {
                selectedTask.setEndDate(endDatePicker.getValue().toString());
                recalculateForwardDate();
                saveProject();
            }
        });
        grid.add(endDatePicker, 1, 4);

        Label outputDirectoryLabel = new Label("Optimizer-Ausgabeordner:");
        optimizerOutputDirectoryField = new TextField();
        optimizerOutputDirectoryField.setPromptText("Ordner für Optimierungs-Reports");
        optimizerOutputDirectoryField.setPrefColumnCount(38);
        optimizerOutputDirectoryField.setOnAction(e -> saveOptimizerOutputDirectory());
        optimizerOutputDirectoryField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (wasFocused && !isFocused) saveOptimizerOutputDirectory();
        });
        HBox.setHgrow(optimizerOutputDirectoryField, Priority.ALWAYS);

        Button browseOutputDirectoryButton = new Button("Ordner wählen...");
        browseOutputDirectoryButton.setOnAction(e -> chooseOptimizerOutputDirectory());
        optimizerOutputDirectoryRow = new HBox(10, optimizerOutputDirectoryField, browseOutputDirectoryButton);
        optimizerOutputDirectoryRow.setAlignment(Pos.CENTER_LEFT);
        grid.add(outputDirectoryLabel, 0, 5);
        grid.add(optimizerOutputDirectoryRow, 1, 5);

        Label outputDirectoryHelp = new Label(
                "Für jeden Optimizer-Lauf wird darin ein eigener Zeitstempel-Unterordner mit tester.ini, " +
                "Optimierungs-Report und optionalem Forward-Report angelegt. Die gefundenen Strategien " +
                "werden zusätzlich in die unter 'Databank routing' gewählte Ziel-Databank geschrieben.");
        outputDirectoryHelp.setWrapText(true);
        outputDirectoryHelp.setStyle("-fx-text-fill: #9aa4b5; -fx-font-size: 12px;");
        outputDirectoryHelp.setMaxWidth(760);
        grid.add(outputDirectoryHelp, 0, 6, 2, 1);

        outputDirectoryLabel.visibleProperty().bind(optimizerOutputDirectoryRow.visibleProperty());
        outputDirectoryLabel.managedProperty().bind(optimizerOutputDirectoryRow.managedProperty());
        outputDirectoryHelp.visibleProperty().bind(optimizerOutputDirectoryRow.visibleProperty());
        outputDirectoryHelp.managedProperty().bind(optimizerOutputDirectoryRow.managedProperty());

        panel.getChildren().addAll(dataSettingsHeading, grid);
        return panel;
    }

    private ScrollPane createRankingSubTab() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color: rgba(26, 30, 40, 0.85); -fx-border-color: #2e3545; -fx-border-radius: 6;");

        Label heading = new Label("Strategy Quality Ranking & Filtering Conditions");
        heading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        heading.setTextFill(Color.web("#00e5ff"));

        Button rankingWeightsBtn = new Button("⚖ Score-Gewichtung anpassen...");
        rankingWeightsBtn.setStyle("-fx-background-color: #2196f3; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        rankingWeightsBtn.setOnAction(e -> {
            WorkflowConfigDialogs.showScoreWeightsDialog(root.getScene().getWindow());
            refreshDatabanksUI();
        });

        HBox headerBox = new HBox(15, heading, rankingWeightsBtn);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        GridPane routeGrid = new GridPane();
        routeGrid.setHgap(15);
        routeGrid.setVgap(10);
        routeGrid.setPadding(new Insets(10));
        routeGrid.setStyle("-fx-background-color: rgba(11, 13, 19, 0.6); -fx-border-color: #2e3545; -fx-border-radius: 4;");

        rankingSourceLabel = new Label("Strategien lesen aus Databank:");
        routeGrid.add(rankingSourceLabel, 0, 0);
        rankingSourceCombo = new ComboBox<>(FXCollections.observableArrayList(databankManager.getDatabankNames()));
        rankingSourceCombo.setValue(selectedTask != null ? selectedTask.getSourceDatabank() : "Results");
        rankingSourceCombo.setOnShowing(e -> updateDatabankComboBoxes());
        rankingSourceCombo.setOnAction(e -> {
            if (selectedTask != null && rankingSourceCombo.getValue() != null) {
                selectedTask.setSourceDatabank(rankingSourceCombo.getValue());
                if (sourceDatabankCombo != null) sourceDatabankCombo.setValue(rankingSourceCombo.getValue());
                saveProject();
            }
        });
        routeGrid.add(rankingSourceCombo, 1, 0);

        rankingTargetLabel = new Label("Gefilterte Ergebnisse speichern in Databank:");
        routeGrid.add(rankingTargetLabel, 0, 1);
        rankingTargetCombo = new ComboBox<>(FXCollections.observableArrayList(databankManager.getDatabankNames()));
        rankingTargetCombo.setValue(selectedTask != null ? selectedTask.getTargetDatabank() : "Results");
        rankingTargetCombo.setOnShowing(e -> updateDatabankComboBoxes());
        rankingTargetCombo.setOnAction(e -> {
            if (selectedTask != null && rankingTargetCombo.getValue() != null) {
                selectedTask.setTargetDatabank(rankingTargetCombo.getValue());
                if (targetDatabankCombo != null) targetDatabankCombo.setValue(rankingTargetCombo.getValue());
                saveProject();
            }
        });
        routeGrid.add(rankingTargetCombo, 1, 1);

        deleteFailedCheckBox = new CheckBox("Delete FAILED strategies from databank");
        deleteFailedCheckBox.setSelected(true);
        deleteFailedCheckBox.setStyle("-fx-text-fill: #e6e9f0; -fx-font-weight: bold;");
        deleteFailedCheckBox.setOnAction(e -> {
            if (selectedTask != null) {
                selectedTask.setDeleteFailed(deleteFailedCheckBox.isSelected());
                saveProject();
            }
        });

        filterConditionsTable = new TableView<>();
        filterConditionsTable.setEditable(true);
        filterConditionsTable.setMinHeight(280);
        filterConditionsTable.setPrefHeight(320);
        filterConditionsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(filterConditionsTable, Priority.ALWAYS);

        TableColumn<FilterCondition, Boolean> activeCol = new TableColumn<>("Active");
        activeCol.setCellValueFactory(c -> {
            FilterCondition cond = c.getValue();
            SimpleBooleanProperty prop = new SimpleBooleanProperty(cond.isEnabled());
            prop.addListener((obs, oldVal, newVal) -> {
                if (newVal != null) {
                    cond.setEnabled(newVal);
                    saveProject();
                }
            });
            return prop;
        });
        activeCol.setCellFactory(CheckBoxTableCell.forTableColumn(activeCol));
        activeCol.setPrefWidth(70);

        TableColumn<FilterCondition, FilterCondition.Metric> metricCol = new TableColumn<>("Metric");
        metricCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getMetric()));
        metricCol.setCellFactory(ComboBoxTableCell.forTableColumn(new javafx.util.StringConverter<FilterCondition.Metric>() {
            @Override public String toString(FilterCondition.Metric object) { return object != null ? object.getDisplayName() : ""; }
            @Override public FilterCondition.Metric fromString(String string) { return FilterCondition.Metric.valueOf(string); }
        }, FilterCondition.Metric.values()));
        metricCol.setOnEditCommit(e -> {
            if (e.getRowValue() != null && e.getNewValue() != null) {
                e.getRowValue().setMetric(e.getNewValue());
                saveProject();
            }
        });
        metricCol.setPrefWidth(260);

        TableColumn<FilterCondition, FilterCondition.Operator> opCol = new TableColumn<>("<=>");
        opCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getOperator()));
        opCol.setCellFactory(ComboBoxTableCell.forTableColumn(new javafx.util.StringConverter<FilterCondition.Operator>() {
            @Override public String toString(FilterCondition.Operator object) { return object != null ? object.getSymbol() + " (" + object.getLabel() + ")" : ""; }
            @Override public FilterCondition.Operator fromString(String string) { return FilterCondition.Operator.valueOf(string); }
        }, FilterCondition.Operator.values()));
        opCol.setOnEditCommit(e -> {
            if (e.getRowValue() != null && e.getNewValue() != null) {
                e.getRowValue().setOperator(e.getNewValue());
                saveProject();
            }
        });
        opCol.setPrefWidth(160);

        TableColumn<FilterCondition, Double> valCol = new TableColumn<>("Value");
        valCol.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().getValue()).asObject());
        valCol.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        valCol.setOnEditCommit(e -> {
            Double newValue = e.getNewValue();
            if (e.getRowValue() != null && newValue != null && Double.isFinite(newValue)) {
                e.getRowValue().setValue(newValue);
                saveProject();
            } else {
                filterConditionsTable.refresh();
                logToConsole("FILTER", "NaN und unendliche Filterwerte sind nicht zulässig.");
            }
        });
        valCol.setPrefWidth(100);

        TableColumn<FilterCondition, Void> actionCol = new TableColumn<>("");
        actionCol.setPrefWidth(55);
        actionCol.setCellFactory(param -> new TableCell<>() {
            private final Button deleteBtn = new Button("❌");
            {
                deleteBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ff5252; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 0 5 0 5;");
                deleteBtn.setTooltip(new Tooltip("Diesen Filter löschen"));
                deleteBtn.setOnAction(event -> {
                    if (getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                        FilterCondition cond = getTableView().getItems().get(getIndex());
                        if (cond != null && selectedTask != null) {
                            selectedTask.getFilterConditions().remove(cond);
                            filterConditionsTable.getItems().setAll(selectedTask.getFilterConditions());
                            saveProject();
                        }
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(deleteBtn);
                }
            }
        });

        filterConditionsTable.getColumns().addAll(activeCol, metricCol, opCol, valCol, actionCol);

        HBox btnBox = new HBox(10);
        Button addCondBtn = new Button("➕ Add filter");
        addCondBtn.getStyleClass().add("button-start");
        addCondBtn.setOnAction(e -> {
            if (selectedTask != null) {
                FilterCondition cond = new FilterCondition(FilterCondition.Metric.BT_PROFIT_FACTOR, FilterCondition.Operator.GREATER_THAN, 1.2);
                selectedTask.addFilterCondition(cond);
                filterConditionsTable.getItems().setAll(selectedTask.getFilterConditions());
                saveProject();
            }
        });

        Button removeCondBtn = new Button("🗑 Remove selected");
        removeCondBtn.getStyleClass().add("button-cancel");
        removeCondBtn.setOnAction(e -> {
            FilterCondition sel = filterConditionsTable.getSelectionModel().getSelectedItem();
            if (sel != null && selectedTask != null) {
                selectedTask.getFilterConditions().remove(sel);
                filterConditionsTable.getItems().setAll(selectedTask.getFilterConditions());
                saveProject();
            }
        });

        btnBox.getChildren().addAll(addCondBtn, removeCondBtn);

        panel.getChildren().addAll(headerBox, routeGrid, deleteFailedCheckBox, new Separator(), filterConditionsTable, btnBox);

        ScrollPane scrollPane = new ScrollPane(panel);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return scrollPane;
    }

    private VBox createDiversitySubTab() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color: rgba(26, 30, 40, 0.85); -fx-border-color: #2e3545; -fx-border-radius: 6;");

        HBox headerBox = new HBox(15);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        Label heading = new Label("Diversitäts-Clustering der ausgewählten Quell-Databank");
        heading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        heading.setTextFill(Color.web("#00e5ff"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button resetDefaultsBtn = new Button("Standards setzen");
        resetDefaultsBtn.setStyle("-fx-background-color: #1e293b; -fx-text-fill: #00e5ff; -fx-border-color: #00e5ff; -fx-border-radius: 4; -fx-cursor: hand; -fx-font-weight: bold;");
        resetDefaultsBtn.setOnAction(e -> applyRecommendedDiversityDefaults());

        headerBox.getChildren().addAll(heading, spacer, resetDefaultsBtn);

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);

        grid.add(new Label("Param Differenz %:"), 0, 0);
        diversityParamDiffField = new TextField(String.format(Locale.US, "%.0f",
                WorkflowTask.DEFAULT_DIVERSITY_PARAM_DIFF_PCT * 100));
        diversityParamDiffField.setOnAction(e -> commitDiversityPercentage(diversityParamDiffField, true));
        diversityParamDiffField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (wasFocused && !isFocused) commitDiversityPercentage(diversityParamDiffField, true);
        });
        grid.add(diversityParamDiffField, 1, 0);

        grid.add(new Label("Trades Differenz %:"), 2, 0);
        diversityTradeDiffField = new TextField(String.format(Locale.US, "%.0f",
                WorkflowTask.DEFAULT_DIVERSITY_TRADE_DIFF_PCT * 100));
        diversityTradeDiffField.setOnAction(e -> commitDiversityPercentage(diversityTradeDiffField, false));
        diversityTradeDiffField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (wasFocused && !isFocused) commitDiversityPercentage(diversityTradeDiffField, false);
        });
        grid.add(diversityTradeDiffField, 3, 0);

        grid.add(new Label("Min. differente Params:"), 0, 1);
        diversityMinDiffParamsSpinner = new Spinner<>(1, 100,
                WorkflowTask.DEFAULT_DIVERSITY_MIN_DIFFERENT_PARAMS, 1);
        diversityMinDiffParamsSpinner.setEditable(true);
        diversityMinDiffParamsSpinner.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!updatingDiversityControls && selectedTask != null && newValue != null) {
                selectedTask.setDiversityMinDifferentParams(newValue);
                saveProject();
            }
        });
        grid.add(diversityMinDiffParamsSpinner, 1, 1);

        grid.add(new Label("Max. Strategien (Ziel):"), 2, 1);
        diversityMaxStrategiesSpinner = new Spinner<>(1, 10000,
                WorkflowTask.DEFAULT_DIVERSITY_MAX_STRATEGIES, 1);
        diversityMaxStrategiesSpinner.setEditable(true);
        diversityMaxStrategiesSpinner.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!updatingDiversityControls && selectedTask != null && newValue != null) {
                selectedTask.setDiversityMaxStrategies(newValue);
                saveProject();
            }
        });
        grid.add(diversityMaxStrategiesSpinner, 3, 1);

        Label sourceInfo = new Label(
                "Es wird ausschließlich die unter 'Databank routing' gewählte Quell-Databank geclustert. " +
                "Performance-Filter und Retests werden als eigene Tasks angelegt. Für Langzeitdaten wird hinter " +
                "dem Retester ein weiterer Diversitäts-Clustering-Task mit dessen Ausgabedatabank als Quelle eingefügt."
        );
        sourceInfo.setWrapText(true);
        sourceInfo.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 12px;");

        panel.getChildren().addAll(headerBox, grid, new Separator(), sourceInfo);
        return panel;
    }

    private void applyRecommendedDiversityDefaults() {
        updatingDiversityControls = true;
        try {
            double defaultParamDiff = WorkflowTask.DEFAULT_DIVERSITY_PARAM_DIFF_PCT;
            double defaultTradeDiff = WorkflowTask.DEFAULT_DIVERSITY_TRADE_DIFF_PCT;
            int defaultMinParams = WorkflowTask.DEFAULT_DIVERSITY_MIN_DIFFERENT_PARAMS;
            int defaultMaxStrats = WorkflowTask.DEFAULT_DIVERSITY_MAX_STRATEGIES;

            diversityParamDiffField.setText(String.format(Locale.US, "%.0f", defaultParamDiff * 100));
            diversityTradeDiffField.setText(String.format(Locale.US, "%.0f", defaultTradeDiff * 100));
            diversityMinDiffParamsSpinner.getValueFactory().setValue(defaultMinParams);
            diversityMaxStrategiesSpinner.getValueFactory().setValue(defaultMaxStrats);

            if (selectedTask != null) {
                selectedTask.setDiversityParamDiffPct(defaultParamDiff);
                selectedTask.setDiversityTradeDiffPct(defaultTradeDiff);
                selectedTask.setDiversityMinDifferentParams(defaultMinParams);
                selectedTask.setDiversityMaxStrategies(defaultMaxStrats);
                saveProject();
            }
        } finally {
            updatingDiversityControls = false;
        }
    }

    private void commitDiversityPercentage(TextField field, boolean parameterDifference) {
        if (updatingDiversityControls || selectedTask == null || field == null) return;
        double currentValue = parameterDifference
                ? selectedTask.getDiversityParamDiffPct() : selectedTask.getDiversityTradeDiffPct();
        try {
            double percentage = Double.parseDouble(field.getText().trim().replace(',', '.'));
            double fraction = percentage / 100.0;
            if (parameterDifference) {
                selectedTask.setDiversityParamDiffPct(fraction);
            } else {
                selectedTask.setDiversityTradeDiffPct(fraction);
            }
            field.setText(String.format(Locale.US, "%.0f", percentage));
            field.setStyle("");
            saveProject();
        } catch (RuntimeException ex) {
            field.setText(String.format(Locale.US, "%.0f", currentValue * 100));
            field.setStyle("-fx-border-color: #ff5252;");
        }
    }

    private VBox createRobustnessSubTab() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color: rgba(26, 30, 40, 0.85); -fx-border-color: #2e3545; -fx-border-radius: 6;");

        Label heading = new Label("Parameter-Sensitivity Sweeps & Stresstests (Robustness CV)");
        heading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        heading.setTextFill(Color.web("#00e5ff"));

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);

        grid.add(new Label("Sweep Abweichung %:"), 0, 0);
        robustnessSweepPctField = new TextField(String.format(Locale.US, "%.0f",
                WorkflowTask.DEFAULT_ROBUSTNESS_SWEEP_PCT * 100));
        robustnessSweepPctField.setPromptText("z.B. 5 oder 10");
        robustnessSweepPctField.setOnAction(e -> commitRobustnessPercentage(robustnessSweepPctField));
        robustnessSweepPctField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (wasFocused && !isFocused) commitRobustnessPercentage(robustnessSweepPctField);
        });
        grid.add(robustnessSweepPctField, 1, 0);

        grid.add(new Label("Sweep Schritte (Punkte):"), 2, 0);
        robustnessStepsSpinner = new Spinner<>(1, 100, WorkflowTask.DEFAULT_ROBUSTNESS_STEPS, 1);
        robustnessStepsSpinner.setEditable(true);
        robustnessStepsSpinner.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!updatingRobustnessControls && selectedTask != null && newValue != null) {
                selectedTask.setRobustnessSteps(newValue);
                saveProject();
            }
        });
        grid.add(robustnessStepsSpinner, 3, 0);

        grid.add(new Label("Time Shifts (Anzahl):"), 0, 1);
        robustnessTimeShiftsSpinner = new Spinner<>(0, 100, WorkflowTask.DEFAULT_ROBUSTNESS_TIME_SHIFTS, 1);
        robustnessTimeShiftsSpinner.setEditable(true);
        robustnessTimeShiftsSpinner.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!updatingRobustnessControls && selectedTask != null && newValue != null) {
                selectedTask.setRobustnessTimeShifts(newValue);
                saveProject();
            }
        });
        grid.add(robustnessTimeShiftsSpinner, 1, 1);

        grid.add(new Label("Shift Tage (Period):"), 2, 1);
        robustnessShiftDaysSpinner = new Spinner<>(1, 365, WorkflowTask.DEFAULT_ROBUSTNESS_SHIFT_DAYS, 1);
        robustnessShiftDaysSpinner.setEditable(true);
        robustnessShiftDaysSpinner.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!updatingRobustnessControls && selectedTask != null && newValue != null) {
                selectedTask.setRobustnessShiftDays(newValue);
                saveProject();
            }
        });
        grid.add(robustnessShiftDaysSpinner, 3, 1);

        grid.add(new Label("Ausgeschlossene Parameter:"), 0, 2);
        robustnessExcludedParamsField = new TextField();
        robustnessExcludedParamsField.setPromptText("z.B. Inp_Min_Lot, Inp_Max_Lot (kommagetrennt)");
        robustnessExcludedParamsField.setOnAction(e -> {
            if (!updatingRobustnessControls && selectedTask != null) {
                selectedTask.setRobustnessExcludedParams(robustnessExcludedParamsField.getText());
                saveProject();
                updateRobustnessParamsTable();
            }
        });
        robustnessExcludedParamsField.textProperty().addListener((obs, oldText, newText) -> {
            if (!updatingRobustnessControls && selectedTask != null) {
                selectedTask.setRobustnessExcludedParams(newText);
                saveProject();
                updateRobustnessParamsTable();
            }
        });
        grid.add(robustnessExcludedParamsField, 1, 2, 3, 1);

        Label infoLabel = new Label(
                "Info: Der Parameter-Sensitivity Sweep variiert alle optimierten numerischen Parameter einzeln " +
                "um die eingestellte prozentuale Abweichung. Parameter in der Ausschlussliste oder mit weniger als 4 Werten " +
                "(Booleans/Enums) werden automatisch übersprungen."
        );
        infoLabel.setWrapText(true);
        infoLabel.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 12px;");

        panel.getChildren().addAll(heading, grid, new Separator(), infoLabel);
        return panel;
    }

    private void commitRobustnessPercentage(TextField field) {
        if (updatingRobustnessControls || selectedTask == null || field == null) return;
        double currentValue = selectedTask.getRobustnessSweepPct();
        try {
            double percentage = Double.parseDouble(field.getText().trim().replace(',', '.'));
            double fraction = percentage / 100.0;
            selectedTask.setRobustnessSweepPct(fraction);
            field.setText(String.format(Locale.US, "%.0f", percentage));
            field.setStyle("");
            saveProject();
        } catch (RuntimeException ex) {
            field.setText(String.format(Locale.US, "%.0f", currentValue * 100));
            field.setStyle("-fx-border-color: #ff5252;");
        }
    }

    private VBox createRobustnessParamsSubTab() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color: rgba(26, 30, 40, 0.85); -fx-border-color: #2e3545; -fx-border-radius: 6;");

        Label heading = new Label("Vorschau aller EA-Parameter & Sweep-Status");
        heading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        heading.setTextFill(Color.web("#00e5ff"));

        Label subtitle = new Label("Hier wird in Echtzeit angezeigt, welche EA-Parameter beim Robustness Test gesweept werden und welche durch Typ-Filter oder die Ausschlussliste übersprungen werden.");
        subtitle.setWrapText(true);
        subtitle.setStyle("-fx-text-fill: #9aa4b5; -fx-font-size: 12px;");

        robustnessParamsTable = new TableView<>();
        robustnessParamsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        robustnessParamsTable.setStyle("-fx-background-color: #121620; -fx-base: #121620;");
        robustnessParamsTable.setPrefHeight(380);

        TableColumn<SweptParamInfo, String> nameCol = new TableColumn<>("Parameter Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(220);

        TableColumn<SweptParamInfo, String> typeCol = new TableColumn<>("Datentyp");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        typeCol.setPrefWidth(140);

        TableColumn<SweptParamInfo, String> valCol = new TableColumn<>("Aktueller Wert");
        valCol.setCellValueFactory(new PropertyValueFactory<>("currentValue"));
        valCol.setPrefWidth(140);

        TableColumn<SweptParamInfo, String> statusCol = new TableColumn<>("Sweep-Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(280);
        statusCol.setCellFactory(col -> new TableCell<SweptParamInfo, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    SweptParamInfo row = getTableView().getItems().get(getIndex());
                    Label badge = new Label(item);
                    badge.setStyle("-fx-font-weight: bold; -fx-padding: 3 8 3 8; -fx-background-radius: 4; " +
                            "-fx-background-color: " + (row != null ? row.getStatusColor() : "#2e3545") + "; -fx-text-fill: white;");
                    setGraphic(badge);
                }
            }
        });

        robustnessParamsTable.getColumns().addAll(nameCol, typeCol, valCol, statusCol);

        panel.getChildren().addAll(heading, subtitle, robustnessParamsTable);
        return panel;
    }

    public void updateRobustnessParamsTable() {
        if (robustnessParamsTable == null) return;

        String currentExpert = (project != null && project.getExpert() != null && !project.getExpert().isBlank())
                ? project.getExpert() : engine.getExpert();

        List<com.backtester.config.EaParameter> params = eaParamManager.getEffectiveParameters(currentExpert);
        if (params == null || params.isEmpty()) {
            params = engine.getEaParameters();
        }

        Set<String> excludedSet = new java.util.HashSet<>();
        if (robustnessExcludedParamsField != null && robustnessExcludedParamsField.getText() != null) {
            for (String token : robustnessExcludedParamsField.getText().split("[,;\\s]+")) {
                if (!token.isBlank()) {
                    excludedSet.add(token.trim().toLowerCase(Locale.US));
                }
            }
        }

        ObservableList<SweptParamInfo> items = FXCollections.observableArrayList();
        if (params != null) {
            for (com.backtester.config.EaParameter p : params) {
                String name = p.getName();
                String val = p.getValue() != null ? p.getValue() : "";
                String typeStr;
                String status;
                String colorHex;

                if (p.isStringType()) {
                    typeStr = "Enum / Selection / Text";
                    status = "🔴 Übersprungen (Typ: Text/Enum)";
                    colorHex = "#883333";
                } else if (name.toLowerCase(Locale.US).startsWith("inp_use_") || val.equalsIgnoreCase("true") || val.equalsIgnoreCase("false")) {
                    typeStr = "Boolean (Switch)";
                    status = "🔴 Übersprungen (Typ: Boolean)";
                    colorHex = "#883333";
                } else if (excludedSet.contains(name.toLowerCase(Locale.US))) {
                    typeStr = "Numerisch";
                    status = "⚠️ Ausgeschlossen (Ausschlussliste)";
                    colorHex = "#aa6600";
                } else if (p.isOptimizeEnabled()) {
                    typeStr = "Numerisch (Optimierbar)";
                    status = "🟢 Wird gesweept (Numerisch & Optimiert)";
                    colorHex = "#1e8449";
                } else {
                    typeStr = "Numerisch (Statisch)";
                    status = "🟢 Wird gesweept (Numerisch)";
                    colorHex = "#1e8449";
                }

                items.add(new SweptParamInfo(name, typeStr, val, status, colorHex));
            }
        }

        robustnessParamsTable.setItems(items);
    }

    public static class SweptParamInfo {
        private final String name;
        private final String type;
        private final String currentValue;
        private final String status;
        private final String statusColor;

        public SweptParamInfo(String name, String type, String currentValue, String status, String statusColor) {
            this.name = name;
            this.type = type;
            this.currentValue = currentValue;
            this.status = status;
            this.statusColor = statusColor;
        }

        public String getName() { return name; }
        public String getType() { return type; }
        public String getCurrentValue() { return currentValue; }
        public String getStatus() { return status; }
        public String getStatusColor() { return statusColor; }
    }

    private VBox createStrategySelectionSubTab() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color: rgba(26, 30, 40, 0.85); -fx-border-color: #2e3545; -fx-border-radius: 6;");

        Label heading = new Label("Strategie-Auswahl (Expert Advisor)");
        heading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        heading.setTextFill(Color.web("#00e5ff"));

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);

        grid.add(new Label("Expert Advisor:"), 0, 0);
        String initialEA = (project != null && project.getExpert() != null && !project.getExpert().isBlank())
                ? project.getExpert() : (engine.getExpert() != null ? engine.getExpert() : "");
        expertField = new TextField(initialEA);
        expertField.setPrefWidth(280);
        expertField.textProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                String trimmed = newV.trim();
                engine.setExpert(trimmed);
                if (project != null) {
                    project.setExpert(trimmed);
                    saveProject();
                }
            }
        });

        Button browseBtn = new Button("📁 Durchsuchen");
        browseBtn.getStyleClass().add("button");
        browseBtn.setOnAction(e -> {
            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.setTitle("Select Expert Advisor");
            chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("MetaTrader EA", "*.ex5", "*.ex4"));
            java.io.File selected = chooser.showOpenDialog(root.getScene().getWindow());
            if (selected != null) {
                String path = selected.getName();
                if (path.toLowerCase().endsWith(".ex5") || path.toLowerCase().endsWith(".ex4")) {
                    path = path.substring(0, path.length() - 4);
                }
                engine.changeExpert(path);
                if (project != null) {
                    project.setExpert(path);
                }
                expertField.setText(path);
                saveProject();
            }
        });
        HBox eaBox = new HBox(8, expertField, browseBtn);
        grid.add(eaBox, 1, 0);

        panel.getChildren().addAll(heading, grid);
        return panel;
    }

    private VBox createOptimizerSettingsSubTab() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color: rgba(26, 30, 40, 0.85); -fx-border-color: #2e3545; -fx-border-radius: 6;");

        Label heading = new Label("MT5 Optimierungs-Metriken & Algorithmus");
        heading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        heading.setTextFill(Color.web("#00e5ff"));

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);

        grid.add(new Label("Algorithmus:"), 0, 0);
        optimizerAlgorithmCombo = new ComboBox<>(FXCollections.observableArrayList(OptimizationConfig.OPTIMIZATION_MODES));
        int currAlgo = engine.getOptimizationMode();
        optimizerAlgorithmCombo.getSelectionModel().select(currAlgo == 1 ? 0 : 1);
        optimizerAlgorithmCombo.setOnAction(e -> saveSelectedOptimizerSettings());
        grid.add(optimizerAlgorithmCombo, 1, 0);

        grid.add(new Label("Optimierungsziel:"), 0, 1);
        optimizerCriterionCombo = new ComboBox<>(FXCollections.observableArrayList(OptimizationConfig.OPTIMIZATION_CRITERIA));
        int currCrit = engine.getOptimizationCriterion();
        optimizerCriterionCombo.getSelectionModel().select(
                currCrit >= 0 && currCrit < optimizerCriterionCombo.getItems().size() ? currCrit : 4);
        optimizerCriterionCombo.setOnAction(e -> saveSelectedOptimizerSettings());
        grid.add(optimizerCriterionCombo, 1, 1);

        grid.add(new Label("Forward-Test:"), 0, 2);
        optimizerForwardModeCombo = new ComboBox<>(FXCollections.observableArrayList(OptimizationConfig.FORWARD_MODES));
        int currFwd = engine.getForwardMode();
        optimizerForwardModeCombo.getSelectionModel().select(
                currFwd >= 0 && currFwd < optimizerForwardModeCombo.getItems().size() ? currFwd : 1);
        optimizerForwardModeCombo.setOnAction(e -> saveSelectedOptimizerSettings());
        grid.add(optimizerForwardModeCombo, 1, 2);

        grid.add(new Label("Forward Datum:"), 0, 3);
        optimizerForwardDatePicker = new DatePicker(engine.getForwardDate() != null
                ? engine.getForwardDate() : LocalDate.now().minusMonths(2));
        optimizerForwardDatePicker.setOnAction(e -> saveSelectedOptimizerSettings());
        grid.add(optimizerForwardDatePicker, 1, 3);

        HBox btnBox = new HBox(12);
        Button openStep1Btn = new Button("⚙ EA Parameter & Suchräume konfigurieren");
        openStep1Btn.getStyleClass().add("button");
        openStep1Btn.setOnAction(e -> {
            WorkflowConfigDialogs.showStep1Dialog(engine, root.getScene().getWindow());
        });

        Button openStep2Btn = new Button("⚙ Vollständigen Optimizer-Dialog öffnen");
        openStep2Btn.getStyleClass().add("button-start");
        openStep2Btn.setOnAction(e -> {
            WorkflowConfigDialogs.showStep2Dialog(engine, root.getScene().getWindow(), () -> {
                if (selectedTask != null && selectedTask.getType() == WorkflowTask.TaskType.OPTIMIZER) {
                    selectedTask.setOptimizerMode(engine.getOptimizationMode());
                    selectedTask.setOptimizerCriterion(engine.getOptimizationCriterion());
                    selectedTask.setOptimizerForwardMode(engine.getForwardMode());
                    selectedTask.setOptimizerForwardDate(engine.getForwardDate() != null
                            ? engine.getForwardDate().toString() : "");
                    updateOptimizerControls(selectedTask);
                    saveProject();
                }
            });
        });

        btnBox.getChildren().addAll(openStep1Btn, openStep2Btn);

        panel.getChildren().addAll(heading, grid, new Separator(), btnBox);
        return panel;
    }

    private VBox createResultsTabContent() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(10));
        Label lbl = new Label("Vollbild-Ergebnisansicht für selektierte Databank");
        lbl.setStyle("-fx-text-fill: #7e889a;");
        box.getChildren().add(lbl);
        return box;
    }

    private VBox createBottomDatabankPanel() {
        VBox panel = new VBox(8);
        panel.setPadding(new Insets(8));
        panel.setStyle("-fx-background-color: rgba(15, 18, 26, 0.95); -fx-border-color: #00e5ff; -fx-border-width: 1 0 0 0;");
        panel.setMinHeight(160);
        panel.setPrefHeight(260);

        // Databank Header Toolbar
        FlowPane bar = new FlowPane(15, 8);
        bar.setAlignment(Pos.CENTER_LEFT);
        databankToolbar = bar;

        Button newDatabankBtn = new Button("+ New databank");
        newDatabankBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #00e5ff; -fx-font-weight: bold; -fx-cursor: hand;");
        newDatabankBtn.setOnAction(e -> promptCreateNewDatabank());

        Button clearCurrentDbBtn = new Button("🧹 Strategien in Databank leeren");
        clearCurrentDbBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ffab40; -fx-font-weight: bold; -fx-cursor: hand;");
        clearCurrentDbBtn.setOnAction(e -> {
            Tab activeTab = bottomDatabankTabPane.getSelectionModel().getSelectedItem();
            if (activeTab != null) {
                String dbName = activeTab.getText().replaceAll("\\s*\\(\\d+\\)$", "");
                if (!confirmDestructiveAction("Databank leeren",
                        "Alle Strategien aus '" + dbName + "' unwiderruflich entfernen?")) return;
                databankManager.clearDatabank(dbName);
                flushProjectSaveAsync(() -> refreshDatabanksUI(dbName));
                logToConsole("DATABANK", "Alle Strategien aus Databank '" + dbName + "' wurden geleert.");
            }
        });

        Button clearAllBtn = new Button("Clear all databanks");
        clearAllBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ff5252; -fx-font-weight: bold; -fx-cursor: hand;");
        clearAllBtn.setOnAction(e -> {
            if (!confirmDestructiveAction("Alle Databanken leeren",
                    "Alle Strategien aus allen Databanken entfernen? (Die Databank-Tabs bleiben erhalten)")) return;
            databankManager.clearAll();
            flushProjectSaveAsync(() -> refreshDatabanksUI());
            logToConsole("DATABANK", "Alle Databanken wurden geleert.");
        });

        Button deleteDatabankBtn = new Button("🗑 Databank löschen");
        deleteDatabankBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ff5252; -fx-font-weight: bold; -fx-cursor: hand;");
        deleteDatabankBtn.setOnAction(e -> deleteCurrentDatabank());

        Button deleteSelectedStratsBtn = new Button("🗑 Selektierte Strategien löschen");
        deleteSelectedStratsBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ffab40; -fx-font-weight: bold; -fx-cursor: hand;");
        deleteSelectedStratsBtn.setOnAction(e -> {
            Tab activeTab = bottomDatabankTabPane.getSelectionModel().getSelectedItem();
            if (activeTab != null && activeTab.getContent() instanceof TableView) {
                @SuppressWarnings("unchecked")
                TableView<CombinedPass> table = (TableView<CombinedPass>) activeTab.getContent();
                String dbName = activeTab.getText().replaceAll("\\s*\\(\\d+\\)$", "");
                deleteSelectedRowsFromDatabank(dbName, table);
            }
        });

        persistDatabanksCheckBox = new CheckBox("💾 Databanken persistent in DB speichern");
        persistDatabanksCheckBox.setStyle("-fx-text-fill: #00e5ff; -fx-font-weight: bold; -fx-cursor: hand;");
        persistDatabanksCheckBox.setSelected(project != null ? project.isSaveDatabanksPersistently() : true);
        persistDatabanksCheckBox.setOnAction(e -> {
            if (project != null) {
                project.setSaveDatabanksPersistently(persistDatabanksCheckBox.isSelected());
                saveProject();
            }
        });

        Button configColumnsBtn = new Button("⚙ Columns");
        configColumnsBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ffd740; -fx-font-weight: bold; -fx-cursor: hand;");
        configColumnsBtn.setOnAction(e -> {
            DatabankColumnChooserDialog.show(root.getScene().getWindow(), this::refreshDatabanksUI);
        });

        Button scoreWeightsBtn = new Button("⚖ Score-Gewichtung");
        scoreWeightsBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #00e5ff; -fx-font-weight: bold; -fx-cursor: hand;");
        scoreWeightsBtn.setOnAction(e -> {
            WorkflowConfigDialogs.showScoreWeightsDialog(root.getScene().getWindow());
            refreshDatabanksUI();
        });

        Button compareDatabanksBtn = new Button("📊 Databanken vergleichen");
        compareDatabanksBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #76ff03; -fx-font-weight: bold; -fx-cursor: hand;");
        compareDatabanksBtn.setOnAction(e -> DatabankComparisonDialog.show(root.getScene().getWindow(), databankManager));

        Button showEquityCurvesBtn = new Button("📈 Equitykurven alle anzeigen");
        showEquityCurvesBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #00e5ff; -fx-font-weight: bold; -fx-cursor: hand;");
        showEquityCurvesBtn.setTooltip(new Tooltip("Alle Equitykurven und Backtest-Grafiken der aktuellen Databank anzeigen"));
        showEquityCurvesBtn.setOnAction(e -> {
            Tab activeTab = bottomDatabankTabPane.getSelectionModel().getSelectedItem();
            String currentDbName = activeTab != null ? activeTab.getText().replaceAll("\\s*\\(\\d+\\)$", "") : DatabankManager.RESULTS;
            DatabankEquityGalleryDialog.show(root.getScene().getWindow(), databankManager, currentDbName, project);
        });

        Button backupBtn = new Button("💾 Backup");
        backupBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #b388ff; -fx-font-weight: bold; -fx-cursor: hand;");
        backupBtn.setTooltip(new Tooltip("Projekt und Databanken in eine Datei exportieren"));
        backupBtn.setOnAction(e -> backupProject());

        Button restoreBtn = new Button("📂 Restore");
        restoreBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #b388ff; -fx-font-weight: bold; -fx-cursor: hand;");
        restoreBtn.setTooltip(new Tooltip("Projekt und Databanken aus einer Datei importieren"));
        restoreBtn.setOnAction(e -> restoreProject());

        bar.getChildren().addAll(newDatabankBtn, clearCurrentDbBtn, clearAllBtn, deleteDatabankBtn,
                deleteSelectedStratsBtn, configColumnsBtn, scoreWeightsBtn, compareDatabanksBtn, showEquityCurvesBtn, backupBtn, restoreBtn, persistDatabanksCheckBox);

        bottomDatabankTabPane = new TabPane();
        VBox.setVgrow(bottomDatabankTabPane, Priority.ALWAYS);

        refreshDatabanksUI();

        panel.getChildren().addAll(bar, bottomDatabankTabPane);
        return panel;
    }

    private void promptCreateNewDatabank() {
        TextInputDialog dialog = new TextInputDialog("OOS_Passed");
        dialog.setTitle("New Databank");
        dialog.setHeaderText("Enter name for new Databank:");
        dialog.setContentText("Name:");

        dialog.getDialogPane().setStyle("-fx-background-color: #0b0d13;");
        if (root.getScene() != null && !root.getScene().getStylesheets().isEmpty()) {
            dialog.getDialogPane().getStylesheets().addAll(root.getScene().getStylesheets());
        }

        dialog.showAndWait().ifPresent(name -> {
            if (name != null && !name.trim().isEmpty()) {
                if (databankManager.createDatabank(name.trim())) {
                    saveProject();
                    refreshDatabanksUI();
                    updateDatabankComboBoxes();
                } else {
                    Alert alert = new Alert(Alert.AlertType.WARNING, "Databank exists or invalid name.", ButtonType.OK);
                    alert.initOwner(root.getScene() != null ? root.getScene().getWindow() : null);
                    alert.showAndWait();
                }
            }
        });
    }

    private void deleteCurrentDatabank() {
        Tab currentTab = bottomDatabankTabPane.getSelectionModel().getSelectedItem();
        if (currentTab == null) return;
        String dbName = currentTab.getText().replaceAll("\\s*\\(\\d+\\)$", "");
        deleteDatabankByName(dbName);
    }

    private void deleteDatabankByName(String dbName) {
        if (dbName == null) return;
        if (dbName.equalsIgnoreCase("Results") || dbName.equalsIgnoreCase("Existing portfolio") || dbName.equalsIgnoreCase("Final")) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Standard-Databanken (" + dbName + ") können nicht gelöscht werden.", ButtonType.OK);
            alert.initOwner(root.getScene() != null ? root.getScene().getWindow() : null);
            alert.show();
            return;
        }

        if (!confirmDestructiveAction("Databank löschen",
                "Databank '" + dbName + "' einschließlich aller Strategien löschen?")) return;

        databankManager.removeDatabank(dbName);
        updateDatabankComboBoxes();
        flushProjectSaveAsync(() -> refreshDatabanksUI("Results"));
        logToConsole("DATABANK", "Databank '" + dbName + "' wurde gelöscht.");
    }

    private boolean confirmDestructiveAction(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.CANCEL, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        if (root.getScene() != null) alert.initOwner(root.getScene().getWindow());
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void deleteSelectedRowsFromDatabank(String dbName, TableView<CombinedPass> table) {
        if (table == null) return;
        List<CombinedPass> selected = new ArrayList<>(table.getSelectionModel().getSelectedItems());
        if (selected == null || selected.isEmpty()) return;

        databankManager.removePassesFromDatabank(dbName, selected);
        flushProjectSaveAsync(() -> refreshDatabanksUI(dbName));
        logToConsole("DATABANK", selected.size() + " Strategie(n) aus Databank '" + dbName + "' gelöscht.");
    }

    private void updateDatabankComboBoxes() {
        List<String> names = databankManager.getDatabankNames();
        if (selectedTask != null) {
            String taskSrc = selectedTask.getSourceDatabank();
            String taskTgt = selectedTask.getTargetDatabank();
            if (taskSrc != null && !taskSrc.isBlank() && !names.contains(taskSrc)) names.add(taskSrc);
            if (taskTgt != null && !taskTgt.isBlank() && !names.contains(taskTgt)) names.add(taskTgt);
        }
        if (sourceDatabankCombo != null) {
            String currSrc = selectedTask != null ? selectedTask.getSourceDatabank() : sourceDatabankCombo.getValue();
            sourceDatabankCombo.getItems().setAll(names);
            if (currSrc != null && names.contains(currSrc)) sourceDatabankCombo.setValue(currSrc);
            else if (!names.isEmpty()) sourceDatabankCombo.setValue(names.get(0));
        }
        if (targetDatabankCombo != null) {
            String currTgt = selectedTask != null ? selectedTask.getTargetDatabank() : targetDatabankCombo.getValue();
            targetDatabankCombo.getItems().setAll(names);
            if (currTgt != null && names.contains(currTgt)) targetDatabankCombo.setValue(currTgt);
            else if (!names.isEmpty()) targetDatabankCombo.setValue(names.get(0));
        }
        if (rankingSourceCombo != null) {
            String currSrc = selectedTask != null ? selectedTask.getSourceDatabank() : rankingSourceCombo.getValue();
            rankingSourceCombo.getItems().setAll(names);
            if (currSrc != null && names.contains(currSrc)) rankingSourceCombo.setValue(currSrc);
            else if (!names.isEmpty()) rankingSourceCombo.setValue(names.get(0));
        }
        if (rankingTargetCombo != null) {
            String currTgt = selectedTask != null ? selectedTask.getTargetDatabank() : rankingTargetCombo.getValue();
            rankingTargetCombo.getItems().setAll(names);
            if (currTgt != null && names.contains(currTgt)) rankingTargetCombo.setValue(currTgt);
            else if (!names.isEmpty()) rankingTargetCombo.setValue(names.get(0));
        }
    }

    private void refreshDatabanksUI() {
        refreshDatabanksUI(null);
    }

    private void refreshDatabanksUI(String targetTabToFocus) {
        if (bottomDatabankTabPane == null) return;

        Tab currentTab = bottomDatabankTabPane.getSelectionModel().getSelectedItem();
        String activeDbName = targetTabToFocus != null ? targetTabToFocus : (currentTab != null ? currentTab.getText().replaceAll("\\s*\\(\\d+\\)$", "") : null);

        bottomDatabankTabPane.getTabs().clear();
        Tab tabToSelect = null;

        Set<DatabankColumnChooserDialog.DatabankColumn> visibleCols = DatabankColumnChooserDialog.getVisibleColumns();

        for (String dbName : databankManager.getDatabankNames()) {
            boolean isStandard = dbName.equalsIgnoreCase("Results") || dbName.equalsIgnoreCase("Existing portfolio") || dbName.equalsIgnoreCase("Final");
            List<CombinedPass> passes = databankManager.getDatabank(dbName);
            Tab tab = new Tab(dbName + " (" + passes.size() + ")");
            tab.setClosable(!isStandard);
            tab.setOnCloseRequest(e -> {
                e.consume();
                deleteDatabankByName(dbName);
            });

            TableView<CombinedPass> table = new TableView<>();
            table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            table.getItems().setAll(passes);

            table.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.DELETE) {
                    deleteSelectedRowsFromDatabank(dbName, table);
                }
            });

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.NAME)) {
                TableColumn<CombinedPass, String> nameCol = new TableColumn<>("Strategy Name");
                nameCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getStrategyName()));
                nameCol.setPrefWidth(130);
                table.getColumns().add(nameCol);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.PASS)) {
                TableColumn<CombinedPass, Integer> passCol = new TableColumn<>("Pass");
                passCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getPassNumber()));
                passCol.setPrefWidth(60);
                table.getColumns().add(passCol);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.SCORE)) {
                TableColumn<CombinedPass, Double> scoreCol = new TableColumn<>("Score");
                scoreCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getScore()));
                scoreCol.setPrefWidth(70);
                table.getColumns().add(scoreCol);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.BT_PROFIT)) {
                TableColumn<CombinedPass, Double> btProf = new TableColumn<>("BT Profit");
                btProf.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getBtProfit()));
                btProf.setPrefWidth(90);
                table.getColumns().add(btProf);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.FW_PROFIT)) {
                TableColumn<CombinedPass, Double> fwProf = new TableColumn<>("FW Profit");
                fwProf.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getFwProfit()));
                fwProf.setPrefWidth(90);
                table.getColumns().add(fwProf);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.LT_PROFIT)) {
                TableColumn<CombinedPass, Double> ltProf = new TableColumn<>("LT Profit");
                ltProf.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getLtProfit()));
                ltProf.setPrefWidth(90);
                table.getColumns().add(ltProf);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.BT_PF)) {
                TableColumn<CombinedPass, String> btPf = new TableColumn<>("BT Profit Factor");
                btPf.setCellValueFactory(c -> {
                    double v = c.getValue().getBtPf();
                    return new SimpleStringProperty(Double.isNaN(v) || v <= 0 ? "-" : String.format(Locale.US, "%.2f", v));
                });
                btPf.setPrefWidth(115);
                table.getColumns().add(btPf);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.FW_PF)) {
                TableColumn<CombinedPass, String> fwPf = new TableColumn<>("FW Profit Factor");
                fwPf.setCellValueFactory(c -> {
                    double v = c.getValue().getFwPf();
                    return new SimpleStringProperty(Double.isNaN(v) || v <= 0 ? "-" : String.format(Locale.US, "%.2f", v));
                });
                fwPf.setPrefWidth(115);
                table.getColumns().add(fwPf);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.LT_PF)) {
                TableColumn<CombinedPass, String> ltPf = new TableColumn<>("LT Profit Factor");
                ltPf.setCellValueFactory(c -> {
                    double v = c.getValue().getLtPf();
                    return new SimpleStringProperty(Double.isNaN(v) || v <= 0 ? "-" : String.format(Locale.US, "%.2f", v));
                });
                ltPf.setPrefWidth(115);
                table.getColumns().add(ltPf);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.BT_DD)) {
                TableColumn<CombinedPass, Double> btDd = new TableColumn<>("BT DD %");
                btDd.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getBtDd()));
                btDd.setPrefWidth(80);
                table.getColumns().add(btDd);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.FW_DD)) {
                TableColumn<CombinedPass, Double> fwDd = new TableColumn<>("FW DD %");
                fwDd.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getFwDd()));
                fwDd.setPrefWidth(80);
                table.getColumns().add(fwDd);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.LT_DD)) {
                TableColumn<CombinedPass, Double> ltDd = new TableColumn<>("LT DD %");
                ltDd.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getLtDd()));
                ltDd.setPrefWidth(80);
                table.getColumns().add(ltDd);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.BT_TRADES)) {
                TableColumn<CombinedPass, Integer> btTr = new TableColumn<>("BT Trades");
                btTr.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getBtTrades()));
                btTr.setPrefWidth(75);
                table.getColumns().add(btTr);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.FW_TRADES)) {
                TableColumn<CombinedPass, Integer> fwTr = new TableColumn<>("FW Trades");
                fwTr.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getFwTrades()));
                fwTr.setPrefWidth(75);
                table.getColumns().add(fwTr);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.LT_TRADES)) {
                TableColumn<CombinedPass, Integer> ltTr = new TableColumn<>("LT Trades");
                ltTr.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getLtTrades()));
                ltTr.setPrefWidth(75);
                table.getColumns().add(ltTr);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.BT_SHARPE)) {
                TableColumn<CombinedPass, Double> btSh = new TableColumn<>("BT Sharpe");
                btSh.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getBtSharpe()));
                btSh.setPrefWidth(80);
                table.getColumns().add(btSh);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.FW_SHARPE)) {
                TableColumn<CombinedPass, Double> fwSh = new TableColumn<>("FW Sharpe");
                fwSh.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getFwSharpe()));
                fwSh.setPrefWidth(80);
                table.getColumns().add(fwSh);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.BT_RECOVERY)) {
                TableColumn<CombinedPass, Double> btRec = new TableColumn<>("BT Rec");
                btRec.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getBtRecovery()));
                btRec.setPrefWidth(80);
                table.getColumns().add(btRec);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.FW_RECOVERY)) {
                TableColumn<CombinedPass, Double> fwRec = new TableColumn<>("FW Rec");
                fwRec.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getFwRecovery()));
                fwRec.setPrefWidth(80);
                table.getColumns().add(fwRec);
            }

            // Robustness actions are scoped to the exact run that produced this databank.
            final long databankSensitivityTimestamp = findSensitivityRunTimestampForDatabank(dbName);

            // Context Menu & Row click handlers
            table.setRowFactory(tv -> {
                TableRow<CombinedPass> row = new TableRow<>();

                ContextMenu contextMenu = new ContextMenu();
                MenuItem inspectItem = new MenuItem("🔍 Details & EA Parameter anzeigen (Doppelklick)");
                inspectItem.setOnAction(e -> {
                    if (!row.isEmpty()) StrategyDetailsModalDialog.show(row.getItem(), dbName, project, root.getScene().getWindow(), 0);
                });

                MenuItem singleBtItem = new MenuItem("▶ Einzel-Backtest im MetaTrader ausführen (Terminal bleibt offen)");
                singleBtItem.setOnAction(e -> {
                    if (!row.isEmpty()) SingleBacktestHelper.runSingleBacktestInMetaTrader(row.getItem(), dbName, project, root.getScene().getWindow());
                });

                MenuItem sensitivityItem = new MenuItem("📈 Sensitivitäts-Kennlinien & Stresstest (Rechtsklick)");
                sensitivityItem.setOnAction(e -> {
                    if (!row.isEmpty()) StrategyDetailsModalDialog.show(
                            row.getItem(), dbName, project, root.getScene().getWindow(), 3, databankSensitivityTimestamp);
                });

                MenuItem htmlReportItem = new MenuItem("🌐 HTML Robustness Scanner Report im Browser öffnen");
                htmlReportItem.setOnAction(e -> {
                    if (!row.isEmpty()) StrategyDetailsModalDialog.openRobustnessHtmlReport(
                            row.getItem(), databankSensitivityTimestamp);
                });

                MenuItem deleteItem = new MenuItem("🗑 Selektierte Strategie(n) löschen (Entf)");
                deleteItem.setOnAction(e -> deleteSelectedRowsFromDatabank(dbName, table));

                SeparatorMenuItem robustnessSeparator = new SeparatorMenuItem();
                contextMenu.getItems().addAll(inspectItem, singleBtItem, sensitivityItem, htmlReportItem, robustnessSeparator, deleteItem);
                contextMenu.setOnShowing(e -> {
                    boolean hasSensitivity = !row.isEmpty()
                            && DatabaseManager.getInstance().hasSensitivityDetails(
                                    databankSensitivityTimestamp,
                                    row.getItem().getPassNumber(),
                                    row.getItem().getStrategyName());
                    sensitivityItem.setVisible(hasSensitivity);
                    htmlReportItem.setVisible(hasSensitivity);
                    robustnessSeparator.setVisible(hasSensitivity);
                });

                row.setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2 && (!row.isEmpty())) {
                        CombinedPass rowData = row.getItem();
                        StrategyDetailsModalDialog.show(rowData, dbName, project, root.getScene().getWindow(), 0);
                    }
                });

                row.contextMenuProperty().bind(
                    javafx.beans.binding.Bindings.when(row.emptyProperty())
                        .then((ContextMenu) null)
                        .otherwise(contextMenu)
                );
                return row;
            });

            tab.setContent(table);
            bottomDatabankTabPane.getTabs().add(tab);

            if (activeDbName != null && dbName.equalsIgnoreCase(activeDbName)) {
                tabToSelect = tab;
            }
        }

        if (tabToSelect != null) {
            bottomDatabankTabPane.getSelectionModel().select(tabToSelect);
        }
    }

    // ─── Task Selection & Form Update ─────────────────────────────────────────

    private void refreshTaskChain() {
        taskChainListBox.getChildren().clear();
        if (project == null || project.getTasks() == null) return;

        List<WorkflowTask> tasks = project.getTasks();
        for (int i = 0; i < tasks.size(); i++) {
            WorkflowTask task = tasks.get(i);
            taskChainListBox.getChildren().add(createTaskCard(task, i, tasks.size()));
        }
    }

    private VBox createTaskCard(WorkflowTask task, int index, int totalTasks) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(10));
        card.setStyle(
            (selectedTask == task ? "-fx-background-color: rgba(0, 229, 255, 0.15); -fx-border-color: #00e5ff; " : "-fx-background-color: rgba(26, 30, 40, 0.85); -fx-border-color: #3e4555; ") +
            "-fx-border-width: 1.5; -fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: hand;"
        );

        card.setOnMouseClicked(e -> selectTask(task));

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label((index + 1) + ". " + task.getName());
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        title.setTextFill(task.isEnabled() ? Color.web("#e6e9f0") : Color.web("#7e889a"));
        HBox.setHgrow(title, Priority.ALWAYS);

        CheckBox toggleBox = new CheckBox();
        toggleBox.setSelected(task.isEnabled());
        toggleBox.setOnAction(e -> {
            task.setEnabled(toggleBox.isSelected());
            saveProject();
            refreshTaskChain();
        });

        topRow.getChildren().addAll(title, toggleBox);

        HBox subRow = new HBox(8);
        subRow.setAlignment(Pos.CENTER_LEFT);

        Label catLabel = new Label(task.getType() != null ? task.getType().getDisplayName() : "");
        catLabel.setStyle("-fx-text-fill: #00e5ff; -fx-font-size: 11px;");
        HBox.setHgrow(catLabel, Priority.ALWAYS);

        Label statusBadge = new Label(task.getStatus().getLabel());
        String statusColor = "#7e889a";
        if (task.getStatus() == WorkflowTask.TaskStatus.COMPLETED) statusColor = "#00e676";
        else if (task.getStatus() == WorkflowTask.TaskStatus.RUNNING) statusColor = "#00e5ff";
        else if (task.getStatus() == WorkflowTask.TaskStatus.FAILED) statusColor = "#ff5252";
        statusBadge.setStyle("-fx-text-fill: " + statusColor + "; -fx-font-weight: bold; -fx-font-size: 11px;");

        subRow.getChildren().addAll(catLabel, statusBadge);

        // Databank routing info directly in card rectangle
        String srcDb = task.getSourceDatabank();
        if (srcDb == null || srcDb.isBlank()) srcDb = "Results";
        String tgtDb = task.getTargetDatabank();
        if (tgtDb == null || tgtDb.isBlank()) tgtDb = "Results";

        HBox dbRoutingRow = null;
        if (task.getType() == WorkflowTask.TaskType.STRATEGY_SELECTION) {
            // Strategy Selection defines EA/Symbol parameters only, no databanks.
        } else if (task.getType() == WorkflowTask.TaskType.OPTIMIZER) {
            dbRoutingRow = new HBox(6);
            dbRoutingRow.setAlignment(Pos.CENTER_LEFT);
            dbRoutingRow.setPadding(new Insets(4, 8, 4, 8));
            dbRoutingRow.setStyle("-fx-background-color: rgba(11, 14, 20, 0.8); -fx-border-color: #262d3d; -fx-border-radius: 4; -fx-background-radius: 4;");

            Label dbFlowLabel = new Label("📤 Speichert in: " + tgtDb);
            dbFlowLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
            dbFlowLabel.setTextFill(Color.web("#00e5ff"));
            dbRoutingRow.getChildren().add(dbFlowLabel);
        } else if (task.getType() == WorkflowTask.TaskType.PORTFOLIO_EXPORT) {
            dbRoutingRow = new HBox(6);
            dbRoutingRow.setAlignment(Pos.CENTER_LEFT);
            dbRoutingRow.setPadding(new Insets(4, 8, 4, 8));
            dbRoutingRow.setStyle("-fx-background-color: rgba(11, 14, 20, 0.8); -fx-border-color: #262d3d; -fx-border-radius: 4; -fx-background-radius: 4;");

            Label dbFlowLabel = new Label("📥 Liest aus: " + srcDb);
            dbFlowLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
            dbFlowLabel.setTextFill(Color.web("#00e5ff"));
            dbRoutingRow.getChildren().add(dbFlowLabel);
        } else {
            dbRoutingRow = new HBox(6);
            dbRoutingRow.setAlignment(Pos.CENTER_LEFT);
            dbRoutingRow.setPadding(new Insets(4, 8, 4, 8));
            dbRoutingRow.setStyle("-fx-background-color: rgba(11, 14, 20, 0.8); -fx-border-color: #262d3d; -fx-border-radius: 4; -fx-background-radius: 4;");

            Label dbFlowLabel = new Label("📥 " + srcDb + "  ➔  📤 " + tgtDb);
            dbFlowLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
            dbFlowLabel.setTextFill(Color.web("#00e5ff"));
            dbRoutingRow.getChildren().add(dbFlowLabel);
        }

        HBox actionsRow = new HBox(5);
        actionsRow.setAlignment(Pos.CENTER_RIGHT);

        Button upBtn = new Button("▲");
        upBtn.setDisable(index == 0);
        upBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #cbd5e1; -fx-padding: 2; -fx-cursor: hand;");
        upBtn.setTooltip(new Tooltip("Task nach oben verschieben"));
        upBtn.setOnAction(e -> {
            e.consume();
            if (project.moveTaskUp(index)) {
                saveProject();
                refreshTaskChain();
            }
        });

        Button downBtn = new Button("▼");
        downBtn.setDisable(index == totalTasks - 1);
        downBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #cbd5e1; -fx-padding: 2; -fx-cursor: hand;");
        downBtn.setTooltip(new Tooltip("Task nach unten verschieben"));
        downBtn.setOnAction(e -> {
            e.consume();
            if (project.moveTaskDown(index)) {
                saveProject();
                refreshTaskChain();
            }
        });

        Button runSingleBtn = new Button("▶");
        runSingleBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #00e5ff; -fx-font-weight: bold; -fx-padding: 2; -fx-cursor: hand;");
        runSingleBtn.setTooltip(new Tooltip("Einzelstep ausführen (nur diese Kachel)"));
        runSingleBtn.setOnAction(e -> {
            e.consume();
            logger.info(">>> USER CLICKED SINGLE-STEP BUTTON ▶ FOR TASK: '{}' (Type: {}, Source: '{}', Target: '{}')",
                task.getName(), task.getType(), task.getSourceDatabank(), task.getTargetDatabank());
            selectTask(task);
            runSingleTask(task);
        });

        Button configBtn = new Button(task.getType() == WorkflowTask.TaskType.DIVERSITY_FILTER
                ? "⚙ Einstellungen" : "⚙");
        configBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ffd740; -fx-padding: 2; -fx-cursor: hand;");
        configBtn.setTooltip(new Tooltip(task.getType() == WorkflowTask.TaskType.DIVERSITY_FILTER
                ? "Diversitäts-Clustering konfigurieren"
                : "Modulname und Task-Einstellungen öffnen"));
        configBtn.setOnAction(e -> {
            e.consume();
            openTaskSettings(task);
        });

        Button deleteBtn = new Button("🗑");
        deleteBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ff5252; -fx-padding: 2; -fx-cursor: hand;");
        deleteBtn.setTooltip(new Tooltip("Task aus dem Workflow löschen"));
        deleteBtn.setOnAction(e -> {
            e.consume();
            project.removeTask(task);
            saveProject();
            refreshTaskChain();
            if (selectedTask == task) {
                selectTask(project.getTasks().isEmpty() ? null : project.getTasks().get(0));
            }
        });

        actionsRow.getChildren().addAll(upBtn, downBtn, runSingleBtn, configBtn, deleteBtn);
        if (dbRoutingRow != null) {
            card.getChildren().addAll(topRow, subRow, dbRoutingRow, actionsRow);
        } else {
            card.getChildren().addAll(topRow, subRow, actionsRow);
        }
        return card;
    }

    private void openTaskSettings(WorkflowTask task) {
        selectTask(task);
        centerMainTabPane.getSelectionModel().select(fullSettingsTab);

        if (task.getType() == WorkflowTask.TaskType.DIVERSITY_FILTER) {
            if (diversitySubTab != null) {
                fullSettingsSubTabPane.getSelectionModel().select(diversitySubTab);
            }
            WorkflowConfigDialogs.showDiversityClusteringDialog(
                    task,
                    databankManager.getDatabankNames(),
                    root.getScene() != null ? root.getScene().getWindow() : null,
                    () -> {
                        databankManager.createDatabank(task.getTargetDatabank());
                        saveProject();
                        selectTask(task);
                        centerMainTabPane.getSelectionModel().select(fullSettingsTab);
                        if (diversitySubTab != null) {
                            fullSettingsSubTabPane.getSelectionModel().select(diversitySubTab);
                        }
                    });
            return;
        }

        Platform.runLater(() -> {
            if (taskNameField != null) {
                taskNameField.requestFocus();
                taskNameField.selectAll();
            }
        });
    }

    private void selectTask(WorkflowTask task) {
        if (this.selectedTask != null && this.selectedTask != task) {
            applySelectedTaskName();
            commitCurrentTaskDataSettings();
            saveOptimizerOutputDirectory();
        }
        this.selectedTask = task;
        refreshTaskChain();
        updateDatabankComboBoxes();
        if (task != null) {
            currentTaskSettingsHeader.setText("Advanced settings for '" + task.getName() + "'");
            if (taskNameField != null) {
                taskNameField.setDisable(false);
                taskNameField.setText(task.getName());
            }
            String src = task.getSourceDatabank() != null ? task.getSourceDatabank() : "Results";
            String tgt = task.getTargetDatabank() != null ? task.getTargetDatabank() : "Results";

            if (sourceDatabankCombo != null) sourceDatabankCombo.setValue(src);
            if (targetDatabankCombo != null) targetDatabankCombo.setValue(tgt);
            if (rankingSourceCombo != null) rankingSourceCombo.setValue(src);
            if (rankingTargetCombo != null) rankingTargetCombo.setValue(tgt);

            boolean hasSource = (task.getType() != WorkflowTask.TaskType.STRATEGY_SELECTION && task.getType() != WorkflowTask.TaskType.OPTIMIZER);
            boolean hasTarget = (task.getType() != WorkflowTask.TaskType.STRATEGY_SELECTION && task.getType() != WorkflowTask.TaskType.PORTFOLIO_EXPORT);

            if (rankingSourceLabel != null) rankingSourceLabel.setDisable(!hasSource);
            if (rankingSourceCombo != null) rankingSourceCombo.setDisable(!hasSource);
            if (sourceDatabankCombo != null) sourceDatabankCombo.setDisable(!hasSource);

            if (rankingTargetLabel != null) rankingTargetLabel.setDisable(!hasTarget);
            if (rankingTargetCombo != null) rankingTargetCombo.setDisable(!hasTarget);
            if (targetDatabankCombo != null) targetDatabankCombo.setDisable(!hasTarget);
            updatingDiversityControls = true;
            try {
                if (diversityParamDiffField != null) {
                    diversityParamDiffField.setText(String.format(Locale.US, "%.0f",
                            task.getDiversityParamDiffPct() * 100));
                    diversityParamDiffField.setStyle("");
                }
                if (diversityTradeDiffField != null) {
                    diversityTradeDiffField.setText(String.format(Locale.US, "%.0f",
                            task.getDiversityTradeDiffPct() * 100));
                    diversityTradeDiffField.setStyle("");
                }
                if (diversityMinDiffParamsSpinner != null) {
                    diversityMinDiffParamsSpinner.getValueFactory().setValue(task.getDiversityMinDifferentParams());
                }
                if (diversityMaxStrategiesSpinner != null) {
                    diversityMaxStrategiesSpinner.getValueFactory().setValue(task.getDiversityMaxStrategies());
                }
            } finally {
                updatingDiversityControls = false;
            }
            if (expertField != null) {
                String currentEA = (project != null && project.getExpert() != null && !project.getExpert().isBlank())
                        ? project.getExpert() : engine.getExpert();
                expertField.setText(currentEA != null ? currentEA : "");
            }
            deleteFailedCheckBox.setSelected(task.isDeleteFailed());
            filterConditionsTable.getItems().setAll(task.getFilterConditions());

            if (execModeCombo != null) {
                switch (task.getExecutionMode()) {
                    case WorkflowTask.MODE_EVERY_TICK: execModeCombo.setValue("Every Tick (Ticksimulation)"); break;
                    case WorkflowTask.MODE_REAL_TICKS: execModeCombo.setValue("Every Tick based on Real Ticks (Realtick)"); break;
                    case WorkflowTask.MODE_OPEN_PRICES: execModeCombo.setValue("Open Prices Only"); break;
                    default: execModeCombo.setValue("OHLC M1 (Every tick based on OHLC M1)"); break;
                }
            }
            if (symbolCombo != null) {
                String taskSymbol = task.getRetestSymbol();
                symbolCombo.setValue(taskSymbol != null && !taskSymbol.isBlank()
                        ? taskSymbol : (project != null ? project.getSymbol() : "EURUSD"));
            }
            if (timeframeCombo != null) {
                String taskPeriod = task.getRetestPeriod();
                timeframeCombo.setValue(taskPeriod != null && !taskPeriod.isBlank()
                        ? taskPeriod : (project != null ? project.getPeriod() : "H1"));
            }
            if (startDatePicker != null) {
                LocalDate startVal = parseDateOrNull(task.getStartDate());
                if (startVal == null) {
                    startVal = engine.getFromDate() != null ? engine.getFromDate() : LocalDate.now().minusYears(2);
                    task.setStartDate(startVal.toString());
                }
                startDatePicker.setValue(startVal);
            }
            if (endDatePicker != null) {
                LocalDate endVal = parseDateOrNull(task.getEndDate());
                if (endVal == null) {
                    endVal = engine.getToDate() != null ? engine.getToDate() : LocalDate.now();
                    task.setEndDate(endVal.toString());
                }
                endDatePicker.setValue(endVal);
            }

            boolean optimizerTask = task.getType() == WorkflowTask.TaskType.OPTIMIZER;
            if (dataSettingsHeading != null) {
                dataSettingsHeading.setText(optimizerTask
                        ? "Optimizer Data & Output Settings"
                        : "Backtest Data Settings (Retester)");
            }
            if (optimizerOutputDirectoryRow != null) {
                optimizerOutputDirectoryRow.setVisible(optimizerTask);
                optimizerOutputDirectoryRow.setManaged(optimizerTask);
            }
            if (optimizerOutputDirectoryField != null) {
                optimizerOutputDirectoryField.setText(effectiveOptimizerOutputDirectory(task));
            }
            if (optimizerAlgorithmCombo != null && optimizerTask) {
                updateOptimizerControls(task);
            }

            // Dynamically display only sub-tabs relevant to this task type
            fullSettingsSubTabPane.getTabs().clear();

            switch (task.getType()) {
                case STRATEGY_SELECTION:
                    if (strategySelectionTab != null) fullSettingsSubTabPane.getTabs().add(strategySelectionTab);
                    break;
                case OPTIMIZER:
                    if (optimizerSettingsTab != null) fullSettingsSubTabPane.getTabs().add(optimizerSettingsTab);
                    if (dataSubTab != null) fullSettingsSubTabPane.getTabs().add(dataSubTab);
                    if (rankingSubTab != null) fullSettingsSubTabPane.getTabs().add(rankingSubTab);
                    if (retestSubTab != null) fullSettingsSubTabPane.getTabs().add(retestSubTab);
                    break;
                case RETESTER:
                    if (retestSubTab != null) fullSettingsSubTabPane.getTabs().add(retestSubTab);
                    if (dataSubTab != null) fullSettingsSubTabPane.getTabs().add(dataSubTab);
                    if (rankingSubTab != null) fullSettingsSubTabPane.getTabs().add(rankingSubTab);
                    break;
                case DIVERSITY_FILTER:
                    if (diversitySubTab != null) fullSettingsSubTabPane.getTabs().add(diversitySubTab);
                    if (retestSubTab != null) fullSettingsSubTabPane.getTabs().add(retestSubTab);
                    if (rankingSubTab != null) fullSettingsSubTabPane.getTabs().add(rankingSubTab);
                    break;
                case ROBUSTNESS_CV:
                    if (dataSubTab != null) fullSettingsSubTabPane.getTabs().add(dataSubTab);
                    if (robustnessSubTab != null) fullSettingsSubTabPane.getTabs().add(robustnessSubTab);
                    if (robustnessParamsSubTab != null) fullSettingsSubTabPane.getTabs().add(robustnessParamsSubTab);
                    if (retestSubTab != null) fullSettingsSubTabPane.getTabs().add(retestSubTab);
                    if (rankingSubTab != null) fullSettingsSubTabPane.getTabs().add(rankingSubTab);
                    updateRobustnessParamsTable();
                    break;
                case PRE_FILTER:
                    if (rankingSubTab != null) fullSettingsSubTabPane.getTabs().add(rankingSubTab);
                    if (retestSubTab != null) fullSettingsSubTabPane.getTabs().add(retestSubTab);
                    break;
                default:
                    if (retestSubTab != null) fullSettingsSubTabPane.getTabs().add(retestSubTab);
                    if (rankingSubTab != null) fullSettingsSubTabPane.getTabs().add(rankingSubTab);
                    break;
            }

            if (!fullSettingsSubTabPane.getTabs().isEmpty()) {
                fullSettingsSubTabPane.getSelectionModel().select(0);
            }
        } else {
            currentTaskSettingsHeader.setText("No task selected");
            if (taskNameField != null) {
                taskNameField.clear();
                taskNameField.setDisable(true);
            }
            fullSettingsSubTabPane.getTabs().clear();
        }
    }

    private void applySelectedTaskName() {
        if (selectedTask == null || taskNameField == null) return;
        String requestedName = taskNameField.getText() != null ? taskNameField.getText().trim() : "";
        if (requestedName.isEmpty()) {
            taskNameField.setText(selectedTask.getName());
            return;
        }
        if (requestedName.equals(selectedTask.getName())) return;

        selectedTask.setName(requestedName);
        currentTaskSettingsHeader.setText("Advanced settings for '" + requestedName + "'");
        saveProject();
        refreshTaskChain();
    }

    private static LocalDate parseDateOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String effectiveOptimizerOutputDirectory(WorkflowTask task) {
        String configured = task != null ? task.getOptimizerOutputDirectory() : "";
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return AppConfig.getInstance().getReportsDirectory().toAbsolutePath().normalize().toString();
    }

    private void recalculateForwardDate() {
        if (updatingOptimizerControls || selectedTask == null
                || selectedTask.getType() != WorkflowTask.TaskType.OPTIMIZER) return;
        if (startDatePicker == null || endDatePicker == null || optimizerForwardModeCombo == null || optimizerForwardDatePicker == null) return;

        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();
        int forwardMode = optimizerForwardModeCombo.getSelectionModel().getSelectedIndex();

        boolean isCustom = (forwardMode == 4);
        optimizerForwardDatePicker.setDisable(!isCustom);

        if (forwardMode <= 0) {
            updatingOptimizerControls = true;
            try {
                optimizerForwardDatePicker.setValue(null);
            } finally {
                updatingOptimizerControls = false;
            }
            selectedTask.setOptimizerForwardDate("");
            engine.setForwardDate(null);
            return;
        }

        if (start == null || end == null || !end.isAfter(start) || isCustom) {
            return;
        }

        long totalDays = java.time.temporal.ChronoUnit.DAYS.between(start, end);
        if (totalDays <= 0) return;

        LocalDate newForwardDate = null;
        if (forwardMode == 1) { // 1/2 period (50% in-sample, 50% forward)
            newForwardDate = start.plusDays(totalDays / 2);
        } else if (forwardMode == 2) { // 1/3 period (66.7% in-sample, 33.3% forward)
            newForwardDate = start.plusDays((totalDays * 2) / 3);
        } else if (forwardMode == 3) { // 1/4 period (75% in-sample, 25% forward)
            newForwardDate = start.plusDays((totalDays * 3) / 4);
        }

        if (newForwardDate != null) {
            updatingOptimizerControls = true;
            try {
                optimizerForwardDatePicker.setValue(newForwardDate);
            } finally {
                updatingOptimizerControls = false;
            }
            if (selectedTask != null) {
                selectedTask.setOptimizerForwardDate(newForwardDate.toString());
            }
            engine.setForwardDate(newForwardDate);
        }
    }

    private void saveSelectedOptimizerSettings() {
        if (updatingOptimizerControls || selectedTask == null
                || selectedTask.getType() != WorkflowTask.TaskType.OPTIMIZER) return;
        int algorithmIndex = optimizerAlgorithmCombo.getSelectionModel().getSelectedIndex();
        int criterionIndex = optimizerCriterionCombo.getSelectionModel().getSelectedIndex();
        int forwardIndex = optimizerForwardModeCombo.getSelectionModel().getSelectedIndex();
        if (algorithmIndex < 0 || criterionIndex < 0 || forwardIndex < 0) return;

        recalculateForwardDate();

        int optimizerMode = OptimizationConfig.OPTIMIZATION_MODE_VALUES[algorithmIndex];
        LocalDate forwardDate = forwardIndex > 0 ? optimizerForwardDatePicker.getValue() : null;
        selectedTask.setOptimizerMode(optimizerMode);
        selectedTask.setOptimizerCriterion(criterionIndex);
        selectedTask.setOptimizerForwardMode(forwardIndex);
        selectedTask.setOptimizerForwardDate(forwardDate != null ? forwardDate.toString() : "");

        engine.setOptimizationMode(optimizerMode);
        engine.setOptimizationCriterion(criterionIndex);
        engine.setForwardMode(forwardIndex);
        engine.setForwardDate(forwardDate);
        saveProject();
    }

    private void updateOptimizerControls(WorkflowTask task) {
        if (task == null || task.getType() != WorkflowTask.TaskType.OPTIMIZER) return;
        task.initializeOptimizerSettings(
                engine.getOptimizationMode(), engine.getOptimizationCriterion(),
                engine.getForwardMode(), engine.getForwardDate());

        updatingOptimizerControls = true;
        try {
            optimizerAlgorithmCombo.getSelectionModel().select(task.getOptimizerMode() == 1 ? 0 : 1);
            optimizerCriterionCombo.getSelectionModel().select(task.getOptimizerCriterion());
            optimizerForwardModeCombo.getSelectionModel().select(task.getOptimizerForwardMode());
            if (task.getOptimizerForwardMode() == 4) { // Custom
                LocalDate forwardDate = parseDateOrNull(task.getOptimizerForwardDate());
                optimizerForwardDatePicker.setValue(forwardDate);
            } else if (task.getOptimizerForwardMode() == 0) {
                optimizerForwardDatePicker.setValue(null);
            }
        } finally {
            updatingOptimizerControls = false;
        }
        recalculateForwardDate();
        saveProject();
    }

    private void saveOptimizerOutputDirectory() {
        if (selectedTask == null || selectedTask.getType() != WorkflowTask.TaskType.OPTIMIZER
                || optimizerOutputDirectoryField == null) return;
        String requested = optimizerOutputDirectoryField.getText() != null
                ? optimizerOutputDirectoryField.getText().trim() : "";
        if (requested.isEmpty()) {
            requested = AppConfig.getInstance().getReportsDirectory().toAbsolutePath().normalize().toString();
            optimizerOutputDirectoryField.setText(requested);
        }
        if (!requested.equals(selectedTask.getOptimizerOutputDirectory())) {
            selectedTask.setOptimizerOutputDirectory(requested);
            saveProject();
        }
    }

    private void chooseOptimizerOutputDirectory() {
        if (selectedTask == null || selectedTask.getType() != WorkflowTask.TaskType.OPTIMIZER) return;

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Optimizer-Ausgabeordner wählen");
        Path initialPath;
        try {
            initialPath = Paths.get(effectiveOptimizerOutputDirectory(selectedTask)).toAbsolutePath().normalize();
        } catch (RuntimeException ex) {
            initialPath = AppConfig.getInstance().getReportsDirectory().toAbsolutePath().normalize();
        }
        while (initialPath != null && !Files.isDirectory(initialPath)) {
            initialPath = initialPath.getParent();
        }
        if (initialPath != null) chooser.setInitialDirectory(initialPath.toFile());

        File selectedDirectory = chooser.showDialog(root.getScene() != null ? root.getScene().getWindow() : null);
        if (selectedDirectory == null) return;
        String selectedPath = selectedDirectory.toPath().toAbsolutePath().normalize().toString();
        optimizerOutputDirectoryField.setText(selectedPath);
        selectedTask.setOptimizerOutputDirectory(selectedPath);
        saveProject();
    }

    private Path optimizerOutputBaseDirectory(WorkflowTask task) {
        try {
            return Paths.get(effectiveOptimizerOutputDirectory(task)).toAbsolutePath().normalize();
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Ungültiger Optimizer-Ausgabeordner: "
                    + effectiveOptimizerOutputDirectory(task), ex);
        }
    }

    private long findSensitivityRunTimestamp(WorkflowTask aiTask) {
        return findRunTimestampForTaskSource(aiTask, WorkflowTask.TaskType.ROBUSTNESS_CV);
    }

    private long findSensitivityRunTimestampForDatabank(String databankName) {
        return findRunTimestampForDatabank(databankName, WorkflowTask.TaskType.ROBUSTNESS_CV);
    }

    private long findKiRunTimestampForTask(WorkflowTask targetTask) {
        return findRunTimestampForTaskSource(targetTask, WorkflowTask.TaskType.KI_EVALUATION);
    }

    private long findRunTimestampForTaskSource(WorkflowTask targetTask,
                                               WorkflowTask.TaskType producerType) {
        if (project == null || targetTask == null) return 0L;
        java.util.Map<String, Long> timestampByDatabank = buildRunTimestampLineage(targetTask, producerType);
        return timestampByDatabank.getOrDefault(
                normalizedDatabankName(targetTask.getSourceDatabank()), 0L);
    }

    private long findRunTimestampForDatabank(String databankName,
                                             WorkflowTask.TaskType producerType) {
        if (project == null || databankName == null) return 0L;
        return buildRunTimestampLineage(null, producerType).getOrDefault(
                normalizedDatabankName(databankName), 0L);
    }

    private java.util.Map<String, Long> buildRunTimestampLineage(WorkflowTask stopBefore,
                                                                  WorkflowTask.TaskType producerType) {
        java.util.Map<String, Long> timestampByDatabank = new java.util.HashMap<>();
        for (WorkflowTask task : project.getTasks()) {
            if (task == stopBefore) break;
            if (task == null || !task.isEnabled() || task.getType() == null
                    || task.getType() == WorkflowTask.TaskType.PORTFOLIO_EXPORT
                    || task.getType() == WorkflowTask.TaskType.STRATEGY_SELECTION) {
                continue;
            }

            String sourceKey = normalizedDatabankName(task.getSourceDatabank());
            String targetKey = normalizedDatabankName(task.getTargetDatabank());
            long propagatedTimestamp = task.getType() == WorkflowTask.TaskType.OPTIMIZER
                    ? 0L : timestampByDatabank.getOrDefault(sourceKey, 0L);
            if (task.getType() == producerType) {
                propagatedTimestamp = task.getSensitivityRunTimestamp();
            }
            timestampByDatabank.put(targetKey, propagatedTimestamp);
        }
        return timestampByDatabank;
    }

    /** Applies every task-level override before any runner/config is created. */
    private void applyTaskExecutionConfig(WorkflowTask task) {
        if (task == null || task.getType() == null) {
            throw new IllegalArgumentException("Task-Typ fehlt oder ist ungültig.");
        }

        boolean requiresExpert = (task.getType() == WorkflowTask.TaskType.STRATEGY_SELECTION ||
                                  task.getType() == WorkflowTask.TaskType.OPTIMIZER ||
                                  task.getType() == WorkflowTask.TaskType.RETESTER ||
                                  task.getType() == WorkflowTask.TaskType.ROBUSTNESS_CV);

        if (requiresExpert) {
            String taskExpert = (project != null && project.getExpert() != null && !project.getExpert().isBlank())
                    ? project.getExpert().trim()
                    : engine.getExpert();
            if (taskExpert != null && !taskExpert.isBlank()) {
                engine.setExpert(taskExpert);
            } else if (engine.getExpert() == null || engine.getExpert().isBlank()) {
                throw new IllegalArgumentException("Kein Expert Advisor im Projekt festgelegt. Bitte wähle in Task 1 eine .ex5-Datei aus.");
            }
        }

        engine.setTickModel(task.getMt5Model());
        if (task.getType() == WorkflowTask.TaskType.OPTIMIZER) {
            engine.setOptimizationMode(task.getOptimizerMode());
            engine.setOptimizationCriterion(task.getOptimizerCriterion());
            engine.setForwardMode(task.getOptimizerForwardMode());
            engine.setForwardDate(parseDateOrNull(task.getOptimizerForwardDate()));
        }
        String taskSymbol = task.getRetestSymbol();
        String taskPeriod = task.getRetestPeriod();
        engine.setSymbol(taskSymbol != null && !taskSymbol.isBlank()
                ? taskSymbol.trim() : (project != null ? project.getSymbol() : engine.getSymbol()));
        engine.setPeriod(taskPeriod != null && !taskPeriod.isBlank()
                ? taskPeriod.trim() : (project != null ? project.getPeriod() : engine.getPeriod()));

        if (selectedTask == task) {
            if (startDatePicker != null && startDatePicker.getValue() != null) {
                task.setStartDate(startDatePicker.getValue().toString());
            }
            if (endDatePicker != null && endDatePicker.getValue() != null) {
                task.setEndDate(endDatePicker.getValue().toString());
            }
        }

        String startText = task.getStartDate();
        String endText = task.getEndDate();
        boolean hasStart = startText != null && !startText.isBlank();
        boolean hasEnd = endText != null && !endText.isBlank();

        LocalDate start = null;
        LocalDate end = null;

        if (hasStart) {
            try {
                start = LocalDate.parse(startText.trim());
            } catch (Exception ex) {
                throw new IllegalArgumentException("Ungültiges Startdatum im Task '"
                        + task.getName() + "': " + startText, ex);
            }
        }
        if (hasEnd) {
            try {
                end = LocalDate.parse(endText.trim());
            } catch (Exception ex) {
                throw new IllegalArgumentException("Ungültiges Enddatum im Task '"
                        + task.getName() + "': " + endText, ex);
            }
        }

        // Fallback for missing dates from engine configuration
        if (start == null && end != null) {
            start = engine.getFromDate();
            if (start == null) start = end.minusYears(3);
        } else if (end == null && start != null) {
            end = engine.getToDate();
            if (end == null) end = LocalDate.now();
        } else if (start == null && end == null) {
            start = engine.getFromDate();
            end = engine.getToDate();
        }

        if (start != null && end != null && !start.isBefore(end)) {
            throw new IllegalArgumentException("Der Zeitraum für Task '" + task.getName()
                    + "' ist ungültig: Das Startdatum muss vor dem Enddatum liegen.");
        }

        switch (task.getType()) {
            case OPTIMIZER:
            case ROBUSTNESS_CV:
                if (start != null && end != null) {
                    engine.setFromDate(start);
                    engine.setToDate(end);
                }
                break;
            case RETESTER:
                engine.setLongtermFromDate(start != null ? start : LocalDate.now().minusYears(7));
                engine.setLongtermToDate(end != null ? end : LocalDate.now());
                break;
            default:
                break;
        }
    }

    private List<CombinedPass> exportPortfolioCandidates(WorkflowTask portfolioTask,
                                                         List<CombinedPass> inputPasses) {
        if (inputPasses == null || inputPasses.isEmpty()) {
            throw new IllegalStateException("Keine Strategien für den Portfolio-Export vorhanden.");
        }
        long requiredKiRunTimestamp = findKiRunTimestampForTask(portfolioTask);
        if (requiredKiRunTimestamp <= 0L) {
            throw new IllegalStateException("Portfolio-Export abgebrochen: Es wurde kein erfolgreiches, "
                    + "zur Quell-Databank passendes KI-Ergebnis gefunden.");
        }
        // Custom-project OOS checks are Retester tasks, not the legacy Step-7
        // validation state. Always run the current candidates through the KI
        // selection so stale global validation results cannot bypass it.
        List<CombinedPass> exportPasses = engine.selectFinalPasses(inputPasses, requiredKiRunTimestamp);
        engine.exportPortfolio(AppConfig.getInstance().getExportDirectory().toString());
        engine.saveWorkflowToHistory();
        return exportPasses;
    }

    private static boolean taskRequiresInputStrategies(WorkflowTask.TaskType type) {
        return type != WorkflowTask.TaskType.STRATEGY_SELECTION
                && type != WorkflowTask.TaskType.OPTIMIZER;
    }

    private void requireTaskInputStrategies(WorkflowTask task, List<CombinedPass> inputPasses) {
        if (taskRequiresInputStrategies(task.getType())
                && (inputPasses == null || inputPasses.isEmpty())) {
            throw new IllegalStateException("Task '" + task.getName() + "' kann nicht starten: Die Quell-Databank '"
                    + task.getSourceDatabank() + "' enthält keine Strategien.");
        }
    }

    // ─── Execution Logic ──────────────────────────────────────────────────────

    private void runSingleTask(WorkflowTask task) {
        if (task == null) return;

        commitCurrentTaskDataSettings();
        if (!confirmRetesterConfigurationWarnings(task)) return;
        saveProject();

        startBtn.setDisable(true);
        stopBtn.setDisable(false);
        resetBtn.setDisable(true);
        setEditorLocked(true);
        consoleLog.clear();

        centerMainTabPane.getSelectionModel().select(progressTab);

        logToConsole("SINGLE-STEP", "=== STARTE EINZELTEST FÜR TASK: " + task.getName() + " ===");

        activeProjectTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                if (!projectSaveCoordinator.flush(PROJECT_SAVE_FLUSH_TIMEOUT)) {
                    throw new IllegalStateException("Projekt konnte vor dem Einzeltest nicht gespeichert werden.");
                }
                task.setStatus(WorkflowTask.TaskStatus.RUNNING);
                Platform.runLater(() -> {
                    refreshTaskChain();
                    progressBar.setProgress(0.5);
                    progressLabel.setText("Führe Einzelstep aus: " + task.getName());
                });

                List<CombinedPass> inputPasses = databankManager.getDatabank(task.getSourceDatabank());
                List<CombinedPass> outputPasses = new ArrayList<>();
                requireTaskInputStrategies(task, inputPasses);
                applyTaskExecutionConfig(task);

                switch (task.getType()) {
                    case STRATEGY_SELECTION:
                        engine.runStep1();
                        logToConsole("STRATEGIE-SELEKTION", "Strategie " + engine.getExpert() + " (" + engine.getSymbol() + " " + engine.getPeriod() + ") initialisiert.");
                        outputPasses = new ArrayList<>(inputPasses);
                        break;
                    case OPTIMIZER:
                        engine.runStep1();
                        engine.runStep2(
                            msg -> logToConsole("MT5-OPT", msg),
                            (curr, totPasses) -> updateProgressUI((double) curr / Math.max(1, totPasses), "Optimizer Pass " + curr + " / " + totPasses),
                            optimizerOutputBaseDirectory(task)
                        );
                        if (engine.getOptResult() != null) {
                            outputPasses = engine.getOptResult().buildCombinedPasses(engine.getForwardMode() > 0, engine.loadScoreWeightsFromDb());
                            String modelName = com.backtester.engine.OptimizationConfig.MODEL_NAMES[task.getMt5Model()];
                            if (outputPasses != null) {
                                for (CombinedPass cp : outputPasses) {
                                    if (cp.getBacktestPass() != null) {
                                        cp.getBacktestPass().setTickModel(modelName);
                                    }
                                    if (cp.getForwardPass() != null) {
                                        cp.getForwardPass().setTickModel(modelName);
                                    }
                                }
                            }
                        }
                        break;
                    case RETESTER:
                        long retStartMs = System.currentTimeMillis();
                        int retTotal = inputPasses != null ? inputPasses.size() : 1;
                        outputPasses = engine.runLongtermTest(
                            inputPasses,
                            task,
                            msg -> logToConsole("RETESTER", msg),
                            pct -> {
                                int curr = Math.min(retTotal, Math.max(0, (int) Math.round(((double) pct / 100.0) * retTotal)));
                                updateProgressUI((double) pct / 100.0, formatProgressWithEta(task.getName(), curr, retTotal, retStartMs));
                            }
                        );
                        break;
                    case PRE_FILTER:
                        outputPasses = new ArrayList<>(inputPasses);
                        break;
                    case DIVERSITY_FILTER:
                        outputPasses = engine.clusterDatabankPasses(
                                inputPasses,
                                task.getDiversityParamDiffPct(),
                                task.getDiversityTradeDiffPct(),
                                task.getDiversityMinDifferentParams(),
                                task.getDiversityMaxStrategies());
                        break;
                    case ROBUSTNESS_CV:
                        engine.setSelectedDiversePasses(inputPasses);
                        task.setSensitivityRunTimestamp(0L);
                        long robStartMs = System.currentTimeMillis();
                        int robTotal = inputPasses != null ? inputPasses.size() : 1;
                        updateProgressUI(0.0, formatProgressWithEta("Robustness Test", 0, robTotal, robStartMs));
                        engine.runStep4(
                            msg -> logToConsole("STRESS", msg),
                            pct -> {
                                int curr = Math.min(robTotal, Math.max(0, (int) Math.round(((double) pct / 100.0) * robTotal)));
                                updateProgressUI((double) pct / 100.0, formatProgressWithEta("Robustness Test", curr, robTotal, robStartMs));
                            },
                            task
                        );
                        task.setSensitivityRunTimestamp(engine.getSensitivityRunTimestamp());
                        outputPasses = new ArrayList<>(inputPasses);
                        break;
                    case KI_EVALUATION:
                        long kiRunTimestamp = findSensitivityRunTimestamp(task);
                        engine.setSensitivityRunTimestamp(kiRunTimestamp);
                        task.setSensitivityRunTimestamp(kiRunTimestamp);
                        engine.setSelectedDiversePasses(inputPasses);
                        engine.retainSensitivityResultsForPasses(inputPasses);
                        engine.runStep5(msg -> logToConsole("KI-EVAL", msg));
                        outputPasses = new ArrayList<>(inputPasses);
                        break;
                    case PORTFOLIO_EXPORT:
                        outputPasses = exportPortfolioCandidates(task,
                                databankManager.filterPasses(task, inputPasses));
                        break;
                    default:
                        outputPasses = new ArrayList<>(inputPasses);
                        break;
                }

                List<CombinedPass> processed = databankManager.processTaskDatabanks(task, outputPasses);
                task.setOutputPasses(processed);
                task.setStatus(WorkflowTask.TaskStatus.COMPLETED);

                logToConsole("SINGLE-STEP", "=== EINZELSTEP ERFOLGREICH BEENDET. Databank '" + task.getTargetDatabank() + "' enthält " + processed.size() + " Strategien ===");
                updateProgressUI(1.0, "Einzelstep beendet.");
                return null;
            }

            @Override
            protected void succeeded() { cleanupExecutionState(); }
            @Override
            protected void failed() {
                task.setStatus(WorkflowTask.TaskStatus.FAILED);
                Throwable error = getException();
                String message = error != null && error.getMessage() != null ? error.getMessage() : "Unbekannter Fehler";
                task.setLastExecutionLog(message);
                logger.error("Task '" + task.getName() + "' fehlgeschlagen", error);
                logToConsole("ERROR", "Task '" + task.getName() + "' fehlgeschlagen: " + message);
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Task '" + task.getName() + "' fehlgeschlagen:\n\n" + message, ButtonType.OK);
                    alert.setTitle("Task-Fehler");
                    alert.setHeaderText("Fehler bei Task-Ausführung");
                    if (root.getScene() != null) alert.initOwner(root.getScene().getWindow());
                    alert.showAndWait();
                });
                cleanupExecutionState();
            }
            @Override
            protected void cancelled() {
                task.setStatus(WorkflowTask.TaskStatus.PENDING);
                task.setLastExecutionLog("Vom Benutzer abgebrochen.");
                cleanupExecutionState();
            }
        };

        Thread t = new Thread(activeProjectTask);
        t.setDaemon(true);
        t.start();
    }

    private void startProjectExecution() {
        if (project == null || project.getTasks().isEmpty()) return;
        commitCurrentTaskDataSettings();
        try {
            validateProjectExecutionOrder();
        } catch (IllegalStateException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK);
            alert.setTitle("Workflow-Konfiguration ungültig");
            alert.setHeaderText("Projekt kann nicht sicher gestartet werden");
            if (root.getScene() != null) alert.initOwner(root.getScene().getWindow());
            alert.showAndWait();
            return;
        }
        if (!confirmRetesterConfigurationWarnings(null)) return;

        saveProject();

        startBtn.setDisable(true);
        stopBtn.setDisable(false);
        resetBtn.setDisable(true);
        setEditorLocked(true);
        consoleLog.clear();

        centerMainTabPane.getSelectionModel().select(progressTab);

        logToConsole("PROJECT", "=== STARTE CUSTOM PROJECT: " + project.getName() + " ===");

        activeProjectTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                if (!projectSaveCoordinator.flush(PROJECT_SAVE_FLUSH_TIMEOUT)) {
                    throw new IllegalStateException("Projekt konnte vor dem Workflow-Start nicht gespeichert werden.");
                }
                List<WorkflowTask> tasks = project.getTasks();
                int total = tasks.size();
                List<CombinedPass> currentPipelinePasses = new ArrayList<>();

                for (int i = 0; i < total; i++) {
                    WorkflowTask task = tasks.get(i);
                    if (!task.isEnabled()) {
                        task.setStatus(WorkflowTask.TaskStatus.DISABLED);
                        logToConsole("PROJECT", "Überspringe deaktivierten Task " + (i + 1) + ": " + task.getName());
                        continue;
                    }

                    task.setStatus(WorkflowTask.TaskStatus.RUNNING);
                    final int currentIdx = i;
                    Platform.runLater(() -> {
                        refreshTaskChain();
                        progressBar.setProgress((double) currentIdx / total);
                        progressLabel.setText("Führe Task " + (currentIdx + 1) + " von " + total + " aus: " + task.getName());
                    });

                    logToConsole("PROJECT", "=== STARTE TASK " + (i + 1) + ": " + task.getName() +
                        " [Source: " + task.getSourceDatabank() + " -> Target: " + task.getTargetDatabank() + "] ===");

                    try {
                        List<CombinedPass> inputPasses = databankManager.getDatabank(task.getSourceDatabank());
                        currentPipelinePasses = new ArrayList<>(inputPasses);
                        requireTaskInputStrategies(task, inputPasses);
                        applyTaskExecutionConfig(task);

                        switch (task.getType()) {
                            case STRATEGY_SELECTION:
                                engine.runStep1();
                                logToConsole("STRATEGIE-SELEKTION", "Strategie " + engine.getExpert() + " (" + engine.getSymbol() + " " + engine.getPeriod() + ") initialisiert.");
                                break;
                            case OPTIMIZER:
                                engine.runStep1();
                                engine.runStep2(
                                    msg -> logToConsole("MT5-OPT", msg),
                                    (curr, totPasses) -> updateProgressUI((double) currentIdx / total, "Optimizer Pass " + curr + " / " + totPasses),
                                    optimizerOutputBaseDirectory(task)
                                );
                                if (engine.getOptResult() != null) {
                                    currentPipelinePasses = engine.getOptResult().buildCombinedPasses(engine.getForwardMode() > 0, engine.loadScoreWeightsFromDb());
                                }
                                break;
                            case RETESTER:
                                long loopRetStartMs = System.currentTimeMillis();
                                int loopRetTotal = inputPasses != null ? inputPasses.size() : 1;
                                currentPipelinePasses = engine.runLongtermTest(
                                    inputPasses,
                                    task,
                                    msg -> logToConsole("RETESTER", msg),
                                    pct -> {
                                        int curr = Math.min(loopRetTotal, Math.max(0, (int) Math.round(((double) pct / 100.0) * loopRetTotal)));
                                        double overallProgress = ((double) currentIdx + ((double) pct / 100.0)) / total;
                                        updateProgressUI(overallProgress, formatProgressWithEta(task.getName(), curr, loopRetTotal, loopRetStartMs));
                                    }
                                );
                                break;
                            case PRE_FILTER:
                                currentPipelinePasses = new ArrayList<>(inputPasses);
                                break;
                            case DIVERSITY_FILTER:
                                currentPipelinePasses = engine.clusterDatabankPasses(
                                        inputPasses,
                                        task.getDiversityParamDiffPct(),
                                        task.getDiversityTradeDiffPct(),
                                        task.getDiversityMinDifferentParams(),
                                        task.getDiversityMaxStrategies());
                                break;
                            case ROBUSTNESS_CV:
                                engine.setSelectedDiversePasses(inputPasses);
                                task.setSensitivityRunTimestamp(0L);
                                long loopRobStartMs = System.currentTimeMillis();
                                int loopRobTotal = inputPasses != null ? inputPasses.size() : 1;
                                updateProgressUI((double) currentIdx / total, formatProgressWithEta("Robustness Test", 0, loopRobTotal, loopRobStartMs));
                                engine.runStep4(
                                    msg -> logToConsole("STRESS", msg),
                                    pct -> {
                                        int curr = Math.min(loopRobTotal, Math.max(0, (int) Math.round(((double) pct / 100.0) * loopRobTotal)));
                                        double overallProgress = ((double) currentIdx + ((double) pct / 100.0)) / total;
                                        updateProgressUI(overallProgress, formatProgressWithEta("Robustness Test", curr, loopRobTotal, loopRobStartMs));
                                    },
                                    task
                                );
                                task.setSensitivityRunTimestamp(engine.getSensitivityRunTimestamp());
                                currentPipelinePasses = new ArrayList<>(inputPasses);
                                break;
                            case KI_EVALUATION:
                                long kiRunTimestamp = findSensitivityRunTimestamp(task);
                                engine.setSensitivityRunTimestamp(kiRunTimestamp);
                                task.setSensitivityRunTimestamp(kiRunTimestamp);
                                engine.setSelectedDiversePasses(inputPasses);
                                engine.retainSensitivityResultsForPasses(inputPasses);
                                engine.runStep5(msg -> logToConsole("KI-EVAL", msg));
                                currentPipelinePasses = new ArrayList<>(inputPasses);
                                break;
                            case PORTFOLIO_EXPORT:
                                currentPipelinePasses = exportPortfolioCandidates(task,
                                        databankManager.filterPasses(task, inputPasses));
                                break;
                            default:
                                break;
                        }

                        List<CombinedPass> processed = databankManager.processTaskDatabanks(task, currentPipelinePasses);
                        task.setOutputPasses(processed);
                        task.setStatus(WorkflowTask.TaskStatus.COMPLETED);
                        logToConsole("PROJECT", "Task " + (i + 1) + " (" + task.getName() + ") erfolgreich beendet. Databank '" + task.getTargetDatabank() + "' hat nun " + processed.size() + " Strategien.");
                    } catch (Exception taskEx) {
                        task.setStatus(WorkflowTask.TaskStatus.FAILED);
                        String errMsg = taskEx.getMessage() != null ? taskEx.getMessage() : taskEx.getClass().getSimpleName();
                        task.setLastExecutionLog(errMsg);
                        logger.error("Fehler bei Ausfuehrung von Task " + (i + 1) + " (" + task.getName() + ")", taskEx);
                        logToConsole("ERROR", "Task " + (i + 1) + " (" + task.getName() + ") fehlgeschlagen: " + errMsg);
                        throw taskEx;
                    }

                    if (isCancelled()) return null;
                }

                project.setLastRunTimestamp(System.currentTimeMillis());
                saveProject();
                updateProgressUI(1.0, "Projekt erfolgreich abgeschlossen!");
                logToConsole("PROJECT", "=== CUSTOM PROJECT ERFOLGREICH BEENDET ===");
                return null;
            }

            @Override
            protected void succeeded() {
                cleanupExecutionState();
            }

            @Override
            protected void failed() {
                Throwable error = getException();
                String message = error != null && error.getMessage() != null ? error.getMessage() : "Unbekannter Fehler";
                logger.error("Projektlauf fehlgeschlagen", error);
                logToConsole("ERROR", "Projektlauf fehlgeschlagen: " + message);
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Projektlauf fehlgeschlagen:\n\n" + message, ButtonType.OK);
                    alert.setTitle("Workflow-Fehler");
                    alert.setHeaderText("Fehler bei Workflow-Ausführung");
                    if (root.getScene() != null) alert.initOwner(root.getScene().getWindow());
                    alert.showAndWait();
                });
                cleanupExecutionState();
            }

            @Override
            protected void cancelled() {
                if (project != null) {
                    for (WorkflowTask task : project.getTasks()) {
                        if (task != null && task.getStatus() == WorkflowTask.TaskStatus.RUNNING) {
                            task.setStatus(WorkflowTask.TaskStatus.PENDING);
                            task.setLastExecutionLog("Vom Benutzer abgebrochen.");
                        }
                    }
                }
                cleanupExecutionState();
            }
        };

        Thread t = new Thread(activeProjectTask);
        t.setDaemon(true);
        t.start();
    }

    private void validateProjectExecutionOrder() {
        java.util.Map<String, Set<String>> robustnessLineageByDatabank = new java.util.HashMap<>();
        java.util.Map<String, Set<String>> kiLineageByDatabank = new java.util.HashMap<>();
        for (WorkflowTask task : project.getTasks()) {
            if (task == null || !task.isEnabled()) continue;
            if (task.getType() == null) {
                throw new IllegalStateException("Ein aktivierter Task besitzt keinen gültigen Typ.");
            }
            if (!databankManager.hasDatabank(task.getSourceDatabank())) {
                throw new IllegalStateException("Task '" + task.getName() + "' verweist auf die nicht vorhandene Quell-Databank '"
                        + task.getSourceDatabank() + "'.");
            }
            if (!databankManager.hasDatabank(task.getTargetDatabank())) {
                throw new IllegalStateException("Task '" + task.getName() + "' verweist auf die nicht vorhandene Ziel-Databank '"
                        + task.getTargetDatabank() + "'.");
            }
            String sourceKey = normalizedDatabankName(task.getSourceDatabank());
            String targetKey = normalizedDatabankName(task.getTargetDatabank());
            Set<String> sourceKiLineage = new java.util.HashSet<>(
                    kiLineageByDatabank.getOrDefault(sourceKey, Set.of()));
            Set<String> sourceRobustnessLineage = new java.util.HashSet<>(
                    robustnessLineageByDatabank.getOrDefault(sourceKey, Set.of()));
            if (task.getType() == WorkflowTask.TaskType.PORTFOLIO_EXPORT && sourceKiLineage.isEmpty()) {
                throw new IllegalStateException("Task '" + task.getName()
                        + "' benötigt davor einen aktivierten KI-Bewertungs-Task, dessen Ergebnisse bis zur Quell-Databank '"
                        + task.getSourceDatabank() + "' weitergereicht werden.");
            }
            if (task.getType() == WorkflowTask.TaskType.OPTIMIZER
                    || task.getType() == WorkflowTask.TaskType.RETESTER
                    || task.getType() == WorkflowTask.TaskType.ROBUSTNESS_CV) {
                LocalDate start = parseDateOrNull(task.getStartDate());
                LocalDate end = parseDateOrNull(task.getEndDate());
                if (start == null || end == null || !start.isBefore(end)) {
                    throw new IllegalStateException("Task '" + task.getName()
                            + "' besitzt keinen gültigen Zeitraum.");
                }
            }
            if (task.getType() == WorkflowTask.TaskType.OPTIMIZER
                    && task.getOptimizerForwardMode() == 4) {
                LocalDate forwardDate = parseDateOrNull(task.getOptimizerForwardDate());
                LocalDate start = parseDateOrNull(task.getStartDate());
                LocalDate end = parseDateOrNull(task.getEndDate());
                if (forwardDate == null || !forwardDate.isAfter(start) || !forwardDate.isBefore(end)) {
                    throw new IllegalStateException("Task '" + task.getName()
                            + "' besitzt kein gültiges benutzerdefiniertes Forward-Datum.");
                }
            }
            if (task.getType() == WorkflowTask.TaskType.KI_EVALUATION
                    && sourceRobustnessLineage.isEmpty()) {
                throw new IllegalStateException("Task '" + task.getName()
                        + "' benötigt davor einen aktivierten Robustness-Task, dessen Ergebnisse bis zur Quell-Databank '"
                        + task.getSourceDatabank() + "' weitergereicht werden.");
            }
            if (task.getType() != WorkflowTask.TaskType.PORTFOLIO_EXPORT
                    && task.getType() != WorkflowTask.TaskType.STRATEGY_SELECTION) {
                Set<String> targetKiLineage = task.isDeleteFailed()
                        ? new java.util.HashSet<>()
                        : new java.util.HashSet<>(kiLineageByDatabank.getOrDefault(targetKey, Set.of()));
                if (task.getType() != WorkflowTask.TaskType.OPTIMIZER) {
                    targetKiLineage.addAll(sourceKiLineage);
                }
                if (task.getType() == WorkflowTask.TaskType.KI_EVALUATION) {
                    targetKiLineage.add(task.getName());
                }
                kiLineageByDatabank.put(targetKey, targetKiLineage);

                Set<String> targetRobustnessLineage = task.isDeleteFailed()
                        ? new java.util.HashSet<>()
                        : new java.util.HashSet<>(robustnessLineageByDatabank.getOrDefault(targetKey, Set.of()));
                if (task.getType() != WorkflowTask.TaskType.OPTIMIZER) {
                    targetRobustnessLineage.addAll(sourceRobustnessLineage);
                }
                if (task.getType() == WorkflowTask.TaskType.ROBUSTNESS_CV) {
                    targetRobustnessLineage.add(task.getName());
                }
                robustnessLineageByDatabank.put(targetKey, targetRobustnessLineage);
            }
        }
    }

    private boolean confirmRetesterConfigurationWarnings(WorkflowTask onlyTask) {
        if (project == null) return true;
        List<RetesterOverwriteRisk> risks = WorkflowConfigurationValidator
                .findRetesterOverwriteRisks(project.getTasks());
        if (onlyTask != null) {
            risks.removeIf(risk -> risk.task() != onlyTask);
        }
        if (risks.isEmpty()) return true;

        StringBuilder details = new StringBuilder();
        for (RetesterOverwriteRisk risk : risks) {
            if (details.length() > 0) details.append("\n\n");
            details.append("• Retester '").append(risk.task().getName())
                    .append("' liest aus '").append(risk.sourceDatabank()).append("'.\n")
                    .append("  Diese Daten enthalten bereits ein Retest-Ergebnis von: ")
                    .append(String.join(", ", risk.upstreamRetesterNames())).append(".");
        }
        details.append("\n\nEine Strategie kann im weitergegebenen Datensatz derzeit nur ein Retest-Ergebnis tragen. Der spätere Retester "
                + "ersetzt dieses Ergebnis in seiner Ausgabe. Verwende für unabhängige Retests eine gemeinsame "
                + "Ausgangs-Databank vor dem ersten Retester und getrennte Ziel-Databanken, sodass kein Retester "
                + "die Ausgabe eines anderen Retesters als Quelle liest.");

        ButtonType configureButton = new ButtonType("Zur Konfiguration", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType continueButton = new ButtonType("Trotzdem starten", ButtonBar.ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.WARNING, details.toString(), configureButton, continueButton);
        alert.setTitle("Retester-Konfiguration prüfen");
        alert.setHeaderText("Mehrere Retester verwenden denselben Ergebnis-Pfad");
        if (root.getScene() != null) alert.initOwner(root.getScene().getWindow());
        return alert.showAndWait().orElse(configureButton) == continueButton;
    }

    private static String normalizedDatabankName(String name) {
        String cleanName = name == null || name.isBlank() ? DatabankManager.RESULTS : name.trim();
        return cleanName.toLowerCase(Locale.ROOT);
    }

    private void stopProjectExecution() {
        if (activeProjectTask != null) {
            activeProjectTask.cancel();
        }
        engine.cancel();
    }

    private void resetProjectExecution() {
        if (project != null && project.getTasks() != null) {
            for (WorkflowTask t : project.getTasks()) {
                t.setStatus(WorkflowTask.TaskStatus.PENDING);
                t.getOutputPasses().clear();
            }
            databankManager.clearAll();
            flushProjectSaveAsync(() -> {
                refreshTaskChain();
                refreshDatabanksUI();
            });
        }
        progressBar.setProgress(0);
        progressLabel.setText("Zurückgesetzt.");
        consoleLog.clear();
    }

    private void cleanupExecutionState() {
        saveProject();
        Platform.runLater(() -> {
            startBtn.setDisable(false);
            stopBtn.setDisable(true);
            resetBtn.setDisable(false);
            setEditorLocked(false);
            activeProjectTask = null;
            refreshTaskChain();
            String focusDb = selectedTask != null ? selectedTask.getTargetDatabank() : null;
            refreshDatabanksUI(focusDb);
        });
    }

    private void setEditorLocked(boolean locked) {
        Runnable updateUI = () -> {
            if (fullSettingsTab != null) fullSettingsTab.setDisable(locked);
            if (resultsTab != null) resultsTab.setDisable(locked);
            if (taskChainListBox != null) taskChainListBox.setDisable(locked);
            if (bottomDatabankTabPane != null) bottomDatabankTabPane.setDisable(locked);
            if (databankToolbar != null) databankToolbar.setDisable(locked);
        };
        if (Platform.isFxApplicationThread()) {
            updateUI.run();
        } else {
            Platform.runLater(updateUI);
        }
    }

    private static void commitDatePicker(DatePicker datePicker) {
        if (datePicker == null || datePicker.getEditor() == null) return;
        String text = datePicker.getEditor().getText();
        if (text != null && !text.isBlank()) {
            try {
                LocalDate parsed = datePicker.getConverter().fromString(text.trim());
                if (parsed != null && !parsed.equals(datePicker.getValue())) {
                    datePicker.setValue(parsed);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void commitCurrentTaskDataSettings() {
        if (selectedTask == null) return;
        commitDatePicker(startDatePicker);
        commitDatePicker(endDatePicker);
        commitDatePicker(optimizerForwardDatePicker);

        if (startDatePicker != null && startDatePicker.getValue() != null) {
            selectedTask.setStartDate(startDatePicker.getValue().toString());
        }
        if (endDatePicker != null && endDatePicker.getValue() != null) {
            selectedTask.setEndDate(endDatePicker.getValue().toString());
        }
        if (selectedTask.getType() == WorkflowTask.TaskType.OPTIMIZER) {
            if (optimizerForwardDatePicker != null) {
                LocalDate forwardDate = optimizerForwardDatePicker.getValue();
                selectedTask.setOptimizerForwardDate(forwardDate != null ? forwardDate.toString() : "");
            }
            recalculateForwardDate();
        }
    }

    private void saveProject() {
        if (project != null) {
            projectSaveCoordinator.requestSave(project, databankManager);
        }
    }

    private void backupProject() {
        if (project == null) return;
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Projekt-Backup speichern");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Dateien", "*.json"));
        fileChooser.setInitialFileName(project.getName().replaceAll("[^a-zA-Z0-9_.-]", "_") + "_backup.json");
        File file = fileChooser.showSaveDialog(root.getScene().getWindow());
        if (file != null) {
            try {
                // Save current memory state to a temporary snapshot
                CustomProject snapshot = project.copyMetadataForPersistence();
                databankManager.saveToProject(snapshot, true);

                com.google.gson.Gson gson = DatabaseManager.createCustomProjectGson();
                String json = gson.toJson(snapshot);
                Files.writeString(file.toPath(), json, StandardCharsets.UTF_8);

                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Backup erfolgreich gespeichert!");
                alert.show();
            } catch (Exception ex) {
                logger.error("Fehler beim Backup", ex);
                Alert alert = new Alert(Alert.AlertType.ERROR, "Fehler beim Backup: " + ex.getMessage());
                alert.show();
            }
        }
    }

    private void restoreProject() {
        if (project == null) return;
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Projekt-Backup laden");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Dateien", "*.json"));
        File file = fileChooser.showOpenDialog(root.getScene().getWindow());
        if (file != null) {
            try {
                String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                com.google.gson.Gson gson = DatabaseManager.createCustomProjectGson();
                CustomProject loaded = gson.fromJson(json, CustomProject.class);

                if (loaded != null) {
                    loadProject(loaded);
                    saveProject();
                    projectSaveCoordinator.flushAsync().whenComplete((saved, error) -> Platform.runLater(() -> {
                        if (error != null || !Boolean.TRUE.equals(saved)) {
                            logger.error("Wiederhergestelltes Projekt konnte nicht gespeichert werden", error);
                            new Alert(Alert.AlertType.ERROR,
                                    "Das Backup wurde geladen, konnte aber nicht in SQLite gespeichert werden.",
                                    ButtonType.OK).show();
                        } else {
                            new Alert(Alert.AlertType.INFORMATION,
                                    "Projekt, Tasks, Einstellungen und Databanken wurden wiederhergestellt.",
                                    ButtonType.OK).show();
                        }
                    }));
                }
            } catch (Exception ex) {
                logger.error("Fehler beim Restore", ex);
                Alert alert = new Alert(Alert.AlertType.ERROR, "Fehler beim Restore: " + ex.getMessage());
                alert.show();
            }
        }
    }

    private void flushProjectSaveAsync(Runnable continuation) {
        saveProject();
        projectSaveCoordinator.flushAsync().whenComplete((saved, error) -> Platform.runLater(() -> {
            if (error != null || !Boolean.TRUE.equals(saved)) {
                logToConsole("DB", "Projekt konnte nicht vollstaendig gespeichert werden.");
            }
            if (continuation != null) continuation.run();
        }));
    }

    /** Flushes pending project data before the application shuts down. */
    public void shutdown() {
        commitCurrentTaskDataSettings();
        saveProject();
        if (!projectSaveCoordinator.flush(PROJECT_SAVE_FLUSH_TIMEOUT)) {
            logger.warn("Pending Custom Project data could not be flushed during shutdown");
        }
        projectSaveCoordinator.close();
    }

    private void logToConsole(String tag, String msg) {
        logger.info("[{}] {}", tag, msg);
        Platform.runLater(() -> {
            if (consoleLog != null) {
                consoleLog.appendText("[" + tag + "] " + msg + "\n");
            }
        });
    }

    private String formatProgressWithEta(String taskName, int current, int total, long startTimeMs) {
        if (total <= 0) return taskName + " (" + current + "%)";
        int pct = (int) Math.min(100, Math.max(0, Math.round(((double) current / total) * 100.0)));
        StringBuilder sb = new StringBuilder();
        sb.append(taskName).append(": Strategie ").append(current).append(" / ").append(total).append(" (").append(pct).append("%)");

        if (current > 0 && startTimeMs > 0) {
            long elapsedMs = System.currentTimeMillis() - startTimeMs;
            double avgMsPerStrategy = (double) elapsedMs / current;
            int remainingStrategies = total - current;
            long remainingSec = Math.max(0, Math.round((remainingStrategies * avgMsPerStrategy) / 1000.0));

            long mins = remainingSec / 60;
            long secs = remainingSec % 60;
            double avgSec = avgMsPerStrategy / 1000.0;

            sb.append(" | Restzeit: ");
            if (mins > 0) {
                sb.append(String.format(Locale.US, "%02dm %02ds", mins, secs));
            } else {
                sb.append(String.format(Locale.US, "%ds", secs));
            }
            sb.append(String.format(Locale.US, " (Ø %.1fs/Strat.)", avgSec));
        } else {
            sb.append(" | Restzeit: Berechne...");
        }

        return sb.toString();
    }

    private void updateProgressUI(double progress, String label) {
        Platform.runLater(() -> {
            progressBar.setProgress(progress);
            progressLabel.setText(label);
        });
    }
}
