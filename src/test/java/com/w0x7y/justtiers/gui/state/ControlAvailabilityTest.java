package com.w0x7y.justtiers.gui.state;

import com.w0x7y.justtiers.config.Palette;
import com.w0x7y.justtiers.resolve.DisplayMode;
import com.w0x7y.justtiers.tier.Source;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ControlAvailabilityTest {

    @Test
    void everythingIsLiveInASingleSiteModeForThatSite() {
        var state = ControlAvailability.of(true, DisplayMode.MCTIERS_ONLY);
        assertTrue(state.displayMode());
        assertTrue(state.showRetired());
        assertTrue(state.appearance());
        assertTrue(state.gamemode(Source.MCTIERS));
        assertFalse(state.gamemode(Source.SUBTIERS));
        assertFalse(state.gamemode(Source.NOVATIERS));
    }

    @Test
    void noGamemodeIsSelectableInAllMode() {
        var state = ControlAvailability.of(true, DisplayMode.ALL);
        assertTrue(state.displayMode());
        for (Source source : Source.values()) {
            assertFalse(state.gamemode(source));
            assertEquals(ControlAvailability.Reason.MODE_IS_ALL, state.reasonFor(source));
        }
    }

    @Test
    void disablingTheModGreysEverythingButTheMasterSwitch() {
        var state = ControlAvailability.of(false, DisplayMode.MCTIERS_ONLY);
        assertFalse(state.displayMode());
        assertFalse(state.showRetired());
        assertFalse(state.appearance());
        for (Source source : Source.values()) {
            assertFalse(state.gamemode(source));
            assertEquals(ControlAvailability.Reason.MOD_DISABLED, state.reasonFor(source));
        }
    }

    @Test
    void reasonDistinguishesTheOtherSitesFromAllMode() {
        var state = ControlAvailability.of(true, DisplayMode.SUBTIERS_ONLY);
        assertEquals(ControlAvailability.Reason.AVAILABLE, state.reasonFor(Source.SUBTIERS));
        assertEquals(ControlAvailability.Reason.OTHER_SITE, state.reasonFor(Source.MCTIERS));
        assertEquals(ControlAvailability.Reason.OTHER_SITE, state.reasonFor(Source.NOVATIERS));
    }

    @Test
    void theBadgeShapeStaysLiveInEveryDisplayMode() {
        // Where the badge sits and what chrome it carries means the same thing whichever
        // sites are being shown, so only the master switch may grey those rows.
        for (DisplayMode mode : DisplayMode.values()) {
            assertTrue(ControlAvailability.of(true, mode).appearance(), mode.toString());
            assertFalse(ControlAvailability.of(false, mode).appearance(), mode.toString());
        }
    }

    @Test
    void everyModeAndToggleCombinationIsCovered() {
        for (DisplayMode mode : DisplayMode.values()) {
            for (boolean enabled : new boolean[]{true, false}) {
                var state = ControlAvailability.of(enabled, mode);
                for (Source source : Source.values()) {
                    assertEquals(state.gamemode(source),
                            state.reasonFor(source) == ControlAvailability.Reason.AVAILABLE);
                }
            }
        }
    }

    @Test
    void theColorPickersAreLiveOnlyForTheCustomPalette() {
        assertTrue(ControlAvailability.of(true, DisplayMode.ALL, Palette.CUSTOM).customColors());
        assertFalse(ControlAvailability.of(true, DisplayMode.ALL, Palette.DEFAULT).customColors());
        assertFalse(ControlAvailability.of(true, DisplayMode.ALL, Palette.COLORBLIND).customColors());
    }

    @Test
    void theColorPickersAreDeadWhileTheModIsOff() {
        assertFalse(ControlAvailability.of(false, DisplayMode.ALL, Palette.CUSTOM).customColors());
    }

    @Test
    void thePaletteDoesNotDisturbTheOtherControls() {
        ControlAvailability withCustom =
                ControlAvailability.of(true, DisplayMode.ALL, Palette.CUSTOM);
        ControlAvailability withDefault =
                ControlAvailability.of(true, DisplayMode.ALL, Palette.DEFAULT);

        assertEquals(withDefault.displayMode(), withCustom.displayMode());
        assertEquals(withDefault.showRetired(), withCustom.showRetired());
        assertEquals(withDefault.appearance(), withCustom.appearance());
        assertEquals(withDefault.reasons(), withCustom.reasons());
    }

    @Test
    void theTwoArgumentFormStillMeansTheDefaultPalette() {
        assertFalse(ControlAvailability.of(true, DisplayMode.ALL).customColors());
    }
}
