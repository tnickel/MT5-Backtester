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
    private static final int MAX_PARALLEL_DOWNLOADS = 4;
    private static final int MAX_RETRIES = 3;

    private final HttpClient httpClient;
    private final Path dataDirectory;
    private Consumer<String> logCallback;
    private Consumer<Double> progressCallback;
    private volatile boolean cancelled = false;

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
        PRICE_POINT_MAP.put("EURJPY", 1000);
        PRICE_POINT_MAP.put("GBPJPY", 1000);
        PRICE_POINT_MAP.put("USDJPY", 1000);
        // Metals
        PRICE_POINT_MAP.put("XAUUSD", 1000);
        PRICE_POINT_MAP.put("XAGUSD", 100000);
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

    /**
     * Get the price point multiplier for a symbol.
     */
    public static int getPricePoint(String symbol) {
        return PRICE_POINT_MAP.getOrDefault(symbol.toUpperCase(), 100000);
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
                    Path result = future.get(60, TimeUnit.SECONDS);
                    if (result != null) {
                        downloadedFiles.add(result);
                    }
                    completed++;
                    updateProgress((double) completed / total);
                } catch (TimeoutException e) {
                    log.warn("Download timed out for a file");
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
                // Skip weekends (Saturday and Sunday have no data)
                if (current.getDayOfWeek() == DayOfWeek.SATURDAY ||
                    current.getDayOfWeek() == DayOfWeek.SUNDAY) {
                    break;
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
        return String.format("%s/%s/%d/%02d/%02d/%02dh_ticks.bi5",
                BASE_URL,
                symbol.toUpperCase(),
                date.getYear(),
                date.getMonthValue() - 1,  // 0-indexed month!
                date.getDayOfMonth(),
                hour);
    }

    /**
     * Get the local file path for caching.
     */
    private Path getLocalFilePath(String symbol, LocalDate date, int hour) {
        return dataDirectory
                .resolve(symbol.toUpperCase())
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
                        Files.write(task.localPath, body);
                        log.debug("Downloaded: {}", task.localPath.getFileName());
                        return task.localPath;
                    } else {
                        // Empty response = no data for this hour (e.g. holiday)
                        log.debug("No data for: {}", task.url);
                        return null;
                    }
                } else if (response.statusCode() == 404) {
                    // No data available for this hour
                    log.debug("No data (404): {}", task.url);
                    return null;
                } else {
                    log.warn("HTTP {} for {} (attempt {})", response.statusCode(), task.url, attempt);
                }

            } catch (Exception e) {
                if (cancelled) return null;
                log.warn("Download failed (attempt {}): {} - {}", attempt, task.url, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(1000L * attempt); } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Check which dates have been downloaded for a symbol.
     */
    public Map<LocalDate, Boolean> getDownloadedDates(String symbol, LocalDate from, LocalDate to) {
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
