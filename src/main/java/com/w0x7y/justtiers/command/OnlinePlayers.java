package com.w0x7y.justtiers.command;

import com.mojang.authlib.GameProfile;
import com.w0x7y.justtiers.api.PlayerRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads the client's player list — everyone on the server, whether or not they are
 * anywhere near render distance, which is exactly the set {@code /justtiers lookup} is
 * for. Resolving a name here costs nothing and never touches the network.
 */
final class OnlinePlayers {

    /**
     * @return the account behind an online name, matched case-insensitively. Empty for a
     *         name nobody on the server holds, and also for a player whose UUID is not a
     *         v4 account UUID: offline-mode and proxy servers mint v3 UUIDs, which are
     *         not what the leaderboards are keyed by, so those fall through to Mojang
     *         rather than being looked up as a player who will never be found.
     */
    static Optional<PlayerRef> find(String name) {
        if (name == null) {
            return Optional.empty();
        }
        for (GameProfile profile : profiles()) {
            if (profile.name() != null && profile.name().equalsIgnoreCase(name)) {
                return profile.id() != null && profile.id().version() == 4
                        ? Optional.of(new PlayerRef(profile.name(), profile.id()))
                        : Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** Every online name, for tab-completion. Empty on the title screen. */
    static List<String> names() {
        List<String> names = new ArrayList<>();
        for (GameProfile profile : profiles()) {
            if (profile.name() != null && !profile.name().isBlank()) {
                names.add(profile.name());
            }
        }
        return names;
    }

    private static List<GameProfile> profiles() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return List.of();
        }
        List<GameProfile> profiles = new ArrayList<>();
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            GameProfile profile = info.getProfile();
            if (profile != null) {
                profiles.add(profile);
            }
        }
        return profiles;
    }

    private OnlinePlayers() {
    }
}
