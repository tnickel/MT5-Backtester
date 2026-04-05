package com.backtester.ui;

import com.backtester.config.AppConfig;
import com.backtester.dukascopy.Bi5Decoder;
import com.backtester.dukascopy.CsvConverter;
import com.backtester.dukascopy.DukascopyDownloader;
import com.backtester.engine.BacktestConfig;
import com.backtester.mt5.CustomSymbolManager;
import com.backtester.mt5.Mt5DataImporter;
import com.github.lgooddatepicker.components.DatePicker;
import com.github.lgooddatepicker.components.DatePickerSettings;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.List;

/**
 * Panel for downloading Dukascopy data and importing into MT5.
 * 
 * Two sections:
 * 1. Download — select symbols and date range, download .bi5 files
 * 2. Import to MT5 — convert to CSV and create Custom Symbols
 */
public class DukascopyPanel extends JPanel {

    private final LogPanel logPanel;
    private final AppConfig config;
    private final CustomSymbolManager symbolManager;

    // Download section
    private JPanel symbolCheckboxPanel;
    private final Map<String, JCheckBox> symbolCheckboxes = new LinkedHashMap<>();
    private DatePicker downloadFromDate;
    private DatePicker downloadToDate;
    private JButton downloadButton;
    private JButton cancelDownloadButton;
    private JProgressBar downloadProgress;

    // Import section
    private DefaultTableModel dataTableModel;
    private JTable dataTable;
    private JButton convertButton;
    private JButton importButton;
    private JProgressBar importProgress;

    // Workers
    private SwingWorker<?, ?> currentDownloadWorker;
    private DukascopyDownloader currentDownloader;

    public DukascopyPanel(LogPanel logPanel) {
        this.logPanel = logPanel;
        this.config = AppConfig.getInstance();
        this.symbolManager = new CustomSymbolManager(config.getBasePath().resolve("config"));

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Split into download (top) and import (bottom)
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                createDownloadPanel(), createImportPanel());
        splitPane.setDividerLocation(350);
        splitPane.setResizeWeight(0.45);

        add(splitPane, BorderLayout.CENTER);
    }

    private JPanel createDownloadPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Title
        JLabel title = new JLabel("Step 1: Download Dukascopy Data");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(new Color(78, 154, 241));

        // Symbol selection grid
        JPanel symbolSection = new JPanel(new BorderLayout(5, 5));
        JLabel symbolLabel = new JLabel("Select Currency Pairs:");
        symbolLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

        symbolCheckboxPanel = new JPanel(new GridLayout(0, 4, 8, 4));
        symbolCheckboxPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));

        for (String symbol : BacktestConfig.SYMBOLS) {
            JCheckBox cb = new JCheckBox(symbol);
            cb.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            symbolCheckboxes.put(symbol, cb);
            symbolCheckboxPanel.add(cb);
        }
        // Pre-select EURUSD
        symbolCheckboxes.get("EURUSD").setSelected(true);

        // Select All / None buttons
        JPanel selectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        selectPanel.setOpaque(false);
        JButton selectAll = new JButton("Select All");
        selectAll.addActionListener(e -> symbolCheckboxes.values().forEach(cb -> cb.setSelected(true)));
        JButton selectNone = new JButton("Select None");
        selectNone.addActionListener(e -> symbolCheckboxes.values().forEach(cb -> cb.setSelected(false)));
        selectPanel.add(selectAll);
        selectPanel.add(selectNone);

        symbolSection.add(symbolLabel, BorderLayout.NORTH);
        symbolSection.add(symbolCheckboxPanel, BorderLayout.CENTER);
        symbolSection.add(selectPanel, BorderLayout.SOUTH);

        // Date range
        JPanel datePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        datePanel.setOpaque(false);

        DatePickerSettings fromSettings = new DatePickerSettings();
        fromSettings.setFormatForDatesCommonEra("yyyy-MM-dd");
        downloadFromDate = new DatePicker(fromSettings);
        downloadFromDate.setDate(LocalDate.now().minusMonths(6));

        DatePickerSettings toSettings = new DatePickerSettings();
        toSettings.setFormatForDatesCommonEra("yyyy-MM-dd");
        downloadToDate = new DatePicker(toSettings);
        downloadToDate.setDate(LocalDate.now().minusDays(1));

        datePanel.add(new JLabel("From:"));
        datePanel.add(downloadFromDate);
        datePanel.add(Box.createHorizontalStrut(10));
        datePanel.add(new JLabel("To:"));
        datePanel.add(downloadToDate);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        buttonPanel.setOpaque(false);

        downloadButton = new JButton("⬇  Download Data");
        downloadButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        downloadButton.setBackground(new Color(40, 100, 140));
        downloadButton.setForeground(Color.WHITE);
        downloadButton.setPreferredSize(new Dimension(180, 36));
        downloadButton.addActionListener(e -> startDownload());

        cancelDownloadButton = new JButton("Cancel");
        cancelDownloadButton.setEnabled(false);
        cancelDownloadButton.addActionListener(e -> cancelDownload());

        buttonPanel.add(downloadButton);
        buttonPanel.add(cancelDownloadButton);

        downloadProgress = new JProgressBar(0, 100);
        downloadProgress.setStringPainted(true);
        downloadProgress.setString("Ready");
        downloadProgress.setPreferredSize(new Dimension(0, 22));

        // Layout
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.setOpaque(false);
        centerPanel.add(symbolSection, BorderLayout.NORTH);

        JPanel bottomControls = new JPanel(new BorderLayout(5, 5));
        bottomControls.setOpaque(false);
        bottomControls.add(datePanel, BorderLayout.NORTH);
        bottomControls.add(buttonPanel, BorderLayout.CENTER);
        bottomControls.add(downloadProgress, BorderLayout.SOUTH);
        centerPanel.add(bottomControls, BorderLayout.CENTER);

        panel.add(title, BorderLayout.NORTH);
        panel.add(centerPanel, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createImportPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Title
        JLabel title = new JLabel("Step 2: Convert & Import to MetaTrader 5");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(new Color(78, 154, 241));

        // Data table showing downloaded data
        String[] columns = {"Symbol", "Data Range", "Status", "Bars", "CSV File"};
        dataTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        dataTable = new JTable(dataTableModel);
        dataTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        dataTable.setRowHeight(26);
        dataTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        dataTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        JScrollPane tableScroll = new JScrollPane(dataTable);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        buttonPanel.setOpaque(false);

        JButton scanBtn = new JButton("Scan Downloaded Data");
        scanBtn.addActionListener(e -> scanDownloadedData());

        convertButton = new JButton("Convert to CSV");
        convertButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        convertButton.setBackground(new Color(120, 100, 40));
        convertButton.setForeground(Color.WHITE);
        convertButton.addActionListener(e -> convertToCsv());

        importButton = new JButton("Import to MT5");
        importButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        importButton.setBackground(new Color(40, 120, 70));
        importButton.setForeground(Color.WHITE);
        importButton.addActionListener(e -> importToMt5());

        buttonPanel.add(scanBtn);
        buttonPanel.add(convertButton);
        buttonPanel.add(importButton);

        importProgress = new JProgressBar(0, 100);
        importProgress.setStringPainted(true);
        importProgress.setString("Ready");
        importProgress.setPreferredSize(new Dimension(0, 22));

        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setOpaque(false);
        bottomPanel.add(buttonPanel, BorderLayout.NORTH);
        bottomPanel.add(importProgress, BorderLayout.SOUTH);

        panel.add(title, BorderLayout.NORTH);
        panel.add(tableScroll, BorderLayout.CENTER);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        // Initial scan
        SwingUtilities.invokeLater(this::scanDownloadedData);

        return panel;
    }

    private void startDownload() {
        List<String> selectedSymbols = getSelectedSymbols();
        if (selectedSymbols.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please select at least one currency pair.",
                    "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        LocalDate from = downloadFromDate.getDate();
        LocalDate to = downloadToDate.getDate();
        if (from == null || to == null || from.isAfter(to)) {
            JOptionPane.showMessageDialog(this,
                    "Please select a valid date range.",
                    "Invalid Dates", JOptionPane.WARNING_MESSAGE);
            return;
        }

        downloadButton.setEnabled(false);
        cancelDownloadButton.setEnabled(true);
        downloadProgress.setIndeterminate(false);
        downloadProgress.setValue(0);

        logPanel.log("INFO", "Starting Dukascopy download for: " + String.join(", ", selectedSymbols));
        logPanel.log("INFO", "Date range: " + from + " to " + to);

        currentDownloader = new DukascopyDownloader(config.getDataDirectory());
        currentDownloader.setLogCallback(msg -> logPanel.log("INFO", msg));
        currentDownloader.setProgressCallback(progress ->
                SwingUtilities.invokeLater(() -> {
                    downloadProgress.setValue((int) (progress * 100));
                    downloadProgress.setString(String.format("%.0f%%", progress * 100));
                }));

        currentDownloadWorker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                int total = selectedSymbols.size();
                for (int i = 0; i < total; i++) {
                    if (isCancelled()) break;
                    String symbol = selectedSymbols.get(i);
                    logPanel.log("INFO", String.format("Downloading %s (%d/%d)...",
                            symbol, i + 1, total));

                    downloadProgress.setString(String.format("%s (%d/%d)",
                            symbol, i + 1, total));

                    currentDownloader.download(symbol, from, to);
                }
                return null;
            }

            @Override
            protected void done() {
                downloadButton.setEnabled(true);
                cancelDownloadButton.setEnabled(false);
                downloadProgress.setValue(100);
                downloadProgress.setString("Download complete");

                try {
                    get();
                    logPanel.log("INFO", "All downloads completed!");
                } catch (Exception e) {
                    if (!isCancelled()) {
                        logPanel.log("ERROR", "Download error: " + e.getMessage());
                    }
                }

                // Refresh data table
                scanDownloadedData();
            }
        };

        currentDownloadWorker.execute();
    }

    private void cancelDownload() {
        if (currentDownloader != null) {
            currentDownloader.cancel();
        }
        if (currentDownloadWorker != null) {
            currentDownloadWorker.cancel(true);
        }
        downloadButton.setEnabled(true);
        cancelDownloadButton.setEnabled(false);
        downloadProgress.setString("Cancelled");
        logPanel.log("WARN", "Download cancelled");
    }

    private void scanDownloadedData() {
        dataTableModel.setRowCount(0);
        Path dataDir = config.getDataDirectory();

        if (!Files.exists(dataDir)) return;

        try (var dirs = Files.list(dataDir)) {
            dirs.filter(Files::isDirectory).sorted().forEach(symbolDir -> {
                String symbol = symbolDir.getFileName().toString();
                // Count .bi5 files
                try (var walker = Files.walk(symbolDir)) {
                    long fileCount = walker.filter(p -> p.toString().endsWith(".bi5")).count();
                    if (fileCount > 0) {
                        // Check if CSV already exists
                        Path csvFile = dataDir.resolve(symbol + "_M1.csv");
                        String status = Files.exists(csvFile) ? "CSV Ready" : "Downloaded (.bi5)";
                        String csvPath = Files.exists(csvFile) ? csvFile.toString() : "—";

                        // Check if already imported to MT5
                        String customName = CustomSymbolManager.toCustomName(symbol);
                        if (symbolManager.hasSymbol(customName)) {
                            status = "Imported to MT5";
                        }

                        dataTableModel.addRow(new Object[]{
                                symbol,
                                fileCount + " files",
                                status,
                                "—",
                                csvPath
                        });
                    }
                } catch (Exception e) {
                    logPanel.log("WARN", "Error scanning " + symbol + ": " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logPanel.log("ERROR", "Error scanning data directory: " + e.getMessage());
        }
    }

    private void convertToCsv() {
        int[] selectedRows = dataTable.getSelectedRows();
        if (selectedRows.length == 0) {
            // If nothing selected, convert all
            selectedRows = new int[dataTableModel.getRowCount()];
            for (int i = 0; i < selectedRows.length; i++) selectedRows[i] = i;
        }

        final int[] rows = selectedRows;
        convertButton.setEnabled(false);
        importProgress.setIndeterminate(true);
        importProgress.setString("Converting...");

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                CsvConverter converter = new CsvConverter(config.getBrokerTimezoneOffset());
                Bi5Decoder decoder = new Bi5Decoder();
                Path dataDir = config.getDataDirectory();

                for (int row : rows) {
                    String symbol = (String) dataTableModel.getValueAt(row, 0);
                    logPanel.log("INFO", "Converting " + symbol + " to M1 CSV...");

                    // Find date range from downloaded files
                    DukascopyDownloader downloader = new DukascopyDownloader(dataDir);

                    // Decode all ticks for this symbol
                    List<Bi5Decoder.Tick> allTicks = new ArrayList<>();
                    Path symbolDir = dataDir.resolve(symbol);

                    try (var walker = Files.walk(symbolDir)) {
                        walker.filter(p -> p.toString().endsWith(".bi5"))
                              .sorted()
                              .forEach(bi5File -> {
                                  try {
                                      Path relative = symbolDir.relativize(bi5File);
                                      int year = Integer.parseInt(relative.getName(0).toString());
                                      int month = Integer.parseInt(relative.getName(1).toString());
                                      int day = Integer.parseInt(relative.getName(2).toString());
                                      String hourStr = relative.getName(3).toString();
                                      int hour = Integer.parseInt(hourStr.substring(0, 2));

                                      LocalDate date = LocalDate.of(year, month, day);
                                      List<Bi5Decoder.Tick> ticks = decoder.decode(bi5File, symbol, date, hour);
                                      allTicks.addAll(ticks);
                                  } catch (Exception e) {
                                      // Skip files that can't be parsed
                                  }
                              });
                    }

                    if (allTicks.isEmpty()) {
                        logPanel.log("WARN", "No tick data for " + symbol);
                        continue;
                    }

                    // Convert to M1 bars
                    List<CsvConverter.M1Bar> bars = converter.aggregateToM1(allTicks);
                    Path csvFile = dataDir.resolve(symbol + "_M1.csv");
                    int digits = CsvConverter.getDigits(symbol);
                    converter.writeCsv(bars, csvFile, digits);

                    logPanel.log("INFO", String.format("Converted %s: %d ticks → %d M1 bars → %s",
                            symbol, allTicks.size(), bars.size(), csvFile.getFileName()));

                    // Update table
                    final int r = row;
                    SwingUtilities.invokeLater(() -> {
                        dataTableModel.setValueAt("CSV Ready", r, 2);
                        dataTableModel.setValueAt(String.valueOf(bars.size()), r, 3);
                        dataTableModel.setValueAt(csvFile.toString(), r, 4);
                    });
                }
                return null;
            }

            @Override
            protected void done() {
                convertButton.setEnabled(true);
                importProgress.setIndeterminate(false);
                importProgress.setValue(100);
                importProgress.setString("Conversion complete");

                try {
                    get();
                    logPanel.log("INFO", "CSV conversion completed!");
                } catch (Exception e) {
                    logPanel.log("ERROR", "Conversion error: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    private void importToMt5() {
        int[] selectedRows = dataTable.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this,
                    "Please select rows to import, or convert data to CSV first.",
                    "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        importButton.setEnabled(false);
        importProgress.setIndeterminate(true);
        importProgress.setString("Importing to MT5...");

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                Mt5DataImporter importer = new Mt5DataImporter();
                importer.setLogCallback(msg -> logPanel.log("INFO", msg));
                Path dataDir = config.getDataDirectory();

                for (int row : selectedRows) {
                    String symbol = (String) dataTableModel.getValueAt(row, 0);
                    String csvPath = (String) dataTableModel.getValueAt(row, 4);

                    if (csvPath == null || csvPath.equals("—")) {
                        logPanel.log("WARN", symbol + ": No CSV file, convert first!");
                        continue;
                    }

                    Path csvFile = Paths.get(csvPath);
                    if (!Files.exists(csvFile)) {
                        logPanel.log("WARN", symbol + ": CSV file not found: " + csvPath);
                        continue;
                    }

                    String customName = CustomSymbolManager.toCustomName(symbol);
                    int digits = CsvConverter.getDigits(symbol);

                    logPanel.log("INFO", "Importing " + symbol + " as " + customName + "...");

                    boolean success = importer.importToMt5(csvFile, customName, symbol, digits);

                    if (success) {
                        symbolManager.registerSymbol(customName, symbol,
                                LocalDate.now().minusYears(1), LocalDate.now(), digits, 0);

                        final int r = row;
                        SwingUtilities.invokeLater(() -> {
                            dataTableModel.setValueAt("Imported to MT5", r, 2);
                        });
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                importButton.setEnabled(true);
                importProgress.setIndeterminate(false);
                importProgress.setValue(100);
                importProgress.setString("Import complete");

                try {
                    get();
                    logPanel.log("INFO", "MT5 import completed!");
                    logPanel.log("INFO", "Please compile DukaImporter.mq5 in MetaEditor and run it on a chart.");
                } catch (Exception e) {
                    logPanel.log("ERROR", "Import error: " + e.getMessage());
                }
            }
        };

        worker.execute();
    }

    private List<String> getSelectedSymbols() {
        List<String> selected = new ArrayList<>();
        for (Map.Entry<String, JCheckBox> entry : symbolCheckboxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                selected.add(entry.getKey());
            }
        }
        return selected;
    }
}
