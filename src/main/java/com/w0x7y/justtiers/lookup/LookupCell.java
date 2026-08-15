package com.w0x7y.justtiers.lookup;

import com.w0x7y.justtiers.tier.Gamemode;
import com.w0x7y.justtiers.tier.Tier;

import java.util.Optional;

/**
 * One gamemode's column in a lookup row: the gamemode itself, and the tier the player
 * holds in it if any. An empty tier is drawn as dashes — the site answered, and this is
 * a gamemode the player has never placed in.
 */
public record LookupCell(Gamemode gamemode, Optional<Tier> tier) {

    public LookupCell {
        if (gamemode == null) {
            throw new IllegalArgumentException("cell needs a gamemode");
        }
        tier = tier == null ? Optional.empty() : tier;
    }

    public boolean ranked() {
        return tier.isPresent();
    }
}
