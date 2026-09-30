package com.w0x7y.justtiers.api;

import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Fetches one player's tiers from one site. An empty map means the site answered and
 * the player is genuinely unranked. A lookup that could not be completed — a transport
 * failure, malformed response, or any HTTP status other than 200 and 404 — fails the future,
 * so callers can retry rather than caching "unranked" for a site that was merely down.
 */
public interface TierSource {

    Source source();

    CompletableFuture<Map<String, Tier>> fetch(UUID uuid);

    /** Bulk sources retain their original data age when serving a cached snapshot. */
    record Answer(Map<String, Tier> tiers, java.time.Duration age, boolean refreshFailed) {
        public Answer {
            tiers = Map.copyOf(tiers);
            age = age.isNegative() ? java.time.Duration.ZERO : age;
        }
    }

    default CompletableFuture<Answer> fetchAnswer(UUID uuid) {
        return fetch(uuid).thenApply(tiers -> new Answer(tiers, java.time.Duration.ZERO, false));
    }

    /** A bulk source can refresh independently of its per-player cache. */
    record RefreshState(boolean pending, boolean failed, java.time.Duration cooldown) {
        public static final RefreshState IDLE = new RefreshState(false, false, java.time.Duration.ZERO);
    }

    default RefreshState refreshState() { return RefreshState.IDLE; }

}
