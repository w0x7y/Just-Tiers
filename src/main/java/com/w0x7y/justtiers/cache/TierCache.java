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
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

/** Nonblocking player answers, bounded site work and retries, with last-success retention. */
public final class TierCache {
    private final Map<Source, SiteRequests> sites = new EnumMap<>(Source.class);
    private volatile CachePolicy policy;

    /** Immutable placements and freshness belonging to one successful source response. */
    public record CachedAnswer(Map<String, Tier> tiers, Duration age, boolean stale,
                               boolean refreshing, boolean refreshFailed, Optional<Duration> freshFor, long revision) {
        public CachedAnswer { tiers = Map.copyOf(tiers); }
    }

    /**
     * One player's entry as a nonblocking reader sees it. A visible answer can be retained
     * while its replacement is in flight. Without one, {@code inFlight} separates a request
     * that may still answer from a lookup that failed, was deferred or never started.
     */
    public record Observation(Optional<CachedAnswer> answer, boolean inFlight) {
        public static final Observation NONE = new Observation(Optional.empty(), false);
    }

    public TierCache(List<TierSource> sources) { this(sources, CachePolicy.DEFAULT); }
    public TierCache(List<TierSource> sources, CachePolicy policy) {
        this(sources, policy, System::nanoTime, () -> ThreadLocalRandom.current().nextDouble());
    }
    public TierCache(List<TierSource> sources, CachePolicy policy, LongSupplier clock, DoubleSupplier random) {
        this.policy = policy;
        Map<Source, TierSource> registered = new EnumMap<>(Source.class);
        for (TierSource source : sources) registered.put(source.source(), source);
        for (Source source : Source.ALL) {
            sites.put(source, new SiteRequests(registered.get(source), () -> this.policy, clock, random));
        }
    }

    public void setTtl(Duration ttl) { policy = policy.withTtl(ttl); }

    /** Last known placements remain visible during refresh, for a finite stale grace period. */
    public Optional<Map<String, Tier>> peek(Source source, UUID uuid) { return sites.get(source).peek(uuid); }

    /**
     * Explicit lookups share pending work and overtake queued nametag work. The completed
     * lookup carries its own response's age and revision, even after invalidation.
     */
    public CompletableFuture<CachedAnswer> loadAnswer(Source source, UUID uuid) { return sites.get(source).loadAnswer(uuid); }

    /**
     * Reads settlement and answer from one entry, without starting a request or consuming a
     * probe. The retained answer alone is {@code observe(source, uuid).answer()}.
     */
    public Observation observe(Source source, UUID uuid) { return sites.get(source).observe(uuid); }

    public void forgetFailed(Source source, UUID uuid) { sites.get(source).forgetFailed(uuid); }

    /** Manual refresh keeps successful placements but forces the next access to recheck. */
    public void refreshAll() { sites.values().forEach(site -> site.reset(true)); }
    public void invalidateAll() { sites.values().forEach(site -> site.reset(false)); }
    public void invalidate(Source source) { sites.get(source).reset(false); }

    /** Run periodically on the client tick, independent of whether departed players reappear. */
    public void maintain() { sites.values().forEach(SiteRequests::maintain); }

    public SiteHealth.Snapshot health(Source source) { return sites.get(source).health(); }
    public SiteGate.Status gateStatus(Source source) { return sites.get(source).gateStatus(); }
    public int cachedPlayers(Source source) { return sites.get(source).cachedPlayers(); }
    public int pendingLookups(Source source) { return sites.get(source).pendingLookups(); }
    public int activeRequests(Source source) { return sites.get(source).activeRequests(); }
    public int queuedRequests(Source source) { return sites.get(source).queuedRequests(); }
    public Duration cooldownRemaining(Source source) { return sites.get(source).cooldownRemaining(); }
    public int playersAwaitingRetry(Source source) { return sites.get(source).playersAwaitingRetry(); }
}
