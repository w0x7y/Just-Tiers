package com.w0x7y.justtiers.render;

import com.w0x7y.justtiers.JustTiersClient;
import com.w0x7y.justtiers.render.model.NametagModel;
import com.w0x7y.justtiers.render.model.NametagStyle;
import com.w0x7y.justtiers.render.model.Segment;
import com.w0x7y.justtiers.resolve.DisplayMode;
import com.w0x7y.justtiers.resolve.ResolvedTier;
import com.w0x7y.justtiers.resolve.TierResolver;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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

        // Sources still in flight are simply left out, so a badge appears as soon as the
        // first site answers instead of waiting on the slowest one. It fills in over the
        // next few frames as the others land.
        for (Source source : sourcesFor(mode)) {
            JustTiersClient.cache().peek(source, uuid)
                    .ifPresent(tiers -> tiersBySource.put(source, tiers));
        }
        if (tiersBySource.isEmpty()) {
            return original;
        }

        List<ResolvedTier> resolved = TierResolver.resolve(
                mode, tiersBySource, config.selectedGamemodesBySource(), config.isShowRetired());
        NametagStyle style = config.nametagStyle();
        List<Segment> segments = NametagModel.build(resolved, style);
        if (segments.isEmpty()) {
            return original;
        }

        return Segments.compose(segments, original, style.position());
    }

    private static List<Source> sourcesFor(DisplayMode mode) {
        return mode.singleSource().map(List::of).orElseGet(() -> List.of(Source.values()));
    }

    private NametagRenderer() {
    }
}
