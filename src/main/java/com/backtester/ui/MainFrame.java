package com.backtester.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Main application window with tabbed interface.
 * Tabs: Backtest, Multi-Backtester, Dukascopy Data, Settings, Log
 */
public class MainFrame extends JFrame {

    private final BacktestPanel backtestPanel;
    private final MultiBacktestPanel multiBacktestPanel;
    private final DukascopyPanel dukascopyPanel;
    private final SettingsPanel settingsPanel;
    private final LogPanel logPanel;

    public MainFrame() {
        setTitle("MT5 Backtester — Automated MetaTrader 5 Backtesting");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 750));
        setPreferredSize(new Dimension(1280, 850));

        // Center on screen
        setLocationRelativeTo(null);

        // Create the shared log panel
        logPanel = new LogPanel();

        // Create panels
        backtestPanel = new BacktestPanel(logPanel);
        multiBacktestPanel = new MultiBacktestPanel(logPanel);
        dukascopyPanel = new DukascopyPanel(logPanel);
        settingsPanel = new SettingsPanel();

        // Build tabbed pane
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        tabbedPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        tabbedPane.addTab("  ▶  Backtest  ", createTabIcon("▶"), backtestPanel);
        tabbedPane.addTab("  🔁  Multi-Backtester  ", createTabIcon("🔁"), multiBacktestPanel);
        tabbedPane.addTab("  ⬇  Dukascopy Data  ", createTabIcon("⬇"), dukascopyPanel);
        tabbedPane.addTab("  ⚙  Settings  ", createTabIcon("⚙"), settingsPanel);
        tabbedPane.addTab("  📋  Log  ", createTabIcon("📋"), logPanel);

        // Header bar
        JPanel headerPanel = createHeaderPanel();

        // Layout
        setLayout(new BorderLayout());
        add(headerPanel, BorderLayout.NORTH);
        add(tabbedPane, BorderLayout.CENTER);
        add(createStatusBar(), BorderLayout.SOUTH);

        // Window close handler
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                int result = JOptionPane.showConfirmDialog(MainFrame.this,
                        "Are you sure you want to exit?",
                        "MT5 Backtester",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE);
                if (result == JOptionPane.YES_OPTION) {
                    settingsPanel.saveSettings();
                    dispose();
                    System.exit(0);
                }
            }
        });

        pack();

        // Log startup
        logPanel.log("INFO", "MT5 Backtester v1.0.0 started");
        logPanel.log("INFO", "Ready.");
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(12, 15, 8, 15));
        header.setBackground(new Color(30, 33, 40));

        // Title
        JLabel title = new JLabel("MT5 Backtester");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(new Color(78, 154, 241));

        JLabel subtitle = new JLabel("   Automated MetaTrader 5 Backtesting & Dukascopy Data Integration");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitle.setForeground(new Color(140, 145, 160));

        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        titlePanel.setOpaque(false);
        titlePanel.add(title);
        titlePanel.add(subtitle);

        // Version label
        JLabel version = new JLabel("v1.0.0");
        version.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        version.setForeground(new Color(100, 105, 120));

        header.add(titlePanel, BorderLayout.WEST);
        header.add(version, BorderLayout.EAST);

        // Bottom separator
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(header, BorderLayout.CENTER);
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(60, 65, 75));
        wrapper.add(sep, BorderLayout.SOUTH);

        return wrapper;
    }

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(60, 65, 75)),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        statusBar.setBackground(new Color(30, 33, 40));

        JLabel statusLabel = new JLabel("Ready");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        statusLabel.setForeground(new Color(140, 145, 160));

        JLabel memoryLabel = new JLabel();
        memoryLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        memoryLabel.setForeground(new Color(100, 105, 120));

        // Update memory info periodically
        Timer timer = new Timer(5000, e -> {
            Runtime rt = Runtime.getRuntime();
            long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
            long maxMB = rt.maxMemory() / (1024 * 1024);
            memoryLabel.setText(String.format("Memory: %d / %d MB", usedMB, maxMB));
        });
        timer.start();
        timer.getActionListeners()[0].actionPerformed(null); // initial update

        statusBar.add(statusLabel, BorderLayout.WEST);
        statusBar.add(memoryLabel, BorderLayout.EAST);

        return statusBar;
    }

    private Icon createTabIcon(String emoji) {
        // Return null — we use text-based tab labels with unicode icons
        return null;
    }
}
