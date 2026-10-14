package com.w0x7y.justtiers.api;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerRefTest {

    @Test
    void onlyOnlineModeAccountUuidsCanBeOnALeaderboard() {
        assertTrue(PlayerRef.isAccount(UUID.fromString("11111111-1111-4111-8111-111111111111")));
        assertFalse(PlayerRef.isAccount(
                UUID.nameUUIDFromBytes("OfflinePlayer:Steve".getBytes(StandardCharsets.UTF_8))));
        assertFalse(PlayerRef.isAccount(null));
    }
}
