package com.w0x7y.justtiers.api;

import java.util.UUID;

/**
 * A player a lookup can be run against: the account UUID the leaderboards are keyed by,
 * plus the name to print it under. Produced either from the client's tab list or from
 * Mojang's profile API, so the rest of the lookup does not care which one answered.
 */
public record PlayerRef(String name, UUID uuid) {

    /**
     * Whether a UUID can be on the leaderboards at all. They are keyed by online-mode
     * account UUIDs, which are version 4; offline-mode and proxy servers mint version 3
     * UUIDs, and NPCs use whatever they like. Asking a site about any other UUID only
     * spends its request budget on an answer that cannot exist.
     */
    public static boolean isAccount(UUID uuid) {
        return uuid != null && uuid.version() == 4;
    }
}
