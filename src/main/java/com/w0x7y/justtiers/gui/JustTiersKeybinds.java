package com.w0x7y.justtiers.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.w0x7y.justtiers.JustTiers;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Opens the config screen from a key or from {@code /justtiers gui}.
 *
 * <p>The key is unbound by default on purpose: a client mod that claims a key on first
 * launch is a nuisance, and the command and ModMenu both reach the same screen.
 */
public final class JustTiersKeybinds {

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(JustTiers.MOD_ID, "main"));

    private static KeyMapping openConfig;

    /**
     * Set by a command and consumed on the next tick. A command runs while the chat
     * screen is still closing, so a screen opened inside the command body would be
     * overwritten immediately; Fabric events cannot be unregistered, so the one
     * permanent tick handler below picks this up instead of a one-shot listener.
     *
     * <p>A supplier rather than a screen: whichever screen this ends up being should be
     * built on the tick it is shown on, not on the tick it was asked for.
     */
    private static final AtomicReference<Supplier<Screen>> pending = new AtomicReference<>();

    public static void register() {
        openConfig = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.justtiers.open_config",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Supplier<Screen> requested = pending.getAndSet(null);
            boolean keyPressed = false;
            while (openConfig.consumeClick()) {
                keyPressed = true;
            }
            // The key always means the config screen, and outranks anything queued: it
            // was pressed after whatever asked for a screen a tick ago.
            if (keyPressed) {
                requested = configScreen();
            }
            if (requested != null) {
                client.setScreenAndShow(requested.get());
            }
        });
    }

    /** Asks for the config screen on the next client tick. */
    public static void requestOpen() {
        requestOpen(configScreen());
    }

    /**
     * Asks for a screen on the next client tick. A second request in the same tick
     * replaces the first: only one screen can be shown, and it should be the newer one.
     */
    public static void requestOpen(Supplier<Screen> screen) {
        pending.set(screen);
    }

    private static Supplier<Screen> configScreen() {
        // No parent: the key and the command both fire from gameplay, so closing the
        // config should drop straight back into the world.
        return () -> JustTiersScreens.create(null);
    }

    private JustTiersKeybinds() {
    }
}
