package com.w0x7y.justtiers.config;

import com.w0x7y.justtiers.tier.Source;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaletteTest {

    private static Map<String, String> custom(String mctiers, String subtiers, String nova) {
        Map<String, String> colors = new HashMap<>();
        colors.put(Source.MCTIERS.name(), mctiers);
        colors.put(Source.SUBTIERS.name(), subtiers);
        colors.put(Source.NOVATIERS.name(), nova);
        return colors;
    }

    @Test
    void theDefaultPaletteIsWhatTheSitesAlreadyUse() {
        for (Source source : Source.ALL) {
            assertEquals(source.defaultColor(), Palette.DEFAULT.colorOf(source, Map.of()));
        }
    }

    @Test
    void presetsCarryTheirDocumentedColours() {
        assertEquals(0xE69F00, Palette.COLORBLIND.colorOf(Source.MCTIERS, Map.of()));
        assertEquals(0x56B4E9, Palette.COLORBLIND.colorOf(Source.SUBTIERS, Map.of()));
        assertEquals(0xFFFFFF, Palette.COLORBLIND.colorOf(Source.NOVATIERS, Map.of()));

        assertEquals(0xFFFFFF, Palette.HIGH_CONTRAST.colorOf(Source.MCTIERS, Map.of()));
        assertEquals(0xFFAA00, Palette.HIGH_CONTRAST.colorOf(Source.SUBTIERS, Map.of()));
        assertEquals(0x00FFFF, Palette.HIGH_CONTRAST.colorOf(Source.NOVATIERS, Map.of()));
    }

    @Test
    void everyPresetTellsTheThreeSitesApart() {
        for (Palette palette : Palette.values()) {
            if (palette.isCustom()) {
                continue;
            }
            Set<Integer> colors = new HashSet<>();
            for (Source source : Source.ALL) {
                colors.add(palette.colorOf(source, Map.of()));
            }
            assertEquals(Source.ALL.size(), colors.size(), palette.id());
        }
    }

    @Test
    void aPresetIgnoresTheCustomColours() {
        Map<String, String> colors = custom("#111111", "#222222", "#333333");
        assertEquals(0xFFFF55, Palette.DEFAULT.colorOf(Source.MCTIERS, colors));
        assertEquals(0xE69F00, Palette.COLORBLIND.colorOf(Source.MCTIERS, colors));
    }

    @Test
    void customUsesTheSuppliedColours() {
        Map<String, String> colors = custom("#111111", "#222222", "#333333");
        assertEquals(0x111111, Palette.CUSTOM.colorOf(Source.MCTIERS, colors));
        assertEquals(0x222222, Palette.CUSTOM.colorOf(Source.SUBTIERS, colors));
        assertEquals(0x333333, Palette.CUSTOM.colorOf(Source.NOVATIERS, colors));
    }

    @Test
    void aBadCustomColourCostsOnlyItsOwnSite() {
        Map<String, String> colors = custom("#111111", "not a colour", null);
        assertEquals(0x111111, Palette.CUSTOM.colorOf(Source.MCTIERS, colors));
        assertEquals(Source.SUBTIERS.defaultColor(),
                Palette.CUSTOM.colorOf(Source.SUBTIERS, colors));
        assertEquals(Source.NOVATIERS.defaultColor(),
                Palette.CUSTOM.colorOf(Source.NOVATIERS, colors));
    }

    @Test
    void customWithNothingStoredIsTheDefaultPalette() {
        for (Source source : Source.ALL) {
            assertEquals(source.defaultColor(), Palette.CUSTOM.colorOf(source, Map.of()));
            assertEquals(source.defaultColor(), Palette.CUSTOM.colorOf(source, null));
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
