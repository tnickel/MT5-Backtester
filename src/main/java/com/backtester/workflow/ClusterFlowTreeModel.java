package com.backtester.workflow;

import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.workflow.ClusterCensus.ClusterLine;
import com.backtester.workflow.ClusterCensus.ClusterStageSnapshot;
import com.backtester.workflow.ClusterCensus.ClusterStatus;
import com.backtester.workflow.ClusterCensus.StageVerdict;
import com.backtester.workflow.WorkflowHandoffAuditService.FlowNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure-Java cluster flow model for Show Flow. Empty census → no tree (linear fallback).
 * Provides cluster columns × stage rows plus a top-down Stammbaum layout for the zoomable canvas.
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
                        cells,
                        node.getTaskType(),
                        node.getSourceCount(),
                        node.getTargetCount(),
                        node.getSummary()));
            }
        }
        return new ClusterFlowTreeModel(
                Collections.unmodifiableList(columns),
                Collections.unmodifiableList(rows));
    }

    public boolean hasTree() {
        if (columns.isEmpty() || rows.isEmpty()) {
            return false;
        }
        for (Row row : rows) {
            for (Cell cell : row.getCells()) {
                if (!isAbsent(cell)) {
                    return true;
                }
            }
        }
        return false;
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
     * Use for filter/measure stages and gallery of survivors.
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

    /**
     * Databank whose rows match the tree badge for this stage.
     * Optimizer stages use {@code _raw} (Kandidaten); other stages prefer {@code _pick}.
     */
    public static String detailDatabankName(Row row) {
        if (row == null) {
            return "";
        }
        if (row.isOptimizerResult()) {
            String target = row.getTargetDatabank();
            if (!target.isBlank()) {
                return target;
            }
        }
        return pickDatabankName(row);
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

    /**
     * Layout constants for the zoomable multipath canvas (scene units).
     * Tree grows top → bottom: Stamm (root) at {@link #LAYOUT_ROOT_Y}, then stage rows.
     */
    public static final double LAYOUT_TRUNK_X = 86;
    public static final double LAYOUT_ROOT_Y = 40;
    public static final double LAYOUT_COL0_X = 260;
    public static final double LAYOUT_COL_GAP = 140;
    public static final double LAYOUT_ROW0_Y = 170;
    public static final double LAYOUT_ROW_GAP = 130;
    public static final double LAYOUT_PAD = 48;

    /**
     * Positioned trunk or cluster node for the graphical tree. Pure data — no JavaFX.
     * Root: {@code isRoot()} (trunk, rowIndex &lt; 0). Stage labels: trunk with rowIndex ≥ 0
     * (handoff only, not in the fork). Cluster cells: branches under the root.
     */
    public static final class LayoutNode {
        private final boolean trunk;
        private final int rowIndex;
        private final int colIndex;
        private final double x;
        private final double y;
        private final Row row;
        private final Column column;
        private final Cell cell;
        private final String rootLabel;

        LayoutNode(boolean trunk,
                   int rowIndex,
                   int colIndex,
                   double x,
                   double y,
                   Row row,
                   Column column,
                   Cell cell) {
            this(trunk, rowIndex, colIndex, x, y, row, column, cell, null);
        }

        LayoutNode(boolean trunk,
                   int rowIndex,
                   int colIndex,
                   double x,
                   double y,
                   Row row,
                   Column column,
                   Cell cell,
                   String rootLabel) {
            this.trunk = trunk;
            this.rowIndex = rowIndex;
            this.colIndex = colIndex;
            this.x = x;
            this.y = y;
            this.row = row;
            this.column = column;
            this.cell = cell;
            this.rootLabel = rootLabel;
        }

        public boolean isTrunk() {
            return trunk;
        }

        /** Top-of-tree Stamm node (not a stage handoff chip). */
        public boolean isRoot() {
            return trunk && rowIndex < 0;
        }

        public int getRowIndex() {
            return rowIndex;
        }

        public int getColIndex() {
            return colIndex;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public Row getRow() {
            return row;
        }

        public Column getColumn() {
            return column;
        }

        public Cell getCell() {
            return cell;
        }

        public String displayLabel() {
            if (isRoot()) {
                return rootLabel != null && !rootLabel.isBlank() ? rootLabel : "Stamm";
            }
            if (trunk) {
                if (row == null) {
                    return "";
                }
                if (row.isOptimizerResult()) {
                    int candidates = row.candidateTotal();
                    String line2 = candidates > 0
                            ? "Ergebnis · " + candidates + " Kand."
                            : "Ergebnis Suche";
                    return row.trunkLabel() + "\n" + line2;
                }
                String action = row.actionVerb();
                int total = row.liveTotal();
                String line2 = total > 0 ? action + " · Σ" + total : action;
                return row.trunkLabel() + "\n" + line2;
            }
            if (cell == null) {
                return "—";
            }
            String id = cell.getClusterId();
            String stage = cell.getStageKey();
            String head = stage.isBlank() ? id : id + " " + stage;
            String count = cell.cellLabel();
            if (row != null && row.isOptimizerResult()) {
                if ("—".equals(count)) {
                    return head + "\n—";
                }
                String n = count.replace(" ▲", "").replace(" ✕", "").trim();
                return head + "\n" + n + " Kand.";
            }
            if ("—".equals(count)) {
                return head + "\n—";
            }
            if (count.startsWith("0")) {
                return head + "\n" + count;
            }
            return head + "\nn=" + count.replace(" ▲", "").trim()
                    + (cell.getVerdict() == StageVerdict.IMPROVED ? " ▲" : "");
        }
    }

    /** Directed edge between two layout nodes (trunk chain, branch spur, or line chain). */
    public static final class LayoutEdge {
        private final LayoutNode from;
        private final LayoutNode to;

        LayoutEdge(LayoutNode from, LayoutNode to) {
            this.from = from;
            this.to = to;
        }

        public LayoutNode getFrom() {
            return from;
        }

        public LayoutNode getTo() {
            return to;
        }
    }

    public List<LayoutNode> layoutNodes() {
        int[] activeRows = activeRowIndices();
        int[] activeCols = activeColumnIndices();
        if (activeRows.length == 0 || activeCols.length == 0) {
            return List.of();
        }
        List<LayoutNode> nodes = new ArrayList<>();
        double treeCenterX = LAYOUT_COL0_X + (activeCols.length - 1) * LAYOUT_COL_GAP / 2.0;
        nodes.add(new LayoutNode(
                true, -1, -1, treeCenterX, LAYOUT_ROOT_Y, null, null, null, rootDisplayLabel(activeRows)));

        for (int ri = 0; ri < activeRows.length; ri++) {
            int r = activeRows[ri];
            Row row = rows.get(r);
            double y = LAYOUT_ROW0_Y + ri * LAYOUT_ROW_GAP;
            // Left stage chips: handoff only — not wired into the fork.
            nodes.add(new LayoutNode(true, r, -1, LAYOUT_TRUNK_X, y, row, null, null));
            List<Cell> cells = row.getCells();
            for (int ci = 0; ci < activeCols.length; ci++) {
                int c = activeCols[ci];
                if (c >= cells.size()) {
                    continue;
                }
                Cell cell = cells.get(c);
                if (isAbsent(cell)) {
                    continue; // no placeholder: unused stage×line is not drawn
                }
                double x = LAYOUT_COL0_X + ci * LAYOUT_COL_GAP;
                nodes.add(new LayoutNode(false, r, c, x, y, row, columns.get(c), cell));
            }
        }
        return Collections.unmodifiableList(nodes);
    }

    /**
     * Edges form a classic tree: Stamm → first present stage of each line, then
     * vertical links between consecutive present stages of that line.
     * Absent ({@code —}) cells are skipped so unused slots do not break the flow.
     */
    public List<LayoutEdge> layoutEdges() {
        List<LayoutNode> nodes = layoutNodes();
        if (nodes.isEmpty()) {
            return List.of();
        }
        LayoutNode root = null;
        Map<Integer, List<LayoutNode>> byColumn = new LinkedHashMap<>();
        for (LayoutNode node : nodes) {
            if (node.isRoot()) {
                root = node;
            } else if (!node.isTrunk()) {
                byColumn.computeIfAbsent(node.getColIndex(), k -> new ArrayList<>()).add(node);
            }
        }
        List<LayoutEdge> edges = new ArrayList<>();
        for (List<LayoutNode> chain : byColumn.values()) {
            chain.sort(Comparator.comparingInt(LayoutNode::getRowIndex));
            if (chain.isEmpty()) {
                continue;
            }
            if (root != null) {
                edges.add(new LayoutEdge(root, chain.get(0)));
            }
            for (int i = 0; i + 1 < chain.size(); i++) {
                edges.add(new LayoutEdge(chain.get(i), chain.get(i + 1)));
            }
        }
        return Collections.unmodifiableList(edges);
    }

    public double layoutWidth() {
        int cols = activeColumnIndices().length;
        if (cols == 0) {
            return LAYOUT_TRUNK_X + LAYOUT_PAD;
        }
        return LAYOUT_COL0_X + (cols - 1) * LAYOUT_COL_GAP + LAYOUT_PAD;
    }

    public double layoutHeight() {
        int rowCount = activeRowIndices().length;
        if (rowCount == 0) {
            return LAYOUT_ROOT_Y + LAYOUT_PAD;
        }
        return LAYOUT_ROW0_Y + (rowCount - 1) * LAYOUT_ROW_GAP + LAYOUT_PAD;
    }

    private int[] activeRowIndices() {
        List<Integer> out = new ArrayList<>();
        for (int r = 0; r < rows.size(); r++) {
            for (Cell cell : rows.get(r).getCells()) {
                if (!isAbsent(cell)) {
                    out.add(r);
                    break;
                }
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    private int[] activeColumnIndices() {
        List<Integer> out = new ArrayList<>();
        for (int c = 0; c < columns.size(); c++) {
            for (Row row : rows) {
                List<Cell> cells = row.getCells();
                if (c < cells.size() && !isAbsent(cells.get(c))) {
                    out.add(c);
                    break;
                }
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    private String rootDisplayLabel() {
        return rootDisplayLabel(activeRowIndices());
    }

    private String rootDisplayLabel(int[] activeRows) {
        if (activeRows == null || activeRows.length == 0) {
            return "Stamm";
        }
        String first = rows.get(activeRows[0]).getStageKey();
        String last = rows.get(activeRows[activeRows.length - 1]).getStageKey();
        if (first.isBlank() && last.isBlank()) {
            return "Stamm";
        }
        if (first.equals(last) || last.isBlank()) {
            return "Stamm " + first;
        }
        if (first.isBlank()) {
            return "Stamm .." + last;
        }
        return "Stamm " + first + ".." + last;
    }

    private static boolean isAbsent(Cell cell) {
        return cell == null || "—".equals(cell.cellLabel());
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
        ClusterStageSnapshot bySource = matchDatabank(line, node.getSourceDatabank());
        if (bySource != null) {
            return bySource;
        }
        return matchStageToken(line, shortStageKey(node));
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

    /**
     * Fallback when FlowNode databank names do not exactly match census snapshots
     * (e.g. token {@code g01} vs {@code g01_grid_pick}). Prefers non-{@code _raw} banks.
     */
    private static ClusterStageSnapshot matchStageToken(ClusterLine line, String stageToken) {
        if (line == null || stageToken == null || stageToken.isBlank()) {
            return null;
        }
        String token = stageToken.trim();
        ClusterStageSnapshot rawFallback = null;
        for (ClusterStageSnapshot snapshot : line.getPerStage()) {
            if (snapshot == null || snapshot.getDatabankName() == null) {
                continue;
            }
            String db = snapshot.getDatabankName();
            if (!token.equalsIgnoreCase(leadingStageToken(db))) {
                continue;
            }
            if (!isRawDatabank(db)) {
                return snapshot;
            }
            if (rawFallback == null) {
                rawFallback = snapshot;
            }
        }
        return rawFallback;
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
        private final WorkflowTask.TaskType taskType;
        private final int sourceCount;
        private final int targetCount;
        private final String summary;

        /** Test/helper ctor without task metadata. */
        public Row(int workflowNumber,
                   String taskName,
                   String stageKey,
                   String sourceDatabank,
                   String targetDatabank,
                   List<Cell> cells) {
            this(workflowNumber, taskName, stageKey, sourceDatabank, targetDatabank, cells,
                    null, -1, -1, "");
        }

        Row(int workflowNumber,
            String taskName,
            String stageKey,
            String sourceDatabank,
            String targetDatabank,
            List<Cell> cells,
            WorkflowTask.TaskType taskType,
            int sourceCount,
            int targetCount,
            String summary) {
            this.workflowNumber = workflowNumber;
            this.taskName = taskName != null ? taskName : "";
            this.stageKey = stageKey != null ? stageKey : "";
            this.sourceDatabank = sourceDatabank != null ? sourceDatabank : "";
            this.targetDatabank = targetDatabank != null ? targetDatabank : "";
            this.cells = cells != null ? List.copyOf(cells) : List.of();
            this.taskType = taskType != null ? taskType.canonical() : null;
            this.sourceCount = sourceCount;
            this.targetCount = targetCount;
            this.summary = summary != null ? summary : "";
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

        public WorkflowTask.TaskType getTaskType() {
            return taskType;
        }

        public int getSourceCount() {
            return sourceCount;
        }

        public int getTargetCount() {
            return targetCount;
        }

        public String getSummary() {
            return summary;
        }

        /** Short verb for tree chips, e.g. Optimiert / Filtert / Misst. */
        public String actionVerb() {
            return actionVerbFor(taskType);
        }

        /** One plain-German sentence: what this tile does (and does not do). */
        public String actionExplanation() {
            return actionExplanationFor(taskType);
        }

        public boolean isOptimizerResult() {
            return taskType == WorkflowTask.TaskType.OPTIMIZER;
        }

        /**
         * Optimizer target count ({@code _raw} candidates). Falls back to summed cell counts.
         */
        public int candidateTotal() {
            if (targetCount > 0) {
                return targetCount;
            }
            return liveTotal();
        }

        /** Sum of live strategies across all cluster lines in this stage. */
        public int liveTotal() {
            int sum = 0;
            for (Cell cell : cells) {
                if (cell != null && cell.getLiveCount() > 0) {
                    sum += cell.getLiveCount();
                }
            }
            return sum;
        }

        /** Number of lines that still have at least one live strategy. */
        public int livingLineCount() {
            int n = 0;
            for (Cell cell : cells) {
                if (cell != null && cell.getLiveCount() > 0) {
                    n++;
                }
            }
            return n;
        }

        public String trunkLabel() {
            String key = !stageKey.isBlank() ? stageKey : taskName;
            return workflowNumber + ". " + key;
        }
    }

    public static String actionVerbFor(WorkflowTask.TaskType type) {
        if (type == null) {
            return "Stufe";
        }
        return switch (type.canonical()) {
            case OPTIMIZER -> "Optimiert";
            case PRE_FILTER -> "Filtert";
            case DIVERSITY_FILTER -> "Diversität";
            case MASTER_REFERENCE -> "Misst";
            case RETESTER -> "Retestet";
            case ROBUSTNESS_CV -> "Robustheit";
            case KI_EVALUATION -> "KI";
            case STRATEGY_SELECTION -> "Konfiguriert";
            case PORTFOLIO_EXPORT -> "Export";
            default -> "Verarbeitet";
        };
    }

    public static String actionExplanationFor(WorkflowTask.TaskType type) {
        if (type == null) {
            return "Unbekannter Schritt.";
        }
        return switch (type.canonical()) {
            case OPTIMIZER ->
                    "Hier liegt das Ergebnis der Parametersuche: aus den zuvor gewählten "
                            + "Strategien/Linien-Champions sucht MT5 viele Varianten. "
                            + "Die große Zahl ist _raw-Kandidaten, keine finalen Picks — "
                            + "die Auswahl kommt erst im nächsten Filter.";
            case PRE_FILTER ->
                    "Hier wird gefiltert: Strategien nach Kennzahlen aussortieren. "
                            + "Keine neue Parametersuche im Optimizer.";
            case DIVERSITY_FILTER ->
                    "Hier wird nach Form diversifiziert: ähnliche Strategien zusammenfassen "
                            + "und bis zu B1–B10 Linien stempeln. Kein Optimizer.";
            case MASTER_REFERENCE ->
                    "Hier wird gemessen (Master-Referenz): bestehende Strategien unter festen "
                            + "Bedingungen backtesten und Linien bewerten/aussieben. "
                            + "Kein Optimieren und kein Qualitätsfilter wie bei Vorauswahl.";
            case RETESTER ->
                    "Hier wird retestet: vorhandene Strategien auf einem anderen Zeitraum "
                            + "nochmals laufen lassen. Keine neue Parametersuche.";
            case ROBUSTNESS_CV ->
                    "Hier läuft ein Robustheitstest (Sensitivity/CV). Kein normaler Optimizer-Lauf.";
            case KI_EVALUATION ->
                    "Hier bewertet die KI die Strategien. Kein MT5-Optimizer.";
            case STRATEGY_SELECTION ->
                    "Konfiguration: EA, Symbol, Period und Startbasis. Noch keine Suche/Filter.";
            case PORTFOLIO_EXPORT ->
                    "Export der finalen Sets/Berichte. Keine Optimierung.";
            default ->
                    "Schrittverarbeitung in der Pipeline.";
        };
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
