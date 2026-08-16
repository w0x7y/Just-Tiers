package com.w0x7y.justtiers.mixin;

import com.w0x7y.justtiers.JustTiersClient;
import com.w0x7y.justtiers.render.NametagRenderer;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public class PlayerMixin {

    @ModifyReturnValue(method = "getDisplayName", at = @At("RETURN"))
    private Component justtiers$prependTier(Component original) {
        Player self = (Player) (Object) this;
        if (!(self instanceof AbstractClientPlayer)) {
            return original;
        }
        // Your own tag, when you have asked not to see a badge on it. Checked here rather
        // than in the renderer because this is where the entity is in hand: the renderer
        // only ever sees a UUID. Lookup and scan still show you — this is about your own
        // nametag in third person, not about hiding yourself from yourself.
        if (JustTiersClient.config().isHideOwnBadge() && self == Minecraft.getInstance().player) {
            return original;
        }
        return NametagRenderer.decorate(self.getUUID(), original);
    }
}
