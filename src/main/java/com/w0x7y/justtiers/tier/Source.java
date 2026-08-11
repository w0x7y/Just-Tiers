package com.w0x7y.justtiers.tier;

public enum Source {
    MCTIERS("MCTiers", "https://mctiers.com/api", 0xFFFF55),
    SUBTIERS("SubTiers", "https://subtiers.net/api", 0x55FFFF),
    NOVATIERS("NovaTiers", "https://novatiers.com", 0xAA55FF);

    private final String displayName;
    private final String baseUrl;
    private final int color;

    Source(String displayName, String baseUrl, int color) {
        this.displayName = displayName;
        this.baseUrl = baseUrl;
        this.color = color;
    }

    public String displayName() {
        return displayName;
    }

    public String baseUrl() {
        return baseUrl;
    }

    /** Colour applied to tier text originating from this site. */
    public int color() {
        return color;
    }
}
