package com.backtester.ui.javafx;

import com.backtester.config.AppConfig;
import com.backtester.database.DatabaseManager;
import com.backtester.report.OptimizationDateRangeResolver;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.workflow.CustomProject;
import com.backtester.workflow.DatabankManager;
import com.backtester.workflow.GuidedOptimizationService;
import com.backtester.workflow.Mt5OptimizationImportService;
import com.backtester.workflow.WorkflowTask;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Bottom databank TabPane + toolbar for {@link ProjectWorkflowEditorView}.
 * Owns clear/delete/refresh UI; editor supplies project/save/adopt hooks via {@link Host}.
 */
public class ProjectWorkflowDatabankPanel {

    /**
     * Dependencies owned by the workflow editor (combos, save, adopt, invalidate).
     */
    public interface Host {
        CustomProject getProject();

        WorkflowTask getSelectedTask();

        Window getOwnerWindow();

        List<String> getStylesheets();

        void saveProject();

        void flushProjectSaveAsync(Runnable continuation);

        void refreshTaskChain();

        void logToConsole(String tag, String message);

        /** Reset task statuses / progress UI after databank wipe or adoption-touching delete. */
        void invalidateWorkflowResultsAfterDatabankClear(boolean allDatabanks, String databankName);

        /**
         * Full wipe: MT5 tester cache, leftover OptimizationReport files, and
         * optimizer output folders for this project (in addition to databank clear).
         */
        void purgeWorkflowRunArtifacts();

        void adoptPassParameters(CombinedPass pass, String dbName);

        long findSensitivityRunTimestampForDatabank(String databankName);

        void backupProject();

        void restoreProject();

        ComboBox<String> getSourceDatabankCombo();

        ComboBox<String> getTargetDatabankCombo();

        ComboBox<String> getRankingSourceCombo();

        ComboBox<String> getRankingTargetCombo();

        /**
         * Re-load the selected task into the right-hand form (dates, combos, …)
         * after the model was changed externally (e.g. MT5 opti import).
         */
        void reloadSelectedTaskForm();

        /**
         * After applying settings externally to a task (e.g. import dates), adopt the
         * new execution signature so the next save does not wipe that task's target.
         */
        void acknowledgeTaskExecutionSignature(WorkflowTask task);

        /**
         * Runs an action while downstream databank invalidation is suppressed (MT5 import).
         */
        void runSuppressingDownstreamInvalidation(Runnable action);
    }

    private final DatabankManager databankManager;
    private final Host host;

    private final VBox root;
    private TabPane bottomDatabankTabPane;
    private Pane databankToolbar;
    private CheckBox persistDatabanksCheckBox;
    private Button importMt5OptiBtn;
    private Label parameterAdoptionBanner;
    private boolean rebuildingDatabankTabs;
    /** Task-linked databanks: yellow tab headers; focus prefers output. */
    private String highlightedSourceDatabank;
    private String highlightedTargetDatabank;

    private static final String TAB_HIGHLIGHT_CLASS = "databank-task-highlight";

    /** Fixed row height keeps VirtualFlow from inserting blank gaps after rebuilds. */
    private static final double DATABANK_ROW_HEIGHT = 28.0;

    public ProjectWorkflowDatabankPanel(DatabankManager databankManager, Host host) {
        this.databankManager = databankManager;
        this.host = host;
        this.root = buildPanel();
    }

    public VBox getNode() {
        return root;
    }

    public void setLocked(boolean locked) {
        if (bottomDatabankTabPane != null) bottomDatabankTabPane.setDisable(locked);
        if (databankToolbar != null) databankToolbar.setDisable(locked);
    }

    public void syncPersistCheckboxFromProject() {
        CustomProject project = host.getProject();
        if (persistDatabanksCheckBox != null && project != null) {
            persistDatabanksCheckBox.setSelected(project.isSaveDatabanksPersistently());
        }
    }

    public void showParameterAdoptionBanner(String message, boolean success) {
        if (parameterAdoptionBanner == null) return;
        parameterAdoptionBanner.setText((success ? "✓ " : "⚠ ") + message);
        parameterAdoptionBanner.setStyle("-fx-background-color: "
                + (success ? "rgba(76, 175, 80, 0.24)" : "rgba(255, 82, 82, 0.24)")
                + "; -fx-border-color: " + (success ? "#76ff03" : "#ff5252")
                + "; -fx-border-radius: 4; -fx-background-radius: 4; -fx-text-fill: #ffffff; -fx-font-weight: bold;");
        parameterAdoptionBanner.setManaged(true);
        parameterAdoptionBanner.setVisible(true);
        PauseTransition hide = new PauseTransition(javafx.util.Duration.seconds(8));
        hide.setOnFinished(e -> {
            parameterAdoptionBanner.setVisible(false);
            parameterAdoptionBanner.setManaged(false);
        });
        hide.play();
    }

    public void refreshDatabanksUI() {
        refreshDatabanksUI(null);
    }

    /**
     * Marks the task's input/output databank tabs yellow and selects the output
     * tab (or input if output is blank). Empty names clear the highlight.
     */
    public void focusTaskDatabanks(String sourceDatabank, String targetDatabank) {
        highlightedSourceDatabank = blankToNull(sourceDatabank);
        highlightedTargetDatabank = blankToNull(targetDatabank);
        if (highlightedSourceDatabank != null) {
            databankManager.createDatabank(highlightedSourceDatabank);
        }
        if (highlightedTargetDatabank != null) {
            databankManager.createDatabank(highlightedTargetDatabank);
        }
        String focus = highlightedTargetDatabank != null
                ? highlightedTargetDatabank
                : highlightedSourceDatabank;
        refreshDatabanksUI(focus);
    }

    public void clearTaskDatabankHighlight() {
        highlightedSourceDatabank = null;
        highlightedTargetDatabank = null;
        applyTaskDatabankTabStyles();
    }

    public void refreshDatabanksUI(String targetTabToFocus) {
        if (bottomDatabankTabPane == null) return;

        Tab currentTab = bottomDatabankTabPane.getSelectionModel().getSelectedItem();
        String activeDbName = targetTabToFocus != null ? targetTabToFocus : (currentTab != null ? currentTab.getText().replaceAll("\\s*\\(\\d+\\)$", "") : null);

        rebuildingDatabankTabs = true;
        try {
            bottomDatabankTabPane.getTabs().clear();
            Tab tabToSelect = null;

            Set<DatabankColumnChooserDialog.DatabankColumn> visibleCols = DatabankColumnChooserDialog.getVisibleColumns();

            for (String dbName : databankManager.getDatabankNames()) {
                boolean isStandard = dbName.equalsIgnoreCase("Results") || dbName.equalsIgnoreCase("Existing portfolio") || dbName.equalsIgnoreCase("Final");
                List<CombinedPass> passes = databankManager.getDatabank(dbName);
                Tab tab = new Tab(dbName + " (" + passes.size() + ")");
                tab.setClosable(!isStandard);
                tab.setUserData(dbName);
                tab.setOnCloseRequest(e -> {
                    e.consume();
                    deleteDatabankByName(dbName);
                });

                TableView<CombinedPass> table = createDatabankTable(dbName, passes, visibleCols);
                tab.setContent(table);
                bottomDatabankTabPane.getTabs().add(tab);

                if (activeDbName != null && dbName.equalsIgnoreCase(activeDbName)) {
                    tabToSelect = tab;
                }
            }

            applyTaskDatabankTabStyles();

            if (tabToSelect != null) {
                bottomDatabankTabPane.getSelectionModel().select(tabToSelect);
                ensureTabHeaderVisible(tabToSelect);
            }
        } finally {
            rebuildingDatabankTabs = false;
        }

        // TableViews rebuilt inside a SplitPane often get a broken VirtualFlow until
        // the next tab switch; force the same repair that a manual tab change triggers.
        Platform.runLater(() -> repairVisibleDatabankTable(true));
    }

    private void applyTaskDatabankTabStyles() {
        if (bottomDatabankTabPane == null) {
            return;
        }
        for (Tab tab : bottomDatabankTabPane.getTabs()) {
            String dbName = databankNameOf(tab);
            boolean highlight = dbName != null && (
                    (highlightedSourceDatabank != null && dbName.equalsIgnoreCase(highlightedSourceDatabank))
                            || (highlightedTargetDatabank != null && dbName.equalsIgnoreCase(highlightedTargetDatabank)));
            tab.getStyleClass().remove(TAB_HIGHLIGHT_CLASS);
            if (highlight) {
                tab.getStyleClass().add(TAB_HIGHLIGHT_CLASS);
            }
        }
    }

    private void ensureTabHeaderVisible(Tab tab) {
        if (tab == null || bottomDatabankTabPane == null) {
            return;
        }
        Platform.runLater(() -> {
            // Selecting already scrolls most TabPanes; re-select after layout for long tab strips.
            if (bottomDatabankTabPane.getTabs().contains(tab)) {
                bottomDatabankTabPane.getSelectionModel().select(tab);
            }
        });
    }

    private static String databankNameOf(Tab tab) {
        if (tab == null) {
            return null;
        }
        if (tab.getUserData() instanceof String name && !name.isBlank()) {
            return name;
        }
        String text = tab.getText();
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.replaceAll("\\s*\\(\\d+\\)$", "");
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private TableView<CombinedPass> createDatabankTable(String dbName,
                                                         List<CombinedPass> passes,
                                                         Set<DatabankColumnChooserDialog.DatabankColumn> visibleCols) {
            TableView<CombinedPass> table = new TableView<>();
            table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            table.setFixedCellSize(DATABANK_ROW_HEIGHT);
            table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
            // SortedList + bound comparator: column-header clicks stay reliable with NaN metrics.
            ObservableList<CombinedPass> rowItems = FXCollections.observableArrayList(passes);
            SortedList<CombinedPass> sortedRows = new SortedList<>(rowItems);
            sortedRows.comparatorProperty().bind(table.comparatorProperty());
            table.setItems(sortedRows);
            attachVirtualFlowRepairTriggers(table);

            table.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.DELETE) {
                    deleteSelectedRowsFromDatabank(dbName, table);
                }
            });

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.NAME)) {
                TableColumn<CombinedPass, String> nameCol = new TableColumn<>("Strategy Name");
                nameCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getStrategyName()));
                nameCol.setComparator(Comparator.nullsLast(String::compareToIgnoreCase));
                nameCol.setPrefWidth(130);
                table.getColumns().add(nameCol);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.PASS)) {
                TableColumn<CombinedPass, Integer> passCol = new TableColumn<>("Pass");
                passCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getPassNumber()));
                passCol.setComparator(Comparator.nullsLast(Integer::compareTo));
                passCol.setPrefWidth(60);
                table.getColumns().add(passCol);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.SCORE)) {
                TableColumn<CombinedPass, Double> scoreCol = new TableColumn<>("Score");
                scoreCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getScore()));
                scoreCol.setComparator(nanSafeDoubleComparator());
                scoreCol.setPrefWidth(70);
                table.getColumns().add(scoreCol);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.BT_PROFIT)) {
                TableColumn<CombinedPass, Double> btProf = new TableColumn<>("BT Backtest");
                btProf.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getBtBacktestBalance()));
                btProf.setComparator(nanSafeDoubleComparator());
                btProf.setPrefWidth(100);
                table.getColumns().add(btProf);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.FW_PROFIT)) {
                TableColumn<CombinedPass, Double> fwProf = new TableColumn<>("FW Forward");
                fwProf.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getFwForwardBalance()));
                fwProf.setComparator(nanSafeDoubleComparator());
                fwProf.setPrefWidth(100);
                table.getColumns().add(fwProf);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.LT_PROFIT)) {
                TableColumn<CombinedPass, Double> ltProf = new TableColumn<>("LT Profit");
                ltProf.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getLtProfit()));
                ltProf.setComparator(nanSafeDoubleComparator());
                ltProf.setPrefWidth(90);
                table.getColumns().add(ltProf);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.BT_PF)) {
                TableColumn<CombinedPass, String> btPf = new TableColumn<>("BT Profit Factor");
                btPf.setCellValueFactory(c -> {
                    double v = c.getValue().getBtPf();
                    return new SimpleStringProperty(Double.isNaN(v) || v <= 0 ? "-" : String.format(Locale.US, "%.2f", v));
                });
                btPf.setComparator(OptimizationCombinedPanel.numericStringComparator());
                btPf.setPrefWidth(115);
                table.getColumns().add(btPf);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.FW_PF)) {
                TableColumn<CombinedPass, String> fwPf = new TableColumn<>("FW Profit Factor");
                fwPf.setCellValueFactory(c -> {
                    double v = c.getValue().getFwPf();
                    return new SimpleStringProperty(Double.isNaN(v) || v <= 0 ? "-" : String.format(Locale.US, "%.2f", v));
                });
                fwPf.setComparator(OptimizationCombinedPanel.numericStringComparator());
                fwPf.setPrefWidth(115);
                table.getColumns().add(fwPf);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.LT_PF)) {
                TableColumn<CombinedPass, String> ltPf = new TableColumn<>("LT Profit Factor");
                ltPf.setCellValueFactory(c -> {
                    double v = c.getValue().getLtPf();
                    return new SimpleStringProperty(Double.isNaN(v) || v <= 0 ? "-" : String.format(Locale.US, "%.2f", v));
                });
                ltPf.setComparator(OptimizationCombinedPanel.numericStringComparator());
                ltPf.setPrefWidth(115);
                table.getColumns().add(ltPf);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.BT_DD)) {
                TableColumn<CombinedPass, Double> btDd = new TableColumn<>("BT DD %");
                btDd.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getBtDd()));
                btDd.setComparator(nanSafeDoubleComparator());
                btDd.setPrefWidth(80);
                table.getColumns().add(btDd);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.FW_DD)) {
                TableColumn<CombinedPass, Double> fwDd = new TableColumn<>("FW DD %");
                fwDd.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getFwDd()));
                fwDd.setComparator(nanSafeDoubleComparator());
                fwDd.setPrefWidth(80);
                table.getColumns().add(fwDd);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.LT_DD)) {
                TableColumn<CombinedPass, Double> ltDd = new TableColumn<>("LT DD %");
                ltDd.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getLtDd()));
                ltDd.setComparator(nanSafeDoubleComparator());
                ltDd.setPrefWidth(80);
                table.getColumns().add(ltDd);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.BT_TRADES)) {
                TableColumn<CombinedPass, Integer> btTr = new TableColumn<>("BT Trades");
                btTr.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getBtTrades()));
                btTr.setComparator(Comparator.nullsLast(Integer::compareTo));
                btTr.setPrefWidth(75);
                table.getColumns().add(btTr);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.FW_TRADES)) {
                TableColumn<CombinedPass, Integer> fwTr = new TableColumn<>("FW Trades");
                fwTr.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getFwTrades()));
                fwTr.setComparator(Comparator.nullsLast(Integer::compareTo));
                fwTr.setPrefWidth(75);
                table.getColumns().add(fwTr);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.LT_TRADES)) {
                TableColumn<CombinedPass, Integer> ltTr = new TableColumn<>("LT Trades");
                ltTr.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getLtTrades()));
                ltTr.setComparator(Comparator.nullsLast(Integer::compareTo));
                ltTr.setPrefWidth(75);
                table.getColumns().add(ltTr);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.BT_SHARPE)) {
                TableColumn<CombinedPass, Double> btSh = new TableColumn<>("BT Sharpe");
                btSh.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getBtSharpe()));
                btSh.setComparator(nanSafeDoubleComparator());
                btSh.setPrefWidth(80);
                table.getColumns().add(btSh);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.FW_SHARPE)) {
                TableColumn<CombinedPass, Double> fwSh = new TableColumn<>("FW Sharpe");
                fwSh.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getFwSharpe()));
                fwSh.setComparator(nanSafeDoubleComparator());
                fwSh.setPrefWidth(80);
                table.getColumns().add(fwSh);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.BT_RECOVERY)) {
                TableColumn<CombinedPass, Double> btRec = new TableColumn<>("BT Rec");
                btRec.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getBtRecovery()));
                btRec.setComparator(nanSafeDoubleComparator());
                btRec.setPrefWidth(80);
                table.getColumns().add(btRec);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.FW_RECOVERY)) {
                TableColumn<CombinedPass, Double> fwRec = new TableColumn<>("FW Rec");
                fwRec.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getFwRecovery()));
                fwRec.setComparator(nanSafeDoubleComparator());
                fwRec.setPrefWidth(80);
                table.getColumns().add(fwRec);
            }

            // Robustness actions are scoped to the exact run that produced this databank.
            final long databankSensitivityTimestamp = host.findSensitivityRunTimestampForDatabank(dbName);

            // Context Menu & Row click handlers
            table.setRowFactory(tv -> {
                TableRow<CombinedPass> row = new TableRow<>() {
                    @Override
                    protected void updateItem(CombinedPass item, boolean empty) {
                        super.updateItem(item, empty);
                        getStyleClass().remove("databank-row-adopted");
                        setTooltip(null);
                        if (empty || item == null) {
                            return;
                        }
                        if (GuidedOptimizationService.isAdoptedBasisPass(host.getProject(), dbName, item)) {
                            getStyleClass().add("databank-row-adopted");
                            List<String> consumers = GuidedOptimizationService.adoptedBasisConsumerNames(
                                    host.getProject(), dbName, item);
                            String consumerText = consumers.isEmpty()
                                    ? "nächsten Optimizer"
                                    : String.join(", ", consumers);
                            setTooltip(new Tooltip("Hand-Pick übernommen → " + consumerText
                                    + " (Pass #" + item.getPassNumber() + ")"));
                        }
                    }
                };

                ContextMenu contextMenu = new ContextMenu();
                MenuItem inspectItem = new MenuItem("🔍 Details & EA Parameter anzeigen (Doppelklick)");
                inspectItem.setOnAction(e -> {
                    if (!row.isEmpty()) StrategyDetailsModalDialog.show(row.getItem(), dbName, host.getProject(), host.getOwnerWindow(), 0);
                });

                MenuItem singleBtItem = new MenuItem("▶ Einzel-Backtest im MetaTrader ausführen (Terminal bleibt offen)");
                singleBtItem.setOnAction(e -> {
                    if (!row.isEmpty()) SingleBacktestHelper.runSingleBacktestInMetaTrader(row.getItem(), dbName, host.getProject(), host.getOwnerWindow());
                });

                MenuItem sensitivityItem = new MenuItem("📈 Sensitivitäts-Kennlinien & Stresstest (Rechtsklick)");
                sensitivityItem.setOnAction(e -> {
                    if (!row.isEmpty()) StrategyDetailsModalDialog.show(
                            row.getItem(), dbName, host.getProject(), host.getOwnerWindow(), 3, databankSensitivityTimestamp);
                });

                MenuItem htmlReportItem = new MenuItem("🌐 HTML Robustness Scanner Report im Browser öffnen");
                htmlReportItem.setOnAction(e -> {
                    if (!row.isEmpty()) StrategyDetailsModalDialog.openRobustnessHtmlReport(
                            row.getItem(), databankSensitivityTimestamp);
                });

                MenuItem adoptParametersItem = new MenuItem("📌 Als Parameter-Basis für nächsten Task übernehmen");
                adoptParametersItem.setOnAction(e -> {
                    if (!row.isEmpty()) host.adoptPassParameters(row.getItem(), dbName);
                });

                MenuItem deleteItem = new MenuItem("🗑 Selektierte Strategie(n) löschen (Entf)");
                deleteItem.setOnAction(e -> deleteSelectedRowsFromDatabank(dbName, table));

                SeparatorMenuItem robustnessSeparator = new SeparatorMenuItem();
                SeparatorMenuItem adoptionSeparator = new SeparatorMenuItem();
                contextMenu.getItems().addAll(inspectItem, singleBtItem, sensitivityItem, htmlReportItem,
                        robustnessSeparator, adoptParametersItem, adoptionSeparator, deleteItem);
                contextMenu.setOnShowing(e -> {
                    boolean hasSensitivity = !row.isEmpty()
                            && DatabaseManager.getInstance().hasSensitivityDetails(
                                    databankSensitivityTimestamp,
                                    row.getItem().getPassNumber(),
                                    row.getItem().getStrategyName());
                    sensitivityItem.setVisible(hasSensitivity);
                    htmlReportItem.setVisible(hasSensitivity);
                    robustnessSeparator.setVisible(hasSensitivity);
                    boolean canAdopt = !row.isEmpty()
                            && GuidedOptimizationService.findNextActiveOptimizer(host.getProject(), dbName).isPresent();
                    adoptParametersItem.setVisible(canAdopt);
                    adoptionSeparator.setVisible(canAdopt);
                });

                row.setOnContextMenuRequested(event -> {
                    if (!row.isEmpty() && !row.isSelected()) {
                        table.getSelectionModel().clearAndSelect(row.getIndex());
                    }
                });

                row.setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2 && (!row.isEmpty())) {
                        CombinedPass rowData = row.getItem();
                        StrategyDetailsModalDialog.show(rowData, dbName, host.getProject(), host.getOwnerWindow(), 0);
                    }
                });

                row.contextMenuProperty().bind(
                    javafx.beans.binding.Bindings.when(row.emptyProperty())
                        .then((ContextMenu) null)
                        .otherwise(contextMenu)
                );
                return row;
            });

            return table;
    }

    private void attachVirtualFlowRepairTriggers(TableView<?> table) {
        table.heightProperty().addListener((obs, oldHeight, newHeight) -> {
            if (newHeight == null) return;
            double next = newHeight.doubleValue();
            double prev = oldHeight != null ? oldHeight.doubleValue() : 0.0;
            if (next > 40 && prev < 40) {
                Platform.runLater(() -> repairTableVirtualFlow(table));
            }
        });
        table.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene == null && newScene != null) {
                Platform.runLater(() -> repairTableVirtualFlow(table));
            }
        });
    }

    private void repairVisibleDatabankTable(boolean deep) {
        if (bottomDatabankTabPane == null || rebuildingDatabankTabs) return;
        Tab selected = bottomDatabankTabPane.getSelectionModel().getSelectedItem();
        if (selected == null || !(selected.getContent() instanceof TableView<?> table)) return;
        repairTableVirtualFlow(table);
        if (deep) {
            // Second pulse after SplitPane finishes assigning real height.
            Platform.runLater(() -> repairTableVirtualFlow(table));
        }
    }

    private static void repairTableVirtualFlow(TableView<?> table) {
        if (table == null) return;
        table.requestLayout();
        table.refresh();
    }

    public void updateDatabankComboBoxes() {
        List<String> names = databankManager.getDatabankNames();
        WorkflowTask selectedTask = host.getSelectedTask();
        if (selectedTask != null) {
            String taskSrc = selectedTask.getSourceDatabank();
            String taskTgt = selectedTask.getTargetDatabank();
            if (taskSrc != null && !taskSrc.isBlank() && !names.contains(taskSrc)) names.add(taskSrc);
            if (taskTgt != null && !taskTgt.isBlank() && !names.contains(taskTgt)) names.add(taskTgt);
        }
        ComboBox<String> sourceDatabankCombo = host.getSourceDatabankCombo();
        if (sourceDatabankCombo != null) {
            String currSrc = selectedTask != null ? selectedTask.getSourceDatabank() : sourceDatabankCombo.getValue();
            sourceDatabankCombo.getItems().setAll(names);
            if (currSrc != null && names.contains(currSrc)) sourceDatabankCombo.setValue(currSrc);
            else if (!names.isEmpty()) sourceDatabankCombo.setValue(names.get(0));
        }
        ComboBox<String> targetDatabankCombo = host.getTargetDatabankCombo();
        if (targetDatabankCombo != null) {
            String currTgt = selectedTask != null ? selectedTask.getTargetDatabank() : targetDatabankCombo.getValue();
            targetDatabankCombo.getItems().setAll(names);
            if (currTgt != null && names.contains(currTgt)) targetDatabankCombo.setValue(currTgt);
            else if (!names.isEmpty()) targetDatabankCombo.setValue(names.get(0));
        }
        ComboBox<String> rankingSourceCombo = host.getRankingSourceCombo();
        if (rankingSourceCombo != null) {
            String currSrc = selectedTask != null ? selectedTask.getSourceDatabank() : rankingSourceCombo.getValue();
            rankingSourceCombo.getItems().setAll(names);
            if (currSrc != null && names.contains(currSrc)) rankingSourceCombo.setValue(currSrc);
            else if (!names.isEmpty()) rankingSourceCombo.setValue(names.get(0));
        }
        ComboBox<String> rankingTargetCombo = host.getRankingTargetCombo();
        if (rankingTargetCombo != null) {
            String currTgt = selectedTask != null ? selectedTask.getTargetDatabank() : rankingTargetCombo.getValue();
            rankingTargetCombo.getItems().setAll(names);
            if (currTgt != null && names.contains(currTgt)) rankingTargetCombo.setValue(currTgt);
            else if (!names.isEmpty()) rankingTargetCombo.setValue(names.get(0));
        }
    }

    private VBox buildPanel() {
        VBox panel = new VBox(8);
        panel.setPadding(new Insets(8));
        panel.setStyle("-fx-background-color: rgba(15, 18, 26, 0.95); -fx-border-color: #00e5ff; -fx-border-width: 1 0 0 0;");
        panel.setMinHeight(160);
        panel.setPrefHeight(260);

        // Databank Header Toolbar
        FlowPane bar = new FlowPane(15, 8);
        bar.setAlignment(Pos.CENTER_LEFT);
        databankToolbar = bar;

        Button newDatabankBtn = new Button("+ New databank");
        newDatabankBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #00e5ff; -fx-font-weight: bold; -fx-cursor: hand;");
        newDatabankBtn.setOnAction(e -> promptCreateNewDatabank());

        Button clearCurrentDbBtn = new Button("🧹 Strategien in Databank leeren");
        clearCurrentDbBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ffab40; -fx-font-weight: bold; -fx-cursor: hand;");
        clearCurrentDbBtn.setOnAction(e -> {
            Tab activeTab = bottomDatabankTabPane.getSelectionModel().getSelectedItem();
            if (activeTab != null) {
                String dbName = activeTab.getText().replaceAll("\\s*\\(\\d+\\)$", "");
                if (!confirmDestructiveAction("Databank leeren",
                        "Alle Strategien aus '" + dbName + "' unwiderruflich entfernen?")) return;
                databankManager.clearDatabank(dbName);
                host.invalidateWorkflowResultsAfterDatabankClear(false, dbName);
                host.flushProjectSaveAsync(() -> {
                    host.refreshTaskChain();
                    refreshDatabanksUI(dbName);
                });
                host.logToConsole("DATABANK", "Alle Strategien aus Databank '" + dbName + "' wurden geleert.");
            }
        });

        Button clearAllBtn = new Button("Clear all databanks");
        clearAllBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ff5252; -fx-font-weight: bold; -fx-cursor: hand;");
        clearAllBtn.setTooltip(new Tooltip(
                "Leert alle Databanken und den Master-Strategie-Verlauf, setzt Task-Status zurück "
                        + "und löscht MT5-Optimizer-Cache sowie Report-Artefakte dieses Workflows."));
        clearAllBtn.setOnAction(e -> {
            if (!confirmDestructiveAction("Workflow komplett zurücksetzen",
                    "Wirklich ALLES für diesen Workflow löschen?\n\n"
                            + "• alle Strategien in allen Databanken\n"
                            + "• Task-Status / Adopted Bases → PENDING\n"
                            + "• Master-Strategie-Verlauf inkl. Profit/DD-Schwelle\n"
                            + "• MT5 Tester-Cache (*.opt) für diesen EA\n"
                            + "• OptimizationReport-Dateien in MT5\n"
                            + "• Optimizer-Ausgabeordner dieses Projekts\n\n"
                            + "Die Databank-Tabs bleiben erhalten.")) return;
            databankManager.clearAll();
            host.invalidateWorkflowResultsAfterDatabankClear(true, null);
            host.purgeWorkflowRunArtifacts();
            host.flushProjectSaveAsync(() -> {
                host.refreshTaskChain();
                refreshDatabanksUI();
            });
            host.logToConsole("DATABANK", "Alle Databanken, Task-Status und Workflow-Caches wurden geleert.");
        });

        Button deleteDatabankBtn = new Button("🗑 Databank löschen");
        deleteDatabankBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ff5252; -fx-font-weight: bold; -fx-cursor: hand;");
        deleteDatabankBtn.setOnAction(e -> deleteCurrentDatabank());

        Button deleteSelectedStratsBtn = new Button("🗑 Selektierte Strategien löschen");
        deleteSelectedStratsBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ffab40; -fx-font-weight: bold; -fx-cursor: hand;");
        deleteSelectedStratsBtn.setOnAction(e -> {
            Tab activeTab = bottomDatabankTabPane.getSelectionModel().getSelectedItem();
            if (activeTab != null && activeTab.getContent() instanceof TableView) {
                @SuppressWarnings("unchecked")
                TableView<CombinedPass> table = (TableView<CombinedPass>) activeTab.getContent();
                String dbName = activeTab.getText().replaceAll("\\s*\\(\\d+\\)$", "");
                deleteSelectedRowsFromDatabank(dbName, table);
            }
        });

        persistDatabanksCheckBox = new CheckBox("💾 Databanken persistent in DB speichern");
        persistDatabanksCheckBox.setStyle("-fx-text-fill: #00e5ff; -fx-font-weight: bold; -fx-cursor: hand;");
        CustomProject project = host.getProject();
        persistDatabanksCheckBox.setSelected(project != null ? project.isSaveDatabanksPersistently() : true);
        persistDatabanksCheckBox.setOnAction(e -> {
            CustomProject p = host.getProject();
            if (p != null) {
                p.setSaveDatabanksPersistently(persistDatabanksCheckBox.isSelected());
                host.saveProject();
            }
        });

        Button configColumnsBtn = new Button("⚙ Columns");
        configColumnsBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ffd740; -fx-font-weight: bold; -fx-cursor: hand;");
        configColumnsBtn.setOnAction(e -> {
            DatabankColumnChooserDialog.show(host.getOwnerWindow(), this::refreshDatabanksUI);
        });

        Button scoreWeightsBtn = new Button("⚖ Score-Gewichtung");
        scoreWeightsBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #00e5ff; -fx-font-weight: bold; -fx-cursor: hand;");
        scoreWeightsBtn.setOnAction(e -> {
            WorkflowConfigDialogs.showScoreWeightsDialog(host.getOwnerWindow());
            refreshDatabanksUI();
        });

        Button compareDatabanksBtn = new Button("📊 Databanken vergleichen");
        compareDatabanksBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #76ff03; -fx-font-weight: bold; -fx-cursor: hand;");
        compareDatabanksBtn.setOnAction(e -> DatabankComparisonDialog.show(host.getOwnerWindow(), databankManager));

        Button showEquityCurvesBtn = new Button("📈 Equitykurven alle anzeigen");
        showEquityCurvesBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #00e5ff; -fx-font-weight: bold; -fx-cursor: hand;");
        showEquityCurvesBtn.setTooltip(new Tooltip("Alle Equitykurven und Backtest-Grafiken der aktuellen Databank anzeigen"));
        showEquityCurvesBtn.setOnAction(e -> {
            Tab activeTab = bottomDatabankTabPane.getSelectionModel().getSelectedItem();
            String currentDbName = activeTab != null ? activeTab.getText().replaceAll("\\s*\\(\\d+\\)$", "") : DatabankManager.RESULTS;
            DatabankEquityGalleryDialog.show(host.getOwnerWindow(), databankManager, currentDbName, host.getProject(),
                    () -> refreshDatabanksUI(currentDbName));
        });

        importMt5OptiBtn = new Button("📥 MT5 Opti importieren");
        importMt5OptiBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #69f0ae; -fx-font-weight: bold; -fx-cursor: hand;");
        importMt5OptiBtn.setTooltip(new Tooltip(
                "Liest OptimizationReport.xml/.htm aus MetaTrader in die aktuelle Databank.\n"
                        + "Nur Dateilesen — MetaTrader wird dabei weder gestartet noch beendet."));
        importMt5OptiBtn.setOnAction(e -> importMt5OptimizationIntoCurrentDatabank());

        Button backupBtn = new Button("💾 Backup");
        backupBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #b388ff; -fx-font-weight: bold; -fx-cursor: hand;");
        backupBtn.setTooltip(new Tooltip("Vollstaendiges Projekt-Backup: Tasks, Einstellungen, Databanken, Archive, Lineage"));
        backupBtn.setOnAction(e -> host.backupProject());

        Button restoreBtn = new Button("📂 Restore");
        restoreBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #b388ff; -fx-font-weight: bold; -fx-cursor: hand;");
        restoreBtn.setTooltip(new Tooltip("Projekt 1:1 aus einer Backup-Datei wiederherstellen"));
        restoreBtn.setOnAction(e -> host.restoreProject());

        bar.getChildren().addAll(newDatabankBtn, clearCurrentDbBtn, clearAllBtn, deleteDatabankBtn,
                deleteSelectedStratsBtn, importMt5OptiBtn, configColumnsBtn, scoreWeightsBtn,
                compareDatabanksBtn, showEquityCurvesBtn, backupBtn, restoreBtn, persistDatabanksCheckBox);

        bottomDatabankTabPane = new TabPane();
        VBox.setVgrow(bottomDatabankTabPane, Priority.ALWAYS);
        bottomDatabankTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (rebuildingDatabankTabs || newTab == null) return;
            Platform.runLater(() -> repairVisibleDatabankTable(true));
        });

        parameterAdoptionBanner = new Label();
        parameterAdoptionBanner.setMaxWidth(Double.MAX_VALUE);
        parameterAdoptionBanner.setPadding(new Insets(7, 10, 7, 10));
        parameterAdoptionBanner.setVisible(false);
        parameterAdoptionBanner.setManaged(false);

        refreshDatabanksUI();

        panel.getChildren().addAll(bar, parameterAdoptionBanner, bottomDatabankTabPane);
        return panel;
    }

    private void promptCreateNewDatabank() {
        TextInputDialog dialog = new TextInputDialog("OOS_Passed");
        dialog.setTitle("New Databank");
        dialog.setHeaderText("Enter name for new Databank:");
        dialog.setContentText("Name:");

        dialog.getDialogPane().setStyle("-fx-background-color: #0b0d13;");
        List<String> stylesheets = host.getStylesheets();
        if (stylesheets != null && !stylesheets.isEmpty()) {
            dialog.getDialogPane().getStylesheets().addAll(stylesheets);
        }

        dialog.showAndWait().ifPresent(name -> {
            if (name != null && !name.trim().isEmpty()) {
                if (databankManager.createDatabank(name.trim())) {
                    host.saveProject();
                    refreshDatabanksUI();
                    updateDatabankComboBoxes();
                } else {
                    Alert alert = new Alert(Alert.AlertType.WARNING, "Databank exists or invalid name.", ButtonType.OK);
                    alert.initOwner(host.getOwnerWindow());
                    alert.showAndWait();
                }
            }
        });
    }

    private void deleteCurrentDatabank() {
        Tab currentTab = bottomDatabankTabPane.getSelectionModel().getSelectedItem();
        if (currentTab == null) return;
        String dbName = currentTab.getText().replaceAll("\\s*\\(\\d+\\)$", "");
        deleteDatabankByName(dbName);
    }

    private void deleteDatabankByName(String dbName) {
        if (dbName == null) return;
        if (dbName.equalsIgnoreCase("Results") || dbName.equalsIgnoreCase("Existing portfolio") || dbName.equalsIgnoreCase("Final")) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Standard-Databanken (" + dbName + ") können nicht gelöscht werden.", ButtonType.OK);
            alert.initOwner(host.getOwnerWindow());
            alert.show();
            return;
        }

        if (!confirmDestructiveAction("Databank löschen",
                "Databank '" + dbName + "' einschließlich aller Strategien löschen?")) return;

        databankManager.removeDatabank(dbName);
        host.invalidateWorkflowResultsAfterDatabankClear(false, dbName);
        updateDatabankComboBoxes();
        host.flushProjectSaveAsync(() -> {
            host.refreshTaskChain();
            refreshDatabanksUI("Results");
        });
        host.logToConsole("DATABANK", "Databank '" + dbName + "' wurde gelöscht.");
    }

    private void importMt5OptimizationIntoCurrentDatabank() {
        String dbName = currentDatabankName();
        if (dbName == null || dbName.isBlank()) {
            infoAlert("MT5 Opti importieren", "Bitte zuerst eine Databank-Tab auswählen (z. B. g01_grid_raw).");
            return;
        }

        ButtonType fromMt5 = new ButtonType("Aus MT5-Ordner");
        ButtonType chooseFile = new ButtonType("Datei wählen…");
        ButtonType cancel = ButtonType.CANCEL;
        Alert source = new Alert(Alert.AlertType.CONFIRMATION);
        source.setTitle("MT5 Opti importieren");
        source.setHeaderText("Optimierungsergebnisse in Databank '" + dbName + "' laden");
        source.setContentText(
                "Quelle wählen.\n\n"
                        + "Wichtig: MetaTrader wird dabei NICHT beendet oder gestartet — "
                        + "nur die Report-Datei wird gelesen.\n"
                        + "Wenn die Optimierung noch läuft, kann der Report unvollständig sein.");
        source.getButtonTypes().setAll(fromMt5, chooseFile, cancel);
        Window owner = host.getOwnerWindow();
        if (owner != null) source.initOwner(owner);

        Optional<ButtonType> choice = source.showAndWait();
        if (choice.isEmpty() || choice.get() == cancel) {
            return;
        }

        // Parsing large reports runs in a background Task; only dialogs and
        // table updates stay on the FX thread.
        if (choice.get() == fromMt5) {
            Path mtDir = resolveConfiguredMtInstallDir();
            if (mtDir == null) {
                infoAlert("MT5 Opti importieren",
                        "Kein MT5-Installationspfad in der Konfiguration gefunden.\n"
                                + "Bitte Datei manuell wählen oder mt5.terminal.path setzen.");
                return;
            }
            host.logToConsole("IMPORT", "Lese OptimizationReport aus MT5-Ordner (ohne Prozess-Eingriff): " + mtDir);
            startMt5OptiImportTask(dbName, mtDir, null, null);
        } else {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("OptimizationReport wählen");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("MT5/MT4 Reports", "*.xml", "*.htm", "*.html"),
                    new FileChooser.ExtensionFilter("Alle Dateien", "*.*"));
            Path mtDir = resolveConfiguredMtInstallDir();
            if (mtDir != null) {
                chooser.setInitialDirectory(mtDir.toFile());
            }
            java.io.File file = chooser.showOpenDialog(owner);
            if (file == null) {
                return;
            }
            Path main = file.toPath();
            Path forward = main.getParent() != null
                    ? main.getParent().resolve(Mt5OptimizationImportService.FORWARD_XML)
                    : null;
            host.logToConsole("IMPORT", "Lese Report-Datei (ohne Prozess-Eingriff): " + main);
            startMt5OptiImportTask(dbName, null, main, forward);
        }
    }

    /** Runs the file parsing off the FX thread; button disabled while running. */
    private void startMt5OptiImportTask(String dbName, Path mtInstallDir, Path mainReport, Path forwardReport) {
        if (importMt5OptiBtn != null) importMt5OptiBtn.setDisable(true);
        Task<Mt5OptimizationImportService.ImportResult> importTask = new Task<>() {
            @Override
            protected Mt5OptimizationImportService.ImportResult call() throws Exception {
                updateMessage("MT5-Report wird gelesen und geparst …");
                return mtInstallDir != null
                        ? Mt5OptimizationImportService.importFromMt5Install(mtInstallDir)
                        : Mt5OptimizationImportService.importFromReportFiles(mainReport, forwardReport);
            }
        };
        // Task fires property changes on the FX thread — reuse the status banner.
        importTask.messageProperty().addListener((obs, oldMsg, newMsg) -> {
            if (newMsg != null && !newMsg.isBlank()) {
                showParameterAdoptionBanner(newMsg, true);
            }
        });
        importTask.setOnSucceeded(e -> {
            if (importMt5OptiBtn != null) importMt5OptiBtn.setDisable(false);
            hideParameterAdoptionBanner();
            try {
                applyImportedPasses(dbName, importTask.getValue());
            } catch (Exception ex) {
                showImportError(ex);
            }
        });
        importTask.setOnFailed(e -> {
            if (importMt5OptiBtn != null) importMt5OptiBtn.setDisable(false);
            hideParameterAdoptionBanner();
            showImportError(importTask.getException());
        });
        Thread importThread = new Thread(importTask, "mt5-opti-import");
        importThread.setDaemon(true);
        importThread.start();
    }

    private void showImportError(Throwable ex) {
        host.logToConsole("IMPORT", "Import fehlgeschlagen: " + ex.getMessage());
        Alert err = new Alert(Alert.AlertType.ERROR,
                "Import fehlgeschlagen:\n\n" + ex.getMessage(),
                ButtonType.OK);
        err.setTitle("MT5 Opti importieren");
        err.setHeaderText(null);
        Window owner = host.getOwnerWindow();
        if (owner != null) err.initOwner(owner);
        err.showAndWait();
    }

    private void hideParameterAdoptionBanner() {
        if (parameterAdoptionBanner != null) {
            parameterAdoptionBanner.setVisible(false);
            parameterAdoptionBanner.setManaged(false);
        }
    }

    private void applyImportedPasses(String dbName, Mt5OptimizationImportService.ImportResult imported) {
        int existing = databankManager.getDatabank(dbName).size();
        WorkflowTask optimizer = findOptimizerForTargetDatabank(dbName);
        String identityError = validateImportedReportIdentity(optimizer, imported);
        if (!identityError.isBlank()) {
            deleteImportedSnapshotDirectory(imported);
            host.logToConsole("IMPORT", "Import abgebrochen: " + identityError);
            errorAlert("MT5 Opti importieren", identityError);
            return;
        }
        String dateNote = imported.hasDateRange()
                ? "\nStart/End day aus Report: " + imported.fromDate() + " → " + imported.toDate()
                + (optimizer != null
                ? "\n(wird an Task '" + optimizer.getName() + "' übernommen)"
                : "\n(kein Optimizer-Task mit Ziel '" + dbName + "' gefunden — Daten nur in Passes)")
                : "\nKein Zeitraum im Report gefunden — Start/End day bleiben unverändert.";

        ButtonType replace = new ButtonType("Ersetzen");
        ButtonType merge = new ButtonType("Hinzufügen");
        ButtonType cancel = ButtonType.CANCEL;
        Alert mode = new Alert(Alert.AlertType.CONFIRMATION);
        mode.setTitle("Import bestätigen");
        mode.setHeaderText(imported.passCount() + " Pass(es) → Databank '" + dbName + "'");
        mode.setContentText(
                imported.message() + "\n\n"
                        + "Quelle: " + imported.mainReport() + "\n"
                        + (imported.forwardReport() != null
                        ? "Forward: " + imported.forwardReport() + "\n"
                        : "Forward: (keine)\n")
                        + "Aktuell in Databank: " + existing + " Strategie(n)"
                        + dateNote + "\n\n"
                        + "Ersetzen = Databank leeren und neu füllen\n"
                        + "Hinzufügen = bestehende behalten, neue dazumergen");
        mode.getButtonTypes().setAll(replace, merge, cancel);
        Window owner = host.getOwnerWindow();
        if (owner != null) mode.initOwner(owner);

        Optional<ButtonType> decided = mode.showAndWait();
        if (decided.isEmpty() || decided.get() == cancel) {
            deleteImportedSnapshotDirectory(imported);
            return;
        }

        final boolean doReplace = decided.get() == replace;
        host.runSuppressingDownstreamInvalidation(() -> {
            String dateApplyMsg = applyImportedDatesToOptimizer(optimizer, imported);
            host.acknowledgeTaskExecutionSignature(optimizer);

            if (doReplace) {
                databankManager.setDatabankContent(dbName, imported.passes());
            } else {
                databankManager.addPassesToDatabank(dbName, imported.passes());
            }

            int after = databankManager.getDatabank(dbName).size();
            // Reloading the task form recomputes forward dates and fires control
            // listeners, so the wipe guard must also cover this async continuation.
            host.flushProjectSaveAsync(() -> host.runSuppressingDownstreamInvalidation(() -> {
                host.refreshTaskChain();
                host.reloadSelectedTaskForm();
                host.acknowledgeTaskExecutionSignature(optimizer);
                refreshDatabanksUI(dbName);
                int stillThere = databankManager.getDatabank(dbName).size();
                host.logToConsole("IMPORT",
                        "Import OK: " + imported.passCount() + " Pass(es) → '" + dbName
                                + "' (" + (doReplace ? "ersetzt" : "hinzugefügt")
                                + ", jetzt " + stillThere + "). MetaTrader wurde nicht angefasst."
                                + (dateApplyMsg.isBlank() ? "" : " " + dateApplyMsg));
                if (stillThere != after) {
                    host.logToConsole("IMPORT",
                            "Warnung: Databank '" + dbName + "' Größe nach Save geändert: "
                                    + after + " → " + stillThere + ".");
                }
                infoAlert("MT5 Opti importieren",
                        imported.passCount() + " Strategie(n) in '" + dbName + "' geladen.\n"
                                + "Databank enthält jetzt " + stillThere + " Einträge.\n"
                                + (dateApplyMsg.isBlank() ? "" : dateApplyMsg + "\n")
                                + "\nMetaTrader wurde weder gestartet noch beendet.");
            }));
        });
    }

    /**
     * The import snapshot directory only stays on disk when the import is
     * actually applied (it is the reportDirectory of the archived passes); on
     * abort paths it is cleaned up again.
     */
    private void deleteImportedSnapshotDirectory(Mt5OptimizationImportService.ImportResult imported) {
        Path dir = imported != null ? imported.snapshotDirectory() : null;
        if (dir == null || !Files.isDirectory(dir)) return;
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ex) {
                    host.logToConsole("IMPORT", "Snapshot-Datei konnte nicht gelöscht werden: " + p);
                }
            });
        } catch (IOException ex) {
            host.logToConsole("IMPORT", "Snapshot-Verzeichnis konnte nicht aufgeräumt werden: " + dir);
        }
    }

    private String validateImportedReportIdentity(WorkflowTask optimizer,
                                                   Mt5OptimizationImportService.ImportResult imported) {
        if (optimizer == null || imported == null) return "";
        String reportSymbol = normalizeIdentity(imported.reportSymbol());
        String reportPeriod = normalizeIdentity(imported.reportPeriod());
        if (reportSymbol.isBlank() && reportPeriod.isBlank()) return "";

        String expectedSymbol = normalizeIdentity(optimizer.getRetestSymbol());
        String expectedPeriod = normalizeIdentity(optimizer.getRetestPeriod());
        if (expectedSymbol.isBlank()) {
            CustomProject project = host.getProject();
            expectedSymbol = project != null ? normalizeIdentity(project.getSymbol()) : "";
        }
        if (expectedPeriod.isBlank()) {
            CustomProject project = host.getProject();
            expectedPeriod = project != null ? normalizeIdentity(project.getPeriod()) : "";
        }

        List<String> mismatches = new ArrayList<>();
        if (!reportSymbol.isBlank() && !expectedSymbol.isBlank()
                && !reportSymbol.equals(expectedSymbol)) {
            mismatches.add("Symbol: Report " + imported.reportSymbol()
                    + ", Projekt erwartet " + expectedSymbol);
        }
        if (!reportPeriod.isBlank() && !expectedPeriod.isBlank()
                && !reportPeriod.equals(expectedPeriod)) {
            mismatches.add("Zeiteinheit: Report " + imported.reportPeriod()
                    + ", Projekt erwartet " + expectedPeriod);
        }
        if (mismatches.isEmpty()) return "";
        return "Import abgebrochen: Der Report gehört nicht zum gewählten Projekt.\n"
                + String.join("\n", mismatches)
                + "\n\nBitte den passenden MT5-Report für "
                + (expectedSymbol.isBlank() ? "das Projekt" : expectedSymbol)
                + (expectedPeriod.isBlank() ? "" : " " + expectedPeriod)
                + " auswählen.";
    }

    private static String normalizeIdentity(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String applyImportedDatesToOptimizer(WorkflowTask optimizer,
                                                 Mt5OptimizationImportService.ImportResult imported) {
        if (optimizer == null || !imported.hasDateRange()) {
            return "";
        }
        String oldFrom = optimizer.getStartDate();
        String oldTo = optimizer.getEndDate();
        optimizer.setStartDate(imported.fromDate());
        optimizer.setEndDate(imported.toDate());
        // Must use ForwardSplit: any deviation makes the task look "changed" on the
        // next form reload, which would wipe the databank we just filled.
        int forwardMode = optimizer.getOptimizerForwardMode();
        if (forwardMode > 0 && forwardMode != 4) {
            try {
                LocalDate forwardStart = com.backtester.engine.ForwardSplit.computeForwardStartDate(
                        LocalDate.parse(imported.fromDate()),
                        LocalDate.parse(imported.toDate()),
                        forwardMode,
                        null);
                optimizer.setOptimizerForwardDate(forwardStart != null ? forwardStart.toString() : "");
            } catch (Exception ignored) {
                // Leave existing forward date if parse fails.
            }
        }

        // MT5 puts the complete optimization range in both report titles. Apply
        // the configured IS/OOS split to the imported pass legs before they are
        // persisted; otherwise the Strategy Details dialog shows the same dates
        // for Backtest (IS) and Forward (OOS).
        try {
            LocalDate from = LocalDate.parse(imported.fromDate());
            LocalDate to = LocalDate.parse(imported.toDate());
            LocalDate configuredForwardDate = null;
            if (forwardMode == 4 && !optimizer.getOptimizerForwardDate().isBlank()) {
                configuredForwardDate = LocalDate.parse(optimizer.getOptimizerForwardDate());
            }
            OptimizationDateRangeResolver.apply(
                    imported.passes(), from, to, forwardMode, configuredForwardDate);
        } catch (Exception ignored) {
            // Keep the imported dates when the report/task range is malformed.
        }
        return "Start/End day an '" + optimizer.getName() + "': "
                + oldFrom + "…" + oldTo + " → "
                + imported.fromDate() + "…" + imported.toDate() + ".";
    }

    private WorkflowTask findOptimizerForTargetDatabank(String dbName) {
        if (dbName == null || dbName.isBlank()) return null;
        CustomProject project = host.getProject();
        if (project != null && project.getTasks() != null) {
            for (WorkflowTask task : project.getTasks()) {
                if (task == null || task.getType() != WorkflowTask.TaskType.OPTIMIZER) continue;
                String tgt = task.getTargetDatabank();
                if (tgt != null && tgt.equalsIgnoreCase(dbName)) {
                    return task;
                }
            }
        }
        WorkflowTask selected = host.getSelectedTask();
        if (selected != null && selected.getType() == WorkflowTask.TaskType.OPTIMIZER) {
            return selected;
        }
        return null;
    }

    private String currentDatabankName() {
        if (bottomDatabankTabPane == null) return null;
        Tab active = bottomDatabankTabPane.getSelectionModel().getSelectedItem();
        if (active == null) return null;
        return active.getText().replaceAll("\\s*\\(\\d+\\)$", "");
    }

    private Path resolveConfiguredMtInstallDir() {
        try {
            AppConfig cfg = AppConfig.getInstance();
            CustomProject project = host.getProject();
            String expert = project != null ? project.getExpert() : null;
            if (expert != null && !expert.isBlank()) {
                Path dir = cfg.getMtInstallDir(expert);
                if (dir != null) {
                    return dir;
                }
            }
            String terminal = cfg.getMt5TerminalPath();
            if (terminal == null || terminal.isBlank()) {
                return null;
            }
            Path parent = Paths.get(terminal).getParent();
            return parent;
        } catch (Exception ex) {
            return null;
        }
    }

    private void infoAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        Window owner = host.getOwnerWindow();
        if (owner != null) alert.initOwner(owner);
        alert.showAndWait();
    }

    private void errorAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        Window owner = host.getOwnerWindow();
        if (owner != null) alert.initOwner(owner);
        alert.showAndWait();
    }

    private boolean confirmDestructiveAction(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.CANCEL, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        Window owner = host.getOwnerWindow();
        if (owner != null) alert.initOwner(owner);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    /** NaN/null last so TimSort stays contract-safe on incomplete FW/LT metrics. */
    static Comparator<Double> nanSafeDoubleComparator() {
        return (a, b) -> {
            boolean aMissing = a == null || Double.isNaN(a);
            boolean bMissing = b == null || Double.isNaN(b);
            if (aMissing && bMissing) return 0;
            if (aMissing) return 1;
            if (bMissing) return -1;
            return Double.compare(a, b);
        };
    }

    private void deleteSelectedRowsFromDatabank(String dbName, TableView<CombinedPass> table) {
        if (table == null) return;
        List<CombinedPass> selected = new ArrayList<>(table.getSelectionModel().getSelectedItems());
        if (selected == null || selected.isEmpty()) return;

        boolean touchesAdoption = selectedTouchesAdoption(dbName, selected);
        databankManager.removePassesFromDatabank(dbName, selected);
        List<CombinedPass> remaining = databankManager.getDatabank(dbName);
        boolean empty = remaining == null || remaining.isEmpty();
        if (empty || touchesAdoption) {
            host.invalidateWorkflowResultsAfterDatabankClear(false, dbName);
            host.flushProjectSaveAsync(() -> {
                host.refreshTaskChain();
                refreshDatabanksUI(dbName);
            });
        } else {
            host.flushProjectSaveAsync(() -> refreshDatabanksUI(dbName));
        }
        host.logToConsole("DATABANK", selected.size() + " Strategie(n) aus Databank '" + dbName + "' gelöscht."
                + (empty ? " Databank ist leer — Task-Status zurückgesetzt." : ""));
    }

    /** True when a deleted pass is still referenced as an adopted optimizer basis. */
    private boolean selectedTouchesAdoption(String dbName, List<CombinedPass> selected) {
        CustomProject project = host.getProject();
        if (project == null || project.getTasks() == null || selected == null || selected.isEmpty()) {
            return false;
        }
        for (WorkflowTask task : project.getTasks()) {
            if (task == null || !task.isOptimizerParameterBasisAdopted()) continue;
            if (dbName != null && !dbName.equalsIgnoreCase(task.getOptimizerParameterBasisDatabank())
                    && !dbName.equalsIgnoreCase(task.getSourceDatabank())) {
                continue;
            }
            int passNo = task.getOptimizerParameterBasisPassNumber();
            for (CombinedPass pass : selected) {
                if (pass != null && pass.getPassNumber() == passNo) return true;
            }
        }
        return false;
    }
}
