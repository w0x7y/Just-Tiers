package com.w0x7y.justtiers.gui.layout;

import java.util.List;

/**
 * Everything {@link LookupLayout} needs that only the running game can measure: the
 * window, the font, and how many gamemodes each site runs.
 *
 * <p>Splitting these out is what lets the panel's stacking be arithmetic. Nothing here
 * is a decision — the layout makes all of those — and nothing here is a coordinate.
 *
 * @param rowItemCounts one entry per site row, in the order the rows are drawn.
 */
public record LookupMetrics(int screenWidth, int screenHeight,
                            int lineHeight, int nameHeight,
                            int labelTextWidth, int cellWidth, int cellHeight,
                            List<Integer> rowItemCounts) {

    public LookupMetrics {
        rowItemCounts = List.copyOf(rowItemCounts);
    }
}
