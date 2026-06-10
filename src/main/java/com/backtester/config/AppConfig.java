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
    private static final String EXPORTS_DIR = "exports";

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
            Files.createDirectories(getExportDirectory());
            log.info("Directories verified: config/, data/, backtest_reports/, exports/");
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

    public String getMt4TerminalPath() {
        return get("mt4.terminal.path", "C:\\Program Files\\MetaTrader 4\\terminal.exe");
    }

    public void setMt4TerminalPath(String path) {
        set("mt4.terminal.path", path);
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

    public Path getExportDirectory() {
        String dir = get("export.directory", EXPORTS_DIR);
        Path p = Paths.get(dir);
        return p.isAbsolute() ? p : basePath.resolve(p);
    }

    public void setExportDirectory(String path) {
        set("export.directory", path);
    }

    public Path getBestExportDirectory() {
        // Default to a sibling of the export directory: "export gut"
        String defaultDir = getExportDirectory().resolveSibling("export gut").toAbsolutePath().toString();
        String dir = get("export.best.directory", defaultDir);
        Path p = Paths.get(dir);
        return p.isAbsolute() ? p : basePath.resolve(p);
    }

    public void setBestExportDirectory(String path) {
        set("export.best.directory", path);
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

    public int getBacktestTimeoutMinutes() {
        return getInt("backtest.timeout.minutes", 10);
    }

    public void setBacktestTimeoutMinutes(int minutes) {
        set("backtest.timeout.minutes", String.valueOf(minutes));
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

    /**
     * Returns true if the selected terminal is MetaTrader 4 (terminal.exe)
    /**
     * Returns the active platform configuration based on the EA version.
     */
    public MetaTraderPlatform getPlatform(String expertPath) {
        return MetaTraderPlatform.fromExpertPath(expertPath);
    }

    /**
     * Returns true if the default terminal is MetaTrader 4 (terminal.exe)
     */
    public boolean isMt4() {
        String path = getMt5TerminalPath().toLowerCase();
        return path.endsWith("terminal.exe") || path.contains("metatrader 4") || path.contains("mt4");
    }

    /**
     * Returns true if the expert EA points to an MT4 EA (.ex4)
     */
    public boolean isMt4(String expertPath) {
        return getPlatform(expertPath) == MetaTraderPlatform.MT4;
    }

    /**
     * Returns the terminal path based on the expert EA version.
     * For MT4 (.ex4), attempts to derive the terminal path from the expert's file path
     * by locating the MQL4 directory and resolving terminal.exe from its parent.
     * Falls back to the configured mt4.terminal.path if auto-detection fails.
     */
    public String getTerminalPath(String expertPath) {
        if (!isMt4(expertPath)) {
            return getMt5TerminalPath();
        }

        // Try to auto-detect MT4 terminal from expert path
        // Expert paths follow the pattern: <MT4_ROOT>/MQL4/Experts/[subdir/]robot.ex4
        if (expertPath != null && !expertPath.isEmpty()) {
            String normalized = expertPath.replace("/", "\\");
            String normalizedLower = normalized.toLowerCase();
            int mql4Idx = normalizedLower.indexOf("\\mql4\\");
            if (mql4Idx > 0) {
                String mt4Root = normalized.substring(0, mql4Idx);
                Path terminalCandidate = Paths.get(mt4Root, "terminal.exe");
                if (Files.exists(terminalCandidate)) {
                    log.info("Auto-detected MT4 terminal from expert path: {}", terminalCandidate);
                    // Also update the config so it's saved for future runs
                    setMt4TerminalPath(terminalCandidate.toString());
                    return terminalCandidate.toString();
                }
            }
        }

        // Fall back to configured path
        return getMt4TerminalPath();
    }

    /**
     * Returns the MetaTrader installation directory based on the expert EA version
     */
    public Path getMtInstallDir(String expertPath) {
        return Paths.get(getTerminalPath(expertPath)).getParent();
    }

    /**
     * Returns the MQL directory path (MQL4 for MT4, MQL5 for MT5)
     */
    public Path getMqlDir() {
        return getMqlDir(null);
    }

    public Path getMqlDir(String expertPath) {
        return getMtInstallDir(expertPath).resolve(getPlatform(expertPath).getMqlFolderName());
    }

    /**
     * Returns the Experts directory path (MQL4/Experts or MQL5/Experts)
     */
    public Path getExpertsDir() {
        return getExpertsDir(null);
    }

    public Path getExpertsDir(String expertPath) {
        return getMqlDir(expertPath).resolve("Experts");
    }

    /**
     * Returns the tester profiles / presets directory (tester/ for MT4, MQL5/Profiles/Tester/ for MT5)
     */
    public Path getTesterProfilesDir() {
        return getTesterProfilesDir(null);
    }

    public Path getTesterProfilesDir(String expertPath) {
        return getMtInstallDir(expertPath).resolve(getPlatform(expertPath).getPresetsFolderName());
    }
}
