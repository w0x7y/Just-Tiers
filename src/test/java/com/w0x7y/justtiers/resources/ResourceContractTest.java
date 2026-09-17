package com.w0x7y.justtiers.resources;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.w0x7y.justtiers.config.Palette;
import com.w0x7y.justtiers.render.model.BadgePosition;
import com.w0x7y.justtiers.resolve.DisplayMode;
import com.w0x7y.justtiers.tier.Gamemode;
import com.w0x7y.justtiers.tier.Gamemodes;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
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
        for (Gamemode mode : Gamemodes.ALL) {
            String texture = mode.source().name().toLowerCase(Locale.ROOT) + "/" + mode.slug() + ".png";
            assertEquals("justtiers:" + texture, glyphs.get(String.valueOf(mode.icon())),
                    mode.source() + "/" + mode.slug());
            try (InputStream in = resource("assets/justtiers/textures/" + texture)) {
                var png = ImageIO.read(in);
                assertNotNull(png, "unreadable PNG: " + texture);
                assertTrue(png.getWidth() > 0 && png.getHeight() > 0, texture);
            }
        }
    }

    @Test
    void allTranslationKeysUsedByTheModHaveNonemptyEnglishText() throws IOException {
        Map<String, String> translations = translations();
        Set<String> required = new HashSet<>();
        // Minecraft builds this category key from the registered namespace and path.
        required.add("key.category.justtiers.main");
        for (DisplayMode mode : DisplayMode.values()) required.add("justtiers.mode." + mode.id());
        for (BadgePosition position : BadgePosition.values()) required.add("justtiers.badge." + position.id());
        for (Palette palette : Palette.values()) required.add("justtiers.palette." + palette.id());

        Set<String> dynamicPrefixes = Set.of("justtiers.mode.", "justtiers.badge.", "justtiers.palette.");
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
