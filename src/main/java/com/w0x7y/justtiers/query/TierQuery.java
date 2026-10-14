package com.w0x7y.justtiers.query;

import com.w0x7y.justtiers.api.PlayerRef;
import com.w0x7y.justtiers.cache.TierCache;
import com.w0x7y.justtiers.resolve.TierResolver;
import com.w0x7y.justtiers.tier.Gamemode;
import com.w0x7y.justtiers.tier.Gamemodes;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Adapts a TierCache to the public answer types. Both suppliers return null until the mod
 * has initialized, and every site then reads as pending without starting a request.
 *
 * <p>Another mod's query is an automatic lookup, not one the player asked for, so it follows
 * the same switches as nametag lookups. {@code lookupSites} supplies the sites those switches
 * currently allow, {@code NametagSettings.automaticLookupSites}: none while nametag tiers are
 * off. A disabled site is never peeked, and neither is a UUID that is not an account.
 */
final class TierQuery {
    static final List<String> SITE_IDS = Source.ALL.stream().map(Source::id).toList();

    private final Supplier<TierCache> cache;
    private final Supplier<Set<Source>> lookupSites;

    TierQuery(Supplier<TierCache> cache, Supplier<Set<Source>> lookupSites) {
        this.cache = cache;
        this.lookupSites = lookupSites;
    }

    boolean ready() {
        return cache.get() != null && lookupSites.get() != null;
    }

    SiteAnswer peek(UUID player, String siteId) {
        Objects.requireNonNull(player, "player");
        Source source = source(siteId);
        return peek(cache.get(), lookupSites.get(), player, source);
    }

    Map<String, SiteAnswer> peekAll(UUID player) {
        Objects.requireNonNull(player, "player");
        // One reading of each supplier keeps every site's answer on the same settings.
        TierCache live = cache.get();
        Set<Source> allowed = lookupSites.get();
        Map<String, SiteAnswer> answers = new LinkedHashMap<>();
        for (Source source : Source.ALL) answers.put(source.id(), peek(live, allowed, player, source));
        return Collections.unmodifiableMap(answers);
    }

    Optional<Placement> best(UUID player, String siteId) {
        return peek(player, siteId) instanceof SiteAnswer.Ranked ranked ? Optional.of(ranked.best()) : Optional.empty();
    }

    private static SiteAnswer peek(TierCache live, Set<Source> allowed, UUID player, Source source) {
        if (live == null || allowed == null) return new SiteAnswer.Pending(source.id());
        if (!allowed.contains(source)) return new SiteAnswer.Disabled(source.id());
        // Not an account the sites list, so no request is spent on it; nametags skip these too.
        if (!PlayerRef.isAccount(player)) return new SiteAnswer.Unavailable(source.id());
        // peek admits a request on a miss; observe then reads that one entry without side effects.
        live.peek(source, player);
        return answer(source, live.observe(source, player));
    }

    static SiteAnswer answer(Source source, TierCache.Observation observation) {
        String siteId = source.id();
        if (observation.answer().isEmpty()) {
            return observation.inFlight() ? new SiteAnswer.Pending(siteId) : new SiteAnswer.Unavailable(siteId);
        }
        TierCache.CachedAnswer answer = observation.answer().get();
        Freshness freshness = new Freshness(answer.age(), answer.stale(), answer.refreshing(), answer.refreshFailed());
        List<Placement> placements = placements(source, answer.tiers());
        return placements.isEmpty() ? new SiteAnswer.Unranked(siteId, freshness)
                : new SiteAnswer.Ranked(siteId, placements, freshness);
    }

    /** The site's placements best first, in the order {@code TierResolver.ranked} defines. */
    private static List<Placement> placements(Source source, Map<String, Tier> tiers) {
        return TierResolver.ranked(source, tiers).stream()
                .map(resolved -> placement(resolved.gamemode(), resolved.tier()))
                .toList();
    }

    static Placement placement(Gamemode gamemode, Tier tier) {
        return new Placement(gamemode.source().id(), gamemode.slug(), gamemode.displayName(),
                tier.label(), tier.rank(), tier.retired(), Gamemodes.isRegistered(gamemode));
    }

    static Source source(String siteId) {
        Objects.requireNonNull(siteId, "siteId");
        return Source.fromId(siteId).orElseThrow(() -> new IllegalArgumentException(
                "Unknown Just-Tiers site '" + siteId + "'; expected one of " + SITE_IDS));
    }
}
