package com.w0x7y.justtiers.render;

import com.w0x7y.justtiers.JustTiersClient;
import com.w0x7y.justtiers.render.model.NametagModel;
import com.w0x7y.justtiers.render.model.Segment;
import com.w0x7y.justtiers.resolve.DisplayMode;
import com.w0x7y.justtiers.resolve.ResolvedTier;
import com.w0x7y.justtiers.resolve.TierResolver;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Converts resolved tiers into the Component prefix shown in front of a player's name. */
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

        for (Source source : sourcesFor(mode)) {
            Optional<Map<String, Tier>> tiers = JustTiersClient.cache().peek(source, uuid);
            if (tiers.isEmpty()) {
                // Still loading. Render the plain name now; the nametag refreshes next frame.
                return original;
            }
            tiersBySource.put(source, tiers.get());
        }

        List<ResolvedTier> resolved =
                TierResolver.resolve(mode, tiersBySource, config.selectedGamemodesBySource());
        List<Segment> segments = NametagModel.build(resolved);
        if (segments.isEmpty()) {
            return original;
        }

        MutableComponent prefix = Component.empty();
        for (Segment segment : segments) {
            prefix.append(Component.literal(segment.text())
                    .withStyle(style -> style.withColor(segment.color())));
        }
        return prefix.append(original);
    }

    private static List<Source> sourcesFor(DisplayMode mode) {
        return mode.singleSource().map(List::of).orElseGet(() -> List.of(Source.values()));
    }

    private NametagRenderer() {
    }
}
