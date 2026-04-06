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

    /**
     * Formats this parameter as a .set file line.
     */
    public String toSetFileLine() {
        if (stringType) {
            return name + "=" + value;
        }
        return name + "=" + value + "||" + optimizeStart + "||" + optimizeStep + "||" + optimizeEnd + "||" + (optimizeEnabled ? "Y" : "N");
    }

    @Override
    public String toString() {
        return "EaParameter{" + name + "=" + value + (isModified() ? " [MODIFIED from " + defaultValue + "]" : "") + "}";
    }
}
