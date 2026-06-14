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
import java.util.Optional;

public class RobustnessView {

    private final BorderPane root;
    private final LogView logView;
    private final AppConfig config;
    private final OptimizationView optimizationView;

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
    private ComboBox<String> optModeCombo;

    // Selection mode radio buttons
    private RadioButton singleEaRadio;
    private RadioButton selectedTabRadio;

    // Parameter Table
    private TableView<EaParameter> paramTable;
    private final EaParameterManager eaParamManager = new EaParameterManager();
    private RobustnessRunner currentRunner;
    private Task<Void> currentTask;
    private String currentlyOptimizingParamName = null;
    
    // Controls
    private Button startBtn;
    private Button cancelBtn;
    private ProgressBar progress;
    private Label progressLabel;
    private final java.util.Set<String> flatParameters = new java.util.HashSet<>();

    public RobustnessView(LogView logView, OptimizationView optimizationView) {
        this.logView = logView;
        this.optimizationView = optimizationView;
        this.config = AppConfig.getInstance();

        root = new BorderPane();
        root.setPadding(new Insets(15));

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

        SplitPane mainLayout = new SplitPane(topBox, resultsBox);
        mainLayout.setOrientation(javafx.geometry.Orientation.VERTICAL);
        mainLayout.setDividerPositions(0.45);
        mainLayout.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        mainLayout.getStyleClass().add("transparent-split-pane");

        // Bottom: Controls
        HBox controlBox = createControlBox();

        root.setCenter(mainLayout);
        root.setBottom(controlBox);

        loadPreferences();
        expertField.textProperty().addListener((obs, oldVal, newVal) -> loadParameters());
        loadResultsFromDb();

        symbolCombo.valueProperty().addListener((obs, oldVal, newVal) -> loadParameters());
        periodCombo.valueProperty().addListener((obs, oldVal, newVal) -> loadParameters());
    }

    private VBox createConfigBox() {
        VBox box = new VBox(15);
        box.getStyleClass().add("sci-fi-panel");

        Label title = new Label("Scan Settings");
        title.getStyleClass().add("sci-fi-panel-title");
        
        String overview = "Der Robustness Scanner ist die ultimative Feuerprobe für jede Trading-Strategie. Bevor du echtes Geld auf ein System setzt, musst du dir zu 100% sicher sein, dass die Ergebnisse des Backtests oder der Optimierung nicht nur reines Glück oder eine Überanpassung (Curve-Fitting) an historische Daten waren.\n\n" +
                          "Das Grundprinzip der Robustheits-Prüfung beruht auf der Simulation von Markt-Unvollkommenheiten. Märkte wiederholen sich nie exakt. Was passiert mit deiner Strategie, wenn sich die Volatilität ändert, wenn Spreads breiter werden, Slippage auftritt, oder wenn Einstiegssignale zufällig ein paar Pips später erfolgen? \n\n" +
                          "Der Robustness Scanner führt genau solche Stresstests durch. Er nimmt deine vermeintlich perfekten Parameter und manipuliert sie systematisch oder fügt künstliches Rauschen hinzu (Monte Carlo Simulationen, Parameter-Shifting). Wenn deine Strategie nach diesen 'Sabotage-Akten' zusammenbricht und massive Verluste einfährt, ist sie wertlos für den Live-Handel. Überlebt sie den Stresstest und bleibt profitabel, hast du eine hochrobuste Strategie gefunden.";
        String details = "Erweiterte Erklärung der Stresstest-Methodiken:\n\n" +
                         "1. Selection Mode (Datenquelle):\n" +
                         "   - 'Current Input Parameters': Testet exakt die Parameter, die momentan in den EA geladen sind. Ideal für einen schnellen Check vor dem Live-Gang.\n" +
                         "   - 'From Best Optimization Results': Ein Workflow-Booster. Du kannst die Top-10 Ergebnisse aus dem Optimizer-Tab direkt hierher importieren und sie alle nacheinander stresse-testen lassen. So filterst du die 'Glückstreffer' aus der Optimierung heraus.\n\n" +
                         "2. Parameter Shifting (Verschiebung):\n" +
                         "   Dies ist das Herzstück des Scanners. Du wählst Kernparameter deiner Strategie (z.B. StopLoss, TrailingStop, Indikator-Schwellenwerte) aus. Das System wird nun dutzende Backtests durchführen, wobei es diese Parameter bei jedem Durchlauf zufällig um einen bestimmten Prozentsatz nach oben oder unten abweichen lässt (Shift).\n" +
                         "   - Warum? Weil eine gute Strategie nicht von einer magischen Zahl abhängen darf. Wenn ein StopLoss von 50 Pips extrem profitabel ist, ein StopLoss von 48 oder 52 Pips aber das Konto vernichtet, ist das System instabil und wird live scheitern.\n\n" +
                         "3. Shifts Configuration (Durchläufe):\n" +
                         "   - Shifts (Anzahl): Bestimmt, wie viele mutierte Backtests generiert werden sollen. Je mehr, desto statistisch signifikanter das Ergebnis.\n" +
                         "   - Shift Days/Period: Erlaubt es, den Start- oder Endzeitpunkt des historischen Tests leicht zu verschieben, um zu prüfen, ob die Strategie auch dann funktioniert, wenn sie an einem ungünstigen Tag gestartet wird (Market Timing Robustness).\n\n" +
                         "4. Interpretation der Ergebnisse (Results Tabs):\n" +
                         "   Nach Abschluss des Scans präsentiert dir das System eine Verteilung der Ergebnisse.\n" +
                         "   - Du wirst sehen, wie stark der Profit und der Drawdown unter Stress schwanken.\n" +
                         "   - Achte auf die Degradation: Es ist völlig normal, dass der Profit bei manipulierten Parametern sinkt. Wichtig ist, DASS er im positiven Bereich bleibt. Fällt die Mehrheit der Stress-Durchläufe in den negativen Bereich (Verlust), gilt die Strategie als nicht robust und das Risiko für einen Einsatz auf einem Live-Konto ist extrem hoch.";
                         
        javafx.scene.layout.Region infoSpacer = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(infoSpacer, javafx.scene.layout.Priority.ALWAYS);
        javafx.scene.layout.HBox titleBox = new javafx.scene.layout.HBox(15, title, infoSpacer, DocHelper.createInfoButton("Robustness Scanner", overview, details));
        titleBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        ToggleGroup modeGroup = new ToggleGroup();
        singleEaRadio = new RadioButton("Single Expert Advisor");
        singleEaRadio.setToggleGroup(modeGroup);
        singleEaRadio.setSelected(true);
        singleEaRadio.setStyle("-fx-text-fill: white;");
        
        selectedTabRadio = new RadioButton("Use all strategies in Selected tab");
        selectedTabRadio.setToggleGroup(modeGroup);
        selectedTabRadio.setStyle("-fx-text-fill: white;");
        
        VBox modeBox = new VBox(8, singleEaRadio, selectedTabRadio);
        modeBox.setPadding(new Insets(5, 0, 15, 0));

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

        modeGroup.selectedToggleProperty().addListener((obs, oldV, newV) -> {
            boolean useSelected = selectedTabRadio.isSelected();
            eaBox.setDisable(useSelected);
        });

        // Row 1: Symbol & Period
        grid.add(new Label("Symbol:"), 0, 1);
        symbolCombo = new ComboBox<>(FXCollections.observableArrayList(com.backtester.engine.BacktestConfig.SYMBOLS));
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

        // Row 6: Opt. Mode
        grid.add(new Label("Opt. Mode:"), 0, 6);
        optModeCombo = new ComboBox<>(FXCollections.observableArrayList(OptimizationConfig.OPTIMIZATION_MODES));
        optModeCombo.getStyleClass().add("combo-box");
        grid.add(optModeCombo, 1, 6);

        box.getChildren().addAll(titleBox, modeBox, grid);
        return box;
    }

    private TableView<com.backtester.report.OptimizationResult.CombinedPass> selectedTable;
    private ListView<String> resultsList;

    private VBox createParamBox() {
        VBox box = new VBox(10);
        box.getStyleClass().add("sci-fi-panel");

        Label title = new Label("EA Parameters (Target Set)");
        title.getStyleClass().add("sci-fi-panel-title");

        paramTable = new TableView<>();
        paramTable.setStyle("-fx-background-color: transparent;");
        paramTable.setEditable(true);
        paramTable.setRowFactory(tv -> new TableRow<EaParameter>() {
            @Override
            protected void updateItem(EaParameter item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    getStyleClass().removeAll("opt-highlighted", "currently-optimizing", "flat-parameter");
                } else {
                    if (item.isOptimizeEnabled()) {
                        if (!getStyleClass().contains("opt-highlighted")) {
                            getStyleClass().add("opt-highlighted");
                        }
                    } else {
                        getStyleClass().remove("opt-highlighted");
                    }
                    
                    if (item.getName().equals(currentlyOptimizingParamName)) {
                        if (!getStyleClass().contains("currently-optimizing")) {
                            getStyleClass().add("currently-optimizing");
                        }
                    } else {
                        getStyleClass().remove("currently-optimizing");
                    }

                    if (flatParameters.contains(item.getName())) {
                        if (!getStyleClass().contains("flat-parameter")) {
                            getStyleClass().add("flat-parameter");
                        }
                    } else {
                        getStyleClass().remove("flat-parameter");
                    }
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
        valCol.setOnEditCommit(e -> {
            e.getRowValue().setValue(e.getNewValue());
            saveParametersOnDemand();
        });
        valCol.setPrefWidth(100);
        
        TableColumn<EaParameter, String> startCol = new TableColumn<>("Start");
        startCol.setCellValueFactory(new PropertyValueFactory<>("optimizeStart"));
        startCol.setCellFactory(EnumAwareParamCell.forTableColumn());
        startCol.setOnEditCommit(e -> {
            e.getRowValue().setOptimizeStart(e.getNewValue());
            saveParametersOnDemand();
        });
        
        TableColumn<EaParameter, String> stepCol = new TableColumn<>("Step");
        stepCol.setCellValueFactory(new PropertyValueFactory<>("optimizeStep"));
        stepCol.setCellFactory(EnumAwareParamCell.forTableColumn());
        stepCol.setOnEditCommit(e -> {
            e.getRowValue().setOptimizeStep(e.getNewValue());
            saveParametersOnDemand();
        });
        
        TableColumn<EaParameter, String> stopCol = new TableColumn<>("Stop");
        stopCol.setCellValueFactory(new PropertyValueFactory<>("optimizeEnd"));
        stopCol.setCellFactory(EnumAwareParamCell.forTableColumn());
        stopCol.setOnEditCommit(e -> {
            e.getRowValue().setOptimizeEnd(e.getNewValue());
            saveParametersOnDemand();
        });
        
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
        
        Button loadBtn = new Button("Load .set");
        loadBtn.setOnAction(e -> loadFromFile());
        
        Button saveBtn = new Button("Save .set");
        saveBtn.setOnAction(e -> saveToFile());
        
        btnBox.getChildren().addAll(genConfigBtn, autoConfigBtn, loadBtn, saveBtn);

        box.getChildren().addAll(title, paramTable, btnBox);
        return box;
    }

    private VBox createResultsBox() {
        VBox box = new VBox(10);
        box.getStyleClass().add("sci-fi-panel");

        Label title = new Label("Robustness Results");
        title.getStyleClass().add("sci-fi-panel-title");

        TabPane tabPane = new TabPane();
        tabPane.setStyle("-fx-background-color: transparent;");

        // --- Tab 1: Selected Strategies ---
        Tab selectedTab = new Tab("Selected Strategies");
        selectedTab.setClosable(false);
        VBox selBox = new VBox(10);
        selBox.setPadding(new Insets(10));
        
        selectedTable = new TableView<>();
        if (optimizationView != null) {
            selectedTable.setItems(optimizationView.getSelectedStrategies());
        }
        
        TableColumn<com.backtester.report.OptimizationResult.CombinedPass, String> nameCol2 = new TableColumn<>("Name");
        nameCol2.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol2.setPrefWidth(100);
        
        TableColumn<com.backtester.report.OptimizationResult.CombinedPass, String> scoreCol = new TableColumn<>();
        HBox scoreHeader = new HBox(4);
        scoreHeader.setAlignment(Pos.CENTER_LEFT);
        Label scoreLabel = new Label("Score");
        scoreLabel.setTooltip(new Tooltip("Gesamt-Score (0-100) bewertet nur Endkennzahlen; betrachtet NICHT den Verlauf der Kennlinie (Equity-Kurve)"));
        Button infoBtn = DocHelper.createSmallInfoButton("Klicken für detaillierte Erklärung des Scores", () -> {
            DocHelper.showScoreDocDialog(scoreLabel.getScene() != null ? scoreLabel.getScene().getWindow() : null);
        });
        scoreHeader.getChildren().addAll(scoreLabel, infoBtn);
        scoreCol.setGraphic(scoreHeader);
        scoreCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(String.format(java.util.Locale.US, "%.2f", c.getValue().getScore())));
        scoreCol.setPrefWidth(95);
        
        TableColumn<com.backtester.report.OptimizationResult.CombinedPass, String> profitCol = new TableColumn<>("BT Profit");
        profitCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(String.format("%.2f", c.getValue().getBtProfit())));
        
        selectedTable.getColumns().addAll(nameCol2, scoreCol, profitCol);
        VBox.setVgrow(selectedTable, Priority.ALWAYS);
        selBox.getChildren().add(selectedTable);
        selectedTab.setContent(selBox);

        // --- Tab 2: Results Summary ---
        Tab resultsTab = new Tab("Results");
        resultsTab.setClosable(false);
        VBox resBox = new VBox(10);
        resBox.setPadding(new Insets(10));
        resultsList = new ListView<>();
        VBox.setVgrow(resultsList, Priority.ALWAYS);
        resBox.getChildren().add(resultsList);
        resultsTab.setContent(resBox);

        tabPane.getTabs().addAll(selectedTab, resultsTab);
        
        box.getChildren().addAll(title, tabPane);
        VBox.setVgrow(tabPane, Priority.ALWAYS);
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

        progressLabel = new Label("Ready");
        progressLabel.setStyle("-fx-text-fill: #e6e9f0; -fx-font-family: 'Consolas'; -fx-font-size: 13px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button dbStoreBtn = new Button("Store set in DB");
        dbStoreBtn.getStyleClass().add("button");
        
        Button dbGetBtn = new Button("Get set from DB");
        dbGetBtn.getStyleClass().add("button");

        box.getChildren().addAll(startBtn, cancelBtn, progress, progressLabel, spacer, dbStoreBtn, dbGetBtn);
        return box;
    }

    private void browseEA() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Expert Advisor");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("MetaTrader EA", "*.ex5", "*.ex4"));
        
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
        
        File selected = chooser.showOpenDialog(expertField.getScene().getWindow());
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
        flatParameters.clear();
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
                    eaParamManager.applyTranslations(expert, params);
                    paramTable.getItems().setAll(params);
                    logView.log("INFO", "Loaded parameters for " + EaParameterManager.extractEaBaseName(expert) + " [" + symbol + ", " + period + "] from DB");
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

        String optModeStr = config.get("robustness.optimization.mode", "Slow Complete Algorithm");
        if (optModeStr.contains("Slow") || optModeStr.contains("Complete")) {
            optModeCombo.getSelectionModel().select(0); // Slow Complete (index 0)
        } else if (optModeStr.contains("Genetic") || optModeStr.contains("Fast")) {
            optModeCombo.getSelectionModel().select(1); // Fast Genetic (index 1)
        } else {
            optModeCombo.setValue(optModeStr);
        }
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
        if (optModeCombo.getValue() != null) config.set("robustness.optimization.mode", optModeCombo.getValue());
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
        saveParametersOnDemand();
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
                String expertPath = expertField.getText().trim();
                java.util.List<com.backtester.config.EaParameter> merged = eaParamManager.getEffectiveParameters(expertPath, params);
                eaParamManager.applyTranslations(expertPath, merged);
                paramTable.getItems().setAll(merged);
                logView.log("INFO", "Loaded parameters from " + file.getName());
                saveParametersOnDemand();
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

    private void startScan() {
        try {
            savePreferences();
            
            String expert = expertField.getText().trim();
            if (selectedTabRadio.isSelected() && optimizationView != null) {
                expert = optimizationView.getExpertName();
                // Also update the local field so the user sees it (even if disabled)
                if (!expert.isEmpty()) {
                    expertField.setText(expert);
                }
            }
            
            if (expert.isEmpty()) {
                logView.log("WARN", "Please select an Expert Advisor.");
                return;
            }

            if (paramTable != null && !paramTable.getItems().isEmpty()) {
                String symbol = symbolCombo.getValue() != null ? symbolCombo.getValue() : "EURUSD";
                String period = periodCombo.getValue() != null ? periodCombo.getValue() : "H1";
                com.backtester.database.DatabaseManager.getInstance().saveEaParameterSettings(expert, symbol, period, new com.google.gson.Gson().toJson(paramTable.getItems()));
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
            int selectedIdx = optModeCombo.getSelectionModel().getSelectedIndex();
            int optMode = (selectedIdx == 0) ? 1 : 2; // 0 -> Complete (1), 1 -> Genetic (2)
            optConfig.setOptimizationMode(optMode);

            java.util.List<EaParameter> params = new java.util.ArrayList<>(paramTable.getItems());
            if (params.isEmpty()) {
                logView.log("WARN", "No parameters to sweep.");
                return;
            }

            java.util.List<com.backtester.report.OptimizationResult.CombinedPass> stratsToRun = new java.util.ArrayList<>();
            if (selectedTabRadio.isSelected()) {
                if (optimizationView != null && !optimizationView.getSelectedStrategies().isEmpty()) {
                    stratsToRun.addAll(optimizationView.getSelectedStrategies());
                } else {
                    logView.log("WARN", "No strategies selected in 'Selected' tab.");
                    return;
                }
                
                if (stratsToRun.size() > 10) {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Warning: Many Strategies");
                    alert.setHeaderText("Opening HTML reports for " + stratsToRun.size() + " strategies.");
                    alert.setContentText("Möchtest du wirklich mehr als 10 Browser-Fenster aufhaben?");
                    Optional<ButtonType> res = alert.showAndWait();
                    if (!res.isPresent() || res.get() != ButtonType.OK) {
                        return;
                    }
                }
            } else {
                stratsToRun.add(null); // Single EA marker
            }

            setUIState(true);
            progress.setProgress(-1); // Indeterminate
            progressLabel.setText("Initializing...");
            flatParameters.clear();
            resultsList.getItems().clear();

            String targetMetric = metricCombo.getValue() != null ? metricCombo.getValue() : "Profit";
            int shifts = shiftsSpinner.getValue() != null ? shiftsSpinner.getValue() : 10;
            int shiftDays = shiftDaysSpinner.getValue() != null ? shiftDaysSpinner.getValue() : 90;

            currentTask = new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    for (int i = 0; i < stratsToRun.size(); i++) {
                        if (isCancelled()) break;
                        
                        com.backtester.report.OptimizationResult.CombinedPass strat = stratsToRun.get(i);
                        String runName = strat != null ? strat.getName() : "SingleEA";
                        Platform.runLater(() -> logView.log("ROBUST", "Starting scan for: " + runName));
                        
                        // Prepare parameters for this specific strategy
                        java.util.List<EaParameter> runParams = new java.util.ArrayList<>();
                        for (EaParameter p : params) {
                            EaParameter copy = new EaParameter();
                            copy.setName(p.getName());
                            copy.setValue(p.getValue());
                            copy.setOptimizeStart(p.getOptimizeStart());
                            copy.setOptimizeStep(p.getOptimizeStep());
                            copy.setOptimizeEnd(p.getOptimizeEnd());
                            copy.setOptimizeEnabled(p.isOptimizeEnabled());
                            copy.setStringType(p.isStringType());
                            runParams.add(copy);
                        }
                        
                        // Modify runParams if strat != null
                        if (strat != null && strat.getBacktestPass() != null) {
                            for (EaParameter p : runParams) {
                                String stratVal = strat.getBacktestPass().getParameter(p.getName());
                                if (stratVal != null && !stratVal.isEmpty()) {
                                    p.setValue(stratVal);
                                }
                            }
                        } else if (strat != null) {
                            Platform.runLater(() -> logView.log("WARN", "Skipping strategy " + strat.getName() + " due to missing backtest parameters."));
                            continue;
                        }

                        final int stratIndex = i + 1;
                        final int totalStrats = stratsToRun.size();
                        currentRunner = new RobustnessRunner(config);
                        currentRunner.setLogCallback(msg -> logView.log("ROBUST", msg));
                        currentRunner.setProgressCallback(percent -> {
                            Platform.runLater(() -> progress.setProgress(percent / 100.0));
                        });
                        currentRunner.setProgressCountCallback((current, total) -> {
                            Platform.runLater(() -> {
                                int percent = total > 0 ? (int) (((double) current / total) * 100) : 0;
                                progress.setProgress((double) current / total);
                                String prefix = totalStrats > 1 ? String.format("[%d/%d] ", stratIndex, totalStrats) : "";
                                progressLabel.setText(String.format("%sProgress: %d / %d Backtests (%d%%)", prefix, current, total, percent));
                            });
                        });
                        currentRunner.setCurrentParamCallback(paramName -> {
                            Platform.runLater(() -> {
                                currentlyOptimizingParamName = paramName;
                                paramTable.refresh();
                                for (EaParameter p : paramTable.getItems()) {
                                    if (p.getName().equals(paramName)) {
                                        paramTable.scrollTo(p);
                                        break;
                                    }
                                }
                            });
                        });
                        
                        com.backtester.report.RobustnessResult res = currentRunner.runRobustnessScan(optConfig, runParams, shifts, shiftDays);
                        Platform.runLater(() -> handleSingleScanResult(res, optConfig, targetMetric, runParams, shifts, shiftDays, runName));
                    }
                    
                    Platform.runLater(() -> {
                        currentlyOptimizingParamName = null;
                        paramTable.refresh();
                        setUIState(false);
                        progress.setProgress(1.0);
                        progressLabel.setText("Scan completed.");
                        logView.log("INFO", "All robustness scans completed.");
                    });
                    return null;
                }
            };

            currentTask.setOnFailed(e -> {
                currentlyOptimizingParamName = null;
                paramTable.refresh();
                Throwable ex = currentTask.getException();
                logView.log("ERROR", "Robustness Scan failed: " + (ex != null ? ex.getMessage() : "Unknown Error"));
                if (ex != null) ex.printStackTrace();
                setUIState(false);
                progress.setProgress(0);
                progressLabel.setText("Scan failed.");
            });

            Thread t = new Thread(currentTask);
            t.setDaemon(true);
            t.start();
        } catch (Exception ex) {
            logView.log("ERROR", "Error in startScan: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void handleSingleScanResult(com.backtester.report.RobustnessResult res, OptimizationConfig optConfig, String targetMetric, java.util.List<EaParameter> params, int shifts, int shiftDays, String runName) {
        if (res != null && res.isSuccess()) {
            resultsList.getItems().add("SUCCESS: " + runName + " - " + res.getMessage());
            
            // Detect flat parameters (where all optimization passes yielded identical profit values)
            try {
                for (java.util.Map.Entry<String, java.util.Map<String, com.backtester.report.OptimizationResult>> sweepEntry : res.getParameterSweeps().entrySet()) {
                    String paramName = sweepEntry.getKey();
                    java.util.Map<String, com.backtester.report.OptimizationResult> periodMap = sweepEntry.getValue();
                    if (periodMap == null || periodMap.isEmpty()) continue;
                    
                    boolean allPeriodsFlat = true;
                    int validPeriods = 0;
                    for (com.backtester.report.OptimizationResult optRes : periodMap.values()) {
                        if (optRes == null || optRes.getPasses() == null || optRes.getPasses().isEmpty()) continue;
                        validPeriods++;
                        
                        if (optRes.getPasses().size() < 2) {
                            continue;
                        }
                        double firstProfit = optRes.getPasses().get(0).getProfit();
                        boolean periodFlat = true;
                        for (com.backtester.report.OptimizationResult.Pass pass : optRes.getPasses()) {
                            if (Math.abs(pass.getProfit() - firstProfit) > 0.00001) {
                                periodFlat = false;
                                break;
                            }
                        }
                        if (!periodFlat) {
                            allPeriodsFlat = false;
                            break;
                        }
                    }
                    if (allPeriodsFlat && validPeriods > 0) {
                        flatParameters.add(paramName);
                    }
                }
                paramTable.refresh();
            } catch (Exception ex) {
                logView.log("ERROR", "Error detecting flat parameters: " + ex.getMessage());
            }

            try {
                String reportTitle = targetMetric + " (Strategy " + runName + ")";
                com.backtester.report.RobustnessHtmlGenerator.generateReport(res, optConfig, targetMetric, reportTitle, params);
                
                // Rename the generated report to include the strategy name so they don't overwrite if in the same dir
                java.nio.file.Path oldPath = java.nio.file.Paths.get(res.getOutputDirectory(), "robustness_report.html");
                java.nio.file.Path newPath = java.nio.file.Paths.get(res.getOutputDirectory(), "robustness_report_" + runName.replace("+", "_") + ".html");
                if (java.nio.file.Files.exists(oldPath)) {
                    java.nio.file.Files.move(oldPath, newPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } else {
                    newPath = oldPath; // Fallback
                }
                
                com.google.gson.JsonObject metrics = new com.google.gson.JsonObject();
                metrics.addProperty("targetMetric", targetMetric);
                metrics.addProperty("shifts", shifts);
                metrics.addProperty("shiftDays", shiftDays);
                metrics.addProperty("strategyName", runName);
                
                com.backtester.database.DatabaseManager.getInstance().saveRun(
                    "ROBUSTNESS", 
                    optConfig.getExpert(), 
                    System.currentTimeMillis(), 
                    metrics.toString(), 
                    newPath.toAbsolutePath().toString()
                );
                
                java.awt.Desktop.getDesktop().open(newPath.toFile());
            } catch (Exception ex) {
                logView.log("ERROR", "Failed to save robustness to DB for " + runName + ": " + ex.getMessage());
            }
        } else if (res != null) {
            resultsList.getItems().add("FAILED: " + runName + " - " + res.getMessage());
            logView.log("ERROR", "Robustness scan failed for " + runName + ": " + res.getMessage());
        }
    }

    private void cancelScan() {
        currentlyOptimizingParamName = null;
        paramTable.refresh();
        if (currentRunner != null) currentRunner.cancel();
        if (currentTask != null) currentTask.cancel(true);
        setUIState(false);
        progress.setProgress(0.0);
        progressLabel.setText("Scan cancelled.");
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

    private void loadResultsFromDb() {
        try {
            java.util.List<com.backtester.database.HistoryRun> runs =
                com.backtester.database.DatabaseManager.getInstance().getRunsByType("ROBUSTNESS");
            if (runs.isEmpty()) return;

            int loaded = 0;
            for (com.backtester.database.HistoryRun run : runs) {
                try {
                    String label = "";
                    if (run.getResultJson() != null && !run.getResultJson().isEmpty()) {
                        com.google.gson.JsonObject json = new com.google.gson.Gson().fromJson(run.getResultJson(), com.google.gson.JsonObject.class);
                        String stratName = json.has("strategyName") ? json.get("strategyName").getAsString() : "";
                        String metric = json.has("targetMetric") ? json.get("targetMetric").getAsString() : "";
                        label = "SUCCESS: " + (stratName.isEmpty() ? run.getExpertName() : stratName) + " - " + metric;
                    } else {
                        label = "SUCCESS: " + run.getExpertName();
                    }
                    resultsList.getItems().add(label);
                    loaded++;
                } catch (Exception ex) {
                    // Skip invalid entries
                }
            }
            if (loaded > 0) {
                logView.log("INFO", "Loaded " + loaded + " robustness results from database.");
            }
        } catch (Exception ex) {
            logView.log("ERROR", "Failed to load robustness results from DB: " + ex.getMessage());
        }
    }

    public void bindTab(Tab tab) {
        updateTabTitle(tab, resultsList.getItems().size());
        resultsList.getItems().addListener((javafx.collections.ListChangeListener<String>) c -> {
            updateTabTitle(tab, resultsList.getItems().size());
        });
    }

    private void updateTabTitle(Tab tab, int count) {
        javafx.application.Platform.runLater(() -> tab.setText("Robustness (" + count + ")"));
    }

    public BorderPane getView() {
        return root;
    }

    private void saveParametersOnDemand() {
        String expert = expertField.getText().trim();
        if (expert.isEmpty()) return;
        if (paramTable != null && !paramTable.getItems().isEmpty()) {
            String symbol = symbolCombo.getValue() != null ? symbolCombo.getValue() : "EURUSD";
            String period = periodCombo.getValue() != null ? periodCombo.getValue() : "H1";
            com.backtester.database.DatabaseManager.getInstance().saveEaParameterSettings(expert, symbol, period, new com.google.gson.Gson().toJson(paramTable.getItems()));
        }
    }
}
