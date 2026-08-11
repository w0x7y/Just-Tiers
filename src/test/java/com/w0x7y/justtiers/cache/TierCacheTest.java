package com.w0x7y.justtiers.cache;

import com.w0x7y.justtiers.api.TierSource;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TierCacheTest {

    private static final UUID PLAYER = UUID.randomUUID();

    /** A source we can control precisely, counting calls and completing on demand. */
    private static final class FakeSource implements TierSource {
        private final Source source;
        private final Map<String, Tier> result;
        final AtomicInteger calls = new AtomicInteger();
        CompletableFuture<Map<String, Tier>> pending;

        FakeSource(Source source, Map<String, Tier> result) {
            this.source = source;
            this.result = result;
        }

        @Override public Source source() { return source; }

        @Override public CompletableFuture<Map<String, Tier>> fetch(UUID uuid) {
            calls.incrementAndGet();
            pending = new CompletableFuture<>();
            return pending;
        }

        void complete() { pending.complete(result); }
    }

    @Test
    void peekReturnsEmptyWhilePendingThenTheResult() {
        FakeSource fake = new FakeSource(Source.MCTIERS, Map.of("axe", new Tier(2, true, false)));
        TierCache cache = new TierCache(List.of(fake));

        assertEquals(Optional.empty(), cache.peek(Source.MCTIERS, PLAYER));
        assertEquals(1, fake.calls.get());

        fake.complete();
        Optional<Map<String, Tier>> loaded = cache.peek(Source.MCTIERS, PLAYER);
        assertTrue(loaded.isPresent());
        assertEquals("HT2", loaded.get().get("axe").label());
    }

    @Test
    void repeatedPeeksIssueOnlyOneFetch() {
        FakeSource fake = new FakeSource(Source.MCTIERS, Map.of());
        TierCache cache = new TierCache(List.of(fake));

        cache.peek(Source.MCTIERS, PLAYER);
        cache.peek(Source.MCTIERS, PLAYER);
        cache.peek(Source.MCTIERS, PLAYER);

        assertEquals(1, fake.calls.get(), "in-flight requests must be coalesced");
    }

    @Test
    void unrankedResultsAreCachedAsEmptyNotRefetched() {
        FakeSource fake = new FakeSource(Source.MCTIERS, Map.of());
        TierCache cache = new TierCache(List.of(fake));

        cache.peek(Source.MCTIERS, PLAYER);
        fake.complete();

        Optional<Map<String, Tier>> loaded = cache.peek(Source.MCTIERS, PLAYER);
        assertTrue(loaded.isPresent(), "a known-unranked player is loaded, not pending");
        assertTrue(loaded.get().isEmpty());

        cache.peek(Source.MCTIERS, PLAYER);
        assertEquals(1, fake.calls.get(), "negative results must not be refetched");
    }

    @Test
    void sourcesAreCachedIndependently() {
        FakeSource mct = new FakeSource(Source.MCTIERS, Map.of("axe", new Tier(1, true, false)));
        FakeSource sub = new FakeSource(Source.SUBTIERS, Map.of("bow", new Tier(3, false, false)));
        TierCache cache = new TierCache(List.of(mct, sub));

        cache.peek(Source.MCTIERS, PLAYER);
        mct.complete();

        assertTrue(cache.peek(Source.MCTIERS, PLAYER).isPresent());
        assertEquals(Optional.empty(), cache.peek(Source.SUBTIERS, PLAYER));
        assertEquals(1, sub.calls.get());
    }

    @Test
    void peekForAnUnconfiguredSourceIsLoadedAndEmpty() {
        TierCache cache = new TierCache(List.of());
        Optional<Map<String, Tier>> result = cache.peek(Source.NOVATIERS, PLAYER);
        assertTrue(result.isPresent());
        assertTrue(result.get().isEmpty());
    }

    @Test
    void invalidateAllForcesARefetch() {
        FakeSource fake = new FakeSource(Source.MCTIERS, Map.of());
        TierCache cache = new TierCache(List.of(fake));

        cache.peek(Source.MCTIERS, PLAYER);
        fake.complete();
        cache.invalidateAll();
        cache.peek(Source.MCTIERS, PLAYER);

        assertEquals(2, fake.calls.get());
    }

    @Test
    void loadExposesTheAwaitableFuture() throws Exception {
        FakeSource fake = new FakeSource(Source.MCTIERS, Map.of("axe", new Tier(4, false, false)));
        TierCache cache = new TierCache(List.of(fake));

        CompletableFuture<Map<String, Tier>> future = cache.load(Source.MCTIERS, PLAYER);
        fake.complete();
        assertEquals("LT4", future.get().get("axe").label());
    }

    @Test
    void aFailedFetchIsNotCachedAndCanBeRetried() {
        FakeSource fake = new FakeSource(Source.MCTIERS, Map.of());
        TierCache cache = new TierCache(List.of(fake));

        cache.peek(Source.MCTIERS, PLAYER);
        fake.pending.completeExceptionally(new RuntimeException("network down"));

        assertEquals(Optional.empty(), cache.peek(Source.MCTIERS, PLAYER),
                "a failed lookup must not be reported as loaded");
        assertEquals(2, fake.calls.get(), "a failed lookup must be retried");
    }
}
