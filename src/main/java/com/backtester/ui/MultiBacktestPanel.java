package com.backtester.ui;

import com.backtester.config.AppConfig;
import com.backtester.config.EaParameterManager;
import com.backtester.engine.BacktestConfig;
import com.backtester.engine.MultiBacktestConfig;
import com.backtester.engine.MultiBacktestRunner;
import com.backtester.report.BacktestResult;
import com.github.lgooddatepicker.components.DatePicker;
import com.github.lgooddatepicker.components.DatePickerSettings;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Multi-Backtester configuration and execution panel.
 * Master-Detail View implementation for accumulated batch runs.
 */
public class MultiBacktestPanel extends JPanel {

    private final LogPanel logPanel;
    private final AppConfig config;
    private final EaParameterManager eaParamManager = new EaParameterManager();

    // Inputs: Experts
    private DefaultListModel<String> expertsModel;
    private JList<String> expertsList;

    // Inputs: Symbols & Timeframes
    private List<JCheckBox> symbolChecks = new ArrayList<>();
    private List<JCheckBox> periodChecks = new ArrayList<>();
    private JTextField customSymbolField;

    private JComboBox<String> modelCombo;
    private DatePicker fromDatePicker;
    private DatePicker toDatePicker;
    private JSpinner depositSpinner;

    // Action buttons
    private JButton startButton;
    private JButton cancelButton;
    private JProgressBar progressBar;

    // Results Master-Detail
    private static class BatchRunModel {
        String name;
        Path reportPath;
        List<BacktestResult> results = new ArrayList<>();
        @Override
        public String toString() { return name; }
    }

    private DefaultListModel<BatchRunModel> batchListModel;
    private JList<BatchRunModel> batchList;

    private DefaultTableModel resultsTableModel;
    private JTable resultsTable;

    private MultiBacktestRunner currentRunner;

    public MultiBacktestPanel(LogPanel logPanel) {
        this.logPanel = logPanel;
        this.config = AppConfig.getInstance();

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel configPanel = createConfigPanel();
        JPanel resultsPanel = createResultsPanel();

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, configPanel, resultsPanel);
        splitPane.setDividerLocation(360);
        splitPane.setResizeWeight(0.2);

        add(splitPane, BorderLayout.CENTER);
    }

    private JPanel createConfigPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        JLabel title = new JLabel("Multi-Backtester Configuration");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(new Color(241, 154, 78));
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 0.5;

        // 1. Experts Panel
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.4;
        form.add(createExpertsSection(), gbc);

        // 2. Symbols Panel
        gbc.gridx = 1; gbc.weightx = 0.3;
        form.add(createSymbolsSection(), gbc);

        // 3. Timeframes Panel
        gbc.gridx = 2; gbc.weightx = 0.3;
        form.add(createTimeframesSection(), gbc);

        // Common settings
        gbc.weighty = 0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.gridwidth = 3;
        JPanel commonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 5));
        
        JPanel modelP = new JPanel(new BorderLayout(5, 5));
        modelP.add(createLabel("Tick Model:"), BorderLayout.NORTH);
        modelCombo = new JComboBox<>(BacktestConfig.MODEL_NAMES);
        modelP.add(modelCombo, BorderLayout.CENTER);
        commonPanel.add(modelP);

        JPanel fromP = new JPanel(new BorderLayout(5, 5));
        fromP.add(createLabel("From:"), BorderLayout.NORTH);
        DatePickerSettings fs = new DatePickerSettings();
        fs.setFormatForDatesCommonEra("yyyy-MM-dd");
        fromDatePicker = new DatePicker(fs);
        fromDatePicker.setDate(LocalDate.now().minusYears(1));
        fromP.add(fromDatePicker, BorderLayout.CENTER);
        commonPanel.add(fromP);

        JPanel toP = new JPanel(new BorderLayout(5, 5));
        toP.add(createLabel("To:"), BorderLayout.NORTH);
        DatePickerSettings ts = new DatePickerSettings();
        ts.setFormatForDatesCommonEra("yyyy-MM-dd");
        toDatePicker = new DatePicker(ts);
        toDatePicker.setDate(LocalDate.now());
        toP.add(toDatePicker, BorderLayout.CENTER);
        commonPanel.add(toP);

        JPanel depP = new JPanel(new BorderLayout(5, 5));
        depP.add(createLabel("Deposit:"), BorderLayout.NORTH);
        depositSpinner = new JSpinner(new SpinnerNumberModel(config.getDefaultDeposit(), 100, 10000000, 1000));
        depP.add(depositSpinner, BorderLayout.CENTER);
        commonPanel.add(depP);

        gbc.gridx = 0; gbc.gridy = 1;
        form.add(commonPanel, gbc);

        // Buttons
        gbc.gridy = 2;
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        
        startButton = new JButton("▶ Start Batch");
        startButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        startButton.setBackground(new Color(200, 100, 40));
        startButton.setForeground(Color.WHITE);
        startButton.setPreferredSize(new Dimension(160, 38));
        startButton.addActionListener(this::startMultiTest);

        cancelButton = new JButton("■ Cancel");
        cancelButton.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        cancelButton.setPreferredSize(new Dimension(100, 38));
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(e -> {
            if (currentRunner != null) currentRunner.cancelBatch();
        });

        buttonPanel.add(startButton);
        buttonPanel.add(cancelButton);
        form.add(buttonPanel, gbc);

        // Progress
        gbc.gridy = 3;
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("Ready");
        form.add(progressBar, gbc);

        panel.add(title, BorderLayout.NORTH);
        panel.add(form, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createExpertsSection() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.add(createLabel("Selected Expert Advisors (Robots)"), BorderLayout.NORTH);
        
        expertsModel = new DefaultListModel<>();
        expertsList = new JList<>(expertsModel);
        expertsList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        p.add(new JScrollPane(expertsList), BorderLayout.CENTER);

        JPanel btnP = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JButton addBtn = new JButton("📁 Add EA...");
        addBtn.addActionListener(e -> browseExpert());
        JButton rmvBtn = new JButton("❌ Remove");
        rmvBtn.addActionListener(e -> {
            int[] sel = expertsList.getSelectedIndices();
            for (int i = sel.length - 1; i >= 0; i--) {
                expertsModel.remove(sel[i]);
            }
        });
        JButton cfgBtn = new JButton("⚙ Config");
        cfgBtn.setToolTipText("Edit input parameters for selected EA");
        cfgBtn.addActionListener(e -> {
            String selectedEa = expertsList.getSelectedValue();
            if (selectedEa != null) {
                EaConfigDialog.showForExpert(SwingUtilities.getWindowAncestor(this), selectedEa);
            } else {
                JOptionPane.showMessageDialog(this, "Please select an EA first.", "No Selection", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        btnP.add(addBtn);
        btnP.add(rmvBtn);
        btnP.add(cfgBtn);
        p.add(btnP, BorderLayout.SOUTH);

        return p;
    }

    private JPanel createSymbolsSection() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.add(createLabel("Select Symbols"), BorderLayout.NORTH);
        
        JPanel checkPanel = new JPanel(new GridLayout(0, 2, 5, 2));
        for (String sym : BacktestConfig.SYMBOLS) {
            JCheckBox cb = new JCheckBox(sym);
            if (sym.equals("EURUSD")) cb.setSelected(true);
            symbolChecks.add(cb);
            checkPanel.add(cb);
        }
        p.add(new JScrollPane(checkPanel), BorderLayout.CENTER);

        JPanel customP = new JPanel(new BorderLayout(5, 0));
        customSymbolField = new JTextField();
        JButton addCustomBtn = new JButton("Add Custom");
        addCustomBtn.addActionListener(e -> {
            String val = customSymbolField.getText().trim();
            if (!val.isEmpty()) {
                JCheckBox cb = new JCheckBox(val, true);
                symbolChecks.add(cb);
                checkPanel.add(cb);
                customSymbolField.setText("");
                checkPanel.revalidate();
                checkPanel.repaint();
            }
        });
        customP.add(customSymbolField, BorderLayout.CENTER);
        customP.add(addCustomBtn, BorderLayout.EAST);
        p.add(customP, BorderLayout.SOUTH);

        return p;
    }

    private JPanel createTimeframesSection() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.add(createLabel("Select Timeframes"), BorderLayout.NORTH);
        
        JPanel checkPanel = new JPanel(new GridLayout(0, 2, 5, 2));
        for (String tf : BacktestConfig.TIMEFRAMES) {
            JCheckBox cb = new JCheckBox(tf);
            if (tf.equals("H1")) cb.setSelected(true);
            periodChecks.add(cb);
            checkPanel.add(cb);
        }
        p.add(new JScrollPane(checkPanel), BorderLayout.CENTER);
        return p;
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
        chooser.setMultiSelectionEnabled(true);

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File[] selectedFiles = chooser.getSelectedFiles();
            for (File selected : selectedFiles) {
                if (expertsDir != null && selected.toPath().startsWith(expertsDir)) {
                    String relative = expertsDir.relativize(selected.toPath()).toString();
                    if (relative.toLowerCase().endsWith(".ex5")) {
                        relative = relative.substring(0, relative.length() - 4);
                    }
                    if (!expertsModel.contains(relative)) expertsModel.addElement(relative);
                } else {
                    if (!expertsModel.contains(selected.getAbsolutePath())) expertsModel.addElement(selected.getAbsolutePath());
                }
            }
        }
    }

    private JPanel createResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        
        // ---- Master View (List of Batches) ----
        batchListModel = new DefaultListModel<>();
        batchList = new JList<>(batchListModel);
        batchList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        batchList.setFont(new Font("Segoe UI", Font.BOLD, 12));
        
        JPanel masterPanel = new JPanel(new BorderLayout());
        masterPanel.add(new JLabel(" Batch History"), BorderLayout.NORTH);
        masterPanel.add(new JScrollPane(batchList), BorderLayout.CENTER);
        
        JButton deleteBatchBtn = new JButton("🗑 Delete Batch");
        deleteBatchBtn.addActionListener(e -> deleteSelectedBatches());
        
        JButton openMultiReportBtn = new JButton("🌐 Open Multi-Report Node");
        openMultiReportBtn.addActionListener(e -> openSelectedMultiReport());
        
        JPanel batchBtnP = new JPanel(new GridLayout(2,1));
        batchBtnP.add(openMultiReportBtn);
        batchBtnP.add(deleteBatchBtn);
        masterPanel.add(batchBtnP, BorderLayout.SOUTH);

        // ---- Detail View (Table of Runs) ----
        String[] cols = {"Comb.", "Robot", "Symbol", "Period", "Trades", "Win Rate", "Drawdown", "Profit", "Status", "Config", "Directory", "ResultObject"};
        resultsTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        resultsTable = new JTable(resultsTableModel);
        resultsTable.setRowHeight(24);
        resultsTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        resultsTable.setAutoCreateRowSorter(true);
        
        // Hide directory and result object columns (indices 10,11 now)
        resultsTable.getColumnModel().getColumn(10).setMinWidth(0);
        resultsTable.getColumnModel().getColumn(10).setMaxWidth(0);
        resultsTable.getColumnModel().getColumn(10).setWidth(0);
        resultsTable.getColumnModel().getColumn(11).setMinWidth(0);
        resultsTable.getColumnModel().getColumn(11).setMaxWidth(0);
        resultsTable.getColumnModel().getColumn(11).setWidth(0);

        resultsTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) showSelectedSingleReport();
            }
        });

        JPanel detailPanel = new JPanel(new BorderLayout());
        detailPanel.add(new JLabel(" Runs for Selected Batch"), BorderLayout.NORTH);
        detailPanel.add(new JScrollPane(resultsTable), BorderLayout.CENTER);

        JPanel detailBtnP = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        JButton showSingleBtn = new JButton("📊 Show Single Report");
        showSingleBtn.addActionListener(e -> showSelectedSingleReport());
        JButton deleteRunBtn = new JButton("🗑 Delete Selected Runs");
        deleteRunBtn.addActionListener(e -> deleteSelectedRuns());
        
        detailBtnP.add(showSingleBtn);
        detailBtnP.add(deleteRunBtn);
        detailPanel.add(detailBtnP, BorderLayout.SOUTH);

        // Connect Master to Detail
        batchList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                refreshDetailTable();
            }
        });

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, masterPanel, detailPanel);
        splitPane.setDividerLocation(180);
        panel.add(splitPane, BorderLayout.CENTER);

        return panel;
    }

    private void refreshDetailTable() {
        resultsTableModel.setRowCount(0);
        BatchRunModel selected = batchList.getSelectedValue();
        if (selected == null) return;
        
        int idx = 1;
        for (BacktestResult result : selected.results) {
            resultsTableModel.addRow(new Object[]{
                idx++,
                result.getExpert(),
                result.getSymbol(),
                result.getPeriod(),
                result.getTotalTrades(),
                String.format("%.1f%%", result.getWinRate()),
                String.format("%.2f%%", result.getMaxDrawdown()),
                String.format("%.2f", result.getTotalProfit()),
                result.isSuccess() ? "OK" : "FAIL",
                result.getConfigInfo(),
                result.getOutputDirectory(),
                result
            });
        }
    }

    private void showSelectedSingleReport() {
        int r = resultsTable.getSelectedRow();
        if (r >= 0) {
            String dirStr = (String) resultsTableModel.getValueAt(resultsTable.convertRowIndexToModel(r), 10);
            if (dirStr != null && !dirStr.isEmpty()) {
                File dir = new File(dirStr);
                if (dir.exists()) {
                    ReportViewerDialog.showForDirectory(SwingUtilities.getWindowAncestor(this), dir.getAbsolutePath());
                } else {
                    JOptionPane.showMessageDialog(this, "Directory no longer exists.", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    private void deleteSelectedBatches() {
        List<BatchRunModel> selected = batchList.getSelectedValuesList();
        if (selected.isEmpty()) return;
        
        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete " + selected.size() + " batch run(s)?\nThis will permanently delete all enclosed directories.",
                "Confirm Batch Deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            for (BatchRunModel batch : selected) {
                // Delete multi report file if exists
                if (batch.reportPath != null && Files.exists(batch.reportPath)) {
                    try { Files.delete(batch.reportPath); } catch (Exception ignored) {}
                }
                // Delete all individual runs
                for (BacktestResult res : batch.results) {
                    deleteDirectory(res.getOutputDirectory());
                }
                batchListModel.removeElement(batch);
            }
            resultsTableModel.setRowCount(0);
        }
    }

    private void deleteSelectedRuns() {
        int[] rows = resultsTable.getSelectedRows();
        if (rows.length == 0) return;
        
        BatchRunModel activeBatch = batchList.getSelectedValue();
        if (activeBatch == null) return;

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete the " + rows.length + " selected test run(s)?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            java.util.Arrays.sort(rows);
            for (int i = rows.length - 1; i >= 0; i--) {
                int modelRow = resultsTable.convertRowIndexToModel(rows[i]);
                String dir = (String) resultsTableModel.getValueAt(modelRow, 10);
                BacktestResult res = (BacktestResult) resultsTableModel.getValueAt(modelRow, 11);
                
                deleteDirectory(dir);
                
                activeBatch.results.remove(res);
                resultsTableModel.removeRow(modelRow);
            }
        }
    }

    private void openSelectedMultiReport() {
        BatchRunModel selected = batchList.getSelectedValue();
        if (selected != null && selected.reportPath != null && Files.exists(selected.reportPath)) {
            try { Desktop.getDesktop().browse(selected.reportPath.toUri()); } 
            catch (Exception ex) { ex.printStackTrace(); }
        } else {
            JOptionPane.showMessageDialog(this, "No valid Multi-Report generated for this node yet.", "Info", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void deleteDirectory(String dirPathStr) {
        if (dirPathStr == null || dirPathStr.isEmpty()) return;
        try {
            Path dirPath = Paths.get(dirPathStr);
            if (Files.exists(dirPath)) {
                try (var files = Files.walk(dirPath)) {
                    files.sorted(java.util.Comparator.reverseOrder())
                         .map(Path::toFile)
                         .forEach(File::delete);
                }
                logPanel.log("INFO", "Deleted: " + dirPathStr);
            }
        } catch (Exception e) {
            logPanel.log("ERROR", "Failed to delete: " + e.getMessage());
        }
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 12));
        return label;
    }

    private void startMultiTest(ActionEvent e) {
        List<String> expList = new ArrayList<>();
        for (int i=0; i<expertsModel.getSize(); i++) expList.add(expertsModel.getElementAt(i));
        
        List<String> symList = new ArrayList<>();
        for (JCheckBox cb : symbolChecks) {
            if (cb.isSelected()) symList.add(cb.getText());
        }
        
        List<String> perList = new ArrayList<>();
        for (JCheckBox cb : periodChecks) {
            if (cb.isSelected()) perList.add(cb.getText());
        }

        if (expList.isEmpty() || symList.isEmpty() || perList.isEmpty()) {
            JOptionPane.showMessageDialog(this, "You must provide at least one item in each list (Experts, Symbols, Timeframes).");
            return;
        }

        MultiBacktestConfig conf = new MultiBacktestConfig();
        conf.setExperts(expList);
        conf.setSymbols(symList);
        conf.setPeriods(perList);
        conf.setModel(modelCombo.getSelectedIndex());
        conf.setFromDate(fromDatePicker.getDate());
        conf.setToDate(toDatePicker.getDate());
        conf.setDeposit((Integer) depositSpinner.getValue());

        // Prepare EA parameters for each expert
        for (String expert : expList) {
            String setFileName = eaParamManager.prepareForBacktest(expert);
            if (setFileName != null) {
                conf.setExpertParameters(expert, setFileName);
                boolean isCustom = eaParamManager.hasCustomConfig(expert);
                logPanel.log("INFO", "EA Config [" + EaParameterManager.extractEaBaseName(expert) + "]: Using " + 
                    (isCustom ? "CUSTOM" : "DEFAULT") + " parameters");
            } else {
                logPanel.log("INFO", "EA Config [" + EaParameterManager.extractEaBaseName(expert) + "]: No .set file - using compiled defaults");
            }
        }

        // Create new Node in Tree / List
        BatchRunModel newBatch = new BatchRunModel();
        newBatch.name = "▶ Batch " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " ("+conf.getTotalCombinations()+" Tasks)";
        batchListModel.addElement(newBatch);
        batchList.setSelectedValue(newBatch, true);

        startButton.setEnabled(false);
        cancelButton.setEnabled(true);

        progressBar.setString("Running 0 / " + conf.getTotalCombinations());
        progressBar.setValue(0);

        final int[] count = {0};
        final int total = conf.getTotalCombinations();

        currentRunner = new MultiBacktestRunner(conf, 
            msg -> {}, 
            pct -> {
                progressBar.setValue(pct);
                progressBar.setString("Progress " + pct + "%");
            },
            result -> {
                count[0]++;
                newBatch.results.add(result);
                progressBar.setString("Running " + count[0] + " / " + total);
                
                // Only immediately add to table if this batch is currently selected
                if (batchList.getSelectedValue() == newBatch) {
                    resultsTableModel.addRow(new Object[]{
                        newBatch.results.size(),
                        result.getExpert(),
                        result.getSymbol(),
                        result.getPeriod(),
                        result.getTotalTrades(),
                        String.format("%.1f%%", result.getWinRate()),
                        String.format("%.2f%%", result.getMaxDrawdown()),
                        String.format("%.2f", result.getTotalProfit()),
                        result.isSuccess() ? "OK" : "FAIL",
                        result.getConfigInfo(),
                        result.getOutputDirectory(),
                        result
                    });
                }
            }
        ) {
            @Override
            protected void done() {
                try { get(); } catch (Exception ex) { ex.printStackTrace(); }
                finally {
                    startButton.setEnabled(true);
                    cancelButton.setEnabled(false);
                    progressBar.setString("Batch finished");
                    newBatch.reportPath = currentRunner.getGeneratedReportPath();
                    batchList.revalidate();
                    batchList.repaint();
                    
                    if (newBatch.reportPath != null) {
                        try { Desktop.getDesktop().browse(newBatch.reportPath.toUri()); } 
                        catch (Exception ex) { ex.printStackTrace(); }
                    }
                }
            }
        };

        currentRunner.execute();
    }
}
