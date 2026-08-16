package com.w0x7y.justtiers.gui.layout;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanLayoutTest {

    private static ScanLayout layout() {
        return ScanLayout.of(10, 20, 100);   // 200px of rows in a 100px viewport
    }

    @Test
    void contentIsAsTallAsItsRows() {
        assertEquals(200, layout().contentHeight());
    }

    @Test
    void scrollStopsAtTheEndOfTheContent() {
        assertEquals(100, layout().maxScroll());
        assertEquals(0, layout().clampScroll(-40));
        assertEquals(100, layout().clampScroll(500));
        assertEquals(60, layout().clampScroll(60));
    }

    @Test
    void contentShorterThanTheViewportDoesNotScroll() {
        ScanLayout small = ScanLayout.of(2, 20, 100);
        assertEquals(0, small.maxScroll());
        assertEquals(0, small.clampScroll(50));
    }

    @Test
    void onlyVisibleRowsAreDrawn() {
        ScanLayout layout = layout();
        assertEquals(0, layout.firstVisible(0));
        assertEquals(5, layout.lastVisible(0));

        // Scrolled by half a row: the first row is clipped but still on screen.
        assertEquals(0, layout.firstVisible(10));
        assertEquals(6, layout.lastVisible(10));

        assertEquals(3, layout.firstVisible(70));
        assertEquals(9, layout.lastVisible(70));
    }

    @Test
    void aRowSitsWhereTheScrollPutIt() {
        ScanLayout layout = layout();
        assertEquals(0, layout.yOf(0, 0));
        assertEquals(40, layout.yOf(2, 0));
        assertEquals(20, layout.yOf(2, 20));
        assertEquals(-10, layout.yOf(0, 10));
    }

    @Test
    void aClickFindsItsRow() {
        ScanLayout layout = layout();
        assertEquals(OptionalInt.of(0), layout.indexAt(5, 0));
        assertEquals(OptionalInt.of(1), layout.indexAt(25, 0));
        assertEquals(OptionalInt.of(2), layout.indexAt(5, 40));
    }

    @Test
    void aClickOutsideTheContentHitsNothing() {
        ScanLayout layout = layout();
        assertFalse(layout.indexAt(-1, 0).isPresent());
        assertFalse(layout.indexAt(120, 0).isPresent());
        // Scrolled to the bottom, the content fills the viewport exactly, so every
        // point in it is still on a row.
        assertTrue(layout.indexAt(95, 100).isPresent());

        // Two rows in a hundred pixels: the empty space below them hits nothing.
        ScanLayout shortList = ScanLayout.of(2, 20, 100);
        assertTrue(shortList.indexAt(39, 0).isPresent());
        assertFalse(shortList.indexAt(40, 0).isPresent());
        assertFalse(shortList.indexAt(90, 0).isPresent());
    }

    @Test
    void columnsDivideTheSpaceEvenly() {
        assertEquals(100, ScanLayout.columnWidth(310, 3, 5));
        assertEquals(0, ScanLayout.columnLeft(0, 100, 5, 0));
        assertEquals(105, ScanLayout.columnLeft(0, 100, 5, 1));
        assertEquals(230, ScanLayout.columnLeft(20, 100, 5, 2));
    }
}
