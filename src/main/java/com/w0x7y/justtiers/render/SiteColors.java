package com.w0x7y.justtiers.render;

import com.w0x7y.justtiers.JustTiersClient;
import com.w0x7y.justtiers.tier.Source;

/**
 * What colour a site is drawn in, right now, under whatever palette is configured.
 *
 * <p>Every screen goes through here rather than reading {@link Source#defaultColor()},
 * because colour carries exactly one meaning in this UI — which leaderboard this is — and
 * a screen that read the constant would keep saying it in a colour the user has changed.
 *
 * <p>The nametag does not use this. {@code NametagModel} is Minecraft-free and
 * unit-tested, so its colours arrive in the {@code NametagStyle} it is handed instead.
 */
public final class SiteColors {

    public static int of(Source source) {
        return JustTiersClient.config().colorOf(source);
    }

    private SiteColors() {
    }
}
