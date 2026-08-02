package com.backtester.ui.javafx;

import com.backtester.engine.OptimizationConfig;
import com.backtester.engine.OptimizationRunner;
import com.backtester.engine.BacktestConfig;
import com.backtester.engine.BacktestRunner;
import com.backtester.config.EaParameterManager;
import com.backtester.config.EaParameter;
import com.backtester.config.AppConfig;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

public class OptimizationView {

    private static final Logger log = LoggerFactory.getLogger(OptimizationView.class);

    private BorderPane root;
    private final LogView logView;
    private final AppConfig config;

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
    private Label dateRangeTitleLabel;
    private Label dateRangeMonthsLabel;
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
    private Button startSenBtn;
    private Button cancelSenBtn;
    private Button clearSenBtn;
    private Button llmAnalyzeBtn;

    private ProgressBar progressBar;
    private Label progressLabel;
    private TableView<com.backtester.report.OptimizationResult.Pass> resultTable;
    private TableView<com.backtester.report.OptimizationResult.Pass> forwardTable;
    private TableView<CombinedPass> combinedTable;
    private TableView<CombinedPass> selectedTable;
    private Tab selectedTab;
    private TableView<com.backtester.report.SensitivityResult> sensitivityTable;
    private com.backtester.engine.SensitivityRunner currentSensitivityRunner;
    private OptimizationConfig optConfig;
    private OptimizationResult lastOptResult;
    private TabPane resultTabs;
    private Tab kiAnalysisTab;
    private javafx.scene.web.WebView kiWebView;
    private TableView<com.backtester.engine.KiReport> kiReportsTable;

    // Search and Master List for Selected tab
    private final javafx.collections.ObservableList<CombinedPass> masterSelectedList = javafx.collections.FXCollections.observableArrayList();
    private TextField combinedSearchField;
    private TextField selectedSearchField;

    public boolean addSelectedPass(CombinedPass p) {
        boolean exists = false;
        for (CombinedPass cp : masterSelectedList) {
            if (cp.getPassNumber() == p.getPassNumber()) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            masterSelectedList.add(p);
            saveStateToDb();
            applySelectedFilter();
            return true;
        }
        return false;
    }

    public void removeSelectedPasses(List<CombinedPass> passes) {
        masterSelectedList.removeAll(passes);
        saveStateToDb();
        applySelectedFilter();
    }

    public void clearSelectedPasses() {
        masterSelectedList.clear();
        saveStateToDb();
        applySelectedFilter();
    }

    private void applySelectedFilter() {
        if (selectedTable == null) return;
        String searchText = selectedSearchField != null ? selectedSearchField.getText().trim() : "";
        if (searchText.isEmpty()) {
            selectedTable.getItems().setAll(masterSelectedList);
        } else {
            java.util.List<CombinedPass> filtered = masterSelectedList.stream()
                .filter(cp -> String.valueOf(cp.getPassNumber()).contains(searchText))
                .collect(java.util.stream.Collectors.toList());
            selectedTable.getItems().setAll(filtered);
        }
    }

    // Combined-tab filter controls
    private double filterMinBtProfit = 0.01;
    private double filterMinFwProfit = 0.01;
    private int filterMinBtTrades = 100;
    private int filterMinFwTrades = 50;
    private double filterMaxBtDd = 100.0;
    private double filterMaxFwDd = 100.0;
    private double filterMinBtPayoff = 0.0;
    private double filterMinFwPayoff = 0.0;
    private double filterMinBtSharpe = 0.0;
    private double filterMinFwSharpe = 0.0;
    private double filterMinBtRecovery = 1.0;
    private double filterMinFwRecovery = 1.0;
    private double filterMinScore = 0.0;
    private double filterMinConsistency = 0.0;

    private ComboBox<String> combinedSortCombo;
    private CheckBox filterEnabledCheck;
    private CheckBox onlyMatchedCheck;
    private Label combinedCountLabel;

    // Unified Score-Gewichtungs-Spinner (10 Säulen)
    private Spinner<Integer> wBtProfitSpin;
    private Spinner<Integer> wFwProfitSpin;
    private Spinner<Integer> wConsistSpin;
    private Spinner<Integer> wRiskSpin;
    private Spinner<Integer> wEquityConsistSpin; // Sharpe-Ratio-Säule (DB-Key: opt.weight.equityConsist)
    private Spinner<Integer> wSampleSizeSpin;
    private Spinner<Integer> wFwTradesSpin;
    private Spinner<Integer> wRecoverySpin;
    private Label weightSumLabel;

    public OptimizationView(LogView logView) {
        this.logView = logView;
        this.config = AppConfig.getInstance();
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
        expertField.textProperty().addListener((obs, oldVal, newVal) -> loadParameters());

        symbolCombo.valueProperty().addListener((obs, oldVal, newVal) -> loadParameters());
        periodCombo.valueProperty().addListener((obs, oldVal, newVal) -> loadParameters());

        // Load state from DB after UI is built
        Platform.runLater(this::loadStateFromDb);
    }

    private GridPane createConfigGrid() {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("sci-fi-panel");
        grid.setHgap(10);
        grid.setVgap(10);

        Label title = new Label("Optimization Settings");
        title.getStyleClass().add("sci-fi-panel-title");

        String overview = "Der Optimizer-Tab ist das mächtigste analytische Instrument innerhalb der Suite, birgt aber gleichzeitig das größte Risiko für strategische Fehler. Sein Zweck ist es, durch maschinelles Ausprobieren abertausender Kombinationen die perfekten Eingabeparameter (z.B. Stop-Loss Abstände, Indikator-Perioden, Take-Profit Level) für deinen Expert Advisor zu finden.\n\n" +
                          "Warum ist Optimierung so wichtig, aber auch gefährlich? Eine Strategie kann oft von kleinen Parameter-Anpassungen massiv profitieren, da sich Märkte verändern. Die Gefahr liegt im 'Curve-Fitting' (Überanpassung). Wenn du den Optimizer zwingst, den absoluten Maximalprofit der Vergangenheit zu finden, wird er eine Parameterkombination ausspucken, die perfekt auf historische Marktrauschen zugeschnitten ist, aber im zukünftigen Live-Handel sofort versagt.\n\n" +
                          "Daher bietet dieser Tab nicht nur simple Optimierungs-Listen, sondern hochentwickelte Werkzeuge wie Forward-Testing (Out-of-Sample Validierung), Sensitivitätsanalysen und sogar KI-gestützte Auswertungen, um echte, robuste Parameter zu identifizieren, die den Test der Zeit überstehen.";
        String details = "Tiefgehende Erläuterung der Optimierungs-Features:\n\n" +
                         "1. EA Parameters & Optimization Ranges:\n" +
                         "   In dieser Tabelle siehst du alle verfügbaren Inputs deines EAs. Um einen Parameter zu optimieren, aktivierst du die Checkbox. Dann definierst du den Suchraum:\n" +
                         "   - Start: Der minimale Wert, ab dem getestet wird.\n" +
                         "   - Step: Die Schrittweite (z.B. in 5er Schritten erhöhen).\n" +
                         "   - Stop: Die Obergrenze des Tests.\n" +
                         "   Vorsicht: Je mehr Parameter gleichzeitig optimiert werden, desto exponentiell gigantischer wird die Anzahl der Kombinationen (kann leicht in die Millionen gehen).\n\n" +
                         "2. Optimization Mode (Algorithmus):\n" +
                         "   - 'Slow Complete Algorithm': Brute-Force. Testet stur jede einzelne erdenkliche Kombination aus dem definierten Suchraum. Dauert ewig, garantiert aber das Finden des absoluten Optimums.\n" +
                         "   - 'Fast Genetic Algorithm': Eine hochintelligente, evolutionäre Methode. Er testet eine zufällige Population von Parametern, selektiert die profitabelsten 'Eltern' und kreuzt/mutiert deren Parameter für die nächste Generation. Dies reduziert die Suchzeit oft von Monaten auf wenige Minuten, liefert aber 'nur' lokale Maxima.\n\n" +
                         "3. Optimization Criterion (Ziel-Metrik):\n" +
                         "   Wonach soll der genetische Algorithmus suchen? Suchst du nach 'Maximum Profit', wird er riskante Einstellungen bevorzugen. Suchst du nach 'Sharpe Ratio' oder 'Recovery Factor', wird er nach glatten, risikoarmen, kontinuierlich steigenden Equity-Kurven streben (stark empfohlen!).\n\n" +
                         "4. Forward Testing (Out-of-Sample):\n" +
                         "   Eine der wichtigsten Techniken gegen Curve-Fitting. Der historische Zeitraum wird in zwei Teile gespalten (z.B. 80% In-Sample, 20% Out-of-Sample). Der EA wird nur auf den 80% optimiert. Die besten Ergebnisse werden dann auf den ungesehenen 20% der Zukunft getestet. Nur Parameter, die in beiden Phasen profitabel sind, gelten als robust.\n\n" +
                         "5. Analytische Sub-Tabs:\n" +
                         "   - Main Optimization: Die rohe Liste aller Durchläufe (Passes). Mit 'Apply Best Parameters' werden diese als Standard für zukünftige Single-Backtests gesetzt.\n" +
                         "   - Combined Analysis: Setzt In-Sample und Forward-Ergebnisse ins Verhältnis.\n" +
                         "   - Sensitivity Analysis: Eine komplexe mathematische Prüfung. Wie stark bricht der Profit ein, wenn der Take-Profit Parameter nicht exakt 50, sondern 49 oder 51 ist? Wenn kleine Abweichungen zum Totalverlust führen, ist die Strategie instabil.\n" +
                         "   - KI Analysis: Sendet die Sensitivitätsdaten an ein Large Language Model (z.B. OpenAI/Gemini), welches dir als virtueller Data-Scientist eine menschlich lesbare Warnung oder Empfehlung zur Robustheit der Parameterlandschaft gibt.";

        javafx.scene.layout.Region infoSpacer = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(infoSpacer, javafx.scene.layout.Priority.ALWAYS);
        javafx.scene.layout.HBox titleBox = new javafx.scene.layout.HBox(15, title, infoSpacer, DocHelper.createInfoButton("Optimizer", overview, details));
        titleBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        grid.add(titleBox, 0, 0, 3, 1);

        grid.add(new Label("Expert Advisor:"), 0, 1);
        expertField = new TextField();
        expertField.getStyleClass().add("text-input");
        grid.add(expertField, 1, 1);
        Button browseBtn = new Button("...");
        browseBtn.setOnAction(e -> browseEA());
        grid.add(browseBtn, 2, 1);

        grid.add(new Label("Symbol:"), 0, 2);
        symbolCombo = new ComboBox<>(FXCollections.observableArrayList(com.backtester.engine.BacktestConfig.SYMBOLS));
        symbolCombo.getStyleClass().add("combo-box");
        grid.add(symbolCombo, 1, 2, 2, 1);

        grid.add(new Label("Period:"), 0, 3);
        periodCombo = new ComboBox<>(FXCollections.observableArrayList(
            "M1", "M5", "M15", "M30", "H1", "H4", "D1"));
        periodCombo.getStyleClass().add("combo-box");
        grid.add(periodCombo, 1, 3, 2, 1);

        dateRangeTitleLabel = new Label("Date Range:");
        grid.add(dateRangeTitleLabel, 0, 4);
        HBox dateRow = new HBox(5);
        fromDatePicker = new DatePicker();
        toDatePicker = new DatePicker();
        fromDatePicker.setPrefWidth(130);
        toDatePicker.setPrefWidth(130);

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
        fromDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> updateDateRangeMonthsLabel());
        toDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> updateDateRangeMonthsLabel());

        dateRangeMonthsLabel = new Label();
        dateRangeMonthsLabel.setStyle("-fx-text-fill: #00e5ff; -fx-font-size: 11px;");
        dateRow.getChildren().addAll(fromDatePicker, new Label(" - "), toDatePicker);
        VBox dateBox = new VBox(3, dateRow, dateRangeMonthsLabel);
        dateRangeMonthsLabel.setTranslateX(2);
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
        forwardDatePicker.disableProperty().bind(
            forwardModeCombo.getSelectionModel().selectedIndexProperty().isNotEqualTo(4)
        );
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

        paramTable.getItems().addListener((javafx.collections.ListChangeListener<EaParameter>) c -> {
            int count = paramTable.getItems().size();
            title.setText("EA Parameters & Optimization Ranges (" + count + " Parameter)");
        });
        paramTable.setRowFactory(tv -> new TableRow<EaParameter>() {
            @Override
            protected void updateItem(EaParameter item, boolean empty) {
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

        TableColumn<EaParameter, Boolean> optCol = new TableColumn<>("Opt");
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

        TableColumn<EaParameter, String> nameCol = new TableColumn<>("Variable");
        nameCol.setCellValueFactory(cellData -> {
            EaParameter param = cellData.getValue();
            String display = param.getDisplayName();
            if (display == null || display.trim().isEmpty()) {
                display = param.getName();
            }
            return new javafx.beans.property.SimpleStringProperty(display);
        });
        nameCol.setCellFactory(column -> new javafx.scene.control.TableCell<EaParameter, String>() {
            private final javafx.scene.control.Tooltip tooltip = new javafx.scene.control.Tooltip();
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item);
                    EaParameter param = getTableRow() != null ? getTableRow().getItem() : null;
                    if (param != null) {
                        tooltip.setText("Variable: " + param.getName());
                        setTooltip(tooltip);
                    } else {
                        setTooltip(null);
                    }
                }
            }
        });
        nameCol.setPrefWidth(200);

        TableColumn<EaParameter, String> valCol = new TableColumn<>("Value");
        valCol.setCellValueFactory(new PropertyValueFactory<>("value"));
        valCol.setCellFactory(EnumAwareParamCell.forTableColumn());
        valCol.setOnEditCommit(e -> e.getRowValue().setValue(e.getNewValue()));
        valCol.setPrefWidth(100);

        TableColumn<EaParameter, String> startCol = new TableColumn<>("Start");
        startCol.setCellValueFactory(new PropertyValueFactory<>("optimizeStart"));
        startCol.setCellFactory(EnumAwareParamCell.forTableColumn());
        startCol.setOnEditCommit(e -> e.getRowValue().setOptimizeStart(e.getNewValue()));

        TableColumn<EaParameter, String> stepCol = new TableColumn<>("Step");
        stepCol.setCellValueFactory(new PropertyValueFactory<>("optimizeStep"));
        stepCol.setCellFactory(EnumAwareParamCell.forTableColumn());
        stepCol.setOnEditCommit(e -> e.getRowValue().setOptimizeStep(e.getNewValue()));

        TableColumn<EaParameter, String> stopCol = new TableColumn<>("Stop");
        stopCol.setCellValueFactory(new PropertyValueFactory<>("optimizeEnd"));
        stopCol.setCellFactory(EnumAwareParamCell.forTableColumn());
        stopCol.setOnEditCommit(e -> e.getRowValue().setOptimizeEnd(e.getNewValue()));

        paramTable.getColumns().addAll(optCol, nameCol, valCol, startCol, stepCol, stopCol);

        Label placeholder = new Label("No parameters loaded.\nLoad an Expert Advisor or a .set file.");
        placeholder.setStyle("-fx-text-fill: #7e889a;");
        paramTable.setPlaceholder(placeholder);

        VBox.setVgrow(paramTable, Priority.ALWAYS);

        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_RIGHT);
        Button genConfigBtn = new Button("Gen Config");
        genConfigBtn.setOnAction(e -> generateDefaultConfig());

        Button autoConfigBtn = new Button("AutoConfig");
        autoConfigBtn.setOnAction(e -> autoConfigParameters());

        Button refreshBtn = new Button("🔄 Refresh");
        refreshBtn.setTooltip(new javafx.scene.control.Tooltip("Clear DB cache and reload parameters directly from disk defaults"));
        refreshBtn.setOnAction(e -> resetDefaults());

        Button loadBtn = new Button("Load .set");
        loadBtn.setOnAction(e -> loadFromFile());

        Button saveBtn = new Button("Save .set");
        saveBtn.setOnAction(e -> saveToFile());

        btnBox.getChildren().addAll(genConfigBtn, autoConfigBtn, refreshBtn, loadBtn, saveBtn);

        box.getChildren().addAll(title, paramTable, btnBox);
        return box;
    }

    private void resetDefaults() {
        String expert = expertField.getText().trim();
        if (expert.isEmpty()) {
            logView.log("WARN", "Please select an Expert Advisor first.");
            return;
        }
        com.backtester.database.DatabaseManager.getInstance().deleteEaParameterSettings(expert);
        java.util.List<com.backtester.config.EaParameter> params = eaParamManager.getEffectiveParameters(expert);
        if (params != null) {
            paramTable.getItems().setAll(params);
            logView.log("INFO", "Reset and reloaded " + params.size() + " parameters from disk defaults for " + EaParameterManager.extractEaBaseName(expert));
        } else {
            paramTable.getItems().clear();
            logView.log("WARN", "No default parameters found for " + EaParameterManager.extractEaBaseName(expert));
        }
    }

    private VBox createResultsBox() {
        VBox box = new VBox(10);
        box.getStyleClass().add("sci-fi-panel");

        Label title = new Label("Optimization Results");
        title.getStyleClass().add("sci-fi-panel-title");

        Button globalClearBtn = new Button("🗑️ Alles löschen");
        globalClearBtn.getStyleClass().addAll("button", "button-cancel");
        globalClearBtn.setTooltip(new javafx.scene.control.Tooltip("Alle Tabellen & KI-Analysen komplett löschen"));
        globalClearBtn.setOnAction(e -> clearAllTablesGlobal());

        Region titleSpacer = new Region();
        javafx.scene.layout.HBox.setHgrow(titleSpacer, javafx.scene.layout.Priority.ALWAYS);

        javafx.scene.layout.HBox titleBox = new javafx.scene.layout.HBox(title, titleSpacer, globalClearBtn);
        titleBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        this.resultTabs = new TabPane();
        resultTabs.getStyleClass().add("tab-pane");

        resultTable = createResultTable();
        forwardTable = createResultTable();

        Tab mainTab = new Tab("Main Optimization", resultTable);
        mainTab.getStyleClass().add("tab");
        mainTab.setClosable(false);

        Tab forwardTab = new Tab("Forward Results", forwardTable);
        forwardTab.getStyleClass().add("tab");
        forwardTab.setClosable(false);

        Tab combinedTab = new Tab("🏆 Combined Analysis", createCombinedPane());
        combinedTab.getStyleClass().add("tab");
        combinedTab.setClosable(false);

        selectedTab = new Tab("⭐ Selected", createSelectedPane());
        selectedTab.getStyleClass().add("tab");
        selectedTab.setClosable(false);

        Tab sensitivityTab = new Tab("⚖ Sensitivity Analysis", createSensitivityPane());
        sensitivityTab.getStyleClass().add("tab");
        sensitivityTab.setClosable(false);

        kiAnalysisTab = new Tab("🤖 KI Analysis", createKiAnalysisPane());
        kiAnalysisTab.getStyleClass().add("tab");
        kiAnalysisTab.setClosable(false);

        resultTabs.getTabs().addAll(mainTab, forwardTab, combinedTab, selectedTab, sensitivityTab, kiAnalysisTab);
        bindTabCounter(mainTab, "Main Optimization", resultTable);
        bindTabCounter(forwardTab, "Forward Results", forwardTable);
        bindTabCounter(combinedTab, "🏆 Combined Analysis", combinedTable);
        bindTabCounter(selectedTab, "⭐ Selected", selectedTable);
        bindTabCounter(sensitivityTab, "⚖ Sensitivity Analysis", sensitivityTable);
        bindTabCounter(kiAnalysisTab, "🤖 KI Analysis", kiReportsTable);
        VBox.setVgrow(resultTabs, Priority.ALWAYS);

        HBox controlBox = new HBox(15);
        controlBox.setAlignment(Pos.CENTER_LEFT);

        startBtn = new Button("▶ Start Optimization");
        startBtn.getStyleClass().addAll("button", "button-start");
        startBtn.setOnAction(e -> startOptimization(true));

        startKeepOpenBtn = new Button("▶ Start (Keep MT4/5 Open)");
        startKeepOpenBtn.getStyleClass().addAll("button", "button-start");
        startKeepOpenBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #116b91, #0a4d6b); -fx-border-color: #1a8fbd;");
        startKeepOpenBtn.setOnAction(e -> startOptimization(false));

        cancelBtn = new Button("⬛ Cancel");
        cancelBtn.getStyleClass().addAll("button", "button-cancel");
        cancelBtn.setDisable(true);
        cancelBtn.setOnAction(e -> cancelOptimization());

        startSenBtn = new Button("▶ Start Sensitivity Analysis");
        startSenBtn.getStyleClass().addAll("button", "button-start");
        startSenBtn.setOnAction(e -> startSensitivityAnalysis());
        startSenBtn.setVisible(false);
        startSenBtn.setManaged(false);

        cancelSenBtn = new Button("⏹ Cancel");
        cancelSenBtn.getStyleClass().addAll("button", "button-cancel");
        cancelSenBtn.setOnAction(e -> {
            if (currentSensitivityRunner != null) currentSensitivityRunner.cancel();
        });
        cancelSenBtn.setVisible(false);
        cancelSenBtn.setManaged(false);



        llmAnalyzeBtn = new Button("\uD83E\uDD16 KI-Analyse starten");
        llmAnalyzeBtn.getStyleClass().addAll("button");
        llmAnalyzeBtn.setOnAction(e -> runLlmAnalysis(llmAnalyzeBtn));
        llmAnalyzeBtn.setVisible(false);
        llmAnalyzeBtn.setManaged(false);
        updateLlmAnalyzeButtonState(false);

        Button llmSettingsBtn = new Button("\u2699 KI-Einstellungen");
        llmSettingsBtn.getStyleClass().addAll("button");
        llmSettingsBtn.setStyle("-fx-background-color: #374151; -fx-text-fill: #d1d5db; -fx-font-weight: bold;");
        llmSettingsBtn.setOnAction(e -> showLlmSettingsDialog());
        llmSettingsBtn.setVisible(false);
        llmSettingsBtn.setManaged(false);

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
        Button clearAllTablesBtn = new Button("🗑️ Tabelle löschen");
        clearAllTablesBtn.getStyleClass().addAll("button", "button-cancel");
        clearAllTablesBtn.setOnAction(e -> clearCurrentTab());

        controlBox.getChildren().addAll(startBtn, startKeepOpenBtn, cancelBtn, startSenBtn, cancelSenBtn, llmAnalyzeBtn, llmSettingsBtn, progressBar, progressLabel, spacer, applyBtn, openXmlBtn, clearAllTablesBtn);

        resultTabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            boolean isSensitivity = (newTab == sensitivityTab);
            boolean hideMainButtons = (newTab == sensitivityTab || newTab == combinedTab || newTab == selectedTab || newTab == kiAnalysisTab);
            startBtn.setVisible(!hideMainButtons);
            startBtn.setManaged(!hideMainButtons);
            startKeepOpenBtn.setVisible(!hideMainButtons);
            startKeepOpenBtn.setManaged(!hideMainButtons);
            cancelBtn.setVisible(!hideMainButtons);
            cancelBtn.setManaged(!hideMainButtons);
            applyBtn.setVisible(!hideMainButtons);
            applyBtn.setManaged(!hideMainButtons);
            openXmlBtn.setVisible(!hideMainButtons);
            openXmlBtn.setManaged(!hideMainButtons);
            clearAllTablesBtn.setVisible(true); // Always visible
            clearAllTablesBtn.setManaged(true);

            startSenBtn.setVisible(isSensitivity);
            startSenBtn.setManaged(isSensitivity);
            cancelSenBtn.setVisible(isSensitivity);
            cancelSenBtn.setManaged(isSensitivity);
            llmAnalyzeBtn.setVisible(isSensitivity);
            llmAnalyzeBtn.setManaged(isSensitivity);
            llmSettingsBtn.setVisible(isSensitivity);
            llmSettingsBtn.setManaged(isSensitivity);
        });

        box.getChildren().addAll(titleBox, resultTabs, controlBox);
        return box;
    }

    private void updateLlmAnalyzeButtonState(boolean enabled) {
        if (llmAnalyzeBtn != null) {
            llmAnalyzeBtn.setDisable(!enabled);
            if (enabled) {
                llmAnalyzeBtn.setStyle("-fx-background-color: linear-gradient(to right, #10b981, #059669); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px; -fx-cursor: hand;");
            } else {
                llmAnalyzeBtn.setStyle("-fx-background-color: #2e3543; -fx-text-fill: #6b7280; -fx-font-weight: bold; -fx-font-size: 13px; -fx-border-color: #3e4555; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-cursor: default;");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void bindTabCounter(Tab tab, String baseTitle, TableView<?> table) {
        javafx.beans.value.ChangeListener<javafx.collections.ObservableList<?>> listChangeListener = new javafx.beans.value.ChangeListener<javafx.collections.ObservableList<?>>() {
            private javafx.collections.ListChangeListener<?> currentListener = null;

            @Override
            public void changed(javafx.beans.value.ObservableValue<? extends javafx.collections.ObservableList<?>> obs, javafx.collections.ObservableList<?> oldList, javafx.collections.ObservableList<?> newList) {
                if (oldList != null && currentListener != null) {
                    oldList.removeListener((javafx.collections.ListChangeListener) currentListener);
                }
                if (newList != null) {
                    currentListener = c -> tab.setText(baseTitle + " (" + newList.size() + ")");
                    newList.addListener((javafx.collections.ListChangeListener) currentListener);
                    tab.setText(baseTitle + " (" + newList.size() + ")");
                } else {
                    tab.setText(baseTitle + " (0)");
                }
            }
        };

        table.itemsProperty().addListener((javafx.beans.value.ChangeListener) listChangeListener);

        // Trigger initially
        listChangeListener.changed((javafx.beans.value.ObservableValue) table.itemsProperty(), null, table.getItems());
    }

    private void runLlmAnalysis(Button analyzeBtn) {
        if (sensitivityTable.getItems().isEmpty()) {
            logView.log("WARN", "Keine Sensitivitätsdaten vorhanden. Bitte führe zuerst eine Analyse durch.");
            return;
        }

        analyzeBtn.setDisable(true);

        // Zeige Lade-Dialog
        javafx.stage.Stage loadingStage = new javafx.stage.Stage();
        loadingStage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        loadingStage.initStyle(javafx.stage.StageStyle.UNDECORATED);

        VBox loadBox = new VBox(15);
        loadBox.setAlignment(Pos.CENTER);
        loadBox.setStyle("-fx-background-color: #1a1d27; -fx-padding: 30; -fx-border-color: #7c3aed; -fx-border-width: 2; -fx-border-radius: 8; -fx-background-radius: 8;");

        javafx.scene.control.ProgressIndicator spinner = new javafx.scene.control.ProgressIndicator();
        spinner.setStyle("-fx-progress-color: #a78bfa;");

        Label waitLabel = new Label("KI analysiert Strategien...");
        waitLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        waitLabel.setTextFill(Color.web("#e2e8f0"));

        Label subLabel = new Label("Bitte warten, das Modell verarbeitet die Daten.");
        subLabel.setTextFill(Color.web("#94a3b8"));

        loadBox.getChildren().addAll(spinner, waitLabel, subLabel);

        javafx.scene.Scene loadScene = new javafx.scene.Scene(loadBox);
        loadScene.setFill(Color.TRANSPARENT);
        loadingStage.setScene(loadScene);

        if (root.getScene() != null) {
            loadingStage.initOwner(root.getScene().getWindow());
        }
        loadingStage.show();

        List<Integer> activePasses = new java.util.ArrayList<>();
        for (com.backtester.report.SensitivityResult item : sensitivityTable.getItems()) {
            if (item != null && item.getOriginalPass() != null) {
                activePasses.add(item.getOriginalPass().getPassNumber());
            }
        }
        String exp = expertField.getText() != null && !expertField.getText().trim().isEmpty() ? expertField.getText().trim() : "Unknown";
        String sym = symbolCombo.getValue() != null && !symbolCombo.getValue().isEmpty() ? symbolCombo.getValue() : "Unknown";
        String per = periodCombo.getValue() != null && !periodCombo.getValue().isEmpty() ? periodCombo.getValue() : "Unknown";

        // Build performance data map from sensitivity table's CombinedPass objects
        java.util.Map<Integer, com.backtester.engine.LlmAnalysisService.PassPerformance> performanceData = new java.util.HashMap<>();
        for (com.backtester.report.SensitivityResult item : sensitivityTable.getItems()) {
            if (item != null && item.getOriginalPass() != null) {
                try {
                    performanceData.put(item.getOriginalPass().getPassNumber(),
                            new com.backtester.engine.LlmAnalysisService.PassPerformance(item.getOriginalPass()));
                } catch (Exception ex) {
                    log.warn("[KI] Failed to extract performance for pass {}: {}",
                            item.getOriginalPass().getPassNumber(), ex.getMessage());
                }
            }
        }
        log.info("[KI] Built performanceData map with {} entries for {} passes", performanceData.size(), activePasses.size());

        new Thread(() -> {
            try {
                com.backtester.engine.LlmAnalysisService llmService = new com.backtester.engine.LlmAnalysisService();
                String response = llmService.analyzeStrategies(activePasses, exp, sym, performanceData);

                javafx.application.Platform.runLater(() -> {
                    loadingStage.close();
                    analyzeBtn.setDisable(false);
                    // Save the report to DB so it persists across restarts
                    long ts = System.currentTimeMillis();
                    com.backtester.database.DatabaseManager.getInstance().saveKiReport(ts, exp, sym, per, response);

                    // Log response length
                    int charCount = response.length();
                    int estimatedTokens = charCount / 4; // rough estimate: ~4 chars per token
                    logView.log("INFO", String.format("KI-Antwort: %d Zeichen (~%d Tokens)", charCount, estimatedTokens));

                    // Parse LLM response using Regex to extract STABILITY_SCORE lines for the Sensitivity Table
                    try {
                        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("STABILITY_SCORE\\|(\\d+)\\|(\\d+)");
                        java.util.regex.Matcher matcher = pattern.matcher(response);
                        int matchCount = 0;
                        int tableSize = sensitivityTable.getItems().size();
                        // Log all pass numbers currently in the table for cross-reference
                        StringBuilder passNums = new StringBuilder();
                        for (com.backtester.report.SensitivityResult item : sensitivityTable.getItems()) {
                            passNums.append(item.getOriginalPass().getPassNumber()).append(", ");
                        }
                        log.info("[KI-Parse] Sensitivity-Tabelle: {} Eintraege. Pass-Nummern: {}", tableSize, passNums);
                        log.info("[KI-Parse] Response erste 300 Zeichen: {}", response.substring(0, Math.min(300, response.length())));
                        while (matcher.find()) {
                            matchCount++;
                            try {
                                int passNum = Integer.parseInt(matcher.group(1));
                                int score = Math.max(0, Math.min(100, Integer.parseInt(matcher.group(2))));
                                boolean found = false;
                                for (com.backtester.report.SensitivityResult item : sensitivityTable.getItems()) {
                                    if (item.getOriginalPass().getPassNumber() == passNum) {
                                        item.setKiResult(String.valueOf(score));
                                        found = true;
                                    }
                                }
                                log.info("[KI-Parse] STABILITY_SCORE Pass={} Score={} -> {}", passNum, score, found ? "GESETZT" : "KEIN Match!");
                            } catch (Exception ignored) {}
                        }
                        log.info("[KI-Parse] Abgeschlossen: {} Scores gefunden, {} Tabellenzeilen.", matchCount, tableSize);
                        sensitivityTable.refresh();
                        if (combinedTable != null) combinedTable.refresh();
                        if (selectedTable != null) selectedTable.refresh();
                    } catch (Exception parseEx) {
                        logView.log("WARN", "Konnte KI-Resultate nicht für die Tabelle parsen: " + parseEx.getMessage());
                    }

                    // Persist KI scores into the saved state so they survive restarts
                    saveStateToDb();

                    // Refresh KI history table and switch to KI Analysis tab
                    refreshKiReportsTable();
                    resultTabs.getSelectionModel().select(kiAnalysisTab);
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    loadingStage.close();
                    analyzeBtn.setDisable(false);
                    logView.log("ERROR", "LLM Analyse fehlgeschlagen: " + e.getMessage());
                });
            }
        }).start();
    }

    private VBox createKiAnalysisPane() {
        VBox box = new VBox(10);
        box.setStyle("-fx-background-color: #0b0d13;");

        kiReportsTable = new TableView<>();
        kiReportsTable.getStyleClass().add("table-view");
        VBox.setVgrow(kiReportsTable, Priority.ALWAYS);

        javafx.scene.control.TableColumn<com.backtester.engine.KiReport, String> dateCol = new javafx.scene.control.TableColumn<>("Datum");
        dateCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getCreatedAt()));
        dateCol.setPrefWidth(150);

        javafx.scene.control.TableColumn<com.backtester.engine.KiReport, String> expertCol = new javafx.scene.control.TableColumn<>("Expert");
        expertCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getExpertName()));
        expertCol.setPrefWidth(200);

        javafx.scene.control.TableColumn<com.backtester.engine.KiReport, String> symbolCol = new javafx.scene.control.TableColumn<>("Symbol");
        symbolCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getSymbol()));
        symbolCol.setPrefWidth(100);

        javafx.scene.control.TableColumn<com.backtester.engine.KiReport, String> periodCol = new javafx.scene.control.TableColumn<>("Periode");
        periodCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getPeriod()));
        periodCol.setPrefWidth(100);

        kiReportsTable.getColumns().addAll(dateCol, expertCol, symbolCol, periodCol);

        box.getChildren().add(kiReportsTable);

        // Listener to open report in a new window when a row is clicked
        kiReportsTable.setOnMouseClicked(event -> {
            if (kiReportsTable.getSelectionModel().getSelectedItem() == null) return;

            // Only perform action on double click to prevent popup from stealing focus on single click
            if (event.getClickCount() == 2) {
                com.backtester.engine.KiReport selected = kiReportsTable.getSelectionModel().getSelectedItem();
                try {
                    // Parse LLM response using Regex to extract STABILITY_SCORE lines
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("STABILITY_SCORE\\|(\\d+)\\|(\\d+)");
                    java.util.regex.Matcher matcher = pattern.matcher(selected.getReportMarkdown());
                    int matchCount = 0;

                    // First clear old KI scores
                    for (com.backtester.report.SensitivityResult item : sensitivityTable.getItems()) {
                        item.setKiResult("");
                    }

                    while (matcher.find()) {
                        matchCount++;
                        try {
                            int passNum = Integer.parseInt(matcher.group(1));
                            int score = Math.max(0, Math.min(100, Integer.parseInt(matcher.group(2))));
                            for (com.backtester.report.SensitivityResult item : sensitivityTable.getItems()) {
                                if (item.getOriginalPass().getPassNumber() == passNum) {
                                    item.setKiResult(String.valueOf(score));
                                }
                            }
                        } catch (Exception e) {}
                    }

                    sensitivityTable.refresh();
                    if (combinedTable != null) combinedTable.refresh();
                    if (selectedTable != null) selectedTable.refresh();
                    logView.log("INFO", "Geparste KI-Werte aus Historie: " + matchCount + " Einträge gefunden.");

                    // Save state so it survives restart
                    saveStateToDb();
                } catch (Exception parseEx) {
                    logView.log("WARN", "Konnte KI-Resultate aus Historie nicht parsen: " + parseEx.getMessage());
                }

                // Show the report window
                showKiReportWindow(selected);
            }
        });

        refreshKiReportsTable();

        return box;
    }

    private void refreshKiReportsTable() {
        java.util.List<com.backtester.engine.KiReport> reports = com.backtester.database.DatabaseManager.getInstance().getAllKiReports();
        kiReportsTable.getItems().setAll(reports);
    }

    private void showKiReportWindow(com.backtester.engine.KiReport report) {
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("\uD83E\uDD16 KI Strategie-Analyse: " + report.getExpertName() + " | " + report.getSymbol());
        stage.initModality(javafx.stage.Modality.NONE);

        // Convert Markdown to HTML using commonmark
        org.commonmark.parser.Parser parser = org.commonmark.parser.Parser.builder().extensions(java.util.Collections.singletonList(org.commonmark.ext.gfm.tables.TablesExtension.create())).build();
        org.commonmark.node.Node document = parser.parse(report.getReportMarkdown());
        org.commonmark.renderer.html.HtmlRenderer renderer = org.commonmark.renderer.html.HtmlRenderer.builder()
                .extensions(java.util.Collections.singletonList(org.commonmark.ext.gfm.tables.TablesExtension.create()))
                .escapeHtml(true)
                .softbreak("<br />")
                .build();
        String htmlBody = renderer.render(document);

        // Wrap in styled HTML
        String fullHtml = "<!DOCTYPE html><html><head><style>" +
                "body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #0b0d13; color: #e2e8f0; line-height: 1.6; padding: 20px; }" +
                "h1, h2, h3 { color: #a78bfa; margin-top: 1.5em; }" +
                "h1 { font-size: 24px; border-bottom: 1px solid #2a2d3a; padding-bottom: 10px; }" +
                "h2 { font-size: 20px; color: #60a5fa; }" +
                "ul, ol { padding-left: 20px; }" +
                "li { margin-bottom: 5px; }" +
                "code { background-color: #1a1d27; padding: 2px 5px; border-radius: 3px; font-family: 'Consolas', monospace; }" +
                "strong { color: #f8fafc; font-weight: 600; }" +
                "blockquote { border-left: 4px solid #3b82f6; padding: 10px 15px; margin-left: 0; color: #94a3b8; background-color: #131620; border-radius: 0 4px 4px 0; font-style: italic; }" +
                "table { width: 100%; border-collapse: collapse; margin-top: 15px; margin-bottom: 15px; background-color: #131620; border-radius: 8px; overflow: hidden; }" +
                "th { background-color: #1e293b; color: #e2e8f0; padding: 12px 15px; text-align: left; font-weight: 600; border-bottom: 2px solid #334155; }" +
                "td { padding: 12px 15px; border-bottom: 1px solid #1e293b; color: #cbd5e1; }" +
                "tr:last-child td { border-bottom: none; }" +
                "tr:nth-child(even) { background-color: rgba(255, 255, 255, 0.02); }" +
                "</style></head><body>" + htmlBody + "</body></html>";

        javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
        webView.setContextMenuEnabled(false);
        webView.getEngine().loadContent(fullHtml);

        javafx.scene.Scene scene = new javafx.scene.Scene(webView, 900, 700);
        stage.setScene(scene);
        stage.show();
    }

    private void showLlmSettingsDialog() {
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("\u2699 KI-Einstellungen");
        stage.initModality(javafx.stage.Modality.WINDOW_MODAL);
        if (root.getScene() != null) {
            stage.initOwner(root.getScene().getWindow());
        }

        VBox box = new VBox(18);
        box.setStyle("-fx-background-color: #0b0d13; -fx-padding: 30;");

        Label titleLabel = new Label("OpenRouter API Konfiguration");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 23));
        titleLabel.setTextFill(Color.web("#a78bfa"));

        // API Key
        Label keyLabel = new Label("API Key:");
        keyLabel.setTextFill(Color.web("#c8cddc"));
        keyLabel.setFont(Font.font("Segoe UI", 16));
        javafx.scene.control.PasswordField keyField = new javafx.scene.control.PasswordField();
        keyField.setStyle("-fx-control-inner-background: #1a1d27; -fx-text-fill: white; -fx-prompt-text-fill: #6b7280; -fx-font-size: 15px;");
        keyField.setPromptText("sk-or-v1-...");

        com.backtester.database.DatabaseManager db = com.backtester.database.DatabaseManager.getInstance();
        String savedKey = db.getSetting(com.backtester.engine.LlmAnalysisService.SETTING_API_KEY);
        if (savedKey != null) keyField.setText(savedKey);

        // Model
        Label modelLabel = new Label("Modell:");
        modelLabel.setTextFill(Color.web("#c8cddc"));
        modelLabel.setFont(Font.font("Segoe UI", 16));
        javafx.scene.control.ComboBox<String> modelCombo = new javafx.scene.control.ComboBox<>();
        modelCombo.getItems().addAll(
            "openai/gpt-4o-mini  (max 16k output)",
            "moonshotai/kimi-k2.6  (max 64k output)",
            "anthropic/claude-3-haiku  (max 4k output)",
            "google/gemini-2.5-flash  (max 65k output)",
            "google/gemini-3-flash-preview  (max 65k output)"
        );
        modelCombo.setEditable(true);
        modelCombo.setStyle("-fx-control-inner-background: #1a1d27; -fx-text-fill: white; -fx-font-size: 15px;");
        String savedModel = db.getSetting(com.backtester.engine.LlmAnalysisService.SETTING_MODEL, com.backtester.engine.LlmAnalysisService.DEFAULT_MODEL);
        if (savedModel != null && (savedModel.contains("1.5-flash") || savedModel.contains("flash-1.5"))) {
            savedModel = "google/gemini-2.5-flash";
        }
        // Match saved model ID to display entry
        String matchedEntry = savedModel;
        if (savedModel != null) {
            for (String entry : modelCombo.getItems()) {
                if (entry.startsWith(savedModel)) {
                    matchedEntry = entry;
                    break;
                }
            }
        }
        modelCombo.setValue(matchedEntry);

        Label hintLabel = new Label("Standard: openai/gpt-4o-mini — eigene Modell-IDs sind auch möglich");
        hintLabel.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 14px;");

        // Max Tokens
        Label maxTokensLabel = new Label("Max Tokens:");
        maxTokensLabel.setTextFill(Color.web("#c8cddc"));
        maxTokensLabel.setFont(Font.font("Segoe UI", 16));
        javafx.scene.control.TextField maxTokensField = new javafx.scene.control.TextField();
        maxTokensField.setStyle("-fx-control-inner-background: #1a1d27; -fx-text-fill: white; -fx-font-size: 15px;");
        maxTokensField.setPromptText(String.valueOf(com.backtester.engine.LlmAnalysisService.DEFAULT_MAX_TOKENS));
        String savedMaxTokens = db.getSetting(com.backtester.engine.LlmAnalysisService.SETTING_MAX_TOKENS);
        maxTokensField.setText(savedMaxTokens != null && !savedMaxTokens.isBlank() ? savedMaxTokens : String.valueOf(com.backtester.engine.LlmAnalysisService.DEFAULT_MAX_TOKENS));
        Label maxTokensHint = new Label("Standard: 16384 — Erhöhen bei abgeschnittenen Antworten");
        maxTokensHint.setStyle("-fx-text-fill: #6b7280; -fx-font-size: 14px;");

        // Prompt
        Label promptLabel = new Label("System Prompt:");
        promptLabel.setTextFill(Color.web("#c8cddc"));
        promptLabel.setFont(Font.font("Segoe UI", 16));
        javafx.scene.control.TextArea promptField = new javafx.scene.control.TextArea();
        promptField.setStyle("-fx-control-inner-background: #1a1d27; -fx-text-fill: white; -fx-font-family: 'Consolas'; -fx-font-size: 15px;");
        promptField.setWrapText(true);
        promptField.setPrefRowCount(14);
        VBox.setVgrow(promptField, Priority.ALWAYS);
        String savedPrompt = db.getSetting(com.backtester.engine.LlmAnalysisService.SETTING_PROMPT, com.backtester.engine.LlmAnalysisService.DEFAULT_PROMPT);
        promptField.setText(savedPrompt);

        // Performance & Stability Weights
        Label perfWeightLabel = new Label("Gewichtung Performance (0.0 - 1.0):");
        perfWeightLabel.setTextFill(Color.web("#c8cddc"));
        perfWeightLabel.setFont(Font.font("Segoe UI", 16));
        javafx.scene.control.TextField perfWeightField = new javafx.scene.control.TextField();
        perfWeightField.setStyle("-fx-control-inner-background: #1a1d27; -fx-text-fill: white; -fx-font-size: 15px;");
        String savedPerfW = db.getSetting(com.backtester.engine.LlmAnalysisService.SETTING_PERFORMANCE_WEIGHT);
        perfWeightField.setText(savedPerfW != null && !savedPerfW.isBlank() ? savedPerfW : String.valueOf(com.backtester.engine.LlmAnalysisService.DEFAULT_PERFORMANCE_WEIGHT));

        Label stabWeightLabel = new Label("Gewichtung Stabilität (0.0 - 1.0):");
        stabWeightLabel.setTextFill(Color.web("#c8cddc"));
        stabWeightLabel.setFont(Font.font("Segoe UI", 16));
        javafx.scene.control.TextField stabWeightField = new javafx.scene.control.TextField();
        stabWeightField.setStyle("-fx-control-inner-background: #1a1d27; -fx-text-fill: white; -fx-font-size: 15px;");
        String savedStabW = db.getSetting(com.backtester.engine.LlmAnalysisService.SETTING_STABILITY_WEIGHT);
        stabWeightField.setText(savedStabW != null && !savedStabW.isBlank() ? savedStabW : String.valueOf(com.backtester.engine.LlmAnalysisService.DEFAULT_STABILITY_WEIGHT));

        // Save Button
        Button saveBtn = new Button("Speichern");
        saveBtn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 16px; -fx-padding: 8 24;");
        saveBtn.setOnAction(e -> {
            db.saveSetting(com.backtester.engine.LlmAnalysisService.SETTING_API_KEY, keyField.getText().trim());
            // Extract model ID without the "(max ...)" suffix
            String selectedModel = modelCombo.getValue().trim();
            if (selectedModel.contains("(")) {
                selectedModel = selectedModel.substring(0, selectedModel.indexOf("(")).trim();
            }
            db.saveSetting(com.backtester.engine.LlmAnalysisService.SETTING_MODEL, selectedModel);
            db.saveSetting(com.backtester.engine.LlmAnalysisService.SETTING_MAX_TOKENS, maxTokensField.getText().trim());
            db.saveSetting(com.backtester.engine.LlmAnalysisService.SETTING_PROMPT, promptField.getText().trim());

            double perfW = com.backtester.engine.LlmAnalysisService.DEFAULT_PERFORMANCE_WEIGHT;
            double stabW = com.backtester.engine.LlmAnalysisService.DEFAULT_STABILITY_WEIGHT;
            try {
                perfW = Double.parseDouble(perfWeightField.getText().trim());
                stabW = Double.parseDouble(stabWeightField.getText().trim());
            } catch (Exception ignored) {}
            db.saveSetting(com.backtester.engine.LlmAnalysisService.SETTING_PERFORMANCE_WEIGHT, String.valueOf(perfW));
            db.saveSetting(com.backtester.engine.LlmAnalysisService.SETTING_STABILITY_WEIGHT, String.valueOf(stabW));

            stage.close();
            logView.log("INFO", "KI-Einstellungen gespeichert.");
        });

        box.getChildren().addAll(titleLabel, keyLabel, keyField, modelLabel, modelCombo, hintLabel,
            perfWeightLabel, perfWeightField, stabWeightLabel, stabWeightField,
            maxTokensLabel, maxTokensField, maxTokensHint, promptLabel, promptField, saveBtn);

        javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane(box);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background: #0b0d13; -fx-background-color: #0b0d13;");

        javafx.scene.Scene scene = new javafx.scene.Scene(scrollPane, 650, 750);
        scene.setFill(Color.web("#0b0d13"));
        if (root.getScene() != null && !root.getScene().getStylesheets().isEmpty()) {
            scene.getStylesheets().addAll(root.getScene().getStylesheets());
        }
        stage.setScene(scene);
        stage.show();
    }

    private static Gson buildGson() {
        return new com.google.gson.GsonBuilder()
                .registerTypeHierarchyAdapter(javafx.beans.property.StringProperty.class,
                        new com.google.gson.TypeAdapter<javafx.beans.property.StringProperty>() {
                            @Override
                            public void write(com.google.gson.stream.JsonWriter out, javafx.beans.property.StringProperty value) throws java.io.IOException {
                                if (value == null) out.nullValue();
                                else out.value(value.get());
                            }
                            @Override
                            public javafx.beans.property.StringProperty read(com.google.gson.stream.JsonReader in) throws java.io.IOException {
                                if (in.peek() == com.google.gson.stream.JsonToken.NULL) { in.nextNull(); return new javafx.beans.property.SimpleStringProperty(""); }
                                return new javafx.beans.property.SimpleStringProperty(in.nextString());
                            }
                        })
                .create();
    }

    void saveStateToDb() {
        try {
            Gson gson = buildGson();
            String optJson = lastOptResult != null ? gson.toJson(lastOptResult) : null;

            List<Integer> selectedPassIds = new java.util.ArrayList<>();
            for (CombinedPass cp : masterSelectedList) {
                if (cp != null) {
                    selectedPassIds.add(cp.getPassNumber());
                }
            }
            String selectedJson = gson.toJson(selectedPassIds);

            String sensitivityJson = null;
            if (sensitivityTable != null) {
                sensitivityJson = gson.toJson(new java.util.ArrayList<>(sensitivityTable.getItems()));
            }

            com.backtester.database.DatabaseManager.getInstance().saveOptimizationState(optJson, selectedJson, sensitivityJson);
        } catch (Throwable t) {
            log.error("Failed to save optimization state", t);
            if (logView != null) {
                logView.log("ERROR", "Failed to save optimization state: " + t.getMessage());
            }
        }
    }

    private void loadStateFromDb() {
        try {
            String[] state = com.backtester.database.DatabaseManager.getInstance().getOptimizationState();
            if (state == null) return;

            Gson gson = buildGson();

            // 1. Load lastOptResult
            if (state[0] != null && !state[0].isEmpty()) {
                lastOptResult = gson.fromJson(state[0], OptimizationResult.class);
                if (lastOptResult != null) {
                    resultTable.setItems(FXCollections.observableArrayList(lastOptResult.getPasses()));
                    if (lastOptResult.hasForwardResults()) {
                        forwardTable.setItems(FXCollections.observableArrayList(lastOptResult.getForwardPasses()));
                    }
                    applyCombinedFilter();
                }
            }

            // 2. Load Selected Passes
            if (state[1] != null && !state[1].isEmpty() && lastOptResult != null) {
                Type listType = new TypeToken<List<Integer>>(){}.getType();
                List<Integer> selectedPassIds = gson.fromJson(state[1], listType);
                if (selectedPassIds != null && !selectedPassIds.isEmpty()) {
                    List<CombinedPass> allCombined = lastOptResult.buildCombinedPasses(onlyMatchedCheck.isSelected(), OptimizationResult.ScoreWeights.defaults());
                    masterSelectedList.clear();
                    for (Integer id : selectedPassIds) {
                        for (CombinedPass cp : allCombined) {
                            if (cp.getPassNumber() == id) {
                                masterSelectedList.add(cp);
                                break;
                            }
                        }
                    }
                    applySelectedFilter();
                }
            }

            // 3. Load Sensitivity Results
            if (state[2] != null && !state[2].isEmpty()) {
                Type senListType = new TypeToken<List<com.backtester.report.SensitivityResult>>(){}.getType();
                List<com.backtester.report.SensitivityResult> senResults = gson.fromJson(state[2], senListType);
                if (senResults != null) {
                    sensitivityTable.setItems(FXCollections.observableArrayList(senResults));
                    updateLlmAnalyzeButtonState(!senResults.isEmpty());
                } else {
                    updateLlmAnalyzeButtonState(false);
                }
            } else {
                updateLlmAnalyzeButtonState(false);
            }

            logView.log("INFO", "Optimization state loaded from database.");
        } catch (Exception e) {
            logView.log("ERROR", "Failed to load optimization state: " + e.getMessage());
        }
    }

    private void clearCurrentTab() {
        Tab current = resultTabs.getSelectionModel().getSelectedItem();
        if (current == null) return;

        javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION,
            "Möchtest du wirklich die aktuelle Tabelle (" + current.getText().replaceAll("[⭐🏆⚖🤖]", "").trim() + ") leeren?",
            javafx.scene.control.ButtonType.YES, javafx.scene.control.ButtonType.NO);
        confirm.setHeaderText("Tabelle löschen");

        if (confirm.showAndWait().orElse(javafx.scene.control.ButtonType.NO) == javafx.scene.control.ButtonType.YES) {
            String txt = current.getText();
            if (txt.contains("Main")) resultTable.getItems().clear();
            else if (txt.contains("Forward")) forwardTable.getItems().clear();
            else if (txt.contains("Combined")) combinedTable.getItems().clear();
            else if (txt.contains("Selected")) clearSelectedPasses();
            else if (txt.contains("Sensitivity")) {
                sensitivityTable.getItems().clear();
                updateLlmAnalyzeButtonState(false);
            }
            else if (txt.contains("KI")) {
                com.backtester.database.DatabaseManager.getInstance().clearKiReports();
                refreshKiReportsTable();
                logView.log("INFO", "KI-Historie gelöscht.");
            }
        }
    }

    private void clearAllTablesGlobal() {
        javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION,
            "Möchtest du wirklich ALLE Optimierungstabellen UND die gesamte KI-Historie aus der Datenbank löschen?",
            javafx.scene.control.ButtonType.YES, javafx.scene.control.ButtonType.NO);
        confirm.setHeaderText("Alles löschen");

        if (confirm.showAndWait().orElse(javafx.scene.control.ButtonType.NO) == javafx.scene.control.ButtonType.YES) {
            resultTable.getItems().clear();
            forwardTable.getItems().clear();
            combinedTable.getItems().clear();
            masterSelectedList.clear();
            selectedTable.getItems().clear();
            sensitivityTable.getItems().clear();
            lastOptResult = null;
            combinedCountLabel.setText("");
            progressLabel.setText("0 / 0 Passes");
            updateLlmAnalyzeButtonState(false);
            com.backtester.database.DatabaseManager.getInstance().clearOptimizationState();
            com.backtester.database.DatabaseManager.getInstance().clearKiReports();
            refreshKiReportsTable();
            logView.log("INFO", "Alle Tabellen und KI-Historie gelöscht.");
        }
    }

    // ─── Combined Analysis Pane ──────────────────────────────────────────────

    private VBox createCombinedPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(10));

        // ── Toolbar ───────────────────────────────────────────────────────────
        VBox toolbarContainer = new VBox(8);
        toolbarContainer.getStyleClass().add("sci-fi-panel");
        toolbarContainer.setPadding(new Insets(10));

        HBox filterRow = new HBox(12);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        HBox actionRow = new HBox(12);
        actionRow.setAlignment(Pos.CENTER_LEFT);

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
        onlyMatchedCheck.setOnAction(e -> {
            com.backtester.database.DatabaseManager.getInstance().saveSetting("opt.filter.onlyMatched", String.valueOf(onlyMatchedCheck.isSelected()));
            applyCombinedFilter();
        });

        filterEnabledCheck = new CheckBox("Filter aktiv");
        filterEnabledCheck.setSelected(false); // standardmäßig aus, um Verwirrung bei ersten Resultaten zu vermeiden
        filterEnabledCheck.setStyle("-fx-text-fill: #00e5ff;");
        filterEnabledCheck.setOnAction(e -> {
            com.backtester.database.DatabaseManager.getInstance().saveSetting("opt.filter.enabled", String.valueOf(filterEnabledCheck.isSelected()));
            applyCombinedFilter();
        });

        Button applyFilterBtn = new Button("🔄 Aktualisieren");
        applyFilterBtn.getStyleClass().add("button");
        applyFilterBtn.setOnAction(e -> applyCombinedFilter());

        Button delPassBtn = new Button("🗑 Markierte Zeilen entfernen");
        delPassBtn.getStyleClass().addAll("button", "button-cancel");
        delPassBtn.setOnAction(e -> deleteSelectedCombinedPasses());

        Button selectStrategiesBtn = new Button("⭐ Select Strategies");
        selectStrategiesBtn.getStyleClass().add("button");
        selectStrategiesBtn.setOnAction(e -> {
            java.util.List<CombinedPass> selected = combinedTable.getSelectionModel().getSelectedItems();
            if (selected == null || selected.isEmpty()) return;
            for (CombinedPass p : selected) {
                addSelectedPass(p);
            }
            new Alert(Alert.AlertType.INFORMATION, "Strategien erfolgreich zum 'Selected' Tab hinzugefügt!").show();
        });

        Button evaluatorBtn = new Button("📊 Advanced Evaluator");
        evaluatorBtn.getStyleClass().add("button");
        evaluatorBtn.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #69f0ae; -fx-border-color: #69f0ae; -fx-border-width: 1;");
        evaluatorBtn.setOnAction(e -> showStrategyEvaluatorDialog());

        combinedCountLabel = new Label("");
        combinedCountLabel.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 11px;");

        Label searchLabel = new Label("🔍 Pass:");
        searchLabel.setStyle("-fx-text-fill: #00e5ff; -fx-font-weight: bold;");
        combinedSearchField = new TextField();
        combinedSearchField.setPromptText("Pass #...");
        combinedSearchField.setPrefWidth(90);
        combinedSearchField.getStyleClass().add("text-input");
        combinedSearchField.textProperty().addListener((obs, oldVal, newVal) -> applyCombinedFilter());

        filterRow.getChildren().addAll(
            filterEnabledCheck, filterSettingsBtn, weightSettingsBtn, styledLabel("Sortierung:"), combinedSortCombo,
            onlyMatchedCheck, applyFilterBtn
        );

        Button mainInfoBtn = DocHelper.createThickCircularInfoButton("Erklärung aller Indizes und Kennzahlen", () -> {
            DocHelper.showAllIndicesDocDialog(evaluatorBtn.getScene() != null ? evaluatorBtn.getScene().getWindow() : null);
        });

        actionRow.getChildren().addAll(
            selectStrategiesBtn, evaluatorBtn, mainInfoBtn, delPassBtn, searchLabel, combinedSearchField, combinedCountLabel
        );

        toolbarContainer.getChildren().addAll(filterRow, actionRow);

        // Defaults kommen aus der einzigen Quelle ScoreWeights.defaults()
        OptimizationResult.ScoreWeights wDef = OptimizationResult.ScoreWeights.defaults();
        wBtProfitSpin     = makeWeightSpinner((int) wDef.wBtProfit);
        wFwProfitSpin     = makeWeightSpinner((int) wDef.wFwProfit);
        wConsistSpin      = makeWeightSpinner((int) wDef.wConsistency);
        wRiskSpin         = makeWeightSpinner((int) wDef.wRisk);
        wEquityConsistSpin = makeWeightSpinner((int) wDef.wEquityConsist);
        wSampleSizeSpin   = makeWeightSpinner((int) wDef.wSampleSize);
        wFwTradesSpin     = makeWeightSpinner((int) wDef.wFwTrades);
        wRecoverySpin     = makeWeightSpinner((int) wDef.wRecovery);

        // ── Combined Table ────────────────────────────────────────────────────
        combinedTable = createCombinedTable();
        VBox.setVgrow(combinedTable, Priority.ALWAYS);

        pane.getChildren().addAll(toolbarContainer, combinedTable);
        VBox.setVgrow(pane, Priority.ALWAYS);
        return pane;
    }

    private OptimizationResult.ScoreWeights getScoreWeightsFromUI() {
        OptimizationResult.ScoreWeights weights = new OptimizationResult.ScoreWeights();
        weights.wBtProfit      = wBtProfitSpin.getValue();
        weights.wFwProfit      = wFwProfitSpin.getValue();
        weights.wConsistency   = wConsistSpin.getValue();
        weights.wRisk          = wRiskSpin.getValue();
        weights.wEquityConsist = wEquityConsistSpin.getValue();
        weights.wSampleSize    = wSampleSizeSpin.getValue();
        weights.wFwTrades      = wFwTradesSpin.getValue();
        weights.wRecovery      = wRecoverySpin.getValue();

        com.backtester.database.DatabaseManager db = com.backtester.database.DatabaseManager.getInstance();
        weights.recoveryMin    = Double.parseDouble(db.getSetting("opt.weight.recovery.min", "1.0"));
        weights.recoveryMax    = Double.parseDouble(db.getSetting("opt.weight.recovery.max", "5.0"));

        return weights;
    }

    private void showStrategyEvaluatorDialog() {
        if (lastOptResult == null) {
            new Alert(Alert.AlertType.WARNING, "Keine Optimierungsergebnisse geladen. Führe erst eine Optimierung durch!").show();
            return;
        }
        List<CombinedPass> allCombined = lastOptResult.buildCombinedPasses(onlyMatchedCheck.isSelected(), getScoreWeightsFromUI());
        if (allCombined == null || allCombined.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Keine kombinierten Ergebnisse zum Evaluieren vorhanden.").show();
            return;
        }

        StrategyEvaluatorDialog dialog = new StrategyEvaluatorDialog(allCombined, this);
        dialog.initOwner(root.getScene().getWindow());
        dialog.show();
    }

    private java.util.Comparator<String> numericStringComparator() {
        return (s1, s2) -> {
            if (s1 == s2) return 0;
            if (s1 == null || s1.trim().isEmpty() || s1.equals("—") || s1.equals("-")) return -1;
            if (s2 == null || s2.trim().isEmpty() || s2.equals("—") || s2.equals("-")) return 1;
            try {
                double d1 = Double.parseDouble(s1.replace(" %", "").replace(",", "."));
                double d2 = Double.parseDouble(s2.replace(" %", "").replace(",", "."));
                return Double.compare(d1, d2);
            } catch (NumberFormatException e) {
                return s1.compareTo(s2);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private TableView<CombinedPass> createCombinedTable() {
        TableView<CombinedPass> t = new TableView<>();
        t.setStyle("-fx-background-color: transparent;");
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        t.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Score (highlight column)
        TableColumn<CombinedPass, String> scoreCol = new TableColumn<>();
        scoreCol.setGraphic(DocHelper.createHeaderWithTooltip("Score",
            "Unified Score (0-100):\nGewichteter Gesamtwert aus 10 Kriterien. Klicke auf das ⓘ Symbol, um den Mindest-Score-Filter anzupassen und die Doku zu öffnen.",
            () -> showScoreDoc()));
        scoreCol.setCellValueFactory(c -> new SimpleStringProperty(
                String.format("%.2f", c.getValue().getScore())));
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
        scoreCol.setPrefWidth(75);
        // Direct comparator: compare CombinedPass.getScore() directly to avoid re-evaluating cellValueFactory
        scoreCol.setComparator((s1, s2) -> {
            try {
                return Double.compare(Double.parseDouble(s1.replace(",", ".")), Double.parseDouble(s2.replace(",", ".")));
            } catch (NumberFormatException e) { return 0; }
        });

        TableColumn<CombinedPass, String> consistCol = new TableColumn<>();
        consistCol.setGraphic(DocHelper.createHeaderWithTooltip("Konsistenz",
            "Forward-Konsistenz (0.0-2.0):\nVerhältnis der Performance im Forward-Test zum Backtest. Klicke auf das ⓘ Symbol, um den Mindest-Konsistenz-Filter anzupassen und die Doku zu öffnen.",
            () -> showConsistencyDoc()));
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
        consistCol.setPrefWidth(95);
        consistCol.setComparator(numericStringComparator());



        TableColumn<CombinedPass, Number> passCol = new TableColumn<>("Pass");
        passCol.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getPassNumber()));
        passCol.setPrefWidth(50);

        // ── Backtest columns (blue tint header) ──
        TableColumn<CombinedPass, String> btGroup = new TableColumn<>("◀ Backtest");
        btGroup.setStyle("-fx-text-fill: #4fc3f7;");

        TableColumn<CombinedPass, String> btProfit = new TableColumn<>("Profit");
        btProfit.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f", c.getValue().getBtProfit())));
        btProfit.setCellFactory(col -> profitCell());
        btProfit.setPrefWidth(80);
        btProfit.setComparator(numericStringComparator());

        TableColumn<CombinedPass, Number> btTrades = new TableColumn<>("Trades");
        btTrades.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getBtTrades()));
        btTrades.setPrefWidth(55);

        TableColumn<CombinedPass, String> btPf = new TableColumn<>("PF");
        btPf.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f", c.getValue().getBtPf())));
        btPf.setPrefWidth(60);
        btPf.setComparator(numericStringComparator());

        TableColumn<CombinedPass, String> btDd = new TableColumn<>("DD%");
        btDd.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f", c.getValue().getBtDd())));
        btDd.setCellFactory(col -> ddCell());
        btDd.setPrefWidth(60);

        TableColumn<CombinedPass, String> btRecovery = new TableColumn<>("Erholung");
        btRecovery.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f", c.getValue().getBtRecovery())));
        btRecovery.setPrefWidth(65);
        btRecovery.setComparator(numericStringComparator());

        btGroup.getColumns().addAll(btProfit, btTrades, btPf, btDd, btRecovery);

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
        fwProfit.setComparator(numericStringComparator());

        TableColumn<CombinedPass, String> fwTrades = new TableColumn<>("Trades");
        fwTrades.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getForwardPass() != null ? String.valueOf(c.getValue().getFwTrades()) : "—"));
        fwTrades.setPrefWidth(55);
        fwTrades.setComparator(numericStringComparator());

        TableColumn<CombinedPass, String> fwPf = new TableColumn<>("PF");
        fwPf.setCellValueFactory(c -> {
            double v = c.getValue().getFwPf();
            return new SimpleStringProperty(Double.isNaN(v) ? "—" : String.format("%.2f", v));
        });
        fwPf.setPrefWidth(60);
        fwPf.setComparator(numericStringComparator());

        TableColumn<CombinedPass, String> fwDd = new TableColumn<>("DD%");
        fwDd.setCellValueFactory(c -> {
            double v = c.getValue().getFwDd();
            return new SimpleStringProperty(Double.isNaN(v) ? "—" : String.format("%.2f", v));
        });
        fwDd.setCellFactory(col -> ddCell());
        fwDd.setPrefWidth(60);
        fwDd.setComparator(numericStringComparator());

        TableColumn<CombinedPass, String> fwRecovery = new TableColumn<>("Erholung");
        fwRecovery.setCellValueFactory(c -> {
            double v = c.getValue().getFwRecovery();
            return new SimpleStringProperty(Double.isNaN(v) ? "—" : String.format("%.2f", v));
        });
        fwRecovery.setPrefWidth(65);
        fwRecovery.setComparator(numericStringComparator());

        fwGroup.getColumns().addAll(fwProfit, fwTrades, fwPf, fwDd, fwRecovery);

        // Build a lookup map for KI scores so sorting doesn't iterate the sensitivityTable O(n) per comparison
        TableColumn<CombinedPass, String> kiCol = new TableColumn<>();
        kiCol.setGraphic(DocHelper.createHeaderWithTooltip("KI",
            "KI-Stabilitätsscore (0-100):\nDas qualitative Urteil der künstlichen Intelligenz (LLM) über die Form und Stabilität der Parameter-Kennlinien. Erkennt Curve-Fitting (Überoptimierung)."));
        kiCol.setCellValueFactory(c -> {
            int pn = c.getValue().getPassNumber();
            String kiScore = "";
            for (com.backtester.report.SensitivityResult sr : sensitivityTable.getItems()) {
                if (sr.getOriginalPass().getPassNumber() == pn) {
                    kiScore = sr.getKiResult();
                    break;
                }
            }
            return new javafx.beans.property.SimpleStringProperty(kiScore);
        });
        kiCol.setStyle("-fx-alignment: CENTER;");
        kiCol.setCellFactory(col -> new TableCell<CombinedPass, String>() {
            @Override protected void updateItem(String kiScore, boolean empty) {
                super.updateItem(kiScore, empty);
                if (empty || kiScore == null || kiScore.isEmpty()) {
                    setText(null); setStyle("-fx-alignment: CENTER;"); return;
                }

                setText(kiScore + " / 100");
                try {
                    int v = Integer.parseInt(kiScore.trim());
                    if (v >= 70) setStyle("-fx-alignment: CENTER; -fx-text-fill: #00e676; -fx-font-weight: bold;");
                    else if (v >= 50) setStyle("-fx-alignment: CENTER; -fx-text-fill: #ffd740;");
                    else if (v >= 30) setStyle("-fx-alignment: CENTER; -fx-text-fill: #ff9100;");
                    else setStyle("-fx-alignment: CENTER; -fx-text-fill: #ff5252; -fx-font-weight: bold;");
                } catch (NumberFormatException e) {
                    setStyle("-fx-alignment: CENTER;");
                }
            }
        });
        kiCol.setPrefWidth(60);
        kiCol.setComparator(numericStringComparator());

        TableColumn<CombinedPass, String> robScoreCol = new TableColumn<>();
        robScoreCol.setGraphic(DocHelper.createHeaderWithTooltip("Rob. Scorecard",
            "Robustness Scorecard (0-100):\nErgebnis des Monte-Carlo-Stresstests und systematischen Parameter-Shifting. Simuliert Rauschen (Slippage, Spread, Execution) und bewertet die Geradlinigkeit (R²-Stabilität) der Equity-Kurve."));
        robScoreCol.setCellValueFactory(c -> {
            String fromDateStr = "Unbekannt";
            String toDateStr = "Unbekannt";
            if (lastOptResult != null) {
                if (lastOptResult.getFromDate() != null && !lastOptResult.getFromDate().isEmpty()) {
                    fromDateStr = lastOptResult.getFromDate();
                }
                if (lastOptResult.getToDate() != null && !lastOptResult.getToDate().isEmpty()) {
                    toDateStr = lastOptResult.getToDate();
                }
            } else if (fromDatePicker != null && fromDatePicker.getValue() != null) {
                fromDateStr = fromDatePicker.getValue().toString();
                if (toDatePicker != null && toDatePicker.getValue() != null) {
                    toDateStr = toDatePicker.getValue().toString();
                }
            }
            double score = c.getValue().getCachedOverallScore(fromDateStr, toDateStr);
            return new SimpleStringProperty(String.format(java.util.Locale.US, "%.0f", score));
        });
        robScoreCol.setStyle("-fx-alignment: CENTER;");
        robScoreCol.setCellFactory(col -> new TableCell<CombinedPass, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(item);
                    try {
                        double score = Double.parseDouble(item);
                        String color;
                        if (score >= 70) {
                            color = "#00e676"; // Green
                        } else if (score >= 55) {
                            color = "#ffd740"; // Yellow
                        } else {
                            color = "#ff5252"; // Red
                        }
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: " + color + "; -fx-font-weight: bold;");
                    } catch (Exception e) {
                        setStyle("-fx-alignment: CENTER;");
                    }
                }
            }
        });
        robScoreCol.setPrefWidth(115);
        // Direct comparator to avoid re-evaluating the expensive cellValueFactory during sort
        robScoreCol.setComparator((s1, s2) -> {
            try {
                return Double.compare(Double.parseDouble(s1), Double.parseDouble(s2));
            } catch (NumberFormatException e) { return 0; }
        });

        TableColumn<CombinedPass, String> riCol = new TableColumn<>();
        riCol.setGraphic(DocHelper.createHeaderWithTooltip("RI",
            "Robustness Index (RI):\nEin fixierter mathematischer Wert ohne Gewichtung. Multipliziert BT Recovery Factor, Trades-Gewichtung und Forward-Konsistenz. Dient als objektiver Tie-Breaker."));
        riCol.setCellValueFactory(c -> {
            double ri = StrategyEvaluatorDialog.calculateRobustnessIndex(c.getValue());
            return new SimpleStringProperty(String.format(java.util.Locale.US, "%.2f", ri));
        });
        riCol.setStyle("-fx-alignment: CENTER;");
        riCol.setPrefWidth(60);
        // Direct comparator to avoid re-evaluating calculateRobustnessIndex during sort
        riCol.setComparator((s1, s2) -> {
            try {
                return Double.compare(Double.parseDouble(s1), Double.parseDouble(s2));
            } catch (NumberFormatException e) { return 0; }
        });

        t.getColumns().addAll(scoreCol, consistCol, robScoreCol, kiCol, riCol, passCol, btGroup, fwGroup);

        Label placeholder = new Label("Noch keine Daten.\nStarte eine Optimierung mit Forward Test, dann hier Filter anwenden.");
        placeholder.setStyle("-fx-text-fill: #7e889a;");
        t.setPlaceholder(placeholder);

        // Double-click → Zeige detaillierte Erklärung, Right-click → Kontextmenü für Backtest
        t.setRowFactory(tv -> {
            javafx.scene.control.TableRow<CombinedPass> row = new javafx.scene.control.TableRow<>();

            javafx.scene.control.ContextMenu contextMenu = new javafx.scene.control.ContextMenu();
            javafx.scene.control.MenuItem backtestItem = new javafx.scene.control.MenuItem("Backtest in MT5 ausführen (Terminal offen lassen)");
            backtestItem.setOnAction(event -> {
                CombinedPass item = row.getItem();
                if (item != null) {
                    runVerificationBacktest(item.getBacktestPass());
                }
            });
            contextMenu.getItems().add(backtestItem);

            row.emptyProperty().addListener((obs, wasEmpty, isEmpty) -> {
                if (isEmpty) {
                    row.setContextMenu(null);
                } else {
                    row.setContextMenu(contextMenu);
                }
            });

            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    showPassExplanationDialog(row.getItem());
                }
            });
            return row;
        });

        return t;
    }

    private Label addMetricRow(GridPane grid, int row, String labelText, String valueText) {
        Label label = new Label(labelText);
        label.setTextFill(Color.web("#7e889a"));
        label.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 12));

        Label value = new Label(valueText);
        value.setTextFill(Color.web("#e6e9f0"));
        value.setFont(javafx.scene.text.Font.font("Segoe UI", 12));

        grid.add(label, 0, row);
        grid.add(value, 1, row);
        return value;
    }

    private void showPassExplanationDialog(CombinedPass sel) {
        javafx.stage.Stage dialog = new javafx.stage.Stage();
        dialog.setTitle("Auswertung: Pass #" + sel.getPassNumber());
        dialog.initModality(javafx.stage.Modality.NONE);
        if (root.getScene() != null) {
            dialog.initOwner(root.getScene().getWindow());
        }

        VBox rootBox = new VBox(20);
        rootBox.setPadding(new Insets(25));
        rootBox.setStyle("-fx-background-color: #0d0f17;");

        // ── Title ──
        Label title = new Label("🔬 Analyse der Strategie (Pass #" + sel.getPassNumber() + ")");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        title.setTextFill(Color.web("#00e5ff"));

        // ── Left Column: Explanations ──
        // Consistency Explanation
        double cons = sel.getConsistency();
        String consLevel;
        Color consColor;
        String consDesc;

        if (cons < 0.3) {
            consLevel = "Ungenügend / Gefährlich";
            consColor = Color.web("#ff3b30");
            consDesc = "Die Strategie macht im Forward-Test (unbekannte Daten) fast gar keinen Gewinn mehr im Vergleich zum Backtest.\n\n" +
                       "Ursache: Höchstwahrscheinlich 'Curve Fitting' (Überoptimierung). Parameter extrem an Vergangenheit angepasst. Hände weg!";
        } else if (cons < 0.6) {
            consLevel = "Schwach";
            consColor = Color.web("#ff9500");
            consDesc = "Der Forward-Test ist profitabel, erreicht aber nur einen Bruchteil des Backtest-Profits.\n\n" +
                       "Bedeutung: Strategie hat etwas Robustheit, verliert in unbekannten Märkten aber deutlich an Leistung. Vorsichtig agieren.";
        } else if (cons < 0.9) {
            consLevel = "Gut";
            consColor = Color.web("#4cd964");
            consDesc = "Sehr solide! Die Strategie erzielt im Forward-Test fast genauso viel Profit wie im Backtest.\n\n" +
                       "Bedeutung: Parameter sind robust. Strategie hat echte Marktineffizienzen gefunden und nicht nur Historie auswendig gelernt.";
        } else if (cons <= 1.2) {
            consLevel = "Hervorragend (Perfekt)";
            consColor = Color.web("#00e676");
            consDesc = "Die Performance im Forward-Test entspricht exakt dem Backtest oder ist sogar leicht besser.\n\n" +
                       "Bedeutung: Perfekte Konsistenz. Dies ist ein hochgradig robustes Setup.";
        } else {
            consLevel = "Ungewöhnlich Hoch";
            consColor = Color.web("#ffd740");
            consDesc = "Der Forward-Test hat deutlich MEHR Profit gemacht als der Backtest.\n\n" +
                       "Bedeutung: Positiv, kann aber bedeuten, dass die Forward-Phase zufällig sehr günstig war. Dennoch besser als Verlust!";
        }

        Label consTitle = new Label("Konsistenz: " + String.format("%.2f", cons) + " (" + consLevel + ")");
        consTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        consTitle.setTextFill(consColor);

        String consCalc = "Berechnung: Forward Profit / Backtest Profit\n" +
                          String.format("=> %.2f / %.2f = %.2f\n", (sel.getForwardPass() != null ? sel.getFwProfit() : 0.0), sel.getBtProfit(), cons) +
                          "(Wert 1.0 bedeutet 100% identische Performance).\n\n" + consDesc;

        Label consText = new Label(consCalc);
        consText.setWrapText(true);
        consText.setStyle("-fx-text-fill: #b4bac8; -fx-font-size: 13px;");

        VBox consBox = new VBox(8, consTitle, consText);
        consBox.setStyle("-fx-background-color: #171b26; -fx-padding: 15; -fx-background-radius: 8; -fx-border-color: #2e3543; -fx-border-width: 1px; -fx-border-radius: 8;");

        // Score Explanation
        double score = sel.getScore();
        String scoreLevel;
        Color scoreColor;
        String scoreDesc;

        if (score >= 80) {
            scoreLevel = "Exzellent (Top Tier)";
            scoreColor = Color.web("#00e676");
            scoreDesc = "Diese Strategie gehört zu den besten der Optimierung. Sie kombiniert hohen Profit mit exzellenter Konsistenz und moderatem Drawdown.";
        } else if (score >= 60) {
            scoreLevel = "Solide (Gute Wahl)";
            scoreColor = Color.web("#4cd964");
            scoreDesc = "Gute, brauchbare Strategie. Kleine Schwächen bei Drawdown oder Konsistenz, aber vielversprechend.";
        } else if (score >= 40) {
            scoreLevel = "Mittelmäßig";
            scoreColor = Color.web("#ffd740");
            scoreDesc = "Potenzial vorhanden, aber deutliche Schwächen (z.B. wenige Trades oder hohe Drawdowns).";
        } else {
            scoreLevel = "Mangelhaft (Ausschuss)";
            scoreColor = Color.web("#ff3b30");
            scoreDesc = "Durchgefallen. Profit zu gering, Drawdowns zu hoch oder Konsistenz eingebrochen.";
        }

        Label scoreTitle = new Label("Gesamt-Score: " + String.format("%.1f", score) + " / 100 (" + scoreLevel + ")");
        scoreTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        scoreTitle.setTextFill(scoreColor);

        String scoreCalc = "Was bedeutet dieser Wert?\n" +
                           "Der Gesamt-Score (0-100) bewertet auf einen Blick, wie ausgewogen und robust eine Strategie im Vergleich zu allen anderen Durchgängen dieser Optimierung abschneidet. Ein hoher Score zeigt, dass die Strategie nicht nur hohen Profit erzielt, sondern auch ein gesundes Verhältnis von Risiko (geringer Drawdown), Stabilität (hoher Profit Factor) und Konsistenz zwischen Backtest und Forward-Phase aufweist. Er schützt vor Überoptimierung, indem er reine Gewinn-Ausreißer abwertet, wenn diese bei Marktveränderungen einbrechen. Kurz gesagt: Er filtert die stabilsten Allrounder heraus.\n\n" +
                           "⚠️ HINWEIS ZUR KENNLINIE:\n" +
                           "Dieser Gesamt-Score bewertet ausschließlich die endgültigen Kennzahlen am Schluss. Er betrachtet NICHT den Verlauf der Kennlinie (Equity-Kurve). Nur der Robustness Score analysiert den tatsächlichen Verlauf der Kennlinie per linearer Regression (R²-Stabilität), um Glückstreffer oder instabile Verläufe aufzudecken.\n\n" +
                           "Berechnung & Details:\n" +
                           "Er bewertet Profit, Drawdown, PF und Konsistenz gemeinsam basierend auf deinen Filter-Gewichtungen.\n\n" + scoreDesc + "\n\n" +
                           "--- GENAUE BERECHNUNG ---\n" + sel.getScoreDetails();

        Label scoreText = new Label(scoreCalc);
        scoreText.setWrapText(true);
        scoreText.setStyle("-fx-text-fill: #b4bac8; -fx-font-size: 13px; -fx-font-family: monospace;");

        VBox scoreBox = new VBox(8, scoreTitle, scoreText);
        scoreBox.setStyle("-fx-background-color: #171b26; -fx-padding: 15; -fx-background-radius: 8; -fx-border-color: #2e3543; -fx-border-width: 1px; -fx-border-radius: 8;");

        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(scoreBox);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #0d0f17; -fx-border-color: transparent;");
        scroll.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox leftPane = new VBox(15, consBox, scroll);
        leftPane.setPrefWidth(550);
        leftPane.setMinWidth(450);
        HBox.setHgrow(leftPane, Priority.ALWAYS);

        // ── Right Column: Performance Data, Chart, Parameters ──
        // 1. Backtest Card
        VBox btCard = new VBox(10);
        btCard.setPadding(new Insets(12));
        btCard.setStyle("-fx-background-color: #171b26; -fx-border-color: #00e676; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px;");
        HBox.setHgrow(btCard, Priority.ALWAYS);
        Label btTitle = new Label("◀ BACKTEST METRIKEN");
        btTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        btTitle.setTextFill(Color.web("#00e676"));
        GridPane btGrid = new GridPane();
        btGrid.setHgap(15);
        btGrid.setVgap(6);
        addMetricRow(btGrid, 0, "Nettoprofit:", String.format(java.util.Locale.US, "%.2f", sel.getBtProfit()));
        addMetricRow(btGrid, 1, "Trades:", String.valueOf(sel.getBtTrades()));
        addMetricRow(btGrid, 2, "Profit Factor:", String.format(java.util.Locale.US, "%.2f", sel.getBtPf()));
        addMetricRow(btGrid, 3, "Max. Drawdown:", String.format(java.util.Locale.US, "%.2f%%", sel.getBtDd()));
        addMetricRow(btGrid, 4, "Recovery Factor:", String.format(java.util.Locale.US, "%.2f", sel.getBtRecovery()));
        addMetricRow(btGrid, 5, "Sharpe Ratio:", Double.isNaN(sel.getBtSharpe()) ? "—" : String.format(java.util.Locale.US, "%.2f", sel.getBtSharpe()));
        addMetricRow(btGrid, 6, "Expected Payoff:", String.format(java.util.Locale.US, "%.2f", sel.getBtExpectedPayoff()));
        btCard.getChildren().addAll(btTitle, btGrid);

        // 2. Forward Card
        VBox fwCard = new VBox(10);
        fwCard.setPadding(new Insets(12));
        fwCard.setStyle("-fx-background-color: #171b26; -fx-border-color: #00e5ff; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px;");
        HBox.setHgrow(fwCard, Priority.ALWAYS);
        Label fwTitle = new Label("FORWARD METRIKEN ▶");
        fwTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        fwTitle.setTextFill(Color.web("#00e5ff"));
        GridPane fwGrid = new GridPane();
        fwGrid.setHgap(15);
        fwGrid.setVgap(6);

        if (sel.getForwardPass() != null) {
            addMetricRow(fwGrid, 0, "Nettoprofit:", String.format(java.util.Locale.US, "%.2f", sel.getFwProfit()));
            addMetricRow(fwGrid, 1, "Trades:", String.valueOf(sel.getFwTrades()));
            addMetricRow(fwGrid, 2, "Profit Factor:", Double.isNaN(sel.getFwPf()) ? "—" : String.format(java.util.Locale.US, "%.2f", sel.getFwPf()));
            addMetricRow(fwGrid, 3, "Max. Drawdown:", Double.isNaN(sel.getFwDd()) ? "—" : String.format(java.util.Locale.US, "%.2f%%", sel.getFwDd()));
            addMetricRow(fwGrid, 4, "Recovery Factor:", Double.isNaN(sel.getFwRecovery()) ? "—" : String.format(java.util.Locale.US, "%.2f", sel.getFwRecovery()));
            addMetricRow(fwGrid, 5, "Sharpe Ratio:", Double.isNaN(sel.getFwSharpe()) ? "—" : String.format(java.util.Locale.US, "%.2f", sel.getFwSharpe()));
            addMetricRow(fwGrid, 6, "Expected Payoff:", Double.isNaN(sel.getFwExpectedPayoff()) ? "—" : String.format(java.util.Locale.US, "%.2f", sel.getFwExpectedPayoff()));
            fwCard.getChildren().addAll(fwTitle, fwGrid);
        } else {
            Label noFwLabel = new Label("Kein Forward-Test\ndurchgeführt.");
            noFwLabel.setTextFill(Color.web("#7e889a"));
            noFwLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
            noFwLabel.setAlignment(Pos.CENTER);
            VBox.setVgrow(noFwLabel, Priority.ALWAYS);
            fwCard.getChildren().addAll(fwTitle, noFwLabel);
        }

        HBox metricsBox = new HBox(12, btCard, fwCard);
        metricsBox.setAlignment(Pos.TOP_LEFT);

        // Equity Chart
        Label chartTitleLabel = new Label("EQUITY-KURVE (KAPITALVERLAUF)");
        chartTitleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        chartTitleLabel.setTextFill(Color.web("#00e5ff"));

        javafx.scene.chart.NumberAxis xAxis = new javafx.scene.chart.NumberAxis();
        xAxis.setLabel("Trades");
        xAxis.setTickLabelFill(Color.web("#7e889a"));
        xAxis.setMinorTickVisible(false);

        javafx.scene.chart.NumberAxis yAxis = new javafx.scene.chart.NumberAxis();
        yAxis.setLabel("Equity");
        yAxis.setTickLabelFill(Color.web("#7e889a"));
        yAxis.setForceZeroInRange(false);

        javafx.scene.chart.LineChart<Number, Number> equityChart = new javafx.scene.chart.LineChart<>(xAxis, yAxis);
        equityChart.setCreateSymbols(false);
        equityChart.setPrefHeight(200);
        equityChart.setMinHeight(200);
        equityChart.setMaxHeight(200);
        equityChart.setAnimated(false);
        equityChart.setStyle("-fx-background-color: transparent;");
        equityChart.setHorizontalGridLinesVisible(true);
        equityChart.setVerticalGridLinesVisible(false);

        // Generate curves
        double btEndBalance = sel.getBacktestPass().getBalance();
        double btStartBalance = btEndBalance - sel.getBtProfit();
        if (btStartBalance <= 0) {
            btStartBalance = 10000.0;
        }

        java.util.List<Double> btCurve = StrategyEvaluatorDialog.generateSyntheticEquityCurve(btStartBalance, sel.getBtProfit(), sel.getBtTrades(), sel.getBtPf(), sel.getPassNumber());
        javafx.scene.chart.XYChart.Series<Number, Number> backtestSeries = new javafx.scene.chart.XYChart.Series<>();
        backtestSeries.setName("Backtest");
        for (int i = 0; i < btCurve.size(); i++) {
            backtestSeries.getData().add(new javafx.scene.chart.XYChart.Data<>(i, btCurve.get(i)));
        }
        equityChart.getData().add(backtestSeries);

        final javafx.scene.chart.XYChart.Series<Number, Number> forwardSeriesRef;
        if (sel.getForwardPass() != null) {
            double fwStartBalance = btCurve.get(btCurve.size() - 1);
            java.util.List<Double> fwCurve = StrategyEvaluatorDialog.generateSyntheticEquityCurve(fwStartBalance, sel.getFwProfit(), sel.getFwTrades(), sel.getFwPf(), sel.getPassNumber() + 999);
            javafx.scene.chart.XYChart.Series<Number, Number> forwardSeries = new javafx.scene.chart.XYChart.Series<>();
            forwardSeries.setName("Forward");

            // Connect seamlessly
            int offset = btCurve.size() - 1;
            forwardSeries.getData().add(new javafx.scene.chart.XYChart.Data<>(offset, fwStartBalance));
            for (int j = 1; j < fwCurve.size(); j++) {
                forwardSeries.getData().add(new javafx.scene.chart.XYChart.Data<>(offset + j, fwCurve.get(j)));
            }
            equityChart.getData().add(forwardSeries);
            forwardSeriesRef = forwardSeries;
        } else {
            forwardSeriesRef = null;
        }

        // Parameters Table
        Label paramTitle = new Label("STRATEGIE-PARAMETER");
        paramTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        paramTitle.setTextFill(Color.web("#00e5ff"));

        TableView<StrategyEvaluatorDialog.ParameterRow> paramTable = new TableView<>();
        paramTable.setStyle("-fx-background-color: transparent;");
        paramTable.setPrefHeight(200);
        VBox.setVgrow(paramTable, Priority.ALWAYS);

        TableColumn<StrategyEvaluatorDialog.ParameterRow, String> nameCol = new TableColumn<>("Parameter");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(250);

        TableColumn<StrategyEvaluatorDialog.ParameterRow, String> valCol = new TableColumn<>("Wert");
        valCol.setCellValueFactory(new PropertyValueFactory<>("value"));
        valCol.setPrefWidth(250);

        paramTable.getColumns().addAll(nameCol, valCol);

        java.util.List<StrategyEvaluatorDialog.ParameterRow> paramList = new java.util.ArrayList<>();
        java.util.Map<String, String> params = sel.getBacktestPass().getParameterValues();
        for (java.util.Map.Entry<String, String> entry : params.entrySet()) {
            paramList.add(new StrategyEvaluatorDialog.ParameterRow(entry.getKey(), entry.getValue()));
        }
        paramTable.setItems(FXCollections.observableArrayList(paramList));

        VBox rightPane = new VBox(12, metricsBox, chartTitleLabel, equityChart, paramTitle, paramTable);
        rightPane.setPrefWidth(650);
        rightPane.setMinWidth(550);
        HBox.setHgrow(rightPane, Priority.ALWAYS);

        // ── Horizontal Content Panel ──
        HBox contentBox = new HBox(25, leftPane, rightPane);
        contentBox.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(contentBox, Priority.ALWAYS);

        // ── Close Button ──
        Button closeBtn = new Button("Schließen");
        closeBtn.setStyle("-fx-background-color: #00e5ff; -fx-text-fill: #0d0f17; -fx-font-weight: bold; -fx-padding: 8 20; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> dialog.close());

        HBox btnBox = new HBox(closeBtn);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        Label subtitle = new Label("Zeitraum: " + (lastOptResult != null && lastOptResult.getFromDate() != null && !lastOptResult.getFromDate().isEmpty() ? lastOptResult.getFromDate() + " bis " + lastOptResult.getToDate() : "Unbekannt"));
        subtitle.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 13));
        subtitle.setTextFill(Color.web("#7e889a"));

        rootBox.getChildren().addAll(title, subtitle, contentBox, btnBox);

        javafx.scene.Scene scene = new javafx.scene.Scene(rootBox, 1200, 865);
        try {
            scene.getStylesheets().add(getClass().getResource("/css/antigravity.css").toExternalForm());
        } catch (Exception ignored) {}
        dialog.setScene(scene);

        // Style chart after elements are shown
        dialog.setOnShown(e -> {
            if (backtestSeries.getNode() != null) {
                backtestSeries.getNode().setStyle("-fx-stroke: #00e676; -fx-stroke-width: 3px;");
            }
            if (forwardSeriesRef != null && forwardSeriesRef.getNode() != null) {
                forwardSeriesRef.getNode().setStyle("-fx-stroke: #00e5ff; -fx-stroke-width: 3px;");
            }
            javafx.scene.Node plotBg = equityChart.lookup(".chart-plot-background");
            if (plotBg != null) {
                plotBg.setStyle("-fx-background-color: #171b26; -fx-border-color: #3e4555; -fx-border-width: 1px;");
            }
        });

        dialog.show();
    }

    private VBox createSelectedPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(10));

        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("sci-fi-panel");
        topBar.setPadding(new Insets(10));

        Button removeBtn = new Button("🗑 Markierte Zeilen entfernen");
        removeBtn.getStyleClass().addAll("button", "button-cancel");
        removeBtn.setOnAction(e -> {
            java.util.List<CombinedPass> selection = new java.util.ArrayList<>(selectedTable.getSelectionModel().getSelectedItems());
            removeSelectedPasses(selection);
        });

        Button clearAllBtn = new Button("⏹ Clear All");
        clearAllBtn.getStyleClass().addAll("button", "button-cancel");
        clearAllBtn.setOnAction(e -> {
            clearSelectedPasses();
        });

        Label searchLabel = new Label("🔍 Pass:");
        searchLabel.setStyle("-fx-text-fill: #00e5ff; -fx-font-weight: bold;");
        selectedSearchField = new TextField();
        selectedSearchField.setPromptText("Pass #...");
        selectedSearchField.setPrefWidth(90);
        selectedSearchField.getStyleClass().add("text-input");
        selectedSearchField.textProperty().addListener((obs, oldVal, newVal) -> applySelectedFilter());

        Button selectedInfoBtn = DocHelper.createThickCircularInfoButton("Erklärung aller Indizes und Kennzahlen", () -> {
            DocHelper.showAllIndicesDocDialog(clearAllBtn.getScene() != null ? clearAllBtn.getScene().getWindow() : null);
        });

        topBar.getChildren().addAll(removeBtn, clearAllBtn, selectedInfoBtn, searchLabel, selectedSearchField);

        selectedTable = createCombinedTable();
        VBox.setVgrow(selectedTable, Priority.ALWAYS);
        pane.getChildren().addAll(topBar, selectedTable);
        return pane;
    }

    public javafx.collections.ObservableList<CombinedPass> getSelectedStrategies() {
        return masterSelectedList;
    }

    public String getExpertName() {
        return expertField.getText().trim();
    }

    private VBox createSensitivityPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(10));

        sensitivityTable = createSensitivityTable();
        VBox.setVgrow(sensitivityTable, Priority.ALWAYS);

        pane.getChildren().addAll(sensitivityTable);
        VBox.setVgrow(pane, Priority.ALWAYS);
        return pane;
    }

    @SuppressWarnings("unchecked")
    private TableView<com.backtester.report.SensitivityResult> createSensitivityTable() {
        TableView<com.backtester.report.SensitivityResult> t = new TableView<>();
        t.setStyle("-fx-background-color: transparent;");
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<com.backtester.report.SensitivityResult, Integer> passCol = new TableColumn<>("Pass");
        passCol.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getOriginalPass().getPassNumber()).asObject());



        TableColumn<com.backtester.report.SensitivityResult, String> btProfitCol = new TableColumn<>("BT Profit");
        btProfitCol.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f", c.getValue().getOriginalPass().getBtProfit())));
        btProfitCol.setComparator(numericStringComparator());

        TableColumn<com.backtester.report.SensitivityResult, String> fwProfitCol = new TableColumn<>("FW Profit");
        fwProfitCol.setCellValueFactory(c -> {
            double fw = c.getValue().getOriginalPass().getFwProfit();
            return new SimpleStringProperty(Double.isNaN(fw) ? "-" : String.format("%.2f", fw));
        });
        fwProfitCol.setComparator(numericStringComparator());

        java.util.Comparator<String> percentComparator = (s1, s2) -> {
            if ("-".equals(s1) && "-".equals(s2)) return 0;
            if ("-".equals(s1)) return 1;
            if ("-".equals(s2)) return -1;
            double v1 = Double.parseDouble(s1.replace(" %", "").replace(",", "."));
            double v2 = Double.parseDouble(s2.replace(" %", "").replace(",", "."));
            return Double.compare(v1, v2);
        };

        TableColumn<com.backtester.report.SensitivityResult, String> btCvCol = new TableColumn<>();
        Label btCvTitle = new Label("BT CV (worst)");
        btCvTitle.setTextFill(Color.web("#80d8ff"));
        Button btCvInfoBtn = new Button("ℹ");
        btCvInfoBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #00e5ff; -fx-padding: 0 0 0 5; -fx-cursor: hand; -fx-font-weight: bold; -fx-font-size: 11px;");
        btCvInfoBtn.setOnAction(e -> {
            e.consume();
            showCvExplanationDialog("BT CV (worst) Erklärung", "BT CV (worst) - Backtest Variationskoeffizient", getBtCvExplanationHtml());
        });
        btCvInfoBtn.setOnMousePressed(javafx.event.Event::consume);
        btCvInfoBtn.setOnMouseClicked(javafx.event.Event::consume);
        HBox btCvHeader = new HBox(btCvTitle, btCvInfoBtn);
        btCvHeader.setAlignment(Pos.CENTER_LEFT);
        btCvCol.setGraphic(btCvHeader);

        btCvCol.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.2f %%", c.getValue().getOverallCV())));
        btCvCol.setComparator(percentComparator);
        btCvCol.setCellFactory(col -> new TableCell<com.backtester.report.SensitivityResult, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.equals("-") || item.equals("—")) {
                    setText(null); setStyle("");
                } else {
                    setText(item);
                    double cv = getTableRow() != null && getTableRow().getItem() != null ? getTableRow().getItem().getOverallCV() : 0;
                    if (cv < 30) setStyle("-fx-text-fill: #00e676; -fx-font-weight: bold;");
                    else if (cv <= 60) setStyle("-fx-text-fill: #ffd740; -fx-font-weight: bold;");
                    else setStyle("-fx-text-fill: #ff3b30; -fx-font-weight: bold;");
                }
            }
        });

        TableColumn<com.backtester.report.SensitivityResult, String> fwCvCol = new TableColumn<>();
        Label fwCvTitle = new Label("FW CV (worst)");
        fwCvTitle.setTextFill(Color.web("#80d8ff"));
        Button fwCvInfoBtn = new Button("ℹ");
        fwCvInfoBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #00e5ff; -fx-padding: 0 0 0 5; -fx-cursor: hand; -fx-font-weight: bold; -fx-font-size: 11px;");
        fwCvInfoBtn.setOnAction(e -> {
            e.consume();
            showCvExplanationDialog("FW CV (worst) Erklärung", "FW CV (worst) - Forward Variationskoeffizient", getFwCvExplanationHtml());
        });
        fwCvInfoBtn.setOnMousePressed(javafx.event.Event::consume);
        fwCvInfoBtn.setOnMouseClicked(javafx.event.Event::consume);
        HBox fwCvHeader = new HBox(fwCvTitle, fwCvInfoBtn);
        fwCvHeader.setAlignment(Pos.CENTER_LEFT);
        fwCvCol.setGraphic(fwCvHeader);

        fwCvCol.setCellValueFactory(c -> {
            com.backtester.report.SensitivityResult r = c.getValue();
            return new SimpleStringProperty(r.hasForwardCV() ? String.format("%.2f %%", r.getOverallCVFw()) : "-");
        });
        fwCvCol.setComparator(percentComparator);
        fwCvCol.setCellFactory(col -> new TableCell<com.backtester.report.SensitivityResult, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.equals("-") || item.equals("—")) {
                    setText(null); setStyle("");
                } else {
                    setText(item);
                    double cv = getTableRow() != null && getTableRow().getItem() != null ? getTableRow().getItem().getOverallCVFw() : 0;
                    if (cv < 30) setStyle("-fx-text-fill: #00e676; -fx-font-weight: bold;");
                    else if (cv <= 60) setStyle("-fx-text-fill: #ffd740; -fx-font-weight: bold;");
                    else setStyle("-fx-text-fill: #ff3b30; -fx-font-weight: bold;");
                }
            }
        });

        TableColumn<com.backtester.report.SensitivityResult, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus()));

        TableColumn<com.backtester.report.SensitivityResult, String> kiResultCol = new TableColumn<>("KI Result");
        kiResultCol.setCellValueFactory(c -> c.getValue().kiResultProperty());
        kiResultCol.setCellFactory(col -> new TableCell<com.backtester.report.SensitivityResult, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isEmpty()) {
                    setText(null); setStyle("");
                } else {
                    try {
                        int score = Integer.parseInt(item.trim());
                        setText(score + " / 100");
                        String color;
                        if (score >= 80) color = "#00e676"; // bright green
                        else if (score >= 70) color = "#66bb6a"; // green
                        else if (score >= 60) color = "#aed581"; // light green
                        else if (score >= 50) color = "#ffd740"; // amber
                        else if (score >= 40) color = "#ffb300"; // dark amber
                        else if (score >= 25) color = "#ff6d00"; // orange
                        else color = "#ff3b30"; // red
                        setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold; -fx-font-size: 13px;");
                    } catch (NumberFormatException e) {
                        setText(item);
                        setStyle("");
                    }
                }
            }
        });

        t.getColumns().addAll(passCol, btProfitCol, fwProfitCol, btCvCol, fwCvCol, statusCol, kiResultCol);
        t.setStyle("-fx-selection-bar: rgba(0, 229, 255, 0.2); -fx-selection-bar-text: #e6e9f0;");

        t.setRowFactory(tv -> {
            TableRow<com.backtester.report.SensitivityResult> row = new TableRow<>();

            javafx.scene.control.ContextMenu contextMenu = new javafx.scene.control.ContextMenu();
            javafx.scene.control.MenuItem backtestItem = new javafx.scene.control.MenuItem("Backtest in MT5 ausführen (Terminal offen lassen)");
            backtestItem.setOnAction(event -> {
                com.backtester.report.SensitivityResult item = row.getItem();
                if (item != null && item.getOriginalPass() != null) {
                    runVerificationBacktest(item.getOriginalPass().getBacktestPass());
                }
            });
            contextMenu.getItems().add(backtestItem);

            row.emptyProperty().addListener((obs, wasEmpty, isEmpty) -> {
                if (isEmpty) {
                    row.setContextMenu(null);
                } else {
                    row.setContextMenu(contextMenu);
                }
            });

            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    com.backtester.report.SensitivityResult rowData = row.getItem();
                    showSensitivityDetails(rowData);
                }
            });
            return row;
        });

        return t;
    }

    private void showSensitivityDetails(com.backtester.report.SensitivityResult result) {
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("Sensitivity Details - Pass " + result.getOriginalPass().getPassNumber());

        VBox box = new VBox(15);
        box.setPadding(new Insets(20));
        box.getStyleClass().add("root");
        box.setStyle("-fx-background-color: #0b0d13;"); // Ensure dark background

        Label title = new Label("Strategy Details: Pass " + result.getOriginalPass().getPassNumber());
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#00e5ff"));

        String fwSummary = result.hasForwardCV()
                ? String.format("  |  FW Profit: %.2f  |  FW CV (worst): %.2f %%",
                        result.getOriginalPass().getFwProfit(), result.getOverallCVFw())
                : "  |  FW: -";
        Label scoreLabel = new Label(String.format("BT Profit: %.2f  |  BT CV (worst): %.2f %%%s",
                result.getOriginalPass().getBtProfit(), result.getOverallCV(), fwSummary));
        scoreLabel.setTextFill(Color.web("#00e676"));

        Label btCvLabel = new Label("Backtest (in-sample) Parameter Robustness:");
        btCvLabel.setTextFill(Color.WHITE);
        btCvLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        TableView<java.util.Map.Entry<String, Double>> btCvTable = buildCvBreakdownTable(
                result.getParameterCVs(),
                result.getParameterCurves(),
                result.getOriginalPass().getBacktestPass().getParameterValues(),
                "#00e5ff");

        // All Strategy Parameters Table (from Original Pass)
        Label paramLabel = new Label("Optimized Strategy Settings:");
        paramLabel.setTextFill(Color.WHITE);
        paramLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));

        TableView<java.util.Map.Entry<String, String>> settingsTable = new TableView<>();
        TableColumn<java.util.Map.Entry<String, String>, String> sParamCol = new TableColumn<>("Parameter");
        sParamCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getKey()));
        TableColumn<java.util.Map.Entry<String, String>, String> sValCol = new TableColumn<>("Value");
        sValCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getValue()));
        settingsTable.getColumns().addAll(sParamCol, sValCol);
        settingsTable.getItems().addAll(result.getOriginalPass().getBacktestPass().getParameterValues().entrySet());
        settingsTable.setPrefHeight(250);
        settingsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        settingsTable.setSelectionModel(null); // avoid selection text-color artifacts on dark theme

        box.getChildren().addAll(title, scoreLabel, btCvLabel, btCvTable);

        if (result.hasForwardCV()) {
            Label fwCvLabel = new Label("Forward (out-of-sample) Parameter Robustness:");
            fwCvLabel.setTextFill(Color.WHITE);
            fwCvLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
            TableView<java.util.Map.Entry<String, Double>> fwCvTable = buildCvBreakdownTable(
                    result.getParameterCVsFw(),
                    result.getParameterCurvesFw(),
                    result.getOriginalPass().getBacktestPass().getParameterValues(),
                    "#ff9100");
            box.getChildren().addAll(fwCvLabel, fwCvTable);
        }

        box.getChildren().addAll(paramLabel, settingsTable);

        // --- Detailed Interpretation ---
        Label explanationTitle = new Label("Ausführliche Erklärung zur Interpretation");
        explanationTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        explanationTitle.setTextFill(Color.web("#00e5ff"));

        String interpretationText =
            "Die Sensitivitätsanalyse testet, wie 'zerbrechlich' deine Strategie ist.\n" +
            "Dazu wird jeder Parameter (z.B. StopLoss, Takeprofit) in kleinen Schritten um seinen optimierten Wert herum verschoben. " +
            "Anschließend messen wir, wie stark sich der Profit durch diese kleinen Änderungen verändert.\n\n" +
            "Der CV-Wert (Coefficient of Variation) ist das Maß für diese Schwankung:\n" +
            "• Unter 30% (Grün): Der Parameter ist extrem stabil. Wenn der Markt sich leicht ändert (Slippage, andere Spreads, leicht veränderte Volatilität), bleibt dein Profit weitgehend gleich.\n" +
            "• 30% bis 60% (Gelb): Normale Schwankung. Die Strategie reagiert auf Marktveränderungen, bleibt aber vermutlich noch profitabel.\n" +
            "• Über 60% (Rot): Gefahr! Die Strategie ist ein 'One-Hit-Wonder'. Ein winziger Unterschied im Markt, und die Strategie stürzt ab. Solche Werte bedeuten oft, dass der Backtest komplett 'curve-fitted' (überoptimiert) ist.\n\n";

        double worstCv = result.getOverallCV();
        if (result.hasForwardCV() && result.getOverallCVFw() > worstCv) {
            worstCv = result.getOverallCVFw();
        }

        String passVerdict = "";
        Color verdictColor = Color.WHITE;
        if (worstCv < 30.0) {
            passVerdict = String.format("FAZIT ZU DIESEM PASS:\nDein schlechtester CV liegt bei %.2f %%. Dies ist ein exzellenter Wert! Die Parameter sind extrem robust.", worstCv);
            verdictColor = Color.web("#00e676");
        } else if (worstCv <= 60.0) {
            passVerdict = String.format("FAZIT ZU DIESEM PASS:\nDein schlechtester CV liegt bei %.2f %%. Das ist solide. Die Strategie wird nicht sofort zusammenbrechen, wenn sich die Marktbedingungen leicht ändern.", worstCv);
            verdictColor = Color.web("#ffd740");
        } else {
            passVerdict = String.format("FAZIT ZU DIESEM PASS:\nACHTUNG! Der schlechteste CV liegt bei gigantischen %.2f %%! Dieser Pass ist zu stark überoptimiert (Curve-Fitted). Im Live-Handel wird er höchstwahrscheinlich Verluste einfahren. Bitte mit Vorsicht behandeln!", worstCv);
            verdictColor = Color.web("#ff3b30");
        }

        Label interpretationLabel = new Label(interpretationText);
        interpretationLabel.setWrapText(true);
        interpretationLabel.setStyle("-fx-text-fill: #b4bac8; -fx-font-size: 13px;");

        Label verdictLabel = new Label(passVerdict);
        verdictLabel.setWrapText(true);
        verdictLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        verdictLabel.setTextFill(verdictColor);

        VBox expBox = new VBox(10, explanationTitle, interpretationLabel, verdictLabel);
        expBox.setStyle("-fx-background-color: #1a1d27; -fx-padding: 15; -fx-background-radius: 8; -fx-border-color: #2a2d3a; -fx-border-width: 1; -fx-border-radius: 8;");

        box.getChildren().add(expBox);

        // Wrap content in a scroll pane so the dialog scales gracefully when both
        // BT and FW breakdown tables are shown.
        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(box);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: #0b0d13;");
        javafx.scene.Scene scene = new javafx.scene.Scene(scroll, 1000, 750);
        if (root.getScene() != null && !root.getScene().getStylesheets().isEmpty()) {
            scene.getStylesheets().addAll(root.getScene().getStylesheets());
        }
        stage.setScene(scene);
        stage.show();
    }

    @SuppressWarnings("unchecked")
    private TableView<java.util.Map.Entry<String, Double>> buildCvBreakdownTable(
            java.util.Map<String, Double> cvMap,
            java.util.Map<String, java.util.List<com.backtester.report.SensitivityResult.DataPoint>> curves,
            java.util.Map<String, String> baseValues,
            String accentColor) {

        TableView<java.util.Map.Entry<String, Double>> cvTable = new TableView<>();
        TableColumn<java.util.Map.Entry<String, Double>, String> paramCol = new TableColumn<>("Parameter");
        paramCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getKey()));

        TableColumn<java.util.Map.Entry<String, Double>, VBox> valCol = new TableColumn<>("CV (%)");
        valCol.setCellValueFactory(c -> {
            String pName = c.getValue().getKey();
            double cv = c.getValue().getValue();
            java.util.List<com.backtester.report.SensitivityResult.DataPoint> curveData =
                    curves != null ? curves.get(pName) : null;

            VBox calcBox = new VBox(5);
            calcBox.setAlignment(Pos.CENTER_LEFT);
            calcBox.setPadding(new Insets(0, 0, 0, 10));

            Label cvValueLabel = new Label(String.format("%.2f %%", cv));
            cvValueLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
            cvValueLabel.setTextFill(Color.web(accentColor));

            Button infoBtn = new Button("\u2139");
            infoBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + accentColor +
                    "; -fx-cursor: hand; -fx-border-color: " + accentColor +
                    "; -fx-border-radius: 15px; -fx-font-weight: bold; -fx-padding: 0 5 0 5;");

            javafx.scene.layout.HBox topBox = new javafx.scene.layout.HBox(10, cvValueLabel, infoBtn);
            topBox.setAlignment(Pos.CENTER_LEFT);

            if (curveData != null && !curveData.isEmpty()) {
                double sum = curveData.stream().mapToDouble(d -> d.profit).sum();
                double mean = sum / curveData.size();
                double varianceSum = 0;
                for (com.backtester.report.SensitivityResult.DataPoint dp : curveData) {
                    varianceSum += Math.pow(dp.profit - mean, 2);
                }
                double stdDev = Math.sqrt(varianceSum / curveData.size());

                infoBtn.setOnAction(e -> {
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                            javafx.scene.control.Alert.AlertType.INFORMATION);
                    alert.setTitle("Erkl\u00e4rung: Parameter Robustness");
                    alert.setHeaderText("Was bedeutet der CV-Wert f\u00fcr " + pName + "?");

                    String explanation = String.format(java.util.Locale.US,
                        "Der CV-Wert (Coefficient of Variation) zeigt an, wie stark der Profit schwankt, wenn sich der Parameter '%s' leicht \u00e4ndert.\n\n" +
                        "Ein kleiner CV-Wert bedeutet, dass die Strategie sehr stabil (robust) ist.\n" +
                        "Ein hoher Wert zeigt an, dass schon winzige \u00c4nderungen am Parameter den Profit massiv einbrechen lassen k\u00f6nnen \u2013 die Strategie ist hier anf\u00e4llig und \u00fcberoptimiert!\n\n" +
                        "--- BERECHNUNG ---\n\n" +
                        "1. Durchschnittlicher Profit der Varianten (Mean):\n" +
                        "In unseren Tests lag der Profit f\u00fcr diesen Parameter im Schnitt bei %.2f USD.\n\n" +
                        "2. Schwankung (Standardabweichung / StdDev):\n" +
                        "Der Profit schwankte im Schnitt um %.2f USD.\n\n" +
                        "3. Die Formel (CV):\n" +
                        "Wir teilen die Schwankung durch den ORIGINALEN Basis-Profit der optimierten Strategie und rechnen mal 100:\n" +
                        "CV = (StdDev / |Basis-Profit|) * 100\n" +
                        "CV = (%.2f / %.2f) * 100 = %.2f %%\n\n" +
                        "Hinweis: Wir verwenden den Basis-Profit statt des Durchschnitts, weil der klassische CV bei Profiten nahe Null (wo positive und negative Ergebnisse gemischt werden) unsinnig hohe Werte liefert.\n\n" +
                        "Faustregel:\n" +
                        "\u2022 Unter 20%%: Sehr robust. Der Parameter ist stabil.\n" +
                        "\u2022 20%% - 50%%: Akzeptabel. Es gibt Schwankungen, aber im Rahmen.\n" +
                        "\u2022 \u00dcber 50%%: Gef\u00e4hrlich! Die Strategie ist hier eine 'Klippe' und extrem riskant.",
                        pName, mean, stdDev, stdDev, Math.abs(mean) > 0.01 ? Math.abs(mean) : 1.0, cv
                    );

                    Label expLabel = new Label(explanation);
                    expLabel.setWrapText(true);
                    expLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13px;");
                    alert.getDialogPane().setContent(expLabel);
                    alert.getDialogPane().setPrefWidth(550);
                    alert.getDialogPane().setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
                    try {
                        if (cvTable.getScene() != null && !cvTable.getScene().getStylesheets().isEmpty()) {
                            alert.getDialogPane().getStylesheets().addAll(cvTable.getScene().getStylesheets());
                        }
                    } catch (Exception ignored) {}
                    alert.getDialogPane().setStyle("-fx-base: #11141d; -fx-background-color: #11141d; -fx-text-fill: white;");
                    alert.showAndWait();
                });

                Label formulaLabel = new Label("CV = (StdDev / |Basis-Profit|) * 100");
                formulaLabel.setFont(Font.font("Segoe UI", 10));
                formulaLabel.setTextFill(Color.web("#8093a5"));

                Label calcLabel = new Label(String.format(java.util.Locale.US,
                        "= (%.2f / |Basis-Profit|) * 100 = %.2f%%", stdDev, cv));
                calcLabel.setFont(Font.font("Segoe UI", 10));
                calcLabel.setTextFill(Color.web("#8093a5"));

                calcBox.getChildren().addAll(topBox, formulaLabel, calcLabel);
            } else {
                calcBox.getChildren().add(topBox);
            }
            return new javafx.beans.property.SimpleObjectProperty<>(calcBox);
        });
        valCol.setPrefWidth(200);

        TableColumn<java.util.Map.Entry<String, Double>, VBox> chartCol = new TableColumn<>("Curve");
        chartCol.setCellValueFactory(c -> {
            String pName = c.getValue().getKey();
            java.util.List<com.backtester.report.SensitivityResult.DataPoint> curveData =
                    curves != null ? curves.get(pName) : null;
            if (curveData == null || curveData.isEmpty()) {
                return new javafx.beans.property.SimpleObjectProperty<>(null);
            }

            String baseValueStr = baseValues != null ? baseValues.get(pName) : null;
            double baseValue = 0;
            try { if (baseValueStr != null) baseValue = Double.parseDouble(baseValueStr); } catch (Exception ignored) {}
            final double finalBaseValue = baseValue;

            double minX = curveData.get(0).paramValue;
            double maxX = curveData.get(curveData.size() - 1).paramValue;
            double xPadding = (maxX - minX) * 0.05;
            if (xPadding == 0) xPadding = 1;

            javafx.scene.chart.NumberAxis xAxis = new javafx.scene.chart.NumberAxis();
            xAxis.setTickLabelsVisible(true); xAxis.setOpacity(1);
            xAxis.setTickMarkVisible(true); xAxis.setMinorTickVisible(false);
            xAxis.setTickLabelFill(Color.WHITE);
            xAxis.setAutoRanging(false);
            xAxis.setLowerBound(minX - xPadding);
            xAxis.setUpperBound(maxX + xPadding);

            javafx.scene.chart.NumberAxis yAxis = new javafx.scene.chart.NumberAxis();
            yAxis.setTickLabelsVisible(false); yAxis.setOpacity(0);
            yAxis.setTickMarkVisible(false); yAxis.setMinorTickVisible(false);

            double minY = curveData.stream().mapToDouble(d -> d.profit).min().orElse(0);
            double maxY = curveData.stream().mapToDouble(d -> d.profit).max().orElse(1);
            double yPadding = (maxY - minY) * 0.1;
            if (yPadding == 0) yPadding = 1;
            yAxis.setAutoRanging(false);
            yAxis.setLowerBound(minY - yPadding);
            yAxis.setUpperBound(maxY + yPadding);

            javafx.scene.chart.LineChart<Number, Number> chart = new javafx.scene.chart.LineChart<>(xAxis, yAxis);
            chart.setCreateSymbols(true);
            chart.setLegendVisible(false);
            chart.setAnimated(false);
            chart.setPrefHeight(100); chart.setMinHeight(100); chart.setMaxHeight(100);
            chart.setPrefWidth(300);
            chart.setHorizontalGridLinesVisible(false);
            chart.setVerticalGridLinesVisible(false);

            javafx.scene.chart.XYChart.Series<Number, Number> series = new javafx.scene.chart.XYChart.Series<>();
            com.backtester.report.SensitivityResult.DataPoint closestToBase = null;
            double minDiff = Double.MAX_VALUE;

            for (com.backtester.report.SensitivityResult.DataPoint dp : curveData) {
                series.getData().add(new javafx.scene.chart.XYChart.Data<>(dp.paramValue, dp.profit));
                double diff = Math.abs(dp.paramValue - finalBaseValue);
                if (diff < minDiff) { minDiff = diff; closestToBase = dp; }
            }
            chart.getData().add(series);
            final com.backtester.report.SensitivityResult.DataPoint finalClosest = closestToBase;

            chart.lookup(".chart-plot-background").setStyle("-fx-background-color: transparent;");
            chart.setStyle("-fx-padding: 0; -fx-background-color: transparent;");

            javafx.application.Platform.runLater(() -> {
                if (series.getNode() != null) {
                    series.getNode().setStyle("-fx-stroke: " + accentColor + "; -fx-stroke-width: 4px;");
                }
                for (javafx.scene.chart.XYChart.Data<Number, Number> data : series.getData()) {
                    if (data.getNode() != null) {
                        boolean isBase = finalClosest != null && data.getXValue().doubleValue() == finalClosest.paramValue;
                        if (isBase) {
                            data.getNode().setStyle("-fx-background-color: #ff3d00, white; -fx-background-insets: 0, 2; -fx-background-radius: 8px; -fx-padding: 6px;");
                        } else {
                            data.getNode().setStyle("-fx-background-color: " + accentColor + ", #0b0d13; -fx-background-insets: 0, 2; -fx-background-radius: 4px; -fx-padding: 3px;");
                        }
                    }
                }
            });

            double stepVal = curveData.size() > 1 ? (maxX - minX) / (curveData.size() - 1) : 0;
            String infoTxt = String.format(java.util.Locale.US, "Start: %.4f | Step: %.4f | End: %.4f", minX, stepVal, maxX)
                                   .replaceAll("0+ \\|", " |").replaceAll("\\. \\|", " |");
            Label infoLabel = new Label(infoTxt);
            infoLabel.setTextFill(Color.web("#8093a5"));
            infoLabel.setFont(Font.font("Segoe UI", 11));

            VBox chartBox = new VBox(5, chart, infoLabel);
            chartBox.setAlignment(Pos.CENTER);
            return new javafx.beans.property.SimpleObjectProperty<>(chartBox);
        });

        chartCol.setCellFactory(col -> new TableCell<java.util.Map.Entry<String, Double>, VBox>() {
            @Override
            protected void updateItem(VBox item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    setGraphic(item);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        cvTable.getColumns().addAll(paramCol, valCol, chartCol);
        if (cvMap != null) {
            for (java.util.Map.Entry<String, Double> entry : cvMap.entrySet()) {
                cvTable.getItems().add(new java.util.AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
            }
        }
        cvTable.setPrefHeight(300);
        cvTable.setFixedCellSize(130);
        cvTable.setSelectionModel(null);
        cvTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        return cvTable;
    }

    private void startSensitivityAnalysis() {
        List<CombinedPass> selected = selectedTable.getItems();
        if (selected == null || selected.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Bitte markiere zuerst mindestens einen Pass in der Combined Analysis Tabelle (Strg/Shift für mehrere).").show();
            return;
        }

        List<com.backtester.report.SensitivityResult> targets = new java.util.ArrayList<>();
        for (CombinedPass cp : selected) {
            targets.add(new com.backtester.report.SensitivityResult(cp));
        }

        sensitivityTable.getItems().setAll(targets);

        if (this.optConfig == null) {
            buildConfigFromUI();
        }
        OptimizationConfig baseConfig = this.optConfig;
        if (baseConfig == null || baseConfig.getExpert() == null || baseConfig.getExpert().isEmpty()) {
            new Alert(Alert.AlertType.ERROR, "Keine Basis-Konfiguration gefunden. Bitte führe zuerst eine Optimierung durch!").show();
            return;
        }

        currentSensitivityRunner = new com.backtester.engine.SensitivityRunner(com.backtester.config.AppConfig.getInstance());
        currentSensitivityRunner.setLogCallback(msg -> Platform.runLater(() -> logView.log("INFO", msg)));
        currentSensitivityRunner.setProgressCallback(pct -> Platform.runLater(() -> {
            progressBar.setProgress(pct / 100.0);
            progressLabel.setText("Sensitivity Scan: " + pct + "%");
        }));
        currentSensitivityRunner.setResultUpdateCallback(res -> Platform.runLater(() -> {
            sensitivityTable.refresh();
            saveStateToDb();
        }));

        List<EaParameter> allParams = eaParamManager.getEffectiveParameters(baseConfig.getExpert());

        currentTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                Platform.runLater(() -> setSensitivityUIState(true));
                currentSensitivityRunner.runSensitivityScan(targets, baseConfig, allParams);
                return null;
            }
            @Override
            protected void succeeded() {
                Platform.runLater(() -> {
                    setSensitivityUIState(false);
                    progressBar.setProgress(1.0);

                    if (currentSensitivityRunner != null && currentSensitivityRunner.isCancelled()) {
                        progressLabel.setText("Sensitivity Analysis cancelled.");
                        updateLlmAnalyzeButtonState(!sensitivityTable.getItems().isEmpty());
                    } else {
                        progressLabel.setText("Sensitivity Analysis completed.");
                        updateLlmAnalyzeButtonState(true);
                        new Alert(Alert.AlertType.INFORMATION, "Sensitivity Analysis completed!").show();
                    }

                    saveStateToDb();
                });
            }
            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    setSensitivityUIState(false);
                    logView.log("ERROR", "Sensitivity Task failed: " + getException().getMessage());
                });
            }
        };

        Thread t = new Thread(currentTask);
        t.setDaemon(true);
        t.start();
    }

    /** Applies current filter settings and re-populates the combined table. */
    private void deleteSelectedCombinedPasses() {
        if (combinedTable == null || combinedTable.getItems().isEmpty()) return;

        List<CombinedPass> selected = new java.util.ArrayList<>(combinedTable.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION,
                "Bitte markiere zuerst die Zeilen in der Tabelle, die du löschen möchtest.\n(Nutze Strg/Shift für Mehrfachauswahl)");
            alert.show();
            return;
        }

        javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION,
            "Bist du sicher, dass du die " + selected.size() + " ausgewählten Optimierungsergebnisse löschen möchtest?\n\nSie werden aus dieser Tabelle und aus dem Speicher entfernt.",
            javafx.scene.control.ButtonType.YES, javafx.scene.control.ButtonType.NO);
        confirm.setHeaderText("Ergebnisse löschen");

        if (confirm.showAndWait().orElse(javafx.scene.control.ButtonType.NO) == javafx.scene.control.ButtonType.YES) {
            // Remove from the underlying model so they don't reappear on refresh
            if (lastOptResult != null) {
                for (CombinedPass cp : selected) {
                    if (cp.getBacktestPass() != null) {
                        lastOptResult.getPasses().remove(cp.getBacktestPass());
                    }
                    if (cp.getForwardPass() != null) {
                        lastOptResult.getForwardPasses().remove(cp.getForwardPass());
                    }
                }
            }
            // Now refresh the table by applying the filter again
            applyCombinedFilter();
        }
    }

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

        // ── Unified Score-Gewichte aus Spinnern lesen
        OptimizationResult.ScoreWeights weights = getScoreWeightsFromUI();

        List<CombinedPass> all = lastOptResult.buildCombinedPasses(onlyMatched, weights);

        List<CombinedPass> filtered = all;
        if (filterEnabledCheck != null && filterEnabledCheck.isSelected()) {
            filtered = all.stream()
                .filter(cp -> cp.getBtProfit() >= filterMinBtProfit)
                .filter(cp -> Double.isNaN(cp.getFwProfit()) ? (filterMinFwProfit <= 0.0) : (cp.getFwProfit() >= filterMinFwProfit))
                .filter(cp -> cp.getBtTrades() >= filterMinBtTrades)
                .filter(cp -> cp.getFwTrades() >= filterMinFwTrades)
                .filter(cp -> cp.getBtDd() <= filterMaxBtDd)
                .filter(cp -> Double.isNaN(cp.getFwDd()) ? (filterMaxFwDd >= 100.0) : (cp.getFwDd() <= filterMaxFwDd))
                .filter(cp -> cp.getBtSharpe() >= filterMinBtSharpe)
                .filter(cp -> Double.isNaN(cp.getFwSharpe()) ? (filterMinFwSharpe <= 0.0) : (cp.getFwSharpe() >= filterMinFwSharpe))
                .filter(cp -> cp.getBtRecovery() >= filterMinBtRecovery)
                .filter(cp -> Double.isNaN(cp.getFwRecovery()) ? (filterMinFwRecovery <= 0.0) : (cp.getFwRecovery() >= filterMinFwRecovery))
                .filter(cp -> cp.getBtExpectedPayoff() >= filterMinBtPayoff)
                .filter(cp -> Double.isNaN(cp.getFwExpectedPayoff()) ? (filterMinFwPayoff <= 0.0) : (cp.getFwExpectedPayoff() >= filterMinFwPayoff))
                .filter(cp -> cp.getScore() >= filterMinScore)
                .filter(cp -> cp.getConsistency() >= filterMinConsistency)
                .collect(java.util.stream.Collectors.toList());
        }

        if (combinedSearchField != null) {
            String searchText = combinedSearchField.getText().trim();
            if (!searchText.isEmpty()) {
                filtered = filtered.stream()
                    .filter(cp -> String.valueOf(cp.getPassNumber()).contains(searchText))
                    .collect(java.util.stream.Collectors.toList());
            }
        }

        filtered = filtered.stream()
            .sorted(buildCombinedComparator())
            .collect(java.util.stream.Collectors.toList());

        combinedTable.getItems().setAll(filtered);
        combinedCountLabel.setText(filtered.size() + " von " + all.size() + " Passes");
        logView.log("INFO", "Unified Score: " + filtered.size() + " Passes (10 Säulen)");
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

        // ── Title
        Label title = new Label("\u2699\ufe0f  Unified Score-Gewichtung");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        title.setTextFill(Color.web("#ffd740"));

        Label hint = new Label("Jeder Parameter wird relativ zum anderen gewichtet.\n" +
                "Die Summe muss nicht genau 100 ergeben \u2014 sie wird automatisch normalisiert.\n" +
                "Unified Score = Performance + Robustheit in einem Score.");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 11px;");

        // \u2500\u2500 Slider rows (8 S\u00e4ulen \u2014 nur echte Messdaten)
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(10);

        Label[] labels = {
            dialogLabel("BT Profitabilit\u00e4t"),
            dialogLabel("FW Profitabilit\u00e4t"),
            dialogLabel("Konsistenz FW/BT"),
            dialogLabel("Risiko-Verh\u00e4ltnis"),
            dialogLabel("Sharpe Ratio"),
            dialogLabel("Stichprobengr\u00f6\u00dfe"),
            dialogLabel("FW Trade Count"),
            dialogLabel("Erholungsfaktor")
        };
        String[] tooltips = {
            "Backtest ROI + Profit Factor \u2014 Wie profitabel ist die Strategie im In-Sample?",
            "Forward ROI + Profit Factor \u2014 Wie profitabel ist die Strategie Out-of-Sample?",
            "Verh\u00e4ltnis FW/BT: 1.0 = perfekte Reproduzierbarkeit der Ergebnisse",
            "Return/Drawdown + Calmar Ratio \u2014 Gewinn im Verh\u00e4ltnis zum Risiko",
            "Von MT5 gemessene Sharpe Ratio (BT + FW gemittelt) \u2014 echte Kennzahl statt gesch\u00e4tzter Equity-Stabilit\u00e4t",
            "Anzahl Trades + reale Testjahre \u2014 Statistische Signifikanz der Ergebnisse",
            "Mehr FW-Trades = statistisch belastbarer. Zus\u00e4tzlich automatische Strafe wenn FW-Trades < median/2.",
            "Recovery Factor: Net Profit / Max Drawdown (BT und FW gemittelt)"
        };
        Spinner<Integer>[] spinners = new Spinner[]{
                wBtProfitSpin, wFwProfitSpin, wConsistSpin, wRiskSpin,
                wEquityConsistSpin, wSampleSizeSpin,
                wFwTradesSpin, wRecoverySpin};

        final int N = spinners.length;
        Slider[] sliders = new Slider[N];
        Label[] valLabels = new Label[N];

        com.backtester.database.DatabaseManager db = com.backtester.database.DatabaseManager.getInstance();
        TextField tfMin = new TextField(db.getSetting("opt.weight.recovery.min", "1.0"));
        tfMin.setPrefWidth(50);
        tfMin.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #fff; -fx-border-color: #444; -fx-border-width: 1; -fx-font-size: 11px;");

        TextField tfMax = new TextField(db.getSetting("opt.weight.recovery.max", "5.0"));
        tfMax.setPrefWidth(50);
        tfMax.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #fff; -fx-border-color: #444; -fx-border-width: 1; -fx-font-size: 11px;");

        for (int i = 0; i < N; i++) {
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

            if (i == 7) {
                HBox scaleBox = new HBox(6);
                scaleBox.setAlignment(Pos.CENTER_LEFT);
                scaleBox.setPadding(new Insets(4, 0, 0, 0));

                Label scaleLabel = new Label("Skalierung: Min");
                scaleLabel.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 11px;");

                Label scaleToLabel = new Label("bis Max");
                scaleToLabel.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 11px;");

                Button infoBtn = new Button("ℹ");
                infoBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ffd740; -fx-cursor: hand; -fx-font-size: 12px; -fx-padding: 0 4 0 4;");
                Tooltip infoTooltip = new Tooltip(
                    "Grenzwerte für die lineare Skalierung des Erholungsfaktors auf 0-100 Punkte.\n" +
                    "Werte unter Min geben 0 Punkte, über Max geben 100 Punkte."
                );
                infoTooltip.setShowDelay(javafx.util.Duration.millis(100));
                Tooltip.install(infoBtn, infoTooltip);
                infoBtn.setOnAction(evt -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Erholungsfaktor Skalierung");
                    alert.setHeaderText("Wie funktioniert die Skalierung des Erholungsfaktors?");
                    alert.setContentText(
                        "Der Erholungsfaktor (Net Profit / Max Drawdown) wird anhand dieser Grenzwerte linear auf 0-100 Punkte skaliert.\n\n" +
                        "• Ein Wert kleiner oder gleich Min erhält 0 Punkte.\n" +
                        "• Ein Wert größer oder gleich Max erhält 100 Punkte.\n" +
                        "• Dazwischen wird linear interpoliert.\n\n" +
                        "Wenn Sie z.B. Min=1.0 und Max=2.0 einstellen, hat eine Strategie mit Recovery Factor = 1.5 genau 50 Punkte."
                    );
                    alert.getDialogPane().setStyle("-fx-background-color: #1a1d27;");
                    alert.getDialogPane().lookup(".content.label").setStyle("-fx-text-fill: #b4bac8;");
                    alert.getDialogPane().lookup(".header-panel").setStyle("-fx-background-color: #1a1d27;");
                    if (alert.getDialogPane().lookup(".header-panel").lookup(".label") != null) {
                        alert.getDialogPane().lookup(".header-panel").lookup(".label").setStyle("-fx-text-fill: #ffd740;");
                    }
                    alert.initOwner(dialog);
                    alert.showAndWait();
                });

                scaleBox.getChildren().addAll(scaleLabel, tfMin, scaleToLabel, tfMax, infoBtn);

                VBox sliderContainer = new VBox(4);
                sliderContainer.getChildren().addAll(sl, scaleBox);
                grid.add(sliderContainer, 1, i);
            } else {
                grid.add(sl,             1, i);
            }
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
        for (int i = 0; i < N; i++) {
            sliders[i].valueProperty().addListener((o, a, b) -> refreshSum.run());
        }
        refreshSum.run();

        Label autoPenaltyHint = new Label(
            "Automatische Schutzschwelle: FW-Trades unter median/2 erhalten zus\u00e4tzlich " +
            "eine multiplikative Strafe (max. \u221250 %).\n" +
            "Alle 8 S\u00e4ulen basieren auf echten MT5-Messwerten (keine gesch\u00e4tzten Kennzahlen).");
        autoPenaltyHint.setWrapText(true);
        autoPenaltyHint.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 10px; -fx-font-style: italic;");

        // \u2500\u2500 Buttons
        Button resetBtn = new Button("\u21ba Zur\u00fccksetzen");
        resetBtn.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #b4bac8; -fx-border-color: #444; -fx-border-width: 1;");
        resetBtn.setOnAction(e -> {
            // Einzige Quelle f\u00fcr Defaults: ScoreWeights.defaults()
            OptimizationResult.ScoreWeights d = OptimizationResult.ScoreWeights.defaults();
            double[] defaults = {d.wBtProfit, d.wFwProfit, d.wConsistency, d.wRisk,
                    d.wEquityConsist, d.wSampleSize, d.wFwTrades, d.wRecovery};
            for (int i = 0; i < N; i++) {
                sliders[i].setValue(defaults[i]);
            }
            tfMin.setText(String.valueOf(d.recoveryMin));
            tfMax.setText(String.valueOf(d.recoveryMax));
        });

        boolean[] applied = {false};
        Button applyBtn = new Button("\u2714 \u00dcbernehmen & Schlie\u00dfen");
        applyBtn.setStyle("-fx-background-color: #00e5ff; -fx-text-fill: #0d0f17; -fx-font-weight: bold;");
        applyBtn.setOnAction(e -> {
            applied[0] = true;
            dialog.close();
        });

        Button cancelBtn2 = new Button("Abbrechen");
        cancelBtn2.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #b4bac8; -fx-border-color: #444; -fx-border-width: 1;");
        cancelBtn2.setOnAction(e -> {
            for (int i = 0; i < N; i++) {
                sliders[i].setValue(spinners[i].getValue());
            }
            dialog.close();
        });

        // Presets: Low = Performance-fokussiert, Med = ausgewogen, High = Robustheit-fokussiert
        Button btnPresetLow = new Button("Low / Zahm");
        btnPresetLow.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #a7f3d0; -fx-border-color: #10b981; -fx-border-width: 1; -fx-cursor: hand;");
        btnPresetLow.setOnAction(e -> {
            int[] lowWeights = {15, 15, 10, 10, 5, 15, 3, 5, 20, 15};
            for (int i = 0; i < N; i++) sliders[i].setValue(lowWeights[i]);
        });

        Button btnPresetMed = new Button("Med / Ausgewogen");
        btnPresetMed.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #fde047; -fx-border-color: #eab308; -fx-border-width: 1; -fx-cursor: hand;");
        btnPresetMed.setOnAction(e -> {
            int[] medWeights = {10, 15, 15, 15, 10, 25, 5, 10, 30, 25};
            for (int i = 0; i < N; i++) sliders[i].setValue(medWeights[i]);
        });

        Button btnPresetHigh = new Button("High / Streng");
        btnPresetHigh.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #fca5a5; -fx-border-color: #ef4444; -fx-border-width: 1; -fx-cursor: hand;");
        btnPresetHigh.setOnAction(e -> {
            int[] highWeights = {5, 10, 15, 15, 15, 25, 5, 15, 35, 30};
            for (int i = 0; i < N; i++) sliders[i].setValue(highWeights[i]);
        });

        Button btnPresetGrid = new Button("Grid / High-Trade");
        btnPresetGrid.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #38bdf8; -fx-border-color: #0284c7; -fx-border-width: 1; -fx-cursor: hand;");
        btnPresetGrid.setOnAction(e -> {
            int[] gridWeights = {5, 5, 15, 5, 5, 35, 5, 5, 35, 40};
            for (int i = 0; i < N; i++) sliders[i].setValue(gridWeights[i]);
        });

        HBox presetRow = new HBox(8, styledLabel("Voreinstellungen:"), btnPresetLow, btnPresetMed, btnPresetHigh, btnPresetGrid);
        presetRow.setAlignment(Pos.CENTER_LEFT);

        Button mainInfoBtn = DocHelper.createThickCircularInfoButton("Erklärung aller Indizes und Kennzahlen", () -> {
            DocHelper.showAllIndicesDocDialog(dialog);
        });
        HBox btnRow = new HBox(10, resetBtn, mainInfoBtn, new Region(), applyBtn, cancelBtn2);
        HBox.setHgrow(btnRow.getChildren().get(1), Priority.ALWAYS);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        javafx.scene.control.Separator sep = new javafx.scene.control.Separator();
        sep.setStyle("-fx-background-color: #2a2d3a;");
        javafx.scene.control.Separator sep2 = new javafx.scene.control.Separator();
        sep2.setStyle("-fx-background-color: #2a2d3a;");

        root.getChildren().addAll(title, hint, grid, sep, sumLabel, autoPenaltyHint, presetRow, sep2, btnRow);

        javafx.scene.Scene scene = new javafx.scene.Scene(root, 540, 710);
        dialog.setScene(scene);
        dialog.showAndWait();

        if (applied[0]) {
            double rMin = 1.0;
            double rMax = 5.0;
            try {
                rMin = Double.parseDouble(tfMin.getText().trim().replace(',', '.'));
            } catch (Exception ex) {}
            try {
                rMax = Double.parseDouble(tfMax.getText().trim().replace(',', '.'));
            } catch (Exception ex) {}
            db.saveSetting("opt.weight.recovery.min", String.valueOf(rMin));
            db.saveSetting("opt.weight.recovery.max", String.valueOf(rMax));

            db.saveSetting("opt.weight.btProfit", String.valueOf(wBtProfitSpin.getValue()));
            db.saveSetting("opt.weight.fwProfit", String.valueOf(wFwProfitSpin.getValue()));
            db.saveSetting("opt.weight.consistency", String.valueOf(wConsistSpin.getValue()));
            db.saveSetting("opt.weight.risk", String.valueOf(wRiskSpin.getValue()));
            db.saveSetting("opt.weight.equityConsist", String.valueOf(wEquityConsistSpin.getValue()));
            db.saveSetting("opt.weight.sampleSize", String.valueOf(wSampleSizeSpin.getValue()));
            db.saveSetting("opt.weight.fwTrades", String.valueOf(wFwTradesSpin.getValue()));
            db.saveSetting("opt.weight.recovery", String.valueOf(wRecoverySpin.getValue()));
            applyCombinedFilter();
        }
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
        TextField tfBtPayoff    = makeFilterField(String.valueOf(filterMinBtPayoff));
        TextField tfFwPayoff    = makeFilterField(String.valueOf(filterMinFwPayoff));
        TextField tfBtSharpe    = makeFilterField(String.valueOf(filterMinBtSharpe));
        TextField tfFwSharpe    = makeFilterField(String.valueOf(filterMinFwSharpe));
        TextField tfBtRecovery  = makeFilterField(String.valueOf(filterMinBtRecovery));
        TextField tfFwRecovery  = makeFilterField(String.valueOf(filterMinFwRecovery));
        TextField tfMinScore    = makeFilterField(String.valueOf(filterMinScore));
        TextField tfMinConsist  = makeFilterField(String.valueOf(filterMinConsistency));

        grid.add(styledLabel("BT Profit ≥"),        0, 0); grid.add(tfBtProfit,    1, 0);
        grid.add(styledLabel("FW Profit ≥"),        2, 0); grid.add(tfFwProfit,    3, 0);
        grid.add(styledLabel("Min BT Trades ≥"),    0, 1); grid.add(tfMinBtTrades, 1, 1);
        grid.add(styledLabel("Min FW Trades ≥"),    2, 1); grid.add(tfMinFwTrades, 3, 1);
        grid.add(styledLabel("Max BT Drawdown% ≤"), 0, 2); grid.add(tfMaxBtDd,     1, 2);
        grid.add(styledLabel("Max FW Drawdown% ≤"), 2, 2); grid.add(tfMaxFwDd,     3, 2);
        grid.add(styledLabel("BT Exp. Payoff ≥"),   0, 3); grid.add(tfBtPayoff,    1, 3);
        grid.add(styledLabel("FW Exp. Payoff ≥"),   2, 3); grid.add(tfFwPayoff,    3, 3);
        grid.add(styledLabel("BT Sharpe Ratio ≥"),  0, 4); grid.add(tfBtSharpe,    1, 4);
        grid.add(styledLabel("FW Sharpe Ratio ≥"),  2, 4); grid.add(tfFwSharpe,    3, 4);
        grid.add(styledLabel("BT Recovery Factor ≥"),0, 5); grid.add(tfBtRecovery,  1, 5);
        grid.add(styledLabel("FW Recovery Factor ≥"),2, 5); grid.add(tfFwRecovery,  3, 5);
        grid.add(styledLabel("Mindest-Score ≥"),    0, 6); grid.add(tfMinScore,    1, 6);
        grid.add(styledLabel("Mindest-Konsistenz ≥"),2, 6); grid.add(tfMinConsist,  3, 6);

        Button applyBtn = new Button("✔ Anwenden & Schließen");
        applyBtn.setStyle("-fx-background-color: #00e5ff; -fx-text-fill: #0d0f17; -fx-font-weight: bold;");
        applyBtn.setOnAction(e -> {
            filterMinBtProfit = parseFilterDouble(tfBtProfit, 0.0);
            filterMinFwProfit = parseFilterDouble(tfFwProfit, 0.0);
            filterMinBtTrades = parseFilterInt(tfMinBtTrades, 0);
            filterMinFwTrades = parseFilterInt(tfMinFwTrades, 0);
            filterMaxBtDd     = parseFilterDouble(tfMaxBtDd, 100.0);
            filterMaxFwDd     = parseFilterDouble(tfMaxFwDd, 100.0);
            filterMinBtPayoff = parseFilterDouble(tfBtPayoff, 0.0);
            filterMinFwPayoff = parseFilterDouble(tfFwPayoff, 0.0);
            filterMinBtSharpe = parseFilterDouble(tfBtSharpe, 0.0);
            filterMinFwSharpe = parseFilterDouble(tfFwSharpe, 0.0);
            filterMinBtRecovery = parseFilterDouble(tfBtRecovery, 0.0);
            filterMinFwRecovery = parseFilterDouble(tfFwRecovery, 0.0);
            filterMinScore      = parseFilterDouble(tfMinScore, 0.0);
            filterMinConsistency = parseFilterDouble(tfMinConsist, 0.0);

            com.backtester.database.DatabaseManager db = com.backtester.database.DatabaseManager.getInstance();
            db.saveSetting("opt.filter.minBtProfit", String.valueOf(filterMinBtProfit));
            db.saveSetting("opt.filter.minFwProfit", String.valueOf(filterMinFwProfit));
            db.saveSetting("opt.filter.minBtTrades", String.valueOf(filterMinBtTrades));
            db.saveSetting("opt.filter.minFwTrades", String.valueOf(filterMinFwTrades));
            db.saveSetting("opt.filter.maxBtDd", String.valueOf(filterMaxBtDd));
            db.saveSetting("opt.filter.maxFwDd", String.valueOf(filterMaxFwDd));
            db.saveSetting("opt.filter.minBtPayoff", String.valueOf(filterMinBtPayoff));
            db.saveSetting("opt.filter.minFwPayoff", String.valueOf(filterMinFwPayoff));
            db.saveSetting("opt.filter.minBtSharpe", String.valueOf(filterMinBtSharpe));
            db.saveSetting("opt.filter.minFwSharpe", String.valueOf(filterMinFwSharpe));
            db.saveSetting("opt.filter.minBtRecovery", String.valueOf(filterMinBtRecovery));
            db.saveSetting("opt.filter.minFwRecovery", String.valueOf(filterMinFwRecovery));
            db.saveSetting("opt.filter.minScore", String.valueOf(filterMinScore));
            db.saveSetting("opt.filter.minConsistency", String.valueOf(filterMinConsistency));

            // Auto-enable filter and save state
            if (filterEnabledCheck != null) {
                filterEnabledCheck.setSelected(true);
            }
            db.saveSetting("opt.filter.enabled", "true");

            dialog.close();
            applyCombinedFilter();
        });

        Button resetBtn = new Button("↺ Zurücksetzen");
        resetBtn.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #b4bac8; -fx-border-color: #444; -fx-border-width: 1;");
        resetBtn.setOnAction(e -> {
            tfBtProfit.setText("0.0");
            tfFwProfit.setText("0.0");
            tfMinBtTrades.setText("1");
            tfMinFwTrades.setText("0");
            tfMaxBtDd.setText("100.0");
            tfMaxFwDd.setText("100.0");
            tfBtPayoff.setText("0.0");
            tfFwPayoff.setText("0.0");
            tfBtSharpe.setText("0.0");
            tfFwSharpe.setText("0.0");
            tfBtRecovery.setText("0.0");
            tfFwRecovery.setText("0.0");
            tfMinScore.setText("0.0");
            tfMinConsist.setText("0.0");
        });

        HBox btnRow = new HBox(10, resetBtn, new Region(), applyBtn);
        HBox.setHgrow(btnRow.getChildren().get(1), Priority.ALWAYS);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        javafx.scene.control.Separator sep = new javafx.scene.control.Separator();
        sep.setStyle("-fx-background-color: #2a2d3a;");

        root.getChildren().addAll(title, grid, sep, btnRow);

        javafx.scene.Scene scene = new javafx.scene.Scene(root, 580, 440);
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
        passCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().getPassNumber()));
        passCol.setPrefWidth(60);

        TableColumn<com.backtester.report.OptimizationResult.Pass, String> profitCol = new TableColumn<>("Profit");
        profitCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(String.format(java.util.Locale.US, "%.2f", cellData.getValue().getProfit())));
        profitCol.setPrefWidth(100);

        TableColumn<com.backtester.report.OptimizationResult.Pass, Integer> tradesCol = new TableColumn<>("Trades");
        tradesCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().getTotalTrades()));
        tradesCol.setPrefWidth(70);

        TableColumn<com.backtester.report.OptimizationResult.Pass, String> pfCol = new TableColumn<>("Profit Factor");
        pfCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(String.format(java.util.Locale.US, "%.2f", cellData.getValue().getProfitFactor())));
        pfCol.setPrefWidth(100);

        TableColumn<com.backtester.report.OptimizationResult.Pass, String> payoffCol = new TableColumn<>("Expected Payoff");
        payoffCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(String.format(java.util.Locale.US, "%.2f", cellData.getValue().getExpectedPayoff())));
        payoffCol.setPrefWidth(120);

        TableColumn<com.backtester.report.OptimizationResult.Pass, String> ddCol = new TableColumn<>("Drawdown %");
        ddCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(String.format(java.util.Locale.US, "%.2f", cellData.getValue().getDrawdownPercent())));
        ddCol.setPrefWidth(100);

        TableColumn<com.backtester.report.OptimizationResult.Pass, String> recoveryCol = new TableColumn<>("Recovery Factor");
        recoveryCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(String.format(java.util.Locale.US, "%.2f", cellData.getValue().getRecoveryFactor())));
        recoveryCol.setPrefWidth(120);

        TableColumn<com.backtester.report.OptimizationResult.Pass, String> sharpeCol = new TableColumn<>("Sharpe Ratio");
        sharpeCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(String.format(java.util.Locale.US, "%.2f", cellData.getValue().getSharpeRatio())));
        sharpeCol.setPrefWidth(100);

        table.getColumns().addAll(passCol, profitCol, tradesCol, pfCol, payoffCol, ddCol, recoveryCol, sharpeCol);

        Label placeholder = new Label("No results yet. Run an optimization.");
        placeholder.setStyle("-fx-text-fill: #7e889a;");
        table.setPlaceholder(placeholder);

        table.setRowFactory(tv -> {
            javafx.scene.control.TableRow<com.backtester.report.OptimizationResult.Pass> row = new javafx.scene.control.TableRow<>();

            javafx.scene.control.ContextMenu contextMenu = new javafx.scene.control.ContextMenu();
            javafx.scene.control.MenuItem backtestItem = new javafx.scene.control.MenuItem("Backtest in MT5 ausführen (Terminal offen lassen)");
            backtestItem.setOnAction(event -> {
                com.backtester.report.OptimizationResult.Pass item = row.getItem();
                if (item != null) {
                    runVerificationBacktest(item);
                }
            });
            contextMenu.getItems().add(backtestItem);

            row.emptyProperty().addListener((obs, wasEmpty, isEmpty) -> {
                if (isEmpty) {
                    row.setContextMenu(null);
                } else {
                    row.setContextMenu(contextMenu);
                }
            });

            return row;
        });

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
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("MetaTrader EA", "*.ex5", "*.ex4"));

        com.backtester.config.AppConfig config = com.backtester.config.AppConfig.getInstance();
        String currentExpert = expertField.getText().trim();
        java.nio.file.Path expertsDir = null;
        if (config.isMt4(currentExpert)) {
            expertsDir = config.getExpertsDir("dummy.ex4");
        } else {
            expertsDir = config.getExpertsDir("dummy.ex5");
        }

        if (expertsDir != null && java.nio.file.Files.exists(expertsDir)) {
            chooser.setInitialDirectory(expertsDir.toFile());
        } else {
            java.nio.file.Path otherDir = config.isMt4(currentExpert) ? config.getExpertsDir("dummy.ex5") : config.getExpertsDir("dummy.ex4");
            if (otherDir != null && java.nio.file.Files.exists(otherDir)) {
                chooser.setInitialDirectory(otherDir.toFile());
            }
        }

        java.io.File selected = chooser.showOpenDialog(expertField.getScene().getWindow());
        if (selected != null) {
            String pathStr = selected.getAbsolutePath().toLowerCase();
            boolean isEx4 = pathStr.endsWith(".ex4");
            java.nio.file.Path activeExpertsDir = isEx4 ? config.getExpertsDir("dummy.ex4") : config.getExpertsDir("dummy.ex5");

            if (activeExpertsDir != null && selected.toPath().startsWith(activeExpertsDir)) {
                String relative = activeExpertsDir.relativize(selected.toPath()).toString();
                if (!isEx4 && relative.toLowerCase().endsWith(".ex5")) {
                    relative = relative.substring(0, relative.length() - 4);
                }
                expertField.setText(relative);
            } else {
                String path = selected.getAbsolutePath();
                if (!isEx4 && path.toLowerCase().endsWith(".ex5")) {
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

        String symbol = symbolCombo.getValue() != null ? symbolCombo.getValue() : "EURUSD";
        String period = periodCombo.getValue() != null ? periodCombo.getValue() : "H1";

        // Try DB first
        String dbParamsJson = com.backtester.database.DatabaseManager.getInstance().getEaParameterSettings(expert, symbol, period);
        if (dbParamsJson != null && !dbParamsJson.isEmpty()) {
            try {
                java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<java.util.List<com.backtester.config.EaParameter>>(){}.getType();
                java.util.List<com.backtester.config.EaParameter> params = new com.google.gson.Gson().fromJson(dbParamsJson, listType);
                if (params != null && !params.isEmpty()) {
                    java.util.List<com.backtester.config.EaParameter> diskParams = eaParamManager.getEffectiveParameters(expert);
                    params = eaParamManager.mergeLoadedWithExisting(diskParams, params);
                    eaParamManager.applyTranslations(expert, params);
                    paramTable.getItems().setAll(params);
                    logView.log("INFO", "Loaded " + params.size() + " parameters for " + EaParameterManager.extractEaBaseName(expert) + " [" + symbol + ", " + period + "] from DB");
                    return;
                }
            } catch (Exception e) {
                logView.log("WARN", "Failed to parse parameters from DB: " + e.getMessage());
            }
        }

        // Fallback to files
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

        // Load weights and filters from database
        try {
            com.backtester.database.DatabaseManager db = com.backtester.database.DatabaseManager.getInstance();
            filterMinScore = Double.parseDouble(db.getSetting("opt.filter.minScore", String.valueOf(filterMinScore)));
            filterMinConsistency = Double.parseDouble(db.getSetting("opt.filter.minConsistency", String.valueOf(filterMinConsistency)));

            filterMinBtProfit = Double.parseDouble(db.getSetting("opt.filter.minBtProfit", "0.01"));
            filterMinFwProfit = Double.parseDouble(db.getSetting("opt.filter.minFwProfit", "0.01"));
            filterMinBtTrades = Integer.parseInt(db.getSetting("opt.filter.minBtTrades", "100"));
            filterMinFwTrades = Integer.parseInt(db.getSetting("opt.filter.minFwTrades", "50"));
            filterMaxBtDd = Double.parseDouble(db.getSetting("opt.filter.maxBtDd", "100.0"));
            filterMaxFwDd = Double.parseDouble(db.getSetting("opt.filter.maxFwDd", "100.0"));
            filterMinBtPayoff = Double.parseDouble(db.getSetting("opt.filter.minBtPayoff", "0.0"));
            filterMinFwPayoff = Double.parseDouble(db.getSetting("opt.filter.minFwPayoff", "0.0"));
            filterMinBtSharpe = Double.parseDouble(db.getSetting("opt.filter.minBtSharpe", "0.0"));
            filterMinFwSharpe = Double.parseDouble(db.getSetting("opt.filter.minFwSharpe", "0.0"));
            filterMinBtRecovery = Double.parseDouble(db.getSetting("opt.filter.minBtRecovery", "1.0"));
            filterMinFwRecovery = Double.parseDouble(db.getSetting("opt.filter.minFwRecovery", "1.0"));

            boolean filterEnabled = Boolean.parseBoolean(db.getSetting("opt.filter.enabled", "false"));
            if (filterEnabledCheck != null) filterEnabledCheck.setSelected(filterEnabled);

            boolean onlyMatched = Boolean.parseBoolean(db.getSetting("opt.filter.onlyMatched", "true"));
            if (onlyMatchedCheck != null) onlyMatchedCheck.setSelected(onlyMatched);

            // Gewichte über die einzige Default-Quelle laden (ScoreWeights.loadFromDatabase)
            OptimizationResult.ScoreWeights sw = OptimizationResult.ScoreWeights.loadFromDatabase();
            if (wBtProfitSpin != null) wBtProfitSpin.getValueFactory().setValue((int) sw.wBtProfit);
            if (wFwProfitSpin != null) wFwProfitSpin.getValueFactory().setValue((int) sw.wFwProfit);
            if (wConsistSpin != null) wConsistSpin.getValueFactory().setValue((int) sw.wConsistency);
            if (wRiskSpin != null) wRiskSpin.getValueFactory().setValue((int) sw.wRisk);
            if (wEquityConsistSpin != null) wEquityConsistSpin.getValueFactory().setValue((int) sw.wEquityConsist);
            if (wSampleSizeSpin != null) wSampleSizeSpin.getValueFactory().setValue((int) sw.wSampleSize);
            if (wFwTradesSpin != null) wFwTradesSpin.getValueFactory().setValue((int) sw.wFwTrades);
            if (wRecoverySpin != null) wRecoverySpin.getValueFactory().setValue((int) sw.wRecovery);
        } catch (Exception e) {
            log.error("Failed to load weights and filters from DB", e);
        }

        updateDateRangeMonthsLabel();
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

    private void updateDateRangeMonthsLabel() {
        if (dateRangeMonthsLabel == null) return;
        if (fromDatePicker == null || toDatePicker == null) return;

        java.time.LocalDate from = fromDatePicker.getValue();
        java.time.LocalDate to = toDatePicker.getValue();

        if (from == null || to == null) {
            if (dateRangeTitleLabel != null) dateRangeTitleLabel.setText("Date Range:");
            dateRangeMonthsLabel.setText("");
            return;
        }

        if (to.isBefore(from)) {
            if (dateRangeTitleLabel != null) dateRangeTitleLabel.setText("Date Range:");
            dateRangeMonthsLabel.setText("(ungültiger Zeitraum)");
            return;
        }

        long months = ChronoUnit.MONTHS.between(from, to);
        if (dateRangeTitleLabel != null) {
            dateRangeTitleLabel.setText("Date Range (" + months + (months == 1 ? " Monat):" : " Monate):"));
        }
        dateRangeMonthsLabel.setText("(" + months + (months == 1 ? " Monat)" : " Monate)"));
    }

    // ==================== Optimization Execution Logic ====================

    private void startOptimization(boolean closeTerminal) {
        savePreferences();

        // Save current param table to custom .set
        if (!paramTable.getItems().isEmpty()) {
            String expert = expertField.getText().trim();
            String symbol = symbolCombo.getValue() != null ? symbolCombo.getValue() : "EURUSD";
            String period = periodCombo.getValue() != null ? periodCombo.getValue() : "H1";
            com.backtester.database.DatabaseManager.getInstance().saveEaParameterSettings(expert, symbol, period, new com.google.gson.Gson().toJson(paramTable.getItems()));
            eaParamManager.saveCustomParameters(expert, new java.util.ArrayList<>(paramTable.getItems()));
        }

        // Validation: check if any optimized parameter has start == stop range
        java.util.List<com.backtester.config.EaParameter> invalidParams = new java.util.ArrayList<>();
        for (com.backtester.config.EaParameter p : paramTable.getItems()) {
            if (p.isOptimizeEnabled()) {
                String start = p.getOptimizeStart();
                String end = p.getOptimizeEnd();
                if (start != null && end != null) {
                    start = start.trim();
                    end = end.trim();
                    if (!start.isEmpty() && !end.isEmpty()) {
                        boolean equal = start.equals(end);
                        if (!equal) {
                            try {
                                double dStart = Double.parseDouble(start);
                                double dEnd = Double.parseDouble(end);
                                if (Math.abs(dStart - dEnd) < 1e-9) {
                                    equal = true;
                                }
                            } catch (NumberFormatException e) {
                                // Not numeric, e.g. booleans
                            }
                        }
                        if (equal) {
                            invalidParams.add(p);
                        }
                    }
                }
            }
        }

        if (!invalidParams.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Die Optimierung kann nicht gestartet werden, da folgende Parameter zur Optimierung ausgewählt sind, aber identische Start- und Stopwerte haben:\n\n");
            for (com.backtester.config.EaParameter p : invalidParams) {
                sb.append(" - ").append(p.getName()).append(" (Wert: ").append(p.getOptimizeStart()).append(")\n");
            }
            sb.append("\nMetaTrader 5 erlaubt keine Optimierung mit einer Range-Größe von 1. Bitte deaktiviere das Häkchen (Opt) für diese Parameter oder vergrößere deren Range.");

            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle("Optimierungs-Fehler");
            alert.setHeaderText("Ungültige Parameter-Auswahl");
            alert.setContentText(sb.toString());
            alert.showAndWait();
            return;
        }

        // Safety-Check for 1-Parameter Forward Test
        boolean isForwardEnabled = forwardModeCombo.getSelectionModel().getSelectedIndex() > 0;
        long optimizedParamsCount = paramTable.getItems().stream().filter(com.backtester.config.EaParameter::isOptimizeEnabled).count();
        if (isForwardEnabled && optimizedParamsCount == 1) {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING,
                "Achtung: Für genetische Forward-Tests in MetaTrader 5 empfiehlt es sich, mindestens 2 Parameter zu aktivieren.\n\n" +
                "Mit nur 1 Parameter springt der genetische Algorithmus von MT5 oft nicht an (zu wenig Kombinationen), " +
                "was dazu führen kann, dass der Forward-Test ignoriert wird oder fehlschlägt.\n\n" +
                "Möchtest du trotzdem fortfahren?",
                javafx.scene.control.ButtonType.YES, javafx.scene.control.ButtonType.NO);
            alert.setHeaderText("Mögliches MT5 Forward-Test Problem");
            if (alert.showAndWait().orElse(javafx.scene.control.ButtonType.NO) != javafx.scene.control.ButtonType.YES) {
                return;
            }
        }

        buildConfigFromUI();
        optConfig.setShutdownTerminal(closeTerminal);


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

    private void buildConfigFromUI() {
        this.optConfig = new OptimizationConfig();
        optConfig.setShutdownTerminal(false);
        optConfig.setExpert(expertField.getText() != null ? expertField.getText().trim() : "");
        try {
            if (!optConfig.getExpert().isEmpty()) {
                String preset = eaParamManager.prepareForBacktest(optConfig.getExpert());
                if (preset != null) {
                    optConfig.setExpertParameters(preset);
                }
            }
        } catch (Exception e) {
            logView.log("ERROR", "Cannot resolve config path in buildConfigFromUI");
        }

        optConfig.setSymbol(symbolCombo.getValue());
        optConfig.setPeriod(periodCombo.getValue());
        int mIdx = modelCombo.getSelectionModel().getSelectedIndex();
        optConfig.setModel(mIdx >= 0 ? mIdx : 0);
        if (fromDatePicker.getValue() != null) optConfig.setFromDate(fromDatePicker.getValue());
        if (toDatePicker.getValue() != null) optConfig.setToDate(toDatePicker.getValue());

        try {
            optConfig.setDeposit(Integer.parseInt(depositField.getText().trim()));
        } catch (Exception e) {
            optConfig.setDeposit(10000);
        }
        optConfig.setCurrency(currencyField.getText().trim());
        optConfig.setLeverage(leverageField.getText().trim());

        optConfig.setOptimizationMode(OptimizationConfig.OPTIMIZATION_MODE_VALUES[Math.max(0, modeCombo.getSelectionModel().getSelectedIndex())]);
        optConfig.setOptimizationCriterion(Math.max(0, optimizationCriterionCombo.getSelectionModel().getSelectedIndex()));
        optConfig.setForwardMode(Math.max(0, forwardModeCombo.getSelectionModel().getSelectedIndex()));
        if (forwardDatePicker.getValue() != null) optConfig.setForwardDate(forwardDatePicker.getValue());
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
                    saveStateToDb();
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
        if (startSenBtn != null) {
            startSenBtn.setDisable(running);
        }
    }

    private void setSensitivityUIState(boolean running) {
        if (startSenBtn != null) {
            startSenBtn.setDisable(running);
        }
        if (cancelSenBtn != null) {
            cancelSenBtn.setDisable(!running);
        }
        if (startBtn != null) {
            startBtn.setDisable(running);
        }
        if (startKeepOpenBtn != null) {
            startKeepOpenBtn.setDisable(running);
        }
        if (expertField != null) {
            expertField.setDisable(running);
        }
        if (symbolCombo != null) {
            symbolCombo.setDisable(running);
        }
        if (paramTable != null) {
            paramTable.setDisable(running);
        }
    }

    // ==================== AutoConfig & File I/O Logic ====================

    private void autoConfigParameters() {
        AutoConfigDialogHelper.showAutoConfigDialog(
            paramTable,
            logView,
            root.getScene() != null ? root.getScene().getWindow() : null,
            this::saveParametersOnDemand
        );
    }

    private void generateDefaultConfig() {
        String expert = expertField.getText().trim();
        if (expert.isEmpty()) {
            logView.log("WARN", "Please select an Expert Advisor first.");
            return;
        }

        logView.log("INFO", "Starting config generation for " + EaParameterManager.extractEaBaseName(expert) + "... Please wait.");

        Task<Boolean> task = new Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                return eaParamManager.generateDefaultConfig(expert);
            }
        };

        task.setOnSucceeded(evt -> {
            boolean success = task.getValue();
            if (success) {
                logView.log("INFO", "Config generated successfully. Loading parameters...");
                loadParameters();
            } else {
                logView.log("ERROR", "Failed to generate config. Check MetaTrader logs / config.");
            }
        });

        task.setOnFailed(evt -> {
            logView.log("ERROR", "Config generation failed: " + task.getException().getMessage());
        });

        new Thread(task).start();
    }

    private void loadFromFile() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Load .set File");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("MT5 Set Files", "*.set"));
        java.io.File file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file != null) {
            java.util.List<com.backtester.config.EaParameter> params = eaParamManager.readSetFile(file.toPath());
            if (params != null && !params.isEmpty()) {
                String expertPath = expertField.getText().trim();
                java.util.List<com.backtester.config.EaParameter> existing = new java.util.ArrayList<>(paramTable.getItems());
                java.util.List<com.backtester.config.EaParameter> merged = eaParamManager.mergeLoadedWithExisting(params, existing);
                eaParamManager.applyTranslations(expertPath, merged);
                paramTable.getItems().setAll(merged);
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
        String expertPath = expertField.getText().trim();
        String eaName = com.backtester.config.EaParameterManager.extractEaBaseName(expertPath);
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Save .set File");
        chooser.setInitialFileName(eaName.isEmpty() ? "params.set" : eaName + ".set");
        boolean isMt4 = config.isMt4(expertPath);
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter(isMt4 ? "MT4 Set Files" : "MT5 Set Files", "*.set"));
        java.io.File file = chooser.showSaveDialog(root.getScene().getWindow());
        if (file != null) {
            eaParamManager.writeSetFile(file.toPath(), new java.util.ArrayList<>(paramTable.getItems()), expertPath);
            logView.log("INFO", "Saved parameters to " + file.getName());
        }
    }

    public void bindTab(Tab tab) {
        javafx.collections.ListChangeListener<com.backtester.report.OptimizationResult.Pass> listListener = c -> {
            updateTabTitle(tab, resultTable.getItems() != null ? resultTable.getItems().size() : 0);
        };

        updateTabTitle(tab, resultTable.getItems() != null ? resultTable.getItems().size() : 0);
        if (resultTable.getItems() != null) {
            resultTable.getItems().addListener(listListener);
        }

        resultTable.itemsProperty().addListener((obs, oldList, newList) -> {
            if (oldList != null) {
                oldList.removeListener(listListener);
            }
            updateTabTitle(tab, newList != null ? newList.size() : 0);
            if (newList != null) {
                newList.addListener(listListener);
            }
        });
    }

    private void showCvExplanationDialog(String title, String mainHeading, String htmlBodyContent) {
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle(title);
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        if (root.getScene() != null) {
            stage.initOwner(root.getScene().getWindow());
        }

        VBox box = new VBox(15);
        box.setPadding(new Insets(20));
        box.setStyle("-fx-background-color: #0b0d13; -fx-border-color: #3e4555; -fx-border-width: 1px; -fx-border-radius: 8px; -fx-background-radius: 8px;");

        Label titleLabel = new Label(mainHeading);
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        titleLabel.setTextFill(Color.web("#00e5ff"));

        javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
        webView.setPrefSize(800, 500);

        String fullHtml = "<html><head><style>"
                + "body { background-color:#161821; color:#c8cddc; font-family:\"Segoe UI\", sans-serif; font-size:16px; line-height:1.7; margin:20px; }"
                + "h3 { color:#00e5ff; font-size:20px; margin-top:20px; border-bottom: 1px solid #3e4555; padding-bottom: 5px; font-weight: bold; }"
                + "h4 { color:#e2e8f0; font-size:17px; margin-top:15px; font-weight: bold; }"
                + "code { background-color:#1f2937; padding:4px 8px; border-radius:4px; color:#38bdf8; font-family:Consolas, monospace; font-size:14px; display:block; margin:8px 0; }"
                + "ul, ol { margin-left: 20px; padding-left: 0; }"
                + "li { margin-bottom: 8px; }"
                + "</style></head><body>"
                + htmlBodyContent
                + "</body></html>";
        webView.getEngine().loadContent(fullHtml);
        webView.setStyle("-fx-background-color: #161821;");

        Button closeBtn = new Button("Schließen");
        closeBtn.getStyleClass().addAll("button");
        closeBtn.setStyle("-fx-background-color: #374151; -fx-text-fill: #d1d5db; -fx-font-weight: bold; -fx-padding: 8 16; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> stage.close());

        HBox btnBox = new HBox(closeBtn);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        box.getChildren().addAll(titleLabel, webView, btnBox);
        VBox.setVgrow(webView, Priority.ALWAYS);

        javafx.scene.Scene scene = new javafx.scene.Scene(box, 850, 650);
        stage.setScene(scene);
        stage.show();
    }

    private String getBtCvExplanationHtml() {
        return "<h3>BT CV (worst) - Backtest Variationskoeffizient</h3>"
             + "<p>Der <b>Variationskoeffizient (CV - Coefficient of Variation)</b> im Backtest-Zeitraum (In-Sample) misst die relative Streuung der Profite, wenn einzelne Optimierungsparameter variiert werden.</p>"
             + "<h4>Berechnung:</h4>"
             + "<p>Für jeden optimierten Parameter wird ein Sweep um die engere Umgebung des Optimalwerts durchgeführt. Daraus wird berechnet:</p>"
             + "<code>"
             + "CV = (Standardabweichung des Profits / Absoluter Basis-Profit) * 100%"
             + "</code>"
             + "<p>Der <b>BT CV (worst)</b> ist der <b>schlechteste (maximale) CV-Wert</b> über alle getesteten Parameter. Eine Strategie ist nur so robust wie ihr empfindlichster Parameter.</p>"
             + "<h4>Bedeutung der Werte:</h4>"
             + "<ul>"
             + "  <li><span style='color:#00e676; font-weight:bold;'>&lt; 30% (Robust):</span> Sehr stabil. Parameteränderungen in der nahen Umgebung haben kaum Einfluss auf das Endergebnis.</li>"
             + "  <li><span style='color:#ffd740; font-weight:bold;'>30% - 60% (Akzeptabel):</span> Mäßige Empfindlichkeit. Vertretbares Risiko für Überoptimierung.</li>"
             + "  <li><span style='color:#ff3b30; font-weight:bold;'>&gt; 60% (Fragil):</span> Sehr empfindlich. Kleine Parameteränderungen führen zu massiven Unterschieden im Gewinn oder Verlust.</li>"
             + "</ul>"
             + "<h4>Warum sind die Werte manchmal so hoch (z.B. 200%)?</h4>"
             + "<ol>"
             + "  <li><b>Geringer Basis-Profit:</b> Da der Basis-Profit im Nenner steht, explodiert der CV-Wert bei profitarmen Strategien. Wenn eine Strategie z.B. nur 10 € Gewinn macht, führt eine kleine Schwankung um 20 € bereits zu einem CV von 200%.</li>"
             + "  <li><b>Harte Filterung:</b> Wir testen die Parameter isoliert durch erneutes Backtesting. Fällt der Profit bei einer kleinen Änderung eines Parameters stark ab, deutet das auf <i>Curve-Fitting</i> (Überoptimierung) hin. Ein hoher CV warnt dich vor unzuverlässigen Strategien.</li>"
             + "  <li><b>Capping:</b> Um extreme Werte übersichtlich darzustellen, deckeln wir den angezeigten CV-Wert bei maximal 200%.</li>"
             + "</ol>";
    }

    private String getFwCvExplanationHtml() {
        return "<h3>FW CV (worst) - Forward Variationskoeffizient</h3>"
             + "<p>Der <b>Variationskoeffizient (CV - Coefficient of Variation)</b> im Forward-Zeitraum (Out-of-Sample) misst die relative Streuung der Profite im Forward-Test, wenn die Parameter variiert werden.</p>"
             + "<h4>Berechnung:</h4>"
             + "<p>Es wird derselbe Parameter-Sweep wie im Backtest durchgeführt, jedoch ausschließlich auf den Out-of-Sample Forward-Daten:</p>"
             + "<code>"
             + "CV = (Standardabweichung des Profits / Absoluter Forward-Profit) * 100%"
             + "</code>"
             + "<p>Der <b>FW CV (worst)</b> zeigt den maximalen CV-Wert aller Parameter im Forward-Test-Zeitraum.</p>"
             + "<h4>Bedeutung der Werte:</h4>"
             + "<ul>"
             + "  <li><span style='color:#00e676; font-weight:bold;'>&lt; 30% (Robust):</span> Exzellente Stabilität auch auf unbekannten Zukunftsdaten (Forward).</li>"
             + "  <li><span style='color:#ffd740; font-weight:bold;'>30% - 60% (Akzeptabel):</span> Vertretbare Abweichung im Forward-Zeitraum.</li>"
             + "  <li><span style='color:#ff3b30; font-weight:bold;'>&gt; 60% (Fragil):</span> Extrem unzuverlässiges Verhalten in der Forward-Phase bei minimalen Parameterverschiebungen.</li>"
             + "</ul>"
             + "<h4>Warum sind die Werte manchmal so hoch (z.B. 200%)?</h4>"
             + "<ol>"
             + "  <li><b>Geringer Forward-Profit:</b> Im Forward-Zeitraum sind die Gewinne oft noch kleiner oder nahe null. Dadurch wird der Nenner sehr klein, was zu extrem hohen Prozentwerten führt.</li>"
             + "  <li><b>Verlustphasen im Forward:</b> Wenn der Forward-Test schlechter läuft (was oft vorkommt, da Out-of-Sample-Daten), steigt die Standardabweichung im Verhältnis zum Profit drastisch an.</li>"
             + "  <li><b>Capping:</b> Um extreme Werte übersichtlich darzustellen, deckeln wir den angezeigten CV-Wert bei maximal 200%.</li>"
             + "</ol>";
    }

    private void updateTabTitle(Tab tab, int count) {
        javafx.application.Platform.runLater(() -> tab.setText("Optimizer (" + count + ")"));
    }

    public OptimizationResult getLastOptResult() {
        return lastOptResult;
    }

    private void showScoreDoc() {
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("Strategie-Score - Dokumentation & Filter");
        stage.initModality(javafx.stage.Modality.NONE);
        if (root.getScene() != null) {
            stage.initOwner(root.getScene().getWindow());
        }

        VBox mainBox = new VBox(15);
        mainBox.setPadding(new Insets(20));
        mainBox.setStyle("-fx-background-color: #0d0f17;");

        // Header
        Label titleLabel = new Label("🏆 Strategie-Score (Kombinierter Filter)");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        titleLabel.setTextFill(Color.web("#00e5ff"));

        // Documentation Area
        javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
        webView.setPrefSize(950, 500);
        webView.setStyle("-fx-background-color: #161821;");

        webView.getEngine().loadContent(DocHelper.getScoreDocHtml());

        // Filter Controls Area (Glassmorphic style panel)
        VBox filterBox = new VBox(10);
        filterBox.setPadding(new Insets(15));
        filterBox.setStyle("-fx-background-color: #1a1d27; -fx-border-color: #3e4555; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-background-radius: 6px;");

        Label sliderTitle = new Label("Score-Filter konfigurieren");
        sliderTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        sliderTitle.setTextFill(Color.web("#e2e8f0"));

        Slider slider = new Slider(0, 100, filterMinScore);
        slider.setShowTickLabels(true);
        slider.setShowTickMarks(true);
        slider.setMajorTickUnit(20);
        slider.setMinorTickCount(5);
        slider.setBlockIncrement(5);
        slider.setStyle("-fx-control-inner-background: #2a2d3a;");

        Label valLabel = new Label(String.format(java.util.Locale.US, "Mindest-Score: %.1f", filterMinScore));
        valLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        valLabel.setTextFill(Color.web("#00e5ff"));

        slider.valueProperty().addListener((obs, oldVal, newVal) -> {
            filterMinScore = Math.round(newVal.doubleValue() * 10.0) / 10.0;
            valLabel.setText(String.format(java.util.Locale.US, "Mindest-Score: %.1f", filterMinScore));
        });

        Button btnLow = new Button("Low / Zahm (30.0)");
        btnLow.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #a7f3d0; -fx-border-color: #10b981; -fx-border-width: 1; -fx-cursor: hand;");
        btnLow.setOnAction(e -> slider.setValue(30.0));

        Button btnMed = new Button("Med / Ausgewogen (50.0)");
        btnMed.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #fde047; -fx-border-color: #eab308; -fx-border-width: 1; -fx-cursor: hand;");
        btnMed.setOnAction(e -> slider.setValue(50.0));

        Button btnHigh = new Button("High / Streng (70.0)");
        btnHigh.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #fca5a5; -fx-border-color: #ef4444; -fx-border-width: 1; -fx-cursor: hand;");
        btnHigh.setOnAction(e -> slider.setValue(70.0));

        HBox presetRow = new HBox(10, styledLabel("Voreinstellungen:"), btnLow, btnMed, btnHigh);
        presetRow.setAlignment(Pos.CENTER_LEFT);

        filterBox.getChildren().addAll(sliderTitle, presetRow, slider, valLabel);

        // Buttons
        Button okBtn = new Button("✔ OK / Übernehmen");
        okBtn.setStyle("-fx-background-color: #00e5ff; -fx-text-fill: #0d0f17; -fx-font-weight: bold; -fx-padding: 8 16; -fx-cursor: hand;");
        okBtn.setOnAction(e -> stage.close());

        Button cancelBtn = new Button("Abbrechen");
        cancelBtn.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #b4bac8; -fx-border-color: #444; -fx-border-width: 1; -fx-padding: 8 16; -fx-cursor: hand;");
        // Restore original value on cancel
        double originalValue = filterMinScore;
        cancelBtn.setOnAction(e -> {
            filterMinScore = originalValue;
            stage.close();
        });

        HBox btnRow = new HBox(10, new Region(), cancelBtn, okBtn);
        HBox.setHgrow(btnRow.getChildren().get(0), Priority.ALWAYS);

        mainBox.getChildren().addAll(titleLabel, webView, filterBox, btnRow);
        VBox.setVgrow(webView, Priority.ALWAYS);

        stage.setOnHiding(e -> {
            if (filterEnabledCheck != null) {
                filterEnabledCheck.setSelected(true);
            }
            com.backtester.database.DatabaseManager.getInstance().saveSetting("opt.filter.enabled", "true");
            com.backtester.database.DatabaseManager.getInstance().saveSetting("opt.filter.minScore", String.valueOf(filterMinScore));
            applyCombinedFilter();
        });

        javafx.scene.Scene scene = new javafx.scene.Scene(mainBox, 1000, 800);
        try {
            java.net.URL css = DocHelper.class.getResource("/css/antigravity.css");
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            } else {
                java.net.URL css2 = DocHelper.class.getResource("/style.css");
                if (css2 != null) scene.getStylesheets().add(css2.toExternalForm());
            }
        } catch (Exception ignored) {}

        stage.setScene(scene);
        stage.show();
    }

    private void showConsistencyDoc() {
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("Konsistenz - Dokumentation & Filter");
        stage.initModality(javafx.stage.Modality.NONE);
        if (root.getScene() != null) {
            stage.initOwner(root.getScene().getWindow());
        }

        VBox mainBox = new VBox(15);
        mainBox.setPadding(new Insets(20));
        mainBox.setStyle("-fx-background-color: #0d0f17;");

        // Header
        Label titleLabel = new Label("⚖️ Konsistenz (FW/BT-Verhältnis)");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        titleLabel.setTextFill(Color.web("#00e5ff"));

        // Documentation Area
        javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
        webView.setPrefSize(950, 500);
        webView.setStyle("-fx-background-color: #161821;");

        webView.getEngine().loadContent(DocHelper.getConsistencyDocHtml());

        // Filter Controls Area (Glassmorphic style panel)
        VBox filterBox = new VBox(10);
        filterBox.setPadding(new Insets(15));
        filterBox.setStyle("-fx-background-color: #1a1d27; -fx-border-color: #3e4555; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-background-radius: 6px;");

        Label sliderTitle = new Label("Konsistenz-Filter konfigurieren");
        sliderTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        sliderTitle.setTextFill(Color.web("#e2e8f0"));

        Slider slider = new Slider(0.0, 2.0, filterMinConsistency);
        slider.setShowTickLabels(true);
        slider.setShowTickMarks(true);
        slider.setMajorTickUnit(0.5);
        slider.setMinorTickCount(5);
        slider.setBlockIncrement(0.1);
        slider.setStyle("-fx-control-inner-background: #2a2d3a;");

        Label valLabel = new Label(String.format(java.util.Locale.US, "Mindest-Konsistenz: %.2f", filterMinConsistency));
        valLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        valLabel.setTextFill(Color.web("#00e5ff"));

        slider.valueProperty().addListener((obs, oldVal, newVal) -> {
            filterMinConsistency = Math.round(newVal.doubleValue() * 100.0) / 100.0;
            valLabel.setText(String.format(java.util.Locale.US, "Mindest-Konsistenz: %.2f", filterMinConsistency));
        });

        Button btnLow = new Button("Low / Zahm (0.4)");
        btnLow.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #a7f3d0; -fx-border-color: #10b981; -fx-border-width: 1; -fx-cursor: hand;");
        btnLow.setOnAction(e -> slider.setValue(0.4));

        Button btnMed = new Button("Med / Ausgewogen (0.6)");
        btnMed.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #fde047; -fx-border-color: #eab308; -fx-border-width: 1; -fx-cursor: hand;");
        btnMed.setOnAction(e -> slider.setValue(0.6));

        Button btnHigh = new Button("High / Streng (0.8)");
        btnHigh.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #fca5a5; -fx-border-color: #ef4444; -fx-border-width: 1; -fx-cursor: hand;");
        btnHigh.setOnAction(e -> slider.setValue(0.8));

        HBox presetRow = new HBox(10, styledLabel("Voreinstellungen:"), btnLow, btnMed, btnHigh);
        presetRow.setAlignment(Pos.CENTER_LEFT);

        filterBox.getChildren().addAll(sliderTitle, presetRow, slider, valLabel);

        // Buttons
        Button okBtn = new Button("✔ OK / Übernehmen");
        okBtn.setStyle("-fx-background-color: #00e5ff; -fx-text-fill: #0d0f17; -fx-font-weight: bold; -fx-padding: 8 16; -fx-cursor: hand;");
        okBtn.setOnAction(e -> stage.close());

        Button cancelBtn = new Button("Abbrechen");
        cancelBtn.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #b4bac8; -fx-border-color: #444; -fx-border-width: 1; -fx-padding: 8 16; -fx-cursor: hand;");
        // Restore original value on cancel
        double originalValue = filterMinConsistency;
        cancelBtn.setOnAction(e -> {
            filterMinConsistency = originalValue;
            stage.close();
        });

        HBox btnRow = new HBox(10, new Region(), cancelBtn, okBtn);
        HBox.setHgrow(btnRow.getChildren().get(0), Priority.ALWAYS);

        mainBox.getChildren().addAll(titleLabel, webView, filterBox, btnRow);
        VBox.setVgrow(webView, Priority.ALWAYS);

        stage.setOnHiding(e -> {
            if (filterEnabledCheck != null) {
                filterEnabledCheck.setSelected(true);
            }
            com.backtester.database.DatabaseManager.getInstance().saveSetting("opt.filter.enabled", "true");
            com.backtester.database.DatabaseManager.getInstance().saveSetting("opt.filter.minConsistency", String.valueOf(filterMinConsistency));
            applyCombinedFilter();
        });

        javafx.scene.Scene scene = new javafx.scene.Scene(mainBox, 1000, 800);
        try {
            java.net.URL css = DocHelper.class.getResource("/css/antigravity.css");
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            } else {
                java.net.URL css2 = DocHelper.class.getResource("/style.css");
                if (css2 != null) scene.getStylesheets().add(css2.toExternalForm());
            }
        } catch (Exception ignored) {}

        stage.setScene(scene);
        stage.show();
    }

    private void runVerificationBacktest(com.backtester.report.OptimizationResult.Pass pass) {
        if (pass == null) return;
        String expert = expertField.getText().trim();
        if (expert.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Bitte wähle zuerst einen Expert Advisor aus.").show();
            return;
        }

        com.backtester.config.AppConfig config = com.backtester.config.AppConfig.getInstance();
        java.nio.file.Path mtDir = config.getMtInstallDir(expert);
        if (mtDir == null) {
            new Alert(Alert.AlertType.ERROR, "MetaTrader Installationsverzeichnis ist nicht konfiguriert.").show();
            return;
        }

        List<EaParameter> allParams = eaParamManager.getEffectiveParameters(expert);
        if (allParams == null || allParams.isEmpty()) {
            new Alert(Alert.AlertType.ERROR, "Konnte Parameter für den Expert Advisor nicht laden.").show();
            return;
        }

        // Parameterwerte aus dem gewählten Pass in die EA-Parameterliste schreiben und Optimierung deaktivieren
        java.util.Map<String, String> passVals = pass.getParameterValues();
        for (EaParameter param : allParams) {
            if (passVals.containsKey(param.getName())) {
                param.setValue(passVals.get(param.getName()));
            }
            param.setOptimizeEnabled(false);
        }

        // Parameter direkt in das Tester-Profilverzeichnis schreiben
        String eaName = EaParameterManager.extractEaBaseName(expert);
        String presetFileName = "Backtester_" + eaName + "_Verify.set";
        java.nio.file.Path presetsDir = config.getTesterProfilesDir(expert);
        try {
            java.nio.file.Files.createDirectories(presetsDir);
        } catch (java.io.IOException ex) {
            log.error("Failed to create Tester directory", ex);
            new Alert(Alert.AlertType.ERROR, "Fehler beim Erstellen des Presets-Verzeichnisses: " + ex.getMessage()).show();
            return;
        }

        java.nio.file.Path destFile = presetsDir.resolve(presetFileName);
        eaParamManager.writeSetFile(destFile, allParams, expert);

        // BacktestConfig erstellen
        BacktestConfig btConfig = new BacktestConfig();
        btConfig.setExpert(expert);
        btConfig.setExpertParameters(presetFileName);
        btConfig.setSymbol(symbolCombo.getValue());
        btConfig.setPeriod(periodCombo.getValue());
        int mIdx = modelCombo.getSelectionModel().getSelectedIndex();
        btConfig.setModel(mIdx >= 0 ? mIdx : 0);

        if (fromDatePicker.getValue() != null) {
            btConfig.setFromDate(fromDatePicker.getValue());
        }
        if (toDatePicker.getValue() != null) {
            btConfig.setToDate(toDatePicker.getValue());
        }

        try {
            btConfig.setDeposit(Integer.parseInt(depositField.getText().trim()));
        } catch (Exception ex) {
            btConfig.setDeposit(10000);
        }
        btConfig.setCurrency(currencyField.getText().trim());
        btConfig.setLeverage(leverageField.getText().trim());

        // Terminal offen lassen
        btConfig.setShutdownTerminal(false);

        logView.log("INFO", "Starte Verifikations-Backtest für Pass #" + pass.getPassNumber() + " (Terminal bleibt offen)...");
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progressLabel.setText("Backtest Pass #" + pass.getPassNumber() + "...");
        setUIState(true);

        BacktestRunner runner = new BacktestRunner();
        runner.setLogCallback(msg -> Platform.runLater(() -> logView.log("INFO", msg)));

        Task<com.backtester.report.BacktestResult> task = new Task<>() {
            @Override
            protected com.backtester.report.BacktestResult call() throws Exception {
                return runner.runBacktest(btConfig);
            }
        };

        task.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                setUIState(false);
                progressBar.setProgress(1.0);
                progressLabel.setText("Backtest Pass #" + pass.getPassNumber() + " fertig");
                com.backtester.report.BacktestResult res = task.getValue();
                if (res != null && res.isSuccess()) {
                    logView.log("INFO", "Verifikations-Backtest für Pass #" + pass.getPassNumber() + " erfolgreich abgeschlossen.");
                    // Report anzeigen
                    try {
                        javax.swing.SwingUtilities.invokeLater(() -> {
                            com.backtester.ui.ReportViewerDialog.showForDirectory(null, res.getOutputDirectory());
                        });
                    } catch (Exception ex) {
                        logView.log("ERROR", "Fehler beim Öffnen des Reports: " + ex.getMessage());
                    }
                } else {
                    logView.log("WARN", "Verifikations-Backtest für Pass #" + pass.getPassNumber() + " fehlgeschlagen oder keine Trades.");
                }
            });
        });

        task.setOnFailed(e -> {
            Platform.runLater(() -> {
                setUIState(false);
                progressBar.setProgress(0.0);
                progressLabel.setText("Fehler");
                logView.log("ERROR", "Verifikations-Backtest fehlgeschlagen: " + task.getException().getMessage());
            });
        });

        Thread th = new Thread(task);
        th.setDaemon(true);
        th.start();
    }

    public BorderPane getView() {
        return root;
    }

    public List<com.backtester.report.SensitivityResult> getSensitivityResults() {
        return sensitivityTable != null ? new java.util.ArrayList<>(sensitivityTable.getItems()) : new java.util.ArrayList<>();
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
