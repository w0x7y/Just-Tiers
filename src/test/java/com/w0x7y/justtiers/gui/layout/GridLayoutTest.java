package com.w0x7y.justtiers.gui.layout;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

class GridLayoutTest {

    private static GridLayout layout(int count, int width) {
        return GridLayout.of(count, width, 72, 72, 8, 6);
    }

    @Test
    void columnsFillTheAvailableWidthUpToTheCap() {
        assertEquals(4, layout(12, 340).columns());   // 4*72 + 3*8 = 312 fits, 5 would not
        assertEquals(6, layout(12, 2000).columns());  // capped
        assertEquals(1, layout(12, 100).columns());   // never zero
    }

    @Test
    void rowsCoverEveryItem() {
        GridLayout grid = layout(12, 340);
        assertEquals(3, grid.rows());
        assertEquals(2, layout(8, 340).rows());
        assertEquals(3, layout(9, 340).rows());       // partial last row still counts
    }

    @Test
    void positionsAdvanceByTileAndGap() {
        GridLayout grid = layout(12, 340);
        assertEquals(0, grid.xOf(0));
        assertEquals(80, grid.xOf(1));                // 72 + 8
        assertEquals(0, grid.xOf(4));                 // wrapped
        assertEquals(0, grid.yOf(0));
        assertEquals(80, grid.yOf(4));
    }

    @Test
    void contentSizeMatchesTheOccupiedArea() {
        GridLayout grid = layout(12, 340);
        assertEquals(312, grid.contentWidth());       // 4*72 + 3*8
        assertEquals(232, grid.contentHeight());      // 3*72 + 2*8
    }

    @Test
    void hitTestingFindsTheTileUnderThePoint() {
        GridLayout grid = layout(12, 340);
        assertEquals(OptionalInt.of(0), grid.indexAt(0, 0));
        assertEquals(OptionalInt.of(0), grid.indexAt(71, 71));
        assertEquals(OptionalInt.of(1), grid.indexAt(80, 0));
        assertEquals(OptionalInt.of(4), grid.indexAt(0, 80));
    }

    @Test
    void gapsAndOutOfBoundsSelectNothing() {
        GridLayout grid = layout(12, 340);
        assertEquals(OptionalInt.empty(), grid.indexAt(75, 0));    // horizontal gap
        assertEquals(OptionalInt.empty(), grid.indexAt(0, 75));    // vertical gap
        assertEquals(OptionalInt.empty(), grid.indexAt(-1, 0));
        assertEquals(OptionalInt.empty(), grid.indexAt(0, 1000));
    }

    @Test
    void trailingEmptyCellsOfThePartialRowSelectNothing() {
        GridLayout grid = layout(9, 340);              // 4 columns, last row holds one tile
        assertEquals(OptionalInt.of(8), grid.indexAt(0, 160));
        assertEquals(OptionalInt.empty(), grid.indexAt(80, 160));
    }

    @Test
    void keyboardNavigationClampsAtTheEdges() {
        GridLayout grid = layout(12, 340);
        assertEquals(1, grid.move(0, GridLayout.Direction.RIGHT));
        assertEquals(0, grid.move(0, GridLayout.Direction.LEFT));
        assertEquals(4, grid.move(0, GridLayout.Direction.DOWN));
        assertEquals(0, grid.move(0, GridLayout.Direction.UP));
        assertEquals(11, grid.move(11, GridLayout.Direction.DOWN));
        assertEquals(11, grid.move(11, GridLayout.Direction.RIGHT));
    }

    @Test
    void navigationDoesNotLandOnEmptyTrailingCells() {
        GridLayout grid = layout(9, 340);
        assertEquals(8, grid.move(4, GridLayout.Direction.DOWN));
        assertEquals(8, grid.move(5, GridLayout.Direction.DOWN));  // clamped onto the last item
    }

    @Test
    void zeroItemsIsHarmless() {
        GridLayout grid = layout(0, 340);
        assertEquals(0, grid.rows());
        assertEquals(OptionalInt.empty(), grid.indexAt(0, 0));
    }
}
