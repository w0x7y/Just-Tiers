package com.w0x7y.justtiers.debug;

import com.w0x7y.justtiers.cache.TierCache;
import com.w0x7y.justtiers.tier.Source;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads a {@link TierCache}'s own account of itself into the rows
 * {@code /justtiers debug} prints.
 *
 * <p>Here rather than on the cache: the cache is what fetches and remembers tiers, and
 * having it build a record whose only purpose is to be formatted into a bug report would
 * point the dependency the wrong way round. Debug knows about caching; caching does not
 * need to know it is ever reported on. See
 * {@code docs/adr/0001-tiercache-keeps-its-own-observability.md}.
 *
 * <p>Small, and still worth a module of its own, because {@link SiteDiagnostics} ends in
 * three adjacent {@code int} parameters. Nothing in the type system stops two of them
 * being swapped, so the mapping is asserted rather than assumed.
 */
public final class CacheDiagnostics {

    /** One row per site, in {@link Source} declaration order, read in one pass. */
    public static List<SiteDiagnostics> of(TierCache cache) {
        List<SiteDiagnostics> sites = new ArrayList<>(Source.ALL.size());
        for (Source source : Source.ALL) {
            sites.add(of(cache, source));
        }
        return List.copyOf(sites);
    }

    public static SiteDiagnostics of(TierCache cache, Source source) {
        return new SiteDiagnostics(source,
                cache.health(source),
                cache.gateStatus(source),
                cache.cachedPlayers(source),
                cache.pendingLookups(source),
                cache.playersAwaitingRetry(source));
    }

    private CacheDiagnostics() {
    }
}
