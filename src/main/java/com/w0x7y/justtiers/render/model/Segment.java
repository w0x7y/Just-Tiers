package com.w0x7y.justtiers.render.model;

/**
 * A run of nametag text with a single colour. Deliberately Minecraft-free.
 *
 * <p>{@code icon} marks the runs that are gamemode glyphs rather than words. They are
 * drawn from Just-Tiers' own font, which nothing else on a nametag is, and a boolean is
 * as much as this record can say about that without learning what a font identifier is.
 */
public record Segment(String text, int color, boolean icon) {

    /** Ordinary text, in the default font. */
    public Segment(String text, int color) {
        this(text, color, false);
    }

    /**
     * The same run in a different colour. Recolouring by rebuilding through the
     * two-argument constructor would quietly turn an icon back into ordinary text, and
     * an icon drawn in the default font is a missing-glyph box.
     */
    public Segment withColor(int color) {
        return new Segment(text, color, icon);
    }
}
