package com.w0x7y.justtiers.render;

import com.w0x7y.justtiers.render.model.BadgePosition;
import com.w0x7y.justtiers.render.model.Segment;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/** Turns the Minecraft-free {@link Segment} list into a colored {@link Component}. */
public final class Segments {

    public static MutableComponent toComponent(List<Segment> segments) {
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

    /**
     * The badge and the name, in the order {@code position} asks for. The badge already
     * carries the space between the two, so there is nothing to insert here.
     */
    public static MutableComponent compose(List<Segment> badge, Component name,
                                           BadgePosition position) {
        if (badge.isEmpty()) {
            return name.copy();
        }
        return position.prepends()
                ? toComponent(badge).append(name)
                : name.copy().append(toComponent(badge));
    }

    private Segments() {
    }
}
