package com.w0x7y.justtiers.cache;

import com.w0x7y.justtiers.api.TierSource;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

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

    private final Map<Source, TierSource> sources = new EnumMap<>(Source.class);
    private final Map<Source, Map<UUID, CompletableFuture<Map<String, Tier>>>> entries =
            new EnumMap<>(Source.class);

    public TierCache(List<TierSource> sources) {
        for (TierSource source : sources) {
            this.sources.put(source.source(), source);
        }
        for (Source source : Source.values()) {
            this.entries.put(source, new ConcurrentHashMap<>());
        }
    }

    /**
     * @return the player's tiers if already loaded (an empty map means "known unranked"),
     *         or {@link Optional#empty()} if a lookup is still in flight.
     */
    public Optional<Map<String, Tier>> peek(Source source, UUID uuid) {
        if (!sources.containsKey(source)) {
            return Optional.of(Map.of());
        }
        CompletableFuture<Map<String, Tier>> future = load(source, uuid);
        if (!future.isDone()) {
            return Optional.empty();
        }
        if (future.isCompletedExceptionally()) {
            // Drop the failure and start a fresh attempt, rather than caching it forever.
            entries.get(source).remove(uuid, future);
            load(source, uuid);
            return Optional.empty();
        }
        return Optional.ofNullable(future.getNow(null));
    }

    /** Starts (or joins) a lookup and returns its future. */
    public CompletableFuture<Map<String, Tier>> load(Source source, UUID uuid) {
        TierSource tierSource = sources.get(source);
        if (tierSource == null) {
            return CompletableFuture.completedFuture(Map.of());
        }
        return entries.get(source).computeIfAbsent(uuid, tierSource::fetch);
    }

    public void invalidateAll() {
        entries.values().forEach(Map::clear);
    }

    /** Clears cached entries for a single source, leaving every other source's cache intact. */
    public void invalidate(Source source) {
        entries.get(source).clear();
    }
}
