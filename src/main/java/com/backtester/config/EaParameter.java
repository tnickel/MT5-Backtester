package com.backtester.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * POJO representing a single EA input parameter from a .set file.
 * 
 * MT5 .set file format:
 *   ParameterName=Value||OptStart||OptStep||OptEnd||Y/N
 *   StringParameter=StringValue  (no optimization fields)
 *   ; comment or section header
 */
public class EaParameter {

    /** ENUM_TIMEFRAMES codes → MT5-style labels, declaration order. */
    private static final Map<String, String> TIMEFRAME_CODE_TO_LABEL;
    private static final Map<String, String> TIMEFRAME_LABEL_TO_CODE;
    private static final List<String> TIMEFRAME_DISPLAY_OPTIONS;

    static {
        Map<String, String> codeToLabel = new LinkedHashMap<>();
        codeToLabel.put("0", "current");
        codeToLabel.put("1", "M1");
        codeToLabel.put("2", "M2");
        codeToLabel.put("3", "M3");
        codeToLabel.put("4", "M4");
        codeToLabel.put("5", "M5");
        codeToLabel.put("6", "M6");
        codeToLabel.put("10", "M10");
        codeToLabel.put("12", "M12");
        codeToLabel.put("15", "M15");
        codeToLabel.put("20", "M20");
        codeToLabel.put("30", "M30");
        // Hour periods are 0x4000 + hours, so H4 is 16388 and not 16384 (which is no
        // valid ENUM_TIMEFRAMES member at all). D1 = 16384 + 24 follows the same rule.
        codeToLabel.put("16385", "H1");
        codeToLabel.put("16386", "H2");
        codeToLabel.put("16387", "H3");
        codeToLabel.put("16388", "H4");
        codeToLabel.put("16390", "H6");
        codeToLabel.put("16392", "H8");
        codeToLabel.put("16396", "H12");
        codeToLabel.put("16408", "D1");
        codeToLabel.put("32769", "W1");
        codeToLabel.put("49153", "MN1");
        TIMEFRAME_CODE_TO_LABEL = Collections.unmodifiableMap(codeToLabel);

        Map<String, String> labelToCode = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : codeToLabel.entrySet()) {
            labelToCode.put(entry.getValue().toLowerCase(Locale.ROOT), entry.getKey());
        }
        labelToCode.put("period_current", "0");
        labelToCode.put("period_m1", "1");
        labelToCode.put("period_m2", "2");
        labelToCode.put("period_m3", "3");
        labelToCode.put("period_m4", "4");
        labelToCode.put("period_m5", "5");
        labelToCode.put("period_m6", "6");
        labelToCode.put("period_m10", "10");
        labelToCode.put("period_m12", "12");
        labelToCode.put("period_m15", "15");
        labelToCode.put("period_m20", "20");
        labelToCode.put("period_m30", "30");
        labelToCode.put("period_h1", "16385");
        labelToCode.put("period_h2", "16386");
        labelToCode.put("period_h3", "16387");
        labelToCode.put("period_h4", "16388");
        labelToCode.put("period_h6", "16390");
        labelToCode.put("period_h8", "16392");
        labelToCode.put("period_h12", "16396");
        labelToCode.put("period_d1", "16408");
        labelToCode.put("period_w1", "32769");
        labelToCode.put("period_mn1", "49153");
        TIMEFRAME_LABEL_TO_CODE = Collections.unmodifiableMap(labelToCode);
        TIMEFRAME_DISPLAY_OPTIONS = Collections.unmodifiableList(new ArrayList<>(codeToLabel.values()));
    }

    private String name;
    private String value;
    private String defaultValue;
    private String section;
    private String displayName;

    // Optimization fields (from .set file format)
    private String optimizeStart = "";
    private String optimizeStep = "";
    private String optimizeEnd = "";
    private boolean optimizeEnabled = false;

    /** True if this is a string parameter (no || delimiters in .set file) */
    private boolean stringType = false;

    /** The raw original line from the .set file (for preserving formatting) */
    private String rawLine = "";

    /** True if this parameter is a section header line (or comment divider) */
    private boolean sectionHeader = false;

    public EaParameter() {}

    public EaParameter(String name, String value) {
        this.name = name;
        this.value = value;
        this.defaultValue = value;
    }

    public EaParameter copy() {
        EaParameter p = new EaParameter();
        p.setName(this.name);
        p.setValue(this.value);
        p.setDefaultValue(this.defaultValue != null ? this.defaultValue : this.value);
        p.setSection(this.section);
        p.setDisplayName(this.displayName);
        p.setOptimizeStart(this.optimizeStart);
        p.setOptimizeStep(this.optimizeStep);
        p.setOptimizeEnd(this.optimizeEnd);
        p.setOptimizeEnabled(this.optimizeEnabled);
        p.setStringType(this.stringType);
        p.setRawLine(this.rawLine);
        p.setSectionHeader(this.sectionHeader);
        return p;
    }

    /**
     * Returns true if this parameter acts as a section header / category divider.
     */
    public boolean isSectionHeader() {
        if (sectionHeader) return true;
        if (name != null) {
            String n = name.trim();
            if (n.startsWith("---") || n.startsWith("===") || n.startsWith("###") || n.startsWith("----")) return true;
        }
        if (value != null) {
            String v = value.trim();
            if ((v.startsWith("---") && v.endsWith("---")) ||
                (v.startsWith("===") && v.endsWith("===")) ||
                (v.startsWith("###") && v.endsWith("###")) ||
                (v.startsWith("#######") && v.endsWith("#######")) ||
                (v.startsWith("----") && v.endsWith("----")) ||
                v.contains("--- MONEY") || v.contains("--- GRID") || v.contains("--- INDICATOR") || v.contains("--- TREND")) {
                return true;
            }
        }
        return false;
    }

    public void setSectionHeader(boolean sectionHeader) {
        this.sectionHeader = sectionHeader;
    }

    /**
     * Formats section title cleanly with folder icon and dashes like MetaTrader.
     * e.g. "📁 ---- MONEY MANAGE ----"
     */
    public String getFormattedSectionTitle() {
        String text = "";
        if (displayName != null && !displayName.trim().isEmpty() && !displayName.startsWith("Inp_Header")) {
            text = displayName;
        } else if (value != null && !value.trim().isEmpty()) {
            text = value;
        } else if (name != null) {
            text = name;
        }

        String cleaned = text.replaceAll("^[-=*#;\\s]+", "").replaceAll("[-=*#;\\s]+$", "").trim();
        if (cleaned.isEmpty() && section != null) {
            cleaned = section.replaceAll("^[-=*#;\\s]+", "").replaceAll("[-=*#;\\s]+$", "").trim();
        }
        if (cleaned.isEmpty()) {
            cleaned = "SECTION";
        }
        return "📁  ---- " + cleaned.toUpperCase() + " ----";
    }

    // --- Getters & Setters ---

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public String getOptimizeStart() { return optimizeStart; }
    public void setOptimizeStart(String optimizeStart) { this.optimizeStart = optimizeStart; }

    public String getOptimizeStep() { return optimizeStep; }
    public void setOptimizeStep(String optimizeStep) { this.optimizeStep = optimizeStep; }

    public String getOptimizeEnd() { return optimizeEnd; }
    public void setOptimizeEnd(String optimizeEnd) { this.optimizeEnd = optimizeEnd; }

    public boolean isOptimizeEnabled() { return optimizeEnabled; }
    public void setOptimizeEnabled(boolean optimizeEnabled) { this.optimizeEnabled = optimizeEnabled; }

    public boolean isStringType() { return stringType; }
    public void setStringType(boolean stringType) { this.stringType = stringType; }

    public String getRawLine() { return rawLine; }
    public void setRawLine(String rawLine) { this.rawLine = rawLine; }

    /**
     * Whether this parameter's value differs from the default.
     */
    public boolean isModified() {
        if (defaultValue == null) return false;
        return !value.equals(defaultValue);
    }

    /**
     * Resets value to default.
     */
    public void resetToDefault() {
        this.value = this.defaultValue;
    }

    public static String normalizeMql5Value(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        // UI / report labels that must never reach MT5 .set files as text.
        if (upper.equals("CURRENT") || upper.equals("CHART") || upper.startsWith("CHART/")) {
            return "0";
        }
        switch (upper) {
            case "PERIOD_CURRENT": return "0";
            case "PERIOD_M1": return "1";
            case "PERIOD_M2": return "2";
            case "PERIOD_M3": return "3";
            case "PERIOD_M4": return "4";
            case "PERIOD_M5": return "5";
            case "PERIOD_M6": return "6";
            case "PERIOD_M10": return "10";
            case "PERIOD_M12": return "12";
            case "PERIOD_M15": return "15";
            case "PERIOD_M20": return "20";
            case "PERIOD_M30": return "30";
            case "PERIOD_H1": return "16385";
            case "PERIOD_H2": return "16386";
            case "PERIOD_H3": return "16387";
            case "PERIOD_H4": return "16388";
            case "PERIOD_H6": return "16390";
            case "PERIOD_H8": return "16392";
            case "PERIOD_H12": return "16396";
            case "PERIOD_D1": return "16408";
            case "PERIOD_W1": return "32769";
            case "PERIOD_MN1": return "49153";

            case "MODE_SMA": return "0";
            case "MODE_EMA": return "1";
            case "MODE_SMMA": return "2";
            case "MODE_LWMA": return "3";

            // ENUM_APPLIED_PRICE runs 1…7, not 0-based: MQL5 numbers it from PRICE_CLOSE=1.
            case "PRICE_CLOSE": return "1";
            case "PRICE_OPEN": return "2";
            case "PRICE_HIGH": return "3";
            case "PRICE_LOW": return "4";
            case "PRICE_MEDIAN": return "5";
            case "PRICE_TYPICAL": return "6";
            case "PRICE_WEIGHTED": return "7";

            default: return raw;
        }
    }

    /** True for EA inputs that store ENUM_TIMEFRAMES codes (e.g. Inp_ATR_Timeframe). */
    public static boolean isTimeframeParameterName(String name) {
        if (name == null || name.isBlank()) return false;
        return name.toLowerCase(Locale.ROOT).contains("timeframe");
    }

    /**
     * Position of a timeframe code within the ENUM_TIMEFRAMES declaration order, or
     * {@code -1} when unknown. MT5 walks enum members by position, not by numeric code
     * ({@code M30}=30 is followed by {@code H1}=16385), so search-band arithmetic on
     * timeframe inputs has to happen on these indices.
     */
    public static int timeframeEnumIndex(String codeOrLabel) {
        String code = fromTimeframeDisplay(codeOrLabel);
        if (code == null || code.isBlank()) return -1;
        int index = 0;
        for (String known : TIMEFRAME_CODE_TO_LABEL.keySet()) {
            if (known.equals(code)) return index;
            index++;
        }
        return -1;
    }

    /** Inverse of {@link #timeframeEnumIndex(String)}; empty when out of range. */
    public static String timeframeCodeAtIndex(int index) {
        if (index < 0) return "";
        int i = 0;
        for (String known : TIMEFRAME_CODE_TO_LABEL.keySet()) {
            if (i == index) return known;
            i++;
        }
        return "";
    }

    /** MT5-style labels in ENUM_TIMEFRAMES declaration order. */
    public static List<String> timeframeDisplayOptions() {
        return timeframeDisplayOptions(null);
    }

    /**
     * Like {@link #timeframeDisplayOptions()}, but renders PERIOD_CURRENT as
     * {@code Chart/M5} (etc.) when {@code chartPeriod} is known — never the bare word
     * {@code current}, which confuses operators.
     */
    public static List<String> timeframeDisplayOptions(String chartPeriod) {
        List<String> options = new ArrayList<>(TIMEFRAME_DISPLAY_OPTIONS.size());
        for (String label : TIMEFRAME_DISPLAY_OPTIONS) {
            if ("current".equals(label)) {
                options.add(chartCurrentDisplayLabel(chartPeriod));
            } else {
                options.add(label);
            }
        }
        return Collections.unmodifiableList(options);
    }

    /**
     * Converts a stored timeframe code / PERIOD_* name / label into the MT5 UI label
     * (e.g. {@code 15} → {@code M15}). Code {@code 0} becomes {@code Chart/M5} when the
     * chart period is known, otherwise {@code Chart}.
     */
    public static String toTimeframeDisplay(String storedOrLabel) {
        return toTimeframeDisplay(storedOrLabel, null);
    }

    public static String toTimeframeDisplay(String storedOrLabel, String chartPeriod) {
        if (storedOrLabel == null || storedOrLabel.isBlank()) return "";
        String trimmed = storedOrLabel.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.equals("current") || lower.startsWith("chart/") || lower.equals("chart")
                || lower.equals("period_current")) {
            return chartCurrentDisplayLabel(chartPeriod);
        }
        String fromLabel = TIMEFRAME_LABEL_TO_CODE.get(lower);
        if (fromLabel != null) {
            if ("0".equals(fromLabel)) return chartCurrentDisplayLabel(chartPeriod);
            return TIMEFRAME_CODE_TO_LABEL.getOrDefault(fromLabel, trimmed);
        }
        String normalized = normalizeMql5Value(trimmed);
        if ("0".equals(normalized)) return chartCurrentDisplayLabel(chartPeriod);
        return TIMEFRAME_CODE_TO_LABEL.getOrDefault(normalized, trimmed);
    }

    /**
     * Converts a UI label or PERIOD_* name back to the numeric ENUM_TIMEFRAMES code
     * used in .set files. Unknown input is returned normalized as-is.
     */
    public static String fromTimeframeDisplay(String displayOrStored) {
        if (displayOrStored == null || displayOrStored.isBlank()) return "";
        String trimmed = displayOrStored.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.equals("current") || lower.equals("chart") || lower.startsWith("chart/")
                || lower.equals("period_current")) {
            return "0";
        }
        String fromLabel = TIMEFRAME_LABEL_TO_CODE.get(lower);
        if (fromLabel != null) return fromLabel;
        String normalized = normalizeMql5Value(trimmed);
        if (TIMEFRAME_CODE_TO_LABEL.containsKey(normalized)) return normalized;
        return normalized;
    }

    /**
     * Coerces boolean optimize bands from the common MT5 export pattern
     * {@code false / 0 / true} to {@code false / 1 / true} so both states are walked.
     *
     * @return true if start/step/end were changed
     */
    public static boolean normalizeBooleanOptimizeBand(EaParameter parameter) {
        if (parameter == null || parameter.isSectionHeader() || parameter.isStringType()) {
            return false;
        }
        String oldStart = parameter.getOptimizeStart() != null ? parameter.getOptimizeStart().trim() : "";
        String oldStep = parameter.getOptimizeStep() != null ? parameter.getOptimizeStep().trim() : "";
        String oldEnd = parameter.getOptimizeEnd() != null ? parameter.getOptimizeEnd().trim() : "";
        if (!isBooleanToken(oldStart) || !isBooleanToken(oldEnd)) {
            return false;
        }
        // Require at least one textual true/false so numeric bands like 0..1 are not
        // mistaken for booleans (ENUM/int ranges).
        if (!isStrictBooleanToken(oldStart) && !isStrictBooleanToken(oldEnd)) {
            return false;
        }

        String newStart = normalizeBooleanToken(oldStart);
        String newEnd = normalizeBooleanToken(oldEnd);
        String newStep = ("0".equals(oldStep) || oldStep.isBlank()) ? "1" : oldStep;

        boolean changed = !newStart.equals(oldStart) || !newStep.equals(oldStep) || !newEnd.equals(oldEnd);
        if (changed) {
            parameter.setOptimizeStart(newStart);
            parameter.setOptimizeStep(newStep);
            parameter.setOptimizeEnd(newEnd);
        }
        return changed;
    }

    private static boolean isBooleanToken(String raw) {
        if (raw == null || raw.isBlank()) return false;
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return v.equals("true") || v.equals("false") || v.equals("0") || v.equals("1")
                || v.equals("yes") || v.equals("no");
    }

    private static boolean isStrictBooleanToken(String raw) {
        if (raw == null || raw.isBlank()) return false;
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return v.equals("true") || v.equals("false") || v.equals("yes") || v.equals("no");
    }

    private static String normalizeBooleanToken(String raw) {
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (v.equals("true") || v.equals("1") || v.equals("yes")) return "true";
        return "false";
    }

    /**
     * Coerces timeframe optimize bands away from the useless {@code 0/0/49153} .set
     * pattern to the guided ENUM walk {@code start / 1 / …} (CURRENT→H1 by default).
     *
     * @return true if start/step/end were changed
     */
    public static boolean normalizeTimeframeOptimizeBand(EaParameter parameter) {
        if (parameter == null || !isTimeframeParameterName(parameter.getName())) {
            return false;
        }
        String oldStart = parameter.getOptimizeStart() != null ? parameter.getOptimizeStart().trim() : "";
        String oldStep = parameter.getOptimizeStep() != null ? parameter.getOptimizeStep().trim() : "";
        String oldEnd = parameter.getOptimizeEnd() != null ? parameter.getOptimizeEnd().trim() : "";

        String newStart = fromTimeframeDisplay(oldStart);
        // PERIOD_CURRENT (0) is not a usable Opt start — MT5 labels it "current" and
        // the Inputs grid often shows an empty step for that edge.
        if (newStart.isBlank() || "0".equals(newStart)) {
            newStart = "1"; // PERIOD_M1
        }

        // ENUM_TIMEFRAMES walk needs step 1; legacy .set files often have 0.
        String newStep = ("0".equals(oldStep) || oldStep.isBlank()) ? "1" : oldStep;

        String newEnd = fromTimeframeDisplay(oldEnd);
        if (newEnd.isBlank() || isOversizedTimeframeStop(newEnd)) {
            newEnd = "16385"; // PERIOD_H1
        }

        boolean changed = !newStart.equals(oldStart) || !newStep.equals(oldStep) || !newEnd.equals(oldEnd);
        if (changed) {
            parameter.setOptimizeStart(newStart);
            parameter.setOptimizeStep(newStep);
            parameter.setOptimizeEnd(newEnd);
        }
        return changed;
    }

    /** D1 / W1 / MN1 (and blank) are not useful stops for indicator TF search on M5. */
    private static boolean isOversizedTimeframeStop(String code) {
        return "16408".equals(code)   // PERIOD_D1
                || "32769".equals(code) // PERIOD_W1
                || "49153".equals(code); // PERIOD_MN1
    }

    /** {@code Chart/M5} when period known, otherwise {@code Chart}. */
    public static String chartCurrentDisplayLabel(String chartPeriod) {
        String tf = normalizeChartPeriodLabel(chartPeriod);
        return tf != null ? "Chart/" + tf : "Chart";
    }

    private static String normalizeChartPeriodLabel(String chartPeriod) {
        if (chartPeriod == null || chartPeriod.isBlank()) return null;
        String p = chartPeriod.trim().toUpperCase(Locale.ROOT);
        if (p.matches("M\\d+") || p.matches("H\\d+")
                || p.equals("D1") || p.equals("W1") || p.equals("MN1")) {
            return p;
        }
        return null;
    }

    /**
     * True when this parameter is marked for optimization but its step cannot
     * advance the search (blank or {@code ≤ 0}). MT5 will not iterate such rows.
     */
    public boolean hasInvalidOptimizeStep() {
        if (!optimizeEnabled || sectionHeader || stringType) {
            return false;
        }
        String step = optimizeStep != null ? optimizeStep.trim() : "";
        if (step.isBlank()) {
            return true;
        }
        try {
            return Double.parseDouble(normalizeMql5Value(step)) <= 0.0;
        } catch (NumberFormatException ex) {
            return true;
        }
    }

    /**
     * Names of parameters with Opt=Y and unusable step (blank / {@code ≤ 0}).
     */
    public static List<String> findInvalidOptimizeSteps(List<EaParameter> params) {
        if (params == null || params.isEmpty()) {
            return List.of();
        }
        List<String> bad = new ArrayList<>();
        for (EaParameter parameter : params) {
            if (parameter != null && parameter.hasInvalidOptimizeStep()) {
                String name = parameter.getName() != null ? parameter.getName() : "(unnamed)";
                bad.add(name);
            }
        }
        return bad;
    }

    /**
     * Hard fail before an optimization run: Opt=Y with step 0/blank is a misconfiguration.
     */
    public static void requireValidOptimizeSteps(List<EaParameter> params) {
        List<String> bad = findInvalidOptimizeSteps(params);
        if (bad.isEmpty()) {
            return;
        }
        throw new IllegalStateException(
                "Fehlkonfiguration: Optimierung aktiv (Y), aber Schritt ist 0/leer — "
                        + "MT5 iteriert diese Parameter nicht: " + String.join(", ", bad)
                        + ". Schritt muss > 0 sein (bei Bools/Enums typisch 1).");
    }

    /**
     * Formats this parameter as a .set file line.
     * <p>
     * Timeframe inputs are always written as numeric {@code ENUM_TIMEFRAMES} codes
     * ({@code 0}=PERIOD_CURRENT, {@code 1}=M1, {@code 16385}=H1, …). Display labels such as
     * {@code current}, {@code Chart/M5} or {@code H1} must never appear in the .set file —
     * MT5 only accepts the integer codes.
     */
    public String toSetFileLine() {
        if (stringType) {
            return name + "=" + normalizeMql5Value(value);
        }
        if (isTimeframeParameterName(name)) {
            String normValue = coerceTimeframeSetToken(value, "0");
            // Opt=Y must not ship PERIOD_CURRENT as Start — see normalizeTimeframeOptimizeBand.
            String startFallback = optimizeEnabled ? "1" : "0";
            String normStart = coerceTimeframeSetToken(optimizeStart, startFallback);
            if (optimizeEnabled && "0".equals(normStart)) {
                normStart = "1";
            }
            String normStep = coerceTimeframeStepToken(optimizeStep);
            String normEnd = coerceTimeframeSetToken(optimizeEnd, "16385");
            return name + "=" + normValue + "||" + normStart + "||" + normStep + "||" + normEnd
                    + "||" + (optimizeEnabled ? "Y" : "N");
        }
        String normValue = normalizeMql5Value(value);
        String normStart = normalizeMql5Value(optimizeStart);
        String normStep = normalizeMql5Value(optimizeStep);
        String normEnd = normalizeMql5Value(optimizeEnd);
        return name + "=" + normValue + "||" + normStart + "||" + normStep + "||" + normEnd
                + "||" + (optimizeEnabled ? "Y" : "N");
    }

    /** Numeric ENUM_TIMEFRAMES token for .set Value/Start/Stop — never a UI label. */
    public static String coerceTimeframeSetToken(String raw, String fallbackCode) {
        String code = fromTimeframeDisplay(raw);
        if (code == null || code.isBlank()) {
            return fallbackCode != null ? fallbackCode : "0";
        }
        // Reject leftover labels that fromTimeframeDisplay could not map.
        if (!code.matches("-?\\d+")) {
            return fallbackCode != null ? fallbackCode : "0";
        }
        return code;
    }

    /** Optimization step for TF enums must be a positive integer (normally {@code 1}). */
    public static String coerceTimeframeStepToken(String raw) {
        if (raw == null || raw.isBlank() || "0".equals(raw.trim())) {
            return "1";
        }
        String trimmed = raw.trim();
        if (trimmed.matches("-?\\d+(\\.\\d+)?")) {
            try {
                double d = Double.parseDouble(trimmed);
                if (d <= 0) return "1";
                if (d == Math.rint(d)) return String.valueOf((long) d);
                return trimmed;
            } catch (NumberFormatException ignored) {
                return "1";
            }
        }
        return "1";
    }

    /**
     * Rewrites in-memory timeframe fields to numeric ENUM codes so UI labels never leak
     * into snapshots or subsequent .set writes.
     */
    public static boolean sanitizeTimeframeFieldsForSetFile(EaParameter parameter) {
        if (parameter == null || !isTimeframeParameterName(parameter.getName()) || parameter.isStringType()) {
            return false;
        }
        String newValue = coerceTimeframeSetToken(parameter.getValue(), "0");
        String startFallback = parameter.isOptimizeEnabled() ? "1" : "0";
        String newStart = coerceTimeframeSetToken(parameter.getOptimizeStart(), startFallback);
        if (parameter.isOptimizeEnabled() && "0".equals(newStart)) {
            newStart = "1";
        }
        String newStep = coerceTimeframeStepToken(parameter.getOptimizeStep());
        String newEnd = coerceTimeframeSetToken(parameter.getOptimizeEnd(), "16385");
        // Clamp oversized stops here too — sanitize must be complete on its own.
        if (isOversizedTimeframeStop(newEnd)) {
            newEnd = "16385";
        }
        boolean changed = !newValue.equals(parameter.getValue() != null ? parameter.getValue().trim() : "")
                || !newStart.equals(parameter.getOptimizeStart() != null ? parameter.getOptimizeStart().trim() : "")
                || !newStep.equals(parameter.getOptimizeStep() != null ? parameter.getOptimizeStep().trim() : "")
                || !newEnd.equals(parameter.getOptimizeEnd() != null ? parameter.getOptimizeEnd().trim() : "");
        if (changed) {
            parameter.setValue(newValue);
            parameter.setOptimizeStart(newStart);
            parameter.setOptimizeStep(newStep);
            parameter.setOptimizeEnd(newEnd);
        }
        return changed;
    }

    @Override
    public String toString() {
        return "EaParameter{" + name + "=" + value + (isModified() ? " [MODIFIED from " + defaultValue + "]" : "") + "}";
    }
}
