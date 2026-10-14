package com.w0x7y.justtiers.api;

import com.w0x7y.justtiers.tier.Tier;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Nova identities and placements normalize consistently, with malformed data kept distinct from an empty leaderboard. */
class NovaParserTest {

    private static final String USERS_JSON = """
            [
              {"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504","minecraftUsername":"X_SUS",
               "region":"EU",
               "tiers":{"Elytra":"HT4","Modern SMP":"HT3","Spear Mace":"LT3"},
               "peakTiers":{"Elytra":"HT1"},
               "retiredTiers":{"Elytra":false,"Modern SMP":true,"Spear Mace":false}},
              {"minecraftUuid":"dadd05d5-e1a2-41bc-be3e-de5f7d9fffee","minecraftUsername":"sigeonpexign",
               "region":"NA",
               "tiers":{"Vanilla":"HT4","Diamond Cart":"LT5"},
               "peakTiers":{},
               "retiredTiers":{}}
            ]
            """;

    private static final UUID X_SUS = UUID.fromString("4b25be24-97f5-4adf-967d-8d69ef54d504");
    private static final UUID SIGEON = UUID.fromString("dadd05d5-e1a2-41bc-be3e-de5f7d9fffee");

    @Test
    void undashedUuidsAreIndexed() {
        assertTrue(NovaParser.parseUsersDetailed(USERS_JSON).users().containsKey(X_SUS));
    }

    @Test
    void dashedUuidsAreAlsoAccepted() {
        assertTrue(NovaParser.parseUsersDetailed(USERS_JSON).users().containsKey(SIGEON));
    }

    @Test
    void displayNameKeysAreNormalisedToSlugs() {
        Map<String, Tier> tiers = NovaParser.parseUsersDetailed(USERS_JSON).users().get(X_SUS);
        assertEquals(java.util.Set.of("elytra", "modernsmp", "spearmace"), tiers.keySet());
    }

    @Test
    void tierStringsAreParsed() {
        Map<String, Tier> tiers = NovaParser.parseUsersDetailed(USERS_JSON).users().get(X_SUS);
        assertEquals("HT4", tiers.get("elytra").label());
        assertEquals("LT3", tiers.get("spearmace").label());
    }

    @Test
    void retiredMapMarksTiersRetired() {
        Map<String, Tier> tiers = NovaParser.parseUsersDetailed(USERS_JSON).users().get(X_SUS);
        assertTrue(tiers.get("modernsmp").retired());
        assertEquals("RHT3", tiers.get("modernsmp").label());
        assertFalse(tiers.get("elytra").retired());
    }

    @Test
    void peakTiersAreIgnored() {
        // Elytra peaks at HT1 but is currently HT4; the peak must not surface.
        assertEquals("HT4", NovaParser.parseUsersDetailed(USERS_JSON).users().get(X_SUS).get("elytra").label());
    }

    @Test
    void rPrefixedTierStringsAreTreatedAsRetired() {
        String json = """
                [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504",
                  "tiers":{"Axe":"RHT2"},"retiredTiers":{}}]
                """;
        assertEquals("RHT2", NovaParser.parseUsersDetailed(json).users().get(X_SUS).get("axe").label());
    }

    @Test
    void retiredMapFalseOverridesAnRPrefixedString() {
        // retiredTiers is authoritative in both directions: an explicit false must
        // clear the R prefix, not lose to it.
        String json = """
                [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504",
                  "tiers":{"Axe":"RHT2"},"retiredTiers":{"Axe":false}}]
                """;
        Tier tier = NovaParser.parseUsersDetailed(json).users().get(X_SUS).get("axe");
        assertFalse(tier.retired());
        assertEquals("HT2", tier.label());
    }

    @Test
    void unknownGamemodeKeysBecomeDiscoveredSlugsThatKeepTheirWords() {
        String json = """
                [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504",
                  "tiers":{"Axe":"HT2","Quake Pro":"HT1"},"retiredTiers":{"Quake Pro":true}}]
                """;
        Map<String, Tier> tiers = NovaParser.parseUsersDetailed(json).users().get(X_SUS);
        assertEquals(java.util.Set.of("axe", "quake_pro"), tiers.keySet());
        assertEquals("RHT1", tiers.get("quake_pro").label());
    }

    @Test
    void unacceptableGamemodeKeysAreDroppedWithoutRejectingTheRestOfThePlayer() {
        String json = """
                [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504",
                  "tiers":{"Axe":"HT2","<b>Bold</b>":"HT1","%s":"HT1"}}]
                """.formatted("x".repeat(33));
        NovaParser.ParsedIndex parsed = NovaParser.parseUsersDetailed(json);
        assertEquals(java.util.Set.of("axe"), parsed.users().get(X_SUS).keySet());
        assertTrue(parsed.rejectedPlayers().isEmpty());
        assertEquals(2, parsed.unknownGamemodes());
        assertEquals(0, parsed.malformedPlacements());
    }

    /**
     * Placements under refused keys are no evidence about the player, so a record made
     * only of them is unavailable rather than unranked. A payload made only of such
     * records is not a valid leaderboard at all.
     */
    @Test
    void aPlayerListedOnlyUnderRefusedKeysIsRejectedNotUnranked() {
        String json = """
                [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504","tiers":{"<b>Bold</b>":"HT1"}},
                 {"minecraftUuid":"dadd05d5-e1a2-41bc-be3e-de5f7d9fffee","tiers":{"Axe":"LT2"}}]
                """;
        NovaParser.ParsedIndex parsed = NovaParser.parseUsersDetailed(json);
        assertEquals(java.util.Set.of(X_SUS), parsed.rejectedPlayers());
        assertFalse(parsed.users().containsKey(X_SUS));
        assertThrows(TierLookupException.class, () -> NovaParser.parseUsersDetailed("""
                [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504","tiers":{"<b>Bold</b>":"HT1"}}]
                """));
    }

    @Test
    void aPlayerListedOnlyInDiscoveredGamemodesIsRanked() {
        String json = """
                [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504","tiers":{"Future Mode":"LT2"}}]
                """;
        NovaParser.ParsedIndex parsed = NovaParser.parseUsersDetailed(json);
        assertEquals(Map.of("future_mode", new Tier(2, false, false)), parsed.users().get(X_SUS));
        assertTrue(parsed.rejectedPlayers().isEmpty());
    }

    @Test
    void discoveredGamemodesAreCappedPerPlayerWhileRegisteredOnesAreKept() {
        StringBuilder modes = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            modes.append("\"Mode ").append((char) ('a' + i)).append("\":\"HT3\",");
        }
        String json = """
                [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504","tiers":{%s"Axe":"LT1"}}]
                """.formatted(modes);
        NovaParser.ParsedIndex parsed = NovaParser.parseUsersDetailed(json);
        Map<String, Tier> tiers = parsed.users().get(X_SUS);
        assertEquals(17, tiers.size());
        assertTrue(tiers.containsKey("axe"));
        assertTrue(tiers.containsKey("mode_a"));
        assertTrue(tiers.containsKey("mode_p"), "the sixteenth in payload order");
        assertFalse(tiers.containsKey("mode_q"));
        assertEquals(4, parsed.unknownGamemodes());
        assertTrue(parsed.rejectedPlayers().isEmpty());
    }

    @Test
    void playersWithNoTiersAreOmittedEntirely() {
        String json = """
                [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504",
                  "tiers":{},"retiredTiers":{}}]
                """;
        assertTrue(NovaParser.parseUsersDetailed(json).users().isEmpty());
    }

    @Test
    void malformedBodiesAreNotAnEmptyLeaderboard() {
        for (String body : new String[]{null, "", "not json", "{\"a\":1}", "null",
                "[{\"name\":\"Nobody\"}]"}) {
            assertThrows(TierLookupException.class, () -> NovaParser.parseUsersDetailed(body).users());
        }
        assertTrue(NovaParser.parseUsersDetailed(" [] ").users().isEmpty());
    }

    @Test
    void entriesWithBadUuidsAreSkippedNotFatal() {
        String json = """
                [{"minecraftUuid":"zzz","tiers":{"Axe":"HT2"},"retiredTiers":{}},
                 {"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504","tiers":{"Axe":"HT3"},"retiredTiers":{}}]
                """;
        Map<UUID, Map<String, Tier>> users = NovaParser.parseUsersDetailed(json).users();
        assertEquals(1, users.size());
        assertEquals("HT3", users.get(X_SUS).get("axe").label());
    }

    @Test
    void parseUuidAcceptsBothForms() {
        assertEquals(Optional.of(X_SUS), NovaParser.parseUuid("4b25be2497f54adf967d8d69ef54d504"));
        assertEquals(Optional.of(SIGEON), NovaParser.parseUuid("dadd05d5-e1a2-41bc-be3e-de5f7d9fffee"));
        assertEquals(Optional.empty(), NovaParser.parseUuid("zzz"));
        assertEquals(Optional.empty(), NovaParser.parseUuid(null));
    }
    @Test
    void anIndexContainingOnlyUnparseablePlacementsIsNotAnEmptyLeaderboard() {
        String body = """
                [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504",
                  "tiers":{"Axe":"unknown tier"}}]
                """;
        assertThrows(TierLookupException.class, () -> NovaParser.parseUsersDetailed(body).users());
    }

    @Test
    void aBrokenPlacementDoesNotDiscardTheSamePlayersValidPlacements() {
        String body = """
                [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504",
                  "tiers":{"SMP":{"unexpected":"object"},"Axe":"HT2"}}]
                """;
        assertEquals("HT2", NovaParser.parseUsersDetailed(body).users().get(X_SUS).get("axe").label());
    }

    @Test
    void detailedIndexRecordsRejectedPlayersAndAggregatedDiagnostics() {
        String body = """
                [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504",
                  "tiers":{"Axe":"HT2","New Mode":"HT1"}},
                 {"minecraftUuid":"dadd05d5-e1a2-41bc-be3e-de5f7d9fffee",
                  "tiers":{"SMP":{"unexpected":"object"},"Axe":"LT3"}},
                 {"minecraftUuid":"invalid","tiers":{}}, false]
                """;
        NovaParser.ParsedIndex parsed = NovaParser.parseUsersDetailed(body);
        assertEquals(java.util.Set.of(SIGEON), parsed.rejectedPlayers());
        assertEquals(3, parsed.malformedUsers());
        assertEquals(1, parsed.malformedPlacements());
        assertEquals(0, parsed.unknownGamemodes());
        assertEquals("HT2", parsed.users().get(X_SUS).get("axe").label());
        assertEquals("HT1", parsed.users().get(X_SUS).get("new_mode").label());
        // The compatibility map still preserves understood placements for existing callers.
        assertEquals("LT3", NovaParser.parseUsersDetailed(body).users().get(SIGEON).get("axe").label());
    }

    @Test
    void detailedIndexIsDeeplyImmutable() {
        NovaParser.ParsedIndex parsed = NovaParser.parseUsersDetailed(USERS_JSON);
        assertThrows(UnsupportedOperationException.class, () -> parsed.users().clear());
        assertThrows(UnsupportedOperationException.class, () -> parsed.users().get(X_SUS).clear());
        assertThrows(UnsupportedOperationException.class, () -> parsed.rejectedPlayers().add(SIGEON));
    }

    @Test
    void discoveredModesAndEmptyTierMapsDoNotRejectPlayers() {
        String body = """
                [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504","tiers":{"Future Mode":"HT1"}},
                 {"minecraftUuid":"dadd05d5-e1a2-41bc-be3e-de5f7d9fffee","tiers":{}}]
                """;
        NovaParser.ParsedIndex parsed = NovaParser.parseUsersDetailed(body);
        assertEquals(java.util.Set.of(X_SUS), parsed.users().keySet());
        assertTrue(parsed.rejectedPlayers().isEmpty());
        assertEquals(0, parsed.malformedUsers());
        assertEquals(0, parsed.malformedPlacements());
        assertEquals(0, parsed.unknownGamemodes());
    }

    @Test
    void duplicateIdenticalRecordsAreAcceptedAndConflictingAliasesAreRejected() {
        String repeated = """
                [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504","tiers":{"Axe":"HT2"}},
                 {"minecraftUuid":"4b25be24-97f5-4adf-967d-8d69ef54d504","tiers":{"Axe":"HT2"}}]
                """;
        NovaParser.ParsedIndex parsed = NovaParser.parseUsersDetailed(repeated);
        assertTrue(parsed.rejectedPlayers().isEmpty());
        assertEquals(1, parsed.users().size());
        String aliases = """
                [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504",
                  "tiers":{"Spear Mace":"HT2","Mace":"LT3"}}]
                """;
        assertEquals(java.util.Set.of(X_SUS), NovaParser.parseUsersDetailed(aliases).rejectedPlayers());
    }

}
