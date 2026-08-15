package com.backtester.workflow;

import com.backtester.report.OptimizationResult.CombinedPass;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-project snapshot of B-cluster survival across databanks. Gson-friendly POJO.
 */
public class ClusterCensus {

    public enum ClusterStatus {
        LIVE,
        DEAD
    }

    public enum StageVerdict {
        IMPROVED,
        FLAT,
        DIED,
        PENDING
    }

    public static class ClusterStageSnapshot {
        private String databankName = "";
        private int liveCount;
        private int championPassNumber = -1;
        private StageVerdict verdict = StageVerdict.PENDING;

        public ClusterStageSnapshot() {
        }

        public String getDatabankName() {
            return databankName != null ? databankName : "";
        }

        public void setDatabankName(String databankName) {
            this.databankName = databankName != null ? databankName : "";
        }

        public int getLiveCount() {
            return liveCount;
        }

        public void setLiveCount(int liveCount) {
            this.liveCount = liveCount;
        }

        public int getChampionPassNumber() {
            return championPassNumber;
        }

        public void setChampionPassNumber(int championPassNumber) {
            this.championPassNumber = championPassNumber;
        }

        public StageVerdict getVerdict() {
            return verdict != null ? verdict : StageVerdict.PENDING;
        }

        public void setVerdict(StageVerdict verdict) {
            this.verdict = verdict != null ? verdict : StageVerdict.PENDING;
        }

        public ClusterStageSnapshot copy() {
            ClusterStageSnapshot copy = new ClusterStageSnapshot();
            copy.databankName = databankName;
            copy.liveCount = liveCount;
            copy.championPassNumber = championPassNumber;
            copy.verdict = verdict;
            return copy;
        }
    }

    public static class ClusterLine {
        private String clusterId = "";
        private String label = "";
        private ClusterStatus status = ClusterStatus.LIVE;
        private String diedAtStage;
        private double lastReferenceRatio = Double.NaN;
        private double lastReferenceProfit = Double.NaN;
        private List<ClusterStageSnapshot> perStage = new ArrayList<>();

        public ClusterLine() {
        }

        public String getClusterId() {
            return clusterId != null ? clusterId : "";
        }

        public void setClusterId(String clusterId) {
            this.clusterId = clusterId != null ? clusterId : "";
        }

        public String getLabel() {
            return label != null ? label : "";
        }

        public void setLabel(String label) {
            this.label = label != null ? label : "";
        }

        public ClusterStatus getStatus() {
            return status != null ? status : ClusterStatus.LIVE;
        }

        public void setStatus(ClusterStatus status) {
            this.status = status != null ? status : ClusterStatus.LIVE;
        }

        public String getDiedAtStage() {
            return diedAtStage;
        }

        public void setDiedAtStage(String diedAtStage) {
            this.diedAtStage = diedAtStage;
        }

        public double getLastReferenceRatio() {
            return lastReferenceRatio;
        }

        public void setLastReferenceRatio(double lastReferenceRatio) {
            this.lastReferenceRatio = lastReferenceRatio;
        }

        public double getLastReferenceProfit() {
            return lastReferenceProfit;
        }

        public void setLastReferenceProfit(double lastReferenceProfit) {
            this.lastReferenceProfit = lastReferenceProfit;
        }

        public List<ClusterStageSnapshot> getPerStage() {
            if (perStage == null) {
                perStage = new ArrayList<>();
            }
            return perStage;
        }

        public void setPerStage(List<ClusterStageSnapshot> perStage) {
            this.perStage = perStage != null ? new ArrayList<>(perStage) : new ArrayList<>();
        }

        public ClusterLine copy() {
            ClusterLine copy = new ClusterLine();
            copy.clusterId = clusterId;
            copy.label = label;
            copy.status = status;
            copy.diedAtStage = diedAtStage;
            copy.lastReferenceRatio = lastReferenceRatio;
            copy.lastReferenceProfit = lastReferenceProfit;
            List<ClusterStageSnapshot> stages = new ArrayList<>();
            for (ClusterStageSnapshot snapshot : getPerStage()) {
                if (snapshot != null) {
                    stages.add(snapshot.copy());
                }
            }
            copy.perStage = stages;
            return copy;
        }
    }

    private List<ClusterLine> clusters = new ArrayList<>();

    public ClusterCensus() {
    }

    public List<ClusterLine> getClusters() {
        if (clusters == null) {
            clusters = new ArrayList<>();
        }
        return clusters;
    }

    public void setClusters(List<ClusterLine> clusters) {
        this.clusters = clusters != null ? new ArrayList<>(clusters) : new ArrayList<>();
    }

    public ClusterCensus copy() {
        ClusterCensus copy = new ClusterCensus();
        List<ClusterLine> lines = new ArrayList<>();
        for (ClusterLine line : getClusters()) {
            if (line != null) {
                lines.add(line.copy());
            }
        }
        copy.clusters = lines;
        return copy;
    }

    public ClusterLine findLine(String clusterId) {
        String id = ClusterIdentity.normalize(clusterId);
        if (id == null) {
            return null;
        }
        for (ClusterLine line : getClusters()) {
            if (line != null && id.equals(ClusterIdentity.normalize(line.getClusterId()))) {
                return line;
            }
        }
        return null;
    }

    /**
     * Rebuilds live counts from the project's databanks. Previous labels are kept.
     */
    public static ClusterCensus rebuild(CustomProject project) {
        ClusterCensus previous = project != null ? project.getClusterCensus() : new ClusterCensus();
        ClusterCensus census = new ClusterCensus();
        if (project == null) {
            return census;
        }

        Map<String, List<CombinedPass>> databanks = project.getDatabanks();
        if (databanks == null) {
            databanks = Map.of();
        }

        Set<String> clusterIds = new LinkedHashSet<>();
        for (List<CombinedPass> passes : databanks.values()) {
            if (passes == null) {
                continue;
            }
            for (CombinedPass pass : passes) {
                String id = ClusterIdentity.normalize(pass);
                if (id != null) {
                    clusterIds.add(id);
                }
            }
        }
        List<String> orderedIds = new ArrayList<>(clusterIds);
        orderedIds.sort(ClusterCensus::compareClusterIds);

        List<String> stages = stageDatabanks(project, databanks);
        Map<String, String> stageLabels = stageDisplayNames(project);

        for (String clusterId : orderedIds) {
            ClusterLine line = new ClusterLine();
            line.setClusterId(clusterId);
            ClusterLine prior = previous.findLine(clusterId);
            if (prior != null) {
                line.setLabel(prior.getLabel());
                line.setLastReferenceRatio(prior.getLastReferenceRatio());
                line.setLastReferenceProfit(prior.getLastReferenceProfit());
            }

            Double previousChampionScore = null;
            boolean everLive = false;
            String diedAt = null;
            List<ClusterStageSnapshot> snapshots = new ArrayList<>();

            for (String databankName : stages) {
                List<CombinedPass> members = membersOf(databanks.get(databankName), clusterId);
                ClusterStageSnapshot snapshot = new ClusterStageSnapshot();
                snapshot.setDatabankName(databankName);
                snapshot.setLiveCount(members.size());
                CombinedPass champion = GuidedOptimizationService.selectBestPass(members).orElse(null);
                snapshot.setChampionPassNumber(champion != null ? champion.getPassNumber() : -1);

                boolean survivalEvidence = countsAsSurvivalEvidence(databankName, databanks, project);
                if (members.isEmpty()) {
                    if (survivalEvidence && everLive) {
                        snapshot.setVerdict(StageVerdict.DIED);
                        if (diedAt == null) {
                            diedAt = stageLabels.getOrDefault(databankName, databankName);
                        }
                    } else {
                        snapshot.setVerdict(StageVerdict.PENDING);
                    }
                } else if (isMeasurementDatabank(databankName, project)) {
                    snapshot.setVerdict(StageVerdict.FLAT);
                } else if (previousChampionScore == null) {
                    snapshot.setVerdict(StageVerdict.PENDING);
                    everLive = true;
                    previousChampionScore = champion != null ? champion.getScore() : null;
                } else {
                    double currentScore = champion != null ? champion.getScore() : Double.NEGATIVE_INFINITY;
                    snapshot.setVerdict(currentScore > previousChampionScore
                            ? StageVerdict.IMPROVED : StageVerdict.FLAT);
                    everLive = true;
                    previousChampionScore = currentScore;
                }
                snapshots.add(snapshot);
            }

            overlayReferenceVerdicts(snapshots, prior);
            line.setPerStage(snapshots);
            boolean latestAlive = presentInLastSurvivalDatabank(project, clusterId);
            if (latestAlive) {
                line.setStatus(ClusterStatus.LIVE);
                line.setDiedAtStage(null);
            } else if (prior != null && prior.getStatus() == ClusterStatus.DEAD) {
                line.setStatus(ClusterStatus.DEAD);
                line.setDiedAtStage(prior.getDiedAtStage() != null ? prior.getDiedAtStage() : diedAt);
            } else if (everLive) {
                line.setStatus(ClusterStatus.DEAD);
                line.setDiedAtStage(diedAt);
            } else {
                line.setStatus(ClusterStatus.LIVE);
                line.setDiedAtStage(null);
            }
            census.getClusters().add(line);
        }
        return census;
    }

    private static void overlayReferenceVerdicts(List<ClusterStageSnapshot> snapshots,
                                                 ClusterLine prior) {
        if (prior == null || snapshots == null) {
            return;
        }
        Map<String, ClusterStageSnapshot> previousByDatabank = new LinkedHashMap<>();
        for (ClusterStageSnapshot snapshot : prior.getPerStage()) {
            if (snapshot != null && snapshot.getDatabankName() != null && !snapshot.getDatabankName().isBlank()) {
                previousByDatabank.put(snapshot.getDatabankName(), snapshot);
            }
        }
        for (ClusterStageSnapshot snapshot : snapshots) {
            if (snapshot == null) {
                continue;
            }
            ClusterStageSnapshot previous = previousByDatabank.get(snapshot.getDatabankName());
            if (previous == null) {
                continue;
            }
            StageVerdict priorVerdict = previous.getVerdict();
            if (priorVerdict == StageVerdict.IMPROVED) {
                snapshot.setVerdict(priorVerdict);
            }
        }
    }

    private static int compareClusterIds(String a, String b) {
        return Integer.compare(clusterOrdinal(a), clusterOrdinal(b));
    }

    private static int clusterOrdinal(String id) {
        String normalized = ClusterIdentity.normalize(id);
        if (normalized == null) {
            return Integer.MAX_VALUE;
        }
        return Integer.parseInt(normalized.substring(1));
    }

    private static List<CombinedPass> membersOf(List<CombinedPass> passes, String clusterId) {
        List<CombinedPass> members = new ArrayList<>();
        if (passes == null) {
            return members;
        }
        for (CombinedPass pass : passes) {
            if (clusterId.equals(ClusterIdentity.normalize(pass))) {
                members.add(pass);
            }
        }
        return members;
    }

    private static List<String> stageDatabanks(CustomProject project,
                                               Map<String, List<CombinedPass>> databanks) {
        LinkedHashSet<String> stages = new LinkedHashSet<>();
        for (WorkflowTask task : project.getTasks()) {
            if (task == null) {
                continue;
            }
            addStage(stages, task.getSourceDatabank());
            addStage(stages, task.getTargetDatabank());
        }
        if (databanks != null) {
            for (Map.Entry<String, List<CombinedPass>> entry : databanks.entrySet()) {
                if (entry.getKey() != null && !entry.getKey().isBlank()
                        && containsAnyCluster(entry.getValue())) {
                    stages.add(entry.getKey());
                }
            }
        }
        return new ArrayList<>(stages);
    }

    /**
     * Last pick/raw/filter databank that actually contains clustered rows.
     * {@code *_master} measurement copies are ignored so an OHLC checkpoint
     * cannot declare a line dead, and empty later workflow tabs cannot either.
     */
    public static boolean presentInLastSurvivalDatabank(CustomProject project, String clusterId) {
        String id = ClusterIdentity.normalize(clusterId);
        if (project == null || id == null) {
            return false;
        }
        Map<String, List<CombinedPass>> databanks = project.getDatabanks();
        if (databanks == null) {
            return false;
        }
        String last = null;
        for (String name : stageDatabanks(project, databanks)) {
            if (countsAsSurvivalEvidence(name, databanks, project)) {
                last = name;
            }
        }
        if (last == null) {
            return false;
        }
        return !membersOf(databanks.get(last), id).isEmpty();
    }

    public static boolean isMasterReferenceDeath(ClusterLine line) {
        if (line == null || line.getDiedAtStage() == null) {
            return false;
        }
        String diedAt = line.getDiedAtStage().toLowerCase(java.util.Locale.ROOT);
        return diedAt.contains("master-referenz") || diedAt.contains("master_reference");
    }

    private static boolean countsAsSurvivalEvidence(String databankName,
                                                    Map<String, List<CombinedPass>> databanks,
                                                    CustomProject project) {
        return !isMeasurementDatabank(databankName, project)
                && containsAnyCluster(databanks != null ? databanks.get(databankName) : null);
    }

    private static boolean isMeasurementDatabank(String databankName, CustomProject project) {
        if (databankName == null || databankName.isBlank()) {
            return false;
        }
        String name = databankName.trim();
        if (name.toLowerCase(java.util.Locale.ROOT).endsWith("_master")) {
            return true;
        }
        if (project == null || project.getTasks() == null) {
            return false;
        }
        for (WorkflowTask task : project.getTasks()) {
            if (task == null || task.getType() != WorkflowTask.TaskType.MASTER_REFERENCE) {
                continue;
            }
            if (name.equalsIgnoreCase(task.getTargetDatabank())
                    || name.equalsIgnoreCase(task.getSourceDatabank() + "_master")) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAnyCluster(List<CombinedPass> passes) {
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

    private static void addStage(Set<String> stages, String databankName) {
        if (databankName != null && !databankName.isBlank()) {
            stages.add(databankName.trim());
        }
    }

    private static Map<String, String> stageDisplayNames(CustomProject project) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (WorkflowTask task : project.getTasks()) {
            if (task == null || task.getTargetDatabank() == null || task.getTargetDatabank().isBlank()) {
                continue;
            }
            String databank = task.getTargetDatabank().trim();
            String taskName = task.getName() != null && !task.getName().isBlank()
                    ? task.getName() : databank;
            labels.putIfAbsent(databank, taskName);
        }
        return labels;
    }
}
