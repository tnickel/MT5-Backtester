package com.backtester.ui.javafx;

import com.backtester.config.AppConfig;
import com.backtester.engine.WorkflowEngine;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.workflow.CustomProject;
import com.backtester.workflow.DatabankManager;
import com.backtester.workflow.GuidedOptimizationService;
import com.backtester.workflow.WorkflowConfigurationValidator;
import com.backtester.workflow.WorkflowConfigurationValidator.RetesterOverwriteRisk;
import com.backtester.workflow.WorkflowTask;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
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

    /** Applies every task-level override before any runner/config is created. */
    private void applyTaskExecutionConfig(WorkflowTask task) {
        if (task == null || task.getType() == null) {
            throw new IllegalArgumentException("Task-Typ fehlt oder ist ungültig.");
        }

        CustomProject project = host.getProject();

        boolean requiresExpert = (task.getType() == WorkflowTask.TaskType.STRATEGY_SELECTION ||
                                  task.getType() == WorkflowTask.TaskType.OPTIMIZER ||
                                  task.getType() == WorkflowTask.TaskType.RETESTER ||
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
            engine.applyOptimizerTaskParameters(task,
                    GuidedOptimizationService.requiresAdoptedBasis(project, task));
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
                && type != WorkflowTask.TaskType.OPTIMIZER;
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
     * Manual workflows only trust COMPLETED results. Automatic workflows also
     * recover stale PENDING/RUNNING states when their strategy output is still
     * present; FAILED results are always executed again.
     */
    public static boolean shouldReuseExistingTaskResult(WorkflowTask task,
                                                        boolean automaticMode,
                                                        java.util.function.Predicate<String> databankHasStrategies) {
        if (task == null || task.getType() == null || task.getStatus() == null
                || task.getStatus() == WorkflowTask.TaskStatus.FAILED
                || task.getStatus() == WorkflowTask.TaskStatus.DISABLED) {
            return false;
        }
        if (task.getType() == WorkflowTask.TaskType.STRATEGY_SELECTION
                || task.getType() == WorkflowTask.TaskType.PORTFOLIO_EXPORT) {
            return task.getStatus() == WorkflowTask.TaskStatus.COMPLETED;
        }
        boolean resumableStatus = task.getStatus() == WorkflowTask.TaskStatus.COMPLETED
                || (automaticMode && (task.getStatus() == WorkflowTask.TaskStatus.PENDING
                        || task.getStatus() == WorkflowTask.TaskStatus.RUNNING));
        return resumableStatus && databankHasStrategies != null
                && databankHasStrategies.test(task.getTargetDatabank());
    }

    public static boolean shouldAutomaticallyAdoptBestPass(CustomProject project, WorkflowTask task) {
        return project != null && project.isAutomaticModeEnabled()
                && GuidedOptimizationService.isFollowUpOptimizer(project, task);
    }

    private void requireTaskInputStrategies(WorkflowTask task, List<CombinedPass> inputPasses) {
        if (taskRequiresInputStrategies(task.getType())
                && (inputPasses == null || inputPasses.isEmpty())) {
            throw new IllegalStateException("Task '" + task.getName() + "' kann nicht starten: Die Quell-Databank '"
                    + task.getSourceDatabank() + "' enthält keine Strategien.");
        }
    }

    // ─── Execution Logic ──────────────────────────────────────────────────────

    private void runSingleTask(WorkflowTask task) {
        if (task == null) return;

        CustomProject project = host.getProject();
        host.commitCurrentTaskDataSettings();
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
                if (!host.flushProjectSave(host.projectSaveFlushTimeout())) {
                    throw new IllegalStateException("Projekt konnte vor dem Einzeltest nicht gespeichert werden.");
                }
                task.setStatus(WorkflowTask.TaskStatus.RUNNING);
                Platform.runLater(() -> {
                    host.refreshTaskChain();
                    updateProgressUI(0.5, "Führe Einzelstep aus: " + task.getName());
                });

                List<CombinedPass> inputPasses = databankManager.getDatabank(task.getSourceDatabank());
                List<CombinedPass> outputPasses = new ArrayList<>();
                requireTaskInputStrategies(task, inputPasses);

                if (GuidedOptimizationService.isFollowUpOptimizer(project, task)) {
                    if (shouldAutomaticallyAdoptBestPass(project, task)) {
                        host.adoptBestPassAutomatically(task);
                    } else if (GuidedOptimizationService.requiresAdoptedBasis(project, task)) {
                        task.setStatus(WorkflowTask.TaskStatus.PENDING);
                        String pickDb = handPickDatabankLabel(task);
                        String pauseMessage = "Einzelstep pausiert: Task '" + task.getName()
                                + "' braucht zuerst einen Hand-Pick aus der Databank '" + pickDb
                                + "' (Rechtsklick auf einen Pass → Parameter übernehmen).";
                        task.setLastExecutionLog(pauseMessage);
                        host.logToConsole("GUIDED", pauseMessage);
                        updateProgressUI(0.0,
                                "Pausiert: Pass als Parameter-Basis für '" + task.getName() + "' auswählen.");
                        showHandPickRequiredDialog(task.getName(), pickDb);
                        host.saveProject();
                        return null;
                    }
                }

                applyTaskExecutionConfig(task);

                switch (task.getType()) {
                    case STRATEGY_SELECTION:
                        engine.runStep1();
                        host.logToConsole("STRATEGIE-SELEKTION", "Strategie " + engine.getExpert() + " (" + engine.getSymbol() + " " + engine.getPeriod() + ") initialisiert.");
                        outputPasses = new ArrayList<>(inputPasses);
                        break;
                    case OPTIMIZER:
                        engine.runStep1();
                        engine.runStep2(
                            msg -> host.logToConsole("MT5-OPT", msg),
                            (curr, totPasses) -> updateProgressUI((double) curr / Math.max(1, totPasses), "Optimizer Pass " + curr + " / " + totPasses),
                            host.optimizerOutputBaseDirectory(task)
                        );
                        if (engine.getOptResult() != null) {
                            outputPasses = engine.getOptResult().buildCombinedPasses(engine.getForwardMode() > 0, engine.loadScoreWeightsFromDb());
                            String modelName = com.backtester.engine.OptimizationConfig.MODEL_NAMES[task.getMt5Model()];
                            String optSymbol = engine.getSymbol();
                            String optPeriod = engine.getPeriod();
                            if (outputPasses != null) {
                                for (CombinedPass cp : outputPasses) {
                                    if (cp.getSymbol() == null || cp.getSymbol().isBlank()) {
                                        cp.setSymbol(optSymbol);
                                    }
                                    if (cp.getPeriod() == null || cp.getPeriod().isBlank()) {
                                        cp.setPeriod(optPeriod);
                                    }
                                    if (cp.getBacktestPass() != null) {
                                        cp.getBacktestPass().setTickModel(modelName);
                                    }
                                    if (cp.getForwardPass() != null) {
                                        cp.getForwardPass().setTickModel(modelName);
                                    }
                                }
                            }
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
                                task.getDiversityParameterSnapshot());
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
                task.setStatus(WorkflowTask.TaskStatus.COMPLETED);

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
        } catch (IllegalStateException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK);
            alert.setTitle("Workflow-Konfiguration ungültig");
            alert.setHeaderText("Projekt kann nicht sicher gestartet werden");
            Window owner = host.getOwnerWindow();
            if (owner != null) alert.initOwner(owner);
            alert.showAndWait();
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

                    if (GuidedOptimizationService.isFollowUpOptimizer(project, task)) {
                        if (shouldAutomaticallyAdoptBestPass(project, task)) {
                            host.adoptBestPassAutomatically(task);
                        } else if (GuidedOptimizationService.requiresAdoptedBasis(project, task)) {
                            task.setStatus(WorkflowTask.TaskStatus.PENDING);
                            String pickDb = handPickDatabankLabel(task);
                            String pauseMessage = "Workflow wartet vor Task " + (i + 1) + " ('"
                                    + task.getName() + "') auf einen Hand-Pick aus der Databank '"
                                    + pickDb + "'.";
                            task.setLastExecutionLog(pauseMessage);
                            host.logToConsole("GUIDED", pauseMessage);
                            updateProgressUI((double) i / total,
                                    "Pausiert: Pass als Parameter-Basis für '" + task.getName() + "' auswählen.");
                            showHandPickRequiredDialog(task.getName(), pickDb);
                            host.saveProject();
                            return null;
                        }
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
                        List<CombinedPass> inputPasses = databankManager.getDatabank(task.getSourceDatabank());
                        currentPipelinePasses = new ArrayList<>(inputPasses);
                        requireTaskInputStrategies(task, inputPasses);
                        applyTaskExecutionConfig(task);

                        switch (task.getType()) {
                            case STRATEGY_SELECTION:
                                engine.runStep1();
                                host.logToConsole("STRATEGIE-SELEKTION", "Strategie " + engine.getExpert() + " (" + engine.getSymbol() + " " + engine.getPeriod() + ") initialisiert.");
                                break;
                            case OPTIMIZER:
                                engine.runStep1();
                                engine.runStep2(
                                    msg -> host.logToConsole("MT5-OPT", msg),
                                    (curr, totPasses) -> updateProgressUI((double) currentIdx / total, "Optimizer Pass " + curr + " / " + totPasses),
                                    host.optimizerOutputBaseDirectory(task)
                                );
                                if (engine.getOptResult() != null) {
                                    currentPipelinePasses = engine.getOptResult().buildCombinedPasses(engine.getForwardMode() > 0, engine.loadScoreWeightsFromDb());
                                }
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
                                        task.getDiversityParameterSnapshot());
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
                        task.setStatus(WorkflowTask.TaskStatus.COMPLETED);
                        host.logToConsole("PROJECT", "Task " + (i + 1) + " (" + task.getName() + ") erfolgreich beendet. Databank '" + task.getTargetDatabank() + "' hat nun " + processed.size() + " Strategien.");
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
