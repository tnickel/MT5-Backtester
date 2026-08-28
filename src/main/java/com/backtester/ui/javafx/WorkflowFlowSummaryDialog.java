package com.backtester.ui.javafx;

import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.workflow.ClusterFlowTreeModel;
import com.backtester.workflow.ClusterFlowTreeModel.LayoutNode;
import com.backtester.workflow.ClusterIdentity;
import com.backtester.workflow.CustomProject;
import com.backtester.workflow.DatabankManager;
import com.backtester.workflow.WorkflowFlowSummaryService;
import com.backtester.workflow.WorkflowFlowSummaryService.FlowStepSummary;
import com.backtester.workflow.WorkflowHandoffAuditService;
import com.backtester.workflow.WorkflowHandoffAuditService.FlowNode;
import com.backtester.workflow.WorkflowHandoffAuditService.HandoffTransition;
import com.backtester.workflow.WorkflowHandoffAuditService.MatchStatus;
import com.backtester.workflow.WorkflowHandoffAuditService.ParamSource;
import com.backtester.workflow.WorkflowHandoffAuditService.ParameterTransfer;
import com.backtester.workflow.WorkflowTask;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Show Flow: timeline uses the same 1..N numbering as workflow tiles;
 * optimizer tiles open the parameter handoff/diff board.
 */
public final class WorkflowFlowSummaryDialog {

    private WorkflowFlowSummaryDialog() {
    }

    public static void show(Window owner,
                            CustomProject project,
                            DatabankManager databankManager,
                            Function<WorkflowTask, String> outputDirectoryResolver) {
        Stage stage = new Stage();
        stage.setTitle("Show Flow — Parameter-Übergänge");
        stage.initModality(Modality.NONE);
        if (owner != null) stage.initOwner(owner);

        if (project != null && hasClusteredPasses(project)) {
            DatabankManager.rebuildCensus(project);
        }
        List<FlowNode> nodes = WorkflowHandoffAuditService.buildTimeline(project, databankManager);
        List<FlowStepSummary> stepSummaries = WorkflowFlowSummaryService.build(
                project, databankManager, outputDirectoryResolver);
        ClusterFlowTreeModel treeModel = ClusterFlowTreeModel.from(project, nodes);
        Map<Integer, FlowStepSummary> summaryByNumber = new HashMap<>();
        for (FlowStepSummary step : stepSummaries) {
            if (step != null) summaryByNumber.put(step.getIndex(), step);
        }

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(14));
        root.setStyle("-fx-background-color: #0b0d13;");
        root.setPrefSize(1480, 920);

        Label title = new Label("Show Flow");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        title.setTextFill(Color.web("#00e5ff"));

        String projectName = project != null && project.getName() != null ? project.getName() : "—";
        boolean clustered = hasClusteredPasses(project);
        boolean showTree = treeModel.hasTree();
        Label subtitle = new Label(showTree
                ? "Linienbaum für „" + projectName
                + "“ — nur Stufen/Linien mit Daten. Klick Stamm = Parameter-Übernahme; "
                + "Klick Ast = Strategien (Galerie & Einzel-Backtest). Ziehen = verschieben."
                : clustered
                ? "Für „" + projectName
                + "“ gibt es Cluster-IDs, aber noch keine Linien-Zähler in den Pick-Stufen. "
                + "Oben die Task-Leiste; der Stammbaum erscheint, sobald Live-Strategien in _pick liegen."
                : "Parameter-Timeline für „" + projectName
                + "“ — der Linienbaum (B1–B10) erscheint erst nach "
                + "„Diversität (B-Cluster)“, wenn Cluster-IDs gestempelt sind.");
        subtitle.setWrapText(true);
        subtitle.setStyle("-fx-text-fill: #9aa4b5; -fx-font-size: 12px;");

        VBox detailHost = new VBox(12);
        detailHost.setPadding(new Insets(8));
        detailHost.setFillWidth(true);

        Map<Integer, FlowNode> nodeByNumber = new HashMap<>();
        for (FlowNode n : nodes) {
            if (n != null) {
                nodeByNumber.put(n.getWorkflowNumber(), n);
            }
        }

        NodeConsumer onSelect = node -> detailHost.getChildren().setAll(
                createNodeDetail(node, summaryByNumber.get(node.getWorkflowNumber()), null));

        javafx.scene.Node centerPane;
        javafx.scene.Node timelineStrip = createTimeline(nodes, onSelect);
        if (showTree) {
            ClusterFlowTreeView treeView = new ClusterFlowTreeView(treeModel);
            VBox.setVgrow(treeView, Priority.ALWAYS);
            treeView.setMinHeight(520);
            treeView.setOnNodeSelected(layoutNode -> {
                FlowNode flowNode = layoutNode.getRow() != null
                        ? nodeByNumber.get(layoutNode.getRow().getWorkflowNumber())
                        : null;
                if (layoutNode.isTrunk()) {
                    if (flowNode == null && layoutNode.isRoot() && !nodes.isEmpty()) {
                        flowNode = nodes.get(0);
                    }
                    if (flowNode != null) {
                        detailHost.getChildren().setAll(createNodeDetail(
                                flowNode,
                                summaryByNumber.get(flowNode.getWorkflowNumber()),
                                layoutNode.getRow()));
                    }
                    return;
                }
                detailHost.getChildren().setAll(createClusterNodeDetail(
                        stage, project, databankManager, layoutNode, flowNode,
                        summaryByNumber.get(flowNode != null ? flowNode.getWorkflowNumber() : -1)));
            });
            centerPane = treeView;
        } else {
            centerPane = createTreeUnavailablePlaceholder(clustered);
        }

        VBox header = showTree
                ? new VBox(8, title, subtitle)
                : new VBox(8, title, subtitle, timelineStrip);
        root.setTop(header);

        ScrollPane detailScroll = new ScrollPane(detailHost);
        detailScroll.setFitToWidth(true);
        detailScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        detailScroll.setStyle("-fx-background: #0b0d13; -fx-background-color: #0b0d13;"
                + " -fx-border-color: #232a3b; -fx-border-radius: 6; -fx-background-radius: 6;");
        detailScroll.setPrefWidth(420);
        detailScroll.setMinWidth(360);
        detailScroll.setMaxWidth(480);
        BorderPane.setMargin(detailScroll, new Insets(12, 0, 10, 12));

        // Same shell with or without tree: center = canvas/placeholder, right = detail.
        root.setCenter(centerPane);
        BorderPane.setMargin(centerPane, new Insets(12, 0, 10, 0));
        root.setRight(detailScroll);

        if (nodes.isEmpty()) {
            detailHost.getChildren().setAll(placeholder("Kein Flow", "Dieses Projekt hat keine Tasks."));
        } else {
            FlowNode initial = nodes.stream()
                    .filter(FlowNode::hasHandoff)
                    .findFirst()
                    .orElse(nodes.get(0));
            detailHost.getChildren().setAll(
                    createNodeDetail(initial, summaryByNumber.get(initial.getWorkflowNumber()), null));
        }

        Button closeBtn = new Button("Schließen");
        closeBtn.setStyle(
                "-fx-background-color: #1e2432; -fx-text-fill: #e6e9f0; -fx-border-color: #596273; "
                        + "-fx-border-radius: 4; -fx-background-radius: 4; -fx-font-weight: bold;");
        closeBtn.setOnAction(e -> stage.close());
        HBox bottom = new HBox(closeBtn);
        bottom.setAlignment(Pos.CENTER_RIGHT);
        root.setBottom(bottom);

        Scene scene = new Scene(root);
        if (owner != null && owner.getScene() != null && !owner.getScene().getStylesheets().isEmpty()) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        stage.setScene(scene);
        stage.show();
    }

    private interface NodeConsumer {
        void accept(FlowNode node);
    }

    private static boolean hasClusteredPasses(CustomProject project) {
        if (project == null || project.getDatabanks() == null) {
            return false;
        }
        for (List<CombinedPass> passes : project.getDatabanks().values()) {
            if (passes == null) {
                continue;
            }
            for (CombinedPass pass : passes) {
                if (ClusterIdentity.hasId(pass)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static HBox createTimeline(List<FlowNode> nodes, NodeConsumer onSelect) {
        HBox row = new HBox(0);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8));

        ToggleGroup group = new ToggleGroup();
        ToggleButton firstSelected = null;
        for (int i = 0; i < nodes.size(); i++) {
            FlowNode node = nodes.get(i);
            if (i > 0) {
                Line link = new Line(0, 0, 16, 0);
                link.setStroke(Color.web("#334155"));
                link.setStrokeWidth(2);
                StackPane linkBox = new StackPane(link);
                linkBox.setPrefWidth(16);
                row.getChildren().add(linkBox);
            }

            ToggleButton chip = new ToggleButton();
            chip.setToggleGroup(group);
            chip.setUserData(node);
            chip.setMaxWidth(132);
            chip.setPrefWidth(124);
            chip.setWrapText(true);
            chip.setTextAlignment(TextAlignment.CENTER);
            // Same pattern as ProjectWorkflowEditorView.createTaskCard: "{n}. {name}"
            chip.setText(node.getWorkflowNumber() + ".\n" + shortName(node.getTaskName(), 18));
            styleTimelineChip(chip, node, false);
            chip.selectedProperty().addListener((obs, o, selected) -> {
                styleTimelineChip(chip, node, selected);
                if (selected) onSelect.accept(node);
            });
            if (firstSelected == null && node.hasHandoff()) {
                firstSelected = chip;
            }
            if (firstSelected == null && i == 0) {
                firstSelected = chip;
            }
            row.getChildren().add(chip);
        }
        if (firstSelected != null) {
            firstSelected.setSelected(true);
        }

        ScrollPane strip = new ScrollPane(row);
        strip.setFitToHeight(true);
        strip.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        strip.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        strip.setPrefHeight(78);
        strip.setStyle(
                "-fx-background: #10141e; -fx-background-color: #10141e; -fx-border-color: #232a3b; "
                        + "-fx-border-radius: 6; -fx-background-radius: 6;");

        HBox wrap = new HBox(strip);
        HBox.setHgrow(strip, Priority.ALWAYS);
        return wrap;
    }

    private static javafx.scene.Node createClusterTree(Window owner,
                                                       CustomProject project,
                                                       DatabankManager databankManager,
                                                       ClusterFlowTreeModel model,
                                                       List<FlowNode> nodes,
                                                       NodeConsumer onSelect) {
        // Kept for compatibility; show() now builds ClusterFlowTreeView directly.
        ClusterFlowTreeView treeView = new ClusterFlowTreeView(model);
        Map<Integer, FlowNode> nodeByNumber = new HashMap<>();
        if (nodes != null) {
            for (FlowNode node : nodes) {
                if (node != null) {
                    nodeByNumber.put(node.getWorkflowNumber(), node);
                }
            }
        }
        treeView.setOnNodeSelected(layoutNode -> {
            FlowNode flowNode = layoutNode.getRow() != null
                    ? nodeByNumber.get(layoutNode.getRow().getWorkflowNumber())
                    : null;
            if (layoutNode.isTrunk()) {
                if (flowNode != null) {
                    onSelect.accept(flowNode);
                }
                return;
            }
            // Selection without detail host — open gallery as fallback.
            if (layoutNode.getRow() != null && layoutNode.getCell() != null) {
                openClusterGallery(owner, project, databankManager, layoutNode.getRow(), layoutNode.getCell());
            }
        });
        return treeView;
    }

    private static VBox createClusterNodeDetail(Window owner,
                                                CustomProject project,
                                                DatabankManager databankManager,
                                                LayoutNode layoutNode,
                                                FlowNode flowNode,
                                                FlowStepSummary summary) {
        ClusterFlowTreeModel.Row row = layoutNode.getRow();
        ClusterFlowTreeModel.Cell cell = layoutNode.getCell();
        String clusterId = cell != null ? cell.getClusterId() : "";
        String detailDb = ClusterFlowTreeModel.detailDatabankName(row);
        boolean optimizerRaw = row != null && row.isOptimizerResult()
                && ClusterFlowTreeModel.isRawDatabank(detailDb);

        VBox board = new VBox(10);
        board.setPadding(new Insets(4));

        Label path = new Label((row != null ? row.trunkLabel() : "")
                + "  ·  " + (cell != null ? cell.fullLabel() : clusterId));
        path.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        path.setTextFill(Color.web("#e6e9f0"));

        if (row != null) {
            Label action = new Label(row.actionVerb() + " — " + row.actionExplanation());
            action.setWrapText(true);
            action.setStyle("-fx-text-fill: #00e5ff; -fx-font-size: 13px;");
            board.getProperties().put("actionLabel", action);
        }

        int live = cell != null ? cell.getLiveCount() : 0;
        int stageTotal = row != null ? row.liveTotal() : 0;
        int livingLines = row != null ? row.livingLineCount() : 0;
        Label meta = new Label(optimizerRaw
                ? ("Diese Linie (" + clusterId + "): " + live + " Optimizer-Kandidaten"
                + (stageTotal > 0
                ? "\nStufe insgesamt: " + stageTotal + " Kandidaten in " + livingLines + " Linien"
                : "")
                + (detailDb.isBlank() ? "" : "\nDatabank: " + detailDb + " (_raw = Suchergebnis)")
                + (cell != null && cell.getChampionPassNumber() > 0
                ? "\nChampion Pass #" + cell.getChampionPassNumber() : ""))
                : ("Diese Linie (" + clusterId + "): " + live + " Strategie(n)"
                + (stageTotal > 0
                ? "\nStufe insgesamt: " + stageTotal + " in " + livingLines + " Linien"
                + " (Badge im Baum = nur diese Linie)"
                : "")
                + (detailDb.isBlank() ? "" : "\nDatabank: " + detailDb)
                + (cell != null && cell.getChampionPassNumber() > 0
                ? "\nChampion Pass #" + cell.getChampionPassNumber() : "")));
        meta.setStyle("-fx-text-fill: #9aa4b5; -fx-font-size: 12px;");
        meta.setWrapText(true);

        List<CombinedPass> bankPasses = loadDatabankPasses(project, databankManager, detailDb);
        List<CombinedPass> passes = ClusterFlowTreeModel.filterByCluster(bankPasses, clusterId);
        String tableNote = null;
        if (passes.isEmpty() && !bankPasses.isEmpty() && live > 0) {
            passes = bankPasses;
            tableNote = "Hinweis: Cluster-Filter lieferte 0 Treffer — zeige alle "
                    + bankPasses.size() + " Passes aus „" + detailDb + "“.";
        } else if (passes.isEmpty() && bankPasses.isEmpty() && !detailDb.isBlank()) {
            tableNote = "Databank „" + detailDb + "“ ist leer oder nicht geladen.";
        } else if (optimizerRaw && !passes.isEmpty()) {
            tableNote = passes.size() + " Kandidaten dieser Linie aus „" + detailDb
                    + "“ (nicht die Eingangs-Picks). Sortiert nach Score.";
        } else if (!optimizerRaw && live > 0 && passes.size() < live) {
            tableNote = "Tabelle: " + passes.size() + " Passes mit clusterId "
                    + clusterId + " — Census zählt " + live + ".";
        }

        passes = sortByScoreDesc(passes);

        TableView<CombinedPass> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(Math.min(420, 120 + Math.min(passes.size(), 12) * 28));
        table.setStyle("-fx-background-color: #10141e;");

        TableColumn<CombinedPass, String> passCol = new TableColumn<>("Pass");
        passCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue() != null ? String.valueOf(cd.getValue().getPassNumber()) : ""));
        TableColumn<CombinedPass, String> scoreCol = new TableColumn<>("Score");
        scoreCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue() != null ? String.format(Locale.US, "%.1f", cd.getValue().getScore()) : ""));
        TableColumn<CombinedPass, String> profitCol = new TableColumn<>("BT Backtest");
        profitCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue() != null ? String.format(Locale.US, "%.2f", cd.getValue().getBtBacktestBalance()) : ""));
        TableColumn<CombinedPass, String> pfCol = new TableColumn<>("BT PF");
        pfCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue() != null ? String.format(Locale.US, "%.2f", cd.getValue().getBtPf()) : ""));
        TableColumn<CombinedPass, String> ddCol = new TableColumn<>("BT DD%");
        ddCol.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue() != null ? String.format(Locale.US, "%.2f", cd.getValue().getBtDd()) : ""));
        table.getColumns().addAll(passCol, scoreCol, profitCol, pfCol, ddCol);
        table.setItems(FXCollections.observableArrayList(passes));
        if (passes.isEmpty()) {
            table.setPlaceholder(new Label("Keine Strategien in diesem Knoten."));
        }
        if (tableNote != null) {
            Label note = new Label(tableNote);
            note.setWrapText(true);
            note.setStyle("-fx-text-fill: #ffb74d; -fx-font-size: 11px;");
            board.getProperties().put("tableNote", note);
        }

        Button galleryBtn = new Button("Equity-Galerie");
        galleryBtn.setStyle(
                "-fx-background-color: #1e2432; -fx-text-fill: #e6e9f0; -fx-border-color: #00e5ff; "
                        + "-fx-border-radius: 4; -fx-background-radius: 4; -fx-font-weight: bold;");
        galleryBtn.setOnAction(e -> {
            if (row != null && cell != null) {
                openClusterGallery(owner, project, databankManager, row, cell);
            }
        });

        Button detailsBtn = new Button("Details");
        detailsBtn.setStyle(
                "-fx-background-color: #1e2432; -fx-text-fill: #e6e9f0; -fx-border-color: #596273; "
                        + "-fx-border-radius: 4; -fx-background-radius: 4; -fx-font-weight: bold;");
        detailsBtn.setOnAction(e -> {
            CombinedPass selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                info(owner, "Bitte eine Strategie in der Tabelle auswählen.");
                return;
            }
            StrategyDetailsModalDialog.show(selected, detailDb, project, owner, 0);
        });

        Button backtestBtn = new Button("Einzel-Backtest im MT5");
        backtestBtn.setStyle(
                "-fx-background-color: #123024; -fx-text-fill: #e6e9f0; -fx-border-color: #00e676; "
                        + "-fx-border-radius: 4; -fx-background-radius: 4; -fx-font-weight: bold;");
        backtestBtn.setOnAction(e -> {
            CombinedPass selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                info(owner, "Bitte eine Strategie in der Tabelle auswählen.");
                return;
            }
            SingleBacktestHelper.runSingleBacktestInMetaTrader(selected, detailDb, project, owner);
        });

        HBox actions = new HBox(10, galleryBtn, detailsBtn, backtestBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        board.getChildren().addAll(path, meta);
        Object actionNode = board.getProperties().get("actionLabel");
        if (actionNode instanceof Label actionLabel) {
            board.getChildren().add(1, actionLabel);
        }
        Object noteNode = board.getProperties().get("tableNote");
        if (noteNode instanceof Label noteLabel) {
            board.getChildren().add(noteLabel);
        }
        board.getChildren().addAll(table, actions);

        if (summary != null) {
            board.getChildren().add(1, createNarrativeCard(summary));
        }
        if (flowNode != null && flowNode.hasHandoff()) {
            Label handoffHint = new Label("Parameter-Übergabe dieser Stufe siehe Stamm-Knoten.");
            handoffHint.setStyle("-fx-text-fill: #7a8496; -fx-font-size: 11px;");
            board.getChildren().add(handoffHint);
        }
        return board;
    }

    private static List<CombinedPass> sortByScoreDesc(List<CombinedPass> passes) {
        if (passes == null || passes.isEmpty()) {
            return List.of();
        }
        List<CombinedPass> sorted = new java.util.ArrayList<>(passes);
        sorted.sort((a, b) -> Double.compare(
                b != null ? b.getScore() : Double.NEGATIVE_INFINITY,
                a != null ? a.getScore() : Double.NEGATIVE_INFINITY));
        return sorted;
    }

    private static List<CombinedPass> loadDatabankPasses(CustomProject project,
                                                         DatabankManager databankManager,
                                                         String dbName) {
        if (dbName == null || dbName.isBlank()) {
            return List.of();
        }
        if (databankManager != null) {
            List<CombinedPass> fromManager = databankManager.getDatabank(dbName);
            if (fromManager != null && !fromManager.isEmpty()) {
                return fromManager;
            }
        }
        if (project != null && project.getDatabanks() != null) {
            List<CombinedPass> fromProject = project.getDatabanks().get(dbName);
            if (fromProject != null && !fromProject.isEmpty()) {
                return List.copyOf(fromProject);
            }
            for (var entry : project.getDatabanks().entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(dbName)
                        && entry.getValue() != null && !entry.getValue().isEmpty()) {
                    return List.copyOf(entry.getValue());
                }
            }
        }
        return List.of();
    }

    private static void info(Window owner, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle("Show Flow");
        alert.setHeaderText(null);
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.showAndWait();
    }

    private static void openClusterGallery(Window owner,
                                           CustomProject project,
                                           DatabankManager databankManager,
                                           ClusterFlowTreeModel.Row row,
                                           ClusterFlowTreeModel.Cell cell) {
        String clusterId = cell != null ? cell.getClusterId() : null;
        String detailDb = ClusterFlowTreeModel.detailDatabankName(row);
        if (clusterId == null || clusterId.isBlank()) {
            return;
        }
        if (detailDb.isBlank() || databankManager == null) {
            info(owner, "Keine Strategien in " + clusterId + ".");
            return;
        }
        DatabankEquityGalleryDialog.show(owner, databankManager, detailDb, project, null, clusterId);
    }

    private static void styleTimelineChip(ToggleButton chip, FlowNode node, boolean selected) {
        String border;
        if (!node.isEnabled()) {
            border = "#546e7a";
        } else if (node.hasHandoff()) {
            HandoffTransition h = node.getHandoff();
            border = h.getMismatchCount() > 0 ? "#ff5252"
                    : (!h.isAdopted() && !h.isRootStage() ? "#ffab40"
                    : (h.isGateForced() ? "#00e676" : "#64b5f6"));
        } else if (node.getTaskType() == WorkflowTask.TaskType.PRE_FILTER) {
            border = "#ab47bc";
        } else {
            border = "#78909c";
        }
        chip.setStyle(
                "-fx-background-color: " + (selected ? "rgba(0, 229, 255, 0.12)" : "#141822")
                        + "; -fx-text-fill: " + (node.isEnabled() ? "#e6e9f0" : "#78909c")
                        + "; -fx-font-size: 11px; -fx-font-weight: bold; "
                        + "-fx-border-color: " + border + "; -fx-border-width: " + (selected ? "2" : "1")
                        + "; -fx-border-radius: 6; -fx-background-radius: 6; -fx-cursor: hand;");
    }

    private static VBox createNodeDetail(FlowNode node,
                                         FlowStepSummary summary,
                                         ClusterFlowTreeModel.Row treeRow) {
        VBox detail = node.hasHandoff() ? createHandoffBoard(node) : createGenericTaskBoard(node);
        int insertAt = 1;
        detail.getChildren().add(insertAt++, createActionCard(node, treeRow));
        if (treeRow != null && treeRow.isOptimizerResult() && treeRow.candidateTotal() > 0) {
            Label treeSum = new Label("Optimizer-Ergebnis: " + treeRow.candidateTotal()
                    + " Kandidaten in _raw (Suche aus den vorher gewählten Linien). "
                    + "Das sind noch keine finalen Picks — der nächste Filter wählt wieder wenige aus.");
            treeSum.setWrapText(true);
            treeSum.setStyle("-fx-text-fill: #ffd54f; -fx-font-size: 12px;");
            detail.getChildren().add(insertAt++, treeSum);
        } else if (treeRow != null && treeRow.liveTotal() > 0) {
            Label treeSum = new Label("Im Linienbaum: Σ " + treeRow.liveTotal()
                    + " auf " + treeRow.livingLineCount()
                    + " Linien — Badge pro Ast-Knoten = nur diese Linie; Σ = Summe.");
            treeSum.setWrapText(true);
            treeSum.setStyle("-fx-text-fill: #ffd54f; -fx-font-size: 12px;");
            detail.getChildren().add(insertAt++, treeSum);
        }
        if (summary != null) {
            detail.getChildren().add(insertAt, createNarrativeCard(summary));
        }
        return detail;
    }

    private static VBox createActionCard(FlowNode node, ClusterFlowTreeModel.Row treeRow) {
        WorkflowTask.TaskType type = treeRow != null && treeRow.getTaskType() != null
                ? treeRow.getTaskType()
                : node.getTaskType();
        String verb = ClusterFlowTreeModel.actionVerbFor(type);
        String explain = ClusterFlowTreeModel.actionExplanationFor(type);
        String typeName = type != null ? type.getDisplayName() : node.getTypeLabel();

        VBox card = panelCard("Was passiert hier");
        Label headline = new Label(verb + "  ·  " + typeName);
        headline.setWrapText(true);
        headline.setStyle("-fx-text-fill: #00e5ff; -fx-font-size: 15px; -fx-font-weight: bold;");
        Label body = new Label(explain);
        body.setWrapText(true);
        body.setStyle("-fx-text-fill: #e6e9f0; -fx-font-size: 13px;");
        card.getChildren().addAll(headline, body);

        int inCount = treeRow != null && treeRow.getSourceCount() >= 0
                ? treeRow.getSourceCount() : node.getSourceCount();
        int outCount = treeRow != null && treeRow.getTargetCount() >= 0
                ? treeRow.getTargetCount() : node.getTargetCount();
        String src = treeRow != null && !treeRow.getSourceDatabank().isBlank()
                ? treeRow.getSourceDatabank() : node.getSourceDatabank();
        String tgt = treeRow != null && !treeRow.getTargetDatabank().isBlank()
                ? treeRow.getTargetDatabank() : node.getTargetDatabank();
        if (!src.isBlank() || !tgt.isBlank() || inCount >= 0 || outCount >= 0) {
            String io = (src.isBlank() ? "—" : "„" + src + "“ (" + Math.max(0, inCount) + ")")
                    + "  →  "
                    + (tgt.isBlank() ? "—" : "„" + tgt + "“ (" + Math.max(0, outCount) + ")");
            Label ioLabel = new Label(io);
            ioLabel.setWrapText(true);
            ioLabel.setStyle("-fx-text-fill: #9aa4b5; -fx-font-size: 12px;");
            card.getChildren().add(ioLabel);
        }
        return card;
    }

    private static VBox createNarrativeCard(FlowStepSummary summary) {
        VBox card = panelCard("Ablauf / Entscheidung");
        Label what = new Label(summary.getWhatHappened() != null ? summary.getWhatHappened() : "");
        what.setWrapText(true);
        what.setStyle("-fx-text-fill: #cfd8dc; -fx-font-size: 12px;");
        Label decision = new Label(summary.getDecision() != null ? summary.getDecision() : "");
        decision.setWrapText(true);
        decision.setStyle("-fx-text-fill: #00e5ff; -fx-font-size: 12px; -fx-font-weight: bold;");
        card.getChildren().addAll(what, decision);
        if (summary.getDetails() != null) {
            for (String line : summary.getDetails()) {
                if (line == null || line.isBlank()) continue;
                Label detail = new Label("• " + line);
                detail.setWrapText(true);
                detail.setStyle("-fx-text-fill: #9aa4b5; -fx-font-size: 11px;");
                card.getChildren().add(detail);
            }
        }
        return card;
    }

    private static VBox createGenericTaskBoard(FlowNode node) {
        VBox board = new VBox(10);
        board.setPadding(new Insets(4));

        Label path = new Label(node.getWorkflowNumber() + ". " + node.getTaskName());
        path.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        path.setTextFill(Color.web("#e6e9f0"));
        path.setWrapText(true);

        Label type = new Label(node.getTypeLabel() + "  ·  " + node.getStatusLabel());
        type.setStyle("-fx-text-fill: #00e5ff; -fx-font-size: 12px;");

        Label summary = new Label(node.getSummary());
        summary.setWrapText(true);
        summary.setStyle("-fx-text-fill: #cfd8dc; -fx-font-size: 13px;");

        VBox card = panelCard("Kachel #" + node.getWorkflowNumber());
        card.getChildren().addAll(
                kv("Typ", node.getTypeLabel()),
                kv("Status", node.getStatusLabel()),
                kv("Source", blankDash(node.getSourceDatabank()) + " (" + node.getSourceCount() + ")"),
                kv("Target", blankDash(node.getTargetDatabank()) + " (" + node.getTargetCount() + ")"));

        Label hint = new Label(node.getTaskType() == WorkflowTask.TaskType.PRE_FILTER
                ? "Filter-Kacheln bereiten die Databank für den nächsten Optimizer vor. "
                + "Klicke die folgende Optimizer-Kachel für die Setfile-Übernahme."
                : "Parameter-Setfile-Diff gibt es bei Optimizer-Kacheln (blaue/grüne Rahmen in der Timeline).");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #9aa4b5; -fx-font-size: 12px;");

        board.getChildren().addAll(path, type, summary, card, hint);
        return board;
    }

    private static VBox createHandoffBoard(FlowNode node) {
        HandoffTransition handoff = node.getHandoff();
        VBox board = new VBox(12);
        board.setPadding(new Insets(4));

        Label path = new Label(node.getWorkflowNumber() + ". " + node.getTaskName());
        path.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        path.setTextFill(Color.web("#e6e9f0"));
        path.setWrapText(true);

        Label chain = new Label(handoff.getTitle());
        chain.setWrapText(true);
        chain.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

        Label proof = new Label(handoff.getProofHeadline());
        proof.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        proof.setTextFill(Color.web(handoff.getMismatchCount() > 0 ? "#ff5252"
                : (!handoff.isAdopted() && !handoff.isRootStage() ? "#ffab40" : "#00e676")));

        HBox columns = new HBox(12);
        columns.getChildren().addAll(createSourceCard(handoff), createTargetCard(node, handoff));
        HBox.setHgrow(columns.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(columns.getChildren().get(1), Priority.ALWAYS);

        board.getChildren().addAll(path, chain, proof, columns, createTransferSection(handoff));
        return board;
    }

    private static VBox createSourceCard(HandoffTransition handoff) {
        VBox card = panelCard(handoff.isRootStage() ? "Startstufe" : "Quelle (Vorstufe)");
        if (handoff.isRootStage()) {
            card.getChildren().add(kv("Hinweis", "Erste Optimizer-Stufe — keine Pass-Übernahme von einer Vorstufe"));
            card.getChildren().add(sectionLabel("Suchziele dieser Stufe"));
            card.getChildren().add(tagRow(handoff.getToTargets(), "#69f0ae"));
            return card;
        }
        card.getChildren().add(kv("Optimizer", blankDash(handoff.getFromTaskName())));
        if (!handoff.getViaTaskName().isBlank()) {
            card.getChildren().add(kv("via Filter", handoff.getViaTaskName()));
        }
        card.getChildren().add(kv("Übernommener Pass",
                handoff.isAdopted()
                        ? "#" + handoff.getPassNumber()
                        + (Double.isFinite(handoff.getPassScore())
                        ? " · Score " + String.format(Locale.ROOT, "%.3f", handoff.getPassScore())
                        : "")
                        : "— noch nicht"));
        card.getChildren().add(kv("Databank", blankDash(handoff.getDatabank())));
        card.getChildren().add(sectionLabel("Optimierte Parameter der Vorstufe"));
        card.getChildren().add(tagRow(handoff.getFromTargets(), "#69f0ae"));

        if (handoff.isGateForced() || !handoff.getGateParameter().isBlank()) {
            card.getChildren().add(sectionLabel("Filter-Entscheidung"));
            Label badge = new Label(handoff.isGateForced()
                    ? ("FILTER "
                    + ("true".equalsIgnoreCase(handoff.getGateForcedValue()) ? "AN" : "AUS")
                    + " → SETFILE")
                    : "Gate ohne Force");
            badge.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
            badge.setTextFill(Color.web(handoff.isGateForced()
                    && "true".equalsIgnoreCase(handoff.getGateForcedValue()) ? "#00e676" : "#ffab40"));
            badge.setPadding(new Insets(8, 12, 8, 12));
            badge.setStyle("-fx-border-color: " + (handoff.isGateForced() ? "#00e676" : "#ffab40")
                    + "; -fx-border-width: 2; -fx-border-radius: 6; -fx-background-radius: 6;");
            card.getChildren().add(badge);
            if (!handoff.getGateParameter().isBlank()) {
                card.getChildren().add(kv("Gate", handoff.getGateParameter()
                        + (handoff.getGateForcedValue().isBlank() ? "" : "=" + handoff.getGateForcedValue())));
            }
            if (Double.isFinite(handoff.getGateOnMedian()) || Double.isFinite(handoff.getGateOffMedian())) {
                card.getChildren().add(createMiniChart(handoff));
            }
            if (!handoff.getGateNote().isBlank()) {
                Label note = new Label(handoff.getGateNote());
                note.setWrapText(true);
                note.setStyle("-fx-text-fill: #9aa4b5; -fx-font-size: 11px;");
                card.getChildren().add(note);
            }
        }
        return card;
    }

    private static VBox createTargetCard(FlowNode node, HandoffTransition handoff) {
        VBox card = panelCard("Ziel-Setfile — Kachel #" + node.getWorkflowNumber());
        card.getChildren().add(kv("Optimizer", blankDash(handoff.getToTaskName())));
        card.getChildren().add(kv("Status", handoff.getToStatus() != null ? handoff.getToStatus().name() : "—"));
        card.getChildren().add(kv("Basis übernommen", handoff.isAdopted() ? "ja" : "nein"));
        card.getChildren().add(sectionLabel("Neue Opt-Ziele (Y)"));
        card.getChildren().add(tagRow(handoff.getToTargets(), "#00e5ff"));

        int fixed = 0;
        int forced = 0;
        int targets = 0;
        for (ParameterTransfer t : handoff.getTransfers()) {
            if (t.getSource() == ParamSource.PASS_FIXED) fixed++;
            else if (t.getSource() == ParamSource.GATE_FORCED) forced++;
            else if (t.getSource() == ParamSource.STAGE_TARGET) targets++;
        }
        card.getChildren().add(sectionLabel("Übernahme-Zähler"));
        card.getChildren().add(kv("Pass-fixiert", String.valueOf(fixed)));
        card.getChildren().add(kv("Filter erzwungen", String.valueOf(forced)));
        card.getChildren().add(kv("Neue Ziele", String.valueOf(targets)));
        card.getChildren().add(kv("Abgleich",
                handoff.getOkCount() + " OK · "
                        + handoff.getMismatchCount() + " Abweichung · "
                        + handoff.getPendingCount() + " pending"));
        return card;
    }

    private static VBox createTransferSection(HandoffTransition handoff) {
        VBox box = new VBox(8);
        Label head = new Label("Parameter-Übernahme & Setfile-Diff");
        head.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        head.setTextFill(Color.web("#ffd54f"));

        TextField search = new TextField();
        search.setPromptText("Suche Parameter…");
        search.setStyle("-fx-background-color: #141822; -fx-text-fill: #00e5ff; -fx-border-color: #232a3b;");

        CheckBox onlyDiff = new CheckBox("Nur Abweichungen / fehlend / pending");
        onlyDiff.setStyle("-fx-text-fill: #cfd8dc;");
        CheckBox onlyGates = new CheckBox("Nur Use_*/Filter");
        onlyGates.setStyle("-fx-text-fill: #cfd8dc;");
        CheckBox onlyForced = new CheckBox("Nur erzwungen");
        onlyForced.setStyle("-fx-text-fill: #cfd8dc;");

        HBox filters = new HBox(14, search, onlyDiff, onlyGates, onlyForced);
        filters.setAlignment(Pos.CENTER_LEFT);

        ObservableList<ParameterTransfer> master = FXCollections.observableArrayList(handoff.getTransfers());
        FilteredList<ParameterTransfer> filtered = new FilteredList<>(master, p -> true);

        Runnable applyFilter = () -> filtered.setPredicate(row -> {
            if (row == null) return false;
            String q = search.getText() != null ? search.getText().trim().toLowerCase(Locale.ROOT) : "";
            if (!q.isEmpty() && !row.getName().toLowerCase(Locale.ROOT).contains(q)
                    && !row.getSetfileLine().toLowerCase(Locale.ROOT).contains(q)) {
                return false;
            }
            if (onlyDiff.isSelected() && row.getMatchStatus() == MatchStatus.OK) return false;
            if (onlyForced.isSelected() && row.getSource() != ParamSource.GATE_FORCED) return false;
            if (onlyGates.isSelected()) {
                String n = row.getName().toLowerCase(Locale.ROOT);
                if (!(n.contains("use_") || row.getSource() == ParamSource.GATE_FORCED)) return false;
            }
            return true;
        });
        search.textProperty().addListener((o, a, b) -> applyFilter.run());
        onlyDiff.selectedProperty().addListener((o, a, b) -> applyFilter.run());
        onlyGates.selectedProperty().addListener((o, a, b) -> applyFilter.run());
        onlyForced.selectedProperty().addListener((o, a, b) -> applyFilter.run());

        TableView<ParameterTransfer> table = new TableView<>(filtered);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(360);
        table.setStyle(
                "-fx-background-color: #141822; -fx-control-inner-background: #141822; "
                        + "-fx-table-cell-border-color: #232a3b; -fx-text-fill: #e6e9f0;");
        table.setPlaceholder(new Label("Keine Parameter in diesem Übergang"));

        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(ParameterTransfer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                    return;
                }
                setStyle(switch (item.getMatchStatus()) {
                    case OK -> "-fx-background-color: rgba(0, 230, 118, 0.08);";
                    case MISMATCH, MISSING -> "-fx-background-color: rgba(255, 82, 82, 0.14);";
                    case PENDING -> "-fx-background-color: rgba(255, 171, 64, 0.12);";
                });
            }
        });

        TableColumn<ParameterTransfer, String> statusCol = col("Status", 90, ParameterTransfer::getMatchLabel);
        TableColumn<ParameterTransfer, String> sourceCol = col("Herkunft", 120, ParameterTransfer::getSourceLabel);
        TableColumn<ParameterTransfer, String> nameCol = col("Parameter", 200, ParameterTransfer::getName);
        TableColumn<ParameterTransfer, String> expectedCol = col("Erwartet", 90, ParameterTransfer::getExpectedValue);
        TableColumn<ParameterTransfer, String> actualCol = col("Im Setfile", 90, ParameterTransfer::getActualValue);
        TableColumn<ParameterTransfer, String> optCol = col("Opt", 50, r -> r.isOptimizeEnabled() ? "Y" : "N");
        TableColumn<ParameterTransfer, String> rangeCol = col("Start/Step/Stop", 120,
                r -> joinRange(r.getOptimizeStart(), r.getOptimizeStep(), r.getOptimizeEnd()));
        TableColumn<ParameterTransfer, String> lineCol = col("Setfile-Zeile", 280, ParameterTransfer::getSetfileLine);
        TableColumn<ParameterTransfer, String> noteCol = col("Hinweis", 180, ParameterTransfer::getNote);

        statusCol.setCellFactory(c -> colorStatusCell());
        sourceCol.setCellFactory(c -> colorSourceCell());

        table.getColumns().setAll(
                statusCol, sourceCol, nameCol, expectedCol, actualCol, optCol, rangeCol, lineCol, noteCol);

        box.getChildren().addAll(head, filters, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        return box;
    }

    private static TableColumn<ParameterTransfer, String> col(String title,
                                                              double width,
                                                              java.util.function.Function<ParameterTransfer, String> getter) {
        TableColumn<ParameterTransfer, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue() != null ? getter.apply(c.getValue()) : ""));
        return column;
    }

    private static TableCell<ParameterTransfer, String> colorStatusCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                setStyle("-fx-font-weight: bold; -fx-text-fill: "
                        + switch (item) {
                    case "OK" -> "#00e676";
                    case "ABWEICHUNG", "FEHLT" -> "#ff5252";
                    default -> "#ffab40";
                } + ";");
            }
        };
    }

    private static TableCell<ParameterTransfer, String> colorSourceCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                setStyle("-fx-text-fill: "
                        + switch (item) {
                    case "Filter erzwungen" -> "#69f0ae";
                    case "Pass fixiert" -> "#90caf9";
                    case "Neues Opt-Ziel" -> "#00e5ff";
                    default -> "#b0bec5";
                } + ";");
            }
        };
    }

    private static BarChart<String, Number> createMiniChart(HandoffTransition handoff) {
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        y.setLabel("Median Score");
        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setTitle("Filter AN vs AUS");
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setPrefHeight(150);
        chart.setMaxHeight(160);
        chart.setStyle("-fx-background-color: transparent;");
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        if (Double.isFinite(handoff.getGateOnMedian())) {
            series.getData().add(new XYChart.Data<>("AN", handoff.getGateOnMedian()));
        }
        if (Double.isFinite(handoff.getGateOffMedian())) {
            series.getData().add(new XYChart.Data<>("AUS", handoff.getGateOffMedian()));
        }
        chart.getData().add(series);
        return chart;
    }

    private static VBox panelCard(String titleText) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(12));
        card.setStyle(
                "-fx-background-color: #141822; -fx-border-color: #2e3545; -fx-border-radius: 8; "
                        + "-fx-background-radius: 8;");
        Label title = new Label(titleText);
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        title.setTextFill(Color.web("#00e5ff"));
        card.getChildren().add(title);
        return card;
    }

    private static Label sectionLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-weight: bold;");
        return label;
    }

    private static HBox kv(String key, String value) {
        Label k = new Label(key + ":");
        k.setStyle("-fx-text-fill: #78909c; -fx-font-size: 12px;");
        k.setMinWidth(130);
        Label v = new Label(value != null ? value : "—");
        v.setWrapText(true);
        v.setStyle("-fx-text-fill: #e6e9f0; -fx-font-size: 12px; -fx-font-weight: bold;");
        HBox row = new HBox(8, k, v);
        row.setAlignment(Pos.TOP_LEFT);
        return row;
    }

    private static HBox tagRow(List<String> values, String color) {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        if (values == null || values.isEmpty()) {
            Label empty = new Label("—");
            empty.setStyle("-fx-text-fill: #64748b;");
            row.getChildren().add(empty);
            return row;
        }
        for (String value : values) {
            Label tag = new Label(value);
            tag.setWrapText(true);
            tag.setStyle("-fx-text-fill: " + color
                    + "; -fx-background-color: rgba(255,255,255,0.04); -fx-padding: 3 8 3 8; "
                    + "-fx-background-radius: 4; -fx-border-color: " + color
                    + "; -fx-border-radius: 4; -fx-font-size: 11px;");
            row.getChildren().add(tag);
        }
        return row;
    }

    /**
     * Fills the tree canvas area when {@link ClusterFlowTreeModel#hasTree()} is false,
     * so Show Flow never looks like a blank/broken panel.
     */
    private static javafx.scene.Node createTreeUnavailablePlaceholder(boolean clustered) {
        VBox card = new VBox(14);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(36));
        card.setMaxWidth(560);
        card.setStyle(
                "-fx-background-color: #141822; -fx-border-color: #2e3545; -fx-border-radius: 8; "
                        + "-fx-background-radius: 8;");

        Label head = new Label(clustered
                ? "Linienbaum noch ohne Zähler"
                : "Linienbaum noch nicht verfügbar");
        head.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        head.setTextFill(Color.web("#e6e9f0"));
        head.setTextAlignment(TextAlignment.CENTER);

        Label body = new Label(clustered
                ? "Cluster-IDs sind schon vorhanden, aber in den Pick-Stufen gibt es noch "
                + "keine Live-Zähler (alles wäre „—“). Sobald Diversität/Optimizer Strategien "
                + "in _pick-Databanken legen, erscheint hier der Stammbaum B1–B10."
                : "Der Stammbaum (B1–B10) wird erst gezeichnet, nachdem der Task "
                + "„01 Grid-Fundament — Diversität (B-Cluster)“ Cluster-IDs gestempelt hat.\n\n"
                + "Aktuell siehst du oben die Parameter-Timeline (1…N). Rechts die Details "
                + "zur gewählten Stufe — das schwarze Feld war kein kaputter Baum, "
                + "sondern einfach noch leer.");
        body.setWrapText(true);
        body.setMaxWidth(480);
        body.setTextAlignment(TextAlignment.CENTER);
        body.setStyle("-fx-text-fill: #9aa4b5; -fx-font-size: 13px;");

        Label hint = new Label("Reihenfolge: Optimizer → Shortlist → Tick-Gate → Diversität (B-Cluster) → dann Linienbaum");
        hint.setWrapText(true);
        hint.setMaxWidth(480);
        hint.setTextAlignment(TextAlignment.CENTER);
        hint.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");

        card.getChildren().addAll(head, body, hint);

        StackPane wrap = new StackPane(card);
        wrap.setAlignment(Pos.CENTER);
        wrap.setStyle("-fx-background-color: #10141e; -fx-border-color: #232a3b; "
                + "-fx-border-radius: 6; -fx-background-radius: 6;");
        wrap.setMinHeight(520);
        return wrap;
    }

    private static VBox placeholder(String title, String body) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(20));
        card.setStyle(
                "-fx-background-color: #141822; -fx-border-color: #2e3545; -fx-border-radius: 6; "
                        + "-fx-background-radius: 6;");
        Label head = new Label(title);
        head.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        head.setTextFill(Color.web("#90a4ae"));
        Label text = new Label(body);
        text.setWrapText(true);
        text.setStyle("-fx-text-fill: #78909c;");
        card.getChildren().addAll(head, text);
        return card;
    }

    private static String shortName(String name, int max) {
        if (name == null || name.isBlank()) return "—";
        String t = name.trim();
        return t.length() <= max ? t : t.substring(0, Math.max(1, max - 1)) + "…";
    }

    private static String blankDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String joinRange(String start, String step, String stop) {
        if ((start == null || start.isBlank())
                && (step == null || step.isBlank())
                && (stop == null || stop.isBlank())) {
            return "";
        }
        return nullToEmpty(start) + " / " + nullToEmpty(step) + " / " + nullToEmpty(stop);
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
