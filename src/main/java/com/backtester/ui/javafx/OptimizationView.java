package com.backtester.ui.javafx;

import com.backtester.engine.OptimizationConfig;
import com.backtester.engine.OptimizationRunner;
import com.backtester.config.EaParameterManager;
import com.backtester.config.EaParameter;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.collections.FXCollections;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

public class OptimizationView {

    private BorderPane root;
    private final LogView logView;

    // Backend
    private final EaParameterManager eaParamManager = new EaParameterManager();
    private OptimizationRunner currentRunner;
    private Task<Void> currentTask;
    
    // UI controls
    private TextField expertField;
    private ComboBox<String> symbolCombo;
    private ComboBox<String> modeCombo;
    private ComboBox<String> periodCombo;
    private ComboBox<String> modelCombo;
    private DatePicker fromDatePicker;
    private DatePicker toDatePicker;
    private TextField depositField;
    private TextField currencyField;
    private TextField leverageField;
    private ComboBox<String> optimizationCriterionCombo;
    private ComboBox<String> forwardModeCombo;
    private DatePicker forwardDatePicker;
    private TableView<EaParameter> paramTable;
    private Button startBtn;
    private Button cancelBtn;
    private ProgressBar progressBar;
    private Label progressLabel;
    
    public OptimizationView(LogView logView) {
        this.logView = logView;
        root = new BorderPane();
        root.setPadding(new Insets(15));

        // Top Split: Config vs Parameters
        GridPane configGrid = createConfigGrid();
        VBox paramBox = createParamBox();

        HBox topBox = new HBox(15, configGrid, paramBox);
        HBox.setHgrow(paramBox, Priority.ALWAYS);

        // Bottom Split: Results
        VBox resultsBox = createResultsBox();

        VBox mainLayout = new VBox(15, topBox, resultsBox);
        VBox.setVgrow(resultsBox, Priority.ALWAYS);

        root.setCenter(mainLayout);
        
        loadPreferences();
    }

    private GridPane createConfigGrid() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("sci-fi-panel");
        grid.setHgap(10);
        grid.setVgap(10);
        
        Label title = new Label("Optimization Settings");
        title.getStyleClass().add("sci-fi-panel-title");
        grid.add(title, 0, 0, 3, 1);

        grid.add(new Label("Expert Advisor:"), 0, 1);
        expertField = new TextField();
        expertField.getStyleClass().add("text-input");
        grid.add(expertField, 1, 1);
        Button browseBtn = new Button("...");
        browseBtn.setOnAction(e -> browseEA());
        grid.add(browseBtn, 2, 1);

        grid.add(new Label("Symbol:"), 0, 2);
        symbolCombo = new ComboBox<>(FXCollections.observableArrayList(
            "EURUSD", "GBPUSD", "USDJPY", "AUDUSD", "USDCAD", "USDCHF", "NZDUSD"));
        symbolCombo.getStyleClass().add("combo-box");
        grid.add(symbolCombo, 1, 2, 2, 1);
        
        grid.add(new Label("Period:"), 0, 3);
        periodCombo = new ComboBox<>(FXCollections.observableArrayList(
            "M1", "M5", "M15", "M30", "H1", "H4", "D1"));
        periodCombo.getStyleClass().add("combo-box");
        grid.add(periodCombo, 1, 3, 2, 1);

        grid.add(new Label("Date Range:"), 0, 4);
        HBox dateBox = new HBox(5);
        fromDatePicker = new DatePicker();
        toDatePicker = new DatePicker();
        fromDatePicker = new DatePicker();
        toDatePicker = new DatePicker();
        fromDatePicker.setPrefWidth(150);
        toDatePicker.setPrefWidth(150);
        
        javafx.util.StringConverter<java.time.LocalDate> converter = new javafx.util.StringConverter<java.time.LocalDate>() {
            java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
            @Override
            public String toString(java.time.LocalDate date) {
                return (date != null) ? dateFormatter.format(date) : "";
            }
            @Override
            public java.time.LocalDate fromString(String string) {
                return (string != null && !string.isEmpty()) ? java.time.LocalDate.parse(string, dateFormatter) : null;
            }
        };
        fromDatePicker.setConverter(converter);
        toDatePicker.setConverter(converter);
        
        dateBox.getChildren().addAll(fromDatePicker, new Label(" - "), toDatePicker);
        grid.add(dateBox, 1, 4, 2, 1);
        
        grid.add(new Label("Deposit:"), 0, 5);
        HBox depBox = new HBox(5);
        depositField = new TextField("10000");
        currencyField = new TextField("USD");
        depositField.getStyleClass().add("text-input");
        currencyField.getStyleClass().add("text-input");
        depositField.setPrefWidth(100);
        currencyField.setPrefWidth(60);
        depBox.getChildren().addAll(depositField, currencyField);
        grid.add(depBox, 1, 5, 2, 1);
        
        grid.add(new Label("Leverage:"), 0, 6);
        leverageField = new TextField("1:100");
        leverageField.getStyleClass().add("text-input");
        grid.add(leverageField, 1, 6, 2, 1);

        grid.add(new Label("Tick Model:"), 0, 7);
        modelCombo = new ComboBox<>(FXCollections.observableArrayList(OptimizationConfig.MODEL_NAMES));
        modelCombo.getStyleClass().add("combo-box");
        grid.add(modelCombo, 1, 7, 2, 1);

        grid.add(new Label("Opt. Mode:"), 0, 8);
        modeCombo = new ComboBox<>(FXCollections.observableArrayList(OptimizationConfig.OPTIMIZATION_MODES));
        modeCombo.getStyleClass().add("combo-box");
        grid.add(modeCombo, 1, 8, 2, 1);
        
        grid.add(new Label("Opt. Criterion:"), 0, 9);
        optimizationCriterionCombo = new ComboBox<>(FXCollections.observableArrayList(OptimizationConfig.OPTIMIZATION_CRITERIA));
        optimizationCriterionCombo.getStyleClass().add("combo-box");
        grid.add(optimizationCriterionCombo, 1, 9, 2, 1);
        
        grid.add(new Label("Forward Test:"), 0, 10);
        forwardModeCombo = new ComboBox<>(FXCollections.observableArrayList(OptimizationConfig.FORWARD_MODES));
        forwardModeCombo.getStyleClass().add("combo-box");
        grid.add(forwardModeCombo, 1, 10, 2, 1);
        
        grid.add(new Label("Forward Date:"), 0, 11);
        forwardDatePicker = new DatePicker();
        forwardDatePicker.setPrefWidth(150);
        forwardDatePicker.setConverter(converter);
        grid.add(forwardDatePicker, 1, 11, 2, 1);

        return grid;
    }

    private VBox createParamBox() {
        VBox box = new VBox(10);
        box.getStyleClass().add("sci-fi-panel");
        
        Label title = new Label("EA Parameters & Optimization Ranges");
        title.getStyleClass().add("sci-fi-panel-title");
        
        paramTable = new TableView<>();
        paramTable.setStyle("-fx-background-color: transparent;");
        paramTable.setEditable(true);
        
        TableColumn<EaParameter, Boolean> optCol = new TableColumn<>("Opt");
        optCol.setCellValueFactory(cellData -> {
            com.backtester.config.EaParameter param = cellData.getValue();
            javafx.beans.property.BooleanProperty property = new javafx.beans.property.SimpleBooleanProperty(param.isOptimizeEnabled());
            property.addListener((obs, oldV, newV) -> param.setOptimizeEnabled(newV));
            return property;
        });
        optCol.setCellFactory(javafx.scene.control.cell.CheckBoxTableCell.forTableColumn(optCol));
        optCol.setPrefWidth(40);
        
        TableColumn<EaParameter, String> nameCol = new TableColumn<>("Variable");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(200);
        
        TableColumn<EaParameter, String> valCol = new TableColumn<>("Value");
        valCol.setCellValueFactory(new PropertyValueFactory<>("value"));
        valCol.setCellFactory(javafx.scene.control.cell.TextFieldTableCell.forTableColumn());
        valCol.setOnEditCommit(e -> e.getRowValue().setValue(e.getNewValue()));
        valCol.setPrefWidth(100);
        
        TableColumn<EaParameter, String> startCol = new TableColumn<>("Start");
        startCol.setCellValueFactory(new PropertyValueFactory<>("optimizeStart"));
        startCol.setCellFactory(javafx.scene.control.cell.TextFieldTableCell.forTableColumn());
        startCol.setOnEditCommit(e -> e.getRowValue().setOptimizeStart(e.getNewValue()));
        
        TableColumn<EaParameter, String> stepCol = new TableColumn<>("Step");
        stepCol.setCellValueFactory(new PropertyValueFactory<>("optimizeStep"));
        stepCol.setCellFactory(javafx.scene.control.cell.TextFieldTableCell.forTableColumn());
        stepCol.setOnEditCommit(e -> e.getRowValue().setOptimizeStep(e.getNewValue()));
        
        TableColumn<EaParameter, String> stopCol = new TableColumn<>("Stop");
        stopCol.setCellValueFactory(new PropertyValueFactory<>("optimizeEnd"));
        stopCol.setCellFactory(javafx.scene.control.cell.TextFieldTableCell.forTableColumn());
        stopCol.setOnEditCommit(e -> e.getRowValue().setOptimizeEnd(e.getNewValue()));
        
        paramTable.getColumns().addAll(optCol, nameCol, valCol, startCol, stepCol, stopCol);
        
        Label placeholder = new Label("No parameters loaded.\nLoad an Expert Advisor or a .set file.");
        placeholder.setStyle("-fx-text-fill: #7e889a;");
        paramTable.setPlaceholder(placeholder);
        
        VBox.setVgrow(paramTable, Priority.ALWAYS);

        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_RIGHT);
        Button autoConfigBtn = new Button("AutoConfig");
        autoConfigBtn.setOnAction(e -> autoConfigParameters());
        
        Button loadBtn = new Button("Load .set");
        loadBtn.setOnAction(e -> loadFromFile());
        
        Button saveBtn = new Button("Save .set");
        saveBtn.setOnAction(e -> saveToFile());
        
        btnBox.getChildren().addAll(autoConfigBtn, loadBtn, saveBtn);

        box.getChildren().addAll(title, paramTable, btnBox);
        return box;
    }

    private VBox createResultsBox() {
        VBox box = new VBox(10);
        box.getStyleClass().add("sci-fi-panel");
        
        Label title = new Label("Optimization Results");
        title.getStyleClass().add("sci-fi-panel-title");

        TabPane resultTabs = new TabPane();
        resultTabs.getStyleClass().add("tab-pane");
        
        Tab mainTab = new Tab("Main Optimization", createQuantumBackground());
        mainTab.getStyleClass().add("tab");
        mainTab.setClosable(false);
        
        Tab forwardTab = new Tab("Forward Results");
        forwardTab.getStyleClass().add("tab");
        forwardTab.setClosable(false);
        
        resultTabs.getTabs().addAll(mainTab, forwardTab);
        VBox.setVgrow(resultTabs, Priority.ALWAYS);

        HBox controlBox = new HBox(15);
        controlBox.setAlignment(Pos.CENTER_LEFT);
        
        startBtn = new Button("▶ Start Optimization");
        startBtn.getStyleClass().addAll("button", "button-start");
        startBtn.setOnAction(e -> startOptimization());
        
        cancelBtn = new Button("⬛ Cancel");
        cancelBtn.getStyleClass().addAll("button", "button-cancel");
        cancelBtn.setDisable(true);
        cancelBtn.setOnAction(e -> cancelOptimization());

        progressBar = new ProgressBar(0.0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setMinHeight(30);
        HBox.setHgrow(progressBar, Priority.ALWAYS);

        progressLabel = new Label("0 / 0 Passes");
        progressLabel.getStyleClass().add("sci-fi-panel-title");
        progressLabel.setFont(Font.font("Segoe UI", 14));
        progressLabel.setTextFill(Color.web("#c8cddc"));
        progressLabel.setMinWidth(120);
        progressLabel.setAlignment(Pos.CENTER_RIGHT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button applyBtn = new Button("Apply Best Parameters");
        Button openXmlBtn = new Button("Open XML");
        
        controlBox.getChildren().addAll(startBtn, cancelBtn, progressBar, progressLabel, spacer, applyBtn, openXmlBtn);

        box.getChildren().addAll(title, resultTabs, controlBox);
        return box;
    }

    private StackPane createQuantumBackground() {
        StackPane pane = new StackPane();
        pane.setStyle("-fx-background-image: url('/images/quantum_singularity.png'); -fx-background-size: cover; -fx-background-position: center;");
        Label waiting = new Label("Antigravity Protocol: Waiting for Data");
        waiting.setFont(Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 24));
        waiting.setTextFill(Color.web("rgba(255,255,255,0.8)"));
        pane.getChildren().add(waiting);
        return pane;
    }

    private void browseEA() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Select Expert Advisor");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("MetaTrader 5 EA", "*.ex5"));
        
        com.backtester.config.AppConfig config = com.backtester.config.AppConfig.getInstance();
        java.nio.file.Path mt5Dir = config.getMt5InstallDir();
        java.nio.file.Path expertsDir = mt5Dir != null ? mt5Dir.resolve("MQL5").resolve("Experts") : null;
        if (expertsDir != null && java.nio.file.Files.exists(expertsDir)) {
            chooser.setInitialDirectory(expertsDir.toFile());
        }
        
        java.io.File selected = chooser.showOpenDialog(expertField.getScene().getWindow());
        if (selected != null) {
            if (expertsDir != null && selected.toPath().startsWith(expertsDir)) {
                String relative = expertsDir.relativize(selected.toPath()).toString();
                if (relative.toLowerCase().endsWith(".ex5")) {
                    relative = relative.substring(0, relative.length() - 4);
                }
                expertField.setText(relative);
            } else {
                String path = selected.getAbsolutePath();
                if (path.toLowerCase().endsWith(".ex5")) {
                    path = path.substring(0, path.length() - 4);
                }
                expertField.setText(path);
            }
            savePreferences();
            loadParameters();
        }
    }

    private void loadParameters() {
        String expert = expertField.getText().trim();
        if (expert.isEmpty()) return;
        
        java.util.List<EaParameter> params = eaParamManager.getEffectiveParameters(expert);
        if (params != null) {
            paramTable.getItems().setAll(params);
            logView.log("INFO", "Loaded " + params.size() + " parameters for " + EaParameterManager.extractEaBaseName(expert));
        } else {
            paramTable.getItems().clear();
            logView.log("WARN", "No parameters found for " + EaParameterManager.extractEaBaseName(expert) + ". Click AutoConfig or select a valid EA.");
        }
    }

    private void loadPreferences() {
        com.backtester.config.AppConfig config = com.backtester.config.AppConfig.getInstance();
        String exp = config.get("optimization.expert", "");
        if (!exp.isEmpty()) {
            expertField.setText(exp);
            loadParameters();
        }
        
        symbolCombo.setValue(config.get("optimization.symbol", "EURUSD"));
        periodCombo.setValue(config.get("optimization.period", "H1"));
        
        String savedModel = config.get("optimization.model", "Every tick");
        if (!modelCombo.getItems().contains(savedModel)) modelCombo.getSelectionModel().select(0);
        else modelCombo.setValue(savedModel);

        String savedMode = config.get("optimization.mode", "Fast Genetic Algorithm");
        if (savedMode.contains("Genetic") || savedMode.contains("Fast")) modeCombo.getSelectionModel().select(1);
        else if (savedMode.contains("Slow") || savedMode.contains("Complete")) modeCombo.getSelectionModel().select(0);
        else modeCombo.setValue(savedMode);

        String savedCrit = config.get("optimization.criterion", "Balance max");
        if (!optimizationCriterionCombo.getItems().contains(savedCrit)) optimizationCriterionCombo.getSelectionModel().select(0);
        else optimizationCriterionCombo.setValue(savedCrit);

        String savedFwd = config.get("optimization.forwardMode", "Off");
        if (!forwardModeCombo.getItems().contains(savedFwd)) forwardModeCombo.getSelectionModel().select(0);
        else forwardModeCombo.setValue(savedFwd);
        
        try {
            String from = config.get("optimization.fromDate", "");
            if (!from.isEmpty()) fromDatePicker.setValue(java.time.LocalDate.parse(from));
            else fromDatePicker.setValue(java.time.LocalDate.now().minusYears(1));
            
            String to = config.get("optimization.toDate", "");
            if (!to.isEmpty()) toDatePicker.setValue(java.time.LocalDate.parse(to));
            else toDatePicker.setValue(java.time.LocalDate.now());
        } catch (Exception e) {}
    }

    private void savePreferences() {
        com.backtester.config.AppConfig config = com.backtester.config.AppConfig.getInstance();
        config.set("optimization.expert", expertField.getText().trim());
        if (symbolCombo.getValue() != null) config.set("optimization.symbol", symbolCombo.getValue());
        if (periodCombo.getValue() != null) config.set("optimization.period", periodCombo.getValue());
        if (modelCombo.getValue() != null) config.set("optimization.model", modelCombo.getValue());
        if (modeCombo.getValue() != null) config.set("optimization.mode", modeCombo.getValue());
        if (optimizationCriterionCombo.getValue() != null) config.set("optimization.criterion", optimizationCriterionCombo.getValue());
        if (forwardModeCombo.getValue() != null) config.set("optimization.forwardMode", forwardModeCombo.getValue());
        if (fromDatePicker.getValue() != null) config.set("optimization.fromDate", fromDatePicker.getValue().toString());
        if (toDatePicker.getValue() != null) config.set("optimization.toDate", toDatePicker.getValue().toString());
        config.save();
    }

    // ==================== Optimization Execution Logic ====================

    private void startOptimization() {
        savePreferences();
        
        // Save current param table to custom .set
        if (!paramTable.getItems().isEmpty()) {
            eaParamManager.saveCustomParameters(expertField.getText().trim(), new java.util.ArrayList<>(paramTable.getItems()));
        }

        OptimizationConfig optConfig = new OptimizationConfig();
        optConfig.setExpert(expertField.getText().trim());
        try {
            String preset = eaParamManager.prepareForBacktest(expertField.getText().trim());
            if (preset != null) {
                optConfig.setExpertParameters(preset);
            }
        } catch (Exception e) {
            logView.log("ERROR", "Cannot resolve config path");
            return;
        }

        optConfig.setSymbol(symbolCombo.getValue());
        optConfig.setPeriod(periodCombo.getValue());
        optConfig.setModel(modelCombo.getSelectionModel().getSelectedIndex());
        if (fromDatePicker.getValue() != null) optConfig.setFromDate(fromDatePicker.getValue());
        if (toDatePicker.getValue() != null) optConfig.setToDate(toDatePicker.getValue());
        
        try {
            optConfig.setDeposit(Integer.parseInt(depositField.getText().trim()));
        } catch (NumberFormatException e) {
            optConfig.setDeposit(10000);
        }
        optConfig.setCurrency(currencyField.getText().trim());
        optConfig.setLeverage(leverageField.getText().trim());

        optConfig.setOptimizationMode(OptimizationConfig.OPTIMIZATION_MODE_VALUES[Math.max(0, modeCombo.getSelectionModel().getSelectedIndex())]);
        optConfig.setOptimizationCriterion(Math.max(0, optimizationCriterionCombo.getSelectionModel().getSelectedIndex()));
        optConfig.setForwardMode(Math.max(0, forwardModeCombo.getSelectionModel().getSelectedIndex()));
        if (forwardDatePicker.getValue() != null) optConfig.setForwardDate(forwardDatePicker.getValue());
        
        
        setUIState(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progressLabel.setText("Preparing...");
        logView.log("INFO", "Starting optimization for " + optConfig.getExpert());

        com.backtester.config.AppConfig config = com.backtester.config.AppConfig.getInstance();
        currentRunner = new OptimizationRunner(config);
        
        long totalPasses = 1;
        if (optConfig.getOptimizationMode() == 1) { // Slow Complete
            totalPasses = eaParamManager.calculateTotalPasses(paramTable.getItems());
        } else {
            totalPasses = 10496; // Heuristic max for Genetic Algorithm
        }
        currentRunner.setTotalPasses(totalPasses);
        
        currentRunner.setLogCallback(msg -> logView.log("OPT", msg));
        currentRunner.setProgressCallback((current, total) -> {
            Platform.runLater(() -> {
                if (total > 0) {
                    progressBar.setProgress((double) current / total);
                    progressLabel.setText(current + " / " + total + " Passes");
                }
            });
        });

        currentTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                com.backtester.report.OptimizationResult result = currentRunner.runOptimization(optConfig);
                Platform.runLater(() -> handleOptimizationResult(result));
                return null;
            }
        };

        Thread t = new Thread(currentTask);
        t.setDaemon(true);
        t.start();
    }

    private void cancelOptimization() {
        if (currentRunner != null) {
            logView.log("INFO", "Cancelling optimization...");
            currentRunner.cancel();
        }
        if (currentTask != null) {
            currentTask.cancel(true);
        }
        setUIState(false);
        progressBar.setProgress(0.0);
        progressLabel.setText("Cancelled");
    }

    private void handleOptimizationResult(com.backtester.report.OptimizationResult result) {
        setUIState(false);
        progressBar.setProgress(1.0);
        progressLabel.setText("Finished");
        if (result.isSuccess()) {
            if (result.getPasses().isEmpty()) {
                logView.log("WARN", "Optimization finished, but no passes were produced.");
            } else {
                logView.log("INFO", "Optimization finished successfully. Found " + result.getPasses().size() + " passes.");
                // TODO: Populate Result Tables
            }
        } else if (result.getMessage() != null && result.getMessage().contains("cancelled")) {
            logView.log("WARN", "Optimization cancelled by user.");
        } else {
            logView.log("ERROR", "Optimization failed: " + result.getMessage());
        }
    }

    private void setUIState(boolean running) {
        startBtn.setDisable(running);
        cancelBtn.setDisable(!running);
        expertField.setDisable(running);
        symbolCombo.setDisable(running);
        paramTable.setDisable(running);
    }

    // ==================== AutoConfig & File I/O Logic ====================

    private void autoConfigParameters() {
        if (paramTable.getItems().isEmpty()) {
            logView.log("WARN", "No parameters loaded. Please select an EA first.");
            return;
        }

        int activated = 0;
        int skipped = 0;

        for (com.backtester.config.EaParameter param : paramTable.getItems()) {
            String name = param.getName();
            String value = param.getValue();

            if (isExcludedParameterName(name) || !isNumericValue(value)) {
                param.setOptimizeEnabled(false);
                skipped++;
                continue;
            }

            double[] range = calculateOptRange(name, value);
            if (range == null) {
                param.setOptimizeEnabled(false);
                skipped++;
                continue;
            }

            double steps = (range[2] - range[0]) / range[1];
            if (steps < 5) {
                param.setOptimizeEnabled(false);
                skipped++;
                continue;
            }

            param.setOptimizeEnabled(true);
            param.setOptimizeStart(formatNumber(range[0]));
            param.setOptimizeStep(formatNumber(range[1]));
            param.setOptimizeEnd(formatNumber(range[2]));
            activated++;
        }
        paramTable.refresh();
        logView.log("INFO", "AutoConfig applied: " + activated + " enabled, " + skipped + " skipped.");
    }

    private boolean isExcludedParameterName(String name) {
        String lower = name.toLowerCase();
        return lower.contains("magic") || lower.contains("slippage") || lower.contains("comment") || lower.contains("color");
    }

    private boolean isNumericValue(String value) {
        if (value == null || value.isEmpty() || value.contains(":") || value.contains(",")) return false;
        try { Double.parseDouble(value); return true; } catch (NumberFormatException e) { return false; }
    }

    private double[] calculateOptRange(String name, String currentValue) {
        double current;
        try { current = Double.parseDouble(currentValue); } catch (NumberFormatException e) { return null; }
        
        double start = 1;
        double end = current;
        double step = 1;
        
        String lower = name.toLowerCase();
        if (lower.contains("lot") || lower.contains("volume")) {
            start = 0.01;
            end = Math.max(current, 0.1);
            step = 0.01;
        } else if (lower.contains("dist") || lower.contains("step") || lower.contains("tp") || lower.contains("sl")) {
            start = 10;
            end = Math.max(current, 100);
            step = 10;
        } else if (lower.contains("period") || lower.contains("ma") || lower.contains("rsi")) {
            start = 2;
            end = Math.max(current, 50);
            step = 1;
        } else if (lower.contains("mult") || lower.contains("factor")) {
            start = 1.0;
            end = Math.max(current, 3.0);
            step = 0.1;
        } else {
            if (current == 0) return null;
            if (current < 1) {
                start = 0.01;
                end = current;
                step = 0.01;
            } else if (current <= 10) {
                start = 1;
                end = current;
                step = 1;
            } else if (current <= 100) {
                start = 5;
                end = current;
                step = 5;
            } else {
                start = 10;
                end = current;
                step = 10;
            }
        }
        
        return new double[]{start, step, end};
    }

    private String formatNumber(double value) {
        if (value == (long) value) return String.format(java.util.Locale.US, "%d", (long) value);
        else return String.format(java.util.Locale.US, "%s", value);
    }

    private void loadFromFile() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Load .set File");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("MT5 Set Files", "*.set"));
        java.io.File file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file != null) {
            java.util.List<com.backtester.config.EaParameter> params = eaParamManager.readSetFile(file.toPath());
            if (params != null && !params.isEmpty()) {
                paramTable.getItems().setAll(params);
                logView.log("INFO", "Loaded parameters from " + file.getName());
            } else {
                logView.log("ERROR", "Failed to load parameters or file is empty.");
            }
        }
    }

    private void saveToFile() {
        if (paramTable.getItems().isEmpty()) {
            logView.log("WARN", "No parameters to save.");
            return;
        }
        String eaName = com.backtester.config.EaParameterManager.extractEaBaseName(expertField.getText());
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Save .set File");
        chooser.setInitialFileName(eaName.isEmpty() ? "params.set" : eaName + ".set");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("MT5 Set Files", "*.set"));
        java.io.File file = chooser.showSaveDialog(root.getScene().getWindow());
        if (file != null) {
            eaParamManager.writeSetFile(file.toPath(), new java.util.ArrayList<>(paramTable.getItems()), eaName);
            logView.log("INFO", "Saved parameters to " + file.getName());
        }
    }

    public BorderPane getView() {
        return root;
    }
}
