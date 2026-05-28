package com.backtester.ui.javafx;

import com.backtester.database.DatabaseManager;
import com.backtester.database.HistoryRun;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.application.Platform;

import java.awt.Desktop;
import java.io.File;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HistoryView {

    private final BorderPane root;
    private final TreeView<RunNodeData> treeView;
    private final TextArea detailsArea;
    private final DatabaseManager dbManager;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final LocalDate today = LocalDate.now();
    private final Label countLabel;
    private Tab tab;
    private final WorkflowView workflowView;

    public HistoryView() {
        this(null);
    }

    public HistoryView(WorkflowView workflowView) {
        this.workflowView = workflowView;
        this.dbManager = DatabaseManager.getInstance();
        
        root = new BorderPane();
        root.setPadding(new Insets(15));

        SplitPane splitPane = new SplitPane();
        splitPane.setStyle("-fx-background-color: transparent; -fx-box-border: transparent;");

        // Left Side: Tree
        VBox leftBox = new VBox(10);
        leftBox.getStyleClass().add("sci-fi-panel");
        
        Label treeTitle = new Label("Saved Runs");
        treeTitle.getStyleClass().add("sci-fi-panel-title");
        countLabel = new Label("Total: 0");
        countLabel.setStyle("-fx-text-fill: #00e5ff; -fx-font-weight: bold; -fx-padding: 2 0 0 0;");
        
        String overview = "Der Database-Tab, auch History genannt, ist das zentrale Gedächtnis und das Archiv deiner gesamten Entwicklungsarbeit in der Antigravity Protocol Suite. Jedes Mal, wenn du einen Single-Backtest, einen Multi-Backtest, eine komplexe Optimierung oder einen intensiven Robustness-Scan ausführst, generiert das System eine gewaltige Menge an Ergebnissen, Parametern und Metriken.\n\n" +
                          "Warum ist diese Historie so essentiell wichtig? Strategie-Entwicklung ist ein iterativer Prozess. Du änderst oft nur eine winzige Einstellung am Expert Advisor, führst einen Test durch, änderst sie wieder und testest erneut. Ohne eine lückenlose Dokumentation verliert man nach 50 Durchläufen komplett den Überblick, welche Parameterkombination zu welchem Ergebnis geführt hat.\n\n" +
                          "Dieser Tab speichert all diese Durchläufe dauerhaft in einer eingebetteten, hochperformanten Datenbank ab. Er fungiert als dein persönliches Trading-Labor-Logbuch, in dem du jederzeit Wochen später nachsehen kannst, welche Einstellungen damals zu jener phänomenalen (oder katastrophalen) Equity-Kurve geführt haben.";
        String details = "Detaillierte Analyse der Funktionen:\n\n" +
                         "1. Die hierarchische Baumansicht (Tree View):\n" +
                         "   Auf der linken Seite werden alle deine vergangenen Experimente strukturiert aufgelistet. Die oberste Ebene bildet der Name des Expert Advisors. Darunter gliedern sich die Durchläufe in logische Kategorien: 'Single Backtests', 'Multi Backtests', 'Optimizations' und 'Robustness Scans'. Das sorgt für maximale Übersichtlichkeit, selbst wenn die Datenbank Tausende von Einträgen fasst.\n\n" +
                         "2. Lauf-Details & Parameter-Snapshots:\n" +
                         "   Wenn du einen beliebigen Lauf anklickst, wird auf der rechten Seite das Dashboard aktualisiert. Das System speichert nicht nur das Endergebnis (Profit, Drawdown), sondern macht auch einen 'Snapshot' der exakten Konfiguration (Deposit, Zeitraum, Tick-Modell) und – besonders wichtig – der EA-Input-Parameter (.set Dateien), die zum Zeitpunkt des Tests verwendet wurden. Du kannst also einen Test, der vor Monaten durchgeführt wurde, mit exakt denselben Parametern reproduzieren.\n\n" +
                         "3. Direkter Zugriff auf HTML-Reports:\n" +
                         "   Der MetaTrader generiert bei jedem Test detaillierte HTML-Berichte und Graphen, die auf der Festplatte abgelegt werden. Ein Doppelklick auf einen Eintrag im Database-Tab öffnet diesen nativen Bericht direkt in deinem Standard-Webbrowser, vorausgesetzt, die Dateien wurden nicht physisch vom Laufwerk gelöscht. Dies ermöglicht tiefgehende Post-Analysen der Trade-Historie.\n\n" +
                         "4. Datenbank-Verwaltung (Cleanup):\n" +
                         "   Da die Reports viel Speicherplatz beanspruchen können, bietet der Tab Werkzeuge zur Datenpflege. Du kannst gezielt veraltete oder fehlerhafte Läufe löschen ('Delete Selected'). Dies entfernt nicht nur den Eintrag aus der Datenbank, sondern räumt auf Wunsch auch die zugehörigen physischen Dateien und Reports von deiner Festplatte auf, um Speicher freizugeben.";
                         
        javafx.scene.layout.Region infoSpacer = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(infoSpacer, javafx.scene.layout.Priority.ALWAYS);
        HBox titleBox = new HBox(15, treeTitle, countLabel, infoSpacer, DocHelper.createInfoButton("Database", overview, details));
        titleBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        TreeItem<RunNodeData> rootItem = new TreeItem<>(new RunNodeData("History", null, false));
        rootItem.setExpanded(true);
        treeView = new TreeView<>(rootItem);
        treeView.setStyle("-fx-background-color: transparent;");
        treeView.setShowRoot(false);
        VBox.setVgrow(treeView, Priority.ALWAYS);
        
        treeView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        treeView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> onNodeSelected(newVal));
        treeView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) onNodeDoubleClicked();
        });
        treeView.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.DELETE) {
                deleteSelectedRuns();
            }
        });

        // Custom TreeCell to highlight today's runs and add right-click restore for Workflows
        treeView.setCellFactory(tv -> new TreeCell<RunNodeData>() {
            @Override
            protected void updateItem(RunNodeData item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    setContextMenu(null);
                } else {
                    setText(item.label);
                    if (item.isToday) {
                        setStyle("-fx-background-color: rgba(0, 229, 255, 0.12); -fx-text-fill: #00e5ff; -fx-font-weight: bold;");
                    } else {
                        setStyle("");
                    }

                    // Attach context menu to Workflow runs
                    if (item.run != null && "Workflow".equals(item.run.getRunType())) {
                        ContextMenu menu = new ContextMenu();
                        MenuItem restoreItem = new MenuItem("🔄 Workflow wiederherstellen (Restore)");
                        restoreItem.setOnAction(evt -> restoreWorkflow(item.run));
                        menu.getItems().add(restoreItem);
                        setContextMenu(menu);
                    } else {
                        setContextMenu(null);
                    }
                }
            }
        });

        HBox btnBox = new HBox(5);
        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("button");
        refreshBtn.setMaxWidth(Double.MAX_VALUE);
        refreshBtn.setOnAction(e -> reloadTree());
        HBox.setHgrow(refreshBtn, Priority.ALWAYS);

        Button delSelBtn = new Button("Delete Selected");
        delSelBtn.getStyleClass().add("button");
        delSelBtn.setMaxWidth(Double.MAX_VALUE);
        delSelBtn.setOnAction(e -> deleteSelectedRuns());
        HBox.setHgrow(delSelBtn, Priority.ALWAYS);

        Button delAllBtn = new Button("Delete All");
        delAllBtn.getStyleClass().addAll("button", "button-cancel");
        delAllBtn.setMaxWidth(Double.MAX_VALUE);
        delAllBtn.setOnAction(e -> deleteAllRuns());
        HBox.setHgrow(delAllBtn, Priority.ALWAYS);

        btnBox.getChildren().addAll(refreshBtn, delSelBtn, delAllBtn);

        leftBox.getChildren().addAll(titleBox, treeView, btnBox);

        // Right Side: Details
        VBox rightBox = new VBox(10);
        rightBox.getStyleClass().add("sci-fi-panel");

        Label detailsTitle = new Label("Run Summary");
        detailsTitle.getStyleClass().add("sci-fi-panel-title");

        detailsArea = new TextArea();
        detailsArea.setEditable(false);
        detailsArea.setText("No run selected.\n\nPlease select a run from the 'Saved Runs' tree on the left to view its details and performance metrics here.");
        detailsArea.setStyle("-fx-font-family: Consolas; -fx-font-size: 14px;");
        VBox.setVgrow(detailsArea, Priority.ALWAYS);

        Label helpText = new Label("Double-click a run on the left to open the full HTML Report in your browser.");
        helpText.setStyle("-fx-text-fill: #646978; -fx-font-size: 12px;");

        rightBox.getChildren().addAll(detailsTitle, detailsArea, helpText);

        splitPane.getItems().addAll(leftBox, rightBox);
        splitPane.setDividerPositions(0.4);

        root.setCenter(splitPane);
        
        Platform.runLater(this::reloadTree);
    }
    
    public void reloadTree() {
        TreeItem<RunNodeData> rootItem = treeView.getRoot();
        rootItem.getChildren().clear();

        List<HistoryRun> runs = dbManager.getAllRuns();
        
        Platform.runLater(() -> {
            countLabel.setText("Total: " + runs.size());
            if (tab != null) {
                tab.setText("Database (" + runs.size() + ")");
            }
        });
        
        // Group by type -> expert
        Map<String, Map<String, List<HistoryRun>>> grouped = runs.stream()
                .collect(Collectors.groupingBy(HistoryRun::getRunType,
                         Collectors.groupingBy(HistoryRun::getExpertName)));

        for (Map.Entry<String, Map<String, List<HistoryRun>>> typeEntry : grouped.entrySet()) {
            TreeItem<RunNodeData> typeNode = new TreeItem<>(new RunNodeData(typeEntry.getKey(), null, false));
            typeNode.setExpanded(true);
            
            for (Map.Entry<String, List<HistoryRun>> expertEntry : typeEntry.getValue().entrySet()) {
                TreeItem<RunNodeData> expertNode = new TreeItem<>(new RunNodeData(expertEntry.getKey(), null, false));
                expertNode.setExpanded(true);
                
                for (HistoryRun run : expertEntry.getValue()) {
                    String label = df.format(new Date(run.getTimestamp()));
                    
                    try {
                        if (run.getResultJson() != null && !run.getResultJson().isEmpty()) {
                            JsonObject json = gson.fromJson(run.getResultJson(), JsonObject.class);
                            String symbol = json.has("symbol") ? json.get("symbol").getAsString() : "";
                            String period = json.has("period") ? json.get("period").getAsString() : "";
                            
                            if (symbol != null && !symbol.trim().isEmpty() && period != null && !period.trim().isEmpty()) {
                                label += " \u2014 " + symbol + " " + period;
                            } else if (symbol != null && !symbol.trim().isEmpty()) {
                                label += " \u2014 " + symbol;
                            }
                        }
                    } catch (Exception ignored) {}

                    // Check if this run is from today
                    LocalDate runDate = Instant.ofEpochMilli(run.getTimestamp())
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate();
                    boolean isToday = runDate.equals(today);

                    TreeItem<RunNodeData> runNode = new TreeItem<>(new RunNodeData(label, run, isToday));
                    expertNode.getChildren().add(runNode);
                }
                typeNode.getChildren().add(expertNode);
            }
            rootItem.getChildren().add(typeNode);
        }
    }
    
    private void onNodeSelected(TreeItem<RunNodeData> node) {
        if (node == null || node.getValue() == null || node.getValue().run == null) {
            detailsArea.setText("");
            return;
        }

        HistoryRun run = node.getValue().run;
        
        StringBuilder sb = new StringBuilder();
        sb.append("Type: ").append(run.getRunType()).append("\n");
        sb.append("Expert: ").append(run.getExpertName()).append("\n");
        sb.append("Date: ").append(df.format(new Date(run.getTimestamp()))).append("\n");
        sb.append("HTML Path: ").append(run.getHtmlPath()).append("\n\n");
        
        try {
            if (run.getResultJson() != null && !run.getResultJson().trim().isEmpty()) {
                JsonObject json = gson.fromJson(run.getResultJson(), JsonObject.class);
                sb.append("Summary Metrics:\n");
                sb.append("----------------\n");
                sb.append(gson.toJson(json));
            }
        } catch (Exception ex) {
            sb.append("Metrics JSON: ").append(run.getResultJson());
        }
        
        detailsArea.setText(sb.toString());
    }

    private void onNodeDoubleClicked() {
        TreeItem<RunNodeData> node = treeView.getSelectionModel().getSelectedItem();
        if (node != null && node.getValue() != null && node.getValue().run != null) {
            String path = node.getValue().run.getHtmlPath();
            if (path != null && !path.trim().isEmpty()) {
                try {
                    File f = new File(path);
                    if (f.exists()) {
                        javax.swing.SwingUtilities.invokeLater(() -> {
                            com.backtester.ui.ReportViewerDialog.showForDirectory(null, f.getParent());
                        });
                    } else {
                        Alert alert = new Alert(Alert.AlertType.ERROR, "File no longer exists: " + path);
                        alert.show();
                    }
                } catch (Exception ex) {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Could not open Report: " + ex.getMessage());
                    alert.show();
                }
            }
        }
    }

    private void deleteSelectedRuns() {
        List<TreeItem<RunNodeData>> selected = new java.util.ArrayList<>(treeView.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) return;

        int deletedCount = 0;
        for (TreeItem<RunNodeData> item : selected) {
            if (item != null && item.getValue() != null && item.getValue().run != null) {
                dbManager.deleteRun(item.getValue().run.getId());
                deletedCount++;
            }
        }
        
        if (deletedCount > 0) {
            reloadTree();
        }
    }

    private void deleteAllRuns() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Deletion");
        alert.setHeaderText("Delete All Runs");
        alert.setContentText("Are you sure you want to completely clear the entire Saved Runs database?\nThis action cannot be undone.");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                dbManager.deleteAllRuns();
                reloadTree();
            }
        });
    }

    private void restoreWorkflow(HistoryRun run) {
        if (workflowView == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Workflow Automator View ist nicht verfügbar.");
            alert.show();
            return;
        }

        try {
            com.backtester.engine.WorkflowEngine engine = workflowView.getEngine();
            engine.restoreWorkflowState(run.getResultJson());

            // UI aktualisieren
            workflowView.refreshUI();

            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Workflow-Zustand erfolgreich aus der Datenbank wiederhergestellt!");
            alert.show();

            // Umschalten auf das Workflow Automator Tab
            if (treeView.getScene() != null) {
                javafx.scene.Node parent = treeView.getParent();
                while (parent != null && !(parent instanceof TabPane)) {
                    parent = parent.getParent();
                }
                if (parent instanceof TabPane) {
                    TabPane tp = (TabPane) parent;
                    for (Tab t : tp.getTabs()) {
                        if (t.getText() != null && t.getText().contains("Workflow")) {
                            tp.getSelectionModel().select(t);
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Fehler beim Wiederherstellen des Workflows:\n" + e.getMessage());
            alert.show();
        }
    }

    private static class RunNodeData {
        String label;
        HistoryRun run;
        boolean isToday;

        RunNodeData(String label, HistoryRun run, boolean isToday) {
            this.label = label;
            this.run = run;
            this.isToday = isToday;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public void bindTab(Tab tab) {
        this.tab = tab;
        if (dbManager != null) {
            List<HistoryRun> runs = dbManager.getAllRuns();
            Platform.runLater(() -> tab.setText("Database (" + runs.size() + ")"));
        }
    }

    public BorderPane getView() {
        return root;
    }
}
