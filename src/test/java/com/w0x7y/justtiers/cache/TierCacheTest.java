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

/** Lookups remain nonblocking, expire and retry correctly, and obsolete work cannot change current retry state. */
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
        FakeSource fake = new FakeSource(Source.PVPTIERS, Map.of("axe", new Tier(2, true, false)));
        TierCache cache = new TierCache(List.of(fake));

        assertEquals(Optional.empty(), cache.peek(Source.PVPTIERS, PLAYER));
        assertEquals(1, fake.calls.get());

        fake.complete();
        Optional<Map<String, Tier>> loaded = cache.peek(Source.PVPTIERS, PLAYER);
        assertTrue(loaded.isPresent());
        assertEquals("HT2", loaded.get().get("axe").label());
    }

    @Test
    void repeatedPeeksIssueOnlyOneFetch() {
        FakeSource fake = new FakeSource(Source.PVPTIERS, Map.of());
        TierCache cache = new TierCache(List.of(fake));

        cache.peek(Source.PVPTIERS, PLAYER);
        cache.peek(Source.PVPTIERS, PLAYER);
        cache.peek(Source.PVPTIERS, PLAYER);

        assertEquals(1, fake.calls.get(), "in-flight requests must be coalesced");
    }

    @Test
    void unrankedResultsAreCachedAsEmptyNotRefetched() {
        FakeSource fake = new FakeSource(Source.PVPTIERS, Map.of());
        TierCache cache = new TierCache(List.of(fake));

        cache.peek(Source.PVPTIERS, PLAYER);
        fake.complete();

        Optional<Map<String, Tier>> loaded = cache.peek(Source.PVPTIERS, PLAYER);
        assertTrue(loaded.isPresent(), "a known-unranked player is loaded, not pending");
        assertTrue(loaded.get().isEmpty());

        cache.peek(Source.PVPTIERS, PLAYER);
        assertEquals(1, fake.calls.get(), "negative results must not be refetched");
    }

    @Test
    void sourcesAreCachedIndependently() {
        FakeSource mct = new FakeSource(Source.PVPTIERS, Map.of("axe", new Tier(1, true, false)));
        FakeSource sub = new FakeSource(Source.SUBTIERS, Map.of("bow", new Tier(3, false, false)));
        TierCache cache = new TierCache(List.of(mct, sub));

        cache.peek(Source.PVPTIERS, PLAYER);
        mct.complete();

        assertTrue(cache.peek(Source.PVPTIERS, PLAYER).isPresent());
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
        FakeSource fake = new FakeSource(Source.PVPTIERS, Map.of());
        TierCache cache = new TierCache(List.of(fake));

        cache.peek(Source.PVPTIERS, PLAYER);
        fake.complete();
        cache.invalidateAll();
        cache.peek(Source.PVPTIERS, PLAYER);

        assertEquals(2, fake.calls.get());
    }

    @Test
    void invalidateOnlyForcesARefetchForTheGivenSource() {
        FakeSource mct = new FakeSource(Source.PVPTIERS, Map.of());
        FakeSource sub = new FakeSource(Source.SUBTIERS, Map.of());
        TierCache cache = new TierCache(List.of(mct, sub));

        cache.peek(Source.PVPTIERS, PLAYER);
        mct.complete();
        cache.peek(Source.SUBTIERS, PLAYER);
        sub.complete();

        cache.invalidate(Source.PVPTIERS);

        cache.peek(Source.PVPTIERS, PLAYER);
        assertEquals(2, mct.calls.get(), "the invalidated source must be refetched");

        cache.peek(Source.SUBTIERS, PLAYER);
        assertEquals(1, sub.calls.get(), "the untouched source must survive");
    }

    @Test
    void loadExposesTheAwaitableFuture() throws Exception {
        FakeSource fake = new FakeSource(Source.PVPTIERS, Map.of("axe", new Tier(4, false, false)));
        TierCache cache = new TierCache(List.of(fake));

        CompletableFuture<Map<String, Tier>> future = cache.load(Source.PVPTIERS, PLAYER);
        fake.complete();
        assertEquals("LT4", future.get().get("axe").label());
    }

    @Test
    void aFailedFetchIsNotCachedAndIsRetriedOnceTheDelayHasPassed() {
        FakeSource fake = new FakeSource(Source.PVPTIERS, Map.of());
        TierCache cache = new TierCache(List.of(fake), CachePolicy.DEFAULT.withBaseRetry(Duration.ZERO));

        cache.peek(Source.PVPTIERS, PLAYER);
        fake.pending.completeExceptionally(new RuntimeException("network down"));

        assertEquals(Optional.empty(), cache.peek(Source.PVPTIERS, PLAYER),
                "a failed lookup must not be reported as loaded");
        assertEquals(Optional.empty(), cache.peek(Source.PVPTIERS, PLAYER));
        assertEquals(2, fake.calls.get(), "a failed lookup must be retried");
    }

    @Test
    void aFailedFetchIsNotRetriedWhileTheDelayIsStillRunning() {
        FakeSource fake = new FakeSource(Source.PVPTIERS, Map.of());
        TierCache cache = new TierCache(List.of(fake), CachePolicy.DEFAULT.withBaseRetry(Duration.ofMinutes(10)));

        cache.peek(Source.PVPTIERS, PLAYER);
        fake.pending.completeExceptionally(new RuntimeException("network down"));

        // peek() runs every frame, so the backoff is what stops a failing site being hammered.
        for (int i = 0; i < 50; i++) {
            assertEquals(Optional.empty(), cache.peek(Source.PVPTIERS, PLAYER));
        }
        assertEquals(1, fake.calls.get(), "the retry delay must suppress further attempts");
    }

    @Test
    void aFailedFetchIsNeverReportedAsAnUnrankedPlayer() {
        FakeSource fake = new FakeSource(Source.PVPTIERS, Map.of());
        TierCache cache = new TierCache(List.of(fake), CachePolicy.DEFAULT.withBaseRetry(Duration.ofMinutes(10)));

        cache.peek(Source.PVPTIERS, PLAYER);
        fake.pending.completeExceptionally(new RuntimeException("site down"));

        // Optional.of(Map.of()) would mean "known unranked" and would blank the badge.
        assertTrue(cache.peek(Source.PVPTIERS, PLAYER).isEmpty());
    }

    @Test
    void invalidatingClearsTheRetryDelaySoRefreshRetriesImmediately() {
        FakeSource fake = new FakeSource(Source.PVPTIERS, Map.of());
        TierCache cache = new TierCache(List.of(fake), CachePolicy.DEFAULT.withBaseRetry(Duration.ofMinutes(10)));

        cache.peek(Source.PVPTIERS, PLAYER);
        fake.pending.completeExceptionally(new RuntimeException("site down"));
        cache.peek(Source.PVPTIERS, PLAYER);
        assertEquals(1, fake.calls.get());

        cache.invalidateAll();
        cache.peek(Source.PVPTIERS, PLAYER);
        assertEquals(2, fake.calls.get(), "/justtiers refresh must not wait out the backoff");
    }

    // --- forgetFailed ---

    @Test
    void loadOnItsOwnWouldKeepAFailureForever() {
        // The behaviour forgetFailed exists to correct: nothing peeks at a player who is
        // not in the world, so a failed load would be replayed by every later lookup.
        FakeSource fake = new FakeSource(Source.PVPTIERS, Map.of());
        TierCache cache = new TierCache(List.of(fake));

        cache.load(Source.PVPTIERS, PLAYER);
        fake.pending.completeExceptionally(new RuntimeException("site down"));

        assertTrue(cache.load(Source.PVPTIERS, PLAYER).isCompletedExceptionally());
        assertEquals(1, fake.calls.get());
    }

    @Test
    void forgetFailedLetsTheNextLoadTryAgain() {
        FakeSource fake = new FakeSource(Source.PVPTIERS, Map.of("axe", new Tier(1, true, false)));
        TierCache cache = new TierCache(List.of(fake));

        cache.load(Source.PVPTIERS, PLAYER);
        fake.pending.completeExceptionally(new RuntimeException("site down"));
        cache.forgetFailed(Source.PVPTIERS, PLAYER);

        cache.load(Source.PVPTIERS, PLAYER);
        assertEquals(2, fake.calls.get());
    }

    @Test
    void forgetFailedLeavesASuccessfulEntryAlone() throws Exception {
        FakeSource fake = new FakeSource(Source.PVPTIERS, Map.of("axe", new Tier(1, true, false)));
        TierCache cache = new TierCache(List.of(fake));

        CompletableFuture<Map<String, Tier>> loaded = cache.load(Source.PVPTIERS, PLAYER);
        fake.complete();
        cache.forgetFailed(Source.PVPTIERS, PLAYER);

        assertSame(loaded, cache.load(Source.PVPTIERS, PLAYER));
        assertEquals(1, fake.calls.get());
        assertEquals("HT1", loaded.get().get("axe").label());
    }

    @Test
    void forgetFailedLeavesALookupStillInFlightAlone() {
        FakeSource fake = new FakeSource(Source.PVPTIERS, Map.of());
        TierCache cache = new TierCache(List.of(fake));

        CompletableFuture<Map<String, Tier>> inFlight = cache.load(Source.PVPTIERS, PLAYER);
        cache.forgetFailed(Source.PVPTIERS, PLAYER);

        assertSame(inFlight, cache.load(Source.PVPTIERS, PLAYER));
        assertEquals(1, fake.calls.get());
    }

    @Test
    void aSuccessfulLoadEndsTheBackoffAnEarlierFailureLeftBehind() {
        // /justtiers lookup goes through load(), which ignores the backoff. When it
        // succeeds the site has answered, so peek() must stop reporting "not yet known"
        // rather than blanking the badge for the rest of the delay.
        FakeSource fake = new FakeSource(Source.PVPTIERS, Map.of("axe", new Tier(1, true, false)));
        TierCache cache = new TierCache(List.of(fake), CachePolicy.DEFAULT.withBaseRetry(Duration.ofMinutes(10)));

        cache.peek(Source.PVPTIERS, PLAYER);
        fake.pending.completeExceptionally(new RuntimeException("site down"));
        assertEquals(Optional.empty(), cache.peek(Source.PVPTIERS, PLAYER));

        cache.load(Source.PVPTIERS, PLAYER);
        fake.complete();

        Optional<Map<String, Tier>> loaded = cache.peek(Source.PVPTIERS, PLAYER);
        assertTrue(loaded.isPresent(), "the badge must not stay blank behind a spent backoff");
        assertEquals("HT1", loaded.get().get("axe").label());
    }

    @Test
    void aFailedLoadLeavesTheBackoffInPlace() {
        // Only an answer spends the backoff; a second failure must not hand peek() a
        // free retry, or a failing site gets hammered every frame again.
        FakeSource fake = new FakeSource(Source.PVPTIERS, Map.of());
        TierCache cache = new TierCache(List.of(fake), CachePolicy.DEFAULT.withBaseRetry(Duration.ofMinutes(10)));

        cache.peek(Source.PVPTIERS, PLAYER);
        fake.pending.completeExceptionally(new RuntimeException("site down"));
        cache.peek(Source.PVPTIERS, PLAYER);

        cache.load(Source.PVPTIERS, PLAYER);
        fake.pending.completeExceptionally(new RuntimeException("still down"));
        cache.forgetFailed(Source.PVPTIERS, PLAYER);

        for (int i = 0; i < 10; i++) {
            assertEquals(Optional.empty(), cache.peek(Source.PVPTIERS, PLAYER));
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
            this.fake = new FakeSource(Source.PVPTIERS, result);
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

        it.cache.peek(Source.PVPTIERS, PLAYER);
        it.fake.complete();
        it.advance(Duration.ofMinutes(59));

        assertTrue(it.cache.peek(Source.PVPTIERS, PLAYER).isPresent());
        assertEquals(1, it.fake.calls.get());
    }

    @Test
    void anAnswerIsFetchedAgainOnceItIsStale() {
        Controlled it = new Controlled(Map.of("axe", new Tier(1, true, false)), policy());

        it.cache.peek(Source.PVPTIERS, PLAYER);
        it.fake.complete();
        it.advance(Duration.ofMinutes(60));

        assertEquals("HT1", it.cache.peek(Source.PVPTIERS, PLAYER).orElseThrow().get("axe").label(),
                "the previous answer remains visible while refreshing");
        assertEquals(2, it.fake.calls.get(), "it must be asked again");
    }

    @Test
    void anUnrankedAnswerGoesStaleTooSoATestedPlayerAppears() {
        // The whole point of the TTL: a player nobody had tested at login must not read
        // as untested all session once they have been.
        Controlled it = new Controlled(Map.of(), policy());

        it.cache.peek(Source.PVPTIERS, PLAYER);
        it.fake.complete();
        assertEquals(Optional.of(Map.of()), it.cache.peek(Source.PVPTIERS, PLAYER));

        it.advance(Duration.ofMinutes(60));
        assertEquals(Optional.of(Map.of()), it.cache.peek(Source.PVPTIERS, PLAYER));
        assertEquals(2, it.fake.calls.get());
    }

    @Test
    void aZeroTtlKeepsAnswersForTheWholeSession() {
        Controlled it = new Controlled(Map.of("axe", new Tier(1, true, false)),
                policy().withTtl(Duration.ZERO));

        it.cache.peek(Source.PVPTIERS, PLAYER);
        it.fake.complete();
        it.advance(Duration.ofDays(30));

        assertTrue(it.cache.peek(Source.PVPTIERS, PLAYER).isPresent());
        assertEquals(1, it.fake.calls.get());
    }

    @Test
    void loadAlsoRefusesToServeAStaleAnswer() {
        Controlled it = new Controlled(Map.of("axe", new Tier(1, true, false)), policy());

        it.cache.load(Source.PVPTIERS, PLAYER);
        it.fake.complete();
        it.advance(Duration.ofMinutes(60));

        it.cache.load(Source.PVPTIERS, PLAYER);
        assertEquals(2, it.fake.calls.get());
    }

    @Test
    void eachConsecutiveFailureWaitsLongerThanTheLast() {
        Controlled it = new Controlled(Map.of(), policy());

        // First failure: a minute.
        it.cache.peek(Source.PVPTIERS, PLAYER);
        it.fake.pending.completeExceptionally(new RuntimeException("down"));
        it.cache.peek(Source.PVPTIERS, PLAYER);

        it.advance(Duration.ofSeconds(59));
        it.cache.peek(Source.PVPTIERS, PLAYER);
        assertEquals(1, it.fake.calls.get(), "a minute has not passed");

        it.advance(Duration.ofSeconds(1));
        it.cache.peek(Source.PVPTIERS, PLAYER);
        assertEquals(2, it.fake.calls.get());

        // Second failure: two minutes, so the minute that sufficed before does not.
        it.fake.pending.completeExceptionally(new RuntimeException("still down"));
        it.cache.peek(Source.PVPTIERS, PLAYER);
        it.advance(Duration.ofSeconds(60));
        it.cache.peek(Source.PVPTIERS, PLAYER);
        assertEquals(2, it.fake.calls.get(), "the wait must have grown");

        it.advance(Duration.ofSeconds(60));
        it.cache.peek(Source.PVPTIERS, PLAYER);
        assertEquals(3, it.fake.calls.get());
    }

    @Test
    void aSuccessResetsTheGrowthSoTheNextFailureWaitsAMinuteAgain() {
        Controlled it = new Controlled(Map.of("axe", new Tier(1, true, false)), policy());

        it.cache.peek(Source.PVPTIERS, PLAYER);
        it.fake.pending.completeExceptionally(new RuntimeException("down"));
        it.cache.peek(Source.PVPTIERS, PLAYER);
        it.advance(Duration.ofSeconds(60));
        it.cache.peek(Source.PVPTIERS, PLAYER);
        it.fake.complete();
        it.advance(Duration.ofMinutes(60));

        // Stale, so it is asked again, and this time it fails.
        it.cache.peek(Source.PVPTIERS, PLAYER);
        it.fake.pending.completeExceptionally(new RuntimeException("down again"));
        it.cache.peek(Source.PVPTIERS, PLAYER);

        int before = it.fake.calls.get();
        it.advance(Duration.ofSeconds(60));
        it.cache.peek(Source.PVPTIERS, PLAYER);
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
            it.cache.peek(Source.PVPTIERS, player);
            it.fake.pending.completeExceptionally(new RuntimeException("down"));
            it.cache.peek(Source.PVPTIERS, player);
        }
        assertEquals(8, it.fake.calls.get());

        // A ninth player, never seen before, is not asked either: the site is closed.
        assertEquals(Optional.empty(), it.cache.peek(Source.PVPTIERS, UUID.randomUUID()));
        assertEquals(8, it.fake.calls.get(), "a closed site must not be asked");
    }

    @Test
    void aClosedSiteFailsALoadImmediatelyRatherThanAskingIt() {
        Controlled it = new Controlled(Map.of(), policy());

        for (int i = 0; i < 8; i++) {
            UUID player = UUID.randomUUID();
            it.cache.peek(Source.PVPTIERS, player);
            it.fake.pending.completeExceptionally(new RuntimeException("down"));
            it.cache.peek(Source.PVPTIERS, player);
        }

        // A lobby of two hundred players must not wave them all past the gate.
        CompletableFuture<Map<String, Tier>> future = it.cache.load(Source.PVPTIERS, UUID.randomUUID());
        assertTrue(future.isCompletedExceptionally());
        assertEquals(8, it.fake.calls.get());
    }

    @Test
    void aClosedSiteReopensWithOneProbe() {
        Controlled it = new Controlled(Map.of("axe", new Tier(1, true, false)), policy());

        for (int i = 0; i < 8; i++) {
            UUID player = UUID.randomUUID();
            it.cache.peek(Source.PVPTIERS, player);
            it.fake.pending.completeExceptionally(new RuntimeException("down"));
            it.cache.peek(Source.PVPTIERS, player);
        }
        it.advance(Duration.ofSeconds(30));

        // Exactly one request goes out, however many players are on screen.
        for (int i = 0; i < 20; i++) {
            it.cache.peek(Source.PVPTIERS, UUID.randomUUID());
        }
        assertEquals(9, it.fake.calls.get(), "the pause must end with a single probe");

        // It answers, so the site is open for business again.
        it.fake.complete();
        it.cache.peek(Source.PVPTIERS, UUID.randomUUID());
        assertEquals(10, it.fake.calls.get());
    }

    @Test
    void refreshingReopensAClosedSite() {
        Controlled it = new Controlled(Map.of(), policy());

        for (int i = 0; i < 8; i++) {
            UUID player = UUID.randomUUID();
            it.cache.peek(Source.PVPTIERS, player);
            it.fake.pending.completeExceptionally(new RuntimeException("down"));
            it.cache.peek(Source.PVPTIERS, player);
        }
        assertEquals(Optional.empty(), it.cache.peek(Source.PVPTIERS, UUID.randomUUID()));
        assertEquals(8, it.fake.calls.get());

        // /justtiers refresh is the user saying "try again now".
        it.cache.invalidateAll();
        it.cache.peek(Source.PVPTIERS, UUID.randomUUID());
        assertEquals(9, it.fake.calls.get());
    }

    @Test
    void changingTheTtlKeepsWhatIsAlreadyCached() {
        // The setting is a slider; nudging it must not blank every badge on screen.
        Controlled it = new Controlled(Map.of("axe", new Tier(1, true, false)), policy());

        it.cache.peek(Source.PVPTIERS, PLAYER);
        it.fake.complete();
        assertTrue(it.cache.peek(Source.PVPTIERS, PLAYER).isPresent());

        it.cache.setTtl(Duration.ofMinutes(120));
        assertTrue(it.cache.peek(Source.PVPTIERS, PLAYER).isPresent(),
                "the answer was still fresh and must have survived");
        assertEquals(1, it.fake.calls.get());
    }

    @Test
    void aLongerTtlKeepsAnAnswerThatWouldHaveGoneStale() {
        Controlled it = new Controlled(Map.of("axe", new Tier(1, true, false)), policy());

        it.cache.peek(Source.PVPTIERS, PLAYER);
        it.fake.complete();
        it.cache.setTtl(Duration.ofMinutes(120));
        it.advance(Duration.ofMinutes(90));

        assertTrue(it.cache.peek(Source.PVPTIERS, PLAYER).isPresent());
        assertEquals(1, it.fake.calls.get());
    }

    @Test
    void aShorterTtlCanMakeACachedAnswerStaleAtOnce() {
        Controlled it = new Controlled(Map.of("axe", new Tier(1, true, false)), policy());

        it.cache.peek(Source.PVPTIERS, PLAYER);
        it.fake.complete();
        it.advance(Duration.ofMinutes(30));
        it.cache.setTtl(Duration.ofMinutes(10));

        assertTrue(it.cache.peek(Source.PVPTIERS, PLAYER).isPresent());
        assertEquals(2, it.fake.calls.get());
    }

    @Test
    void failedRefreshRetainsTheLastSuccessfulBadgeUntilTheGracePeriodEnds() {
        Controlled it = new Controlled(Map.of("axe", new Tier(1, true, false)), policy());
        it.cache.peek(Source.PVPTIERS, PLAYER);
        it.fake.complete();
        it.advance(Duration.ofMinutes(60));
        assertTrue(it.cache.peek(Source.PVPTIERS, PLAYER).isPresent());
        it.fake.pending.completeExceptionally(new RuntimeException("offline"));
        assertEquals("HT1", it.cache.peek(Source.PVPTIERS, PLAYER).orElseThrow().get("axe").label());
        it.advance(Duration.ofHours(6));
        assertTrue(it.cache.peek(Source.PVPTIERS, PLAYER).isEmpty(), "old data has a finite lifetime");
    }

    @Test
    void replacementAnswerCanRemoveAnOldBadgeByReportingUnranked() {
        Controlled it = new Controlled(Map.of("axe", new Tier(1, true, false)), policy());
        it.cache.peek(Source.PVPTIERS, PLAYER);
        it.fake.complete();
        it.advance(Duration.ofMinutes(60));
        assertTrue(it.cache.peek(Source.PVPTIERS, PLAYER).isPresent());
        it.fake.pending.complete(Map.of());
        assertEquals(Optional.of(Map.of()), it.cache.peek(Source.PVPTIERS, PLAYER));
    }

    @Test
    void lobbyRequestsAreBoundedAndExplicitLookupPromotesQueuedWork() {
        MultiSource source = new MultiSource();
        TierCache cache = new TierCache(List.of(source));
        UUID[] players = java.util.stream.IntStream.range(0, 8).mapToObj(i -> UUID.randomUUID()).toArray(UUID[]::new);
        for (UUID player : players) cache.peek(Source.PVPTIERS, player);
        assertEquals(4, source.started.size(), "only four requests may be active per site");
        var explicit = cache.load(Source.PVPTIERS, players[7]);
        source.pending.get(players[0]).complete(Map.of());
        assertEquals(players[7], source.started.get(4), "an explicit lookup overtakes background work");
        source.pending.get(players[7]).complete(Map.of("axe", new Tier(2, true, false)));
        assertEquals("HT2", explicit.join().get("axe").label());
        assertEquals(1, source.started.stream().filter(players[7]::equals).count(), "promotion shares one request");
    }

    @Test
    void repeatedExplicitLookupsShareAndPromoteOneQueuedClaim() {
        MultiSource source = new MultiSource();
        TierCache cache = new TierCache(List.of(source));
        UUID[] players = java.util.stream.IntStream.range(0, 8)
                .mapToObj(i -> UUID.randomUUID()).toArray(UUID[]::new);
        for (UUID player : players) cache.peek(Source.PVPTIERS, player);

        var first = cache.load(Source.PVPTIERS, players[7]);
        var repeated = cache.load(Source.PVPTIERS, players[7]);
        cache.peek(Source.PVPTIERS, players[7]);

        assertFalse(first.isDone());
        assertFalse(repeated.isDone());
        assertEquals(4, cache.queuedRequests(Source.PVPTIERS), "promotion never inserts another request");
        source.pending.get(players[0]).complete(Map.of());
        assertEquals(players[7], source.started.get(4));
        source.pending.get(players[7]).complete(Map.of("axe", new Tier(2, true, false)));
        assertEquals("HT2", repeated.join().get("axe").label());
        assertEquals(1, source.started.stream().filter(players[7]::equals).count());
    }

    @Test
    void sourceInvocationAllowsAnotherThreadToShareTheClaim() {
        var cacheReference = new java.util.concurrent.atomic.AtomicReference<TierCache>();
        var shared = new java.util.concurrent.atomic.AtomicReference<CompletableFuture<Map<String, Tier>>>();
        var response = new CompletableFuture<Map<String, Tier>>();
        TierSource source = new TierSource() {
            public Source source() { return Source.PVPTIERS; }
            public CompletableFuture<Map<String, Tier>> fetch(UUID uuid) {
                try {
                    shared.set(CompletableFuture.supplyAsync(() -> cacheReference.get().load(source(), uuid),
                            action -> Thread.ofVirtual().start(action)).get(5, java.util.concurrent.TimeUnit.SECONDS));
                } catch (Exception error) {
                    throw new AssertionError("source invocation must release the admission lock", error);
                }
                return response;
            }
        };
        TierCache cache = new TierCache(List.of(source));
        cacheReference.set(cache);
        var requested = cache.load(Source.PVPTIERS, PLAYER);
        assertNotNull(shared.get());
        assertFalse(shared.get().isDone());
        response.complete(Map.of());
        assertEquals(Map.of(), requested.join());
        assertEquals(Map.of(), shared.get().join());
    }

    @Test
    void completedLookupCallbacksObserveTheReleasedActiveSlot() {
        MultiSource source = new MultiSource();
        TierCache cache = new TierCache(List.of(source));
        var first = cache.load(Source.PVPTIERS, PLAYER);
        for (int i = 0; i < 3; i++) cache.load(Source.PVPTIERS, UUID.randomUUID());
        UUID replacement = UUID.randomUUID();
        AtomicInteger observedActive = new AtomicInteger(-1);
        var startedDuringCallback = new java.util.concurrent.atomic.AtomicBoolean();
        first.whenComplete((answer, error) -> {
            observedActive.set(cache.activeRequests(Source.PVPTIERS));
            cache.load(Source.PVPTIERS, replacement);
            startedDuringCallback.set(source.started.contains(replacement));
        });

        source.pending.get(PLAYER).complete(Map.of());

        assertEquals(3, observedActive.get(), "the completed request releases capacity before publication");
        assertTrue(startedDuringCallback.get(), "a continuation can immediately use the released slot");
        assertEquals(4, cache.activeRequests(Source.PVPTIERS));
    }

    @Test
    void cancelledRequestCallbacksCanAdmitWorkInTheNewGeneration() {
        MultiSource source = new MultiSource();
        TierCache cache = new TierCache(List.of(source));
        for (int i = 0; i < 4; i++) cache.peek(Source.PVPTIERS, UUID.randomUUID());
        var obsolete = cache.load(Source.PVPTIERS, UUID.randomUUID());
        var replacement = new java.util.concurrent.atomic.AtomicReference<CompletableFuture<Map<String, Tier>>>();
        obsolete.whenComplete((answer, error) -> replacement.set(cache.load(Source.PVPTIERS, PLAYER)));

        cache.invalidate(Source.PVPTIERS);

        assertTrue(obsolete.isCompletedExceptionally());
        assertNotNull(replacement.get());
        assertFalse(replacement.get().isDone());
        assertEquals(1, cache.queuedRequests(Source.PVPTIERS));
        source.pending.get(source.started.getFirst()).complete(Map.of());
        assertEquals(PLAYER, source.started.getLast());
        source.pending.get(PLAYER).complete(Map.of());
        assertEquals(Map.of(), replacement.get().join());
    }

    @Test
    void retainingAnswersDoesNotObserveTheSourceWhileHoldingTheAdmissionLock() {
        var cacheReference = new java.util.concurrent.atomic.AtomicReference<TierCache>();
        var observeAdmission = new java.util.concurrent.atomic.AtomicBoolean();
        TierSource source = new TierSource() {
            public Source source() { return Source.PVPTIERS; }
            public CompletableFuture<Map<String, Tier>> fetch(UUID uuid) {
                return CompletableFuture.completedFuture(Map.of("axe", new Tier(1, true, false)));
            }
            public RefreshState refreshState() {
                if (observeAdmission.getAndSet(false)) {
                    try {
                        CompletableFuture.runAsync(() -> cacheReference.get().load(source(), UUID.randomUUID()),
                                action -> Thread.ofVirtual().start(action)).get(5, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (Exception error) {
                        throw new AssertionError("source observation must not hold the admission lock", error);
                    }
                }
                return RefreshState.IDLE;
            }
        };
        TierCache cache = new TierCache(List.of(source));
        cacheReference.set(cache);
        cache.load(Source.PVPTIERS, PLAYER).join();
        observeAdmission.set(true);

        assertDoesNotThrow(cache::refreshAll);
        assertTrue(cache.cachedAnswer(Source.PVPTIERS, PLAYER).orElseThrow().stale());
    }

    @Test
    void loadAnswerCapturesTheCompletedGenerationRatherThanReadingAReplacement() {
        Controlled it = new Controlled(Map.of("axe", new Tier(1, true, false)), policy());
        var oldRequest = it.cache.loadAnswer(Source.PVPTIERS, PLAYER);
        it.fake.complete();
        TierCache.CachedAnswer captured = oldRequest.join();
        it.advance(Duration.ofMinutes(10));
        it.cache.invalidate(Source.PVPTIERS);
        it.cache.load(Source.PVPTIERS, PLAYER);
        it.fake.complete();

        assertEquals(Duration.ZERO, captured.age());
        assertEquals(Optional.of(Duration.ofMinutes(60)), captured.freshFor());
        assertFalse(captured.stale());
        assertEquals(captured, oldRequest.join(), "the completed request remains its own immutable answer");
        assertEquals(Duration.ZERO, it.cache.cachedAnswer(Source.PVPTIERS, PLAYER).orElseThrow().age());
    }

    @Test
    void cachedLoadAnswerAgesWithoutFetchingThePlayerAgain() {
        Controlled it = new Controlled(Map.of("axe", new Tier(1, true, false)), policy());
        it.cache.loadAnswer(Source.PVPTIERS, PLAYER);
        it.fake.complete();
        it.advance(Duration.ofMinutes(10));

        TierCache.CachedAnswer answer = it.cache.loadAnswer(Source.PVPTIERS, PLAYER).join();

        assertEquals(Duration.ofMinutes(10), answer.age());
        assertEquals(Optional.of(Duration.ofMinutes(50)), answer.freshFor());
        assertEquals(1, it.fake.calls.get());
    }

    @Test
    void identicalAnswersAtTheSameTimeStillHaveDifferentRevisions() {
        Controlled it = new Controlled(Map.of("axe", new Tier(1, true, false)), policy());
        it.cache.load(Source.PVPTIERS, PLAYER);
        it.fake.complete();
        long first = it.cache.cachedAnswer(Source.PVPTIERS, PLAYER).orElseThrow().revision();

        it.cache.refreshAll();
        assertEquals(first, it.cache.cachedAnswer(Source.PVPTIERS, PLAYER).orElseThrow().revision(),
                "retained placements keep the original response's revision");
        it.cache.load(Source.PVPTIERS, PLAYER);
        it.fake.complete();

        assertNotEquals(first, it.cache.cachedAnswer(Source.PVPTIERS, PLAYER).orElseThrow().revision(),
                "another response has a new revision even if its placements and timestamp match");
    }

    @Test
    void completedEntriesCannotAccumulateAcrossThousandsOfDepartedPlayers() {
        TierSource immediate = new TierSource() {
            public Source source() { return Source.PVPTIERS; }
            public CompletableFuture<Map<String, Tier>> fetch(UUID uuid) {
                return CompletableFuture.completedFuture(Map.of());
            }
        };
        TierCache cache = new TierCache(List.of(immediate));
        for (int i = 0; i < 4200; i++) cache.load(Source.PVPTIERS, UUID.randomUUID()).join();
        assertTrue(cache.cachedPlayers(Source.PVPTIERS) <= 4096, "retention is bounded even without another peek");
    }

    private static final class MultiSource implements TierSource {
        final java.util.List<UUID> started = new java.util.ArrayList<>();
        final java.util.Map<UUID, CompletableFuture<Map<String, Tier>>> pending = new java.util.HashMap<>();
        public Source source() { return Source.PVPTIERS; }
        public CompletableFuture<Map<String, Tier>> fetch(UUID uuid) {
            started.add(uuid);
            var result = new CompletableFuture<Map<String, Tier>>();
            pending.put(uuid, result);
            return result;
        }
    }

    @Test
    void refreshPreservesAnswersWhileDiscardingObsoleteCompletions() {
        Controlled it = new Controlled(Map.of("axe", new Tier(1, true, false)), policy());
        it.cache.load(Source.PVPTIERS, PLAYER);
        it.fake.complete();
        it.advance(Duration.ofMinutes(60));
        it.cache.peek(Source.PVPTIERS, PLAYER);
        var obsolete = it.fake.pending;
        it.cache.refreshAll();
        assertEquals("HT1", it.cache.peek(Source.PVPTIERS, PLAYER).orElseThrow().get("axe").label());
        obsolete.complete(Map.of("axe", new Tier(5, false, false)));
        assertEquals("HT1", it.cache.peek(Source.PVPTIERS, PLAYER).orElseThrow().get("axe").label());
        it.fake.pending.complete(Map.of("axe", new Tier(2, true, false)));
        assertEquals("HT2", it.cache.peek(Source.PVPTIERS, PLAYER).orElseThrow().get("axe").label());
    }

    @Test
    void maintenanceReclaimsExpiredPlayersWithoutAnotherLookup() {
        Controlled it = new Controlled(Map.of("axe", new Tier(1, true, false)), policy());
        it.cache.load(Source.PVPTIERS, PLAYER);
        it.fake.complete();
        it.advance(Duration.ofHours(8));
        it.cache.maintain();
        assertEquals(0, it.cache.cachedPlayers(Source.PVPTIERS));
    }

    @Test
    void checkingAnOldBulkSnapshotDoesNotResetItsAgeOrKeepItAliveIndefinitely() {
        AtomicLong time = new AtomicLong();
        AtomicInteger fetches = new AtomicInteger();
        TierSource source = new TierSource() {
            public Source source() { return Source.NOVATIERS; }
            public CompletableFuture<Map<String, Tier>> fetch(UUID uuid) {
                return CompletableFuture.completedFuture(Map.of("vanilla", new Tier(1, true, false)));
            }
            public CompletableFuture<TierSource.Answer> fetchAnswer(UUID uuid) {
                fetches.incrementAndGet();
                return fetch(uuid).thenApply(tiers -> new TierSource.Answer(tiers, Duration.ofNanos(time.get()), time.get() > 0));
            }
        };
        TierCache cache = new TierCache(List.of(source), policy(), time::get, () -> 0.5);
        cache.load(Source.NOVATIERS, PLAYER).join();
        time.set(Duration.ofMinutes(61).toNanos());
        for (int i = 0; i < 20; i++) assertTrue(cache.peek(Source.NOVATIERS, PLAYER).isPresent());
        assertEquals(Duration.ofMinutes(61), cache.cachedAnswer(Source.NOVATIERS, PLAYER).orElseThrow().age());
        assertTrue(cache.cachedAnswer(Source.NOVATIERS, PLAYER).orElseThrow().refreshFailed());
        assertEquals(2, fetches.get(), "checking an old snapshot is not repeated on every frame");
        time.set(Duration.ofHours(7).plusSeconds(1).toNanos());
        assertTrue(cache.peek(Source.NOVATIERS, PLAYER).isEmpty());
        cache.forgetFailed(Source.NOVATIERS, PLAYER);
        assertTrue(cache.load(Source.NOVATIERS, PLAYER).isCompletedExceptionally(), "explicit lookup cannot present expired bulk data as fresh");
    }

    @Test
    void rateLimitCooldownAppliesToEveryPlayerAndSurvivesManualRefresh() {
        MultiSource source = new MultiSource();
        AtomicLong time = new AtomicLong();
        TierCache cache = new TierCache(List.of(source), policy(), time::get, () -> 0.5);
        UUID limited = UUID.randomUUID();
        cache.load(Source.PVPTIERS, limited);
        source.pending.get(limited).completeExceptionally(new com.w0x7y.justtiers.api.RetryAfterException(
                "rate limited", Duration.ofMinutes(2)));
        cache.refreshAll();
        assertTrue(cache.load(Source.PVPTIERS, UUID.randomUUID()).isCompletedExceptionally());
        assertEquals(1, source.started.size());
        time.set(Duration.ofMinutes(2).toNanos());
        cache.load(Source.PVPTIERS, UUID.randomUUID());
        assertEquals(2, source.started.size());
    }

    @Test
    void queueOverflowStaysBoundedAndExplicitWorkCanDisplaceBackgroundWork() {
        MultiSource source = new MultiSource();
        TierCache cache = new TierCache(List.of(source));
        for (int i = 0; i < 200; i++) cache.peek(Source.PVPTIERS, UUID.randomUUID());
        assertEquals(4, source.started.size());
        assertEquals(128, cache.queuedRequests(Source.PVPTIERS));
        UUID requested = UUID.randomUUID();
        var explicit = cache.load(Source.PVPTIERS, requested);
        assertFalse(explicit.isDone());
        source.pending.get(source.started.getFirst()).complete(Map.of());
        assertEquals(requested, source.started.getLast());
        assertTrue(cache.queuedRequests(Source.PVPTIERS) <= 128);
        assertEquals(0, cache.health(Source.PVPTIERS).failures(), "queue pressure is not a site failure");
    }

    @Test
    void invalidationCancelsQueuedWorkWithoutResettingActiveRequestCapacity() {
        MultiSource source = new MultiSource();
        TierCache cache = new TierCache(List.of(source));
        for (int i = 0; i < 8; i++) cache.peek(Source.PVPTIERS, UUID.randomUUID());
        UUID oldActive = source.started.getFirst();
        cache.invalidate(Source.PVPTIERS);
        cache.load(Source.PVPTIERS, PLAYER);
        assertEquals(4, source.started.size(), "old active HTTP requests still occupy slots");
        source.pending.get(oldActive).complete(Map.of());
        assertEquals(PLAYER, source.started.getLast());
        assertEquals(5, source.started.size(), "obsolete queued players are never fetched");
    }

    // --- what /justtiers debug reads ---

    @Test
    void aSuccessIsTimedFromWhenTheRequestWentOut() {
        Controlled it = new Controlled(Map.of("axe", new Tier(1, true, false)), policy());

        it.cache.peek(Source.PVPTIERS, PLAYER);
        it.advance(Duration.ofMillis(250));
        it.fake.complete();

        SiteHealth.Snapshot health = it.cache.health(Source.PVPTIERS);
        assertEquals(1, health.successes());
        assertEquals(0, health.failures());
        assertEquals(Duration.ofMillis(250).toNanos(), health.lastLatencyNanos().getAsLong());
        assertEquals(0, health.sinceLastSuccessNanos().getAsLong());
    }

    @Test
    void aFailureIsTimedAndItsReasonKept() {
        Controlled it = new Controlled(Map.of(), policy());

        it.cache.peek(Source.PVPTIERS, PLAYER);
        it.advance(Duration.ofSeconds(10));
        it.fake.pending.completeExceptionally(new RuntimeException("site down"));

        SiteHealth.Snapshot health = it.cache.health(Source.PVPTIERS);
        assertEquals(1, health.failures());
        // A site that timed out and one that refused the connection look identical
        // without this.
        assertEquals(Duration.ofSeconds(10).toNanos(), health.lastLatencyNanos().getAsLong());
        assertEquals("RuntimeException: site down", health.lastError().orElseThrow());
    }

    @Test
    void aSiteNobodyHasAskedReportsNothingRatherThanThrowing() {
        // Constructed with no sources at all: every accessor still has to answer.
        TierCache cache = new TierCache(List.of());

        assertTrue(cache.health(Source.NOVATIERS).idle());
        assertFalse(cache.gateStatus(Source.NOVATIERS).closed());
        assertEquals(0, cache.cachedPlayers(Source.NOVATIERS));
        assertEquals(0, cache.pendingLookups(Source.NOVATIERS));
        assertEquals(0, cache.playersAwaitingRetry(Source.NOVATIERS));
    }

    @Test
    void inFlightLookupsAreCountedSeparatelyFromSettledOnes() {
        Controlled it = new Controlled(Map.of("axe", new Tier(1, true, false)), policy());

        it.cache.peek(Source.PVPTIERS, PLAYER);
        assertEquals(1, it.cache.cachedPlayers(Source.PVPTIERS));
        assertEquals(1, it.cache.pendingLookups(Source.PVPTIERS));

        it.fake.complete();
        assertEquals(1, it.cache.cachedPlayers(Source.PVPTIERS));
        assertEquals(0, it.cache.pendingLookups(Source.PVPTIERS));
    }

    @Test
    void playersWaitingOutARetryAreCountedWhileTheyWait() {
        Controlled it = new Controlled(Map.of(), policy());

        it.cache.peek(Source.PVPTIERS, PLAYER);
        it.fake.pending.completeExceptionally(new RuntimeException("site down"));
        it.cache.peek(Source.PVPTIERS, PLAYER);

        assertEquals(1, it.cache.playersAwaitingRetry(Source.PVPTIERS));

        // Once the delay is up the player is free to be tried again, and stops counting.
        it.advance(Duration.ofMinutes(5));
        assertEquals(0, it.cache.playersAwaitingRetry(Source.PVPTIERS));
    }

    @Test
    void theGateIsVisibleOnceItHasGivenUpOnASite() {
        Controlled it = new Controlled(Map.of(), policy());

        for (int i = 0; i < CachePolicy.DEFAULT.siteFailureThreshold(); i++) {
            UUID player = UUID.randomUUID();
            it.cache.peek(Source.PVPTIERS, player);
            it.fake.pending.completeExceptionally(new RuntimeException("site down"));
        }

        SiteGate.Status gate = it.cache.gateStatus(Source.PVPTIERS);
        assertTrue(gate.closed(), "the report has to be able to say why nothing is being asked");
        assertEquals(CachePolicy.DEFAULT.basePause().toNanos(), gate.reopensInNanos());
    }

    @Test
    void refreshingReopensTheGateWithoutRewritingHistory() {
        Controlled it = new Controlled(Map.of(), policy());

        it.cache.peek(Source.PVPTIERS, PLAYER);
        it.fake.pending.completeExceptionally(new RuntimeException("site down"));
        it.cache.invalidate(Source.PVPTIERS);

        assertEquals(0, it.cache.gateStatus(Source.PVPTIERS).consecutiveFailures());
        // The failures leading up to a refresh are usually the interesting half of a
        // bug report, so clearing the cache must not clear them.
        assertEquals(1, it.cache.health(Source.PVPTIERS).failures());
        assertEquals("RuntimeException: site down",
                it.cache.health(Source.PVPTIERS).lastError().orElseThrow());
    }
    @Test
    void aFailureBetweenCompletionChecksNeverEscapesPeek() {
        class RacingFuture extends CompletableFuture<Map<String, Tier>> {
            boolean armed;
            @Override public boolean isDone() {
                if (armed) {
                    armed = false;
                    completeExceptionally(new RuntimeException("HTTP timeout"));
                }
                return super.isDone();
            }
        }
        RacingFuture response = new RacingFuture();
        TierSource source = new TierSource() {
            public Source source() { return Source.PVPTIERS; }
            public CompletableFuture<Map<String, Tier>> fetch(UUID uuid) { return response; }
        };
        TierCache cache = new TierCache(List.of(source));
        cache.load(Source.PVPTIERS, PLAYER);
        response.armed = true;
        assertTrue(assertDoesNotThrow(() -> cache.peek(Source.PVPTIERS, PLAYER)).isEmpty());
    }

    @Test
    void discardedFailuresCannotCloseARefreshedGate() {
        FakeSource fake = new FakeSource(Source.PVPTIERS, Map.of());
        TierCache cache = new TierCache(List.of(fake));
        var discarded = new java.util.ArrayList<CompletableFuture<Map<String, Tier>>>();
        for (int i = 0; i < 4; i++) {
            cache.load(Source.PVPTIERS, UUID.randomUUID());
            discarded.add(fake.pending);
        }
        cache.invalidateAll();
        discarded.forEach(future -> future.completeExceptionally(new RuntimeException("old failure")));
        assertFalse(cache.gateStatus(Source.PVPTIERS).closed());
        assertEquals(0, cache.playersAwaitingRetry(Source.PVPTIERS));
        assertEquals(4, cache.health(Source.PVPTIERS).failures(), "history still records old requests");
    }

    @Test
    void aDiscardedSuccessCannotEraseTheNewRequestsBackoff() {
        FakeSource fake = new FakeSource(Source.PVPTIERS, Map.of());
        TierCache cache = new TierCache(List.of(fake));
        cache.load(Source.PVPTIERS, PLAYER);
        var discarded = fake.pending;
        cache.invalidate(Source.PVPTIERS);
        cache.load(Source.PVPTIERS, PLAYER);
        fake.pending.completeExceptionally(new RuntimeException("new failure"));
        discarded.complete(Map.of());
        assertEquals(1, cache.playersAwaitingRetry(Source.PVPTIERS));
        assertEquals(1, cache.gateStatus(Source.PVPTIERS).consecutiveFailures());
    }

}
