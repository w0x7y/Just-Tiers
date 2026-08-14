package com.w0x7y.justtiers.api;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MojangNameSourceTest {

    private static final String NAME = "Notch";
    private static final String PREFIX = "/users/profiles/minecraft/";
    private static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    private HttpServer server;
    private String baseUrl;
    private final HttpClient client = HttpClient.newHttpClient();
    private final AtomicInteger requestCount = new AtomicInteger();

    private final int[] status = {200};
    private final String[] body = {""};
    /** The name the last request actually asked about, as Mojang would have seen it. */
    private volatile String requestedName;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // Mojang matches names case-insensitively, so the stub answers for any spelling
        // and records which one it was asked for.
        server.createContext(PREFIX, exchange -> {
            requestCount.incrementAndGet();
            requestedName = exchange.getRequestURI().getPath().substring(PREFIX.length());
            byte[] bytes = body[0].getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status[0], bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void respond(int code, String content) {
        status[0] = code;
        body[0] = content;
    }

    private MojangNameSource source() {
        return new MojangNameSource(client, baseUrl);
    }

    @Test
    void resolvesAnUndashedIdIntoAUuid() throws Exception {
        respond(200, "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\"}");

        PlayerRef profile = source().resolve(NAME).get().orElseThrow();
        assertEquals(NOTCH, profile.uuid());
        assertEquals("Notch", profile.name());
    }

    @Test
    void prefersMojangsSpellingOfTheName() throws Exception {
        respond(200, "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\"}");

        // Typed in lower case, printed back the way the account actually spells it.
        assertEquals("Notch", source().resolve("notch").get().orElseThrow().name());
    }

    @Test
    void anUnownedNameIsAnAnswerNotAFailure() throws Exception {
        respond(404, "");
        assertTrue(source().resolve(NAME).get().isEmpty());
    }

    @Test
    void aServerErrorFailsRatherThanLookingLikeAnUnownedName() {
        respond(500, "");
        ExecutionException thrown = assertThrows(ExecutionException.class,
                () -> source().resolve(NAME).get());
        assertInstanceOf(TierLookupException.class, thrown.getCause());
    }

    @Test
    void beingRateLimitedFailsRatherThanLookingLikeAnUnownedName() {
        respond(429, "");
        assertThrows(ExecutionException.class, () -> source().resolve(NAME).get());
    }

    @Test
    void anImpossibleNameNeverBecomesARequest() throws Exception {
        MojangNameSource source = source();
        assertTrue(source.resolve("no").get().isEmpty());
        assertTrue(source.resolve("this_name_is_far_too_long").get().isEmpty());
        assertTrue(source.resolve("has spaces").get().isEmpty());
        assertTrue(source.resolve(null).get().isEmpty());
        assertEquals(0, requestCount.get());
    }

    @Test
    void aResolvedNameIsRememberedForTheSession() throws Exception {
        respond(200, "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\"}");
        MojangNameSource source = source();

        assertEquals(NOTCH, source.resolve(NAME).get().orElseThrow().uuid());
        int before = requestCount.get();
        // Case does not start a second lookup either: it is the same account.
        assertEquals(NOTCH, source.resolve("notch").get().orElseThrow().uuid());
        assertEquals(before, requestCount.get());
    }

    @Test
    void aFailureIsRetriedRatherThanReplayed() throws Exception {
        respond(503, "");
        MojangNameSource source = source();
        assertThrows(ExecutionException.class, () -> source.resolve(NAME).get());

        respond(200, "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\"}");
        assertEquals(NOTCH, source.resolve(NAME).get().orElseThrow().uuid());
    }

    @Test
    void aMalformedAnswerIsNotMistakenForAUuid() throws Exception {
        respond(200, "{\"name\":\"Notch\"}");
        assertTrue(source().resolve(NAME).get().isEmpty());

        respond(200, "not json at all");
        assertTrue(new MojangNameSource(client, baseUrl).resolve(NAME).get().isEmpty());
    }

    @Test
    void connectionFailuresFailRatherThanLookingLikeAnUnownedName() {
        // Nothing is listening here, so the transport error must reach the caller.
        MojangNameSource dead = new MojangNameSource(client, "http://127.0.0.1:1");
        assertThrows(ExecutionException.class, () -> dead.resolve(NAME).get());
    }

    @Test
    void aTrailingSlashOnTheBaseUrlDoesNotDoubleUp() throws Exception {
        respond(200, "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\"}");
        assertEquals(NOTCH,
                new MojangNameSource(client, baseUrl + "/").resolve(NAME).get()
                        .orElseThrow().uuid());
    }

    @Test
    void theResolvedUuidIsTheV4FormTheLeaderboardsAreKeyedBy() throws Exception {
        respond(200, "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\"}");
        assertEquals(4, source().resolve(NAME).get().orElseThrow().uuid().version());
    }

    @Test
    void theNameIsSentAsTypedRatherThanLowerCased() throws Exception {
        // Only the cache key is case-folded; the request keeps the caller's spelling.
        respond(200, "{\"id\":\"069a79f444e94726a5befca90e38aaf5\",\"name\":\"Notch\"}");
        source().resolve(NAME).get();
        assertEquals(NAME, requestedName);
    }
}
