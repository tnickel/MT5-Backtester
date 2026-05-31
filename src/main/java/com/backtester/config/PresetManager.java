package com.backtester.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class PresetManager {
    private static final Logger log = LoggerFactory.getLogger(PresetManager.class);
    private static final String PRESETS_FILE = "config/presets.json";
    private static PresetManager instance;
    private final List<Preset> presets = new ArrayList<>();
    private final Path presetsPath;

    private PresetManager() {
        presetsPath = Paths.get(System.getProperty("user.dir")).resolve(PRESETS_FILE);
        loadPresets();
    }

    public static synchronized PresetManager getInstance() {
        if (instance == null) {
            instance = new PresetManager();
        }
        return instance;
    }

    public List<Preset> getPresets() {
        return presets;
    }

    public void loadPresets() {
        presets.clear();
        if (Files.exists(presetsPath)) {
            try (Reader reader = Files.newBufferedReader(presetsPath, StandardCharsets.UTF_8)) {
                Gson gson = new Gson();
                List<Preset> loaded = gson.fromJson(reader, new TypeToken<List<Preset>>() {}.getType());
                if (loaded != null) {
                    presets.addAll(loaded);
                }
                log.info("Loaded {} presets from {}", presets.size(), presetsPath);
            } catch (Exception e) {
                log.error("Failed to load presets from {}", presetsPath, e);
            }
        }
        
        // Add default Set 1 if empty
        if (presets.isEmpty()) {
            presets.add(new Preset(
                "Set 1 (M5 Core Pairs)",
                "Market\\Scalper Deriv",
                "AUDJPY,AUDUSD,EURAUD,EURCHF,EURGBP,EURJPY,EURUSD,GBPCHF,GBPJPY,GBPUSD,NZDUSD,USDCAD,USDCHF,USDJPY",
                "M5"
            ));
            savePresets();
        }
    }

    public void savePresets() {
        try {
            Files.createDirectories(presetsPath.getParent());
            try (Writer writer = Files.newBufferedWriter(presetsPath, StandardCharsets.UTF_8)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(presets, writer);
                log.info("Saved presets to {}", presetsPath);
            }
        } catch (Exception e) {
            log.error("Failed to save presets to {}", presetsPath, e);
        }
    }

    public void addPreset(Preset preset) {
        presets.add(preset);
        savePresets();
    }

    public void removePreset(Preset preset) {
        presets.remove(preset);
        savePresets();
    }
}
