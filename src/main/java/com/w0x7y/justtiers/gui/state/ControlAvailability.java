package com.w0x7y.justtiers.gui.state;

import com.w0x7y.justtiers.config.Palette;
import com.w0x7y.justtiers.resolve.GamemodeSelection;
import com.w0x7y.justtiers.tier.Source;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Decides which controls the config screen leaves live. Nothing is ever hidden; a
 * control that cannot do anything useful is grayed and carries a {@link Reason} the
 * UI turns into an explanation, so the screen always shows the whole configuration
 * surface rather than a shape that changes under the user. A row is live exactly when
 * its reason is {@link Reason#AVAILABLE}.
 */
public record ControlAvailability(Reason sites,
                                  Reason showRetired,
                                  Reason preferSelected,
                                  Reason appearance,
                                  Reason customColors,
                                  Map<Source, Reason> reasons) {

    /**
     * Why a row is live or grayed, one explanation each. A gamemode picker's reasons
     * apart from the master switch are {@link GamemodeSelection.Use}, so the picker says
     * exactly what the nametag will do.
     */
    public enum Reason {
        AVAILABLE,
        MOD_DISABLED,
        /** Several sites show their best tiers instead of their selections. */
        MULTIPLE_SITES,
        /** Enabling the site would put its selection to use. */
        SITE_DISABLED,
        /** The selection is used only while this is the only enabled site. */
        ONLY_SITE_NEEDED,
        /** Preferring selections changes nothing until more than one site is enabled. */
        SINGLE_SITE,
        /** The color pickers feed only the Custom palette. */
        PALETTE_NOT_CUSTOM
    }

    public ControlAvailability {
        reasons = Map.copyOf(reasons);
    }

    /**
     * {@code preferSelected} is the pending "prefer selected gamemodes" choice. It makes
     * every enabled site's picker matter, and itself only matters with several sites.
     */
    public static ControlAvailability of(boolean enabled, Set<Source> sites,
                                         boolean preferSelected, Palette palette) {
        Map<Source, Reason> reasons = new EnumMap<>(Source.class);
        for (Source source : Source.ALL) {
            reasons.put(source, reasonFor(enabled, sites, preferSelected, source));
        }
        // The badge's shape - its side, its icons, its brackets - means the same thing in
        // every site selection, so the master switch is the only thing that can gray it.
        Reason nametags = enabled ? Reason.AVAILABLE : Reason.MOD_DISABLED;
        Reason preference = !enabled ? Reason.MOD_DISABLED
                : GamemodeSelection.preferenceMatters(sites) ? Reason.AVAILABLE : Reason.SINGLE_SITE;
        // The palette also colors the lookup screen and download progress, so it stays live
        // with nametags off, and the color pickers need only the palette they feed.
        Reason colors = palette != null && palette.isCustom()
                ? Reason.AVAILABLE : Reason.PALETTE_NOT_CUSTOM;
        return new ControlAvailability(nametags, nametags, preference, nametags, colors, reasons);
    }

    private static Reason reasonFor(boolean enabled, Set<Source> sites, boolean preferSelected,
                                    Source source) {
        if (!enabled) {
            return Reason.MOD_DISABLED;
        }
        return switch (GamemodeSelection.of(source, sites, preferSelected)) {
            case USED -> Reason.AVAILABLE;
            case SHOWING_BEST -> Reason.MULTIPLE_SITES;
            case NEEDS_SITE -> Reason.SITE_DISABLED;
            case NEEDS_ONLY_SITE -> Reason.ONLY_SITE_NEEDED;
        };
    }

    /** Why a site's gamemode picker is live or grayed. */
    public Reason reasonFor(Source source) {
        return reasons.getOrDefault(source, Reason.SITE_DISABLED);
    }
}
