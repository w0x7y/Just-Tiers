package com.w0x7y.justtiers.gui;

/**
 * Color carries exactly one meaning in this UI: which leaderboard something belongs
 * to. Everything else is neutral, which is why there are so few constants here.
 */
final class Colors {

    /** Neutral secondary text, matching YACL's own inactive grey. */
    static final int SECONDARY = 0xFFA0A0A0;
    static final int DISABLED = 0xFF707070;

    /** {@code SiteColors.of} is a bare RGB triple; draw calls want opaque ARGB. */
    static int opaque(int rgb) {
        return 0xFF000000 | rgb;
    }

    private Colors() {
    }
}
