package com.w0x7y.justtiers.gui.state;

import com.w0x7y.justtiers.gui.state.LookupFocus.Cell;
import com.w0x7y.justtiers.gui.state.LookupFocus.Done;
import com.w0x7y.justtiers.gui.state.LookupFocus.History;
import com.w0x7y.justtiers.gui.state.LookupFocus.Key;
import com.w0x7y.justtiers.gui.state.LookupFocus.Link;
import com.w0x7y.justtiers.gui.state.LookupFocus.Name;
import com.w0x7y.justtiers.gui.state.LookupFocus.Search;
import com.w0x7y.justtiers.tier.Source;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.*;

/** A relayout keeps the focus on the same control, or the nearest one that still exists. */
class LookupFocusTest {

    private static final List<String> HISTORY = List.of("Steve", "Alex", "Notch");
    private static final int LINKS = Source.ALL.size();

    /** Every row with the same number of cells. */
    private static ToIntFunction<Source> rows(int cells) {
        return source -> cells;
    }

    /** One row with its own count, every other row with ten cells. */
    private static ToIntFunction<Source> rows(Source source, int cells) {
        Map<Source, Integer> counts = Map.of(source, cells);
        return row -> counts.getOrDefault(row, 10);
    }

    private static Key restore(Key key) {
        return LookupFocus.restore(key, HISTORY, rows(10), LINKS);
    }

    @Test
    void theSearchAndDoneControlsAlwaysKeepTheFocus() {
        assertEquals(new Name(), restore(new Name()));
        assertEquals(new Search(), restore(new Search()));
        assertEquals(new Done(), restore(new Done()));
        // Even with no history, cells or links left on the screen.
        assertEquals(new Search(), LookupFocus.restore(new Search(), List.of(), rows(0), 0));
        assertEquals(new Done(), LookupFocus.restore(new Done(), List.of(), rows(0), 0));
        assertEquals(new Name(), LookupFocus.restore(new Name(), List.of(), rows(0), 0));
    }

    @Test
    void aCellOrLinkStillOnScreenKeepsTheFocus() {
        assertEquals(new Cell(Source.SUBTIERS, 0), restore(new Cell(Source.SUBTIERS, 0)));
        assertEquals(new Cell(Source.NOVATIERS, 9), restore(new Cell(Source.NOVATIERS, 9)));
        assertEquals(new Link(0), restore(new Link(0)));
        assertEquals(new Link(LINKS - 1), restore(new Link(LINKS - 1)));
    }

    @Test
    void aRecentNameKeepsTheFocusWhereverItMoved() {
        // A lookup moves its name to the front: the focus follows the name, not the slot.
        assertEquals(new History("Notch"), LookupFocus.restore(new History("Notch"),
                List.of("Notch", "Steve", "Alex"), rows(10), LINKS));
        assertEquals(OptionalInt.of(0), LookupFocus.historySlot("Notch", List.of("Notch", "Steve")));
    }

    @Test
    void aRecentNameIsMatchedWithoutCaseAndTakesItsShownSpelling() {
        // Looking a name up again under other casing moves it to the front in that casing.
        assertEquals(new History("NOTCH"), LookupFocus.restore(new History("Notch"),
                List.of("NOTCH", "Steve"), rows(10), LINKS));
        assertEquals(OptionalInt.of(1), LookupFocus.historySlot("notch", List.of("Steve", "NOTCH")));
    }

    @Test
    void aRecentNameNoLongerShownFallsBackToTheNameField() {
        // The strip shows only the names that fit, so a name can drop out of it.
        assertEquals(new Name(), LookupFocus.restore(new History("Notch"),
                List.of("Steve", "Alex"), rows(10), LINKS));
        assertEquals(new Name(), LookupFocus.restore(new History("Notch"), List.of(), rows(10), LINKS));
        assertEquals(OptionalInt.empty(), LookupFocus.historySlot("Notch", List.of("Steve")));
    }

    /**
     * The fixed bug: a new lookup whose answer holds fewer discovered cells shrinks the
     * row past the focused cell. No control used to get the focus; now the row's last
     * cell does.
     */
    @Test
    void aCellPastTheShrunkRowFallsBackToTheRowsLastCell() {
        assertEquals(new Cell(Source.PVPTIERS, 7),
                LookupFocus.restore(new Cell(Source.PVPTIERS, 11), HISTORY, rows(Source.PVPTIERS, 8), LINKS));
        assertEquals(new Cell(Source.PVPTIERS, 7),
                LookupFocus.restore(new Cell(Source.PVPTIERS, 8), HISTORY, rows(Source.PVPTIERS, 8), LINKS));
        // Only the focused cell's own row decides.
        assertEquals(new Cell(Source.SUBTIERS, 0),
                LookupFocus.restore(new Cell(Source.SUBTIERS, 4), HISTORY, rows(Source.SUBTIERS, 1), LINKS));
        assertEquals(new Cell(Source.NOVATIERS, 4),
                LookupFocus.restore(new Cell(Source.NOVATIERS, 4), HISTORY, rows(Source.SUBTIERS, 1), LINKS));
    }

    @Test
    void aCellInARowWithNoCellsFallsBackToTheNameField() {
        assertEquals(new Name(),
                LookupFocus.restore(new Cell(Source.NOVATIERS, 0), HISTORY, rows(Source.NOVATIERS, 0), LINKS));
    }

    @Test
    void aLinkPastTheFooterFallsBackToTheLastLinkThenTheNameField() {
        assertEquals(new Link(1), LookupFocus.restore(new Link(4), HISTORY, rows(10), 2));
        assertEquals(new Name(), LookupFocus.restore(new Link(0), HISTORY, rows(10), 0));
    }
}
