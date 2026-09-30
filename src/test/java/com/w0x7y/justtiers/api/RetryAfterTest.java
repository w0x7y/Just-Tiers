package com.w0x7y.justtiers.api;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class RetryAfterTest {
    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");
    @Test void deltaSecondsAndHttpDatesProduceTheRequestedWait() {
        assertEquals(Duration.ofSeconds(120), RetryAfter.parse("120", NOW));
        assertEquals(Duration.ofSeconds(120), RetryAfter.parse("Wed, 30 Sep 2026 12:02:00 GMT", NOW));
    }
    @Test void malformedOrAbsentDelaysUseASafeFallbackAndLargeValuesAreBounded() {
        assertEquals(Duration.ofSeconds(60), RetryAfter.parse(null, NOW));
        assertEquals(Duration.ofSeconds(60), RetryAfter.parse("-1", NOW));
        assertEquals(Duration.ofSeconds(60), RetryAfter.parse("broken", NOW));
        assertEquals(Duration.ofDays(1), RetryAfter.parse("99999999999999999999999", NOW));
        assertEquals(Duration.ZERO, RetryAfter.parse("Wed, 30 Sep 2026 11:00:00 GMT", NOW));
    }

    @Test void rateLimitedHttpResponseCarriesTheSiteCooldownToTheCache() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/profile/", exchange -> {
            exchange.getResponseHeaders().set("Retry-After", "120");
            exchange.sendResponseHeaders(429, -1);
            exchange.close();
        });
        server.start();
        try {
            var source = new MctiersLikeSource(com.w0x7y.justtiers.tier.Source.MCTIERS,
                    java.net.http.HttpClient.newHttpClient(), "http://127.0.0.1:" + server.getAddress().getPort());
            var error = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> source.fetch(java.util.UUID.randomUUID()).get(5, java.util.concurrent.TimeUnit.SECONDS));
            var cooldown = assertInstanceOf(RetryAfterException.class, error.getCause());
            assertEquals(Duration.ofSeconds(120), cooldown.delay());
        } finally { server.stop(0); }
    }
}
