package com.w0x7y.justtiers.render;

import com.w0x7y.justtiers.JustTiersClient;
import com.w0x7y.justtiers.render.model.NametagSettings;
import com.w0x7y.justtiers.render.model.TierView;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The running mod, seen through {@link TierView}: the saved config and the live cache.
 * The only class on the nametag path that touches the static hub, so everything above it
 * can be answered without the game.
 */
public final class LiveTierView implements TierView {

    public static final LiveTierView INSTANCE = new LiveTierView();

    @Override
    public NametagSettings settings() {
        return JustTiersClient.config().nametagSettings();
    }

    @Override
    public Optional<Map<String, Tier>> peek(Source source, UUID uuid) {
        return JustTiersClient.cache().peek(source, uuid);
    }

    private LiveTierView() {
    }
}
