package com.w0x7y.justtiers.gui.layout;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HistoryStripTest {

    private static final int LABEL = 36;

    private static HistoryStrip strip(int width, List<Integer> names) {
        return HistoryStrip.of(10, 32, width, 20, LABEL, names);
    }

    @Test
    void namesKeepTheirOrderAfterTheLabel() {
        HistoryStrip strip = strip(400, List.of(30, 50, 40));

        assertEquals(3, strip.entries().size());
        assertEquals(strip.labelX() + LABEL + HistoryStrip.LABEL_GAP, strip.entries().getFirst().x());
        assertEquals(50 + 2 * HistoryStrip.TEXT_PADDING, strip.entries().get(1).width());
        for (int i = 1; i < strip.entries().size(); i++) {
            HistoryStrip.Entry previous = strip.entries().get(i - 1);
            assertEquals(previous.x() + previous.width() + HistoryStrip.GAP,
                    strip.entries().get(i).x(), "entry " + i);
        }
    }

    @Test
    void whenNamesDoNotAllFitTheOldestAreDropped() {
        int each = 60 + 2 * HistoryStrip.TEXT_PADDING;
        int room = LABEL + HistoryStrip.LABEL_GAP + each + HistoryStrip.GAP + each;
        HistoryStrip strip = strip(room, List.of(60, 60, 60, 60));

        assertEquals(2, strip.entries().size());
        HistoryStrip.Entry last = strip.entries().getLast();
        assertEquals(10 + room, last.x() + last.width(), "exactly full");
    }

    @Test
    void theRunIsCentredInItsSpace() {
        HistoryStrip strip = strip(400, List.of(30));
        int used = LABEL + HistoryStrip.LABEL_GAP + HistoryStrip.MIN_WIDTH;
        HistoryStrip.Entry only = strip.entries().getFirst();

        int left = strip.labelX() - 10;
        int right = 10 + 400 - (only.x() + only.width());
        assertEquals(10 + (400 - used) / 2, strip.labelX());
        assertTrue(Math.abs(left - right) <= 1, left + " vs " + right);
    }

    @Test
    void buttonWidthsAreClampedToReadableBounds() {
        HistoryStrip strip = strip(600, List.of(4, 300));

        assertEquals(HistoryStrip.MIN_WIDTH, strip.entries().get(0).width());
        assertEquals(HistoryStrip.MAX_WIDTH, strip.entries().get(1).width());
    }

    @Test
    void nothingFitsOrNothingRecentMeansNoEntries() {
        assertTrue(strip(LABEL + 10, List.of(30)).entries().isEmpty());
        assertTrue(strip(400, List.of()).entries().isEmpty());
    }

    @Test
    void textIsCentredInTheStripHeight() {
        HistoryStrip strip = strip(400, List.of(30));
        int top = strip.textTop(9);

        int above = top - strip.y();
        int below = strip.y() + strip.height() - top - 9;
        assertTrue(Math.abs(above - below) <= 1, above + " above, " + below + " below");
    }
}
