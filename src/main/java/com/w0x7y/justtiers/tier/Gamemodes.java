package com.w0x7y.justtiers.tier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class Gamemodes {

    private static final List<Gamemode> MCTIERS = List.of(
            new Gamemode(Source.MCTIERS, "axe", "Axe", ''),
            new Gamemode(Source.MCTIERS, "mace", "Mace", ''),
            new Gamemode(Source.MCTIERS, "nethop", "Netherite OP", ''),
            new Gamemode(Source.MCTIERS, "pot", "Pot", ''),
            new Gamemode(Source.MCTIERS, "smp", "SMP", ''),
            new Gamemode(Source.MCTIERS, "sword", "Sword", ''),
            new Gamemode(Source.MCTIERS, "uhc", "UHC", ''),
            new Gamemode(Source.MCTIERS, "vanilla", "Vanilla", ''));

    private static final List<Gamemode> SUBTIERS = List.of(
            new Gamemode(Source.SUBTIERS, "bed", "Bed", ''),
            new Gamemode(Source.SUBTIERS, "bow", "Bow", ''),
            new Gamemode(Source.SUBTIERS, "creeper", "Creeper", ''),
            new Gamemode(Source.SUBTIERS, "debuff", "DeBuff", ''),
            new Gamemode(Source.SUBTIERS, "dia_crystal", "Diamond Vanilla", ''),
            new Gamemode(Source.SUBTIERS, "dia_smp", "Diamond SMP", ''),
            new Gamemode(Source.SUBTIERS, "elytra", "Elytra", ''),
            new Gamemode(Source.SUBTIERS, "manhunt", "Manhunt", ''),
            new Gamemode(Source.SUBTIERS, "minecart", "Minecart", ''),
            new Gamemode(Source.SUBTIERS, "og_vanilla", "OG Vanilla", ''),
            new Gamemode(Source.SUBTIERS, "speed", "Speed", ''),
            new Gamemode(Source.SUBTIERS, "trident", "Trident", ''));

    private static final List<Gamemode> NOVATIERS = List.of(
            new Gamemode(Source.NOVATIERS, "axe", "Axe", ''),
            new Gamemode(Source.NOVATIERS, "diamondcart", "Diamond Cart", ''),
            new Gamemode(Source.NOVATIERS, "diamondop", "Diamond OP", ''),
            new Gamemode(Source.NOVATIERS, "elytra", "Elytra", ''),
            new Gamemode(Source.NOVATIERS, "elytraspear", "Elytra Spear", ''),
            new Gamemode(Source.NOVATIERS, "modernsmp", "Modern SMP", ''),
            new Gamemode(Source.NOVATIERS, "pufferfish", "Pufferfish", ''),
            new Gamemode(Source.NOVATIERS, "smp", "SMP", ''),
            new Gamemode(Source.NOVATIERS, "spearmace", "Spear Mace", ''),
            new Gamemode(Source.NOVATIERS, "spleef", "Spleef", ''),
            new Gamemode(Source.NOVATIERS, "uhc", "UHC", ''),
            new Gamemode(Source.NOVATIERS, "vanilla", "Vanilla", ''));

    public static final List<Gamemode> ALL =
            java.util.stream.Stream.of(MCTIERS, SUBTIERS, NOVATIERS)
                    .flatMap(List::stream)
                    .toList();

    /**
     * Maps a squashed NovaTiers key to our slug. Mirrors the alias table in
     * novatiers.com/js/script.js so historical key spellings keep resolving.
     */
    private static final Map<String, String> NOVA_ALIASES = buildNovaAliases();

    private static Map<String, String> buildNovaAliases() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("spear", "elytraspear");
        map.put("elytraspear", "elytraspear");
        map.put("elytrasword", "elytraspear");
        map.put("mace", "spearmace");
        map.put("spearmace", "spearmace");
        map.put("spearmacekit", "spearmace");
        map.put("modern", "modernsmp");
        map.put("modernsmp", "modernsmp");
        map.put("smp", "smp");
        map.put("diamondop", "diamondop");
        map.put("diamondcart", "diamondcart");
        map.put("spleef", "spleef");
        map.put("pufferfish", "pufferfish");
        map.put("uhc", "uhc");
        map.put("vanilla", "vanilla");
        map.put("axe", "axe");
        map.put("elytra", "elytra");
        return Map.copyOf(map);
    }

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
        String squashed = apiKey.trim().toLowerCase(Locale.ROOT).replaceAll("[_\s-]+", "");
        return Optional.ofNullable(NOVA_ALIASES.get(squashed));
    }

    private Gamemodes() {
    }
}