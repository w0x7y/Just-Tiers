package com.w0x7y.justtiers.scan;

import com.w0x7y.justtiers.api.PlayerRef;
import com.w0x7y.justtiers.lookup.LookupSection;
import com.w0x7y.justtiers.tier.Source;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * One player's line in a scan: who they are, what each site has said about them so far,
 * and what that is worth. A row is rebuilt rather than mutated every time an answer
 * lands, so the list the screen draws is always a consistent snapshot.
 *
 * <p>A site missing from {@code sections} has not answered yet, which is different from
 * a site that answered and had nothing: the first draws as still loading, the second as
 * a row of dashes.
 */
public record ScanRow(PlayerRef player,
                      Map<Source, LookupSection> sections,
                      int points,
                      boolean complete) {

    public static ScanRow of(PlayerRef player, Map<Source, LookupSection> sections) {
        Map<Source, LookupSection> copy = new EnumMap<>(Source.class);
        copy.putAll(sections);
        return new ScanRow(player, Collections.unmodifiableMap(copy),
                TierPoints.total(copy.values()), copy.size() == Source.ALL.size());
    }

    /** Empty while that site is still being waited on. */
    public Optional<LookupSection> section(Source source) {
        return Optional.ofNullable(sections.get(source));
    }
}
