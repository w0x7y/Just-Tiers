package com.w0x7y.justtiers.query;

import java.util.Objects;

/**
 * A player's placement in one gamemode on one site.
 *
 * <p>{@code rank} orders placements without parsing labels. Lower is better:
 * {@code rank = (level - 1) * 3 + division}, where division is 0 for high, 1 for middle and
 * 2 for low. HT1 is 0, MT1 is 1, LT1 is 2, HT2 is 3, and so on to LT5 at 14. Only PvPHQ
 * reports middle divisions, which fall between the high and low tiers of the same level.
 * Retirement does not change the rank; Just-Tiers breaks equal ranks toward the active
 * placement. Sites are independent competitions, so equal ranks on different sites are
 * not equal standings.
 *
 * @param siteId       the site's stable lowercase id, one of {@link JustTiersQuery#SITE_IDS}
 * @param gamemode     the site's gamemode slug, such as {@code crystal} or {@code neth_pot}
 * @param gamemodeName the gamemode's English display name, such as {@code Netherite Pot}
 * @param label        the tier as Just-Tiers displays it, such as {@code HT1}, {@code MT3}
 *                     or {@code RLT3}. Retired placements have an {@code R} prefix.
 * @param rank         0 (HT1) to 14 (LT5), lower is better
 * @param retired      whether the site marks this placement as retired
 */
public record Placement(String siteId, String gamemode, String gamemodeName, String label,
                        int rank, boolean retired) {
    /** The best possible rank, HT1. */
    public static final int BEST_RANK = 0;
    /** The worst possible rank, LT5. */
    public static final int WORST_RANK = 14;

    public Placement {
        Objects.requireNonNull(siteId, "siteId");
        Objects.requireNonNull(gamemode, "gamemode");
        Objects.requireNonNull(gamemodeName, "gamemodeName");
        Objects.requireNonNull(label, "label");
        if (rank < BEST_RANK || rank > WORST_RANK) throw new IllegalArgumentException("rank out of range: " + rank);
    }
}
