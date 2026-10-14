package com.w0x7y.justtiers.query;

import com.w0x7y.justtiers.api.RetryAfterException;
import com.w0x7y.justtiers.api.TierLookupException;
import com.w0x7y.justtiers.api.TierSource;
import com.w0x7y.justtiers.cache.CachePolicy;
import com.w0x7y.justtiers.cache.TierCache;
import com.w0x7y.justtiers.config.JustTiersConfig;
import com.w0x7y.justtiers.resolve.TierResolver;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** Internal cache states map to public answers without blocking, and stay distinct. */
class TierQueryTest {
    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    /** Completes each request by hand, so no test waits on time or threads. */
    private static final class Site implements TierSource {
        private final Source source;
        int calls;
        CompletableFuture<Answer> pending;

        Site(Source source) { this.source = source; }

        @Override public Source source() { return source; }
        @Override public CompletableFuture<Answer> fetchAnswer(UUID uuid) {
            calls++;
            pending = new CompletableFuture<>();
            return pending;
        }

        void answer(Map<String, Tier> tiers) { answer(tiers, Duration.ZERO); }
        void answer(Map<String, Tier> tiers, Duration age) { pending.complete(new Answer(tiers, age, false)); }
        void fail(RuntimeException error) { pending.completeExceptionally(error); }
    }

    private static final class Fixture {
        final AtomicLong clock = new AtomicLong();
        final Map<Source, Site> sites = new EnumMap<>(Source.class);
        final TierCache cache;
        /** Default settings: nametag tiers and every site enabled. */
        final JustTiersConfig config = new JustTiersConfig();
        final TierQuery query;

        Fixture(Source... registered) {
            List<TierSource> sources = new ArrayList<>();
            for (Source source : registered) {
                Site site = new Site(source);
                sites.put(source, site);
                sources.add(site);
            }
            cache = new TierCache(sources, CachePolicy.DEFAULT, clock::get, () -> 0.5);
            query = new TierQuery(() -> cache, () -> config.nametagSettings().automaticLookupSites());
        }

        Site site(Source source) { return sites.get(source); }
        void advance(Duration by) { clock.addAndGet(by.toNanos()); }
    }

    private static Tier tier(String label) {
        return Tier.parse(label).orElseThrow();
    }

    @Test
    void aMissIsPendingStartsOneRequestAndBecomesRankedBestFirst() {
        Fixture it = new Fixture(Source.PVPTIERS);
        Site site = it.site(Source.PVPTIERS);

        assertEquals(new SiteAnswer.Pending("pvptiers"), it.query.peek(PLAYER, "pvptiers"));
        assertEquals(new SiteAnswer.Pending("pvptiers"), it.query.peek(PLAYER, "pvptiers"));
        assertEquals(1, site.calls, "repeated peeks share the one request");

        site.answer(Map.of("axe", tier("LT3"), "crystal", tier("HT1"), "sword", tier("RHT1")));
        SiteAnswer.Ranked ranked = assertInstanceOf(SiteAnswer.Ranked.class, it.query.peek(PLAYER, "pvptiers"));

        assertEquals(List.of(
                new Placement("pvptiers", "crystal", "Crystal", "HT1", 0, false, true),
                new Placement("pvptiers", "sword", "Sword", "RHT1", 0, true, true),
                new Placement("pvptiers", "axe", "Axe", "LT3", 8, false, true)), ranked.placements());
        assertEquals(new Freshness(Duration.ZERO, false, false, false), ranked.freshness());
        assertEquals(1, site.calls, "a fresh answer is not requested again");
    }

    @Test
    void aValidEmptyAnswerIsUnrankedAndAFailureIsUnavailable() {
        Fixture it = new Fixture(Source.SUBTIERS);
        Site site = it.site(Source.SUBTIERS);

        it.query.peek(PLAYER, "subtiers");
        site.answer(Map.of());
        assertInstanceOf(SiteAnswer.Unranked.class, it.query.peek(PLAYER, "subtiers"));

        it.query.peek(OTHER, "subtiers");
        site.fail(new TierLookupException("malformed HTTP 200"));
        assertEquals(new SiteAnswer.Unavailable("subtiers"), it.query.peek(OTHER, "subtiers"));
        assertEquals(new SiteAnswer.Unavailable("subtiers"), it.query.peek(OTHER, "subtiers"),
                "the player's backoff keeps the site unavailable without another request");
        assertEquals(2, site.calls);

        it.advance(Duration.ofMinutes(2));
        assertEquals(new SiteAnswer.Pending("subtiers"), it.query.peek(OTHER, "subtiers"),
                "once the backoff elapses a peek retries");
        assertEquals(3, site.calls);
    }

    @Test
    void aPausedSiteIsUnavailableWithoutRequesting() {
        Fixture it = new Fixture(Source.PVPHQ);
        Site site = it.site(Source.PVPHQ);

        it.query.peek(PLAYER, "pvphq");
        site.fail(new RetryAfterException("HTTP 429", Duration.ofMinutes(10)));

        assertEquals(new SiteAnswer.Unavailable("pvphq"), it.query.peek(OTHER, "pvphq"));
        assertEquals(1, site.calls, "a cooldown defers the new player's request");
    }

    @Test
    void retainedPlacementsStayRankedWhileRefreshingAndAfterARefreshFails() {
        Fixture it = new Fixture(Source.PVPTIERS);
        Site site = it.site(Source.PVPTIERS);
        it.query.peek(PLAYER, "pvptiers");
        site.answer(Map.of("mace", tier("HT2")));

        it.advance(CachePolicy.DEFAULT_TTL);
        SiteAnswer.Ranked refreshing = assertInstanceOf(SiteAnswer.Ranked.class, it.query.peek(PLAYER, "pvptiers"));
        assertEquals(2, site.calls, "an expired answer starts its replacement");
        assertEquals(new Freshness(CachePolicy.DEFAULT_TTL, true, true, false), refreshing.freshness());
        assertEquals("HT2", refreshing.best().label());

        site.fail(new TierLookupException("timeout"));
        SiteAnswer.Ranked failed = assertInstanceOf(SiteAnswer.Ranked.class, it.query.peek(PLAYER, "pvptiers"));
        assertEquals(new Freshness(CachePolicy.DEFAULT_TTL, true, false, true), failed.freshness());
    }

    @Test
    void aBulkAnswerReportsTheAgeOfItsIndex() {
        Fixture it = new Fixture(Source.NOVATIERS);
        it.query.peek(PLAYER, "novatiers");
        it.site(Source.NOVATIERS).answer(Map.of("vanilla", tier("LT1")), Duration.ofMinutes(10));

        SiteAnswer.Ranked ranked = assertInstanceOf(SiteAnswer.Ranked.class, it.query.peek(PLAYER, "novatiers"));
        assertEquals(Duration.ofMinutes(10), ranked.freshness().age());
        assertEquals(new Placement("novatiers", "vanilla", "Vanilla", "LT1", 2, false, true), ranked.best());
    }

    @Test
    void pvpHqMiddleDivisionsRankBetweenHighAndLow() {
        Fixture it = new Fixture(Source.PVPHQ);
        it.query.peek(PLAYER, "pvphq");
        it.site(Source.PVPHQ).answer(Map.of("axe", tier("LT3"), "cart", tier("MT3"), "mace", tier("HT3"),
                "uhc", tier("RMT2")));

        SiteAnswer.Ranked ranked = assertInstanceOf(SiteAnswer.Ranked.class, it.query.peek(PLAYER, "pvphq"));
        assertEquals(List.of("RMT2", "HT3", "MT3", "LT3"), ranked.placements().stream().map(Placement::label).toList());
        assertEquals(List.of(4, 6, 7, 8), ranked.placements().stream().map(Placement::rank).toList());
        assertTrue(ranked.placements().get(0).retired());
    }

    @Test
    void bestMirrorsTheResolverIncludingRetiredTiesDeclaredOrderAndDiscoveredGamemodes() {
        List<Map<String, Tier>> cases = List.of(
                Map.of("axe", tier("RHT2"), "pot", tier("HT2")),
                Map.of("sword", tier("HT2"), "axe", tier("HT2")),
                Map.of("uhc", tier("RHT1"), "smp", tier("LT1")),
                Map.of("neth_pot", tier("RLT3")),
                // Discovered only: equal tiers break by slug.
                Map.of("quake_pro", tier("LT2"), "beta_mode", tier("LT2")),
                // A registered gamemode wins a tie with a discovered one, whatever the slugs.
                Map.of("sword", tier("HT3"), "aaa_mode", tier("HT3")),
                Map.of("axe", tier("LT3"), "zz_mode", tier("HT1")),
                Map.of("axe", tier("RHT2"), "new_mode", tier("HT2")));
        for (Map<String, Tier> tiers : cases) {
            Fixture it = new Fixture(Source.PVPTIERS);
            it.query.peek(PLAYER, "pvptiers");
            it.site(Source.PVPTIERS).answer(tiers);

            var expected = TierResolver.highestOn(Source.PVPTIERS, tiers).orElseThrow();
            assertEquals(Optional.of(TierQuery.placement(expected.gamemode(), expected.tier())),
                    it.query.best(PLAYER, "pvptiers"), tiers.toString());
        }
    }

    @Test
    void retiredLabelsKeepTheirPrefix() {
        Fixture it = new Fixture(Source.SUBTIERS);
        it.query.peek(PLAYER, "subtiers");
        it.site(Source.SUBTIERS).answer(Map.of("bow", tier("RLT3")));

        Placement placement = it.query.best(PLAYER, "subtiers").orElseThrow();
        assertEquals("RLT3", placement.label());
        assertTrue(placement.retired());
        assertEquals(8, placement.rank());
    }

    @Test
    void discoveredGamemodesAreRankedPlacementsWithDerivedNames() {
        Fixture it = new Fixture(Source.PVPTIERS, Source.SUBTIERS);
        it.query.peek(PLAYER, "pvptiers");
        it.site(Source.PVPTIERS).answer(Map.of("quake_pro", tier("RHT1")));
        it.query.peek(PLAYER, "subtiers");
        it.site(Source.SUBTIERS).answer(Map.of("zz_mode", tier("LT4"), "bed", tier("LT4"),
                "aa_mode", tier("LT4"), "bow", tier("HT5")));

        SiteAnswer.Ranked onlyDiscovered = assertInstanceOf(SiteAnswer.Ranked.class, it.query.peek(PLAYER, "pvptiers"));
        assertEquals(List.of(new Placement("pvptiers", "quake_pro", "Quake Pro", "RHT1", 0, true, false)),
                onlyDiscovered.placements(), "a placement in a new gamemode is evidence, not unranked");

        SiteAnswer.Ranked mixed = assertInstanceOf(SiteAnswer.Ranked.class, it.query.peek(PLAYER, "subtiers"));
        assertEquals(List.of("bed", "aa_mode", "zz_mode", "bow"),
                mixed.placements().stream().map(Placement::gamemode).toList(),
                "equal tiers list registered gamemodes first, then discovered ones by slug");
        assertEquals(List.of(true, false, false, true),
                mixed.placements().stream().map(Placement::recognized).toList());
        assertEquals("Zz Mode", mixed.placements().get(2).gamemodeName());
    }

    @Test
    void peekAllKeepsEachSiteIndependentInSiteOrder() {
        Fixture it = new Fixture(Source.PVPTIERS, Source.PVPHQ, Source.SUBTIERS);
        it.query.peekAll(PLAYER);
        it.site(Source.PVPTIERS).answer(Map.of("pot", tier("HT4")));
        it.site(Source.PVPHQ).fail(new TierLookupException("HTTP 500"));

        Map<String, SiteAnswer> answers = it.query.peekAll(PLAYER);
        assertEquals(List.of("pvptiers", "pvphq", "subtiers", "novatiers"), List.copyOf(answers.keySet()));
        assertInstanceOf(SiteAnswer.Ranked.class, answers.get("pvptiers"));
        assertEquals(new SiteAnswer.Unavailable("pvphq"), answers.get("pvphq"));
        assertEquals(new SiteAnswer.Pending("subtiers"), answers.get("subtiers"));
        assertEquals(new SiteAnswer.Unavailable("novatiers"), answers.get("novatiers"),
                "a site with no configured source gives no evidence");
        assertThrows(UnsupportedOperationException.class, () -> answers.put("pvptiers", null));
    }

    @Test
    void observationsMapToExactlyOneCase() {
        var cached = new TierCache.CachedAnswer(Map.of(), Duration.ofSeconds(3), false, true, false,
                Optional.of(Duration.ofMinutes(1)), 1);
        assertEquals(new SiteAnswer.Pending("pvptiers"),
                TierQuery.answer(Source.PVPTIERS, new TierCache.Observation(Optional.empty(), true)));
        assertEquals(new SiteAnswer.Unavailable("pvptiers"), TierQuery.answer(Source.PVPTIERS, TierCache.Observation.NONE));
        assertEquals(new SiteAnswer.Unranked("pvptiers", new Freshness(Duration.ofSeconds(3), false, true, false)),
                TierQuery.answer(Source.PVPTIERS, new TierCache.Observation(Optional.of(cached), true)),
                "a retained answer stays evidence while its replacement is in flight");
    }

    @Test
    void aDisabledSiteIsDisabledAndNeverRequested() {
        Fixture it = new Fixture(Source.PVPTIERS, Source.PVPHQ);
        it.config.setSiteEnabled(Source.PVPHQ, false);

        assertEquals(new SiteAnswer.Disabled("pvphq"), it.query.peek(PLAYER, "pvphq"));
        assertEquals(Optional.empty(), it.query.best(PLAYER, "pvphq"));
        Map<String, SiteAnswer> answers = it.query.peekAll(PLAYER);
        assertEquals(new SiteAnswer.Disabled("pvphq"), answers.get("pvphq"));
        assertEquals(new SiteAnswer.Pending("pvptiers"), answers.get("pvptiers"));
        assertEquals(0, it.site(Source.PVPHQ).calls, "a disabled site must not be asked");
        assertEquals(1, it.site(Source.PVPTIERS).calls, "other sites keep working");
    }

    @Test
    void turningNametagTiersOffDisablesEverySiteEvenWithAKnownAnswer() {
        Fixture it = new Fixture(Source.PVPTIERS, Source.PVPHQ, Source.SUBTIERS, Source.NOVATIERS);
        it.query.peek(PLAYER, "pvptiers");
        it.site(Source.PVPTIERS).answer(Map.of("axe", tier("HT1")));
        it.config.setEnabled(false);

        it.query.peekAll(PLAYER).forEach((site, answer) -> assertEquals(new SiteAnswer.Disabled(site), answer));
        assertEquals(Optional.empty(), it.query.best(PLAYER, "pvptiers"), "a cached answer is not reported either");
        it.advance(CachePolicy.DEFAULT_TTL);
        it.query.peekAll(PLAYER);
        assertEquals(1, it.site(Source.PVPTIERS).calls, "an expired answer is not refreshed while disabled");
        for (Source source : List.of(Source.PVPHQ, Source.SUBTIERS, Source.NOVATIERS)) {
            assertEquals(0, it.site(source).calls, source + " must not be asked");
        }
    }

    @Test
    void reEnablingRestoresNormalAnswersAndRequests() {
        Fixture it = new Fixture(Source.PVPTIERS, Source.SUBTIERS);
        it.config.setEnabled(false);
        it.config.setSiteEnabled(Source.SUBTIERS, false);
        it.query.peekAll(PLAYER);

        it.config.setEnabled(true);
        assertEquals(new SiteAnswer.Pending("pvptiers"), it.query.peek(PLAYER, "pvptiers"));
        assertEquals(new SiteAnswer.Disabled("subtiers"), it.query.peek(PLAYER, "subtiers"),
                "the site toggle still applies once the master switch is back on");
        it.site(Source.PVPTIERS).answer(Map.of("pot", tier("LT2")));
        assertEquals("LT2", it.query.best(PLAYER, "pvptiers").orElseThrow().label());

        it.config.setSiteEnabled(Source.SUBTIERS, true);
        assertEquals(new SiteAnswer.Pending("subtiers"), it.query.peek(PLAYER, "subtiers"));
        assertEquals(1, it.site(Source.PVPTIERS).calls);
        assertEquals(1, it.site(Source.SUBTIERS).calls);
    }

    @Test
    void aUuidThatIsNotAnAccountIsUnavailableAndNeverSentToASite() {
        Fixture it = new Fixture(Source.PVPTIERS, Source.NOVATIERS);
        // Offline-mode servers mint name-based version 3 UUIDs; the leaderboards never list them.
        UUID offline = UUID.nameUUIDFromBytes("OfflinePlayer:Steve".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(3, offline.version());

        assertEquals(new SiteAnswer.Unavailable("pvptiers"), it.query.peek(offline, "pvptiers"));
        assertEquals(new SiteAnswer.Unavailable("novatiers"), it.query.peekAll(offline).get("novatiers"));
        assertEquals(Optional.empty(), it.query.best(offline, "pvptiers"));
        assertEquals(0, it.site(Source.PVPTIERS).calls);
        assertEquals(0, it.site(Source.NOVATIERS).calls);

        // A disabled site still reads as the player's choice rather than as unavailable.
        it.config.setSiteEnabled(Source.PVPTIERS, false);
        assertEquals(new SiteAnswer.Disabled("pvptiers"), it.query.peek(offline, "pvptiers"));
    }

    @Test
    void automaticLookupSitesFollowTheMasterSwitchAndSiteToggles() {
        JustTiersConfig config = new JustTiersConfig();
        assertEquals(java.util.Set.copyOf(Source.ALL), config.nametagSettings().automaticLookupSites());
        config.setSiteEnabled(Source.NOVATIERS, false);
        assertEquals(java.util.Set.of(Source.PVPTIERS, Source.PVPHQ, Source.SUBTIERS),
                config.nametagSettings().automaticLookupSites());
        config.setEnabled(false);
        assertEquals(java.util.Set.of(), config.nametagSettings().automaticLookupSites());
    }

    @Test
    void beforeInitializationEverySiteIsPendingAndNothingStarts() {
        TierQuery query = new TierQuery(() -> null, () -> null);
        assertFalse(query.ready());
        assertEquals(new SiteAnswer.Pending("pvphq"), query.peek(PLAYER, "pvphq"));
        assertEquals(Optional.empty(), query.best(PLAYER, "pvphq"));
        query.peekAll(PLAYER).forEach((site, answer) -> assertEquals(new SiteAnswer.Pending(site), answer));

        // The cache is created before the settings during initialization; that window is still pending.
        Site site = new Site(Source.PVPTIERS);
        TierCache cache = new TierCache(List.of(site));
        TierQuery halfway = new TierQuery(() -> cache, () -> null);
        assertFalse(halfway.ready());
        assertEquals(new SiteAnswer.Pending("pvptiers"), halfway.peek(PLAYER, "pvptiers"));
        assertEquals(0, site.calls);

        // In unit tests the client initializer never runs, so the public entry point is uninitialized.
        assertFalse(JustTiersQuery.isReady());
        assertEquals(new SiteAnswer.Pending("novatiers"), JustTiersQuery.peek(PLAYER, "novatiers"));
    }

    @Test
    void siteIdsAreStableLowercaseAndMatchCommandIds() {
        assertEquals(List.of("pvptiers", "pvphq", "subtiers", "novatiers"), JustTiersQuery.SITE_IDS);
        assertEquals(Source.ALL.stream().map(Source::id).toList(),
                JustTiersQuery.SITE_IDS, "the API and /justtiers site use the same ids");
        assertEquals(1, JustTiersQuery.API_VERSION);
        assertEquals(Source.PVPHQ, TierQuery.source(" PvPHQ "));
        assertThrows(IllegalArgumentException.class, () -> TierQuery.source("mctiers"));
        assertThrows(NullPointerException.class, () -> new TierQuery(() -> null, () -> null).peek(null, "pvptiers"));
    }
}
