package com.w0x7y.justtiers.gui.layout;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CreditLineTest {

    private static final int PREFIX = 60;
    private static final int SPACE = 4;
    private static final int SEPARATOR = 9;
    private static final List<Integer> NAMES = List.of(30, 40, 35);

    private static CreditLine line() {
        return CreditLine.centeredIn(100, 400, PREFIX, SPACE, SEPARATOR, NAMES);
    }

    @Test
    void theWholeLineIsCentredInThePanel() {
        CreditLine line = line();
        int total = PREFIX + SPACE + 30 + 40 + 35 + 2 * SEPARATOR;
        CreditLine.Span last = line.spans().getLast();

        assertEquals(100 + (400 - total) / 2, line.x());
        // Odd slack cannot be split evenly; a pixel more on the right is the whole
        // difference an integer divide can make.
        int left = line.x() - 100;
        int right = 100 + 400 - (last.x() + last.width());
        assertTrue(Math.abs(left - right) <= 1, left + " vs " + right);
    }

    @Test
    void theNamesFollowThePrefixInOrder() {
        CreditLine line = line();

        assertEquals(line.x() + PREFIX + SPACE, line.spans().getFirst().x());
        for (int i = 0; i < NAMES.size(); i++) {
            assertEquals(NAMES.get(i), line.spans().get(i).width(), "name " + i);
        }
        for (int i = 1; i < NAMES.size(); i++) {
            CreditLine.Span previous = line.spans().get(i - 1);
            assertEquals(previous.x() + previous.width() + SEPARATOR,
                    line.spans().get(i).x(), "name " + i);
        }
    }

    @Test
    void aClickLandsOnTheNameItLooksLikeItLandsOn() {
        CreditLine line = line();

        for (int i = 0; i < line.spans().size(); i++) {
            CreditLine.Span span = line.spans().get(i);
            assertEquals(i, line.spanAt(span.x()).orElseThrow(), "left edge of " + i);
            assertEquals(i, line.spanAt(span.x() + span.width() - 1).orElseThrow(),
                    "right edge of " + i);
        }
    }

    /** The separator between two names is not a third thing to click. */
    @Test
    void theSeparatorBetweenTwoNamesOpensNothing() {
        CreditLine line = line();
        CreditLine.Span first = line.spans().getFirst();

        assertTrue(line.spanAt(first.x() + first.width()).isEmpty());
        assertTrue(line.spanAt(first.x() + first.width() + SEPARATOR - 1).isEmpty());
    }

    @Test
    void theLeadInTextIsNotClickable() {
        CreditLine line = line();

        assertTrue(line.spanAt(line.x()).isEmpty());
        assertTrue(line.spanAt(line.x() + PREFIX).isEmpty());
    }

    @Test
    void pastEitherEndIsNothing() {
        CreditLine line = line();
        CreditLine.Span last = line.spans().getLast();

        assertTrue(line.spanAt(line.x() - 1).isEmpty());
        assertTrue(line.spanAt(last.x() + last.width()).isEmpty());
    }

    @Test
    void aSingleNameNeedsNoSeparator() {
        CreditLine line = CreditLine.centeredIn(0, 200, PREFIX, SPACE, SEPARATOR, List.of(30));

        assertEquals(1, line.spans().size());
        assertEquals((200 - (PREFIX + SPACE + 30)) / 2, line.x());
        assertEquals(0, line.spanAt(line.spans().getFirst().x()).orElseThrow());
    }

    @Test
    void noNamesIsStillALine() {
        CreditLine line = CreditLine.centeredIn(0, 200, PREFIX, SPACE, SEPARATOR, List.of());

        assertEquals(List.of(), line.spans());
        assertTrue(line.spanAt(line.x()).isEmpty());
    }
}
