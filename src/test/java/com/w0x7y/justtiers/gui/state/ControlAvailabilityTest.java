package com.w0x7y.justtiers.gui.state;

import com.w0x7y.justtiers.config.Palette;
import com.w0x7y.justtiers.tier.Source;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Disabled controls stay visible, with a reason matching the active sites and palette. */
class ControlAvailabilityTest {

    @Test
    void everythingIsLiveInASingleSiteModeForThatSite() {
        var state = ControlAvailability.of(true, Set.of(Source.PVPTIERS), Palette.DEFAULT);
        assertTrue(state.sites());
        assertTrue(state.showRetired());
        assertTrue(state.appearance());
        assertTrue(state.gamemode(Source.PVPTIERS));
        assertFalse(state.gamemode(Source.SUBTIERS));
        assertFalse(state.gamemode(Source.NOVATIERS));
    }

    @Test
    void noGamemodeIsSelectableInAllMode() {
        var state = ControlAvailability.of(true, Set.copyOf(Source.ALL), Palette.DEFAULT);
        assertTrue(state.sites());
        for (Source source : Source.values()) {
            assertFalse(state.gamemode(source));
            assertEquals(ControlAvailability.Reason.MULTIPLE_SITES, state.reasonFor(source));
        }
    }

    @Test
    void disablingTheModGreysEverythingButTheMasterSwitch() {
        var state = ControlAvailability.of(false, Set.of(Source.PVPTIERS), Palette.DEFAULT);
        assertFalse(state.sites());
        assertFalse(state.showRetired());
        assertFalse(state.appearance());
        for (Source source : Source.values()) {
            assertFalse(state.gamemode(source));
            assertEquals(ControlAvailability.Reason.MOD_DISABLED, state.reasonFor(source));
        }
    }

    @Test
    void reasonDistinguishesTheOtherSitesFromAllMode() {
        var state = ControlAvailability.of(true, Set.of(Source.SUBTIERS), Palette.DEFAULT);
        assertEquals(ControlAvailability.Reason.AVAILABLE, state.reasonFor(Source.SUBTIERS));
        assertEquals(ControlAvailability.Reason.SITE_DISABLED, state.reasonFor(Source.PVPTIERS));
        assertEquals(ControlAvailability.Reason.SITE_DISABLED, state.reasonFor(Source.NOVATIERS));
    }

    @Test
    void theBadgeShapeStaysLiveInEveryDisplayMode() {
        // Where the badge sits and what chrome it carries means the same thing whichever
        // sites are being shown, so only the master switch may grey those rows.
        for (Set<Source> sites : java.util.List.of(Set.of(Source.PVPTIERS), Set.of(Source.PVPHQ), Set.of(Source.SUBTIERS), Set.of(Source.NOVATIERS), Set.copyOf(Source.ALL))) {
            assertTrue(ControlAvailability.of(true, sites, Palette.DEFAULT).appearance(), sites.toString());
            assertFalse(ControlAvailability.of(false, sites, Palette.DEFAULT).appearance(), sites.toString());
        }
    }

    @Test
    void everyModeAndToggleCombinationIsCovered() {
        for (Set<Source> sites : java.util.List.of(Set.of(Source.PVPTIERS), Set.of(Source.PVPHQ), Set.of(Source.SUBTIERS), Set.of(Source.NOVATIERS), Set.copyOf(Source.ALL))) {
            for (boolean enabled : new boolean[]{true, false}) {
                var state = ControlAvailability.of(enabled, sites, Palette.DEFAULT);
                for (Source source : Source.values()) {
                    assertEquals(state.gamemode(source),
                            state.reasonFor(source) == ControlAvailability.Reason.AVAILABLE);
                }
            }
        }
    }

    @Test
    void theColorPickersAreLiveOnlyForTheCustomPalette() {
        assertTrue(ControlAvailability.of(true, Set.copyOf(Source.ALL), Palette.CUSTOM).customColors());
        assertFalse(ControlAvailability.of(true, Set.copyOf(Source.ALL), Palette.DEFAULT).customColors());
        assertFalse(ControlAvailability.of(true, Set.copyOf(Source.ALL), Palette.COLORBLIND).customColors());
    }

    @Test
    void theColorPickersAreDeadWhileTheModIsOff() {
        assertFalse(ControlAvailability.of(false, Set.copyOf(Source.ALL), Palette.CUSTOM).customColors());
    }

    @Test
    void thePaletteDoesNotDisturbTheOtherControls() {
        ControlAvailability withCustom =
                ControlAvailability.of(true, Set.copyOf(Source.ALL), Palette.CUSTOM);
        ControlAvailability withDefault =
                ControlAvailability.of(true, Set.copyOf(Source.ALL), Palette.DEFAULT);

        assertEquals(withDefault.sites(), withCustom.sites());
        assertEquals(withDefault.showRetired(), withCustom.showRetired());
        assertEquals(withDefault.appearance(), withCustom.appearance());
        assertEquals(withDefault.reasons(), withCustom.reasons());
    }

}
