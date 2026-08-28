package com.backtester.workflow;

import com.backtester.report.OptimizationResult.CombinedPass;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stable B1…B10 identities on surviving {@link CombinedPass} rows.
 * Included in {@code DatabankManager} pass identity so sequential cluster
 * optimizer runs do not collapse overlapping MT5 pass numbers.
 *
 * <p>{@link #MAX_CLUSTERS} caps distinct form lines; {@link #MAX_POOLED_STRATEGIES}
 * caps how many stamped passes may remain (cousins share a Bn).
 */
public final class ClusterIdentity {

    /** Maximum distinct form-line ids (B1…B10). */
    public static final int MAX_CLUSTERS = 10;
    /** Maximum stamped strategies across all lines (cousins allowed). */
    public static final int MAX_POOLED_STRATEGIES = 100;
    private static final Pattern ID_PATTERN = Pattern.compile("^B([1-9]|10)$");

    private ClusterIdentity() {
    }

    public static String normalize(String clusterId) {
        if (clusterId == null || clusterId.isBlank()) {
            return null;
        }
        String trimmed = clusterId.trim().toUpperCase(Locale.ROOT);
        Matcher matcher = ID_PATTERN.matcher(trimmed);
        return matcher.matches() ? "B" + matcher.group(1) : null;
    }

    public static String normalize(CombinedPass pass) {
        return pass == null ? null : normalize(pass.getClusterId());
    }

    public static boolean hasId(CombinedPass pass) {
        return normalize(pass) != null;
    }

    /**
     * Assigns unused B-ids in list order. Existing ids are kept. Multiple passes
     * may share a Bn (cousins). At most {@link #MAX_POOLED_STRATEGIES} clustered
     * rows remain; extras without an id after line assignment are filled onto
     * existing lines, then the pool is score-capped.
     */
    public static List<CombinedPass> stampInOrder(List<CombinedPass> passes) {
        return stamp(passes, true);
    }

    /**
     * Unassigned survivors receive free B-ids (then cousins on existing lines),
     * ranked by score, up to {@link #MAX_POOLED_STRATEGIES} total.
     */
    public static List<CombinedPass> stampUnassignedByScore(List<CombinedPass> passes) {
        return stamp(passes, false);
    }

    static List<CombinedPass> stamp(List<CombinedPass> passes, boolean assignInListOrder) {
        if (passes == null || passes.isEmpty()) {
            return new ArrayList<>();
        }
        List<CombinedPass> work = new ArrayList<>();
        for (CombinedPass pass : passes) {
            if (pass == null || pass.getBacktestPass() == null) {
                continue;
            }
            String existing = normalize(pass);
            if (existing != null) {
                pass.setClusterId(existing);
            }
            work.add(pass);
        }

        Set<String> used = new LinkedHashSet<>();
        for (CombinedPass pass : work) {
            String id = normalize(pass);
            if (id != null) {
                used.add(id);
            }
        }

        List<CombinedPass> unassigned = new ArrayList<>();
        for (CombinedPass pass : work) {
            if (!hasId(pass)) {
                unassigned.add(pass);
            }
        }
        if (!assignInListOrder) {
            unassigned.sort(scoreThenPassNumber());
        }

        // Phase 1: fill distinct B1…B10.
        int next = 1;
        List<CombinedPass> stillUnassigned = new ArrayList<>();
        for (CombinedPass pass : unassigned) {
            if (used.size() >= MAX_CLUSTERS) {
                stillUnassigned.add(pass);
                continue;
            }
            while (next <= MAX_CLUSTERS && used.contains("B" + next)) {
                next++;
            }
            if (next > MAX_CLUSTERS) {
                stillUnassigned.add(pass);
                continue;
            }
            String id = "B" + next;
            pass.setClusterId(id);
            used.add(id);
            next++;
        }

        // Phase 2: remaining survivors become cousins on existing lines (round-robin).
        if (!stillUnassigned.isEmpty()) {
            List<String> lines = used.isEmpty() ? defaultLineIds() : new ArrayList<>(used);
            int cursor = 0;
            for (CombinedPass pass : stillUnassigned) {
                String id = lines.get(cursor % lines.size());
                pass.setClusterId(id);
                cursor++;
            }
        }

        List<CombinedPass> clustered = new ArrayList<>();
        List<CombinedPass> leftover = new ArrayList<>();
        for (CombinedPass pass : work) {
            if (hasId(pass)) {
                clustered.add(pass);
            } else {
                leftover.add(pass);
            }
        }
        if (clustered.size() > MAX_POOLED_STRATEGIES) {
            clustered.sort(scoreThenPassNumber());
            clustered = new ArrayList<>(clustered.subList(0, MAX_POOLED_STRATEGIES));
        }
        // Unassigned extras beyond the pool are dropped (they are not clusters).
        return clustered.isEmpty() && leftover.size() == work.size() ? work : clustered;
    }

    private static List<String> defaultLineIds() {
        List<String> lines = new ArrayList<>(MAX_CLUSTERS);
        for (int i = 1; i <= MAX_CLUSTERS; i++) {
            lines.add("B" + i);
        }
        return lines;
    }

    private static Comparator<CombinedPass> scoreThenPassNumber() {
        return Comparator
                .comparingDouble(CombinedPass::getScore).reversed()
                .thenComparingInt(CombinedPass::getPassNumber);
    }
}
