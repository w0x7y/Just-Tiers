package com.w0x7y.justtiers.api;

import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Fetches one player's tiers from one site. An empty map means the site answered and
 * the player is genuinely unranked. A lookup that could not be completed — a transport
 * failure, or any HTTP status other than 200 and 404 — fails the returned future instead,
 * so callers can retry rather than caching "unranked" for a site that was merely down.
 */
public interface TierSource {

    Source source();

    CompletableFuture<Map<String, Tier>> fetch(UUID uuid);

    /**
     * Whether a site answered with a body that carried something but parsed to nothing.
     * That is the signature of a response schema having changed under us, as opposed to
     * a site legitimately answering "no placements" — worth a warning, but not worth
     * failing a lookup over. Two characters is the shortest an empty JSON object or
     * array can be, so anything longer had content we did not understand.
     */
    static boolean nothingUnderstood(Map<?, ?> parsed, String body) {
        return parsed.isEmpty() && body != null && body.length() > 2;
    }
}
