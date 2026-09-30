package com.w0x7y.justtiers.lookup;

import java.time.Duration;
import java.util.Optional;

/** One source's displayed answer, request settlement and freshness, read together. */
public record LookupResult(Optional<LookupSection> section, boolean complete, Optional<Freshness> freshness) {
    public enum RefreshStatus { FRESH, STALE, REFRESHING, REFRESH_FAILED }

    public record Freshness(Duration age, RefreshStatus status) { }
}
