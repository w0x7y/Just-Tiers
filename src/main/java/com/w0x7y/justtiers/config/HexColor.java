package com.w0x7y.justtiers.config;

import java.util.Locale;
import java.util.OptionalInt;

/**
 * The on-disk spelling of a colour: {@code #RRGGBB}, with or without the hash, in either
 * case. Alpha is deliberately not accepted — every consumer supplies its own, and a
 * four-byte value read as three would be wrong in a way nobody could see coming.
 *
 * <p>Empty rather than an exception for anything unparseable: a hand-edited config is
 * corrected on load, not rejected.
 */
public final class HexColor {

    private static final int DIGITS = 6;
    private static final int RGB_MASK = 0xFFFFFF;

    public static OptionalInt parse(String raw) {
        if (raw == null) {
            return OptionalInt.empty();
        }
        String text = raw.trim();
        if (text.startsWith("#")) {
            text = text.substring(1);
        }
        if (text.length() != DIGITS) {
            return OptionalInt.empty();
        }
        for (int i = 0; i < DIGITS; i++) {
            if (Character.digit(text.charAt(i), 16) < 0) {
                return OptionalInt.empty();
            }
        }
        return OptionalInt.of(Integer.parseInt(text, 16));
    }

    /** The canonical spelling: hashed, upper case, six digits, no alpha. */
    public static String format(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & RGB_MASK);
    }

    private HexColor() {
    }
}
