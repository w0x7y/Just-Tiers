package com.w0x7y.justtiers.render.model;

import com.w0x7y.justtiers.tier.Source;

import java.util.EnumMap;
import java.util.Map;

/**
 * The purely cosmetic half of the nametag: where the badge sits, how much chrome it
 * carries, and what colour each site is drawn in. None of it changes <em>which</em> tiers
 * are shown — that is {@link com.w0x7y.justtiers.resolve.DisplayMode}'s job — so the same
 * resolved tiers can be drawn in any of these shapes.
 *
 * <p>The colours travel in the style rather than being looked up where they are drawn,
 * which is what keeps {@link NametagModel} free of both Minecraft and the config.
 *
 * <p>With icons off, the sites are told apart by tier colour alone, which is the legend
 * the config screen already teaches on its display-mode row.
 */
public record NametagStyle(BadgePosition position, boolean icons, boolean brackets,
                           Map<Source, Integer> colors) {

    /** What Just-Tiers has always drawn: {@code [<icon>HT2] } in front of the name. */
    public static final NametagStyle DEFAULT = new NametagStyle(BadgePosition.BEFORE, true, true);

    public NametagStyle {
        // A null position can only come from a hand-edited config; before is the default
        // everywhere else, and a preview that refuses to draw would be worse.
        position = position == null ? BadgePosition.BEFORE : position;
        colors = colors == null || colors.isEmpty() ? defaultColors() : Map.copyOf(colors);
    }

    /** The shape alone, drawn in the sites' own colours. */
    public NametagStyle(BadgePosition position, boolean icons, boolean brackets) {
        this(position, icons, brackets, defaultColors());
    }

    public int colorOf(Source source) {
        return colors.getOrDefault(source, source.defaultColor());
    }

    private static Map<Source, Integer> defaultColors() {
        Map<Source, Integer> colors = new EnumMap<>(Source.class);
        for (Source source : Source.ALL) {
            colors.put(source, source.defaultColor());
        }
        return Map.copyOf(colors);
    }
}
