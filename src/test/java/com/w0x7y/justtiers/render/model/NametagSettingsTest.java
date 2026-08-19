package com.w0x7y.justtiers.render.model;

import com.w0x7y.justtiers.resolve.DisplayMode;
import com.w0x7y.justtiers.tier.Source;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NametagSettingsTest {

    private static final Map<Source, String> SELECTED = Map.of(
            Source.MCTIERS, "vanilla",
            Source.SUBTIERS, "bow",
            Source.NOVATIERS, "spleef");

    private static NametagSettings settings() {
        return new NametagSettings(true, DisplayMode.ALL, SELECTED, true, NametagStyle.DEFAULT);
    }

    @Test
    void swappingOneGamemodeLeavesEverythingElseAlone() {
        NametagSettings swapped = settings().withGamemode(Source.MCTIERS, "axe");

        assertEquals("axe", swapped.selectedGamemodes().get(Source.MCTIERS));
        assertEquals("bow", swapped.selectedGamemodes().get(Source.SUBTIERS));
        assertEquals("spleef", swapped.selectedGamemodes().get(Source.NOVATIERS));
        assertEquals(settings().enabled(), swapped.enabled());
        assertEquals(settings().displayMode(), swapped.displayMode());
        assertEquals(settings().showRetired(), swapped.showRetired());
        assertEquals(settings().style(), swapped.style());
    }

    @Test
    void swappingAGamemodeLeavesTheOriginalUntouched() {
        NametagSettings original = settings();
        original.withGamemode(Source.MCTIERS, "axe");

        assertEquals("vanilla", original.selectedGamemodes().get(Source.MCTIERS));
    }

    @Test
    void theSelectionIsCopiedRatherThanBorrowed() {
        Map<Source, String> mutable = new HashMap<>(SELECTED);
        NametagSettings settings = new NametagSettings(true, DisplayMode.ALL, mutable, true,
                NametagStyle.DEFAULT);

        mutable.put(Source.MCTIERS, "axe");
        assertEquals("vanilla", settings.selectedGamemodes().get(Source.MCTIERS));
    }

    /** A hand-edited config can leave the style out; a nametag that refused to draw
     * would be worse than one in the default shape. */
    @Test
    void aMissingStyleFallsBackToTheDefault() {
        NametagSettings settings = new NametagSettings(true, DisplayMode.ALL, SELECTED, true, null);

        assertEquals(NametagStyle.DEFAULT, settings.style());
    }

    @Test
    void thePreviewBadgeFollowsTheSettings() {
        assertEquals(settings().previewBadge(0L).plainText(),
                Badge.preview(DisplayMode.ALL, SELECTED, true, 0L, NametagStyle.DEFAULT)
                        .plainText());
    }
}
