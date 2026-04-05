package com.backtester.ui;

import com.backtester.config.AppConfig;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Settings panel for configuring MT5 paths, output directories,
 * and default backtest parameters.
 */
public class SettingsPanel extends JPanel {

    private final AppConfig config;

    // MT5 settings
    private JTextField mt5PathField;
    private JCheckBox portableCheckbox;

    // Directory settings
    private JTextField outputDirField;
    private JTextField dataDirField;

    // Default parameters
    private JSpinner depositSpinner;
    private JComboBox<String> currencyCombo;
    private JTextField leverageField;
    private JSpinner timezoneSpinner;
    private JComboBox<String> defaultModelCombo;

    public SettingsPanel() {
        this.config = AppConfig.getInstance();

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Title
        JLabel title = new JLabel("Application Settings");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(new Color(78, 154, 241));
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));

        // Main content
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(createMt5Section());
        content.add(Box.createVerticalStrut(15));
        content.add(createDirectorySection());
        content.add(Box.createVerticalStrut(15));
        content.add(createDefaultsSection());
        content.add(Box.createVerticalStrut(20));
        content.add(createButtonSection());
        content.add(Box.createVerticalGlue());

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(10);

        add(title, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel createMt5Section() {
        JPanel section = createSection("MetaTrader 5");

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 8, 5, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // MT5 Terminal Path
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        section.add(createLabel("Terminal Path:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        mt5PathField = new JTextField(config.getMt5TerminalPath(), 40);
        JPanel pathPanel = new JPanel(new BorderLayout(5, 0));
        pathPanel.setOpaque(false);
        pathPanel.add(mt5PathField, BorderLayout.CENTER);
        JButton browseBtn = new JButton("Browse...");
        browseBtn.addActionListener(e -> browseMt5Path());
        pathPanel.add(browseBtn, BorderLayout.EAST);
        section.add(pathPanel, gbc);

        // Portable mode
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        section.add(createLabel(""), gbc);
        gbc.gridx = 1;
        portableCheckbox = new JCheckBox("Use Portable Mode (/portable flag)", config.isPortableMode());
        portableCheckbox.setToolTipText("Forces MT5 to use the installation directory for all data. Recommended.");
        section.add(portableCheckbox, gbc);

        // MT5 status
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        JLabel statusLabel = new JLabel();
        statusLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        updateMt5Status(statusLabel);
        mt5PathField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateMt5Status(statusLabel); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateMt5Status(statusLabel); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateMt5Status(statusLabel); }
        });
        section.add(statusLabel, gbc);

        return section;
    }

    private void updateMt5Status(JLabel label) {
        String path = mt5PathField.getText().trim();
        if (Files.exists(Paths.get(path))) {
            label.setText("✓ Terminal found");
            label.setForeground(new Color(100, 200, 120));
        } else {
            label.setText("✗ Terminal not found at specified path");
            label.setForeground(new Color(240, 100, 100));
        }
    }

    private JPanel createDirectorySection() {
        JPanel section = createSection("Directories");

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 8, 5, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Output directory
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        section.add(createLabel("Reports Output:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        outputDirField = new JTextField(config.getReportsDirectory().toString(), 40);
        JPanel outputPanel = createDirField(outputDirField);
        section.add(outputPanel, gbc);

        // Data directory
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        section.add(createLabel("Data Directory:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        dataDirField = new JTextField(config.getDataDirectory().toString(), 40);
        JPanel dataPanel = createDirField(dataDirField);
        section.add(dataPanel, gbc);

        return section;
    }

    private JPanel createDefaultsSection() {
        JPanel section = createSection("Default Backtest Parameters");

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 8, 5, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Deposit
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        section.add(createLabel("Default Deposit:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        depositSpinner = new JSpinner(new SpinnerNumberModel(
                config.getDefaultDeposit(), 100, 10000000, 1000));
        section.add(depositSpinner, gbc);

        // Currency
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        section.add(createLabel("Default Currency:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        currencyCombo = new JComboBox<>(new String[]{"USD", "EUR", "GBP", "JPY", "CHF"});
        currencyCombo.setSelectedItem(config.getDefaultCurrency());
        section.add(currencyCombo, gbc);

        // Leverage
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        section.add(createLabel("Default Leverage:"), gbc);
        gbc.gridx = 1;
        leverageField = new JTextField(config.getDefaultLeverage());
        section.add(leverageField, gbc);

        // Tick Model
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        section.add(createLabel("Default Tick Model:"), gbc);
        gbc.gridx = 1;
        defaultModelCombo = new JComboBox<>(com.backtester.engine.BacktestConfig.MODEL_NAMES);
        defaultModelCombo.setSelectedIndex(config.getDefaultModel());
        section.add(defaultModelCombo, gbc);

        // Broker timezone offset
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0;
        section.add(createLabel("Broker Timezone (UTC+):"), gbc);
        gbc.gridx = 1;
        timezoneSpinner = new JSpinner(new SpinnerNumberModel(
                config.getBrokerTimezoneOffset(), -12, 14, 1));
        timezoneSpinner.setToolTipText("Hours offset from UTC for the broker server (e.g. 2 for EET/GMT+2)");
        section.add(timezoneSpinner, gbc);

        return section;
    }

    private JPanel createButtonSection() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        panel.setOpaque(false);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

        JButton saveBtn = new JButton("💾  Save Settings");
        saveBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        saveBtn.setBackground(new Color(40, 120, 70));
        saveBtn.setForeground(Color.WHITE);
        saveBtn.setPreferredSize(new Dimension(180, 38));
        saveBtn.addActionListener(e -> {
            saveSettings();
            JOptionPane.showMessageDialog(this,
                    "Settings saved successfully!",
                    "Settings", JOptionPane.INFORMATION_MESSAGE);
        });

        JButton resetBtn = new JButton("Reset to Defaults");
        resetBtn.addActionListener(e -> resetDefaults());

        panel.add(saveBtn);
        panel.add(resetBtn);

        return panel;
    }

    public void saveSettings() {
        config.setMt5TerminalPath(mt5PathField.getText().trim());
        config.set("mt5.portable.mode", String.valueOf(portableCheckbox.isSelected()));
        config.setReportsDirectory(outputDirField.getText().trim());
        config.setDataDirectory(dataDirField.getText().trim());
        config.set("backtest.deposit", String.valueOf(depositSpinner.getValue()));
        config.set("backtest.currency", (String) currencyCombo.getSelectedItem());
        config.set("backtest.leverage", leverageField.getText().trim());
        config.set("backtest.model", String.valueOf(defaultModelCombo.getSelectedIndex()));
        config.set("broker.timezone.offset", String.valueOf(timezoneSpinner.getValue()));
        config.save();
    }

    private void resetDefaults() {
        mt5PathField.setText("C:\\Program Files\\MetaTrader 5\\terminal64.exe");
        portableCheckbox.setSelected(true);
        depositSpinner.setValue(10000);
        currencyCombo.setSelectedItem("USD");
        leverageField.setText("1:100");
        defaultModelCombo.setSelectedIndex(0);
        timezoneSpinner.setValue(2);
    }

    private JPanel createSection(String title) {
        JPanel section = new JPanel(new GridBagLayout());
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(new Color(60, 65, 75)),
                        " " + title + " ",
                        javax.swing.border.TitledBorder.LEFT,
                        javax.swing.border.TitledBorder.TOP,
                        new Font("Segoe UI", Font.BOLD, 13),
                        new Color(180, 185, 195)),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        return section;
    }

    private JPanel createDirField(JTextField field) {
        JPanel panel = new JPanel(new BorderLayout(5, 0));
        panel.setOpaque(false);
        panel.add(field, BorderLayout.CENTER);
        JButton browseBtn = new JButton("Browse...");
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(field.getText());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                field.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        panel.add(browseBtn, BorderLayout.EAST);
        return panel;
    }

    private void browseMt5Path() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "MetaTrader 5 Terminal (terminal64.exe)", "exe"));
        chooser.setDialogTitle("Select MetaTrader 5 Terminal");

        // Start in common MT5 locations
        File progFiles = new File("C:\\Program Files");
        if (progFiles.exists()) {
            chooser.setCurrentDirectory(progFiles);
        }

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            mt5PathField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        return label;
    }
}
