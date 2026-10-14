package com.w0x7y.justtiers.tier;

import java.util.Objects;
import java.util.Optional;

/**
 * One gamemode on one site. {@code slug} is the identifier used by that site's API
 * (for NovaTiers, our normalised slug rather than their spaced display name).
 *
 * <p>{@code icon} is a private-use codepoint bound to a bitmap glyph in the font provider.
 * Every registered gamemode has one. A gamemode discovered in API data that this build
 * does not know has none, and nothing may draw it in the icon font: an empty icon is the
 * only signal for that, never a placeholder codepoint.
 */
public record Gamemode(Source source, String slug, String displayName, Optional<Character> icon) {

    public Gamemode {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(icon, "icon");
    }

    /** A registered gamemode with its own glyph. */
    public Gamemode(Source source, String slug, String displayName, char icon) {
        this(source, slug, displayName, Optional.of(icon));
    }
}
