package com.w0x7y.justtiers.render.model;

import com.w0x7y.justtiers.resolve.ResolvedTier;
import com.w0x7y.justtiers.tier.Source;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays out the tier badge that goes with a player's name, as
 * {@code [<icon>HT2 <icon>LT3] }. Tier text is always colored by its source site;
 * retired tiers are distinguished by their {@code R} prefix alone.
 *
 * <p>The brackets, the icons and the side the badge sits on are all
 * {@link NametagStyle} choices. The badge always carries the single space that separates
 * it from the name, on whichever side the name is, so callers only ever concatenate.
 *
 * <p>Package-private: this is the layout half of {@link Badge}, which is what the rest
 * of the mod builds badges through. Its own tests sit in this package and exercise it
 * directly.
 */
final class NametagModel {

    static final int BRACKET_COLOR = 0x555555;
    /** Bitmap glyphs are multiplied by the text color, so icons must be white. */
    static final int ICON_COLOR = 0xFFFFFF;

    static List<Segment> build(List<ResolvedTier> tiers, NametagStyle style) {
        List<Segment> entries = entries(tiers, style);
        if (entries.isEmpty()) {
            return List.of();
        }

        String open = style.brackets() ? "[" : "";
        String close = style.brackets() ? "]" : "";
        if (style.position().prepends()) {
            close = close + " ";
        } else {
            open = " " + open;
        }

        List<Segment> segments = new ArrayList<>(entries.size() + 2);
        // With brackets off one side has nothing left to draw; an empty segment would
        // still be a component to lay out, so it is left out rather than emitted blank.
        if (!open.isEmpty()) {
            segments.add(new Segment(open, BRACKET_COLOR));
        }
        segments.addAll(entries);
        if (!close.isEmpty()) {
            segments.add(new Segment(close, BRACKET_COLOR));
        }
        return List.copyOf(segments);
    }

    /**
     * The tier entries alone, separated by single spaces — no brackets and no spacing to
     * a name: the run of icons and labels the badge wraps.
     */
    private static List<Segment> entries(List<ResolvedTier> tiers, NametagStyle style) {
        if (tiers == null || tiers.isEmpty()) {
            return List.of();
        }

        List<Segment> segments = new ArrayList<>(tiers.size() * 3);
        for (int i = 0; i < tiers.size(); i++) {
            if (i > 0) {
                segments.add(new Segment(" ", BRACKET_COLOR));
            }
            ResolvedTier resolved = tiers.get(i);
            if (style.icons()) {
                segments.add(new Segment(String.valueOf(resolved.gamemode().icon()),
                        ICON_COLOR, true));
            }
            Source source = resolved.gamemode().source();
            segments.add(new Segment(resolved.tier().label(),
                    style.colorOf(source)));
        }
        return List.copyOf(segments);
    }

    /** Concatenated text, ignoring color. Reached through {@link Badge#plainText()}. */
    static String plainText(List<Segment> segments) {
        StringBuilder builder = new StringBuilder();
        for (Segment segment : segments) {
            builder.append(segment.text());
        }
        return builder.toString();
    }

    private NametagModel() {
    }
}
