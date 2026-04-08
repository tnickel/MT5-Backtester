package com.backtester.ui;

import com.backtester.config.AppConfig;
import com.backtester.config.EaParameter;
import com.backtester.config.EaParameterManager;
import com.backtester.engine.BacktestConfig;
import com.backtester.engine.OptimizationConfig;
import com.backtester.engine.OptimizationRunner;
import com.backtester.report.OptimizationResult;
import com.github.lgooddatepicker.components.DatePicker;
import com.github.lgooddatepicker.components.DatePickerSettings;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.Vector;

public class OptimizationPanel extends JPanel {

    private final LogPanel logPanel;
    private final AppConfig config;
    private final EaParameterManager eaParamManager = new EaParameterManager();

    // Config fields
    private JTextField expertField;
    private JButton expertBrowseBtn;
    private JLabel configStatusLabel;
    private JComboBox<String> symbolCombo;
    private JComboBox<String> periodCombo;
    private JComboBox<String> modelCombo;
    private DatePicker fromDatePicker;
    private DatePicker toDatePicker;
    private JTextField depositField;
    private JTextField currencyField;
    private JTextField leverageField;

    // Optimization fields
    private JComboBox<String> optimizationModeCombo;
    private JComboBox<String> optimizationCriterionCombo;
    private JComboBox<String> forwardModeCombo;
    private DatePicker forwardDatePicker;
    private JCheckBox useLocalBox;
    private JCheckBox useRemoteBox;
    private JCheckBox useCloudBox;

    // Parameter Table
    private JTable paramTable;
    private DefaultTableModel paramTableModel;
    private JButton loadSetBtn;
    private JButton saveSetBtn;
    private JButton generateDefaultBtn;

    // Result Tables
    private JTabbedPane resultTabbedPane;
    private JTable resultTable;
    private DefaultTableModel resultTableModel;
    private JTable forwardResultTable;
    private DefaultTableModel forwardResultTableModel;
    private JLabel bestResultLabel;
    private JButton applyBestBtn;
    private JButton openReportBtn;

    // Control
    private JButton startBtn;
    private JButton cancelBtn;
    private JProgressBar progressBar;

    private OptimizationRunner currentRunner;
    private SwingWorker<OptimizationResult, String> currentWorker;
    private OptimizationResult lastResult;
    private java.util.Map<String, com.backtester.report.BacktestResult> backtestCache = new java.util.HashMap<>();

    public OptimizationPanel(LogPanel logPanel) {
        this.logPanel = logPanel;
        this.config = AppConfig.getInstance();

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Top: Split between Settings and Params
        JPanel topPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        topPanel.add(createConfigPanel());
        topPanel.add(createParamPanel());

        // Bottom: Results
        JPanel resultsPanel = createResultsPanel();

        // Split pane Main
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topPanel, resultsPanel);
        mainSplitPane.setDividerLocation(380);
        mainSplitPane.setResizeWeight(0.5);

        add(mainSplitPane, BorderLayout.CENTER);
        
        loadPreferences();
    }

    private JPanel createConfigPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createTitledBorder("Optimization Settings"));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // Expert
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel("Expert Advisor:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        expertField = new JTextField();
        JPanel expertPanel = new JPanel(new BorderLayout(5, 0));
        expertPanel.add(expertField, BorderLayout.CENTER);
        expertBrowseBtn = new JButton("...");
        expertBrowseBtn.addActionListener(e -> browseExpert());
        expertPanel.add(expertBrowseBtn, BorderLayout.EAST);
        form.add(expertPanel, gbc);

        row++;
        gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1;
        configStatusLabel = new JLabel(" ");
        configStatusLabel.setForeground(new Color(241, 178, 78));
        configStatusLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        form.add(configStatusLabel, gbc);

        // Symbol & Period
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel("Symbol:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        JPanel symPerPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        symbolCombo = new JComboBox<>(BacktestConfig.SYMBOLS);
        symbolCombo.setEditable(true);
        periodCombo = new JComboBox<>(new String[]{"M1", "M5", "M15", "M30", "H1", "H4", "D1"});
        periodCombo.setSelectedItem("H1");
        symPerPanel.add(symbolCombo);
        symPerPanel.add(periodCombo);
        form.add(symPerPanel, gbc);

        // Model
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel("Tick Model:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        modelCombo = new JComboBox<>(OptimizationConfig.MODEL_NAMES);
        modelCombo.setSelectedIndex(1); // Default: 1 min OHLC for speed
        form.add(modelCombo, gbc);

        // Date Range
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel("Dates:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        JPanel datePanel = new JPanel(new GridLayout(1, 2, 5, 0));
        DatePickerSettings fromSettings = new DatePickerSettings();
        fromSettings.setFormatForDatesCommonEra("yyyy-MM-dd");
        fromDatePicker = new DatePicker(fromSettings);
        fromDatePicker.setDate(LocalDate.now().minusYears(1));
        
        DatePickerSettings toSettings = new DatePickerSettings();
        toSettings.setFormatForDatesCommonEra("yyyy-MM-dd");
        toDatePicker = new DatePicker(toSettings);
        toDatePicker.setDate(LocalDate.now());
        
        datePanel.add(fromDatePicker);
        datePanel.add(toDatePicker);
        form.add(datePanel, gbc);

        // Deposit
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel("Account:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        JPanel accPanel = new JPanel(new GridLayout(1, 3, 5, 0));
        depositField = new JTextField("10000");
        currencyField = new JTextField("USD");
        leverageField = new JTextField("1:100");
        accPanel.add(depositField);
        accPanel.add(currencyField);
        accPanel.add(leverageField);
        form.add(accPanel, gbc);

        // Separator
        row++;
        gbc.gridx = 0; gbc.gridwidth = 2; gbc.gridy = row;
        form.add(new JSeparator(), gbc);
        gbc.gridwidth = 1;

        // Optimization Mode
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel("Opt. Mode:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        optimizationModeCombo = new JComboBox<>(OptimizationConfig.OPTIMIZATION_MODES);
        optimizationModeCombo.setSelectedIndex(1); // Default to Genetic
        form.add(optimizationModeCombo, gbc);

        // Opt Criterion
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel("Criterion:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        optimizationCriterionCombo = new JComboBox<>(OptimizationConfig.OPTIMIZATION_CRITERIA);
        optimizationCriterionCombo.setSelectedIndex(0); // Balance
        form.add(optimizationCriterionCombo, gbc);

        // Forward
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel("Forward:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        JPanel fwdPanel = new JPanel(new BorderLayout(5, 0));
        forwardModeCombo = new JComboBox<>(OptimizationConfig.FORWARD_MODES);
        DatePickerSettings fwdSettings = new DatePickerSettings();
        fwdSettings.setFormatForDatesCommonEra("yyyy-MM-dd");
        forwardDatePicker = new DatePicker(fwdSettings);
        forwardDatePicker.setEnabled(false);
        
        forwardModeCombo.addActionListener(e -> {
            forwardDatePicker.setEnabled(forwardModeCombo.getSelectedIndex() == 4);
        });
        
        fwdPanel.add(forwardModeCombo, BorderLayout.CENTER);
        fwdPanel.add(forwardDatePicker, BorderLayout.EAST);
        form.add(fwdPanel, gbc);

        // Agents
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel("Agents:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        JPanel agentPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        useLocalBox = new JCheckBox("Local", true);
        useRemoteBox = new JCheckBox("Remote", false);
        useCloudBox = new JCheckBox("MQL5 Cloud", false);
        agentPanel.add(useLocalBox);
        agentPanel.add(useRemoteBox);
        agentPanel.add(useCloudBox);
        form.add(agentPanel, gbc);

        JScrollPane scrollPane = new JScrollPane(form);
        scrollPane.setBorder(null);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createParamPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("EA Parameters & Optimization Ranges"));

        String[] cols = {"Opt", "Name", "Value", "Start", "Step", "End"};
        paramTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 0) return Boolean.class;
                return String.class;
            }
            @Override
            public boolean isCellEditable(int row, int column) {
                if (column == 1) return false; // Name is readonly
                return true;
            }
        };

        paramTable = new JTable(paramTableModel);
        paramTable.getColumnModel().getColumn(0).setMaxWidth(40);
        paramTable.setRowHeight(24);

        JScrollPane scrollPane = new JScrollPane(paramTable);
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new BorderLayout());
        
        JPanel leftBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton autoConfigBtn = new JButton("\uD83D\uDD27 AutoConfig");
        autoConfigBtn.setToolTipText("Automatisch Optimierungs-Ranges für Kennlinienfahrt setzen");
        autoConfigBtn.setBackground(new Color(55, 90, 145));
        autoConfigBtn.setForeground(Color.WHITE);
        autoConfigBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        autoConfigBtn.addActionListener(e -> autoConfigParameters());
        leftBtnPanel.add(autoConfigBtn);
        
        JPanel rightBtnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        loadSetBtn = new JButton("Load .set");
        saveSetBtn = new JButton("Save .set");
        generateDefaultBtn = new JButton("Generate Default");
        
        loadSetBtn.addActionListener(e -> loadParameters());
        saveSetBtn.addActionListener(e -> saveParameters());
        generateDefaultBtn.addActionListener(e -> generateDefaultParams());

        rightBtnPanel.add(generateDefaultBtn);
        rightBtnPanel.add(loadSetBtn);
        rightBtnPanel.add(saveSetBtn);
        
        btnPanel.add(leftBtnPanel, BorderLayout.WEST);
        btnPanel.add(rightBtnPanel, BorderLayout.EAST);
        panel.add(btnPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Optimization Results"));
        
        resultTabbedPane = new JTabbedPane();

        // Main Result Table
        resultTableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        resultTable = new JTable(resultTableModel);
        resultTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        resultTable.setAutoCreateRowSorter(true);
        resultTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int viewRow = resultTable.getSelectedRow();
                    if (viewRow != -1) runBacktestForPass(resultTable, viewRow);
                }
            }
        });
        resultTabbedPane.addTab("Main Optimization", new JScrollPane(resultTable));
        
        // Forward Result Table
        forwardResultTableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        forwardResultTable = new JTable(forwardResultTableModel);
        forwardResultTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        forwardResultTable.setAutoCreateRowSorter(true);
        forwardResultTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int viewRow = forwardResultTable.getSelectedRow();
                    if (viewRow != -1) runBacktestForPass(forwardResultTable, viewRow);
                }
            }
        });
        resultTabbedPane.addTab("Forward Results", new JScrollPane(forwardResultTable));

        panel.add(resultTabbedPane, BorderLayout.CENTER);

        // Control Panel
        JPanel controlPanel = new JPanel(new BorderLayout(10, 0));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JPanel runPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        startBtn = new JButton("▶ Start Optimization");
        startBtn.setBackground(new Color(40, 130, 40));
        startBtn.setForeground(Color.WHITE);
        startBtn.addActionListener(e -> startOptimization());

        cancelBtn = new JButton("⬛ Cancel");
        cancelBtn.setEnabled(false);
        cancelBtn.addActionListener(e -> cancelOptimization());

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(150, 20));

        runPanel.add(startBtn);
        runPanel.add(cancelBtn);
        runPanel.add(progressBar);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bestResultLabel = new JLabel("");
        applyBestBtn = new JButton("Apply Best Parameters");
        applyBestBtn.setEnabled(false);
        applyBestBtn.addActionListener(e -> applyBestResult());

        openReportBtn = new JButton("Open XML");
        openReportBtn.setEnabled(false);
        openReportBtn.addActionListener(e -> openReport());

        actionPanel.add(bestResultLabel);
        actionPanel.add(applyBestBtn);
        actionPanel.add(openReportBtn);

        controlPanel.add(runPanel, BorderLayout.WEST);
        controlPanel.add(actionPanel, BorderLayout.EAST);

        panel.add(controlPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void browseExpert() {
        JFileChooser chooser = new JFileChooser(Paths.get(config.getMt5TerminalPath()).getParent().resolve("MQL5").resolve("Experts").toFile());
        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            String name = chooser.getSelectedFile().getName();
            if (name.toLowerCase().endsWith(".ex5")) {
                name = name.substring(0, name.length() - 4);
            }
            expertField.setText(name);
            loadParameters();
            savePreferences();
        }
    }

    private void loadParameters() {
        String expert = expertField.getText().trim();
        if (expert.isEmpty()) return;

        try {
            if (!eaParamManager.hasCustomConfig(expert) && !eaParamManager.hasDefaultConfig(expert)) {
                eaParamManager.generateDefaultConfig(expert);
            }
            List<EaParameter> params = eaParamManager.getEffectiveParameters(expert);
            updateParamTable(params);
            configStatusLabel.setText("Loaded parameters for " + expert);
        } catch (Exception e) {
            logPanel.log("ERROR", "Failed to load parameters: " + e.getMessage());
            configStatusLabel.setText("Error loading parameters");
        }
    }

    private void updateParamTable(List<EaParameter> params) {
        paramTableModel.setRowCount(0);
        for (EaParameter p : params) {
            paramTableModel.addRow(new Object[]{
                p.isOptimizeEnabled(),
                p.getName(),
                p.getValue(),
                p.getOptimizeStart(),
                p.getOptimizeStep(),
                p.getOptimizeEnd()
            });
        }
    }

    private void saveParameters() {
        String expert = expertField.getText().trim();
        if (expert.isEmpty()) return;

        if (paramTable.isEditing()) {
            paramTable.getCellEditor().stopCellEditing();
        }

        try {
            List<EaParameter> params = eaParamManager.getEffectiveParameters(expert);
            if (params == null) return;
            
            // Update from table
            int mappingCount = Math.min(params.size(), paramTableModel.getRowCount());
            for (int i = 0; i < mappingCount; i++) {
                EaParameter p = params.get(i);
                p.setOptimizeEnabled((Boolean) paramTableModel.getValueAt(i, 0));
                p.setValue(String.valueOf(paramTableModel.getValueAt(i, 2)));
                p.setOptimizeStart(String.valueOf(paramTableModel.getValueAt(i, 3)));
                p.setOptimizeStep(String.valueOf(paramTableModel.getValueAt(i, 4)));
                p.setOptimizeEnd(String.valueOf(paramTableModel.getValueAt(i, 5)));
            }

            eaParamManager.saveCustomParameters(expert, params);
            logPanel.log("INFO", "Saved optimization ranges for " + expert);
        } catch (Exception e) {
            logPanel.log("ERROR", "Failed to save parameters: " + e.getMessage());
        }
    }

    private void generateDefaultParams() {
        String expert = expertField.getText().trim();
        if (expert.isEmpty()) return;

        try {
            logPanel.log("INFO", "Generating default parameters from MT5 for " + expert + "...");
            eaParamManager.generateDefaultConfig(expert);
            List<EaParameter> params = eaParamManager.getEffectiveParameters(expert);
            updateParamTable(params);
            logPanel.log("INFO", "Default parameters generated.");
        } catch (Exception e) {
            logPanel.log("ERROR", "Failed to generate defaults: " + e.getMessage());
        }
    }

    private void startOptimization() {
        savePreferences();
        saveParameters(); // Ensure current UI table goes to .set file

        OptimizationConfig optConfig = new OptimizationConfig();
        optConfig.setExpert(expertField.getText().trim());
        try {
            String preset = eaParamManager.prepareForBacktest(expertField.getText().trim());
            if (preset != null) {
                optConfig.setExpertParameters(preset);
            }
        } catch (Exception e) {
            logPanel.log("ERROR", "Cannot resolve config path");
            return;
        }

        optConfig.setSymbol(symbolCombo.getSelectedItem().toString());
        optConfig.setPeriod(periodCombo.getSelectedItem().toString());
        optConfig.setModel(modelCombo.getSelectedIndex());
        optConfig.setFromDate(fromDatePicker.getDate());
        optConfig.setToDate(toDatePicker.getDate());
        
        try {
            optConfig.setDeposit(Integer.parseInt(depositField.getText().trim()));
        } catch (NumberFormatException e) {
            optConfig.setDeposit(10000);
        }
        optConfig.setCurrency(currencyField.getText().trim());
        optConfig.setLeverage(leverageField.getText().trim());

        // Optimization specific
        optConfig.setOptimizationMode(OptimizationConfig.OPTIMIZATION_MODE_VALUES[optimizationModeCombo.getSelectedIndex()]);
        optConfig.setOptimizationCriterion(optimizationCriterionCombo.getSelectedIndex());
        optConfig.setForwardMode(forwardModeCombo.getSelectedIndex());
        optConfig.setForwardDate(forwardDatePicker.getDate());
        optConfig.setUseLocal(useLocalBox.isSelected());
        optConfig.setUseRemote(useRemoteBox.isSelected());
        optConfig.setUseCloud(useCloudBox.isSelected());

        setUIState(true);
        resultTableModel.setRowCount(0);
        resultTableModel.setColumnCount(0);
        forwardResultTableModel.setRowCount(0);
        forwardResultTableModel.setColumnCount(0);
        backtestCache.clear();

        currentRunner = new OptimizationRunner(config);
        currentRunner.setLogCallback(msg -> logPanel.log("OPT", msg));

        currentWorker = new SwingWorker<OptimizationResult, String>() {
            @Override
            protected OptimizationResult doInBackground() throws Exception {
                return currentRunner.runOptimization(optConfig);
            }

            @Override
            protected void done() {
                try {
                    lastResult = get();
                    if (lastResult.isSuccess()) {
                        if (lastResult.getPasses().isEmpty()) {
                            logPanel.log("WARN", "Optimierung abgeschlossen, aber keine Ergebnisse: " + 
                                    (lastResult.getMessage() != null ? lastResult.getMessage() : "Keine Passes produziert"));
                            bestResultLabel.setText("⚠ Keine Daten");
                            bestResultLabel.setForeground(new Color(241, 178, 78));
                        } else {
                            logPanel.log("INFO", "Optimization finished successfully.");
                            updateResultTable(lastResult);
                        }
                        try {
                            com.google.gson.JsonObject metrics = new com.google.gson.JsonObject();
                            metrics.addProperty("passes", lastResult.getPasses().size());
                            metrics.addProperty("forwardPasses", lastResult.getForwardPasses().size());
                            com.backtester.database.DatabaseManager.getInstance().saveRun(
                                "OPTIMIZATION", 
                                optConfig.getExpert(), 
                                System.currentTimeMillis(), 
                                metrics.toString(), 
                                lastResult.getOutputDirectory()
                            );
                        } catch (Exception ex) {
                            logPanel.log("ERROR", "Failed to save optimization to DB");
                        }
                    } else if (lastResult.getMessage() != null && lastResult.getMessage().contains("cancelled")) {
                        logPanel.log("WARN", "Optimization cancelled by user.");
                    } else {
                        logPanel.log("ERROR", "Optimization failed: " + lastResult.getMessage());
                        bestResultLabel.setText("⚠ Fehler: " + lastResult.getMessage());
                        bestResultLabel.setForeground(new Color(220, 80, 80));
                    }
                } catch (Exception e) {
                    logPanel.log("ERROR", "Worker error: " + e.getMessage());
                } finally {
                    setUIState(false);
                }
            }
        };

        currentWorker.execute();
    }

    private void cancelOptimization() {
        if (currentRunner != null) {
            currentRunner.cancel();
        }
    }

    private void setUIState(boolean running) {
        startBtn.setEnabled(!running);
        cancelBtn.setEnabled(running);
        progressBar.setVisible(running);
        applyBestBtn.setEnabled(!running && lastResult != null && lastResult.isSuccess());
        openReportBtn.setEnabled(!running && lastResult != null && lastResult.isSuccess());
    }

    private void updateResultTable(OptimizationResult result) {
        populateTable(resultTable, resultTableModel, result.getPasses(), result.getParameterNames());
        populateTable(forwardResultTable, forwardResultTableModel, result.getForwardPasses(), result.getParameterNames());
        
        if (result.hasForwardResults()) {
            resultTabbedPane.setSelectedIndex(1); // Show forward tab automatically
        } else {
            resultTabbedPane.setSelectedIndex(0);
        }
        
        // Highlight best
        OptimizationResult.Pass best = result.getBestByCriterion(optimizationCriterionCombo.getSelectedIndex());
        if (best != null) {
            bestResultLabel.setText(String.format("Best Pass: #%d (Profit: %.2f)", best.getPassNumber(), best.getProfit()));
        } else {
            bestResultLabel.setText("");
        }
    }
    
    private void populateTable(JTable table, DefaultTableModel model, List<OptimizationResult.Pass> passes, List<String> paramNames) {
        if (passes.isEmpty()) return;
        
        // Build columns
        Vector<String> colNames = new Vector<>();
        colNames.add("Pass");
        colNames.add("Result/Profit");
        colNames.add("Total Trades");
        colNames.add("Profit Factor");
        colNames.add("Expected Payoff");
        colNames.add("Drawdown $");
        colNames.add("Drawdown %");
        colNames.add("Recovery Factor");
        colNames.add("Sharpe");
        
        for (String param : paramNames) {
            colNames.add(param);
        }
        
        model.setColumnIdentifiers(colNames);
        
        // Add Rows
        for (OptimizationResult.Pass pass : passes) {
            Vector<Object> row = new Vector<>();
            row.add(pass.getPassNumber());
            row.add(pass.getProfit());
            row.add(pass.getTotalTrades());
            row.add(pass.getProfitFactor());
            row.add(pass.getExpectedPayoff());
            row.add(pass.getDrawdown());
            row.add(pass.getDrawdownPercent() + "%");
            row.add(pass.getRecoveryFactor());
            row.add(pass.getSharpeRatio());
            
            for (String param : paramNames) {
                row.add(pass.getParameter(param));
            }
            
            model.addRow(row);
        }
        
        // Adjust column widths
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(100);
        }
    }

    private void runBacktestForPass(JTable table, int viewRow) {
        int modelRow = table.convertRowIndexToModel(viewRow);
        int passNumber = (Integer) table.getModel().getValueAt(modelRow, 0);
        
        OptimizationResult.Pass pass = null;
        boolean isForward = (table == forwardResultTable);
        if (lastResult != null) {
            for (OptimizationResult.Pass p : lastResult.getPasses()) {
                if (p.getPassNumber() == passNumber) { pass = p; break; }
            }
            if (pass == null && lastResult.getForwardPasses() != null) {
                for (OptimizationResult.Pass p : lastResult.getForwardPasses()) {
                    if (p.getPassNumber() == passNumber) { pass = p; break; }
                }
            }
        }
        
        if (pass == null) return;
        
        String cacheKey = (isForward ? "F" : "M") + passNumber;
        if (backtestCache.containsKey(cacheKey)) {
            new com.backtester.ui.ReportViewerDialog(
                (JFrame) SwingUtilities.getWindowAncestor(OptimizationPanel.this),
                backtestCache.get(cacheKey)
            ).setVisible(true);
            return;
        }
        
        // 1. Save these params to .set
        String expert = expertField.getText().trim();
        try {
            List<EaParameter> params = eaParamManager.getEffectiveParameters(expert);
            if (params != null) {
                for (EaParameter p : params) {
                    String val = pass.getParameter(p.getName());
                    if (val != null && !val.isEmpty()) {
                        p.setValue(val);
                    }
                }
                eaParamManager.saveCustomParameters(expert, params);
            }
        } catch (Exception ex) {
            logPanel.log("ERROR", "Failed to apply parameters for backtest: " + ex.getMessage());
            return;
        }

        // 2. Set up backtest config
        com.backtester.engine.BacktestConfig btConfig = new com.backtester.engine.BacktestConfig();
        btConfig.setSymbol(symbolCombo.getSelectedItem().toString());
        btConfig.setPeriod(periodCombo.getSelectedItem().toString());
        btConfig.setExpert(expert);
        btConfig.setModel(modelCombo.getSelectedIndex());
        btConfig.setFromDate(fromDatePicker.getDate());
        btConfig.setToDate(toDatePicker.getDate());
        
        try {
            btConfig.setDeposit(Integer.parseInt(depositField.getText().trim()));
        } catch (NumberFormatException e) {
            btConfig.setDeposit(10000);
        }
        btConfig.setCurrency(currencyField.getText().trim());
        btConfig.setLeverage(leverageField.getText().trim());

        String presetFile = eaParamManager.prepareForBacktest(expert);
        btConfig.setExpertParameters(presetFile);
        
        // 3. Run backtest in background
        setUIState(true);
        progressBar.setString("Running Single Backtest...");
        progressBar.setStringPainted(true);
        
        com.backtester.engine.BacktestRunner runner = new com.backtester.engine.BacktestRunner();
        runner.setLogCallback(msg -> logPanel.log("OPT-BT", msg));
        
        new SwingWorker<com.backtester.report.BacktestResult, Void>() {
            @Override
            protected com.backtester.report.BacktestResult doInBackground() {
                return runner.runBacktest(btConfig);
            }
            @Override
            protected void done() {
                setUIState(false);
                progressBar.setStringPainted(false);
                try {
                    com.backtester.report.BacktestResult res = get();
                    if (res != null && res.isSuccess()) {
                        backtestCache.put(cacheKey, res);
                        new com.backtester.ui.ReportViewerDialog(
                            (JFrame) SwingUtilities.getWindowAncestor(OptimizationPanel.this),
                            res
                        ).setVisible(true);
                    } else {
                        JOptionPane.showMessageDialog(OptimizationPanel.this, "Backtest failed.", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }.execute();
    }

    private void applyBestResult() {
        if (lastResult == null || !lastResult.isSuccess()) return;
        
        OptimizationResult.Pass best = lastResult.getBestByCriterion(optimizationCriterionCombo.getSelectedIndex());
        if (best == null) return;
        
        String expert = expertField.getText().trim();
        try {
            List<EaParameter> params = eaParamManager.getEffectiveParameters(expert);
            if (params == null) return;
            
            for (EaParameter p : params) {
                String bestVal = best.getParameter(p.getName());
                if (bestVal != null && !bestVal.isEmpty()) {
                    p.setValue(bestVal);
                }
            }
            
            eaParamManager.saveCustomParameters(expert, params);
            updateParamTable(params);
            logPanel.log("INFO", "Applied best parameters (pass #" + best.getPassNumber() + ") to EA config.");
            JOptionPane.showMessageDialog(this, "Best parameters applied and saved to config.", "Success", JOptionPane.INFORMATION_MESSAGE);
            
        } catch (Exception e) {
            logPanel.log("ERROR", "Failed to apply best parameters: " + e.getMessage());
        }
    }

    private void openReport() {
        if (lastResult != null && lastResult.getOutputDirectory() != null) {
            Path reportPath = Paths.get(lastResult.getOutputDirectory(), "optimization_report.xml");
            if (Files.exists(reportPath)) {
                try {
                    Desktop.getDesktop().open(reportPath.toFile());
                } catch (Exception e) {
                    logPanel.log("ERROR", "Could not open report: " + e.getMessage());
                }
            }
        }
    }

    private void savePreferences() {
        // Same as BacktestPanel, we just use a small local save for now.
        // For brevity we only save expert string in AppConfig or local preferences in the future.
    }

    private void loadPreferences() {
        // Not strictly implemented for Opt yet
    }

    // ==================== AutoConfig Logic ====================

    /**
     * Automatically configures optimization ranges for all relevant EA parameters.
     * Designed for "Kennlinienfahrt" (characteristic curve analysis):
     * - Activates all numeric parameters (except excluded ones)
     * - Sets Start to a sensible minimum (typically 1)
     * - Sets End based on the current value
     * - Calculates Step to produce 10-100 data points
     */
    private void autoConfigParameters() {
        if (paramTableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this,
                    "Keine Parameter geladen. Bitte zuerst einen EA auswählen und Parameter laden.",
                    "AutoConfig", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "AutoConfig überschreibt alle Optimization-Ranges\n" +
                "und aktiviert relevante Parameter für die Kennlinienfahrt.\n\n" +
                "Fortfahren?",
                "AutoConfig", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (confirm != JOptionPane.YES_OPTION) return;

        int activatedCount = 0;
        int skippedCount = 0;

        for (int i = 0; i < paramTableModel.getRowCount(); i++) {
            String name = (String) paramTableModel.getValueAt(i, 1);
            String value = String.valueOf(paramTableModel.getValueAt(i, 2)).trim();

            // Skip excluded parameters
            if (isExcludedParameterName(name)) {
                paramTableModel.setValueAt(false, i, 0); // Deactivate
                skippedCount++;
                continue;
            }

            // Skip non-numeric values
            if (!isNumericValue(value)) {
                paramTableModel.setValueAt(false, i, 0);
                skippedCount++;
                continue;
            }

            // Calculate optimization range
            double[] range = calculateOptRange(name, value);
            if (range == null) {
                paramTableModel.setValueAt(false, i, 0);
                skippedCount++;
                continue;
            }

            // Skip parameters with fewer than 5 steps - not useful for Kennlinie
            double steps = (range[2] - range[0]) / range[1];
            if (steps < 5) {
                paramTableModel.setValueAt(false, i, 0);
                skippedCount++;
                continue;
            }

            // Apply range: [0]=start, [1]=step, [2]=end
            paramTableModel.setValueAt(true, i, 0);  // Activate
            paramTableModel.setValueAt(formatNumber(range[0]), i, 3); // Start
            paramTableModel.setValueAt(formatNumber(range[1]), i, 4); // Step
            paramTableModel.setValueAt(formatNumber(range[2]), i, 5); // End
            activatedCount++;
        }

        logPanel.log("INFO", String.format("AutoConfig: %d Parameter aktiviert, %d übersprungen",
                activatedCount, skippedCount));

        paramTable.repaint();
    }

    /**
     * Checks if a parameter name should be excluded from AutoConfig.
     * Excludes trade sizing, meta, and identification parameters.
     */
    private boolean isExcludedParameterName(String name) {
        if (name == null || name.isEmpty()) return true;
        String lower = name.toLowerCase();

        // Trade sizing parameters - should not be optimized in Kennlinie
        String[] excludePatterns = {
                "lot", "volume", "risk", "money",
                "magic", "comment", "slippage",
                "color", "font", "show", "display", "visible", "alert",
                "button", "panel", "gui", "chart",
                "email", "notification", "push", "telegram",
                "license", "key", "account"
        };

        for (String pattern : excludePatterns) {
            if (lower.contains(pattern)) return true;
        }

        return false;
    }

    /**
     * Checks if a string value is numeric (integer or decimal).
     */
    private boolean isNumericValue(String value) {
        if (value == null || value.isEmpty()) return false;
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Calculates optimal optimization range [start, step, end] for a parameter.
     * Target: 10-100 data points for good characteristic curve resolution.
     *
     * @return double[3] = {start, step, end}, or null if not optimizable
     */
    private double[] calculateOptRange(String name, String valueStr) {
        double value;
        try {
            value = Double.parseDouble(valueStr);
        } catch (NumberFormatException e) {
            return null;
        }

        boolean isDecimal = valueStr.contains(".") && !valueStr.endsWith(".0");
        boolean isBoolean = (value == 0.0 || value == 1.0) && !isDecimal && !valueStr.contains(".");

        // Boolean parameter: just 0 and 1
        if (isBoolean && value <= 1.0) {
            return new double[]{0, 1, 1};
        }

        double start, end, step;

        if (isDecimal) {
            // Decimal parameter (e.g., 0.5, 1.5, 2.3)
            double absVal = Math.abs(value);
            if (absVal < 0.01) {
                start = 0.0;
                end = 1.0;
                step = 0.05;
            } else if (absVal <= 1.0) {
                start = 0.1;
                end = Math.max(value * 3, 2.0);
                step = roundStep((end - start) / 20.0, true);
            } else if (absVal <= 10.0) {
                start = 0.1;
                end = Math.max(value * 2, 10.0);
                step = roundStep((end - start) / 20.0, true);
            } else if (absVal <= 100.0) {
                start = 1.0;
                end = Math.max(value * 2, 100.0);
                step = roundStep((end - start) / 25.0, true);
            } else {
                start = 1.0;
                end = value * 2;
                step = roundStep((end - start) / 20.0, true);
            }
        } else {
            // Integer parameter
            long intVal = Math.abs(Math.round(value));

            if (intVal == 0) {
                start = 0;
                end = 100;
                step = 5;
            } else if (intVal <= 5) {
                start = 1;
                end = Math.max(intVal * 3, 10);
                step = 1;
            } else if (intVal <= 20) {
                start = 1;
                end = Math.max(intVal * 2, 20);
                step = 1;
            } else if (intVal <= 100) {
                start = 1;
                end = Math.max(intVal * 2, 100);
                step = roundStep((end - start) / 30.0, false);
                if (step < 1) step = 1;
            } else if (intVal <= 500) {
                start = 10;
                end = Math.max(intVal * 2, 500);
                step = roundStep((end - start) / 25.0, false);
                if (step < 1) step = 1;
            } else if (intVal <= 1000) {
                start = 10;
                end = Math.max(intVal * 2, 1000);
                step = roundStep((end - start) / 20.0, false);
            } else {
                start = 100;
                end = intVal * 2;
                step = roundStep((end - start) / 20.0, false);
            }
        }

        // Sanity checks
        if (step <= 0) step = 1;
        if (end <= start) end = start + step * 10;

        // Ensure we get between 10 and 100 data points
        double points = (end - start) / step;
        if (points > 100) {
            step = roundStep((end - start) / 50.0, isDecimal);
            if (!isDecimal && step < 1) step = 1;
        } else if (points < 10 && !isBoolean) {
            step = roundStep((end - start) / 20.0, isDecimal);
            if (!isDecimal && step < 1) step = 1;
        }

        return new double[]{start, step, end};
    }

    /**
     * Rounds a step value to a "nice" number.
     * For integers: rounds to nearest 1, 2, 5, 10, 20, 50, 100, etc.
     * For decimals: rounds to nearest 0.01, 0.02, 0.05, 0.1, 0.2, 0.5, etc.
     */
    private double roundStep(double rawStep, boolean isDecimal) {
        if (rawStep <= 0) return isDecimal ? 0.1 : 1;

        double magnitude = Math.pow(10, Math.floor(Math.log10(rawStep)));
        double normalized = rawStep / magnitude;

        double niceStep;
        if (normalized < 1.5) {
            niceStep = 1;
        } else if (normalized < 3.5) {
            niceStep = 2;
        } else if (normalized < 7.5) {
            niceStep = 5;
        } else {
            niceStep = 10;
        }

        double result = niceStep * magnitude;

        if (!isDecimal) {
            result = Math.max(1, Math.round(result));
        }

        return result;
    }

    /**
     * Formats a number for display - integers without decimal point, decimals with appropriate precision.
     */
    private String formatNumber(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        // Up to 4 decimal places, remove trailing zeros
        String formatted = String.format("%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
        return formatted;
    }
}
