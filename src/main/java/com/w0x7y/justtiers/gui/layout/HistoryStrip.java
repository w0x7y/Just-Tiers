package com.w0x7y.justtiers.gui.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * The one-line strip of recent lookups under the lookup screen's search field: a label,
 * then one button per recent name, the whole run centred in the space it is given.
 *
 * <p>Names arrive most recent first, and the strip keeps them in that order. When they
 * do not all fit it drops the oldest rather than wrapping, so the strip is always one
 * line tall and never pushes the search field or Done off the screen.
 *
 * @param labelX where the label starts. Meaningless when there are no entries, because
 *               the label is not drawn then.
 * @param entries the buttons that fit, index-aligned with the names they were measured
 *                from. May be shorter than that list, or empty.
 */
public record HistoryStrip(int labelX, int y, int height, List<Entry> entries) {

    /** Room either side of a name inside its button. */
    static final int TEXT_PADDING = 4;
    /** Narrower buttons look like icons; wider ones waste the line. */
    static final int MIN_WIDTH = 40;
    /** Wide enough for a sixteen-character name in Minecraft's font. */
    static final int MAX_WIDTH = 104;
    static final int GAP = 4;
    static final int LABEL_GAP = 6;

    /** One button: where it starts, and how wide it is. */
    public record Entry(int x, int width) {
    }

    public HistoryStrip {
        entries = List.copyOf(entries);
    }

    public static HistoryStrip none(int y, int height) {
        return new HistoryStrip(0, y, height, List.of());
    }

    /**
     * @param nameWidths the drawn widths of the recent names, most recent first.
     */
    public static HistoryStrip of(int x, int y, int width, int height,
                                  int labelWidth, List<Integer> nameWidths) {
        List<Integer> widths = new ArrayList<>(nameWidths.size());
        int used = labelWidth;
        for (int nameWidth : nameWidths) {
            int buttonWidth = Math.clamp(nameWidth + 2 * TEXT_PADDING, MIN_WIDTH, MAX_WIDTH);
            int gap = widths.isEmpty() ? LABEL_GAP : GAP;
            if (used + gap + buttonWidth > width) break;
            used += gap + buttonWidth;
            widths.add(buttonWidth);
        }
        if (widths.isEmpty()) return none(y, height);

        int labelX = x + (width - used) / 2;
        int cursor = labelX + labelWidth + LABEL_GAP;
        List<Entry> entries = new ArrayList<>(widths.size());
        for (int buttonWidth : widths) {
            entries.add(new Entry(cursor, buttonWidth));
            cursor += buttonWidth + GAP;
        }
        return new HistoryStrip(labelX, y, height, entries);
    }

    /** The top of one line of text, centred in the strip's height. */
    public int textTop(int lineHeight) {
        return y + (height - lineHeight) / 2;
    }
}
