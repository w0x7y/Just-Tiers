package com.w0x7y.justtiers.gui.state;

import com.w0x7y.justtiers.config.Palette;
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
                                  boolean appearance,
                                  boolean customColors,
                                  Map<Source, Reason> reasons) {

    public enum Reason { AVAILABLE, MOD_DISABLED, MULTIPLE_SITES, SITE_DISABLED }

    public ControlAvailability {
        reasons = Map.copyOf(reasons);
    }

    public static ControlAvailability of(boolean enabled, Set<Source> sites, Palette palette) {
        Map<Source, Reason> reasons = new EnumMap<>(Source.class);
        for (Source source : Source.ALL) {
            reasons.put(source, reasonFor(enabled, sites, source));
        }
        // The badge's shape - its side, its icons, its brackets - means the same thing in
        // every site selection, so the master switch is the only thing that can grey it.
        // The color pickers additionally need the palette to be the one they feed.
        return new ControlAvailability(enabled, enabled, enabled,
                enabled && palette != null && palette.isCustom(), reasons);
    }

    private static Reason reasonFor(boolean enabled, Set<Source> sites, Source source) {
        if (!enabled) {
            return Reason.MOD_DISABLED;
        }
        if (!sites.contains(source)) return Reason.SITE_DISABLED;
        return sites.size() == 1 ? Reason.AVAILABLE : Reason.MULTIPLE_SITES;
    }

    public boolean gamemode(Source source) {
        return reasonFor(source) == Reason.AVAILABLE;
    }

    public Reason reasonFor(Source source) {
        return reasons.getOrDefault(source, Reason.SITE_DISABLED);
    }
}
