package com.backtester.engine;

import com.backtester.config.MetaTraderPlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Tails MT5 log files (Terminal and Tester) continuously.
 * MT5 logs are written in UTF-16LE format.
 */
public class Mt5LogTailer implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(Mt5LogTailer.class);
    static final int MAX_LOG_READ_BYTES = 256 * 1024;
    private static final String AGENT_PASS_MARKER = "OnTester result";

    private final Path mt5Dir;
    private final MetaTraderPlatform platform;
    private final Consumer<String> logCallback;
    private java.util.function.BiConsumer<Integer, Integer> progressCallback;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean criticalFailure = new AtomicBoolean(false);

    public boolean hasCriticalFailure() {
        return criticalFailure.get();
    }

    private volatile long lastActivityTime = System.currentTimeMillis();

    public long getLastActivityTime() {
        return lastActivityTime;
    }

    public void updateLastActivityTime() {
        this.lastActivityTime = System.currentTimeMillis();
    }

    private long terminalLogLastPos = 0;
    private long testerLogLastPos = 0;

    private Path terminalLogPath;
    private Path testerLogPath;

    private final java.util.Map<Path, Long> agentLogPositions = new java.util.HashMap<>();
    private final java.util.Map<Path, String> agentLogMarkerSuffixes = new java.util.HashMap<>();
    private int agentTotalPasses = 0;

    public Mt5LogTailer(Path mt5Dir, MetaTraderPlatform platform, Consumer<String> logCallback) {
        this.mt5Dir = mt5Dir;
        this.platform = platform;
        this.logCallback = logCallback;
        
        // Find newest log files initially, or fall back to today's date if none exist yet
        this.terminalLogPath = findNewestLogFile(mt5Dir.resolve("logs"));
        this.testerLogPath = findNewestLogFile(mt5Dir.resolve(platform.getTesterLogsFolderName()));
        
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        if (this.terminalLogPath == null) {
            this.terminalLogPath = mt5Dir.resolve("logs").resolve(dateStr + ".log");
        }
        if (this.testerLogPath == null) {
            this.testerLogPath = mt5Dir.resolve(platform.getTesterLogsFolderName()).resolve(dateStr + ".log");
        }
    }

    public void setProgressCallback(java.util.function.BiConsumer<Integer, Integer> progressCallback) {
        this.progressCallback = progressCallback;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            // Clear agent log tracking state on start
            agentLogPositions.clear();
            agentLogMarkerSuffixes.clear();
            agentTotalPasses = 0;
            initializeExistingAgentLogPositions();

            // Initialize positions to current file lengths so we only tail NEW logs
            terminalLogLastPos = getFileLengthSafely(terminalLogPath);
            testerLogLastPos = getFileLengthSafely(testerLogPath);

            Thread thread = new Thread(this, "Mt5-Log-Tailer");
            thread.setDaemon(true);
            thread.start();
        }
    }

    public void stop() {
        running.set(false);
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                // Dynamically refresh log paths if a newer log file is created in the directories
                Path newTerminalPath = findNewestLogFile(mt5Dir.resolve("logs"));
                if (newTerminalPath != null && !newTerminalPath.equals(terminalLogPath)) {
                    terminalLogPath = newTerminalPath;
                    terminalLogLastPos = 0; // Start reading new file from beginning
                }
                
                Path newTesterPath = findNewestLogFile(mt5Dir.resolve(platform.getTesterLogsFolderName()));
                if (newTesterPath != null && !newTesterPath.equals(testerLogPath)) {
                    testerLogPath = newTesterPath;
                    testerLogLastPos = 0; // Start reading new file from beginning
                }

                terminalLogLastPos = tailFile(terminalLogPath, terminalLogLastPos, "[Terminal] ");
                testerLogLastPos = tailFile(testerLogPath, testerLogLastPos, "[Tester] ");
                
                // Poll agent logs for real-time progress updates
                pollAgentLogs();
                
                Thread.sleep(1000); // Poll every 1 second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error in log tailer", e);
            }
        }
        
        // Final read before exiting to catch any last logs
        tailFile(terminalLogPath, terminalLogLastPos, "[Terminal] ");
        tailFile(testerLogPath, testerLogLastPos, "[Tester] ");
    }

    private long getFileLengthSafely(Path filePath) {
        if (filePath == null) return 0;
        try {
            if (Files.exists(filePath)) {
                return Files.size(filePath);
            }
        } catch (IOException e) {
            log.warn("Could not get file size for {}", filePath, e);
        }
        return 0;
    }

    private long tailFile(Path filePath, long lastPos, String prefix) {
        if (filePath == null) return lastPos;
        File file = filePath.toFile();
        if (!file.exists()) {
            return lastPos;
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long length = raf.length();
            if (length < lastPos) {
                // File was truncated or recreated
                lastPos = 0;
            }

            if (length > lastPos) {
                raf.seek(lastPos);
                int bytesToRead = boundedReadSize(length - lastPos,
                        platform.getLogCharset() == StandardCharsets.UTF_16LE);
                
                if (bytesToRead > 0) {
                    byte[] buffer = new byte[bytesToRead];
                    raf.readFully(buffer);
                    lastPos += bytesToRead;
                    updateLastActivityTime();

                    String newContent = new String(buffer, platform.getLogCharset());
                    processNewLines(newContent, prefix);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read log file: {}", filePath, e);
        }
        return lastPos;
    }

    private Path findNewestLogFile(Path dir) {
        if (!Files.exists(dir)) {
            return null;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                .filter(p -> p.getFileName().toString().endsWith(".log") && Files.isRegularFile(p))
                .max((p1, p2) -> {
                    try {
                        return Long.compare(Files.getLastModifiedTime(p1).toMillis(), Files.getLastModifiedTime(p2).toMillis());
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    static int boundedReadSize(long remainingBytes, boolean utf16) {
        int size = (int) Math.min(Math.max(0L, remainingBytes), MAX_LOG_READ_BYTES);
        return utf16 && (size & 1) != 0 ? size - 1 : size;
    }

    /**
     * Existing agent logs may contain all optimizations from the current day.
     * Start at EOF for files that predate this tailer so their old passes are not
     * counted as progress of the new run. Agent logs created afterwards still start at 0.
     */
    private void initializeExistingAgentLogPositions() {
        Path testerDir = mt5Dir.resolve("Tester");
        if (!Files.exists(testerDir)) return;
        try (Stream<Path> stream = Files.list(testerDir)) {
            stream.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("Agent-"))
                    .map(path -> findNewestLogFile(path.resolve("logs")))
                    .filter(java.util.Objects::nonNull)
                    .forEach(path -> {
                        long length = path.toFile().length();
                        agentLogPositions.put(path, length - (length & 1L));
                    });
        } catch (IOException e) {
            log.debug("Could not initialize existing agent-log positions", e);
        }
    }

    private void pollAgentLogs() {
        if (!platform.supportsMultiThreadAgents()) {
            return;
        }
        Path testerDir = mt5Dir.resolve("Tester");
        if (!Files.exists(testerDir)) {
            return;
        }

        boolean foundNewPasses = false;
        try (Stream<Path> stream = Files.list(testerDir)) {
            Iterator<Path> iterator = stream.iterator();
            while (iterator.hasNext()) {
                Path agentDir = iterator.next();
                if (Files.isDirectory(agentDir) && agentDir.getFileName().toString().startsWith("Agent-")) {
                    Path logsDir = agentDir.resolve("logs");
                    Path newestLog = findNewestLogFile(logsDir);
                    if (newestLog != null && Files.exists(newestLog)) {
                        long lastPos = agentLogPositions.getOrDefault(newestLog, 0L);
                        long length = newestLog.toFile().length();
                        
                        if (length < lastPos) {
                            lastPos = 0;
                            agentLogMarkerSuffixes.remove(newestLog);
                        }
                        
                        if (length > lastPos) {
                            try (RandomAccessFile raf = new RandomAccessFile(newestLog.toFile(), "r")) {
                                raf.seek(lastPos);
                                int bytesToRead = boundedReadSize(length - lastPos, true);
                                if (bytesToRead > 0) {
                                    byte[] buffer = new byte[bytesToRead];
                                    raf.readFully(buffer);
                                    lastPos += bytesToRead;
                                    agentLogPositions.put(newestLog, lastPos);
                                    updateLastActivityTime();
                                    
                                    String content = new String(buffer, StandardCharsets.UTF_16LE);
                                    String searchable = agentLogMarkerSuffixes.getOrDefault(newestLog, "") + content;
                                    int newPasses = countOccurrences(searchable, AGENT_PASS_MARKER);
                                    int suffixLength = Math.min(AGENT_PASS_MARKER.length() - 1, searchable.length());
                                    agentLogMarkerSuffixes.put(newestLog,
                                            searchable.substring(searchable.length() - suffixLength));
                                    if (newPasses > 0) {
                                        agentTotalPasses += newPasses;
                                        foundNewPasses = true;
                                    }
                                }
                            } catch (IOException e) {
                                // Ignore locked or temporary access issues
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Error scanning agent directories", e);
        }

        if (foundNewPasses && progressCallback != null) {
            progressCallback.accept(agentTotalPasses, -1);
        }
    }

    private int countOccurrences(String text, String word) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(word, index)) != -1) {
            count++;
            index += word.length();
        }
        return count;
    }

    public static boolean shouldForwardToUi(String line) {
        if (line == null) return false;
        String lower = line.toLowerCase();
        
        // Always show warnings and errors
        if (lower.contains("error") || lower.contains("failed") || lower.contains("cannot load") || 
            lower.contains("warning") || lower.contains("warn") || lower.contains("critical") || 
            lower.contains("exception") || lower.contains("timeout") || lower.contains("aborted")) {
            return true;
        }
        
        // Show terminal lifecycle events
        if (lower.contains("exit with code") || lower.contains("stopped with") || 
            lower.contains("shutdown with") || lower.contains("started")) {
            return true;
        }
        
        // Show connection/network updates
        if (lower.contains("connected") || lower.contains("disconnected") || lower.contains("login")) {
            return true;
        }
        
        // Show backtest summary statistics
        if (lower.contains("final balance") || lower.contains("testing finished") || 
            lower.contains("test passed") || (lower.contains("ticks,") && lower.contains("bars generated")) ||
            lower.contains("ticks for all symbols")) {
            return true;
        }
        
        // Show optimization progress (generation updates and completion)
        if (lower.contains("best result") || lower.contains("optimization finished") ||
            lower.contains("optimization started") || lower.contains("processing")) {
            return true;
        }
        
        return false;
    }

    void processNewLines(String content, String prefix) {
        String[] lines = content.split("\\r?\\n");
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty()) {
                String lowerLine = line.toLowerCase();
                
                // Parse optimization progress from MT5 log lines
                if (progressCallback != null) {
                    // Genetic optimization: "Best result X.XX produced at generation Y. Next generation Z"
                    if (lowerLine.contains("next generation")) {
                        try {
                            java.util.regex.Matcher m = java.util.regex.Pattern.compile("next generation\\s+(\\d+)").matcher(lowerLine);
                            if (m.find()) {
                                int nextGen = Integer.parseInt(m.group(1));
                                // Estimate total generations (MT5 genetic typically uses 8-15)
                                // Dynamically adjust: if we see generation 12, estimate becomes at least 14
                                int estimatedTotal = Math.max(10, nextGen + 2);
                                progressCallback.accept(nextGen, estimatedTotal);
                            }
                        } catch (Exception e) {}
                    }
                    // Genetic optimization: progress percentage "processing X %" or "processing X%"
                    else if (lowerLine.contains("processing") && lowerLine.contains("%")) {
                        try {
                            java.util.regex.Matcher m = java.util.regex.Pattern.compile("processing\\s+(\\d+)\\s*%").matcher(lowerLine);
                            if (m.find()) {
                                int percent = Integer.parseInt(m.group(1));
                                progressCallback.accept(percent, 100);
                            }
                        } catch (Exception e) {}
                    }
                    // Genetic optimization finished: report 100%
                    else if (lowerLine.contains("genetic optimization finished")) {
                        progressCallback.accept(1, 1);
                    }
                    // Complete (non-genetic) optimization: "pass X returned"
                    else if (lowerLine.contains("pass") && lowerLine.contains("returned")) {
                        try {
                            java.util.regex.Matcher m = java.util.regex.Pattern.compile("pass\\s+(\\d+)\\s+returned").matcher(lowerLine);
                            if (m.find()) {
                                int passNum = Integer.parseInt(m.group(1));
                                progressCallback.accept(passNum, -1);
                            }
                        } catch (Exception e) {}
                    }
                    // Complete optimization finished: "optimization finished, total passes N"
                    else if (lowerLine.contains("optimization finished")) {
                        progressCallback.accept(1, 1);
                    }
                }

                // Check for critical failures that prevent backtest startup
                if (lowerLine.contains("cannot load") || 
                    lowerLine.contains("cannot start with configuration file") || 
                    lowerLine.contains("global initialization failed") || 
                    lowerLine.contains("expertremove") ||
                    lowerLine.contains("removed itself within oninit") ||
                    lowerLine.contains("tester stopped because oninit failed")) {
                    criticalFailure.set(true);
                }

                if (shouldForwardToUi(line)) {
                    // If the line is an error, highlight it
                    if (lowerLine.contains("error") || lowerLine.contains("failed") || lowerLine.contains("cannot load")) {
                        logCallback.accept("❌ " + prefix + line);
                    } else {
                        logCallback.accept(prefix + line);
                    }
                } else {
                    log.debug(prefix + line);
                }
            }
        }
    }
}
