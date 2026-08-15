package com.w0x7y.justtiers.tier;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Gamemodes {

    private static final List<Gamemode> MCTIERS = List.of(
            new Gamemode(Source.MCTIERS, "axe", "Axe", '\uE101'),
            new Gamemode(Source.MCTIERS, "mace", "Mace", '\uE102'),
            new Gamemode(Source.MCTIERS, "nethop", "Netherite OP", '\uE103'),
            new Gamemode(Source.MCTIERS, "pot", "Pot", '\uE104'),
            new Gamemode(Source.MCTIERS, "smp", "SMP", '\uE105'),
            new Gamemode(Source.MCTIERS, "sword", "Sword", '\uE106'),
            new Gamemode(Source.MCTIERS, "uhc", "UHC", '\uE107'),
            new Gamemode(Source.MCTIERS, "vanilla", "Vanilla", '\uE108'));

    private static final List<Gamemode> SUBTIERS = List.of(
            new Gamemode(Source.SUBTIERS, "bed", "Bed", '\uE201'),
            new Gamemode(Source.SUBTIERS, "bow", "Bow", '\uE202'),
            new Gamemode(Source.SUBTIERS, "creeper", "Creeper", '\uE203'),
            new Gamemode(Source.SUBTIERS, "debuff", "DeBuff", '\uE204'),
            new Gamemode(Source.SUBTIERS, "dia_crystal", "Diamond Vanilla", '\uE205'),
            new Gamemode(Source.SUBTIERS, "dia_smp", "Diamond SMP", '\uE206'),
            new Gamemode(Source.SUBTIERS, "elytra", "Elytra", '\uE207'),
            new Gamemode(Source.SUBTIERS, "manhunt", "Manhunt", '\uE208'),
            new Gamemode(Source.SUBTIERS, "minecart", "Minecart", '\uE209'),
            new Gamemode(Source.SUBTIERS, "og_vanilla", "OG Vanilla", '\uE20A'),
            new Gamemode(Source.SUBTIERS, "speed", "Speed", '\uE20B'),
            new Gamemode(Source.SUBTIERS, "trident", "Trident", '\uE20C'));

    private static final List<Gamemode> NOVATIERS = List.of(
            new Gamemode(Source.NOVATIERS, "axe", "Axe", '\uE301'),
            new Gamemode(Source.NOVATIERS, "diamondcart", "Diamond Cart", '\uE302'),
            new Gamemode(Source.NOVATIERS, "diamondop", "Diamond OP", '\uE303'),
            new Gamemode(Source.NOVATIERS, "elytra", "Elytra", '\uE304'),
            new Gamemode(Source.NOVATIERS, "elytraspear", "Elytra Spear", '\uE305'),
            new Gamemode(Source.NOVATIERS, "modernsmp", "Modern SMP", '\uE306'),
            new Gamemode(Source.NOVATIERS, "pufferfish", "Pufferfish", '\uE307'),
            new Gamemode(Source.NOVATIERS, "smp", "SMP", '\uE308'),
            new Gamemode(Source.NOVATIERS, "spearmace", "Spear Mace", '\uE309'),
            new Gamemode(Source.NOVATIERS, "spleef", "Spleef", '\uE30A'),
            new Gamemode(Source.NOVATIERS, "uhc", "UHC", '\uE30B'),
            new Gamemode(Source.NOVATIERS, "vanilla", "Vanilla", '\uE30C'));

    public static final List<Gamemode> ALL =
            Stream.of(MCTIERS, SUBTIERS, NOVATIERS).flatMap(List::stream).toList();

    /**
     * Maps a squashed NovaTiers key to our slug. Every slug maps to itself; listed here
     * are only the historical spellings that do not, mirroring the alias table in
     * novatiers.com/js/script.js so old key spellings keep resolving.
     */
    private static final Map<String, String> NOVA_ALIASES = Map.of(
            "spear", "elytraspear",
            "elytrasword", "elytraspear",
            "mace", "spearmace",
            "spearmacekit", "spearmace",
            "modern", "modernsmp");

    private static final Set<String> NOVA_SLUGS =
            NOVATIERS.stream().map(Gamemode::slug).collect(Collectors.toUnmodifiableSet());

    public static List<Gamemode> of(Source source) {
        return switch (source) {
            case MCTIERS -> MCTIERS;
            case SUBTIERS -> SUBTIERS;
            case NOVATIERS -> NOVATIERS;
        };
    }

    public static Optional<Gamemode> find(Source source, String slug) {
        if (slug == null) {
            return Optional.empty();
        }
        return of(source).stream().filter(m -> m.slug().equals(slug)).findFirst();
    }

    /** "Spear Mace" / "spear_mace" / "SPEARMACE" all collapse to "spearmace". */
    public static Optional<String> normaliseNovaKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        String squashed = apiKey.trim().toLowerCase(Locale.ROOT).replaceAll("[_\\s-]+", "");
        if (NOVA_SLUGS.contains(squashed)) {
            return Optional.of(squashed);
        }
        return Optional.ofNullable(NOVA_ALIASES.get(squashed));
    }

    private Gamemodes() {
    }
}
