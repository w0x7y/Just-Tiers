package com.w0x7y.justtiers.api;

import com.sun.net.httpserver.HttpServer;
import com.w0x7y.justtiers.download.DownloadProgress;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** HTTP outcomes remain distinct from unranked answers, and Nova downloads share work without losing a good index. */
class TierSourceTest {

    private HttpServer server;
    private String baseUrl;
    private final HttpClient client = HttpClient.newHttpClient();
    private final AtomicInteger requestCount = new AtomicInteger();

    private static final UUID PLAYER = UUID.fromString("4b25be24-97f5-4adf-967d-8d69ef54d504");

    private final Map<String, int[]> routes = new ConcurrentHashMap<>();
    private final Map<String, String> bodies = new ConcurrentHashMap<>();
    private final Map<String, String> retryAfterHeaders = new ConcurrentHashMap<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    /**
     * Registers (or replaces) the canned response for a path. Calling this twice for the
     * same path swaps the answer, which is how the retry tests move a site from failing
     * back to healthy mid-test.
     */
    private void respond(String path, int status, String body) {
        if (routes.put(path, new int[]{status}) == null) {
            server.createContext(path, exchange -> {
                requestCount.incrementAndGet();
                byte[] bytes = bodies.get(path).getBytes(StandardCharsets.UTF_8);
                String retryAfter = retryAfterHeaders.get(path);
                if (retryAfter != null) {
                    exchange.getResponseHeaders().set("Retry-After", retryAfter);
                }
                exchange.sendResponseHeaders(
                        routes.get(path)[0], bytes.length == 0 ? -1 : bytes.length);
                if (bytes.length > 0) {
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(bytes);
                    }
                }
            });
        } else {
            routes.get(path)[0] = status;
        }
        bodies.put(path, body);
    }

    // --- ProfileTierSource ---

    @Test
    void pvpTiersUsesACompactUuidAndUnwrapsTheProfileRankings() throws Exception {
        respond("/profile/" + PLAYER.toString().replace("-", ""), 200, """
                {"uuid":"4b25be2497f54adf967d8d69ef54d504","name":"Player",
                 "rankings":{"crystal":{"tier":2,"pos":0,"retired":true,"peak_tier":1},
                             "neth_pot":{"tier":3,"pos":1}},"points":28}
                """);
        Map<String, Tier> tiers = new ProfileTierSource(Source.PVPTIERS, client, baseUrl).fetch(PLAYER).get();
        assertEquals("RHT2", tiers.get("crystal").label());
        assertEquals("LT3", tiers.get("neth_pot").label());
    }

    @Test
    void pvpHqReadsCurrentTiersAndTheCartAliasWithoutTreatingInactivityAsRetirement() throws Exception {
        respond("/players/" + PLAYER, 200, """
                {"uuid":"4b25be24-97f5-4adf-967d-8d69ef54d504","name":"Player",
                 "ranked":[{"gametype":"sword","tier":"MT3","unranked":false,
                            "inactive":true,"peakTier":"HT1"},
                           {"gametype":"ht_cart","tier":"HT4","unranked":false},
                           {"gametype":"axe","tier":"HT1","unranked":true}]}
                """);
        Map<String, Tier> tiers = new ProfileTierSource(Source.PVPHQ, client, baseUrl).fetch(PLAYER).get();
        assertEquals("MT3", tiers.get("sword").label());
        assertEquals("HT4", tiers.get("cart").label());
        assertFalse(tiers.containsKey("axe"));
        assertEquals(2, tiers.size());
    }

    @Test
    void newSitesKeepUnrankedFailureAndRateLimitOutcomesDistinct() throws Exception {
        for (Source source : java.util.List.of(Source.PVPTIERS, Source.PVPHQ)) {
            String path = source == Source.PVPTIERS
                    ? "/profile/" + PLAYER.toString().replace("-", "") : "/players/" + PLAYER;
            var api = new ProfileTierSource(source, client, baseUrl);
            respond(path, 404, "");
            assertTrue(api.fetch(PLAYER).get().isEmpty());
            respond(path, 200, source == Source.PVPTIERS ? "{\"rankings\":{}}" : "{\"ranked\":[]}");
            assertTrue(api.fetch(PLAYER).get().isEmpty());
            for (String body : java.util.List.of("{}", "null", "[]", "<html>maintenance</html>",
                    "{\"rankings\":{\"sword\":{}},\"ranked\":[{\"gametype\":\"sword\",\"tier\":\"bad\"}]}")) {
                respond(path, 200, body);
                assertThrows(ExecutionException.class, () -> api.fetch(PLAYER).get(), source + ": " + body);
            }
            respond(path, 500, "");
            assertInstanceOf(TierLookupException.class,
                    assertThrows(ExecutionException.class, () -> api.fetch(PLAYER).get()).getCause());
            respond(path, 429, "");
            retryAfterHeaders.put(path, "120");
            var error = assertThrows(ExecutionException.class, () -> api.fetch(PLAYER).get());
            assertEquals(java.time.Duration.ofSeconds(120),
                    assertInstanceOf(RetryAfterException.class, error.getCause()).delay());
        }
    }

    @Test
    void pvpHqCanReturnOnlyUnrankedGamemodes() throws Exception {
        respond("/players/" + PLAYER, 200, """
                {"ranked":[{"gametype":"sword","tier":null,"unranked":true}]}
                """);
        assertTrue(new ProfileTierSource(Source.PVPHQ, client, baseUrl).fetch(PLAYER).get().isEmpty());
    }

    @Test
    void fetchesAndParsesRankings() throws Exception {
        respond("/v2/profile/" + PLAYER + "/rankings", 200,
                "{\"vanilla\":{\"tier\":2,\"pos\":0,\"retired\":false}}");
        Map<String, Tier> tiers =
                new ProfileTierSource(Source.SUBTIERS, client, baseUrl).fetch(PLAYER).get();
        assertEquals("HT2", tiers.get("vanilla").label());
    }

    @Test
    void notFoundMeansUnrankedNotFailure() throws Exception {
        respond("/v2/profile/" + PLAYER + "/rankings", 404, "");
        Map<String, Tier> tiers =
                new ProfileTierSource(Source.SUBTIERS, client, baseUrl).fetch(PLAYER).get();
        assertNotNull(tiers);
        assertTrue(tiers.isEmpty());
    }

    @Test
    void serverErrorsFailRatherThanLookingLikeAnUnrankedPlayer() {
        respond("/v2/profile/" + PLAYER + "/rankings", 500, "boom");
        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> new ProfileTierSource(Source.SUBTIERS, client, baseUrl).fetch(PLAYER).get());
        assertInstanceOf(TierLookupException.class, thrown.getCause());
    }

    @Test
    void connectionFailuresFailRatherThanLookingLikeAnUnrankedPlayer() {
        // Nothing is listening here, so the transport error must reach the caller.
        ProfileTierSource dead = new ProfileTierSource(
                Source.SUBTIERS, client, "http://127.0.0.1:1");
        assertThrows(ExecutionException.class, () -> dead.fetch(PLAYER).get());
    }

    private static final String ONE_USER = """
            [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504",
              "tiers":{"Axe":"HT3"},"retiredTiers":{}}]
            """;

    // --- NovaTiersSource ---

    @Test
    void novaIndexesTheBulkListAndServesFromMemory() throws Exception {
        respond("/users", 200, """
                [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504",
                  "tiers":{"Axe":"HT3"},"retiredTiers":{}}]
                """);
        NovaTiersSource nova = new NovaTiersSource(client, baseUrl);

        assertEquals("HT3", nova.fetch(PLAYER).get().get("axe").label());
        assertEquals(1, nova.indexedPlayerCount());

        // A second lookup must not hit the network again.
        int before = requestCount.get();
        assertEquals("HT3", nova.fetch(PLAYER).get().get("axe").label());
        assertEquals(before, requestCount.get());
    }

    @Test
    void novaReturnsEmptyForPlayersNotInTheList() throws Exception {
        respond("/users", 200, "[]");
        NovaTiersSource nova = new NovaTiersSource(client, baseUrl);
        assertTrue(nova.fetch(UUID.randomUUID()).get().isEmpty());
    }

    @Test
    void novaRefreshRefetchesTheList() throws Exception {
        respond("/users", 200, """
                [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504",
                  "tiers":{"Axe":"HT3"},"retiredTiers":{}}]
                """);
        NovaTiersSource nova = new NovaTiersSource(client, baseUrl);
        nova.fetch(PLAYER).get();
        int before = requestCount.get();

        nova.refresh().get();
        assertTrue(requestCount.get() > before);
    }

    @Test
    void novaReportsAFailedDownloadInsteadOfAnEmptyIndex() {
        respond("/users", 503, "");
        NovaTiersSource nova = new NovaTiersSource(client, baseUrl);
        assertThrows(ExecutionException.class, () -> nova.fetch(PLAYER).get());
        assertEquals(0, nova.indexedPlayerCount());
    }

    @Test
    void novaRetriesAfterAFailedFirstDownloadRatherThanReplayingTheError() throws Exception {
        respond("/users", 500, "");
        NovaTiersSource nova = new NovaTiersSource(client, baseUrl);
        assertThrows(ExecutionException.class, () -> nova.fetch(PLAYER).get());

        respond("/users", 200, ONE_USER);
        assertFalse(nova.fetch(PLAYER).get().isEmpty());
    }

    @Test
    void novaKeepsTheExistingIndexWhenARefreshFails() throws Exception {
        respond("/users", 200, ONE_USER);
        NovaTiersSource nova = new NovaTiersSource(client, baseUrl);
        assertFalse(nova.fetch(PLAYER).get().isEmpty());
        int indexed = nova.indexedPlayerCount();
        assertTrue(indexed > 0);

        respond("/users", 503, "");
        assertThrows(ExecutionException.class, () -> nova.refresh().get());

        assertFalse(nova.fetch(PLAYER).get().isEmpty(), "stale index must survive a failed refresh");
        assertEquals(indexed, nova.indexedPlayerCount());
    }

    // --- download progress ---

    @Test
    void reportsProgressForASuccessfulBulkDownload() throws Exception {
        respond("/users", 200, "[]");
        DownloadProgress progress = new DownloadProgress();

        new NovaTiersSource(client, baseUrl, progress).fetch(PLAYER).get();

        DownloadProgress.Snapshot snapshot = progress.snapshot();
        assertEquals(DownloadProgress.State.IDLE, snapshot.state());
        // Calibrated by the download that just finished, so the next one can show a percentage.
        assertEquals(2, snapshot.total());
        assertTrue(snapshot.determinate());
    }

    @Test
    void reportsFailureWhenTheBulkDownloadFails() {
        respond("/users", 500, "");
        DownloadProgress progress = new DownloadProgress();

        assertThrows(ExecutionException.class,
                () -> new NovaTiersSource(client, baseUrl, progress).fetch(PLAYER).get());

        DownloadProgress.Snapshot snapshot = progress.snapshot();
        assertEquals(DownloadProgress.State.FAILED, snapshot.state());
        // A failed download is not a measurement, so it must not calibrate the next one.
        assertFalse(snapshot.determinate());
    }

    @Test
    void malformedSuccessfulResponsesFailInsteadOfBecomingUnranked() {
        for (String body : java.util.List.of("<html>unavailable</html>", "[]", "null", "", "{\"error\":\"maintenance\"}")) {
            respond("/v2/profile/" + PLAYER + "/rankings", 200, body);
            assertThrows(ExecutionException.class,
                    () -> new ProfileTierSource(Source.SUBTIERS, client, baseUrl).fetch(PLAYER).get(), body);
        }
    }

    @Test
    void aMalformedRefreshFailsAndRetainsTheLastGoodNovaIndex() throws Exception {
        respond("/users", 200, ONE_USER);
        NovaTiersSource nova = new NovaTiersSource(client, baseUrl);
        assertEquals("HT3", nova.fetch(PLAYER).get().get("axe").label());
        respond("/users", 200, "<html>maintenance</html>");
        assertThrows(ExecutionException.class, () -> nova.refresh().get());
        assertEquals("HT3", nova.fetch(PLAYER).get().get("axe").label());
        assertEquals(1, nova.indexedPlayerCount());
    }

    @Test
    void overlappingRefreshesShareTheInitialDownload() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        server.createContext("/users", exchange -> {
            requestCount.incrementAndGet();
            entered.countDown();
            try {
                if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new IOException("test did not release response");
                }
                byte[] bytes = ONE_USER.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        NovaTiersSource nova = new NovaTiersSource(client, baseUrl);
        var initial = nova.fetch(PLAYER);
        try {
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
            var first = nova.refresh();
            var second = nova.refresh();
            release.countDown();
            initial.get(); first.get(); second.get();
            assertEquals(1, requestCount.get());
        } finally {
            release.countDown();
        }
    }

    @Test
    void novaMalformedIdentifiablePlayersFailWhileOtherPlayersRemainAvailable() throws Exception {
        UUID damaged = UUID.fromString("dadd05d5-e1a2-41bc-be3e-de5f7d9fffee");
        for (String brokenTiers : java.util.List.of("null", "[]", "{\"Axe\":\"unknown tier\"}")) {
            respond("/users", 200, """
                    [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504","tiers":{"Axe":"HT3"}},
                     {"minecraftUuid":"dadd05d5-e1a2-41bc-be3e-de5f7d9fffee","tiers":%s}]
                    """.formatted(brokenTiers));
            NovaTiersSource nova = new NovaTiersSource(client, baseUrl);
            assertEquals("HT3", nova.fetch(PLAYER).get().get("axe").label());
            ExecutionException error = assertThrows(ExecutionException.class,
                    () -> nova.fetch(damaged).get(), brokenTiers);
            assertInstanceOf(TierLookupException.class, error.getCause());
            assertTrue(nova.fetch(UUID.randomUUID()).get().isEmpty());
        }
    }

    @Test
    void novaPartiallyDamagedPlayersFailInsteadOfClaimingOtherGamemodesAreAbsent() throws Exception {
        respond("/users", 200, """
                [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504",
                  "tiers":{"Axe":"HT3","SMP":{"unexpected":"object"}}}]
                """);
        NovaTiersSource nova = new NovaTiersSource(client, baseUrl);
        ExecutionException error = assertThrows(ExecutionException.class, () -> nova.fetch(PLAYER).get());
        assertInstanceOf(TierLookupException.class, error.getCause());
    }

    @Test
    void novaServesPreviousIndexImmediatelyWhileRefreshIsPending() throws Exception {
        respond("/users", 200, ONE_USER);
        var clock = new java.util.concurrent.atomic.AtomicLong();
        NovaTiersSource nova = new NovaTiersSource(client, baseUrl, new DownloadProgress(), clock::get);
        assertEquals("HT3", nova.fetch(PLAYER).get().get("axe").label());
        clock.addAndGet(java.time.Duration.ofHours(2).toNanos());
        server.removeContext("/users");
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        server.createContext("/users", exchange -> {
            requestCount.incrementAndGet();
            entered.countDown();
            try {
                if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new IOException("test did not release refresh");
                }
                byte[] bytes = ONE_USER.replace("HT3", "HT1").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        try {
            var refresh = nova.refresh();
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertSame(refresh, nova.refresh());
            var duringRefresh = nova.fetchAnswer(PLAYER);
            assertTrue(duringRefresh.isDone(), "a published snapshot must be available during a download");
            assertEquals("HT3", duringRefresh.get().tiers().get("axe").label());
            assertEquals(java.time.Duration.ofHours(2), duringRefresh.get().age());
            assertFalse(duringRefresh.get().refreshFailed());
            release.countDown();
            refresh.get();
            assertEquals("HT1", nova.fetch(PLAYER).get().get("axe").label());
            assertEquals(2, requestCount.get());
        } finally {
            release.countDown();
        }
    }

    @Test
    void novaConflictingDuplicatePlayersFailInsteadOfChoosingOneRecord() throws Exception {
        respond("/users", 200, """
                [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504","tiers":{"Axe":"HT3"}},
                 {"minecraftUuid":"4b25be24-97f5-4adf-967d-8d69ef54d504","tiers":{"Axe":"HT1"}}]
                """);
        NovaTiersSource nova = new NovaTiersSource(client, baseUrl);
        assertThrows(ExecutionException.class, () -> nova.fetch(PLAYER).get());
    }

    @Test
    void novaMalformedRetirementDataFailsTheIdentifiablePlayer() {
        for (String retirement : java.util.List.of("[]", "{\"Axe\":\"false\"}", "{\"Axe\":{}}")) {
            respond("/users", 200, """
                    [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504",
                      "tiers":{"Axe":"HT3"},"retiredTiers":%s}]
                    """.formatted(retirement));
            NovaTiersSource nova = new NovaTiersSource(client, baseUrl);
            assertThrows(ExecutionException.class, () -> nova.fetch(PLAYER).get(), retirement);
        }
    }

    @Test
    void novaUnknownValidGamemodesAndEmptyTierMapsRemainSuccessful() throws Exception {
        respond("/users", 200, """
                [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504","tiers":{"New Mode":"HT1"}},
                 {"minecraftUuid":"dadd05d5-e1a2-41bc-be3e-de5f7d9fffee","tiers":{}}]
                """);
        NovaTiersSource nova = new NovaTiersSource(client, baseUrl);
        assertTrue(nova.fetch(PLAYER).get().isEmpty());
        assertTrue(nova.fetch(UUID.fromString("dadd05d5-e1a2-41bc-be3e-de5f7d9fffee")).get().isEmpty());
    }

    @Test
    void novaStartupRateLimitBlocksRepeatedFetchesAndExplicitRefreshes() {
        retryAfterHeaders.put("/users", "120");
        respond("/users", 429, "rate limited");
        NovaTiersSource nova = new NovaTiersSource(client, baseUrl);
        assertThrows(ExecutionException.class, () -> nova.fetch(PLAYER).get());
        respond("/users", 200, ONE_USER);
        assertThrows(ExecutionException.class, () -> nova.fetch(PLAYER).get());
        assertThrows(ExecutionException.class, () -> nova.refresh().get());
        assertEquals(1, requestCount.get(), "all bulk download paths must honor the site's deadline");
    }

    @Test
    void novaRefreshRateLimitKeepsSnapshotAndBlocksFurtherRefreshes() throws Exception {
        respond("/users", 200, ONE_USER);
        NovaTiersSource nova = new NovaTiersSource(client, baseUrl);
        assertEquals("HT3", nova.fetch(PLAYER).get().get("axe").label());
        retryAfterHeaders.put("/users", "120");
        respond("/users", 503, "temporarily unavailable");
        assertThrows(ExecutionException.class, () -> nova.refresh().get());
        respond("/users", 200, ONE_USER.replace("HT3", "HT1"));
        assertEquals("HT3", nova.fetch(PLAYER).get().get("axe").label());
        assertThrows(ExecutionException.class, () -> nova.refresh().get());
        assertEquals(2, requestCount.get());
    }

    @Test
    void novaAnswersKeepOriginalSnapshotAgeAndReportFailedRefreshes() throws Exception {
        var clock = new java.util.concurrent.atomic.AtomicLong(java.time.Duration.ofSeconds(10).toNanos());
        respond("/users", 200, ONE_USER);
        NovaTiersSource nova = new NovaTiersSource(client, baseUrl, new DownloadProgress(), clock::get);
        assertEquals(java.time.Duration.ZERO, nova.fetchAnswer(PLAYER).get().age());
        clock.addAndGet(java.time.Duration.ofHours(2).toNanos());
        TierSource.Answer old = nova.fetchAnswer(PLAYER).get();
        assertEquals(java.time.Duration.ofHours(2), old.age());
        assertFalse(old.refreshFailed());
        respond("/users", 500, "unavailable");
        assertThrows(ExecutionException.class, () -> nova.refresh().get());
        clock.addAndGet(java.time.Duration.ofHours(1).toNanos());
        TierSource.Answer failedRefresh = nova.fetchAnswer(PLAYER).get();
        assertEquals("HT3", failedRefresh.tiers().get("axe").label());
        assertEquals(java.time.Duration.ofHours(3), failedRefresh.age());
        assertTrue(failedRefresh.refreshFailed());
        respond("/users", 200, ONE_USER.replace("HT3", "HT1"));
        nova.refresh().get();
        TierSource.Answer replaced = nova.fetchAnswer(PLAYER).get();
        assertEquals("HT1", replaced.tiers().get("axe").label());
        assertEquals(java.time.Duration.ZERO, replaced.age());
        assertFalse(replaced.refreshFailed());
    }

    @Test
    void novaBulkCooldownExpiresAtTheMonotonicDeadline() throws Exception {
        var clock = new java.util.concurrent.atomic.AtomicLong(-100_000_000_000L);
        retryAfterHeaders.put("/users", "120");
        respond("/users", 429, "rate limited");
        NovaTiersSource nova = new NovaTiersSource(client, baseUrl, new DownloadProgress(), clock::get);
        ExecutionException first = assertThrows(ExecutionException.class, () -> nova.fetch(PLAYER).get());
        assertEquals(java.time.Duration.ofSeconds(120),
                assertInstanceOf(RetryAfterException.class, first.getCause()).delay());
        respond("/users", 200, ONE_USER);
        clock.addAndGet(java.time.Duration.ofSeconds(119).toNanos());
        ExecutionException blocked = assertThrows(ExecutionException.class, () -> nova.refresh().get());
        assertEquals(java.time.Duration.ofSeconds(1),
                assertInstanceOf(RetryAfterException.class, blocked.getCause()).delay());
        assertEquals(1, requestCount.get());
        clock.addAndGet(java.time.Duration.ofSeconds(1).toNanos());
        assertEquals("HT3", nova.fetch(PLAYER).get().get("axe").label());
        assertEquals(2, requestCount.get());
    }

    @Test
    void novaBulkCooldownHonorsHttpDatesAndFallbackDelays() throws Exception {
        var clock = new java.util.concurrent.atomic.AtomicLong();
        String date = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(
                java.time.Instant.now().plusSeconds(120).atZone(java.time.ZoneOffset.UTC));
        for (String hint : java.util.List.of(date, "invalid hint")) {
            int before = requestCount.get();
            retryAfterHeaders.put("/users", hint);
            respond("/users", 503, "unavailable");
            NovaTiersSource nova = new NovaTiersSource(client, baseUrl, new DownloadProgress(), clock::get);
            ExecutionException first = assertThrows(ExecutionException.class, () -> nova.fetch(PLAYER).get());
            assertInstanceOf(RetryAfterException.class, first.getCause());
            respond("/users", 200, ONE_USER);
            assertThrows(ExecutionException.class, () -> nova.refresh().get());
            assertEquals(before + 1, requestCount.get());
            clock.addAndGet(java.time.Duration.ofSeconds(121).toNanos());
            nova.refresh().get();
            assertEquals(before + 2, requestCount.get());
        }
    }

    @Test
    void novaAndTierCacheKeepDataVintageAndExpireBeyondStaleGrace() throws Exception {
        var clock = new java.util.concurrent.atomic.AtomicLong();
        respond("/users", 200, ONE_USER);
        NovaTiersSource nova = new NovaTiersSource(client, baseUrl, new DownloadProgress(), clock::get);
        var cache = new com.w0x7y.justtiers.cache.TierCache(java.util.List.of(nova),
                com.w0x7y.justtiers.cache.CachePolicy.DEFAULT, clock::get, () -> 0.5);
        assertEquals("HT3", cache.load(Source.NOVATIERS, PLAYER).get().get("axe").label());
        clock.addAndGet(java.time.Duration.ofHours(2).toNanos());
        respond("/users", 500, "unavailable");
        assertThrows(ExecutionException.class, () -> nova.refresh().get());
        cache.load(Source.NOVATIERS, PLAYER).get();
        var retained = cache.cachedAnswer(Source.NOVATIERS, PLAYER).orElseThrow();
        assertEquals(java.time.Duration.ofHours(2), retained.age());
        assertTrue(retained.stale());
        assertTrue(retained.refreshFailed());
        for (int i = 0; i < 100; i++) {
            assertEquals("HT3", cache.peek(Source.NOVATIERS, PLAYER).orElseThrow().get("axe").label());
        }
        assertEquals(2, cache.health(Source.NOVATIERS).successes(),
                "repeated frames must not treat the original data age as the last local check time");
        clock.addAndGet(java.time.Duration.ofHours(5).toNanos());
        assertTrue(cache.peek(Source.NOVATIERS, PLAYER).isEmpty());
        assertTrue(cache.cachedAnswer(Source.NOVATIERS, PLAYER).isEmpty());
        assertThrows(ExecutionException.class, () -> cache.load(Source.NOVATIERS, PLAYER).get());
        assertEquals(2, requestCount.get());
        respond("/users", 200, ONE_USER.replace("HT3", "HT1"));
        nova.refresh().get();
        cache.invalidate(Source.NOVATIERS);
        assertEquals("HT1", cache.load(Source.NOVATIERS, PLAYER).get().get("axe").label());
        var fresh = cache.cachedAnswer(Source.NOVATIERS, PLAYER).orElseThrow();
        assertEquals(java.time.Duration.ZERO, fresh.age());
        assertFalse(fresh.stale());
        assertFalse(fresh.refreshFailed());
    }

    @Test
    void novaTimedBulkRefreshStateIsVisibleThroughStillFreshPlayerCache() throws Exception {
        var clock = new java.util.concurrent.atomic.AtomicLong();
        respond("/users", 200, ONE_USER);
        NovaTiersSource nova = new NovaTiersSource(client, baseUrl, new DownloadProgress(), clock::get);
        var cache = new com.w0x7y.justtiers.cache.TierCache(java.util.List.of(nova),
                com.w0x7y.justtiers.cache.CachePolicy.DEFAULT, clock::get, () -> 0.5);
        cache.load(Source.NOVATIERS, PLAYER).get();
        clock.addAndGet(java.time.Duration.ofMinutes(30).toNanos());
        retryAfterHeaders.put("/users", "120");
        respond("/users", 503, "unavailable");
        assertThrows(ExecutionException.class, () -> nova.refresh().get());
        var failed = cache.cachedAnswer(Source.NOVATIERS, PLAYER).orElseThrow();
        assertTrue(failed.refreshFailed(), "a timed bulk failure must be visible before the player TTL expires");
        assertTrue(failed.stale());
        assertFalse(failed.refreshing());
        assertEquals(java.time.Duration.ofMinutes(30), failed.age());
        assertEquals(java.time.Duration.ofSeconds(120), cache.cooldownRemaining(Source.NOVATIERS));
        assertEquals(1, cache.health(Source.NOVATIERS).successes(), "metadata reads must not issue player fetches");
        assertEquals(2, requestCount.get());
        clock.addAndGet(java.time.Duration.ofSeconds(120).toNanos());
        assertEquals(java.time.Duration.ZERO, cache.cooldownRemaining(Source.NOVATIERS));
        server.removeContext("/users");
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        server.createContext("/users", exchange -> {
            requestCount.incrementAndGet();
            entered.countDown();
            try {
                if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new IOException("test did not release bulk refresh");
                }
                byte[] bytes = ONE_USER.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        try {
            var refresh = nova.refresh();
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
            var pending = cache.cachedAnswer(Source.NOVATIERS, PLAYER).orElseThrow();
            assertTrue(pending.refreshing());
            assertTrue(pending.refreshFailed());
            assertEquals("HT3", pending.tiers().get("axe").label());
            release.countDown();
            refresh.get();
            var succeeded = cache.cachedAnswer(Source.NOVATIERS, PLAYER).orElseThrow();
            assertFalse(succeeded.refreshing());
            assertFalse(succeeded.refreshFailed());
            assertEquals(1, cache.health(Source.NOVATIERS).successes());
            assertEquals(3, requestCount.get());
        } finally {
            release.countDown();
        }
    }

}
