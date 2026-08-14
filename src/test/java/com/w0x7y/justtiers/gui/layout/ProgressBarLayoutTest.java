package com.w0x7y.justtiers.gui.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProgressBarLayoutTest {

    @Test
    void fractionIsTheRatioOfBytesToTotal() {
        assertEquals(0.5, ProgressBarLayout.fraction(500, 1_000), 1e-9);
    }

    @Test
    void fractionIsZeroWhenTheTotalIsUnknown() {
        assertEquals(0.0, ProgressBarLayout.fraction(500, 0), 1e-9);
    }

    @Test
    void fractionNeverReachesOneWhileDownloading() {
        // The leaderboard grows, so a download can outrun last session's size. The bar
        // holds just short of full rather than overflowing or claiming to be done.
        assertEquals(ProgressBarLayout.MAX_FRACTION,
                ProgressBarLayout.fraction(2_000, 1_000), 1e-9);
        assertEquals(ProgressBarLayout.MAX_FRACTION,
                ProgressBarLayout.fraction(1_000, 1_000), 1e-9);
    }

    @Test
    void marqueeStaysWithinItsTravel() {
        for (long nanos = 0; nanos < 5_000_000_000L; nanos += 7_000_000L) {
            double start = ProgressBarLayout.marqueeStart(nanos);
            assertTrue(start >= -ProgressBarLayout.MARQUEE_WIDTH_FRACTION,
                    "start " + start + " at " + nanos);
            assertTrue(start <= 1.0, "start " + start + " at " + nanos);
        }
    }

    @Test
    void marqueeWrapsAndHandlesNegativeClocks() {
        // System.nanoTime() has an arbitrary origin and may be negative.
        double start = ProgressBarLayout.marqueeStart(-1_234_567_890L);
        assertTrue(start >= -ProgressBarLayout.MARQUEE_WIDTH_FRACTION && start <= 1.0);
    }

    @Test
    void formatsBytesAsMegabytes() {
        assertEquals("1.7 MB", ProgressBarLayout.formatBytes(1_736_861));
        assertEquals("0.0 MB", ProgressBarLayout.formatBytes(0));
    }

    @Test
    void formatsPercentWithoutRoundingUp() {
        assertEquals("50%", ProgressBarLayout.formatPercent(0.509));
        assertEquals("99%", ProgressBarLayout.formatPercent(ProgressBarLayout.MAX_FRACTION));
        assertEquals("0%", ProgressBarLayout.formatPercent(0.0));
    }
}
