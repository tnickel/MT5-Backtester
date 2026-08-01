package com.backtester.workflow;

import com.backtester.report.OptimizationResult.CombinedPass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages strategy Databanks for Custom Projects (e.g. "Results", "Portfolio", "Final").
 */
public class DatabankManager {

    private static final Logger logger = LoggerFactory.getLogger(DatabankManager.class);

    private final Map<String, List<CombinedPass>> databanks = new ConcurrentHashMap<>();

    public DatabankManager() {
        // Pre-create standard default databanks
        databanks.put("Results", new ArrayList<>());
        databanks.put("Existing portfolio", new ArrayList<>());
        databanks.put("Final", new ArrayList<>());
    }

    public void loadFromProject(CustomProject project) {
        if (project == null) return;
        Map<String, List<CombinedPass>> saved = project.getDatabanks();
        if (saved != null && !saved.isEmpty()) {
            for (Map.Entry<String, List<CombinedPass>> entry : saved.entrySet()) {
                databanks.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
    }

    public void saveToProject(CustomProject project) {
        if (project == null) return;
        project.setDatabanks(new ConcurrentHashMap<>(databanks));
    }

    public List<String> getDatabankNames() {
        return new ArrayList<>(databanks.keySet());
    }

    public boolean createDatabank(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        String cleanName = name.trim();
        if (!databanks.containsKey(cleanName)) {
            databanks.put(cleanName, new ArrayList<>());
            return true;
        }
        return false;
    }

    public void removeDatabank(String name) {
        if (name != null && !name.equals("Results") && !name.equals("Existing portfolio") && !name.equals("Final")) {
            databanks.remove(name);
        }
    }

    public List<CombinedPass> getDatabank(String name) {
        if (name == null || name.isEmpty()) name = "Results";
        return databanks.computeIfAbsent(name, k -> new ArrayList<>());
    }

    public void setDatabankContent(String name, List<CombinedPass> passes) {
        if (name == null || name.isEmpty()) name = "Results";
        databanks.put(name, passes != null ? new ArrayList<>(passes) : new ArrayList<>());
    }

    public void addPassesToDatabank(String name, List<CombinedPass> passes) {
        if (passes == null || passes.isEmpty()) return;
        List<CombinedPass> target = getDatabank(name);
        target.addAll(passes);
    }

    public void clearDatabank(String name) {
        if (name != null && databanks.containsKey(name)) {
            databanks.get(name).clear();
        }
    }

    public void clearAll() {
        databanks.clear();
        databanks.put("Results", new ArrayList<>());
        databanks.put("Existing portfolio", new ArrayList<>());
        databanks.put("Final", new ArrayList<>());
    }

    /**
     * Executes Databank transfer & filter condition evaluation for a given task.
     */
    public List<CombinedPass> processTaskDatabanks(WorkflowTask task, List<CombinedPass> inputPasses) {
        if (task == null) return inputPasses;

        String sourceName = task.getSourceDatabank();
        String targetName = task.getTargetDatabank();

        // 1. Get input passes from source databank (or provided input)
        List<CombinedPass> sourceList = getDatabank(sourceName);
        if (sourceList.isEmpty() && inputPasses != null && !inputPasses.isEmpty()) {
            sourceList = new ArrayList<>(inputPasses);
            setDatabankContent(sourceName, sourceList);
        }

        logger.info("DATABANK ROUTING START: Task '{}' ({}) | Source: '{}' ({} passes) --> Target: '{}'",
            task.getName(), task.getType(), sourceName, sourceList.size(), targetName);

        List<CombinedPass> filteredOutput = new ArrayList<>();
        List<FilterCondition> conditions = task.getFilterConditions();

        // 2. Evaluate filter conditions
        for (CombinedPass pass : sourceList) {
            boolean passAll = true;
            if (conditions != null && !conditions.isEmpty()) {
                for (FilterCondition cond : conditions) {
                    if (!cond.evaluate(pass)) {
                        passAll = false;
                        break;
                    }
                }
            }

            if (passAll) {
                filteredOutput.add(pass);
            }
        }

        // 3. Write results to target databank
        setDatabankContent(targetName, filteredOutput);

        logger.info("DATABANK ROUTING SUCCESS: Task '{}' copied {} / {} passes into Databank '{}'.",
            task.getName(), filteredOutput.size(), sourceList.size(), targetName);

        return filteredOutput;
    }
}
