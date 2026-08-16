package com.w0x7y.justtiers.cache;

import com.w0x7y.justtiers.api.TierSource;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
    void invalidateOnlyForcesARefetchForTheGivenSource() {
        FakeSource mct = new FakeSource(Source.MCTIERS, Map.of());
        FakeSource sub = new FakeSource(Source.SUBTIERS, Map.of());
        TierCache cache = new TierCache(List.of(mct, sub));

        cache.peek(Source.MCTIERS, PLAYER);
        mct.complete();
        cache.peek(Source.SUBTIERS, PLAYER);
        sub.complete();

        cache.invalidate(Source.MCTIERS);

        cache.peek(Source.MCTIERS, PLAYER);
        assertEquals(2, mct.calls.get(), "the invalidated source must be refetched");

        cache.peek(Source.SUBTIERS, PLAYER);
        assertEquals(1, sub.calls.get(), "the untouched source must survive");
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
    void aFailedFetchIsNotCachedAndIsRetriedOnceTheDelayHasPassed() {
        FakeSource fake = new FakeSource(Source.MCTIERS, Map.of());
        TierCache cache = new TierCache(List.of(fake), Duration.ZERO);

        cache.peek(Source.MCTIERS, PLAYER);
        fake.pending.completeExceptionally(new RuntimeException("network down"));

        assertEquals(Optional.empty(), cache.peek(Source.MCTIERS, PLAYER),
                "a failed lookup must not be reported as loaded");
        assertEquals(Optional.empty(), cache.peek(Source.MCTIERS, PLAYER));
        assertEquals(2, fake.calls.get(), "a failed lookup must be retried");
    }

    @Test
    void aFailedFetchIsNotRetriedWhileTheDelayIsStillRunning() {
        FakeSource fake = new FakeSource(Source.MCTIERS, Map.of());
        TierCache cache = new TierCache(List.of(fake), Duration.ofMinutes(10));

        cache.peek(Source.MCTIERS, PLAYER);
        fake.pending.completeExceptionally(new RuntimeException("network down"));

        // peek() runs every frame, so the backoff is what stops a failing site being hammered.
        for (int i = 0; i < 50; i++) {
            assertEquals(Optional.empty(), cache.peek(Source.MCTIERS, PLAYER));
        }
        assertEquals(1, fake.calls.get(), "the retry delay must suppress further attempts");
    }

    @Test
    void aFailedFetchIsNeverReportedAsAnUnrankedPlayer() {
        FakeSource fake = new FakeSource(Source.MCTIERS, Map.of());
        TierCache cache = new TierCache(List.of(fake), Duration.ofMinutes(10));

        cache.peek(Source.MCTIERS, PLAYER);
        fake.pending.completeExceptionally(new RuntimeException("site down"));

        // Optional.of(Map.of()) would mean "known unranked" and would blank the badge.
        assertTrue(cache.peek(Source.MCTIERS, PLAYER).isEmpty());
    }

    @Test
    void invalidatingClearsTheRetryDelaySoRefreshRetriesImmediately() {
        FakeSource fake = new FakeSource(Source.MCTIERS, Map.of());
        TierCache cache = new TierCache(List.of(fake), Duration.ofMinutes(10));

        cache.peek(Source.MCTIERS, PLAYER);
        fake.pending.completeExceptionally(new RuntimeException("site down"));
        cache.peek(Source.MCTIERS, PLAYER);
        assertEquals(1, fake.calls.get());

        cache.invalidateAll();
        cache.peek(Source.MCTIERS, PLAYER);
        assertEquals(2, fake.calls.get(), "/justtiers refresh must not wait out the backoff");
    }

    // --- forgetFailed ---

    @Test
    void loadOnItsOwnWouldKeepAFailureForever() {
        // The behaviour forgetFailed exists to correct: nothing peeks at a player who is
        // not in the world, so a failed load would be replayed by every later lookup.
        FakeSource fake = new FakeSource(Source.MCTIERS, Map.of());
        TierCache cache = new TierCache(List.of(fake));

        cache.load(Source.MCTIERS, PLAYER);
        fake.pending.completeExceptionally(new RuntimeException("site down"));

        assertTrue(cache.load(Source.MCTIERS, PLAYER).isCompletedExceptionally());
        assertEquals(1, fake.calls.get());
    }

    @Test
    void forgetFailedLetsTheNextLoadTryAgain() {
        FakeSource fake = new FakeSource(Source.MCTIERS, Map.of("axe", new Tier(1, true, false)));
        TierCache cache = new TierCache(List.of(fake));

        cache.load(Source.MCTIERS, PLAYER);
        fake.pending.completeExceptionally(new RuntimeException("site down"));
        cache.forgetFailed(Source.MCTIERS, PLAYER);

        cache.load(Source.MCTIERS, PLAYER);
        assertEquals(2, fake.calls.get());
    }

    @Test
    void forgetFailedLeavesASuccessfulEntryAlone() throws Exception {
        FakeSource fake = new FakeSource(Source.MCTIERS, Map.of("axe", new Tier(1, true, false)));
        TierCache cache = new TierCache(List.of(fake));

        CompletableFuture<Map<String, Tier>> loaded = cache.load(Source.MCTIERS, PLAYER);
        fake.complete();
        cache.forgetFailed(Source.MCTIERS, PLAYER);

        assertSame(loaded, cache.load(Source.MCTIERS, PLAYER));
        assertEquals(1, fake.calls.get());
        assertEquals("HT1", loaded.get().get("axe").label());
    }

    @Test
    void forgetFailedLeavesALookupStillInFlightAlone() {
        FakeSource fake = new FakeSource(Source.MCTIERS, Map.of());
        TierCache cache = new TierCache(List.of(fake));

        CompletableFuture<Map<String, Tier>> inFlight = cache.load(Source.MCTIERS, PLAYER);
        cache.forgetFailed(Source.MCTIERS, PLAYER);

        assertSame(inFlight, cache.load(Source.MCTIERS, PLAYER));
        assertEquals(1, fake.calls.get());
    }

    @Test
    void aSuccessfulLoadEndsTheBackoffAnEarlierFailureLeftBehind() {
        // /justtiers lookup goes through load(), which ignores the backoff. When it
        // succeeds the site has answered, so peek() must stop reporting "not yet known"
        // rather than blanking the badge for the rest of the delay.
        FakeSource fake = new FakeSource(Source.MCTIERS, Map.of("axe", new Tier(1, true, false)));
        TierCache cache = new TierCache(List.of(fake), Duration.ofMinutes(10));

        cache.peek(Source.MCTIERS, PLAYER);
        fake.pending.completeExceptionally(new RuntimeException("site down"));
        assertEquals(Optional.empty(), cache.peek(Source.MCTIERS, PLAYER));

        cache.load(Source.MCTIERS, PLAYER);
        fake.complete();

        Optional<Map<String, Tier>> loaded = cache.peek(Source.MCTIERS, PLAYER);
        assertTrue(loaded.isPresent(), "the badge must not stay blank behind a spent backoff");
        assertEquals("HT1", loaded.get().get("axe").label());
    }

    @Test
    void aFailedLoadLeavesTheBackoffInPlace() {
        // Only an answer spends the backoff; a second failure must not hand peek() a
        // free retry, or a failing site gets hammered every frame again.
        FakeSource fake = new FakeSource(Source.MCTIERS, Map.of());
        TierCache cache = new TierCache(List.of(fake), Duration.ofMinutes(10));

        cache.peek(Source.MCTIERS, PLAYER);
        fake.pending.completeExceptionally(new RuntimeException("site down"));
        cache.peek(Source.MCTIERS, PLAYER);

        cache.load(Source.MCTIERS, PLAYER);
        fake.pending.completeExceptionally(new RuntimeException("still down"));
        cache.forgetFailed(Source.MCTIERS, PLAYER);

        for (int i = 0; i < 10; i++) {
            assertEquals(Optional.empty(), cache.peek(Source.MCTIERS, PLAYER));
        }
        assertEquals(2, fake.calls.get(), "the backoff must survive a failed load");
    }

    @Test
    void forgettingAPlayerWhoWasNeverLookedUpIsHarmless() {
        TierCache cache = new TierCache(List.of());
        assertDoesNotThrow(() -> cache.forgetFailed(Source.NOVATIERS, PLAYER));
    }

    // --- freshness, growing delays and the site gate ---

    /** A cache whose clock we drive by hand, with jitter pinned to the middle. */
    private static final class Controlled {
        final AtomicLong clock = new AtomicLong();
        final FakeSource fake;
        final TierCache cache;

        Controlled(Map<String, Tier> result, CachePolicy policy) {
            this.fake = new FakeSource(Source.MCTIERS, result);
            this.cache = new TierCache(List.of(fake), policy, clock::get, () -> 0.5);
        }

        void advance(Duration by) {
            clock.addAndGet(by.toNanos());
        }
    }

    private static CachePolicy policy() {
        return CachePolicy.DEFAULT;
    }

    @Test
    void aFreshAnswerIsNotFetchedAgain() {
        Controlled it = new Controlled(Map.of("axe", new Tier(1, true, false)), policy());

        it.cache.peek(Source.MCTIERS, PLAYER);
        it.fake.complete();
        it.advance(Duration.ofMinutes(59));

        assertTrue(it.cache.peek(Source.MCTIERS, PLAYER).isPresent());
        assertEquals(1, it.fake.calls.get());
    }

    @Test
    void anAnswerIsFetchedAgainOnceItIsStale() {
        Controlled it = new Controlled(Map.of("axe", new Tier(1, true, false)), policy());

        it.cache.peek(Source.MCTIERS, PLAYER);
        it.fake.complete();
        it.advance(Duration.ofMinutes(60));

        assertEquals(Optional.empty(), it.cache.peek(Source.MCTIERS, PLAYER),
                "a stale answer is not an answer");
        assertEquals(2, it.fake.calls.get(), "it must be asked again");
    }

    @Test
    void anUnrankedAnswerGoesStaleTooSoATestedPlayerAppears() {
        // The whole point of the TTL: a player nobody had tested at login must not read
        // as untested all session once they have been.
        Controlled it = new Controlled(Map.of(), policy());

        it.cache.peek(Source.MCTIERS, PLAYER);
        it.fake.complete();
        assertEquals(Optional.of(Map.of()), it.cache.peek(Source.MCTIERS, PLAYER));

        it.advance(Duration.ofMinutes(60));
        assertEquals(Optional.empty(), it.cache.peek(Source.MCTIERS, PLAYER));
        assertEquals(2, it.fake.calls.get());
    }

    @Test
    void aZeroTtlKeepsAnswersForTheWholeSession() {
        Controlled it = new Controlled(Map.of("axe", new Tier(1, true, false)),
                policy().withTtl(Duration.ZERO));

        it.cache.peek(Source.MCTIERS, PLAYER);
        it.fake.complete();
        it.advance(Duration.ofDays(30));

        assertTrue(it.cache.peek(Source.MCTIERS, PLAYER).isPresent());
        assertEquals(1, it.fake.calls.get());
    }

    @Test
    void loadAlsoRefusesToServeAStaleAnswer() {
        Controlled it = new Controlled(Map.of("axe", new Tier(1, true, false)), policy());

        it.cache.load(Source.MCTIERS, PLAYER);
        it.fake.complete();
        it.advance(Duration.ofMinutes(60));

        it.cache.load(Source.MCTIERS, PLAYER);
        assertEquals(2, it.fake.calls.get());
    }

    @Test
    void eachConsecutiveFailureWaitsLongerThanTheLast() {
        Controlled it = new Controlled(Map.of(), policy());

        // First failure: a minute.
        it.cache.peek(Source.MCTIERS, PLAYER);
        it.fake.pending.completeExceptionally(new RuntimeException("down"));
        it.cache.peek(Source.MCTIERS, PLAYER);

        it.advance(Duration.ofSeconds(59));
        it.cache.peek(Source.MCTIERS, PLAYER);
        assertEquals(1, it.fake.calls.get(), "a minute has not passed");

        it.advance(Duration.ofSeconds(1));
        it.cache.peek(Source.MCTIERS, PLAYER);
        assertEquals(2, it.fake.calls.get());

        // Second failure: two minutes, so the minute that sufficed before does not.
        it.fake.pending.completeExceptionally(new RuntimeException("still down"));
        it.cache.peek(Source.MCTIERS, PLAYER);
        it.advance(Duration.ofSeconds(60));
        it.cache.peek(Source.MCTIERS, PLAYER);
        assertEquals(2, it.fake.calls.get(), "the wait must have grown");

        it.advance(Duration.ofSeconds(60));
        it.cache.peek(Source.MCTIERS, PLAYER);
        assertEquals(3, it.fake.calls.get());
    }

    @Test
    void aSuccessResetsTheGrowthSoTheNextFailureWaitsAMinuteAgain() {
        Controlled it = new Controlled(Map.of("axe", new Tier(1, true, false)), policy());

        it.cache.peek(Source.MCTIERS, PLAYER);
        it.fake.pending.completeExceptionally(new RuntimeException("down"));
        it.cache.peek(Source.MCTIERS, PLAYER);
        it.advance(Duration.ofSeconds(60));
        it.cache.peek(Source.MCTIERS, PLAYER);
        it.fake.complete();
        it.advance(Duration.ofMinutes(60));

        // Stale, so it is asked again, and this time it fails.
        it.cache.peek(Source.MCTIERS, PLAYER);
        it.fake.pending.completeExceptionally(new RuntimeException("down again"));
        it.cache.peek(Source.MCTIERS, PLAYER);

        int before = it.fake.calls.get();
        it.advance(Duration.ofSeconds(60));
        it.cache.peek(Source.MCTIERS, PLAYER);
        assertEquals(before + 1, it.fake.calls.get(),
                "the run of failures was broken, so this is a first failure again");
    }

    @Test
    void enoughFailuresInARowStopTheSiteBeingAskedAtAll() {
        Controlled it = new Controlled(Map.of(), policy());

        // Eight different players, each failing once: no single player's delay is up,
        // but the site has now failed eight times in a row.
        for (int i = 0; i < 8; i++) {
            UUID player = UUID.randomUUID();
            it.cache.peek(Source.MCTIERS, player);
            it.fake.pending.completeExceptionally(new RuntimeException("down"));
            it.cache.peek(Source.MCTIERS, player);
        }
        assertEquals(8, it.fake.calls.get());

        // A ninth player, never seen before, is not asked either: the site is closed.
        assertEquals(Optional.empty(), it.cache.peek(Source.MCTIERS, UUID.randomUUID()));
        assertEquals(8, it.fake.calls.get(), "a closed site must not be asked");
    }

    @Test
    void aClosedSiteFailsALoadImmediatelyRatherThanAskingIt() {
        Controlled it = new Controlled(Map.of(), policy());

        for (int i = 0; i < 8; i++) {
            UUID player = UUID.randomUUID();
            it.cache.peek(Source.MCTIERS, player);
            it.fake.pending.completeExceptionally(new RuntimeException("down"));
            it.cache.peek(Source.MCTIERS, player);
        }

        // A scan of two hundred players must not wave them all past the gate.
        CompletableFuture<Map<String, Tier>> future = it.cache.load(Source.MCTIERS, UUID.randomUUID());
        assertTrue(future.isCompletedExceptionally());
        assertEquals(8, it.fake.calls.get());
    }

    @Test
    void aClosedSiteReopensWithOneProbe() {
        Controlled it = new Controlled(Map.of("axe", new Tier(1, true, false)), policy());

        for (int i = 0; i < 8; i++) {
            UUID player = UUID.randomUUID();
            it.cache.peek(Source.MCTIERS, player);
            it.fake.pending.completeExceptionally(new RuntimeException("down"));
            it.cache.peek(Source.MCTIERS, player);
        }
        it.advance(Duration.ofSeconds(30));

        // Exactly one request goes out, however many players are on screen.
        for (int i = 0; i < 20; i++) {
            it.cache.peek(Source.MCTIERS, UUID.randomUUID());
        }
        assertEquals(9, it.fake.calls.get(), "the pause must end with a single probe");

        // It answers, so the site is open for business again.
        it.fake.complete();
        it.cache.peek(Source.MCTIERS, UUID.randomUUID());
        assertEquals(10, it.fake.calls.get());
    }

    @Test
    void refreshingReopensAClosedSite() {
        Controlled it = new Controlled(Map.of(), policy());

        for (int i = 0; i < 8; i++) {
            UUID player = UUID.randomUUID();
            it.cache.peek(Source.MCTIERS, player);
            it.fake.pending.completeExceptionally(new RuntimeException("down"));
            it.cache.peek(Source.MCTIERS, player);
        }
        assertEquals(Optional.empty(), it.cache.peek(Source.MCTIERS, UUID.randomUUID()));
        assertEquals(8, it.fake.calls.get());

        // /justtiers refresh is the user saying "try again now".
        it.cache.invalidateAll();
        it.cache.peek(Source.MCTIERS, UUID.randomUUID());
        assertEquals(9, it.fake.calls.get());
    }
}
