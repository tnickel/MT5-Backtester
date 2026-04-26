package com.backtester.ui.javafx;

import com.backtester.config.AppConfig;
import com.backtester.engine.BacktestConfig;
import com.backtester.engine.OptimizationConfig;
import com.backtester.engine.RobustnessRunner;
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
import javafx.stage.FileChooser;

import java.io.File;
import java.time.LocalDate;

public class RobustnessView {

    private final BorderPane root;
    private final LogView logView;
    private final AppConfig config;

    // Config fields
    private TextField expertField;
    private ComboBox<String> symbolCombo;
    private ComboBox<String> periodCombo;
    private ComboBox<String> modelCombo;
    private DatePicker fromDatePicker;
    private DatePicker toDatePicker;
    private TextField depositField;
    private TextField currencyField;
    private TextField leverageField;
    private ComboBox<String> metricCombo;
    private Spinner<Integer> shiftsSpinner;
    private Spinner<Integer> shiftDaysSpinner;

    // Parameter Table
    private TableView<EaParameter> paramTable;
    private final EaParameterManager eaParamManager = new EaParameterManager();
    private RobustnessRunner currentRunner;
    private Task<Void> currentTask;
    
    // Controls
    private Button startBtn;
    private Button cancelBtn;
    private ProgressBar progress;

    public RobustnessView(LogView logView) {
        this.logView = logView;
        this.config = AppConfig.getInstance();

        root = new BorderPane();
        root.setPadding(new Insets(15));

        // Top: Split between Config and Params
        HBox topBox = new HBox(15);
        VBox configBox = createConfigBox();
        VBox paramBox = createParamBox();
        
        HBox.setHgrow(configBox, Priority.ALWAYS);
        HBox.setHgrow(paramBox, Priority.ALWAYS);
        topBox.getChildren().addAll(configBox, paramBox);

        // Bottom: Controls
        HBox controlBox = createControlBox();

        root.setCenter(topBox);
        root.setBottom(controlBox);

        loadPreferences();
    }

    private VBox createConfigBox() {
        VBox box = new VBox(15);
        box.getStyleClass().add("sci-fi-panel");

        Label title = new Label("Scan Settings");
        title.getStyleClass().add("sci-fi-panel-title");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        // Row 0: EA
        grid.add(new Label("Expert Advisor:"), 0, 0);
        expertField = new TextField();
        expertField.getStyleClass().add("text-input");
        Button browseBtn = new Button("...");
        browseBtn.getStyleClass().add("button");
        browseBtn.setOnAction(e -> browseEA());
        HBox eaBox = new HBox(5, expertField, browseBtn);
        HBox.setHgrow(expertField, Priority.ALWAYS);
        grid.add(eaBox, 1, 0, 3, 1);

        // Row 1: Symbol & Period
        grid.add(new Label("Symbol:"), 0, 1);
        symbolCombo = new ComboBox<>(FXCollections.observableArrayList("EURUSD", "GBPUSD"));
        symbolCombo.getStyleClass().add("combo-box");
        grid.add(symbolCombo, 1, 1);

        grid.add(new Label("Period:"), 2, 1);
        periodCombo = new ComboBox<>(FXCollections.observableArrayList("M1", "M5", "M15", "H1", "H4", "D1"));
        periodCombo.getStyleClass().add("combo-box");
        grid.add(periodCombo, 3, 1);

        // Row 2: Dates
        grid.add(new Label("Dates:"), 0, 2);
        fromDatePicker = new DatePicker(LocalDate.now().minusMonths(6));
        grid.add(fromDatePicker, 1, 2);

        grid.add(new Label("To:"), 2, 2);
        toDatePicker = new DatePicker(LocalDate.now());
        grid.add(toDatePicker, 3, 2);

        // Row 3: Shifts
        grid.add(new Label("Time Shifts:"), 0, 3);
        shiftsSpinner = new Spinner<>(1, 100, 10, 1);
        grid.add(shiftsSpinner, 1, 3);

        grid.add(new Label("Shift Days:"), 2, 3);
        shiftDaysSpinner = new Spinner<>(1, 365, 7, 1);
        grid.add(shiftDaysSpinner, 3, 3);

        // Row 4: Account settings
        grid.add(new Label("Deposit:"), 0, 4);
        depositField = new TextField("10000");
        depositField.getStyleClass().add("text-input");
        grid.add(depositField, 1, 4);

        grid.add(new Label("Currency/Lev:"), 2, 4);
        currencyField = new TextField("USD");
        currencyField.getStyleClass().add("text-input");
        currencyField.setPrefWidth(60);
        leverageField = new TextField("1:100");
        leverageField.getStyleClass().add("text-input");
        leverageField.setPrefWidth(60);
        HBox curLevBox = new HBox(5, currencyField, leverageField);
        grid.add(curLevBox, 3, 4);

        // Row 5: Metric & Model
        grid.add(new Label("Metric:"), 0, 5);
        metricCombo = new ComboBox<>(FXCollections.observableArrayList(OptimizationConfig.OPTIMIZATION_CRITERIA));
        metricCombo.getStyleClass().add("combo-box");
        grid.add(metricCombo, 1, 5);

        grid.add(new Label("Tick Model:"), 2, 5);
        modelCombo = new ComboBox<>(FXCollections.observableArrayList(BacktestConfig.MODEL_NAMES));
        modelCombo.getStyleClass().add("combo-box");
        grid.add(modelCombo, 3, 5);

        box.getChildren().addAll(title, grid);
        return box;
    }

    private VBox createParamBox() {
        VBox box = new VBox(10);
        box.getStyleClass().add("sci-fi-panel");

        Label title = new Label("EA Parameters (Target Set)");
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

    private HBox createControlBox() {
        HBox box = new HBox(15);
        box.getStyleClass().add("sci-fi-panel");
        box.setAlignment(Pos.CENTER_LEFT);
        BorderPane.setMargin(box, new Insets(15, 0, 0, 0));

        startBtn = new Button("▶ Start Robustness Scan");
        startBtn.getStyleClass().addAll("button", "button-start");
        startBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #782878, #3c143c);");
        startBtn.setOnAction(e -> startScan());

        cancelBtn = new Button("⬛ Cancel");
        cancelBtn.getStyleClass().addAll("button", "button-cancel");
        cancelBtn.setDisable(true);
        cancelBtn.setOnAction(e -> cancelScan());

        progress = new ProgressBar(0);
        progress.setPrefWidth(300);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button dbStoreBtn = new Button("Store set in DB");
        dbStoreBtn.getStyleClass().add("button");
        
        Button dbGetBtn = new Button("Get set from DB");
        dbGetBtn.getStyleClass().add("button");

        box.getChildren().addAll(startBtn, cancelBtn, progress, spacer, dbStoreBtn, dbGetBtn);
        return box;
    }

    private void browseEA() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Expert Advisor");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("MetaTrader 5 EA", "*.ex5"));
        
        java.nio.file.Path mt5Dir = config.getMt5InstallDir();
        java.nio.file.Path expertsDir = mt5Dir != null ? mt5Dir.resolve("MQL5").resolve("Experts") : null;
        if (expertsDir != null && java.nio.file.Files.exists(expertsDir)) {
            chooser.setInitialDirectory(expertsDir.toFile());
        }
        
        File selected = chooser.showOpenDialog(expertField.getScene().getWindow());
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
        String exp = config.get("robustness.expert", "");
        if (!exp.isEmpty()) {
            expertField.setText(exp);
            loadParameters();
        }
        
        String sym = config.get("robustness.symbol", "EURUSD");
        symbolCombo.setValue(sym);
        
        String per = config.get("robustness.period", "H1");
        periodCombo.setValue(per);
        
        // Handle both string and legacy integer index from old swing config
        String modStr = config.get("robustness.model", "Every tick");
        try {
            int modIdx = Integer.parseInt(modStr);
            if (modIdx >= 0 && modIdx < modelCombo.getItems().size()) {
                modelCombo.getSelectionModel().select(modIdx);
            }
        } catch (NumberFormatException e) {
            modelCombo.setValue(modStr);
        }
        
        String metricStr = config.get("robustness.metric", "Profit");
        try {
            int metricIdx = Integer.parseInt(metricStr);
            if (metricIdx >= 0 && metricIdx < metricCombo.getItems().size()) {
                metricCombo.getSelectionModel().select(metricIdx);
            }
        } catch (NumberFormatException e) {
            metricCombo.setValue(metricStr);
        }

        try {
            String dFrom = config.get("robustness.dateFrom", "");
            if (!dFrom.isEmpty()) fromDatePicker.setValue(LocalDate.parse(dFrom));
            
            String dTo = config.get("robustness.dateTo", "");
            if (!dTo.isEmpty()) toDatePicker.setValue(LocalDate.parse(dTo));
        } catch (Exception ignored) {}

        depositField.setText(config.get("robustness.deposit", "10000"));
        currencyField.setText(config.get("robustness.currency", "USD"));
        leverageField.setText(config.get("robustness.leverage", "1:100"));
        
        shiftsSpinner.getValueFactory().setValue(config.getInt("robustness.shifts", 10));
        shiftDaysSpinner.getValueFactory().setValue(config.getInt("robustness.shiftdays", 7));
    }

    private void savePreferences() {
        config.set("robustness.expert", expertField.getText().trim());
        if (symbolCombo.getValue() != null) config.set("robustness.symbol", symbolCombo.getValue());
        if (periodCombo.getValue() != null) config.set("robustness.period", periodCombo.getValue());
        if (modelCombo.getValue() != null) config.set("robustness.model", modelCombo.getValue());
        if (metricCombo.getValue() != null) config.set("robustness.metric", metricCombo.getValue());
        if (fromDatePicker.getValue() != null) config.set("robustness.dateFrom", fromDatePicker.getValue().toString());
        if (toDatePicker.getValue() != null) config.set("robustness.dateTo", toDatePicker.getValue().toString());
        config.set("robustness.deposit", depositField.getText().trim());
        config.set("robustness.currency", currencyField.getText().trim());
        config.set("robustness.leverage", leverageField.getText().trim());
        config.set("robustness.shifts", String.valueOf(shiftsSpinner.getValue()));
        config.set("robustness.shiftdays", String.valueOf(shiftDaysSpinner.getValue()));
        config.save();
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

    private void startScan() {
        try {
            savePreferences();
            
            String expert = expertField.getText().trim();
            if (expert.isEmpty()) {
                logView.log("WARN", "Please select an Expert Advisor.");
                return;
            }

            OptimizationConfig optConfig = new OptimizationConfig();
            optConfig.setExpert(expert);
            optConfig.setSymbol(symbolCombo.getValue() != null ? symbolCombo.getValue() : "EURUSD");
            optConfig.setPeriod(periodCombo.getValue() != null ? periodCombo.getValue() : "H1");
            optConfig.setModel(modelCombo.getSelectionModel().getSelectedIndex() >= 0 ? modelCombo.getSelectionModel().getSelectedIndex() : 1);
            if (fromDatePicker.getValue() != null) optConfig.setFromDate(fromDatePicker.getValue());
            if (toDatePicker.getValue() != null) optConfig.setToDate(toDatePicker.getValue());
            
            try { optConfig.setDeposit(Integer.parseInt(depositField.getText().trim())); } 
            catch (Exception e) { optConfig.setDeposit(10000); }
            optConfig.setCurrency(currencyField.getText().trim());
            optConfig.setLeverage(leverageField.getText().trim());
            optConfig.setUseLocal(true);

            java.util.List<EaParameter> params = new java.util.ArrayList<>(paramTable.getItems());
            if (params.isEmpty()) {
                logView.log("WARN", "No parameters to sweep.");
                return;
            }

            setUIState(true);
            progress.setProgress(-1); // Indeterminate

            currentRunner = new RobustnessRunner(config);
            currentRunner.setLogCallback(msg -> logView.log("ROBUST", msg));
            currentRunner.setProgressCallback(percent -> {
                Platform.runLater(() -> progress.setProgress(percent / 100.0));
            });

            String targetMetric = metricCombo.getValue() != null ? metricCombo.getValue() : "Profit";
            int shifts = shiftsSpinner.getValue() != null ? shiftsSpinner.getValue() : 10;
            int shiftDays = shiftDaysSpinner.getValue() != null ? shiftDaysSpinner.getValue() : 90;

            currentTask = new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    com.backtester.report.RobustnessResult res = currentRunner.runRobustnessScan(optConfig, params, shifts, shiftDays);
                    Platform.runLater(() -> handleScanResult(res, optConfig, targetMetric, params, shifts, shiftDays));
                    return null;
                }
            };

            currentTask.setOnFailed(e -> {
                Throwable ex = currentTask.getException();
                logView.log("ERROR", "Robustness Scan failed: " + (ex != null ? ex.getMessage() : "Unknown Error"));
                if (ex != null) ex.printStackTrace();
                setUIState(false);
                progress.setProgress(0);
            });

            Thread t = new Thread(currentTask);
            t.setDaemon(true);
            t.start();
        } catch (Exception ex) {
            logView.log("ERROR", "Error in startScan: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void handleScanResult(com.backtester.report.RobustnessResult res, OptimizationConfig optConfig, String targetMetric, java.util.List<EaParameter> params, int shifts, int shiftDays) {
        setUIState(false);
        progress.setProgress(1.0);
        
        if (res != null && res.isSuccess()) {
            logView.log("INFO", "Robustness scan finished successfully.");
            try {
                com.backtester.report.RobustnessHtmlGenerator.generateReport(res, optConfig, targetMetric, targetMetric, params);
                java.nio.file.Path reportPath = java.nio.file.Paths.get(res.getOutputDirectory(), "robustness_report.html");
                
                com.google.gson.JsonObject metrics = new com.google.gson.JsonObject();
                metrics.addProperty("targetMetric", targetMetric);
                metrics.addProperty("shifts", shifts);
                metrics.addProperty("shiftDays", shiftDays);
                
                com.backtester.database.DatabaseManager.getInstance().saveRun(
                    "ROBUSTNESS", 
                    optConfig.getExpert(), 
                    System.currentTimeMillis(), 
                    metrics.toString(), 
                    reportPath.toAbsolutePath().toString()
                );
                
                java.awt.Desktop.getDesktop().open(reportPath.toFile());
            } catch (Exception ex) {
                logView.log("ERROR", "Failed to save robustness to DB: " + ex.getMessage());
            }
        } else if (res != null) {
            logView.log("ERROR", "Robustness scan failed: " + res.getMessage());
        }
    }

    private void cancelScan() {
        if (currentRunner != null) currentRunner.cancel();
        if (currentTask != null) currentTask.cancel(true);
        setUIState(false);
        progress.setProgress(0.0);
        logView.log("WARN", "Robustness scan cancelled.");
    }

    private void setUIState(boolean running) {
        startBtn.setDisable(running);
        cancelBtn.setDisable(!running);
        expertField.setDisable(running);
        symbolCombo.setDisable(running);
        paramTable.setDisable(running);
        shiftsSpinner.setDisable(running);
        shiftDaysSpinner.setDisable(running);
    }

    public BorderPane getView() {
        return root;
    }
}
