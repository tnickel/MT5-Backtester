package com.backtester.ui.javafx;

import com.backtester.config.AppConfig;
import com.backtester.config.EaParameter;
import com.backtester.database.CustomProjectSaveCoordinator;
import com.backtester.database.DatabaseManager;
import com.backtester.engine.OptimizationConfig;
import com.backtester.engine.WorkflowEngine;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.PassPresetResolver;
import com.backtester.workflow.CustomProject;
import com.backtester.workflow.DatabankManager;
import com.backtester.workflow.FilterCondition;
import com.backtester.workflow.FilterGateAnalysisService;
import com.backtester.workflow.GuidedOptimizationService;
import com.backtester.workflow.ToTheMoon132GuidedWorkflowFactory;
import com.backtester.workflow.WorkflowTask;
import java.util.Set;
import java.util.LinkedHashSet;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import javafx.stage.Window;
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
    private ToggleButton automaticModeToggle;

    // Main Center Tabs (Progress | Full settings | Results)
    private TabPane centerMainTabPane;
    private Tab progressTab;
    private Tab fullSettingsTab;
    private Tab resultsTab;

    // Left Panel: Tasks Chain List
    private VBox taskChainListBox;
    private Button addTaskBtn;
    private WorkflowTask selectedTask;
    private OptimizerSettingsHighlightDialog optimizerSettingsHighlightDialog;

    // Progress Tab Components
    private ProgressBar progressBar;
    private Label progressPercentLabel;
    private Label progressLabel;
    private Label currentTaskBannerLabel;
    private TextArea consoleLog;

    /** True while Start/Single-Step execution is running (keeps task chain visible). */
    private boolean editorLocked;

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
    private Button filterGateAnalysisButton;
    private HBox filterGateAnalysisRow;
    private ComboBox<String> optimizerAlgorithmCombo;
    private ComboBox<String> optimizerCriterionCombo;
    private ComboBox<String> optimizerForwardModeCombo;
    private DatePicker optimizerForwardDatePicker;
    private boolean updatingOptimizerControls;
    private TextField optimizerTargetFilterField;
    private VBox optimizerTargetParametersBox;
    private Label optimizerTargetParametersSummary;
    private boolean updatingOptimizerTargetControls;
    private CheckBox deleteFailedCheckBox;
    private TableView<FilterCondition> filterConditionsTable;
    private TextField expertField;
    private TextField taskNameField;
    private TextField diversityParamDiffField;
    private TextField diversityTradeDiffField;
    private Spinner<Integer> diversityMinDiffParamsSpinner;
    private Spinner<Integer> diversityMaxStrategiesSpinner;
    private CheckBox diversityRankByScoreCheckBox;
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

    // Bottom Fixed Databank Panel
    private ProjectWorkflowDatabankPanel databankPanel;

    private ProjectWorkflowPipelineRunner pipelineRunner;

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

        // Pipeline runner before databank panel: panel refresh calls findSensitivityRunTimestampForDatabank.
        pipelineRunner = new ProjectWorkflowPipelineRunner(engine, databankManager, createPipelineHost());

        databankPanel = new ProjectWorkflowDatabankPanel(databankManager, createDatabankPanelHost());
        VBox bottomDatabankPanel = databankPanel.getNode();

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
            projectChanged |= ToTheMoon132GuidedWorkflowFactory.ensureDevelopmentTop20Selection(proj);
            projectChanged |= ToTheMoon132GuidedWorkflowFactory.repairStageOptimizerSearchSpaces(proj);
            engine.resetTransientResults();
            projectTitleLabel.setText("/ " + proj.getName());
            if (automaticModeToggle != null) {
                automaticModeToggle.setSelected(proj.isAutomaticModeEnabled());
                updateAutomaticModeToggleAppearance();
            }
            databankManager.loadFromProject(proj);
            if (databankPanel != null) {
                databankPanel.syncPersistCheckboxFromProject();
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
        if (databankPanel != null) databankPanel.refreshDatabanksUI();
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

        automaticModeToggle = new ToggleButton("🤖 Automatik: AUS");
        automaticModeToggle.setTooltip(new Tooltip(
                "Wählt zwischen Guided-Stufen automatisch den Pass mit dem höchsten Score."));
        automaticModeToggle.setOnAction(e -> {
            if (project == null) {
                automaticModeToggle.setSelected(false);
                updateAutomaticModeToggleAppearance();
                return;
            }
            project.setAutomaticModeEnabled(automaticModeToggle.isSelected());
            updateAutomaticModeToggleAppearance();
            saveProject();
            logToConsole("AUTOMATIK", automaticModeToggle.isSelected()
                    ? "Automatikmodus aktiviert: Höchster endlicher Score wird automatisch übernommen."
                    : "Automatikmodus deaktiviert: Auswahl erfolgt wieder per Hand-Pick.");
        });
        updateAutomaticModeToggleAppearance();

        Button showFlowBtn = new Button("Show Flow");
        showFlowBtn.setTooltip(new Tooltip(
                "Gesamtablauf: alle Tasks, was passiert ist und wie entschieden wurde."));
        showFlowBtn.setStyle(
                "-fx-background-color: #1e2432; -fx-text-fill: #e6e9f0; -fx-border-color: #00e676; "
                        + "-fx-border-radius: 5; -fx-background-radius: 5; -fx-font-weight: bold;");
        showFlowBtn.setOnAction(e -> openWorkflowFlowSummary());

        startBtn = new Button("▶ Start");
        startBtn.getStyleClass().add("button-start");
        startBtn.setOnAction(e -> pipelineRunner.start());

        stopBtn = new Button("⏹ Stop");
        stopBtn.getStyleClass().add("button-cancel");
        stopBtn.setDisable(true);
        stopBtn.setOnAction(e -> pipelineRunner.stop());

        resetBtn = new Button("🔄 Reset");
        resetBtn.getStyleClass().add("button");
        resetBtn.setOnAction(e -> pipelineRunner.reset());

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

        bar.getChildren().addAll(backBtn, titleBox, spacer, automaticModeToggle, showFlowBtn,
                startBtn, stopBtn, resetBtn, saveBtn, cloneBtn);
        return bar;
    }

    private void openWorkflowFlowSummary() {
        if (project == null) return;
        Window owner = root.getScene() != null ? root.getScene().getWindow() : null;
        WorkflowFlowSummaryDialog.show(
                owner,
                project,
                databankManager,
                this::effectiveOptimizerOutputDirectory);
    }

    private void updateAutomaticModeToggleAppearance() {
        if (automaticModeToggle == null) return;
        boolean enabled = automaticModeToggle.isSelected();
        automaticModeToggle.setText(enabled ? "🤖 Automatik: AN" : "🤖 Automatik: AUS");
        automaticModeToggle.setStyle(enabled
                ? "-fx-background-color: rgba(0, 230, 118, 0.24); -fx-text-fill: #69f0ae; "
                    + "-fx-border-color: #00e676; -fx-border-radius: 5; -fx-font-weight: bold;"
                : "-fx-background-color: #1e2432; -fx-text-fill: #cbd5e1; "
                    + "-fx-border-color: #596273; -fx-border-radius: 5; -fx-font-weight: bold;");
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

        addTaskBtn = new Button("➕ Add new task");
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
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(10));

        currentTaskBannerLabel = new Label("Aktueller Task: —");
        currentTaskBannerLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        currentTaskBannerLabel.setTextFill(Color.web("#00e676"));
        currentTaskBannerLabel.setMaxWidth(Double.MAX_VALUE);
        currentTaskBannerLabel.setWrapText(true);
        currentTaskBannerLabel.setPadding(new Insets(10, 12, 10, 12));
        currentTaskBannerLabel.setStyle(
            "-fx-background-color: rgba(0, 230, 118, 0.08); -fx-border-color: #00e676; "
                + "-fx-border-width: 1.5; -fx-border-radius: 6; -fx-background-radius: 6;"
        );

        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setPrefHeight(18);
        HBox.setHgrow(progressBar, Priority.ALWAYS);

        progressPercentLabel = new Label("0%");
        progressPercentLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        progressPercentLabel.setTextFill(Color.web("#00e676"));
        progressPercentLabel.setMinWidth(48);
        progressPercentLabel.setAlignment(Pos.CENTER_RIGHT);

        HBox progressRow = new HBox(10, progressBar, progressPercentLabel);
        progressRow.setAlignment(Pos.CENTER_LEFT);

        progressLabel = new Label("Bereit");
        progressLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        progressLabel.setTextFill(Color.web("#cbd5e1"));
        progressLabel.setWrapText(true);

        consoleLog = new TextArea();
        consoleLog.setEditable(false);
        consoleLog.setFont(Font.font("Consolas", 12));
        consoleLog.getStyleClass().add("text-area");
        VBox.setVgrow(consoleLog, Priority.ALWAYS);

        panel.getChildren().addAll(currentTaskBannerLabel, progressRow, progressLabel, consoleLog);
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

        filterGateAnalysisButton = new Button("Filter an/aus analysieren");
        filterGateAnalysisButton.setStyle(
                "-fx-background-color: #2e7d32; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        filterGateAnalysisButton.setTooltip(new Tooltip(
                "Vergleicht Optimizer-Passes mit Use_*-Filter an vs aus (Report bevorzugt, sonst Databank-Fallback)."));
        filterGateAnalysisButton.setOnAction(e -> openFilterGateAnalysis(selectedTask));
        filterGateAnalysisRow = new HBox(10, filterGateAnalysisButton);
        filterGateAnalysisRow.setAlignment(Pos.CENTER_LEFT);
        filterGateAnalysisRow.setPadding(new Insets(4, 0, 0, 0));
        filterGateAnalysisRow.visibleProperty().bind(optimizerOutputDirectoryRow.visibleProperty());
        filterGateAnalysisRow.managedProperty().bind(optimizerOutputDirectoryRow.managedProperty());
        grid.add(filterGateAnalysisRow, 0, 7, 2, 1);

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
            if (databankPanel != null) databankPanel.refreshDatabanksUI();
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
        rankingSourceCombo.setOnShowing(e -> {
            if (databankPanel != null) databankPanel.updateDatabankComboBoxes();
        });
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
        rankingTargetCombo.setOnShowing(e -> {
            if (databankPanel != null) databankPanel.updateDatabankComboBoxes();
        });
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

        diversityRankByScoreCheckBox = new CheckBox("Vor Clustering nach Score absteigend sortieren");
        diversityRankByScoreCheckBox.setTooltip(new Tooltip(
                "Der höchste endliche Score wird zuerst geprüft; bei Gleichstand gewinnt die kleinere Passnummer."));
        diversityRankByScoreCheckBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
            if (!updatingDiversityControls && selectedTask != null) {
                selectedTask.setDiversityRankByScore(newValue);
                saveProject();
            }
        });
        grid.add(diversityRankByScoreCheckBox, 0, 2, 4, 1);

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
            if (diversityRankByScoreCheckBox != null) diversityRankByScoreCheckBox.setSelected(false);

            if (selectedTask != null) {
                selectedTask.setDiversityParamDiffPct(defaultParamDiff);
                selectedTask.setDiversityTradeDiffPct(defaultTradeDiff);
                selectedTask.setDiversityMinDifferentParams(defaultMinParams);
                selectedTask.setDiversityMaxStrategies(defaultMaxStrats);
                selectedTask.setDiversityRankByScore(false);
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
            if (selectedTask != null && selectedTask.getType() == WorkflowTask.TaskType.OPTIMIZER) {
                List<EaParameter> taskSnapshot = selectedTask.getOptimizerParameterSnapshot();
                if (!taskSnapshot.isEmpty()) engine.setEaParameters(taskSnapshot);
            }
            WorkflowConfigDialogs.showStep1Dialog(engine, root.getScene().getWindow(), () -> {
                if (selectedTask != null && selectedTask.getType() == WorkflowTask.TaskType.OPTIMIZER) {
                    selectedTask.setOptimizerParameterSnapshot(
                            applyTargetFlags(engine.getEaParameters(), selectedTask.getOptimizerTargetParameters()));
                    refreshOptimizerTargetParameterControls(selectedTask);
                    saveProject();
                }
            });
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

        Label targetHeading = new Label("Ziel-Parameter dieser Optimierungsstufe");
        targetHeading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        targetHeading.setTextFill(Color.web("#76ff03"));
        Label targetHelp = new Label(
                "Nur diese Parameter werden nach einem Hand-Pick auf Y gesetzt. Alle geerbten Werte bleiben fest auf N.");
        targetHelp.setWrapText(true);
        targetHelp.setStyle("-fx-text-fill: #aab2c0;");

        optimizerTargetFilterField = new TextField();
        optimizerTargetFilterField.setPromptText("Parameter suchen …");
        optimizerTargetFilterField.textProperty().addListener((obs, oldValue, newValue) -> {
            if (selectedTask != null && selectedTask.getType() == WorkflowTask.TaskType.OPTIMIZER) {
                refreshOptimizerTargetParameterControls(selectedTask);
            }
        });

        optimizerTargetParametersBox = new VBox(4);
        ScrollPane targetScroll = new ScrollPane(optimizerTargetParametersBox);
        targetScroll.setFitToWidth(true);
        targetScroll.setPrefViewportHeight(190);
        targetScroll.setStyle("-fx-background-color: transparent;");

        Button useCurrentOptimizedBtn = new Button("Aktuelle Y-Parameter übernehmen");
        useCurrentOptimizedBtn.setOnAction(e -> useCurrentOptimizedParametersAsTargets());
        Button clearTargetsBtn = new Button("Auswahl leeren");
        clearTargetsBtn.setOnAction(e -> {
            if (selectedTask == null || selectedTask.getType() != WorkflowTask.TaskType.OPTIMIZER) return;
            selectedTask.setOptimizerTargetParameters(List.of());
            selectedTask.clearOptimizerParameterBasis();
            refreshOptimizerTargetParameterControls(selectedTask);
            saveProject();
        });
        optimizerTargetParametersSummary = new Label();
        HBox targetButtons = new HBox(10, useCurrentOptimizedBtn, clearTargetsBtn, optimizerTargetParametersSummary);
        targetButtons.setAlignment(Pos.CENTER_LEFT);

        panel.getChildren().addAll(heading, grid, new Separator(), btnBox, new Separator(),
                targetHeading, targetHelp, optimizerTargetFilterField, targetScroll, targetButtons);
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

    private ProjectWorkflowDatabankPanel.Host createDatabankPanelHost() {
        return new ProjectWorkflowDatabankPanel.Host() {
            @Override
            public CustomProject getProject() {
                return project;
            }

            @Override
            public WorkflowTask getSelectedTask() {
                return selectedTask;
            }

            @Override
            public Window getOwnerWindow() {
                return root.getScene() != null ? root.getScene().getWindow() : null;
            }

            @Override
            public List<String> getStylesheets() {
                if (root.getScene() == null) return List.of();
                return List.copyOf(root.getScene().getStylesheets());
            }

            @Override
            public void saveProject() {
                ProjectWorkflowEditorView.this.saveProject();
            }

            @Override
            public void flushProjectSaveAsync(Runnable continuation) {
                ProjectWorkflowEditorView.this.flushProjectSaveAsync(continuation);
            }

            @Override
            public void refreshTaskChain() {
                ProjectWorkflowEditorView.this.refreshTaskChain();
            }

            @Override
            public void logToConsole(String tag, String message) {
                ProjectWorkflowEditorView.this.logToConsole(tag, message);
            }

            @Override
            public void invalidateWorkflowResultsAfterDatabankClear(boolean allDatabanks, String databankName) {
                ProjectWorkflowEditorView.this.invalidateWorkflowResultsAfterDatabankClear(allDatabanks, databankName);
            }

            @Override
            public void purgeWorkflowRunArtifacts() {
                ProjectWorkflowEditorView.this.purgeWorkflowRunArtifacts();
            }

            @Override
            public void adoptPassParameters(CombinedPass pass, String dbName) {
                adoptPassParametersForNextTask(pass, dbName);
            }

            @Override
            public long findSensitivityRunTimestampForDatabank(String databankName) {
                return ProjectWorkflowEditorView.this.findSensitivityRunTimestampForDatabank(databankName);
            }

            @Override
            public void backupProject() {
                ProjectWorkflowEditorView.this.backupProject();
            }

            @Override
            public void restoreProject() {
                ProjectWorkflowEditorView.this.restoreProject();
            }

            @Override
            public ComboBox<String> getSourceDatabankCombo() {
                return sourceDatabankCombo;
            }

            @Override
            public ComboBox<String> getTargetDatabankCombo() {
                return targetDatabankCombo;
            }

            @Override
            public ComboBox<String> getRankingSourceCombo() {
                return rankingSourceCombo;
            }

            @Override
            public ComboBox<String> getRankingTargetCombo() {
                return rankingTargetCombo;
            }
        };
    }

    private ProjectWorkflowPipelineRunner.Host createPipelineHost() {
        return new ProjectWorkflowPipelineRunner.Host() {
            @Override
            public CustomProject getProject() {
                return project;
            }

            @Override
            public WorkflowTask getSelectedTask() {
                return selectedTask;
            }

            @Override
            public Window getOwnerWindow() {
                return root.getScene() != null ? root.getScene().getWindow() : null;
            }

            @Override
            public void commitCurrentTaskDataSettings() {
                ProjectWorkflowEditorView.this.commitCurrentTaskDataSettings();
            }

            @Override
            public void syncDatePickersIntoSelectedTask(WorkflowTask task) {
                if (selectedTask != task) return;
                if (startDatePicker != null && startDatePicker.getValue() != null) {
                    task.setStartDate(startDatePicker.getValue().toString());
                }
                if (endDatePicker != null && endDatePicker.getValue() != null) {
                    task.setEndDate(endDatePicker.getValue().toString());
                }
            }

            @Override
            public void saveProject() {
                ProjectWorkflowEditorView.this.saveProject();
            }

            @Override
            public boolean flushProjectSave(Duration timeout) {
                return projectSaveCoordinator.flush(timeout);
            }

            @Override
            public void flushProjectSaveAsync(Runnable continuation) {
                ProjectWorkflowEditorView.this.flushProjectSaveAsync(continuation);
            }

            @Override
            public void logToConsole(String tag, String message) {
                ProjectWorkflowEditorView.this.logToConsole(tag, message);
            }

            @Override
            public void refreshTaskChain() {
                ProjectWorkflowEditorView.this.refreshTaskChain();
            }

            @Override
            public void refreshDatabanksUI() {
                if (databankPanel != null) databankPanel.refreshDatabanksUI();
            }

            @Override
            public void refreshDatabanksUI(String focusDb) {
                if (databankPanel != null) databankPanel.refreshDatabanksUI(focusDb);
            }

            @Override
            public void setEditorLocked(boolean locked) {
                ProjectWorkflowEditorView.this.setEditorLocked(locked);
            }

            @Override
            public void setStartStopResetDisabled(boolean startDisabled, boolean stopDisabled, boolean resetDisabled) {
                if (startBtn != null) startBtn.setDisable(startDisabled);
                if (stopBtn != null) stopBtn.setDisable(stopDisabled);
                if (resetBtn != null) resetBtn.setDisable(resetDisabled);
            }

            @Override
            public void selectProgressTab() {
                if (centerMainTabPane != null && progressTab != null) {
                    centerMainTabPane.getSelectionModel().select(progressTab);
                }
            }

            @Override
            public void clearConsoleLog() {
                if (consoleLog != null) consoleLog.clear();
            }

            @Override
            public void updateMainProgress(double progress, String percentText, String label, String taskBannerName) {
                if (progressBar != null) progressBar.setProgress(progress);
                if (progressPercentLabel != null) progressPercentLabel.setText(percentText);
                if (progressLabel != null) progressLabel.setText(label);
                if (currentTaskBannerLabel != null) {
                    currentTaskBannerLabel.setText("Aktueller Task: " + taskBannerName);
                }
            }

            @Override
            public void resetProgressDisplay(String label) {
                if (progressBar != null) progressBar.setProgress(0);
                if (progressPercentLabel != null) progressPercentLabel.setText("0%");
                if (progressLabel != null) progressLabel.setText(label);
                if (currentTaskBannerLabel != null) currentTaskBannerLabel.setText("Aktueller Task: —");
            }

            @Override
            public void clearRunningTaskBannerIfStale() {
                if (currentTaskBannerLabel != null && progressLabel != null
                        && (progressLabel.getText() == null || progressLabel.getText().isBlank()
                        || progressLabel.getText().startsWith("Führe"))) {
                    currentTaskBannerLabel.setText("Aktueller Task: —");
                }
            }

            @Override
            public void purgeWorkflowRunArtifacts() {
                ProjectWorkflowEditorView.this.purgeWorkflowRunArtifacts();
            }

            @Override
            public void adoptBestPassAutomatically(WorkflowTask nextOptimizer) {
                ProjectWorkflowEditorView.this.adoptBestPassAutomatically(nextOptimizer);
            }

            @Override
            public Path optimizerOutputBaseDirectory(WorkflowTask task) {
                return ProjectWorkflowEditorView.this.optimizerOutputBaseDirectory(task);
            }

            @Override
            public Duration projectSaveFlushTimeout() {
                return PROJECT_SAVE_FLUSH_TIMEOUT;
            }
        };
    }

    // ─── Task Selection & Form Update ─────────────────────────────────────────

    private void refreshTaskChain() {
        taskChainListBox.getChildren().clear();
        if (project == null || project.getTasks() == null) return;

        List<WorkflowTask> tasks = project.getTasks();
        VBox runningCard = null;
        for (int i = 0; i < tasks.size(); i++) {
            WorkflowTask task = tasks.get(i);
            VBox card = createTaskCard(task, i, tasks.size());
            taskChainListBox.getChildren().add(card);
            if (task.getStatus() == WorkflowTask.TaskStatus.RUNNING) {
                runningCard = card;
            }
        }
        if (runningCard != null) {
            VBox target = runningCard;
            Platform.runLater(() -> {
                target.requestFocus();
                target.setViewOrder(-1);
            });
        }
    }

    private VBox createTaskCard(WorkflowTask task, int index, int totalTasks) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(10));
        boolean isRunning = task.getStatus() == WorkflowTask.TaskStatus.RUNNING;
        boolean isSelected = selectedTask == task;
        if (isRunning) {
            card.setStyle(
                "-fx-background-color: rgba(0, 230, 118, 0.12); -fx-border-color: #00e676; "
                    + "-fx-border-width: 3.5; -fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: hand;"
            );
        } else if (isSelected) {
            card.setStyle(
                "-fx-background-color: rgba(0, 229, 255, 0.15); -fx-border-color: #00e5ff; "
                    + "-fx-border-width: 1.5; -fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: hand;"
            );
        } else {
            card.setStyle(
                "-fx-background-color: rgba(26, 30, 40, 0.85); -fx-border-color: #3e4555; "
                    + "-fx-border-width: 1.5; -fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: hand;"
            );
        }

        card.setOnMouseClicked(e -> selectTask(task));

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label((index + 1) + ". " + task.getName());
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        title.setTextFill(task.isEnabled() ? Color.web("#e6e9f0") : Color.web("#7e889a"));
        HBox.setHgrow(title, Priority.ALWAYS);

        CheckBox toggleBox = new CheckBox();
        toggleBox.setSelected(task.isEnabled());
        toggleBox.setDisable(editorLocked);
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
        else if (task.getStatus() == WorkflowTask.TaskStatus.RUNNING) statusColor = "#00e676";
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
        upBtn.setDisable(editorLocked || index == 0);
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
        downBtn.setDisable(editorLocked || index == totalTasks - 1);
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
        runSingleBtn.setDisable(editorLocked);
        runSingleBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #00e5ff; -fx-font-weight: bold; -fx-padding: 2; -fx-cursor: hand;");
        runSingleBtn.setTooltip(new Tooltip("Einzelstep ausführen (nur diese Kachel)"));
        runSingleBtn.setOnAction(e -> {
            e.consume();
            logger.info(">>> USER CLICKED SINGLE-STEP BUTTON ▶ FOR TASK: '{}' (Type: {}, Source: '{}', Target: '{}')",
                task.getName(), task.getType(), task.getSourceDatabank(), task.getTargetDatabank());
            selectTask(task);
            pipelineRunner.runSingle(task);
        });

        Button configBtn = new Button(task.getType() == WorkflowTask.TaskType.DIVERSITY_FILTER
                ? "⚙ Einstellungen" : "⚙");
        configBtn.setDisable(editorLocked);
        configBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ffd740; -fx-padding: 2; -fx-cursor: hand;");
        configBtn.setTooltip(new Tooltip(task.getType() == WorkflowTask.TaskType.DIVERSITY_FILTER
                ? "Diversitäts-Clustering konfigurieren"
                : "Modulname und Task-Einstellungen öffnen"));
        configBtn.setOnAction(e -> {
            e.consume();
            openTaskSettings(task);
        });

        Button cloneBtn = new Button("⧉");
        cloneBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #80cbc4; -fx-font-weight: bold; -fx-padding: 2; -fx-cursor: hand;");
        cloneBtn.setTooltip(new Tooltip("Clone below — Task mit identischen Settings darunter einfügen"));
        cloneBtn.setOnAction(e -> {
            e.consume();
            WorkflowTask cloned = task.cloneWithSettings();
            if (project.insertTaskBelow(index, cloned)) {
                if (cloned.getSourceDatabank() != null && !cloned.getSourceDatabank().isBlank()) {
                    databankManager.createDatabank(cloned.getSourceDatabank());
                }
                if (cloned.getTargetDatabank() != null && !cloned.getTargetDatabank().isBlank()) {
                    databankManager.createDatabank(cloned.getTargetDatabank());
                }
                saveProject();
                refreshTaskChain();
                selectTask(cloned);
                logToConsole("WORKFLOW", "Task geklont unter Position " + (index + 1) + ": " + cloned.getName());
            }
        });

        Button deleteBtn = new Button("🗑");
        deleteBtn.setDisable(editorLocked);
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

        actionsRow.getChildren().addAll(upBtn, downBtn, runSingleBtn, configBtn, cloneBtn, deleteBtn);
        if (dbRoutingRow != null) {
            card.getChildren().addAll(topRow, subRow, dbRoutingRow, actionsRow);
        } else {
            card.getChildren().addAll(topRow, subRow, actionsRow);
        }

        if (task.getType() == WorkflowTask.TaskType.OPTIMIZER) {
            ContextMenu cardMenu = new ContextMenu();
            MenuItem filterAnalysisItem = new MenuItem("Filter an/aus analysieren");
            filterAnalysisItem.setOnAction(e -> {
                selectTask(task);
                openFilterGateAnalysis(task);
            });
            cardMenu.getItems().add(filterAnalysisItem);
            card.setOnContextMenuRequested(event -> {
                cardMenu.show(card, event.getScreenX(), event.getScreenY());
                event.consume();
            });
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
        if (databankPanel != null) databankPanel.updateDatabankComboBoxes();
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
                if (diversityRankByScoreCheckBox != null) {
                    diversityRankByScoreCheckBox.setSelected(task.isDiversityRankByScore());
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
                refreshOptimizerTargetParameterControls(task);
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
        updateOptimizerSettingsHighlightWindow(task);
    }

    /**
     * Opens (on first Optimizer click) or refreshes the companion setting window
     * so the green-marked search-space rows always match the selected task.
     */
    private void updateOptimizerSettingsHighlightWindow(WorkflowTask task) {
        boolean optimizer = task != null && task.getType() == WorkflowTask.TaskType.OPTIMIZER;
        boolean windowOpen = optimizerSettingsHighlightDialog != null
                && optimizerSettingsHighlightDialog.isShowing();
        if (!optimizer && !windowOpen) {
            return;
        }
        // Defer until after selectTask finished rebuilding the chain/controls,
        // so the companion window always reads the final selected task state.
        final WorkflowTask selected = task;
        Platform.runLater(() -> {
            if (selectedTask != selected) {
                // A newer click already superseded this selection.
                return;
            }
            Window owner = root.getScene() != null ? root.getScene().getWindow() : null;
            List<EaParameter> fallback = engine != null ? engine.getEaParameters() : List.of();
            String outputDir = selected != null ? effectiveOptimizerOutputDirectory(selected) : null;
            optimizerSettingsHighlightDialog = OptimizerSettingsHighlightDialog.showOrRefresh(
                    optimizerSettingsHighlightDialog,
                    owner,
                    selected,
                    fallback,
                    outputDir,
                    databankManager);
        });
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

    private void openFilterGateAnalysis(WorkflowTask task) {
        if (task == null || task.getType() != WorkflowTask.TaskType.OPTIMIZER) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                    "Filter-Analyse ist nur für Optimizer-Tasks verfügbar.", ButtonType.OK);
            alert.setTitle("Filter an/aus");
            alert.setHeaderText(null);
            if (root.getScene() != null) alert.initOwner(root.getScene().getWindow());
            alert.showAndWait();
            return;
        }

        FilterGateAnalysisService.PassLoadResult loaded = FilterGateAnalysisService.loadPassesForTask(
                task, effectiveOptimizerOutputDirectory(task), databankManager);
        if (loaded.getPasses().isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING,
                    "Keine Passes gefunden.\n\nWeder ein Optimizer-Report unter dem Ausgabeordner "
                            + "noch Strategien in der Ziel-Databank „" + task.getTargetDatabank() + "“.",
                    ButtonType.OK);
            alert.setTitle("Filter an/aus");
            alert.setHeaderText("Keine Daten");
            if (root.getScene() != null) alert.initOwner(root.getScene().getWindow());
            alert.showAndWait();
            return;
        }

        List<String> candidates = FilterGateAnalysisService.listGateParameterCandidates(
                task, loaded.getPasses());
        List<String> optimized = FilterGateAnalysisService.listOptimizedParameterNames(
                task, loaded.getPasses());
        String gate = candidates.isEmpty() ? "" : candidates.get(0);
        FilterGateAnalysisService.FilterGateAnalysis analysis = FilterGateAnalysisService.analyze(
                loaded.getPasses(),
                gate,
                loaded.getDataSource(),
                loaded.getSourcePath(),
                loaded.getDatabankName(),
                FilterGateAnalysisService.DEFAULT_MIN_COHORT_SIZE,
                FilterGateAnalysisService.DEFAULT_TOP_N,
                FilterGateAnalysisService.DEFAULT_SCORE_MARGIN,
                candidates,
                optimized);

        Window owner = root.getScene() != null ? root.getScene().getWindow() : null;
        FilterGateAnalysisDialog.show(owner, task.getName(), analysis, loaded.getPasses(), pass -> {
            if (pass != null) {
                StrategyDetailsModalDialog.show(pass, task.getTargetDatabank(), project, owner, 0);
            }
        });

        if (loaded.isFallback()) {
            logToConsole("FILTER-ANALYSE",
                    "Fallback auf Ziel-Databank „" + loaded.getDatabankName()
                            + "“ — kein vollständiger Optimizer-Report gefunden.");
        } else {
            logToConsole("FILTER-ANALYSE",
                    "Quelle Optimizer-Report: " + loaded.getSourcePath());
        }
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

    private void adoptPassParametersForNextTask(CombinedPass selectedPass, String dbName) {
        try {
            WorkflowTask nextOptimizer = GuidedOptimizationService.findNextActiveOptimizer(project, dbName)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Nach der Databank '" + dbName + "' ist kein aktiver Optimizer-Task vorhanden."));
            int targetCount = nextOptimizer.getOptimizerTargetParameters().size();
            if (targetCount == 0) {
                throw new IllegalArgumentException("Im nächsten Optimizer-Task '" + nextOptimizer.getName()
                        + "' sind noch keine Ziel-Parameter ausgewählt.");
            }

            PassPresetResolver.Resolution resolution = PassPresetResolver.resolve(
                    selectedPass, project != null ? project.getExpert() : engine.getExpert());
            String fidelityText;
            switch (resolution.fidelity()) {
                case EXACT_SNAPSHOT: fidelityText = "exakter Lauf-Snapshot"; break;
                case OPTIMIZATION_BASE: fidelityText = "archiviertes Optimizer-Preset + Passwerte"; break;
                default: fidelityText = "aktuelle EA-Konfiguration (nicht vollständig beweisbar)"; break;
            }

            String warning = resolution.warning() != null && !resolution.warning().isBlank()
                    ? "\n\nWARNUNG: " + resolution.warning() : "";
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.setTitle("Parameter-Basis übernehmen");
            confirmation.setHeaderText("Pass #" + selectedPass.getPassNumber() + " → " + nextOptimizer.getName());
            confirmation.setContentText("Quelle: " + dbName
                    + "\nParameterquelle: " + fidelityText
                    + "\nNeue Optimierungsziele: " + targetCount
                    + "\n\nAlle übrigen Parameter werden mit den Passwerten fixiert (N)."
                    + warning);
            confirmation.initOwner(root.getScene().getWindow());
            if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

            GuidedOptimizationService.AdoptionResult result = GuidedOptimizationService.adoptPassParameters(
                    project, engine.getEaParameters(), resolution.parameters(), selectedPass, dbName);
            WorkflowTask consumer = result.getNextOptimizer();
            WorkflowTask producer = GuidedOptimizationService.findPreviousEnabledOptimizer(project, consumer)
                    .orElse(null);
            GuidedOptimizationService.FilterGateForceResult gateForce =
                    GuidedOptimizationService.applyFilterGateRecommendation(
                            producer,
                            consumer,
                            producer != null ? effectiveOptimizerOutputDirectory(producer) : "",
                            databankManager);
            engine.setEaParameters(consumer.getOptimizerParameterSnapshot());
            saveProject();
            refreshTaskChain();
            selectTask(consumer);
            String banner = "Parameter aus Pass #" + result.getPassNumber()
                    + " als Basis für '" + consumer.getName() + "' übernommen";
            if (gateForce.isForced()) {
                banner += " · Filter erzwungen (" + gateForce.getForcedDisplay() + ")";
            }
            showParameterAdoptionBanner(banner, true);
            if (databankPanel != null) {
                databankPanel.refreshDatabanksUI(dbName);
            }
            logToConsole("GUIDED-OPT", result.getAdoptedParameterCount() + " Passparameter fixiert; "
                    + result.getEnabledTargetCount() + " Zielparameter für den nächsten Optimizer aktiviert."
                    + (gateForce.getNote().isBlank() ? "" : " " + gateForce.getNote()));
        } catch (IllegalArgumentException ex) {
            showParameterAdoptionBanner(ex.getMessage(), false);
            Alert alert = new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK);
            alert.setTitle("Parameter-Basis konnte nicht übernommen werden");
            alert.setHeaderText("Guided Optimization");
            alert.initOwner(root.getScene().getWindow());
            alert.show();
        } catch (RuntimeException ex) {
            logger.error("Fehler bei der Parameter-Übernahme aus Databank {}", dbName, ex);
            showParameterAdoptionBanner("Parameter-Übernahme fehlgeschlagen: " + ex.getMessage(), false);
        }
    }

    private void adoptBestPassAutomatically(WorkflowTask nextOptimizer) {
        String sourceDatabank = nextOptimizer != null ? nextOptimizer.getSourceDatabank() : "";
        List<CombinedPass> candidates = databankManager.getDatabank(sourceDatabank);
        CombinedPass bestPass = GuidedOptimizationService.selectBestPass(candidates)
                .orElseThrow(() -> new IllegalStateException(
                        "Automatikmodus: In Databank '" + sourceDatabank
                                + "' existiert keine Strategie mit einem endlichen Score."));

        PassPresetResolver.Resolution resolution = PassPresetResolver.resolve(
                bestPass, project != null ? project.getExpert() : engine.getExpert());
        if (resolution.fidelity() == PassPresetResolver.Fidelity.CURRENT_CONFIG) {
            throw new IllegalStateException("Automatikmodus: Pass #" + bestPass.getPassNumber()
                    + " kann nicht sicher übernommen werden, weil kein archiviertes Lauf-Preset vorhanden ist. "
                    + "Die automatische Vererbung wurde gestoppt, statt Parameterwerte zu raten.");
        }

        GuidedOptimizationService.AdoptionResult result = GuidedOptimizationService.adoptPassParameters(
                project, engine.getEaParameters(), resolution.parameters(), bestPass, sourceDatabank);
        if (result.getNextOptimizer() != nextOptimizer) {
            throw new IllegalStateException("Automatikmodus hat einen unerwarteten Ziel-Task ermittelt: "
                    + result.getNextOptimizer().getName());
        }

        WorkflowTask producer = GuidedOptimizationService.findPreviousEnabledOptimizer(project, nextOptimizer)
                .orElse(null);
        String producerOut = producer != null ? effectiveOptimizerOutputDirectory(producer) : "";
        GuidedOptimizationService.FilterGateForceResult gateForce =
                GuidedOptimizationService.applyFilterGateRecommendation(
                        producer, nextOptimizer, producerOut, databankManager);

        engine.setEaParameters(nextOptimizer.getOptimizerParameterSnapshot());
        saveProject();
        String scoreText = String.format(Locale.ROOT, "%.3f", bestPass.getScore());
        StringBuilder messageBuilder = new StringBuilder()
                .append("Pass #").append(bestPass.getPassNumber())
                .append(" mit höchstem Score ").append(scoreText)
                .append(" aus '").append(sourceDatabank)
                .append("' automatisch als Basis für '")
                .append(nextOptimizer.getName()).append("' übernommen.");
        if (gateForce.isForced()) {
            messageBuilder.append(" Filter-Empfehlung übernommen: ")
                    .append(gateForce.getForcedDisplay())
                    .append(".");
        } else if (gateForce.getNote() != null && !gateForce.getNote().isBlank()) {
            messageBuilder.append(' ').append(gateForce.getNote());
        }
        String message = messageBuilder.toString();
        logToConsole("AUTOMATIK", message);
        Platform.runLater(() -> {
            showParameterAdoptionBanner(message, true);
            if (databankPanel != null) {
                databankPanel.refreshDatabanksUI(sourceDatabank);
            }
        });
    }

    private void showParameterAdoptionBanner(String message, boolean success) {
        if (databankPanel != null) {
            databankPanel.showParameterAdoptionBanner(message, success);
        }
    }

    private void refreshOptimizerTargetParameterControls(WorkflowTask task) {
        if (optimizerTargetParametersBox == null || optimizerTargetParametersSummary == null) return;
        updatingOptimizerTargetControls = true;
        try {
            optimizerTargetParametersBox.getChildren().clear();
            if (task == null || task.getType() != WorkflowTask.TaskType.OPTIMIZER) {
                optimizerTargetParametersSummary.setText("");
                return;
            }

            Set<String> targets = new LinkedHashSet<>(task.getOptimizerTargetParameters());
            String filter = optimizerTargetFilterField != null && optimizerTargetFilterField.getText() != null
                    ? optimizerTargetFilterField.getText().trim().toLowerCase(Locale.ROOT) : "";
            List<EaParameter> parameters = task.getOptimizerParameterSnapshot();
            if (parameters.isEmpty()) parameters = engine.getEaParameters();

            int visible = 0;
            for (EaParameter parameter : parameters) {
                if (parameter == null || parameter.getName() == null || parameter.getName().isBlank()
                        || parameter.isSectionHeader() || parameter.isStringType()) continue;
                String name = parameter.getName().trim();
                String display = parameter.getDisplayName() != null ? parameter.getDisplayName().trim() : "";
                String searchable = (name + " " + display).toLowerCase(Locale.ROOT);
                if (!filter.isEmpty() && !searchable.contains(filter)) continue;

                CheckBox checkBox = new CheckBox(display.isEmpty() || display.equals(name)
                        ? name : name + "  —  " + display);
                checkBox.setUserData(name);
                checkBox.setSelected(targets.contains(name));
                checkBox.setStyle("-fx-text-fill: #d9dde6;");
                checkBox.setOnAction(e -> {
                    if (updatingOptimizerTargetControls) return;
                    Set<String> changed = new LinkedHashSet<>(task.getOptimizerTargetParameters());
                    if (checkBox.isSelected()) changed.add(name); else changed.remove(name);
                    task.setOptimizerTargetParameters(new ArrayList<>(changed));
                    synchronizeSnapshotTargetFlags(task);
                    updateOptimizerTargetSummary(task);
                    saveProject();
                });
                optimizerTargetParametersBox.getChildren().add(checkBox);
                visible++;
            }
            if (visible == 0) {
                Label empty = new Label(parameters.isEmpty()
                        ? "Keine EA-Parameter geladen. Zuerst den EA-Parameterdialog öffnen."
                        : "Keine Parameter entsprechen dem Suchtext.");
                empty.setStyle("-fx-text-fill: #ffab40;");
                optimizerTargetParametersBox.getChildren().add(empty);
            }
            updateOptimizerTargetSummary(task);
        } finally {
            updatingOptimizerTargetControls = false;
        }
    }

    private void updateOptimizerTargetSummary(WorkflowTask task) {
        if (optimizerTargetParametersSummary == null || task == null) return;
        int count = task.getOptimizerTargetParameters().size();
        boolean hasSnapshot = !task.getOptimizerParameterSnapshot().isEmpty();
        String basis = task.isOptimizerParameterBasisAdopted()
                ? "Basis Pass #" + task.getOptimizerParameterBasisPassNumber()
                : (hasSnapshot ? "Suchraum-Vorlage vorhanden" : "noch kein Snapshot");
        optimizerTargetParametersSummary.setText(count + " Zielparameter · " + basis);
        optimizerTargetParametersSummary.setStyle("-fx-text-fill: "
                + (count > 0 ? "#76ff03" : "#ffab40") + "; -fx-font-weight: bold;");
    }

    private void useCurrentOptimizedParametersAsTargets() {
        if (selectedTask == null || selectedTask.getType() != WorkflowTask.TaskType.OPTIMIZER) return;
        List<EaParameter> parameters = selectedTask.getOptimizerParameterSnapshot();
        if (parameters.isEmpty()) parameters = engine.getEaParameters();
        List<String> targets = new ArrayList<>();
        for (EaParameter parameter : parameters) {
            if (parameter != null && parameter.isOptimizeEnabled() && !parameter.isSectionHeader()
                    && !parameter.isStringType() && parameter.getName() != null && !parameter.getName().isBlank()) {
                targets.add(parameter.getName().trim());
            }
        }
        selectedTask.setOptimizerTargetParameters(targets);
        synchronizeSnapshotTargetFlags(selectedTask);
        refreshOptimizerTargetParameterControls(selectedTask);
        saveProject();
    }

    private void synchronizeSnapshotTargetFlags(WorkflowTask task) {
        if (task == null) return;
        List<EaParameter> snapshot = task.getOptimizerParameterSnapshot();
        if (snapshot.isEmpty()) return;
        task.setOptimizerParameterSnapshot(applyTargetFlags(snapshot, task.getOptimizerTargetParameters()));
    }

    private static List<EaParameter> applyTargetFlags(List<EaParameter> parameters, List<String> targetNames) {
        Set<String> targets = new LinkedHashSet<>(targetNames != null ? targetNames : List.of());
        List<EaParameter> result = new ArrayList<>();
        if (parameters == null) return result;
        for (EaParameter source : parameters) {
            if (source == null) continue;
            EaParameter copy = source.copy();
            copy.setOptimizeEnabled(copy.getName() != null && targets.contains(copy.getName()));
            result.add(copy);
        }
        return result;
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

    private long findSensitivityRunTimestampForDatabank(String databankName) {
        if (pipelineRunner == null) {
            return 0L;
        }
        return pipelineRunner.findSensitivityRunTimestampForDatabank(databankName);
    }

    // Thin wrappers / static forwarders for tests and call sites
    private void startProjectExecution() {
        pipelineRunner.start();
    }

    private void runSingleTask(WorkflowTask task) {
        pipelineRunner.runSingle(task);
    }

    static boolean shouldSkipCompletedTask(WorkflowTask task,
                                           java.util.function.Predicate<String> databankHasStrategies) {
        return ProjectWorkflowPipelineRunner.shouldSkipCompletedTask(task, databankHasStrategies);
    }

    static boolean shouldReuseExistingTaskResult(WorkflowTask task,
                                                 boolean automaticMode,
                                                 java.util.function.Predicate<String> databankHasStrategies) {
        return ProjectWorkflowPipelineRunner.shouldReuseExistingTaskResult(task, automaticMode, databankHasStrategies);
    }

    static boolean shouldAutomaticallyAdoptBestPass(CustomProject project, WorkflowTask task) {
        return ProjectWorkflowPipelineRunner.shouldAutomaticallyAdoptBestPass(project, task);
    }

    /**
     * Databank wipe removes the evidence that completed tasks produced. Reset
     * matching tile statuses to PENDING so the pipeline no longer shows FERTIG.
     */
    private void invalidateWorkflowResultsAfterDatabankClear(boolean allDatabanks, String databankName) {
        if (project == null) return;
        GuidedOptimizationService.resetTaskStatusesAfterDatabankWipe(project, allDatabanks, databankName);
        if (allDatabanks) {
            try {
                engine.resetTransientResults();
            } catch (RuntimeException ignored) {
                // best-effort; databank wipe must not fail because of engine state
            }
        }
        if (progressBar != null) progressBar.setProgress(0);
        if (progressPercentLabel != null) progressPercentLabel.setText("0%");
        if (progressLabel != null) {
            progressLabel.setText(allDatabanks
                    ? "Workflow zurückgesetzt — Databanken und Status geleert."
                    : "Databanken geleert — Task-Status zurückgesetzt.");
        }
        if (currentTaskBannerLabel != null) currentTaskBannerLabel.setText("Aktueller Task: —");
    }

    private void purgeWorkflowRunArtifacts() {
        if (project == null) return;
        var cleanup = com.backtester.workflow.WorkflowRunArtifactCleanupService.purgeProjectArtifacts(project);
        logToConsole("CLEANUP", "Workflow-Artefakte gelöscht — " + cleanup.summary());
        for (String detail : cleanup.details) {
            logToConsole("CLEANUP", detail);
        }
        if (progressLabel != null) {
            progressLabel.setText("Workflow zurückgesetzt — " + cleanup.summary());
        }
    }

    private void setEditorLocked(boolean locked) {
        editorLocked = locked;
        Runnable updateUI = () -> {
            if (fullSettingsTab != null) fullSettingsTab.setDisable(locked);
            if (resultsTab != null) resultsTab.setDisable(locked);
            if (addTaskBtn != null) addTaskBtn.setDisable(locked);
            // Keep task chain visible so the green running border stays readable.
            // Interaction is blocked per-control inside createTaskCard via editorLocked.
            if (taskChainListBox != null) {
                refreshTaskChain();
            }
            if (databankPanel != null) databankPanel.setLocked(locked);
            if (automaticModeToggle != null) automaticModeToggle.setDisable(locked);
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
}
