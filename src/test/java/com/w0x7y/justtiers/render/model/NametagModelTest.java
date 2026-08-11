package com.w0x7y.justtiers.render.model;

import com.w0x7y.justtiers.resolve.ResolvedTier;
import com.w0x7y.justtiers.tier.Gamemode;
import com.w0x7y.justtiers.tier.Gamemodes;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;
import org.junit.jupiter.api.Test;

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
    void retiredTiersOverrideTheSiteColourWithLightRed() {
        List<Segment> segments = NametagModel.build(
                List.of(resolved(Source.MCTIERS, "vanilla", new Tier(1, true, true))));

        Segment tier = segments.stream().filter(s -> s.text().equals("RHT1")).findFirst().orElseThrow();
        assertEquals(NametagModel.RETIRED_COLOR, tier.color());
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
    void mixedActiveAndRetiredEntriesKeepIndependentColours() {
        List<Segment> segments = NametagModel.build(List.of(
                resolved(Source.MCTIERS, "axe", new Tier(1, true, true)),
                resolved(Source.NOVATIERS, "uhc", new Tier(4, true, false))));

        Segment retired = segments.stream().filter(s -> s.text().equals("RHT1")).findFirst().orElseThrow();
        Segment active = segments.stream().filter(s -> s.text().equals("HT4")).findFirst().orElseThrow();

        assertEquals(NametagModel.RETIRED_COLOR, retired.color());
        assertEquals(Source.NOVATIERS.color(), active.color());
    }
}
