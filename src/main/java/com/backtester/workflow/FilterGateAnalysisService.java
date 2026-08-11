package com.backtester.workflow;

import com.backtester.config.EaParameter;
import com.backtester.report.OptimizationReportParser;
import com.backtester.report.OptimizationResult;
import com.backtester.report.OptimizationResult.CombinedPass;
import com.backtester.report.OptimizationResult.Pass;
import com.backtester.report.OptimizationResult.ScoreWeights;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Splits an optimizer run by a boolean Use_* gate and aggregates cohort metrics
 * so a workflow tile can show whether the filter helped.
 */
public final class FilterGateAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(FilterGateAnalysisService.class);

    public static final int DEFAULT_MIN_COHORT_SIZE = 5;
    public static final int DEFAULT_TOP_N = 10;
    /** Relative median-score gap required for a decisive verdict (10%). */
    public static final double DEFAULT_SCORE_MARGIN = 0.10;

    private static final String REPORT_FILE = "optimization_report.xml";
    private static final String FORWARD_FILE = "optimization_forward.xml";

    private FilterGateAnalysisService() {
    }

    public enum DataSource {
        OPTIMIZER_REPORT,
        DATABANK_FALLBACK
    }

    public enum Verdict {
        FILTER_ON_BETTER,
        FILTER_OFF_BETTER,
        UNCLEAR,
        INSUFFICIENT_DATA,
        GATE_MISSING
    }

    public enum GateValue {
        ON,
        OFF,
        UNKNOWN
    }

    public static final class CohortStats {
        private final int count;
        private final double medianProfit;
        private final double medianScore;
        private final double medianDrawdownPct;
        private final double medianTrades;
        private final double q3Profit;
        private final double q3Score;
        private final List<CombinedPass> topByScore;

        public CohortStats(int count,
                           double medianProfit,
                           double medianScore,
                           double medianDrawdownPct,
                           double medianTrades,
                           double q3Profit,
                           double q3Score,
                           List<CombinedPass> topByScore) {
            this.count = count;
            this.medianProfit = medianProfit;
            this.medianScore = medianScore;
            this.medianDrawdownPct = medianDrawdownPct;
            this.medianTrades = medianTrades;
            this.q3Profit = q3Profit;
            this.q3Score = q3Score;
            this.topByScore = topByScore != null ? List.copyOf(topByScore) : List.of();
        }

        public int getCount() { return count; }
        public double getMedianProfit() { return medianProfit; }
        public double getMedianScore() { return medianScore; }
        public double getMedianDrawdownPct() { return medianDrawdownPct; }
        public double getMedianTrades() { return medianTrades; }
        public double getQ3Profit() { return q3Profit; }
        public double getQ3Score() { return q3Score; }
        public List<CombinedPass> getTopByScore() { return topByScore; }
    }

    public static final class PassLoadResult {
        private final List<CombinedPass> passes;
        private final DataSource dataSource;
        private final String sourcePath;
        private final String databankName;

        public PassLoadResult(List<CombinedPass> passes,
                              DataSource dataSource,
                              String sourcePath,
                              String databankName) {
            this.passes = passes != null ? List.copyOf(passes) : List.of();
            this.dataSource = dataSource != null ? dataSource : DataSource.DATABANK_FALLBACK;
            this.sourcePath = sourcePath != null ? sourcePath : "";
            this.databankName = databankName != null ? databankName : "";
        }

        public List<CombinedPass> getPasses() { return passes; }
        public DataSource getDataSource() { return dataSource; }
        public String getSourcePath() { return sourcePath; }
        public String getDatabankName() { return databankName; }
        public boolean isFallback() { return dataSource == DataSource.DATABANK_FALLBACK; }
    }

    public static final class FilterGateAnalysis {
        private final String gateParameter;
        private final DataSource dataSource;
        private final String sourcePath;
        private final String databankName;
        private final CohortStats onStats;
        private final CohortStats offStats;
        private final int unknownCount;
        private final int topNOnCount;
        private final int topNTotal;
        private final Verdict verdict;
        private final String verdictMessage;
        private final List<String> candidateGateParameters;
        private final List<String> optimizedParameterNames;

        public FilterGateAnalysis(String gateParameter,
                                  DataSource dataSource,
                                  String sourcePath,
                                  String databankName,
                                  CohortStats onStats,
                                  CohortStats offStats,
                                  int unknownCount,
                                  int topNOnCount,
                                  int topNTotal,
                                  Verdict verdict,
                                  String verdictMessage,
                                  List<String> candidateGateParameters) {
            this(gateParameter, dataSource, sourcePath, databankName, onStats, offStats,
                    unknownCount, topNOnCount, topNTotal, verdict, verdictMessage,
                    candidateGateParameters, List.of());
        }

        public FilterGateAnalysis(String gateParameter,
                                  DataSource dataSource,
                                  String sourcePath,
                                  String databankName,
                                  CohortStats onStats,
                                  CohortStats offStats,
                                  int unknownCount,
                                  int topNOnCount,
                                  int topNTotal,
                                  Verdict verdict,
                                  String verdictMessage,
                                  List<String> candidateGateParameters,
                                  List<String> optimizedParameterNames) {
            this.gateParameter = gateParameter != null ? gateParameter : "";
            this.dataSource = dataSource != null ? dataSource : DataSource.DATABANK_FALLBACK;
            this.sourcePath = sourcePath != null ? sourcePath : "";
            this.databankName = databankName != null ? databankName : "";
            this.onStats = onStats;
            this.offStats = offStats;
            this.unknownCount = Math.max(0, unknownCount);
            this.topNOnCount = Math.max(0, topNOnCount);
            this.topNTotal = Math.max(0, topNTotal);
            this.verdict = verdict != null ? verdict : Verdict.UNCLEAR;
            this.verdictMessage = verdictMessage != null ? verdictMessage : "";
            this.candidateGateParameters = candidateGateParameters != null
                    ? List.copyOf(candidateGateParameters) : List.of();
            this.optimizedParameterNames = optimizedParameterNames != null
                    ? List.copyOf(optimizedParameterNames) : List.of();
        }

        public String getGateParameter() { return gateParameter; }
        public DataSource getDataSource() { return dataSource; }
        public String getSourcePath() { return sourcePath; }
        public String getDatabankName() { return databankName; }
        public boolean isFallback() { return dataSource == DataSource.DATABANK_FALLBACK; }
        public CohortStats getOnStats() { return onStats; }
        public CohortStats getOffStats() { return offStats; }
        public int getUnknownCount() { return unknownCount; }
        public int getTopNOnCount() { return topNOnCount; }
        public int getTopNTotal() { return topNTotal; }
        public Verdict getVerdict() { return verdict; }
        public String getVerdictMessage() { return verdictMessage; }
        public List<String> getCandidateGateParameters() { return candidateGateParameters; }
        public List<String> getOptimizedParameterNames() { return optimizedParameterNames; }

        public int getTotalPassCount() {
            return (onStats != null ? onStats.getCount() : 0)
                    + (offStats != null ? offStats.getCount() : 0)
                    + unknownCount;
        }
    }

    /** Resolves the primary Use_* gate from task targets / snapshot / pass sample. */
    public static Optional<String> resolveGateParameter(WorkflowTask task) {
        List<String> candidates = listGateParameterCandidates(task, null);
        return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.get(0));
    }

    public static Optional<String> resolveGateParameter(WorkflowTask task, List<CombinedPass> samplePasses) {
        List<String> candidates = listGateParameterCandidates(task, samplePasses);
        return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.get(0));
    }

    /**
     * Gates that Automatik / analysis should evaluate: Prefer Use_* names listed as
     * optimizer targets (stage definition order). If the stage has no Use_* targets,
     * returns empty — report-only Use_* columns are never auto-forced.
     */
    public static List<String> selectGatesForAnalysis(WorkflowTask producer, List<String> candidates) {
        if (candidates == null || candidates.isEmpty() || producer == null) {
            return List.of();
        }
        List<String> fromTargets = new ArrayList<>();
        for (String target : producer.getOptimizerTargetParameters()) {
            if (!looksLikeUseGate(target)) continue;
            String trimmed = target.trim();
            for (String candidate : candidates) {
                if (candidate != null && candidate.equalsIgnoreCase(trimmed)) {
                    fromTargets.add(candidate);
                    break;
                }
            }
        }
        return fromTargets;
    }

    /** True when the audit string names this gate (single or comma-separated list). */
    public static boolean gateAuditMentions(String auditParameter, String gateName) {
        if (auditParameter == null || auditParameter.isBlank() || gateName == null) return false;
        if (auditParameter.trim().equalsIgnoreCase(gateName.trim())) return true;
        for (String part : auditParameter.split(",")) {
            if (part != null && part.trim().equalsIgnoreCase(gateName.trim())) return true;
        }
        return false;
    }

    /**
     * Maps multi-gate forced values ("true, false") onto the matching gate name
     * by position in the audit parameter list.
     */
    public static String resolveForcedValueForGate(String auditParameter,
                                                   String forcedValues,
                                                   String gateName) {
        if (forcedValues == null || forcedValues.isBlank()) return "";
        if (auditParameter == null || auditParameter.isBlank() || gateName == null) {
            return forcedValues.trim();
        }
        String[] gates = auditParameter.split(",");
        String[] vals = forcedValues.split(",");
        for (int i = 0; i < gates.length; i++) {
            if (gates[i] != null && gates[i].trim().equalsIgnoreCase(gateName.trim())) {
                return i < vals.length && vals[i] != null ? vals[i].trim() : forcedValues.trim();
            }
        }
        return forcedValues.trim();
    }

    /** Human label for forced value audit text (single or multi). */
    public static String formatForcedGateBadge(String auditParameter, String forcedValues) {
        if (forcedValues == null || forcedValues.isBlank()) return "FILTER ERZWUNGEN";
        if (auditParameter != null && auditParameter.contains(",")) {
            return "FILTER MULTI → SETFILE (" + forcedValues.trim() + ")";
        }
        if ("true".equalsIgnoreCase(forcedValues.trim())) return "FILTER AN → SETFILE";
        if ("false".equalsIgnoreCase(forcedValues.trim())) return "FILTER AUS → SETFILE";
        return "FILTER → SETFILE (" + forcedValues.trim() + ")";
    }

    public static List<String> listGateParameterCandidates(WorkflowTask task, List<CombinedPass> samplePasses) {
        LinkedUnique names = new LinkedUnique();
        if (task != null) {
            for (String name : task.getOptimizerTargetParameters()) {
                if (looksLikeUseGate(name)) names.add(name);
            }
            for (EaParameter param : task.getOptimizerParameterSnapshot()) {
                if (param == null || param.isSectionHeader()) continue;
                if (param.isOptimizeEnabled() && looksLikeUseGate(param.getName())) {
                    names.add(param.getName());
                }
            }
        }
        if (samplePasses != null) {
            for (CombinedPass pass : samplePasses) {
                Pass bt = pass != null ? pass.getBacktestPass() : null;
                if (bt == null || bt.getParameterValues() == null) continue;
                for (String name : bt.getParameterValues().keySet()) {
                    if (looksLikeUseGate(name)) names.add(name);
                }
            }
        }
        return names.toList().stream()
                .sorted(Comparator
                        .comparingInt((String n) -> gatePriority(n))
                        .thenComparing(n -> n, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    /**
     * Names that were varied in this optimizer stage (targets, enabled snapshot, or report columns).
     * Used when no Use_* gate exists so the UI can explain what was optimized instead.
     */
    public static List<String> listOptimizedParameterNames(WorkflowTask task, List<CombinedPass> samplePasses) {
        LinkedUnique names = new LinkedUnique();
        if (task != null) {
            for (String name : task.getOptimizerTargetParameters()) {
                if (name != null && !name.isBlank()) names.add(name.trim());
            }
            for (EaParameter param : task.getOptimizerParameterSnapshot()) {
                if (param == null || param.isSectionHeader() || !param.isOptimizeEnabled()) continue;
                if (param.getName() != null && !param.getName().isBlank()) names.add(param.getName().trim());
            }
        }
        if (names.toList().isEmpty() && samplePasses != null) {
            for (CombinedPass pass : samplePasses) {
                Pass bt = pass != null ? pass.getBacktestPass() : null;
                if (bt == null || bt.getParameterValues() == null) continue;
                for (String name : bt.getParameterValues().keySet()) {
                    if (name != null && !name.isBlank()) names.add(name.trim());
                }
                if (!names.toList().isEmpty()) break;
            }
        }
        return names.toList().stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    public static String formatNoGateExplanation(int passCount, List<String> optimizedParameterNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("Kein An/Aus-Filter-Schalter (Use_*) in dieser Stufe. ");
        sb.append("Es wurde nur optimiert (Parameterwerte variiert), kein Filter ein- oder ausgeschaltet. ");
        sb.append("Die ").append(Math.max(0, passCount)).append(" Optimizer-Passes sind vorhanden — ");
        sb.append("nichts wurde für diese Analyse weggefiltert. ");
        List<String> params = optimizedParameterNames != null ? optimizedParameterNames : List.of();
        if (!params.isEmpty()) {
            sb.append("Optimierte Parameter: ").append(String.join(", ", params)).append('.');
        } else {
            sb.append("Ein An/Aus-Vergleich ist für diese Kachel nicht möglich.");
        }
        return sb.toString();
    }

    public static Boolean normalizeBoolean(String raw) {
        if (raw == null) return null;
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (v.isEmpty()) return null;
        if ("true".equals(v) || "1".equals(v) || "yes".equals(v) || "y".equals(v) || "on".equals(v)) {
            return Boolean.TRUE;
        }
        if ("false".equals(v) || "0".equals(v) || "no".equals(v) || "n".equals(v) || "off".equals(v)) {
            return Boolean.FALSE;
        }
        return null;
    }

    public static GateValue classifyGate(CombinedPass pass, String gateParameter) {
        if (pass == null || pass.getBacktestPass() == null
                || gateParameter == null || gateParameter.isBlank()) {
            return GateValue.UNKNOWN;
        }
        String raw = pass.getBacktestPass().getParameter(gateParameter);
        if (raw == null || raw.isBlank()) {
            // Case-insensitive fallback for report header quirks
            for (var entry : pass.getBacktestPass().getParameterValues().entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(gateParameter)) {
                    raw = entry.getValue();
                    break;
                }
            }
        }
        Boolean normalized = normalizeBoolean(raw);
        if (normalized == null) return GateValue.UNKNOWN;
        return normalized ? GateValue.ON : GateValue.OFF;
    }

    public static FilterGateAnalysis analyze(List<CombinedPass> passes,
                                             String gateParameter,
                                             DataSource dataSource,
                                             String sourcePath,
                                             String databankName) {
        return analyze(passes, gateParameter, dataSource, sourcePath, databankName,
                DEFAULT_MIN_COHORT_SIZE, DEFAULT_TOP_N, DEFAULT_SCORE_MARGIN, List.of(), List.of());
    }

    public static FilterGateAnalysis analyze(List<CombinedPass> passes,
                                             String gateParameter,
                                             DataSource dataSource,
                                             String sourcePath,
                                             String databankName,
                                             int minCohortSize,
                                             int topN,
                                             double scoreMargin,
                                             List<String> candidateGateParameters) {
        return analyze(passes, gateParameter, dataSource, sourcePath, databankName,
                minCohortSize, topN, scoreMargin, candidateGateParameters, List.of());
    }

    public static FilterGateAnalysis analyze(List<CombinedPass> passes,
                                             String gateParameter,
                                             DataSource dataSource,
                                             String sourcePath,
                                             String databankName,
                                             int minCohortSize,
                                             int topN,
                                             double scoreMargin,
                                             List<String> candidateGateParameters,
                                             List<String> optimizedParameterNames) {
        List<CombinedPass> safePasses = passes != null ? passes : List.of();
        List<String> optimized = (optimizedParameterNames == null || optimizedParameterNames.isEmpty())
                ? listOptimizedParameterNames(null, safePasses)
                : List.copyOf(optimizedParameterNames);
        List<CombinedPass> on = new ArrayList<>();
        List<CombinedPass> off = new ArrayList<>();
        int unknown = 0;

        if (gateParameter == null || gateParameter.isBlank()) {
            CohortStats empty = emptyCohort();
            String message = appendFallbackNote(
                    formatNoGateExplanation(safePasses.size(), optimized), dataSource);
            return new FilterGateAnalysis("", dataSource, sourcePath, databankName,
                    empty, empty, safePasses.size(), 0, 0, Verdict.GATE_MISSING,
                    message, candidateGateParameters, optimized);
        }

        for (CombinedPass pass : safePasses) {
            if (pass == null || pass.getBacktestPass() == null) {
                unknown++;
                continue;
            }
            switch (classifyGate(pass, gateParameter)) {
                case ON -> on.add(pass);
                case OFF -> off.add(pass);
                default -> unknown++;
            }
        }

        CohortStats onStats = buildCohort(on, topN);
        CohortStats offStats = buildCohort(off, topN);

        List<CombinedPass> ranked = safePasses.stream()
                .filter(p -> p != null && p.getBacktestPass() != null && Double.isFinite(p.getScore()))
                .sorted(Comparator.comparingDouble(CombinedPass::getScore).reversed()
                        .thenComparingInt(CombinedPass::getPassNumber))
                .limit(Math.max(1, topN))
                .collect(Collectors.toList());
        int topNTotal = ranked.size();
        int topNOn = 0;
        for (CombinedPass pass : ranked) {
            if (classifyGate(pass, gateParameter) == GateValue.ON) topNOn++;
        }

        VerdictVerdict vv = decideVerdict(onStats, offStats, minCohortSize, scoreMargin, dataSource);
        return new FilterGateAnalysis(gateParameter, dataSource, sourcePath, databankName,
                onStats, offStats, unknown, topNOn, topNTotal, vv.verdict, vv.message,
                candidateGateParameters, optimized);
    }

    /**
     * Loads passes for an optimizer task: report directory first, databank fallback second.
     */
    public static PassLoadResult loadPassesForTask(WorkflowTask task,
                                                   String optimizerOutputDirectory,
                                                   DatabankManager databankManager) {
        Path reportDir = findLatestReportDirectory(optimizerOutputDirectory);
        if (reportDir != null) {
            try {
                List<CombinedPass> fromReport = parseReportDirectory(reportDir);
                if (!fromReport.isEmpty()) {
                    return new PassLoadResult(fromReport, DataSource.OPTIMIZER_REPORT,
                            reportDir.toAbsolutePath().normalize().toString(), "");
                }
            } catch (Exception ex) {
                log.warn("Failed to parse optimizer report in {}: {}", reportDir, ex.toString());
            }
        }

        String databankName = task != null ? task.getTargetDatabank() : "";
        List<CombinedPass> fromDb = List.of();
        if (databankManager != null && databankName != null && !databankName.isBlank()) {
            fromDb = databankManager.getDatabank(databankName);
        }
        return new PassLoadResult(fromDb, DataSource.DATABANK_FALLBACK,
                reportDir != null ? reportDir.toAbsolutePath().normalize().toString() : blank(optimizerOutputDirectory),
                databankName != null ? databankName : "");
    }

    public static Path findLatestReportDirectory(String optimizerOutputDirectory) {
        if (optimizerOutputDirectory == null || optimizerOutputDirectory.isBlank()) return null;
        Path base;
        try {
            base = Paths.get(optimizerOutputDirectory.trim());
        } catch (Exception ex) {
            return null;
        }
        if (!Files.isDirectory(base)) return null;

        if (Files.isRegularFile(base.resolve(REPORT_FILE))) {
            return base;
        }

        Path best = null;
        long bestTime = Long.MIN_VALUE;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(base)) {
            for (Path child : stream) {
                if (!Files.isDirectory(child)) continue;
                Path report = child.resolve(REPORT_FILE);
                if (!Files.isRegularFile(report)) continue;
                long modified = Files.getLastModifiedTime(report).toMillis();
                if (modified >= bestTime) {
                    bestTime = modified;
                    best = child;
                }
            }
        } catch (Exception ex) {
            log.debug("Could not scan optimizer output directory {}: {}", base, ex.toString());
        }
        return best;
    }

    static List<CombinedPass> parseReportDirectory(Path reportDir) throws Exception {
        Path xml = reportDir.resolve(REPORT_FILE);
        OptimizationResult result = new OptimizationResult();
        OptimizationReportParser parser = new OptimizationReportParser();
        parser.parse(xml, result);
        result.setOutputDirectory(reportDir.toString());

        Path forwardXml = reportDir.resolve(FORWARD_FILE);
        boolean hasForward = Files.isRegularFile(forwardXml);
        if (hasForward) {
            try {
                parser.parseForward(forwardXml, result);
            } catch (Exception ex) {
                log.warn("Forward report parse failed in {}: {}", reportDir, ex.toString());
                hasForward = false;
            }
        }
        List<CombinedPass> combined = result.buildCombinedPasses(
                hasForward && !result.getForwardPasses().isEmpty(),
                ScoreWeights.defaults());
        for (CombinedPass pass : combined) {
            if (pass != null && (pass.getReportDirectory() == null || pass.getReportDirectory().isBlank())) {
                pass.setReportDirectory(reportDir.toString());
            }
        }
        return combined;
    }

    private static CohortStats buildCohort(List<CombinedPass> passes, int topN) {
        if (passes == null || passes.isEmpty()) return emptyCohort();
        List<Double> profits = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        List<Double> dds = new ArrayList<>();
        List<Double> trades = new ArrayList<>();
        for (CombinedPass pass : passes) {
            if (pass == null || pass.getBacktestPass() == null) continue;
            profits.add(pass.getBtProfit());
            if (Double.isFinite(pass.getScore())) scores.add(pass.getScore());
            dds.add(pass.getBtDd());
            trades.add((double) pass.getBtTrades());
        }
        List<CombinedPass> top = passes.stream()
                .filter(p -> p != null && p.getBacktestPass() != null && Double.isFinite(p.getScore()))
                .sorted(Comparator.comparingDouble(CombinedPass::getScore).reversed()
                        .thenComparingInt(CombinedPass::getPassNumber))
                .limit(Math.max(1, topN))
                .collect(Collectors.toList());
        return new CohortStats(
                passes.size(),
                median(profits),
                median(scores),
                median(dds),
                median(trades),
                quartile(profits, 0.75),
                quartile(scores, 0.75),
                top);
    }

    private static CohortStats emptyCohort() {
        return new CohortStats(0, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, List.of());
    }

    private static VerdictVerdict decideVerdict(CohortStats on,
                                                CohortStats off,
                                                int minCohortSize,
                                                double scoreMargin,
                                                DataSource dataSource) {
        if (on.getCount() < minCohortSize || off.getCount() < minCohortSize
                || !Double.isFinite(on.getMedianScore()) || !Double.isFinite(off.getMedianScore())) {
            return new VerdictVerdict(Verdict.INSUFFICIENT_DATA,
                    appendFallbackNote(
                            "Zu wenig Passes je Kohorte für ein belastbares Urteil "
                                    + "(mindestens " + minCohortSize + " an und aus).",
                            dataSource));
        }
        double onScore = on.getMedianScore();
        double offScore = off.getMedianScore();
        double denom = Math.max(1.0, Math.max(Math.abs(onScore), Math.abs(offScore)));
        double rel = (onScore - offScore) / denom;
        if (rel >= scoreMargin) {
            return new VerdictVerdict(Verdict.FILTER_ON_BETTER,
                    appendFallbackNote(String.format(Locale.ROOT,
                            "Median-Score mit Filter an (%.1f) liegt klar über Filter aus (%.1f).",
                            onScore, offScore), dataSource));
        }
        if (rel <= -scoreMargin) {
            return new VerdictVerdict(Verdict.FILTER_OFF_BETTER,
                    appendFallbackNote(String.format(Locale.ROOT,
                            "Median-Score mit Filter aus (%.1f) liegt klar über Filter an (%.1f).",
                            offScore, onScore), dataSource));
        }
        return new VerdictVerdict(Verdict.UNCLEAR,
                appendFallbackNote(String.format(Locale.ROOT,
                        "Kein klarer Vorteil: Median-Score an %.1f vs aus %.1f.",
                        onScore, offScore), dataSource));
    }

    private static String appendFallbackNote(String message, DataSource dataSource) {
        if (dataSource == DataSource.DATABANK_FALLBACK) {
            return message + " (auf Basis Databank)";
        }
        return message;
    }

    private static double median(List<Double> values) {
        return quartile(values, 0.5);
    }

    private static double quartile(List<Double> values, double q) {
        if (values == null || values.isEmpty()) return Double.NaN;
        List<Double> sorted = values.stream()
                .filter(Objects::nonNull)
                .filter(Double::isFinite)
                .sorted()
                .collect(Collectors.toList());
        if (sorted.isEmpty()) return Double.NaN;
        if (sorted.size() == 1) return sorted.get(0);
        double pos = q * (sorted.size() - 1);
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);
        if (lo == hi) return sorted.get(lo);
        double w = pos - lo;
        return sorted.get(lo) * (1.0 - w) + sorted.get(hi) * w;
    }

    /** True for boolean Use_ / Inp_Use_ filter switches. */
    public static boolean looksLikeUseGate(String name) {
        if (name == null || name.isBlank()) return false;
        String n = name.trim();
        String lower = n.toLowerCase(Locale.ROOT);
        return lower.startsWith("inp_use_")
                || lower.startsWith("use_")
                || lower.contains("_use_");
    }

    private static int gatePriority(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith("inp_use_")) return 0;
        if (lower.startsWith("use_")) return 1;
        return 2;
    }

    private static String blank(String value) {
        return value != null ? value.trim() : "";
    }

    private static final class VerdictVerdict {
        final Verdict verdict;
        final String message;

        VerdictVerdict(Verdict verdict, String message) {
            this.verdict = verdict;
            this.message = message;
        }
    }

    private static final class LinkedUnique {
        private final List<String> values = new ArrayList<>();

        void add(String value) {
            if (value == null || value.isBlank()) return;
            for (String existing : values) {
                if (existing.equalsIgnoreCase(value)) return;
            }
            values.add(value.trim());
        }

        List<String> toList() {
            return values;
        }
    }
}
