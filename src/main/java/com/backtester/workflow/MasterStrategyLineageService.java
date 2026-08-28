package com.backtester.workflow;

import com.backtester.config.AppConfig;
import com.backtester.config.EaParameter;
import com.backtester.config.EaParameterManager;
import com.backtester.engine.BacktestConfig;
import com.backtester.engine.BacktestRunner;
import com.backtester.report.BacktestResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Measures the master strategy after every hand-pick.
 *
 * <p>Unified scores from different stages cannot be compared: each one is computed
 * against its own optimization population and its own trade thresholds. To answer
 * "is the chain making the strategy better", the adopted parameter set is re-run
 * under fixed reference conditions (same symbol, period, date range and modelling
 * every time) and the results are appended to {@link CustomProject#getMasterStrategyLineage()}.
 */
public final class MasterStrategyLineageService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(MasterStrategyLineageService.class);

    /** Fixed reference window — identical for every entry, otherwise nothing is comparable. */
    public static final String REFERENCE_FROM = ToTheMoon132GuidedWorkflowFactory.SEARCH_FROM;
    public static final String REFERENCE_TO = ToTheMoon132GuidedWorkflowFactory.DEVELOPMENT_TO;
    public static final int REFERENCE_MODEL = BacktestConfig.MODEL_OHLC_M1;
    public static final int REFERENCE_DEPOSIT = 10000;
    public static final String REFERENCE_CURRENCY = "USD";
    public static final String REFERENCE_LEVERAGE = "1:100";

    /** A relative change smaller than this counts as noise, not as progress. */
    static final double NEUTRAL_TOLERANCE = 0.01;
    /** Below this the baseline has no usable relative scale. */
    private static final double ZERO_BASELINE = 1e-9;
    /** Enough shape for the chart without bloating the project JSON. */
    static final int MAX_EQUITY_POINTS = 1500;
    /** Bumped whenever the signature encoding changes. */
    private static final String SIGNATURE_VERSION = "v1";
    /**
     * Process-local reset generation. A reference measurement may outlive the UI action
     * that cleared the lineage; such an old worker must not recreate the erased history
     * when it eventually finishes. Weak keys avoid retaining closed projects.
     */
    private static final Map<CustomProject, Long> LINEAGE_GENERATIONS = new WeakHashMap<>();

    private MasterStrategyLineageService() {
    }

    /**
     * Profit per unit of drawdown. Profit alone rewards taking more risk, so the
     * verdict is based on this instead.
     */
    public static double returnToDrawdown(double profit, double maxDrawdownAbsolute) {
        if (!Double.isFinite(maxDrawdownAbsolute) || maxDrawdownAbsolute <= 0) return Double.NaN;
        if (!Double.isFinite(profit)) return Double.NaN;
        double ratio = profit / maxDrawdownAbsolute;
        return Double.isFinite(ratio) ? ratio : Double.NaN;
    }

    /**
     * Best measured entry so far within the same reference conditions. Entries from
     * another symbol, timeframe or period are a different experiment and must not
     * serve as a baseline.
     */
    public static Optional<MasterStrategyEntry> bestEntry(List<MasterStrategyEntry> entries,
                                                          String contextKey) {
        if (entries == null) return Optional.empty();
        MasterStrategyEntry best = null;
        for (MasterStrategyEntry entry : entries) {
            if (entry == null || !entry.isBacktestSucceeded()) continue;
            if (!Double.isFinite(comparisonMetric(entry))) continue;
            if (contextKey != null && !contextKey.isBlank()
                    && !contextKey.equals(entry.contextKey())) {
                continue;
            }
            if (best == null || isBetter(entry, best)) best = entry;
        }
        return Optional.ofNullable(best);
    }

    /** Best entry under the reference conditions of the newest measurement (display only). */
    public static Optional<MasterStrategyEntry> bestEntry(List<MasterStrategyEntry> entries) {
        if (entries == null || entries.isEmpty()) return Optional.empty();
        return bestEntry(entries, entries.get(entries.size() - 1).contextKey());
    }

    /**
     * Appends the entry, numbers it and rates it exclusively against the measurement that
     * confirmed the persisted master. Numbering, rating and insert happen under the
     * project lock: two concurrent writers would otherwise hand out the same sequence
     * or drop an entry.
     */
    public static MasterStrategyEntry append(CustomProject project, MasterStrategyEntry entry) {
        if (project == null || entry == null) return entry;
        return project.withMasterStrategyLineage(lineage -> appendLocked(project, lineage, entry));
    }

    private static MasterStrategyEntry appendLocked(CustomProject project,
                                                     List<MasterStrategyEntry> lineage,
                                                     MasterStrategyEntry entry) {
        entry.setSequence(lineage.size() + 1);
        if (entry.getCreatedAt() <= 0) entry.setCreatedAt(System.currentTimeMillis());
        rate(entry, confirmedMasterEntry(project, lineage, entry.contextKey()).orElse(null));
        lineage.add(entry);
        return entry;
    }

    /** Captures the reset generation under the same lock used by clear and append. */
    static long captureLineageGeneration(CustomProject project) {
        if (project == null) return -1L;
        return project.withMasterStrategyLineage(ignored -> generationLocked(project));
    }

    /**
     * Appends only if no clear happened since the measurement started. Package visibility
     * keeps the generation race independently testable without running MetaTrader.
     */
    static MasterStrategyEntry appendIfGeneration(CustomProject project,
                                                   MasterStrategyEntry entry,
                                                   long expectedGeneration) {
        if (project == null || entry == null) return null;
        return project.withMasterStrategyLineage(lineage -> {
            if (generationLocked(project) != expectedGeneration) return null;
            return appendLocked(project, lineage, entry);
        });
    }

    private static long generationLocked(CustomProject project) {
        synchronized (LINEAGE_GENERATIONS) {
            return LINEAGE_GENERATIONS.getOrDefault(project, 0L);
        }
    }

    private static void advanceGenerationLocked(CustomProject project) {
        synchronized (LINEAGE_GENERATIONS) {
            long generation = LINEAGE_GENERATIONS.getOrDefault(project, 0L);
            LINEAGE_GENERATIONS.put(project, generation + 1L);
        }
    }

    /**
     * The measurement that backs the persisted master, if it can be proven. New projects
     * store its sequence explicitly. Legacy projects are migrated conservatively: an exact
     * parameter/context signature wins; otherwise the measured ratio may identify exactly
     * one entry under the persisted context. Merely being the best historical entry is not
     * evidence that an entry was ever committed.
     */
    public static Optional<MasterStrategyEntry> confirmedMasterEntry(CustomProject project) {
        if (project == null) return Optional.empty();
        return project.withMasterStrategyLineage(lineage -> confirmedMasterEntry(
                project, lineage, project.getProvenMasterContextKey()));
    }

    private static Optional<MasterStrategyEntry> confirmedMasterEntry(
            CustomProject project,
            List<MasterStrategyEntry> lineage,
            String requiredContext) {
        if (project == null || lineage == null || lineage.isEmpty()) return Optional.empty();

        int confirmedSequence = project.getConfirmedMasterSequence();
        if (confirmedSequence > 0) {
            MasterStrategyEntry anchored = findSequence(lineage, confirmedSequence);
            if (isUsableConfirmedEntry(anchored, requiredContext)) {
                return Optional.of(anchored);
            }
            log.warn("Bestätigte Master-Sequenz #{} ist im Verlauf nicht als vergleichbare Messung vorhanden.",
                    confirmedSequence);
            return Optional.empty();
        }

        if (!project.hasProvenMaster()) return Optional.empty();
        String persistedContext = project.getProvenMasterContextKey();
        if (persistedContext.isBlank()
                || (requiredContext != null && !requiredContext.isBlank()
                    && !persistedContext.equals(requiredContext))) {
            return Optional.empty();
        }

        MasterStrategyEntry signatureMatch = legacySignatureMatch(project, lineage, persistedContext);
        if (signatureMatch != null) {
            project.setConfirmedMasterSequence(signatureMatch.getSequence());
            return Optional.of(signatureMatch);
        }

        double persistedRatio = project.getMasterSelectionRatio();
        if (!Double.isFinite(persistedRatio)) return Optional.empty();
        MasterStrategyEntry ratioMatch = null;
        for (MasterStrategyEntry candidate : lineage) {
            if (!isUsableConfirmedEntry(candidate, persistedContext)
                    || !sameMetric(candidate.getReturnToDrawdown(), persistedRatio)) {
                continue;
            }
            if (ratioMatch != null) {
                log.warn("Legacy-Master kann nicht eindeutig migriert werden: Mehrere Messpunkte "
                        + "passen zu Kontext und Profit/DD {}.", persistedRatio);
                return Optional.empty();
            }
            ratioMatch = candidate;
        }
        if (ratioMatch != null) {
            project.setConfirmedMasterSequence(ratioMatch.getSequence());
            return Optional.of(ratioMatch);
        }
        log.warn("Legacy-Master kann keinem gemessenen Verlaufspunkt sicher zugeordnet werden.");
        return Optional.empty();
    }

    private static MasterStrategyEntry legacySignatureMatch(CustomProject project,
                                                            List<MasterStrategyEntry> lineage,
                                                            String persistedContext) {
        if (!persistedContext.equals(currentContextKey(project))) return null;
        List<EaParameter> parameters = project.getProvenMasterParameters();
        if (parameters.isEmpty()) return null;
        String expected = measurementSignature(buildReferenceConfig(project, ""), parameters);
        MasterStrategyEntry newest = null;
        for (MasterStrategyEntry candidate : lineage) {
            if (isUsableConfirmedEntry(candidate, persistedContext)
                    && expected.equals(candidate.getMeasurementSignature())
                    && (newest == null || candidate.getSequence() > newest.getSequence())) {
                newest = candidate;
            }
        }
        return newest;
    }

    private static MasterStrategyEntry findSequence(List<MasterStrategyEntry> lineage, int sequence) {
        for (MasterStrategyEntry entry : lineage) {
            if (entry != null && entry.getSequence() == sequence) return entry;
        }
        return null;
    }

    private static boolean isUsableConfirmedEntry(MasterStrategyEntry entry, String requiredContext) {
        if (entry == null || !entry.isBacktestSucceeded()
                || !Double.isFinite(comparisonMetric(entry))) {
            return false;
        }
        return requiredContext == null || requiredContext.isBlank()
                || requiredContext.equals(entry.contextKey());
    }

    private static boolean sameMetric(double first, double second) {
        if (!Double.isFinite(first) || !Double.isFinite(second)) return false;
        double scale = Math.max(1.0, Math.max(Math.abs(first), Math.abs(second)));
        return Math.abs(first - second) <= 1e-9 * scale;
    }

    /**
     * Forgets everything known about the master strategy: the measured lineage, the
     * confirmed basis and the profit/drawdown floor derived from it. Returns how many
     * measurements were removed.
     *
     * <p>All three go at once. Keeping the confirmed basis after erasing its lineage
     * would leave a master that no longer has a measurement behind it, and the next
     * candidate would count as the first one and overwrite it without any comparison.
     */
    public static int clear(CustomProject project) {
        if (project == null) return 0;
        return project.withMasterStrategyLineage(lineage -> {
            int size = lineage.size();
            advanceGenerationLocked(project);
            lineage.clear();
            // Keep the lineage and its confirmation anchor as one atomic state transition.
            // Service appends cannot observe an empty history with stale master metadata.
            project.clearProvenMaster();
            return size;
        });
    }

    /**
     * Recovers the preset of the measurement that is provably tied to the confirmed master.
     * Legacy projects without an explicit sequence use the conservative signature or unique
     * ratio/context migration in {@link #confirmedMasterEntry(CustomProject)}. The historically
     * best unconfirmed measurement is never treated as proof of adoption.
     *
     * <p>Only names and values are read from it, which is all the carry uses; the search
     * bands always come from the stage itself.
     *
     * @return the recovered basis, or empty when nothing can be recovered
     */
    public static List<EaParameter> recoverProvenMasterFromLineage(CustomProject project) {
        if (project == null) return List.of();
        MasterStrategyEntry confirmed = confirmedMasterEntry(project).orElse(null);
        if (confirmed == null || confirmed.getSetfileContent().isBlank()) return List.of();

        Path temporary = null;
        try {
            temporary = Files.createTempFile("master-basis-", ".set");
            Files.writeString(temporary, confirmed.getSetfileContent(), StandardCharsets.UTF_8);
            return new EaParameterManager().readSetFile(temporary);
        } catch (IOException | RuntimeException ex) {
            log.warn("Bewiesene Master-Basis konnte nicht aus dem Verlauf rekonstruiert werden", ex);
            return List.of();
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // A leftover temp file is harmless; the recovery itself already succeeded.
                }
            }
        }
    }

    /** The reference conditions this project measures under right now. */
    public static String currentContextKey(CustomProject project) {
        return contextKey(buildReferenceConfig(project, ""));
    }

    /**
     * Drops a confirmed master that was measured under different reference conditions.
     * A change of expert, symbol or period makes the old measurement meaningless: its
     * parameters are no proof of anything here, and its floor would block every
     * candidate before a single one has been measured under the new conditions.
     *
     * @return true when a master was dropped
     */
    public static boolean rebaselineOnContextChange(CustomProject project) {
        if (project == null || !project.hasProvenMaster()) return false;
        String current = currentContextKey(project);
        if (current.equals(project.getProvenMasterContextKey())) return false;
        project.clearProvenMaster();
        return true;
    }

    /** Fills verdict and deltas of {@code entry} relative to {@code reference}. */
    static void rate(MasterStrategyEntry entry, MasterStrategyEntry reference) {
        if (entry == null) return;
        if (reference == null || !entry.isBacktestSucceeded()) {
            entry.setVerdict(MasterStrategyEntry.Verdict.UNBEKANNT);
            entry.setComparedToSequence(reference != null ? reference.getSequence() : -1);
            entry.setDeltaProfit(0);
            entry.setDeltaReturnToDrawdown(0);
            entry.setDeltaMaxDrawdownPercent(0);
            return;
        }

        entry.setComparedToSequence(reference.getSequence());
        entry.setDeltaProfit(entry.getProfit() - reference.getProfit());
        entry.setDeltaMaxDrawdownPercent(entry.getMaxDrawdownPercent() - reference.getMaxDrawdownPercent());
        boolean bothRatios = Double.isFinite(entry.getReturnToDrawdown())
                && Double.isFinite(reference.getReturnToDrawdown());
        entry.setDeltaReturnToDrawdown(bothRatios
                ? entry.getReturnToDrawdown() - reference.getReturnToDrawdown() : 0);

        double candidate = comparisonMetric(entry);
        double baseline = comparisonMetric(reference);
        if (!Double.isFinite(candidate) || !Double.isFinite(baseline)) {
            entry.setVerdict(MasterStrategyEntry.Verdict.UNBEKANNT);
            return;
        }
        entry.setVerdict(judge(candidate, baseline));
    }

    /**
     * Changes smaller than {@value #NEUTRAL_TOLERANCE} (1 %) count as noise. A baseline of
     * effectively zero has no meaningful relative scale, so there any real difference
     * decides — otherwise a swing from +1e-12 to −1e-12 would be reported as unchanged.
     */
    static MasterStrategyEntry.Verdict judge(double candidate, double baseline) {
        if (!Double.isFinite(candidate) || !Double.isFinite(baseline)) {
            return MasterStrategyEntry.Verdict.UNBEKANNT;
        }
        double difference = candidate - baseline;
        if (Math.abs(baseline) < ZERO_BASELINE) {
            if (Math.abs(difference) < ZERO_BASELINE) return MasterStrategyEntry.Verdict.NEUTRAL;
            return difference > 0
                    ? MasterStrategyEntry.Verdict.BESSER : MasterStrategyEntry.Verdict.SCHLECHTER;
        }
        // Include the exact 1 % boundary. The tiny slack absorbs binary floating-point
        // representation error without turning a materially smaller change into progress.
        double threshold = NEUTRAL_TOLERANCE * Math.abs(baseline) * (1 - 1e-12);
        if (difference >= threshold) return MasterStrategyEntry.Verdict.BESSER;
        if (difference <= -threshold) return MasterStrategyEntry.Verdict.SCHLECHTER;
        return MasterStrategyEntry.Verdict.NEUTRAL;
    }

    static boolean isBetter(MasterStrategyEntry candidate, MasterStrategyEntry reference) {
        double a = comparisonMetric(candidate);
        double b = comparisonMetric(reference);
        if (Double.isFinite(a) && Double.isFinite(b)) return a > b;
        return Double.isFinite(a);
    }

    /**
     * Return/drawdown when both entries expose it, profit otherwise. Mixing the two
     * would compare different units, so entries without a drawdown figure fall back
     * to profit only when the reference does too.
     */
    private static double comparisonMetric(MasterStrategyEntry entry) {
        if (entry == null || !entry.isBacktestSucceeded()) return Double.NaN;
        double ratio = entry.getReturnToDrawdown();
        return Double.isFinite(ratio) ? ratio : Double.NaN;
    }

    /**
     * Fingerprint of a measurement: the parameter values plus the full reference
     * context. Identical fingerprint means the identical experiment, so it does not
     * need another MT5 run. The context has to be part of it — the same parameters on
     * another symbol or period are a different measurement.
     *
     * <p>Fields are length-prefixed so no combination of names and values can produce
     * the same encoded text as a different combination.
     */
    public static String measurementSignature(BacktestConfig context, List<EaParameter> parameters) {
        List<String> encoded = new ArrayList<>();
        if (parameters != null) {
            for (EaParameter parameter : parameters) {
                if (parameter == null || parameter.getName() == null) continue;
                encoded.add(field(parameter.getName().trim()) + field(safeValue(parameter.getValue())));
            }
        }
        Collections.sort(encoded);
        String payload = SIGNATURE_VERSION + "|" + field(contextKey(context)) + String.join("", encoded);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            return SIGNATURE_VERSION + "-" + payload.hashCode();
        }
    }

    /** Identifies the reference conditions; only entries sharing it are comparable. */
    public static String contextKey(BacktestConfig config) {
        if (config == null) return "";
        return String.join("|",
                config.getExpert(),
                config.getSymbol(),
                config.getPeriod(),
                config.getFromDate() != null ? config.getFromDate().toString() : "",
                config.getToDate() != null ? config.getToDate().toString() : "",
                String.valueOf(config.getModel()),
                String.valueOf(config.getDeposit()),
                config.getCurrency(),
                config.getLeverage());
    }

    /**
     * The preset file name is stable per project, so a failed write would leave the
     * previous run's file in place and MT5 would measure that one instead — recorded under
     * the signature of the basis we meant to test. {@code writeSetFile} reports failures
     * only to the log, so the file itself has to be the proof. The caller deletes it
     * first; anything not written back is therefore missing or empty here.
     *
     * <p>Existence alone is not enough: a write that dies halfway through leaves a file
     * that looks plausible but is missing parameters, and MT5 would silently fall back to
     * the expert's defaults for those. Checking the names alone is not enough either — a
     * file left over from another basis carries every name and only differs in the values,
     * which is exactly the case this guard exists for. The comparison uses the same
     * renderer that produced the file, so it cannot fail over formatting.
     */
    public static void verifyPresetWritten(Path presetFile, List<EaParameter> expected)
            throws IOException {
        if (presetFile == null || !Files.isRegularFile(presetFile)) {
            throw new IOException("Referenz-Preset " + presetFile + " wurde nicht angelegt.");
        }
        String content = StrategyBacktestArchiveStore.readSetfileContent(presetFile);
        // The decoded byte order mark is not whitespace, so a file holding nothing else
        // would count as content — and it would also hide a parameter on the first line.
        if (content.startsWith("\uFEFF")) content = content.substring(1);
        if (content.isBlank()) {
            throw new IOException("Referenz-Preset " + presetFile + " ist leer.");
        }
        if (expected == null) return;
        for (EaParameter parameter : expected) {
            if (parameter == null || parameter.isSectionHeader()) continue;
            String name = parameter.getName() != null ? parameter.getName().trim() : "";
            if (name.isEmpty()) continue;
            String written = writtenValue(content, name);
            if (written == null) {
                throw new IOException("Referenz-Preset " + presetFile + " ist unvollständig: '"
                        + name + "' fehlt.");
            }
            String expectedValue = EaParameter.normalizeMql5Value(parameter.getValue());
            if (!GuidedOptimizationService.valuesEquivalent(expectedValue, written)) {
                throw new IOException("Referenz-Preset " + presetFile + " enthält für '" + name
                        + "' den Wert '" + written + "' statt '" + expectedValue + "'.");
            }
        }
    }

    /**
     * The value a preset assigns to {@code name}, or null when it assigns none. Matching is
     * line-anchored so one parameter cannot be satisfied by another one ending in its name.
     * MT5 appends the optimisation band to the same line behind {@code ||}; that part is
     * not the value and is cut off.
     */
    private static String writtenValue(String content, String name) {
        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith(name + "=")) continue;
            String rest = trimmed.substring(name.length() + 1);
            int band = rest.indexOf("||");
            return band >= 0 ? rest.substring(0, band) : rest;
        }
        return null;
    }

    private static String field(String value) {
        String text = value != null ? value : "";
        return text.length() + ":" + text + ";";
    }

    /**
     * The newest entry when it is a successful measurement of exactly this experiment.
     * A failed run must not count — otherwise the error would never be retried.
     *
     * <p>Callers need the entry, not just the fact: a basis that was already measured
     * and rejected has to be rejected again after a restart, instead of slipping through
     * as "nothing left to measure".
     */
    public static Optional<MasterStrategyEntry> findLatestMeasurement(CustomProject project,
                                                                      String signature) {
        if (project == null || signature == null || signature.isBlank()) return Optional.empty();
        List<MasterStrategyEntry> lineage = project.getMasterStrategyLineage();
        if (lineage.isEmpty()) return Optional.empty();
        MasterStrategyEntry newest = lineage.get(lineage.size() - 1);
        return newest.isBacktestSucceeded() && signature.equals(newest.getMeasurementSignature())
                ? Optional.of(newest) : Optional.empty();
    }

    public static boolean alreadyMeasured(CustomProject project, String signature) {
        return findLatestMeasurement(project, signature).isPresent();
    }

    private static String safeValue(String value) {
        return value != null ? value.trim() : "";
    }

    /**
     * Keeps the curve's shape but caps the point count. A few thousand trades per entry
     * across a dozen entries would otherwise dominate the project JSON.
     */
    static List<double[]> capEquityPoints(List<double[]> points) {
        if (points == null || points.size() <= MAX_EQUITY_POINTS) {
            return points != null ? points : List.of();
        }
        List<double[]> reduced = new ArrayList<>(MAX_EQUITY_POINTS);
        int last = points.size() - 1;
        double stride = (double) last / (MAX_EQUITY_POINTS - 1);
        for (int i = 0; i < MAX_EQUITY_POINTS - 1; i++) {
            reduced.add(points.get((int) Math.round(i * stride)));
        }
        reduced.add(points.get(last));
        return reduced;
    }

    /**
     * What an adoption actually did, in the form the lineage window renders: the
     * parameters the producing stage optimized (with the values they replace), the
     * remaining preset carry-over, and the grid the next stage is going to walk.
     */
    public static final class AdoptionSummary {
        private final String producerStageName;
        private final List<MasterStrategyEntry.ParameterChange> optimizedParameters;
        private final List<MasterStrategyEntry.ParameterChange> additionalChanges;
        private final List<MasterStrategyEntry.OptimizationTarget> nextStageTargets;

        public AdoptionSummary(String producerStageName,
                               List<MasterStrategyEntry.ParameterChange> optimizedParameters,
                               List<MasterStrategyEntry.ParameterChange> additionalChanges,
                               List<MasterStrategyEntry.OptimizationTarget> nextStageTargets) {
            this.producerStageName = producerStageName != null ? producerStageName : "";
            this.optimizedParameters = optimizedParameters != null
                    ? List.copyOf(optimizedParameters) : List.of();
            this.additionalChanges = additionalChanges != null
                    ? List.copyOf(additionalChanges) : List.of();
            this.nextStageTargets = nextStageTargets != null
                    ? List.copyOf(nextStageTargets) : List.of();
        }

        public String getProducerStageName() { return producerStageName; }
        public List<MasterStrategyEntry.ParameterChange> getOptimizedParameters() {
            return optimizedParameters;
        }
        public List<MasterStrategyEntry.ParameterChange> getAdditionalChanges() {
            return additionalChanges;
        }
        public List<MasterStrategyEntry.OptimizationTarget> getNextStageTargets() {
            return nextStageTargets;
        }

        /** The "Name: alt → neu" lines kept for the compact console/banner form. */
        public List<String> describeChangedParameters() {
            List<String> lines = new ArrayList<>();
            for (MasterStrategyEntry.ParameterChange change : optimizedParameters) {
                if (change != null && change.isChanged()) {
                    lines.add(change.getName() + ": " + change.getOldValue()
                            + " → " + change.getNewValue());
                }
            }
            return lines;
        }
    }

    /**
     * Builds the summary from a dry-run preview plus the parameter set the next stage
     * will actually run with. That effective basis — not the preview — decides the new
     * values: search bands are aligned to the champion during the adoption itself and a
     * filter gate can be forced afterwards, so the preview can name a value the stage
     * never sees.
     */
    public static AdoptionSummary summarize(GuidedOptimizationService.AdoptionPreview preview,
                                            WorkflowTask nextOptimizer,
                                            List<EaParameter> adoptedParameters) {
        return summarize(preview, nextOptimizer, adoptedParameters, null);
    }

    /**
     * Builds an incremental audit against the confirmed basis that was active before
     * adoption. This prevents every later stage from repeating differences against its
     * factory template as if they had just happened again.
     */
    public static AdoptionSummary summarize(GuidedOptimizationService.AdoptionPreview preview,
                                            WorkflowTask nextOptimizer,
                                            List<EaParameter> adoptedParameters,
                                            List<EaParameter> confirmedBasisBeforeAdoption) {
        if (preview == null) {
            return new AdoptionSummary("", List.of(), List.of(), List.of());
        }
        Map<String, EaParameter> effective = indexByName(adoptedParameters);
        Map<String, EaParameter> confirmed = indexByName(confirmedBasisBeforeAdoption);
        List<MasterStrategyEntry.ParameterChange> optimized = toParameterChanges(
                preview.getPassParameters(), effective, confirmed);
        List<MasterStrategyEntry.ParameterChange> additional = confirmed.isEmpty()
                ? toParameterChanges(preview.getOtherBasisValueChanges(), effective, confirmed)
                : actualBasisChanges(confirmed, effective, optimized);
        return new AdoptionSummary(
                preview.getProducerStageName(),
                optimized,
                additional,
                describeNextStageTargets(nextOptimizer, adoptedParameters));
    }

    private static List<MasterStrategyEntry.ParameterChange> toParameterChanges(
            List<GuidedOptimizationService.ParameterValueChange> changes,
            Map<String, EaParameter> effective,
            Map<String, EaParameter> confirmed) {
        List<MasterStrategyEntry.ParameterChange> result = new ArrayList<>();
        if (changes == null) return result;
        for (GuidedOptimizationService.ParameterValueChange change : changes) {
            if (change == null) continue;
            EaParameter oldParameter = confirmed.get(normalizeName(change.getName()));
            result.add(new MasterStrategyEntry.ParameterChange(
                    change.getName(), oldParameter != null ? oldParameter.getValue() : change.getOldValue(),
                    effectiveValue(effective, change.getName(), change.getNewValue())));
        }
        return result;
    }

    private static List<MasterStrategyEntry.ParameterChange> actualBasisChanges(
            Map<String, EaParameter> confirmed,
            Map<String, EaParameter> effective,
            List<MasterStrategyEntry.ParameterChange> optimized) {
        Set<String> optimizedNames = new LinkedHashSet<>();
        for (MasterStrategyEntry.ParameterChange change : optimized) {
            if (change != null) optimizedNames.add(normalizeName(change.getName()));
        }
        List<MasterStrategyEntry.ParameterChange> result = new ArrayList<>();
        for (Map.Entry<String, EaParameter> entry : effective.entrySet()) {
            String name = entry.getKey();
            EaParameter actual = entry.getValue();
            EaParameter old = confirmed.get(name);
            if (old == null || actual == null || optimizedNames.contains(name)
                    || actual.isSectionHeader() || actual.isStringType()) {
                continue;
            }
            String oldValue = old.getValue() != null ? old.getValue() : "";
            String newValue = actual.getValue() != null ? actual.getValue() : "";
            if (!GuidedOptimizationService.valuesEquivalent(oldValue, newValue)) {
                result.add(new MasterStrategyEntry.ParameterChange(
                        actual.getName(), oldValue, newValue));
            }
        }
        return result;
    }

    private static String effectiveValue(Map<String, EaParameter> effective,
                                         String name,
                                         String previewValue) {
        if (effective.isEmpty() || name == null || name.isBlank()) return previewValue;
        EaParameter actual = effective.get(name.trim().toLowerCase(Locale.ROOT));
        return actual != null && actual.getValue() != null ? actual.getValue() : previewValue;
    }

    private static Map<String, EaParameter> indexByName(List<EaParameter> parameters) {
        Map<String, EaParameter> byName = new LinkedHashMap<>();
        if (parameters == null) return byName;
        for (EaParameter parameter : parameters) {
            if (parameter == null || parameter.getName() == null || parameter.getName().isBlank()) continue;
            byName.putIfAbsent(parameter.getName().trim().toLowerCase(Locale.ROOT), parameter);
        }
        return byName;
    }

    private static String normalizeName(String name) {
        return name != null ? name.trim().toLowerCase(Locale.ROOT) : "";
    }

    /** Target parameters of the consuming stage with the grid it will walk. */
    public static List<MasterStrategyEntry.OptimizationTarget> describeNextStageTargets(
            WorkflowTask nextOptimizer, List<EaParameter> adoptedParameters) {
        List<MasterStrategyEntry.OptimizationTarget> targets = new ArrayList<>();
        if (nextOptimizer == null) return targets;
        List<EaParameter> parameters = adoptedParameters != null && !adoptedParameters.isEmpty()
                ? adoptedParameters : nextOptimizer.getOptimizerParameterSnapshot();
        Map<String, EaParameter> byName = indexByName(parameters);
        for (String name : nextOptimizer.getOptimizerTargetParameters()) {
            if (name == null || name.isBlank()) continue;
            EaParameter parameter = byName.get(name.trim().toLowerCase(Locale.ROOT));
            targets.add(parameter == null
                    ? new MasterStrategyEntry.OptimizationTarget(name.trim(), "", "", "", "")
                    : new MasterStrategyEntry.OptimizationTarget(
                            name.trim(), parameter.getValue(), parameter.getOptimizeStart(),
                            parameter.getOptimizeStep(), parameter.getOptimizeEnd()));
        }
        return targets;
    }

    public static BacktestConfig buildReferenceConfig(CustomProject project, String presetFileName) {
        return buildReferenceConfig(project, findReferenceTask(project), presetFileName);
    }

    /**
     * Builds the fixed comparison run from the workflow task that owns the current
     * search window. Persisted projects can therefore keep their historic 3Y context,
     * while new 1Y templates execute exactly the dates displayed in their task.
     */
    public static BacktestConfig buildReferenceConfig(CustomProject project,
                                                      WorkflowTask referenceTask,
                                                      String presetFileName) {
        AppConfig config = AppConfig.getInstance();
        String expert = project != null && project.getExpert() != null && !project.getExpert().isBlank()
                ? project.getExpert() : config.get("app.expert", "ToTheMoon_KI_v132");
        String symbol = referenceTask != null && !referenceTask.getRetestSymbol().isBlank()
                ? referenceTask.getRetestSymbol()
                : project != null && project.getSymbol() != null && !project.getSymbol().isBlank()
                        ? project.getSymbol() : config.get("app.symbol", "EURUSD");
        String period = referenceTask != null && !referenceTask.getRetestPeriod().isBlank()
                ? referenceTask.getRetestPeriod()
                : project != null && project.getPeriod() != null && !project.getPeriod().isBlank()
                        ? project.getPeriod() : config.get("app.period", "H1");
        LocalDate from = parseReferenceDate(
                referenceTask != null ? referenceTask.getStartDate() : null, REFERENCE_FROM);
        LocalDate to = parseReferenceDate(
                referenceTask != null ? referenceTask.getEndDate() : null, REFERENCE_TO);
        if (!from.isBefore(to)) {
            from = LocalDate.parse(REFERENCE_FROM);
            to = LocalDate.parse(REFERENCE_TO);
        }

        BacktestConfig btConfig = new BacktestConfig();
        btConfig.setExpert(expert);
        btConfig.setExpertParameters(presetFileName);
        btConfig.setSymbol(symbol);
        btConfig.setPeriod(period);
        btConfig.setFromDate(from);
        btConfig.setToDate(to);
        btConfig.setModel(referenceTask != null ? referenceTask.getMt5Model() : REFERENCE_MODEL);
        btConfig.setDeposit(REFERENCE_DEPOSIT);
        btConfig.setCurrency(REFERENCE_CURRENCY);
        btConfig.setLeverage(REFERENCE_LEVERAGE);
        btConfig.setShutdownTerminal(true);
        btConfig.setAutoKillMt5(true);
        return btConfig;
    }

    private static WorkflowTask findReferenceTask(CustomProject project) {
        if (project == null || project.getTasks() == null) return null;
        for (WorkflowTask task : project.getTasks()) {
            if (task != null && task.isEnabled() && task.getType() == WorkflowTask.TaskType.OPTIMIZER) {
                return task;
            }
        }
        return null;
    }

    private static LocalDate parseReferenceDate(String value, String fallback) {
        try {
            return LocalDate.parse(value != null && !value.isBlank() ? value.trim() : fallback);
        } catch (RuntimeException ignored) {
            return LocalDate.parse(fallback);
        }
    }

    /** Maps a finished reference backtest onto a lineage entry (not yet appended). */
    public static MasterStrategyEntry toEntry(BacktestConfig config, BacktestResult result) {
        MasterStrategyEntry entry = new MasterStrategyEntry();
        entry.setCreatedAt(System.currentTimeMillis());
        if (config != null) {
            entry.setExpert(config.getExpert());
            entry.setSymbol(config.getSymbol());
            entry.setPeriod(config.getPeriod());
            entry.setFromDate(config.getFromDate() != null ? config.getFromDate().toString() : REFERENCE_FROM);
            entry.setToDate(config.getToDate() != null ? config.getToDate().toString() : REFERENCE_TO);
            entry.setTickModel(config.getModelName());
            entry.setModel(config.getModel());
            entry.setDeposit(config.getDeposit());
            entry.setCurrency(config.getCurrency());
            entry.setLeverage(config.getLeverage());
            entry.setContextKey(contextKey(config));
        }
        if (result == null) {
            entry.setBacktestSucceeded(false);
            entry.setFailureMessage("MetaTrader lieferte kein Ergebnis.");
            return entry;
        }
        entry.setBacktestSucceeded(result.isSuccess());
        if (!result.isSuccess()) {
            entry.setFailureMessage(result.getMessage() != null && !result.getMessage().isBlank()
                    ? result.getMessage() : "Referenz-Backtest fehlgeschlagen.");
        }
        entry.setProfit(result.getTotalProfit());
        entry.setProfitFactor(result.getProfitFactor());
        entry.setMaxDrawdownPercent(result.getMaxDrawdownPercent());
        entry.setMaxDrawdownAbsolute(result.getMaxDrawdownAbsolute());
        entry.setTotalTrades(result.getTotalTrades());
        entry.setRecoveryFactor(result.getRecoveryFactor());
        entry.setSharpeRatio(result.getSharpeRatio());
        entry.setExpectedPayoff(result.getExpectedPayoff());
        entry.setFinalBalance(result.getFinalBalance());
        entry.setReturnToDrawdown(returnToDrawdown(result.getTotalProfit(), result.getMaxDrawdownAbsolute()));
        entry.setEquityCurve(capEquityPoints(result.getEquityHistory()));
        String outputDir = result.getOutputDirectory();
        entry.setReportDirectory(outputDir != null ? outputDir : "");
        if (outputDir != null && !outputDir.isBlank()) {
            Path png = Path.of(outputDir).resolve("BacktestReport.png");
            if (Files.isRegularFile(png)) entry.setEquityImagePath(png.toString());
        }
        if (result.getTickModel() != null && !result.getTickModel().isBlank()) {
            entry.setTickModel(result.getTickModel());
        }
        return entry;
    }

    /**
     * Runs the reference backtest for an adopted parameter set and appends the result
     * to the project lineage. Blocking — call this from a background thread.
     *
     * @param parameters the adopted master parameter set (values as they will be used)
     * @param summary    what the adoption changed, may be {@code null}
     * @param logSink    optional console sink, may be {@code null}
     */
    public static MasterStrategyEntry runAndAppend(CustomProject project,
                                                   WorkflowTask consumer,
                                                   String sourceDatabank,
                                                   int passNumber,
                                                   List<EaParameter> parameters,
                                                   AdoptionSummary summary,
                                                   Consumer<String> logSink,
                                                   BacktestRunner runner) {
        long lineageGeneration = captureLineageGeneration(project);
        BacktestConfig btConfig = buildReferenceConfig(project, consumer, "");
        String expert = btConfig.getExpert();
        // Project-scoped file name: two projects must not overwrite each other's preset.
        String presetFileName = "MasterStrategy_Ref_"
                + EaParameterManager.extractEaBaseName(expert) + "_"
                + projectSlug(project) + ".set";
        btConfig.setExpertParameters(presetFileName);
        btConfig.setReportFileName("MasterStrategyReference");

        MasterStrategyEntry entry;
        try {
            if (parameters == null || parameters.isEmpty()) {
                throw new IllegalStateException("Keine Parameter zum Messen vorhanden.");
            }
            Path presetsDir = AppConfig.getInstance().getTesterProfilesDir(expert);
            Files.createDirectories(presetsDir);
            Path presetFile = presetsDir.resolve(presetFileName);
            Files.deleteIfExists(presetFile);
            new EaParameterManager().writeSetFile(presetFile, parameters, expert);
            verifyPresetWritten(presetFile, parameters);

            emit(logSink, "Referenz-Backtest der Master-Strategie startet ("
                    + btConfig.getSymbol() + " " + btConfig.getPeriod() + ", "
                    + btConfig.getFromDate() + " bis " + btConfig.getToDate()
                    + ", " + btConfig.getModelName() + ").");

            BacktestRunner activeRunner = runner != null ? runner : new BacktestRunner();
            if (logSink != null) activeRunner.setLogCallback(msg -> logSink.accept(msg));
            BacktestResult result = activeRunner.runBacktest(btConfig);

            entry = toEntry(btConfig, result);
            entry.setSetfileContent(StrategyBacktestArchiveStore.readSetfileContent(presetFile));
        } catch (IOException ex) {
            log.error("Referenz-Backtest konnte nicht vorbereitet werden", ex);
            entry = toEntry(btConfig, null);
            entry.setFailureMessage("Preset konnte nicht geschrieben werden: " + ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("Referenz-Backtest fehlgeschlagen", ex);
            entry = toEntry(btConfig, null);
            entry.setFailureMessage("Referenz-Backtest fehlgeschlagen: " + ex.getMessage());
        }

        entry.setStageTaskName(consumer != null ? consumer.getName() : "");
        entry.setSourceDatabank(sourceDatabank != null ? sourceDatabank : "");
        entry.setSourcePassNumber(passNumber);
        if (summary != null) {
            entry.setAdoptedChanges(summary.describeChangedParameters());
            entry.setOptimizedStageName(summary.getProducerStageName());
            entry.setOptimizedParameters(summary.getOptimizedParameters());
            entry.setAdditionalChanges(summary.getAdditionalChanges());
            entry.setNextStageTargets(summary.getNextStageTargets());
        }
        entry.setMeasurementSignature(measurementSignature(btConfig, parameters));

        if (appendIfGeneration(project, entry, lineageGeneration) == null) {
            log.info("Verwerfe abgeschlossene Referenzmessung, weil der Master-Verlauf zwischenzeitlich gelöscht wurde.");
            emit(logSink, "Referenz-Backtest beendet, aber verworfen: Der Master-Verlauf wurde während der Messung gelöscht.");
            return null;
        }
        emit(logSink, describeOutcome(entry));
        return entry;
    }

    /** One-line summary used for the console and the pause dialog. */
    public static String describeOutcome(MasterStrategyEntry entry) {
        if (entry == null) return "";
        if (!entry.isBacktestSucceeded()) {
            return "Master-Strategie #" + entry.getSequence() + ": Referenz-Backtest ohne Ergebnis ("
                    + entry.getFailureMessage() + ").";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Master-Strategie #").append(entry.getSequence()).append(" (")
                .append(entry.getStageTaskName()).append("): Profit ")
                .append(String.format(java.util.Locale.US, "%.2f", entry.getProfit()))
                .append(", PF ").append(String.format(java.util.Locale.US, "%.2f", entry.getProfitFactor()))
                .append(", DD ").append(String.format(java.util.Locale.US, "%.2f%%", entry.getMaxDrawdownPercent()))
                .append(", Trades ").append(entry.getTotalTrades())
                .append(", Profit/DD ")
                .append(Double.isFinite(entry.getReturnToDrawdown())
                        ? String.format(java.util.Locale.US, "%.2f", entry.getReturnToDrawdown()) : "—");
        if (entry.getComparedToSequence() > 0) {
            sb.append(" — ").append(entry.getVerdict()).append(" als #")
                    .append(entry.getComparedToSequence())
                    .append(" (Profit ").append(String.format(java.util.Locale.US, "%+.2f", entry.getDeltaProfit()))
                    .append(", Profit/DD ")
                    .append(String.format(java.util.Locale.US, "%+.2f", entry.getDeltaReturnToDrawdown()))
                    .append(")");
        } else {
            sb.append(" — erster Referenzpunkt");
        }
        return sb.toString();
    }

    private static String projectSlug(CustomProject project) {
        String id = project != null && project.getId() != null ? project.getId() : "default";
        String cleaned = id.replaceAll("[^A-Za-z0-9]", "");
        return cleaned.length() > 12 ? cleaned.substring(0, 12) : cleaned;
    }

    private static void emit(Consumer<String> logSink, String message) {
        if (logSink != null && message != null && !message.isBlank()) logSink.accept(message);
    }
}
