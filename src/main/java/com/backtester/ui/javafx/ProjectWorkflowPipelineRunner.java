package com.backtester.ui.javafx;

import com.backtester.config.AppConfig;
import com.backtester.config.EaParameter;
import com.backtester.engine.OptimizationConfig;
import com.backtester.engine.WorkflowEngine;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.engine.MetaTraderRunLock;
import com.backtester.report.PassPresetResolver;
import com.backtester.workflow.ClusterAutomation;
import com.backtester.workflow.ClusterCensus;
import com.backtester.workflow.ClusterIdentity;
import com.backtester.workflow.CustomProject;
import com.backtester.workflow.DatabankManager;
import com.backtester.workflow.GuidedOptimizationService;
import com.backtester.workflow.MasterSearchSpaceValidator;
import com.backtester.workflow.MasterStrategyLineageService;
import com.backtester.workflow.WorkflowConfigurationValidator;
import com.backtester.workflow.WorkflowConfigurationValidator.RetesterOverwriteRisk;
import com.backtester.workflow.WorkflowPauseException;
import com.backtester.workflow.WorkflowTask;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Window;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Project workflow / single-task pipeline execution extracted from
 * {@link ProjectWorkflowEditorView}. UI and adopt/commit hooks stay on the editor via {@link Host}.
 */
public class ProjectWorkflowPipelineRunner {

    /**
     * Dependencies owned by the workflow editor.
     */
    public interface Host {
        CustomProject getProject();

        WorkflowTask getSelectedTask();

        Window getOwnerWindow();

        void commitCurrentTaskDataSettings();

        /**
         * When {@code task} is the currently selected task, copy DatePicker values into the task
         * (same peek as the former inline {@code applyTaskExecutionConfig} logic). Callers must
         * still {@link #commitCurrentTaskDataSettings()} before a run so typed-but-uncommitted
         * Retester dates are not lost.
         */
        void syncDatePickersIntoSelectedTask(WorkflowTask task);

        void saveProject();

        boolean flushProjectSave(Duration timeout);

        void flushProjectSaveAsync(Runnable continuation);

        void logToConsole(String tag, String message);

        void refreshTaskChain();

        void refreshDatabanksUI();

        void refreshDatabanksUI(String focusDb);

        void setEditorLocked(boolean locked);

        void setStartStopResetDisabled(boolean startDisabled, boolean stopDisabled, boolean resetDisabled);

        void selectProgressTab();

        void clearConsoleLog();

        void updateMainProgress(double progress, String percentText, String label, String taskBannerName);

        void resetProgressDisplay(String label);

        /** Clear banner when progress label still looks like an in-progress "Führe..." message. */
        void clearRunningTaskBannerIfStale();

        /** Purge MT5 cache + optimizer report artefacts for a full workflow reset. */
        void purgeWorkflowRunArtifacts();

        void adoptBestPassAutomatically(WorkflowTask nextOptimizer);

        /**
         * After a stage pick: adopt the winner into the next optimizer if needed,
         * then run the reference backtest that fills the master lineage.
         */
        void recordMasterReferenceCheckpoint(WorkflowTask checkpoint);

        /**
         * Measure each live cluster champion. Does not apply improve-or-die.
         */
        List<CombinedPass> recordClusteredMasterReferences(WorkflowTask checkpoint,
                                                           List<CombinedPass> inputPasses);

        Path optimizerOutputBaseDirectory(WorkflowTask task);

        Duration projectSaveFlushTimeout();
    }

    private static final org.slf4j.Logger logger =
            org.slf4j.LoggerFactory.getLogger(ProjectWorkflowPipelineRunner.class);

    private final WorkflowEngine engine;
    private final DatabankManager databankManager;
    private final Host host;
    private final WorkflowExecutionStatusWindow statusWindow;

    private Task<Void> activeProjectTask;

    public ProjectWorkflowPipelineRunner(WorkflowEngine engine,
                                         DatabankManager databankManager,
                                         Host host) {
        this.engine = engine;
        this.databankManager = databankManager;
        this.host = host;
        this.statusWindow = new WorkflowExecutionStatusWindow(
                host::getOwnerWindow,
                () -> {
                    Window owner = host.getOwnerWindow();
                    if (owner != null && owner.getScene() != null) {
                        return List.copyOf(owner.getScene().getStylesheets());
                    }
                    return List.of();
                });
    }

    public void start() {
        startProjectExecution();
    }

    public void runSingle(WorkflowTask task) {
        runSingleTask(task);
    }

    public void stop() {
        stopProjectExecution();
    }

    public void reset() {
        resetProjectExecution();
    }

    public long findSensitivityRunTimestampForDatabank(String databankName) {
        return findRunTimestampForDatabank(databankName, WorkflowTask.TaskType.ROBUSTNESS_CV);
    }

    // ─── Timestamp lineage ────────────────────────────────────────────────────

    private long findSensitivityRunTimestamp(WorkflowTask aiTask) {
        return findRunTimestampForTaskSource(aiTask, WorkflowTask.TaskType.ROBUSTNESS_CV);
    }

    private long findKiRunTimestampForTask(WorkflowTask targetTask) {
        return findRunTimestampForTaskSource(targetTask, WorkflowTask.TaskType.KI_EVALUATION);
    }

    private long findRunTimestampForTaskSource(WorkflowTask targetTask,
                                               WorkflowTask.TaskType producerType) {
        CustomProject project = host.getProject();
        if (project == null || targetTask == null) return 0L;
        java.util.Map<String, Long> timestampByDatabank = buildRunTimestampLineage(targetTask, producerType);
        return timestampByDatabank.getOrDefault(
                normalizedDatabankName(targetTask.getSourceDatabank()), 0L);
    }

    private long findRunTimestampForDatabank(String databankName,
                                             WorkflowTask.TaskType producerType) {
        CustomProject project = host.getProject();
        if (project == null || databankName == null) return 0L;
        return buildRunTimestampLineage(null, producerType).getOrDefault(
                normalizedDatabankName(databankName), 0L);
    }

    private java.util.Map<String, Long> buildRunTimestampLineage(WorkflowTask stopBefore,
                                                                  WorkflowTask.TaskType producerType) {
        CustomProject project = host.getProject();
        java.util.Map<String, Long> timestampByDatabank = new java.util.HashMap<>();
        for (WorkflowTask task : project.getTasks()) {
            if (task == stopBefore) break;
            if (task == null || !task.isEnabled() || task.getType() == null
                    || task.getType() == WorkflowTask.TaskType.PORTFOLIO_EXPORT
                    || task.getType() == WorkflowTask.TaskType.STRATEGY_SELECTION) {
                continue;
            }

            String sourceKey = normalizedDatabankName(task.getSourceDatabank());
            String targetKey = normalizedDatabankName(task.getTargetDatabank());
            long propagatedTimestamp = task.getType() == WorkflowTask.TaskType.OPTIMIZER
                    ? 0L : timestampByDatabank.getOrDefault(sourceKey, 0L);
            if (task.getType() == producerType) {
                propagatedTimestamp = task.getSensitivityRunTimestamp();
            }
            timestampByDatabank.put(targetKey, propagatedTimestamp);
        }
        return timestampByDatabank;
    }

    private void stampOptimizerPasses(List<CombinedPass> passes, WorkflowTask task) {
        if (passes == null) {
            return;
        }
        String modelName = tickModelName(task != null ? task.getMt5Model() : -1);
        String symbol = engine.getSymbol();
        String period = engine.getPeriod();
        for (CombinedPass pass : passes) {
            if (pass != null) {
                pass.stampMarketIfBlank(symbol, period, modelName);
            }
        }
    }

    private static String tickModelName(int model) {
        String[] names = OptimizationConfig.MODEL_NAMES;
        if (model >= 0 && model < names.length) {
            return names[model];
        }
        return "";
    }

    /** Applies every task-level override before any runner/config is created. */
    private void applyTaskExecutionConfig(WorkflowTask task) {
        if (task == null || task.getType() == null) {
            throw new IllegalArgumentException("Task-Typ fehlt oder ist ungültig.");
        }

        CustomProject project = host.getProject();

        boolean requiresExpert = (task.getType() == WorkflowTask.TaskType.STRATEGY_SELECTION ||
                                  task.getType() == WorkflowTask.TaskType.OPTIMIZER ||
                                  task.getType() == WorkflowTask.TaskType.RETESTER ||
                                  task.getType() == WorkflowTask.TaskType.MASTER_REFERENCE ||
                                  task.getType() == WorkflowTask.TaskType.ROBUSTNESS_CV);

        if (requiresExpert) {
            String taskExpert = (project != null && project.getExpert() != null && !project.getExpert().isBlank())
                    ? project.getExpert().trim()
                    : engine.getExpert();
            if (taskExpert != null && !taskExpert.isBlank()) {
                engine.setExpert(taskExpert);
            } else if (engine.getExpert() == null || engine.getExpert().isBlank()) {
                throw new IllegalArgumentException("Kein Expert Advisor im Projekt festgelegt. Bitte wähle in Task 1 eine .ex5-Datei aus.");
            }
        }

        engine.setTickModel(task.getMt5Model());
        if (task.getType() == WorkflowTask.TaskType.OPTIMIZER) {
            engine.setOptimizationMode(task.getOptimizerMode());
            engine.setOptimizationCriterion(task.getOptimizerCriterion());
            engine.setForwardMode(task.getOptimizerForwardMode());
            engine.setForwardDate(parseDateOrNull(task.getOptimizerForwardDate()));
            List<CombinedPass> sourcePasses = databankManager.getDatabank(task.getSourceDatabank());
            boolean sequentialClusters = ClusterAutomation.shouldRunSequentialClusterOptimizers(
                    project, sourcePasses);
            if (!sequentialClusters) {
                engine.applyOptimizerTaskParameters(task,
                        GuidedOptimizationService.requiresAdoptedBasis(project, task));
            }
        }
        String taskSymbol = task.getRetestSymbol();
        String taskPeriod = task.getRetestPeriod();
        engine.setSymbol(taskSymbol != null && !taskSymbol.isBlank()
                ? taskSymbol.trim() : (project != null ? project.getSymbol() : engine.getSymbol()));
        engine.setPeriod(taskPeriod != null && !taskPeriod.isBlank()
                ? taskPeriod.trim() : (project != null ? project.getPeriod() : engine.getPeriod()));

        // Preserve former selectedTask==task DatePicker peek (after commit before run).
        host.syncDatePickersIntoSelectedTask(task);

        String startText = task.getStartDate();
        String endText = task.getEndDate();
        boolean hasStart = startText != null && !startText.isBlank();
        boolean hasEnd = endText != null && !endText.isBlank();

        LocalDate start = null;
        LocalDate end = null;

        if (hasStart) {
            try {
                start = LocalDate.parse(startText.trim());
            } catch (Exception ex) {
                throw new IllegalArgumentException("Ungültiges Startdatum im Task '"
                        + task.getName() + "': " + startText, ex);
            }
        }
        if (hasEnd) {
            try {
                end = LocalDate.parse(endText.trim());
            } catch (Exception ex) {
                throw new IllegalArgumentException("Ungültiges Enddatum im Task '"
                        + task.getName() + "': " + endText, ex);
            }
        }

        // Fallback for missing dates from engine configuration
        if (start == null && end != null) {
            start = engine.getFromDate();
            if (start == null) start = end.minusYears(3);
        } else if (end == null && start != null) {
            end = engine.getToDate();
            if (end == null) end = LocalDate.now();
        } else if (start == null && end == null) {
            start = engine.getFromDate();
            end = engine.getToDate();
        }

        if (start != null && end != null && !start.isBefore(end)) {
            throw new IllegalArgumentException("Der Zeitraum für Task '" + task.getName()
                    + "' ist ungültig: Das Startdatum muss vor dem Enddatum liegen.");
        }

        switch (task.getType()) {
            case OPTIMIZER:
            case ROBUSTNESS_CV:
                if (start != null && end != null) {
                    engine.setFromDate(start);
                    engine.setToDate(end);
                }
                break;
            case RETESTER:
                engine.setLongtermFromDate(start != null ? start : LocalDate.now().minusYears(7));
                engine.setLongtermToDate(end != null ? end : LocalDate.now());
                break;
            default:
                break;
        }
    }

    private List<CombinedPass> exportPortfolioCandidates(WorkflowTask portfolioTask,
                                                         List<CombinedPass> inputPasses) {
        if (inputPasses == null || inputPasses.isEmpty()) {
            throw new IllegalStateException("Keine Strategien für den Portfolio-Export vorhanden.");
        }
        long requiredKiRunTimestamp = findKiRunTimestampForTask(portfolioTask);
        if (requiredKiRunTimestamp <= 0L) {
            throw new IllegalStateException("Portfolio-Export abgebrochen: Es wurde kein erfolgreiches, "
                    + "zur Quell-Databank passendes KI-Ergebnis gefunden.");
        }
        // Custom-project OOS checks are Retester tasks, not the legacy Step-7
        // validation state. Always run the current candidates through the KI
        // selection so stale global validation results cannot bypass it.
        List<CombinedPass> exportPasses = engine.selectFinalPasses(inputPasses, requiredKiRunTimestamp);
        engine.exportPortfolio(AppConfig.getInstance().getExportDirectory().toString());
        engine.saveWorkflowToHistory();
        return exportPasses;
    }

    private static boolean taskRequiresInputStrategies(WorkflowTask.TaskType type) {
        return type != WorkflowTask.TaskType.STRATEGY_SELECTION
                && type != WorkflowTask.TaskType.OPTIMIZER
                && type != WorkflowTask.TaskType.MASTER_REFERENCE;
    }

    private static List<CombinedPass> championPasses(List<CombinedPass> inputPasses) {
        return GuidedOptimizationService.selectBestPass(inputPasses)
                .map(pass -> {
                    List<CombinedPass> one = new ArrayList<>();
                    one.add(pass);
                    return one;
                })
                .orElseGet(ArrayList::new);
    }

    /** One score leader per cluster id. Empty when nothing is clustered. */
    public static List<CombinedPass> championsByCluster(List<CombinedPass> inputPasses) {
        return ClusterAutomation.championsByCluster(inputPasses);
    }

    public static List<CombinedPass> liveClusterChampions(CustomProject project,
                                                          List<CombinedPass> inputPasses) {
        return ClusterAutomation.liveChampions(project, inputPasses);
    }

    /**
     * Decides whether resume may trust a COMPLETED task.
     * Strategy selection only initializes the EA and portfolio export writes an
     * external artifact, so neither task has a strategy target to validate.
     * Every other task must still have strategies in its configured target
     * databank; otherwise a persisted status without its output is stale.
     */
    public static boolean shouldSkipCompletedTask(WorkflowTask task,
                                                  java.util.function.Predicate<String> databankHasStrategies) {
        return shouldReuseExistingTaskResult(task, false, databankHasStrategies);
    }

    /**
     * Decides whether an already persisted task result can be reused on resume.
     * If the target databank still has strategies, the task is skipped — including
     * after a later FAILED re-run attempt — until the user clears that databank
     * (or Clear-all). Empty/missing output always runs again. DISABLED never runs.
     * Strategy selection / portfolio export only trust COMPLETED (no strategy target).
     */
    public static boolean shouldReuseExistingTaskResult(WorkflowTask task,
                                                        boolean automaticMode,
                                                        java.util.function.Predicate<String> databankHasStrategies) {
        if (task == null || task.getType() == null || task.getStatus() == null
                || task.getStatus() == WorkflowTask.TaskStatus.DISABLED) {
            return false;
        }
        if (task.getType() == WorkflowTask.TaskType.STRATEGY_SELECTION
                || task.getType() == WorkflowTask.TaskType.PORTFOLIO_EXPORT) {
            return task.getStatus() == WorkflowTask.TaskStatus.COMPLETED;
        }
        // Data presence is authoritative: keep finished work across FAILED retries
        // until the user explicitly clears the target databank / Clear-all.
        if (databankHasStrategies != null && databankHasStrategies.test(task.getTargetDatabank())) {
            return true;
        }
        return false;
    }

    public static boolean shouldAutomaticallyAdoptBestPass(CustomProject project, WorkflowTask task) {
        return project != null && project.isAutomaticModeEnabled()
                && GuidedOptimizationService.isFollowUpOptimizer(project, task);
    }

    private void requireTaskInputStrategies(WorkflowTask task, List<CombinedPass> inputPasses) {
        if (taskRequiresInputStrategies(task.getType())
                && (inputPasses == null || inputPasses.isEmpty())) {
            throw new WorkflowPauseException("Task '" + task.getName() + "' kann nicht starten: Die Quell-Databank '"
                    + task.getSourceDatabank() + "' enthält keine Strategien.");
        }
    }

    private boolean pipelineCancelled() {
        return activeProjectTask != null && activeProjectTask.isCancelled();
    }

    // ─── Execution Logic ──────────────────────────────────────────────────────

    private void runSingleTask(WorkflowTask task) {
        if (task == null) return;

        CustomProject project = host.getProject();
        host.commitCurrentTaskDataSettings();
        try {
            if (!shouldDeferRuntimeSearchSpaceCheck(project, task,
                    databankManager.getDatabank(task.getSourceDatabank()))) {
                requireMasterSearchSpace(task, masterBasisForPreflight(project));
            }
        } catch (IllegalStateException ex) {
            showConfigurationError(ex.getMessage(), "Einzelstep kann nicht sicher gestartet werden");
            return;
        }
        if (!confirmRetesterConfigurationWarnings(task)) return;
        host.saveProject();

        host.setStartStopResetDisabled(true, false, true);
        host.setEditorLocked(true);
        host.clearConsoleLog();

        host.selectProgressTab();

        host.logToConsole("SINGLE-STEP", "=== STARTE EINZELTEST FÜR TASK: " + task.getName() + " ===");
        statusWindow.show("Einzelstep", task.getName(), "Task wird gestartet...");
        updateProgressUI(0.0, "Führe Einzelstep aus: " + task.getName());

        engine.setActiveCustomProject(project);
        activeProjectTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try (MetaTraderRunLock.Handle terminal = acquireTerminal("Einzelstep: " + task.getName())) {
                    return runLocked();
                }
            }

            private Void runLocked() throws Exception {
                if (!host.flushProjectSave(host.projectSaveFlushTimeout())) {
                    throw new IllegalStateException("Projekt konnte vor dem Einzeltest nicht gespeichert werden.");
                }

                boolean reuseExistingResult = shouldReuseExistingTaskResult(task,
                        project != null && project.isAutomaticModeEnabled(),
                        databankName -> databankManager.hasDatabank(databankName)
                                && !databankManager.getDatabank(databankName).isEmpty());
                if (reuseExistingResult) {
                    task.setStatus(WorkflowTask.TaskStatus.COMPLETED);
                    String skipMessage = "Einzelstep übersprungen: Task '" + task.getName()
                            + "' hat bereits Ergebnisse in Databank '" + task.getTargetDatabank()
                            + "'. Neu rechnen erst nach Clear-all / leerer Zieldatabank.";
                    task.setLastExecutionLog(skipMessage);
                    host.logToConsole("SINGLE-STEP", skipMessage);
                    updateProgressUI(1.0, "Übersprungen (Daten vorhanden): " + task.getName());
                    host.saveProject();
                    return null;
                }

                List<CombinedPass> inputPasses = databankManager.getDatabank(task.getSourceDatabank());
                List<CombinedPass> outputPasses = new ArrayList<>();
                try {
                    requireTaskInputStrategies(task, inputPasses);
                } catch (WorkflowPauseException pause) {
                    pauseChain(task, pause.getMessage(), 0.0);
                    return null;
                }

                if (!prepareFollowUpOptimizer(project, task, inputPasses, 0.0)) {
                    return null;
                }

                if (!shouldDeferRuntimeSearchSpaceCheck(project, task, inputPasses)) {
                    requireRuntimeMasterSearchSpace(task, runtimeBasis(task, project));
                }
                task.setStatus(WorkflowTask.TaskStatus.RUNNING);
                Platform.runLater(() -> {
                    host.refreshTaskChain();
                    updateProgressUI(0.5, "Führe Einzelstep aus: " + task.getName());
                });

                applyTaskExecutionConfig(task);

                switch (task.getType()) {
                    case STRATEGY_SELECTION:
                        engine.runStep1();
                        host.logToConsole("STRATEGIE-SELEKTION", "Strategie " + engine.getExpert() + " (" + engine.getSymbol() + " " + engine.getPeriod() + ") initialisiert.");
                        outputPasses = new ArrayList<>(inputPasses);
                        break;
                    case OPTIMIZER:
                        try {
                            outputPasses = executeOptimizer(task, project, inputPasses,
                                (curr, totPasses) -> updateProgressUI((double) curr / Math.max(1, totPasses),
                                        "Optimizer Pass " + curr + " / " + totPasses));
                        } catch (WorkflowPauseException pause) {
                            pauseChain(task, pause.getMessage(), 0.0);
                            return null;
                        }
                        break;
                    case RETESTER:
                        long retStartMs = System.currentTimeMillis();
                        int retTotal = inputPasses != null ? inputPasses.size() : 1;
                        outputPasses = engine.runLongtermTest(
                            inputPasses,
                            task,
                            msg -> host.logToConsole("RETESTER", msg),
                            pct -> {
                                int curr = Math.min(retTotal, Math.max(0, (int) Math.round(((double) pct / 100.0) * retTotal)));
                                updateProgressUI((double) pct / 100.0, formatProgressWithEta(task.getName(), curr, retTotal, retStartMs));
                            }
                        );
                        break;
                    case MASTER_REFERENCE:
                        if (inputPasses == null || inputPasses.isEmpty()) {
                            throw new WorkflowPauseException("Checkpoint '" + task.getName()
                                    + "' kann nicht starten: Die Quell-Databank '"
                                    + task.getSourceDatabank() + "' ist leer.");
                        }
                        try {
                            outputPasses = runMasterReference(task, project, inputPasses);
                        } catch (WorkflowPauseException pause) {
                            pauseChain(task, pause.getMessage(), 0.0);
                            return null;
                        }
                        break;
                    case PRE_FILTER:
                        outputPasses = new ArrayList<>(inputPasses);
                        break;
                    case DIVERSITY_FILTER:
                        outputPasses = engine.clusterDatabankPasses(
                                inputPasses,
                                task.getDiversityParamDiffPct(),
                                task.getDiversityTradeDiffPct(),
                                task.getDiversityMinDifferentParams(),
                                task.getDiversityMaxStrategies(),
                                task.isDiversityRankByScore(),
                                task.getDiversityParameterSnapshot(),
                                task.isDiversityDeduplicateEffectiveV132(),
                                task.isDiversityRankByActivity());
                        break;
                    case ROBUSTNESS_CV:
                        engine.setSelectedDiversePasses(inputPasses);
                        task.setSensitivityRunTimestamp(0L);
                        long robStartMs = System.currentTimeMillis();
                        int robTotal = inputPasses != null ? inputPasses.size() : 1;
                        updateProgressUI(0.0, formatProgressWithEta("Robustness Test", 0, robTotal, robStartMs));
                        engine.runStep4(
                            msg -> host.logToConsole("STRESS", msg),
                            pct -> {
                                int curr = Math.min(robTotal, Math.max(0, (int) Math.round(((double) pct / 100.0) * robTotal)));
                                updateProgressUI((double) pct / 100.0, formatProgressWithEta("Robustness Test", curr, robTotal, robStartMs));
                            },
                            task
                        );
                        task.setSensitivityRunTimestamp(engine.getSensitivityRunTimestamp());
                        outputPasses = new ArrayList<>(inputPasses);
                        break;
                    case KI_EVALUATION:
                        long kiRunTimestamp = findSensitivityRunTimestamp(task);
                        engine.setSensitivityRunTimestamp(kiRunTimestamp);
                        task.setSensitivityRunTimestamp(kiRunTimestamp);
                        engine.setSelectedDiversePasses(inputPasses);
                        engine.retainSensitivityResultsForPasses(inputPasses);
                        engine.runStep5(msg -> host.logToConsole("KI-EVAL", msg));
                        outputPasses = new ArrayList<>(inputPasses);
                        break;
                    case PORTFOLIO_EXPORT:
                        outputPasses = exportPortfolioCandidates(task,
                                databankManager.filterPasses(task, inputPasses));
                        break;
                    default:
                        outputPasses = new ArrayList<>(inputPasses);
                        break;
                }

                List<CombinedPass> processed = databankManager.processTaskDatabanks(task, outputPasses);
                task.setOutputPasses(processed);
                if (DatabankManager.shouldHaltChainAfterPreFilter(task, processed)) {
                    pauseChain(task, DatabankManager.emptyPreFilterHaltMessage(task), 1.0);
                    return null;
                }
                task.setStatus(WorkflowTask.TaskStatus.COMPLETED);
                // Otherwise an earlier pause message keeps claiming the task is waiting.
                task.setLastExecutionLog("Erfolgreich beendet: " + processed.size()
                        + " Strategien in '" + task.getTargetDatabank() + "'.");
                if (!task.getFilterRejectionNote().isBlank()) {
                    host.logToConsole("FILTER-WARNUNG", task.getFilterRejectionNote());
                }

                host.logToConsole("SINGLE-STEP", "=== EINZELSTEP ERFOLGREICH BEENDET. Databank '" + task.getTargetDatabank() + "' enthält " + processed.size() + " Strategien ===");
                updateProgressUI(1.0, "Einzelstep beendet.");
                return null;
            }

            @Override
            protected void succeeded() { cleanupExecutionState(); }
            @Override
            protected void failed() {
                task.setStatus(WorkflowTask.TaskStatus.FAILED);
                Throwable error = getException();
                String message = error != null && error.getMessage() != null ? error.getMessage() : "Unbekannter Fehler";
                task.setLastExecutionLog(message);
                logger.error("Task '" + task.getName() + "' fehlgeschlagen", error);
                host.logToConsole("ERROR", "Task '" + task.getName() + "' fehlgeschlagen: " + message);
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Task '" + task.getName() + "' fehlgeschlagen:\n\n" + message, ButtonType.OK);
                    alert.setTitle("Task-Fehler");
                    alert.setHeaderText("Fehler bei Task-Ausführung");
                    Window owner = host.getOwnerWindow();
                    if (owner != null) alert.initOwner(owner);
                    alert.showAndWait();
                });
                cleanupExecutionState();
            }
            @Override
            protected void cancelled() {
                task.setStatus(WorkflowTask.TaskStatus.PENDING);
                task.setLastExecutionLog("Vom Benutzer abgebrochen.");
                cleanupExecutionState();
            }
        };

        Thread t = new Thread(activeProjectTask);
        t.setDaemon(true);
        t.start();
    }

    private void startProjectExecution() {
        CustomProject project = host.getProject();
        if (project == null || project.getTasks().isEmpty()) return;
        host.commitCurrentTaskDataSettings();
        try {
            validateProjectExecutionOrder();
            MasterSearchSpaceValidator.requireProject(
                    preflightSearchSpaceTasks(project),
                    masterBasisForPreflight(project), project.getPeriod());
        } catch (IllegalStateException ex) {
            showConfigurationError(ex.getMessage(), "Projekt kann nicht sicher gestartet werden");
            return;
        }
        if (!confirmRetesterConfigurationWarnings(null)) return;

        host.saveProject();

        host.setStartStopResetDisabled(true, false, true);
        host.setEditorLocked(true);
        host.clearConsoleLog();

        host.selectProgressTab();

        host.logToConsole("PROJECT", "=== STARTE CUSTOM PROJECT: " + project.getName() + " ===");
        statusWindow.show("Workflow", project.getName(), "Workflow wird gestartet...");
        updateProgressUI(0.0, "Workflow startet...");

        engine.setActiveCustomProject(project);
        activeProjectTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try (MetaTraderRunLock.Handle terminal = acquireTerminal("Workflow: " + project.getName())) {
                    return runLocked();
                }
            }

            private Void runLocked() throws Exception {
                if (!host.flushProjectSave(host.projectSaveFlushTimeout())) {
                    throw new IllegalStateException("Projekt konnte vor dem Workflow-Start nicht gespeichert werden.");
                }
                List<WorkflowTask> tasks = project.getTasks();
                int total = tasks.size();
                List<CombinedPass> currentPipelinePasses = new ArrayList<>();

                for (int i = 0; i < total; i++) {
                    WorkflowTask task = tasks.get(i);
                    if (!task.isEnabled()) {
                        task.setStatus(WorkflowTask.TaskStatus.DISABLED);
                        host.logToConsole("PROJECT", "Überspringe deaktivierten Task " + (i + 1) + ": " + task.getName());
                        continue;
                    }

                    WorkflowTask.TaskStatus persistedStatus = task.getStatus();
                    boolean reuseExistingResult = shouldReuseExistingTaskResult(task,
                            project.isAutomaticModeEnabled(), databankName ->
                                    databankManager.hasDatabank(databankName)
                                            && !databankManager.getDatabank(databankName).isEmpty());
                    if (reuseExistingResult) {
                        task.setStatus(WorkflowTask.TaskStatus.COMPLETED);
                        host.logToConsole("PROJECT", "Überspringe Task mit vorhandenem Ergebnis "
                                + (i + 1) + ": " + task.getName()
                                + (persistedStatus != WorkflowTask.TaskStatus.COMPLETED
                                        ? " (persistierter Status: " + persistedStatus + ")" : ""));
                        continue;
                    }

                    if (persistedStatus == WorkflowTask.TaskStatus.COMPLETED) {
                        task.setStatus(WorkflowTask.TaskStatus.PENDING);
                        host.logToConsole("PROJECT", "Task " + (i + 1) + " ('" + task.getName()
                                + "') war als abgeschlossen markiert, aber die Zieldatabank '"
                                + task.getTargetDatabank()
                                + "' enthält keine Strategien. Der Task wird erneut ausgeführt.");
                    }

                    List<CombinedPass> inputPasses = databankManager.getDatabank(task.getSourceDatabank());
                    if (!prepareFollowUpOptimizer(project, task, inputPasses, (double) i / total)) {
                        return null;
                    }

                    if (!shouldDeferRuntimeSearchSpaceCheck(project, task, inputPasses)) {
                        requireRuntimeMasterSearchSpace(task, runtimeBasis(task, project));
                    }
                    task.setStatus(WorkflowTask.TaskStatus.RUNNING);
                    final int currentIdx = i;
                    Platform.runLater(() -> {
                        host.refreshTaskChain();
                        updateProgressUI((double) currentIdx / total,
                            "Führe Task " + (currentIdx + 1) + " von " + total + " aus: " + task.getName());
                    });

                    host.logToConsole("PROJECT", "=== STARTE TASK " + (i + 1) + ": " + task.getName() +
                        " [Source: " + task.getSourceDatabank() + " -> Target: " + task.getTargetDatabank() + "] ===");

                    try {
                        currentPipelinePasses = new ArrayList<>(inputPasses);
                        requireTaskInputStrategies(task, inputPasses);
                        applyTaskExecutionConfig(task);

                        switch (task.getType()) {
                            case STRATEGY_SELECTION:
                                engine.runStep1();
                                host.logToConsole("STRATEGIE-SELEKTION", "Strategie " + engine.getExpert() + " (" + engine.getSymbol() + " " + engine.getPeriod() + ") initialisiert.");
                                break;
                            case OPTIMIZER:
                                currentPipelinePasses = executeOptimizer(task, project, inputPasses,
                                    (curr, totPasses) -> updateProgressUI((double) currentIdx / total,
                                            "Optimizer Pass " + curr + " / " + totPasses));
                                break;
                            case RETESTER:
                                long loopRetStartMs = System.currentTimeMillis();
                                int loopRetTotal = inputPasses != null ? inputPasses.size() : 1;
                                currentPipelinePasses = engine.runLongtermTest(
                                    inputPasses,
                                    task,
                                    msg -> host.logToConsole("RETESTER", msg),
                                    pct -> {
                                        int curr = Math.min(loopRetTotal, Math.max(0, (int) Math.round(((double) pct / 100.0) * loopRetTotal)));
                                        double overallProgress = ((double) currentIdx + ((double) pct / 100.0)) / total;
                                        updateProgressUI(overallProgress, formatProgressWithEta(task.getName(), curr, loopRetTotal, loopRetStartMs));
                                    }
                                );
                                break;
                            case MASTER_REFERENCE:
                                if (inputPasses == null || inputPasses.isEmpty()) {
                                    pauseChain(task, "Checkpoint '" + task.getName()
                                            + "' kann nicht starten: Die Quell-Databank '"
                                            + task.getSourceDatabank() + "' ist leer.",
                                            (double) i / total);
                                    return null;
                                }
                                try {
                                    currentPipelinePasses = runMasterReference(task, project, inputPasses);
                                } catch (WorkflowPauseException pause) {
                                    pauseChain(task, pause.getMessage(), (double) i / total);
                                    return null;
                                }
                                break;
                            case PRE_FILTER:
                                currentPipelinePasses = new ArrayList<>(inputPasses);
                                break;
                            case DIVERSITY_FILTER:
                                currentPipelinePasses = engine.clusterDatabankPasses(
                                        inputPasses,
                                        task.getDiversityParamDiffPct(),
                                        task.getDiversityTradeDiffPct(),
                                        task.getDiversityMinDifferentParams(),
                                        task.getDiversityMaxStrategies(),
                                        task.isDiversityRankByScore(),
                                        task.getDiversityParameterSnapshot(),
                                        task.isDiversityDeduplicateEffectiveV132(),
                                        task.isDiversityRankByActivity());
                                break;
                            case ROBUSTNESS_CV:
                                engine.setSelectedDiversePasses(inputPasses);
                                task.setSensitivityRunTimestamp(0L);
                                long loopRobStartMs = System.currentTimeMillis();
                                int loopRobTotal = inputPasses != null ? inputPasses.size() : 1;
                                updateProgressUI((double) currentIdx / total, formatProgressWithEta("Robustness Test", 0, loopRobTotal, loopRobStartMs));
                                engine.runStep4(
                                    msg -> host.logToConsole("STRESS", msg),
                                    pct -> {
                                        int curr = Math.min(loopRobTotal, Math.max(0, (int) Math.round(((double) pct / 100.0) * loopRobTotal)));
                                        double overallProgress = ((double) currentIdx + ((double) pct / 100.0)) / total;
                                        updateProgressUI(overallProgress, formatProgressWithEta("Robustness Test", curr, loopRobTotal, loopRobStartMs));
                                    },
                                    task
                                );
                                task.setSensitivityRunTimestamp(engine.getSensitivityRunTimestamp());
                                currentPipelinePasses = new ArrayList<>(inputPasses);
                                break;
                            case KI_EVALUATION:
                                long kiRunTimestamp = findSensitivityRunTimestamp(task);
                                engine.setSensitivityRunTimestamp(kiRunTimestamp);
                                task.setSensitivityRunTimestamp(kiRunTimestamp);
                                engine.setSelectedDiversePasses(inputPasses);
                                engine.retainSensitivityResultsForPasses(inputPasses);
                                engine.runStep5(msg -> host.logToConsole("KI-EVAL", msg));
                                currentPipelinePasses = new ArrayList<>(inputPasses);
                                break;
                            case PORTFOLIO_EXPORT:
                                currentPipelinePasses = exportPortfolioCandidates(task,
                                        databankManager.filterPasses(task, inputPasses));
                                break;
                            default:
                                break;
                        }

                        List<CombinedPass> processed = databankManager.processTaskDatabanks(task, currentPipelinePasses);
                        task.setOutputPasses(processed);
                        if (DatabankManager.shouldHaltChainAfterPreFilter(task, processed)) {
                            pauseChain(task, DatabankManager.emptyPreFilterHaltMessage(task),
                                    (double) i / total);
                            return null;
                        }
                        task.setStatus(WorkflowTask.TaskStatus.COMPLETED);
                        // Otherwise an earlier pause message keeps claiming the task is waiting.
                        task.setLastExecutionLog("Erfolgreich beendet: " + processed.size()
                                + " Strategien in '" + task.getTargetDatabank() + "'.");
                        if (!task.getFilterRejectionNote().isBlank()) {
                            host.logToConsole("FILTER-WARNUNG", task.getFilterRejectionNote());
                        }
                        host.logToConsole("PROJECT", "Task " + (i + 1) + " (" + task.getName() + ") erfolgreich beendet. Databank '" + task.getTargetDatabank() + "' hat nun " + processed.size() + " Strategien.");
                    } catch (WorkflowPauseException pause) {
                        pauseChain(task, pause.getMessage(), (double) i / total);
                        return null;
                    } catch (Exception taskEx) {
                        task.setStatus(WorkflowTask.TaskStatus.FAILED);
                        String errMsg = taskEx.getMessage() != null ? taskEx.getMessage() : taskEx.getClass().getSimpleName();
                        task.setLastExecutionLog(errMsg);
                        logger.error("Fehler bei Ausfuehrung von Task " + (i + 1) + " (" + task.getName() + ")", taskEx);
                        host.logToConsole("ERROR", "Task " + (i + 1) + " (" + task.getName() + ") fehlgeschlagen: " + errMsg);
                        throw taskEx;
                    }

                    if (isCancelled()) return null;
                }

                project.setLastRunTimestamp(System.currentTimeMillis());
                host.saveProject();
                updateProgressUI(1.0, "Projekt erfolgreich abgeschlossen!");
                host.logToConsole("PROJECT", "=== CUSTOM PROJECT ERFOLGREICH BEENDET ===");
                return null;
            }

            @Override
            protected void succeeded() {
                cleanupExecutionState();
            }

            @Override
            protected void failed() {
                Throwable error = getException();
                String message = error != null && error.getMessage() != null ? error.getMessage() : "Unbekannter Fehler";
                logger.error("Projektlauf fehlgeschlagen", error);
                host.logToConsole("ERROR", "Projektlauf fehlgeschlagen: " + message);
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Projektlauf fehlgeschlagen:\n\n" + message, ButtonType.OK);
                    alert.setTitle("Workflow-Fehler");
                    alert.setHeaderText("Fehler bei Workflow-Ausführung");
                    Window owner = host.getOwnerWindow();
                    if (owner != null) alert.initOwner(owner);
                    alert.showAndWait();
                });
                cleanupExecutionState();
            }

            @Override
            protected void cancelled() {
                CustomProject cancelledProject = host.getProject();
                if (cancelledProject != null) {
                    for (WorkflowTask task : cancelledProject.getTasks()) {
                        if (task != null && task.getStatus() == WorkflowTask.TaskStatus.RUNNING) {
                            task.setStatus(WorkflowTask.TaskStatus.PENDING);
                            task.setLastExecutionLog("Vom Benutzer abgebrochen.");
                        }
                    }
                }
                cleanupExecutionState();
            }
        };

        Thread t = new Thread(activeProjectTask);
        t.setDaemon(true);
        t.start();
    }

    /**
     * Uses the confirmed master only when it belongs to the active reference context.
     * A project without one uses its own first enabled optimizer snapshot. The global
     * engine state is deliberately excluded because it may still belong to another project.
     */
    /**
     * Follow-up Automatik with 2+ live clusters cannot share one SET/snapshot.
     * Search-space checks run after each cluster basis is applied instead.
     */
    public static boolean shouldDeferRuntimeSearchSpaceCheck(CustomProject project,
                                                             WorkflowTask task,
                                                             List<CombinedPass> sourcePasses) {
        if (task == null || task.getType() != WorkflowTask.TaskType.OPTIMIZER
                || task.getOptimizerTargetParameters().isEmpty()) {
            return false;
        }
        return ClusterAutomation.shouldRunSequentialClusterOptimizers(project, sourcePasses);
    }

    private List<WorkflowTask> preflightSearchSpaceTasks(CustomProject project) {
        List<WorkflowTask> tasks = new ArrayList<>();
        if (project == null || project.getTasks() == null) {
            return tasks;
        }
        for (WorkflowTask task : project.getTasks()) {
            if (task == null) continue;
            List<CombinedPass> source = databankManager != null
                    ? databankManager.getDatabank(task.getSourceDatabank())
                    : List.of();
            if (shouldDeferRuntimeSearchSpaceCheck(project, task, source)) {
                continue;
            }
            tasks.add(task);
        }
        return tasks;
    }

    static List<EaParameter> masterBasisForPreflight(CustomProject project) {
        if (hasContextValidProvenMaster(project)) {
            return project.getProvenMasterParameters();
        }
        if (project != null && project.getTasks() != null) {
            for (WorkflowTask task : project.getTasks()) {
                if (task == null || !task.isEnabled()
                        || task.getType() != WorkflowTask.TaskType.OPTIMIZER) continue;
                List<EaParameter> snapshot = task.getOptimizerParameterSnapshot();
                if (!snapshot.isEmpty()) return snapshot;
            }
        }
        return List.of();
    }

    private List<EaParameter> runtimeBasis(WorkflowTask task, CustomProject project) {
        return runtimeBasis(task, project, engine.getEaParameters());
    }

    static List<EaParameter> runtimeBasis(WorkflowTask task,
                                          CustomProject project,
                                          List<EaParameter> activeEngineBasis) {
        // Adoption and carry-over synchronously update both task snapshot and engine. The
        // engine copy is therefore the independent active basis against which runtime drift
        // in the persisted snapshot can be detected.
        if (task != null && task.isOptimizerParameterBasisAdopted()
                && activeEngineBasis != null && !activeEngineBasis.isEmpty()) {
            return activeEngineBasis;
        }
        if (hasContextValidProvenMaster(project)) {
            return project.getProvenMasterParameters();
        }
        return masterBasisForPreflight(project);
    }

    private static boolean hasContextValidProvenMaster(CustomProject project) {
        return project != null && project.hasProvenMaster()
                && project.getProvenMasterContextKey().equals(
                        MasterStrategyLineageService.currentContextKey(project));
    }

    private void requireMasterSearchSpace(WorkflowTask task, List<EaParameter> basis) {
        if (task == null || task.getType() != WorkflowTask.TaskType.OPTIMIZER
                || task.getOptimizerTargetParameters().isEmpty()) {
            return;
        }
        CustomProject project = host.getProject();
        String period = task.getRetestPeriod();
        if ((period == null || period.isBlank()) && project != null) period = project.getPeriod();
        MasterSearchSpaceValidator.requireTask(task, basis, period);
    }

    private void requireRuntimeMasterSearchSpace(WorkflowTask task, List<EaParameter> basis) {
        if (task == null || task.getType() != WorkflowTask.TaskType.OPTIMIZER
                || task.getOptimizerTargetParameters().isEmpty()) {
            return;
        }
        CustomProject project = host.getProject();
        String period = task.getRetestPeriod();
        if ((period == null || period.isBlank()) && project != null) period = project.getPeriod();
        MasterSearchSpaceValidator.requireRuntimeTask(task, basis, period);
    }

    private void showConfigurationError(String message, String header) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Workflow-Konfiguration ungültig");
        Window owner = host.getOwnerWindow();
        if (owner != null) dialog.initOwner(owner);

        VBox content = new VBox(12);
        content.setPadding(new Insets(16));
        content.setPrefWidth(720);
        content.setPrefHeight(420);
        content.setStyle("-fx-background-color: #0b0d13;");

        Label headerLbl = new Label(header != null ? header : "Workflow-Konfiguration ungültig");
        headerLbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        headerLbl.setTextFill(Color.web("#ff5252"));

        TextArea textArea = new TextArea(message);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setStyle("-fx-control-inner-background: #0f141c; -fx-text-fill: #e2e8f0; -fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 12px;");
        VBox.setVgrow(textArea, Priority.ALWAYS);

        content.getChildren().addAll(headerLbl, textArea);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dialog.getDialogPane().setStyle("-fx-background-color: #0b0d13;");

        if (owner != null && owner.getScene() != null && !owner.getScene().getStylesheets().isEmpty()) {
            dialog.getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
        }

        dialog.showAndWait();
    }

    private void validateProjectExecutionOrder() {
        CustomProject project = host.getProject();
        WorkflowConfigurationValidator.validateDatabankExecutionOrder(
                project.getTasks(), databankManager.getDatabankNames());
        java.util.Map<String, Set<String>> robustnessLineageByDatabank = new java.util.HashMap<>();
        java.util.Map<String, Set<String>> kiLineageByDatabank = new java.util.HashMap<>();
        for (WorkflowTask task : project.getTasks()) {
            if (task == null || !task.isEnabled()) continue;
            if (task.getType() == null) {
                throw new IllegalStateException("Ein aktivierter Task besitzt keinen gültigen Typ.");
            }
            String sourceKey = normalizedDatabankName(task.getSourceDatabank());
            String targetKey = normalizedDatabankName(task.getTargetDatabank());
            Set<String> sourceKiLineage = new java.util.HashSet<>(
                    kiLineageByDatabank.getOrDefault(sourceKey, Set.of()));
            Set<String> sourceRobustnessLineage = new java.util.HashSet<>(
                    robustnessLineageByDatabank.getOrDefault(sourceKey, Set.of()));
            if (task.getType() == WorkflowTask.TaskType.PORTFOLIO_EXPORT && sourceKiLineage.isEmpty()) {
                throw new IllegalStateException("Task '" + task.getName()
                        + "' benötigt davor einen aktivierten KI-Bewertungs-Task, dessen Ergebnisse bis zur Quell-Databank '"
                        + task.getSourceDatabank() + "' weitergereicht werden.");
            }
            if (task.getType() == WorkflowTask.TaskType.OPTIMIZER
                    || task.getType() == WorkflowTask.TaskType.RETESTER
                    || task.getType() == WorkflowTask.TaskType.MASTER_REFERENCE
                    || task.getType() == WorkflowTask.TaskType.ROBUSTNESS_CV) {
                LocalDate start = parseDateOrNull(task.getStartDate());
                LocalDate end = parseDateOrNull(task.getEndDate());
                if (start == null || end == null || !start.isBefore(end)) {
                    throw new IllegalStateException("Task '" + task.getName()
                            + "' besitzt keinen gültigen Zeitraum.");
                }
            }
            if (task.getType() == WorkflowTask.TaskType.OPTIMIZER
                    && task.getOptimizerForwardMode() == 4) {
                LocalDate forwardDate = parseDateOrNull(task.getOptimizerForwardDate());
                LocalDate start = parseDateOrNull(task.getStartDate());
                LocalDate end = parseDateOrNull(task.getEndDate());
                if (forwardDate == null || !forwardDate.isAfter(start) || !forwardDate.isBefore(end)) {
                    throw new IllegalStateException("Task '" + task.getName()
                            + "' besitzt kein gültiges benutzerdefiniertes Forward-Datum.");
                }
            }
            if (task.getType() == WorkflowTask.TaskType.KI_EVALUATION
                    && sourceRobustnessLineage.isEmpty()) {
                throw new IllegalStateException("Task '" + task.getName()
                        + "' benötigt davor einen aktivierten Robustness-Task, dessen Ergebnisse bis zur Quell-Databank '"
                        + task.getSourceDatabank() + "' weitergereicht werden.");
            }
            if (task.getType() != WorkflowTask.TaskType.PORTFOLIO_EXPORT
                    && task.getType() != WorkflowTask.TaskType.STRATEGY_SELECTION) {
                Set<String> targetKiLineage = task.isDeleteFailed()
                        ? new java.util.HashSet<>()
                        : new java.util.HashSet<>(kiLineageByDatabank.getOrDefault(targetKey, Set.of()));
                if (task.getType() != WorkflowTask.TaskType.OPTIMIZER) {
                    targetKiLineage.addAll(sourceKiLineage);
                }
                if (task.getType() == WorkflowTask.TaskType.KI_EVALUATION) {
                    targetKiLineage.add(task.getName());
                }
                kiLineageByDatabank.put(targetKey, targetKiLineage);

                Set<String> targetRobustnessLineage = task.isDeleteFailed()
                        ? new java.util.HashSet<>()
                        : new java.util.HashSet<>(robustnessLineageByDatabank.getOrDefault(targetKey, Set.of()));
                if (task.getType() != WorkflowTask.TaskType.OPTIMIZER) {
                    targetRobustnessLineage.addAll(sourceRobustnessLineage);
                }
                if (task.getType() == WorkflowTask.TaskType.ROBUSTNESS_CV) {
                    targetRobustnessLineage.add(task.getName());
                }
                robustnessLineageByDatabank.put(targetKey, targetRobustnessLineage);
            }
        }
    }

    private boolean confirmRetesterConfigurationWarnings(WorkflowTask onlyTask) {
        CustomProject project = host.getProject();
        if (project == null) return true;
        List<RetesterOverwriteRisk> risks = WorkflowConfigurationValidator
                .findRetesterOverwriteRisks(project.getTasks());
        if (onlyTask != null) {
            risks.removeIf(risk -> risk.task() != onlyTask);
        }
        if (risks.isEmpty()) return true;

        StringBuilder details = new StringBuilder();
        for (RetesterOverwriteRisk risk : risks) {
            if (details.length() > 0) details.append("\n\n");
            details.append("• Retester '").append(risk.task().getName())
                    .append("' schreibt in Ziel-Tab '").append(risk.targetDatabank()).append("'.\n")
                    .append("  Dieser Tab wurde bereits beschrieben von: ")
                    .append(String.join(", ", risk.upstreamRetesterNames())).append(".");
        }
        details.append("\n\nGleicher Ziel-Tab überschreibt den Backtest-Run dieses Tabs (Setfile + Ergebnis). "
                + "Andere Tabs bleiben in der Strategie-Historie erhalten. "
                + "Für parallele Retests getrennte Ziel-Databanken verwenden.");

        ButtonType configureButton = new ButtonType("Zur Konfiguration", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType continueButton = new ButtonType("Trotzdem starten", ButtonBar.ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.INFORMATION, details.toString(), configureButton, continueButton);
        alert.setTitle("Retester-Konfiguration prüfen");
        alert.setHeaderText("Gleicher Ziel-Tab überschreibt den Run dieses Tabs");
        Window owner = host.getOwnerWindow();
        if (owner != null) alert.initOwner(owner);
        return alert.showAndWait().orElse(configureButton) == continueButton;
    }

    private static String normalizedDatabankName(String name) {
        String cleanName = name == null || name.isBlank() ? DatabankManager.RESULTS : name.trim();
        return cleanName.toLowerCase(Locale.ROOT);
    }

    private static LocalDate parseDateOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void stopProjectExecution() {
        if (activeProjectTask != null) {
            activeProjectTask.cancel();
        }
        engine.cancel();
    }

    private void resetProjectExecution() {
        CustomProject project = host.getProject();
        if (project != null && project.getTasks() != null) {
            for (WorkflowTask t : project.getTasks()) {
                t.setStatus(WorkflowTask.TaskStatus.PENDING);
                t.getOutputPasses().clear();
            }
            GuidedOptimizationService.clearAdoptedBasesForRestart(project);
            databankManager.clearAll();
            host.purgeWorkflowRunArtifacts();
            host.flushProjectSaveAsync(() -> {
                host.refreshTaskChain();
                host.refreshDatabanksUI();
            });
        }
        host.resetProgressDisplay("Zurückgesetzt.");
        host.clearConsoleLog();
    }

    private void cleanupExecutionState() {
        engine.setActiveCustomProject(null);
        host.saveProject();
        Platform.runLater(() -> {
            statusWindow.hide();
            host.setStartStopResetDisabled(false, true, false);
            host.setEditorLocked(false);
            activeProjectTask = null;
            host.refreshTaskChain();
            WorkflowTask selected = host.getSelectedTask();
            String focusDb = selected != null ? selected.getTargetDatabank() : null;
            host.refreshDatabanksUI(focusDb);
            host.clearRunningTaskBannerIfStale();
        });
    }

    private String formatProgressWithEta(String taskName, int current, int total, long startTimeMs) {
        if (total <= 0) return taskName + " (" + current + "%)";
        int pct = (int) Math.min(100, Math.max(0, Math.round(((double) current / total) * 100.0)));
        StringBuilder sb = new StringBuilder();
        sb.append(taskName).append(": Strategie ").append(current).append(" / ").append(total).append(" (").append(pct).append("%)");

        if (current > 0 && startTimeMs > 0) {
            long elapsedMs = System.currentTimeMillis() - startTimeMs;
            double avgMsPerStrategy = (double) elapsedMs / current;
            int remainingStrategies = total - current;
            long remainingSec = Math.max(0, Math.round((remainingStrategies * avgMsPerStrategy) / 1000.0));

            long mins = remainingSec / 60;
            long secs = remainingSec % 60;
            double avgSec = avgMsPerStrategy / 1000.0;

            sb.append(" | Restzeit: ");
            if (mins > 0) {
                sb.append(String.format(Locale.US, "%02dm %02ds", mins, secs));
            } else {
                sb.append(String.format(Locale.US, "%ds", secs));
            }
            sb.append(String.format(Locale.US, " (Ø %.1fs/Strat.)", avgSec));
        } else {
            sb.append(" | Restzeit: Berechne...");
        }

        return sb.toString();
    }

    private void updateProgressUI(double progress, String label) {
        double clamped = Math.max(0.0, Math.min(1.0, progress));
        int pct = (int) Math.round(clamped * 100.0);
        String percentText = pct + "%";
        String taskBannerName = extractCurrentTaskName(label);
        Platform.runLater(() -> {
            host.updateMainProgress(clamped, percentText, label, taskBannerName);
            statusWindow.update(taskBannerName, clamped, percentText, label);
        });
    }

    private static String handPickDatabankLabel(WorkflowTask task) {
        if (task != null && task.getSourceDatabank() != null && !task.getSourceDatabank().isBlank()) {
            return task.getSourceDatabank().trim();
        }
        return "der vorherigen Stufe";
    }

    /**
     * Follow-up Automatik: pause on 0 live clusters; sequential skip of the global
     * champion adopt when 2+ lines live; otherwise today's single adopt.
     *
     * @return false when the chain was paused
     */
    private boolean prepareFollowUpOptimizer(CustomProject project,
                                             WorkflowTask task,
                                             List<CombinedPass> inputPasses,
                                             double pauseProgress) {
        if (!GuidedOptimizationService.isFollowUpOptimizer(project, task)) {
            return true;
        }
        if (ClusterAutomation.hasZeroLiveClusters(project, inputPasses)) {
            pauseChain(task, ClusterAutomation.zeroLiveClustersMessage(task), pauseProgress);
            return false;
        }
        if (ClusterAutomation.shouldRunSequentialClusterOptimizers(project, inputPasses)) {
            host.logToConsole("CLUSTER", "Folge-Optimizer '" + task.getName()
                    + "' läuft nacheinander für "
                    + ClusterAutomation.liveChampions(project, inputPasses).size()
                    + " lebende Linien (ein Terminal).");
            return true;
        }
        if (shouldAutomaticallyAdoptBestPass(project, task)) {
            try {
                host.adoptBestPassAutomatically(task);
            } catch (WorkflowPauseException pause) {
                pauseChain(task, pause.getMessage(), pauseProgress);
                return false;
            }
            return true;
        }
        if (GuidedOptimizationService.requiresAdoptedBasis(project, task)) {
            task.setStatus(WorkflowTask.TaskStatus.PENDING);
            String pickDb = handPickDatabankLabel(task);
            String pauseMessage = "Workflow wartet vor '" + task.getName()
                    + "' auf einen Hand-Pick aus der Databank '" + pickDb + "'.";
            task.setLastExecutionLog(pauseMessage);
            host.logToConsole("GUIDED", pauseMessage);
            updateProgressUI(pauseProgress,
                    "Pausiert: Pass als Parameter-Basis für '" + task.getName() + "' auswählen.");
            showHandPickRequiredDialog(task.getName(), pickDb);
            host.saveProject();
            return false;
        }
        return true;
    }

    private List<CombinedPass> runMasterReference(WorkflowTask task,
                                                  CustomProject project,
                                                  List<CombinedPass> inputPasses) {
        if (ClusterAutomation.usesClusteredAutomatik(project, inputPasses)) {
            List<CombinedPass> survivors = host.recordClusteredMasterReferences(task, inputPasses);
            if (survivors == null || survivors.isEmpty()) {
                throw new WorkflowPauseException(ClusterAutomation.zeroLiveClustersMessage(task));
            }
            return survivors;
        }
        host.recordMasterReferenceCheckpoint(task);
        return championPasses(inputPasses);
    }

    private List<CombinedPass> executeOptimizer(WorkflowTask task,
                                                CustomProject project,
                                                List<CombinedPass> inputPasses,
                                                java.util.function.BiConsumer<Integer, Integer> progress)
            throws Exception {
        if (ClusterAutomation.shouldRunSequentialClusterOptimizers(project, inputPasses)) {
            return runSequentialClusterOptimizers(task, project, inputPasses, progress);
        }
        engine.runStep1();
        engine.runStep2(
                msg -> host.logToConsole("MT5-OPT", msg),
                progress,
                host.optimizerOutputBaseDirectory(task));
        List<CombinedPass> outputPasses = new ArrayList<>();
        if (engine.getOptResult() != null) {
            outputPasses = engine.getOptResult().buildCombinedPasses(
                    engine.getForwardMode() > 0, engine.loadScoreWeightsFromDb());
            stampOptimizerPasses(outputPasses, task);
        }
        return outputPasses;
    }

    private List<CombinedPass> runSequentialClusterOptimizers(WorkflowTask task,
                                                              CustomProject project,
                                                              List<CombinedPass> inputPasses,
                                                              java.util.function.BiConsumer<Integer, Integer> progress)
            throws Exception {
        List<CombinedPass> live = ClusterAutomation.liveChampions(project, inputPasses);
        List<CombinedPass> merged = new ArrayList<>();
        if (project.getClusterCensus() == null) {
            project.setClusterCensus(new ClusterCensus());
        }
        int total = live.size();
        int index = 0;
        int failedLines = 0;
        for (CombinedPass champion : live) {
            if (pipelineCancelled()) {
                throw new InterruptedException(
                        "Workflow abgebrochen während sequenzieller Cluster-Optimizer.");
            }
            index++;
            String clusterId = ClusterIdentity.normalize(champion);
            host.logToConsole("CLUSTER", "Optimizer '" + task.getName() + "' Linie " + clusterId
                    + " (" + index + "/" + total + "), Basis Pass #" + champion.getPassNumber()
                    + " — sequentiell, ein Terminal.");
            try {
                applyClusterOptimizerBasis(task, project, champion);
                engine.runStep1();
                engine.runStep2(
                        msg -> host.logToConsole("MT5-OPT", clusterId + ": " + msg),
                        progress,
                        host.optimizerOutputBaseDirectory(task));
                if (engine.getOptResult() != null) {
                    List<CombinedPass> produced = engine.getOptResult().buildCombinedPasses(
                            engine.getForwardMode() > 0, engine.loadScoreWeightsFromDb());
                    stampOptimizerPasses(produced, task);
                    ClusterAutomation.stampFixedClusterId(produced, clusterId);
                    merged.addAll(produced);
                }
            } catch (InterruptedException ex) {
                throw ex;
            } catch (Exception ex) {
                failedLines++;
                String err = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                host.logToConsole("CLUSTER", "Linie " + clusterId + " fehlgeschlagen: " + err
                        + " — bereits gerechnete Linien bleiben, restliche laufen weiter.");
                logger.warn("Sequential cluster optimizer failed for {}", clusterId, ex);
                ClusterAutomation.markDied(project.getClusterCensus(), clusterId,
                        task.getName(), task.getTargetDatabank());
            }
        }
        List<CombinedPass> kept = ClusterAutomation.applyOptimizerImproveOrDie(
                project.getClusterCensus(),
                task.getName(),
                task.getTargetDatabank(),
                live,
                merged);
        if (kept.isEmpty() && failedLines == total && total > 0) {
            throw new WorkflowPauseException("Automatik angehalten: Alle "
                    + total + " Cluster-Linien sind in '" + task.getName() + "' fehlgeschlagen.");
        }
        return kept;
    }

    private void applyClusterOptimizerBasis(WorkflowTask task,
                                            CustomProject project,
                                            CombinedPass champion) {
        PassPresetResolver.Resolution resolution = PassPresetResolver.resolve(
                champion, project != null ? project.getExpert() : engine.getExpert());
        if (resolution.fidelity() == PassPresetResolver.Fidelity.CURRENT_CONFIG) {
            throw new IllegalStateException("Cluster " + ClusterIdentity.normalize(champion)
                    + ": Pass #" + champion.getPassNumber()
                    + " hat kein archiviertes Lauf-Preset.");
        }
        GuidedOptimizationService.AdoptionResult result = GuidedOptimizationService.adoptPassParameters(
                project, engine.getEaParameters(), resolution.parameters(), champion,
                task.getSourceDatabank());
        if (result.getNextOptimizer() != task) {
            throw new IllegalStateException("Cluster-Optimizer zielte auf '"
                    + result.getNextOptimizer().getName() + "' statt '" + task.getName() + "'.");
        }
        engine.setEaParameters(task.getOptimizerParameterSnapshot());
        engine.applyOptimizerTaskParameters(task, false);
        requireRuntimeMasterSearchSpace(task, result.getParameters());
    }

    /**
     * Claims the MetaTrader terminal for this run. A reference backtest started from a
     * hand-pick uses the same lock, so the two can no longer kill each other's terminal.
     */
    private MetaTraderRunLock.Handle acquireTerminal(String owner) throws InterruptedException {
        if (MetaTraderRunLock.isBusyForOtherThread()) {
            host.logToConsole("MT5", "Wartet auf MetaTrader — belegt durch: "
                    + MetaTraderRunLock.currentOwner());
        }
        return MetaTraderRunLock.acquire(owner);
    }

    /**
     * Deliberate stop, not a failure: the task stays reopenable and the user decides
     * whether to continue. Restarting the chain is the confirmation.
     */
    private void pauseChain(WorkflowTask task, String reason, double progress) {
        String message = reason != null && !reason.isBlank()
                ? reason : "Der Workflow wurde angehalten.";
        task.setStatus(WorkflowTask.TaskStatus.PENDING);
        task.setLastExecutionLog(message);
        host.logToConsole("WORKFLOW-PAUSE", message);
        updateProgressUI(progress, "Angehalten vor '" + task.getName() + "'");
        host.saveProject();
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
            alert.setTitle("Workflow angehalten");
            alert.setHeaderText("Kette wartet auf deine Entscheidung");
            Window owner = host.getOwnerWindow();
            if (owner != null) alert.initOwner(owner);
            alert.showAndWait();
        });
    }

    private void showHandPickRequiredDialog(String taskName, String pickDatabank) {
        String safeTask = taskName != null && !taskName.isBlank() ? taskName.trim() : "Optimizer";
        String safeDb = pickDatabank != null && !pickDatabank.isBlank() ? pickDatabank.trim() : "der vorherigen Stufe";
        String body = "Task '" + safeTask + "' braucht zuerst einen Hand-Pick.\n\n"
                + "1. Databank '" + safeDb + "' öffnen\n"
                + "2. Pass wählen → Rechtsklick → Parameter übernehmen\n"
                + "3. Danach den Task erneut starten";
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, body, ButtonType.OK);
            alert.setTitle("Hand-Pick erforderlich");
            alert.setHeaderText("Parameter-Basis fehlt");
            Window owner = host.getOwnerWindow();
            if (owner != null) alert.initOwner(owner);
            alert.showAndWait();
        });
    }

    private String extractCurrentTaskName(String label) {
        if (label == null || label.isBlank()) return "—";
        String text = label.trim();
        // "Führe Task 2 von 4 aus: Name" / "Führe Einzelstep aus: Name"
        int colon = text.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < text.length()) {
            String after = text.substring(colon + 1).trim();
            if (!after.isEmpty()) {
                // Keep only the task name before optional " | Restzeit..."
                int pipe = after.indexOf(" | ");
                return pipe >= 0 ? after.substring(0, pipe).trim() : after;
            }
        }
        int pipe = text.indexOf(" | ");
        return pipe >= 0 ? text.substring(0, pipe).trim() : text;
    }
}
