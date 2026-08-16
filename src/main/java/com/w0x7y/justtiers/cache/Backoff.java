package com.w0x7y.justtiers.cache;

import java.time.Duration;
import java.util.function.DoubleSupplier;

/**
 * How long to leave a failing lookup alone. The wait doubles with each consecutive
 * failure and stops at a cap, so a site that is briefly unhappy is retried soon and one
 * that is properly down is not asked again every minute for an hour.
 *
 * <p>Jitter spreads the retries out. Without it, a lobby whose lookups all failed in the
 * same frame would retry in the same frame too, for as long as the outage lasts —
 * turning one bad moment into a repeating thundering herd.
 *
 * <p>Pure: randomness arrives as a supplier of a number in {@code [0, 1)}, which is what
 * lets "the wait doubles" and "jitter stays inside its bounds" be unit tests.
 */
public final class Backoff {

    private final long baseNanos;
    private final long maxNanos;
    private final double jitter;

    /**
     * @param jitter the fraction either side of the delay that the wait may land in.
     *               {@code 0.25} means 75%–125% of it; {@code 0} disables jitter.
     */
    public Backoff(Duration base, Duration max, double jitter) {
        this.baseNanos = Math.max(1, base.toNanos());
        this.maxNanos = Math.max(this.baseNanos, max.toNanos());
        this.jitter = Math.clamp(jitter, 0.0, 1.0);
    }

    /**
     * The wait after {@code failures} consecutive failures, in nanoseconds. Always
     * positive: a zero wait would be an immediate retry, which is the thing this exists
     * to prevent.
     */
    public long delayAfter(int failures, DoubleSupplier random) {
        int consecutive = Math.max(1, failures);
        long delay = maxNanos;
        // Shifting rather than pow, and stopping the moment the cap is passed, so a
        // failure count in the thousands cannot overflow into a very short wait.
        if (consecutive - 1 < Long.numberOfLeadingZeros(baseNanos)) {
            delay = Math.min(maxNanos, baseNanos << (consecutive - 1));
        }
        if (jitter == 0.0) {
            return delay;
        }
        double factor = 1.0 + jitter * (2.0 * random.getAsDouble() - 1.0);
        return Math.max(1, Math.round(delay * factor));
    }
}
