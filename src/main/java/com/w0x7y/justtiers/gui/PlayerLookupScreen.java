package com.w0x7y.justtiers.gui;

import com.w0x7y.justtiers.gui.layout.GridLayout;
import com.w0x7y.justtiers.gui.layout.SkinLayout;
import com.w0x7y.justtiers.lookup.LookupCell;
import com.w0x7y.justtiers.lookup.LookupSection;
import com.w0x7y.justtiers.tier.Gamemode;
import com.w0x7y.justtiers.tier.Gamemodes;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The screen {@code /justtiers lookup} opens: a player's name, their skin, and one row
 * per site showing every gamemode that site runs — the tier they hold in it, or dashes
 * where they have never been tested.
 *
 * <p>Rows fill in one at a time as their site answers. Everything the screen draws lives
 * in a {@link LookupSession}, so this class only ever reads and never waits.
 *
 * <p>Unlike the nametag, this screen always draws the gamemode icons: on a nametag an
 * icon says which gamemode earned a tier, but here it is the only thing naming the
 * column, and a row of bare tiers would say nothing about what they were earned in.
 */
public final class PlayerLookupScreen extends Screen {

    private static final int PANEL_PADDING = 10;
    private static final int SECTION_GAP = 10;
    private static final int BOX_PADDING = 4;
    private static final int CELL_GAP = 2;
    private static final int CELL_TEXT_GAP = 2;
    private static final int CELL_SIDE_PADDING = 3;
    private static final int ROW_GAP = 4;
    private static final int LABEL_GAP = 6;
    private static final int SCREEN_MARGIN = 8;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 6;

    private static final int PANEL_BACKGROUND = 0xC0000000;
    private static final int PANEL_BORDER = 0xFF3A3A3A;
    private static final int SEPARATOR = 0xFF3A3A3A;
    private static final int CELL_BACKGROUND = 0x40000000;
    private static final int CELL_HOVERED = 0x60FFFFFF;
    private static final int NAME_COLOR = 0xFFFFFFFF;
    private static final int UNAVAILABLE_COLOR = 0xFFFF5555;
    private static final float NAME_SCALE = 1.5f;

    /** Every tier fits in four characters, retired ones included, so cells never jump. */
    private static final String WIDEST_LABEL = "RHT5";
    private static final String NOT_TESTED = "---";
    private static final String SITE_SEPARATOR = " · ";
    private static final int SKIN_SIZE = 64;
    private static final int[] SKIN_SCALES = {3, 2, 1};

    private final LookupSession session;
    private final Screen parent;

    private final List<Row> rows = new ArrayList<>(Source.ALL.size());
    private final List<Link> links = new ArrayList<>(Source.ALL.size());

    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int nameY;
    private int skinY;
    private int skinScale = SKIN_SCALES[0];
    private int tiersY;
    private int noteY;
    private int footerY;
    private int creditX;
    private int firstSeparatorY;
    private int secondSeparatorY;
    private int thirdSeparatorY;
    private int cellWidth;
    private int cellHeight;
    private int iconWidth;

    /** One site's row: the box, and the grid its cells sit in inside that box. */
    private record Row(Source source, GridLayout grid, int x, int y, int width, int height) {
    }

    /** A site name in the footer credit, and the page it opens. */
    private record Link(Source source, int x, int width) {
    }

    public PlayerLookupScreen(String name) {
        this(name, null);
    }

    /**
     * Opened from somewhere worth going back to — the scan screen — rather than from a
     * command. Closing returns there with its state intact instead of closing the game
     * menu outright.
     */
    public PlayerLookupScreen(String name, Screen parent) {
        super(Component.translatable("justtiers.lookup.header", name));
        this.session = LookupSession.start(name);
        this.parent = parent;
    }

    @Override
    public void onClose() {
        if (parent == null) {
            super.onClose();
            return;
        }
        minecraft.setScreenAndShow(parent);
    }

    @Override
    protected void init() {
        int doneY = layout();
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .pos(width / 2 - 50, doneY).size(100, BUTTON_HEIGHT).build());
    }

    // ---------------------------------------------------------------- layout

    /**
     * Places everything and returns the y for the Done button. The whole panel is sized
     * once, up front, from the sites' gamemode counts rather than from the answers that
     * have arrived — a panel that resized itself as each site replied would shift under
     * the cursor mid-read.
     */
    private int layout() {
        measureCells();

        int labelWidth = labelWidth();
        int available = Math.max(cellWidth,
                width - 2 * SCREEN_MARGIN - 2 * PANEL_PADDING - labelWidth - 2 * BOX_PADDING);

        List<GridLayout> grids = new ArrayList<>(Source.ALL.size());
        int widest = 0;
        for (Source source : Source.ALL) {
            int count = Gamemodes.of(source).size();
            GridLayout grid = GridLayout.of(count, available, cellWidth, cellHeight,
                    CELL_GAP, count);
            grids.add(grid);
            widest = Math.max(widest, grid.contentWidth());
        }

        panelWidth = Math.min(width - 2 * SCREEN_MARGIN,
                labelWidth + widest + 2 * BOX_PADDING + 2 * PANEL_PADDING);
        panelX = (width - panelWidth) / 2;

        skinScale = chooseSkinScale(grids);
        panelHeight = place(grids, labelWidth, 0);
        panelY = Math.max(SCREEN_MARGIN,
                (height - panelHeight - BUTTON_HEIGHT - BUTTON_GAP) / 2);
        place(grids, labelWidth, panelY);

        layoutFooterLinks();
        return panelY + panelHeight + BUTTON_GAP;
    }

    /** The largest skin that still leaves the Done button on screen. */
    private int chooseSkinScale(List<GridLayout> grids) {
        int labelWidth = labelWidth();
        for (int scale : SKIN_SCALES) {
            skinScale = scale;
            int needed = place(grids, labelWidth, 0)
                    + BUTTON_GAP + BUTTON_HEIGHT + 2 * SCREEN_MARGIN;
            if (needed <= height) {
                return scale;
            }
        }
        return SKIN_SCALES[SKIN_SCALES.length - 1];
    }

    /**
     * Stacks the panel's blocks from {@code top} downwards and returns the panel height.
     * Called once with a top of zero to measure, then again to place.
     */
    private int place(List<GridLayout> grids, int labelWidth, int top) {
        int y = top + PANEL_PADDING;

        nameY = y;
        y += nameHeight();
        y += SECTION_GAP / 2;
        firstSeparatorY = y;
        y += SECTION_GAP / 2;

        skinY = y;
        y += SkinLayout.HEIGHT * skinScale;
        y += SECTION_GAP / 2;
        secondSeparatorY = y;
        y += SECTION_GAP / 2;

        tiersY = y;
        y += font.lineHeight + 5;

        rows.clear();
        int boxX = panelX + PANEL_PADDING + labelWidth;
        int boxWidth = panelWidth - 2 * PANEL_PADDING - labelWidth;
        for (int i = 0; i < Source.ALL.size(); i++) {
            GridLayout grid = grids.get(i);
            int boxHeight = grid.contentHeight() + 2 * BOX_PADDING;
            rows.add(new Row(Source.ALL.get(i), grid, boxX, y, boxWidth, boxHeight));
            y += boxHeight + ROW_GAP;
        }
        y -= ROW_GAP;

        // Reserved whether or not the note is showing: it only becomes true once the last
        // site answers, and the panel must not grow a line under the cursor when it does.
        noteY = y + 5;
        y = noteY + font.lineHeight;

        y += SECTION_GAP / 2;
        thirdSeparatorY = y;
        y += SECTION_GAP / 2;

        footerY = y;
        y += font.lineHeight + PANEL_PADDING;
        return y - top;
    }

    private void measureCells() {
        iconWidth = 0;
        for (Gamemode gamemode : Gamemodes.ALL) {
            iconWidth = Math.max(iconWidth, font.width(String.valueOf(gamemode.icon())));
        }
        cellWidth = iconWidth + CELL_TEXT_GAP + font.width(WIDEST_LABEL)
                + 2 * CELL_SIDE_PADDING;
        cellHeight = font.lineHeight + 5;
    }

    private int labelWidth() {
        int widest = 0;
        for (Source source : Source.ALL) {
            widest = Math.max(widest, font.width(source.displayName()));
        }
        return widest + LABEL_GAP;
    }

    private int nameHeight() {
        return Math.round(font.lineHeight * NAME_SCALE);
    }

    private void layoutFooterLinks() {
        links.clear();
        Component prefix = Component.translatable("justtiers.lookup.credit");
        int total = font.width(prefix) + font.width(" ");
        for (int i = 0; i < Source.ALL.size(); i++) {
            total += font.width(Source.ALL.get(i).displayName());
            if (i > 0) {
                total += font.width(SITE_SEPARATOR);
            }
        }

        creditX = panelX + (panelWidth - total) / 2;
        int x = creditX + font.width(prefix) + font.width(" ");
        for (int i = 0; i < Source.ALL.size(); i++) {
            if (i > 0) {
                x += font.width(SITE_SEPARATOR);
            }
            Source source = Source.ALL.get(i);
            int textWidth = font.width(source.displayName());
            links.add(new Link(source, x, textWidth));
            x += textWidth;
        }
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics,
                                   int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight,
                PANEL_BACKGROUND);
        graphics.outline(panelX, panelY, panelWidth, panelHeight, PANEL_BORDER);
        separator(graphics, firstSeparatorY);
        separator(graphics, secondSeparatorY);
        separator(graphics, thirdSeparatorY);

        drawName(graphics);
        Optional<Component> error = session.error();
        if (error.isPresent()) {
            drawError(graphics, error.get());
        } else {
            drawSkin(graphics);
            graphics.centeredText(font, Component.translatable("justtiers.lookup.tiers"),
                    width / 2, tiersY, Colors.SECONDARY);
            drawRows(graphics, mouseX, mouseY);
            if (session.rankedNowhere()) {
                graphics.centeredText(font,
                        Component.translatable("justtiers.lookup.none", session.name()),
                        width / 2, noteY, Colors.SECONDARY);
            }
        }
        drawFooter(graphics, mouseX, mouseY);
    }

    private void separator(GuiGraphicsExtractor graphics, int y) {
        graphics.horizontalLine(panelX + 1, panelX + panelWidth - 2, y, SEPARATOR);
    }

    private void drawName(GuiGraphicsExtractor graphics) {
        String name = session.name();
        int nameWidth = Math.round(font.width(name) * NAME_SCALE);
        graphics.pose().pushMatrix();
        graphics.pose().translate((width - nameWidth) / 2f, (float) nameY);
        graphics.pose().scale(NAME_SCALE, NAME_SCALE);
        graphics.text(font, name, 0, 0, NAME_COLOR, true);
        graphics.pose().popMatrix();
    }

    /**
     * The skin drawn flat, front on, straight out of its texture. Nothing here needs a
     * world or an entity, so it works on the title screen as well as in game.
     */
    private void drawSkin(GuiGraphicsExtractor graphics) {
        PlayerSkin skin = session.skin();
        Identifier texture = skin.body().texturePath();
        boolean slim = skin.model() == PlayerModelType.SLIM;
        int left = (width - SkinLayout.width(slim) * skinScale) / 2;

        for (SkinLayout.Piece piece : SkinLayout.pieces(slim)) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                    left + piece.x() * skinScale, skinY + piece.y() * skinScale,
                    piece.u(), piece.v(),
                    piece.width() * skinScale, piece.height() * skinScale,
                    piece.width(), piece.height(), SKIN_SIZE, SKIN_SIZE);
        }
    }

    private void drawError(GuiGraphicsExtractor graphics, Component message) {
        graphics.centeredText(font, message, width / 2,
                skinY + (SkinLayout.HEIGHT * skinScale) / 2, UNAVAILABLE_COLOR);
    }

    private void drawRows(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (Row row : rows) {
            int textY = row.y() + (row.height() - font.lineHeight) / 2;
            graphics.text(font, row.source().displayName(),
                    row.x() - LABEL_GAP - font.width(row.source().displayName()), textY,
                    Colors.opaque(row.source().defaultColor()));
            graphics.outline(row.x(), row.y(), row.width(), row.height(),
                    Colors.opaque(row.source().defaultColor()));

            Optional<LookupSection> section = session.section(row.source());
            if (section.isEmpty()) {
                graphics.centeredText(font, Component.translatable("justtiers.lookup.pending"),
                        row.x() + row.width() / 2, textY, Colors.SECONDARY);
            } else if (section.get().status() == LookupSection.Status.UNAVAILABLE) {
                graphics.centeredText(font,
                        Component.translatable("justtiers.lookup.unavailable"),
                        row.x() + row.width() / 2, textY, UNAVAILABLE_COLOR);
            } else {
                drawCells(graphics, row, section.get(), mouseX, mouseY);
            }
        }
    }

    private void drawCells(GuiGraphicsExtractor graphics, Row row, LookupSection section,
                           int mouseX, int mouseY) {
        OptionalInt hovered = cellAt(row, mouseX, mouseY);
        List<LookupCell> cells = section.cells();
        for (int i = 0; i < cells.size() && i < row.grid().itemCount(); i++) {
            int x = cellsLeft(row) + row.grid().xOf(i);
            int y = row.y() + BOX_PADDING + row.grid().yOf(i);
            boolean isHovered = hovered.isPresent() && hovered.getAsInt() == i;
            drawCell(graphics, cells.get(i), row.source(), x, y, isHovered);
            if (isHovered) {
                graphics.setTooltipForNextFrame(font, tooltip(cells.get(i)), mouseX, mouseY);
            }
        }
    }

    private void drawCell(GuiGraphicsExtractor graphics, LookupCell cell, Source source,
                          int x, int y, boolean hovered) {
        graphics.fill(x, y, x + cellWidth, y + cellHeight,
                hovered ? CELL_HOVERED : CELL_BACKGROUND);

        Optional<Tier> tier = cell.tier();
        String label = tier.map(Tier::label).orElse(NOT_TESTED);
        String icon = String.valueOf(cell.gamemode().icon());
        int contentWidth = iconWidth + CELL_TEXT_GAP + font.width(label);
        int textX = x + (cellWidth - contentWidth) / 2;
        int textY = y + (cellHeight - font.lineHeight) / 2 + 1;

        // Bitmap glyphs are multiplied by the text colour, so the icon has to stay white
        // even in a row that is otherwise entirely its site's colour.
        graphics.text(font, icon, textX + (iconWidth - font.width(icon)) / 2, textY,
                0xFFFFFFFF, false);
        graphics.text(font, label, textX + iconWidth + CELL_TEXT_GAP, textY,
                tier.isPresent() ? Colors.opaque(source.defaultColor()) : Colors.DISABLED, false);
    }

    private Component tooltip(LookupCell cell) {
        String gamemode = cell.gamemode().displayName();
        return cell.tier()
                .map(tier -> Component.literal(gamemode + ": " + tier.label()))
                .orElseGet(() -> Component.translatable("justtiers.lookup.cellUntested",
                        gamemode));
    }

    private void drawFooter(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, Component.translatable("justtiers.lookup.credit"),
                creditX, footerY, Colors.SECONDARY);
        for (int i = 1; i < links.size(); i++) {
            Link link = links.get(i);
            graphics.text(font, SITE_SEPARATOR,
                    link.x() - font.width(SITE_SEPARATOR), footerY, Colors.SECONDARY);
        }
        for (Link link : links) {
            int color = Colors.opaque(link.source().defaultColor());
            graphics.text(font, link.source().displayName(), link.x(), footerY, color);
            if (isOver(link, mouseX, mouseY)) {
                graphics.horizontalLine(link.x(), link.x() + link.width() - 1,
                        footerY + font.lineHeight - 1, color);
            }
        }
    }

    // ---------------------------------------------------------------- input

    /**
     * Every box is as wide as the widest site's row, so a site with fewer gamemodes has
     * room to spare; its cells are centred in it rather than left hanging off one edge.
     */
    private int cellsLeft(Row row) {
        return row.x() + (row.width() - row.grid().contentWidth()) / 2;
    }

    private OptionalInt cellAt(Row row, double mouseX, double mouseY) {
        return row.grid().indexAt(
                (int) mouseX - cellsLeft(row),
                (int) mouseY - row.y() - BOX_PADDING);
    }

    private boolean isOver(Link link, double mouseX, double mouseY) {
        return mouseX >= link.x() && mouseX < link.x() + link.width()
                && mouseY >= footerY && mouseY < footerY + font.lineHeight;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            for (Link link : links) {
                if (isOver(link, event.x(), event.y())) {
                    ConfirmLinkScreen.confirmLinkNow(this, link.source().homeUrl());
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
