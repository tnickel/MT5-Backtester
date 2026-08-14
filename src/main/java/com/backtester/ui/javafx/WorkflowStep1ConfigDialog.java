package com.backtester.ui.javafx;

import com.backtester.config.EaParameter;
import com.backtester.config.AppConfig;
import com.backtester.config.Preset;
import com.backtester.config.PresetManager;
import javafx.scene.web.WebView;
import com.backtester.config.EaParameterManager;
import com.backtester.engine.BacktestConfig;
import com.backtester.engine.OptimizationConfig;
import com.backtester.engine.WorkflowEngine;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.SensitivityResult;
import com.backtester.workflow.WorkflowTask;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Step 1: strategy / EA configuration dialog.
 */
public final class WorkflowStep1ConfigDialog {

    private static final Logger log = LoggerFactory.getLogger(WorkflowStep1ConfigDialog.class);
    private static final EaParameterManager eaParamManager = new EaParameterManager();

    private WorkflowStep1ConfigDialog() {}

    // ─── Step 1: Strategy Selector & Ranges ─────────────────────────────────────

    public static void showStep1Dialog(WorkflowEngine engine, Window owner) {
        showStep1Dialog(engine, owner, null);
    }

    public static void showStep1Dialog(WorkflowEngine engine, Window owner, Runnable onSave) {
        log.info("=== [SECTION-HEADER-LOG] Opening showStep1Dialog for expert: {} ===", engine.getExpert());
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
        ComboBox<Preset> presetCombo = new ComboBox<>();
        presetCombo.setPrefWidth(220);
        presetCombo.setPromptText("Preset wählen...");
        presetCombo.getItems().setAll(PresetManager.getInstance().getPresets());

        Button addPresetBtn = new Button("➕ Neu");
        addPresetBtn.getStyleClass().add("button");

        Button savePresetBtn = new Button("💾 Speichern");
        savePresetBtn.getStyleClass().add("button");

        Button editPresetBtn = new Button("✏ Ändern");
        editPresetBtn.getStyleClass().add("button");

        Button deletePresetBtn = new Button("🗑 Löschen");
        deletePresetBtn.getStyleClass().add("button");

        HBox presetBox = new HBox(8, presetCombo, addPresetBtn, savePresetBtn, editPresetBtn, deletePresetBtn);
        presetBox.setAlignment(Pos.CENTER_LEFT);
        grid.add(presetBox, 1, 2, 3, 1);

        grid.add(new Label("Datum von:"), 0, 3);
        DatePicker fromDatePicker = new DatePicker(engine.getFromDate());
        fromDatePicker.setConverter(WorkflowConfigDialogSupport.createDateConverter());
        grid.add(fromDatePicker, 1, 3);

        grid.add(new Label("bis:"), 2, 3);
        DatePicker toDatePicker = new DatePicker(engine.getToDate());
        toDatePicker.setConverter(WorkflowConfigDialogSupport.createDateConverter());
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

        paramTable.getItems().addListener((javafx.collections.ListChangeListener<EaParameter>) c -> {
            int count = paramTable.getItems().size();
            paramTitle.setText("EA Parameter & Optimierungs-Suchraum (" + count + " Parameter)");
        });

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
        nameCol.setCellValueFactory(cellData -> {
            EaParameter param = cellData.getValue();
            String display = param.getDisplayName();
            if (display == null || display.trim().isEmpty()) {
                display = param.getName();
            }
            return new javafx.beans.property.SimpleStringProperty(display);
        });
        nameCol.setCellFactory(column -> new TableCell<EaParameter, String>() {
            private final Tooltip tooltip = new Tooltip();
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item);
                    EaParameter param = getTableRow() != null ? getTableRow().getItem() : null;
                    if (param != null) {
                        tooltip.setText("Variable: " + param.getName());
                        setTooltip(tooltip);
                    } else {
                        setTooltip(null);
                    }
                }
            }
        });
        nameCol.setPrefWidth(180);

        TableColumn<EaParameter, String> valCol = new TableColumn<>("Wert");
        valCol.setCellValueFactory(new PropertyValueFactory<>("value"));
        valCol.setCellFactory(EnumAwareParamCell.forTableColumn());
        valCol.setOnEditCommit(e -> { e.getRowValue().setValue(e.getNewValue()); paramTable.refresh(); });
        valCol.setPrefWidth(100);

        TableColumn<EaParameter, String> startCol = new TableColumn<>("Start");
        startCol.setCellValueFactory(new PropertyValueFactory<>("optimizeStart"));
        startCol.setCellFactory(EnumAwareParamCell.forTableColumn());
        startCol.setOnEditCommit(e -> { e.getRowValue().setOptimizeStart(e.getNewValue()); paramTable.refresh(); });

        TableColumn<EaParameter, String> stepCol = new TableColumn<>("Schritt");
        stepCol.setCellValueFactory(new PropertyValueFactory<>("optimizeStep"));
        stepCol.setCellFactory(EnumAwareParamCell.forTableColumn());
        stepCol.setOnEditCommit(e -> { e.getRowValue().setOptimizeStep(e.getNewValue()); paramTable.refresh(); });

        TableColumn<EaParameter, String> stopCol = new TableColumn<>("Stopp");
        stopCol.setCellValueFactory(new PropertyValueFactory<>("optimizeEnd"));
        stopCol.setCellFactory(EnumAwareParamCell.forTableColumn());
        stopCol.setOnEditCommit(e -> { e.getRowValue().setOptimizeEnd(e.getNewValue()); paramTable.refresh(); });

        paramTable.getColumns().addAll(optCol, nameCol, valCol, startCol, stepCol, stopCol);
        EaParameterTableHelper.configureTable(paramTable, optCol, nameCol, valCol, startCol, stepCol, stopCol);

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

                            List<EaParameter> diskParams = eaParamManager.getEffectiveParameters(expert);
                            List<EaParameter> mergedEngineParams = eaParamManager.mergeLoadedWithExisting(engine.getEaParameters(), diskParams);

                            List<EaParameter> tableCopy = new ArrayList<>();
                            for (EaParameter p : mergedEngineParams) {
                                tableCopy.add(p.copy());
                            }
                            tableCopy = eaParamManager.ensureSectionHeaders(tableCopy);
                            log.info("=== [SECTION-HEADER-LOG] Setting {} params into paramTable (strategy config loaded) ===", tableCopy.size());
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
                    if (params != null && !params.isEmpty()) {
                        List<EaParameter> diskParams = eaParamManager.getEffectiveParameters(expert);
                        params = eaParamManager.mergeLoadedWithExisting(params, diskParams);
                        eaParamManager.applyTranslations(expert, params);
                    }
                } catch (Exception ignored) {}
            }

            if (params == null || params.isEmpty()) {
                params = eaParamManager.getEffectiveParameters(expert);
            }

            if (params != null) {
                List<EaParameter> tableCopy = new ArrayList<>();
                for (EaParameter p : params) {
                    tableCopy.add(p.copy());
                }
                tableCopy = eaParamManager.ensureSectionHeaders(tableCopy);
                log.info("=== [SECTION-HEADER-LOG] Setting {} params into paramTable (headers count={}) ===",
                        tableCopy.size(), tableCopy.stream().filter(EaParameter::isSectionHeader).count());
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

        presetCombo.setOnShowing(evt -> {
            Preset currentSel = presetCombo.getValue();
            presetCombo.getItems().setAll(PresetManager.getInstance().getPresets());
            if (currentSel != null) {
                for (Preset p : presetCombo.getItems()) {
                    if (p.getName().equals(currentSel.getName())) {
                        presetCombo.setValue(p);
                        break;
                    }
                }
            }
        });

        presetCombo.setOnAction(evt -> {
            Preset sel = presetCombo.getValue();
            if (sel != null) {
                if (sel.getEaName() != null && !sel.getEaName().isEmpty()) {
                    expertField.setText(sel.getEaName());
                }
                if (sel.getSymbols() != null && !sel.getSymbols().isEmpty()) {
                    symbolField.setText(sel.getSymbols());
                }
                if (sel.getPeriod() != null && !sel.getPeriod().isEmpty()) {
                    periodCombo.setValue(sel.getPeriod());
                }

                if (sel.getEaParameters() != null && !sel.getEaParameters().isEmpty()) {
                    // Update cache values so updateParamsTable knows we don't need a DB refresh immediately
                    lastCheckedExpert[0] = expertField.getText().trim();
                    lastCheckedSymbol[0] = symbolField.getText().trim();
                    lastCheckedPeriod[0] = periodCombo.getValue() != null ? periodCombo.getValue().trim() : "";
                    
                    // Load copy of parameters from Preset
                    List<EaParameter> tableCopy = new ArrayList<>();
                    for (EaParameter p : sel.getEaParameters()) {
                        EaParameter copy = new EaParameter();
                        copy.setName(p.getName());
                        copy.setDisplayName(p.getDisplayName());
                        copy.setValue(p.getValue());
                        copy.setDefaultValue(p.getDefaultValue() != null ? p.getDefaultValue() : p.getValue());
                        copy.setSection(p.getSection());
                        copy.setOptimizeStart(p.getOptimizeStart());
                        copy.setOptimizeStep(p.getOptimizeStep());
                        copy.setOptimizeEnd(p.getOptimizeEnd());
                        copy.setOptimizeEnabled(p.isOptimizeEnabled());
                        copy.setStringType(p.isStringType());
                        copy.setSectionHeader(p.isSectionHeader());
                        tableCopy.add(copy);
                    }
                    paramTable.getItems().setAll(tableCopy);
                } else {
                    updateParamsTable.run();
                }
            }
        });

        savePresetBtn.setOnAction(evt -> {
            Preset sel = presetCombo.getValue();
            if (sel == null) {
                Alert alert = new Alert(Alert.AlertType.WARNING, "Bitte wählen Sie zuerst ein Preset aus, auf dem gespeichert werden soll.");
                alert.initOwner(stage);
                alert.show();
                return;
            }

            // Snapshot parameters
            List<EaParameter> currentParams = new ArrayList<>();
            for (EaParameter p : paramTable.getItems()) {
                EaParameter copy = new EaParameter();
                copy.setName(p.getName());
                copy.setDisplayName(p.getDisplayName());
                copy.setValue(p.getValue());
                copy.setDefaultValue(p.getDefaultValue() != null ? p.getDefaultValue() : p.getValue());
                copy.setSection(p.getSection());
                copy.setOptimizeStart(p.getOptimizeStart());
                copy.setOptimizeStep(p.getOptimizeStep());
                copy.setOptimizeEnd(p.getOptimizeEnd());
                copy.setOptimizeEnabled(p.isOptimizeEnabled());
                copy.setStringType(p.isStringType());
                copy.setSectionHeader(p.isSectionHeader());
                currentParams.add(copy);
            }

            sel.setEaName(expertField.getText().trim());
            sel.setSymbols(symbolField.getText().trim());
            sel.setPeriod(periodCombo.getValue() != null ? periodCombo.getValue().trim() : "");
            sel.setEaParameters(currentParams);

            PresetManager.getInstance().savePresets();

            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Preset \"" + sel.getName() + "\" erfolgreich mit den aktuellen Parametern gespeichert.");
            alert.initOwner(stage);
            alert.show();
        });

        addPresetBtn.setOnAction(evt -> {
            TextInputDialog inputDialog = new TextInputDialog("Set " + (PresetManager.getInstance().getPresets().size() + 1));
            inputDialog.setTitle("Neues Preset erstellen");
            inputDialog.setHeaderText("Preset-Namen eingeben");
            inputDialog.setContentText("Name:");
            
            inputDialog.getDialogPane().setStyle("-fx-background-color: #0b0d13;");
            inputDialog.getDialogPane().lookup(".content.label").setStyle("-fx-text-fill: #e6e9f0;");
            inputDialog.getDialogPane().lookup(".header-panel").setStyle("-fx-background-color: #0b0d13;");
            if (inputDialog.getDialogPane().lookup(".header-panel").lookup(".label") != null) {
                inputDialog.getDialogPane().lookup(".header-panel").lookup(".label").setStyle("-fx-text-fill: #00e5ff;");
            }
            if (stage.getScene() != null && !stage.getScene().getStylesheets().isEmpty()) {
                inputDialog.getDialogPane().getStylesheets().addAll(stage.getScene().getStylesheets());
            }

            java.util.Optional<String> result = inputDialog.showAndWait();
            if (result.isPresent() && !result.get().trim().isEmpty()) {
                String name = result.get().trim();
                String ea = expertField.getText().trim();
                String syms = symbolField.getText().trim();
                String per = periodCombo.getValue() != null ? periodCombo.getValue().trim() : "";
                
                // Snapshot parameters
                List<EaParameter> currentParams = new ArrayList<>();
                for (EaParameter p : paramTable.getItems()) {
                    EaParameter copy = new EaParameter();
                    copy.setName(p.getName());
                    copy.setDisplayName(p.getDisplayName());
                    copy.setValue(p.getValue());
                    copy.setDefaultValue(p.getDefaultValue() != null ? p.getDefaultValue() : p.getValue());
                    copy.setSection(p.getSection());
                    copy.setOptimizeStart(p.getOptimizeStart());
                    copy.setOptimizeStep(p.getOptimizeStep());
                    copy.setOptimizeEnd(p.getOptimizeEnd());
                    copy.setOptimizeEnabled(p.isOptimizeEnabled());
                    copy.setStringType(p.isStringType());
                    copy.setSectionHeader(p.isSectionHeader());
                    currentParams.add(copy);
                }

                Preset newPreset = new Preset(name, ea, syms, per, currentParams);
                PresetManager.getInstance().addPreset(newPreset);
                presetCombo.getItems().setAll(PresetManager.getInstance().getPresets());
                presetCombo.setValue(newPreset);
            }
        });

        editPresetBtn.setOnAction(evt -> {
            Preset sel = presetCombo.getValue();
            if (sel == null) {
                Alert alert = new Alert(Alert.AlertType.WARNING, "Bitte wählen Sie zuerst ein Preset aus, das geändert werden soll.");
                alert.initOwner(stage);
                alert.show();
                return;
            }

            TextInputDialog inputDialog = new TextInputDialog(sel.getName());
            inputDialog.setTitle("Preset bearbeiten");
            inputDialog.setHeaderText("Ggf. Namen anpassen. Die aktuellen Werte des Formulars\n(EA, Symbole, Periode) werden im Preset gespeichert.");
            inputDialog.setContentText("Name:");

            inputDialog.getDialogPane().setStyle("-fx-background-color: #0b0d13;");
            inputDialog.getDialogPane().lookup(".content.label").setStyle("-fx-text-fill: #e6e9f0;");
            inputDialog.getDialogPane().lookup(".header-panel").setStyle("-fx-background-color: #0b0d13;");
            if (inputDialog.getDialogPane().lookup(".header-panel").lookup(".label") != null) {
                inputDialog.getDialogPane().lookup(".header-panel").lookup(".label").setStyle("-fx-text-fill: #00e5ff;");
            }
            if (stage.getScene() != null && !stage.getScene().getStylesheets().isEmpty()) {
                inputDialog.getDialogPane().getStylesheets().addAll(stage.getScene().getStylesheets());
            }

            java.util.Optional<String> result = inputDialog.showAndWait();
            if (result.isPresent() && !result.get().trim().isEmpty()) {
                sel.setName(result.get().trim());
                sel.setEaName(expertField.getText().trim());
                sel.setSymbols(symbolField.getText().trim());
                sel.setPeriod(periodCombo.getValue() != null ? periodCombo.getValue().trim() : "");
                
                // Snapshot parameters
                List<EaParameter> currentParams = new ArrayList<>();
                for (EaParameter p : paramTable.getItems()) {
                    EaParameter copy = new EaParameter();
                    copy.setName(p.getName());
                    copy.setDisplayName(p.getDisplayName());
                    copy.setValue(p.getValue());
                    copy.setDefaultValue(p.getDefaultValue() != null ? p.getDefaultValue() : p.getValue());
                    copy.setSection(p.getSection());
                    copy.setOptimizeStart(p.getOptimizeStart());
                    copy.setOptimizeStep(p.getOptimizeStep());
                    copy.setOptimizeEnd(p.getOptimizeEnd());
                    copy.setOptimizeEnabled(p.isOptimizeEnabled());
                    copy.setStringType(p.isStringType());
                    copy.setSectionHeader(p.isSectionHeader());
                    currentParams.add(copy);
                }
                sel.setEaParameters(currentParams);
                
                PresetManager.getInstance().savePresets();
                
                presetCombo.getItems().setAll(PresetManager.getInstance().getPresets());
                presetCombo.setValue(sel);
            }
        });

        deletePresetBtn.setOnAction(evt -> {
            Preset sel = presetCombo.getValue();
            if (sel == null) {
                Alert alert = new Alert(Alert.AlertType.WARNING, "Bitte wählen Sie zuerst ein Preset aus, das gelöscht werden soll.");
                alert.initOwner(stage);
                alert.show();
                return;
            }

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Möchten Sie das Preset \"" + sel.getName() + "\" wirklich löschen?", ButtonType.YES, ButtonType.NO);
            confirm.setTitle("Preset löschen");
            confirm.setHeaderText("Bestätigung erforderlich");
            confirm.initOwner(stage);

            confirm.getDialogPane().setStyle("-fx-background-color: #0b0d13;");
            confirm.getDialogPane().lookup(".content.label").setStyle("-fx-text-fill: #e6e9f0;");
            confirm.getDialogPane().lookup(".header-panel").setStyle("-fx-background-color: #0b0d13;");
            if (confirm.getDialogPane().lookup(".header-panel").lookup(".label") != null) {
                confirm.getDialogPane().lookup(".header-panel").lookup(".label").setStyle("-fx-text-fill: #00e5ff;");
            }
            if (stage.getScene() != null && !stage.getScene().getStylesheets().isEmpty()) {
                confirm.getDialogPane().getStylesheets().addAll(stage.getScene().getStylesheets());
            }

            confirm.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    PresetManager.getInstance().removePreset(sel);
                    presetCombo.getItems().setAll(PresetManager.getInstance().getPresets());
                    presetCombo.setValue(null);
                }
            });
        });

        // Set action for browse button
        browseBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select Expert Advisor");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("MetaTrader EA", "*.ex5", "*.ex4"));
            
            com.backtester.config.AppConfig config = com.backtester.config.AppConfig.getInstance();
            String currentExpert = expertField.getText().trim();
            java.nio.file.Path expertsDir = null;
            if (config.isMt4(currentExpert)) {
                expertsDir = config.getExpertsDir("dummy.ex4");
            } else {
                expertsDir = config.getExpertsDir("dummy.ex5");
            }

            if (expertsDir != null && java.nio.file.Files.exists(expertsDir)) {
                chooser.setInitialDirectory(expertsDir.toFile());
            } else {
                java.nio.file.Path otherDir = config.isMt4(currentExpert) ? config.getExpertsDir("dummy.ex5") : config.getExpertsDir("dummy.ex4");
                if (otherDir != null && java.nio.file.Files.exists(otherDir)) {
                    chooser.setInitialDirectory(otherDir.toFile());
                }
            }

            File selected = chooser.showOpenDialog(stage);
            if (selected != null) {
                String pathStr = selected.getAbsolutePath().toLowerCase();
                boolean isEx4 = pathStr.endsWith(".ex4");
                java.nio.file.Path activeExpertsDir = isEx4 ? config.getExpertsDir("dummy.ex4") : config.getExpertsDir("dummy.ex5");

                if (activeExpertsDir != null && selected.toPath().startsWith(activeExpertsDir)) {
                    String relative = activeExpertsDir.relativize(selected.toPath()).toString();
                    if (!isEx4 && relative.toLowerCase().endsWith(".ex5")) {
                        relative = relative.substring(0, relative.length() - 4);
                    }
                    expertField.setText(relative);
                } else {
                    String path = selected.getAbsolutePath();
                    if (!isEx4 && path.toLowerCase().endsWith(".ex5")) {
                        path = path.substring(0, path.length() - 4);
                    }
                    expertField.setText(path);
                }
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
            String expert = engine.getExpert() != null ? engine.getExpert().trim() : "";
            List<EaParameter> diskParams = eaParamManager.getEffectiveParameters(expert);
            List<EaParameter> mergedEngineParams = eaParamManager.mergeLoadedWithExisting(engine.getEaParameters(), diskParams);
            EaParameterManager.normalizeTimeframeOptimizeBands(mergedEngineParams);

            List<EaParameter> currentParams = new ArrayList<>();
            for (EaParameter p : mergedEngineParams) {
                currentParams.add(p.copy());
            }
            currentParams = eaParamManager.ensureSectionHeaders(currentParams);
            log.info("=== [SECTION-HEADER-LOG] Initial dialog load: setting {} params into paramTable ===", currentParams.size());
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
                    String expertPath = expertField.getText().trim();
                    List<EaParameter> existing = new ArrayList<>(paramTable.getItems());
                    List<EaParameter> merged = eaParamManager.mergeLoadedWithExisting(pList, existing);
                    eaParamManager.applyTranslations(expertPath, merged);
                    paramTable.getItems().setAll(merged);
                }
            }
        });

        Button saveSetBtn = new Button("Save .set");
        saveSetBtn.setOnAction(e -> {
            FileChooser ch = new FileChooser();
            ch.setTitle("Save .set File");
            com.backtester.config.AppConfig config = com.backtester.config.AppConfig.getInstance();
            String expertPath = expertField.getText().trim();
            boolean isMt4 = config.isMt4(expertPath);
            ch.getExtensionFilters().add(new FileChooser.ExtensionFilter(isMt4 ? "MT4 Set Files" : "MT5 Set Files", "*.set"));
            File file = ch.showSaveDialog(stage);
            if (file != null) {
                eaParamManager.writeSetFile(file.toPath(), new ArrayList<>(paramTable.getItems()), expertPath);
            }
        });

        Button genConfigBtn = new Button("Gen Config");
        genConfigBtn.setOnAction(e -> {
            String expert = expertField.getText().trim();
            if (expert.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.WARNING, "Bitte wählen Sie zuerst einen Expert Advisor aus.");
                alert.initOwner(stage);
                alert.show();
                return;
            }

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, 
                "Möchten Sie die Standard-Konfiguration für \"" + expert + "\" wirklich neu generieren?\n" +
                "Dies wird alle zuvor gespeicherten Werte/Einstellungen für diesen EA löschen.",
                ButtonType.YES, ButtonType.NO);
            confirm.initOwner(stage);
            confirm.getDialogPane().setStyle("-fx-background-color: #0b0d13;");
            confirm.getDialogPane().lookup(".content.label").setStyle("-fx-text-fill: #e6e9f0;");
            if (confirm.getDialogPane().lookup(".header-panel") != null) {
                confirm.getDialogPane().lookup(".header-panel").setStyle("-fx-background-color: #0b0d13;");
                if (confirm.getDialogPane().lookup(".header-panel").lookup(".label") != null) {
                    confirm.getDialogPane().lookup(".header-panel").lookup(".label").setStyle("-fx-text-fill: #00e5ff;");
                }
            }
            if (stage.getScene() != null && !stage.getScene().getStylesheets().isEmpty()) {
                confirm.getDialogPane().getStylesheets().addAll(stage.getScene().getStylesheets());
            }

            confirm.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    genConfigBtn.setDisable(true);
                    genConfigBtn.setText("Generating...");

                    javafx.concurrent.Task<Boolean> task = new javafx.concurrent.Task<Boolean>() {
                        @Override
                        protected Boolean call() throws Exception {
                            // 1. Delete custom set file
                            eaParamManager.deleteCustomParameters(expert);
                            // 2. Delete DB configs
                            com.backtester.database.DatabaseManager db = com.backtester.database.DatabaseManager.getInstance();
                            db.deleteWorkflowStrategyConfig(expert);
                            db.deleteEaParameterSettings(expert);
                            // 3. Generate default config via MT5
                            return eaParamManager.generateDefaultConfig(expert);
                        }
                    };

                    task.setOnSucceeded(evt -> {
                        genConfigBtn.setDisable(false);
                        genConfigBtn.setText("Gen Config");
                        boolean success = task.getValue();
                        if (success) {
                            lastCheckedExpert[0] = ""; // Force reload
                            updateParamsTable.run();
                            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Standard-Parameter erfolgreich generiert.");
                            alert.initOwner(stage);
                            alert.show();
                        } else {
                            Alert alert = new Alert(Alert.AlertType.ERROR, "Fehler beim Generieren der Standard-Parameter. Bitte MT5 Logs prüfen.");
                            alert.initOwner(stage);
                            alert.show();
                        }
                    });

                    task.setOnFailed(evt -> {
                        genConfigBtn.setDisable(false);
                        genConfigBtn.setText("Gen Config");
                        Alert alert = new Alert(Alert.AlertType.ERROR, "Fehler: " + task.getException().getMessage());
                        alert.initOwner(stage);
                        alert.show();
                    });

                    new Thread(task).start();
                }
            });
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
                    if (params != null && !params.isEmpty()) {
                        eaParamManager.applyTranslations(robExpert, params);
                    }
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
                    copy.setDisplayName(p.getDisplayName());
                    copy.setValue(p.getValue());
                    copy.setDefaultValue(p.getDefaultValue() != null ? p.getDefaultValue() : p.getValue());
                    copy.setSection(p.getSection());
                    copy.setOptimizeStart(p.getOptimizeStart());
                    copy.setOptimizeStep(p.getOptimizeStep());
                    copy.setOptimizeEnd(p.getOptimizeEnd());
                    copy.setOptimizeEnabled(p.isOptimizeEnabled());
                    copy.setStringType(p.isStringType());
                    copy.setSectionHeader(p.isSectionHeader());
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

        paramActions.getChildren().addAll(genConfigBtn, takeParamsBtn, autoBtn, loadSetBtn, saveSetBtn);
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
        WorkflowConfigDialogSupport.applyTheme(stage, owner);
        stage.showAndWait();
    }

}
