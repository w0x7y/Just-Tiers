package com.w0x7y.justtiers.gui.state;

import com.w0x7y.justtiers.config.Palette;
import com.w0x7y.justtiers.resolve.GamemodeSelection;
import com.w0x7y.justtiers.tier.Source;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Decides which controls the config screen leaves live. Nothing is ever hidden; a
 * control that cannot do anything useful is greyed and carries a {@link Reason} the
 * UI turns into an explanation, so the screen always shows the whole configuration
 * surface rather than a shape that changes under the user.
 */
public record ControlAvailability(boolean sites,
                                  boolean showRetired,
                                  boolean preferSelected,
                                  boolean appearance,
                                  boolean customColors,
                                  Map<Source, Reason> reasons) {

    /**
     * Why a site's gamemode picker is live or greyed, one explanation each. Apart from the
     * master switch these are {@link GamemodeSelection.Use}, so the picker says exactly
     * what the nametag will do.
     */
    public enum Reason {
        AVAILABLE,
        MOD_DISABLED,
        /** Several sites show their best tiers instead of their selections. */
        MULTIPLE_SITES,
        /** Enabling the site would put its selection to use. */
        SITE_DISABLED,
        /** The selection is used only while this is the only enabled site. */
        ONLY_SITE_NEEDED
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
        // every site selection, so the master switch is the only thing that can grey it.
        // The color pickers additionally need the palette to be the one they feed.
        return new ControlAvailability(enabled, enabled,
                enabled && GamemodeSelection.preferenceMatters(sites), enabled,
                enabled && palette != null && palette.isCustom(), reasons);
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

    public boolean gamemode(Source source) {
        return reasonFor(source) == Reason.AVAILABLE;
    }

    public Reason reasonFor(Source source) {
        return reasons.getOrDefault(source, Reason.SITE_DISABLED);
    }
}
