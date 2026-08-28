package com.backtester.dukascopy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Converts Dukascopy tick data into M1 (1-minute) OHLCV CSV files
 * suitable for import into MetaTrader 5 Custom Symbols.
 * 
 * Output CSV format (MT5 compatible):
 * Date,Time,Open,High,Low,Close,TickVolume,Volume,Spread
 * 2024.01.02,00:00,1.10234,1.10256,1.10220,1.10245,42,0,0
 */
public class CsvConverter {

    private static final Logger log = LoggerFactory.getLogger(CsvConverter.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private int timezoneOffsetHours = 0; // UTC offset for broker timezone
    private ZoneId brokerZone = null;    // IANA broker zone (DST-aware); when set it takes precedence

    public CsvConverter() {
    }

    public CsvConverter(int timezoneOffsetHours) {
        this.timezoneOffsetHours = timezoneOffsetHours;
    }

    /**
     * DST-aware constructor. Tick timestamps are converted using the zone rules of
     * {@code brokerZone} (e.g. "Europe/Helsinki" for EET/EEST brokers) instead of a
     * fixed offset, so half-year DST shifts are handled correctly.
     */
    public CsvConverter(ZoneId brokerZone) {
        this.brokerZone = brokerZone;
    }

    /**
     * Represents a single M1 bar.
     */
    public static class M1Bar {
        public LocalDateTime dateTime;
        public double open;
        public double high;
        public double low;
        public double close;
        public int tickVolume;
        public long volume;
        public int spread;

        public M1Bar(LocalDateTime dateTime, double open, double high, double low,
                     double close, int tickVolume, long volume, int spread) {
            this.dateTime = dateTime;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.tickVolume = tickVolume;
            this.volume = volume;
            this.spread = spread;
        }
    }

    /**
     * Aggregate tick data into M1 bars.
     *
     * @param ticks list of decoded ticks
     * @return list of M1 bars
     */
    public List<M1Bar> aggregateToM1(List<Bi5Decoder.Tick> ticks) {
        return aggregateToM1(ticks, "EURUSD");
    }

    /** Aggregate tick data using the instrument's point size for spread conversion. */
    public List<M1Bar> aggregateToM1(List<Bi5Decoder.Tick> ticks, String symbol) {
        if (ticks.isEmpty()) return Collections.emptyList();

        // Sort ticks by time
        ticks.sort(Comparator.comparing(t -> t.timestamp));

        List<M1Bar> bars = new ArrayList<>();
        LocalDateTime currentMinute = null;
        double open = 0, high = Double.MIN_VALUE, low = Double.MAX_VALUE, close = 0;
        int tickCount = 0;
        int spread = 0;

        for (Bi5Decoder.Tick tick : ticks) {
            // Apply timezone offset (DST-aware when a broker zone is configured)
            LocalDateTime adjusted = toBrokerTime(tick.timestamp);
            LocalDateTime minute = adjusted.withSecond(0).withNano(0);

            // Use bid price for candle formation (industry standard)
            double price = tick.bid;

            if (currentMinute == null) {
                // First tick
                currentMinute = minute;
                open = price;
                high = price;
                low = price;
                close = price;
                tickCount = 1;
                spread = calculateSpread(tick, symbol);
            } else if (minute.equals(currentMinute)) {
                // Same minute — update bar
                high = Math.max(high, price);
                low = Math.min(low, price);
                close = price;
                tickCount++;
                spread = calculateSpread(tick, symbol);
            } else {
                // New minute — save previous bar and start new one
                bars.add(new M1Bar(currentMinute, open, high, low, close, tickCount, 0, spread));

                currentMinute = minute;
                open = price;
                high = price;
                low = price;
                close = price;
                tickCount = 1;
                spread = calculateSpread(tick, symbol);
            }
        }

        // Don't forget the last bar
        if (currentMinute != null && tickCount > 0) {
            bars.add(new M1Bar(currentMinute, open, high, low, close, tickCount, 0, spread));
        }

        log.info("Aggregated {} ticks into {} M1 bars", ticks.size(), bars.size());
        return bars;
    }

    /**
     * Write M1 bars to a CSV file in MT5-compatible format.
     *
     * @param bars       the M1 bars
     * @param outputFile path for the output CSV
     * @param digits     number of decimal places for prices (5 for most forex, 3 for JPY pairs)
     */
    public void writeCsv(List<M1Bar> bars, Path outputFile, int digits) throws IOException {
        Files.createDirectories(outputFile.getParent());

        String priceFormat = "%." + digits + "f";

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile)) {
            // Header
            writer.write("Date,Time,Open,High,Low,Close,TickVolume,Volume,Spread");
            writer.newLine();

            for (M1Bar bar : bars) {
                writer.write(String.format("%s,%s,%s,%s,%s,%s,%d,%d,%d",
                        bar.dateTime.format(DATE_FORMAT),
                        bar.dateTime.format(TIME_FORMAT),
                        String.format(Locale.US, priceFormat, bar.open),
                        String.format(Locale.US, priceFormat, bar.high),
                        String.format(Locale.US, priceFormat, bar.low),
                        String.format(Locale.US, priceFormat, bar.close),
                        bar.tickVolume,
                        bar.volume,
                        bar.spread));
                writer.newLine();
            }
        }

        log.info("CSV written: {} ({} bars)", outputFile, bars.size());
    }

    /**
     * Full pipeline: decode .bi5 files → aggregate to M1 → write CSV.
     *
     * @param dataDir  base data directory
     * @param symbol   trading symbol
     * @param from     start date
     * @param to       end date
     * @param outputCsv path for output CSV file
     * @return the output path
     */
    public Path convertFull(Path dataDir, String symbol, LocalDate from, LocalDate to,
                           Path outputCsv) throws IOException {
        Bi5Decoder decoder = new Bi5Decoder();
        List<Bi5Decoder.Tick> ticks = decoder.decodeRange(dataDir, symbol, from, to);

        if (ticks.isEmpty()) {
            log.warn("No tick data found for {} ({} to {})", symbol, from, to);
            return null;
        }

        List<M1Bar> bars = aggregateToM1(ticks, symbol);
        int digits = getDigits(symbol);
        writeCsv(bars, outputCsv, digits);

        return outputCsv;
    }

    /**
     * Get the number of decimal digits for a symbol.
     */
    public static int getDigits(String symbol) {
        int point = DukascopyDownloader.getPricePoint(symbol);
        return Integer.toString(point).length() - 1;
    }

    /**
     * Convert a UTC tick timestamp into broker time.
     * Tick timestamps are UTC: Dukascopy's datafeed files are keyed by UTC hour (see Bi5Decoder).
     * When a broker zone is configured, zone rules are used so DST transitions (EET/EEST etc.)
     * are honored; otherwise the legacy fixed-offset behavior applies.
     */
    private LocalDateTime toBrokerTime(LocalDateTime utcTimestamp) {
        if (brokerZone != null) {
            return utcTimestamp.atZone(ZoneOffset.UTC).withZoneSameInstant(brokerZone).toLocalDateTime();
        }
        return utcTimestamp.plusHours(timezoneOffsetHours);
    }

    private int calculateSpread(Bi5Decoder.Tick tick, String symbol) {
        double spreadRaw = Math.abs(tick.ask - tick.bid);
        return (int) Math.round(spreadRaw * DukascopyDownloader.getPricePoint(symbol));
    }
}
