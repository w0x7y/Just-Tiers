package com.w0x7y.justtiers.resolve;

import com.w0x7y.justtiers.tier.Gamemode;
import com.w0x7y.justtiers.tier.Tier;

/** One tier to display, together with the gamemode that earned it. */
public record ResolvedTier(Gamemode gamemode, Tier tier) {
}
