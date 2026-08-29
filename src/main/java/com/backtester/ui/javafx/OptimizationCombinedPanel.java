package com.backtester.ui.javafx;

import com.backtester.report.OptimizationResult;
import com.backtester.report.OptimizationResult.CombinedPass;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Combined Analysis UI extracted from OptimizationView.
 */
public final class OptimizationCombinedPanel {

    public interface Host {
        LogView logView();
        javafx.stage.Window ownerWindow();
        OptimizationResult lastOptResult();
        TableView<com.backtester.report.SensitivityResult> sensitivityTable();
        String fromDateFallback();
        String toDateFallback();
        boolean addSelectedPass(CombinedPass pass);
        OptimizationView parentView();
        void runVerificationBacktest(OptimizationResult.Pass pass);
    }

    private final Host host;

    private final OptimizationFilterDialogs.OptimizationFilterState filterState =
            new OptimizationFilterDialogs.OptimizationFilterState();

    private TableView<CombinedPass> combinedTable;
    private ComboBox<String> combinedSortCombo;
    private CheckBox filterEnabledCheck;
    private CheckBox onlyMatchedCheck;
    private Label combinedCountLabel;
    private TextField combinedSearchField;

    private Spinner<Integer> wBtProfitSpin;
    private Spinner<Integer> wFwProfitSpin;
    private Spinner<Integer> wConsistSpin;
    private Spinner<Integer> wRiskSpin;
    private Spinner<Integer> wEquityConsistSpin;
    private Spinner<Integer> wSampleSizeSpin;
    private Spinner<Integer> wFwTradesSpin;
    private Spinner<Integer> wRecoverySpin;

    public OptimizationCombinedPanel(Host host) {
        this.host = host;
    }

    public TableView<CombinedPass> getTable() {
        return combinedTable;
    }

    public boolean isOnlyMatchedSelected() {
        return onlyMatchedCheck != null && onlyMatchedCheck.isSelected();
    }

    public void setOnlyMatchedSelected(boolean selected) {
        if (onlyMatchedCheck != null) {
            onlyMatchedCheck.setSelected(selected);
        }
    }

    public void clearCountLabel() {
        if (combinedCountLabel != null) {
            combinedCountLabel.setText("");
        }
    }

    public VBox createPane() {
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
        filterSettingsBtn.setOnAction(e -> OptimizationFilterDialogs.showFilterDialog(
                filterSettingsBtn, filterState, filterEnabledCheck, this::applyFilter));

        Button weightSettingsBtn = new Button("⚙ Score-Gewichtung...");
        weightSettingsBtn.getStyleClass().add("button");
        weightSettingsBtn.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #ffd740; -fx-border-color: #ffd740; -fx-border-width: 1;");
        weightSettingsBtn.setOnAction(e -> OptimizationFilterDialogs.showScoreWeightsDialog(
                weightSettingsBtn,
                new Spinner[]{wBtProfitSpin, wFwProfitSpin, wConsistSpin, wRiskSpin,
                        wEquityConsistSpin, wSampleSizeSpin, wFwTradesSpin, wRecoverySpin},
                this::applyFilter));

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
            applyFilter();
        });

        filterEnabledCheck = new CheckBox("Filter aktiv");
        filterEnabledCheck.setSelected(false); // standardmäßig aus, um Verwirrung bei ersten Resultaten zu vermeiden
        filterEnabledCheck.setStyle("-fx-text-fill: #00e5ff;");
        filterEnabledCheck.setOnAction(e -> {
            com.backtester.database.DatabaseManager.getInstance().saveSetting("opt.filter.enabled", String.valueOf(filterEnabledCheck.isSelected()));
            applyFilter();
        });

        Button applyFilterBtn = new Button("🔄 Aktualisieren");
        applyFilterBtn.getStyleClass().add("button");
        applyFilterBtn.setOnAction(e -> applyFilter());

        Button delPassBtn = new Button("🗑 Markierte Zeilen entfernen");
        delPassBtn.getStyleClass().addAll("button", "button-cancel");
        delPassBtn.setOnAction(e -> deleteSelectedPasses());

        Button selectStrategiesBtn = new Button("⭐ Select Strategies");
        selectStrategiesBtn.getStyleClass().add("button");
        selectStrategiesBtn.setOnAction(e -> {
            java.util.List<CombinedPass> selected = combinedTable.getSelectionModel().getSelectedItems();
            if (selected == null || selected.isEmpty()) return;
            for (CombinedPass p : selected) {
                host.addSelectedPass(p);
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
        combinedSearchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilter());

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
        combinedTable = createTable();
        VBox.setVgrow(combinedTable, Priority.ALWAYS);

        pane.getChildren().addAll(toolbarContainer, combinedTable);
        VBox.setVgrow(pane, Priority.ALWAYS);
        return pane;
    }

    public OptimizationResult.ScoreWeights getScoreWeightsFromUI() {
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
        OptimizationResult lastOptResult = host.lastOptResult();
        if (lastOptResult == null) {
            new Alert(Alert.AlertType.WARNING, "Keine Optimierungsergebnisse geladen. Führe erst eine Optimierung durch!").show();
            return;
        }
        List<CombinedPass> allCombined = lastOptResult.buildCombinedPasses(onlyMatchedCheck.isSelected(), getScoreWeightsFromUI());
        if (allCombined == null || allCombined.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Keine kombinierten Ergebnisse zum Evaluieren vorhanden.").show();
            return;
        }

        StrategyEvaluatorDialog dialog = new StrategyEvaluatorDialog(allCombined, host.parentView());
        dialog.initOwner(host.ownerWindow());
        dialog.show();
    }

    public static java.util.Comparator<String> numericStringComparator() {
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
    public TableView<CombinedPass> createTable() {
        TableView<CombinedPass> t = new TableView<>();
        t.setStyle("-fx-background-color: transparent;");
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        t.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Score (highlight column)
        TableColumn<CombinedPass, String> scoreCol = new TableColumn<>();
        scoreCol.setGraphic(DocHelper.createHeaderWithTooltip("Score",
            "Unified Score (0-100):\nGewichteter Gesamtwert aus 10 Kriterien. Klicke auf das ⓘ Symbol, um den Mindest-Score-Filter anzupassen und die Doku zu öffnen.",
            this::showScoreDoc));
        scoreCol.setCellValueFactory(c -> new SimpleStringProperty(
                String.format(Locale.US, "%.2f", c.getValue().getScore())));
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
            this::showConsistencyDoc));
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
            for (com.backtester.report.SensitivityResult sr : host.sensitivityTable().getItems()) {
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
            OptimizationResult lastOptResult = host.lastOptResult();
            if (lastOptResult != null) {
                if (lastOptResult.getFromDate() != null && !lastOptResult.getFromDate().isEmpty()) {
                    fromDateStr = lastOptResult.getFromDate();
                }
                if (lastOptResult.getToDate() != null && !lastOptResult.getToDate().isEmpty()) {
                    toDateStr = lastOptResult.getToDate();
                }
            } else {
                String fromFallback = host.fromDateFallback();
                if (fromFallback != null && !fromFallback.isEmpty()) {
                    fromDateStr = fromFallback;
                    String toFallback = host.toDateFallback();
                    if (toFallback != null && !toFallback.isEmpty()) {
                        toDateStr = toFallback;
                    }
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
                    host.runVerificationBacktest(item.getBacktestPass());
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

    private void showPassExplanationDialog(CombinedPass sel) {
        String dateSubtitle = null;
        OptimizationResult lastOptResult = host.lastOptResult();
        if (lastOptResult != null && lastOptResult.getFromDate() != null && !lastOptResult.getFromDate().isEmpty()) {
            dateSubtitle = lastOptResult.getFromDate() + " bis " + lastOptResult.getToDate();
        }
        PassExplanationDialog.show(host.ownerWindow(), sel, dateSubtitle);
    }

    /** Applies current filter settings and re-populates the combined table. */
    private void deleteSelectedPasses() {
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
            OptimizationResult lastOptResult = host.lastOptResult();
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
            applyFilter();
        }
    }

    public void applyFilter() {
        OptimizationResult lastOptResult = host.lastOptResult();
        if (lastOptResult == null || lastOptResult.getPasses().isEmpty()) {
            host.logView().log("WARN", "Noch keine Optimierungsdaten vorhanden.");
            return;
        }

        double minBtProfit    = filterState.filterMinBtProfit;
        double minFwProfit    = filterState.filterMinFwProfit;
        int    minBtTrades    = filterState.filterMinBtTrades;
        int    minFwTrades    = filterState.filterMinFwTrades;
        double maxBtDd        = filterState.filterMaxBtDd;
        double maxFwDd        = filterState.filterMaxFwDd;
        boolean onlyMatched   = onlyMatchedCheck.isSelected();

        // ── Unified Score-Gewichte aus Spinnern lesen
        OptimizationResult.ScoreWeights weights = getScoreWeightsFromUI();

        List<CombinedPass> all = lastOptResult.buildCombinedPasses(onlyMatched, weights);

        List<CombinedPass> filtered = all;
        if (filterEnabledCheck != null && filterEnabledCheck.isSelected()) {
            filtered = all.stream()
                .filter(cp -> cp.getBtProfit() >= filterState.filterMinBtProfit)
                .filter(cp -> Double.isNaN(cp.getFwProfit()) ? (filterState.filterMinFwProfit <= 0.0) : (cp.getFwProfit() >= filterState.filterMinFwProfit))
                .filter(cp -> cp.getBtTrades() >= filterState.filterMinBtTrades)
                .filter(cp -> cp.getFwTrades() >= filterState.filterMinFwTrades)
                .filter(cp -> cp.getBtDd() <= filterState.filterMaxBtDd)
                .filter(cp -> Double.isNaN(cp.getFwDd()) ? (filterState.filterMaxFwDd >= 100.0) : (cp.getFwDd() <= filterState.filterMaxFwDd))
                .filter(cp -> cp.getBtSharpe() >= filterState.filterMinBtSharpe)
                .filter(cp -> Double.isNaN(cp.getFwSharpe()) ? (filterState.filterMinFwSharpe <= 0.0) : (cp.getFwSharpe() >= filterState.filterMinFwSharpe))
                .filter(cp -> cp.getBtRecovery() >= filterState.filterMinBtRecovery)
                .filter(cp -> Double.isNaN(cp.getFwRecovery()) ? (filterState.filterMinFwRecovery <= 0.0) : (cp.getFwRecovery() >= filterState.filterMinFwRecovery))
                .filter(cp -> cp.getBtExpectedPayoff() >= filterState.filterMinBtPayoff)
                .filter(cp -> Double.isNaN(cp.getFwExpectedPayoff()) ? (filterState.filterMinFwPayoff <= 0.0) : (cp.getFwExpectedPayoff() >= filterState.filterMinFwPayoff))
                .filter(cp -> cp.getScore() >= filterState.filterMinScore)
                .filter(cp -> cp.getConsistency() >= filterState.filterMinConsistency)
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
        host.logView().log("INFO", "Unified Score: " + filtered.size() + " Passes (10 Säulen)");
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

    public void loadFilterAndWeightPreferences() {
        try {
            com.backtester.database.DatabaseManager db = com.backtester.database.DatabaseManager.getInstance();
            filterState.filterMinScore = Double.parseDouble(db.getSetting("opt.filter.minScore", String.valueOf(filterState.filterMinScore)));
            filterState.filterMinConsistency = Double.parseDouble(db.getSetting("opt.filter.minConsistency", String.valueOf(filterState.filterMinConsistency)));

            filterState.filterMinBtProfit = Double.parseDouble(db.getSetting("opt.filter.minBtProfit", "0.01"));
            filterState.filterMinFwProfit = Double.parseDouble(db.getSetting("opt.filter.minFwProfit", "0.01"));
            filterState.filterMinBtTrades = Integer.parseInt(db.getSetting("opt.filter.minBtTrades", "100"));
            filterState.filterMinFwTrades = Integer.parseInt(db.getSetting("opt.filter.minFwTrades", "50"));
            filterState.filterMaxBtDd = Double.parseDouble(db.getSetting("opt.filter.maxBtDd", "100.0"));
            filterState.filterMaxFwDd = Double.parseDouble(db.getSetting("opt.filter.maxFwDd", "100.0"));
            filterState.filterMinBtPayoff = Double.parseDouble(db.getSetting("opt.filter.minBtPayoff", "0.0"));
            filterState.filterMinFwPayoff = Double.parseDouble(db.getSetting("opt.filter.minFwPayoff", "0.0"));
            filterState.filterMinBtSharpe = Double.parseDouble(db.getSetting("opt.filter.minBtSharpe", "0.0"));
            filterState.filterMinFwSharpe = Double.parseDouble(db.getSetting("opt.filter.minFwSharpe", "0.0"));
            filterState.filterMinBtRecovery = Double.parseDouble(db.getSetting("opt.filter.minBtRecovery", "1.0"));
            filterState.filterMinFwRecovery = Double.parseDouble(db.getSetting("opt.filter.minFwRecovery", "1.0"));

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
            org.slf4j.LoggerFactory.getLogger(OptimizationCombinedPanel.class)
                    .error("Failed to load weights and filters from DB", e);
        }
    }

    private void showScoreDoc() {
        OptimizationScoreDocs.showScoreDoc(
                host.ownerWindow(),
                filterState.filterMinScore,
                v -> filterState.filterMinScore = v,
                () -> {
                    if (filterEnabledCheck != null) {
                        filterEnabledCheck.setSelected(true);
                    }
                },
                this::applyFilter);
    }

    private void showConsistencyDoc() {
        OptimizationScoreDocs.showConsistencyDoc(
                host.ownerWindow(),
                filterState.filterMinConsistency,
                v -> filterState.filterMinConsistency = v,
                () -> {
                    if (filterEnabledCheck != null) {
                        filterEnabledCheck.setSelected(true);
                    }
                },
                this::applyFilter);
    }
}
