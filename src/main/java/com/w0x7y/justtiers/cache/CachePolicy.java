package com.w0x7y.justtiers.cache;

import java.time.Duration;

/**
 * How long the cache trusts an answer, and how hard it tries after a failure. Gathered
 * into one record so {@link TierCache} takes a policy rather than seven parameters, and
 * so a test can change one rule without restating the rest.
 *
 * <p>Only {@code ttl} is exposed to users, as {@code tierCacheMinutes}. The retry rules
 * are the mod being a polite guest on someone else's API; they are not a preference, and
 * seven knobs nobody turns would be worse than none.
 */
public record CachePolicy(Duration ttl,
                          Duration baseRetry,
                          Duration maxRetry,
                          double retryJitter,
                          int siteFailureThreshold,
                          Duration basePause,
                          Duration maxPause) {

    /** An hour is long enough that nothing re-fetches often, short enough that a
     *  player tested mid-session stops reading as untested before they log off. */
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(60);

    public static final CachePolicy DEFAULT = new CachePolicy(
            DEFAULT_TTL,
            Duration.ofSeconds(60),
            Duration.ofMinutes(16),
            0.25,
            8,
            Duration.ofSeconds(30),
            Duration.ofMinutes(4));

    public CachePolicy {
        ttl = ttl == null || ttl.isNegative() ? DEFAULT_TTL : ttl;
    }

    public CachePolicy withTtl(Duration ttl) {
        return new CachePolicy(ttl, baseRetry, maxRetry, retryJitter,
                siteFailureThreshold, basePause, maxPause);
    }

    public CachePolicy withBaseRetry(Duration baseRetry) {
        return new CachePolicy(ttl, baseRetry, maxRetry, retryJitter,
                siteFailureThreshold, basePause, maxPause);
    }

    /** Zero disables expiry: answers are kept for the whole session, as they once were. */
    public boolean expires() {
        return !ttl.isZero();
    }

    public Backoff backoff() {
        return new Backoff(baseRetry, maxRetry, retryJitter);
    }
}
