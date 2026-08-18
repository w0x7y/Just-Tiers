package com.w0x7y.justtiers.debug;

import com.w0x7y.justtiers.resolve.DisplayMode;

import java.time.Duration;
import java.util.List;

/**
 * Everything {@code /justtiers debug} reports, gathered in one pass.
 *
 * <p>Collected by the command — which is the only part that may touch Minecraft — and
 * handed to {@link DebugReport}, which turns it into text without knowing where any of it
 * came from. That split is what lets the whole format be unit-tested against numbers a
 * test makes up, including the states that are hard to produce on purpose: a site paused
 * mid-outage, a lookup that has never once succeeded.
 *
 * @param cacheTtl zero when expiry is off and answers are kept for the whole session.
 */
public record DebugSnapshot(String modVersion,
                            String minecraftVersion,
                            String loaderVersion,
                            boolean enabled,
                            DisplayMode displayMode,
                            Duration cacheTtl,
                            int novaIndexedPlayers,
                            int novaRefreshMinutes,
                            List<SiteDiagnostics> sites) {

    public DebugSnapshot {
        sites = List.copyOf(sites);
    }
}
