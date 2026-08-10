package com.backtester.report;

import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds the data view used by the HTML gallery. The selected gallery
 * databank owns the retest/long-term result, while the explicitly selected
 * short-term databank owns the optimizer backtest and forward result.
 */
public final class DatabankGalleryDataResolver {

    private DatabankGalleryDataResolver() {
    }

    public static Resolution resolve(List<CombinedPass> galleryPasses,
                                     List<CombinedPass> shortTermPasses) {
        List<CombinedPass> gallery = galleryPasses != null ? galleryPasses : List.of();
        List<CombinedPass> shortTerm = shortTermPasses != null ? shortTermPasses : List.of();

        Map<StrategyIdentity, CombinedPass> exactIndex = new LinkedHashMap<>();
        Map<Integer, List<CombinedPass>> passIndex = new LinkedHashMap<>();
        for (CombinedPass pass : shortTerm) {
            if (pass == null || pass.getBacktestPass() == null) continue;
            exactIndex.putIfAbsent(identity(pass), pass);
            passIndex.computeIfAbsent(pass.getPassNumber(), ignored -> new ArrayList<>()).add(pass);
        }

        List<CombinedPass> resolved = new ArrayList<>();
        List<StrategyIdentity> missing = new ArrayList<>();
        for (CombinedPass galleryPass : gallery) {
            if (galleryPass == null || galleryPass.getBacktestPass() == null) continue;

            CombinedPass shortPass = exactIndex.get(identity(galleryPass));
            if (shortPass == null) {
                List<CombinedPass> sameNumber = passIndex.get(galleryPass.getPassNumber());
                if (sameNumber != null && sameNumber.size() == 1) {
                    // A user-assigned display name may have changed after the optimizer.
                    // Falling back is safe only while the pass number is unambiguous.
                    shortPass = sameNumber.get(0);
                }
            }

            if (shortPass == null) {
                missing.add(identity(galleryPass));
                continue;
            }

            Pass bt = copy(shortPass.getBacktestPass());
            Pass fw = copy(shortPass.getForwardPass());
            Pass lt = copy(galleryPass.getLongtermPass());
            CombinedPass combined = new CombinedPass(bt, fw, lt,
                    galleryPass.getScore(), galleryPass.getConsistency(), galleryPass.getScoreDetails());
            combined.setStrategyName(prefer(galleryPass.getStrategyName(), shortPass.getStrategyName()));
            combined.setSymbol(prefer(galleryPass.getSymbol(), shortPass.getSymbol()));
            combined.setPeriod(prefer(galleryPass.getPeriod(), shortPass.getPeriod()));
            combined.setReportDirectory(prefer(galleryPass.getReportDirectory(), shortPass.getReportDirectory()));
            resolved.add(combined);
        }

        return new Resolution(List.copyOf(resolved), List.copyOf(missing));
    }

    private static Pass copy(Pass pass) {
        return pass != null ? pass.copy() : null;
    }

    private static String prefer(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }

    private static StrategyIdentity identity(CombinedPass pass) {
        return new StrategyIdentity(pass.getPassNumber(), normalizeName(pass.getStrategyName()));
    }

    private static String normalizeName(String name) {
        return name != null ? name.trim().toLowerCase(java.util.Locale.ROOT) : "";
    }

    public record StrategyIdentity(int passNumber, String strategyName) {
        public StrategyIdentity {
            strategyName = Objects.requireNonNullElse(strategyName, "");
        }

        public String displayName() {
            return strategyName.isBlank()
                    ? "Pass #" + passNumber
                    : strategyName + " (Pass #" + passNumber + ")";
        }
    }

    public record Resolution(List<CombinedPass> passes,
                             List<StrategyIdentity> missingShortTermStrategies) {
        public boolean isComplete() {
            return missingShortTermStrategies.isEmpty();
        }
    }
}
