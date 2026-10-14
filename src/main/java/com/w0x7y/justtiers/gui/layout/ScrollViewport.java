package com.w0x7y.justtiers.gui.layout;

import java.util.Optional;

/**
 * A window onto vertically scrolling content. Offsets and positions are measured from
 * the top of the content, which sits at the top of the viewport when the offset is 0.
 *
 * <p>Deliberately Minecraft-free: the screens own the offset and route the wheel, page
 * keys and focus to it; this answers where those leave the content.
 */
public record ScrollViewport(int contentHeight, int viewportHeight) {

    /** How far one wheel notch scrolls. */
    public static final int WHEEL_STEP = 16;
    /** The shortest a scrollbar thumb is drawn, so it stays visible over long content. */
    public static final int THUMB_MIN_HEIGHT = 8;

    /** Where the scrollbar thumb sits, from the viewport's top, and how tall it is. */
    public record Thumb(int y, int height) {
    }

    /** How far the content can scroll; 0 when it fits. */
    public int maxScroll() {
        return Math.max(0, contentHeight - viewportHeight);
    }

    /** True when the content does not fit and scrolling it can move anything. */
    public boolean scrolls() {
        return maxScroll() > 0;
    }

    public int clamp(int offset) {
        return Math.clamp(offset, 0, maxScroll());
    }

    /** The offset after the wheel turned by {@code delta} notches; positive scrolls up. */
    public int wheel(int offset, double delta) {
        return clamp(offset - (int) (delta * WHEEL_STEP));
    }

    /** The offset a whole viewport further down (positive) or up (negative). */
    public int page(int offset, int direction) {
        return clamp(offset + direction * viewportHeight);
    }

    /**
     * The nearest offset that shows the span {@code top} to {@code top + height}
     * completely: a span above the viewport moves to its top, one below it moves to its
     * bottom, and an offset that already shows the span stays.
     */
    public int reveal(int offset, int top, int height) {
        int next = offset;
        if (top < next) {
            next = top;
        } else if (top + height > next + viewportHeight) {
            next = top + height - viewportHeight;
        }
        return clamp(next);
    }

    /**
     * The scrollbar thumb at an offset: as tall as the visible share of the content,
     * but at least {@link #THUMB_MIN_HEIGHT}, and moving from the top of the viewport at
     * offset 0 to its bottom at {@link #maxScroll()}. Empty when nothing scrolls.
     */
    public Optional<Thumb> thumb(int offset) {
        if (!scrolls()) return Optional.empty();
        int height = Math.max(THUMB_MIN_HEIGHT, viewportHeight * viewportHeight / contentHeight);
        return Optional.of(new Thumb(offset * (viewportHeight - height) / maxScroll(), height));
    }
}
