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

        // Launch JavaFX UI
        com.backtester.ui.javafx.AppLauncher.main(args);
    }
}
