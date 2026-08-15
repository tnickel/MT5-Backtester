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
    public static final String GRID_QUALITY_DATABANK = "g01_grid_quality";
    public static final String GRID_IS_OOS_DATABANK = "g01_grid_is_oos";
    public static final String GRID_SHORTLIST_DATABANK = "g01_grid_shortlist";
    public static final String GRID_TICK_DATABANK = "g01_grid_tick";
    public static final String GRID_CLUSTER_DATABANK = "g01_grid_pick";
    public static final int GRID_SHORTLIST_MAX_STRATEGIES = 100;
    public static final int GRID_HALF_MIN_TRADES = 500;
    public static final double GRID_HALF_MIN_PROFIT = 600;
    public static final int GRID_TICK_MIN_TRADES = 400;
    public static final double GRID_TICK_MIN_PROFIT = 200;
    private static final int DEVELOPMENT_TOP20_MIN_DIFFERENT_PARAMS = 2;
    private static final int GRID_CLUSTER_MIN_DIFFERENT_PARAMS = 1;

    private static final String PERIOD_H1 = "16385";
    private static final Set<String> FORM_DISTANCE_NAMES = Set.of(
            "Inp_Grid_Step", "Inp_Step_Multiplier", "Inp_Next_Lot_Multiplier", "Inp_TakeProfit",
            "Inp_Envelopes_Period", "Inp_Envelopes_Deviation",
            "Inp_Envelopes_Period_Lower", "Inp_Envelopes_Deviation_Lower");

    /**
     * Pair-aware search stages. g01 is one genetic pass over every trading knob
     * (complex criterion, 1:1 forward). Later stages refine one section at a time
     * so the B-cluster paths can be inspected. Envelope timeframes stay H1.
     */
    private static List<Stage> stagesFor(String symbol) {
        List<Stage> sections = sectionStages();
        Map<String, Range> first = new LinkedHashMap<>();
        for (Range range : firstPassRanges(isJpyPair(symbol))) {
            first.putIfAbsent(range.parameterName(), range);
        }
        for (Stage section : sections) {
            for (Range range : section.ranges()) {
                first.putIfAbsent(range.parameterName(), range);
            }
        }
        List<Stage> stages = new ArrayList<>();
        stages.add(new Stage("01 Grid-Fundament", "g01_grid", 2, List.copyOf(first.values())));
        stages.addAll(sections);
        return List.copyOf(stages);
    }

    private static List<Range> firstPassRanges(boolean jpy) {
        List<Range> ranges = new ArrayList<>();
        ranges.add(range("Inp_Grid_Step", jpy ? "1200" : "400", jpy ? "100" : "25", jpy ? "2500" : "900"));
        ranges.add(range("Inp_Step_Multiplier", jpy ? "1.10" : "1.05", "0.05", jpy ? "1.40" : "1.35"));
        ranges.add(range("Inp_Next_Lot_Multiplier", jpy ? "1.20" : "1.15", "0.05", jpy ? "1.60" : "1.45"));
        ranges.add(range("Inp_TakeProfit", "40", "5", "80"));
        ranges.add(range("Inp_Envelopes_Period", "8", "2", "20"));
        ranges.add(range("Inp_Envelopes_Deviation", "0.08", "0.01", "0.40"));
        ranges.add(range("Inp_Envelopes_Period_Lower", "10", "2", "24"));
        ranges.add(range("Inp_Envelopes_Deviation_Lower", "0.10", "0.01", "0.50"));
        ranges.add(range("Inp_Use_Trend_Filter", "false", "1", "true"));
        ranges.add(range("Inp_Trend_EMA_Period", "100", "25", "400"));
        ranges.add(range("Inp_Use_RSI_Filter", "false", "1", "true"));
        ranges.add(range("Inp_RSI_Period", "7", "2", "31"));
        ranges.add(range("Inp_RSI_Oversold", "15", "2.5", "35"));
        ranges.add(range("Inp_RSI_Overbought", "65", "2.5", "85"));
        ranges.add(range("Inp_Use_ER_Filter", "false", "1", "true"));
        ranges.add(range("Inp_ER_Period", "5", "1", "20"));
        ranges.add(timeframeRange("Inp_ER_Timeframe"));
        ranges.add(range("Inp_ER_Max_Level", "0.10", "0.05", "0.60"));
        ranges.add(range("Inp_Use_D1_Trend_Filter", "false", "1", "true"));
        ranges.add(range("Inp_D1_Trend_EMA_Period", "50", "25", "300"));
        ranges.add(range("Inp_Use_BreakEven", "false", "1", "true"));
        ranges.add(range("Inp_BE_Trigger_Points", "50", "25", "300"));
        ranges.add(range("Inp_BE_Points", "10", "10", "100"));
        ranges.add(range("Inp_Max_DD_Percent", "10", "5", "50"));
        ranges.add(range("Inp_Halt_After_DD_Stop", "false", "1", "true"));
        ranges.add(range("Inp_Use_Trailing_TP", "false", "1", "true"));
        ranges.add(range("Inp_Use_Midline_TP", "false", "1", "true"));
        ranges.add(range("Inp_Use_ATR_TP", "false", "1", "true"));
        ranges.add(range("Inp_ATR_TP_Multiplier", "0.3", "0.1", "1.0"));
        ranges.add(range("Inp_Stale_Basket_Hours", "0", "6", "30"));
        ranges.add(range("Inp_Soft_DD_Percent", "0", "3", "12"));
        ranges.add(range("Inp_Deep_Basket_Level", "0", "1", "6"));
        ranges.add(range("Inp_Deep_Basket_BE_Points", "5", "5", "25"));
        ranges.add(range("Inp_Use_VIX_Filter", "false", "1", "true"));
        ranges.add(range("Inp_VIX_Max_Level", "15", "2.5", "50"));
        return ranges;
    }

    private static List<Stage> sectionStages() {
        return List.of(
            stage("02 Order-Taktung", "g02_cadence",
                    range("Min_Profit", "3", "1", "9"),
                    range("Inp_Wait_Open_Equal_Orders", "20", "5", "50"),
                    range("Inp_Wait_Next_Lot", "420", "60", "780"),
                    range("Inp_Start_Wait_Next_Lot", "1", "1", "3"),
                    range("Inp_Stop_Wait_Next_Lot", "80", "10", "120")),
            geneticStage("03 Envelopes oben", "g03_env_upper",
                    range("Inp_Envelopes_Period", "8", "2", "20"),
                    range("Inp_Envelopes_Deviation", "0.08", "0.01", "0.40"),
                    range("Envelopes_Method", "0", "1", "3"),
                    range("Envelopes_Price", "1", "1", "7")),
            geneticStage("04 Envelopes unten", "g04_env_lower",
                    range("Inp_Envelopes_Period_Lower", "10", "2", "24"),
                    range("Inp_Envelopes_Deviation_Lower", "0.10", "0.01", "0.50"),
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
                    range("Inp_Use_Session_Filter", "false", "1", "true")));
    }

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
        String sym = symbol != null && !symbol.isBlank() ? symbol.trim() : "AUDCAD";
        String per = period != null && !period.isBlank() ? period.trim() : "M5";

        CustomProject project = new CustomProject(projectName, "ToTheMoon_KI_v132", sym, per);
        project.setSaveDatabanksPersistently(true);

        String previousPick = appendSearchChain(project, provenPreset, optimizerReportsRoot);

        WorkflowTask developmentTop20 = createDevelopmentTop20Task(
                previousPick, formDistanceSnapshot(project), sym, per);
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
     * Selection plus the 11 M1-OHLC optimizer stages. MT5 ranks by complex
     * criterion; the PRE_FILTER tasks only forward the raw databank.
     *
     * @return last pick databank, currently {@code g11_safety_pick}
     */
    public static String appendSearchChain(CustomProject project,
                                           List<EaParameter> provenPreset,
                                           Path optimizerReportsRoot) {
        return appendSearchChain(project, provenPreset, optimizerReportsRoot, false);
    }

    /**
     * Selection plus the 11 M1-OHLC optimizer stages. MT5 ranks by complex
     * criterion; the PRE_FILTER tasks only forward the raw databank.
     *
     * @param insertMasterReferences when true, a {@code MASTER_REFERENCE} checkpoint
     *        follows each stage pick so the master lineage is filled after every
     *        optimization without waiting for the next optimizer's hand-pick
     * @return last pick databank, currently {@code g11_safety_pick}
     */
    public static String appendSearchChain(CustomProject project,
                                           List<EaParameter> provenPreset,
                                           Path optimizerReportsRoot,
                                           boolean insertMasterReferences) {
        if (project == null) {
            throw new IllegalArgumentException("Projekt fehlt.");
        }
        if (provenPreset == null || provenPreset.isEmpty()) {
            throw new IllegalArgumentException("Das bewährte ToTheMoon132-Preset ist leer.");
        }

        String sym = project.getSymbol() != null && !project.getSymbol().isBlank()
                ? project.getSymbol().trim() : "AUDCAD";
        String per = project.getPeriod() != null && !project.getPeriod().isBlank()
                ? project.getPeriod().trim() : "M5";
        List<Stage> stages = stagesFor(sym);
        List<EaParameter> workingPreset = applyArchitectureBaseline(provenPreset, sym);
        if (project.getProvenMasterParameters() == null || project.getProvenMasterParameters().isEmpty()) {
            project.setProvenMasterParameters(workingPreset);
        }
        validateRequiredParameters(indexParameters(workingPreset), stages);

        WorkflowTask selection = new WorkflowTask("00 Strategie-Auswahl — ToTheMoon132 " + sym + " " + per,
                WorkflowTask.TaskType.STRATEGY_SELECTION);
        configureMarket(selection, sym, per, DEVELOPMENT_FROM, DEVELOPMENT_TO, WorkflowTask.MODE_OHLC_M1);
        selection.setSourceDatabank(DatabankManager.RESULTS);
        selection.setTargetDatabank(DatabankManager.RESULTS);
        project.addTask(selection);

        String previousPick = DatabankManager.RESULTS;
        for (int i = 0; i < stages.size(); i++) {
            Stage stage = stages.get(i);
            WorkflowTask optimizer = new WorkflowTask(stage.name + " — Optimizer", WorkflowTask.TaskType.OPTIMIZER);
            configureMarket(optimizer, sym, per, DEVELOPMENT_FROM, DEVELOPMENT_TO, WorkflowTask.MODE_OHLC_M1);
            optimizer.setSourceDatabank(previousPick);
            optimizer.setTargetDatabank(stage.databankPrefix + "_raw");
            optimizer.setOptimizerMode(stage.optimizerMode);
            optimizer.setOptimizerCriterion(7); // MetaQuotes complex criterion (equity, risk, sample).
            optimizer.setOptimizerForwardMode(1); // 1:1 = last half is forward.
            java.time.LocalDate fwdStart = com.backtester.engine.ForwardSplit.computeForwardStartDate(
                    java.time.LocalDate.parse(DEVELOPMENT_FROM), java.time.LocalDate.parse(DEVELOPMENT_TO), 1, null);
            optimizer.setOptimizerForwardDate(fwdStart != null ? fwdStart.toString() : FORWARD_FROM);
            optimizer.setDeleteFailed(false);
            if (optimizerReportsRoot != null) {
                optimizer.setOptimizerOutputDirectory(
                        optimizerReportsRoot.resolve(stage.databankPrefix).toAbsolutePath().normalize().toString());
            }
            optimizer.setOptimizerTargetParameters(stage.targetNames());
            optimizer.setOptimizerParameterSnapshot(buildStageSnapshot(workingPreset, stage));
            optimizer.setOptimizerParameterBasisAdopted(false);
            optimizer.setOptimizerParameterBasisPassNumber(-1);
            optimizer.setOptimizerParameterBasisDatabank("");
            project.addTask(optimizer);

            WorkflowTask filter = new WorkflowTask(stage.name + " — Trade/Qualitätsfilter",
                    WorkflowTask.TaskType.PRE_FILTER);
            configureMarket(filter, sym, per, DEVELOPMENT_FROM, DEVELOPMENT_TO, WorkflowTask.MODE_OHLC_M1);
            filter.setSourceDatabank(stage.databankPrefix + "_raw");
            boolean firstGridStage = i == 0;
            filter.setTargetDatabank(firstGridStage ? GRID_QUALITY_DATABANK : stage.databankPrefix + "_pick");
            filter.setDeleteFailed(true);
            filter.setFilterConditions(List.of());
            project.addTask(filter);
            if (firstGridStage) {
                project.addTask(createGridIsOosFilter(sym, per));
                project.addTask(createGridShortlistDiversityTask(optimizer, sym, per));
                project.addTask(createGridTickGateTask(optimizer, sym, per));
                project.addTask(createGridClusterDiversityTask(optimizer, sym, per));
            }
            if (insertMasterReferences) {
                project.addTask(createMasterReferenceTask(stage, sym, per));
            }
            previousPick = stage.databankPrefix + "_pick";
        }
        return previousPick;
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
        WorkflowTask task = new WorkflowTask("12 Re-Diversität der Überlebenden (B-Cluster)",
                WorkflowTask.TaskType.DIVERSITY_FILTER);
        configureMarket(task, symbol, period,
                DEVELOPMENT_FROM, DEVELOPMENT_TO, WorkflowTask.MODE_OHLC_M1);
        task.setSourceDatabank(sourceDatabank);
        task.setTargetDatabank(DEVELOPMENT_TOP20_DATABANK);
        task.setDiversityRankByScore(true);
        task.setDiversityDeduplicateEffectiveV132(true);
        task.setDiversityParamDiffPct(WorkflowTask.DEFAULT_DIVERSITY_PARAM_DIFF_PCT);
        task.setDiversityTradeDiffPct(WorkflowTask.DEFAULT_DIVERSITY_TRADE_DIFF_PCT);
        task.setDiversityMinDifferentParams(DEVELOPMENT_TOP20_MIN_DIFFERENT_PARAMS);
        task.setDiversityMaxStrategies(ClusterIdentity.MAX_CLUSTERS);
        task.setDiversityParameterSnapshot(comparisonParameters);
        task.setDeleteFailed(true);
        return task;
    }

    /**
     * Form-distance snapshot: only grid + envelope bands, even when g01 searches
     * every knob. Existing {@code clusterId} values are kept by {@link ClusterIdentity}.
     */
    static List<EaParameter> formDistanceSnapshot(CustomProject project) {
        WorkflowTask grid = findOptimizerByStagePrefix(project, "01 Grid-Fundament");
        if (grid == null) {
            return List.of();
        }
        Map<String, EaParameter> byName = new LinkedHashMap<>();
        for (EaParameter parameter : formParametersFrom(grid.getOptimizerParameterSnapshot())) {
            byName.put(parameter.getName(), parameter);
        }
        mergeOptimizeEnabled(byName, findOptimizerByStagePrefix(project, "03 Envelopes oben"));
        mergeOptimizeEnabled(byName, findOptimizerByStagePrefix(project, "04 Envelopes unten"));
        for (EaParameter parameter : byName.values()) {
            if (parameter != null && !FORM_DISTANCE_NAMES.contains(parameter.getName())) {
                parameter.setOptimizeEnabled(false);
            }
        }
        return new ArrayList<>(byName.values());
    }

    private static List<EaParameter> formParametersFrom(List<EaParameter> snapshot) {
        List<EaParameter> result = new ArrayList<>();
        if (snapshot == null) return result;
        for (EaParameter parameter : snapshot) {
            if (parameter == null) continue;
            EaParameter copy = parameter.copy();
            copy.setOptimizeEnabled(FORM_DISTANCE_NAMES.contains(copy.getName()));
            result.add(copy);
        }
        return result;
    }

    private static void mergeOptimizeEnabled(Map<String, EaParameter> byName, WorkflowTask optimizer) {
        if (optimizer == null) return;
        for (EaParameter source : optimizer.getOptimizerParameterSnapshot()) {
            if (source == null || !source.isOptimizeEnabled()) continue;
            EaParameter target = byName.get(source.getName());
            if (target == null) {
                byName.put(source.getName(), source.copy());
                continue;
            }
            target.setOptimizeEnabled(true);
            target.setOptimizeStart(source.getOptimizeStart());
            target.setOptimizeStep(source.getOptimizeStep());
            target.setOptimizeEnd(source.getOptimizeEnd());
        }
    }

    private static WorkflowTask findOptimizerByStagePrefix(CustomProject project, String stagePrefix) {
        if (project == null || stagePrefix == null) return null;
        return project.getTasks().stream()
                .filter(task -> task.getType() == WorkflowTask.TaskType.OPTIMIZER)
                .filter(task -> task.getName() != null && task.getName().startsWith(stagePrefix))
                .findFirst()
                .orElse(null);
    }

    private static WorkflowTask createGridIsOosFilter(String symbol, String period) {
        WorkflowTask task = new WorkflowTask("01 Grid-Fundament — IS/OOS-Konsistenz (1:1)",
                WorkflowTask.TaskType.PRE_FILTER);
        configureMarket(task, symbol, period, DEVELOPMENT_FROM, DEVELOPMENT_TO, WorkflowTask.MODE_OHLC_M1);
        task.setSourceDatabank(GRID_QUALITY_DATABANK);
        task.setTargetDatabank(GRID_IS_OOS_DATABANK);
        task.setDeleteFailed(true);
        task.setFilterConditions(List.of(
                condition(FilterCondition.Metric.BT_NET_PROFIT, FilterCondition.Operator.GREATER_EQUAL, GRID_HALF_MIN_PROFIT),
                condition(FilterCondition.Metric.FW_NET_PROFIT, FilterCondition.Operator.GREATER_EQUAL, GRID_HALF_MIN_PROFIT),
                condition(FilterCondition.Metric.BT_TOTAL_TRADES, FilterCondition.Operator.GREATER_EQUAL, GRID_HALF_MIN_TRADES),
                condition(FilterCondition.Metric.FW_TOTAL_TRADES, FilterCondition.Operator.GREATER_EQUAL, GRID_HALF_MIN_TRADES)));
        return task;
    }

    private static WorkflowTask createGridShortlistDiversityTask(WorkflowTask gridOptimizer,
                                                                 String symbol,
                                                                 String period) {
        WorkflowTask task = new WorkflowTask("01 Grid-Fundament — Diversität (Shortlist 100)",
                WorkflowTask.TaskType.DIVERSITY_FILTER);
        configureMarket(task, symbol, period, DEVELOPMENT_FROM, DEVELOPMENT_TO, WorkflowTask.MODE_OHLC_M1);
        task.setSourceDatabank(GRID_IS_OOS_DATABANK);
        task.setTargetDatabank(GRID_SHORTLIST_DATABANK);
        task.setDiversityRankByScore(true);
        task.setDiversityRankByActivity(false);
        task.setDiversityDeduplicateEffectiveV132(true);
        task.setDiversityParamDiffPct(WorkflowTask.DEFAULT_DIVERSITY_PARAM_DIFF_PCT);
        task.setDiversityTradeDiffPct(WorkflowTask.DEFAULT_DIVERSITY_TRADE_DIFF_PCT);
        task.setDiversityMinDifferentParams(GRID_CLUSTER_MIN_DIFFERENT_PARAMS);
        task.setDiversityMaxStrategies(GRID_SHORTLIST_MAX_STRATEGIES);
        task.setDiversityStampClusterIds(false);
        task.setDiversityParameterSnapshot(gridOptimizer != null
                ? formParametersFrom(gridOptimizer.getOptimizerParameterSnapshot())
                : List.of());
        task.setDeleteFailed(true);
        return task;
    }

    private static WorkflowTask createGridTickGateTask(WorkflowTask gridOptimizer,
                                                       String symbol,
                                                       String period) {
        WorkflowTask task = new WorkflowTask(
                "01 Grid-Fundament — Tick-Gate (Every Tick, 1:1)",
                WorkflowTask.TaskType.RETESTER);
        configureMarket(task, symbol, period, DEVELOPMENT_FROM, DEVELOPMENT_TO, WorkflowTask.MODE_EVERY_TICK);
        task.setSourceDatabank(GRID_SHORTLIST_DATABANK);
        task.setTargetDatabank(GRID_TICK_DATABANK);
        task.setOptimizerForwardMode(1);
        java.time.LocalDate fwdStart = com.backtester.engine.ForwardSplit.computeForwardStartDate(
                java.time.LocalDate.parse(DEVELOPMENT_FROM), java.time.LocalDate.parse(DEVELOPMENT_TO), 1, null);
        task.setOptimizerForwardDate(fwdStart != null
                ? fwdStart.toString()
                : (gridOptimizer != null ? gridOptimizer.getOptimizerForwardDate() : FORWARD_FROM));
        task.setDeleteFailed(true);
        // OHLC BT/FW stay on the pass; longterm is the combined tick 1:1 result.
        task.setFilterConditions(List.of(
                condition(FilterCondition.Metric.LT_NET_PROFIT, FilterCondition.Operator.GREATER_EQUAL, GRID_TICK_MIN_PROFIT),
                condition(FilterCondition.Metric.LT_TOTAL_TRADES, FilterCondition.Operator.GREATER_EQUAL, GRID_TICK_MIN_TRADES)));
        return task;
    }

    private static WorkflowTask createGridClusterDiversityTask(WorkflowTask gridOptimizer,
                                                               String symbol,
                                                               String period) {
        WorkflowTask task = new WorkflowTask("01 Grid-Fundament — Diversität (B-Cluster)",
                WorkflowTask.TaskType.DIVERSITY_FILTER);
        configureMarket(task, symbol, period,
                DEVELOPMENT_FROM, DEVELOPMENT_TO, WorkflowTask.MODE_OHLC_M1);
        task.setSourceDatabank(GRID_TICK_DATABANK);
        task.setTargetDatabank(GRID_CLUSTER_DATABANK);
        task.setDiversityRankByScore(true);
        task.setDiversityDeduplicateEffectiveV132(true);
        task.setDiversityParamDiffPct(WorkflowTask.DEFAULT_DIVERSITY_PARAM_DIFF_PCT);
        task.setDiversityTradeDiffPct(WorkflowTask.DEFAULT_DIVERSITY_TRADE_DIFF_PCT);
        task.setDiversityMinDifferentParams(GRID_CLUSTER_MIN_DIFFERENT_PARAMS);
        task.setDiversityMaxStrategies(ClusterIdentity.MAX_CLUSTERS);
        task.setDiversityParameterSnapshot(gridOptimizer != null
                ? formParametersFrom(gridOptimizer.getOptimizerParameterSnapshot())
                : List.of());
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
        return stageTargetParameters("");
    }

    public static Map<String, List<String>> stageTargetParameters(String symbol) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Stage stage : stagesFor(symbol)) result.put(stage.name, stage.targetNames());
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

    private static WorkflowTask createMasterReferenceTask(Stage stage, String symbol, String period) {
        WorkflowTask task = new WorkflowTask(stage.name + " — Master-Referenz (OHLC 3J)",
                WorkflowTask.TaskType.MASTER_REFERENCE);
        configureMarket(task, symbol, period, DEVELOPMENT_FROM, DEVELOPMENT_TO, WorkflowTask.MODE_OHLC_M1);
        task.setSourceDatabank(stage.databankPrefix + "_pick");
        task.setTargetDatabank(stage.databankPrefix + "_master");
        task.setDeleteFailed(true);
        task.setFilterConditions(List.of());
        return task;
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

    private static void validateRequiredParameters(Map<String, EaParameter> baseByName, List<Stage> stages) {
        List<String> missing = new ArrayList<>();
        for (Stage stage : stages) {
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

    /**
     * Rewrites a poison seed (M1 envelopes, 0.01 % deviation, trend off, pair-wrong
     * grid) onto the ToTheMoon architecture before any stage snapshot is built.
     */
    static List<EaParameter> applyArchitectureBaseline(List<EaParameter> source, String symbol) {
        List<EaParameter> copy = new ArrayList<>();
        if (source == null) return copy;
        for (EaParameter parameter : source) {
            copy.add(parameter == null ? null : parameter.copy());
        }
        Map<String, EaParameter> byName = indexParameters(copy);
        boolean jpy = isJpyPair(symbol);

        setFixed(byName, "TimeFrame_Envelopes", PERIOD_H1);
        setFixed(byName, "TimeFrame_Envelopes_Lower", PERIOD_H1);
        setFixed(byName, "Values_Envelopes_Lower", "1");
        setFixed(byName, "Envelopes_Method", "0");
        setFixed(byName, "Envelopes_Price", "1");
        setFixed(byName, "Envelopes_Method_Lower", "0");
        setFixed(byName, "Envelopes_Price_Lower", "4");
        setFixed(byName, "Inp_Use_Trend_Filter", "true");
        setFixed(byName, "Inp_Trend_EMA_Period", "250");

        clampOrDefault(byName, "Inp_Envelopes_Period", 8, 20, 14);
        clampOrDefault(byName, "Inp_Envelopes_Period_Lower", 10, 24, 20);
        clampOrDefault(byName, "Inp_Envelopes_Deviation", 0.08, 0.40, jpy ? 0.30 : 0.17);
        clampOrDefault(byName, "Inp_Envelopes_Deviation_Lower", 0.10, 0.50, jpy ? 0.45 : 0.29);
        clampOrDefault(byName, "Inp_Grid_Step", jpy ? 1200 : 400, jpy ? 2500 : 900, jpy ? 2000 : 600);
        clampOrDefault(byName, "Inp_Step_Multiplier", jpy ? 1.10 : 1.05, jpy ? 1.40 : 1.35, jpy ? 1.30 : 1.15);
        clampOrDefault(byName, "Inp_Next_Lot_Multiplier", jpy ? 1.20 : 1.15, jpy ? 1.60 : 1.45, jpy ? 1.60 : 1.20);
        clampOrDefault(byName, "Inp_TakeProfit", 40, 80, 50);
        clampOrDefault(byName, "Inp_Trend_EMA_Period", 100, 400, 250);
        clampOrDefault(byName, "Inp_RSI_Period", 7, 31, 21);
        clampOrDefault(byName, "Inp_RSI_Oversold", 15, 35, 23.6);
        clampOrDefault(byName, "Inp_RSI_Overbought", 65, 85, 69.7);
        clampOrDefault(byName, "Inp_ER_Period", 5, 20, 10);
        clampOrDefault(byName, "Inp_ER_Max_Level", 0.10, 0.60, 0.30);
        clampOrDefault(byName, "Inp_D1_Trend_EMA_Period", 50, 300, 150);
        clampOrDefault(byName, "Inp_BE_Trigger_Points", 50, 300, 150);
        clampOrDefault(byName, "Inp_BE_Points", 10, 100, 30);
        clampOrDefault(byName, "Inp_Max_DD_Percent", 10, 50, 30);
        clampOrDefault(byName, "Inp_ATR_TP_Multiplier", 0.3, 1.0, 0.5);
        clampOrDefault(byName, "Inp_Deep_Basket_BE_Points", 5, 25, 10);
        clampOrDefault(byName, "Inp_VIX_Max_Level", 15, 50, 30);
        clampOrDefault(byName, "Min_Profit", 3, 9, 5);
        clampOrDefault(byName, "Inp_Wait_Open_Equal_Orders", 20, 50, 30);
        clampOrDefault(byName, "Inp_Wait_Next_Lot", 420, 780, 600);
        clampOrDefault(byName, "Inp_Stop_Wait_Next_Lot", 80, 120, 100);
        clampOrDefault(byName, "Inp_ADX_Period", 9, 31, 14);
        clampOrDefault(byName, "Inp_ADX_Max_Level", 30, 50, 30);
        clampOrDefault(byName, "Inp_ATR_Period", 5, 19, 10);
        clampOrDefault(byName, "Inp_ATR_Multiplier", 1.3, 2.9, 1.5);
        clampOrDefault(byName, "Inp_Vol_ATR_Period", 10, 24, 10);
        clampOrDefault(byName, "Inp_Vol_ATR_Max_Multiplier", 1.1, 2.0, 1.5);
        clampOrDefault(byName, "Inp_Max_Grid_Levels", 8, 16, 12);
        clampOrDefault(byName, "Inp_Emergency_SL_Buffer_Percent", 0.5, 2.0, 1.0);
        clampOrDefault(byName, "Inp_Max_Entry_Excursion_Points", 100, 300, 100);
        clampOrDefault(byName, "Inp_Trail_Start_Points", 60, 160, 100);
        clampOrDefault(byName, "Inp_Trail_Step_Points", 3, 15, 5);
        clampOrDefault(byName, "Inp_Adaptive_ADX_Ref", 20, 40, 25);
        clampOrDefault(byName, "Inp_Adaptive_Max_Widen", 1.25, 2.75, 2.0);
        clampOrDefault(byName, "Inp_Esc_Lookback_Bars", 3, 9, 6);
        clampOrDefault(byName, "Inp_Esc_ADX_Rise", 1.5, 4.5, 3.0);
        return copy;
    }

    private static boolean isJpyPair(String symbol) {
        return symbol != null && symbol.toUpperCase(Locale.ROOT).contains("JPY");
    }

    private static void setFixed(Map<String, EaParameter> byName, String name, String value) {
        EaParameter parameter = byName.get(name);
        if (parameter == null) return;
        parameter.setValue(value);
    }

    private static void clampOrDefault(Map<String, EaParameter> byName, String name,
                                       double min, double max, double fallback) {
        EaParameter parameter = byName.get(name);
        if (parameter == null) return;
        double value;
        try {
            value = Double.parseDouble(EaParameter.normalizeMql5Value(
                    parameter.getValue() != null ? parameter.getValue() : "").trim());
        } catch (NumberFormatException ex) {
            parameter.setValue(plainNumber(fallback));
            return;
        }
        if (value < min || value > max) {
            parameter.setValue(plainNumber(fallback));
        }
    }

    private static String plainNumber(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1_000_000_000d) {
            return Long.toString(Math.round(value));
        }
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
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
