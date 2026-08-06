package com.backtester.server;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GallerySessionStoreTest {

    @Test
    public void creatingAnotherGalleryDoesNotInvalidateExistingToken() {
        AtomicLong now = new AtomicLong(1_000L);
        GallerySessionStore<String> store = new GallerySessionStore<>(4, 10_000L, now::get);

        String first = store.create("ticktest");
        String second = store.create("data2");

        assertEquals("ticktest", store.find(first).orElseThrow());
        assertEquals("data2", store.find(second).orElseThrow());
        assertEquals(2, store.size());
    }

    @Test
    public void expiresOldSessionsAndRejectsUnknownTokens() {
        AtomicLong now = new AtomicLong(1_000L);
        GallerySessionStore<String> store = new GallerySessionStore<>(4, 100L, now::get);
        String token = store.create("ticktest");

        now.set(1_099L);
        assertTrue(store.find(token).isPresent());
        now.set(1_100L);
        assertFalse(store.find(token).isPresent());
        assertFalse(store.find("unknown").isPresent());
    }

    @Test
    public void evictsOnlyOldestSessionWhenCapacityIsReached() {
        AtomicLong now = new AtomicLong(1_000L);
        GallerySessionStore<String> store = new GallerySessionStore<>(2, 10_000L, now::get);
        String first = store.create("first");
        now.incrementAndGet();
        String second = store.create("second");
        now.incrementAndGet();
        String third = store.create("third");

        assertFalse(store.find(first).isPresent());
        assertEquals("second", store.find(second).orElseThrow());
        assertEquals("third", store.find(third).orElseThrow());
    }
}
