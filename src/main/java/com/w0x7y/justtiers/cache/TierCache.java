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
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Caches per-player tier lookups for every site. {@link #peek} never blocks, so it is
 * safe to call from the render thread; a miss schedules a background fetch and reports
 * "not yet known" until it lands.
 *
 * <p>An answer is trusted for {@link CachePolicy#ttl}, after which it is fetched again.
 * That applies to "unranked" as much as to a tier: a player tested during your session
 * would otherwise read as untested until you restarted, and one who ranks up would keep
 * their old tier just as long.
 *
 * <p>Failures are held off twice over. Each player's own retry delay grows with each
 * consecutive failure ({@link Backoff}), and each site has a {@link SiteGate} that stops
 * it being asked at all once enough lookups in a row have failed — a per-player delay
 * alone still means one request per player per period, which for a full lobby is exactly
 * the flood a failing site does not need.
 */
public final class TierCache {

    private final Map<Source, TierSource> sources = new EnumMap<>(Source.class);
    private final Map<Source, Map<UUID, Entry>> entries = new EnumMap<>(Source.class);
    private final Map<Source, Map<UUID, Attempt>> attempts = new EnumMap<>(Source.class);
    private final Map<Source, SiteGate> gates = new EnumMap<>(Source.class);
    private final Map<Source, SiteHealth> health = new EnumMap<>(Source.class);

    private volatile CachePolicy policy;
    private final Backoff backoff;
    private final LongSupplier clock;
    private final DoubleSupplier random;

    /** One cached lookup, and when it finished — which is what a TTL is measured from. */
    private static final class Entry {
        private final CompletableFuture<Map<String, Tier>> future;
        private volatile boolean settled;
        private volatile long settledAtNanos;

        private Entry(CompletableFuture<Map<String, Tier>> future) {
            this.future = future;
        }

        private void settle(long nanos) {
            settledAtNanos = nanos;
            settled = true;
        }
    }

    /** One player's run of failures on one site, and when they may be tried again. */
    private record Attempt(int failures, long retryAtNanos) {
    }

    public TierCache(List<TierSource> sources) {
        this(sources, CachePolicy.DEFAULT);
    }

    /** Retained for callers that only care about the first retry delay. */
    public TierCache(List<TierSource> sources, Duration retryDelay) {
        this(sources, CachePolicy.DEFAULT.withBaseRetry(retryDelay));
    }

    public TierCache(List<TierSource> sources, CachePolicy policy) {
        this(sources, policy, System::nanoTime, () -> ThreadLocalRandom.current().nextDouble());
    }

    /**
     * The clock and the randomness are injected so that expiry, growing retry delays and
     * jitter can be tested without waiting for real time to pass.
     */
    public TierCache(List<TierSource> sources, CachePolicy policy,
                     LongSupplier clock, DoubleSupplier random) {
        this.policy = policy;
        this.backoff = policy.backoff();
        this.clock = clock;
        this.random = random;
        for (TierSource source : sources) {
            this.sources.put(source.source(), source);
        }
        for (Source source : Source.ALL) {
            this.entries.put(source, new ConcurrentHashMap<>());
            this.attempts.put(source, new ConcurrentHashMap<>());
            this.gates.put(source, new SiteGate(policy.siteFailureThreshold(),
                    policy.basePause(), policy.maxPause(), clock));
            this.health.put(source, new SiteHealth(clock));
        }
    }

    /**
     * Changes how long an answer is trusted, without discarding anything already cached.
     * The setting is a slider on the config screen, and a user who nudges it should not
     * pay for it with every badge on screen disappearing while they are re-fetched.
     */
    public void setTtl(Duration ttl) {
        this.policy = policy.withTtl(ttl);
    }

    /**
     * @return the player's tiers if already loaded and still fresh (an empty map means
     *         "known unranked"), or {@link Optional#empty()} if a lookup is still in
     *         flight or is waiting out the delay after a failure. A failed lookup is
     *         never reported as "unranked", so a site being down does not blank a player
     *         until the next refresh.
     */
    public Optional<Map<String, Tier>> peek(Source source, UUID uuid) {
        if (!sources.containsKey(source)) {
            return Optional.of(Map.of());
        }
        Entry entry = entries.get(source).get(uuid);
        if (entry != null && entry.future.isCompletedExceptionally()) {
            // Drop the failure as soon as it is seen, so the entry does not sit there
            // being replayed. What stops the retry going out on the very next frame is
            // the delay below, not this entry's presence.
            entries.get(source).remove(uuid, entry);
            return Optional.empty();
        }
        if (entry != null && isStale(entry)) {
            entries.get(source).remove(uuid, entry);
            entry = null;
        }
        if (entry != null) {
            return entry.future.isDone()
                    ? Optional.ofNullable(entry.future.getNow(null))
                    : Optional.empty();
        }

        // Nothing cached, so this would start a fetch. That is what the retry delay and
        // the site gate are allowed to refuse.
        Attempt attempt = attempts.get(source).get(uuid);
        if (attempt != null && clock.getAsLong() - attempt.retryAtNanos() < 0) {
            return Optional.empty();
        }

        CompletableFuture<Map<String, Tier>> future = load(source, uuid);
        if (!future.isDone() || future.isCompletedExceptionally()) {
            return Optional.empty();
        }
        return Optional.ofNullable(future.getNow(null));
    }

    /**
     * Starts (or joins) a lookup and returns its future. A lookup that succeeds also
     * ends the backoff an earlier failure left behind: the site has just answered for
     * this player, so {@link #peek} must not go on reporting "not yet known" — and blank
     * the badge — for the rest of a delay the answer has already settled.
     *
     * <p>A site whose gate is closed fails immediately without being asked. That is the
     * truth of the situation and it is what callers already draw as "site unavailable";
     * pretending otherwise would mean a lobby scan waving two hundred requests past the
     * very thing holding them back.
     */
    public CompletableFuture<Map<String, Tier>> load(Source source, UUID uuid) {
        TierSource tierSource = sources.get(source);
        if (tierSource == null) {
            return CompletableFuture.completedFuture(Map.of());
        }

        Entry existing = entries.get(source).get(uuid);
        if (existing != null && isStale(existing)) {
            entries.get(source).remove(uuid, existing);
        }
        Entry cached = entries.get(source).get(uuid);
        if (cached != null) {
            return cached.future;
        }
        if (!gates.get(source).allowRequest()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException(source.displayName() + " is not being asked "
                            + "right now: too many failures in a row"));
        }
        return entries.get(source).computeIfAbsent(uuid, fetch(source, tierSource)).future;
    }

    private Function<UUID, Entry> fetch(Source source, TierSource tierSource) {
        return key -> {
            // Timed from here rather than from inside the source: what a user waits on is
            // the whole round trip, including whatever queueing the source does before the
            // request goes out.
            long startedAtNanos = clock.getAsLong();
            Entry entry = new Entry(tierSource.fetch(key));
            entry.future.whenComplete((tiers, error) -> {
                long settledAtNanos = clock.getAsLong();
                entry.settle(settledAtNanos);
                long latencyNanos = settledAtNanos - startedAtNanos;
                if (error == null) {
                    attempts.get(source).remove(key);
                    gates.get(source).recordSuccess();
                    health.get(source).recordSuccess(latencyNanos);
                } else {
                    attempts.get(source).compute(key, (ignored, previous) -> {
                        int failures = previous == null ? 1 : previous.failures() + 1;
                        return new Attempt(failures,
                                clock.getAsLong() + backoff.delayAfter(failures, random));
                    });
                    gates.get(source).recordFailure();
                    health.get(source).recordFailure(latencyNanos, error);
                }
            });
            return entry;
        };
    }

    private boolean isStale(Entry entry) {
        CachePolicy current = policy;
        return current.expires() && entry.settled
                && clock.getAsLong() - entry.settledAtNanos >= current.ttl().toNanos();
    }

    /**
     * Drops one player's failed entry for a site so the next attempt goes out again.
     * {@link #peek} does this for itself, behind a retry delay, because it runs every
     * frame; {@link #load} cannot, so a caller that waits on a load has to say when a
     * failure is finished with. A successful entry, and one still in flight, are both
     * left alone.
     */
    public void forgetFailed(Source source, UUID uuid) {
        Map<UUID, Entry> entriesForSource = entries.get(source);
        Entry entry = entriesForSource.get(uuid);
        if (entry != null && entry.future.isCompletedExceptionally()) {
            entriesForSource.remove(uuid, entry);
        }
    }

    /**
     * What this site has answered this session, for {@code /justtiers debug}. Deliberately
     * survives {@link #invalidate}: clearing the cache is a user asking to fetch again, not
     * a claim that the failures before it never happened — and the run-up to a refresh is
     * usually the interesting half of a bug report.
     */
    public SiteHealth.Snapshot health(Source source) {
        return health.get(source).snapshot();
    }

    /** Whether this site is currently being asked at all, and if not, for how much longer. */
    public SiteGate.Status gateStatus(Source source) {
        return gates.get(source).status();
    }

    /** Players this site holds an answer for, including lookups still in flight. */
    public int cachedPlayers(Source source) {
        return entries.get(source).size();
    }

    /** Of those, the ones that have not come back yet. */
    public int pendingLookups(Source source) {
        int pending = 0;
        for (Entry entry : entries.get(source).values()) {
            if (!entry.future.isDone()) {
                pending++;
            }
        }
        return pending;
    }

    /**
     * Players whose next attempt at this site is still waiting out a retry delay. A high
     * count next to a healthy gate is the signature of failures spread thinly enough to
     * never trip it.
     */
    public int playersAwaitingRetry(Source source) {
        long now = clock.getAsLong();
        int waiting = 0;
        for (Attempt attempt : attempts.get(source).values()) {
            if (now - attempt.retryAtNanos() < 0) {
                waiting++;
            }
        }
        return waiting;
    }

    public void invalidateAll() {
        entries.values().forEach(Map::clear);
        attempts.values().forEach(Map::clear);
        Source.ALL.forEach(source -> gates.get(source).recordSuccess());
    }

    /** Clears cached entries for a single source, leaving every other source's cache intact. */
    public void invalidate(Source source) {
        entries.get(source).clear();
        attempts.get(source).clear();
        // A manual refresh is the user saying "try again now", which includes a site the
        // gate had given up on.
        gates.get(source).recordSuccess();
    }
}
