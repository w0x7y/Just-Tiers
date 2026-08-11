package com.w0x7y.justtiers.tier;

import java.util.Locale;
import java.util.Optional;

/**
 * A single tier placement. {@code level} is 1-5, {@code high} distinguishes HT from LT.
 * Ordering is by {@link #rank()} ascending, so HT1 sorts first and LT5 last.
 */
public record Tier(int level, boolean high, boolean retired) implements Comparable<Tier> {

    public Tier {
        if (level < 1 || level > 5) {
            throw new IllegalArgumentException("tier level out of range: " + level);
        }
    }

    /** Lower is better. HT1 = 0, LT1 = 1, HT2 = 2, ... HT5 = 8, LT5 = 9. */
    public int rank() {
        return (level - 1) * 2 + (high ? 0 : 1);
    }

    public String label() {
        return (retired ? "R" : "") + (high ? "HT" : "LT") + level;
    }

    /** Parses NovaTiers-style strings: HT1..LT5, optionally R-prefixed. */
    public static Optional<Tier> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String s = raw.trim().toUpperCase(Locale.ROOT);
        boolean retired = false;
        if (s.startsWith("R")) {
            retired = true;
            s = s.substring(1);
        }
        if (s.length() != 3 || s.charAt(1) != 'T') {
            return Optional.empty();
        }
        char hl = s.charAt(0);
        if (hl != 'H' && hl != 'L') {
            return Optional.empty();
        }
        int level = s.charAt(2) - '0';
        if (level < 1 || level > 5) {
            return Optional.empty();
        }
        return Optional.of(new Tier(level, hl == 'H', retired));
    }

    /** Builds a tier from the MCTiers/SubTiers wire format, where pos 0 means high. */
    public static Tier fromMctiers(int tier, int pos, boolean retired) {
        return new Tier(tier, pos == 0, retired);
    }

    @Override
    public int compareTo(Tier other) {
        int byRank = Integer.compare(rank(), other.rank());
        if (byRank != 0) {
            return byRank;
        }
        return Boolean.compare(retired, other.retired);
    }
}
