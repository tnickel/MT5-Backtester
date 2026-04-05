package com.backtester;

import com.backtester.config.AppConfig;
import com.backtester.ui.MainFrame;
import com.formdev.flatlaf.FlatDarkLaf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;

/**
 * Application entry point.
 * Initializes FlatLaf dark theme and launches the main window.
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("=== MT5 Backtester Starting ===");

        // Initialize configuration
        AppConfig.getInstance();

        // Set up FlatLaf dark theme
        try {
            UIManager.put("Component.arc", 8);
            UIManager.put("Button.arc", 8);
            UIManager.put("TextComponent.arc", 6);
            UIManager.put("ScrollBar.width", 10);
            UIManager.put("TabbedPane.showTabSeparators", true);
            UIManager.put("Table.showHorizontalLines", true);
            UIManager.put("Table.showVerticalLines", false);

            FlatDarkLaf.setup();

            // Custom colors for a more modern look
            UIManager.put("Component.focusColor", new Color(78, 154, 241));
            UIManager.put("Component.focusedBorderColor", new Color(78, 154, 241));
            UIManager.put("Button.default.background", new Color(55, 90, 145));
            UIManager.put("Button.default.foreground", Color.WHITE);
            UIManager.put("TabbedPane.selectedBackground", new Color(45, 50, 60));
            UIManager.put("ProgressBar.foreground", new Color(78, 154, 241));

        } catch (Exception e) {
            log.warn("Failed to set FlatLaf theme, falling back to system L&F", e);
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                log.error("Failed to set any Look and Feel", ex);
            }
        }

        // Launch GUI on EDT
        SwingUtilities.invokeLater(() -> {
            try {
                MainFrame frame = new MainFrame();
                frame.setVisible(true);
                log.info("GUI launched successfully");
            } catch (Exception e) {
                log.error("Failed to launch GUI", e);
                JOptionPane.showMessageDialog(null,
                        "Failed to start application:\n" + e.getMessage(),
                        "MT5 Backtester - Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
