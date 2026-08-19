package com.w0x7y.justtiers.render.model;

import com.w0x7y.justtiers.preview.PreviewSample;
import com.w0x7y.justtiers.resolve.DisplayMode;
import com.w0x7y.justtiers.resolve.ResolvedTier;
import com.w0x7y.justtiers.resolve.TierResolver;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntUnaryOperator;

/**
 * The tier badge that goes beside a player's name, finished and ready to draw: the runs
 * of colored text, and the side of the name they belong on.
 *
 * <p>This is the only way to build one. Picking which tiers to show, laying them out and
 * choosing the side used to be three calls a caller made in the right order, with the
 * style read twice — once for the layout and again for the side — so a caller could
 * quietly compose a badge built under one style onto the side another asked for. Here the
 * side travels with the segments it was built with, and the sequence is behind a single
 * factory.
 *
 * <p>Deliberately Minecraft-free, and so is everything it reads: {@link TierView} stands
 * in for the running mod, and {@link com.w0x7y.justtiers.render.Nametags} is the only
 * place a badge meets a {@code Component}. Deciding what a player wears therefore needs
 * neither the game nor the static hub.
 */
public record Badge(List<Segment> segments, BadgePosition position) {

    /** No tiers to show. Composing this onto a name leaves the name alone. */
    public static final Badge NONE = new Badge(List.of(), BadgePosition.BEFORE);

    public Badge {
        segments = segments == null ? List.of() : List.copyOf(segments);
        position = position == null ? BadgePosition.BEFORE : position;
    }

    /** The badge for tiers that have already been chosen. */
    public static Badge of(List<ResolvedTier> tiers, NametagStyle style) {
        NametagStyle effective = style == null ? NametagStyle.DEFAULT : style;
        List<Segment> segments = NametagModel.build(tiers, effective);
        return segments.isEmpty() ? NONE : new Badge(segments, effective.position());
    }

    /**
     * The badge one player should be wearing right now: the whole decision, from the
     * settings in force and whatever the sites have said, with nothing left for the
     * caller to get right. Empty when the mod is off, when the player cannot be on a
     * leaderboard, or when no site has answered yet.
     *
     * <p>Only the sites the display mode actually reads are asked, so switching to a
     * single-site mode stops the other two from reaching the tag even when their answers
     * are already cached.
     */
    public static Badge forPlayer(TierView view, UUID uuid) {
        NametagSettings settings = view.settings();
        if (!settings.enabled() || uuid == null) {
            return NONE;
        }
        // Offline-mode and NPC entities use v3 UUIDs and are never in these leaderboards.
        if (uuid.version() != 4) {
            return NONE;
        }

        Map<Source, Map<String, Tier>> answers = new EnumMap<>(Source.class);
        for (Source source : settings.displayMode().sources()) {
            view.peek(source, uuid).ifPresent(tiers -> answers.put(source, tiers));
        }
        return forPlayer(settings.displayMode(), answers, settings.selectedGamemodes(),
                settings.showRetired(), settings.style());
    }

    /**
     * The badge for one player, from whatever the sites have answered so far. Sites still
     * in flight are simply absent from {@code tiersBySource}, so a badge appears as soon
     * as the first one answers and fills in over the next few frames.
     */
    public static Badge forPlayer(DisplayMode mode,
                                  Map<Source, Map<String, Tier>> tiersBySource,
                                  Map<Source, String> selectedGamemodes,
                                  boolean showRetired,
                                  NametagStyle style) {
        if (tiersBySource == null || tiersBySource.isEmpty()) {
            return NONE;
        }
        return of(TierResolver.resolve(mode, tiersBySource, selectedGamemodes, showRetired),
                style);
    }

    /**
     * The made-up badge the config screen draws. Every placement is tier 1 — see
     * {@link PreviewSample} — so this is a picture of the settings, never a lookup, and
     * {@code timeMillis} is what drives the active/retired cycle.
     */
    public static Badge preview(DisplayMode mode,
                                Map<Source, String> selectedGamemodes,
                                boolean showRetired,
                                long timeMillis,
                                NametagStyle style) {
        boolean retired = PreviewSample.retiredPhase(showRetired, timeMillis);
        return of(PreviewSample.resolve(mode, selectedGamemodes, retired), style);
    }

    public boolean isEmpty() {
        return segments.isEmpty();
    }

    /**
     * The same badge with every run run through {@code recolor}. Used by the config
     * screen to dim the preview while the mod is switched off; icons stay icons, because
     * the recoloring goes through {@link Segment#withColor}.
     */
    public Badge recolor(IntUnaryOperator recolor) {
        if (segments.isEmpty()) {
            return this;
        }
        return new Badge(segments.stream()
                .map(segment -> segment.withColor(recolor.applyAsInt(segment.color())))
                .toList(), position);
    }

    /** Concatenated text, ignoring color. What the badge's tests assert on. */
    public String plainText() {
        return NametagModel.plainText(segments);
    }
}
