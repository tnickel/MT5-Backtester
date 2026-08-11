package com.backtester.ui.javafx;

import com.backtester.config.AppConfig;
import com.backtester.config.EaParameter;
import com.backtester.config.EaParameterManager;
import com.backtester.engine.BacktestConfig;
import com.backtester.engine.BacktestRunner;
import com.backtester.report.BacktestResult;
import com.backtester.report.BacktestArtifactReplayResolver;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import com.backtester.report.PassPresetResolver;
import com.backtester.workflow.CustomProject;
import com.backtester.workflow.DatabankArtifactContextResolver;
import com.backtester.workflow.WorkflowTask;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;
import javafx.scene.control.TextArea;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import com.backtester.engine.WorkflowEngine;
import java.util.Collection;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Helper to launch a single verification backtest in MetaTrader for a selected strategy pass
 * with the terminal remaining open afterwards for inspection.
 */
public class SingleBacktestHelper {
    private static final Logger log = LoggerFactory.getLogger(SingleBacktestHelper.class);

    public static void runSingleBacktestInMetaTrader(CombinedPass combinedPass, Window parentWindow) {
        runSingleBacktestInMetaTrader(combinedPass, null, (CustomProject) null, parentWindow);
    }

    /**
     * Replays the exact preset and tester configuration which produced the MT5
     * artifact displayed in the HTML gallery.
     */
    public static void runArtifactBacktestInMetaTrader(
            CombinedPass combinedPass, BacktestArtifactReplayResolver.Replay replay,
            Window parentWindow) {
        if (combinedPass == null || replay == null || replay.config() == null) return;

        BacktestConfig btConfig = replay.config();
        AppConfig appConfig = AppConfig.getInstance();
        Path profilesDir = appConfig.getTesterProfilesDir(btConfig.getExpert())
                .toAbsolutePath().normalize();
        String replayPresetName = "GalleryReplay_Pass" + combinedPass.getPassNumber()
                + "_" + Integer.toUnsignedString(replay.artifactDirectory().getFileName()
                .toString().hashCode(), 36) + ".set";
        try {
            Files.createDirectories(profilesDir);
            Files.copy(replay.presetSource(), profilesDir.resolve(replayPresetName),
                    StandardCopyOption.REPLACE_EXISTING);
            btConfig.setExpertParameters(replayPresetName);
        } catch (IOException ex) {
            log.error("Failed to stage exact gallery preset", ex);
            showError(parentWindow, "Das Preset der angezeigten Kurve konnte nicht für MT5 bereitgestellt werden: "
                    + ex.getMessage());
            return;
        }

        String messageText = String.format(
                "MT5-Replay für Pass #%d gestartet.\n\nEA: %s | Symbol: %s | Period: %s\n"
                        + "Zeitraum: %s bis %s\nPreset der angezeigten Kurve: %s",
                combinedPass.getPassNumber(), btConfig.getExpert(), btConfig.getSymbol(),
                btConfig.getPeriod(), btConfig.getFromDate(), btConfig.getToDate(),
                replay.originalPresetName());
        launchBacktest(btConfig, messageText, parentWindow);
    }

    public static void runSingleBacktestInMetaTrader(CombinedPass combinedPass, String dbName, Window parentWindow) {
        runSingleBacktestInMetaTrader(combinedPass, dbName, (CustomProject) null, parentWindow);
    }

    public static void runSingleBacktestInMetaTrader(CombinedPass combinedPass, String dbName, CustomProject project, Window parentWindow) {
        if (combinedPass == null) return;
        Pass pass = combinedPass.getBacktestPass();
        if (pass == null) return;

        AppConfig config = AppConfig.getInstance();
        String expert = project != null && project.getExpert() != null && !project.getExpert().isBlank()
                ? project.getExpert() : config.get("app.expert", "ToTheMoon_KI_v132");

        // 1) Per-strategy market context stamped when the optimizer/retester wrote the pass
        String symbol = firstNonBlank(combinedPass.getSymbol());
        String period = firstNonBlank(combinedPass.getPeriod());
        String tickModel = firstNonBlank(combinedPass.getTickModel(), pass.getTickModel());

        // 2) Walk filter lineage (data1 → data0 → OPTIMIZER). Never trust PRE_FILTER UI leftovers.
        DatabankArtifactContextResolver.Context lineage = null;
        if (project != null && dbName != null && !dbName.isBlank()) {
            lineage = DatabankArtifactContextResolver.resolve(
                    project, dbName, List.of(combinedPass), expert,
                    config.get("app.symbol", "EURUSD"),
                    config.get("app.period", "H1"));
            if (symbol == null) symbol = firstNonBlank(lineage.symbol());
            if (period == null) period = firstNonBlank(lineage.period());
            if (expert == null || expert.isBlank()) expert = firstNonBlank(lineage.expert());
        }

        // 3) Project / AppConfig last resort only
        if (symbol == null) {
            symbol = firstNonBlank(
                    project != null ? project.getSymbol() : null,
                    config.get("app.symbol", "EURUSD"));
        }
        if (period == null) {
            period = firstNonBlank(
                    project != null ? project.getPeriod() : null,
                    config.get("app.period", "H1"));
        }
        if (symbol == null) symbol = "EURUSD";
        if (period == null) period = "H1";

        // Dates: longterm pass → pass dates → lineage execution task → defaults
        LocalDate fromDate = null;
        LocalDate toDate = null;
        Pass ltPass = combinedPass.getLongtermPass();
        if (ltPass != null) {
            fromDate = parseDateOrNull(ltPass.getFromDate());
            toDate = parseDateOrNull(ltPass.getToDate());
        }
        if (fromDate == null) fromDate = parseDateOrNull(pass.getFromDate());
        if (toDate == null) toDate = parseDateOrNull(pass.getToDate());
        if (lineage != null) {
            if (fromDate == null) fromDate = lineage.from();
            if (toDate == null) toDate = lineage.to();
        }
        if (fromDate == null) fromDate = LocalDate.now().minusYears(5);
        if (toDate == null) toDate = LocalDate.now();

        int modelId;
        if (tickModel != null) {
            modelId = parseModelToId(tickModel);
        } else {
            WorkflowTask origin = project != null ? project.findOriginTaskForDatabank(dbName) : null;
            modelId = origin != null ? origin.getMt5Model() : 0;
        }

        // Persist missing period onto the in-memory strategy so subsequent saves keep it
        if ((combinedPass.getPeriod() == null || combinedPass.getPeriod().isBlank()) && period != null) {
            combinedPass.setPeriod(period);
        }
        if ((combinedPass.getSymbol() == null || combinedPass.getSymbol().isBlank()) && symbol != null) {
            combinedPass.setSymbol(symbol);
        }
        if ((pass.getTickModel() == null || pass.getTickModel().isBlank()) && tickModel != null) {
            pass.setTickModel(tickModel);
        } else if ((pass.getTickModel() == null || pass.getTickModel().isBlank()) && modelId >= 0
                && modelId < BacktestConfig.MODEL_NAMES.length) {
            pass.setTickModel(BacktestConfig.MODEL_NAMES[modelId]);
        }

        log.info("Einzel-Backtest context: pass=#{} symbol={} period={} model={} db={}",
                pass.getPassNumber(), symbol, period, modelId, dbName);

        runSingleBacktestInMetaTrader(pass, expert, symbol, period, fromDate, toDate, parentWindow, modelId);
    }

    public static void runSingleBacktestInMetaTrader(Pass pass, String passSymbol, Window parentWindow) {
        runSingleBacktestInMetaTrader(pass, passSymbol, (LocalDate) null, (LocalDate) null, parentWindow);
    }

    public static void runSingleBacktestInMetaTrader(Pass pass, String passSymbol, LocalDate customFromDate, LocalDate customToDate, Window parentWindow) {
        runSingleBacktestInMetaTrader(pass, null, passSymbol, null, customFromDate, customToDate, parentWindow);
    }

    public static void runSingleBacktestInMetaTrader(Pass pass, String passSymbol, String dbName, CustomProject project, Window parentWindow) {
        CombinedPass wrapper = new CombinedPass(pass, null, 0.0, 0.0, "");
        if (passSymbol != null && !passSymbol.isBlank()) {
            wrapper.setSymbol(passSymbol);
        }
        runSingleBacktestInMetaTrader(wrapper, dbName, project, parentWindow);
    }

    public static void runSingleBacktestInMetaTrader(Pass pass, String customExpert, String passSymbol, String customPeriod,
                                                     LocalDate customFromDate, LocalDate customToDate, Window parentWindow) {
        runSingleBacktestInMetaTrader(pass, customExpert, passSymbol, customPeriod, customFromDate, customToDate, parentWindow, 0);
    }

    public static void runSingleBacktestInMetaTrader(Pass pass, String customExpert, String passSymbol, String customPeriod,
                                                     LocalDate customFromDate, LocalDate customToDate, Window parentWindow, int modelId) {
        if (pass == null) return;

        AppConfig config = AppConfig.getInstance();
        EaParameterManager eaParamManager = new EaParameterManager();

        String expert = customExpert != null && !customExpert.isBlank()
                ? customExpert : config.get("app.expert", "ToTheMoon_KI_v132");

        String symbol = passSymbol != null && !passSymbol.isBlank()
                ? passSymbol : config.get("app.symbol", "EURUSD");

        String period = customPeriod != null && !customPeriod.isBlank()
                ? customPeriod : config.get("app.period", "H1");

        // Never rebuild from the current EA config: it lacks the parameters MT5
        // omitted from the optimization report and is rewritten by MT5 itself.
        PassPresetResolver.Resolution resolution = PassPresetResolver.resolve(pass, expert);
        List<EaParameter> baseParams = resolution.parameters();
        if (baseParams == null || baseParams.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Konnte Parameter für EA '" + expert + "' nicht laden.", ButtonType.OK);
            if (parentWindow != null) alert.initOwner(parentWindow);
            alert.showAndWait();
            return;
        }

        int passNum = pass.getPassNumber();
        String eaName = EaParameterManager.extractEaBaseName(expert);
        String presetFileName = "Backtester_" + eaName + "_Verify_Pass" + pass.getPassNumber() + ".set";
        Path presetsDir = config.getTesterProfilesDir(expert);
        try {
            Files.createDirectories(presetsDir);
            Path destFile = presetsDir.resolve(presetFileName);
            eaParamManager.writeSetFile(destFile, baseParams, expert);
            log.info("[SETFILE-LOG] Single Backtest Pass #{}: wrote {} parameters to {}", passNum, baseParams.size(), destFile);
            for (EaParameter p : baseParams) {
                log.debug("[SETFILE-PARAM] SingleBacktest Pass #{} | {} = {}", passNum, p.getName(), p.getValue());
            }
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
        btConfig.setModel(modelId);

        LocalDate fromDate = customFromDate != null ? customFromDate : LocalDate.now().minusYears(5);
        LocalDate toDate = customToDate != null ? customToDate : LocalDate.now();
        btConfig.setFromDate(fromDate);
        btConfig.setToDate(toDate);

        btConfig.setDeposit(10000);
        btConfig.setCurrency("USD");
        btConfig.setLeverage("1:100");

        // CRITICAL REQUIREMENT: Terminal stays open after backtest finishes
        btConfig.setShutdownTerminal(false);

        StringBuilder messageText = new StringBuilder(String.format(
                "Einzel-Backtest für Pass #%d gestartet.\n\nEA: %s | Symbol: %s | Period: %s\nZeitraum: %s bis %s\nModell: %s\nParameter-Quelle: %s",
                pass.getPassNumber(),
                expert,
                symbol,
                period,
                fromDate.toString(),
                toDate.toString(),
                BacktestConfig.MODEL_NAMES[Math.max(0, Math.min(modelId, BacktestConfig.MODEL_NAMES.length - 1))],
                describeParameterSource(resolution)));

        // The original run's model is the only fair comparison baseline; a different
        // model alone can change the trade count by a large factor.
        int originalModel = PassPresetResolver.readTesterModel(pass.getReportDirectory());
        if (originalModel >= 0 && originalModel != modelId
                && originalModel < BacktestConfig.MODEL_NAMES.length) {
            messageText.append("\n\n⚠ Der Original-Lauf verwendete das Modell '")
                    .append(BacktestConfig.MODEL_NAMES[originalModel])
                    .append("'. Abweichende Modelle liefern abweichende Trade-Zahlen.");
        }
        if (resolution.warning() != null) {
            messageText.append("\n\n⚠ ").append(resolution.warning());
        }
        messageText.append("\n\nMetaTrader wird geöffnet und bleibt nach dem Test geöffnet.");

        launchBacktest(btConfig, messageText.toString(), parentWindow, pass);
    }

    private static String describeParameterSource(PassPresetResolver.Resolution resolution) {
        return switch (resolution.fidelity()) {
            case EXACT_SNAPSHOT -> "Original-Preset des Laufs (exakt)";
            case OPTIMIZATION_BASE -> "Optimierungs-Preset + Report-Werte";
            case CURRENT_CONFIG -> "aktuelle EA-Konfiguration (unvollständig)";
        };
    }

    private static void launchBacktest(BacktestConfig btConfig, String messageText, Window parentWindow) {
        launchBacktest(btConfig, messageText, parentWindow, null);
    }

    private static void launchBacktest(BacktestConfig btConfig, String messageText, Window parentWindow, Pass targetPass) {
        if (parentWindow != null) {
            Alert infoAlert = new Alert(Alert.AlertType.INFORMATION, messageText, ButtonType.OK);
            infoAlert.setTitle("Einzel-Backtest gestartet");
            infoAlert.setHeaderText("MetaTrader Einzel-Backtest (" + btConfig.getSymbol()
                    + " " + btConfig.getPeriod() + ")");
            infoAlert.initOwner(parentWindow);
            infoAlert.show();
        } else {
            log.info("Single backtest launched for MT5: {}", messageText.replace("\n", " "));
        }

        BacktestRunner runner = new BacktestRunner();
        runner.setLogCallback(msg -> log.info("[Single-BT] {}", msg));

        Task<BacktestResult> task = new Task<>() {
            @Override
            protected BacktestResult call() throws Exception {
                BacktestResult res = runner.runBacktest(btConfig);
                // Keep the link to the run that produced the stored metrics. Pointing
                // the pass at this verification run would detach its numbers from
                // their source and make the original preset unrecoverable.
                if (res != null && res.getOutputDirectory() != null && targetPass != null
                        && targetPass.getReportDirectory().isBlank()) {
                    targetPass.setReportDirectory(res.getOutputDirectory());
                }
                return res;
            }
        };

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private static void showError(Window parentWindow, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        if (parentWindow != null) alert.initOwner(parentWindow);
        alert.showAndWait();
    }

    public static int parseModelToId(String modelStr) {
        if (modelStr == null || modelStr.isBlank()) return 0;
        String m = modelStr.trim();
        String lower = m.toLowerCase(Locale.ROOT);
        if (m.equals("0") || lower.equals("every tick") || lower.equals("model_every_tick")) return 0;
        if (m.equals("1") || lower.equals("1 minute ohlc") || lower.equals("ohlc_m1")
                || lower.equals("model_ohlc_m1") || (lower.contains("ohlc") && lower.contains("m1"))) return 1;
        if (m.equals("2") || lower.equals("open prices") || lower.equals("open price only")
                || lower.equals("model_open_prices")) return 2;
        if (m.equals("3") || lower.equals("math calculations") || lower.equals("model_math_calculations")) return 3;
        if (m.equals("4") || lower.contains("real tick") || lower.equals("model_real_ticks")) return 4;
        return 0;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    private static LocalDate parseDateOrNull(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Express Workflow single-pass backtest with progress dialog, cancel, visual mode,
     * and auto-open report. Uses WorkflowEngine market/context settings.
     * Behavior differs from {@link #runSingleBacktestInMetaTrader}: terminal shutdown
     * follows {@code !visual}, deposit/leverage/currency come from the engine, and a
     * modal progress UI is shown (existing helper keeps the terminal open with an info alert).
     */
    public static void runExpressWorkflowBacktest(CombinedPass cp, boolean visual,
                                                   WorkflowEngine engine, Window owner,
                                                   Collection<String> stylesheets,
                                                   Consumer<String> openReport) {
        if (cp == null || engine == null) return;
        String expert = engine.getExpert();
        if (expert == null || expert.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Bitte wählen Sie zuerst einen Expert Advisor aus!").show();
            return;
        }

        String symbol = engine.getSymbol();
        String period = engine.getPeriod();
        LocalDate from = engine.getFromDate();
        LocalDate to = engine.getToDate();
        int deposit = engine.getDeposit();
        String currency = engine.getCurrency();
        String leverage = engine.getLeverage();
        int tickModel = engine.getTickModel();

        // 1. Prepare BacktestConfig
        BacktestConfig btConfig = new BacktestConfig();
        btConfig.setExpert(expert);
        btConfig.setSymbol(symbol);
        btConfig.setPeriod(period);
        btConfig.setModel(tickModel);
        btConfig.setFromDate(from);
        btConfig.setToDate(to);
        btConfig.setDeposit(deposit);
        btConfig.setCurrency(currency);
        btConfig.setLeverage(leverage);
        btConfig.setShutdownTerminal(!visual);
        btConfig.setVisualMode(visual);

        // 2. Prepare parameter override file
        String eaName = EaParameterManager.extractEaBaseName(expert);
        EaParameterManager eaParamManager = new EaParameterManager();
        com.backtester.report.PassPresetResolver.Resolution resolution =
                com.backtester.report.PassPresetResolver.resolve(cp, expert);
        List<EaParameter> params = resolution.parameters();
        if (params != null && !params.isEmpty()) {
            Path mt5Dir = AppConfig.getInstance().getMt5InstallDir();
            if (mt5Dir != null) {
                Path presetsDir = mt5Dir.resolve("MQL5").resolve("Profiles").resolve("Tester");
                // A dedicated name: Backtester_<EA>.set is the optimizer's preset and
                // overwriting it would destroy the record of the run being verified.
                String presetFileName = "Backtester_" + eaName + "_Verify_Pass" + cp.getPassNumber() + ".set";
                eaParamManager.writeSetFile(presetsDir.resolve(presetFileName), params, eaName);
                btConfig.setExpertParameters(presetFileName);
            }
        }
        if (resolution.warning() != null) {
            new Alert(Alert.AlertType.WARNING, resolution.warning()).show();
        }

        // 3. Create dialog for logs and progress
        Stage dialogStage = new Stage();
        dialogStage.setTitle("Backtest - Pass " + cp.getPassNumber() + (visual ? " (Visuell)" : ""));
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) {
            dialogStage.initOwner(owner);
        }

        VBox dialogBox = new VBox(12);
        dialogBox.setPadding(new Insets(20));
        dialogBox.setStyle("-fx-background-color: #0b0d13; -fx-border-color: #3e4555; -fx-border-width: 1px;");

        Label titleLabel = new Label("BACKTEST LÄUFT...");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        titleLabel.setTextFill(Color.web("#00e5ff"));

        Label statusLabel = new Label("Initialisiere MetaTrader 5...");
        statusLabel.setTextFill(Color.web("#cbd5e1"));
        statusLabel.setFont(Font.font("Segoe UI", 13));

        ProgressBar pb = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
        pb.setPrefWidth(560);

        TextArea logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setFont(Font.font("Consolas", 12));
        logArea.setPrefHeight(300);
        logArea.getStyleClass().add("text-area");
        logArea.setStyle("-fx-control-inner-background: #14161c; -fx-text-fill: #b4bac8;");

        Button cancelBtn = new Button("Abbrechen");
        cancelBtn.getStyleClass().add("button");
        cancelBtn.setStyle("-fx-background-color: #ff3b30; -fx-text-fill: white;");

        dialogBox.getChildren().addAll(titleLabel, statusLabel, pb, logArea, cancelBtn);
        
        Scene dialogScene = new Scene(dialogBox, 600, 480);
        if (stylesheets != null && !stylesheets.isEmpty()) {
            dialogScene.getStylesheets().addAll(stylesheets);
        }
        dialogStage.setScene(dialogScene);

        // 4. Set up task and runner
        BacktestRunner runner = new BacktestRunner();
        runner.setLogCallback(msg -> Platform.runLater(() -> {
            logArea.appendText(msg + "\n");
            logArea.selectPositionCaret(logArea.getLength());
        }));

        Task<BacktestResult> task = new Task<BacktestResult>() {
            @Override
            protected BacktestResult call() throws Exception {
                return runner.runBacktest(btConfig);
            }
        };

        cancelBtn.setOnAction(e -> {
            if (task.isRunning()) {
                runner.cancel();
                task.cancel();
            }
            dialogStage.close();
        });

        task.setOnSucceeded(e -> {
            BacktestResult result = task.getValue();
            Platform.runLater(() -> {
                pb.setProgress(1.0);
                if (result != null && result.isSuccess()) {
                    titleLabel.setText("BACKTEST ERFOLGREICH");
                    titleLabel.setTextFill(Color.web("#00e676"));
                    statusLabel.setText("Backtest abgeschlossen. Report wird geöffnet...");
                    cancelBtn.setText("Schließen");
                    cancelBtn.setStyle(""); // reset red background
                    // Auto open HTML report
                    if (openReport != null) {
                        openReport.accept(result.getOutputDirectory());
                    }
                } else {
                    titleLabel.setText("BACKTEST FEHLGESCHLAGEN");
                    titleLabel.setTextFill(Color.web("#ff3b30"));
                    statusLabel.setText(result != null ? "Fehler: " + result.getMessage() : "Fehler beim Ausführen des Backtests.");
                    cancelBtn.setText("Schließen");
                    cancelBtn.setStyle("");
                }
            });
        });

        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            Platform.runLater(() -> {
                pb.setProgress(0.0);
                titleLabel.setText("BACKTEST FEHLER");
                titleLabel.setTextFill(Color.web("#ff3b30"));
                statusLabel.setText(ex != null ? ex.getMessage() : "Unbekannter Fehler.");
                cancelBtn.setText("Schließen");
                cancelBtn.setStyle("");
            });
        });

        task.setOnCancelled(e -> {
            Platform.runLater(() -> {
                pb.setProgress(0.0);
                titleLabel.setText("BACKTEST ABGEBROCHEN");
                titleLabel.setTextFill(Color.web("#ffb300"));
                statusLabel.setText("Der Backtest wurde vom Benutzer abgebrochen.");
                cancelBtn.setText("Schließen");
                cancelBtn.setStyle("");
            });
        });

        // 5. Start background execution
        Thread th = new Thread(task);
        th.setDaemon(true);
        th.start();

        // 6. Show dialog
        dialogStage.show();
    }

    public static void openReport(String directory, Consumer<String> onError) {
        try {
            javax.swing.SwingUtilities.invokeLater(() -> {
                com.backtester.ui.ReportViewerDialog.showForDirectory(null, directory);
            });
        } catch (Exception e) {
            if (onError != null) {
                onError.accept("Could not open report: " + e.getMessage());
            } else {
                log.error("Could not open report: {}", e.getMessage());
            }
        }
    }

}
