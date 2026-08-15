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
 */
public final class ClusterIdentity {

    public static final int MAX_CLUSTERS = 10;
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
     * Assigns unused B-ids in list order. Existing ids are kept. At most
     * {@link #MAX_CLUSTERS} clustered rows remain; extras without an id are dropped.
     * If more than ten already-stamped ids exist, the highest-score ten are kept.
     */
    public static List<CombinedPass> stampInOrder(List<CombinedPass> passes) {
        return stamp(passes, true);
    }

    /**
     * Unassigned survivors receive the next free B-ids, ranked by
     * {@link GuidedOptimizationService#selectBestPass} order, up to ten clusters.
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

        int next = 1;
        for (CombinedPass pass : unassigned) {
            if (used.size() >= MAX_CLUSTERS) {
                break;
            }
            while (next <= MAX_CLUSTERS && used.contains("B" + next)) {
                next++;
            }
            if (next > MAX_CLUSTERS) {
                break;
            }
            String id = "B" + next;
            pass.setClusterId(id);
            used.add(id);
            next++;
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
        if (clustered.size() > MAX_CLUSTERS) {
            clustered.sort(scoreThenPassNumber());
            clustered = new ArrayList<>(clustered.subList(0, MAX_CLUSTERS));
        }
        // Unassigned extras beyond the cap are dropped (they are not clusters).
        return clustered.isEmpty() && leftover.size() == work.size() ? work : clustered;
    }

    private static Comparator<CombinedPass> scoreThenPassNumber() {
        return Comparator
                .comparingDouble(CombinedPass::getScore).reversed()
                .thenComparingInt(CombinedPass::getPassNumber);
    }
}
