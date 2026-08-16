package com.w0x7y.justtiers.scan;

import com.w0x7y.justtiers.lookup.LookupReport;
import com.w0x7y.justtiers.lookup.LookupSection;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TierPointsTest {

    private static Tier ht(int level) { return new Tier(level, true, false); }
    private static Tier lt(int level) { return new Tier(level, false, false); }

    @Test
    void everyTierScoresItsPlaceOnTheTenPointScale() {
        assertEquals(10, TierPoints.points(ht(1)));
        assertEquals(9, TierPoints.points(lt(1)));
        assertEquals(8, TierPoints.points(ht(2)));
        assertEquals(7, TierPoints.points(lt(2)));
        assertEquals(6, TierPoints.points(ht(3)));
        assertEquals(5, TierPoints.points(lt(3)));
        assertEquals(4, TierPoints.points(ht(4)));
        assertEquals(3, TierPoints.points(lt(4)));
        assertEquals(2, TierPoints.points(ht(5)));
        assertEquals(1, TierPoints.points(lt(5)));
    }

    @Test
    void retiredPlacementsScoreNothingAtEveryLevel() {
        for (int level = 1; level <= 5; level++) {
            assertEquals(0, TierPoints.points(new Tier(level, true, true)));
            assertEquals(0, TierPoints.points(new Tier(level, false, true)));
        }
    }

    @Test
    void aTotalSumsEveryGamemodeOnEverySite() {
        Map<String, Tier> mctiers = new LinkedHashMap<>();
        mctiers.put("vanilla", ht(1));   // 10
        mctiers.put("sword", lt(3));     // 5
        Map<String, Tier> nova = new LinkedHashMap<>();
        nova.put("vanilla", ht(2));      // 8

        List<LookupSection> sections = List.of(
                LookupReport.section(Source.MCTIERS, Optional.of(mctiers)),
                LookupReport.section(Source.NOVATIERS, Optional.of(nova)));

        assertEquals(23, TierPoints.total(sections));
    }

    @Test
    void unrankedAndUnavailableSitesBothContributeNothing() {
        List<LookupSection> sections = List.of(
                LookupReport.section(Source.MCTIERS, Optional.of(Map.of())),
                LookupReport.section(Source.SUBTIERS, Optional.empty()));

        assertEquals(0, TierPoints.total(sections));
        assertEquals(0, TierPoints.total(List.of()));
    }
}
