package com.w0x7y.justtiers.api;

import com.w0x7y.justtiers.download.DownloadProgress;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;
import com.sun.net.httpserver.HttpServer;
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

class TierSourceTest {

    private HttpServer server;
    private String baseUrl;
    private final HttpClient client = HttpClient.newHttpClient();
    private final AtomicInteger requestCount = new AtomicInteger();

    private static final UUID PLAYER = UUID.fromString("4b25be24-97f5-4adf-967d-8d69ef54d504");

    private final Map<String, int[]> routes = new ConcurrentHashMap<>();
    private final Map<String, String> bodies = new ConcurrentHashMap<>();

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

    // --- MctiersLikeSource ---

    @Test
    void fetchesAndParsesRankings() throws Exception {
        respond("/v2/profile/" + PLAYER + "/rankings", 200,
                "{\"vanilla\":{\"tier\":2,\"pos\":0,\"retired\":false}}");
        Map<String, Tier> tiers =
                new MctiersLikeSource(Source.MCTIERS, client, baseUrl).fetch(PLAYER).get();
        assertEquals("HT2", tiers.get("vanilla").label());
    }

    @Test
    void notFoundMeansUnrankedNotFailure() throws Exception {
        respond("/v2/profile/" + PLAYER + "/rankings", 404, "");
        Map<String, Tier> tiers =
                new MctiersLikeSource(Source.MCTIERS, client, baseUrl).fetch(PLAYER).get();
        assertNotNull(tiers);
        assertTrue(tiers.isEmpty());
    }

    @Test
    void serverErrorsFailRatherThanLookingLikeAnUnrankedPlayer() {
        respond("/v2/profile/" + PLAYER + "/rankings", 500, "boom");
        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> new MctiersLikeSource(Source.MCTIERS, client, baseUrl).fetch(PLAYER).get());
        assertInstanceOf(TierLookupException.class, thrown.getCause());
    }

    @Test
    void connectionFailuresFailRatherThanLookingLikeAnUnrankedPlayer() {
        // Nothing is listening here, so the transport error must reach the caller.
        MctiersLikeSource dead = new MctiersLikeSource(
                Source.MCTIERS, client, "http://127.0.0.1:1");
        assertThrows(ExecutionException.class, () -> dead.fetch(PLAYER).get());
    }

    @Test
    void a404IsStillAGenuineUnrankedAnswer() throws Exception {
        respond("/v2/profile/" + PLAYER + "/rankings", 404, "");
        assertTrue(new MctiersLikeSource(Source.MCTIERS, client, baseUrl)
                .fetch(PLAYER).get().isEmpty());
    }

    @Test
    void sourceIdentityIsReported() {
        assertEquals(Source.SUBTIERS,
                new MctiersLikeSource(Source.SUBTIERS, client, baseUrl).source());
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
        respond("/users", 503, "");
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
        nova.refresh().get();

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

    // --- nothingUnderstood ---

    @Test
    void aBodyThatParsedToNothingButCarriedSomethingIsReported() {
        // The schema changed under us: content arrived, none of it was understood.
        assertTrue(TierSource.nothingUnderstood(Map.of(), "{\"axe\":{\"grade\":3}}"));
    }

    @Test
    void anEmptyAnswerIsNotAParseFailure() {
        // A site legitimately saying "no placements" must stay silent.
        assertFalse(TierSource.nothingUnderstood(Map.of(), "{}"));
        assertFalse(TierSource.nothingUnderstood(Map.of(), "[]"));
        assertFalse(TierSource.nothingUnderstood(Map.of(), ""));
        assertFalse(TierSource.nothingUnderstood(Map.of(), null));
    }

    @Test
    void anythingParsedIsNeverAParseFailure() {
        assertFalse(TierSource.nothingUnderstood(Map.of("axe", 1), "{\"axe\":{\"tier\":1}}"));
    }
}
