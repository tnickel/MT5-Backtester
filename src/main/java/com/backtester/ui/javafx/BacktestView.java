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

    // Backend
    private final LogView logView;
    private final EaParameterManager eaParamManager = new EaParameterManager();
    private BacktestRunner currentRunner;
    private Task<BacktestResult> currentTask;

    // UI Buttons that need state management
    private Button startBtn;
    private Button visualBtn;
    private Button cancelBtn;
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

        Label title = new Label("Backtest Configuration");
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

        // Row 1: Symbol & Period
        grid.add(new Label("Symbol:"), 0, 1);
        symbolCombo = new ComboBox<>(FXCollections.observableArrayList(
            "EURUSD", "GBPUSD", "USDJPY", "USDCHF", "AUDUSD", "NZDUSD", "USDCAD",
            "EURGBP", "EURJPY", "GBPJPY", "AUDCAD", "AUDNZD", "AUDCHF",
            "NZDJPY", "CADJPY", "CADCHF", "XAUUSD", "XAGUSD", "XTIUSD"));
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

        progress = new ProgressBar(0);
        progress.setPrefWidth(200);

        btnBox.getChildren().addAll(startBtn, visualBtn, cancelBtn, progress);

        box.getChildren().addAll(title, grid, btnBox);
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
        winCol.setCellValueFactory(cellData -> new SimpleStringProperty(String.format("%.1f%%", cellData.getValue().getWinRate())));
        
        TableColumn<BacktestResult, String> ddCol = new TableColumn<>("Drawdown");
        ddCol.setCellValueFactory(cellData -> new SimpleStringProperty(String.format("%.2f%%", cellData.getValue().getMaxDrawdown())));
        
        resultsTable.getColumns().addAll(eaCol, symCol, perCol, profCol, tradesCol, winCol, ddCol);
        
        resultsTable.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) showSelectedReport();
        });
        
        VBox.setVgrow(resultsTable, Priority.ALWAYS);

        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_RIGHT);
        
        Button openHtmlBtn = new Button("Open HTML Report");
        openHtmlBtn.getStyleClass().add("button");
        openHtmlBtn.setOnAction(e -> showSelectedReport());
        
        Button openXmlBtn = new Button("Open Directory");
        openXmlBtn.getStyleClass().add("button");
        openXmlBtn.setOnAction(e -> openDirectory());

        btnBox.getChildren().addAll(openHtmlBtn, openXmlBtn);

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
        btConfig.setModel(modelCombo.getSelectionModel().getSelectedIndex());
        btConfig.setFromDate(from);
        btConfig.setToDate(to);
        btConfig.setDeposit(depositSpinner.getValue());
        btConfig.setCurrency(currencyCombo.getValue());
        btConfig.setLeverage(leverageField.getText().trim());
        btConfig.setShutdownTerminal(!visual);

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
        if (!exp.isEmpty()) expertField.setText(exp);
        
        String sym = config.get("backtest.symbol", "EURUSD");
        symbolCombo.setValue(sym);
        
        String per = config.get("backtest.period", "H1");
        periodCombo.setValue(per);
        
        String mod = config.get("backtest.model", "Every tick");
        modelCombo.setValue(mod);
    }

    private void savePreferences() {
        config.set("backtest.expert", expertField.getText().trim());
        if (symbolCombo.getValue() != null) config.set("backtest.symbol", symbolCombo.getValue());
        if (periodCombo.getValue() != null) config.set("backtest.period", periodCombo.getValue());
        if (modelCombo.getValue() != null) config.set("backtest.model", modelCombo.getValue());
        config.save();
    }

    public BorderPane getView() {
        return root;
    }
}
