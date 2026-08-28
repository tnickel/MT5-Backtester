package com.backtester.workflow;

import com.backtester.report.OptimizationDateRangeResolver;
import com.backtester.report.OptimizationResult;
import com.backtester.report.OptimizationResult.CombinedPass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
        repairPersistedOptimizerDateRanges(project);
        repairPersistedDerivedDateRanges(project);
        // Seed tab-keyed archive from legacy longtermPass slots when missing.
        StrategyBacktestArchiveStore.migrateFromLongtermPasses(project);
        rebuildCensus(project);
    }

    /**
     * Repairs legacy/imported optimizer passes whose MT5 report title stored the
     * complete range on both the backtest and forward legs. Fresh optimizer runs
     * already do this in {@code OptimizationRunner}; persisted projects need the
     * same normalization when their databanks are loaded.
     */
    private void repairPersistedOptimizerDateRanges(CustomProject project) {
        if (project == null || project.getTasks() == null) return;
        for (WorkflowTask task : project.getTasks()) {
            if (task == null || task.getType() != WorkflowTask.TaskType.OPTIMIZER) continue;
            String storedName = findStoredName(task.getTargetDatabank());
            if (storedName == null) continue;

            LocalDate from = parseDate(task.getStartDate());
            LocalDate to = parseDate(task.getEndDate());
            if (from == null || to == null || !from.isBefore(to)) continue;

            LocalDate configuredForwardDate = null;
            if (task.getOptimizerForwardMode() == 4) {
                configuredForwardDate = parseDate(task.getOptimizerForwardDate());
            }
            OptimizationDateRangeResolver.apply(
                    databanks.get(storedName),
                    from,
                    to,
                    task.getOptimizerForwardMode(),
                    configuredForwardDate);
        }
    }

    /**
     * Filter and diversity tasks copy the optimizer passes into new databanks.
     * Older saved copies still contain MT5's full-range labels, even when the
     * optimizer source has already been repaired. Propagate the source leg
     * ranges through those pass-through tasks without touching retest outputs,
     * whose dates intentionally describe a new test run.
     */
    private void repairPersistedDerivedDateRanges(CustomProject project) {
        if (project == null || project.getTasks() == null) return;
        for (WorkflowTask task : project.getTasks()) {
            if (task == null || !preservesPassDateRanges(task.getType())) continue;
            String sourceName = findStoredName(task.getSourceDatabank());
            String targetName = findStoredName(task.getTargetDatabank());
            if (sourceName == null || targetName == null
                    || sourceName.equalsIgnoreCase(targetName)) continue;
            copyDateRanges(databanks.get(sourceName), databanks.get(targetName));
        }
    }

    private static boolean preservesPassDateRanges(WorkflowTask.TaskType type) {
        return type != null
                && type != WorkflowTask.TaskType.OPTIMIZER
                && type != WorkflowTask.TaskType.RETESTER;
    }

    private static void copyDateRanges(List<CombinedPass> source,
                                       List<CombinedPass> target) {
        if (source == null || target == null || source.isEmpty() || target.isEmpty()) return;
        Map<String, CombinedPass> byIdentity = new LinkedHashMap<>();
        Map<String, CombinedPass> byPassNumber = new LinkedHashMap<>();
        for (CombinedPass pass : source) {
            if (pass == null || pass.getBacktestPass() == null) continue;
            byIdentity.put(passIdentity(pass), pass);
            byPassNumber.putIfAbsent(passNumberKey(pass), pass);
        }
        for (CombinedPass pass : target) {
            if (pass == null || pass.getBacktestPass() == null) continue;
            CombinedPass sourcePass = byIdentity.get(passIdentity(pass));
            if (sourcePass == null) sourcePass = byPassNumber.get(passNumberKey(pass));
            if (sourcePass == null) continue;
            copyDateRange(sourcePass.getBacktestPass(), pass.getBacktestPass());
            if (sourcePass.getForwardPass() != null && pass.getForwardPass() != null) {
                copyDateRange(sourcePass.getForwardPass(), pass.getForwardPass());
            }
        }
    }

    private static void copyDateRange(OptimizationResult.Pass source,
                                      OptimizationResult.Pass target) {
        if (source == null || target == null) return;
        target.setFromDate(source.getFromDate());
        target.setToDate(source.getToDate());
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim().replace('.', '-'));
        } catch (RuntimeException ignored) {
            return null;
        }
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
            rebuildCensus(project);
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
        java.util.Set<String> passKeys = new java.util.HashSet<>();
        for (CombinedPass reference : references) {
            if (reference == null) continue;
            identities.add(passIdentity(reference));
            passKeys.add(passNumberKey(reference));
        }

        List<CombinedPass> matches = new ArrayList<>();
        for (CombinedPass candidate : databanks.get(storedName)) {
            if (candidate == null || candidate.getBacktestPass() == null) continue;
            if (identities.contains(passIdentity(candidate))
                    || passKeys.contains(passNumberKey(candidate))) {
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

        String rejectionNote = FilterRejectionReport.describeDroppedLeader(
                task, candidates, filteredOutput);
        if (task.getType() == WorkflowTask.TaskType.PRE_FILTER) {
            List<CombinedPass> withFallback = applyPreFilterEmptyFallback(candidates, filteredOutput);
            if (shouldHaltChainAfterPreFilter(task, withFallback) && !candidates.isEmpty()) {
                String halt = " Die Kette wird angehalten, statt mit dem Score-Besten weiterzulaufen.";
                rejectionNote = rejectionNote.isBlank()
                        ? "Kein Pass hat den Qualitätsfilter überstanden." + halt
                        : rejectionNote + halt;
            }
            filteredOutput = withFallback;
            // Quality banks (e.g. g01_grid_quality) must keep every survivor so
            // the following form-diversity task can stamp B1–B10. Census picks
            // still cap and assign missing ids here.
            if (isCensusPickDatabank(targetName)) {
                filteredOutput = ClusterIdentity.stampUnassignedByScore(filteredOutput);
            }
        } else if (task.getType() == WorkflowTask.TaskType.DIVERSITY_FILTER
                && task.isDiversityStampClusterIds()
                && task.getDiversityMaxStrategies() <= ClusterIdentity.MAX_CLUSTERS) {
            filteredOutput = ClusterIdentity.stampInOrder(filteredOutput);
        }
        task.setFilterRejectionNote(rejectionNote);
        if (!rejectionNote.isBlank()) {
            logger.warn("DATABANK FILTER: {}", rejectionNote);
        }

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

    /**
     * Unclustered empty PRE_FILTER stays empty — promoting the score leader
     * created zombie pipelines that burned hours of MT5 without an adoptable
     * master. Clustered rows never receive another cluster's champion: a
     * non-empty filter that emptied one cluster stays empty for that cluster;
     * a fully empty clustered filter may keep each cluster's own leader.
     */
    static List<CombinedPass> applyPreFilterEmptyFallback(List<CombinedPass> candidates,
                                                          List<CombinedPass> accepted) {
        if (accepted != null && !accepted.isEmpty()) {
            return new ArrayList<>(accepted);
        }
        if (candidates == null || candidates.isEmpty()) {
            return new ArrayList<>();
        }
        boolean anyClustered = false;
        Map<String, List<CombinedPass>> byCluster = new LinkedHashMap<>();
        for (CombinedPass candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            String id = ClusterIdentity.normalize(candidate);
            if (id == null) {
                continue;
            }
            anyClustered = true;
            byCluster.computeIfAbsent(id, key -> new ArrayList<>()).add(candidate);
        }
        if (!anyClustered) {
            return new ArrayList<>();
        }
        List<CombinedPass> kept = new ArrayList<>();
        for (List<CombinedPass> group : byCluster.values()) {
            GuidedOptimizationService.selectBestPass(group).ifPresent(kept::add);
        }
        return kept;
    }

    /**
     * True when a PRE_FILTER wrote nothing. The pipeline must pause here:
     * the next optimizer does not require input and would otherwise keep
     * searching around a missing basis.
     */
    public static boolean shouldHaltChainAfterPreFilter(WorkflowTask task, List<CombinedPass> output) {
        return task != null
                && task.getType() == WorkflowTask.TaskType.PRE_FILTER
                && (output == null || output.isEmpty());
    }

    public static String emptyPreFilterHaltMessage(WorkflowTask task) {
        String name = task != null && task.getName() != null ? task.getName() : "Qualitätsfilter";
        return "Automatik angehalten: Filter '" + name
                + "' hat 0 Überlebende. Die Kette läuft nicht mit dem Score-Besten weiter.";
    }

    /**
     * Rebuilds {@link ClusterCensus} from the project's databank snapshot.
     */
    public static void rebuildCensus(CustomProject project) {
        if (project == null) {
            return;
        }
        project.setClusterCensus(ClusterCensus.rebuild(project));
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

    private static boolean isCensusPickDatabank(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).endsWith("_pick");
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

    /**
     * Dedup key for a databank row. Cluster id is included so sequential
     * optimizer runs (each restarting MT5 pass numbers at 1) do not collapse
     * B1#5 onto B7#5. Unclustered rows keep the legacy pass+name key.
     */
    public static String passIdentity(CombinedPass pass) {
        if (pass == null) return "<null>";
        String cluster = ClusterIdentity.normalize(pass);
        return pass.getPassNumber() + "\u0000" + pass.getStrategyName()
                + "\u0000" + (cluster != null ? cluster : "");
    }

    /** Gallery rename fallback: same pass number only inside the same cluster. */
    private static String passNumberKey(CombinedPass pass) {
        String cluster = ClusterIdentity.normalize(pass);
        return pass.getPassNumber() + "\u0000" + (cluster != null ? cluster : "");
    }
}
