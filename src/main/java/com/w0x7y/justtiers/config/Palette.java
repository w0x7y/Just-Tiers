package com.w0x7y.justtiers.config;

import com.w0x7y.justtiers.tier.Source;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * The color scheme telling the three leaderboards apart. Color carries exactly one
 * meaning in this UI — which site something came from — so a palette answers for all
 * three sites or it is not a palette.
 *
 * <p>There is one colorblind preset rather than one per condition. Its colors separate
 * by luminance as well as by hue, so the same three work for protanopia, deuteranopia and
 * tritanopia; a second preset differing only slightly would be a worse answer than one
 * that works for everybody.
 */
public enum Palette {

    DEFAULT("default", 0xFFFF55, 0x55FFFF, 0xAA55FF),
    COLORBLIND("colorblind", 0xE69F00, 0x56B4E9, 0xFFFFFF),
    HIGH_CONTRAST("high_contrast", 0xFFFFFF, 0xFFAA00, 0x00FFFF),
    /** Whatever the user picked; colors come from the config rather than from here. */
    CUSTOM("custom");

    private final String id;
    private final Map<Source, Integer> presetColors;

    Palette(String id) {
        this.id = id;
        this.presetColors = Map.of();
    }

    Palette(String id, int mctiers, int subtiers, int novatiers) {
        this.id = id;
        Map<Source, Integer> bySource = new EnumMap<>(Source.class);
        bySource.put(Source.MCTIERS, mctiers);
        bySource.put(Source.SUBTIERS, subtiers);
        bySource.put(Source.NOVATIERS, novatiers);
        this.presetColors = Map.copyOf(bySource);
    }

    /** The on-disk and command-argument spelling. */
    public String id() {
        return id;
    }

    /** The translation key for this palette's name on the config screen. */
    public String displayKey() {
        return "justtiers.palette." + id;
    }

    public boolean isCustom() {
        return this == CUSTOM;
    }

    /**
     * Every site's color under this palette — the whole answer, since a palette that
     * spoke for one site would not be a palette.
     *
     * <p>{@code customColor} is asked only by {@link #CUSTOM}, and only for what it
     * cannot know itself. Where those colors are kept is the caller's business: the
     * config parses them out of hex in the file, the config screen reads three live
     * pickers, and neither format reaches this enum.
     */
    public Map<Source, Integer> colors(ToIntFunction<Source> customColor) {
        Map<Source, Integer> resolved = new EnumMap<>(Source.class);
        for (Source source : Source.ALL) {
            resolved.put(source, colorOf(source, customColor));
        }
        return Map.copyOf(resolved);
    }

    /** One site's color under this palette. */
    public int colorOf(Source source, ToIntFunction<Source> customColor) {
        if (!isCustom()) {
            return presetColors.getOrDefault(source, source.defaultColor());
        }
        // A caller with nothing to say gets the site's own color rather than an error:
        // the pickers on the config screen do not exist yet while the rows are built.
        return customColor == null ? source.defaultColor() : customColor.applyAsInt(source);
    }
}
