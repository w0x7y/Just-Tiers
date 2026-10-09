package com.w0x7y.justtiers.tier;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TierTest {

    @Test
    void middleTiersKeepTheirLabelAndSortBetweenHighAndLow() {
        Optional<Tier> middle = Tier.parse("MT3");
        assertTrue(middle.isPresent(), "PvPHQ middle tiers must be recognized");
        assertEquals("MT3", middle.orElseThrow().label());
        assertTrue(Tier.parse("HT3").orElseThrow().compareTo(middle.orElseThrow()) < 0);
        assertTrue(middle.orElseThrow().compareTo(Tier.parse("LT3").orElseThrow()) < 0);
        assertTrue(Tier.parse("LT2").orElseThrow().compareTo(middle.orElseThrow()) < 0);
        assertEquals("RMT4", Tier.parse("rmt4").orElseThrow().label());
    }

    @Test
    void rankOrdersHt1BestAndLt5Worst() {
        assertEquals(0, new Tier(1, true, false).rank());
        assertEquals(2, new Tier(1, false, false).rank());
        assertEquals(3, new Tier(2, true, false).rank());
        assertEquals(12, new Tier(5, true, false).rank());
        assertEquals(14, new Tier(5, false, false).rank());
    }

    @Test
    void sortingProducesTheDocumentedOrder() {
        List<Tier> tiers = new java.util.ArrayList<>(List.of(
                new Tier(5, false, false), new Tier(1, true, false),
                new Tier(3, false, false), new Tier(2, true, false)));
        java.util.Collections.sort(tiers);
        assertEquals(List.of("HT1", "HT2", "LT3", "LT5"),
                tiers.stream().map(Tier::label).toList());
    }

    @Test
    void activeBeatsRetiredAtEqualRank() {
        Tier active = new Tier(2, true, false);
        Tier retired = new Tier(2, true, true);
        assertTrue(active.compareTo(retired) < 0);
    }

    @Test
    void labelPrefixesRetiredWithR() {
        assertEquals("HT1", new Tier(1, true, false).label());
        assertEquals("LT4", new Tier(4, false, false).label());
        assertEquals("RHT1", new Tier(1, true, true).label());
        assertEquals("RLT5", new Tier(5, false, true).label());
    }

    @Test
    void parseReadsNovaStyleStrings() {
        assertEquals(Optional.of(new Tier(1, true, false)), Tier.parse("HT1"));
        assertEquals(Optional.of(new Tier(5, false, false)), Tier.parse("lt5"));
        assertEquals(Optional.of(new Tier(2, true, true)), Tier.parse("RHT2"));
    }

    @Test
    void parseRejectsUnrankedAndGarbage() {
        assertEquals(Optional.empty(), Tier.parse("-"));
        assertEquals(Optional.empty(), Tier.parse(""));
        assertEquals(Optional.empty(), Tier.parse(null));
        assertEquals(Optional.empty(), Tier.parse("HT6"));
        assertEquals(Optional.empty(), Tier.parse("XT1"));
    }

    @Test
    void constructorRejectsOutOfRangeLevels() {
        assertThrows(IllegalArgumentException.class, () -> new Tier(0, true, false));
        assertThrows(IllegalArgumentException.class, () -> new Tier(6, true, false));
    }
}
