package com.backtester.workflow;

import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Helpers for the per-project strategy backtest archive (tab-keyed upsert).
 */
public final class StrategyBacktestArchiveStore {

    private static final Charset MT5_CHARSET = StandardCharsets.UTF_16LE;
    /** Same identity separator as {@link DatabankManager}. */
    private static final char IDENTITY_SEP = '\u0000';

    private StrategyBacktestArchiveStore() {
    }

    public static String strategyKey(CombinedPass pass) {
        if (pass == null) return "<null>";
        return strategyKey(pass.getPassNumber(), pass.getStrategyName());
    }

    public static String strategyKey(int passNumber, String strategyName) {
        String name = strategyName != null ? strategyName : "";
        return passNumber + IDENTITY_SEP + name;
    }

    public static List<StrategyBacktestRun> getAllRuns(CustomProject project, String strategyKey) {
        StrategyBacktestArchive archive = getArchive(project, strategyKey);
        return archive != null ? archive.getAllRuns() : List.of();
    }

    public static List<StrategyBacktestRun> getAllRuns(CustomProject project, CombinedPass pass) {
        return getAllRuns(project, strategyKey(pass));
    }

    public static Optional<StrategyBacktestRun> getRun(CustomProject project, String strategyKey, String tabName) {
        StrategyBacktestArchive archive = getArchive(project, strategyKey);
        if (archive == null || tabName == null) return Optional.empty();
        return Optional.ofNullable(archive.getRun(tabName));
    }

    public static StrategyBacktestArchive getOrCreateArchive(CustomProject project, CombinedPass pass) {
        if (project == null || pass == null) {
            throw new IllegalArgumentException("project and pass required");
        }
        String key = strategyKey(pass);
        Map<String, StrategyBacktestArchive> archives = project.getStrategyArchives();
        StrategyBacktestArchive archive = archives.get(key);
        if (archive == null) {
            archive = new StrategyBacktestArchive(key, pass.getStrategyName(), pass.getPassNumber());
            archives.put(key, archive);
        } else {
            archive.setStrategyName(pass.getStrategyName());
            archive.setPassNumber(pass.getPassNumber());
        }
        return archive;
    }

    public static void upsertRun(CustomProject project, CombinedPass pass, StrategyBacktestRun run) {
        if (project == null || pass == null || run == null) return;
        getOrCreateArchive(project, pass).upsert(run);
    }

    /**
     * Seeds archive entries from existing {@link CombinedPass#getLongtermPass()} when a tab has
     * a result but no archive run yet (legacy projects).
     */
    public static void migrateFromLongtermPasses(CustomProject project) {
        if (project == null) return;
        Map<String, List<CombinedPass>> databanks = project.getDatabanks();
        if (databanks == null || databanks.isEmpty()) return;

        for (Map.Entry<String, List<CombinedPass>> entry : databanks.entrySet()) {
            String tabName = entry.getKey();
            if (tabName == null || entry.getValue() == null) continue;
            for (CombinedPass pass : entry.getValue()) {
                if (pass == null || pass.getLongtermPass() == null) continue;
                String key = strategyKey(pass);
                StrategyBacktestArchive archive = getOrCreateArchive(project, pass);
                if (archive.getRun(tabName) != null) continue;

                Pass lt = pass.getLongtermPass();
                StrategyBacktestRun run = new StrategyBacktestRun();
                run.setTabName(tabName);
                run.setTaskName("");
                run.setSymbol(pass.getSymbol() != null ? pass.getSymbol() : "");
                run.setPeriod(pass.getPeriod() != null ? pass.getPeriod() : "");
                run.setTickModel(lt.getTickModel());
                run.setFromDate(lt.getFromDate());
                run.setToDate(lt.getToDate());
                run.setCompletedAt(0L);
                run.setSetfileContent("");
                run.setResult(lt.copy());
                archive.upsert(run);
            }
        }
    }

    public static LinkedHashMap<String, StrategyBacktestArchive> copyArchives(
            Map<String, StrategyBacktestArchive> source) {
        LinkedHashMap<String, StrategyBacktestArchive> copy = new LinkedHashMap<>();
        if (source == null) return copy;
        for (Map.Entry<String, StrategyBacktestArchive> entry : source.entrySet()) {
            StrategyBacktestArchive archive = entry.getValue();
            copy.put(entry.getKey(), archive != null ? archive.copy() : null);
        }
        return copy;
    }

    /**
     * Reads an MT5 .set file as a Java String (UTF-16LE with optional BOM, else UTF-8).
     */
    public static String readSetfileContent(Path setFile) {
        if (setFile == null || !Files.isRegularFile(setFile)) return "";
        try {
            byte[] bytes = Files.readAllBytes(setFile);
            if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
                return new String(bytes, MT5_CHARSET);
            }
            boolean hasNulls = false;
            for (byte b : bytes) {
                if (b == 0) {
                    hasNulls = true;
                    break;
                }
            }
            if (hasNulls) {
                return new String(bytes, MT5_CHARSET);
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return "";
        }
    }

    public static StrategyBacktestRun buildRun(String tabName,
                                              String taskName,
                                              String symbol,
                                              String period,
                                              String tickModel,
                                              String fromDate,
                                              String toDate,
                                              String setfileContent,
                                              Pass result) {
        StrategyBacktestRun run = new StrategyBacktestRun();
        run.setTabName(tabName != null && !tabName.isBlank() ? tabName : DatabankManager.RESULTS);
        run.setTaskName(taskName != null ? taskName : "");
        run.setSymbol(symbol != null ? symbol : "");
        run.setPeriod(period != null ? period : "");
        run.setTickModel(tickModel != null ? tickModel : "");
        run.setFromDate(fromDate != null ? fromDate : "");
        run.setToDate(toDate != null ? toDate : "");
        run.setCompletedAt(System.currentTimeMillis());
        run.setSetfileContent(setfileContent != null ? setfileContent : "");
        run.setResult(result != null ? result.copy() : null);
        return run;
    }

    private static StrategyBacktestArchive getArchive(CustomProject project, String strategyKey) {
        if (project == null || strategyKey == null) return null;
        return project.getStrategyArchives().get(strategyKey);
    }

    /** Visible for tests: all runs across all strategies. */
    public static List<StrategyBacktestRun> flattenAllRuns(CustomProject project) {
        List<StrategyBacktestRun> all = new ArrayList<>();
        if (project == null) return all;
        for (StrategyBacktestArchive archive : project.getStrategyArchives().values()) {
            if (archive != null) all.addAll(archive.getAllRuns());
        }
        return all;
    }
}
