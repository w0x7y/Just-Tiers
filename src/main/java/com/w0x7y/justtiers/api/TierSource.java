package com.w0x7y.justtiers.api;

import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Fetches one player's tiers from one site. Implementations never fail the returned
 * future: unreachable services and unranked players both resolve to an empty map.
 */
public interface TierSource {

    Source source();

    CompletableFuture<Map<String, Tier>> fetch(UUID uuid);
}
