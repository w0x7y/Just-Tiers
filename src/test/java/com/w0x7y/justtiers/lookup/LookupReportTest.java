package com.w0x7y.justtiers.lookup;

import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LookupReportTest {

    private static Tier ht(int level) { return new Tier(level, true, false); }
    private static Tier lt(int level) { return new Tier(level, false, false); }
    private static Tier retiredHt(int level) { return new Tier(level, true, true); }

    private static Map<Source, Optional<Map<String, Tier>>> answers() {
        return new EnumMap<>(Source.class);
    }

    private static LookupSection sectionFor(List<LookupSection> sections, Source source) {
        return sections.stream().filter(s -> s.source() == source).findFirst().orElseThrow();
    }

    @Test
    void everySiteGetsASectionInDeclarationOrder() {
        List<LookupSection> sections = LookupReport.build(answers());

        assertEquals(List.of(Source.values()),
                sections.stream().map(LookupSection::source).toList());
    }

    @Test
    void aRankedSiteListsEveryPlacementBestFirst() {
        var answers = answers();
        answers.put(Source.MCTIERS, Optional.of(Map.of(
                "axe", ht(3), "vanilla", ht(1), "sword", lt(1))));

        LookupSection section = sectionFor(LookupReport.build(answers), Source.MCTIERS);

        assertEquals(LookupSection.Status.RANKED, section.status());
        assertEquals(List.of("vanilla", "sword", "axe"),
                section.tiers().stream().map(t -> t.gamemode().slug()).toList());
    }

    @Test
    void aSiteThatAnsweredWithNothingIsUnrankedNotUnavailable() {
        var answers = answers();
        answers.put(Source.SUBTIERS, Optional.of(Map.of()));

        assertEquals(LookupSection.Status.UNRANKED,
                sectionFor(LookupReport.build(answers), Source.SUBTIERS).status());
    }

    @Test
    void aSiteThatDidNotAnswerIsUnavailableNotUnranked() {
        var answers = answers();
        answers.put(Source.NOVATIERS, Optional.empty());

        LookupSection section = sectionFor(LookupReport.build(answers), Source.NOVATIERS);
        assertEquals(LookupSection.Status.UNAVAILABLE, section.status());
        assertTrue(section.tiers().isEmpty());
    }

    @Test
    void aSiteMissingFromTheAnswersIsTreatedAsUnavailable() {
        // Nothing put an answer in for it at all, which can only mean it never reported.
        assertEquals(LookupSection.Status.UNAVAILABLE,
                sectionFor(LookupReport.build(answers()), Source.MCTIERS).status());
    }

    @Test
    void oneSiteBeingDownDoesNotAffectTheOthers() {
        var answers = answers();
        answers.put(Source.MCTIERS, Optional.of(Map.of("axe", ht(2))));
        answers.put(Source.SUBTIERS, Optional.empty());
        answers.put(Source.NOVATIERS, Optional.of(Map.of()));

        List<LookupSection> sections = LookupReport.build(answers);
        assertEquals(LookupSection.Status.RANKED, sectionFor(sections, Source.MCTIERS).status());
        assertEquals(LookupSection.Status.UNAVAILABLE, sectionFor(sections, Source.SUBTIERS).status());
        assertEquals(LookupSection.Status.UNRANKED, sectionFor(sections, Source.NOVATIERS).status());
    }

    @Test
    void retiredPlacementsAreListedRatherThanFilteredOut() {
        // A lookup is an inspection, not a nametag: showRetired shortens the tag, it does
        // not decide what a player has actually placed in.
        var answers = answers();
        answers.put(Source.MCTIERS, Optional.of(Map.of("axe", retiredHt(1))));

        LookupSection section = sectionFor(LookupReport.build(answers), Source.MCTIERS);
        assertEquals(LookupSection.Status.RANKED, section.status());
        assertEquals("RHT1", section.tiers().get(0).tier().label());
    }

    @Test
    void nothingRankedIsTrueOnlyWhenNoSiteHasAPlacement() {
        var empty = answers();
        empty.put(Source.MCTIERS, Optional.of(Map.of()));
        empty.put(Source.SUBTIERS, Optional.empty());
        assertTrue(LookupReport.nothingRanked(LookupReport.build(empty)));

        var ranked = answers();
        ranked.put(Source.SUBTIERS, Optional.of(Map.of("bow", lt(4))));
        assertFalse(LookupReport.nothingRanked(LookupReport.build(ranked)));
    }

    @Test
    void sectionsAreImmutableOnceBuilt() {
        var answers = answers();
        answers.put(Source.MCTIERS, Optional.of(Map.of("axe", ht(2))));
        LookupSection section = sectionFor(LookupReport.build(answers), Source.MCTIERS);

        assertThrows(UnsupportedOperationException.class, () -> section.tiers().clear());
    }
}
