package com.w0x7y.justtiers.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.w0x7y.justtiers.JustTiers;
import com.w0x7y.justtiers.render.model.PixelArt;
import com.w0x7y.justtiers.render.model.PixelArt.CompositeIcon;
import com.w0x7y.justtiers.render.model.PixelArt.Grid;
import com.w0x7y.justtiers.render.model.PixelArt.TextureRef;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Bridges {@link PixelArt.CompositeIcon} to Minecraft's {@link ResourceManager} during
 * bitmap font loading. Only handles synthetic {@code justtiers:textures/composite/*.png}
 * identifiers, and still yields to any resource pack that explicitly overrides one.
 */
public final class CompositeIcons {

    public static Optional<InputStream> open(ResourceManager manager, Identifier textureId) throws IOException {
        if (!JustTiers.MOD_ID.equals(textureId.getNamespace())) {
            return Optional.empty();
        }
        Optional<CompositeIcon> composite = CompositeIcon.fromTexturePath(textureId.getPath());
        if (composite.isEmpty()) {
            return Optional.empty();
        }
        // Let an explicit resource-pack texture win when one exists.
        if (manager.getResource(textureId).isPresent()) {
            return Optional.empty();
        }
        Grid composed = composite.get().compose(ref -> readGrid(manager, ref));
        return Optional.of(new ByteArrayInputStream(PixelArt.encodePng(composed)));
    }

    private static Optional<Grid> readGrid(ResourceManager manager, TextureRef ref) throws IOException {
        Identifier id = Identifier.fromNamespaceAndPath(ref.namespace(), "textures/" + ref.path());
        Optional<Resource> resource = manager.getResource(id);
        if (resource.isEmpty()) {
            return Optional.empty();
        }
        try (InputStream in = resource.get().open();
             NativeImage image = NativeImage.read(in)) {
            int width = image.getWidth();
            int height = image.getHeight();
            int[] argb = new int[width * height];
            for (int y = 0; y < height; y++) {
                int row = y * width;
                for (int x = 0; x < width; x++) {
                    argb[row + x] = image.getPixel(x, y);
                }
            }
            return Optional.of(new Grid(width, height, argb));
        }
    }

    private CompositeIcons() {
    }
}
