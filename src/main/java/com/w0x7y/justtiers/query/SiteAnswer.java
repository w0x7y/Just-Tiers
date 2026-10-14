package com.w0x7y.justtiers.query;

import java.util.List;
import java.util.Objects;

/**
 * One site's current answer about one player. Only {@link Unranked} and {@link Ranked}
 * are evidence about the player's standing on that site. A pending or unavailable site
 * says nothing about whether the player is ranked, and must not be shown as unranked.
 * One site's answer says nothing about another site.
 */
public sealed interface SiteAnswer {

    /** The site's stable lowercase id, one of {@link JustTiersQuery#SITE_IDS}. */
    String siteId();

    /**
     * No answer is known yet, and a request is queued or in flight. Also returned for every
     * site before Just-Tiers has initialized. Peek again later.
     */
    record Pending(String siteId) implements SiteAnswer {
        public Pending { Objects.requireNonNull(siteId, "siteId"); }
    }

    /**
     * No answer is known and none is currently being fetched: the site failed, is paused or
     * cooling down, the player is backing off after a failure, or the request was deferred.
     * A later peek may retry once those delays allow it.
     */
    record Unavailable(String siteId) implements SiteAnswer {
        public Unavailable { Objects.requireNonNull(siteId, "siteId"); }
    }

    /** The site answered successfully and lists no placement this build recognizes. */
    record Unranked(String siteId, Freshness freshness) implements SiteAnswer {
        public Unranked {
            Objects.requireNonNull(siteId, "siteId");
            Objects.requireNonNull(freshness, "freshness");
        }
    }

    /**
     * The site answered successfully with at least one placement. Placements are sorted best
     * first: by rank, then active before retired, then in the site's gamemode order. Retired
     * placements are included regardless of the user's nametag settings.
     */
    record Ranked(String siteId, List<Placement> placements, Freshness freshness) implements SiteAnswer {
        public Ranked {
            Objects.requireNonNull(siteId, "siteId");
            Objects.requireNonNull(freshness, "freshness");
            placements = List.copyOf(placements);
            if (placements.isEmpty()) throw new IllegalArgumentException("a ranked answer needs a placement");
        }

        /**
         * The site's best placement, chosen as a nametag chooses one site's best tier when
         * retired tiers are shown. It ignores the user's selected gamemode.
         */
        public Placement best() {
            return placements.get(0);
        }
    }
}
