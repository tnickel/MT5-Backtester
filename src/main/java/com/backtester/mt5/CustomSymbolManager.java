package com.backtester.mt5;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/**
 * Manages Custom Symbols created in MT5 from Dukascopy data.
 * Tracks which symbols exist and their data ranges.
 */
public class CustomSymbolManager {

    private static final Logger log = LoggerFactory.getLogger(CustomSymbolManager.class);
    private static final String SYMBOLS_FILE = "symbols.json";

    private final Path configDir;
    private final Map<String, SymbolInfo> symbols;
    private final Gson gson;

    public CustomSymbolManager(Path configDir) {
        this.configDir = configDir;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.symbols = loadSymbols();
    }

    /**
     * Information about a custom symbol.
     */
    public static class SymbolInfo {
        public String customName;
        public String originName;
        public String dataFrom;
        public String dataTo;
        public int digits;
        public long barCount;
        public String lastUpdated;

        public SymbolInfo() {}

        public SymbolInfo(String customName, String originName, LocalDate from, LocalDate to,
                         int digits, long barCount) {
            this.customName = customName;
            this.originName = originName;
            this.dataFrom = from.toString();
            this.dataTo = to.toString();
            this.digits = digits;
            this.barCount = barCount;
            this.lastUpdated = java.time.LocalDateTime.now().toString();
        }
    }

    /**
     * Register a custom symbol.
     */
    public void registerSymbol(String customName, String originName, LocalDate from, LocalDate to,
                                int digits, long barCount) {
        symbols.put(customName, new SymbolInfo(customName, originName, from, to, digits, barCount));
        saveSymbols();
        log.info("Registered custom symbol: {} ({})", customName, originName);
    }

    /**
     * Remove a custom symbol registration.
     */
    public void removeSymbol(String customName) {
        symbols.remove(customName);
        saveSymbols();
    }

    /**
     * Get all registered symbols.
     */
    public Map<String, SymbolInfo> getSymbols() {
        return Collections.unmodifiableMap(symbols);
    }

    /**
     * Get info for a specific symbol.
     */
    public SymbolInfo getSymbolInfo(String customName) {
        return symbols.get(customName);
    }

    /**
     * Check if a symbol is registered.
     */
    public boolean hasSymbol(String customName) {
        return symbols.containsKey(customName);
    }

    /**
     * Generate a custom symbol name.
     */
    public static String toCustomName(String symbol) {
        return symbol.toUpperCase() + "_Duka";
    }

    private Map<String, SymbolInfo> loadSymbols() {
        Path file = configDir.resolve(SYMBOLS_FILE);
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                Map<String, SymbolInfo> loaded = gson.fromJson(reader,
                        new TypeToken<Map<String, SymbolInfo>>(){}.getType());
                if (loaded != null) {
                    log.info("Loaded {} custom symbols from config", loaded.size());
                    return loaded;
                }
            } catch (Exception e) {
                log.warn("Failed to load symbols config, starting fresh", e);
            }
        }
        return new HashMap<>();
    }

    private void saveSymbols() {
        try {
            Files.createDirectories(configDir);
            Path file = configDir.resolve(SYMBOLS_FILE);
            try (Writer writer = Files.newBufferedWriter(file)) {
                gson.toJson(symbols, writer);
            }
        } catch (IOException e) {
            log.error("Failed to save symbols config", e);
        }
    }
}
