package com.backtester.workflow;

import com.backtester.config.EaParameter;
import com.backtester.config.EaParameterManager;
import com.backtester.report.BacktestArtifactReplayResolver;
import com.backtester.report.OptimizationReportParser;
import com.backtester.report.OptimizationResult;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import com.backtester.report.OptimizationResult.ScoreWeights;
import com.backtester.report.PassPresetResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Imports MetaTrader optimization results into workflow databanks.
 * <p>
 * File-only: never starts, stops, or kills MetaTrader. Safe to use while an
 * optimization is still running (reads a snapshot copy of the report files).
 * Also extracts the optimization window (Start/End day) from the report title
 * or {@code tester_optimization.ini} when present.
 */
public final class Mt5OptimizationImportService {

    private static final Logger log = LoggerFactory.getLogger(Mt5OptimizationImportService.class);

    public static final String MAIN_XML = "OptimizationReport.xml";
    public static final String FORWARD_XML = "OptimizationReport.forward.xml";
    public static final String MAIN_HTM = "OptimizationReport.htm";
    public static final String MAIN_HTML = "OptimizationReport.html";
    public static final String TESTER_OPTIMIZATION_INI = "tester_optimization.ini";

    private static final Pattern TITLE_RANGE = Pattern.compile(
            "(\\d{4}[\\.\\-]\\d{2}[\\.\\-]\\d{2})\\s*[-–]\\s*(\\d{4}[\\.\\-]\\d{2}[\\.\\-]\\d{2})");
    private static final Pattern TITLE_IDENTITY = Pattern.compile(
            "(?i)\\b([A-Za-z0-9._-]+)\\s*,\\s*([A-Za-z0-9._-]+)\\s+\\d{4}[\\.\\-]\\d{2}[\\.\\-]\\d{2}");
    private static final Pattern INI_FROM = Pattern.compile(
            "(?im)^\\s*FromDate\\s*=\\s*(\\d{4}[\\.\\-]\\d{2}[\\.\\-]\\d{2})");
    private static final Pattern INI_TO = Pattern.compile(
            "(?im)^\\s*ToDate\\s*=\\s*(\\d{4}[\\.\\-]\\d{2}[\\.\\-]\\d{2})");

    private Mt5OptimizationImportService() {}

    public record ImportResult(
            List<CombinedPass> passes,
            Path mainReport,
            Path forwardReport,
            Path snapshotDirectory,
            boolean forwardUsed,
            /** ISO {@code yyyy-MM-dd}, or blank if unknown. */
            String fromDate,
            /** ISO {@code yyyy-MM-dd}, or blank if unknown. */
            String toDate,
            String message,
            /** Symbol parsed from the report title, or blank if unknown. */
            String reportSymbol,
            /** Period/timeframe parsed from the report title, or blank if unknown. */
            String reportPeriod
    ) {
        /** Backwards-compatible constructor for callers that only need the original import fields. */
        public ImportResult(List<CombinedPass> passes,
                            Path mainReport,
                            Path forwardReport,
                            Path snapshotDirectory,
                            boolean forwardUsed,
                            String fromDate,
                            String toDate,
                            String message) {
            this(passes, mainReport, forwardReport, snapshotDirectory, forwardUsed,
                    fromDate, toDate, message, "", "");
        }

        public int passCount() {
            return passes == null ? 0 : passes.size();
        }

        public boolean hasDateRange() {
            return fromDate != null && !fromDate.isBlank()
                    && toDate != null && !toDate.isBlank();
        }
    }

    /**
     * Locates the usual report files in a MetaTrader install directory
     * ({@code OptimizationReport.xml} / optional forward / HTML fallback).
     */
    public static ImportResult importFromMt5Install(Path mtInstallDir) throws Exception {
        Objects.requireNonNull(mtInstallDir, "mtInstallDir");
        if (!Files.isDirectory(mtInstallDir)) {
            throw new IllegalArgumentException("MT5-Installationsordner nicht gefunden: " + mtInstallDir);
        }
        Path main = resolveMainReport(mtInstallDir);
        if (main == null) {
            throw new IllegalArgumentException(
                    "Kein OptimizationReport.xml/.htm im MT5-Ordner gefunden:\n" + mtInstallDir
                            + "\n\nFalls die Optimierung noch läuft, speichert MetaTrader den Report "
                            + "manchmal erst später. Du kannst auch eine Report-Datei manuell wählen.");
        }
        Path forward = mtInstallDir.resolve(FORWARD_XML);
        if (!Files.isRegularFile(forward)) {
            forward = null;
        }
        Path testerIni = mtInstallDir.resolve(TESTER_OPTIMIZATION_INI);
        if (!Files.isRegularFile(testerIni)) {
            testerIni = null;
        }
        return importFromReportFiles(main, forward, testerIni);
    }

    public static ImportResult importFromReportFiles(Path mainReport, Path forwardReport) throws Exception {
        Path testerIni = null;
        if (mainReport.getParent() != null) {
            Path candidate = mainReport.getParent().resolve(TESTER_OPTIMIZATION_INI);
            if (Files.isRegularFile(candidate)) {
                testerIni = candidate;
            }
        }
        return importFromReportFiles(mainReport, forwardReport, testerIni);
    }

    /**
     * Parses a main optimization report (XML or HTML) and an optional forward XML.
     * Copies sources into a temp snapshot first so a live MT5 write does not
     * corrupt mid-parse.
     */
    public static ImportResult importFromReportFiles(Path mainReport,
                                                     Path forwardReport,
                                                     Path testerOptimizationIni) throws Exception {
        Objects.requireNonNull(mainReport, "mainReport");
        if (!Files.isRegularFile(mainReport)) {
            throw new IllegalArgumentException("Report-Datei nicht gefunden: " + mainReport);
        }

        Path snapshotDir = Files.createTempDirectory("mt5_opti_import_");
        Path mainCopy = snapshotDir.resolve(mainReport.getFileName().toString());
        copyBestEffort(mainReport, mainCopy);

        Path forwardCopy = null;
        boolean forwardUsed = false;
        if (forwardReport != null && Files.isRegularFile(forwardReport)) {
            forwardCopy = snapshotDir.resolve(forwardReport.getFileName().toString());
            copyBestEffort(forwardReport, forwardCopy);
        }

        OptimizationResult result = new OptimizationResult();
        result.setOutputDirectory(snapshotDir.toString());
        OptimizationReportParser parser = new OptimizationReportParser();

        String lower = mainCopy.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".htm") || lower.endsWith(".html")) {
            parser.parseHtml(mainCopy, result);
        } else {
            parser.parse(mainCopy, result);
        }

        if (forwardCopy != null) {
            try {
                parser.parseForward(forwardCopy, result);
                forwardUsed = !result.getForwardPasses().isEmpty();
            } catch (Exception ex) {
                log.warn("Forward-Report konnte nicht gelesen werden ({}): {}", forwardCopy, ex.toString());
                forwardUsed = false;
            }
        }

        String[] range = extractDateRange(result, mainCopy, testerOptimizationIni);
        String fromIso = range[0];
        String toIso = range[1];
        String[] identity = extractReportIdentity(mainCopy);
        if (!fromIso.isBlank() && !toIso.isBlank()) {
            result.setFromDate(fromIso);
            result.setToDate(toIso);
        }

        Path originalPreset = findOriginalPreset(mainReport, testerOptimizationIni,
                identity[0], identity[1], fromIso, toIso);
        boolean presetArchived = false;
        if (originalPreset != null) {
            Path presetCopy = snapshotDir.resolve(BacktestArtifactReplayResolver.PRESET_SNAPSHOT_FILE);
            copyBestEffort(originalPreset, presetCopy);
            if (testerOptimizationIni != null && Files.isRegularFile(testerOptimizationIni)) {
                copyBestEffort(testerOptimizationIni, snapshotDir.resolve("tester.ini"));
            }
            presetArchived = true;
        }
        // Deliberately NO bulk PassPresetResolver.embedConcreteSetfile here anymore:
        // embedding full .set lines into every imported pass was an unbounded memory
        // spike. Passes resolve from the archived preset in the snapshot directory
        // at execution time and embed lazily into the few passes actually used.

        List<CombinedPass> combined = result.buildCombinedPasses(
                forwardUsed,
                ScoreWeights.defaults());
        if (combined == null) {
            combined = List.of();
        }
        for (CombinedPass pass : combined) {
            if (pass == null) continue;
            if (pass.getReportDirectory() == null || pass.getReportDirectory().isBlank()) {
                pass.setReportDirectory(snapshotDir.toString());
            }
        }

        if (combined.isEmpty()) {
            throw new IllegalArgumentException(
                    "Report enthält keine Passes. Datei evtl. noch unvollständig "
                            + "(Optimierung läuft noch) oder Format unbekannt:\n" + mainReport);
        }

        String msg = combined.size() + " Pass(es) aus " + mainReport.getFileName()
                + (forwardUsed ? " (+ Forward)" : " (ohne Forward)")
                + " importiert.";
        if (!fromIso.isBlank() && !toIso.isBlank()) {
            msg += " Zeitraum: " + fromIso + " → " + toIso + ".";
        } else {
            msg += " Zeitraum im Report nicht gefunden — Start/End day unverändert.";
        }
        if (presetArchived) {
            msg += " Das Original-Optimierungsset wurde im Snapshot archiviert.";
        } else {
            msg += " WARNUNG: Kein zum Report passendes Original-Optimierungsset gefunden; "
                    + "die Passes sind nicht exakt reproduzierbar.";
        }
        return new ImportResult(
                new ArrayList<>(combined),
                mainReport.toAbsolutePath().normalize(),
                forwardReport != null && Files.isRegularFile(forwardReport)
                        ? forwardReport.toAbsolutePath().normalize() : null,
                snapshotDir,
                forwardUsed,
                fromIso,
                toIso,
                msg,
                identity[0],
                identity[1]);
    }

    /**
     * Prefer report-title / pass dates; fall back to tester_optimization.ini.
     * Returns {@code [fromIso, toIso]} (possibly blank).
     */
    static String[] extractDateRange(OptimizationResult result,
                                     Path mainReportCopy,
                                     Path testerOptimizationIni) {
        String from = "";
        String to = "";

        if (result != null && result.getPasses() != null) {
            for (Pass pass : result.getPasses()) {
                if (pass == null) continue;
                String pf = normalizeIsoDate(pass.getFromDate());
                String pt = normalizeIsoDate(pass.getToDate());
                if (!pf.isBlank() && !pt.isBlank()) {
                    from = pf;
                    to = pt;
                    break;
                }
            }
        }

        if ((from.isBlank() || to.isBlank()) && mainReportCopy != null && Files.isRegularFile(mainReportCopy)) {
            try {
                String head = Files.readString(mainReportCopy, StandardCharsets.UTF_8);
                if (head.length() > 8000) {
                    head = head.substring(0, 8000);
                }
                Matcher m = TITLE_RANGE.matcher(head);
                if (m.find()) {
                    from = normalizeIsoDate(m.group(1));
                    to = normalizeIsoDate(m.group(2));
                }
            } catch (Exception ex) {
                log.debug("Could not read title dates from {}: {}", mainReportCopy, ex.toString());
            }
        }

        if ((from.isBlank() || to.isBlank())
                && testerOptimizationIni != null
                && Files.isRegularFile(testerOptimizationIni)) {
            try {
                String ini = Files.readString(testerOptimizationIni, StandardCharsets.UTF_8);
                Matcher mf = INI_FROM.matcher(ini);
                Matcher mt = INI_TO.matcher(ini);
                if (mf.find() && mt.find()) {
                    from = normalizeIsoDate(mf.group(1));
                    to = normalizeIsoDate(mt.group(1));
                }
            } catch (Exception ex) {
                log.debug("Could not read dates from {}: {}", testerOptimizationIni, ex.toString());
            }
        }

        return new String[]{from, to};
    }

    /** Extracts the report symbol/timeframe from titles such as {@code EA AUDCAD,M5 2024.01.01-2025.01.01}. */
    static String[] extractReportIdentity(Path reportCopy) {
        if (reportCopy == null || !Files.isRegularFile(reportCopy)) {
            return new String[]{"", ""};
        }
        try {
            String head = Files.readString(reportCopy, StandardCharsets.UTF_8);
            if (head.length() > 8000) head = head.substring(0, 8000);
            Matcher matcher = TITLE_IDENTITY.matcher(head);
            if (matcher.find()) {
                return new String[]{matcher.group(1).trim(), matcher.group(2).trim()};
            }
        } catch (Exception ex) {
            log.debug("Could not read report identity from {}: {}", reportCopy, ex.toString());
        }
        return new String[]{"", ""};
    }

    /** Accepts {@code 2024.08.01} or {@code 2024-08-01}; returns ISO or blank. */
    static String normalizeIsoDate(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String cleaned = raw.trim().replace('.', '-');
        try {
            return LocalDate.parse(cleaned).toString();
        } catch (Exception ex) {
            return "";
        }
    }

    static Path resolveMainReport(Path mtInstallDir) {
        Path xml = mtInstallDir.resolve(MAIN_XML);
        if (Files.isRegularFile(xml)) return xml;
        Path htm = mtInstallDir.resolve(MAIN_HTM);
        if (Files.isRegularFile(htm)) return htm;
        Path html = mtInstallDir.resolve(MAIN_HTML);
        if (Files.isRegularFile(html)) return html;
        return null;
    }

    /**
     * Locates the preset named by tester_optimization.ini. A tester ini is only
     * trusted when its available market/date fields agree with the report; MT5
     * routinely overwrites this file on the next run.
     */
    static Path findOriginalPreset(Path mainReport,
                                   Path testerOptimizationIni,
                                   String reportSymbol,
                                   String reportPeriod,
                                   String reportFrom,
                                   String reportTo) {
        Path reportDir = mainReport != null ? mainReport.toAbsolutePath().normalize().getParent() : null;
        if (reportDir != null) {
            Path archived = reportDir.resolve(BacktestArtifactReplayResolver.PRESET_SNAPSHOT_FILE);
            if (Files.isRegularFile(archived)) return archived;
        }
        if (testerOptimizationIni == null || !Files.isRegularFile(testerOptimizationIni)) return null;
        try {
            String ini = Files.readString(testerOptimizationIni, StandardCharsets.UTF_8);
            if (!matchesWhenKnown(reportSymbol, readIniValue(ini, "Symbol"))
                    || !matchesWhenKnown(reportPeriod, readIniValue(ini, "Period"))
                    || !matchesDateWhenKnown(reportFrom, readIniValue(ini, "FromDate"))
                    || !matchesDateWhenKnown(reportTo, readIniValue(ini, "ToDate"))) {
                log.warn("tester_optimization.ini passt nicht zum importierten Report; "
                        + "das darin referenzierte Setfile wird nicht verwendet: {}", testerOptimizationIni);
                return null;
            }

            String configured = readIniValue(ini, "ExpertParameters");
            if (configured == null || configured.isBlank()) return null;
            Path leaf = Path.of(configured.trim()).getFileName();
            if (leaf == null || !leaf.toString().equals(configured.trim())) return null;

            List<Path> candidates = new ArrayList<>();
            Path iniDir = testerOptimizationIni.toAbsolutePath().normalize().getParent();
            if (iniDir != null) {
                candidates.add(iniDir.resolve(leaf));
                candidates.add(iniDir.resolve("MQL5").resolve("Profiles").resolve("Tester").resolve(leaf));
                candidates.add(iniDir.resolve("Profiles").resolve("Tester").resolve(leaf));
            }
            if (reportDir != null) candidates.add(reportDir.resolve(leaf));
            for (Path candidate : candidates) {
                if (Files.isRegularFile(candidate)) return candidate;
            }
        } catch (Exception ex) {
            log.warn("Original-Optimierungsset konnte nicht ermittelt werden: {}", ex.toString());
        }
        return null;
    }

    private static String readIniValue(String ini, String key) {
        if (ini == null || key == null) return null;
        Pattern pattern = Pattern.compile("(?im)^\\s*" + Pattern.quote(key) + "\\s*=\\s*(.*?)\\s*$");
        Matcher matcher = pattern.matcher(ini);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static boolean matchesWhenKnown(String reportValue, String iniValue) {
        return reportValue == null || reportValue.isBlank()
                || iniValue == null || iniValue.isBlank()
                || reportValue.trim().equalsIgnoreCase(iniValue.trim());
    }

    private static boolean matchesDateWhenKnown(String reportValue, String iniValue) {
        return reportValue == null || reportValue.isBlank()
                || iniValue == null || iniValue.isBlank()
                || reportValue.equals(normalizeIsoDate(iniValue));
    }

    private static void copyBestEffort(Path source, Path target) throws IOException {
        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException first) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
