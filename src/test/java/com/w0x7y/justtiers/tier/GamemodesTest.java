package com.w0x7y.justtiers.tier;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GamemodesTest {

    @Test
    void eachSiteHasTheExpectedNumberOfGamemodes() {
        assertEquals(8, Gamemodes.of(Source.PVPTIERS).size());
        assertEquals(12, Gamemodes.of(Source.SUBTIERS).size());
        assertEquals(12, Gamemodes.of(Source.NOVATIERS).size());
        assertEquals(11, Gamemodes.of(Source.PVPHQ).size());
        assertEquals(43, Gamemodes.ALL.size());
    }

    @Test
    void everyIconCodepointIsUnique() {
        Set<Character> icons = new HashSet<>();
        for (Gamemode mode : Gamemodes.ALL) {
            assertTrue(icons.add(mode.icon().orElseThrow()),
                    "duplicate icon for " + mode.source() + "/" + mode.slug());
        }
    }

    @Test
    void iconsLiveInThePrivateUseArea() {
        for (Gamemode mode : Gamemodes.ALL) {
            assertTrue(mode.icon().orElseThrow() >= '\uE000' && mode.icon().orElseThrow() <= '\uF8FF',
                    "icon out of PUA for " + mode.slug());
        }
    }

    /** Discovered gamemodes are the only ones without a glyph. */
    @Test
    void everyRegisteredGamemodeHasAGlyphAndASanitizedSlug() {
        for (Gamemode mode : Gamemodes.ALL) {
            assertTrue(mode.icon().isPresent(), mode.slug());
            assertTrue(DiscoveredGamemodes.isAcceptable(mode.slug()), mode.slug());
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
        assertEquals(Optional.of("Crystal"),
                Gamemodes.find(Source.PVPTIERS, "crystal").map(Gamemode::displayName));
        assertEquals(Optional.of("Netherite Pot"),
                Gamemodes.find(Source.PVPTIERS, "neth_pot").map(Gamemode::displayName));
        assertEquals(Optional.of("Diamond SMP"),
                Gamemodes.find(Source.SUBTIERS, "dia_smp").map(Gamemode::displayName));
        assertEquals(Optional.empty(), Gamemodes.find(Source.PVPTIERS, "spleef"));
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
    void discoveredGamemodesAreTheUnregisteredAcceptableSlugsBySlug() {
        var discovered = Gamemodes.discovered(Source.PVPTIERS,
                java.util.List.of("zz_mode", "axe", "Not Valid", "aa_mode"));

        assertEquals(java.util.List.of("aa_mode", "zz_mode"),
                discovered.stream().map(Gamemode::slug).toList());
        for (Gamemode gamemode : discovered) {
            assertFalse(Gamemodes.isRegistered(gamemode));
            assertTrue(gamemode.icon().isEmpty());
        }
        assertTrue(Gamemodes.discovered(Source.PVPTIERS, java.util.List.of("axe", "sword")).isEmpty());
    }

    @Test
    void discoveredGamemodesAreCappedPerAnswer() {
        var slugs = new java.util.ArrayList<String>();
        for (int i = 0; i < DiscoveredGamemodes.MAX_PER_ANSWER + 4; i++) slugs.add(String.format("mode_%02d", i));

        var discovered = Gamemodes.discovered(Source.NOVATIERS, slugs);

        assertEquals(DiscoveredGamemodes.MAX_PER_ANSWER, discovered.size());
        assertEquals("mode_00", discovered.getFirst().slug());
    }
}
