package com.backtester.workflow;

import com.backtester.config.EaParameter;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.math.BigDecimal;

/** Domain logic for the interactive, staged optimizer hand-off. */
public final class GuidedOptimizationService {

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
        WorkflowTask nextOptimizer = findNextActiveOptimizer(project, databankName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Nach der Databank '" + safe(databankName) + "' ist kein aktiver Optimizer-Task vorhanden."));

        if (selectedPass == null || selectedPass.getBacktestPass() == null) {
            throw new IllegalArgumentException("Der ausgewählte Pass enthält keine Backtest-Parameter.");
        }
        Pass backtestPass = selectedPass.getBacktestPass();
        Map<String, String> selectedValues = backtestPass.getParameterValues();
        if (selectedValues == null || selectedValues.isEmpty()) {
            throw new IllegalArgumentException("Der ausgewählte Pass enthält keine Parameterwerte.");
        }
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
        Set<String> duplicateNames = new LinkedHashSet<>();
        for (EaParameter original : currentParameters) {
            if (original == null) continue;
            EaParameter copy = original.copy();
            copy.setOptimizeEnabled(false);
            prepared.add(copy);
            if (copy.getName() != null && !copy.getName().isBlank()) {
                String key = normalizeKey(copy.getName());
                if (byName.putIfAbsent(key, copy) != null) duplicateNames.add(copy.getName().trim());
            }
        }
        if (!duplicateNames.isEmpty()) {
            throw new IllegalArgumentException("Doppelte Parameternamen in der EA-Konfiguration: "
                    + String.join(", ", duplicateNames));
        }

        if (resolvedPassParameters != null) {
            for (EaParameter inherited : resolvedPassParameters) {
                if (inherited == null || inherited.getName() == null) continue;
                EaParameter parameter = byName.get(normalizeKey(inherited.getName()));
                if (parameter != null && inherited.getValue() != null && !inherited.getValue().isBlank()) {
                    parameter.setValue(inherited.getValue());
                }
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

        nextOptimizer.setOptimizerParameterSnapshot(prepared);
        nextOptimizer.setOptimizerParameterBasisAdopted(true);
        nextOptimizer.setOptimizerParameterBasisPassNumber(backtestPass.getPassNumber());
        nextOptimizer.setOptimizerParameterBasisDatabank(databankName);
        return new AdoptionResult(nextOptimizer, prepared, backtestPass.getPassNumber(), adoptedCount, targets.size());
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

        private AdoptionResult(WorkflowTask nextOptimizer, List<EaParameter> parameters,
                               int passNumber, int adoptedParameterCount, int enabledTargetCount) {
            this.nextOptimizer = nextOptimizer;
            this.parameters = parameters.stream().map(EaParameter::copy).collect(Collectors.toList());
            this.passNumber = passNumber;
            this.adoptedParameterCount = adoptedParameterCount;
            this.enabledTargetCount = enabledTargetCount;
        }

        public WorkflowTask getNextOptimizer() { return nextOptimizer; }
        public List<EaParameter> getParameters() {
            return parameters.stream().map(EaParameter::copy).collect(Collectors.toList());
        }
        public int getPassNumber() { return passNumber; }
        public int getAdoptedParameterCount() { return adoptedParameterCount; }
        public int getEnabledTargetCount() { return enabledTargetCount; }
    }
}
