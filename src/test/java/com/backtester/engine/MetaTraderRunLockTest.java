package com.backtester.engine;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MetaTraderRunLockTest {

    @Test
    public void aSecondThreadCannotDriveTheTerminalAtTheSameTime() throws Exception {
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean secondGotIn = new AtomicBoolean(true);

        Thread owner = new Thread(() -> {
            try (MetaTraderRunLock.Handle handle = MetaTraderRunLock.acquire("Workflow")) {
                held.countDown();
                release.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
        owner.setDaemon(true);
        owner.start();

        assertTrue(held.await(5, TimeUnit.SECONDS));
        Thread contender = new Thread(() -> {
            MetaTraderRunLock.Handle handle = MetaTraderRunLock.tryAcquire("Referenz");
            secondGotIn.set(handle != null);
            if (handle != null) handle.close();
        });
        contender.setDaemon(true);
        contender.start();
        contender.join(TimeUnit.SECONDS.toMillis(5));

        assertFalse(secondGotIn.get());
        assertEquals("Workflow", MetaTraderRunLock.currentOwner());
        release.countDown();
        owner.join(TimeUnit.SECONDS.toMillis(5));

        MetaTraderRunLock.Handle afterRelease = MetaTraderRunLock.tryAcquire("Referenz");
        assertNotNull(afterRelease);
        afterRelease.close();
    }

    @Test
    public void aReferenceRunInsideTheRunningPipelineThreadIsNotBlocked() throws Exception {
        try (MetaTraderRunLock.Handle pipeline = MetaTraderRunLock.acquire("Workflow")) {
            assertFalse(MetaTraderRunLock.isBusyForOtherThread());
            try (MetaTraderRunLock.Handle nested = MetaTraderRunLock.acquire("Referenz")) {
                assertNotNull(nested);
            }
            // The nested close must not hand the terminal to someone else yet.
            AtomicBoolean stolen = new AtomicBoolean();
            Thread other = new Thread(() -> {
                MetaTraderRunLock.Handle handle = MetaTraderRunLock.tryAcquire("Fremd");
                stolen.set(handle != null);
                if (handle != null) handle.close();
            });
            other.setDaemon(true);
            other.start();
            other.join(TimeUnit.SECONDS.toMillis(5));
            assertFalse(stolen.get());
        }
        assertNull(nullIfHeld());
    }

    /** Null when the lock is free again, otherwise a marker for the assertion. */
    private static String nullIfHeld() {
        MetaTraderRunLock.Handle handle = MetaTraderRunLock.tryAcquire("Pruefung");
        if (handle == null) return "still held";
        handle.close();
        return null;
    }
}
