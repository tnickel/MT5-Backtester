package com.backtester.ui.javafx;

import com.backtester.config.AppConfig;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SettingsView {

    private final VBox root;
    private final AppConfig config;

    // MT5 settings
    private TextField mt5PathField;
    private CheckBox portableCheckbox;
    private Label statusLabel;

    // Directory settings
    private TextField outputDirField;
    private TextField dataDirField;

    // Default parameters
    private Spinner<Integer> depositSpinner;
    private ComboBox<String> currencyCombo;
    private TextField leverageField;
    private Spinner<Integer> timezoneSpinner;
    private ComboBox<String> defaultModelCombo;

    public SettingsView() {
        this.config = AppConfig.getInstance();

        root = new VBox(20);
        root.setPadding(new Insets(20));

        Label title = new Label("Application Settings");
        title.getStyleClass().add("sci-fi-panel-title");
        title.setStyle("-fx-font-size: 18px; -fx-text-fill: #4e9af1;");

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("edge-to-edge");

        VBox content = new VBox(20);
        content.getChildren().addAll(
                createMt5Section(),
                createDirectorySection(),
                createDefaultsSection(),
                createButtonSection()
        );

        scrollPane.setContent(content);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        root.getChildren().addAll(title, scrollPane);
    }

    private VBox createSection(String titleStr) {
        VBox section = new VBox(15);
        section.getStyleClass().add("sci-fi-panel");
        Label title = new Label(titleStr);
        title.getStyleClass().add("sci-fi-panel-title");
        section.getChildren().add(title);
        return section;
    }

    private VBox createMt5Section() {
        VBox section = createSection("MetaTrader 5");

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(10);

        grid.add(new Label("Terminal Path:"), 0, 0);
        
        mt5PathField = new TextField(config.getMt5TerminalPath());
        mt5PathField.getStyleClass().add("text-input");
        mt5PathField.setPrefWidth(400);
        
        Button browseBtn = new Button("Browse...");
        browseBtn.getStyleClass().add("button");
        browseBtn.setOnAction(e -> browseMt5Path());

        HBox pathBox = new HBox(10, mt5PathField, browseBtn);
        HBox.setHgrow(mt5PathField, Priority.ALWAYS);
        grid.add(pathBox, 1, 0);

        portableCheckbox = new CheckBox("Use Portable Mode (/portable flag)");
        portableCheckbox.setSelected(config.isPortableMode());
        grid.add(portableCheckbox, 1, 1);

        statusLabel = new Label();
        statusLabel.setStyle("-fx-font-style: italic; -fx-font-size: 11px;");
        updateMt5Status();
        mt5PathField.textProperty().addListener((obs, oldV, newV) -> updateMt5Status());
        grid.add(statusLabel, 0, 2, 2, 1);

        section.getChildren().add(grid);
        return section;
    }

    private void updateMt5Status() {
        String path = mt5PathField.getText().trim();
        Path p = Paths.get(path);
        if (Files.exists(p)) {
            if (!p.getFileName().toString().toLowerCase().equals("terminal64.exe")) {
                statusLabel.setText("✗ Invalid executable: must be terminal64.exe");
                statusLabel.setStyle("-fx-text-fill: #f06464;");
            } else {
                statusLabel.setText("✓ Terminal found");
                statusLabel.setStyle("-fx-text-fill: #64c878;");
            }
        } else {
            statusLabel.setText("✗ Terminal not found at specified path");
            statusLabel.setStyle("-fx-text-fill: #f06464;");
        }
    }

    private VBox createDirectorySection() {
        VBox section = createSection("Directories");

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(10);

        grid.add(new Label("Reports Output:"), 0, 0);
        outputDirField = new TextField(config.getReportsDirectory().toString());
        outputDirField.getStyleClass().add("text-input");
        grid.add(createDirField(outputDirField), 1, 0);

        grid.add(new Label("Data Directory:"), 0, 1);
        dataDirField = new TextField(config.getDataDirectory().toString());
        dataDirField.getStyleClass().add("text-input");
        grid.add(createDirField(dataDirField), 1, 1);

        section.getChildren().add(grid);
        return section;
    }

    private VBox createDefaultsSection() {
        VBox section = createSection("Default Backtest Parameters");

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(10);

        grid.add(new Label("Default Deposit:"), 0, 0);
        depositSpinner = new Spinner<>(100, 10000000, config.getDefaultDeposit(), 1000);
        depositSpinner.setEditable(true);
        grid.add(depositSpinner, 1, 0);

        grid.add(new Label("Default Currency:"), 0, 1);
        currencyCombo = new ComboBox<>(FXCollections.observableArrayList("USD", "EUR", "GBP", "JPY", "CHF"));
        currencyCombo.setValue(config.getDefaultCurrency());
        currencyCombo.getStyleClass().add("combo-box");
        grid.add(currencyCombo, 1, 1);

        grid.add(new Label("Default Leverage:"), 0, 2);
        leverageField = new TextField(config.getDefaultLeverage());
        leverageField.getStyleClass().add("text-input");
        grid.add(leverageField, 1, 2);

        grid.add(new Label("Default Tick Model:"), 0, 3);
        defaultModelCombo = new ComboBox<>(FXCollections.observableArrayList(com.backtester.engine.BacktestConfig.MODEL_NAMES));
        defaultModelCombo.getSelectionModel().select(config.getDefaultModel());
        defaultModelCombo.getStyleClass().add("combo-box");
        grid.add(defaultModelCombo, 1, 3);

        grid.add(new Label("Broker Timezone (UTC+):"), 0, 4);
        timezoneSpinner = new Spinner<>(-12, 14, config.getBrokerTimezoneOffset(), 1);
        grid.add(timezoneSpinner, 1, 4);

        section.getChildren().add(grid);
        return section;
    }

    private HBox createButtonSection() {
        HBox box = new HBox(15);
        box.setAlignment(Pos.CENTER_LEFT);

        Button saveBtn = new Button("💾 Save Settings");
        saveBtn.getStyleClass().add("button");
        saveBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #287846, #143c23);");
        saveBtn.setOnAction(e -> saveSettings());

        Button resetBtn = new Button("Reset to Defaults");
        resetBtn.getStyleClass().add("button");
        resetBtn.setOnAction(e -> resetDefaults());

        box.getChildren().addAll(saveBtn, resetBtn);
        return box;
    }

    private void saveSettings() {
        String path = mt5PathField.getText().trim();
        Path p = Paths.get(path);
        if (!Files.exists(p) || !p.getFileName().toString().toLowerCase().equals("terminal64.exe")) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Invalid MetaTrader 5 Terminal Path. The file MUST exist and be named 'terminal64.exe'.");
            alert.showAndWait();
            return;
        }

        config.setMt5TerminalPath(path);
        config.set("mt5.portable.mode", String.valueOf(portableCheckbox.isSelected()));
        config.setReportsDirectory(outputDirField.getText().trim());
        config.setDataDirectory(dataDirField.getText().trim());
        config.set("backtest.deposit", String.valueOf(depositSpinner.getValue()));
        config.set("backtest.currency", currencyCombo.getValue());
        config.set("backtest.leverage", leverageField.getText().trim());
        config.set("backtest.model", String.valueOf(defaultModelCombo.getSelectionModel().getSelectedIndex()));
        config.set("broker.timezone.offset", String.valueOf(timezoneSpinner.getValue()));
        config.save();

        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Settings saved successfully!");
        alert.showAndWait();
    }

    private void resetDefaults() {
        mt5PathField.setText("C:\\Program Files\\MetaTrader 5\\terminal64.exe");
        portableCheckbox.setSelected(true);
        depositSpinner.getValueFactory().setValue(10000);
        currencyCombo.setValue("USD");
        leverageField.setText("1:100");
        defaultModelCombo.getSelectionModel().select(0);
        timezoneSpinner.getValueFactory().setValue(2);
    }

    private HBox createDirField(TextField field) {
        Button browseBtn = new Button("Browse...");
        browseBtn.getStyleClass().add("button");
        browseBtn.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            File dir = new File(field.getText());
            if (dir.exists()) chooser.setInitialDirectory(dir);
            File selected = chooser.showDialog(field.getScene().getWindow());
            if (selected != null) {
                field.setText(selected.getAbsolutePath());
            }
        });
        HBox box = new HBox(10, field, browseBtn);
        HBox.setHgrow(field, Priority.ALWAYS);
        return box;
    }

    private void browseMt5Path() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select MetaTrader 5 Terminal");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("MetaTrader 5 Terminal", "terminal64.exe"));
        
        File progFiles = new File("C:\\Program Files");
        if (progFiles.exists()) chooser.setInitialDirectory(progFiles);

        File selected = chooser.showOpenDialog(mt5PathField.getScene().getWindow());
        if (selected != null) {
            mt5PathField.setText(selected.getAbsolutePath());
        }
    }

    public VBox getView() {
        return root;
    }
}
