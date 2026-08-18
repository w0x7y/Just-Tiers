package com.w0x7y.justtiers.debug;

import com.w0x7y.justtiers.cache.SiteGate;
import com.w0x7y.justtiers.cache.SiteHealth;
import com.w0x7y.justtiers.tier.Source;

/**
 * One site's line in the debug report: what it has answered, and what the cache and the
 * retry rules are currently holding for it.
 *
 * @param cachedPlayers       players this site has an answer for, in-flight ones included.
 * @param pendingLookups      of those, the ones still waiting on the network.
 * @param playersAwaitingRetry players held back by their own retry delay after a failure.
 */
public record SiteDiagnostics(Source source,
                              SiteHealth.Snapshot health,
                              SiteGate.Status gate,
                              int cachedPlayers,
                              int pendingLookups,
                              int playersAwaitingRetry) {
}
