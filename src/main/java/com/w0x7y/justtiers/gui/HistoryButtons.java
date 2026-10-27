package com.w0x7y.justtiers.gui;

import com.w0x7y.justtiers.gui.layout.HistoryStrip;
import com.w0x7y.justtiers.gui.layout.LookupLayout;
import com.w0x7y.justtiers.lookup.LookupHistory;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * The lookup screen's recent-name buttons: one slot per name the history can hold, placed
 * by {@link HistoryStrip}, with slots beyond what fits hidden. Each runs its lookup in
 * place, as editing the name and retrying would. Focus stays with the screen.
 */
final class HistoryButtons {
    private final LookupLayout layout;
    private final Font font;
    private final List<Button> slots = new ArrayList<>();
    /** The history snapshot the slots show, or null before the first {@link #show}. */
    private List<String> shown;
    private HistoryStrip strip;

    /** {@code add} registers each slot with the screen, so Tab order follows this call. */
    HistoryButtons(LookupLayout layout, Font font, int width, int height,
                   UnaryOperator<Button> add, Consumer<String> lookUp) {
        this.layout = layout;
        this.font = font;
        for (int i = 0; i < LookupHistory.CAPACITY; i++) {
            int slot = i;
            Button button = add.apply(Button.builder(Component.empty(), ignored -> {
                        if (shown != null && slot < shown.size()) lookUp.accept(shown.get(slot));
                    })
                    .pos(0, layout.historyY()).size(width, height).build());
            button.visible = false;
            slots.add(button);
        }
    }

    /** Lays the slots out for these names. False when they already show them. */
    boolean show(List<String> names) {
        if (names.equals(shown)) return false;
        shown = names;
        strip = layout.history(font.width(Component.translatable("justtiers.lookup.recent")),
                names.stream().map(font::width).toList());
        for (int i = 0; i < slots.size(); i++) {
            Button button = slots.get(i);
            button.visible = i < strip.entries().size();
            if (!button.visible) continue;
            HistoryStrip.Entry entry = strip.entries().get(i);
            String name = names.get(i);
            button.setX(entry.x());
            button.setWidth(entry.width());
            button.setMessage(Component.literal(name));
            button.setTooltip(Tooltip.create(Component.translatable("justtiers.lookup.again", name)));
        }
        return true;
    }

    HistoryStrip strip() {
        return strip;
    }

    /** The names the visible slots show, in slot order. */
    List<String> visible() {
        return shown.subList(0, strip.entries().size());
    }

    /** The name on this widget if it is one of the slots, or null. */
    String nameOn(Object widget) {
        int slot = slots.indexOf(widget);
        return shown != null && slot >= 0 && slot < shown.size() ? shown.get(slot) : null;
    }

    Button slot(int index) {
        return slots.get(index);
    }
}
