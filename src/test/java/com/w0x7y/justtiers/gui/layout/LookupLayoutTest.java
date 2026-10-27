package com.w0x7y.justtiers.gui.layout;

import org.junit.jupiter.api.Test;

import java.util.Collections;
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


    /** Heights just tall enough for the whole panel at skin scale 1, under the history strip. */
    @Test
    void aSmallerSkinAvoidsScrollingWhenTheWholePanelCanFit() {
        for (int height : List.of(330, 350)) {
            LookupLayout layout = layout(854, height);
            assertEquals(0, layout.scrolling().maxScroll(), "screen height " + height);
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

    // --- recent lookups ---

    /** Sixteen characters of Minecraft's font: the widest name an account can have. */
    private static final int WIDEST_NAME = 96;
    private static final int RECENT_LABEL = 36;

    @Test
    void theHistoryStripSitsBetweenSearchAndResultsAtSmallGuiSizes() {
        for (int[] size : new int[][]{{320, 240}, {427, 240}, {480, 270}}) {
            String at = size[0] + "x" + size[1];
            LookupLayout layout = layout(size[0], size[1]);
            assertTrue(layout.showsHistory(), at);
            assertTrue(layout.historyY() >= layout.searchY() + LookupLayout.BUTTON_HEIGHT, at);
            assertTrue(layout.historyY() + LookupLayout.HISTORY_HEIGHT < layout.panelY(), at);
            assertTrue(layout.viewportHeight() >= LookupLayout.MIN_VIEWPORT, at);
            assertTrue(layout.doneButtonY() + LookupLayout.BUTTON_HEIGHT <= size[1] - 8, at);
            assertTrue(layout.searchX() >= 0, at);
            assertTrue(layout.searchX() + layout.searchWidth() <= size[0], at);
        }
    }

    @Test
    void historyButtonsStayOnScreenWithoutOverlappingAtSmallGuiSizes() {
        List<Integer> names = Collections.nCopies(8, WIDEST_NAME);
        for (int[] size : new int[][]{{320, 240}, {427, 240}, {480, 270}}) {
            String at = size[0] + "x" + size[1];
            HistoryStrip strip = layout(size[0], size[1]).history(RECENT_LABEL, names);
            assertFalse(strip.entries().isEmpty(), at);
            assertTrue(strip.entries().size() < 8, at + ": eight of the widest names cannot fit");
            assertTrue(strip.labelX() >= LookupLayout.SCREEN_MARGIN, at);
            HistoryStrip.Entry last = strip.entries().getLast();
            assertTrue(last.x() + last.width() <= size[0] - LookupLayout.SCREEN_MARGIN, at);
            for (int i = 1; i < strip.entries().size(); i++) {
                HistoryStrip.Entry previous = strip.entries().get(i - 1);
                assertTrue(previous.x() + previous.width() < strip.entries().get(i).x(), at);
            }
        }
    }

    @Test
    void aWideScreenShowsAFullHistoryOfShortNames() {
        HistoryStrip strip = layout(854, 480).history(RECENT_LABEL, Collections.nCopies(8, 30));
        assertEquals(8, strip.entries().size());
    }

    @Test
    void aScreenTooShortForTheStripKeepsSearchAndDoneAndDropsTheStrip() {
        LookupLayout layout = layout(320, 150);

        assertFalse(layout.showsHistory());
        assertEquals(LookupLayout.SEARCH_BOTTOM, layout.panelY());
        assertTrue(layout.history(RECENT_LABEL, List.of(30, 40)).entries().isEmpty());
        assertTrue(layout.doneButtonY() + LookupLayout.BUTTON_HEIGHT <= 150 - 8);
        assertTrue(layout.doneButtonY() >= layout.searchY() + LookupLayout.BUTTON_HEIGHT);
    }

    @Test
    void theStripIsShownFromTheShortestHeightThatKeepsItsMinimumViewport() {
        int shortest = LookupLayout.HISTORY_BOTTOM + LookupLayout.MIN_VIEWPORT
                + LookupLayout.BUTTON_GAP + LookupLayout.BUTTON_HEIGHT + LookupLayout.SCREEN_MARGIN;
        assertTrue(layout(320, shortest).showsHistory());
        assertFalse(layout(320, shortest - 1).showsHistory());
        assertTrue(layout(320, shortest).viewportHeight() >= LookupLayout.MIN_VIEWPORT);
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
        assertEquals(4, layout(854, 480).rows().size());
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

    // --- cells inside a row ---

    @Test
    void cellsAreCentredInABoxWiderThanTheyNeed() {
        LookupLayout layout = layout(854, 480);
        LookupLayout.Row widest = layout.rows().get(2);
        LookupLayout.Row narrowest = layout.rows().getFirst();

        // The panel is sized around the widest row, which therefore has exactly the
        // box's own padding to spare.
        assertEquals(widest.x() + LookupLayout.BOX_PADDING, widest.cellsLeft());

        int slack = narrowest.width() - narrowest.grid().contentWidth();
        assertEquals(narrowest.x() + slack / 2, narrowest.cellsLeft());
        assertTrue(narrowest.cellsLeft() > widest.cellsLeft());
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

    // --- discovered gamemodes add cells after the registered ones ---

    /** Registry counts plus the most discovered cells one answer can add, on every site. */
    private static LookupMetrics withDiscovered(int screenWidth, int screenHeight) {
        return new LookupMetrics(screenWidth, screenHeight, 9, 14, 48, 44, 14,
                com.w0x7y.justtiers.tier.Source.ALL.stream()
                        .map(source -> com.w0x7y.justtiers.tier.Gamemodes.of(source).size()
                                + com.w0x7y.justtiers.tier.DiscoveredGamemodes.MAX_PER_ANSWER)
                        .toList());
    }

    @Test
    void theMostDiscoveredCellsStillFitAndScrollAtTheSmallestGui() {
        for (int[] size : new int[][]{{320, 240}, {427, 240}, {854, 480}}) {
            LookupLayout layout = LookupLayout.of(withDiscovered(size[0], size[1]));
            String screen = size[0] + "x" + size[1];

            assertTrue(layout.panelX() >= LookupLayout.SCREEN_MARGIN, screen);
            assertTrue(layout.panelRight() <= size[0] - LookupLayout.SCREEN_MARGIN, screen);
            assertTrue(layout.panelY() >= LookupLayout.SEARCH_BOTTOM, screen);
            assertTrue(layout.doneButtonY() + LookupLayout.BUTTON_HEIGHT <= size[1] - LookupLayout.SCREEN_MARGIN,
                    screen);
            assertEquals(layout.footerY() + 9 + LookupLayout.PANEL_PADDING, layout.panelBottom(), screen);

            for (LookupLayout.Row row : layout.rows()) {
                for (int i = 0; i < row.grid().itemCount(); i++) {
                    assertTrue(row.cellX(i) >= row.x() && row.cellX(i) + 44 <= row.x() + row.width(),
                            screen + " cell " + i);
                    assertTrue(row.cellY(i) >= row.y() && row.cellY(i) + 14 <= row.y() + row.height(),
                            screen + " cell " + i);
                }
            }
            for (int i = 1; i < layout.rows().size(); i++) {
                LookupLayout.Row above = layout.rows().get(i - 1);
                assertTrue(above.y() + above.height() <= layout.rows().get(i).y(), screen + " row " + i);
            }
        }
    }

    @Test
    void scrollingRevealsTheLastDiscoveredCellAndTheFooterAt320x240() {
        LookupLayout layout = LookupLayout.of(withDiscovered(320, 240));
        assertTrue(layout.scrolling().maxScroll() > 0);

        for (LookupLayout.Row row : layout.rows()) {
            int last = row.grid().itemCount() - 1;
            int scroll = layout.scrollTo(row.cellY(last), 14, 0);
            assertTrue(row.cellY(last) - scroll >= layout.panelY());
            assertTrue(row.cellY(last) - scroll + 14 <= layout.viewportBottom());
        }
        int footerScroll = layout.scrollTo(layout.footerY(), 9, 0);
        assertTrue(layout.footerY() - footerScroll + 9 <= layout.viewportBottom());
    }

    /**
     * The history strip and the most discovered cells together: the strip keeps its line,
     * search and Done stay on screen, and every cell can still be scrolled into view.
     */
    @Test
    void theHistoryStripAndTheMostDiscoveredCellsTogetherKeepSearchAndDoneReachable() {
        List<Integer> widestNames = Collections.nCopies(8, WIDEST_NAME);
        for (int[] size : new int[][]{{320, 240}, {427, 240}, {480, 270}}) {
            String screen = size[0] + "x" + size[1];
            LookupLayout layout = LookupLayout.of(withDiscovered(size[0], size[1]));

            assertTrue(layout.showsHistory(), screen);
            assertTrue(layout.historyY() >= layout.searchY() + LookupLayout.BUTTON_HEIGHT, screen);
            assertTrue(layout.historyY() + LookupLayout.HISTORY_HEIGHT < layout.panelY(), screen);
            assertTrue(layout.panelY() >= LookupLayout.HISTORY_BOTTOM, screen);
            assertTrue(layout.viewportHeight() >= LookupLayout.MIN_VIEWPORT, screen);
            assertTrue(layout.doneButtonY() >= layout.viewportBottom(), screen);
            assertTrue(layout.doneButtonY() + LookupLayout.BUTTON_HEIGHT <= size[1] - LookupLayout.SCREEN_MARGIN,
                    screen);
            assertTrue(layout.scrolling().maxScroll() > 0, screen);

            HistoryStrip strip = layout.history(RECENT_LABEL, widestNames);
            assertFalse(strip.entries().isEmpty(), screen);
            HistoryStrip.Entry last = strip.entries().getLast();
            assertTrue(last.x() + last.width() <= size[0] - LookupLayout.SCREEN_MARGIN, screen);

            for (LookupLayout.Row row : layout.rows()) {
                int lastCell = row.grid().itemCount() - 1;
                int scroll = layout.scrollTo(row.cellY(lastCell), 14, 0);
                assertTrue(row.cellY(lastCell) - scroll >= layout.panelY(), screen);
                assertTrue(row.cellY(lastCell) - scroll + 14 <= layout.viewportBottom(), screen);
            }
            int footerScroll = layout.scrollTo(layout.footerY(), 9, 0);
            assertTrue(layout.footerY() - footerScroll + 9 <= layout.viewportBottom(), screen);
        }
    }

    @Test
    void extraCellsWrapIntoMoreGridRowsRatherThanWideningThePanel() {
        LookupLayout registered = layout(320, 240);
        LookupLayout discovered = LookupLayout.of(withDiscovered(320, 240));

        assertEquals(registered.panelWidth(), discovered.panelWidth());
        for (int i = 0; i < registered.rows().size(); i++) {
            assertTrue(discovered.rows().get(i).grid().rows() > registered.rows().get(i).grid().rows(),
                    "row " + i);
        }
        assertTrue(discovered.panelHeight() > registered.panelHeight());
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
