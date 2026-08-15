package com.w0x7y.justtiers.gui.state;

import com.w0x7y.justtiers.resolve.DisplayMode;
import com.w0x7y.justtiers.tier.Source;

import java.util.EnumMap;
import java.util.Map;

/**
 * Decides which controls the config screen leaves live. Nothing is ever hidden; a
 * control that cannot do anything useful is greyed and carries a {@link Reason} the
 * UI turns into an explanation, so the screen always shows the whole configuration
 * surface rather than a shape that changes under the user.
 */
public record ControlAvailability(boolean displayMode,
                                  boolean showRetired,
                                  boolean appearance,
                                  Map<Source, Reason> reasons) {

    public enum Reason { AVAILABLE, MOD_DISABLED, MODE_IS_ALL, OTHER_SITE }

    public ControlAvailability {
        reasons = Map.copyOf(reasons);
    }

    public static ControlAvailability of(boolean enabled, DisplayMode mode) {
        Map<Source, Reason> reasons = new EnumMap<>(Source.class);
        for (Source source : Source.ALL) {
            reasons.put(source, reasonFor(enabled, mode, source));
        }
        // The badge's shape - its side, its icons, its brackets - means the same thing in
        // every display mode, so the master switch is the only thing that can grey it.
        return new ControlAvailability(enabled, enabled, enabled, reasons);
    }

    private static Reason reasonFor(boolean enabled, DisplayMode mode, Source source) {
        if (!enabled) {
            return Reason.MOD_DISABLED;
        }
        var single = mode.singleSource();
        if (single.isEmpty()) {
            return Reason.MODE_IS_ALL;
        }
        return single.get() == source ? Reason.AVAILABLE : Reason.OTHER_SITE;
    }

    public boolean gamemode(Source source) {
        return reasonFor(source) == Reason.AVAILABLE;
    }

    public Reason reasonFor(Source source) {
        return reasons.getOrDefault(source, Reason.OTHER_SITE);
    }
}
