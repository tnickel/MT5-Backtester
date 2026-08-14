package com.backtester.workflow;

import com.backtester.database.DatabaseManager;
import com.backtester.report.OptimizationResult.CombinedPass;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Full project dump and restore. A backup is the JSON of the live project as it
 * currently is — tasks, settings, databanks, archives, lineage and proven master.
 * Restore replaces the open project's contents and keeps its SQLite identity.
 * Nothing is rewritten, repaired or migrated on the way in or out.
 */
public final class CustomProjectBackup {

    private CustomProjectBackup() {
    }

    public static String toJson(CustomProject project, DatabankManager databankManager) {
        if (project == null) {
            throw new IllegalArgumentException("Kein Projekt zum Sichern.");
        }
        CustomProject snapshot = project.copyMetadataForPersistence();
        if (databankManager != null) {
            databankManager.saveToProject(snapshot, true);
        } else {
            snapshot.setDatabanks(copyDatabanks(project.getDatabanks()));
        }
        snapshot.setStrategyArchives(
                StrategyBacktestArchiveStore.copyArchives(project.getStrategyArchives()));
        return DatabaseManager.createCustomProjectGson().toJson(snapshot);
    }

    public static CustomProject fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Backup-Datei ist leer.");
        }
        CustomProject loaded = DatabaseManager.createCustomProjectGson()
                .fromJson(json, CustomProject.class);
        if (loaded == null) {
            throw new IllegalArgumentException("Backup-Datei enthaelt kein Projekt.");
        }
        return loaded;
    }

    /**
     * Overlays the backup onto the currently open project so SQLite updates the
     * same row instead of inserting a second project.
     */
    public static CustomProject restoreInto(CustomProject current, CustomProject backup) {
        if (backup == null) {
            throw new IllegalArgumentException("Backup-Datei enthaelt kein Projekt.");
        }
        if (current != null) {
            backup.setId(current.getId());
            backup.setSortOrder(current.getSortOrder());
        }
        return backup;
    }

    private static Map<String, List<CombinedPass>> copyDatabanks(Map<String, List<CombinedPass>> source) {
        Map<String, List<CombinedPass>> copy = new LinkedHashMap<>();
        if (source == null) {
            return copy;
        }
        for (Map.Entry<String, List<CombinedPass>> entry : source.entrySet()) {
            copy.put(entry.getKey(), entry.getValue() != null
                    ? new ArrayList<>(entry.getValue())
                    : new ArrayList<>());
        }
        return copy;
    }
}
