package com.w0x7y.justtiers.lookup;

import com.w0x7y.justtiers.resolve.ResolvedTier;
import com.w0x7y.justtiers.tier.Source;

import java.util.List;

/**
 * One site's contribution to a {@code /justtiers lookup}: every gamemode the player is
 * ranked in on that site, best first, or the reason there is nothing to list.
 */
public record LookupSection(Source source, Status status, List<ResolvedTier> tiers) {

    public enum Status {
        /** At least one placement, listed in {@link #tiers}. */
        RANKED,
        /** The site answered, and has never placed this player. */
        UNRANKED,
        /** The site could not be reached, so calling the player unranked would be a lie. */
        UNAVAILABLE
    }

    public LookupSection {
        tiers = List.copyOf(tiers);
    }
}
