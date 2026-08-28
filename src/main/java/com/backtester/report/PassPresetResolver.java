package com.backtester.report;

import com.backtester.config.AppConfig;
import com.backtester.config.EaParameter;
import com.backtester.config.EaParameterManager;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Rebuilds the exact EA input set behind a single optimization pass.
 *
 * <p>Two MT5 properties make this non-trivial and were the source of silent
 * mismatches between a databank row and its verification backtest:
 *
 * <ul>
 *   <li>For a parameter with the optimize flag {@code Y}, MT5 ignores the value
 *       field of the .set line and iterates {@code start..stop}. When
 *       {@code start == stop} the parameter is constant across the whole run and
 *       MT5 therefore emits <b>no column</b> for it in the optimization report.
 *       Its real value is {@code start} — never the value field.</li>
 *   <li>Inputs that were not optimized at all never appear in the report either.
 *       They can only be recovered from the preset the run actually used, not
 *       from the current EA configuration, which changes over time and is even
 *       rewritten by MT5 itself.</li>
 * </ul>
 *
 * <p>Resolution therefore always starts from an archived preset belonging to the
 * run and only overlays the report columns on top of it.
 */
public final class PassPresetResolver {
    private static final Logger log = LoggerFactory.getLogger(PassPresetResolver.class);

    /** How trustworthy a resolved parameter set is. */
    public enum Fidelity {
        /** Byte-exact preset of one concrete MT5 run — no reconstruction involved. */
        EXACT_SNAPSHOT,
        /** Concrete pass preset embedded in the strategy/databank itself. */
        EMBEDDED_PASS,
        /** Archived optimization preset as base, report columns overlaid. */
        OPTIMIZATION_BASE,
        /** No archived preset found; values partly guessed from the current EA config. */
        CURRENT_CONFIG
    }

    /**
     * An archived preset found next to an MT5 report.
     *
     * @param fromOptimization {@code true} if the preset describes an optimization
     *                         search space rather than one concrete run, in which
     *                         case it must never be used byte-for-byte as a pass preset.
     */
    public record Snapshot(Path presetFile, Path directory, boolean fromOptimization) {}

    /** Result of rebuilding a pass parameter set. */
    public record Resolution(List<EaParameter> parameters, Fidelity fidelity, Path source, String warning) {
        public boolean isExact() {
            return fidelity == Fidelity.EXACT_SNAPSHOT || fidelity == Fidelity.EMBEDDED_PASS;
        }
    }

    private PassPresetResolver() {
    }

    // ==================== snapshot discovery ====================

    /**
     * Locates the archived preset of a report directory and classifies it.
     * Accepts absolute paths as well as names relative to the reports root.
     */
    public static Snapshot findSnapshot(String reportDirectory) {
        Path dir = resolveReportDirectory(reportDirectory);
        if (dir == null) return null;

        Path preset = dir.resolve(BacktestArtifactReplayResolver.PRESET_SNAPSHOT_FILE);
        if (!Files.isRegularFile(preset)) return null;

        return new Snapshot(preset, dir, isOptimizationRun(dir, preset));
    }

    /**
     * Reads the MT5 model id from the {@code tester.ini} archived with a report.
     * Returns {@code -1} when it cannot be determined. This is the only
     * authoritative record of the simulation model a report was produced with.
     */
    public static int readTesterModel(String reportDirectory) {
        String raw = readTesterIniValue(resolveReportDirectory(reportDirectory), "model");
        if (raw == null) return -1;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static Path resolveReportDirectory(String reportDirectory) {
        if (reportDirectory == null || reportDirectory.isBlank()) return null;
        try {
            Path dir = Paths.get(reportDirectory.trim());
            if (dir.isAbsolute() && Files.isDirectory(dir)) return dir;

            Path reportsRoot = AppConfig.getInstance().getReportsDirectory();
            if (reportsRoot != null) {
                Path relative = reportsRoot.resolve(reportDirectory.trim());
                if (Files.isDirectory(relative)) return relative;
            }
            return Files.isDirectory(dir) ? dir : null;
        } catch (Exception ex) {
            log.debug("Could not resolve report directory '{}': {}", reportDirectory, ex.getMessage());
            return null;
        }
    }

    /**
     * An archived preset belongs to an optimization when the run's tester.ini says
     * so. If the ini is missing, the presence of an enabled optimize flag in the
     * preset is used instead — a single concrete run never has one.
     */
    private static boolean isOptimizationRun(Path directory, Path preset) {
        String optimization = readTesterIniValue(directory, "optimization");
        if (optimization != null) {
            try {
                return Integer.parseInt(optimization.trim()) != 0;
            } catch (NumberFormatException ignored) {
                // fall through to preset inspection
            }
        }
        List<EaParameter> params = new EaParameterManager().readSetFile(preset);
        if (params == null) return false;
        return params.stream().anyMatch(EaParameter::isOptimizeEnabled);
    }

    private static String readTesterIniValue(Path directory, String lowerCaseKey) {
        if (directory == null) return null;
        Path ini = directory.resolve("tester.ini");
        if (!Files.isRegularFile(ini)) return null;
        try {
            for (String line : Files.readAllLines(ini, StandardCharsets.UTF_8)) {
                int sep = line.indexOf('=');
                if (sep <= 0) continue;
                if (line.substring(0, sep).trim().toLowerCase(Locale.ROOT).equals(lowerCaseKey)) {
                    return line.substring(sep + 1).trim();
                }
            }
        } catch (Exception ex) {
            log.debug("Could not read tester.ini in {}: {}", directory, ex.getMessage());
        }
        return null;
    }

    // ==================== parameter resolution ====================

    /**
     * Rebuilds the EA input set of a pass, preferring archived presets over the
     * mutable current EA configuration.
     *
     * @param expertFallback expert used only when no archived preset exists
     */
    public static Resolution resolve(CombinedPass combinedPass, String expertFallback) {
        if (combinedPass == null) {
            return new Resolution(Collections.emptyList(), Fidelity.CURRENT_CONFIG, null,
                    "Kein Pass übergeben.");
        }
        Pass backtestPass = combinedPass.getBacktestPass();
        Resolution resolution = resolve(candidateDirectories(combinedPass),
                backtestPass != null ? backtestPass.getParameterValues() : Collections.emptyMap(),
                backtestPass != null ? backtestPass.getParameterSetLines() : Collections.emptyList(),
                combinedPass.getPassNumber(),
                backtestPass != null ? backtestPass.getDrawdownPercent() : Double.NaN,
                expertFallback);
        embedResolvedSetfile(backtestPass, resolution);
        return resolution;
    }

    /** Rebuilds the EA input set of a bare {@link Pass} that has no combined view. */
    public static Resolution resolve(Pass pass, String expertFallback) {
        if (pass == null) {
            return new Resolution(Collections.emptyList(), Fidelity.CURRENT_CONFIG, null,
                    "Kein Pass übergeben.");
        }
        Resolution resolution = resolve(List.of(pass.getReportDirectory()), pass.getParameterValues(), pass.getParameterSetLines(),
                pass.getPassNumber(), pass.getDrawdownPercent(), expertFallback);
        embedResolvedSetfile(pass, resolution);
        return resolution;
    }

    /**
     * @param reportDirectories directories to search for an archived preset, most
     *                          specific first
     * @param reportValues      the parameter columns MT5 reported for this pass
     */
    public static Resolution resolve(List<String> reportDirectories,
                                     Map<String, String> reportValues,
                                     List<String> embeddedSetLines,
                                     int passNumber,
                                     double drawdownPercent,
                                     String expertFallback) {
        if (reportValues == null) reportValues = Collections.emptyMap();

        List<EaParameter> embedded = parseConcreteSetfileLines(embeddedSetLines);
        if (!embedded.isEmpty()) {
            return new Resolution(embedded, Fidelity.EMBEDDED_PASS, null, null);
        }

        EaParameterManager manager = new EaParameterManager();
        Snapshot optimizationSnapshot = null;

        for (String candidate : reportDirectories) {
            Snapshot snapshot = findSnapshot(candidate);
            if (snapshot == null) continue;

            if (!snapshot.fromOptimization()) {
                List<EaParameter> params = manager.readSetFile(snapshot.presetFile());
                if (params != null && !params.isEmpty()) {
                    // Values are already the concrete ones of that run; only make sure no
                    // stale optimize flag can leak into a preset written from this list.
                    List<EaParameter> exact = new ArrayList<>(params.size());
                    for (EaParameter p : params) {
                        EaParameter copy = copyOf(p);
                        copy.setOptimizeEnabled(false);
                        exact.add(copy);
                    }
                    return new Resolution(exact, Fidelity.EXACT_SNAPSHOT, snapshot.presetFile(), null);
                }
            } else if (optimizationSnapshot == null) {
                optimizationSnapshot = snapshot;
            }
        }

        if (optimizationSnapshot != null) {
            List<EaParameter> base = manager.readSetFile(optimizationSnapshot.presetFile());
            if (base != null && !base.isEmpty()) {
                List<EaParameter> params = applyPassValues(base, reportValues, true, passNumber, drawdownPercent);
                return new Resolution(params, Fidelity.OPTIMIZATION_BASE,
                        optimizationSnapshot.presetFile(), null);
            }
        }

        String expert = expertFallback != null && !expertFallback.isBlank()
                ? expertFallback
                : AppConfig.getInstance().get("app.expert", "");
        List<EaParameter> base = expert.isBlank() ? null : manager.getEffectiveParameters(expert);
        if (base == null || base.isEmpty()) {
            List<EaParameter> onlyReported = new ArrayList<>();
            for (Map.Entry<String, String> entry : reportValues.entrySet()) {
                EaParameter p = new EaParameter(entry.getKey(), entry.getValue());
                p.setOptimizeEnabled(false);
                onlyReported.add(p);
            }
            return new Resolution(onlyReported, Fidelity.CURRENT_CONFIG, null,
                    "Weder ein archiviertes Preset noch eine EA-Konfiguration gefunden. "
                            + "Es sind nur die " + onlyReported.size() + " Parameter aus dem MT5-Report bekannt.");
        }

        List<EaParameter> params = applyPassValues(base, reportValues, false, passNumber, drawdownPercent);
        String warning = "Für Pass #" + passNumber + " existiert kein archiviertes Preset des Original-Laufs. "
                + "Die " + countUnreported(base, reportValues) + " Parameter, die nicht im MT5-Report stehen, "
                + "stammen aus der aktuellen EA-Konfiguration und können vom Original-Lauf abweichen.";
        log.warn(warning);
        return new Resolution(params, Fidelity.CURRENT_CONFIG, null, warning);
    }

    private static List<String> candidateDirectories(CombinedPass combinedPass) {
        List<String> dirs = new ArrayList<>();
        addIfPresent(dirs, combinedPass.getReportDirectory());
        if (combinedPass.getLongtermPass() != null) {
            addIfPresent(dirs, combinedPass.getLongtermPass().getReportDirectory());
        }
        if (combinedPass.getBacktestPass() != null) {
            addIfPresent(dirs, combinedPass.getBacktestPass().getReportDirectory());
        }
        if (combinedPass.getForwardPass() != null) {
            addIfPresent(dirs, combinedPass.getForwardPass().getReportDirectory());
        }
        return dirs;
    }

    private static void addIfPresent(List<String> target, String value) {
        if (value != null && !value.isBlank() && !target.contains(value)) {
            target.add(value);
        }
    }

    private static int countUnreported(List<EaParameter> base, Map<String, String> reportValues) {
        int count = 0;
        for (EaParameter p : base) {
            if (!reportValues.containsKey(p.getName())) count++;
        }
        return count;
    }

    /**
     * Overlays the report columns of one pass onto a base preset.
     *
     * @param baseIsOptimizationPreset when {@code true}, a parameter with an
     *                                 enabled optimize flag but no report column
     *                                 takes its optimize start value, because that
     *                                 is what MT5 used for every pass
     */
    public static List<EaParameter> applyPassValues(List<EaParameter> basePreset,
                                                    Map<String, String> reportValues,
                                                    boolean baseIsOptimizationPreset,
                                                    int passNumber,
                                                    double drawdownPercent) {
        Map<String, String> values = reportValues != null ? reportValues : Collections.emptyMap();
        int ddPercent = Double.isNaN(drawdownPercent) ? 0 : (int) Math.round(drawdownPercent);

        List<EaParameter> result = new ArrayList<>();
        Map<String, EaParameter> byName = new LinkedHashMap<>();

        for (EaParameter source : basePreset) {
            EaParameter p = copyOf(source);

            String reported = values.get(source.getName());
            if (reported != null && !reported.isBlank()) {
                p.setValue(reported.trim());
            } else if (baseIsOptimizationPreset) {
                p.setValue(effectiveBaseValue(source));
            }

            if (isMagicNumberParameter(p.getName())) {
                p.setValue(String.valueOf(passNumber));
            } else if (isOrderCommentParameter(p.getName())) {
                p.setValue(String.format(Locale.US, "%dproz_Pass%d", ddPercent, passNumber));
            }

            p.setOptimizeEnabled(false);
            EaParameter.sanitizeTimeframeFieldsForSetFile(p);
            result.add(p);
            byName.put(p.getName(), p);
        }

        // A report column without a matching preset entry would otherwise be lost.
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (byName.containsKey(entry.getKey())) continue;
            EaParameter extra = new EaParameter(entry.getKey(), entry.getValue());
            extra.setOptimizeEnabled(false);
            result.add(extra);
        }

        return result;
    }

    /**
     * Materializes the effective values of an optimization pass as concrete .set
     * lines and stores them on the pass itself. The snapshot is deliberately
     * independent of the report directory.
     *
     * <p>This is <b>not</b> called for whole optimization populations anymore
     * (thousands of passes × full setfile lines was an unbounded memory spike).
     * Instead {@link #embedResolvedSetfile} embeds lazily: a pass becomes
     * self-contained the first time it is actually resolved (execution, export,
     * UI detail view). Callers today are tests and explicit single-pass imports.
     */
    public static void embedConcreteSetfile(Pass pass,
                                            List<EaParameter> basePreset,
                                            boolean baseIsOptimizationPreset) {
        if (pass == null || basePreset == null || basePreset.isEmpty()) return;
        List<EaParameter> concrete = applyPassValues(basePreset, pass.getParameterValues(),
                baseIsOptimizationPreset, pass.getPassNumber(), pass.getDrawdownPercent());
        pass.setParameterSetLines(toConcreteSetFileLines(concrete));
    }

    /**
     * Lazily stores the just-resolved concrete .set lines on the pass itself, so
     * the strategy stays reproducible even if its report directory is later
     * deleted, moved or overwritten — but only for passes that are actually
     * resolved (a handful), never for the full optimization population.
     *
     * <p>Deliberately skipped for {@link Fidelity#CURRENT_CONFIG}: that
     * resolution is partly guessed from the mutable current EA configuration and
     * must not be frozen into the strategy as if it were the original run's
     * setfile. Already-embedded passes are left untouched.
     */
    private static void embedResolvedSetfile(Pass pass, Resolution resolution) {
        if (pass == null || resolution == null || pass.hasParameterSetSnapshot()) return;
        if (resolution.fidelity() != Fidelity.EXACT_SNAPSHOT
                && resolution.fidelity() != Fidelity.OPTIMIZATION_BASE) {
            return;
        }
        List<EaParameter> params = resolution.parameters();
        if (params == null || params.isEmpty()) return;
        List<EaParameter> snapshot = new ArrayList<>(params.size());
        for (EaParameter parameter : params) {
            if (parameter != null) snapshot.add(copyOf(parameter));
        }
        pass.setParameterSetLines(toConcreteSetFileLines(snapshot));
        log.debug("Embedded lazily resolved setfile snapshot into pass #{} (fidelity {}).",
                pass.getPassNumber(), resolution.fidelity());
    }

    /** Formats parameters as concrete .set lines (mutates the given copies). */
    private static List<String> toConcreteSetFileLines(List<EaParameter> parameters) {
        List<String> lines = new ArrayList<>();
        for (EaParameter parameter : parameters) {
            if (parameter == null || parameter.isSectionHeader()
                    || parameter.getName() == null || parameter.getName().isBlank()) {
                continue;
            }
            parameter.setOptimizeEnabled(false);
            EaParameter.sanitizeTimeframeFieldsForSetFile(parameter);
            lines.add(parameter.toSetFileLine());
        }
        return lines;
    }

    private static List<EaParameter> parseConcreteSetfileLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) return Collections.emptyList();
        List<EaParameter> parameters = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null) continue;
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith(";")) continue;
            int separator = line.indexOf('=');
            if (separator <= 0) continue;

            String name = line.substring(0, separator).trim();
            String encoded = line.substring(separator + 1);
            EaParameter parameter = new EaParameter(name, "");
            if (encoded.contains("||")) {
                String[] parts = encoded.split("\\|\\|", -1);
                parameter.setValue(parts.length > 0 ? parts[0].trim() : "");
                parameter.setDefaultValue(parameter.getValue());
                parameter.setOptimizeStart(parts.length > 1 ? parts[1].trim() : "");
                parameter.setOptimizeStep(parts.length > 2 ? parts[2].trim() : "");
                parameter.setOptimizeEnd(parts.length > 3 ? parts[3].trim() : "");
                parameter.setStringType(false);
            } else {
                parameter.setValue(encoded);
                parameter.setDefaultValue(encoded);
                parameter.setStringType(true);
            }
            parameter.setOptimizeEnabled(false);
            EaParameter.sanitizeTimeframeFieldsForSetFile(parameter);
            parameters.add(parameter);
        }
        return parameters;
    }

    /**
     * The value MT5 actually feeds an EA for a parameter of an optimization
     * preset: the optimize start for optimized parameters, the value field
     * otherwise. Using the value field for an optimized parameter is wrong —
     * MT5 never reads it in that case.
     */
    public static String effectiveBaseValue(EaParameter parameter) {
        if (parameter == null) return "";
        if (parameter.isOptimizeEnabled() && !parameter.isStringType()) {
            String start = parameter.getOptimizeStart();
            if (start != null && !start.isBlank()) {
                return start.trim();
            }
        }
        return parameter.getValue();
    }

    public static boolean isMagicNumberParameter(String name) {
        if (name == null || name.isBlank()) return false;
        String compact = name.trim().toLowerCase(Locale.ROOT).replace("_", "");
        return compact.equals("magic")
                || compact.equals("magicnumber")
                || compact.equals("inpmagicnumber");
    }

    public static boolean isOrderCommentParameter(String name) {
        if (name == null || name.isBlank()) return false;
        String compact = name.trim().toLowerCase(Locale.ROOT).replace("_", "");
        return compact.equals("comment")
                || compact.equals("ordercomment")
                || compact.equals("inpordercomment");
    }

    /**
     * Concrete, non-optimizing parameters for retest / validation / export of one pass.
     *
     * <p>Uses {@link #resolve} (embedded lines, archived presets) and re-stamps magic
     * number and order comment with the pass identity. Prefer this over merging report
     * columns onto a shared engine EA config — that silently contaminates fixed values
     * across cluster strategies.
     */
    public static Resolution resolveForExecution(CombinedPass combinedPass, String expert) {
        if (combinedPass == null) {
            return new Resolution(Collections.emptyList(), Fidelity.CURRENT_CONFIG, null,
                    "Kein Pass übergeben.");
        }
        Resolution resolved = resolve(combinedPass, expert);
        int passNumber = combinedPass.getPassNumber();
        double drawdownPercent = combinedPass.getBtDd();
        int ddPercent = Double.isNaN(drawdownPercent) ? 0 : (int) Math.round(drawdownPercent);

        List<EaParameter> stamped = new ArrayList<>(resolved.parameters().size());
        for (EaParameter source : resolved.parameters()) {
            if (source == null || source.isSectionHeader()) continue;
            EaParameter p = copyOf(source);
            if (isMagicNumberParameter(p.getName())) {
                p.setValue(String.valueOf(passNumber));
            } else if (isOrderCommentParameter(p.getName())) {
                p.setValue(String.format(Locale.US, "%dproz_Pass%d", ddPercent, passNumber));
            }
            p.setOptimizeEnabled(false);
            EaParameter.sanitizeTimeframeFieldsForSetFile(p);
            stamped.add(p);
        }
        return new Resolution(stamped, resolved.fidelity(), resolved.source(), resolved.warning());
    }

    /**
     * Like {@link #resolveForExecution}, but when no embedded/archived preset exists,
     * overlays report columns onto {@code engineFallback} (legacy databanks / UI
     * snapshots). Prefer embeds; the fallback is the pre-embed contamination path and
     * must stay loud via {@link Resolution#warning()}.
     */
    public static Resolution resolveForExecutionWithFallback(CombinedPass combinedPass,
                                                             String expert,
                                                             List<EaParameter> engineFallback) {
        Resolution resolved = resolveForExecution(combinedPass, expert);
        if (resolved.fidelity() != Fidelity.CURRENT_CONFIG && !resolved.parameters().isEmpty()) {
            return resolved;
        }
        if (combinedPass != null && engineFallback != null && !engineFallback.isEmpty()) {
            Map<String, String> reportValues = combinedPass.getBacktestPass() != null
                    ? combinedPass.getBacktestPass().getParameterValues()
                    : Collections.emptyMap();
            List<EaParameter> merged = applyPassValues(
                    engineFallback, reportValues, true,
                    combinedPass.getPassNumber(), combinedPass.getBtDd());
            String warning = resolved.warning() != null ? resolved.warning()
                    : ("Pass #" + combinedPass.getPassNumber()
                    + ": kein archiviertes/embedded Setfile — Fallback auf geteilte Basis-Parameter.");
            log.warn(warning);
            return new Resolution(merged, Fidelity.CURRENT_CONFIG, null, warning);
        }
        return resolved;
    }

    private static EaParameter copyOf(EaParameter source) {
        EaParameter copy = new EaParameter(source.getName(), source.getValue());
        copy.setDefaultValue(source.getDefaultValue());
        copy.setSection(source.getSection());
        copy.setDisplayName(source.getDisplayName());
        copy.setStringType(source.isStringType());
        copy.setOptimizeStart(source.getOptimizeStart());
        copy.setOptimizeStep(source.getOptimizeStep());
        copy.setOptimizeEnd(source.getOptimizeEnd());
        copy.setOptimizeEnabled(source.isOptimizeEnabled());
        copy.setRawLine(source.getRawLine());
        return copy;
    }
}
