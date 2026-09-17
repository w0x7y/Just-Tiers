package com.w0x7y.justtiers.gui.layout;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LookupLayoutTest {

    @Test
    void theDoneButtonRemainsReachableAtSmallGuiSizes() {
        for (int[] size : new int[][]{{320, 240}, {427, 240}, {480, 270}}) {
            LookupLayout layout = layout(size[0], size[1]);
            assertTrue(layout.doneButtonY() >= 0);
            assertTrue(layout.doneButtonY() + 20 <= size[1] - 8,
                    size[0] + "x" + size[1] + " bottom=" + (layout.doneButtonY() + 20));
        }
    }


    @Test
    void aSmallerSkinAvoidsScrollingWhenTheWholePanelCanFit() {
        for (int height : List.of(300, 320)) {
            LookupLayout layout = layout(854, height);
            assertEquals(0, layout.maxScroll(), "screen height " + height);
            assertTrue(layout.panelY() >= LookupLayout.SEARCH_BOTTOM);
            assertTrue(layout.doneButtonY() + 20 <= height - 8);
        }
    }

    @Test
    void focusCanRevealTheFooterThenReturnToTheFirstRowOnASmallScreen() {
        LookupLayout layout = layout(320, 240);
        int footerScroll = layout.scrollTo(layout.footerY(), 9, 0);
        assertTrue(footerScroll > 0);
        assertTrue(layout.footerY() - footerScroll >= layout.panelY());
        assertTrue(layout.footerY() - footerScroll + 9 <= layout.viewportBottom());

        LookupLayout.Row first = layout.rows().getFirst();
        int firstRowScroll = layout.scrollTo(first.cellY(0), 14, footerScroll);
        assertTrue(firstRowScroll < footerScroll);
        assertTrue(first.cellY(0) - firstRowScroll >= layout.panelY());
        assertTrue(first.cellY(0) - firstRowScroll + 14 <= layout.viewportBottom());
    }

    /** Roughly what Minecraft's own font reports at default GUI scale. */
    private static LookupMetrics metrics(int screenWidth, int screenHeight) {
        return new LookupMetrics(screenWidth, screenHeight, 9, 14, 48, 44, 14,
                com.w0x7y.justtiers.tier.Source.ALL.stream()
                        .map(source -> com.w0x7y.justtiers.tier.Gamemodes.of(source).size()).toList());
    }

    private static LookupLayout layout(int screenWidth, int screenHeight) {
        return LookupLayout.of(metrics(screenWidth, screenHeight));
    }

    // --- the stack holds together ---

    /**
     * The height is computed on its own, before a single coordinate exists, so that the
     * skin scale can be chosen and the panel centred. Nothing keeps it agreeing with the
     * placing pass except this.
     */
    @Test
    void theMeasuredHeightIsExactlyWhereThePlacedStackEnds() {
        for (int screenHeight : List.of(240, 320, 480, 720, 1080)) {
            LookupLayout layout = layout(854, screenHeight);
            assertEquals(layout.footerY() + 9 + LookupLayout.PANEL_PADDING,
                    layout.panelBottom(), "screen height " + screenHeight);
        }
    }

    @Test
    void everyBlockIsInsideThePanelAndInOrder() {
        LookupLayout layout = layout(854, 480);

        assertTrue(layout.panelY() < layout.nameY());
        assertTrue(layout.nameY() < layout.firstSeparatorY());
        assertTrue(layout.firstSeparatorY() < layout.skinY());
        assertTrue(layout.skinY() < layout.secondSeparatorY());
        assertTrue(layout.secondSeparatorY() < layout.tiersY());
        assertTrue(layout.tiersY() < layout.rows().getFirst().y());
        assertTrue(layout.rows().getLast().y() < layout.noteY());
        assertTrue(layout.noteY() < layout.thirdSeparatorY());
        assertTrue(layout.thirdSeparatorY() < layout.footerY());
        assertTrue(layout.footerY() < layout.panelBottom());
    }

    @Test
    void theRowsStackWithoutOverlapping() {
        LookupLayout layout = layout(854, 480);

        for (int i = 1; i < layout.rows().size(); i++) {
            LookupLayout.Row above = layout.rows().get(i - 1);
            LookupLayout.Row row = layout.rows().get(i);
            assertTrue(above.y() + above.height() <= row.y(), "row " + i);
        }
    }

    @Test
    void oneRowPerSiteThatWasMeasured() {
        assertEquals(3, layout(854, 480).rows().size());
        for (LookupLayout.Row row : layout(854, 480).rows()) {
            assertTrue(row.grid().itemCount() > 0);
        }
    }

    // --- the panel fits the screen ---

    @Test
    void thePanelIsCentredAndInsideTheMargins() {
        LookupLayout layout = layout(854, 480);

        assertEquals(854 - layout.panelRight(), layout.panelX());
        assertTrue(layout.panelX() >= LookupLayout.SCREEN_MARGIN);
    }

    @Test
    void aNarrowScreenIsNotOverflowed() {
        LookupLayout layout = layout(320, 480);

        assertTrue(layout.panelWidth() <= 320 - 2 * LookupLayout.SCREEN_MARGIN);
        assertTrue(layout.panelX() >= 0);
    }

    /**
     * The Done button is why the skin shrinks at all, so it had better still be on
     * screen once it has.
     */
    @Test
    void theDoneButtonStaysOnScreenAtEveryHeightItCan() {
        for (int screenHeight : List.of(300, 360, 480, 720, 1080)) {
            LookupLayout layout = layout(854, screenHeight);
            assertTrue(layout.doneButtonY() + LookupLayout.BUTTON_HEIGHT <= screenHeight,
                    "screen height " + screenHeight);
        }
    }

    @Test
    void theSkinIsDrawnAsLargeAsFits() {
        assertEquals(3, layout(854, 1080).skinScale());
        assertTrue(layout(854, 300).skinScale() < 3);
        assertTrue(layout(854, 240).skinScale() >= 1);
    }

    /** A screen too short for even the smallest skin still lays out rather than failing. */
    @Test
    void anImpossiblyShortScreenStillGetsAPanel() {
        LookupLayout layout = layout(854, 100);

        assertEquals(1, layout.skinScale());
        assertEquals(LookupLayout.SEARCH_BOTTOM, layout.panelY());
        assertTrue(layout.panelHeight() > 0);
    }

    @Test
    void theSkinBandIsWhereAFailedLookupSaysWhy() {
        LookupLayout layout = layout(854, 480);

        assertEquals(layout.skinY() + layout.skinHeight() / 2, layout.skinCenterY());
        assertTrue(layout.skinCenterY() > layout.skinY());
        assertTrue(layout.skinCenterY() < layout.tiersY());
    }

    // --- cells inside a row ---

    @Test
    void cellsAreCentredInABoxWiderThanTheyNeed() {
        LookupLayout layout = layout(854, 480);
        LookupLayout.Row widest = layout.rows().get(1);
        LookupLayout.Row narrowest = layout.rows().getFirst();

        // The panel is sized around the widest row, which therefore has exactly the
        // box's own padding to spare.
        assertEquals(widest.x() + LookupLayout.BOX_PADDING, widest.cellsLeft());

        int slack = narrowest.width() - narrowest.grid().contentWidth();
        assertEquals(narrowest.x() + slack / 2, narrowest.cellsLeft());
        assertTrue(narrowest.cellsLeft() > widest.cellsLeft());
    }

    @Test
    void aCellIsFoundWhereItWasDrawn() {
        LookupLayout.Row row = layout(854, 480).rows().getFirst();

        for (int i = 0; i < row.grid().itemCount(); i++) {
            assertEquals(i, row.cellAt(row.cellX(i) + 1, row.cellY(i) + 1).orElseThrow(),
                    "cell " + i);
        }
    }

    @Test
    void theGapBetweenTwoCellsIsNoCellAtAll() {
        LookupLayout.Row row = layout(854, 480).rows().getFirst();
        int betweenX = row.cellX(0) + metrics(854, 480).cellWidth();

        assertTrue(row.cellAt(betweenX, row.cellY(0) + 1).isEmpty());
        assertTrue(row.cellAt(row.x() - 1, row.cellY(0) + 1).isEmpty());
        assertTrue(row.cellAt(row.cellX(0) + 1, row.y() - 1).isEmpty());
    }

    @Test
    void everyCellIsInsideItsOwnBox() {
        for (LookupLayout.Row row : layout(854, 480).rows()) {
            for (int i = 0; i < row.grid().itemCount(); i++) {
                assertTrue(row.cellX(i) >= row.x(), "cell " + i);
                assertTrue(row.cellY(i) >= row.y(), "cell " + i);
                assertTrue(row.cellX(i) + 44 <= row.x() + row.width(), "right edge " + i);
                assertTrue(row.cellY(i) + 14 <= row.y() + row.height(), "bottom edge " + i);
            }
        }
    }

    @Test
    void aSiteNameSitsToTheLeftOfItsBox() {
        LookupLayout.Row row = layout(854, 480).rows().getFirst();

        assertTrue(row.labelRight() < row.x());
        assertEquals(row.x() + row.width() / 2, row.centerX());
    }

    @Test
    void oneLineOfTextIsCentredInTheBoxHeight() {
        LookupLayout.Row row = layout(854, 480).rows().getFirst();
        int top = row.textTop(9);

        int above = top - row.y();
        int below = row.y() + row.height() - top - 9;
        assertTrue(Math.abs(above - below) <= 1, above + " above, " + below + " below");
    }

    // --- the same measurements always give the same panel ---

    @Test
    void aSiteWithNoGamemodesDoesNotBreakTheStack() {
        LookupLayout layout = LookupLayout.of(new LookupMetrics(854, 480, 9, 14, 48, 44, 14,
                List.of()));

        assertEquals(List.of(), layout.rows());
        assertEquals(layout.footerY() + 9 + LookupLayout.PANEL_PADDING, layout.panelBottom());
    }
}
