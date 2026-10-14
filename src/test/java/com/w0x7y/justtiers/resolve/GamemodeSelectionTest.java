package com.w0x7y.justtiers.resolve;

import com.w0x7y.justtiers.resolve.GamemodeSelection.Use;
import com.w0x7y.justtiers.tier.Source;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** One rule decides when a site's selected gamemode shows, for every caller that explains it. */
class GamemodeSelectionTest {

    private static final Set<Source> ONE = Set.of(Source.PVPTIERS);
    private static final Set<Source> TWO = Set.of(Source.PVPTIERS, Source.SUBTIERS);

    @Test
    void aSingleSiteAlwaysUsesItsSelection() {
        assertTrue(GamemodeSelection.applies(ONE, false));
        assertTrue(GamemodeSelection.applies(ONE, true));
        assertEquals(Use.USED, GamemodeSelection.of(Source.PVPTIERS, ONE, false));
        assertFalse(GamemodeSelection.preferenceMatters(ONE));
    }

    @Test
    void severalSitesUseSelectionsOnlyWhilePreferred() {
        assertFalse(GamemodeSelection.applies(TWO, false));
        assertTrue(GamemodeSelection.applies(TWO, true));
        assertEquals(Use.SHOWING_BEST, GamemodeSelection.of(Source.SUBTIERS, TWO, false));
        assertEquals(Use.USED, GamemodeSelection.of(Source.SUBTIERS, TWO, true));
        assertTrue(GamemodeSelection.preferenceMatters(TWO));
    }

    @Test
    void aDisabledSiteSaysWhatWouldPutItsSelectionToUse() {
        assertEquals(Use.NEEDS_SITE, GamemodeSelection.of(Source.NOVATIERS, TWO, true));
        assertEquals(Use.NEEDS_ONLY_SITE, GamemodeSelection.of(Source.NOVATIERS, TWO, false));
        assertEquals(Use.NEEDS_ONLY_SITE, GamemodeSelection.of(Source.NOVATIERS, ONE, false));
    }

    @Test
    void noEnabledSiteUsesNothing() {
        assertFalse(GamemodeSelection.applies(Set.of(), false));
        assertFalse(GamemodeSelection.preferenceMatters(Set.of()));
        assertEquals(Use.NEEDS_SITE, GamemodeSelection.of(Source.PVPHQ, Set.of(), true));
    }
}
