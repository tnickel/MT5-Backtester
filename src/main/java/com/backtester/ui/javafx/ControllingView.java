package com.backtester.ui.javafx;

import com.backtester.config.AppConfig;
import com.backtester.config.EaParameter;
import com.backtester.config.EaParameterManager;
import com.backtester.database.DatabaseManager;
import com.backtester.database.HistoryRun;
import com.backtester.engine.BacktestConfig;
import com.backtester.engine.BacktestRunner;
import com.backtester.report.BacktestResult;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import com.backtester.report.SensitivityResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Tab view for controlling, evaluating, and manually running backtests/presets 
 * on selected strategies from workflow history.
 */
public class ControllingView {

    private static final Logger log = LoggerFactory.getLogger(ControllingView.class);

    private final BorderPane root;
    private final LogView logView;
    private final WorkflowView workflowView;
    private final DatabaseManager dbManager;
    private final EaParameterManager eaParamManager;
    private final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // UI Left
    private final TableView<ControllingStrategy> table;
    private final ToggleGroup filterGroup;
    private final RadioButton allFilterBtn;
    private final RadioButton bestFilterBtn;
    private final TextField keywordField;
    private final ComboBox<String> dateModeCombo;
    private final DatePicker customDatePicker;
    private final TextField exportDirField;
    private final Button browseExportBtn;

    // UI Right
    private final Label expertLabel;
    private final Label symbolLabel;
    private final Label periodLabel;
    private final Label dateLabel;

    private final Label profitVal;
    private final Label ddVal;
    private final Label tradesVal;
    private final Label pfVal;
    private final Label sharpeVal;
    private final Label recVal;
    private final Label unifiedScoreVal;
    private final Label kiScoreVal;

    private final Label verdictLabel;
    private final VBox kiPanel;
    private final Label kiVerdictLabel;
    private final Label kiExplanationLabel;
    private final LineChart<Number, Number> equityChart;
    private final TableView<EaParameter> paramTable;
    
    private final ComboBox<String> modelCombo;
    private final Button runBacktestBtn;
    private final Button exportSettingsBtn;
    private final Button deleteStrategyBtn;
    private final Button autoReviewBtn;
    private final ProgressBar progress;
    private final Label progressLabel;

    private final WebView kiReportWebView;

    // Tab 3 fields
    private final Label profit1y;
    private final Label profit2y;
    private final Label dd1y;
    private final Label dd2y;
    private final Label trades1y;
    private final Label trades2y;
    private final Label winRate1y;
    private final Label winRate2y;
    private final Label pf1y;
    private final Label pf2y;
    private final Label sharpe1y;
    private final Label sharpe2y;
    private final Label rec1y;
    private final Label rec2y;
    private final Label autoReviewWarningLabel;
    private final GridPane autoReviewGrid;

    private final List<ControllingStrategy> allLoadedStrategies = new ArrayList<>();
    private final ObservableList<ControllingStrategy> tableItems = FXCollections.observableArrayList();

    private Task<BacktestResult> runningBacktestTask;

    private final TabPane graphModeTabPane;
    private final Tab tabOriginal;
    private final Tab tab1y;
    private final Tab tab2y;
    private ControllingStrategy selectedStrategy;

    public ControllingView(LogView logView, WorkflowView workflowView) {
        this.logView = logView;
        this.workflowView = workflowView;
        this.dbManager = DatabaseManager.getInstance();
        this.eaParamManager = new EaParameterManager();

        root = new BorderPane();
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: transparent;");

        // SplitPane
        SplitPane splitPane = new SplitPane();
        splitPane.setStyle("-fx-background-color: transparent; -fx-box-border: transparent;");
        splitPane.setDividerPositions(0.42);

        // ==========================================
        // LEFT SIDE: Table & Filters
        // ==========================================
        VBox leftBox = new VBox(15);
        leftBox.getStyleClass().add("sci-fi-panel");
        leftBox.setPadding(new Insets(15));

        HBox titleBox = new HBox(15);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        Label titleLabel = new Label("Strategie Controlling-System");
        titleLabel.getStyleClass().add("sci-fi-panel-title");
        
        javafx.scene.layout.Region titleSpacer = new javafx.scene.layout.Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);
        titleBox.getChildren().addAll(titleLabel, titleSpacer, DocHelper.createInfoButton("Controlling", 
            "Das Controlling-Modul dient als analytische Schnittstelle zur Überprüfung und Re-Verifikation optimierter Handelssysteme.",
            "Hier werden alle Portfolios und Strategie-Durchläufe aus abgeschlossenen Workflows zusammengeführt. Sie können:\n\n" +
            "1. Durch das Archiv scrollen und die Leistungskurven vergleichen.\n" +
            "2. Einzelne Strategiekonfigurationen (.set) exportieren.\n" +
            "3. Einen unmittelbaren Nachtest (OHLC oder Every Tick) im MetaTrader auslösen, um die Stabilität manuell nachzuweisen."));

        // Filters HBox
        HBox filterBox = new HBox(15);
        filterBox.setAlignment(Pos.CENTER_LEFT);
        
        filterGroup = new ToggleGroup();
        allFilterBtn = new RadioButton("Alle anzeigen");
        allFilterBtn.setToggleGroup(filterGroup);
        allFilterBtn.setSelected(true);
        allFilterBtn.getStyleClass().add("radio-button");
        allFilterBtn.setStyle("-fx-text-fill: #e2e8f0;");
        allFilterBtn.setOnAction(e -> applyFilter());

        bestFilterBtn = new RadioButton("Nur die Besten anzeigen");
        bestFilterBtn.setToggleGroup(filterGroup);
        bestFilterBtn.getStyleClass().add("radio-button");
        bestFilterBtn.setStyle("-fx-text-fill: #e2e8f0;");
        bestFilterBtn.setOnAction(e -> applyFilter());

        Button bestInfoBtn = DocHelper.createSmallInfoButton("Beste Strategien", 
            "Wie werden die besten Strategien (in 'exports_gut') ermittelt?", 
            "Die in 'exports_gut' abgelegten Strategien müssen folgende Kriterien erfüllen:\n\n" +
            "1. Sie müssen Teil der endgültig ausgewählten Strategien (Top 3-5) am Ende des Workflows sein (Schritt 6).\n" +
            "2. Sie müssen einen KI-Stabilitätswert (KI Score) von mindestens 70 von 100 Punkten aufweisen.\n\n" +
            "HINTERGRUND-FORMEL:\n" +
            "-----------------\n" +
            "Die Selektion erfolgt nach der Formel:\n" +
            "   KI-Score >= 70\n\n" +
            "Der KI-Score wird in Schritt 5 durch ein LLM (Large Language Model) ermittelt, welches die Kennlinien-Datenpunkte, " +
            "Kurvenformen und die Profit-/Trade-Konsistenz zwischen In-Sample (Backtest) und Out-of-Sample (Forward) bewertet:\n\n" +
            "- Plateau/Glocke-Formen erhalten hohe Bewertungen (Robust).\n" +
            "- Peaks, Cliffs oder chaotische Verläufe führen zu Abzügen (Fragil/Überoptimiert).\n" +
            "- Ein Score >= 70 signalisiert hohe Stabilität bei Parameter-Variationen.");

        Button refreshBtn = new Button("🔄 Refresh");
        refreshBtn.getStyleClass().add("button");
        refreshBtn.setOnAction(e -> refreshResults());

        autoReviewBtn = new Button("🔍 Automatisches Review");
        autoReviewBtn.getStyleClass().add("button");
        autoReviewBtn.setOnAction(e -> startAutomaticReview());

        filterBox.getChildren().addAll(allFilterBtn, bestFilterBtn, bestInfoBtn, refreshBtn, autoReviewBtn);

        // Search and Date Filters HBox
        HBox searchFilterBox = new HBox(10);
        searchFilterBox.setAlignment(Pos.CENTER_LEFT);

        Label searchLabel = new Label("Suche:");
        searchLabel.setStyle("-fx-text-fill: #7e889a; -fx-font-weight: bold;");

        keywordField = new TextField();
        keywordField.setPromptText("EA, Symbol, Timeframe...");
        keywordField.setPrefWidth(160);
        keywordField.getStyleClass().add("text-input");
        keywordField.textProperty().addListener((obs, oldVal, newVal) -> applyFilter());

        Label dateFilterLabel = new Label("Datum:");
        dateFilterLabel.setStyle("-fx-text-fill: #7e889a; -fx-font-weight: bold;");

        dateModeCombo = new ComboBox<>(FXCollections.observableArrayList(
            "Alle Daten", "Heute", "Gestern", "Auswählen..."
        ));
        dateModeCombo.getStyleClass().add("combo-box");
        dateModeCombo.setValue("Alle Daten");
        dateModeCombo.setPrefWidth(110);

        customDatePicker = new DatePicker();
        customDatePicker.setPromptText("Datum...");
        customDatePicker.setPrefWidth(115);
        customDatePicker.setVisible(false);
        customDatePicker.setManaged(false);
        customDatePicker.getStyleClass().add("date-picker");

        dateModeCombo.setOnAction(e -> {
            boolean isCustom = "Auswählen...".equals(dateModeCombo.getValue());
            customDatePicker.setVisible(isCustom);
            customDatePicker.setManaged(isCustom);
            applyFilter();
        });

        customDatePicker.setOnAction(e -> applyFilter());

        searchFilterBox.getChildren().addAll(
            searchLabel, keywordField, 
            dateFilterLabel, dateModeCombo, customDatePicker
        );

        // Table definition
        table = new TableView<>();
        table.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<ControllingStrategy, String> dateCol = new TableColumn<>("Datum");
        dateCol.setCellValueFactory(cellData -> new SimpleStringProperty(df.format(new Date(cellData.getValue().getRunTimestamp()))));
        dateCol.setPrefWidth(130);

        TableColumn<ControllingStrategy, String> eaCol = new TableColumn<>("Expert");
        eaCol.setCellValueFactory(cellData -> new SimpleStringProperty(EaParameterManager.extractEaBaseName(cellData.getValue().getExpert())));
        eaCol.setPrefWidth(110);

        TableColumn<ControllingStrategy, String> symCol = new TableColumn<>("Symbol");
        symCol.setCellValueFactory(new PropertyValueFactory<>("symbol"));
        symCol.setPrefWidth(65);

        TableColumn<ControllingStrategy, String> tfCol = new TableColumn<>("Period");
        tfCol.setCellValueFactory(new PropertyValueFactory<>("period"));
        tfCol.setPrefWidth(55);

        TableColumn<ControllingStrategy, Integer> passCol = new TableColumn<>("Pass");
        passCol.setCellValueFactory(new PropertyValueFactory<>("passNumber"));
        passCol.setPrefWidth(45);

        TableColumn<ControllingStrategy, String> profitCol = new TableColumn<>("Gewinn");
        profitCol.setCellValueFactory(cellData -> new SimpleStringProperty(String.format(Locale.US, "%.2f", cellData.getValue().getBtProfit())));
        profitCol.setPrefWidth(70);

        TableColumn<ControllingStrategy, String> ddCol = new TableColumn<>("Max DD");
        ddCol.setCellValueFactory(cellData -> new SimpleStringProperty(String.format(Locale.US, "%.2f%%", cellData.getValue().getBtDd())));
        ddCol.setPrefWidth(65);

        TableColumn<ControllingStrategy, Integer> tableTradesCol = new TableColumn<>("Trades");
        tableTradesCol.setCellValueFactory(new PropertyValueFactory<>("btTrades"));
        tableTradesCol.setPrefWidth(55);

        TableColumn<ControllingStrategy, String> scoreCol = new TableColumn<>("Score");
        scoreCol.setCellValueFactory(cellData -> new SimpleStringProperty(String.format(Locale.US, "%.1f", cellData.getValue().getScore())));
        scoreCol.setPrefWidth(50);

        TableColumn<ControllingStrategy, String> kiCol = new TableColumn<>("KI Score");
        kiCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getKiScore() >= 0 ? String.valueOf(cellData.getValue().getKiScore()) : "-"));
        kiCol.setPrefWidth(65);

        TableColumn<ControllingStrategy, String> reviewCol = new TableColumn<>("Review");
        reviewCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getReviewText()));
        reviewCol.setPrefWidth(120);

        table.getColumns().addAll(dateCol, eaCol, symCol, tfCol, passCol, profitCol, ddCol, tableTradesCol, scoreCol, kiCol, reviewCol);
        table.setItems(tableItems);

        // Context menu and coloring via RowFactory
        table.setRowFactory(tv -> {
            TableRow<ControllingStrategy> row = new TableRow<>() {
                @Override
                protected void updateItem(ControllingStrategy item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setStyle("");
                        getStyleClass().removeAll("top-choice", "good-choice", "average-choice", "weak-choice", "poor-choice");
                        setContextMenu(null);
                    } else {
                        String color = item.getColorRating();
                        getStyleClass().removeAll("top-choice", "good-choice", "average-choice", "weak-choice", "poor-choice");
                        setStyle(""); // Reset inline style to let CSS classes take effect
                        if (color != null && !color.isEmpty()) {
                            switch (color.toLowerCase()) {
                                case "dunkel grün":
                                case "dunkelgrün":
                                    getStyleClass().add("top-choice");
                                    break;
                                case "grün":
                                    getStyleClass().add("good-choice");
                                    break;
                                case "gelb":
                                    getStyleClass().add("average-choice");
                                    break;
                                case "orange":
                                    getStyleClass().add("weak-choice");
                                    break;
                                case "rot":
                                    getStyleClass().add("poor-choice");
                                    break;
                            }
                        }

                        ContextMenu contextMenu = new ContextMenu();
                        MenuItem reviewItem = new MenuItem("Review schreiben...");
                        reviewItem.setOnAction(e -> showReviewDialog(item));
                        contextMenu.getItems().add(reviewItem);
                        setContextMenu(contextMenu);
                    }
                }
            };
            return row;
        });

        // Export folder settings line
        HBox exportFolderBox = new HBox(10);
        exportFolderBox.setAlignment(Pos.CENTER_LEFT);
        exportFolderBox.getStyleClass().add("sci-fi-panel");
        exportFolderBox.setPadding(new Insets(10));
        
        Label exportLabelBtn = new Label("Export-Ordner:");
        exportLabelBtn.setStyle("-fx-text-fill: #7e889a; -fx-font-weight: bold;");
        
        exportDirField = new TextField(AppConfig.getInstance().getExportDirectory().toString());
        exportDirField.getStyleClass().add("text-input");
        exportDirField.setEditable(false);
        HBox.setHgrow(exportDirField, Priority.ALWAYS);

        browseExportBtn = new Button("...");
        browseExportBtn.getStyleClass().add("button");
        browseExportBtn.setOnAction(e -> browseExportDirectory());

        exportFolderBox.getChildren().addAll(exportLabelBtn, exportDirField, browseExportBtn);

        leftBox.getChildren().addAll(titleBox, filterBox, searchFilterBox, table, exportFolderBox);

        // ==========================================
        // RIGHT SIDE: Details TabPane
        // ==========================================
        VBox rightBox = new VBox(10);
        rightBox.getStyleClass().add("sci-fi-panel");
        rightBox.setPadding(new Insets(15));

        Label rightTitle = new Label("Strategie Leistungsanalyse & Preset-Aktionen");
        rightTitle.getStyleClass().add("sci-fi-panel-title");

        // TabPane inside Right Panel
        TabPane rightTabPane = new TabPane();
        rightTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(rightTabPane, Priority.ALWAYS);

        // --- Tab 1: Analysis & Actions ---
        VBox tab1Content = new VBox(12);
        tab1Content.setPadding(new Insets(10, 0, 0, 0));

        // Metadata grid
        GridPane metaGrid = new GridPane();
        metaGrid.setHgap(15);
        metaGrid.setVgap(8);
        metaGrid.setPadding(new Insets(10));
        metaGrid.setStyle("-fx-background-color: rgba(30, 40, 60, 0.25); -fx-background-radius: 6px; -fx-border-color: rgba(0, 229, 255, 0.15); -fx-border-radius: 6px;");

        metaGrid.add(new Label("EA Name:"), 0, 0);
        expertLabel = new Label("-");
        expertLabel.setStyle("-fx-text-fill: #00e5ff; -fx-font-weight: bold;");
        metaGrid.add(expertLabel, 1, 0);

        metaGrid.add(new Label("Symbol:"), 2, 0);
        symbolLabel = new Label("-");
        symbolLabel.setStyle("-fx-text-fill: #00e5ff; -fx-font-weight: bold;");
        metaGrid.add(symbolLabel, 3, 0);

        metaGrid.add(new Label("Zeitrahmen:"), 0, 1);
        periodLabel = new Label("-");
        periodLabel.setStyle("-fx-text-fill: #00e5ff; -fx-font-weight: bold;");
        metaGrid.add(periodLabel, 1, 1);

        metaGrid.add(new Label("Datum Lauf:"), 2, 1);
        dateLabel = new Label("-");
        dateLabel.setStyle("-fx-text-fill: #00e5ff; -fx-font-weight: bold;");
        metaGrid.add(dateLabel, 3, 1);

        // Performance Metrics Grid
        GridPane metricsGrid = new GridPane();
        metricsGrid.setHgap(20);
        metricsGrid.setVgap(10);
        metricsGrid.setPadding(new Insets(10));
        metricsGrid.setStyle("-fx-background-color: rgba(11, 19, 32, 0.5); -fx-background-radius: 8px; -fx-border-color: rgba(0, 229, 255, 0.3); -fx-border-radius: 8px; -fx-border-width: 1px;");

        profitVal = createMetricLabel();
        ddVal = createMetricLabel();
        tradesVal = createMetricLabel();
        pfVal = createMetricLabel();
        sharpeVal = createMetricLabel();
        recVal = createMetricLabel();
        unifiedScoreVal = createMetricLabel();
        kiScoreVal = createMetricLabel();

        addMetricRow(metricsGrid, "Backtest Gewinn:", profitVal, "Max Drawdown %:", ddVal, 0);
        addMetricRow(metricsGrid, "Anzahl Trades:", tradesVal, "Profit Factor:", pfVal, 1);
        addMetricRow(metricsGrid, "Sharpe Ratio:", sharpeVal, "Recovery Factor:", recVal, 2);
        addMetricRow(metricsGrid, "Unified Score:", unifiedScoreVal, "KI Stabilität:", kiScoreVal, 3);

        // Robustness Verdict Label (Banner)
        verdictLabel = new Label("URTEIL: Keine Strategie ausgewählt.");
        verdictLabel.setWrapText(true);
        verdictLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        verdictLabel.setMaxWidth(Double.MAX_VALUE);
        verdictLabel.setStyle("-fx-padding: 8px; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-text-fill: #7e889a;");

        // KI Verdict & Explanation Panel
        kiPanel = new VBox(8);
        kiPanel.setPadding(new Insets(10));
        kiPanel.setStyle("-fx-background-color: rgba(30, 40, 60, 0.25); -fx-background-radius: 6px; -fx-border-color: rgba(0, 229, 255, 0.15); -fx-border-radius: 6px;");

        Label kiHeader = new Label("🧠 KI-Analyse & Stabilitätsbericht");
        kiHeader.setStyle("-fx-text-fill: #00e5ff; -fx-font-weight: bold; -fx-font-size: 13px;");

        kiVerdictLabel = new Label("KI-Urteil: Keine Strategie ausgewählt.");
        kiVerdictLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        kiVerdictLabel.setWrapText(true);
        kiVerdictLabel.setStyle("-fx-text-fill: #7e889a;");

        kiExplanationLabel = new Label("Wählen Sie eine Strategie aus der Tabelle, um die KI-Begründung anzuzeigen.");
        kiExplanationLabel.setWrapText(true);
        kiExplanationLabel.setStyle("-fx-text-fill: #e6e9f0; -fx-font-size: 12px; -fx-line-spacing: 3px;");

        kiPanel.getChildren().addAll(kiHeader, kiVerdictLabel, kiExplanationLabel);

        // Chart setup
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Trades");
        xAxis.setForceZeroInRange(false);
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Equity ($)");
        yAxis.setForceZeroInRange(false);

        equityChart = new LineChart<>(xAxis, yAxis);
        equityChart.setCreateSymbols(false);
        equityChart.setPrefHeight(200);
        equityChart.setMinHeight(200);
        equityChart.setAnimated(false);
        equityChart.setStyle("-fx-background-color: transparent;");
        equityChart.setHorizontalGridLinesVisible(true);
        equityChart.setVerticalGridLinesVisible(false);

        // Parameters table setup
        paramTable = new TableView<>();
        paramTable.setStyle("-fx-background-color: transparent;");
        paramTable.setPrefHeight(150);
        VBox.setVgrow(paramTable, Priority.ALWAYS);

        TableColumn<EaParameter, String> paramNameCol = new TableColumn<>("Parameter Variable");
        paramNameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        paramNameCol.setPrefWidth(220);

        TableColumn<EaParameter, String> paramValCol = new TableColumn<>("Wert");
        paramValCol.setCellValueFactory(new PropertyValueFactory<>("value"));
        paramValCol.setPrefWidth(200);

        paramTable.getColumns().addAll(paramNameCol, paramValCol);

        // Action Section
        HBox actionBox = new HBox(15);
        actionBox.setAlignment(Pos.CENTER_LEFT);
        
        Label modelLabel = new Label("Nachtest-Modell:");
        modelLabel.setStyle("-fx-text-fill: #7e889a; -fx-font-weight: bold;");

        modelCombo = new ComboBox<>(FXCollections.observableArrayList("Every tick", "1 minute OHLC"));
        modelCombo.getStyleClass().add("combo-box");
        modelCombo.setValue("1 minute OHLC");

        runBacktestBtn = new Button("▶ Einzeltest starten");
        runBacktestBtn.getStyleClass().addAll("button", "button-start");
        runBacktestBtn.setOnAction(e -> runVerificationBacktest());

        exportSettingsBtn = new Button("💾 Settings exportieren");
        exportSettingsBtn.getStyleClass().add("button");
        exportSettingsBtn.setOnAction(e -> exportSettings());

        deleteStrategyBtn = new Button("❌ Löschen");
        deleteStrategyBtn.getStyleClass().add("button");
        deleteStrategyBtn.setStyle("-fx-background-color: #991b1b; -fx-text-fill: white; -fx-font-weight: bold;");
        deleteStrategyBtn.setOnAction(e -> deleteSelectedStrategy());

        progress = new ProgressBar(0);
        progress.setPrefWidth(200);

        progressLabel = new Label("");
        progressLabel.setStyle("-fx-text-fill: #cbd5e1; -fx-font-weight: bold; -fx-font-size: 12px;");

        actionBox.getChildren().addAll(modelLabel, modelCombo, runBacktestBtn, exportSettingsBtn, deleteStrategyBtn, progress, progressLabel);

        // sub-TabPane for graphic / review mode selection
        graphModeTabPane = new TabPane();
        graphModeTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        graphModeTabPane.setPrefHeight(45);
        graphModeTabPane.setMinHeight(45);
        graphModeTabPane.setMaxHeight(45);
        
        tabOriginal = new Tab("Haupttest (Original)");
        tab1y = new Tab("Review 1-Jahr");
        tab2y = new Tab("Review 2-Jahre");
        
        tab1y.setDisable(true);
        tab2y.setDisable(true);
        
        graphModeTabPane.getTabs().addAll(tabOriginal, tab1y, tab2y);
        graphModeTabPane.getSelectionModel().selectedIndexProperty().addListener((obs, oldIdx, newIdx) -> {
            if (selectedStrategy != null) {
                updateViewForMode(selectedStrategy, newIdx.intValue());
            }
        });

        tab1Content.getChildren().addAll(metaGrid, graphModeTabPane, metricsGrid, verdictLabel, kiPanel, equityChart, paramTable, actionBox);
        
        ScrollPane tab1Scroll = new ScrollPane(tab1Content);
        tab1Scroll.setFitToWidth(true);
        tab1Scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        tab1Scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        tab1Scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        Tab tab1 = new Tab("📊 Analyse & Nachtest", tab1Scroll);

        // --- Tab 2: KI Report (WebView) ---
        kiReportWebView = new WebView();
        kiReportWebView.setStyle("-fx-background-color: #1a1e28;");
        kiReportWebView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            log.info("kiReportWebView load state changed: {} -> {}", oldState, newState);
        });
        kiReportWebView.getEngine().getLoadWorker().exceptionProperty().addListener((obs, oldExc, newExc) -> {
            if (newExc != null) {
                log.error("kiReportWebView load exception: ", newExc);
            }
        });
        
        Tab tab2 = new Tab("🧠 KI Stabilitätsbericht", kiReportWebView);

        // --- Tab 3: Automatisches Review ---
        VBox tab3Content = new VBox(15);
        tab3Content.setPadding(new Insets(15));
        tab3Content.setStyle("-fx-background-color: transparent;");

        autoReviewWarningLabel = new Label("Keine Auto-Review Daten vorhanden. Bitte führen Sie das automatische Review für diese Strategie aus.");
        autoReviewWarningLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold; -fx-font-size: 13px;");
        autoReviewWarningLabel.setWrapText(true);

        autoReviewGrid = new GridPane();
        autoReviewGrid.setHgap(20);
        autoReviewGrid.setVgap(12);
        autoReviewGrid.setPadding(new Insets(15));
        autoReviewGrid.setStyle("-fx-background-color: rgba(11, 19, 32, 0.5); -fx-background-radius: 8px; -fx-border-color: rgba(0, 229, 255, 0.3); -fx-border-radius: 8px; -fx-border-width: 1px;");

        Label colHeaderMetric = new Label("Kennzahl");
        colHeaderMetric.setStyle("-fx-text-fill: #7e889a; -fx-font-weight: bold; -fx-font-size: 13px;");
        Label colHeader1y = new Label("1-Jahr Nachtest");
        colHeader1y.setStyle("-fx-text-fill: #00e5ff; -fx-font-weight: bold; -fx-font-size: 13px;");
        Label colHeader2y = new Label("2-Jahre Nachtest");
        colHeader2y.setStyle("-fx-text-fill: #00e5ff; -fx-font-weight: bold; -fx-font-size: 13px;");

        autoReviewGrid.add(colHeaderMetric, 0, 0);
        autoReviewGrid.add(colHeader1y, 1, 0);
        autoReviewGrid.add(colHeader2y, 2, 0);

        profit1y = createMetricLabel();
        profit2y = createMetricLabel();
        dd1y = createMetricLabel();
        dd2y = createMetricLabel();
        trades1y = createMetricLabel();
        trades2y = createMetricLabel();
        winRate1y = createMetricLabel();
        winRate2y = createMetricLabel();
        pf1y = createMetricLabel();
        pf2y = createMetricLabel();
        sharpe1y = createMetricLabel();
        sharpe2y = createMetricLabel();
        rec1y = createMetricLabel();
        rec2y = createMetricLabel();

        addMetricComparisonRow(autoReviewGrid, "Gewinn:", profit1y, profit2y, 1);
        addMetricComparisonRow(autoReviewGrid, "Max Drawdown %:", dd1y, dd2y, 2);
        addMetricComparisonRow(autoReviewGrid, "Trades:", trades1y, trades2y, 3);
        addMetricComparisonRow(autoReviewGrid, "Win Rate %:", winRate1y, winRate2y, 4);
        addMetricComparisonRow(autoReviewGrid, "Profit Factor:", pf1y, pf2y, 5);
        addMetricComparisonRow(autoReviewGrid, "Sharpe Ratio:", sharpe1y, sharpe2y, 6);
        addMetricComparisonRow(autoReviewGrid, "Recovery Factor:", rec1y, rec2y, 7);

        tab3Content.getChildren().addAll(autoReviewWarningLabel, autoReviewGrid);
        
        ScrollPane tab3Scroll = new ScrollPane(tab3Content);
        tab3Scroll.setFitToWidth(true);
        tab3Scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        tab3Scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        tab3Scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        Tab tab3 = new Tab("🔍 Auto-Review", tab3Scroll);

        rightTabPane.getTabs().addAll(tab1, tab2, tab3);
        rightBox.getChildren().addAll(rightTitle, rightTabPane);

        splitPane.getItems().addAll(leftBox, rightBox);
        root.setCenter(splitPane);

        // Selection Listener
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> onStrategySelected(newVal));

        // Initialize details and WebView background
        clearDetails();

        // Load initially
        Platform.runLater(this::refreshResults);
    }

    private Label createMetricLabel() {
        Label l = new Label("-");
        l.setFont(Font.font("Consolas", FontWeight.BOLD, 14));
        l.setTextFill(Color.WHITE);
        return l;
    }

    private void addMetricRow(GridPane grid, String label1, Label val1, String label2, Label val2, int row) {
        Label l1 = new Label(label1);
        l1.setStyle("-fx-text-fill: #7e889a; -fx-font-weight: bold;");
        Label l2 = new Label(label2);
        l2.setStyle("-fx-text-fill: #7e889a; -fx-font-weight: bold;");
        grid.add(l1, 0, row);
        grid.add(val1, 1, row);
        grid.add(l2, 2, row);
        grid.add(val2, 3, row);
    }

    private void browseExportDirectory() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Export-Verzeichnis wählen");
        dc.setInitialDirectory(new File(AppConfig.getInstance().getExportDirectory().toString()));
        File selected = dc.showDialog(root.getScene().getWindow());
        if (selected != null) {
            AppConfig.getInstance().setExportDirectory(selected.getAbsolutePath());
            AppConfig.getInstance().save();
            exportDirField.setText(selected.getAbsolutePath());
            logView.log("INFO", "Export-Verzeichnis geändert auf: " + selected.getAbsolutePath());
        }
    }

    public void refreshResults() {
        allLoadedStrategies.clear();
        tableItems.clear();

        List<HistoryRun> runs = dbManager.getRunsByType("Workflow");
        Gson gson = buildGson();

        // Load reviews from DB
        List<com.backtester.database.DatabaseManager.StrategyReview> reviewsList = dbManager.getAllStrategyReviews();
        Map<String, com.backtester.database.DatabaseManager.StrategyReview> reviewMap = new HashMap<>();
        for (com.backtester.database.DatabaseManager.StrategyReview r : reviewsList) {
            String key = r.getExpertName() + "|" + r.getSymbol() + "|" + r.getPeriod() + "|" + r.getRunTimestamp() + "|" + r.getPassNumber();
            reviewMap.put(key, r);
        }

        for (HistoryRun run : runs) {
            if (run.getResultJson() == null || run.getResultJson().trim().isEmpty()) {
                continue;
            }
            try {
                Map<String, Object> stateMap = gson.fromJson(run.getResultJson(), Map.class);
                if (stateMap == null) continue;

                String expert = (String) stateMap.get("expert_name");
                String symbol = (String) stateMap.get("symbol");
                String period = (String) stateMap.get("period");
                LocalDate fromDate = null;
                if (stateMap.get("from_date") != null) {
                    fromDate = LocalDate.parse((String) stateMap.get("from_date"));
                }
                LocalDate toDate = null;
                if (stateMap.get("to_date") != null) {
                    toDate = LocalDate.parse((String) stateMap.get("to_date"));
                }
                int deposit = 10000;
                if (stateMap.get("deposit") != null) {
                    deposit = ((Number) stateMap.get("deposit")).intValue();
                }
                String currency = (String) stateMap.get("currency");
                if (currency == null) currency = "USD";
                String leverage = (String) stateMap.get("leverage");
                if (leverage == null) leverage = "1:100";
                int tickModel = 1;
                if (stateMap.get("tick_model") != null) {
                    tickModel = ((Number) stateMap.get("tick_model")).intValue();
                }

                // Parse base parameters
                List<EaParameter> baseParams = new ArrayList<>();
                String eaParamsJson = (String) stateMap.get("ea_parameters_json");
                if (eaParamsJson != null && !eaParamsJson.isEmpty()) {
                    java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<List<EaParameter>>(){}.getType();
                    baseParams = gson.fromJson(eaParamsJson, listType);
                }

                // Parse sensitivity results
                List<SensitivityResult> sensResults = new ArrayList<>();
                String sensJson = (String) stateMap.get("sensitivity_results_json");
                if (sensJson != null && !sensJson.isEmpty()) {
                    java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<List<SensitivityResult>>(){}.getType();
                    sensResults = gson.fromJson(sensJson, listType);
                }

                // Parse final selected passes
                List<CombinedPass> finalPasses = new ArrayList<>();
                String finalPassesJson = (String) stateMap.get("final_selected_passes_json");
                if (finalPassesJson != null && !finalPassesJson.isEmpty()) {
                    java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<List<CombinedPass>>(){}.getType();
                    finalPasses = gson.fromJson(finalPassesJson, listType);
                }

                String kiReportText = (String) stateMap.get("ki_report_text");
                if (kiReportText == null) kiReportText = "";

                for (CombinedPass cp : finalPasses) {
                    int kiScore = -1;
                    double worstCv = 0.0;
                    for (SensitivityResult sr : sensResults) {
                        if (sr.getOriginalPass() != null && sr.getOriginalPass().getPassNumber() == cp.getPassNumber()) {
                            String kiRes = sr.getKiResult();
                            if (kiRes != null && !kiRes.isEmpty()) {
                                try {
                                    kiScore = Integer.parseInt(kiRes.trim());
                                } catch (NumberFormatException ignored) {}
                            }

                            worstCv = sr.getOverallCV();
                            if (sr.hasForwardCV() && sr.getOverallCVFw() > worstCv) {
                                worstCv = sr.getOverallCVFw();
                            }
                            break;
                        }
                    }

                    ControllingStrategy cs = new ControllingStrategy(
                        expert, symbol, period, fromDate, toDate, deposit, currency, leverage, tickModel, baseParams, cp, run.getTimestamp(), kiScore, worstCv, kiReportText, run.getId()
                    );
                    String key = cs.getExpert() + "|" + cs.getSymbol() + "|" + cs.getPeriod() + "|" + cs.getRunTimestamp() + "|" + cs.getPassNumber();
                    com.backtester.database.DatabaseManager.StrategyReview rev = reviewMap.get(key);
                    if (rev != null) {
                        cs.setReviewText(rev.getReviewText());
                        cs.setColorRating(rev.getColorRating());
                    }
                    allLoadedStrategies.add(cs);
                }

            } catch (Exception e) {
                log.error("Failed to parse workflow run history entry ID " + run.getId(), e);
            }
        }

        applyFilter();
    }

    private void applyFilter() {
        tableItems.clear();
        boolean filterBest = bestFilterBtn.isSelected();
        String keyword = keywordField.getText() != null ? keywordField.getText().trim().toLowerCase() : "";
        String dateMode = dateModeCombo.getValue();
        LocalDate targetDate = null;

        if ("Heute".equals(dateMode)) {
            targetDate = LocalDate.now();
        } else if ("Gestern".equals(dateMode)) {
            targetDate = LocalDate.now().minusDays(1);
        } else if ("Auswählen...".equals(dateMode)) {
            targetDate = customDatePicker.getValue();
        }

        for (ControllingStrategy cs : allLoadedStrategies) {
            // 1. Best filter (KI Score >= 70)
            if (filterBest && cs.getKiScore() < 70) {
                continue;
            }

            // 2. Keyword filter
            if (!keyword.isEmpty()) {
                String expName = EaParameterManager.extractEaBaseName(cs.getExpert()).toLowerCase();
                String sym = cs.getSymbol().toLowerCase();
                String tf = cs.getPeriod().toLowerCase();
                String pass = String.valueOf(cs.getPassNumber());
                if (!expName.contains(keyword) && !sym.contains(keyword) && !tf.contains(keyword) && !pass.contains(keyword)) {
                    continue;
                }
            }

            // 3. Date filter
            if (targetDate != null) {
                LocalDate runDate = new java.util.Date(cs.getRunTimestamp())
                    .toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate();
                if (!runDate.equals(targetDate)) {
                    continue;
                }
            }

            tableItems.add(cs);
        }
        table.refresh();
    }

    private void onStrategySelected(ControllingStrategy selected) {
        if (selected == null) {
            clearDetails();
            return;
        }

        this.selectedStrategy = selected;

        // Set Labels
        expertLabel.setText(EaParameterManager.extractEaBaseName(selected.getExpert()));
        symbolLabel.setText(selected.getSymbol());
        periodLabel.setText(selected.getPeriod());
        dateLabel.setText(df.format(new Date(selected.getRunTimestamp())));

        // Check for automatic review data to enable/disable tabs
        com.backtester.database.DatabaseManager.AutomaticReview autoReview = dbManager.getAutomaticReview(
            selected.getExpert(),
            selected.getSymbol(),
            selected.getPeriod(),
            selected.getRunTimestamp(),
            selected.getPassNumber()
        );

        if (autoReview != null) {
            tab1y.setDisable(false);
            tab2y.setDisable(false);
        } else {
            tab1y.setDisable(true);
            tab2y.setDisable(true);
            graphModeTabPane.getSelectionModel().select(tabOriginal);
        }

        // Update Tab 3 Metrics Comparison Table
        if (autoReview != null) {
            autoReviewWarningLabel.setVisible(false);
            autoReviewWarningLabel.setManaged(false);
            autoReviewGrid.setVisible(true);
            autoReviewGrid.setManaged(true);

            Gson gson = buildGson();
            BacktestResult res1y = gson.fromJson(autoReview.getResult1yJson(), BacktestResult.class);
            BacktestResult res2y = gson.fromJson(autoReview.getResult2yJson(), BacktestResult.class);

            if (res1y != null) {
                profit1y.setText(String.format(Locale.US, "%.2f %s", res1y.getTotalProfit(), selected.getCurrency()));
                profit1y.setTextFill(res1y.getTotalProfit() >= 0 ? Color.web("#22c55e") : Color.web("#ef4444"));

                dd1y.setText(String.format(Locale.US, "%.2f%%", res1y.getMaxDrawdown()));
                dd1y.setTextFill(res1y.getMaxDrawdown() > 25 ? Color.web("#ef4444") : Color.web("#22c55e"));

                trades1y.setText(String.valueOf(res1y.getTotalTrades()));
                winRate1y.setText(String.format(Locale.US, "%.2f%%", res1y.getWinRate()));
                pf1y.setText(String.format(Locale.US, "%.2f", res1y.getProfitFactor()));
                sharpe1y.setText(String.format(Locale.US, "%.2f", res1y.getSharpeRatio()));
                rec1y.setText(String.format(Locale.US, "%.2f", res1y.getRecoveryFactor()));
            } else {
                clearMetrics1y();
            }

            if (res2y != null) {
                profit2y.setText(String.format(Locale.US, "%.2f %s", res2y.getTotalProfit(), selected.getCurrency()));
                profit2y.setTextFill(res2y.getTotalProfit() >= 0 ? Color.web("#22c55e") : Color.web("#ef4444"));

                dd2y.setText(String.format(Locale.US, "%.2f%%", res2y.getMaxDrawdown()));
                dd2y.setTextFill(res2y.getMaxDrawdown() > 25 ? Color.web("#ef4444") : Color.web("#22c55e"));

                trades2y.setText(String.valueOf(res2y.getTotalTrades()));
                winRate2y.setText(String.format(Locale.US, "%.2f%%", res2y.getWinRate()));
                pf2y.setText(String.format(Locale.US, "%.2f", res2y.getProfitFactor()));
                sharpe2y.setText(String.format(Locale.US, "%.2f", res2y.getSharpeRatio()));
                rec2y.setText(String.format(Locale.US, "%.2f", res2y.getRecoveryFactor()));
            } else {
                clearMetrics2y();
            }
        } else {
            autoReviewWarningLabel.setVisible(true);
            autoReviewWarningLabel.setManaged(true);
            autoReviewGrid.setVisible(false);
            autoReviewGrid.setManaged(false);
            clearMetrics1y();
            clearMetrics2y();
        }

        // Parameters table
        List<EaParameter> finalParams = getStrategyParameters(selected.getBaseParameters(), selected.combinedPass);
        paramTable.getItems().setAll(finalParams);

        // Update KI Report WebView
        String reportMarkdown = selected.getKiReportText();
        log.info("onStrategySelected: selected pass={}, kiReportText length={}", selected.getPassNumber(), reportMarkdown != null ? reportMarkdown.length() : 0);
        if (reportMarkdown == null || reportMarkdown.trim().isEmpty()) {
            kiReportWebView.getEngine().loadContent("<html><body style='background-color:#1a1e28; color:#7e889a; font-family:Segoe UI; padding:20px;'>Kein KI-Stabilitätsbericht für diesen Workflow-Lauf vorhanden.</body></html>");
        } else {
            try {
                Parser parser = Parser.builder().extensions(Collections.singletonList(TablesExtension.create())).build();
                Node document = parser.parse(reportMarkdown);
                HtmlRenderer renderer = HtmlRenderer.builder().extensions(Collections.singletonList(TablesExtension.create())).build();
                String rawHtml = renderer.render(document);
                
                // Highlight selected pass in the HTML table and paragraph
                String passStr = String.valueOf(selected.getPassNumber());
                String rowPattern = "(?is)<tr>(\\s*<td>\\s*" + passStr + "\\s*</td>)";
                rawHtml = rawHtml.replaceAll(rowPattern, "<tr class=\"highlighted-row\">$1");
                
                String pPattern = "(?is)<p>(\\s*<strong>\\s*Pass\\s+" + passStr + "\\b)";
                rawHtml = rawHtml.replaceAll(pPattern, "<p class=\"highlighted-paragraph\">$1");

                String css = "<style>"
                        + "body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #1a1e28; color: #e6e9f0; padding: 20px; line-height: 1.6; }"
                        + "h1, h2, h3 { color: #00e5ff; border-bottom: 1px solid #3e4555; padding-bottom: 5px; margin-top: 25px; }"
                        + "blockquote { border-left: 4px solid #00e5ff; margin-left: 0; padding-left: 15px; color: #b4bac8; }"
                        + "table { border-collapse: collapse; width: 100%; margin-bottom: 20px; }"
                        + "th, td { border: 1px solid #3e4555; padding: 8px 12px; text-align: left; }"
                        + "th { background-color: #14161c; color: #00e5ff; }"
                        + "tr:nth-child(even) { background-color: #14161c; }"
                        + ".highlighted-row { background-color: rgba(234, 179, 8, 0.25) !important; color: #ffd740 !important; font-weight: bold; }"
                        + ".highlighted-row td { background-color: rgba(234, 179, 8, 0.25) !important; border-color: #ffd740 !important; }"
                        + ".highlighted-paragraph { background-color: rgba(234, 179, 8, 0.12) !important; border-left: 4px solid #ffd740 !important; padding: 10px !important; border-radius: 4px; }"
                        + "</style>";
                kiReportWebView.getEngine().loadContent("<html><head>" + css + "</head><body>" + rawHtml + "</body></html>");
            } catch (Exception ex) {
                kiReportWebView.getEngine().loadContent("<html><body style='background-color:#1a1e28; color:#ff3b30; font-family:Segoe UI; padding:20px;'>Fehler beim Rendern des KI-Berichts: " + ex.getMessage() + "</body></html>");
            }
        }

        // Dynamically update metrics and chart based on currently selected graph Mode Tab
        updateViewForMode(selected, graphModeTabPane.getSelectionModel().getSelectedIndex());
    }

    private void clearDetails() {
        selectedStrategy = null;
        tab1y.setDisable(true);
        tab2y.setDisable(true);
        graphModeTabPane.getSelectionModel().select(tabOriginal);

        expertLabel.setText("-");
        symbolLabel.setText("-");
        periodLabel.setText("-");
        dateLabel.setText("-");

        profitVal.setText("-"); profitVal.setTextFill(Color.WHITE);
        ddVal.setText("-"); ddVal.setTextFill(Color.WHITE);
        tradesVal.setText("-");
        pfVal.setText("-");
        sharpeVal.setText("-");
        recVal.setText("-");
        unifiedScoreVal.setText("-"); unifiedScoreVal.setTextFill(Color.WHITE);
        kiScoreVal.setText("-"); kiScoreVal.setTextFill(Color.WHITE);

        verdictLabel.setText("URTEIL: Keine Strategie ausgewählt.");
        verdictLabel.setStyle("-fx-padding: 8px; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-text-fill: #7e889a;");
        verdictLabel.setVisible(true);
        verdictLabel.setManaged(true);

        kiVerdictLabel.setText("KI-Urteil: Keine Strategie ausgewählt.");
        kiVerdictLabel.setStyle("-fx-text-fill: #7e889a;");
        kiExplanationLabel.setText("Wählen Sie eine Strategie aus der Tabelle, um die KI-Begründung anzuzeigen.");
        kiPanel.setVisible(true);
        kiPanel.setManaged(true);

        equityChart.getData().clear();
        paramTable.getItems().clear();
        kiReportWebView.getEngine().loadContent("<html><body style='background-color:#1a1e28;'></body></html>");
        
        autoReviewWarningLabel.setVisible(true);
        autoReviewWarningLabel.setManaged(true);
        autoReviewGrid.setVisible(false);
        autoReviewGrid.setManaged(false);
        clearMetrics1y();
        clearMetrics2y();
    }

    private List<EaParameter> getStrategyParameters(List<EaParameter> baseParams, CombinedPass cp) {
        List<EaParameter> finalParams = new ArrayList<>();
        if (baseParams == null) return finalParams;
        for (EaParameter base : baseParams) {
            EaParameter p = new EaParameter();
            p.setName(base.getName());
            p.setStringType(base.isStringType());
            p.setSection(base.getSection());
            String passVal = cp.getBacktestPass().getParameter(base.getName());
            if (passVal != null && !passVal.isEmpty()) {
                p.setValue(passVal);
            } else {
                p.setValue(base.getValue());
            }
            p.setOptimizeEnabled(false);
            finalParams.add(p);
        }
        return finalParams;
    }

    private void exportSettings() {
        ControllingStrategy selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Bitte wählen Sie zuerst eine Strategie aus der Tabelle aus.");
            alert.show();
            return;
        }

        try {
            String eaName = EaParameterManager.extractEaBaseName(selected.getExpert());
            String tf = selected.getPeriod().replaceAll("[^a-zA-Z0-9_.-]", "_");
            String sym = selected.getSymbol().replaceAll("[^a-zA-Z0-9_.-]", "_");
            String filename = String.format("%s_%s_%s_Pass%d.set", eaName, sym, tf, selected.getPassNumber());
            
            Path destPath = Paths.get(AppConfig.getInstance().getExportDirectory().toString()).resolve(filename);
            
            List<EaParameter> finalParams = getStrategyParameters(selected.getBaseParameters(), selected.combinedPass);
            eaParamManager.writeSetFile(destPath, finalParams, eaName);

            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Die Parameter (.set) wurden erfolgreich exportiert:\n" + destPath.toAbsolutePath().toString());
            alert.setTitle("Export erfolgreich");
            alert.setHeaderText(null);
            alert.show();
            logView.log("INFO", "Settings exportiert nach: " + destPath);

        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Fehler beim Exportieren der Parameter:\n" + ex.getMessage());
            alert.show();
            logView.log("ERROR", "Settings-Export fehlgeschlagen: " + ex.getMessage());
        }
    }

    private void runVerificationBacktest() {
        ControllingStrategy selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Bitte wählen Sie zuerst eine Strategie aus der Tabelle aus.");
            alert.show();
            return;
        }

        // Safety check if workflow automator is running
        if (workflowView != null && workflowView.isWorkflowRunning()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("MetaTrader belegt");
            alert.setHeaderText("Workflow läuft gerade");
            alert.setContentText("Ein automatischer Workflow wird aktuell ausgeführt. Bitte warten Sie, bis der Workflow beendet ist, da MetaTrader nur eine portable Instanz gleichzeitig unterstützt.");
            alert.show();
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Backtest starten");
        confirm.setHeaderText("Einzeltest im MetaTrader 5 ausführen?");
        confirm.setContentText(String.format("Möchten Sie einen Nachtest für %s (%s, %s, Pass #%d) starten?", 
            EaParameterManager.extractEaBaseName(selected.getExpert()), selected.getSymbol(), selected.getPeriod(), selected.getPassNumber()));
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            triggerBacktestTask(selected);
        }
    }

    private void triggerBacktestTask(ControllingStrategy selected) {
        // UI State
        runBacktestBtn.setDisable(true);
        exportSettingsBtn.setDisable(true);
        deleteStrategyBtn.setDisable(true);
        browseExportBtn.setDisable(true);
        bestFilterBtn.setDisable(true);
        allFilterBtn.setDisable(true);
        keywordField.setDisable(true);
        dateModeCombo.setDisable(true);
        customDatePicker.setDisable(true);
        autoReviewBtn.setDisable(true);
        table.setDisable(true);
        progress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progressLabel.setText("Einzeltest läuft...");

        logView.log("INFO", "Starte manuellen Verifikations-Backtest für Pass " + selected.getPassNumber());

        Path mt5Dir = AppConfig.getInstance().getMt5InstallDir();
        Path presetsDir = mt5Dir.resolve("MQL5").resolve("Profiles").resolve("Tester");
        String eaName = EaParameterManager.extractEaBaseName(selected.getExpert());
        String presetFileName = "Backtester_" + eaName + ".set";
        Path destFile = presetsDir.resolve(presetFileName);

        // Write parameters to presets folder
        List<EaParameter> finalParams = getStrategyParameters(selected.getBaseParameters(), selected.combinedPass);
        eaParamManager.writeSetFile(destFile, finalParams, eaName);

        // Configure Backtest
        BacktestConfig btConfig = new BacktestConfig();
        btConfig.setExpert(selected.getExpert());
        btConfig.setSymbol(selected.getSymbol());
        btConfig.setPeriod(selected.getPeriod());
        
        // Simulation model
        int modelIdx = 1; // Default OHLC
        if ("Every tick".equals(modelCombo.getValue())) {
            modelIdx = 0;
        }
        btConfig.setModel(modelIdx);
        
        btConfig.setFromDate(selected.getFromDate());
        btConfig.setToDate(selected.getToDate());
        btConfig.setDeposit(selected.getDeposit());
        btConfig.setCurrency(selected.getCurrency());
        btConfig.setLeverage(selected.getLeverage());
        btConfig.setShutdownTerminal(false);
        btConfig.setUseVirtualDesktop(false);
        btConfig.setExpertParameters(presetFileName);

        BacktestRunner runner = new BacktestRunner();
        runner.setLogCallback(msg -> Platform.runLater(() -> logView.log("INFO", msg)));

        runningBacktestTask = new Task<>() {
            @Override
            protected BacktestResult call() throws Exception {
                return runner.runBacktest(btConfig);
            }
        };

        runningBacktestTask.setOnSucceeded(e -> {
            BacktestResult res = runningBacktestTask.getValue();
            if (res != null) {
                if (res.isSuccess()) {
                    logView.log("INFO", "Nachtest erfolgreich abgeschlossen.");
                    // Persist run
                    try {
                        String fullJson = new Gson().toJson(res);
                        int generatedId = DatabaseManager.getInstance().saveRun(
                            "BACKTEST",
                            res.getExpert(),
                            System.currentTimeMillis(),
                            fullJson,
                            res.getOutputDirectory()
                        );
                        res.setDbId(generatedId);
                    } catch (Exception ex) {
                        logView.log("ERROR", "Nachtest-Resultat konnte nicht in DB gespeichert werden: " + ex.getMessage());
                    }

                    // Open popup confirmation
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Nachtest beendet");
                    alert.setHeaderText("Verifikations-Backtest beendet");
                    alert.setContentText("Der Backtest wurde erfolgreich beendet. Möchten Sie den Bericht jetzt anzeigen?");
                    ButtonType viewReportBtn = new ButtonType("Bericht anzeigen", ButtonBar.ButtonData.OK_DONE);
                    ButtonType closeBtn = new ButtonType("Schließen", ButtonBar.ButtonData.CANCEL_CLOSE);
                    alert.getButtonTypes().setAll(viewReportBtn, closeBtn);

                    alert.showAndWait().ifPresent(response -> {
                        if (response == viewReportBtn) {
                            Platform.runLater(() -> {
                                try {
                                    javax.swing.SwingUtilities.invokeLater(() -> {
                                        com.backtester.ui.ReportViewerDialog.showForDirectory(null, res.getOutputDirectory());
                                    });
                                } catch (Exception ex) {
                                    logView.log("ERROR", "HTML Report konnte nicht geöffnet werden: " + ex.getMessage());
                                }
                            });
                        }
                    });

                } else {
                    logView.log("WARN", "Nachtest abgeschlossen mit Fehlern: " + res.getMessage());
                    Alert alert = new Alert(Alert.AlertType.WARNING, "Nachtest fehlgeschlagen oder abgebrochen:\n" + res.getMessage());
                    alert.show();
                }
            }
            resetUIState();
        });

        runningBacktestTask.setOnFailed(e -> {
            logView.log("ERROR", "Nachtest fehlgeschlagen: " + runningBacktestTask.getException().getMessage());
            Alert alert = new Alert(Alert.AlertType.ERROR, "Fehler beim Ausführen des Nachtests:\n" + runningBacktestTask.getException().getMessage());
            alert.show();
            resetUIState();
        });

        runningBacktestTask.setOnCancelled(e -> {
            logView.log("WARN", "Nachtest abgebrochen.");
            resetUIState();
        });

        Thread th = new Thread(runningBacktestTask);
        th.setDaemon(true);
        th.start();
    }

    private void resetUIState() {
        runBacktestBtn.setDisable(false);
        exportSettingsBtn.setDisable(false);
        deleteStrategyBtn.setDisable(false);
        browseExportBtn.setDisable(false);
        bestFilterBtn.setDisable(false);
        allFilterBtn.setDisable(false);
        keywordField.setDisable(false);
        dateModeCombo.setDisable(false);
        customDatePicker.setDisable(false);
        autoReviewBtn.setDisable(false);
        table.setDisable(false);
        progress.progressProperty().unbind();
        progress.setProgress(0);
        progressLabel.textProperty().unbind();
        progressLabel.setText("");
    }

    public void bindTab(Tab tab) {
        updateTabTitle(tab, tableItems.size());
        tableItems.addListener((javafx.collections.ListChangeListener<ControllingStrategy>) c -> {
            updateTabTitle(tab, tableItems.size());
        });
    }

    private void updateTabTitle(Tab tab, int count) {
        Platform.runLater(() -> tab.setText("📊 Controlling (" + count + ")"));
    }

    public BorderPane getView() {
        return root;
    }

    private static Gson buildGson() {
        return new GsonBuilder()
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

    private void showReviewDialog(ControllingStrategy selected) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Review schreiben — Pass " + selected.getPassNumber());
        dialog.setHeaderText("Fügen Sie ein Review und eine Bewertung hinzu.");

        // Apply style sheets
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/css/antigravity.css").toExternalForm());
        dialog.getDialogPane().setStyle("-fx-background-color: #0b0d13;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 20, 20));

        TextArea reviewArea = new TextArea();
        reviewArea.setPromptText("Review Text...");
        reviewArea.setPrefRowCount(3);
        reviewArea.setWrapText(true);
        reviewArea.setPrefWidth(300);
        if (selected.getReviewText() != null) {
            reviewArea.setText(selected.getReviewText());
        }

        ComboBox<String> colorCombo = new ComboBox<>(FXCollections.observableArrayList(
            "Dunkel grün", "Grün", "Gelb", "Orange", "Rot"
        ));
        
        String curColor = selected.getColorRating();
        if (curColor != null && !curColor.isEmpty()) {
            if ("dunkel grün".equalsIgnoreCase(curColor) || "dunkelgrün".equalsIgnoreCase(curColor)) {
                colorCombo.setValue("Dunkel grün");
            } else {
                colorCombo.setValue(capitalize(curColor));
            }
        } else {
            colorCombo.setValue("Grün");
        }

        Label reviewLabel = new Label("Review:");
        reviewLabel.setStyle("-fx-text-fill: #b4bac8; -fx-font-weight: bold;");
        
        Label ratingLabel = new Label("Bewertung:");
        ratingLabel.setStyle("-fx-text-fill: #b4bac8; -fx-font-weight: bold;");

        grid.add(reviewLabel, 0, 0);
        grid.add(reviewArea, 1, 0);
        grid.add(ratingLabel, 0, 1);
        grid.add(colorCombo, 1, 1);

        dialog.getDialogPane().setContent(grid);

        ButtonType saveButtonType = new ButtonType("Speichern", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        // Styling for combobox and textarea inside dialog
        reviewArea.setStyle("-fx-control-inner-background: #14161c; -fx-text-fill: #e6e9f0; -fx-border-color: #3e4555;");
        colorCombo.setStyle("-fx-background-color: #14161c; -fx-text-fill: #e6e9f0; -fx-border-color: #3e4555;");

        // Request focus on the review text area by default
        Platform.runLater(reviewArea::requestFocus);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == saveButtonType) {
            String text = reviewArea.getText().trim();
            String rating = colorCombo.getValue().toLowerCase();
            
            // Save to DB
            dbManager.saveStrategyReview(
                selected.getExpert(),
                selected.getSymbol(),
                selected.getPeriod(),
                selected.getRunTimestamp(),
                selected.getPassNumber(),
                text,
                rating
            );

            // Update in-memory model
            selected.setReviewText(text);
            selected.setColorRating(rating);

            // Refresh table
            table.refresh();
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return Character.toUpperCase(str.charAt(0)) + str.substring(1).toLowerCase();
    }

    private void deleteSelectedStrategy() {
        ControllingStrategy selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Bitte wählen Sie zuerst eine Strategie aus der Tabelle aus.");
            alert.show();
            return;
        }

        // Sicherheitsabfrage (Confirmation Dialog)
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Strategie löschen");
        confirm.setHeaderText("Strategie löschen bestätigen");
        confirm.setContentText("Sind Sie sicher, dass Sie diese Strategie (Pass " + selected.getPassNumber() + ") unwiderruflich aus der Datenbank löschen möchten?");
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                int runDbId = selected.getRunDbId();
                int passNumber = selected.getPassNumber();

                HistoryRun run = dbManager.getRunById(runDbId);
                if (run != null) {
                    Gson gson = buildGson();
                    Map<String, Object> stateMap = gson.fromJson(run.getResultJson(), Map.class);
                    if (stateMap != null) {
                        String finalPassesJson = (String) stateMap.get("final_selected_passes_json");
                        if (finalPassesJson != null && !finalPassesJson.isEmpty()) {
                            java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<List<CombinedPass>>(){}.getType();
                            List<CombinedPass> finalPasses = gson.fromJson(finalPassesJson, listType);
                            
                            // Remove the matching pass
                            int beforeSize = finalPasses.size();
                            finalPasses.removeIf(cp -> cp.getPassNumber() == passNumber);
                            
                            if (finalPasses.size() < beforeSize) {
                                // Delete strategy review from DB
                                dbManager.deleteStrategyReview(
                                    selected.getExpert(),
                                    selected.getSymbol(),
                                    selected.getPeriod(),
                                    selected.getRunTimestamp(),
                                    selected.getPassNumber()
                                );
                                // Delete automatic review from DB
                                dbManager.deleteAutomaticReview(
                                    selected.getExpert(),
                                    selected.getSymbol(),
                                    selected.getPeriod(),
                                    selected.getRunTimestamp(),
                                    selected.getPassNumber()
                                );

                                if (finalPasses.isEmpty()) {
                                    // No more strategies left in this workflow run - delete the entire run
                                    dbManager.deleteRun(runDbId);
                                    logView.log("INFO", "Workflow-Lauf ID " + runDbId + " vollständig gelöscht, da keine Strategien mehr übrig waren.");
                                } else {
                                    // Update final_selected_passes_json inside stateMap
                                    stateMap.put("final_selected_passes_json", gson.toJson(finalPasses));
                                    String updatedResultJson = gson.toJson(stateMap);
                                    dbManager.updateRunResultJson(runDbId, updatedResultJson);
                                    logView.log("INFO", "Strategie Pass " + passNumber + " aus Workflow-Lauf ID " + runDbId + " gelöscht.");
                                }
                            }
                        }
                    }
                }

                // Reload data and clear right side details
                refreshResults();
                clearDetails();

                Alert success = new Alert(Alert.AlertType.INFORMATION, "Die Strategie wurde erfolgreich gelöscht.");
                success.show();

            } catch (Exception ex) {
                log.error("Fehler beim Löschen der Strategie", ex);
                Alert error = new Alert(Alert.AlertType.ERROR, "Fehler beim Löschen der Strategie:\n" + ex.getMessage());
                error.show();
            }
        }
    }

    // ==========================================
    // Inner Helper Class: ControllingStrategy
    // ==========================================
    public static class ControllingStrategy {
        private final String expert;
        private final String symbol;
        private final String period;
        private final LocalDate fromDate;
        private final LocalDate toDate;
        private final int deposit;
        private final String currency;
        private final String leverage;
        private final int tickModel;
        private final List<EaParameter> baseParameters;
        
        private final CombinedPass combinedPass;
        private final long runTimestamp;
        private final int kiScore;
        private final double worstCv;
        private final String kiReportText;
        private final int runDbId;

        private String reviewText;
        private String colorRating;

        public ControllingStrategy(String expert, String symbol, String period, 
                                   LocalDate fromDate, LocalDate toDate, int deposit, 
                                   String currency, String leverage, int tickModel, 
                                   List<EaParameter> baseParameters,
                                   CombinedPass combinedPass, long runTimestamp, int kiScore,
                                   double worstCv, String kiReportText, int runDbId) {
            this.expert = expert;
            this.symbol = symbol;
            this.period = period;
            this.fromDate = fromDate;
            this.toDate = toDate;
            this.deposit = deposit;
            this.currency = currency;
            this.leverage = leverage;
            this.tickModel = tickModel;
            this.baseParameters = baseParameters;
            this.combinedPass = combinedPass;
            this.runTimestamp = runTimestamp;
            this.kiScore = kiScore;
            this.worstCv = worstCv;
            this.kiReportText = kiReportText;
            this.runDbId = runDbId;
            this.reviewText = "";
            this.colorRating = "";
        }

        public String getExpert() { return expert; }
        public String getSymbol() { return symbol; }
        public String getPeriod() { return period; }
        public LocalDate getFromDate() { return fromDate; }
        public LocalDate getToDate() { return toDate; }
        public int getDeposit() { return deposit; }
        public String getCurrency() { return currency; }
        public String getLeverage() { return leverage; }
        public int getTickModel() { return tickModel; }
        public List<EaParameter> getBaseParameters() { return baseParameters; }
        
        public CombinedPass getCombinedPass() { return combinedPass; }
        public long getRunTimestamp() { return runTimestamp; }
        public int getKiScore() { return kiScore; }
        public double getWorstCv() { return worstCv; }
        public String getKiReportText() { return kiReportText; }
        public int getRunDbId() { return runDbId; }

        public int getPassNumber() { return combinedPass.getPassNumber(); }
        public double getBtProfit() { return combinedPass.getBtProfit(); }
        public double getBtDd() { return combinedPass.getBtDd(); }
        public int getBtTrades() { return combinedPass.getBtTrades(); }
        public double getScore() { return combinedPass.getScore(); }

        public String getReviewText() { return reviewText; }
        public void setReviewText(String reviewText) { this.reviewText = reviewText; }
        public String getColorRating() { return colorRating; }
        public void setColorRating(String colorRating) { this.colorRating = colorRating; }
    }

    // ==========================================
    // KI Report Parser Helper
    // ==========================================
    public static class KiStrategyReport {
        public String status = "";
        public String score = "";
        public String curves = "";
        public String conclusion = "";
        public String explanation = "";
    }

    public static KiStrategyReport parseKiReportForPass(String reportText, int passNumber) {
        KiStrategyReport rep = new KiStrategyReport();
        if (reportText == null || reportText.trim().isEmpty()) {
            return rep;
        }

        String passStr = String.valueOf(passNumber);
        String[] lines = reportText.split("\\r?\\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                String[] parts = trimmed.split("\\|");
                if (parts.length >= 4) {
                    if (parts[1].trim().equals(passStr)) {
                        rep.status = parts[2].trim();
                        rep.score = parts[3].trim();
                        if (parts.length > 8) {
                            rep.curves = parts[8].trim();
                        }
                        if (parts.length > 9) {
                            rep.conclusion = parts[9].trim();
                        }
                        break;
                    }
                }
            }
        }

        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("|") && (trimmed.contains("Pass " + passStr) || trimmed.contains("Pass: " + passStr) || trimmed.contains("Pass: **" + passStr))) {
                rep.explanation = trimmed;
                break;
            }
        }

        return rep;
    }

    private void clearMetrics1y() {
        profit1y.setText("-"); profit1y.setTextFill(Color.WHITE);
        dd1y.setText("-"); dd1y.setTextFill(Color.WHITE);
        trades1y.setText("-");
        winRate1y.setText("-");
        pf1y.setText("-");
        sharpe1y.setText("-");
        rec1y.setText("-");
    }

    private void clearMetrics2y() {
        profit2y.setText("-"); profit2y.setTextFill(Color.WHITE);
        dd2y.setText("-"); dd2y.setTextFill(Color.WHITE);
        trades2y.setText("-");
        winRate2y.setText("-");
        pf2y.setText("-");
        sharpe2y.setText("-");
        rec2y.setText("-");
    }

    private void addMetricComparisonRow(GridPane grid, String label, Label val1, Label val2, int row) {
        Label l = new Label(label);
        l.setStyle("-fx-text-fill: #7e889a; -fx-font-weight: bold;");
        grid.add(l, 0, row);
        grid.add(val1, 1, row);
        grid.add(val2, 2, row);
    }

    private void startAutomaticReview() {
        // Safety check if workflow automator is running
        if (workflowView != null && workflowView.isWorkflowRunning()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("MetaTrader belegt");
            alert.setHeaderText("Workflow läuft gerade");
            alert.setContentText("Ein automatischer Workflow wird aktuell ausgeführt. Bitte warten Sie, bis der Workflow beendet ist, da MetaTrader nur eine portable Instanz gleichzeitig unterstützt.");
            alert.show();
            return;
        }

        if (tableItems.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Keine Strategien in der Liste vorhanden.");
            alert.show();
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Automatisches Review starten");
        confirm.setHeaderText("Automatisches Review ausführen?");
        confirm.setContentText(String.format("Möchten Sie das automatische Review für alle %d Strategien in der Liste starten?\n" +
            "Für jede Strategie wird ein 1-Jahres und ein 2-Jahres Nachtest mit Tick-Simulation im Hintergrund ausgeführt.\n" +
            "Dies kann einige Zeit in Anspruch nehmen.", tableItems.size()));
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            triggerAutomaticReviewTask();
        }
    }

    private void triggerAutomaticReviewTask() {
        // UI State
        runBacktestBtn.setDisable(true);
        exportSettingsBtn.setDisable(true);
        deleteStrategyBtn.setDisable(true);
        browseExportBtn.setDisable(true);
        bestFilterBtn.setDisable(true);
        allFilterBtn.setDisable(true);
        keywordField.setDisable(true);
        dateModeCombo.setDisable(true);
        customDatePicker.setDisable(true);
        autoReviewBtn.setDisable(true);
        table.setDisable(true);
        progress.setProgress(0);

        logView.log("INFO", "Starte automatisches Review für alle gelisteten Strategien...");

        List<ControllingStrategy> targets = new ArrayList<>(tableItems);

        Task<Void> autoReviewTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                Path mt5Dir = AppConfig.getInstance().getMt5InstallDir();
                Path presetsDir = mt5Dir.resolve("MQL5").resolve("Profiles").resolve("Tester");
                
                int total = targets.size();
                updateProgress(0, total * 2);
                updateMessage("Starte Review...");

                for (int idx = 0; idx < total; idx++) {
                    if (isCancelled()) {
                        break;
                    }
                    ControllingStrategy strategy = targets.get(idx);
                    final int currentIdx = idx;
                    Platform.runLater(() -> {
                        logView.log("INFO", String.format("Review [%d/%d]: Starte Tests für %s (%s, %s, Pass #%d)",
                            currentIdx + 1, total,
                            EaParameterManager.extractEaBaseName(strategy.getExpert()),
                            strategy.getSymbol(), strategy.getPeriod(), strategy.getPassNumber()));
                    });

                    // 1. Write parameter preset file
                    String eaName = EaParameterManager.extractEaBaseName(strategy.getExpert());
                    String presetFileName = "AutoReview_" + eaName + "_" + strategy.getPassNumber() + ".set";
                    Path destFile = presetsDir.resolve(presetFileName);
                    List<EaParameter> finalParams = getStrategyParameters(strategy.getBaseParameters(), strategy.combinedPass);
                    eaParamManager.writeSetFile(destFile, finalParams, eaName);

                    // 2. Configure 1-Year Backtest
                    BacktestConfig config1y = new BacktestConfig();
                    config1y.setExpert(strategy.getExpert());
                    config1y.setSymbol(strategy.getSymbol());
                    config1y.setPeriod(strategy.getPeriod());
                    config1y.setModel(0); // Every tick
                    config1y.setFromDate(LocalDate.now().minusYears(1));
                    config1y.setToDate(LocalDate.now());
                    config1y.setDeposit(strategy.getDeposit());
                    config1y.setCurrency(strategy.getCurrency());
                    config1y.setLeverage(strategy.getLeverage());
                    config1y.setShutdownTerminal(true);
                    config1y.setUseVirtualDesktop(true);
                    config1y.setExpertParameters(presetFileName);
                    config1y.setAutoKillMt5(true);

                    // 3. Configure 2-Year Backtest
                    BacktestConfig config2y = new BacktestConfig();
                    config2y.setExpert(strategy.getExpert());
                    config2y.setSymbol(strategy.getSymbol());
                    config2y.setPeriod(strategy.getPeriod());
                    config2y.setModel(0); // Every tick
                    config2y.setFromDate(LocalDate.now().minusYears(2));
                    config2y.setToDate(LocalDate.now());
                    config2y.setDeposit(strategy.getDeposit());
                    config2y.setCurrency(strategy.getCurrency());
                    config2y.setLeverage(strategy.getLeverage());
                    config2y.setShutdownTerminal(true);
                    config2y.setUseVirtualDesktop(true);
                    config2y.setExpertParameters(presetFileName);
                    config2y.setAutoKillMt5(true);

                    BacktestRunner runner = new BacktestRunner();
                    runner.setLogCallback(msg -> Platform.runLater(() -> logView.log("INFO", "  [Runner] " + msg)));

                    // Run 1-Year Backtest
                    updateProgress(idx * 2, total * 2);
                    updateMessage(String.format("Review [%d/%d]: 1-Jahr-Test läuft...", idx + 1, total));
                    Platform.runLater(() -> logView.log("INFO", "  Führe 1-Jahres-Test aus..."));
                    BacktestResult res1y = runner.runBacktest(config1y);
                    if (res1y == null || !res1y.isSuccess()) {
                        String errMsg = (res1y == null) ? "Unbekannter Fehler" : res1y.getMessage();
                        Platform.runLater(() -> logView.log("ERROR", "  1-Jahres-Test fehlgeschlagen: " + errMsg));
                        updateProgress((idx + 1) * 2, total * 2);
                        continue;
                    }

                    // Run 2-Year Backtest
                    updateProgress(idx * 2 + 1, total * 2);
                    updateMessage(String.format("Review [%d/%d]: 2-Jahre-Test läuft...", idx + 1, total));
                    Platform.runLater(() -> logView.log("INFO", "  Führe 2-Jahres-Test aus..."));
                    BacktestResult res2y = runner.runBacktest(config2y);
                    if (res2y == null || !res2y.isSuccess()) {
                        String errMsg = (res2y == null) ? "Unbekannter Fehler" : res2y.getMessage();
                        Platform.runLater(() -> logView.log("ERROR", "  2-Jahres-Test fehlgeschlagen: " + errMsg));
                        updateProgress((idx + 1) * 2, total * 2);
                        continue;
                    }

                    // Save to Database
                    Gson gson = buildGson();
                    String res1yJson = gson.toJson(res1y);
                    String res2yJson = gson.toJson(res2y);

                    dbManager.saveAutomaticReview(
                        strategy.getExpert(),
                        strategy.getSymbol(),
                        strategy.getPeriod(),
                        strategy.getRunTimestamp(),
                        strategy.getPassNumber(),
                        res1yJson,
                        res2yJson
                    );

                    Platform.runLater(() -> logView.log("INFO", "  Erfolgreich gespeichert."));

                    // Delete the temporary preset file
                    try {
                        java.nio.file.Files.deleteIfExists(destFile);
                    } catch (Exception ignored) {}

                    updateProgress((idx + 1) * 2, total * 2);
                }
                updateProgress(total * 2, total * 2);
                return null;
            }
        };

        autoReviewTask.setOnSucceeded(e -> {
            logView.log("INFO", "Automatisches Review abgeschlossen.");
            resetUIState();
            refreshResults();
            // Re-select currently selected item if any
            ControllingStrategy selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                onStrategySelected(selected);
            }
        });

        autoReviewTask.setOnFailed(e -> {
            logView.log("ERROR", "Automatisches Review abgebrochen/fehlgeschlagen: " + autoReviewTask.getException().getMessage());
            resetUIState();
        });

        autoReviewTask.setOnCancelled(e -> {
            logView.log("WARN", "Automatisches Review abgebrochen.");
            resetUIState();
        });

        progress.progressProperty().bind(autoReviewTask.progressProperty());
        progressLabel.textProperty().bind(autoReviewTask.messageProperty());

        Thread th = new Thread(autoReviewTask);
        th.setDaemon(true);
        th.start();
    }

    private void updateViewForMode(ControllingStrategy selected, int mode) {
        if (selected == null) return;

        // Clear chart
        equityChart.getData().clear();

        if (mode == 0) {
            // ==========================================
            // Mode 0: Original-Test
            // ==========================================
            profitVal.setText(String.format(Locale.US, "%.2f %s", selected.getBtProfit(), selected.getCurrency()));
            profitVal.setTextFill(selected.getBtProfit() >= 0 ? Color.web("#22c55e") : Color.web("#ef4444"));

            ddVal.setText(String.format(Locale.US, "%.2f%%", selected.getBtDd()));
            ddVal.setTextFill(selected.getBtDd() > 25 ? Color.web("#ef4444") : Color.web("#22c55e"));

            tradesVal.setText(String.valueOf(selected.getBtTrades()));
            pfVal.setText(String.format(Locale.US, "%.2f", selected.combinedPass.getBtPf()));
            sharpeVal.setText(String.format(Locale.US, "%.2f", selected.combinedPass.getBtSharpe()));
            recVal.setText(String.format(Locale.US, "%.2f", selected.combinedPass.getBtRecovery()));

            unifiedScoreVal.setText(String.format(Locale.US, "%.1f / 100", selected.getScore()));
            unifiedScoreVal.setTextFill(selected.getScore() >= 70 ? Color.web("#22c55e") : (selected.getScore() >= 50 ? Color.WHITE : Color.web("#ef4444")));

            kiScoreVal.setText(selected.getKiScore() >= 0 ? selected.getKiScore() + " / 100" : "-");
            if (selected.getKiScore() >= 0) {
                kiScoreVal.setTextFill(selected.getKiScore() >= 70 ? Color.web("#22c55e") : (selected.getKiScore() >= 50 ? Color.WHITE : Color.web("#ef4444")));
            } else {
                kiScoreVal.setTextFill(Color.WHITE);
            }

            // Set Robustness Verdict Banner and KI Verdict visibility
            verdictLabel.setVisible(true);
            verdictLabel.setManaged(true);
            kiPanel.setVisible(true);
            kiPanel.setManaged(true);

            // Set Robustness Verdict text
            double worstCv = selected.getWorstCv();
            String verdictText;
            if (worstCv <= 0.0) {
                verdictText = "URTEIL: Keine Sensitivitätsdaten verfügbar.";
                verdictLabel.setStyle("-fx-background-color: transparent; -fx-text-fill: #7e889a; -fx-padding: 8px; -fx-font-weight: bold;");
            } else if (worstCv < 30.0) {
                verdictText = String.format("URTEIL: Die Parameter sind extrem robust! (Schlechtester CV: %.2f %%)", worstCv);
                verdictLabel.setStyle("-fx-background-color: rgba(34, 197, 94, 0.15); -fx-border-color: #22c55e; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 8px; -fx-text-fill: #22c55e; -fx-font-weight: bold;");
            } else if (worstCv <= 60.0) {
                verdictText = String.format("URTEIL: Solide Parameter-Konfiguration. (Schlechtester CV: %.2f %%)", worstCv);
                verdictLabel.setStyle("-fx-background-color: rgba(234, 179, 8, 0.15); -fx-border-color: #eab308; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 8px; -fx-text-fill: #eab308; -fx-font-weight: bold;");
            } else {
                verdictText = String.format("ACHTUNG: Die Parameter sind sehr fragil / überoptimiert! (Schlechtester CV: %.2f %%)", worstCv);
                verdictLabel.setStyle("-fx-background-color: rgba(239, 68, 68, 0.15); -fx-border-color: #ef4444; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px; -fx-padding: 8px; -fx-text-fill: #ef4444; -fx-font-weight: bold;");
            }
            verdictLabel.setText(verdictText);

            // Set KI Verdict
            KiStrategyReport kiReport = parseKiReportForPass(selected.getKiReportText(), selected.getPassNumber());
            if (kiReport.status.isEmpty() && kiReport.explanation.isEmpty()) {
                kiVerdictLabel.setText("KI-Urteil: Keine KI-Analyse für diese Strategie vorhanden.");
                kiVerdictLabel.setStyle("-fx-text-fill: #7e889a; -fx-font-weight: bold;");
                kiExplanationLabel.setText("Möglicherweise wurde der Workflow ohne KI-Schritt ausgeführt oder die Daten konnten nicht geladen werden.");
            } else {
                String statusText = kiReport.status;
                String curveText = kiReport.curves;
                String conclusionText = kiReport.conclusion;

                StringBuilder verdictSb = new StringBuilder("KI-Urteil: ");
                if (!statusText.isEmpty()) {
                    verdictSb.append(statusText);
                }
                if (!curveText.isEmpty()) {
                    verdictSb.append(" (").append(curveText).append(")");
                }
                if (!conclusionText.isEmpty()) {
                    verdictSb.append(" - ").append(conclusionText);
                }
                kiVerdictLabel.setText(verdictSb.toString());

                if (statusText.toLowerCase().contains("robust") || statusText.contains("✅")) {
                    kiVerdictLabel.setStyle("-fx-text-fill: #22c55e; -fx-font-weight: bold;");
                } else if (statusText.toLowerCase().contains("fragil") || statusText.contains("labil") || statusText.contains("⚠️")) {
                    kiVerdictLabel.setStyle("-fx-text-fill: #eab308; -fx-font-weight: bold;");
                } else if (statusText.toLowerCase().contains("überoptimiert") || statusText.contains("❌")) {
                    kiVerdictLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
                } else {
                    kiVerdictLabel.setStyle("-fx-text-fill: #e6e9f0; -fx-font-weight: bold;");
                }

                String rawExp = kiReport.explanation;
                String cleanExp = rawExp;
                if (rawExp.startsWith("**Pass")) {
                    int colonIdx = rawExp.indexOf(":**");
                    if (colonIdx != -1) {
                        cleanExp = rawExp.substring(colonIdx + 3).trim();
                    } else {
                        colonIdx = rawExp.indexOf(":");
                        if (colonIdx != -1) {
                            cleanExp = rawExp.substring(colonIdx + 1).trim();
                        }
                    }
                }
                cleanExp = cleanExp.replace("**", "");
                kiExplanationLabel.setText(cleanExp);
            }

            // Draw original synthetic curves
            double btEndBalance = selected.combinedPass.getBacktestPass().getBalance();
            double btStartBalance = btEndBalance - selected.getBtProfit();
            if (btStartBalance <= 0) {
                btStartBalance = 10000.0;
            }

            List<Double> btCurve = StrategyEvaluatorDialog.generateSyntheticEquityCurve(btStartBalance, selected.getBtProfit(), selected.getBtTrades(), selected.combinedPass.getBtPf(), selected.combinedPass.getPassNumber());
            XYChart.Series<Number, Number> backtestSeries = new XYChart.Series<>();
            backtestSeries.setName("Backtest (In-Sample)");
            for (int i = 0; i < btCurve.size(); i++) {
                backtestSeries.getData().add(new XYChart.Data<>(i, btCurve.get(i)));
            }
            equityChart.getData().add(backtestSeries);

            if (selected.combinedPass.getForwardPass() != null) {
                double fwStartBalance = btCurve.get(btCurve.size() - 1);
                List<Double> fwCurve = StrategyEvaluatorDialog.generateSyntheticEquityCurve(fwStartBalance, selected.combinedPass.getFwProfit(), selected.combinedPass.getFwTrades(), selected.combinedPass.getFwPf(), selected.combinedPass.getPassNumber() + 999);
                XYChart.Series<Number, Number> forwardSeries = new XYChart.Series<>();
                forwardSeries.setName("Forward (Out-of-Sample)");

                int offset = btCurve.size() - 1;
                forwardSeries.getData().add(new XYChart.Data<>(offset, fwStartBalance));
                for (int j = 1; j < fwCurve.size(); j++) {
                    forwardSeries.getData().add(new XYChart.Data<>(offset + j, fwCurve.get(j)));
                }
                equityChart.getData().add(forwardSeries);
            }

        } else {
            // Review Mode (1 = 1y, 2 = 2y)
            com.backtester.database.DatabaseManager.AutomaticReview autoReview = dbManager.getAutomaticReview(
                selected.getExpert(),
                selected.getSymbol(),
                selected.getPeriod(),
                selected.getRunTimestamp(),
                selected.getPassNumber()
            );

            if (autoReview != null) {
                Gson gson = buildGson();
                BacktestResult res = (mode == 1) 
                    ? gson.fromJson(autoReview.getResult1yJson(), BacktestResult.class)
                    : gson.fromJson(autoReview.getResult2yJson(), BacktestResult.class);

                if (res != null) {
                    profitVal.setText(String.format(Locale.US, "%.2f %s", res.getTotalProfit(), selected.getCurrency()));
                    profitVal.setTextFill(res.getTotalProfit() >= 0 ? Color.web("#22c55e") : Color.web("#ef4444"));

                    ddVal.setText(String.format(Locale.US, "%.2f%%", res.getMaxDrawdown()));
                    ddVal.setTextFill(res.getMaxDrawdown() > 25 ? Color.web("#ef4444") : Color.web("#22c55e"));

                    tradesVal.setText(String.valueOf(res.getTotalTrades()));
                    pfVal.setText(String.format(Locale.US, "%.2f", res.getProfitFactor()));
                    sharpeVal.setText(String.format(Locale.US, "%.2f", res.getSharpeRatio()));
                    recVal.setText(String.format(Locale.US, "%.2f", res.getRecoveryFactor()));

                    unifiedScoreVal.setText("-");
                    unifiedScoreVal.setTextFill(Color.WHITE);
                    kiScoreVal.setText("-");
                    kiScoreVal.setTextFill(Color.WHITE);

                    // Hide original robustness verdict and KI panels for reviews
                    verdictLabel.setVisible(false);
                    verdictLabel.setManaged(false);
                    kiPanel.setVisible(false);
                    kiPanel.setManaged(false);

                    if (res.getEquityHistory() != null && !res.getEquityHistory().isEmpty()) {
                        XYChart.Series<Number, Number> series = new XYChart.Series<>();
                        series.setName(mode == 1 ? "1-Jahr Nachtest (Tick)" : "2-Jahre Nachtest (Tick)");
                        List<double[]> hist = res.getEquityHistory();
                        for (int i = 0; i < hist.size(); i++) {
                            double[] point = hist.get(i);
                            double val = point.length > 2 ? point[2] : point[1];
                            series.getData().add(new XYChart.Data<>(i, val));
                        }
                        equityChart.getData().add(series);
                        Platform.runLater(() -> {
                            if (series.getNode() != null) {
                                series.getNode().setStyle(mode == 1 
                                    ? "-fx-stroke: #22c55e; -fx-stroke-width: 1.5px;" 
                                    : "-fx-stroke: #3b82f6; -fx-stroke-width: 1.5px;");
                            }
                        });
                    }
                }
            }
        }
    }
}
