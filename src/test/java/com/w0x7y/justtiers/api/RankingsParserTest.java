package com.w0x7y.justtiers.api;

import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Current rankings survive partial bad data, while invalid payloads cannot masquerade as unranked players. */
class RankingsParserTest {

    private static final String MARLOWWW_JSON = """
            {"uhc":{"tier":1,"pos":1,"peak_tier":1,"peak_pos":1,"attained":1784635509,"retired":true},
             "nethop":{"tier":1,"pos":0,"peak_tier":1,"peak_pos":0,"attained":1784635498,"retired":true},
             "vanilla":{"tier":1,"pos":0,"peak_tier":1,"peak_pos":0,"attained":1784635475,"retired":true}}
            """;

    private static final String ACTIVE_JSON = """
            {"sword":{"tier":2,"pos":0,"peak_tier":1,"peak_pos":1,"attained":1784635481,"retired":false},
             "pot":{"tier":5,"pos":1,"peak_tier":null,"peak_pos":null,"attained":1784635494,"retired":false}}
            """;

    @Test
    void posZeroIsHighAndPosOneIsLow() {
        Map<String, Tier> tiers = RankingsParser.parseRankings(Source.SUBTIERS, MARLOWWW_JSON);
        assertEquals("RHT1", tiers.get("vanilla").label());
        assertEquals("RLT1", tiers.get("uhc").label());
    }

    @Test
    void retiredFlagIsCarriedThrough() {
        Map<String, Tier> tiers = RankingsParser.parseRankings(Source.SUBTIERS, MARLOWWW_JSON);
        assertTrue(tiers.get("nethop").retired());
        assertFalse(RankingsParser.parseRankings(Source.SUBTIERS, ACTIVE_JSON).get("sword").retired());
    }

    @Test
    void allGamemodeKeysArePreserved() {
        assertEquals(java.util.Set.of("uhc", "nethop", "vanilla"),
                RankingsParser.parseRankings(Source.SUBTIERS, MARLOWWW_JSON).keySet());
    }

    @Test
    void peakFieldsAreIgnoredEvenWhenBetterThanCurrent() {
        // sword is currently HT2 with a peak of LT1; the peak must not leak into the result.
        assertEquals("HT2", RankingsParser.parseRankings(Source.SUBTIERS, ACTIVE_JSON).get("sword").label());
    }

    @Test
    void nullPeaksDoNotBreakParsing() {
        assertEquals("LT5", RankingsParser.parseRankings(Source.SUBTIERS, ACTIVE_JSON).get("pot").label());
    }

    @Test
    void anEmptyObjectIsAValidUnrankedAnswer() {
        assertTrue(RankingsParser.parseRankings(Source.SUBTIERS, " {} ").isEmpty());
    }

    @Test
    void malformedBodiesAreNotValidUnrankedAnswers() {
        for (String body : new String[]{null, "", "   ", "not json", "[1,2,3]", "null",
                "{\"error\":\"maintenance\"}", "{\"axe\":{\"grade\":3}}"}) {
            assertThrows(TierLookupException.class, () -> RankingsParser.parseRankings(Source.SUBTIERS, body));
        }
    }

    @Test
    void entriesWithOutOfRangeTiersAreSkippedNotFatal() {
        String json = """
                {"good":{"tier":3,"pos":0,"retired":false},
                 "bad":{"tier":9,"pos":0,"retired":false},
                 "alsoBad":{"tier":"x","pos":0,"retired":false}}
                """;
        Map<String, Tier> tiers = RankingsParser.parseRankings(Source.SUBTIERS, json);
        assertEquals(java.util.Set.of("good"), tiers.keySet());
        assertEquals("HT3", tiers.get("good").label());
    }

    // --- gamemodes this build does not register ---

    @Test
    void keysAreLowercasedSoRegisteredGamemodesStillMatch() {
        Map<String, Tier> tiers = RankingsParser.parseRankings(Source.PVPTIERS,
                "{\"Crystal\":{\"tier\":2,\"pos\":0},\"Bed_Wars\":{\"tier\":3,\"pos\":1}}");
        assertEquals(Set.of("crystal", "bed_wars"), tiers.keySet());
    }

    /**
     * The wholly-unrecognized rule protects against payloads we cannot read. A profile
     * whose only placements are in valid but unregistered gamemodes is readable, so it is
     * a ranked answer rather than a failure.
     */
    @Test
    void aProfileOnlyInDiscoveredGamemodesIsARankedAnswer() {
        Map<String, Tier> tiers = RankingsParser.parseRankings(Source.SUBTIERS,
                "{\"future_mode\":{\"tier\":1,\"pos\":0,\"retired\":false}}");
        assertEquals(Map.of("future_mode", new Tier(1, true, false)), tiers);
    }

    /**
     * Placements under keys that fail sanitizing are dropped. When nothing else is left,
     * the payload is wholly unrecognized: it fails rather than claiming the player is
     * unranked.
     */
    @Test
    void aProfileOnlyUnderRefusedKeysFails() {
        for (String key : new String[]{"bad key", "<b>x</b>", "_leading", "x".repeat(33), "", "é"}) {
            String body = "{\"" + key + "\":{\"tier\":1,\"pos\":0}}";
            assertThrows(TierLookupException.class,
                    () -> RankingsParser.parseRankings(Source.SUBTIERS, body), key);
        }
    }

    @Test
    void refusedKeysDoNotDiscardTheRestOfTheProfile() {
        Map<String, Tier> tiers = RankingsParser.parseRankings(Source.SUBTIERS,
                "{\"bad key\":{\"tier\":1,\"pos\":0},\"bow\":{\"tier\":2,\"pos\":1}}");
        assertEquals(Set.of("bow"), tiers.keySet());
    }

    @Test
    void discoveredGamemodesAreCappedButRegisteredOnesAreAlwaysKept() {
        StringBuilder json = new StringBuilder("{");
        for (int i = 0; i < 20; i++) {
            json.append("\"mode").append(i).append("\":{\"tier\":3,\"pos\":0},");
        }
        json.append("\"bow\":{\"tier\":4,\"pos\":0}}");

        Map<String, Tier> tiers = RankingsParser.parseRankings(Source.SUBTIERS, json.toString());

        assertEquals(17, tiers.size());
        assertTrue(tiers.containsKey("bow"));
        assertTrue(tiers.containsKey("mode15"), "the sixteenth in payload order");
        assertFalse(tiers.containsKey("mode16"));
    }
}
