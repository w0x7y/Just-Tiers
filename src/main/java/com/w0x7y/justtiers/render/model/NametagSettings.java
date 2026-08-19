package com.w0x7y.justtiers.render.model;

import com.w0x7y.justtiers.resolve.DisplayMode;
import com.w0x7y.justtiers.tier.Source;

import java.util.EnumMap;
import java.util.Map;

/**
 * Everything about the settings that changes what a nametag looks like: whether to draw
 * one at all, which tiers it picks, and how they are laid out.
 *
 * <p>Two things produce these, which is the point of naming the shape. The world nametag
 * reads the <em>saved</em> config; the config screen reads YACL's <em>pending</em> values,
 * so its preview agrees with what Save would write and Cancel discards it along with
 * everything else. Both then go through the same {@link Badge}, which is what keeps the
 * preview honest about the real thing.
 */
public record NametagSettings(boolean enabled,
                              DisplayMode displayMode,
                              Map<Source, String> selectedGamemodes,
                              boolean showRetired,
                              NametagStyle style) {

    public NametagSettings {
        selectedGamemodes = Map.copyOf(selectedGamemodes);
        style = style == null ? NametagStyle.DEFAULT : style;
    }

    /**
     * The same settings with one site's gamemode swapped. The gamemode grid previews a
     * tile by hovering it, which is exactly this and nothing else.
     */
    public NametagSettings withGamemode(Source source, String slug) {
        Map<Source, String> swapped = new EnumMap<>(Source.class);
        swapped.putAll(selectedGamemodes);
        swapped.put(source, slug);
        return new NametagSettings(enabled, displayMode, swapped, showRetired, style);
    }

    /**
     * The made-up badge these settings would draw. {@code timeMillis} drives the
     * preview's active/retired cycle, so a widget that redraws every frame gets an
     * animated tag out of a record that holds no clock of its own.
     */
    public Badge previewBadge(long timeMillis) {
        return Badge.preview(displayMode, selectedGamemodes, showRetired, timeMillis, style);
    }
}
