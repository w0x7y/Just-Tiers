package com.w0x7y.justtiers.gui.state;

import com.w0x7y.justtiers.tier.Source;

import java.util.List;
import java.util.OptionalInt;
import java.util.function.ToIntFunction;

/**
 * Which lookup-screen control keeps the focus when the screen lays its widgets out again.
 * The screen rebuilds its widgets when a row gains or loses cells and moves recent names
 * when the history changes; both drop the focused widget, so the screen records it as a
 * {@link Key} first and asks {@link #restore} which control to focus afterwards.
 *
 * <p>Minecraft-free: the screen keeps only the mapping between its widgets and keys.
 */
public final class LookupFocus {

    /** A focusable control, named by what it is rather than by the widget showing it. */
    public sealed interface Key {
    }

    /** The name field. */
    public record Name() implements Key {
    }

    /** The lookup button beside the name field. */
    public record Search() implements Key {
    }

    /** The Done button. */
    public record Done() implements Key {
    }

    /** A recent-lookup button, by the name it shows rather than by its slot. */
    public record History(String name) implements Key {
    }

    /** One cell of a site's row. */
    public record Cell(Source source, int index) implements Key {
    }

    /** A site's link in the footer. */
    public record Link(int index) implements Key {
    }

    /**
     * The control to focus after a relayout.
     *
     * <ul>
     *   <li>The name field, search and Done controls always exist and keep the focus.</li>
     *   <li>A recent name keeps the focus wherever it moved in the shown strip, matched
     *       without case. When it is no longer shown, the name field takes the focus.</li>
     *   <li>A cell keeps its index while the row still has it. When the row shrank past
     *       it, the row's last cell takes the focus, or the name field when the row has
     *       no cells.</li>
     *   <li>A link likewise keeps its index, falling back to the last link, then the name
     *       field.</li>
     * </ul>
     *
     * @param shownHistory the names the strip shows, in slot order; names that did not fit
     *                     are not shown and so cannot hold the focus.
     * @param rowCounts    how many cells each site's row has after the relayout.
     * @param linkCount    how many footer links there are after the relayout.
     */
    public static Key restore(Key key, List<String> shownHistory,
                              ToIntFunction<Source> rowCounts, int linkCount) {
        return switch (key) {
            case Name name -> name;
            case Search search -> search;
            case Done done -> done;
            case History history -> {
                OptionalInt slot = historySlot(history.name(), shownHistory);
                yield slot.isPresent() ? new History(shownHistory.get(slot.getAsInt())) : new Name();
            }
            case Cell cell -> {
                int count = rowCounts.applyAsInt(cell.source());
                if (cell.index() < count) yield cell;
                yield count > 0 ? new Cell(cell.source(), count - 1) : new Name();
            }
            case Link link -> {
                if (link.index() < linkCount) yield link;
                yield linkCount > 0 ? new Link(linkCount - 1) : new Name();
            }
        };
    }

    /** The first slot showing this name, ignoring case, or empty when none shows it. */
    public static OptionalInt historySlot(String name, List<String> shownHistory) {
        for (int i = 0; i < shownHistory.size(); i++) {
            if (shownHistory.get(i).equalsIgnoreCase(name)) return OptionalInt.of(i);
        }
        return OptionalInt.empty();
    }

    private LookupFocus() {
    }
}
