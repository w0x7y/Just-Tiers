package com.w0x7y.justtiers.debug;

import com.w0x7y.justtiers.cache.SiteGate;
import com.w0x7y.justtiers.cache.SiteHealth;
import com.w0x7y.justtiers.resolve.DisplayMode;
import com.w0x7y.justtiers.tier.Source;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugReportTest {

    private static final SiteGate.Status OPEN = new SiteGate.Status(false, false, 0, 0);

    private static SiteHealth.Snapshot idle() {
        return new SiteHealth.Snapshot(0, 0, OptionalLong.empty(), OptionalLong.empty(),
                Optional.empty(), OptionalLong.empty(), OptionalLong.empty());
    }

    private static SiteHealth.Snapshot healthy() {
        return new SiteHealth.Snapshot(12, 0,
                OptionalLong.of(Duration.ofSeconds(4).toNanos()), OptionalLong.empty(),
                Optional.empty(),
                OptionalLong.of(Duration.ofMillis(180).toNanos()),
                OptionalLong.of(Duration.ofMillis(210).toNanos()));
    }

    private static DebugSnapshot snapshotOf(SiteDiagnostics... sites) {
        return new DebugSnapshot("1.0.2+mc26.2", "26.2", "0.19.3", true, DisplayMode.ALL,
                Duration.ofMinutes(60), 12345, 30, List.of(sites));
    }

    private static SiteDiagnostics site(SiteHealth.Snapshot health, SiteGate.Status gate) {
        return new SiteDiagnostics(Source.MCTIERS, health, gate, 42, 1, 0);
    }

    /** The one site line the report produced, for a report built with exactly one site. */
    private static String siteLine(SiteDiagnostics site) {
        List<String> lines = DebugReport.lines(snapshotOf(site));
        return lines.get(4);
    }

    @Test
    void theHeaderCarriesTheVersionsABugReportNeeds() {
        List<String> lines = DebugReport.lines(snapshotOf(site(healthy(), OPEN)));

        assertEquals("=== Just-Tiers debug ===", lines.get(0));
        assertEquals("Just-Tiers 1.0.2+mc26.2 | Minecraft 26.2 | Fabric Loader 0.19.3", lines.get(1));
        assertEquals("nametags on | mode all | cache TTL 60m", lines.get(2));
        assertEquals("NovaTiers index 12345 players | refresh every 30m", lines.get(3));
    }

    @Test
    void aHealthySiteReportsCountsAgesLatencyAndCache() {
        assertEquals("MCTiers: ok | 12 ok, 0 failed | last ok 4s ago "
                        + "| latency 180ms last, 210ms mean | 42 cached, 1 in flight, 0 retrying",
                siteLine(site(healthy(), OPEN)));
    }

    @Test
    void aSiteNobodyHasAskedYetSaysSoRatherThanReportingZeroes() {
        String line = siteLine(site(idle(), OPEN));

        assertTrue(line.contains("no lookups yet"), line);
        assertFalse(line.contains("latency"), "there is no latency to report yet: " + line);
        assertFalse(line.contains("last ok"), line);
        // What the cache holds is still worth saying: NovaTiers fills it without lookups.
        assertTrue(line.contains("42 cached"), line);
    }

    @Test
    void aPausedSiteNamesTheWait() {
        SiteGate.Status paused = new SiteGate.Status(true, false,
                Duration.ofSeconds(28).toNanos(), 8);

        assertTrue(siteLine(site(healthy(), paused)).startsWith("MCTiers: PAUSED, retrying in 28s"));
    }

    @Test
    void aPausedSiteWithAProbeOutSaysThatInstead() {
        SiteGate.Status probing = new SiteGate.Status(true, true, 0, 8);

        assertTrue(siteLine(site(healthy(), probing)).startsWith("MCTiers: PAUSED, probe in flight"));
    }

    @Test
    void aPauseThatHasAlreadyExpiredIsNotReportedAsZeroSeconds() {
        SiteGate.Status due = new SiteGate.Status(true, false, 0, 8);

        assertTrue(siteLine(site(healthy(), due)).startsWith("MCTiers: PAUSED, retrying now"));
    }

    @Test
    void failuresBelowTheThresholdAreVisibleWhileTheGateIsStillOpen() {
        SiteGate.Status nearly = new SiteGate.Status(false, false, 0, 7);

        // The state that reads as healthy and is one lookup from not being.
        assertTrue(siteLine(site(healthy(), nearly))
                .startsWith("MCTiers: ok (7 failures in a row)"));
    }

    @Test
    void theLastErrorGetsItsOwnLineUnderTheSite() {
        SiteHealth.Snapshot failing = new SiteHealth.Snapshot(3, 9,
                OptionalLong.of(Duration.ofMinutes(6).toNanos()),
                OptionalLong.of(Duration.ofSeconds(12).toNanos()),
                Optional.of("TierLookupException: HTTP 503"),
                OptionalLong.of(Duration.ofSeconds(4).toNanos()),
                OptionalLong.of(Duration.ofMillis(1200).toNanos()));

        List<String> lines = DebugReport.lines(snapshotOf(site(failing, OPEN)));

        assertEquals("MCTiers: ok | 3 ok, 9 failed | last ok 6m ago, last fail 12s ago "
                        + "| latency 4.0s last, 1.2s mean | 42 cached, 1 in flight, 0 retrying",
                lines.get(4));
        assertEquals("  last error: TierLookupException: HTTP 503", lines.get(5));
    }

    @Test
    void aSiteWithNoErrorGetsNoErrorLine() {
        assertEquals(5, DebugReport.lines(snapshotOf(site(healthy(), OPEN))).size(),
                "an empty 'last error: none' on every site is three wasted lines");
    }

    @Test
    void aSiteThatHasOnlyEverFailedSaysSo() {
        SiteHealth.Snapshot neverWorked = new SiteHealth.Snapshot(0, 4,
                OptionalLong.empty(), OptionalLong.of(Duration.ofSeconds(3).toNanos()),
                Optional.of("ConnectException: refused"),
                OptionalLong.of(0), OptionalLong.of(0));

        assertTrue(siteLine(site(neverWorked, OPEN)).contains("never succeeded, last fail 3s ago"),
                "an absent success must not read as a recent one");
    }

    @Test
    void everySiteIsReportedInDeclarationOrder() {
        List<SiteDiagnostics> sites = Source.ALL.stream()
                .map(source -> new SiteDiagnostics(source, idle(), OPEN, 0, 0, 0))
                .toList();
        List<String> lines = DebugReport.lines(snapshotOf(sites.toArray(SiteDiagnostics[]::new)));

        assertEquals(4 + Source.ALL.size(), lines.size());
        for (int i = 0; i < Source.ALL.size(); i++) {
            assertTrue(lines.get(4 + i).startsWith(Source.ALL.get(i).displayName() + ":"),
                    lines.get(4 + i));
        }
    }

    @Test
    void aDisabledModAndAnOffTtlAreBothStated() {
        DebugSnapshot off = new DebugSnapshot("1.0.2", "26.2", "0.19.3", false,
                DisplayMode.MCTIERS_ONLY, Duration.ZERO, 0, 30, List.of());

        assertEquals("nametags off | mode mctiers_only | cache TTL off (kept for the session)",
                DebugReport.lines(off).get(2));
    }

    @Test
    void longAgesFallBackToCoarserUnits() {
        SiteHealth.Snapshot old = new SiteHealth.Snapshot(1, 0,
                OptionalLong.of(Duration.ofHours(3).plusMinutes(20).toNanos()),
                OptionalLong.empty(), Optional.empty(),
                OptionalLong.of(Duration.ofMillis(999).toNanos()),
                OptionalLong.of(Duration.ofMillis(1000).toNanos()));

        String line = siteLine(site(old, OPEN));
        assertTrue(line.contains("last ok 3h ago"), line);
        // The millisecond/second boundary, from both sides.
        assertTrue(line.contains("latency 999ms last, 1.0s mean"), line);
    }

    @Test
    void theClipboardTextIsTheSameLinesJoined() {
        DebugSnapshot snapshot = snapshotOf(site(healthy(), OPEN));

        assertEquals(String.join("\n", DebugReport.lines(snapshot)), DebugReport.asText(snapshot));
    }
}
