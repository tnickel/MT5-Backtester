package com.backtester.database;

import com.backtester.workflow.CustomProject;
import com.backtester.workflow.DatabankManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Coalesces frequent Custom Project changes and persists them on one background
 * writer. Only the small project/task metadata snapshot is created by the
 * caller; potentially large databanks are copied and serialized by the writer.
 */
public final class CustomProjectSaveCoordinator implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(CustomProjectSaveCoordinator.class);
    private static final Duration DEFAULT_CLOSE_TIMEOUT = Duration.ofSeconds(30);

    private final Object monitor = new Object();
    private final long debounceMillis;
    private final Function<CustomProject, Boolean> writer;
    private final Consumer<String> failureHandler;
    private final ScheduledExecutorService executor;

    private SaveRequest latestRequest;
    private ScheduledFuture<?> scheduledSave;
    private long latestVersion;
    private long persistedVersion;
    private boolean closed;

    public CustomProjectSaveCoordinator(DatabaseManager databaseManager,
                                        long debounceMillis,
                                        Consumer<String> failureHandler) {
        this(debounceMillis, databaseManager::saveCustomProject, failureHandler);
    }

    CustomProjectSaveCoordinator(long debounceMillis,
                                 Function<CustomProject, Boolean> writer,
                                 Consumer<String> failureHandler) {
        if (debounceMillis < 0) {
            throw new IllegalArgumentException("debounceMillis must not be negative");
        }
        this.debounceMillis = debounceMillis;
        this.writer = Objects.requireNonNull(writer, "writer");
        this.failureHandler = failureHandler != null ? failureHandler : message -> { };
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "custom-project-sqlite-writer");
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newSingleThreadScheduledExecutor(threadFactory);
    }

    /**
     * Queues the current state for persistence. Repeated calls within the
     * debounce interval replace the pending state instead of adding writes.
     */
    public void requestSave(CustomProject project, DatabankManager databankManager) {
        if (project == null || databankManager == null) return;

        CustomProject metadataSnapshot = project.copyMetadataForPersistence();
        boolean includeDatabankContents = project.isSaveDatabanksPersistently();

        synchronized (monitor) {
            if (closed) {
                logger.warn("Ignoring Custom Project save request after coordinator shutdown");
                return;
            }
            latestVersion++;
            latestRequest = new SaveRequest(latestVersion, metadataSnapshot,
                    databankManager, includeDatabankContents);
            if (scheduledSave != null && !scheduledSave.isDone()) {
                scheduledSave.cancel(false);
            }
            scheduledSave = executor.schedule(this::persistLatestIfNeeded,
                    debounceMillis, TimeUnit.MILLISECONDS);
        }
    }

    /** Forces the most recently requested state onto the single writer queue. */
    public CompletableFuture<Boolean> flushAsync() {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        synchronized (monitor) {
            if (closed) {
                result.complete(false);
                return result;
            }
            if (scheduledSave != null && !scheduledSave.isDone()) {
                scheduledSave.cancel(false);
            }
            executor.execute(() -> {
                try {
                    result.complete(persistLatestIfNeeded());
                } catch (RuntimeException ex) {
                    result.completeExceptionally(ex);
                }
            });
        }
        return result;
    }

    /** Waits for a forced save; intended for worker or application-shutdown threads. */
    public boolean flush(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        try {
            return flushAsync().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while flushing Custom Project save", ex);
        } catch (TimeoutException ex) {
            logger.warn("Timed out while flushing Custom Project save after {} ms", timeout.toMillis());
        } catch (Exception ex) {
            logger.error("Failed to flush Custom Project save", ex);
        }
        return false;
    }

    private boolean persistLatestIfNeeded() {
        SaveRequest request;
        synchronized (monitor) {
            request = latestRequest;
            if (request == null || request.version() <= persistedVersion) {
                return true;
            }
        }

        boolean saved = false;
        try {
            request.databankManager().saveToProject(
                    request.projectSnapshot(), request.includeDatabankContents());
            saved = Boolean.TRUE.equals(writer.apply(request.projectSnapshot()));
        } catch (RuntimeException ex) {
            logger.error("Unexpected error while saving Custom Project", ex);
        }

        if (saved) {
            synchronized (monitor) {
                persistedVersion = Math.max(persistedVersion, request.version());
            }
        } else {
            notifyFailure("Projekt konnte nicht in SQLite gespeichert werden.");
        }
        return saved;
    }

    private void notifyFailure(String message) {
        try {
            failureHandler.accept(message);
        } catch (RuntimeException ex) {
            logger.warn("Custom Project save failure handler failed", ex);
        }
    }

    @Override
    public void close() {
        flush(DEFAULT_CLOSE_TIMEOUT);
        synchronized (monitor) {
            closed = true;
            if (scheduledSave != null && !scheduledSave.isDone()) {
                scheduledSave.cancel(false);
            }
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(DEFAULT_CLOSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                logger.warn("Custom Project SQLite writer did not stop within {} ms",
                        DEFAULT_CLOSE_TIMEOUT.toMillis());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private record SaveRequest(long version,
                               CustomProject projectSnapshot,
                               DatabankManager databankManager,
                               boolean includeDatabankContents) {
    }
}
