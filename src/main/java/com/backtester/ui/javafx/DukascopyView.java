package com.backtester.ui.javafx;

import com.backtester.config.AppConfig;
import com.backtester.engine.BacktestConfig;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;

public class DukascopyView {

    private final BorderPane root;
    private final LogView logView;
    private final AppConfig config;

    // Download section
    private DatePicker downloadFromDate;
    private DatePicker downloadToDate;
    private ProgressBar downloadProgress;

    // Import section
    private TableView<Object> dataTable;
    private ProgressBar importProgress;

    public DukascopyView(LogView logView) {
        this.logView = logView;
        this.config = AppConfig.getInstance();

        root = new BorderPane();
        root.setPadding(new Insets(15));

        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);
        splitPane.setStyle("-fx-background-color: transparent; -fx-box-border: transparent;");

        VBox downloadBox = createDownloadBox();
        VBox importBox = createImportBox();

        splitPane.getItems().addAll(downloadBox, importBox);
        splitPane.setDividerPositions(0.45);

        root.setCenter(splitPane);
    }

    private VBox createDownloadBox() {
        VBox box = new VBox(15);
        box.getStyleClass().add("sci-fi-panel");

        Label title = new Label("Step 1: Download Dukascopy Data");
        title.getStyleClass().add("sci-fi-panel-title");
        
        String overview = "Der Dukascopy Data Tab ist die professionelle Lösung für das fundamentale Problem ungenauer Broker-Daten. Wenn du Backtests im MetaTrader durchführst, bist du standardmäßig auf die historischen Daten deines Brokers angewiesen. Diese weisen oft Lücken auf, haben eine schlechte Qualität (weniger als 99% Modelling Quality) oder gehen nicht weit genug in die Vergangenheit zurück.\n\n" +
                          "Warum ist das ein Problem? Weil Scalping-Strategien oder hochfrequente EAs bei schlechten Daten fantastische Ergebnisse im Backtest liefern können, die in der Realität jedoch komplett scheitern. Dieses Phänomen nennt sich 'Garbage In, Garbage Out'.\n\n" +
                          "Dukascopy ist ein renommierter Schweizer Broker, der seine exakten, realen Tick-Daten (jeden einzelnen Preis-Tick) kostenlos zur Verfügung stellt. Mit diesem Tab kannst du diese riesigen Datenmengen vollautomatisiert herunterladen, sie in das benötigte Format konvertieren und als 'Custom Symbol' (z.B. EURUSD_Duka) in deinen MetaTrader importieren. Dies garantiert dir eine Modellierungsqualität von 99.9% und somit Backtest-Ergebnisse, denen du tatsächlich vertrauen kannst.";
        String details = "Umfassender Workflow und Funktionen:\n\n" +
                         "1. Symbol Selection (Währungspaare):\n" +
                         "   Wähle die Instrumente aus, für die du historische Daten benötigst. Bedenke: Tick-Daten sind gigantisch groß. Ein einziges Jahr EURUSD kann als unkomprimierte CSV-Datei mehrere Gigabyte Speicherplatz beanspruchen. Lade daher nur die Paare herunter, die du wirklich intensiv testen möchtest.\n\n" +
                         "2. Date Range (Zeitraum):\n" +
                         "   Lege fest, wie weit in die Vergangenheit die Daten reichen sollen. Ein robuster Backtest sollte im Idealfall über 5-10 Jahre durchgeführt werden, um sicherzustellen, dass die Strategie Bullenmärkte, Bärenmärkte und Flash-Crashes überlebt.\n\n" +
                         "3. Step 1: Download Data:\n" +
                         "   Dieser Prozess verbindet sich mit den Servern von Dukascopy und lädt die Daten im stark komprimierten, proprietären '.bi5' Format herunter. Dieser Vorgang kann je nach Zeitraum und Internetverbindung extrem lange dauern (teilweise Stunden). Der Download-Manager im Hintergrund stellt sicher, dass abbrechende Verbindungen wieder aufgenommen werden.\n\n" +
                         "4. Step 2: Scan & Convert (Konvertierung):\n" +
                         "   Die heruntergeladenen .bi5 Dateien können vom MetaTrader nicht direkt gelesen werden. \n" +
                         "   - 'Scan Downloaded Data' durchsucht deinen lokalen Speicher nach erfolgreich heruntergeladenen Datensätzen.\n" +
                         "   - 'Convert to CSV' entpackt und wandelt die .bi5 Dateien in standardisierte, tabellarische CSV-Dateien um. Dabei werden Zeitstempel (inklusive Millisekunden), Bid-Preis, Ask-Preis und das Volumen exakt extrahiert.\n\n" +
                         "5. Step 3: Import to MT5 (MetaTrader Integration):\n" +
                         "   Dies ist der wichtigste Schritt. Das Tool generiert automatisch ein sogenanntes 'Custom Symbol' in deinem MetaTrader. Es injiziert die konvertierten Tick-Daten direkt in die Datenbank des MT5. Wenn du nun im Backtest-Tab dieses Custom Symbol auswählst und das Tick-Modell auf 'Every tick based on real ticks' stellst, führt der MT5 den Test mit den Schweizer Bank-Daten aus. Das Ergebnis ist ein Backtest in institutioneller Qualität.";
                         
        javafx.scene.layout.Region infoSpacer = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(infoSpacer, javafx.scene.layout.Priority.ALWAYS);
        javafx.scene.layout.HBox titleBox = new javafx.scene.layout.HBox(15, title, infoSpacer, DocHelper.createInfoButton("Dukascopy Data", overview, details));
        titleBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // Symbols
        VBox symbolBox = new VBox(10);
        Label symbolLabel = new Label("Select Currency Pairs:");
        
        GridPane cbGrid = new GridPane();
        cbGrid.setHgap(15);
        cbGrid.setVgap(10);
        int col = 0;
        int row = 0;
        for (String symbol : BacktestConfig.SYMBOLS) {
            CheckBox cb = new CheckBox(symbol);
            if (symbol.equals("EURUSD")) cb.setSelected(true);
            cbGrid.add(cb, col, row);
            col++;
            if (col > 3) {
                col = 0;
                row++;
            }
        }
        
        HBox selectBtns = new HBox(10);
        Button selectAll = new Button("Select All");
        selectAll.getStyleClass().add("button");
        Button selectNone = new Button("Select None");
        selectNone.getStyleClass().add("button");
        selectBtns.getChildren().addAll(selectAll, selectNone);

        symbolBox.getChildren().addAll(symbolLabel, cbGrid, selectBtns);

        // Dates
        HBox dateBox = new HBox(15);
        dateBox.setAlignment(Pos.CENTER_LEFT);
        
        downloadFromDate = new DatePicker(LocalDate.now().minusMonths(6));
        downloadToDate = new DatePicker(LocalDate.now().minusDays(1));
        
        dateBox.getChildren().addAll(
                new Label("From:"), downloadFromDate,
                new Label("To:"), downloadToDate
        );

        // Controls
        HBox controlBox = new HBox(15);
        controlBox.setAlignment(Pos.CENTER_LEFT);
        
        Button downloadBtn = new Button("⬇ Download Data");
        downloadBtn.getStyleClass().addAll("button", "button-start");
        downloadBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #28648c, #143246);");

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("button");
        cancelBtn.setDisable(true);

        downloadProgress = new ProgressBar(0);
        downloadProgress.setPrefWidth(300);

        controlBox.getChildren().addAll(downloadBtn, cancelBtn, downloadProgress);

        box.getChildren().addAll(titleBox, symbolBox, dateBox, controlBox);
        return box;
    }

    private VBox createImportBox() {
        VBox box = new VBox(10);
        box.getStyleClass().add("sci-fi-panel");

        Label title = new Label("Step 2: Convert & Import to MetaTrader 5");
        title.getStyleClass().add("sci-fi-panel-title");

        dataTable = new TableView<>();
        dataTable.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(dataTable, Priority.ALWAYS);

        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_LEFT);

        Button scanBtn = new Button("Scan Downloaded Data");
        scanBtn.getStyleClass().add("button");

        Button convertBtn = new Button("Convert to CSV");
        convertBtn.getStyleClass().add("button");
        convertBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #786428, #3c3214);");

        Button importBtn = new Button("Import to MT5");
        importBtn.getStyleClass().addAll("button", "button-start");
        importBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #287846, #143c23);");

        Button exportBtn = new Button("\uD83D\uDCCA Export CSV");
        exportBtn.getStyleClass().add("button");

        importProgress = new ProgressBar(0);
        importProgress.setPrefWidth(200);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        btnBox.getChildren().addAll(scanBtn, convertBtn, importBtn, exportBtn, spacer, importProgress);

        box.getChildren().addAll(title, dataTable, btnBox);
        return box;
    }

    public BorderPane getView() {
        return root;
    }
}
