package com.backtester.ui.javafx;

import com.backtester.database.DatabaseManager;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.workflow.CustomProject;
import com.backtester.workflow.DatabankManager;
import com.backtester.workflow.GuidedOptimizationService;
import com.backtester.workflow.WorkflowTask;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
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
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

        void adoptPassParameters(CombinedPass pass, String dbName);

        long findSensitivityRunTimestampForDatabank(String databankName);

        void backupProject();

        void restoreProject();

        ComboBox<String> getSourceDatabankCombo();

        ComboBox<String> getTargetDatabankCombo();

        ComboBox<String> getRankingSourceCombo();

        ComboBox<String> getRankingTargetCombo();
    }

    private final DatabankManager databankManager;
    private final Host host;

    private final VBox root;
    private TabPane bottomDatabankTabPane;
    private Pane databankToolbar;
    private CheckBox persistDatabanksCheckBox;
    private Label parameterAdoptionBanner;

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

    public void refreshDatabanksUI(String targetTabToFocus) {
        if (bottomDatabankTabPane == null) return;

        Tab currentTab = bottomDatabankTabPane.getSelectionModel().getSelectedItem();
        String activeDbName = targetTabToFocus != null ? targetTabToFocus : (currentTab != null ? currentTab.getText().replaceAll("\\s*\\(\\d+\\)$", "") : null);

        bottomDatabankTabPane.getTabs().clear();
        Tab tabToSelect = null;

        Set<DatabankColumnChooserDialog.DatabankColumn> visibleCols = DatabankColumnChooserDialog.getVisibleColumns();

        for (String dbName : databankManager.getDatabankNames()) {
            boolean isStandard = dbName.equalsIgnoreCase("Results") || dbName.equalsIgnoreCase("Existing portfolio") || dbName.equalsIgnoreCase("Final");
            List<CombinedPass> passes = databankManager.getDatabank(dbName);
            Tab tab = new Tab(dbName + " (" + passes.size() + ")");
            tab.setClosable(!isStandard);
            tab.setOnCloseRequest(e -> {
                e.consume();
                deleteDatabankByName(dbName);
            });

            TableView<CombinedPass> table = new TableView<>();
            table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            table.getItems().setAll(passes);

            table.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.DELETE) {
                    deleteSelectedRowsFromDatabank(dbName, table);
                }
            });

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.NAME)) {
                TableColumn<CombinedPass, String> nameCol = new TableColumn<>("Strategy Name");
                nameCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getStrategyName()));
                nameCol.setPrefWidth(130);
                table.getColumns().add(nameCol);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.PASS)) {
                TableColumn<CombinedPass, Integer> passCol = new TableColumn<>("Pass");
                passCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getPassNumber()));
                passCol.setPrefWidth(60);
                table.getColumns().add(passCol);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.SCORE)) {
                TableColumn<CombinedPass, Double> scoreCol = new TableColumn<>("Score");
                scoreCol.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getScore()));
                scoreCol.setPrefWidth(70);
                table.getColumns().add(scoreCol);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.BT_PROFIT)) {
                TableColumn<CombinedPass, Double> btProf = new TableColumn<>("BT Profit");
                btProf.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getBtProfit()));
                btProf.setPrefWidth(90);
                table.getColumns().add(btProf);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.FW_PROFIT)) {
                TableColumn<CombinedPass, Double> fwProf = new TableColumn<>("FW Profit");
                fwProf.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getFwProfit()));
                fwProf.setPrefWidth(90);
                table.getColumns().add(fwProf);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.LT_PROFIT)) {
                TableColumn<CombinedPass, Double> ltProf = new TableColumn<>("LT Profit");
                ltProf.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getLtProfit()));
                ltProf.setPrefWidth(90);
                table.getColumns().add(ltProf);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.BT_PF)) {
                TableColumn<CombinedPass, String> btPf = new TableColumn<>("BT Profit Factor");
                btPf.setCellValueFactory(c -> {
                    double v = c.getValue().getBtPf();
                    return new SimpleStringProperty(Double.isNaN(v) || v <= 0 ? "-" : String.format(Locale.US, "%.2f", v));
                });
                btPf.setPrefWidth(115);
                table.getColumns().add(btPf);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.FW_PF)) {
                TableColumn<CombinedPass, String> fwPf = new TableColumn<>("FW Profit Factor");
                fwPf.setCellValueFactory(c -> {
                    double v = c.getValue().getFwPf();
                    return new SimpleStringProperty(Double.isNaN(v) || v <= 0 ? "-" : String.format(Locale.US, "%.2f", v));
                });
                fwPf.setPrefWidth(115);
                table.getColumns().add(fwPf);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.LT_PF)) {
                TableColumn<CombinedPass, String> ltPf = new TableColumn<>("LT Profit Factor");
                ltPf.setCellValueFactory(c -> {
                    double v = c.getValue().getLtPf();
                    return new SimpleStringProperty(Double.isNaN(v) || v <= 0 ? "-" : String.format(Locale.US, "%.2f", v));
                });
                ltPf.setPrefWidth(115);
                table.getColumns().add(ltPf);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.BT_DD)) {
                TableColumn<CombinedPass, Double> btDd = new TableColumn<>("BT DD %");
                btDd.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getBtDd()));
                btDd.setPrefWidth(80);
                table.getColumns().add(btDd);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.FW_DD)) {
                TableColumn<CombinedPass, Double> fwDd = new TableColumn<>("FW DD %");
                fwDd.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getFwDd()));
                fwDd.setPrefWidth(80);
                table.getColumns().add(fwDd);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.LT_DD)) {
                TableColumn<CombinedPass, Double> ltDd = new TableColumn<>("LT DD %");
                ltDd.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getLtDd()));
                ltDd.setPrefWidth(80);
                table.getColumns().add(ltDd);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.BT_TRADES)) {
                TableColumn<CombinedPass, Integer> btTr = new TableColumn<>("BT Trades");
                btTr.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getBtTrades()));
                btTr.setPrefWidth(75);
                table.getColumns().add(btTr);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.FW_TRADES)) {
                TableColumn<CombinedPass, Integer> fwTr = new TableColumn<>("FW Trades");
                fwTr.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getFwTrades()));
                fwTr.setPrefWidth(75);
                table.getColumns().add(fwTr);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.LT_TRADES)) {
                TableColumn<CombinedPass, Integer> ltTr = new TableColumn<>("LT Trades");
                ltTr.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getLtTrades()));
                ltTr.setPrefWidth(75);
                table.getColumns().add(ltTr);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.BT_SHARPE)) {
                TableColumn<CombinedPass, Double> btSh = new TableColumn<>("BT Sharpe");
                btSh.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getBtSharpe()));
                btSh.setPrefWidth(80);
                table.getColumns().add(btSh);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.FW_SHARPE)) {
                TableColumn<CombinedPass, Double> fwSh = new TableColumn<>("FW Sharpe");
                fwSh.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getFwSharpe()));
                fwSh.setPrefWidth(80);
                table.getColumns().add(fwSh);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.BT_RECOVERY)) {
                TableColumn<CombinedPass, Double> btRec = new TableColumn<>("BT Rec");
                btRec.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getBtRecovery()));
                btRec.setPrefWidth(80);
                table.getColumns().add(btRec);
            }

            if (visibleCols.contains(DatabankColumnChooserDialog.DatabankColumn.FW_RECOVERY)) {
                TableColumn<CombinedPass, Double> fwRec = new TableColumn<>("FW Rec");
                fwRec.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getFwRecovery()));
                fwRec.setPrefWidth(80);
                table.getColumns().add(fwRec);
            }

            // Robustness actions are scoped to the exact run that produced this databank.
            final long databankSensitivityTimestamp = host.findSensitivityRunTimestampForDatabank(dbName);

            // Context Menu & Row click handlers
            table.setRowFactory(tv -> {
                TableRow<CombinedPass> row = new TableRow<>();

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

            tab.setContent(table);
            bottomDatabankTabPane.getTabs().add(tab);

            if (activeDbName != null && dbName.equalsIgnoreCase(activeDbName)) {
                tabToSelect = tab;
            }
        }

        if (tabToSelect != null) {
            bottomDatabankTabPane.getSelectionModel().select(tabToSelect);
        }
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
        clearAllBtn.setOnAction(e -> {
            if (!confirmDestructiveAction("Alle Databanken leeren",
                    "Alle Strategien aus allen Databanken entfernen? (Die Databank-Tabs bleiben erhalten)")) return;
            databankManager.clearAll();
            host.invalidateWorkflowResultsAfterDatabankClear(true, null);
            host.flushProjectSaveAsync(() -> {
                host.refreshTaskChain();
                refreshDatabanksUI();
            });
            host.logToConsole("DATABANK", "Alle Databanken wurden geleert.");
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

        Button backupBtn = new Button("💾 Backup");
        backupBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #b388ff; -fx-font-weight: bold; -fx-cursor: hand;");
        backupBtn.setTooltip(new Tooltip("Projekt und Databanken in eine Datei exportieren"));
        backupBtn.setOnAction(e -> host.backupProject());

        Button restoreBtn = new Button("📂 Restore");
        restoreBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #b388ff; -fx-font-weight: bold; -fx-cursor: hand;");
        restoreBtn.setTooltip(new Tooltip("Projekt und Databanken aus einer Datei importieren"));
        restoreBtn.setOnAction(e -> host.restoreProject());

        bar.getChildren().addAll(newDatabankBtn, clearCurrentDbBtn, clearAllBtn, deleteDatabankBtn,
                deleteSelectedStratsBtn, configColumnsBtn, scoreWeightsBtn, compareDatabanksBtn, showEquityCurvesBtn, backupBtn, restoreBtn, persistDatabanksCheckBox);

        bottomDatabankTabPane = new TabPane();
        VBox.setVgrow(bottomDatabankTabPane, Priority.ALWAYS);

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

    private boolean confirmDestructiveAction(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.CANCEL, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        Window owner = host.getOwnerWindow();
        if (owner != null) alert.initOwner(owner);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
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
