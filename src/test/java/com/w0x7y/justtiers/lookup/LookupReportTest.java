package com.w0x7y.justtiers.lookup;

import com.w0x7y.justtiers.tier.Gamemode;
import com.w0x7y.justtiers.tier.Gamemodes;
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

    private static List<String> slugs(LookupSection section) {
        return section.cells().stream().map(cell -> cell.gamemode().slug()).toList();
    }

    private static Optional<Tier> tierOf(LookupSection section, String slug) {
        return section.cells().stream()
                .filter(cell -> cell.gamemode().slug().equals(slug))
                .findFirst().orElseThrow().tier();
    }

    @Test
    void everySiteGetsASectionInDeclarationOrder() {
        List<LookupSection> sections = LookupReport.build(answers());

        assertEquals(List.of(Source.values()),
                sections.stream().map(LookupSection::source).toList());
    }

    @Test
    void aRankedSiteGetsOneCellPerGamemodeInSiteOrder() {
        // The screen draws a fixed row of columns, so the cells must line up with the
        // site's own gamemode order however few placements came back.
        var answers = answers();
        answers.put(Source.MCTIERS, Optional.of(Map.of(
                "axe", ht(3), "vanilla", ht(1), "sword", lt(1))));

        LookupSection section = sectionFor(LookupReport.build(answers), Source.MCTIERS);

        assertEquals(LookupSection.Status.RANKED, section.status());
        assertEquals(Gamemodes.of(Source.MCTIERS).stream().map(Gamemode::slug).toList(),
                slugs(section));
    }

    @Test
    void aGamemodeThePlayerHasNotPlacedInHasAnEmptyCell() {
        var answers = answers();
        answers.put(Source.MCTIERS, Optional.of(Map.of("axe", ht(3))));

        LookupSection section = sectionFor(LookupReport.build(answers), Source.MCTIERS);

        assertEquals(Optional.of(ht(3)), tierOf(section, "axe"));
        assertTrue(tierOf(section, "sword").isEmpty(), "sword was never placed in");
    }

    @Test
    void aGamemodeTheSiteAddedAfterThisBuildIsIgnored() {
        // Same rule TierResolver.rankAll follows: a slug we have no icon or name for is
        // skipped rather than guessed at, so it must not become a nameless column.
        var answers = answers();
        answers.put(Source.MCTIERS, Optional.of(Map.of("trident_but_on_fire", ht(2))));

        LookupSection section = sectionFor(LookupReport.build(answers), Source.MCTIERS);

        assertEquals(Gamemodes.of(Source.MCTIERS).size(), section.cells().size());
        assertFalse(slugs(section).contains("trident_but_on_fire"));
    }

    @Test
    void aSiteWithOnlyUnknownGamemodesIsUnranked() {
        var answers = answers();
        answers.put(Source.MCTIERS, Optional.of(Map.of("trident_but_on_fire", ht(2))));

        assertEquals(LookupSection.Status.UNRANKED,
                sectionFor(LookupReport.build(answers), Source.MCTIERS).status());
    }

    @Test
    void anUnrankedSiteStillGetsItsFullRowOfEmptyCells() {
        // The row is drawn as dashes rather than left out: "we asked, nothing here".
        var answers = answers();
        answers.put(Source.SUBTIERS, Optional.of(Map.of()));

        LookupSection section = sectionFor(LookupReport.build(answers), Source.SUBTIERS);

        assertEquals(LookupSection.Status.UNRANKED, section.status());
        assertEquals(Gamemodes.of(Source.SUBTIERS).size(), section.cells().size());
        assertTrue(section.cells().stream().allMatch(cell -> cell.tier().isEmpty()));
    }

    @Test
    void aSiteThatDidNotAnswerIsUnavailableNotUnranked() {
        var answers = answers();
        answers.put(Source.NOVATIERS, Optional.empty());

        LookupSection section = sectionFor(LookupReport.build(answers), Source.NOVATIERS);
        assertEquals(LookupSection.Status.UNAVAILABLE, section.status());
    }

    @Test
    void anUnavailableSiteHasNoCellsAtAll() {
        // Dashes would claim the player is not ranked in any of them; the site said
        // nothing, so the row has nothing to draw and says so instead.
        var answers = answers();
        answers.put(Source.NOVATIERS, Optional.empty());

        assertTrue(sectionFor(LookupReport.build(answers), Source.NOVATIERS).cells().isEmpty());
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
        assertEquals("RHT1", tierOf(section, "axe").orElseThrow().label());
    }

    @Test
    void oneSitesSectionCanBeBuiltOnItsOwnAsItsAnswerArrives() {
        // The screen fills a row in the moment its site answers rather than waiting for
        // the slowest one, so a section has to be buildable from a single answer.
        var answers = answers();
        answers.put(Source.MCTIERS, Optional.of(Map.of("axe", ht(2))));

        assertEquals(sectionFor(LookupReport.build(answers), Source.MCTIERS),
                LookupReport.section(Source.MCTIERS, Optional.of(Map.of("axe", ht(2)))));
    }

    @Test
    void aSectionBuiltFromNoAnswerAtAllIsUnavailable() {
        assertEquals(LookupSection.Status.UNAVAILABLE,
                LookupReport.section(Source.NOVATIERS, Optional.empty()).status());
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
    void anySiteAnsweredIsFalseWhenEverySiteWasUnavailable() {
        // Three "site unavailable" rows are no evidence at all, and "not ranked anywhere"
        // on top of them would be the exact confusion UNAVAILABLE exists to prevent.
        var down = answers();
        for (Source source : Source.values()) {
            down.put(source, Optional.empty());
        }
        List<LookupSection> sections = LookupReport.build(down);

        assertTrue(LookupReport.nothingRanked(sections));
        assertFalse(LookupReport.anySiteAnswered(sections));
    }

    @Test
    void aSiteAnsweringUnrankedCountsAsAnAnswer() {
        var mixed = answers();
        mixed.put(Source.MCTIERS, Optional.of(Map.of()));
        mixed.put(Source.SUBTIERS, Optional.empty());

        List<LookupSection> sections = LookupReport.build(mixed);
        assertTrue(LookupReport.nothingRanked(sections));
        assertTrue(LookupReport.anySiteAnswered(sections),
                "one site saying 'never placed' is enough to say the player is unranked");
    }

    @Test
    void aRankedSiteIsAnAnswerToo() {
        var ranked = answers();
        ranked.put(Source.NOVATIERS, Optional.of(Map.of("sword", ht(2))));

        assertTrue(LookupReport.anySiteAnswered(LookupReport.build(ranked)));
    }

    @Test
    void sectionsAreImmutableOnceBuilt() {
        var answers = answers();
        answers.put(Source.MCTIERS, Optional.of(Map.of("axe", ht(2))));
        LookupSection section = sectionFor(LookupReport.build(answers), Source.MCTIERS);

        assertThrows(UnsupportedOperationException.class, () -> section.cells().clear());
    }
}
