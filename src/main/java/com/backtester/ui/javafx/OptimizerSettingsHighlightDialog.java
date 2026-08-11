package com.backtester.ui.javafx;

import com.backtester.config.EaParameter;
import com.backtester.config.EaParameterManager;
import com.backtester.workflow.DatabankManager;
import com.backtester.workflow.FilterGateAnalysisService;
import com.backtester.workflow.FilterGateAnalysisService.FilterGateAnalysis;
import com.backtester.workflow.FilterGateAnalysisService.Verdict;
import com.backtester.workflow.ToTheMoon132GuidedWorkflowFactory;
import com.backtester.workflow.WorkflowTask;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Non-modal companion window: full EA setting with the current optimizer stage's
 * target parameters highlighted green on the left, and Filter-Nutzen extract +
 * next-step ON/OFF decision on the right.
 */
public final class OptimizerSettingsHighlightDialog {

    private static final String HIGHLIGHT_STYLE =
            "-fx-background-color: rgba(0, 230, 118, 0.28); -fx-border-color: #00e676; "
                    + "-fx-border-width: 0 0 0 4;";
    private static final String SECTION_STYLE =
            "-fx-background-color: #152238; -fx-font-weight: bold;";

    private final Stage stage;
    private final Label titleLabel;
    private final Label subtitleLabel;
    private final Label countBadge;
    private final TextField filterField;
    private final TableView<EaParameter> table;
    private final StackPane analysisPane;
    private final ObservableList<EaParameter> masterItems = FXCollections.observableArrayList();
    private final FilteredList<EaParameter> filteredItems = new FilteredList<>(masterItems, p -> true);
    private final Set<String> highlightNames = new HashSet<>();

    private OptimizerSettingsHighlightDialog(Window owner) {
        stage = new Stage();
        stage.setTitle("Optimizer-Setting — betroffene Parameter");
        stage.initModality(Modality.NONE);
        if (owner != null) stage.initOwner(owner);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(14));
        root.setStyle("-fx-background-color: #0b0d13;");
        root.setPrefSize(1480, 760);

        titleLabel = new Label("Optimizer-Setting");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 17));
        titleLabel.setTextFill(Color.web("#00e5ff"));

        subtitleLabel = new Label();
        subtitleLabel.setWrapText(true);
        subtitleLabel.setStyle("-fx-text-fill: #9aa4b5; -fx-font-size: 12px;");

        countBadge = new Label();
        countBadge.setStyle(
                "-fx-background-color: rgba(0, 230, 118, 0.15); -fx-text-fill: #00e676; "
                        + "-fx-font-weight: bold; -fx-padding: 4 10 4 10; -fx-background-radius: 4;");

        HBox headerRow = new HBox(12, titleLabel, countBadge);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        Label legend = new Label(
                "Links: Setting mit grünen Zielparametern  ·  Rechts: Filter-Nutzen-Extrakt & Entscheidung für nächsten Step");
        legend.setStyle("-fx-text-fill: #69f0ae; -fx-font-size: 12px;");

        filterField = new TextField();
        filterField.setPromptText("Suche Parameter / Sektion…");
        filterField.setStyle(
                "-fx-background-color: #141822; -fx-text-fill: #00e5ff; -fx-border-color: #232a3b;");
        filterField.textProperty().addListener((obs, o, n) -> applyFilter());

        VBox top = new VBox(8, headerRow, subtitleLabel, legend, filterField);
        root.setTop(top);

        table = new TableView<>(filteredItems);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setStyle(
                "-fx-background-color: #141822; -fx-control-inner-background: #141822; "
                        + "-fx-table-cell-border-color: #232a3b; -fx-text-fill: #e6e9f0;");
        table.setPlaceholder(new Label("Keine Parameter geladen"));

        TableColumn<EaParameter, Boolean> optCol = new TableColumn<>("Opt");
        optCol.setPrefWidth(48);
        optCol.setMaxWidth(56);
        optCol.setCellValueFactory(c -> new SimpleBooleanProperty(
                c.getValue() != null && c.getValue().isOptimizeEnabled()).asObject());

        TableColumn<EaParameter, String> nameCol = new TableColumn<>("Parameter");
        nameCol.setPrefWidth(260);
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue() != null ? c.getValue().getName() : ""));

        TableColumn<EaParameter, String> valueCol = new TableColumn<>("Wert");
        valueCol.setPrefWidth(120);
        valueCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue() != null ? nullToEmpty(c.getValue().getValue()) : ""));

        TableColumn<EaParameter, String> startCol = new TableColumn<>("Start");
        startCol.setPrefWidth(90);
        startCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue() != null ? nullToEmpty(c.getValue().getOptimizeStart()) : ""));

        TableColumn<EaParameter, String> stepCol = new TableColumn<>("Step");
        stepCol.setPrefWidth(70);
        stepCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue() != null ? nullToEmpty(c.getValue().getOptimizeStep()) : ""));

        TableColumn<EaParameter, String> stopCol = new TableColumn<>("Stop");
        stopCol.setPrefWidth(90);
        stopCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue() != null ? nullToEmpty(c.getValue().getOptimizeEnd()) : ""));

        table.getColumns().setAll(optCol, nameCol, valueCol, startCol, stepCol, stopCol);
        EaParameterTableHelper.configureTable(table, optCol, nameCol, valueCol, startCol, stepCol, stopCol);

        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(EaParameter item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("section-header-row", "optimizer-target-row");
                if (empty || item == null) {
                    setStyle("");
                    return;
                }
                if (item.isSectionHeader()) {
                    setStyle(SECTION_STYLE);
                    if (!getStyleClass().contains("section-header-row")) {
                        getStyleClass().add("section-header-row");
                    }
                    return;
                }
                if (isHighlighted(item)) {
                    setStyle(HIGHLIGHT_STYLE);
                    if (!getStyleClass().contains("optimizer-target-row")) {
                        getStyleClass().add("optimizer-target-row");
                    }
                } else {
                    setStyle("");
                }
            }
        });

        optCol.setEditable(false);
        optCol.setCellFactory(tc -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                EaParameter param = getTableRow() != null ? getTableRow().getItem() : null;
                if (empty || param == null || param.isSectionHeader()) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(param.isOptimizeEnabled() || isHighlighted(param) ? "●" : "");
                setAlignment(Pos.CENTER);
                setStyle(isHighlighted(param)
                        ? "-fx-text-fill: #00e676; -fx-font-weight: bold;"
                        : "-fx-text-fill: #546e7a;");
            }
        });

        VBox left = new VBox(8);
        Label leftTitle = new Label("Setting / Suchraum");
        leftTitle.setStyle("-fx-text-fill: #00e5ff; -fx-font-weight: bold;");
        left.getChildren().addAll(leftTitle, table);
        VBox.setVgrow(table, Priority.ALWAYS);

        analysisPane = new StackPane();
        analysisPane.setPadding(new Insets(0, 0, 0, 4));
        analysisPane.getChildren().setAll(createAnalysisPlaceholder(
                "Filter-Nutzen-Analyse", "Wähle eine Optimizer-Kachel mit Report."));

        VBox right = new VBox(8);
        Label rightTitle = new Label("Filter-Nutzen · Extrakt & nächster Step");
        rightTitle.setStyle("-fx-text-fill: #00e5ff; -fx-font-weight: bold;");
        ScrollPane analysisScroll = new ScrollPane(analysisPane);
        analysisScroll.setFitToWidth(true);
        analysisScroll.setStyle("-fx-background: #0b0d13; -fx-background-color: #0b0d13;");
        VBox.setVgrow(analysisScroll, Priority.ALWAYS);
        right.getChildren().addAll(rightTitle, analysisScroll);

        SplitPane split = new SplitPane(left, right);
        split.setDividerPositions(0.48);
        split.setStyle("-fx-background-color: #0b0d13;");
        root.setCenter(split);
        BorderPane.setMargin(split, new Insets(12, 0, 0, 0));

        Scene scene = new Scene(root);
        if (owner != null && owner.getScene() != null && !owner.getScene().getStylesheets().isEmpty()) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        stage.setScene(scene);
    }

    /**
     * Shows the window if needed and refreshes content for the selected task.
     */
    public static OptimizerSettingsHighlightDialog showOrRefresh(
            OptimizerSettingsHighlightDialog existing,
            Window owner,
            WorkflowTask task,
            List<EaParameter> fallbackEaParameters,
            String optimizerOutputDirectory,
            DatabankManager databankManager) {
        OptimizerSettingsHighlightDialog dialog = existing;
        if (dialog == null || dialog.stage == null || !dialog.stage.isShowing()) {
            dialog = new OptimizerSettingsHighlightDialog(owner);
        }
        dialog.refresh(task, fallbackEaParameters, optimizerOutputDirectory, databankManager);
        if (!dialog.stage.isShowing()) {
            dialog.stage.show();
        }
        return dialog;
    }

    public boolean isShowing() {
        return stage != null && stage.isShowing();
    }

    public void close() {
        if (stage != null) stage.close();
    }

    private void refresh(WorkflowTask task,
                         List<EaParameter> fallbackEaParameters,
                         String optimizerOutputDirectory,
                         DatabankManager databankManager) {
        highlightNames.clear();
        if (filterField != null && filterField.getText() != null && !filterField.getText().isEmpty()) {
            filterField.setText("");
        }

        if (task == null) {
            masterItems.setAll(List.of());
            filteredItems.setPredicate(p -> true);
            table.getSelectionModel().clearSelection();
            table.refresh();
            titleLabel.setText("Kein Task ausgewählt");
            subtitleLabel.setText("Wähle eine Optimizer-Kachel im Workflow.");
            countBadge.setText("0 markiert");
            stage.setTitle("Optimizer-Setting — betroffene Parameter");
            setAnalysisContent(createAnalysisPlaceholder(
                    "Keine Auswahl", "Klicke eine Optimizer-Kachel."));
            return;
        }

        titleLabel.setText(task.getName());
        stage.setTitle("Optimizer-Setting — " + task.getName());

        List<EaParameter> display = resolveDisplayParameters(task, fallbackEaParameters);
        Set<String> targets = resolveHighlightNames(task, display);
        highlightNames.addAll(targets);

        EaParameterManager paramManager = new EaParameterManager();
        final List<EaParameter> rows = paramManager.ensureSectionHeaders(display);

        masterItems.setAll(rows);
        filteredItems.setPredicate(p -> true);
        table.getSelectionModel().clearSelection();
        table.refresh();

        int marked = 0;
        for (EaParameter p : rows) {
            if (p != null && !p.isSectionHeader() && isHighlighted(p)) marked++;
        }
        countBadge.setText(marked + " markiert");

        if (task.getType() != WorkflowTask.TaskType.OPTIMIZER) {
            subtitleLabel.setText(
                    "Kein Optimizer-Task — keine Stufe markiert. Klicke eine Optimizer-Kachel.");
            setAnalysisContent(createAnalysisPlaceholder(
                    "Kein Optimizer", "Filter-Nutzen nur für Optimizer-Kacheln."));
        } else if (targets.isEmpty()) {
            subtitleLabel.setText("Optimizer-Task ohne Zielparameter.");
            refreshFilterAnalysisPanel(task, optimizerOutputDirectory, databankManager);
        } else {
            subtitleLabel.setText(
                    "Grün markiert (" + marked + "): " + String.join(", ", new ArrayList<>(targets)));
            refreshFilterAnalysisPanel(task, optimizerOutputDirectory, databankManager);
        }

        Platform.runLater(() -> {
            table.refresh();
            scrollToFirstHighlight(rows);
        });
    }

    private void refreshFilterAnalysisPanel(WorkflowTask task,
                                            String optimizerOutputDirectory,
                                            DatabankManager databankManager) {
        try {
            FilterGateAnalysisService.PassLoadResult loaded = FilterGateAnalysisService.loadPassesForTask(
                    task, optimizerOutputDirectory, databankManager);
            if (loaded.getPasses().isEmpty()) {
                setAnalysisContent(createAnalysisPlaceholder(
                        "Keine Optimizer-Passes",
                        "Noch kein Report/keine Databank-Strategien für diese Stufe."));
                return;
            }
            List<String> candidates = FilterGateAnalysisService.listGateParameterCandidates(
                    task, loaded.getPasses());
            List<String> optimized = FilterGateAnalysisService.listOptimizedParameterNames(
                    task, loaded.getPasses());
            String gate = candidates.isEmpty() ? "" : candidates.get(0);
            FilterGateAnalysis analysis = FilterGateAnalysisService.analyze(
                    loaded.getPasses(),
                    gate,
                    loaded.getDataSource(),
                    loaded.getSourcePath(),
                    loaded.getDatabankName(),
                    FilterGateAnalysisService.DEFAULT_MIN_COHORT_SIZE,
                    FilterGateAnalysisService.DEFAULT_TOP_N,
                    FilterGateAnalysisService.DEFAULT_SCORE_MARGIN,
                    candidates,
                    optimized);

            if (analysis.getVerdict() == Verdict.GATE_MISSING) {
                setAnalysisContent(createGateMissingAnalysis(analysis));
                return;
            }

            VBox chartDecision = FilterGateAnalysisDialog.createChartAndDecisionColumn(analysis);
            setAnalysisContent(chartDecision);
        } catch (RuntimeException ex) {
            setAnalysisContent(createAnalysisPlaceholder(
                    "Analyse fehlgeschlagen",
                    ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()));
        }
    }

    private void setAnalysisContent(Node node) {
        analysisPane.getChildren().setAll(node);
    }

    private static VBox createGateMissingAnalysis(FilterGateAnalysis analysis) {
        VBox box = new VBox(12);
        box.setPadding(new Insets(12));
        Label head = new Label("Kein Use_*-Schalter");
        head.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        head.setTextFill(Color.web("#64b5f6"));
        Label body = new Label(analysis.getVerdictMessage());
        body.setWrapText(true);
        body.setStyle("-fx-text-fill: #bbdefb; -fx-font-size: 12px;");
        FilterGateAnalysisDialog.NextStepDecision decision =
                FilterGateAnalysisDialog.decideNextStepFilter(analysis);
        Label badge = new Label(decision.badgeText());
        badge.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        badge.setTextFill(Color.web(decision.color()));
        badge.setPadding(new Insets(10, 14, 10, 14));
        badge.setMaxWidth(Double.MAX_VALUE);
        badge.setAlignment(Pos.CENTER);
        badge.setStyle("-fx-background-color: " + decision.background()
                + "; -fx-border-color: " + decision.color()
                + "; -fx-border-width: 2; -fx-border-radius: 6; -fx-background-radius: 6;");
        Label advice = new Label(decision.adviceText(analysis.getGateParameter()));
        advice.setWrapText(true);
        advice.setStyle("-fx-text-fill: #cfd8dc; -fx-font-size: 12px;");
        box.getChildren().addAll(head, body, badge, advice);
        return box;
    }

    private static VBox createAnalysisPlaceholder(String title, String detail) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(16));
        box.setStyle(
                "-fx-background-color: rgba(26, 30, 40, 0.9); -fx-border-color: #2e3545; "
                        + "-fx-border-radius: 6; -fx-background-radius: 6;");
        Label head = new Label(title);
        head.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        head.setTextFill(Color.web("#90a4ae"));
        Label body = new Label(detail != null ? detail : "");
        body.setWrapText(true);
        body.setStyle("-fx-text-fill: #78909c; -fx-font-size: 12px;");
        box.getChildren().addAll(head, body);
        return box;
    }

    static List<EaParameter> resolveDisplayParameters(WorkflowTask task,
                                                      List<EaParameter> fallbackEaParameters) {
        Set<String> highlightTargets = resolveHighlightNames(task, null);
        List<EaParameter> snapshot = task != null ? task.getOptimizerParameterSnapshot() : List.of();
        if (snapshot != null && !snapshot.isEmpty()) {
            List<EaParameter> copy = new ArrayList<>(snapshot.size());
            for (EaParameter p : snapshot) {
                if (p != null) copy.add(p.copy());
            }
            // Always paint Opt flags from the resolved stage targets, never from a stale
            // live-EA optimizeEnabled state that may still reflect a later stage.
            if (!highlightTargets.isEmpty()) {
                return applyOptimizeFlags(copy, new ArrayList<>(highlightTargets));
            }
            return copy;
        }
        List<EaParameter> fallback = fallbackEaParameters != null ? fallbackEaParameters : List.of();
        List<EaParameter> copy = new ArrayList<>(fallback.size());
        for (EaParameter p : fallback) {
            if (p != null) copy.add(p.copy());
        }
        if (!highlightTargets.isEmpty()) {
            return applyOptimizeFlags(copy, new ArrayList<>(highlightTargets));
        }
        // No stage targets known: clear all Opt flags so a later stage's live EA
        // cannot paint green rows onto an unrelated tile.
        return applyOptimizeFlags(copy, List.of());
    }

    static List<EaParameter> applyOptimizeFlags(List<EaParameter> parameters, List<String> targetNames) {
        Set<String> targets = new HashSet<>();
        if (targetNames != null) {
            for (String name : targetNames) {
                if (name != null && !name.isBlank()) targets.add(name.trim().toLowerCase(Locale.ROOT));
            }
        }
        List<EaParameter> result = new ArrayList<>();
        if (parameters == null) return result;
        for (EaParameter source : parameters) {
            if (source == null) continue;
            EaParameter copy = source.copy();
            String name = copy.getName() != null ? copy.getName().trim().toLowerCase(Locale.ROOT) : "";
            copy.setOptimizeEnabled(!name.isEmpty() && targets.contains(name));
            result.add(copy);
        }
        return result;
    }

    /**
     * Highlight names for a task. Guided ToTheMoon132 stage titles are authoritative
     * so a stale live-EA / wrong stored target list cannot paint every tile like the
     * last optimized stage. Non-guided optimizers still use stored targets.
     */
    static Set<String> resolveHighlightNames(WorkflowTask task, List<EaParameter> displayIgnored) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (task == null || task.getType() != WorkflowTask.TaskType.OPTIMIZER) {
            return names;
        }
        Optional<List<String>> guided = ToTheMoon132GuidedWorkflowFactory
                .resolveStageTargetsForTaskName(task.getName());
        if (guided.isPresent() && !guided.get().isEmpty()) {
            names.addAll(guided.get());
            return names;
        }
        for (String name : task.getOptimizerTargetParameters()) {
            if (name != null && !name.isBlank()) names.add(name.trim());
        }
        return names;
    }

    private boolean isHighlighted(EaParameter param) {
        if (param == null || param.isSectionHeader() || param.getName() == null) return false;
        String name = param.getName().trim();
        for (String highlighted : highlightNames) {
            if (highlighted.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private void applyFilter() {
        String q = filterField.getText() != null
                ? filterField.getText().trim().toLowerCase(Locale.ROOT) : "";
        if (q.isEmpty()) {
            filteredItems.setPredicate(p -> true);
            return;
        }
        filteredItems.setPredicate(p -> {
            if (p == null) return false;
            if (p.isSectionHeader()) {
                String title = p.getFormattedSectionTitle();
                return title != null && title.toLowerCase(Locale.ROOT).contains(q);
            }
            String name = p.getName() != null ? p.getName().toLowerCase(Locale.ROOT) : "";
            String section = p.getSection() != null ? p.getSection().toLowerCase(Locale.ROOT) : "";
            String value = p.getValue() != null ? p.getValue().toLowerCase(Locale.ROOT) : "";
            return name.contains(q) || section.contains(q) || value.contains(q);
        });
    }

    private void scrollToFirstHighlight(List<EaParameter> display) {
        if (display == null) return;
        for (int i = 0; i < display.size(); i++) {
            EaParameter p = display.get(i);
            if (p != null && !p.isSectionHeader() && isHighlighted(p)) {
                // Index in filtered list may differ; search there.
                for (int f = 0; f < filteredItems.size(); f++) {
                    EaParameter fp = filteredItems.get(f);
                    if (fp != null && fp.getName() != null
                            && fp.getName().equalsIgnoreCase(p.getName())) {
                        table.getSelectionModel().select(f);
                        table.scrollTo(Math.max(0, f - 2));
                        return;
                    }
                }
                return;
            }
        }
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
