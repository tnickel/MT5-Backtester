package com.backtester.workflow;

import com.backtester.config.EaParameter;
import com.backtester.report.OptimizationResult.CombinedPass;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Phase-1 Show Flow audit: which parameters moved from one optimizer stage
 * into the next task's setfile snapshot, and whether that matches.
 */
public final class WorkflowHandoffAuditService {

    private WorkflowHandoffAuditService() {
    }

    public enum ParamSource {
        PASS_FIXED,
        GATE_FORCED,
        STAGE_TARGET,
        SNAPSHOT_ONLY
    }

    public enum MatchStatus {
        OK,
        MISMATCH,
        MISSING,
        PENDING
    }

    public static final class ParameterTransfer {
        private final String name;
        private final String expectedValue;
        private final String actualValue;
        private final String optimizeStart;
        private final String optimizeStep;
        private final String optimizeEnd;
        private final boolean optimizeEnabled;
        private final ParamSource source;
        private final MatchStatus matchStatus;
        private final String setfileLine;
        private final String note;

        public ParameterTransfer(String name,
                                 String expectedValue,
                                 String actualValue,
                                 String optimizeStart,
                                 String optimizeStep,
                                 String optimizeEnd,
                                 boolean optimizeEnabled,
                                 ParamSource source,
                                 MatchStatus matchStatus,
                                 String setfileLine,
                                 String note) {
            this.name = name != null ? name : "";
            this.expectedValue = expectedValue != null ? expectedValue : "";
            this.actualValue = actualValue != null ? actualValue : "";
            this.optimizeStart = optimizeStart != null ? optimizeStart : "";
            this.optimizeStep = optimizeStep != null ? optimizeStep : "";
            this.optimizeEnd = optimizeEnd != null ? optimizeEnd : "";
            this.optimizeEnabled = optimizeEnabled;
            this.source = source != null ? source : ParamSource.SNAPSHOT_ONLY;
            this.matchStatus = matchStatus != null ? matchStatus : MatchStatus.PENDING;
            this.setfileLine = setfileLine != null ? setfileLine : "";
            this.note = note != null ? note : "";
        }

        public String getName() { return name; }
        public String getExpectedValue() { return expectedValue; }
        public String getActualValue() { return actualValue; }
        public String getOptimizeStart() { return optimizeStart; }
        public String getOptimizeStep() { return optimizeStep; }
        public String getOptimizeEnd() { return optimizeEnd; }
        public boolean isOptimizeEnabled() { return optimizeEnabled; }
        public ParamSource getSource() { return source; }
        public MatchStatus getMatchStatus() { return matchStatus; }
        public String getSetfileLine() { return setfileLine; }
        public String getNote() { return note; }

        public String getSourceLabel() {
            return switch (source) {
                case PASS_FIXED -> "Pass fixiert";
                case GATE_FORCED -> "Filter erzwungen";
                case STAGE_TARGET -> "Neues Opt-Ziel";
                case SNAPSHOT_ONLY -> "Snapshot";
            };
        }

        public String getMatchLabel() {
            return switch (matchStatus) {
                case OK -> "OK";
                case MISMATCH -> "ABWEICHUNG";
                case MISSING -> "FEHLT";
                case PENDING -> "PENDING";
            };
        }
    }

    public static final class HandoffTransition {
        private final int index;
        private final String fromTaskName;
        private final String viaTaskName;
        private final String toTaskName;
        private final WorkflowTask.TaskStatus toStatus;
        private final boolean adopted;
        private final int passNumber;
        private final String databank;
        private final double passScore;
        private final String gateParameter;
        private final String gateForcedValue;
        private final boolean gateForced;
        private final String gateNote;
        private final double gateOnMedian;
        private final double gateOffMedian;
        private final List<String> fromTargets;
        private final List<String> toTargets;
        private final List<ParameterTransfer> transfers;
        private final int okCount;
        private final int mismatchCount;
        private final int pendingCount;

        public HandoffTransition(int index,
                                 String fromTaskName,
                                 String viaTaskName,
                                 String toTaskName,
                                 WorkflowTask.TaskStatus toStatus,
                                 boolean adopted,
                                 int passNumber,
                                 String databank,
                                 double passScore,
                                 String gateParameter,
                                 String gateForcedValue,
                                 boolean gateForced,
                                 String gateNote,
                                 double gateOnMedian,
                                 double gateOffMedian,
                                 List<String> fromTargets,
                                 List<String> toTargets,
                                 List<ParameterTransfer> transfers) {
            this.index = index;
            this.fromTaskName = fromTaskName != null ? fromTaskName : "";
            this.viaTaskName = viaTaskName != null ? viaTaskName : "";
            this.toTaskName = toTaskName != null ? toTaskName : "";
            this.toStatus = toStatus;
            this.adopted = adopted;
            this.passNumber = passNumber;
            this.databank = databank != null ? databank : "";
            this.passScore = passScore;
            this.gateParameter = gateParameter != null ? gateParameter : "";
            this.gateForcedValue = gateForcedValue != null ? gateForcedValue : "";
            this.gateForced = gateForced;
            this.gateNote = gateNote != null ? gateNote : "";
            this.gateOnMedian = gateOnMedian;
            this.gateOffMedian = gateOffMedian;
            this.fromTargets = fromTargets != null ? List.copyOf(fromTargets) : List.of();
            this.toTargets = toTargets != null ? List.copyOf(toTargets) : List.of();
            this.transfers = transfers != null ? List.copyOf(transfers) : List.of();
            int ok = 0;
            int bad = 0;
            int pending = 0;
            for (ParameterTransfer t : this.transfers) {
                if (t.getMatchStatus() == MatchStatus.OK) ok++;
                else if (t.getMatchStatus() == MatchStatus.PENDING) pending++;
                else bad++;
            }
            this.okCount = ok;
            this.mismatchCount = bad;
            this.pendingCount = pending;
        }

        public int getIndex() { return index; }
        public String getFromTaskName() { return fromTaskName; }
        public String getViaTaskName() { return viaTaskName; }
        public String getToTaskName() { return toTaskName; }
        public WorkflowTask.TaskStatus getToStatus() { return toStatus; }
        public boolean isAdopted() { return adopted; }
        public int getPassNumber() { return passNumber; }
        public String getDatabank() { return databank; }
        public double getPassScore() { return passScore; }
        public String getGateParameter() { return gateParameter; }
        public String getGateForcedValue() { return gateForcedValue; }
        public boolean isGateForced() { return gateForced; }
        public String getGateNote() { return gateNote; }
        public double getGateOnMedian() { return gateOnMedian; }
        public double getGateOffMedian() { return gateOffMedian; }
        public List<String> getFromTargets() { return fromTargets; }
        public List<String> getToTargets() { return toTargets; }
        public List<ParameterTransfer> getTransfers() { return transfers; }
        public int getOkCount() { return okCount; }
        public int getMismatchCount() { return mismatchCount; }
        public int getPendingCount() { return pendingCount; }

        public String getTitle() {
            String to = toTaskName.isBlank() ? "—" : toTaskName;
            if (fromTaskName.isBlank()) {
                return "Startstufe → " + to;
            }
            String from = fromTaskName;
            if (!viaTaskName.isBlank()) {
                return from + "  →  " + viaTaskName + "  →  " + to;
            }
            return from + "  →  " + to;
        }

        public boolean isRootStage() {
            return fromTaskName == null || fromTaskName.isBlank();
        }

        public String getProofHeadline() {
            if (isRootStage()) {
                if (!toTargets.isEmpty() || !transfers.isEmpty()) {
                    return "Startstufe — Setfile aus Preset/Snapshot (keine Vorstufen-Übernahme)";
                }
                return "Startstufe — keine Vorstufen-Übernahme";
            }
            if (!adopted) {
                return "Noch nicht übernommen (" + pendingCount + " Parameter ausstehend)";
            }
            if (mismatchCount > 0) {
                return "ABWEICHUNG — " + mismatchCount + " problematisch, " + okCount + " OK";
            }
            if (okCount > 0) {
                return "VERIFIZIERT — " + okCount + " Parameter abgeglichen";
            }
            return "Keine Parameter zum Abgleich";
        }
    }

    /**
     * One timeline entry per workflow tile. {@code workflowNumber} matches the
     * editor card label {@code (index + 1) + ". " + name}.
     */
    public static final class FlowNode {
        private final int workflowNumber;
        private final String taskName;
        private final WorkflowTask.TaskType taskType;
        private final WorkflowTask.TaskStatus status;
        private final boolean enabled;
        private final HandoffTransition handoff;
        private final String sourceDatabank;
        private final String targetDatabank;
        private final int sourceCount;
        private final int targetCount;
        private final String summary;

        private FlowNode(int workflowNumber,
                         String taskName,
                         WorkflowTask.TaskType taskType,
                         WorkflowTask.TaskStatus status,
                         boolean enabled,
                         HandoffTransition handoff,
                         String sourceDatabank,
                         String targetDatabank,
                         int sourceCount,
                         int targetCount,
                         String summary) {
            this.workflowNumber = workflowNumber;
            this.taskName = taskName != null ? taskName : "";
            this.taskType = taskType;
            this.status = status;
            this.enabled = enabled;
            this.handoff = handoff;
            this.sourceDatabank = sourceDatabank != null ? sourceDatabank : "";
            this.targetDatabank = targetDatabank != null ? targetDatabank : "";
            this.sourceCount = sourceCount;
            this.targetCount = targetCount;
            this.summary = summary != null ? summary : "";
        }

        public int getWorkflowNumber() { return workflowNumber; }
        public String getTaskName() { return taskName; }
        public WorkflowTask.TaskType getTaskType() { return taskType; }
        public WorkflowTask.TaskStatus getStatus() { return status; }
        public boolean isEnabled() { return enabled; }
        public HandoffTransition getHandoff() { return handoff; }
        public boolean hasHandoff() { return handoff != null; }
        public String getSourceDatabank() { return sourceDatabank; }
        public String getTargetDatabank() { return targetDatabank; }
        public int getSourceCount() { return sourceCount; }
        public int getTargetCount() { return targetCount; }
        public String getSummary() { return summary; }

        public String getTypeLabel() {
            return taskType != null ? taskType.canonical().getDisplayName() : "?";
        }

        public String getStatusLabel() {
            if (!enabled) return "DISABLED";
            return status != null ? status.name() : "PENDING";
        }
    }

    /**
     * Full workflow timeline — one node per task, numbers identical to the editor tiles.
     */
    public static List<FlowNode> buildTimeline(CustomProject project,
                                               DatabankManager databankManager) {
        List<FlowNode> nodes = new ArrayList<>();
        if (project == null || project.getTasks() == null) return nodes;

        List<WorkflowTask> tasks = project.getTasks();
        for (int i = 0; i < tasks.size(); i++) {
            WorkflowTask task = tasks.get(i);
            if (task == null) continue;
            int workflowNumber = i + 1;
            WorkflowTask.TaskType type = task.getType() != null ? task.getType().canonical() : null;

            if (type == WorkflowTask.TaskType.OPTIMIZER) {
                WorkflowTask producer = GuidedOptimizationService
                        .findPreviousEnabledOptimizer(project, task).orElse(null);
                WorkflowTask viaFilter = findFilterBetween(tasks, producer, task);
                HandoffTransition handoff = buildOne(
                        workflowNumber, producer, viaFilter, task, databankManager, project.getSymbol());
                nodes.add(new FlowNode(
                        workflowNumber,
                        task.getName(),
                        type,
                        task.getStatus(),
                        task.isEnabled(),
                        handoff,
                        task.getSourceDatabank(),
                        task.getTargetDatabank(),
                        count(databankManager, task.getSourceDatabank()),
                        count(databankManager, task.getTargetDatabank()),
                        handoff.getProofHeadline()));
                continue;
            }

            int inCount = count(databankManager, task.getSourceDatabank());
            int outCount = count(databankManager, task.getTargetDatabank());
            String summary;
            if (type == WorkflowTask.TaskType.PRE_FILTER) {
                summary = "Qualitätsfilter: " + inCount + " → " + outCount
                        + " („" + nullToEmpty(task.getSourceDatabank()) + "“ → „"
                        + nullToEmpty(task.getTargetDatabank()) + "“)";
            } else if (type == WorkflowTask.TaskType.STRATEGY_SELECTION) {
                summary = "Projektbasis (EA/Symbol/Period)";
            } else if (type == WorkflowTask.TaskType.RETESTER) {
                summary = "Retest: " + inCount + " → " + outCount;
            } else if (type == WorkflowTask.TaskType.DIVERSITY_FILTER) {
                summary = "Diversität: " + inCount + " → " + outCount;
            } else {
                summary = (type != null ? type.getDisplayName() : "Task")
                        + ": „" + nullToEmpty(task.getSourceDatabank()) + "“ → „"
                        + nullToEmpty(task.getTargetDatabank()) + "“";
            }
            nodes.add(new FlowNode(
                    workflowNumber,
                    task.getName(),
                    type,
                    task.getStatus(),
                    task.isEnabled(),
                    null,
                    task.getSourceDatabank(),
                    task.getTargetDatabank(),
                    inCount,
                    outCount,
                    summary));
        }
        return nodes;
    }

    /** Optimizer-only handoffs; {@code index} is the workflow tile number of the consumer. */
    public static List<HandoffTransition> build(CustomProject project,
                                                DatabankManager databankManager) {
        List<HandoffTransition> handoffs = new ArrayList<>();
        for (FlowNode node : buildTimeline(project, databankManager)) {
            if (node.hasHandoff()) handoffs.add(node.getHandoff());
        }
        return handoffs;
    }

    private static int count(DatabankManager databankManager, String dbName) {
        if (databankManager == null) return 0;
        List<CombinedPass> list = databankManager.getDatabank(dbName);
        return list != null ? list.size() : 0;
    }

    static HandoffTransition buildOne(int index,
                                      WorkflowTask producer,
                                      WorkflowTask viaFilter,
                                      WorkflowTask consumer,
                                      DatabankManager databankManager,
                                      String projectSymbol) {
        boolean adopted = consumer != null && consumer.isOptimizerParameterBasisAdopted();
        int passNumber = -1;
        String databank = "";
        CombinedPass adoptedPass = null;
        double score = Double.NaN;
        // Only surface pass identity when adoption actually happened — never invent a
        // "best pass" candidate that looks like a completed hand-off.
        if (adopted && consumer != null) {
            passNumber = consumer.getOptimizerParameterBasisPassNumber();
            databank = consumer.getOptimizerParameterBasisDatabank();
            adoptedPass = findPass(databankManager, databank, passNumber);
            if (adoptedPass == null && !consumer.getSourceDatabank().isBlank()) {
                adoptedPass = findPass(databankManager, consumer.getSourceDatabank(), passNumber);
            }
            if (adoptedPass != null) {
                score = adoptedPass.getScore();
            }
        }

        List<String> fromTargets = resolveTargets(producer, projectSymbol);
        List<String> toTargets = resolveTargets(consumer, projectSymbol);

        Map<String, String> passParams = parameterMap(adoptedPass);
        Map<String, EaParameter> snapshot = indexSnapshot(
                consumer != null ? consumer.getOptimizerParameterSnapshot() : List.of());
        boolean adoptedPassMissing = adopted && adoptedPass == null
                && consumer != null && consumer.getOptimizerParameterBasisPassNumber() >= 0;

        Set<String> watch = new LinkedHashSet<>();
        watch.addAll(fromTargets);
        watch.addAll(toTargets);
        if (consumer != null && !consumer.getAdoptedFilterGateParameter().isBlank()) {
            for (String part : consumer.getAdoptedFilterGateParameter().split(",")) {
                if (part != null && !part.isBlank()) watch.add(part.trim());
            }
        }
        for (String key : passParams.keySet()) {
            if (FilterGateAnalysisService.looksLikeUseGate(key)) watch.add(key);
        }
        for (String key : snapshot.keySet()) {
            EaParameter p = snapshot.get(key);
            if (p != null && FilterGateAnalysisService.looksLikeUseGate(p.getName())) {
                watch.add(p.getName());
            }
        }

        List<ParameterTransfer> transfers = new ArrayList<>();
        boolean rootStage = producer == null;
        for (String rawName : watch) {
            if (rawName == null || rawName.isBlank()) continue;
            transfers.add(buildTransfer(
                    rawName.trim(), consumer, fromTargets, toTargets, passParams, snapshot,
                    adopted, rootStage, adoptedPassMissing));
        }
        transfers.sort((a, b) -> {
            int sa = sourceOrder(a.getSource());
            int sb = sourceOrder(b.getSource());
            if (sa != sb) return Integer.compare(sa, sb);
            return a.getName().compareToIgnoreCase(b.getName());
        });

        return new HandoffTransition(
                index,
                producer != null ? producer.getName() : "",
                viaFilter != null ? viaFilter.getName() : "",
                consumer != null ? consumer.getName() : "",
                consumer != null ? consumer.getStatus() : null,
                adopted,
                passNumber,
                databank,
                score,
                consumer != null ? consumer.getAdoptedFilterGateParameter() : "",
                consumer != null ? consumer.getAdoptedFilterGateForcedValue() : "",
                consumer != null && consumer.isAdoptedFilterGateForced(),
                consumer != null ? consumer.getAdoptedFilterGateNote() : "",
                consumer != null ? consumer.getAdoptedFilterGateOnMedianScore() : Double.NaN,
                consumer != null ? consumer.getAdoptedFilterGateOffMedianScore() : Double.NaN,
                fromTargets,
                toTargets,
                transfers);
    }

    private static ParameterTransfer buildTransfer(String name,
                                                   WorkflowTask consumer,
                                                   List<String> fromTargets,
                                                   List<String> toTargets,
                                                   Map<String, String> passParams,
                                                   Map<String, EaParameter> snapshot,
                                                   boolean adopted,
                                                   boolean rootStage,
                                                   boolean adoptedPassMissing) {
        EaParameter inSet = findSnapshot(snapshot, name);
        boolean isToTarget = containsIgnoreCase(toTargets, name);
        boolean isFromTarget = containsIgnoreCase(fromTargets, name);
        boolean isForcedGate = consumer != null
                && consumer.isAdoptedFilterGateForced()
                && FilterGateAnalysisService.gateAuditMentions(
                        consumer.getAdoptedFilterGateParameter(), name);

        ParamSource source;
        String expected;
        String note = "";
        if (isForcedGate) {
            source = ParamSource.GATE_FORCED;
            expected = FilterGateAnalysisService.resolveForcedValueForGate(
                    consumer.getAdoptedFilterGateParameter(),
                    consumer.getAdoptedFilterGateForcedValue(),
                    name);
            note = "Filter-Empfehlung der Vorstufe";
        } else if (isToTarget) {
            source = ParamSource.STAGE_TARGET;
            expected = inSet != null ? nullToEmpty(inSet.getValue()) : "";
            note = rootStage
                    ? "Suchziel dieser Startstufe"
                    : "Wird in dieser Stufe neu optimiert (Opt=Y)";
        } else if (isFromTarget || passParams.containsKey(name)
                || findParamIgnoreCase(passParams, name) != null) {
            source = ParamSource.PASS_FIXED;
            expected = findParamIgnoreCase(passParams, name);
            if (expected == null) expected = "";
            note = adoptedPassMissing
                    ? "Übernommener Pass fehlt in der Databank"
                    : "Aus übernommenem Pass fixiert";
        } else {
            source = ParamSource.SNAPSHOT_ONLY;
            expected = inSet != null ? nullToEmpty(inSet.getValue()) : "";
            note = rootStage ? "Startstufe-Snapshot" : "Nur im Snapshot vorhanden";
        }

        if (!adopted && !rootStage) {
            return new ParameterTransfer(
                    name,
                    expected,
                    inSet != null ? nullToEmpty(inSet.getValue()) : "",
                    inSet != null ? nullToEmpty(inSet.getOptimizeStart()) : "",
                    inSet != null ? nullToEmpty(inSet.getOptimizeStep()) : "",
                    inSet != null ? nullToEmpty(inSet.getOptimizeEnd()) : "",
                    inSet != null && inSet.isOptimizeEnabled(),
                    source,
                    MatchStatus.PENDING,
                    inSet != null ? inSet.toSetFileLine() : "",
                    "Basis noch nicht übernommen");
        }

        if (inSet == null) {
            return new ParameterTransfer(
                    name, expected, "", "", "", "", false, source,
                    rootStage ? MatchStatus.PENDING : MatchStatus.MISSING,
                    "",
                    rootStage ? "Nicht im Start-Snapshot" : "Fehlt im Setfile-Snapshot");
        }

        String actual = nullToEmpty(inSet.getValue());
        if (rootStage) {
            // Start stage: show current snapshot as ground truth, no prior pass to match.
            return new ParameterTransfer(
                    name,
                    actual,
                    actual,
                    nullToEmpty(inSet.getOptimizeStart()),
                    nullToEmpty(inSet.getOptimizeStep()),
                    nullToEmpty(inSet.getOptimizeEnd()),
                    inSet.isOptimizeEnabled(),
                    source,
                    MatchStatus.OK,
                    inSet.toSetFileLine(),
                    note);
        }

        if (adoptedPassMissing && source == ParamSource.PASS_FIXED) {
            return new ParameterTransfer(
                    name,
                    expected,
                    actual,
                    nullToEmpty(inSet.getOptimizeStart()),
                    nullToEmpty(inSet.getOptimizeStep()),
                    nullToEmpty(inSet.getOptimizeEnd()),
                    inSet.isOptimizeEnabled(),
                    source,
                    MatchStatus.MISSING,
                    inSet.toSetFileLine(),
                    "Übernommener Pass #"
                            + (consumer != null ? consumer.getOptimizerParameterBasisPassNumber() : "?")
                            + " nicht in Databank — Abgleich unmöglich");
        }

        boolean valueOk = source == ParamSource.STAGE_TARGET
                || valuesMatch(expected, actual);
        // Blank expected is only OK for stage targets (value is the search seed), not for
        // missing pass evidence.
        if (source != ParamSource.STAGE_TARGET && (expected == null || expected.isBlank())
                && !isForcedGate) {
            valueOk = false;
            note = "Kein erwarteter Pass-Wert zum Abgleich";
        }
        boolean optOk = isToTarget ? inSet.isOptimizeEnabled() : !inSet.isOptimizeEnabled();
        if (isForcedGate && !isToTarget) {
            optOk = !inSet.isOptimizeEnabled();
            valueOk = valuesMatch(expected, actual);
        }

        MatchStatus match = (valueOk && optOk) ? MatchStatus.OK : MatchStatus.MISMATCH;
        if (!valueOk && (note == null || note.isBlank()
                || note.equals("Aus übernommenem Pass fixiert")
                || note.equals("Filter-Empfehlung der Vorstufe")
                || note.equals("Nur im Snapshot vorhanden"))) {
            note = "Wert weicht ab (erwartet=" + expected + ", ist=" + actual + ")";
        } else if (!optOk) {
            note = isToTarget ? "Sollte Opt=Y sein" : "Sollte Opt=N (fixiert) sein";
        }

        return new ParameterTransfer(
                name,
                expected,
                actual,
                nullToEmpty(inSet.getOptimizeStart()),
                nullToEmpty(inSet.getOptimizeStep()),
                nullToEmpty(inSet.getOptimizeEnd()),
                inSet.isOptimizeEnabled(),
                source,
                match,
                inSet.toSetFileLine(),
                note);
    }

    private static int sourceOrder(ParamSource source) {
        return switch (source) {
            case GATE_FORCED -> 0;
            case PASS_FIXED -> 1;
            case STAGE_TARGET -> 2;
            case SNAPSHOT_ONLY -> 3;
        };
    }

    /** Stored targets, or Guided stage targets resolved from the task name. */
    static List<String> resolveTargets(WorkflowTask task) {
        return resolveTargets(task, "");
    }

    static List<String> resolveTargets(WorkflowTask task, String projectSymbol) {
        if (task == null) return List.of();
        List<String> stored = task.getOptimizerTargetParameters();
        if (stored != null && !stored.isEmpty()) {
            return new ArrayList<>(stored);
        }
        return ToTheMoon132GuidedWorkflowFactory.resolveStageTargetsForTaskName(task.getName(), projectSymbol)
                .map(ArrayList::new)
                .orElseGet(ArrayList::new);
    }

    private static WorkflowTask findFilterBetween(List<WorkflowTask> tasks,
                                                  WorkflowTask producer,
                                                  WorkflowTask consumer) {
        if (tasks == null || consumer == null) return null;
        boolean between = producer == null;
        WorkflowTask found = null;
        for (WorkflowTask task : tasks) {
            if (task == producer) {
                between = true;
                continue;
            }
            if (task == consumer) break;
            if (!between || task == null || !task.isEnabled()) continue;
            if (task.getType() == WorkflowTask.TaskType.PRE_FILTER) {
                found = task;
            }
        }
        return found;
    }

    private static CombinedPass findPass(DatabankManager databankManager, String dbName, int passNumber) {
        if (databankManager == null || passNumber < 0) return null;
        for (CombinedPass pass : databankManager.getDatabank(dbName)) {
            if (pass != null && pass.getPassNumber() == passNumber) return pass;
        }
        return null;
    }

    private static Map<String, String> parameterMap(CombinedPass pass) {
        if (pass == null || pass.getBacktestPass() == null
                || pass.getBacktestPass().getParameterValues() == null) {
            return Map.of();
        }
        return pass.getBacktestPass().getParameterValues();
    }

    private static Map<String, EaParameter> indexSnapshot(List<EaParameter> snapshot) {
        Map<String, EaParameter> byName = new LinkedHashMap<>();
        if (snapshot == null) return byName;
        for (EaParameter p : snapshot) {
            if (p == null || p.isSectionHeader() || p.getName() == null || p.getName().isBlank()) continue;
            byName.putIfAbsent(p.getName().trim().toLowerCase(Locale.ROOT), p);
        }
        return byName;
    }

    private static EaParameter findSnapshot(Map<String, EaParameter> byName, String name) {
        if (byName == null || name == null) return null;
        return byName.get(name.trim().toLowerCase(Locale.ROOT));
    }

    private static String findParamIgnoreCase(Map<String, String> map, String name) {
        if (map == null || name == null) return null;
        if (map.containsKey(name)) return map.get(name);
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) return e.getValue();
        }
        return null;
    }

    private static boolean containsIgnoreCase(List<String> names, String name) {
        if (names == null || name == null) return false;
        for (String n : names) {
            if (n != null && n.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private static boolean valuesMatch(String expected, String actual) {
        if (expected == null || expected.isBlank()) return true;
        if (actual == null) return false;
        Boolean e = FilterGateAnalysisService.normalizeBoolean(expected);
        Boolean a = FilterGateAnalysisService.normalizeBoolean(actual);
        if (e != null && a != null) return e.equals(a);
        return expected.trim().equalsIgnoreCase(actual.trim());
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
