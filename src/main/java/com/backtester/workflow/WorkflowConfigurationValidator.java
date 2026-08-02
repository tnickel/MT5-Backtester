package com.backtester.workflow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Stateless configuration checks for custom-project workflow graphs.
 */
public final class WorkflowConfigurationValidator {

    private WorkflowConfigurationValidator() {
    }

    /**
     * Finds Retester tasks whose input already contains the single Retester
     * result slot written by an earlier Retester. Running such a task is
     * allowed, but the UI should warn that the earlier result is replaced in
     * the downstream copy and recommend separate source/target databanks.
     */
    public static List<RetesterOverwriteRisk> findRetesterOverwriteRisks(List<WorkflowTask> tasks) {
        Map<String, LinkedHashSet<String>> retesterLineageByDatabank = new LinkedHashMap<>();
        List<RetesterOverwriteRisk> risks = new ArrayList<>();

        if (tasks == null) return risks;

        for (WorkflowTask task : tasks) {
            if (task == null || !task.isEnabled() || task.getType() == null) continue;

            String sourceName = effectiveDatabankName(task.getSourceDatabank());
            String targetName = effectiveDatabankName(task.getTargetDatabank());
            LinkedHashSet<String> sourceLineage = new LinkedHashSet<>(
                    retesterLineageByDatabank.getOrDefault(normalize(sourceName), new LinkedHashSet<>()));

            if (task.getType() == WorkflowTask.TaskType.RETESTER && !sourceLineage.isEmpty()) {
                risks.add(new RetesterOverwriteRisk(task, sourceName, new ArrayList<>(sourceLineage)));
            }

            if (task.getType() == WorkflowTask.TaskType.PORTFOLIO_EXPORT) continue;

            LinkedHashSet<String> targetLineage = task.isDeleteFailed()
                    ? new LinkedHashSet<>()
                    : new LinkedHashSet<>(retesterLineageByDatabank.getOrDefault(
                            normalize(targetName), new LinkedHashSet<>()));
            targetLineage.addAll(sourceLineage);
            if (task.getType() == WorkflowTask.TaskType.RETESTER) {
                targetLineage.add(task.getName());
            }
            retesterLineageByDatabank.put(normalize(targetName), targetLineage);
        }

        return risks;
    }

    private static String effectiveDatabankName(String name) {
        return name == null || name.isBlank() ? DatabankManager.RESULTS : name.trim();
    }

    private static String normalize(String name) {
        return effectiveDatabankName(name).toLowerCase(Locale.ROOT);
    }

    public record RetesterOverwriteRisk(WorkflowTask task,
                                        String sourceDatabank,
                                        List<String> upstreamRetesterNames) {
        public RetesterOverwriteRisk {
            upstreamRetesterNames = upstreamRetesterNames == null
                    ? List.of() : List.copyOf(upstreamRetesterNames);
        }
    }
}
