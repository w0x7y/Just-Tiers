package com.w0x7y.justtiers.api;

import com.w0x7y.justtiers.JustTiers;
import com.w0x7y.justtiers.download.DownloadProgress;
import com.w0x7y.justtiers.download.ProgressBodyHandler;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;

/**
 * NovaTiers exposes only a bulk {@code /users} array (~6.5k players, ~1.7 MB), so the
 * entire list is downloaded once and held as a UUID index. Call {@link #refresh()}
 * periodically to pick up new placements.
 */
public final class NovaTiersSource implements TierSource {

    private final HttpClient client;
    private final String baseUrl;
    private final DownloadProgress progress;
    private final LongSupplier nanoTime;
    private long cooldownUntilNanos;
    private boolean cooldownSet;

    private record PublishedIndex(NovaParser.ParsedIndex snapshot, long receivedAtNanos) { }

    private volatile PublishedIndex publishedIndex;
    private volatile PublishedIndex failedRefreshIndex;
    private CompletableFuture<PublishedIndex> indexDownload;
    private CompletableFuture<Void> refreshInFlight;

    public NovaTiersSource(HttpClient client, String baseUrl) {
        this(client, baseUrl, new DownloadProgress());
    }

    public NovaTiersSource(HttpClient client, String baseUrl, DownloadProgress progress) {
        this(client, baseUrl, progress, System::nanoTime);
    }

    public NovaTiersSource(HttpClient client, String baseUrl, DownloadProgress progress, LongSupplier nanoTime) {
        this.client = client;
        this.baseUrl = JustTiers.trimTrailingSlash(baseUrl);
        this.progress = progress;
        this.nanoTime = nanoTime;
    }

    @Override
    public Source source() {
        return Source.NOVATIERS;
    }

    @Override
    public CompletableFuture<Map<String, Tier>> fetch(UUID uuid) {
        return fetchAnswer(uuid).thenApply(Answer::tiers);
    }

    @Override
    public CompletableFuture<Answer> fetchAnswer(UUID uuid) {
        return ensureLoaded().thenApply(published -> {
            NovaParser.ParsedIndex snapshot = published.snapshot();
            if (snapshot.rejectedPlayers().contains(uuid)) {
                throw new TierLookupException("NovaTiers contained malformed or conflicting data for " + uuid);
            }
            Duration age = Duration.ofNanos(Math.max(0, nanoTime.getAsLong() - published.receivedAtNanos()));
            return new Answer(snapshot.users().getOrDefault(uuid, Map.of()), age,
                    failedRefreshIndex == published);
        });
    }

    @Override
    public synchronized RefreshState refreshState() {
        PublishedIndex published = publishedIndex;
        long remaining = cooldownSet ? Math.max(0, cooldownUntilNanos - nanoTime.getAsLong()) : 0;
        return new RefreshState(refreshInFlight != null && !refreshInFlight.isDone(),
                published != null && failedRefreshIndex == published, Duration.ofNanos(remaining));
    }

    /** Number of players currently indexed. Useful for logging and the refresh command. */
    public int indexedPlayerCount() {
        PublishedIndex published = publishedIndex;
        return published == null ? 0 : published.snapshot().users().size();
    }

    private synchronized CompletableFuture<PublishedIndex> ensureLoaded() {
        if (publishedIndex != null) {
            return CompletableFuture.completedFuture(publishedIndex);
        }
        if (indexDownload == null || indexDownload.isCompletedExceptionally()) {
            startDownload();
        }
        return indexDownload;
    }

    /**
     * Downloads the list again. A failed refresh keeps the index we already have rather
     * than replacing it with nothing, so a site outage cannot blank every NovaTiers badge
     * until the next successful refresh. Concurrent callers share the current download.
     * The returned future reports its failure even when lookups retain the old index,
     * so callers can show an honest refresh result and avoid invalidating good cache data.
     */
    public synchronized CompletableFuture<Void> refresh() {
        if (refreshInFlight != null && !refreshInFlight.isDone()) {
            return refreshInFlight;
        }
        startDownload();
        return refreshInFlight;
    }

    private void startDownload() {
        long remaining = cooldownSet ? cooldownUntilNanos - nanoTime.getAsLong() : 0;
        if (remaining > 0) {
            indexDownload = CompletableFuture.failedFuture(new RetryAfterException(
                    "NovaTiers bulk download is paused by Retry-After", Duration.ofNanos(remaining)));
            refreshInFlight = indexDownload.thenApply(ignored -> null);
            return;
        }
        indexDownload = download().thenApply(parsed -> {
            // Publish only a validated immutable result; pending refreshes do not hide it.
            PublishedIndex published = new PublishedIndex(parsed, nanoTime.getAsLong());
            publishedIndex = published;
            return published;
        });
        // Initial fetches and explicit refreshes share this same download and its failure.
        refreshInFlight = indexDownload.thenApply(ignored -> null);
    }

    private CompletableFuture<NovaParser.ParsedIndex> download() {
        HttpRequest request =
                JustTiers.jsonRequest(baseUrl + "/users", Duration.ofSeconds(30));

        long token = progress.started();
        return client.sendAsync(request,
                        new ProgressBodyHandler(bytes -> progress.advanced(token, bytes)))
                .thenApply(response -> {
                    if (response.statusCode() == 429 || response.statusCode() == 503) {
                        Duration delay = RetryAfter.parse(response.headers().firstValue("Retry-After").orElse(null),
                                Instant.now());
                        recordCooldown(delay);
                        throw new RetryAfterException("NovaTiers returned HTTP " + response.statusCode(), delay);
                    }
                    if (response.statusCode() != 200) {
                        throw new TierLookupException(
                                "NovaTiers returned HTTP " + response.statusCode());
                    }
                    String body = response.body();
                    NovaParser.ParsedIndex parsed = NovaParser.parseUsersDetailed(body);
                    JustTiers.LOGGER.info("Indexed {} NovaTiers players, {} rejected players and {} unknown gamemode placements",
                            parsed.users().size(), parsed.rejectedPlayers().size(), parsed.unknownGamemodes());
                    return parsed;
                })
                .whenComplete((parsed, error) -> {
                    if (error != null) {
                        progress.failed(token);
                        failedRefreshIndex = publishedIndex;
                        if (publishedIndex != null) {
                            JustTiers.LOGGER.warn("NovaTiers refresh failed, keeping {} indexed players: {}",
                                    indexedPlayerCount(), error.toString());
                        }
                    } else {
                        progress.finished(token);
                    }
                });
    }

    private synchronized void recordCooldown(Duration delay) {
        long now = nanoTime.getAsLong();
        long previous = cooldownSet ? Math.max(0, cooldownUntilNanos - now) : 0;
        cooldownUntilNanos = now + Math.max(previous, delay.toNanos());
        cooldownSet = true;
    }
}
