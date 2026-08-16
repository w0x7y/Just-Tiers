package com.w0x7y.justtiers.scan;

import com.w0x7y.justtiers.api.PlayerRef;
import com.w0x7y.justtiers.lookup.LookupReport;
import com.w0x7y.justtiers.lookup.LookupSection;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanReportTest {

    private static PlayerRef player(String name) {
        return new PlayerRef(name, UUID.nameUUIDFromBytes(name.getBytes()));
    }

    private static Map<Source, LookupSection> answered(Source source, Map<String, Tier> tiers) {
        Map<Source, LookupSection> sections = new EnumMap<>(Source.class);
        sections.put(source, LookupReport.section(source, Optional.of(tiers)));
        return sections;
    }

    private static ScanRow row(String name, Source source, Map<String, Tier> tiers) {
        return ScanRow.of(player(name), answered(source, tiers));
    }

    private static List<String> names(List<ScanRow> rows) {
        return rows.stream().map(r -> r.player().name()).toList();
    }

    @Test
    void aRowScoresTheSectionsItHas() {
        ScanRow row = row("Notch", Source.MCTIERS,
                Map.of("vanilla", new Tier(1, true, false)));
        assertEquals(10, row.points());
    }

    @Test
    void aRowIsIncompleteUntilEverySiteHasAnswered() {
        ScanRow partial = row("Notch", Source.MCTIERS, Map.of());
        assertFalse(partial.complete());

        Map<Source, LookupSection> all = new EnumMap<>(Source.class);
        for (Source source : Source.ALL) {
            all.put(source, LookupReport.section(source, Optional.of(Map.of())));
        }
        assertTrue(ScanRow.of(player("Notch"), all).complete());
    }

    @Test
    void rowsSortByPointsDescending() {
        List<ScanRow> sorted = ScanReport.sorted(List.of(
                row("Low", Source.MCTIERS, Map.of("vanilla", new Tier(5, false, false))),
                row("High", Source.MCTIERS, Map.of("vanilla", new Tier(1, true, false))),
                row("Mid", Source.MCTIERS, Map.of("vanilla", new Tier(3, true, false)))));

        assertEquals(List.of("High", "Mid", "Low"), names(sorted));
    }

    @Test
    void equalPointsBreakByNameIgnoringCase() {
        Map<String, Tier> same = Map.of("vanilla", new Tier(1, true, false));
        List<ScanRow> sorted = ScanReport.sorted(List.of(
                row("charlie", Source.MCTIERS, same),
                row("Alice", Source.MCTIERS, same),
                row("bob", Source.MCTIERS, same)));

        assertEquals(List.of("Alice", "bob", "charlie"), names(sorted));
    }

    @Test
    void anIncompleteRowSortsOnThePointsItHasSoFar() {
        // Nova has answered for both; MCTiers has answered for neither. The list is
        // provisional, and must still be ordered by what is known.
        ScanRow waiting = row("Waiting", Source.NOVATIERS,
                Map.of("vanilla", new Tier(1, true, false)));
        ScanRow ahead = row("Ahead", Source.NOVATIERS,
                Map.of("vanilla", new Tier(1, true, false), "smp", new Tier(1, true, false)));

        assertEquals(List.of("Ahead", "Waiting"),
                names(ScanReport.sorted(List.of(waiting, ahead))));
    }

    @Test
    void resortingAfterAnAnswerMatchesSortingThatStateFromScratch() {
        ScanRow first = row("First", Source.NOVATIERS,
                Map.of("vanilla", new Tier(4, true, false)));
        ScanRow second = row("Second", Source.NOVATIERS,
                Map.of("vanilla", new Tier(2, true, false)));
        assertEquals(List.of("Second", "First"), names(ScanReport.sorted(List.of(first, second))));

        // MCTiers now answers for First, taking it past Second.
        Map<Source, LookupSection> grown = new EnumMap<>(Source.class);
        grown.putAll(first.sections());
        grown.put(Source.MCTIERS, LookupReport.section(Source.MCTIERS,
                Optional.of(Map.of("vanilla", new Tier(1, true, false)))));
        ScanRow updated = ScanRow.of(first.player(), grown);

        assertEquals(List.of("First", "Second"),
                names(ScanReport.sorted(List.of(updated, second))));
    }

    @Test
    void retiredPlacementsNeverReachARowsScore() {
        // The session strips them before building a row; a row that is handed one anyway
        // must still refuse to score it.
        ScanRow row = row("Retired", Source.MCTIERS,
                Map.of("vanilla", new Tier(1, true, true)));
        assertEquals(0, row.points());
    }

    @Test
    void sortedIsImmutable() {
        List<ScanRow> sorted = ScanReport.sorted(List.of(
                row("Notch", Source.MCTIERS, Map.of())));
        assertThrows(UnsupportedOperationException.class, () -> sorted.add(null));
    }
}
