package com.backtester.ui.javafx;

import com.backtester.engine.OptimizationConfig;
import com.backtester.engine.OptimizationRunner;
import com.backtester.config.EaParameterManager;
import com.backtester.config.EaParameter;
import com.backtester.report.OptimizationResult;
import com.backtester.report.OptimizationResult.CombinedPass;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.Comparator;
import java.util.List;

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
    private Button startKeepOpenBtn;
    private Button cancelBtn;
    private ProgressBar progressBar;
    private Label progressLabel;
    private TableView<com.backtester.report.OptimizationResult.Pass> resultTable;
    private TableView<com.backtester.report.OptimizationResult.Pass> forwardTable;
    private TableView<CombinedPass> combinedTable;
    private OptimizationConfig optConfig;
    private OptimizationResult lastOptResult;

    // Combined-tab filter controls
    // Combined-tab filter values
    private double filterMinBtProfit = 0.0;
    private double filterMinFwProfit = 0.0;
    private int filterMinBtTrades = 0;
    private int filterMinFwTrades = 0;
    private double filterMaxBtDd = 100.0;
    private double filterMaxFwDd = 100.0;

    private ComboBox<String> combinedSortCombo;
    private CheckBox filterEnabledCheck;
    private CheckBox onlyMatchedCheck;
    private Label combinedCountLabel;

    // Score-Gewichtungs-Spinner
    private Spinner<Integer> wBtProfitSpin;
    private Spinner<Integer> wFwProfitSpin;
    private Spinner<Integer> wConsistSpin;
    private Spinner<Integer> wFwPfSpin;
    private Spinner<Integer> wDdSpin;
    private Label weightSumLabel;

    public OptimizationView(LogView logView) {
        this.logView = logView;
        root = new BorderPane();
        root.setPadding(new Insets(15));

        // Top Split: Config vs Parameters
        GridPane configGrid = createConfigGrid();
        VBox paramBox = createParamBox();

        HBox topBox = new HBox(15, configGrid, paramBox);
        HBox.setHgrow(paramBox, Priority.ALWAYS);
        topBox.setMinHeight(0); // Allow collapsing all the way up

        // Bottom Split: Results
        VBox resultsBox = createResultsBox();

        SplitPane mainLayout = new SplitPane(topBox, resultsBox);
        mainLayout.setOrientation(javafx.geometry.Orientation.VERTICAL);
        mainLayout.setDividerPositions(0.45);
        mainLayout.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        
        // Remove borders from the SplitPane dividers
        mainLayout.getStyleClass().add("transparent-split-pane");

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
            "EURUSD", "GBPUSD", "USDJPY", "USDCHF", "AUDUSD", "NZDUSD", "USDCAD",
            "EURGBP", "EURJPY", "GBPJPY", "AUDCAD", "AUDNZD", "AUDCHF",
            "NZDJPY", "CADJPY", "CADCHF", "XAUUSD", "XAGUSD", "XTIUSD"));
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
        
        resultTable = createResultTable();
        forwardTable = createResultTable();
        
        Tab mainTab = new Tab("Main Optimization", resultTable);
        mainTab.getStyleClass().add("tab");
        mainTab.setClosable(false);
        
        Tab forwardTab = new Tab("Forward Results", forwardTable);
        forwardTab.getStyleClass().add("tab");
        forwardTab.setClosable(false);

        Tab combinedTab = new Tab("⭐ Combined Analysis", createCombinedPane());
        combinedTab.getStyleClass().add("tab");
        combinedTab.setClosable(false);
        
        resultTabs.getTabs().addAll(mainTab, forwardTab, combinedTab);
        VBox.setVgrow(resultTabs, Priority.ALWAYS);

        HBox controlBox = new HBox(15);
        controlBox.setAlignment(Pos.CENTER_LEFT);
        
        startBtn = new Button("▶ Start Optimization");
        startBtn.getStyleClass().addAll("button", "button-start");
        startBtn.setOnAction(e -> startOptimization(true));

        startKeepOpenBtn = new Button("▶ Start (Keep MT5 Open)");
        startKeepOpenBtn.getStyleClass().addAll("button", "button-start");
        startKeepOpenBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #116b91, #0a4d6b); -fx-border-color: #1a8fbd;");
        startKeepOpenBtn.setOnAction(e -> startOptimization(false));
        
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
        
        controlBox.getChildren().addAll(startBtn, startKeepOpenBtn, cancelBtn, progressBar, progressLabel, spacer, applyBtn, openXmlBtn);

        box.getChildren().addAll(title, resultTabs, controlBox);
        return box;
    }

    // ─── Combined Analysis Pane ──────────────────────────────────────────────

    private VBox createCombinedPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(10));

        // ── Toolbar ───────────────────────────────────────────────────────────
        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("sci-fi-panel");
        topBar.setPadding(new Insets(10));

        Button filterSettingsBtn = new Button("🔍 Filter & Sortierung...");
        filterSettingsBtn.getStyleClass().add("button");
        filterSettingsBtn.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #00e5ff; -fx-border-color: #00e5ff; -fx-border-width: 1;");
        filterSettingsBtn.setOnAction(e -> showFilterDialog(filterSettingsBtn));

        Button weightSettingsBtn = new Button("⚙ Score-Gewichtung...");
        weightSettingsBtn.getStyleClass().add("button");
        weightSettingsBtn.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #ffd740; -fx-border-color: #ffd740; -fx-border-width: 1;");
        weightSettingsBtn.setOnAction(e -> showScoreWeightsDialog(weightSettingsBtn));

        combinedSortCombo = new ComboBox<>(FXCollections.observableArrayList(
            "Score (kombiniert)",
            "BT Profit (absteigend)",
            "FW Profit (absteigend)",
            "Konsistenz FW/BT (absteigend)",
            "FW Profit Factor (absteigend)",
            "FW Drawdown% (aufsteigend)",
            "Pass-Nummer"
        ));
        combinedSortCombo.getStyleClass().add("combo-box");
        combinedSortCombo.setValue("Score (kombiniert)");

        onlyMatchedCheck = new CheckBox("Nur Passes mit Forward-Ergebnis");
        onlyMatchedCheck.setSelected(true);
        onlyMatchedCheck.setStyle("-fx-text-fill: #b4bac8;");

        filterEnabledCheck = new CheckBox("Filter aktiv");
        filterEnabledCheck.setSelected(false); // standardmäßig aus, um Verwirrung bei ersten Resultaten zu vermeiden
        filterEnabledCheck.setStyle("-fx-text-fill: #00e5ff;");
        filterEnabledCheck.setOnAction(e -> applyCombinedFilter());

        Button applyFilterBtn = new Button("🔄 Aktualisieren");
        applyFilterBtn.getStyleClass().add("button");
        applyFilterBtn.setOnAction(e -> applyCombinedFilter());

        combinedCountLabel = new Label("");
        combinedCountLabel.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 11px;");

        topBar.getChildren().addAll(
            filterEnabledCheck, filterSettingsBtn, weightSettingsBtn, styledLabel("Sortierung:"), combinedSortCombo,
            onlyMatchedCheck, applyFilterBtn, combinedCountLabel
        );

        // Spinner werden lazy initialisiert (defaults) und im Dialog angezeigt
        wBtProfitSpin = makeWeightSpinner(25);
        wFwProfitSpin = makeWeightSpinner(35);
        wConsistSpin  = makeWeightSpinner(20);
        wFwPfSpin     = makeWeightSpinner(10);
        wDdSpin       = makeWeightSpinner(5);

        // ── Combined Table ────────────────────────────────────────────────────
        combinedTable = createCombinedTable();
        VBox.setVgrow(combinedTable, Priority.ALWAYS);

        pane.getChildren().addAll(topBar, combinedTable);
        VBox.setVgrow(pane, Priority.ALWAYS);
        return pane;
    }

    @SuppressWarnings("unchecked")
    private TableView<CombinedPass> createCombinedTable() {
        TableView<CombinedPass> t = new TableView<>();
        t.setStyle("-fx-background-color: transparent;");
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Score (highlight column)
        TableColumn<CombinedPass, String> scoreCol = new TableColumn<>("Score");
        scoreCol.setCellValueFactory(c -> new SimpleStringProperty(
                String.format("%.1f", c.getValue().getScore())));
        scoreCol.setStyle("-fx-alignment: CENTER;");
        scoreCol.setCellFactory(col -> new TableCell<CombinedPass, String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                double v = Double.parseDouble(item.replace(",", "."));
                if (v >= 70) setStyle("-fx-text-fill: #00e676; -fx-font-weight: bold;");
                else if (v >= 45) setStyle("-fx-text-fill: #ffd740;");
                else setStyle("-fx-text-fill: #ff5252;");
            }
        });
        scoreCol.setPrefWidth(60);

        TableColumn<CombinedPass, String> consistCol = new TableColumn<>("Konsistenz");
        consistCol.setCellValueFactory(c -> new SimpleStringProperty(
                String.format("%.2f", c.getValue().getConsistency())));
        consistCol.setStyle("-fx-alignment: CENTER;");
        consistCol.setCellFactory(col -> new TableCell<CombinedPass, String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                try {
                    double v = Double.parseDouble(item.replace(",", "."));
                    if (v >= 0.8) setStyle("-fx-text-fill: #00e676;");
                    else if (v >= 0.4) setStyle("-fx-text-fill: #ffd740;");
                    else setStyle("-fx-text-fill: #ff5252;");
                } catch (NumberFormatException ex) {
                    setStyle("");
                }
            }
        });
        consistCol.setPrefWidth(75);

        TableColumn<CombinedPass, Number> passCol = new TableColumn<>("Pass");
        passCol.setCellValueFactory(new PropertyValueFactory<>("passNumber"));
        passCol.setPrefWidth(50);

        // ── Backtest columns (blue tint header) ──
        TableColumn<CombinedPass, String> btGroup = new TableColumn<>("◀ Backtest");
        btGroup.setStyle("-fx-text-fill: #4fc3f7;");

        TableColumn<CombinedPass, String> btProfit = new TableColumn<>("Profit");
        btProfit.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f", c.getValue().getBtProfit())));
        btProfit.setCellFactory(col -> profitCell());
        btProfit.setPrefWidth(80);

        TableColumn<CombinedPass, Number> btTrades = new TableColumn<>("Trades");
        btTrades.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getBtTrades()));
        btTrades.setPrefWidth(55);

        TableColumn<CombinedPass, String> btPf = new TableColumn<>("PF");
        btPf.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.3f", c.getValue().getBtPf())));
        btPf.setPrefWidth(60);

        TableColumn<CombinedPass, String> btDd = new TableColumn<>("DD%");
        btDd.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f", c.getValue().getBtDd())));
        btDd.setCellFactory(col -> ddCell());
        btDd.setPrefWidth(60);

        btGroup.getColumns().addAll(btProfit, btTrades, btPf, btDd);

        // ── Forward columns (green tint header) ──
        TableColumn<CombinedPass, String> fwGroup = new TableColumn<>("Forward ▶");
        fwGroup.setStyle("-fx-text-fill: #69f0ae;");

        TableColumn<CombinedPass, String> fwProfit = new TableColumn<>("Profit");
        fwProfit.setCellValueFactory(c -> {
            double v = c.getValue().getFwProfit();
            return new SimpleStringProperty(Double.isNaN(v) ? "—" : String.format("%.2f", v));
        });
        fwProfit.setCellFactory(col -> profitCell());
        fwProfit.setPrefWidth(80);

        TableColumn<CombinedPass, String> fwTrades = new TableColumn<>("Trades");
        fwTrades.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getForwardPass() != null ? String.valueOf(c.getValue().getFwTrades()) : "—"));
        fwTrades.setPrefWidth(55);

        TableColumn<CombinedPass, String> fwPf = new TableColumn<>("PF");
        fwPf.setCellValueFactory(c -> {
            double v = c.getValue().getFwPf();
            return new SimpleStringProperty(Double.isNaN(v) ? "—" : String.format("%.3f", v));
        });
        fwPf.setPrefWidth(60);

        TableColumn<CombinedPass, String> fwDd = new TableColumn<>("DD%");
        fwDd.setCellValueFactory(c -> {
            double v = c.getValue().getFwDd();
            return new SimpleStringProperty(Double.isNaN(v) ? "—" : String.format("%.2f", v));
        });
        fwDd.setCellFactory(col -> ddCell());
        fwDd.setPrefWidth(60);

        fwGroup.getColumns().addAll(fwProfit, fwTrades, fwPf, fwDd);

        t.getColumns().addAll(scoreCol, consistCol, passCol, btGroup, fwGroup);

        Label placeholder = new Label("Noch keine Daten.\nStarte eine Optimierung mit Forward Test, dann hier Filter anwenden.");
        placeholder.setStyle("-fx-text-fill: #7e889a;");
        t.setPlaceholder(placeholder);

        // Double-click → Single Backtest mit diesen Parametern starten (Backtest-Pass)
        t.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                CombinedPass sel = t.getSelectionModel().getSelectedItem();
                if (sel != null) {
                    logView.log("INFO",
                        "Combined Pass #" + sel.getPassNumber() +
                        " | BT Profit: " + String.format("%.2f", sel.getBtProfit()) +
                        " | FW Profit: " + (Double.isNaN(sel.getFwProfit()) ? "—" : String.format("%.2f", sel.getFwProfit())) +
                        " | Score: " + String.format("%.1f", sel.getScore()) +
                        " | Konsistenz: " + String.format("%.2f", sel.getConsistency()));
                }
            }
        });

        return t;
    }

    /** Applies current filter settings and re-populates the combined table. */
    private void applyCombinedFilter() {
        if (lastOptResult == null || lastOptResult.getPasses().isEmpty()) {
            logView.log("WARN", "Noch keine Optimierungsdaten vorhanden.");
            return;
        }

        double minBtProfit    = filterMinBtProfit;
        double minFwProfit    = filterMinFwProfit;
        int    minBtTrades    = filterMinBtTrades;
        int    minFwTrades    = filterMinFwTrades;
        double maxBtDd        = filterMaxBtDd;
        double maxFwDd        = filterMaxFwDd;
        boolean onlyMatched   = onlyMatchedCheck.isSelected();

        // ── Score-Gewichte aus Spinnern lesen
        OptimizationResult.ScoreWeights weights = new OptimizationResult.ScoreWeights();
        weights.wBtProfit    = wBtProfitSpin.getValue() / 100.0;
        weights.wFwProfit    = wFwProfitSpin.getValue() / 100.0;
        weights.wConsistency = wConsistSpin.getValue()  / 100.0;
        weights.wFwPf        = wFwPfSpin.getValue()     / 100.0;
        // Drawdown-Strafe gilt für BT und FW gleich (halber Wert je)
        double ddHalf        = wDdSpin.getValue() / 200.0;
        weights.wBtDd        = ddHalf;
        weights.wFwDd        = ddHalf;

        List<CombinedPass> all = lastOptResult.buildCombinedPasses(onlyMatched, weights);
        System.out.println("DEBUG: BT passes: " + lastOptResult.getPasses().size());
        System.out.println("DEBUG: FW passes: " + lastOptResult.getForwardPasses().size());
        System.out.println("DEBUG: Combined passes: " + all.size());

        List<CombinedPass> filtered = all;
        if (filterEnabledCheck != null && filterEnabledCheck.isSelected()) {
            filtered = all.stream()
                .filter(cp -> cp.getBtProfit() >= minBtProfit)
                .filter(cp -> Double.isNaN(cp.getFwProfit()) || cp.getFwProfit() >= minFwProfit)
                .filter(cp -> cp.getBtTrades() >= minBtTrades)
                .filter(cp -> cp.getFwTrades() >= minFwTrades)
                .filter(cp -> cp.getBtDd() <= maxBtDd)
                .filter(cp -> Double.isNaN(cp.getFwDd()) || cp.getFwDd() <= maxFwDd)
                .collect(java.util.stream.Collectors.toList());
        }

        filtered = filtered.stream()
            .sorted(buildCombinedComparator())
            .collect(java.util.stream.Collectors.toList());

        combinedTable.getItems().setAll(filtered);
        combinedCountLabel.setText(filtered.size() + " von " + all.size() + " Passes");
        logView.log("INFO", "Combined Analysis: " + filtered.size() + " Passes | Gewichte BT=" +
            wBtProfitSpin.getValue() + "% FW=" + wFwProfitSpin.getValue() + "% Konsi=" +
            wConsistSpin.getValue() + "% FWpf=" + wFwPfSpin.getValue() + "% DD=" + wDdSpin.getValue() + "%");
    }

    private Comparator<CombinedPass> buildCombinedComparator() {
        String sort = combinedSortCombo.getValue();
        if (sort == null) return Comparator.comparingDouble(CombinedPass::getScore).reversed();
        switch (sort) {
            case "BT Profit (absteigend)":          return Comparator.comparingDouble(CombinedPass::getBtProfit).reversed();
            case "FW Profit (absteigend)":          return Comparator.comparingDouble(cp -> {
                                                        double v = cp.getFwProfit();
                                                        return Double.isNaN(v) ? Double.NEGATIVE_INFINITY : -v;
                                                    });
            case "Konsistenz FW/BT (absteigend)":  return Comparator.comparingDouble(CombinedPass::getConsistency).reversed();
            case "FW Profit Factor (absteigend)":  return Comparator.comparingDouble(cp -> {
                                                        double v = cp.getFwPf();
                                                        return Double.isNaN(v) ? Double.NEGATIVE_INFINITY : -v;
                                                    });
            case "FW Drawdown% (aufsteigend)":     return Comparator.comparingDouble(cp -> {
                                                        double v = cp.getFwDd();
                                                        return Double.isNaN(v) ? Double.MAX_VALUE : v;
                                                    });
            case "Pass-Nummer":                    return Comparator.comparingInt(CombinedPass::getPassNumber);
            default:                               return Comparator.comparingDouble(CombinedPass::getScore).reversed();
        }
    }

    /** Opens a modal dialog to configure the score weights. */
    private void showScoreWeightsDialog(javafx.scene.Node owner) {
        javafx.stage.Stage dialog = new javafx.stage.Stage();
        dialog.setTitle("Score-Gewichtung konfigurieren");
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        if (owner.getScene() != null && owner.getScene().getWindow() != null) {
            dialog.initOwner(owner.getScene().getWindow());
        }
        dialog.setResizable(false);

        VBox root = new VBox(18);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: #1a1d27;");

        // \u2500\u2500 Title
        Label title = new Label("\u2699\ufe0f  Score-Gewichtung");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        title.setTextFill(Color.web("#ffd740"));

        Label hint = new Label("Jeder Parameter wird relativ zum anderen gewichtet.\n" +
                "Die Summe muss nicht genau 100 ergeben \u2014 sie wird automatisch normalisiert.");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 11px;");

        // \u2500\u2500 Slider rows
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(12);

        // For each weight we need: label, slider, value-label
        Label[] labels = {
            dialogLabel("BT Profit"),
            dialogLabel("FW Profit"),
            dialogLabel("Konsistenz FW/BT"),
            dialogLabel("FW Profit Factor"),
            dialogLabel("Drawdown-Strafe")
        };
        String[] tooltips = {
            "Gewinn im Backtest-Zeitraum",
            "Gewinn im Forward-Zeitraum (Out-of-Sample) \u2014 h\u00f6chste Priorit\u00e4t",
            "Verh\u00e4ltnis FW/BT: 1.0 = perfekte Reproduzierbarkeit",
            "Profit Factor im Forward-Test (Handelsqualit\u00e4t)",
            "Straf-Faktor f\u00fcr hohen Drawdown (BT und FW je zur H\u00e4lfte)"
        };
        Spinner<Integer>[] spinners = new Spinner[]{wBtProfitSpin, wFwProfitSpin, wConsistSpin, wFwPfSpin, wDdSpin};

        Slider[] sliders = new Slider[5];
        Label[] valLabels = new Label[5];

        for (int i = 0; i < 5; i++) {
            Slider sl = new Slider(0, 100, spinners[i].getValue());
            sl.setMajorTickUnit(25);
            sl.setMinorTickCount(4);
            sl.setShowTickMarks(true);
            sl.setSnapToTicks(false);
            sl.setPrefWidth(260);
            sl.setStyle("-fx-control-inner-background: #2a2d3a;");
            sliders[i] = sl;

            Label vl = new Label(spinners[i].getValue() + "%");
            vl.setMinWidth(36);
            vl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
            vl.setTextFill(Color.web("#00e5ff"));
            valLabels[i] = vl;

            final int idx = i;
            sl.valueProperty().addListener((o, a, b) -> {
                int v = (int) Math.round(b.doubleValue());
                sl.setValue(v);
                valLabels[idx].setText(v + "%");
                spinners[idx].getValueFactory().setValue(v);
            });

            labels[i].setTooltip(new Tooltip(tooltips[i]));
            grid.add(labels[i],  0, i);
            grid.add(sl,         1, i);
            grid.add(vl,         2, i);
        }

        // \u2500\u2500 Live sum display
        Label sumLabel = new Label();
        sumLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        Runnable refreshSum = () -> {
            int s = 0;
            for (Spinner<Integer> sp : spinners) s += sp.getValue();
            sumLabel.setText("\u03a3 = " + s + (s == 100 ? "  \u2713 optimal" : "  (wird normalisiert)"));
            sumLabel.setTextFill(s == 100 ? Color.web("#00e676") : Color.web("#ffd740"));
        };
        for (int i = 0; i < 5; i++) {
            sliders[i].valueProperty().addListener((o, a, b) -> refreshSum.run());
        }
        refreshSum.run();

        // \u2500\u2500 Buttons
        Button resetBtn = new Button("\u21ba Zurücksetzen");
        resetBtn.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #b4bac8; -fx-border-color: #444; -fx-border-width: 1;");
        resetBtn.setOnAction(e -> {
            int[] defaults = {25, 35, 20, 10, 5};
            for (int i = 0; i < 5; i++) {
                sliders[i].setValue(defaults[i]);
            }
        });

        Button applyBtn = new Button("\u2714 Übernehmen & Schließen");
        applyBtn.setStyle("-fx-background-color: #00e5ff; -fx-text-fill: #0d0f17; -fx-font-weight: bold;");
        applyBtn.setOnAction(e -> dialog.close());

        Button cancelBtn2 = new Button("Abbrechen");
        cancelBtn2.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #b4bac8; -fx-border-color: #444; -fx-border-width: 1;");
        cancelBtn2.setOnAction(e -> {
            // Restore original values from spinners (no change)
            for (int i = 0; i < 5; i++) {
                sliders[i].setValue(spinners[i].getValue());
            }
            dialog.close();
        });

        HBox btnRow = new HBox(10, resetBtn, new Region(), applyBtn, cancelBtn2);
        HBox.setHgrow(btnRow.getChildren().get(1), Priority.ALWAYS);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        javafx.scene.control.Separator sep = new javafx.scene.control.Separator();
        sep.setStyle("-fx-background-color: #2a2d3a;");

        root.getChildren().addAll(title, hint, grid, sep, sumLabel, btnRow);

        javafx.scene.Scene scene = new javafx.scene.Scene(root, 480, 430);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    /** Opens a modal dialog to configure the filters. */
    private void showFilterDialog(javafx.scene.Node owner) {
        javafx.stage.Stage dialog = new javafx.stage.Stage();
        dialog.setTitle("Filter-Kriterien konfigurieren");
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        if (owner.getScene() != null && owner.getScene().getWindow() != null) {
            dialog.initOwner(owner.getScene().getWindow());
        }
        dialog.setResizable(false);

        VBox root = new VBox(18);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: #1a1d27;");

        Label title = new Label("🔍  Filter-Kriterien");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        title.setTextFill(Color.web("#00e5ff"));

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(10);

        TextField tfBtProfit    = makeFilterField(String.valueOf(filterMinBtProfit));
        TextField tfFwProfit    = makeFilterField(String.valueOf(filterMinFwProfit));
        TextField tfMinBtTrades = makeFilterField(String.valueOf(filterMinBtTrades));
        TextField tfMinFwTrades = makeFilterField(String.valueOf(filterMinFwTrades));
        TextField tfMaxBtDd     = makeFilterField(String.valueOf(filterMaxBtDd));
        TextField tfMaxFwDd     = makeFilterField(String.valueOf(filterMaxFwDd));

        grid.add(styledLabel("BT Profit ≥"),        0, 0); grid.add(tfBtProfit,    1, 0);
        grid.add(styledLabel("FW Profit ≥"),        2, 0); grid.add(tfFwProfit,    3, 0);
        grid.add(styledLabel("Min BT Trades ≥"),    0, 1); grid.add(tfMinBtTrades, 1, 1);
        grid.add(styledLabel("Min FW Trades ≥"),    2, 1); grid.add(tfMinFwTrades, 3, 1);
        grid.add(styledLabel("Max BT Drawdown% ≤"), 0, 2); grid.add(tfMaxBtDd,     1, 2);
        grid.add(styledLabel("Max FW Drawdown% ≤"), 2, 2); grid.add(tfMaxFwDd,     3, 2);

        Button applyBtn = new Button("✔ Anwenden & Schließen");
        applyBtn.setStyle("-fx-background-color: #00e5ff; -fx-text-fill: #0d0f17; -fx-font-weight: bold;");
        applyBtn.setOnAction(e -> {
            filterMinBtProfit = parseFilterDouble(tfBtProfit, 0.0);
            filterMinFwProfit = parseFilterDouble(tfFwProfit, 0.0);
            filterMinBtTrades = parseFilterInt(tfMinBtTrades, 0);
            filterMinFwTrades = parseFilterInt(tfMinFwTrades, 0);
            filterMaxBtDd     = parseFilterDouble(tfMaxBtDd, 100.0);
            filterMaxFwDd     = parseFilterDouble(tfMaxFwDd, 100.0);
            dialog.close();
            applyCombinedFilter();
        });

        Button resetBtn = new Button("↺ Zurücksetzen");
        resetBtn.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #b4bac8; -fx-border-color: #444; -fx-border-width: 1;");
        resetBtn.setOnAction(e -> {
            tfBtProfit.setText("0.0");
            tfFwProfit.setText("0.0");
            tfMinBtTrades.setText("0");
            tfMinFwTrades.setText("0");
            tfMaxBtDd.setText("100.0");
            tfMaxFwDd.setText("100.0");
        });

        HBox btnRow = new HBox(10, resetBtn, new Region(), applyBtn);
        HBox.setHgrow(btnRow.getChildren().get(1), Priority.ALWAYS);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        javafx.scene.control.Separator sep = new javafx.scene.control.Separator();
        sep.setStyle("-fx-background-color: #2a2d3a;");

        root.getChildren().addAll(title, grid, sep, btnRow);

        javafx.scene.Scene scene = new javafx.scene.Scene(root, 460, 250);
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private Label dialogLabel(String text) {
        Label l = new Label(text);
        l.setMinWidth(140);
        l.setStyle("-fx-text-fill: #b4bac8; -fx-font-size: 12px;");
        return l;
    }

    // ── Cell factory helpers ─────────────────────────────────────────────────

    private TableCell<CombinedPass, String> profitCell() {
        return new TableCell<CombinedPass, String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.equals("—")) { setText(item); setStyle(""); return; }
                setText(item);
                try {
                    double v = Double.parseDouble(item.replace(",", "."));
                    setStyle(v >= 0 ? "-fx-text-fill: #00e676;" : "-fx-text-fill: #ff5252;");
                } catch (NumberFormatException ex) { setStyle(""); }
            }
        };
    }

    private TableCell<CombinedPass, String> ddCell() {
        return new TableCell<CombinedPass, String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.equals("—")) { setText(item); setStyle(""); return; }
                setText(item);
                try {
                    double v = Double.parseDouble(item.replace(",", "."));
                    if (v > 25) setStyle("-fx-text-fill: #ff5252;");
                    else if (v > 15) setStyle("-fx-text-fill: #ffd740;");
                    else setStyle("-fx-text-fill: #00e676;");
                } catch (NumberFormatException ex) { setStyle(""); }
            }
        };
    }

    private TextField makeFilterField(String defaultVal) {
        TextField tf = new TextField(defaultVal);
        tf.getStyleClass().add("text-input");
        tf.setPrefWidth(70);
        return tf;
    }

    private Spinner<Integer> makeWeightSpinner(int defaultVal) {
        Spinner<Integer> sp = new Spinner<>(0, 100, defaultVal, 5);
        sp.setEditable(true);
        sp.setPrefWidth(70);
        sp.getStyleClass().add("spinner");
        return sp;
    }

    private Label styledLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #b4bac8;");
        return l;
    }

    private double parseFilterDouble(TextField tf, double fallback) {
        try { return Double.parseDouble(tf.getText().trim().replace(",", ".")); }
        catch (NumberFormatException e) { return fallback; }
    }

    private int parseFilterInt(TextField tf, int fallback) {
        try { return Integer.parseInt(tf.getText().trim()); }
        catch (NumberFormatException e) { return fallback; }
    }

    private TableView<com.backtester.report.OptimizationResult.Pass> createResultTable() {
        TableView<com.backtester.report.OptimizationResult.Pass> table = new TableView<>();
        table.setStyle("-fx-background-color: transparent;");
        
        TableColumn<com.backtester.report.OptimizationResult.Pass, Integer> passCol = new TableColumn<>("Pass");
        passCol.setCellValueFactory(new PropertyValueFactory<>("passNumber"));
        passCol.setPrefWidth(60);
        
        TableColumn<com.backtester.report.OptimizationResult.Pass, Double> profitCol = new TableColumn<>("Profit");
        profitCol.setCellValueFactory(new PropertyValueFactory<>("profit"));
        profitCol.setPrefWidth(100);
        
        TableColumn<com.backtester.report.OptimizationResult.Pass, Integer> tradesCol = new TableColumn<>("Trades");
        tradesCol.setCellValueFactory(new PropertyValueFactory<>("totalTrades"));
        tradesCol.setPrefWidth(70);
        
        TableColumn<com.backtester.report.OptimizationResult.Pass, Double> pfCol = new TableColumn<>("Profit Factor");
        pfCol.setCellValueFactory(new PropertyValueFactory<>("profitFactor"));
        pfCol.setPrefWidth(100);
        
        TableColumn<com.backtester.report.OptimizationResult.Pass, Double> payoffCol = new TableColumn<>("Expected Payoff");
        payoffCol.setCellValueFactory(new PropertyValueFactory<>("expectedPayoff"));
        payoffCol.setPrefWidth(120);
        
        TableColumn<com.backtester.report.OptimizationResult.Pass, Double> ddCol = new TableColumn<>("Drawdown %");
        ddCol.setCellValueFactory(new PropertyValueFactory<>("drawdownPercent"));
        ddCol.setPrefWidth(100);
        
        TableColumn<com.backtester.report.OptimizationResult.Pass, Double> recoveryCol = new TableColumn<>("Recovery Factor");
        recoveryCol.setCellValueFactory(new PropertyValueFactory<>("recoveryFactor"));
        recoveryCol.setPrefWidth(120);
        
        TableColumn<com.backtester.report.OptimizationResult.Pass, Double> sharpeCol = new TableColumn<>("Sharpe Ratio");
        sharpeCol.setCellValueFactory(new PropertyValueFactory<>("sharpeRatio"));
        sharpeCol.setPrefWidth(100);
        
        table.getColumns().addAll(passCol, profitCol, tradesCol, pfCol, payoffCol, ddCol, recoveryCol, sharpeCol);
        
        Label placeholder = new Label("No results yet. Run an optimization.");
        placeholder.setStyle("-fx-text-fill: #7e889a;");
        table.setPlaceholder(placeholder);
        
        return table;
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

    private void startOptimization(boolean closeTerminal) {
        savePreferences();
        
        // Save current param table to custom .set
        if (!paramTable.getItems().isEmpty()) {
            eaParamManager.saveCustomParameters(expertField.getText().trim(), new java.util.ArrayList<>(paramTable.getItems()));
        }

        this.optConfig = new OptimizationConfig();
        optConfig.setShutdownTerminal(closeTerminal);
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
        progressLabel.setText("Running...");
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
        // Simple indeterminate progress, no label updates
        currentRunner.setProgressCallback((current, total) -> {});

        currentTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                com.backtester.report.OptimizationResult result = currentRunner.runOptimization(optConfig);
                Platform.runLater(() -> handleOptimizationResult(result));
                return null;
            }
        };

        currentTask.setOnFailed(e -> {
            Throwable ex = currentTask.getException();
            logView.log("ERROR", "Task failed: " + (ex != null ? ex.getMessage() : "Unknown Error"));
            if (ex != null) ex.printStackTrace();
            setUIState(false);
            progressBar.setProgress(0);
            progressLabel.setText("Error");
        });

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
        try {
            setUIState(false);
            progressBar.setProgress(1.0);
            progressLabel.setText("Finished");
            if (result.isSuccess()) {
                if (result.getPasses().isEmpty()) {
                    logView.log("WARN", "Optimization finished, but no passes were produced.");
                } else {
                    logView.log("INFO", "Optimization finished successfully. Found " + result.getPasses().size() + " passes.");
                    
                    // Populate Tables
                    resultTable.setItems(FXCollections.observableArrayList(result.getPasses()));
                    if (result.hasForwardResults()) {
                        forwardTable.setItems(FXCollections.observableArrayList(result.getForwardPasses()));
                        logView.log("INFO", "Forward Results: " + result.getForwardPasses().size() + " passes.");
                    } else {
                        forwardTable.getItems().clear();
                    }

                    // Store result for Combined tab and auto-populate it
                    lastOptResult = result;
                    if (!result.hasForwardResults()) {
                        onlyMatchedCheck.setSelected(false);
                        logView.log("INFO", "Kein Forward Test vorhanden. Kombinierte Analyse zeigt nur Backtest-Daten.");
                    }
                    applyCombinedFilter();
                    logView.log("INFO", "Combined Analysis Tab automatisch aktualisiert.");

                    // Save to DB
                    try {
                        com.google.gson.JsonObject metrics = new com.google.gson.JsonObject();
                        metrics.addProperty("passes", result.getPasses().size());
                        metrics.addProperty("forwardPasses", result.getForwardPasses().size());
                        com.backtester.database.DatabaseManager.getInstance().saveRun(
                            "OPTIMIZATION", 
                            optConfig.getExpert(), 
                            System.currentTimeMillis(), 
                            metrics.toString(), 
                            result.getOutputDirectory()
                        );
                    } catch (Exception ex) {
                        logView.log("ERROR", "Failed to save optimization to DB: " + ex.getMessage());
                    }
                }
            } else if (result.getMessage() != null && result.getMessage().contains("cancelled")) {
                logView.log("WARN", "Optimization cancelled by user.");
            } else {
                logView.log("ERROR", "Optimization failed: " + result.getMessage());
            }
        } catch (Exception t) {
            t.printStackTrace();
            logView.log("ERROR", "UI Update crashed: " + t.getMessage());
        }
    }

    private void setUIState(boolean running) {
        startBtn.setDisable(running);
        startKeepOpenBtn.setDisable(running);
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
