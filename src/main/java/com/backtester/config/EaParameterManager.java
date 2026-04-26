package com.backtester.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Manages EA input parameters (.set files) for backtesting.
 * 
 * Responsibilities:
 * - Read/write MT5 .set files (UTF-16 LE format)
 * - Load default configs from MQL5/Profiles/Tester/
 * - Store custom configs in config/ea_params/
 * - Generate default configs by briefly starting MT5 if none exist
 * - Compare custom vs default values
 */
public class EaParameterManager {

    private static final Logger log = LoggerFactory.getLogger(EaParameterManager.class);
    private static final String EA_PARAMS_DIR = "ea_params";
    private static final Charset MT5_CHARSET = StandardCharsets.UTF_16LE;

    private final AppConfig appConfig;

    /** Cache of loaded parameters per EA name */
    private final Map<String, List<EaParameter>> customParamsCache = new HashMap<>();

    public EaParameterManager() {
        this.appConfig = AppConfig.getInstance();
        ensureParamsDirectory();
    }

    private void ensureParamsDirectory() {
        try {
            Files.createDirectories(getCustomParamsDir());
        } catch (IOException e) {
            log.error("Failed to create ea_params directory", e);
        }
    }

    /**
     * Returns the directory where custom EA parameter configs are stored.
     */
    public Path getCustomParamsDir() {
        return appConfig.getBasePath().resolve("config").resolve(EA_PARAMS_DIR);
    }

    /**
     * Extracts the EA base name from an expert path.
     * e.g. "MyEAs\\MyRobot" -> "MyRobot"
     *      "CC_ADR_Stoch_Grid_with_Buttons" -> "CC_ADR_Stoch_Grid_with_Buttons"
     */
    public static String extractEaBaseName(String expertPath) {
        if (expertPath == null || expertPath.isEmpty()) return "";
        String name = expertPath;
        if (name.contains("\\")) {
            name = name.substring(name.lastIndexOf('\\') + 1);
        }
        if (name.contains("/")) {
            name = name.substring(name.lastIndexOf('/') + 1);
        }
        if (name.toLowerCase().endsWith(".ex5")) {
            name = name.substring(0, name.length() - 4);
        }
        return name;
    }

    /**
     * Loads the default .set file for an EA from MQL5/Profiles/Tester/.
     * Returns null if no .set file exists.
     */
    public List<EaParameter> loadDefaultParameters(String expertPath) {
        String eaName = extractEaBaseName(expertPath);
        Path mt5Dir = appConfig.getMt5InstallDir();
        if (mt5Dir == null) return null;

        Path testerProfileDir = mt5Dir.resolve("MQL5").resolve("Profiles").resolve("Tester");
        Path setFile = testerProfileDir.resolve(eaName + ".set");

        if (Files.exists(setFile)) {
            log.info("Loading default parameters from: {}", setFile);
            return readSetFile(setFile);
        }

        log.info("No default .set file found for EA '{}' at: {}", eaName, setFile);
        return null;
    }

    /**
     * Loads the custom .set file for an EA from config/ea_params/.
     * Returns null if no custom config exists.
     */
    public List<EaParameter> loadCustomParameters(String expertPath) {
        String eaName = extractEaBaseName(expertPath);
        Path customFile = getCustomParamsDir().resolve(eaName + ".set");

        if (Files.exists(customFile)) {
            log.info("Loading custom parameters from: {}", customFile);
            return readSetFile(customFile);
        }

        return null;
    }

    /**
     * Gets the effective parameters for an EA.
     * Priority: Custom config > Default config > null
     * 
     * When custom config exists, it merges default values for the "default" column.
     */
    public List<EaParameter> getEffectiveParameters(String expertPath) {
        List<EaParameter> defaults = loadDefaultParameters(expertPath);
        List<EaParameter> custom = loadCustomParameters(expertPath);

        if (custom != null && defaults != null) {
            // Merge: use custom values but keep default values for comparison
            Map<String, EaParameter> defaultMap = new LinkedHashMap<>();
            for (EaParameter dp : defaults) {
                defaultMap.put(dp.getName(), dp);
            }
            for (EaParameter cp : custom) {
                EaParameter dp = defaultMap.get(cp.getName());
                if (dp != null) {
                    cp.setDefaultValue(dp.getValue());
                }
            }
            return custom;
        }

        if (custom != null) {
            return custom;
        }

        if (defaults != null) {
            // Return a copy of defaults (so editing doesn't affect the default source)
            List<EaParameter> copy = new ArrayList<>();
            for (EaParameter dp : defaults) {
                EaParameter cp = new EaParameter();
                cp.setName(dp.getName());
                cp.setValue(dp.getValue());
                cp.setDefaultValue(dp.getValue());
                cp.setSection(dp.getSection());
                cp.setOptimizeStart(dp.getOptimizeStart());
                cp.setOptimizeStep(dp.getOptimizeStep());
                cp.setOptimizeEnd(dp.getOptimizeEnd());
                cp.setOptimizeEnabled(dp.isOptimizeEnabled());
                cp.setStringType(dp.isStringType());
                cp.setRawLine(dp.getRawLine());
                copy.add(cp);
            }
            return copy;
        }

        return null;
    }

    /**
     * Saves custom parameters for an EA to config/ea_params/.
     */
    public void saveCustomParameters(String expertPath, List<EaParameter> params) {
        String eaName = extractEaBaseName(expertPath);
        Path customFile = getCustomParamsDir().resolve(eaName + ".set");

        writeSetFile(customFile, params, eaName);
        customParamsCache.put(eaName, params);
        log.info("Saved custom parameters for '{}' to: {}", eaName, customFile);
    }

    /**
     * Deletes custom parameters for an EA (reverts to default).
     */
    public void deleteCustomParameters(String expertPath) {
        String eaName = extractEaBaseName(expertPath);
        Path customFile = getCustomParamsDir().resolve(eaName + ".set");
        try {
            Files.deleteIfExists(customFile);
            customParamsCache.remove(eaName);
            log.info("Deleted custom parameters for '{}'", eaName);
        } catch (IOException e) {
            log.error("Failed to delete custom parameters for {}", eaName, e);
        }
    }

    /**
     * Checks if a custom config exists for an EA.
     */
    public boolean hasCustomConfig(String expertPath) {
        String eaName = extractEaBaseName(expertPath);
        return Files.exists(getCustomParamsDir().resolve(eaName + ".set"));
    }

    /**
     * Checks if default parameters exist for an EA.
     */
    public boolean hasDefaultConfig(String expertPath) {
        String eaName = extractEaBaseName(expertPath);
        Path mt5Dir = appConfig.getMt5InstallDir();
        if (mt5Dir == null) return false;
        Path setFile = mt5Dir.resolve("MQL5").resolve("Profiles").resolve("Tester").resolve(eaName + ".set");
        return Files.exists(setFile);
    }

    /**
     * Generates a default .set file by briefly starting MT5 with a minimal backtest.
     * MT5 automatically writes EaName.set to MQL5/Profiles/Tester/ when testing an EA.
     * 
     * @return true if the .set file was generated successfully
     */
    public boolean generateDefaultConfig(String expertPath) {
        String eaName = extractEaBaseName(expertPath);
        String terminalPath = appConfig.getMt5TerminalPath();

        if (!Files.exists(Paths.get(terminalPath))) {
            log.error("MT5 terminal not found at: {}", terminalPath);
            return false;
        }

        Path mt5Dir = Paths.get(terminalPath).getParent();
        Path testerDir = mt5Dir.resolve("MQL5").resolve("Profiles").resolve("Tester");
        Path expectedSetFile = testerDir.resolve(eaName + ".set");

        log.info("Generating default config for '{}' by starting MT5 briefly...", eaName);

        try {
            // Create a minimal tester.ini that will make MT5 open the tester for this EA,
            // then immediately shutdown. The EA just needs to be loaded so MT5 writes the .set file.
            Path tempIni = Files.createTempFile("mt5_genconfig_", ".ini");
            try (Writer writer = Files.newBufferedWriter(tempIni, StandardCharsets.UTF_8)) {
                writer.write("[Tester]\r\n");
                writer.write("Expert=" + expertPath + "\r\n");
                writer.write("Symbol=EURUSD\r\n");
                writer.write("Period=H1\r\n");
                writer.write("Model=2\r\n");  // Open price only (fastest)
                writer.write("ExecutionMode=0\r\n");
                writer.write("FromDate=2025.01.01\r\n");
                writer.write("ToDate=2025.01.02\r\n");  // Minimal period (1 day)
                writer.write("Deposit=10000\r\n");
                writer.write("Currency=USD\r\n");
                writer.write("Leverage=1:100\r\n");
                writer.write("Optimization=0\r\n");
                writer.write("Report=_genconfig_temp\r\n");
                writer.write("ReplaceReport=1\r\n");
                writer.write("ShutdownTerminal=1\r\n");
            }

            // Start MT5 with this config
            ProcessBuilder pb;
            if (appConfig.isPortableMode()) {
                pb = new ProcessBuilder(terminalPath, "/portable", "/config:" + tempIni.toAbsolutePath());
            } else {
                pb = new ProcessBuilder(terminalPath, "/config:" + tempIni.toAbsolutePath());
            }
            pb.redirectErrorStream(true);
            pb.directory(mt5Dir.toFile());

            log.info("Starting MT5 to generate default config...");
            Process process = pb.start();

            // Consume output to prevent deadlock
            Thread outputConsumer = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    while (reader.readLine() != null) { /* discard */ }
                } catch (IOException ignored) {}
            }, "MT5-Config-Gen-Output");
            outputConsumer.setDaemon(true);
            outputConsumer.start();

            // Wait up to 90 seconds for MT5 to complete
            boolean finished = process.waitFor(90, TimeUnit.SECONDS);
            if (!finished) {
                log.warn("MT5 did not finish within 90 seconds, killing process...");
                process.destroyForcibly();
            }

            // Clean up temp files
            Files.deleteIfExists(tempIni);
            // Clean up any temp report
            Path tempReport = mt5Dir.resolve("_genconfig_temp.xml");
            Files.deleteIfExists(tempReport);
            tempReport = mt5Dir.resolve("_genconfig_temp.htm");
            Files.deleteIfExists(tempReport);

            // Check if .set file was created
            if (Files.exists(expectedSetFile)) {
                log.info("Default config generated successfully: {}", expectedSetFile);
                return true;
            } else {
                log.warn("MT5 finished but no .set file was created at: {}", expectedSetFile);
                return false;
            }

        } catch (Exception e) {
            log.error("Failed to generate default config for " + eaName, e);
            return false;
        }
    }

    /**
     * Prepares the .set file for a backtest run.
     * Copies the effective config (custom or default) to MQL5/Presets/ and returns
     * the filename to use in ExpertParameters.
     * 
     * @return the filename for ExpertParameters, or null if no config exists
     */
    public String prepareForBacktest(String expertPath) {
        String eaName = extractEaBaseName(expertPath);
        Path mt5Dir = appConfig.getMt5InstallDir();
        if (mt5Dir == null) return null;

        Path presetsDir = mt5Dir.resolve("MQL5").resolve("Profiles").resolve("Tester");
        try {
            Files.createDirectories(presetsDir);
        } catch (IOException e) {
            log.error("Failed to create Tester directory", e);
            return null;
        }

        // Determine source: custom > default
        Path sourceFile = null;
        String configType = "none";

        Path customFile = getCustomParamsDir().resolve(eaName + ".set");
        if (Files.exists(customFile)) {
            sourceFile = customFile;
            configType = "custom";
        } else {
            Path defaultFile = mt5Dir.resolve("MQL5").resolve("Profiles").resolve("Tester").resolve(eaName + ".set");
            if (Files.exists(defaultFile)) {
                sourceFile = defaultFile;
                configType = "default";
            }
        }

        if (sourceFile == null) {
            log.info("No .set file available for '{}'. EA will use its compiled defaults.", eaName);
            return null;
        }

        // Copy to Presets directory with a backtester-specific name
        String presetFileName = "Backtester_" + eaName + ".set";
        Path destFile = presetsDir.resolve(presetFileName);
        try {
            Files.copy(sourceFile, destFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("Prepared {} config for backtest: {} -> {}", configType, sourceFile, destFile);
            return presetFileName;
        } catch (IOException e) {
            log.error("Failed to copy .set file to Presets", e);
            return null;
        }
    }

    /**
     * Counts how many parameters differ from default in a custom config.
     */
    public int countModifiedParameters(String expertPath) {
        List<EaParameter> params = getEffectiveParameters(expertPath);
        if (params == null) return 0;
        return (int) params.stream().filter(EaParameter::isModified).count();
    }

    /**
     * Calculates the total mathematically possible number of passes 
     * based on the active optimization parameters.
     */
    public long calculateTotalPasses(List<EaParameter> params) {
        if (params == null || params.isEmpty()) return 1;
        long total = 1;
        for (EaParameter p : params) {
            if (p.isOptimizeEnabled() && !p.isStringType()) {
                try {
                    double start = Double.parseDouble(p.getOptimizeStart());
                    double step = Double.parseDouble(p.getOptimizeStep());
                    double stop = Double.parseDouble(p.getOptimizeEnd());
                    if (step > 0 && stop >= start) {
                        long passes = (long) Math.floor((stop - start) / step) + 1;
                        if (passes > 0) {
                            total *= passes;
                        }
                    }
                } catch (Exception e) {
                    // Ignore parse errors, treat as 1 pass for this parameter
                }
            }
        }
        return total;
    }

    // ==================== .set File I/O ====================

    /**
     * Reads a .set file and returns the list of parameters.
     * Supports UTF-16 LE (standard MT5) with fallback to UTF-8.
     */
    public List<EaParameter> readSetFile(Path setFile) {
        List<EaParameter> params = new ArrayList<>();
        String currentSection = "";

        try {
            // Try to read with UTF-16 LE first (MT5 standard)
            List<String> lines = tryReadLines(setFile);

            for (String line : lines) {
                line = line.trim();

                // Skip empty lines
                if (line.isEmpty()) continue;

                // Handle BOM
                if (line.charAt(0) == '\uFEFF') {
                    line = line.substring(1);
                }

                // Comment / section header
                if (line.startsWith(";")) {
                    String comment = line.substring(1).trim();
                    // Check if it's a section header like "; === Section Name ==="
                    if (comment.startsWith("===") || comment.startsWith("---")) {
                        currentSection = comment
                                .replace("===", "")
                                .replace("---", "")
                                .trim();
                    }
                    continue;
                }

                // Parameter line
                int eqIdx = line.indexOf('=');
                if (eqIdx <= 0) continue;

                String name = line.substring(0, eqIdx).trim();
                String rest = line.substring(eqIdx + 1);

                EaParameter param = new EaParameter();
                param.setName(name);
                param.setSection(currentSection);
                param.setRawLine(line);

                if (rest.contains("||")) {
                    // Numeric/bool parameter with optimization fields
                    String[] parts = rest.split("\\|\\|");
                    param.setValue(parts[0].trim());
                    param.setDefaultValue(parts[0].trim());
                    if (parts.length > 1) param.setOptimizeStart(parts[1].trim());
                    if (parts.length > 2) param.setOptimizeStep(parts[2].trim());
                    if (parts.length > 3) param.setOptimizeEnd(parts[3].trim());
                    if (parts.length > 4) param.setOptimizeEnabled("Y".equalsIgnoreCase(parts[4].trim()));
                    param.setStringType(false);
                } else {
                    // String parameter
                    param.setValue(rest);
                    param.setDefaultValue(rest);
                    param.setStringType(true);
                }

                params.add(param);
            }

            log.info("Read {} parameters from: {}", params.size(), setFile);

        } catch (Exception e) {
            log.error("Failed to read .set file: " + setFile, e);
        }

        return params;
    }

    /**
     * Tries to read lines from a file, first as UTF-16 LE, then as UTF-8.
     */
    private List<String> tryReadLines(Path file) throws IOException {
        // First try UTF-16 LE (the standard MT5 format)
        try {
            byte[] bytes = Files.readAllBytes(file);
            // Check for UTF-16 LE BOM (FF FE)
            if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
                String content = new String(bytes, MT5_CHARSET);
                return Arrays.asList(content.split("\\r?\\n"));
            }
            // No BOM - try UTF-8
            String content = new String(bytes, StandardCharsets.UTF_8);
            return Arrays.asList(content.split("\\r?\\n"));
        } catch (Exception e) {
            // Fallback
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        }
    }

    /**
     * Writes parameters to a .set file in UTF-16 LE format (MT5 compatible).
     */
    public void writeSetFile(Path setFile, List<EaParameter> params, String eaName) {
        try {
            Files.createDirectories(setFile.getParent());

            try (OutputStream os = Files.newOutputStream(setFile);
                 Writer writer = new OutputStreamWriter(os, MT5_CHARSET)) {
                
                // Write UTF-16 LE BOM
                os.write(new byte[]{(byte) 0xFF, (byte) 0xFE});

                // Header comment
                writer.write("; saved by MT5 Backtester on " + 
                        java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss")) + "\r\n");
                writer.write("; custom parameters for " + eaName + " expert advisor\r\n");
                writer.write(";\r\n");

                String lastSection = "";
                for (EaParameter param : params) {
                    // Write section header if changed
                    if (param.getSection() != null && !param.getSection().isEmpty() 
                            && !param.getSection().equals(lastSection)) {
                        lastSection = param.getSection();
                        writer.write("; === " + lastSection + " ===\r\n");
                    }

                    writer.write(param.toSetFileLine() + "\r\n");
                }
            }

            log.info("Wrote .set file: {}", setFile);

        } catch (IOException e) {
            log.error("Failed to write .set file: " + setFile, e);
        }
    }
}
