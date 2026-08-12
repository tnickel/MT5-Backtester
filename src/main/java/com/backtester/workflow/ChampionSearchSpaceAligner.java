package com.backtester.workflow;

import com.backtester.config.EaParameter;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Guarantees that the current value of an optimized parameter is itself one of the
 * passes MT5 will walk.
 *
 * <p>A staged workflow fixes everything found so far ({@code Opt=N}) and re-opens only
 * the new stage targets ({@code Opt=Y}). For those targets MT5 ignores the value field
 * and iterates {@code Start / Step / Stop}. If the value in force — the champion — is
 * outside that band or between two grid points, the stage cannot reproduce it and its
 * best pass may be worse than what the chain already had. Aligning the band to the
 * champion turns "may regress" into "cannot regress in-sample".
 *
 * <p>Two corrections are applied, both preserving the configured step width:
 * <ul>
 *   <li>range extension when the champion lies below start or above stop,</li>
 *   <li>grid phase shift (start moves down by less than one step) when the champion
 *       lies inside the band but between two grid points.</li>
 * </ul>
 *
 * <p>Timeframe inputs are handled on ENUM_TIMEFRAMES positions, because their numeric
 * codes are not linear. {@code PERIOD_CURRENT} is never pulled back into a band —
 * MT5 drops the step for that enum edge — so such a champion is reported as unreachable
 * instead of silently corrupting the band.
 */
public final class ChampionSearchSpaceAligner {

    /**
     * How far outside its band a champion may sit before widening is refused.
     * A few steps means the band was cut slightly too tight and can be repaired. Far
     * beyond that, band and value contradict each other; silently stretching the search
     * space would replace the configured experiment instead of protecting it, so the
     * conflict is reported and left to the operator.
     */
    static final int MAX_EXTENSION_STEPS = 10;

    private ChampionSearchSpaceAligner() {
    }

    public enum Outcome {
        /** Band was widened because the champion was outside start/stop. */
        RANGE_EXTENDED,
        /** Start moved by less than one step so the champion sits on the grid. */
        GRID_SHIFTED,
        /** Champion lies so far outside the band that widening was refused. */
        SKIPPED_TOO_FAR,
        /** Champion cannot be expressed in this band (non-numeric, PERIOD_CURRENT, …). */
        SKIPPED_UNREACHABLE
    }

    public static final class Adjustment {
        private final String parameterName;
        private final String championValue;
        private final String oldStart;
        private final String oldStep;
        private final String oldEnd;
        private final String newStart;
        private final String newStep;
        private final String newEnd;
        private final Outcome outcome;
        private final String note;

        Adjustment(String parameterName, String championValue,
                   String oldStart, String oldStep, String oldEnd,
                   String newStart, String newStep, String newEnd,
                   Outcome outcome, String note) {
            this.parameterName = parameterName != null ? parameterName : "";
            this.championValue = championValue != null ? championValue : "";
            this.oldStart = oldStart != null ? oldStart : "";
            this.oldStep = oldStep != null ? oldStep : "";
            this.oldEnd = oldEnd != null ? oldEnd : "";
            this.newStart = newStart != null ? newStart : "";
            this.newStep = newStep != null ? newStep : "";
            this.newEnd = newEnd != null ? newEnd : "";
            this.outcome = outcome;
            this.note = note != null ? note : "";
        }

        public String getParameterName() { return parameterName; }
        public String getChampionValue() { return championValue; }
        public String getOldStart() { return oldStart; }
        public String getOldStep() { return oldStep; }
        public String getOldEnd() { return oldEnd; }
        public String getNewStart() { return newStart; }
        public String getNewStep() { return newStep; }
        public String getNewEnd() { return newEnd; }
        public Outcome getOutcome() { return outcome; }
        public String getNote() { return note; }

        public boolean isApplied() {
            return outcome == Outcome.RANGE_EXTENDED || outcome == Outcome.GRID_SHIFTED;
        }

        public String getOldBand() { return oldStart + " / " + oldStep + " / " + oldEnd; }
        public String getNewBand() { return newStart + " / " + newStep + " / " + newEnd; }

        /** One-line log/dialog text. */
        public String describe() {
            if (!isApplied()) {
                return parameterName + " = " + championValue + " (Suchraum " + getOldBand()
                        + "): " + note;
            }
            return parameterName + " = " + championValue + ": Suchraum " + getOldBand()
                    + " → " + getNewBand()
                    + (outcome == Outcome.RANGE_EXTENDED ? " (erweitert)" : " (Raster verschoben)");
        }
    }

    /** Aligns every {@code Opt=Y} row in place and reports what had to change. */
    public static List<Adjustment> align(List<EaParameter> parameters) {
        List<Adjustment> adjustments = new ArrayList<>();
        if (parameters == null) return adjustments;
        for (EaParameter parameter : parameters) {
            align(parameter).ifPresent(adjustments::add);
        }
        return adjustments;
    }

    /** Empty when the champion is already a grid point (the normal case). */
    public static Optional<Adjustment> align(EaParameter parameter) {
        if (parameter == null || parameter.isSectionHeader() || parameter.isStringType()
                || !parameter.isOptimizeEnabled()) {
            return Optional.empty();
        }
        String champion = normalized(parameter.getValue());
        String start = normalized(parameter.getOptimizeStart());
        String step = normalized(parameter.getOptimizeStep());
        String end = normalized(parameter.getOptimizeEnd());
        if (champion.isEmpty() || start.isEmpty() || step.isEmpty() || end.isEmpty()) {
            return Optional.empty();
        }
        // Boolean bands walk both states, so the champion is covered by construction.
        if (isBooleanToken(start) || isBooleanToken(end)) {
            return Optional.empty();
        }
        if (EaParameter.isTimeframeParameterName(parameter.getName())) {
            return alignTimeframe(parameter, champion, start, step, end);
        }
        return alignNumeric(parameter, champion, start, step, end);
    }

    private static Optional<Adjustment> alignNumeric(EaParameter parameter, String champion,
                                                     String start, String step, String end) {
        BigDecimal value;
        BigDecimal oldStart;
        BigDecimal stepWidth;
        BigDecimal oldEnd;
        try {
            value = new BigDecimal(champion);
            oldStart = new BigDecimal(start);
            stepWidth = new BigDecimal(step);
            oldEnd = new BigDecimal(end);
        } catch (NumberFormatException ex) {
            return Optional.of(unreachable(parameter, champion, start, step, end,
                    "kein numerischer Wert — Suchraum kann nicht angepasst werden."));
        }
        if (stepWidth.signum() <= 0 || oldEnd.compareTo(oldStart) < 0) {
            return Optional.of(unreachable(parameter, champion, start, step, end,
                    "Suchraum ist unbrauchbar (Schritt ≤ 0 oder Ende < Start)."));
        }

        BigDecimal newStart = alignedStart(value, oldStart, stepWidth);
        BigDecimal newEnd = oldEnd.max(value);
        if (newStart.compareTo(oldStart) == 0 && newEnd.compareTo(oldEnd) == 0) {
            return Optional.empty();
        }
        long extension = stepsBetween(value, oldStart, stepWidth)
                + stepsBetween(oldEnd, value, stepWidth);
        if (extension > MAX_EXTENSION_STEPS) {
            return Optional.of(tooFar(parameter, champion, start, step, end, extension));
        }

        int scale = Math.min(8, Math.max(Math.max(value.scale(), oldStart.scale()),
                Math.max(stepWidth.scale(), oldEnd.scale())));
        String startText = plain(newStart, scale);
        String endText = plain(newEnd, scale);
        boolean outsideBand = value.compareTo(oldStart) < 0 || value.compareTo(oldEnd) > 0;
        parameter.setOptimizeStart(startText);
        parameter.setOptimizeEnd(endText);
        return Optional.of(new Adjustment(name(parameter), champion, start, step, end,
                startText, step, endText,
                outsideBand ? Outcome.RANGE_EXTENDED : Outcome.GRID_SHIFTED, ""));
    }

    private static Optional<Adjustment> alignTimeframe(EaParameter parameter, String champion,
                                                       String start, String step, String end) {
        int championIndex = EaParameter.timeframeEnumIndex(champion);
        int startIndex = EaParameter.timeframeEnumIndex(start);
        int endIndex = EaParameter.timeframeEnumIndex(end);
        if (championIndex < 0 || startIndex < 0 || endIndex < 0 || endIndex < startIndex) {
            return Optional.of(unreachable(parameter, champion, start, step, end,
                    "unbekannter ENUM_TIMEFRAMES-Wert — Suchraum kann nicht angepasst werden."));
        }
        if (championIndex == 0) {
            return Optional.of(unreachable(parameter, champion, start, step, end,
                    "PERIOD_CURRENT ist als Optimierungs-Start unbrauchbar (MT5 lässt dort den "
                            + "Schritt fallen) — die Stufe kann diesen Wert nicht reproduzieren."));
        }
        int stepWidth;
        try {
            stepWidth = Integer.parseInt(step);
        } catch (NumberFormatException ex) {
            stepWidth = 1;
        }
        if (stepWidth <= 0) stepWidth = 1;

        int newStartIndex = startIndex;
        if (championIndex < startIndex) {
            newStartIndex = championIndex;
        } else if ((championIndex - startIndex) % stepWidth != 0) {
            int steps = (championIndex - startIndex) / stepWidth + 1;
            newStartIndex = championIndex - steps * stepWidth;
            if (newStartIndex < 1) newStartIndex = championIndex % stepWidth;
            if (newStartIndex < 1) newStartIndex = 1;
        }
        int newEndIndex = Math.max(endIndex, championIndex);
        if (newStartIndex == startIndex && newEndIndex == endIndex) {
            return Optional.empty();
        }
        long extension = Math.max(0, startIndex - championIndex)
                + Math.max(0, championIndex - endIndex);
        if (extension > (long) MAX_EXTENSION_STEPS * stepWidth) {
            return Optional.of(tooFar(parameter, champion, start, step, end, extension));
        }

        String startCode = EaParameter.timeframeCodeAtIndex(newStartIndex);
        String endCode = EaParameter.timeframeCodeAtIndex(newEndIndex);
        if (startCode.isEmpty() || endCode.isEmpty()) {
            return Optional.of(unreachable(parameter, champion, start, step, end,
                    "ENUM_TIMEFRAMES-Position außerhalb der bekannten Liste."));
        }
        parameter.setOptimizeStart(startCode);
        parameter.setOptimizeEnd(endCode);
        return Optional.of(new Adjustment(name(parameter), champion, start, step, end,
                startCode, step, endCode,
                championIndex < startIndex || championIndex > endIndex
                        ? Outcome.RANGE_EXTENDED : Outcome.GRID_SHIFTED, ""));
    }

    /**
     * Largest grid start {@code ≤ min(start, value)} whose grid hits the champion.
     * The phase never drops more than one full step below the configured start.
     */
    private static BigDecimal alignedStart(BigDecimal value, BigDecimal start, BigDecimal step) {
        BigDecimal quotient = value.subtract(start).divide(step, MathContext.DECIMAL64);
        BigDecimal steps = quotient
                .setScale(9, RoundingMode.HALF_UP)
                .setScale(0, RoundingMode.CEILING);
        if (steps.signum() < 0) steps = BigDecimal.ZERO;
        return value.subtract(step.multiply(steps));
    }

    /** Whole steps needed to get from {@code to} up to {@code from}; 0 when already above. */
    private static long stepsBetween(BigDecimal from, BigDecimal to, BigDecimal step) {
        if (from.compareTo(to) >= 0) return 0L;
        BigDecimal span = to.subtract(from).divide(step, MathContext.DECIMAL64);
        return span.setScale(0, RoundingMode.CEILING).longValue();
    }

    private static Adjustment unreachable(EaParameter parameter, String champion,
                                          String start, String step, String end, String note) {
        return new Adjustment(name(parameter), champion, start, step, end,
                start, step, end, Outcome.SKIPPED_UNREACHABLE, note);
    }

    private static Adjustment tooFar(EaParameter parameter, String champion,
                                     String start, String step, String end, long extension) {
        return new Adjustment(name(parameter), champion, start, step, end,
                start, step, end, Outcome.SKIPPED_TOO_FAR,
                "liegt " + extension + " Schritte außerhalb (Grenze " + MAX_EXTENSION_STEPS
                        + ") — Suchraum bleibt unverändert. Entweder passt der Suchraum nicht "
                        + "zum aktuellen Wert oder umgekehrt.");
    }

    private static String plain(BigDecimal value, int scale) {
        return value.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    private static String name(EaParameter parameter) {
        return parameter.getName() != null ? parameter.getName().trim() : "";
    }

    private static String normalized(String raw) {
        return EaParameter.normalizeMql5Value(raw != null ? raw : "").trim();
    }

    private static boolean isBooleanToken(String raw) {
        String v = raw.toLowerCase(Locale.ROOT);
        return v.equals("true") || v.equals("false") || v.equals("yes") || v.equals("no");
    }
}
