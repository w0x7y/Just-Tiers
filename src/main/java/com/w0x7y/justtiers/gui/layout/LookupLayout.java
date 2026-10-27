package com.w0x7y.justtiers.gui.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * Every coordinate the lookup panel draws at, worked out in one go from
 * {@link LookupMetrics}.
 *
 * <p>The panel is a fixed stack: name, skin, a heading, one box per site, a reserved
 * note line, and a footer, with a separator between the first three blocks. Its size
 * comes from the sites' gamemode counts rather than from the answers that have arrived,
 * so a row filling in never shifts the panel under the cursor. The skin is drawn as
 * large as still leaves the Done button on screen.
 *
 * <p>Above the panel, outside its scrolling, sit the search field and a one-line strip of
 * recent lookups. The strip's line is reserved whenever the screen is tall enough, even
 * before there is anything in it, so the panel does not move when the first name arrives.
 * A screen too short to spare it a line goes without the strip rather than squeezing the
 * results further.
 *
 * <p>Deliberately Minecraft-free, like {@link GridLayout} and {@link SkinLayout} beside
 * it. The screen used to keep fifteen mutable coordinates and refill them by calling its
 * own stacking pass up to four times — once per skin scale it tried, once to measure and
 * once to place — so a half-finished measuring pass and a finished one were the same
 * fields. Here the height is its own function of the scale, and placing happens once.
 */
public record LookupLayout(int panelX, int panelY, int panelWidth, int panelHeight,
                           int skinScale, int nameY, int skinY, int tiersY, int noteY,
                           int footerY,
                           int firstSeparatorY, int secondSeparatorY, int thirdSeparatorY,
                           int viewportHeight, int doneButtonY,
                           int searchX, int searchWidth, int historyY, boolean showsHistory,
                           List<Row> rows) {

    static final int PANEL_PADDING = 10;
    static final int SECTION_GAP = 10;
    static final int BOX_PADDING = 4;
    static final int CELL_GAP = 2;
    static final int ROW_GAP = 4;
    static final int LABEL_GAP = 6;
    static final int SCREEN_MARGIN = 8;
    static final int BUTTON_HEIGHT = 20;
    static final int BUTTON_GAP = 6;
    /** Where the name field and lookup button sit, outside the scrollable content. */
    static final int SEARCH_TOP = 8;
    static final int SEARCH_MAX_WIDTH = 320;
    /** Room for the name field and lookup button, outside the scrollable content. */
    static final int SEARCH_BOTTOM = 36;
    /** The recent-lookups strip: one button tall, just under the search field. */
    static final int HISTORY_GAP = 4;
    static final int HISTORY_HEIGHT = BUTTON_HEIGHT;
    static final int HISTORY_Y = SEARCH_TOP + BUTTON_HEIGHT + HISTORY_GAP;
    /** Where the panel can start when the strip is shown. */
    static final int HISTORY_BOTTOM = HISTORY_Y + HISTORY_HEIGHT + SCREEN_MARGIN;
    /** The least scrolling room the strip may leave the results; any less and it is dropped. */
    static final int MIN_VIEWPORT = 64;

    /** Largest first: the skin is drawn as big as fits. */
    private static final int[] SKIN_SCALES = {3, 2, 1};

    /** The gap between the heading and the first box, and above the note line. */
    private static final int HEADING_GAP = 5;

    public LookupLayout {
        rows = List.copyOf(rows);
    }

    /**
     * One site's box, and the grid its cells sit in. Index-aligned with
     * {@link LookupMetrics#rowItemCounts()}, so the screen pairs them up by position.
     */
    public record Row(GridLayout grid, int x, int y, int width, int height) {

        /**
         * Every box is as wide as the widest site's row, so a site with fewer gamemodes
         * has room to spare; its cells are centred in it rather than left hanging off
         * one edge.
         */
        public int cellsLeft() {
            return x + (width - grid.contentWidth()) / 2;
        }

        public int cellsTop() {
            return y + BOX_PADDING;
        }

        public int cellX(int index) {
            return cellsLeft() + grid.xOf(index);
        }

        public int cellY(int index) {
            return cellsTop() + grid.yOf(index);
        }

        /** Where the site's name ends: its text is right-aligned to here. */
        public int labelRight() {
            return x - LABEL_GAP;
        }

        public int centerX() {
            return x + width / 2;
        }

        /** The top of one line of text, centred in the box's height. */
        public int textTop(int lineHeight) {
            return y + (height - lineHeight) / 2;
        }
    }

    public static LookupLayout of(LookupMetrics metrics) {
        int labelWidth = metrics.labelTextWidth() + LABEL_GAP;
        int available = Math.max(metrics.cellWidth(),
                metrics.screenWidth() - 2 * SCREEN_MARGIN - 2 * PANEL_PADDING
                        - labelWidth - 2 * BOX_PADDING);

        List<GridLayout> grids = new ArrayList<>(metrics.rowItemCounts().size());
        int widest = 0;
        for (int count : metrics.rowItemCounts()) {
            GridLayout grid = GridLayout.of(count, available,
                    metrics.cellWidth(), metrics.cellHeight(), CELL_GAP, count);
            grids.add(grid);
            widest = Math.max(widest, grid.contentWidth());
        }

        int panelWidth = Math.min(metrics.screenWidth() - 2 * SCREEN_MARGIN,
                labelWidth + widest + 2 * BOX_PADDING + 2 * PANEL_PADDING);
        int panelX = (metrics.screenWidth() - panelWidth) / 2;

        boolean showsHistory = metrics.screenHeight() - HISTORY_BOTTOM
                - BUTTON_GAP - BUTTON_HEIGHT - SCREEN_MARGIN >= MIN_VIEWPORT;
        int contentTop = showsHistory ? HISTORY_BOTTOM : SEARCH_BOTTOM;

        int skinScale = skinScale(metrics, grids, contentTop);
        int panelHeight = height(metrics, grids, skinScale);
        int panelY = Math.max(contentTop,
                (metrics.screenHeight() - panelHeight - BUTTON_HEIGHT - BUTTON_GAP) / 2);

        return place(metrics, grids, skinScale, panelX, panelY, panelWidth, panelHeight,
                labelWidth, showsHistory);
    }

    /** The largest skin that still leaves the Done button on screen. */
    private static int skinScale(LookupMetrics metrics, List<GridLayout> grids, int contentTop) {
        for (int scale : SKIN_SCALES) {
            int needed = height(metrics, grids, scale)
                    + BUTTON_GAP + BUTTON_HEIGHT + contentTop + SCREEN_MARGIN;
            if (needed <= metrics.screenHeight()) {
                return scale;
            }
        }
        return SKIN_SCALES[SKIN_SCALES.length - 1];
    }

    /**
     * How tall the panel is at a given skin scale. The stack is the same shape wherever
     * it starts, so this answers without placing anything — which is what lets the scale
     * be chosen and the panel be centred before a single coordinate exists.
     */
    private static int height(LookupMetrics metrics, List<GridLayout> grids, int skinScale) {
        int y = PANEL_PADDING;
        y += metrics.nameHeight() + SECTION_GAP;
        y += SkinLayout.HEIGHT * skinScale + SECTION_GAP;
        y += metrics.lineHeight() + HEADING_GAP;
        for (GridLayout grid : grids) {
            y += grid.contentHeight() + 2 * BOX_PADDING + ROW_GAP;
        }
        if (!grids.isEmpty()) {
            y -= ROW_GAP;
        }
        // Reserved whether or not the note is showing: it only becomes true once the last
        // site answers, and the panel must not grow a line under the cursor when it does.
        y += HEADING_GAP + metrics.lineHeight();
        y += SECTION_GAP;
        y += metrics.lineHeight() + PANEL_PADDING;
        return y;
    }

    private static LookupLayout place(LookupMetrics metrics, List<GridLayout> grids,
                                      int skinScale, int panelX, int panelY,
                                      int panelWidth, int panelHeight, int labelWidth,
                                      boolean showsHistory) {
        int y = panelY + PANEL_PADDING;

        int nameY = y;
        y += metrics.nameHeight() + SECTION_GAP / 2;
        int firstSeparatorY = y;
        y += SECTION_GAP / 2;

        int skinY = y;
        y += SkinLayout.HEIGHT * skinScale + SECTION_GAP / 2;
        int secondSeparatorY = y;
        y += SECTION_GAP / 2;

        int tiersY = y;
        y += metrics.lineHeight() + HEADING_GAP;

        int boxX = panelX + PANEL_PADDING + labelWidth;
        int boxWidth = panelWidth - 2 * PANEL_PADDING - labelWidth;
        List<Row> rows = new ArrayList<>(grids.size());
        for (GridLayout grid : grids) {
            int boxHeight = grid.contentHeight() + 2 * BOX_PADDING;
            rows.add(new Row(grid, boxX, y, boxWidth, boxHeight));
            y += boxHeight + ROW_GAP;
        }
        if (!grids.isEmpty()) {
            y -= ROW_GAP;
        }

        int noteY = y + HEADING_GAP;
        y = noteY + metrics.lineHeight() + SECTION_GAP / 2;
        int thirdSeparatorY = y;
        y += SECTION_GAP / 2;

        int footerY = y;

        int doneY = Math.min(panelY + panelHeight + BUTTON_GAP,
                metrics.screenHeight() - SCREEN_MARGIN - BUTTON_HEIGHT);
        int viewportHeight = Math.max(0, doneY - BUTTON_GAP - panelY);
        int searchWidth = Math.min(metrics.screenWidth() - 2 * SCREEN_MARGIN, SEARCH_MAX_WIDTH);
        int searchX = (metrics.screenWidth() - searchWidth) / 2;
        return new LookupLayout(panelX, panelY, panelWidth, panelHeight, skinScale,
                nameY, skinY, tiersY, noteY, footerY,
                firstSeparatorY, secondSeparatorY, thirdSeparatorY, viewportHeight, doneY,
                searchX, searchWidth, HISTORY_Y, showsHistory, rows);
    }

    public int searchY() {
        return SEARCH_TOP;
    }

    /**
     * Places the recent names that fit on the strip's line, which spans the wider of the
     * panel and the search field. Empty when the screen is too short to show the strip.
     *
     * @param nameWidths the drawn widths of the recent names, most recent first.
     */
    public HistoryStrip history(int labelWidth, List<Integer> nameWidths) {
        if (!showsHistory) return HistoryStrip.none(historyY, HISTORY_HEIGHT);
        int x = Math.min(panelX, searchX);
        int right = Math.max(panelRight(), searchX + searchWidth);
        return HistoryStrip.of(x, historyY, right - x, HISTORY_HEIGHT, labelWidth, nameWidths);
    }

    /** How tall the skin is drawn, at the scale that was chosen. */
    public int skinHeight() {
        return SkinLayout.HEIGHT * skinScale;
    }

    public int panelRight() {
        return panelX + panelWidth;
    }

    public int panelBottom() {
        return panelY + panelHeight;
    }

    public int viewportBottom() {
        return panelY + viewportHeight;
    }

    /** The panel scrolls inside the space between its top and the Done button. */
    public ScrollViewport scrolling() {
        return new ScrollViewport(panelHeight, viewportHeight);
    }

    public int maxScroll() {
        return scrolling().maxScroll();
    }

    /** Scroll a focused control completely into view, in content coordinates. */
    public int scrollTo(int top, int height, int scroll) {
        return scrolling().reveal(scroll, top - panelY, height);
    }
}
