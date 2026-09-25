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
        assertEquals(0, fixture.sources.get(Source.MCTIERS).calls);
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

        fixture.sources.get(Source.MCTIERS).pending.complete(Map.of("axe", new Tier(2, true, true)));
        assertTrue(session.section(Source.MCTIERS).isEmpty());
        fixture.executor.drain();
        assertEquals(LookupSection.Status.RANKED, session.section(Source.MCTIERS).orElseThrow().status());
        assertTrue(session.section(Source.SUBTIERS).isEmpty());
        assertFalse(session.complete());
        assertFalse(session.rankedNowhere());
    }

    @Test
    void rankedNowhereRequiresEverySiteToSettleAndAtLeastOneAnswer() {
        Fixture fixture = new Fixture();
        fixture.players.online = Optional.of(PLAYER);
        LookupSession session = fixture.start(PLAYER.name());
        fixture.executor.drain();
        fixture.sources.get(Source.MCTIERS).pending.complete(Map.of());
        fixture.executor.drain();
        assertFalse(session.rankedNowhere());
        fixture.sources.get(Source.SUBTIERS).pending.completeExceptionally(new IllegalStateException("offline"));
        fixture.sources.get(Source.NOVATIERS).pending.complete(Map.of());
        fixture.executor.drain();
        assertTrue(session.complete());
        assertTrue(session.rankedNowhere());
        assertEquals(LookupSection.Status.UNAVAILABLE, session.section(Source.SUBTIERS).orElseThrow().status());
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
        fixture.sources.get(Source.MCTIERS).pending.complete(Map.of());
        fixture.sources.get(Source.SUBTIERS).pending.completeExceptionally(new IllegalStateException("offline"));
        fixture.executor.drain();
        LookupSession retry = fixture.start(PLAYER.name());
        fixture.executor.drain();
        assertEquals(1, fixture.sources.get(Source.MCTIERS).calls);
        assertEquals(2, fixture.sources.get(Source.SUBTIERS).calls);
        assertEquals(1, fixture.sources.get(Source.NOVATIERS).calls);
        fixture.sources.get(Source.SUBTIERS).pending.complete(Map.of());
        fixture.sources.get(Source.NOVATIERS).pending.complete(Map.of());
        fixture.executor.drain();
        assertTrue(first.complete());
        assertTrue(retry.complete());
        assertEquals(LookupSection.Status.UNAVAILABLE, first.section(Source.SUBTIERS).orElseThrow().status());
        assertEquals(LookupSection.Status.UNRANKED, retry.section(Source.SUBTIERS).orElseThrow().status());
    }

    private static final class Fixture {
        final QueuedExecutor executor = new QueuedExecutor();
        final Players players = new Players();
        final Map<Source, ControlledSource> sources = new EnumMap<>(Source.class);
        final TierCache cache;

        Fixture() {
            Source.ALL.forEach(source -> sources.put(source, new ControlledSource(source)));
            cache = new TierCache(sources.values().stream().map(source -> (TierSource) source).toList());
        }

        LookupSession start(String name) {
            return LookupSession.start(name, players, cache, executor);
        }
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

        ControlledSource(Source source) { this.source = source; }
        @Override public Source source() { return source; }
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
