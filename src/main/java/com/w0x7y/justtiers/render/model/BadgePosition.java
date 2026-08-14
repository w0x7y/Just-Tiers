package com.w0x7y.justtiers.render.model;

import java.util.Locale;

/** Which side of the player's name the tier badge sits on. */
public enum BadgePosition {
    BEFORE,
    AFTER;

    /** Lower-case name used by the config file and command arguments. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** True when the badge is drawn in front of the name rather than behind it. */
    public boolean prepends() {
        return this == BEFORE;
    }
}
