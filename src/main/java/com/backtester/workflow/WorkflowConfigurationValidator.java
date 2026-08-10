package com.backtester.workflow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Stateless configuration checks for custom-project workflow graphs.
 */
public final class WorkflowConfigurationValidator {

    private WorkflowConfigurationValidator() {
    }

    /**
     * Validates databank routing in execution order. A target databank is an
     * output declaration and therefore does not have to exist before its task
     * runs. It becomes available to all following enabled consumers. Tasks
     * that consume strategies need a source that either exists already or was
     * produced by an earlier enabled task. Optimizers are source-independent;
     * strategy selection and portfolio export do not produce a databank.
     */
    public static void validateDatabankExecutionOrder(List<WorkflowTask> tasks,
                                                       Collection<String> existingDatabanks) {
        Set<String> available = new LinkedHashSet<>();
        if (existingDatabanks != null) {
            for (String databank : existingDatabanks) {
                available.add(normalize(databank));
            }
        }
        if (tasks == null) return;

        for (WorkflowTask task : tasks) {
            if (task == null || !task.isEnabled()) continue;
            WorkflowTask.TaskType type = task.getType();
            if (type == null) continue; // The editor reports the invalid task type separately.

            boolean requiresSource = type != WorkflowTask.TaskType.STRATEGY_SELECTION
                    && type != WorkflowTask.TaskType.OPTIMIZER;
            if (requiresSource) {
                String source = effectiveDatabankName(task.getSourceDatabank());
                if (!available.contains(normalize(source))) {
                    throw new IllegalStateException("Task '" + task.getName()
                            + "' verweist auf die nicht vorhandene Quell-Databank '" + source
                            + "'. Sie muss bereits existieren oder von einem vorherigen aktiven Task erzeugt werden.");
                }
            }

            boolean producesTarget = type != WorkflowTask.TaskType.STRATEGY_SELECTION
                    && type != WorkflowTask.TaskType.PORTFOLIO_EXPORT;
            if (producesTarget) {
                String target = effectiveDatabankName(task.getTargetDatabank());
                available.add(normalize(target));
            }
        }
    }

    /**
     * Finds Retester tasks that write into a target databank tab already written
     * by an earlier Retester. Archive history is tab-keyed: the same target tab
     * overwrites that tab's run; different target tabs keep separate runs.
     * This is informational — not a hard blocker.
     */
    public static List<RetesterOverwriteRisk> findRetesterOverwriteRisks(List<WorkflowTask> tasks) {
        Map<String, LinkedHashSet<String>> writersByTarget = new LinkedHashMap<>();
        List<RetesterOverwriteRisk> risks = new ArrayList<>();

        if (tasks == null) return risks;

        for (WorkflowTask task : tasks) {
            if (task == null || !task.isEnabled() || task.getType() != WorkflowTask.TaskType.RETESTER) {
                continue;
            }

            String targetName = effectiveDatabankName(task.getTargetDatabank());
            String targetKey = normalize(targetName);
            LinkedHashSet<String> priorWriters = writersByTarget.getOrDefault(targetKey, new LinkedHashSet<>());

            if (!priorWriters.isEmpty()) {
                risks.add(new RetesterOverwriteRisk(task, targetName, new ArrayList<>(priorWriters)));
            }

            LinkedHashSet<String> updated = new LinkedHashSet<>(priorWriters);
            updated.add(task.getName());
            writersByTarget.put(targetKey, updated);
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
                                        String targetDatabank,
                                        List<String> upstreamRetesterNames) {
        public RetesterOverwriteRisk {
            upstreamRetesterNames = upstreamRetesterNames == null
                    ? List.of() : List.copyOf(upstreamRetesterNames);
        }

        /** @deprecated use {@link #targetDatabank()} — kept for older call sites */
        @Deprecated
        public String sourceDatabank() {
            return targetDatabank;
        }
    }
}
