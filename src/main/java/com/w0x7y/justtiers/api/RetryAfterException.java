package com.w0x7y.justtiers.api;

import java.time.Duration;

/** A remote site's cooldown applies to every player, including explicit retries. */
public final class RetryAfterException extends TierLookupException {
    private final Duration delay;
    public RetryAfterException(String message, Duration delay) {
        super(message);
        this.delay = delay.isNegative() ? Duration.ZERO : delay.compareTo(Duration.ofDays(1)) > 0 ? Duration.ofDays(1) : delay;
    }
    public Duration delay() { return delay; }
}
