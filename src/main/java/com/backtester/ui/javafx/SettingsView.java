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

    // MetaTrader settings
    private TextField mt5PathField;
    private TextField mt4PathField;
    private CheckBox portableCheckbox;
    private ComboBox<String> launchModeCombo;
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
    private Spinner<Integer> timeoutSpinner;
    private Button deleteLogsBtn;

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
                createMtSection(),
                createDirectorySection(),
                createDefaultsSection(),
                createButtonSection()
        );

        scrollPane.setContent(content);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        root.getChildren().addAll(title, scrollPane);
        startLogSizeUpdater();
    }

    private VBox createSection(String titleStr) {
        VBox section = new VBox(15);
        section.getStyleClass().add("sci-fi-panel");
        Label title = new Label(titleStr);
        title.getStyleClass().add("sci-fi-panel-title");
        section.getChildren().add(title);
        return section;
    }

    private VBox createMtSection() {
        VBox section = createSection("MetaTrader Installations");

        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(10);

        grid.add(new Label("MT5 Terminal Path:"), 0, 0);
        
        mt5PathField = new TextField(config.getMt5TerminalPath());
        mt5PathField.getStyleClass().add("text-input");
        mt5PathField.setPrefWidth(550);
        
        Button mt5BrowseBtn = new Button("Browse...");
        mt5BrowseBtn.getStyleClass().add("button");
        mt5BrowseBtn.setOnAction(e -> browseTerminalPath(mt5PathField, "terminal64.exe"));

        HBox mt5PathBox = new HBox(10, mt5PathField, mt5BrowseBtn);
        HBox.setHgrow(mt5PathField, Priority.ALWAYS);
        grid.add(mt5PathBox, 1, 0);

        grid.add(new Label("MT4 Terminal Path:"), 0, 1);
        
        mt4PathField = new TextField(config.getMt4TerminalPath());
        mt4PathField.getStyleClass().add("text-input");
        mt4PathField.setPrefWidth(550);
        
        Button mt4BrowseBtn = new Button("Browse...");
        mt4BrowseBtn.getStyleClass().add("button");
        mt4BrowseBtn.setOnAction(e -> browseTerminalPath(mt4PathField, "terminal.exe"));

        HBox mt4PathBox = new HBox(10, mt4PathField, mt4BrowseBtn);
        HBox.setHgrow(mt4PathField, Priority.ALWAYS);
        grid.add(mt4PathBox, 1, 1);

        portableCheckbox = new CheckBox("Use Portable Mode (/portable flag)");
        portableCheckbox.setSelected(config.isPortableMode());
        grid.add(portableCheckbox, 1, 2);

        grid.add(new Label("Start-Modus (MT5):"), 0, 3);
        launchModeCombo = new ComboBox<>(FXCollections.observableArrayList(
            "Normal (Sichtbar)",
            "Virtueller Desktop 1",
            "Virtueller Desktop 2",
            "Virtueller Desktop 3",
            "Headless (Unsichtbar /hide)"
        ));
        String currentMode = config.get("mt5.launch.mode", "HEADLESS");
        if ("HEADLESS".equalsIgnoreCase(currentMode)) {
            launchModeCombo.setValue("Headless (Unsichtbar /hide)");
        } else if ("NORMAL".equalsIgnoreCase(currentMode)) {
            launchModeCombo.setValue("Normal (Sichtbar)");
        } else if ("VIRTUAL_DESKTOP_1".equalsIgnoreCase(currentMode)) {
            launchModeCombo.setValue("Virtueller Desktop 1");
        } else if ("VIRTUAL_DESKTOP_3".equalsIgnoreCase(currentMode)) {
            launchModeCombo.setValue("Virtueller Desktop 3");
        } else {
            launchModeCombo.setValue("Virtueller Desktop 2");
        }
        launchModeCombo.getStyleClass().add("combo-box");
        grid.add(launchModeCombo, 1, 3);

        statusLabel = new Label();
        statusLabel.setStyle("-fx-font-style: italic; -fx-font-size: 11px;");
        
        Runnable updateStatus = () -> {
            boolean mt5Ok = validateTerminalPath(mt5PathField.getText().trim(), "terminal64.exe");
            boolean mt4Ok = validateTerminalPath(mt4PathField.getText().trim(), "terminal.exe");
            
            if (mt5Ok && mt4Ok) {
                statusLabel.setText("✓ Both terminals found");
                statusLabel.setStyle("-fx-text-fill: #64c878;");
            } else {
                if (!mt5Ok && !mt4Ok) {
                    statusLabel.setText("✗ Both terminal paths are invalid or missing");
                    statusLabel.setStyle("-fx-text-fill: #f06464;");
                } else if (!mt5Ok) {
                    statusLabel.setText("✗ MT5 terminal path is invalid (must exist and end with terminal64.exe)");
                    statusLabel.setStyle("-fx-text-fill: #f06464;");
                } else {
                    statusLabel.setText("✗ MT4 terminal path is invalid (must exist and end with terminal.exe)");
                    statusLabel.setStyle("-fx-text-fill: #f06464;");
                }
            }
        };

        updateStatus.run();
        mt5PathField.textProperty().addListener((obs, oldV, newV) -> updateStatus.run());
        mt4PathField.textProperty().addListener((obs, oldV, newV) -> updateStatus.run());
        grid.add(statusLabel, 0, 4, 2, 1);

        section.getChildren().add(grid);
        return section;
    }

    private boolean validateTerminalPath(String path, String expectedExeName) {
        if (path.isEmpty()) return false;
        Path p = Paths.get(path);
        if (!Files.exists(p)) return false;
        String fileName = p.getFileName().toString().toLowerCase();
        return fileName.equals(expectedExeName);
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

        grid.add(new Label("Backtest Timeout (minutes):"), 0, 5);
        timeoutSpinner = new Spinner<>(1, 120, config.getBacktestTimeoutMinutes(), 1);
        grid.add(timeoutSpinner, 1, 5);

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

        deleteLogsBtn = new Button("Delete Log Files");
        deleteLogsBtn.getStyleClass().add("button");
        deleteLogsBtn.setStyle("-fx-background-color: linear-gradient(to bottom, #782828, #3c1414);");
        deleteLogsBtn.setOnAction(e -> deleteLogFiles());

        box.getChildren().addAll(saveBtn, resetBtn, deleteLogsBtn);
        return box;
    }

    private void saveSettings() {
        String mt5Path = mt5PathField.getText().trim();
        if (!mt5Path.isEmpty()) {
            Path p = Paths.get(mt5Path);
            String fileName = p.getFileName().toString().toLowerCase();
            if (!Files.exists(p) || !fileName.equals("terminal64.exe")) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Invalid MetaTrader 5 Terminal Path. The file MUST exist and be named 'terminal64.exe'.");
                alert.showAndWait();
                return;
            }
        }
        String mt4Path = mt4PathField.getText().trim();
        if (!mt4Path.isEmpty()) {
            Path p = Paths.get(mt4Path);
            String fileName = p.getFileName().toString().toLowerCase();
            if (!Files.exists(p) || !fileName.equals("terminal.exe")) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Invalid MetaTrader 4 Terminal Path. The file MUST exist and be named 'terminal.exe'.");
                alert.showAndWait();
                return;
            }
        }
        if (mt5Path.isEmpty() && mt4Path.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "You must configure at least one MetaTrader terminal path.");
            alert.showAndWait();
            return;
        }

        config.setMt5TerminalPath(mt5Path);
        config.setMt4TerminalPath(mt4Path);
        config.set("mt5.portable.mode", String.valueOf(portableCheckbox.isSelected()));
        String selectedMode = "VIRTUAL_DESKTOP_2";
        String val = launchModeCombo.getValue();
        if ("Headless (Unsichtbar /hide)".equals(val)) {
            selectedMode = "HEADLESS";
        } else if ("Normal (Sichtbar)".equals(val)) {
            selectedMode = "NORMAL";
        } else if ("Virtueller Desktop 1".equals(val)) {
            selectedMode = "VIRTUAL_DESKTOP_1";
        } else if ("Virtueller Desktop 3".equals(val)) {
            selectedMode = "VIRTUAL_DESKTOP_3";
        } else {
            selectedMode = "VIRTUAL_DESKTOP_2";
        }
        config.set("mt5.launch.mode", selectedMode);
        config.setReportsDirectory(outputDirField.getText().trim());
        config.setDataDirectory(dataDirField.getText().trim());
        config.set("backtest.deposit", String.valueOf(depositSpinner.getValue()));
        config.set("backtest.currency", currencyCombo.getValue());
        config.set("backtest.leverage", leverageField.getText().trim());
        config.set("backtest.model", String.valueOf(defaultModelCombo.getSelectionModel().getSelectedIndex()));
        config.set("broker.timezone.offset", String.valueOf(timezoneSpinner.getValue()));
        config.setBacktestTimeoutMinutes(timeoutSpinner.getValue());
        config.save();

        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Settings saved successfully!");
        alert.showAndWait();
    }

    private void resetDefaults() {
        mt5PathField.setText("C:\\Program Files\\MetaTrader 5\\terminal64.exe");
        mt4PathField.setText("C:\\Program Files\\MetaTrader 4\\terminal.exe");
        portableCheckbox.setSelected(true);
        launchModeCombo.setValue("Virtueller Desktop 2");
        depositSpinner.getValueFactory().setValue(10000);
        currencyCombo.setValue("USD");
        leverageField.setText("1:100");
        defaultModelCombo.getSelectionModel().select(0);
        timezoneSpinner.getValueFactory().setValue(2);
        timeoutSpinner.getValueFactory().setValue(10);
    }

    private void deleteLogFiles() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, 
            "Are you sure you want to delete all MetaTrader and Java backtester log files?\n\nThis will free up disk space.",
            ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Delete Log Files");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                long deletedBytes = performLogDeletion();
                double deletedMb = deletedBytes / (1024.0 * 1024.0);
                Alert info = new Alert(Alert.AlertType.INFORMATION, 
                    String.format("Successfully deleted %.2f MB of log files.", deletedMb));
                info.showAndWait();
                new Thread(this::updateDeleteButtonText).start();
            }
        });
    }

    private long performLogDeletion() {
        long totalDeletedBytes = 0;
        
        // 1. Clear Java backtester logs (truncate logs/backtester.log)
        Path backtesterLog = Paths.get("logs", "backtester.log");
        if (Files.exists(backtesterLog)) {
            try {
                long size = Files.size(backtesterLog);
                try (java.io.OutputStream os = Files.newOutputStream(backtesterLog)) {
                    // opening/closing a stream without append will truncate it to 0 bytes
                }
                totalDeletedBytes += size;
            } catch (Exception e) {
                // Ignore if locked
            }
        }
        
        // 2. Delete MT5 log files
        try {
            Path mt5Dir = config.getMt5InstallDir();
            if (Files.exists(mt5Dir)) {
                totalDeletedBytes += deleteLogFilesInDirectory(mt5Dir);
            }
        } catch (Exception e) {
            // Ignore
        }
        
        // 3. Delete MT4 log files
        try {
            String mt4Path = config.getMt4TerminalPath();
            if (mt4Path != null && !mt4Path.isEmpty()) {
                Path mt4Dir = Paths.get(mt4Path).getParent();
                if (mt4Dir != null && Files.exists(mt4Dir)) {
                    totalDeletedBytes += deleteLogFilesInDirectory(mt4Dir);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        
        return totalDeletedBytes;
    }

    private long deleteLogFilesInDirectory(Path root) {
        final long[] deletedBytes = {0};
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".log"))
                .forEach(p -> {
                    try {
                        long size = Files.size(p);
                        Files.delete(p);
                        deletedBytes[0] += size;
                    } catch (Exception e) {
                        // Skip locked files
                    }
                });
        } catch (Exception e) {
            // Ignore
        }
        return deletedBytes[0];
    }

    private HBox createDirField(TextField field) {
        field.setPrefWidth(550);
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

    private void browseTerminalPath(TextField field, String expectedExeName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select MetaTrader Terminal (" + expectedExeName + ")");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("MetaTrader Terminal", expectedExeName));
        
        File progFiles = new File("C:\\Program Files");
        if (progFiles.exists()) chooser.setInitialDirectory(progFiles);

        File selected = chooser.showOpenDialog(field.getScene().getWindow());
        if (selected != null) {
            field.setText(selected.getAbsolutePath());
        }
    }

    public VBox getView() {
        return root;
    }

    private void startLogSizeUpdater() {
        Thread thread = new Thread(() -> {
            while (true) {
                try {
                    updateDeleteButtonText();
                    Thread.sleep(60 * 60 * 1000L); // 1 hour
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    try { Thread.sleep(60000); } catch (InterruptedException ie) { break; }
                }
            }
        }, "LogSizeUpdater-Thread");
        thread.setDaemon(true);
        thread.start();
    }

    private void updateDeleteButtonText() {
        long totalBytes = calculateLogsTotalSize();
        double sizeMb = totalBytes / (1024.0 * 1024.0);
        String label;
        if (sizeMb >= 1024) {
            label = String.format("Delete Log Files (%.2f GB)", sizeMb / 1024.0);
        } else {
            label = String.format("Delete Log Files (%.2f MB)", sizeMb);
        }
        javafx.application.Platform.runLater(() -> deleteLogsBtn.setText(label));
    }

    private long calculateLogsTotalSize() {
        long totalBytes = 0;
        
        // 1. Java log size
        Path backtesterLog = Paths.get("logs", "backtester.log");
        if (Files.exists(backtesterLog)) {
            try {
                totalBytes += Files.size(backtesterLog);
            } catch (Exception e) {
                // Ignore
            }
        }
        
        // 2. MT5 logs size
        try {
            Path mt5Dir = config.getMt5InstallDir();
            if (Files.exists(mt5Dir)) {
                totalBytes += getLogFilesSizeInDirectory(mt5Dir);
            }
        } catch (Exception e) {
            // Ignore
        }
        
        // 3. MT4 logs size
        try {
            String mt4Path = config.getMt4TerminalPath();
            if (mt4Path != null && !mt4Path.isEmpty()) {
                Path mt4Dir = Paths.get(mt4Path).getParent();
                if (mt4Dir != null && Files.exists(mt4Dir)) {
                    totalBytes += getLogFilesSizeInDirectory(mt4Dir);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        
        return totalBytes;
    }

    private long getLogFilesSizeInDirectory(Path root) {
        final long[] size = {0};
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".log"))
                .forEach(p -> {
                    try {
                        size[0] += Files.size(p);
                    } catch (Exception e) {
                        // Ignore
                    }
                });
        } catch (Exception e) {
            // Ignore
        }
        return size[0];
    }
}
