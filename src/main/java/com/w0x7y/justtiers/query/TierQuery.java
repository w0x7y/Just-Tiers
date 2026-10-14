package com.w0x7y.justtiers.query;

import com.w0x7y.justtiers.cache.TierCache;
import com.w0x7y.justtiers.tier.Gamemode;
import com.w0x7y.justtiers.tier.Gamemodes;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Adapts a TierCache to the public answer types. The cache supplier returns null until the
 * mod has initialized, and every site then reads as pending without starting a request.
 */
final class TierQuery {
    static final List<String> SITE_IDS = Source.ALL.stream().map(TierQuery::siteId).toList();

    /** Ties keep the site's declared gamemode order, the same final tie-break a nametag uses. */
    private static final Comparator<Placement> BEST_FIRST =
            Comparator.comparingInt(Placement::rank).thenComparing(Placement::retired);

    private final Supplier<TierCache> cache;

    TierQuery(Supplier<TierCache> cache) {
        this.cache = cache;
    }

    boolean ready() {
        return cache.get() != null;
    }

    SiteAnswer peek(UUID player, String siteId) {
        Objects.requireNonNull(player, "player");
        return peek(cache.get(), player, source(siteId));
    }

    Map<String, SiteAnswer> peekAll(UUID player) {
        Objects.requireNonNull(player, "player");
        TierCache live = cache.get();
        Map<String, SiteAnswer> answers = new LinkedHashMap<>();
        for (Source source : Source.ALL) answers.put(siteId(source), peek(live, player, source));
        return Collections.unmodifiableMap(answers);
    }

    Optional<Placement> best(UUID player, String siteId) {
        return peek(player, siteId) instanceof SiteAnswer.Ranked ranked ? Optional.of(ranked.best()) : Optional.empty();
    }

    private static SiteAnswer peek(TierCache live, UUID player, Source source) {
        if (live == null) return new SiteAnswer.Pending(siteId(source));
        // peek admits a request on a miss; observe then reads that one entry without side effects.
        live.peek(source, player);
        return answer(source, live.observe(source, player));
    }

    static SiteAnswer answer(Source source, TierCache.Observation observation) {
        String siteId = siteId(source);
        if (observation.answer().isEmpty()) {
            return observation.inFlight() ? new SiteAnswer.Pending(siteId) : new SiteAnswer.Unavailable(siteId);
        }
        TierCache.CachedAnswer answer = observation.answer().get();
        Freshness freshness = new Freshness(answer.age(), answer.stale(), answer.refreshing(), answer.refreshFailed());
        List<Placement> placements = placements(source, answer.tiers());
        return placements.isEmpty() ? new SiteAnswer.Unranked(siteId, freshness)
                : new SiteAnswer.Ranked(siteId, placements, freshness);
    }

    /**
     * Placements in gamemodes this build does not know are omitted, as the lookup screen and
     * nametags omit them, rather than reported under a guessed name.
     */
    private static List<Placement> placements(Source source, Map<String, Tier> tiers) {
        List<Placement> placements = new ArrayList<>(tiers.size());
        for (Gamemode gamemode : Gamemodes.of(source)) {
            Tier tier = tiers.get(gamemode.slug());
            if (tier != null) placements.add(placement(gamemode, tier));
        }
        placements.sort(BEST_FIRST);
        return placements;
    }

    static Placement placement(Gamemode gamemode, Tier tier) {
        return new Placement(siteId(gamemode.source()), gamemode.slug(), gamemode.displayName(),
                tier.label(), tier.rank(), tier.retired());
    }

    /** Spelled out rather than derived from enum names, so an internal rename cannot change the API. */
    static String siteId(Source source) {
        return switch (source) {
            case PVPTIERS -> "pvptiers";
            case PVPHQ -> "pvphq";
            case SUBTIERS -> "subtiers";
            case NOVATIERS -> "novatiers";
        };
    }

    static Source source(String siteId) {
        Objects.requireNonNull(siteId, "siteId");
        String id = siteId.trim().toLowerCase(Locale.ROOT);
        for (Source source : Source.ALL) {
            if (siteId(source).equals(id)) return source;
        }
        throw new IllegalArgumentException("Unknown Just-Tiers site '" + siteId + "'; expected one of " + SITE_IDS);
    }
}
