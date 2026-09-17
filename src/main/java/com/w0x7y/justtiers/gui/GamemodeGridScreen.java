package com.w0x7y.justtiers.gui;

import com.w0x7y.justtiers.render.Icons;
import com.w0x7y.justtiers.render.SiteColors;
import com.mojang.blaze3d.platform.InputConstants;
import com.w0x7y.justtiers.gui.layout.GridLayout;
import com.w0x7y.justtiers.render.model.NametagSettings;
import com.w0x7y.justtiers.render.Nametags;
import com.w0x7y.justtiers.tier.Gamemode;
import com.w0x7y.justtiers.tier.Gamemodes;
import com.w0x7y.justtiers.tier.Source;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.ArrayList;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Full-screen icon grid for choosing one site's gamemode. Hovering a tile previews the
 * nametag that choice would produce, and a single click commits it and returns — there
 * is no confirm step, because there is nothing to confirm: the pick joins YACL's
 * pending state and Cancel still discards it.
 */
public final class GamemodeGridScreen extends Screen {

    private static final int TILE = 72;
    private static final int GAP = 8;
    private static final int MARGIN = 24;
    private static final int MAX_COLUMNS = 6;

    private static final int TITLE_Y = 14;
    private static final int PREVIEW_Y = 30;
    private static final int HINT_Y = 54;
    private static final int GRID_TOP = 70;
    private static final int FOOTER_HEIGHT = 44;

    private static final int TILE_BACKGROUND = 0x40000000;
    private static final int TILE_HOVERED = 0x60FFFFFF;
    private static final int HINT_COLOR = Colors.SECONDARY;
    private static final int LABEL_COLOR = 0xFFFFFFFF;
    private static final float ICON_SCALE = 2f;
    private static final float TAG_SCALE = 2f;

    private final Screen parent;
    private final Source source;
    private final List<Gamemode> gamemodes;
    private final Supplier<NametagSettings> baseState;
    private final Consumer<String> onPick;
    private final String selectedSlug;

    private GridLayout grid;
    private int originX;
    private int originY;
    private int viewportHeight;
    private int scroll;
    private final List<TileButton> tiles = new ArrayList<>();
    private int hoveredIndex = -1;

    public GamemodeGridScreen(Screen parent, Source source, String selectedSlug,
                              Supplier<NametagSettings> baseState, Consumer<String> onPick) {
        super(Component.translatable("justtiers.grid.title", source.displayName()));
        this.parent = parent;
        this.source = source;
        this.selectedSlug = selectedSlug;
        this.gamemodes = Gamemodes.of(source);
        this.baseState = baseState;
        this.onPick = onPick;

    }

    private int indexOf(String slug) {
        for (int i = 0; i < gamemodes.size(); i++) {
            if (gamemodes.get(i).slug().equals(slug)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void init() {
        layoutGrid();
        tiles.clear();
        for (int i = 0; i < gamemodes.size(); i++) {
            TileButton tile = new TileButton(i);
            tiles.add(addWidget(tile));
        }
        positionTiles();
        addRenderableWidget(Button.builder(
                        Component.translatable("justtiers.grid.back"), button -> onClose())
                .pos(width / 2 - 50, height - 30).size(100, 20).build());
    }

    @Override
    protected void repositionElements() {
        super.repositionElements();
        layoutGrid();
    }

    private void layoutGrid() {
        grid = GridLayout.of(gamemodes.size(), width - 2 * MARGIN, TILE, TILE, GAP, MAX_COLUMNS);
        originX = (width - grid.contentWidth()) / 2;
        originY = GRID_TOP;
        viewportHeight = Math.max(0, height - FOOTER_HEIGHT - originY);
        scroll = Math.clamp(scroll, 0, maxScroll());
        positionTiles();
    }

    private void positionTiles() {
        for (TileButton tile : tiles) {
            tile.setX(originX + grid.xOf(tile.index));
            tile.setY(originY + grid.yOf(tile.index) - scroll);
        }
    }

    @Override
    protected void setInitialFocus() {
        if (!tiles.isEmpty()) setFocused(tiles.get(Math.max(0, indexOf(selectedSlug))));
    }

    @Override
    public void setFocused(GuiEventListener listener) {
        super.setFocused(listener);
        if (listener instanceof TileButton tile) scrollTo(tile.index);
    }

    private int maxScroll() {
        return Math.max(0, grid.contentHeight() - viewportHeight);
    }

    /** The pending settings with this site's gamemode swapped for the candidate slug. */
    private NametagSettings stateFor(String slug) {
        return baseState.get().withGamemode(source, slug);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics,
                                   int mouseX, int mouseY, float delta) {
        hoveredIndex = indexAtScreen(mouseX, mouseY).orElse(-1);
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.centeredText(font, title, width / 2, TITLE_Y, Colors.opaque(SiteColors.of(source)));
        extractPreview(graphics);
        graphics.centeredText(font, Component.translatable("justtiers.grid.hint"),
                width / 2, HINT_Y, HINT_COLOR);

        extractTiles(graphics);
    }

    private void extractPreview(GuiGraphicsExtractor graphics) {
        int focused = getFocused() instanceof TileButton tile ? tile.index : indexOf(selectedSlug);
        int candidate = minecraft.getLastInputType().isKeyboard() || hoveredIndex < 0
                ? focused : hoveredIndex;
        String slug = candidate >= 0 ? gamemodes.get(candidate).slug() : selectedSlug;
        NametagSettings state = stateFor(slug);
        Component tag = Nametags.compose(state.previewBadge(System.currentTimeMillis()),
                PreviewName.component());

        float scale = Math.min(TAG_SCALE, (float) Math.max(1, width - 16)
                / Math.max(1, font.width(tag)));
        int tagWidth = Math.round(font.width(tag) * scale);
        graphics.pose().pushMatrix();
        graphics.pose().translate((width - tagWidth) / 2f, (float) PREVIEW_Y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, tag, 0, 0, 0xFFFFFFFF, true);
        graphics.pose().popMatrix();
    }

    private void extractTiles(GuiGraphicsExtractor graphics) {
        if (grid.itemCount() == 0 || viewportHeight <= 0) {
            return;
        }
        graphics.enableScissor(0, originY, width, originY + viewportHeight);
        for (int i = 0; i < gamemodes.size(); i++) {
            tiles.get(i).extractRenderState(graphics,
                    hoveredIndex == i ? tiles.get(i).getX() + 1 : -1,
                    hoveredIndex == i ? tiles.get(i).getY() + 1 : -1, 0);
        }
        graphics.disableScissor();
    }

    private void extractTile(GuiGraphicsExtractor graphics, int index) {
        Gamemode gamemode = gamemodes.get(index);
        int x = originX + grid.xOf(index);
        int y = originY + grid.yOf(index) - scroll;
        if (y + TILE < originY || y > originY + viewportHeight) {
            return;   // scrolled out of the viewport
        }

        boolean highlighted = tiles.get(index).isHoveredOrFocused();
        graphics.fill(x, y, x + TILE, y + TILE, highlighted ? TILE_HOVERED : TILE_BACKGROUND);
        if (gamemode.slug().equals(selectedSlug)) {
            // The only color on this screen besides the title: which site you are in.
            graphics.outline(x, y, TILE, TILE, Colors.opaque(SiteColors.of(source)));
        }

        Component icon = Icons.of(gamemode.icon());
        int iconWidth = Math.round(font.width(icon) * ICON_SCALE);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + (TILE - iconWidth) / 2f, y + 16f);
        graphics.pose().scale(ICON_SCALE, ICON_SCALE);
        graphics.text(font, icon, 0, 0, 0xFFFFFFFF, false);
        graphics.pose().popMatrix();

        int labelY = y + TILE - 26;
        for (var line : font.split(Component.literal(gamemode.displayName()), TILE - 6)) {
            graphics.text(font, line, x + (TILE - font.width(line)) / 2, labelY, LABEL_COLOR);
            labelY += font.lineHeight;
        }
    }

    private OptionalInt indexAtScreen(double mouseX, double mouseY) {
        if (grid == null || mouseY < originY || mouseY > originY + viewportHeight) {
            return OptionalInt.empty();
        }
        return grid.indexAt((int) mouseX - originX, (int) mouseY - originY + scroll);
    }

    private void pick(int index) {
        onPick.accept(gamemodes.get(index).slug());
        minecraft.setScreenAndShow(parent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontal, double vertical) {
        if (maxScroll() > 0) {
            scroll = Math.clamp(scroll - (int) (vertical * 16), 0, maxScroll());
            positionTiles();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        GridLayout.Direction direction = switch (event.key()) {
            case InputConstants.KEY_LEFT -> GridLayout.Direction.LEFT;
            case InputConstants.KEY_RIGHT -> GridLayout.Direction.RIGHT;
            case InputConstants.KEY_UP -> GridLayout.Direction.UP;
            case InputConstants.KEY_DOWN -> GridLayout.Direction.DOWN;
            default -> null;
        };
        if (direction != null && getFocused() instanceof TileButton tile) {
            int next = grid.move(tile.index, direction);
            if (next != tile.index) {
                setFocused(tiles.get(next));
                return true;
            }
        }
        return super.keyPressed(event);
    }

    /** Keeps the keyboard focus inside the viewport when navigation runs off the edge. */
    private void scrollTo(int index) {
        int top = grid.yOf(index);
        int bottom = top + TILE;
        if (top < scroll) {
            scroll = top;
        } else if (bottom > scroll + viewportHeight) {
            scroll = bottom - viewportHeight;
        }
        scroll = Math.clamp(scroll, 0, maxScroll());
        positionTiles();
    }

    /** Native focus, activation and narration, with only the tile artwork customized. */
    private final class TileButton extends AbstractButton {
        private final int index;

        private TileButton(int index) {
            super(0, 0, TILE, TILE, Component.literal(gamemodes.get(index).displayName()));
            this.index = index;
            setTooltip(Tooltip.create(getMessage()));
        }

        @Override
        public void onPress(InputWithModifiers input) {
            pick(index);
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return mouseY >= originY && mouseY < originY + viewportHeight
                    && super.isMouseOver(mouseX, mouseY);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            return isMouseOver(event.x(), event.y()) && super.mouseClicked(event, doubleClick);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            extractTile(graphics, index);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }
}
