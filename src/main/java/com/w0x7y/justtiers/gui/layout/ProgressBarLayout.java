package com.w0x7y.justtiers.gui.layout;

import java.util.Locale;

/** Geometry and formatting for the download indicator. No Minecraft types. */
public final class ProgressBarLayout {

    /**
     * The bar stops just short of full while downloading. The total is last session's
     * download size and the leaderboard grows, so a download can legitimately exceed it;
     * holding at 99% is honest, whereas 100% would claim a completion that has not happened.
     */
    public static final double MAX_FRACTION = 0.99;

    /** Width of the sliding segment shown when the total is unknown. */
    public static final double MARQUEE_WIDTH_FRACTION = 0.25;

    private static final long MARQUEE_PERIOD_NANOS = 1_200_000_000L;

    public static double fraction(long bytesRead, long total) {
        if (total <= 0) {
            return 0.0;
        }
        return Math.min((double) bytesRead / total, MAX_FRACTION);
    }

    /**
     * Left edge of the sliding segment, as a fraction of the track, travelling from just
     * off the left edge to the right edge and wrapping. Driven by {@code System.nanoTime()}
     * rather than tick counts so it animates on the title screen, where nothing ticks.
     */
    public static double marqueeStart(long nanoTime) {
        double phase = (double) Math.floorMod(nanoTime, MARQUEE_PERIOD_NANOS) / MARQUEE_PERIOD_NANOS;
        return phase * (1.0 + MARQUEE_WIDTH_FRACTION) - MARQUEE_WIDTH_FRACTION;
    }

    public static String formatBytes(long bytes) {
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0);
    }

    /** Floors rather than rounds, so the bar never reads 100% before it is finished. */
    public static String formatPercent(double fraction) {
        return (int) Math.floor(fraction * 100) + "%";
    }

    private ProgressBarLayout() {
    }
}
