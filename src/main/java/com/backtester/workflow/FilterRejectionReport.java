package com.backtester.workflow;

import com.backtester.report.OptimizationResult.CombinedPass;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Makes it visible when a quality filter removes the very pass the next stage would
 * have inherited.
 *
 * <p>The staged hand-off picks the highest unified score from the filtered databank.
 * If the score leader is dropped one threshold short, the chain silently continues on
 * a weaker branch — which looks like "the master strategy stopped improving". This
 * report does not change the filter decision, it only names what was lost.
 */
public final class FilterRejectionReport {

    private FilterRejectionReport() {
    }

    /**
     * Note about the score leader that the filter removed, or empty when the leader
     * survived (or there was nothing to compare).
     */
    public static String describeDroppedLeader(WorkflowTask task,
                                               List<CombinedPass> candidates,
                                               List<CombinedPass> accepted) {
        if (task == null || candidates == null || candidates.isEmpty()) return "";
        if (task.getFilterConditions().isEmpty()) return "";

        CombinedPass leader = GuidedOptimizationService.selectBestPass(candidates).orElse(null);
        if (leader == null) return "";
        if (containsIdentity(accepted, leader)) return "";

        List<String> reasons = new ArrayList<>();
        for (FilterCondition condition : task.getFilterConditions()) {
            if (condition == null) continue;
            String failure = condition.describeFailure(leader);
            if (!failure.isBlank()) reasons.add(failure);
        }

        CombinedPass survivor = GuidedOptimizationService.selectBestPass(accepted).orElse(null);
        StringBuilder note = new StringBuilder();
        note.append(String.format(Locale.ROOT,
                "Filter „%s“ hat den Score-Besten verworfen: Pass #%d (Score %.1f). Grund: %s.",
                task.getName() != null ? task.getName() : "",
                leader.getPassNumber(),
                leader.getScore(),
                reasons.isEmpty() ? "Bedingung nicht erfüllt" : String.join("; ", reasons)));
        if (survivor != null) {
            note.append(String.format(Locale.ROOT,
                    " Übernommen wird stattdessen Pass #%d (Score %.1f, %+.1f).",
                    survivor.getPassNumber(), survivor.getScore(),
                    survivor.getScore() - leader.getScore()));
        } else {
            note.append(" Es hat kein Pass den Filter überstanden.");
        }
        return note.toString();
    }

    private static boolean containsIdentity(List<CombinedPass> passes, CombinedPass needle) {
        if (passes == null || needle == null) return false;
        for (CombinedPass pass : passes) {
            if (pass == null) continue;
            if (pass == needle) return true;
            if (pass.getPassNumber() == needle.getPassNumber()
                    && sameName(pass.getStrategyName(), needle.getStrategyName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameName(String left, String right) {
        String a = left != null ? left.trim() : "";
        String b = right != null ? right.trim() : "";
        return a.equalsIgnoreCase(b);
    }
}
