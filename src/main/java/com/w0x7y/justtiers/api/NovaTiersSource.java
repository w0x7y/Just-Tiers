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

/**
 * NovaTiers exposes only a bulk {@code /users} array (~6.5k players, ~1.9 MB), so the
 * entire list is downloaded once and held as a UUID index. Call {@link #refresh()}
 * periodically to pick up new placements.
 */
public final class NovaTiersSource implements TierSource {

    private final HttpClient client;
    private final String baseUrl;

    private volatile CompletableFuture<Map<UUID, Map<String, Tier>>> index;
    private volatile int indexedPlayerCount;

    public NovaTiersSource(HttpClient client, String baseUrl) {
        this.client = client;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    public Source source() {
        return Source.NOVATIERS;
    }

    @Override
    public CompletableFuture<Map<String, Tier>> fetch(UUID uuid) {
        return ensureLoaded().thenApply(idx -> idx.getOrDefault(uuid, Map.of()));
    }

    /** Number of players currently indexed. Useful for logging and the refresh command. */
    public int indexedPlayerCount() {
        return indexedPlayerCount;
    }

    private synchronized CompletableFuture<Map<UUID, Map<String, Tier>>> ensureLoaded() {
        if (index == null) {
            index = download();
        }
        return index;
    }

    /** Discards the cached index and downloads it again. */
    public synchronized CompletableFuture<Void> refresh() {
        index = download();
        return index.thenAccept(idx -> { });
    }

    private CompletableFuture<Map<UUID, Map<String, Tier>>> download() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/users"))
                .header("User-Agent", JustTiers.USER_AGENT)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        JustTiers.LOGGER.warn("NovaTiers returned HTTP {}", response.statusCode());
                        return Map.<UUID, Map<String, Tier>>of();
                    }
                    Map<UUID, Map<String, Tier>> parsed = NovaParser.parseUsers(response.body());
                    if (parsed.isEmpty() && response.body() != null && response.body().length() > 2) {
                        JustTiers.LOGGER.warn(
                                "NovaTiers answered HTTP 200 but nothing was understood; "
                                        + "the response schema may have changed");
                    }
                    JustTiers.LOGGER.info("Indexed {} NovaTiers players", parsed.size());
                    return parsed;
                })
                .exceptionally(throwable -> {
                    JustTiers.LOGGER.warn("NovaTiers download failed: {}", throwable.toString());
                    return Map.of();
                })
                .thenApply(idx -> {
                    indexedPlayerCount = idx.size();
                    return idx;
                });
    }
}
