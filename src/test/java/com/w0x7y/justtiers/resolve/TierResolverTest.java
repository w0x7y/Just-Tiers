package com.w0x7y.justtiers.resolve;

import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TierResolverTest {

    private static Tier ht(int level) { return new Tier(level, true, false); }
    private static Tier lt(int level) { return new Tier(level, false, false); }
    private static Tier retiredHt(int level) { return new Tier(level, true, true); }

    private static final Map<Source, String> SELECTED = Map.of(
            Source.MCTIERS, "vanilla",
            Source.SUBTIERS, "bow",
            Source.NOVATIERS, "spleef");

    // --- single-site modes ---

    @Test
    void showsTheSelectedGamemodeWhenThePlayerIsRankedInIt() {
        List<ResolvedTier> result = TierResolver.resolve(
                DisplayMode.MCTIERS_ONLY,
                Map.of(Source.MCTIERS, Map.of("vanilla", ht(2), "axe", ht(1))),
                SELECTED);

        assertEquals(1, result.size());
        assertEquals("vanilla", result.get(0).gamemode().slug());
        assertEquals("HT2", result.get(0).tier().label());
    }

    @Test
    void fallsBackToTheHighestTierOnTheSameSite() {
        List<ResolvedTier> result = TierResolver.resolve(
                DisplayMode.MCTIERS_ONLY,
                Map.of(Source.MCTIERS, Map.of("axe", ht(3), "sword", lt(1), "pot", ht(4))),
                SELECTED);

        assertEquals(1, result.size());
        assertEquals("sword", result.get(0).gamemode().slug());
        assertEquals("LT1", result.get(0).tier().label());
    }

    @Test
    void showsNothingWhenUnrankedOnTheSelectedSite() {
        assertTrue(TierResolver.resolve(
                DisplayMode.MCTIERS_ONLY,
                Map.of(Source.MCTIERS, Map.of()),
                SELECTED).isEmpty());
    }

    @Test
    void singleSiteModeNeverConsultsOtherSites() {
        List<ResolvedTier> result = TierResolver.resolve(
                DisplayMode.MCTIERS_ONLY,
                Map.of(Source.MCTIERS, Map.of(),
                       Source.SUBTIERS, Map.of("bow", ht(1)),
                       Source.NOVATIERS, Map.of("spleef", ht(1))),
                SELECTED);

        assertTrue(result.isEmpty(), "MCTiers-only must not borrow tiers from other sites");
    }

    @Test
    void subtiersAndNovatiersModesBehaveTheSameWay() {
        List<ResolvedTier> sub = TierResolver.resolve(
                DisplayMode.SUBTIERS_ONLY,
                Map.of(Source.SUBTIERS, Map.of("bow", lt(2))),
                SELECTED);
        assertEquals("bow", sub.get(0).gamemode().slug());
        assertEquals(Source.SUBTIERS, sub.get(0).gamemode().source());

        List<ResolvedTier> nova = TierResolver.resolve(
                DisplayMode.NOVATIERS_ONLY,
                Map.of(Source.NOVATIERS, Map.of("axe", ht(5))),
                SELECTED);
        assertEquals("axe", nova.get(0).gamemode().slug(), "falls back to highest on Nova");
        assertEquals(Source.NOVATIERS, nova.get(0).gamemode().source());
    }

    // --- ALL mode ---

    @Test
    void allModeShowsTheBestTierFromEachSiteInFixedOrder() {
        List<ResolvedTier> result = TierResolver.resolve(
                DisplayMode.ALL,
                Map.of(Source.MCTIERS, Map.of("axe", ht(2), "pot", lt(4)),
                       Source.SUBTIERS, Map.of("bow", lt(3)),
                       Source.NOVATIERS, Map.of("spleef", ht(4), "uhc", ht(1))),
                SELECTED);

        assertEquals(3, result.size());
        assertEquals(List.of(Source.MCTIERS, Source.SUBTIERS, Source.NOVATIERS),
                result.stream().map(r -> r.gamemode().source()).toList());
        assertEquals(List.of("HT2", "LT3", "HT1"),
                result.stream().map(r -> r.tier().label()).toList());
        assertEquals(List.of("axe", "bow", "uhc"),
                result.stream().map(r -> r.gamemode().slug()).toList());
    }

    @Test
    void allModeOmitsSitesWithNoTier() {
        List<ResolvedTier> result = TierResolver.resolve(
                DisplayMode.ALL,
                Map.of(Source.MCTIERS, Map.of("axe", ht(2)),
                       Source.SUBTIERS, Map.of(),
                       Source.NOVATIERS, Map.of("uhc", ht(1))),
                SELECTED);

        assertEquals(2, result.size());
        assertEquals(List.of(Source.MCTIERS, Source.NOVATIERS),
                result.stream().map(r -> r.gamemode().source()).toList());
    }

    @Test
    void allModeIgnoresTheSelectedGamemode() {
        // vanilla is selected on MCTiers but axe is higher, so axe must win in ALL mode.
        List<ResolvedTier> result = TierResolver.resolve(
                DisplayMode.ALL,
                Map.of(Source.MCTIERS, Map.of("vanilla", lt(5), "axe", ht(1))),
                SELECTED);

        assertEquals(1, result.size());
        assertEquals("axe", result.get(0).gamemode().slug());
    }

    @Test
    void allModeReturnsEmptyWhenUnrankedEverywhere() {
        assertTrue(TierResolver.resolve(
                DisplayMode.ALL,
                Map.of(Source.MCTIERS, Map.of(), Source.SUBTIERS, Map.of(), Source.NOVATIERS, Map.of()),
                SELECTED).isEmpty());
    }

    // --- highest-tier semantics ---

    @Test
    void retiredTiersCompeteForHighest() {
        // Marlowww's case: every MCTiers mode retired. RHT1 must still beat an active HT3.
        List<ResolvedTier> result = TierResolver.resolve(
                DisplayMode.MCTIERS_ONLY,
                Map.of(Source.MCTIERS, Map.of("vanilla", retiredHt(1), "axe", ht(3))),
                Map.of(Source.MCTIERS, "sword"));

        assertEquals("RHT1", result.get(0).tier().label());
        assertEquals("vanilla", result.get(0).gamemode().slug());
    }

    @Test
    void activeBeatsRetiredAtTheSameRank() {
        List<ResolvedTier> result = TierResolver.resolve(
                DisplayMode.MCTIERS_ONLY,
                Map.of(Source.MCTIERS, Map.of("vanilla", retiredHt(2), "axe", ht(2))),
                Map.of(Source.MCTIERS, "sword"));

        assertEquals("HT2", result.get(0).tier().label());
        assertEquals("axe", result.get(0).gamemode().slug());
    }

    @Test
    void tiesBreakByDeclaredGamemodeOrder() {
        // axe precedes sword in the MCTiers registry, so axe wins an exact tie.
        List<ResolvedTier> result = TierResolver.resolve(
                DisplayMode.MCTIERS_ONLY,
                Map.of(Source.MCTIERS, Map.of("sword", ht(2), "axe", ht(2))),
                Map.of(Source.MCTIERS, "pot"));

        assertEquals("axe", result.get(0).gamemode().slug());
    }

    @Test
    void unknownGamemodeSlugsFromTheApiAreIgnored() {
        List<ResolvedTier> result = TierResolver.resolve(
                DisplayMode.MCTIERS_ONLY,
                Map.of(Source.MCTIERS, Map.of("brand_new_mode", ht(1), "axe", ht(4))),
                Map.of(Source.MCTIERS, "vanilla"));

        assertEquals(1, result.size());
        assertEquals("axe", result.get(0).gamemode().slug());
    }

    @Test
    void missingSourceEntriesAreTreatedAsUnranked() {
        assertTrue(TierResolver.resolve(DisplayMode.ALL, Map.of(), SELECTED).isEmpty());
        assertTrue(TierResolver.resolve(DisplayMode.MCTIERS_ONLY, Map.of(), SELECTED).isEmpty());
    }

    @Test
    void singleSourceReportsTheSiteForSingleSiteModes() {
        assertEquals(java.util.Optional.of(Source.MCTIERS), DisplayMode.MCTIERS_ONLY.singleSource());
        assertEquals(java.util.Optional.of(Source.SUBTIERS), DisplayMode.SUBTIERS_ONLY.singleSource());
        assertEquals(java.util.Optional.of(Source.NOVATIERS), DisplayMode.NOVATIERS_ONLY.singleSource());
        assertEquals(java.util.Optional.empty(), DisplayMode.ALL.singleSource());
    }

    // --- hiding retired tiers (showRetired = false) ---

    @Test
    void hidingRetiredFallsBackToTheBestActiveTierInAllMode() {
        List<ResolvedTier> result = TierResolver.resolve(
                DisplayMode.ALL,
                Map.of(Source.MCTIERS, Map.of("vanilla", retiredHt(1), "axe", lt(3))),
                SELECTED,
                false);

        assertEquals(1, result.size());
        assertEquals("axe", result.get(0).gamemode().slug());
        assertEquals("LT3", result.get(0).tier().label());
    }

    @Test
    void hidingRetiredDropsTheSiteEntirelyWhenEveryTierIsRetired() {
        List<ResolvedTier> result = TierResolver.resolve(
                DisplayMode.ALL,
                Map.of(Source.MCTIERS, Map.of("vanilla", retiredHt(1), "axe", retiredHt(4))),
                SELECTED,
                false);

        assertTrue(result.isEmpty());
    }

    @Test
    void hidingRetiredAlsoAppliesToSingleSiteModes() {
        List<ResolvedTier> result = TierResolver.resolve(
                DisplayMode.MCTIERS_ONLY,
                Map.of(Source.MCTIERS, Map.of("vanilla", retiredHt(1), "axe", ht(5))),
                SELECTED,
                false);

        assertEquals(1, result.size());
        assertEquals("axe", result.get(0).gamemode().slug());
        assertEquals("HT5", result.get(0).tier().label());
    }

    @Test
    void hidingRetiredLeavesOtherSitesUntouched() {
        List<ResolvedTier> result = TierResolver.resolve(
                DisplayMode.ALL,
                Map.of(Source.MCTIERS, Map.of("vanilla", retiredHt(1)),
                       Source.NOVATIERS, Map.of("uhc", ht(2))),
                SELECTED,
                false);

        assertEquals(1, result.size());
        assertEquals(Source.NOVATIERS, result.get(0).gamemode().source());
        assertEquals("HT2", result.get(0).tier().label());
    }

    @Test
    void hidingRetiredChangesNothingWhenNothingIsRetired() {
        Map<Source, Map<String, Tier>> tiers =
                Map.of(Source.MCTIERS, Map.of("vanilla", ht(3), "axe", lt(2)));

        assertEquals(TierResolver.resolve(DisplayMode.ALL, tiers, SELECTED, true),
                TierResolver.resolve(DisplayMode.ALL, tiers, SELECTED, false));
    }

    @Test
    void theThreeArgOverloadStillShowsRetiredTiers() {
        List<ResolvedTier> result = TierResolver.resolve(
                DisplayMode.ALL,
                Map.of(Source.MCTIERS, Map.of("vanilla", retiredHt(1), "axe", lt(3))),
                SELECTED);

        assertEquals(1, result.size());
        assertEquals("RHT1", result.get(0).tier().label());
    }

    // --- rankAll ---

    @Test
    void rankAllListsEveryPlacementBestFirst() {
        List<ResolvedTier> result = TierResolver.rankAll(Source.MCTIERS,
                Map.of("axe", ht(3), "sword", lt(1), "pot", ht(4), "vanilla", ht(1)));

        assertEquals(List.of("vanilla", "sword", "axe", "pot"),
                result.stream().map(r -> r.gamemode().slug()).toList());
        assertEquals(List.of("HT1", "LT1", "HT3", "HT4"),
                result.stream().map(r -> r.tier().label()).toList());
    }

    @Test
    void rankAllBreaksTiesTowardTheActiveTierThenTheSitesOrder() {
        List<ResolvedTier> result = TierResolver.rankAll(Source.MCTIERS,
                Map.of("sword", retiredHt(2), "axe", ht(2), "pot", ht(2)));

        // axe before pot is the site's declared order; the retired HT2 comes last.
        assertEquals(List.of("axe", "pot", "sword"),
                result.stream().map(r -> r.gamemode().slug()).toList());
    }

    @Test
    void rankAllSkipsGamemodesThisBuildDoesNotKnow() {
        List<ResolvedTier> result = TierResolver.rankAll(Source.MCTIERS,
                Map.of("axe", ht(2), "not_a_real_gamemode", ht(1)));

        assertEquals(1, result.size());
        assertEquals("axe", result.get(0).gamemode().slug());
    }

    @Test
    void rankAllOfNothingIsAnEmptyList() {
        assertTrue(TierResolver.rankAll(Source.MCTIERS, Map.of()).isEmpty());
        assertTrue(TierResolver.rankAll(Source.MCTIERS, null).isEmpty());
    }

    @Test
    void highestOnIsTheHeadOfRankAll() {
        Map<String, Tier> tiers = Map.of("axe", ht(3), "sword", lt(1), "pot", ht(4));
        assertEquals(TierResolver.rankAll(Source.MCTIERS, tiers).get(0),
                TierResolver.highestOn(Source.MCTIERS, tiers).orElseThrow());
    }

    @Test
    void activeOnlyDropsRetiredPlacementsAndKeepsOrder() {
        Map<String, Tier> tiers = new LinkedHashMap<>();
        tiers.put("vanilla", ht(1));
        tiers.put("sword", retiredHt(2));
        tiers.put("pot", lt(3));

        Map<String, Tier> active = TierResolver.activeOnly(tiers);

        assertEquals(List.of("vanilla", "pot"), List.copyOf(active.keySet()));
    }

    @Test
    void activeOnlyReturnsTheSameMapWhenNothingIsRetired() {
        Map<String, Tier> tiers = Map.of("vanilla", ht(1));
        assertSame(tiers, TierResolver.activeOnly(tiers));
    }

    @Test
    void activeOnlyHandlesNullAndEmpty() {
        assertTrue(TierResolver.activeOnly(null).isEmpty());
        assertTrue(TierResolver.activeOnly(Map.of()).isEmpty());
    }
}
