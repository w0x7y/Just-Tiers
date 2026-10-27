package com.w0x7y.justtiers.gui.layout;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Both scrolling screens share one set of answers for wheel, page, focus and scrollbar. */
class ScrollViewportTest {

    /** 300 tall content in a 100 tall viewport: it scrolls by up to 200. */
    private static final ScrollViewport TALL = new ScrollViewport(300, 100);

    @Test
    void contentShorterThanTheViewportDoesNotScroll() {
        ScrollViewport fits = new ScrollViewport(60, 100);
        assertEquals(0, fits.maxScroll());
        assertFalse(fits.scrolls());
        assertEquals(0, fits.clamp(40));
        assertEquals(0, fits.wheel(0, -3));
        assertEquals(0, fits.page(0, 1));
        assertEquals(0, fits.reveal(0, 50, 10));
        assertEquals(Optional.empty(), fits.thumb(0));
    }

    @Test
    void contentExactlyTheViewportsHeightDoesNotScroll() {
        ScrollViewport exact = new ScrollViewport(100, 100);
        assertEquals(0, exact.maxScroll());
        assertFalse(exact.scrolls());
        assertEquals(Optional.empty(), exact.thumb(0));
    }

    @Test
    void anEmptyViewportScrollsThroughAllTheContent() {
        assertEquals(50, new ScrollViewport(50, 0).maxScroll());
    }

    @Test
    void offsetsAreClampedToTheContent() {
        assertEquals(200, TALL.maxScroll());
        assertTrue(TALL.scrolls());
        assertEquals(0, TALL.clamp(-5));
        assertEquals(120, TALL.clamp(120));
        assertEquals(200, TALL.clamp(500));
    }

    @Test
    void eachWheelNotchScrollsSixteenAndStopsAtBothEnds() {
        assertEquals(16, TALL.wheel(0, -1));
        assertEquals(48, TALL.wheel(0, -3));
        assertEquals(84, TALL.wheel(100, 1));
        // A fractional notch truncates toward zero, as the screens always have.
        assertEquals(100 - 8, TALL.wheel(100, 0.5));
        assertEquals(0, TALL.wheel(10, 1));
        assertEquals(200, TALL.wheel(190, -1));
    }

    @Test
    void aPageScrollsOneViewportAndStopsAtBothEnds() {
        assertEquals(100, TALL.page(0, 1));
        assertEquals(200, TALL.page(150, 1));
        assertEquals(50, TALL.page(150, -1));
        assertEquals(0, TALL.page(50, -1));
    }

    @Test
    void aSpanAlreadyInViewKeepsTheOffset() {
        assertEquals(40, TALL.reveal(40, 40, 100));
        assertEquals(40, TALL.reveal(40, 60, 20));
    }

    @Test
    void revealingASpanAboveTheViewportMovesItToTheTop() {
        assertEquals(30, TALL.reveal(80, 30, 20));
        assertEquals(0, TALL.reveal(80, 0, 20));
    }

    @Test
    void revealingASpanBelowTheViewportMovesItToTheBottom() {
        assertEquals(50, TALL.reveal(0, 130, 20));
        // A span straddling the bottom edge counts as below.
        assertEquals(20, TALL.reveal(0, 90, 30));
    }

    @Test
    void revealingTheLastSpanStopsAtMaxScroll() {
        assertEquals(200, TALL.reveal(0, 290, 10));
        assertEquals(200, TALL.reveal(0, 280, 40));
    }

    @Test
    void theThumbShowsTheVisibleShareAndTravelsTheWholeViewport() {
        // 100 * 100 / 300 = 33: a third of the track for a third of the content.
        assertEquals(new ScrollViewport.Thumb(0, 33), TALL.thumb(0).orElseThrow());
        assertEquals(new ScrollViewport.Thumb(100 - 33, 33), TALL.thumb(200).orElseThrow());
        assertEquals(new ScrollViewport.Thumb(100 * (100 - 33) / 200, 33), TALL.thumb(100).orElseThrow());
    }

    @Test
    void aThumbOverLongContentKeepsItsMinimumHeight() {
        ScrollViewport longContent = new ScrollViewport(10_000, 100);
        assertEquals(new ScrollViewport.Thumb(0, ScrollViewport.THUMB_MIN_HEIGHT),
                longContent.thumb(0).orElseThrow());
        assertEquals(new ScrollViewport.Thumb(100 - ScrollViewport.THUMB_MIN_HEIGHT, ScrollViewport.THUMB_MIN_HEIGHT),
                longContent.thumb(longContent.maxScroll()).orElseThrow());
    }

    @Test
    void theLookupLayoutDelegatesToItsViewport() {
        LookupLayout layout = LookupLayout.of(new LookupMetrics(320, 240, 9, 14, 48, 44, 14,
                java.util.List.of(8, 9, 10, 9)));
        ScrollViewport viewport = layout.scrolling();
        assertEquals(layout.panelHeight(), viewport.contentHeight());
        assertEquals(layout.viewportHeight(), viewport.viewportHeight());
        assertEquals(viewport.reveal(0, layout.footerY() - layout.panelY(), 9),
                layout.scrollTo(layout.footerY(), 9, 0));
    }
}
