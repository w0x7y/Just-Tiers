package com.w0x7y.justtiers.lookup;

import com.w0x7y.justtiers.tier.Source;

import java.util.List;

/**
 * One site's row in the lookup screen: a cell for every gamemode that site runs, in the
 * site's own order, or the reason there is no row to draw.
 */
public record LookupSection(Source source, Status status, List<LookupCell> cells) {

    public enum Status {
        /** The site answered and placed the player in at least one of its gamemodes. */
        RANKED,
        /** The site answered, and has never placed this player. */
        UNRANKED,
        /** The site could not be reached, so calling the player unranked would be a lie. */
        UNAVAILABLE
    }

    public LookupSection {
        cells = List.copyOf(cells);
    }
}
