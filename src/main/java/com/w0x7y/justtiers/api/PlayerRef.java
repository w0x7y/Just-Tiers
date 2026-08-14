package com.w0x7y.justtiers.api;

import java.util.UUID;

/**
 * A player a lookup can be run against: the account UUID the leaderboards are keyed by,
 * plus the name to print it under. Produced either from the client's tab list or from
 * Mojang's profile API, so the rest of the lookup does not care which one answered.
 */
public record PlayerRef(String name, UUID uuid) {
}
