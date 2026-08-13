package com.w0x7y.justtiers.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * The name the preview nametag carries: the player's own account name, so the config
 * screen shows the tag as they will see it above their own head.
 *
 * <p>Deliberately the account name rather than the in-world display name — the config
 * screen opens from the title screen too, where no world and no display name exist, and
 * a preview that renamed itself on joining a server would look like a bug.
 */
public final class PreviewName {

    public static Component component() {
        String name = accountName();
        return name == null
                ? Component.translatable("justtiers.preview.player")
                : Component.literal(name);
    }

    private static String accountName() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getGameProfile() == null) {
            return null;
        }
        String name = client.getGameProfile().getName();
        return name == null || name.isBlank() ? null : name;
    }

    private PreviewName() {
    }
}
