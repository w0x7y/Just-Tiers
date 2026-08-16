package com.w0x7y.justtiers.gui.layout;

import java.util.OptionalInt;

/**
 * Where the rows of a scan sit, and which of them a scrolled viewport can see. Rows are
 * a fixed height, so all of this is arithmetic — and being arithmetic rather than
 * drawing, "a click in the gap below the last row selects nothing" is a unit test.
 *
 * <p>All y coordinates are relative to the viewport's own top-left, and a scroll offset
 * is how far the content has moved up past it.
 */
public final class ScanLayout {

    private final int rowCount;
    private final int rowHeight;
    private final int viewportHeight;

    private ScanLayout(int rowCount, int rowHeight, int viewportHeight) {
        this.rowCount = Math.max(0, rowCount);
        this.rowHeight = Math.max(1, rowHeight);
        this.viewportHeight = Math.max(0, viewportHeight);
    }

    public static ScanLayout of(int rowCount, int rowHeight, int viewportHeight) {
        return new ScanLayout(rowCount, rowHeight, viewportHeight);
    }

    public int rowCount() {
        return rowCount;
    }

    public int rowHeight() {
        return rowHeight;
    }

    public int contentHeight() {
        return rowCount * rowHeight;
    }

    public int maxScroll() {
        return Math.max(0, contentHeight() - viewportHeight);
    }

    public int clampScroll(int scroll) {
        return Math.clamp(scroll, 0, maxScroll());
    }

    /** The first row with any pixel on screen; it may be clipped at the top. */
    public int firstVisible(int scroll) {
        return Math.clamp(clampScroll(scroll) / rowHeight, 0, rowCount);
    }

    /** One past the last row with any pixel on screen. */
    public int lastVisible(int scroll) {
        int bottom = clampScroll(scroll) + viewportHeight;
        return Math.clamp(Math.ceilDiv(bottom, rowHeight), 0, rowCount);
    }

    public int yOf(int index, int scroll) {
        return index * rowHeight - clampScroll(scroll);
    }

    /** The row under a point in the viewport, if the point is on a row at all. */
    public OptionalInt indexAt(int y, int scroll) {
        if (y < 0 || y >= viewportHeight) {
            return OptionalInt.empty();
        }
        int index = (y + clampScroll(scroll)) / rowHeight;
        return index < rowCount ? OptionalInt.of(index) : OptionalInt.empty();
    }

    /** Equal columns across the available width, with a gap between each pair. */
    public static int columnWidth(int available, int columns, int gap) {
        if (columns < 1) {
            return 0;
        }
        return Math.max(0, (available - (columns - 1) * gap) / columns);
    }

    public static int columnLeft(int left, int columnWidth, int gap, int index) {
        return left + index * (columnWidth + gap);
    }
}
