package com.w0x7y.justtiers.preview;

import com.w0x7y.justtiers.render.model.Badge;
import com.w0x7y.justtiers.render.model.BadgePosition;
import com.w0x7y.justtiers.render.model.NametagStyle;
import com.w0x7y.justtiers.tier.Gamemode;
import com.w0x7y.justtiers.tier.Gamemodes;
import com.w0x7y.justtiers.tier.Source;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PreviewSampleTest {

    private static final Map<Source, String> DEFAULTS = Map.of(
            Source.PVPTIERS, "crystal",
            Source.SUBTIERS, "elytra",
            Source.NOVATIERS, "vanilla");

    /** What the sample tiers draw as, through the same Badge the config screen uses. */
    private String text(Set<Source> sites, Map<Source, String> selected, boolean retired) {
        return Badge.of(PreviewSample.resolve(sites, selected, retired), NametagStyle.DEFAULT)
                .plainText();
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
                String shown = text(Set.of(source), Map.of(source, gamemode.slug()), false);
                assertEquals("[" + entry(gamemode, false) + "] ", shown,
                        source + "/" + gamemode.slug() + " should preview as HT1");
            }
        }
    }

    @Test
    void allModeShowsTheFixedTrio() {
        String expected = "[" + entry(gamemode(Source.PVPTIERS, "crystal"), false)
                + " " + entry(gamemode(Source.PVPHQ, "vanilla"), false)
                + " " + entry(gamemode(Source.SUBTIERS, "minecart"), false)
                + " " + entry(gamemode(Source.NOVATIERS, "spearmace"), false)
                + "] ";
        assertEquals(expected, text(Set.copyOf(Source.ALL), DEFAULTS, false));
    }

    @Test
    void allModeIgnoresTheGamemodeSelections() {
        Map<Source, String> other = Map.of(
                Source.PVPTIERS, "axe",
                Source.SUBTIERS, "bow",
                Source.NOVATIERS, "spleef");
        assertEquals(text(Set.copyOf(Source.ALL), DEFAULTS, false),
                text(Set.copyOf(Source.ALL), other, false));
    }

    @Test
    void theRetiredPhaseMarksEveryEntry() {
        String expected = "[" + entry(gamemode(Source.PVPTIERS, "crystal"), true)
                + " " + entry(gamemode(Source.PVPHQ, "vanilla"), true)
                + " " + entry(gamemode(Source.SUBTIERS, "minecart"), true)
                + " " + entry(gamemode(Source.NOVATIERS, "spearmace"), true)
                + "] ";
        assertEquals(expected, text(Set.copyOf(Source.ALL), DEFAULTS, true));
        assertEquals("[" + entry(gamemode(Source.PVPTIERS, "axe"), true) + "] ",
                text(Set.of(Source.PVPTIERS), Map.of(Source.PVPTIERS, "axe"), true));
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
    void thePreviewPicksItsPhaseFromTheClock() {
        long retiredTime = PreviewSample.RETIRED_CYCLE_MILLIS;
        assertEquals(text(Set.copyOf(Source.ALL), DEFAULTS, true),
                Badge.preview(Set.copyOf(Source.ALL), DEFAULTS, true, retiredTime,
                        NametagStyle.DEFAULT).plainText());
        assertEquals(text(Set.copyOf(Source.ALL), DEFAULTS, false),
                Badge.preview(Set.copyOf(Source.ALL), DEFAULTS, true, 0,
                        NametagStyle.DEFAULT).plainText());
    }

    @Test
    void aMissingOrStaleSelectionStillPreviewsSomething() {
        Map<Source, String> stale = new HashMap<>();
        stale.put(Source.PVPTIERS, "no-such-gamemode");
        stale.put(Source.SUBTIERS, null);

        String unknown = text(Set.of(Source.PVPTIERS), stale, false);
        String missing = text(Set.of(Source.SUBTIERS), stale, false);
        assertEquals("[" + entry(Gamemodes.of(Source.PVPTIERS).getFirst(), false) + "] ", unknown);
        assertEquals("[" + entry(Gamemodes.of(Source.SUBTIERS).getFirst(), false) + "] ", missing);
    }

    @Test
    void thePreviewIsNeverEmpty() {
        for (Set<Source> sites : java.util.List.of(Set.of(Source.PVPTIERS), Set.of(Source.PVPHQ), Set.of(Source.SUBTIERS), Set.of(Source.NOVATIERS), Set.copyOf(Source.ALL))) {
            assertFalse(PreviewSample.resolve(sites, DEFAULTS, false).isEmpty(), sites.toString());
            assertFalse(PreviewSample.resolve(sites, Map.of(), true).isEmpty(), sites.toString());
        }
    }

    @Test
    void thePreviewIsDrawnInWhateverStyleTheScreenIsPendingOn() {
        // The point of the preview is that it answers to the appearance rows too, not
        // just the sites and gamemode ones.
        NametagStyle stripped = new NametagStyle(BadgePosition.AFTER, false, false);
        String shown = Badge.of(PreviewSample.resolve(Set.of(Source.PVPTIERS),
                Map.of(Source.PVPTIERS, "axe"), false), stripped).plainText();

        assertEquals(" HT1", shown);
        assertEquals("[" + entry(gamemode(Source.PVPTIERS, "axe"), false) + "] ",
                text(Set.of(Source.PVPTIERS), Map.of(Source.PVPTIERS, "axe"), false));
    }

    @Test
    void theStyleSurvivesTheClockToo() {
        NametagStyle stripped = new NametagStyle(BadgePosition.AFTER, false, false);
        assertEquals(
                Badge.of(PreviewSample.resolve(Set.copyOf(Source.ALL), DEFAULTS, true), stripped)
                        .plainText(),
                Badge.preview(Set.copyOf(Source.ALL), DEFAULTS, true,
                        PreviewSample.RETIRED_CYCLE_MILLIS, stripped).plainText());
    }

    @Test
    void everyFixedGamemodeIsARealGamemode() {
        PreviewSample.MULTI_SITE_GAMEMODES.forEach((source, slug) ->
                assertTrue(Gamemodes.find(source, slug).isPresent(),
                        source + "/" + slug + " is not a real gamemode"));
        assertEquals(Source.values().length, PreviewSample.MULTI_SITE_GAMEMODES.size());
    }
}
