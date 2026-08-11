package com.w0x7y.justtiers.api;

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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TierSourceTest {

    private HttpServer server;
    private String baseUrl;
    private final HttpClient client = HttpClient.newHttpClient();
    private final AtomicInteger requestCount = new AtomicInteger();

    private static final UUID PLAYER = UUID.fromString("4b25be24-97f5-4adf-967d-8d69ef54d504");

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

    private void respond(String path, int status, String body) {
        server.createContext(path, exchange -> {
            requestCount.incrementAndGet();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
        });
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
    void serverErrorsResolveToEmptyRatherThanThrowing() throws Exception {
        respond("/v2/profile/" + PLAYER + "/rankings", 500, "boom");
        assertTrue(new MctiersLikeSource(Source.MCTIERS, client, baseUrl)
                .fetch(PLAYER).get().isEmpty());
    }

    @Test
    void connectionFailuresResolveToEmptyRatherThanThrowing() throws Exception {
        // Nothing is listening on this port path; the future must still complete.
        MctiersLikeSource dead = new MctiersLikeSource(
                Source.MCTIERS, client, "http://127.0.0.1:1");
        assertTrue(dead.fetch(PLAYER).get().isEmpty());
    }

    @Test
    void sourceIdentityIsReported() {
        assertEquals(Source.SUBTIERS,
                new MctiersLikeSource(Source.SUBTIERS, client, baseUrl).source());
    }

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
    void novaSurvivesAFailedFetch() throws Exception {
        respond("/users", 503, "");
        NovaTiersSource nova = new NovaTiersSource(client, baseUrl);
        assertTrue(nova.fetch(PLAYER).get().isEmpty());
        assertEquals(0, nova.indexedPlayerCount());
    }
}
