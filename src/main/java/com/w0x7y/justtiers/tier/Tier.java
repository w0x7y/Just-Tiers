package com.w0x7y.justtiers.tier;

import java.util.Locale;
import java.util.Optional;

/**
 * A single tier placement. {@code level} is 1-5, with high, middle or low division.
 * Ordering is by {@link #rank()} ascending, so HT1 sorts first and LT5 last.
 */
public record Tier(int level, Division division, boolean retired) implements Comparable<Tier> {

    public enum Division { HIGH, MIDDLE, LOW }

    public Tier(int level, boolean high, boolean retired) {
        this(level, high ? Division.HIGH : Division.LOW, retired);
    }

    public Tier {
        if (level < 1 || level > 5) {
            throw new IllegalArgumentException("tier level out of range: " + level);
        }
        java.util.Objects.requireNonNull(division, "division");
    }

    public boolean high() {
        return division == Division.HIGH;
    }

    /** Lower is better: HT1, MT1, LT1, HT2, ... LT5. */
    public int rank() {
        return (level - 1) * 3 + division.ordinal();
    }

    public String label() {
        String prefix = switch (division) {
            case HIGH -> "HT";
            case MIDDLE -> "MT";
            case LOW -> "LT";
        };
        return (retired ? "R" : "") + prefix + level;
    }

    /** Parses HT, MT and LT labels, optionally R-prefixed. */
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
        if (hl != 'H' && hl != 'M' && hl != 'L') {
            return Optional.empty();
        }
        int level = s.charAt(2) - '0';
        if (level < 1 || level > 5) {
            return Optional.empty();
        }
        Division division = switch (hl) {
            case 'H' -> Division.HIGH;
            case 'M' -> Division.MIDDLE;
            default -> Division.LOW;
        };
        return Optional.of(new Tier(level, division, retired));
    }

    /** Builds a tier from the PvPTiers/SubTiers wire format, where pos 0 means high. */
    public static Tier fromRanking(int tier, int pos, boolean retired) {
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
