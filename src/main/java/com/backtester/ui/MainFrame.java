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
        setTitle("MT5 Backtester — Antigravity Protocol Suite");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 900));
        setPreferredSize(new Dimension(1300, 1050));

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

        OptimizationPanel optimizationPanel = new OptimizationPanel(logPanel);
        RobustnessPanel robustnessPanel = new RobustnessPanel(logPanel);
        HistoryPanel historyPanel = new HistoryPanel();
        HelpPanel helpPanel = new HelpPanel(logPanel);

        tabbedPane.addTab("  ▶  Backtest  ", createTabIcon("▶"), backtestPanel);
        tabbedPane.addTab("  🔁  Multi-Backtester  ", createTabIcon("🔁"), multiBacktestPanel);
        tabbedPane.addTab("  🔬  Optimizer  ", createTabIcon("🔬"), optimizationPanel);
        tabbedPane.addTab("  📉  Robustness  ", createTabIcon("📉"), robustnessPanel);
        tabbedPane.addTab("  📚  Database  ", createTabIcon("📚"), historyPanel);
        tabbedPane.addTab("  ⬇  Dukascopy Data  ", createTabIcon("⬇"), dukascopyPanel);
        tabbedPane.addTab("  ⚙  Settings  ", createTabIcon("⚙"), settingsPanel);
        tabbedPane.addTab("  📋  Log  ", createTabIcon("📋"), logPanel);
        tabbedPane.addTab("  📖  Manual  ", createTabIcon("📖"), helpPanel);

        // Header bar
        JPanel headerPanel = createHeaderPanel();

        // Create Textured Content Pane
        JPanel contentPane = new JPanel(new BorderLayout()) {
            private java.awt.image.BufferedImage metalBg;
            {
                try {
                    java.net.URL imgUrl = getClass().getResource("/images/brushed_metal.png");
                    if (imgUrl != null) metalBg = javax.imageio.ImageIO.read(imgUrl);
                } catch (Exception e) {
                    System.err.println("Could not load brushed_metal.png");
                }
            }
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (metalBg != null) {
                    int w = getWidth();
                    int h = getHeight();
                    int iw = metalBg.getWidth();
                    int ih = metalBg.getHeight();
                    // Tile the brushed metal texture across the entire background
                    for (int x = 0; x < w; x += iw) {
                        for (int y = 0; y < h; y += ih) {
                            g.drawImage(metalBg, x, y, this);
                        }
                    }
                }
            }
        };
        setContentPane(contentPane);

        // Layout
        contentPane.add(headerPanel, BorderLayout.NORTH);
        contentPane.add(tabbedPane, BorderLayout.CENTER);
        contentPane.add(createStatusBar(), BorderLayout.SOUTH);

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
        logPanel.log("INFO", "MT5 Backtester v1.2.6 started");
        logPanel.log("INFO", "Ready.");

        // Run startup checks for directories and MT5 terminal
        SwingUtilities.invokeLater(this::runStartupChecks);
    }

    private void runStartupChecks() {
        com.backtester.config.AppConfig config = com.backtester.config.AppConfig.getInstance();
        java.util.List<String> warnings = new java.util.ArrayList<>();

        // Check MT5 terminal
        java.nio.file.Path mt5Path = java.nio.file.Paths.get(config.getMt5TerminalPath());
        if (!java.nio.file.Files.exists(mt5Path) || !java.nio.file.Files.isRegularFile(mt5Path)) {
            warnings.add("MetaTrader 5 terminal not found at:\n" + mt5Path + "\n(Please update the path in Settings)");
            logPanel.log("ERROR", "MT5 Terminal not found: " + mt5Path);
        } else if (!mt5Path.getFileName().toString().toLowerCase().equals("terminal64.exe")) {
            warnings.add("Invalid terminal executable. It MUST be named terminal64.exe:\n" + mt5Path + "\n(Please update the path in Settings)");
            logPanel.log("ERROR", "Invalid MT5 executable named: " + mt5Path.getFileName());
        }

        // Check Reports Directory
        java.nio.file.Path reportsDir = config.getReportsDirectory();
        if (!java.nio.file.Files.exists(reportsDir)) {
            try {
                java.nio.file.Files.createDirectories(reportsDir);
            } catch (Exception e) {}
        }
        if (!java.nio.file.Files.exists(reportsDir) || !java.nio.file.Files.isWritable(reportsDir)) {
            warnings.add("Reports directory is not accessible or writable:\n" + reportsDir + "\n(Check folder permissions or change path in Settings)");
            logPanel.log("ERROR", "Reports directory not writable: " + reportsDir);
        }

        // Check Data Directory
        java.nio.file.Path dataDir = config.getDataDirectory();
        if (!java.nio.file.Files.exists(dataDir)) {
            try {
                java.nio.file.Files.createDirectories(dataDir);
            } catch (Exception e) {}
        }
        if (!java.nio.file.Files.exists(dataDir) || !java.nio.file.Files.isWritable(dataDir)) {
            warnings.add("Data directory is not accessible or writable:\n" + dataDir + "\n(Check folder permissions or change path in Settings)");
            logPanel.log("ERROR", "Data directory not writable: " + dataDir);
        }

        // Show warning dialog if needed
        if (!warnings.isEmpty()) {
            StringBuilder sb = new StringBuilder("The following configuration issues were detected:\n\n");
            for (String w : warnings) {
                sb.append("• ").append(w).append("\n\n");
            }
            sb.append("Some features of the MT5 Backtester will fail until these issues are resolved.");
            
            JOptionPane.showMessageDialog(this,
                    sb.toString(),
                    "Configuration Warning",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(12, 15, 8, 15));
        header.setBackground(new Color(17, 20, 26)); // Dark Steel

        // Title
        JLabel title = new JLabel("MT5 Backtester");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(new Color(0, 229, 255)); // Electric Blue

        // Get execution path to verify correct version is running
        String execPath = System.getProperty("user.dir");
        
        JLabel subtitle = new JLabel("   — Antigravity Protocol Suite | Running from: " + execPath);
        subtitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        subtitle.setForeground(new Color(200, 205, 220));

        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        titlePanel.setOpaque(false);
        titlePanel.add(title);
        titlePanel.add(subtitle);

        // Version label
        JLabel version = new JLabel("v1.2.6");
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
