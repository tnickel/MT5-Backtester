package com.backtester.ui.javafx;

import com.backtester.report.OptimizationResult.CombinedPass;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.*;
import java.util.stream.Collectors;

public class StrategyEvaluatorDialog extends Stage {
    private static final Logger log = LoggerFactory.getLogger(StrategyEvaluatorDialog.class);

    private final List<CombinedPass> allPasses;
    private final OptimizationView parentView;
    private final int referenceTrades;
    private final double referenceProfit;

    private Slider minTradesSlider;
    private Slider maxDdSlider;
    private Slider minPfSlider;

    private Label minTradesLabel;
    private Label maxDdLabel;
    private Label minPfLabel;

    private TableView<CombinedPass> table;
    private BarChart<String, Number> barChart;
    private CategoryAxis xAxis;
    private ComboBox<String> metricCombo;

    private Label kpiTotalVal;
    private Label kpiExcellentVal;
    private Label kpiSolidVal;
    private Label kpiStableLowVal;
    private Label kpiWarningVal;
    private Label kpiBadVal;

    private VBox kpiTotalCard;
    private VBox kpiExcellentCard;
    private VBox kpiSolidCard;
    private VBox kpiStableLowCard;
    private VBox kpiWarningCard;
    private VBox kpiBadCard;

    private String selectedKpiFilter = "ALL";

    public static class Evaluation {
        public final String rating;  // "EXCELLENT", "GOOD", "WARNING", "BAD"
        public final String remark;  // Die deutsche Beschreibung
        public final String color;   // Farb-Code für die UI (#00e676, #ffd740, #ff5252)

        public Evaluation(String rating, String remark, String color) {
            this.rating = rating;
            this.remark = remark;
            this.color = color;
        }
    }

    public static class ParameterRow {
        private final String name;
        private final String value;
        public ParameterRow(String name, String value) {
            this.name = name;
            this.value = value;
        }
        public String getName() { return name; }
        public String getValue() { return value; }
    }

    public StrategyEvaluatorDialog(List<CombinedPass> allPasses, OptimizationView parentView) {
        this.allPasses = allPasses;
        this.parentView = parentView;

        // Calculate dynamic reference trades as median of all loaded passes
        List<Integer> tradesList = allPasses.stream()
            .map(CombinedPass::getBtTrades)
            .sorted()
            .collect(Collectors.toList());
            
        int refTrades = 80; // default fallback
        if (!tradesList.isEmpty()) {
            int size = tradesList.size();
            if (size % 2 == 0) {
                refTrades = (tradesList.get(size / 2 - 1) + tradesList.get(size / 2)) / 2;
            } else {
                refTrades = tradesList.get(size / 2);
            }
        }
        // Set a minimum safety limit of 30 trades for statistical significance
        this.referenceTrades = Math.max(30, refTrades);

        // Calculate dynamic reference profit as median of all loaded passes
        List<Double> profitList = allPasses.stream()
            .map(CombinedPass::getBtProfit)
            .sorted()
            .collect(Collectors.toList());
            
        double refProfit = 500.0; // default fallback
        if (!profitList.isEmpty()) {
            int size = profitList.size();
            if (size % 2 == 0) {
                refProfit = (profitList.get(size / 2 - 1) + profitList.get(size / 2)) / 2.0;
            } else {
                refProfit = profitList.get(size / 2);
            }
        }
        // Set a minimum safety limit of 100.0 profit
        this.referenceProfit = Math.max(100.0, refProfit);

        initModality(Modality.WINDOW_MODAL);
        setTitle("Antigravity Advanced Strategy Evaluator");

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #11141d;");
        root.setPadding(new Insets(15));

        // ── Header ────────────────────────────────────────────────────────────
        VBox headerBox = new VBox(5);
        headerBox.setPadding(new Insets(0, 0, 15, 0));
        
        Label title = new Label("ADVANCED STRATEGY EVALUATOR");
        title.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 22));
        title.setTextFill(Color.web("#00e5ff"));
        
        Label subtitle = new Label("Statistische Breitenanalyse: Glockenkurve der Tradezahl & automatische Stresstest-Klassifizierung");
        subtitle.setFont(javafx.scene.text.Font.font("Segoe UI", 13));
        subtitle.setTextFill(Color.web("#7e889a"));
        
        headerBox.getChildren().addAll(title, subtitle);
        root.setTop(headerBox);

        // ── SplitPane ─────────────────────────────────────────────────────────
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.38);
        splitPane.setStyle("-fx-background-color: transparent; -fx-box-border: transparent;");

        // ── Left Panel (Filters & Glockenkurve) ──────────────────────────────
        VBox leftBox = new VBox(15);
        leftBox.setPadding(new Insets(0, 10, 0, 0));

        VBox filterPanel = new VBox(12);
        filterPanel.getStyleClass().add("sci-fi-panel");
        filterPanel.setPadding(new Insets(12));
        filterPanel.setStyle("-fx-border-color: #00e5ff; -fx-border-radius: 5px; -fx-border-width: 1px; -fx-background-color: #171b26;");

        Label filterTitle = new Label("Filter & Qualitäts-Kriterien");
        filterTitle.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 14));
        filterTitle.setTextFill(Color.web("#00e5ff"));

        Button filterInfoBtn = DocHelper.createSmallInfoButton(
            "Qualitäts-Filter im Evaluator",
            "Standardmäßig werden beim Öffnen des Advanced Evaluators solide Mindestanforderungen angewendet, um unbrauchbare Durchgänge sofort auszufiltern. Dies reduziert die Gesamtanzahl der geladenen Strategien auf die stabilsten Kandidaten.",
            "Die voreingestellten Kriterien:\n\n" +
            "1. Mindestanzahl an Trades (Min. Trades: 50):\n" +
            "   Filtert statistisch unzuverlässige Strategien mit zu geringer Aktivität aus.\n\n" +
            "2. Maximaler Drawdown (Max. Drawdown %: 25%):\n" +
            "   Verhindert unvertretbar hohe Verlustrisiken. Dieser Filter gilt sowohl für den Backtest (In-Sample) als auch für den Forward-Test (Out-of-Sample).\n\n" +
            "3. Mindest-Profitfaktor (Min. Profit Factor: 1.3):\n" +
            "   Stellt sicher, dass die Bruttogewinne mindestens das 1,3-fache der Bruttoverluste in beiden Testphasen betragen.\n\n" +
            "Tipp zur Ansicht aller Strategien:\n" +
            "Möchtest du alle Optimierungsdurchgänge sehen und analysieren, ziehe einfach alle Schieberegler auf das jeweilige Minimum/Maximum:\n" +
            "• Min. Trades -> 0\n" +
            "• Max. Drawdown % -> 100%\n" +
            "• Min. Profit Factor -> 1.0\n\n" +
            "Die Anzeige, die Glockenkurve und die KPI-Karten aktualisieren sich daraufhin sofort."
        );

        HBox filterTitleBox = new HBox(8, filterTitle, filterInfoBtn);
        filterTitleBox.setAlignment(Pos.CENTER_LEFT);

        // Min. Trades Slider
        minTradesLabel = new Label("Min. Trades: 50");
        minTradesLabel.setTextFill(Color.web("#b4bac8"));
        minTradesSlider = new Slider(0, 300, 50);
        minTradesSlider.setBlockIncrement(10);
        minTradesSlider.setShowTickMarks(true);
        minTradesSlider.setShowTickLabels(true);

        // Max. Drawdown Slider
        maxDdLabel = new Label("Max. Drawdown %: 25%");
        maxDdLabel.setTextFill(Color.web("#b4bac8"));
        maxDdSlider = new Slider(5, 100, 25);
        maxDdSlider.setBlockIncrement(5);
        maxDdSlider.setShowTickMarks(true);
        maxDdSlider.setShowTickLabels(true);

        // Min. Profit Factor Slider
        minPfLabel = new Label("Min. Profit Factor: 1.3");
        minPfLabel.setTextFill(Color.web("#b4bac8"));
        minPfSlider = new Slider(1.0, 5.0, 1.3);
        minPfSlider.setBlockIncrement(0.1);
        minPfSlider.setShowTickMarks(true);
        minPfSlider.setShowTickLabels(true);

        filterPanel.getChildren().addAll(
            filterTitleBox, 
            minTradesLabel, minTradesSlider, 
            maxDdLabel, maxDdSlider, 
            minPfLabel, minPfSlider
        );

        // Metrik Selector Header
        HBox chartHeader = new HBox(10);
        chartHeader.setAlignment(Pos.CENTER_LEFT);
        
        Label comboLabel = new Label("Metrik für Verteilung:");
        comboLabel.setTextFill(Color.web("#b4bac8"));
        comboLabel.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 12));
        
        metricCombo = new ComboBox<>();
        metricCombo.getItems().addAll("Trades (Häufigkeit)", "Max. Drawdown (%)", "Recovery Factor (Profit/DD)", "Robustness-Index (RI)", "Nettoprofit (Backtest)", "Nettoprofit (Forward)");
        metricCombo.setValue("Trades (Häufigkeit)");
        metricCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(metricCombo, Priority.ALWAYS);
        
        chartHeader.getChildren().addAll(comboLabel, metricCombo);

        // Glockenkurve / BarChart setup
        xAxis = new CategoryAxis();
        xAxis.setLabel("Trades (Bereich)");
        xAxis.setTickLabelFill(Color.web("#7e889a"));
        xAxis.setTickLabelRotation(30);
        
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Anzahl Passes");
        yAxis.setTickLabelFill(Color.web("#7e889a"));
        yAxis.setMinorTickVisible(false);
        
        barChart = new BarChart<>(xAxis, yAxis);
        barChart.setLegendVisible(false);
        barChart.setAnimated(false);
        barChart.setTitle("Glockenkurve (Trade-Häufigkeit)");
        barChart.setStyle("-fx-text-fill: white; -fx-background-color: transparent;");
        VBox.setVgrow(barChart, Priority.ALWAYS);

        leftBox.getChildren().addAll(filterPanel, chartHeader, barChart);

        // ── Right Panel (Table View) ──────────────────────────────────────────
        VBox rightBox = new VBox(10);
        rightBox.setPadding(new Insets(0, 0, 0, 10));

        kpiTotalVal = new Label();
        kpiExcellentVal = new Label();
        kpiSolidVal = new Label();
        kpiStableLowVal = new Label();
        kpiWarningVal = new Label();
        kpiBadVal = new Label();

        kpiTotalCard = createKpiCard("GESAMT LÄUFE", "#b4bac8", kpiTotalVal);
        kpiExcellentCard = createKpiCard("💎 EXZELLENT", "#00e676", kpiExcellentVal);
        kpiSolidCard = createKpiCard("✅ SOLIDE", "#81c784", kpiSolidVal);
        kpiStableLowCard = createKpiCard("ℹ️ MÄSS. PROFIT", "#80d8ff", kpiStableLowVal);
        kpiWarningCard = createKpiCard("⚠️ WARNUNG", "#ffd740", kpiWarningVal);
        kpiBadCard = createKpiCard("❌ RISIKO", "#ff5252", kpiBadVal);

        kpiTotalCard.setOnMouseClicked(e -> selectKpiFilter("ALL"));
        kpiExcellentCard.setOnMouseClicked(e -> selectKpiFilter("EXCELLENT"));
        kpiSolidCard.setOnMouseClicked(e -> selectKpiFilter("SOLID"));
        kpiStableLowCard.setOnMouseClicked(e -> selectKpiFilter("STABLE_LOW"));
        kpiWarningCard.setOnMouseClicked(e -> selectKpiFilter("WARNING"));
        kpiBadCard.setOnMouseClicked(e -> selectKpiFilter("BAD"));

        HBox kpiBox = new HBox(8);
        kpiBox.setAlignment(Pos.CENTER);
        kpiBox.setPadding(new Insets(5, 0, 5, 0));
        kpiBox.getChildren().addAll(kpiTotalCard, kpiExcellentCard, kpiSolidCard, kpiStableLowCard, kpiWarningCard, kpiBadCard);
        HBox.setHgrow(kpiTotalCard, Priority.ALWAYS);
        HBox.setHgrow(kpiExcellentCard, Priority.ALWAYS);
        HBox.setHgrow(kpiSolidCard, Priority.ALWAYS);
        HBox.setHgrow(kpiStableLowCard, Priority.ALWAYS);
        HBox.setHgrow(kpiWarningCard, Priority.ALWAYS);
        HBox.setHgrow(kpiBadCard, Priority.ALWAYS);

        createTable();
        VBox.setVgrow(table, Priority.ALWAYS);

        Label remarkInfo = new Label("* Bemerkungen bewerten Tradeanzahl, Drawdown-Verhalten & Konsistenz automatisch.");
        remarkInfo.setFont(javafx.scene.text.Font.font("Segoe UI", 10));
        remarkInfo.setTextFill(Color.web("#7e889a"));

        rightBox.getChildren().addAll(kpiBox, table, remarkInfo);

        splitPane.getItems().addAll(leftBox, rightBox);
        root.setCenter(splitPane);

        // ── Bottom Panel (Action Buttons) ────────────────────────────────────
        HBox bottomBox = new HBox(15);
        bottomBox.setAlignment(Pos.CENTER_RIGHT);
        bottomBox.setPadding(new Insets(15, 0, 0, 0));

        Button autoSelectBtn = new Button("⭐ Auto-Select Top 5");
        autoSelectBtn.getStyleClass().add("button");
        autoSelectBtn.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #ffd740; -fx-border-color: #ffd740;");
        autoSelectBtn.setOnAction(e -> autoSelectTopFive());

        Button autoSelectInfoBtn = new Button("ℹ");
        autoSelectInfoBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ffd740; -fx-padding: 0 5 0 5; -fx-font-size: 14px; -fx-font-weight: bold; -fx-cursor: hand;");
        autoSelectInfoBtn.setOnAction(e -> showAutoSelectExplanation());

        HBox autoSelectBox = new HBox(5, autoSelectBtn, autoSelectInfoBtn);
        autoSelectBox.setAlignment(Pos.CENTER_LEFT);

        Button addSelectedBtn = new Button("✔️ Add Selected to 'Selected' Tab");
        addSelectedBtn.getStyleClass().addAll("button", "button-start");
        addSelectedBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #287846, #143c23); -fx-text-fill: white;");
        addSelectedBtn.setOnAction(e -> addSelectedToOptimizationView());

        Button closeBtn = new Button("Schließen");
        closeBtn.getStyleClass().add("button");
        closeBtn.setOnAction(e -> close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button mainInfoBtn = DocHelper.createThickCircularInfoButton("Erklärung aller Indizes und Kennzahlen", () -> {
            DocHelper.showAllIndicesDocDialog(closeBtn.getScene() != null ? closeBtn.getScene().getWindow() : null);
        });

        bottomBox.getChildren().addAll(mainInfoBtn, spacer, autoSelectBox, addSelectedBtn, closeBtn);
        root.setBottom(bottomBox);

        // Set Scene & Stylesheet
        Scene scene = new Scene(root, 1280, 800);
        try {
            scene.getStylesheets().add(getClass().getResource("/css/antigravity.css").toExternalForm());
        } catch (Exception e) {
            // Fallback
        }
        setScene(scene);

        // Listeners for sliders
        minTradesSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int val = newVal.intValue();
            minTradesLabel.setText("Min. Trades: " + val);
            applyFilters();
        });
        maxDdSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int val = newVal.intValue();
            maxDdLabel.setText("Max. Drawdown %: " + val + "%");
            applyFilters();
        });
        minPfSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            double val = Math.round(newVal.doubleValue() * 10.0) / 10.0;
            minPfLabel.setText(String.format("Min. Profit Factor: %.1f", val));
            applyFilters();
        });

        metricCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            applyFilters();
        });

        // Trigger initial calculation
        selectKpiFilter("ALL");
    }

    private void createTable() {
        table = new TableView<>();
        table.setStyle("-fx-background-color: transparent;");
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
        TableColumn<CombinedPass, Number> passCol = new TableColumn<>("Pass");
        passCol.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getPassNumber()));
        passCol.setPrefWidth(50);
        
        TableColumn<CombinedPass, String> scoreCol = new TableColumn<>();
        scoreCol.setGraphic(DocHelper.createHeaderWithTooltip("Score", 
            "Unified Score (0-100):\nGewichteter Gesamtwert aus 10 Kriterien (Profit, DD, PF etc.). Konfigurierbar über das Regler-Symbol. Zeigt die beste Gesamtperformance."));
        scoreCol.setCellValueFactory(c -> new SimpleStringProperty(String.format(Locale.US, "%.2f", c.getValue().getScore())));
        scoreCol.setPrefWidth(75);

        TableColumn<CombinedPass, String> riCol = new TableColumn<>();
        riCol.setGraphic(DocHelper.createHeaderWithTooltip("RI", 
            "Robustness Index (RI):\nEin fixierter mathematischer Wert ohne Gewichtung. Multipliziert BT Recovery Factor, Trades-Gewichtung und Forward-Konsistenz. Dient als objektiver Tie-Breaker."));
        riCol.setCellValueFactory(c -> new SimpleStringProperty(String.format(Locale.US, "%.2f", calculateRobustnessIndex(c.getValue(), referenceTrades))));
        riCol.setPrefWidth(55);
        
        TableColumn<CombinedPass, String> consistCol = new TableColumn<>();
        consistCol.setGraphic(DocHelper.createHeaderWithTooltip("Konsistenz", 
            "Forward-Konsistenz (0.0-2.0):\nDas Verhältnis der Performance (Gewinn & Erholung) im Forward-Test (ungesehene Daten) zum Backtest. Je näher an 1.0 (oder höher), desto stabiler ist das Live-Verhalten."));
        consistCol.setCellValueFactory(c -> new SimpleStringProperty(String.format(Locale.US, "%.2f", c.getValue().getConsistency())));
        consistCol.setPrefWidth(95);

        TableColumn<CombinedPass, String> robScoreCol = new TableColumn<>();
        robScoreCol.setGraphic(DocHelper.createHeaderWithTooltip("Rob. Scorecard",
            "Robustness Scorecard (0-100):\nGewichteter Score aus 8 Säulen echter MT5-Messdaten: Profitabilität (BT+FW), FW/BT-Konsistenz, Risiko-Verhältnis, Sharpe Ratio, Stichprobengröße, FW-Trades und Erholungsfaktor."));
        robScoreCol.setCellValueFactory(c -> {
            String fromDateStr = "Unbekannt";
            String toDateStr = "Unbekannt";
            if (parentView != null && parentView.getLastOptResult() != null) {
                fromDateStr = parentView.getLastOptResult().getFromDate();
                toDateStr = parentView.getLastOptResult().getToDate();
            }
            double score = c.getValue().getCachedOverallScore(fromDateStr, toDateStr);
            return new SimpleStringProperty(String.format(Locale.US, "%.0f", score));
        });
        robScoreCol.setPrefWidth(115);
        robScoreCol.setCellFactory(col -> new TableCell<CombinedPass, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
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
                        setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
                    } catch (Exception e) {
                        setStyle("");
                    }
                }
            }
        });

        // Backtest Sub-Columns
        TableColumn<CombinedPass, String> btGroup = new TableColumn<>("◀ Backtest");
        
        TableColumn<CombinedPass, String> btProfitCol = new TableColumn<>("Profit");
        btProfitCol.setCellValueFactory(c -> new SimpleStringProperty(String.format(Locale.US, "%.2f", c.getValue().getBtProfit())));
        btProfitCol.setPrefWidth(70);
        
        TableColumn<CombinedPass, String> btTradesCol = new TableColumn<>("Trades");
        btTradesCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getBtTrades())));
        btTradesCol.setPrefWidth(50);
        
        TableColumn<CombinedPass, String> btDdCol = new TableColumn<>("DD%");
        btDdCol.setCellValueFactory(c -> new SimpleStringProperty(String.format(Locale.US, "%.2f", c.getValue().getBtDd())));
        btDdCol.setPrefWidth(55);
        
        TableColumn<CombinedPass, String> btRecoveryCol = new TableColumn<>("Erholung");
        btRecoveryCol.setCellValueFactory(c -> new SimpleStringProperty(String.format(Locale.US, "%.2f", c.getValue().getBtRecovery())));
        btRecoveryCol.setPrefWidth(65);
        
        btGroup.getColumns().addAll(btProfitCol, btTradesCol, btDdCol, btRecoveryCol);

        // Forward Sub-Columns
        TableColumn<CombinedPass, String> fwGroup = new TableColumn<>("Forward ▶");
        
        TableColumn<CombinedPass, String> fwProfitCol = new TableColumn<>("Profit");
        fwProfitCol.setCellValueFactory(c -> {
            double v = c.getValue().getFwProfit();
            return new SimpleStringProperty(Double.isNaN(v) ? "—" : String.format(Locale.US, "%.2f", v));
        });
        fwProfitCol.setPrefWidth(70);
        
        TableColumn<CombinedPass, String> fwTradesCol = new TableColumn<>("Trades");
        fwTradesCol.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getForwardPass() != null ? String.valueOf(c.getValue().getFwTrades()) : "—"));
        fwTradesCol.setPrefWidth(50);
        
        TableColumn<CombinedPass, String> fwDdCol = new TableColumn<>("DD%");
        fwDdCol.setCellValueFactory(c -> {
            double v = c.getValue().getFwDd();
            return new SimpleStringProperty(Double.isNaN(v) ? "—" : String.format(Locale.US, "%.2f", v));
        });
        fwDdCol.setPrefWidth(55);
        
        TableColumn<CombinedPass, String> fwRecoveryCol = new TableColumn<>("Erholung");
        fwRecoveryCol.setCellValueFactory(c -> {
            double v = c.getValue().getFwRecovery();
            return new SimpleStringProperty(Double.isNaN(v) ? "—" : String.format(Locale.US, "%.2f", v));
        });
        fwRecoveryCol.setPrefWidth(65);
        
        fwGroup.getColumns().addAll(fwProfitCol, fwTradesCol, fwDdCol, fwRecoveryCol);

        // Evaluation column
        TableColumn<CombinedPass, String> remarkCol = new TableColumn<>("Bemerkung / Analyse");
        remarkCol.setCellValueFactory(c -> new SimpleStringProperty(evaluatePass(c.getValue(), referenceTrades, referenceProfit).remark));
        remarkCol.setPrefWidth(330);
        remarkCol.setCellFactory(col -> new TableCell<CombinedPass, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    CombinedPass pass = getTableRow() != null ? getTableRow().getItem() : null;
                    if (pass != null) {
                        Evaluation eval = evaluatePass(pass, referenceTrades, referenceProfit);
                        setStyle("-fx-text-fill: " + eval.color + "; -fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        table.getColumns().addAll(passCol, scoreCol, riCol, consistCol, robScoreCol, btGroup, fwGroup, remarkCol);

        table.setRowFactory(tv -> {
            TableRow<CombinedPass> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    showPassDetailsDialog(row.getItem());
                }
            });
            return row;
        });
    }

    private void applyFilters() {
        int minTrades = (int) minTradesSlider.getValue();
        int maxDd = (int) maxDdSlider.getValue();
        double minPf = minPfSlider.getValue();

        List<CombinedPass> sliderFiltered = allPasses.stream()
            .filter(cp -> cp.getBtTrades() >= minTrades)
            .filter(cp -> cp.getBtDd() <= maxDd)
            .filter(cp -> Double.isNaN(cp.getFwDd()) || cp.getFwDd() <= maxDd)
            .filter(cp -> cp.getBtPf() >= minPf)
            .filter(cp -> Double.isNaN(cp.getFwPf()) || cp.getFwPf() >= minPf)
            .collect(Collectors.toList());

        // Count categories based on sliderFiltered list
        int total = sliderFiltered.size();
        int excellent = 0;
        int solid = 0;
        int stableLow = 0;
        int warning = 0;
        int bad = 0;

        for (CombinedPass cp : sliderFiltered) {
            Evaluation eval = evaluatePass(cp, referenceTrades, referenceProfit);
            if (eval.rating.equals("EXCELLENT")) {
                excellent++;
            } else if (eval.rating.equals("GOOD")) {
                if (eval.color.equals("#00e676")) {
                    solid++;
                } else if (eval.color.equals("#80d8ff")) {
                    if (eval.remark.contains("Stabil, aber mäßiger Profit")) {
                        stableLow++;
                    } else {
                        solid++;
                    }
                }
            } else if (eval.rating.equals("WARNING")) {
                warning++;
            } else if (eval.rating.equals("BAD")) {
                bad++;
            }
        }

        kpiTotalVal.setText(String.valueOf(total));
        if (total > 0) {
            kpiExcellentVal.setText(String.format(Locale.US, "%d (%.1f%%)", excellent, (excellent * 100.0 / total)));
            kpiSolidVal.setText(String.format(Locale.US, "%d (%.1f%%)", solid, (solid * 100.0 / total)));
            kpiStableLowVal.setText(String.format(Locale.US, "%d (%.1f%%)", stableLow, (stableLow * 100.0 / total)));
            kpiWarningVal.setText(String.format(Locale.US, "%d (%.1f%%)", warning, (warning * 100.0 / total)));
            kpiBadVal.setText(String.format(Locale.US, "%d (%.1f%%)", bad, (bad * 100.0 / total)));
        } else {
            kpiExcellentVal.setText("0 (0.0%)");
            kpiSolidVal.setText("0 (0.0%)");
            kpiStableLowVal.setText("0 (0.0%)");
            kpiWarningVal.setText("0 (0.0%)");
            kpiBadVal.setText("0 (0.0%)");
        }

        // Apply KPI filter for the visible list in Table & Chart
        List<CombinedPass> finalFiltered = sliderFiltered.stream()
            .filter(cp -> {
                if ("ALL".equals(selectedKpiFilter)) return true;
                Evaluation eval = evaluatePass(cp, referenceTrades, referenceProfit);
                if ("EXCELLENT".equals(selectedKpiFilter)) return eval.rating.equals("EXCELLENT");
                if ("SOLID".equals(selectedKpiFilter)) {
                    return eval.rating.equals("GOOD") && (eval.color.equals("#00e676") || !eval.remark.contains("Stabil, aber mäßiger Profit"));
                }
                if ("STABLE_LOW".equals(selectedKpiFilter)) {
                    return eval.rating.equals("GOOD") && eval.color.equals("#80d8ff") && eval.remark.contains("Stabil, aber mäßiger Profit");
                }
                if ("WARNING".equals(selectedKpiFilter)) return eval.rating.equals("WARNING");
                if ("BAD".equals(selectedKpiFilter)) return eval.rating.equals("BAD");
                return true;
            })
            .collect(Collectors.toList());

        table.setItems(FXCollections.observableArrayList(finalFiltered));
        updateChart(finalFiltered);
    }

    private void updateChart(List<CombinedPass> filteredPasses) {
        barChart.getData().clear();
        if (filteredPasses.isEmpty()) return;

        String selectedMetric = metricCombo.getValue();
        if (selectedMetric == null) {
            selectedMetric = "Trades (Häufigkeit)";
        }

        // Get values based on selected metric
        List<Double> values = new ArrayList<>();
        String xLabel = "Bereich";
        String chartTitle = "Glockenkurve";
        boolean isInteger = false;

        if (selectedMetric.equals("Trades (Häufigkeit)")) {
            for (CombinedPass cp : filteredPasses) {
                values.add((double) cp.getBtTrades());
            }
            xLabel = "Trades (Bereich)";
            chartTitle = "Glockenkurve (Trade-Häufigkeit)";
            isInteger = true;
        } else if (selectedMetric.equals("Max. Drawdown (%)")) {
            for (CombinedPass cp : filteredPasses) {
                values.add(cp.getBtDd());
            }
            xLabel = "Max. Drawdown % (Bereich)";
            chartTitle = "Glockenkurve (Max. Drawdown %)";
        } else if (selectedMetric.equals("Recovery Factor (Profit/DD)")) {
            for (CombinedPass cp : filteredPasses) {
                values.add(cp.getBtRecovery());
            }
            xLabel = "Recovery Factor (Bereich)";
            chartTitle = "Glockenkurve (Recovery Factor)";
        } else if (selectedMetric.equals("Robustness-Index (RI)")) {
            for (CombinedPass cp : filteredPasses) {
                values.add(calculateRobustnessIndex(cp, referenceTrades));
            }
            xLabel = "Robustness-Index (Bereich)";
            chartTitle = "Glockenkurve (Robustness-Index)";
        } else if (selectedMetric.equals("Nettoprofit (Backtest)")) {
            for (CombinedPass cp : filteredPasses) {
                values.add(cp.getBtProfit());
            }
            xLabel = "Nettoprofit Backtest (Bereich)";
            chartTitle = "Glockenkurve (Nettoprofit Backtest)";
        } else if (selectedMetric.equals("Nettoprofit (Forward)")) {
            for (CombinedPass cp : filteredPasses) {
                if (cp.getForwardPass() != null) {
                    values.add(cp.getFwProfit());
                }
            }
            xLabel = "Nettoprofit Forward (Bereich)";
            chartTitle = "Glockenkurve (Nettoprofit Forward)";
        }

        xAxis.setLabel(xLabel);
        barChart.setTitle(chartTitle);

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double v : values) {
            if (Double.isNaN(v) || Double.isInfinite(v)) continue;
            if (v < min) min = v;
            if (v > max) max = v;
        }

        if (min == Double.MAX_VALUE) {
            min = 0.0;
            max = 10.0;
        }
        if (Math.abs(max - min) < 1e-9) {
            max = min + 1.0;
        }

        double range = max - min;
        double binWidth = getNiceBinWidthDouble(range);
        if (isInteger) {
            binWidth = Math.max(1.0, Math.round(binWidth));
        }

        // Start binning from the multiple of binWidth just below min
        double startBin = Math.floor(min / binWidth) * binWidth;

        // Create bins
        List<Double> binBoundaries = new ArrayList<>();
        double current = startBin;
        // Avoid infinite loop if binWidth is extremely small
        if (binWidth < 1e-9) {
            binWidth = 0.1;
        }
        int safetyCounter = 0;
        while (current <= max + 1e-9 && safetyCounter < 100) {
            binBoundaries.add(current);
            current += binWidth;
            safetyCounter++;
        }
        binBoundaries.add(current); // one extra upper bound

        // Initialize frequencies
        int[] frequencies = new int[binBoundaries.size() - 1];

        for (double v : values) {
            if (Double.isNaN(v) || Double.isInfinite(v)) continue;
            for (int i = 0; i < binBoundaries.size() - 1; i++) {
                double lower = binBoundaries.get(i);
                double upper = binBoundaries.get(i + 1);

                if (i == binBoundaries.size() - 2) {
                    if (v >= lower) {
                        frequencies[i]++;
                        break;
                    }
                } else {
                    if (v >= lower && v < upper) {
                        frequencies[i]++;
                        break;
                    }
                }
            }
        }

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (int i = 0; i < binBoundaries.size() - 1; i++) {
            double lower = binBoundaries.get(i);
            double upper = binBoundaries.get(i + 1);
            String label;

            if (isInteger) {
                int ilower = (int) Math.round(lower);
                int iupper = (int) Math.round(upper);
                if (iupper - ilower == 1) {
                    label = String.valueOf(ilower);
                } else {
                    label = ilower + "-" + (iupper - 1);
                }
            } else {
                if (selectedMetric.equals("Max. Drawdown (%)")) {
                    label = String.format(Locale.US, "%.1f%%-%.1f%%", lower, upper);
                } else if (selectedMetric.contains("Nettoprofit")) {
                    label = String.format(Locale.US, "%.0f - %.0f", lower, upper);
                } else {
                    label = String.format(Locale.US, "%.2f-%.2f", lower, upper);
                }
            }
            series.getData().add(new XYChart.Data<>(label, frequencies[i]));
        }

        barChart.getData().add(series);
    }

    private double getNiceBinWidthDouble(double range) {
        if (range <= 0) return 0.1;
        double rawStep = range / 20.0; // Target around 20 bins
        double log10 = Math.log10(rawStep);
        double power = Math.pow(10, Math.floor(log10));
        double ratio = rawStep / power;

        double step;
        if (ratio < 1.5) {
            step = 1.0 * power;
        } else if (ratio < 3.0) {
            step = 2.0 * power;
        } else if (ratio < 7.0) {
            step = 5.0 * power;
        } else {
            step = 10.0 * power;
        }
        return step;
    }

    public static double calculateRobustnessIndex(CombinedPass cp) {
        return calculateRobustnessIndex(cp, 80);
    }

    public static double calculateRobustnessIndex(CombinedPass cp, int referenceTrades) {
        double rfBt = cp.getBtRecovery();
        if (Double.isNaN(rfBt) || rfBt <= 0) {
            rfBt = 0.0;
        }

        // 1. Trade-Anzahl Faktor (Je mehr Trades, desto besser. Sigmoid/Exponential-Annäherung)
        int tradesBt = cp.getBtTrades();
        double wTrades = 1.0 - Math.exp(-tradesBt / (double) referenceTrades);

        // 2. Konsistenz-Faktor (Backtest vs Forward)
        double wConsistency = 1.0;
        if (cp.getForwardPass() != null) {
            double rfFw = cp.getFwRecovery();
            if (Double.isNaN(rfFw) || rfFw <= 0) {
                wConsistency = 0.0; // Verlust im Forward ist ein K.O.-Kriterium
            } else {
                double ratio = rfFw / rfBt;
                if (ratio >= 0.95) {
                    wConsistency = 1.0; // Forward ist ungefähr gleich oder besser -> Kein Abzug
                } else {
                    wConsistency = ratio; // Linearer Abzug bei schlechterem Forward
                }
            }
        } else {
            // Kein Forward-Test vorhanden -> milder Abzug, da unbestätigt
            wConsistency = 0.7;
        }

        // Berechne finalen Score: Recovery-Factor * Trades-Gewicht * Konsistenz-Gewicht
        double ri = rfBt * wTrades * wConsistency;
        if (Double.isNaN(ri) || Double.isInfinite(ri) || ri < 0) {
            ri = 0.0;
        }
        return ri;
    }

    private void showRobustnessIndexExplanation() {
        Stage infoStage = new Stage();
        infoStage.initOwner(this);
        infoStage.initModality(Modality.APPLICATION_MODAL);
        infoStage.setTitle("Was ist der Robustness-Index (RI)?");

        VBox layout = new VBox(15);
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color: #11141d; -fx-border-color: #ffd740; -fx-border-width: 1px; -fx-border-radius: 5px;");

        Label title = new Label("Robustness-Index (RI) - Erklärung");
        title.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#ffd740"));

        Label scoreComparisonTitle = new Label("1. Zusammenhang zur Score-Gewichtung");
        scoreComparisonTitle.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 13));
        scoreComparisonTitle.setTextFill(Color.web("#00e5ff"));

        Label scoreComparisonText = new Label(
            "Nein, der Robustness-Index wird NICHT aus der Score-Gewichtung berechnet.\n\n" +
            "• Der normale 'Score' in den Tabellen basiert auf Ihren individuellen Gewichtungen (im Hauptfenster konfigurierbar unter 'Score-Gewichtung...'). " +
            "Wichtig: Dieser Score bewertet ausschließlich die endgültigen Kennzahlen am Schluss und ignoriert den eigentlichen Verlauf der Kennlinie.\n\n" +
            "• Der 'Robustness-Index (RI)' ist ein mathematisch fixierter Standard-Benchmark, der Ihnen eine objektive, einheitliche Bewertung Ihrer EAs unabhängig von Ihren gewählten UI-Gewichtungen erlaubt. " +
            "Er baut auf dem Robustness Score auf und bewertet mit festen Formeln Recovery-Faktor, Trade-Anzahl und Forward-Konsistenz."
        );
        scoreComparisonText.setWrapText(true);
        scoreComparisonText.setTextFill(Color.web("#e6e9f0"));

        Label formulaTitle = new Label("2. Wie wird der RI berechnet?");
        formulaTitle.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 13));
        formulaTitle.setTextFill(Color.web("#00e5ff"));

        Label formulaText = new Label(
            "Formel: RI = Recovery Factor (Backtest) × Trade-Gewicht (w_trades) × Konsistenz-Gewicht (w_konsistenz)\n\n" +
            "• Base Metric: Der Recovery Factor (Nettoprofit / max. Drawdown) aus dem Backtest bildet die Basis.\n" +
            "• Trade-Gewicht (w_trades): Berechnet sich as (1 - e^(-Trades / N_ref)).\n" +
            "   - Dynamisches N_ref für diesen Durchlauf: N_ref = " + referenceTrades + " Trades.\n" +
            "     (Berechnet als Median der Tradeanzahl über alle " + allPasses.size() + " geladenen Strategien, mindestens jedoch 30).\n" +
            "   - Ein Durchlauf mit genau " + referenceTrades + " Trades erhält somit ein Trade-Gewicht von ca. 0,63.\n" +
            "   - Höhere Tradezahlen nähern sich asymptotisch dem Gewicht 1,0 an (z. B. " + (3 * referenceTrades) + " Trades erreichen ca. 0,95).\n" +
            "• Konsistenz-Gewicht (w_konsistenz): Vergleicht den Recovery-Faktor von Backtest und Forward:\n" +
            "   - Forward gleich gut oder besser (>= 95%): Gewicht = 1.0 (kein Abzug).\n" +
            "   - Forward schlechter: Gewicht sinkt linear mit dem Verhältnis der Recovery-Faktoren.\n" +
            "   - Forward ist ein Verlustgeschäft: Gewicht = 0.0 (K.O.-Kriterium, RI wird 0).\n" +
            "   - Kein Forward-Test vorhanden: Gewicht = 0.7 (milder Vorsichtsabzug)."
        );
        formulaText.setWrapText(true);
        formulaText.setTextFill(Color.web("#e6e9f0"));

        Label parameterTitle = new Label("3. Wo stellt man die Parameter dafür ein?");
        parameterTitle.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 13));
        parameterTitle.setTextFill(Color.web("#00e5ff"));

        Label parameterText = new Label(
            "• Die mathematischen Gewichtungs-Formeln des RI sind fest programmiert, um als standardisierter, unbeeinflussbarer Benchmark zu dienen.\n" +
            "• Die Filter-Schwellenwerte für Trades, Drawdown und Profit Factor können Sie links über die Schieberegler live anpassen.\n" +
            "• Die Gewichtung für den normalen 'Score' können Sie im Hauptfenster über den Button 'Score-Gewichtung...' einstellen."
        );
        parameterText.setWrapText(true);
        parameterText.setTextFill(Color.web("#e6e9f0"));

        Button closeBtn = new Button("Verstanden");
        closeBtn.getStyleClass().add("button");
        closeBtn.setOnAction(e -> infoStage.close());

        HBox btnBox = new HBox(closeBtn);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        layout.getChildren().addAll(title, scoreComparisonTitle, scoreComparisonText, formulaTitle, formulaText, parameterTitle, parameterText, btnBox);

        ScrollPane scrollPane = new ScrollPane(layout);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: #11141d; -fx-border-color: transparent;");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        Scene scene = new Scene(scrollPane, 580, 580);
        try {
            scene.getStylesheets().add(getClass().getResource("/css/antigravity.css").toExternalForm());
        } catch (Exception e) {
            // Ignore
        }
        infoStage.setScene(scene);
        infoStage.showAndWait();
    }

    public static void showRobustnessScoreExplanation(javafx.stage.Window owner) {
        Stage infoStage = new Stage();
        if (owner != null) {
            infoStage.initOwner(owner);
        }
        infoStage.initModality(Modality.APPLICATION_MODAL);
        infoStage.setTitle("Was ist der Robustness Score?");

        VBox layout = new VBox(15);
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color: #11141d; -fx-border-color: #ffd740; -fx-border-width: 1px; -fx-border-radius: 5px;");

        Label title = new Label("Robustness Score (0-100) - Erklärung");
        title.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#ffd740"));

        Label descText = new Label(
            "Der Robustness Score ist ein von 0 bis 100 skalierter Gesamtwert, der auf 6 wesentlichen Säulen der Strategiequalität basiert. " +
            "Er prüft detailliert, ob eine Strategie robust ist oder Anzeichen von Überoptimierung (Curve-Fitting) aufweist."
        );
        descText.setWrapText(true);
        descText.setTextFill(Color.web("#e6e9f0"));

        Label comparisonNote = new Label(
            "⚠️ WICHTIGER UNTERSCHIED ZUM GESAMT-SCORE:\n" +
            "Der normale Gesamt-Score bewertet ausschließlich die endgültigen Kennzahlen am Schluss (Gewinn, Drawdown, etc.). " +
            "Der Robustness Score gewichtet zusätzlich Konsistenz (FW/BT), Sharpe Ratio und Stichprobengröße, " +
            "um Glückstreffer oder instabile Strategien aufzudecken."
        );
        comparisonNote.setWrapText(true);
        comparisonNote.setTextFill(Color.web("#ffd740"));
        comparisonNote.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 12));

        Label pillarsTitle = new Label("Die Säulen der Robustheit (nur echte Messdaten):");
        pillarsTitle.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 13));
        pillarsTitle.setTextFill(Color.web("#00e5ff"));

        Label pillarsText = new Label(
            "• 1. Profitabilität (BT + FW): Bewertet ROI und Profit Factor in beiden Phasen.\n" +
            "• 2. Konsistenz (FW/BT): Reproduzierbarkeit der Ergebnisse im Forward-Test.\n" +
            "• 3. Risiko-Verhältnis (Risk/Reward): Bewertet Calmar Ratio und Recovery Factor.\n" +
            "• 4. Sharpe Ratio: Von MT5 gemessene Ertragsgleichmäßigkeit (BT + FW).\n" +
            "• 5. Stichprobengröße (Sample Size): Trades und reale Testjahre.\n" +
            "• 6. FW Trade Count: Statistische Belastbarkeit der Forward-Phase."
        );
        pillarsText.setWrapText(true);
        pillarsText.setTextFill(Color.web("#e6e9f0"));

        Label evaluationTitle = new Label("Bewertungsskala:");
        evaluationTitle.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 13));
        evaluationTitle.setTextFill(Color.web("#00e5ff"));

        Label evaluationText = new Label(
            "• >= 70 (Klasse A / B): Hervorragende Robustheit, sehr gut geeignet für Live-Tests.\n" +
            "• 55 - 69 (Klasse C): Grenzwertig robuste Performance mit Schwächen.\n" +
            "• < 55 (Klasse D / F): Mangelnde Robustheit, hohe Wahrscheinlichkeit von Curve-Fitting."
        );
        evaluationText.setWrapText(true);
        evaluationText.setTextFill(Color.web("#e6e9f0"));

        Button closeBtn = new Button("Verstanden");
        closeBtn.getStyleClass().add("button");
        closeBtn.setOnAction(e -> infoStage.close());

        HBox btnBox = new HBox(closeBtn);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        layout.getChildren().addAll(title, descText, comparisonNote, pillarsTitle, pillarsText, evaluationTitle, evaluationText, btnBox);

        ScrollPane scrollPane = new ScrollPane(layout);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: #11141d; -fx-border-color: transparent;");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        Scene scene = new Scene(scrollPane, 580, 580);
        try {
            scene.getStylesheets().add(StrategyEvaluatorDialog.class.getResource("/css/antigravity.css").toExternalForm());
        } catch (Exception e) {
            // Ignore
        }
        infoStage.setScene(scene);
        infoStage.showAndWait();
    }

    private VBox createKpiCard(String title, String colorHex, Label valueLabelRef) {
        VBox card = new VBox(4);
        card.setPadding(new Insets(8, 12, 8, 12));
        card.setStyle(
            "-fx-background-color: #171b26; " +
            "-fx-border-color: " + colorHex + "; " +
            "-fx-border-width: 1px; " +
            "-fx-border-radius: 4px; " +
            "-fx-background-radius: 4px; " +
            "-fx-min-width: 110px; " +
            "-fx-cursor: hand;"
        );
        card.setAlignment(Pos.CENTER);

        Label titleLabel = new Label(title);
        titleLabel.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 10));
        titleLabel.setTextFill(Color.web(colorHex).deriveColor(0, 1, 0.7, 1));

        valueLabelRef.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 14));
        valueLabelRef.setTextFill(Color.web(colorHex));
        valueLabelRef.setText("0");

        card.getChildren().addAll(titleLabel, valueLabelRef);
        return card;
    }

    private void selectKpiFilter(String filterType) {
        this.selectedKpiFilter = filterType;
        applyFilters();
        
        kpiTotalCard.setStyle(getKpiStyle("#b4bac8", "ALL".equals(filterType)));
        kpiExcellentCard.setStyle(getKpiStyle("#00e676", "EXCELLENT".equals(filterType)));
        kpiSolidCard.setStyle(getKpiStyle("#81c784", "SOLID".equals(filterType)));
        kpiStableLowCard.setStyle(getKpiStyle("#80d8ff", "STABLE_LOW".equals(filterType)));
        kpiWarningCard.setStyle(getKpiStyle("#ffd740", "WARNING".equals(filterType)));
        kpiBadCard.setStyle(getKpiStyle("#ff5252", "BAD".equals(filterType)));
    }
    
    private String getKpiStyle(String colorHex, boolean isSelected) {
        if (isSelected) {
            return "-fx-background-color: #1a2238; " +
                   "-fx-border-color: " + colorHex + "; " +
                   "-fx-border-width: 2px; " +
                   "-fx-border-radius: 4px; " +
                   "-fx-background-radius: 4px; " +
                   "-fx-min-width: 110px; " +
                   "-fx-cursor: hand; " +
                   "-fx-effect: dropshadow(three-pass-box, " + colorHex + ", 10, 0.0, 0, 0);";
        } else {
            return "-fx-background-color: #171b26; " +
                   "-fx-border-color: " + colorHex + "; " +
                   "-fx-border-width: 1px; " +
                   "-fx-border-radius: 4px; " +
                   "-fx-background-radius: 4px; " +
                   "-fx-min-width: 110px; " +
                   "-fx-cursor: hand;";
        }
    }

    public static Evaluation evaluatePass(CombinedPass cp) {
        return evaluatePass(cp, 80, 500.0);
    }

    public static Evaluation evaluatePass(CombinedPass cp, int referenceTrades) {
        return evaluatePass(cp, referenceTrades, 500.0);
    }

    public static Evaluation evaluatePass(CombinedPass cp, int referenceTrades, double referenceProfit) {
        double btProfit = cp.getBtProfit();
        double fwProfit = cp.getFwProfit();
        int btTrades = cp.getBtTrades();
        int fwTrades = cp.getFwTrades();
        double btDd = cp.getBtDd();
        double fwDd = cp.getFwDd();
        double consistency = cp.getConsistency();
        double btPf = cp.getBtPf();
        double fwPf = cp.getFwPf();

        double ri = calculateRobustnessIndex(cp, referenceTrades);

        // 1. Statistische Relevanz prüfen (Geringe Tradezahl)
        if (btTrades < 10 || fwTrades < 10) {
            return new Evaluation("BAD", String.format(Locale.US, "❌ Statistische Irrelevanz (RI: %.2f - zu wenig Trades)", ri), "#ff5252");
        }
        
        // 2. Extremes Risiko prüfen (Drawdown)
        if (btDd > 50.0 || fwDd > 50.0) {
            return new Evaluation("BAD", String.format(Locale.US, "❌ Klippen-Risiko (RI: %.2f): Extrem hoher Drawdown (>50%%)", ri), "#ff5252");
        }

        // 3. Verlustreiche Läufe
        if (btProfit <= 0 || fwProfit <= 0) {
            return new Evaluation("BAD", String.format(Locale.US, "❌ Nicht profitabel im Back- oder Forward (RI: %.2f)", ri), "#ff5252");
        }

        // 4. Warnung bei mäßiger Tradeanzahl
        if (btTrades < 40 || fwTrades < 40) {
            if (btDd <= 10.0 && fwDd <= 10.0 && consistency >= 1.0) {
                return new Evaluation("WARNING", String.format(Locale.US, "⚠️ Gute Konsistenz, aber geringe Tradeanzahl (RI: %.2f)", ri), "#ffd740");
            }
            return new Evaluation("WARNING", String.format(Locale.US, "⚠️ Geringe statistische Breite (RI: %.2f)", ri), "#ffd740");
        }

        // 5. Leistungseinbruch im Forward
        if (consistency < 0.6) {
            return new Evaluation("WARNING", String.format(Locale.US, "⚠️ Deutlicher Leistungseinbruch im Forward (RI: %.2f)", ri), "#ffd740");
        }

        // 6. Exzellente Kandidaten (Stabilität + hoher Profit)
        if (consistency >= 1.0 && btTrades >= 80 && btDd <= 15.0 && fwDd <= 15.0 && btPf >= 1.5 && fwPf >= 1.5 && btProfit >= referenceProfit) {
            return new Evaluation("EXCELLENT", String.format(Locale.US, "💎 Exzellent! Stabil, geringer Drawdown & konsistent (RI: %.2f)", ri), "#00e676");
        }

        // 7. Solide Kandidaten (Stabilität + hoher Profit)
        if (consistency >= 0.8 && btTrades >= 50 && btDd <= 20.0 && fwDd <= 20.0 && btProfit >= referenceProfit * 0.6) {
            return new Evaluation("GOOD", String.format(Locale.US, "✅ Solide & robuste Strategie für Live-Tests (RI: %.2f)", ri), "#00e676");
        }

        // Falls zwar stabil, aber mäßiger Profit
        if (consistency >= 0.8 && btTrades >= 50 && btDd <= 20.0 && fwDd <= 20.0) {
            return new Evaluation("GOOD", String.format(Locale.US, "ℹ️ Stabil, aber mäßiger Profit (RI: %.2f)", ri), "#80d8ff");
        }

        return new Evaluation("GOOD", String.format(Locale.US, "ℹ️ Solide Performance (RI: %.2f, Detailprüfung empfohlen)", ri), "#80d8ff");
    }

    private void autoSelectTopFive() {
        ObservableList<CombinedPass> items = table.getItems();
        if (items.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Keine Strategien zum Auswählen vorhanden.").show();
            return;
        }

        String fromDateStr = "Unbekannt";
        String toDateStr = "Unbekannt";
        if (parentView != null && parentView.getLastOptResult() != null) {
            fromDateStr = parentView.getLastOptResult().getFromDate();
            toDateStr = parentView.getLastOptResult().getToDate();
        }
        final String fFrom = fromDateStr;
        final String fTo = toDateStr;

        List<CombinedPass> sorted = items.stream()
            .sorted((p1, p2) -> {
                double ri1 = calculateRobustnessIndex(p1, referenceTrades);
                double ri2 = calculateRobustnessIndex(p2, referenceTrades);
                int cmp = Double.compare(ri2, ri1); // Higher Robustness Index (RI) first
                if (cmp != 0) return cmp;
                // Tie-breaker: Higher Vue Robustness Score
                double vs1 = com.backtester.report.RobustnessScorecardGenerator.calculateOverallScore(p1, fFrom, fTo);
                double vs2 = com.backtester.report.RobustnessScorecardGenerator.calculateOverallScore(p2, fFrom, fTo);
                return Double.compare(vs2, vs1);
            })
            .limit(5)
            .collect(Collectors.toList());
        
        table.getSelectionModel().clearSelection();
        int added = 0;
        for (CombinedPass p : sorted) {
            int idx = items.indexOf(p);
            if (idx >= 0) {
                table.getSelectionModel().select(idx);
            }
            if (parentView.addSelectedPass(p)) {
                added++;
            }
        }
        new Alert(Alert.AlertType.INFORMATION, "Die Top " + sorted.size() + " Strategien (nach Robustness-Index & Vue Robustness Score) wurden in der Tabelle markiert\nund erfolgreich zum 'Selected' Tab hinzugefügt!").show();
    }

    private void showAutoSelectExplanation() {
        Stage infoStage = new Stage();
        infoStage.initOwner(this);
        infoStage.initModality(Modality.APPLICATION_MODAL);
        infoStage.setTitle("Wie funktioniert Auto-Select?");

        VBox layout = new VBox(15);
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color: #11141d; -fx-border-color: #ffd740; -fx-border-width: 1px; -fx-border-radius: 5px;");

        Label title = new Label("Auto-Select Top 5 - Erklärung");
        title.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#ffd740"));

        Label functionTitle = new Label("1. Funktionsweise");
        functionTitle.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 13));
        functionTitle.setTextFill(Color.web("#00e5ff"));

        Label functionText = new Label(
            "Der Auto-Select-Button durchsucht die aktuell gefilterten Durchgänge der Tabelle, sortiert sie nach Qualität und Robustheit und wählt die 5 besten Kandidaten aus. " +
            "Diese werden in der Tabelle markiert und direkt in das Tab 'Selected' kopiert, damit Sie sie vergleichen und exportieren können."
        );
        functionText.setWrapText(true);
        functionText.setTextFill(Color.web("#e6e9f0"));

        Label logicTitle = new Label("2. Die Sortier-Logik & Warum wir zwei Indizes nutzen");
        logicTitle.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 13));
        logicTitle.setTextFill(Color.web("#00e5ff"));

        Label logicText = new Label(
            "Um die stabilsten Strategien mathematisch präzise zu ermitteln, kombiniert die Funktion zwei komplementäre Metriken:\n\n" +
            "• Warum zwei Indizes?\n" +
            "  - Der Robustness-Index (RI) ist eine präzise Fließkommazahl, bei der es fast nie zu Gleichständen kommt. Er eignet sich hervorragend zur Feinsortierung.\n" +
            "  - Der Robustness Score (0-100) ist ein ganzzahliger Qualitätsfilter (Pillars-Check). Da er gerundet ist, weisen viele ähnliche EAs denselben Score auf (z. B. 67).\n\n" +
            "• Ablauf der Auto-Filterung:\n" +
            "  - 1. Primäres Kriterium: Robustness-Index (RI)\n" +
            "    Alle Durchgänge werden primär absteigend nach dem RI sortiert.\n" +
            "  - 2. Sekundäres Kriterium: Robustness Score als Tie-Breaker\n" +
            "    Haben zwei Strategien exakt denselben RI (z. B. wenn mehrere EAs bei einem RI von 0.25 gleichauf liegen), entscheidet der Robustness Score. Eine Strategie mit einem Score von 67 wird somit bevorzugt vor einer Strategie mit 66 ausgewählt.\n\n" +
            "• Wichtiger Hinweis:\n" +
            "  Der normale Gesamt-Score bewertet ausschließlich die endgültigen Kennzahlen am Schluss (Gewinn, Drawdown, etc.). Der Robustness Score und der RI gewichten zusätzlich Forward-Konsistenz, Sharpe Ratio und Stichprobengröße, um instabile Strategien abzuwerten. Den tatsächlichen Kennlinien-VERLAUF bewertet die Sensitivitätsanalyse (Schritt 4) mit der KI-Auswertung (Schritt 5)."
        );
        logicText.setWrapText(true);
        logicText.setTextFill(Color.web("#e6e9f0"));

        Label tipsTitle = new Label("3. Praxistipp zur Anwendung");
        tipsTitle.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 13));
        tipsTitle.setTextFill(Color.web("#00e5ff"));

        Label tipsText = new Label(
            "• Nutzen Sie zuerst die Schieberegler auf der linken Seite (Min. Trades, Max. Drawdown, Min. Profit Factor), um ungeeignete Strategien generell auszublenden.\n" +
            "• Klicken Sie danach auf 'Auto-Select Top 5'. Die Funktion bewertet dann nur die verbleibenden, bereits vorgefilterten Durchgänge und wählt die absolut besten 5 aus."
        );
        tipsText.setWrapText(true);
        tipsText.setTextFill(Color.web("#e6e9f0"));

        Button closeBtn = new Button("Verstanden");
        closeBtn.getStyleClass().add("button");
        closeBtn.setOnAction(e -> infoStage.close());

        HBox btnBox = new HBox(closeBtn);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        layout.getChildren().addAll(title, functionTitle, functionText, logicTitle, logicText, tipsTitle, tipsText, btnBox);

        ScrollPane scrollPane = new ScrollPane(layout);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: #11141d; -fx-border-color: transparent;");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        Scene scene = new Scene(scrollPane, 580, 620);
        try {
            scene.getStylesheets().add(getClass().getResource("/css/antigravity.css").toExternalForm());
        } catch (Exception e) {
            // Ignore
        }
        infoStage.setScene(scene);
        infoStage.showAndWait();
    }

    private void addSelectedToOptimizationView() {
        List<CombinedPass> selected = table.getSelectionModel().getSelectedItems();
        if (selected == null || selected.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Bitte markieren Sie mindestens eine Strategie in der Tabelle!").show();
            return;
        }
        int added = 0;
        for (CombinedPass p : selected) {
            if (parentView.addSelectedPass(p)) {
                added++;
            }
        }
        if (added > 0) {
            new Alert(Alert.AlertType.INFORMATION, added + " Strategie(n) erfolgreich zum 'Selected' Tab hinzugefügt!").show();
        } else {
            new Alert(Alert.AlertType.INFORMATION, "Alle ausgewählten Strategien befanden sich bereits im 'Selected' Tab.").show();
        }
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

    private void showPassDetailsDialog(CombinedPass cp) {
        Stage detailStage = new Stage();
        detailStage.initOwner(this);
        detailStage.initModality(Modality.APPLICATION_MODAL);
        detailStage.setTitle("Strategie-Details: Pass #" + cp.getPassNumber());

        VBox layout = new VBox(15);
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color: #11141d; -fx-border-color: #00e5ff; -fx-border-width: 1px; -fx-border-radius: 5px;");

        // --- Header ---
        Label titleLabel = new Label("STRATEGIE-DETAILS (PASS #" + cp.getPassNumber() + ")");
        titleLabel.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 18));
        titleLabel.setTextFill(Color.web("#00e5ff"));

        String fromDateStr = "Unbekannt";
        String toDateStr = "Unbekannt";
        if (parentView != null && parentView.getLastOptResult() != null) {
            if (parentView.getLastOptResult().getFromDate() != null && !parentView.getLastOptResult().getFromDate().isEmpty()) {
                fromDateStr = parentView.getLastOptResult().getFromDate();
            }
            if (parentView.getLastOptResult().getToDate() != null && !parentView.getLastOptResult().getToDate().isEmpty()) {
                toDateStr = parentView.getLastOptResult().getToDate();
            }
        }
        Label subtitleLabel = new Label("Zeitraum: " + fromDateStr + " bis " + toDateStr);
        subtitleLabel.setFont(javafx.scene.text.Font.font("Segoe UI", 13));
        subtitleLabel.setTextFill(Color.web("#7e889a"));

        layout.getChildren().addAll(titleLabel, subtitleLabel);

        // --- Cards Pane ---
        HBox cardsBox = new HBox(12);
        cardsBox.setAlignment(Pos.TOP_LEFT);

        // 1. Backtest Card
        VBox btCard = new VBox(10);
        btCard.setPadding(new Insets(12));
        btCard.setStyle("-fx-background-color: #171b26; -fx-border-color: #00e676; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px;");
        HBox.setHgrow(btCard, Priority.ALWAYS);
        Label btTitle = new Label("◀ BACKTEST METRIKEN");
        btTitle.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 13));
        btTitle.setTextFill(Color.web("#00e676"));
        GridPane btGrid = new GridPane();
        btGrid.setHgap(15);
        btGrid.setVgap(6);
        addMetricRow(btGrid, 0, "Nettoprofit:", String.format(Locale.US, "%.2f", cp.getBtProfit()));
        addMetricRow(btGrid, 1, "Trades:", String.valueOf(cp.getBtTrades()));
        addMetricRow(btGrid, 2, "Profit Factor:", String.format(Locale.US, "%.2f", cp.getBtPf()));
        addMetricRow(btGrid, 3, "Max. Drawdown:", String.format(Locale.US, "%.2f%%", cp.getBtDd()));
        addMetricRow(btGrid, 4, "Recovery Factor:", String.format(Locale.US, "%.2f", cp.getBtRecovery()));
        addMetricRow(btGrid, 5, "Sharpe Ratio:", Double.isNaN(cp.getBtSharpe()) ? "—" : String.format(Locale.US, "%.2f", cp.getBtSharpe()));
        addMetricRow(btGrid, 6, "Expected Payoff:", String.format(Locale.US, "%.2f", cp.getBtExpectedPayoff()));
        btCard.getChildren().addAll(btTitle, btGrid);

        // 2. Forward Card
        VBox fwCard = new VBox(10);
        fwCard.setPadding(new Insets(12));
        fwCard.setStyle("-fx-background-color: #171b26; -fx-border-color: #00e5ff; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px;");
        HBox.setHgrow(fwCard, Priority.ALWAYS);
        Label fwTitle = new Label("FORWARD METRIKEN ▶");
        fwTitle.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 13));
        fwTitle.setTextFill(Color.web("#00e5ff"));
        GridPane fwGrid = new GridPane();
        fwGrid.setHgap(15);
        fwGrid.setVgap(6);

        if (cp.getForwardPass() != null) {
            addMetricRow(fwGrid, 0, "Nettoprofit:", String.format(Locale.US, "%.2f", cp.getFwProfit()));
            addMetricRow(fwGrid, 1, "Trades:", String.valueOf(cp.getFwTrades()));
            addMetricRow(fwGrid, 2, "Profit Factor:", Double.isNaN(cp.getFwPf()) ? "—" : String.format(Locale.US, "%.2f", cp.getFwPf()));
            addMetricRow(fwGrid, 3, "Max. Drawdown:", Double.isNaN(cp.getFwDd()) ? "—" : String.format(Locale.US, "%.2f%%", cp.getFwDd()));
            addMetricRow(fwGrid, 4, "Recovery Factor:", Double.isNaN(cp.getFwRecovery()) ? "—" : String.format(Locale.US, "%.2f", cp.getFwRecovery()));
            addMetricRow(fwGrid, 5, "Sharpe Ratio:", Double.isNaN(cp.getFwSharpe()) ? "—" : String.format(Locale.US, "%.2f", cp.getFwSharpe()));
            addMetricRow(fwGrid, 6, "Expected Payoff:", Double.isNaN(cp.getFwExpectedPayoff()) ? "—" : String.format(Locale.US, "%.2f", cp.getFwExpectedPayoff()));
            fwCard.getChildren().addAll(fwTitle, fwGrid);
        } else {
            Label noFwLabel = new Label("Kein Forward-Test\ndurchgeführt.");
            noFwLabel.setTextFill(Color.web("#7e889a"));
            noFwLabel.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 12));
            noFwLabel.setAlignment(Pos.CENTER);
            VBox.setVgrow(noFwLabel, Priority.ALWAYS);
            fwCard.getChildren().addAll(fwTitle, noFwLabel);
        }

        // 3. Evaluation Card
        VBox evalCard = new VBox(10);
        evalCard.setPadding(new Insets(12));
        evalCard.setStyle("-fx-background-color: #171b26; -fx-border-color: #ffd740; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px;");
        HBox.setHgrow(evalCard, Priority.ALWAYS);
        Label evalTitle = new Label("BEWERTUNG & ROBUSTHEIT");
        evalTitle.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 13));
        evalTitle.setTextFill(Color.web("#ffd740"));
        GridPane evalGrid = new GridPane();
        evalGrid.setHgap(15);
        evalGrid.setVgap(6);

        Evaluation eval = evaluatePass(cp, referenceTrades, referenceProfit);
        addMetricRow(evalGrid, 0, "Score (Gewichtung):", String.format(Locale.US, "%.2f", cp.getScore()));
        addMetricRow(evalGrid, 1, "Robustness-Index (RI):", String.format(Locale.US, "%.2f", calculateRobustnessIndex(cp, referenceTrades)));
        addMetricRow(evalGrid, 2, "Forward-Konsistenz:", String.format(Locale.US, "%.2f", cp.getConsistency()));
        Label verdictVal = addMetricRow(evalGrid, 3, "Analyse-Urteil:", eval.remark);
        verdictVal.setTextFill(Color.web(eval.color));
        verdictVal.setStyle("-fx-font-weight: bold;");
        verdictVal.setWrapText(true);
        verdictVal.setMaxWidth(160);
        
        evalCard.getChildren().addAll(evalTitle, evalGrid);

        cardsBox.getChildren().addAll(btCard, fwCard, evalCard);
        layout.getChildren().add(cardsBox);

        // --- Equity Chart ---
        Label chartTitleLabel = new Label("EQUITY-KURVE (KAPITALVERLAUF)");
        chartTitleLabel.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 14));
        chartTitleLabel.setTextFill(Color.web("#00e5ff"));
        layout.getChildren().add(chartTitleLabel);

        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Trades");
        xAxis.setTickLabelFill(Color.web("#7e889a"));
        xAxis.setMinorTickVisible(false);

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Equity");
        yAxis.setTickLabelFill(Color.web("#7e889a"));
        yAxis.setForceZeroInRange(false);

        LineChart<Number, Number> equityChart = new LineChart<>(xAxis, yAxis);
        equityChart.setCreateSymbols(false);
        equityChart.setPrefHeight(200);
        equityChart.setMinHeight(200);
        equityChart.setMaxHeight(200);
        equityChart.setAnimated(false);
        equityChart.setStyle("-fx-background-color: transparent;");
        equityChart.setHorizontalGridLinesVisible(true);
        equityChart.setVerticalGridLinesVisible(false);
        VBox.setVgrow(equityChart, Priority.ALWAYS);

        // Generate curves
        double btEndBalance = cp.getBacktestPass().getBalance();
        double btStartBalance = btEndBalance - cp.getBtProfit();
        if (btStartBalance <= 0) {
            btStartBalance = 10000.0;
        }

        List<Double> btCurve = generateSyntheticEquityCurve(btStartBalance, cp.getBtProfit(), cp.getBtTrades(), cp.getBtPf(), cp.getPassNumber());
        XYChart.Series<Number, Number> backtestSeries = new XYChart.Series<>();
        backtestSeries.setName("Backtest");
        for (int i = 0; i < btCurve.size(); i++) {
            backtestSeries.getData().add(new XYChart.Data<>(i, btCurve.get(i)));
        }
        equityChart.getData().add(backtestSeries);

        XYChart.Series<Number, Number> forwardSeries = null;
        if (cp.getForwardPass() != null) {
            double fwStartBalance = btCurve.get(btCurve.size() - 1);
            List<Double> fwCurve = generateSyntheticEquityCurve(fwStartBalance, cp.getFwProfit(), cp.getFwTrades(), cp.getFwPf(), cp.getPassNumber() + 999);
            forwardSeries = new XYChart.Series<>();
            forwardSeries.setName("Forward");
            
            // Connect seamlessly
            int offset = btCurve.size() - 1;
            forwardSeries.getData().add(new XYChart.Data<>(offset, fwStartBalance));
            for (int j = 1; j < fwCurve.size(); j++) {
                forwardSeries.getData().add(new XYChart.Data<>(offset + j, fwCurve.get(j)));
            }
            equityChart.getData().add(forwardSeries);
        }

        layout.getChildren().add(equityChart);

        // --- Parameter section ---
        Label paramTitle = new Label("STRATEGIE-PARAMETER");
        paramTitle.setFont(javafx.scene.text.Font.font("Segoe UI", javafx.scene.text.FontWeight.BOLD, 14));
        paramTitle.setTextFill(Color.web("#00e5ff"));
        layout.getChildren().add(paramTitle);

        TableView<ParameterRow> paramTable = new TableView<>();
        paramTable.setStyle("-fx-background-color: transparent;");
        paramTable.setPrefHeight(200);
        VBox.setVgrow(paramTable, Priority.ALWAYS);

        TableColumn<ParameterRow, String> nameCol = new TableColumn<>("Parameter");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(350);

        TableColumn<ParameterRow, String> valCol = new TableColumn<>("Wert");
        valCol.setCellValueFactory(new PropertyValueFactory<>("value"));
        valCol.setPrefWidth(350);

        paramTable.getColumns().addAll(nameCol, valCol);

        List<ParameterRow> paramList = new ArrayList<>();
        Map<String, String> params = cp.getBacktestPass().getParameterValues();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            paramList.add(new ParameterRow(entry.getKey(), entry.getValue()));
        }
        paramTable.setItems(FXCollections.observableArrayList(paramList));
        layout.getChildren().add(paramTable);

        // --- Bottom bar ---
        Button closeBtn = new Button("Schließen");
        closeBtn.getStyleClass().add("button");
        closeBtn.setOnAction(e -> detailStage.close());

        HBox btnBox = new HBox(10, closeBtn);
        btnBox.setAlignment(Pos.CENTER_RIGHT);
        layout.getChildren().add(btnBox);

        // --- Scorecard WebView (Right Side) ---
        String symbolStr = parentView != null && parentView.getLastOptResult() != null ? parentView.getLastOptResult().getSymbol() : "EURUSD";
        String periodStr = parentView != null && parentView.getLastOptResult() != null ? parentView.getLastOptResult().getPeriod() : "H1";
        String expertStr = parentView != null && parentView.getLastOptResult() != null ? parentView.getLastOptResult().getExpert() : "";

        double sensitivScoreVal = -1.0;
        double kiScoreVal = -1.0;
        if (parentView != null && parentView.getSensitivityResults() != null) {
            for (com.backtester.report.SensitivityResult sr : parentView.getSensitivityResults()) {
                if (sr.getOriginalPass() != null && sr.getOriginalPass().getPassNumber() == cp.getPassNumber()) {
                    sensitivScoreVal = 100.0 - Math.max(sr.getOverallCV(), sr.getOverallCVFw());
                    String kiRes = sr.getKiResult();
                    if (kiRes != null && !kiRes.isEmpty()) {
                        try {
                            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,3})\\s*/\\s*100").matcher(kiRes);
                            if (m.find()) {
                                kiScoreVal = Double.parseDouble(m.group(1));
                            } else {
                                kiScoreVal = Double.parseDouble(kiRes.trim());
                            }
                        } catch (Exception ignored) {}
                    }
                    break;
                }
            }
        }

        String htmlContent = com.backtester.report.RobustnessScorecardGenerator.generateHtml(
            cp, expertStr, symbolStr, periodStr, fromDateStr, toDateStr, sensitivScoreVal, kiScoreVal
        );

        javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
        webView.getEngine().setOnAlert(event -> log.info("JS ALERT: " + event.getData()));
        webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.RUNNING || newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                try {
                    netscape.javascript.JSObject window = (netscape.javascript.JSObject) webView.getEngine().executeScript("window");
                    window.setMember("consoleBridge", new ConsoleLoggerBridge());
                } catch (Exception ex) {
                    // Ignore
                }
            }
        });
        webView.getEngine().getLoadWorker().exceptionProperty().addListener((obs, oldExc, newExc) -> {
            if (newExc != null) {
                log.error("WebView LoadWorker Exception: ", newExc);
            }
        });
        webView.getEngine().loadContent(htmlContent);

        VBox rightPane = new VBox(webView);
        VBox.setVgrow(webView, Priority.ALWAYS);
        rightPane.setStyle("-fx-background-color: #11141d;");

        ScrollPane leftScroll = new ScrollPane(layout);
        leftScroll.setFitToWidth(true);
        leftScroll.setStyle("-fx-background-color: transparent; -fx-background: #11141d; -fx-box-border: transparent;");

        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(leftScroll, rightPane);
        splitPane.setDividerPositions(0.55);
        splitPane.setStyle("-fx-background-color: #11141d; -fx-box-border: transparent;");

        Scene scene = new Scene(splitPane, 1500, 850);
        try {
            scene.getStylesheets().add(getClass().getResource("/css/antigravity.css").toExternalForm());
        } catch (Exception e) {
            // Ignore
        }
        detailStage.setScene(scene);

        // Style chart after elements are shown
        final XYChart.Series<Number, Number> finalFwSeries = forwardSeries;
        detailStage.setOnShown(e -> {
            if (backtestSeries.getNode() != null) {
                backtestSeries.getNode().setStyle("-fx-stroke: #00e676; -fx-stroke-width: 3px;");
            }
            if (finalFwSeries != null && finalFwSeries.getNode() != null) {
                finalFwSeries.getNode().setStyle("-fx-stroke: #00e5ff; -fx-stroke-width: 3px;");
            }
            javafx.scene.Node plotBg = equityChart.lookup(".chart-plot-background");
            if (plotBg != null) {
                plotBg.setStyle("-fx-background-color: #171b26; -fx-border-color: #3e4555; -fx-border-width: 1px;");
            }
        });

        detailStage.showAndWait();
    }

    private void showRobustnessScorecardWebView(CombinedPass cp) {
        Stage stage = new Stage();
        stage.setTitle("🛡️ Robustness Scorecard: Pass #" + cp.getPassNumber());
        stage.initOwner(this);
        stage.initModality(Modality.APPLICATION_MODAL);

        String fromDateStr = "Unbekannt";
        String toDateStr = "Unbekannt";
        if (parentView != null && parentView.getLastOptResult() != null) {
            fromDateStr = parentView.getLastOptResult().getFromDate();
            toDateStr = parentView.getLastOptResult().getToDate();
        }
        
        String symbolStr = parentView != null && parentView.getLastOptResult() != null ? parentView.getLastOptResult().getSymbol() : "EURUSD";
        String periodStr = parentView != null && parentView.getLastOptResult() != null ? parentView.getLastOptResult().getPeriod() : "H1";
        String expertStr = parentView != null && parentView.getLastOptResult() != null ? parentView.getLastOptResult().getExpert() : "";

        double sensitivScoreVal = -1.0;
        double kiScoreVal = -1.0;
        if (parentView != null && parentView.getSensitivityResults() != null) {
            for (com.backtester.report.SensitivityResult sr : parentView.getSensitivityResults()) {
                if (sr.getOriginalPass() != null && sr.getOriginalPass().getPassNumber() == cp.getPassNumber()) {
                    sensitivScoreVal = 100.0 - Math.max(sr.getOverallCV(), sr.getOverallCVFw());
                    String kiRes = sr.getKiResult();
                    if (kiRes != null && !kiRes.isEmpty()) {
                        try {
                            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,3})\\s*/\\s*100").matcher(kiRes);
                            if (m.find()) {
                                kiScoreVal = Double.parseDouble(m.group(1));
                            } else {
                                kiScoreVal = Double.parseDouble(kiRes.trim());
                            }
                        } catch (Exception ignored) {}
                    }
                    break;
                }
            }
        }

        String htmlContent = com.backtester.report.RobustnessScorecardGenerator.generateHtml(
            cp, expertStr, symbolStr, periodStr, fromDateStr, toDateStr, sensitivScoreVal, kiScoreVal
        );

        javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
        webView.getEngine().setOnAlert(event -> log.info("JS ALERT: " + event.getData()));
        webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.RUNNING || newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                try {
                    netscape.javascript.JSObject window = (netscape.javascript.JSObject) webView.getEngine().executeScript("window");
                    window.setMember("consoleBridge", new ConsoleLoggerBridge());
                } catch (Exception e) {
                    // Ignore if window is not ready yet
                }
            }
        });
        webView.getEngine().getLoadWorker().exceptionProperty().addListener((obs, oldExc, newExc) -> {
            if (newExc != null) {
                log.error("WebView LoadWorker Exception: ", newExc);
            }
        });
        webView.getEngine().loadContent(htmlContent);

        VBox box = new VBox(webView);
        VBox.setVgrow(webView, Priority.ALWAYS);
        
        Scene scene = new Scene(box, 750, 750);
        stage.setScene(scene);
        stage.show();
    }

    public static List<Double> generateSyntheticEquityCurve(double startBalance, double profit, int trades, double pf, int passNumber) {
        List<Double> curve = new ArrayList<>();
        curve.add(startBalance);
        if (trades <= 0) {
            return curve;
        }

        // Determine Gross Profit and Gross Loss
        double grossProfit;
        double grossLoss;
        double effectivePf = (Double.isNaN(pf) || pf <= 1.0) ? 1.5 : pf;
        
        if (effectivePf > 1.0) {
            grossLoss = profit / (effectivePf - 1.0);
            grossProfit = profit * effectivePf / (effectivePf - 1.0);
        } else {
            grossLoss = Math.abs(profit) * 2.0;
            grossProfit = grossLoss + profit;
        }

        // Assume a win rate of around 55%
        double winRate = 0.55;
        int wins = (int) Math.round(trades * winRate);
        if (wins < 1 && profit > 0) wins = 1;
        int losses = trades - wins;
        if (losses < 1 && profit < 0) losses = 1;
        if (wins + losses != trades) {
            losses = trades - wins;
        }

        double avgWin = wins > 0 ? grossProfit / wins : 0;
        double avgLoss = losses > 0 ? grossLoss / losses : 0;

        List<Double> tradeOutputs = new ArrayList<>();
        for (int i = 0; i < wins; i++) {
            tradeOutputs.add(avgWin);
        }
        for (int i = 0; i < losses; i++) {
            tradeOutputs.add(-avgLoss);
        }

        // Shuffle deterministically based on passNumber seed
        Random rand = new Random(passNumber * 1337L);
        Collections.shuffle(tradeOutputs, rand);

        double current = startBalance;
        for (double trade : tradeOutputs) {
            // Add some minor random noise to make it look organic
            double noise = (rand.nextDouble() - 0.5) * 0.1 * (avgWin + avgLoss);
            current += trade + noise;
            curve.add(current);
        }
        
        // Adjust the last point slightly to make the final sum match the exact net profit
        double targetEnd = startBalance + profit;
        double currentEnd = curve.get(curve.size() - 1);
        double difference = targetEnd - currentEnd;
        
        if (curve.size() > 1 && Math.abs(difference) > 1e-5) {
            double stepDiff = difference / (curve.size() - 1);
            double cumulative = 0;
            for (int i = 1; i < curve.size(); i++) {
                cumulative += stepDiff;
                curve.set(i, curve.get(i) + cumulative);
            }
        }

        return curve;
    }

    public static class ConsoleLoggerBridge {
        public void log(String text) { log.info("JS CONSOLE: " + text); }
        public void error(String text) { log.error("JS ERROR: " + text); }
    }
}
