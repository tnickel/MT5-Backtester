package com.backtester.ui;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Log panel with color-coded log levels and auto-scroll.
 */
public class LogPanel extends JPanel {

    private final JTextPane logTextPane;
    private final StyledDocument styledDoc;
    private final DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    // Color scheme for log levels
    private final Style infoStyle;
    private final Style warnStyle;
    private final Style errorStyle;
    private final Style debugStyle;
    private final Style timestampStyle;
    private final Style mt5Style;

    public LogPanel() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Log text area
        logTextPane = new JTextPane();
        logTextPane.setEditable(false);
        logTextPane.setFont(new Font("Consolas", Font.PLAIN, 12));
        logTextPane.setBackground(new Color(20, 22, 28));
        logTextPane.setForeground(new Color(200, 205, 215));

        styledDoc = logTextPane.getStyledDocument();

        // Setup styles
        infoStyle = createStyle("info", new Color(120, 200, 130));
        warnStyle = createStyle("warn", new Color(240, 200, 80));
        errorStyle = createStyle("error", new Color(240, 100, 100));
        debugStyle = createStyle("debug", new Color(140, 145, 160));
        timestampStyle = createStyle("timestamp", new Color(100, 105, 120));
        mt5Style = createStyle("mt5", new Color(78, 154, 241));

        JScrollPane scrollPane = new JScrollPane(logTextPane);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(50, 55, 65)));
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        // Toolbar
        JPanel toolbar = createToolbar();

        add(toolbar, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        toolbar.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        toolbar.setOpaque(false);

        JButton clearBtn = new JButton("Clear Log");
        clearBtn.addActionListener(e -> clearLog());

        JButton copyBtn = new JButton("Copy to Clipboard");
        copyBtn.addActionListener(e -> {
            logTextPane.selectAll();
            logTextPane.copy();
            logTextPane.setCaretPosition(styledDoc.getLength());
        });

        JLabel label = new JLabel("Application Log");
        label.setFont(new Font("Segoe UI", Font.BOLD, 14));
        label.setForeground(new Color(180, 185, 195));

        toolbar.add(label);
        toolbar.add(Box.createHorizontalStrut(20));
        toolbar.add(clearBtn);
        toolbar.add(copyBtn);

        return toolbar;
    }

    private Style createStyle(String name, Color color) {
        Style style = styledDoc.addStyle(name, null);
        StyleConstants.setForeground(style, color);
        return style;
    }

    /**
     * Log a message with the specified level.
     * Thread-safe — can be called from any thread.
     */
    public void log(String level, String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                String timestamp = LocalDateTime.now().format(timeFormat);

                // Timestamp
                styledDoc.insertString(styledDoc.getLength(), timestamp + " ", timestampStyle);

                // Level tag
                Style levelStyle = switch (level.toUpperCase()) {
                    case "WARN", "WARNING" -> warnStyle;
                    case "ERROR" -> errorStyle;
                    case "DEBUG" -> debugStyle;
                    case "MT5" -> mt5Style;
                    default -> infoStyle;
                };

                styledDoc.insertString(styledDoc.getLength(),
                        String.format("[%-5s] ", level.toUpperCase()), levelStyle);

                // Message (use MT5 style for MT5 prefixed messages)
                Style msgStyle = message.startsWith("[MT5]") ? mt5Style : levelStyle;
                styledDoc.insertString(styledDoc.getLength(), message + "\n", msgStyle);

                // Auto-scroll to bottom
                logTextPane.setCaretPosition(styledDoc.getLength());

                // Limit log size (keep last 5000 lines)
                Element root = styledDoc.getDefaultRootElement();
                if (root.getElementCount() > 5000) {
                    int end = root.getElement(500).getEndOffset();
                    styledDoc.remove(0, end);
                }

            } catch (BadLocationException e) {
                // Ignore
            }
        });
    }

    /**
     * Convenience method for logging from the backtest engine.
     * Auto-detects level from message prefix.
     */
    public void logMessage(String message) {
        String level = "INFO";
        if (message.contains("ERROR")) level = "ERROR";
        else if (message.contains("WARNING") || message.contains("WARN")) level = "WARN";
        else if (message.contains("[MT5]")) level = "MT5";
        log(level, message);
    }

    public void clearLog() {
        try {
            styledDoc.remove(0, styledDoc.getLength());
        } catch (BadLocationException e) {
            // Ignore
        }
    }
}
