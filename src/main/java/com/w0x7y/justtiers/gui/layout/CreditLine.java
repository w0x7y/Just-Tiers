package com.w0x7y.justtiers.gui.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * The one-line credit at the foot of the lookup panel: some lead-in text, then the site
 * names separated by a fixed string, the whole run centred in the panel.
 *
 * <p>Each name is clickable, so where each one starts and stops is not decoration — it
 * is the hit box. Minecraft-free, so "clicking the separator between two site names
 * opens nothing" is a unit test rather than a careful aim with a mouse.
 */
public record CreditLine(int x, List<Span> spans) {

    /** One clickable name: where it starts, and how wide it is. */
    public record Span(int x, int width) {

        public boolean contains(double mouseX) {
            return mouseX >= x && mouseX < x + width;
        }
    }

    public CreditLine {
        spans = List.copyOf(spans);
    }

    /**
     * Lays the line out centred in a panel. {@code nameWidths} are the drawn widths of
     * the site names, in the order they appear.
     */
    public static CreditLine centeredIn(int panelX, int panelWidth,
                                        int prefixWidth, int spaceWidth, int separatorWidth,
                                        List<Integer> nameWidths) {
        int total = prefixWidth + spaceWidth;
        for (int i = 0; i < nameWidths.size(); i++) {
            total += nameWidths.get(i);
            if (i > 0) {
                total += separatorWidth;
            }
        }

        int x = panelX + (panelWidth - total) / 2;
        int cursor = x + prefixWidth + spaceWidth;
        List<Span> spans = new ArrayList<>(nameWidths.size());
        for (int i = 0; i < nameWidths.size(); i++) {
            if (i > 0) {
                cursor += separatorWidth;
            }
            spans.add(new Span(cursor, nameWidths.get(i)));
            cursor += nameWidths.get(i);
        }
        return new CreditLine(x, List.copyOf(spans));
    }

    /** The name under this point, or empty between two of them. */
    public OptionalInt spanAt(double mouseX) {
        for (int i = 0; i < spans.size(); i++) {
            if (spans.get(i).contains(mouseX)) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }
}
