package com.w0x7y.justtiers.lookup;

import com.w0x7y.justtiers.api.MojangNameSource;
import com.w0x7y.justtiers.api.PlayerRef;
import com.w0x7y.justtiers.cache.TierCache;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

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
    private final Map<Source, LookupSection> sections = new EnumMap<>(Source.class);
    private final CompletableFuture<Optional<PlayerRef>> resolvedPlayer = new CompletableFuture<>();
    private PlayerRef player;
    private Error error;

    private LookupSession(String requestedName, TierCache cache, Executor publisher) {
        this.requestedName = requestedName;
        this.cache = cache;
        this.publisher = publisher;
    }

    public static LookupSession start(String name, Players players, TierCache cache, Executor publisher) {
        LookupSession session = new LookupSession(name, cache, publisher);
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
            cache.load(site, found.uuid()).whenComplete((tiers, failure) -> {
                Optional<Map<String, Tier>> answer;
                if (failure == null) {
                    answer = Optional.of(tiers);
                } else {
                    // Offline players may never be peeked by nametags. Explicit lookup
                    // must clear their failed entry so the next attempt can retry it.
                    cache.forgetFailed(site, found.uuid());
                    answer = Optional.empty();
                }
                publisher.execute(() -> sections.put(site, LookupReport.section(site, answer)));
            });
        }
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

    /** Empty means pending, distinct from an unavailable or unranked answer. */
    public Optional<LookupSection> section(Source source) {
        return Optional.ofNullable(sections.get(source));
    }

    public boolean complete() {
        return sections.size() == Source.ALL.size();
    }

    public boolean rankedNowhere() {
        return complete() && LookupReport.anySiteAnswered(sections.values())
                && LookupReport.nothingRanked(sections.values());
    }
}
