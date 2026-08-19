package com.w0x7y.justtiers.render;

import com.w0x7y.justtiers.render.model.Badge;
import com.w0x7y.justtiers.render.model.Segment;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * Where a {@link Badge} meets Minecraft: the one place the Minecraft-free segments
 * become a colored {@link Component}, and the only thing on the nametag path that
 * needs the game to be loaded.
 */
public final class Nametags {

    /**
     * The badge and the name, in the order the badge was built for. The badge already
     * carries the space between the two, so there is nothing to insert here.
     */
    public static MutableComponent compose(Badge badge, Component name) {
        if (badge.isEmpty()) {
            return name.copy();
        }
        return badge.position().prepends()
                ? toComponent(badge.segments()).append(name)
                : name.copy().append(toComponent(badge.segments()));
    }

    private static MutableComponent toComponent(List<Segment> segments) {
        MutableComponent result = Component.empty();
        for (Segment segment : segments) {
            // An icon is drawn from Just-Tiers' own font; everything else stays on the
            // default one, which is the only font that can render words.
            MutableComponent piece = segment.icon()
                    ? Component.literal(segment.text()).withStyle(
                            style -> style.withFont(Icons.FONT))
                    : Component.literal(segment.text());
            result.append(piece.withStyle(style -> style.withColor(segment.color())));
        }
        return result;
    }

    private Nametags() {
    }
}
