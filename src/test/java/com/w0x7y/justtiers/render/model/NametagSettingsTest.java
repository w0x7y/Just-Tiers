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
        return new NametagSettings(true, Set.copyOf(Source.ALL), SELECTED, true, NametagStyle.DEFAULT);
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
                NametagStyle.DEFAULT);

        mutable.put(Source.PVPTIERS, "axe");
        assertEquals("crystal", settings.selectedGamemodes().get(Source.PVPTIERS));
    }

    /** A hand-edited config can leave the style out; a nametag that refused to draw
     * would be worse than one in the default shape. */
    @Test
    void aMissingStyleFallsBackToTheDefault() {
        NametagSettings settings = new NametagSettings(true, Set.copyOf(Source.ALL), SELECTED, true, null);

        assertEquals(NametagStyle.DEFAULT, settings.style());
    }

    @Test
    void thePreviewBadgeFollowsTheSettings() {
        assertEquals(settings().previewBadge(0L).plainText(),
                Badge.preview(Set.copyOf(Source.ALL), SELECTED, true, 0L, NametagStyle.DEFAULT)
                        .plainText());
    }
}
