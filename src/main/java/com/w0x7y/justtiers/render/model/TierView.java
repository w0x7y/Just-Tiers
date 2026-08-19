package com.w0x7y.justtiers.render.model;

import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What the nametag path is allowed to know about the running mod: the settings in force,
 * and whatever the sites have already said. Both are live — a badge is rebuilt every
 * frame, and the answer changes as settings are saved and lookups land.
 *
 * <p>This exists so that deciding what a player wears does not require the game to be
 * running. {@link com.w0x7y.justtiers.render.LiveTierView} is the one that reads the real
 * config and cache; tests supply their own.
 */
public interface TierView {

    /** The settings in force right now. */
    NametagSettings settings();

    /**
     * What this site has already said about this player, or empty while it is still
     * being asked. Never blocks and never starts a lookup: this is read per player per
     * frame, and a site that has not answered yet is simply left out of the badge.
     */
    Optional<Map<String, Tier>> peek(Source source, UUID uuid);
}
