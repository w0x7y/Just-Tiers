package com.w0x7y.justtiers.resolve;

import com.w0x7y.justtiers.tier.Source;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum DisplayMode {
    MCTIERS_ONLY(Source.MCTIERS),
    SUBTIERS_ONLY(Source.SUBTIERS),
    NOVATIERS_ONLY(Source.NOVATIERS),
    /** Show the best tier from every site side by side. */
    ALL(null);

    private final Source source;
    private final List<Source> sources;

    DisplayMode(Source source) {
        this.source = source;
        // Held per constant rather than built on demand: the nametag path asks every
        // player every frame, and the Optional and singleton list this replaces were
        // allocated every one of those times.
        this.sources = source == null ? Source.ALL : List.of(source);
    }

    /** The single site this mode reads, or empty for {@link #ALL}. */
    public Optional<Source> singleSource() {
        return Optional.ofNullable(source);
    }

    /** Every site this mode reads: one of them, or all of them. */
    public List<Source> sources() {
        return sources;
    }

    /** Lower-case name used by the config file and command arguments. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
