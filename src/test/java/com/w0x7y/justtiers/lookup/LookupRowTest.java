package com.w0x7y.justtiers.lookup;

import com.w0x7y.justtiers.tier.Gamemodes;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Drawing, focusable cells and narration read one row state and one cell count. */
class LookupRowTest {

    private static final Tier HT1 = new Tier(1, true, false);

    private static Optional<LookupSection> answered(Source source, Map<String, Tier> tiers) {
        return Optional.of(LookupReport.section(source, Optional.of(tiers)));
    }

    private static Optional<LookupSection> unavailable(Source source) {
        return Optional.of(LookupReport.section(source, Optional.empty()));
    }

    private static Map<String, Tier> discovered(int count) {
        Map<String, Tier> tiers = new HashMap<>();
        for (int i = 0; i < count; i++) {
            tiers.put("future" + i, HT1);
        }
        return tiers;
    }

    @Test
    void aRowWithoutASectionIsPending() {
        assertEquals(LookupRow.State.PENDING, LookupRow.state(Optional.empty()));
    }

    @Test
    void anUnreachableSiteIsUnavailableRatherThanUnranked() {
        assertEquals(LookupRow.State.UNAVAILABLE, LookupRow.state(unavailable(Source.PVPTIERS)));
    }

    @Test
    void rankedAndUnrankedAnswersBothShowCells() {
        String slug = Gamemodes.of(Source.SUBTIERS).getFirst().slug();
        assertEquals(LookupRow.State.ANSWERED, LookupRow.state(answered(Source.SUBTIERS, Map.of(slug, HT1))));
        assertEquals(LookupRow.State.ANSWERED, LookupRow.state(answered(Source.SUBTIERS, Map.of())));
    }

    @Test
    void aRowReservesItsRegisteredCellsBeforeAndWithoutAnAnswer() {
        for (Source source : Source.ALL) {
            int registered = Gamemodes.of(source).size();
            assertEquals(registered, LookupRow.cellCount(source, Optional.empty()), source.name());
            assertEquals(registered, LookupRow.cellCount(source, unavailable(source)), source.name());
            assertEquals(registered, LookupRow.cellCount(source, answered(source, Map.of())), source.name());
        }
    }

    @Test
    void discoveredGamemodesAddCellsAfterTheRegisteredOnes() {
        for (Source source : Source.ALL) {
            int registered = Gamemodes.of(source).size();
            assertEquals(registered + 3, LookupRow.cellCount(source, answered(source, discovered(3))),
                    source.name());
        }
    }

    @Test
    void onlyAnsweredCellsCanBeFocused() {
        Source source = Source.PVPTIERS;
        assertEquals(0, LookupRow.shownCells(Optional.empty()));
        assertEquals(0, LookupRow.shownCells(unavailable(source)));
        assertEquals(Gamemodes.of(source).size(), LookupRow.shownCells(answered(source, Map.of())));
        assertEquals(Gamemodes.of(source).size() + 2, LookupRow.shownCells(answered(source, discovered(2))));
    }
}
