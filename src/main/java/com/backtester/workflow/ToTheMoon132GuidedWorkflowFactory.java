package com.backtester.workflow;

import com.backtester.config.EaParameter;
import com.backtester.config.EaParameterManager;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Builds the AUDCAD M5 guided optimization project around a proven v132 preset. */
public final class ToTheMoon132GuidedWorkflowFactory {

    public static final String DEVELOPMENT_FROM = "2022-08-01";
    public static final String DEVELOPMENT_TO = "2025-08-01";
    public static final String FORWARD_FROM = "2024-08-01";
    public static final String OOS_FROM = "2025-08-02";
    public static final String FINAL_TO = "2026-08-01";
    public static final String DEVELOPMENT_TOP20_DATABANK = "g11_dev_top20";
    private static final int DEVELOPMENT_TOP20_MIN_DIFFERENT_PARAMS = 2;

    private static final List<Stage> STAGES = List.of(
            stage("01 Grid-Fundament", "g01_grid",
                    range("Inp_Grid_Step", "550", "25", "900"),
                    range("Inp_Step_Multiplier", "1.00", "0.05", "1.30"),
                    range("Inp_Next_Lot_Multiplier", "1.10", "0.05", "1.50")),
            stage("02 Order-Taktung", "g02_cadence",
                    range("Min_Profit", "3", "1", "9"),
                    range("Inp_Wait_Open_Equal_Orders", "20", "5", "50"),
                    range("Inp_Wait_Next_Lot", "420", "60", "780"),
                    range("Inp_Start_Wait_Next_Lot", "1", "1", "3"),
                    range("Inp_Stop_Wait_Next_Lot", "80", "10", "120")),
            geneticStage("03 Envelopes oben", "g03_env_upper",
                    timeframeRange("TimeFrame_Envelopes"),
                    range("Inp_Envelopes_Period", "3", "1", "15"),
                    // iEnvelopes expects percentage points. Keep the shipped v132
                    // domain so an incumbent such as 1.47 % remains reproducible.
                    range("Inp_Envelopes_Deviation", "0.01", "0.01", "1.70"),
                    range("Envelopes_Method", "0", "1", "3"),
                    range("Envelopes_Price", "1", "1", "7")),
            geneticStage("04 Envelopes unten", "g04_env_lower",
                    timeframeRange("TimeFrame_Envelopes_Lower"),
                    range("Inp_Envelopes_Period_Lower", "9", "2", "41"),
                    range("Inp_Envelopes_Deviation_Lower", "0.01", "0.01", "2.00"),
                    range("Envelopes_Method_Lower", "0", "1", "3"),
                    range("Envelopes_Price_Lower", "1", "1", "7")),
            stage("05 ADX-Regime", "g05_adx",
                    range("Inp_Use_ADX_Filter", "false", "1", "true"),
                    range("Inp_ADX_Period", "9", "2", "31"),
                    timeframeRange("Inp_ADX_Timeframe"),
                    range("Inp_ADX_Max_Level", "30", "2.5", "50")),
            stage("06 ATR-Gridabstand", "g06_atr_grid",
                    range("Inp_Use_ATR_Step", "false", "1", "true"),
                    range("Inp_ATR_Period", "5", "2", "19"),
                    timeframeRange("Inp_ATR_Timeframe"),
                    range("Inp_ATR_Multiplier", "1.3", "0.2", "2.9")),
            stage("07 Volatilität & Richtung", "g07_vol_corr",
                    range("Inp_Use_Vol_Filter", "false", "1", "true"),
                    range("Inp_Vol_ATR_Period", "10", "2", "24"),
                    timeframeRange("Inp_Vol_ATR_Timeframe"),
                    range("Inp_Vol_ATR_Max_Multiplier", "1.1", "0.1", "2.0"),
                    range("Inp_Use_Correlation_Filter", "false", "1", "true"),
                    range("Inp_Allow_Opposite_Direction", "false", "1", "true")),
            stage("08 Grid-Risiko & Notfall-SL", "g08_risk",
                    range("Inp_Max_Grid_Levels", "8", "1", "16"),
                    range("Inp_Use_Emergency_SL", "false", "1", "true"),
                    range("Inp_Emergency_SL_Buffer_Percent", "0.5", "0.25", "2.0")),
            stage("09 Entry-Qualität", "g09_entry",
                    range("Inp_Entry_Confirmation", "false", "1", "true"),
                    range("Inp_Entry_Confirm_Lookback", "1", "1", "4"),
                    range("Inp_Add_Confirmation", "false", "1", "true"),
                    range("Inp_Max_Entry_Excursion_Points", "100", "25", "300"),
                    range("Inp_Entry_Min_Outside_Bars", "1", "1", "3")),
            stage("10 Exit-Management", "g10_exit",
                    range("Inp_Trail_Start_Points", "60", "10", "160"),
                    range("Inp_Trail_Step_Points", "3", "2", "15")),
            stage("11 Adaptive & Safety-Gates", "g11_safety",
                    range("Inp_Use_Adaptive_Spacing", "false", "1", "true"),
                    range("Inp_Adaptive_ADX_Ref", "20", "2.5", "40"),
                    range("Inp_Adaptive_Max_Widen", "1.25", "0.25", "2.75"),
                    range("Inp_Use_Escalation_Block", "false", "1", "true"),
                    range("Inp_Esc_Lookback_Bars", "3", "1", "9"),
                    range("Inp_Esc_ADX_Rise", "1.5", "0.5", "4.5"),
                    range("Inp_Use_Session_Filter", "false", "1", "true"))
    );

    private ToTheMoon132GuidedWorkflowFactory() {
    }

    public static CustomProject create(String projectName,
                                       List<EaParameter> provenPreset,
                                       Path optimizerReportsRoot) {
        return create(projectName, "AUDCAD", "M5", provenPreset, optimizerReportsRoot);
    }

    public static CustomProject create(String projectName,
                                       String symbol,
                                       String period,
                                       List<EaParameter> provenPreset,
                                       Path optimizerReportsRoot) {
        if (provenPreset == null || provenPreset.isEmpty()) {
            throw new IllegalArgumentException("Das bewährte ToTheMoon132-Preset ist leer.");
        }
        Map<String, EaParameter> baseByName = indexParameters(provenPreset);
        validateRequiredParameters(baseByName);

        String sym = symbol != null && !symbol.isBlank() ? symbol.trim() : "AUDCAD";
        String per = period != null && !period.isBlank() ? period.trim() : "M5";

        CustomProject project = new CustomProject(projectName, "ToTheMoon_KI_v132", sym, per);
        project.setSaveDatabanksPersistently(true);

        WorkflowTask selection = new WorkflowTask("00 Strategie-Auswahl — ToTheMoon132 " + sym + " " + per,
                WorkflowTask.TaskType.STRATEGY_SELECTION);
        configureMarket(selection, sym, per, DEVELOPMENT_FROM, DEVELOPMENT_TO, WorkflowTask.MODE_OHLC_M1);
        selection.setSourceDatabank(DatabankManager.RESULTS);
        selection.setTargetDatabank(DatabankManager.RESULTS);
        project.addTask(selection);

        String previousPick = DatabankManager.RESULTS;
        for (int i = 0; i < STAGES.size(); i++) {
            Stage stage = STAGES.get(i);
            WorkflowTask optimizer = new WorkflowTask(stage.name + " — Optimizer", WorkflowTask.TaskType.OPTIMIZER);
            configureMarket(optimizer, sym, per, DEVELOPMENT_FROM, DEVELOPMENT_TO, WorkflowTask.MODE_OHLC_M1);
            optimizer.setSourceDatabank(previousPick);
            optimizer.setTargetDatabank(stage.databankPrefix + "_raw");
            optimizer.setOptimizerMode(stage.optimizerMode);
            optimizer.setOptimizerCriterion(7); // MT5 complex criterion includes sample size and risk.
            optimizer.setOptimizerForwardMode(1);
            java.time.LocalDate fwdStart = com.backtester.engine.ForwardSplit.computeForwardStartDate(
                    java.time.LocalDate.parse(DEVELOPMENT_FROM), java.time.LocalDate.parse(DEVELOPMENT_TO), 1, null);
            optimizer.setOptimizerForwardDate(fwdStart != null ? fwdStart.toString() : FORWARD_FROM);
            optimizer.setDeleteFailed(false);
            if (optimizerReportsRoot != null) {
                optimizer.setOptimizerOutputDirectory(
                        optimizerReportsRoot.resolve(stage.databankPrefix).toAbsolutePath().normalize().toString());
            }
            optimizer.setOptimizerTargetParameters(stage.targetNames());
            optimizer.setOptimizerParameterSnapshot(buildStageSnapshot(provenPreset, stage));
            optimizer.setOptimizerParameterBasisAdopted(false);
            optimizer.setOptimizerParameterBasisPassNumber(-1);
            optimizer.setOptimizerParameterBasisDatabank("");
            project.addTask(optimizer);

            WorkflowTask filter = new WorkflowTask(stage.name + " — Trade/Qualitätsfilter",
                    WorkflowTask.TaskType.PRE_FILTER);
            configureMarket(filter, sym, per, DEVELOPMENT_FROM, DEVELOPMENT_TO, WorkflowTask.MODE_OHLC_M1);
            filter.setSourceDatabank(stage.databankPrefix + "_raw");
            filter.setTargetDatabank(stage.databankPrefix + "_pick");
            filter.setDeleteFailed(true);
            filter.setFilterConditions(i == STAGES.size() - 1
                    ? strictDevelopmentFilters(sym) : developmentFilters(sym));
            project.addTask(filter);
            previousPick = stage.databankPrefix + "_pick";
        }

        WorkflowTask finalOptimizer = project.getTasks().stream()
                .filter(task -> task.getType() == WorkflowTask.TaskType.OPTIMIZER)
                .reduce((first, second) -> second).orElseThrow();
        WorkflowTask developmentTop20 = createDevelopmentTop20Task(
                previousPick, finalOptimizer.getOptimizerParameterSnapshot(), sym, per);
        project.addTask(developmentTop20);
        previousPick = DEVELOPMENT_TOP20_DATABANK;

        WorkflowTask developmentTick = createDevelopmentRetestTask(sym, per);
        project.addTask(developmentTick);

        WorkflowTask oosTick = createOosRetestTask(sym, per);
        project.addTask(oosTick);

        WorkflowTask finalFourYears = createFinalFourYearsTask(sym, per);
        project.addTask(finalFourYears);

        // OOS already made the final selection. This routing-only step publishes every
        // successful 4Y run; applying LT thresholds here would select on the combined
        // development+OOS period a second time and bias the untouched OOS decision.
        WorkflowTask finalPublication = createFinalPublicationTask(sym, per);
        project.addTask(finalPublication);

        return project;
    }

    /**
     * Loads the on-disk proven preset used when creating Guided projects.
     */
    public static List<EaParameter> loadProvenPresetFromDisk(String expert) {
        try {
            EaParameterManager manager = new EaParameterManager();
            String expertKey = expert == null || expert.isBlank() ? "ToTheMoon_KI_v132" : expert;
            List<EaParameter> custom = manager.loadCustomParameters(expertKey);
            if (custom != null && !custom.isEmpty()) {
                return custom;
            }
            List<EaParameter> defaults = manager.loadDefaultParameters(expertKey);
            return defaults != null ? defaults : List.of();
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private static WorkflowTask createDevelopmentTop20Task(String sourceDatabank,
                                                            List<EaParameter> comparisonParameters,
                                                            String symbol,
                                                            String period) {
        WorkflowTask task = new WorkflowTask("12 Top-20 nach Score & Diversität",
                WorkflowTask.TaskType.DIVERSITY_FILTER);
        configureMarket(task, symbol, period,
                DEVELOPMENT_FROM, DEVELOPMENT_TO, WorkflowTask.MODE_OHLC_M1);
        task.setSourceDatabank(sourceDatabank);
        task.setTargetDatabank(DEVELOPMENT_TOP20_DATABANK);
        task.setDiversityRankByScore(true);
        task.setDiversityDeduplicateEffectiveV132(true);
        task.setDiversityParamDiffPct(WorkflowTask.DEFAULT_DIVERSITY_PARAM_DIFF_PCT);
        task.setDiversityTradeDiffPct(WorkflowTask.DEFAULT_DIVERSITY_TRADE_DIFF_PCT);
        // Stage 11 varies six parameters. With identical performance plateaus,
        // the engine treats <= min as similar; two therefore requires at least
        // three materially different stage parameters and yields 20 candidates
        // for the persisted AUDCAD data instead of only nine with the global default.
        task.setDiversityMinDifferentParams(DEVELOPMENT_TOP20_MIN_DIFFERENT_PARAMS);
        task.setDiversityMaxStrategies(WorkflowTask.DEFAULT_DIVERSITY_MAX_STRATEGIES);
        task.setDiversityParameterSnapshot(comparisonParameters);
        task.setDeleteFailed(true);
        return task;
    }

    private static WorkflowTask createDevelopmentRetestTask(String symbol, String period) {
        WorkflowTask task = new WorkflowTask("13 Development-Retest — Every Tick (3 Jahre)",
                WorkflowTask.TaskType.RETESTER);
        configureMarket(task, symbol, period,
                DEVELOPMENT_FROM, DEVELOPMENT_TO, WorkflowTask.MODE_EVERY_TICK);
        task.setSourceDatabank(DEVELOPMENT_TOP20_DATABANK);
        task.setTargetDatabank("g12_dev_tick");
        task.setDeleteFailed(true);
        task.setFilterConditions(List.of(
                condition(FilterCondition.Metric.LT_NET_PROFIT, FilterCondition.Operator.GREATER_THAN, 0),
                condition(FilterCondition.Metric.LT_TOTAL_TRADES, FilterCondition.Operator.GREATER_EQUAL, 1200),
                condition(FilterCondition.Metric.LT_PROFIT_FACTOR, FilterCondition.Operator.GREATER_EQUAL, 1.25),
                condition(FilterCondition.Metric.LT_MAX_DD_PERCENT, FilterCondition.Operator.LESS_EQUAL, 10)));
        return task;
    }

    private static WorkflowTask createOosRetestTask(String symbol, String period) {
        WorkflowTask task = new WorkflowTask(
                "14 OOS-Retest — Every Tick (einziges finales Selektionsgate)",
                WorkflowTask.TaskType.RETESTER);
        configureMarket(task, symbol, period, OOS_FROM, FINAL_TO, WorkflowTask.MODE_EVERY_TICK);
        task.setSourceDatabank("g12_dev_tick");
        task.setTargetDatabank("g13_oos_tick");
        task.setDeleteFailed(true);
        task.setFilterConditions(List.of(
                condition(FilterCondition.Metric.LT_NET_PROFIT, FilterCondition.Operator.GREATER_THAN, 0),
                condition(FilterCondition.Metric.LT_TOTAL_TRADES, FilterCondition.Operator.GREATER_EQUAL, 350),
                condition(FilterCondition.Metric.LT_PROFIT_FACTOR, FilterCondition.Operator.GREATER_EQUAL, 1.15),
                condition(FilterCondition.Metric.LT_MAX_DD_PERCENT, FilterCondition.Operator.LESS_EQUAL, 12)));
        return task;
    }

    private static WorkflowTask createFinalFourYearsTask(String symbol, String period) {
        WorkflowTask task = new WorkflowTask(
                "15 4Y-Retest — Every Tick (durchgehender DD/Report, kein Gate)",
                WorkflowTask.TaskType.RETESTER);
        configureMarket(task, symbol, period,
                DEVELOPMENT_FROM, FINAL_TO, WorkflowTask.MODE_EVERY_TICK);
        task.setSourceDatabank("g13_oos_tick");
        task.setTargetDatabank("g14_final_4y");
        task.setDeleteFailed(true);
        task.setFilterConditions(List.of());
        return task;
    }

    private static WorkflowTask createFinalPublicationTask(String symbol, String period) {
        WorkflowTask task = new WorkflowTask(
                "16 Veröffentlichung — alle erfolgreichen 4Y-Runs (keine Zusatzfilter)",
                WorkflowTask.TaskType.PRE_FILTER);
        configureMarket(task, symbol, period,
                DEVELOPMENT_FROM, FINAL_TO, WorkflowTask.MODE_EVERY_TICK);
        task.setSourceDatabank("g14_final_4y");
        task.setTargetDatabank(DatabankManager.FINAL);
        task.setDeleteFailed(true);
        task.setFilterConditions(List.of());
        return task;
    }

    public static Map<String, List<String>> stageTargetParameters() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Stage stage : STAGES) result.put(stage.name, stage.targetNames());
        return result;
    }

    /**
     * Resolves the staged search-space parameter names from a workflow task title
     * such as {@code 04 Envelopes unten — Optimizer}.
     */
    public static Optional<List<String>> resolveStageTargetsForTaskName(String taskName) {
        if (taskName == null || taskName.isBlank()) return Optional.empty();
        String normalized = taskName.trim();
        int sep = normalized.indexOf(" — ");
        if (sep < 0) sep = normalized.indexOf(" - ");
        String stageKey = sep > 0 ? normalized.substring(0, sep).trim() : normalized;
        if (stageKey.toLowerCase(Locale.ROOT).endsWith(" optimizer")) {
            stageKey = stageKey.substring(0, stageKey.length() - " optimizer".length()).trim();
        }

        Map<String, List<String>> byStage = stageTargetParameters();
        List<String> exact = byStage.get(stageKey);
        if (exact != null && !exact.isEmpty()) {
            return Optional.of(List.copyOf(exact));
        }
        for (Map.Entry<String, List<String>> entry : byStage.entrySet()) {
            if (stageKey.equalsIgnoreCase(entry.getKey())
                    || stageKey.regionMatches(true, 0, entry.getKey(), 0, entry.getKey().length())) {
                return Optional.of(List.copyOf(entry.getValue()));
            }
        }
        return Optional.empty();
    }

    private static void configureMarket(WorkflowTask task, String symbol, String period, String from, String to, int executionMode) {
        task.setStartDate(from);
        task.setEndDate(to);
        task.setRetestSymbol(symbol != null && !symbol.isBlank() ? symbol.trim() : "AUDCAD");
        task.setRetestPeriod(period != null && !period.isBlank() ? period.trim() : "M5");
        task.setExecutionMode(executionMode);
        task.setStatus(WorkflowTask.TaskStatus.PENDING);
        task.setLastExecutionLog("");
    }

    private static List<FilterCondition> developmentFilters() {
        return developmentFilters("");
    }

    private static List<FilterCondition> strictDevelopmentFilters() {
        return strictDevelopmentFilters("");
    }

    private static List<FilterCondition> developmentFilters(String symbol) {
        double minNetProfit = "GBPJPY".equalsIgnoreCase(symbol) ? 500.0 : 0.0;
        return List.of(
                condition(FilterCondition.Metric.BT_NET_PROFIT, FilterCondition.Operator.GREATER_THAN, minNetProfit),
                condition(FilterCondition.Metric.FW_NET_PROFIT, FilterCondition.Operator.GREATER_THAN, minNetProfit),
                condition(FilterCondition.Metric.BT_TOTAL_TRADES, FilterCondition.Operator.GREATER_EQUAL, 750),
                condition(FilterCondition.Metric.FW_TOTAL_TRADES, FilterCondition.Operator.GREATER_EQUAL, 300),
                condition(FilterCondition.Metric.BT_PROFIT_FACTOR, FilterCondition.Operator.GREATER_EQUAL, 1.20),
                condition(FilterCondition.Metric.FW_PROFIT_FACTOR, FilterCondition.Operator.GREATER_EQUAL, 1.15),
                condition(FilterCondition.Metric.BT_MAX_DD_PERCENT, FilterCondition.Operator.LESS_EQUAL, 12),
                condition(FilterCondition.Metric.FW_MAX_DD_PERCENT, FilterCondition.Operator.LESS_EQUAL, 12));
    }

    private static List<FilterCondition> strictDevelopmentFilters(String symbol) {
        List<FilterCondition> filters = new ArrayList<>(developmentFilters(symbol));
        filters.add(condition(FilterCondition.Metric.BT_RECOVERY_FACTOR,
                FilterCondition.Operator.GREATER_EQUAL, 1.5));
        filters.add(condition(FilterCondition.Metric.FW_RECOVERY_FACTOR,
                FilterCondition.Operator.GREATER_EQUAL, 1.0));
        return filters;
    }

    private static FilterCondition condition(FilterCondition.Metric metric,
                                             FilterCondition.Operator operator,
                                             double value) {
        return new FilterCondition(metric, operator, value);
    }

    private static List<EaParameter> buildStageSnapshot(List<EaParameter> base, Stage stage) {
        Map<String, Range> ranges = new LinkedHashMap<>();
        for (Range range : stage.ranges) ranges.put(range.parameterName, range);

        List<EaParameter> snapshot = new ArrayList<>();
        for (EaParameter source : base) {
            if (source == null) continue;
            EaParameter copy = source.copy();
            copy.setOptimizeEnabled(false);
            Range range = ranges.get(copy.getName());
            if (range != null) {
                copy.setOptimizeStart(range.start);
                copy.setOptimizeStep(range.step);
                copy.setOptimizeEnd(range.end);
                copy.setOptimizeEnabled(true);
            } else {
                EaParameter.sanitizeTimeframeFieldsForSetFile(copy);
                EaParameter.normalizeTimeframeOptimizeBand(copy);
                EaParameter.normalizeBooleanOptimizeBand(copy);
            }
            snapshot.add(copy);
        }
        // A stage that cannot walk the value already in force may hand on a regression.
        ChampionSearchSpaceAligner.align(snapshot);
        return snapshot;
    }

    private static Map<String, EaParameter> indexParameters(List<EaParameter> parameters) {
        Map<String, EaParameter> result = new LinkedHashMap<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (EaParameter parameter : parameters) {
            if (parameter == null || parameter.isSectionHeader()
                    || parameter.getName() == null || parameter.getName().isBlank()) continue;
            if (result.putIfAbsent(parameter.getName(), parameter) != null) duplicates.add(parameter.getName());
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("Doppelte Parameter im Basis-Preset: " + String.join(", ", duplicates));
        }
        return result;
    }

    private static void validateRequiredParameters(Map<String, EaParameter> baseByName) {
        List<String> missing = new ArrayList<>();
        for (Stage stage : STAGES) {
            for (Range range : stage.ranges) {
                EaParameter parameter = baseByName.get(range.parameterName);
                if (parameter == null || parameter.isSectionHeader() || parameter.isStringType()) {
                    missing.add(range.parameterName);
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Im ToTheMoon132-Preset fehlen Zielparameter: "
                    + String.join(", ", missing));
        }
    }

    private static Stage stage(String name, String databankPrefix, Range... ranges) {
        return new Stage(name, databankPrefix, 1, List.of(ranges));
    }

    private static Stage geneticStage(String name, String databankPrefix, Range... ranges) {
        return new Stage(name, databankPrefix, 2, List.of(ranges));
    }

    private static Range range(String parameterName, String start, String step, String end) {
        return new Range(parameterName, start, step, end);
    }

    /**
     * ENUM_TIMEFRAMES search band for exhaustive MT5 runs: PERIOD_M1 (1) through
     * PERIOD_H1 (16385). PERIOD_CURRENT (0) is intentionally excluded — MT5 shows it
     * as "current" and often drops the step for that enum edge, so Opt=Y bands must
     * start at a real timeframe. MT5 walks the real enum members in the band, not
     * every integer from 1…16385. D1/W1/MN1 stay out of scope for M5 grid work.
     */
    private static Range timeframeRange(String parameterName) {
        return range(parameterName, "1", "1", "16385");
    }

    private record Range(String parameterName, String start, String step, String end) {
    }

    private record Stage(String name, String databankPrefix, int optimizerMode, List<Range> ranges) {
        private List<String> targetNames() {
            return ranges.stream().map(Range::parameterName).toList();
        }
    }
}
