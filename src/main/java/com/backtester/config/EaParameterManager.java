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
        if (name.toLowerCase().endsWith(".ex5") || name.toLowerCase().endsWith(".ex4")) {
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
        Path mtDir = appConfig.getMtInstallDir(expertPath);
        if (mtDir == null) return null;

        Path testerProfileDir = appConfig.getTesterProfilesDir(expertPath);
        Path setFile = testerProfileDir.resolve(eaName + ".set");

        List<EaParameter> params = null;
        if (Files.exists(setFile)) {
            log.info("Loading default parameters from: {}", setFile);
            params = readSetFile(setFile);
        } else if (appConfig.isMt4(expertPath)) {
            Path iniFile = testerProfileDir.resolve(eaName + ".ini");
            if (Files.exists(iniFile)) {
                log.info("Loading default parameters from MT4 .ini: {}", iniFile);
                params = readSetFile(iniFile);
            }
        }

        if (params != null) {
            applyTranslations(expertPath, params);
            return params;
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
            List<EaParameter> params = readSetFile(customFile);
            applyTranslations(expertPath, params);
            return params;
        }

        return null;
    }

    /**
     * Applies translations from the database or translates them on the fly.
     */
    public void applyTranslations(String expertPath, List<EaParameter> params) {
        if (params == null || params.isEmpty()) return;
        for (EaParameter param : params) {
            param.setDisplayName(param.getName());
        }
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

        List<EaParameter> result = null;
        if (custom != null && defaults != null) {
            // Merge: use custom values but keep default values for comparison.
            // Also ensure default parameters missing from custom list are preserved.
            Map<String, EaParameter> customMap = new LinkedHashMap<>();
            for (EaParameter cp : custom) {
                customMap.put(cp.getName(), cp);
            }
            List<EaParameter> merged = new ArrayList<>();
            for (EaParameter dp : defaults) {
                EaParameter cp = customMap.remove(dp.getName());
                if (cp != null) {
                    cp.setDefaultValue(dp.getValue());
                    merged.add(cp);
                } else {
                    EaParameter cpMissing = new EaParameter();
                    cpMissing.setName(dp.getName());
                    cpMissing.setValue(dp.getValue());
                    cpMissing.setDefaultValue(dp.getValue());
                    cpMissing.setSection(dp.getSection());
                    cpMissing.setOptimizeStart(dp.getOptimizeStart());
                    cpMissing.setOptimizeStep(dp.getOptimizeStep());
                    cpMissing.setOptimizeEnd(dp.getOptimizeEnd());
                    cpMissing.setOptimizeEnabled(dp.isOptimizeEnabled());
                    cpMissing.setStringType(dp.isStringType());
                    cpMissing.setRawLine(dp.getRawLine());
                    merged.add(cpMissing);
                }
            }
            // Append any custom parameters that were not in defaults
            for (EaParameter cp : customMap.values()) {
                merged.add(cp);
            }
            result = merged;
        } else if (custom != null) {
            result = custom;
        } else if (defaults != null) {
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
            result = copy;
        }

        if (result != null) {
            applyTranslations(expertPath, result);
        }
        return result;
    }

    /**
     * Saves custom parameters for an EA to config/ea_params/.
     */
    public void saveCustomParameters(String expertPath, List<EaParameter> params) {
        String eaName = extractEaBaseName(expertPath);
        Path customFile = getCustomParamsDir().resolve(eaName + ".set");

        writeSetFile(customFile, params, expertPath);
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
        Path mtDir = appConfig.getMtInstallDir(expertPath);
        if (mtDir == null) return false;
        if (appConfig.isMt4(expertPath)) {
            Path testerDir = mtDir.resolve("tester");
            return Files.exists(testerDir.resolve(eaName + ".set")) || Files.exists(testerDir.resolve(eaName + ".ini"));
        } else {
            Path setFile = mtDir.resolve("MQL5").resolve("Profiles").resolve("Tester").resolve(eaName + ".set");
            return Files.exists(setFile);
        }
    }

    /**
     * Generates a default .set file by briefly starting MT4/MT5 with a minimal backtest.
     * 
     * @return true if the .set/.ini file was generated successfully
     */
    public boolean generateDefaultConfig(String expertPath) {
        String eaName = extractEaBaseName(expertPath);
        String terminalPath = appConfig.getTerminalPath(expertPath);

        if (!Files.exists(Paths.get(terminalPath))) {
            log.error("Terminal not found at: {}", terminalPath);
            return false;
        }

        Path mt5Dir = Paths.get(terminalPath).getParent();
        String processName = appConfig.isMt4(expertPath) ? "terminal" : "terminal64";
        killExistingTerminalProcesses(mt5Dir, processName);

        Path testerDir = appConfig.isMt4(expertPath) ? mt5Dir.resolve("tester") : mt5Dir.resolve("MQL5").resolve("Profiles").resolve("Tester");
        Path expectedSetFile = appConfig.isMt4(expertPath) ? testerDir.resolve(eaName + ".ini") : testerDir.resolve(eaName + ".set");

        log.info("Generating default config for '{}' by starting terminal briefly...", eaName);

        Path tempIni = null;
        Path destIni = null;
        Path destConfigIni = null;
        try {
            // Create a minimal tester.ini that will make terminal open the tester for this EA,
            // then immediately shutdown. The EA just needs to be loaded so terminal writes the config file.
            tempIni = Files.createTempFile("genconfig_", ".ini");
            try (Writer writer = Files.newBufferedWriter(tempIni, StandardCharsets.UTF_8)) {
                writer.write("[Tester]\r\n");
                if (appConfig.isMt4(expertPath)) {
                    writer.write("TestExpert=" + extractEaBaseName(expertPath) + "\r\n");
                    writer.write("TestSymbol=EURUSD\r\n");
                    writer.write("TestPeriod=H1\r\n");
                    writer.write("TestModel=2\r\n");  // Open price only
                    writer.write("TestDateEnable=true\r\n");
                    writer.write("TestFromDate=2025.01.01\r\n");
                    writer.write("TestToDate=2025.01.02\r\n");
                    writer.write("TestOptimization=false\r\n");
                    writer.write("TestReport=_genconfig_temp\r\n");
                    writer.write("TestReplaceReport=true\r\n");
                    writer.write("TestShutdownTerminal=true\r\n");
                    writer.write("TestVisualEnable=false\r\n");
                } else {
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
            }

            boolean isMt4 = appConfig.isMt4(expertPath);
            destIni = mt5Dir.resolve("tester_genconfig.ini");
            Files.copy(tempIni, destIni, StandardCopyOption.REPLACE_EXISTING);

            destConfigIni = mt5Dir.resolve("config").resolve("tester_genconfig.ini");
            if (isMt4) {
                try {
                    Files.createDirectories(destConfigIni.getParent());
                    Files.copy(tempIni, destConfigIni, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    log.warn("Failed to copy tempIni to config/ directory", e);
                }
            }

            // Start terminal with this config on Desktop 2
            java.util.List<String> mt5Args = new java.util.ArrayList<>();
            if (appConfig.isPortableMode()) {
                mt5Args.add("/portable");
            }
            if (isMt4) {
                mt5Args.add("config\\tester_genconfig.ini");
            } else {
                mt5Args.add("/config:tester_genconfig.ini");
            }

            log.info("Starting terminal to generate default config...");
            Process process = com.backtester.engine.VirtualDesktopHelper.startOnDesktop2(terminalPath, mt5Args, mt5Dir);

            // Consume output to prevent deadlock
            Thread outputConsumer = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    while (reader.readLine() != null) { /* discard */ }
                } catch (IOException ignored) {}
            }, "Config-Gen-Output");
            outputConsumer.setDaemon(true);
            outputConsumer.start();

            // Wait up to 90 seconds for terminal to complete
            boolean finished = process.waitFor(90, TimeUnit.SECONDS);
            if (!finished) {
                log.warn("Terminal did not finish within 90 seconds, killing process...");
                process.destroyForcibly();
            }

            // Check if expected file was created
            if (Files.exists(expectedSetFile)) {
                log.info("Default config generated successfully: {}", expectedSetFile);
                return true;
            } else {
                log.warn("Terminal finished but no config file was created at: {}", expectedSetFile);
                return false;
            }

        } catch (Exception e) {
            log.error("Failed to generate default config for " + eaName, e);
            return false;
        } finally {
            // Clean up temp files
            if (tempIni != null) {
                try { Files.deleteIfExists(tempIni); } catch (IOException ignored) {}
            }
            if (destIni != null) {
                try { Files.deleteIfExists(destIni); } catch (IOException ignored) {}
            }
            if (destConfigIni != null) {
                try { Files.deleteIfExists(destConfigIni); } catch (IOException ignored) {}
            }
            // Clean up any temp report
            try {
                Path tempReport = mt5Dir.resolve("_genconfig_temp.xml");
                Files.deleteIfExists(tempReport);
                tempReport = mt5Dir.resolve("_genconfig_temp.htm");
                Files.deleteIfExists(tempReport);
                tempReport = mt5Dir.resolve("tester").resolve("Reports").resolve("_genconfig_temp.htm");
                Files.deleteIfExists(tempReport);
            } catch (IOException ignored) {}
        }
    }

    private void killExistingTerminalProcesses(Path mtDir, String processName) {
        try {
            String searchPath = mtDir.toAbsolutePath().toString().replace("'", "''");
            ProcessBuilder killPb = new ProcessBuilder(
                "powershell", "-NoProfile", "-Command",
                "$procs = Get-Process " + processName + " -ErrorAction SilentlyContinue | " +
                "Where-Object { $_.Path -and $_.Path.StartsWith('" + searchPath + "') }; " +
                "if ($procs) { $procs | Stop-Process -Force; Start-Sleep -Milliseconds 500 }"
            );
            killPb.redirectErrorStream(true);
            Process killProc = killPb.start();
            killProc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to check or kill existing terminal processes", e);
        }
    }

    /**
     * Prepares the .set file for a backtest run.
     * 
     * @return the filename for ExpertParameters, or null if no config exists
     */
    public String prepareForBacktest(String expertPath) {
        String eaName = extractEaBaseName(expertPath);
        Path mtDir = appConfig.getMtInstallDir(expertPath);
        if (mtDir == null) return null;

        Path presetsDir = appConfig.getTesterProfilesDir(expertPath);
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
            Path defaultFile = presetsDir.resolve(eaName + ".set");
            if (Files.exists(defaultFile)) {
                sourceFile = defaultFile;
                configType = "default";
            } else if (appConfig.isMt4(expertPath)) {
                Path defaultIni = presetsDir.resolve(eaName + ".ini");
                if (Files.exists(defaultIni)) {
                    sourceFile = defaultIni;
                    configType = "default_ini";
                }
            }
        }

        if (sourceFile == null) {
            log.info("No config file available for '{}'. EA will use its compiled defaults.", eaName);
            return null;
        }

        // Copy to Tester directory with a backtester-specific name
        String presetFileName = "Backtester_" + eaName + ".set";
        Path destFile = presetsDir.resolve(presetFileName);
        try {
            Files.copy(sourceFile, destFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("Prepared {} config for backtest: {} -> {}", configType, sourceFile, destFile);
            return presetFileName;
        } catch (IOException e) {
            log.error("Failed to copy config file to Tester directory", e);
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
                String val = p.getValue() != null ? p.getValue().toLowerCase() : "";
                if ("true".equals(val) || "false".equals(val)) {
                    // Boolean params have 2 states: false and true
                    total *= 2;
                    continue;
                }
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
        Map<String, EaParameter> paramMap = new LinkedHashMap<>();
        String currentSection = "";
        boolean isIni = setFile.toString().toLowerCase().endsWith(".ini");
        boolean insideInputsBlock = !isIni;

        try {
            // Try to read with UTF-16 LE first (MT5/MT4 standard)
            List<String> lines = tryReadLines(setFile);

            for (String line : lines) {
                line = line.trim();

                // Skip empty lines
                if (line.isEmpty()) continue;

                // Handle BOM
                if (line.charAt(0) == '\uFEFF') {
                    line = line.substring(1);
                }

                // Check for XML/ini blocks
                if (line.equalsIgnoreCase("<inputs>")) {
                    insideInputsBlock = true;
                    continue;
                } else if (line.startsWith("<") && line.endsWith(">")) {
                    insideInputsBlock = false;
                    continue;
                }

                if (!insideInputsBlock) continue;

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

                // Check for MT4 parameter suffixes (e.g. Lots,F=1)
                if (name.contains(",")) {
                    int commaIdx = name.indexOf(',');
                    String baseName = name.substring(0, commaIdx).trim();
                    String suffix = name.substring(commaIdx + 1).trim().toLowerCase();

                    EaParameter param = paramMap.get(baseName);
                    if (param == null) {
                        param = new EaParameter();
                        param.setName(baseName);
                        param.setSection(currentSection);
                        paramMap.put(baseName, param);
                        params.add(param);
                    }

                    if ("f".equals(suffix)) {
                        param.setOptimizeEnabled("1".equals(rest.trim()));
                        param.setStringType(false);
                    } else if ("start".equals(suffix) || "1".equals(suffix)) {
                        param.setOptimizeStart(rest.trim());
                        param.setStringType(false);
                    } else if ("step".equals(suffix) || "2".equals(suffix)) {
                        param.setOptimizeStep(rest.trim());
                        param.setStringType(false);
                    } else if ("stop".equals(suffix) || "3".equals(suffix)) {
                        param.setOptimizeEnd(rest.trim());
                        param.setStringType(false);
                    }
                    continue;
                }

                // Standard parameter name=value
                EaParameter param = paramMap.get(name);
                if (param == null) {
                    param = new EaParameter();
                    param.setName(name);
                    param.setSection(currentSection);
                    paramMap.put(name, param);
                    params.add(param);
                }

                if (rest.contains("||")) {
                    // Numeric/bool parameter with optimization fields (MT5 format)
                    String[] parts = rest.split("\\|\\|");
                    param.setValue(parts[0].trim());
                    param.setDefaultValue(parts[0].trim());
                    if (parts.length > 1) param.setOptimizeStart(parts[1].trim());
                    if (parts.length > 2) param.setOptimizeStep(parts[2].trim());
                    if (parts.length > 3) param.setOptimizeEnd(parts[3].trim());
                    if (parts.length > 4) param.setOptimizeEnabled("Y".equalsIgnoreCase(parts[4].trim()));
                    param.setStringType(false);
                } else {
                    // String parameter or simple value
                    param.setValue(rest);
                    param.setDefaultValue(rest);
                    // Default to stringType = true, but if we parsed suffixes first, preserve stringType = false
                    if (param.getOptimizeStart().isEmpty() && !param.isOptimizeEnabled()) {
                        param.setStringType(true);
                    }
                }
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
            // No BOM - check for UTF-16 LE without BOM (lots of null bytes for standard text)
            boolean hasNulls = false;
            for (byte b : bytes) {
                if (b == 0) {
                    hasNulls = true;
                    break;
                }
            }
            if (hasNulls) {
                String content = new String(bytes, MT5_CHARSET);
                return Arrays.asList(content.split("\\r?\\n"));
            }
            // No BOM and no nulls - try UTF-8
            String content = new String(bytes, StandardCharsets.UTF_8);
            return Arrays.asList(content.split("\\r?\\n"));
        } catch (Exception e) {
            // Fallback
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        }
    }

    /**
     * Writes parameters to a .set file in UTF-16 LE format.
     */
    public void writeSetFile(Path setFile, List<EaParameter> params, String expertPathOrEaName) {
        try {
            Files.createDirectories(setFile.getParent());

            try (OutputStream os = Files.newOutputStream(setFile);
                 Writer writer = new OutputStreamWriter(os, MT5_CHARSET)) {
                
                // Write UTF-16 LE BOM
                os.write(new byte[]{(byte) 0xFF, (byte) 0xFE});

                String eaName = extractEaBaseName(expertPathOrEaName);

                // Header comment
                writer.write("; saved by MT5 Backtester on " + 
                        java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss")) + "\r\n");
                writer.write("; custom parameters for " + eaName + " expert advisor\r\n");
                writer.write(";\r\n");

                boolean isMt4 = appConfig.isMt4(expertPathOrEaName);
                String lastSection = "";
                for (EaParameter param : params) {
                    // Write section header if changed
                    if (param.getSection() != null && !param.getSection().isEmpty() 
                            && !param.getSection().equals(lastSection)) {
                        lastSection = param.getSection();
                        writer.write("; === " + lastSection + " ===\r\n");
                    }

                    if (isMt4) {
                        writer.write(param.getName() + "=" + param.getValue() + "\r\n");
                        if (!param.isStringType()) {
                            writer.write(param.getName() + ",F=" + (param.isOptimizeEnabled() ? "1" : "0") + "\r\n");
                            writer.write(param.getName() + ",1=" + param.getOptimizeStart() + "\r\n");
                            writer.write(param.getName() + ",2=" + param.getOptimizeStep() + "\r\n");
                            writer.write(param.getName() + ",3=" + param.getOptimizeEnd() + "\r\n");
                        }
                    } else {
                        writer.write(param.toSetFileLine() + "\r\n");
                    }
                }
            }

            log.info("Wrote .set file: {}", setFile);

        } catch (IOException e) {
            log.error("Failed to write .set file: " + setFile, e);
        }
    }
}
