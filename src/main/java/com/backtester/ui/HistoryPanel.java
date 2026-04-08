package com.backtester.ui;

import com.backtester.database.DatabaseManager;
import com.backtester.database.HistoryRun;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HistoryPanel extends JPanel {

    private static final Color TODAY_COLOR = new Color(80, 210, 120);
    private static final Color TODAY_EXPERT_COLOR = new Color(80, 210, 120);

    private JTree historyTree;
    private DefaultTreeModel treeModel;
    private JTextArea detailsArea;
    private final DatabaseManager dbManager;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public HistoryPanel() {
        this.dbManager = DatabaseManager.getInstance();
        setLayout(new BorderLayout());
        
        // Split Pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(350);

        // Left side: Tree
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("History");
        treeModel = new DefaultTreeModel(root);
        historyTree = new JTree(treeModel);
        historyTree.setCellRenderer(new TodayHighlightRenderer());
        
        historyTree.addTreeSelectionListener(this::onNodeSelected);
        historyTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    onNodeDoubleClicked();
                }
            }
        });

        JScrollPane treeScroll = new JScrollPane(historyTree);
        treeScroll.setBorder(BorderFactory.createTitledBorder("Saved Runs"));
        
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(treeScroll, BorderLayout.CENTER);
        
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> reloadTree());
        leftPanel.add(refreshBtn, BorderLayout.SOUTH);

        // Right side: Details
        detailsArea = new JTextArea();
        detailsArea.setEditable(false);
        detailsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        detailsArea.setMargin(new Insets(10, 10, 10, 10));
        
        JPanel detailsPanel = new JPanel(new BorderLayout());
        detailsPanel.setBorder(BorderFactory.createTitledBorder("Run Summary"));
        detailsPanel.add(new JScrollPane(detailsArea), BorderLayout.CENTER);
        
        JLabel helpLabel = new JLabel("Double-click a run on the left to open the full HTML Report in your browser.", SwingConstants.CENTER);
        helpLabel.setForeground(new Color(150, 150, 150));
        helpLabel.setBorder(new EmptyBorder(5, 5, 5, 5));
        detailsPanel.add(helpLabel, BorderLayout.SOUTH);

        splitPane.setLeftComponent(leftPanel);
        splitPane.setRightComponent(detailsPanel);
        
        add(splitPane, BorderLayout.CENTER);
        
        reloadTree();
    }

    private void reloadTree() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        root.removeAllChildren();

        List<HistoryRun> runs = dbManager.getAllRuns();
        
        // Group by type -> expert
        Map<String, Map<String, List<HistoryRun>>> grouped = runs.stream()
                .collect(Collectors.groupingBy(HistoryRun::getRunType,
                         Collectors.groupingBy(HistoryRun::getExpertName)));

        for (Map.Entry<String, Map<String, List<HistoryRun>>> typeEntry : grouped.entrySet()) {
            DefaultMutableTreeNode typeNode = new DefaultMutableTreeNode(typeEntry.getKey());
            
            for (Map.Entry<String, List<HistoryRun>> expertEntry : typeEntry.getValue().entrySet()) {
                DefaultMutableTreeNode expertNode = new DefaultMutableTreeNode(expertEntry.getKey());
                
                for (HistoryRun run : expertEntry.getValue()) {
                    String label = df.format(new Date(run.getTimestamp()));
                    
                    try {
                        if (run.getResultJson() != null && !run.getResultJson().isEmpty()) {
                            com.google.gson.JsonObject json = gson.fromJson(run.getResultJson(), com.google.gson.JsonObject.class);
                            String symbol = json.has("symbol") ? json.get("symbol").getAsString() : "";
                            String period = json.has("period") ? json.get("period").getAsString() : "";
                            
                            // Sometimes symbol or period is empty for parent robustness runs, ignore if empty
                            if (symbol != null && !symbol.trim().isEmpty() && period != null && !period.trim().isEmpty()) {
                                label += " \u2014 " + symbol + " " + period;
                            } else if (symbol != null && !symbol.trim().isEmpty()) {
                                label += " \u2014 " + symbol;
                            }
                        }
                    } catch (Exception ignored) {}

                    DefaultMutableTreeNode runNode = new DefaultMutableTreeNode(new RunNodeData(label, run));
                    expertNode.add(runNode);
                }
                
                typeNode.add(expertNode);
            }
            root.add(typeNode);
        }

        treeModel.reload();
        for (int i = 0; i < historyTree.getRowCount(); i++) {
            historyTree.expandRow(i);
        }
    }

    private void onNodeSelected(TreeSelectionEvent e) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) historyTree.getLastSelectedPathComponent();
        if (node == null || !node.isLeaf() || !(node.getUserObject() instanceof RunNodeData)) {
            detailsArea.setText("");
            return;
        }

        RunNodeData data = (RunNodeData) node.getUserObject();
        HistoryRun run = data.run;
        
        StringBuilder sb = new StringBuilder();
        sb.append("Type: ").append(run.getRunType()).append("\n");
        sb.append("Expert: ").append(run.getExpertName()).append("\n");
        sb.append("Date: ").append(df.format(new Date(run.getTimestamp()))).append("\n");
        sb.append("HTML Path: ").append(run.getHtmlPath()).append("\n\n");
        
        try {
            if (run.getResultJson() != null && !run.getResultJson().trim().isEmpty()) {
                JsonObject json = gson.fromJson(run.getResultJson(), JsonObject.class);
                sb.append("Summary Metrics:\n");
                sb.append("----------------\n");
                sb.append(gson.toJson(json));
            }
        } catch (Exception ex) {
            sb.append("Metrics JSON: ").append(run.getResultJson());
        }
        
        detailsArea.setText(sb.toString());
        detailsArea.setCaretPosition(0);
    }

    private void onNodeDoubleClicked() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) historyTree.getLastSelectedPathComponent();
        if (node != null && node.isLeaf() && node.getUserObject() instanceof RunNodeData) {
            RunNodeData data = (RunNodeData) node.getUserObject();
            String path = data.run.getHtmlPath();
            if (path != null && !path.trim().isEmpty()) {
                try {
                    File f = new File(path);
                    if (f.exists()) {
                        Desktop.getDesktop().open(f);
                    } else {
                        JOptionPane.showMessageDialog(this, "File no longer exists: " + path, "Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Could not open Report: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    private static class RunNodeData {
        String label;
        HistoryRun run;

        RunNodeData(String label, HistoryRun run) {
            this.label = label;
            this.run = run;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Custom TreeCellRenderer that highlights today's runs in green
     * for instant visual identification of the latest backtests.
     */
    private class TodayHighlightRenderer extends DefaultTreeCellRenderer {

        private final String todayPrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {

            Component c = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            if (!sel && value instanceof DefaultMutableTreeNode) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                Object userObj = node.getUserObject();

                if (userObj instanceof RunNodeData) {
                    // Leaf node: check if timestamp is from today
                    RunNodeData data = (RunNodeData) userObj;
                    if (data.label.startsWith(todayPrefix)) {
                        setForeground(TODAY_COLOR);
                        setFont(getFont().deriveFont(Font.BOLD));
                    }
                } else if (!leaf && node.getParent() != null && node.getParent() != tree.getModel().getRoot()) {
                    // Expert node: check if any child run is from today
                    if (hasChildFromToday(node)) {
                        setForeground(TODAY_EXPERT_COLOR);
                    }
                }
            }

            return c;
        }

        private boolean hasChildFromToday(DefaultMutableTreeNode expertNode) {
            for (int i = 0; i < expertNode.getChildCount(); i++) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) expertNode.getChildAt(i);
                if (child.getUserObject() instanceof RunNodeData) {
                    RunNodeData data = (RunNodeData) child.getUserObject();
                    if (data.label.startsWith(todayPrefix)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
