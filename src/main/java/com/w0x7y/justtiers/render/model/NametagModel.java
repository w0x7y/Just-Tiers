package com.w0x7y.justtiers.render.model;

import com.w0x7y.justtiers.resolve.ResolvedTier;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays out the tier prefix that goes in front of a player's name, as
 * {@code [<icon>HT2 <icon>LT3] }. Tier text is coloured by its source site, except
 * retired tiers which are light red and carry an {@code R} prefix.
 */
public final class NametagModel {

    public static final int BRACKET_COLOR = 0x555555;
    public static final int RETIRED_COLOR = 0xFF5555;
    /** Bitmap glyphs are multiplied by the text colour, so icons must be white. */
    public static final int ICON_COLOR = 0xFFFFFF;

    public static List<Segment> build(List<ResolvedTier> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            return List.of();
        }

        List<Segment> segments = new ArrayList<>(tiers.size() * 3 + 2);
        segments.add(new Segment("[", BRACKET_COLOR));

        for (int i = 0; i < tiers.size(); i++) {
            if (i > 0) {
                segments.add(new Segment(" ", BRACKET_COLOR));
            }
            ResolvedTier resolved = tiers.get(i);
            segments.add(new Segment(String.valueOf(resolved.gamemode().icon()), ICON_COLOR));
            int color = resolved.tier().retired()
                    ? RETIRED_COLOR
                    : resolved.gamemode().source().color();
            segments.add(new Segment(resolved.tier().label(), color));
        }

        segments.add(new Segment("] ", BRACKET_COLOR));
        return List.copyOf(segments);
    }

    /** Concatenated text, ignoring colour. Used by tests and debug logging. */
    public static String plainText(List<Segment> segments) {
        StringBuilder builder = new StringBuilder();
        for (Segment segment : segments) {
            builder.append(segment.text());
        }
        return builder.toString();
    }

    private NametagModel() {
    }
}
