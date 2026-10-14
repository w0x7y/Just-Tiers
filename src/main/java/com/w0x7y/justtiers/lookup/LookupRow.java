package com.w0x7y.justtiers.lookup;

import com.w0x7y.justtiers.tier.Gamemodes;
import com.w0x7y.justtiers.tier.Source;

import java.util.Optional;

/**
 * What one site's row in the lookup screen shows, decided once so drawing, focusable
 * controls and narration cannot disagree about it.
 */
public final class LookupRow {

    public enum State {
        /** The site has not answered yet: there is no section. */
        PENDING,
        /** The site could not be reached, so its row says so instead of showing cells. */
        UNAVAILABLE,
        /** The site answered, ranked or unranked, and its row shows cells. */
        ANSWERED
    }

    public static State state(Optional<LookupSection> section) {
        if (section.isEmpty()) return State.PENDING;
        return section.get().status() == LookupSection.Status.UNAVAILABLE
                ? State.UNAVAILABLE : State.ANSWERED;
    }

    /**
     * How many cells the row reserves: one per registered gamemode before the site
     * answers or when it is unavailable, so the row does not move as answers arrive, and
     * every cell of an answer that holds more, such as discovered gamemodes.
     */
    public static int cellCount(Source source, Optional<LookupSection> section) {
        return Math.max(Gamemodes.of(source).size(), shownCells(section));
    }

    /** How many of the row's cells hold an answer the player can focus and read. */
    public static int shownCells(Optional<LookupSection> section) {
        return state(section) == State.ANSWERED ? section.get().cells().size() : 0;
    }

    private LookupRow() {
    }
}
