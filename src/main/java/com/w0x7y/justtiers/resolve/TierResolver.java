package com.w0x7y.justtiers.resolve;

import com.w0x7y.justtiers.tier.Gamemode;
import com.w0x7y.justtiers.tier.Gamemodes;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
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
        return resolve(mode, tiersBySource, selectedGamemodes, true);
    }

    /**
     * When {@code showRetired} is false, retired tiers are discarded before anything else
     * runs, in every mode. A player whose best tier is retired therefore falls back to
     * their best active tier rather than disappearing, and one who is only ever retired
     * shows nothing for that site.
     */
    public static List<ResolvedTier> resolve(DisplayMode mode,
                                             Map<Source, Map<String, Tier>> tiersBySource,
                                             Map<Source, String> selectedGamemodes,
                                             boolean showRetired) {
        Map<Source, Map<String, Tier>> effective =
                showRetired ? tiersBySource : withoutRetired(tiersBySource);
        Optional<Source> single = mode.singleSource();
        if (single.isPresent()) {
            return resolveSingleSite(single.get(), effective, selectedGamemodes);
        }
        return resolveAll(effective);
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
        return highestOn(source, tiers).map(List::of).orElse(List.of());
    }

    private static List<ResolvedTier> resolveAll(Map<Source, Map<String, Tier>> tiersBySource) {
        List<ResolvedTier> result = new ArrayList<>(Source.ALL.size());
        for (Source source : Source.ALL) {
            highestOn(source, tiersBySource.getOrDefault(source, Map.of())).ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    /**
     * The player's best tier on one site. Retired tiers compete normally; ties break
     * toward the active tier, then toward the site's declared gamemode order.
     */
    public static Optional<ResolvedTier> highestOn(Source source, Map<String, Tier> tiers) {
        return rankAll(source, tiers).stream().findFirst();
    }

    /**
     * Every placement the player holds on one site, best first, under exactly the
     * ordering {@link #highestOn} picks its winner by. {@code /justtiers lookup} lists
     * the whole thing; the nametag only ever wants the head of it.
     */
    public static List<ResolvedTier> rankAll(Source source, Map<String, Tier> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            return List.of();
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

        // Candidates were appended while walking `order`, so they are already in the
        // site's declared order; List.sort is stable, which settles the last tiebreak
        // without a third comparator doing a linear indexOf on every comparison.
        candidates.sort(Comparator.comparingInt((ResolvedTier r) -> r.tier().rank())
                .thenComparing(r -> r.tier().retired()));
        return List.copyOf(candidates);
    }

    private TierResolver() {
    }
}
