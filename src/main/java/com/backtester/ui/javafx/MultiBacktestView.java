package com.backtester.ui.javafx;

import com.backtester.config.AppConfig;
import com.backtester.engine.BacktestConfig;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.LocalDate;

public class MultiBacktestView {

    private final BorderPane root;
    private final AppConfig config;
    private final LogView logView;

    // Config fields
    private TextField expertField;
    private ComboBox<String> modelCombo;
    private DatePicker fromDatePicker;
    private DatePicker toDatePicker;
    private Spinner<Integer> depositSpinner;
    private ComboBox<String> currencyCombo;
    private TextField leverageField;

    // Checkbox selections
    private java.util.List<CheckBox> symbolBoxes = new java.util.ArrayList<>();
    private java.util.List<CheckBox> timeframeBoxes = new java.util.ArrayList<>();
    private TextField customSymbolField;
    private GridPane symbolsGrid;
    private TitledPane symbolsPane;
    private TitledPane timeframesPane;

    // Execution Controls
    private Button startBtn;
    private Button cancelBtn;
    private ProgressBar progress;
    private Label progressLabel;
    private com.backtester.engine.MultiBacktestRunner currentRunner;

    // Results
    private TableView<com.backtester.report.BacktestResult> resultsTable;
    private ListView<BatchRun> batchList;

    public static class BatchRun {
        private String name;
        private java.util.List<com.backtester.report.BacktestResult> results = new java.util.ArrayList<>();
        private java.nio.file.Path htmlReportPath;
        
        public BatchRun(String name) { this.name = name; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public java.util.List<com.backtester.report.BacktestResult> getResults() { return results; }
        public java.nio.file.Path getHtmlReportPath() { return htmlReportPath; }
        public void setHtmlReportPath(java.nio.file.Path htmlReportPath) { this.htmlReportPath = htmlReportPath; }
        @Override
        public String toString() { return name; }
    }

    public MultiBacktestView(LogView logView) {
        this.logView = logView;
        this.config = AppConfig.getInstance();

        root = new BorderPane();
        root.setPadding(new Insets(15));

        // Splitter
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);
        splitPane.setStyle("-fx-background-color: transparent; -fx-box-border: transparent;");

        // Top: Config
        VBox configBox = createConfigBox();
        
        // Bottom: Results
        VBox resultsBox = createResultsBox();

        splitPane.getItems().addAll(configBox, resultsBox);
        splitPane.setDividerPositions(0.45);

        root.setCenter(splitPane);
        
        loadPreferences();
    }

    private VBox createConfigBox() {
        VBox box = new VBox(15);
        box.getStyleClass().add("sci-fi-panel");

        Label title = new Label("Batch / Multi-Symbol Backtest Configuration");
        title.getStyleClass().add("sci-fi-panel-title");

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

        // Row 1: Dates
        grid.add(new Label("Dates:"), 0, 1);
        fromDatePicker = new DatePicker(LocalDate.now().minusMonths(6));
        grid.add(fromDatePicker, 1, 1);

        grid.add(new Label("To:"), 2, 1);
        toDatePicker = new DatePicker(LocalDate.now());
        grid.add(toDatePicker, 3, 1);

        // Row 2: Account
        grid.add(new Label("Deposit:"), 0, 2);
        depositSpinner = new Spinner<>(100, 10000000, config.getDefaultDeposit(), 1000);
        depositSpinner.setEditable(true);
        grid.add(depositSpinner, 1, 2);
        
        currencyCombo = new ComboBox<>(FXCollections.observableArrayList("USD", "EUR", "GBP"));
        currencyCombo.getStyleClass().add("combo-box");
        currencyCombo.setValue(config.getDefaultCurrency());
        
        leverageField = new TextField(config.getDefaultLeverage());
        leverageField.getStyleClass().add("text-input");
        leverageField.setPrefWidth(80);

        HBox accountBox = new HBox(10, currencyCombo, new Label("Lev:"), leverageField);
        accountBox.setAlignment(Pos.CENTER_LEFT);
        grid.add(accountBox, 2, 2, 2, 1);

        // Row 3: Tick Model
        grid.add(new Label("Tick Model:"), 0, 3);
        modelCombo = new ComboBox<>(FXCollections.observableArrayList(BacktestConfig.MODEL_NAMES));
        modelCombo.getStyleClass().add("combo-box");
        modelCombo.getSelectionModel().select(config.getDefaultModel());
        grid.add(modelCombo, 1, 3, 3, 1);

        // Middle: Checkbox Selections
        HBox selectionBox = new HBox(20);
        symbolsPane = createSymbolsPane();
        timeframesPane = createTimeframesPane();
        HBox.setHgrow(symbolsPane, Priority.ALWAYS);
        HBox.setHgrow(timeframesPane, Priority.ALWAYS);
        selectionBox.getChildren().addAll(symbolsPane, timeframesPane);

        // Buttons
        HBox btnBox = new HBox(15);
        btnBox.setAlignment(Pos.CENTER_LEFT);
        btnBox.setPadding(new Insets(10, 0, 0, 0));

        startBtn = new Button("▶ Start Batch");
        startBtn.getStyleClass().addAll("button", "button-start");
        startBtn.setOnAction(e -> startBatch());

        cancelBtn = new Button("⬛ Cancel");
        cancelBtn.getStyleClass().addAll("button", "button-cancel");
        cancelBtn.setDisable(true);
        cancelBtn.setOnAction(e -> cancelBatch());

        progress = new ProgressBar(0);
        progress.setPrefWidth(300);
        progressLabel = new Label("");
        progressLabel.getStyleClass().add("label");
        progressLabel.setStyle("-fx-text-fill: #b4bac8;");

        btnBox.getChildren().addAll(startBtn, cancelBtn, progress, progressLabel);

        box.getChildren().addAll(title, grid, selectionBox, btnBox);
        return box;
    }

    private TitledPane createSymbolsPane() {
        symbolsGrid = new GridPane();
        symbolsGrid.setHgap(20);
        symbolsGrid.setVgap(10);
        
        String[] symbols = {
            "EURUSD", "GBPUSD", "USDJPY", "USDCHF", "AUDUSD", "NZDUSD",
            "USDCAD", "EURGBP", "EURJPY", "GBPJPY", "AUDCAD", "AUDNZD",
            "AUDCHF", "NZDJPY", "CADJPY", "CADCHF", "XAUUSD", "XAGUSD", "XTIUSD"
        };
        
        for (String sym : symbols) {
            CheckBox cb = addSymbolCheckbox(sym);
            cb.setOnAction(e -> updateSymbolsPaneTitle());
        }
        
        HBox customBox = new HBox(10);
        customSymbolField = new TextField();
        customSymbolField.getStyleClass().add("text-input");
        HBox.setHgrow(customSymbolField, Priority.ALWAYS);
        Button addBtn = new Button("Add Custom");
        addBtn.getStyleClass().add("button");
        addBtn.setOnAction(e -> {
            String val = customSymbolField.getText().trim().toUpperCase();
            if (!val.isEmpty()) {
                boolean exists = symbolBoxes.stream().anyMatch(cb -> cb.getText().equals(val));
                if (!exists) {
                    CheckBox cb = addSymbolCheckbox(val);
                    cb.setSelected(true);
                    cb.setOnAction(evt -> updateSymbolsPaneTitle());
                    updateSymbolsPaneTitle();
                }
                customSymbolField.clear();
            }
        });
        customBox.getChildren().addAll(customSymbolField, addBtn);
        
        VBox content = new VBox(10, symbolsGrid, customBox);
        TitledPane tp = new TitledPane("Symbols: (None)", content);
        tp.setExpanded(false);
        return tp;
    }

    private void updateSymbolsPaneTitle() {
        java.util.List<String> sel = new java.util.ArrayList<>();
        for (CheckBox cb : symbolBoxes) {
            if (cb.isSelected()) sel.add(cb.getText());
        }
        String summary = sel.isEmpty() ? "(None)" : String.join(", ", sel);
        if (summary.length() > 40) summary = summary.substring(0, 37) + "...";
        if (symbolsPane != null) symbolsPane.setText("Symbols: " + summary);
    }

    private CheckBox addSymbolCheckbox(String sym) {
        CheckBox cb = new CheckBox(sym);
        cb.getStyleClass().add("check-box");
        symbolBoxes.add(cb);
        int r = (symbolBoxes.size() - 1) / 2;
        int c = (symbolBoxes.size() - 1) % 2;
        symbolsGrid.add(cb, c, r);
        return cb;
    }

    private TitledPane createTimeframesPane() {
        GridPane grid = new GridPane();
        grid.setHgap(40);
        grid.setVgap(20);
        
        String[] tfs = {"M1", "M5", "M15", "M30", "H1", "H4", "D1", "W1", "MN1"};
        
        int row = 0, col = 0;
        for (String tf : tfs) {
            CheckBox cb = new CheckBox(tf);
            cb.getStyleClass().add("check-box");
            cb.setOnAction(e -> updateTimeframesPaneTitle());
            timeframeBoxes.add(cb);
            grid.add(cb, col, row);
            col++;
            if (col > 1) { col = 0; row++; }
        }
        
        TitledPane tp = new TitledPane("Timeframes: (None)", grid);
        tp.setExpanded(false);
        return tp;
    }

    private void updateTimeframesPaneTitle() {
        java.util.List<String> sel = new java.util.ArrayList<>();
        for (CheckBox cb : timeframeBoxes) {
            if (cb.isSelected()) sel.add(cb.getText());
        }
        String summary = sel.isEmpty() ? "(None)" : String.join(", ", sel);
        if (summary.length() > 40) summary = summary.substring(0, 37) + "...";
        if (timeframesPane != null) timeframesPane.setText("Timeframes: " + summary);
    }

    private VBox createResultsBox() {
        VBox box = new VBox(10);
        box.getStyleClass().add("sci-fi-panel");
        
        Label title = new Label("Batch Results Summary");
        title.getStyleClass().add("sci-fi-panel-title");

        SplitPane splitPane = new SplitPane();
        splitPane.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        // Left Side: Batch History
        VBox leftBox = new VBox(5);
        Label historyLabel = new Label("Batch History");
        historyLabel.getStyleClass().add("label");
        
        batchList = new ListView<>();
        batchList.getStyleClass().add("sci-fi-panel");
        VBox.setVgrow(batchList, Priority.ALWAYS);
        
        batchList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            resultsTable.getItems().clear();
            if (newVal != null) {
                resultsTable.getItems().addAll(newVal.getResults());
            }
        });
        
        Button openReportBtn = new Button("🌐 Open Multi-Report Node");
        openReportBtn.getStyleClass().add("button");
        openReportBtn.setMaxWidth(Double.MAX_VALUE);
        openReportBtn.setOnAction(e -> {
            BatchRun sel = batchList.getSelectionModel().getSelectedItem();
            if (sel != null && sel.getHtmlReportPath() != null) {
                try {
                    java.awt.Desktop.getDesktop().browse(sel.getHtmlReportPath().toUri());
                } catch (Exception ex) {
                    logView.log("ERROR", "Failed to open HTML report: " + ex.getMessage());
                }
            } else {
                logView.log("WARN", "No HTML report available for this batch yet.");
            }
        });
        
        Button deleteBatchBtn = new Button("🗑 Delete Batch");
        deleteBatchBtn.getStyleClass().add("button");
        deleteBatchBtn.setMaxWidth(Double.MAX_VALUE);
        deleteBatchBtn.setOnAction(e -> {
            BatchRun sel = batchList.getSelectionModel().getSelectedItem();
            if (sel != null) {
                batchList.getItems().remove(sel);
            }
        });
        
        leftBox.getChildren().addAll(historyLabel, batchList, openReportBtn, deleteBatchBtn);

        // Right Side: Runs
        VBox rightBox = new VBox(5);
        Label runsLabel = new Label("Runs for Selected Batch");
        runsLabel.getStyleClass().add("label");
        
        resultsTable = new TableView<>();
        resultsTable.setStyle("-fx-background-color: transparent;");
        
        javafx.scene.control.TableColumn<com.backtester.report.BacktestResult, String> eaCol = new javafx.scene.control.TableColumn<>("Robot");
        eaCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("expert"));
        eaCol.setPrefWidth(120);
        
        javafx.scene.control.TableColumn<com.backtester.report.BacktestResult, String> symCol = new javafx.scene.control.TableColumn<>("Symbol");
        symCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("symbol"));
        
        javafx.scene.control.TableColumn<com.backtester.report.BacktestResult, String> perCol = new javafx.scene.control.TableColumn<>("Period");
        perCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("period"));
        
        javafx.scene.control.TableColumn<com.backtester.report.BacktestResult, Integer> tradesCol = new javafx.scene.control.TableColumn<>("Trades");
        tradesCol.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("totalTrades"));
        
        javafx.scene.control.TableColumn<com.backtester.report.BacktestResult, String> winCol = new javafx.scene.control.TableColumn<>("Win Rate");
        winCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(String.format("%.1f%%", cellData.getValue().getWinRate())));
        
        javafx.scene.control.TableColumn<com.backtester.report.BacktestResult, String> ddCol = new javafx.scene.control.TableColumn<>("Drawdown");
        ddCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(String.format("%.2f%%", cellData.getValue().getMaxDrawdown())));
        
        javafx.scene.control.TableColumn<com.backtester.report.BacktestResult, String> profCol = new javafx.scene.control.TableColumn<>("Profit");
        profCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(String.format("%.2f", cellData.getValue().getTotalProfit())));

        javafx.scene.control.TableColumn<com.backtester.report.BacktestResult, String> statusCol = new javafx.scene.control.TableColumn<>("Status");
        statusCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().isSuccess() ? "OK" : "Fail"));

        resultsTable.getColumns().addAll(eaCol, symCol, perCol, tradesCol, winCol, ddCol, profCol, statusCol);
        
        resultsTable.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                com.backtester.report.BacktestResult sel = resultsTable.getSelectionModel().getSelectedItem();
                if (sel != null && sel.getOutputDirectory() != null) {
                    com.backtester.ui.ReportViewerDialog.showForDirectory(null, sel.getOutputDirectory());
                }
            }
        });
        
        HBox tableBtnBox = new HBox(10);
        tableBtnBox.setAlignment(Pos.CENTER_RIGHT);
        
        Button showReportBtn = new Button("📊 Show Single Report");
        showReportBtn.getStyleClass().add("button");
        showReportBtn.setOnAction(e -> {
            com.backtester.report.BacktestResult sel = resultsTable.getSelectionModel().getSelectedItem();
            if (sel != null && sel.getOutputDirectory() != null) {
                com.backtester.ui.ReportViewerDialog.showForDirectory(null, sel.getOutputDirectory());
            }
        });
        
        Button delRunBtn = new Button("🗑 Delete Selected Runs");
        delRunBtn.getStyleClass().add("button");
        delRunBtn.setOnAction(e -> {
            com.backtester.report.BacktestResult sel = resultsTable.getSelectionModel().getSelectedItem();
            BatchRun batch = batchList.getSelectionModel().getSelectedItem();
            if (sel != null && batch != null) {
                batch.getResults().remove(sel);
                resultsTable.getItems().remove(sel);
            }
        });
        
        tableBtnBox.getChildren().addAll(showReportBtn, delRunBtn);
        
        VBox.setVgrow(resultsTable, Priority.ALWAYS);
        rightBox.getChildren().addAll(runsLabel, resultsTable, tableBtnBox);

        splitPane.getItems().addAll(leftBox, rightBox);
        splitPane.setDividerPositions(0.20);

        box.getChildren().addAll(title, splitPane);
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
        }
    }

    private void loadPreferences() {
        String exp = config.get("multibacktest.expert", "");
        if (!exp.isEmpty()) {
            expertField.setText(exp);
        }
        
        String syms = config.get("multibacktest.symbol", "EURUSD");
        java.util.List<String> symList = java.util.Arrays.asList(syms.split(",\\s*"));
        for (String s : symList) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) continue;
            boolean found = false;
            for (CheckBox cb : symbolBoxes) {
                if (cb.getText().equals(trimmed)) {
                    cb.setSelected(true);
                    found = true;
                    break;
                }
            }
            if (!found) {
                CheckBox cb = addSymbolCheckbox(trimmed);
                cb.setSelected(true);
                cb.setOnAction(e -> updateSymbolsPaneTitle());
            }
        }
        updateSymbolsPaneTitle();
        
        String tfs = config.get("multibacktest.timeframes", "H1");
        java.util.List<String> tfList = java.util.Arrays.asList(tfs.split(",\\s*"));
        for (CheckBox cb : timeframeBoxes) {
            if (tfList.contains(cb.getText().trim())) {
                cb.setSelected(true);
            }
        }
        updateTimeframesPaneTitle();
        
        String mod = config.get("multibacktest.model", "Every tick");
        modelCombo.setValue(mod);
    }

    private void savePreferences() {
        config.set("multibacktest.expert", expertField.getText().trim());
        
        java.util.List<String> selectedSyms = new java.util.ArrayList<>();
        for (CheckBox cb : symbolBoxes) {
            if (cb.isSelected()) selectedSyms.add(cb.getText());
        }
        config.set("multibacktest.symbol", String.join(",", selectedSyms));
        
        java.util.List<String> selectedTfs = new java.util.ArrayList<>();
        for (CheckBox cb : timeframeBoxes) {
            if (cb.isSelected()) selectedTfs.add(cb.getText());
        }
        config.set("multibacktest.timeframes", String.join(",", selectedTfs));
        
        if (modelCombo.getValue() != null) config.set("multibacktest.model", modelCombo.getValue());
        config.save();
    }

    private void startBatch() {
        String expert = expertField.getText().trim();
        if (expert.isEmpty()) {
            logView.log("ERROR", "Please specify the Expert Advisor path.");
            return;
        }

        savePreferences();

        com.backtester.engine.MultiBacktestConfig batchConfig = new com.backtester.engine.MultiBacktestConfig();
        batchConfig.setExperts(java.util.List.of(expert));

        java.util.List<String> selectedSyms = new java.util.ArrayList<>();
        for (CheckBox cb : symbolBoxes) {
            if (cb.isSelected()) selectedSyms.add(cb.getText());
        }
        if (selectedSyms.isEmpty()) {
            logView.log("ERROR", "Please select at least one symbol.");
            return;
        }
        batchConfig.setSymbols(selectedSyms);

        java.util.List<String> selectedTfs = new java.util.ArrayList<>();
        for (CheckBox cb : timeframeBoxes) {
            if (cb.isSelected()) selectedTfs.add(cb.getText());
        }
        if (selectedTfs.isEmpty()) {
            logView.log("ERROR", "Please select at least one timeframe.");
            return;
        }
        batchConfig.setPeriods(selectedTfs);

        batchConfig.setFromDate(fromDatePicker.getValue());
        batchConfig.setToDate(toDatePicker.getValue());
        batchConfig.setDeposit(depositSpinner.getValue());
        batchConfig.setCurrency(currencyCombo.getValue());
        batchConfig.setLeverage(leverageField.getText().trim());
        batchConfig.setModel(modelCombo.getSelectionModel().getSelectedIndex());

        com.backtester.config.EaParameterManager paramManager = new com.backtester.config.EaParameterManager();
        String setFileName = paramManager.prepareForBacktest(expert);
        if (setFileName != null) {
            batchConfig.setExpertParameters(expert, setFileName);
            logView.log("INFO", "Batch Config: Using parameters (" + setFileName + ")");
        } else {
            logView.log("INFO", "Batch Config: No .set file found - using EA compiled defaults");
        }

        startBtn.setDisable(true);
        cancelBtn.setDisable(false);
        progress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progressLabel.setText("Starting batch...");
        resultsTable.getItems().clear();
        
        BatchRun newBatch = new BatchRun("Batch " + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")) + " (" + batchConfig.getTotalCombinations() + " Tasks)");
        batchList.getItems().add(0, newBatch);
        batchList.getSelectionModel().select(newBatch);

        currentRunner = new com.backtester.engine.MultiBacktestRunner(batchConfig,
                msg -> javafx.application.Platform.runLater(() -> logView.log("INFO", msg)),
                (cur, tot) -> javafx.application.Platform.runLater(() -> {
                    if (cur == tot && cur > 0) {
                        progress.setProgress(1.0);
                        progressLabel.setText("Completed " + tot + " tasks");
                    } else {
                        progress.setProgress((double) (cur - 1) / tot);
                        progressLabel.setText("Running task " + cur + " of " + tot);
                    }
                }),
                res -> javafx.application.Platform.runLater(() -> {
                    newBatch.getResults().add(0, res);
                    if (batchList.getSelectionModel().getSelectedItem() == newBatch) {
                        resultsTable.getItems().add(0, res);
                    }
                })
        ) {
            @Override
            protected void done() {
                javafx.application.Platform.runLater(() -> {
                    startBtn.setDisable(false);
                    cancelBtn.setDisable(true);
                    progress.setProgress(1.0);
                    progressLabel.setText("Batch finished.");
                    
                    try {
                        java.nio.file.Path htmlPath = getGeneratedReportPath();
                        if (htmlPath != null) {
                            newBatch.setHtmlReportPath(htmlPath);
                        }
                    } catch (Exception e) {}
                    
                    batchList.refresh();
                    logView.log("INFO", "Batch execution completed.");
                });
            }
        };

        currentRunner.execute();
    }

    private void cancelBatch() {
        if (currentRunner != null) {
            currentRunner.cancel(true);
            logView.log("WARN", "Batch execution cancelled.");
        }
    }

    public BorderPane getView() {
        return root;
    }
}
