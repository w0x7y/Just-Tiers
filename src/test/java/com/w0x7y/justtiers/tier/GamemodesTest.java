package com.w0x7y.justtiers.tier;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GamemodesTest {

    @Test
    void eachSiteHasTheExpectedNumberOfGamemodes() {
        assertEquals(8, Gamemodes.of(Source.MCTIERS).size());
        assertEquals(12, Gamemodes.of(Source.SUBTIERS).size());
        assertEquals(12, Gamemodes.of(Source.NOVATIERS).size());
        assertEquals(32, Gamemodes.ALL.size());
    }

    @Test
    void everyIconCodepointIsUnique() {
        Set<Character> icons = new HashSet<>();
        for (Gamemode mode : Gamemodes.ALL) {
            assertTrue(icons.add(mode.icon()),
                    "duplicate icon for " + mode.source() + "/" + mode.slug());
        }
    }

    @Test
    void iconsLiveInThePrivateUseArea() {
        for (Gamemode mode : Gamemodes.ALL) {
            assertTrue(mode.icon() >= '' && mode.icon() <= '',
                    "icon out of PUA for " + mode.slug());
        }
    }

    @Test
    void slugsAreUniqueWithinASite() {
        for (Source source : Source.values()) {
            Set<String> slugs = new HashSet<>();
            for (Gamemode mode : Gamemodes.of(source)) {
                assertTrue(slugs.add(mode.slug()), "duplicate slug " + mode.slug());
            }
        }
    }

    @Test
    void findLocatesGamemodesBySlug() {
        assertEquals(Optional.of("Vanilla"),
                Gamemodes.find(Source.MCTIERS, "vanilla").map(Gamemode::displayName));
        assertEquals(Optional.of("Netherite OP"),
                Gamemodes.find(Source.MCTIERS, "nethop").map(Gamemode::displayName));
        assertEquals(Optional.of("Diamond SMP"),
                Gamemodes.find(Source.SUBTIERS, "dia_smp").map(Gamemode::displayName));
        assertEquals(Optional.empty(), Gamemodes.find(Source.MCTIERS, "spleef"));
    }

    @Test
    void novaKeysNormaliseToSlugs() {
        assertEquals(Optional.of("spearmace"), Gamemodes.normaliseNovaKey("Spear Mace"));
        assertEquals(Optional.of("elytraspear"), Gamemodes.normaliseNovaKey("Elytra Spear"));
        assertEquals(Optional.of("modernsmp"), Gamemodes.normaliseNovaKey("Modern SMP"));
        assertEquals(Optional.of("diamondop"), Gamemodes.normaliseNovaKey("Diamond OP"));
        assertEquals(Optional.of("diamondcart"), Gamemodes.normaliseNovaKey("Diamond Cart"));
        assertEquals(Optional.of("smp"), Gamemodes.normaliseNovaKey("SMP"));
    }

    @Test
    void novaLegacyAliasesStillResolve() {
        assertEquals(Optional.of("spearmace"), Gamemodes.normaliseNovaKey("mace"));
        assertEquals(Optional.of("elytraspear"), Gamemodes.normaliseNovaKey("spear"));
        assertEquals(Optional.of("elytraspear"), Gamemodes.normaliseNovaKey("elytra_sword"));
        assertEquals(Optional.of("modernsmp"), Gamemodes.normaliseNovaKey("modern"));
        assertEquals(Optional.of("spearmace"), Gamemodes.normaliseNovaKey("spearmacekit"));
    }

    @Test
    void unknownNovaKeysAreRejected() {
        assertEquals(Optional.empty(), Gamemodes.normaliseNovaKey("Quake Pro"));
        assertEquals(Optional.empty(), Gamemodes.normaliseNovaKey(""));
        assertEquals(Optional.empty(), Gamemodes.normaliseNovaKey(null));
    }

    @Test
    void sourcesCarryTheirBrandColours() {
        assertEquals(0xFFFF55, Source.MCTIERS.color());
        assertEquals(0x55FFFF, Source.SUBTIERS.color());
        assertEquals(0xAA55FF, Source.NOVATIERS.color());
    }
}
