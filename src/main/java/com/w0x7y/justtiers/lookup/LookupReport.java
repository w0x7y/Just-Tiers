package com.w0x7y.justtiers.lookup;

import com.w0x7y.justtiers.tier.Gamemode;
import com.w0x7y.justtiers.tier.Gamemodes;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns the raw per-site answers a lookup collected into the rows the lookup screen
 * draws. Pure and Minecraft-free so it can be unit-tested directly; the screen owns
 * nothing but the pixels.
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
     * unranked: a site that did not answer has said nothing about this player, and a row
     * of dashes would be saying something.
     */
    public static List<LookupSection> build(Map<Source, Optional<Map<String, Tier>>> answers) {
        List<LookupSection> sections = new ArrayList<>(Source.ALL.size());
        for (Source source : Source.ALL) {
            sections.add(section(source, answers.getOrDefault(source, Optional.empty())));
        }
        return List.copyOf(sections);
    }

    /**
     * One site's row, from that site's answer alone. The lookup screen fills each row in
     * the moment its site replies rather than waiting for the slowest of the three, so a
     * section has to stand on its own.
     */
    public static LookupSection section(Source source, Optional<Map<String, Tier>> answer) {
        if (answer.isEmpty()) {
            return new LookupSection(source, LookupSection.Status.UNAVAILABLE, List.of());
        }
        List<LookupCell> cells = cells(source, answer.get());
        boolean ranked = cells.stream().anyMatch(LookupCell::ranked);
        return new LookupSection(source,
                ranked ? LookupSection.Status.RANKED : LookupSection.Status.UNRANKED, cells);
    }

    /**
     * A cell per gamemode the site is known to run, in its declared order. A slug the
     * site has started using since this build is skipped rather than guessed at — the
     * same rule {@code TierResolver.rankAll} follows — because a column with no icon and
     * no name would be worse than a missing one.
     */
    private static List<LookupCell> cells(Source source, Map<String, Tier> tiers) {
        List<Gamemode> gamemodes = Gamemodes.of(source);
        List<LookupCell> cells = new ArrayList<>(gamemodes.size());
        for (Gamemode gamemode : gamemodes) {
            cells.add(new LookupCell(gamemode,
                    Optional.ofNullable(tiers.get(gamemode.slug()))));
        }
        return cells;
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
