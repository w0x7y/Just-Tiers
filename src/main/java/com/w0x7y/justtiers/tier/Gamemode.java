package com.w0x7y.justtiers.tier;

/**
 * One gamemode on one site. {@code slug} is the identifier used by that site's API
 * (for NovaTiers, our normalised slug rather than their spaced display name).
 * {@code icon} is a private-use codepoint bound to a bitmap glyph in the font provider.
 */
public record Gamemode(Source source, String slug, String displayName, char icon) {
}
