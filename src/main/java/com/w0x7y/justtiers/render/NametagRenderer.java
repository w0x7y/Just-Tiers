package com.w0x7y.justtiers.render;

import com.w0x7y.justtiers.render.model.Badge;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Puts a player's badge beside their name. Deciding what that badge is belongs to
 * {@link Badge}, and reading the running mod belongs to {@link LiveTierView}, which
 * leaves this class with the one thing that genuinely needs Minecraft.
 */
public final class NametagRenderer {

    public static Component decorate(UUID uuid, Component original) {
        Badge badge = Badge.forPlayer(LiveTierView.INSTANCE, uuid);
        // Returning the original rather than an equal copy: this runs per player per
        // frame, and most players hold no tiers at all.
        return badge.isEmpty() ? original : Nametags.compose(badge, original);
    }

    private NametagRenderer() {
    }
}
