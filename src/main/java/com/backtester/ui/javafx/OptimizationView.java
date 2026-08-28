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
import javafx.animation.PauseTransition;
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
    private OptimizationKiPanel kiPanel;
    private OptimizationCombinedPanel combinedPanel;
    private final PauseTransition parameterLoadDebounce =
            new PauseTransition(javafx.util.Duration.millis(300));
    private final java.util.concurrent.atomic.AtomicBoolean sensitivityRefreshPending =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    // Search and Master List for Selected tab
    private final javafx.collections.ObservableList<CombinedPass> masterSelectedList = javafx.collections.FXCollections.observableArrayList();
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
        expertField.textProperty().addListener((obs, oldVal, newVal) -> scheduleParameterLoad());

        symbolCombo.valueProperty().addListener((obs, oldVal, newVal) -> scheduleParameterLoad());
        periodCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) applyChartPeriod(newVal);
            scheduleParameterLoad();
        });
        // loadPreferences() runs before the listeners are installed. Load once after
        // symbol and period have reached their final persisted values.
        scheduleParameterLoad();

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
        EaParameterTableHelper.configureTable(paramTable, optCol, nameCol, valCol, startCol, stepCol, stopCol, this::saveParametersOnDemand);

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

        combinedPanel = new OptimizationCombinedPanel(createCombinedHost());
        Tab combinedTab = new Tab("🏆 Combined Analysis", combinedPanel.createPane());
        combinedTab.getStyleClass().add("tab");
        combinedTab.setClosable(false);
        combinedTable = combinedPanel.getTable();

        selectedTab = new Tab("⭐ Selected", createSelectedPane());
        selectedTab.getStyleClass().add("tab");
        selectedTab.setClosable(false);

        Tab sensitivityTab = new Tab("⚖ Sensitivity Analysis", createSensitivityPane());
        sensitivityTab.getStyleClass().add("tab");
        sensitivityTab.setClosable(false);

        kiPanel = new OptimizationKiPanel(createKiHost());
        kiAnalysisTab = new Tab("🤖 KI Analysis", kiPanel.createPane());
        kiAnalysisTab.getStyleClass().add("tab");
        kiAnalysisTab.setClosable(false);

        resultTabs.getTabs().addAll(mainTab, forwardTab, combinedTab, selectedTab, sensitivityTab, kiAnalysisTab);
        bindTabCounter(mainTab, "Main Optimization", resultTable);
        bindTabCounter(forwardTab, "Forward Results", forwardTable);
        bindTabCounter(combinedTab, "🏆 Combined Analysis", combinedTable);
        bindTabCounter(selectedTab, "⭐ Selected", selectedTable);
        bindTabCounter(sensitivityTab, "⚖ Sensitivity Analysis", sensitivityTable);
        bindTabCounter(kiAnalysisTab, "🤖 KI Analysis", kiPanel.getReportsTable());
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
        llmAnalyzeBtn.setOnAction(e -> kiPanel.runAnalysis(llmAnalyzeBtn));
        llmAnalyzeBtn.setVisible(false);
        llmAnalyzeBtn.setManaged(false);
        kiPanel.updateAnalyzeButtonState(false);

        Button llmSettingsBtn = new Button("\u2699 KI-Einstellungen");
        llmSettingsBtn.getStyleClass().addAll("button");
        llmSettingsBtn.setStyle("-fx-background-color: #374151; -fx-text-fill: #d1d5db; -fx-font-weight: bold;");
        llmSettingsBtn.setOnAction(e -> kiPanel.showSettings());
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
        // DB-Read + Gson-Deserialisierung des kompletten States laufen im
        // Hintergrund (wird beim Start ausgeführt); die Tabellenbefüllung
        // läuft in succeeded auf dem FX-Thread.
        record LoadedState(OptimizationResult optResult, List<Integer> selectedPassIds,
                           List<com.backtester.report.SensitivityResult> senResults) {}

        Task<LoadedState> loadTask = new Task<>() {
            @Override
            protected LoadedState call() throws Exception {
                String[] state = com.backtester.database.DatabaseManager.getInstance().getOptimizationState();
                if (state == null) return null;

                Gson gson = buildGson();
                OptimizationResult opt = null;
                if (state[0] != null && !state[0].isEmpty()) {
                    opt = gson.fromJson(state[0], OptimizationResult.class);
                }
                List<Integer> selectedIds = null;
                if (state[1] != null && !state[1].isEmpty() && opt != null) {
                    Type listType = new TypeToken<List<Integer>>(){}.getType();
                    selectedIds = gson.fromJson(state[1], listType);
                }
                List<com.backtester.report.SensitivityResult> sen = null;
                if (state[2] != null && !state[2].isEmpty()) {
                    Type senListType = new TypeToken<List<com.backtester.report.SensitivityResult>>(){}.getType();
                    sen = gson.fromJson(state[2], senListType);
                }
                return new LoadedState(opt, selectedIds, sen);
            }
        };
        loadTask.setOnSucceeded(e -> {
            LoadedState loaded = loadTask.getValue();
            if (loaded == null) return;

            // 1. Apply lastOptResult
            if (loaded.optResult() != null) {
                lastOptResult = loaded.optResult();
                resultTable.setItems(FXCollections.observableArrayList(lastOptResult.getPasses()));
                if (lastOptResult.hasForwardResults()) {
                    forwardTable.setItems(FXCollections.observableArrayList(lastOptResult.getForwardPasses()));
                }
                applyCombinedFilter();

                // 2. Apply Selected Passes
                if (loaded.selectedPassIds() != null && !loaded.selectedPassIds().isEmpty()) {
                    List<CombinedPass> allCombined = lastOptResult.buildCombinedPasses(combinedPanel.isOnlyMatchedSelected(), OptimizationResult.ScoreWeights.defaults());
                    masterSelectedList.clear();
                    for (Integer id : loaded.selectedPassIds()) {
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

            // 3. Apply Sensitivity Results
            if (loaded.senResults() != null) {
                sensitivityTable.setItems(FXCollections.observableArrayList(loaded.senResults()));
                kiPanel.updateAnalyzeButtonState(!loaded.senResults().isEmpty());
            } else {
                kiPanel.updateAnalyzeButtonState(false);
            }

            logView.log("INFO", "Optimization state loaded from database.");
        });
        loadTask.setOnFailed(e ->
                logView.log("ERROR", "Failed to load optimization state: " + loadTask.getException().getMessage()));
        Thread th = new Thread(loadTask);
        th.setDaemon(true);
        th.start();
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
                kiPanel.updateAnalyzeButtonState(false);
            }
            else if (txt.contains("KI")) {
                com.backtester.database.DatabaseManager.getInstance().clearKiReports();
                kiPanel.refreshReports();
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
            combinedPanel.clearCountLabel();
            progressLabel.setText("0 / 0 Passes");
            kiPanel.updateAnalyzeButtonState(false);
            com.backtester.database.DatabaseManager.getInstance().clearOptimizationState();
            com.backtester.database.DatabaseManager.getInstance().clearKiReports();
            kiPanel.refreshReports();
            logView.log("INFO", "Alle Tabellen und KI-Historie gelöscht.");
        }
    }


    private OptimizationKiPanel.Host createKiHost() {
        return new OptimizationKiPanel.Host() {
            @Override public LogView logView() { return OptimizationView.this.logView; }
            @Override public javafx.stage.Window ownerWindow() {
                return root.getScene() != null ? root.getScene().getWindow() : null;
            }
            @Override public java.util.List<String> stylesheets() {
                if (root.getScene() == null) return java.util.Collections.emptyList();
                return root.getScene().getStylesheets();
            }
            @Override public TableView<com.backtester.report.SensitivityResult> sensitivityTable() {
                return OptimizationView.this.sensitivityTable;
            }
            @Override public TableView<CombinedPass> combinedTable() {
                return OptimizationView.this.combinedTable;
            }
            @Override public TableView<CombinedPass> selectedTable() {
                return OptimizationView.this.selectedTable;
            }
            @Override public TabPane resultTabs() { return OptimizationView.this.resultTabs; }
            @Override public Tab kiAnalysisTab() { return OptimizationView.this.kiAnalysisTab; }
            @Override public void saveStateToDb() { OptimizationView.this.saveStateToDb(); }
            @Override public String expertName() {
                return expertField.getText() != null && !expertField.getText().trim().isEmpty()
                        ? expertField.getText().trim() : "Unknown";
            }
            @Override public String symbol() {
                return symbolCombo.getValue() != null && !symbolCombo.getValue().isEmpty()
                        ? symbolCombo.getValue() : "Unknown";
            }
            @Override public String period() {
                return periodCombo.getValue() != null && !periodCombo.getValue().isEmpty()
                        ? periodCombo.getValue() : "Unknown";
            }
            @Override public Button llmAnalyzeBtn() { return OptimizationView.this.llmAnalyzeBtn; }
        };
    }

    private OptimizationCombinedPanel.Host createCombinedHost() {
        return new OptimizationCombinedPanel.Host() {
            @Override public LogView logView() { return OptimizationView.this.logView; }
            @Override public javafx.stage.Window ownerWindow() {
                return root.getScene() != null ? root.getScene().getWindow() : null;
            }
            @Override public OptimizationResult lastOptResult() { return OptimizationView.this.lastOptResult; }
            @Override public TableView<com.backtester.report.SensitivityResult> sensitivityTable() {
                return OptimizationView.this.sensitivityTable;
            }
            @Override public String fromDateFallback() {
                return fromDatePicker != null && fromDatePicker.getValue() != null
                        ? fromDatePicker.getValue().toString() : null;
            }
            @Override public String toDateFallback() {
                return toDatePicker != null && toDatePicker.getValue() != null
                        ? toDatePicker.getValue().toString() : null;
            }
            @Override public boolean addSelectedPass(CombinedPass pass) {
                return OptimizationView.this.addSelectedPass(pass);
            }
            @Override public OptimizationView parentView() { return OptimizationView.this; }
            @Override public void runVerificationBacktest(OptimizationResult.Pass pass) {
                OptimizationView.this.runVerificationBacktest(pass);
            }
        };
    }

    private void applyCombinedFilter() {
        if (combinedPanel != null) {
            combinedPanel.applyFilter();
        }
    }

    private java.util.Comparator<String> numericStringComparator() {
        return OptimizationCombinedPanel.numericStringComparator();
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

        selectedTable = combinedPanel.createTable();
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
            OptimizationScoreDocs.showCvExplanationDialog(
                    root.getScene() != null ? root.getScene().getWindow() : null,
                    "BT CV (worst) Erklärung",
                    "BT CV (worst) - Backtest Variationskoeffizient",
                    OptimizationScoreDocs.getBtCvExplanationHtml());
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
            OptimizationScoreDocs.showCvExplanationDialog(
                    root.getScene() != null ? root.getScene().getWindow() : null,
                    "FW CV (worst) Erklärung",
                    "FW CV (worst) - Forward Variationskoeffizient",
                    OptimizationScoreDocs.getFwCvExplanationHtml());
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
        currentSensitivityRunner.setResultUpdateCallback(res -> {
            // A sweep can publish hundreds of intermediate updates. Keep at most one
            // refresh queued and persist the complete state once after Task success.
            if (sensitivityRefreshPending.compareAndSet(false, true)) {
                Platform.runLater(() -> {
                    sensitivityRefreshPending.set(false);
                    sensitivityTable.refresh();
                });
            }
        });

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
                        kiPanel.updateAnalyzeButtonState(!sensitivityTable.getItems().isEmpty());
                    } else {
                        progressLabel.setText("Sensitivity Analysis completed.");
                        kiPanel.updateAnalyzeButtonState(true);
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
        }
    }

    private void scheduleParameterLoad() {
        parameterLoadDebounce.stop();
        parameterLoadDebounce.setOnFinished(event -> loadParameters());
        parameterLoadDebounce.playFromStart();
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

    /** Keeps the global context AND this tab's parameter table in sync with the selected period. */
    private void applyChartPeriod(String period) {
        if (period == null) return;
        EaParameterUiContext.setChartPeriod(period);
        paramTable.getProperties().put(EaParameterUiContext.CHART_PERIOD_TABLE_KEY, period.trim());
        paramTable.refresh();
    }

    private void loadPreferences() {
        com.backtester.config.AppConfig config = com.backtester.config.AppConfig.getInstance();
        String exp = config.get("optimization.expert", "");
        if (!exp.isEmpty()) {
            expertField.setText(exp);
        }

        symbolCombo.setValue(config.get("optimization.symbol", "EURUSD"));
        periodCombo.setValue(config.get("optimization.period", "H1"));
        applyChartPeriod(periodCombo.getValue());

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

        java.time.LocalDate today = java.time.LocalDate.now();
        String fromKey = "optimization.fromDate";
        String from = config.get(fromKey, "");
        java.time.LocalDate defaultFrom = today.minusYears(1);
        if (from.isEmpty()) {
            fromDatePicker.setValue(defaultFrom);
        } else {
            try {
                fromDatePicker.setValue(java.time.LocalDate.parse(from));
            } catch (java.time.format.DateTimeParseException ex) {
                log.warn("Invalid configuration value '{}' for key '{}'; using default {}.",
                        from, fromKey, defaultFrom, ex);
                fromDatePicker.setValue(defaultFrom);
            }
        }

        String toKey = "optimization.toDate";
        String to = config.get(toKey, "");
        if (to.isEmpty()) {
            toDatePicker.setValue(today);
        } else {
            try {
                toDatePicker.setValue(java.time.LocalDate.parse(to));
            } catch (java.time.format.DateTimeParseException ex) {
                log.warn("Invalid configuration value '{}' for key '{}'; using default {}.",
                        to, toKey, today, ex);
                toDatePicker.setValue(today);
            }
        }

        // Load weights and filters from database
        if (combinedPanel != null) {
            combinedPanel.loadFilterAndWeightPreferences();
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
            try {
                EaParameter.requireValidOptimizeSteps(paramTable.getItems());
            } catch (IllegalStateException ex) {
                new Alert(Alert.AlertType.ERROR, ex.getMessage()).show();
                return;
            }
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
            if (ex != null) log.error("Optimization task failed", ex);
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
                        combinedPanel.setOnlyMatchedSelected(false);
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
            log.error("Failed to update optimization result in the UI", t);
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

    private void updateTabTitle(Tab tab, int count) {
        javafx.application.Platform.runLater(() -> tab.setText("Optimizer (" + count + ")"));
    }

    public OptimizationResult getLastOptResult() {
        return lastOptResult;
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

        final CombinedPass combined = new CombinedPass(pass, null, 0.0, 0.0, "");

        // Parameter werden direkt in das Tester-Profilverzeichnis geschrieben —
        // das passiert zusammen mit der Preset-Auflösung im Hintergrund-Task unten.
        final String eaName = EaParameterManager.extractEaBaseName(expert);
        final String presetFileName = "Backtester_" + eaName + "_Verify_Pass" + pass.getPassNumber() + ".set";
        final java.nio.file.Path presetsDir = config.getTesterProfilesDir(expert);

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
                // Preset-Auflösung und Set-Datei-Schreibung sind Disk-IO und laufen
                // deshalb hier im Hintergrund — Reihenfolge: resolve → write → verify → run.
                com.backtester.report.PassPresetResolver.Resolution resolution =
                        com.backtester.report.PassPresetResolver.resolveForExecutionWithFallback(
                                combined, expert, eaParamManager.getEffectiveParameters(expert));
                List<EaParameter> allParams = resolution.parameters();
                if (allParams == null || allParams.isEmpty()) {
                    throw new java.io.IOException("Konnte Parameter für den Expert Advisor nicht laden.");
                }
                if (resolution.warning() != null) {
                    Platform.runLater(() -> new Alert(Alert.AlertType.WARNING, resolution.warning()).show());
                }

                // Parameter direkt in das Tester-Profilverzeichnis schreiben
                try {
                    java.nio.file.Files.createDirectories(presetsDir);
                    java.nio.file.Path destFile = presetsDir.resolve(presetFileName);
                    java.nio.file.Files.deleteIfExists(destFile);
                    eaParamManager.writeSetFile(destFile, allParams, expert);
                    com.backtester.workflow.MasterStrategyLineageService
                            .verifyPresetWritten(destFile, allParams);
                } catch (Exception ex) {
                    log.error("Failed to write verification preset", ex);
                    throw new java.io.IOException("Fehler beim Erstellen der Preset-Datei: " + ex.getMessage(), ex);
                }

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
