package com.w0x7y.justtiers.render;

import com.w0x7y.justtiers.JustTiersClient;
import com.w0x7y.justtiers.render.model.Badge;
import com.w0x7y.justtiers.resolve.DisplayMode;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * The nametag path's adapter: it reads the live config and cache, hands them to
 * {@link Badge}, and puts the answer in front of (or behind) the player's name.
 * Everything it decides is in those two calls, which is what keeps this class thin
 * enough not to need the game running to be understood.
 */
public final class NametagRenderer {

    public static Component decorate(UUID uuid, Component original) {
        var config = JustTiersClient.config();
        if (!config.isEnabled() || uuid == null) {
            return original;
        }
        // Offline-mode and NPC entities use v3 UUIDs and are never in these leaderboards.
        if (uuid.version() != 4) {
            return original;
        }

        DisplayMode mode = config.getDisplayMode();
        Map<Source, Map<String, Tier>> tiersBySource = new EnumMap<>(Source.class);
        for (Source source : mode.sources()) {
            JustTiersClient.cache().peek(source, uuid)
                    .ifPresent(tiers -> tiersBySource.put(source, tiers));
        }

        Badge badge = Badge.forPlayer(mode, tiersBySource, config.selectedGamemodesBySource(),
                config.isShowRetired(), config.nametagStyle());
        // Returning the original rather than an equal copy: this runs per player per
        // frame, and most players hold no tiers at all.
        return badge.isEmpty() ? original : Nametags.compose(badge, original);
    }

    private NametagRenderer() {
    }
}
