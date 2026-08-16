package com.w0x7y.justtiers.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackoffTest {

    private static final Duration BASE = Duration.ofSeconds(60);
    private static final Duration MAX = Duration.ofMinutes(16);

    /** No jitter, so the doubling is visible on its own. */
    private static Backoff exact() {
        return new Backoff(BASE, MAX, 0.0);
    }

    private static long seconds(long nanos) {
        return Duration.ofNanos(nanos).toSeconds();
    }

    @Test
    void theFirstFailureWaitsTheBaseDelay() {
        assertEquals(60, seconds(exact().delayAfter(1, () -> 0.5)));
    }

    @Test
    void eachConsecutiveFailureDoublesTheWait() {
        Backoff backoff = exact();
        assertEquals(60, seconds(backoff.delayAfter(1, () -> 0.5)));
        assertEquals(120, seconds(backoff.delayAfter(2, () -> 0.5)));
        assertEquals(240, seconds(backoff.delayAfter(3, () -> 0.5)));
        assertEquals(480, seconds(backoff.delayAfter(4, () -> 0.5)));
    }

    @Test
    void theWaitStopsGrowingAtTheCap() {
        Backoff backoff = exact();
        assertEquals(960, seconds(backoff.delayAfter(5, () -> 0.5)));
        assertEquals(960, seconds(backoff.delayAfter(6, () -> 0.5)));
        assertEquals(960, seconds(backoff.delayAfter(50, () -> 0.5)));
    }

    @Test
    void aRidiculousFailureCountDoesNotOverflowIntoAShortWait() {
        // 2^n on a long overflows well before n = 1000; the cap must survive it.
        assertEquals(960, seconds(exact().delayAfter(1000, () -> 0.5)));
        assertEquals(960, seconds(exact().delayAfter(Integer.MAX_VALUE, () -> 0.5)));
    }

    @Test
    void jitterSpreadsTheWaitAroundTheDelay() {
        Backoff backoff = new Backoff(BASE, MAX, 0.25);

        // 0.0 and 1.0 are the ends of the random range: 60s +/- 25%.
        assertEquals(45, seconds(backoff.delayAfter(1, () -> 0.0)));
        assertEquals(75, seconds(backoff.delayAfter(1, () -> 1.0)));
        assertEquals(60, seconds(backoff.delayAfter(1, () -> 0.5)));
    }

    @Test
    void jitterNeverProducesANonPositiveWait() {
        Backoff backoff = new Backoff(BASE, MAX, 1.0);
        assertTrue(backoff.delayAfter(1, () -> 0.0) > 0);
    }

    @Test
    void aFailureCountBelowOneIsTreatedAsTheFirst() {
        assertEquals(60, seconds(exact().delayAfter(0, () -> 0.5)));
        assertEquals(60, seconds(exact().delayAfter(-3, () -> 0.5)));
    }
}
