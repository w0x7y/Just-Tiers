package com.w0x7y.justtiers.render.model;

/**
 * The purely cosmetic half of the nametag: where the badge sits and how much chrome it
 * carries. None of it changes <em>which</em> tiers are shown — that is
 * {@link com.w0x7y.justtiers.resolve.DisplayMode}'s job — so the same resolved tiers can
 * be drawn in any of these shapes.
 *
 * <p>With icons off, the sites are told apart by tier colour alone, which is the legend
 * the config screen already teaches on its display-mode row.
 */
public record NametagStyle(BadgePosition position, boolean icons, boolean brackets) {

    /** What Just-Tiers has always drawn: {@code [<icon>HT2] } in front of the name. */
    public static final NametagStyle DEFAULT = new NametagStyle(BadgePosition.BEFORE, true, true);

    public NametagStyle {
        // A null position can only come from a hand-edited config; before is the default
        // everywhere else, and a preview that refuses to draw would be worse.
        position = position == null ? BadgePosition.BEFORE : position;
    }
}
