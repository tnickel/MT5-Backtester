package com.backtester.ui;

import com.backtester.config.EaParameter;
import com.backtester.database.DatabaseManager;
import com.backtester.database.EaDbConfig;
import com.google.gson.Gson;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class DbConfigSelectionDialog extends JDialog {

    public enum Mode { STORE, GET }

    private final DatabaseManager dbManager = DatabaseManager.getInstance();
    private final String expertName;
    private final Mode mode;
    private final List<EaParameter> currentParamsToSave;

    private JTable table;
    private DefaultTableModel tableModel;
    private JTextField commentField;
    private JButton actionButton;
    private JButton deleteButton;

    private List<EaDbConfig> configs;
    private EaDbConfig selectedToLoad = null;
    private boolean storedSuccessfully = false;

    private DbConfigSelectionDialog(Window owner, String expertName, Mode mode, List<EaParameter> currentParamsToSave) {
        super(owner, mode == Mode.STORE ? "Store Configuration to Database" : "Load Configuration from Database", ModalityType.APPLICATION_MODAL);
        this.expertName = expertName;
        this.mode = mode;
        this.currentParamsToSave = currentParamsToSave;
        
        setSize(550, 400);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel titleLabel = new JLabel("<html><b>EA:</b> " + expertName + "</html>");
        titleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        // Table setup
        String[] columns = {"ID", "Configuration Name / Comment", "Last Updated"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setMinWidth(0);
        table.getColumnModel().getColumn(0).setMaxWidth(0);
        table.getColumnModel().getColumn(0).setPreferredWidth(0); // Hide ID
        table.getColumnModel().getColumn(2).setPreferredWidth(130);
        table.getColumnModel().getColumn(2).setMaxWidth(130);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateSelection();
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Bottom Panel
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        
        JPanel commentPanel = new JPanel(new BorderLayout(5, 0));
        if (mode == Mode.STORE) {
            commentPanel.add(new JLabel("Config Name:"), BorderLayout.WEST);
            commentField = new JTextField();
            commentField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void changedUpdate(javax.swing.event.DocumentEvent e) { checkInput(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { checkInput(); }
                public void insertUpdate(javax.swing.event.DocumentEvent e) { checkInput(); }
            });
            commentPanel.add(commentField, BorderLayout.CENTER);
            bottomPanel.add(commentPanel, BorderLayout.NORTH);
        }

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        
        deleteButton = new JButton("🗑 Delete Selected");
        deleteButton.setEnabled(false);
        deleteButton.addActionListener(e -> deleteSelectedConfig());
        buttonsPanel.add(deleteButton);

        actionButton = new JButton(mode == Mode.STORE ? "💾 Save" : "📥 Load");
        actionButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        if (mode == Mode.STORE) actionButton.setEnabled(false);
        else actionButton.setEnabled(false); // Enable when row selected
        
        actionButton.addActionListener(e -> {
            if (mode == Mode.STORE) {
                performStore();
            } else {
                performLoad();
            }
        });
        
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());

        buttonsPanel.add(actionButton);
        buttonsPanel.add(cancelButton);
        
        bottomPanel.add(buttonsPanel, BorderLayout.SOUTH);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(mainPanel);
        
        refreshData();
    }

    private void refreshData() {
        configs = dbManager.getEaConfigsList(expertName);
        tableModel.setRowCount(0);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (EaDbConfig cfg : configs) {
            tableModel.addRow(new Object[]{
                cfg.getId(),
                cfg.getConfigName(),
                sdf.format(new Date(cfg.getUpdatedAt()))
            });
        }
        if (mode == Mode.GET && configs.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No saved configurations found for this EA in the database.", "Empty", JOptionPane.INFORMATION_MESSAGE);
            SwingUtilities.invokeLater(this::dispose); // Close dialog gracefully
        }
    }

    private void updateSelection() {
        int row = table.getSelectedRow();
        boolean hasSelection = row >= 0;
        deleteButton.setEnabled(hasSelection);
        
        if (mode == Mode.STORE) {
            if (hasSelection) {
                commentField.setText((String) tableModel.getValueAt(row, 1));
            }
            checkInput();
        } else if (mode == Mode.GET) {
            actionButton.setEnabled(hasSelection);
        }
    }

    private void checkInput() {
        if (mode == Mode.STORE) {
            actionButton.setEnabled(!commentField.getText().trim().isEmpty());
        }
    }

    private void deleteSelectedConfig() {
        int row = table.getSelectedRow();
        if (row >= 0) {
            int id = (int) tableModel.getValueAt(row, 0);
            String name = (String) tableModel.getValueAt(row, 1);
            int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete configuration '" + name + "'?", "Delete", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                dbManager.deleteEaConfig(id);
                refreshData();
                if (mode == Mode.STORE) {
                    commentField.setText("");
                }
            }
        }
    }

    private void performStore() {
        String newName = commentField.getText().trim();
        if (newName.isEmpty()) return;
        
        String json = new Gson().toJson(currentParamsToSave);
        
        // Check if name already exists
        int existingId = -1;
        for (EaDbConfig cfg : configs) {
            if (cfg.getConfigName().equalsIgnoreCase(newName)) {
                existingId = cfg.getId();
                break;
            }
        }
        
        if (existingId != -1) {
            int confirm = JOptionPane.showConfirmDialog(this, "A configuration with this name already exists. Overwrite?", "Overwrite", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                dbManager.updateEaConfig(existingId, newName, json);
                storedSuccessfully = true;
                dispose();
            }
        } else {
            dbManager.insertEaConfig(expertName, newName, json);
            storedSuccessfully = true;
            dispose();
        }
    }

    private void performLoad() {
        int row = table.getSelectedRow();
        if (row >= 0) {
            int id = (int) tableModel.getValueAt(row, 0);
            for (EaDbConfig cfg : configs) {
                if (cfg.getId() == id) {
                    selectedToLoad = cfg;
                    break;
                }
            }
            dispose();
        }
    }

    /**
     * Show dialog to STORE a configuration.
     * @return true if stored successfully, false otherwise.
     */
    public static boolean showStoreDialog(Window owner, String expertName, List<EaParameter> params) {
        DbConfigSelectionDialog dlg = new DbConfigSelectionDialog(owner, expertName, Mode.STORE, params);
        dlg.setVisible(true);
        return dlg.storedSuccessfully;
    }

    /**
     * Show dialog to GET a configuration.
     * @return The selected EaDbConfig, or null if cancelled/none selected.
     */
    public static EaDbConfig showGetDialog(Window owner, String expertName) {
        DbConfigSelectionDialog dlg = new DbConfigSelectionDialog(owner, expertName, Mode.GET, null);
        if (!dlg.configs.isEmpty()) { // prevent empty popup from blocking due to invokeLater dispose
            dlg.setVisible(true);
        }
        return dlg.selectedToLoad;
    }
}
