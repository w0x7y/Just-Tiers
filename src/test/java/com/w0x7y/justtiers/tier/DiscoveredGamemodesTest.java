package com.w0x7y.justtiers.tier;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** API slugs are sanitized and bounded before they become gamemodes, and their derived names read well. */
class DiscoveredGamemodesTest {

    private static final Tier HT1 = new Tier(1, true, false);

    @Test
    void displayNamesSplitWordsAndTitleCaseThem() {
        assertEquals("Bed Wars", DiscoveredGamemodes.displayName("bed_wars"));
        assertEquals("Spear Elytra", DiscoveredGamemodes.displayName("spear-elytra"));
        assertEquals("Quake", DiscoveredGamemodes.displayName("quake"));
        assertEquals("Mixed Case", DiscoveredGamemodes.displayName("mixed__case-"));
        assertEquals("1v1 Arena", DiscoveredGamemodes.displayName("1v1_arena"));
    }

    @Test
    void knownShortAcronymsStayInCapitals() {
        assertEquals("Diamond SMP", DiscoveredGamemodes.displayName("diamond_smp"));
        assertEquals("OG Vanilla", DiscoveredGamemodes.displayName("og_vanilla"));
        assertEquals("UHC", DiscoveredGamemodes.displayName("uhc"));
        assertEquals("Netherite OP", DiscoveredGamemodes.displayName("netherite-op"));
        assertEquals("Sumo FFA", DiscoveredGamemodes.displayName("sumo_ffa"));
        // A word that merely starts like an acronym is an ordinary word.
        assertEquals("Opal Smpish", DiscoveredGamemodes.displayName("opal_smpish"));
    }

    @Test
    void onlySanitizedSlugsAreAcceptable() {
        for (String slug : new String[]{"a", "bed_wars", "spear-mace", "1v1", "x".repeat(32)}) {
            assertTrue(DiscoveredGamemodes.isAcceptable(slug), slug);
        }
        for (String slug : new String[]{null, "", "_a", "-a", "Bed", "bed wars", "a.b", "<b>",
                "é", "x".repeat(33), "a\nb"}) {
            assertFalse(DiscoveredGamemodes.isAcceptable(slug), String.valueOf(slug));
        }
    }

    @Test
    void profileKeysAreTrimmedAndLowercased() {
        assertEquals(Optional.of("bed_wars"), DiscoveredGamemodes.profileSlug(" Bed_Wars "));
        assertEquals(Optional.of("crystal"), DiscoveredGamemodes.profileSlug("CRYSTAL"));
        assertEquals(Optional.empty(), DiscoveredGamemodes.profileSlug("bed wars"));
        assertEquals(Optional.empty(), DiscoveredGamemodes.profileSlug(null));
    }

    @Test
    void novaKeysPreferRegisteredSlugsAndOtherwiseKeepTheirWords() {
        assertEquals(Optional.of("spearmace"), DiscoveredGamemodes.novaSlug("Spear Mace"));
        assertEquals(Optional.of("spearmace"), DiscoveredGamemodes.novaSlug("mace"));
        assertEquals(Optional.of("quake_pro"), DiscoveredGamemodes.novaSlug("Quake  Pro"));
        assertEquals("Quake Pro", DiscoveredGamemodes.displayName(
                DiscoveredGamemodes.novaSlug("Quake Pro").orElseThrow()));
        assertEquals(Optional.empty(), DiscoveredGamemodes.novaSlug("Quake (Pro)"));
        assertEquals(Optional.empty(), DiscoveredGamemodes.novaSlug(" "));
        assertEquals(Optional.empty(), DiscoveredGamemodes.novaSlug(null));
    }

    @Test
    void anAbbreviationIsOneCapitalLetter() {
        assertEquals("B", DiscoveredGamemodes.abbreviation("Bed Wars"));
        assertEquals("1", DiscoveredGamemodes.abbreviation("1v1 Arena"));
        assertEquals("?", DiscoveredGamemodes.abbreviation(""));
    }

    @Test
    void aDiscoveredGamemodeHasADerivedNameAndNoIcon() {
        Gamemode gamemode = Gamemodes.forSlug(Source.SUBTIERS, "bed_wars").orElseThrow();
        assertEquals(new Gamemode(Source.SUBTIERS, "bed_wars", "Bed Wars", Optional.empty()), gamemode);
        assertFalse(Gamemodes.isRegistered(gamemode));
        assertEquals(Optional.empty(), Gamemodes.find(Source.SUBTIERS, "bed_wars"),
                "settings only ever find registered gamemodes");
        assertEquals(Optional.empty(), Gamemodes.forSlug(Source.SUBTIERS, "Bed Wars"));
    }

    @Test
    void aRegisteredSlugAlwaysResolvesToItsRegistryEntry() {
        for (Gamemode registered : Gamemodes.ALL) {
            assertEquals(Optional.of(registered),
                    Gamemodes.forSlug(registered.source(), registered.slug()));
            assertTrue(Gamemodes.isRegistered(registered));
        }
    }

    @Test
    void theCollectorAdmitsRegisteredGamemodesBeyondTheDiscoveredCap() {
        DiscoveredGamemodes.Collector collector = new DiscoveredGamemodes.Collector(Source.PVPTIERS);
        for (int i = 0; i < DiscoveredGamemodes.MAX_PER_ANSWER; i++) {
            assertTrue(collector.offer("mode" + i, HT1));
        }
        assertFalse(collector.offer("one_too_many", HT1));
        assertTrue(collector.offer("axe", HT1));
        assertTrue(collector.offer("mode0", new Tier(5, false, false)), "already present");
        assertFalse(collector.offer("Not Sanitized", HT1));

        Map<String, Tier> tiers = collector.tiers();
        assertEquals(DiscoveredGamemodes.MAX_PER_ANSWER + 1, tiers.size());
        assertEquals(HT1, tiers.get("mode0"), "the first placement for a slug wins");
        assertEquals(DiscoveredGamemodes.MAX_PER_ANSWER, collector.discovered());
        assertEquals(2, collector.refused());
        assertThrows(UnsupportedOperationException.class, () -> tiers.put("x", HT1));
    }
}
