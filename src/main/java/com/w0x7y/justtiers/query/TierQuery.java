package com.w0x7y.justtiers.query;

import com.w0x7y.justtiers.cache.TierCache;
import com.w0x7y.justtiers.render.model.NametagSettings;
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
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Adapts a TierCache to the public answer types. Both suppliers return null until the mod
 * has initialized, and every site then reads as pending without starting a request.
 *
 * <p>Another mod's query is an automatic lookup, not one the player asked for, so it follows
 * the same switches as nametag lookups. {@code lookupSites} supplies the sites those switches
 * currently allow: none while nametag tiers are off. A disabled site is never peeked.
 */
final class TierQuery {
    static final List<String> SITE_IDS = Source.ALL.stream().map(TierQuery::siteId).toList();

    /** Ties keep the site's declared gamemode order, the same final tie-break a nametag uses. */
    private static final Comparator<Placement> BEST_FIRST =
            Comparator.comparingInt(Placement::rank).thenComparing(Placement::retired);

    private final Supplier<TierCache> cache;
    private final Supplier<Set<Source>> lookupSites;

    TierQuery(Supplier<TierCache> cache, Supplier<Set<Source>> lookupSites) {
        this.cache = cache;
        this.lookupSites = lookupSites;
    }

    /** The sites automatic lookups may use: none while nametag tiers are off, else the toggled ones. */
    static Set<Source> lookupSites(NametagSettings settings) {
        return settings.enabled() ? settings.enabledSources() : Set.of();
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
        for (Source source : Source.ALL) answers.put(siteId(source), peek(live, allowed, player, source));
        return Collections.unmodifiableMap(answers);
    }

    Optional<Placement> best(UUID player, String siteId) {
        return peek(player, siteId) instanceof SiteAnswer.Ranked ranked ? Optional.of(ranked.best()) : Optional.empty();
    }

    private static SiteAnswer peek(TierCache live, Set<Source> allowed, UUID player, Source source) {
        if (live == null || allowed == null) return new SiteAnswer.Pending(siteId(source));
        if (!allowed.contains(source)) return new SiteAnswer.Disabled(siteId(source));
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
     * Registered gamemodes in the site's declared order, then discovered ones by slug, as
     * the lookup screen lists them. The stable sort keeps that order among equal tiers, so
     * the first placement is the one {@code TierResolver.highestOn} picks. Parsers already
     * bound how many discovered gamemodes one answer holds.
     */
    private static List<Placement> placements(Source source, Map<String, Tier> tiers) {
        List<Placement> placements = new ArrayList<>(tiers.size());
        for (Gamemode gamemode : Gamemodes.of(source)) {
            Tier tier = tiers.get(gamemode.slug());
            if (tier != null) placements.add(placement(gamemode, tier));
        }
        tiers.keySet().stream()
                .filter(slug -> Gamemodes.find(source, slug).isEmpty())
                .sorted()
                .map(slug -> Gamemodes.forSlug(source, slug))
                .flatMap(Optional::stream)
                .forEach(gamemode -> placements.add(placement(gamemode, tiers.get(gamemode.slug()))));
        placements.sort(BEST_FIRST);
        return placements;
    }

    static Placement placement(Gamemode gamemode, Tier tier) {
        return new Placement(siteId(gamemode.source()), gamemode.slug(), gamemode.displayName(),
                tier.label(), tier.rank(), tier.retired(), Gamemodes.isRegistered(gamemode));
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
