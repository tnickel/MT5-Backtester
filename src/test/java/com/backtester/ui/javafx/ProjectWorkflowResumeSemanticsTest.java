package com.backtester.ui.javafx;

import com.backtester.workflow.CustomProject;
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
    public void nonCompletedTaskIsNeverSkippedEvenWhenTargetContainsStrategies() {
        WorkflowTask task = new WorkflowTask("Pending optimizer", WorkflowTask.TaskType.OPTIMIZER);
        task.setStatus(WorkflowTask.TaskStatus.PENDING);

        assertFalse(ProjectWorkflowEditorView.shouldSkipCompletedTask(task, name -> true));
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
    public void automaticModeDoesNotReuseFailedOrOutputlessResult() {
        WorkflowTask task = new WorkflowTask("Failed optimizer", WorkflowTask.TaskType.OPTIMIZER);
        task.setStatus(WorkflowTask.TaskStatus.FAILED);
        assertFalse(ProjectWorkflowEditorView.shouldReuseExistingTaskResult(task, true, name -> true));

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

        next.setOptimizerParameterBasisAdopted(true);
        assertFalse(ProjectWorkflowEditorView.shouldAutomaticallyAdoptBestPass(project, next));
    }

    private static WorkflowTask completedTask(WorkflowTask.TaskType type) {
        WorkflowTask task = new WorkflowTask("Completed task", type);
        task.setStatus(WorkflowTask.TaskStatus.COMPLETED);
        return task;
    }
}
