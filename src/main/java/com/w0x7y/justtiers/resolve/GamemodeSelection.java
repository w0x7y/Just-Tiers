package com.w0x7y.justtiers.resolve;

import com.w0x7y.justtiers.tier.Source;

import java.util.Set;

/**
 * Whether a site's selected gamemode decides what its nametag tier shows. A single enabled
 * site always prefers its selection; several do only while selections are preferred, and
 * otherwise each shows its best tier.
 *
 * <p>The resolver, the config preview, the pickers' explanations and the command warnings all
 * ask this one rule, so they cannot disagree about when a selection matters.
 */
public final class GamemodeSelection {

    /** How one site's selection stands under the enabled sites. */
    public enum Use {
        /** The nametag prefers this site's selection. */
        USED,
        /** The site is enabled, but several sites show their best tiers instead. */
        SHOWING_BEST,
        /** The site is disabled; enabling it would put its selection to use. */
        NEEDS_SITE,
        /** The site is disabled, and only it being the sole enabled site would use its selection. */
        NEEDS_ONLY_SITE
    }

    /** Whether the enabled sites prefer their selections over their best tiers. */
    public static boolean applies(Set<Source> sites, boolean preferSelected) {
        return sites.size() == 1 || preferSelected;
    }

    /** Whether preferring selections changes anything: a single site always prefers its own. */
    public static boolean preferenceMatters(Set<Source> sites) {
        return sites.size() > 1;
    }

    public static Use of(Source source, Set<Source> sites, boolean preferSelected) {
        if (!sites.contains(source)) {
            return preferSelected ? Use.NEEDS_SITE : Use.NEEDS_ONLY_SITE;
        }
        return applies(sites, preferSelected) ? Use.USED : Use.SHOWING_BEST;
    }

    private GamemodeSelection() {
    }
}
