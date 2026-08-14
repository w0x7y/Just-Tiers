package com.w0x7y.justtiers.render.model;

import com.w0x7y.justtiers.resolve.ResolvedTier;
import com.w0x7y.justtiers.tier.Gamemode;
import com.w0x7y.justtiers.tier.Gamemodes;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NametagModelTest {

    private static Gamemode mode(Source source, String slug) {
        return Gamemodes.find(source, slug).orElseThrow();
    }

    private static ResolvedTier resolved(Source source, String slug, Tier tier) {
        return new ResolvedTier(mode(source, slug), tier);
    }

    @Test
    void emptyInputProducesNoSegments() {
        assertTrue(NametagModel.build(List.of()).isEmpty());
    }

    @Test
    void singleTierIsWrappedInBrackets() {
        List<Segment> segments = NametagModel.build(
                List.of(resolved(Source.MCTIERS, "vanilla", new Tier(2, true, false))));

        assertEquals("[\uE108HT2] ", NametagModel.plainText(segments));
    }

    @Test
    void tierTextTakesTheSiteColour() {
        List<Segment> segments = NametagModel.build(
                List.of(resolved(Source.MCTIERS, "vanilla", new Tier(2, true, false))));

        Segment tier = segments.stream().filter(s -> s.text().equals("HT2")).findFirst().orElseThrow();
        assertEquals(0xFFFF55, tier.color());
    }

    @Test
    void eachSiteUsesItsOwnColour() {
        assertEquals(0xFFFF55, colourOf(Source.MCTIERS, "vanilla"));
        assertEquals(0x55FFFF, colourOf(Source.SUBTIERS, "bow"));
        assertEquals(0xAA55FF, colourOf(Source.NOVATIERS, "spleef"));
    }

    private int colourOf(Source source, String slug) {
        List<Segment> segments = NametagModel.build(
                List.of(resolved(source, slug, new Tier(3, true, false))));
        return segments.stream().filter(s -> s.text().equals("HT3")).findFirst().orElseThrow().color();
    }

    @Test
    void retiredTiersKeepTheirSiteColourAndAreMarkedOnlyByTheRPrefix() {
        List<Segment> segments = NametagModel.build(
                List.of(resolved(Source.MCTIERS, "vanilla", new Tier(1, true, true))));

        Segment tier = segments.stream().filter(s -> s.text().equals("RHT1")).findFirst().orElseThrow();
        assertEquals(Source.MCTIERS.color(), tier.color());
        assertEquals("[\uE108RHT1] ", NametagModel.plainText(segments));
    }

    @Test
    void iconSegmentsAreWhiteSoTheArtworkIsNotTinted() {
        List<Segment> segments = NametagModel.build(
                List.of(resolved(Source.MCTIERS, "vanilla", new Tier(2, true, false))));

        Segment icon = segments.stream()
                .filter(s -> s.text().equals("\uE108")).findFirst().orElseThrow();
        assertEquals(0xFFFFFF, icon.color());
    }

    @Test
    void bracketsUseTheBracketColour() {
        List<Segment> segments = NametagModel.build(
                List.of(resolved(Source.MCTIERS, "vanilla", new Tier(2, true, false))));

        assertEquals(NametagModel.BRACKET_COLOR, segments.get(0).color());
        assertEquals("[", segments.get(0).text());
        assertEquals(NametagModel.BRACKET_COLOR, segments.get(segments.size() - 1).color());
        assertEquals("] ", segments.get(segments.size() - 1).text());
    }

    @Test
    void multipleEntriesAreSeparatedBySingleSpaces() {
        List<Segment> segments = NametagModel.build(List.of(
                resolved(Source.MCTIERS, "axe", new Tier(2, true, false)),
                resolved(Source.SUBTIERS, "bow", new Tier(3, false, false)),
                resolved(Source.NOVATIERS, "uhc", new Tier(4, true, false))));

        assertEquals("[\uE101HT2 \uE202LT3 \uE30BHT4] ", NametagModel.plainText(segments));
    }

    @Test
    void retiredAndActiveEntriesAreEachColouredBySite() {
        List<Segment> segments = NametagModel.build(List.of(
                resolved(Source.MCTIERS, "axe", new Tier(1, true, true)),
                resolved(Source.NOVATIERS, "uhc", new Tier(4, true, false))));

        Segment retired = segments.stream().filter(s -> s.text().equals("RHT1")).findFirst().orElseThrow();
        Segment active = segments.stream().filter(s -> s.text().equals("HT4")).findFirst().orElseThrow();

        assertEquals(Source.MCTIERS.color(), retired.color());
        assertEquals(Source.NOVATIERS.color(), active.color());
    }

    // --- style ---

    private static final List<ResolvedTier> PAIR = List.of(
            resolved(Source.MCTIERS, "axe", new Tier(2, true, false)),
            resolved(Source.SUBTIERS, "bow", new Tier(3, false, false)));

    /** PAIR's entries, spelled the way the badge spells them, with icons or without. */
    private static String entries(boolean icons) {
        String axe = icons ? String.valueOf(mode(Source.MCTIERS, "axe").icon()) : "";
        String bow = icons ? String.valueOf(mode(Source.SUBTIERS, "bow").icon()) : "";
        return axe + "HT2 " + bow + "LT3";
    }

    private static String text(NametagStyle style) {
        return NametagModel.plainText(NametagModel.build(PAIR, style));
    }

    @Test
    void theDefaultStyleIsWhatTheSingleArgumentBuildDraws() {
        assertEquals(NametagModel.plainText(NametagModel.build(PAIR)),
                text(NametagStyle.DEFAULT));
        assertEquals("[" + entries(true) + "] ", text(NametagStyle.DEFAULT));
    }

    @Test
    void anAfterBadgeCarriesItsSpaceOnTheLeadingSide() {
        assertEquals(" [" + entries(true) + "]",
                text(new NametagStyle(BadgePosition.AFTER, true, true)));
    }

    @Test
    void iconsCanBeTurnedOffLeavingColourToTellTheSitesApart() {
        NametagStyle style = new NametagStyle(BadgePosition.BEFORE, false, true);
        assertEquals("[" + entries(false) + "] ", text(style));

        // Nothing else distinguishes the two sites once the glyphs are gone, so the
        // per-site colours have to survive.
        List<Segment> segments = NametagModel.build(PAIR, style);
        assertEquals(Source.MCTIERS.color(), colourOfText(segments, "HT2"));
        assertEquals(Source.SUBTIERS.color(), colourOfText(segments, "LT3"));
    }

    private static int colourOfText(List<Segment> segments, String text) {
        return segments.stream().filter(s -> s.text().equals(text))
                .findFirst().orElseThrow().color();
    }

    @Test
    void bracketsCanBeTurnedOffOnEitherSide() {
        assertEquals(entries(true) + " ",
                text(new NametagStyle(BadgePosition.BEFORE, true, false)));
        assertEquals(" " + entries(true),
                text(new NametagStyle(BadgePosition.AFTER, true, false)));
    }

    @Test
    void theStrippedDownBadgeIsStillSeparatedFromTheName() {
        // Nothing but the tiers left: the single space to the name has to survive, or
        // the badge runs straight into it.
        assertEquals(entries(false) + " ",
                text(new NametagStyle(BadgePosition.BEFORE, false, false)));
        assertEquals(" " + entries(false),
                text(new NametagStyle(BadgePosition.AFTER, false, false)));
    }

    @Test
    void theBadgeIsNeverPaddedWithAnEmptySegment() {
        for (NametagStyle style : everyStyle()) {
            assertTrue(NametagModel.build(PAIR, style).stream()
                            .noneMatch(segment -> segment.text().isEmpty()),
                    style.toString());
        }
    }

    @Test
    void everyStyleStillDrawsNothingForAPlayerWithNoTiers() {
        for (NametagStyle style : everyStyle()) {
            assertTrue(NametagModel.build(List.of(), style).isEmpty(), style.toString());
        }
    }

    private static List<NametagStyle> everyStyle() {
        List<NametagStyle> styles = new ArrayList<>();
        for (BadgePosition position : BadgePosition.values()) {
            for (boolean icons : new boolean[]{true, false}) {
                for (boolean brackets : new boolean[]{true, false}) {
                    styles.add(new NametagStyle(position, icons, brackets));
                }
            }
        }
        return styles;
    }

    @Test
    void aNullPositionFallsBackToBeforeRatherThanBlowingUp() {
        assertEquals(BadgePosition.BEFORE, new NametagStyle(null, true, true).position());
    }

    // --- entries ---

    @Test
    void entriesAreTheBadgeWithoutItsWrappingOrItsSpacingToTheName() {
        assertEquals(entries(true), NametagModel.plainText(NametagModel.entries(PAIR, true)));
        assertEquals(entries(false), NametagModel.plainText(NametagModel.entries(PAIR, false)));
    }

    @Test
    void entriesOfNothingIsNothing() {
        assertTrue(NametagModel.entries(List.of(), true).isEmpty());
        assertTrue(NametagModel.entries(null, true).isEmpty());
    }
}
