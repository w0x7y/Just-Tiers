package com.w0x7y.justtiers.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.w0x7y.justtiers.JustTiers;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

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
     * Set by the command and consumed on the next tick. A command runs while the chat
     * screen is still closing, so a screen opened inside the command body would be
     * overwritten immediately; Fabric events cannot be unregistered, so the one
     * permanent tick handler below picks this up instead of a one-shot listener.
     */
    private static volatile boolean openRequested;

    public static void register() {
        openConfig = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.justtiers.open_config",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean open = openRequested;
            openRequested = false;
            while (openConfig.consumeClick()) {
                open = true;
            }
            if (open) {
                // No parent: the key and the command both fire from gameplay, so
                // closing the config should drop straight back into the world.
                client.setScreenAndShow(JustTiersScreens.create(null));
            }
        });
    }

    /** Asks for the config screen on the next client tick. */
    public static void requestOpen() {
        openRequested = true;
    }

    private JustTiersKeybinds() {
    }
}
