package com.w0x7y.justtiers.lookup;

import com.w0x7y.justtiers.api.PlayerRef;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * The names this game session has looked up, most recent first, so a player can be
 * re-checked without retyping. Held only in memory: nothing here is ever written to
 * disk, and a restart starts with an empty history.
 *
 * <p>Only names that resolved to a player identity are kept. A typo, an invalid name or
 * a name Mojang could not answer for is not worth offering again. Names are compared
 * without case; a repeat moves the name to the front under its latest spelling.
 */
public final class LookupHistory {
    public static final int CAPACITY = 8;

    private final LinkedList<String> names = new LinkedList<>();

    /** Moves a name to the front, replacing any spelling of it already held. */
    public synchronized void record(String name) {
        if (name == null || name.isBlank()) return;
        String trimmed = name.trim();
        names.removeIf(existing -> existing.equalsIgnoreCase(trimmed));
        names.addFirst(trimmed);
        while (names.size() > CAPACITY) {
            names.removeLast();
        }
    }

    /**
     * Records the identity a lookup resolves to, under the name that identity carries.
     * A resolution that finds nobody, or fails, records nothing.
     */
    public void recordWhenResolved(CompletionStage<Optional<PlayerRef>> resolution) {
        resolution.thenAccept(found -> found.ifPresent(player -> record(player.name())));
    }

    /** A snapshot, most recent first. */
    public synchronized List<String> names() {
        return List.copyOf(names);
    }

    /**
     * Tab-completion candidates for a partly typed name: the online names first, then
     * recent names that are not already among them. Matching and de-duplication ignore
     * case; the first spelling seen is the one suggested.
     */
    public List<String> suggestions(String typed, Collection<String> online) {
        String prefix = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        Set<String> seen = new HashSet<>();
        List<String> suggestions = new ArrayList<>();
        for (Collection<String> group : List.of(online, names())) {
            for (String name : group) {
                String key = name.toLowerCase(Locale.ROOT);
                if (key.startsWith(prefix) && seen.add(key)) {
                    suggestions.add(name);
                }
            }
        }
        return suggestions;
    }
}
