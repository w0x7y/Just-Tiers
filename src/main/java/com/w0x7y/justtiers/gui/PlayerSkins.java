package com.w0x7y.justtiers.gui;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.w0x7y.justtiers.JustTiers;
import com.w0x7y.justtiers.api.PlayerRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Finds the skin to draw on the lookup screen. A player on the server already has one
 * loaded, so that costs nothing; anyone else needs their profile fetched from Mojang
 * before the skin behind it can be downloaded.
 *
 * <p>Every path ends in a skin. A profile that cannot be fetched, a texture that will
 * not download and a player who has never set a skin all fall back to the default skin
 * for that account, because a blank space where a body should be looks like a bug.
 */
public final class PlayerSkins {

    /**
     * Only fetched skins are remembered. An online player's skin is read straight out of
     * the player list every time, so someone who changes skin mid-session is drawn as
     * they are now rather than as they were the first time they were looked up.
     */
    private static final Map<UUID, CompletableFuture<PlayerSkin>> FETCHED =
            new ConcurrentHashMap<>();

    /**
     * One thread, because {@code fetchProfile} blocks and this is never more than a
     * lookup or two at a time. Daemon, so it cannot hold the game open on quit.
     */
    private static final Executor PROFILES = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "just-tiers-skins");
        thread.setDaemon(true);
        return thread;
    });

    /** The skin to draw before anything is known about who is being looked up. */
    public static PlayerSkin placeholder() {
        return DefaultPlayerSkin.getDefaultSkin();
    }

    public static CompletableFuture<PlayerSkin> resolve(PlayerRef player) {
        Optional<PlayerSkin> online = fromPlayerList(player.uuid());
        if (online.isPresent()) {
            return CompletableFuture.completedFuture(online.get());
        }
        return FETCHED.computeIfAbsent(player.uuid(), PlayerSkins::fetch);
    }

    private static Optional<PlayerSkin> fromPlayerList(UUID uuid) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return Optional.empty();
        }
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            GameProfile profile = info.getProfile();
            if (profile != null && uuid.equals(profile.id())) {
                return Optional.ofNullable(info.getSkin());
            }
        }
        return Optional.empty();
    }

    private static CompletableFuture<PlayerSkin> fetch(UUID uuid) {
        Minecraft minecraft = Minecraft.getInstance();
        return CompletableFuture
                .supplyAsync(() -> minecraft.services().sessionService().fetchProfile(uuid, false),
                        PROFILES)
                // Back on the client thread: the skin manager hands out textures, and the
                // download it starts registers one.
                .thenComposeAsync(result -> {
                    GameProfile profile = result == null ? null : result.profile();
                    if (profile == null) {
                        return CompletableFuture.completedFuture(DefaultPlayerSkin.get(uuid));
                    }
                    return minecraft.getSkinManager().get(profile)
                            .thenApply(skin -> skin.orElseGet(() -> DefaultPlayerSkin.get(uuid)));
                }, (Executor) minecraft::execute)
                .exceptionally(error -> {
                    // Not a warning: a missing skin costs the screen a Steve and nothing
                    // else, and Mojang rate-limiting profile fetches is routine.
                    JustTiers.LOGGER.debug("Could not fetch the skin for {}: {}",
                            uuid, error.toString());
                    return DefaultPlayerSkin.get(uuid);
                });
    }

    private PlayerSkins() {
    }
}
