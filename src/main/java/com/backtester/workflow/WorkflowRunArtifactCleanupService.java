package com.backtester.workflow;

import com.backtester.config.AppConfig;
import com.backtester.config.EaParameterManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Purges on-disk MT5/optimizer artefacts that would otherwise make a "fresh"
 * guided workflow restart reuse stale cache (instant fake optimisations, empty
 * forward results, old report folders).
 */
public final class WorkflowRunArtifactCleanupService {
    private static final Logger log = LoggerFactory.getLogger(WorkflowRunArtifactCleanupService.class);

    private WorkflowRunArtifactCleanupService() {}

    public static final class CleanupResult {
        public int cacheFilesDeleted;
        public int reportFilesDeleted;
        public int reportDirectoryTreesDeleted;
        public final List<String> details = new ArrayList<>();

        public int totalDeleted() {
            return cacheFilesDeleted + reportFilesDeleted + reportDirectoryTreesDeleted;
        }

        public String summary() {
            return "Cache-Dateien: " + cacheFilesDeleted
                    + ", Report-Dateien: " + reportFilesDeleted
                    + ", Report-Ordner: " + reportDirectoryTreesDeleted;
        }
    }

    /**
     * Clears MT5 tester cache + leftover OptimizationReport* files for the
     * project's EA, and deletes optimizer output trees referenced by project tasks.
     */
    public static CleanupResult purgeProjectArtifacts(CustomProject project) {
        CleanupResult result = new CleanupResult();
        if (project == null) {
            return result;
        }

        String expert = project.getExpert();
        String eaBase = EaParameterManager.extractEaBaseName(expert);
        if (eaBase == null || eaBase.isBlank()) {
            eaBase = expert != null ? expert.trim() : "";
        }

        AppConfig config = AppConfig.getInstance();
        try {
            Path mtDir = config.getMtInstallDir(expert);
            if (mtDir != null && Files.isDirectory(mtDir)) {
                result.cacheFilesDeleted += deleteTesterCacheForExpert(mtDir, eaBase, result.details);
                result.reportFilesDeleted += deleteOptimizationReportFiles(mtDir, result.details);
            } else {
                result.details.add("MT5-Installationsordner nicht gefunden — Cache nicht gelöscht.");
            }
        } catch (Exception ex) {
            log.warn("Failed to resolve MT5 install dir for cache purge", ex);
            result.details.add("MT5-Pfad konnte nicht aufgelöst werden: " + ex.getMessage());
        }

        result.reportDirectoryTreesDeleted += deleteOptimizerOutputDirectories(project, result.details);
        return result;
    }

    /**
     * Deletes {@code Tester/cache/<EA>*.opt} (case-insensitive folder name).
     * Visible for unit tests.
     */
    static int deleteTesterCacheForExpert(Path mtDir, String eaBaseName, List<String> details) {
        if (mtDir == null || eaBaseName == null || eaBaseName.isBlank()) {
            return 0;
        }
        Path cacheDir = resolveTesterCacheDir(mtDir);
        if (cacheDir == null || !Files.isDirectory(cacheDir)) {
            if (details != null) {
                details.add("Kein Tester/cache-Ordner unter " + mtDir);
            }
            return 0;
        }
        String prefix = eaBaseName.trim().toLowerCase(Locale.ROOT);
        int deleted = 0;
        try (Stream<Path> stream = Files.list(cacheDir)) {
            List<Path> matches = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.startsWith(prefix) && name.endsWith(".opt");
                    })
                    .toList();
            for (Path file : matches) {
                try {
                    Files.deleteIfExists(file);
                    deleted++;
                    if (details != null) {
                        details.add("Cache gelöscht: " + file.getFileName());
                    }
                } catch (IOException ex) {
                    log.warn("Could not delete cache file {}", file, ex);
                    if (details != null) {
                        details.add("Cache nicht löschbar: " + file.getFileName() + " (" + ex.getMessage() + ")");
                    }
                }
            }
        } catch (IOException ex) {
            log.warn("Could not list tester cache {}", cacheDir, ex);
            if (details != null) {
                details.add("Cache-Ordner nicht lesbar: " + ex.getMessage());
            }
        }
        return deleted;
    }

    static Path resolveTesterCacheDir(Path mtDir) {
        Path upper = mtDir.resolve("Tester").resolve("cache");
        if (Files.isDirectory(upper)) {
            return upper;
        }
        Path lower = mtDir.resolve("tester").resolve("cache");
        if (Files.isDirectory(lower)) {
            return lower;
        }
        return upper;
    }

    static int deleteOptimizationReportFiles(Path mtDir, List<String> details) {
        int deleted = 0;
        deleted += deleteReportFilesIn(mtDir, details);
        deleted += deleteReportFilesIn(mtDir.resolve("Tester"), details);
        deleted += deleteReportFilesIn(mtDir.resolve("tester"), details);
        return deleted;
    }

    private static int deleteReportFilesIn(Path dir, List<String> details) {
        if (dir == null || !Files.isDirectory(dir)) {
            return 0;
        }
        int deleted = 0;
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> matches = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("OptimizationReport")
                                && (name.endsWith(".xml") || name.endsWith(".htm") || name.endsWith(".html")
                                || name.endsWith(".forward.xml"));
                    })
                    .toList();
            for (Path file : matches) {
                try {
                    Files.deleteIfExists(file);
                    deleted++;
                    if (details != null) {
                        details.add("Report gelöscht: " + file.getFileName());
                    }
                } catch (IOException ex) {
                    log.warn("Could not delete report file {}", file, ex);
                }
            }
        } catch (IOException ex) {
            log.warn("Could not list report dir {}", dir, ex);
        }
        return deleted;
    }

    static int deleteOptimizerOutputDirectories(CustomProject project, List<String> details) {
        if (project == null || project.getTasks() == null) {
            return 0;
        }
        Set<Path> roots = new LinkedHashSet<>();
        for (WorkflowTask task : project.getTasks()) {
            if (task == null || task.getType() != WorkflowTask.TaskType.OPTIMIZER) {
                continue;
            }
            String dir = task.getOptimizerOutputDirectory();
            if (dir == null || dir.isBlank()) {
                continue;
            }
            try {
                roots.add(Path.of(dir.trim()).toAbsolutePath().normalize());
            } catch (RuntimeException ex) {
                if (details != null) {
                    details.add("Ungültiger Optimizer-Ausgabeordner: " + dir);
                }
            }
        }
        int deletedTrees = 0;
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try {
                deletedTrees += deleteDirectoryContents(root);
                if (details != null) {
                    details.add("Optimizer-Ausgabe geleert: " + root);
                }
            } catch (IOException ex) {
                log.warn("Could not purge optimizer output dir {}", root, ex);
                if (details != null) {
                    details.add("Optimizer-Ausgabe nicht löschbar: " + root + " (" + ex.getMessage() + ")");
                }
            }
        }
        return deletedTrees;
    }

    /**
     * Deletes children of {@code root} but keeps {@code root} itself.
     * Returns number of top-level child entries removed.
     */
    static int deleteDirectoryContents(Path root) throws IOException {
        if (root == null || !Files.isDirectory(root)) {
            return 0;
        }
        int removed = 0;
        try (Stream<Path> stream = Files.list(root)) {
            List<Path> children = stream.toList();
            for (Path child : children) {
                deleteRecursively(child);
                removed++;
            }
        }
        return removed;
    }

    static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        if (Files.isDirectory(path)) {
            try (Stream<Path> stream = Files.list(path)) {
                for (Path child : stream.toList()) {
                    deleteRecursively(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    /** Package-visible helper for tests — same matching rule as production. */
    static boolean cacheFileMatchesExpert(String fileName, String eaBaseName) {
        if (fileName == null || eaBaseName == null || eaBaseName.isBlank()) {
            return false;
        }
        String name = fileName.toLowerCase(Locale.ROOT);
        String prefix = eaBaseName.trim().toLowerCase(Locale.ROOT);
        return name.startsWith(prefix) && name.endsWith(".opt");
    }

    static String requireNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? Objects.toString(fallback, "") : value.trim();
    }
}
