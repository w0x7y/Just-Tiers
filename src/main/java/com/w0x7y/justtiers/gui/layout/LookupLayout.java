package com.w0x7y.justtiers.gui.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

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

        /** The cell under this point, or empty for a gap, an edge miss or an empty box. */
        public OptionalInt cellAt(double mouseX, double mouseY) {
            return grid.indexAt((int) mouseX - cellsLeft(), (int) mouseY - cellsTop());
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

        int skinScale = skinScale(metrics, grids);
        int panelHeight = height(metrics, grids, skinScale);
        int panelY = Math.max(SCREEN_MARGIN,
                (metrics.screenHeight() - panelHeight - BUTTON_HEIGHT - BUTTON_GAP) / 2);

        return place(metrics, grids, skinScale, panelX, panelY, panelWidth, panelHeight,
                labelWidth);
    }

    /** The largest skin that still leaves the Done button on screen. */
    private static int skinScale(LookupMetrics metrics, List<GridLayout> grids) {
        for (int scale : SKIN_SCALES) {
            int needed = height(metrics, grids, scale)
                    + BUTTON_GAP + BUTTON_HEIGHT + 2 * SCREEN_MARGIN;
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
                                      int panelWidth, int panelHeight, int labelWidth) {
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

        return new LookupLayout(panelX, panelY, panelWidth, panelHeight, skinScale,
                nameY, skinY, tiersY, noteY, footerY,
                firstSeparatorY, secondSeparatorY, thirdSeparatorY, rows);
    }

    /** How tall the skin is drawn, at the scale that was chosen. */
    public int skinHeight() {
        return SkinLayout.HEIGHT * skinScale;
    }

    /** The middle of the space the skin occupies, where a failed lookup says why. */
    public int skinCenterY() {
        return skinY + skinHeight() / 2;
    }

    public int panelRight() {
        return panelX + panelWidth;
    }

    public int panelBottom() {
        return panelY + panelHeight;
    }

    public int doneButtonY() {
        return panelBottom() + BUTTON_GAP;
    }
}
