package com.w0x7y.justtiers.gui;

import com.w0x7y.justtiers.JustTiersClient;
import com.w0x7y.justtiers.api.OnlinePlayers;
import com.w0x7y.justtiers.api.PlayerRef;
import com.w0x7y.justtiers.lookup.LookupResult;
import com.w0x7y.justtiers.lookup.LookupSession;
import com.w0x7y.justtiers.tier.Source;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Adapts the lookup model to client scheduling, translated errors and player skins. */
final class MinecraftLookupSession {
    private final LookupSession lookup;
    private PlayerSkin skin = PlayerSkins.placeholder();

    private MinecraftLookupSession(String name) {
        Minecraft client = Minecraft.getInstance();
        lookup = LookupSession.start(name, new LookupSession.Players() {
            @Override public Optional<PlayerRef> online(String requestedName) {
                return OnlinePlayers.find(requestedName);
            }
            @Override public CompletableFuture<Optional<PlayerRef>> resolve(String requestedName) {
                return JustTiersClient.names().resolve(requestedName);
            }
        }, JustTiersClient.cache(), client::execute);
        lookup.resolvedPlayer().thenAccept(found -> found.ifPresent(player ->
                PlayerSkins.resolve(player).thenAccept(loaded -> client.execute(() -> skin = loaded))));
    }

    static MinecraftLookupSession start(String name) {
        return new MinecraftLookupSession(name);
    }

    String name() { return lookup.name(); }
    PlayerSkin skin() { return skin; }
    LookupResult result(Source source) { return lookup.result(source); }
    boolean rankedNowhere() { return lookup.rankedNowhere(); }

    Optional<Component> error() {
        return lookup.error().map(error -> switch (error) {
            case INVALID_NAME -> Component.translatable("justtiers.lookup.invalidName", lookup.name());
            case UNKNOWN_PLAYER -> Component.translatable("justtiers.lookup.unknown", lookup.name());
            case NAME_UNAVAILABLE -> Component.translatable("justtiers.lookup.nameFailed", lookup.name());
        });
    }
}
