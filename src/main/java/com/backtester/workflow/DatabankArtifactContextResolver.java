package com.backtester.workflow;

import com.backtester.report.OptimizationResult.CombinedPass;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Resolves the MT5 configuration that actually produced a workflow databank.
 * Project-level symbol/period values can be stale when individual execution
 * tasks override them, so report artifacts must follow the databank lineage.
 */
public final class DatabankArtifactContextResolver {

    private DatabankArtifactContextResolver() {
    }

    public static Context resolve(CustomProject project,
                                  String databankName,
                                  List<CombinedPass> passes,
                                  String fallbackExpert,
                                  String fallbackSymbol,
                                  String fallbackPeriod) {
        WorkflowTask executionTask = findExecutionTask(project, databankName);
        String passSymbol = uniquePassSymbol(passes);
        String passPeriod = uniquePassPeriod(passes);

        String expert = firstNonBlank(project != null ? project.getExpert() : null, fallbackExpert);
        // Prefer per-strategy market context stamped at optimizer/retester write time,
        // then walk filter lineage back to the execution producer (never PRE_FILTER UI leftovers).
        String symbol = firstNonBlank(
                passSymbol,
                executionTask != null ? executionTask.getRetestSymbol() : null,
                project != null ? project.getSymbol() : null,
                fallbackSymbol);
        String period = firstNonBlank(
                passPeriod,
                executionTask != null ? executionTask.getRetestPeriod() : null,
                project != null ? project.getPeriod() : null,
                fallbackPeriod);

        LocalDate from = executionTask != null ? parseDate(executionTask.getStartDate()) : null;
        LocalDate to = executionTask != null ? parseDate(executionTask.getEndDate()) : null;
        boolean retestRange = executionTask != null && executionTask.getType() != null
                && executionTask.getType().canonical() == WorkflowTask.TaskType.RETESTER;
        return new Context(expert, symbol, period, from, to, retestRange);
    }

    static WorkflowTask findExecutionTask(CustomProject project, String databankName) {
        if (project == null || project.getTasks() == null
                || databankName == null || databankName.isBlank()) {
            return null;
        }

        String currentDatabank = databankName.trim();
        Set<String> visited = new HashSet<>();
        int maxHops = project.getTasks().size() + 1;
        for (int hop = 0; hop < maxHops; hop++) {
            String key = currentDatabank.toLowerCase(Locale.ROOT);
            if (!visited.add(key)) return null;

            WorkflowTask producer = findLatestProducer(project.getTasks(), currentDatabank);
            if (producer == null) return null;

            WorkflowTask.TaskType type = producer.getType();
            if (type == null) return null;
            type = type.canonical();
            if (type == WorkflowTask.TaskType.RETESTER
                    || type == WorkflowTask.TaskType.MASTER_REFERENCE
                    || type == WorkflowTask.TaskType.OPTIMIZER
                    || type == WorkflowTask.TaskType.ROBUSTNESS_CV) {
                return producer;
            }

            String source = producer.getSourceDatabank();
            if (source == null || source.isBlank()) return null;
            currentDatabank = source.trim();
        }
        return null;
    }

    private static WorkflowTask findLatestProducer(List<WorkflowTask> tasks, String databankName) {
        for (int index = tasks.size() - 1; index >= 0; index--) {
            WorkflowTask task = tasks.get(index);
            if (task != null && task.getTargetDatabank() != null
                    && task.getTargetDatabank().trim().equalsIgnoreCase(databankName)) {
                return task;
            }
        }
        return null;
    }

    private static String uniquePassSymbol(List<CombinedPass> passes) {
        if (passes == null) return "";
        String unique = null;
        for (CombinedPass pass : passes) {
            if (pass == null || pass.getSymbol() == null || pass.getSymbol().isBlank()) continue;
            String symbol = pass.getSymbol().trim();
            if (unique == null) unique = symbol;
            else if (!unique.equalsIgnoreCase(symbol)) return "";
        }
        return unique != null ? unique : "";
    }

    private static String uniquePassPeriod(List<CombinedPass> passes) {
        if (passes == null) return "";
        String unique = null;
        for (CombinedPass pass : passes) {
            if (pass == null || pass.getPeriod() == null || pass.getPeriod().isBlank()) continue;
            String period = pass.getPeriod().trim();
            if (unique == null) unique = period;
            else if (!unique.equalsIgnoreCase(period)) return "";
        }
        return unique != null ? unique : "";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public record Context(String expert, String symbol, String period,
                          LocalDate from, LocalDate to, boolean retestRange) {
    }
}
