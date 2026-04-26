package com.backtester.engine;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Tails MT5 log files (Terminal and Tester) continuously.
 * MT5 logs are written in UTF-16LE format.
 */
public class Mt5LogTailer implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(Mt5LogTailer.class);

    private final Path mt5Dir;
    private final Consumer<String> logCallback;
    private java.util.function.BiConsumer<Integer, Integer> progressCallback;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private long terminalLogLastPos = 0;
    private long testerLogLastPos = 0;

    private Path terminalLogPath;
    private Path testerLogPath;

    public Mt5LogTailer(Path mt5Dir, Consumer<String> logCallback) {
        this.mt5Dir = mt5Dir;
        this.logCallback = logCallback;
        
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        this.terminalLogPath = mt5Dir.resolve("logs").resolve(dateStr + ".log");
        this.testerLogPath = mt5Dir.resolve("Tester").resolve("logs").resolve(dateStr + ".log");
    }

    public void setProgressCallback(java.util.function.BiConsumer<Integer, Integer> progressCallback) {
        this.progressCallback = progressCallback;
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
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
                terminalLogLastPos = tailFile(terminalLogPath, terminalLogLastPos, "[Terminal] ");
                testerLogLastPos = tailFile(testerLogPath, testerLogLastPos, "[Tester] ");
                
                Thread.sleep(500);
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
        File file = filePath.toFile();
        if (!file.exists()) {
            return lastPos;
        }

        long length = file.length();
        if (length < lastPos) {
            // File was truncated or recreated
            lastPos = 0;
        }

        if (length > lastPos) {
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                raf.seek(lastPos);
                int bytesToRead = (int) (length - lastPos);
                
                // Ensure we read an even number of bytes for UTF-16LE
                // If it's odd, MT5 might be mid-write. We'll leave the last byte for next time.
                if (bytesToRead % 2 != 0) {
                    bytesToRead--;
                }
                
                if (bytesToRead > 0) {
                    byte[] buffer = new byte[bytesToRead];
                    raf.readFully(buffer);
                    lastPos += bytesToRead;

                    String newContent = new String(buffer, StandardCharsets.UTF_16LE);
                    processNewLines(newContent, prefix);
                }
            } catch (IOException e) {
                log.warn("Failed to read log file: {}", filePath, e);
            }
        }
        return lastPos;
    }

    private void processNewLines(String content, String prefix) {
        String[] lines = content.split("\\r?\\n");
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty()) {
                String lowerLine = line.toLowerCase();
                
                // Parse optimization progress (e.g. "pass 12 returned result")
                if (progressCallback != null && lowerLine.contains("pass") && lowerLine.contains("returned")) {
                    try {
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("pass\\s+(\\d+)\\s+returned").matcher(lowerLine);
                        if (m.find()) {
                            int passNum = Integer.parseInt(m.group(1));
                            progressCallback.accept(passNum, -1);
                        }
                    } catch (Exception e) {}
                }

                // If the line is an error, highlight it
                if (lowerLine.contains("error") || lowerLine.contains("failed") || lowerLine.contains("cannot load")) {
                    logCallback.accept("❌ " + prefix + line);
                } else {
                    logCallback.accept(prefix + line);
                }
            }
        }
    }
}
