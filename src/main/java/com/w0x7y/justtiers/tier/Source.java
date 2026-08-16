package com.w0x7y.justtiers.tier;

import java.util.List;

public enum Source {
    MCTIERS("MCTiers", "https://mctiers.com/api", "https://mctiers.com", 0xFFFF55),
    SUBTIERS("SubTiers", "https://subtiers.net/api", "https://subtiers.net", 0x55FFFF),
    NOVATIERS("NovaTiers", "https://novatiers.com", "https://novatiers.com", 0xAA55FF);

    /**
     * Every site, in declaration order. {@code values()} clones its array on every
     * call, and this is iterated per nametag and per frame; the order is the one every
     * caller already relied on.
     */
    public static final List<Source> ALL = List.of(values());

    private final String displayName;
    private final String baseUrl;
    private final String homeUrl;
    private final int color;

    Source(String displayName, String baseUrl, String homeUrl, int color) {
        this.displayName = displayName;
        this.baseUrl = baseUrl;
        this.homeUrl = homeUrl;
        this.color = color;
    }

    public String displayName() {
        return displayName;
    }

    public String baseUrl() {
        return baseUrl;
    }

    /**
     * The leaderboard's own page, for crediting it with a link. Not always different
     * from {@link #baseUrl()} - NovaTiers is queried through its site root - but the two
     * mean different things and only one of them is worth showing a reader.
     */
    public String homeUrl() {
        return homeUrl;
    }

    /** Colour applied to tier text originating from this site. */
    /**
     * The colour this site is drawn in when nothing else is configured. Read this only
     * as a fallback — {@link com.w0x7y.justtiers.config.JustTiersConfig#colorOf} is what
     * the user actually chose, and reaching past it is how a screen ends up ignoring
     * their palette.
     */
    public int defaultColor() {
        return color;
    }
}
