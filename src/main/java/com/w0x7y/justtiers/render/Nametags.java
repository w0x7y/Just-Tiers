package com.w0x7y.justtiers.render;

import com.w0x7y.justtiers.render.model.Badge;
import com.w0x7y.justtiers.render.model.BadgePosition;
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
     * A badge already turned into a component, and the side of the name it belongs on.
     * The component is never changed once built, so a nametag can keep one and put it
     * beside a fresh name every frame.
     */
    public record Drawn(Component badge, BadgePosition position) {

        /**
         * The badge and the name, in the order the badge was built for. The badge already
         * carries the space between the two, so there is nothing to insert here.
         */
        public MutableComponent beside(Component name) {
            return position.prepends()
                    ? Component.empty().append(badge).append(name)
                    : name.copy().append(badge);
        }
    }

    public static Drawn draw(Badge badge) {
        return new Drawn(toComponent(badge.segments()), badge.position());
    }

    public static MutableComponent compose(Badge badge, Component name) {
        return badge.isEmpty() ? name.copy() : draw(badge).beside(name);
    }

    private static MutableComponent toComponent(List<Segment> segments) {
        MutableComponent result = Component.empty();
        for (Segment segment : segments) {
            // An icon segment holds one glyph, drawn from Just-Tiers' own font; everything
            // else stays on the default one, which is the only font that can render words.
            MutableComponent piece = segment.icon()
                    ? Icons.of(segment.text().charAt(0))
                    : Component.literal(segment.text());
            result.append(piece.withStyle(style -> style.withColor(segment.color())));
        }
        return result;
    }

    private Nametags() {
    }
}
