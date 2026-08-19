package com.w0x7y.justtiers.config;

import com.w0x7y.justtiers.tier.Source;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaletteTest {

    /** Custom colors from wherever a caller keeps them; this one keeps them in a map. */
    private static ToIntFunction<Source> custom(int mctiers, int subtiers, int nova) {
        Map<Source, Integer> colors = new EnumMap<>(Source.class);
        colors.put(Source.MCTIERS, mctiers);
        colors.put(Source.SUBTIERS, subtiers);
        colors.put(Source.NOVATIERS, nova);
        return source -> colors.getOrDefault(source, source.defaultColor());
    }

    /** A caller a preset must never reach for. */
    private static final ToIntFunction<Source> NEVER_ASKED = source -> {
        throw new AssertionError("a preset asked for a custom color for " + source);
    };

    @Test
    void theDefaultPaletteIsWhatTheSitesAlreadyUse() {
        for (Source source : Source.ALL) {
            assertEquals(source.defaultColor(), Palette.DEFAULT.colorOf(source, NEVER_ASKED));
        }
    }

    @Test
    void presetsCarryTheirDocumentedColors() {
        assertEquals(Map.of(Source.MCTIERS, 0xE69F00,
                        Source.SUBTIERS, 0x56B4E9,
                        Source.NOVATIERS, 0xFFFFFF),
                Palette.COLORBLIND.colors(NEVER_ASKED));

        assertEquals(Map.of(Source.MCTIERS, 0xFFFFFF,
                        Source.SUBTIERS, 0xFFAA00,
                        Source.NOVATIERS, 0x00FFFF),
                Palette.HIGH_CONTRAST.colors(NEVER_ASKED));
    }

    @Test
    void everyPresetTellsTheThreeSitesApart() {
        for (Palette palette : Palette.values()) {
            if (palette.isCustom()) {
                continue;
            }
            Set<Integer> colors = new HashSet<>(palette.colors(NEVER_ASKED).values());
            assertEquals(Source.ALL.size(), colors.size(), palette.id());
        }
    }

    /**
     * The rule the config and the config screen used to spell out separately, each in
     * terms of its own storage: a preset never consults the custom colors, and Custom
     * consults nothing else.
     */
    @Test
    void aPresetIgnoresTheCustomColors() {
        ToIntFunction<Source> colors = custom(0x111111, 0x222222, 0x333333);

        assertEquals(0xFFFF55, Palette.DEFAULT.colorOf(Source.MCTIERS, colors));
        assertEquals(0xE69F00, Palette.COLORBLIND.colorOf(Source.MCTIERS, colors));
    }

    @Test
    void customUsesTheSuppliedColors() {
        assertEquals(Map.of(Source.MCTIERS, 0x111111,
                        Source.SUBTIERS, 0x222222,
                        Source.NOVATIERS, 0x333333),
                Palette.CUSTOM.colors(custom(0x111111, 0x222222, 0x333333)));
    }

    /**
     * Which is exactly what the config screen relies on: the color pickers do not exist
     * yet while the preview supplier that reads them is being built.
     */
    @Test
    void customWithNoOneToAskIsTheDefaultPalette() {
        for (Source source : Source.ALL) {
            assertEquals(source.defaultColor(), Palette.CUSTOM.colorOf(source, null));
        }
        assertEquals(Palette.DEFAULT.colors(NEVER_ASKED), Palette.CUSTOM.colors(null));
    }

    @Test
    void everyPaletteAnswersForEverySite() {
        for (Palette palette : Palette.values()) {
            assertEquals(Source.ALL.size(),
                    palette.colors(custom(0x111111, 0x222222, 0x333333)).size(), palette.id());
        }
    }

    @Test
    void oneSiteAtATimeAgreesWithTheWholePalette() {
        ToIntFunction<Source> colors = custom(0x111111, 0x222222, 0x333333);
        for (Palette palette : Palette.values()) {
            Map<Source, Integer> all = palette.colors(colors);
            for (Source source : Source.ALL) {
                assertEquals(palette.colorOf(source, colors), all.get(source),
                        palette.id() + "/" + source);
            }
        }
    }

    @Test
    void idsAreStableAndUnique() {
        assertEquals("default", Palette.DEFAULT.id());
        assertEquals("colorblind", Palette.COLORBLIND.id());
        assertEquals("high_contrast", Palette.HIGH_CONTRAST.id());
        assertEquals("custom", Palette.CUSTOM.id());

        Set<String> ids = new HashSet<>();
        for (Palette palette : Palette.values()) {
            assertTrue(ids.add(palette.id()), palette.name());
        }
    }

    @Test
    void onlyCustomIsCustom() {
        assertTrue(Palette.CUSTOM.isCustom());
        assertFalse(Palette.DEFAULT.isCustom());
        assertFalse(Palette.COLORBLIND.isCustom());
        assertFalse(Palette.HIGH_CONTRAST.isCustom());
    }
}
