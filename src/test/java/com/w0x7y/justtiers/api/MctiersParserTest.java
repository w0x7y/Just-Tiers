package com.w0x7y.justtiers.api;

import com.w0x7y.justtiers.tier.Tier;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MctiersParserTest {

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
        Map<String, Tier> tiers = MctiersParser.parseRankings(MARLOWWW_JSON);
        assertEquals("RHT1", tiers.get("vanilla").label());
        assertEquals("RLT1", tiers.get("uhc").label());
    }

    @Test
    void retiredFlagIsCarriedThrough() {
        Map<String, Tier> tiers = MctiersParser.parseRankings(MARLOWWW_JSON);
        assertTrue(tiers.get("nethop").retired());
        assertFalse(MctiersParser.parseRankings(ACTIVE_JSON).get("sword").retired());
    }

    @Test
    void allGamemodeKeysArePreserved() {
        assertEquals(java.util.Set.of("uhc", "nethop", "vanilla"),
                MctiersParser.parseRankings(MARLOWWW_JSON).keySet());
    }

    @Test
    void peakFieldsAreIgnoredEvenWhenBetterThanCurrent() {
        // sword is currently HT2 with a peak of LT1; the peak must not leak into the result.
        assertEquals("HT2", MctiersParser.parseRankings(ACTIVE_JSON).get("sword").label());
    }

    @Test
    void nullPeaksDoNotBreakParsing() {
        assertEquals("LT5", MctiersParser.parseRankings(ACTIVE_JSON).get("pot").label());
    }

    @Test
    void emptyAndNullBodiesYieldEmptyMaps() {
        assertTrue(MctiersParser.parseRankings(null).isEmpty());
        assertTrue(MctiersParser.parseRankings("").isEmpty());
        assertTrue(MctiersParser.parseRankings("   ").isEmpty());
        assertTrue(MctiersParser.parseRankings("{}").isEmpty());
    }

    @Test
    void malformedBodiesYieldEmptyMapsRatherThanThrowing() {
        assertTrue(MctiersParser.parseRankings("not json").isEmpty());
        assertTrue(MctiersParser.parseRankings("[1,2,3]").isEmpty());
    }

    @Test
    void entriesWithOutOfRangeTiersAreSkippedNotFatal() {
        String json = """
                {"good":{"tier":3,"pos":0,"retired":false},
                 "bad":{"tier":9,"pos":0,"retired":false},
                 "alsoBad":{"tier":"x","pos":0,"retired":false}}
                """;
        Map<String, Tier> tiers = MctiersParser.parseRankings(json);
        assertEquals(java.util.Set.of("good"), tiers.keySet());
        assertEquals("HT3", tiers.get("good").label());
    }
}
