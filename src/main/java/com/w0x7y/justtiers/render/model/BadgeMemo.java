package com.w0x7y.justtiers.render.model;

import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * One player's rendered badge, rebuilt only when what it is built from changes. A nametag
 * asks for its badge every frame, but the badge moves only when settings change or a
 * site's answer lands, so building it from scratch each time is wasted work.
 *
 * <p>Every call still peeks every site, because peeking is what admits lookups and
 * refreshes. The settings and each answer are compared by identity: the config hands out
 * one settings instance until a setting changes, and the cache one immutable answer map
 * until a new answer arrives. An equal but new instance only costs a rebuild.
 *
 * <p>The badge depends only on the settings and the answers, so those are the whole key;
 * keeping one memo per player is for hits, not correctness. Safe on any thread, since the
 * last build is one immutable snapshot and two threads that race simply both build.
 */
public final class BadgeMemo<T> {

    private record Built<T>(NametagSettings settings, Map<Source, Map<String, Tier>> answers, T rendered) {

        boolean from(NametagSettings settings, Map<Source, Map<String, Tier>> answers) {
            if (this.settings != settings) return false;
            for (Source source : Source.ALL) {
                if (this.answers.get(source) != answers.get(source)) return false;
            }
            return true;
        }
    }

    private final Function<Badge, T> render;
    private volatile Built<T> last;

    /** {@code render} turns a badge, possibly {@link Badge#NONE}, into what {@link #get} returns. */
    public BadgeMemo(Function<Badge, T> render) {
        this.render = render;
    }

    /** The rendered form of {@link Badge#forPlayer(TierView, UUID)}, reused while its inputs are the same. */
    public T get(TierView view, UUID uuid) {
        NametagSettings settings = view.settings();
        Map<Source, Map<String, Tier>> answers = Badge.answers(view, settings, uuid);
        Built<T> built = last;
        if (built == null || !built.from(settings, answers)) {
            built = new Built<>(settings, answers, render.apply(Badge.forPlayer(settings, answers)));
            last = built;
        }
        return built.rendered();
    }
}
