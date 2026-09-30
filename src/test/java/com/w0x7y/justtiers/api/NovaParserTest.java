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
        assertTrue(NovaParser.parseUsers(USERS_JSON).containsKey(X_SUS));
    }

    @Test
    void dashedUuidsAreAlsoAccepted() {
        assertTrue(NovaParser.parseUsers(USERS_JSON).containsKey(SIGEON));
    }

    @Test
    void displayNameKeysAreNormalisedToSlugs() {
        Map<String, Tier> tiers = NovaParser.parseUsers(USERS_JSON).get(X_SUS);
        assertEquals(java.util.Set.of("elytra", "modernsmp", "spearmace"), tiers.keySet());
    }

    @Test
    void tierStringsAreParsed() {
        Map<String, Tier> tiers = NovaParser.parseUsers(USERS_JSON).get(X_SUS);
        assertEquals("HT4", tiers.get("elytra").label());
        assertEquals("LT3", tiers.get("spearmace").label());
    }

    @Test
    void retiredMapMarksTiersRetired() {
        Map<String, Tier> tiers = NovaParser.parseUsers(USERS_JSON).get(X_SUS);
        assertTrue(tiers.get("modernsmp").retired());
        assertEquals("RHT3", tiers.get("modernsmp").label());
        assertFalse(tiers.get("elytra").retired());
    }

    @Test
    void peakTiersAreIgnored() {
        // Elytra peaks at HT1 but is currently HT4; the peak must not surface.
        assertEquals("HT4", NovaParser.parseUsers(USERS_JSON).get(X_SUS).get("elytra").label());
    }

    @Test
    void rPrefixedTierStringsAreTreatedAsRetired() {
        String json = """
                [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504",
                  "tiers":{"Axe":"RHT2"},"retiredTiers":{}}]
                """;
        assertEquals("RHT2", NovaParser.parseUsers(json).get(X_SUS).get("axe").label());
    }

    @Test
    void retiredMapFalseOverridesAnRPrefixedString() {
        // retiredTiers is authoritative in both directions: an explicit false must
        // clear the R prefix, not lose to it.
        String json = """
                [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504",
                  "tiers":{"Axe":"RHT2"},"retiredTiers":{"Axe":false}}]
                """;
        Tier tier = NovaParser.parseUsers(json).get(X_SUS).get("axe");
        assertFalse(tier.retired());
        assertEquals("HT2", tier.label());
    }

    @Test
    void unknownGamemodeKeysAreDropped() {
        String json = """
                [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504",
                  "tiers":{"Axe":"HT2","Quake Pro":"HT1"},"retiredTiers":{}}]
                """;
        assertEquals(java.util.Set.of("axe"), NovaParser.parseUsers(json).get(X_SUS).keySet());
    }

    @Test
    void playersWithNoTiersAreOmittedEntirely() {
        String json = """
                [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504",
                  "tiers":{},"retiredTiers":{}}]
                """;
        assertTrue(NovaParser.parseUsers(json).isEmpty());
    }

    @Test
    void malformedBodiesAreNotAnEmptyLeaderboard() {
        for (String body : new String[]{null, "", "not json", "{\"a\":1}", "null",
                "[{\"name\":\"Nobody\"}]"}) {
            assertThrows(TierLookupException.class, () -> NovaParser.parseUsers(body));
        }
        assertTrue(NovaParser.parseUsers(" [] ").isEmpty());
    }

    @Test
    void entriesWithBadUuidsAreSkippedNotFatal() {
        String json = """
                [{"minecraftUuid":"zzz","tiers":{"Axe":"HT2"},"retiredTiers":{}},
                 {"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504","tiers":{"Axe":"HT3"},"retiredTiers":{}}]
                """;
        Map<UUID, Map<String, Tier>> users = NovaParser.parseUsers(json);
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
        assertThrows(TierLookupException.class, () -> NovaParser.parseUsers(body));
    }

    @Test
    void aBrokenPlacementDoesNotDiscardTheSamePlayersValidPlacements() {
        String body = """
                [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504",
                  "tiers":{"SMP":{"unexpected":"object"},"Axe":"HT2"}}]
                """;
        assertEquals("HT2", NovaParser.parseUsers(body).get(X_SUS).get("axe").label());
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
        assertEquals(1, parsed.unknownGamemodes());
        assertEquals("HT2", parsed.users().get(X_SUS).get("axe").label());
        // The compatibility map still preserves understood placements for existing callers.
        assertEquals("LT3", NovaParser.parseUsers(body).get(SIGEON).get("axe").label());
    }

    @Test
    void detailedIndexIsDeeplyImmutable() {
        NovaParser.ParsedIndex parsed = NovaParser.parseUsersDetailed(USERS_JSON);
        assertThrows(UnsupportedOperationException.class, () -> parsed.users().clear());
        assertThrows(UnsupportedOperationException.class, () -> parsed.users().get(X_SUS).clear());
        assertThrows(UnsupportedOperationException.class, () -> parsed.rejectedPlayers().add(SIGEON));
    }

    @Test
    void validUnknownModesAndEmptyTierMapsDoNotRejectPlayers() {
        String body = """
                [{"minecraftUuid":"4b25be2497f54adf967d8d69ef54d504","tiers":{"Future Mode":"HT1"}},
                 {"minecraftUuid":"dadd05d5-e1a2-41bc-be3e-de5f7d9fffee","tiers":{}}]
                """;
        NovaParser.ParsedIndex parsed = NovaParser.parseUsersDetailed(body);
        assertTrue(parsed.users().isEmpty());
        assertTrue(parsed.rejectedPlayers().isEmpty());
        assertEquals(0, parsed.malformedUsers());
        assertEquals(0, parsed.malformedPlacements());
        assertEquals(1, parsed.unknownGamemodes());
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
