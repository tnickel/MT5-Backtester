package com.backtester.ui.javafx;

import com.backtester.workflow.CustomProject;
import com.backtester.workflow.MasterStrategyEntry;
import com.backtester.workflow.WorkflowTask;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProjectWorkflowResumeSemanticsTest {

    @Test
    public void completedOutputTaskIsSkippedOnlyWhenItsTargetContainsStrategies() {
        WorkflowTask task = completedTask(WorkflowTask.TaskType.OPTIMIZER);
        task.setTargetDatabank("stage-1-output");
        AtomicReference<String> inspectedDatabank = new AtomicReference<>();

        boolean skip = ProjectWorkflowEditorView.shouldSkipCompletedTask(task, name -> {
            inspectedDatabank.set(name);
            return true;
        });

        assertTrue(skip);
        assertEquals("stage-1-output", inspectedDatabank.get());
    }

    @Test
    public void completedOutputTaskRunsAgainWhenTargetIsMissingOrEmpty() {
        WorkflowTask task = completedTask(WorkflowTask.TaskType.RETESTER);
        task.setTargetDatabank("ticktest2");

        assertFalse(ProjectWorkflowEditorView.shouldSkipCompletedTask(task, name -> false));
    }

    @Test
    public void taskWithTargetStrategiesIsSkippedRegardlessOfStatusUntilCleared() {
        WorkflowTask task = new WorkflowTask("Pending optimizer", WorkflowTask.TaskType.OPTIMIZER);
        task.setTargetDatabank("stage-1-output");
        task.setStatus(WorkflowTask.TaskStatus.PENDING);
        assertTrue(ProjectWorkflowEditorView.shouldSkipCompletedTask(task, name -> true));

        task.setStatus(WorkflowTask.TaskStatus.FAILED);
        assertTrue(ProjectWorkflowEditorView.shouldReuseExistingTaskResult(task, true, name -> true));
        assertTrue(ProjectWorkflowEditorView.shouldReuseExistingTaskResult(task, false, name -> true));
    }

    @Test
    public void automaticModeReusesStaleRunningOrPendingResultWhenTargetContainsStrategies() {
        WorkflowTask task = new WorkflowTask("Interrupted optimizer", WorkflowTask.TaskType.OPTIMIZER);
        task.setStatus(WorkflowTask.TaskStatus.RUNNING);
        assertTrue(ProjectWorkflowEditorView.shouldReuseExistingTaskResult(task, true, name -> true));

        task.setStatus(WorkflowTask.TaskStatus.PENDING);
        assertTrue(ProjectWorkflowEditorView.shouldReuseExistingTaskResult(task, true, name -> true));
    }

    @Test
    public void doesNotReuseWhenTargetDatabankIsEmptyEvenIfFailedOrRunning() {
        WorkflowTask task = new WorkflowTask("Failed optimizer", WorkflowTask.TaskType.OPTIMIZER);
        task.setStatus(WorkflowTask.TaskStatus.FAILED);
        assertFalse(ProjectWorkflowEditorView.shouldReuseExistingTaskResult(task, true, name -> false));

        task.setStatus(WorkflowTask.TaskStatus.RUNNING);
        assertFalse(ProjectWorkflowEditorView.shouldReuseExistingTaskResult(task, true, name -> false));
    }

    @Test
    public void completedStrategySelectionNeedsNoStrategyTarget() {
        WorkflowTask task = completedTask(WorkflowTask.TaskType.STRATEGY_SELECTION);
        AtomicBoolean databankInspected = new AtomicBoolean(false);

        boolean skip = ProjectWorkflowEditorView.shouldSkipCompletedTask(task, name -> {
            databankInspected.set(true);
            return false;
        });

        assertTrue(skip);
        assertFalse(databankInspected.get());
    }

    @Test
    public void completedPortfolioExportNeedsNoStrategyTarget() {
        WorkflowTask task = completedTask(WorkflowTask.TaskType.PORTFOLIO_EXPORT);

        assertTrue(ProjectWorkflowEditorView.shouldSkipCompletedTask(task, name -> false));
    }

    @Test
    public void failedReferenceResumeRetriesOnlyReferenceAndDoesNotRerunProducerOptimizer() {
        // The pause after a failed reference measurement leaves the producing optimizer
        // completed and its databank filled. A restart must not recompute that stage —
        // it would replace the very passes the pending adoption refers to.
        WorkflowTask producer = completedTask(WorkflowTask.TaskType.OPTIMIZER);
        producer.setTargetDatabank("stage-1-output");
        assertTrue(ProjectWorkflowEditorView.shouldSkipCompletedTask(producer, name -> true));
        assertTrue(ProjectWorkflowEditorView.shouldReuseExistingTaskResult(producer, true, name -> true));

        // Only once its databank is gone does the stage run again.
        assertFalse(ProjectWorkflowEditorView.shouldSkipCompletedTask(producer, name -> false));
    }

    @Test
    public void onlyAMeasuredImprovementIsAllowedToBecomeTheNewMaster() {
        // The chain keeps whatever it does not roll back, so anything short of a proven
        // improvement has to be rejected — a neutral result is up to two percent worse,
        // and repeating that across stages walks the master downwards unnoticed.
        assertTrue(ProjectWorkflowEditorView.confirmsImprovement(
                rated(MasterStrategyEntry.Verdict.BESSER, 4), true));
        assertFalse(ProjectWorkflowEditorView.confirmsImprovement(
                rated(MasterStrategyEntry.Verdict.NEUTRAL, 4), true));
        assertFalse(ProjectWorkflowEditorView.confirmsImprovement(
                rated(MasterStrategyEntry.Verdict.SCHLECHTER, 4), true));
        assertFalse(ProjectWorkflowEditorView.confirmsImprovement(
                rated(MasterStrategyEntry.Verdict.UNBEKANNT, 4), true));
    }

    @Test
    public void theVeryFirstMeasurementEstablishesTheMasterInsteadOfBeingRejected() {
        // It has nothing to be compared against, which reports as UNBEKANNT. Rejecting it
        // would mean rolling back to a master that does not exist yet.
        MasterStrategyEntry first = rated(MasterStrategyEntry.Verdict.UNBEKANNT, -1);

        assertTrue(ProjectWorkflowEditorView.confirmsImprovement(first, false));
    }

    @Test
    public void anUncomparableResultCannotOverwriteAMasterThatAlreadyExists() {
        // An empty or entirely unratable history also reports UNBEKANNT without a
        // reference. Reading that as "first measurement" would let any candidate replace a
        // confirmed master without a single comparison — the confirmed master itself is
        // the only reliable answer to "do we already have one".
        MasterStrategyEntry withoutReference = rated(MasterStrategyEntry.Verdict.UNBEKANNT, -1);

        assertTrue(ProjectWorkflowEditorView.confirmsImprovement(withoutReference, false));
        assertFalse(ProjectWorkflowEditorView.confirmsImprovement(withoutReference, true));
    }

    @Test
    public void anUnratableFirstMeasurementDoesNotBecomeTheMaster() {
        // A run without a finite profit/drawdown — a drawdown of zero, for instance —
        // proves nothing, and as a master nothing could ever be compared against it again.
        MasterStrategyEntry unratable = rated(MasterStrategyEntry.Verdict.UNBEKANNT, -1);
        unratable.setReturnToDrawdown(Double.POSITIVE_INFINITY);

        assertFalse(ProjectWorkflowEditorView.confirmsImprovement(unratable, false));
    }

    @Test
    public void aFailedMeasurementNeverCountsAsConfirmation() {
        MasterStrategyEntry failed = rated(MasterStrategyEntry.Verdict.UNBEKANNT, -1);
        failed.setBacktestSucceeded(false);

        assertFalse(ProjectWorkflowEditorView.confirmsImprovement(failed, false));
        assertFalse(ProjectWorkflowEditorView.confirmsImprovement(null, false));
    }

    private static MasterStrategyEntry rated(MasterStrategyEntry.Verdict verdict, int comparedTo) {
        MasterStrategyEntry entry = new MasterStrategyEntry();
        entry.setBacktestSucceeded(true);
        entry.setVerdict(verdict);
        entry.setComparedToSequence(comparedTo);
        entry.setReturnToDrawdown(2.5);
        return entry;
    }

    @Test
    public void centralAutomaticModeControlsGuidedBestPassAdoption() {
        CustomProject project = new CustomProject("Guided", "EA", "AUDCAD", "M5");
        WorkflowTask first = new WorkflowTask("Stage 1", WorkflowTask.TaskType.OPTIMIZER);
        WorkflowTask next = new WorkflowTask("Stage 2", WorkflowTask.TaskType.OPTIMIZER);
        next.setOptimizerTargetParameters(java.util.List.of("EnvelopePeriod"));
        project.addTask(first);
        project.addTask(next);

        assertFalse(ProjectWorkflowEditorView.shouldAutomaticallyAdoptBestPass(project, next));
        project.setAutomaticModeEnabled(true);
        assertTrue(ProjectWorkflowEditorView.shouldAutomaticallyAdoptBestPass(project, next));

        // Already-adopted basis must still be refreshed in automatic mode.
        next.setOptimizerParameterBasisAdopted(true);
        assertTrue(ProjectWorkflowEditorView.shouldAutomaticallyAdoptBestPass(project, next));
    }

    private static WorkflowTask completedTask(WorkflowTask.TaskType type) {
        WorkflowTask task = new WorkflowTask("Completed task", type);
        task.setStatus(WorkflowTask.TaskStatus.COMPLETED);
        return task;
    }
}
