package com.w0x7y.justtiers.cache;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

/** Successful answers persist; failed lookups leave a shared retry path. */
class SuccessfulLookupCacheTest {
    @Test
    void failureRetriesWhileSuccessfulAnswersAreRemembered() {
        AtomicInteger requests = new AtomicInteger();
        var cache = new SuccessfulLookupCache<String, String>();
        Supplier<CompletableFuture<String>> fetch = () ->
                requests.getAndIncrement() == 0
                        ? CompletableFuture.failedFuture(new IllegalStateException("offline"))
                        : CompletableFuture.completedFuture("downloaded skin");
        assertTrue(cache.get("player", fetch).isCompletedExceptionally());
        assertEquals("downloaded skin", cache.get("player", fetch).join());
        assertEquals("downloaded skin", cache.get("player", fetch).join());
        assertEquals(2, requests.get());
    }

    @Test
    void pendingRequestsAreSharedAndLateFailureCanRetry() {
        AtomicInteger requests = new AtomicInteger();
        CompletableFuture<String> response = new CompletableFuture<>();
        var cache = new SuccessfulLookupCache<String, String>();
        Supplier<CompletableFuture<String>> fetch = () -> {
            requests.incrementAndGet();
            return response;
        };
        cache.get("player", fetch);
        cache.get("player", fetch);
        assertEquals(1, requests.get());
        response.completeExceptionally(new IllegalStateException("timeout"));
        cache.get("player", fetch);
        assertEquals(2, requests.get());
    }

    @Test
    void aSuccessfulEmptyAnswerIsRetained() {
        AtomicInteger requests = new AtomicInteger();
        var cache = new SuccessfulLookupCache<String, Optional<String>>();
        Supplier<CompletableFuture<Optional<String>>> fetch = () -> {
            requests.incrementAndGet();
            return CompletableFuture.completedFuture(Optional.empty());
        };

        assertTrue(cache.get("unowned name", fetch).join().isEmpty());
        assertTrue(cache.get("unowned name", fetch).join().isEmpty());
        assertEquals(1, requests.get());
    }

    @Test
    void simultaneousLookupsShareOnePendingRequest() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        CompletableFuture<String> response = new CompletableFuture<>();
        var cache = new SuccessfulLookupCache<String, String>();
        Supplier<CompletableFuture<String>> fetch = () -> {
            requests.incrementAndGet();
            return response;
        };

        List<CompletableFuture<String>> results = simultaneousLookups(cache, fetch);

        assertEquals(1, requests.get());
        response.complete("downloaded skin");
        for (CompletableFuture<String> result : results) {
            assertSame(response, result);
            assertEquals("downloaded skin", result.join());
        }
    }

    @Test
    void observingFailureAllowsConcurrentRetryBeforeEvictionCallbackRuns() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        CompletableFuture<String> failed = new CompletableFuture<>();
        CompletableFuture<String> retry = new CompletableFuture<>();
        var cache = new SuccessfulLookupCache<String, String>();
        Supplier<CompletableFuture<String>> fetch = () ->
                requests.getAndIncrement() == 0 ? failed : retry;
        assertSame(failed, cache.get("player", fetch));
        CountDownLatch failureObserved = new CountDownLatch(1);
        CountDownLatch releaseCallback = new CountDownLatch(1);
        // A later dependent runs first and holds up the cache's eviction callback.
        // The future itself has already failed, so a new caller must be able to retry.
        failed.whenComplete((value, error) -> {
            failureObserved.countDown();
            await(releaseCallback);
        });
        ExecutorService completion = Executors.newSingleThreadExecutor();
        try {
            completion.submit(() -> failed.completeExceptionally(new IllegalStateException("offline")));
            assertTrue(failureObserved.await(5, TimeUnit.SECONDS));
            assertTrue(failed.isCompletedExceptionally());

            List<CompletableFuture<String>> results = simultaneousLookups(cache, fetch);

            assertEquals(2, requests.get(), "observed failure must start exactly one shared retry");
            for (CompletableFuture<String> result : results) {
                assertSame(retry, result);
            }
            releaseCallback.countDown();
            completion.shutdown();
            assertTrue(completion.awaitTermination(5, TimeUnit.SECONDS));
            assertSame(retry, cache.get("player", fetch), "late eviction must preserve the retry");
            retry.complete("downloaded skin");
            assertEquals("downloaded skin", cache.get("player", fetch).join());
            assertEquals(2, requests.get());
        } finally {
            releaseCallback.countDown();
            completion.shutdownNow();
        }
    }

    private static List<CompletableFuture<String>> simultaneousLookups(
            SuccessfulLookupCache<String, String> cache,
            Supplier<CompletableFuture<String>> fetch) throws Exception {
        int callers = 8;
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            List<CompletableFuture<CompletableFuture<String>>> calls = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                calls.add(CompletableFuture.supplyAsync(() -> {
                    ready.countDown();
                    await(start);
                    return cache.get("player", fetch);
                }, pool));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<CompletableFuture<String>> results = new ArrayList<>();
            for (CompletableFuture<CompletableFuture<String>> call : calls) {
                results.add(call.get(5, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "callback must be released");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
