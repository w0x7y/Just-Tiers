package com.w0x7y.justtiers.api;

import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Test fakes for per-player sources, whose answers carry no earlier data age. */
public final class TestSources {

    /** A source answering each player with the placements {@code fetch} completes. */
    public static TierSource of(Source source, Function<UUID, CompletableFuture<Map<String, Tier>>> fetch) {
        return new TierSource() {
            @Override public Source source() { return source; }
            @Override public CompletableFuture<Answer> fetchAnswer(UUID uuid) { return fresh(fetch.apply(uuid)); }
        };
    }

    /** Placements as a per-player site answers them: no data age and no failed refresh. */
    public static CompletableFuture<TierSource.Answer> fresh(CompletableFuture<Map<String, Tier>> tiers) {
        return tiers.thenApply(placements -> new TierSource.Answer(placements, Duration.ZERO, false));
    }

    private TestSources() {
    }
}
