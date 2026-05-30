package com.backtester.ui.javafx;

import com.backtester.config.EaParameter;
import com.backtester.config.AppConfig;
import javafx.scene.web.WebView;
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
        layout.setPrefWidth(1000);
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
        expertField.setPrefWidth(400);
        Button browseBtn = new Button("...");
        HBox expBox = new HBox(5, expertField, browseBtn);
        grid.add(expBox, 1, 0, 2, 1);

        grid.add(new Label("Symbol(s):"), 0, 1);
        TextField symbolField = new TextField(engine.getSymbol());
        symbolField.setPrefWidth(300);
        symbolField.setTooltip(new Tooltip("Geben Sie ein oder mehrere Währungspaare kommagetrennt ein (z.B. EURUSD,GBPUSD)"));
        Button selectSymbolsBtn = new Button("Wählen...");
        HBox symbolBox = new HBox(5, symbolField, selectSymbolsBtn);
        grid.add(symbolBox, 1, 1);


        grid.add(new Label("Periode:"), 2, 1);
        ComboBox<String> periodCombo = new ComboBox<>(FXCollections.observableArrayList("M1", "M5", "M15", "M30", "H1", "H4", "D1"));
        periodCombo.setValue(engine.getPeriod());
        grid.add(periodCombo, 3, 1);

        grid.add(new Label("Presets:"), 0, 2);
        Button set1Btn = new Button("Set 1 (M5 Core Pairs)");
        grid.add(set1Btn, 1, 2);

        grid.add(new Label("Datum von:"), 0, 3);
        DatePicker fromDatePicker = new DatePicker(engine.getFromDate());
        fromDatePicker.setConverter(createDateConverter());
        grid.add(fromDatePicker, 1, 3);

        grid.add(new Label("bis:"), 2, 3);
        DatePicker toDatePicker = new DatePicker(engine.getToDate());
        toDatePicker.setConverter(createDateConverter());
        grid.add(toDatePicker, 3, 3);

        grid.add(new Label("Konto / Währung:"), 0, 4);
        TextField depField = new TextField(String.valueOf(engine.getDeposit()));
        TextField curField = new TextField(engine.getCurrency());
        depField.setPrefWidth(80);
        curField.setPrefWidth(50);
        HBox depCur = new HBox(5, depField, curField);
        grid.add(depCur, 1, 4);

        grid.add(new Label("Hebel / Modell:"), 2, 4);
        TextField levField = new TextField(engine.getLeverage());
        levField.setPrefWidth(80);
        ComboBox<String> modelCombo = new ComboBox<>(FXCollections.observableArrayList(OptimizationConfig.MODEL_NAMES));
        if (engine.getTickModel() >= 0 && engine.getTickModel() < modelCombo.getItems().size()) {
            modelCombo.getSelectionModel().select(engine.getTickModel());
        } else {
            modelCombo.getSelectionModel().select(1); // Every tick
        }
        HBox levMod = new HBox(5, levField, modelCombo);
        grid.add(levMod, 3, 4);

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
        valCol.setCellFactory(EnumAwareParamCell.forTableColumn());
        valCol.setOnEditCommit(e -> e.getRowValue().setValue(e.getNewValue()));
        valCol.setPrefWidth(100);

        TableColumn<EaParameter, String> startCol = new TableColumn<>("Start");
        startCol.setCellValueFactory(new PropertyValueFactory<>("optimizeStart"));
        startCol.setCellFactory(EnumAwareParamCell.forTableColumn());
        startCol.setOnEditCommit(e -> e.getRowValue().setOptimizeStart(e.getNewValue()));

        TableColumn<EaParameter, String> stepCol = new TableColumn<>("Schritt");
        stepCol.setCellValueFactory(new PropertyValueFactory<>("optimizeStep"));
        stepCol.setCellFactory(EnumAwareParamCell.forTableColumn());
        stepCol.setOnEditCommit(e -> e.getRowValue().setOptimizeStep(e.getNewValue()));

        TableColumn<EaParameter, String> stopCol = new TableColumn<>("Stopp");
        stopCol.setCellValueFactory(new PropertyValueFactory<>("optimizeEnd"));
        stopCol.setCellFactory(EnumAwareParamCell.forTableColumn());
        stopCol.setOnEditCommit(e -> e.getRowValue().setOptimizeEnd(e.getNewValue()));

        paramTable.getColumns().addAll(optCol, nameCol, valCol, startCol, stepCol, stopCol);

        final String[] lastCheckedExpert = { engine.getExpert() != null ? engine.getExpert().trim() : "" };
        final String[] lastCheckedSymbol = { engine.getSymbol() != null ? engine.getSymbol().trim() : "" };
        final String[] lastCheckedPeriod = { engine.getPeriod() != null ? engine.getPeriod().trim() : "" };

        Runnable updateParamsTable = () -> {
            String expert = expertField.getText().trim();
            String rawSymbol = symbolField.getText().trim();
            String period = periodCombo.getValue() != null ? periodCombo.getValue().trim() : "";

            if (expert.isEmpty()) {
                paramTable.getItems().clear();
                lastCheckedExpert[0] = "";
                lastCheckedSymbol[0] = rawSymbol;
                lastCheckedPeriod[0] = period;
                return;
            }

            boolean expertChanged = !expert.equals(lastCheckedExpert[0]);
            boolean symbolChanged = !rawSymbol.equals(lastCheckedSymbol[0]);
            boolean periodChanged = !period.equals(lastCheckedPeriod[0]);

            if (!expertChanged && !symbolChanged && !periodChanged) {
                return;
            }

            lastCheckedExpert[0] = expert;
            lastCheckedSymbol[0] = rawSymbol;
            lastCheckedPeriod[0] = period;

            if (expertChanged) {
                String strategyConfigJson = null;
                try {
                    strategyConfigJson = com.backtester.database.DatabaseManager.getInstance().getWorkflowStrategyConfig(expert);
                } catch (Exception ignored) {}

                if (strategyConfigJson != null && !strategyConfigJson.isEmpty()) {
                    try {
                        boolean loaded = engine.loadStrategyConfig(expert);
                        if (loaded) {
                            String newSymbol = engine.getSymbol() != null ? engine.getSymbol().trim() : "";
                            String newPeriod = engine.getPeriod() != null ? engine.getPeriod().trim() : "";
                            lastCheckedSymbol[0] = newSymbol;
                            lastCheckedPeriod[0] = newPeriod;

                            symbolField.setText(engine.getSymbol());
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

            String symbol = "EURUSD";
            if (!rawSymbol.isEmpty()) {
                if (rawSymbol.contains(",")) {
                    symbol = rawSymbol.split(",\\s*")[0];
                } else {
                    symbol = rawSymbol;
                }
            }
            String periodDb = period.isEmpty() ? "H1" : period;

            // Try DB first
            String dbParamsJson = null;
            try {
                dbParamsJson = com.backtester.database.DatabaseManager.getInstance().getEaParameterSettings(expert, symbol, periodDb);
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

        selectSymbolsBtn.setOnAction(e -> {
            Stage selectionStage = new Stage();
            selectionStage.setTitle("Währungspaare auswählen");
            selectionStage.initModality(Modality.APPLICATION_MODAL);
            selectionStage.initOwner(stage);
            
            VBox selLayout = new VBox(10);
            selLayout.setStyle("-fx-background-color: #0b0d13; -fx-padding: 15;");
            selLayout.setPrefWidth(450);
            
            Label selTitle = new Label("WÄHRUNGSPAARE AUSWÄHLEN");
            selTitle.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
            selTitle.setTextFill(Color.web("#00e5ff"));
            
            GridPane checkboxesGrid = new GridPane();
            checkboxesGrid.setHgap(15);
            checkboxesGrid.setVgap(8);
            
            List<CheckBox> cbs = new ArrayList<>();
            String currentText = symbolField.getText().trim().toUpperCase();
            List<String> currentSyms = Arrays.asList(currentText.split(",\\s*"));
            
            int rowIdx = 0;
            int colIdx = 0;
            for (String sym : com.backtester.engine.BacktestConfig.SYMBOLS) {
                CheckBox cb = new CheckBox(sym);
                cb.setTextFill(Color.web("#e6e9f0"));
                cb.setSelected(currentSyms.contains(sym));
                checkboxesGrid.add(cb, colIdx, rowIdx);
                cbs.add(cb);
                
                colIdx++;
                if (colIdx > 2) {
                    colIdx = 0;
                    rowIdx++;
                }
            }
            
            HBox addCustomBox = new HBox(5);
            TextField customSymInput = new TextField();
            customSymInput.setPromptText("Zusätzliches Symbol (z.B. BTCUSD)");
            Button addCustomBtn = new Button("Hinzufügen");
            addCustomBox.getChildren().addAll(customSymInput, addCustomBtn);
            
            final int[] nextRow = { rowIdx + 1 };
            final int[] nextCol = { 0 };
            
            addCustomBtn.setOnAction(evt -> {
                String val = customSymInput.getText().trim().toUpperCase();
                if (!val.isEmpty()) {
                    boolean alreadyExists = cbs.stream().anyMatch(cb -> cb.getText().equals(val));
                    if (!alreadyExists) {
                        CheckBox cb = new CheckBox(val);
                        cb.setTextFill(Color.web("#e6e9f0"));
                        cb.setSelected(true);
                        checkboxesGrid.add(cb, nextCol[0], nextRow[0]);
                        cbs.add(cb);
                        
                        nextCol[0]++;
                        if (nextCol[0] > 2) {
                            nextCol[0] = 0;
                            nextRow[0]++;
                        }
                    }
                    customSymInput.clear();
                }
            });
            
            ScrollPane scroll = new ScrollPane(checkboxesGrid);
            scroll.setFitToWidth(true);
            scroll.setPrefHeight(250);
            scroll.setStyle("-fx-background: #11141d; -fx-background-color: transparent; -fx-border-color: #3e4555;");
            
            HBox btnBox = new HBox(10);
            btnBox.setAlignment(Pos.CENTER_RIGHT);
            Button okBtn = new Button("OK");
            okBtn.getStyleClass().add("button-start");
            okBtn.setOnAction(evt -> {
                List<String> selected = new ArrayList<>();
                for (CheckBox cb : cbs) {
                    if (cb.isSelected()) {
                        selected.add(cb.getText());
                    }
                }
                symbolField.setText(String.join(",", selected));
                updateParamsTable.run();
                selectionStage.close();
            });
            
            Button clearBtn = new Button("Alle abwählen");
            clearBtn.setOnAction(evt -> {
                for (CheckBox cb : cbs) {
                    cb.setSelected(false);
                }
            });
            
            btnBox.getChildren().addAll(clearBtn, okBtn);
            
            selLayout.getChildren().addAll(selTitle, scroll, addCustomBox, btnBox);
            
            Scene selScene = new Scene(selLayout);
            if (stage.getScene() != null && !stage.getScene().getStylesheets().isEmpty()) {
                selScene.getStylesheets().addAll(stage.getScene().getStylesheets());
            }
            selectionStage.setScene(selScene);
            selectionStage.showAndWait();
        });

        set1Btn.setOnAction(e -> {
            symbolField.setText("AUDJPY,AUDUSD,EURAUD,EURCHF,EURGBP,EURJPY,EURUSD,GBPCHF,GBPJPY,GBPUSD,NZDUSD,USDCAD,USDCHF,USDJPY");
            periodCombo.setValue("M5");
            updateParamsTable.run();
        });

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
        symbolField.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                updateParamsTable.run();
            }
        });
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
                symbolField.setText(robSymbol);
                periodCombo.setValue(robPeriod);
                
                lastCheckedExpert[0] = robExpert;
                lastCheckedSymbol[0] = robSymbol;
                lastCheckedPeriod[0] = robPeriod;
                
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
            engine.setSymbol(symbolField.getText().trim());
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
            passCol.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getPassNumber()));
            passCol.setPrefWidth(65);
            passCol.setStyle("-fx-alignment: CENTER;");
            
            TableColumn<CombinedPass, Double> scoreCol = new TableColumn<>();
            scoreCol.setGraphic(DocHelper.createHeaderWithTooltip("Score", 
                "Unified Score (0-100):\nGewichteter Gesamtwert aus 10 Kriterien (Profit, DD, PF etc.). Konfigurierbar über das Regler-Symbol. Zeigt die beste Gesamtperformance."));
            scoreCol.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getScore()));
            scoreCol.setPrefWidth(75);
            scoreCol.setStyle("-fx-alignment: CENTER;");
            scoreCol.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
                @Override
                protected void updateItem(Double item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("-fx-alignment: CENTER;");
                    } else {
                        setText(String.format(Locale.US, "%.1f", item));
                        setStyle("-fx-alignment: CENTER;");
                    }
                }
            });
            
            TableColumn<CombinedPass, String> robScoreCol = new TableColumn<>();
            robScoreCol.setGraphic(DocHelper.createHeaderWithTooltip("Rob. Scorecard", 
                "Robustness Scorecard (0-100):\nErgebnis des Monte-Carlo-Stresstests und systematischen Parameter-Shifting. Simuliert Rauschen (Slippage, Spread, Execution) und bewertet die Geradlinigkeit (R²-Stabilität) der Equity-Kurve."));
            robScoreCol.setCellValueFactory(c -> {
                String fromDateStr = engine.getFromDate() != null ? engine.getFromDate().toString() : "Unbekannt";
                String toDateStr = engine.getToDate() != null ? engine.getToDate().toString() : "Unbekannt";
                double score = c.getValue().getCachedOverallScore(fromDateStr, toDateStr);
                return new javafx.beans.property.SimpleStringProperty(String.format(Locale.US, "%.0f", score));
            });
            robScoreCol.setStyle("-fx-alignment: CENTER;");
            robScoreCol.setCellFactory(col -> new TableCell<CombinedPass, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("-fx-alignment: CENTER;");
                    } else {
                        setText(item);
                        try {
                            double score = Double.parseDouble(item);
                            String color;
                            if (score >= 70) {
                                color = "#00e676"; // Green
                            } else if (score >= 55) {
                                color = "#ffd740"; // Yellow
                            } else {
                                color = "#ff5252"; // Red
                            }
                            setStyle("-fx-alignment: CENTER; -fx-text-fill: " + color + "; -fx-font-weight: bold;");
                        } catch (Exception e) {
                            setStyle("-fx-alignment: CENTER;");
                        }
                    }
                }
            });
            robScoreCol.setPrefWidth(115);

            TableColumn<CombinedPass, Double> btProf = new TableColumn<>("BT Profit");
            btProf.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getBtProfit()));
            btProf.setPrefWidth(95);
            btProf.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
                @Override
                protected void updateItem(Double item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null || Double.isNaN(item)) {
                        setText("-");
                        setStyle("-fx-alignment: CENTER;");
                    } else {
                        setText(String.format(Locale.US, "%.2f", item));
                        if (item >= 0) {
                            setStyle("-fx-alignment: CENTER; -fx-text-fill: #00e676; -fx-font-weight: bold;");
                        } else {
                            setStyle("-fx-alignment: CENTER; -fx-text-fill: #ff5252; -fx-font-weight: bold;");
                        }
                    }
                }
            });

            TableColumn<CombinedPass, Integer> btTr = new TableColumn<>("BT Trades");
            btTr.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getBtTrades()));
            btTr.setPrefWidth(85);
            btTr.setCellFactory(col -> new TableCell<CombinedPass, Integer>() {
                @Override
                protected void updateItem(Integer item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText("-");
                        setStyle("-fx-alignment: CENTER;");
                    } else {
                        setText(String.valueOf(item));
                        setStyle("-fx-alignment: CENTER;");
                    }
                }
            });

            table.getColumns().addAll(passCol, scoreCol, robScoreCol, btProf, btTr);
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

        Button mainInfoBtn = DocHelper.createThickCircularInfoButton("Erklärung aller Indizes und Kennzahlen", () -> {
            DocHelper.showAllIndicesDocDialog(cancel.getScene() != null ? cancel.getScene().getWindow() : null);
        });
        Button diversityInfoBtn = DocHelper.createThickCircularCyanInfoButton("Erklärung des Diversitäts-Filters & Strategie-Auswahl", () -> {
            DocHelper.showDiversityDocDialog(cancel.getScene() != null ? cancel.getScene().getWindow() : null);
        });
        Button weightsBtn = new Button("Score-Gewichtung...");
        weightsBtn.getStyleClass().add("button");
        weightsBtn.setOnAction(e -> {
            showScoreWeightsDialog(stage);
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bottomRow.getChildren().addAll(mainInfoBtn, diversityInfoBtn, weightsBtn, spacer, cancel, save);
        layout.getChildren().add(bottomRow);

        stage.setScene(new Scene(layout));
        applyTheme(stage, owner);
        stage.showAndWait();
    }

    public static void showScoreWeightsDialog(Window owner) {
        Stage dialog = new Stage();
        dialog.setTitle("Score-Gewichtung konfigurieren");
        dialog.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setResizable(false);

        VBox root = new VBox(18);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: #1a1d27;");

        Label title = new Label("⚙️  Unified Score-Gewichtung");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        title.setTextFill(Color.web("#ffd740"));

        Label hint = new Label(
            "Hier konfigurierst du den UNIFIED SCORE (Spalte 'Score' in den Tabellen).\n" +
            "Dieser Score bewertet gewichtet die Endergebnisse der Backtest- und Forward-Phase.\n" +
            "• Er unterscheidet sich vom 'Rob. Scorecard' (Stresstests der Equity-Kurve) und dem 'RI' (mathematisch starrer Index).\n" +
            "• Die Schieberegler bestimmen das relative Gewicht (die Summe wird automatisch normalisiert)."
        );
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 11px;");

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(10);

        String[] names = {
            "BT Profitabilität", "FW Profitabilität", "Konsistenz FW/BT", "Risiko-Verhältnis",
            "Equity-Konsistenz", "Stichprobengröße", "Symmetrie L/S", "Tail-Risk",
            "FW Trade Count", "Erholungsfaktor"
        };
        String[] dbKeys = {
            "opt.weight.btProfit", "opt.weight.fwProfit", "opt.weight.consistency", "opt.weight.risk",
            "opt.weight.equityConsist", "opt.weight.sampleSize", "opt.weight.symmetry", "opt.weight.tailRisk",
            "opt.weight.fwTrades", "opt.weight.recovery"
        };
        String[] defaults = {
            "15", "15", "10", "10", "10", "25", "5", "5", "30", "25"
        };
        String[] tooltips = {
            "Backtest ROI + Profit Factor — Wie profitabel ist die Strategie im In-Sample?",
            "Forward ROI + Profit Factor — Wie profitabel ist die Strategie Out-of-Sample?",
            "Verhältnis FW/BT: 1.0 = perfekte Reproduzierbarkeit der Ergebnisse",
            "Return/Drawdown + Calmar Ratio — Gewinn im Verhältnis zum Risiko",
            "R² Stability + SQN — Wie gleichmäßig steigt die Equity-Kurve?",
            "Anzahl Trades + Testjahre — Statistische Signifikanz der Ergebnisse",
            "Long/Short Balance — Funktioniert die Strategie in beide Richtungen?",
            "MaxLoss/AvgLoss + MaxLoss/Profit — Risiko seltener Extremverluste",
            "Mehr FW-Trades = statistisch belastbarer. Zusätzlich automatische Strafe wenn FW-Trades < median/2.",
            "Recovery Factor: Net Profit / Max Drawdown (BT und FW gemittelt)"
        };

        com.backtester.database.DatabaseManager db = com.backtester.database.DatabaseManager.getInstance();
        int N = names.length;
        Slider[] sliders = new Slider[N];
        Label[] valLabels = new Label[N];

        TextField tfMin = new TextField(db.getSetting("opt.weight.recovery.min", "1.0"));
        tfMin.setPrefWidth(50);
        tfMin.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #fff; -fx-border-color: #444; -fx-border-width: 1; -fx-font-size: 11px;");

        TextField tfMax = new TextField(db.getSetting("opt.weight.recovery.max", "5.0"));
        tfMax.setPrefWidth(50);
        tfMax.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #fff; -fx-border-color: #444; -fx-border-width: 1; -fx-font-size: 11px;");

        for (int i = 0; i < N; i++) {
            Label label = new Label(names[i]);
            label.setMinWidth(140);
            label.setStyle("-fx-text-fill: #b4bac8; -fx-font-size: 12px;");
            label.setTooltip(new Tooltip(tooltips[i]));

            int currentVal = Integer.parseInt(db.getSetting(dbKeys[i], defaults[i]));
            Slider sl = new Slider(0, 100, currentVal);
            sl.setMajorTickUnit(25);
            sl.setMinorTickCount(4);
            sl.setShowTickMarks(true);
            sl.setPrefWidth(260);
            sl.setStyle("-fx-control-inner-background: #2a2d3a;");
            sliders[i] = sl;

            Label vl = new Label(currentVal + "%");
            vl.setMinWidth(36);
            vl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
            vl.setTextFill(Color.web("#00e5ff"));
            valLabels[i] = vl;

            final int idx = i;
            sl.valueProperty().addListener((o, a, b) -> {
                int v = (int) Math.round(b.doubleValue());
                sl.setValue(v);
                valLabels[idx].setText(v + "%");
            });

            grid.add(label, 0, i);
            if (i == 9) {
                HBox scaleBox = new HBox(6);
                scaleBox.setAlignment(Pos.CENTER_LEFT);
                scaleBox.setPadding(new Insets(4, 0, 0, 0));

                Label scaleLabel = new Label("Skalierung: Min");
                scaleLabel.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 11px;");

                Label scaleToLabel = new Label("bis Max");
                scaleToLabel.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 11px;");

                Button infoBtn = new Button("ℹ");
                infoBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ffd740; -fx-cursor: hand; -fx-font-size: 12px; -fx-padding: 0 4 0 4;");
                Tooltip infoTooltip = new Tooltip(
                    "Grenzwerte für die lineare Skalierung des Erholungsfaktors auf 0-100 Punkte.\n" +
                    "Werte unter Min geben 0 Punkte, über Max geben 100 Punkte."
                );
                infoTooltip.setShowDelay(javafx.util.Duration.millis(100));
                Tooltip.install(infoBtn, infoTooltip);
                infoBtn.setOnAction(evt -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Erholungsfaktor Skalierung");
                    alert.setHeaderText("Wie funktioniert die Skalierung des Erholungsfaktors?");
                    alert.setContentText(
                        "Der Erholungsfaktor (Net Profit / Max Drawdown) wird anhand dieser Grenzwerte linear auf 0-100 Punkte skaliert.\n\n" +
                        "• Ein Wert kleiner oder gleich Min erhält 0 Punkte.\n" +
                        "• Ein Wert größer oder gleich Max erhält 100 Punkte.\n" +
                        "• Dazwischen wird linear interpoliert.\n\n" +
                        "Wenn Sie z.B. Min=1.0 und Max=2.0 einstellen, hat eine Strategie mit Recovery Factor = 1.5 genau 50 Punkte."
                    );
                    alert.getDialogPane().setStyle("-fx-background-color: #1a1d27;");
                    alert.getDialogPane().lookup(".content.label").setStyle("-fx-text-fill: #b4bac8;");
                    alert.getDialogPane().lookup(".header-panel").setStyle("-fx-background-color: #1a1d27;");
                    if (alert.getDialogPane().lookup(".header-panel").lookup(".label") != null) {
                        alert.getDialogPane().lookup(".header-panel").lookup(".label").setStyle("-fx-text-fill: #ffd740;");
                    }
                    alert.initOwner(dialog);
                    alert.showAndWait();
                });

                scaleBox.getChildren().addAll(scaleLabel, tfMin, scaleToLabel, tfMax, infoBtn);

                VBox sliderContainer = new VBox(4);
                sliderContainer.getChildren().addAll(sl, scaleBox);
                grid.add(sliderContainer, 1, i);
            } else {
                grid.add(sl, 1, i);
            }
            grid.add(vl, 2, i);
        }

        Label sumLabel = new Label();
        sumLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        Runnable refreshSum = () -> {
            int s = 0;
            for (Slider sl : sliders) s += (int) sl.getValue();
            sumLabel.setText("Σ = " + s + (s == 100 ? "  ✓ optimal" : "  (wird normalisiert)"));
            sumLabel.setTextFill(s == 100 ? Color.web("#00e676") : Color.web("#ffd740"));
        };
        for (Slider sl : sliders) {
            sl.valueProperty().addListener((o, a, b) -> refreshSum.run());
        }
        refreshSum.run();

        Label autoPenaltyHint = new Label(
            "Automatische Schutzschwelle: FW-Trades unter median/2 erhalten zusätzlich " +
            "eine multiplikative Strafe (max. −50 %).\n" +
            "Hinweis: Symmetrie + Tail-Risk werden geschätzt, da MT5 keine L/S-Aufschlüsselung liefert.");
        autoPenaltyHint.setWrapText(true);
        autoPenaltyHint.setStyle("-fx-text-fill: #7e889a; -fx-font-size: 10px; -fx-font-style: italic;");

        Button resetBtn = new Button("↺ Zurücksetzen");
        resetBtn.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #b4bac8; -fx-border-color: #444; -fx-border-width: 1;");
        resetBtn.setOnAction(e -> {
            for (int i = 0; i < N; i++) {
                sliders[i].setValue(Integer.parseInt(defaults[i]));
            }
            tfMin.setText("1.0");
            tfMax.setText("5.0");
        });

        Button applyBtn = new Button("✔ Übernehmen & Schließen");
        applyBtn.setStyle("-fx-background-color: #00e5ff; -fx-text-fill: #0d0f17; -fx-font-weight: bold;");
        applyBtn.setOnAction(e -> {
            double rMin = 1.0;
            double rMax = 5.0;
            try {
                rMin = Double.parseDouble(tfMin.getText().trim().replace(',', '.'));
            } catch (Exception ex) {}
            try {
                rMax = Double.parseDouble(tfMax.getText().trim().replace(',', '.'));
            } catch (Exception ex) {}
            db.saveSetting("opt.weight.recovery.min", String.valueOf(rMin));
            db.saveSetting("opt.weight.recovery.max", String.valueOf(rMax));

            for (int i = 0; i < N; i++) {
                db.saveSetting(dbKeys[i], String.valueOf((int) sliders[i].getValue()));
            }
            dialog.close();
        });

        Button cancelBtn = new Button("Abbrechen");
        cancelBtn.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #b4bac8; -fx-border-color: #444; -fx-border-width: 1;");
        cancelBtn.setOnAction(e -> dialog.close());

        Button btnPresetLow = new Button("Low / Zahm");
        btnPresetLow.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #a7f3d0; -fx-border-color: #10b981; -fx-border-width: 1; -fx-cursor: hand;");
        btnPresetLow.setOnAction(e -> {
            int[] lowWeights = {15, 15, 10, 10, 5, 15, 3, 5, 20, 15};
            for (int i = 0; i < N; i++) sliders[i].setValue(lowWeights[i]);
        });

        Button btnPresetMed = new Button("Med / Ausgewogen");
        btnPresetMed.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #fde047; -fx-border-color: #eab308; -fx-border-width: 1; -fx-cursor: hand;");
        btnPresetMed.setOnAction(e -> {
            int[] medWeights = {10, 15, 15, 15, 10, 25, 5, 10, 30, 25};
            for (int i = 0; i < N; i++) sliders[i].setValue(medWeights[i]);
        });

        Button btnPresetHigh = new Button("High / Streng");
        btnPresetHigh.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #fca5a5; -fx-border-color: #ef4444; -fx-border-width: 1; -fx-cursor: hand;");
        btnPresetHigh.setOnAction(e -> {
            int[] highWeights = {5, 10, 15, 15, 15, 25, 5, 15, 35, 30};
            for (int i = 0; i < N; i++) sliders[i].setValue(highWeights[i]);
        });

        Button btnPresetGrid = new Button("Grid / High-Trade");
        btnPresetGrid.setStyle("-fx-background-color: #2a2d3a; -fx-text-fill: #38bdf8; -fx-border-color: #0284c7; -fx-border-width: 1; -fx-cursor: hand;");
        btnPresetGrid.setOnAction(e -> {
            int[] gridWeights = {5, 5, 15, 5, 5, 35, 5, 5, 35, 40};
            for (int i = 0; i < N; i++) sliders[i].setValue(gridWeights[i]);
        });

        HBox presetRow = new HBox(8, new Label("Voreinstellungen:"), btnPresetLow, btnPresetMed, btnPresetHigh, btnPresetGrid);
        presetRow.setAlignment(Pos.CENTER_LEFT);
        presetRow.getChildren().get(0).setStyle("-fx-text-fill: #b4bac8;");

        Button mainInfoBtn = DocHelper.createThickCircularInfoButton("Erklärung aller Indizes und Kennzahlen", () -> {
            DocHelper.showAllIndicesDocDialog(dialog);
        });
        HBox btnRow = new HBox(10, resetBtn, mainInfoBtn, new Region(), applyBtn, cancelBtn);
        HBox.setHgrow(btnRow.getChildren().get(2), Priority.ALWAYS);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #2a2d3a;");
        Separator sep2 = new Separator();
        sep2.setStyle("-fx-background-color: #2a2d3a;");

        root.getChildren().addAll(title, hint, grid, sep, sumLabel, autoPenaltyHint, presetRow, sep2, btnRow);

        Scene scene = new Scene(root, 540, 710);
        try {
            java.net.URL css = DocHelper.class.getResource("/css/antigravity.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
        } catch (Exception ignored) {}
        dialog.setScene(scene);
        applyTheme(dialog, owner);
        dialog.showAndWait();
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
            
            TableColumn<SensitivityResult, String> btCvCol = new TableColumn<>();
            HBox btCvHeader = new HBox(3);
            btCvHeader.setAlignment(Pos.CENTER_LEFT);
            Label btCvLabel = new Label("BT CV (worst)");
            btCvLabel.setTooltip(new Tooltip("Backtest Variationskoeffizient"));
            Button btCvInfo = new Button("ℹ");
            btCvInfo.setTooltip(new Tooltip("Klicken für Erklärung des BT CV (worst)"));
            btCvInfo.setStyle("-fx-background-color: transparent; -fx-text-fill: #ffd740; -fx-padding: 0; -fx-font-weight: bold; -fx-cursor: hand;");
            btCvInfo.setOnAction(e -> {
                e.consume();
                showCvExplanationDialog(btCvInfo.getScene() != null ? btCvInfo.getScene().getWindow() : null, false);
            });
            btCvHeader.getChildren().addAll(btCvLabel, btCvInfo);
            btCvCol.setGraphic(btCvHeader);
            btCvCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(String.format("%.2f %%", c.getValue().getOverallCV())));
            btCvCol.setPrefWidth(130);

            TableColumn<SensitivityResult, String> fwCvCol = new TableColumn<>();
            HBox fwCvHeader = new HBox(3);
            fwCvHeader.setAlignment(Pos.CENTER_LEFT);
            Label fwCvLabel = new Label("FW CV (worst)");
            fwCvLabel.setTooltip(new Tooltip("Forward Variationskoeffizient"));
            Button fwCvInfo = new Button("ℹ");
            fwCvInfo.setTooltip(new Tooltip("Klicken für Erklärung des FW CV (worst)"));
            fwCvInfo.setStyle("-fx-background-color: transparent; -fx-text-fill: #ffd740; -fx-padding: 0; -fx-font-weight: bold; -fx-cursor: hand;");
            fwCvInfo.setOnAction(e -> {
                e.consume();
                showCvExplanationDialog(fwCvInfo.getScene() != null ? fwCvInfo.getScene().getWindow() : null, true);
            });
            fwCvHeader.getChildren().addAll(fwCvLabel, fwCvInfo);
            fwCvCol.setGraphic(fwCvHeader);
            fwCvCol.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().hasForwardCV() ? String.format("%.2f %%", c.getValue().getOverallCVFw()) : "-"));
            fwCvCol.setPrefWidth(130);

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
        layout.setPrefHeight(640);

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

        grid.add(new Label("Gewichtung Performance (0.0 - 1.0):"), 0, 2);
        TextField perfWeightField = new TextField(String.valueOf(engine.getPerformanceWeight()));
        perfWeightField.setStyle("-fx-control-inner-background: #1a1d27; -fx-text-fill: white; -fx-font-size: 14px;");
        grid.add(perfWeightField, 1, 2);

        grid.add(new Label("Gewichtung Stabilität (0.0 - 1.0):"), 0, 3);
        TextField stabWeightField = new TextField(String.valueOf(engine.getStabilityWeight()));
        stabWeightField.setStyle("-fx-control-inner-background: #1a1d27; -fx-text-fill: white; -fx-font-size: 14px;");
        grid.add(stabWeightField, 1, 3);

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
            try {
                engine.setPerformanceWeight(Double.parseDouble(perfWeightField.getText().trim()));
            } catch (Exception ignored) {}
            try {
                engine.setStabilityWeight(Double.parseDouble(stabWeightField.getText().trim()));
            } catch (Exception ignored) {}
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
        passCol.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getPassNumber()));
        passCol.setPrefWidth(65);
        passCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<CombinedPass, Double> scoreCol = new TableColumn<>();
        scoreCol.setGraphic(DocHelper.createHeaderWithTooltip("Score", 
            "Unified Score (0-100):\nGewichteter Gesamtwert aus 10 Kriterien (Profit, DD, PF etc.). Konfigurierbar über das Regler-Symbol. Zeigt die beste Gesamtperformance."));
        scoreCol.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getScore()));
        scoreCol.setPrefWidth(100);
        scoreCol.setStyle("-fx-alignment: CENTER;");
        scoreCol.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.format(Locale.US, "%.1f", item));
                    setStyle("-fx-alignment: CENTER;");
                }
            }
        });

        TableColumn<CombinedPass, String> robScoreCol = new TableColumn<>();
        robScoreCol.setGraphic(DocHelper.createHeaderWithTooltip("Rob. Scorecard", 
            "Robustness Scorecard (0-100):\nErgebnis des Monte-Carlo-Stresstests und systematischen Parameter-Shifting. Simuliert Rauschen (Slippage, Spread, Execution) und bewertet die Geradlinigkeit (R²-Stabilität) der Equity-Kurve."));
        robScoreCol.setCellValueFactory(c -> {
            String fromDateStr = engine.getFromDate() != null ? engine.getFromDate().toString() : "Unbekannt";
            String toDateStr = engine.getToDate() != null ? engine.getToDate().toString() : "Unbekannt";
            double score = c.getValue().getCachedOverallScore(fromDateStr, toDateStr);
            return new javafx.beans.property.SimpleStringProperty(String.format(Locale.US, "%.0f", score));
        });
        robScoreCol.setStyle("-fx-alignment: CENTER;");
        robScoreCol.setCellFactory(col -> new TableCell<CombinedPass, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(item);
                    try {
                        double score = Double.parseDouble(item);
                        String color;
                        if (score >= 70) {
                            color = "#00e676"; // Green
                        } else if (score >= 55) {
                            color = "#ffd740"; // Yellow
                        } else {
                            color = "#ff5252"; // Red
                        }
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: " + color + "; -fx-font-weight: bold;");
                    } catch (Exception e) {
                        setStyle("-fx-alignment: CENTER;");
                    }
                }
            }
        });
        robScoreCol.setPrefWidth(115);

        TableColumn<CombinedPass, String> kiScoreCol = new TableColumn<>("KI-Stabilität");
        kiScoreCol.setCellValueFactory(c -> {
            int score = engine.getKiScoreForPass(c.getValue().getPassNumber());
            return new javafx.beans.property.SimpleStringProperty(score >= 0 ? String.valueOf(score) : "—");
        });
        kiScoreCol.setStyle("-fx-alignment: CENTER;");
        kiScoreCol.setPrefWidth(90);

        TableColumn<CombinedPass, String> weightedScoreCol = new TableColumn<>("Gesamtwert");
        weightedScoreCol.setCellValueFactory(c -> {
            CombinedPass cp = c.getValue();
            double perfScore = cp.getScore();
            int kiScore = engine.getKiScoreForPass(cp.getPassNumber());
            if (kiScore < 0) {
                return new javafx.beans.property.SimpleStringProperty(String.format(Locale.US, "%.1f", perfScore));
            }
            double wScore = engine.getPerformanceWeight() * perfScore + engine.getStabilityWeight() * kiScore;
            return new javafx.beans.property.SimpleStringProperty(String.format(Locale.US, "%.1f", wScore));
        });
        weightedScoreCol.setStyle("-fx-alignment: CENTER; -fx-font-weight: bold; -fx-text-fill: #00e5ff;");
        weightedScoreCol.setPrefWidth(95);

        TableColumn<CombinedPass, Double> btProf = new TableColumn<>("BT Profit");
        btProf.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getBtProfit()));
        btProf.setPrefWidth(95);
        btProf.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || Double.isNaN(item)) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.format(Locale.US, "%.2f", item));
                    if (item >= 0) {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #00e676; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #ff5252; -fx-font-weight: bold;");
                    }
                }
            }
        });

        TableColumn<CombinedPass, Integer> btTr = new TableColumn<>("BT Trades");
        btTr.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getBtTrades()));
        btTr.setPrefWidth(80);
        btTr.setCellFactory(col -> new TableCell<CombinedPass, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.valueOf(item));
                    setStyle("-fx-alignment: CENTER;");
                }
            }
        });

        TableColumn<CombinedPass, Double> btDd = new TableColumn<>("BT DD%");
        btDd.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getBtDd()));
        btDd.setPrefWidth(85);
        btDd.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || Double.isNaN(item)) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.format(Locale.US, "%.2f %%", item));
                    if (item > 25) {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #ff5252;");
                    } else if (item > 15) {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #ffd740;");
                    } else {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #00e676;");
                    }
                }
            }
        });

        TableColumn<CombinedPass, Double> fwProf = new TableColumn<>("FW Profit");
        fwProf.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getFwProfit()));
        fwProf.setPrefWidth(95);
        fwProf.setCellFactory(col -> new TableCell<CombinedPass, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || Double.isNaN(item)) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.format(Locale.US, "%.2f", item));
                    if (item >= 0) {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #00e676; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #ff5252; -fx-font-weight: bold;");
                    }
                }
            }
        });

        TableColumn<CombinedPass, Integer> fwTr = new TableColumn<>("FW Trades");
        fwTr.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().getFwTrades()));
        fwTr.setPrefWidth(80);
        fwTr.setCellFactory(col -> new TableCell<CombinedPass, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("-");
                    setStyle("-fx-alignment: CENTER;");
                } else {
                    setText(String.valueOf(item));
                    setStyle("-fx-alignment: CENTER;");
                }
            }
        });

        table.getColumns().addAll(passCol, scoreCol, robScoreCol, kiScoreCol, weightedScoreCol, btProf, btTr, btDd, fwProf, fwTr);

        List<CombinedPass> results = engine.getFinalSelectedPasses();
        table.getItems().setAll(results);
        layout.getChildren().add(table);

        // Export Directory Configuration Row
        GridPane exportGrid = new GridPane();
        exportGrid.setHgap(10);
        exportGrid.setVgap(5);
        exportGrid.getStyleClass().add("sci-fi-panel");
        exportGrid.setPadding(new Insets(10));

        exportGrid.add(new Label("Export-Verzeichnis:"), 0, 0);
        TextField exportDirField = new TextField(AppConfig.getInstance().getExportDirectory().toString());
        exportDirField.setPrefWidth(550);

        Button browseBtn = new Button("Durchsuchen...");
        browseBtn.getStyleClass().add("button");
        browseBtn.setOnAction(evt -> {
            javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
            chooser.setTitle("Export-Verzeichnis wählen");
            File dir = new File(exportDirField.getText());
            if (dir.exists()) {
                chooser.setInitialDirectory(dir);
            }
            File selected = chooser.showDialog(stage);
            if (selected != null) {
                exportDirField.setText(selected.getAbsolutePath());
                AppConfig.getInstance().setExportDirectory(selected.getAbsolutePath());
                AppConfig.getInstance().save();
            }
        });

        // Listen for manual path edits
        exportDirField.textProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && !newV.trim().isEmpty()) {
                AppConfig.getInstance().setExportDirectory(newV.trim());
                AppConfig.getInstance().save();
            }
        });

        HBox pathBox = new HBox(10, exportDirField, browseBtn);
        HBox.setHgrow(exportDirField, Priority.ALWAYS);
        exportGrid.add(pathBox, 1, 0);
        layout.getChildren().add(exportGrid);

        HBox actionsRow = new HBox(15);
        actionsRow.setAlignment(Pos.CENTER_LEFT);
        
        Button exportBtn = new Button("💾 Portfolio exportieren");
        exportBtn.getStyleClass().add("button-start");
        exportBtn.setOnAction(e -> {
            try {
                String expDir = exportDirField.getText().trim();
                if (expDir.isEmpty()) {
                    new Alert(Alert.AlertType.WARNING, "Bitte gib ein gültiges Export-Verzeichnis an.").show();
                    return;
                }
                engine.exportPortfolio(expDir);
                new Alert(Alert.AlertType.INFORMATION, "Portfolio erfolgreich exportiert!\nPreset-Dateien (.set) und PDF-Reports wurden erstellt.").show();
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Fehler beim Exportieren des Portfolios:\n" + ex.getMessage()).show();
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("Schließen");
        closeBtn.getStyleClass().add("button");
        closeBtn.setOnAction(e -> stage.close());

        Button mainInfoBtn = DocHelper.createThickCircularInfoButton("Erklärung aller Indizes und Kennzahlen", () -> {
            DocHelper.showAllIndicesDocDialog(closeBtn.getScene() != null ? closeBtn.getScene().getWindow() : null);
        });

        actionsRow.getChildren().addAll(exportBtn, mainInfoBtn, spacer, closeBtn);
        layout.getChildren().add(actionsRow);

        stage.setScene(new Scene(layout));
        applyTheme(stage, owner);
        stage.showAndWait();
    }

    public static void showCvExplanationDialog(Window owner, boolean isForward) {
        Stage stage = new Stage();
        stage.setTitle(isForward ? "FW CV (worst) Erklärung" : "BT CV (worst) Erklärung");
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }

        VBox box = new VBox(15);
        box.setPadding(new Insets(20));
        box.setStyle("-fx-background-color: #11141d; -fx-border-color: #ffd740; -fx-border-width: 1px; -fx-border-radius: 5px;");

        Label titleLabel = new Label(isForward ? "FW CV (worst) - Forward Variationskoeffizient" : "BT CV (worst) - Backtest Variationskoeffizient");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        titleLabel.setTextFill(Color.web("#00e5ff"));

        WebView webView = new WebView();
        webView.setPrefSize(750, 480);
        
        String htmlBodyContent = isForward ? getFwCvExplanationHtml() : getBtCvExplanationHtml();
        String fullHtml = "<html><head><style>"
                + "body { background-color:#161821; color:#c8cddc; font-family:\"Segoe UI\", sans-serif; font-size:15px; line-height:1.6; margin:15px; }"
                + "h3 { color:#ffd740; font-size:18px; margin-top:15px; border-bottom: 1px solid #3e4555; padding-bottom: 5px; font-weight: bold; }"
                + "h4 { color:#00e5ff; font-size:15px; margin-top:12px; font-weight: bold; }"
                + "code { background-color:#1f2937; padding:4px 8px; border-radius:4px; color:#38bdf8; font-family:Consolas, monospace; font-size:13px; display:block; margin:8px 0; }"
                + "ul, ol { margin-left: 20px; padding-left: 0; }"
                + "li { margin-bottom: 6px; }"
                + "</style></head><body>"
                + htmlBodyContent
                + "</body></html>";
        webView.getEngine().loadContent(fullHtml);
        webView.setStyle("-fx-background-color: #161821;");

        Button closeBtn = new Button("Schließen");
        closeBtn.getStyleClass().addAll("button");
        closeBtn.setOnAction(e -> stage.close());

        HBox btnBox = new HBox(closeBtn);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        box.getChildren().addAll(titleLabel, webView, btnBox);
        VBox.setVgrow(webView, Priority.ALWAYS);

        Scene scene = new Scene(box, 800, 600);
        try {
            scene.getStylesheets().add(WorkflowConfigDialogs.class.getResource("/css/antigravity.css").toExternalForm());
        } catch (Exception e) {
            // Ignore
        }
        stage.setScene(scene);
        stage.showAndWait();
    }

    private static String getBtCvExplanationHtml() {
        return "<h3>BT CV (worst) - Backtest Variationskoeffizient</h3>"
             + "<p>Der <b>Variationskoeffizient (CV - Coefficient of Variation)</b> im Backtest-Zeitraum (In-Sample) misst die relative Streuung der Profite, wenn einzelne Optimierungsparameter variiert werden.</p>"
             + "<h4>Berechnung:</h4>"
             + "<p>Für jeden optimierten Parameter wird ein Sweep um die engere Umgebung des Optimalwerts durchgeführt. Daraus wird berechnet:</p>"
             + "<code>"
             + "CV = (Standardabweichung des Profits / Absoluter Basis-Profit) * 100%"
             + "</code>"
             + "<p>Der <b>BT CV (worst)</b> ist der <b>schlechteste (maximale) CV-Wert</b> über alle getesteten Parameter. Eine Strategie ist nur so robust wie ihr empfindlichster Parameter.</p>"
             + "<h4>Bedeutung der Werte:</h4>"
             + "<ul>"
             + "  <li><span style='color:#00e676; font-weight:bold;'>&lt; 30% (Robust):</span> Sehr stabil. Parameteränderungen in der nahen Umgebung haben kaum Einfluss auf das Endergebnis.</li>"
             + "  <li><span style='color:#ffd740; font-weight:bold;'>30% - 60% (Akzeptabel):</span> Mäßige Empfindlichkeit. Vertretbares Risiko für Überoptimierung.</li>"
             + "  <li><span style='color:#ff3b30; font-weight:bold;'>&gt; 60% (Fragil):</span> Sehr empfindlich. Kleine Parameteränderungen führen zu massiven Unterschieden im Gewinn oder Verlust.</li>"
             + "</ul>"
             + "<h4>Warum sind die Werte manchmal so hoch (z.B. 200%)?</h4>"
             + "<ol>"
             + "  <li><b>Geringer Basis-Profit:</b> Da der Basis-Profit im Nenner steht, explodiert der CV-Wert bei profitarmen Strategien. Wenn eine Strategie z.B. nur 10 € Gewinn macht, führt eine kleine Schwankung um 20 € bereits zu einem CV von 200%.</li>"
             + "  <li><b>Harte Filterung:</b> Wir testen die Parameter isoliert durch erneutes Backtesting. Fällt der Profit bei einer kleinen Änderung eines Parameters stark ab, deutet das auf <i>Curve-Fitting</i> (Überoptimierung) hin. Ein hoher CV warnt dich vor unzuverlässigen Strategien.</li>"
             + "  <li><b>Capping:</b> Um extreme Werte übersichtlich darzustellen, deckeln wir den angezeigten CV-Wert bei maximal 200%.</li>"
             + "</ol>";
    }

    private static String getFwCvExplanationHtml() {
        return "<h3>FW CV (worst) - Forward Variationskoeffizient</h3>"
             + "<p>Der <b>Variationskoeffizient (CV - Coefficient of Variation)</b> im Forward-Zeitraum (Out-of-Sample) misst die relative Streuung der Profite im Forward-Test, wenn die Parameter variiert werden.</p>"
             + "<h4>Berechnung:</h4>"
             + "<p>Es wird derselbe Parameter-Sweep wie im Backtest durchgeführt, jedoch ausschließlich auf den Out-of-Sample Forward-Daten:</p>"
             + "<code>"
             + "CV = (Standardabweichung des Profits / Absoluter Forward-Profit) * 100%"
             + "</code>"
             + "<p>Der <b>FW CV (worst)</b> zeigt den maximalen CV-Wert aller Parameter im Forward-Test-Zeitraum.</p>"
             + "<h4>Bedeutung der Werte:</h4>"
             + "<ul>"
             + "  <li><span style='color:#00e676; font-weight:bold;'>&lt; 30% (Robust):</span> Exzellente Stabilität auch auf unbekannten Zukundfdaten (Forward).</li>"
             + "  <li><span style='color:#ffd740; font-weight:bold;'>30% - 60% (Akzeptabel):</span> Vertretbare Abweichung im Forward-Zeitraum.</li>"
             + "  <li><span style='color:#ff3b30; font-weight:bold;'>&gt; 60% (Fragil):</span> Extrem unzuverlässiges Verhalten in der Forward-Phase bei minimalen Parameterverschiebungen.</li>"
             + "</ul>"
             + "<h4>Warum sind die Werte manchmal so hoch (z.B. 200%)?</h4>"
             + "<ol>"
             + "  <li><b>Geringer Forward-Profit:</b> Im Forward-Zeitraum sind die Gewinne oft noch kleiner oder nahe null. Dadurch wird der Nenner sehr klein, was zu extrem hohen Prozentwerten führt.</li>"
             + "  <li><b>Verlustphasen im Forward:</b> Wenn der Forward-Test schlechter läuft (was oft vorkommt, da Out-of-Sample-Daten), steigt die Standardabweichung im Verhältnis zum Profit drastisch an.</li>"
             + "  <li><b>Capping:</b> Um extreme Werte übersichtlich darzustellen, deckeln wir den angezeigten CV-Wert bei maximal 200%.</li>"
             + "</ol>";
    }
}
