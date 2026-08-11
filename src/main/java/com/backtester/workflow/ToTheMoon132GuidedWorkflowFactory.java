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
            stage("03 Envelopes oben", "g03_env_upper",
                    range("Inp_Envelopes_Period", "3", "1", "15"),
                    range("Inp_Envelopes_Deviation", "0.005", "0.005", "0.030"),
                    range("Envelopes_Method", "0", "1", "3"),
                    range("Envelopes_Price", "0", "1", "7")),
            stage("04 Envelopes unten", "g04_env_lower",
                    range("Inp_Envelopes_Period_Lower", "9", "2", "41"),
                    range("Inp_Envelopes_Deviation_Lower", "0.005", "0.005", "0.030"),
                    range("Envelopes_Method_Lower", "0", "1", "3"),
                    range("Envelopes_Price_Lower", "0", "1", "7")),
            stage("05 ADX-Regime", "g05_adx",
                    range("Inp_Use_ADX_Filter", "false", "1", "true"),
                    range("Inp_ADX_Period", "9", "2", "31"),
                    range("Inp_ADX_Max_Level", "30", "2.5", "50")),
            stage("06 ATR-Gridabstand", "g06_atr_grid",
                    range("Inp_Use_ATR_Step", "false", "1", "true"),
                    range("Inp_ATR_Period", "5", "2", "19"),
                    range("Inp_ATR_Multiplier", "1.3", "0.2", "2.9")),
            stage("07 Volatilität & Richtung", "g07_vol_corr",
                    range("Inp_Use_Vol_Filter", "false", "1", "true"),
                    range("Inp_Vol_ATR_Period", "10", "2", "24"),
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
                    range("Inp_Esc_ADX_Rise", "1.5", "0.5", "4.5"))
    );

    private ToTheMoon132GuidedWorkflowFactory() {
    }

    public static CustomProject create(String projectName,
                                       List<EaParameter> provenPreset,
                                       Path optimizerReportsRoot) {
        if (provenPreset == null || provenPreset.isEmpty()) {
            throw new IllegalArgumentException("Das bewährte ToTheMoon132-Preset ist leer.");
        }
        Map<String, EaParameter> baseByName = indexParameters(provenPreset);
        validateRequiredParameters(baseByName);

        CustomProject project = new CustomProject(projectName, "ToTheMoon_KI_v132", "AUDCAD", "M5");
        project.setSaveDatabanksPersistently(true);

        WorkflowTask selection = new WorkflowTask("00 Strategie-Auswahl — ToTheMoon132 AUDCAD M5",
                WorkflowTask.TaskType.STRATEGY_SELECTION);
        configureMarket(selection, DEVELOPMENT_FROM, DEVELOPMENT_TO, WorkflowTask.MODE_OHLC_M1);
        selection.setSourceDatabank(DatabankManager.RESULTS);
        selection.setTargetDatabank(DatabankManager.RESULTS);
        project.addTask(selection);

        String previousPick = DatabankManager.RESULTS;
        for (int i = 0; i < STAGES.size(); i++) {
            Stage stage = STAGES.get(i);
            WorkflowTask optimizer = new WorkflowTask(stage.name + " — Optimizer", WorkflowTask.TaskType.OPTIMIZER);
            configureMarket(optimizer, DEVELOPMENT_FROM, DEVELOPMENT_TO, WorkflowTask.MODE_OHLC_M1);
            optimizer.setSourceDatabank(previousPick);
            optimizer.setTargetDatabank(stage.databankPrefix + "_raw");
            optimizer.setOptimizerMode(1); // Small staged spaces: exhaustive search, no genetic blind spots.
            optimizer.setOptimizerCriterion(7); // MT5 complex criterion includes sample size and risk.
            optimizer.setOptimizerForwardMode(4);
            optimizer.setOptimizerForwardDate(FORWARD_FROM);
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
            configureMarket(filter, DEVELOPMENT_FROM, DEVELOPMENT_TO, WorkflowTask.MODE_OHLC_M1);
            filter.setSourceDatabank(stage.databankPrefix + "_raw");
            filter.setTargetDatabank(stage.databankPrefix + "_pick");
            filter.setDeleteFailed(true);
            filter.setFilterConditions(i == STAGES.size() - 1
                    ? strictDevelopmentFilters() : developmentFilters());
            project.addTask(filter);
            previousPick = stage.databankPrefix + "_pick";
        }

        WorkflowTask finalOptimizer = project.getTasks().stream()
                .filter(task -> task.getType() == WorkflowTask.TaskType.OPTIMIZER)
                .reduce((first, second) -> second).orElseThrow();
        WorkflowTask developmentTop20 = createDevelopmentTop20Task(
                previousPick, finalOptimizer.getOptimizerParameterSnapshot());
        project.addTask(developmentTop20);
        previousPick = DEVELOPMENT_TOP20_DATABANK;

        WorkflowTask developmentTick = new WorkflowTask("13 Development-Retest — Every Tick (3 Jahre)",
                WorkflowTask.TaskType.RETESTER);
        configureMarket(developmentTick, DEVELOPMENT_FROM, DEVELOPMENT_TO, WorkflowTask.MODE_EVERY_TICK);
        developmentTick.setSourceDatabank(previousPick);
        developmentTick.setTargetDatabank("g12_dev_tick");
        developmentTick.setDeleteFailed(true);
        developmentTick.setFilterConditions(List.of(
                condition(FilterCondition.Metric.LT_NET_PROFIT, FilterCondition.Operator.GREATER_THAN, 0),
                condition(FilterCondition.Metric.LT_TOTAL_TRADES, FilterCondition.Operator.GREATER_EQUAL, 1200),
                condition(FilterCondition.Metric.LT_PROFIT_FACTOR, FilterCondition.Operator.GREATER_EQUAL, 1.25),
                condition(FilterCondition.Metric.LT_MAX_DD_PERCENT, FilterCondition.Operator.LESS_EQUAL, 10)));
        project.addTask(developmentTick);

        WorkflowTask oosTick = new WorkflowTask("14 OOS-Retest — Every Tick (unberührtes Jahr)",
                WorkflowTask.TaskType.RETESTER);
        configureMarket(oosTick, OOS_FROM, FINAL_TO, WorkflowTask.MODE_EVERY_TICK);
        oosTick.setSourceDatabank("g12_dev_tick");
        oosTick.setTargetDatabank("g13_oos_tick");
        oosTick.setDeleteFailed(true);
        oosTick.setFilterConditions(List.of(
                condition(FilterCondition.Metric.LT_NET_PROFIT, FilterCondition.Operator.GREATER_THAN, 0),
                condition(FilterCondition.Metric.LT_TOTAL_TRADES, FilterCondition.Operator.GREATER_EQUAL, 350),
                condition(FilterCondition.Metric.LT_PROFIT_FACTOR, FilterCondition.Operator.GREATER_EQUAL, 1.15),
                condition(FilterCondition.Metric.LT_MAX_DD_PERCENT, FilterCondition.Operator.LESS_EQUAL, 12)));
        project.addTask(oosTick);

        WorkflowTask finalFourYears = new WorkflowTask("15 Finaler Every-Tick-Retest — volle 4 Jahre",
                WorkflowTask.TaskType.RETESTER);
        configureMarket(finalFourYears, DEVELOPMENT_FROM, FINAL_TO, WorkflowTask.MODE_EVERY_TICK);
        finalFourYears.setSourceDatabank("g13_oos_tick");
        finalFourYears.setTargetDatabank("g14_final_4y");
        finalFourYears.setDeleteFailed(true);
        project.addTask(finalFourYears);

        WorkflowTask finalFilter = new WorkflowTask("16 Finale Auswahl — viele Trades, PF und niedriger DD",
                WorkflowTask.TaskType.PRE_FILTER);
        configureMarket(finalFilter, DEVELOPMENT_FROM, FINAL_TO, WorkflowTask.MODE_EVERY_TICK);
        finalFilter.setSourceDatabank("g14_final_4y");
        finalFilter.setTargetDatabank(DatabankManager.FINAL);
        finalFilter.setDeleteFailed(true);
        finalFilter.setFilterConditions(List.of(
                condition(FilterCondition.Metric.LT_NET_PROFIT, FilterCondition.Operator.GREATER_THAN, 0),
                condition(FilterCondition.Metric.LT_TOTAL_TRADES, FilterCondition.Operator.GREATER_EQUAL, 1800),
                condition(FilterCondition.Metric.LT_PROFIT_FACTOR, FilterCondition.Operator.GREATER_EQUAL, 1.25),
                condition(FilterCondition.Metric.LT_MAX_DD_PERCENT, FilterCondition.Operator.LESS_EQUAL, 8),
                condition(FilterCondition.Metric.LT_RECOVERY_FACTOR, FilterCondition.Operator.GREATER_EQUAL, 2)));
        project.addTask(finalFilter);

        return project;
    }

    /**
     * Idempotently upgrades projects created before the score-ranked Top-20
     * gate was added. Existing optimizer results through g11 are preserved;
     * only the obsolete downstream retest outputs are invalidated.
     */
    public static boolean ensureDevelopmentTop20Selection(CustomProject project) {
        if (project == null || !"ToTheMoon_KI_v132".equalsIgnoreCase(project.getExpert())
                || !"AUDCAD".equalsIgnoreCase(project.getSymbol())
                || !"M5".equalsIgnoreCase(project.getPeriod())) {
            return false;
        }

        List<WorkflowTask> tasks = project.getTasks();
        WorkflowTask developmentRetest = tasks.stream()
                .filter(task -> task != null && task.getType() == WorkflowTask.TaskType.RETESTER
                        && "g12_dev_tick".equalsIgnoreCase(task.getTargetDatabank()))
                .findFirst().orElse(null);
        if (developmentRetest == null) return false;

        WorkflowTask selection = tasks.stream()
                .filter(task -> task != null && task.getType() == WorkflowTask.TaskType.DIVERSITY_FILTER
                        && DEVELOPMENT_TOP20_DATABANK.equalsIgnoreCase(task.getTargetDatabank()))
                .findFirst().orElse(null);
        if (selection != null) return false;

        int retestIndex = tasks.indexOf(developmentRetest);
        String source = developmentRetest.getSourceDatabank();
        WorkflowTask finalOptimizer = tasks.subList(0, retestIndex).stream()
                .filter(task -> task != null && task.getType() == WorkflowTask.TaskType.OPTIMIZER)
                .reduce((first, second) -> second).orElse(null);
        selection = createDevelopmentTop20Task(source,
                finalOptimizer != null ? finalOptimizer.getOptimizerParameterSnapshot() : List.of());
        tasks.add(retestIndex, selection);
        developmentRetest.setSourceDatabank(DEVELOPMENT_TOP20_DATABANK);

        renameTaskPrefix(developmentRetest, "12 ", "13 ");
        renameTaskPrefix(findByTarget(tasks, "g13_oos_tick"), "13 ", "14 ");
        renameTaskPrefix(findByTarget(tasks, "g14_final_4y"), "14 ", "15 ");
        renameTaskPrefix(findByTarget(tasks, DatabankManager.FINAL), "15 ", "16 ");

        project.getDatabanks().put(DEVELOPMENT_TOP20_DATABANK, new ArrayList<>());
        for (int i = retestIndex + 1; i < tasks.size(); i++) {
            WorkflowTask downstream = tasks.get(i);
            if (downstream == null) continue;
            if (downstream.getStatus() != WorkflowTask.TaskStatus.DISABLED) {
                downstream.setStatus(WorkflowTask.TaskStatus.PENDING);
            }
            downstream.setLastExecutionLog("");
            clearDatabank(project, downstream.getTargetDatabank());
            removeArchive(project, downstream.getTargetDatabank());
        }
        return true;
    }

    /**
     * Idempotently realigns each staged Optimizer's target list + snapshot to the
     * current {@link #STAGES} definition. Fixes projects whose first stages were
     * wrongly saved with later-stage search spaces (e.g. Adaptive/Escalation on
     * Grid-Fundament).
     *
     * @return {@code true} if any task was rewritten
     */
    public static boolean repairStageOptimizerSearchSpaces(CustomProject project) {
        if (project == null || !"ToTheMoon_KI_v132".equalsIgnoreCase(project.getExpert())
                || !"AUDCAD".equalsIgnoreCase(project.getSymbol())
                || !"M5".equalsIgnoreCase(project.getPeriod())
                || project.getTasks() == null) {
            return false;
        }

        List<EaParameter> fallbackBase = findLargestOptimizerSnapshot(project);
        if (fallbackBase.isEmpty()) {
            fallbackBase = loadProvenPresetFromDisk(project.getExpert());
        }
        if (fallbackBase.isEmpty()) {
            return false;
        }

        boolean changed = false;

        for (WorkflowTask task : project.getTasks()) {
            if (task == null || task.getType() != WorkflowTask.TaskType.OPTIMIZER) {
                continue;
            }
            Stage stage = findStageForTaskName(task.getName());
            if (stage == null) {
                continue;
            }

            List<String> expectedTargets = stage.targetNames();
            Set<String> expected = new LinkedHashSet<>(expectedTargets);
            Set<String> actualTargets = new LinkedHashSet<>();
            for (String name : task.getOptimizerTargetParameters()) {
                if (name != null && !name.isBlank()) {
                    actualTargets.add(name.trim());
                }
            }
            Set<String> actualEnabled = enabledOptimizeNames(task.getOptimizerParameterSnapshot());
            boolean rangesWrong = !stageRangesMatchSnapshot(task.getOptimizerParameterSnapshot(), stage);
            boolean snapshotMissing = task.getOptimizerParameterSnapshot() == null
                    || task.getOptimizerParameterSnapshot().isEmpty();

            if (!snapshotMissing && expected.equals(actualTargets) && expected.equals(actualEnabled) && !rangesWrong) {
                continue;
            }

            List<EaParameter> base = !task.getOptimizerParameterSnapshot().isEmpty()
                    ? task.getOptimizerParameterSnapshot()
                    : fallbackBase;

            task.setOptimizerTargetParameters(expectedTargets);
            task.setOptimizerParameterSnapshot(buildStageSnapshot(base, stage));
            if (task.getStatus() == WorkflowTask.TaskStatus.COMPLETED
                    || task.getStatus() == WorkflowTask.TaskStatus.FAILED
                    || task.getStatus() == WorkflowTask.TaskStatus.RUNNING) {
                task.setStatus(WorkflowTask.TaskStatus.PENDING);
            }
            task.setLastExecutionLog("Search-Space auf Factory-Definition für '"
                    + stage.name + "' korrigiert.");
            clearDatabank(project, task.getTargetDatabank());
            String pickDb = stage.databankPrefix + "_pick";
            clearDatabank(project, pickDb);
            removeArchive(project, task.getTargetDatabank());
            removeArchive(project, pickDb);
            changed = true;
        }
        return changed;
    }

    /**
     * Loads the on-disk proven preset used when creating Guided projects, so empty
     * persisted optimizer snapshots can be rebuilt.
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

    private static List<EaParameter> findLargestOptimizerSnapshot(CustomProject project) {
        List<EaParameter> best = List.of();
        for (WorkflowTask task : project.getTasks()) {
            if (task == null || task.getType() != WorkflowTask.TaskType.OPTIMIZER) {
                continue;
            }
            List<EaParameter> snapshot = task.getOptimizerParameterSnapshot();
            if (snapshot != null && snapshot.size() > best.size()) {
                best = snapshot;
            }
        }
        return best;
    }

    private static Set<String> enabledOptimizeNames(List<EaParameter> snapshot) {
        Set<String> enabled = new LinkedHashSet<>();
        if (snapshot == null) {
            return enabled;
        }
        for (EaParameter parameter : snapshot) {
            if (parameter != null && parameter.isOptimizeEnabled()
                    && parameter.getName() != null && !parameter.getName().isBlank()) {
                enabled.add(parameter.getName().trim());
            }
        }
        return enabled;
    }

    private static boolean stageRangesMatchSnapshot(List<EaParameter> snapshot, Stage stage) {
        if (snapshot == null || snapshot.isEmpty()) {
            return false;
        }
        Map<String, EaParameter> byName = new LinkedHashMap<>();
        for (EaParameter parameter : snapshot) {
            if (parameter == null || parameter.isSectionHeader()
                    || parameter.getName() == null || parameter.getName().isBlank()) {
                continue;
            }
            byName.putIfAbsent(parameter.getName(), parameter);
        }
        for (Range range : stage.ranges) {
            EaParameter parameter = byName.get(range.parameterName);
            if (parameter == null || !parameter.isOptimizeEnabled()) {
                return false;
            }
            if (!safeEq(parameter.getOptimizeStart(), range.start)
                    || !safeEq(parameter.getOptimizeStep(), range.step)
                    || !safeEq(parameter.getOptimizeEnd(), range.end)) {
                return false;
            }
        }
        return true;
    }

    private static boolean safeEq(String a, String b) {
        String left = a == null ? "" : a.trim();
        String right = b == null ? "" : b.trim();
        return left.equals(right);
    }

    private static Stage findStageForTaskName(String taskName) {
        if (taskName == null || taskName.isBlank()) {
            return null;
        }
        String normalized = taskName.trim();
        int sep = normalized.indexOf(" — ");
        if (sep < 0) {
            sep = normalized.indexOf(" - ");
        }
        String stageKey = sep > 0 ? normalized.substring(0, sep).trim() : normalized;
        if (stageKey.toLowerCase(Locale.ROOT).endsWith(" optimizer")) {
            stageKey = stageKey.substring(0, stageKey.length() - " optimizer".length()).trim();
        }
        for (Stage stage : STAGES) {
            if (stageKey.equalsIgnoreCase(stage.name)
                    || stageKey.regionMatches(true, 0, stage.name, 0, stage.name.length())) {
                return stage;
            }
        }
        return null;
    }

    private static WorkflowTask createDevelopmentTop20Task(String sourceDatabank,
                                                            List<EaParameter> comparisonParameters) {
        WorkflowTask task = new WorkflowTask("12 Top-20 nach Score & Diversität",
                WorkflowTask.TaskType.DIVERSITY_FILTER);
        configureMarket(task, DEVELOPMENT_FROM, DEVELOPMENT_TO, WorkflowTask.MODE_OHLC_M1);
        task.setSourceDatabank(sourceDatabank);
        task.setTargetDatabank(DEVELOPMENT_TOP20_DATABANK);
        task.setDiversityRankByScore(true);
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

    private static WorkflowTask findByTarget(List<WorkflowTask> tasks, String target) {
        if (target == null) return null;
        return tasks.stream().filter(task -> task != null
                && target.equalsIgnoreCase(task.getTargetDatabank())).findFirst().orElse(null);
    }

    private static void renameTaskPrefix(WorkflowTask task, String oldPrefix, String newPrefix) {
        if (task != null && task.getName().startsWith(oldPrefix)) {
            task.setName(newPrefix + task.getName().substring(oldPrefix.length()));
        }
    }

    private static void clearDatabank(CustomProject project, String databankName) {
        if (databankName == null) return;
        String storedName = project.getDatabanks().keySet().stream()
                .filter(name -> databankName.equalsIgnoreCase(name)).findFirst().orElse(databankName);
        project.getDatabanks().put(storedName, new ArrayList<>());
    }

    private static void removeArchive(CustomProject project, String databankName) {
        if (databankName == null) return;
        project.getStrategyArchives().entrySet().removeIf(entry -> {
            StrategyBacktestArchive archive = entry.getValue();
            if (archive == null) return true;
            archive.getRunsByTab().keySet().removeIf(
                    tabName -> databankName.equalsIgnoreCase(tabName));
            return archive.getRunsByTab().isEmpty();
        });
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

    private static void configureMarket(WorkflowTask task, String from, String to, int executionMode) {
        task.setStartDate(from);
        task.setEndDate(to);
        task.setRetestSymbol("AUDCAD");
        task.setRetestPeriod("M5");
        task.setExecutionMode(executionMode);
        task.setStatus(WorkflowTask.TaskStatus.PENDING);
        task.setLastExecutionLog("");
    }

    private static List<FilterCondition> developmentFilters() {
        return List.of(
                condition(FilterCondition.Metric.BT_NET_PROFIT, FilterCondition.Operator.GREATER_THAN, 0),
                condition(FilterCondition.Metric.FW_NET_PROFIT, FilterCondition.Operator.GREATER_THAN, 0),
                condition(FilterCondition.Metric.BT_TOTAL_TRADES, FilterCondition.Operator.GREATER_EQUAL, 750),
                condition(FilterCondition.Metric.FW_TOTAL_TRADES, FilterCondition.Operator.GREATER_EQUAL, 300),
                condition(FilterCondition.Metric.BT_PROFIT_FACTOR, FilterCondition.Operator.GREATER_EQUAL, 1.20),
                condition(FilterCondition.Metric.FW_PROFIT_FACTOR, FilterCondition.Operator.GREATER_EQUAL, 1.15),
                condition(FilterCondition.Metric.BT_MAX_DD_PERCENT, FilterCondition.Operator.LESS_EQUAL, 12),
                condition(FilterCondition.Metric.FW_MAX_DD_PERCENT, FilterCondition.Operator.LESS_EQUAL, 12));
    }

    private static List<FilterCondition> strictDevelopmentFilters() {
        List<FilterCondition> filters = new ArrayList<>(developmentFilters());
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
            }
            snapshot.add(copy);
        }
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
        return new Stage(name, databankPrefix, List.of(ranges));
    }

    private static Range range(String parameterName, String start, String step, String end) {
        return new Range(parameterName, start, step, end);
    }

    private record Range(String parameterName, String start, String step, String end) {
    }

    private record Stage(String name, String databankPrefix, List<Range> ranges) {
        private List<String> targetNames() {
            return ranges.stream().map(Range::parameterName).toList();
        }
    }
}
