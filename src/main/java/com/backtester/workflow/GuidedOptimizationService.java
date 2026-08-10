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
     * Returns whether an optimizer is an interactive follow-up stage whose
     * parameter basis still has to be adopted from an earlier optimizer.
     */
    public static boolean requiresAdoptedBasis(CustomProject project, WorkflowTask optimizerTask) {
        if (project == null || optimizerTask == null
                || optimizerTask.getType() != WorkflowTask.TaskType.OPTIMIZER
                || optimizerTask.getOptimizerTargetParameters().isEmpty()
                || optimizerTask.isOptimizerParameterBasisAdopted()) {
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

    /** Clears inherited pass lineage so a reset project cannot reuse stale hand-picks. */
    public static int clearAdoptedBasesForRestart(CustomProject project) {
        if (project == null) return 0;
        int cleared = 0;
        for (WorkflowTask task : project.getTasks()) {
            if (task == null || task.getType() != WorkflowTask.TaskType.OPTIMIZER
                    || (!task.isOptimizerParameterBasisAdopted()
                    && task.getOptimizerParameterBasisPassNumber() < 0
                    && task.getOptimizerParameterBasisDatabank().isEmpty())) {
                continue;
            }
            task.setOptimizerParameterBasisAdopted(false);
            task.setOptimizerParameterBasisPassNumber(-1);
            task.setOptimizerParameterBasisDatabank("");
            cleared++;
        }
        return cleared;
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
