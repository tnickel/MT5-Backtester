package com.backtester.config;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Encapsulates the specific properties, directory structures, log file encodings,
 * and report outputs for MetaTrader 4 and MetaTrader 5 platforms.
 */
public enum MetaTraderPlatform {
    MT4(
        "terminal.exe",
        "terminal",
        "MT4",
        "MQL4",
        "tester",
        "tester/logs",
        ".htm",
        Charset.forName("windows-1252"), // Default MT4 log encoding is ANSI (e.g. windows-1252)
        false
    ),
    MT5(
        "terminal64.exe",
        "terminal64",
        "MT5",
        "MQL5",
        "MQL5/Profiles/Tester",
        "Tester/logs",
        ".xml",
        StandardCharsets.UTF_16LE, // MT5 log encoding is UTF-16LE
        true
    );

    private final String executableName;
    private final String processName;
    private final String name;
    private final String mqlFolderName;
    private final String presetsFolderName;
    private final String testerLogsFolderName;
    private final String reportFileExtension;
    private final Charset logCharset;
    private final boolean supportsMultiThreadAgents;

    MetaTraderPlatform(
        String executableName,
        String processName,
        String name,
        String mqlFolderName,
        String presetsFolderName,
        String testerLogsFolderName,
        String reportFileExtension,
        Charset logCharset,
        boolean supportsMultiThreadAgents
    ) {
        this.executableName = executableName;
        this.processName = processName;
        this.name = name;
        this.mqlFolderName = mqlFolderName;
        this.presetsFolderName = presetsFolderName;
        this.testerLogsFolderName = testerLogsFolderName;
        this.reportFileExtension = reportFileExtension;
        this.logCharset = logCharset;
        this.supportsMultiThreadAgents = supportsMultiThreadAgents;
    }

    public String getExecutableName() {
        return executableName;
    }

    public String getProcessName() {
        return processName;
    }

    public String getName() {
        return name;
    }

    public String getMqlFolderName() {
        return mqlFolderName;
    }

    public String getPresetsFolderName() {
        return presetsFolderName;
    }

    public String getTesterLogsFolderName() {
        return testerLogsFolderName;
    }

    public String getReportFileExtension() {
        return reportFileExtension;
    }

    public Charset getLogCharset() {
        return logCharset;
    }

    public boolean supportsMultiThreadAgents() {
        return supportsMultiThreadAgents;
    }

    /**
     * Determines the MetaTrader platform based on the expert file path.
     * EAs ending with .ex4 run on MT4, while .ex5 (or any other) run on MT5.
     */
    public static MetaTraderPlatform fromExpertPath(String expertPath) {
        if (expertPath != null && expertPath.toLowerCase().endsWith(".ex4")) {
            return MT4;
        }
        return MT5;
    }
}
