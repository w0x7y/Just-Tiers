package com.w0x7y.justtiers.config;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HexColorTest {

    @Test
    void parsesBothSpellings() {
        assertEquals(OptionalInt.of(0xFFFF55), HexColor.parse("#FFFF55"));
        assertEquals(OptionalInt.of(0xFFFF55), HexColor.parse("FFFF55"));
    }

    @Test
    void parsesCaseInsensitivelyAndIgnoresSurroundingSpace() {
        assertEquals(OptionalInt.of(0xAA55FF), HexColor.parse("#aa55ff"));
        assertEquals(OptionalInt.of(0xAA55FF), HexColor.parse("  #Aa55Ff  "));
    }

    @Test
    void rejectsAnythingThatIsNotSixHexDigits() {
        assertFalse(HexColor.parse(null).isPresent());
        assertFalse(HexColor.parse("").isPresent());
        assertFalse(HexColor.parse("#FFF").isPresent());
        assertFalse(HexColor.parse("#FFFF5").isPresent());
        assertFalse(HexColor.parse("#FFFF555").isPresent());
        assertFalse(HexColor.parse("#GGGGGG").isPresent());
        assertFalse(HexColor.parse("#FFFF55FF").isPresent(), "alpha is not accepted");
    }

    @Test
    void formatsBackToTheCanonicalSpelling() {
        assertEquals("#FFFF55", HexColor.format(0xFFFF55));
        assertEquals("#000000", HexColor.format(0x000000));
        assertEquals("#00FF00", HexColor.format(0x00FF00));
    }

    @Test
    void formatIgnoresAnythingAboveTheRgbTriple() {
        assertEquals("#FFFF55", HexColor.format(0xFF_FFFF55));
    }

    @Test
    void everyFormattedColourParsesBack() {
        for (int rgb : new int[] {0x000000, 0xFFFFFF, 0xE69F00, 0x56B4E9, 0xAA55FF}) {
            assertEquals(OptionalInt.of(rgb), HexColor.parse(HexColor.format(rgb)));
        }
    }
}
