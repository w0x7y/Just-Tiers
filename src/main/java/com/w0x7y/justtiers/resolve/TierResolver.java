package com.w0x7y.justtiers.resolve;

import com.w0x7y.justtiers.tier.Gamemode;
import com.w0x7y.justtiers.tier.Gamemodes;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Turns raw per-site tier maps into the list of tiers to render.
 * Pure and Minecraft-free so it can be unit-tested directly.
 */
public final class TierResolver {

    /**
     * When {@code showRetired} is false, retired tiers are discarded before anything else
     * runs, in every mode. A player whose best tier is retired therefore falls back to
     * their best active tier rather than disappearing, and one who is only ever retired
     * shows nothing for that site.
     */
    public static List<ResolvedTier> resolve(Set<Source> sites,
                                             Map<Source, Map<String, Tier>> tiersBySource,
                                             Map<Source, String> selectedGamemodes,
                                             boolean showRetired) {
        return resolve(sites, tiersBySource, selectedGamemodes, showRetired, false);
    }

    /**
     * As {@link #resolve(Set, Map, Map, boolean)}. With {@code preferSelected}, several
     * enabled sites each prefer their selected gamemode the way a single site always
     * does, falling back to that site's best tier; without it they show their best.
     */
    public static List<ResolvedTier> resolve(Set<Source> sites,
                                             Map<Source, Map<String, Tier>> tiersBySource,
                                             Map<Source, String> selectedGamemodes,
                                             boolean showRetired,
                                             boolean preferSelected) {
        Map<Source, Map<String, Tier>> effective =
                showRetired ? tiersBySource : withoutRetired(tiersBySource);
        boolean selecting = sites.size() == 1 || preferSelected;
        List<ResolvedTier> result = new ArrayList<>(Source.ALL.size());
        for (Source source : Source.ALL) {
            if (!sites.contains(source)) continue;
            Map<String, Tier> tiers = effective.getOrDefault(source, Map.of());
            (selecting ? preferringSelected(source, tiers, selectedGamemodes)
                    : highestOn(source, tiers)).ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    private static Map<Source, Map<String, Tier>> withoutRetired(
            Map<Source, Map<String, Tier>> tiersBySource) {
        if (!anyRetired(tiersBySource)) {
            // Nothing to strip, so nothing to copy. This runs per player per frame for
            // anyone who has turned retired tiers off, and most players hold none.
            return tiersBySource;
        }
        Map<Source, Map<String, Tier>> filtered = new EnumMap<>(Source.class);
        tiersBySource.forEach((source, tiers) -> filtered.put(source, activeOnly(tiers)));
        return filtered;
    }

    /**
     * One site's placements with the retired ones dropped. Returns the argument
     * unchanged when it holds none, which is the common case and saves a copy on a path
     * that runs per player.
     *
     * <p>Package-private rather than private: its own tests exercise it directly.
     */
    static Map<String, Tier> activeOnly(Map<String, Tier> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            return Map.of();
        }
        boolean anyRetired = false;
        for (Tier tier : tiers.values()) {
            if (tier.retired()) {
                anyRetired = true;
                break;
            }
        }
        if (!anyRetired) {
            return tiers;
        }
        Map<String, Tier> active = new LinkedHashMap<>();
        tiers.forEach((slug, tier) -> {
            if (!tier.retired()) {
                active.put(slug, tier);
            }
        });
        return active;
    }

    private static boolean anyRetired(Map<Source, Map<String, Tier>> tiersBySource) {
        for (Map<String, Tier> tiers : tiersBySource.values()) {
            for (Tier tier : tiers.values()) {
                if (tier.retired()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The selected gamemode's tier, or this site's best when the player has none there. */
    private static Optional<ResolvedTier> preferringSelected(Source source,
                                                             Map<String, Tier> tiers,
                                                             Map<Source, String> selectedGamemodes) {
        if (tiers.isEmpty()) {
            return Optional.empty();
        }

        String selectedSlug = selectedGamemodes.get(source);
        Tier selected = selectedSlug == null ? null : tiers.get(selectedSlug);
        if (selected != null) {
            Optional<Gamemode> gamemode = Gamemodes.find(source, selectedSlug);
            if (gamemode.isPresent()) {
                return Optional.of(new ResolvedTier(gamemode.get(), selected));
            }
        }

        // Not ranked in the selected mode: fall back to their best on this same site.
        return highestOn(source, tiers);
    }

    /**
     * The player's best tier on one site. Retired tiers compete normally; ties break
     * toward the active tier, then toward the site's declared gamemode order.
     */
    public static Optional<ResolvedTier> highestOn(Source source, Map<String, Tier> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            return Optional.empty();
        }
        Gamemode bestMode = null;
        Tier bestTier = null;
        // Walking the registry supplies the final tie-break and ignores unknown slugs.
        // The nametag only needs one placement, so no sorted list is built per frame.
        for (Gamemode gamemode : Gamemodes.of(source)) {
            Tier tier = tiers.get(gamemode.slug());
            if (tier != null && (bestTier == null || tier.compareTo(bestTier) < 0)) {
                bestMode = gamemode;
                bestTier = tier;
            }
        }
        return bestMode == null ? Optional.empty()
                : Optional.of(new ResolvedTier(bestMode, bestTier));
    }

    private TierResolver() {
    }
}
