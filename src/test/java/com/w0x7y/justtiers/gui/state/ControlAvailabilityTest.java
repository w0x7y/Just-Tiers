package com.w0x7y.justtiers.gui.state;

import com.w0x7y.justtiers.config.Palette;
import com.w0x7y.justtiers.tier.Source;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.w0x7y.justtiers.gui.state.ControlAvailability.Reason.*;
import static org.junit.jupiter.api.Assertions.*;

/** Disabled controls stay visible, with a reason matching the active sites and palette. */
class ControlAvailabilityTest {

    @Test
    void everythingIsLiveInASingleSiteModeForThatSite() {
        var state = ControlAvailability.of(true, Set.of(Source.PVPTIERS), false, Palette.DEFAULT);
        assertEquals(AVAILABLE, state.sites());
        assertEquals(AVAILABLE, state.showRetired());
        assertEquals(AVAILABLE, state.appearance());
        assertEquals(AVAILABLE, state.reasonFor(Source.PVPTIERS));
        assertEquals(ONLY_SITE_NEEDED, state.reasonFor(Source.SUBTIERS));
        assertEquals(ONLY_SITE_NEEDED, state.reasonFor(Source.NOVATIERS));
    }

    @Test
    void noGamemodeIsSelectableInAllMode() {
        var state = ControlAvailability.of(true, Set.copyOf(Source.ALL), false, Palette.DEFAULT);
        assertEquals(AVAILABLE, state.sites());
        assertEquals(AVAILABLE, state.preferSelected());
        for (Source source : Source.values()) {
            assertEquals(MULTIPLE_SITES, state.reasonFor(source));
        }
    }

    @Test
    void disablingNametagsGraysEveryNametagControl() {
        var state = ControlAvailability.of(false, Set.of(Source.PVPTIERS), false, Palette.DEFAULT);
        assertEquals(MOD_DISABLED, state.sites());
        assertEquals(MOD_DISABLED, state.showRetired());
        assertEquals(MOD_DISABLED, state.preferSelected());
        assertEquals(MOD_DISABLED, state.appearance());
        for (Source source : Source.values()) {
            assertEquals(MOD_DISABLED, state.reasonFor(source));
        }
    }

    @Test
    void reasonDistinguishesTheOtherSitesFromAllMode() {
        var state = ControlAvailability.of(true, Set.of(Source.SUBTIERS), false, Palette.DEFAULT);
        assertEquals(AVAILABLE, state.reasonFor(Source.SUBTIERS));
        // Without preferring selections a disabled site's pick is used only once it is the
        // only enabled site, which is a different explanation from "enable it".
        assertEquals(ONLY_SITE_NEEDED, state.reasonFor(Source.PVPTIERS));
        assertEquals(ONLY_SITE_NEEDED, state.reasonFor(Source.NOVATIERS));
    }

    @Test
    void theBadgeShapeStaysLiveInEveryDisplayMode() {
        // Where the badge sits and what chrome it carries means the same thing whichever
        // sites are being shown, so only the master switch may grey those rows.
        for (Set<Source> sites : java.util.List.of(Set.of(Source.PVPTIERS), Set.of(Source.PVPHQ), Set.of(Source.SUBTIERS), Set.of(Source.NOVATIERS), Set.copyOf(Source.ALL))) {
            assertEquals(AVAILABLE, ControlAvailability.of(true, sites, false, Palette.DEFAULT).appearance(), sites.toString());
            assertEquals(MOD_DISABLED, ControlAvailability.of(false, sites, false, Palette.DEFAULT).appearance(), sites.toString());
        }
    }

    @Test
    void theNametagRowsFollowOnlyTheMasterSwitch() {
        for (Set<Source> sites : java.util.List.of(Set.<Source>of(), Set.of(Source.PVPTIERS), Set.of(Source.NOVATIERS), Set.copyOf(Source.ALL))) {
            for (boolean preferSelected : new boolean[]{true, false}) {
                var on = ControlAvailability.of(true, sites, preferSelected, Palette.CUSTOM);
                var off = ControlAvailability.of(false, sites, preferSelected, Palette.CUSTOM);
                String inputs = sites + " prefer=" + preferSelected;
                assertEquals(AVAILABLE, on.sites(), inputs);
                assertEquals(AVAILABLE, on.showRetired(), inputs);
                assertEquals(MOD_DISABLED, off.sites(), inputs);
                assertEquals(MOD_DISABLED, off.showRetired(), inputs);
            }
        }
    }

    @Test
    void theColorPickersAreLiveOnlyForTheCustomPalette() {
        assertEquals(AVAILABLE, ControlAvailability.of(true, Set.copyOf(Source.ALL), false, Palette.CUSTOM).customColors());
        assertEquals(PALETTE_NOT_CUSTOM, ControlAvailability.of(true, Set.copyOf(Source.ALL), false, Palette.DEFAULT).customColors());
        assertEquals(PALETTE_NOT_CUSTOM, ControlAvailability.of(true, Set.copyOf(Source.ALL), false, Palette.COLORBLIND).customColors());
    }

    @Test
    void theColorPickersFollowOnlyThePaletteWhileNametagsAreOff() {
        // The palette still colors the lookup screen and download progress.
        assertEquals(AVAILABLE, ControlAvailability.of(false, Set.copyOf(Source.ALL), false, Palette.CUSTOM).customColors());
        assertEquals(PALETTE_NOT_CUSTOM, ControlAvailability.of(false, Set.copyOf(Source.ALL), false, Palette.DEFAULT).customColors());
        assertEquals(PALETTE_NOT_CUSTOM, ControlAvailability.of(false, Set.of(), false, Palette.HIGH_CONTRAST).customColors());
    }

    @Test
    void thePaletteDoesNotDisturbTheOtherControls() {
        ControlAvailability withCustom =
                ControlAvailability.of(true, Set.copyOf(Source.ALL), false, Palette.CUSTOM);
        ControlAvailability withDefault =
                ControlAvailability.of(true, Set.copyOf(Source.ALL), false, Palette.DEFAULT);

        assertEquals(withDefault.sites(), withCustom.sites());
        assertEquals(withDefault.showRetired(), withCustom.showRetired());
        assertEquals(withDefault.preferSelected(), withCustom.preferSelected());
        assertEquals(withDefault.appearance(), withCustom.appearance());
        assertEquals(withDefault.reasons(), withCustom.reasons());
    }

    @Test
    void preferringSelectionsMakesEveryEnabledSitesPickerLive() {
        var state = ControlAvailability.of(true, Set.of(Source.PVPTIERS, Source.SUBTIERS),
                true, Palette.DEFAULT);
        assertEquals(AVAILABLE, state.reasonFor(Source.PVPTIERS));
        assertEquals(AVAILABLE, state.reasonFor(Source.SUBTIERS));
        assertEquals(SITE_DISABLED, state.reasonFor(Source.PVPHQ));
        assertEquals(SITE_DISABLED, state.reasonFor(Source.NOVATIERS));
    }

    @Test
    void preferringSelectionsOnlyMattersWithSeveralSites() {
        assertEquals(AVAILABLE, ControlAvailability.of(true, Set.copyOf(Source.ALL), false, Palette.DEFAULT)
                .preferSelected());
        assertEquals(SINGLE_SITE, ControlAvailability.of(true, Set.of(Source.PVPHQ), false, Palette.DEFAULT)
                .preferSelected());
        assertEquals(SINGLE_SITE, ControlAvailability.of(true, Set.of(), true, Palette.DEFAULT)
                .preferSelected());
        assertEquals(MOD_DISABLED, ControlAvailability.of(false, Set.copyOf(Source.ALL), true, Palette.DEFAULT)
                .preferSelected());
    }

    @Test
    void theModSwitchStillGreysPickersWhenSelectionsArePreferred() {
        var state = ControlAvailability.of(false, Set.copyOf(Source.ALL), true, Palette.DEFAULT);
        for (Source source : Source.values()) {
            assertEquals(MOD_DISABLED, state.reasonFor(source));
        }
    }
}
