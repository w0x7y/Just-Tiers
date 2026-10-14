/**
 * The public tier-query API for other client mods. {@link com.w0x7y.justtiers.query.JustTiersQuery}
 * is the entry point; every type it returns is declared in this package and holds only
 * standard Java values. Other Just-Tiers packages are internal and can change in any release.
 *
 * <p>Answers keep five cases apart: disabled, pending, unavailable, unranked and ranked. Only
 * an unranked or ranked answer is evidence about a player's standing on that site. Queries
 * obey the player's nametag and site toggles. See {@code docs/api.md} for usage, threading
 * and network consequences.
 */
package com.w0x7y.justtiers.query;
