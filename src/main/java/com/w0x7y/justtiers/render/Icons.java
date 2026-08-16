package com.w0x7y.justtiers.render;

import com.w0x7y.justtiers.JustTiers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

/**
 * The gamemode glyphs, and the font they live in.
 *
 * <p>That font is Just-Tiers' own — {@code justtiers:icons} — rather than an addition to
 * {@code minecraft:default}. A mod that writes {@code assets/minecraft/font/default.json}
 * is overriding a vanilla file, which puts it in a fight it cannot win with every
 * resource pack and every other mod that adds a glyph: pack order decides who survives.
 * A private font is ours alone and cannot collide.
 *
 * <p>The trade is that a private font inherits none of the vanilla fallbacks, so anything
 * drawn in it that is not one of our thirty-two codepoints comes out as a missing-glyph
 * box. That is exactly why this class exists: one character goes through the font here,
 * and no caller ever spells the identifier itself.
 */
public final class Icons {

    /** {@code assets/justtiers/font/icons.json}. */
    public static final Identifier FONT_ID =
            Identifier.fromNamespaceAndPath(JustTiers.MOD_ID, "icons");

    /** The same font, in the form {@code Style.withFont} wants. */
    public static final FontDescription FONT = new FontDescription.Resource(FONT_ID);

    /** One gamemode glyph, in the font that can actually draw it. */
    public static MutableComponent of(char icon) {
        return Component.literal(String.valueOf(icon))
                .withStyle(style -> style.withFont(FONT));
    }

    private Icons() {
    }
}
