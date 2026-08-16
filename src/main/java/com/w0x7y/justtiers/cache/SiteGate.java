package com.w0x7y.justtiers.cache;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * Whether a whole leaderboard is worth asking right now.
 *
 * <p>A per-player retry delay is not enough on its own: a lobby of two hundred players
 * whose lookups all fail still sends two hundred requests every time those delays expire.
 * This counts failures across every player of one site and, past a threshold, stops
 * asking it at all for a while.
 *
 * <p>Reopening is done with a single probe rather than by letting everything through at
 * once, so a site that is still down costs one request to find that out instead of a
 * fresh flood. A probe that fails doubles the pause, up to a cap; a success clears
 * everything, because the site has just demonstrated it is fine.
 *
 * <p>Touched from the render thread and from HTTP callbacks, so the state is guarded.
 * The lock is uncontended in the normal case and never held across a request.
 */
public final class SiteGate {

    private final int threshold;
    private final long basePauseNanos;
    private final long maxPauseNanos;
    private final LongSupplier clock;

    private int consecutiveFailures;
    private long pauseNanos;
    private long openAtNanos;
    /** True once a probe has been let out and before it has reported back. */
    private boolean probing;
    private boolean closed;

    public SiteGate(int threshold, Duration basePause, Duration maxPause, LongSupplier clock) {
        this.threshold = Math.max(1, threshold);
        this.basePauseNanos = Math.max(1, basePause.toNanos());
        this.maxPauseNanos = Math.max(this.basePauseNanos, maxPause.toNanos());
        this.clock = clock;
    }

    /**
     * @return whether a request may go out now. While the site is closed this is false
     *         for everyone; when the pause expires exactly one caller gets true, and the
     *         rest wait for that probe to report back.
     */
    public synchronized boolean allowRequest() {
        if (!closed) {
            return true;
        }
        if (probing) {
            return false;
        }
        if (clock.getAsLong() - openAtNanos < 0) {
            return false;
        }
        probing = true;
        return true;
    }

    /** The site answered. Everything it was holding against it is forgotten. */
    public synchronized void recordSuccess() {
        consecutiveFailures = 0;
        pauseNanos = 0;
        closed = false;
        probing = false;
    }

    /** The site failed. Enough of these in a row and it stops being asked. */
    public synchronized void recordFailure() {
        if (probing) {
            // The probe was the test, and it failed: wait longer before the next one.
            probing = false;
            pause();
            return;
        }
        if (consecutiveFailures < Integer.MAX_VALUE) {
            consecutiveFailures++;
        }
        if (!closed && consecutiveFailures >= threshold) {
            pause();
        }
    }

    private void pause() {
        pauseNanos = pauseNanos == 0
                ? basePauseNanos
                : Math.min(maxPauseNanos, pauseNanos * 2);
        openAtNanos = clock.getAsLong() + pauseNanos;
        closed = true;
    }
}
