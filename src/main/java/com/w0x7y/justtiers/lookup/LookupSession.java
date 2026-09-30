package com.w0x7y.justtiers.lookup;

import com.w0x7y.justtiers.api.MojangNameSource;
import com.w0x7y.justtiers.api.PlayerRef;
import com.w0x7y.justtiers.cache.TierCache;
import com.w0x7y.justtiers.tier.Source;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

/**
 * One name lookup and its independently arriving site answers. The supplied executor
 * owns all published state; consumers read it on that same executor's thread.
 */
public final class LookupSession {
    public enum Error { INVALID_NAME, UNKNOWN_PLAYER, NAME_UNAVAILABLE }

    /** Online identities take precedence over a remote name lookup. */
    public interface Players {
        Optional<PlayerRef> online(String name);
        CompletableFuture<Optional<PlayerRef>> resolve(String name);
    }

    private final String requestedName;
    private final TierCache cache;
    private final Executor publisher;
    private record CapturedAnswer(TierCache.CachedAnswer answer, long capturedAt) { }
    private record SourceResult(Optional<LookupSection> section, boolean complete,
                                Optional<CapturedAnswer> answer, boolean refreshFailed) {
        static final SourceResult PENDING = new SourceResult(Optional.empty(), false, Optional.empty(), false);
    }
    private final Map<Source, SourceResult> results = new EnumMap<>(Source.class);
    private final LongSupplier clock;
    private final CompletableFuture<Optional<PlayerRef>> resolvedPlayer = new CompletableFuture<>();
    private PlayerRef player;
    private Error error;

    private LookupSession(String requestedName, TierCache cache, Executor publisher, LongSupplier clock) {
        this.requestedName = requestedName;
        this.cache = cache;
        this.publisher = publisher;
        this.clock = clock;
        Source.ALL.forEach(source -> results.put(source, SourceResult.PENDING));
    }

    public static LookupSession start(String name, Players players, TierCache cache, Executor publisher) {
        return start(name, players, cache, publisher, System::nanoTime);
    }

    static LookupSession start(String name, Players players, TierCache cache, Executor publisher,
                               LongSupplier clock) {
        LookupSession session = new LookupSession(name, cache, publisher, clock);
        publisher.execute(() -> session.resolve(players));
        return session;
    }

    private void resolve(Players players) {
        Optional<PlayerRef> online = players.online(requestedName);
        if (online.isPresent()) {
            begin(online.get());
        } else if (!MojangNameSource.isAskable(requestedName)) {
            fail(Error.INVALID_NAME);
        } else {
            players.resolve(requestedName).whenComplete((profile, failure) -> publisher.execute(() -> {
                if (failure != null) fail(Error.NAME_UNAVAILABLE);
                else if (profile.isEmpty()) fail(Error.UNKNOWN_PLAYER);
                else begin(profile.get());
            }));
        }
    }

    private void fail(Error reason) {
        error = reason;
        resolvedPlayer.complete(Optional.empty());
    }

    private void begin(PlayerRef found) {
        player = found;
        resolvedPlayer.complete(Optional.of(found));
        for (Source site : Source.ALL) {
            cache.cachedAnswer(site, found.uuid()).ifPresent(cached -> results.put(site,
                    answered(site, cached, clock.getAsLong(), false)));
            cache.loadAnswer(site, found.uuid()).whenComplete((answer, failure) -> {
                long capturedAt = clock.getAsLong();
                if (failure != null) {
                    // An offline player may never be peeked by nametags, so allow a new session to retry.
                    cache.forgetFailed(site, found.uuid());
                }
                publisher.execute(() -> {
                    if (failure == null) {
                        results.put(site, answered(site, answer, capturedAt, true));
                    } else {
                        SourceResult previous = results.get(site);
                        Optional<LookupSection> section = previous.answer().isPresent() ? previous.section()
                                : Optional.of(LookupReport.section(site, Optional.empty()));
                        results.put(site, new SourceResult(section, true, previous.answer(), true));
                    }
                });
            });
        }
    }

    private static SourceResult answered(Source site, TierCache.CachedAnswer answer, long capturedAt, boolean complete) {
        return new SourceResult(Optional.of(LookupReport.section(site, Optional.of(answer.tiers()))), complete,
                Optional.of(new CapturedAnswer(answer, capturedAt)), false);
    }

    public String name() {
        return player == null ? requestedName : player.name();
    }

    public Optional<Error> error() {
        return Optional.ofNullable(error);
    }

    /** Completes on the owning executor; presentation can then load a skin independently. */
    public CompletionStage<Optional<PlayerRef>> resolvedPlayer() {
        return resolvedPlayer.minimalCompletionStage();
    }

    /** Read placement and freshness together; a newer answer never rewrites this session's placements. */
    public LookupResult result(Source source) {
        SourceResult state = results.get(source);
        return new LookupResult(state.section(), state.complete(), state.answer().map(captured -> {
            TierCache.CachedAnswer answer = captured.answer();
            long elapsed = Math.max(0, clock.getAsLong() - captured.capturedAt());
            Duration age = answer.age().plusNanos(elapsed);
            boolean stale = answer.stale() || answer.refreshing() || answer.freshFor().map(limit -> Duration.ofNanos(elapsed).compareTo(limit) >= 0).orElse(false);
            // Transient progress belongs to a live revision, not a historical snapshot.
            // Once that revision disappears, retain its placements as stale instead.
            boolean refreshing = false;
            boolean failed = answer.refreshFailed();
            if (player != null) {
                var current = cache.cachedAnswer(source, player.uuid());
                // A shared retained answer can expose live bulk-refresh progress. Identical
                // placements from a later download are a different revision and a different age.
                if (current.isPresent() && current.get().revision() == answer.revision()) {
                    TierCache.CachedAnswer observed = current.get();
                    age = observed.age();
                    stale = observed.stale();
                    refreshing = observed.refreshing();
                    failed = observed.refreshFailed();
                }
            }
            LookupResult.RefreshStatus status = state.refreshFailed() || failed ? LookupResult.RefreshStatus.REFRESH_FAILED
                    : !state.complete() || refreshing ? LookupResult.RefreshStatus.REFRESHING
                    : stale ? LookupResult.RefreshStatus.STALE : LookupResult.RefreshStatus.FRESH;
            return new LookupResult.Freshness(age, status);
        }));
    }

    public boolean complete() {
        return results.values().stream().allMatch(SourceResult::complete);
    }

    public boolean rankedNowhere() {
        var sections = results.values().stream().flatMap(state -> state.section().stream()).toList();
        return complete() && LookupReport.anySiteAnswered(sections) && LookupReport.nothingRanked(sections);
    }
}
