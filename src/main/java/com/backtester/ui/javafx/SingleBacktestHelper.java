package com.backtester.ui.javafx;

import com.backtester.config.AppConfig;
import com.backtester.config.EaParameter;
import com.backtester.config.EaParameterManager;
import com.backtester.engine.BacktestConfig;
import com.backtester.engine.BacktestRunner;
import com.backtester.engine.WorkflowEngine;
import com.backtester.report.BacktestResult;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
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
        if (combinedPass == null) return;
        Pass pass = combinedPass.getBacktestPass();
        if (pass == null) return;
        runSingleBacktestInMetaTrader(pass, combinedPass.getSymbol(), null, null, parentWindow);
    }

    public static void runSingleBacktestInMetaTrader(CombinedPass combinedPass, String dbName, Window parentWindow) {
        if (combinedPass == null) return;
        Pass pass = combinedPass.getBacktestPass();
        if (pass == null) return;

        WorkflowEngine engine = new WorkflowEngine(AppConfig.getInstance());
        LocalDate fromDate = null;
        LocalDate toDate = null;

        if (dbName != null) {
            String dbLower = dbName.toLowerCase(Locale.ROOT);
            if (dbLower.contains("langzeit") || dbLower.contains("retest") || dbLower.contains("lt")) {
                fromDate = engine.getEffectiveLongtermFromDate();
                toDate = engine.getEffectiveLongtermToDate();
            } else if (dbLower.contains("data1") || dbLower.contains("fw") || dbLower.contains("forward")) {
                fromDate = engine.getForwardDate();
                toDate = engine.getToDate();
            } else if (dbLower.contains("data0") || dbLower.contains("results") || dbLower.contains("is")) {
                fromDate = engine.getFromDate();
                toDate = engine.getForwardMode() > 0 && engine.getForwardDate() != null
                        ? engine.getForwardDate() : engine.getToDate();
            }
        }

        runSingleBacktestInMetaTrader(pass, combinedPass.getSymbol(), fromDate, toDate, parentWindow);
    }

    public static void runSingleBacktestInMetaTrader(Pass pass, String passSymbol, Window parentWindow) {
        runSingleBacktestInMetaTrader(pass, passSymbol, null, null, parentWindow);
    }

    public static void runSingleBacktestInMetaTrader(Pass pass, String passSymbol, LocalDate customFromDate, LocalDate customToDate, Window parentWindow) {
        if (pass == null) return;

        AppConfig config = AppConfig.getInstance();
        WorkflowEngine engine = new WorkflowEngine(config);
        EaParameterManager eaParamManager = new EaParameterManager();

        String expert = engine.getExpert();
        if (expert == null || expert.isBlank()) {
            expert = "ToTheMoon_KI_v132";
        }

        String symbol = passSymbol != null && !passSymbol.isBlank()
                ? passSymbol : engine.getSymbol();
        if (symbol == null || symbol.isBlank()) symbol = "EURUSD";

        String period = engine.getPeriod();
        if (period == null || period.isBlank()) period = "H1";

        List<EaParameter> allParams = eaParamManager.getEffectiveParameters(expert);
        if (allParams == null || allParams.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Konnte Parameter für EA '" + expert + "' nicht laden.", ButtonType.OK);
            if (parentWindow != null) alert.initOwner(parentWindow);
            alert.showAndWait();
            return;
        }

        Map<String, String> passVals = pass.getParameterValues();
        if (passVals != null) {
            for (EaParameter param : allParams) {
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
            eaParamManager.writeSetFile(destFile, allParams, expert);
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
        btConfig.setModel(engine.getTickModel());

        LocalDate fromDate = customFromDate != null ? customFromDate : engine.getEffectiveLongtermFromDate();
        LocalDate toDate = customToDate != null ? customToDate : engine.getEffectiveLongtermToDate();
        if (fromDate != null) btConfig.setFromDate(fromDate);
        if (toDate != null) btConfig.setToDate(toDate);

        btConfig.setDeposit(engine.getDeposit());
        btConfig.setCurrency(engine.getCurrency());
        btConfig.setLeverage(engine.getLeverage());

        // CRITICAL REQUIREMENT: Terminal stays open after backtest finishes
        btConfig.setShutdownTerminal(false);

        String messageText = String.format(
                "Einzel-Backtest für Pass #%d (%s bis %s) gestartet.\n\nMetaTrader wird geöffnet und bleibt nach dem Test geöffnet.",
                pass.getPassNumber(),
                fromDate != null ? fromDate.toString() : "Anfang",
                toDate != null ? toDate.toString() : "Heute"
        );

        Alert infoAlert = new Alert(Alert.AlertType.INFORMATION, messageText, ButtonType.OK);
        infoAlert.setTitle("Einzel-Backtest gestartet");
        infoAlert.setHeaderText("MetaTrader Einzel-Backtest (Terminal bleibt offen)");
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
}
