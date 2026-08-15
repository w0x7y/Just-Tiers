package com.w0x7y.justtiers.tier;

public enum Source {
    MCTIERS("MCTiers", "https://mctiers.com/api", "https://mctiers.com", 0xFFFF55),
    SUBTIERS("SubTiers", "https://subtiers.net/api", "https://subtiers.net", 0x55FFFF),
    NOVATIERS("NovaTiers", "https://novatiers.com", "https://novatiers.com", 0xAA55FF);

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
    public int color() {
        return color;
    }
}
