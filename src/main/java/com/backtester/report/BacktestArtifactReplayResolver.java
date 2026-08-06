package com.backtester.report;

import com.backtester.config.AppConfig;
import com.backtester.engine.BacktestConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the exact tester configuration and parameter preset behind an MT5
 * report artifact. This keeps a gallery replay tied to the displayed curve
 * instead of rebuilding a preset from mutable EA defaults.
 */
public final class BacktestArtifactReplayResolver {
    public static final String PRESET_SNAPSHOT_FILE = "expert-parameters.set";
    private static final Pattern PASS_PATTERN =
            Pattern.compile("pass[_#\\s-]*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter MT_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private BacktestArtifactReplayResolver() {
    }

    public static Replay resolve(Path reportsRoot, String artifactDirectoryName,
                                 int expectedPassNumber) throws IOException {
        if (reportsRoot == null) {
            throw new IOException("Das Backtest-Berichtsverzeichnis ist nicht konfiguriert.");
        }
        String safeDirectoryName = requireLeafFileName(artifactDirectoryName, "Report-Verzeichnis");
        Path normalizedRoot = reportsRoot.toAbsolutePath().normalize();
        Path realRoot = normalizedRoot.toRealPath();
        Path requestedArtifact = realRoot.resolve(safeDirectoryName).normalize();
        if (!requestedArtifact.getParent().equals(realRoot)
                || !Files.isDirectory(requestedArtifact)) {
            throw new IOException("Das angeforderte MT5-Report-Verzeichnis existiert nicht.");
        }
        Path artifactDirectory = requestedArtifact.toRealPath();
        if (!artifactDirectory.getParent().equals(realRoot)) {
            throw new IOException("Das angeforderte MT5-Report-Verzeichnis liegt außerhalb des Berichtsordners.");
        }

        Path ini = artifactDirectory.resolve("tester.ini");
        if (!Files.isRegularFile(ini)) {
            throw new IOException("Zum angezeigten MT5-Bild fehlt die tester.ini.");
        }
        Map<String, String> values = readIni(ini);

        String expert = required(values, "expert", "Expert");
        String presetName = requireLeafFileName(
                required(values, "expertparameters", "ExpertParameters"), "ExpertParameters");
        int actualPass = extractPassNumber(presetName);
        if (actualPass != expectedPassNumber) {
            throw new IOException("Das Preset des angezeigten MT5-Reports gehört nicht zu Pass #"
                    + expectedPassNumber + ".");
        }

        Path snapshot = artifactDirectory.resolve(PRESET_SNAPSHOT_FILE);
        Path presetSource;
        if (Files.isRegularFile(snapshot)) {
            presetSource = snapshot.toRealPath();
            if (!presetSource.getParent().equals(artifactDirectory)) {
                throw new IOException("Das archivierte Preset liegt außerhalb des MT5-Report-Verzeichnisses.");
            }
        } else {
            Path profileDirectory = AppConfig.getInstance().getTesterProfilesDir(expert)
                    .toAbsolutePath().normalize().toRealPath();
            Path requestedPreset = profileDirectory.resolve(presetName).normalize();
            if (!requestedPreset.getParent().equals(profileDirectory) || !Files.isRegularFile(requestedPreset)) {
                throw new IOException("Das für die angezeigte Kurve verwendete Preset '"
                        + presetName + "' wurde nicht gefunden.");
            }
            presetSource = requestedPreset.toRealPath();
            if (!presetSource.getParent().equals(profileDirectory)) {
                throw new IOException("Das verwendete Preset liegt außerhalb des MT5-Profilordners.");
            }
        }

        BacktestConfig config = new BacktestConfig();
        config.setExpert(expert);
        config.setExpertParameters(presetName);
        config.setSymbol(required(values, "symbol", "Symbol"));
        config.setPeriod(required(values, "period", "Period"));
        config.setModel(parseInt(values, "model", "Model"));
        config.setExecutionMode(parseInt(values, "executionmode", "ExecutionMode", 0));
        config.setFromDate(parseDate(values, "fromdate", "FromDate"));
        config.setToDate(parseDate(values, "todate", "ToDate"));
        config.setDeposit(parseInt(values, "deposit", "Deposit"));
        config.setCurrency(required(values, "currency", "Currency"));
        config.setLeverage(required(values, "leverage", "Leverage"));
        if (parseInt(values, "usedate", "UseDate", 1) != 1) {
            throw new IOException("Der angezeigte MT5-Report verwendet keinen festen Testzeitraum.");
        }
        if (parseInt(values, "optimization", "Optimization", 0) != 0) {
            throw new IOException("Ein Optimierungsartefakt kann nicht als Einzel-Backtest wiederholt werden.");
        }
        if (config.getDeposit() <= 0 || !config.getFromDate().isBefore(config.getToDate())) {
            throw new IOException("Die tester.ini enthält einen ungültigen Testzeitraum oder Kontostand.");
        }
        config.setOptimization(0);
        config.setShutdownTerminal(false);
        return new Replay(config, presetSource, presetName, artifactDirectory);
    }

    private static Map<String, String> readIni(Path ini) throws IOException {
        Map<String, String> values = new HashMap<>();
        for (String line : Files.readAllLines(ini, StandardCharsets.UTF_8)) {
            int separator = line.indexOf('=');
            if (separator <= 0) continue;
            values.put(line.substring(0, separator).trim().toLowerCase(Locale.ROOT),
                    line.substring(separator + 1).trim());
        }
        return values;
    }

    private static String required(Map<String, String> values, String key, String displayName)
            throws IOException {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IOException("In der tester.ini fehlt '" + displayName + "'.");
        }
        return value.trim();
    }

    private static int parseInt(Map<String, String> values, String key, String displayName)
            throws IOException {
        return parseInt(values, key, displayName, null);
    }

    private static int parseInt(Map<String, String> values, String key, String displayName,
                                Integer defaultValue) throws IOException {
        String value = values.get(key);
        if ((value == null || value.isBlank()) && defaultValue != null) return defaultValue;
        try {
            return Integer.parseInt(required(values, key, displayName));
        } catch (NumberFormatException ex) {
            throw new IOException("Ungültiger Wert für '" + displayName + "' in tester.ini.", ex);
        }
    }

    private static LocalDate parseDate(Map<String, String> values, String key, String displayName)
            throws IOException {
        try {
            return LocalDate.parse(required(values, key, displayName), MT_DATE);
        } catch (RuntimeException ex) {
            throw new IOException("Ungültiges Datum für '" + displayName + "' in tester.ini.", ex);
        }
    }

    private static String requireLeafFileName(String value, String displayName) throws IOException {
        if (value == null || value.isBlank()) {
            throw new IOException(displayName + " fehlt.");
        }
        String trimmed = value.trim();
        Path path;
        try {
            path = Path.of(trimmed);
        } catch (RuntimeException ex) {
            throw new IOException("Ungültiger Wert für " + displayName + ".", ex);
        }
        if (path.getNameCount() != 1 || !path.getFileName().toString().equals(trimmed)
                || trimmed.equals(".") || trimmed.equals("..")) {
            throw new IOException("Ungültiger Pfad für " + displayName + ".");
        }
        return trimmed;
    }

    private static int extractPassNumber(String presetName) {
        Matcher matcher = PASS_PATTERN.matcher(presetName);
        if (!matcher.find()) return -1;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    public record Replay(BacktestConfig config, Path presetSource,
                         String originalPresetName, Path artifactDirectory) {
    }
}
