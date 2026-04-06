package com.backtester.ui;

import com.backtester.config.AppConfig;
import com.backtester.engine.BacktestConfig;
import com.backtester.engine.BacktestRunner;
import com.backtester.report.BacktestResult;
import com.github.lgooddatepicker.components.DatePicker;
import com.github.lgooddatepicker.components.DatePickerSettings;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.stream.Stream;
import com.backtester.config.EaParameterManager;

/**
 * Backtest configuration and execution panel.
 * Allows user to set all backtest parameters and start/cancel tests.
 */
public class BacktestPanel extends JPanel {

    private final LogPanel logPanel;
    private final AppConfig config;

    // Input fields
    private JComboBox<String> symbolCombo;
    private JComboBox<String> periodCombo;
    private JComboBox<String> modelCombo;
    private JTextField expertField;
    private JButton expertBrowseBtn;
    private JButton expertConfigBtn;
    private JLabel configStatusLabel;
    private DatePicker fromDatePicker;
    private DatePicker toDatePicker;
    private JSpinner depositSpinner;
    private JComboBox<String> currencyCombo;
    private JTextField leverageField;

    // Action buttons
    private JButton startButton;
    private JButton cancelButton;
    private JProgressBar progressBar;

    // Results table
    private DefaultTableModel resultsTableModel;
    private JTable resultsTable;

    // Runner
    private BacktestRunner currentRunner;
    private SwingWorker<BacktestResult, String> currentWorker;
    private final EaParameterManager eaParamManager = new EaParameterManager();

    public BacktestPanel(LogPanel logPanel) {
        this.logPanel = logPanel;
        this.config = AppConfig.getInstance();

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Top: Configuration form
        JPanel configPanel = createConfigPanel();

        // Bottom: Results table
        JPanel resultsPanel = createResultsPanel();

        // Split pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, configPanel, resultsPanel);
        splitPane.setDividerLocation(380);
        splitPane.setResizeWeight(0.4);

        add(splitPane, BorderLayout.CENTER);

        // Load existing results
        loadExistingResults();
    }

    private JPanel createConfigPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        // Title
        JLabel title = new JLabel("Backtest Configuration");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(new Color(78, 154, 241));
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        // Form grid
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // Expert Advisor
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(createLabel("Expert Advisor:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        expertField = new JTextField(30);
        expertField.setToolTipText("Path relative to MQL5/Experts/ (e.g. MyEAs\\MyRobot)");
        JPanel expertPanel = new JPanel(new BorderLayout(5, 0));
        expertPanel.add(expertField, BorderLayout.CENTER);
        JPanel expertBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        expertBtnPanel.setOpaque(false);
        expertBrowseBtn = new JButton("...");
        expertBrowseBtn.setPreferredSize(new Dimension(40, 28));
        expertBrowseBtn.addActionListener(e -> browseExpert());
        expertConfigBtn = new JButton("⚙");
        expertConfigBtn.setPreferredSize(new Dimension(40, 28));
        expertConfigBtn.setToolTipText("Edit EA Input Parameters");
        expertConfigBtn.addActionListener(e -> openEaConfig());
        expertBtnPanel.add(expertBrowseBtn);
        expertBtnPanel.add(expertConfigBtn);
        expertPanel.add(expertBtnPanel, BorderLayout.EAST);
        form.add(expertPanel, gbc);

        // Config status label (own row)
        row++;
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1;
        gbc.insets = new Insets(0, 8, 4, 8);
        configStatusLabel = new JLabel("");
        configStatusLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        configStatusLabel.setForeground(new Color(241, 178, 78));
        form.add(configStatusLabel, gbc);
        gbc.insets = new Insets(6, 8, 6, 8);

        // Symbol
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(createLabel("Symbol:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        symbolCombo = new JComboBox<>(BacktestConfig.SYMBOLS);
        symbolCombo.setEditable(true);
        form.add(symbolCombo, gbc);

        // Period / Timeframe
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(createLabel("Timeframe:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        periodCombo = new JComboBox<>(BacktestConfig.TIMEFRAMES);
        periodCombo.setSelectedItem("H1");
        form.add(periodCombo, gbc);

        // Model
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(createLabel("Tick Model:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        modelCombo = new JComboBox<>(BacktestConfig.MODEL_NAMES);
        form.add(modelCombo, gbc);

        // Date range
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(createLabel("From Date:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        DatePickerSettings fromSettings = new DatePickerSettings();
        fromSettings.setFormatForDatesCommonEra("yyyy-MM-dd");
        fromSettings.setAllowEmptyDates(false);
        fromDatePicker = new DatePicker(fromSettings);
        fromDatePicker.setDate(LocalDate.now().minusYears(1));
        form.add(fromDatePicker, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(createLabel("To Date:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        DatePickerSettings toSettings = new DatePickerSettings();
        toSettings.setFormatForDatesCommonEra("yyyy-MM-dd");
        toSettings.setAllowEmptyDates(false);
        toDatePicker = new DatePicker(toSettings);
        toDatePicker.setDate(LocalDate.now());
        form.add(toDatePicker, gbc);

        // Deposit
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(createLabel("Deposit:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        depositSpinner = new JSpinner(new SpinnerNumberModel(
                config.getDefaultDeposit(), 100, 10000000, 1000));
        form.add(depositSpinner, gbc);

        // Currency + Leverage on same row
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(createLabel("Currency / Leverage:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        JPanel clPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        currencyCombo = new JComboBox<>(new String[]{"USD", "EUR", "GBP", "JPY", "CHF"});
        leverageField = new JTextField(config.getDefaultLeverage());
        clPanel.add(currencyCombo);
        clPanel.add(leverageField);
        form.add(clPanel, gbc);

        // Buttons row
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.weightx = 1;
        gbc.insets = new Insets(15, 8, 6, 8);
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttonPanel.setOpaque(false);

        startButton = new JButton("▶  Start Backtest");
        startButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        startButton.setBackground(new Color(40, 120, 70));
        startButton.setForeground(Color.WHITE);
        startButton.setPreferredSize(new Dimension(200, 38));
        startButton.addActionListener(e -> startBacktest());

        cancelButton = new JButton("■  Cancel");
        cancelButton.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        cancelButton.setPreferredSize(new Dimension(120, 38));
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(e -> cancelBacktest());

        buttonPanel.add(startButton);
        buttonPanel.add(cancelButton);

        form.add(buttonPanel, gbc);

        // Progress bar
        row++;
        gbc.gridy = row;
        gbc.insets = new Insets(4, 8, 6, 8);
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("Ready");
        progressBar.setPreferredSize(new Dimension(0, 24));
        form.add(progressBar, gbc);

        panel.add(title, BorderLayout.NORTH);
        panel.add(form, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JLabel title = new JLabel("Backtest Results");
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));
        title.setForeground(new Color(78, 154, 241));
        title.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));

        String[] columns = {"Expert", "Symbol", "Period", "Profit", "Trades",
                "Win Rate", "Drawdown", "Profit Factor", "Directory"};
        resultsTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        resultsTable = new JTable(resultsTableModel);
        resultsTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        resultsTable.setRowHeight(26);
        resultsTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        resultsTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        resultsTable.setAutoCreateRowSorter(true);

        // Set column widths
        resultsTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        resultsTable.getColumnModel().getColumn(8).setPreferredWidth(200);

        // Double-click to show report viewer
        resultsTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    showSelectedReport();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(resultsTable);

        // Toolbar for results
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        toolbar.setOpaque(false);

        JButton showReportBtn = new JButton("📊 Show Report");
        showReportBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        showReportBtn.setBackground(new Color(55, 90, 145));
        showReportBtn.setForeground(Color.WHITE);
        showReportBtn.addActionListener(e -> showSelectedReport());

        JButton openDirBtn = new JButton("📁 Open Folder");
        openDirBtn.addActionListener(e -> {
            int row = resultsTable.getSelectedRow();
            if (row >= 0) {
                String dir = (String) resultsTableModel.getValueAt(
                        resultsTable.convertRowIndexToModel(row), 8);
                openDirectory(dir);
            } else {
                openDirectory(config.getReportsDirectory().toString());
            }
        });

        JButton refreshBtn = new JButton("🔄 Refresh");
        refreshBtn.addActionListener(e -> loadExistingResults());

        JButton deleteBtn = new JButton("🗑 Delete");
        deleteBtn.addActionListener(e -> deleteSelectedResults());

        toolbar.add(showReportBtn);
        toolbar.add(openDirBtn);
        toolbar.add(refreshBtn);
        toolbar.add(deleteBtn);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);
        topPanel.add(title, BorderLayout.WEST);
        topPanel.add(toolbar, BorderLayout.EAST);

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private void startBacktest() {
        // Validate inputs
        String expert = expertField.getText().trim();
        if (expert.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please specify the Expert Advisor path.",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        LocalDate from = fromDatePicker.getDate();
        LocalDate to = toDatePicker.getDate();
        if (from == null || to == null) {
            JOptionPane.showMessageDialog(this,
                    "Please select valid start and end dates.",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (from.isAfter(to)) {
            JOptionPane.showMessageDialog(this,
                    "Start date must be before end date.",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Build config
        BacktestConfig btConfig = new BacktestConfig();
        btConfig.setExpert(expert);
        btConfig.setSymbol((String) symbolCombo.getSelectedItem());
        btConfig.setPeriod((String) periodCombo.getSelectedItem());
        btConfig.setModel(modelCombo.getSelectedIndex());
        btConfig.setFromDate(from);
        btConfig.setToDate(to);
        btConfig.setDeposit((Integer) depositSpinner.getValue());
        btConfig.setCurrency((String) currencyCombo.getSelectedItem());
        btConfig.setLeverage(leverageField.getText().trim());

        // Set EA parameters from config manager
        String setFileName = eaParamManager.prepareForBacktest(expert);
        if (setFileName != null) {
            btConfig.setExpertParameters(setFileName);
            boolean isCustom = eaParamManager.hasCustomConfig(expert);
            logPanel.log("INFO", "EA Config: Using " + (isCustom ? "CUSTOM" : "DEFAULT") + " parameters (" + setFileName + ")");
        } else {
            logPanel.log("INFO", "EA Config: No .set file found - using EA compiled defaults");
        }

        // UI state
        startButton.setEnabled(false);
        cancelButton.setEnabled(true);
        progressBar.setIndeterminate(true);
        progressBar.setString("Running backtest...");

        logPanel.log("INFO", "Starting backtest: " + btConfig.toDirectoryName());

        // Run in background
        currentRunner = new BacktestRunner();
        currentRunner.setLogCallback(msg -> logPanel.logMessage(msg));

        currentWorker = new SwingWorker<>() {
            @Override
            protected BacktestResult doInBackground() {
                return currentRunner.runBacktest(btConfig);
            }

            @Override
            protected void done() {
                try {
                    BacktestResult result = get();
                    if (result != null) {
                        addResultToTable(result);
                        // Track config info
                        boolean isCustom = eaParamManager.hasCustomConfig(expert);
                        if (setFileName != null) {
                            result.setUsedDefaultConfig(!isCustom);
                            int modCount = eaParamManager.countModifiedParameters(expert);
                            result.setConfigInfo(isCustom ? "Custom (" + modCount + " modified)" : "Default");
                        } else {
                            result.setUsedDefaultConfig(true);
                            result.setConfigInfo("No config (compiled defaults)");
                        }
                        if (result.isSuccess()) {
                            logPanel.log("INFO", "Backtest completed successfully");
                        } else {
                            logPanel.log("WARN", "Backtest finished with issues: " + result.getMessage());
                        }
                    } else {
                        logPanel.log("WARN", "Backtest returned no result (cancelled or failed)");
                    }
                } catch (Exception e) {
                    logPanel.log("ERROR", "Backtest failed: " + e.getMessage());
                } finally {
                    startButton.setEnabled(true);
                    cancelButton.setEnabled(false);
                    progressBar.setIndeterminate(false);
                    progressBar.setString("Ready");
                    progressBar.setValue(0);
                }
            }
        };

        currentWorker.execute();
    }

    private void cancelBacktest() {
        if (currentRunner != null) {
            currentRunner.cancel();
        }
        if (currentWorker != null) {
            currentWorker.cancel(true);
        }
        logPanel.log("WARN", "Backtest cancelled by user");
    }

    private void addResultToTable(BacktestResult result) {
        resultsTableModel.insertRow(0, new Object[]{
                result.getExpert(),
                result.getSymbol(),
                result.getPeriod(),
                String.format("%.2f", result.getTotalProfit()),
                result.getTotalTrades(),
                String.format("%.1f%%", result.getWinRate()),
                String.format("%.2f%%", result.getMaxDrawdown()),
                String.format("%.2f", result.getProfitFactor()),
                result.getOutputDirectory()
        });
    }

    private void loadExistingResults() {
        resultsTableModel.setRowCount(0);

        Path reportsDir = config.getReportsDirectory();
        if (!Files.exists(reportsDir)) return;

        com.backtester.report.ReportParser parser = new com.backtester.report.ReportParser();

        try (Stream<Path> dirs = Files.list(reportsDir)) {
            dirs.filter(Files::isDirectory)
                .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
                .forEach(dir -> {
                    BacktestResult result = new BacktestResult();
                    result.setOutputDirectory(dir.toString());

                    // First try to parse the actual report (.htm or legacy .xml)
                    Path reportHtm = dir.resolve("report.htm");
                    Path reportXml = dir.resolve("report.xml");
                    Path reportToParse = Files.exists(reportHtm) ? reportHtm : (Files.exists(reportXml) ? reportXml : null);
                    if (reportToParse != null) {
                        try {
                            BacktestResult parsed = parser.parse(reportToParse);
                            if (parsed != null) {
                                result = parsed;
                            }
                        } catch (Exception e) {
                            // parse failed
                        }
                    }

                    // Extract metadata from summary.txt if needed
                    Path summary = dir.resolve("summary.txt");
                    if (Files.exists(summary)) {
                        try {
                            String content = Files.readString(summary);
                            if (result.getExpert() == null || result.getExpert().isEmpty()) {
                                result.setExpert(extractValue(content, "Expert:"));
                                result.setSymbol(extractValue(content, "Symbol:"));
                                result.setPeriod(extractValue(content, "Period:"));
                            }
                            // Fallback stats if still 0
                            if (result.getTotalTrades() == 0 && result.getTotalProfit() == 0.0) {
                                try { result.setTotalProfit(Double.parseDouble(extractValue(content, "Total Profit:").replaceAll("[^\\d.\\-]", ""))); } catch(Exception e){}
                                try { result.setTotalTrades((int)Double.parseDouble(extractValue(content, "Total Trades:").replaceAll("[^\\d.\\-]", ""))); } catch(Exception e){}
                                try { result.setWinRate(Double.parseDouble(extractValue(content, "Win Rate:").replace("%", "").replaceAll("[^\\d.\\-]", ""))); } catch(Exception e){}
                                try { result.setMaxDrawdown(Double.parseDouble(extractValue(content, "Max Drawdown:").replace("%", "").replaceAll("[^\\d.\\-]", ""))); } catch(Exception e){}
                                try { result.setProfitFactor(Double.parseDouble(extractValue(content, "Profit Factor:").replaceAll("[^\\d.\\-]", ""))); } catch(Exception e){}
                            }
                        } catch (Exception e) {
                            // fallback failed
                        }
                    }

                    resultsTableModel.addRow(new Object[]{
                            result.getExpert() != null && !result.getExpert().isEmpty() ? result.getExpert() : dir.getFileName().toString(),
                            result.getSymbol() != null && !result.getSymbol().isEmpty() ? result.getSymbol() : "-",
                            result.getPeriod() != null && !result.getPeriod().isEmpty() ? result.getPeriod() : "-",
                            String.format("%.2f", result.getTotalProfit()),
                            result.getTotalTrades(),
                            String.format("%.1f%%", result.getWinRate()),
                            String.format("%.2f%%", result.getMaxDrawdown()),
                            String.format("%.2f", result.getProfitFactor()),
                            dir.toString()
                    });
                });
        } catch (Exception e) {
            logPanel.log("WARN", "Could not load existing results: " + e.getMessage());
        }
    }

    private String extractValue(String content, String key) {
        for (String line : content.split("\n")) {
            if (line.trim().startsWith(key)) {
                return line.substring(line.indexOf(':') + 1).trim();
            }
        }
        return "";
    }

    private void browseExpert() {
        Path mt5Dir = config.getMt5InstallDir();
        Path expertsDir = mt5Dir != null ? mt5Dir.resolve("MQL5").resolve("Experts") : null;

        JFileChooser chooser = new JFileChooser();
        if (expertsDir != null && Files.exists(expertsDir)) {
            chooser.setCurrentDirectory(expertsDir.toFile());
        }
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("MT5 Expert Advisor (*.ex5)", "ex5"));
        chooser.setDialogTitle("Select Expert Advisor");

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            // Try to make path relative to MQL5/Experts/
            if (expertsDir != null && selected.toPath().startsWith(expertsDir)) {
                String relative = expertsDir.relativize(selected.toPath()).toString();
                // Remove .ex5 extension
                if (relative.toLowerCase().endsWith(".ex5")) {
                    relative = relative.substring(0, relative.length() - 4);
                }
                expertField.setText(relative);
            } else {
                expertField.setText(selected.getAbsolutePath());
            }
            updateConfigStatus();
        }
    }

    private void openEaConfig() {
        String expert = expertField.getText().trim();
        if (expert.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please specify the Expert Advisor first.",
                    "No EA Selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        EaConfigDialog.showForExpert(SwingUtilities.getWindowAncestor(this), expert);
        updateConfigStatus();
    }

    private void updateConfigStatus() {
        String expert = expertField.getText().trim();
        if (expert.isEmpty()) {
            configStatusLabel.setText("");
            return;
        }
        if (eaParamManager.hasCustomConfig(expert)) {
            int modCount = eaParamManager.countModifiedParameters(expert);
            configStatusLabel.setText("⚡ Custom Config (" + modCount + " parameters modified)");
            configStatusLabel.setForeground(new Color(241, 178, 78));
        } else if (eaParamManager.hasDefaultConfig(expert)) {
            configStatusLabel.setText("✓ Default Config available");
            configStatusLabel.setForeground(new Color(100, 200, 120));
        } else {
            configStatusLabel.setText("○ No config found (EA compiled defaults)");
            configStatusLabel.setForeground(new Color(160, 165, 175));
        }
    }

    private void showSelectedReport() {
        int row = resultsTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this,
                    "Please select a backtest result to view.",
                    "No Selection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String dir = (String) resultsTableModel.getValueAt(
                resultsTable.convertRowIndexToModel(row), 8);
        ReportViewerDialog.showForDirectory(
                SwingUtilities.getWindowAncestor(this), dir);
    }

    private void deleteSelectedResults() {
        int[] rows = resultsTable.getSelectedRows();
        if (rows.length == 0) {
            JOptionPane.showMessageDialog(this,
                    "Please select one or more results to delete.",
                    "No Selection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete the " + rows.length + " selected test run(s)?\nThis will permanently delete the associated files from disk.",
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            // Sort rows in descending order to avoid index shifting issues when removing from model
            java.util.Arrays.sort(rows);
            for (int i = rows.length - 1; i >= 0; i--) {
                int modelRow = resultsTable.convertRowIndexToModel(rows[i]);
                String dir = (String) resultsTableModel.getValueAt(modelRow, 8);
                try {
                    Path dirPath = Paths.get(dir);
                    if (Files.exists(dirPath)) {
                        try (var files = Files.walk(dirPath)) {
                            files.sorted(java.util.Comparator.reverseOrder())
                                 .map(Path::toFile)
                                 .forEach(File::delete);
                        }
                    }
                    resultsTableModel.removeRow(modelRow);
                    logPanel.log("INFO", "Deleted: " + dir);
                } catch (Exception e) {
                    logPanel.log("ERROR", "Failed to delete: " + e.getMessage());
                }
            }
        }
    }

    private void openDirectory(String path) {
        try {
            Desktop.getDesktop().open(new File(path));
        } catch (Exception e) {
            logPanel.log("ERROR", "Could not open directory: " + e.getMessage());
        }
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        return label;
    }
}
