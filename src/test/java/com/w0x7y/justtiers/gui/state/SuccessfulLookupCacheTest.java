package com.w0x7y.justtiers.gui.state;

import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** A temporary skin failure must not become the account's permanent cached skin. */
class SuccessfulLookupCacheTest {
    @Test
    void failureRetriesWhileSuccessfulAnswersAreRemembered() {
        AtomicInteger requests = new AtomicInteger();
        var cache = new SuccessfulLookupCache<String, String>(key ->
                requests.getAndIncrement() == 0
                        ? CompletableFuture.failedFuture(new IllegalStateException("offline"))
                        : CompletableFuture.completedFuture("downloaded skin"));
        assertTrue(cache.get("player").isCompletedExceptionally());
        assertEquals("downloaded skin", cache.get("player").join());
        assertEquals("downloaded skin", cache.get("player").join());
        assertEquals(2, requests.get());
    }

    @Test
    void pendingRequestsAreSharedAndLateFailureCanRetry() {
        AtomicInteger requests = new AtomicInteger();
        CompletableFuture<String> response = new CompletableFuture<>();
        var cache = new SuccessfulLookupCache<String, String>(key -> {
            requests.incrementAndGet();
            return response;
        });
        cache.get("player");
        cache.get("player");
        assertEquals(1, requests.get());
        response.completeExceptionally(new IllegalStateException("timeout"));
        cache.get("player");
        assertEquals(2, requests.get());
    }
}
