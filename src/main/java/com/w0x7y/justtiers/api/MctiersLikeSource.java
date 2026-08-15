package com.w0x7y.justtiers.api;

import com.w0x7y.justtiers.JustTiers;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

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
        this.baseUrl = JustTiers.trimTrailingSlash(baseUrl);
    }

    @Override
    public Source source() {
        return source;
    }

    @Override
    public CompletableFuture<Map<String, Tier>> fetch(UUID uuid) {
        HttpRequest request = JustTiers.jsonRequest(
                baseUrl + "/v2/profile/" + uuid + "/rankings", Duration.ofSeconds(10));

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    int status = response.statusCode();
                    if (status == 404) {
                        // The site returns 404 for players it has never tested. Not an error.
                        return Map.<String, Tier>of();
                    }
                    if (status != 200) {
                        // Not "unranked" — the site failed to answer. Fail so the cache retries.
                        throw new TierLookupException(
                                source + " returned HTTP " + status + " for " + uuid);
                    }
                    Map<String, Tier> parsed = MctiersParser.parseRankings(response.body());
                    if (parsed.isEmpty() && response.body() != null && response.body().length() > 2) {
                        JustTiers.LOGGER.warn(
                                "{} answered HTTP 200 but nothing was understood for {}; "
                                        + "the response schema may have changed",
                                source, uuid);
                    }
                    return parsed;
                })
                .whenComplete((tiers, throwable) -> {
                    if (throwable != null) {
                        JustTiers.LOGGER.warn("{} lookup failed for {}: {}",
                                source, uuid, throwable.toString());
                    }
                });
    }
}
