package com.w0x7y.justtiers.render.model;

import com.w0x7y.justtiers.render.model.PixelArt.CompositeIcon;
import com.w0x7y.justtiers.render.model.PixelArt.Grid;
import com.w0x7y.justtiers.render.model.PixelArt.TextureRef;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PixelArtTest {

    @Test
    void flipHorizontalMirrorsEachRow() {
        Grid grid = new Grid(2, 2, new int[] {
                0xFF112233, 0xFF445566,
                0x00000000, 0xFF778899
        });
        Grid flipped = grid.flipHorizontal();
        assertEquals(0xFF445566, flipped.pixel(0, 0));
        assertEquals(0xFF112233, flipped.pixel(1, 0));
        assertEquals(0xFF778899, flipped.pixel(0, 1));
        assertEquals(0x00000000, flipped.pixel(1, 1));
    }

    @Test
    void tintMultipliesVisiblePixelsAndPreservesTransparency() {
        Grid grid = new Grid(2, 1, new int[] { 0x80FFFFFF, 0x00FFFFFF });
        Grid tinted = grid.tint(0xF82423);
        assertEquals(0x80F82423, tinted.pixel(0, 0));
        assertEquals(0x00000000, tinted.pixel(1, 0));
    }

    @Test
    void overBlendsAndScalesMismatchedPackResolutions() {
        Grid base = new Grid(1, 1, new int[] { 0xFF0000FF });
        Grid top = new Grid(2, 2, new int[] {
                0xFFFF0000, 0x00000000,
                0x00000000, 0x8000FF00
        });
        Grid composed = base.over(top);
        assertEquals(2, composed.width());
        assertEquals(2, composed.height());
        assertEquals(0xFFFF0000, composed.pixel(0, 0));
        assertEquals(0xFF0000FF, composed.pixel(1, 0));
        assertEquals(0xFF0000FF, composed.pixel(0, 1));
        int blended = composed.pixel(1, 1);
        assertEquals(0xFF, blended >>> 24);
        assertTrue(((blended >>> 8) & 0xFF) > 100, "green channel blended");
        assertTrue((blended & 0xFF) > 100, "blue channel retained");
    }

    @Test
    void encodePngRoundTripsPixelsThroughImageIo() throws IOException {
        Grid grid = new Grid(2, 2, new int[] {
                0xFFFF0000, 0x8000FF00,
                0x00000000, 0xFF123456
        });
        byte[] bytes = PixelArt.encodePng(grid);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
        assertNotNull(decoded);
        assertEquals(2, decoded.getWidth());
        assertEquals(2, decoded.getHeight());
        assertEquals(0xFFFF0000, decoded.getRGB(0, 0));
        assertEquals(0x8000FF00, decoded.getRGB(1, 0));
        assertEquals(0x00000000, decoded.getRGB(0, 1));
        assertEquals(0xFF123456, decoded.getRGB(1, 1));
    }

    @Test
    void textBadgeRendersReadableSquareGlyphs() throws IOException {
        Grid sm = PixelArt.textBadge("SM");
        Grid es = PixelArt.textBadge("ES");
        assertEquals(12, sm.width());
        assertEquals(12, sm.height());
        assertNotEquals(sm, es);

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(PixelArt.encodePng(sm)));
        assertNotNull(decoded);
        assertEquals(12, decoded.getWidth());
        assertEquals(12, decoded.getHeight());
    }

    @Test
    void missingSourceTexturesFallBackToTextBadges() throws IOException {
        Grid mace = new Grid(2, 2, new int[] { 0xFFAAAAAA, 0, 0, 0 });
        Grid elytra = new Grid(2, 2, new int[] { 0, 0, 0xFF888899, 0 });
        Map<TextureRef, Grid> withoutSpear = Map.of(
                TextureRef.minecraft("item/mace.png"), mace,
                TextureRef.minecraft("item/elytra.png"), elytra);

        assertEquals(PixelArt.textBadge("SM"),
                CompositeIcon.SPEAR_MACE.compose(ref -> Optional.ofNullable(withoutSpear.get(ref))));
        assertEquals(PixelArt.textBadge("ES"),
                CompositeIcon.ELYTRA_SPEAR.compose(ref -> Optional.ofNullable(withoutSpear.get(ref))));

        for (CompositeIcon icon : CompositeIcon.values()) {
            assertEquals(PixelArt.textBadge(icon.fallbackLabel()), icon.compose(ref -> Optional.empty()),
                    icon.name());
        }
    }

    @Test
    void compositeRecipesCombineSourcesWhenPresent() throws IOException {
        Grid overlay = new Grid(2, 2, new int[] {
                0xFFFFFFFF, 0x00000000,
                0xFFFFFFFF, 0x00000000
        });
        Grid splashBottle = new Grid(2, 2, new int[] {
                0x00000000, 0xFFCCCCCC,
                0xFF222222, 0x00000000
        });
        Grid regularBottle = new Grid(2, 2, new int[] {
                0x00000000, 0xFFEEEEEE,
                0xFF333333, 0x00000000
        });
        Grid mace = new Grid(2, 2, new int[] {
                0xFFAAAAAA, 0x00000000,
                0x00000000, 0x00000000
        });
        Grid spear = new Grid(2, 2, new int[] {
                0xFF111111, 0x00000000,
                0x00000000, 0xFF555555
        });
        Grid elytra = new Grid(2, 2, new int[] {
                0xFF888899, 0xFF888899,
                0x00000000, 0x00000000
        });
        Map<TextureRef, Grid> sources = Map.of(
                TextureRef.minecraft("item/potion_overlay.png"), overlay,
                TextureRef.minecraft("item/splash_potion.png"), splashBottle,
                TextureRef.minecraft("item/potion.png"), regularBottle,
                TextureRef.minecraft("item/mace.png"), mace,
                TextureRef.minecraft("item/netherite_spear.png"), spear,
                TextureRef.minecraft("item/elytra.png"), elytra);

        Grid pot = CompositeIcon.POT.compose(ref -> Optional.ofNullable(sources.get(ref)));
        assertEquals(0xFFF82423, pot.pixel(0, 0));
        assertEquals(0xFFCCCCCC, pot.pixel(1, 0));
        assertEquals(0xFF222222, pot.pixel(0, 1));

        Grid debuff = CompositeIcon.DEBUFF.compose(ref -> Optional.ofNullable(sources.get(ref)));
        assertEquals(0xFF5AB835, debuff.pixel(0, 0));

        Grid speed = CompositeIcon.SPEED.compose(ref -> Optional.ofNullable(sources.get(ref)));
        assertEquals(0xFF33EBFF, speed.pixel(0, 0));
        assertEquals(0xFFEEEEEE, speed.pixel(1, 0));

        Grid spearMace = CompositeIcon.SPEAR_MACE.compose(ref -> Optional.ofNullable(sources.get(ref)));
        // Mace pixel at (0,0) flips horizontally to (1,0); spear draws over (0,0) and (1,1).
        assertEquals(0xFF111111, spearMace.pixel(0, 0));
        assertEquals(0xFFAAAAAA, spearMace.pixel(1, 0));
        assertEquals(0xFF555555, spearMace.pixel(1, 1));

        Grid elytraSpear = CompositeIcon.ELYTRA_SPEAR.compose(ref -> Optional.ofNullable(sources.get(ref)));
        assertEquals(0xFF111111, elytraSpear.pixel(0, 0));
        assertEquals(0xFF888899, elytraSpear.pixel(1, 0));
        assertEquals(0xFF555555, elytraSpear.pixel(1, 1));
    }

    @Test
    void compositeLookupAndTextBadgeValidation() {
        for (CompositeIcon icon : CompositeIcon.values()) {
            assertEquals(Optional.of(icon), CompositeIcon.fromFontPath(icon.fontPath()));
            assertEquals(Optional.of(icon), CompositeIcon.fromTexturePath(icon.texturePath()));
        }
        assertEquals(Optional.empty(), CompositeIcon.fromFontPath("composite/unknown.png"));
        assertEquals(Optional.empty(), CompositeIcon.fromTexturePath("textures/composite/unknown.png"));

        assertEquals(PixelArt.textBadge("A"), PixelArt.textBadge("a"));
        assertThrows(IllegalArgumentException.class, () -> PixelArt.textBadge(""));
        assertThrows(IllegalArgumentException.class, () -> PixelArt.textBadge("ABC"));
        assertThrows(IllegalArgumentException.class, () -> PixelArt.textBadge("12"));
    }
}
