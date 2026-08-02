package com.backtester.ui.javafx;

import com.backtester.config.AppConfig;
import com.backtester.config.EaParameter;
import com.backtester.config.EaParameterManager;
import com.backtester.engine.BacktestConfig;
import com.backtester.engine.BacktestRunner;
import com.backtester.report.BacktestResult;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import com.backtester.workflow.CustomProject;
import com.backtester.workflow.WorkflowTask;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Helper to launch a single verification backtest in MetaTrader for a selected strategy pass
 * with the terminal remaining open afterwards for inspection.
 */
public class SingleBacktestHelper {
    private static final Logger log = LoggerFactory.getLogger(SingleBacktestHelper.class);

    public static void runSingleBacktestInMetaTrader(CombinedPass combinedPass, Window parentWindow) {
        runSingleBacktestInMetaTrader(combinedPass, null, (CustomProject) null, parentWindow);
    }

    public static void runSingleBacktestInMetaTrader(CombinedPass combinedPass, String dbName, Window parentWindow) {
        runSingleBacktestInMetaTrader(combinedPass, dbName, (CustomProject) null, parentWindow);
    }

    public static void runSingleBacktestInMetaTrader(CombinedPass combinedPass, String dbName, CustomProject project, Window parentWindow) {
        if (combinedPass == null) return;
        Pass pass = combinedPass.getBacktestPass();
        if (pass == null) return;

        String passSymbol = combinedPass.getSymbol();
        runSingleBacktestInMetaTrader(pass, passSymbol, dbName, project, parentWindow);
    }

    public static void runSingleBacktestInMetaTrader(Pass pass, String passSymbol, Window parentWindow) {
        runSingleBacktestInMetaTrader(pass, passSymbol, (LocalDate) null, (LocalDate) null, parentWindow);
    }

    public static void runSingleBacktestInMetaTrader(Pass pass, String passSymbol, LocalDate customFromDate, LocalDate customToDate, Window parentWindow) {
        runSingleBacktestInMetaTrader(pass, null, passSymbol, null, customFromDate, customToDate, parentWindow);
    }

    public static void runSingleBacktestInMetaTrader(Pass pass, String passSymbol, String dbName, CustomProject project, Window parentWindow) {
        String expert = project != null && project.getExpert() != null && !project.getExpert().isBlank()
                ? project.getExpert() : AppConfig.getInstance().get("app.expert", "ToTheMoon_KI_v132");

        String symbol = null;
        String period = null;

        // 1. Base symbol/period on Project (most relevant global context)
        if (project != null && project.getSymbol() != null && !project.getSymbol().isBlank()) {
            symbol = project.getSymbol();
        }
        if (project != null && project.getPeriod() != null && !project.getPeriod().isBlank()) {
            period = project.getPeriod();
        }

        // 2. Fallback to passSymbol if we still don't have one
        if (symbol == null && passSymbol != null && !passSymbol.isBlank()) {
            symbol = passSymbol;
        }

        LocalDate fromDate = null;
        LocalDate toDate = null;

        if (project != null && project.getTasks() != null) {
            // 3. Override with Optimizer Task defaults
            WorkflowTask optTask = findOptimizerTask(project);
            if (optTask != null) {
                if (optTask.getRetestSymbol() != null && !optTask.getRetestSymbol().isBlank()) {
                    symbol = optTask.getRetestSymbol();
                }
                if (optTask.getRetestPeriod() != null && !optTask.getRetestPeriod().isBlank()) {
                    period = optTask.getRetestPeriod();
                }
            }

            // 4. Override with Target Task defaults
            WorkflowTask targetTask = findTaskForDatabank(project, dbName);
            if (targetTask != null) {
                if (targetTask.getRetestSymbol() != null && !targetTask.getRetestSymbol().isBlank()) {
                    symbol = targetTask.getRetestSymbol();
                }
                if (targetTask.getRetestPeriod() != null && !targetTask.getRetestPeriod().isBlank()) {
                    period = targetTask.getRetestPeriod();
                }
                fromDate = parseDateOrNull(targetTask.getStartDate());
                toDate = parseDateOrNull(targetTask.getEndDate());

                if (dbName != null && dbName.toLowerCase(Locale.ROOT).contains("data0") && targetTask.getOptimizerForwardDate() != null) {
                    LocalDate fwdDate = parseDateOrNull(targetTask.getOptimizerForwardDate());
                    if (fwdDate != null) {
                        toDate = fwdDate;
                    }
                } else if (dbName != null && (dbName.toLowerCase(Locale.ROOT).contains("data1") || dbName.toLowerCase(Locale.ROOT).contains("fw"))) {
                    LocalDate fwdDate = parseDateOrNull(targetTask.getOptimizerForwardDate());
                    if (fwdDate != null) {
                        fromDate = fwdDate;
                    }
                }
            }
        }

        // 5. Ultimate fallback to AppConfig
        if (symbol == null || symbol.isBlank()) {
            symbol = AppConfig.getInstance().get("app.symbol", "EURUSD");
        }
        if (period == null || period.isBlank()) {
            period = AppConfig.getInstance().get("app.period", "H1");
        }

        if (fromDate == null) fromDate = LocalDate.now().minusYears(2);
        if (toDate == null) toDate = LocalDate.now();

        runSingleBacktestInMetaTrader(pass, expert, symbol, period, fromDate, toDate, parentWindow);
    }

    public static void runSingleBacktestInMetaTrader(Pass pass, String customExpert, String passSymbol, String customPeriod,
                                                     LocalDate customFromDate, LocalDate customToDate, Window parentWindow) {
        if (pass == null) return;

        AppConfig config = AppConfig.getInstance();
        EaParameterManager eaParamManager = new EaParameterManager();

        String expert = customExpert != null && !customExpert.isBlank()
                ? customExpert : config.get("app.expert", "ToTheMoon_KI_v132");

        String symbol = passSymbol != null && !passSymbol.isBlank()
                ? passSymbol : config.get("app.symbol", "EURUSD");

        String period = customPeriod != null && !customPeriod.isBlank()
                ? customPeriod : config.get("app.period", "H1");

        List<EaParameter> baseParams = eaParamManager.getEffectiveParameters(expert);
        if (baseParams == null || baseParams.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Konnte Parameter für EA '" + expert + "' nicht laden.", ButtonType.OK);
            if (parentWindow != null) alert.initOwner(parentWindow);
            alert.showAndWait();
            return;
        }

        Map<String, String> passVals = pass.getParameterValues();
        if (passVals != null) {
            for (EaParameter param : baseParams) {
                if (passVals.containsKey(param.getName())) {
                    param.setValue(passVals.get(param.getName()));
                }
                param.setOptimizeEnabled(false);
            }
        }

        String eaName = EaParameterManager.extractEaBaseName(expert);
        String presetFileName = "Backtester_" + eaName + "_Verify_Pass" + pass.getPassNumber() + ".set";
        Path presetsDir = config.getTesterProfilesDir(expert);
        try {
            Files.createDirectories(presetsDir);
            Path destFile = presetsDir.resolve(presetFileName);
            eaParamManager.writeSetFile(destFile, baseParams, expert);
        } catch (IOException ex) {
            log.error("Failed to write preset file for single backtest", ex);
            Alert alert = new Alert(Alert.AlertType.ERROR, "Fehler beim Erstellen der Preset-Datei: " + ex.getMessage(), ButtonType.OK);
            if (parentWindow != null) alert.initOwner(parentWindow);
            alert.showAndWait();
            return;
        }

        BacktestConfig btConfig = new BacktestConfig();
        btConfig.setExpert(expert);
        btConfig.setExpertParameters(presetFileName);
        btConfig.setSymbol(symbol);
        btConfig.setPeriod(period);
        btConfig.setModel(0); // Every Tick / OHLC M1

        LocalDate fromDate = customFromDate != null ? customFromDate : LocalDate.now().minusYears(2);
        LocalDate toDate = customToDate != null ? customToDate : LocalDate.now();
        btConfig.setFromDate(fromDate);
        btConfig.setToDate(toDate);

        btConfig.setDeposit(10000);
        btConfig.setCurrency("USD");
        btConfig.setLeverage("1:100");

        // CRITICAL REQUIREMENT: Terminal stays open after backtest finishes
        btConfig.setShutdownTerminal(false);

        String messageText = String.format(
                "Einzel-Backtest für Pass #%d gestartet.\n\nEA: %s | Symbol: %s | Period: %s\nZeitraum: %s bis %s\n\nMetaTrader wird geöffnet und bleibt nach dem Test geöffnet.",
                pass.getPassNumber(),
                expert,
                symbol,
                period,
                fromDate.toString(),
                toDate.toString()
        );

        Alert infoAlert = new Alert(Alert.AlertType.INFORMATION, messageText, ButtonType.OK);
        infoAlert.setTitle("Einzel-Backtest gestartet");
        infoAlert.setHeaderText("MetaTrader Einzel-Backtest (" + symbol + " " + period + ")");
        if (parentWindow != null) infoAlert.initOwner(parentWindow);
        infoAlert.show();

        BacktestRunner runner = new BacktestRunner();
        runner.setLogCallback(msg -> log.info("[Single-BT] {}", msg));

        Task<BacktestResult> task = new Task<>() {
            @Override
            protected BacktestResult call() throws Exception {
                return runner.runBacktest(btConfig);
            }
        };

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private static WorkflowTask findOptimizerTask(CustomProject project) {
        if (project == null || project.getTasks() == null) return null;
        for (WorkflowTask t : project.getTasks()) {
            if (t.getType() == WorkflowTask.TaskType.OPTIMIZER) return t;
        }
        return null;
    }

    private static WorkflowTask findTaskForDatabank(CustomProject project, String dbName) {
        if (project == null || project.getTasks() == null || dbName == null) return null;
        String cleanDbName = dbName.replaceAll("\\s*\\(\\d+\\)$", "").trim();

        for (WorkflowTask t : project.getTasks()) {
            if (cleanDbName.equalsIgnoreCase(t.getTargetDatabank())) {
                return t;
            }
        }
        for (WorkflowTask t : project.getTasks()) {
            if (cleanDbName.equalsIgnoreCase(t.getSourceDatabank())) {
                return t;
            }
        }
        String dbLower = cleanDbName.toLowerCase(Locale.ROOT);
        for (WorkflowTask t : project.getTasks()) {
            if (dbLower.contains("data0") && t.getType() == WorkflowTask.TaskType.OPTIMIZER) return t;
            if (dbLower.contains("data1") && t.getType() == WorkflowTask.TaskType.PRE_FILTER) return t;
            if ((dbLower.contains("langzeit") || dbLower.contains("retest")) && t.getType() == WorkflowTask.TaskType.RETESTER) return t;
        }
        return project.getTasks().isEmpty() ? null : project.getTasks().get(0);
    }

    private static LocalDate parseDateOrNull(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
