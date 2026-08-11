package com.w0x7y.justtiers.resolve;

import com.w0x7y.justtiers.tier.Gamemode;
import com.w0x7y.justtiers.tier.Gamemodes;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns raw per-site tier maps into the list of tiers to render.
 * Pure and Minecraft-free so it can be unit-tested directly.
 */
public final class TierResolver {

    public static List<ResolvedTier> resolve(DisplayMode mode,
                                             Map<Source, Map<String, Tier>> tiersBySource,
                                             Map<Source, String> selectedGamemodes) {
        Optional<Source> single = mode.singleSource();
        if (single.isPresent()) {
            return resolveSingleSite(single.get(), tiersBySource, selectedGamemodes);
        }
        return resolveAll(tiersBySource);
    }

    private static List<ResolvedTier> resolveSingleSite(Source source,
                                                        Map<Source, Map<String, Tier>> tiersBySource,
                                                        Map<Source, String> selectedGamemodes) {
        Map<String, Tier> tiers = tiersBySource.getOrDefault(source, Map.of());
        if (tiers.isEmpty()) {
            return List.of();
        }

        String selectedSlug = selectedGamemodes.get(source);
        Tier selected = selectedSlug == null ? null : tiers.get(selectedSlug);
        if (selected != null) {
            Optional<Gamemode> gamemode = Gamemodes.find(source, selectedSlug);
            if (gamemode.isPresent()) {
                return List.of(new ResolvedTier(gamemode.get(), selected));
            }
        }

        // Not ranked in the selected mode: fall back to their best on this same site.
        return highestOn(source, tiers).map(List::of).orElseGet(List::of);
    }

    private static List<ResolvedTier> resolveAll(Map<Source, Map<String, Tier>> tiersBySource) {
        List<ResolvedTier> result = new ArrayList<>(Source.values().length);
        for (Source source : Source.values()) {
            highestOn(source, tiersBySource.getOrDefault(source, Map.of())).ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    /**
     * The player's best tier on one site. Retired tiers compete normally; ties break
     * toward the active tier, then toward the site's declared gamemode order.
     */
    public static Optional<ResolvedTier> highestOn(Source source, Map<String, Tier> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            return Optional.empty();
        }

        List<Gamemode> order = Gamemodes.of(source);
        List<ResolvedTier> candidates = new ArrayList<>(tiers.size());
        for (Gamemode gamemode : order) {
            Tier tier = tiers.get(gamemode.slug());
            if (tier != null) {
                candidates.add(new ResolvedTier(gamemode, tier));
            }
        }
        // Gamemodes the site added after this build are skipped rather than guessed at.

        return candidates.stream().min(
                Comparator.comparingInt((ResolvedTier r) -> r.tier().rank())
                        .thenComparing(r -> r.tier().retired())
                        .thenComparingInt(r -> order.indexOf(r.gamemode())));
    }

    private TierResolver() {
    }
}
