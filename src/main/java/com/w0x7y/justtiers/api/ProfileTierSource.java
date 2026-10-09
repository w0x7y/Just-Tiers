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

/** Per-player HTTP lookups with shared status handling and site-specific payloads. */
public final class ProfileTierSource implements TierSource {

    private final Source source;
    private final HttpClient client;
    private final String baseUrl;

    public ProfileTierSource(Source source, HttpClient client, String baseUrl) {
        if (source == Source.NOVATIERS) throw new IllegalArgumentException("NovaTiers uses a bulk index");
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
                baseUrl + profilePath(uuid), Duration.ofSeconds(10));

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    int status = response.statusCode();
                    if (status == 404) {
                        // The site returns 404 for players it has never tested. Not an error.
                        return Map.<String, Tier>of();
                    }
                    if (status != 200) {
                        if (status == 429 || (status == 503 && response.headers().firstValue("Retry-After").isPresent())) {
                            throw new RetryAfterException(source + " returned HTTP " + status,
                                    RetryAfter.parse(response.headers().firstValue("Retry-After").orElse(null),
                                            java.time.Instant.now()));
                        }
                        // Not "unranked" — the site failed to answer. Fail so the cache retries.
                        throw new TierLookupException(
                                source + " returned HTTP " + status + " for " + uuid);
                    }
                    return switch (source) {
                        case PVPTIERS -> PvpTiersParser.parseProfile(response.body());
                        case PVPHQ -> PvpHqParser.parseProfile(response.body());
                        case SUBTIERS -> RankingsParser.parseRankings(response.body());
                        case NOVATIERS -> throw new IllegalStateException("NovaTiers uses a bulk index");
                    };
                })
                .whenComplete((tiers, throwable) -> {
                    if (throwable != null) {
                        JustTiers.LOGGER.warn("{} lookup failed for {}: {}",
                                source, uuid, throwable.toString());
                    }
                });
    }

    private String profilePath(UUID uuid) {
        return switch (source) {
            case PVPTIERS -> "/profile/" + uuid.toString().replace("-", "");
            case PVPHQ -> "/players/" + uuid;
            case SUBTIERS -> "/v2/profile/" + uuid + "/rankings";
            case NOVATIERS -> throw new IllegalStateException("NovaTiers uses a bulk index");
        };
    }
}
