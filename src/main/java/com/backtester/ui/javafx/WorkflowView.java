package com.backtester.ui.javafx;

import com.backtester.config.AppConfig;
import com.backtester.config.EaParameter;
import com.backtester.engine.WorkflowEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.backtester.report.OptimizationResult;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.SensitivityResult;
import com.backtester.engine.BacktestConfig;
import com.backtester.engine.BacktestRunner;
import com.backtester.report.BacktestResult;
import com.backtester.config.EaParameterManager;
import javafx.scene.input.MouseButton;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import netscape.javascript.JSObject;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.TableCell;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User interface panel for the visual Automated multi-step Workflow.
 */
public class WorkflowView {
    private static final Logger log = LoggerFactory.getLogger(WorkflowView.class);

    private final BorderPane root;
    private final LogView globalLogView;
    private final WorkflowEngine engine;
    private Tab workflowTab;

    // UI elements
    private VBox flowchartBox;
    private Button runAllBtn;
    private Button cancelBtn;
    private Button resetBtn;
    private Button clearResultsBtn;
    private ProgressBar progressBar;
    private Label progressLabel;
    private TextArea consoleLog;

    // Bottom pane tabs and table
    private TabPane tabPane;
    private Tab resultsTab;
    private TableView<CombinedPass> resultsTable;
    private Label noDataLabel;
    private int selectedStep = -1;
    private Tab kiReportTab;
    private javafx.scene.web.WebView kiWebView;
    private Tab parametersTab;
    private TableView<EaParameter> parametersTable;
    private Tab statisticsTab;
    private TableView<OptimizationStatsRow> statsTable;
    private Label noStatsLabel;

    // Visual boxes for the 6 stages
    private VBox step1Box, step2Box, step3Box, step4Box, step5Box, step6Box;
    private Label step1Status, step2Status, step3Status, step4Status, step5Status, step6Status;
    private Label step1Details, step2Details, step3Details, step4Details, step5Details, step6Details;
    private ProgressIndicator step1Spinner, step2Spinner, step3Spinner, step4Spinner, step5Spinner, step6Spinner;
    private int currentlyRunningStep = -1;
    private volatile String currentRunningSymbol = "";
    private volatile int currentSymbolIndex = 0;
    private volatile int totalSymbolsCount = 0;

    private Task<Void> activeWorkflowTask;
    private final JavaBridge javaBridge = new JavaBridge(this);

    public WorkflowView(LogView globalLogView) {
        this.globalLogView = globalLogView;
        this.engine = new WorkflowEngine(AppConfig.getInstance());

        root = new BorderPane();
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: transparent;");

        // Top Header
        VBox header = new VBox(5);
        header.setPadding(new Insets(0, 0, 15, 0));
        Label title = new Label("🔄 WORKFLOW AUTOMATION PIPELINE");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        title.setTextFill(Color.web("#00e5ff"));
        Label desc = new Label("Optimieren, filtern, stresse-testen und bewerten Sie Ihre Handelsstrategien vollautomatisch in einem durchgehenden Workflow.");
        desc.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 13px;");
        header.getChildren().addAll(title, desc);
        root.setTop(header);

        // Center visual flowchart and controls
        VBox mainContent = new VBox(15);
        mainContent.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(mainContent, Priority.ALWAYS);

        // Flowchart horizontal row
        HBox flowchart = createFlowchartRow();
        mainContent.getChildren().add(flowchart);

        // Controls bar
        HBox controlsRow = createControlsRow();
        mainContent.getChildren().add(controlsRow);

        // Tabbed results & logs
        tabPane = createBottomTabPane();
        mainContent.getChildren().add(tabPane);

        root.setCenter(mainContent);

        // Load saved state or default to step 1
        int activeStep = engine.getLastActiveStep();
        if (activeStep > 0) {
            selectStep(activeStep);
        } else {
            selectStep(1);
        }
    }

    public BorderPane getView() {
        return root;
    }

    public void bindTab(Tab tab) {
        this.workflowTab = tab;
    }

    private void setTabRunning(boolean running) {
        if (workflowTab != null) {
            Platform.runLater(() -> {
                if (running) {
                    if (!workflowTab.getStyleClass().contains("workflow-running-tab")) {
                        workflowTab.getStyleClass().add("workflow-running-tab");
                    }
                    workflowTab.setText("⚡ Workflow Automator [LÄUFT]");
                } else {
                    workflowTab.getStyleClass().remove("workflow-running-tab");
                    workflowTab.setText("🔄 Workflow Automator");
                }
            });
        }
    }

    // --- UI Construction ---

    private HBox createFlowchartRow() {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER);
        row.setPadding(new Insets(15, 5, 15, 5));
        row.getStyleClass().add("sci-fi-panel");

        // Initialize spinners
        step1Spinner = createSpinner();
        step2Spinner = createSpinner();
        step3Spinner = createSpinner();
        step4Spinner = createSpinner();
        step5Spinner = createSpinner();
        step6Spinner = createSpinner();

        // Step 1 Box
        step1Box = buildStepBox("1. Strategie-Auswahl", "Symbol & Parameterbereich festlegen", step1Spinner);
        step1Status = (Label) step1Box.getChildren().get(2);
        step1Details = (Label) step1Box.getChildren().get(3);
        Button btn1 = (Button) step1Box.getChildren().get(4);
        btn1.setOnAction(e -> {
            WorkflowConfigDialogs.showStep1Dialog(engine, root.getScene().getWindow(), () -> {
                updateVisualStates();
                selectStep(1);
            });
        });
        step1Box.setCursor(javafx.scene.Cursor.HAND);
        step1Box.setOnMouseClicked(e -> selectStep(1));

        // Step 2 Box
        step2Box = buildStepBox("2. MT5 Optimizer", "Evolutionäre Parametersuche ausführen", step2Spinner);
        step2Status = (Label) step2Box.getChildren().get(2);
        step2Details = (Label) step2Box.getChildren().get(3);
        Button btn2 = (Button) step2Box.getChildren().get(4);
        btn2.setOnAction(e -> {
            WorkflowConfigDialogs.showStep2Dialog(engine, root.getScene().getWindow());
            updateVisualStates();
            selectStep(2);
        });
        step2Box.setCursor(javafx.scene.Cursor.HAND);
        step2Box.setOnMouseClicked(e -> selectStep(2));

        // Step 3 Box
        step3Box = buildStepBox("3. Diversitäts-Filter", "Top-5 diverse Strategien selektieren", step3Spinner);
        step3Status = (Label) step3Box.getChildren().get(2);
        step3Details = (Label) step3Box.getChildren().get(3);
        Button btn3 = (Button) step3Box.getChildren().get(4);
        btn3.setOnAction(e -> {
            WorkflowConfigDialogs.showStep3Dialog(engine, root.getScene().getWindow());
            updateVisualStates();
            selectStep(3);
        });
        step3Box.setCursor(javafx.scene.Cursor.HAND);
        step3Box.setOnMouseClicked(e -> selectStep(3));

        // Step 4 Box
        step4Box = buildStepBox("4. Robustness Test (CV)", "Robustheits sweeps für Parameter", step4Spinner);
        step4Status = (Label) step4Box.getChildren().get(2);
        step4Details = (Label) step4Box.getChildren().get(3);
        Button btn4 = (Button) step4Box.getChildren().get(4);
        btn4.setOnAction(e -> {
            WorkflowConfigDialogs.showStep4Dialog(engine, root.getScene().getWindow());
            updateVisualStates();
            selectStep(4);
        });
        step4Box.setCursor(javafx.scene.Cursor.HAND);
        step4Box.setOnMouseClicked(e -> selectStep(4));

        // Step 5 Box
        step5Box = buildStepBox("5. KI-Bewertung", "LLM-gestützte Stabilitätseinstufung", step5Spinner);
        step5Status = (Label) step5Box.getChildren().get(2);
        step5Details = (Label) step5Box.getChildren().get(3);
        Button btn5 = (Button) step5Box.getChildren().get(4);
        btn5.setOnAction(e -> {
            WorkflowConfigDialogs.showStep5Dialog(engine, root.getScene().getWindow());
            updateVisualStates();
            selectStep(5);
        });
        step5Box.setCursor(javafx.scene.Cursor.HAND);
        step5Box.setOnMouseClicked(e -> selectStep(5));

        // Step 6 Box
        step6Box = buildStepBox("6. Portfolio Export", "Finale 3-5 Strategien speichern", step6Spinner);
        step6Status = (Label) step6Box.getChildren().get(2);
        step6Details = (Label) step6Box.getChildren().get(3);
        Button btn6 = (Button) step6Box.getChildren().get(4);
        btn6.setOnAction(e -> {
            WorkflowConfigDialogs.showStep6Dialog(engine, root.getScene().getWindow());
            updateVisualStates();
            selectStep(6);
        });
        step6Box.setCursor(javafx.scene.Cursor.HAND);
        step6Box.setOnMouseClicked(e -> selectStep(6));

        // Set up context menus for single step execution
        setupContextMenu(step1Box, 1);
        setupContextMenu(step2Box, 2);
        setupContextMenu(step3Box, 3);
        setupContextMenu(step4Box, 4);
        setupContextMenu(step5Box, 5);
        setupContextMenu(step6Box, 6);

        row.getChildren().addAll(
            step1Box, createArrow(), 
            step2Box, createArrow(), 
            step3Box, createArrow(), 
            step4Box, createArrow(), 
            step5Box, createArrow(), 
            step6Box
        );
        return row;
    }

    private ProgressIndicator createSpinner() {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(16, 16);
        spinner.setMinSize(16, 16);
        spinner.setMaxSize(16, 16);
        spinner.setVisible(false);
        spinner.setManaged(false);
        spinner.setStyle("-fx-progress-color: #00e5ff;");
        return spinner;
    }

    private VBox buildStepBox(String title, String subtitleText, ProgressIndicator spinner) {
        VBox box = new VBox(8);
        box.setPrefWidth(210);
        box.setMinHeight(200);
        box.setPadding(new Insets(12));
        box.setAlignment(Pos.TOP_CENTER);
        box.setStyle("-fx-background-color: rgba(26, 30, 40, 0.7); -fx-border-color: #3e4555; -fx-border-width: 1.5; -fx-border-radius: 6; -fx-background-radius: 6;");

        HBox titleBox = new HBox(5);
        titleBox.setAlignment(Pos.CENTER);

        Label numTitle = new Label(title);
        numTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        numTitle.setTextFill(Color.web("#e6e9f0"));

        Button infoBtn = new Button("ℹ");
        infoBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ffd740; -fx-padding: 0; -fx-font-weight: bold; -fx-cursor: hand; -fx-font-size: 13px;");
        infoBtn.setTooltip(new Tooltip("Erklärung zu diesem Schritt anzeigen"));
        infoBtn.setOnAction(e -> {
            e.consume();
            showStepExplanation(title);
        });

        titleBox.getChildren().addAll(spinner, numTitle, infoBtn);

        Label subtitle = new Label(subtitleText);
        subtitle.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 11px;");
        subtitle.setWrapText(true);
        subtitle.setAlignment(Pos.CENTER);

        Label status = new Label("BEREITSTELLUNG");
        status.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        status.setTextFill(Color.web("#ffb300")); // Orange default

        Label details = new Label("Keine Daten");
        details.setStyle("-fx-text-fill: #b4bac8; -fx-font-size: 11px;");
        details.setWrapText(true);
        details.setAlignment(Pos.CENTER);
        details.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        Button btn = new Button("Konfigurieren");
        btn.setStyle("-fx-font-size: 11px; -fx-padding: 4 10;");

        box.getChildren().addAll(titleBox, subtitle, status, details, btn);
        return box;
    }

    private void showStepExplanation(String stepTitle) {
        Stage infoStage = new Stage();
        javafx.stage.Window owner = root.getScene() != null ? root.getScene().getWindow() : null;
        if (owner != null) {
            infoStage.initOwner(owner);
        }
        infoStage.initModality(Modality.APPLICATION_MODAL);
        infoStage.setTitle("Schritt-Erklärung: " + stepTitle);

        VBox layout = new VBox(15);
        layout.setPadding(new Insets(25));
        layout.setStyle("-fx-background-color: #11141d; -fx-border-color: #ffd740; -fx-border-width: 1px; -fx-border-radius: 5px;");

        Label title = new Label("Pipeline-Schritt: " + stepTitle);
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#ffd740"));

        Label subtitle = new Label("Einfache Erklärung:");
        subtitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        subtitle.setTextFill(Color.web("#00e5ff"));

        String text = "";
        if (stepTitle.contains("1.")) {
            text = "Hier wählen Sie den Handelsroboter (Expert Advisor), das Währungspaar (Symbol) und den Zeitraum für den Backtest aus. " +
                   "Zudem bestimmen Sie, welche Einstellungen (z. B. Stop Loss oder Take Profit) optimiert und gesucht werden sollen.";
        } else if (stepTitle.contains("2.")) {
            text = "Der MetaTrader 5 (MT5) sucht vollautomatisch nach den besten Einstellungen für Ihren Roboter. " +
                   "Durch ein intelligentes Suchverfahren (evolutionärer Algorithmus) werden tausende Einstellungskombinationen getestet, um die profitabelsten zu finden.";
        } else if (stepTitle.contains("3.")) {
            text = "Der Diversitäts-Filter wählt aus allen profitablen Durchgängen des Optimierers maximal 5 Strategien aus, die sich in ihren Einstellungen und ihrem Verhalten möglichst stark voneinander unterscheiden.\n\n" +
                   "1. Das Problem: Klumpenrisiko & Überoptimierung\n" +
                   "Nach einem Optimierungslauf liefert der MetaTrader oft hunderte profitable Varianten. Diese sind jedoch meist fast identisch. Sie nutzen z. B. nur einen minimal verschobenen Stop Loss (z. B. 52 statt 50 Pips) oder eine leicht geänderte Indikator-Periode. Würden Sie diese Strategien alle in ein Portfolio packen und parallel laufen lassen, hätten Sie ein massives Klumpenrisiko:\n" +
                   "• Alle EAs eröffnen fast zeitgleich dieselben Trades.\n" +
                   "• Läuft der Markt gegen Sie, verlieren alle EAs gleichzeitig.\n" +
                   "• Ihr Drawdown multipliziert sich, während keine echte Risikostreuung stattfindet.\n\n" +
                   "Der Diversitäts-Filter löst dieses Problem, indem er sicherstellt, dass nur Strategien kombiniert werden, die den Markt auf unterschiedliche Weise analysieren und handeln.\n\n" +
                   "2. Der mathematische Ähnlichkeits-Check\n" +
                   "Der Algorithmus prüft paarweise, ob eine neue Strategie zu ähnlich zu einer bereits ausgewählten Strategie ist. Dabei werden zwei Kriterien herangezogen:\n\n" +
                   "A) Das Handelsverhalten (Trade-Abweichung):\n" +
                   "Es wird die prozentuale Differenz der ausgeführten Trades im Backtest verglichen:\n" +
                   "Differenz = |Trades_1 - Trades_2| / max(Trades_1, 1)\n" +
                   "Liegt die Abweichung unter 10 %, gilt das Verhalten als ähnlich.\n\n" +
                   "B) Die Parameter-Struktur (Normalisierung nach Suchraum):\n" +
                   "Parameterwerte können nicht einfach absolut verglichen werden (ein Unterschied von 5 Pips bei einem Stop Loss von 10 ist riesig, bei einem Stop Loss von 500 minimal). Daher wird jede Parameter-Abweichung anhand des konfigurierten Suchraums (Bereich = |Endwert - Startwert|) normalisiert. Liegt die normalisierte Abweichung unter 5 %, gilt der Parameter als ähnlich.\n\n" +
                   "Zwei Strategien gelten als ZU ÄHNLICH (und der Kandidat wird aussortiert), wenn:\n" +
                   "• Die Tradeanzahl-Abweichung unter 10 % liegt UND\n" +
                   "• die durchschnittliche Parameter-Abweichung unter 5 % liegt (oder weniger als 1 Parameter signifikant abweicht).\n\n" +
                   "3. Detailliertes Rechenbeispiel:\n" +
                   "Angenommen, der Suchraum für den Stop Loss liegt bei 10 bis 110 (Bereich = 100) und für die MA-Periode bei 10 bis 60 (Bereich = 50).\n\n" +
                   "• Strategie A (bereits ausgewählt):\n" +
                   "  - Stop Loss = 30\n" +
                   "  - MA-Periode = 15\n" +
                   "  - Trades = 80\n\n" +
                   "• Strategie B (Kandidat 1):\n" +
                   "  - Stop Loss = 35 (Abweichung = 5 -> normalisiert: 5 / 100 = 5 %)\n" +
                   "  - MA-Periode = 15 (Abweichung = 0 -> normalisiert: 0 %)\n" +
                   "  - Trades = 78 (Abweichung = |80 - 78| / 80 = 2,5 %)\n" +
                   "  => Analyse: Die Trade-Abweichung (2,5 %) liegt unter 10 % und die durchschnittliche Parameter-Abweichung beträgt nur 2,5 % (unter 5 %). Strategie B ist zu ähnlich und wird AUSSORTIERT.\n\n" +
                   "• Strategie C (Kandidat 2):\n" +
                   "  - Stop Loss = 90 (Abweichung = 60 -> normalisiert: 60 / 100 = 60 %)\n" +
                   "  - MA-Periode = 45 (Abweichung = 30 -> normalisiert: 30 / 50 = 60 %)\n" +
                   "  - Trades = 40 (Abweichung = |80 - 40| / 80 = 50 %)\n" +
                   "  => Analyse: Sowohl die Trade-Abweichung (50 %) als auch die Parameter-Abweichungen (60 %) liegen weit über den Grenzwerten. Strategie C handelt völlig andere Marktphasen und wird als DIVERS AKZEPTIERT.";
        } else if (stepTitle.contains("4.")) {
            text = "Hier wird geprüft, ob die gefundenen Einstellungen auch bei kleinen Marktveränderungen stabil bleiben (Stresstest). " +
                   "Die Parameter werden minimal variiert (z. B. Spread oder Stop-Loss geringfügig verschoben). " +
                   "Strategien, deren Gewinne dabei stark einbrechen, fliegen heraus, da sie wahrscheinlich überoptimiert (curve-fitted) sind.";
        } else if (stepTitle.contains("5.")) {
            text = "Eine künstliche Intelligenz (LLM) analysiert alle Ergebnisse, Kennzahlen und Stresstests der verbleibenden Strategien. " +
                   "Sie vergibt eine Bewertung und gibt Ihnen eine Empfehlung, welche Strategien sich am besten für den echten handel eignen.";
        } else if (stepTitle.contains("6.")) {
            text = "Die verbleibenden, besten 3 bis 5 Strategien werden zu einem gemeinsamen Portfolio zusammengefasst und abgespeichert. " +
                   "Dieses Portfolio können Sie direkt exportieren, um es auf Ihrem Live- oder Demokonto einzusetzen.";
        } else {
            text = "Keine Erklärung für diesen Schritt vorhanden.";
        }

        Label explanationText = new Label(text);
        explanationText.setWrapText(true);
        explanationText.setTextFill(Color.web("#e6e9f0"));
        explanationText.setFont(Font.font("Segoe UI", 12));

        Button closeBtn = new Button("Verstanden");
        closeBtn.getStyleClass().add("button");
        closeBtn.setOnAction(e -> infoStage.close());

        HBox btnBox = new HBox(closeBtn);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        layout.getChildren().addAll(title, subtitle, explanationText, btnBox);

        ScrollPane scrollPane = new ScrollPane(layout);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: #11141d; -fx-border-color: transparent;");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        Scene scene = new Scene(scrollPane, 700, 550);
        try {
            scene.getStylesheets().add(getClass().getResource("/css/antigravity.css").toExternalForm());
        } catch (Exception e) {
            // Ignore
        }
        infoStage.setScene(scene);
        infoStage.showAndWait();
    }

    private Label createArrow() {
        Label arrow = new Label("➔");
        arrow.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        arrow.setTextFill(Color.web("#3e4555"));
        return arrow;
    }

    private HBox createControlsRow() {
        HBox box = new HBox(15);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(10, 15, 10, 15));
        box.getStyleClass().add("sci-fi-panel");

        runAllBtn = new Button("▶ Workflow starten");
        runAllBtn.getStyleClass().addAll("button", "button-start");
        runAllBtn.setOnAction(e -> startWorkflow());

        cancelBtn = new Button("⬛ Abbrechen");
        cancelBtn.getStyleClass().addAll("button", "button-cancel");
        cancelBtn.setDisable(true);
        cancelBtn.setOnAction(e -> cancelWorkflow());

        resetBtn = new Button("🔄 Zurücksetzen");
        resetBtn.getStyleClass().addAll("button");
        resetBtn.setOnAction(e -> resetWorkflow());

        clearResultsBtn = new Button("Ergebnisse löschen");
        clearResultsBtn.getStyleClass().addAll("button");
        clearResultsBtn.setOnAction(e -> clearWorkflowResults());

        progressBar = new ProgressBar(0);
        progressBar.setId("workflow-progress-bar");
        progressBar.setPrefWidth(300);
        HBox.setHgrow(progressBar, Priority.ALWAYS);

        progressLabel = new Label("Bereit");
        progressLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        progressLabel.setTextFill(Color.web("#cbd5e1"));

        Button mainInfoBtn = DocHelper.createThickCircularInfoButton("Erklärung aller Indizes und Kennzahlen", () -> {
            DocHelper.showAllIndicesDocDialog(clearResultsBtn.getScene() != null ? clearResultsBtn.getScene().getWindow() : null);
        });

        box.getChildren().addAll(runAllBtn, cancelBtn, resetBtn, clearResultsBtn, mainInfoBtn, progressBar, progressLabel);
        return box;
    }

    private TabPane createBottomTabPane() {
        tabPane = new TabPane();
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        // Tab 1: Ergebnisse
        resultsTab = new Tab("Ergebnisse: Keine");
        resultsTab.setClosable(false);

        // Tab 2: Parameter & Suchräume
        parametersTab = new Tab("Parameter & Suchräume");
        parametersTab.setClosable(false);

        parametersTable = new TableView<>();
        parametersTable.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(parametersTable, Priority.ALWAYS);

        TableColumn<EaParameter, Boolean> optCol = new TableColumn<>("Opt");
        optCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleBooleanProperty(cellData.getValue().isOptimizeEnabled()));
        optCol.setCellFactory(tc -> new TableCell<EaParameter, Boolean>() {
            private final CheckBox checkBox = new CheckBox();
            {
                checkBox.setDisable(true);
                setAlignment(Pos.CENTER);
            }
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    checkBox.setSelected(item);
                    setGraphic(checkBox);
                }
            }
        });
        optCol.setPrefWidth(50);

        TableColumn<EaParameter, String> nameCol = new TableColumn<>("Variable");
        nameCol.setCellValueFactory(cellData -> {
            EaParameter param = cellData.getValue();
            String display = param.getDisplayName();
            if (display == null || display.trim().isEmpty()) {
                display = param.getName();
            }
            return new javafx.beans.property.SimpleStringProperty(display);
        });
        nameCol.setCellFactory(column -> new TableCell<EaParameter, String>() {
            private final Tooltip tooltip = new Tooltip();
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
        nameCol.setPrefWidth(220);

        TableColumn<EaParameter, String> valCol = new TableColumn<>("Wert");
        valCol.setCellValueFactory(new PropertyValueFactory<>("value"));
        valCol.setPrefWidth(120);

        TableColumn<EaParameter, String> startCol = new TableColumn<>("Start");
        startCol.setCellValueFactory(new PropertyValueFactory<>("optimizeStart"));
        startCol.setPrefWidth(120);

        TableColumn<EaParameter, String> stepCol = new TableColumn<>("Schritt");
        stepCol.setCellValueFactory(new PropertyValueFactory<>("optimizeStep"));
        stepCol.setPrefWidth(120);

        TableColumn<EaParameter, String> stopCol = new TableColumn<>("Stopp");
        stopCol.setCellValueFactory(new PropertyValueFactory<>("optimizeEnd"));
        stopCol.setPrefWidth(120);

        parametersTable.getColumns().addAll(optCol, nameCol, valCol, startCol, stepCol, stopCol);
        parametersTab.setContent(parametersTable);

        resultsTable = new TableView<>();
        resultsTable.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(resultsTable, Priority.ALWAYS);
        resultsTable.setRowFactory(tv -> {
            TableRow<CombinedPass> row = new TableRow<>();
            
            ContextMenu rowMenu = new ContextMenu();
            MenuItem normalBtItem = new MenuItem("▶ Backtest starten (Normal)");
            normalBtItem.setOnAction(e -> {
                CombinedPass selected = row.getItem();
                if (selected != null) {
                    runSingleBacktest(selected, false);
                }
            });
            MenuItem visualBtItem = new MenuItem("👁 Backtest starten (Visuell)");
            visualBtItem.setOnAction(e -> {
                CombinedPass selected = row.getItem();
                if (selected != null) {
                    runSingleBacktest(selected, true);
                }
            });
            rowMenu.getItems().addAll(normalBtItem, visualBtItem);

            // Bind context menu only to non-empty rows
            row.contextMenuProperty().bind(
                javafx.beans.binding.Bindings.when(row.emptyProperty())
                    .then((ContextMenu) null)
                    .otherwise(rowMenu)
            );

            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && row.getItem() != null && event.getButton() == MouseButton.PRIMARY) {
                    CombinedPass selected = row.getItem();
                    showStrategyDetailDialog(selected);
                }
            });
            row.hoverProperty().addListener((obs, wasHovered, isNowHovered) -> {
                if (isNowHovered && !row.isEmpty()) {
                    row.setCursor(javafx.scene.Cursor.HAND);
                } else {
                    row.setCursor(javafx.scene.Cursor.DEFAULT);
                }
            });
            return row;
        });

        TableColumn<CombinedPass, Integer> passCol = new TableColumn<>("Pass");
        passCol.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getPassNumber()));
        passCol.setPrefWidth(65);
        passCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<CombinedPass, Double> scoreCol = new TableColumn<>();
        scoreCol.setGraphic(DocHelper.createHeaderWithTooltip("Score", 
            "Unified Score (0-100):\nGewichteter Gesamtwert aus 10 Kriterien (Profit, DD, PF etc.). Konfigurierbar über das Regler-Symbol. Zeigt die beste Gesamtperformance."));
        scoreCol.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getScore()));
        scoreCol.setPrefWidth(75);
        scoreCol.setStyle("-fx-alignment: CENTER;");
        scoreCol.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.format(Locale.US, "%.1f", item));
                    setStyle("-fx-alignment: CENTER;");
                }
            }
        });

        TableColumn<CombinedPass, String> robScoreCol = new TableColumn<>();
        robScoreCol.setGraphic(DocHelper.createHeaderWithTooltip("Rob. Scorecard", 
            "Robustness Scorecard (0-100):\nErgebnis des Monte-Carlo-Stresstests und systematischen Parameter-Shifting. Simuliert Rauschen (Slippage, Spread, Execution) und bewertet die Geradlinigkeit (R²-Stabilität) der Equity-Kurve."));
        robScoreCol.setCellValueFactory(c -> {
            String fromDateStr = engine.getFromDate() != null ? engine.getFromDate().toString() : "Unbekannt";
            String toDateStr = engine.getToDate() != null ? engine.getToDate().toString() : "Unbekannt";
            double score = c.getValue().getCachedOverallScore(fromDateStr, toDateStr);
            return new javafx.beans.property.SimpleStringProperty(String.format(Locale.US, "%.0f", score));
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
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: " + color + "; -fx-font-weight: bold;");
                    } catch (Exception e) {
                        setStyle("-fx-alignment: CENTER;");
                    }
                }
            }
        });

        TableColumn<CombinedPass, Double> btProf = new TableColumn<>("BT Profit");
        btProf.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getBtProfit()));
        btProf.setPrefWidth(95);
        btProf.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || Double.isNaN(item)) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.format(Locale.US, "%.2f", item));
                    if (item >= 0) {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #00e676; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #ff5252; -fx-font-weight: bold;");
                    }
                }
            }
        });

        TableColumn<CombinedPass, Integer> btTr = new TableColumn<>("BT Trades");
        btTr.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getBtTrades()));
        btTr.setPrefWidth(85);
        btTr.setCellFactory(col -> new TableCell<CombinedPass, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.valueOf(item));
                    setStyle("-fx-alignment: CENTER;");
                }
            }
        });

        TableColumn<CombinedPass, Double> btPf = new TableColumn<>("BT PF");
        btPf.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getBtPf()));
        btPf.setPrefWidth(75);
        btPf.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || Double.isNaN(item)) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.format(Locale.US, "%.2f", item));
                    setStyle("-fx-alignment: CENTER;");
                }
            }
        });

        TableColumn<CombinedPass, Double> btDd = new TableColumn<>("BT DD%");
        btDd.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getBtDd()));
        btDd.setPrefWidth(85);
        btDd.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || Double.isNaN(item)) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.format(Locale.US, "%.2f %%", item));
                    if (item > 25) {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #ff5252;");
                    } else if (item > 15) {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #ffd740;");
                    } else {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #00e676;");
                    }
                }
            }
        });

        TableColumn<CombinedPass, Double> btRec = new TableColumn<>("BT RF");
        btRec.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getBtRecovery()));
        btRec.setPrefWidth(75);
        btRec.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || Double.isNaN(item)) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.format(Locale.US, "%.2f", item));
                    setStyle("-fx-alignment: CENTER;");
                }
            }
        });

        TableColumn<CombinedPass, Double> fwProf = new TableColumn<>("FW Profit");
        fwProf.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getFwProfit()));
        fwProf.setPrefWidth(95);
        fwProf.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || Double.isNaN(item)) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.format(Locale.US, "%.2f", item));
                    if (item >= 0) {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #00e676; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #ff5252; -fx-font-weight: bold;");
                    }
                }
            }
        });

        TableColumn<CombinedPass, Integer> fwTr = new TableColumn<>("FW Trades");
        fwTr.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getFwTrades()));
        fwTr.setPrefWidth(85);
        fwTr.setCellFactory(col -> new TableCell<CombinedPass, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.valueOf(item));
                    setStyle("-fx-alignment: CENTER;");
                }
            }
        });

        TableColumn<CombinedPass, Double> fwPf = new TableColumn<>("FW PF");
        fwPf.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getFwPf()));
        fwPf.setPrefWidth(75);
        fwPf.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || Double.isNaN(item)) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.format(Locale.US, "%.2f", item));
                    setStyle("-fx-alignment: CENTER;");
                }
            }
        });

        TableColumn<CombinedPass, Double> fwDd = new TableColumn<>("FW DD%");
        fwDd.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getFwDd()));
        fwDd.setPrefWidth(85);
        fwDd.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || Double.isNaN(item)) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.format(Locale.US, "%.2f %%", item));
                    if (item > 25) {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #ff5252;");
                    } else if (item > 15) {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #ffd740;");
                    } else {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #00e676;");
                    }
                }
            }
        });

        TableColumn<CombinedPass, Double> fwRec = new TableColumn<>("FW RF");
        fwRec.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getFwRecovery()));
        fwRec.setPrefWidth(75);
        fwRec.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || Double.isNaN(item)) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.format(Locale.US, "%.2f", item));
                    setStyle("-fx-alignment: CENTER;");
                }
            }
        });

        TableColumn<CombinedPass, String> btCvCol = new TableColumn<>("Worst BT CV");
        btCvCol.setCellValueFactory(cellData -> {
            CombinedPass cp = cellData.getValue();
            double cv = engine.getWorstCvForPass(cp.getPassNumber(), false);
            return new javafx.beans.property.SimpleStringProperty(Double.isNaN(cv) || cv == 0 ? "-" : String.format(Locale.US, "%.2f %%", cv));
        });
        btCvCol.setPrefWidth(110);
        btCvCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<CombinedPass, String> fwCvCol = new TableColumn<>("Worst FW CV");
        fwCvCol.setCellValueFactory(cellData -> {
            CombinedPass cp = cellData.getValue();
            double cv = engine.getWorstCvForPass(cp.getPassNumber(), true);
            return new javafx.beans.property.SimpleStringProperty(Double.isNaN(cv) || cv == 0 ? "-" : String.format(Locale.US, "%.2f %%", cv));
        });
        fwCvCol.setPrefWidth(110);
        fwCvCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<CombinedPass, String> kiRatingCol = new TableColumn<>("KI Stabilität");
        kiRatingCol.setCellValueFactory(cellData -> {
            CombinedPass cp = cellData.getValue();
            int score = engine.getKiScoreForPass(cp.getPassNumber());
            return new javafx.beans.property.SimpleStringProperty(score < 0 ? "-" : String.valueOf(score) + " / 100");
        });
        kiRatingCol.setPrefWidth(110);
        kiRatingCol.setCellFactory(col -> new TableCell<CombinedPass, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.equals("-")) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(item);
                    try {
                        int score = Integer.parseInt(item.split(" ")[0]);
                        String color;
                        if (score >= 80) color = "#00e676";
                        else if (score >= 70) color = "#66bb6a";
                        else if (score >= 50) color = "#ffd740";
                        else color = "#ff3b30";
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: " + color + "; -fx-font-weight: bold;");
                    } catch (Exception e) {
                        setStyle("-fx-alignment: CENTER;");
                    }
                }
            }
        });

        TableColumn<CombinedPass, String> paramsCol = new TableColumn<>("Parameter");
        paramsCol.setCellValueFactory(cellData -> {
            CombinedPass cp = cellData.getValue();
            if (cp.getBacktestPass() == null) return new javafx.beans.property.SimpleStringProperty("-");
            Map<String, String> pVals = cp.getBacktestPass().getParameterValues();
            StringBuilder sb = new StringBuilder();
            pVals.forEach((k, v) -> sb.append(k).append("=").append(v).append(", "));
            if (sb.length() > 2) sb.setLength(sb.length() - 2);
            return new javafx.beans.property.SimpleStringProperty(sb.toString());
        });
        paramsCol.setPrefWidth(300);

        resultsTable.getColumns().addAll(passCol, scoreCol, robScoreCol, btProf, btTr, btPf, btDd, btRec, fwProf, fwTr, fwPf, fwDd, fwRec, btCvCol, fwCvCol, kiRatingCol, paramsCol);

        noDataLabel = new Label("Klicke auf ein abgeschlossenes Workflow-Element oben, um dessen Strategieliste anzuzeigen.");
        noDataLabel.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 13px;");
        
        StackPane resultsStack = new StackPane(resultsTable, noDataLabel);
        StackPane.setAlignment(noDataLabel, Pos.CENTER);
        resultsTab.setContent(resultsStack);

        // Tab 2: KI-Bericht
        kiReportTab = new Tab("KI-Bericht");
        kiReportTab.setClosable(false);

        try {
            kiWebView = new javafx.scene.web.WebView();
            VBox.setVgrow(kiWebView, Priority.ALWAYS);
            kiReportTab.setContent(kiWebView);

            // Bind JS Bridge to Java
            kiWebView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal == Worker.State.SUCCEEDED) {
                    try {
                        JSObject window = (JSObject) kiWebView.getEngine().executeScript("window");
                        window.setMember("app", javaBridge);
                        
                        // Inject JS click handling and hover effects
                        String js = 
                            "function setupTableClicks() {\n" +
                            "  var tables = document.getElementsByTagName('table');\n" +
                            "  for (var t = 0; t < tables.length; t++) {\n" +
                            "    var table = tables[t];\n" +
                            "    var rows = table.getElementsByTagName('tr');\n" +
                            "    var headers = rows[0] ? rows[0].getElementsByTagName('th') : [];\n" +
                            "    var passColIndex = -1;\n" +
                            "    for (var h = 0; h < headers.length; h++) {\n" +
                            "      var text = (headers[h].textContent || headers[h].innerText || '').trim();\n" +
                            "      if (text === 'Pass') {\n" +
                            "        passColIndex = h;\n" +
                            "        break;\n" +
                            "      }\n" +
                            "    }\n" +
                            "    if (passColIndex !== -1) {\n" +
                            "      for (var r = 1; r < rows.length; r++) {\n" +
                            "        (function() {\n" +
                            "          var row = rows[r];\n" +
                            "          var cells = row.getElementsByTagName('td');\n" +
                            "          if (cells.length > passColIndex) {\n" +
                            "            var cellText = (cells[passColIndex].textContent || cells[passColIndex].innerText || '').trim();\n" +
                            "            var passNum = parseInt(cellText, 10);\n" +
                            "            if (!isNaN(passNum)) {\n" +
                            "              row.classList.add('clickable-row');\n" +
                            "              row.title = 'Klicken für Mega-Report für Pass ' + passNum;\n" +
                            "              row.onclick = function() {\n" +
                            "                if (window.app) {\n" +
                            "                  window.app.showPass(passNum);\n" +
                            "                }\n" +
                            "              };\n" +
                            "            }\n" +
                            "          }\n" +
                            "        })();\n" +
                            "      }\n" +
                            "    }\n" +
                            "  }\n" +
                            "}\n" +
                            "setupTableClicks();";
                        kiWebView.getEngine().executeScript(js);
                    } catch (Exception ex) {
                        System.err.println("Fehler beim Initialisieren der JS-Brücke: " + ex.getMessage());
                    }
                }
            });
        } catch (Throwable t) {
            System.err.println("WebView initialization failed (likely headless): " + t.getMessage());
            kiWebView = null;
            Label fallbackLabel = new Label("KI-Bericht-Visualisierung ist in diesem System nicht verfügbar (fehlende WebKit-Unterstützung).");
            fallbackLabel.setStyle("-fx-text-fill: #ff5252; -fx-padding: 20px; -fx-font-size: 13px;");
            kiReportTab.setContent(fallbackLabel);
        }

        // Tab 3: Logs
        Tab logTab = new Tab("Prozess-Logbuch");
        logTab.setClosable(false);

        consoleLog = new TextArea();
        consoleLog.setEditable(false);
        consoleLog.setFont(Font.font("Consolas", 12));
        consoleLog.getStyleClass().add("text-area");
        VBox.setVgrow(consoleLog, Priority.ALWAYS);
        logTab.setContent(consoleLog);

        // Tab 4: Statistik
        statisticsTab = new Tab("Statistik");
        statisticsTab.setClosable(false);

        statsTable = new TableView<>();
        statsTable.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(statsTable, Priority.ALWAYS);

        statsTable.setRowFactory(tv -> {
            TableRow<OptimizationStatsRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && row.getItem() != null && event.getButton() == MouseButton.PRIMARY) {
                    if (engine.getOptResult() != null) {
                        OptimizationStatsDialog dialog = new OptimizationStatsDialog(engine.getOptResult());
                        dialog.show();
                    }
                }
            });
            row.hoverProperty().addListener((obs, wasHovered, isNowHovered) -> {
                if (isNowHovered && !row.isEmpty()) {
                    row.setCursor(javafx.scene.Cursor.HAND);
                } else {
                    row.setCursor(javafx.scene.Cursor.DEFAULT);
                }
            });
            return row;
        });

        TableColumn<OptimizationStatsRow, String> eaCol = new TableColumn<>("Expert Advisor");
        eaCol.setCellValueFactory(new PropertyValueFactory<>("eaName"));
        eaCol.setPrefWidth(220);

        TableColumn<OptimizationStatsRow, String> symCol = new TableColumn<>("Symbol");
        symCol.setCellValueFactory(new PropertyValueFactory<>("symbol"));
        symCol.setPrefWidth(100);
        symCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<OptimizationStatsRow, String> tfCol = new TableColumn<>("Timeframe");
        tfCol.setCellValueFactory(new PropertyValueFactory<>("period"));
        tfCol.setPrefWidth(100);
        tfCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<OptimizationStatsRow, String> dateCol = new TableColumn<>("Zeitraum");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("periodDate"));
        dateCol.setPrefWidth(220);
        dateCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<OptimizationStatsRow, Integer> passesCol = new TableColumn<>("Durchgänge");
        passesCol.setCellValueFactory(new PropertyValueFactory<>("totalPasses"));
        passesCol.setPrefWidth(120);
        passesCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<OptimizationStatsRow, Double> maxProfitCol = new TableColumn<>("Max. Profit");
        maxProfitCol.setCellValueFactory(new PropertyValueFactory<>("maxProfit"));
        maxProfitCol.setPrefWidth(120);
        maxProfitCol.setCellFactory(col -> new TableCell<OptimizationStatsRow, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.format(Locale.US, "%.2f", item));
                    if (item >= 0) {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #00e676; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #ff5252; -fx-font-weight: bold;");
                    }
                }
            }
        });

        TableColumn<OptimizationStatsRow, String> statusCol = new TableColumn<>("Aktion");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(250);
        statusCol.setCellFactory(col -> new TableCell<OptimizationStatsRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("-fx-alignment: CENTER; -fx-text-fill: #00e5ff; -fx-font-weight: bold;");
                }
            }
        });

        statsTable.getColumns().addAll(eaCol, symCol, tfCol, dateCol, passesCol, maxProfitCol, statusCol);

        noStatsLabel = new Label("Keine Optimierungsdaten vorhanden. Bitte Optimierung in Schritt 2 ausführen.");
        noStatsLabel.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 13px;");

        StackPane statsStack = new StackPane(statsTable, noStatsLabel);
        StackPane.setAlignment(noStatsLabel, Pos.CENTER);
        statisticsTab.setContent(statsStack);

        tabPane.getTabs().addAll(resultsTab, parametersTab, kiReportTab, statisticsTab, logTab);
        return tabPane;
    }

    // --- State & Visual Updates ---

    private void selectStep(int stepNum) {
        this.selectedStep = stepNum;
        engine.setLastActiveStep(stepNum);
        engine.saveState();
        updateVisualStates();

        if (engine.getEaParameters() != null) {
            parametersTable.getItems().setAll(engine.getEaParameters());
        } else {
            parametersTable.getItems().clear();
        }

        String stepName = "";
        switch (stepNum) {
            case 1: stepName = "1. Strategie-Auswahl"; break;
            case 2: stepName = "2. MT5 Optimizer"; break;
            case 3: stepName = "3. Diversitäts-Filter"; break;
            case 4: stepName = "4. Robustness Test (CV)"; break;
            case 5: stepName = "5. KI-Bewertung"; break;
            case 6: stepName = "6. Portfolio Export"; break;
        }

        resultsTab.setText("Ergebnisse: " + stepName);

        List<CombinedPass> listToShow = new ArrayList<>();
        String noDataText = "Keine Daten für diesen Schritt vorhanden.";

        switch (stepNum) {
            case 1:
                noDataText = "In Schritt 1 werden die EA Parameter festgelegt. Bitte führen Sie den Optimizer in Schritt 2 aus.";
                break;
            case 2:
                if (engine.getOptResult() != null) {
                    listToShow = engine.getOptResult().buildCombinedPasses(engine.getForwardMode() > 0, engine.loadScoreWeightsFromDb());
                } else {
                    noDataText = "Keine Optimierungsergebnisse vorhanden. Starten Sie den Workflow oder führen Sie die Optimierung aus.";
                }
                break;
            case 3:
            case 4:
            case 5:
                if (!engine.getSelectedDiversePasses().isEmpty()) {
                    listToShow = engine.getSelectedDiversePasses();
                } else {
                    noDataText = "Keine gefilterten diversen Strategien vorhanden. Bitte führen Sie Schritt 3 aus.";
                }
                break;
            case 6:
                if (!engine.getFinalSelectedPasses().isEmpty()) {
                    listToShow = engine.getFinalSelectedPasses();
                } else {
                    noDataText = "Kein finales Portfolio vorhanden. Bitte führen Sie den Workflow komplett aus.";
                }
                break;
        }

        if (listToShow.isEmpty()) {
            resultsTable.getItems().clear();
            noDataLabel.setText(noDataText);
            noDataLabel.setVisible(true);
            resultsTable.setVisible(false);
        } else {
            resultsTable.getItems().setAll(listToShow);
            noDataLabel.setVisible(false);
            resultsTable.setVisible(true);
        }

        updateKiReportView();
        if (stepNum == 5 && tabPane != null && kiReportTab != null) {
            tabPane.getSelectionModel().select(kiReportTab);
        } else if ((stepNum == 1 || stepNum == 4) && tabPane != null && parametersTab != null) {
            tabPane.getSelectionModel().select(parametersTab);
        } else if (tabPane != null && resultsTab != null) {
            tabPane.getSelectionModel().select(resultsTab);
        }
    }

    private void setRunningStep(int stepNum) {
        if (Platform.isFxApplicationThread()) {
            this.currentlyRunningStep = stepNum;
            if (stepNum == -1) {
                this.currentRunningSymbol = "";
                this.currentSymbolIndex = 0;
                this.totalSymbolsCount = 0;
            }
            updateVisualStates();
        } else {
            Platform.runLater(() -> {
                this.currentlyRunningStep = stepNum;
                if (stepNum == -1) {
                    this.currentRunningSymbol = "";
                    this.currentSymbolIndex = 0;
                    this.totalSymbolsCount = 0;
                }
                updateVisualStates();
            });
        }
    }

    private void updateVisualStates() {
        // Hide all spinners first
        if (step1Spinner != null) { step1Spinner.setVisible(false); step1Spinner.setManaged(false); }
        if (step2Spinner != null) { step2Spinner.setVisible(false); step2Spinner.setManaged(false); }
        if (step3Spinner != null) { step3Spinner.setVisible(false); step3Spinner.setManaged(false); }
        if (step4Spinner != null) { step4Spinner.setVisible(false); step4Spinner.setManaged(false); }
        if (step5Spinner != null) { step5Spinner.setVisible(false); step5Spinner.setManaged(false); }
        if (step6Spinner != null) { step6Spinner.setVisible(false); step6Spinner.setManaged(false); }

        // Show active spinner
        if (currentlyRunningStep == 1 && step1Spinner != null) { step1Spinner.setVisible(true); step1Spinner.setManaged(true); }
        if (currentlyRunningStep == 2 && step2Spinner != null) { step2Spinner.setVisible(true); step2Spinner.setManaged(true); }
        if (currentlyRunningStep == 3 && step3Spinner != null) { step3Spinner.setVisible(true); step3Spinner.setManaged(true); }
        if (currentlyRunningStep == 4 && step4Spinner != null) { step4Spinner.setVisible(true); step4Spinner.setManaged(true); }
        if (currentlyRunningStep == 5 && step5Spinner != null) { step5Spinner.setVisible(true); step5Spinner.setManaged(true); }
        if (currentlyRunningStep == 6 && step6Spinner != null) { step6Spinner.setVisible(true); step6Spinner.setManaged(true); }

        String runningInfo = "";
        if (currentlyRunningStep != -1 && currentRunningSymbol != null && !currentRunningSymbol.isEmpty()) {
            runningInfo = currentRunningSymbol + (totalSymbolsCount > 1 ? " (" + currentSymbolIndex + "/" + totalSymbolsCount + ")" : "");
        }

        // Step 1
        if (currentlyRunningStep == 1) {
            setStepBoxState(step1Box, step1Status, step1Details, "LÄUFT...", "Analysiere: " + runningInfo, "#00e5ff", selectedStep == 1);
        } else if (engine.getExpert() == null || engine.getExpert().isEmpty()) {
            setStepBoxState(step1Box, step1Status, step1Details, "PENDING", "Kein EA geladen", "#ff3b30", selectedStep == 1);
        } else {
            String expertPath = engine.getExpert();
            String eaName = expertPath.substring(Math.max(expertPath.lastIndexOf("\\"), expertPath.lastIndexOf("/")) + 1);
            String symbolDisplay = engine.getSymbol();
            if (symbolDisplay != null && symbolDisplay.contains(",")) {
                String[] syms = symbolDisplay.split(",");
                if (syms.length > 2) {
                    symbolDisplay = syms[0].trim() + ", " + syms[1].trim() + "... (" + syms.length + " Pairs)";
                }
            }
            setStepBoxState(step1Box, step1Status, step1Details, "BEREIT", eaName + " (" + symbolDisplay + ")", "#00e676", selectedStep == 1);
        }

        // Step 2
        if (currentlyRunningStep == 2) {
            setStepBoxState(step2Box, step2Status, step2Details, "LÄUFT...", "Optimierung: " + runningInfo, "#00e5ff", selectedStep == 2);
        } else if (engine.getOptResult() == null) {
            setStepBoxState(step2Box, step2Status, step2Details, "WARTEND", "Keine Ergebnisse", "#ffb300", selectedStep == 2);
        } else {
            setStepBoxState(step2Box, step2Status, step2Details, "FERTIG", engine.getOptResult().getPasses().size() + " Strategien gefunden", "#00e676", selectedStep == 2);
        }

        // Step 3
        if (currentlyRunningStep == 3) {
            setStepBoxState(step3Box, step3Status, step3Details, "LÄUFT...", "Filtere: " + runningInfo, "#00e5ff", selectedStep == 3);
        } else if (engine.getSelectedDiversePasses().isEmpty()) {
            setStepBoxState(step3Box, step3Status, step3Details, "WARTEND", "Keine Selektion", "#ffb300", selectedStep == 3);
        } else {
            setStepBoxState(step3Box, step3Status, step3Details, "SELEKTIERT", engine.getSelectedDiversePasses().size() + " diverse Strategien", "#00e676", selectedStep == 3);
        }

        // Step 4
        if (currentlyRunningStep == 4) {
            setStepBoxState(step4Box, step4Status, step4Details, "LÄUFT...", "Stresstest: " + runningInfo, "#00e5ff", selectedStep == 4);
        } else if (engine.getSensitivityResults().isEmpty()) {
            setStepBoxState(step4Box, step4Status, step4Details, "WARTEND", "Keine Analysen", "#ffb300", selectedStep == 4);
        } else {
            long completed = engine.getSensitivityResults().stream().filter(r -> "Completed".equals(r.getStatus())).count();
            setStepBoxState(step4Box, step4Status, step4Details, "STRESSTEST", engine.getSensitivityResults().size() + " Strategien (" + completed + "/" + engine.getSensitivityResults().size() + " fertig)", "#00e676", selectedStep == 4);
        }

        // Step 5
        if (currentlyRunningStep == 5) {
            setStepBoxState(step5Box, step5Status, step5Details, "LÄUFT...", "KI-Analyse: " + runningInfo, "#00e5ff", selectedStep == 5);
        } else if (engine.getKiReportText() == null || engine.getKiReportText().isEmpty()) {
            setStepBoxState(step5Box, step5Status, step5Details, "WARTEND", "Kein Bericht", "#ffb300", selectedStep == 5);
        } else {
            setStepBoxState(step5Box, step5Status, step5Details, "BEWERTET", engine.getSelectedDiversePasses().size() + " Strategien bewertet", "#00e676", selectedStep == 5);
        }

        // Step 6
        if (currentlyRunningStep == 6) {
            setStepBoxState(step6Box, step6Status, step6Details, "LÄUFT...", "Portfolio: " + runningInfo, "#00e5ff", selectedStep == 6);
        } else if (engine.getFinalSelectedPasses().isEmpty()) {
            setStepBoxState(step6Box, step6Status, step6Details, "WARTEND", "Kein Export", "#ffb300", selectedStep == 6);
        } else {
            setStepBoxState(step6Box, step6Status, step6Details, "BEREIT", engine.getFinalSelectedPasses().size() + " Best-Strategien exportiert", "#00e676", selectedStep == 6);
        }
        updateStatsTab();
    }

    private void setStepBoxState(VBox box, Label status, Label details, String statusText, String detailsText, String hexColor, boolean isSelected) {
        status.setText(statusText);
        status.setTextFill(Color.web(hexColor));
        details.setText(detailsText);

        String borderColor = isSelected ? "#00e5ff" : hexColor;
        double borderWidth = isSelected ? 2.5 : 1.5;

        box.setStyle("-fx-background-color: rgba(26, 30, 40, 0.7); -fx-border-color: " + borderColor + "; -fx-border-width: " + borderWidth + "; -fx-border-radius: 6; -fx-background-radius: 6;");
    }

    // --- Execution Core ---

    private void logToConsole(String prefix, String message) {
        Platform.runLater(() -> {
            consoleLog.appendText("[" + prefix + "] " + message + "\n");
            globalLogView.log(prefix, message);
        });
    }

    private void startWorkflow() {
        if (engine.getExpert() == null || engine.getExpert().isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Bitte wähle in Schritt 1 einen Expert Advisor aus!").show();
            return;
        }

        runAllBtn.setDisable(true);
        cancelBtn.setDisable(false);
        resetBtn.setDisable(true);
        clearResultsBtn.setDisable(true);
        consoleLog.clear();

        logToConsole("WORKFLOW", "=== AUTOMATION GESTARTET ===");

        // Show Desktop 2 info toast, then start the workflow after it disappears
        showDesktop2Notification(() -> launchWorkflowTask());
    }

    /**
     * Shows a brief notification telling the user that MT5 runs on Desktop 2.
     * Auto-dismisses after 2 seconds, then calls the onDismissed callback.
     */
    private void showDesktop2Notification(Runnable onDismissed) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("MetaTrader 5 — Desktop 2");
        alert.setHeaderText("MetaTrader 5 läuft auf Desktop 2");
        alert.setContentText(
            "Um den MetaTrader zu sehen, drücke:\n\n" +
            "        Strg  +  Win  +  →\n\n" +
            "Der Workflow startet automatisch..."
        );
        alert.initOwner(root.getScene() != null ? root.getScene().getWindow() : null);

        // Style the alert with dark theme
        alert.getDialogPane().setStyle(
            "-fx-background-color: #1a1e28; -fx-border-color: #00e5ff; -fx-border-width: 1.5;"
        );

        alert.show();

        // Auto-dismiss after 2 seconds
        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
        pause.setOnFinished(e -> {
            alert.setResult(javafx.scene.control.ButtonType.OK);
            alert.hide();
            onDismissed.run();
        });
        pause.play();
    }

    private void launchWorkflowTask() {
        setTabRunning(true);
        activeWorkflowTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                String rawSymbols = engine.getSymbol();
                if (rawSymbols == null || rawSymbols.isEmpty()) {
                    rawSymbols = "EURUSD";
                }
                String[] symbols = rawSymbols.split(",\\s*");
                totalSymbolsCount = symbols.length;

                for (int i = 0; i < symbols.length; i++) {
                    String currentSym = symbols[i].trim();
                    if (currentSym.isEmpty()) continue;
                    
                    currentRunningSymbol = currentSym;
                    currentSymbolIndex = i + 1;
                    
                    logToConsole("WORKFLOW", "=== STARTE WORKFLOW FÜR SYMBOL " + (i + 1) + " VON " + symbols.length + ": " + currentSym + " ===");
                    engine.setSymbol(currentSym);

                    // Step 1: Validate Setup
                    setRunningStep(1);
                    updateProgressUI(((double) i / symbols.length) + (0.05 / symbols.length), "[" + currentSym + "] Schritt 1: Validiere Einstellungen...");
                    logToConsole("WORKFLOW", "[" + currentSym + "] Schritt 1: Initialisiere Parameter...");
                    engine.runStep1();
                    Platform.runLater(() -> {
                        updateVisualStates();
                        if (selectedStep == 1) selectStep(1);
                    });
                    if (isCancelled()) return null;

                    // Step 2: Optimization
                    setRunningStep(2);
                    updateProgressUI(((double) i / symbols.length) + (0.10 / symbols.length), "[" + currentSym + "] Schritt 2: Starte MT5 Optimierung...");
                    logToConsole("WORKFLOW", "[" + currentSym + "] Schritt 2: Rufe MT5 genetic optimizer auf...");
                    
                    final String symPrefix = currentSym;
                    final double baseProgress = (double) i / symbols.length;
                    engine.runStep2(
                        logMsg -> logToConsole("MT5-OPT [" + symPrefix + "]", logMsg),
                        (curr, tot) -> {
                            if (tot <= 0) {
                                Platform.runLater(() -> {
                                    progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
                                    progressLabel.setText("[" + symPrefix + "] Optimierung: Pass " + curr);
                                });
                            } else {
                                updateProgressUI(baseProgress + (0.10 / symbols.length) + (0.45 / symbols.length) * ((double) curr / Math.max(tot, 1)), "[" + symPrefix + "] Optimierung: Pass " + curr + " / " + tot);
                            }
                        }
                    );
                    
                    Platform.runLater(() -> {
                        updateVisualStates();
                        selectStep(2);
                    });
                    if (isCancelled()) return null;

                    // Step 3: Diverse Strategy Filter
                    setRunningStep(3);
                    updateProgressUI(baseProgress + (0.55 / symbols.length), "[" + currentSym + "] Schritt 3: Filtere diverse Strategien...");
                    logToConsole("WORKFLOW", "[" + currentSym + "] Schritt 3: Wende Ähnlichkeits-Clustering auf Ergebnisse an...");
                    engine.runStep3();
                    Platform.runLater(() -> {
                        updateVisualStates();
                        selectStep(3);
                    });
                    if (isCancelled()) return null;

                    // Step 4: Sensitivity analysis
                    setRunningStep(4);
                    updateProgressUI(baseProgress + (0.60 / symbols.length), "[" + currentSym + "] Schritt 4: Starte Robustheits-Tests...");
                    logToConsole("WORKFLOW", "[" + currentSym + "] Schritt 4: Sweepe optimierte Parameter zur Sensitivitäts-Prüfung...");
                    
                    engine.runStep4(
                        logMsg -> logToConsole("STRESS [" + symPrefix + "]", logMsg),
                        pct -> updateProgressUI(baseProgress + (0.60 / symbols.length) + (0.28 / symbols.length) * ((double) pct / 100.0), "[" + symPrefix + "] Robustheits-Tests: " + pct + "% abgeschlossen")
                    );

                    Platform.runLater(() -> {
                        updateVisualStates();
                        selectStep(4);
                    });
                    if (isCancelled()) return null;

                    // Step 5: LLM Scoring
                    setRunningStep(5);
                    updateProgressUI(baseProgress + (0.88 / symbols.length), "[" + currentSym + "] Schritt 5: KI Bewertung der Stabilität...");
                    logToConsole("WORKFLOW", "[" + currentSym + "] Schritt 5: Sende Sensitivitätsdaten an OpenRouter...");
                    
                    engine.runStep5(logMsg -> logToConsole("KI-EVAL [" + symPrefix + "]", logMsg));

                    Platform.runLater(() -> {
                        updateVisualStates();
                        selectStep(5);
                    });
                    if (isCancelled()) return null;

                    // Step 6: Final Portfolio selection
                    setRunningStep(6);
                    updateProgressUI(baseProgress + (0.95 / symbols.length), "[" + currentSym + "] Schritt 6: Generiere finales Portfolio...");
                    logToConsole("WORKFLOW", "[" + currentSym + "] Schritt 6: Wähle die 3-5 stabilsten, unkorrelierten Strategien...");
                    engine.runStep6();
                    Platform.runLater(() -> {
                        updateVisualStates();
                        selectStep(6);
                    });
                    
                    logToConsole("WORKFLOW", "=== SYMBOL " + currentSym + " ERFOLGREICH BEENDET ===");

                    if (i < symbols.length - 1) {
                        logToConsole("WORKFLOW", "Bereinige temporäre Daten für den nächsten Durchlauf...");
                        engine.clearResults();
                        Platform.runLater(() -> {
                            updateVisualStates();
                            selectStep(1);
                        });
                    }
                }

                // Restore the original symbols list in the engine's symbol state variable
                engine.setSymbol(rawSymbols);
                engine.saveState();

                updateProgressUI(1.0, "Workflow erfolgreich für alle Währungspaare abgeschlossen!");
                logToConsole("WORKFLOW", "=== BATCH-WORKFLOW AUTOMATION ABGESCHLOSSEN ===");
                return null;
            }

            @Override
            protected void succeeded() {
                setRunningStep(-1);
                cleanupTaskState();
                Platform.runLater(() -> {
                    new Alert(Alert.AlertType.INFORMATION, "Workflow erfolgreich abgeschlossen!").show();
                    // Open step 6 export window directly
                    WorkflowConfigDialogs.showStep6Dialog(engine, root.getScene().getWindow());
                });
            }

            @Override
            protected void failed() {
                setRunningStep(-1);
                cleanupTaskState();
                Throwable ex = getException();
                logToConsole("ERROR", "Fehler im Workflow: " + ex.getMessage());
                Platform.runLater(() -> {
                    new Alert(Alert.AlertType.ERROR, "Fehler im Workflow:\n" + ex.getMessage()).show();
                    updateVisualStates();
                });
            }

            @Override
            protected void cancelled() {
                setRunningStep(-1);
                cleanupTaskState();
                logToConsole("WORKFLOW", "Workflow vom Benutzer abgebrochen.");
                Platform.runLater(() -> {
                    new Alert(Alert.AlertType.INFORMATION, "Workflow abgebrochen.").show();
                    updateVisualStates();
                });
            }
        };

        Thread t = new Thread(activeWorkflowTask);
        t.setDaemon(true);
        t.start();
    }

    private void updateProgressUI(double progress, String label) {
        Platform.runLater(() -> {
            progressBar.setProgress(progress);
            progressLabel.setText(label);
        });
    }

    private void cleanupTaskState() {
        runAllBtn.setDisable(false);
        cancelBtn.setDisable(true);
        resetBtn.setDisable(false);
        clearResultsBtn.setDisable(false);
        activeWorkflowTask = null;
        setTabRunning(false);
    }

    private void cancelWorkflow() {
        if (activeWorkflowTask != null) {
            activeWorkflowTask.cancel();
        }
        engine.cancel();
    }

    private void resetWorkflow() {
        engine.clearState();
        progressBar.setProgress(0);
        progressLabel.setText("Zurückgesetzt.");
        consoleLog.clear();
        selectStep(1);
        logToConsole("WORKFLOW", "Workflow-Daten zurückgesetzt.");
    }

    private void clearWorkflowResults() {
        engine.clearResults();
        progressBar.setProgress(0);
        progressLabel.setText("Ergebnisse gelöscht.");
        consoleLog.clear();
        selectStep(1);
        updateVisualStates();
        logToConsole("WORKFLOW", "Ergebnisse des letzten Workflows gelöscht. Einstellungen wurden beibehalten.");
    }

    private void setupContextMenu(VBox stepBox, int stepNum) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem runStepItem = new MenuItem("▶ Nur diesen Schritt ausführen");
        runStepItem.setOnAction(evt -> runSingleStep(stepNum));
        contextMenu.getItems().add(runStepItem);

        stepBox.setOnContextMenuRequested(evt -> {
            if (activeWorkflowTask == null) {
                contextMenu.show(stepBox, evt.getScreenX(), evt.getScreenY());
            }
        });
    }

    private void runSingleStep(int stepNum) {
        if (engine.getExpert() == null || engine.getExpert().isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Bitte wähle in Schritt 1 einen Expert Advisor aus!").show();
            return;
        }
        if (engine.getSymbol() != null && engine.getSymbol().contains(",")) {
            new Alert(Alert.AlertType.WARNING, "Einzelschritte können bei mehreren Währungspaaren nicht ausgeführt werden. Bitte starten Sie den gesamten Workflow.").show();
            return;
        }

        currentRunningSymbol = engine.getSymbol();
        currentSymbolIndex = 1;
        totalSymbolsCount = 1;

        runAllBtn.setDisable(true);
        cancelBtn.setDisable(false);
        resetBtn.setDisable(true);
        clearResultsBtn.setDisable(true);
        consoleLog.clear();

        logToConsole("WORKFLOW", "=== EINZELSCHRITT " + stepNum + " START ===");

        setTabRunning(true);
        activeWorkflowTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                setRunningStep(stepNum);
                switch (stepNum) {
                    case 1:
                        updateProgressUI(0.05, "Führe Schritt 1: Konfiguration aus...");
                        engine.runStep1();
                        break;
                    case 2:
                        updateProgressUI(0.10, "Führe Schritt 2: MT5 Optimierung aus...");
                        engine.runStep2(
                            logMsg -> logToConsole("MT5-OPT", logMsg),
                            (curr, tot) -> {
                                if (tot <= 0) {
                                    Platform.runLater(() -> {
                                        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
                                        progressLabel.setText("Optimierung: Pass " + curr);
                                    });
                                } else {
                                    updateProgressUI(0.10 + 0.90 * ((double) curr / Math.max(tot, 1)), "Optimierung: Pass " + curr + " / " + tot);
                                }
                            }
                        );
                        break;
                    case 3:
                        updateProgressUI(0.20, "Führe Schritt 3: Diversitäts-Filter aus...");
                        engine.runStep3();
                        break;
                    case 4:
                        updateProgressUI(0.30, "Führe Schritt 4: Robustheits-Test (CV) aus...");
                        engine.runStep4(
                            logMsg -> logToConsole("STRESS", logMsg),
                            pct -> updateProgressUI(0.30 + 0.70 * ((double) pct / 100.0), "Robustheits-Tests: " + pct + "%")
                        );
                        break;
                    case 5:
                        updateProgressUI(0.50, "Führe Schritt 5: KI-Bewertung aus...");
                        engine.runStep5(logMsg -> logToConsole("KI-EVAL", logMsg));
                        break;
                    case 6:
                        updateProgressUI(0.80, "Führe Schritt 6: Portfolio Export aus...");
                        engine.runStep6();
                        break;
                }
                updateProgressUI(1.0, "Schritt " + stepNum + " erfolgreich abgeschlossen!");
                logToConsole("WORKFLOW", "=== EINZELSCHRITT " + stepNum + " ABGESCHLOSSEN ===");
                return null;
            }

            @Override
            protected void succeeded() {
                setRunningStep(-1);
                cleanupTaskState();
                Platform.runLater(() -> {
                    updateVisualStates();
                    selectStep(stepNum);
                    new Alert(Alert.AlertType.INFORMATION, "Schritt " + stepNum + " erfolgreich abgeschlossen!").show();
                });
            }

            @Override
            protected void failed() {
                setRunningStep(-1);
                cleanupTaskState();
                Throwable ex = getException();
                logToConsole("ERROR", "Fehler in Schritt " + stepNum + ": " + ex.getMessage());
                Platform.runLater(() -> {
                    new Alert(Alert.AlertType.ERROR, "Fehler in Schritt " + stepNum + ":\n" + ex.getMessage()).show();
                    updateVisualStates();
                });
            }

            @Override
            protected void cancelled() {
                cleanupTaskState();
                logToConsole("WORKFLOW", "Schritt " + stepNum + " abgebrochen.");
                Platform.runLater(() -> {
                    new Alert(Alert.AlertType.INFORMATION, "Schritt abgebrochen.").show();
                    updateVisualStates();
                });
            }
        };

        Thread t = new Thread(activeWorkflowTask);
        t.setDaemon(true);
        t.start();
    }

    // --- KI Report & Details Popup Helper Methods ---

    private void updateKiReportView() {
        if (kiWebView == null) return;
        
        String rawMarkdown = engine.getKiReportText();
        String markdownContent;
        if (rawMarkdown == null || rawMarkdown.trim().isEmpty()) {
            markdownContent = "## Kein KI-Bericht vorhanden\n\nBitte führen Sie Schritt 5 (KI-Bewertung) im Workflow aus, um einen Stabilitätsbericht zu generieren.";
        } else {
            markdownContent = java.util.Arrays.stream(rawMarkdown.split("\\r?\\n"))
                    .filter(line -> !line.trim().startsWith("STABILITY_SCORE"))
                    .collect(Collectors.joining("\n"));
        }

        try {
            Parser parser = Parser.builder()
                    .extensions(Collections.singletonList(TablesExtension.create()))
                    .build();
            Node document = parser.parse(markdownContent);
            HtmlRenderer renderer = HtmlRenderer.builder()
                    .extensions(Collections.singletonList(TablesExtension.create()))
                    .build();
            String rawHtml = renderer.render(document);

            String css = "<style>"
                    + "body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #1a1e28; color: #e6e9f0; padding: 20px; line-height: 1.6; }"
                    + "h1, h2, h3 { color: #00e5ff; border-bottom: 1px solid #3e4555; padding-bottom: 5px; }"
                    + "a { color: #50d278; text-decoration: none; }"
                    + "code { background-color: #14161c; padding: 2px 5px; border-radius: 3px; font-family: Consolas, monospace; border: 1px solid #3e4555; }"
                    + "pre { background-color: #14161c; padding: 15px; border-radius: 5px; overflow-x: auto; border: 1px solid #3e4555; }"
                    + "blockquote { border-left: 4px solid #00e5ff; margin-left: 0; padding-left: 15px; color: #b4bac8; }"
                    + "table { border-collapse: collapse; width: 100%; margin-bottom: 20px; }"
                    + "th, td { border: 1px solid #3e4555; padding: 8px 12px; text-align: left; }"
                    + "th { background-color: #14161c; color: #00e5ff; }"
                    + "tr:nth-child(even) { background-color: #14161c; }"
                    + "tr.clickable-row { transition: background-color 0.15s ease; }"
                    + "tr.clickable-row:hover { background-color: #2a2d3a !important; cursor: pointer; }"
                    + "</style>";

            String fullHtml = "<html><head>" + css + "</head><body>" + rawHtml + "</body></html>";
            kiWebView.getEngine().loadContent(fullHtml);
        } catch (Exception e) {
            kiWebView.getEngine().loadContent("<html><body style='background-color:#1a1e28; color:red; font-family:sans-serif;'><h2>Fehler beim Rendern des KI-Berichts</h2><p>" + e.getMessage() + "</p></body></html>");
        }
    }

    private void showStrategyDetailDialog(CombinedPass cp) {
        Stage stage = new Stage();
        stage.setTitle("Strategie-Details - Pass " + cp.getPassNumber());
        stage.initModality(Modality.APPLICATION_MODAL);
        if (root.getScene() != null && root.getScene().getWindow() != null) {
            stage.initOwner(root.getScene().getWindow());
        }

        VBox box = new VBox(15);
        box.setPadding(new Insets(20));
        box.setStyle("-fx-background-color: #0b0d13;"); // Ensure dark background

        // --- Header ---
        Label titleLabel = new Label("STRATEGIE-DETAILS (PASS #" + cp.getPassNumber() + ")");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        titleLabel.setTextFill(Color.web("#00e5ff"));

        String fromDateStr = engine.getFromDate() != null ? engine.getFromDate().toString() : "Unbekannt";
        String toDateStr = engine.getToDate() != null ? engine.getToDate().toString() : "Unbekannt";
        Label subtitleLabel = new Label("Zeitraum: " + fromDateStr + " bis " + toDateStr);
        subtitleLabel.setFont(Font.font("Segoe UI", 16));
        subtitleLabel.setTextFill(Color.web("#7e889a"));

        box.getChildren().addAll(titleLabel, subtitleLabel);

        // --- Calculate Reference Values ---
        List<CombinedPass> allPasses = new ArrayList<>(resultsTable.getItems());
        int refTrades = 80;
        double refProfit = 500.0;
        if (!allPasses.isEmpty()) {
            List<Integer> tradesList = allPasses.stream()
                .map(CombinedPass::getBtTrades)
                .sorted()
                .collect(Collectors.toList());
            int size = tradesList.size();
            if (size % 2 == 0) {
                refTrades = (tradesList.get(size / 2 - 1) + tradesList.get(size / 2)) / 2;
            } else {
                refTrades = tradesList.get(size / 2);
            }
            refTrades = Math.max(30, refTrades);

            List<Double> profitList = allPasses.stream()
                .map(CombinedPass::getBtProfit)
                .sorted()
                .collect(Collectors.toList());
            if (!profitList.isEmpty()) {
                int pSize = profitList.size();
                if (pSize % 2 == 0) {
                    refProfit = (profitList.get(pSize / 2 - 1) + profitList.get(pSize / 2)) / 2.0;
                } else {
                    refProfit = profitList.get(pSize / 2);
                }
            }
            refProfit = Math.max(100.0, refProfit);
        }

        // --- Cards Pane ---
        HBox cardsBox = new HBox(12);
        cardsBox.setAlignment(Pos.TOP_LEFT);

        // 1. Backtest Card
        VBox btCard = new VBox(10);
        btCard.setPadding(new Insets(12));
        btCard.setStyle("-fx-background-color: #171b26; -fx-border-color: #00e676; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px;");
        HBox.setHgrow(btCard, Priority.ALWAYS);
        Label btTitle = new Label("◀ BACKTEST METRIKEN");
        btTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        btTitle.setTextFill(Color.web("#00e676"));
        GridPane btGrid = new GridPane();
        btGrid.setHgap(15);
        btGrid.setVgap(6);
        addDetailMetricRow(btGrid, 0, "Nettoprofit:", String.format(Locale.US, "%.2f %s", cp.getBtProfit(), engine.getCurrency()));
        addDetailMetricRow(btGrid, 1, "Trades:", String.valueOf(cp.getBtTrades()));
        addDetailMetricRow(btGrid, 2, "Profit Factor:", String.format(Locale.US, "%.2f", cp.getBtPf()));
        addDetailMetricRow(btGrid, 3, "Max. Drawdown:", String.format(Locale.US, "%.2f%%", cp.getBtDd()));
        addDetailMetricRow(btGrid, 4, "Recovery Factor:", String.format(Locale.US, "%.2f", cp.getBtRecovery()));
        addDetailMetricRow(btGrid, 5, "Sharpe Ratio:", Double.isNaN(cp.getBtSharpe()) ? "—" : String.format(Locale.US, "%.2f", cp.getBtSharpe()));
        addDetailMetricRow(btGrid, 6, "Expected Payoff:", String.format(Locale.US, "%.2f", cp.getBtExpectedPayoff()));
        btCard.getChildren().addAll(btTitle, btGrid);

        // 2. Forward Card
        VBox fwCard = new VBox(10);
        fwCard.setPadding(new Insets(12));
        fwCard.setStyle("-fx-background-color: #171b26; -fx-border-color: #00e5ff; -fx-border-width: 1px; -fx-border-radius: 4px; -fx-background-radius: 4px;");
        HBox.setHgrow(fwCard, Priority.ALWAYS);
        Label fwTitle = new Label("FORWARD METRIKEN ▶");
        fwTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        fwTitle.setTextFill(Color.web("#00e5ff"));
        GridPane fwGrid = new GridPane();
        fwGrid.setHgap(15);
        fwGrid.setVgap(6);

        if (cp.getForwardPass() != null) {
            addDetailMetricRow(fwGrid, 0, "Nettoprofit:", String.format(Locale.US, "%.2f %s", cp.getFwProfit(), engine.getCurrency()));
            addDetailMetricRow(fwGrid, 1, "Trades:", String.valueOf(cp.getFwTrades()));
            addDetailMetricRow(fwGrid, 2, "Profit Factor:", Double.isNaN(cp.getFwPf()) ? "—" : String.format(Locale.US, "%.2f", cp.getFwPf()));
            addDetailMetricRow(fwGrid, 3, "Max. Drawdown:", Double.isNaN(cp.getFwDd()) ? "—" : String.format(Locale.US, "%.2f%%", cp.getFwDd()));
            addDetailMetricRow(fwGrid, 4, "Recovery Factor:", Double.isNaN(cp.getFwRecovery()) ? "—" : String.format(Locale.US, "%.2f", cp.getFwRecovery()));
            addDetailMetricRow(fwGrid, 5, "Sharpe Ratio:", Double.isNaN(cp.getFwSharpe()) ? "—" : String.format(Locale.US, "%.2f", cp.getFwSharpe()));
            addDetailMetricRow(fwGrid, 6, "Expected Payoff:", Double.isNaN(cp.getFwExpectedPayoff()) ? "—" : String.format(Locale.US, "%.2f", cp.getFwExpectedPayoff()));
            fwCard.getChildren().addAll(fwTitle, fwGrid);
        } else {
            Label noFwLabel = new Label("Kein Forward-Test\ndurchgeführt.");
            noFwLabel.setTextFill(Color.web("#7e889a"));
            noFwLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
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
        evalTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        evalTitle.setTextFill(Color.web("#ffd740"));
        GridPane evalGrid = new GridPane();
        evalGrid.setHgap(15);
        evalGrid.setVgap(6);

        StrategyEvaluatorDialog.Evaluation eval = StrategyEvaluatorDialog.evaluatePass(cp, refTrades, refProfit);
        addDetailMetricRow(evalGrid, 0, "Score (Gewichtung):", String.format(Locale.US, "%.2f", cp.getScore()));
        addDetailMetricRow(evalGrid, 1, "Robustness-Index (RI):", String.format(Locale.US, "%.2f", StrategyEvaluatorDialog.calculateRobustnessIndex(cp, refTrades)));
        addDetailMetricRow(evalGrid, 2, "Forward-Konsistenz:", String.format(Locale.US, "%.2f", cp.getConsistency()));
        Label verdictVal = addDetailMetricRow(evalGrid, 3, "Analyse-Urteil:", eval.remark);
        verdictVal.setTextFill(Color.web(eval.color));
        verdictVal.setStyle("-fx-font-weight: bold;");
        verdictVal.setWrapText(true);
        verdictVal.setMaxWidth(220);
        
        evalCard.getChildren().addAll(evalTitle, evalGrid);

        cardsBox.getChildren().addAll(btCard, fwCard, evalCard);
        box.getChildren().add(cardsBox);

        // --- Equity Chart ---
        Label chartTitleLabel = new Label("EQUITY-KURVE (KAPITALVERLAUF)");
        chartTitleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17));
        chartTitleLabel.setTextFill(Color.web("#00e5ff"));
        box.getChildren().add(chartTitleLabel);

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
        equityChart.setPrefHeight(260);
        equityChart.setMinHeight(260);
        equityChart.setMaxHeight(260);
        equityChart.setAnimated(false);
        equityChart.setStyle("-fx-background-color: transparent;");
        equityChart.setHorizontalGridLinesVisible(true);
        equityChart.setVerticalGridLinesVisible(false);

        double btEndBalance = cp.getBacktestPass().getBalance();
        double btStartBalance = btEndBalance - cp.getBtProfit();
        if (btStartBalance <= 0) {
            btStartBalance = 10000.0;
        }

        List<Double> btCurve = StrategyEvaluatorDialog.generateSyntheticEquityCurve(btStartBalance, cp.getBtProfit(), cp.getBtTrades(), cp.getBtPf(), cp.getPassNumber());
        XYChart.Series<Number, Number> backtestSeries = new XYChart.Series<>();
        backtestSeries.setName("Backtest");
        for (int i = 0; i < btCurve.size(); i++) {
            backtestSeries.getData().add(new XYChart.Data<>(i, btCurve.get(i)));
        }
        equityChart.getData().add(backtestSeries);

        XYChart.Series<Number, Number> forwardSeries = null;
        if (cp.getForwardPass() != null) {
            double fwStartBalance = btCurve.get(btCurve.size() - 1);
            List<Double> fwCurve = StrategyEvaluatorDialog.generateSyntheticEquityCurve(fwStartBalance, cp.getFwProfit(), cp.getFwTrades(), cp.getFwPf(), cp.getPassNumber() + 999);
            forwardSeries = new XYChart.Series<>();
            forwardSeries.setName("Forward");
            
            int offset = btCurve.size() - 1;
            forwardSeries.getData().add(new XYChart.Data<>(offset, fwStartBalance));
            for (int j = 1; j < fwCurve.size(); j++) {
                forwardSeries.getData().add(new XYChart.Data<>(offset + j, fwCurve.get(j)));
            }
            equityChart.getData().add(forwardSeries);
        }
        box.getChildren().add(equityChart);

        // --- Robustness Test breakdown with line charts ---
        SensitivityResult match = null;
        if (engine.getSensitivityResults() != null) {
            for (SensitivityResult sr : engine.getSensitivityResults()) {
                if (sr.getOriginalPass() != null && sr.getOriginalPass().getPassNumber() == cp.getPassNumber()) {
                    match = sr;
                    break;
                }
            }
        }

        if (match == null) {
            Label noStabilityLbl = new Label("🛡️ PARAMETER-SENSITIVITÄT & KINNLINIEN");
            noStabilityLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17));
            noStabilityLbl.setTextFill(Color.web("#00e5ff"));
            
            Label noStabilityDesc = new Label("Robustheits-Kennlinien sind erst nach Durchführung von Schritt 4 (Robustness Test (CV)) im Workflow verfügbar.");
            noStabilityDesc.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 15px; -fx-padding: 0 0 15 0;");
            
            box.getChildren().addAll(noStabilityLbl, noStabilityDesc);
        } else {
            Label btCvLabel = new Label("🛡️ Backtest (in-sample) Parameter-Robustheit & Kennlinien:");
            btCvLabel.setTextFill(Color.WHITE);
            btCvLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17));
            
            TableView<Map.Entry<String, Double>> btCvTable = buildCvBreakdownTable(
                    match.getParameterCVs(),
                    match.getParameterCurves(),
                    match.getOriginalPass().getBacktestPass().getParameterValues(),
                    "#00e5ff");
            
            box.getChildren().addAll(btCvLabel, btCvTable);

            if (match.hasForwardCV()) {
                Label fwCvLabel = new Label("🛡️ Forward (out-of-sample) Parameter-Robustheit & Kennlinien:");
                fwCvLabel.setTextFill(Color.WHITE);
                fwCvLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17));
                
                TableView<Map.Entry<String, Double>> fwCvTable = buildCvBreakdownTable(
                        match.getParameterCVsFw(),
                        match.getParameterCurvesFw(),
                        match.getOriginalPass().getBacktestPass().getParameterValues(),
                        "#ff9100");
                
                box.getChildren().addAll(fwCvLabel, fwCvTable);
            }

            // Detailed interpretation and verdict box
            Label explanationTitle = new Label("Ausführliche Erklärung zur Interpretation");
            explanationTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
            explanationTitle.setTextFill(Color.web("#00e5ff"));
            
            String interpretationText = 
                "Die Sensitivitätsanalyse testet, wie 'zerbrechlich' deine Strategie ist.\n" +
                "Dazu wird jeder Parameter (z.B. StopLoss, Takeprofit) in kleinen Schritten um seinen optimierten Wert herum verschoben. " +
                "Anschließend messen wir, wie stark sich der Profit durch diese kleinen Änderungen verändert.\n\n" +
                "Der CV-Wert (Coefficient of Variation) ist das Maß für diese Schwankung:\n" +
                "• Unter 30% (Grün): Der Parameter ist extrem stabil. Wenn der Markt sich leicht ändert, bleibt dein Profit weitgehend gleich.\n" +
                "• 30% bis 60% (Gelb): Normale Schwankung. Die Strategie bleibt vermutlich noch profitabel.\n" +
                "• Über 60% (Rot): Gefahr! Die Strategie ist ein 'One-Hit-Wonder'. Ein winziger Unterschied im Markt, und die Strategie stürzt ab (Curve-Fitted).\n\n";
                
            double worstCv = match.getOverallCV();
            if (match.hasForwardCV() && match.getOverallCVFw() > worstCv) {
                worstCv = match.getOverallCVFw();
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
                passVerdict = String.format("FAZIT ZU DIESEM PASS:\nACHTUNG! Der schlechteste CV liegt bei gigantischen %.2f %%! Dieser Pass ist zu stark überoptimiert (Curve-Fitted). Im Live-Handel wird er höchstwahrscheinlich Verluste einfahren.", worstCv);
                verdictColor = Color.web("#ff3b30");
            }
            
            Label interpretationLabel = new Label(interpretationText);
            interpretationLabel.setWrapText(true);
            interpretationLabel.setStyle("-fx-text-fill: #b4bac8; -fx-font-size: 15px;");
            
            Label verdictLabel = new Label(passVerdict);
            verdictLabel.setWrapText(true);
            verdictLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17));
            verdictLabel.setTextFill(verdictColor);
            
            VBox expBox = new VBox(10, explanationTitle, interpretationLabel, verdictLabel);
            expBox.setStyle("-fx-background-color: #1a1d27; -fx-padding: 15; -fx-background-radius: 8; -fx-border-color: #2a2d3a; -fx-border-width: 1; -fx-border-radius: 8;");
            
            box.getChildren().add(expBox);
        }

        // --- Optimized Strategy Settings ---
        Label paramLabel = new Label("⚙️ STRATEGIE-PARAMETER (EINSTELLUNGEN)");
        paramLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17));
        paramLabel.setTextFill(Color.web("#00e5ff"));
        box.getChildren().add(paramLabel);

        TableView<Map.Entry<String, String>> paramTable = new TableView<>();
        paramTable.setStyle("-fx-background-color: transparent; -fx-font-size: 14px;");
        paramTable.setPrefHeight(280);

        TableColumn<Map.Entry<String, String>, String> sParamCol = new TableColumn<>("Parameter");
        sParamCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getKey()));
        sParamCol.setPrefWidth(350);

        TableColumn<Map.Entry<String, String>, String> sValCol = new TableColumn<>("Wert");
        sValCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getValue()));
        sValCol.setPrefWidth(350);

        paramTable.getColumns().addAll(sParamCol, sValCol);
        if (cp.getBacktestPass() != null && cp.getBacktestPass().getParameterValues() != null) {
            paramTable.getItems().addAll(cp.getBacktestPass().getParameterValues().entrySet());
        }
        paramTable.setSelectionModel(null);
        box.getChildren().add(paramTable);

        // --- Bottom bar ---
        Button closeBtn = new Button("Schließen");
        closeBtn.getStyleClass().add("button");
        closeBtn.setOnAction(e -> stage.close());

        HBox btnBox = new HBox(10, closeBtn);
        btnBox.setAlignment(Pos.CENTER_RIGHT);
        box.getChildren().add(btnBox);

        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(box);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: #0b0d13; -fx-box-border: transparent;");

        // --- Scorecard WebView (Right Side) ---
        String symbolStr = engine.getSymbol() != null ? engine.getSymbol() : "EURUSD";
        String periodStr = engine.getPeriod() != null ? engine.getPeriod() : "H1";
        String expertStr = engine.getExpert() != null ? engine.getExpert() : "";

        // --- Compute Sensitiv + KI Scores for the scorecard circles ---
        double sensitivScoreVal = -1.0;
        double kiScoreVal = -1.0;
        if (match != null) {
            double worstCv = Math.max(match.getOverallCV(), match.getOverallCVFw());
            sensitivScoreVal = Math.max(0, Math.min(100, 100.0 - worstCv));
            String kiText = match.getKiResult();
            if (kiText != null && !kiText.isEmpty()) {
                try {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,3})\\s*/\\s*100").matcher(kiText);
                    if (m.find()) {
                        kiScoreVal = Double.parseDouble(m.group(1));
                    } else {
                        kiScoreVal = Double.parseDouble(kiText.trim());
                    }
                } catch (Exception ignored) {}
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
        rightPane.setStyle("-fx-background-color: #0b0d13;");

        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(scroll, rightPane);
        splitPane.setDividerPositions(0.55);
        splitPane.setStyle("-fx-background-color: #0b0d13; -fx-box-border: transparent;");

        Scene scene = new Scene(splitPane, 1500, 950);
        stage.setScene(scene);

        // Inherit styling from main screen
        if (root.getScene() != null && !root.getScene().getStylesheets().isEmpty()) {
            scene.getStylesheets().addAll(root.getScene().getStylesheets());
        }

        // Style chart after elements are shown
        final XYChart.Series<Number, Number> finalFwSeries = forwardSeries;
        stage.setOnShown(e -> {
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

        stage.showAndWait();
    }

    private void showRobustnessScorecardWebView(CombinedPass cp) {
        Stage stage = new Stage();
        stage.setTitle("🛡️ Robustness Scorecard: Pass #" + cp.getPassNumber());
        if (root.getScene() != null && root.getScene().getWindow() != null) {
            stage.initOwner(root.getScene().getWindow());
        }
        stage.initModality(Modality.APPLICATION_MODAL);

        String fromDateStr = engine.getFromDate() != null ? engine.getFromDate().toString() : "Unbekannt";
        String toDateStr = engine.getToDate() != null ? engine.getToDate().toString() : "Unbekannt";
        String symbolStr = engine.getSymbol() != null ? engine.getSymbol() : "EURUSD";
        String periodStr = engine.getPeriod() != null ? engine.getPeriod() : "H1";
        String expertStr = engine.getExpert() != null ? engine.getExpert() : "";

        // --- Compute Sensitiv + KI Scores for the scorecard circles ---
        double sensitivScoreVal2 = -1.0;
        double kiScoreVal2 = -1.0;
        if (engine.getSensitivityResults() != null) {
            for (SensitivityResult sr : engine.getSensitivityResults()) {
                if (sr.getOriginalPass() != null && sr.getOriginalPass().getPassNumber() == cp.getPassNumber()) {
                    double worstCv = Math.max(sr.getOverallCV(), sr.getOverallCVFw());
                    sensitivScoreVal2 = Math.max(0, Math.min(100, 100.0 - worstCv));
                    String kiText = sr.getKiResult();
                    if (kiText != null && !kiText.isEmpty()) {
                        try {
                            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,3})\\s*/\\s*100").matcher(kiText);
                            if (m.find()) {
                                kiScoreVal2 = Double.parseDouble(m.group(1));
                            } else {
                                kiScoreVal2 = Double.parseDouble(kiText.trim());
                            }
                        } catch (Exception ignored) {}
                    }
                    break;
                }
            }
        }

        String htmlContent = com.backtester.report.RobustnessScorecardGenerator.generateHtml(
            cp, expertStr, symbolStr, periodStr, fromDateStr, toDateStr, sensitivScoreVal2, kiScoreVal2
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

    private Label addDetailMetricRow(GridPane grid, int row, String labelText, String valueText) {
        Label label = new Label(labelText);
        label.setTextFill(Color.web("#7e889a"));
        label.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        
        Label value = new Label(valueText);
        value.setTextFill(Color.web("#e6e9f0"));
        value.setFont(Font.font("Segoe UI", 15));
        
        grid.add(label, 0, row);
        grid.add(value, 1, row);
        return value;
    }

    @SuppressWarnings("unchecked")
    private TableView<Map.Entry<String, Double>> buildCvBreakdownTable(
            Map<String, Double> cvMap,
            Map<String, List<com.backtester.report.SensitivityResult.DataPoint>> curves,
            Map<String, String> baseValues,
            String accentColor) {

        TableView<Map.Entry<String, Double>> cvTable = new TableView<>();
        TableColumn<Map.Entry<String, Double>, String> paramCol = new TableColumn<>("Parameter");
        paramCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getKey()));

        TableColumn<Map.Entry<String, Double>, VBox> valCol = new TableColumn<>("CV (%)");
        valCol.setCellValueFactory(c -> {
            String pName = c.getValue().getKey();
            double cv = c.getValue().getValue();
            List<com.backtester.report.SensitivityResult.DataPoint> curveData =
                    curves != null ? curves.get(pName) : null;

            VBox calcBox = new VBox(5);
            calcBox.setAlignment(Pos.CENTER_LEFT);
            calcBox.setPadding(new Insets(0, 0, 0, 10));

            Label cvValueLabel = new Label(String.format("%.2f %%", cv));
            cvValueLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17));
            cvValueLabel.setTextFill(Color.web(accentColor));

            Button infoBtn = new Button("ℹ");
            infoBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + accentColor +
                    "; -fx-cursor: hand; -fx-border-color: " + accentColor +
                    "; -fx-border-radius: 15px; -fx-font-weight: bold; -fx-padding: 0 5 0 5;");

            HBox topBox = new HBox(10, cvValueLabel, infoBtn);
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
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Erklärung: Parameter Robustness");
                    alert.setHeaderText("Was bedeutet der CV-Wert für " + pName + "?");

                    String explanation = String.format(Locale.US,
                        "Der CV-Wert (Coefficient of Variation) zeigt an, wie stark der Profit schwankt, wenn sich der Parameter '%s' leicht ändert.\n\n" +
                        "Ein kleiner CV-Wert bedeutet, dass die Strategie sehr stabil (robust) ist.\n" +
                        "Ein hoher Wert zeigt an, dass schon winzige Änderungen am Parameter den Profit massiv einbrechen lassen können – die Strategie ist hier anfällig und überoptimiert!\n\n" +
                        "--- BERECHNUNG ---\n\n" +
                        "1. Durchschnittlicher Profit der Varianten (Mean):\n" +
                        "In unseren Tests lag der Profit für diesen Parameter im Schnitt bei %.2f USD.\n\n" +
                        "2. Schwankung (Standardabweichung / StdDev):\n" +
                        "Der Profit schwankte im Schnitt um %.2f USD.\n\n" +
                        "3. Die Formel (CV):\n" +
                        "Wir teilen die Schwankung durch den ORIGINALEN Basis-Profit der optimierten Strategie und rechnen mal 100:\n" +
                        "CV = (StdDev / |Basis-Profit|) * 100\n" +
                        "CV = (%.2f / %.2f) * 100 = %.2f %%\n\n" +
                        "Hinweis: Wir verwenden den Basis-Profit statt des Durchschnitts, weil der klassische CV bei Profiten nahe Null (wo positive und negative Ergebnisse gemischt werden) unsinnig hohe Werte liefert.\n\n" +
                        "Faustregel:\n" +
                        "• Unter 20%: Sehr robust. Der Parameter ist stabil.\n" +
                        "• 20% - 50%: Akzeptabel. Es gibt Schwankungen, aber im Rahmen.\n" +
                        "• Über 50%: Gefährlich! Die Strategie ist hier eine 'Klippe' und extrem riskant.",
                        pName, mean, stdDev, stdDev, Math.abs(mean) > 0.01 ? Math.abs(mean) : 1.0, cv
                    );

                    Label expLabel = new Label(explanation);
                    expLabel.setWrapText(true);
                    expLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13px;");
                    alert.getDialogPane().setContent(expLabel);
                    alert.getDialogPane().setPrefWidth(550);
                    alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
                    try {
                        if (cvTable.getScene() != null && !cvTable.getScene().getStylesheets().isEmpty()) {
                            alert.getDialogPane().getStylesheets().addAll(cvTable.getScene().getStylesheets());
                        }
                    } catch (Exception ignored) {}
                    alert.getDialogPane().setStyle("-fx-base: #11141d; -fx-background-color: #11141d; -fx-text-fill: white;");
                    alert.showAndWait();
                });

                Label formulaLabel = new Label("CV = (StdDev / |Basis-Profit|) * 100");
                formulaLabel.setFont(Font.font("Segoe UI", 12));
                formulaLabel.setTextFill(Color.web("#8093a5"));

                Label calcLabel = new Label(String.format(Locale.US,
                        "= (%.2f / |Basis-Profit|) * 100 = %.2f%%", stdDev, cv));
                calcLabel.setFont(Font.font("Segoe UI", 12));
                calcLabel.setTextFill(Color.web("#8093a5"));

                calcBox.getChildren().addAll(topBox, formulaLabel, calcLabel);
            } else {
                calcBox.getChildren().add(topBox);
            }
            return new SimpleObjectProperty<>(calcBox);
        });
        valCol.setPrefWidth(200);

        TableColumn<Map.Entry<String, Double>, VBox> chartCol = new TableColumn<>("Curve");
        chartCol.setCellValueFactory(c -> {
            String pName = c.getValue().getKey();
            List<com.backtester.report.SensitivityResult.DataPoint> curveData =
                    curves != null ? curves.get(pName) : null;
            if (curveData == null || curveData.isEmpty()) {
                return new SimpleObjectProperty<>(null);
            }

            String baseValueStr = baseValues != null ? baseValues.get(pName) : null;
            double baseValue = 0;
            try { if (baseValueStr != null) baseValue = Double.parseDouble(baseValueStr); } catch (Exception ignored) {}
            final double finalBaseValue = baseValue;

            double minX = curveData.get(0).paramValue;
            double maxX = curveData.get(curveData.size() - 1).paramValue;
            double xPadding = (maxX - minX) * 0.05;
            if (xPadding == 0) xPadding = 1;

            NumberAxis xAxis = new NumberAxis();
            xAxis.setTickLabelsVisible(true); xAxis.setOpacity(1);
            xAxis.setTickMarkVisible(true); xAxis.setMinorTickVisible(false);
            xAxis.setTickLabelFill(Color.WHITE);
            xAxis.setAutoRanging(false);
            xAxis.setLowerBound(minX - xPadding);
            xAxis.setUpperBound(maxX + xPadding);

            NumberAxis yAxis = new NumberAxis();
            yAxis.setTickLabelsVisible(false); yAxis.setOpacity(0);
            yAxis.setTickMarkVisible(false); yAxis.setMinorTickVisible(false);

            double minY = curveData.stream().mapToDouble(d -> d.profit).min().orElse(0);
            double maxY = curveData.stream().mapToDouble(d -> d.profit).max().orElse(1);
            double yPadding = (maxY - minY) * 0.1;
            if (yPadding == 0) yPadding = 1;
            yAxis.setAutoRanging(false);
            yAxis.setLowerBound(minY - yPadding);
            yAxis.setUpperBound(maxY + yPadding);

            LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
            chart.setCreateSymbols(true);
            chart.setLegendVisible(false);
            chart.setAnimated(false);
            chart.setPrefHeight(130); chart.setMinHeight(130); chart.setMaxHeight(130);
            chart.setPrefWidth(400);
            chart.setHorizontalGridLinesVisible(false);
            chart.setVerticalGridLinesVisible(false);

            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            com.backtester.report.SensitivityResult.DataPoint closestToBase = null;
            double minDiff = Double.MAX_VALUE;

            for (com.backtester.report.SensitivityResult.DataPoint dp : curveData) {
                series.getData().add(new XYChart.Data<>(dp.paramValue, dp.profit));
                double diff = Math.abs(dp.paramValue - finalBaseValue);
                if (diff < minDiff) { minDiff = diff; closestToBase = dp; }
            }
            chart.getData().add(series);
            final com.backtester.report.SensitivityResult.DataPoint finalClosest = closestToBase;

            chart.lookup(".chart-plot-background").setStyle("-fx-background-color: transparent;");
            chart.setStyle("-fx-padding: 0; -fx-background-color: transparent;");

            Platform.runLater(() -> {
                if (series.getNode() != null) {
                    series.getNode().setStyle("-fx-stroke: " + accentColor + "; -fx-stroke-width: 4px;");
                }
                for (XYChart.Data<Number, Number> data : series.getData()) {
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
            String infoTxt = String.format(Locale.US, "Start: %.4f | Step: %.4f | End: %.4f", minX, stepVal, maxX)
                                   .replaceAll("0+ \\|", " |").replaceAll("\\. \\|", " |");
            Label infoLabel = new Label(infoTxt);
            infoLabel.setTextFill(Color.web("#8093a5"));
            infoLabel.setFont(Font.font("Segoe UI", 13));

            VBox chartBox = new VBox(5, chart, infoLabel);
            chartBox.setAlignment(Pos.CENTER);
            return new SimpleObjectProperty<>(chartBox);
        });

        chartCol.setCellFactory(col -> new TableCell<Map.Entry<String, Double>, VBox>() {
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
            for (Map.Entry<String, Double> entry : cvMap.entrySet()) {
                cvTable.getItems().add(new java.util.AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
            }
        }
        cvTable.setStyle("-fx-background-color: transparent; -fx-font-size: 14px;");
        cvTable.setPrefHeight(380);
        cvTable.setFixedCellSize(160);
        cvTable.setSelectionModel(null);
        cvTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        return cvTable;
    }

    private CombinedPass findPassByNumber(int passNum) {
        // 1. Check selected diverse passes (steps 3-5)
        for (CombinedPass cp : engine.getSelectedDiversePasses()) {
            if (cp.getPassNumber() == passNum) {
                return cp;
            }
        }
        // 2. Check final selected passes (step 6)
        for (CombinedPass cp : engine.getFinalSelectedPasses()) {
            if (cp.getPassNumber() == passNum) {
                return cp;
            }
        }
        // 3. Check all optimization passes (step 2)
        if (engine.getOptResult() != null) {
            List<CombinedPass> allPasses = engine.getOptResult().buildCombinedPasses(engine.getForwardMode() > 0, engine.loadScoreWeightsFromDb());
            for (CombinedPass cp : allPasses) {
                if (cp.getPassNumber() == passNum) {
                    return cp;
                }
            }
        }
        return null;
    }

    private void runSingleBacktest(CombinedPass cp, boolean visual) {
        String expert = engine.getExpert();
        if (expert == null || expert.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Bitte wählen Sie zuerst einen Expert Advisor aus!").show();
            return;
        }

        String symbol = engine.getSymbol();
        String period = engine.getPeriod();
        LocalDate from = engine.getFromDate();
        LocalDate to = engine.getToDate();
        int deposit = engine.getDeposit();
        String currency = engine.getCurrency();
        String leverage = engine.getLeverage();
        int tickModel = engine.getTickModel();

        // 1. Prepare BacktestConfig
        BacktestConfig btConfig = new BacktestConfig();
        btConfig.setExpert(expert);
        btConfig.setSymbol(symbol);
        btConfig.setPeriod(period);
        btConfig.setModel(tickModel);
        btConfig.setFromDate(from);
        btConfig.setToDate(to);
        btConfig.setDeposit(deposit);
        btConfig.setCurrency(currency);
        btConfig.setLeverage(leverage);
        btConfig.setShutdownTerminal(!visual);
        btConfig.setVisualMode(visual);

        // 2. Prepare parameter override file
        String eaName = EaParameterManager.extractEaBaseName(expert);
        EaParameterManager eaParamManager = new EaParameterManager();
        List<EaParameter> params = eaParamManager.getEffectiveParameters(expert);
        if (params != null) {
            Map<String, String> passVals = cp.getBacktestPass().getParameterValues();
            for (EaParameter p : params) {
                if (passVals.containsKey(p.getName())) {
                    p.setValue(passVals.get(p.getName()));
                }
            }
            Path mt5Dir = AppConfig.getInstance().getMt5InstallDir();
            if (mt5Dir != null) {
                Path presetsDir = mt5Dir.resolve("MQL5").resolve("Profiles").resolve("Tester");
                Path destFile = presetsDir.resolve("Backtester_" + eaName + ".set");
                eaParamManager.writeSetFile(destFile, params, eaName);
                btConfig.setExpertParameters("Backtester_" + eaName + ".set");
            }
        }

        // 3. Create dialog for logs and progress
        Stage dialogStage = new Stage();
        dialogStage.setTitle("Backtest - Pass " + cp.getPassNumber() + (visual ? " (Visuell)" : ""));
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        if (root.getScene() != null && root.getScene().getWindow() != null) {
            dialogStage.initOwner(root.getScene().getWindow());
        }

        VBox dialogBox = new VBox(12);
        dialogBox.setPadding(new Insets(20));
        dialogBox.setStyle("-fx-background-color: #0b0d13; -fx-border-color: #3e4555; -fx-border-width: 1px;");

        Label titleLabel = new Label("BACKTEST LÄUFT...");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        titleLabel.setTextFill(Color.web("#00e5ff"));

        Label statusLabel = new Label("Initialisiere MetaTrader 5...");
        statusLabel.setTextFill(Color.web("#cbd5e1"));
        statusLabel.setFont(Font.font("Segoe UI", 13));

        ProgressBar pb = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
        pb.setPrefWidth(560);

        TextArea logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setFont(Font.font("Consolas", 12));
        logArea.setPrefHeight(300);
        logArea.getStyleClass().add("text-area");
        logArea.setStyle("-fx-control-inner-background: #14161c; -fx-text-fill: #b4bac8;");

        Button cancelBtn = new Button("Abbrechen");
        cancelBtn.getStyleClass().add("button");
        cancelBtn.setStyle("-fx-background-color: #ff3b30; -fx-text-fill: white;");

        dialogBox.getChildren().addAll(titleLabel, statusLabel, pb, logArea, cancelBtn);
        
        Scene dialogScene = new Scene(dialogBox, 600, 480);
        if (root.getScene() != null && !root.getScene().getStylesheets().isEmpty()) {
            dialogScene.getStylesheets().addAll(root.getScene().getStylesheets());
        }
        dialogStage.setScene(dialogScene);

        // 4. Set up task and runner
        BacktestRunner runner = new BacktestRunner();
        runner.setLogCallback(msg -> Platform.runLater(() -> {
            logArea.appendText(msg + "\n");
            logArea.selectPositionCaret(logArea.getLength());
        }));

        Task<BacktestResult> task = new Task<BacktestResult>() {
            @Override
            protected BacktestResult call() throws Exception {
                return runner.runBacktest(btConfig);
            }
        };

        cancelBtn.setOnAction(e -> {
            if (task.isRunning()) {
                runner.cancel();
                task.cancel();
            }
            dialogStage.close();
        });

        task.setOnSucceeded(e -> {
            BacktestResult result = task.getValue();
            Platform.runLater(() -> {
                pb.setProgress(1.0);
                if (result != null && result.isSuccess()) {
                    titleLabel.setText("BACKTEST ERFOLGREICH");
                    titleLabel.setTextFill(Color.web("#00e676"));
                    statusLabel.setText("Backtest abgeschlossen. Report wird geöffnet...");
                    cancelBtn.setText("Schließen");
                    cancelBtn.setStyle(""); // reset red background
                    // Auto open HTML report
                    openReport(result.getOutputDirectory());
                } else {
                    titleLabel.setText("BACKTEST FEHLGESCHLAGEN");
                    titleLabel.setTextFill(Color.web("#ff3b30"));
                    statusLabel.setText(result != null ? "Fehler: " + result.getMessage() : "Fehler beim Ausführen des Backtests.");
                    cancelBtn.setText("Schließen");
                    cancelBtn.setStyle("");
                }
            });
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            Platform.runLater(() -> {
                pb.setProgress(0.0);
                titleLabel.setText("BACKTEST FEHLER");
                titleLabel.setTextFill(Color.web("#ff3b30"));
                statusLabel.setText(ex != null ? ex.getMessage() : "Unbekannter Fehler.");
                cancelBtn.setText("Schließen");
                cancelBtn.setStyle("");
            });
        });

        task.setOnCancelled(e -> {
            Platform.runLater(() -> {
                pb.setProgress(0.0);
                titleLabel.setText("BACKTEST ABGEBROCHEN");
                titleLabel.setTextFill(Color.web("#ffb300"));
                statusLabel.setText("Der Backtest wurde vom Benutzer abgebrochen.");
                cancelBtn.setText("Schließen");
                cancelBtn.setStyle("");
            });
        });

        // 5. Start background execution
        Thread th = new Thread(task);
        th.setDaemon(true);
        th.start();

        // 6. Show dialog
        dialogStage.show();
    }

    private void openReport(String directory) {
        try {
            javax.swing.SwingUtilities.invokeLater(() -> {
                com.backtester.ui.ReportViewerDialog.showForDirectory(null, directory);
            });
        } catch (Exception e) {
            logToConsole("ERROR", "Could not open report: " + e.getMessage());
        }
    }

    public static class JavaBridge {
        private final WorkflowView view;

        public JavaBridge(WorkflowView view) {
            this.view = view;
        }

        public void showPass(int passNum) {
            Platform.runLater(() -> {
                CombinedPass cp = view.findPassByNumber(passNum);
                if (cp != null) {
                    view.showStrategyDetailDialog(cp);
                } else {
                    view.logToConsole("BRIDGE", "Pass " + passNum + " nicht in den aktuellen Workflow-Ergebnissen gefunden.");
                }
            });
        }
    }

    public static class ConsoleLoggerBridge {
        public void log(String text) { log.info("JS CONSOLE: " + text); }
        public void error(String text) { log.error("JS ERROR: " + text); }
    }

    private void updateStatsTab() {
        if (statsTable == null || noStatsLabel == null) return;
        if (engine.getOptResult() != null && !engine.getOptResult().getPasses().isEmpty()) {
            String ea = engine.getExpert() != null ? engine.getExpert() : "";
            if (ea.contains("\\")) {
                ea = ea.substring(ea.lastIndexOf("\\") + 1);
            } else if (ea.contains("/")) {
                ea = ea.substring(ea.lastIndexOf("/") + 1);
            }
            String sym = engine.getSymbol() != null ? engine.getSymbol() : "-";
            String tf = engine.getPeriod() != null ? engine.getPeriod() : "-";
            String dates = (engine.getFromDate() != null ? engine.getFromDate() : "?") + " bis " + (engine.getToDate() != null ? engine.getToDate() : "?");
            int passesCount = engine.getOptResult().getPasses().size();
            double maxProf = engine.getOptResult().getBestByProfit() != null ? engine.getOptResult().getBestByProfit().getProfit() : 0.0;

            OptimizationStatsRow row = new OptimizationStatsRow(
                ea, sym, tf, dates, passesCount, maxProf, "▶ Klicke für Statistik-Grafiken"
            );
            statsTable.getItems().setAll(row);
            statsTable.setVisible(true);
            noStatsLabel.setVisible(false);
        } else {
            statsTable.getItems().clear();
            statsTable.setVisible(false);
            noStatsLabel.setVisible(true);
        }
    }

    public WorkflowEngine getEngine() {
        return engine;
    }

    public boolean isWorkflowRunning() {
        return activeWorkflowTask != null;
    }

    public void refreshUI() {
        Platform.runLater(() -> {
            updateVisualStates();
            int lastStep = engine.getLastActiveStep();
            selectStep(lastStep > 0 ? lastStep : 1);
        });
    }

    public static class OptimizationStatsRow {
        private final String eaName;
        private final String symbol;
        private final String period;
        private final String periodDate;
        private final int totalPasses;
        private final double maxProfit;
        private final String status;

        public OptimizationStatsRow(String eaName, String symbol, String period, String periodDate, int totalPasses, double maxProfit, String status) {
            this.eaName = eaName;
            this.symbol = symbol;
            this.period = period;
            this.periodDate = periodDate;
            this.totalPasses = totalPasses;
            this.maxProfit = maxProfit;
            this.status = status;
        }

        public String getEaName() { return eaName; }
        public String getSymbol() { return symbol; }
        public String getPeriod() { return period; }
        public String getPeriodDate() { return periodDate; }
        public int getTotalPasses() { return totalPasses; }
        public double getMaxProfit() { return maxProfit; }
        public String getStatus() { return status; }
    }
}
