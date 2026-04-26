package com.backtester.ui;

import com.backtester.config.AppConfig;
import com.backtester.config.EaParameter;
import com.backtester.config.EaParameterManager;
import com.backtester.engine.BacktestConfig;
import com.backtester.engine.OptimizationConfig;
import com.backtester.engine.RobustnessRunner;
import com.backtester.report.RobustnessHtmlGenerator;
import com.backtester.report.RobustnessResult;
import com.github.lgooddatepicker.components.DatePicker;
import com.github.lgooddatepicker.components.DatePickerSettings;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class RobustnessPanel extends JPanel {

    private final LogPanel logPanel;
    private final AppConfig config;
    private final EaParameterManager eaParamManager = new EaParameterManager();

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
    private JComboBox<String> metricCombo;
    private JSpinner shiftsSpinner;
    private JSpinner shiftDaysSpinner;

    private JTable paramTable;
    private DefaultTableModel paramTableModel;
    private JButton loadSetBtn;
    private JButton saveSetBtn;
    private JButton generateDefaultBtn;
    private JButton storeInDbBtn;
    private JButton getFromDbBtn;
    private JButton startBtn;
    private JButton cancelBtn;
    private JProgressBar progressBar;

    private RobustnessRunner currentRunner;
    private int activeScanRow = -1;
    
    private enum RowStatus { SUCCESS, ERROR, FLAT }
    private Map<String, RowStatus> rowStatusMap = new HashMap<>();

    public RobustnessPanel(LogPanel logPanel) {
        this.logPanel = logPanel;
        this.config = AppConfig.getInstance();

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel topPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        topPanel.add(createConfigPanel());
        topPanel.add(createParamPanel());

        JPanel controlPanel = createControlPanel();

        add(topPanel, BorderLayout.CENTER);
        add(controlPanel, BorderLayout.SOUTH);
        
        loadPreferences();
    }

    private JPanel createConfigPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createTitledBorder("Scan Settings"));

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
        form.add(configStatusLabel, gbc);

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

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel("Tick Model:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        modelCombo = new JComboBox<>(OptimizationConfig.MODEL_NAMES);
        modelCombo.setSelectedIndex(1);
        form.add(modelCombo, gbc);

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

        row++;
        gbc.gridx = 0; gbc.gridwidth = 2; gbc.gridy = row;
        form.add(new JSeparator(), gbc);
        gbc.gridwidth = 1;

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel("Plateau Metric:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        metricCombo = new JComboBox<>(new String[]{"Profit", "Profit Factor", "Expected Payoff", "Sharpe", "Drawdown"});
        metricCombo.setSelectedIndex(0);
        form.add(metricCombo, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel("Historical Shifts (n):"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        shiftsSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 20, 1));
        form.add(shiftsSpinner, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        form.add(new JLabel("Shift Step (Days):"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        shiftDaysSpinner = new JSpinner(new SpinnerNumberModel(90, 1, 365, 1));
        form.add(shiftDaysSpinner, gbc);

        JScrollPane scrollPane = new JScrollPane(form);
        scrollPane.setBorder(null);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createParamPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Select Parameters to Sweep"));

        String[] cols = {"Select", "Name", "Default", "Start", "Step", "End"};
        paramTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 0) return Boolean.class;
                return String.class;
            }
            @Override
            public boolean isCellEditable(int row, int column) {
                return column != 1;
            }
        };

        paramTable = new JTable(paramTableModel) {
            @Override
            public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                if (!isRowSelected(row)) {
                    String paramName = (String) paramTableModel.getValueAt(row, 1);
                    RowStatus status = rowStatusMap.getOrDefault(paramName, RowStatus.SUCCESS);
                    
                    if (row == activeScanRow) {
                        c.setBackground(new Color(60, 100, 160)); // Highlight active parameter
                        c.setForeground(Color.WHITE);
                    } else if (status == RowStatus.ERROR) {
                        c.setBackground(new Color(220, 130, 50)); // Error/Failed - Orange-Red
                        c.setForeground(Color.WHITE);
                    } else if (status == RowStatus.FLAT) {
                        c.setBackground(new Color(200, 160, 60)); // Flat line - Yellowish
                        c.setForeground(Color.WHITE);
                    } else {
                        c.setBackground(getBackground());
                        c.setForeground(getForeground());
                    }
                }
                return c;
            }
        };
        paramTable.getColumnModel().getColumn(0).setMaxWidth(50);
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
        
        JButton removeFailedBtn = new JButton("🗑 Remove Failed");
        removeFailedBtn.setToolTipText("Deselect parameters that caused an error or produced a flat curve");
        removeFailedBtn.setBackground(new Color(180, 80, 50));
        removeFailedBtn.setForeground(Color.WHITE);
        removeFailedBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        removeFailedBtn.addActionListener(e -> removeFailedParameters());
        leftBtnPanel.add(removeFailedBtn);
        
        JPanel rightBtnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        getFromDbBtn = new JButton("Get from DB");
        storeInDbBtn = new JButton("Store DB");
        loadSetBtn = new JButton("Load .set");
        saveSetBtn = new JButton("Save Config");
        generateDefaultBtn = new JButton("Generate Defaults");
        
        getFromDbBtn.addActionListener(e -> getFromDatabase());
        storeInDbBtn.addActionListener(e -> storeInDatabase());
        loadSetBtn.addActionListener(e -> loadParameters());
        saveSetBtn.addActionListener(e -> saveParameters());
        generateDefaultBtn.addActionListener(e -> generateDefaultParams());

        rightBtnPanel.add(generateDefaultBtn);
        rightBtnPanel.add(getFromDbBtn);
        rightBtnPanel.add(storeInDbBtn);
        rightBtnPanel.add(loadSetBtn);
        rightBtnPanel.add(saveSetBtn);
        
        btnPanel.add(leftBtnPanel, BorderLayout.WEST);
        btnPanel.add(rightBtnPanel, BorderLayout.EAST);
        panel.add(btnPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JPanel runPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        startBtn = new JButton("▶ Start Robustness Scan");
        startBtn.setBackground(new Color(60, 110, 180));
        startBtn.setForeground(Color.WHITE);
        startBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        startBtn.addActionListener(e -> startScan());

        cancelBtn = new JButton("⬛ Cancel");
        cancelBtn.setEnabled(false);
        cancelBtn.addActionListener(e -> {
            if (currentRunner != null) currentRunner.cancel();
        });

        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("Ready");
        progressBar.setPreferredSize(new Dimension(300, 24));

        runPanel.add(startBtn);
        runPanel.add(cancelBtn);
        runPanel.add(progressBar);

        panel.add(runPanel, BorderLayout.WEST);
        return panel;
    }

    private void browseExpert() {
        JFileChooser chooser = new JFileChooser(Paths.get(config.getMt5TerminalPath()).getParent().resolve("MQL5").resolve("Experts").toFile());
        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            Path expertsRoot = Paths.get(config.getMt5TerminalPath()).getParent().resolve("MQL5").resolve("Experts");
            Path selected = chooser.getSelectedFile().toPath();
            String relative = expertsRoot.relativize(selected).toString();
            if (relative.toLowerCase().endsWith(".ex5")) {
                relative = relative.substring(0, relative.length() - 4);
            }
            expertField.setText(relative);
            loadParameters();
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
        }
        updateDbButtonsColor();
    }

    private void updateDbButtonsColor() {
        if (storeInDbBtn == null || getFromDbBtn == null) return;
        String expert = expertField.getText().trim();
        if (expert.isEmpty()) return;
        
        List<com.backtester.database.EaDbConfig> configs = com.backtester.database.DatabaseManager.getInstance().getEaConfigsList(expert);
        if (configs != null && !configs.isEmpty()) {
            java.awt.Color green = new java.awt.Color(60, 140, 80);
            storeInDbBtn.setBackground(green);
            storeInDbBtn.setForeground(java.awt.Color.WHITE);
            getFromDbBtn.setBackground(green);
            getFromDbBtn.setForeground(java.awt.Color.WHITE);
        } else {
            storeInDbBtn.setBackground(javax.swing.UIManager.getColor("Button.background"));
            storeInDbBtn.setForeground(javax.swing.UIManager.getColor("Button.foreground"));
            getFromDbBtn.setBackground(javax.swing.UIManager.getColor("Button.background"));
            getFromDbBtn.setForeground(javax.swing.UIManager.getColor("Button.foreground"));
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

        if (paramTable.isEditing()) paramTable.getCellEditor().stopCellEditing();

        try {
            List<EaParameter> params = eaParamManager.getEffectiveParameters(expert);
            if (params == null) return;
            
            for (int i = 0; i < paramTableModel.getRowCount(); i++) {
                EaParameter p = params.get(i);
                p.setOptimizeEnabled((Boolean) paramTableModel.getValueAt(i, 0));
                p.setValue(String.valueOf(paramTableModel.getValueAt(i, 2))); // Default Value
                p.setOptimizeStart(String.valueOf(paramTableModel.getValueAt(i, 3)));
                p.setOptimizeStep(String.valueOf(paramTableModel.getValueAt(i, 4)));
                p.setOptimizeEnd(String.valueOf(paramTableModel.getValueAt(i, 5)));
            }

            eaParamManager.saveCustomParameters(expert, params);
            logPanel.log("INFO", "Saved config.");
        } catch (Exception e) {
            logPanel.log("ERROR", "Failed to save: " + e.getMessage());
        }
    }

    private void storeInDatabase() {
        String expert = expertField.getText().trim();
        if (expert.isEmpty()) return;

        if (paramTable.isEditing()) paramTable.getCellEditor().stopCellEditing();

        try {
            List<EaParameter> params = eaParamManager.getEffectiveParameters(expert);
            if (params == null) return;
            
            for (int i = 0; i < paramTableModel.getRowCount(); i++) {
                EaParameter p = params.get(i);
                p.setOptimizeEnabled((Boolean) paramTableModel.getValueAt(i, 0));
                p.setValue(String.valueOf(paramTableModel.getValueAt(i, 2))); // Default Value
                p.setOptimizeStart(String.valueOf(paramTableModel.getValueAt(i, 3)));
                p.setOptimizeStep(String.valueOf(paramTableModel.getValueAt(i, 4)));
                p.setOptimizeEnd(String.valueOf(paramTableModel.getValueAt(i, 5)));
            }

            boolean success = DbConfigSelectionDialog.showStoreDialog(javax.swing.SwingUtilities.getWindowAncestor(this), expert, params);
            if (success) {
                logPanel.log("INFO", "Stored DB configuration for " + expert);
                updateDbButtonsColor();
            }
        } catch (Exception e) {
            logPanel.log("ERROR", "Failed to store DB config: " + e.getMessage());
        }
    }

    private void getFromDatabase() {
        String expert = expertField.getText().trim();
        if (expert.isEmpty()) return;

        try {
            com.backtester.database.EaDbConfig selected = DbConfigSelectionDialog.showGetDialog(javax.swing.SwingUtilities.getWindowAncestor(this), expert);
            if (selected == null) return; // cancelled or none selected
            
            Gson gson = new Gson();
            java.lang.reflect.Type listType = new TypeToken<List<EaParameter>>(){}.getType();
            List<EaParameter> dbParams = gson.fromJson(selected.getParametersJson(), listType);

            if (dbParams != null && !dbParams.isEmpty()) {
                eaParamManager.saveCustomParameters(expert, dbParams);
                updateParamTable(dbParams);
                logPanel.log("INFO", "Loaded configuration '" + selected.getConfigName() + "' from DB");
                javax.swing.JOptionPane.showMessageDialog(this, "Loaded configuration: " + selected.getConfigName(), "Success", javax.swing.JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            logPanel.log("ERROR", "Failed to load DB config: " + e.getMessage());
        }
    }

    private void generateDefaultParams() {
        String expert = expertField.getText().trim();
        if (!expert.isEmpty()) {
            eaParamManager.generateDefaultConfig(expert);
            loadParameters();
        }
    }

    private void startScan() {
        rowStatusMap.clear();
        paramTable.repaint();
        savePreferences();
        saveParameters();

        String expert = expertField.getText().trim();
        if (expert.isEmpty()) return;

        OptimizationConfig optConfig = new OptimizationConfig();
        optConfig.setExpert(expert);
        optConfig.setSymbol(symbolCombo.getSelectedItem().toString());
        optConfig.setPeriod(periodCombo.getSelectedItem().toString());
        optConfig.setModel(modelCombo.getSelectedIndex());
        optConfig.setFromDate(fromDatePicker.getDate());
        optConfig.setToDate(toDatePicker.getDate());
        
        try { optConfig.setDeposit(Integer.parseInt(depositField.getText().trim())); } 
        catch (Exception e) { optConfig.setDeposit(10000); }
        optConfig.setCurrency(currencyField.getText().trim());
        optConfig.setLeverage(leverageField.getText().trim());
        optConfig.setUseLocal(true);

        List<EaParameter> params = eaParamManager.getEffectiveParameters(expert);
        if (params == null) return;

        startBtn.setEnabled(false);
        cancelBtn.setEnabled(true);
        progressBar.setIndeterminate(true);
        progressBar.setMaximum(100);
        progressBar.setString("Running Sweeps...");

        currentRunner = new RobustnessRunner(config);
        currentRunner.setLogCallback(msg -> {
            logPanel.log("ROBUST", msg);
            SwingUtilities.invokeLater(() -> progressBar.setString(msg));
        });
        currentRunner.setProgressCallback(percent -> {
            SwingUtilities.invokeLater(() -> {
                if (progressBar.isIndeterminate()) progressBar.setIndeterminate(false);
                progressBar.setValue(percent);
            });
        });
        String targetMetric = metricCombo.getSelectedItem().toString();
        int shifts = (Integer) shiftsSpinner.getValue();
        int shiftDays = (Integer) shiftDaysSpinner.getValue();

        currentRunner.setCurrentParamCallback(paramName -> {
            SwingUtilities.invokeLater(() -> setActiveScanParameter(paramName));
        });
        currentRunner.setParamFinishCallback((paramName, periods) -> {
            SwingUtilities.invokeLater(() -> updateRowStatus(paramName, periods, targetMetric));
        });

        new SwingWorker<RobustnessResult, Void>() {
            @Override
            protected RobustnessResult doInBackground() {
                return currentRunner.runRobustnessScan(optConfig, params, shifts, shiftDays);
            }

            @Override
            protected void done() {
                startBtn.setEnabled(true);
                cancelBtn.setEnabled(false);
                progressBar.setIndeterminate(false);
                progressBar.setValue(100);
                progressBar.setString("Finished");
                SwingUtilities.invokeLater(() -> setActiveScanParameter(null));
                
                try {
                    RobustnessResult res = get();
                    if (res != null) {
                        for (int i=0; i<paramTableModel.getRowCount(); i++) {
                            if ((Boolean)paramTableModel.getValueAt(i, 0)) {
                                String pName = (String)paramTableModel.getValueAt(i, 1);
                                if (!rowStatusMap.containsKey(pName)) rowStatusMap.put(pName, RowStatus.ERROR);
                            }
                        }
                        paramTable.repaint();
                    }

                    if (res != null && res.isSuccess()) {
                        RobustnessHtmlGenerator.generateReport(res, optConfig, targetMetric, targetMetric, params);
                        Path reportPath = java.nio.file.Paths.get(res.getOutputDirectory(), "robustness_report.html");
                        try {
                            com.google.gson.JsonObject metrics = new com.google.gson.JsonObject();
                            metrics.addProperty("targetMetric", targetMetric);
                            metrics.addProperty("shifts", shifts);
                            metrics.addProperty("shiftDays", shiftDays);
                            com.backtester.database.DatabaseManager.getInstance().saveRun(
                                "ROBUSTNESS", 
                                optConfig.getExpert(), 
                                System.currentTimeMillis(), 
                                metrics.toString(), 
                                reportPath.toAbsolutePath().toString()
                            );
                        } catch (Exception ex) {
                            logPanel.log("ERROR", "Failed to save robustness to DB");
                        }
                        Desktop.getDesktop().open(reportPath.toFile());
                    } else if (res != null) {
                        JOptionPane.showMessageDialog(RobustnessPanel.this, res.getMessage(), "Warning", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception ex) {
                    logPanel.log("ERROR", "Robustness worker failed: " + ex.getMessage());
                }
            }
        }.execute();
    }

    // ==================== AutoConfig Logic ====================

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

            if (isExcludedParameterName(name)) {
                paramTableModel.setValueAt(false, i, 0);
                skippedCount++;
                continue;
            }

            if (!isNumericValue(value)) {
                paramTableModel.setValueAt(false, i, 0);
                skippedCount++;
                continue;
            }

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

            paramTableModel.setValueAt(true, i, 0);
            paramTableModel.setValueAt(formatNumber(range[0]), i, 3);
            paramTableModel.setValueAt(formatNumber(range[1]), i, 4);
            paramTableModel.setValueAt(formatNumber(range[2]), i, 5);
            activatedCount++;
        }

        logPanel.log("INFO", String.format("AutoConfig: %d Parameter aktiviert, %d übersprungen",
                activatedCount, skippedCount));
        paramTable.repaint();
    }

    private boolean isExcludedParameterName(String name) {
        if (name == null || name.isEmpty()) return true;
        String lower = name.toLowerCase();
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

    private boolean isNumericValue(String value) {
        if (value == null || value.isEmpty()) return false;
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private double[] calculateOptRange(String name, String valueStr) {
        double value;
        try {
            value = Double.parseDouble(valueStr);
        } catch (NumberFormatException e) {
            return null;
        }

        boolean isDecimal = valueStr.contains(".") && !valueStr.endsWith(".0");
        boolean isBoolean = (value == 0.0 || value == 1.0) && !isDecimal && !valueStr.contains(".");

        if (isBoolean && value <= 1.0) {
            return new double[]{0, 1, 1};
        }

        double start, end, step;

        if (isDecimal) {
            double absVal = Math.abs(value);
            if (absVal < 0.01) {
                start = 0.0; end = 1.0; step = 0.05;
            } else if (absVal <= 1.0) {
                start = 0.1; end = Math.max(value * 3, 2.0);
                step = roundStep((end - start) / 20.0, true);
            } else if (absVal <= 10.0) {
                start = 0.1; end = Math.max(value * 2, 10.0);
                step = roundStep((end - start) / 20.0, true);
            } else if (absVal <= 100.0) {
                start = 1.0; end = Math.max(value * 2, 100.0);
                step = roundStep((end - start) / 25.0, true);
            } else {
                start = 1.0; end = value * 2;
                step = roundStep((end - start) / 20.0, true);
            }
        } else {
            long intVal = Math.abs(Math.round(value));
            if (intVal == 0) {
                start = 0; end = 100; step = 5;
            } else if (intVal <= 5) {
                start = 1; end = Math.max(intVal * 3, 10); step = 1;
            } else if (intVal <= 20) {
                start = 1; end = Math.max(intVal * 2, 20); step = 1;
            } else if (intVal <= 100) {
                start = 1; end = Math.max(intVal * 2, 100);
                step = roundStep((end - start) / 30.0, false);
                if (step < 1) step = 1;
            } else if (intVal <= 500) {
                start = 10; end = Math.max(intVal * 2, 500);
                step = roundStep((end - start) / 25.0, false);
                if (step < 1) step = 1;
            } else if (intVal <= 1000) {
                start = 10; end = Math.max(intVal * 2, 1000);
                step = roundStep((end - start) / 20.0, false);
            } else {
                start = 100; end = intVal * 2;
                step = roundStep((end - start) / 20.0, false);
            }
        }

        if (step <= 0) step = 1;
        if (end <= start) end = start + step * 10;

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

    private double roundStep(double rawStep, boolean isDecimal) {
        if (rawStep <= 0) return isDecimal ? 0.1 : 1;
        double magnitude = Math.pow(10, Math.floor(Math.log10(rawStep)));
        double normalized = rawStep / magnitude;
        double niceStep;
        if (normalized < 1.5) niceStep = 1;
        else if (normalized < 3.5) niceStep = 2;
        else if (normalized < 7.5) niceStep = 5;
        else niceStep = 10;
        double result = niceStep * magnitude;
        if (!isDecimal) result = Math.max(1, Math.round(result));
        return result;
    }

    private void updateRowStatus(String paramName, Map<String, com.backtester.report.OptimizationResult> periods, String targetMetric) {
        boolean hasData = false;
        boolean isFlat = true;
        
        for (com.backtester.report.OptimizationResult optRes : periods.values()) {
            if (optRes == null || !optRes.isSuccess() || optRes.getPasses().isEmpty()) continue;
            hasData = true;
            
            double paramMin = Double.MAX_VALUE;
            double paramMax = -Double.MAX_VALUE;
            
            for (com.backtester.report.OptimizationResult.Pass pass : optRes.getPasses()) {
                double val = getMetricValue(pass, targetMetric);
                if (val < paramMin) paramMin = val;
                if (val > paramMax) paramMax = val;
            }
            
            double diff = paramMax - paramMin;
            double maxAbs = Math.max(Math.abs(paramMax), Math.abs(paramMin));
            
            if (diff > 0.01 && (maxAbs == 0 || diff / maxAbs > 0.001)) {
                isFlat = false;
                break;
            }
        }
        
        if (!hasData) {
            rowStatusMap.put(paramName, RowStatus.ERROR);
        } else if (isFlat) {
            rowStatusMap.put(paramName, RowStatus.FLAT);
        } else {
            rowStatusMap.put(paramName, RowStatus.SUCCESS);
        }
        paramTable.repaint();
    }

    private void removeFailedParameters() {
        boolean changed = false;
        for (int i = 0; i < paramTableModel.getRowCount(); i++) {
            String pName = (String) paramTableModel.getValueAt(i, 1);
            RowStatus status = rowStatusMap.getOrDefault(pName, RowStatus.SUCCESS);
            if (status == RowStatus.ERROR || status == RowStatus.FLAT) {
                if ((Boolean) paramTableModel.getValueAt(i, 0)) {
                    paramTableModel.setValueAt(false, i, 0); // Deselect
                    changed = true;
                }
            }
        }
        if (changed) {
            paramTable.repaint();
            logPanel.log("INFO", "Removed failed/flat parameters from selection.");
        }
    }

    private String formatNumber(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.format("%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private double getMetricValue(com.backtester.report.OptimizationResult.Pass pass, String metric) {
        switch (metric) {
            case "Profit": return pass.getProfit();
            case "Profit Factor": return pass.getProfitFactor();
            case "Expected Payoff": return pass.getExpectedPayoff();
            case "Sharpe": return pass.getSharpeRatio();
            case "Drawdown": return pass.getDrawdownPercent();
            default: return pass.getProfit();
        }
    }

    private void setActiveScanParameter(String paramName) {
        if (paramName == null) {
            activeScanRow = -1;
            paramTable.repaint();
            return;
        }
        for (int i = 0; i < paramTableModel.getRowCount(); i++) {
            if (paramName.equals(paramTableModel.getValueAt(i, 1))) {
                activeScanRow = i;
                paramTable.repaint();
                // Scroll the table to make the active row visible
                Rectangle rect = paramTable.getCellRect(i, 0, true);
                if (rect != null) {
                    paramTable.scrollRectToVisible(rect);
                }
                break;
            }
        }
    }

    // ==================== Preferences Logic ====================

    private void savePreferences() {
        config.set("robustness.expert", expertField.getText().trim());
        if (symbolCombo.getSelectedItem() != null) config.set("robustness.symbol", symbolCombo.getSelectedItem().toString());
        if (periodCombo.getSelectedItem() != null) config.set("robustness.period", periodCombo.getSelectedItem().toString());
        config.set("robustness.model", String.valueOf(modelCombo.getSelectedIndex()));
        
        if (fromDatePicker.getDate() != null) config.set("robustness.dateFrom", fromDatePicker.getDate().toString());
        if (toDatePicker.getDate() != null) config.set("robustness.dateTo", toDatePicker.getDate().toString());
        
        config.set("robustness.deposit", depositField.getText().trim());
        config.set("robustness.currency", currencyField.getText().trim());
        config.set("robustness.leverage", leverageField.getText().trim());
        
        config.set("robustness.metric", String.valueOf(metricCombo.getSelectedIndex()));
        config.set("robustness.shifts", String.valueOf(shiftsSpinner.getValue()));
        config.set("robustness.shiftdays", String.valueOf(shiftDaysSpinner.getValue()));
        
        config.save();
    }

    private void loadPreferences() {
        String exp = config.get("robustness.expert", "");
        if (!exp.isEmpty()) {
            expertField.setText(exp);
            loadParameters();
        }
        
        String sym = config.get("robustness.symbol", "");
        if (!sym.isEmpty()) symbolCombo.setSelectedItem(sym);
        
        String per = config.get("robustness.period", "");
        if (!per.isEmpty()) periodCombo.setSelectedItem(per);
        
        int mod = config.getInt("robustness.model", 1);
        if (mod >= 0 && mod < modelCombo.getItemCount()) modelCombo.setSelectedIndex(mod);
        
        String dFrom = config.get("robustness.dateFrom", "");
        if (!dFrom.isEmpty()) {
            try { fromDatePicker.setDate(LocalDate.parse(dFrom)); } catch (Exception ignored) {}
        }
        
        String dTo = config.get("robustness.dateTo", "");
        if (!dTo.isEmpty()) {
            try { toDatePicker.setDate(LocalDate.parse(dTo)); } catch (Exception ignored) {}
        }
        
        String dep = config.get("robustness.deposit", "10000");
        depositField.setText(dep);
        
        String cur = config.get("robustness.currency", "USD");
        currencyField.setText(cur);
        
        String lev = config.get("robustness.leverage", "1:100");
        leverageField.setText(lev);
        
        int met = config.getInt("robustness.metric", 0);
        if (met >= 0 && met < metricCombo.getItemCount()) metricCombo.setSelectedIndex(met);
        
        int sh = config.getInt("robustness.shifts", 0);
        shiftsSpinner.setValue(sh);
        
        int shd = config.getInt("robustness.shiftdays", 90);
        shiftDaysSpinner.setValue(shd);
    }
}
