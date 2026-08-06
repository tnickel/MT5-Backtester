package com.backtester.server;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Keeps several independently generated HTML galleries valid at the same time. */
final class GallerySessionStore<T> {

    private final Map<String, Entry<T>> entries = new ConcurrentHashMap<>();
    private final int maxEntries;
    private final long ttlMillis;
    private final LongSupplier clock;

    GallerySessionStore(int maxEntries, long ttlMillis, LongSupplier clock) {
        if (maxEntries < 1) throw new IllegalArgumentException("maxEntries must be positive");
        if (ttlMillis < 1) throw new IllegalArgumentException("ttlMillis must be positive");
        this.maxEntries = maxEntries;
        this.ttlMillis = ttlMillis;
        this.clock = clock;
    }

    synchronized String create(T context) {
        long now = clock.getAsLong();
        removeExpired(now);
        while (entries.size() >= maxEntries) removeOldest();

        String token = UUID.randomUUID().toString();
        entries.put(token, new Entry<>(context, now));
        return token;
    }

    Optional<T> find(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        Entry<T> entry = entries.get(token);
        if (entry == null) return Optional.empty();
        if (isExpired(entry, clock.getAsLong())) {
            entries.remove(token, entry);
            return Optional.empty();
        }
        return Optional.ofNullable(entry.context());
    }

    void clear() {
        entries.clear();
    }

    int size() {
        return entries.size();
    }

    private void removeExpired(long now) {
        entries.entrySet().removeIf(item -> isExpired(item.getValue(), now));
    }

    private boolean isExpired(Entry<T> entry, long now) {
        return now - entry.createdAtMillis() >= ttlMillis;
    }

    private void removeOldest() {
        entries.entrySet().stream()
                .min(Comparator.comparingLong(item -> item.getValue().createdAtMillis()))
                .ifPresent(item -> entries.remove(item.getKey(), item.getValue()));
    }

    private record Entry<T>(T context, long createdAtMillis) {
    }
}
