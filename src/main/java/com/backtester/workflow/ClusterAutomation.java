package com.backtester.workflow;

import com.backtester.report.OptimizationResult.CombinedPass;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Improve-or-die helpers for B-cluster Automatik. Unclustered projects stay on
 * the single global champion path.
 */
public final class ClusterAutomation {

    private ClusterAutomation() {
    }

    public static boolean hasAnyClusterId(List<CombinedPass> passes) {
        if (passes == null) {
            return false;
        }
        for (CombinedPass pass : passes) {
            if (ClusterIdentity.hasId(pass)) {
                return true;
            }
        }
        return false;
    }

    /**
     * One score champion per {@code clusterId}. Passes without an id are omitted;
     * callers keep {@code championPasses} for unclustered projects.
     */
    public static List<CombinedPass> championsByCluster(List<CombinedPass> inputPasses) {
        if (inputPasses == null || inputPasses.isEmpty()) {
            return List.of();
        }
        Map<String, List<CombinedPass>> groups = new LinkedHashMap<>();
        for (CombinedPass pass : inputPasses) {
            String id = ClusterIdentity.normalize(pass);
            if (id == null) {
                continue;
            }
            groups.computeIfAbsent(id, key -> new ArrayList<>()).add(pass);
        }
        List<CombinedPass> champions = new ArrayList<>();
        for (List<CombinedPass> group : groups.values()) {
            GuidedOptimizationService.selectBestPass(group).ifPresent(champions::add);
        }
        return champions;
    }

    public static List<CombinedPass> liveChampions(CustomProject project, List<CombinedPass> inputPasses) {
        List<CombinedPass> champions = championsByCluster(inputPasses);
        if (champions.isEmpty()) {
            return List.of();
        }
        ClusterCensus census = project != null ? project.getClusterCensus() : null;
        if (census == null || census.getClusters().isEmpty()) {
            return champions;
        }
        List<CombinedPass> live = new ArrayList<>();
        for (CombinedPass champion : champions) {
            String id = ClusterIdentity.normalize(champion);
            ClusterCensus.ClusterLine line = census.findLine(id);
            if (shouldSkipDeadChampion(project, line, id)) {
                continue;
            }
            live.add(champion);
        }
        return live;
    }

    /**
     * Census DEAD is ignored when the line only "died" at a measurement checkpoint
     * or is still present in the last populated pick/raw databank.
     */
    static boolean shouldSkipDeadChampion(CustomProject project,
                                          ClusterCensus.ClusterLine line,
                                          String clusterId) {
        if (line == null || line.getStatus() != ClusterCensus.ClusterStatus.DEAD) {
            return false;
        }
        if (ClusterCensus.isMasterReferenceDeath(line)) {
            return false;
        }
        return !ClusterCensus.presentInLastSurvivalDatabank(project, clusterId);
    }

    public static boolean usesClusteredAutomatik(CustomProject project, List<CombinedPass> inputPasses) {
        return project != null && hasAnyClusterId(inputPasses);
    }

    /**
     * Two or more live lines: one sequential optimizer run each. One live line
     * keeps today's single Automatik adopt. Zero live lines must pause instead.
     */
    public static boolean shouldRunSequentialClusterOptimizers(CustomProject project,
                                                               List<CombinedPass> sourcePasses) {
        return project != null
                && project.isAutomaticModeEnabled()
                && liveChampions(project, sourcePasses).size() >= 2;
    }

    public static boolean hasZeroLiveClusters(CustomProject project, List<CombinedPass> sourcePasses) {
        return usesClusteredAutomatik(project, sourcePasses)
                && liveChampions(project, sourcePasses).isEmpty();
    }

    public static String zeroLiveClustersMessage(WorkflowTask task) {
        String name = task != null && task.getName() != null ? task.getName() : "Task";
        return "Automatik angehalten: Keine lebende Cluster-Linie vor '" + name
                + "'. Tote Linien werden nicht als Score-Sieger übernommen.";
    }

    /**
     * Improve-or-die vs that line's last confirmed ratio, or vs the project master
     * when the line has no floor yet.
     */
    public static boolean confirmsLineImprovement(MasterStrategyEntry entry,
                                                  ClusterCensus.ClusterLine line,
                                                  boolean projectHasProvenMaster) {
        if (line != null && Double.isFinite(line.getLastReferenceRatio())) {
            if (entry == null || !entry.isBacktestSucceeded()) {
                return false;
            }
            if (!Double.isFinite(entry.getReturnToDrawdown())) {
                return false;
            }
            return entry.getReturnToDrawdown() > line.getLastReferenceRatio();
        }
        return confirmsImprovement(entry, projectHasProvenMaster);
    }

    /** Same gate as {@code ProjectWorkflowEditorView.confirmsImprovement}. */
    public static boolean confirmsImprovement(MasterStrategyEntry entry, boolean hasProvenMaster) {
        if (entry == null || !entry.isBacktestSucceeded()) {
            return false;
        }
        if (!Double.isFinite(entry.getReturnToDrawdown())) {
            return false;
        }
        if (entry.getVerdict() == MasterStrategyEntry.Verdict.BESSER) {
            return true;
        }
        return !hasProvenMaster
                && entry.getVerdict() == MasterStrategyEntry.Verdict.UNBEKANNT;
    }

    public static void markMeasured(ClusterCensus census,
                                    String clusterId,
                                    String databankName,
                                    CombinedPass champion,
                                    MasterStrategyEntry entry) {
        if (census == null) {
            return;
        }
        ClusterCensus.ClusterLine line = ensureLine(census, clusterId);
        line.setStatus(ClusterCensus.ClusterStatus.LIVE);
        line.setDiedAtStage(null);
        if (entry != null && entry.isBacktestSucceeded()
                && Double.isFinite(entry.getReturnToDrawdown())) {
            line.setLastReferenceRatio(entry.getReturnToDrawdown());
            line.setLastReferenceProfit(entry.getProfit());
        }
        ClusterCensus.ClusterStageSnapshot snapshot = snapshotFor(line, databankName);
        snapshot.setLiveCount(Math.max(1, snapshot.getLiveCount()));
        snapshot.setChampionPassNumber(champion != null ? champion.getPassNumber() : snapshot.getChampionPassNumber());
        if (snapshot.getVerdict() != ClusterCensus.StageVerdict.IMPROVED) {
            snapshot.setVerdict(ClusterCensus.StageVerdict.FLAT);
        }
    }

    /**
     * After a later-stage optimizer: keep a line only when its new champion score
     * beats that line's previous champion. Unclustered callers never use this.
     */
    public static List<CombinedPass> applyOptimizerImproveOrDie(ClusterCensus census,
                                                                String stageName,
                                                                String databankName,
                                                                List<CombinedPass> previousLive,
                                                                List<CombinedPass> produced) {
        List<CombinedPass> previous = previousLive != null ? previousLive : List.of();
        List<CombinedPass> output = produced != null ? produced : List.of();
        Map<String, CombinedPass> newByCluster = new LinkedHashMap<>();
        for (CombinedPass champion : championsByCluster(output)) {
            String id = ClusterIdentity.normalize(champion);
            if (id != null) {
                newByCluster.put(id, champion);
            }
        }
        List<CombinedPass> kept = new ArrayList<>();
        for (CombinedPass prior : previous) {
            String id = ClusterIdentity.normalize(prior);
            if (id == null) {
                continue;
            }
            CombinedPass next = newByCluster.get(id);
            if (next != null && Double.isFinite(next.getScore()) && Double.isFinite(prior.getScore())
                    && next.getScore() >= prior.getScore()) {
                markImproved(census, id, databankName, next, null);
            } else {
                markDied(census, id, stageName, databankName);
            }
        }
        for (CombinedPass pass : output) {
            String id = ClusterIdentity.normalize(pass);
            if (id == null) {
                kept.add(pass);
                continue;
            }
            ClusterCensus.ClusterLine line = census != null ? census.findLine(id) : null;
            if (line != null && line.getStatus() == ClusterCensus.ClusterStatus.DEAD) {
                continue;
            }
            kept.add(pass);
        }
        return kept;
    }

    public static void markImproved(ClusterCensus census,
                                    String clusterId,
                                    String databankName,
                                    CombinedPass champion,
                                    MasterStrategyEntry entry) {
        if (census == null) {
            return;
        }
        ClusterCensus.ClusterLine line = ensureLine(census, clusterId);
        line.setStatus(ClusterCensus.ClusterStatus.LIVE);
        line.setDiedAtStage(null);
        if (entry != null) {
            line.setLastReferenceRatio(entry.getReturnToDrawdown());
            line.setLastReferenceProfit(entry.getProfit());
        }
        ClusterCensus.ClusterStageSnapshot snapshot = snapshotFor(line, databankName);
        snapshot.setLiveCount(Math.max(1, snapshot.getLiveCount()));
        snapshot.setChampionPassNumber(champion != null ? champion.getPassNumber() : snapshot.getChampionPassNumber());
        snapshot.setVerdict(ClusterCensus.StageVerdict.IMPROVED);
    }

    public static void markDied(ClusterCensus census,
                                String clusterId,
                                String stageName,
                                String databankName) {
        if (census == null) {
            return;
        }
        ClusterCensus.ClusterLine line = ensureLine(census, clusterId);
        line.setStatus(ClusterCensus.ClusterStatus.DEAD);
        line.setDiedAtStage(stageName);
        ClusterCensus.ClusterStageSnapshot snapshot = snapshotFor(line, databankName);
        snapshot.setLiveCount(0);
        snapshot.setChampionPassNumber(-1);
        snapshot.setVerdict(ClusterCensus.StageVerdict.DIED);
    }

    public static void stampFixedClusterId(List<CombinedPass> passes, String clusterId) {
        String id = ClusterIdentity.normalize(clusterId);
        if (id == null || passes == null) {
            return;
        }
        for (CombinedPass pass : passes) {
            if (pass != null) {
                pass.setClusterId(id);
            }
        }
    }

    private static ClusterCensus.ClusterLine ensureLine(ClusterCensus census, String clusterId) {
        String id = ClusterIdentity.normalize(clusterId);
        ClusterCensus.ClusterLine line = census.findLine(id);
        if (line == null) {
            line = new ClusterCensus.ClusterLine();
            line.setClusterId(id != null ? id : "");
            census.getClusters().add(line);
        }
        return line;
    }

    private static ClusterCensus.ClusterStageSnapshot snapshotFor(ClusterCensus.ClusterLine line,
                                                                  String databankName) {
        String name = databankName != null ? databankName.trim() : "";
        for (ClusterCensus.ClusterStageSnapshot snapshot : line.getPerStage()) {
            if (snapshot != null && name.equals(snapshot.getDatabankName())) {
                return snapshot;
            }
        }
        ClusterCensus.ClusterStageSnapshot snapshot = new ClusterCensus.ClusterStageSnapshot();
        snapshot.setDatabankName(name);
        line.getPerStage().add(snapshot);
        return snapshot;
    }
}
