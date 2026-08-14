package com.w0x7y.justtiers.lookup;

import com.w0x7y.justtiers.resolve.ResolvedTier;
import com.w0x7y.justtiers.resolve.TierResolver;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns the raw per-site answers a lookup collected into the sections it prints.
 * Pure and Minecraft-free so it can be unit-tested directly; the command owns nothing
 * but the wording.
 *
 * <p>A lookup deliberately ignores the display mode and the selected gamemodes: those
 * settings exist to keep a nametag short, and the whole point of asking about a player
 * by name is to see everything they have placed in. Retired tiers are listed too, marked
 * by their {@code R} prefix as usual.
 */
public final class LookupReport {

    /**
     * One section per site, always in {@link Source} declaration order so repeated
     * lookups do not reshuffle. A source missing from {@code answers}, or mapped to an
     * empty optional, is reported {@link LookupSection.Status#UNAVAILABLE} rather than
     * unranked: a site that did not answer has said nothing about this player.
     */
    public static List<LookupSection> build(Map<Source, Optional<Map<String, Tier>>> answers) {
        List<LookupSection> sections = new ArrayList<>(Source.values().length);
        for (Source source : Source.values()) {
            Optional<Map<String, Tier>> answer =
                    answers.getOrDefault(source, Optional.empty());
            if (answer.isEmpty()) {
                sections.add(new LookupSection(
                        source, LookupSection.Status.UNAVAILABLE, List.of()));
                continue;
            }
            List<ResolvedTier> tiers = TierResolver.rankAll(source, answer.get());
            sections.add(new LookupSection(source,
                    tiers.isEmpty() ? LookupSection.Status.UNRANKED : LookupSection.Status.RANKED,
                    tiers));
        }
        return List.copyOf(sections);
    }

    /** True when not one site had a placement to show. */
    public static boolean nothingRanked(List<LookupSection> sections) {
        return sections.stream()
                .noneMatch(section -> section.status() == LookupSection.Status.RANKED);
    }

    /**
     * True when at least one site actually answered, ranked or not. With every site
     * unavailable a lookup has collected no evidence about the player at all, so
     * {@link #nothingRanked} is only worth saying out loud when this is true.
     */
    public static boolean anySiteAnswered(List<LookupSection> sections) {
        return sections.stream()
                .anyMatch(section -> section.status() != LookupSection.Status.UNAVAILABLE);
    }

    private LookupReport() {
    }
}
