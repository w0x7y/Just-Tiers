package com.w0x7y.justtiers.resources;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.w0x7y.justtiers.config.Palette;
import com.w0x7y.justtiers.render.model.BadgePosition;
import com.w0x7y.justtiers.render.model.PixelArt;
import com.w0x7y.justtiers.render.model.PixelArt.CompositeIcon;
import com.w0x7y.justtiers.render.model.PixelArt.Grid;
import com.w0x7y.justtiers.render.model.PixelArt.TextureRef;
import com.w0x7y.justtiers.tier.Gamemode;
import com.w0x7y.justtiers.tier.Gamemodes;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** A compiled mod must also ship the glyphs, textures and words its Java code names. */
class ResourceContractTest {

    private static final Pattern TRANSLATION_LITERAL =
            Pattern.compile("\"((?:justtiers|key\\.justtiers)\\.[A-Za-z0-9_.]+)\"");
    private static final Pattern TICK_BOX =
            Pattern.compile("tickBox\\(\"(justtiers\\.[A-Za-z0-9_.]+)\"");

    private static InputStream resource(String path) {
        InputStream stream = ResourceContractTest.class.getResourceAsStream("/" + path);
        assertNotNull(stream, "missing packaged resource: " + path);
        return stream;
    }

    @Test
    void everyRegisteredGamemodeHasExactlyItsOwnGlyphAndReadableTexture() throws IOException {
        JsonObject font;
        try (var reader = new InputStreamReader(resource("assets/justtiers/font/icons.json"),
                StandardCharsets.UTF_8)) {
            font = JsonParser.parseReader(reader).getAsJsonObject();
        }
        Map<String, String> glyphs = new HashMap<>();
        for (var entry : font.getAsJsonArray("providers")) {
            JsonObject provider = entry.getAsJsonObject();
            assertEquals("bitmap", provider.get("type").getAsString());
            var chars = provider.getAsJsonArray("chars");
            assertEquals(1, chars.size());
            String glyph = chars.get(0).getAsString();
            assertEquals(1, glyph.length(), "one icon per provider");
            String texture = provider.get("file").getAsString();
            assertNull(glyphs.put(glyph, texture), "duplicate glyph: " + glyph);
            assertEquals(8, provider.get("height").getAsInt());
            assertEquals(8, provider.get("ascent").getAsInt());
        }
        assertEquals(Gamemodes.ALL.size(), glyphs.size(), "extra or missing font providers");
        Set<String> packagedTextures = new HashSet<>();
        Set<CompositeIcon> usedComposites = new HashSet<>();
        for (Gamemode mode : Gamemodes.ALL) {
            String file = glyphs.get(String.valueOf(mode.icon().orElseThrow()));
            assertNotNull(file, mode.source() + "/" + mode.slug());
            if (file.startsWith("minecraft:")) {
                assertReadablePng("assets/minecraft/textures/" + file.substring("minecraft:".length()), file);
            } else {
                assertTrue(file.startsWith("justtiers:"), "unexpected namespace: " + file);
                String relative = file.substring("justtiers:".length());
                Optional<CompositeIcon> composite = CompositeIcon.fromFontPath(relative);
                if (composite.isPresent()) {
                    usedComposites.add(composite.get());
                } else {
                    String expectedCustom = mode.source().name().toLowerCase(Locale.ROOT)
                            + "/" + mode.slug() + ".png";
                    assertEquals(expectedCustom, relative, mode.source() + "/" + mode.slug());
                    packagedTextures.add(relative);
                    assertReadablePng("assets/justtiers/textures/" + relative, file);
                }
            }
        }
        assertEquals(Set.of(CompositeIcon.values()), usedComposites, "unused composite icon recipe");
        for (CompositeIcon composite : usedComposites) {
            boolean allSourcesPresent = true;
            for (TextureRef source : composite.primarySources()) {
                if (loadClasspathGrid(source).isEmpty()) {
                    allSourcesPresent = false;
                }
            }
            if (composite == CompositeIcon.POT || composite == CompositeIcon.DEBUFF
                    || composite == CompositeIcon.SPEED) {
                assertTrue(allSourcesPresent, "missing vanilla potion source texture for " + composite);
            }
            Grid composed = composite.compose(ResourceContractTest::loadClasspathGrid);
            if (allSourcesPresent) {
                assertNotEquals(PixelArt.textBadge(composite.fallbackLabel()), composed,
                        "expected composite icon instead of text fallback for " + composite);
            } else {
                assertEquals(PixelArt.textBadge(composite.fallbackLabel()), composed,
                        "expected text fallback when a source texture is absent for " + composite);
            }
            var png = ImageIO.read(new ByteArrayInputStream(PixelArt.encodePng(composed)));
            assertNotNull(png, "unreadable composite PNG: " + composite);
            assertTrue(png.getWidth() > 0 && png.getHeight() > 0, composite.fontPath());
        }

        Path texturesRoot = Path.of("src/main/resources/assets/justtiers/textures");
        if (Files.isDirectory(texturesRoot)) {
            try (var files = Files.walk(texturesRoot)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".png")).toList()) {
                    String relative = texturesRoot.relativize(file).toString().replace('\\', '/');
                    assertTrue(packagedTextures.contains(relative),
                            "unreferenced gamemode texture: " + relative);
                }
            }
        }
    }

    private static void assertReadablePng(String resourcePath, String label) throws IOException {
        try (InputStream in = resource(resourcePath)) {
            var png = ImageIO.read(in);
            assertNotNull(png, "unreadable PNG: " + label);
            assertTrue(png.getWidth() > 0 && png.getHeight() > 0, label);
        }
    }

    private static Optional<Grid> loadClasspathGrid(TextureRef ref) throws IOException {
        String path = "/assets/" + ref.namespace() + "/textures/" + ref.path();
        try (InputStream in = ResourceContractTest.class.getResourceAsStream(path)) {
            if (in == null) {
                return Optional.empty();
            }
            BufferedImage image = ImageIO.read(in);
            assertNotNull(image, "unreadable PNG: " + path);
            int w = image.getWidth();
            int h = image.getHeight();
            int[] argb = image.getRGB(0, 0, w, h, null, 0, w);
            return Optional.of(new Grid(w, h, argb));
        }
    }

    @Test
    void allTranslationKeysUsedByTheModHaveNonemptyEnglishText() throws IOException {
        Map<String, String> translations = translations();
        Set<String> required = new HashSet<>();
        // Minecraft builds this category key from the registered namespace and path.
        required.add("key.category.justtiers.main");
        for (BadgePosition position : BadgePosition.values()) required.add("justtiers.badge." + position.id());
        for (Palette palette : Palette.values()) required.add("justtiers.palette." + palette.id());

        Set<String> dynamicPrefixes = Set.of("justtiers.badge.", "justtiers.palette.");
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                Matcher keys = TRANSLATION_LITERAL.matcher(source);
                while (keys.find()) {
                    String key = keys.group(1);
                    if (key.equals("justtiers.json")) continue; // Config filename, not a translation.
                    if (key.endsWith(".")) {
                        assertTrue(dynamicPrefixes.contains(key),
                                "expand this dynamic translation family in the contract: " + key);
                    } else {
                        required.add(key);
                    }
                }
                // The checkbox helper appends .desc rather than spelling the full key.
                Matcher boxes = TICK_BOX.matcher(source);
                while (boxes.find()) required.add(boxes.group(1) + ".desc");
            }
        }
        for (String key : required) {
            assertTrue(translations.containsKey(key), "missing English translation: " + key);
            assertFalse(translations.get(key).isBlank(), "empty English translation: " + key);
        }
    }

    private static Map<String, String> translations() throws IOException {
        Map<String, String> result = new HashMap<>();
        // Streaming preserves duplicate keys, which parsing straight to a map would hide.
        try (var reader = new JsonReader(new InputStreamReader(
                resource("assets/justtiers/lang/en_us.json"), StandardCharsets.UTF_8))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                assertEquals(JsonToken.STRING, reader.peek(), "translation must be text: " + key);
                assertNull(result.put(key, reader.nextString()), "duplicate translation: " + key);
            }
            reader.endObject();
            assertEquals(JsonToken.END_DOCUMENT, reader.peek(), "trailing translation data");
        }
        return result;
    }
}
