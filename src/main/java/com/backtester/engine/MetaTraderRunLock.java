package com.backtester.engine;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Serializes everything that drives the MetaTrader terminal.
 *
 * <p>Optimizer, retester and reference backtest share one installation, one
 * {@code tester.ini} and one report file name, and several of them kill stray
 * terminals on start. Two concurrent runs therefore do not just queue up — they
 * destroy each other's results. This lock is process-wide and reentrant, so a
 * reference backtest started from inside a running pipeline thread still passes.
 */
public final class MetaTraderRunLock {

    private static final ReentrantLock LOCK = new ReentrantLock(true);
    private static volatile String currentOwner = "";

    private MetaTraderRunLock() {
    }

    /**
     * Blocks until the terminal is free. The returned handle must be closed on the
     * same thread that acquired it.
     */
    public static Handle acquire(String owner) throws InterruptedException {
        LOCK.lockInterruptibly();
        currentOwner = owner != null ? owner : "";
        return new Handle();
    }

    /** Non-blocking variant; empty when someone else is driving the terminal. */
    public static Handle tryAcquire(String owner) {
        if (!LOCK.tryLock()) return null;
        currentOwner = owner != null ? owner : "";
        return new Handle();
    }

    /** True when another thread currently drives the terminal. */
    public static boolean isBusyForOtherThread() {
        return LOCK.isLocked() && !LOCK.isHeldByCurrentThread();
    }

    public static String currentOwner() {
        return currentOwner;
    }

    public static final class Handle implements AutoCloseable {
        private boolean closed;

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (LOCK.isHeldByCurrentThread()) {
                if (LOCK.getHoldCount() == 1) currentOwner = "";
                LOCK.unlock();
            }
        }
    }
}
