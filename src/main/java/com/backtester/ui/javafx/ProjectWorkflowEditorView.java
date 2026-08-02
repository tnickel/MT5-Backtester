package com.backtester.ui.javafx;

import com.backtester.config.AppConfig;
import com.backtester.database.CustomProjectSaveCoordinator;
import com.backtester.database.DatabaseManager;
import com.backtester.engine.WorkflowEngine;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.ValidationResult;
import com.backtester.workflow.CustomProject;
import com.backtester.workflow.DatabankManager;
import com.backtester.workflow.FilterCondition;
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
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.converter.DoubleStringConverter;

import java.time.LocalDate;
import java.time.Duration;
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
    private ComboBox<String> rankingSourceCombo;
    private ComboBox<String> rankingTargetCombo;
    private DatePicker startDatePicker;
    private DatePicker endDatePicker;
    private ComboBox<String> timeframeCombo;
    private ComboBox<String> symbolCombo;
    private ComboBox<String> execModeCombo;
    private CheckBox deleteFailedCheckBox;
    private TableView<FilterCondition> filterConditionsTable;
    private TextField expertField;
    private TextField taskNameField;
    private TextField diversityParamDiffField;
    private TextField diversityTradeDiffField;
    private Spinner<Integer> diversityMinDiffParamsSpinner;
    private Spinner<Integer> diversityMaxStrategiesSpinner;
    private boolean updatingDiversityControls;
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

        // Left Panel: Task Chain Builder (Width ~ 300px)
        VBox leftTaskPanel = createLeftTaskPanel();

        // Center Main Tabs (Progress | Full settings | Results)
        centerMainTabPane = createCenterMainTabPane();

        centerSplit.getItems().addAll(leftTaskPanel, centerMainTabPane);
        centerSplit.setDividerPositions(0.28);

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

        bar.getChildren().addAll(backBtn, projectTitleLabel, spacer, startBtn, stopBtn, resetBtn, saveBtn);
        return bar;
    }

    private VBox createLeftTaskPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        panel.getStyleClass().add("sci-fi-panel");
        panel.setMinWidth(280);

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

        fullSettingsSubTabPane.getTabs().addAll(
            strategySelectionTab, optimizerSettingsTab, retestSubTab, dataSubTab, rankingSubTab, diversitySubTab
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

        Label heading = new Label("Backtest Data Settings (Retester)");
        heading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        heading.setTextFill(Color.web("#00e5ff"));

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);

        grid.add(new Label("Symbol:"), 0, 0);
        symbolCombo = new ComboBox<>(FXCollections.observableArrayList("EURUSD", "GBPUSD", "USDJPY", "AUDCAD", "XAUUSD", "GBPJPY_M1_dukas"));
        symbolCombo.setValue("EURUSD");
        symbolCombo.setOnAction(e -> {
            if (selectedTask != null && symbolCombo.getValue() != null) {
                selectedTask.setRetestSymbol(symbolCombo.getValue());
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
            if (selectedTask != null && startDatePicker.getValue() != null) {
                selectedTask.setStartDate(startDatePicker.getValue().toString());
                saveProject();
            }
        });
        grid.add(startDatePicker, 1, 3);

        grid.add(new Label("End day (Test To):"), 0, 4);
        endDatePicker = new DatePicker();
        endDatePicker.setOnAction(e -> {
            if (selectedTask != null && endDatePicker.getValue() != null) {
                selectedTask.setEndDate(endDatePicker.getValue().toString());
                saveProject();
            }
        });
        grid.add(endDatePicker, 1, 4);

        panel.getChildren().addAll(heading, grid);
        return panel;
    }

    private ScrollPane createRankingSubTab() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color: rgba(26, 30, 40, 0.85); -fx-border-color: #2e3545; -fx-border-radius: 6;");

        Label heading = new Label("Strategy Quality Ranking & Filtering Conditions");
        heading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        heading.setTextFill(Color.web("#00e5ff"));

        GridPane routeGrid = new GridPane();
        routeGrid.setHgap(15);
        routeGrid.setVgap(10);
        routeGrid.setPadding(new Insets(10));
        routeGrid.setStyle("-fx-background-color: rgba(11, 13, 19, 0.6); -fx-border-color: #2e3545; -fx-border-radius: 4;");

        routeGrid.add(new Label("Strategien lesen aus Databank:"), 0, 0);
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

        routeGrid.add(new Label("Gefilterte Ergebnisse speichern in Databank:"), 0, 1);
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

        panel.getChildren().addAll(heading, routeGrid, deleteFailedCheckBox, new Separator(), filterConditionsTable, btnBox);

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

        Label heading = new Label("Diversitäts-Clustering der ausgewählten Quell-Databank");
        heading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        heading.setTextFill(Color.web("#00e5ff"));

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

        panel.getChildren().addAll(heading, grid, new Separator(), sourceInfo);
        return panel;
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
        ComboBox<String> algoCombo = new ComboBox<>(FXCollections.observableArrayList(
            "Slow Complete Algorithm", "Fast Genetic Algorithm", "All symbols in Market Watch"
        ));
        int currAlgo = engine.getOptimizationMode();
        algoCombo.setValue(currAlgo >= 0 && currAlgo < algoCombo.getItems().size() ? algoCombo.getItems().get(currAlgo) : "Fast Genetic Algorithm");
        algoCombo.setOnAction(e -> engine.setOptimizationMode(algoCombo.getSelectionModel().getSelectedIndex()));
        grid.add(algoCombo, 1, 0);

        grid.add(new Label("Optimierungsziel:"), 0, 1);
        ComboBox<String> critCombo = new ComboBox<>(FXCollections.observableArrayList(
            "Balance max", "Profit Factor max", "Expected Payoff max", "Drawdown min", "Recovery Factor max", "Sharpe Ratio max", "Custom max"
        ));
        int currCrit = engine.getOptimizationCriterion();
        critCombo.setValue(currCrit >= 0 && currCrit < critCombo.getItems().size() ? critCombo.getItems().get(currCrit) : "Recovery Factor max");
        critCombo.setOnAction(e -> engine.setOptimizationCriterion(critCombo.getSelectionModel().getSelectedIndex()));
        grid.add(critCombo, 1, 1);

        grid.add(new Label("Forward-Test:"), 0, 2);
        ComboBox<String> fwdCombo = new ComboBox<>(FXCollections.observableArrayList(
            "No", "1/2 period", "1/3 period", "1/4 period", "Custom Date"
        ));
        int currFwd = engine.getForwardMode();
        fwdCombo.setValue(currFwd >= 0 && currFwd < fwdCombo.getItems().size() ? fwdCombo.getItems().get(currFwd) : "1/2 period");
        fwdCombo.setOnAction(e -> engine.setForwardMode(fwdCombo.getSelectionModel().getSelectedIndex()));
        grid.add(fwdCombo, 1, 2);

        grid.add(new Label("Forward Datum:"), 0, 3);
        DatePicker fwdDatePicker = new DatePicker(engine.getForwardDate() != null ? engine.getForwardDate() : LocalDate.now().minusMonths(2));
        fwdDatePicker.setOnAction(e -> { if (fwdDatePicker.getValue() != null) engine.setForwardDate(fwdDatePicker.getValue()); });
        grid.add(fwdDatePicker, 1, 3);

        HBox btnBox = new HBox(12);
        Button openStep1Btn = new Button("⚙ EA Parameter & Suchräume konfigurieren");
        openStep1Btn.getStyleClass().add("button");
        openStep1Btn.setOnAction(e -> {
            WorkflowConfigDialogs.showStep1Dialog(engine, root.getScene().getWindow());
        });

        Button openStep2Btn = new Button("⚙ Vollständigen Optimizer-Dialog öffnen");
        openStep2Btn.getStyleClass().add("button-start");
        openStep2Btn.setOnAction(e -> {
            WorkflowConfigDialogs.showStep2Dialog(engine, root.getScene().getWindow());
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
                saveProject();
                refreshDatabanksUI(dbName);
                logToConsole("DATABANK", "Alle Strategien aus Databank '" + dbName + "' wurden geleert.");
            }
        });

        Button clearAllBtn = new Button("Clear all databanks");
        clearAllBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ff5252; -fx-font-weight: bold; -fx-cursor: hand;");
        clearAllBtn.setOnAction(e -> {
            if (!confirmDestructiveAction("Alle Databanken leeren",
                    "Alle Strategien aus allen Databanken entfernen? (Die Databank-Tabs bleiben erhalten)")) return;
            databankManager.clearAll();
            saveProject();
            refreshDatabanksUI();
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

        bar.getChildren().addAll(newDatabankBtn, clearCurrentDbBtn, clearAllBtn, deleteDatabankBtn,
                deleteSelectedStratsBtn, configColumnsBtn, persistDatabanksCheckBox);

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
        saveProject();
        updateDatabankComboBoxes();
        refreshDatabanksUI("Results");
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
        saveProject();
        refreshDatabanksUI(dbName);
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

            // Context Menu & Row click handlers
            table.setRowFactory(tv -> {
                TableRow<CombinedPass> row = new TableRow<>();

                ContextMenu contextMenu = new ContextMenu();
                MenuItem inspectItem = new MenuItem("🔍 Details & EA Parameter anzeigen (Doppelklick)");
                inspectItem.setOnAction(e -> {
                    if (!row.isEmpty()) StrategyDetailsModalDialog.show(row.getItem(), root.getScene().getWindow(), 0);
                });

                MenuItem sensitivityItem = new MenuItem("📈 Sensitivitäts-Kennlinien & Stresstest (Rechtsklick)");
                sensitivityItem.setOnAction(e -> {
                    if (!row.isEmpty()) StrategyDetailsModalDialog.show(row.getItem(), root.getScene().getWindow(), 3);
                });

                MenuItem htmlReportItem = new MenuItem("🌐 HTML Robustness Scanner Report im Browser öffnen");
                htmlReportItem.setOnAction(e -> {
                    if (!row.isEmpty()) StrategyDetailsModalDialog.openRobustnessHtmlReport(row.getItem());
                });

                MenuItem deleteItem = new MenuItem("🗑 Selektierte Strategie(n) löschen (Entf)");
                deleteItem.setOnAction(e -> deleteSelectedRowsFromDatabank(dbName, table));

                contextMenu.getItems().addAll(inspectItem, sensitivityItem, htmlReportItem, new SeparatorMenuItem(), deleteItem);

                row.setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2 && (!row.isEmpty())) {
                        CombinedPass rowData = row.getItem();
                        StrategyDetailsModalDialog.show(rowData, root.getScene().getWindow(), 0);
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
        card.getChildren().addAll(topRow, subRow, actionsRow);
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
                    if (retestSubTab != null) fullSettingsSubTabPane.getTabs().add(retestSubTab);
                    if (rankingSubTab != null) fullSettingsSubTabPane.getTabs().add(rankingSubTab);
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
        String taskSymbol = task.getRetestSymbol();
        String taskPeriod = task.getRetestPeriod();
        engine.setSymbol(taskSymbol != null && !taskSymbol.isBlank()
                ? taskSymbol.trim() : (project != null ? project.getSymbol() : engine.getSymbol()));
        engine.setPeriod(taskPeriod != null && !taskPeriod.isBlank()
                ? taskPeriod.trim() : (project != null ? project.getPeriod() : engine.getPeriod()));

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
                System.err.println("Ungültiges Startdatum-Format im Task: " + startText);
            }
        }
        if (hasEnd) {
            try {
                end = LocalDate.parse(endText.trim());
            } catch (Exception ex) {
                System.err.println("Ungültiges Enddatum-Format im Task: " + endText);
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
            end = start.plusYears(1);
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

    private List<CombinedPass> exportPortfolioCandidates(List<CombinedPass> inputPasses) {
        if (inputPasses == null || inputPasses.isEmpty()) {
            throw new IllegalStateException("Keine Strategien für den Portfolio-Export vorhanden.");
        }
        boolean allPassedOos = true;
        for (CombinedPass pass : inputPasses) {
            ValidationResult result = pass != null ? engine.getValidationResultForPass(pass.getPassNumber()) : null;
            if (result == null || !result.isPassed()) {
                allPassedOos = false;
                break;
            }
        }

        List<CombinedPass> exportPasses;
        if (allPassedOos) {
            exportPasses = new ArrayList<>(inputPasses);
            engine.setFinalSelectedPasses(exportPasses);
        } else {
            exportPasses = engine.selectFinalPasses(inputPasses);
        }
        engine.exportPortfolio(AppConfig.getInstance().getExportDirectory().toString());
        engine.saveWorkflowToHistory();
        return exportPasses;
    }

    // ─── Execution Logic ──────────────────────────────────────────────────────

    private void runSingleTask(WorkflowTask task) {
        if (task == null) return;

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
                            (curr, totPasses) -> updateProgressUI((double) curr / Math.max(1, totPasses), "Optimizer Pass " + curr + " / " + totPasses)
                        );
                        if (engine.getOptResult() != null) {
                            outputPasses = engine.getOptResult().buildCombinedPasses(engine.getForwardMode() > 0, engine.loadScoreWeightsFromDb());
                        }
                        break;
                    case RETESTER:
                        outputPasses = engine.runLongtermTest(
                            inputPasses,
                            msg -> logToConsole("RETESTER", msg),
                            pct -> updateProgressUI((double) pct / 100.0, task.getName() + " " + pct + "%")
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
                        engine.runStep4(
                            msg -> logToConsole("STRESS", msg),
                            pct -> updateProgressUI((double) pct / 100.0, "Robustness " + pct + "%")
                        );
                        outputPasses = new ArrayList<>(inputPasses);
                        break;
                    case KI_EVALUATION:
                        engine.setSelectedDiversePasses(inputPasses);
                        engine.retainSensitivityResultsForPasses(inputPasses);
                        engine.runStep5(msg -> logToConsole("KI-EVAL", msg));
                        outputPasses = new ArrayList<>(inputPasses);
                        break;
                    case PORTFOLIO_EXPORT:
                        outputPasses = exportPortfolioCandidates(inputPasses);
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
                                    (curr, totPasses) -> updateProgressUI((double) currentIdx / total, "Optimizer Pass " + curr + " / " + totPasses)
                                );
                                if (engine.getOptResult() != null) {
                                    currentPipelinePasses = engine.getOptResult().buildCombinedPasses(engine.getForwardMode() > 0, engine.loadScoreWeightsFromDb());
                                }
                                break;
                            case RETESTER:
                                currentPipelinePasses = engine.runLongtermTest(
                                    inputPasses,
                                    msg -> logToConsole("RETESTER", msg),
                                    pct -> updateProgressUI((double) currentIdx / total, task.getName() + " " + pct + "%")
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
                                engine.runStep4(
                                    msg -> logToConsole("STRESS", msg),
                                    pct -> updateProgressUI((double) currentIdx / total, "Robustness " + pct + "%")
                                );
                                currentPipelinePasses = new ArrayList<>(inputPasses);
                                break;
                            case KI_EVALUATION:
                                engine.setSelectedDiversePasses(inputPasses);
                                engine.retainSensitivityResultsForPasses(inputPasses);
                                engine.runStep5(msg -> logToConsole("KI-EVAL", msg));
                                currentPipelinePasses = new ArrayList<>(inputPasses);
                                break;
                            case PORTFOLIO_EXPORT:
                                currentPipelinePasses = exportPortfolioCandidates(inputPasses);
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
        for (WorkflowTask task : project.getTasks()) {
            if (task != null && task.isEnabled() && task.getType() == null) {
                throw new IllegalStateException("Ein aktivierter Task besitzt keinen gültigen Typ.");
            }
        }
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
            saveProject();
            refreshTaskChain();
            refreshDatabanksUI();
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

    private void saveProject() {
        if (project != null) {
            projectSaveCoordinator.requestSave(project, databankManager);
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

    private void updateProgressUI(double progress, String label) {
        Platform.runLater(() -> {
            progressBar.setProgress(progress);
            progressLabel.setText(label);
        });
    }
}
