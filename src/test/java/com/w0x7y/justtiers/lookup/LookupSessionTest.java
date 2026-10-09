package com.w0x7y.justtiers.lookup;

import com.w0x7y.justtiers.api.PlayerRef;
import com.w0x7y.justtiers.api.TierSource;
import com.w0x7y.justtiers.cache.TierCache;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;

class LookupSessionTest {
    private static final PlayerRef PLAYER = new PlayerRef("CanonicalName", UUID.randomUUID());

    @Test
    void onlineIdentityAvoidsMojangAndPublishesOnlyOnTheOwnerExecutor() {
        Fixture fixture = new Fixture();
        fixture.players.online = Optional.of(PLAYER);
        LookupSession session = fixture.start("typedName");
        assertEquals("typedName", session.name());
        assertEquals(0, fixture.sources.get(Source.PVPTIERS).calls);
        fixture.executor.drain();
        assertEquals("CanonicalName", session.name());
        assertEquals(0, fixture.players.requests);
        assertEquals(Optional.of(PLAYER), session.resolvedPlayer().toCompletableFuture().join());
        fixture.sources.values().forEach(source -> assertEquals(1, source.calls));
    }

    @Test
    void invalidUnknownAndFailedNamesStayDistinctAndNeverFetchTiers() {
        Fixture invalid = new Fixture();
        LookupSession invalidSession = invalid.start("!");
        invalid.executor.drain();
        assertEquals(Optional.of(LookupSession.Error.INVALID_NAME), invalidSession.error());
        assertEquals(0, invalid.players.requests);

        Fixture unknown = new Fixture();
        LookupSession unknownSession = unknown.start("Unknown");
        unknown.executor.drain();
        unknown.players.pending.complete(Optional.empty());
        assertTrue(unknownSession.error().isEmpty());
        unknown.executor.drain();
        assertEquals(Optional.of(LookupSession.Error.UNKNOWN_PLAYER), unknownSession.error());

        Fixture failed = new Fixture();
        LookupSession failedSession = failed.start("Unknown");
        failed.executor.drain();
        failed.players.pending.completeExceptionally(new IllegalStateException("offline"));
        failed.executor.drain();
        assertEquals(Optional.of(LookupSession.Error.NAME_UNAVAILABLE), failedSession.error());
        for (Fixture fixture : new Fixture[]{invalid, unknown, failed}) {
            fixture.sources.values().forEach(source -> assertEquals(0, source.calls));
        }
        assertEquals(Optional.empty(), failedSession.resolvedPlayer().toCompletableFuture().join());
    }

    @Test
    void mojangIdentityStartsAllSitesAndFastResultsAppearBeforeSlowResults() {
        Fixture fixture = new Fixture();
        LookupSession session = fixture.start("typedName");
        fixture.executor.drain();
        fixture.players.pending.complete(Optional.of(PLAYER));
        assertEquals("typedName", session.name());
        fixture.executor.drain();
        assertEquals(1, fixture.players.requests);
        fixture.sources.values().forEach(source -> assertEquals(1, source.calls));

        fixture.sources.get(Source.PVPTIERS).pending.complete(Map.of("axe", new Tier(2, true, true)));
        assertTrue(session.result(Source.PVPTIERS).section().isEmpty());
        fixture.executor.drain();
        assertEquals(LookupSection.Status.RANKED, session.result(Source.PVPTIERS).section().orElseThrow().status());
        assertTrue(session.result(Source.SUBTIERS).section().isEmpty());
        assertFalse(session.complete());
        assertFalse(session.rankedNowhere());
    }

    @Test
    void rankedNowhereRequiresEverySiteToSettleAndAtLeastOneAnswer() {
        Fixture fixture = new Fixture();
        fixture.players.online = Optional.of(PLAYER);
        LookupSession session = fixture.start(PLAYER.name());
        fixture.executor.drain();
        fixture.sources.get(Source.PVPTIERS).pending.complete(Map.of());
        fixture.executor.drain();
        assertFalse(session.rankedNowhere());
        fixture.sources.get(Source.SUBTIERS).pending.completeExceptionally(new IllegalStateException("offline"));
        fixture.sources.get(Source.NOVATIERS).pending.complete(Map.of());
        fixture.sources.get(Source.PVPHQ).pending.complete(Map.of());
        fixture.executor.drain();
        assertTrue(session.complete());
        assertTrue(session.rankedNowhere());
        assertEquals(LookupSection.Status.UNAVAILABLE, session.result(Source.SUBTIERS).section().orElseThrow().status());
    }

    @Test
    void allUnavailableSaysNothingAboutRankingsAndANewSessionRetriesFailures() {
        Fixture fixture = new Fixture();
        fixture.players.online = Optional.of(PLAYER);
        LookupSession first = fixture.start(PLAYER.name());
        fixture.executor.drain();
        fixture.sources.values().forEach(source -> source.pending.completeExceptionally(new IllegalStateException("offline")));
        fixture.executor.drain();
        assertTrue(first.complete());
        assertFalse(first.rankedNowhere());

        LookupSession retry = fixture.start(PLAYER.name());
        fixture.executor.drain();
        fixture.sources.values().forEach(source -> {
            assertEquals(2, source.calls);
            source.pending.complete(Map.of());
        });
        fixture.executor.drain();
        assertTrue(retry.rankedNowhere());
        assertFalse(first.rankedNowhere(), "a retry must not rewrite an older session");
    }

    @Test
    void retryKeepsSuccessfulCachedAnswersAndSharesRequestsStillInFlight() {
        Fixture fixture = new Fixture();
        fixture.players.online = Optional.of(PLAYER);
        LookupSession first = fixture.start(PLAYER.name());
        fixture.executor.drain();
        fixture.sources.get(Source.PVPTIERS).pending.complete(Map.of());
        fixture.sources.get(Source.SUBTIERS).pending.completeExceptionally(new IllegalStateException("offline"));
        fixture.executor.drain();
        LookupSession retry = fixture.start(PLAYER.name());
        fixture.executor.drain();
        assertEquals(1, fixture.sources.get(Source.PVPTIERS).calls);
        assertEquals(2, fixture.sources.get(Source.SUBTIERS).calls);
        assertEquals(1, fixture.sources.get(Source.NOVATIERS).calls);
        fixture.sources.get(Source.SUBTIERS).pending.complete(Map.of());
        fixture.sources.get(Source.NOVATIERS).pending.complete(Map.of());
        fixture.sources.get(Source.PVPHQ).pending.complete(Map.of());
        fixture.executor.drain();
        assertTrue(first.complete());
        assertTrue(retry.complete());
        assertEquals(LookupSection.Status.UNAVAILABLE, first.result(Source.SUBTIERS).section().orElseThrow().status());
        assertEquals(LookupSection.Status.UNRANKED, retry.result(Source.SUBTIERS).section().orElseThrow().status());
    }

    private static final class Fixture {
        final QueuedExecutor executor = new QueuedExecutor();
        final java.util.concurrent.atomic.AtomicLong time = new java.util.concurrent.atomic.AtomicLong();
        final Players players = new Players();
        final Map<Source, ControlledSource> sources = new EnumMap<>(Source.class);
        final TierCache cache;

        Fixture() {
            Source.ALL.forEach(source -> sources.put(source, new ControlledSource(source)));
            cache = new TierCache(sources.values().stream().map(source -> (TierSource) source).toList(),
                    com.w0x7y.justtiers.cache.CachePolicy.DEFAULT, time::get, () -> 0.5);
        }

        LookupSession start(String name) {
            return LookupSession.start(name, players, cache, executor, time::get);
        }
    }

    @Test
    void cachedPlacementsRemainVisibleDuringRefreshAndItsFailureIsExplicit() {
        Fixture fixture = new Fixture();
        fixture.players.online = Optional.of(PLAYER);
        fixture.start(PLAYER.name());
        fixture.executor.drain();
        fixture.sources.values().forEach(source -> source.pending.complete(Map.of("axe", new Tier(2, true, false))));
        fixture.executor.drain();
        fixture.cache.refreshAll();

        LookupSession refreshing = fixture.start(PLAYER.name());
        fixture.executor.drain();
        assertEquals(LookupSection.Status.RANKED, refreshing.result(Source.PVPTIERS).section().orElseThrow().status());
        assertTrue(refreshing.result(Source.PVPTIERS).freshness().orElseThrow().status() == LookupResult.RefreshStatus.REFRESHING);
        assertFalse(refreshing.complete(), "cached sections do not mean the new requests have settled");
        fixture.sources.get(Source.PVPTIERS).pending.completeExceptionally(new IllegalStateException("offline"));
        fixture.executor.drain();
        assertEquals(LookupSection.Status.RANKED, refreshing.result(Source.PVPTIERS).section().orElseThrow().status());
        assertEquals(LookupResult.RefreshStatus.REFRESH_FAILED, refreshing.result(Source.PVPTIERS).freshness().orElseThrow().status());
        assertTrue(refreshing.result(Source.PVPTIERS).complete());
        fixture.time.addAndGet(java.time.Duration.ofMinutes(2).toNanos());
        assertEquals(java.time.Duration.ofMinutes(2), refreshing.result(Source.PVPTIERS).freshness().orElseThrow().age());
    }

    @Test
    void laterSamePlacementRefreshCannotRewriteAnEarlierSessionsAgeOrFailure() {
        Fixture fixture = new Fixture();
        fixture.players.online = Optional.of(PLAYER);
        LookupSession first = fixture.start(PLAYER.name());
        fixture.executor.drain();
        Map<String, Tier> tiers = Map.of("axe", new Tier(2, true, false));
        fixture.sources.get(Source.PVPTIERS).pending.complete(tiers);
        fixture.executor.drain();
        fixture.time.addAndGet(java.time.Duration.ofMinutes(10).toNanos());
        fixture.cache.refreshAll();
        LookupSession failed = fixture.start(PLAYER.name());
        fixture.executor.drain();
        fixture.sources.get(Source.PVPTIERS).pending.completeExceptionally(new IllegalStateException("offline"));
        fixture.executor.drain();
        fixture.cache.refreshAll();
        fixture.time.addAndGet(java.time.Duration.ofMinutes(5).toNanos());
        LookupSession retry = fixture.start(PLAYER.name());
        fixture.executor.drain();
        fixture.sources.get(Source.PVPTIERS).pending.complete(tiers);
        fixture.executor.drain();

        LookupResult before = first.result(Source.PVPTIERS);
        LookupResult failure = failed.result(Source.PVPTIERS);
        LookupResult recovered = retry.result(Source.PVPTIERS);
        assertEquals(java.time.Duration.ofMinutes(15), before.freshness().orElseThrow().age());
        assertEquals(java.time.Duration.ofMinutes(15), failure.freshness().orElseThrow().age());
        assertEquals(LookupResult.RefreshStatus.REFRESH_FAILED, failure.freshness().orElseThrow().status());
        assertEquals(java.time.Duration.ZERO, recovered.freshness().orElseThrow().age());
        assertEquals(LookupResult.RefreshStatus.FRESH, recovered.freshness().orElseThrow().status());
        assertEquals(before.section(), recovered.section());
    }

    @Test
    void capturedAnswerBecomesStaleAfterItsFreshnessDeadlineEvenAfterInvalidation() {
        Fixture fixture = new Fixture();
        fixture.players.online = Optional.of(PLAYER);
        LookupSession session = fixture.start(PLAYER.name());
        fixture.executor.drain();
        fixture.sources.get(Source.PVPTIERS).pending.complete(Map.of("axe", new Tier(2, true, false)));
        fixture.executor.drain();
        fixture.cache.invalidateAll();
        fixture.time.addAndGet(java.time.Duration.ofMinutes(60).toNanos());
        LookupResult result = session.result(Source.PVPTIERS);
        assertEquals(LookupSection.Status.RANKED, result.section().orElseThrow().status());
        assertEquals(LookupResult.RefreshStatus.STALE, result.freshness().orElseThrow().status());
        assertEquals(java.time.Duration.ofMinutes(60), result.freshness().orElseThrow().age());
        assertTrue(result.complete());
    }

    @Test
    void delayedPublicationKeepsTheCompletedRequestsMetadataWithItsPlacements() {
        Fixture fixture = new Fixture();
        fixture.players.online = Optional.of(PLAYER);
        LookupSession session = fixture.start(PLAYER.name());
        fixture.executor.drain();
        fixture.sources.get(Source.PVPTIERS).pending.complete(Map.of("axe", new Tier(2, true, false)));
        fixture.time.addAndGet(java.time.Duration.ofMinutes(2).toNanos());
        fixture.cache.invalidate(Source.PVPTIERS);
        fixture.cache.load(Source.PVPTIERS, PLAYER.uuid());
        fixture.sources.get(Source.PVPTIERS).pending.complete(Map.of("axe", new Tier(1, true, false)));
        fixture.executor.drain();
        LookupResult result = session.result(Source.PVPTIERS);
        assertEquals("HT2", result.section().orElseThrow().cells().stream()
                .filter(cell -> cell.gamemode().slug().equals("axe")).findFirst().orElseThrow().tier().orElseThrow().label());
        assertEquals(java.time.Duration.ofMinutes(2), result.freshness().orElseThrow().age());
        assertTrue(result.complete());
    }

    @Test
    void liveBulkRefreshStatusCanChangeWithoutReplacingTheDisplayedAnswer() {
        Fixture fixture = new Fixture();
        fixture.players.online = Optional.of(PLAYER);
        LookupSession session = fixture.start(PLAYER.name());
        fixture.executor.drain();
        ControlledSource bulk = fixture.sources.get(Source.NOVATIERS);
        bulk.pending.complete(Map.of("axe", new Tier(2, true, false)));
        fixture.executor.drain();
        LookupResult settled = session.result(Source.NOVATIERS);
        bulk.refresh = new TierSource.RefreshState(true, false, java.time.Duration.ZERO);
        assertEquals(LookupResult.RefreshStatus.REFRESHING, session.result(Source.NOVATIERS).freshness().orElseThrow().status());
        bulk.refresh = new TierSource.RefreshState(false, true, java.time.Duration.ZERO);
        assertEquals(LookupResult.RefreshStatus.REFRESH_FAILED, session.result(Source.NOVATIERS).freshness().orElseThrow().status());
        bulk.refresh = TierSource.RefreshState.IDLE;
        assertEquals(LookupResult.RefreshStatus.FRESH, session.result(Source.NOVATIERS).freshness().orElseThrow().status());
        assertEquals(settled.section(), session.result(Source.NOVATIERS).section());
        assertTrue(session.result(Source.NOVATIERS).complete());
    }

    @Test
    void capturedAnswerWithExpiryDisabledKeepsAgingWithoutBecomingStale() {
        Fixture fixture = new Fixture();
        fixture.cache.setTtl(java.time.Duration.ZERO);
        fixture.players.online = Optional.of(PLAYER);
        LookupSession session = fixture.start(PLAYER.name());
        fixture.executor.drain();
        fixture.sources.get(Source.PVPTIERS).pending.complete(Map.of());
        fixture.executor.drain();
        fixture.cache.invalidateAll();
        fixture.time.addAndGet(java.time.Duration.ofDays(2).toNanos());
        LookupResult result = session.result(Source.PVPTIERS);
        assertEquals(LookupResult.RefreshStatus.FRESH, result.freshness().orElseThrow().status());
        assertEquals(java.time.Duration.ofDays(2), result.freshness().orElseThrow().age());
        assertEquals(LookupSection.Status.UNRANKED, result.section().orElseThrow().status());
    }

    @Test
    void completedBulkRefreshCannotLeaveAnInvalidatedResultRefreshingForever() {
        Fixture fixture = new Fixture();
        fixture.players.online = Optional.of(PLAYER);
        fixture.start(PLAYER.name());
        fixture.executor.drain();
        ControlledSource bulk = fixture.sources.get(Source.NOVATIERS);
        bulk.pending.complete(Map.of("axe", new Tier(2, true, false)));
        fixture.executor.drain();
        bulk.refresh = new TierSource.RefreshState(true, false, java.time.Duration.ZERO);
        LookupSession duringRefresh = fixture.start(PLAYER.name());
        fixture.executor.drain();
        assertEquals(LookupResult.RefreshStatus.REFRESHING, duringRefresh.result(Source.NOVATIERS).freshness().orElseThrow().status());
        assertTrue(duringRefresh.result(Source.NOVATIERS).complete());
        bulk.refresh = TierSource.RefreshState.IDLE;
        fixture.cache.invalidate(Source.NOVATIERS);
        LookupResult result = duringRefresh.result(Source.NOVATIERS);
        assertEquals(LookupResult.RefreshStatus.STALE, result.freshness().orElseThrow().status());
        assertEquals(LookupSection.Status.RANKED, result.section().orElseThrow().status());
        assertTrue(result.complete());
    }

    private static final class Players implements LookupSession.Players {
        Optional<PlayerRef> online = Optional.empty();
        final CompletableFuture<Optional<PlayerRef>> pending = new CompletableFuture<>();
        int requests;

        @Override public Optional<PlayerRef> online(String name) { return online; }
        @Override public CompletableFuture<Optional<PlayerRef>> resolve(String name) {
            requests++;
            return pending;
        }
    }

    private static final class ControlledSource implements TierSource {
        final Source source;
        CompletableFuture<Map<String, Tier>> pending;
        int calls;
        TierSource.RefreshState refresh = TierSource.RefreshState.IDLE;

        ControlledSource(Source source) { this.source = source; }
        @Override public Source source() { return source; }
        @Override public RefreshState refreshState() { return refresh; }
        @Override public CompletableFuture<Map<String, Tier>> fetch(UUID uuid) {
            assertEquals(PLAYER.uuid(), uuid);
            calls++;
            pending = new CompletableFuture<>();
            return pending;
        }
    }

    private static final class QueuedExecutor implements Executor {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        @Override public void execute(Runnable command) { tasks.add(command); }
        void drain() {
            while (!tasks.isEmpty()) tasks.remove().run();
        }
    }
}
