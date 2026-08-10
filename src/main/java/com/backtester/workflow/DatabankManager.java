package com.backtester.workflow;

import com.backtester.report.OptimizationResult.CombinedPass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages strategy Databanks for Custom Projects (e.g. "Results", "Portfolio", "Final").
 */
public class DatabankManager {

    private static final Logger logger = LoggerFactory.getLogger(DatabankManager.class);
    public static final String RESULTS = "Results";
    public static final String EXISTING_PORTFOLIO = "Existing portfolio";
    public static final String FINAL = "Final";

    /** Guarded by this instance's monitor; values are never exposed directly. */
    private final Map<String, List<CombinedPass>> databanks = new LinkedHashMap<>();

    public DatabankManager() {
        resetToDefaults();
    }

    public synchronized void loadFromProject(CustomProject project) {
        resetToDefaults();
        if (project == null) return;
        Map<String, List<CombinedPass>> saved = project.getDatabanks();
        if (saved != null && !saved.isEmpty()) {
            for (Map.Entry<String, List<CombinedPass>> entry : saved.entrySet()) {
                String name = canonicalName(entry.getKey());
                if (name != null) {
                    databanks.put(name, copyValidPasses(entry.getValue()));
                }
            }
        }
        // Seed tab-keyed archive from legacy longtermPass slots when missing.
        StrategyBacktestArchiveStore.migrateFromLongtermPasses(project);
    }

    public synchronized void saveToProject(CustomProject project) {
        saveToProject(project, true);
    }

    /**
     * Persists a stable snapshot. When contents are disabled, custom databank
     * names are still retained so their tabs do not disappear after restart.
     */
    public synchronized void saveToProject(CustomProject project, boolean includeContents) {
        if (project == null) return;
        Map<String, List<CombinedPass>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, List<CombinedPass>> entry : databanks.entrySet()) {
            snapshot.put(entry.getKey(), includeContents
                    ? copyValidPasses(entry.getValue()) : new ArrayList<>());
        }
        project.setDatabanks(snapshot);
        if (includeContents) {
            StrategyBacktestArchiveStore.migrateFromLongtermPasses(project);
        }
    }

    public synchronized List<String> getDatabankNames() {
        return new ArrayList<>(databanks.keySet());
    }

    public synchronized boolean hasDatabank(String name) {
        return findStoredName(name) != null;
    }

    public synchronized boolean createDatabank(String name) {
        String cleanName = canonicalName(name);
        if (cleanName == null || findStoredName(cleanName) != null) return false;
        if (!databanks.containsKey(cleanName)) {
            databanks.put(cleanName, new ArrayList<>());
            return true;
        }
        return false;
    }

    public synchronized void removeDatabank(String name) {
        String storedName = findStoredName(name);
        if (storedName != null && !isStandard(storedName)) databanks.remove(storedName);
    }

    /** Returns a snapshot so callers cannot mutate an ArrayList during a worker run. */
    public synchronized List<CombinedPass> getDatabank(String name) {
        String storedName = ensureDatabank(name);
        return copyValidPasses(databanks.get(storedName));
    }

    /**
     * Returns only rows that can correspond to the supplied references. This
     * avoids cloning an entire optimizer databank when a small retest gallery
     * needs metrics for just a few strategies.
     */
    public synchronized List<CombinedPass> getDatabankMatches(String name,
                                                               Collection<CombinedPass> references) {
        String storedName = findStoredName(name);
        if (storedName == null || references == null || references.isEmpty()) return new ArrayList<>();

        java.util.Set<String> identities = new java.util.HashSet<>();
        java.util.Set<Integer> passNumbers = new java.util.HashSet<>();
        for (CombinedPass reference : references) {
            if (reference == null) continue;
            identities.add(passIdentity(reference));
            passNumbers.add(reference.getPassNumber());
        }

        List<CombinedPass> matches = new ArrayList<>();
        for (CombinedPass candidate : databanks.get(storedName)) {
            if (candidate == null || candidate.getBacktestPass() == null) continue;
            if (identities.contains(passIdentity(candidate))
                    || passNumbers.contains(candidate.getPassNumber())) {
                matches.add(candidate.copy());
            }
        }
        return matches;
    }

    public synchronized void setDatabankContent(String name, List<CombinedPass> passes) {
        databanks.put(ensureDatabank(name), copyValidPasses(passes));
    }

    public synchronized void addPassesToDatabank(String name, List<CombinedPass> passes) {
        if (passes == null || passes.isEmpty()) return;
        String storedName = ensureDatabank(name);
        databanks.put(storedName, mergeByIdentity(databanks.get(storedName), passes));
    }

    public synchronized void removePassesFromDatabank(String name, Collection<CombinedPass> passes) {
        if (passes == null || passes.isEmpty()) return;
        String storedName = findStoredName(name);
        if (storedName == null) return;
        List<CombinedPass> remaining = new ArrayList<>(databanks.get(storedName));
        for (CombinedPass pass : passes) {
            String identity = passIdentity(pass);
            remaining.removeIf(candidate -> passIdentity(candidate).equals(identity));
        }
        databanks.put(storedName, remaining);
    }

    public synchronized void clearDatabank(String name) {
        String storedName = findStoredName(name);
        if (storedName != null) databanks.get(storedName).clear();
    }

    public synchronized void clearAll() {
        for (List<CombinedPass> list : databanks.values()) {
            list.clear();
        }
    }

    /**
     * Executes Databank transfer & filter condition evaluation for a given task.
     */
    public synchronized List<CombinedPass> processTaskDatabanks(WorkflowTask task, List<CombinedPass> inputPasses) {
        if (task == null) return copyValidPasses(inputPasses);

        String sourceName = ensureDatabank(task.getSourceDatabank());
        String targetName = ensureDatabank(task.getTargetDatabank());

        // Explicit task output always wins, including an explicit empty result.
        // A null input is the only signal to read the source databank directly.
        List<CombinedPass> sourceSnapshot = copyValidPasses(databanks.get(sourceName));
        List<CombinedPass> candidates = inputPasses != null
                ? copyValidPasses(inputPasses) : sourceSnapshot;

        logger.info("DATABANK ROUTING START: Task '{}' ({}) | Source: '{}' ({} passes) --> Target: '{}'",
            task.getName(), task.getType(), sourceName, candidates.size(), targetName);

        List<CombinedPass> filteredOutput = filterPasses(task, candidates);

        // A separate target is a copy. With an in-place route, deleteFailed
        // controls whether rejected strategies are removed or merely retained.
        if (!sourceName.equalsIgnoreCase(targetName) || task.isDeleteFailed()) {
            databanks.put(targetName, copyValidPasses(filteredOutput));
        } else {
            databanks.put(targetName, mergeByIdentity(sourceSnapshot, filteredOutput));
        }

        logger.info("DATABANK ROUTING SUCCESS: Task '{}' routed {} / {} passes into Databank '{}'.",
            task.getName(), filteredOutput.size(), candidates.size(), targetName);

        return copyValidPasses(filteredOutput);
    }

    /** Applies only the task's configured conditions without modifying a databank. */
    public synchronized List<CombinedPass> filterPasses(WorkflowTask task, List<CombinedPass> passes) {
        List<CombinedPass> candidates = copyValidPasses(passes);
        if (task == null || task.getFilterConditions().isEmpty()) return candidates;

        List<FilterCondition> conditions = new ArrayList<>(task.getFilterConditions());
        List<CombinedPass> filtered = new ArrayList<>();
        for (CombinedPass pass : candidates) {
            boolean passAll = true;
            for (FilterCondition condition : conditions) {
                if (condition == null || !condition.evaluate(pass)) {
                    passAll = false;
                    break;
                }
            }
            if (passAll) filtered.add(pass);
        }
        return filtered;
    }

    private void resetToDefaults() {
        databanks.clear();
        databanks.put(RESULTS, new ArrayList<>());
        databanks.put(EXISTING_PORTFOLIO, new ArrayList<>());
        databanks.put(FINAL, new ArrayList<>());
    }

    private String ensureDatabank(String name) {
        String cleanName = canonicalName(name);
        if (cleanName == null) cleanName = RESULTS;
        String storedName = findStoredName(cleanName);
        if (storedName != null) return storedName;
        databanks.put(cleanName, new ArrayList<>());
        return cleanName;
    }

    private String findStoredName(String name) {
        String cleanName = canonicalName(name);
        if (cleanName == null) return null;
        for (String storedName : databanks.keySet()) {
            if (storedName.equalsIgnoreCase(cleanName)) return storedName;
        }
        return null;
    }

    private static String canonicalName(String name) {
        if (name == null || name.trim().isEmpty()) return RESULTS;
        String cleanName = name.trim();
        if (RESULTS.equalsIgnoreCase(cleanName)) return RESULTS;
        if (EXISTING_PORTFOLIO.equalsIgnoreCase(cleanName)) return EXISTING_PORTFOLIO;
        if (FINAL.equalsIgnoreCase(cleanName)) return FINAL;
        return cleanName;
    }

    private static boolean isStandard(String name) {
        return RESULTS.equalsIgnoreCase(name)
                || EXISTING_PORTFOLIO.equalsIgnoreCase(name)
                || FINAL.equalsIgnoreCase(name);
    }

    private static List<CombinedPass> copyValidPasses(Collection<CombinedPass> passes) {
        Map<String, CombinedPass> unique = new LinkedHashMap<>();
        if (passes != null) {
            for (CombinedPass pass : passes) {
                if (pass != null && pass.getBacktestPass() != null) unique.put(passIdentity(pass), pass.copy());
            }
        }
        return new ArrayList<>(unique.values());
    }

    private static List<CombinedPass> mergeByIdentity(Collection<CombinedPass> existing,
                                                       Collection<CombinedPass> updates) {
        Map<String, CombinedPass> merged = new LinkedHashMap<>();
        if (existing != null) {
            for (CombinedPass pass : existing) {
                if (pass != null && pass.getBacktestPass() != null) merged.put(passIdentity(pass), pass.copy());
            }
        }
        if (updates != null) {
            for (CombinedPass pass : updates) {
                if (pass != null && pass.getBacktestPass() != null) merged.put(passIdentity(pass), pass.copy());
            }
        }
        return new ArrayList<>(merged.values());
    }

    private static String passIdentity(CombinedPass pass) {
        if (pass == null) return "<null>";
        return pass.getPassNumber() + "\u0000" + pass.getStrategyName();
    }
}
