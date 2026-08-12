package com.backtester.workflow;

import com.backtester.config.EaParameter;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;
import java.math.BigDecimal;

/** Domain logic for the interactive, staged optimizer hand-off. */
public final class GuidedOptimizationService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(GuidedOptimizationService.class);

    private GuidedOptimizationService() {
    }

    /**
     * Selects the pass used by a non-interactive guided-optimization hand-off.
     * Only finite scores participate. The highest score wins; equal scores are
     * resolved by the lower MT5 pass number. The pass number is a stable result
     * identity, so this tie-break is reproducible and independent of list order
     * without inventing an additional performance signal.
     */
    public static Optional<CombinedPass> selectBestPass(List<CombinedPass> candidates) {
        if (candidates == null || candidates.isEmpty()) return Optional.empty();
        return candidates.stream()
                .filter(pass -> pass != null && pass.getBacktestPass() != null
                        && Double.isFinite(pass.getScore()))
                .min(java.util.Comparator
                        .comparingDouble(CombinedPass::getScore).reversed()
                        .thenComparingInt(CombinedPass::getPassNumber));
    }

    /** How many score leaders enter the profit/drawdown comparison. */
    public static final int ADOPTION_SHORTLIST = 10;

    /** Basis pass number recorded when a stage handed its basis on without adopting one. */
    public static final int CARRIED_BASIS_PASS_NUMBER = -1;

    /**
     * Profit per unit of drawdown of a pass, estimated from the optimizer report — the
     * same measure the reference backtest uses, so selection and monitoring point in one
     * direction.
     *
     * <p>The report holds no absolute drawdown column, but the recovery factor is
     * profit / absolute drawdown, so the drawdown can be recovered from it. Backtest and
     * forward segment are joined by summing the profits and taking the larger drawdown:
     * a drawdown does not add up across segments. This is an estimate — the true
     * drawdown of the joined equity curve can be larger — but it ranks passes the same
     * way the reference measurement does.
     */
    public static OptionalDouble estimatedReturnToDrawdown(CombinedPass pass) {
        if (pass == null) return OptionalDouble.empty();
        double profit = 0;
        double drawdown = 0;
        boolean any = false;
        for (Pass segment : new Pass[]{pass.getBacktestPass(), pass.getForwardPass()}) {
            if (segment == null) continue;
            OptionalDouble segmentDrawdown = absoluteDrawdown(segment);
            if (segmentDrawdown.isEmpty()) return OptionalDouble.empty();
            profit += segment.getProfit();
            drawdown = Math.max(drawdown, segmentDrawdown.getAsDouble());
            any = true;
        }
        if (!any || !(drawdown > 0) || !Double.isFinite(profit)) return OptionalDouble.empty();
        double ratio = profit / drawdown;
        return Double.isFinite(ratio) ? OptionalDouble.of(ratio) : OptionalDouble.empty();
    }

    private static OptionalDouble absoluteDrawdown(Pass segment) {
        double reported = segment.getDrawdown();
        if (Double.isFinite(reported) && reported > 0) return OptionalDouble.of(reported);
        double recovery = segment.getRecoveryFactor();
        double profit = segment.getProfit();
        if (!Double.isFinite(recovery) || recovery == 0 || !Double.isFinite(profit)) {
            return OptionalDouble.empty();
        }
        double derived = profit / recovery;
        return Double.isFinite(derived) && derived > 0
                ? OptionalDouble.of(derived) : OptionalDouble.empty();
    }

    /** Outcome of the adoption choice, including why nothing was adopted. */
    public static final class AdoptionChoice {
        private final CombinedPass selected;
        private final double selectedRatio;
        private final CombinedPass scoreLeader;
        private final CombinedPass shortlistLeader;
        private final double bestAvailableRatio;
        private final int shortlistSize;
        private final String note;
        private final boolean masterFloorUnverified;

        AdoptionChoice(CombinedPass selected, double selectedRatio, CombinedPass scoreLeader,
                       CombinedPass shortlistLeader, double bestAvailableRatio,
                       int shortlistSize, String note) {
            this(selected, selectedRatio, scoreLeader, shortlistLeader, bestAvailableRatio,
                    shortlistSize, note, false);
        }

        AdoptionChoice(CombinedPass selected, double selectedRatio, CombinedPass scoreLeader,
                       CombinedPass shortlistLeader, double bestAvailableRatio,
                       int shortlistSize, String note, boolean masterFloorUnverified) {
            this.selected = selected;
            this.selectedRatio = selectedRatio;
            this.scoreLeader = scoreLeader;
            this.shortlistLeader = shortlistLeader;
            this.bestAvailableRatio = bestAvailableRatio;
            this.shortlistSize = shortlistSize;
            this.note = note != null ? note : "";
            this.masterFloorUnverified = masterFloorUnverified;
        }

        public Optional<CombinedPass> getSelected() { return Optional.ofNullable(selected); }
        public double getSelectedRatio() { return selectedRatio; }
        public CombinedPass getScoreLeader() { return scoreLeader; }
        public double getBestAvailableRatio() { return bestAvailableRatio; }
        public int getShortlistSize() { return shortlistSize; }
        public String getNote() { return note; }

        /**
         * Best shortlist pass by profit/drawdown regardless of the master floor. Present
         * even when {@link #getSelected()} is empty because the floor blocked it, so a
         * caller can name the pass it is refusing.
         */
        public Optional<CombinedPass> getBestAvailable() {
            return Optional.ofNullable(selected != null ? selected : shortlistLeader);
        }

        /** True when a usable pass exists but stays below the current master basis. */
        public boolean isBlockedByMasterFloor() {
            return selected == null && shortlistLeader != null;
        }

        /**
         * True when a master floor exists but the selected pass carries no profit/drawdown
         * to compare against it. The pick then rests on the score alone — it is neither
         * "above the floor" nor "below" it, and no caller may treat it as confirmed.
         */
        public boolean isMasterFloorUnverified() {
            return masterFloorUnverified;
        }
    }

    /**
     * Picks the pass to adopt: the score decides who is eligible, profit/drawdown decides
     * among them.
     *
     * <p>Ranking by score alone let stages regress, because the score blends stability,
     * profit factor and trade count and can prefer a pass that earns less per unit of
     * risk. Ranking by profit/drawdown alone would drop those quality gates and, since
     * the reference backtest covers the same period the optimizer saw, would turn the
     * monitor into its own target. So the score leaders form a shortlist, and inside that
     * shortlist the best profit/drawdown wins.
     *
     * <p>{@link AdoptionChoice#getSelected()} stays empty when the best shortlist entry
     * is below {@code championRatio}, so no caller adopts a regression by accident. The
     * pass itself remains reachable through {@link AdoptionChoice#getBestAvailable()} for
     * callers that must keep the chain moving.
     */
    public static AdoptionChoice chooseAdoptionPass(List<CombinedPass> candidates,
                                                    int shortlistSize,
                                                    double championRatio) {
        List<CombinedPass> ranked = candidates == null ? List.of() : candidates.stream()
                .filter(pass -> pass != null && pass.getBacktestPass() != null
                        && Double.isFinite(pass.getScore()))
                .sorted(java.util.Comparator
                        .comparingDouble(CombinedPass::getScore).reversed()
                        .thenComparingInt(CombinedPass::getPassNumber))
                .collect(Collectors.toList());
        if (ranked.isEmpty()) {
            return new AdoptionChoice(null, Double.NaN, null, null, Double.NaN, 0,
                    "Keine bewertbare Strategie vorhanden.");
        }

        int limit = Math.max(1, Math.min(shortlistSize, ranked.size()));
        List<CombinedPass> shortlist = ranked.subList(0, limit);
        CombinedPass scoreLeader = ranked.get(0);

        CombinedPass best = null;
        double bestRatio = Double.NaN;
        for (CombinedPass pass : shortlist) {
            OptionalDouble ratio = estimatedReturnToDrawdown(pass);
            if (ratio.isEmpty()) continue;
            if (best == null || ratio.getAsDouble() > bestRatio) {
                best = pass;
                bestRatio = ratio.getAsDouble();
            }
        }

        if (best == null) {
            // No usable drawdown anywhere in the shortlist: the score stays the fallback
            // instead of refusing to continue over a missing report column.
            boolean floorUnverified = Double.isFinite(championRatio);
            return new AdoptionChoice(scoreLeader, Double.NaN, scoreLeader, scoreLeader, Double.NaN, limit,
                    "Profit/DD ist für keine der " + limit
                            + " Score-besten Strategien bestimmbar; es entscheidet der Score."
                            + (floorUnverified
                                    ? String.format(Locale.US, " Die Master-Basis (%.2f) ist damit "
                                            + "für diese Stufe nicht überprüfbar.", championRatio)
                                    : ""),
                    floorUnverified);
        }

        if (Double.isFinite(championRatio) && bestRatio < championRatio) {
            return new AdoptionChoice(null, bestRatio, scoreLeader, best, bestRatio, limit,
                    String.format(Locale.US,
                            "Keine der %d Score-besten Strategien erreicht das Profit/DD der "
                                    + "aktuellen Master-Basis (beste %.2f < Basis %.2f).",
                            limit, bestRatio, championRatio));
        }

        String note = best == scoreLeader ? ""
                : String.format(Locale.US,
                        "Score-Führer Pass #%d (Score %.3f, Profit/DD %s) zugunsten von Pass #%d "
                                + "(Score %.3f, Profit/DD %.2f) übergangen.",
                        scoreLeader.getPassNumber(), scoreLeader.getScore(),
                        estimatedReturnToDrawdown(scoreLeader).isPresent()
                                ? String.format(Locale.US, "%.2f",
                                        estimatedReturnToDrawdown(scoreLeader).getAsDouble())
                                : "n/a",
                        best.getPassNumber(), best.getScore(), bestRatio);
        return new AdoptionChoice(best, bestRatio, scoreLeader, best, bestRatio, limit, note);
    }

    /**
     * True when this optimizer sits after another enabled optimizer and therefore
     * needs a parameter basis from the previous stage (regardless of whether one
     * was already adopted earlier).
     */
    public static boolean isFollowUpOptimizer(CustomProject project, WorkflowTask optimizerTask) {
        if (project == null || optimizerTask == null
                || optimizerTask.getType() != WorkflowTask.TaskType.OPTIMIZER
                || optimizerTask.getOptimizerTargetParameters().isEmpty()) {
            return false;
        }
        boolean previousEnabledOptimizer = false;
        for (WorkflowTask task : project.getTasks()) {
            if (task == optimizerTask) return previousEnabledOptimizer;
            if (task != null && task.isEnabled() && task.getType() == WorkflowTask.TaskType.OPTIMIZER) {
                previousEnabledOptimizer = true;
            }
        }
        return false;
    }

    /**
     * Returns whether an optimizer is an interactive follow-up stage whose
     * parameter basis still has to be adopted from an earlier optimizer.
     */
    public static boolean requiresAdoptedBasis(CustomProject project, WorkflowTask optimizerTask) {
        return isFollowUpOptimizer(project, optimizerTask)
                && !optimizerTask.isOptimizerParameterBasisAdopted();
    }

    /**
     * True when {@code pass} from {@code databankName} is recorded as the adopted
     * parameter basis of any optimizer task in the project.
     */
    public static boolean isAdoptedBasisPass(CustomProject project, String databankName, CombinedPass pass) {
        if (project == null || pass == null || databankName == null || databankName.isBlank()) {
            return false;
        }
        String db = databankName.trim();
        int passNumber = pass.getPassNumber();
        for (WorkflowTask task : project.getTasks()) {
            if (task == null || !task.isOptimizerParameterBasisAdopted()) continue;
            if (task.getOptimizerParameterBasisPassNumber() != passNumber) continue;
            if (db.equalsIgnoreCase(task.getOptimizerParameterBasisDatabank())) {
                return true;
            }
        }
        return false;
    }

    /** Names of consumer tasks that adopted this pass from the given databank (for tooltips). */
    public static List<String> adoptedBasisConsumerNames(CustomProject project, String databankName, CombinedPass pass) {
        if (project == null || pass == null || databankName == null || databankName.isBlank()) {
            return List.of();
        }
        String db = databankName.trim();
        int passNumber = pass.getPassNumber();
        List<String> names = new ArrayList<>();
        for (WorkflowTask task : project.getTasks()) {
            if (task == null || !task.isOptimizerParameterBasisAdopted()) continue;
            if (task.getOptimizerParameterBasisPassNumber() != passNumber) continue;
            if (!db.equalsIgnoreCase(task.getOptimizerParameterBasisDatabank())) continue;
            if (task.getName() != null && !task.getName().isBlank()) {
                names.add(task.getName().trim());
            }
        }
        return names;
    }

    public static Optional<WorkflowTask> findPreviousEnabledOptimizer(CustomProject project,
                                                                      WorkflowTask optimizerTask) {
        if (project == null || optimizerTask == null) return Optional.empty();
        WorkflowTask previous = null;
        for (WorkflowTask task : project.getTasks()) {
            if (task == optimizerTask) {
                return Optional.ofNullable(previous);
            }
            if (task != null && task.isEnabled() && task.getType() == WorkflowTask.TaskType.OPTIMIZER) {
                previous = task;
            }
        }
        return Optional.empty();
    }

    /** Clears inherited pass lineage so a reset project cannot reuse stale hand-picks. */
    public static int clearAdoptedBasesForRestart(CustomProject project) {
        if (project == null) return 0;
        int cleared = 0;
        for (WorkflowTask task : project.getTasks()) {
            if (task == null || task.getType() != WorkflowTask.TaskType.OPTIMIZER
                    || (!task.isOptimizerParameterBasisAdopted()
                    && task.getOptimizerParameterBasisPassNumber() < 0
                    && task.getOptimizerParameterBasisDatabank().isEmpty()
                    && task.getAdoptedFilterGateParameter().isEmpty())) {
                continue;
            }
            task.setOptimizerParameterBasisAdopted(false);
            task.setOptimizerParameterBasisPassNumber(-1);
            task.setOptimizerParameterBasisDatabank("");
            task.clearAdoptedFilterGateAudit();
            cleared++;
        }
        return cleared;
    }

    /**
     * Clears adoption lineage that points at {@code databankName} (source or
     * recorded basis databank) after a single-tab wipe.
     */
    public static int clearAdoptedBasesForDatabank(CustomProject project, String databankName) {
        if (project == null || databankName == null || databankName.isBlank()) return 0;
        String needle = databankName.trim();
        int cleared = 0;
        for (WorkflowTask task : project.getTasks()) {
            if (task == null || task.getType() != WorkflowTask.TaskType.OPTIMIZER) continue;
            boolean touches = needle.equalsIgnoreCase(task.getOptimizerParameterBasisDatabank())
                    || needle.equalsIgnoreCase(task.getSourceDatabank());
            if (!touches) continue;
            if (!task.isOptimizerParameterBasisAdopted()
                    && task.getOptimizerParameterBasisPassNumber() < 0
                    && task.getOptimizerParameterBasisDatabank().isEmpty()
                    && task.getAdoptedFilterGateParameter().isEmpty()) {
                continue;
            }
            task.setOptimizerParameterBasisAdopted(false);
            task.setOptimizerParameterBasisPassNumber(-1);
            task.setOptimizerParameterBasisDatabank("");
            task.clearAdoptedFilterGateAudit();
            cleared++;
        }
        return cleared;
    }

    /**
     * After databanks are wiped, completed tiles must not keep showing FERTIG.
     * Returns how many tasks were moved back to PENDING.
     */
    public static int resetTaskStatusesAfterDatabankWipe(CustomProject project,
                                                        boolean allDatabanks,
                                                        String databankName) {
        if (project == null || project.getTasks() == null) return 0;
        String needle = allDatabanks || databankName == null || databankName.isBlank()
                ? null
                : databankName.trim().toLowerCase(Locale.ROOT);
        int reset = 0;
        for (WorkflowTask task : project.getTasks()) {
            if (task == null || task.getStatus() == WorkflowTask.TaskStatus.DISABLED) continue;
            if (!allDatabanks && !taskTouchesDatabank(task, needle)) continue;
            if (task.getStatus() == WorkflowTask.TaskStatus.COMPLETED
                    || task.getStatus() == WorkflowTask.TaskStatus.FAILED
                    || task.getStatus() == WorkflowTask.TaskStatus.RUNNING) {
                task.setStatus(WorkflowTask.TaskStatus.PENDING);
                reset++;
            }
            task.getOutputPasses().clear();
            task.setLastExecutionLog("");
        }
        if (allDatabanks) {
            clearAdoptedBasesForRestart(project);
        } else {
            clearAdoptedBasesForDatabank(project, databankName);
        }
        return reset;
    }

    private static boolean taskTouchesDatabank(WorkflowTask task, String normalizedDb) {
        if (task == null || normalizedDb == null) return false;
        return normalizedDb.equals(safeDb(task.getSourceDatabank()))
                || normalizedDb.equals(safeDb(task.getTargetDatabank()))
                || normalizedDb.equals(safeDb(task.getOptimizerParameterBasisDatabank()));
    }

    private static String safeDb(String name) {
        return name == null || name.isBlank() ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * After a max-score pass was adopted into {@code consumer}, analyse each
     * producer Use_* stage target and force decisive AN/AUS values into the
     * consumer snapshot. Soft-fails (missing snapshot/gate) — never throws.
     */
    public static FilterGateForceResult applyFilterGateRecommendation(WorkflowTask producer,
                                                                      WorkflowTask consumer,
                                                                      String producerOutputDirectory,
                                                                      DatabankManager databankManager) {
        if (consumer == null) {
            return FilterGateForceResult.none("Kein Ziel-Optimizer.");
        }
        if (producer == null) {
            consumer.clearAdoptedFilterGateAudit();
            return FilterGateForceResult.none("Kein vorheriger Optimizer für Filter-Analyse.");
        }

        FilterGateAnalysisService.PassLoadResult loaded = FilterGateAnalysisService.loadPassesForTask(
                producer, producerOutputDirectory, databankManager);
        List<String> candidates = FilterGateAnalysisService.listGateParameterCandidates(
                producer, loaded.getPasses());
        if (candidates.isEmpty()) {
            consumer.recordAdoptedFilterGate("", "GATE_MISSING", "", false,
                    Double.NaN, Double.NaN,
                    "Kein Use_*-Gate in der Vorstufe — nur Pass-Parameter übernommen.");
            return FilterGateForceResult.none("Kein Use_*-Gate in Vorstufe „" + producer.getName() + "“.");
        }

        List<String> gates = FilterGateAnalysisService.selectGatesForAnalysis(producer, candidates);
        if (gates.isEmpty()) {
            consumer.recordAdoptedFilterGate("", "GATE_MISSING", "", false,
                    Double.NaN, Double.NaN,
                    "Kein Use_*-Zielparameter in Vorstufe „" + producer.getName()
                            + "“ — keine Filter-Erzwingung (Report-only Use_* werden ignoriert).");
            return FilterGateForceResult.none("Kein Use_*-Zielparameter in Vorstufe „"
                    + producer.getName() + "“.");
        }

        List<String> optimizedNames = FilterGateAnalysisService.listOptimizedParameterNames(
                producer, loaded.getPasses());
        List<String> noteParts = new ArrayList<>();
        List<String> forcedGates = new ArrayList<>();
        List<String> forcedValues = new ArrayList<>();
        List<String> appliedLines = new ArrayList<>();
        String lastVerdict = "";
        String firstForcedVerdict = null;
        double onMed = Double.NaN;
        double offMed = Double.NaN;
        Boolean primaryForcedOn = null;
        boolean anyForced = false;

        for (String gate : gates) {
            FilterGateAnalysisService.FilterGateAnalysis analysis = FilterGateAnalysisService.analyze(
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
            lastVerdict = analysis.getVerdict().name();
            if (analysis.getOnStats() != null) {
                onMed = analysis.getOnStats().getMedianScore();
            }
            if (analysis.getOffStats() != null) {
                offMed = analysis.getOffStats().getMedianScore();
            }

            if (analysis.getVerdict() == FilterGateAnalysisService.Verdict.FILTER_ON_BETTER
                    || analysis.getVerdict() == FilterGateAnalysisService.Verdict.FILTER_OFF_BETTER) {
                boolean on = analysis.getVerdict() == FilterGateAnalysisService.Verdict.FILTER_ON_BETTER;
                Optional<String> applied = tryForceGateValue(consumer, gate, on);
                if (applied.isPresent()) {
                    anyForced = true;
                    if (primaryForcedOn == null) primaryForcedOn = on;
                    if (firstForcedVerdict == null) firstForcedVerdict = analysis.getVerdict().name();
                    forcedGates.add(gate);
                    forcedValues.add(on ? "true" : "false");
                    appliedLines.add(applied.get());
                    noteParts.add("Filter " + (on ? "AN" : "AUS") + " erzwungen: „" + gate + "“="
                            + (on ? "true" : "false") + " (Vorstufe „" + producer.getName() + "“). "
                            + analysis.getVerdictMessage());
                } else {
                    noteParts.add("Filter-Empfehlung für „" + gate + "“ konnte nicht geschrieben werden "
                            + "(fehlt im Setfile-Snapshot) — Pass-Wert belassen. "
                            + analysis.getVerdictMessage());
                }
            } else {
                String passValue = readGateFromSnapshot(consumer, gate);
                noteParts.add("Filter „" + gate + "“ nicht eindeutig — Pass-Wert beibehalten"
                        + (passValue.isBlank() ? "." : " (" + gate + "=" + passValue + "). ")
                        + analysis.getVerdictMessage());
            }
        }

        String auditGate = forcedGates.isEmpty()
                ? (gates.size() == 1 ? gates.get(0) : String.join(", ", gates))
                : String.join(", ", forcedGates);
        String auditValue = forcedValues.isEmpty() ? "" : String.join(", ", forcedValues);
        String verdict;
        if (forcedGates.size() > 1) {
            verdict = "MULTI_GATE";
        } else if (forcedGates.size() == 1) {
            verdict = firstForcedVerdict != null ? firstForcedVerdict : lastVerdict;
        } else {
            verdict = lastVerdict;
        }
        String note = String.join(" ", noteParts);
        consumer.recordAdoptedFilterGate(auditGate, verdict, auditValue, anyForced, onMed, offMed, note);
        return new FilterGateForceResult(
                auditGate,
                primaryForcedOn,
                anyForced,
                verdict,
                String.join(" | ", appliedLines),
                note,
                onMed,
                offMed,
                auditValue);
    }

    /** Soft-fail: missing snapshot or gate does not abort the workflow. */
    private static Optional<String> tryForceGateValue(WorkflowTask consumer, String gateName, boolean on) {
        String value = on ? "true" : "false";
        List<EaParameter> snapshot = consumer.getOptimizerParameterSnapshot();
        if (snapshot == null || snapshot.isEmpty()) {
            return Optional.empty();
        }
        EaParameter match = null;
        for (EaParameter parameter : snapshot) {
            if (parameter == null || parameter.getName() == null) continue;
            if (parameter.getName().trim().equalsIgnoreCase(gateName.trim())) {
                match = parameter;
                break;
            }
        }
        if (match == null) {
            return Optional.empty();
        }
        match.setValue(value);
        // Keep fixed unless this follow-up stage itself re-optimizes the same gate.
        boolean isTarget = false;
        for (String target : consumer.getOptimizerTargetParameters()) {
            if (target != null && target.equalsIgnoreCase(gateName)) {
                isTarget = true;
                break;
            }
        }
        if (!isTarget) {
            match.setOptimizeEnabled(false);
        }
        consumer.setOptimizerParameterSnapshot(snapshot);
        return Optional.of(match.toSetFileLine());
    }

    private static String readGateFromSnapshot(WorkflowTask consumer, String gateName) {
        if (consumer == null || gateName == null) return "";
        for (EaParameter parameter : consumer.getOptimizerParameterSnapshot()) {
            if (parameter != null && parameter.getName() != null
                    && parameter.getName().trim().equalsIgnoreCase(gateName.trim())) {
                return parameter.getValue() != null ? parameter.getValue().trim() : "";
            }
        }
        return "";
    }

    public static final class FilterGateForceResult {
        private final String gateParameter;
        private final Boolean forcedOn;
        private final boolean forced;
        private final String verdict;
        private final String setfileLineOrValue;
        private final String note;
        private final double onMedianScore;
        private final double offMedianScore;
        private final String forcedValueText;

        private FilterGateForceResult(String gateParameter,
                                      Boolean forcedOn,
                                      boolean forced,
                                      String verdict,
                                      String setfileLineOrValue,
                                      String note,
                                      double onMedianScore,
                                      double offMedianScore) {
            this(gateParameter, forcedOn, forced, verdict, setfileLineOrValue, note,
                    onMedianScore, offMedianScore, "");
        }

        private FilterGateForceResult(String gateParameter,
                                      Boolean forcedOn,
                                      boolean forced,
                                      String verdict,
                                      String setfileLineOrValue,
                                      String note,
                                      double onMedianScore,
                                      double offMedianScore,
                                      String forcedValueText) {
            this.gateParameter = gateParameter != null ? gateParameter : "";
            this.forcedOn = forcedOn;
            this.forced = forced;
            this.verdict = verdict != null ? verdict : "";
            this.setfileLineOrValue = setfileLineOrValue != null ? setfileLineOrValue : "";
            this.note = note != null ? note : "";
            this.onMedianScore = onMedianScore;
            this.offMedianScore = offMedianScore;
            this.forcedValueText = forcedValueText != null ? forcedValueText : "";
        }

        public static FilterGateForceResult none(String note) {
            return new FilterGateForceResult("", null, false, "", "", note, Double.NaN, Double.NaN, "");
        }

        public String getGateParameter() { return gateParameter; }
        public Boolean getForcedOn() { return forcedOn; }
        public boolean isForced() { return forced; }
        public String getVerdict() { return verdict; }
        public String getSetfileLineOrValue() { return setfileLineOrValue; }
        public String getNote() { return note; }
        public double getOnMedianScore() { return onMedianScore; }
        public double getOffMedianScore() { return offMedianScore; }
        public String getForcedValueText() { return forcedValueText; }

        /** Display fragment for logs/banners (supports multi-gate). */
        public String getForcedDisplay() {
            if (!forced) return "";
            if (!forcedValueText.isBlank()) {
                return gateParameter + "=" + forcedValueText;
            }
            if (forcedOn == null) return gateParameter;
            return gateParameter + "=" + (forcedOn ? "true (AN)" : "false (AUS)");
        }
    }

    /**
     * Finds the last enabled task writing the selected databank and then the
     * next enabled optimizer below it in the configured task order.
     */
    public static Optional<WorkflowTask> findNextActiveOptimizer(CustomProject project, String databankName) {
        if (project == null || databankName == null || databankName.isBlank()) return Optional.empty();

        List<WorkflowTask> tasks = project.getTasks();
        int producerIndex = -1;
        for (int i = 0; i < tasks.size(); i++) {
            WorkflowTask task = tasks.get(i);
            if (task != null && task.isEnabled()
                    && databankName.trim().equalsIgnoreCase(task.getTargetDatabank())) {
                producerIndex = i;
            }
        }
        if (producerIndex < 0) return Optional.empty();

        for (int i = producerIndex + 1; i < tasks.size(); i++) {
            WorkflowTask task = tasks.get(i);
            if (task != null && task.isEnabled() && task.getType() == WorkflowTask.TaskType.OPTIMIZER) {
                return Optional.of(task);
            }
        }
        return Optional.empty();
    }

    /**
     * Builds and stores the exact parameter snapshot for the next optimizer.
     * No caller-owned EA parameter is mutated.
     */
    public static AdoptionResult adoptPassParameters(CustomProject project,
                                                      List<EaParameter> currentParameters,
                                                      CombinedPass selectedPass,
                                                      String databankName) {
        return adoptPassParameters(project, currentParameters, null, selectedPass, databankName);
    }

    /**
     * Variant using a fully reconstructed pass preset for all fixed values while
     * retaining the target template's optimization ranges.
     */
    public static AdoptionResult adoptPassParameters(CustomProject project,
                                                      List<EaParameter> targetTemplate,
                                                      List<EaParameter> resolvedPassParameters,
                                                      CombinedPass selectedPass,
                                                      String databankName) {
        PreparedAdoption prepared = prepareAdoption(
                project, targetTemplate, resolvedPassParameters, selectedPass, databankName);
        WorkflowTask nextOptimizer = prepared.nextOptimizer;
        nextOptimizer.setOptimizerParameterSnapshot(prepared.parameters);
        nextOptimizer.setOptimizerParameterBasisAdopted(true);
        nextOptimizer.setOptimizerParameterBasisPassNumber(prepared.passNumber);
        nextOptimizer.setOptimizerParameterBasisDatabank(databankName);
        return new AdoptionResult(nextOptimizer, prepared.parameters, prepared.passNumber,
                prepared.adoptedParameterCount, prepared.enabledTargetCount,
                prepared.bandAdjustments, prepared.schemaDrift);
    }

    /**
     * Hands the proven basis to the next optimizer <em>unchanged</em>: no pass value is
     * taken over, only this stage's own targets are opened and their search bands are
     * aligned to the values already in place.
     *
     * <p>This is what a stage that produced nothing above the master floor is worth. The
     * alternative — adopting its best pass anyway — makes the regression the new
     * reference, and since the floor moves with it, the loss accumulates across stages
     * without any lower bound.
     *
     * <p>{@code provenBasis} is applied as inherited values, not merely as a fallback
     * template: the consumer usually already carries a stage snapshot that the guided
     * factory pre-seeded with the original preset, and that snapshot would otherwise win
     * and silently undo everything the chain has proven so far. Its search bands are still
     * the ones that count — only the values come from the proven master.
     */
    public static AdoptionResult carryBasisToNextOptimizer(CustomProject project,
                                                           List<EaParameter> provenBasis,
                                                           String databankName) {
        PreparedAdoption prepared = prepareBasis(
                project, provenBasis, provenBasis, Map.of(), CARRIED_BASIS_PASS_NUMBER, databankName);
        WorkflowTask nextOptimizer = prepared.nextOptimizer;
        nextOptimizer.setOptimizerParameterSnapshot(prepared.parameters);
        nextOptimizer.setOptimizerParameterBasisAdopted(true);
        nextOptimizer.setOptimizerParameterBasisPassNumber(CARRIED_BASIS_PASS_NUMBER);
        nextOptimizer.setOptimizerParameterBasisDatabank(databankName);
        return new AdoptionResult(nextOptimizer, prepared.parameters, prepared.passNumber,
                prepared.adoptedParameterCount, prepared.enabledTargetCount,
                prepared.bandAdjustments, prepared.schemaDrift);
    }

    /** Dry-run of {@link #carryBasisToNextOptimizer}, for the lineage summary. */
    public static AdoptionPreview previewBasisCarryOver(CustomProject project,
                                                        List<EaParameter> provenBasis,
                                                        String databankName) {
        return toPreview(prepareBasis(project, provenBasis, provenBasis, Map.of(),
                CARRIED_BASIS_PASS_NUMBER, databankName), project, databankName);
    }

    /**
     * Dry-run of a hand-pick adoption: value changes (old → new) without mutating
     * the project/task. Used by the confirmation dialog.
     */
    public static AdoptionPreview previewPassAdoption(CustomProject project,
                                                      List<EaParameter> targetTemplate,
                                                      List<EaParameter> resolvedPassParameters,
                                                      CombinedPass selectedPass,
                                                      String databankName) {
        PreparedAdoption prepared = prepareAdoption(
                project, targetTemplate, resolvedPassParameters, selectedPass, databankName);
        return toPreview(prepared, project, databankName);
    }

    private static AdoptionPreview toPreview(PreparedAdoption prepared,
                                             CustomProject project,
                                             String databankName) {
        return new AdoptionPreview(
                prepared.nextOptimizer,
                prepared.producerStageName,
                prepared.passNumber,
                safe(databankName),
                prepared.enabledTargetCount,
                prepared.adoptedParameterCount,
                prepared.passParameters,
                prepared.passValueChanges,
                prepared.otherBasisValueChanges,
                prepared.bandAdjustments,
                listStaleDownstreamDatabanks(project, prepared.nextOptimizer, databankName));
    }

    private static PreparedAdoption prepareAdoption(CustomProject project,
                                                    List<EaParameter> targetTemplate,
                                                    List<EaParameter> resolvedPassParameters,
                                                    CombinedPass selectedPass,
                                                    String databankName) {
        if (selectedPass == null || selectedPass.getBacktestPass() == null) {
            throw new IllegalArgumentException("Der ausgewählte Pass enthält keine Backtest-Parameter.");
        }
        Pass backtestPass = selectedPass.getBacktestPass();
        Map<String, String> selectedValues = backtestPass.getParameterValues();
        if (selectedValues == null || selectedValues.isEmpty()) {
            throw new IllegalArgumentException("Der ausgewählte Pass enthält keine Parameterwerte.");
        }
        return prepareBasis(project, targetTemplate, resolvedPassParameters, selectedValues,
                backtestPass.getPassNumber(), databankName);
    }

    /**
     * Shared core of adoption and carry-over. {@code selectedValues} empty means the
     * basis is handed on untouched; only the consumer's targets are opened.
     */
    private static PreparedAdoption prepareBasis(CustomProject project,
                                                 List<EaParameter> targetTemplate,
                                                 List<EaParameter> resolvedPassParameters,
                                                 Map<String, String> selectedValues,
                                                 int passNumber,
                                                 String databankName) {
        WorkflowTask nextOptimizer = findNextActiveOptimizer(project, databankName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Nach der Databank '" + safe(databankName) + "' ist kein aktiver Optimizer-Task vorhanden."));

        List<EaParameter> taskTemplate = nextOptimizer.getOptimizerParameterSnapshot();
        List<EaParameter> currentParameters = !taskTemplate.isEmpty() ? taskTemplate : targetTemplate;
        if (currentParameters == null || currentParameters.isEmpty()) {
            throw new IllegalArgumentException("Für den Expert Advisor sind keine EA-Parameter geladen.");
        }

        List<String> targets = normalizeNames(nextOptimizer.getOptimizerTargetParameters());
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("Im nächsten Optimizer-Task '" + nextOptimizer.getName()
                    + "' sind keine Ziel-Parameter ausgewählt.");
        }

        List<EaParameter> prepared = new ArrayList<>();
        Map<String, EaParameter> byName = new LinkedHashMap<>();
        Map<String, String> oldValues = new LinkedHashMap<>();
        Set<String> duplicateNames = new LinkedHashSet<>();
        for (EaParameter original : currentParameters) {
            if (original == null) continue;
            EaParameter copy = original.copy();
            copy.setOptimizeEnabled(false);
            prepared.add(copy);
            if (copy.getName() != null && !copy.getName().isBlank()) {
                String key = normalizeKey(copy.getName());
                if (byName.putIfAbsent(key, copy) != null) duplicateNames.add(copy.getName().trim());
                else oldValues.put(key, nullToEmpty(copy.getValue()));
            }
        }
        if (!duplicateNames.isEmpty()) {
            throw new IllegalArgumentException("Doppelte Parameternamen in der EA-Konfiguration: "
                    + String.join(", ", duplicateNames));
        }

        Set<String> inheritedKeys = new LinkedHashSet<>();
        Set<String> missingInStage = new LinkedHashSet<>();
        if (resolvedPassParameters != null) {
            for (EaParameter inherited : resolvedPassParameters) {
                if (inherited == null || inherited.getName() == null) continue;
                inheritedKeys.add(normalizeKey(inherited.getName()));
                EaParameter parameter = byName.get(normalizeKey(inherited.getName()));
                if (parameter == null) {
                    if (!inherited.isSectionHeader()) missingInStage.add(inherited.getName().trim());
                    continue;
                }
                if (inherited.getValue() != null && !inherited.getValue().isBlank()) {
                    parameter.setValue(inherited.getValue());
                    EaParameter.sanitizeTimeframeFieldsForSetFile(parameter);
                }
            }
        }

        // Only for the carry: there the inherited values are the confirmed master and are
        // meant to cover the stage completely, so anything left over is real drift. During a
        // normal adoption the pass preset covers only part of the basis by design, and
        // reporting that would drown the genuine case in noise.
        BasisSchemaDrift schemaDrift = BasisSchemaDrift.NONE;
        if (selectedValues.isEmpty() && resolvedPassParameters != null) {
            Set<String> missingInBasis = new LinkedHashSet<>();
            for (EaParameter parameter : prepared) {
                if (parameter == null || parameter.isSectionHeader()) continue;
                if (parameter.getName() == null || parameter.getName().isBlank()) continue;
                if (!inheritedKeys.contains(normalizeKey(parameter.getName()))) {
                    missingInBasis.add(parameter.getName().trim());
                }
            }
            schemaDrift = new BasisSchemaDrift(missingInStage, missingInBasis);
            if (!schemaDrift.isEmpty()) {
                log.warn("Schema-Drift beim Weiterreichen an '{}': {}",
                        nextOptimizer.getName(), schemaDrift.describe());
            }
        }

        Set<String> missingSelected = new LinkedHashSet<>();
        int adoptedCount = 0;
        for (Map.Entry<String, String> entry : selectedValues.entrySet()) {
            EaParameter parameter = byName.get(normalizeKey(entry.getKey()));
            if (parameter == null) {
                missingSelected.add(entry.getKey());
                continue;
            }
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                throw new IllegalArgumentException("Leerer Wert für Passparameter '" + entry.getKey() + "'.");
            }
            parameter.setValue(entry.getValue());
            EaParameter.sanitizeTimeframeFieldsForSetFile(parameter);
            adoptedCount++;
        }
        if (!missingSelected.isEmpty()) {
            throw new IllegalArgumentException("Pass und geladener Expert Advisor passen nicht zusammen. Fehlende Parameter: "
                    + String.join(", ", missingSelected));
        }

        Set<String> missingTargets = new LinkedHashSet<>();
        Set<String> invalidTargets = new LinkedHashSet<>();
        Set<String> invalidRanges = new LinkedHashSet<>();
        for (String targetName : targets) {
            EaParameter parameter = byName.get(normalizeKey(targetName));
            if (parameter == null) {
                missingTargets.add(targetName);
            } else if (parameter.isSectionHeader() || parameter.isStringType()) {
                invalidTargets.add(targetName);
            } else if (!hasUsableOptimizationRange(parameter)) {
                invalidRanges.add(targetName);
            } else {
                parameter.setOptimizeEnabled(true);
            }
        }
        if (!missingTargets.isEmpty()) {
            throw new IllegalArgumentException("Unbekannte Ziel-Parameter in Task '" + nextOptimizer.getName()
                    + "': " + String.join(", ", missingTargets));
        }
        if (!invalidTargets.isEmpty()) {
            throw new IllegalArgumentException("Nicht optimierbare Ziel-Parameter in Task '" + nextOptimizer.getName()
                    + "': " + String.join(", ", invalidTargets));
        }
        if (!invalidRanges.isEmpty()) {
            throw new IllegalArgumentException("Fehlender oder ungültiger Start/Schritt/Ende-Suchraum in Task '"
                    + nextOptimizer.getName() + "': " + String.join(", ", invalidRanges));
        }

        // The adopted value of a stage target must be a pass MT5 actually walks, otherwise
        // this stage cannot reproduce the chain's current best and may hand on a regression.
        List<ChampionSearchSpaceAligner.Adjustment> bandAdjustments =
                ChampionSearchSpaceAligner.align(prepared);

        Set<String> passKeys = new LinkedHashSet<>();
        for (String name : selectedValues.keySet()) {
            if (name != null && !name.isBlank()) passKeys.add(normalizeKey(name));
        }
        // Also surface producer stage targets if they changed — same mental model as
        // "was in this stage optimized", even if a report map is incomplete.
        Optional<WorkflowTask> producer = findPreviousEnabledOptimizer(project, nextOptimizer);
        producer.ifPresent(task -> {
            for (String name : task.getOptimizerTargetParameters()) {
                if (name != null && !name.isBlank()) passKeys.add(normalizeKey(name));
            }
        });

        List<ParameterValueChange> passParameters = new ArrayList<>();
        List<ParameterValueChange> passChanges = new ArrayList<>();
        List<ParameterValueChange> otherChanges = new ArrayList<>();
        for (EaParameter parameter : prepared) {
            if (parameter == null || parameter.isSectionHeader() || parameter.getName() == null) continue;
            String key = normalizeKey(parameter.getName());
            String oldValue = oldValues.getOrDefault(key, "");
            String newValue = nullToEmpty(parameter.getValue());
            boolean optimizedHere = passKeys.contains(key);
            if (optimizedHere) {
                // Unchanged targets stay in the list: "the stage varied it and kept the
                // value" is a different statement from "the stage never touched it".
                passParameters.add(new ParameterValueChange(
                        parameter.getName().trim(), oldValue, newValue));
            }
            if (valuesEquivalent(oldValue, newValue)) continue;
            ParameterValueChange change = new ParameterValueChange(
                    parameter.getName().trim(), oldValue, newValue);
            if (optimizedHere) {
                passChanges.add(change);
            } else {
                otherChanges.add(change);
            }
        }

        return new PreparedAdoption(
                nextOptimizer,
                producer.map(WorkflowTask::getName).orElse(""),
                prepared,
                passNumber,
                adoptedCount,
                targets.size(),
                passParameters,
                passChanges,
                otherChanges,
                bandAdjustments,
                schemaDrift);
    }

    /**
     * Databanks whose content was produced from a parameter basis that this adoption
     * replaces. They must be cleared, otherwise the resume logic — which only checks
     * whether a target databank holds strategies — silently keeps the stale results
     * and the new basis is never optimized.
     *
     * <p>Everything from {@code consumer} downwards qualifies; the databank the pick
     * came from and all upstream stages stay untouched.
     */
    public static List<String> listStaleDownstreamDatabanks(CustomProject project,
                                                            WorkflowTask consumer,
                                                            String pickedDatabank) {
        if (project == null || consumer == null) return List.of();
        Set<String> upstream = new LinkedHashSet<>();
        if (pickedDatabank != null && !pickedDatabank.isBlank()) {
            upstream.add(safeDb(pickedDatabank));
        }
        List<String> stale = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        boolean atOrAfterConsumer = false;
        for (WorkflowTask task : project.getTasks()) {
            if (task == null) continue;
            if (task == consumer) atOrAfterConsumer = true;
            if (!atOrAfterConsumer) {
                upstream.add(safeDb(task.getTargetDatabank()));
                continue;
            }
            String target = task.getTargetDatabank();
            if (target == null || target.isBlank()) continue;
            String key = safeDb(target);
            if (upstream.contains(key)) continue;
            if (seen.add(key)) stale.add(target.trim());
        }
        return stale;
    }

    /**
     * Whether two parameter values mean the same thing. {@code 1.20} and {@code 1.2} are
     * one value, not a change — every place that decides "did this parameter change" has
     * to use this rule, otherwise a display contradicts the adoption it describes.
     */
    public static boolean valuesEquivalent(String left, String right) {
        String a = EaParameter.normalizeMql5Value(nullToEmpty(left)).trim();
        String b = EaParameter.normalizeMql5Value(nullToEmpty(right)).trim();
        if (a.equals(b)) return true;
        if (a.equalsIgnoreCase(b)) return true;
        try {
            return new BigDecimal(a).compareTo(new BigDecimal(b)) == 0;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    /**
     * Parameters that the proven master and the target stage do not have in common. Every
     * one of them means the stage runs with a value that did not come from the confirmed
     * master, so the resulting mixture was never measured in that form.
     */
    public static final class BasisSchemaDrift {

        public static final BasisSchemaDrift NONE = new BasisSchemaDrift(List.of(), List.of());

        /** How many names are spelled out before the rest is only counted. */
        private static final int NAMES_IN_MESSAGE = 5;

        private final List<String> missingInStage;
        private final List<String> missingInBasis;

        private BasisSchemaDrift(Collection<String> missingInStage, Collection<String> missingInBasis) {
            this.missingInStage = missingInStage != null ? List.copyOf(missingInStage) : List.of();
            this.missingInBasis = missingInBasis != null ? List.copyOf(missingInBasis) : List.of();
        }

        /** Master parameters the target stage does not know, so their value is dropped. */
        public List<String> getMissingInStage() { return missingInStage; }

        /** Stage parameters the master does not know, so the stage template supplies them. */
        public List<String> getMissingInBasis() { return missingInBasis; }

        public boolean isEmpty() {
            return missingInStage.isEmpty() && missingInBasis.isEmpty();
        }

        /** German plain text for the run console; empty when there is nothing to report. */
        public String describe() {
            if (isEmpty()) return "";
            StringBuilder text = new StringBuilder("Die übernommene Basis und der Ziel-Task "
                    + "passen nicht vollständig zusammen. ");
            if (!missingInBasis.isEmpty()) {
                text.append(count(missingInBasis)).append(" des Ziel-Tasks ")
                        .append(missingInBasis.size() == 1 ? "kommt" : "kommen")
                        .append(" nicht in der bestätigten Master-Strategie vor und ")
                        .append(missingInBasis.size() == 1 ? "behält seinen" : "behalten ihren")
                        .append(" Wert aus der Vorlage der Stufe: ")
                        .append(names(missingInBasis)).append(". ");
            }
            if (!missingInStage.isEmpty()) {
                text.append(count(missingInStage)).append(" der Master-Strategie ")
                        .append(missingInStage.size() == 1 ? "existiert" : "existieren")
                        .append(" im Ziel-Task nicht und ")
                        .append(missingInStage.size() == 1 ? "wird" : "werden")
                        .append(" deshalb nicht übernommen: ")
                        .append(names(missingInStage)).append(". ");
            }
            text.append("Die Kette läuft mit dieser Mischung weiter, gemessen wurde sie so nie.");
            return text.toString();
        }

        private static String count(List<String> names) {
            return names.size() == 1 ? "Ein Parameter" : names.size() + " Parameter";
        }

        private static String names(List<String> names) {
            if (names.size() <= NAMES_IN_MESSAGE) return String.join(", ", names);
            return String.join(", ", names.subList(0, NAMES_IN_MESSAGE))
                    + " und " + (names.size() - NAMES_IN_MESSAGE) + " weitere";
        }
    }

    private static final class PreparedAdoption {
        private final WorkflowTask nextOptimizer;
        private final String producerStageName;
        private final List<EaParameter> parameters;
        private final int passNumber;
        private final int adoptedParameterCount;
        private final int enabledTargetCount;
        private final List<ParameterValueChange> passParameters;
        private final List<ParameterValueChange> passValueChanges;
        private final List<ParameterValueChange> otherBasisValueChanges;
        private final List<ChampionSearchSpaceAligner.Adjustment> bandAdjustments;
        private final BasisSchemaDrift schemaDrift;

        private PreparedAdoption(WorkflowTask nextOptimizer,
                                 String producerStageName,
                                 List<EaParameter> parameters,
                                 int passNumber,
                                 int adoptedParameterCount,
                                 int enabledTargetCount,
                                 List<ParameterValueChange> passParameters,
                                 List<ParameterValueChange> passValueChanges,
                                 List<ParameterValueChange> otherBasisValueChanges,
                                 List<ChampionSearchSpaceAligner.Adjustment> bandAdjustments,
                                 BasisSchemaDrift schemaDrift) {
            this.schemaDrift = schemaDrift != null ? schemaDrift : BasisSchemaDrift.NONE;
            this.nextOptimizer = nextOptimizer;
            this.producerStageName = producerStageName != null ? producerStageName : "";
            this.parameters = parameters;
            this.passNumber = passNumber;
            this.adoptedParameterCount = adoptedParameterCount;
            this.enabledTargetCount = enabledTargetCount;
            this.passParameters = passParameters != null ? passParameters : List.of();
            this.passValueChanges = passValueChanges != null ? passValueChanges : List.of();
            this.otherBasisValueChanges = otherBasisValueChanges != null
                    ? otherBasisValueChanges : List.of();
            this.bandAdjustments = bandAdjustments != null ? bandAdjustments : List.of();
        }
    }

    public static final class ParameterValueChange {
        private final String name;
        private final String oldValue;
        private final String newValue;

        public ParameterValueChange(String name, String oldValue, String newValue) {
            this.name = name != null ? name : "";
            this.oldValue = oldValue != null ? oldValue : "";
            this.newValue = newValue != null ? newValue : "";
        }

        public String getName() { return name; }
        public String getOldValue() { return oldValue; }
        public String getNewValue() { return newValue; }
    }

    public static final class AdoptionPreview {
        private final WorkflowTask nextOptimizer;
        private final String producerStageName;
        private final int passNumber;
        private final String databankName;
        private final int enabledTargetCount;
        private final int adoptedParameterCount;
        private final List<ParameterValueChange> passParameters;
        private final List<ParameterValueChange> passValueChanges;
        private final List<ParameterValueChange> otherBasisValueChanges;
        private final List<ChampionSearchSpaceAligner.Adjustment> searchSpaceAdjustments;
        private final List<String> staleDownstreamDatabanks;

        private AdoptionPreview(WorkflowTask nextOptimizer,
                                String producerStageName,
                                int passNumber,
                                String databankName,
                                int enabledTargetCount,
                                int adoptedParameterCount,
                                List<ParameterValueChange> passParameters,
                                List<ParameterValueChange> passValueChanges,
                                List<ParameterValueChange> otherBasisValueChanges,
                                List<ChampionSearchSpaceAligner.Adjustment> searchSpaceAdjustments,
                                List<String> staleDownstreamDatabanks) {
            this.nextOptimizer = nextOptimizer;
            this.producerStageName = producerStageName != null ? producerStageName : "";
            this.passNumber = passNumber;
            this.databankName = databankName != null ? databankName : "";
            this.enabledTargetCount = enabledTargetCount;
            this.adoptedParameterCount = adoptedParameterCount;
            this.passParameters = passParameters != null
                    ? List.copyOf(passParameters) : List.of();
            this.passValueChanges = passValueChanges != null
                    ? List.copyOf(passValueChanges) : List.of();
            this.otherBasisValueChanges = otherBasisValueChanges != null
                    ? List.copyOf(otherBasisValueChanges) : List.of();
            this.searchSpaceAdjustments = searchSpaceAdjustments != null
                    ? List.copyOf(searchSpaceAdjustments) : List.of();
            this.staleDownstreamDatabanks = staleDownstreamDatabanks != null
                    ? List.copyOf(staleDownstreamDatabanks) : List.of();
        }

        /** Search-band corrections that keep the adopted values reachable in this stage. */
        public List<ChampionSearchSpaceAligner.Adjustment> getSearchSpaceAdjustments() {
            return searchSpaceAdjustments;
        }

        /** Databanks that will be emptied because their basis is replaced. */
        public List<String> getStaleDownstreamDatabanks() {
            return staleDownstreamDatabanks;
        }

        public WorkflowTask getNextOptimizer() { return nextOptimizer; }
        public int getPassNumber() { return passNumber; }
        public String getDatabankName() { return databankName; }
        public int getEnabledTargetCount() { return enabledTargetCount; }
        public int getAdoptedParameterCount() { return adoptedParameterCount; }

        /** Name of the stage that produced the pass, i.e. the one that optimized it. */
        public String getProducerStageName() { return producerStageName; }

        /**
         * Every parameter the producing stage optimized, with old and new value —
         * including the ones the pass left unchanged.
         */
        public List<ParameterValueChange> getPassParameters() { return passParameters; }

        /** Pass/stage-target value changes — what the hand-pick dialog lists. */
        public List<ParameterValueChange> getValueChanges() { return passValueChanges; }

        public List<ParameterValueChange> getPassValueChanges() { return passValueChanges; }

        /**
         * Extra diffs from the full run preset vs the next-stage template.
         * Still applied on OK, but not the focus of the confirmation table.
         */
        public List<ParameterValueChange> getOtherBasisValueChanges() {
            return otherBasisValueChanges;
        }

        public int getOtherBasisChangeCount() {
            return otherBasisValueChanges.size();
        }
    }

    private static List<String> normalizeNames(List<String> names) {
        if (names == null) return List.of();
        Map<String, String> unique = new LinkedHashMap<>();
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                String trimmed = name.trim();
                unique.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private static String safe(String text) {
        return text != null ? text.trim() : "";
    }

    private static String normalizeKey(String name) {
        return name != null ? name.trim().toLowerCase(Locale.ROOT) : "";
    }

    private static boolean hasUsableOptimizationRange(EaParameter parameter) {
        if (parameter.getOptimizeStart() == null || parameter.getOptimizeStart().isBlank()
                || parameter.getOptimizeStep() == null || parameter.getOptimizeStep().isBlank()
                || parameter.getOptimizeEnd() == null || parameter.getOptimizeEnd().isBlank()) {
            return false;
        }
        try {
            return new BigDecimal(EaParameter.normalizeMql5Value(parameter.getOptimizeStep()).trim())
                    .compareTo(BigDecimal.ZERO) > 0;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public static final class AdoptionResult {
        private final WorkflowTask nextOptimizer;
        private final List<EaParameter> parameters;
        private final int passNumber;
        private final int adoptedParameterCount;
        private final int enabledTargetCount;
        private final List<ChampionSearchSpaceAligner.Adjustment> searchSpaceAdjustments;
        private final BasisSchemaDrift schemaDrift;

        private AdoptionResult(WorkflowTask nextOptimizer, List<EaParameter> parameters,
                               int passNumber, int adoptedParameterCount, int enabledTargetCount,
                               List<ChampionSearchSpaceAligner.Adjustment> searchSpaceAdjustments,
                               BasisSchemaDrift schemaDrift) {
            this.schemaDrift = schemaDrift != null ? schemaDrift : BasisSchemaDrift.NONE;
            this.nextOptimizer = nextOptimizer;
            this.parameters = parameters.stream().map(EaParameter::copy).collect(Collectors.toList());
            this.passNumber = passNumber;
            this.adoptedParameterCount = adoptedParameterCount;
            this.enabledTargetCount = enabledTargetCount;
            this.searchSpaceAdjustments = searchSpaceAdjustments != null
                    ? List.copyOf(searchSpaceAdjustments) : List.of();
        }

        public List<ChampionSearchSpaceAligner.Adjustment> getSearchSpaceAdjustments() {
            return searchSpaceAdjustments;
        }

        /** Parameters the carried basis and the target stage do not have in common. */
        public BasisSchemaDrift getSchemaDrift() { return schemaDrift; }

        public WorkflowTask getNextOptimizer() { return nextOptimizer; }
        public List<EaParameter> getParameters() {
            return parameters.stream().map(EaParameter::copy).collect(Collectors.toList());
        }
        public int getPassNumber() { return passNumber; }
        public int getAdoptedParameterCount() { return adoptedParameterCount; }
        public int getEnabledTargetCount() { return enabledTargetCount; }
    }
}
