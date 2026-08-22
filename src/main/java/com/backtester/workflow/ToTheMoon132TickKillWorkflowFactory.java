package com.backtester.workflow;

import com.backtester.config.EaParameter;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * ToTheMoon132 search that spends money only after cheap kills.
 *
 * <p>OHLC overstates grid fills. Searching four years on every tick is too slow,
 * and ranking on one year of ticks overfits that year. This project keeps the
 * proven 11-stage M1-OHLC search, then kills survivors on short every-tick
 * windows before anyone pays for three-year, OOS, or four-year ticks.
 *
 * <ol>
 *   <li>M1-OHLC search on 3 years development, forward split, staged filters</li>
   *   <li>g01: IS/OOS both green, ~100 diverse OHLC survivors, 1J every-tick gate (1:1),
 *       then B1–B10; k12 re-clusters later survivors and keeps those ids</li>
 *   <li>3-month every-tick smoke: blow-up kill only, not a rank</li>
 *   <li>1-year every-tick kill: pass/fail on the last development year</li>
 *   <li>3-year every-tick development retest</li>
 *   <li>1-year OOS every-tick — the only selection gate</li>
 *   <li>4-year every-tick report, no gate</li>
 *   <li>4-year real ticks report, no gate (1–few finalists)</li>
 * </ol>
 */
public final class ToTheMoon132TickKillWorkflowFactory {

    public static final String DEVELOPMENT_FROM = ToTheMoon132GuidedWorkflowFactory.DEVELOPMENT_FROM;
    public static final String DEVELOPMENT_TO = ToTheMoon132GuidedWorkflowFactory.DEVELOPMENT_TO;
    public static final String OOS_FROM = ToTheMoon132GuidedWorkflowFactory.OOS_FROM;
    public static final String FINAL_TO = ToTheMoon132GuidedWorkflowFactory.FINAL_TO;
    public static final String SMOKE_FROM = LocalDate.parse(DEVELOPMENT_TO).minusMonths(3).toString();
    public static final String KILL_1Y_FROM = LocalDate.parse(DEVELOPMENT_TO).minusYears(1).toString();
    public static final String TOP20_DATABANK = "k12_dev_top20";
    public static final String SMOKE_DATABANK = "k13_smoke_tick";
    public static final String KILL_1Y_DATABANK = "k14_kill_1y";
    public static final String DEV_TICK_DATABANK = "k15_dev_tick";
    public static final String OOS_DATABANK = "k16_oos_tick";
    public static final String FOUR_YEAR_DATABANK = "k17_final_4y";
    public static final String REAL_TICKS_DATABANK = "k18_real_4y";
    private static final int TOP20_MIN_DIFFERENT_PARAMS = 2;

    private ToTheMoon132TickKillWorkflowFactory() {
    }

    public static CustomProject create(String projectName,
                                       List<EaParameter> provenPreset,
                                       Path optimizerReportsRoot) {
        return create(projectName, "GBPJPY", "M5", provenPreset, optimizerReportsRoot);
    }

    public static CustomProject create(String projectName,
                                       String symbol,
                                       String period,
                                       List<EaParameter> provenPreset,
                                       Path optimizerReportsRoot) {
        String sym = symbol != null && !symbol.isBlank() ? symbol.trim() : "GBPJPY";
        String per = period != null && !period.isBlank() ? period.trim() : "M5";

        CustomProject project = new CustomProject(projectName, "ToTheMoon_KI_v132", sym, per);
        project.setSaveDatabanksPersistently(true);

        String previousPick = ToTheMoon132GuidedWorkflowFactory.appendSearchChain(
                project, provenPreset, optimizerReportsRoot, true);

        project.addTask(createTop20Task(
                previousPick, ToTheMoon132GuidedWorkflowFactory.formDistanceSnapshot(project), sym, per));

        project.addTask(createSmokeKillTask(sym, per));
        project.addTask(createOneYearKillTask(sym, per));
        project.addTask(createDevelopmentTickTask(sym, per));
        project.addTask(createOosSelectionTask(sym, per));
        project.addTask(createFourYearReportTask(sym, per));
        project.addTask(createRealTicksReportTask(sym, per));
        project.addTask(createPublicationTask(sym, per));
        return project;
    }

    private static WorkflowTask createTop20Task(String sourceDatabank,
                                                List<EaParameter> comparisonParameters,
                                                String symbol,
                                                String period) {
        WorkflowTask task = new WorkflowTask("12 Re-Diversität der Überlebenden (B-Cluster)",
                WorkflowTask.TaskType.DIVERSITY_FILTER);
        configureMarket(task, symbol, period, DEVELOPMENT_FROM, DEVELOPMENT_TO, WorkflowTask.MODE_OHLC_M1);
        task.setSourceDatabank(sourceDatabank);
        task.setTargetDatabank(TOP20_DATABANK);
        task.setDiversityRankByScore(true);
        task.setDiversityDeduplicateEffectiveV132(true);
        task.setDiversityParamDiffPct(WorkflowTask.DEFAULT_DIVERSITY_PARAM_DIFF_PCT);
        task.setDiversityTradeDiffPct(WorkflowTask.DEFAULT_DIVERSITY_TRADE_DIFF_PCT);
        task.setDiversityMinDifferentParams(TOP20_MIN_DIFFERENT_PARAMS);
        task.setDiversityMaxStrategies(ClusterIdentity.MAX_CLUSTERS);
        task.setDiversityParameterSnapshot(comparisonParameters);
        task.setDeleteFailed(true);
        return task;
    }

    private static WorkflowTask createSmokeKillTask(String symbol, String period) {
        WorkflowTask task = new WorkflowTask(
                "13 Smoke-Kill — Every Tick (3 Monate, nur Blow-up)",
                WorkflowTask.TaskType.RETESTER);
        configureMarket(task, symbol, period, SMOKE_FROM, DEVELOPMENT_TO, WorkflowTask.MODE_EVERY_TICK);
        task.setSourceDatabank(TOP20_DATABANK);
        task.setTargetDatabank(SMOKE_DATABANK);
        task.setDeleteFailed(true);
        // Short window is noisy. Kill account explosions, do not require a green quarter.
        task.setFilterConditions(List.of(
                condition(FilterCondition.Metric.LT_TOTAL_TRADES, FilterCondition.Operator.GREATER_EQUAL, 60),
                condition(FilterCondition.Metric.LT_MAX_DD_PERCENT, FilterCondition.Operator.LESS_EQUAL, 18)));
        return task;
    }

    private static WorkflowTask createOneYearKillTask(String symbol, String period) {
        WorkflowTask task = new WorkflowTask(
                "14 Kill — Every Tick (1 Jahr Dev, Pass/Fail, kein Ranking)",
                WorkflowTask.TaskType.RETESTER);
        configureMarket(task, symbol, period, KILL_1Y_FROM, DEVELOPMENT_TO, WorkflowTask.MODE_EVERY_TICK);
        task.setSourceDatabank(SMOKE_DATABANK);
        task.setTargetDatabank(KILL_1Y_DATABANK);
        task.setDeleteFailed(true);
        task.setFilterConditions(List.of(
                condition(FilterCondition.Metric.LT_NET_PROFIT, FilterCondition.Operator.GREATER_THAN, 0),
                condition(FilterCondition.Metric.LT_TOTAL_TRADES, FilterCondition.Operator.GREATER_EQUAL, 300),
                condition(FilterCondition.Metric.LT_PROFIT_FACTOR, FilterCondition.Operator.GREATER_EQUAL, 1.10),
                condition(FilterCondition.Metric.LT_MAX_DD_PERCENT, FilterCondition.Operator.LESS_EQUAL, 14)));
        return task;
    }

    private static WorkflowTask createDevelopmentTickTask(String symbol, String period) {
        WorkflowTask task = new WorkflowTask(
                "15 Development-Retest — Every Tick (3 Jahre)",
                WorkflowTask.TaskType.RETESTER);
        configureMarket(task, symbol, period, DEVELOPMENT_FROM, DEVELOPMENT_TO, WorkflowTask.MODE_EVERY_TICK);
        task.setSourceDatabank(KILL_1Y_DATABANK);
        task.setTargetDatabank(DEV_TICK_DATABANK);
        task.setDeleteFailed(true);
        task.setFilterConditions(List.of(
                condition(FilterCondition.Metric.LT_NET_PROFIT, FilterCondition.Operator.GREATER_THAN, 0),
                condition(FilterCondition.Metric.LT_TOTAL_TRADES, FilterCondition.Operator.GREATER_EQUAL, 1200),
                condition(FilterCondition.Metric.LT_PROFIT_FACTOR, FilterCondition.Operator.GREATER_EQUAL, 1.25),
                condition(FilterCondition.Metric.LT_MAX_DD_PERCENT, FilterCondition.Operator.LESS_EQUAL, 10)));
        return task;
    }

    private static WorkflowTask createOosSelectionTask(String symbol, String period) {
        WorkflowTask task = new WorkflowTask(
                "16 OOS-Retest — Every Tick (einziges finales Selektionsgate)",
                WorkflowTask.TaskType.RETESTER);
        configureMarket(task, symbol, period, OOS_FROM, FINAL_TO, WorkflowTask.MODE_EVERY_TICK);
        task.setSourceDatabank(DEV_TICK_DATABANK);
        task.setTargetDatabank(OOS_DATABANK);
        task.setDeleteFailed(true);
        task.setFilterConditions(List.of(
                condition(FilterCondition.Metric.LT_NET_PROFIT, FilterCondition.Operator.GREATER_THAN, 0),
                condition(FilterCondition.Metric.LT_TOTAL_TRADES, FilterCondition.Operator.GREATER_EQUAL, 350),
                condition(FilterCondition.Metric.LT_PROFIT_FACTOR, FilterCondition.Operator.GREATER_EQUAL, 1.15),
                condition(FilterCondition.Metric.LT_MAX_DD_PERCENT, FilterCondition.Operator.LESS_EQUAL, 12)));
        return task;
    }

    private static WorkflowTask createFourYearReportTask(String symbol, String period) {
        WorkflowTask task = new WorkflowTask(
                "17 4Y-Retest — Every Tick (Report, kein Gate)",
                WorkflowTask.TaskType.RETESTER);
        configureMarket(task, symbol, period, DEVELOPMENT_FROM, FINAL_TO, WorkflowTask.MODE_EVERY_TICK);
        task.setSourceDatabank(OOS_DATABANK);
        task.setTargetDatabank(FOUR_YEAR_DATABANK);
        task.setDeleteFailed(true);
        task.setFilterConditions(List.of());
        return task;
    }

    private static WorkflowTask createRealTicksReportTask(String symbol, String period) {
        WorkflowTask task = new WorkflowTask(
                "18 4Y-Retest — Real Ticks (Report, kein Gate)",
                WorkflowTask.TaskType.RETESTER);
        configureMarket(task, symbol, period, DEVELOPMENT_FROM, FINAL_TO, WorkflowTask.MODE_REAL_TICKS);
        task.setSourceDatabank(FOUR_YEAR_DATABANK);
        task.setTargetDatabank(REAL_TICKS_DATABANK);
        task.setDeleteFailed(true);
        task.setFilterConditions(List.of());
        return task;
    }

    private static WorkflowTask createPublicationTask(String symbol, String period) {
        WorkflowTask task = new WorkflowTask(
                "19 Veröffentlichung — alle erfolgreichen Real-Tick-Runs (keine Zusatzfilter)",
                WorkflowTask.TaskType.PRE_FILTER);
        configureMarket(task, symbol, period, DEVELOPMENT_FROM, FINAL_TO, WorkflowTask.MODE_REAL_TICKS);
        task.setSourceDatabank(REAL_TICKS_DATABANK);
        task.setTargetDatabank(DatabankManager.FINAL);
        task.setDeleteFailed(true);
        task.setFilterConditions(List.of());
        return task;
    }

    private static void configureMarket(WorkflowTask task, String symbol, String period,
                                        String from, String to, int executionMode) {
        task.setStartDate(from);
        task.setEndDate(to);
        task.setRetestSymbol(symbol != null && !symbol.isBlank() ? symbol.trim() : "GBPJPY");
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
}
