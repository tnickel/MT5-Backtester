package com.backtester.ui.javafx;

import com.backtester.config.AppConfig;
import com.backtester.database.DatabaseManager;
import com.backtester.engine.WorkflowEngine;
import com.backtester.report.OptimizationResult.CombinedPass;
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

    private final BorderPane root;
    private CustomProject project;
    private final WorkflowEngine engine;
    private final DatabankManager databankManager;
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
    private Label currentTaskSettingsHeader;

    // Bottom Fixed Databank Panel Components
    private TabPane bottomDatabankTabPane;
    private CheckBox persistDatabanksCheckBox;

    // Execution state
    private Task<Void> activeProjectTask;

    public ProjectWorkflowEditorView() {
        this.engine = new WorkflowEngine(AppConfig.getInstance());
        this.databankManager = new DatabankManager();

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
        this.project = proj;
        if (proj != null) {
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
                    WorkflowTask selTask = new WorkflowTask("1. Strategie-Auswahl", WorkflowTask.TaskType.STRATEGY_SELECTION);
                    proj.getTasks().add(0, selTask);
                    for (int i = 0; i < proj.getTasks().size(); i++) {
                        WorkflowTask t = proj.getTasks().get(i);
                        String cleanName = t.getName().replaceAll("^\\d+\\.\\s*", "");
                        t.setName((i + 1) + ". " + cleanName);
                    }
                    saveProject();
                }
            }

            if (proj.getExpert() != null && !proj.getExpert().isEmpty()) {
                engine.changeExpert(proj.getExpert());
                engine.setSymbol(proj.getSymbol());
                engine.setPeriod(proj.getPeriod());
            }
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
            if (onBackToOverviewCallback != null) {
                onBackToOverviewCallback.run();
            }
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
        saveBtn.setOnAction(e -> saveProject());

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

        // Sub-Tab 3: What to retest / Databanks
        retestSubTab = new Tab("What to retest");
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
        box.getChildren().addAll(currentTaskSettingsHeader, fullSettingsSubTabPane);

        return box;
    }

    private VBox createWhatToRetestSubTab() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color: rgba(26, 30, 40, 0.85); -fx-border-color: #2e3545; -fx-border-radius: 6;");

        Label heading = new Label("What to retest / Databank routing settings");
        heading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        heading.setTextFill(Color.web("#00e5ff"));

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);

        grid.add(new Label("Retest all strategies from databank:"), 0, 0);
        sourceDatabankCombo = new ComboBox<>(FXCollections.observableArrayList("Results", "Existing portfolio", "Final"));
        sourceDatabankCombo.setValue("Results");
        sourceDatabankCombo.setOnAction(e -> {
            if (selectedTask != null) selectedTask.setSourceDatabank(sourceDatabankCombo.getValue());
        });
        grid.add(sourceDatabankCombo, 1, 0);

        grid.add(new Label("and store results in databank:"), 0, 1);
        targetDatabankCombo = new ComboBox<>(FXCollections.observableArrayList("Results", "Existing portfolio", "Final"));
        targetDatabankCombo.setValue("Results");
        targetDatabankCombo.setOnAction(e -> {
            if (selectedTask != null) selectedTask.setTargetDatabank(targetDatabankCombo.getValue());
        });
        grid.add(targetDatabankCombo, 1, 1);

        Label helpText = new Label(
            "If you choose a different databank to store the retested results, the strategies will be copied to the " +
            "destination databank and the original strategies with their existing results will remain in the source databank."
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

        Label heading = new Label("Backtest Data Settings (OOS / Retest Period)");
        heading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        heading.setTextFill(Color.web("#00e5ff"));

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);

        grid.add(new Label("Symbol:"), 0, 0);
        symbolCombo = new ComboBox<>(FXCollections.observableArrayList("EURUSD", "GBPUSD", "USDJPY", "AUDCAD", "XAUUSD", "GBPJPY_M1_dukas"));
        symbolCombo.setValue("EURUSD");
        symbolCombo.setOnAction(e -> {
            if (selectedTask != null) selectedTask.setRetestSymbol(symbolCombo.getValue());
        });
        grid.add(symbolCombo, 1, 0);

        grid.add(new Label("Timeframe:"), 0, 1);
        timeframeCombo = new ComboBox<>(FXCollections.observableArrayList("M1", "M5", "M15", "M30", "H1", "H4", "D1"));
        timeframeCombo.setValue("H1");
        timeframeCombo.setOnAction(e -> {
            if (selectedTask != null) selectedTask.setRetestPeriod(timeframeCombo.getValue());
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
                int mode = 1;
                String val = execModeCombo.getValue();
                if (val.contains("Every Tick (Ticksimulation)")) mode = 0;
                else if (val.contains("OHLC M1")) mode = 1;
                else if (val.contains("Real Ticks")) mode = 2;
                else if (val.contains("Open Prices")) mode = 3;
                selectedTask.setExecutionMode(mode);
                saveProject();
            }
        });
        grid.add(execModeCombo, 1, 2);

        grid.add(new Label("Start day (OOS From):"), 0, 3);
        startDatePicker = new DatePicker(LocalDate.now().minusYears(2));
        startDatePicker.setOnAction(e -> {
            if (selectedTask != null && startDatePicker.getValue() != null) {
                selectedTask.setStartDate(startDatePicker.getValue().toString());
            }
        });
        grid.add(startDatePicker, 1, 3);

        grid.add(new Label("End day (OOS To):"), 0, 4);
        endDatePicker = new DatePicker(LocalDate.now());
        endDatePicker.setOnAction(e -> {
            if (selectedTask != null && endDatePicker.getValue() != null) {
                selectedTask.setEndDate(endDatePicker.getValue().toString());
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
            if (selectedTask != null) selectedTask.setDeleteFailed(deleteFailedCheckBox.isSelected());
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
            e.getRowValue().setMetric(e.getNewValue());
            saveProject();
        });
        metricCol.setPrefWidth(260);

        TableColumn<FilterCondition, FilterCondition.Operator> opCol = new TableColumn<>("<=>");
        opCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getOperator()));
        opCol.setCellFactory(ComboBoxTableCell.forTableColumn(new javafx.util.StringConverter<FilterCondition.Operator>() {
            @Override public String toString(FilterCondition.Operator object) { return object != null ? object.getSymbol() + " (" + object.getLabel() + ")" : ""; }
            @Override public FilterCondition.Operator fromString(String string) { return FilterCondition.Operator.valueOf(string); }
        }, FilterCondition.Operator.values()));
        opCol.setOnEditCommit(e -> {
            e.getRowValue().setOperator(e.getNewValue());
            saveProject();
        });
        opCol.setPrefWidth(160);

        TableColumn<FilterCondition, Double> valCol = new TableColumn<>("Value");
        valCol.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().getValue()).asObject());
        valCol.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        valCol.setOnEditCommit(e -> {
            e.getRowValue().setValue(e.getNewValue());
            saveProject();
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

        Label heading = new Label("Diversitäts-Filter & Korrelations-Schwellenwerte");
        heading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        heading.setTextFill(Color.web("#00e5ff"));

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);

        grid.add(new Label("Param Differenz %:"), 0, 0);
        TextField paramDiffField = new TextField(String.format(Locale.US, "%.0f", engine.getParamDiffPct() * 100));
        grid.add(paramDiffField, 1, 0);

        grid.add(new Label("Trades Differenz %:"), 2, 0);
        TextField tradeDiffField = new TextField(String.format(Locale.US, "%.0f", engine.getTradeDiffPct() * 100));
        grid.add(tradeDiffField, 3, 0);

        grid.add(new Label("Min. differente Params:"), 0, 1);
        Spinner<Integer> minDiffParamsSpin = new Spinner<>(1, 10, engine.getMinDifferentParams(), 1);
        grid.add(minDiffParamsSpin, 1, 1);

        grid.add(new Label("Max. Strategien (Ziel):"), 2, 1);
        Spinner<Integer> maxStratsSpin = new Spinner<>(1, 20, engine.getMaxStrategiesToSelect(), 1);
        grid.add(maxStratsSpin, 3, 1);

        Button openFullDialogBtn = new Button("⚙ Vollständigen Diversitäts- & Filtereinstellungs-Dialog öffnen");
        openFullDialogBtn.getStyleClass().add("button-start");
        openFullDialogBtn.setOnAction(e -> {
            WorkflowConfigDialogs.showStep3Dialog(engine, root.getScene().getWindow());
        });

        panel.getChildren().addAll(heading, grid, new Separator(), openFullDialogBtn);
        return panel;
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
        TextField expertField = new TextField(engine.getExpert() != null ? engine.getExpert() : "");
        expertField.setPrefWidth(280);
        expertField.textProperty().addListener((obs, oldV, newV) -> engine.setExpert(newV));

        Button browseBtn = new Button("📁 Durchsuchen");
        browseBtn.getStyleClass().add("button");
        browseBtn.setOnAction(e -> {
            javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
            chooser.setTitle("Select Expert Advisor");
            chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("MetaTrader EA", "*.ex5", "*.ex4"));
            java.io.File selected = chooser.showOpenDialog(root.getScene().getWindow());
            if (selected != null) {
                String path = selected.getName();
                if (path.toLowerCase().endsWith(".ex5")) {
                    path = path.substring(0, path.length() - 4);
                }
                engine.changeExpert(path);
                expertField.setText(path);
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
        panel.setMinHeight(220);

        // Databank Header Toolbar
        HBox bar = new HBox(15);
        bar.setAlignment(Pos.CENTER_LEFT);

        Button newDatabankBtn = new Button("+ New databank");
        newDatabankBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #00e5ff; -fx-font-weight: bold; -fx-cursor: hand;");
        newDatabankBtn.setOnAction(e -> promptCreateNewDatabank());

        Button clearCurrentDbBtn = new Button("🧹 Strategien in Databank leeren");
        clearCurrentDbBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ffab40; -fx-font-weight: bold; -fx-cursor: hand;");
        clearCurrentDbBtn.setOnAction(e -> {
            Tab activeTab = bottomDatabankTabPane.getSelectionModel().getSelectedItem();
            if (activeTab != null) {
                String dbName = activeTab.getText().replaceAll("\\s*\\(\\d+\\)$", "");
                databankManager.clearDatabank(dbName);
                saveProject();
                refreshDatabanksUI(dbName);
                logToConsole("DATABANK", "Alle Strategien aus Databank '" + dbName + "' wurden geleert.");
            }
        });

        Button clearAllBtn = new Button("Clear all databanks");
        clearAllBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ff5252; -fx-font-weight: bold; -fx-cursor: hand;");
        clearAllBtn.setOnAction(e -> {
            databankManager.clearAll();
            saveProject();
            refreshDatabanksUI();
        });

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

        bar.getChildren().addAll(newDatabankBtn, clearCurrentDbBtn, clearAllBtn, deleteSelectedStratsBtn, configColumnsBtn, persistDatabanksCheckBox);

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

        databankManager.removeDatabank(dbName);
        saveProject();
        updateDatabankComboBoxes();
        refreshDatabanksUI("Results");
        logToConsole("DATABANK", "Databank '" + dbName + "' wurde gelöscht.");
    }

    private void deleteSelectedRowsFromDatabank(String dbName, TableView<CombinedPass> table) {
        if (table == null) return;
        List<CombinedPass> selected = new ArrayList<>(table.getSelectionModel().getSelectedItems());
        if (selected == null || selected.isEmpty()) return;

        List<CombinedPass> dbList = databankManager.getDatabank(dbName);
        dbList.removeAll(selected);
        saveProject();
        refreshDatabanksUI(dbName);
        logToConsole("DATABANK", selected.size() + " Strategie(n) aus Databank '" + dbName + "' gelöscht.");
    }

    private void updateDatabankComboBoxes() {
        List<String> names = databankManager.getDatabankNames();
        if (sourceDatabankCombo != null) {
            String currSrc = sourceDatabankCombo.getValue();
            sourceDatabankCombo.getItems().setAll(names);
            sourceDatabankCombo.setValue(currSrc != null && names.contains(currSrc) ? currSrc : (names.isEmpty() ? "Results" : names.get(0)));
        }
        if (targetDatabankCombo != null) {
            String currTgt = targetDatabankCombo.getValue();
            targetDatabankCombo.getItems().setAll(names);
            targetDatabankCombo.setValue(currTgt != null && names.contains(currTgt) ? currTgt : (names.isEmpty() ? "Results" : names.get(0)));
        }
        if (rankingSourceCombo != null) {
            String currSrc = rankingSourceCombo.getValue();
            rankingSourceCombo.getItems().setAll(names);
            rankingSourceCombo.setValue(currSrc != null && names.contains(currSrc) ? currSrc : (names.isEmpty() ? "Results" : names.get(0)));
        }
        if (rankingTargetCombo != null) {
            String currTgt = rankingTargetCombo.getValue();
            rankingTargetCombo.getItems().setAll(names);
            rankingTargetCombo.setValue(currTgt != null && names.contains(currTgt) ? currTgt : (names.isEmpty() ? "Results" : names.get(0)));
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
                MenuItem inspectItem = new MenuItem("🔍 Details anzeigen (Doppelklick)");
                inspectItem.setOnAction(e -> {
                    if (!row.isEmpty()) StrategyDetailsModalDialog.show(row.getItem(), root.getScene().getWindow());
                });
                MenuItem deleteItem = new MenuItem("🗑 Selektierte Strategie(n) löschen (Entf)");
                deleteItem.setOnAction(e -> deleteSelectedRowsFromDatabank(dbName, table));
                contextMenu.getItems().addAll(inspectItem, deleteItem);

                row.setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2 && (!row.isEmpty())) {
                        CombinedPass rowData = row.getItem();
                        StrategyDetailsModalDialog.show(rowData, root.getScene().getWindow());
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

        Button configBtn = new Button("⚙");
        configBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ffd740; -fx-padding: 2; -fx-cursor: hand;");
        configBtn.setTooltip(new Tooltip("Task-Einstellungen in der Mitte öffnen"));
        configBtn.setOnAction(e -> {
            e.consume();
            selectTask(task);
            centerMainTabPane.getSelectionModel().select(fullSettingsTab);
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

    private void selectTask(WorkflowTask task) {
        this.selectedTask = task;
        refreshTaskChain();
        updateDatabankComboBoxes();
        if (task != null) {
            currentTaskSettingsHeader.setText("Advanced settings for '" + task.getName() + "'");
            String src = task.getSourceDatabank() != null ? task.getSourceDatabank() : "Results";
            String tgt = task.getTargetDatabank() != null ? task.getTargetDatabank() : "Results";

            if (sourceDatabankCombo != null) sourceDatabankCombo.setValue(src);
            if (targetDatabankCombo != null) targetDatabankCombo.setValue(tgt);
            if (rankingSourceCombo != null) rankingSourceCombo.setValue(src);
            if (rankingTargetCombo != null) rankingTargetCombo.setValue(tgt);
            deleteFailedCheckBox.setSelected(task.isDeleteFailed());
            filterConditionsTable.getItems().setAll(task.getFilterConditions());

            if (execModeCombo != null) {
                switch (task.getExecutionMode()) {
                    case 0: execModeCombo.setValue("Every Tick (Ticksimulation)"); break;
                    case 2: execModeCombo.setValue("Every Tick based on Real Ticks (Realtick)"); break;
                    case 3: execModeCombo.setValue("Open Prices Only"); break;
                    default: execModeCombo.setValue("OHLC M1 (Every tick based on OHLC M1)"); break;
                }
            }
            if (symbolCombo != null && task.getRetestSymbol() != null && !task.getRetestSymbol().isEmpty()) {
                symbolCombo.setValue(task.getRetestSymbol());
            }
            if (timeframeCombo != null && task.getRetestPeriod() != null && !task.getRetestPeriod().isEmpty()) {
                timeframeCombo.setValue(task.getRetestPeriod());
            }
            if (startDatePicker != null && task.getStartDate() != null && !task.getStartDate().isEmpty()) {
                try { startDatePicker.setValue(LocalDate.parse(task.getStartDate())); } catch (Exception ignored) {}
            }
            if (endDatePicker != null && task.getEndDate() != null && !task.getEndDate().isEmpty()) {
                try { endDatePicker.setValue(LocalDate.parse(task.getEndDate())); } catch (Exception ignored) {}
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
                case LONGTERM_RETEST:
                    if (retestSubTab != null) fullSettingsSubTabPane.getTabs().add(retestSubTab);
                    if (dataSubTab != null) fullSettingsSubTabPane.getTabs().add(dataSubTab);
                    if (rankingSubTab != null) fullSettingsSubTabPane.getTabs().add(rankingSubTab);
                    break;
                case DIVERSITY_FILTER:
                    if (diversitySubTab != null) fullSettingsSubTabPane.getTabs().add(diversitySubTab);
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
            fullSettingsSubTabPane.getTabs().clear();
        }
    }

    // ─── Execution Logic ──────────────────────────────────────────────────────

    private void runSingleTask(WorkflowTask task) {
        if (task == null) return;

        startBtn.setDisable(true);
        stopBtn.setDisable(false);
        resetBtn.setDisable(true);
        consoleLog.clear();

        centerMainTabPane.getSelectionModel().select(progressTab);

        logToConsole("SINGLE-STEP", "=== STARTE EINZELTEST FÜR TASK: " + task.getName() + " ===");

        activeProjectTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                task.setStatus(WorkflowTask.TaskStatus.RUNNING);
                Platform.runLater(() -> {
                    refreshTaskChain();
                    progressBar.setProgress(0.5);
                    progressLabel.setText("Führe Einzelstep aus: " + task.getName());
                });

                List<CombinedPass> inputPasses = databankManager.getDatabank(task.getSourceDatabank());
                List<CombinedPass> outputPasses = new ArrayList<>();

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
                    case LONGTERM_RETEST:
                        engine.setTickModel(task.getExecutionMode());
                        if (task.getRetestSymbol() != null && !task.getRetestSymbol().isEmpty()) engine.setSymbol(task.getRetestSymbol());
                        if (task.getRetestPeriod() != null && !task.getRetestPeriod().isEmpty()) engine.setPeriod(task.getRetestPeriod());
                        if (task.getStartDate() != null && !task.getStartDate().isEmpty()) {
                            try { engine.setLongtermFromDate(LocalDate.parse(task.getStartDate())); } catch (Exception ignored) {}
                        }
                        if (task.getEndDate() != null && !task.getEndDate().isEmpty()) {
                            try { engine.setLongtermToDate(LocalDate.parse(task.getEndDate())); } catch (Exception ignored) {}
                        }
                        outputPasses = engine.runLongtermTest(
                            msg -> logToConsole("RETEST", msg),
                            pct -> updateProgressUI((double) pct / 100.0, "Retest " + pct + "%")
                        );
                        break;
                    case PRE_FILTER:
                    case DIVERSITY_FILTER:
                        outputPasses = new ArrayList<>(inputPasses);
                        break;
                    case ROBUSTNESS_CV:
                        engine.runStep4(
                            msg -> logToConsole("STRESS", msg),
                            pct -> updateProgressUI((double) pct / 100.0, "Robustness " + pct + "%")
                        );
                        outputPasses = engine.getSelectedDiversePasses();
                        break;
                    case KI_EVALUATION:
                        engine.runStep5(msg -> logToConsole("KI-EVAL", msg));
                        outputPasses = engine.getSelectedDiversePasses();
                        break;
                    case PORTFOLIO_EXPORT:
                        outputPasses = engine.runStep6();
                        break;
                    case OOS_VALIDATION:
                        if (engine.hasUsableValidationWindow(14)) {
                            engine.runStep7(
                                msg -> logToConsole("VALIDIERUNG", msg),
                                (curr, totV) -> updateProgressUI((double) curr / Math.max(1, totV), "Validierung " + curr + " / " + totV)
                            );
                        }
                        outputPasses = engine.getFinalSelectedPasses();
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
            protected void failed() { cleanupExecutionState(); }
            @Override
            protected void cancelled() { cleanupExecutionState(); }
        };

        Thread t = new Thread(activeProjectTask);
        t.setDaemon(true);
        t.start();
    }

    private void startProjectExecution() {
        if (project == null || project.getTasks().isEmpty()) return;

        startBtn.setDisable(true);
        stopBtn.setDisable(false);
        resetBtn.setDisable(true);
        consoleLog.clear();

        centerMainTabPane.getSelectionModel().select(progressTab);

        logToConsole("PROJECT", "=== STARTE CUSTOM PROJECT: " + project.getName() + " ===");

        activeProjectTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
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
                            case LONGTERM_RETEST:
                                engine.setTickModel(task.getExecutionMode());
                                if (task.getRetestSymbol() != null && !task.getRetestSymbol().isEmpty()) engine.setSymbol(task.getRetestSymbol());
                                if (task.getRetestPeriod() != null && !task.getRetestPeriod().isEmpty()) engine.setPeriod(task.getRetestPeriod());
                                if (task.getStartDate() != null && !task.getStartDate().isEmpty()) {
                                    try { engine.setLongtermFromDate(LocalDate.parse(task.getStartDate())); } catch (Exception ignored) {}
                                }
                                if (task.getEndDate() != null && !task.getEndDate().isEmpty()) {
                                    try { engine.setLongtermToDate(LocalDate.parse(task.getEndDate())); } catch (Exception ignored) {}
                                }
                                currentPipelinePasses = engine.runLongtermTest(
                                    msg -> logToConsole("LANGZEITTEST", msg),
                                    pct -> updateProgressUI((double) currentIdx / total, "Langzeittest " + pct + "%")
                                );
                                break;
                            case PRE_FILTER:
                            case DIVERSITY_FILTER:
                                List<CombinedPass> srcList = databankManager.getDatabank(task.getSourceDatabank());
                                currentPipelinePasses = new ArrayList<>(srcList.isEmpty() ? currentPipelinePasses : srcList);
                                break;
                            case ROBUSTNESS_CV:
                                engine.runStep4(
                                    msg -> logToConsole("STRESS", msg),
                                    pct -> updateProgressUI((double) currentIdx / total, "Robustness " + pct + "%")
                                );
                                currentPipelinePasses = engine.getSelectedDiversePasses();
                                break;
                            case KI_EVALUATION:
                                engine.runStep5(msg -> logToConsole("KI-EVAL", msg));
                                currentPipelinePasses = engine.getSelectedDiversePasses();
                                break;
                            case PORTFOLIO_EXPORT:
                                currentPipelinePasses = engine.runStep6();
                                break;
                            case OOS_VALIDATION:
                                if (engine.hasUsableValidationWindow(14)) {
                                    engine.runStep7(
                                        msg -> logToConsole("VALIDIERUNG", msg),
                                        (curr, totV) -> updateProgressUI((double) currentIdx / total, "Validierung " + curr + " / " + totV)
                                    );
                                }
                                currentPipelinePasses = engine.getFinalSelectedPasses();
                                break;
                            default:
                                break;
                        }

                        // Databank processing & filtering
                        List<CombinedPass> processed = databankManager.processTaskDatabanks(task, currentPipelinePasses);
                        task.setOutputPasses(processed);
                        task.setStatus(WorkflowTask.TaskStatus.COMPLETED);

                        logToConsole("PROJECT", "=== TASK " + (i + 1) + " FERTIG. Databank '" + task.getTargetDatabank() + "' enthält " + processed.size() + " Strategien ===");
                        Platform.runLater(() -> refreshDatabanksUI());
                    } catch (Exception ex) {
                        task.setStatus(WorkflowTask.TaskStatus.FAILED);
                        logToConsole("ERROR", "Fehler in Task " + (i + 1) + ": " + ex.getMessage());
                        throw ex;
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
                cleanupExecutionState();
            }

            @Override
            protected void cancelled() {
                cleanupExecutionState();
            }
        };

        Thread t = new Thread(activeProjectTask);
        t.setDaemon(true);
        t.start();
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
        startBtn.setDisable(false);
        stopBtn.setDisable(true);
        resetBtn.setDisable(false);
        activeProjectTask = null;
        saveProject();
        Platform.runLater(() -> {
            refreshTaskChain();
            String focusDb = selectedTask != null ? selectedTask.getTargetDatabank() : null;
            refreshDatabanksUI(focusDb);
        });
    }

    private void saveProject() {
        if (project != null) {
            if (project.isSaveDatabanksPersistently()) {
                databankManager.saveToProject(project);
            } else {
                project.getDatabanks().clear();
            }
            DatabaseManager.getInstance().saveCustomProject(project);
        }
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
