package com.w0x7y.justtiers.render.model;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Minecraft-free pixel compositing, pixel-text fallback rendering, and PNG encoding for
 * runtime-built gamemode icons.
 *
 * <p>Pixels use packed {@code 0xAARRGGBB} ints, matching {@code NativeImage.getPixel} and
 * {@code BufferedImage.getRGB}. All recipes read textures from the player's active resource
 * packs at runtime; if any source texture is missing on an older Minecraft version (such as
 * {@code netherite_spear.png} on 1.21.10), the icon falls back to a crisp pixel-text
 * abbreviation ({@code SM}, {@code ES}, etc.) with no custom PNGs shipped in the JAR.
 */
public final class PixelArt {

    private static final int TEXT_FOREGROUND = 0xFFEBF0F8;
    private static final int TEXT_SHADOW = 0xFF2D303A;

    /** Minimal 5x7 uppercase pixel font for text-based icon fallbacks. */
    private static final Map<Character, String[]> FONT_5X7 = Map.ofEntries(
            Map.entry('A', new String[] {".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"}),
            Map.entry('B', new String[] {"####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."}),
            Map.entry('C', new String[] {".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."}),
            Map.entry('D', new String[] {"####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."}),
            Map.entry('E', new String[] {"#####", "#....", "#....", "####.", "#....", "#....", "#####"}),
            Map.entry('F', new String[] {"#####", "#....", "#....", "####.", "#....", "#....", "#...."}),
            Map.entry('G', new String[] {".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".###."}),
            Map.entry('H', new String[] {"#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"}),
            Map.entry('I', new String[] {".###.", "..#..", "..#..", "..#..", "..#..", "..#..", ".###."}),
            Map.entry('J', new String[] {"..###", "...#.", "...#.", "...#.", "#..#.", "#..#.", ".##.."}),
            Map.entry('K', new String[] {"#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"}),
            Map.entry('L', new String[] {"#....", "#....", "#....", "#....", "#....", "#....", "#####"}),
            Map.entry('M', new String[] {"#...#", "##.##", "#.#.#", "#...#", "#...#", "#...#", "#...#"}),
            Map.entry('N', new String[] {"#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"}),
            Map.entry('O', new String[] {".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."}),
            Map.entry('P', new String[] {"####.", "#...#", "#...#", "####.", "#....", "#....", "#...."}),
            Map.entry('Q', new String[] {".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"}),
            Map.entry('R', new String[] {"####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"}),
            Map.entry('S', new String[] {".###.", "#...#", "#....", ".###.", "....#", "#...#", ".###."}),
            Map.entry('T', new String[] {"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."}),
            Map.entry('U', new String[] {"#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."}),
            Map.entry('V', new String[] {"#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."}),
            Map.entry('W', new String[] {"#...#", "#...#", "#...#", "#...#", "#.#.#", "##.##", "#...#"}),
            Map.entry('X', new String[] {"#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"}),
            Map.entry('Y', new String[] {"#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."}),
            Map.entry('Z', new String[] {"#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"})
    );

    /**
     * An immutable rectangular RGBA pixel buffer stored row-major as {@code 0xAARRGGBB}.
     */
    public record Grid(int width, int height, int[] argb) {

        public Grid {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("non-positive dimensions: " + width + "x" + height);
            }
            if (argb == null || argb.length != width * height) {
                throw new IllegalArgumentException("pixel array length must equal width * height");
            }
            argb = argb.clone();
        }

        public int pixel(int x, int y) {
            return argb[y * width + x];
        }

        /** Mirrors every row left-to-right. */
        public Grid flipHorizontal() {
            int[] out = new int[argb.length];
            for (int y = 0; y < height; y++) {
                int row = y * width;
                for (int x = 0; x < width; x++) {
                    out[row + x] = argb[row + (width - 1 - x)];
                }
            }
            return new Grid(width, height, out);
        }

        /** Multiplies RGB channels of visible pixels by {@code rgb} ({@code 0xRRGGBB}), keeping alpha. */
        public Grid tint(int rgb) {
            int tr = (rgb >>> 16) & 0xFF;
            int tg = (rgb >>> 8) & 0xFF;
            int tb = rgb & 0xFF;
            int[] out = new int[argb.length];
            for (int i = 0; i < argb.length; i++) {
                int p = argb[i];
                int a = p >>> 24;
                if (a == 0) {
                    continue;
                }
                int r = (((p >>> 16) & 0xFF) * tr) / 255;
                int g = (((p >>> 8) & 0xFF) * tg) / 255;
                int b = ((p & 0xFF) * tb) / 255;
                out[i] = (a << 24) | (r << 16) | (g << 8) | b;
            }
            return new Grid(width, height, out);
        }

        /** Nearest-neighbor rescale so mixed-resolution resource packs composite cleanly. */
        public Grid scaleNearest(int targetWidth, int targetHeight) {
            if (targetWidth == width && targetHeight == height) {
                return this;
            }
            if (targetWidth <= 0 || targetHeight <= 0) {
                throw new IllegalArgumentException("non-positive target: " + targetWidth + "x" + targetHeight);
            }
            int[] out = new int[targetWidth * targetHeight];
            for (int y = 0; y < targetHeight; y++) {
                int sy = (y * height) / targetHeight;
                int dstRow = y * targetWidth;
                int srcRow = sy * width;
                for (int x = 0; x < targetWidth; x++) {
                    int sx = (x * width) / targetWidth;
                    out[dstRow + x] = argb[srcRow + sx];
                }
            }
            return new Grid(targetWidth, targetHeight, out);
        }

        /** Porter-Duff source-over composite of {@code top} above {@code this}. */
        public Grid over(Grid top) {
            int w = Math.max(this.width, top.width);
            int h = Math.max(this.height, top.height);
            Grid base = this.scaleNearest(w, h);
            Grid layer = top.scaleNearest(w, h);
            int[] out = new int[w * h];
            for (int i = 0; i < out.length; i++) {
                out[i] = blendOver(base.argb[i], layer.argb[i]);
            }
            return new Grid(w, h, out);
        }

        private static int blendOver(int dst, int src) {
            int sa = src >>> 24;
            if (sa == 0) {
                return dst;
            }
            if (sa == 255) {
                return src;
            }
            int da = dst >>> 24;
            if (da == 0) {
                return src;
            }
            int invSa = 255 - sa;
            int outA = sa + (da * invSa + 127) / 255;
            if (outA == 0) {
                return 0;
            }
            int sr = (src >>> 16) & 0xFF;
            int sg = (src >>> 8) & 0xFF;
            int sb = src & 0xFF;
            int dr = (dst >>> 16) & 0xFF;
            int dg = (dst >>> 8) & 0xFF;
            int db = dst & 0xFF;

            int r = (sr * sa * 255 + dr * da * invSa + (outA * 255) / 2) / (outA * 255);
            int g = (sg * sa * 255 + dg * da * invSa + (outA * 255) / 2) / (outA * 255);
            int b = (sb * sa * 255 + db * da * invSa + (outA * 255) / 2) / (outA * 255);
            return (outA << 24)
                    | (Math.min(255, r) << 16)
                    | (Math.min(255, g) << 8)
                    | Math.min(255, b);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Grid other
                    && width == other.width
                    && height == other.height
                    && Arrays.equals(argb, other.argb);
        }

        @Override
        public int hashCode() {
            int h = 31 * width + height;
            return 31 * h + Arrays.hashCode(argb);
        }
    }

    /**
     * Renders a crisp 1- or 2-letter uppercase pixel-text monogram inside a 12x12 square grid
     * with a 1px drop shadow, keeping the exact 1:1 advance width of normal gamemode icons.
     */
    public static Grid textBadge(String label) {
        String normalized = label.toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 2) {
            throw new IllegalArgumentException("fallback badge expects 1 or 2 letters: " + label);
        }
        int size = 12;
        int[] argb = new int[size * size];
        int totalWidth = normalized.length() * 5 + (normalized.length() - 1);
        int startX = (size - 1 - totalWidth) / 2;
        int startY = 2;
        drawText(argb, size, normalized, startX + 1, startY + 1, TEXT_SHADOW);
        drawText(argb, size, normalized, startX, startY, TEXT_FOREGROUND);
        return new Grid(size, size, argb);
    }

    private static void drawText(int[] argb, int size, String text, int ox, int oy, int color) {
        for (int i = 0; i < text.length(); i++) {
            String[] glyph = FONT_5X7.get(text.charAt(i));
            if (glyph == null) {
                throw new IllegalArgumentException("unsupported fallback letter: " + text.charAt(i));
            }
            int gx = ox + i * 6;
            for (int y = 0; y < glyph.length; y++) {
                String row = glyph[y];
                for (int x = 0; x < row.length(); x++) {
                    if (row.charAt(x) == '#') {
                        int px = gx + x;
                        int py = oy + y;
                        if (px >= 0 && px < size && py >= 0 && py < size) {
                            argb[py * size + px] = color;
                        }
                    }
                }
            }
        }
    }

    /** A texture dependency expressed as a {@code namespace:path} relative to {@code textures/}. */
    public record TextureRef(String namespace, String path) {
        public static TextureRef minecraft(String path) {
            return new TextureRef("minecraft", path);
        }
    }

    /** Supplies one loaded texture grid, or empty when that texture is absent in this game version. */
    @FunctionalInterface
    public interface TextureLoader {
        Optional<Grid> load(TextureRef ref) throws IOException;
    }

    /**
     * Gamemode icons synthesized at runtime from the active resource packs.
     * Each entry's {@link #fontPath()} matches {@code assets/justtiers/font/icons.json};
     * if any required vanilla source texture is absent, it renders {@link #fallbackLabel()}.
     */
    public enum CompositeIcon {
        POT("composite/pot.png", "PT",
                List.of(TextureRef.minecraft("item/splash_potion.png"),
                        TextureRef.minecraft("item/potion_overlay.png")),
                loader -> composePotion(loader, "item/splash_potion.png", 0xF82423)),
        DEBUFF("composite/debuff.png", "DB",
                List.of(TextureRef.minecraft("item/splash_potion.png"),
                        TextureRef.minecraft("item/potion_overlay.png")),
                loader -> composePotion(loader, "item/splash_potion.png", 0x5AB835)),
        SPEED("composite/speed.png", "SP",
                List.of(TextureRef.minecraft("item/potion.png"),
                        TextureRef.minecraft("item/potion_overlay.png")),
                loader -> composePotion(loader, "item/potion.png", 0x33EBFF)),
        SPEAR_MACE("composite/spear_mace.png", "SM",
                List.of(TextureRef.minecraft("item/mace.png"),
                        TextureRef.minecraft("item/netherite_spear.png")),
                loader -> {
                    Optional<Grid> mace = loader.load(TextureRef.minecraft("item/mace.png"));
                    Optional<Grid> spear = loader.load(TextureRef.minecraft("item/netherite_spear.png"));
                    if (mace.isEmpty() || spear.isEmpty()) {
                        return Optional.empty();
                    }
                    return Optional.of(mace.get().flipHorizontal().over(spear.get()));
                }),
        ELYTRA_SPEAR("composite/elytra_spear.png", "ES",
                List.of(TextureRef.minecraft("item/elytra.png"),
                        TextureRef.minecraft("item/netherite_spear.png")),
                loader -> {
                    Optional<Grid> elytra = loader.load(TextureRef.minecraft("item/elytra.png"));
                    Optional<Grid> spear = loader.load(TextureRef.minecraft("item/netherite_spear.png"));
                    if (elytra.isEmpty() || spear.isEmpty()) {
                        return Optional.empty();
                    }
                    return Optional.of(elytra.get().over(spear.get()));
                });

        private final String fontPath;
        private final String fallbackLabel;
        private final List<TextureRef> primarySources;
        private final Recipe recipe;

        CompositeIcon(String fontPath, String fallbackLabel, List<TextureRef> primarySources, Recipe recipe) {
            this.fontPath = fontPath;
            this.fallbackLabel = fallbackLabel;
            this.primarySources = List.copyOf(primarySources);
            this.recipe = recipe;
        }

        /** Path inside {@code justtiers:} as written in {@code icons.json}, e.g. {@code composite/pot.png}. */
        public String fontPath() {
            return fontPath;
        }

        /** Path relative to {@code assets/justtiers/}, e.g. {@code textures/composite/pot.png}. */
        public String texturePath() {
            return "textures/" + fontPath;
        }

        /** Short uppercase abbreviation rendered if any vanilla source texture is missing. */
        public String fallbackLabel() {
            return fallbackLabel;
        }

        /** Vanilla textures this recipe requires before falling back to {@link #fallbackLabel()}. */
        public List<TextureRef> primarySources() {
            return primarySources;
        }

        public Grid compose(TextureLoader loader) throws IOException {
            Optional<Grid> composed = recipe.compose(loader);
            return composed.isPresent() ? composed.get() : textBadge(fallbackLabel);
        }

        public static Optional<CompositeIcon> fromFontPath(String fontPath) {
            for (CompositeIcon icon : values()) {
                if (icon.fontPath.equals(fontPath)) {
                    return Optional.of(icon);
                }
            }
            return Optional.empty();
        }

        public static Optional<CompositeIcon> fromTexturePath(String texturePath) {
            for (CompositeIcon icon : values()) {
                if (icon.texturePath().equals(texturePath)) {
                    return Optional.of(icon);
                }
            }
            return Optional.empty();
        }

        private static Optional<Grid> composePotion(TextureLoader loader, String bottlePath, int tintRgb)
                throws IOException {
            Optional<Grid> overlay = loader.load(TextureRef.minecraft("item/potion_overlay.png"));
            Optional<Grid> bottle = loader.load(TextureRef.minecraft(bottlePath));
            if (overlay.isEmpty() || bottle.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(overlay.get().tint(tintRgb).over(bottle.get()));
        }

        @FunctionalInterface
        private interface Recipe {
            Optional<Grid> compose(TextureLoader loader) throws IOException;
        }
    }

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    /**
     * Encodes an RGBA {@link Grid} into a standard 8-bit PNG byte array without touching
     * {@code java.awt} or {@code javax.imageio}, avoiding headless/toolkit side effects on macOS.
     */
    public static byte[] encodePng(Grid grid) {
        try {
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            png.write(PNG_SIGNATURE);

            ByteArrayOutputStream ihdr = new ByteArrayOutputStream(13);
            DataOutputStream ihdrData = new DataOutputStream(ihdr);
            ihdrData.writeInt(grid.width());
            ihdrData.writeInt(grid.height());
            ihdrData.writeByte(8); // bit depth
            ihdrData.writeByte(6); // color type: RGBA
            ihdrData.writeByte(0); // compression: deflate
            ihdrData.writeByte(0); // filter: adaptive
            ihdrData.writeByte(0); // interlace: none
            writeChunk(png, "IHDR", ihdr.toByteArray());

            ByteArrayOutputStream idat = new ByteArrayOutputStream();
            Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
            try (DeflaterOutputStream compressed = new DeflaterOutputStream(idat, deflater)) {
                byte[] scanline = new byte[1 + grid.width() * 4];
                scanline[0] = 0; // filter type: None
                for (int y = 0; y < grid.height(); y++) {
                    int pos = 1;
                    for (int x = 0; x < grid.width(); x++) {
                        int p = grid.pixel(x, y);
                        scanline[pos++] = (byte) ((p >>> 16) & 0xFF);
                        scanline[pos++] = (byte) ((p >>> 8) & 0xFF);
                        scanline[pos++] = (byte) (p & 0xFF);
                        scanline[pos++] = (byte) ((p >>> 24) & 0xFF);
                    }
                    compressed.write(scanline);
                }
            } finally {
                deflater.end();
            }
            writeChunk(png, "IDAT", idat.toByteArray());
            writeChunk(png, "IEND", new byte[0]);
            return png.toByteArray();
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        DataOutputStream stream = new DataOutputStream(out);
        stream.writeInt(data.length);
        stream.write(typeBytes);
        stream.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        stream.writeInt((int) crc.getValue());
    }

    private PixelArt() {
    }
}
