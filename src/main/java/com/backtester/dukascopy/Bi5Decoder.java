package com.backtester.dukascopy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tukaani.xz.LZMAInputStream;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/**
 * Decodes Dukascopy .bi5 files (LZMA-compressed binary tick data).
 * 
 * Each tick is a 20-byte block:
 *   - TIME (4 bytes, int32): milliseconds since hour start
 *   - ASKP (4 bytes, int32): ask price (scaled integer)
 *   - BIDP (4 bytes, int32): bid price (scaled integer)
 *   - ASKV (4 bytes, float32): ask volume
 *   - BIDV (4 bytes, float32): bid volume
 */
public class Bi5Decoder {

    private static final Logger log = LoggerFactory.getLogger(Bi5Decoder.class);
    private static final int TICK_SIZE = 20; // bytes per tick

    /**
     * Represents a single decoded tick.
     */
    public static class Tick {
        public LocalDateTime timestamp;
        public double ask;
        public double bid;
        public float askVolume;
        public float bidVolume;

        public Tick(LocalDateTime timestamp, double ask, double bid, float askVolume, float bidVolume) {
            this.timestamp = timestamp;
            this.ask = ask;
            this.bid = bid;
            this.askVolume = askVolume;
            this.bidVolume = bidVolume;
        }

        @Override
        public String toString() {
            return String.format("%s, Ask=%.5f, Bid=%.5f, AV=%.2f, BV=%.2f",
                    timestamp, ask, bid, askVolume, bidVolume);
        }
    }

    /**
     * Decode a .bi5 file into a list of ticks.
     *
     * @param bi5File    path to the .bi5 file
     * @param symbol     trading symbol (for price point calculation)
     * @param date       the date this file represents
     * @param hour       the hour this file represents (0-23)
     * @return list of decoded ticks
     */
    public List<Tick> decode(Path bi5File, String symbol, LocalDate date, int hour) throws IOException {
        List<Tick> ticks = new ArrayList<>();
        int pricePoint = DukascopyDownloader.getPricePoint(symbol);

        byte[] compressedData = Files.readAllBytes(bi5File);
        if (compressedData.length == 0) {
            return ticks; // Empty file = no data
        }

        LocalDateTime hourStart = LocalDateTime.of(date, LocalTime.of(hour, 0));

        try (LZMAInputStream lzmaIn = new LZMAInputStream(new ByteArrayInputStream(compressedData))) {
            byte[] buffer = new byte[TICK_SIZE];

            while (true) {
                int bytesRead = readFully(lzmaIn, buffer);
                if (bytesRead < TICK_SIZE) break;

                ByteBuffer bb = ByteBuffer.wrap(buffer).order(ByteOrder.BIG_ENDIAN);

                int timeOffsetMs = bb.getInt();     // ms since hour start
                int askRaw = bb.getInt();            // scaled ask price
                int bidRaw = bb.getInt();            // scaled bid price
                float askVol = bb.getFloat();        // ask volume
                float bidVol = bb.getFloat();        // bid volume

                // Convert time offset to actual timestamp
                LocalDateTime tickTime = hourStart.plusNanos(timeOffsetMs * 1_000_000L);

                // Convert scaled integers to actual prices
                double ask = (double) askRaw / pricePoint;
                double bid = (double) bidRaw / pricePoint;

                ticks.add(new Tick(tickTime, ask, bid, askVol, bidVol));
            }
        } catch (Exception e) {
            log.warn("Error decoding {}: {}", bi5File.getFileName(), e.getMessage());
        }

        log.debug("Decoded {} ticks from {}", ticks.size(), bi5File.getFileName());
        return ticks;
    }

    /**
     * Decode all .bi5 files in a directory tree for a given symbol and date range.
     */
    public List<Tick> decodeRange(Path dataDir, String symbol, LocalDate from, LocalDate to) throws IOException {
        List<Tick> allTicks = new ArrayList<>();
        DukascopyDownloader downloader = new DukascopyDownloader(dataDir);
        List<Path> files = downloader.getLocalFiles(symbol, from, to);

        for (Path file : files) {
            // Extract date and hour from path: .../SYMBOL/YEAR/MONTH/DAY/HHh_ticks.bi5
            try {
                Path relative = dataDir.resolve(symbol.toUpperCase()).relativize(file);
                // relative = YEAR/MONTH/DAY/HHh_ticks.bi5
                int year = Integer.parseInt(relative.getName(0).toString());
                int month = Integer.parseInt(relative.getName(1).toString());
                int day = Integer.parseInt(relative.getName(2).toString());
                String hourFile = relative.getName(3).toString();
                int hour = Integer.parseInt(hourFile.substring(0, 2));

                LocalDate date = LocalDate.of(year, month, day);
                List<Tick> ticks = decode(file, symbol, date, hour);
                allTicks.addAll(ticks);
            } catch (Exception e) {
                log.warn("Failed to decode file: {} - {}", file, e.getMessage());
            }
        }

        // Sort by timestamp
        allTicks.sort(Comparator.comparing(t -> t.timestamp));
        log.info("Total decoded ticks for {} ({} to {}): {}", symbol, from, to, allTicks.size());
        return allTicks;
    }

    /**
     * Read exactly 'buffer.length' bytes or return fewer if EOF.
     */
    private int readFully(InputStream in, byte[] buffer) throws IOException {
        int total = 0;
        while (total < buffer.length) {
            int read = in.read(buffer, total, buffer.length - total);
            if (read < 0) break;
            total += read;
        }
        return total;
    }
}
