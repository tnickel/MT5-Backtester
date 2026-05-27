package com.backtester.engine;

import com.backtester.config.AppConfig;
import com.backtester.config.EaParameter;
import com.backtester.config.EaParameterManager;
import com.backtester.database.DatabaseManager;
import com.backtester.report.OptimizationResult;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.SensitivityResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;

/**
 * State machine and execution orchestrator for the automated multi-step trading strategy optimization pipeline.
 */
public class WorkflowEngine {
    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

    private final AppConfig config;
    private final EaParameterManager eaParamManager = new EaParameterManager();

    // Step 1 State
    private String expert = "";
    private String symbol = "EURUSD";
    private String period = "H1";
    private LocalDate fromDate = LocalDate.now().minusMonths(6);
    private LocalDate toDate = LocalDate.now();
    private int deposit = 10000;
    private String currency = "USD";
    private String leverage = "1:100";
    private int tickModel = 1; // 1 = Every tick
    private List<EaParameter> eaParameters = new ArrayList<>();

    // Step 2 State
    private int optimizationMode = 2; // Fast genetic
    private int optimizationCriterion = 4; // Recovery factor
    private int forwardMode = 1; // 1/2
    private LocalDate forwardDate = LocalDate.now().minusMonths(3);
    private OptimizationResult optResult;

    // Step 3 State (Diversity Filter)
    private double minBtProfit = 0.0;
    private double minFwProfit = 0.0;
    private int minBtTrades = 1;
    private int minFwTrades = 0;
    private double maxBtDd = 100.0;
    private double maxFwDd = 100.0;
    private double paramDiffPct = 0.10;
    private double tradeDiffPct = 0.15;
    private int minDifferentParams = 2;
    private int maxStrategiesToSelect = 5;
    private List<CombinedPass> selectedDiversePasses = new ArrayList<>();

    // Step 4 State
    private List<SensitivityResult> sensitivityResults = new ArrayList<>();

    // Step 5 State
    private String openRouterApiKey = "";
    private String openRouterModel = LlmAnalysisService.DEFAULT_MODEL;
    private String openRouterPrompt = LlmAnalysisService.DEFAULT_PROMPT;
    private String kiReportText = "";

    // Step 6 State
    private List<CombinedPass> finalSelectedPasses = new ArrayList<>();
    private int lastActiveStep = 0;

    // Pipeline Runners
    private OptimizationRunner currentOptRunner;
    private SensitivityRunner currentSensitivityRunner;

    public WorkflowEngine(AppConfig config) {
        this.config = config;
        loadPreferences();
        loadState(); // Restore state from database on startup
    }

    public void loadPreferences() {
        DatabaseManager db = DatabaseManager.getInstance();
        this.openRouterApiKey = db.getSetting(LlmAnalysisService.SETTING_API_KEY, "");
        this.openRouterModel = db.getSetting(LlmAnalysisService.SETTING_MODEL, LlmAnalysisService.DEFAULT_MODEL);
        this.openRouterPrompt = db.getSetting(LlmAnalysisService.SETTING_PROMPT, LlmAnalysisService.DEFAULT_PROMPT);

        // Load some defaults from global config if available
        if (config != null) {
            this.expert = config.get("optimizer.expert", "");
            this.symbol = config.get("optimizer.symbol", "EURUSD");
            this.period = config.get("optimizer.period", "H1");
            try {
                String dFrom = config.get("optimizer.dateFrom", "");
                if (!dFrom.isEmpty()) this.fromDate = LocalDate.parse(dFrom);
                String dTo = config.get("optimizer.dateTo", "");
                if (!dTo.isEmpty()) this.toDate = LocalDate.parse(dTo);
            } catch (Exception ignored) {}
            try {
                this.deposit = Integer.parseInt(config.get("optimizer.deposit", "10000"));
            } catch (Exception ignored) {}
            this.currency = config.get("optimizer.currency", "USD");
            this.leverage = config.get("optimizer.leverage", "1:100");
        }
    }

    public void savePreferences() {
        DatabaseManager db = DatabaseManager.getInstance();
        if (openRouterApiKey != null && !openRouterApiKey.isEmpty()) {
            db.saveSetting(LlmAnalysisService.SETTING_API_KEY, openRouterApiKey);
        }
        db.saveSetting(LlmAnalysisService.SETTING_MODEL, openRouterModel);
        db.saveSetting(LlmAnalysisService.SETTING_PROMPT, openRouterPrompt);
    }

    // --- State Serialization & Database Persistence ---

    private static com.google.gson.Gson buildGson() {
        return new com.google.gson.GsonBuilder()
                .registerTypeHierarchyAdapter(javafx.beans.property.StringProperty.class,
                        new com.google.gson.TypeAdapter<javafx.beans.property.StringProperty>() {
                            @Override
                            public void write(com.google.gson.stream.JsonWriter out, javafx.beans.property.StringProperty value) throws java.io.IOException {
                                if (value == null) out.nullValue();
                                else out.value(value.get());
                            }
                            @Override
                            public javafx.beans.property.StringProperty read(com.google.gson.stream.JsonReader in) throws java.io.IOException {
                                if (in.peek() == com.google.gson.stream.JsonToken.NULL) { in.nextNull(); return new javafx.beans.property.SimpleStringProperty(""); }
                                return new javafx.beans.property.SimpleStringProperty(in.nextString());
                            }
                        })
                .create();
    }

    public static class StrategyConfig {
        public String symbol;
        public String period;
        public String fromDate;
        public String toDate;
        public int deposit;
        public String currency;
        public String leverage;
        public int tickModel;
        public List<EaParameter> eaParameters;
        public int optimizationMode;
        public int optimizationCriterion;
        public int forwardMode;
        public String forwardDate;
        public double minBtProfit;
        public double minFwProfit;
        public int minBtTrades;
        public int minFwTrades;
        public double maxBtDd;
        public double maxFwDd;
        public double paramDiffPct;
        public double tradeDiffPct;
        public int minDifferentParams;
        public int maxStrategiesToSelect;
        public String openRouterApiKey;
        public String openRouterModel;
        public String openRouterPrompt;
    }

    public void changeExpert(String newExpert) {
        if (newExpert == null) newExpert = "";
        newExpert = newExpert.trim();
        if (newExpert.equals(this.expert)) {
            return;
        }

        // 1. Save current expert's config if set
        if (this.expert != null && !this.expert.isEmpty()) {
            saveStrategyConfig(this.expert);
        }

        this.expert = newExpert;

        // 2. Load the new expert's config or clear run results
        if (!this.expert.isEmpty()) {
            boolean loaded = loadStrategyConfig(this.expert);
            if (!loaded) {
                // If new, clear run results and save configuration immediately to register it
                clearRunResults();
                saveStrategyConfig(this.expert);
            } else {
                // If loaded existing config, clear run results since it's a different strategy
                clearRunResults();
            }
        } else {
            clearRunResults();
        }
    }

    private void clearRunResults() {
        this.optResult = null;
        this.selectedDiversePasses = new ArrayList<>();
        this.sensitivityResults = new ArrayList<>();
        this.kiReportText = "";
        this.finalSelectedPasses = new ArrayList<>();
        this.lastActiveStep = 0;
    }

    public void saveStrategyConfig(String expertName) {
        if (expertName == null || expertName.isEmpty()) return;
        try {
            StrategyConfig sc = new StrategyConfig();
            sc.symbol = this.symbol;
            sc.period = this.period;
            sc.fromDate = this.fromDate != null ? this.fromDate.toString() : null;
            sc.toDate = this.toDate != null ? this.toDate.toString() : null;
            sc.deposit = this.deposit;
            sc.currency = this.currency;
            sc.leverage = this.leverage;
            sc.tickModel = this.tickModel;
            sc.eaParameters = this.eaParameters;
            sc.optimizationMode = this.optimizationMode;
            sc.optimizationCriterion = this.optimizationCriterion;
            sc.forwardMode = this.forwardMode;
            sc.forwardDate = this.forwardDate != null ? this.forwardDate.toString() : null;
            sc.minBtProfit = this.minBtProfit;
            sc.minFwProfit = this.minFwProfit;
            sc.minBtTrades = this.minBtTrades;
            sc.minFwTrades = this.minFwTrades;
            sc.maxBtDd = this.maxBtDd;
            sc.maxFwDd = this.maxFwDd;
            sc.paramDiffPct = this.paramDiffPct;
            sc.tradeDiffPct = this.tradeDiffPct;
            sc.minDifferentParams = this.minDifferentParams;
            sc.maxStrategiesToSelect = this.maxStrategiesToSelect;
            sc.openRouterApiKey = this.openRouterApiKey;
            sc.openRouterModel = this.openRouterModel;
            sc.openRouterPrompt = this.openRouterPrompt;

            com.google.gson.Gson gson = buildGson();
            String json = gson.toJson(sc);
            DatabaseManager.getInstance().saveWorkflowStrategyConfig(expertName, json);
        } catch (Exception e) {
            log.error("Failed to save strategy configuration for " + expertName, e);
        }
    }

    public boolean loadStrategyConfig(String expertName) {
        if (expertName == null || expertName.isEmpty()) return false;
        try {
            String json = DatabaseManager.getInstance().getWorkflowStrategyConfig(expertName);
            if (json == null || json.isEmpty()) {
                return false;
            }

            com.google.gson.Gson gson = buildGson();
            StrategyConfig sc = gson.fromJson(json, StrategyConfig.class);
            if (sc == null) return false;

            this.symbol = sc.symbol != null ? sc.symbol : "EURUSD";
            this.period = sc.period != null ? sc.period : "H1";
            if (sc.fromDate != null) this.fromDate = LocalDate.parse(sc.fromDate);
            if (sc.toDate != null) this.toDate = LocalDate.parse(sc.toDate);
            this.deposit = sc.deposit;
            this.currency = sc.currency != null ? sc.currency : "USD";
            this.leverage = sc.leverage != null ? sc.leverage : "1:100";
            this.tickModel = sc.tickModel;
            this.eaParameters = sc.eaParameters != null ? sc.eaParameters : new ArrayList<>();
            this.optimizationMode = sc.optimizationMode;
            this.optimizationCriterion = sc.optimizationCriterion;
            this.forwardMode = sc.forwardMode;
            if (sc.forwardDate != null) this.forwardDate = LocalDate.parse(sc.forwardDate);
            this.minBtProfit = sc.minBtProfit;
            this.minFwProfit = sc.minFwProfit;
            this.minBtTrades = sc.minBtTrades;
            this.minFwTrades = sc.minFwTrades;
            this.maxBtDd = sc.maxBtDd;
            this.maxFwDd = sc.maxFwDd;
            this.paramDiffPct = sc.paramDiffPct;
            this.tradeDiffPct = sc.tradeDiffPct;
            this.minDifferentParams = sc.minDifferentParams;
            this.maxStrategiesToSelect = sc.maxStrategiesToSelect;
            this.openRouterApiKey = sc.openRouterApiKey != null ? sc.openRouterApiKey : "";
            this.openRouterModel = sc.openRouterModel != null ? sc.openRouterModel : LlmAnalysisService.DEFAULT_MODEL;
            this.openRouterPrompt = sc.openRouterPrompt != null ? sc.openRouterPrompt : LlmAnalysisService.DEFAULT_PROMPT;

            log.info("Successfully loaded strategy configuration for: {}", expertName);
            return true;
        } catch (Exception e) {
            log.error("Failed to load strategy configuration for " + expertName, e);
            return false;
        }
    }

    public void saveState() {
        try {
            if (expert != null && !expert.isEmpty()) {
                saveStrategyConfig(expert);
            }
            com.google.gson.Gson gson = buildGson();
            String eaParamsJson = gson.toJson(eaParameters);
            String optResultJson = gson.toJson(optResult);
            String selectedDiverseJson = gson.toJson(selectedDiversePasses);
            String sensitivityJson = gson.toJson(sensitivityResults);
            String finalSelectedJson = gson.toJson(finalSelectedPasses);

            DatabaseManager.getInstance().saveWorkflowState(
                expert,
                symbol,
                period,
                fromDate != null ? fromDate.toString() : null,
                toDate != null ? toDate.toString() : null,
                deposit,
                currency,
                leverage,
                tickModel,
                eaParamsJson,
                optResultJson,
                selectedDiverseJson,
                sensitivityJson,
                kiReportText,
                finalSelectedJson,
                lastActiveStep
            );
        } catch (Exception e) {
            log.error("Failed to save workflow state to database", e);
        }
    }

    public void loadState() {
        try {
            Object[] data = DatabaseManager.getInstance().getWorkflowState();
            if (data == null) {
                return;
            }

            com.google.gson.Gson gson = buildGson();

            this.expert = (String) data[0];
            if (this.expert != null && !this.expert.isEmpty()) {
                loadStrategyConfig(this.expert);
            }

            this.symbol = (String) data[1];
            this.period = (String) data[2];
            if (data[3] != null) this.fromDate = LocalDate.parse((String) data[3]);
            if (data[4] != null) this.toDate = LocalDate.parse((String) data[4]);
            if (data[5] != null) this.deposit = (Integer) data[5];
            this.currency = (String) data[6];
            this.leverage = (String) data[7];
            if (data[8] != null) this.tickModel = (Integer) data[8];

            java.lang.reflect.Type paramType = new com.google.gson.reflect.TypeToken<List<EaParameter>>(){}.getType();
            String eaParamsJson = (String) data[9];
            if (eaParamsJson != null && !eaParamsJson.isEmpty()) {
                this.eaParameters = gson.fromJson(eaParamsJson, paramType);
            }

            String optResultJson = (String) data[10];
            if (optResultJson != null && !optResultJson.isEmpty()) {
                this.optResult = gson.fromJson(optResultJson, OptimizationResult.class);
            }

            java.lang.reflect.Type passType = new com.google.gson.reflect.TypeToken<List<CombinedPass>>(){}.getType();
            String selectedDiverseJson = (String) data[11];
            if (selectedDiverseJson != null && !selectedDiverseJson.isEmpty()) {
                this.selectedDiversePasses = gson.fromJson(selectedDiverseJson, passType);
            }

            java.lang.reflect.Type sensitivityType = new com.google.gson.reflect.TypeToken<List<SensitivityResult>>(){}.getType();
            String sensitivityJson = (String) data[12];
            if (sensitivityJson != null && !sensitivityJson.isEmpty()) {
                this.sensitivityResults = gson.fromJson(sensitivityJson, sensitivityType);
            }

            this.kiReportText = (String) data[13];

            String finalSelectedJson = (String) data[14];
            if (finalSelectedJson != null && !finalSelectedJson.isEmpty()) {
                this.finalSelectedPasses = gson.fromJson(finalSelectedJson, passType);
            }

            if (data[15] != null) {
                this.lastActiveStep = (Integer) data[15];
            }
            log.info("Loaded workflow state from database. Last active step: {}", lastActiveStep);
        } catch (Exception e) {
            log.error("Failed to load workflow state from database", e);
        }
    }

    public void clearState() {
        this.expert = "";
        this.symbol = "EURUSD";
        this.period = "H1";
        this.fromDate = LocalDate.now().minusMonths(6);
        this.toDate = LocalDate.now();
        this.deposit = 10000;
        this.currency = "USD";
        this.leverage = "1:100";
        this.tickModel = 1;
        this.eaParameters = new ArrayList<>();
        this.optResult = null;
        this.selectedDiversePasses = new ArrayList<>();
        this.sensitivityResults = new ArrayList<>();
        this.kiReportText = "";
        this.finalSelectedPasses = new ArrayList<>();
        this.lastActiveStep = 0;
        
        try {
            DatabaseManager.getInstance().clearWorkflowState();
        } catch (Exception e) {
            log.error("Failed to clear workflow state from database", e);
        }
    }

    public void clearResults() {
        clearRunResults();
        saveState();
    }

    // --- Steps execution ---

    public boolean runStep1() throws Exception {
        if (expert == null || expert.isEmpty()) {
            throw new IllegalArgumentException("Kein Expert Advisor ausgewählt.");
        }
        if (eaParameters.isEmpty()) {
            throw new IllegalArgumentException("Keine EA Parameter geladen. Bitte lade einen Expert Advisor.");
        }
        // Save parameters to DB so runner can access them
        DatabaseManager.getInstance().saveEaParameterSettings(expert, symbol, period, new com.google.gson.Gson().toJson(eaParameters));
        this.lastActiveStep = Math.max(this.lastActiveStep, 1);
        saveState();
        return true;
    }

    public OptimizationResult runStep2(Consumer<String> logCallback, java.util.function.BiConsumer<Integer, Integer> progressCallback) throws Exception {
        runStep1(); // Ensure step 1 parameters are saved

        OptimizationConfig optConfig = new OptimizationConfig();
        optConfig.setExpert(expert);

        // Write the custom parameters directly to the MT5 tester profile folder as a .set file
        String eaName = EaParameterManager.extractEaBaseName(expert);
        java.nio.file.Path mt5Dir = config.getMt5InstallDir();
        if (mt5Dir != null) {
            java.nio.file.Path presetsDir = mt5Dir.resolve("MQL5").resolve("Profiles").resolve("Tester");
            java.nio.file.Files.createDirectories(presetsDir);
            String presetFileName = "Backtester_" + eaName + ".set";
            java.nio.file.Path destFile = presetsDir.resolve(presetFileName);
            
            eaParamManager.writeSetFile(destFile, eaParameters, eaName);
            optConfig.setExpertParameters(presetFileName);
        }

        optConfig.setSymbol(symbol);
        optConfig.setPeriod(period);
        optConfig.setFromDate(fromDate);
        optConfig.setToDate(toDate);
        optConfig.setDeposit(deposit);
        optConfig.setCurrency(currency);
        optConfig.setLeverage(leverage);
        optConfig.setModel(tickModel);
        optConfig.setOptimizationMode(optimizationMode);
        optConfig.setOptimizationCriterion(optimizationCriterion);
        optConfig.setForwardMode(forwardMode);
        optConfig.setForwardDate(forwardDate);
        optConfig.setUseLocal(true);
        optConfig.setShutdownTerminal(true);

        // Precompute total passes
        long totalPasses = 1000; // heuristic default
        
        currentOptRunner = new OptimizationRunner(config);
        currentOptRunner.setLogCallback(logCallback);
        currentOptRunner.setProgressCallback(progressCallback);
        currentOptRunner.setTotalPasses(totalPasses);

        optResult = currentOptRunner.runOptimization(optConfig);
        if (optResult == null || !optResult.isSuccess()) {
            throw new RuntimeException("Optimierungs-Run fehlgeschlagen: " + (optResult != null ? optResult.getMessage() : "Unbekannter Fehler"));
        }
        this.lastActiveStep = Math.max(this.lastActiveStep, 2);
        saveState();
        return optResult;
    }

    public List<CombinedPass> runStep3() {
        if (optResult == null) {
            throw new IllegalStateException("Kein Optimierungsergebnis vorhanden. Bitte führe zuerst Schritt 2 aus.");
        }
        
        // Build combined passes from optResult
        List<CombinedPass> allPasses = optResult.buildCombinedPasses(forwardMode > 0, OptimizationResult.ScoreWeights.defaults());
        selectedDiversePasses = filterDiversePasses(allPasses);
        this.lastActiveStep = Math.max(this.lastActiveStep, 3);
        saveState();
        return selectedDiversePasses;
    }

    public List<SensitivityResult> runStep4(Consumer<String> logCallback, Consumer<Integer> progressCallback) throws Exception {
        if (selectedDiversePasses.isEmpty()) {
            throw new IllegalStateException("Keine ausgewählten Strategien für die Sensitivitätsanalyse vorhanden. Bitte führe zuerst Schritt 3 aus.");
        }

        List<SensitivityResult> targets = new ArrayList<>();
        for (CombinedPass cp : selectedDiversePasses) {
            targets.add(new SensitivityResult(cp));
        }

        OptimizationConfig baseConfig = new OptimizationConfig();
        baseConfig.setExpert(expert);
        baseConfig.setSymbol(symbol);
        baseConfig.setPeriod(period);
        baseConfig.setFromDate(fromDate);
        baseConfig.setToDate(toDate);
        baseConfig.setDeposit(deposit);
        baseConfig.setCurrency(currency);
        baseConfig.setLeverage(leverage);
        baseConfig.setModel(tickModel);
        baseConfig.setOptimizationCriterion(optimizationCriterion);
        baseConfig.setForwardMode(forwardMode);
        baseConfig.setForwardDate(forwardDate);
        baseConfig.setUseLocal(true);

        currentSensitivityRunner = new SensitivityRunner(config);
        currentSensitivityRunner.setLogCallback(logCallback);
        currentSensitivityRunner.setProgressCallback(progressCallback);

        // We run sweeps based on the current state's EA parameters
        currentSensitivityRunner.runSensitivityScan(targets, baseConfig, eaParameters);
        
        sensitivityResults = targets;
        this.lastActiveStep = Math.max(this.lastActiveStep, 4);
        saveState();
        return sensitivityResults;
    }

    public String runStep5(Consumer<String> logCallback) throws Exception {
        if (sensitivityResults.isEmpty()) {
            throw new IllegalStateException("Keine Sensitivitätsanalysen vorhanden. Bitte führe zuerst Schritt 4 aus.");
        }

        List<Integer> activePasses = new ArrayList<>();
        for (SensitivityResult sr : sensitivityResults) {
            if (sr.getOriginalPass() != null) {
                activePasses.add(sr.getOriginalPass().getPassNumber());
            }
        }

        savePreferences(); // Save openrouter credentials if any

        LlmAnalysisService llmService = new LlmAnalysisService();
        kiReportText = llmService.analyzeStrategies(activePasses, expert, symbol);

        if (kiReportText.startsWith("ERROR")) {
            throw new RuntimeException(kiReportText);
        }

        // Save report to database
        long ts = System.currentTimeMillis();
        DatabaseManager.getInstance().saveKiReport(ts, expert, symbol, period, kiReportText);

        // Parse stability scores from LLM response
        try {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("STABILITY_SCORE\\|(\\d+)\\|(\\d+)");
            java.util.regex.Matcher matcher = pattern.matcher(kiReportText);
            while (matcher.find()) {
                int passNum = Integer.parseInt(matcher.group(1));
                int score = Math.max(0, Math.min(100, Integer.parseInt(matcher.group(2))));
                for (SensitivityResult sr : sensitivityResults) {
                    if (sr.getOriginalPass().getPassNumber() == passNum) {
                        sr.setKiResult(String.valueOf(score));
                    }
                }
            }
        } catch (Exception parseEx) {
            log.warn("Failed to parse LLM scores in Step 5: " + parseEx.getMessage());
        }

        this.lastActiveStep = Math.max(this.lastActiveStep, 5);
        saveState();
        return kiReportText;
    }

    public List<CombinedPass> runStep6() {
        if (selectedDiversePasses.isEmpty()) {
            throw new IllegalStateException("Keine ausgewählten Strategien vorhanden. Bitte führe zuerst Schritt 3 aus.");
        }

        // Filter and compile final 3-5 strategies based on KI stability results
        List<CombinedPass> candidates = new ArrayList<>(selectedDiversePasses);
        
        // Sort candidates based on KI stability score if available, otherwise fallback to standard combined score
        candidates.sort((cp1, cp2) -> {
            int score1 = getKiScore(cp1.getPassNumber());
            int score2 = getKiScore(cp2.getPassNumber());
            if (score1 != score2) {
                return Integer.compare(score2, score1); // descending stability score
            }
            return Double.compare(cp2.getScore(), cp1.getScore()); // descending combined score
        });

        // Filter out fragile ones (KI score < 50) if we have enough strategies
        List<CombinedPass> finalFiltered = new ArrayList<>();
        for (CombinedPass cp : candidates) {
            int kiScore = getKiScore(cp.getPassNumber());
            // If we have KI scores, require score >= 50, otherwise keep it
            if (kiScore >= 0 && kiScore < 50) {
                continue;
            }
            finalFiltered.add(cp);
        }

        // Fallback: if we filtered out everything, restore them to have at least some output
        if (finalFiltered.isEmpty()) {
            finalFiltered.addAll(candidates);
        }

        // Keep 3-5 strategies
        int targetCount = Math.max(3, Math.min(5, finalFiltered.size()));
        finalSelectedPasses = new ArrayList<>(finalFiltered.subList(0, Math.min(targetCount, finalFiltered.size())));
        
        this.lastActiveStep = Math.max(this.lastActiveStep, 6);
        saveState();
        return finalSelectedPasses;
    }

    public double getWorstCvForPass(int passNum, boolean forward) {
        if (sensitivityResults == null) return 0.0;
        for (SensitivityResult sr : sensitivityResults) {
            if (sr.getOriginalPass() != null && sr.getOriginalPass().getPassNumber() == passNum) {
                return forward ? sr.getOverallCVFw() : sr.getOverallCV();
            }
        }
        return 0.0;
    }

    public int getKiScoreForPass(int passNum) {
        return getKiScore(passNum);
    }

    private int getKiScore(int passNum) {
        if (sensitivityResults == null) return -1;
        for (SensitivityResult sr : sensitivityResults) {
            if (sr.getOriginalPass() != null && sr.getOriginalPass().getPassNumber() == passNum) {
                String res = sr.getKiResult();
                if (res != null && !res.isEmpty()) {
                    try {
                        return Integer.parseInt(res.trim());
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return -1; // no KI score available
    }

    // --- Helper Logic (Diversity Filter Algorithm) ---

    public List<CombinedPass> filterDiversePasses(List<CombinedPass> allPasses) {
        List<CombinedPass> resultList = new ArrayList<>();
        if (allPasses == null || allPasses.isEmpty()) {
            return resultList;
        }

        // 1. Filter out passes that do not meet minimum performance requirements
        List<CombinedPass> filtered = new ArrayList<>();
        for (CombinedPass cp : allPasses) {
            if (cp.getBtProfit() < minBtProfit) continue;
            if (cp.getBtTrades() < minBtTrades) continue;
            if (cp.getBtDd() > maxBtDd) continue;
            if (forwardMode > 0) {
                if (cp.getFwProfit() < minFwProfit) continue;
                if (cp.getFwTrades() < minFwTrades) continue;
                if (cp.getFwDd() > maxFwDd) continue;
            }
            filtered.add(cp);
        }

        // 2. Sort remaining passes by their combined score descending
        filtered.sort((cp1, cp2) -> Double.compare(cp2.getScore(), cp1.getScore()));

        // 3. Greedily pick the highest-ranked pass, checking for diversity
        for (CombinedPass candidate : filtered) {
            if (resultList.size() >= maxStrategiesToSelect) {
                break;
            }
            
            boolean isDiverse = true;
            for (CombinedPass selected : resultList) {
                if (arePassesSimilar(candidate, selected, paramDiffPct, tradeDiffPct, minDifferentParams, eaParameters)) {
                    isDiverse = false;
                    break;
                }
            }
            
            if (isDiverse) {
                resultList.add(candidate);
            }
        }

        return resultList;
    }

    /**
     * Checks if two optimization passes are too similar to be considered "diverse".
     */
    public static boolean arePassesSimilar(CombinedPass cp1, CombinedPass cp2, double paramDiffPct, double tradeDiffPct, int minDifferentParams) {
        return arePassesSimilar(cp1, cp2, paramDiffPct, tradeDiffPct, minDifferentParams, null);
    }

    public static boolean arePassesSimilar(CombinedPass cp1, CombinedPass cp2, double paramDiffPct, double tradeDiffPct, int minDifferentParams, List<EaParameter> eaParams) {
        // Compare backtest trade counts
        int trades1 = cp1.getBtTrades();
        int trades2 = cp2.getBtTrades();
        double tradeDiff = (double) Math.abs(trades1 - trades2) / Math.max(trades1, 1);
        boolean tradesSimilar = tradeDiff < tradeDiffPct;

        // Compare parameters
        Map<String, String> params1 = cp1.getBacktestPass().getParameterValues();
        Map<String, String> params2 = cp2.getBacktestPass().getParameterValues();

        int differentParamsCount = 0;

        if (eaParams == null || eaParams.isEmpty()) {
            // Old fallback logic
            for (String key : params1.keySet()) {
                String val1 = params1.get(key);
                String val2 = params2.get(key);
                if (val2 == null) {
                    differentParamsCount++;
                    continue;
                }
                if (val1.equals(val2)) {
                    continue;
                }

                // Try to parse numeric parameters
                try {
                    double d1 = Double.parseDouble(val1);
                    double d2 = Double.parseDouble(val2);
                    double maxAbs = Math.max(Math.abs(d1), Math.abs(d2));
                    if (maxAbs > 0) {
                        double diff = Math.abs(d1 - d2) / maxAbs;
                        if (diff >= paramDiffPct) {
                            differentParamsCount++;
                        }
                    }
                } catch (NumberFormatException e) {
                    // Non-numeric and not string-equal => different parameter
                    differentParamsCount++;
                }
            }
        } else {
            // New logic: compare only optimized parameters, normalized by their search space range!
            int optCount = 0;
            double totalNormalizedDiff = 0.0;

            for (EaParameter p : eaParams) {
                if (!p.isOptimizeEnabled() || p.isStringType()) continue;

                String val1 = params1.get(p.getName());
                String val2 = params2.get(p.getName());
                if (val1 == null || val2 == null) continue;

                optCount++;
                try {
                    double d1 = Double.parseDouble(val1);
                    double d2 = Double.parseDouble(val2);

                    double start = Double.parseDouble(p.getOptimizeStart());
                    double end = Double.parseDouble(p.getOptimizeEnd());
                    double range = Math.abs(end - start);

                    if (range > 0) {
                        double normDiff = Math.abs(d1 - d2) / range;
                        totalNormalizedDiff += normDiff;
                        if (normDiff >= paramDiffPct) {
                            differentParamsCount++;
                        }
                    } else {
                        // Fallback to relative diff
                        double maxAbs = Math.max(Math.abs(d1), Math.abs(d2));
                        if (maxAbs > 0) {
                            double relDiff = Math.abs(d1 - d2) / maxAbs;
                            totalNormalizedDiff += relDiff;
                            if (relDiff >= paramDiffPct) {
                                differentParamsCount++;
                            }
                        }
                    }
                } catch (Exception e) {
                    if (!val1.equals(val2)) {
                        differentParamsCount++;
                        totalNormalizedDiff += 1.0;
                    }
                }
            }

            // Check average normalized difference
            double avgDiff = optCount > 0 ? (totalNormalizedDiff / optCount) : 0.0;

            // If the average difference is less than the threshold (e.g. paramDiffPct),
            // they are similar (i.e. they are NOT diverse enough).
            // So if avgDiff < paramDiffPct, we say differentParamsCount is 0 (they are similar).
            if (avgDiff < paramDiffPct) {
                return tradesSimilar; // similar parameters => similar passes if trades are also similar
            }
        }

        // Similar if trade counts are close AND the number of significantly different parameters is low
        return tradesSimilar && (differentParamsCount < minDifferentParams);
    }

    public void cancel() {
        if (currentOptRunner != null) {
            currentOptRunner.cancel();
        }
        if (currentSensitivityRunner != null) {
            currentSensitivityRunner.cancel();
        }
    }

    // --- Getters & Setters ---

    public String getExpert() { return expert; }
    public void setExpert(String expert) { this.expert = expert; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public LocalDate getFromDate() { return fromDate; }
    public void setFromDate(LocalDate fromDate) { this.fromDate = fromDate; }

    public LocalDate getToDate() { return toDate; }
    public void setToDate(LocalDate toDate) { this.toDate = toDate; }

    public int getDeposit() { return deposit; }
    public void setDeposit(int deposit) { this.deposit = deposit; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getLeverage() { return leverage; }
    public void setLeverage(String leverage) { this.leverage = leverage; }

    public int getTickModel() { return tickModel; }
    public void setTickModel(int tickModel) { this.tickModel = tickModel; }

    public List<EaParameter> getEaParameters() { return eaParameters; }
    public void setEaParameters(List<EaParameter> eaParameters) { this.eaParameters = eaParameters; }

    public int getOptimizationMode() { return optimizationMode; }
    public void setOptimizationMode(int optimizationMode) { this.optimizationMode = optimizationMode; }

    public int getOptimizationCriterion() { return optimizationCriterion; }
    public void setOptimizationCriterion(int optimizationCriterion) { this.optimizationCriterion = optimizationCriterion; }

    public int getForwardMode() { return forwardMode; }
    public void setForwardMode(int forwardMode) { this.forwardMode = forwardMode; }

    public LocalDate getForwardDate() { return forwardDate; }
    public void setForwardDate(LocalDate forwardDate) { this.forwardDate = forwardDate; }

    public OptimizationResult getOptResult() { return optResult; }
    public void setOptResult(OptimizationResult optResult) { this.optResult = optResult; }

    public double getMinBtProfit() { return minBtProfit; }
    public void setMinBtProfit(double minBtProfit) { this.minBtProfit = minBtProfit; }

    public double getMinFwProfit() { return minFwProfit; }
    public void setMinFwProfit(double minFwProfit) { this.minFwProfit = minFwProfit; }

    public int getMinBtTrades() { return minBtTrades; }
    public void setMinBtTrades(int minBtTrades) { this.minBtTrades = minBtTrades; }

    public int getMinFwTrades() { return minFwTrades; }
    public void setMinFwTrades(int minFwTrades) { this.minFwTrades = minFwTrades; }

    public double getMaxBtDd() { return maxBtDd; }
    public void setMaxBtDd(double maxBtDd) { this.maxBtDd = maxBtDd; }

    public double getMaxFwDd() { return maxFwDd; }
    public void setMaxFwDd(double maxFwDd) { this.maxFwDd = maxFwDd; }

    public double getParamDiffPct() { return paramDiffPct; }
    public void setParamDiffPct(double paramDiffPct) { this.paramDiffPct = paramDiffPct; }

    public double getTradeDiffPct() { return tradeDiffPct; }
    public void setTradeDiffPct(double tradeDiffPct) { this.tradeDiffPct = tradeDiffPct; }

    public int getMinDifferentParams() { return minDifferentParams; }
    public void setMinDifferentParams(int minDifferentParams) { this.minDifferentParams = minDifferentParams; }

    public int getMaxStrategiesToSelect() { return maxStrategiesToSelect; }
    public void setMaxStrategiesToSelect(int maxStrategiesToSelect) { this.maxStrategiesToSelect = maxStrategiesToSelect; }

    public List<CombinedPass> getSelectedDiversePasses() { return selectedDiversePasses; }
    public void setSelectedDiversePasses(List<CombinedPass> selectedDiversePasses) { this.selectedDiversePasses = selectedDiversePasses; }

    public List<SensitivityResult> getSensitivityResults() { return sensitivityResults; }
    public void setSensitivityResults(List<SensitivityResult> sensitivityResults) { this.sensitivityResults = sensitivityResults; }

    public String getOpenRouterApiKey() { return openRouterApiKey; }
    public void setOpenRouterApiKey(String openRouterApiKey) { this.openRouterApiKey = openRouterApiKey; }

    public String getOpenRouterModel() { return openRouterModel; }
    public void setOpenRouterModel(String openRouterModel) { this.openRouterModel = openRouterModel; }

    public String getOpenRouterPrompt() { return openRouterPrompt; }
    public void setOpenRouterPrompt(String openRouterPrompt) { this.openRouterPrompt = openRouterPrompt; }

    public String getKiReportText() { return kiReportText; }
    public void setKiReportText(String kiReportText) { this.kiReportText = kiReportText; }

    public List<CombinedPass> getFinalSelectedPasses() { return finalSelectedPasses; }
    public void setFinalSelectedPasses(List<CombinedPass> finalSelectedPasses) { this.finalSelectedPasses = finalSelectedPasses; }

    public int getLastActiveStep() { return lastActiveStep; }
    public void setLastActiveStep(int lastActiveStep) { this.lastActiveStep = lastActiveStep; }
}
