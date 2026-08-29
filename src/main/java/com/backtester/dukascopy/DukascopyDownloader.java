package com.backtester.dukascopy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Downloads historical M1 tick data from Dukascopy's public datafeed.
 * 
 * URL Schema: https://datafeed.dukascopy.com/datafeed/{SYMBOL}/{YEAR}/{MONTH_0INDEXED}/{DAY}/{HH}h_ticks.bi5
 * 
 * Data is downloaded hourly as .bi5 (LZMA-compressed) files and cached locally.
 */
public class DukascopyDownloader {

    private static final Logger log = LoggerFactory.getLogger(DukascopyDownloader.class);
    private static final String BASE_URL = "https://datafeed.dukascopy.com/datafeed";
    private static final int MAX_PARALLEL_DOWNLOADS = 10;
    private static final int MAX_RETRIES = 3;
    private static final int DOWNLOAD_COLLECTION_TIMEOUT_SECONDS = 120;
    /** Fallback price point for symbols missing from PRICE_POINT_MAP */
    private static final int DEFAULT_PRICE_POINT = 100000;
    /** Symbols already warned about as unmapped (avoid spamming the log per tick) */
    private static final Set<String> WARNED_UNMAPPED_SYMBOLS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final HttpClient httpClient;
    private final Path dataDirectory;
    private Consumer<String> logCallback;
    private Consumer<Double> progressCallback;
    private volatile boolean cancelled = false;
    private final java.util.concurrent.atomic.AtomicInteger actualErrors = new java.util.concurrent.atomic.AtomicInteger(0);

    /** Price point multipliers for different instruments */
    private static final Map<String, Integer> PRICE_POINT_MAP = new HashMap<>();
    static {
        // 5-digit pairs
        PRICE_POINT_MAP.put("EURUSD", 100000);
        PRICE_POINT_MAP.put("GBPUSD", 100000);
        PRICE_POINT_MAP.put("USDCHF", 100000);
        PRICE_POINT_MAP.put("AUDUSD", 100000);
        PRICE_POINT_MAP.put("NZDUSD", 100000);
        PRICE_POINT_MAP.put("USDCAD", 100000);
        PRICE_POINT_MAP.put("EURGBP", 100000);
        PRICE_POINT_MAP.put("AUDCAD", 100000);
        PRICE_POINT_MAP.put("AUDNZD", 100000);
        PRICE_POINT_MAP.put("AUDCHF", 100000);
        PRICE_POINT_MAP.put("CADCHF", 100000);
        PRICE_POINT_MAP.put("EURAUD", 100000);
        PRICE_POINT_MAP.put("EURNZD", 100000);
        PRICE_POINT_MAP.put("GBPAUD", 100000);
        PRICE_POINT_MAP.put("GBPCAD", 100000);
        PRICE_POINT_MAP.put("GBPCHF", 100000);
        PRICE_POINT_MAP.put("GBPNZD", 100000);
        PRICE_POINT_MAP.put("NZDCAD", 100000);
        PRICE_POINT_MAP.put("NZDCHF", 100000);
        PRICE_POINT_MAP.put("NZDSGD", 100000);
        PRICE_POINT_MAP.put("USDSGD", 100000);
        // 3-digit JPY pairs
        PRICE_POINT_MAP.put("EURJPY", 1000);
        PRICE_POINT_MAP.put("GBPJPY", 1000);
        PRICE_POINT_MAP.put("USDJPY", 1000);
        PRICE_POINT_MAP.put("NZDJPY", 1000);
        PRICE_POINT_MAP.put("CADJPY", 1000);
        PRICE_POINT_MAP.put("AUDJPY", 1000);
        PRICE_POINT_MAP.put("CHFJPY", 1000);
        // Metals & Commodities
        PRICE_POINT_MAP.put("XAUUSD", 1000);
        PRICE_POINT_MAP.put("XAGUSD", 100000);
        PRICE_POINT_MAP.put("XTIUSD", 1000);
        PRICE_POINT_MAP.put("XBRUSD", 1000);
        // Indices (2-digit points multiplier 100)
        PRICE_POINT_MAP.put("DE40", 100);
        PRICE_POINT_MAP.put("FRANCE40", 100);
        PRICE_POINT_MAP.put("SPAIN35", 100);
        PRICE_POINT_MAP.put("US30", 100);
        PRICE_POINT_MAP.put("US500", 100);
        PRICE_POINT_MAP.put("USTEC", 100);
        PRICE_POINT_MAP.put("UK100", 100);
        PRICE_POINT_MAP.put("JP225", 100);
        PRICE_POINT_MAP.put("HK50", 100);
        PRICE_POINT_MAP.put("CHINA50", 100);
        PRICE_POINT_MAP.put("EU50", 100);
        PRICE_POINT_MAP.put("AUS200", 100);
    }

    public DukascopyDownloader(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
    }

    public void setProgressCallback(Consumer<Double> callback) {
        this.progressCallback = callback;
    }

    public void cancel() {
        this.cancelled = true;
    }

    public int getActualErrors() {
        return actualErrors.get();
    }

    /**
     * Get the price point multiplier for a symbol.
     */
    public static int getPricePoint(String symbol) {
        String normalized = symbol.toUpperCase(Locale.ROOT);
        Integer point = PRICE_POINT_MAP.get(normalized);
        if (point == null && WARNED_UNMAPPED_SYMBOLS.add(normalized)) {
            // Make mis-mapped symbols (e.g. legacy "GER40") visible instead of silently
            // producing wrong spread/digit conversions.
            log.warn("No price point mapping for symbol '{}' - assuming default {} (spread/digits may be wrong)",
                    normalized, DEFAULT_PRICE_POINT);
        }
        return point != null ? point : DEFAULT_PRICE_POINT;
    }

    /**
     * Downloads tick data for the given symbol and date range.
     * Files are stored as: data/{SYMBOL}/{YEAR}/{MONTH}/{DAY}/{HH}h_ticks.bi5
     * 
     * @param symbol  the trading symbol (e.g. "EURUSD")
     * @param from    start date (inclusive)
     * @param to      end date (inclusive)
     * @return list of downloaded .bi5 file paths
     */
    public List<Path> download(String symbol, LocalDate from, LocalDate to) throws Exception {
        cancelled = false;
        symbol = normalizeSymbol(symbol);
        List<DownloadTask> tasks = buildTaskList(symbol, from, to);

        if (tasks.isEmpty()) {
            logMsg("No data to download (all files already cached)");
            return Collections.emptyList();
        }

        logMsg(String.format("Downloading %d files for %s (%s to %s)...",
                tasks.size(), symbol, from, to));

        List<Path> downloadedFiles = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(MAX_PARALLEL_DOWNLOADS);

        try {
            int completed = 0;
            int total = tasks.size();

            // Submit all tasks
            List<Future<Path>> futures = new ArrayList<>();
            for (DownloadTask task : tasks) {
                futures.add(executor.submit(() -> downloadSingleFile(task)));
            }

            // Collect results
            for (Future<Path> future : futures) {
                if (cancelled) break;
                try {
                    Path result = future.get(DOWNLOAD_COLLECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    if (result != null) {
                        downloadedFiles.add(result);
                    }
                    completed++;
                    updateProgress((double) completed / total);
                } catch (TimeoutException e) {
                    log.warn("Download timed out for a file");
                    future.cancel(true);
                    completed++;
                } catch (Exception e) {
                    log.warn("Download failed: {}", e.getMessage());
                    completed++;
                }
            }

        } finally {
            executor.shutdownNow();
        }

        logMsg(String.format("Download complete: %d/%d files for %s",
                downloadedFiles.size(), tasks.size(), symbol));
        return downloadedFiles;
    }

    /**
     * Build list of files to download, skipping already cached files.
     */
    private List<DownloadTask> buildTaskList(String symbol, LocalDate from, LocalDate to) {
        List<DownloadTask> tasks = new ArrayList<>();
        LocalDate current = from;

        while (!current.isAfter(to)) {
            for (int hour = 0; hour < 24; hour++) {
                // Dukascopy resumes publishing on Sunday evening (as early as 21:00 UTC).
                if (current.getDayOfWeek() == DayOfWeek.SATURDAY ||
                    (current.getDayOfWeek() == DayOfWeek.SUNDAY && hour < 21)) {
                    continue;
                }

                Path localPath = getLocalFilePath(symbol, current, hour);
                if (!Files.exists(localPath)) {
                    String url = buildUrl(symbol, current, hour);
                    tasks.add(new DownloadTask(url, localPath, symbol, current, hour));
                }
            }
            current = current.plusDays(1);
        }

        return tasks;
    }

    /**
     * Build the Dukascopy download URL.
     * Note: Months are 0-indexed in Dukascopy URLs!
     */
    private String buildUrl(String symbol, LocalDate date, int hour) {
        String dukaSymbol = normalizeSymbol(symbol);
        if (dukaSymbol.equals("XTIUSD")) {
            dukaSymbol = "LIGHTCMDUSD";
        }
        return String.format("%s/%s/%d/%02d/%02d/%02dh_ticks.bi5",
                BASE_URL,
                dukaSymbol,
                date.getYear(),
                date.getMonthValue() - 1,  // 0-indexed month!
                date.getDayOfMonth(),
                hour);
    }

    /**
     * Get the local file path for caching.
     */
    private Path getLocalFilePath(String symbol, LocalDate date, int hour) {
        String safeSymbol = normalizeSymbol(symbol);
        return dataDirectory
                .resolve(safeSymbol)
                .resolve(String.valueOf(date.getYear()))
                .resolve(String.format("%02d", date.getMonthValue()))
                .resolve(String.format("%02d", date.getDayOfMonth()))
                .resolve(String.format("%02dh_ticks.bi5", hour));
    }

    /**
     * Download a single .bi5 file with retry logic.
     */
    private Path downloadSingleFile(DownloadTask task) {
        if (cancelled) return null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Files.createDirectories(task.localPath.getParent());

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(task.url))
                        .timeout(Duration.ofSeconds(30))
                        .header("User-Agent", "MT5-Backtester/1.0")
                        .GET()
                        .build();

                HttpResponse<byte[]> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofByteArray());

                if (response.statusCode() == 200) {
                    byte[] body = response.body();
                    if (body.length > 0) {
                        writeCachedFile(task.localPath, body);
                        log.debug("Downloaded: {}", task.localPath.getFileName());
                        return task.localPath;
                    } else {
                        // Empty response = no data for this hour (e.g. holiday). Write a marker file so we don't redownload it.
                        log.debug("No data for: {}", task.url);
                        writeCachedFile(task.localPath, new byte[0]);
                        return task.localPath;
                    }
                } else if (response.statusCode() == 404) {
                    // No data available for this hour. Write a marker file so we don't redownload it.
                    log.debug("No data (404): {}", task.url);
                    writeCachedFile(task.localPath, new byte[0]);
                    return task.localPath;
                } else {
                    log.warn("HTTP {} for {} (attempt {})", response.statusCode(), task.url, attempt);
                    if (attempt < MAX_RETRIES) {
                        if (!backoffBeforeRetry(attempt)) return null;
                    } else {
                        actualErrors.incrementAndGet();
                    }
                }

            } catch (Exception e) {
                if (cancelled) return null;
                log.warn("Download failed (attempt {}): {} - {}", attempt, task.url, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    if (!backoffBeforeRetry(attempt)) return null;
                } else {
                    actualErrors.incrementAndGet();
                }
            }
        }
        return null;
    }

    /**
     * Writes a download body into the cache. The bytes first go to a temp file in
     * the target directory and are moved to the final name only on success, so a
     * cancelled/interrupted download (e.g. the collection timeout cancelling the
     * future) can never leave a truncated .bi5 behind — the cache would treat it
     * as valid data on the next run and skip re-downloading the hour.
     */
    private static void writeCachedFile(Path target, byte[] body) throws IOException {
        Path temp = null;
        try {
            temp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".part");
            Files.write(temp, body);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // Filesystems without atomic-move support: plain move is still far
                // better than writing the final name directly.
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            temp = null;
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException cleanupError) {
                    log.debug("Could not delete partial download {}: {}", temp, cleanupError.getMessage());
                }
            }
        }
    }

    /**
     * Check which dates have been downloaded for a symbol.
     */
    public Map<LocalDate, Boolean> getDownloadedDates(String symbol, LocalDate from, LocalDate to) {
        symbol = normalizeSymbol(symbol);
        Map<LocalDate, Boolean> result = new LinkedHashMap<>();
        LocalDate current = from;
        while (!current.isAfter(to)) {
            boolean hasData = false;
            for (int hour = 0; hour < 24; hour++) {
                if (Files.exists(getLocalFilePath(symbol, current, hour))) {
                    hasData = true;
                    break;
                }
            }
            result.put(current, hasData);
            current = current.plusDays(1);
        }
        return result;
    }

    /**
     * Get all .bi5 files for a symbol and date range (already downloaded).
     */
    public List<Path> getLocalFiles(String symbol, LocalDate from, LocalDate to) {
        symbol = normalizeSymbol(symbol);
        List<Path> files = new ArrayList<>();
        LocalDate current = from;
        while (!current.isAfter(to)) {
            for (int hour = 0; hour < 24; hour++) {
                Path p = getLocalFilePath(symbol, current, hour);
                if (Files.exists(p)) {
                    files.add(p);
                }
            }
            current = current.plusDays(1);
        }
        return files;
    }

    private boolean backoffBeforeRetry(int attempt) {
        try {
            Thread.sleep(1000L * attempt);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String normalizeSymbol(String symbol) {
        if (symbol == null || !symbol.matches("[A-Za-z0-9]{1,12}")) {
            throw new IllegalArgumentException("Invalid Dukascopy symbol: " + symbol);
        }
        return symbol.toUpperCase(Locale.ROOT);
    }

    private void logMsg(String msg) {
        log.info(msg);
        if (logCallback != null) logCallback.accept(msg);
    }

    private void updateProgress(double progress) {
        if (progressCallback != null) progressCallback.accept(progress);
    }

    /** Internal download task descriptor */
    private record DownloadTask(String url, Path localPath, String symbol, LocalDate date, int hour) {}
}
