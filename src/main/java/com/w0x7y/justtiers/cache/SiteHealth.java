package com.w0x7y.justtiers.cache;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.LongSupplier;

/**
 * What one site has actually done this session: how many lookups it answered, how many it
 * failed, how long they took, and what the last failure said.
 *
 * <p>Nothing here changes any behaviour. {@link SiteGate} and {@link Backoff} decide what
 * happens after a failure; this only remembers enough of it to answer "why is this player
 * showing no tiers?" without a debug build.
 *
 * <p>Ages rather than timestamps come out of {@link #snapshot()}: the clock is
 * {@code nanoTime}, which has no epoch and so no meaning outside a difference. That also
 * leaves the report's formatting a pure function of numbers a test can hand it.
 *
 * <p>Written from HTTP callbacks and read from the client thread, so it is synchronized.
 * The lock is uncontended in the normal case — one acquisition per completed request.
 */
public final class SiteHealth {

    /**
     * How much of a failure is worth keeping. A site answering with an HTML error page can
     * put the whole page in an exception message, and a debug report that is one paste is
     * the entire point of this class.
     */
    private static final int MAX_ERROR_LENGTH = 120;

    private final LongSupplier clock;

    private int successes;
    private int failures;
    private long lastSuccessNanos;
    private long lastFailureNanos;
    private boolean everSucceeded;
    private boolean everFailed;
    private String lastError;
    private long lastLatencyNanos;
    private long totalLatencyNanos;

    public SiteHealth(LongSupplier clock) {
        this.clock = clock;
    }

    public synchronized void recordSuccess(long latencyNanos) {
        successes++;
        lastSuccessNanos = clock.getAsLong();
        everSucceeded = true;
        recordLatency(latencyNanos);
    }

    public synchronized void recordFailure(long latencyNanos, Throwable error) {
        failures++;
        lastFailureNanos = clock.getAsLong();
        everFailed = true;
        lastError = describe(error);
        recordLatency(latencyNanos);
    }

    /**
     * A failed request took time too, and how long it took is the difference between a
     * refused connection and a site that timed out — which is exactly what a bug report
     * needs to distinguish.
     */
    private void recordLatency(long latencyNanos) {
        long latency = Math.max(0, latencyNanos);
        lastLatencyNanos = latency;
        totalLatencyNanos += latency;
    }

    public synchronized Snapshot snapshot() {
        long now = clock.getAsLong();
        int completed = successes + failures;
        return new Snapshot(
                successes,
                failures,
                everSucceeded ? OptionalLong.of(now - lastSuccessNanos) : OptionalLong.empty(),
                everFailed ? OptionalLong.of(now - lastFailureNanos) : OptionalLong.empty(),
                Optional.ofNullable(lastError),
                completed == 0 ? OptionalLong.empty() : OptionalLong.of(lastLatencyNanos),
                completed == 0
                        ? OptionalLong.empty()
                        : OptionalLong.of(totalLatencyNanos / completed));
    }

    /**
     * The one line a failure is worth in a paste.
     *
     * <p>The wrapper a {@link java.util.concurrent.CompletableFuture} puts around a failure
     * is unwrapped first: "CompletionException: TierLookupException: HTTP 503" says nothing
     * the second half does not, and the wrapper is the half that would be cut off by the
     * length cap.
     */
    static String describe(Throwable error) {
        if (error == null) {
            return "unknown error";
        }
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }

        String message = cause.getMessage();
        String described = message == null || message.isBlank()
                ? cause.getClass().getSimpleName()
                : cause.getClass().getSimpleName() + ": " + message;
        // Newlines and runs of spaces collapse: a report is one line per fact, and an
        // exception carrying a response body would otherwise break that.
        described = described.replaceAll("\\s+", " ").trim();
        return described.length() <= MAX_ERROR_LENGTH
                ? described
                : described.substring(0, MAX_ERROR_LENGTH - 3) + "...";
    }

    /**
     * One site's counters, frozen. Every duration is an age — nanoseconds since the thing
     * happened — and an empty optional means it has not happened yet.
     */
    public record Snapshot(int successes,
                           int failures,
                           OptionalLong sinceLastSuccessNanos,
                           OptionalLong sinceLastFailureNanos,
                           Optional<String> lastError,
                           OptionalLong lastLatencyNanos,
                           OptionalLong meanLatencyNanos) {

        /** True before the site has been asked anything at all. */
        public boolean idle() {
            return successes == 0 && failures == 0;
        }
    }
}
