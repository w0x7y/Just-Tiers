package com.w0x7y.justtiers.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.w0x7y.justtiers.render.CompositeIcons;
import net.minecraft.client.gui.font.providers.BitmapProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

@Mixin(BitmapProvider.Definition.class)
public class BitmapProviderMixin {

    @WrapOperation(
            method = "load",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/resources/ResourceManager;open(Lnet/minecraft/resources/Identifier;)Ljava/io/InputStream;"
            )
    )
    private InputStream justtiers$openCompositeIcon(
            ResourceManager manager, Identifier id, Operation<InputStream> original) throws IOException {
        Optional<InputStream> composite = CompositeIcons.open(manager, id);
        if (composite.isPresent()) {
            return composite.get();
        }
        return original.call(manager, id);
    }
}
