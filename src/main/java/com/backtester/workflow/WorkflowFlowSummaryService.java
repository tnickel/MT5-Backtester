package com.backtester.workflow;

import com.backtester.config.EaParameter;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import com.backtester.workflow.FilterGateAnalysisService.FilterGateAnalysis;
import com.backtester.workflow.FilterGateAnalysisService.Verdict;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Builds a readable end-to-end narrative of what each workflow task did and
 * which hand-off / filter decisions are visible from persisted state.
 * FilterGate Use_* advice is recomputed on demand (not historically logged).
 */
public final class WorkflowFlowSummaryService {

    private WorkflowFlowSummaryService() {
    }

    public enum StepTone {
        OK, WARN, PENDING, FAIL, IDLE
    }

    public enum ProofStatus {
        VERIFIED, MISMATCH, PENDING, NOT_APPLICABLE
    }

    /**
     * Audit trail: decision/pass values vs. the next tile's stored setfile snapshot.
     */
    public static final class SetfileProof {
        private final ProofStatus status;
        private final String headline;
        private final List<String> lines;

        public SetfileProof(ProofStatus status, String headline, List<String> lines) {
            this.status = status != null ? status : ProofStatus.NOT_APPLICABLE;
            this.headline = headline != null ? headline : "";
            this.lines = lines != null ? List.copyOf(lines) : List.of();
        }

        public static SetfileProof none() {
            return new SetfileProof(ProofStatus.NOT_APPLICABLE, "", List.of());
        }

        public ProofStatus getStatus() { return status; }
        public String getHeadline() { return headline; }
        public List<String> getLines() { return lines; }
        public boolean isPresent() { return status != ProofStatus.NOT_APPLICABLE; }
    }

    public static final class FlowStepSummary {
        private final int index;
        private final String taskName;
        private final String taskTypeLabel;
        private final String statusLabel;
        private final StepTone tone;
        private final String whatHappened;
        private final String decision;
        private final List<String> details;
        private final SetfileProof setfileProof;
        private final VisualDecision visualDecision;

        public FlowStepSummary(int index,
                               String taskName,
                               String taskTypeLabel,
                               String statusLabel,
                               StepTone tone,
                               String whatHappened,
                               String decision,
                               List<String> details) {
            this(index, taskName, taskTypeLabel, statusLabel, tone, whatHappened, decision, details,
                    SetfileProof.none(), VisualDecision.none());
        }

        public FlowStepSummary(int index,
                               String taskName,
                               String taskTypeLabel,
                               String statusLabel,
                               StepTone tone,
                               String whatHappened,
                               String decision,
                               List<String> details,
                               SetfileProof setfileProof) {
            this(index, taskName, taskTypeLabel, statusLabel, tone, whatHappened, decision, details,
                    setfileProof, VisualDecision.none());
        }

        public FlowStepSummary(int index,
                               String taskName,
                               String taskTypeLabel,
                               String statusLabel,
                               StepTone tone,
                               String whatHappened,
                               String decision,
                               List<String> details,
                               SetfileProof setfileProof,
                               VisualDecision visualDecision) {
            this.index = index;
            this.taskName = taskName != null ? taskName : "";
            this.taskTypeLabel = taskTypeLabel != null ? taskTypeLabel : "";
            this.statusLabel = statusLabel != null ? statusLabel : "";
            this.tone = tone != null ? tone : StepTone.IDLE;
            this.whatHappened = whatHappened != null ? whatHappened : "";
            this.decision = decision != null ? decision : "";
            this.details = details != null ? List.copyOf(details) : List.of();
            this.setfileProof = setfileProof != null ? setfileProof : SetfileProof.none();
            this.visualDecision = visualDecision != null ? visualDecision : VisualDecision.none();
        }

        public int getIndex() { return index; }
        public String getTaskName() { return taskName; }
        public String getTaskTypeLabel() { return taskTypeLabel; }
        public String getStatusLabel() { return statusLabel; }
        public StepTone getTone() { return tone; }
        public String getWhatHappened() { return whatHappened; }
        public String getDecision() { return decision; }
        public List<String> getDetails() { return details; }
        public SetfileProof getSetfileProof() { return setfileProof; }
        public VisualDecision getVisualDecision() { return visualDecision; }
    }

    /** Chart-ready filter decision for Show Flow graphics. */
    public static final class VisualDecision {
        private final String gateParameter;
        private final String badgeText;
        private final String badgeColor;
        private final double onMedianScore;
        private final double offMedianScore;
        private final boolean forced;
        private final String setfileLine;
        private final String caption;

        public VisualDecision(String gateParameter,
                              String badgeText,
                              String badgeColor,
                              double onMedianScore,
                              double offMedianScore,
                              boolean forced,
                              String setfileLine,
                              String caption) {
            this.gateParameter = gateParameter != null ? gateParameter : "";
            this.badgeText = badgeText != null ? badgeText : "";
            this.badgeColor = badgeColor != null ? badgeColor : "#90a4ae";
            this.onMedianScore = onMedianScore;
            this.offMedianScore = offMedianScore;
            this.forced = forced;
            this.setfileLine = setfileLine != null ? setfileLine : "";
            this.caption = caption != null ? caption : "";
        }

        public static VisualDecision none() {
            return new VisualDecision("", "", "#90a4ae", Double.NaN, Double.NaN, false, "", "");
        }

        public boolean isPresent() {
            return !badgeText.isBlank() || Double.isFinite(onMedianScore) || Double.isFinite(offMedianScore);
        }

        public String getGateParameter() { return gateParameter; }
        public String getBadgeText() { return badgeText; }
        public String getBadgeColor() { return badgeColor; }
        public double getOnMedianScore() { return onMedianScore; }
        public double getOffMedianScore() { return offMedianScore; }
        public boolean isForced() { return forced; }
        public String getSetfileLine() { return setfileLine; }
        public String getCaption() { return caption; }
    }

    public static List<FlowStepSummary> build(CustomProject project,
                                              DatabankManager databankManager,
                                              Function<WorkflowTask, String> outputDirectoryResolver) {
        List<FlowStepSummary> steps = new ArrayList<>();
        if (project == null) return steps;
        List<WorkflowTask> tasks = project.getTasks();
        if (tasks == null || tasks.isEmpty()) return steps;

        Function<WorkflowTask, String> dirs = outputDirectoryResolver != null
                ? outputDirectoryResolver : t -> "";

        for (int i = 0; i < tasks.size(); i++) {
            WorkflowTask task = tasks.get(i);
            if (task == null) continue;
            steps.add(summarizeTask(project, task, i + 1, tasks, databankManager, dirs));
        }
        return steps;
    }

    private static FlowStepSummary summarizeTask(CustomProject project,
                                                 WorkflowTask task,
                                                 int index,
                                                 List<WorkflowTask> allTasks,
                                                 DatabankManager databankManager,
                                                 Function<WorkflowTask, String> dirs) {
        String typeLabel = task.getType() != null
                ? task.getType().canonical().getDisplayName() : "?";
        String statusLabel = statusText(task);
        StepTone tone = toneFor(task);

        if (!task.isEnabled()) {
            return new FlowStepSummary(index, task.getName(), typeLabel, statusLabel, StepTone.IDLE,
                    "Task ist deaktiviert.",
                    "Keine Ausführung.",
                    List.of());
        }

        return switch (task.getType().canonical()) {
            case OPTIMIZER -> summarizeOptimizer(project, task, index, typeLabel, statusLabel, tone,
                    allTasks, databankManager, dirs.apply(task));
            case PRE_FILTER -> summarizePreFilter(task, index, typeLabel, statusLabel, tone,
                    allTasks, databankManager);
            case RETESTER -> summarizeDatabankMove(task, index, typeLabel, statusLabel, tone,
                    databankManager, "Retest ausgeführt");
            case DIVERSITY_FILTER -> summarizeDatabankMove(task, index, typeLabel, statusLabel, tone,
                    databankManager, "Diversitäts-Filter ausgeführt");
            case STRATEGY_SELECTION -> new FlowStepSummary(index, task.getName(), typeLabel, statusLabel, tone,
                    "Strategie-/Projektbasis (EA, Symbol, Zeitraum).",
                    project != null && project.isAutomaticModeEnabled()
                            ? "Automatikmodus aktiv — Stufen übernehmen höchsten Score."
                            : "Manueller Modus — Hand-Pick zwischen Stufen.",
                    List.of(
                            "Expert: " + nullDash(project != null ? project.getExpert() : null),
                            "Symbol: " + nullDash(project != null ? project.getSymbol() : null),
                            "Period: " + nullDash(project != null ? project.getPeriod() : null)));
            default -> new FlowStepSummary(index, task.getName(), typeLabel, statusLabel, tone,
                    "Task-Typ: " + typeLabel,
                    emptyLogOr(task, "Keine spezielle Entscheidungs-Logik hinterlegt."),
                    databankRouteDetails(task, databankManager));
        };
    }

    private static FlowStepSummary summarizeOptimizer(CustomProject project,
                                                      WorkflowTask task,
                                                      int index,
                                                      String typeLabel,
                                                      String statusLabel,
                                                      StepTone tone,
                                                      List<WorkflowTask> allTasks,
                                                      DatabankManager databankManager,
                                                      String outputDirectory) {
        List<String> details = new ArrayList<>();
        List<String> targets = task.getOptimizerTargetParameters();
        if (!targets.isEmpty()) {
            details.add("Optimierte Parameter: " + String.join(", ", targets));
        } else {
            details.add("Keine expliziten Zielparameter hinterlegt.");
        }

        StringBuilder decision = new StringBuilder();
        if (task.isOptimizerParameterBasisAdopted()) {
            details.add("Start-Basis übernommen: Pass #"
                    + task.getOptimizerParameterBasisPassNumber()
                    + " aus „" + task.getOptimizerParameterBasisDatabank() + "“.");
            if (task.isAdoptedFilterGateForced()) {
                details.add("Filter erzwungen: " + task.getAdoptedFilterGateParameter()
                        + "=" + task.getAdoptedFilterGateForcedValue());
                decision.append(FilterGateAnalysisService.formatForcedGateBadge(
                                task.getAdoptedFilterGateParameter(),
                                task.getAdoptedFilterGateForcedValue())
                        .replace(" → SETFILE", " im Setfile"))
                        .append(" · ");
            } else if (!task.getAdoptedFilterGateNote().isBlank()) {
                details.add(task.getAdoptedFilterGateNote());
            }
        } else if (GuidedOptimizationService.requiresAdoptedBasis(project, task)) {
            details.add("Wartet noch auf Parameter-Basis vom vorherigen Optimizer (Hand-Pick/Automatik).");
        }

        String targetDb = task.getTargetDatabank();
        int outCount = count(databankManager, targetDb);
        details.add(routeLine(task.getSourceDatabank(), targetDb, -1, outCount));

        CombinedPass bestInTarget = GuidedOptimizationService.selectBestPass(
                databankManager != null ? databankManager.getDatabank(targetDb) : List.of())
                .orElse(null);

        String what;
        if (task.getStatus() == WorkflowTask.TaskStatus.COMPLETED || outCount > 0) {
            what = "Optimizer ausgeführt → " + outCount + " Strategien in „" + nullDash(targetDb) + "“.";
        } else if (task.getStatus() == WorkflowTask.TaskStatus.RUNNING) {
            what = "Optimizer läuft…";
        } else if (task.getStatus() == WorkflowTask.TaskStatus.FAILED) {
            what = "Optimizer fehlgeschlagen.";
            if (task.getLastExecutionLog() != null && !task.getLastExecutionLog().isBlank()) {
                details.add("Log: " + task.getLastExecutionLog().trim());
            }
        } else {
            what = "Optimizer noch nicht ausgeführt (oder Ergebnis fehlt).";
        }

        String gateAdvice = "";
        if (bestInTarget != null) {
            decision.append("Bester Pass in Ziel-Databank: #")
                    .append(bestInTarget.getPassNumber())
                    .append(" (Score ")
                    .append(fmtScore(bestInTarget.getScore()))
                    .append(").");
            Map<String, String> relevant = extractRelevantParams(bestInTarget, targets);
            if (!relevant.isEmpty()) {
                details.add("Werte im besten Pass: " + formatParamMap(relevant));
            }
            gateAdvice = appendGateDecision(decision, details, task, outputDirectory, databankManager, bestInTarget);
        } else if (decision.length() == 0) {
            decision.append(emptyLogOr(task, "Noch keine Pass-Entscheidung ableitbar."));
        }

        WorkflowTask nextOptimizer = findNextEnabledOptimizer(allTasks, task);
        SetfileProof proof = SetfileProof.none();
        if (task.isOptimizerParameterBasisAdopted()) {
            CombinedPass basisPass = findPass(
                    databankManager,
                    task.getOptimizerParameterBasisDatabank(),
                    task.getOptimizerParameterBasisPassNumber());
            proof = buildConsumerSetfileProof(task, basisPass, List.of(), gateAdvice);
        }
        if (nextOptimizer != null && nextOptimizer.isOptimizerParameterBasisAdopted()
                && databankMatchesAdoption(nextOptimizer, task, allTasks)) {
            decision.append(" → Weitergegeben an „")
                    .append(nextOptimizer.getName())
                    .append("“ als Basis Pass #")
                    .append(nextOptimizer.getOptimizerParameterBasisPassNumber())
                    .append(".");
            CombinedPass adopted = findPass(
                    databankManager,
                    nextOptimizer.getOptimizerParameterBasisDatabank(),
                    nextOptimizer.getOptimizerParameterBasisPassNumber());
            SetfileProof handoff = buildHandoffSetfileProof(
                    task.getName(),
                    nextOptimizer,
                    adopted,
                    targets,
                    gateAdvice);
            proof = mergeProofs(proof, handoff);
        } else if (nextOptimizer != null
                && (bestInTarget != null || task.getStatus() == WorkflowTask.TaskStatus.COMPLETED)
                && !task.isOptimizerParameterBasisAdopted()) {
            proof = new SetfileProof(
                    ProofStatus.PENDING,
                    "Entscheidung noch nicht im Setfile der nächsten Kachel",
                    List.of(
                            "Nächste Kachel: „" + nextOptimizer.getName() + "“",
                            "Basis-Übernahme fehlt noch (Automatik/Hand-Pick).",
                            "Ohne Übernahme schreibt die nächste Stufe NICHT die Werte aus dieser Entscheidung.",
                            gateAdvice.isBlank()
                                    ? "Filter-Empfehlung ist nur Hinweis — Setfile entsteht erst bei Pass-Übernahme."
                                    : "Aktuelle Empfehlung: " + gateAdvice));
        } else if (nextOptimizer != null
                && (bestInTarget != null || task.getStatus() == WorkflowTask.TaskStatus.COMPLETED)
                && !nextOptimizer.isOptimizerParameterBasisAdopted()) {
            SetfileProof pendingOut = new SetfileProof(
                    ProofStatus.PENDING,
                    "Ausgang noch nicht in nächste Kachel übernommen",
                    List.of(
                            "Nächste Kachel: „" + nextOptimizer.getName() + "“ wartet auf Pass-Übernahme.",
                            gateAdvice.isBlank() ? "Empfehlung allein ändert kein Setfile." : gateAdvice));
            proof = mergeProofs(proof, pendingOut);
        }

        return new FlowStepSummary(index, task.getName(), typeLabel, statusLabel, tone,
                what, decision.toString().trim(), details, proof, visualDecisionFromTask(task));
    }

    private static VisualDecision visualDecisionFromTask(WorkflowTask task) {
        if (task == null || (task.getAdoptedFilterGateParameter().isBlank()
                && task.getAdoptedFilterGateNote().isBlank())) {
            return VisualDecision.none();
        }
        String verdict = task.getAdoptedFilterGateVerdict();
        String badge;
        String color;
        if (task.isAdoptedFilterGateForced()) {
            badge = FilterGateAnalysisService.formatForcedGateBadge(
                    task.getAdoptedFilterGateParameter(),
                    task.getAdoptedFilterGateForcedValue());
            color = task.getAdoptedFilterGateParameter().contains(",")
                    ? "#00bcd4"
                    : ("true".equalsIgnoreCase(task.getAdoptedFilterGateForcedValue())
                    ? "#00e676" : "#ffab40");
        } else if ("FILTER_ON_BETTER".equals(verdict)) {
            badge = "AN empfohlen (nicht erzwungen)";
            color = "#81c784";
        } else if ("FILTER_OFF_BETTER".equals(verdict)) {
            badge = "AUS empfohlen (nicht erzwungen)";
            color = "#ffcc80";
        } else if ("MULTI_GATE".equals(verdict)) {
            badge = "FILTER MULTI";
            color = "#00bcd4";
        } else if ("GATE_MISSING".equals(verdict) || task.getAdoptedFilterGateParameter().isBlank()) {
            badge = "KEIN GATE";
            color = "#90a4ae";
        } else {
            badge = "FILTER UNKLAR";
            color = "#64b5f6";
        }

        String setLine = "";
        String gate = task.getAdoptedFilterGateParameter();
        if (!gate.isBlank() && !gate.contains(",")) {
            for (EaParameter p : task.getOptimizerParameterSnapshot()) {
                if (p != null && p.getName() != null && p.getName().equalsIgnoreCase(gate)) {
                    setLine = p.toSetFileLine();
                    break;
                }
            }
        } else if (gate.contains(",")) {
            setLine = gate + "=" + task.getAdoptedFilterGateForcedValue();
        }
        return new VisualDecision(
                gate,
                badge,
                color,
                task.getAdoptedFilterGateOnMedianScore(),
                task.getAdoptedFilterGateOffMedianScore(),
                task.isAdoptedFilterGateForced(),
                setLine,
                task.getAdoptedFilterGateNote());
    }
    private static String appendGateDecision(StringBuilder decision,
                                           List<String> details,
                                           WorkflowTask task,
                                           String outputDirectory,
                                           DatabankManager databankManager,
                                           CombinedPass bestPass) {
        try {
            FilterGateAnalysisService.PassLoadResult loaded =
                    FilterGateAnalysisService.loadPassesForTask(task, outputDirectory, databankManager);
            List<String> candidates = FilterGateAnalysisService.listGateParameterCandidates(
                    task, loaded.getPasses());
            if (candidates.isEmpty()) {
                details.add("Kein Use_*-Gate in dieser Stufe (reine Parameter-Optimierung).");
                return "";
            }
            List<String> gates = FilterGateAnalysisService.selectGatesForAnalysis(task, candidates);
            if (gates.isEmpty()) {
                details.add("Kein Use_*-Zielparameter in dieser Stufe — Report-only Use_* werden "
                        + "nicht als Filter-Entscheidung ausgewertet.");
                return "";
            }
            List<String> optimizedNames = FilterGateAnalysisService.listOptimizedParameterNames(
                    task, loaded.getPasses());
            List<String> adviceParts = new ArrayList<>();
            for (String gate : gates) {
                FilterGateAnalysis analysis = FilterGateAnalysisService.analyze(
                        loaded.getPasses(),
                        gate,
                        loaded.getDataSource(),
                        loaded.getSourcePath(),
                        loaded.getDatabankName(),
                        FilterGateAnalysisService.DEFAULT_MIN_COHORT_SIZE,
                        FilterGateAnalysisService.DEFAULT_TOP_N,
                        FilterGateAnalysisService.DEFAULT_SCORE_MARGIN,
                        candidates,
                        optimizedNames);

                String actual = parameterValue(bestPass, gate);
                Boolean actualBool = FilterGateAnalysisService.normalizeBoolean(actual);
                String actualText = actualBool == null
                        ? (actual == null || actual.isBlank() ? "?" : actual)
                        : (actualBool ? "true (AN)" : "false (AUS)");

                details.add("Filter-Gate „" + gate + "“ im besten Pass: " + actualText);
                details.add("Filter-Nutzen („" + gate + "“): " + analysis.getVerdictMessage());

                String advice;
                if (analysis.getVerdict() == Verdict.FILTER_ON_BETTER) {
                    advice = "Filter AN empfohlen";
                    decision.append(" Entscheidung („").append(gate).append("“): ").append(advice);
                    if (Boolean.FALSE.equals(actualBool)) {
                        decision.append(" — bester Pass hat aber AUS");
                    } else if (Boolean.TRUE.equals(actualBool)) {
                        decision.append(" — bester Pass hat AN");
                    }
                    decision.append(".");
                } else if (analysis.getVerdict() == Verdict.FILTER_OFF_BETTER) {
                    advice = "Filter AUS empfohlen";
                    decision.append(" Entscheidung („").append(gate).append("“): ").append(advice);
                    if (Boolean.TRUE.equals(actualBool)) {
                        decision.append(" — bester Pass hat aber AN");
                    } else if (Boolean.FALSE.equals(actualBool)) {
                        decision.append(" — bester Pass hat AUS");
                    }
                    decision.append(".");
                } else if (analysis.getVerdict() == Verdict.GATE_MISSING) {
                    advice = "kein Gate";
                    decision.append(" Kein An/Aus-Filter entscheidbar für „").append(gate).append("“.");
                } else {
                    advice = "Filter unklar";
                    decision.append(" Filter-Entscheidung unklar / Datenlage dünn für „")
                            .append(gate).append("“.");
                }
                adviceParts.add(advice + " (" + gate + "=" + actualText + ")");
            }
            details.add("Im Automatikmodus wird eine klare AN/AUS-Empfehlung zusätzlich "
                    + "in das Setfile der nächsten Kachel geschrieben.");
            return String.join("; ", adviceParts);
        } catch (RuntimeException ex) {
            details.add("Filter-Nutzen konnte nicht berechnet werden: "
                    + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()));
            return "";
        }
    }

    private static FlowStepSummary summarizePreFilter(WorkflowTask task,
                                                      int index,
                                                      String typeLabel,
                                                      String statusLabel,
                                                      StepTone tone,
                                                      List<WorkflowTask> allTasks,
                                                      DatabankManager databankManager) {
        int inCount = count(databankManager, task.getSourceDatabank());
        int outCount = count(databankManager, task.getTargetDatabank());
        List<String> details = new ArrayList<>();
        details.add(routeLine(task.getSourceDatabank(), task.getTargetDatabank(), inCount, outCount));
        for (FilterCondition condition : task.getFilterConditions()) {
            if (condition == null || !condition.isEnabled()) continue;
            details.add("Regel: " + formatCondition(condition));
        }
        if (task.getFilterConditions().isEmpty()) {
            details.add("Keine Filterregeln konfiguriert (Durchreichung).");
        }

        String what;
        if (task.getStatus() == WorkflowTask.TaskStatus.COMPLETED || (inCount > 0 || outCount > 0)) {
            what = "Qualitätsfilter ausgeführt: " + inCount + " → " + outCount + " Strategien.";
        } else if (task.getStatus() == WorkflowTask.TaskStatus.FAILED) {
            what = "Filter fehlgeschlagen.";
        } else {
            what = "Filter noch nicht gelaufen.";
        }

        StringBuilder decision = new StringBuilder();
        if (inCount > 0 || outCount > 0) {
            int dropped = Math.max(0, inCount - outCount);
            decision.append(dropped)
                    .append(" aussortiert, ")
                    .append(outCount)
                    .append(" weiter.");
        } else {
            decision.append(emptyLogOr(task, "Noch kein Filterergebnis."));
        }

        WorkflowTask nextOptimizer = findNextEnabledOptimizer(allTasks, task);
        SetfileProof proof = SetfileProof.none();
        if (nextOptimizer != null && nextOptimizer.isOptimizerParameterBasisAdopted()
                && Objects.equals(
                normalizeDb(nextOptimizer.getOptimizerParameterBasisDatabank()),
                normalizeDb(task.getTargetDatabank()))) {
            CombinedPass adopted = findPass(
                    databankManager,
                    nextOptimizer.getOptimizerParameterBasisDatabank(),
                    nextOptimizer.getOptimizerParameterBasisPassNumber());
            decision.append(" Übernahme für nächste Stufe: Pass #")
                    .append(nextOptimizer.getOptimizerParameterBasisPassNumber());
            if (adopted != null) {
                decision.append(" (Score ").append(fmtScore(adopted.getScore())).append(")");
                Map<String, String> gates = extractGateParams(adopted);
                if (!gates.isEmpty()) {
                    details.add("Übernommene Filter-Schalter: " + formatParamMap(gates));
                }
            }
            decision.append(" → „").append(nextOptimizer.getName()).append("“.");
            // Previous optimizer targets are the values that should be frozen into the next setfile.
            WorkflowTask previousOptimizer = findPreviousEnabledOptimizer(allTasks, task);
            List<String> producerTargets = previousOptimizer != null
                    ? previousOptimizer.getOptimizerTargetParameters() : List.of();
            proof = buildHandoffSetfileProof(
                    task.getName(), nextOptimizer, adopted, producerTargets, "");
        } else if (nextOptimizer != null && outCount > 0) {
            proof = new SetfileProof(
                    ProofStatus.PENDING,
                    "Filter-Ergebnis noch nicht als Setfile in die nächste Kachel übernommen",
                    List.of(
                            "Nächste Kachel: „" + nextOptimizer.getName() + "“",
                            "Nach Hand-Pick/Automatik erscheint hier der Setfile-Abgleich."));
        }

        return new FlowStepSummary(index, task.getName(), typeLabel, statusLabel, tone,
                what, decision.toString().trim(), details, proof);
    }

    private static FlowStepSummary summarizeDatabankMove(WorkflowTask task,
                                                         int index,
                                                         String typeLabel,
                                                         String statusLabel,
                                                         StepTone tone,
                                                         DatabankManager databankManager,
                                                         String actionVerb) {
        int inCount = count(databankManager, task.getSourceDatabank());
        int outCount = count(databankManager, task.getTargetDatabank());
        List<String> details = databankRouteDetails(task, databankManager);
        String what;
        if (task.getStatus() == WorkflowTask.TaskStatus.COMPLETED || outCount > 0) {
            what = actionVerb + ": " + inCount + " → " + outCount + ".";
        } else if (task.getStatus() == WorkflowTask.TaskStatus.FAILED) {
            what = actionVerb + " fehlgeschlagen.";
        } else {
            what = actionVerb.replace(" ausgeführt", "") + " noch ausstehend.";
        }
        String decision = outCount > 0
                ? outCount + " Strategien in „" + nullDash(task.getTargetDatabank()) + "“."
                : emptyLogOr(task, "Noch kein Ergebnis in der Ziel-Databank.");
        CombinedPass best = GuidedOptimizationService.selectBestPass(
                databankManager != null ? databankManager.getDatabank(task.getTargetDatabank()) : List.of())
                .orElse(null);
        if (best != null) {
            details.add("Top-Pass: #" + best.getPassNumber() + " Score " + fmtScore(best.getScore()));
        }
        return new FlowStepSummary(index, task.getName(), typeLabel, statusLabel, tone,
                what, decision, details);
    }

    static Map<String, String> extractRelevantParams(CombinedPass pass, List<String> targetNames) {
        Map<String, String> all = parameterMap(pass);
        Map<String, String> out = new LinkedHashMap<>();
        if (targetNames != null) {
            for (String name : targetNames) {
                if (name == null || name.isBlank()) continue;
                String value = findParamIgnoreCase(all, name.trim());
                if (value != null) out.put(name.trim(), value);
            }
        }
        for (Map.Entry<String, String> e : all.entrySet()) {
            if (FilterGateAnalysisService.looksLikeUseGate(e.getKey()) && !containsKeyIgnoreCase(out, e.getKey())) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    static Map<String, String> extractGateParams(CombinedPass pass) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : parameterMap(pass).entrySet()) {
            if (FilterGateAnalysisService.looksLikeUseGate(e.getKey())) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    static SetfileProof buildHandoffSetfileProof(String fromTaskName,
                                                 WorkflowTask nextOptimizer,
                                                 CombinedPass adoptedPass,
                                                 List<String> producerTargets,
                                                 String gateAdvice) {
        return buildConsumerSetfileProof(
                nextOptimizer,
                adoptedPass,
                producerTargets,
                gateAdvice,
                "Setfile-Nachweis → nächste Kachel „" + (nextOptimizer != null ? nextOptimizer.getName() : "?") + "“"
                        + (fromTaskName != null && !fromTaskName.isBlank() ? " (von „" + fromTaskName + "“)" : ""));
    }

    static SetfileProof buildConsumerSetfileProof(WorkflowTask consumer,
                                                  CombinedPass adoptedPass,
                                                  List<String> producerTargets,
                                                  String gateAdvice) {
        String headline = consumer != null
                ? "Setfile dieser Kachel „" + consumer.getName() + "“"
                : "Setfile";
        return buildConsumerSetfileProof(consumer, adoptedPass, producerTargets, gateAdvice, headline);
    }

    private static SetfileProof buildConsumerSetfileProof(WorkflowTask consumer,
                                                          CombinedPass adoptedPass,
                                                          List<String> producerTargets,
                                                          String gateAdvice,
                                                          String headline) {
        List<String> lines = new ArrayList<>();
        lines.add("Mechanik: Max-Score-Pass wird übernommen; bei klarer Filter-Empfehlung "
                + "wird Use_* zusätzlich auf AN/AUS im Setfile der nächsten Kachel geschrieben.");
        if (gateAdvice != null && !gateAdvice.isBlank()) {
            lines.add("Empfehlung aus Analyse: " + gateAdvice);
        }
        if (consumer != null && consumer.isAdoptedFilterGateForced()) {
            lines.add("ERZWUNGEN: " + consumer.getAdoptedFilterGateParameter()
                    + "=" + consumer.getAdoptedFilterGateForcedValue()
                    + " (" + consumer.getAdoptedFilterGateVerdict() + ")");
        } else if (consumer != null && !consumer.getAdoptedFilterGateNote().isBlank()) {
            lines.add(consumer.getAdoptedFilterGateNote());
        }
        if (consumer == null) {
            return new SetfileProof(ProofStatus.MISMATCH, headline + " — fehlt", lines);
        }
        if (!consumer.isOptimizerParameterBasisAdopted()) {
            lines.add("Keine Basis-Übernahme auf dieser Kachel.");
            return new SetfileProof(ProofStatus.PENDING, headline + " — noch nicht übernommen", lines);
        }
        lines.add("Übernommen: Pass #" + consumer.getOptimizerParameterBasisPassNumber()
                + " aus „" + consumer.getOptimizerParameterBasisDatabank() + "“.");

        if (adoptedPass == null && consumer.getOptimizerParameterBasisPassNumber() >= 0) {
            lines.add("FEHLER: Übernommener Pass ist in der Databank nicht mehr vorhanden — "
                    + "Abgleich unmöglich.");
            return new SetfileProof(ProofStatus.MISMATCH, headline + " — Pass fehlt", lines);
        }

        List<EaParameter> snapshot = consumer.getOptimizerParameterSnapshot();
        if (snapshot == null || snapshot.isEmpty()) {
            lines.add("FEHLER: Snapshot/Setfile auf der Kachel ist leer.");
            return new SetfileProof(ProofStatus.MISMATCH, headline + " — Snapshot fehlt", lines);
        }

        Map<String, EaParameter> byName = indexSnapshot(snapshot);
        Map<String, String> passParams = parameterMap(adoptedPass);
        List<String> watchNames = new ArrayList<>();
        if (producerTargets != null) watchNames.addAll(producerTargets);
        for (String gate : extractGateParams(adoptedPass).keySet()) {
            if (!containsNameIgnoreCase(watchNames, gate)) watchNames.add(gate);
        }
        // Always include Use_* from snapshot even if pass missing
        for (EaParameter p : snapshot) {
            if (p != null && FilterGateAnalysisService.looksLikeUseGate(p.getName())
                    && !containsNameIgnoreCase(watchNames, p.getName())) {
                watchNames.add(p.getName());
            }
        }
        // Cap to keep UI readable: prefer gates + producer targets first
        if (watchNames.size() > 20) {
            watchNames = watchNames.subList(0, 20);
        }

        int mismatches = 0;
        int checked = 0;
        for (String name : watchNames) {
            if (name == null || name.isBlank()) continue;
            EaParameter inSet = findSnapshotParam(byName, name);
            String passValue = findParamIgnoreCase(passParams, name);
            if (inSet == null) {
                mismatches++;
                lines.add("✗ " + name + " — fehlt im Setfile-Snapshot der nächsten Kachel");
                continue;
            }
            checked++;
            String setValue = inSet.getValue();
            String expectedValue = passValue;
            if (consumer.isAdoptedFilterGateForced()
                    && FilterGateAnalysisService.gateAuditMentions(
                            consumer.getAdoptedFilterGateParameter(), name)
                    && !consumer.getAdoptedFilterGateForcedValue().isBlank()) {
                expectedValue = FilterGateAnalysisService.resolveForcedValueForGate(
                        consumer.getAdoptedFilterGateParameter(),
                        consumer.getAdoptedFilterGateForcedValue(),
                        name);
            }
            boolean valueOk = valuesMatch(expectedValue, setValue);
            boolean optOk = isTargetName(consumer, name)
                    ? inSet.isOptimizeEnabled()
                    : !inSet.isOptimizeEnabled();

            String setLine = inSet.toSetFileLine();
            if (valueOk && optOk) {
                lines.add("✓ " + name + " erwartet=" + nullDash(expectedValue)
                        + " → Setfile " + setLine);
            } else {
                mismatches++;
                String problem = !valueOk
                        ? "Wert weicht ab (erwartet=" + nullDash(expectedValue)
                        + ", Setfile=" + nullDash(setValue) + ")"
                        : (isTargetName(consumer, name)
                        ? "sollte Opt=Y sein"
                        : "sollte Opt=N (fixiert) sein");
                lines.add("✗ " + name + " — " + problem + " | " + setLine);
            }
        }

        if (checked == 0) {
            lines.add("Keine Gate-/Zielparameter zum Abgleich gefunden — zeige Zielparameter der Kachel:");
            for (String target : consumer.getOptimizerTargetParameters()) {
                EaParameter p = findSnapshotParam(byName, target);
                if (p != null) lines.add("· Ziel " + p.toSetFileLine());
            }
        }

        if (mismatches > 0) {
            return new SetfileProof(ProofStatus.MISMATCH,
                    headline + " — ABWEICHUNG (" + mismatches + ")", lines);
        }
        return new SetfileProof(ProofStatus.VERIFIED,
                headline + " — VERIFIZIERT (" + checked + " Parameter abgeglichen)", lines);
    }

    private static SetfileProof mergeProofs(SetfileProof first, SetfileProof second) {
        if (first == null || !first.isPresent()) return second != null ? second : SetfileProof.none();
        if (second == null || !second.isPresent()) return first;
        List<String> lines = new ArrayList<>(first.getLines());
        lines.add("—");
        lines.add(second.getHeadline());
        lines.addAll(second.getLines());
        ProofStatus status = first.getStatus() == ProofStatus.MISMATCH
                || second.getStatus() == ProofStatus.MISMATCH
                ? ProofStatus.MISMATCH
                : (first.getStatus() == ProofStatus.PENDING || second.getStatus() == ProofStatus.PENDING
                ? ProofStatus.PENDING
                : ProofStatus.VERIFIED);
        return new SetfileProof(status, first.getHeadline() + " + Weitergabe", lines);
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

    private static EaParameter findSnapshotParam(Map<String, EaParameter> byName, String name) {
        if (byName == null || name == null) return null;
        return byName.get(name.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean isTargetName(WorkflowTask task, String name) {
        if (task == null || name == null) return false;
        for (String t : task.getOptimizerTargetParameters()) {
            if (t != null && t.equalsIgnoreCase(name.trim())) return true;
        }
        return false;
    }

    private static boolean valuesMatch(String passValue, String setValue) {
        if (passValue == null || passValue.isBlank()) {
            return true;
        }
        if (setValue == null) return false;
        Boolean passBool = FilterGateAnalysisService.normalizeBoolean(passValue);
        Boolean setBool = FilterGateAnalysisService.normalizeBoolean(setValue);
        if (passBool != null && setBool != null) return passBool.equals(setBool);
        return passValue.trim().equalsIgnoreCase(setValue.trim());
    }

    private static boolean containsNameIgnoreCase(List<String> names, String name) {
        if (names == null || name == null) return false;
        for (String n : names) {
            if (n != null && n.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private static WorkflowTask findPreviousEnabledOptimizer(List<WorkflowTask> tasks, WorkflowTask current) {
        if (tasks == null || current == null) return null;
        WorkflowTask previous = null;
        for (WorkflowTask task : tasks) {
            if (task == current) return previous;
            if (task != null && task.isEnabled() && task.getType() == WorkflowTask.TaskType.OPTIMIZER) {
                previous = task;
            }
        }
        return null;
    }

    private static List<String> databankRouteDetails(WorkflowTask task, DatabankManager databankManager) {
        List<String> details = new ArrayList<>();
        details.add(routeLine(
                task.getSourceDatabank(),
                task.getTargetDatabank(),
                count(databankManager, task.getSourceDatabank()),
                count(databankManager, task.getTargetDatabank())));
        return details;
    }

    private static String routeLine(String source, String target, int inCount, int outCount) {
        String inPart = inCount >= 0 ? " (" + inCount + ")" : "";
        String outPart = outCount >= 0 ? " (" + outCount + ")" : "";
        return "Databank: „" + nullDash(source) + "“" + inPart
                + " → „" + nullDash(target) + "“" + outPart;
    }

    private static WorkflowTask findNextEnabledOptimizer(List<WorkflowTask> tasks, WorkflowTask current) {
        if (tasks == null || current == null) return null;
        boolean seen = false;
        for (WorkflowTask task : tasks) {
            if (task == current) {
                seen = true;
                continue;
            }
            if (!seen) continue;
            if (task != null && task.isEnabled() && task.getType() == WorkflowTask.TaskType.OPTIMIZER) {
                return task;
            }
        }
        return null;
    }

    private static boolean databankMatchesAdoption(WorkflowTask nextOptimizer,
                                                   WorkflowTask currentOptimizer,
                                                   List<WorkflowTask> allTasks) {
        String adoptedDb = normalizeDb(nextOptimizer.getOptimizerParameterBasisDatabank());
        if (adoptedDb.isEmpty()) return false;
        if (adoptedDb.equals(normalizeDb(currentOptimizer.getTargetDatabank()))) return true;
        // Common guided path: optimizer → pre_filter pick bank is the adoption source.
        int idx = allTasks.indexOf(currentOptimizer);
        if (idx < 0) return false;
        for (int i = idx + 1; i < allTasks.size(); i++) {
            WorkflowTask t = allTasks.get(i);
            if (t == null) continue;
            if (t.getType() == WorkflowTask.TaskType.OPTIMIZER) break;
            if (t.getType() == WorkflowTask.TaskType.PRE_FILTER
                    && adoptedDb.equals(normalizeDb(t.getTargetDatabank()))
                    && normalizeDb(t.getSourceDatabank()).equals(normalizeDb(currentOptimizer.getTargetDatabank()))) {
                return true;
            }
        }
        return false;
    }

    private static CombinedPass findPass(DatabankManager databankManager, String dbName, int passNumber) {
        if (databankManager == null || passNumber < 0) return null;
        for (CombinedPass pass : databankManager.getDatabank(dbName)) {
            if (pass != null && pass.getPassNumber() == passNumber) return pass;
        }
        return null;
    }

    private static int count(DatabankManager databankManager, String dbName) {
        if (databankManager == null) return 0;
        List<CombinedPass> list = databankManager.getDatabank(dbName);
        return list != null ? list.size() : 0;
    }

    private static Map<String, String> parameterMap(CombinedPass pass) {
        if (pass == null) return Map.of();
        Pass bt = pass.getBacktestPass();
        if (bt == null || bt.getParameterValues() == null) return Map.of();
        return bt.getParameterValues();
    }

    private static String parameterValue(CombinedPass pass, String name) {
        return findParamIgnoreCase(parameterMap(pass), name);
    }

    private static String findParamIgnoreCase(Map<String, String> map, String name) {
        if (map == null || name == null) return null;
        if (map.containsKey(name)) return map.get(name);
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name)) return e.getValue();
        }
        return null;
    }

    private static boolean containsKeyIgnoreCase(Map<String, String> map, String name) {
        if (map == null || name == null) return false;
        for (String key : map.keySet()) {
            if (key != null && key.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    static String formatParamMap(Map<String, String> map) {
        if (map == null || map.isEmpty()) return "—";
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> e : map.entrySet()) {
            parts.add(e.getKey() + "=" + e.getValue());
        }
        return String.join(", ", parts);
    }

    static String formatCondition(FilterCondition condition) {
        if (condition == null) return "—";
        String metric = condition.getMetric() != null ? condition.getMetric().getDisplayName() : "?";
        String op = condition.getOperator() != null ? condition.getOperator().getSymbol() : "?";
        return metric + " " + op + " " + condition.getValue();
    }

    private static String statusText(WorkflowTask task) {
        if (task == null) return "?";
        if (!task.isEnabled()) return "DISABLED";
        return task.getStatus() != null ? task.getStatus().name() : "PENDING";
    }

    private static StepTone toneFor(WorkflowTask task) {
        if (task == null || !task.isEnabled()) return StepTone.IDLE;
        return switch (task.getStatus()) {
            case COMPLETED -> StepTone.OK;
            case FAILED -> StepTone.FAIL;
            case RUNNING -> StepTone.WARN;
            case DISABLED -> StepTone.IDLE;
            default -> StepTone.PENDING;
        };
    }

    private static String emptyLogOr(WorkflowTask task, String fallback) {
        if (task != null && task.getLastExecutionLog() != null && !task.getLastExecutionLog().isBlank()) {
            return task.getLastExecutionLog().trim();
        }
        return fallback;
    }

    private static String fmtScore(double score) {
        return Double.isFinite(score) ? String.format(Locale.ROOT, "%.3f", score) : "n/a";
    }

    private static String nullDash(String value) {
        return value == null || value.isBlank() ? "—" : value.trim();
    }

    private static String normalizeDb(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
