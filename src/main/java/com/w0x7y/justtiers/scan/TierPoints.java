package com.w0x7y.justtiers.scan;

import com.w0x7y.justtiers.lookup.LookupCell;
import com.w0x7y.justtiers.lookup.LookupSection;
import com.w0x7y.justtiers.tier.Tier;

import java.util.Collection;

/**
 * What a placement is worth when ranking a lobby. Ten points for HT1 down to one for
 * LT5, which is exactly {@code 10 - Tier.rank()} — the tier model already orders itself
 * that way, so there is no table here to drift out of step with it.
 *
 * <p>A player's total is the sum over every gamemode on every site, not their best and
 * not their average: someone placed in eleven gamemodes is more dangerous than someone
 * placed in one at the same tier, and only summing says so.
 *
 * <p>Retired placements score nothing. A scan asks who is a threat right now, and a tier
 * nobody is defending is not one — which is why this ignores the {@code showRetired}
 * setting that the nametag and the lookup screen both honour.
 */
public final class TierPoints {

    /** The best possible placement, HT1, and therefore the size of the scale. */
    private static final int BEST = 10;

    public static int points(Tier tier) {
        if (tier == null || tier.retired()) {
            return 0;
        }
        return BEST - tier.rank();
    }

    /** Sums a player's placements. A site that never answered contributes nothing. */
    public static int total(Collection<LookupSection> sections) {
        int total = 0;
        for (LookupSection section : sections) {
            for (LookupCell cell : section.cells()) {
                total += cell.tier().map(TierPoints::points).orElse(0);
            }
        }
        return total;
    }

    private TierPoints() {
    }
}
