package com.backtester.ui.javafx;

import com.backtester.config.AppConfig;
import com.backtester.dukascopy.Bi5Decoder;
import com.backtester.dukascopy.CsvConverter;
import com.backtester.dukascopy.DukascopyDownloader;
import com.backtester.engine.BacktestConfig;
import com.backtester.mt5.CustomSymbolManager;
import com.backtester.mt5.Mt5DataImporter;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * JavaFX view for downloading Dukascopy tick data and importing it into MT5.
 *
 * Two sections:
 * 1. Download — select symbols and date range, download .bi5 files
 * 2. Import to MT5 — convert to CSV and create Custom Symbols
 *
 * All blocking work (download, scan, convert, import, export) runs inside
 * javafx.concurrent.Task; results are reported via the shared LogView and
 * JavaFX Alerts.
 */
public class DukascopyView {

    private final BorderPane root;
    private final LogView logView;
    private final AppConfig config;
    private final CustomSymbolManager symbolManager;

    // Download section
    private final List<CheckBox> symbolBoxes = new ArrayList<>();
    private DatePicker downloadFromDate;
    private DatePicker downloadToDate;
    private Button downloadBtn;
    private Button cancelBtn;
    private ProgressBar downloadProgress;
    private Task<Void> downloadTask;
    private DukascopyDownloader currentDownloader;

    // Import section
    private TableView<DataRow> dataTable;
    private Button scanBtn;
    private Button convertBtn;
    private Button importBtn;
    private Button exportBtn;
    private ProgressBar importProgress;

    public DukascopyView(LogView logView) {
        this.logView = logView;
        this.config = AppConfig.getInstance();
        this.symbolManager = new CustomSymbolManager(config.getBasePath().resolve("config"));

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

        // Initial scan so the table reflects already-downloaded data
        Platform.runLater(this::scanDownloadedData);
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
            symbolBoxes.add(cb);
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
        selectAll.setOnAction(e -> symbolBoxes.forEach(cb -> cb.setSelected(true)));
        Button selectNone = new Button("Select None");
        selectNone.getStyleClass().add("button");
        selectNone.setOnAction(e -> symbolBoxes.forEach(cb -> cb.setSelected(false)));
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

        downloadBtn = new Button("⬇ Download Data");
        downloadBtn.getStyleClass().addAll("button", "button-start");
        downloadBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #28648c, #143246);");
        downloadBtn.setOnAction(e -> startDownload());

        cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("button");
        cancelBtn.setDisable(true);
        cancelBtn.setOnAction(e -> cancelDownload());

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
        dataTable.setPlaceholder(new Label("No downloaded data yet — download data or click 'Scan Downloaded Data'."));
        dataTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        TableColumn<DataRow, String> symbolCol = new TableColumn<>("Symbol");
        symbolCol.setCellValueFactory(cd -> cd.getValue().symbolProperty());
        symbolCol.setPrefWidth(110);

        TableColumn<DataRow, String> rangeCol = new TableColumn<>("Data Range");
        rangeCol.setCellValueFactory(cd -> cd.getValue().rangeProperty());
        rangeCol.setPrefWidth(110);

        TableColumn<DataRow, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cd -> cd.getValue().statusProperty());
        statusCol.setPrefWidth(150);

        TableColumn<DataRow, String> barsCol = new TableColumn<>("Bars");
        barsCol.setCellValueFactory(cd -> cd.getValue().barsProperty());
        barsCol.setPrefWidth(90);

        TableColumn<DataRow, String> csvCol = new TableColumn<>("CSV File");
        csvCol.setCellValueFactory(cd -> cd.getValue().csvFileProperty());
        csvCol.setPrefWidth(420);

        dataTable.getColumns().addAll(symbolCol, rangeCol, statusCol, barsCol, csvCol);
        VBox.setVgrow(dataTable, Priority.ALWAYS);

        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_LEFT);

        scanBtn = new Button("Scan Downloaded Data");
        scanBtn.getStyleClass().add("button");
        scanBtn.setOnAction(e -> scanDownloadedData());

        convertBtn = new Button("Convert to CSV");
        convertBtn.getStyleClass().add("button");
        convertBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #786428, #3c3214);");
        convertBtn.setOnAction(e -> convertToCsv());

        importBtn = new Button("Import to MT5");
        importBtn.getStyleClass().addAll("button", "button-start");
        importBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #287846, #143c23);");
        importBtn.setOnAction(e -> importToMt5());

        exportBtn = new Button("\uD83D\uDCCA Export CSV");
        exportBtn.getStyleClass().add("button");
        exportBtn.setOnAction(e -> exportSelectedToCsv());

        importProgress = new ProgressBar(0);
        importProgress.setPrefWidth(200);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        btnBox.getChildren().addAll(scanBtn, convertBtn, importBtn, exportBtn, spacer, importProgress);

        box.getChildren().addAll(title, dataTable, btnBox);
        return box;
    }

    // ------------------------------------------------------------------
    // Step 1: Download
    // ------------------------------------------------------------------

    private void startDownload() {
        List<String> symbols = getSelectedSymbols();
        if (symbols.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select at least one currency pair.");
            return;
        }

        LocalDate from = downloadFromDate.getValue();
        LocalDate to = downloadToDate.getValue();
        if (from == null || to == null || from.isAfter(to)) {
            showAlert(Alert.AlertType.WARNING, "Invalid Dates", "Please select a valid date range.");
            return;
        }

        setActionsDisabled(true);
        cancelBtn.setDisable(false);
        downloadProgress.progressProperty().unbind();
        downloadProgress.setProgress(0);

        logView.log("INFO", "Starting Dukascopy download for: " + String.join(", ", symbols));
        logView.log("INFO", "Date range: " + from + " to " + to);

        currentDownloader = new DukascopyDownloader(config.getDataDirectory());
        currentDownloader.setLogCallback(msg -> logView.log("INFO", msg));

        downloadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                currentDownloader.setProgressCallback(progress -> updateProgress(progress, 1.0));
                int total = symbols.size();
                for (int i = 0; i < total; i++) {
                    if (isCancelled()) break;
                    String symbol = symbols.get(i);
                    updateMessage(String.format("Downloading %s (%d/%d)...", symbol, i + 1, total));
                    logView.log("INFO", String.format("Downloading %s (%d/%d)...", symbol, i + 1, total));
                    currentDownloader.download(symbol, from, to);
                }
                return null;
            }
        };

        downloadProgress.progressProperty().bind(downloadTask.progressProperty());

        downloadTask.setOnSucceeded(e -> {
            downloadProgress.progressProperty().unbind();
            downloadProgress.setProgress(1.0);
            setActionsDisabled(false);
            cancelBtn.setDisable(true);

            int errors = currentDownloader != null ? currentDownloader.getActualErrors() : 0;
            if (errors > 0) {
                logView.log("WARN", "Download completed with " + errors + " missing hours due to server errors.");
                showAlert(Alert.AlertType.WARNING, "Download mit kleinen Lücken",
                        "Download abgeschlossen.\n\n" + errors + " Dateien konnten wegen Überlastung des Dukascopy-Servers\n" +
                        "(HTTP 503 Fehler) nicht geladen werden.\n\n" +
                        "Das Programm hat diese Stunden zum Schutz vor einem Absturz übersprungen.");
            } else {
                logView.log("INFO", "All downloads completed perfectly!");
                showAlert(Alert.AlertType.INFORMATION, "Download Erfolgreich",
                        "Der Download aller gewählten Währungspaare\nwurde fehlerfrei und vollständig abgeschlossen!");
            }

            // Refresh data table
            scanDownloadedData();
        });
        downloadTask.setOnFailed(e -> {
            downloadProgress.progressProperty().unbind();
            downloadProgress.setProgress(0);
            setActionsDisabled(false);
            cancelBtn.setDisable(true);
            Throwable ex = downloadTask.getException();
            String msg = ex != null ? ex.getMessage() : "Unknown error";
            logView.log("ERROR", "Download error: " + msg);
            showAlert(Alert.AlertType.ERROR, "Download Fehler", "Beim Download ist ein Fehler aufgetreten:\n" + msg);
        });
        downloadTask.setOnCancelled(e -> {
            downloadProgress.progressProperty().unbind();
            downloadProgress.setProgress(0);
            setActionsDisabled(false);
            cancelBtn.setDisable(true);
            logView.log("WARN", "Download cancelled");
        });

        Thread t = new Thread(downloadTask, "dukascopy-download");
        t.setDaemon(true);
        t.start();
    }

    private void cancelDownload() {
        // The downloader's own cancel flag stops in-flight file downloads promptly;
        // cancelling the Task stops the per-symbol loop.
        if (currentDownloader != null) {
            currentDownloader.cancel();
        }
        if (downloadTask != null && downloadTask.isRunning()) {
            downloadTask.cancel(true);
        }
    }

    private List<String> getSelectedSymbols() {
        List<String> selected = new ArrayList<>();
        for (CheckBox cb : symbolBoxes) {
            if (cb.isSelected()) {
                selected.add(cb.getText());
            }
        }
        return selected;
    }

    // ------------------------------------------------------------------
    // Step 2: Scan / Convert / Import / Export
    // ------------------------------------------------------------------

    private void scanDownloadedData() {
        setActionsDisabled(true);
        importProgress.progressProperty().unbind();
        importProgress.setProgress(-1); // Indeterminate

        Task<List<DataRow>> scanTask = new Task<>() {
            @Override
            protected List<DataRow> call() {
                List<DataRow> rows = new ArrayList<>();
                Path dataDir = config.getDataDirectory();
                if (!Files.exists(dataDir)) return rows;

                try (var dirs = Files.list(dataDir)) {
                    List<Path> symbolDirs = dirs.filter(Files::isDirectory).sorted().toList();
                    int total = symbolDirs.size();
                    int done = 0;
                    for (Path symbolDir : symbolDirs) {
                        if (total > 0) updateProgress(done, total);
                        done++;
                        String symbol = symbolDir.getFileName().toString();
                        // Count .bi5 files
                        try (var walker = Files.walk(symbolDir)) {
                            long fileCount = walker.filter(p -> p.toString().endsWith(".bi5")).count();
                            if (fileCount > 0) {
                                // Check if CSV already exists
                                Path csvFile = dataDir.resolve(symbol + "_M1.csv");
                                String status = Files.exists(csvFile) ? "CSV Ready" : "Downloaded (.bi5)";
                                String csvPath = Files.exists(csvFile) ? csvFile.toString() : "—";

                                // Check if already imported to MT5
                                String customName = CustomSymbolManager.toCustomName(symbol);
                                if (symbolManager.hasSymbol(customName)) {
                                    status = "Imported to MT5";
                                }

                                rows.add(new DataRow(symbol, fileCount + " files", status, "—", csvPath));
                            }
                        } catch (Exception e) {
                            logView.log("WARN", "Error scanning " + symbol + ": " + e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    logView.log("ERROR", "Error scanning data directory: " + e.getMessage());
                }
                return rows;
            }
        };

        importProgress.progressProperty().bind(scanTask.progressProperty());

        scanTask.setOnSucceeded(e -> {
            importProgress.progressProperty().unbind();
            importProgress.setProgress(0);
            setActionsDisabled(false);
            dataTable.getItems().setAll(scanTask.getValue());
            logView.log("INFO", "Scan finished: " + scanTask.getValue().size() + " symbol(s) with downloaded data.");
        });
        scanTask.setOnFailed(e -> {
            importProgress.progressProperty().unbind();
            importProgress.setProgress(0);
            setActionsDisabled(false);
            Throwable ex = scanTask.getException();
            logView.log("ERROR", "Error scanning data directory: " + (ex != null ? ex.getMessage() : "Unknown error"));
        });

        Thread t = new Thread(scanTask, "dukascopy-scan");
        t.setDaemon(true);
        t.start();
    }

    private void convertToCsv() {
        List<DataRow> rows = getSelectedRowsOrAll();
        if (rows.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Data", "No downloaded data found. Download data first.");
            return;
        }

        setActionsDisabled(true);
        importProgress.progressProperty().unbind();
        importProgress.setProgress(-1); // Indeterminate until per-symbol progress starts

        Path dataDir = config.getDataDirectory();
        // Prefer the DST-aware broker zone from config when set; fall back to the fixed offset
        CsvConverter converter = config.getDukascopyBrokerZone() != null
                ? new CsvConverter(config.getDukascopyBrokerZone())
                : new CsvConverter(config.getBrokerTimezoneOffset());

        Task<Void> convertTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                Bi5Decoder decoder = new Bi5Decoder();
                int total = rows.size();
                for (int i = 0; i < total; i++) {
                    DataRow row = rows.get(i);
                    String symbol = row.getSymbol();
                    updateMessage("Converting " + symbol + "...");
                    updateProgress(i, total);
                    logView.log("INFO", "Converting " + symbol + " to M1 CSV...");

                    // Decode all ticks for this symbol
                    List<Bi5Decoder.Tick> allTicks = new ArrayList<>();
                    Path symbolDir = dataDir.resolve(symbol);

                    try (var walker = Files.walk(symbolDir)) {
                        walker.filter(p -> p.toString().endsWith(".bi5"))
                              .sorted()
                              .forEach(bi5File -> {
                                  try {
                                      Path relative = symbolDir.relativize(bi5File);
                                      int year = Integer.parseInt(relative.getName(0).toString());
                                      int month = Integer.parseInt(relative.getName(1).toString());
                                      int day = Integer.parseInt(relative.getName(2).toString());
                                      String hourStr = relative.getName(3).toString();
                                      int hour = Integer.parseInt(hourStr.substring(0, 2));

                                      LocalDate date = LocalDate.of(year, month, day);
                                      List<Bi5Decoder.Tick> ticks = decoder.decode(bi5File, symbol, date, hour);
                                      allTicks.addAll(ticks);
                                  } catch (Exception e) {
                                      // Corrupt cached .bi5: delete it and abort instead of silently
                                      // shortening the tick data (same behavior as Bi5Decoder.decodeRange).
                                      try {
                                          Files.deleteIfExists(bi5File);
                                      } catch (IOException deleteError) {
                                          e.addSuppressed(deleteError);
                                      }
                                      logView.log("ERROR", "Corrupt Dukascopy cache file deleted: " + bi5File
                                              + " (" + e.getMessage() + ")");
                                      IOException failure = e instanceof IOException io
                                              ? io
                                              : new IOException(e.getMessage(), e);
                                      throw new UncheckedIOException(
                                              "Corrupt Dukascopy cache file deleted: " + bi5File
                                                      + " — please re-download the data for " + symbol,
                                              failure);
                                  }
                              });
                    }

                    if (allTicks.isEmpty()) {
                        logView.log("WARN", "No tick data for " + symbol);
                        continue;
                    }

                    // Convert to M1 bars
                    List<CsvConverter.M1Bar> bars = converter.aggregateToM1(allTicks, symbol);
                    Path csvFile = dataDir.resolve(symbol + "_M1.csv");
                    int digits = CsvConverter.getDigits(symbol);
                    converter.writeCsv(bars, csvFile, digits);

                    logView.log("INFO", String.format("Converted %s: %d ticks → %d M1 bars → %s",
                            symbol, allTicks.size(), bars.size(), csvFile.getFileName()));

                    DataRow r = row;
                    String barCount = String.valueOf(bars.size());
                    String csvPath = csvFile.toString();
                    Platform.runLater(() -> {
                        r.statusProperty().set("CSV Ready");
                        r.barsProperty().set(barCount);
                        r.csvFileProperty().set(csvPath);
                    });
                }
                return null;
            }
        };

        importProgress.progressProperty().bind(convertTask.progressProperty());

        convertTask.setOnSucceeded(e -> {
            importProgress.progressProperty().unbind();
            importProgress.setProgress(1.0);
            setActionsDisabled(false);
            logView.log("INFO", "All selected symbols converted and saved.");
        });
        convertTask.setOnFailed(e -> {
            importProgress.progressProperty().unbind();
            importProgress.setProgress(0);
            setActionsDisabled(false);
            Throwable ex = convertTask.getException();
            String msg = ex != null ? ex.getMessage() : "Unknown error";
            logView.log("ERROR", "Conversion error: " + msg);
            showAlert(Alert.AlertType.ERROR, "Conversion Fehler", "Beim Konvertieren ist ein Fehler aufgetreten:\n" + msg);
        });

        Thread t = new Thread(convertTask, "dukascopy-convert");
        t.setDaemon(true);
        t.start();
    }

    private void importToMt5() {
        List<DataRow> rows = new ArrayList<>(dataTable.getSelectionModel().getSelectedItems());
        if (rows.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select rows to import, or convert data to CSV first.");
            return;
        }

        setActionsDisabled(true);
        importProgress.progressProperty().unbind();
        importProgress.setProgress(-1); // Indeterminate

        Task<Void> importTask = new Task<>() {
            @Override
            protected Void call() {
                Mt5DataImporter importer = new Mt5DataImporter();
                importer.setLogCallback(msg -> logView.log("INFO", msg));

                int total = rows.size();
                for (int i = 0; i < total; i++) {
                    DataRow row = rows.get(i);
                    if (total > 0) updateProgress(i, total);
                    String symbol = row.getSymbol();
                    String csvPath = row.getCsvFile();

                    if (csvPath == null || csvPath.equals("—")) {
                        logView.log("WARN", symbol + ": No CSV file, convert first!");
                        continue;
                    }

                    Path csvFile = Paths.get(csvPath);
                    if (!Files.exists(csvFile)) {
                        logView.log("WARN", symbol + ": CSV file not found: " + csvPath);
                        continue;
                    }

                    String customName = CustomSymbolManager.toCustomName(symbol);
                    int digits = CsvConverter.getDigits(symbol);

                    logView.log("INFO", "Importing " + symbol + " as " + customName + "...");

                    boolean success = importer.importToMt5(csvFile, customName, symbol, digits);

                    if (success) {
                        symbolManager.registerSymbol(customName, symbol,
                                LocalDate.now().minusYears(1), LocalDate.now(), digits, 0);

                        DataRow r = row;
                        Platform.runLater(() -> r.statusProperty().set("Imported to MT5"));
                    }
                }
                return null;
            }
        };

        importProgress.progressProperty().bind(importTask.progressProperty());

        importTask.setOnSucceeded(e -> {
            importProgress.progressProperty().unbind();
            importProgress.setProgress(1.0);
            setActionsDisabled(false);
            logView.log("INFO", "MT5 import completed!");
            logView.log("INFO", "Please compile DukaImporter.mq5 in MetaEditor and run it on a chart.");
        });
        importTask.setOnFailed(e -> {
            importProgress.progressProperty().unbind();
            importProgress.setProgress(0);
            setActionsDisabled(false);
            Throwable ex = importTask.getException();
            String msg = ex != null ? ex.getMessage() : "Unknown error";
            logView.log("ERROR", "Import error: " + msg);
            showAlert(Alert.AlertType.ERROR, "Import Fehler", "Beim Import in MT5 ist ein Fehler aufgetreten:\n" + msg);
        });

        Thread t = new Thread(importTask, "dukascopy-import");
        t.setDaemon(true);
        t.start();
    }

    private void exportSelectedToCsv() {
        List<DataRow> rows = new ArrayList<>(dataTable.getSelectionModel().getSelectedItems());
        if (rows.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select at least one row from the table.");
            return;
        }

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Export Directory for CSV Files");
        javafx.stage.Window owner = root.getScene() != null ? root.getScene().getWindow() : null;
        java.io.File dir = chooser.showDialog(owner);
        if (dir == null) return; // user cancelled
        Path targetDir = dir.toPath();

        setActionsDisabled(true);
        importProgress.progressProperty().unbind();
        importProgress.setProgress(-1); // Indeterminate

        Task<Integer> exportTask = new Task<>() {
            @Override
            protected Integer call() {
                int exportedCount = 0;
                int total = rows.size();
                for (int i = 0; i < total; i++) {
                    if (total > 0) updateProgress(i, total);
                    DataRow row = rows.get(i);
                    String symbol = row.getSymbol();
                    String csvPathStr = row.getCsvFile();
                    if (csvPathStr != null && !csvPathStr.equals("—")) {
                        try {
                            Path sourcePath = Paths.get(csvPathStr);
                            Path targetPath = targetDir.resolve(sourcePath.getFileName());
                            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                            logView.log("INFO", "Exported CSV to: " + targetPath);
                            exportedCount++;
                        } catch (Exception e) {
                            logView.log("ERROR", "Failed to export " + symbol + ": " + e.getMessage());
                        }
                    } else {
                        logView.log("WARN", "No CSV available for " + symbol + ". Please Convert it first.");
                    }
                }
                return exportedCount;
            }
        };

        importProgress.progressProperty().bind(exportTask.progressProperty());

        exportTask.setOnSucceeded(e -> {
            importProgress.progressProperty().unbind();
            importProgress.setProgress(0);
            setActionsDisabled(false);
            int exportedCount = exportTask.getValue();
            if (exportedCount > 0) {
                showAlert(Alert.AlertType.INFORMATION, "Export Complete",
                        exportedCount + " CSV file(s) successfully exported.");
            }
        });
        exportTask.setOnFailed(e -> {
            importProgress.progressProperty().unbind();
            importProgress.setProgress(0);
            setActionsDisabled(false);
            Throwable ex = exportTask.getException();
            String msg = ex != null ? ex.getMessage() : "Unknown error";
            logView.log("ERROR", "Export error: " + msg);
            showAlert(Alert.AlertType.ERROR, "Export Fehler", "Beim Export der CSV-Dateien ist ein Fehler aufgetreten:\n" + msg);
        });

        Thread t = new Thread(exportTask, "dukascopy-export");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** All selected table rows; if nothing is selected, all rows (legacy Convert behavior). */
    private List<DataRow> getSelectedRowsOrAll() {
        List<DataRow> selected = new ArrayList<>(dataTable.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) {
            selected = new ArrayList<>(dataTable.getItems());
        }
        return selected;
    }

    /** Disable/enable all pipeline action buttons while a background task runs. */
    private void setActionsDisabled(boolean disabled) {
        downloadBtn.setDisable(disabled);
        scanBtn.setDisable(disabled);
        convertBtn.setDisable(disabled);
        importBtn.setDisable(disabled);
        exportBtn.setDisable(disabled);
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.show();
    }

    public BorderPane getView() {
        return root;
    }

    /** Row model for the downloaded-data table. */
    private static class DataRow {
        private final SimpleStringProperty symbol;
        private final SimpleStringProperty range;
        private final SimpleStringProperty status;
        private final SimpleStringProperty bars;
        private final SimpleStringProperty csvFile;

        DataRow(String symbol, String range, String status, String bars, String csvFile) {
            this.symbol = new SimpleStringProperty(symbol);
            this.range = new SimpleStringProperty(range);
            this.status = new SimpleStringProperty(status);
            this.bars = new SimpleStringProperty(bars);
            this.csvFile = new SimpleStringProperty(csvFile);
        }

        SimpleStringProperty symbolProperty() {
            return symbol;
        }

        SimpleStringProperty rangeProperty() {
            return range;
        }

        SimpleStringProperty statusProperty() {
            return status;
        }

        SimpleStringProperty barsProperty() {
            return bars;
        }

        SimpleStringProperty csvFileProperty() {
            return csvFile;
        }

        String getSymbol() {
            return symbol.get();
        }

        String getCsvFile() {
            return csvFile.get();
        }
    }
}
