package com.w0x7y.justtiers.api;

import com.w0x7y.justtiers.JustTiers;
import com.w0x7y.justtiers.download.DownloadProgress;
import com.w0x7y.justtiers.download.ProgressBodyHandler;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * NovaTiers exposes only a bulk {@code /users} array (~6.5k players, ~1.7 MB), so the
 * entire list is downloaded once and held as a UUID index. Call {@link #refresh()}
 * periodically to pick up new placements.
 */
public final class NovaTiersSource implements TierSource {

    private final HttpClient client;
    private final String baseUrl;
    private final DownloadProgress progress;

    private volatile CompletableFuture<Map<UUID, Map<String, Tier>>> index;
    private volatile int indexedPlayerCount;

    public NovaTiersSource(HttpClient client, String baseUrl) {
        this(client, baseUrl, new DownloadProgress());
    }

    public NovaTiersSource(HttpClient client, String baseUrl, DownloadProgress progress) {
        this.client = client;
        this.baseUrl = JustTiers.trimTrailingSlash(baseUrl);
        this.progress = progress;
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
        if (usableIndex() == null) {
            index = loadIndex(null);
        }
        return index;
    }

    /**
     * The cached index, or {@code null} when there is none worth keeping. A download still
     * in flight counts as usable so that callers join it instead of starting a second one;
     * one that already failed does not, so the next lookup retries rather than replaying
     * the old error forever.
     */
    private CompletableFuture<Map<UUID, Map<String, Tier>>> usableIndex() {
        return index == null || index.isCompletedExceptionally() ? null : index;
    }

    /**
     * Downloads the list again. A failed refresh keeps the index we already have rather
     * than replacing it with nothing, so a site outage cannot blank every NovaTiers badge
     * until the next successful refresh. The returned future never fails; it only signals
     * that the attempt has finished.
     */
    public synchronized CompletableFuture<Void> refresh() {
        index = loadIndex(usableIndex());
        return index.handle((idx, error) -> null);
    }

    private CompletableFuture<Map<UUID, Map<String, Tier>>> loadIndex(
            CompletableFuture<Map<UUID, Map<String, Tier>>> previous) {
        CompletableFuture<Map<UUID, Map<String, Tier>>> fresh = download();
        if (previous == null) {
            // Nothing worth keeping yet, so let the failure surface to the caller.
            return fresh;
        }
        return fresh.exceptionallyCompose(error -> {
            JustTiers.LOGGER.warn("NovaTiers refresh failed, keeping {} indexed players: {}",
                    indexedPlayerCount, error.toString());
            return previous;
        });
    }


    private CompletableFuture<Map<UUID, Map<String, Tier>>> download() {
        HttpRequest request =
                JustTiers.jsonRequest(baseUrl + "/users", Duration.ofSeconds(30));

        long token = progress.started();
        return client.sendAsync(request,
                        new ProgressBodyHandler(bytes -> progress.advanced(token, bytes)))
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new TierLookupException(
                                "NovaTiers returned HTTP " + response.statusCode());
                    }
                    Map<UUID, Map<String, Tier>> parsed = NovaParser.parseUsers(response.body());
                    if (parsed.isEmpty() && response.body() != null && response.body().length() > 2) {
                        JustTiers.LOGGER.warn(
                                "NovaTiers answered HTTP 200 but nothing was understood; "
                                        + "the response schema may have changed");
                    }
                    JustTiers.LOGGER.info("Indexed {} NovaTiers players", parsed.size());
                    indexedPlayerCount = parsed.size();
                    return parsed;
                })
                // whenComplete passes the result and the failure straight through, so the
                // caller's error handling - including loadIndex keeping the previous index -
                // is untouched.
                .whenComplete((parsed, error) -> {
                    if (error != null) {
                        progress.failed(token);
                    } else {
                        progress.finished(token);
                    }
                });
    }
}
