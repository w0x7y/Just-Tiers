package com.w0x7y.justtiers.gui;

import com.w0x7y.justtiers.gui.state.PreviewState;
import com.w0x7y.justtiers.preview.PreviewSample;
import com.w0x7y.justtiers.render.Segments;
import com.w0x7y.justtiers.render.model.Segment;
import dev.isxander.yacl3.api.Controller;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.AbstractWidget;
import dev.isxander.yacl3.gui.YACLScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.function.Supplier;

/**
 * A read-only option row that draws the nametag the current pending settings would
 * produce, under the player's own name. The tiers come from {@link PreviewSample} and
 * are made up — always tier 1 — so this is a picture of the settings, never a
 * leaderboard lookup; the shape and colours still come from the same {@code NametagModel}
 * the world nametag uses.
 *
 * <p>The widget re-reads its {@link PreviewState} supplier every frame, so it follows
 * pending edits with no listener wiring at all.
 */
public final class NametagPreviewController implements Controller<Component> {

    private static final int ROW_HEIGHT = 56;
    private static final int PLATE_INSET = 4;
    private static final int CONTENT_INSET = 12;
    private static final int PLATE_BACKGROUND = 0x40000000;
    private static final int PLATE_OUTLINE = 0x30FFFFFF;
    private static final int CAPTION_COLOR = Colors.SECONDARY;
    private static final int CAPTION_DISABLED_COLOR = Colors.DISABLED;
    /** How far the tag is dimmed while the mod is switched off. */
    private static final float DISABLED_TINT = 0.4f;
    private static final float TAG_SCALE = 2f;

    private final Option<Component> option;
    private final Supplier<PreviewState> state;

    private NametagPreviewController(Option<Component> option, Supplier<PreviewState> state) {
        this.option = option;
        this.state = state;
    }

    /** Builds the preview row. The binding is a no-op: the preview stores nothing. */
    public static Option<Component> option(Supplier<PreviewState> state) {
        return Option.<Component>createBuilder()
                .name(Component.empty())
                .binding(Component.empty(), Component::empty, value -> {
                })
                .customController(opt -> new NametagPreviewController(opt, state))
                .build();
    }

    @Override
    public Option<Component> option() {
        return option;
    }

    @Override
    public Component formatValue() {
        return tag(state.get(), null, System.currentTimeMillis());
    }

    @Override
    public AbstractWidget provideWidget(YACLScreen screen, Dimension<Integer> dim) {
        return new PreviewWidget(dim);
    }

    /**
     * The tag as a component. {@code widget} is passed only so a disabled preview can
     * borrow YACL's colour-multiply; pass null for an undimmed tag.
     */
    private MutableComponent tag(PreviewState current, PreviewWidget widget, long timeMillis) {
        List<Segment> segments = PreviewSample.segments(current.displayMode(),
                current.selectedGamemodes(), current.showRetired(), timeMillis,
                current.style());
        if (widget != null && !current.enabled()) {
            segments = segments.stream()
                    .map(segment -> segment.withColor(widget.dim(segment.color())))
                    .toList();
        }
        return Segments.compose(segments, PreviewName.component(),
                current.style().position());
    }

    private Component caption(PreviewState current) {
        return current.enabled()
                ? Component.translatable("justtiers.preview.example")
                : Component.translatable("justtiers.preview.off");
    }

    /** Inert by design — the preview is something to look at, not something to click. */
    private final class PreviewWidget extends AbstractWidget {

        private PreviewWidget(Dimension<Integer> dim) {
            super(dim.withHeight(ROW_HEIGHT));
        }

        /**
         * The row list hands widgets a fresh dimension on every relayout, so the
         * self-sizing has to be reapplied here rather than only in the constructor.
         */
        @Override
        public void setDimension(Dimension<Integer> dim) {
            super.setDimension(dim.withHeight(ROW_HEIGHT));
        }

        private int dim(int color) {
            return multiplyColor(color, DISABLED_TINT);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics,
                                       int mouseX, int mouseY, float delta) {
            Dimension<Integer> dim = getDimension();
            PreviewState current = state.get();

            int left = dim.x() + PLATE_INSET;
            int top = dim.y() + PLATE_INSET;
            int right = dim.xLimit() - PLATE_INSET;
            int bottom = dim.yLimit() - PLATE_INSET;
            graphics.fill(left, top, right, bottom, PLATE_BACKGROUND);
            graphics.outline(left, top, right - left, bottom - top, PLATE_OUTLINE);

            int contentX = dim.x() + CONTENT_INSET;
            int tagY = dim.y() + CONTENT_INSET;

            // Drawn at 2x so the 8x8 gamemode glyphs are legible; the per-segment
            // colours survive the scale because they live in the component's Style.
            graphics.pose().pushMatrix();
            graphics.pose().translate(contentX, tagY);
            graphics.pose().scale(TAG_SCALE, TAG_SCALE);
            graphics.text(textRenderer, tag(current, this, System.currentTimeMillis()),
                    0, 0, 0xFFFFFFFF, true);
            graphics.pose().popMatrix();

            int captionY = tagY + Math.round(textRenderer.lineHeight * TAG_SCALE) + 6;
            graphics.text(textRenderer, caption(current), contentX, captionY,
                    current.enabled() ? CAPTION_COLOR : CAPTION_DISABLED_COLOR, false);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            return false;
        }

        @Override
        public boolean canReset() {
            return false;
        }

        // Not focusable: tab should walk straight past the preview to the first control.
        @Override
        public boolean isFocused() {
            return false;
        }

        @Override
        public void setFocused(boolean focused) {
        }

        @Override
        public NarratableEntry.NarrationPriority narrationPriority() {
            return NarratableEntry.NarrationPriority.NONE;
        }

        @Override
        public void updateNarration(NarrationElementOutput output) {
            PreviewState current = state.get();
            output.add(NarratedElementType.TITLE, tag(current, null, System.currentTimeMillis()));
            output.add(NarratedElementType.HINT, caption(current));
        }

        @Override
        public boolean matchesSearch(String query) {
            return false;
        }
    }
}
