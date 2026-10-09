package com.w0x7y.justtiers.render.model;

import com.w0x7y.justtiers.preview.PreviewSample;
import com.w0x7y.justtiers.tier.Gamemode;
import com.w0x7y.justtiers.tier.Gamemodes;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The nametag path end to end, without Minecraft: cache answers in, finished badge out.
 * This is the run that used to live inside {@code NametagRenderer.decorate} and could
 * only be exercised by launching the game.
 */
class BadgeTest {

    private static final Map<Source, String> SELECTED = Map.of(
            Source.PVPTIERS, "crystal",
            Source.SUBTIERS, "bow",
            Source.NOVATIERS, "spleef");

    private static Tier ht(int level) {
        return new Tier(level, true, false);
    }

    private static Tier retiredHt(int level) {
        return new Tier(level, true, true);
    }

    private static Gamemode gamemode(Source source, String slug) {
        return Gamemodes.find(source, slug).orElseThrow();
    }

    private static Badge badge(Set<Source> sites, Map<Source, Map<String, Tier>> answers,
                               boolean showRetired) {
        return Badge.forPlayer(sites, answers, SELECTED, showRetired, NametagStyle.DEFAULT);
    }

    // --- what the nametag shows ---

    @Test
    void aRankedPlayerGetsTheirSelectedGamemode() {
        Badge badge = badge(Set.of(Source.PVPTIERS),
                Map.of(Source.PVPTIERS, Map.of("crystal", ht(2), "axe", ht(1))), true);

        assertEquals("[" + gamemode(Source.PVPTIERS, "crystal").icon() + "HT2] ",
                badge.plainText());
        assertEquals(BadgePosition.BEFORE, badge.position());
    }

    @Test
    void allModeShowsTheBestFromEverySiteThatAnswered() {
        Map<Source, Map<String, Tier>> answers = new EnumMap<>(Source.class);
        answers.put(Source.PVPTIERS, Map.of("crystal", ht(3)));
        answers.put(Source.NOVATIERS, Map.of("spleef", ht(1)));

        assertEquals("[" + gamemode(Source.PVPTIERS, "crystal").icon() + "HT3 "
                        + gamemode(Source.NOVATIERS, "spleef").icon() + "HT1] ",
                badge(Set.copyOf(Source.ALL), answers, true).plainText());
    }

    /**
     * The reason a badge is built from whatever has arrived rather than from a complete
     * set: a site still in flight is simply absent, and the tag fills in when it lands.
     */
    @Test
    void oneSiteAnsweringIsEnoughToDrawABadge() {
        Badge badge = badge(Set.copyOf(Source.ALL), Map.of(Source.SUBTIERS, Map.of("bow", ht(4))), true);

        assertFalse(badge.isEmpty());
        assertEquals("[" + gamemode(Source.SUBTIERS, "bow").icon() + "HT4] ", badge.plainText());
    }

    @Test
    void hidingRetiredTiersFallsBackToTheBestActiveOne() {
        Map<String, Tier> tiers = Map.of("crystal", retiredHt(1), "axe", ht(4));

        assertEquals("[" + gamemode(Source.PVPTIERS, "crystal").icon() + "RHT1] ",
                badge(Set.of(Source.PVPTIERS), Map.of(Source.PVPTIERS, tiers), true).plainText());
        assertEquals("[" + gamemode(Source.PVPTIERS, "axe").icon() + "HT4] ",
                badge(Set.of(Source.PVPTIERS), Map.of(Source.PVPTIERS, tiers), false).plainText());
    }

    // --- when there is nothing to draw ---

    @Test
    void noAnswersMeansNoBadge() {
        assertTrue(badge(Set.copyOf(Source.ALL), Map.of(), true).isEmpty());
        assertTrue(Badge.forPlayer(Set.copyOf(Source.ALL), null, SELECTED, true,
                NametagStyle.DEFAULT).isEmpty());
    }

    @Test
    void aPlayerWhoIsOnlyEverRetiredDropsOutWhenRetiredIsHidden() {
        Badge badge = badge(Set.of(Source.PVPTIERS),
                Map.of(Source.PVPTIERS, Map.of("crystal", retiredHt(2))), false);

        assertTrue(badge.isEmpty());
        assertEquals("", badge.plainText());
    }

    @Test
    void noTiersMeansTheSharedEmptyBadge() {
        assertSame(Badge.NONE, Badge.of(List.of(), NametagStyle.DEFAULT));
    }

    // --- the side travels with the segments ---

    /**
     * The invariant the callers used to have to hold themselves: they read the style
     * once to lay the badge out and again to pick the side, and nothing stopped the two
     * from disagreeing.
     */
    @Test
    void theSideIsTheOneTheBadgeWasLaidOutFor() {
        NametagStyle after = new NametagStyle(BadgePosition.AFTER, false, true);
        Badge badge = styled(after);

        assertEquals(BadgePosition.AFTER, badge.position());
        // Laid out for AFTER, so the badge carries its space on the left instead.
        assertEquals(" [HT2]", badge.plainText());
    }

    @Test
    void aBadgeBuiltForBeforeCarriesItsSpaceOnTheRight() {
        Badge badge = styled(new NametagStyle(BadgePosition.BEFORE, false, true));

        assertEquals(BadgePosition.BEFORE, badge.position());
        assertEquals("[HT2] ", badge.plainText());
    }

    /** The same one placement, laid out under whatever style is being tested. */
    private static Badge styled(NametagStyle style) {
        return Badge.forPlayer(Set.of(Source.PVPTIERS),
                Map.of(Source.PVPTIERS, Map.of("crystal", ht(2))), SELECTED, true, style);
    }

    @Test
    void aMissingStyleFallsBackToTheDefaultRatherThanFailing() {
        assertEquals(styled(NametagStyle.DEFAULT).plainText(),
                Badge.forPlayer(Set.of(Source.PVPTIERS),
                        Map.of(Source.PVPTIERS, Map.of("crystal", ht(2))),
                        SELECTED, true, null).plainText());
    }

    // --- colors ---

    @Test
    void theStylesSiteColorsReachTheBadge() {
        Map<Source, Integer> colors = new EnumMap<>(Source.class);
        colors.put(Source.PVPTIERS, 0x123456);
        NametagStyle style = new NametagStyle(BadgePosition.BEFORE, false, false, colors);

        // Brackets are off, so the only thing left besides the tier label is the space
        // the badge always carries between itself and the name.
        Segment label = styled(style).segments().stream()
                .filter(segment -> segment.text().equals("HT2"))
                .findFirst().orElseThrow();
        assertEquals(0x123456, label.color());
    }

    @Test
    void recoloringLeavesIconsAsIcons() {
        Badge badge = styled(NametagStyle.DEFAULT).recolor(color -> 0xFF0000);

        assertFalse(badge.isEmpty());
        for (Segment segment : badge.segments()) {
            assertEquals(0xFF0000, segment.color());
        }
        // An icon drawn in the default font is a missing-glyph box, so the flag has to
        // survive a recolor.
        assertTrue(badge.segments().stream().anyMatch(Segment::icon));
    }

    @Test
    void recoloringAnEmptyBadgeIsANoOp() {
        assertSame(Badge.NONE, Badge.NONE.recolor(color -> 0xFF0000));
    }

    @Test
    void recoloringKeepsTheSideAndTheText() {
        Badge badge = styled(new NametagStyle(BadgePosition.AFTER, true, true));
        Badge dimmed = badge.recolor(color -> color / 2);

        assertEquals(badge.position(), dimmed.position());
        assertEquals(badge.plainText(), dimmed.plainText());
    }

    // --- the preview ---

    @Test
    void thePreviewIsAlwaysTierOne() {
        assertEquals("[" + gamemode(Source.PVPTIERS, "crystal").icon() + "HT1] ",
                Badge.preview(Set.of(Source.PVPTIERS), SELECTED, false, 0L,
                        NametagStyle.DEFAULT).plainText());
    }

    @Test
    void thePreviewCyclesThroughRetiredOnlyWhenRetiredIsShown() {
        long retiredTime = PreviewSample.RETIRED_CYCLE_MILLIS;

        assertTrue(Badge.preview(Set.of(Source.PVPTIERS), SELECTED, true, retiredTime,
                NametagStyle.DEFAULT).plainText().contains("RHT1"));
        assertFalse(Badge.preview(Set.of(Source.PVPTIERS), SELECTED, false, retiredTime,
                NametagStyle.DEFAULT).plainText().contains("RHT1"));
    }

    @Test
    void thePreviewIsNeverEmptyInAnyMode() {
        for (Set<Source> sites : java.util.List.of(Set.of(Source.PVPTIERS), Set.of(Source.PVPHQ), Set.of(Source.SUBTIERS), Set.of(Source.NOVATIERS), Set.copyOf(Source.ALL))) {
            assertFalse(Badge.preview(sites, Map.of(), true, 0L, NametagStyle.DEFAULT).isEmpty(),
                    sites.toString());
        }
    }

    // --- the sites a sites reads ---

    @Test
    void noEnabledSitesProducesNoBadgeOrPreview() {
        assertTrue(Badge.preview(Set.of(), SELECTED, true, 0L, NametagStyle.DEFAULT).isEmpty());
        assertTrue(badge(Set.of(), Map.of(Source.PVPTIERS, Map.of("axe", ht(1))), true).isEmpty());
    }

    /** A tier from a site the sites does not read must not reach the badge. */
    @Test
    void aSingleSiteModeIgnoresTheOtherSitesAnswers() {
        Map<Source, Map<String, Tier>> answers = new EnumMap<>(Source.class);
        answers.put(Source.PVPTIERS, Map.of("crystal", ht(2)));
        answers.put(Source.NOVATIERS, Map.of("spleef", ht(1)));

        assertEquals("[" + gamemode(Source.PVPTIERS, "crystal").icon() + "HT2] ",
                badge(Set.of(Source.PVPTIERS), answers, true).plainText());
    }
}
