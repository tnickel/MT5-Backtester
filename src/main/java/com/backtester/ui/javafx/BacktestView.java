package com.backtester.ui.javafx;

import com.backtester.config.AppConfig;
import com.backtester.engine.BacktestConfig;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import javafx.concurrent.Task;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.cell.PropertyValueFactory;
import com.backtester.engine.BacktestRunner;
import com.backtester.report.BacktestResult;
import com.backtester.config.EaParameterManager;
import java.io.File;
import java.time.LocalDate;
import java.awt.Desktop;

public class BacktestView {

    private final BorderPane root;
    private final AppConfig config;

    // Config fields
    private TextField expertField;
    private ComboBox<String> symbolCombo;
    private ComboBox<String> periodCombo;
    private ComboBox<String> modelCombo;
    private DatePicker fromDatePicker;
    private DatePicker toDatePicker;
    private Spinner<Integer> depositSpinner;
    private ComboBox<String> currencyCombo;
    private TextField leverageField;

    // Results
    private TableView<BacktestResult> resultsTable;

    // Parameters Table
    private TableView<com.backtester.config.EaParameter> paramTable;

    // Backend
    private final LogView logView;
    private final EaParameterManager eaParamManager = new EaParameterManager();
    private BacktestRunner currentRunner;
    private Task<BacktestResult> currentTask;

    // UI Buttons that need state management
    private Button startBtn;
    private Button visualBtn;
    private Button cancelBtn;
    private CheckBox keepOpenCb;
    private ProgressBar progress;

    public BacktestView(LogView logView) {
        this.config = AppConfig.getInstance();
        this.logView = logView;

        root = new BorderPane();
        root.setPadding(new Insets(15));

        // Splitter
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);
        splitPane.getStyleClass().add("transparent-split-pane");
        splitPane.setStyle("-fx-background-color: transparent; -fx-box-border: transparent;");

        // Top Split: Config vs Parameters
        HBox topBox = new HBox(15);
        VBox configBox = createConfigBox();
        VBox paramBox = createParamBox();
        
        HBox.setHgrow(configBox, Priority.ALWAYS);
        HBox.setHgrow(paramBox, Priority.ALWAYS);
        topBox.getChildren().addAll(configBox, paramBox);
        topBox.setMinHeight(0);
        
        // Bottom Split: Results
        VBox resultsBox = createResultsBox();

        splitPane.getItems().addAll(topBox, resultsBox);
        splitPane.setDividerPositions(0.45);

        root.setCenter(splitPane);
        
        loadPreferences();
        loadResultsFromDb();

        symbolCombo.valueProperty().addListener((obs, oldVal, newVal) -> loadParameters());
        periodCombo.valueProperty().addListener((obs, oldVal, newVal) -> loadParameters());
    }

    private VBox createConfigBox() {
        VBox box = new VBox(15);
        box.getStyleClass().add("sci-fi-panel");

        Label title = new Label("Backtest Configuration");
        title.getStyleClass().add("sci-fi-panel-title");
        
        String overview = "Der Backtest-Tab ermöglicht es, einen Expert Advisor (EA) für ein einzelnes Symbol und eine spezifische Periode über einen bestimmten Zeitraum in der Vergangenheit zu testen. Hierdurch kann die grundlegende Funktionalität und Performance der Strategie überprüft werden.";
        String details = "Funktionen im Detail:\n\n" +
                         "- Expert Advisor: Wähle den zu testenden EA (.ex5) aus dem MT5-Verzeichnis aus.\n" +
                         "- Symbol & Periode: Lege das Währungspaar (z.B. EURUSD) und den Zeitrahmen (z.B. H1) fest.\n" +
                         "- Datumsbereich (From / To): Bestimmt den Zeitraum für den historischen Test.\n" +
                         "- Deposit, Currency & Leverage: Einstellungen zum simulierten Konto.\n" +
                         "- Tick Model: Wähle die Genauigkeit der Kursdaten (z.B. 'Every tick' für höchste Genauigkeit oder 'Open prices only' für sehr schnelle Tests).\n" +
                         "- Visual Mode: Wenn aktiviert, wird der MT5 Strategy Tester im visuellen Modus gestartet, sodass man den Trades auf dem Chart zusehen kann.\n\n" +
                         "Ergebnisse:\nNach Abschluss des Tests erscheinen die wichtigsten Kennzahlen (Profit, Drawdown, Win Rate) in der unteren Tabelle. Mit einem Doppelklick oder den Buttons unten kann der detaillierte HTML-Report aufgerufen werden.";
                         
        javafx.scene.layout.Region infoSpacer = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(infoSpacer, javafx.scene.layout.Priority.ALWAYS);
        javafx.scene.layout.HBox titleBox = new javafx.scene.layout.HBox(15, title, infoSpacer, DocHelper.createInfoButton("Backtest", overview, details));
        titleBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        GridPane grid = new GridPane();
        grid.setHgap(15);
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
        symbolCombo = new ComboBox<>(FXCollections.observableArrayList(BacktestConfig.SYMBOLS));
        symbolCombo.getStyleClass().add("combo-box");
        symbolCombo.setValue("EURUSD");
        grid.add(symbolCombo, 1, 1);

        grid.add(new Label("Period:"), 2, 1);
        periodCombo = new ComboBox<>(FXCollections.observableArrayList("M1", "M5", "M15", "H1", "H4", "D1"));
        periodCombo.getStyleClass().add("combo-box");
        periodCombo.setValue("H1");
        grid.add(periodCombo, 3, 1);

        // Row 2: Dates
        grid.add(new Label("Dates:"), 0, 2);
        fromDatePicker = new DatePicker(LocalDate.now().minusMonths(6));
        grid.add(fromDatePicker, 1, 2);

        grid.add(new Label("To:"), 2, 2);
        toDatePicker = new DatePicker(LocalDate.now());
        grid.add(toDatePicker, 3, 2);

        // Row 3: Account
        grid.add(new Label("Deposit:"), 0, 3);
        depositSpinner = new Spinner<>(100, 10000000, config.getDefaultDeposit(), 1000);
        depositSpinner.setEditable(true);
        grid.add(depositSpinner, 1, 3);

        currencyCombo = new ComboBox<>(FXCollections.observableArrayList("USD", "EUR", "GBP"));
        currencyCombo.getStyleClass().add("combo-box");
        currencyCombo.setValue(config.getDefaultCurrency());
        
        leverageField = new TextField(config.getDefaultLeverage());
        leverageField.getStyleClass().add("text-input");
        leverageField.setPrefWidth(80);
        
        HBox accountBox = new HBox(10, currencyCombo, new Label("Leverage:"), leverageField);
        accountBox.setAlignment(Pos.CENTER_LEFT);
        grid.add(accountBox, 2, 3, 2, 1);

        // Row 4: Model
        grid.add(new Label("Tick Model:"), 0, 4);
        modelCombo = new ComboBox<>(FXCollections.observableArrayList(BacktestConfig.MODEL_NAMES));
        modelCombo.getStyleClass().add("combo-box");
        modelCombo.getSelectionModel().select(config.getDefaultModel());
        grid.add(modelCombo, 1, 4, 3, 1);

        // Buttons
        HBox btnBox = new HBox(15);
        btnBox.setAlignment(Pos.CENTER_LEFT);
        btnBox.setPadding(new Insets(10, 0, 0, 0));

        startBtn = new Button("▶ Start Backtest");
        startBtn.getStyleClass().addAll("button", "button-start");
        startBtn.setOnAction(e -> startBacktest(false));

        visualBtn = new Button("👁 Visual Mode");
        visualBtn.getStyleClass().add("button");
        visualBtn.setOnAction(e -> startBacktest(true));

        cancelBtn = new Button("⬛ Cancel");
        cancelBtn.getStyleClass().addAll("button", "button-cancel");
        cancelBtn.setDisable(true);
        cancelBtn.setOnAction(e -> cancelBacktest());

        keepOpenCb = new CheckBox("Manual Mode (Keep MT5 Open)");
        keepOpenCb.getStyleClass().add("check-box");

        progress = new ProgressBar(0);
        progress.setPrefWidth(200);

        btnBox.getChildren().addAll(startBtn, visualBtn, cancelBtn, keepOpenCb, progress);

        box.getChildren().addAll(titleBox, grid, btnBox);
        return box;
    }

    private VBox createResultsBox() {
        VBox box = new VBox(10);
        box.getStyleClass().add("sci-fi-panel");
        
        Label title = new Label("Backtest History & Results");
        title.getStyleClass().add("sci-fi-panel-title");

        resultsTable = new TableView<>();
        resultsTable.setStyle("-fx-background-color: transparent;");
        
        TableColumn<BacktestResult, String> eaCol = new TableColumn<>("Expert");
        eaCol.setCellValueFactory(new PropertyValueFactory<>("expert"));
        eaCol.setPrefWidth(150);
        
        TableColumn<BacktestResult, String> symCol = new TableColumn<>("Symbol");
        symCol.setCellValueFactory(new PropertyValueFactory<>("symbol"));
        
        TableColumn<BacktestResult, String> perCol = new TableColumn<>("Period");
        perCol.setCellValueFactory(new PropertyValueFactory<>("period"));
        
        TableColumn<BacktestResult, String> profCol = new TableColumn<>("Profit");
        profCol.setCellValueFactory(cellData -> new SimpleStringProperty(String.format("%.2f", cellData.getValue().getTotalProfit())));
        
        TableColumn<BacktestResult, Integer> tradesCol = new TableColumn<>("Trades");
        tradesCol.setCellValueFactory(new PropertyValueFactory<>("totalTrades"));
        
        TableColumn<BacktestResult, String> winCol = new TableColumn<>("Win Rate");
        winCol.setCellValueFactory(cellData -> new SimpleStringProperty(String.format("%.2f%%", cellData.getValue().getWinRate())));
        
        TableColumn<BacktestResult, String> ddCol = new TableColumn<>("Drawdown");
        ddCol.setCellValueFactory(cellData -> new SimpleStringProperty(String.format("%.2f%%", cellData.getValue().getMaxDrawdown())));
        
        resultsTable.getColumns().addAll(eaCol, symCol, perCol, profCol, tradesCol, winCol, ddCol);
        
        resultsTable.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) showSelectedReport();
        });
        
        VBox.setVgrow(resultsTable, Priority.ALWAYS);

        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_LEFT);
        
        Button deleteBtn = new Button("🗑 Delete Selected");
        deleteBtn.getStyleClass().addAll("button", "button-cancel");
        deleteBtn.setOnAction(e -> deleteSelectedRuns());

        Button deleteAllBtn = new Button("🗑 Delete All History");
        deleteAllBtn.getStyleClass().addAll("button", "button-cancel");
        deleteAllBtn.setOnAction(e -> deleteAllHistory());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button openHtmlBtn = new Button("Open HTML Report");
        openHtmlBtn.getStyleClass().add("button");
        openHtmlBtn.setOnAction(e -> showSelectedReport());
        
        Button openXmlBtn = new Button("Open Directory");
        openXmlBtn.getStyleClass().add("button");
        openXmlBtn.setOnAction(e -> openDirectory());

        btnBox.getChildren().addAll(deleteBtn, deleteAllBtn, spacer, openHtmlBtn, openXmlBtn);

        box.getChildren().addAll(title, resultsTable, btnBox);
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

    private void startBacktest(boolean visual) {
        String expert = expertField.getText().trim();
        if (expert.isEmpty()) {
            logView.log("ERROR", "Please specify the Expert Advisor path.");
            return;
        }

        LocalDate from = fromDatePicker.getValue();
        LocalDate to = toDatePicker.getValue();
        if (from == null || to == null || from.isAfter(to)) {
            logView.log("ERROR", "Invalid dates selected.");
            return;
        }

        savePreferences();

        BacktestConfig btConfig = new BacktestConfig();
        btConfig.setExpert(expert);
        btConfig.setSymbol(symbolCombo.getValue());
        btConfig.setPeriod(periodCombo.getValue());
        int mIdx = modelCombo.getSelectionModel().getSelectedIndex();
        btConfig.setModel(mIdx >= 0 ? mIdx : 0);
        btConfig.setFromDate(from);
        btConfig.setToDate(to);
        btConfig.setDeposit(depositSpinner.getValue());
        btConfig.setCurrency(currencyCombo.getValue());
        btConfig.setLeverage(leverageField.getText().trim());
        btConfig.setShutdownTerminal(!visual && !keepOpenCb.isSelected());

        if (paramTable != null && !paramTable.getItems().isEmpty()) {
            String symbol = symbolCombo.getValue() != null ? symbolCombo.getValue() : "EURUSD";
            String period = periodCombo.getValue() != null ? periodCombo.getValue() : "H1";
            com.backtester.database.DatabaseManager.getInstance().saveEaParameterSettings(expert, symbol, period, new com.google.gson.Gson().toJson(paramTable.getItems()));
            eaParamManager.saveCustomParameters(expert, new java.util.ArrayList<>(paramTable.getItems()));
        }

        String setFileName = eaParamManager.prepareForBacktest(expert);
        if (setFileName != null) {
            btConfig.setExpertParameters(setFileName);
            logView.log("INFO", "EA Config: Using parameters (" + setFileName + ")");
        } else {
            logView.log("INFO", "EA Config: No .set file found - using EA compiled defaults");
        }

        startBtn.setDisable(true);
        visualBtn.setDisable(true);
        cancelBtn.setDisable(false);
        progress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

        logView.log("INFO", "Starting backtest: " + btConfig.toDirectoryName());

        currentRunner = new BacktestRunner();
        currentRunner.setLogCallback(msg -> Platform.runLater(() -> logView.log("INFO", msg)));

        currentTask = new Task<>() {
            @Override
            protected BacktestResult call() throws Exception {
                return currentRunner.runBacktest(btConfig);
            }
        };

        currentTask.setOnSucceeded(e -> {
            BacktestResult result = currentTask.getValue();
            if (result != null) {
                resultsTable.getItems().add(0, result);
                if (result.isSuccess()) {
                    logView.log("INFO", "Backtest completed successfully");
                    // Persist full result to database
                    try {
                        com.google.gson.Gson gson = new com.google.gson.Gson();
                        String fullJson = gson.toJson(result);
                        int generatedId = com.backtester.database.DatabaseManager.getInstance().saveRun(
                            "BACKTEST",
                            result.getExpert(),
                            System.currentTimeMillis(),
                            fullJson,
                            result.getOutputDirectory()
                        );
                        result.setDbId(generatedId);
                    } catch (Exception ex) {
                        logView.log("ERROR", "Failed to save backtest to DB: " + ex.getMessage());
                    }
                } else {
                    logView.log("WARN", "Backtest finished with issues: " + result.getMessage());
                }
            }
            resetUI();
        });

        currentTask.setOnFailed(e -> {
            logView.log("ERROR", "Backtest failed: " + currentTask.getException().getMessage());
            resetUI();
        });

        currentTask.setOnCancelled(e -> resetUI());

        Thread th = new Thread(currentTask);
        th.setDaemon(true);
        th.start();
    }

    private void cancelBacktest() {
        if (currentRunner != null) currentRunner.cancel();
        if (currentTask != null) currentTask.cancel(true);
        logView.log("WARN", "Backtest cancelled by user");
    }

    private void resetUI() {
        startBtn.setDisable(false);
        visualBtn.setDisable(false);
        cancelBtn.setDisable(true);
        progress.setProgress(0);
    }
    
    private void showSelectedReport() {
        BacktestResult res = resultsTable.getSelectionModel().getSelectedItem();
        if (res != null) openReport(res.getOutputDirectory());
    }

    private void openReport(String directory) {
        try {
            javax.swing.SwingUtilities.invokeLater(() -> {
                com.backtester.ui.ReportViewerDialog.showForDirectory(null, directory);
            });
        } catch (Exception e) {
            logView.log("ERROR", "Could not open report: " + e.getMessage());
        }
    }
    
    private void openDirectory() {
        BacktestResult res = resultsTable.getSelectionModel().getSelectedItem();
        if (res == null) return;
        try {
            File dir = new File(res.getOutputDirectory());
            if (dir.exists()) {
                Desktop.getDesktop().open(dir);
            }
        } catch (Exception ex) {
            logView.log("ERROR", "Could not open directory: " + ex.getMessage());
        }
    }

    private void loadPreferences() {
        String exp = config.get("backtest.expert", "");
        if (!exp.isEmpty()) {
            expertField.setText(exp);
            loadParameters();
        }
        
        String sym = config.get("backtest.symbol", "EURUSD");
        symbolCombo.setValue(sym);
        
        String per = config.get("backtest.period", "H1");
        periodCombo.setValue(per);
        
        String mod = config.get("backtest.model", "Every tick");
        try {
            int idx = Integer.parseInt(mod);
            if (idx >= 0 && idx < BacktestConfig.MODEL_NAMES.length) {
                modelCombo.getSelectionModel().select(idx);
            } else {
                modelCombo.getSelectionModel().select(0);
            }
        } catch (NumberFormatException e) {
            if (java.util.Arrays.asList(BacktestConfig.MODEL_NAMES).contains(mod)) {
                modelCombo.setValue(mod);
            } else {
                modelCombo.getSelectionModel().select(0);
            }
        }

        String keepOpenVal = config.get("backtest.keep_open", "false");
        keepOpenCb.setSelected("true".equals(keepOpenVal));
    }

    private void savePreferences() {
        config.set("backtest.expert", expertField.getText().trim());
        if (symbolCombo.getValue() != null) config.set("backtest.symbol", symbolCombo.getValue());
        if (periodCombo.getValue() != null) config.set("backtest.period", periodCombo.getValue());
        if (modelCombo.getValue() != null) config.set("backtest.model", modelCombo.getValue());
        config.set("backtest.keep_open", String.valueOf(keepOpenCb.isSelected()));
        config.save();
    }

    private void loadResultsFromDb() {
        try {
            java.util.List<com.backtester.database.HistoryRun> runs =
                com.backtester.database.DatabaseManager.getInstance().getRunsByType("BACKTEST");
            if (runs.isEmpty()) return;

            com.google.gson.Gson gson = new com.google.gson.Gson();
            int loaded = 0;
            for (com.backtester.database.HistoryRun run : runs) {
                try {
                    if (run.getResultJson() != null && !run.getResultJson().isEmpty()) {
                        BacktestResult result = gson.fromJson(run.getResultJson(), BacktestResult.class);
                        if (result != null) {
                            result.setDbId(run.getId());
                            resultsTable.getItems().add(result);
                            loaded++;
                        }
                    }
                } catch (Exception ex) {
                    // Skip invalid entries (e.g. old format with only summary metrics)
                }
            }
            if (loaded > 0) {
                logView.log("INFO", "Loaded " + loaded + " backtest results from database.");
            }
        } catch (Exception ex) {
            logView.log("ERROR", "Failed to load backtest results from DB: " + ex.getMessage());
        }
    }

    public void bindTab(Tab tab) {
        updateTabTitle(tab, resultsTable.getItems().size());
        resultsTable.getItems().addListener((javafx.collections.ListChangeListener<BacktestResult>) c -> {
            updateTabTitle(tab, resultsTable.getItems().size());
        });
    }

    private void updateTabTitle(Tab tab, int count) {
        javafx.application.Platform.runLater(() -> tab.setText("Backtest (" + count + ")"));
    }

    public BorderPane getView() {
        return root;
    }

    // ==================== EA Parameter Section & Logic ====================

    private void loadParameters() {
        String expert = expertField.getText().trim();
        if (expert.isEmpty()) return;
        
        String symbol = symbolCombo.getValue() != null ? symbolCombo.getValue() : "EURUSD";
        String period = periodCombo.getValue() != null ? periodCombo.getValue() : "H1";
        
        // Try DB first
        String dbParamsJson = com.backtester.database.DatabaseManager.getInstance().getEaParameterSettings(expert, symbol, period);
        if (dbParamsJson != null && !dbParamsJson.isEmpty()) {
            try {
                java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<java.util.List<com.backtester.config.EaParameter>>(){}.getType();
                java.util.List<com.backtester.config.EaParameter> params = new com.google.gson.Gson().fromJson(dbParamsJson, listType);
                if (params != null && !params.isEmpty()) {
                    paramTable.getItems().setAll(params);
                    logView.log("INFO", "Loaded parameters for " + EaParameterManager.extractEaBaseName(expert) + " [" + symbol + ", " + period + "] from DB");
                    return;
                }
            } catch (Exception e) {
                logView.log("WARN", "Failed to parse parameters from DB: " + e.getMessage());
            }
        }
        
        // Fallback to files
        java.util.List<com.backtester.config.EaParameter> params = eaParamManager.getEffectiveParameters(expert);
        if (params != null) {
            paramTable.getItems().setAll(params);
            logView.log("INFO", "Loaded " + params.size() + " parameters for " + EaParameterManager.extractEaBaseName(expert));
        } else {
            paramTable.getItems().clear();
            logView.log("WARN", "No parameters found for " + EaParameterManager.extractEaBaseName(expert) + ". Click AutoConfig or select a valid EA.");
        }
    }

    private VBox createParamBox() {
        VBox box = new VBox(10);
        box.getStyleClass().add("sci-fi-panel");

        Label title = new Label("EA Parameters");
        title.getStyleClass().add("sci-fi-panel-title");

        paramTable = new TableView<>();
        paramTable.setStyle("-fx-background-color: transparent;");
        paramTable.setEditable(true);
        paramTable.setRowFactory(tv -> new TableRow<com.backtester.config.EaParameter>() {
            @Override
            protected void updateItem(com.backtester.config.EaParameter item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    getStyleClass().remove("opt-highlighted");
                } else if (item.isOptimizeEnabled()) {
                    if (!getStyleClass().contains("opt-highlighted")) {
                        getStyleClass().add("opt-highlighted");
                    }
                } else {
                    getStyleClass().remove("opt-highlighted");
                }
            }
        });
        
        TableColumn<com.backtester.config.EaParameter, Boolean> optCol = new TableColumn<>("Opt");
        optCol.setCellValueFactory(cellData -> {
            com.backtester.config.EaParameter param = cellData.getValue();
            javafx.beans.property.BooleanProperty property = new javafx.beans.property.SimpleBooleanProperty(param.isOptimizeEnabled());
            property.addListener((obs, oldV, newV) -> {
                param.setOptimizeEnabled(newV);
                paramTable.refresh();
                saveParametersOnDemand();
            });
            return property;
        });
        optCol.setCellFactory(javafx.scene.control.cell.CheckBoxTableCell.forTableColumn(optCol));
        optCol.setPrefWidth(40);
        
        TableColumn<com.backtester.config.EaParameter, String> nameCol = new TableColumn<>("Variable");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(200);
        
        TableColumn<com.backtester.config.EaParameter, String> valCol = new TableColumn<>("Value");
        valCol.setCellValueFactory(new PropertyValueFactory<>("value"));
        valCol.setCellFactory(createEnumAwareCellFactory());
        valCol.setOnEditCommit(e -> {
            System.out.println("[DEBUG] valCol.onEditCommit: param=" + e.getRowValue().getName() + ", old=" + e.getOldValue() + ", new=" + e.getNewValue());
            e.getRowValue().setValue(e.getNewValue());
        });
        valCol.setPrefWidth(100);
        
        TableColumn<com.backtester.config.EaParameter, String> startCol = new TableColumn<>("Start");
        startCol.setCellValueFactory(new PropertyValueFactory<>("optimizeStart"));
        startCol.setCellFactory(createEnumAwareCellFactory());
        startCol.setOnEditCommit(e -> {
            System.out.println("[DEBUG] startCol.onEditCommit: param=" + e.getRowValue().getName() + ", old=" + e.getOldValue() + ", new=" + e.getNewValue());
            e.getRowValue().setOptimizeStart(e.getNewValue());
        });
        
        TableColumn<com.backtester.config.EaParameter, String> stepCol = new TableColumn<>("Step");
        stepCol.setCellValueFactory(new PropertyValueFactory<>("optimizeStep"));
        stepCol.setCellFactory(javafx.scene.control.cell.TextFieldTableCell.forTableColumn());
        stepCol.setOnEditCommit(e -> {
            System.out.println("[DEBUG] stepCol.onEditCommit: param=" + e.getRowValue().getName() + ", old=" + e.getOldValue() + ", new=" + e.getNewValue());
            e.getRowValue().setOptimizeStep(e.getNewValue());
        });
        
        TableColumn<com.backtester.config.EaParameter, String> stopCol = new TableColumn<>("Stop");
        stopCol.setCellValueFactory(new PropertyValueFactory<>("optimizeEnd"));
        stopCol.setCellFactory(createEnumAwareCellFactory());
        stopCol.setOnEditCommit(e -> {
            System.out.println("[DEBUG] stopCol.onEditCommit: param=" + e.getRowValue().getName() + ", old=" + e.getOldValue() + ", new=" + e.getNewValue());
            e.getRowValue().setOptimizeEnd(e.getNewValue());
        });
        
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

    private static final java.util.Map<String, java.util.List<String>> KNOWN_ENUMS = new java.util.HashMap<>();
    static {
        KNOWN_ENUMS.put("typeposition", java.util.Arrays.asList("Buy & Sell", "Buy Only", "Sell Only"));
    }

    private javafx.util.Callback<TableColumn<com.backtester.config.EaParameter, String>, TableCell<com.backtester.config.EaParameter, String>> createEnumAwareCellFactory() {
        return col -> new TableCell<com.backtester.config.EaParameter, String>() {
            private ComboBox<String> comboBox;
            private TextField textField;

            @Override
            public void startEdit() {
                if (!isEmpty()) {
                    super.startEdit();
                    com.backtester.config.EaParameter param = getTableRow().getItem();
                    String lowerName = param != null && param.getName() != null ? param.getName().toLowerCase() : "";
                    
                    String currentValue = getItem() != null ? getItem().toLowerCase().trim() : "";
                    boolean isBool = "true".equals(currentValue) || "false".equals(currentValue);
                    
                    if (KNOWN_ENUMS.containsKey(lowerName) || isBool) {
                        java.util.List<String> options = KNOWN_ENUMS.containsKey(lowerName) ? 
                            KNOWN_ENUMS.get(lowerName) : java.util.Arrays.asList("false", "true");
                            
                        comboBox = new ComboBox<>(FXCollections.observableArrayList(options));
                        
                        String v = getItem();
                        if (isBool) {
                            comboBox.setValue(v != null ? v.toLowerCase() : "false");
                        } else {
                            try {
                                int idx = Integer.parseInt(v != null ? v.trim() : "0");
                                if (idx >= 0 && idx < options.size()) {
                                    comboBox.setValue(options.get(idx));
                                } else {
                                    comboBox.setValue(options.get(0));
                                }
                            } catch (Exception e) {
                                comboBox.setValue(options.get(0));
                            }
                        }

                        comboBox.valueProperty().addListener((obs, old, newVal) -> {
                            if (newVal != null) {
                                System.out.println("[DEBUG] ComboBox value change: " + newVal + " (isBool=" + isBool + ", param=" + (param != null ? param.getName() : "null") + ")");
                                if (isBool) {
                                    commitEdit(newVal);
                                } else {
                                    int idx = options.indexOf(newVal);
                                    if (idx >= 0) commitEdit(String.valueOf(idx));
                                }
                            }
                        });
                        comboBox.focusedProperty().addListener((obs, old, newVal) -> {
                            System.out.println("[DEBUG] ComboBox focus change: " + newVal + " (isEditing=" + isEditing() + ")");
                            if (!newVal && isEditing()) {
                                if (isBool) {
                                    commitEdit(comboBox.getValue());
                                } else {
                                    int idx = options.indexOf(comboBox.getValue());
                                    if (idx >= 0) commitEdit(String.valueOf(idx));
                                    else cancelEdit();
                                }
                            }
                        });
                        
                        setText(null);
                        setGraphic(comboBox);
                        comboBox.requestFocus();
                        comboBox.show();
                    } else {
                        textField = new TextField(getItem());
                        textField.setOnAction(e -> commitEdit(textField.getText()));
                        textField.focusedProperty().addListener((obs, old, newVal) -> {
                            System.out.println("[DEBUG] TextField focus change: " + newVal + " (isEditing=" + isEditing() + ")");
                            if (!newVal && isEditing()) commitEdit(textField.getText());
                        });
                        textField.setText(getItem());
                        setText(null);
                        setGraphic(textField);
                        textField.selectAll();
                        textField.requestFocus();
                    }
                }
            }

            @Override
            public void cancelEdit() {
                super.cancelEdit();
                setText(getDisplayText(getItem()));
                setGraphic(null);
            }

            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    if (isEditing()) {
                        // handling in startEdit
                    } else {
                        setText(getDisplayText(item));
                        setGraphic(null);
                    }
                }
            }
            
            private String getDisplayText(String val) {
                if (getTableRow() != null && getTableRow().getItem() != null) {
                    String lowerName = getTableRow().getItem().getName().toLowerCase();
                    if (KNOWN_ENUMS.containsKey(lowerName)) {
                        try {
                            int idx = Integer.parseInt(val != null ? val.trim() : "0");
                            java.util.List<String> options = KNOWN_ENUMS.get(lowerName);
                            if (idx >= 0 && idx < options.size()) {
                                return options.get(idx);
                            }
                        } catch (Exception e) {}
                    }
                }
                return val;
            }
        };
    }

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

    private void deleteSelectedRuns() {
        BacktestResult selected = resultsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            logView.log("WARN", "Please select a backtest result to delete.");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Deletion");
        alert.setHeaderText("Delete Selected Backtest");
        alert.setContentText("Are you sure you want to delete this backtest result?");
        java.util.Optional<ButtonType> res = alert.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            if (selected.getDbId() > 0) {
                com.backtester.database.DatabaseManager.getInstance().deleteRun(selected.getDbId());
            }
            resultsTable.getItems().remove(selected);
            logView.log("INFO", "Deleted selected backtest result from history.");
        }
    }

    private void deleteAllHistory() {
        if (resultsTable.getItems().isEmpty()) {
            logView.log("WARN", "No backtest results in history to delete.");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Deletion");
        alert.setHeaderText("Delete All Backtest History");
        alert.setContentText("Are you sure you want to delete ALL backtest results in the history?");
        java.util.Optional<ButtonType> res = alert.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            com.backtester.database.DatabaseManager.getInstance().deleteRunsByType("BACKTEST");
            resultsTable.getItems().clear();
            logView.log("INFO", "Deleted all backtest results from history.");
        }
     }

    private void saveParametersOnDemand() {
        String expert = expertField.getText().trim();
        if (expert.isEmpty()) return;
        if (paramTable != null && !paramTable.getItems().isEmpty()) {
            String symbol = symbolCombo.getValue() != null ? symbolCombo.getValue() : "EURUSD";
            String period = periodCombo.getValue() != null ? periodCombo.getValue() : "H1";
            com.backtester.database.DatabaseManager.getInstance().saveEaParameterSettings(expert, symbol, period, new com.google.gson.Gson().toJson(paramTable.getItems()));
            eaParamManager.saveCustomParameters(expert, new java.util.ArrayList<>(paramTable.getItems()));
        }
    }
}
