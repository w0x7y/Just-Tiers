package com.w0x7y.justtiers.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiteGateTest {

    private static final int THRESHOLD = 8;
    private static final Duration PAUSE = Duration.ofSeconds(30);
    private static final Duration MAX_PAUSE = Duration.ofMinutes(4);

    private final AtomicLong clock = new AtomicLong();

    private SiteGate gate() {
        return new SiteGate(THRESHOLD, PAUSE, MAX_PAUSE, clock::get);
    }

    private void advance(Duration by) {
        clock.addAndGet(by.toNanos());
    }

    private static void fail(SiteGate gate, int times) {
        for (int i = 0; i < times; i++) {
            gate.recordFailure();
        }
    }

    @Test
    void aHealthySiteIsAlwaysOpen() {
        SiteGate gate = gate();
        for (int i = 0; i < 100; i++) {
            assertTrue(gate.allowRequest());
            gate.recordSuccess();
        }
    }

    @Test
    void scatteredFailuresBelowTheThresholdDoNotCloseIt() {
        SiteGate gate = gate();
        fail(gate, THRESHOLD - 1);
        assertTrue(gate.allowRequest());
    }

    @Test
    void theThresholdClosesTheSite() {
        SiteGate gate = gate();
        fail(gate, THRESHOLD);
        assertFalse(gate.allowRequest(), "a site that keeps failing must stop being asked");
    }

    @Test
    void aSuccessResetsTheRunOfFailures() {
        SiteGate gate = gate();
        fail(gate, THRESHOLD - 1);
        gate.recordSuccess();
        fail(gate, THRESHOLD - 1);
        assertTrue(gate.allowRequest(), "the run was broken, so the threshold was never met");
    }

    @Test
    void aClosedSiteStaysClosedForTheWholePause() {
        SiteGate gate = gate();
        fail(gate, THRESHOLD);

        advance(Duration.ofSeconds(29));
        for (int i = 0; i < 50; i++) {
            assertFalse(gate.allowRequest());
        }
    }

    @Test
    void thePauseEndsWithExactlyOneProbe() {
        SiteGate gate = gate();
        fail(gate, THRESHOLD);
        advance(PAUSE);

        assertTrue(gate.allowRequest(), "one request must be let through to test the water");
        assertFalse(gate.allowRequest(), "and only one, until it answers");
        assertFalse(gate.allowRequest());
    }

    @Test
    void aProbeThatSucceedsReopensTheSite() {
        SiteGate gate = gate();
        fail(gate, THRESHOLD);
        advance(PAUSE);

        assertTrue(gate.allowRequest());
        gate.recordSuccess();

        for (int i = 0; i < 20; i++) {
            assertTrue(gate.allowRequest());
        }
    }

    @Test
    void aProbeThatFailsPausesForLonger() {
        SiteGate gate = gate();
        fail(gate, THRESHOLD);
        advance(PAUSE);
        assertTrue(gate.allowRequest());
        gate.recordFailure();

        // The pause doubled, so the delay that was enough last time is not enough now.
        advance(PAUSE);
        assertFalse(gate.allowRequest());
        advance(PAUSE);
        assertTrue(gate.allowRequest());
    }

    @Test
    void thePauseStopsGrowingAtItsCap() {
        SiteGate gate = gate();
        fail(gate, THRESHOLD);
        for (int i = 0; i < 10; i++) {
            advance(MAX_PAUSE);
            assertTrue(gate.allowRequest());
            gate.recordFailure();
        }

        advance(MAX_PAUSE);
        assertTrue(gate.allowRequest(), "the pause must never exceed its cap");
    }

    @Test
    void statusReportsTheRunOfFailuresBeforeItClosesAnything() {
        SiteGate gate = gate();
        fail(gate, THRESHOLD - 1);

        SiteGate.Status status = gate.status();
        assertFalse(status.closed());
        assertEquals(THRESHOLD - 1, status.consecutiveFailures());
        assertEquals(0, status.reopensInNanos());
    }

    @Test
    void statusCountsDownTheRemainingPause() {
        SiteGate gate = gate();
        fail(gate, THRESHOLD);
        advance(Duration.ofSeconds(10));

        SiteGate.Status status = gate.status();
        assertTrue(status.closed());
        assertFalse(status.probing());
        assertEquals(Duration.ofSeconds(20).toNanos(), status.reopensInNanos());
    }

    @Test
    void statusNeverCountsBelowZero() {
        SiteGate gate = gate();
        fail(gate, THRESHOLD);
        advance(MAX_PAUSE);

        assertEquals(0, gate.status().reopensInNanos());
    }

    @Test
    void statusShowsAProbeInFlight() {
        SiteGate gate = gate();
        fail(gate, THRESHOLD);
        advance(PAUSE);
        assertTrue(gate.allowRequest());

        assertTrue(gate.status().probing());
    }

    @Test
    void askingForStatusDoesNotSpendTheProbe() {
        SiteGate gate = gate();
        fail(gate, THRESHOLD);
        advance(PAUSE);

        for (int i = 0; i < 10; i++) {
            assertFalse(gate.status().probing(), "reading the gate must not open it");
        }
        assertTrue(gate.allowRequest(), "the probe must still be there to hand out");
    }

    @Test
    void reopeningForgetsTheOldPauseLength() {
        SiteGate gate = gate();
        fail(gate, THRESHOLD);
        advance(PAUSE);
        assertTrue(gate.allowRequest());
        gate.recordFailure();
        advance(MAX_PAUSE);
        assertTrue(gate.allowRequest());
        gate.recordSuccess();

        // Back to healthy: the next outage starts from the short pause again.
        fail(gate, THRESHOLD);
        advance(PAUSE);
        assertTrue(gate.allowRequest());
    }
}
