package com.w0x7y.justtiers.gui;

import com.w0x7y.justtiers.render.Icons;
import com.w0x7y.justtiers.render.SiteColors;
import com.w0x7y.justtiers.render.model.NametagSettings;
import com.w0x7y.justtiers.tier.Gamemodes;
import com.w0x7y.justtiers.tier.Source;
import dev.isxander.yacl3.api.Controller;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.AbstractWidget;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.controllers.ControllerWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/**
 * The option row that shows one site's current gamemode and opens the icon grid.
 *
 * <p>It is an {@code Option<String>} rather than a button so the pick lands in YACL's
 * pending state: Cancel discards it, Undo reverts it and the preview sees it at once.
 */
public final class GamemodePickerController implements Controller<String> {

    private final Option<String> option;
    private final Source source;
    private final Supplier<NametagSettings> previewState;

    public GamemodePickerController(Option<String> option, Source source,
                                    Supplier<NametagSettings> previewState) {
        this.option = option;
        this.source = source;
        this.previewState = previewState;
    }

    @Override
    public Option<String> option() {
        return option;
    }

    public Source source() {
        return source;
    }

    /** Falls back to the raw slug if a stored gamemode has since been removed. */
    public Component currentValue() {
        String slug = option.pendingValue();
        return Gamemodes.find(source, slug)
                // Two pieces under an empty root, not the name appended to the glyph: a
                // child inherits its parent's style, so hanging the name off the icon
                // would draw it in the icon font, as a row of missing-glyph boxes.
                .map(gamemode -> Component.empty()
                        .append(Icons.of(gamemode.icon()))
                        .append(Component.literal(" " + gamemode.displayName())))
                .orElseGet(() -> Component.literal(String.valueOf(slug)));
    }

    @Override
    public Component formatValue() {
        return currentValue();
    }

    @Override
    public AbstractWidget provideWidget(YACLScreen screen, Dimension<Integer> dim) {
        return new PickerWidget(this, screen, dim);
    }

    /** Extends YACL's own row widget so the row is indistinguishable from a native one. */
    private static final class PickerWidget extends ControllerWidget<GamemodePickerController> {

        private PickerWidget(GamemodePickerController control, YACLScreen screen,
                             Dimension<Integer> dim) {
            super(control, screen, dim);
        }

        @Override
        protected Component getValueText() {
            return control.currentValue();
        }

        @Override
        protected int getValueColor() {
            return isAvailable() ? Colors.opaque(SiteColors.of(control.source())) : inactiveColor;
        }

        @Override
        protected int getHoveredControlWidth() {
            return textRenderer.width(getValueText()) + 8;
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (!isAvailable() || event.button() != 0 || !isMouseOver(event.x(), event.y())) {
                return false;
            }
            playDownSound();
            openGrid();
            return true;
        }

        private void openGrid() {
            Option<String> option = control.option();
            client.setScreenAndShow(new GamemodeGridScreen(
                    screen,
                    control.source(),
                    option.pendingValue(),
                    control.previewState,
                    option::requestSet));
        }
    }
}
