package com.w0x7y.justtiers.render.model;

import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A {@link TierView} with no game behind it: settings you set, answers you hand it, and
 * a record of which sites were actually asked.
 *
 * <p>That last part is the reason this is a class rather than a lambda. "Only the sites
 * the display mode reads get asked" is a rule about a call that does not appear in the
 * result, so the only way to assert it is to watch the seam.
 */
final class FakeTierView implements TierView {

    private NametagSettings settings;
    private final Map<Source, Map<String, Tier>> answers = new EnumMap<>(Source.class);
    private final List<Source> asked = new ArrayList<>();

    FakeTierView(NametagSettings settings) {
        this.settings = settings;
    }

    FakeTierView answering(Source source, Map<String, Tier> tiers) {
        answers.put(source, tiers);
        return this;
    }

    /** Live, like the real thing: a saved setting changes the next badge, not the app. */
    void settings(NametagSettings settings) {
        this.settings = settings;
    }

    /** The sites asked since the last {@link #forget()}, in the order they were asked. */
    List<Source> asked() {
        return List.copyOf(asked);
    }

    void forget() {
        asked.clear();
    }

    @Override
    public NametagSettings settings() {
        return settings;
    }

    @Override
    public Optional<Map<String, Tier>> peek(Source source, UUID uuid) {
        asked.add(source);
        return Optional.ofNullable(answers.get(source));
    }
}
