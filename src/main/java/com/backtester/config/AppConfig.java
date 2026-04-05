package com.backtester.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/**
 * Application configuration manager.
 * Loads/saves properties from config/backtester.properties.
 * Thread-safe singleton.
 */
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);
    private static final String CONFIG_DIR = "config";
    private static final String CONFIG_FILE = "backtester.properties";
    private static final String DATA_DIR = "data";
    private static final String REPORTS_DIR = "backtest_reports";

    private static AppConfig instance;
    private final Properties properties;
    private final Path configPath;
    private final Path basePath;

    private AppConfig() {
        // Determine base path (where the JAR/project root is)
        basePath = Paths.get(System.getProperty("user.dir"));
        configPath = basePath.resolve(CONFIG_DIR).resolve(CONFIG_FILE);
        properties = new Properties();

        // Load defaults from classpath
        try (InputStream defaults = getClass().getResourceAsStream("/default.properties")) {
            if (defaults != null) {
                properties.load(defaults);
            }
        } catch (IOException e) {
            log.warn("Could not load default properties", e);
        }

        // Override with user config if exists
        if (Files.exists(configPath)) {
            try (InputStream fis = Files.newInputStream(configPath)) {
                properties.load(fis);
                log.info("Configuration loaded from {}", configPath);
            } catch (IOException e) {
                log.error("Error loading configuration from {}", configPath, e);
            }
        } else {
            log.info("No user config found at {}, using defaults", configPath);
            save(); // Create initial config file
        }

        // Ensure directories exist
        ensureDirectories();
    }

    public static synchronized AppConfig getInstance() {
        if (instance == null) {
            instance = new AppConfig();
        }
        return instance;
    }

    private void ensureDirectories() {
        try {
            Files.createDirectories(basePath.resolve(CONFIG_DIR));
            Files.createDirectories(getDataDirectory());
            Files.createDirectories(getReportsDirectory());
            log.info("Directories verified: config/, data/, backtest_reports/");
        } catch (IOException e) {
            log.error("Failed to create directories", e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(configPath.getParent());
            try (OutputStream os = Files.newOutputStream(configPath)) {
                properties.store(os, "MT5 Backtester Configuration");
                log.info("Configuration saved to {}", configPath);
            }
        } catch (IOException e) {
            log.error("Failed to save configuration", e);
        }
    }

    // --- Getters & Setters ---

    public String get(String key) {
        return properties.getProperty(key, "");
    }

    public String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public void set(String key, String value) {
        properties.setProperty(key, value);
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(properties.getProperty(key, String.valueOf(defaultValue)));
    }

    // --- Convenience methods ---

    public String getMt5TerminalPath() {
        return get("mt5.terminal.path", "C:\\Program Files\\MetaTrader 5\\terminal64.exe");
    }

    public void setMt5TerminalPath(String path) {
        set("mt5.terminal.path", path);
    }

    public Path getDataDirectory() {
        String dir = get("data.directory", DATA_DIR);
        Path p = Paths.get(dir);
        return p.isAbsolute() ? p : basePath.resolve(p);
    }

    public void setDataDirectory(String path) {
        set("data.directory", path);
    }

    public Path getReportsDirectory() {
        String dir = get("output.directory", REPORTS_DIR);
        Path p = Paths.get(dir);
        return p.isAbsolute() ? p : basePath.resolve(p);
    }

    public void setReportsDirectory(String path) {
        set("output.directory", path);
    }

    public boolean isPortableMode() {
        return getBoolean("mt5.portable.mode", true);
    }

    public int getDefaultDeposit() {
        return getInt("backtest.deposit", 10000);
    }

    public String getDefaultCurrency() {
        return get("backtest.currency", "USD");
    }

    public String getDefaultLeverage() {
        return get("backtest.leverage", "1:100");
    }

    public int getDefaultModel() {
        return getInt("backtest.model", 0);
    }

    public int getBrokerTimezoneOffset() {
        return getInt("broker.timezone.offset", 2);
    }

    public Path getBasePath() {
        return basePath;
    }

    /**
     * Returns the MT5 installation directory (parent of terminal64.exe)
     */
    public Path getMt5InstallDir() {
        return Paths.get(getMt5TerminalPath()).getParent();
    }
}
