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

    public HistoryView() {
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
        HBox titleBox = new HBox(10, treeTitle, countLabel);

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

        // Custom TreeCell to highlight today's runs
        treeView.setCellFactory(tv -> new TreeCell<RunNodeData>() {
            @Override
            protected void updateItem(RunNodeData item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item.label);
                    if (item.isToday) {
                        setStyle("-fx-background-color: rgba(0, 229, 255, 0.12); -fx-text-fill: #00e5ff; -fx-font-weight: bold;");
                    } else {
                        setStyle("");
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
        
        Platform.runLater(() -> countLabel.setText("Total: " + runs.size()));
        
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

    public BorderPane getView() {
        return root;
    }
}
