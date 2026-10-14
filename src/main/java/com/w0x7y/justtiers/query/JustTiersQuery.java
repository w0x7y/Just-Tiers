package com.w0x7y.justtiers.query;

import com.w0x7y.justtiers.JustTiersClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read Just-Tiers' tier answers from another client mod. Guard every call with
 * {@code FabricLoader.getInstance().isModLoaded("justtiers")}; see {@code docs/api.md}.
 *
 * <p><b>Nonblocking.</b> No method waits for the network. Like a nametag, a peek on a
 * missing or expired answer can start an asynchronous request and returns what is known
 * now. Peek again later, for example on a later frame or tick, to see the result.
 *
 * <p><b>Network.</b> A peek can make a request even when the user has disabled nametag tiers
 * or that site's toggle. PvPTiers, PvPHQ and SubTiers then receive the player's account
 * UUID. NovaTiers is read from a bulk index; a peek can start that download when no index
 * is loaded. Requests share the site's queue, retry delays and pauses with nametags, and
 * explicit lookups take priority. Peek only for players your mod shows to the user.
 *
 * <p><b>Settings.</b> Answers ignore nametag display settings, site toggles, selected
 * gamemodes and the retired-tier setting. Retired placements are included and marked.
 *
 * <p><b>Threads.</b> Every method is thread-safe and may be called from any thread. Just-Tiers
 * itself reads the cache from the client thread. Each answer is a snapshot that can be
 * outdated as soon as it is returned; there is no change listener.
 *
 * <p><b>Before initialization.</b> Until Just-Tiers' client initializer has run,
 * {@link #isReady()} is false, every site is {@link SiteAnswer.Pending} and no request starts.
 */
public final class JustTiersQuery {

    /**
     * Increases when this API changes incompatibly or gains features. Initialized at run time
     * so that callers read the installed version rather than a value compiled into their mod.
     */
    public static final int API_VERSION = apiVersion();

    /** Every supported site's stable lowercase id, in Just-Tiers' display order. */
    public static final List<String> SITE_IDS = TierQuery.SITE_IDS;

    private static final TierQuery LIVE = new TierQuery(JustTiersClient::cache);

    private static int apiVersion() {
        return 1;
    }

    /** Whether Just-Tiers has initialized. Before then every answer is pending. */
    public static boolean isReady() {
        return LIVE.ready();
    }

    /**
     * One site's current answer. Site ids are read without case sensitivity.
     *
     * @throws IllegalArgumentException if {@code siteId} is not one of {@link #SITE_IDS}
     * @throws NullPointerException if an argument is null
     */
    public static SiteAnswer peek(UUID player, String siteId) {
        return LIVE.peek(player, siteId);
    }

    /** Every site's current answer, keyed by site id in {@link #SITE_IDS} order. */
    public static Map<String, SiteAnswer> peekAll(UUID player) {
        return LIVE.peekAll(player);
    }

    /**
     * The site's best placement when the site has answered with one, otherwise empty. Empty
     * does not mean unranked; use {@link #peek} to tell unranked from pending or unavailable.
     *
     * @throws IllegalArgumentException if {@code siteId} is not one of {@link #SITE_IDS}
     */
    public static Optional<Placement> best(UUID player, String siteId) {
        return LIVE.best(player, siteId);
    }

    private JustTiersQuery() {
    }
}
