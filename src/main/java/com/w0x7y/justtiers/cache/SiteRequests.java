package com.w0x7y.justtiers.cache;

import com.w0x7y.justtiers.api.RetryAfterException;
import com.w0x7y.justtiers.api.TierSource;
import com.w0x7y.justtiers.tier.Tier;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Owns one site's retained answers, atomic request admission, and generation replacement. */
final class SiteRequests {
    private static final int MAX_PLAYERS = 4096;
    private static final Duration STALE_GRACE = Duration.ofHours(6);
    private static final long IDLE_NANOS = Duration.ofHours(24).toNanos();
    private static final long DEFERRED_RETRY_NANOS = Duration.ofSeconds(5).toNanos();

    private final TierSource source;
    private final Supplier<CachePolicy> policy;
    private final Backoff backoff;
    private final LongSupplier clock;
    private final DoubleSupplier random;
    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
    private final Map<UUID, Attempt> attempts = new ConcurrentHashMap<>();
    private final RequestQueue<Entry, TierCache.CachedAnswer> queue = new RequestQueue<>(this, 4, 128);
    private final SiteGate gate;
    private final SiteHealth health;
    private long generation;
    private long revision;
    private volatile long cooldownUntil;
    private volatile boolean coolingDown;

    private record Answer(Map<String, Tier> tiers, long atNanos, long revision) { }
    private record Attempt(int failures, long retryAtNanos) { }
    private record Request(Entry entry, boolean cached) { }

    private static final class Entry {
        final CompletableFuture<TierCache.CachedAnswer> future = new CompletableFuture<>();
        final CompletableFuture<Map<String, Tier>> tiers = future.thenApply(TierCache.CachedAnswer::tiers);
        final long generation;
        volatile Answer answer;
        volatile long accessedAt;
        volatile long checkedAtNanos;
        volatile boolean forceRefresh;
        volatile boolean retryable;
        volatile boolean refreshFailed;
        Entry(long generation, long now, Answer answer) {
            this.generation = generation;
            this.accessedAt = now;
            this.answer = answer;
        }
    }

    SiteRequests(TierSource source, Supplier<CachePolicy> policy, LongSupplier clock, DoubleSupplier random) {
        this.source = source;
        this.policy = policy;
        this.backoff = policy.get().backoff();
        this.clock = clock;
        this.random = random;
        CachePolicy initial = policy.get();
        gate = new SiteGate(initial.siteFailureThreshold(), initial.basePause(), initial.maxPause(), clock);
        health = new SiteHealth(clock);
    }

    Optional<Map<String, Tier>> peek(UUID uuid) {
        if (source == null) return Optional.of(Map.of());
        Entry entry = entries.get(uuid);
        if (entry != null) {
            entry.accessedAt = clock.getAsLong();
            if (entry.future.isCompletedExceptionally() && !entry.retryable) {
                entry.retryable = true;
                return visibleAnswer(entry);
            }
            if (!entry.future.isDone() || (!entry.future.isCompletedExceptionally() && !needsRefresh(entry))) {
                return visibleAnswer(entry);
            }
        }
        Attempt attempt = attempts.get(uuid);
        if (attempt == null || clock.getAsLong() - attempt.retryAtNanos() >= 0) request(uuid, false);
        return visibleAnswer(entries.get(uuid));
    }

    CompletableFuture<Map<String, Tier>> load(UUID uuid) { return request(uuid, true).entry().tiers; }

    CompletableFuture<TierCache.CachedAnswer> loadAnswer(UUID uuid) {
        Request request = request(uuid, true);
        return request.cached() ? CompletableFuture.completedFuture(snapshot(request.entry(), false)) : request.entry().future;
    }

    private Request request(UUID uuid, boolean explicit) {
        if (source == null) {
            Entry empty = new Entry(0, clock.getAsLong(), null);
            empty.future.complete(snapshot(new Answer(Map.of(), clock.getAsLong(), 0), false, false, false, false));
            return new Request(empty, false);
        }
        Entry entry;
        RequestQueue.Submission<TierCache.CachedAnswer> submission;
        synchronized (this) {
            Entry existing = entries.get(uuid);
            if (existing != null) {
                existing.accessedAt = clock.getAsLong();
                if (!existing.future.isDone()) {
                    if (explicit) queue.promote(existing);
                    return new Request(existing, false);
                }
                if (existing.future.isCompletedExceptionally() && !existing.retryable) return new Request(existing, false);
                if (!existing.future.isCompletedExceptionally() && !needsRefresh(existing)) return new Request(existing, true);
            }
            if (coolingDown()) return deferred("Site requested a cooldown");
            SiteGate.Status status = gate.status();
            if (status.closed() && (status.probing() || status.reopensInNanos() > 0)) {
                return deferred("Site is paused after repeated failures");
            }
            if (existing == null && !makeRoom()) return deferred("Player cache is busy");
            entry = new Entry(generation, clock.getAsLong(), existing == null ? null : existing.answer);
            entry.forceRefresh = existing != null && existing.forceRefresh;
            entry.refreshFailed = existing != null && existing.refreshFailed;
            entries.put(uuid, entry);
            // Claim, priority, and insertion share this owner's critical section.
            submission = queue.enqueue(entry, explicit, () -> fetch(entry, uuid));
        }
        submission.result.whenComplete((answer, failure) -> {
            if (failure != null) {
                Throwable error = unwrap(failure);
                synchronized (this) {
                    if (error instanceof RequestQueue.Deferred && current(uuid, entry)) {
                        rememberAttempt(uuid, new Attempt(0, clock.getAsLong() + DEFERRED_RETRY_NANOS));
                    }
                }
                entry.future.completeExceptionally(error);
            } else entry.future.complete(answer);
        });
        queue.dispatch(submission);
        return new Request(entry, false);
    }

    private Request deferred(String message) {
        Entry entry = new Entry(generation, clock.getAsLong(), null);
        entry.future.completeExceptionally(new RequestQueue.Deferred(message));
        return new Request(entry, false);
    }

    private CompletableFuture<TierCache.CachedAnswer> fetch(Entry entry, UUID uuid) {
        synchronized (this) {
            if (!current(uuid, entry)) return CompletableFuture.failedFuture(new RequestQueue.Deferred("Lookup was invalidated"));
            if (coolingDown() || !gate.allowRequest()) {
                return CompletableFuture.failedFuture(new RequestQueue.Deferred("Site is paused"));
            }
        }
        long started = clock.getAsLong();
        CompletableFuture<TierSource.Answer> response;
        try { response = java.util.Objects.requireNonNull(source.fetchAnswer(uuid)); }
        catch (RuntimeException error) { response = CompletableFuture.failedFuture(error); }
        return response.thenApply(answer -> {
            CachePolicy currentPolicy = policy.get();
            if (currentPolicy.expires() && answer.age().compareTo(currentPolicy.ttl().plus(STALE_GRACE)) >= 0) {
                throw new com.w0x7y.justtiers.api.TierLookupException("Source snapshot is too old to display");
            }
            return answer;
        }).handle((answer, failure) -> {
            long settled = clock.getAsLong();
            Throwable error = failure == null ? null : unwrap(failure);
            Answer completed = null;
            synchronized (this) {
                if (error == null) completed = new Answer(answer.tiers(), settled - answer.age().toNanos(), ++revision);
                if (current(uuid, entry)) {
                    if (error == null) {
                        entry.answer = completed;
                        entry.checkedAtNanos = settled;
                        entry.forceRefresh = false;
                        entry.refreshFailed = answer.refreshFailed();
                        attempts.remove(uuid);
                        gate.recordSuccess();
                    } else {
                        entry.refreshFailed = true;
                        Attempt previous = attempts.get(uuid);
                        int failures = previous == null ? 1 : Math.min(Integer.MAX_VALUE - 1, previous.failures()) + 1;
                        rememberAttempt(uuid, new Attempt(failures, settled + backoff.delayAfter(failures, random)));
                        gate.recordFailure();
                        if (error instanceof RetryAfterException retry) {
                            long until = settled + retry.delay().toNanos();
                            if (!coolingDown() || until - cooldownUntil > 0) cooldownUntil = until;
                            coolingDown = true;
                        }
                    }
                }
                // Obsolete completions contribute history but cannot change operational state.
                if (error == null) health.recordSuccess(settled - started);
                else health.recordFailure(settled - started, error);
            }
            if (error != null) throw new CompletionException(error);
            TierSource.RefreshState state = source.refreshState();
            return snapshot(completed, false, answer.refreshFailed(), state.pending(), state.failed());
        });
    }

    private boolean current(UUID uuid, Entry entry) {
        return generation == entry.generation && entries.get(uuid) == entry;
    }

    private static Throwable unwrap(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null) error = error.getCause();
        return error;
    }

    private boolean coolingDown() {
        return coolingDown && clock.getAsLong() - cooldownUntil < 0;
    }

    private boolean needsRefresh(Entry entry) {
        CachePolicy currentPolicy = policy.get();
        return entry.forceRefresh || entry.answer == null
                || (currentPolicy.expires() && clock.getAsLong() - entry.checkedAtNanos >= currentPolicy.ttl().toNanos());
    }

    private Optional<Map<String, Tier>> visibleAnswer(Entry entry) {
        if (entry == null) return Optional.empty();
        Answer answer = entry.answer;
        if (answer == null) return Optional.empty();
        CachePolicy currentPolicy = policy.get();
        if (currentPolicy.expires() && clock.getAsLong() - answer.atNanos() >= currentPolicy.ttl().plus(STALE_GRACE).toNanos()) {
            return Optional.empty();
        }
        return Optional.of(answer.tiers());
    }

    Optional<TierCache.CachedAnswer> cachedAnswer(UUID uuid) {
        Entry entry = entries.get(uuid);
        return visibleAnswer(entry).map(tiers -> snapshot(entry, !entry.future.isDone()));
    }

    private TierCache.CachedAnswer snapshot(Entry entry, boolean pending) {
        TierSource.RefreshState state = source == null ? TierSource.RefreshState.IDLE : source.refreshState();
        return snapshot(entry.answer, entry.forceRefresh, entry.refreshFailed, pending || state.pending(), state.failed());
    }

    private TierCache.CachedAnswer snapshot(Answer answer, boolean forced, boolean failed, boolean pending, boolean sourceFailed) {
        CachePolicy currentPolicy = policy.get();
        Duration age = Duration.ofNanos(Math.max(0, clock.getAsLong() - answer.atNanos()));
        Optional<Duration> freshFor = currentPolicy.expires()
                ? Optional.of(age.compareTo(currentPolicy.ttl()) >= 0 ? Duration.ZERO : currentPolicy.ttl().minus(age))
                : Optional.empty();
        boolean stale = forced || failed || sourceFailed || (freshFor.isPresent() && freshFor.get().isZero());
        return new TierCache.CachedAnswer(answer.tiers(), age, stale, pending, failed || sourceFailed, freshFor, answer.revision());
    }

    void forgetFailed(UUID uuid) {
        synchronized (this) {
            Entry entry = entries.get(uuid);
            if (entry != null && entry.future.isCompletedExceptionally()) {
                if (entry.answer == null) entries.remove(uuid, entry);
                else entry.retryable = true;
            }
        }
    }

    void reset(boolean retainAnswers) {
        List<CompletableFuture<TierCache.CachedAnswer>> cancelled;
        synchronized (this) {
            long next = ++generation;
            if (retainAnswers) {
                entries.replaceAll((uuid, old) -> {
                    Entry replacement = new Entry(next, old.accessedAt, old.answer);
                    replacement.forceRefresh = true;
                    if (old.answer != null) {
                        replacement.future.complete(snapshot(old.answer, true, false, false, false));
                    }
                    return replacement;
                });
                entries.entrySet().removeIf(item -> item.getValue().answer == null);
            } else entries.clear();
            attempts.clear();
            gate.recordSuccess();
            // Manual refresh preserves a server's Retry-After cooldown.
            cancelled = queue.removeQueued(entry -> entry.generation < next);
        }
        for (var future : cancelled) future.completeExceptionally(new RequestQueue.Deferred("Lookup was invalidated"));
    }

    void maintain() {
        long now = clock.getAsLong();
        synchronized (this) {
            entries.entrySet().removeIf(item -> {
                Entry entry = item.getValue();
                return entry.future.isDone() && (now - entry.accessedAt >= IDLE_NANOS
                        || (visibleAnswer(entry).isEmpty() && now - entry.accessedAt >= Duration.ofHours(1).toNanos()));
            });
            attempts.entrySet().removeIf(item -> !entries.containsKey(item.getKey())
                    && now - item.getValue().retryAtNanos() >= Duration.ofHours(1).toNanos());
        }
    }

    private boolean makeRoom() {
        if (entries.size() < MAX_PLAYERS) return true;
        var oldest = entries.entrySet().stream().filter(item -> item.getValue().future.isDone())
                .min(java.util.Comparator.comparingLong(item -> item.getValue().accessedAt));
        oldest.ifPresent(item -> {
            entries.remove(item.getKey(), item.getValue());
            attempts.remove(item.getKey());
        });
        return entries.size() < MAX_PLAYERS;
    }

    private void rememberAttempt(UUID uuid, Attempt attempt) {
        if (attempts.size() >= MAX_PLAYERS && !attempts.containsKey(uuid)) {
            attempts.entrySet().stream().min(java.util.Comparator.comparingLong(item -> item.getValue().retryAtNanos()))
                    .ifPresent(item -> attempts.remove(item.getKey(), item.getValue()));
        }
        attempts.put(uuid, attempt);
    }

    SiteHealth.Snapshot health() { return health.snapshot(); }
    SiteGate.Status gateStatus() { return gate.status(); }
    int cachedPlayers() { return entries.size(); }
    int pendingLookups() { return (int) entries.values().stream().filter(entry -> !entry.future.isDone()).count(); }
    int activeRequests() { return queue.active(); }
    int queuedRequests() { return queue.waiting(); }
    Duration cooldownRemaining() {
        Duration local = Duration.ofNanos(coolingDown() ? Math.max(0, cooldownUntil - clock.getAsLong()) : 0);
        Duration remote = source == null ? Duration.ZERO : source.refreshState().cooldown();
        return local.compareTo(remote) >= 0 ? local : remote;
    }
    int playersAwaitingRetry() {
        long now = clock.getAsLong();
        return (int) attempts.values().stream().filter(attempt -> now - attempt.retryAtNanos() < 0).count();
    }
}
