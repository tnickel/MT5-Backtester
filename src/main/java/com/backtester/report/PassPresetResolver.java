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
            return fidelity == Fidelity.EXACT_SNAPSHOT;
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
        return resolve(candidateDirectories(combinedPass),
                backtestPass != null ? backtestPass.getParameterValues() : Collections.emptyMap(),
                combinedPass.getPassNumber(),
                backtestPass != null ? backtestPass.getDrawdownPercent() : Double.NaN,
                expertFallback);
    }

    /** Rebuilds the EA input set of a bare {@link Pass} that has no combined view. */
    public static Resolution resolve(Pass pass, String expertFallback) {
        if (pass == null) {
            return new Resolution(Collections.emptyList(), Fidelity.CURRENT_CONFIG, null,
                    "Kein Pass übergeben.");
        }
        return resolve(List.of(pass.getReportDirectory()), pass.getParameterValues(),
                pass.getPassNumber(), pass.getDrawdownPercent(), expertFallback);
    }

    /**
     * @param reportDirectories directories to search for an archived preset, most
     *                          specific first
     * @param reportValues      the parameter columns MT5 reported for this pass
     */
    public static Resolution resolve(List<String> reportDirectories,
                                     Map<String, String> reportValues,
                                     int passNumber,
                                     double drawdownPercent,
                                     String expertFallback) {
        if (reportValues == null) reportValues = Collections.emptyMap();

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
        return name != null
                && (name.equalsIgnoreCase("Inp_Magic_Number") || name.equalsIgnoreCase("MagicNumber"));
    }

    public static boolean isOrderCommentParameter(String name) {
        return name != null
                && (name.equalsIgnoreCase("Inp_Order_Comment") || name.equalsIgnoreCase("OrderComment"));
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
