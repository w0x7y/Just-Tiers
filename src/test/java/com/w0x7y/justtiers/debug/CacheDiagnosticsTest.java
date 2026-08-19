package com.w0x7y.justtiers.debug;

import com.w0x7y.justtiers.api.TierSource;
import com.w0x7y.justtiers.cache.CachePolicy;
import com.w0x7y.justtiers.cache.TierCache;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * That every number lands in the field it belongs to.
 *
 * <p>Worth its own test because {@link SiteDiagnostics} ends in three adjacent
 * {@code int} components — cached, in flight, retrying. Swap two of them and the compiler
 * is happy, every cache test still passes, and {@code /justtiers debug} quietly reports
 * the wrong thing in exactly the situation someone is running it to understand.
 */
class CacheDiagnosticsTest {

    /** A source whose futures are completed, or failed, on demand. */
    private static final class FakeSource implements TierSource {
        private final Source source;
        CompletableFuture<Map<String, Tier>> pending;

        FakeSource(Source source) {
            this.source = source;
        }

        @Override public Source source() {
            return source;
        }

        @Override public CompletableFuture<Map<String, Tier>> fetch(UUID uuid) {
            pending = new CompletableFuture<>();
            return pending;
        }
    }

    private static List<FakeSource> sources() {
        return Source.ALL.stream().map(FakeSource::new).toList();
    }

    private static TierCache cache(List<FakeSource> sources) {
        return new TierCache(List.copyOf(sources), CachePolicy.DEFAULT);
    }

    @Test
    void everySiteGetsARowInDeclarationOrder() {
        List<SiteDiagnostics> rows = CacheDiagnostics.of(cache(sources()));

        assertEquals(Source.ALL.size(), rows.size());
        for (int i = 0; i < Source.ALL.size(); i++) {
            assertEquals(Source.ALL.get(i), rows.get(i).source());
        }
    }

    @Test
    void anUntouchedCacheReportsNothingRatherThanNulls() {
        for (SiteDiagnostics row : CacheDiagnostics.of(cache(sources()))) {
            assertNotNull(row.health(), row.source().toString());
            assertNotNull(row.gate(), row.source().toString());
            assertEquals(0, row.cachedPlayers());
            assertEquals(0, row.pendingLookups());
            assertEquals(0, row.playersAwaitingRetry());
        }
    }

    /**
     * The transposition guard. Three players on one site, in three different states, so
     * cached, in flight and retrying are three <em>different</em> numbers — two equal
     * counts would let a swap through unnoticed.
     */
    @Test
    void cachedInFlightAndRetryingAreToldApart() {
        List<FakeSource> sources = sources();
        FakeSource site = sources.getFirst();
        TierCache cache = cache(sources);

        // One settled answer.
        cache.peek(site.source(), UUID.randomUUID());
        site.pending.complete(Map.of("axe", new Tier(2, true, false)));

        // One failure, which schedules a retry the player is now waiting out.
        cache.peek(site.source(), UUID.randomUUID());
        site.pending.completeExceptionally(new RuntimeException("nope"));

        // Two still on the wire.
        cache.peek(site.source(), UUID.randomUUID());
        cache.peek(site.source(), UUID.randomUUID());

        SiteDiagnostics row = CacheDiagnostics.of(cache, site.source());
        assertEquals(4, row.cachedPlayers(), "cachedPlayers");
        assertEquals(2, row.pendingLookups(), "pendingLookups");
        assertEquals(1, row.playersAwaitingRetry(), "playersAwaitingRetry");
    }

    @Test
    void oneSitesNumbersDoNotLeakIntoAnothers() {
        List<FakeSource> sources = sources();
        TierCache cache = cache(sources);
        FakeSource busy = sources.getFirst();
        FakeSource quiet = sources.getLast();

        cache.peek(busy.source(), UUID.randomUUID());
        cache.peek(busy.source(), UUID.randomUUID());

        assertEquals(2, CacheDiagnostics.of(cache, busy.source()).cachedPlayers());
        assertEquals(0, CacheDiagnostics.of(cache, quiet.source()).cachedPlayers());
    }

    @Test
    void theHealthAndGateAreTheSiteSOwn() {
        List<FakeSource> sources = sources();
        TierCache cache = cache(sources);
        FakeSource site = sources.getFirst();

        cache.peek(site.source(), UUID.randomUUID());
        site.pending.completeExceptionally(new RuntimeException("down"));

        SiteDiagnostics row = CacheDiagnostics.of(cache, site.source());
        // Not compared to cache.health(...) wholesale: a snapshot carries ages measured
        // against nanoTime, so two readings taken microseconds apart differ by design.
        assertEquals(1, row.health().failures());
        assertEquals(0, row.health().successes());
        // SiteHealth qualifies the message with the exception's class; what matters here
        // is only that this site's own failure is the one that reached this row.
        assertTrue(row.health().lastError().orElseThrow().contains("down"));
        assertFalse(row.health().idle());
        assertEquals(cache.gateStatus(site.source()).consecutiveFailures(),
                row.gate().consecutiveFailures());

        assertTrue(CacheDiagnostics.of(cache, sources.getLast().source()).health().idle());
    }

    @Test
    void theWholeReportAgreesWithTheSiteBySiteOne() {
        List<FakeSource> sources = sources();
        TierCache cache = cache(sources);
        cache.peek(sources.getFirst().source(), UUID.randomUUID());

        for (SiteDiagnostics row : CacheDiagnostics.of(cache)) {
            assertEquals(counts(CacheDiagnostics.of(cache, row.source())), counts(row),
                    row.source().toString());
        }
    }

    /** The collector is a read: asking twice must not change what is being asked about. */
    @Test
    void readingTheDiagnosticsChangesNothing() {
        List<FakeSource> sources = sources();
        TierCache cache = cache(sources);
        cache.peek(sources.getFirst().source(), UUID.randomUUID());

        assertEquals(CacheDiagnostics.of(cache).stream().map(CacheDiagnosticsTest::counts).toList(),
                CacheDiagnostics.of(cache).stream().map(CacheDiagnosticsTest::counts).toList());
    }

    /**
     * A row's stable half. The rest of it — how long since the last failure, how long
     * until a closed gate reopens — is an age, so a whole row is a reading rather than a
     * value and two of them are never equal for long.
     */
    private static List<Integer> counts(SiteDiagnostics row) {
        return List.of(row.source().ordinal(), row.cachedPlayers(), row.pendingLookups(),
                row.playersAwaitingRetry(), row.health().successes(), row.health().failures(),
                row.gate().consecutiveFailures());
    }

    @Test
    void aClearedCacheReportsClear() {
        List<FakeSource> sources = sources();
        TierCache cache = cache(sources);
        FakeSource site = sources.getFirst();
        cache.peek(site.source(), UUID.randomUUID());
        site.pending.complete(Map.of());

        cache.invalidateAll();

        SiteDiagnostics row = CacheDiagnostics.of(cache, site.source());
        assertEquals(0, row.cachedPlayers());
        assertEquals(0, row.pendingLookups());
        // Clearing the cache is a request to fetch again, not a claim the history is gone.
        assertFalse(row.health().idle());
    }

    @Test
    void aCacheWithNoSourcesAtAllStillReportsEverySite() {
        TierCache empty = new TierCache(List.of(), Duration.ofMinutes(5));

        assertEquals(Source.ALL.size(), CacheDiagnostics.of(empty).size());
    }
}
