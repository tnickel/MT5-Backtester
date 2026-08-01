package com.backtester.engine;

import com.backtester.config.AppConfig;
import com.backtester.config.EaParameter;
import com.backtester.config.EaParameterManager;
import com.backtester.database.DatabaseManager;
import com.backtester.report.BacktestResult;
import com.backtester.report.OptimizationResult;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.SensitivityResult;
import com.backtester.report.ValidationResult;
import com.backtester.report.PdfReportGenerator;
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
    private static final int SELECTION_PROFILE_VERSION = 1;
    private static final double MIN_POSITIVE_PROFIT = 0.01;

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
    private double minBtProfit = MIN_POSITIVE_PROFIT;
    private double minFwProfit = MIN_POSITIVE_PROFIT;
    private int minBtTrades = 100;
    private int minFwTrades = 50;
    private double minBtRecovery = 1.0;
    private double minFwRecovery = 1.0;
    private double maxBtDd = 100.0;
    private double maxFwDd = 100.0;
    private double paramDiffPct = 0.10;
    private double tradeDiffPct = 0.15;
    private int minDifferentParams = 2;
    private int maxStrategiesToSelect = 5;

    // Long-Term Test State & Dual-Filter Kriterien
    private LocalDate longtermFromDate = LocalDate.now().minusYears(7);
    private LocalDate longtermToDate = LocalDate.now();
    private int maxLongtermCandidates = 20;
    private double minLtProfit = MIN_POSITIVE_PROFIT;
    private int minLtTrades = 30;
    private double minLtRecovery = 1.0;
    private double maxLtDd = 35.0;
    private double minLtPf = 1.10;

    private List<CombinedPass> selectedDiversePasses = new ArrayList<>();

    // Step 4 State
    private List<SensitivityResult> sensitivityResults = new ArrayList<>();

    // Step 5 State
    private String openRouterApiKey = "";
    private String openRouterModel = LlmAnalysisService.DEFAULT_MODEL;
    private String openRouterPrompt = LlmAnalysisService.DEFAULT_PROMPT;
    private String kiReportText = "";
    private double performanceWeight = LlmAnalysisService.DEFAULT_PERFORMANCE_WEIGHT;
    private double stabilityWeight = LlmAnalysisService.DEFAULT_STABILITY_WEIGHT;

    // Step 6 State
    private List<CombinedPass> finalSelectedPasses = new ArrayList<>();
    private int lastActiveStep = 0;

    /**
     * True when the Step-6 KI gate (KI score >= 30) filtered out ALL candidates
     * and the fallback re-admitted them. The exported portfolio is then NOT
     * validated by the KI gate — exports are marked accordingly.
     */
    private boolean kiGateBypassed = false;

    // Step 7 State (Out-of-Sample Validation on untouched data)
    /** Start of the validation window; null = derived (toDate + 1 day). */
    private LocalDate validationFromDate = null;
    /** End of the validation window; null = derived (today). */
    private LocalDate validationToDate = null;
    private List<ValidationResult> validationResults = new ArrayList<>();

    /** Directory of the most recent portfolio export (used to attach the validation report). */
    private String lastExportDirectory = "";

    // Pipeline Runners
    private OptimizationRunner currentOptRunner;
    private SensitivityRunner currentSensitivityRunner;
    private BacktestRunner currentValidationRunner;

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

        try {
            String pwStr = db.getSetting(LlmAnalysisService.SETTING_PERFORMANCE_WEIGHT);
            this.performanceWeight = pwStr != null && !pwStr.isEmpty() ? Double.parseDouble(pwStr) : LlmAnalysisService.DEFAULT_PERFORMANCE_WEIGHT;
        } catch (Exception e) {
            this.performanceWeight = LlmAnalysisService.DEFAULT_PERFORMANCE_WEIGHT;
        }
        try {
            String swStr = db.getSetting(LlmAnalysisService.SETTING_STABILITY_WEIGHT);
            this.stabilityWeight = swStr != null && !swStr.isEmpty() ? Double.parseDouble(swStr) : LlmAnalysisService.DEFAULT_STABILITY_WEIGHT;
        } catch (Exception e) {
            this.stabilityWeight = LlmAnalysisService.DEFAULT_STABILITY_WEIGHT;
        }

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
        db.saveSetting(LlmAnalysisService.SETTING_PERFORMANCE_WEIGHT, String.valueOf(performanceWeight));
        db.saveSetting(LlmAnalysisService.SETTING_STABILITY_WEIGHT, String.valueOf(stabilityWeight));
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
        public int selectionProfileVersion;
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
        public double minBtRecovery;
        public double minFwRecovery;
        public double maxBtDd;
        public double maxFwDd;
        public double paramDiffPct;
        public double tradeDiffPct;
        public int minDifferentParams;
        public int maxStrategiesToSelect;
        public String openRouterApiKey;
        public String openRouterModel;
        public String openRouterPrompt;
        public String validationFromDate;
        public String validationToDate;
        public String longtermFromDate;
        public String longtermToDate;
        public int maxLongtermCandidates;
        public double minLtProfit;
        public int minLtTrades;
        public double minLtRecovery;
        public double maxLtDd;
        public double minLtPf;
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
        this.kiGateBypassed = false;
        this.validationResults = new ArrayList<>();
        this.lastExportDirectory = "";
    }

    public void saveStrategyConfig(String expertName) {
        if (expertName == null || expertName.isEmpty()) return;
        try {
            StrategyConfig sc = new StrategyConfig();
            sc.selectionProfileVersion = SELECTION_PROFILE_VERSION;
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
            sc.minBtRecovery = this.minBtRecovery;
            sc.minFwRecovery = this.minFwRecovery;
            sc.maxBtDd = this.maxBtDd;
            sc.maxFwDd = this.maxFwDd;
            sc.paramDiffPct = this.paramDiffPct;
            sc.tradeDiffPct = this.tradeDiffPct;
            sc.minDifferentParams = this.minDifferentParams;
            sc.maxStrategiesToSelect = this.maxStrategiesToSelect;
            sc.openRouterApiKey = this.openRouterApiKey;
            sc.openRouterModel = this.openRouterModel;
            sc.openRouterPrompt = this.openRouterPrompt;
            sc.validationFromDate = this.validationFromDate != null ? this.validationFromDate.toString() : null;
            sc.validationToDate = this.validationToDate != null ? this.validationToDate.toString() : null;
            sc.longtermFromDate = this.longtermFromDate != null ? this.longtermFromDate.toString() : null;
            sc.longtermToDate = this.longtermToDate != null ? this.longtermToDate.toString() : null;
            sc.maxLongtermCandidates = this.maxLongtermCandidates;
            sc.minLtProfit = this.minLtProfit;
            sc.minLtTrades = this.minLtTrades;
            sc.minLtRecovery = this.minLtRecovery;
            sc.maxLtDd = this.maxLtDd;
            sc.minLtPf = this.minLtPf;

            com.google.gson.Gson gson = buildGson();
            String json = gson.toJson(sc);
            DatabaseManager.getInstance().saveWorkflowStrategyConfig(expertName, json);
            log.info("Successfully saved strategy configuration for: {}", expertName);
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
            if (this.eaParameters != null && !this.eaParameters.isEmpty()) {
                eaParamManager.applyTranslations(this.expert, this.eaParameters);
            }
            this.optimizationMode = sc.optimizationMode;
            this.optimizationCriterion = sc.optimizationCriterion;
            this.forwardMode = sc.forwardMode;
            if (sc.forwardDate != null) this.forwardDate = LocalDate.parse(sc.forwardDate);
            boolean migrateSelectionProfile = sc.selectionProfileVersion < SELECTION_PROFILE_VERSION;
            this.minBtProfit = finiteAtLeast(sc.minBtProfit, MIN_POSITIVE_PROFIT);
            this.minFwProfit = finiteAtLeast(sc.minFwProfit, MIN_POSITIVE_PROFIT);
            this.minBtTrades = migrateSelectionProfile ? Math.max(100, sc.minBtTrades) : Math.max(1, sc.minBtTrades);
            this.minFwTrades = migrateSelectionProfile ? Math.max(50, sc.minFwTrades) : Math.max(1, sc.minFwTrades);
            this.minBtRecovery = finiteAtLeast(sc.minBtRecovery, migrateSelectionProfile ? 1.0 : 0.0);
            this.minFwRecovery = finiteAtLeast(sc.minFwRecovery, migrateSelectionProfile ? 1.0 : 0.0);
            this.maxBtDd = sc.maxBtDd;
            this.maxFwDd = sc.maxFwDd;
            this.paramDiffPct = sc.paramDiffPct;
            this.tradeDiffPct = sc.tradeDiffPct;
            this.minDifferentParams = sc.minDifferentParams;
            this.maxStrategiesToSelect = sc.maxStrategiesToSelect;
            this.openRouterApiKey = sc.openRouterApiKey != null ? sc.openRouterApiKey : "";
            this.openRouterModel = sc.openRouterModel != null ? sc.openRouterModel : LlmAnalysisService.DEFAULT_MODEL;
            this.openRouterPrompt = sc.openRouterPrompt != null ? sc.openRouterPrompt : LlmAnalysisService.DEFAULT_PROMPT;
            this.validationFromDate = sc.validationFromDate != null ? LocalDate.parse(sc.validationFromDate) : null;
            this.validationToDate = sc.validationToDate != null ? LocalDate.parse(sc.validationToDate) : null;
            if (sc.longtermFromDate != null) this.longtermFromDate = LocalDate.parse(sc.longtermFromDate);
            if (sc.longtermToDate != null) this.longtermToDate = LocalDate.parse(sc.longtermToDate);
            this.maxLongtermCandidates = sc.maxLongtermCandidates > 0 ? sc.maxLongtermCandidates : 20;
            this.minLtProfit = finiteAtLeast(sc.minLtProfit, MIN_POSITIVE_PROFIT);
            this.minLtTrades = sc.minLtTrades > 0 ? sc.minLtTrades : 30;
            this.minLtRecovery = finiteAtLeast(sc.minLtRecovery, 0.0);
            this.maxLtDd = sc.maxLtDd > 0 ? sc.maxLtDd : 35.0;
            this.minLtPf = finiteAtLeast(sc.minLtPf, 1.10);

            if (migrateSelectionProfile) {
                saveStrategyConfig(expertName);
            }

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
            String validationJson = gson.toJson(validationResults);

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
                lastActiveStep,
                validationJson,
                kiGateBypassed
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
                if (this.eaParameters != null) {
                    eaParamManager.applyTranslations(this.expert, this.eaParameters);
                }
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

            if (data.length > 16) {
                this.validationResults = parseValidationResults(gson, (String) data[16]);
            }
            if (data.length > 17 && data[17] instanceof Boolean) {
                this.kiGateBypassed = (Boolean) data[17];
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
        this.kiGateBypassed = false;
        this.validationFromDate = null;
        this.validationToDate = null;
        this.validationResults = new ArrayList<>();
        this.lastExportDirectory = "";

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

        // Write the custom parameters directly to the MetaTrader tester profile folder as a .set file
        String eaName = EaParameterManager.extractEaBaseName(expert);
        java.nio.file.Path mtDir = config.getMtInstallDir(expert);
        if (mtDir != null) {
            java.nio.file.Path presetsDir = config.getTesterProfilesDir(expert);
            java.nio.file.Files.createDirectories(presetsDir);
            String presetFileName = "Backtester_" + eaName + ".set";
            java.nio.file.Path destFile = presetsDir.resolve(presetFileName);
            
            eaParamManager.writeSetFile(destFile, eaParameters, expert);
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

        // Precompute total passes from the parameter search space. Exact for
        // the Complete algorithm; the Genetic algorithm decides its own pass
        // count, so the progress callback treats the value only as an upper bound.
        long totalPasses = computeTotalPasses(eaParameters);

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

    /**
     * Loads the unified score weights. Delegates to
     * {@link OptimizationResult.ScoreWeights#loadFromDatabase()} — the single
     * source of truth for DB keys and default values — so Step-3 ranking,
     * scorecard display and UI dialogs always use identical defaults.
     */
    public OptimizationResult.ScoreWeights loadScoreWeightsFromDb() {
        return OptimizationResult.ScoreWeights.loadFromDatabase();
    }

    /**
     * Product of the step counts of all optimize-enabled numeric parameters.
     * Replaces the former hardcoded heuristic of 1000, which made the progress
     * display wrong for the Complete algorithm. Capped at Integer.MAX_VALUE.
     *
     * <p>Overflow-safe: the step count per parameter is capped BEFORE the
     * cast (a huge double would saturate/overflow the long), and the
     * multiplication is guarded BEFORE it happens — a post-check would miss
     * wrap-arounds that land on small positive values.
     */
    static long computeTotalPasses(List<EaParameter> params) {
        long total = 1;
        boolean anyEnabled = false;
        if (params != null) {
            for (EaParameter p : params) {
                if (!p.isOptimizeEnabled() || p.isStringType()) continue;
                try {
                    double start = Double.parseDouble(p.getOptimizeStart());
                    double end = Double.parseDouble(p.getOptimizeEnd());
                    double step = Double.parseDouble(p.getOptimizeStep());
                    if (step <= 0) continue;
                    double steps = Math.abs(end - start) / step;
                    if (Double.isNaN(steps)) continue;
                    anyEnabled = true;
                    if (steps >= Integer.MAX_VALUE) {
                        return Integer.MAX_VALUE;
                    }
                    long count = (long) steps + 1;
                    if (count < 1) count = 1;
                    if (total > Integer.MAX_VALUE / count) {
                        return Integer.MAX_VALUE;
                    }
                    total *= count;
                } catch (Exception ignored) {
                    // parameter without a parseable range does not multiply
                }
            }
        }
        return anyEnabled ? total : 1;
    }

    public LocalDate getLongtermFromDate() { return longtermFromDate; }
    public void setLongtermFromDate(LocalDate date) { this.longtermFromDate = date; }

    public LocalDate getLongtermToDate() { return longtermToDate; }
    public void setLongtermToDate(LocalDate date) { this.longtermToDate = date; }

    public LocalDate getEffectiveLongtermFromDate() {
        return longtermFromDate != null ? longtermFromDate : LocalDate.now().minusYears(7);
    }

    public LocalDate getEffectiveLongtermToDate() {
        return longtermToDate != null ? longtermToDate : LocalDate.now();
    }

    public int getMaxLongtermCandidates() { return maxLongtermCandidates; }
    public void setMaxLongtermCandidates(int max) { this.maxLongtermCandidates = max; }

    public double getMinLtProfit() { return minLtProfit; }
    public void setMinLtProfit(double v) { this.minLtProfit = v; }

    public int getMinLtTrades() { return minLtTrades; }
    public void setMinLtTrades(int v) { this.minLtTrades = v; }

    public double getMinLtRecovery() { return minLtRecovery; }
    public void setMinLtRecovery(double v) { this.minLtRecovery = v; }

    public double getMaxLtDd() { return maxLtDd; }
    public void setMaxLtDd(double v) { this.maxLtDd = v; }

    public double getMinLtPf() { return minLtPf; }
    public void setMinLtPf(double v) { this.minLtPf = v; }

    public List<CombinedPass> runLongtermTest(Consumer<String> logCallback, Consumer<Integer> progressCallback) throws Exception {
        if (optResult == null) {
            throw new IllegalStateException("Kein Optimierungsergebnis vorhanden. Bitte führe zuerst Schritt 2 aus.");
        }

        List<CombinedPass> allPasses = optResult.buildCombinedPasses(forwardMode > 0, loadScoreWeightsFromDb());
        if (allPasses.isEmpty()) {
            log.warn("Langzeittest: 0 Pässe vorhanden.");
            return new ArrayList<>();
        }

        // 1. Pre-filter candidates using strict short-term criteria
        List<CombinedPass> candidates = new ArrayList<>();
        for (CombinedPass cp : allPasses) {
            if (!isPositiveFinite(cp.getBtProfit()) || cp.getBtProfit() < minBtProfit) continue;
            if (cp.getBtTrades() < minBtTrades) continue;
            if (!meetsMinimum(cp.getBtRecovery(), minBtRecovery)) continue;
            if (cp.getBtDd() > maxBtDd) continue;
            if (forwardMode > 0) {
                if (!isPositiveFinite(cp.getFwProfit()) || cp.getFwProfit() < minFwProfit) continue;
                if (cp.getFwTrades() < minFwTrades) continue;
                if (!meetsMinimum(cp.getFwRecovery(), minFwRecovery)) continue;
                if (cp.getFwDd() > maxFwDd) continue;
            }
            candidates.add(cp);
        }

        // Sort candidates by combined score descending
        candidates.sort((cp1, cp2) -> Double.compare(cp2.getScore(), cp1.getScore()));

        // Limit to maxLongtermCandidates
        if (candidates.size() > maxLongtermCandidates) {
            candidates = new ArrayList<>(candidates.subList(0, maxLongtermCandidates));
        }

        if (candidates.isEmpty()) {
            if (logCallback != null) logCallback.accept("WARNUNG: Keine Kandidaten haben die Kurzzeit-Vorauswahl bestanden!");
            return new ArrayList<>();
        }

        if (logCallback != null) {
            logCallback.accept(String.format("Starte Langzeittest für %d Kandidaten (%s bis %s)...",
                    candidates.size(), getEffectiveLongtermFromDate(), getEffectiveLongtermToDate()));
        }

        java.nio.file.Path presetsDir = config.getTesterProfilesDir(expert);
        java.nio.file.Files.createDirectories(presetsDir);

        BacktestRunner runner = new BacktestRunner();
        runner.setLogCallback(logCallback);

        int total = candidates.size();
        for (int i = 0; i < total; i++) {
            CombinedPass cp = candidates.get(i);
            if (progressCallback != null) {
                progressCallback.accept((i * 100) / total);
            }

            String presetFileName = "Longterm_Pass" + cp.getPassNumber() + ".set";
            java.nio.file.Path presetFile = presetsDir.resolve(presetFileName);
            List<EaParameter> finalParams = buildFinalParams(cp);
            eaParamManager.writeSetFile(presetFile, finalParams, expert);

            BacktestConfig btConfig = new BacktestConfig();
            btConfig.setExpert(expert);
            btConfig.setExpertParameters(presetFileName);
            btConfig.setSymbol(symbol);
            btConfig.setPeriod(period);
            btConfig.setFromDate(getEffectiveLongtermFromDate());
            btConfig.setToDate(getEffectiveLongtermToDate());
            btConfig.setDeposit(deposit);
            btConfig.setCurrency(currency);
            btConfig.setLeverage(leverage);
            btConfig.setModel(tickModel);
            btConfig.setShutdownTerminal(true);
            btConfig.setAutoKillMt5(true);
            btConfig.setReportFileName("LongtermReport_Pass" + cp.getPassNumber());

            if (logCallback != null) {
                logCallback.accept(String.format("[%d/%d] Führe Langzeittest für Pass %d aus...", (i + 1), total, cp.getPassNumber()));
            }

            BacktestResult btRes = runner.runBacktest(btConfig);
            if (btRes != null) {
                OptimizationResult.Pass ltPass = new OptimizationResult.Pass();
                ltPass.setPassNumber(cp.getPassNumber());
                ltPass.setProfit(btRes.getTotalProfit());
                ltPass.setTotalTrades(btRes.getTotalTrades());
                ltPass.setProfitFactor(btRes.getProfitFactor());
                ltPass.setDrawdownPercent(btRes.getMaxDrawdownPercent());
                ltPass.setRecoveryFactor(btRes.getRecoveryFactor());
                ltPass.setSharpeRatio(btRes.getSharpeRatio());
                ltPass.setExpectedPayoff(btRes.getExpectedPayoff());
                ltPass.setParameterValues(cp.getBacktestPass().getParameterValues());
                cp.setLongtermPass(ltPass);
            } else {
                if (logCallback != null) {
                    logCallback.accept(String.format("WARNUNG: Pass %d Langzeittest schlug fehl.", cp.getPassNumber()));
                }
            }
        }

        if (progressCallback != null) {
            progressCallback.accept(100);
        }

        saveState();
        return candidates;
    }

    public List<CombinedPass> runStep3() {
        if (optResult == null) {
            throw new IllegalStateException("Kein Optimierungsergebnis vorhanden. Bitte führe zuerst Schritt 2 aus.");
        }
        if (optResult.getPasses().isEmpty()) {
            // Schritt 2 meldet auch bei fehlendem Report "Erfolg mit 0 Pässen",
            // damit der Workflow nicht blockiert — hier machen wir das sichtbar
            // statt still eine leere Auswahl weiterzureichen.
            log.warn("Schritt 3: Optimierungsergebnis enthält 0 Pässe (kein Report erzeugt?). " +
                    "Diversitätsfilter liefert eine leere Auswahl.");
        }

        // Build combined passes from optResult
        List<CombinedPass> allPasses = optResult.buildCombinedPasses(forwardMode > 0, loadScoreWeightsFromDb());
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

        // Build performance data map from selected diverse passes for the LLM
        Map<Integer, LlmAnalysisService.PassPerformance> performanceData = new HashMap<>();
        for (SensitivityResult sr : sensitivityResults) {
            if (sr.getOriginalPass() != null) {
                performanceData.put(sr.getOriginalPass().getPassNumber(),
                        new LlmAnalysisService.PassPerformance(sr.getOriginalPass()));
            }
        }

        LlmAnalysisService llmService = new LlmAnalysisService();
        kiReportText = llmService.analyzeStrategies(activePasses, expert, symbol, performanceData);

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

        // Compile final 3-5 strategies using a WEIGHTED combination of
        // Combined Score (performance) and KI Stability Score.
        // This prevents the KI score from completely dominating — a high-performing
        // strategy with acceptable stability should beat a mediocre but "robust" one.
        List<CombinedPass> candidates = new ArrayList<>(selectedDiversePasses);

        // Sort candidates by weighted final score (descending)
        candidates.sort((cp1, cp2) -> {
            double finalScore1 = computeWeightedFinalScore(cp1);
            double finalScore2 = computeWeightedFinalScore(cp2);
            return Double.compare(finalScore2, finalScore1);
        });

        // Filter out clearly fragile strategies (KI score < 30) if we have enough
        // The threshold is lower than before (was 50) because the weighted score
        // already dampens fragile strategies — no need for a harsh cutoff.
        List<CombinedPass> finalFiltered = new ArrayList<>();
        for (CombinedPass cp : candidates) {
            int kiScore = getKiScore(cp.getPassNumber());
            if (kiScore >= 0 && kiScore < 30) {
                continue;
            }
            finalFiltered.add(cp);
        }

        // Fallback: if we filtered out everything, restore them to have at least
        // some output — but flag the run so exports are visibly marked as
        // NOT KI-validated instead of silently looking like a normal portfolio.
        this.kiGateBypassed = finalFiltered.isEmpty() && !candidates.isEmpty();
        if (kiGateBypassed) {
            log.warn("KI-Gate: ALLE {} Kandidaten haben KI-Score < 30. Fallback aktiv — "
                    + "der Export wird als NICHT VALIDIERT markiert!", candidates.size());
            finalFiltered.addAll(candidates);
        }

        // Keep 3-5 strategies
        int targetCount = Math.max(3, Math.min(5, finalFiltered.size()));
        finalSelectedPasses = new ArrayList<>(finalFiltered.subList(0, Math.min(targetCount, finalFiltered.size())));

        // A new final selection invalidates any previous validation results
        this.validationResults = new ArrayList<>();

        this.lastActiveStep = Math.max(this.lastActiveStep, 6);
        saveState();

        // Automatic export when step 6 executes
        try {
            Path targetDir = (config != null && config.getExportDirectory() != null) ? config.getExportDirectory() : AppConfig.getInstance().getExportDirectory();
            String expDir = targetDir.toString();
            exportPortfolio(expDir);
        } catch (Exception ex) {
            log.error("Automatic step 6 portfolio export failed: " + ex.getMessage(), ex);
        }

        // Save this completed workflow run to HISTORY_RUNS database
        saveWorkflowToHistory();

        return finalSelectedPasses;
    }

    // --- Step 7: Out-of-Sample Validation ---------------------------------

    /**
     * Effective start of the validation window: configured value, or the day
     * after the optimization range ends.
     */
    public LocalDate getEffectiveValidationFromDate() {
        if (validationFromDate != null) return validationFromDate;
        return toDate != null ? toDate.plusDays(1) : null;
    }

    /**
     * Effective end of the validation window: configured value, or today.
     */
    public LocalDate getEffectiveValidationToDate() {
        if (validationToDate != null) return validationToDate;
        return LocalDate.now();
    }

    /**
     * True when a usable validation window exists: it must start after the
     * optimization range (never-seen data) and span at least
     * {@code minDays} days.
     */
    public boolean hasUsableValidationWindow(int minDays) {
        LocalDate vFrom = getEffectiveValidationFromDate();
        LocalDate vTo = getEffectiveValidationToDate();
        if (vFrom == null || vTo == null || toDate == null) return false;
        if (!vFrom.isAfter(toDate)) return false;
        return java.time.temporal.ChronoUnit.DAYS.between(vFrom, vTo) >= minDays;
    }

    /**
     * Step 7: validates the final selected strategies on a time window that
     * was used neither for optimization nor for selection.
     *
     * <p><b>Warum:</b> Das Forward-Fenster wird in Schritt 3–6 bereits als
     * Auswahlkriterium verbraucht (Selektion nach FW-Metriken über tausende
     * Pässe = Multiple-Testing-Bias). Erst dieser Schritt liefert eine echte
     * Out-of-Sample-Schätzung. Das Fenster liegt standardmäßig NACH dem
     * Optimierungszeitraum (toDate+1 bis heute) und darf sich nicht mit ihm
     * überlappen — das wird hart geprüft.
     *
     * @param logCallback      receives progress log lines
     * @param progressCallback (current, total) candidate progress; may be null
     * @return validation results, one per final selected pass
     */
    public List<ValidationResult> runStep7(Consumer<String> logCallback,
                                           java.util.function.BiConsumer<Integer, Integer> progressCallback) throws Exception {
        if (finalSelectedPasses == null || finalSelectedPasses.isEmpty()) {
            throw new IllegalStateException("Keine finalen Strategien vorhanden. Bitte führe zuerst Schritt 6 aus.");
        }

        LocalDate vFrom = getEffectiveValidationFromDate();
        LocalDate vTo = getEffectiveValidationToDate();
        if (vFrom == null || vTo == null || !vFrom.isBefore(vTo)) {
            throw new IllegalStateException(
                    "Kein gültiges Validierungsfenster (" + vFrom + " bis " + vTo + "). " +
                    "Beende die Optimierung früher (toDate in der Vergangenheit) oder konfiguriere das Fenster in Schritt 7.");
        }
        if (toDate != null && !vFrom.isAfter(toDate)) {
            throw new IllegalStateException(
                    "Validierungsfenster (" + vFrom + " bis " + vTo + ") überlappt mit dem Optimierungszeitraum (bis " + toDate + "). " +
                    "Die Validierung ist nur aussagekräftig auf Daten, die weder Optimierung noch Selektion gesehen haben.");
        }

        if (logCallback != null) {
            logCallback.accept(String.format("Validierung von %d Strategien auf unberührtem Fenster %s bis %s",
                    finalSelectedPasses.size(), vFrom, vTo));
        }

        java.nio.file.Path presetsDir = config.getTesterProfilesDir(expert);
        java.nio.file.Files.createDirectories(presetsDir);

        List<ValidationResult> results = new ArrayList<>();
        int total = finalSelectedPasses.size();
        int current = 0;

        for (CombinedPass cp : finalSelectedPasses) {
            current++;
            if (progressCallback != null) progressCallback.accept(current, total);

            ValidationResult vr = new ValidationResult(cp.getPassNumber());
            vr.setValidationFrom(vFrom.toString());
            vr.setValidationTo(vTo.toString());
            vr.setBtProfit(cp.getBtProfit());
            vr.setFwProfit(Double.isNaN(cp.getFwProfit()) ? 0.0 : cp.getFwProfit());

            try {
                // Write the pass parameters as a .set file for the tester
                String presetFileName = "Validation_Pass" + cp.getPassNumber() + ".set";
                java.nio.file.Path presetFile = presetsDir.resolve(presetFileName);
                List<EaParameter> finalParams = buildFinalParams(cp);
                eaParamManager.writeSetFile(presetFile, finalParams, expert);

                BacktestConfig btConfig = new BacktestConfig();
                btConfig.setExpert(expert);
                btConfig.setExpertParameters(presetFileName);
                btConfig.setSymbol(symbol);
                btConfig.setPeriod(period);
                btConfig.setModel(tickModel);
                btConfig.setFromDate(vFrom);
                btConfig.setToDate(vTo);
                btConfig.setDeposit(deposit);
                btConfig.setCurrency(currency);
                btConfig.setLeverage(leverage);
                btConfig.setShutdownTerminal(true);
                btConfig.setAutoKillMt5(true);
                btConfig.setReportFileName("ValidationReport_Pass" + cp.getPassNumber());

                if (logCallback != null) {
                    logCallback.accept(String.format("Pass %d: Validierungs-Backtest (%d/%d)...",
                            cp.getPassNumber(), current, total));
                }

                currentValidationRunner = new BacktestRunner();
                if (logCallback != null) {
                    final Consumer<String> lc = logCallback;
                    currentValidationRunner.setLogCallback(msg -> lc.accept("  [MT] " + msg));
                }
                BacktestResult btResult = currentValidationRunner.runBacktest(btConfig);

                if (btResult == null || !btResult.isSuccess()) {
                    vr.setVerdict(ValidationResult.ERROR);
                    vr.setMessage(btResult != null ? btResult.getMessage() : "Backtest fehlgeschlagen (kein Ergebnis)");
                } else {
                    vr.setProfit(btResult.getTotalProfit());
                    vr.setTrades(btResult.getTotalTrades());
                    vr.setProfitFactor(btResult.getProfitFactor());
                    vr.setDrawdownPercent(btResult.getMaxDrawdownPercent());
                    vr.setRecoveryFactor(btResult.getRecoveryFactor());
                    vr.computeVerdict();
                }
            } catch (Exception e) {
                log.error("Validation backtest for pass " + cp.getPassNumber() + " failed", e);
                vr.setVerdict(ValidationResult.ERROR);
                vr.setMessage(e.getMessage());
            }

            if (logCallback != null) logCallback.accept(vr.toSummaryLine());
            results.add(vr);
        }

        this.validationResults = results;
        this.lastActiveStep = Math.max(this.lastActiveStep, 7);
        saveState();
        saveWorkflowToHistory();

        // Attach the validation report to the last export & flag failed
        // strategies that were already copied to the "best" folder.
        try {
            writeValidationArtifacts();
        } catch (Exception e) {
            log.error("Failed to write validation report artifacts", e);
        }

        long passed = results.stream().filter(ValidationResult::isPassed).count();
        if (logCallback != null) {
            logCallback.accept(String.format("Validierung abgeschlossen: %d von %d Strategien erfüllen Profit-, Trade- und Recovery-Kriterien.",
                    passed, results.size()));
        }
        return results;
    }

    /**
     * Builds the final (non-optimizing) parameter set for a pass by merging
     * the pass values over the base EA parameters. Magic number and order
     * comment are stamped with the pass identity — same rules as the export.
     */
    private List<EaParameter> buildFinalParams(CombinedPass cp) {
        int passNum = cp.getPassNumber();
        double btDd = cp.getBtDd();
        int ddPct = Double.isNaN(btDd) ? 0 : (int) Math.round(btDd);

        List<EaParameter> finalParams = new ArrayList<>();
        for (EaParameter base : getEaParameters()) {
            EaParameter p = new EaParameter();
            p.setName(base.getName());
            p.setStringType(base.isStringType());
            String passVal = cp.getBacktestPass().getParameter(base.getName());
            if (passVal != null && !passVal.isEmpty()) {
                p.setValue(passVal);
            } else {
                p.setValue(base.getValue());
            }
            if (isMagicNumberParameter(p.getName())) {
                p.setValue(String.valueOf(passNum));
            }
            if (isOrderCommentParameter(p.getName())) {
                p.setValue(String.format("%dproz_Pass%d", ddPct, passNum));
            }
            p.setOptimizeEnabled(false);
            finalParams.add(p);
        }
        return finalParams;
    }

    /**
     * Writes VALIDIERUNGS_REPORT.txt into the last export directory and, for
     * strategies that FAILED validation but were already copied to the
     * "best" folder, a companion warning file next to their .set file.
     * Never deletes user files — it marks them.
     */
    private void writeValidationArtifacts() throws IOException {
        if (validationResults == null || validationResults.isEmpty()) return;

        StringBuilder report = new StringBuilder();
        report.append("=== SCHRITT 7: OUT-OF-SAMPLE VALIDIERUNG ===\n");
        report.append("Fenster: ").append(getEffectiveValidationFromDate())
              .append(" bis ").append(getEffectiveValidationToDate()).append("\n");
        report.append("Dieses Zeitfenster wurde weder für die Optimierung noch für die Selektion benutzt.\n");
        report.append("Nur diese Ergebnisse sind eine echte Out-of-Sample-Schätzung — die Forward-Werte\n");
        report.append("wurden bereits als Auswahlkriterium verbraucht (Selection Bias).\n\n");
        for (ValidationResult vr : validationResults) {
            report.append(vr.toSummaryLine()).append("\n");
        }
        long passed = validationResults.stream().filter(ValidationResult::isPassed).count();
        report.append(String.format("%nErgebnis: %d von %d Strategien bestanden.%n", passed, validationResults.size()));

        if (lastExportDirectory != null && !lastExportDirectory.isEmpty()) {
            java.nio.file.Path exportPath = java.nio.file.Paths.get(lastExportDirectory);
            if (Files.exists(exportPath)) {
                Files.write(exportPath.resolve("VALIDIERUNGS_REPORT.txt"),
                        report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                log.info("Validation report written to {}", exportPath);
            }
        }

        // Mark all non-passed strategies in the best folder (files were copied
        // there in Step 6 before validation existed). Warn files are OUR
        // artifacts: write them for every non-PASSED verdict and remove stale
        // ones only when a pass actually passes a later validation run —
        // otherwise an outdated warning would discredit a now-valid strategy.
        java.nio.file.Path bestPath = com.backtester.config.AppConfig.getInstance().getBestExportDirectory();
        if (bestPath != null && Files.exists(bestPath)) {
            for (ValidationResult vr : validationResults) {
                java.nio.file.Path warnFile = validationWarnFile(bestPath, vr.getPassNumber());
                if (!ValidationResult.PASSED.equals(vr.getVerdict())) {
                    String msg = "Pass " + vr.getPassNumber() + " hat die Out-of-Sample-Validierung NICHT bestanden"
                            + " (Verdict: " + vr.getVerdict() + "):\n"
                            + vr.toSummaryLine() + "\n"
                            + "Die zugehörigen .set/.pdf-Dateien in diesem Ordner sollten nicht live eingesetzt werden.\n";
                    Files.write(warnFile, msg.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    log.warn("Marked non-passed validation ({}) for pass {} in best folder.",
                            vr.getVerdict(), vr.getPassNumber());
                } else {
                    if (Files.deleteIfExists(warnFile)) {
                        log.info("Removed stale validation warning for pass {} (verdict now {}).",
                                vr.getPassNumber(), vr.getVerdict());
                    }
                }
            }
        }
    }

    /** Location of the per-pass validation warning marker in the best folder. */
    private static java.nio.file.Path validationWarnFile(java.nio.file.Path bestPath, int passNum) {
        return bestPath.resolve("WARNUNG_Pass" + passNum + "_VALIDIERUNG_FEHLGESCHLAGEN.txt");
    }

    private boolean isMagicNumberParameter(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.equals("magic") || lower.equals("inpmagicnumber") || lower.equals("magicnumber");
    }

    private boolean isOrderCommentParameter(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.equals("comment") || lower.equals("inp_order_comment") || lower.equals("ordercomment") || lower.equals("order_comment");
    }

    /**
     * Exports all final selected strategies (sets + PDF reports) to the target directory.
     */
    public void exportPortfolio(String exportDirStr) {
        String bestDirStr = com.backtester.config.AppConfig.getInstance().getBestExportDirectory().toString();
        exportPortfolio(exportDirStr, bestDirStr);
    }

    public void exportPortfolio(String exportDirStr, String bestDirStr) {
        if (finalSelectedPasses == null || finalSelectedPasses.isEmpty()) {
            log.warn("No final selected passes to export.");
            return;
        }

        try {
            String eaName = com.backtester.config.EaParameterManager.extractEaBaseName(getExpert());
            String dateStr = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm").format(java.time.LocalDateTime.now());
            String symbol = getSymbol().replaceAll("[^a-zA-Z0-9_.-]", "_");
            String timeframe = getPeriod().replaceAll("[^a-zA-Z0-9_.-]", "_");
            String subDirName = String.format("%s_%s_%s_%s", eaName, symbol, timeframe, dateStr);
            
            java.nio.file.Path exportPath = java.nio.file.Paths.get(exportDirStr).resolve(subDirName);
            java.nio.file.Files.createDirectories(exportPath);
            this.lastExportDirectory = exportPath.toString();

            // Visible marker when the Step-6 KI gate had to be bypassed:
            // this portfolio was NOT validated by the stability gate.
            if (kiGateBypassed) {
                String warn = "WARNUNG: Alle Kandidaten dieses Portfolios hatten einen KI-Stabilitäts-Score < 30.\n"
                        + "Das KI-Gate wurde umgangen, damit überhaupt ein Export entsteht.\n"
                        + "Diese Strategien gelten als FRAGIL und sollten NICHT live eingesetzt werden,\n"
                        + "bevor sie Schritt 7 (Out-of-Sample-Validierung) bestanden haben.\n";
                java.nio.file.Files.write(exportPath.resolve("WARNUNG_KI_GATE_UMGANGEN.txt"),
                        warn.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                log.warn("Export {} als NICHT KI-validiert markiert.", exportPath);
            }

            // 1. Export individual set files & detailed PDF reports
            for (CombinedPass cp : finalSelectedPasses) {
                int passNum = cp.getPassNumber();
                double btDd = cp.getBtDd();
                int ddPct = Double.isNaN(btDd) ? 0 : (int) Math.round(btDd);
                String baseFileName = String.format("%s_%s_%s_%dproz_Pass%d", eaName, symbol, timeframe, ddPct, passNum);

                // Set file (parameter merge shared with Step-7 validation)
                java.nio.file.Path setFile = exportPath.resolve(baseFileName + ".set");
                List<EaParameter> finalParams = buildFinalParams(cp);
                eaParamManager.writeSetFile(setFile, finalParams, eaName);
                log.info("Exported preset file to {}", setFile);

                // PDF file
                java.io.File pdfFile = exportPath.resolve(baseFileName + "_Report.pdf").toFile();
                com.backtester.report.PdfReportGenerator.generateReport(this, cp, pdfFile);
                log.info("Exported detailed report to {}", pdfFile);
            }

            // 2. Export combined portfolio PDF report
            String combinedReportName = String.format("Portfolio_Report_%s_%s_%s.pdf", eaName, symbol, timeframe);
            java.io.File combinedPdfFile = exportPath.resolve(combinedReportName).toFile();
            com.backtester.report.PdfReportGenerator.generatePortfolioReport(this, finalSelectedPasses, combinedPdfFile);
            log.info("Exported combined portfolio report to {}", combinedPdfFile);

            // 3. Copy good and stable strategies (KI score >= 70) to the target best directory.
            //    If Step-7 validation results exist, the strategy must also have PASSED
            //    the out-of-sample validation — failed strategies never reach "best".
            java.nio.file.Path bestPath = java.nio.file.Paths.get(bestDirStr);
            boolean createdBestDir = false;

            for (CombinedPass cp : finalSelectedPasses) {
                int kiScore = getKiScore(cp.getPassNumber());
                if (!isValidationPassedOrPending(cp.getPassNumber())) {
                    log.warn("Pass {} wird NICHT in den Best-Ordner kopiert: Out-of-Sample-Validierung nicht bestanden.", cp.getPassNumber());
                    continue;
                }
                // Stale warning from an earlier validation run? The pass is
                // no longer FAILED — remove our own marker before re-exporting.
                try {
                    java.nio.file.Files.deleteIfExists(validationWarnFile(bestPath, cp.getPassNumber()));
                } catch (Exception cleanupEx) {
                    log.warn("Konnte alte Validierungs-Warndatei für Pass {} nicht entfernen: {}",
                            cp.getPassNumber(), cleanupEx.getMessage());
                }
                if (kiScore >= 70) {
                    if (!createdBestDir) {
                        java.nio.file.Files.createDirectories(bestPath);
                        createdBestDir = true;
                    }
                    int passNum = cp.getPassNumber();
                    double btDd = cp.getBtDd();
                    int ddPct = Double.isNaN(btDd) ? 0 : (int) Math.round(btDd);
                    String baseFileName = String.format("%s_%s_%s_%dproz_Pass%d", eaName, symbol, timeframe, ddPct, passNum);

                    java.nio.file.Path srcSet = exportPath.resolve(baseFileName + ".set");
                    java.nio.file.Path destSet = bestPath.resolve(baseFileName + ".set");
                    if (java.nio.file.Files.exists(srcSet)) {
                        java.nio.file.Files.copy(srcSet, destSet, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        log.info("Copied stable preset file to best folder: {}", destSet);
                    }

                    java.nio.file.Path srcPdf = exportPath.resolve(baseFileName + "_Report.pdf");
                    java.nio.file.Path destPdf = bestPath.resolve(baseFileName + "_Report.pdf");
                    if (java.nio.file.Files.exists(srcPdf)) {
                        java.nio.file.Files.copy(srcPdf, destPdf, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        log.info("Copied stable detailed report to best folder: {}", destPdf);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to export portfolio to " + exportDirStr, e);
            throw new RuntimeException("Export failed: " + e.getMessage(), e);
        }
    }

    /**
     * Serializes the current complete workflow state and saves it in the history database under type "Workflow".
     */
    public void saveWorkflowToHistory() {
        try {
            com.google.gson.Gson gson = buildGson();
            Map<String, Object> stateMap = new HashMap<>();
            stateMap.put("expert_name", expert);
            stateMap.put("symbol", symbol);
            stateMap.put("period", period);
            stateMap.put("from_date", fromDate != null ? fromDate.toString() : null);
            stateMap.put("to_date", toDate != null ? toDate.toString() : null);
            stateMap.put("deposit", deposit);
            stateMap.put("currency", currency);
            stateMap.put("leverage", leverage);
            stateMap.put("tick_model", tickModel);
            stateMap.put("ea_parameters_json", gson.toJson(eaParameters));
            stateMap.put("opt_result_json", gson.toJson(optResult));
            stateMap.put("selected_diverse_passes_json", gson.toJson(selectedDiversePasses));
            stateMap.put("sensitivity_results_json", gson.toJson(sensitivityResults));
            stateMap.put("ki_report_text", kiReportText);
            stateMap.put("final_selected_passes_json", gson.toJson(finalSelectedPasses));
            stateMap.put("last_active_step", lastActiveStep);
            stateMap.put("validation_results_json", gson.toJson(validationResults));
            stateMap.put("ki_gate_bypassed", kiGateBypassed);

            String stateJson = gson.toJson(stateMap);
            DatabaseManager.getInstance().saveRun("Workflow", expert, System.currentTimeMillis(), stateJson, "");
            log.info("Workflow run successfully saved to HISTORY_RUNS database.");
        } catch (Exception e) {
            log.error("Failed to save workflow run to history database", e);
        }
    }

    /**
     * Restores the workflow state from a serialized JSON string and saves it as the active state.
     */
    @SuppressWarnings("unchecked")
    public void restoreWorkflowState(String stateJson) {
        try {
            com.google.gson.Gson gson = buildGson();
            Map<String, Object> stateMap = gson.fromJson(stateJson, Map.class);
            if (stateMap == null) return;

            this.expert = (String) stateMap.get("expert_name");
            this.symbol = (String) stateMap.get("symbol");
            this.period = (String) stateMap.get("period");
            if (stateMap.get("from_date") != null) this.fromDate = LocalDate.parse((String) stateMap.get("from_date"));
            if (stateMap.get("to_date") != null) this.toDate = LocalDate.parse((String) stateMap.get("to_date"));
            if (stateMap.get("deposit") != null) this.deposit = ((Double) stateMap.get("deposit")).intValue();
            this.currency = (String) stateMap.get("currency");
            this.leverage = (String) stateMap.get("leverage");
            if (stateMap.get("tick_model") != null) this.tickModel = ((Double) stateMap.get("tick_model")).intValue();

            // ea parameters
            java.lang.reflect.Type paramType = new com.google.gson.reflect.TypeToken<List<EaParameter>>(){}.getType();
            String eaParamsJson = (String) stateMap.get("ea_parameters_json");
            if (eaParamsJson != null && !eaParamsJson.isEmpty()) {
                this.eaParameters = gson.fromJson(eaParamsJson, paramType);
                if (this.eaParameters != null) {
                    eaParamManager.applyTranslations(this.expert, this.eaParameters);
                }
            } else {
                this.eaParameters = new ArrayList<>();
            }

            // opt result
            String optResultJson = (String) stateMap.get("opt_result_json");
            if (optResultJson != null && !optResultJson.isEmpty()) {
                this.optResult = gson.fromJson(optResultJson, OptimizationResult.class);
            } else {
                this.optResult = null;
            }

            // selected diverse passes
            java.lang.reflect.Type cpListType = new com.google.gson.reflect.TypeToken<List<CombinedPass>>(){}.getType();
            String selectedDiverseJson = (String) stateMap.get("selected_diverse_passes_json");
            if (selectedDiverseJson != null && !selectedDiverseJson.isEmpty()) {
                this.selectedDiversePasses = gson.fromJson(selectedDiverseJson, cpListType);
            } else {
                this.selectedDiversePasses = new ArrayList<>();
            }

            // sensitivity results
            java.lang.reflect.Type sensListType = new com.google.gson.reflect.TypeToken<List<SensitivityResult>>(){}.getType();
            String sensitivityJson = (String) stateMap.get("sensitivity_results_json");
            if (sensitivityJson != null && !sensitivityJson.isEmpty()) {
                this.sensitivityResults = gson.fromJson(sensitivityJson, sensListType);
            } else {
                this.sensitivityResults = new ArrayList<>();
            }

            this.kiReportText = (String) stateMap.get("ki_report_text");
            if (this.kiReportText == null) this.kiReportText = "";

            // final selected passes
            String finalSelectedJson = (String) stateMap.get("final_selected_passes_json");
            if (finalSelectedJson != null && !finalSelectedJson.isEmpty()) {
                this.finalSelectedPasses = gson.fromJson(finalSelectedJson, cpListType);
            } else {
                this.finalSelectedPasses = new ArrayList<>();
            }

            if (stateMap.get("last_active_step") != null) {
                this.lastActiveStep = ((Double) stateMap.get("last_active_step")).intValue();
            } else {
                this.lastActiveStep = 0;
            }

            // validation results (Step 7)
            this.validationResults = parseValidationResults(gson, (String) stateMap.get("validation_results_json"));
            Object bypassed = stateMap.get("ki_gate_bypassed");
            this.kiGateBypassed = bypassed instanceof Boolean && (Boolean) bypassed;

            // Save this restored state as the current active one in WORKFLOW_STATE table
            saveState();
            log.info("Successfully restored workflow state from database history.");
        } catch (Exception e) {
            log.error("Failed to restore workflow state", e);
            throw new RuntimeException("Restaurierung fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    /**
     * Computes a weighted final score combining performance (Combined Score) and
     * stability (KI Score). Both scores are on a 0-100 scale.
     *
     * If no KI score is available, falls back to Combined Score only.
     */
    private double computeWeightedFinalScore(CombinedPass cp) {
        double combinedScore = cp.getScore(); // 0-100 scale
        int kiScore = getKiScore(cp.getPassNumber());

        if (kiScore < 0) {
            // No KI score available — use Combined Score only
            return combinedScore;
        }

        return performanceWeight * combinedScore + stabilityWeight * kiScore;
    }

    /**
     * Worst-case CV for a pass from the sensitivity analysis.
     *
     * @return the CV, or {@link Double#NaN} when no sensitivity data exists
     *         for the pass. NaN (displayed as "n/a") instead of 0.0, because
     *         0.0 would make missing data look like perfect robustness.
     */
    public double getWorstCvForPass(int passNum, boolean forward) {
        if (sensitivityResults == null) return Double.NaN;
        for (SensitivityResult sr : sensitivityResults) {
            if (sr.getOriginalPass() != null && sr.getOriginalPass().getPassNumber() == passNum) {
                return forward ? sr.getOverallCVFw() : sr.getOverallCV();
            }
        }
        return Double.NaN;
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

    /**
     * True when no Step-7 validation has been run yet (pending), or when the
     * pass explicitly PASSED. Once validation results exist, every other state
     * (FAILED, INSUFFICIENT_EVIDENCE, ERROR, NO_TRADES, or missing result) blocks the best-folder
     * export.
     */
    private boolean isValidationPassedOrPending(int passNum) {
        if (validationResults == null || validationResults.isEmpty()) return true;
        for (ValidationResult vr : validationResults) {
            if (vr.getPassNumber() == passNum) {
                return ValidationResult.PASSED.equals(vr.getVerdict());
            }
        }
        return false;
    }

    /** Returns the Step-7 validation result for a pass, or null if not validated. */
    public ValidationResult getValidationResultForPass(int passNum) {
        if (validationResults == null) return null;
        for (ValidationResult vr : validationResults) {
            if (vr.getPassNumber() == passNum) return vr;
        }
        return null;
    }

    /** Deserialises the Step-7 validation results; empty list on null/empty JSON. */
    private static List<ValidationResult> parseValidationResults(com.google.gson.Gson gson, String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();
        java.lang.reflect.Type valType = new com.google.gson.reflect.TypeToken<List<ValidationResult>>(){}.getType();
        List<ValidationResult> loaded = gson.fromJson(json, valType);
        if (loaded == null) return new ArrayList<>();
        for (ValidationResult result : loaded) {
            if (result != null && !ValidationResult.ERROR.equals(result.getVerdict())) {
                result.computeVerdict();
            }
        }
        return loaded;
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
            if (!isPositiveFinite(cp.getBtProfit()) || cp.getBtProfit() < minBtProfit) continue;
            if (cp.getBtTrades() < minBtTrades) continue;
            if (!meetsMinimum(cp.getBtRecovery(), minBtRecovery)) continue;
            if (cp.getBtDd() > maxBtDd) continue;
            if (forwardMode > 0) {
                if (!isPositiveFinite(cp.getFwProfit()) || cp.getFwProfit() < minFwProfit) continue;
                if (cp.getFwTrades() < minFwTrades) continue;
                if (!meetsMinimum(cp.getFwRecovery(), minFwRecovery)) continue;
                if (cp.getFwDd() > maxFwDd) continue;
            }
            if (cp.getLongtermPass() != null) {
                if (!isPositiveFinite(cp.getLtProfit()) || cp.getLtProfit() < minLtProfit) continue;
                if (cp.getLtTrades() < minLtTrades) continue;
                if (!meetsMinimum(cp.getLtRecovery(), minLtRecovery)) continue;
                if (cp.getLtDd() > maxLtDd) continue;
                if (!meetsMinimum(cp.getLtPf(), minLtPf)) continue;
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

    private static boolean isPositiveFinite(double value) {
        return Double.isFinite(value) && value > 0.0;
    }

    private static boolean meetsMinimum(double value, double minimum) {
        return Double.isFinite(value) && value >= minimum;
    }

    private static double finiteAtLeast(double value, double minimum) {
        return Double.isFinite(value) ? Math.max(minimum, value) : minimum;
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
        if (currentValidationRunner != null) {
            currentValidationRunner.cancel();
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
    public void setMinBtProfit(double minBtProfit) { this.minBtProfit = finiteAtLeast(minBtProfit, MIN_POSITIVE_PROFIT); }

    public double getMinFwProfit() { return minFwProfit; }
    public void setMinFwProfit(double minFwProfit) { this.minFwProfit = finiteAtLeast(minFwProfit, MIN_POSITIVE_PROFIT); }

    public int getMinBtTrades() { return minBtTrades; }
    public void setMinBtTrades(int minBtTrades) { this.minBtTrades = Math.max(1, minBtTrades); }

    public int getMinFwTrades() { return minFwTrades; }
    public void setMinFwTrades(int minFwTrades) { this.minFwTrades = Math.max(1, minFwTrades); }

    public double getMinBtRecovery() { return minBtRecovery; }
    public void setMinBtRecovery(double minBtRecovery) { this.minBtRecovery = finiteAtLeast(minBtRecovery, 0.0); }

    public double getMinFwRecovery() { return minFwRecovery; }
    public void setMinFwRecovery(double minFwRecovery) { this.minFwRecovery = finiteAtLeast(minFwRecovery, 0.0); }

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

    public boolean isKiGateBypassed() { return kiGateBypassed; }

    public LocalDate getValidationFromDate() { return validationFromDate; }
    public void setValidationFromDate(LocalDate validationFromDate) { this.validationFromDate = validationFromDate; }

    public LocalDate getValidationToDate() { return validationToDate; }
    public void setValidationToDate(LocalDate validationToDate) { this.validationToDate = validationToDate; }

    public List<ValidationResult> getValidationResults() { return validationResults; }
    public void setValidationResults(List<ValidationResult> validationResults) {
        this.validationResults = validationResults != null ? validationResults : new ArrayList<>();
    }

    public String getLastExportDirectory() { return lastExportDirectory; }

    public double getPerformanceWeight() { return performanceWeight; }
    public void setPerformanceWeight(double performanceWeight) { this.performanceWeight = performanceWeight; }

    public double getStabilityWeight() { return stabilityWeight; }
    public void setStabilityWeight(double stabilityWeight) { this.stabilityWeight = stabilityWeight; }
}
