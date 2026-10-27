package com.w0x7y.justtiers.tier;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum Source {
    PVPTIERS("pvptiers", "PvPTiers", "https://pvptiers.com/api", "https://pvptiers.com", 0xFFFF55),
    PVPHQ("pvphq", "PvPHQ", "https://pvphq.com/api/v1", "https://pvphq.com", 0xD2D2D2),
    SUBTIERS("subtiers", "SubTiers", "https://subtiers.net/api", "https://subtiers.net", 0x55FFFF),
    NOVATIERS("novatiers", "NovaTiers", "https://novatiers.com", "https://novatiers.com", 0xAA55FF);

    /**
     * Every site, in declaration order. {@code values()} clones its array on every
     * call, and this is iterated per nametag and per frame; the order is the one every
     * caller already relied on.
     */
    public static final List<Source> ALL = List.of(values());

    private final String id;
    private final String displayName;
    private final String baseUrl;
    private final String homeUrl;
    private final int color;

    Source(String id, String displayName, String baseUrl, String homeUrl, int color) {
        this.id = id;
        this.displayName = displayName;
        this.baseUrl = baseUrl;
        this.homeUrl = homeUrl;
        this.color = color;
    }

    /**
     * The site's id in commands and the tier-query API. Spelled out rather than derived
     * from the constant's name, so an internal rename cannot change what players type or
     * what other mods pass. Config keys use {@link #name()} and are a separate format.
     */
    public String id() {
        return id;
    }

    /** The site with this id, ignoring case and surrounding whitespace. */
    public static Optional<Source> fromId(String raw) {
        if (raw == null) return Optional.empty();
        String id = raw.trim().toLowerCase(Locale.ROOT);
        for (Source source : ALL) {
            if (source.id.equals(id)) return Optional.of(source);
        }
        return Optional.empty();
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

    /**
     * The color this site is drawn in when nothing else is configured. Read this only
     * as a fallback — {@link com.w0x7y.justtiers.config.JustTiersConfig#colorOf} is what
     * the user actually chose, and reaching past it is how a screen ends up ignoring
     * their palette.
     */
    public int defaultColor() {
        return color;
    }
}
