package com.backtester.ui.javafx;

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
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
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

        List<FlowNode> nodes = WorkflowHandoffAuditService.buildTimeline(project, databankManager);
        List<FlowStepSummary> stepSummaries = WorkflowFlowSummaryService.build(
                project, databankManager, outputDirectoryResolver);
        Map<Integer, FlowStepSummary> summaryByNumber = new HashMap<>();
        for (FlowStepSummary step : stepSummaries) {
            if (step != null) summaryByNumber.put(step.getIndex(), step);
        }

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(14));
        root.setStyle("-fx-background-color: #0b0d13;");
        root.setPrefSize(1280, 880);

        Label title = new Label("Show Flow");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        title.setTextFill(Color.web("#00e5ff"));

        String projectName = project != null && project.getName() != null ? project.getName() : "—";
        Label subtitle = new Label("Alle Workflow-Kacheln für „" + projectName
                + "“ — Nummerierung identisch zur Pipeline (1…N). Optimizer zeigen die Setfile-Übernahme.");
        subtitle.setWrapText(true);
        subtitle.setStyle("-fx-text-fill: #9aa4b5; -fx-font-size: 12px;");

        VBox detailHost = new VBox(12);
        detailHost.setPadding(new Insets(4, 0, 0, 0));

        HBox timeline = createTimeline(nodes, node -> detailHost.getChildren().setAll(
                createNodeDetail(node, summaryByNumber.get(node.getWorkflowNumber()))));

        VBox header = new VBox(8, title, subtitle, timeline);
        root.setTop(header);

        ScrollPane scroll = new ScrollPane(detailHost);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #0b0d13; -fx-background-color: #0b0d13;");
        BorderPane.setMargin(scroll, new Insets(12, 0, 10, 0));
        root.setCenter(scroll);

        if (nodes.isEmpty()) {
            detailHost.getChildren().setAll(placeholder("Kein Flow", "Dieses Projekt hat keine Tasks."));
        } else {
            FlowNode initial = nodes.stream()
                    .filter(FlowNode::hasHandoff)
                    .findFirst()
                    .orElse(nodes.get(0));
            detailHost.getChildren().setAll(
                    createNodeDetail(initial, summaryByNumber.get(initial.getWorkflowNumber())));
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

    private static VBox createNodeDetail(FlowNode node, FlowStepSummary summary) {
        VBox detail = node.hasHandoff() ? createHandoffBoard(node) : createGenericTaskBoard(node);
        if (summary != null) {
            detail.getChildren().add(1, createNarrativeCard(summary));
        }
        return detail;
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
