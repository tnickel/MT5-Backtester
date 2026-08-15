package com.backtester.workflow;

import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.workflow.ClusterCensus.ClusterLine;
import com.backtester.workflow.ClusterCensus.ClusterStageSnapshot;
import com.backtester.workflow.ClusterCensus.ClusterStatus;
import com.backtester.workflow.ClusterCensus.StageVerdict;
import com.backtester.workflow.WorkflowHandoffAuditService.FlowNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Pure-Java stages × clusters grid for Show Flow. Empty census → no tree (linear fallback).
 */
public final class ClusterFlowTreeModel {

    private final List<Column> columns;
    private final List<Row> rows;

    private ClusterFlowTreeModel(List<Column> columns, List<Row> rows) {
        this.columns = columns;
        this.rows = rows;
    }

    public static ClusterFlowTreeModel from(CustomProject project, List<FlowNode> nodes) {
        ClusterCensus census = project != null ? project.getClusterCensus() : null;
        List<ClusterLine> lines = census != null ? census.getClusters() : List.of();
        if (lines == null || lines.isEmpty()) {
            return new ClusterFlowTreeModel(List.of(), List.of());
        }

        List<ClusterLine> ordered = new ArrayList<>();
        for (ClusterLine line : lines) {
            if (line != null && ClusterIdentity.normalize(line.getClusterId()) != null) {
                ordered.add(line);
            }
        }
        ordered.sort((a, b) -> Integer.compare(
                clusterOrdinal(a.getClusterId()), clusterOrdinal(b.getClusterId())));

        List<Column> columns = new ArrayList<>();
        for (ClusterLine line : ordered) {
            columns.add(new Column(
                    ClusterIdentity.normalize(line.getClusterId()),
                    line.getLabel(),
                    line.getStatus(),
                    line.getDiedAtStage()));
        }

        List<Row> rows = new ArrayList<>();
        if (nodes != null) {
            for (FlowNode node : nodes) {
                if (node == null) {
                    continue;
                }
                List<Cell> cells = new ArrayList<>();
                for (ClusterLine line : ordered) {
                    cells.add(cellFor(node, line));
                }
                rows.add(new Row(
                        node.getWorkflowNumber(),
                        node.getTaskName(),
                        shortStageKey(node),
                        node.getSourceDatabank(),
                        node.getTargetDatabank(),
                        cells));
            }
        }
        return new ClusterFlowTreeModel(
                Collections.unmodifiableList(columns),
                Collections.unmodifiableList(rows));
    }

    public boolean hasTree() {
        return !columns.isEmpty();
    }

    public List<Column> getColumns() {
        return columns;
    }

    public List<Row> getRows() {
        return rows;
    }

    /**
     * Trunk/stage click keeps the parameter handoff board; a cluster cell opens the equity gallery.
     */
    public enum ClickAction {
        HANDOFF,
        GALLERY
    }

    public static ClickAction resolveClick(boolean clusterCell) {
        return clusterCell ? ClickAction.GALLERY : ClickAction.HANDOFF;
    }

    /**
     * Pick/target databank for a stage row — never Optimizer {@code _raw}.
     */
    public static String pickDatabankName(Row row) {
        if (row == null) {
            return "";
        }
        String target = row.getTargetDatabank();
        if (!isRawDatabank(target) && !target.isBlank()) {
            return target;
        }
        String source = row.getSourceDatabank();
        if (!isRawDatabank(source) && !source.isBlank()) {
            return source;
        }
        return "";
    }

    public static boolean isRawDatabank(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).endsWith("_raw");
    }

    /**
     * Non-mutating filter: returns only passes whose {@code clusterId} matches.
     * Invalid/blank clusterId returns an empty list (do not dump the whole bank).
     */
    public static List<CombinedPass> filterByCluster(List<CombinedPass> passes, String clusterId) {
        String id = ClusterIdentity.normalize(clusterId);
        if (id == null) {
            return List.of();
        }
        if (passes == null || passes.isEmpty()) {
            return List.of();
        }
        List<CombinedPass> out = new ArrayList<>();
        for (CombinedPass pass : passes) {
            if (id.equals(ClusterIdentity.normalize(pass))) {
                out.add(pass);
            }
        }
        return out;
    }

    public Cell cell(int rowIndex, String clusterId) {
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            return null;
        }
        String id = ClusterIdentity.normalize(clusterId);
        for (Cell cell : rows.get(rowIndex).getCells()) {
            if (id != null && id.equals(cell.getClusterId())) {
                return cell;
            }
        }
        return null;
    }

    private static Cell cellFor(FlowNode node, ClusterLine line) {
        String clusterId = ClusterIdentity.normalize(line.getClusterId());
        ClusterStageSnapshot snapshot = findSnapshot(line, node);
        boolean deadLine = line.getStatus() == ClusterStatus.DEAD;
        if (snapshot == null) {
            return new Cell(clusterId, 0, StageVerdict.PENDING, -1, deadLine, shortStageKey(node));
        }
        return new Cell(
                clusterId,
                snapshot.getLiveCount(),
                snapshot.getVerdict(),
                snapshot.getChampionPassNumber(),
                deadLine,
                shortStageKey(node, snapshot.getDatabankName()));
    }

    private static ClusterStageSnapshot findSnapshot(ClusterLine line, FlowNode node) {
        ClusterStageSnapshot byTarget = matchDatabank(line, node.getTargetDatabank());
        if (byTarget != null) {
            return byTarget;
        }
        return matchDatabank(line, node.getSourceDatabank());
    }

    private static ClusterStageSnapshot matchDatabank(ClusterLine line, String databankName) {
        if (line == null || databankName == null || databankName.isBlank()) {
            return null;
        }
        String needle = databankName.trim();
        for (ClusterStageSnapshot snapshot : line.getPerStage()) {
            if (snapshot != null && needle.equalsIgnoreCase(snapshot.getDatabankName())) {
                return snapshot;
            }
        }
        return null;
    }

    static String shortStageKey(FlowNode node) {
        if (node == null) {
            return "";
        }
        return shortStageKey(node, node.getTargetDatabank());
    }

    static String shortStageKey(FlowNode node, String databankName) {
        String fromDb = leadingStageToken(databankName);
        if (!fromDb.isBlank()) {
            return fromDb;
        }
        return leadingStageToken(node != null ? node.getTaskName() : null);
    }

    private static String leadingStageToken(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        int sep = indexOfSeparator(trimmed);
        String token = sep > 0 ? trimmed.substring(0, sep) : trimmed;
        return token.length() > 8 ? token.substring(0, 8) : token;
    }

    private static int indexOfSeparator(String value) {
        int under = value.indexOf('_');
        int space = value.indexOf(' ');
        if (under < 0) {
            return space;
        }
        if (space < 0) {
            return under;
        }
        return Math.min(under, space);
    }

    private static int clusterOrdinal(String id) {
        String normalized = ClusterIdentity.normalize(id);
        if (normalized == null) {
            return Integer.MAX_VALUE;
        }
        return Integer.parseInt(normalized.substring(1));
    }

    public static final class Column {
        private final String clusterId;
        private final String label;
        private final ClusterStatus status;
        private final String diedAtStage;

        Column(String clusterId, String label, ClusterStatus status, String diedAtStage) {
            this.clusterId = clusterId != null ? clusterId : "";
            this.label = label != null ? label : "";
            this.status = status != null ? status : ClusterStatus.LIVE;
            this.diedAtStage = diedAtStage;
        }

        public String getClusterId() {
            return clusterId;
        }

        public String getLabel() {
            return label;
        }

        public ClusterStatus getStatus() {
            return status;
        }

        public String getDiedAtStage() {
            return diedAtStage;
        }

        public boolean isDead() {
            return status == ClusterStatus.DEAD;
        }

        public String headerText() {
            if (label.isBlank()) {
                return clusterId;
            }
            return clusterId + " " + label;
        }
    }

    public static final class Row {
        private final int workflowNumber;
        private final String taskName;
        private final String stageKey;
        private final String sourceDatabank;
        private final String targetDatabank;
        private final List<Cell> cells;

        Row(int workflowNumber,
            String taskName,
            String stageKey,
            String sourceDatabank,
            String targetDatabank,
            List<Cell> cells) {
            this.workflowNumber = workflowNumber;
            this.taskName = taskName != null ? taskName : "";
            this.stageKey = stageKey != null ? stageKey : "";
            this.sourceDatabank = sourceDatabank != null ? sourceDatabank : "";
            this.targetDatabank = targetDatabank != null ? targetDatabank : "";
            this.cells = cells != null ? List.copyOf(cells) : List.of();
        }

        public int getWorkflowNumber() {
            return workflowNumber;
        }

        public String getTaskName() {
            return taskName;
        }

        public String getStageKey() {
            return stageKey;
        }

        public String getSourceDatabank() {
            return sourceDatabank;
        }

        public String getTargetDatabank() {
            return targetDatabank;
        }

        public List<Cell> getCells() {
            return cells;
        }

        public String trunkLabel() {
            String key = !stageKey.isBlank() ? stageKey : taskName;
            return workflowNumber + ". " + key;
        }
    }

    public static final class Cell {
        private final String clusterId;
        private final int liveCount;
        private final StageVerdict verdict;
        private final int championPassNumber;
        private final boolean deadLine;
        private final String stageKey;

        Cell(String clusterId,
             int liveCount,
             StageVerdict verdict,
             int championPassNumber,
             boolean deadLine,
             String stageKey) {
            this.clusterId = clusterId != null ? clusterId : "";
            this.liveCount = liveCount;
            this.verdict = verdict != null ? verdict : StageVerdict.PENDING;
            this.championPassNumber = championPassNumber;
            this.deadLine = deadLine;
            this.stageKey = stageKey != null ? stageKey : "";
        }

        public String getClusterId() {
            return clusterId;
        }

        public int getLiveCount() {
            return liveCount;
        }

        public StageVerdict getVerdict() {
            return verdict;
        }

        public int getChampionPassNumber() {
            return championPassNumber;
        }

        public boolean isDeadLine() {
            return deadLine;
        }

        public String getStageKey() {
            return stageKey;
        }

        public String cellLabel() {
            if (verdict == StageVerdict.PENDING && liveCount <= 0) {
                return "—";
            }
            if (verdict == StageVerdict.DIED || (liveCount <= 0 && deadLine)) {
                return "0 ✕";
            }
            if (liveCount <= 0) {
                return "—";
            }
            if (verdict == StageVerdict.IMPROVED) {
                return liveCount + " ▲";
            }
            return String.valueOf(liveCount);
        }

        public String fullLabel() {
            String count = cellLabel();
            if ("—".equals(count)) {
                return clusterId + " · " + stageKey + " · —";
            }
            return clusterId + " · " + stageKey + " · " + liveCount;
        }

        public String hoverText() {
            if (championPassNumber > 0) {
                return clusterId + " Champion Pass #" + championPassNumber;
            }
            return clusterId + " — kein Champion";
        }

        public boolean greyed() {
            return deadLine || verdict == StageVerdict.DIED || (liveCount <= 0 && verdict != StageVerdict.PENDING);
        }
    }
}
