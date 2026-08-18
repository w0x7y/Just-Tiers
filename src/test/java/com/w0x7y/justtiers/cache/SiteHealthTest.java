package com.w0x7y.justtiers.cache;

import com.w0x7y.justtiers.api.TierLookupException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiteHealthTest {

    private final AtomicLong clock = new AtomicLong();

    private SiteHealth health() {
        return new SiteHealth(clock::get);
    }

    private void advance(Duration by) {
        clock.addAndGet(by.toNanos());
    }

    @Test
    void anUntouchedSiteReportsNothing() {
        SiteHealth.Snapshot snapshot = health().snapshot();

        assertTrue(snapshot.idle());
        assertEquals(0, snapshot.successes());
        assertEquals(0, snapshot.failures());
        assertTrue(snapshot.sinceLastSuccessNanos().isEmpty());
        assertTrue(snapshot.sinceLastFailureNanos().isEmpty());
        assertTrue(snapshot.lastError().isEmpty());
        // No completed request means no latency to average, rather than an average of zero.
        assertTrue(snapshot.lastLatencyNanos().isEmpty());
        assertTrue(snapshot.meanLatencyNanos().isEmpty());
    }

    @Test
    void agesAreMeasuredFromWhenTheOutcomeHappened() {
        SiteHealth health = health();
        health.recordSuccess(Duration.ofMillis(120).toNanos());
        advance(Duration.ofMinutes(5));

        SiteHealth.Snapshot snapshot = health.snapshot();
        assertFalse(snapshot.idle());
        assertEquals(Duration.ofMinutes(5).toNanos(),
                snapshot.sinceLastSuccessNanos().getAsLong());
        assertTrue(snapshot.sinceLastFailureNanos().isEmpty(),
                "a site that has never failed must not report an age for it");
    }

    @Test
    void bothOutcomesAreRememberedSeparately() {
        SiteHealth health = health();
        health.recordSuccess(Duration.ofMillis(100).toNanos());
        advance(Duration.ofSeconds(30));
        health.recordFailure(Duration.ofSeconds(10).toNanos(), new TierLookupException("HTTP 503"));
        advance(Duration.ofSeconds(15));

        SiteHealth.Snapshot snapshot = health.snapshot();
        assertEquals(1, snapshot.successes());
        assertEquals(1, snapshot.failures());
        assertEquals(Duration.ofSeconds(45).toNanos(),
                snapshot.sinceLastSuccessNanos().getAsLong());
        assertEquals(Duration.ofSeconds(15).toNanos(),
                snapshot.sinceLastFailureNanos().getAsLong());
    }

    @Test
    void theMeanCoversFailuresToo() {
        SiteHealth health = health();
        health.recordSuccess(Duration.ofMillis(100).toNanos());
        health.recordFailure(Duration.ofMillis(300).toNanos(), new TierLookupException("nope"));

        SiteHealth.Snapshot snapshot = health.snapshot();
        // A site that times out is slow, and a mean that ignored that would hide it.
        assertEquals(Duration.ofMillis(200).toNanos(), snapshot.meanLatencyNanos().getAsLong());
        assertEquals(Duration.ofMillis(300).toNanos(), snapshot.lastLatencyNanos().getAsLong());
    }

    @Test
    void aLaterSuccessDoesNotEraseTheLastError() {
        SiteHealth health = health();
        health.recordFailure(1, new TierLookupException("HTTP 503"));
        health.recordSuccess(1);

        // The site works again, but why it stopped is the whole reason a report was run.
        assertEquals("TierLookupException: HTTP 503", health.snapshot().lastError().orElseThrow());
    }

    @Test
    void theFutureWrapperIsUnwrapped() {
        assertEquals("TierLookupException: HTTP 503",
                SiteHealth.describe(new CompletionException(new TierLookupException("HTTP 503"))));
    }

    @Test
    void anExceptionWithNoMessageIsStillNamed() {
        assertEquals("IllegalStateException", SiteHealth.describe(new IllegalStateException()));
    }

    @Test
    void aWrapperWithNothingInsideIsReportedAsItself() {
        assertEquals("CompletionException: wrapped",
                SiteHealth.describe(new CompletionException("wrapped", null)));
    }

    @Test
    void aSprawlingErrorIsCutDownToOneLine() {
        String body = "<html>\n  <body>error</body>\n</html>".repeat(20);
        String described = SiteHealth.describe(new TierLookupException(body));

        assertEquals(120, described.length(), "a paste must not be one exception message long");
        assertTrue(described.endsWith("..."));
        assertFalse(described.contains("\n"), "newlines would break one-line-per-fact");
    }

    @Test
    void aShortErrorIsLeftAlone() {
        String described = SiteHealth.describe(new TierLookupException("HTTP 429"));

        assertEquals("TierLookupException: HTTP 429", described);
        assertFalse(described.endsWith("..."));
    }
}
