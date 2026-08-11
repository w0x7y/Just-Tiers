package com.w0x7y.justtiers.resolve;

import com.w0x7y.justtiers.tier.Source;

import java.util.Locale;
import java.util.Optional;

public enum DisplayMode {
    MCTIERS_ONLY(Source.MCTIERS),
    SUBTIERS_ONLY(Source.SUBTIERS),
    NOVATIERS_ONLY(Source.NOVATIERS),
    /** Show the best tier from every site side by side. */
    ALL(null);

    private final Source source;

    DisplayMode(Source source) {
        this.source = source;
    }

    /** The single site this mode reads, or empty for {@link #ALL}. */
    public Optional<Source> singleSource() {
        return Optional.ofNullable(source);
    }

    /** Lower-case name used by the config file and command arguments. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
