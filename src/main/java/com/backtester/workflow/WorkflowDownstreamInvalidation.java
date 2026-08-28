package com.backtester.workflow;

import com.backtester.config.EaParameter;
import com.backtester.report.OptimizationResult.CombinedPass;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Clears stale databank outputs when a task's execution settings change so resume
 * does not silently reuse results computed under old conditions.
 */
public final class WorkflowDownstreamInvalidation {

    private WorkflowDownstreamInvalidation() {
    }

    /**
     * Fingerprint of fields that influence MT5 execution or filter routing for this task.
     */
    public static String executionSignature(WorkflowTask task) {
        if (task == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(256);
        sb.append(task.getType()).append('|');
        sb.append(nullToEmpty(task.getSourceDatabank())).append('|');
        sb.append(nullToEmpty(task.getTargetDatabank())).append('|');
        sb.append(nullToEmpty(task.getRetestSymbol())).append('|');
        sb.append(nullToEmpty(task.getRetestPeriod())).append('|');
        sb.append(task.getExecutionMode()).append('|');
        sb.append(nullToEmpty(task.getStartDate())).append('|');
        sb.append(nullToEmpty(task.getEndDate())).append('|');
        sb.append(task.isDeleteFailed()).append('|');
        sb.append(task.getOptimizerMode()).append('|');
        sb.append(task.getOptimizerCriterion()).append('|');
        sb.append(task.getOptimizerForwardMode()).append('|');
        sb.append(nullToEmpty(task.getOptimizerForwardDate())).append('|');
        for (String target : task.getOptimizerTargetParameters()) {
            sb.append("target=").append(nullToEmpty(target)).append(';');
        }
        sb.append('|');
        for (EaParameter parameter : task.getOptimizerParameterSnapshot()) {
            if (parameter == null) continue;
            sb.append(nullToEmpty(parameter.getName())).append(':')
                    .append(nullToEmpty(parameter.getValue())).append(':')
                    .append(parameter.isOptimizeEnabled()).append(':')
                    .append(nullToEmpty(parameter.getOptimizeStart())).append(':')
                    .append(nullToEmpty(parameter.getOptimizeStep())).append(':')
                    .append(nullToEmpty(parameter.getOptimizeEnd())).append(';');
        }
        sb.append('|');
        sb.append(task.getDiversityParamDiffPct()).append('|');
        sb.append(task.getDiversityTradeDiffPct()).append('|');
        sb.append(task.getDiversityMinDifferentParams()).append('|');
        sb.append(task.getDiversityMaxStrategies()).append('|');
        sb.append(task.isDiversityRankByScore()).append('|');
        sb.append(task.isDiversityRankByActivity()).append('|');
        sb.append(task.isDiversityDeduplicateEffectiveV132()).append('|');
        sb.append(task.isDiversityStampClusterIds()).append('|');
        sb.append(task.getRobustnessSteps()).append('|');
        sb.append(task.getRobustnessTimeShifts()).append('|');
        sb.append(task.getRobustnessShiftDays()).append('|');
        sb.append(task.getRobustnessSweepPct()).append('|');
        sb.append(nullToEmpty(task.getRobustnessExcludedParams())).append('|');
        for (FilterCondition condition : task.getFilterConditions()) {
            if (condition == null) {
                continue;
            }
            sb.append(condition.isEnabled()).append(':')
                    .append(condition.getMetric()).append(':')
                    .append(condition.getOperator()).append(':')
                    .append(condition.getValue()).append(';');
        }
        return sb.toString();
    }

    /**
     * Clears this task's target and every later task target in the chain. Returns how
     * many databanks were cleared.
     */
    public static int invalidateFromTask(CustomProject project,
                                         DatabankManager databankManager,
                                         WorkflowTask changedTask) {
        if (project == null || databankManager == null || changedTask == null) {
            return 0;
        }
        List<WorkflowTask> tasks = project.getTasks();
        if (tasks == null || tasks.isEmpty()) {
            return 0;
        }
        int startIndex = tasks.indexOf(changedTask);
        if (startIndex < 0) {
            return 0;
        }

        int cleared = 0;
        for (int i = startIndex; i < tasks.size(); i++) {
            WorkflowTask task = tasks.get(i);
            if (task == null || !task.isEnabled()) {
                continue;
            }
            if (task.getType() == WorkflowTask.TaskType.STRATEGY_SELECTION
                    || task.getType() == WorkflowTask.TaskType.PORTFOLIO_EXPORT) {
                continue;
            }
            String target = task.getTargetDatabank();
            if (target != null && !target.isBlank()) {
                List<CombinedPass> existing = databankManager.getDatabank(target);
                if (existing != null && !existing.isEmpty()) {
                    databankManager.clearDatabank(target);
                    cleared++;
                }
            }
            if (task.getStatus() == WorkflowTask.TaskStatus.COMPLETED
                    || task.getStatus() == WorkflowTask.TaskStatus.FAILED
                    || task.getStatus() == WorkflowTask.TaskStatus.RUNNING) {
                task.setStatus(WorkflowTask.TaskStatus.PENDING);
                task.setLastExecutionLog("Ausgabe ungültig: Task-Einstellungen wurden geändert.");
            }
        }
        DatabankManager.rebuildCensus(project);
        return cleared;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
