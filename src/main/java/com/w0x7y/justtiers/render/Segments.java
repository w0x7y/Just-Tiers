package com.w0x7y.justtiers.render;

import com.w0x7y.justtiers.render.model.Segment;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/** Turns the Minecraft-free {@link Segment} list into a coloured {@link Component}. */
public final class Segments {

    public static MutableComponent toComponent(List<Segment> segments) {
        MutableComponent result = Component.empty();
        for (Segment segment : segments) {
            result.append(Component.literal(segment.text())
                    .withStyle(style -> style.withColor(segment.color())));
        }
        return result;
    }

    private Segments() {
    }
}
