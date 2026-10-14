package com.w0x7y.justtiers.render.model;

import com.w0x7y.justtiers.tier.Source;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NametagSettingsTest {

    private static final Map<Source, String> SELECTED = Map.of(
            Source.PVPTIERS, "crystal",
            Source.SUBTIERS, "bow",
            Source.NOVATIERS, "spleef");

    private static NametagSettings settings() {
        return new NametagSettings(true, Set.copyOf(Source.ALL), SELECTED, true, NametagStyle.DEFAULT, false);
    }

    @Test
    void swappingOneGamemodeLeavesEverythingElseAlone() {
        NametagSettings swapped = settings().withGamemode(Source.PVPTIERS, "axe");

        assertEquals("axe", swapped.selectedGamemodes().get(Source.PVPTIERS));
        assertEquals("bow", swapped.selectedGamemodes().get(Source.SUBTIERS));
        assertEquals("spleef", swapped.selectedGamemodes().get(Source.NOVATIERS));
        assertEquals(settings().enabled(), swapped.enabled());
        assertEquals(settings().enabledSources(), swapped.enabledSources());
        assertEquals(settings().showRetired(), swapped.showRetired());
        assertEquals(settings().style(), swapped.style());
    }

    @Test
    void swappingAGamemodeLeavesTheOriginalUntouched() {
        NametagSettings original = settings();
        original.withGamemode(Source.PVPTIERS, "axe");

        assertEquals("crystal", original.selectedGamemodes().get(Source.PVPTIERS));
    }

    @Test
    void theSelectionIsCopiedRatherThanBorrowed() {
        Map<Source, String> mutable = new HashMap<>(SELECTED);
        NametagSettings settings = new NametagSettings(true, Set.copyOf(Source.ALL), mutable, true,
                NametagStyle.DEFAULT, false);

        mutable.put(Source.PVPTIERS, "axe");
        assertEquals("crystal", settings.selectedGamemodes().get(Source.PVPTIERS));
    }

    /** A hand-edited config can leave the style out; a nametag that refused to draw
     * would be worse than one in the default shape. */
    @Test
    void aMissingStyleFallsBackToTheDefault() {
        NametagSettings settings = new NametagSettings(true, Set.copyOf(Source.ALL), SELECTED, true, null, false);

        assertEquals(NametagStyle.DEFAULT, settings.style());
    }

    @Test
    void thePreviewBadgeFollowsTheSettings() {
        assertEquals(settings().previewBadge(0L).plainText(),
                new NametagSettings(true, Set.copyOf(Source.ALL), SELECTED, true, NametagStyle.DEFAULT, false).previewBadge(0L)
                        .plainText());
    }

    @Test
    void automaticLookupsUseTheEnabledSitesOnlyWhileTiersAreOn() {
        Set<Source> sites = Set.of(Source.PVPHQ, Source.NOVATIERS);
        assertEquals(sites, new NametagSettings(true, sites, SELECTED, true,
                NametagStyle.DEFAULT, false).automaticLookupSites());
        assertEquals(Set.of(), new NametagSettings(false, sites, SELECTED, true,
                NametagStyle.DEFAULT, false).automaticLookupSites());
    }

    @Test
    void thePreviewFollowsPreferringSelections() {
        Set<Source> sites = Set.of(Source.PVPTIERS, Source.SUBTIERS);
        Map<Source, String> picks = Map.of(Source.PVPTIERS, "axe", Source.SUBTIERS, "bow");
        String preferred = new NametagSettings(true, sites, picks, false, NametagStyle.DEFAULT, true)
                .previewBadge(0L).plainText();
        String samples = new NametagSettings(true, sites, picks, false, NametagStyle.DEFAULT, false)
                .previewBadge(0L).plainText();
        assertTrue(preferred.contains(com.w0x7y.justtiers.tier.Gamemodes.find(Source.PVPTIERS, "axe")
                .orElseThrow().icon().orElseThrow() + "HT1"), preferred);
        assertNotEquals(preferred, samples);
    }
}
