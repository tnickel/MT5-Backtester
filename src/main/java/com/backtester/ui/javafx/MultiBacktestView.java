package com.backtester.ui.javafx;

import com.backtester.config.AppConfig;
import com.backtester.config.Preset;
import com.backtester.config.PresetManager;
import com.backtester.engine.BacktestConfig;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.LocalDate;
import com.backtester.config.EaParameter;
import com.backtester.config.EaParameterManager;
import javafx.scene.control.cell.PropertyValueFactory;

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

    // Parameters Table
    private TableView<com.backtester.config.EaParameter> paramTable;
    private final com.backtester.config.EaParameterManager eaParamManager = new com.backtester.config.EaParameterManager();

    public static class BatchRun {
        private String name;
        private int dbId = -1;
        private java.util.List<com.backtester.report.BacktestResult> results = new java.util.ArrayList<>();
        private java.nio.file.Path htmlReportPath;

        public BatchRun(String name) { this.name = name; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getDbId() { return dbId; }
        public void setDbId(int dbId) { this.dbId = dbId; }
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

        // Bottom: Results
        VBox resultsBox = createResultsBox();

        splitPane.getItems().addAll(topBox, resultsBox);
        splitPane.setDividerPositions(0.45);

        root.setCenter(splitPane);

        loadPreferences();
        expertField.textProperty().addListener((obs, oldVal, newVal) -> loadParameters());
        loadBatchesFromDb();
    }

    private VBox createConfigBox() {
        VBox box = new VBox(15);
        box.getStyleClass().add("sci-fi-panel");

        Label title = new Label("Batch / Multi-Symbol Backtest Configuration");
        title.getStyleClass().add("sci-fi-panel-title");

        String overview = "Der Multi-Backtester Tab ist das ideale Werkzeug, um die Universalität und Skalierbarkeit eines Expert Advisors zu prüfen. Eine klassische Falle in der algorithmischen Entwicklung ist die Überanpassung (Curve-Fitting) an ein einziges Währungspaar, z.B. EURUSD. Eine Strategie, die auf EURUSD fantastisch funktioniert, könnte auf USDJPY verheerende Verluste einfahren, weil sie nicht auf universellen Marktprinzipien beruht.\n\n" +
                          "Warum nutzt man den Multi-Backtester? Um echtes Portfolio-Trading zu simulieren und zu validieren. Anstatt mühsam 20 einzelne Backtests manuell zu starten, die Parameter jedes Mal zu ändern und zu warten, erlaubt dir dieses Tool die Definition einer ganzen 'Batch-Queue' (Warteschlange). Du kannst den EA auf 15 verschiedenen Symbolen und 5 verschiedenen Zeitrahmen testen – mit einem einzigen Klick.\n\n" +
                          "Dieses Tool arbeitet die Liste nacheinander vollautomatisch im Hintergrund ab. So kannst du den Rechner über Nacht laufen lassen und am nächsten Morgen sofort sehen, auf welchen Assets und Timeframes deine Strategie einen statistischen Edge (Vorteil) besitzt und welche Märkte strikt gemieden werden sollten.";
        String details = "Erweiterte Funktionsübersicht:\n\n" +
                         "1. Grid-Auswahl (Symbole & Perioden):\n" +
                         "   Auf der linken Seite befindet sich eine umfangreiche Matrix. Hier markierst du per Checkbox alle gewünschten Instrumente (Majors, Minors, Exoten, Metalle) und die zugehörigen Timeframes. Jede markierte Kombination stellt einen individuellen Backtest-Job dar, der in die Ausführungswarteschlange aufgenommen wird.\n\n" +
                         "2. Globale Konfiguration:\n" +
                         "   Die Konfiguration (Deposit, Hebel, Datumsbereich) im oberen Bereich gilt global für alle ausgewählten Jobs. Dies gewährleistet, dass die Ergebnisse später absolut vergleichbar sind, da sie alle denselben historischen Zeitraum und dieselben Kontobedingungen durchlaufen haben.\n\n" +
                         "3. Sequentielle Batch-Ausführung:\n" +
                         "   Sobald du den Start-Button drückst, übernimmt die Engine die Kontrolle über den MetaTrader. Sie startet den ersten Test, wartet auf den Abschluss, liest die Ergebnisse aus, startet den zweiten Test, usw. Dies spart dem Nutzer unzählige Stunden an monotoner manueller Arbeit.\n\n" +
                         "4. Aggregierte Ergebnistabelle (Runs):\n" +
                         "   Während die Tests laufen, füllt sich die Tabelle auf der rechten Seite in Echtzeit. Diese Tabelle ist sortierbar. Du kannst nach Abschluss sofort auf 'Profit' oder 'Drawdown' klicken, um die lukrativsten oder sichersten Symbol-Timeframe-Kombinationen an die Spitze zu sortieren. So erkennst du sofort Muster (z.B. 'Die Strategie funktioniert nur auf M15, scheitert aber auf H1 völlig').\n\n" +
                         "5. Combined Portfolio Report:\n" +
                         "   Ein herausragendes Feature ist die Möglichkeit, einen kombinierten Report zu erstellen. Dabei werden die Einzelergebnisse aller erfolgreichen Backtests zu einer einzigen, aggregierten Portfolio-Equity-Kurve verschmolzen. Dies zeigt dir, wie sich dein Kapital entwickelt hätte, wenn du den EA auf all diesen Instrumenten gleichzeitig auf einem Live-Konto betrieben hättest, inklusive Überlappungen bei Drawdowns und Margin-Auslastungen.";

        javafx.scene.layout.Region infoSpacer = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(infoSpacer, javafx.scene.layout.Priority.ALWAYS);
        javafx.scene.layout.HBox titleBox = new javafx.scene.layout.HBox(15, title, infoSpacer, DocHelper.createInfoButton("Multi-Backtester", overview, details));
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

        // Row 4: Presets
        grid.add(new Label("Presets:"), 0, 4);
        ComboBox<Preset> presetCombo = new ComboBox<>();
        presetCombo.setPrefWidth(130);
        presetCombo.setPromptText("Preset wählen...");
        presetCombo.getItems().setAll(PresetManager.getInstance().getPresets());

        Button addPresetBtn = new Button("➕ Neu");
        addPresetBtn.getStyleClass().add("button");
        addPresetBtn.setMinWidth(Region.USE_PREF_SIZE);

        Button savePresetBtn = new Button("💾 Speichern");
        savePresetBtn.getStyleClass().add("button");
        savePresetBtn.setMinWidth(Region.USE_PREF_SIZE);

        Button editPresetBtn = new Button("✏ Ändern");
        editPresetBtn.getStyleClass().add("button");
        editPresetBtn.setMinWidth(Region.USE_PREF_SIZE);

        Button deletePresetBtn = new Button("🗑 Löschen");
        deletePresetBtn.getStyleClass().add("button");
        deletePresetBtn.setMinWidth(Region.USE_PREF_SIZE);

        HBox presetBox = new HBox(8, presetCombo, addPresetBtn, savePresetBtn, editPresetBtn, deletePresetBtn);
        presetBox.setAlignment(Pos.CENTER_LEFT);
        grid.add(presetBox, 1, 4, 3, 1);

        presetCombo.setOnShowing(evt -> {
            Preset currentSel = presetCombo.getValue();
            presetCombo.getItems().setAll(PresetManager.getInstance().getPresets());
            if (currentSel != null) {
                for (Preset p : presetCombo.getItems()) {
                    if (p.getName().equals(currentSel.getName())) {
                        presetCombo.setValue(p);
                        break;
                    }
                }
            }
        });

        presetCombo.setOnAction(evt -> {
            Preset sel = presetCombo.getValue();
            if (sel != null) {
                applyPreset(sel);
            }
        });

        savePresetBtn.setOnAction(evt -> {
            Preset sel = presetCombo.getValue();
            if (sel == null) {
                Alert alert = new Alert(Alert.AlertType.WARNING, "Bitte wählen Sie zuerst ein Preset aus, auf dem gespeichert werden soll.");
                if (expertField.getScene() != null) {
                    alert.initOwner(expertField.getScene().getWindow());
                }
                alert.show();
                return;
            }

            // Snapshot parameters
            java.util.List<com.backtester.config.EaParameter> currentParams = new java.util.ArrayList<>();
            if (paramTable != null && !paramTable.getItems().isEmpty()) {
                for (com.backtester.config.EaParameter p : paramTable.getItems()) {
                    com.backtester.config.EaParameter copy = new com.backtester.config.EaParameter();
                    copy.setName(p.getName());
                    copy.setValue(p.getValue());
                    copy.setDefaultValue(p.getDefaultValue() != null ? p.getDefaultValue() : p.getValue());
                    copy.setSection(p.getSection());
                    copy.setOptimizeStart(p.getOptimizeStart());
                    copy.setOptimizeStep(p.getOptimizeStep());
                    copy.setOptimizeEnd(p.getOptimizeEnd());
                    copy.setOptimizeEnabled(p.isOptimizeEnabled());
                    copy.setStringType(p.isStringType());
                    currentParams.add(copy);
                }
            }

            sel.setEaName(expertField.getText().trim());

            java.util.List<String> selectedSyms = new java.util.ArrayList<>();
            for (CheckBox cb : symbolBoxes) {
                if (cb.isSelected()) selectedSyms.add(cb.getText());
            }
            sel.setSymbols(String.join(",", selectedSyms));

            java.util.List<String> selectedTfs = new java.util.ArrayList<>();
            for (CheckBox cb : timeframeBoxes) {
                if (cb.isSelected()) selectedTfs.add(cb.getText());
            }
            sel.setPeriod(String.join(",", selectedTfs));
            sel.setEaParameters(currentParams);

            PresetManager.getInstance().savePresets();

            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Preset \"" + sel.getName() + "\" erfolgreich mit den aktuellen Parametern gespeichert.");
            if (expertField.getScene() != null) {
                alert.initOwner(expertField.getScene().getWindow());
            }
            alert.show();
        });

        addPresetBtn.setOnAction(evt -> {
            TextInputDialog inputDialog = new TextInputDialog("Set " + (PresetManager.getInstance().getPresets().size() + 1));
            inputDialog.setTitle("Neues Preset erstellen");
            inputDialog.setHeaderText("Preset-Namen eingeben");
            inputDialog.setContentText("Name:");

            inputDialog.getDialogPane().setStyle("-fx-background-color: #0b0d13;");
            inputDialog.getDialogPane().lookup(".content.label").setStyle("-fx-text-fill: #e6e9f0;");
            inputDialog.getDialogPane().lookup(".header-panel").setStyle("-fx-background-color: #0b0d13;");
            if (inputDialog.getDialogPane().lookup(".header-panel").lookup(".label") != null) {
                inputDialog.getDialogPane().lookup(".header-panel").lookup(".label").setStyle("-fx-text-fill: #00e5ff;");
            }
            if (expertField.getScene() != null) {
                inputDialog.initOwner(expertField.getScene().getWindow());
                if (!expertField.getScene().getStylesheets().isEmpty()) {
                    inputDialog.getDialogPane().getStylesheets().addAll(expertField.getScene().getStylesheets());
                }
            }

            java.util.Optional<String> result = inputDialog.showAndWait();
            if (result.isPresent() && !result.get().trim().isEmpty()) {
                String name = result.get().trim();
                String ea = expertField.getText().trim();

                java.util.List<String> selectedSyms = new java.util.ArrayList<>();
                for (CheckBox cb : symbolBoxes) {
                    if (cb.isSelected()) selectedSyms.add(cb.getText());
                }
                String syms = String.join(",", selectedSyms);

                java.util.List<String> selectedTfs = new java.util.ArrayList<>();
                for (CheckBox cb : timeframeBoxes) {
                    if (cb.isSelected()) selectedTfs.add(cb.getText());
                }
                String per = String.join(",", selectedTfs);

                // Snapshot parameters
                java.util.List<com.backtester.config.EaParameter> currentParams = new java.util.ArrayList<>();
                if (paramTable != null && !paramTable.getItems().isEmpty()) {
                    for (com.backtester.config.EaParameter p : paramTable.getItems()) {
                        com.backtester.config.EaParameter copy = new com.backtester.config.EaParameter();
                        copy.setName(p.getName());
                        copy.setValue(p.getValue());
                        copy.setDefaultValue(p.getDefaultValue() != null ? p.getDefaultValue() : p.getValue());
                        copy.setSection(p.getSection());
                        copy.setOptimizeStart(p.getOptimizeStart());
                        copy.setOptimizeStep(p.getOptimizeStep());
                        copy.setOptimizeEnd(p.getOptimizeEnd());
                        copy.setOptimizeEnabled(p.isOptimizeEnabled());
                        copy.setStringType(p.isStringType());
                        currentParams.add(copy);
                    }
                }

                Preset newPreset = new Preset(name, ea, syms, per, currentParams);
                PresetManager.getInstance().addPreset(newPreset);
                presetCombo.getItems().setAll(PresetManager.getInstance().getPresets());
                presetCombo.setValue(newPreset);
            }
        });

        editPresetBtn.setOnAction(evt -> {
            Preset sel = presetCombo.getValue();
            if (sel == null) {
                Alert alert = new Alert(Alert.AlertType.WARNING, "Bitte wählen Sie zuerst ein Preset aus, das geändert werden soll.");
                if (expertField.getScene() != null) {
                    alert.initOwner(expertField.getScene().getWindow());
                }
                alert.show();
                return;
            }

            TextInputDialog inputDialog = new TextInputDialog(sel.getName());
            inputDialog.setTitle("Preset bearbeiten");
            inputDialog.setHeaderText("Ggf. Namen anpassen. Die aktuellen Werte des Formulars\n(EA, Symbole, Periode) werden im Preset gespeichert.");
            inputDialog.setContentText("Name:");

            inputDialog.getDialogPane().setStyle("-fx-background-color: #0b0d13;");
            inputDialog.getDialogPane().lookup(".content.label").setStyle("-fx-text-fill: #e6e9f0;");
            inputDialog.getDialogPane().lookup(".header-panel").setStyle("-fx-background-color: #0b0d13;");
            if (inputDialog.getDialogPane().lookup(".header-panel").lookup(".label") != null) {
                inputDialog.getDialogPane().lookup(".header-panel").lookup(".label").setStyle("-fx-text-fill: #00e5ff;");
            }
            if (expertField.getScene() != null) {
                inputDialog.initOwner(expertField.getScene().getWindow());
                if (!expertField.getScene().getStylesheets().isEmpty()) {
                    inputDialog.getDialogPane().getStylesheets().addAll(expertField.getScene().getStylesheets());
                }
            }

            java.util.Optional<String> result = inputDialog.showAndWait();
            if (result.isPresent() && !result.get().trim().isEmpty()) {
                sel.setName(result.get().trim());
                sel.setEaName(expertField.getText().trim());

                java.util.List<String> selectedSyms = new java.util.ArrayList<>();
                for (CheckBox cb : symbolBoxes) {
                    if (cb.isSelected()) selectedSyms.add(cb.getText());
                }
                sel.setSymbols(String.join(",", selectedSyms));

                java.util.List<String> selectedTfs = new java.util.ArrayList<>();
                for (CheckBox cb : timeframeBoxes) {
                    if (cb.isSelected()) selectedTfs.add(cb.getText());
                }
                sel.setPeriod(String.join(",", selectedTfs));

                // Snapshot parameters
                java.util.List<com.backtester.config.EaParameter> currentParams = new java.util.ArrayList<>();
                if (paramTable != null && !paramTable.getItems().isEmpty()) {
                    for (com.backtester.config.EaParameter p : paramTable.getItems()) {
                        com.backtester.config.EaParameter copy = new com.backtester.config.EaParameter();
                        copy.setName(p.getName());
                        copy.setValue(p.getValue());
                        copy.setDefaultValue(p.getDefaultValue() != null ? p.getDefaultValue() : p.getValue());
                        copy.setSection(p.getSection());
                        copy.setOptimizeStart(p.getOptimizeStart());
                        copy.setOptimizeStep(p.getOptimizeStep());
                        copy.setOptimizeEnd(p.getOptimizeEnd());
                        copy.setOptimizeEnabled(p.isOptimizeEnabled());
                        copy.setStringType(p.isStringType());
                        currentParams.add(copy);
                    }
                }
                sel.setEaParameters(currentParams);

                PresetManager.getInstance().savePresets();
                presetCombo.getItems().setAll(PresetManager.getInstance().getPresets());
                presetCombo.setValue(sel);
            }
        });

        deletePresetBtn.setOnAction(evt -> {
            Preset sel = presetCombo.getValue();
            if (sel == null) {
                Alert alert = new Alert(Alert.AlertType.WARNING, "Bitte wählen Sie zuerst ein Preset aus, das gelöscht werden soll.");
                if (expertField.getScene() != null) {
                    alert.initOwner(expertField.getScene().getWindow());
                }
                alert.show();
                return;
            }

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Möchten Sie das Preset \"" + sel.getName() + "\" wirklich löschen?", ButtonType.YES, ButtonType.NO);
            confirm.setTitle("Preset löschen");
            confirm.setHeaderText("Bestätigung erforderlich");
            if (expertField.getScene() != null) {
                confirm.initOwner(expertField.getScene().getWindow());
            }

            confirm.getDialogPane().setStyle("-fx-background-color: #0b0d13;");
            confirm.getDialogPane().lookup(".content.label").setStyle("-fx-text-fill: #e6e9f0;");
            confirm.getDialogPane().lookup(".header-panel").setStyle("-fx-background-color: #0b0d13;");
            if (confirm.getDialogPane().lookup(".header-panel").lookup(".label") != null) {
                confirm.getDialogPane().lookup(".header-panel").lookup(".label").setStyle("-fx-text-fill: #00e5ff;");
            }
            if (expertField.getScene() != null && !expertField.getScene().getStylesheets().isEmpty()) {
                confirm.getDialogPane().getStylesheets().addAll(expertField.getScene().getStylesheets());
            }

            confirm.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    PresetManager.getInstance().removePreset(sel);
                    presetCombo.getItems().setAll(PresetManager.getInstance().getPresets());
                    presetCombo.setValue(null);
                }
            });
        });

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

        box.getChildren().addAll(titleBox, grid, selectionBox, btnBox);
        return box;
    }

    private TitledPane createSymbolsPane() {
        symbolsGrid = new GridPane();
        symbolsGrid.setHgap(15);
        symbolsGrid.setVgap(8);

        String[] symbols = com.backtester.engine.BacktestConfig.SYMBOLS;

        for (String sym : symbols) {
            CheckBox cb = addSymbolCheckbox(sym);
            cb.setOnAction(e -> updateSymbolsPaneTitle());
        }

        // Quick Selection Buttons
        HBox quickBox = new HBox(8);
        Button selectAllBtn = new Button("Select All");
        selectAllBtn.getStyleClass().add("button");
        selectAllBtn.setOnAction(e -> setAllSymbolsSelected(true));

        Button selectForexBtn = new Button("Forex Only");
        selectForexBtn.getStyleClass().add("button");
        selectForexBtn.setOnAction(e -> selectSymbolsCategory("FOREX"));

        Button selectIndicesBtn = new Button("Indices Only");
        selectIndicesBtn.getStyleClass().add("button");
        selectIndicesBtn.setOnAction(e -> selectSymbolsCategory("INDICES"));

        Button clearAllBtn = new Button("Clear");
        clearAllBtn.getStyleClass().add("button");
        clearAllBtn.setOnAction(e -> setAllSymbolsSelected(false));

        Button importPairsBtn = new Button("📥 Import Pairs CSV");
        importPairsBtn.getStyleClass().add("button");
        importPairsBtn.setOnAction(e -> importCurrencyPairsFromCsv());

        quickBox.getChildren().addAll(selectAllBtn, selectForexBtn, selectIndicesBtn, clearAllBtn, importPairsBtn);

        HBox customBox = new HBox(10);
        customSymbolField = new TextField();
        customSymbolField.setPromptText("Custom Symbol (z.B. SPAIN35)");
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

        VBox content = new VBox(10, quickBox, symbolsGrid, customBox);
        TitledPane tp = new TitledPane("Symbols: (None)", content);
        tp.setExpanded(false);
        return tp;
    }

    private void setAllSymbolsSelected(boolean selected) {
        for (CheckBox cb : symbolBoxes) {
            cb.setSelected(selected);
        }
        updateSymbolsPaneTitle();
    }

    private void selectSymbolsCategory(String category) {
        java.util.Set<String> indices = java.util.Set.of(
            "AUS200", "CHINA50", "DE40", "EU50", "FRANCE40", "HK50", "JP225", "SPAIN35", "UK100", "US30", "US500", "USTEC"
        );
        for (CheckBox cb : symbolBoxes) {
            String sym = cb.getText();
            if ("INDICES".equals(category)) {
                cb.setSelected(indices.contains(sym));
            } else if ("FOREX".equals(category)) {
                cb.setSelected(!indices.contains(sym) && !sym.startsWith("XAU") && !sym.startsWith("XAG") && !sym.startsWith("XTI") && !sym.startsWith("XBR"));
            }
        }
        updateSymbolsPaneTitle();
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
        int cols = 4;
        int r = (symbolBoxes.size() - 1) / cols;
        int c = (symbolBoxes.size() - 1) % cols;
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
            cb.setOnAction(e -> {
                updateTimeframesPaneTitle();
                applyChartPeriodToParamTable();
            });
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

    /**
     * Pins the batch's timeframe on the parameter table so timeframe cells resolve
     * against it instead of the global context. With zero or several timeframes
     * selected there is no single "current" period, so the pin is removed and the
     * table falls back to the global context.
     */
    private void applyChartPeriodToParamTable() {
        if (paramTable == null) return;
        java.util.List<String> selectedTfs = new java.util.ArrayList<>();
        for (CheckBox cb : timeframeBoxes) {
            if (cb.isSelected()) selectedTfs.add(cb.getText());
        }
        if (selectedTfs.size() == 1) {
            paramTable.getProperties().put(EaParameterUiContext.CHART_PERIOD_TABLE_KEY, selectedTfs.get(0));
        } else {
            paramTable.getProperties().remove(EaParameterUiContext.CHART_PERIOD_TABLE_KEY);
        }
        paramTable.refresh();
    }

    private VBox createResultsBox() {
        VBox box = new VBox(10);
        box.getStyleClass().add("sci-fi-panel");

        Label title = new Label("Batch Results Summary");
        title.getStyleClass().add("sci-fi-panel-title");

        SplitPane splitPane = new SplitPane();
        splitPane.getStyleClass().add("transparent-split-pane");
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
                if (sel.getDbId() > 0) {
                    com.backtester.database.DatabaseManager.getInstance().deleteBatch(sel.getDbId());
                }
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
        winCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(String.format("%.2f%%", cellData.getValue().getWinRate())));

        javafx.scene.control.TableColumn<com.backtester.report.BacktestResult, String> ddCol = new javafx.scene.control.TableColumn<>("Drawdown");
        ddCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(String.format("%.2f%%", cellData.getValue().getMaxDrawdown())));

        javafx.scene.control.TableColumn<com.backtester.report.BacktestResult, String> recCol = new javafx.scene.control.TableColumn<>("Recovery Factor");
        recCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(String.format("%.2f", cellData.getValue().getRecoveryFactor())));

        javafx.scene.control.TableColumn<com.backtester.report.BacktestResult, String> profCol = new javafx.scene.control.TableColumn<>("Profit");
        profCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(String.format("%.2f", cellData.getValue().getTotalProfit())));

        javafx.scene.control.TableColumn<com.backtester.report.BacktestResult, String> statusCol = new javafx.scene.control.TableColumn<>("Status");
        statusCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().isSuccess() ? "OK" : "Fail"));

        resultsTable.getColumns().addAll(eaCol, symCol, perCol, tradesCol, winCol, ddCol, recCol, profCol, statusCol);

        resultsTable.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                com.backtester.report.BacktestResult sel = resultsTable.getSelectionModel().getSelectedItem();
                if (sel != null && sel.getOutputDirectory() != null) {
                    javax.swing.SwingUtilities.invokeLater(() ->
                            com.backtester.ui.ReportViewerDialog.showForDirectory(
                                    null, sel.getOutputDirectory()));
                }
            }
        });

        HBox tableBtnBox = new HBox(10);
        tableBtnBox.setAlignment(Pos.CENTER_RIGHT);

        Button exportPairsBtn = new Button("📤 Export Successful Pairs");
        exportPairsBtn.getStyleClass().add("button");
        exportPairsBtn.setOnAction(e -> exportSuccessfulPairsToCsv());

        Button showReportBtn = new Button("📊 Show Single Report");
        showReportBtn.getStyleClass().add("button");
        showReportBtn.setOnAction(e -> {
            com.backtester.report.BacktestResult sel = resultsTable.getSelectionModel().getSelectedItem();
            if (sel != null && sel.getOutputDirectory() != null) {
                javax.swing.SwingUtilities.invokeLater(() ->
                        com.backtester.ui.ReportViewerDialog.showForDirectory(
                                null, sel.getOutputDirectory()));
            }
        });

        Button delRunBtn = new Button("🗑 Delete Selected Runs");
        delRunBtn.getStyleClass().add("button");
        delRunBtn.setOnAction(e -> {
            com.backtester.report.BacktestResult sel = resultsTable.getSelectionModel().getSelectedItem();
            BatchRun batch = batchList.getSelectionModel().getSelectedItem();
            if (sel != null && batch != null) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Confirm Deletion");
                alert.setHeaderText("Delete Selected Run");
                alert.setContentText("Are you sure you want to delete this run from the batch?");
                java.util.Optional<ButtonType> res = alert.showAndWait();
                if (res.isPresent() && res.get() == ButtonType.OK) {
                    batch.getResults().remove(sel);
                    resultsTable.getItems().remove(sel);
                    if (batch.getDbId() > 0) {
                        try {
                            String resultsJson = new com.google.gson.Gson().toJson(batch.getResults());
                            com.backtester.database.DatabaseManager.getInstance().updateBatchResults(batch.getDbId(), resultsJson);
                        } catch (Exception ex) {
                            logView.log("ERROR", "Failed to update batch results in DB: " + ex.getMessage());
                        }
                    }
                    logView.log("INFO", "Deleted run from batch.");
                }
            }
        });

        tableBtnBox.getChildren().addAll(exportPairsBtn, showReportBtn, delRunBtn);

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

    private void loadPreferences() {
        String exp = config.get("multibacktest.expert", "");
        if (!exp.isEmpty()) {
            expertField.setText(exp);
            loadParameters();
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
        applyChartPeriodToParamTable();

        String mod = config.get("multibacktest.model", "Every tick");
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
        int mIdx = modelCombo.getSelectionModel().getSelectedIndex();
        batchConfig.setModel(mIdx >= 0 ? mIdx : 0);

        if (paramTable != null && !paramTable.getItems().isEmpty()) {
            com.backtester.database.DatabaseManager.getInstance().saveEaParameterSettings(expert, "GLOBAL", "GLOBAL", new com.google.gson.Gson().toJson(paramTable.getItems()));
            eaParamManager.saveCustomParameters(expert, new java.util.ArrayList<>(paramTable.getItems()));
        }

        String setFileName = eaParamManager.prepareForBacktest(expert);
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

                    // Persist batch to database
                    try {
                        com.google.gson.Gson gson = new com.google.gson.Gson();
                        String resultsJson = gson.toJson(newBatch.getResults());
                        String htmlPathStr = newBatch.getHtmlReportPath() != null ? newBatch.getHtmlReportPath().toString() : "";
                        com.backtester.database.DatabaseManager.getInstance().saveBatch(
                            newBatch.getName(), System.currentTimeMillis(), htmlPathStr, resultsJson);
                        // Also save individual results to HISTORY_RUNS for the History tab
                        for (com.backtester.report.BacktestResult res : newBatch.getResults()) {
                            if (res.isSuccess()) {
                                String fullJson = gson.toJson(res);
                                com.backtester.database.DatabaseManager.getInstance().saveRun(
                                    "MULTI_BACKTEST", res.getExpert(), System.currentTimeMillis(),
                                    fullJson, res.getOutputDirectory());
                            }
                        }
                    } catch (Exception ex) {
                        logView.log("ERROR", "Failed to save batch to DB: " + ex.getMessage());
                    }

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

    private void applyPreset(Preset sel) {
        if (sel == null) return;

        if (sel.getEaName() != null && !sel.getEaName().isEmpty()) {
            expertField.setText(sel.getEaName());
        }

        // 1. Manage timeframes: split by comma, select matched, uncheck others
        java.util.List<String> targetTfs = new java.util.ArrayList<>();
        if (sel.getPeriod() != null && !sel.getPeriod().trim().isEmpty()) {
            for (String tf : sel.getPeriod().split(",\\s*")) {
                targetTfs.add(tf.trim().toUpperCase());
            }
        }
        for (CheckBox cb : timeframeBoxes) {
            cb.setSelected(targetTfs.contains(cb.getText().toUpperCase()));
        }

        // 2. Manage symbols: split by comma, select matched, uncheck others, dynamically add if missing
        java.util.List<String> targetSymbols = new java.util.ArrayList<>();
        if (sel.getSymbols() != null && !sel.getSymbols().trim().isEmpty()) {
            for (String sym : sel.getSymbols().split(",\\s*")) {
                targetSymbols.add(sym.trim().toUpperCase());
            }
        }
        for (String sym : targetSymbols) {
            CheckBox found = null;
            for (CheckBox cb : symbolBoxes) {
                if (cb.getText().equals(sym)) {
                    found = cb;
                    break;
                }
            }
            if (found == null) {
                found = addSymbolCheckbox(sym);
                found.setOnAction(e -> updateSymbolsPaneTitle());
            }
            found.setSelected(true);
        }

        // Uncheck any symbols NOT in the target list
        for (CheckBox cb : symbolBoxes) {
            if (!targetSymbols.contains(cb.getText())) {
                cb.setSelected(false);
            }
        }

        updateSymbolsPaneTitle();
        updateTimeframesPaneTitle();
        applyChartPeriodToParamTable();

        if (sel.getEaParameters() != null && !sel.getEaParameters().isEmpty() && paramTable != null) {
            java.util.List<com.backtester.config.EaParameter> tableCopy = new java.util.ArrayList<>();
            for (com.backtester.config.EaParameter p : sel.getEaParameters()) {
                com.backtester.config.EaParameter copy = new com.backtester.config.EaParameter();
                copy.setName(p.getName());
                copy.setValue(p.getValue());
                copy.setDefaultValue(p.getDefaultValue() != null ? p.getDefaultValue() : p.getValue());
                copy.setSection(p.getSection());
                copy.setOptimizeStart(p.getOptimizeStart());
                copy.setOptimizeStep(p.getOptimizeStep());
                copy.setOptimizeEnd(p.getOptimizeEnd());
                copy.setOptimizeEnabled(p.isOptimizeEnabled());
                copy.setStringType(p.isStringType());
                tableCopy.add(copy);
            }
            paramTable.getItems().setAll(tableCopy);
            logView.log("INFO", "Loaded " + tableCopy.size() + " parameters directly from preset: " + sel.getName());
        } else {
            loadParameters();
        }

        savePreferences();
        logView.log("INFO", "Applied preset: " + sel.getName());
    }

    private void loadBatchesFromDb() {
        try {
            java.util.List<Object[]> dbBatches = com.backtester.database.DatabaseManager.getInstance().getAllBatches();
            if (dbBatches.isEmpty()) return;

            com.google.gson.Gson gson = new com.google.gson.Gson();
            java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<java.util.List<com.backtester.report.BacktestResult>>(){}.getType();
            int loaded = 0;
            for (Object[] row : dbBatches) {
                try {
                    int id = (int) row[0];
                    String batchName = (String) row[1];
                    String htmlPath = (String) row[3];
                    String resultsJson = (String) row[4];

                    BatchRun batch = new BatchRun(batchName);
                    batch.setDbId(id);
                    if (htmlPath != null && !htmlPath.isEmpty()) {
                        batch.setHtmlReportPath(java.nio.file.Paths.get(htmlPath));
                    }
                    if (resultsJson != null && !resultsJson.isEmpty()) {
                        java.util.List<com.backtester.report.BacktestResult> results = gson.fromJson(resultsJson, listType);
                        if (results != null) {
                            batch.getResults().addAll(results);
                        }
                    }
                    batchList.getItems().add(batch);
                    loaded++;
                } catch (Exception ex) {
                    // Skip invalid entries
                }
            }
            if (loaded > 0) {
                logView.log("INFO", "Loaded " + loaded + " multi-backtest batches from database.");
            }
        } catch (Exception ex) {
            logView.log("ERROR", "Failed to load batches from DB: " + ex.getMessage());
        }
    }

    public void bindTab(Tab tab) {
        updateTabTitle(tab, batchList.getItems().size());
        batchList.getItems().addListener((javafx.collections.ListChangeListener<BatchRun>) c -> {
            updateTabTitle(tab, batchList.getItems().size());
        });
    }

    private void updateTabTitle(Tab tab, int count) {
        javafx.application.Platform.runLater(() -> tab.setText("Multi-Backtester (" + count + ")"));
    }

    public BorderPane getView() {
        return root;
    }

    // ==================== EA Parameter Section & Logic ====================

    private void loadParameters() {
        String expert = expertField.getText().trim();
        if (expert.isEmpty()) return;

        // Try DB first
        String dbParamsJson = com.backtester.database.DatabaseManager.getInstance().getEaParameterSettings(expert, "GLOBAL", "GLOBAL");
        if (dbParamsJson != null && !dbParamsJson.isEmpty()) {
            try {
                java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<java.util.List<com.backtester.config.EaParameter>>(){}.getType();
                java.util.List<com.backtester.config.EaParameter> params = new com.google.gson.Gson().fromJson(dbParamsJson, listType);
                if (params != null && !params.isEmpty()) {
                    eaParamManager.applyTranslations(expert, params);
                    paramTable.getItems().setAll(params);
                    logView.log("INFO", "Loaded global parameters for " + EaParameterManager.extractEaBaseName(expert) + " from DB");
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
        nameCol.setCellValueFactory(cellData -> {
            com.backtester.config.EaParameter param = cellData.getValue();
            String display = param.getDisplayName();
            if (display == null || display.trim().isEmpty()) {
                display = param.getName();
            }
            return new javafx.beans.property.SimpleStringProperty(display);
        });
        nameCol.setCellFactory(column -> new javafx.scene.control.TableCell<com.backtester.config.EaParameter, String>() {
            private final javafx.scene.control.Tooltip tooltip = new javafx.scene.control.Tooltip();
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item);
                    com.backtester.config.EaParameter param = getTableRow() != null ? getTableRow().getItem() : null;
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

        TableColumn<com.backtester.config.EaParameter, String> valCol = new TableColumn<>("Value");
        valCol.setCellValueFactory(new PropertyValueFactory<>("value"));
        valCol.setCellFactory(EnumAwareParamCell.forTableColumn());
        valCol.setOnEditCommit(e -> e.getRowValue().setValue(e.getNewValue()));
        valCol.setPrefWidth(100);

        TableColumn<com.backtester.config.EaParameter, String> startCol = new TableColumn<>("Start");
        startCol.setCellValueFactory(new PropertyValueFactory<>("optimizeStart"));
        startCol.setCellFactory(EnumAwareParamCell.forTableColumn());
        startCol.setOnEditCommit(e -> e.getRowValue().setOptimizeStart(e.getNewValue()));

        TableColumn<com.backtester.config.EaParameter, String> stepCol = new TableColumn<>("Step");
        stepCol.setCellValueFactory(new PropertyValueFactory<>("optimizeStep"));
        stepCol.setCellFactory(EnumAwareParamCell.forTableColumn());
        stepCol.setOnEditCommit(e -> e.getRowValue().setOptimizeStep(e.getNewValue()));

        TableColumn<com.backtester.config.EaParameter, String> stopCol = new TableColumn<>("Stop");
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

        javafx.concurrent.Task<Boolean> task = new javafx.concurrent.Task<Boolean>() {
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

    private void saveParametersOnDemand() {
        String expert = expertField.getText().trim();
        if (expert.isEmpty()) return;
        if (paramTable != null && !paramTable.getItems().isEmpty()) {
            com.backtester.database.DatabaseManager.getInstance().saveEaParameterSettings(expert, "GLOBAL", "GLOBAL", new com.google.gson.Gson().toJson(paramTable.getItems()));
            eaParamManager.saveCustomParameters(expert, new java.util.ArrayList<>(paramTable.getItems()));
        }
    }

    public static class ImportedPair {
        public String symbol;
        public String period = "";
        public double profit = 0;
        public int trades = 0;
        public double drawdown = 0;
        public boolean hasDetails = false;

        public ImportedPair(String symbol) {
            this.symbol = symbol;
        }
    }

    private void exportSuccessfulPairsToCsv() {
        java.util.List<com.backtester.report.BacktestResult> currentResults = resultsTable.getItems();
        if (currentResults == null || currentResults.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Keine Ergebnisse in der aktuellen Tabelle zum Exportieren vorhanden.");
            if (root.getScene() != null) alert.initOwner(root.getScene().getWindow());
            alert.show();
            return;
        }

        // Filter: success, profit > 0, trades > 10
        java.util.List<com.backtester.report.BacktestResult> successfulRuns = new java.util.ArrayList<>();
        for (com.backtester.report.BacktestResult r : currentResults) {
            if (r.isSuccess() && r.getTotalProfit() > 0 && r.getTotalTrades() > 10) {
                if (r.getSymbol() != null && !r.getSymbol().trim().isEmpty()) {
                    successfulRuns.add(r);
                }
            }
        }

        if (successfulRuns.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Keine erfolgreichen Währungspaare (Profit > 0 und Trades > 10) in den aktuellen Ergebnissen gefunden.");
            if (root.getScene() != null) alert.initOwner(root.getScene().getWindow());
            alert.show();
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Erfolgreiche Währungspaare als CSV speichern");
        fileChooser.setInitialFileName("successful_currency_pairs.csv");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Dateien (*.csv)", "*.csv"));

        File saveFile = fileChooser.showSaveDialog(root.getScene().getWindow());
        if (saveFile == null) return;

        try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(saveFile))) {
            writer.println("Symbol,Period,Profit,Trades,Drawdown");
            for (com.backtester.report.BacktestResult r : successfulRuns) {
                String sym = r.getSymbol().trim().toUpperCase();
                String per = r.getPeriod() != null ? r.getPeriod().trim() : "";
                double prof = r.getTotalProfit();
                int trades = r.getTotalTrades();
                double dd = r.getMaxDrawdown();
                writer.println(String.format(java.util.Locale.US, "%s,%s,%.2f,%d,%.2f", sym, per, prof, trades, dd));
            }
            logView.log("INFO", "Erfolgreiche Währungspaare (" + successfulRuns.size() + ") exportiert nach: " + saveFile.getAbsolutePath());

            Alert alert = new Alert(Alert.AlertType.INFORMATION, successfulRuns.size() + " erfolgreiche Währungspaare inkl. Gewinnen, Trades & Drawdown wurden exportiert!\n\nDatei: " + saveFile.getName());
            if (root.getScene() != null) alert.initOwner(root.getScene().getWindow());
            alert.show();
        } catch (Exception ex) {
            logView.log("ERROR", "Fehler beim Exportieren der Währungspaare: " + ex.getMessage());
            Alert alert = new Alert(Alert.AlertType.ERROR, "Fehler beim Exportieren: " + ex.getMessage());
            if (root.getScene() != null) alert.initOwner(root.getScene().getWindow());
            alert.show();
        }
    }

    private void importCurrencyPairsFromCsv() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Währungspaare aus CSV importieren");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("CSV / Text Dateien (*.csv, *.txt)", "*.csv", "*.txt"),
            new FileChooser.ExtensionFilter("Alle Dateien (*.*)", "*.*")
        );

        File importFile = fileChooser.showOpenDialog(root.getScene().getWindow());
        if (importFile == null) return;

        java.util.List<ImportedPair> parsedPairs = new java.util.ArrayList<>();
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(importFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.toLowerCase().startsWith("symbol") || line.startsWith("#")) continue;
                String[] tokens = line.split("[,;\\t]+");
                if (tokens.length >= 1) {
                    String sym = tokens[0].trim().toUpperCase();
                    if (!sym.isEmpty() && !sym.equalsIgnoreCase("Symbol")) {
                        ImportedPair pair = new ImportedPair(sym);
                        if (tokens.length >= 5) {
                            try {
                                pair.period = tokens[1].trim();
                                pair.profit = Double.parseDouble(tokens[2].trim());
                                pair.trades = Integer.parseInt(tokens[3].trim());
                                pair.drawdown = Double.parseDouble(tokens[4].trim());
                                pair.hasDetails = true;
                            } catch (Exception ignored) {}
                        } else if (tokens.length >= 2) {
                            pair.period = tokens[1].trim();
                        }
                        parsedPairs.add(pair);
                    }
                }
            }
        } catch (Exception ex) {
            logView.log("ERROR", "Fehler beim Lesen der CSV-Datei: " + ex.getMessage());
            Alert alert = new Alert(Alert.AlertType.ERROR, "Fehler beim Lesen der Datei: " + ex.getMessage());
            if (root.getScene() != null) alert.initOwner(root.getScene().getWindow());
            alert.show();
            return;
        }

        if (parsedPairs.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Keine Währungspaare in der ausgewählten Datei gefunden.");
            if (root.getScene() != null) alert.initOwner(root.getScene().getWindow());
            alert.show();
            return;
        }

        // Show Dialog with detailed Table & Checkboxes for user selection
        Dialog<java.util.List<String>> dialog = new Dialog<>();
        dialog.setTitle("Währungspaare importieren");
        if (root.getScene() != null) dialog.initOwner(root.getScene().getWindow());

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.setStyle("-fx-background-color: #0b0d13;");
        if (expertField.getScene() != null && !expertField.getScene().getStylesheets().isEmpty()) {
            dialogPane.getStylesheets().addAll(expertField.getScene().getStylesheets());
        }

        VBox dialogContent = new VBox(12);
        dialogContent.setPadding(new Insets(12));

        Label headerLabel = new Label("Folgende " + parsedPairs.size() + " Währungspaare wurden in der Datei gefunden.\nWählen Sie die Paare aus, die übernommen werden sollen:");
        headerLabel.setStyle("-fx-text-fill: #00e5ff; -fx-font-weight: bold; -fx-font-size: 13px;");

        // Selection helpers inside dialog
        HBox dlgBtnBox = new HBox(10);
        Button dlgSelectAll = new Button("Alle auswählen");
        dlgSelectAll.getStyleClass().add("button");
        Button dlgDeselectAll = new Button("Alle abwählen");
        dlgDeselectAll.getStyleClass().add("button");
        dlgBtnBox.getChildren().addAll(dlgSelectAll, dlgDeselectAll);

        GridPane dlgGrid = new GridPane();
        dlgGrid.setHgap(20);
        dlgGrid.setVgap(8);
        dlgGrid.setPadding(new Insets(5));

        // Column Headers
        Label hSel = new Label("Auswahl"); hSel.setStyle("-fx-text-fill: #8c91a0; -fx-font-weight: bold;");
        Label hSym = new Label("Symbol"); hSym.setStyle("-fx-text-fill: #8c91a0; -fx-font-weight: bold;");
        Label hPer = new Label("Period"); hPer.setStyle("-fx-text-fill: #8c91a0; -fx-font-weight: bold;");
        Label hProf = new Label("Nettogewinn"); hProf.setStyle("-fx-text-fill: #8c91a0; -fx-font-weight: bold;");
        Label hTrd = new Label("Trades"); hTrd.setStyle("-fx-text-fill: #8c91a0; -fx-font-weight: bold;");
        Label hDd = new Label("Drawdown"); hDd.setStyle("-fx-text-fill: #8c91a0; -fx-font-weight: bold;");

        dlgGrid.add(hSel, 0, 0);
        dlgGrid.add(hSym, 1, 0);
        dlgGrid.add(hPer, 2, 0);
        dlgGrid.add(hProf, 3, 0);
        dlgGrid.add(hTrd, 4, 0);
        dlgGrid.add(hDd, 5, 0);

        java.util.List<CheckBox> dlgCheckboxes = new java.util.ArrayList<>();
        java.util.Map<CheckBox, String> cbSymbolMap = new java.util.HashMap<>();

        for (int i = 0; i < parsedPairs.size(); i++) {
            ImportedPair pair = parsedPairs.get(i);
            int row = i + 1;

            CheckBox cb = new CheckBox();
            cb.getStyleClass().add("check-box");
            cb.setSelected(true); // Pre-checked by default
            dlgCheckboxes.add(cb);
            cbSymbolMap.put(cb, pair.symbol);

            Label lblSym = new Label(pair.symbol);
            lblSym.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold;");

            Label lblPer = new Label(pair.period.isEmpty() ? "-" : pair.period);
            lblPer.setStyle("-fx-text-fill: #d0d0d0;");

            Label lblProf = new Label(pair.hasDetails ? String.format(java.util.Locale.US, "%.2f", pair.profit) : "-");
            lblProf.setStyle(pair.hasDetails && pair.profit >= 0 ? "-fx-text-fill: #12b886; -fx-font-weight: bold;" : "-fx-text-fill: #fa5252; -fx-font-weight: bold;");

            Label lblTrd = new Label(pair.hasDetails ? String.valueOf(pair.trades) : "-");
            lblTrd.setStyle("-fx-text-fill: #d0d0d0;");

            Label lblDd = new Label(pair.hasDetails ? String.format(java.util.Locale.US, "%.2f%%", pair.drawdown) : "-");
            lblDd.setStyle(pair.hasDetails && pair.drawdown > 20 ? "-fx-text-fill: #fa5252; -fx-font-weight: bold;" : "-fx-text-fill: #d0d0d0;");

            dlgGrid.add(cb, 0, row);
            dlgGrid.add(lblSym, 1, row);
            dlgGrid.add(lblPer, 2, row);
            dlgGrid.add(lblProf, 3, row);
            dlgGrid.add(lblTrd, 4, row);
            dlgGrid.add(lblDd, 5, row);
        }

        dlgSelectAll.setOnAction(e -> dlgCheckboxes.forEach(cb -> cb.setSelected(true)));
        dlgDeselectAll.setOnAction(e -> dlgCheckboxes.forEach(cb -> cb.setSelected(false)));

        ScrollPane scrollPane = new ScrollPane(dlgGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(300);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: #141722;");

        dialogContent.getChildren().addAll(headerLabel, dlgBtnBox, scrollPane);
        dialogPane.setContent(dialogContent);

        ButtonType importButtonType = new ButtonType("Übernehmen", ButtonBar.ButtonData.OK_DONE);
        dialogPane.getButtonTypes().addAll(importButtonType, ButtonType.CANCEL);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == importButtonType) {
                java.util.List<String> selected = new java.util.ArrayList<>();
                for (CheckBox cb : dlgCheckboxes) {
                    if (cb.isSelected()) {
                        String sym = cbSymbolMap.get(cb);
                        if (sym != null && !selected.contains(sym)) {
                            selected.add(sym);
                        }
                    }
                }
                return selected;
            }
            return null;
        });

        java.util.Optional<java.util.List<String>> result = dialog.showAndWait();
        if (result.isPresent()) {
            java.util.List<String> selectedToImport = result.get();
            if (selectedToImport.isEmpty()) return;

            // Uncheck all existing symbols first
            setAllSymbolsSelected(false);

            // Check/Add the imported selected symbols
            for (String sym : selectedToImport) {
                boolean found = false;
                for (CheckBox cb : symbolBoxes) {
                    if (cb.getText().equalsIgnoreCase(sym)) {
                        cb.setSelected(true);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    CheckBox newCb = addSymbolCheckbox(sym);
                    newCb.setSelected(true);
                    newCb.setOnAction(evt -> updateSymbolsPaneTitle());
                }
            }
            updateSymbolsPaneTitle();
            if (symbolsPane != null) symbolsPane.setExpanded(true);
            logView.log("INFO", "Währungspaare (" + selectedToImport.size() + ") erfolgreich in die Auswahl übernommen.");
        }
    }
}
