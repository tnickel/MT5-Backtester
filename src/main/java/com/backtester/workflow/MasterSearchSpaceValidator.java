package com.backtester.workflow;

import com.backtester.config.EaParameter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Read-only safety check before MT5 starts: every target of an enabled optimizer
 * must keep the current master value inside its search bounds. Exact grid hits are
 * not required — a nearby walkable point (within one step) is enough.
 */
public final class MasterSearchSpaceValidator {

    private MasterSearchSpaceValidator() {
    }

    public record Issue(String taskName,
                        String parameterName,
                        String masterValue,
                        String start,
                        String step,
                        String end,
                        String reason) {
        public String describe() {
            return "Task '" + taskName + "', " + parameterName + " = " + masterValue
                    + " (Suchraum " + start + " / " + step + " / " + end + "): " + reason;
        }
    }

    /** Checks every enabled guided optimizer against the currently effective master. */
    public static List<Issue> validateProject(List<WorkflowTask> tasks,
                                              List<EaParameter> masterParameters,
                                              String chartPeriod) {
        List<Issue> issues = new ArrayList<>();
        if (tasks == null) return issues;
        for (WorkflowTask task : tasks) {
            if (task == null || !task.isEnabled()
                    || task.getType() != WorkflowTask.TaskType.OPTIMIZER
                    || task.getOptimizerTargetParameters().isEmpty()) {
                continue;
            }
            issues.addAll(validateTask(task, masterParameters, chartPeriod));
        }
        return issues;
    }

    /** Checks one guided optimizer. The method never changes the task or its parameters. */
    public static List<Issue> validateTask(WorkflowTask task,
                                           List<EaParameter> masterParameters,
                                           String chartPeriod) {
        List<Issue> issues = new ArrayList<>();
        if (task == null || !task.isEnabled()
                || task.getType() != WorkflowTask.TaskType.OPTIMIZER
                || task.getOptimizerTargetParameters().isEmpty()) {
            return issues;
        }

        String taskName = display(task.getName(), "(unbenannter Optimizer)");
        Map<String, EaParameter> snapshot = index(task.getOptimizerParameterSnapshot());
        Map<String, EaParameter> master = index(masterParameters);
        if (master.isEmpty()) {
            issues.add(new Issue(taskName, "(Master-Basis)", "fehlt", "-", "-", "-",
                    "keine wirksamen Master-Parameter vorhanden."));
            return issues;
        }

        for (String targetName : task.getOptimizerTargetParameters()) {
            String key = normalizeName(targetName);
            EaParameter band = snapshot.get(key);
            EaParameter incumbent = master.get(key);
            if (band == null) {
                issues.add(new Issue(taskName, targetName, value(incumbent), "-", "-", "-",
                        "Zielparameter fehlt im Optimizer-Snapshot."));
                continue;
            }
            if (incumbent == null) {
                issues.add(issue(taskName, band, "fehlt",
                        "Parameter fehlt in der Master-Basis."));
                continue;
            }
            if (band.isSectionHeader() || band.isStringType()) {
                issues.add(issue(taskName, band, value(incumbent),
                        "Section-/String-Parameter kann nicht numerisch optimiert werden."));
                continue;
            }
            if (!band.isOptimizeEnabled()) {
                issues.add(issue(taskName, band, value(incumbent),
                        "Zielparameter ist nicht mit Opt=Y aktiviert."));
                continue;
            }

            String masterValue = normalized(value(incumbent));
            String start = normalized(band.getOptimizeStart());
            String step = normalized(band.getOptimizeStep());
            String end = normalized(band.getOptimizeEnd());
            if (masterValue.isEmpty() || start.isEmpty() || step.isEmpty() || end.isEmpty()) {
                issues.add(issue(taskName, band, masterValue,
                        "Masterwert oder Start/Schritt/Ende ist leer."));
                continue;
            }

            String name = display(band.getName(), targetName);
            if (isBooleanBand(name, start, end)) {
                validateBoolean(taskName, band, masterValue, start, step, end, issues);
            } else if (EaParameter.isTimeframeParameterName(name)) {
                validateTimeframe(taskName, band, masterValue, start, step, end,
                        chartPeriod, issues);
            } else {
                validateNumeric(taskName, band, masterValue, start, step, end, issues);
            }
        }
        return issues;
    }

    public static void requireProject(List<WorkflowTask> tasks,
                                      List<EaParameter> masterParameters,
                                      String chartPeriod) {
        requireValid(validateProject(tasks, masterParameters, chartPeriod));
    }

    public static void requireTask(WorkflowTask task,
                                   List<EaParameter> masterParameters,
                                   String chartPeriod) {
        requireValid(validateTask(task, masterParameters, chartPeriod));
    }

    /**
     * Runtime guard for the exact optimizer snapshot that will be sent to MT5. First proves
     * that it still represents the independently selected active/adopted/proven basis,
     * including fixed parameters, then proves that every target can reproduce that basis.
     */
    public static List<Issue> validateRuntimeTask(WorkflowTask task,
                                                  List<EaParameter> expectedParameters,
                                                  String chartPeriod) {
        List<Issue> issues = validateSnapshotBasis(task, expectedParameters);
        issues.addAll(validateTask(task, expectedParameters, chartPeriod));
        return issues;
    }

    public static void requireRuntimeTask(WorkflowTask task,
                                          List<EaParameter> expectedParameters,
                                          String chartPeriod) {
        requireValid(validateRuntimeTask(task, expectedParameters, chartPeriod));
    }

    private static List<Issue> validateSnapshotBasis(WorkflowTask task,
                                                     List<EaParameter> expectedParameters) {
        List<Issue> issues = new ArrayList<>();
        if (task == null || !task.isEnabled()
                || task.getType() != WorkflowTask.TaskType.OPTIMIZER
                || task.getOptimizerTargetParameters().isEmpty()) {
            return issues;
        }

        String taskName = display(task.getName(), "(unbenannter Optimizer)");
        Map<String, EaParameter> snapshot = index(task.getOptimizerParameterSnapshot());
        Map<String, EaParameter> expected = index(expectedParameters);
        if (expected.isEmpty()) {
            issues.add(new Issue(taskName, "(erwartete Basis)", "fehlt", "-", "-", "-",
                    "keine unabhängige aktive/adoptierte/bestätigte Basis vorhanden."));
            return issues;
        }
        if (snapshot.isEmpty()) {
            issues.add(new Issue(taskName, "(Optimizer-Snapshot)", "fehlt", "-", "-", "-",
                    "der tatsächlich auszuführende Optimizer-Snapshot ist leer."));
            return issues;
        }

        for (Map.Entry<String, EaParameter> entry : expected.entrySet()) {
            EaParameter actual = snapshot.get(entry.getKey());
            EaParameter incumbent = entry.getValue();
            if (actual == null) {
                issues.add(new Issue(taskName, incumbent.getName(), value(incumbent),
                        "-", "-", "-", "Parameter fehlt im auszuführenden Optimizer-Snapshot."));
                continue;
            }
            if (!GuidedOptimizationService.valuesEquivalent(value(incumbent), value(actual))) {
                issues.add(new Issue(taskName, actual.getName(), value(incumbent),
                        value(actual), "-", "-",
                        "Snapshot-Wert weicht von der erwarteten aktiven Basis ab."));
            }
        }
        for (Map.Entry<String, EaParameter> entry : snapshot.entrySet()) {
            if (!expected.containsKey(entry.getKey())) {
                EaParameter actual = entry.getValue();
                issues.add(new Issue(taskName, actual.getName(), "fehlt",
                        value(actual), "-", "-",
                        "Parameter existiert im Snapshot, aber nicht in der erwarteten Basis."));
            }
        }
        return issues;
    }

    private static void requireValid(List<Issue> issues) {
        if (issues == null || issues.isEmpty()) return;
        StringBuilder message = new StringBuilder(
                "Master-Suchraumprüfung fehlgeschlagen. MT5 wurde nicht gestartet. "
                        + "Jeder Optimizer muss den Masterwert im Suchraum (Start..Ende) haben:");
        for (Issue issue : issues) {
            message.append("\n- ").append(issue.describe());
        }
        throw new IllegalStateException(message.toString());
    }

    private static void validateBoolean(String taskName,
                                        EaParameter band,
                                        String masterValue,
                                        String start,
                                        String step,
                                        String end,
                                        List<Issue> issues) {
        Boolean master = booleanValue(masterValue);
        Boolean first = booleanValue(start);
        Boolean last = booleanValue(end);
        BigDecimal stepValue = decimal(step);
        if (master == null || first == null || last == null || stepValue == null
                || stepValue.compareTo(BigDecimal.ONE) != 0) {
            issues.add(issue(taskName, band, masterValue,
                    "ungültiges Boolean-Band; erwartet false/true (oder 0/1) mit Schritt 1."));
            return;
        }
        int masterIndex = master ? 1 : 0;
        int startIndex = first ? 1 : 0;
        int endIndex = last ? 1 : 0;
        if (endIndex < startIndex || masterIndex < startIndex || masterIndex > endIndex) {
            issues.add(issue(taskName, band, masterValue,
                    "Masterwert liegt nicht im Boolean-Suchraum."));
        }
    }

    private static void validateTimeframe(String taskName,
                                          EaParameter band,
                                          String masterValue,
                                          String start,
                                          String step,
                                          String end,
                                          String chartPeriod,
                                          List<Issue> issues) {
        String effectiveMaster = EaParameter.fromTimeframeDisplay(masterValue);
        if ("0".equals(effectiveMaster)) {
            effectiveMaster = EaParameter.fromTimeframeDisplay(chartPeriod);
        }
        int masterIndex = EaParameter.timeframeEnumIndex(effectiveMaster);
        int startIndex = EaParameter.timeframeEnumIndex(start);
        int endIndex = EaParameter.timeframeEnumIndex(end);
        Integer stepWidth = integer(step);
        if (masterIndex <= 0) {
            issues.add(issue(taskName, band, masterValue,
                    "PERIOD_CURRENT kann ohne gültigen Projekt-Zeitrahmen nicht reproduziert werden."));
            return;
        }
        if (startIndex <= 0 || endIndex < startIndex || stepWidth == null || stepWidth <= 0) {
            issues.add(issue(taskName, band, masterValue,
                    "ungültiges ENUM_TIMEFRAMES-Band."));
            return;
        }
        if (masterIndex < startIndex || masterIndex > endIndex) {
            issues.add(issue(taskName, band, masterValue,
                    "Master-Timeframe liegt außerhalb des Suchraums."));
            return;
        }
    }

    private static void validateNumeric(String taskName,
                                        EaParameter band,
                                        String masterValue,
                                        String start,
                                        String step,
                                        String end,
                                        List<Issue> issues) {
        BigDecimal value = decimal(masterValue);
        BigDecimal first = decimal(start);
        BigDecimal stepWidth = decimal(step);
        BigDecimal last = decimal(end);
        if (value == null || first == null || stepWidth == null || last == null) {
            issues.add(issue(taskName, band, masterValue,
                    "Masterwert oder Suchraum ist nicht numerisch."));
            return;
        }
        if (stepWidth.signum() <= 0 || last.compareTo(first) < 0) {
            issues.add(issue(taskName, band, masterValue,
                    "Suchraum ist unbrauchbar (Schritt <= 0 oder Ende < Start)."));
            return;
        }
        if (!knownEnumDomainContains(band.getName(), value, first, last)) {
            issues.add(issue(taskName, band, masterValue,
                    "Suchraum oder Masterwert liegt außerhalb der gültigen MQL5-Enum-Domain."));
            return;
        }
        if (value.compareTo(first) < 0 || value.compareTo(last) > 0) {
            issues.add(issue(taskName, band, masterValue,
                    "Masterwert liegt außerhalb des Suchraums."));
            return;
        }
        // Off-grid is allowed. MT5 walks start + n*step; the nearest point is then
        // at most one step away, which is close enough to keep the master in play.
    }

    private static boolean knownEnumDomainContains(String name,
                                                   BigDecimal value,
                                                   BigDecimal start,
                                                   BigDecimal end) {
        String key = normalizeName(name);
        if (key.equals("envelopes_method") || key.equals("envelopes_method_lower")) {
            return insideIntegerDomain(value, 0, 3) && insideIntegerDomain(start, 0, 3)
                    && insideIntegerDomain(end, 0, 3);
        }
        if (key.equals("envelopes_price") || key.equals("envelopes_price_lower")) {
            return insideIntegerDomain(value, 1, 7) && insideIntegerDomain(start, 1, 7)
                    && insideIntegerDomain(end, 1, 7);
        }
        return true;
    }

    private static boolean insideIntegerDomain(BigDecimal value, int min, int max) {
        return value.stripTrailingZeros().scale() <= 0
                && value.compareTo(BigDecimal.valueOf(min)) >= 0
                && value.compareTo(BigDecimal.valueOf(max)) <= 0;
    }

    private static boolean isBooleanBand(String name, String start, String end) {
        String key = normalizeName(name);
        return key.startsWith("use_") || key.contains("_use_")
                || isTextBoolean(start) || isTextBoolean(end);
    }

    private static boolean isTextBoolean(String value) {
        String token = value != null ? value.trim().toLowerCase(Locale.ROOT) : "";
        return token.equals("true") || token.equals("false")
                || token.equals("yes") || token.equals("no");
    }

    private static Boolean booleanValue(String value) {
        String token = value != null ? value.trim().toLowerCase(Locale.ROOT) : "";
        return switch (token) {
            case "true", "yes", "1" -> Boolean.TRUE;
            case "false", "no", "0" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static Map<String, EaParameter> index(List<EaParameter> parameters) {
        Map<String, EaParameter> result = new LinkedHashMap<>();
        if (parameters == null) return result;
        for (EaParameter parameter : parameters) {
            if (parameter == null || parameter.isSectionHeader()
                    || parameter.getName() == null || parameter.getName().isBlank()) {
                continue;
            }
            result.putIfAbsent(normalizeName(parameter.getName()), parameter);
        }
        return result;
    }

    private static Issue issue(String taskName, EaParameter band,
                               String masterValue, String reason) {
        return new Issue(taskName,
                display(band != null ? band.getName() : null, "(unbenannter Parameter)"),
                display(masterValue, "leer"),
                display(band != null ? band.getOptimizeStart() : null, "leer"),
                display(band != null ? band.getOptimizeStep() : null, "leer"),
                display(band != null ? band.getOptimizeEnd() : null, "leer"),
                reason);
    }

    private static String value(EaParameter parameter) {
        return parameter != null ? parameter.getValue() : "";
    }

    private static String normalized(String raw) {
        return EaParameter.normalizeMql5Value(raw != null ? raw : "").trim();
    }

    private static String normalizeName(String raw) {
        return raw != null ? raw.trim().toLowerCase(Locale.ROOT) : "";
    }

    private static String display(String raw, String fallback) {
        return raw != null && !raw.isBlank() ? raw.trim() : fallback;
    }

    private static BigDecimal decimal(String raw) {
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer integer(String raw) {
        try {
            BigDecimal value = new BigDecimal(raw).stripTrailingZeros();
            return value.scale() <= 0 ? value.intValueExact() : null;
        } catch (ArithmeticException | NumberFormatException ex) {
            return null;
        }
    }
}
