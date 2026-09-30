package com.w0x7y.justtiers.api;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Converts HTTP Retry-After to a bounded delay; malformed hints still pause rate-limited requests. */
public final class RetryAfter {
    private static final Duration FALLBACK = Duration.ofSeconds(60);
    private static final Duration MAXIMUM = Duration.ofDays(1);

    public static Duration parse(String value, Instant now) {
        if (value == null) return FALLBACK;
        String trimmed = value.trim();
        try {
            if (trimmed.matches("[0-9]+")) {
                return Duration.ofSeconds(new BigInteger(trimmed).min(BigInteger.valueOf(MAXIMUM.toSeconds())).longValue());
            }
            Duration wait = Duration.between(now, ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
            return wait.isNegative() ? Duration.ZERO : wait.compareTo(MAXIMUM) > 0 ? MAXIMUM : wait;
        } catch (RuntimeException invalid) { return FALLBACK; }
    }
    private RetryAfter() { }
}
