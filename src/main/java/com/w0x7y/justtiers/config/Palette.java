package com.w0x7y.justtiers.config;

import com.w0x7y.justtiers.tier.Source;

import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * The colour scheme telling the three leaderboards apart. Colour carries exactly one
 * meaning in this UI — which site something came from — so a palette answers for all
 * three sites or it is not a palette.
 *
 * <p>There is one colourblind preset rather than one per condition. Its colours separate
 * by luminance as well as by hue, so the same three work for protanopia, deuteranopia and
 * tritanopia; a second preset differing only slightly would be a worse answer than one
 * that works for everybody.
 */
public enum Palette {

    DEFAULT("default", 0xFFFF55, 0x55FFFF, 0xAA55FF),
    COLORBLIND("colorblind", 0xE69F00, 0x56B4E9, 0xFFFFFF),
    HIGH_CONTRAST("high_contrast", 0xFFFFFF, 0xFFAA00, 0x00FFFF),
    /** Whatever the user picked; colours come from the config rather than from here. */
    CUSTOM("custom");

    private final String id;
    private final Map<Source, Integer> colors;

    Palette(String id) {
        this.id = id;
        this.colors = Map.of();
    }

    Palette(String id, int mctiers, int subtiers, int novatiers) {
        this.id = id;
        Map<Source, Integer> bySource = new EnumMap<>(Source.class);
        bySource.put(Source.MCTIERS, mctiers);
        bySource.put(Source.SUBTIERS, subtiers);
        bySource.put(Source.NOVATIERS, novatiers);
        this.colors = Map.copyOf(bySource);
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
     * This palette's colour for a site. {@code customColors} is consulted only by
     * {@link #CUSTOM}, and a missing or unparseable entry falls back to that site's own
     * default — per site, so one typo costs one colour rather than three.
     */
    public int colorOf(Source source, Map<String, String> customColors) {
        if (!isCustom()) {
            return colors.getOrDefault(source, source.defaultColor());
        }
        if (customColors == null) {
            return source.defaultColor();
        }
        OptionalInt parsed = HexColor.parse(customColors.get(source.name()));
        return parsed.orElseGet(source::defaultColor);
    }
}
