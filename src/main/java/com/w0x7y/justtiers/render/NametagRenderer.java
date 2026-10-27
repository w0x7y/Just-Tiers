package com.w0x7y.justtiers.render;

import com.w0x7y.justtiers.render.model.Badge;
import com.w0x7y.justtiers.render.model.BadgeMemo;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * Puts a player's badge beside their name. Deciding what that badge is belongs to
 * {@link Badge}, keeping it until it changes to {@link BadgeMemo}, and reading the running
 * mod to {@link LiveTierView}, which leaves this class with the one thing that genuinely
 * needs Minecraft.
 */
public final class NametagRenderer {

    /** A memo for one player entity. The entity holds it, so it goes when the entity does. */
    public static BadgeMemo<Nametags.Drawn> memo() {
        return new BadgeMemo<>(badge -> badge.isEmpty() ? null : Nametags.draw(badge));
    }

    public static Component decorate(BadgeMemo<Nametags.Drawn> memo, UUID uuid, Component original) {
        Nametags.Drawn badge = memo.get(LiveTierView.INSTANCE, uuid);
        // Returning the original rather than an equal copy: this runs per player per
        // frame, and most players hold no tiers at all.
        return badge == null ? original : badge.beside(original);
    }

    private NametagRenderer() {
    }
}
