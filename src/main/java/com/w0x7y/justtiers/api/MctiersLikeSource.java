package com.w0x7y.justtiers.api;

import com.w0x7y.justtiers.JustTiers;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Serves MCTiers and SubTiers, which expose an identical v2 API. */
public final class MctiersLikeSource implements TierSource {

    private final Source source;
    private final HttpClient client;
    private final String baseUrl;

    public MctiersLikeSource(Source source, HttpClient client, String baseUrl) {
        this.source = source;
        this.client = client;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    public Source source() {
        return source;
    }

    @Override
    public CompletableFuture<Map<String, Tier>> fetch(UUID uuid) {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(baseUrl + "/v2/profile/" + uuid + "/rankings"))
                .header("User-Agent", JustTiers.USER_AGENT)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    int status = response.statusCode();
                    if (status == 404) {
                        // The site returns 404 for players it has never tested. Not an error.
                        return Map.<String, Tier>of();
                    }
                    if (status != 200) {
                        JustTiers.LOGGER.warn("{} returned HTTP {} for {}", source, status, uuid);
                        return Map.<String, Tier>of();
                    }
                    return MctiersParser.parseRankings(response.body());
                })
                .exceptionally(throwable -> {
                    JustTiers.LOGGER.warn("{} lookup failed for {}: {}",
                            source, uuid, throwable.toString());
                    return Map.of();
                });
    }
}
