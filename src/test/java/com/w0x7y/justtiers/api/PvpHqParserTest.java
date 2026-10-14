package com.w0x7y.justtiers.api;

import com.w0x7y.justtiers.tier.Tier;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** PvPHQ gametypes this build does not register are kept when readable and never turn unreadable data into unranked. */
class PvpHqParserTest {

    @Test
    void anUnregisteredGametypeBecomesADiscoveredPlacement() {
        Map<String, Tier> tiers = PvpHqParser.parseProfile("""
                {"ranked":[{"gametype":"sword","tier":"HT2","unranked":false},
                           {"gametype":"Bed_Wars","tier":"MT3","unranked":false}]}
                """);
        assertEquals(Set.of("sword", "bed_wars"), tiers.keySet());
        assertEquals("MT3", tiers.get("bed_wars").label());
    }

    @Test
    void theCartAliasStillResolvesAfterLowercasing() {
        Map<String, Tier> tiers = PvpHqParser.parseProfile("""
                {"ranked":[{"gametype":"HT_CART","tier":"HT4","unranked":false}]}
                """);
        assertEquals(Set.of("cart"), tiers.keySet());
    }

    @Test
    void aProfileOnlyInDiscoveredGametypesIsRanked() {
        assertEquals(Map.of("new_mode", new Tier(1, true, false)), PvpHqParser.parseProfile("""
                {"ranked":[{"gametype":"new_mode","tier":"HT1","unranked":false}]}
                """));
    }

    /** An explicit unranked entry under a readable gametype is a valid answer, discovered or not. */
    @Test
    void anExplicitlyUnrankedDiscoveredGametypeIsAValidUnrankedAnswer() {
        assertTrue(PvpHqParser.parseProfile("""
                {"ranked":[{"gametype":"new_mode","tier":null,"unranked":true}]}
                """).isEmpty());
    }

    @Test
    void aProfileOnlyUnderRefusedGametypesFails() {
        for (String gametype : new String[]{"bad key", "<script>", "x".repeat(33)}) {
            String placed = "{\"ranked\":[{\"gametype\":\"" + gametype + "\",\"tier\":\"HT1\"}]}";
            String unranked = "{\"ranked\":[{\"gametype\":\"" + gametype + "\",\"unranked\":true}]}";
            assertThrows(TierLookupException.class, () -> PvpHqParser.parseProfile(placed), gametype);
            assertThrows(TierLookupException.class, () -> PvpHqParser.parseProfile(unranked), gametype);
        }
    }

    @Test
    void refusedGametypesDoNotDiscardTheRestOfTheProfile() {
        assertEquals(Set.of("axe"), PvpHqParser.parseProfile("""
                {"ranked":[{"gametype":"bad key","tier":"HT1"},
                           {"gametype":"axe","tier":"LT2","unranked":false}]}
                """).keySet());
    }

    @Test
    void discoveredGametypesAreCappedWithoutFailingTheAnswer() {
        StringBuilder ranked = new StringBuilder("{\"ranked\":[");
        for (int i = 0; i < 20; i++) {
            ranked.append("{\"gametype\":\"mode").append(i).append("\",\"tier\":\"LT3\"},");
        }
        ranked.append("{\"gametype\":\"uhc\",\"tier\":\"HT5\"}]}");

        Map<String, Tier> tiers = PvpHqParser.parseProfile(ranked.toString());

        assertEquals(17, tiers.size());
        assertTrue(tiers.containsKey("uhc"));
        assertFalse(tiers.containsKey("mode16"));
    }
}
