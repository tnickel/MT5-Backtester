package com.backtester.config;

/**
 * POJO representing a single EA input parameter from a .set file.
 * 
 * MT5 .set file format:
 *   ParameterName=Value||OptStart||OptStep||OptEnd||Y/N
 *   StringParameter=StringValue  (no optimization fields)
 *   ; comment or section header
 */
public class EaParameter {

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

    public EaParameter() {}

    public EaParameter(String name, String value) {
        this.name = name;
        this.value = value;
        this.defaultValue = value;
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
        switch (trimmed.toUpperCase()) {
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
            case "PERIOD_H4": return "16384";
            case "PERIOD_H6": return "16388";
            case "PERIOD_H8": return "16389";
            case "PERIOD_H12": return "16390";
            case "PERIOD_D1": return "16408";
            case "PERIOD_W1": return "32769";
            case "PERIOD_MN1": return "49153";

            case "MODE_SMA": return "0";
            case "MODE_EMA": return "1";
            case "MODE_SMMA": return "2";
            case "MODE_LWMA": return "3";

            case "PRICE_CLOSE": return "1";
            case "PRICE_OPEN": return "0";
            case "PRICE_HIGH": return "2";
            case "PRICE_LOW": return "4";
            case "PRICE_MEDIAN": return "4";
            case "PRICE_TYPICAL": return "5";
            case "PRICE_WEIGHTED": return "6";

            default: return raw;
        }
    }

    /**
     * Formats this parameter as a .set file line.
     */
    public String toSetFileLine() {
        String normValue = normalizeMql5Value(value);
        String normStart = normalizeMql5Value(optimizeStart);
        String normStep = normalizeMql5Value(optimizeStep);
        String normEnd = normalizeMql5Value(optimizeEnd);

        if (stringType) {
            return name + "=" + normValue;
        }
        return name + "=" + normValue + "||" + normStart + "||" + normStep + "||" + normEnd + "||" + (optimizeEnabled ? "Y" : "N");
    }

    @Override
    public String toString() {
        return "EaParameter{" + name + "=" + value + (isModified() ? " [MODIFIED from " + defaultValue + "]" : "") + "}";
    }
}
