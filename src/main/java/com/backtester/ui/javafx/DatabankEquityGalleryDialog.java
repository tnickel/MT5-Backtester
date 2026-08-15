package com.backtester.ui.javafx;

import com.backtester.database.CustomProjectSaveCoordinator;
import com.backtester.database.DatabaseManager;
import com.backtester.report.DatabankHtmlViewerGenerator;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import com.backtester.workflow.ClusterFlowTreeModel;
import com.backtester.workflow.ClusterIdentity;
import com.backtester.workflow.CustomProject;
import com.backtester.workflow.DatabankManager;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Fast interactive gallery dialog for displaying original MT5 backtest screenshot graphics
 * and full performance metrics for all strategies in a selected databank (e.g. 'ticktest', 'langzeit', 'data1').
 */
public class DatabankEquityGalleryDialog {
    private static final Logger log = LoggerFactory.getLogger(DatabankEquityGalleryDialog.class);

    private final Stage stage;
    private final DatabankManager databankManager;
    private final CustomProject project;

    private ComboBox<String> dbSelectionCombo;
    private TextField searchField;
    private ComboBox<String> sortCombo;
    private CheckBox showLineChartCheckBox;
    private Label statsSummaryLabel;
    private Label selectionCountLabel;
    private Button deleteSelectedBtn;
    private VBox galleryContainer;

    // Fast image index cache: passNumber / strategyName -> MT5 screenshot directory/image
    private Map<String, Path> imageIndex = Collections.emptyMap();
    private boolean isScanningImages = false;

    /** Selected strategies for the currently shown databank (identity → pass). */
    private final LinkedHashMap<String, CombinedPass> selectedPasses = new LinkedHashMap<>();
    private final Runnable onDatabankChanged;
    private final CustomProjectSaveCoordinator saveCoordinator;
    /** Normalized B1…Bn filter; null means show the whole databank. */
    private final String clusterIdFilter;

    public static void show(Window owner, DatabankManager databankManager, String initialDbName, CustomProject project) {
        show(owner, databankManager, initialDbName, project, null);
    }

    public static void show(Window owner, DatabankManager databankManager, String initialDbName,
                            CustomProject project, Runnable onDatabankChanged) {
        show(owner, databankManager, initialDbName, project, onDatabankChanged, null);
    }

    /**
     * Opens the gallery for {@code initialDbName} without mutating the live databank.
     * When {@code clusterId} is a valid B-id, only matching passes are shown.
     */
    public static void show(Window owner, DatabankManager databankManager, String initialDbName,
                            CustomProject project, Runnable onDatabankChanged, String clusterId) {
        DatabankEquityGalleryDialog dialog = new DatabankEquityGalleryDialog(
                owner, databankManager, initialDbName, project, onDatabankChanged, clusterId);
        dialog.stage.show();
    }

    public DatabankEquityGalleryDialog(Window owner, DatabankManager databankManager, String initialDbName, CustomProject project) {
        this(owner, databankManager, initialDbName, project, null);
    }

    public DatabankEquityGalleryDialog(Window owner, DatabankManager databankManager, String initialDbName,
                                       CustomProject project, Runnable onDatabankChanged) {
        this(owner, databankManager, initialDbName, project, onDatabankChanged, null);
    }

    public DatabankEquityGalleryDialog(Window owner, DatabankManager databankManager, String initialDbName,
                                       CustomProject project, Runnable onDatabankChanged, String clusterId) {
        this.databankManager = databankManager;
        this.project = project;
        this.onDatabankChanged = onDatabankChanged;
        this.clusterIdFilter = ClusterIdentity.normalize(clusterId);
        this.saveCoordinator = new CustomProjectSaveCoordinator(
                DatabaseManager.getInstance(), 0,
                message -> log.warn("Gallery project save: {}", message));
        this.stage = new Stage();

        stage.setTitle(clusterIdFilter != null
                ? "📸 Equity-Galerie · " + clusterIdFilter
                : "📸 MetaTrader Backtest-Grafiken Übersicht");
        stage.initModality(Modality.NONE);
        if (owner != null) stage.initOwner(owner);
        stage.setOnHidden(e -> {
            try {
                saveCoordinator.close();
            } catch (Exception ex) {
                log.debug("Gallery save coordinator close", ex);
            }
        });

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: #0b0d13;");
        root.setPrefSize(1320, 860);

        // Header Control Bar
        VBox headerBox = createHeaderBox(initialDbName);
        root.setTop(headerBox);

        // Center Content ScrollPane
        galleryContainer = new VBox(20);
        galleryContainer.setPadding(new Insets(15, 10, 15, 10));
        galleryContainer.setStyle("-fx-background-color: #0b0d13;");

        ScrollPane scrollPane = new ScrollPane(galleryContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: #0b0d13; -fx-border-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        root.setCenter(scrollPane);

        // Bottom Footer Bar
        HBox bottomBar = new HBox(15);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setPadding(new Insets(12, 0, 0, 0));

        deleteSelectedBtn = new Button("🗑 Delete selected strategies");
        deleteSelectedBtn.setStyle("-fx-background-color: #3a1f1f; -fx-text-fill: #ffab40; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 8 16; -fx-background-radius: 4; -fx-border-color: #ffab40;");
        deleteSelectedBtn.setDisable(true);
        deleteSelectedBtn.setOnAction(e -> deleteSelectedStrategies());

        selectionCountLabel = new Label("0 ausgewählt");
        selectionCountLabel.setTextFill(Color.web("#94a3b8"));
        selectionCountLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));

        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);

        Button openHtmlBtn = new Button("🌐 HTML Report im Browser öffnen");
        openHtmlBtn.setStyle("-fx-background-color: #00e5ff; -fx-text-fill: #0b0d13; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 8 16; -fx-background-radius: 4;");
        openHtmlBtn.setOnAction(e -> openHtmlViewerInBrowser());

        Button closeBtn = new Button("Schließen");
        closeBtn.getStyleClass().add("button-cancel");
        closeBtn.setStyle("-fx-padding: 8 16; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> stage.close());

        bottomBar.getChildren().addAll(deleteSelectedBtn, selectionCountLabel, footerSpacer, openHtmlBtn, closeBtn);
        root.setBottom(bottomBar);

        Scene scene = new Scene(root);
        if (owner != null && owner.getScene() != null && !owner.getScene().getStylesheets().isEmpty()) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        stage.setScene(scene);

        // Fast background scan of MT5 report images on launch
        startIndexScanAndRender();
    }

    private VBox createHeaderBox(String initialDbName) {
        VBox headerBox = new VBox(12);
        headerBox.setPadding(new Insets(0, 0, 10, 0));

        HBox topRow = new HBox(15);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("📸 MetaTrader Backtest-Grafiken & Performance");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        titleLabel.setTextFill(Color.web("#00e5ff"));

        Region flexSpacer = new Region();
        HBox.setHgrow(flexSpacer, Priority.ALWAYS);

        Label dbLabel = new Label("Databank:");
        dbLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        dbLabel.setTextFill(Color.web("#ffd740"));

        List<String> dbNames = databankManager != null ? new ArrayList<>(databankManager.getDatabankNames()) : Collections.emptyList();
        dbSelectionCombo = new ComboBox<>(FXCollections.observableArrayList(dbNames));
        if (initialDbName != null && dbNames.contains(initialDbName)) {
            dbSelectionCombo.setValue(initialDbName);
        } else if (!dbNames.isEmpty()) {
            dbSelectionCombo.setValue(dbNames.get(0));
        }
        dbSelectionCombo.setStyle("-fx-background-color: #1a202c; -fx-text-fill: white; -fx-font-weight: bold;");
        dbSelectionCombo.setOnAction(e -> {
            selectedPasses.clear();
            updateSelectionUi();
            refreshGallery();
        });

        topRow.getChildren().addAll(titleLabel, flexSpacer, dbLabel, dbSelectionCombo);

        HBox filterRow = new HBox(15);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        searchField = new TextField();
        searchField.setPromptText("🔍 Strategie suchen (z.B. Strat 11180)...");
        searchField.setPrefWidth(280);
        searchField.setStyle("-fx-background-color: #141822; -fx-text-fill: #e6e9f0; -fx-border-color: #232a3b; -fx-border-radius: 4; -fx-padding: 6;");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> refreshGallery());

        Label sortLabel = new Label("Sortierung:");
        sortLabel.setTextFill(Color.web("#94a3b8"));

        sortCombo = new ComboBox<>(FXCollections.observableArrayList(
                "Score ↓",
                "LT Profit ↓",
                "BT Profit ↓",
                "LT Profit Factor ↓",
                "LT Drawdown % ↑",
                "Strategie Name"
        ));
        sortCombo.setValue("Score ↓");
        sortCombo.setStyle("-fx-background-color: #141822; -fx-text-fill: #e6e9f0; -fx-border-color: #232a3b;");
        sortCombo.setOnAction(e -> refreshGallery());

        showLineChartCheckBox = new CheckBox("📈 Synthetische JavaFX-Kurven anzeigen");
        showLineChartCheckBox.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px; -fx-cursor: hand;");
        showLineChartCheckBox.setSelected(false);
        showLineChartCheckBox.setOnAction(e -> refreshGallery());

        statsSummaryLabel = new Label("Indiziere Bilder...");
        statsSummaryLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        statsSummaryLabel.setTextFill(Color.web("#10b981"));

        filterRow.getChildren().addAll(searchField, sortLabel, sortCombo, showLineChartCheckBox, statsSummaryLabel);

        headerBox.getChildren().addAll(topRow, filterRow, new Separator());
        return headerBox;
    }

    private void startIndexScanAndRender() {
        isScanningImages = true;
        statsSummaryLabel.setText("Scanning MT5 Image Directory...");

        Thread scanThread = new Thread(() -> {
            Path reportsDir = Paths.get("backtest_reports");
            Map<String, Path> index = scanImageIndex(reportsDir);

            Platform.runLater(() -> {
                this.imageIndex = index;
                this.isScanningImages = false;
                log.info("MT5 Image index completed: {} image entries indexed.", index.size());
                refreshGallery();
            });
        });
        scanThread.setDaemon(true);
        scanThread.start();
    }

    private static Map<String, Path> scanImageIndex(Path reportsDir) {
        Map<String, Path> index = new HashMap<>();
        List<Path> searchDirs = new ArrayList<>();
        if (reportsDir != null && Files.isDirectory(reportsDir)) {
            searchDirs.add(reportsDir);
        }

        try {
            String mt5Exe = com.backtester.config.AppConfig.getInstance().getMt5TerminalPath();
            if (mt5Exe != null && !mt5Exe.isBlank()) {
                Path mt5Folder = Paths.get(mt5Exe).getParent();
                if (mt5Folder != null && Files.isDirectory(mt5Folder)) {
                    searchDirs.add(mt5Folder);
                }
            }
        } catch (Exception ignored) {}

        for (Path rootDir : searchDirs) {
            try (Stream<Path> stream = Files.walk(rootDir, 4)) {
                stream.filter(Files::isRegularFile)
                      .filter(p -> p.getFileName().toString().equalsIgnoreCase("BacktestReport.png")
                                || p.getFileName().toString().equalsIgnoreCase("Report.png"))
                      .forEach(p -> {
                          Path dir = p.getParent();
                          if (dir == null) return;

                          int foundPass = -1;

                          // 1. Check tester.ini if present
                          Path ini = dir.resolve("tester.ini");
                          if (Files.isRegularFile(ini)) {
                              try {
                                  List<String> lines = Files.readAllLines(ini, StandardCharsets.UTF_8);
                                  for (String line : lines) {
                                      if (line.toLowerCase(Locale.ROOT).startsWith("expertparameters=")) {
                                          int pass = extractPassNumberFromText(line);
                                          if (pass >= 0) {
                                              foundPass = pass;
                                              break;
                                          }
                                      }
                                  }
                              } catch (Exception ignored) {}
                          }

                          // 2. Check parent directory name if tester.ini didn't yield a pass
                          String dirName = dir.getFileName().toString();
                          if (foundPass < 0) {
                              foundPass = extractPassNumberFromText(dirName);
                          }

                          if (foundPass >= 0) {
                              String key = "pass_" + foundPass;
                              Path existing = index.get(key);
                              if (existing == null || lastModifiedMillis(p) > lastModifiedMillis(existing)) {
                                  index.put(key, p);
                              }
                          }

                          String lowerDir = dirName.toLowerCase(Locale.ROOT);
                          Path existingDir = index.get(lowerDir);
                          if (existingDir == null || lastModifiedMillis(p) > lastModifiedMillis(existingDir)) {
                              index.put(lowerDir, p);
                          }
                      });
            } catch (Exception ex) {
                log.warn("Error scanning MT5 report image directory {}", rootDir, ex);
            }
        }
        return index;
    }

    private static long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception e) {
            return 0L;
        }
    }

    private static int extractPassNumberFromText(String text) {
        if (text == null) return -1;
        // Strictly match pass_11180, pass#11180, pass-11180, pass 11180, pass11180
        Matcher m = Pattern.compile("pass[_#\\s-]*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(text);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) {}
        }
        return -1;
    }

    private void refreshGallery() {
        galleryContainer.getChildren().clear();

        String selectedDb = dbSelectionCombo.getValue();
        if (selectedDb == null || databankManager == null) {
            statsSummaryLabel.setText("Keine Databank gewählt");
            return;
        }

        List<CombinedPass> passes = visiblePasses(selectedDb);
        if (passes == null || passes.isEmpty()) {
            String emptyMsg = clusterIdFilter != null
                    ? "ℹ️ Keine lebenden Strategien in " + clusterIdFilter
                    + " (Databank '" + selectedDb + "')."
                    : "ℹ️ Databank '" + selectedDb + "' enthält keine Strategien.";
            Label emptyLbl = new Label(emptyMsg);
            emptyLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
            emptyLbl.setTextFill(Color.web("#ffab40"));
            galleryContainer.getChildren().add(emptyLbl);
            statsSummaryLabel.setText(clusterIdFilter != null
                    ? "0 Strategien in " + clusterIdFilter + " / '" + selectedDb + "'"
                    : "0 Strategien in '" + selectedDb + "'");
            return;
        }

        // Search Filter
        String query = searchField.getText() != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        List<CombinedPass> filtered = passes.stream()
                .filter(p -> {
                    if (query.isEmpty()) return true;
                    String name = p.getStrategyName().toLowerCase(Locale.ROOT);
                    String passStr = String.valueOf(p.getPassNumber());
                    return name.contains(query) || passStr.contains(query);
                })
                .collect(Collectors.toList());

        // Sort
        String sortMode = sortCombo.getValue();
        if (sortMode != null) {
            switch (sortMode) {
                case "Score ↓" -> filtered.sort(Comparator.comparingDouble(CombinedPass::getScore).reversed());
                case "LT Profit ↓" -> filtered.sort(Comparator.comparingDouble((CombinedPass p) -> p.getLongtermPass() != null ? p.getLongtermPass().getProfit() : p.getBtProfit()).reversed());
                case "BT Profit ↓" -> filtered.sort(Comparator.comparingDouble(CombinedPass::getBtProfit).reversed());
                case "LT Profit Factor ↓" -> filtered.sort(Comparator.comparingDouble((CombinedPass p) -> p.getLongtermPass() != null ? p.getLongtermPass().getProfitFactor() : p.getBtPf()).reversed());
                case "LT Drawdown % ↑" -> filtered.sort(Comparator.comparingDouble((CombinedPass p) -> p.getLongtermPass() != null ? p.getLongtermPass().getDrawdownPercent() : p.getBtDd()));
                case "Strategie Name" -> filtered.sort(Comparator.comparing(CombinedPass::getStrategyName));
            }
        }

        double totalLtProfit = filtered.stream().mapToDouble(p -> p.getLongtermPass() != null ? p.getLongtermPass().getProfit() : p.getBtProfit()).sum();
        String dbLabel = clusterIdFilter != null ? selectedDb + " · " + clusterIdFilter : selectedDb;
        statsSummaryLabel.setText(String.format(Locale.US, "%d Strategien in '%s' | Gesamt Profit: $%.2f", filtered.size(), dbLabel, totalLtProfit));

        boolean showLineCharts = showLineChartCheckBox.isSelected();

        for (CombinedPass pass : filtered) {
            galleryContainer.getChildren().add(createStrategyCard(pass, selectedDb, showLineCharts));
        }
    }

    private VBox createStrategyCard(CombinedPass pass, String dbName, boolean showLineCharts) {
        VBox card = new VBox(12);
        card.setPadding(new Insets(16));
        card.setStyle("-fx-background-color: #141822; -fx-border-color: #232a3b; -fx-border-radius: 8; -fx-background-radius: 8;");

        // Top Header Row
        HBox cardHeader = new HBox(12);
        cardHeader.setAlignment(Pos.CENTER_LEFT);

        String identity = DatabankManager.passIdentity(pass);
        CheckBox selectBox = new CheckBox();
        selectBox.setSelected(selectedPasses.containsKey(identity));
        selectBox.setStyle("-fx-cursor: hand;");
        selectBox.setTooltip(new Tooltip("Strategie zum Löschen markieren"));
        selectBox.setOnAction(e -> {
            if (selectBox.isSelected()) {
                selectedPasses.put(identity, pass);
            } else {
                selectedPasses.remove(identity);
            }
            updateSelectionUi();
        });

        Label nameLbl = new Label(pass.getStrategyName());
        nameLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17));
        nameLbl.setTextFill(Color.web("#00e5ff"));

        Label passBadge = new Label("Pass #" + pass.getPassNumber());
        passBadge.setStyle("-fx-background-color: #1e2432; -fx-text-fill: #ffd740; -fx-font-weight: bold; -fx-padding: 4 10 4 10; -fx-background-radius: 4;");

        Label scoreBadge = new Label(String.format(Locale.US, "Score: %.1f", pass.getScore()));
        scoreBadge.setStyle("-fx-background-color: #1e2432; -fx-text-fill: #10b981; -fx-font-weight: bold; -fx-padding: 4 10 4 10; -fx-background-radius: 4;");

        double ltProfit = pass.getLongtermPass() != null ? pass.getLongtermPass().getProfit() : pass.getBtProfit();
        Label profitBadge = new Label(String.format(Locale.US, "Profit: $%.2f", ltProfit));
        profitBadge.setStyle("-fx-background-color: #1e2432; -fx-text-fill: " + (ltProfit >= 0 ? "#10b981" : "#ff5252") + "; -fx-font-weight: bold; -fx-padding: 4 10 4 10; -fx-background-radius: 4;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button deleteOneBtn = new Button("🗑 Delete strategy");
        deleteOneBtn.setStyle("-fx-background-color: #3a1f1f; -fx-text-fill: #ff8a80; -fx-border-color: #ff5252; -fx-border-radius: 4; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 6 12;");
        deleteOneBtn.setOnAction(e -> deleteStrategies(List.of(pass)));

        Button viewSetfileBtn = new Button("👁 Setfile anzeigen");
        viewSetfileBtn.setStyle("-fx-background-color: #1e293b; -fx-text-fill: #00e5ff; -fx-border-color: #00e5ff; -fx-border-radius: 4; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 6 12;");
        viewSetfileBtn.setOnAction(e -> SetfileDialogHelper.showSetfileViewDialog(pass, project, stage));

        Button downloadSetfileBtn = new Button("💾 Setfile download");
        downloadSetfileBtn.setStyle("-fx-background-color: #1e293b; -fx-text-fill: #10b981; -fx-border-color: #10b981; -fx-border-radius: 4; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 6 12;");
        downloadSetfileBtn.setOnAction(e -> SetfileDialogHelper.downloadSetfile(pass, project, stage));

        Button detailsBtn = new Button("▶ Details & MT5 Einzel-Backtest");
        detailsBtn.setStyle("-fx-background-color: #00bcd4; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 6 12; -fx-background-radius: 4;");
        detailsBtn.setOnAction(e -> StrategyDetailsModalDialog.show(pass, dbName, project, stage, 0));

        cardHeader.getChildren().addAll(selectBox, nameLbl, passBadge, scoreBadge, profitBadge, spacer,
                deleteOneBtn, viewSetfileBtn, downloadSetfileBtn, detailsBtn);

        // Expanded Metrics Grid (All available statistics)
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(5);

        grid.add(createHeaderLabel("Phase"), 0, 0);
        grid.add(createHeaderLabel("Net Profit"), 1, 0);
        grid.add(createHeaderLabel("Trades"), 2, 0);
        grid.add(createHeaderLabel("Profit Factor"), 3, 0);
        grid.add(createHeaderLabel("Max Drawdown"), 4, 0);
        grid.add(createHeaderLabel("Recovery Factor"), 5, 0);
        grid.add(createHeaderLabel("Sharpe Ratio"), 6, 0);
        grid.add(createHeaderLabel("Payoff"), 7, 0);
        grid.add(createHeaderLabel("Test Period"), 8, 0);

        // Backtest IS
        Pass bt = pass.getBacktestPass();
        if (bt != null) {
            grid.add(new Label("Backtest (IS):"), 0, 1);
            grid.add(createValueLabel(bt.getProfit(), "$%.2f"), 1, 1);
            grid.add(new Label(String.valueOf(bt.getTotalTrades())), 2, 1);
            grid.add(createValueLabel(bt.getProfitFactor(), "%.2f"), 3, 1);
            grid.add(createValueLabel(bt.getDrawdownPercent(), "%.2f%%"), 4, 1);
            grid.add(createValueLabel(bt.getRecoveryFactor(), "%.2f"), 5, 1);
            grid.add(createValueLabel(bt.getSharpeRatio(), "%.2f"), 6, 1);
            grid.add(createValueLabel(bt.getExpectedPayoff(), "$%.2f"), 7, 1);
            grid.add(new Label(pass.getBtDateRange()), 8, 1);
        }

        // Forward OOS
        if (pass.getForwardPass() != null) {
            Pass fw = pass.getForwardPass();
            grid.add(new Label("Forward (OOS):"), 0, 2);
            grid.add(createValueLabel(fw.getProfit(), "$%.2f"), 1, 2);
            grid.add(new Label(String.valueOf(fw.getTotalTrades())), 2, 2);
            grid.add(createValueLabel(fw.getProfitFactor(), "%.2f"), 3, 2);
            grid.add(createValueLabel(fw.getDrawdownPercent(), "%.2f%%"), 4, 2);
            grid.add(createValueLabel(fw.getRecoveryFactor(), "%.2f"), 5, 2);
            grid.add(createValueLabel(fw.getSharpeRatio(), "%.2f"), 6, 2);
            grid.add(createValueLabel(fw.getExpectedPayoff(), "$%.2f"), 7, 2);
            grid.add(new Label(pass.getFwDateRange()), 8, 2);
        }

        // Longterm LT
        if (pass.getLongtermPass() != null) {
            Pass lt = pass.getLongtermPass();
            grid.add(new Label("Longterm (LT):"), 0, 3);
            grid.add(createValueLabel(lt.getProfit(), "$%.2f"), 1, 3);
            grid.add(new Label(String.valueOf(lt.getTotalTrades())), 2, 3);
            grid.add(createValueLabel(lt.getProfitFactor(), "%.2f"), 3, 3);
            grid.add(createValueLabel(lt.getDrawdownPercent(), "%.2f%%"), 4, 3);
            grid.add(createValueLabel(lt.getRecoveryFactor(), "%.2f"), 5, 3);
            grid.add(createValueLabel(lt.getSharpeRatio(), "%.2f"), 6, 3);
            grid.add(createValueLabel(lt.getExpectedPayoff(), "$%.2f"), 7, 3);
            grid.add(new Label(pass.getLtDateRange()), 8, 3);
        }

        // Image Section: Look up MT5 Backtest Graphic from fast index
        Path screenshotPath = lookupScreenshotImage(pass);

        VBox graphicBox = new VBox(8);
        graphicBox.setPadding(new Insets(10, 0, 0, 0));

        if (screenshotPath != null && Files.exists(screenshotPath)) {
            Label imgHeader = new Label("📸 MetaTrader 5 Original-Backtest Grafik:");
            imgHeader.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
            imgHeader.setTextFill(Color.web("#ffd740"));

            try {
                Image img = new Image(screenshotPath.toUri().toString(), 960, 0, true, true);
                ImageView imgView = new ImageView(img);
                imgView.setPreserveRatio(true);
                imgView.setFitWidth(960);
                imgView.setStyle("-fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.7), 8, 0, 0, 0);");

                Tooltip.install(imgView, new Tooltip("Klick auf das Bild zum Vergrößern (Full-HD Lightbox)"));
                imgView.setOnMouseClicked(e -> showEnlargedImage(screenshotPath, pass.getStrategyName() + " (Pass #" + pass.getPassNumber() + ")"));

                // Thumbnail row for extra MT5 graphics (hst, mfemae, holding) if present in same folder
                HBox thumbRow = createExtraThumbnailsRow(screenshotPath.getParent());

                graphicBox.getChildren().addAll(imgHeader, imgView);
                if (!thumbRow.getChildren().isEmpty()) {
                    graphicBox.getChildren().add(thumbRow);
                }
            } catch (Exception ex) {
                log.warn("Failed to load MT5 screenshot image at " + screenshotPath, ex);
            }
        } else {
            HBox missingBox = new HBox(12);
            missingBox.setAlignment(Pos.CENTER_LEFT);
            missingBox.setStyle("-fx-background-color: #1a202c; -fx-border-color: #2d3748; -fx-padding: 10 14; -fx-background-radius: 6;");

            Label missingIcon = new Label("📸");
            missingIcon.setFont(Font.font(20));

            Label missingMsg = new Label("MetaTrader Einzeltest-Grafik noch nicht vorhanden (Pass #" + pass.getPassNumber() + ").\n" +
                    "Klicke rechts auf '▶ Details & MT5 Einzel-Backtest', um den Test im MetaTrader auszuführen & die Grafik zu speichern.");
            missingMsg.setTextFill(Color.web("#94a3b8"));

            missingBox.getChildren().addAll(missingIcon, missingMsg);
            graphicBox.getChildren().add(missingBox);
        }

        // Optional JavaFX LineChart below if explicitly checked
        if (showLineCharts) {
            LineChart<Number, Number> chart = createEquityLineChart(pass);
            chart.setPrefHeight(240);
            graphicBox.getChildren().add(chart);
        }

        card.getChildren().addAll(cardHeader, grid, graphicBox);
        return card;
    }

    /**
     * Live databank is never replaced; cluster filter is a view copy.
     */
    private List<CombinedPass> visiblePasses(String selectedDb) {
        List<CombinedPass> passes = databankManager.getDatabank(selectedDb);
        if (clusterIdFilter == null) {
            return passes;
        }
        return ClusterFlowTreeModel.filterByCluster(passes, clusterIdFilter);
    }

    private Path lookupScreenshotImage(CombinedPass pass) {
        if (pass != null) {
            String reportDirStr = pass.getReportDirectory();
            if (reportDirStr != null && !reportDirStr.isBlank()) {
                Path dir = Paths.get(reportDirStr);
                if (Files.isDirectory(dir)) {
                    Path candidate1 = dir.resolve("BacktestReport.png");
                    if (Files.isRegularFile(candidate1)) return candidate1;
                    Path candidate2 = dir.resolve("Report.png");
                    if (Files.isRegularFile(candidate2)) return candidate2;
                }
            }
        }

        // Strict mode: Never guess by pass number to prevent displaying wrong/stale graphics from old unrelated runs
        return null;
    }

    private HBox createExtraThumbnailsRow(Path dir) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4, 0, 0, 0));

        if (dir == null || !Files.isDirectory(dir)) return row;

        String[] extraFiles = {"BacktestReport-hst.png", "BacktestReport-mfemae.png", "BacktestReport-holding.png"};
        String[] labels = {"Histogramm", "MAE / MFE", "Holding Time"};

        for (int i = 0; i < extraFiles.length; i++) {
            final String label = labels[i];
            Path extraPath = dir.resolve(extraFiles[i]);
            if (Files.isRegularFile(extraPath)) {
                try {
                    Image thumb = new Image(extraPath.toUri().toString(), 180, 0, true, true);
                    ImageView thumbView = new ImageView(thumb);
                    thumbView.setPreserveRatio(true);
                    thumbView.setFitWidth(180);
                    thumbView.setStyle("-fx-cursor: hand; -fx-border-color: #232a3b;");
                    thumbView.setOnMouseClicked(e -> showEnlargedImage(extraPath, label));

                    VBox thumbBox = new VBox(3, new Label(label), thumbView);
                    thumbBox.getChildren().get(0).setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 10px;");
                    row.getChildren().add(thumbBox);
                } catch (Exception ignored) {}
            }
        }
        return row;
    }

    private LineChart<Number, Number> createEquityLineChart(CombinedPass pass) {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Trades / Fortschritt");
        xAxis.setTickLabelFill(Color.web("#7e889a"));

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Equity ($)");
        yAxis.setTickLabelFill(Color.web("#7e889a"));

        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setCreateSymbols(false);
        chart.setLegendVisible(true);
        chart.setAnimated(false);

        XYChart.Series<Number, Number> equitySeries = new XYChart.Series<>();

        List<double[]> history = null;
        if (pass.getLongtermPass() != null && pass.getLongtermPass().getEquityHistory() != null && !pass.getLongtermPass().getEquityHistory().isEmpty()) {
            history = pass.getLongtermPass().getEquityHistory();
        } else if (pass.getForwardPass() != null && pass.getForwardPass().getEquityHistory() != null && !pass.getForwardPass().getEquityHistory().isEmpty()) {
            history = pass.getForwardPass().getEquityHistory();
        } else if (pass.getBacktestPass() != null && pass.getBacktestPass().getEquityHistory() != null && !pass.getBacktestPass().getEquityHistory().isEmpty()) {
            history = pass.getBacktestPass().getEquityHistory();
        }

        if (history != null && !history.isEmpty()) {
            chart.setTitle("Synthetischer Trade-Verlauf (" + history.size() + " Einzel-Trades)");
            equitySeries.setName("Reale Equity ($)");
            for (double[] pt : history) {
                if (pt != null && pt.length >= 2) {
                    equitySeries.getData().add(new XYChart.Data<>(pt[0], pt[1]));
                }
            }
        } else {
            chart.setTitle("Synthetische Equity-Vorschau");
            equitySeries.setName("Equity ($)");

            double startBalance = 10000.0;
            int totalTrades = Math.max(1, pass.getBtTrades());
            double netProfit = Double.isNaN(pass.getBtProfit()) ? 0 : pass.getBtProfit();

            equitySeries.getData().add(new XYChart.Data<>(0, startBalance));
            double stepProfit = netProfit / totalTrades;
            double currentEquity = startBalance;
            for (int i = 1; i <= totalTrades; i++) {
                currentEquity += stepProfit;
                equitySeries.getData().add(new XYChart.Data<>(i, currentEquity));
            }
        }

        chart.getData().add(equitySeries);
        return chart;
    }

    private void deleteSelectedStrategies() {
        if (selectedPasses.isEmpty()) return;
        deleteStrategies(new ArrayList<>(selectedPasses.values()));
    }

    private void deleteStrategies(List<CombinedPass> toDelete) {
        if (toDelete == null || toDelete.isEmpty() || databankManager == null) return;
        String selectedDb = dbSelectionCombo.getValue();
        if (selectedDb == null || selectedDb.isBlank()) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                toDelete.size() + " Strategie(n) aus Databank '" + selectedDb + "' löschen?",
                ButtonType.CANCEL, ButtonType.OK);
        confirm.setTitle("Strategien löschen");
        confirm.setHeaderText(null);
        confirm.initOwner(stage);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        databankManager.removePassesFromDatabank(selectedDb, toDelete);
        for (CombinedPass pass : toDelete) {
            selectedPasses.remove(DatabankManager.passIdentity(pass));
        }
        persistProjectAfterDelete();
        updateSelectionUi();
        refreshGallery();
        if (onDatabankChanged != null) {
            onDatabankChanged.run();
        }
        log.info("Deleted {} strateg(y/ies) from databank '{}'", toDelete.size(), selectedDb);
    }

    private void persistProjectAfterDelete() {
        if (project == null || databankManager == null) return;
        try {
            saveCoordinator.requestSave(project, databankManager);
            saveCoordinator.flushAsync();
        } catch (Exception ex) {
            log.error("Failed to persist project after gallery delete", ex);
        }
    }

    private void updateSelectionUi() {
        int count = selectedPasses.size();
        if (selectionCountLabel != null) {
            selectionCountLabel.setText(count + " ausgewählt");
        }
        if (deleteSelectedBtn != null) {
            deleteSelectedBtn.setDisable(count == 0);
            deleteSelectedBtn.setText(count > 0
                    ? "🗑 Delete selected strategies (" + count + ")"
                    : "🗑 Delete selected strategies");
        }
    }

    private void showEnlargedImage(Path imagePath, String title) {
        Stage imgStage = new Stage();
        imgStage.initModality(Modality.APPLICATION_MODAL);
        imgStage.initOwner(stage);
        imgStage.setTitle("📸 MetaTrader Backtest-Grafik (Full-HD): " + title);

        Image img = new Image(imagePath.toUri().toString());
        ImageView imgView = new ImageView(img);
        imgView.setPreserveRatio(true);

        ScrollPane sp = new ScrollPane(imgView);
        sp.setStyle("-fx-background-color: #0b0d13; -fx-background: #0b0d13;");

        Scene imgScene = new Scene(sp, Math.min(img.getWidth() + 40, 1500), Math.min(img.getHeight() + 40, 950));
        imgStage.setScene(imgScene);
        imgStage.show();
    }

    private void openHtmlViewerInBrowser() {
        String selectedDb = dbSelectionCombo.getValue();
        if (selectedDb == null || databankManager == null) return;
        List<CombinedPass> passes = visiblePasses(selectedDb);
        if (passes == null || passes.isEmpty()) {
            String msg = clusterIdFilter != null
                    ? "Keine lebenden Strategien in " + clusterIdFilter + "."
                    : "Databank '" + selectedDb + "' ist leer.";
            Alert alert = new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK);
            alert.initOwner(stage);
            alert.showAndWait();
            return;
        }

        try {
            Path reportsDir = Paths.get("backtest_reports");
            String expert = project != null ? project.getExpert() : null;
            String symbol = project != null ? project.getSymbol() : null;
            String period = project != null ? project.getPeriod() : null;

            String token = com.backtester.server.LocalBacktestHttpServer.getInstance().setContext(project, databankManager, stage);
            Path htmlPath = DatabankHtmlViewerGenerator.generate(selectedDb, null, passes, reportsDir, expert, symbol, period, token);
            if (htmlPath != null && Files.exists(htmlPath)) {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(htmlPath.toUri());
                } else {
                    new ProcessBuilder("cmd", "/c", "start", htmlPath.toAbsolutePath().toString()).start();
                }
            }
        } catch (Exception ex) {
            log.error("Failed to generate or open HTML Databank viewer", ex);
            Alert alert = new Alert(Alert.AlertType.ERROR, "Fehler beim Öffnen des HTML Report: " + ex.getMessage(), ButtonType.OK);
            alert.initOwner(stage);
            alert.showAndWait();
        }
    }

    private Label createHeaderLabel(String text) {
        Label lbl = new Label(text);
        lbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        lbl.setTextFill(Color.web("#94a3b8"));
        return lbl;
    }

    private Label createValueLabel(double value, String format) {
        if (Double.isNaN(value)) return new Label("-");
        Label lbl = new Label(String.format(Locale.US, format, value));
        lbl.setStyle("-fx-font-weight: bold; -fx-text-fill: " + (value >= 0 ? "#e6e9f0" : "#ff5252") + ";");
        return lbl;
    }
}
