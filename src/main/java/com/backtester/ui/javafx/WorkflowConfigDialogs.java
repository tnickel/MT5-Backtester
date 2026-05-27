package com.backtester.ui.javafx;

import com.backtester.config.EaParameter;
import com.backtester.config.EaParameterManager;
import com.backtester.engine.BacktestConfig;
import com.backtester.engine.OptimizationConfig;
import com.backtester.engine.WorkflowEngine;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.SensitivityResult;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Modals and settings dialogs for the 6 visual workflow pipeline steps.
 */
public class WorkflowConfigDialogs {

    private static final EaParameterManager eaParamManager = new EaParameterManager();

    private static void applyTheme(Stage stage, Window owner) {
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }
        if (owner != null && owner.getScene() != null && !owner.getScene().getStylesheets().isEmpty()) {
            stage.getScene().getStylesheets().addAll(owner.getScene().getStylesheets());
        }
    }

    private static StringConverter<LocalDate> createDateConverter() {
        return new StringConverter<LocalDate>() {
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            @Override
            public String toString(LocalDate date) {
                return (date != null) ? dateFormatter.format(date) : "";
            }
            @Override
            public LocalDate fromString(String string) {
                return (string != null && !string.isEmpty()) ? LocalDate.parse(string, dateFormatter) : null;
            }
        };
    }

    // ─── Step 1: Strategy Selector & Ranges ─────────────────────────────────────

    public static void showStep1Dialog(WorkflowEngine engine, Window owner, Runnable onSave) {
        Stage stage = new Stage();
        stage.setTitle("Schritt 1: Strategie-Auswahl & Suchräume");

        VBox layout = new VBox(15);
        layout.setStyle("-fx-background-color: #0b0d13; -fx-padding: 20;");
        layout.setPrefWidth(900);
        layout.setPrefHeight(750);

        Label title = new Label("STRATEGIE-AUSWAHL & OPTIMIERUNGS-INPUTS");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#00e5ff"));
        layout.getChildren().add(title);

        // Core fields grid
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.getStyleClass().add("sci-fi-panel");

        grid.add(new Label("Expert Advisor:"), 0, 0);
        TextField expertField = new TextField(engine.getExpert());
        expertField.setPrefWidth(300);
        Button browseBtn = new Button("...");
        HBox expBox = new HBox(5, expertField, browseBtn);
        grid.add(expBox, 1, 0, 2, 1);

        grid.add(new Label("Symbol:"), 0, 1);
        ComboBox<String> symbolCombo = new ComboBox<>(FXCollections.observableArrayList(BacktestConfig.SYMBOLS));
        symbolCombo.setValue(engine.getSymbol());
        grid.add(symbolCombo, 1, 1);

        grid.add(new Label("Periode:"), 2, 1);
        ComboBox<String> periodCombo = new ComboBox<>(FXCollections.observableArrayList("M1", "M5", "M15", "M30", "H1", "H4", "D1"));
        periodCombo.setValue(engine.getPeriod());
        grid.add(periodCombo, 3, 1);

        grid.add(new Label("Datum von:"), 0, 2);
        DatePicker fromDatePicker = new DatePicker(engine.getFromDate());
        fromDatePicker.setConverter(createDateConverter());
        grid.add(fromDatePicker, 1, 2);

        grid.add(new Label("bis:"), 2, 2);
        DatePicker toDatePicker = new DatePicker(engine.getToDate());
        toDatePicker.setConverter(createDateConverter());
        grid.add(toDatePicker, 3, 2);

        grid.add(new Label("Konto / Währung:"), 0, 3);
        TextField depField = new TextField(String.valueOf(engine.getDeposit()));
        TextField curField = new TextField(engine.getCurrency());
        depField.setPrefWidth(80);
        curField.setPrefWidth(50);
        HBox depCur = new HBox(5, depField, curField);
        grid.add(depCur, 1, 3);

        grid.add(new Label("Hebel / Modell:"), 2, 3);
        TextField levField = new TextField(engine.getLeverage());
        levField.setPrefWidth(80);
        ComboBox<String> modelCombo = new ComboBox<>(FXCollections.observableArrayList(OptimizationConfig.MODEL_NAMES));
        if (engine.getTickModel() >= 0 && engine.getTickModel() < modelCombo.getItems().size()) {
            modelCombo.getSelectionModel().select(engine.getTickModel());
        } else {
            modelCombo.getSelectionModel().select(1); // Every tick
        }
        HBox levMod = new HBox(5, levField, modelCombo);
        grid.add(levMod, 3, 3);

        layout.getChildren().add(grid);

        // Parameters Table
        VBox paramBox = new VBox(5);
        VBox.setVgrow(paramBox, Priority.ALWAYS);
        Label paramTitle = new Label("EA Parameter & Optimierungs-Suchraum");
        paramTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        paramTitle.setTextFill(Color.web("#80d8ff"));

        TableView<EaParameter> paramTable = new TableView<>();
        paramTable.setEditable(true);
        paramTable.setStyle("-fx-background-color: transparent;");

        TableColumn<EaParameter, Boolean> optCol = new TableColumn<>("Opt");
        optCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleBooleanProperty(cellData.getValue().isOptimizeEnabled()));
        optCol.setCellFactory(tc -> new TableCell<EaParameter, Boolean>() {
            private final CheckBox checkBox = new CheckBox();
            {
                checkBox.setOnAction(e -> {
                    EaParameter param = getTableRow().getItem();
                    if (param != null) {
                        param.setOptimizeEnabled(checkBox.isSelected());
                    }
                });
                setAlignment(Pos.CENTER);
            }
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    checkBox.setSelected(item);
                    setGraphic(checkBox);
                }
            }
        });
        optCol.setPrefWidth(40);

        TableColumn<EaParameter, String> nameCol = new TableColumn<>("Variable");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(180);

        TableColumn<EaParameter, String> valCol = new TableColumn<>("Wert");
        valCol.setCellValueFactory(new PropertyValueFactory<>("value"));
        valCol.setCellFactory(javafx.scene.control.cell.TextFieldTableCell.forTableColumn());
        valCol.setOnEditCommit(e -> e.getRowValue().setValue(e.getNewValue()));
        valCol.setPrefWidth(100);

        TableColumn<EaParameter, String> startCol = new TableColumn<>("Start");
        startCol.setCellValueFactory(new PropertyValueFactory<>("optimizeStart"));
        startCol.setCellFactory(javafx.scene.control.cell.TextFieldTableCell.forTableColumn());
        startCol.setOnEditCommit(e -> e.getRowValue().setOptimizeStart(e.getNewValue()));

        TableColumn<EaParameter, String> stepCol = new TableColumn<>("Schritt");
        stepCol.setCellValueFactory(new PropertyValueFactory<>("optimizeStep"));
        stepCol.setCellFactory(javafx.scene.control.cell.TextFieldTableCell.forTableColumn());
        stepCol.setOnEditCommit(e -> e.getRowValue().setOptimizeStep(e.getNewValue()));

        TableColumn<EaParameter, String> stopCol = new TableColumn<>("Stopp");
        stopCol.setCellValueFactory(new PropertyValueFactory<>("optimizeEnd"));
        stopCol.setCellFactory(javafx.scene.control.cell.TextFieldTableCell.forTableColumn());
        stopCol.setOnEditCommit(e -> e.getRowValue().setOptimizeEnd(e.getNewValue()));

        paramTable.getColumns().addAll(optCol, nameCol, valCol, startCol, stepCol, stopCol);

        final String[] lastCheckedExpert = { engine.getExpert() };

        Runnable updateParamsTable = () -> {
            String expert = expertField.getText().trim();
            if (expert.isEmpty()) {
                paramTable.getItems().clear();
                return;
            }

            boolean expertChanged = !expert.equals(lastCheckedExpert[0]);
            lastCheckedExpert[0] = expert;

            if (expertChanged) {
                String strategyConfigJson = null;
                try {
                    strategyConfigJson = com.backtester.database.DatabaseManager.getInstance().getWorkflowStrategyConfig(expert);
                } catch (Exception ignored) {}

                if (strategyConfigJson != null && !strategyConfigJson.isEmpty()) {
                    try {
                        boolean loaded = engine.loadStrategyConfig(expert);
                        if (loaded) {
                            symbolCombo.setValue(engine.getSymbol());
                            periodCombo.setValue(engine.getPeriod());
                            fromDatePicker.setValue(engine.getFromDate());
                            toDatePicker.setValue(engine.getToDate());
                            depField.setText(String.valueOf(engine.getDeposit()));
                            curField.setText(engine.getCurrency());
                            levField.setText(engine.getLeverage());
                            if (engine.getTickModel() >= 0 && engine.getTickModel() < modelCombo.getItems().size()) {
                                modelCombo.getSelectionModel().select(engine.getTickModel());
                            }

                            List<EaParameter> tableCopy = new ArrayList<>();
                            for (EaParameter p : engine.getEaParameters()) {
                                EaParameter copy = new EaParameter();
                                copy.setName(p.getName());
                                copy.setValue(p.getValue());
                                copy.setDefaultValue(p.getDefaultValue() != null ? p.getDefaultValue() : p.getValue());
                                copy.setSection(p.getSection());
                                copy.setOptimizeStart(p.getOptimizeStart());
                                copy.setOptimizeStep(p.getOptimizeStep());
                                copy.setOptimizeEnd(p.getOptimizeEnd());
                                copy.setOptimizeEnabled(p.isOptimizeEnabled());
                                copy.setStringType(p.isStringType());
                                tableCopy.add(copy);
                            }
                            paramTable.getItems().setAll(tableCopy);
                            return;
                        }
                    } catch (Exception ignored) {}
                }
            }

            String symbol = symbolCombo.getValue() != null ? symbolCombo.getValue() : "EURUSD";
            String period = periodCombo.getValue() != null ? periodCombo.getValue() : "H1";

            // Try DB first
            String dbParamsJson = null;
            try {
                dbParamsJson = com.backtester.database.DatabaseManager.getInstance().getEaParameterSettings(expert, symbol, period);
            } catch (Exception ignored) {}

            List<EaParameter> params = null;
            if (dbParamsJson != null && !dbParamsJson.isEmpty()) {
                try {
                    java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<java.util.List<com.backtester.config.EaParameter>>(){}.getType();
                    params = new com.google.gson.Gson().fromJson(dbParamsJson, listType);
                } catch (Exception ignored) {}
            }

            if (params == null || params.isEmpty()) {
                params = eaParamManager.getEffectiveParameters(expert);
            }

            if (params != null) {
                List<EaParameter> tableCopy = new ArrayList<>();
                for (EaParameter p : params) {
                    EaParameter copy = new EaParameter();
                    copy.setName(p.getName());
                    copy.setValue(p.getValue());
                    copy.setDefaultValue(p.getDefaultValue() != null ? p.getDefaultValue() : p.getValue());
                    copy.setSection(p.getSection());
                    copy.setOptimizeStart(p.getOptimizeStart());
                    copy.setOptimizeStep(p.getOptimizeStep());
                    copy.setOptimizeEnd(p.getOptimizeEnd());
                    copy.setOptimizeEnabled(p.isOptimizeEnabled());
                    copy.setStringType(p.isStringType());
                    tableCopy.add(copy);
                }
                paramTable.getItems().setAll(tableCopy);
            } else {
                paramTable.getItems().clear();
            }
        };

        // Set action for browse button
        browseBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select Expert Advisor");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("MetaTrader 5 EA", "*.ex5"));
            try {
                com.backtester.config.AppConfig config = com.backtester.config.AppConfig.getInstance();
                java.nio.file.Path mt5Dir = config.getMt5InstallDir();
                java.nio.file.Path expertsDir = mt5Dir != null ? mt5Dir.resolve("MQL5").resolve("Experts") : null;
                if (expertsDir != null && java.nio.file.Files.exists(expertsDir)) {
                    chooser.setInitialDirectory(expertsDir.toFile());
                }
            } catch (Exception ignored) {}

            File selected = chooser.showOpenDialog(stage);
            if (selected != null) {
                String path = selected.getAbsolutePath();
                if (path.toLowerCase().endsWith(".ex5")) {
                    path = path.substring(0, path.length() - 4);
                }
                // Try to make it relative to MT5 Experts folder if possible
                try {
                    String expertsPart = "MQL5" + File.separator + "Experts" + File.separator;
                    int idx = path.indexOf(expertsPart);
                    if (idx != -1) {
                        path = path.substring(idx + expertsPart.length());
                    }
                } catch (Exception ignored) {}
                expertField.setText(path);
                updateParamsTable.run();
            }
        });

        // Listeners to update parameter table dynamically when EA, symbol or period changes
        expertField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) { // focus lost
                updateParamsTable.run();
            }
        });
        expertField.setOnAction(e -> updateParamsTable.run());
        symbolCombo.valueProperty().addListener((obs, oldVal, newVal) -> updateParamsTable.run());
        periodCombo.valueProperty().addListener((obs, oldVal, newVal) -> updateParamsTable.run());

        if (engine.getEaParameters() != null && !engine.getEaParameters().isEmpty()) {
            List<EaParameter> currentParams = new ArrayList<>();
            for (EaParameter p : engine.getEaParameters()) {
                EaParameter copy = new EaParameter();
                copy.setName(p.getName());
                copy.setValue(p.getValue());
                copy.setOptimizeStart(p.getOptimizeStart());
                copy.setOptimizeStep(p.getOptimizeStep());
                copy.setOptimizeEnd(p.getOptimizeEnd());
                copy.setOptimizeEnabled(p.isOptimizeEnabled());
                copy.setStringType(p.isStringType());
                currentParams.add(copy);
            }
            paramTable.getItems().setAll(currentParams);
        } else {
            updateParamsTable.run();
        }

        VBox.setVgrow(paramTable, Priority.ALWAYS);

        // Param actions
        HBox paramActions = new HBox(10);
        paramActions.setAlignment(Pos.CENTER_RIGHT);
        Button autoBtn = new Button("AutoConfig");
        autoBtn.setOnAction(e -> {
            for (EaParameter param : paramTable.getItems()) {
                String name = param.getName();
                String value = param.getValue();
                if (name.toLowerCase().contains("magic") || name.toLowerCase().contains("slippage") || name.toLowerCase().contains("comment")) {
                    param.setOptimizeEnabled(false);
                    continue;
                }
                try {
                    double current = Double.parseDouble(value);
                    if (current == 0) continue;
                    double start, end, step;
                    if (current < 1) {
                        start = 0.01; end = current * 2.0; step = 0.01;
                    } else if (current <= 10) {
                        start = 1; end = current * 2.0; step = 1;
                    } else {
                        start = current * 0.5; end = current * 1.5; step = Math.max(1, current * 0.1);
                        // Round step and start/end nicely
                        start = Math.round(start); end = Math.round(end); step = Math.round(step);
                    }
                    param.setOptimizeEnabled(true);
                    param.setOptimizeStart(String.valueOf(start));
                    param.setOptimizeStep(String.valueOf(step));
                    param.setOptimizeEnd(String.valueOf(end));
                } catch (NumberFormatException ignored) {}
            }
            paramTable.refresh();
        });

        Button loadSetBtn = new Button("Load .set");
        loadSetBtn.setOnAction(e -> {
            FileChooser ch = new FileChooser();
            ch.setTitle("Load .set File");
            ch.getExtensionFilters().add(new FileChooser.ExtensionFilter("MT5 Set Files", "*.set"));
            File file = ch.showOpenDialog(stage);
            if (file != null) {
                List<EaParameter> pList = eaParamManager.readSetFile(file.toPath());
                if (pList != null && !pList.isEmpty()) {
                    paramTable.getItems().setAll(pList);
                }
            }
        });

        Button saveSetBtn = new Button("Save .set");
        saveSetBtn.setOnAction(e -> {
            FileChooser ch = new FileChooser();
            ch.setTitle("Save .set File");
            ch.getExtensionFilters().add(new FileChooser.ExtensionFilter("MT5 Set Files", "*.set"));
            File file = ch.showSaveDialog(stage);
            if (file != null) {
                eaParamManager.writeSetFile(file.toPath(), new ArrayList<>(paramTable.getItems()), EaParameterManager.extractEaBaseName(expertField.getText()));
            }
        });

        Button takeParamsBtn = new Button("Take parameters from Robustness Test");
        takeParamsBtn.setOnAction(e -> {
            com.backtester.config.AppConfig config = com.backtester.config.AppConfig.getInstance();
            String robExpert = config.get("robustness.expert", "").trim();
            String robSymbol = config.get("robustness.symbol", "EURUSD");
            String robPeriod = config.get("robustness.period", "H1");

            if (robExpert.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Warnung");
                alert.setHeaderText(null);
                alert.setContentText("Es wurde kein Expert Advisor im Robustheits-Test konfiguriert.");
                alert.showAndWait();
                return;
            }

            String dbParamsJson = com.backtester.database.DatabaseManager.getInstance().getEaParameterSettings(robExpert, robSymbol, robPeriod);
            List<EaParameter> params = null;
            if (dbParamsJson != null && !dbParamsJson.isEmpty()) {
                try {
                    java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<java.util.List<com.backtester.config.EaParameter>>(){}.getType();
                    params = new com.google.gson.Gson().fromJson(dbParamsJson, listType);
                } catch (Exception ex) {
                    // Ignore, fallback to file
                }
            }

            if (params == null || params.isEmpty()) {
                params = eaParamManager.getEffectiveParameters(robExpert);
            }

            if (params != null && !params.isEmpty()) {
                expertField.setText(robExpert);
                symbolCombo.setValue(robSymbol);
                periodCombo.setValue(robPeriod);
                
                List<EaParameter> tableCopy = new ArrayList<>();
                for (EaParameter p : params) {
                    EaParameter copy = new EaParameter();
                    copy.setName(p.getName());
                    copy.setValue(p.getValue());
                    copy.setDefaultValue(p.getDefaultValue() != null ? p.getDefaultValue() : p.getValue());
                    copy.setSection(p.getSection());
                    copy.setOptimizeStart(p.getOptimizeStart());
                    copy.setOptimizeStep(p.getOptimizeStep());
                    copy.setOptimizeEnd(p.getOptimizeEnd());
                    copy.setOptimizeEnabled(p.isOptimizeEnabled());
                    copy.setStringType(p.isStringType());
                    tableCopy.add(copy);
                }
                paramTable.getItems().setAll(tableCopy);

                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Erfolg");
                alert.setHeaderText(null);
                alert.setContentText("Parameter und Einstellungen erfolgreich aus dem Robustheits-Test übernommen.");
                alert.showAndWait();
            } else {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Fehler");
                alert.setHeaderText(null);
                alert.setContentText("Keine Parameter für Expert Advisor \"" + robExpert + "\" im Robustheits-Test gefunden.");
                alert.showAndWait();
            }
        });

        paramActions.getChildren().addAll(takeParamsBtn, autoBtn, loadSetBtn, saveSetBtn);
        paramBox.getChildren().addAll(paramTitle, paramTable, paramActions);
        layout.getChildren().add(paramBox);

        // Buttons
        HBox bottomRow = new HBox(15);
        bottomRow.setAlignment(Pos.CENTER_RIGHT);
        Button cancel = new Button("Abbrechen");
        cancel.getStyleClass().add("button-cancel");
        cancel.setOnAction(e -> stage.close());
        Button save = new Button("Speichern");
        save.getStyleClass().add("button-start");
        save.setOnAction(e -> {
            engine.changeExpert(expertField.getText().trim());
            engine.setSymbol(symbolCombo.getValue());
            engine.setPeriod(periodCombo.getValue());
            engine.setFromDate(fromDatePicker.getValue());
            engine.setToDate(toDatePicker.getValue());
            try { engine.setDeposit(Integer.parseInt(depField.getText().trim())); } catch (Exception ignored) {}
            engine.setCurrency(curField.getText().trim());
            engine.setLeverage(levField.getText().trim());
            engine.setTickModel(modelCombo.getSelectionModel().getSelectedIndex());
            engine.setEaParameters(new ArrayList<>(paramTable.getItems()));
            engine.saveState();
            
            stage.close();
            if (onSave != null) onSave.run();
        });

        bottomRow.getChildren().addAll(cancel, save);
        layout.getChildren().add(bottomRow);

        stage.setScene(new Scene(layout));
        applyTheme(stage, owner);
        stage.showAndWait();
    }

    // ─── Step 2: Optimizer Settings ───────────────────────────────────────────

    public static void showStep2Dialog(WorkflowEngine engine, Window owner) {
        Stage stage = new Stage();
        stage.setTitle("Schritt 2: Optimizer-Konfiguration");

        VBox layout = new VBox(15);
        layout.setStyle("-fx-background-color: #0b0d13; -fx-padding: 20;");
        layout.setPrefWidth(450);

        Label title = new Label("OPTIMIERUNGS-METRIKEN & MODUS");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#00e5ff"));
        layout.getChildren().add(title);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(15);
        grid.getStyleClass().add("sci-fi-panel");

        grid.add(new Label("Algorithmus:"), 0, 0);
        ComboBox<String> optModeCombo = new ComboBox<>(FXCollections.observableArrayList(OptimizationConfig.OPTIMIZATION_MODES));
        int currentMode = engine.getOptimizationMode();
        int selectedIndex = 1; // Default to genetic (index 1)
        if (currentMode == 1) {
            selectedIndex = 0; // Complete
        } else if (currentMode == 2) {
            selectedIndex = 1; // Genetic
        }
        optModeCombo.getSelectionModel().select(selectedIndex);
        grid.add(optModeCombo, 1, 0);

        grid.add(new Label("Optimierungsziel:"), 0, 1);
        ComboBox<String> criterionCombo = new ComboBox<>(FXCollections.observableArrayList(OptimizationConfig.OPTIMIZATION_CRITERIA));
        if (engine.getOptimizationCriterion() >= 0 && engine.getOptimizationCriterion() < criterionCombo.getItems().size()) {
            criterionCombo.getSelectionModel().select(engine.getOptimizationCriterion());
        } else {
            criterionCombo.getSelectionModel().select(4); // Recovery factor
        }
        grid.add(criterionCombo, 1, 1);

        grid.add(new Label("Forward-Test:"), 0, 2);
        ComboBox<String> forwardCombo = new ComboBox<>(FXCollections.observableArrayList(OptimizationConfig.FORWARD_MODES));
        if (engine.getForwardMode() >= 0 && engine.getForwardMode() < forwardCombo.getItems().size()) {
            forwardCombo.getSelectionModel().select(engine.getForwardMode());
        } else {
            forwardCombo.getSelectionModel().select(1); // 1/2
        }
        grid.add(forwardCombo, 1, 2);

        grid.add(new Label("Forward Datum:"), 0, 3);
        DatePicker forwardDatePicker = new DatePicker(engine.getForwardDate());
        forwardDatePicker.setConverter(createDateConverter());
        grid.add(forwardDatePicker, 1, 3);

        layout.getChildren().add(grid);

        HBox bottomRow = new HBox(15);
        bottomRow.setAlignment(Pos.CENTER_RIGHT);
        Button cancel = new Button("Abbrechen");
        cancel.getStyleClass().add("button-cancel");
        cancel.setOnAction(e -> stage.close());
        Button save = new Button("Speichern");
        save.getStyleClass().add("button-start");
        save.setOnAction(e -> {
            int selectedIdx = optModeCombo.getSelectionModel().getSelectedIndex();
            int optMode = (selectedIdx == 0) ? 1 : 2; // 0 -> Complete (1), 1 -> Genetic (2)
            engine.setOptimizationMode(optMode);
            engine.setOptimizationCriterion(criterionCombo.getSelectionModel().getSelectedIndex());
            engine.setForwardMode(forwardCombo.getSelectionModel().getSelectedIndex());
            engine.setForwardDate(forwardDatePicker.getValue());
            engine.saveState();
            stage.close();
        });

        bottomRow.getChildren().addAll(cancel, save);
        layout.getChildren().add(bottomRow);

        stage.setScene(new Scene(layout));
        applyTheme(stage, owner);
        stage.showAndWait();
    }

    // ─── Step 3: Diversity Filter Settings ──────────────────────────────────────

    public static void showStep3Dialog(WorkflowEngine engine, Window owner) {
        Stage stage = new Stage();
        stage.setTitle("Schritt 3: Top-5 Diversitäts-Filter");

        VBox layout = new VBox(15);
        layout.setStyle("-fx-background-color: #0b0d13; -fx-padding: 20;");
        layout.setPrefWidth(550);

        Label title = new Label("STRATEGIE-FILTER & DIVERSITÄT");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#00e5ff"));
        layout.getChildren().add(title);

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(12);
        grid.getStyleClass().add("sci-fi-panel");

        // Filter thresholds
        grid.add(new Label("Min. Backtest Profit:"), 0, 0);
        TextField minBtProfitField = new TextField(String.valueOf(engine.getMinBtProfit()));
        grid.add(minBtProfitField, 1, 0);

        grid.add(new Label("Min. Forward Profit:"), 2, 0);
        TextField minFwProfitField = new TextField(String.valueOf(engine.getMinFwProfit()));
        grid.add(minFwProfitField, 3, 0);

        grid.add(new Label("Min. Backtest Trades:"), 0, 1);
        TextField minBtTradesField = new TextField(String.valueOf(engine.getMinBtTrades()));
        grid.add(minBtTradesField, 1, 1);

        grid.add(new Label("Min. Forward Trades:"), 2, 1);
        TextField minFwTradesField = new TextField(String.valueOf(engine.getMinFwTrades()));
        grid.add(minFwTradesField, 3, 1);

        grid.add(new Label("Max. Backtest DD %:"), 0, 2);
        TextField maxBtDdField = new TextField(String.valueOf(engine.getMaxBtDd()));
        grid.add(maxBtDdField, 1, 2);

        grid.add(new Label("Max. Forward DD %:"), 2, 2);
        TextField maxFwDdField = new TextField(String.valueOf(engine.getMaxFwDd()));
        grid.add(maxFwDdField, 3, 2);

        // Diversity delta thresholds
        grid.add(new Label("Param Differenz %:"), 0, 4);
        TextField paramDiffField = new TextField(String.format(Locale.US, "%.0f", engine.getParamDiffPct() * 100));
        grid.add(paramDiffField, 1, 4);

        grid.add(new Label("Trades Differenz %:"), 2, 4);
        TextField tradeDiffField = new TextField(String.format(Locale.US, "%.0f", engine.getTradeDiffPct() * 100));
        grid.add(tradeDiffField, 3, 4);

        grid.add(new Label("Min. differente Params:"), 0, 5);
        Spinner<Integer> minDiffParamsSpin = new Spinner<>(1, 10, engine.getMinDifferentParams(), 1);
        grid.add(minDiffParamsSpin, 1, 5);

        grid.add(new Label("Max. Strategien (Ziel):"), 2, 5);
        Spinner<Integer> maxStratsSpin = new Spinner<>(1, 20, engine.getMaxStrategiesToSelect(), 1);
        grid.add(maxStratsSpin, 3, 5);

        // Set row stylings for separator
        Label sepLabel = new Label("DIVERSITÄTS-METRIKEN (ÄHNLICHKEITS-SCHWELLWERTE)");
        sepLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        sepLabel.setTextFill(Color.web("#ffd740"));
        grid.add(sepLabel, 0, 3, 4, 1);

        layout.getChildren().add(grid);

        // Display currently selected passes in Step 3 if any
        if (engine.getSelectedDiversePasses() != null && !engine.getSelectedDiversePasses().isEmpty()) {
            VBox resultsBox = new VBox(5);
            resultsBox.getChildren().add(new Label("Aktuell selektierte diverse Durchgänge:"));
            TableView<CombinedPass> table = new TableView<>();
            table.setPrefHeight(150);
            
            TableColumn<CombinedPass, Integer> passCol = new TableColumn<>("Pass");
            passCol.setCellValueFactory(new PropertyValueFactory<>("passNumber"));
            
            TableColumn<CombinedPass, Double> scoreCol = new TableColumn<>("Score");
            scoreCol.setCellValueFactory(new PropertyValueFactory<>("score"));
            
            TableColumn<CombinedPass, Double> btProf = new TableColumn<>("BT Profit");
            btProf.setCellValueFactory(new PropertyValueFactory<>("btProfit"));

            TableColumn<CombinedPass, Integer> btTr = new TableColumn<>("BT Trades");
            btTr.setCellValueFactory(new PropertyValueFactory<>("btTrades"));

            table.getColumns().addAll(passCol, scoreCol, btProf, btTr);
            table.getItems().setAll(engine.getSelectedDiversePasses());
            resultsBox.getChildren().add(table);
            layout.getChildren().add(resultsBox);
        }

        HBox bottomRow = new HBox(15);
        bottomRow.setAlignment(Pos.CENTER_RIGHT);
        Button cancel = new Button("Abbrechen");
        cancel.getStyleClass().add("button-cancel");
        cancel.setOnAction(e -> stage.close());
        Button save = new Button("Speichern");
        save.getStyleClass().add("button-start");
        save.setOnAction(e -> {
            try { engine.setMinBtProfit(Double.parseDouble(minBtProfitField.getText().trim())); } catch (Exception ignored) {}
            try { engine.setMinFwProfit(Double.parseDouble(minFwProfitField.getText().trim())); } catch (Exception ignored) {}
            try { engine.setMinBtTrades(Integer.parseInt(minBtTradesField.getText().trim())); } catch (Exception ignored) {}
            try { engine.setMinFwTrades(Integer.parseInt(minFwTradesField.getText().trim())); } catch (Exception ignored) {}
            try { engine.setMaxBtDd(Double.parseDouble(maxBtDdField.getText().trim())); } catch (Exception ignored) {}
            try { engine.setMaxFwDd(Double.parseDouble(maxFwDdField.getText().trim())); } catch (Exception ignored) {}
            try { engine.setParamDiffPct(Double.parseDouble(paramDiffField.getText().trim()) / 100.0); } catch (Exception ignored) {}
            try { engine.setTradeDiffPct(Double.parseDouble(tradeDiffField.getText().trim()) / 100.0); } catch (Exception ignored) {}
            engine.setMinDifferentParams(minDiffParamsSpin.getValue());
            engine.setMaxStrategiesToSelect(maxStratsSpin.getValue());
            engine.saveState();
            stage.close();
        });

        bottomRow.getChildren().addAll(cancel, save);
        layout.getChildren().add(bottomRow);

        stage.setScene(new Scene(layout));
        applyTheme(stage, owner);
        stage.showAndWait();
    }

    // ─── Step 4: Sensitivity Sweep ─────────────────────────────────────────────

    public static void showStep4Dialog(WorkflowEngine engine, Window owner) {
        Stage stage = new Stage();
        stage.setTitle("Schritt 4: Sensitivitäts-Analyse");

        VBox layout = new VBox(15);
        layout.setStyle("-fx-background-color: #0b0d13; -fx-padding: 20;");
        layout.setPrefWidth(850);
        layout.setPrefHeight(650);

        Label title = new Label("SENSITIVITÄT & ROBUSTHEITS-ERGEBNISSE");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#00e5ff"));
        layout.getChildren().add(title);

        VBox infoBox = new VBox(5);
        infoBox.getStyleClass().add("sci-fi-panel");
        infoBox.getChildren().add(new Label("Info: Für jede der in Schritt 3 selektierten Strategien"));
        infoBox.getChildren().add(new Label("wird eine Parameterverschiebung (Sweep) durchgeführt."));
        layout.getChildren().add(infoBox);

        // Parameters Table for verification
        Label paramLabel = new Label("Eingestellte Parameter & Suchräume (aus Schritt 1):");
        paramLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        paramLabel.setTextFill(Color.web("#80d8ff"));
        layout.getChildren().add(paramLabel);

        TableView<EaParameter> paramTable = new TableView<>();
        paramTable.setEditable(false);
        paramTable.setPrefHeight(180);
        paramTable.setStyle("-fx-background-color: transparent;");

        TableColumn<EaParameter, Boolean> optCol = new TableColumn<>("Opt");
        optCol.setCellValueFactory(cellData -> new javafx.beans.property.SimpleBooleanProperty(cellData.getValue().isOptimizeEnabled()));
        optCol.setCellFactory(tc -> new TableCell<EaParameter, Boolean>() {
            private final CheckBox checkBox = new CheckBox();
            {
                checkBox.setDisable(true);
                setAlignment(Pos.CENTER);
            }
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    checkBox.setSelected(item);
                    setGraphic(checkBox);
                }
            }
        });
        optCol.setPrefWidth(40);

        TableColumn<EaParameter, String> nameCol = new TableColumn<>("Variable");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(220);

        TableColumn<EaParameter, String> valCol = new TableColumn<>("Wert");
        valCol.setCellValueFactory(new PropertyValueFactory<>("value"));
        valCol.setPrefWidth(100);

        TableColumn<EaParameter, String> startCol = new TableColumn<>("Start");
        startCol.setCellValueFactory(new PropertyValueFactory<>("optimizeStart"));
        startCol.setPrefWidth(90);

        TableColumn<EaParameter, String> stepCol = new TableColumn<>("Schritt");
        stepCol.setCellValueFactory(new PropertyValueFactory<>("optimizeStep"));
        stepCol.setPrefWidth(90);

        TableColumn<EaParameter, String> stopCol = new TableColumn<>("Stopp");
        stopCol.setCellValueFactory(new PropertyValueFactory<>("optimizeEnd"));
        stopCol.setPrefWidth(90);

        paramTable.getColumns().addAll(optCol, nameCol, valCol, startCol, stepCol, stopCol);
        if (engine.getEaParameters() != null) {
            paramTable.getItems().setAll(engine.getEaParameters());
        }
        layout.getChildren().add(paramTable);

        // Results Section
        Label resultsLabel = new Label("Robustheits-Ergebnisse (Sensitivitäts-Sweeps):");
        resultsLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        resultsLabel.setTextFill(Color.web("#80d8ff"));
        layout.getChildren().add(resultsLabel);

        // Display current sweep results if any
        if (engine.getSensitivityResults() != null && !engine.getSensitivityResults().isEmpty()) {
            TableView<SensitivityResult> table = new TableView<>();
            table.setPrefHeight(180);

            TableColumn<SensitivityResult, Integer> passCol = new TableColumn<>("Pass");
            passCol.setCellValueFactory(c -> new javafx.beans.property.SimpleIntegerProperty(c.getValue().getOriginalPass().getPassNumber()).asObject());
            passCol.setPrefWidth(80);
            
            TableColumn<SensitivityResult, String> btCvCol = new TableColumn<>("BT CV (worst)");
            btCvCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(String.format("%.2f %%", c.getValue().getOverallCV())));
            btCvCol.setPrefWidth(120);

            TableColumn<SensitivityResult, String> fwCvCol = new TableColumn<>("FW CV (worst)");
            fwCvCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().hasForwardCV() ? String.format("%.2f %%", c.getValue().getOverallCVFw()) : "-"));
            fwCvCol.setPrefWidth(120);

            TableColumn<SensitivityResult, String> statusCol = new TableColumn<>("Status");
            statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
            statusCol.setPrefWidth(150);

            table.getColumns().addAll(passCol, btCvCol, fwCvCol, statusCol);
            table.getItems().setAll(engine.getSensitivityResults());
            layout.getChildren().add(table);
        } else {
            Label noRes = new Label("Noch keine Sensitivitätsergebnisse vorhanden.");
            noRes.setStyle("-fx-text-fill: #7e889a;");
            layout.getChildren().add(noRes);
        }

        HBox bottomRow = new HBox(15);
        bottomRow.setAlignment(Pos.CENTER_RIGHT);
        Button ok = new Button("Schließen");
        ok.getStyleClass().add("button-start");
        ok.setOnAction(e -> stage.close());
        bottomRow.getChildren().add(ok);
        layout.getChildren().add(bottomRow);

        stage.setScene(new Scene(layout));
        applyTheme(stage, owner);
        stage.showAndWait();
    }

    // ─── Step 5: KI Analysis Setup ──────────────────────────────────────────────

    public static void showStep5Dialog(WorkflowEngine engine, Window owner) {
        Stage stage = new Stage();
        stage.setTitle("Schritt 5: KI Bewertung & OpenRouter Setup");

        VBox layout = new VBox(15);
        layout.setStyle("-fx-background-color: #0b0d13; -fx-padding: 20;");
        layout.setPrefWidth(650);
        layout.setPrefHeight(600);

        Label title = new Label("KI STABILITÄTS-ANALYSE EINSTELLUNGEN");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#00e5ff"));
        layout.getChildren().add(title);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.getStyleClass().add("sci-fi-panel");

        grid.add(new Label("OpenRouter API Key:"), 0, 0);
        PasswordField keyField = new PasswordField();
        keyField.setText(engine.getOpenRouterApiKey());
        keyField.setPrefWidth(350);
        grid.add(keyField, 1, 0);

        grid.add(new Label("LLM Modell:"), 0, 1);
        ComboBox<String> modelCombo = new ComboBox<>();
        modelCombo.getItems().addAll(
            "openai/gpt-4o-mini",
            "moonshotai/kimi-k2.6",
            "anthropic/claude-3-haiku",
            "google/gemini-2.5-flash",
            "google/gemini-3-flash-preview"
        );
        modelCombo.setEditable(true);
        modelCombo.setValue(engine.getOpenRouterModel());
        grid.add(modelCombo, 1, 1);

        layout.getChildren().add(grid);

        VBox promptBox = new VBox(5);
        VBox.setVgrow(promptBox, Priority.ALWAYS);
        promptBox.getChildren().add(new Label("Custom System Prompt:"));
        TextArea promptArea = new TextArea(engine.getOpenRouterPrompt());
        promptArea.setFont(Font.font("Consolas", 12));
        promptArea.setWrapText(true);
        VBox.setVgrow(promptArea, Priority.ALWAYS);
        promptBox.getChildren().add(promptArea);
        layout.getChildren().add(promptBox);

        HBox bottomRow = new HBox(15);
        bottomRow.setAlignment(Pos.CENTER_RIGHT);
        Button cancel = new Button("Abbrechen");
        cancel.getStyleClass().add("button-cancel");
        cancel.setOnAction(e -> stage.close());
        Button save = new Button("Speichern");
        save.getStyleClass().add("button-start");
        save.setOnAction(e -> {
            engine.setOpenRouterApiKey(keyField.getText().trim());
            engine.setOpenRouterModel(modelCombo.getValue());
            engine.setOpenRouterPrompt(promptArea.getText().trim());
            engine.savePreferences();
            engine.saveState();
            stage.close();
        });

        bottomRow.getChildren().addAll(cancel, save);
        layout.getChildren().add(bottomRow);

        stage.setScene(new Scene(layout));
        applyTheme(stage, owner);
        stage.showAndWait();
    }

    // ─── Step 6: Export & Final Selection ──────────────────────────────────────

    public static void showStep6Dialog(WorkflowEngine engine, Window owner) {
        Stage stage = new Stage();
        stage.setTitle("Schritt 6: Finales Portfolio & Export");

        VBox layout = new VBox(15);
        layout.setStyle("-fx-background-color: #0b0d13; -fx-padding: 20;");
        layout.setPrefWidth(850);
        layout.setPrefHeight(500);

        Label title = new Label("PORTFOLIO DER 3-5 BESTEN STRATEGIEN");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        title.setTextFill(Color.web("#00e5ff"));
        layout.getChildren().add(title);

        TableView<CombinedPass> table = new TableView<>();
        table.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<CombinedPass, Integer> passCol = new TableColumn<>("Pass");
        passCol.setCellValueFactory(new PropertyValueFactory<>("passNumber"));
        passCol.setPrefWidth(60);

        TableColumn<CombinedPass, Double> scoreCol = new TableColumn<>("Komb. Score");
        scoreCol.setCellValueFactory(new PropertyValueFactory<>("score"));
        scoreCol.setPrefWidth(90);

        TableColumn<CombinedPass, Double> btProf = new TableColumn<>("BT Profit");
        btProf.setCellValueFactory(new PropertyValueFactory<>("btProfit"));

        TableColumn<CombinedPass, Integer> btTr = new TableColumn<>("BT Trades");
        btTr.setCellValueFactory(new PropertyValueFactory<>("btTrades"));

        TableColumn<CombinedPass, Double> btDd = new TableColumn<>("BT DD%");
        btDd.setCellValueFactory(new PropertyValueFactory<>("btDd"));

        TableColumn<CombinedPass, Double> fwProf = new TableColumn<>("FW Profit");
        fwProf.setCellValueFactory(new PropertyValueFactory<>("fwProfit"));

        TableColumn<CombinedPass, Integer> fwTr = new TableColumn<>("FW Trades");
        fwTr.setCellValueFactory(new PropertyValueFactory<>("fwTrades"));

        table.getColumns().addAll(passCol, scoreCol, btProf, btTr, btDd, fwProf, fwTr);

        List<CombinedPass> results = engine.getFinalSelectedPasses();
        table.getItems().setAll(results);
        layout.getChildren().add(table);

        HBox actionsRow = new HBox(15);
        actionsRow.setAlignment(Pos.CENTER_LEFT);
        
        Button exportBtn = new Button("💾 Als .set Dateien exportieren...");
        exportBtn.getStyleClass().add("button-start");
        exportBtn.setOnAction(e -> {
            CombinedPass selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                new Alert(Alert.AlertType.WARNING, "Bitte markiere erst die Strategie in der Tabelle, die du exportieren möchtest.").show();
                return;
            }
            FileChooser ch = new FileChooser();
            ch.setTitle("Save Strategy Preset");
            ch.setInitialFileName("Pass_" + selected.getPassNumber() + ".set");
            ch.getExtensionFilters().add(new FileChooser.ExtensionFilter("MT5 Set Files", "*.set"));
            File file = ch.showSaveDialog(stage);
            if (file != null) {
                // Construct a set parameter list based on base params mutated by this pass
                List<EaParameter> finalParams = new ArrayList<>();
                for (EaParameter base : engine.getEaParameters()) {
                    EaParameter p = new EaParameter();
                    p.setName(base.getName());
                    p.setStringType(base.isStringType());
                    String passVal = selected.getBacktestPass().getParameter(base.getName());
                    if (passVal != null && !passVal.isEmpty()) {
                        p.setValue(passVal);
                    } else {
                        p.setValue(base.getValue());
                    }
                    p.setOptimizeEnabled(false);
                    finalParams.add(p);
                }
                eaParamManager.writeSetFile(file.toPath(), finalParams, EaParameterManager.extractEaBaseName(engine.getExpert()));
                new Alert(Alert.AlertType.INFORMATION, "Preset erfolgreich gespeichert!").show();
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("Schließen");
        closeBtn.getStyleClass().add("button");
        closeBtn.setOnAction(e -> stage.close());

        actionsRow.getChildren().addAll(exportBtn, spacer, closeBtn);
        layout.getChildren().add(actionsRow);

        stage.setScene(new Scene(layout));
        applyTheme(stage, owner);
        stage.showAndWait();
    }
}
