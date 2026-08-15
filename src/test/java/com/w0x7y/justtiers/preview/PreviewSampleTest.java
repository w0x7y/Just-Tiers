package com.w0x7y.justtiers.preview;

import com.w0x7y.justtiers.render.model.BadgePosition;
import com.w0x7y.justtiers.render.model.NametagModel;
import com.w0x7y.justtiers.render.model.NametagStyle;
import com.w0x7y.justtiers.resolve.DisplayMode;
import com.w0x7y.justtiers.tier.Gamemode;
import com.w0x7y.justtiers.tier.Gamemodes;
import com.w0x7y.justtiers.tier.Source;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PreviewSampleTest {

    private static final Map<Source, String> DEFAULTS = Map.of(
            Source.MCTIERS, "vanilla",
            Source.SUBTIERS, "elytra",
            Source.NOVATIERS, "vanilla");

    private static DisplayMode modeOf(Source source) {
        return switch (source) {
            case MCTIERS -> DisplayMode.MCTIERS_ONLY;
            case SUBTIERS -> DisplayMode.SUBTIERS_ONLY;
            case NOVATIERS -> DisplayMode.NOVATIERS_ONLY;
        };
    }

    private String text(DisplayMode mode, Map<Source, String> selected, boolean retired) {
        return NametagModel.plainText(PreviewSample.segments(mode, selected, retired, NametagStyle.DEFAULT));
    }

    private static Gamemode gamemode(Source source, String slug) {
        return Gamemodes.find(source, slug).orElseThrow();
    }

    private static String entry(Gamemode gamemode, boolean retired) {
        return gamemode.icon() + (retired ? "RHT1" : "HT1");
    }

    @Test
    void everyGamemodeOnEverySitePreviewsAsTierOne() {
        for (Source source : Source.values()) {
            for (Gamemode gamemode : Gamemodes.of(source)) {
                String shown = text(modeOf(source), Map.of(source, gamemode.slug()), false);
                assertEquals("[" + entry(gamemode, false) + "] ", shown,
                        source + "/" + gamemode.slug() + " should preview as HT1");
            }
        }
    }

    @Test
    void allModeShowsTheFixedTrio() {
        String expected = "[" + entry(gamemode(Source.MCTIERS, "vanilla"), false)
                + " " + entry(gamemode(Source.SUBTIERS, "minecart"), false)
                + " " + entry(gamemode(Source.NOVATIERS, "spearmace"), false)
                + "] ";
        assertEquals(expected, text(DisplayMode.ALL, DEFAULTS, false));
    }

    @Test
    void allModeIgnoresTheGamemodeSelections() {
        Map<Source, String> other = Map.of(
                Source.MCTIERS, "axe",
                Source.SUBTIERS, "bow",
                Source.NOVATIERS, "spleef");
        assertEquals(text(DisplayMode.ALL, DEFAULTS, false),
                text(DisplayMode.ALL, other, false));
    }

    @Test
    void theRetiredPhaseMarksEveryEntry() {
        String expected = "[" + entry(gamemode(Source.MCTIERS, "vanilla"), true)
                + " " + entry(gamemode(Source.SUBTIERS, "minecart"), true)
                + " " + entry(gamemode(Source.NOVATIERS, "spearmace"), true)
                + "] ";
        assertEquals(expected, text(DisplayMode.ALL, DEFAULTS, true));
        assertEquals("[" + entry(gamemode(Source.MCTIERS, "axe"), true) + "] ",
                text(DisplayMode.MCTIERS_ONLY, Map.of(Source.MCTIERS, "axe"), true));
    }

    @Test
    void hidingRetiredTiersPinsThePreviewToActive() {
        for (long time = 0; time < 4 * PreviewSample.RETIRED_CYCLE_MILLIS; time += 250) {
            assertFalse(PreviewSample.retiredPhase(false, time),
                    "retired phase should never run while retired tiers are hidden");
        }
    }

    @Test
    void theRetiredPhaseAlternatesEveryFiveSeconds() {
        long cycle = PreviewSample.RETIRED_CYCLE_MILLIS;
        assertFalse(PreviewSample.retiredPhase(true, 0));
        assertFalse(PreviewSample.retiredPhase(true, cycle - 1));
        assertTrue(PreviewSample.retiredPhase(true, cycle));
        assertTrue(PreviewSample.retiredPhase(true, 2 * cycle - 1));
        assertFalse(PreviewSample.retiredPhase(true, 2 * cycle));
        // Util.getMillis() is free to be negative, and a preview must not blow up on it.
        assertTrue(PreviewSample.retiredPhase(true, -1));
    }

    @Test
    void theClockOverloadAgreesWithThePhase() {
        long retiredTime = PreviewSample.RETIRED_CYCLE_MILLIS;
        assertEquals(text(DisplayMode.ALL, DEFAULTS, true),
                NametagModel.plainText(
                        PreviewSample.segments(DisplayMode.ALL, DEFAULTS, true, retiredTime,
                                NametagStyle.DEFAULT)));
        assertEquals(text(DisplayMode.ALL, DEFAULTS, false),
                NametagModel.plainText(
                        PreviewSample.segments(DisplayMode.ALL, DEFAULTS, true, 0,
                                NametagStyle.DEFAULT)));
    }

    @Test
    void aMissingOrStaleSelectionStillPreviewsSomething() {
        Map<Source, String> stale = new HashMap<>();
        stale.put(Source.MCTIERS, "no-such-gamemode");
        stale.put(Source.SUBTIERS, null);

        String unknown = text(DisplayMode.MCTIERS_ONLY, stale, false);
        String missing = text(DisplayMode.SUBTIERS_ONLY, stale, false);
        assertEquals("[" + entry(Gamemodes.of(Source.MCTIERS).getFirst(), false) + "] ", unknown);
        assertEquals("[" + entry(Gamemodes.of(Source.SUBTIERS).getFirst(), false) + "] ", missing);
    }

    @Test
    void thePreviewIsNeverEmpty() {
        for (DisplayMode mode : DisplayMode.values()) {
            assertFalse(PreviewSample.resolve(mode, DEFAULTS, false).isEmpty(), mode.toString());
            assertFalse(PreviewSample.resolve(mode, Map.of(), true).isEmpty(), mode.toString());
        }
    }

    @Test
    void thePreviewIsDrawnInWhateverStyleTheScreenIsPendingOn() {
        // The point of the preview is that it answers to the appearance rows too, not
        // just the mode and gamemode ones.
        NametagStyle stripped = new NametagStyle(BadgePosition.AFTER, false, false);
        String shown = NametagModel.plainText(
                PreviewSample.segments(DisplayMode.MCTIERS_ONLY,
                        Map.of(Source.MCTIERS, "axe"), false, stripped));

        assertEquals(" HT1", shown);
        assertEquals("[" + entry(gamemode(Source.MCTIERS, "axe"), false) + "] ",
                text(DisplayMode.MCTIERS_ONLY, Map.of(Source.MCTIERS, "axe"), false));
    }

    @Test
    void theStyleSurvivesTheClockOverloadToo() {
        NametagStyle stripped = new NametagStyle(BadgePosition.AFTER, false, false);
        assertEquals(
                NametagModel.plainText(PreviewSample.segments(
                        DisplayMode.ALL, DEFAULTS, true, stripped)),
                NametagModel.plainText(PreviewSample.segments(
                        DisplayMode.ALL, DEFAULTS, true,
                        PreviewSample.RETIRED_CYCLE_MILLIS, stripped)));
    }

    @Test
    void everyFixedGamemodeIsARealGamemode() {
        PreviewSample.ALL_MODE_GAMEMODES.forEach((source, slug) ->
                assertTrue(Gamemodes.find(source, slug).isPresent(),
                        source + "/" + slug + " is not a real gamemode"));
        assertEquals(Source.values().length, PreviewSample.ALL_MODE_GAMEMODES.size());
    }
}
