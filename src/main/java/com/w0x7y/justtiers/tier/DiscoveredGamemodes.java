package com.w0x7y.justtiers.tier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Gamemodes a site reports that this build does not register. A site can add a
 * gamemode at any time, and a placement in it is still a placement: it competes for
 * the player's best tier and gets a lookup cell, with a name derived from its slug and
 * no icon. Only registered gamemodes can be selected in settings.
 *
 * <p>The slug is API data, so it is sanitized before anything keeps it: lowercased, then
 * accepted only when it matches {@code [a-z0-9][a-z0-9_-]{0,31}}. One answer admits at
 * most {@link #MAX_PER_ANSWER} discovered gamemodes, in payload order, so a broken or
 * hostile payload cannot flood a nametag or the lookup screen. Refused placements are
 * dropped without failing the rest of the answer.
 *
 * <p>Minecraft-free. The derived names are display text from API data, not translation
 * keys.
 */
public final class DiscoveredGamemodes {

    /** The most discovered gamemodes one site's answer for one player may contribute. */
    public static final int MAX_PER_ANSWER = 16;

    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9_-]{0,31}");
    private static final Pattern WORD_SEPARATORS = Pattern.compile("[_-]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** Short words the sites write in capitals, so a derived name matches theirs. */
    private static final Set<String> ACRONYMS =
            Set.of("smp", "uhc", "op", "og", "ffa", "hcf", "ctf");

    /** True when a normalised slug is safe to keep and display. */
    public static boolean isAcceptable(String slug) {
        return slug != null && SLUG.matcher(slug).matches();
    }

    /**
     * A PvPTiers, PvPHQ or SubTiers gamemode key as a slug: trimmed and lowercased.
     * Empty when the key cannot be accepted. Registered slugs pass unchanged.
     */
    public static Optional<String> profileSlug(String apiKey) {
        if (apiKey == null) {
            return Optional.empty();
        }
        String slug = apiKey.trim().toLowerCase(Locale.ROOT);
        return isAcceptable(slug) ? Optional.of(slug) : Optional.empty();
    }

    /**
     * A NovaTiers key, which is a spaced display name, as a slug. Registered gamemodes and
     * their aliases resolve through {@link Gamemodes#normaliseNovaKey}. Anything else keeps
     * its word boundaries as underscores, so "Quake Pro" becomes {@code quake_pro} and its
     * derived name reads "Quake Pro" again.
     */
    public static Optional<String> novaSlug(String apiKey) {
        Optional<String> registered = Gamemodes.normaliseNovaKey(apiKey);
        if (registered.isPresent() || apiKey == null) {
            return registered;
        }
        String slug = WHITESPACE.matcher(apiKey.trim().toLowerCase(Locale.ROOT)).replaceAll("_");
        return isAcceptable(slug) ? Optional.of(slug) : Optional.empty();
    }

    /**
     * A readable name for a slug: words split on {@code _} and {@code -}, each
     * title-cased, with known short acronyms such as SMP and UHC kept in capitals.
     */
    public static String displayName(String slug) {
        StringBuilder name = new StringBuilder(slug.length());
        for (String word : WORD_SEPARATORS.split(slug)) {
            if (word.isEmpty()) {
                continue;
            }
            if (!name.isEmpty()) {
                name.append(' ');
            }
            if (ACRONYMS.contains(word)) {
                name.append(word.toUpperCase(Locale.ROOT));
            } else {
                name.append(Character.toUpperCase(word.charAt(0))).append(word, 1, word.length());
            }
        }
        return name.isEmpty() ? slug : name.toString();
    }

    /**
     * A one-character stand-in where a registered gamemode would draw its glyph: the first
     * letter of the derived name. It is ordinary text and is never drawn in the icon font.
     */
    public static String abbreviation(String displayName) {
        if (displayName == null || displayName.isEmpty()) {
            return "?";
        }
        return displayName.substring(0, displayName.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
    }

    /** A gamemode this build does not register, or empty when the slug is unacceptable. */
    static Optional<Gamemode> of(Source source, String slug) {
        if (!isAcceptable(slug)) {
            return Optional.empty();
        }
        return Optional.of(new Gamemode(source, slug, displayName(slug), Optional.empty()));
    }

    /**
     * Builds one site's answer for one player. Registered gamemodes are always admitted;
     * discovered ones are admitted until {@link #MAX_PER_ANSWER} distinct slugs are in.
     * The first placement for a slug wins.
     */
    public static final class Collector {
        private final Source source;
        private final Map<String, Tier> tiers = new LinkedHashMap<>();
        private int discovered;
        private int refused;

        public Collector(Source source) {
            this.source = source;
        }

        /**
         * Offers a placement under a normalised slug. True when the answer holds a
         * placement for that slug afterwards; false when the slug is unacceptable or the
         * discovered cap is already reached.
         */
        public boolean offer(String slug, Tier tier) {
            if (tiers.containsKey(slug)) {
                return true;
            }
            if (Gamemodes.find(source, slug).isEmpty()) {
                if (!isAcceptable(slug) || discovered >= MAX_PER_ANSWER) {
                    refused++;
                    return false;
                }
                discovered++;
            }
            tiers.put(slug, tier);
            return true;
        }

        /** The admitted placements, in payload order. */
        public Map<String, Tier> tiers() {
            return Collections.unmodifiableMap(new LinkedHashMap<>(tiers));
        }

        public int discovered() {
            return discovered;
        }

        /** Placements dropped for an unacceptable slug or over the cap. */
        public int refused() {
            return refused;
        }
    }

    private DiscoveredGamemodes() {
    }
}
