package com.w0x7y.justtiers.cache;

import com.w0x7y.justtiers.api.TierSource;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches per-player tier lookups for every site. {@link #peek} never blocks, so it is
 * safe to call from the render thread; a miss schedules a background fetch and reports
 * "not yet known" until it lands.
 */
public final class TierCache {

    /** How long a failed lookup is left alone before it is attempted again. */
    private static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(60);

    private final Map<Source, TierSource> sources = new EnumMap<>(Source.class);
    private final Map<Source, Map<UUID, CompletableFuture<Map<String, Tier>>>> entries =
            new EnumMap<>(Source.class);
    private final Map<Source, Map<UUID, Long>> retryAfter = new EnumMap<>(Source.class);
    private final long retryDelayNanos;

    public TierCache(List<TierSource> sources) {
        this(sources, DEFAULT_RETRY_DELAY);
    }

    public TierCache(List<TierSource> sources, Duration retryDelay) {
        this.retryDelayNanos = retryDelay.toNanos();
        for (TierSource source : sources) {
            this.sources.put(source.source(), source);
        }
        for (Source source : Source.ALL) {
            this.entries.put(source, new ConcurrentHashMap<>());
            this.retryAfter.put(source, new ConcurrentHashMap<>());
        }
    }

    /**
     * @return the player's tiers if already loaded (an empty map means "known unranked"),
     *         or {@link Optional#empty()} if a lookup is still in flight or is waiting out
     *         the retry delay after a failure. A failed lookup is never reported as
     *         "unranked", so a site being down does not blank a player until the next
     *         refresh.
     */
    public Optional<Map<String, Tier>> peek(Source source, UUID uuid) {
        if (!sources.containsKey(source)) {
            return Optional.of(Map.of());
        }
        Long retryAt = retryAfter.get(source).get(uuid);
        if (retryAt != null) {
            if (System.nanoTime() - retryAt < 0) {
                return Optional.empty();
            }
            retryAfter.get(source).remove(uuid, retryAt);
        }
        CompletableFuture<Map<String, Tier>> future = load(source, uuid);
        if (!future.isDone()) {
            return Optional.empty();
        }
        if (future.isCompletedExceptionally()) {
            // Drop the failure and let it be retried, but not before the delay is up:
            // peek() runs every frame, so an immediate retry would hammer a failing site.
            entries.get(source).remove(uuid, future);
            retryAfter.get(source).put(uuid, System.nanoTime() + retryDelayNanos);
            return Optional.empty();
        }
        return Optional.ofNullable(future.getNow(null));
    }

    /**
     * Starts (or joins) a lookup and returns its future. A lookup that succeeds also
     * ends the backoff an earlier failure left behind: the site has just answered for
     * this player, so {@link #peek} must not go on reporting "not yet known" — and blank
     * the badge — for the rest of a delay the answer has already settled.
     */
    public CompletableFuture<Map<String, Tier>> load(Source source, UUID uuid) {
        TierSource tierSource = sources.get(source);
        if (tierSource == null) {
            return CompletableFuture.completedFuture(Map.of());
        }
        return entries.get(source).computeIfAbsent(uuid, key -> tierSource.fetch(key)
                .whenComplete((tiers, error) -> {
                    if (error == null) {
                        retryAfter.get(source).remove(key);
                    }
                }));
    }

    /**
     * Drops one player's failed entry for a site so the next attempt goes out again.
     * {@link #peek} does this for itself, behind a retry delay, because it runs every
     * frame; {@link #load} cannot, so a caller that waits on a load has to say when a
     * failure is finished with. A successful entry, and one still in flight, are both
     * left alone.
     */
    public void forgetFailed(Source source, UUID uuid) {
        Map<UUID, CompletableFuture<Map<String, Tier>>> entriesForSource = entries.get(source);
        CompletableFuture<Map<String, Tier>> entry = entriesForSource.get(uuid);
        if (entry != null && entry.isCompletedExceptionally()) {
            entriesForSource.remove(uuid, entry);
        }
    }

    public void invalidateAll() {
        entries.values().forEach(Map::clear);
        retryAfter.values().forEach(Map::clear);
    }

    /** Clears cached entries for a single source, leaving every other source's cache intact. */
    public void invalidate(Source source) {
        entries.get(source).clear();
        retryAfter.get(source).clear();
    }
}
