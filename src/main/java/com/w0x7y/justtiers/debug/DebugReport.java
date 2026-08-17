package com.w0x7y.justtiers.debug;

import com.w0x7y.justtiers.cache.SiteGate;
import com.w0x7y.justtiers.cache.SiteHealth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

/**
 * Formats a {@link DebugSnapshot} as the lines {@code /justtiers debug} prints and copies.
 *
 * <p>Deliberately not translated, unlike every other string this mod shows. The audience
 * for a debug report is whoever is fixing the bug, not the player running it: a report
 * pasted into an issue in a language the maintainer cannot read would be worth less than
 * no report at all. Everything the command says <em>about</em> the report — that it ran,
 * that it was copied — is a translation key as usual.
 *
 * <p>Every number is formatted in {@link Locale#ROOT} for the same reason: two reports of
 * the same outage should differ in their contents, not in their decimal separators.
 */
public final class DebugReport {

    private static final String HEADER = "=== Just-Tiers debug ===";
    /** Marks the error line as belonging to the site above it, in chat and in a paste alike. */
    private static final String CONTINUATION = "  ";

    /**
     * One line per fact, and one line per site rather than a block each: this is read
     * twice, once in a chat window ten lines tall and once in an issue, and a per-site
     * block is worse in both.
     */
    public static List<String> lines(DebugSnapshot snapshot) {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        lines.add(join("Just-Tiers " + snapshot.modVersion(),
                "Minecraft " + snapshot.minecraftVersion(),
                "Fabric Loader " + snapshot.loaderVersion()));
        lines.add(join("nametags " + (snapshot.enabled() ? "on" : "off"),
                "mode " + snapshot.displayMode().id(),
                "cache TTL " + ttl(snapshot.cacheTtl())));
        lines.add(join("NovaTiers index " + snapshot.novaIndexedPlayers() + " players",
                "refresh every " + snapshot.novaRefreshMinutes() + "m"));
        for (SiteDiagnostics site : snapshot.sites()) {
            lines.add(site(site));
            // Only when there is one: an empty "last error: none" on all three sites is
            // three lines saying nothing, in the report most likely to be pasted.
            site.health().lastError()
                    .ifPresent(error -> lines.add(CONTINUATION + "last error: " + error));
        }
        return List.copyOf(lines);
    }

    /** The same report as one string, for the clipboard. */
    public static String asText(DebugSnapshot snapshot) {
        return String.join("\n", lines(snapshot));
    }

    private static String site(SiteDiagnostics site) {
        SiteHealth.Snapshot health = site.health();
        StringJoiner fields = new StringJoiner(" | ");
        fields.add(site.source().displayName() + ": " + gate(site.gate()));
        if (health.idle()) {
            // Nothing has been asked yet, so counts, ages and latencies would all be
            // zeroes dressed up as measurements.
            fields.add("no lookups yet");
        } else {
            fields.add(health.successes() + " ok, " + health.failures() + " failed");
            fields.add(outcomes(health));
            fields.add(latency(health));
        }
        fields.add(cache(site));
        return fields.toString();
    }

    /** When each of the two outcomes last happened, naming only the ones that have. */
    private static String outcomes(SiteHealth.Snapshot health) {
        StringJoiner outcomes = new StringJoiner(", ");
        outcomes.add(health.sinceLastSuccessNanos().isPresent()
                ? "last ok " + elapsed(health.sinceLastSuccessNanos().getAsLong()) + " ago"
                : "never succeeded");
        health.sinceLastFailureNanos().ifPresent(
                nanos -> outcomes.add("last fail " + elapsed(nanos) + " ago"));
        return outcomes.toString();
    }

    private static String latency(SiteHealth.Snapshot health) {
        return "latency " + duration(health.lastLatencyNanos())
                + " last, " + duration(health.meanLatencyNanos()) + " mean";
    }

    private static String cache(SiteDiagnostics site) {
        return site.cachedPlayers() + " cached, "
                + site.pendingLookups() + " in flight, "
                + site.playersAwaitingRetry() + " retrying";
    }

    /**
     * The gate in a few words. A run of failures is named even while the gate is still
     * open, because a site at 7 of 8 reads as healthy right up until it is not.
     */
    private static String gate(SiteGate.Status gate) {
        if (!gate.closed()) {
            return gate.consecutiveFailures() == 0
                    ? "ok"
                    : "ok (" + gate.consecutiveFailures() + " failures in a row)";
        }
        if (gate.probing()) {
            return "PAUSED, probe in flight";
        }
        return gate.reopensInNanos() <= 0
                ? "PAUSED, retrying now"
                : "PAUSED, retrying in " + elapsed(gate.reopensInNanos());
    }

    /** An age or a wait, to one unit: past a minute, the seconds are noise. */
    private static String elapsed(long nanos) {
        long seconds = TimeUnit.NANOSECONDS.toSeconds(Math.max(0, nanos));
        if (seconds < 60) {
            return seconds + "s";
        }
        if (seconds < 3600) {
            return (seconds / 60) + "m";
        }
        return (seconds / 3600) + "h";
    }

    /**
     * A latency, to a resolution worth reading: whole milliseconds below a second, and
     * one decimal of a second above it, where the difference between 4.0s and 4.2s is not
     * the point but the difference between 4s and 40s very much is.
     */
    private static String duration(OptionalLong nanos) {
        if (nanos.isEmpty()) {
            return "n/a";
        }
        long value = Math.max(0, nanos.getAsLong());
        long millis = TimeUnit.NANOSECONDS.toMillis(value);
        return millis < 1000
                ? millis + "ms"
                : String.format(Locale.ROOT, "%.1fs", value / 1_000_000_000.0);
    }

    private static String ttl(java.time.Duration ttl) {
        return ttl == null || ttl.isZero() || ttl.isNegative()
                ? "off (kept for the session)"
                : ttl.toMinutes() + "m";
    }

    private static String join(String... fields) {
        StringJoiner joiner = new StringJoiner(" | ");
        for (String field : fields) {
            joiner.add(field);
        }
        return joiner.toString();
    }

    private DebugReport() {
    }
}
