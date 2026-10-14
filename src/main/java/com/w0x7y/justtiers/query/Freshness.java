package com.w0x7y.justtiers.query;

import java.time.Duration;
import java.util.Objects;

/**
 * How current one site's answer is.
 *
 * @param age           time since the site produced this answer. A NovaTiers answer is as old
 *                      as the bulk index it came from, not the moment this player was read.
 * @param stale         the answer is past the user's cache interval, a refresh was requested, or
 *                      the last refresh failed. Its placements are still the latest known ones.
 * @param refreshing    a replacement request is in flight; a later peek may return a newer answer
 * @param refreshFailed the latest attempt to replace this answer failed, so it is being retained
 */
public record Freshness(Duration age, boolean stale, boolean refreshing, boolean refreshFailed) {
    public Freshness {
        Objects.requireNonNull(age, "age");
        if (age.isNegative()) throw new IllegalArgumentException("age must not be negative");
    }
}
