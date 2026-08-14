package com.w0x7y.justtiers.preview;

import com.w0x7y.justtiers.render.model.NametagModel;
import com.w0x7y.justtiers.render.model.NametagStyle;
import com.w0x7y.justtiers.render.model.Segment;
import com.w0x7y.justtiers.resolve.DisplayMode;
import com.w0x7y.justtiers.resolve.ResolvedTier;
import com.w0x7y.justtiers.tier.Gamemode;
import com.w0x7y.justtiers.tier.Gamemodes;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The made-up nametag the config screen draws. It is deliberately <em>not</em> a
 * leaderboard lookup: every placement is tier 1, so the preview shows what a setting
 * looks like rather than what anyone has actually earned. A valid selection is always
 * the gamemode you see, and the preview is never empty — the only substitution is a
 * selection naming a gamemode its site no longer has, which draws that site's first
 * gamemode instead of nothing.
 *
 * <p>Minecraft-free on purpose, and still built through the shared
 * {@link NametagModel}, so the preview keeps agreeing with the real nametag's shape,
 * spacing and colours.
 */
public final class PreviewSample {

    /** Every preview placement, active. */
    public static final Tier ACTIVE = new Tier(1, true, false);
    /** The same placement while the retired half of the cycle is showing. */
    public static final Tier RETIRED = new Tier(1, true, true);

    /** How long each half of the active/retired cycle lasts. */
    public static final long RETIRED_CYCLE_MILLIS = 5_000L;

    /**
     * What "all sites" previews, whatever the gamemode pickers say. Those pickers are
     * greyed in this mode, so the preview shows one fixed headline gamemode per site
     * instead of pretending the greyed selections still matter.
     */
    public static final Map<Source, String> ALL_MODE_GAMEMODES = Map.of(
            Source.MCTIERS, "vanilla",
            Source.SUBTIERS, "minecart",
            Source.NOVATIERS, "spearmace");

    /**
     * Which half of the cycle {@code timeMillis} lands in. Always active while retired
     * tiers are hidden, so the toggle still visibly does something; otherwise it
     * alternates so both spellings of a tier get shown.
     */
    public static boolean retiredPhase(boolean showRetired, long timeMillis) {
        if (!showRetired) {
            return false;
        }
        return Math.floorMod(Math.floorDiv(timeMillis, RETIRED_CYCLE_MILLIS), 2L) == 1L;
    }

    public static List<ResolvedTier> resolve(DisplayMode mode,
                                             Map<Source, String> selectedGamemodes,
                                             boolean retired) {
        Tier tier = retired ? RETIRED : ACTIVE;
        return gamemodes(mode, selectedGamemodes).stream()
                .map(gamemode -> new ResolvedTier(gamemode, tier))
                .toList();
    }

    public static List<Segment> segments(DisplayMode mode,
                                         Map<Source, String> selectedGamemodes,
                                         boolean retired) {
        return segments(mode, selectedGamemodes, retired, NametagStyle.DEFAULT);
    }

    public static List<Segment> segments(DisplayMode mode,
                                         Map<Source, String> selectedGamemodes,
                                         boolean retired,
                                         NametagStyle style) {
        return NametagModel.build(resolve(mode, selectedGamemodes, retired), style);
    }

    /** Convenience for callers holding a clock rather than a phase. */
    public static List<Segment> segments(DisplayMode mode,
                                         Map<Source, String> selectedGamemodes,
                                         boolean showRetired,
                                         long timeMillis) {
        return segments(mode, selectedGamemodes, showRetired, timeMillis, NametagStyle.DEFAULT);
    }

    public static List<Segment> segments(DisplayMode mode,
                                         Map<Source, String> selectedGamemodes,
                                         boolean showRetired,
                                         long timeMillis,
                                         NametagStyle style) {
        return segments(mode, selectedGamemodes, retiredPhase(showRetired, timeMillis), style);
    }

    /** The gamemodes the tag shows: the selection on one site, or the fixed trio. */
    private static List<Gamemode> gamemodes(DisplayMode mode,
                                            Map<Source, String> selectedGamemodes) {
        var single = mode.singleSource();
        if (single.isPresent()) {
            return List.of(gamemodeOf(single.get(), selectedGamemodes));
        }

        List<Gamemode> all = new ArrayList<>(Source.values().length);
        for (Source source : Source.values()) {
            all.add(gamemodeOf(source, ALL_MODE_GAMEMODES));
        }
        return List.copyOf(all);
    }

    /**
     * The selected gamemode, or the site's first one when the selection is missing or
     * no longer a real gamemode — a preview should never be blank over a stale slug.
     */
    private static Gamemode gamemodeOf(Source source, Map<Source, String> selectedGamemodes) {
        return Gamemodes.find(source, selectedGamemodes.get(source))
                .orElseGet(() -> Gamemodes.of(source).getFirst());
    }

    private PreviewSample() {
    }
}
