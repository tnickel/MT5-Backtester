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
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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

    /**
     * Only this many leading bytes of a report are read for the title
     * identity/date regexes; optimization reports can reach hundreds of MB and
     * must never be loaded into memory just for their header.
     */
    private static final int HEADER_PROBE_BYTES = 256 * 1024;

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
        // From here on the snapshot directory is owned by this import: any failure
        // must clean it up again. It is only kept deliberately on success, where it
        // stays behind as the reportDirectory of the archived passes.
        try {
            return doImportFromReportFiles(snapshotDir, mainReport, forwardReport, testerOptimizationIni);
        } catch (Exception ex) {
            deleteRecursively(snapshotDir);
            throw ex;
        }
    }

    private static ImportResult doImportFromReportFiles(Path snapshotDir,
                                                        Path mainReport,
                                                        Path forwardReport,
                                                        Path testerOptimizationIni) throws Exception {
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
                String head = readHeaderProbe(mainReportCopy);
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
            String head = readHeaderProbe(reportCopy);
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
     * Locates the preset used for this optimization run. A tester ini is only
     * trusted when its available market/date fields agree with the report; MT5
     * routinely overwrites this file on the next run. An {@code
     * expert-parameters.set} already lying in the report directory is used only
     * when the ini path cannot resolve a set AND an ini in the same directory
     * corroborates the report's symbol/period/date range — the file itself
     * carries no such metadata and may be stale from an earlier run.
     */
    static Path findOriginalPreset(Path mainReport,
                                   Path testerOptimizationIni,
                                   String reportSymbol,
                                   String reportPeriod,
                                   String reportFrom,
                                   String reportTo) {
        Path reportDir = mainReport != null ? mainReport.toAbsolutePath().normalize().getParent() : null;

        // The ini names the set this run actually used, so it wins over a generic
        // expert-parameters.set lying next to the report (possibly stale).
        if (testerOptimizationIni != null && Files.isRegularFile(testerOptimizationIni)) {
            Path viaIni = resolvePresetFromIni(testerOptimizationIni, reportDir,
                    reportSymbol, reportPeriod, reportFrom, reportTo);
            if (viaIni != null) {
                return viaIni;
            }
        }

        if (reportDir != null) {
            Path archived = reportDir.resolve(BacktestArtifactReplayResolver.PRESET_SNAPSHOT_FILE);
            if (Files.isRegularFile(archived)) {
                if (archivedPresetMatchesReport(reportDir, reportSymbol, reportPeriod, reportFrom, reportTo)) {
                    return archived;
                }
                log.warn("expert-parameters.set im Report-Ordner passt nicht (mehr) zum importierten Report; "
                        + "sie wird ignoriert: {}", archived);
            }
        }
        return null;
    }

    private static Path resolvePresetFromIni(Path testerOptimizationIni,
                                             Path reportDir,
                                             String reportSymbol,
                                             String reportPeriod,
                                             String reportFrom,
                                             String reportTo) {
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

    /**
     * The archived {@code expert-parameters.set} carries no symbol/period/date
     * metadata itself, so it is only accepted when an ini in the same directory
     * (the run's {@code tester_optimization.ini} or our copied {@code tester.ini}
     * in snapshot/output directories) agrees with the report identity.
     */
    private static boolean archivedPresetMatchesReport(Path reportDir,
                                                       String reportSymbol,
                                                       String reportPeriod,
                                                       String reportFrom,
                                                       String reportTo) {
        Path ini = reportDir.resolve(TESTER_OPTIMIZATION_INI);
        if (!Files.isRegularFile(ini)) {
            ini = reportDir.resolve("tester.ini");
        }
        if (!Files.isRegularFile(ini)) {
            // No run metadata in this directory ties the set to this report —
            // do not trust it (better re-import without preset than a wrong one).
            log.warn("Keine tester_optimization.ini/tester.ini im Report-Ordner — "
                    + "die dort liegende expert-parameters.set kann nicht geprüft werden und wird ignoriert.");
            return false;
        }
        try {
            String content = Files.readString(ini, StandardCharsets.UTF_8);
            return matchesWhenKnown(reportSymbol, readIniValue(content, "Symbol"))
                    && matchesWhenKnown(reportPeriod, readIniValue(content, "Period"))
                    && matchesDateWhenKnown(reportFrom, readIniValue(content, "FromDate"))
                    && matchesDateWhenKnown(reportTo, readIniValue(content, "ToDate"));
        } catch (Exception ex) {
            log.warn("expert-parameters.set konnte nicht gegen {} geprüft werden und wird ignoriert: {}",
                    ini, ex.toString());
            return false;
        }
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

    /**
     * Reads at most the first {@link #HEADER_PROBE_BYTES} bytes of {@code file}
     * and decodes them with BOM/charset detection. Only the header is needed for
     * the title identity/date regexes — the full report is parsed streamingly by
     * {@link OptimizationReportParser} and must never sit in memory twice.
     */
    private static String readHeaderProbe(Path file) throws IOException {
        byte[] bytes;
        try (InputStream in = Files.newInputStream(file)) {
            bytes = in.readNBytes(HEADER_PROBE_BYTES);
        }
        if (bytes.length == 0) return "";
        String decoded = decodeWithCharsetDetection(bytes);
        if (decoded.startsWith("\uFEFF")) {
            decoded = decoded.substring(1);
        }
        return decoded;
    }

    /** BOM + UTF-16 NUL-pattern heuristics, mirroring the parser's decodeHtml. */
    private static String decodeWithCharsetDetection(byte[] bytes) {
        if (bytes.length >= 2) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            if (first == 0xFF && second == 0xFE) {
                return new String(bytes, StandardCharsets.UTF_16LE);
            }
            if (first == 0xFE && second == 0xFF) {
                return new String(bytes, StandardCharsets.UTF_16BE);
            }
        }

        int evenNuls = 0;
        int oddNuls = 0;
        int sampleLength = Math.min(bytes.length, 4096);
        for (int i = 0; i < sampleLength; i++) {
            if (bytes[i] == 0) {
                if ((i & 1) == 0) evenNuls++;
                else oddNuls++;
            }
        }
        if (oddNuls > sampleLength / 8 && oddNuls > evenNuls * 2) {
            return new String(bytes, StandardCharsets.UTF_16LE);
        }
        if (evenNuls > sampleLength / 8 && evenNuls > oddNuls * 2) {
            return new String(bytes, StandardCharsets.UTF_16BE);
        }

        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        return utf8.contains("\uFFFD")
                ? new String(bytes, Charset.forName("windows-1252"))
                : utf8;
    }

    /** Best-effort recursive delete; never throws. */
    private static void deleteRecursively(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ex) {
                    log.debug("Konnte {} nicht löschen: {}", p, ex.toString());
                }
            });
        } catch (IOException ex) {
            log.debug("Konnte {} nicht zum Aufräumen öffnen: {}", root, ex.toString());
        }
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
