package com.backtester.ui;

import com.backtester.config.EaParameter;
import com.backtester.config.EaParameterManager;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Modal dialog for editing EA input parameters (.set file configuration).
 * 
 * Features:
 * - Table with Section | Parameter | Value | Default | Modified columns
 * - Color highlighting for modified values
 * - Reset to Default button
 * - Generate Default Config button (starts MT5 briefly)
 * - Save & Close / Cancel buttons
 */
public class EaConfigDialog extends JDialog {

    private final String expertPath;
    private final EaParameterManager paramManager;
    private List<EaParameter> parameters;
    private boolean saved = false;

    private JTable paramTable;
    private DefaultTableModel tableModel;
    private JLabel statusLabel;
    private JLabel modifiedCountLabel;
    private JButton saveButton;
    private JButton resetButton;
    private JButton generateButton;
    private JPanel noConfigPanel;
    private JPanel configPanel;

    private JButton storeDbBtn;
    private JButton getDbBtn;

    /** Color constants */
    private static final Color MODIFIED_BG = new Color(60, 50, 30);
    private static final Color SECTION_BG = new Color(40, 45, 55);
    private static final Color SECTION_FG = new Color(130, 160, 200);
    private static final Color HEADER_COLOR = new Color(78, 154, 241);

    public EaConfigDialog(Window owner, String expertPath) {
        super(owner, "EA Configuration: " + EaParameterManager.extractEaBaseName(expertPath), ModalityType.APPLICATION_MODAL);
        this.expertPath = expertPath;
        this.paramManager = new EaParameterManager();

        setSize(800, 600);
        setMinimumSize(new Dimension(600, 400));
        setLocationRelativeTo(owner);

        initComponents();
        loadParameters();
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // === Header ===
        JPanel headerPanel = new JPanel(new BorderLayout(10, 5));
        
        JLabel titleLabel = new JLabel("⚙ EA Input Parameters");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setForeground(HEADER_COLOR);
        
        String eaName = EaParameterManager.extractEaBaseName(expertPath);
        statusLabel = new JLabel("EA: " + eaName);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(160, 165, 175));

        modifiedCountLabel = new JLabel("");
        modifiedCountLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        modifiedCountLabel.setForeground(new Color(241, 178, 78));

        JPanel headerTop = new JPanel(new BorderLayout());
        headerTop.setOpaque(false);
        headerTop.add(titleLabel, BorderLayout.WEST);
        headerTop.add(modifiedCountLabel, BorderLayout.EAST);

        headerPanel.setOpaque(false);
        headerPanel.add(headerTop, BorderLayout.NORTH);
        headerPanel.add(statusLabel, BorderLayout.SOUTH);

        // === Config Panel (shown when params are available) ===
        configPanel = createConfigPanel();

        // === No Config Panel (shown when no .set file exists) ===
        noConfigPanel = createNoConfigPanel();

        // === Button Panel ===
        JPanel buttonPanel = createButtonPanel();

        content.add(headerPanel, BorderLayout.NORTH);
        content.add(configPanel, BorderLayout.CENTER);
        content.add(buttonPanel, BorderLayout.SOUTH);

        add(content);
    }

    private JPanel createConfigPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        // Table
        String[] columns = {"Section", "Parameter", "Value", "Default", "Modified"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // Only the Value column is editable, and not for section-header rows
                if (column != 2) return false;
                Object section = getValueAt(row, 0);
                return section == null || !section.toString().startsWith("──");
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return String.class;
            }
        };

        paramTable = new JTable(tableModel);
        paramTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        paramTable.setRowHeight(26);
        paramTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        paramTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        paramTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

        // Column widths
        paramTable.getColumnModel().getColumn(0).setPreferredWidth(140);
        paramTable.getColumnModel().getColumn(0).setMaxWidth(200);
        paramTable.getColumnModel().getColumn(1).setPreferredWidth(200);
        paramTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        paramTable.getColumnModel().getColumn(3).setPreferredWidth(150);
        paramTable.getColumnModel().getColumn(4).setPreferredWidth(60);
        paramTable.getColumnModel().getColumn(4).setMaxWidth(80);

        // Custom renderer for coloring
        paramTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                
                if (!isSelected) {
                    // Check if this is a section header row
                    Object sectionVal = table.getModel().getValueAt(row, 0);
                    if (sectionVal != null && sectionVal.toString().startsWith("──")) {
                        c.setBackground(SECTION_BG);
                        c.setForeground(SECTION_FG);
                        setFont(getFont().deriveFont(Font.BOLD));
                        return c;
                    }

                    // Check if modified
                    Object modifiedVal = table.getModel().getValueAt(row, 4);
                    if ("✓".equals(modifiedVal)) {
                        c.setBackground(MODIFIED_BG);
                        c.setForeground(new Color(241, 200, 100));
                    } else {
                        c.setBackground(table.getBackground());
                        c.setForeground(table.getForeground());
                    }
                }
                
                // Bold the Modified column marker
                if (column == 4) {
                    setHorizontalAlignment(SwingConstants.CENTER);
                    if ("✓".equals(value)) {
                        c.setForeground(new Color(241, 178, 78));
                        setFont(getFont().deriveFont(Font.BOLD));
                    }
                } else {
                    setHorizontalAlignment(SwingConstants.LEFT);
                }

                return c;
            }
        });

        // Listen for value edits
        tableModel.addTableModelListener(e -> {
            if (e.getColumn() == 2) { // Value column
                int row = e.getFirstRow();
                syncValueToParameter(row);
                updateModifiedCount();
            }
        });

        JScrollPane scrollPane = new JScrollPane(paramTable);

        // Filter/search
        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchPanel.setOpaque(false);
        JLabel searchLabel = new JLabel("🔍 Filter: ");
        searchLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        JTextField searchField = new JTextField();
        searchField.setToolTipText("Type to filter parameters by name");
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                filterTable(searchField.getText().trim());
            }
        });
        searchPanel.add(searchLabel, BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);

        panel.add(searchPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createNoConfigPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

        JLabel iconLabel = new JLabel("⚠", SwingConstants.CENTER);
        iconLabel.setFont(new Font("Segoe UI", Font.PLAIN, 48));
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel msgLabel = new JLabel("<html><center>Keine .set-Konfiguration für diesen EA gefunden.<br>" +
                "Der EA hat möglicherweise keine Input-Parameter oder wurde<br>" +
                "noch nie im MT5 Strategy Tester gestartet.</center></html>");
        msgLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        msgLabel.setHorizontalAlignment(SwingConstants.CENTER);
        msgLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        generateButton = new JButton("🔧 Default Config generieren (MT5 kurz starten)");
        generateButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        generateButton.setBackground(new Color(55, 90, 145));
        generateButton.setForeground(Color.WHITE);
        generateButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        generateButton.setMaximumSize(new Dimension(400, 40));
        generateButton.addActionListener(e -> generateDefaultConfig());

        JLabel hintLabel = new JLabel("<html><center><i>MT5 wird kurz gestartet und wieder beendet,<br>" +
                "um die Standard-Parameter des EAs zu exportieren.</i></center></html>");
        hintLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        hintLabel.setForeground(new Color(130, 135, 145));
        hintLabel.setHorizontalAlignment(SwingConstants.CENTER);
        hintLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        centerPanel.add(Box.createVerticalGlue());
        centerPanel.add(iconLabel);
        centerPanel.add(Box.createVerticalStrut(15));
        centerPanel.add(msgLabel);
        centerPanel.add(Box.createVerticalStrut(20));
        centerPanel.add(generateButton);
        centerPanel.add(Box.createVerticalStrut(10));
        centerPanel.add(hintLabel);
        centerPanel.add(Box.createVerticalGlue());

        panel.add(centerPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JPanel leftBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftBtns.setOpaque(false);

        resetButton = new JButton("🔄 Reset");
        resetButton.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        resetButton.setToolTipText("Reset all parameters to their default values");
        resetButton.addActionListener(e -> resetToDefault());

        JButton deleteCustomBtn = new JButton("🗑 Delete Custom");
        deleteCustomBtn.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        deleteCustomBtn.setToolTipText("Delete custom configuration, use defaults");
        deleteCustomBtn.addActionListener(e -> deleteCustomConfig());

        leftBtns.add(resetButton);
        leftBtns.add(deleteCustomBtn);

        JPanel centerBtns = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        centerBtns.setOpaque(false);

        storeDbBtn = new JButton("💾 Store DB");
        storeDbBtn.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        storeDbBtn.setToolTipText("Backup this configuration to the database");
        storeDbBtn.addActionListener(e -> storeInDatabase());

        getDbBtn = new JButton("📥 Get from DB");
        getDbBtn.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        getDbBtn.setToolTipText("Load configuration from the database");
        getDbBtn.addActionListener(e -> getFromDatabase());

        centerBtns.add(storeDbBtn);
        centerBtns.add(getDbBtn);

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightBtns.setOpaque(false);

        saveButton = new JButton("Save & Close");
        saveButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        saveButton.setBackground(new Color(40, 120, 70));
        saveButton.setForeground(Color.WHITE);
        saveButton.setPreferredSize(new Dimension(130, 34));
        saveButton.addActionListener(e -> saveAndClose());

        JButton cancelButton = new JButton("Cancel");
        cancelButton.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        cancelButton.setPreferredSize(new Dimension(80, 34));
        cancelButton.addActionListener(e -> dispose());

        rightBtns.add(saveButton);
        rightBtns.add(cancelButton);

        panel.add(leftBtns, BorderLayout.WEST);
        panel.add(centerBtns, BorderLayout.CENTER);
        panel.add(rightBtns, BorderLayout.EAST);

        return panel;
    }

    private void loadParameters() {
        parameters = paramManager.getEffectiveParameters(expertPath);

        if (parameters == null || parameters.isEmpty()) {
            // Show the no-config panel
            showNoConfigView();
        } else {
            showConfigView();
            populateTable();
        }
        updateDbButtonsColor();
    }

    private void showNoConfigView() {
        Container parent = configPanel.getParent();
        if (parent != null) {
            parent.remove(configPanel);
            parent.add(noConfigPanel, BorderLayout.CENTER);
            parent.revalidate();
            parent.repaint();
        }
        saveButton.setEnabled(false);
        resetButton.setEnabled(false);
        statusLabel.setText(statusLabel.getText() + "  ·  No configuration found");
    }

    private void showConfigView() {
        Container parent = noConfigPanel.getParent();
        if (parent != null) {
            parent.remove(noConfigPanel);
        }
        // Make sure configPanel is in the content
        Container contentParent = configPanel.getParent();
        if (contentParent == null) {
            // Find the content pane (the JPanel with BorderLayout)
            Component[] comps = getContentPane().getComponents();
            for (Component c : comps) {
                if (c instanceof JPanel) {
                    ((JPanel) c).add(configPanel, BorderLayout.CENTER);
                    break;
                }
            }
        }
        
        saveButton.setEnabled(true);
        resetButton.setEnabled(true);

        boolean hasCustom = paramManager.hasCustomConfig(expertPath);
        String source = hasCustom ? "Custom Config" : "Default Config";
        statusLabel.setText("EA: " + EaParameterManager.extractEaBaseName(expertPath) + "  ·  Source: " + source +
                "  ·  " + (parameters != null ? parameters.size() : 0) + " parameters");
    }

    private void populateTable() {
        tableModel.setRowCount(0);

        if (parameters == null) return;

        String lastSection = "";
        for (EaParameter param : parameters) {
            String section = param.getSection() != null ? param.getSection() : "";

            // Insert section header row
            if (!section.isEmpty() && !section.equals(lastSection)) {
                lastSection = section;
                tableModel.addRow(new Object[]{
                    "── " + section + " ──",
                    "", "", "", ""
                });
            }

            tableModel.addRow(new Object[]{
                "", // Section (already shown as header)
                param.getName(),
                param.getValue(),
                param.getDefaultValue(),
                param.isModified() ? "✓" : ""
            });
        }

        updateModifiedCount();
    }

    private void syncValueToParameter(int tableRow) {
        if (parameters == null) return;

        // Map table row to parameter index (skipping section header rows)
        String paramName = (String) tableModel.getValueAt(tableRow, 1);
        String newValue = (String) tableModel.getValueAt(tableRow, 2);

        if (paramName == null || paramName.isEmpty()) return;

        for (EaParameter param : parameters) {
            if (param.getName().equals(paramName)) {
                param.setValue(newValue);
                // Update the modified marker
                tableModel.setValueAt(param.isModified() ? "✓" : "", tableRow, 4);
                // Update the table coloring
                paramTable.repaint();
                break;
            }
        }
    }

    private void updateModifiedCount() {
        if (parameters == null) {
            modifiedCountLabel.setText("");
            return;
        }
        long modified = parameters.stream().filter(EaParameter::isModified).count();
        if (modified > 0) {
            modifiedCountLabel.setText("⚡ " + modified + " parameter" + (modified > 1 ? "s" : "") + " modified");
        } else {
            modifiedCountLabel.setText("✓ All default");
            modifiedCountLabel.setForeground(new Color(100, 200, 120));
        }
    }

    private void resetToDefault() {
        if (parameters == null) return;

        int confirm = JOptionPane.showConfirmDialog(this,
                "Reset all parameters to their default values?",
                "Reset to Default", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            for (EaParameter param : parameters) {
                param.resetToDefault();
            }
            populateTable();
        }
    }

    private void deleteCustomConfig() {
        if (!paramManager.hasCustomConfig(expertPath)) {
            JOptionPane.showMessageDialog(this,
                    "No custom configuration exists for this EA.",
                    "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete custom configuration? Future backtests will use default parameters.",
                "Delete Custom Config", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            paramManager.deleteCustomParameters(expertPath);
            loadParameters();
        }
    }

    private void saveAndClose() {
        if (parameters == null || parameters.isEmpty()) return;

        // Commit any pending edits
        if (paramTable.isEditing()) {
            paramTable.getCellEditor().stopCellEditing();
        }

        paramManager.saveCustomParameters(expertPath, parameters);
        saved = true;
        dispose();
    }

    private void updateDbButtonsColor() {
        if (storeDbBtn == null || getDbBtn == null) return;
        String eaName = EaParameterManager.extractEaBaseName(expertPath);
        List<com.backtester.database.EaDbConfig> configs = com.backtester.database.DatabaseManager.getInstance().getEaConfigsList(eaName);
        if (configs != null && !configs.isEmpty()) {
            Color green = new Color(60, 140, 80);
            storeDbBtn.setBackground(green);
            storeDbBtn.setForeground(Color.WHITE);
            getDbBtn.setBackground(green);
            getDbBtn.setForeground(Color.WHITE);
        } else {
            storeDbBtn.setBackground(UIManager.getColor("Button.background"));
            storeDbBtn.setForeground(UIManager.getColor("Button.foreground"));
            getDbBtn.setBackground(UIManager.getColor("Button.background"));
            getDbBtn.setForeground(UIManager.getColor("Button.foreground"));
        }
    }

    private void storeInDatabase() {
        if (paramTable != null && paramTable.isEditing()) {
            paramTable.getCellEditor().stopCellEditing();
        }
        
        List<EaParameter> toSave = parameters;
        if (toSave == null || toSave.isEmpty()) return;

        String eaName = EaParameterManager.extractEaBaseName(expertPath);
        boolean success = DbConfigSelectionDialog.showStoreDialog(this, eaName, toSave);
        if (success) {
            updateDbButtonsColor();
            JOptionPane.showMessageDialog(this, "Configuration successfully saved to database.", "Success", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void getFromDatabase() {
        String eaName = EaParameterManager.extractEaBaseName(expertPath);
        com.backtester.database.EaDbConfig selected = DbConfigSelectionDialog.showGetDialog(this, eaName);
        if (selected != null) {
            try {
                Gson gson = new Gson();
                java.lang.reflect.Type listType = new TypeToken<List<EaParameter>>(){}.getType();
                List<EaParameter> dbParams = gson.fromJson(selected.getParametersJson(), listType);

                if (dbParams != null && !dbParams.isEmpty()) {
                    paramManager.saveCustomParameters(expertPath, dbParams);
                    loadParameters(); // Reload table
                    JOptionPane.showMessageDialog(this, "Loaded configuration: " + selected.getConfigName(), "Success", JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Failed to load config: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void generateDefaultConfig() {
        generateButton.setEnabled(false);
        generateButton.setText("⏳ MT5 wird gestartet...");

        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() {
                return paramManager.generateDefaultConfig(expertPath);
            }

            @Override
            protected void done() {
                try {
                    boolean success = get();
                    if (success) {
                        JOptionPane.showMessageDialog(EaConfigDialog.this,
                                "Default-Konfiguration wurde erfolgreich generiert!",
                                "Erfolg", JOptionPane.INFORMATION_MESSAGE);
                        // Reload parameters
                        loadParameters();
                        showConfigView();
                        populateTable();
                        revalidate();
                        repaint();
                    } else {
                        JOptionPane.showMessageDialog(EaConfigDialog.this,
                                "Konfiguration konnte nicht generiert werden.\n" +
                                "Möglicherweise hat der EA keine Input-Parameter,\n" +
                                "oder MT5 konnte nicht korrekt gestartet werden.",
                                "Fehler", JOptionPane.WARNING_MESSAGE);
                        generateButton.setEnabled(true);
                        generateButton.setText("🔧 Default Config generieren (MT5 kurz starten)");
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(EaConfigDialog.this,
                            "Fehler: " + e.getMessage(),
                            "Fehler", JOptionPane.ERROR_MESSAGE);
                    generateButton.setEnabled(true);
                    generateButton.setText("🔧 Default Config generieren (MT5 kurz starten)");
                }
            }
        };
        worker.execute();
    }

    private void filterTable(String filter) {
        if (parameters == null) return;

        tableModel.setRowCount(0);
        String lower = filter.toLowerCase();
        String lastSection = "";

        for (EaParameter param : parameters) {
            if (!filter.isEmpty() && !param.getName().toLowerCase().contains(lower)
                    && (param.getSection() == null || !param.getSection().toLowerCase().contains(lower))) {
                continue;
            }

            String section = param.getSection() != null ? param.getSection() : "";
            if (!section.isEmpty() && !section.equals(lastSection)) {
                lastSection = section;
                tableModel.addRow(new Object[]{"── " + section + " ──", "", "", "", ""});
            }

            tableModel.addRow(new Object[]{
                "",
                param.getName(),
                param.getValue(),
                param.getDefaultValue(),
                param.isModified() ? "✓" : ""
            });
        }
    }

    /**
     * Returns whether the user saved changes.
     */
    public boolean wasSaved() {
        return saved;
    }

    /**
     * Static convenience method to show the dialog.
     */
    public static boolean showForExpert(Window owner, String expertPath) {
        EaConfigDialog dialog = new EaConfigDialog(owner, expertPath);
        dialog.setVisible(true);
        return dialog.wasSaved();
    }
}
