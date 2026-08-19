package com.w0x7y.justtiers.gui;

import com.w0x7y.justtiers.render.Icons;
import com.w0x7y.justtiers.render.SiteColors;
import com.w0x7y.justtiers.gui.layout.CreditLine;
import com.w0x7y.justtiers.gui.layout.LookupLayout;
import com.w0x7y.justtiers.gui.layout.LookupMetrics;
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
 *
 * <p>Where everything sits is {@link LookupLayout}'s answer, worked out once in
 * {@link #init()} from what the font measures. This class keeps no coordinates of its
 * own.
 */
public final class PlayerLookupScreen extends Screen {

    private static final int CELL_TEXT_GAP = 2;
    private static final int CELL_SIDE_PADDING = 3;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 100;

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

    private final LookupSession session;

    private LookupLayout layout;
    private CreditLine credit;
    private int cellWidth;
    private int cellHeight;
    private int iconWidth;

    public PlayerLookupScreen(String name) {
        super(Component.translatable("justtiers.lookup.header", name));
        this.session = LookupSession.start(name);
    }

    @Override
    protected void init() {
        measureCells();
        layout = LookupLayout.of(metrics());
        credit = CreditLine.centeredIn(layout.panelX(), layout.panelWidth(),
                font.width(Component.translatable("justtiers.lookup.credit")),
                font.width(" "), font.width(SITE_SEPARATOR), siteNameWidths());

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .pos(width / 2 - BUTTON_WIDTH / 2, layout.doneButtonY())
                .size(BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    // ---------------------------------------------------------------- measuring

    /** What only the running game can measure. Every decision from here is arithmetic. */
    private LookupMetrics metrics() {
        List<Integer> counts = new ArrayList<>(Source.ALL.size());
        for (Source source : Source.ALL) {
            counts.add(Gamemodes.of(source).size());
        }
        return new LookupMetrics(width, height, font.lineHeight,
                Math.round(font.lineHeight * NAME_SCALE), widestSiteName(),
                cellWidth, cellHeight, counts);
    }

    private void measureCells() {
        iconWidth = 0;
        for (Gamemode gamemode : Gamemodes.ALL) {
            iconWidth = Math.max(iconWidth, font.width(Icons.of(gamemode.icon())));
        }
        cellWidth = iconWidth + CELL_TEXT_GAP + font.width(WIDEST_LABEL)
                + 2 * CELL_SIDE_PADDING;
        cellHeight = font.lineHeight + 5;
    }

    private int widestSiteName() {
        int widest = 0;
        for (Source source : Source.ALL) {
            widest = Math.max(widest, font.width(source.displayName()));
        }
        return widest;
    }

    private List<Integer> siteNameWidths() {
        List<Integer> widths = new ArrayList<>(Source.ALL.size());
        for (Source source : Source.ALL) {
            widths.add(font.width(source.displayName()));
        }
        return widths;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics,
                                   int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.fill(layout.panelX(), layout.panelY(),
                layout.panelRight(), layout.panelBottom(), PANEL_BACKGROUND);
        graphics.outline(layout.panelX(), layout.panelY(),
                layout.panelWidth(), layout.panelHeight(), PANEL_BORDER);
        separator(graphics, layout.firstSeparatorY());
        separator(graphics, layout.secondSeparatorY());
        separator(graphics, layout.thirdSeparatorY());

        drawName(graphics);
        Optional<Component> error = session.error();
        if (error.isPresent()) {
            graphics.centeredText(font, error.get(), width / 2, layout.skinCenterY(),
                    UNAVAILABLE_COLOR);
        } else {
            drawSkin(graphics);
            graphics.centeredText(font, Component.translatable("justtiers.lookup.tiers"),
                    width / 2, layout.tiersY(), Colors.SECONDARY);
            drawRows(graphics, mouseX, mouseY);
            if (session.rankedNowhere()) {
                graphics.centeredText(font,
                        Component.translatable("justtiers.lookup.none", session.name()),
                        width / 2, layout.noteY(), Colors.SECONDARY);
            }
        }
        drawFooter(graphics, mouseX, mouseY);
    }

    private void separator(GuiGraphicsExtractor graphics, int y) {
        graphics.horizontalLine(layout.panelX() + 1, layout.panelRight() - 2, y, SEPARATOR);
    }

    private void drawName(GuiGraphicsExtractor graphics) {
        String name = session.name();
        int nameWidth = Math.round(font.width(name) * NAME_SCALE);
        graphics.pose().pushMatrix();
        graphics.pose().translate((width - nameWidth) / 2f, (float) layout.nameY());
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
        int scale = layout.skinScale();
        int left = (width - SkinLayout.width(slim) * scale) / 2;

        for (SkinLayout.Piece piece : SkinLayout.pieces(slim)) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                    left + piece.x() * scale, layout.skinY() + piece.y() * scale,
                    piece.u(), piece.v(),
                    piece.width() * scale, piece.height() * scale,
                    piece.width(), piece.height(), SKIN_SIZE, SKIN_SIZE);
        }
    }

    private void drawRows(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (int i = 0; i < layout.rows().size(); i++) {
            Source source = Source.ALL.get(i);
            LookupLayout.Row row = layout.rows().get(i);
            int color = Colors.opaque(SiteColors.of(source));
            int textY = row.textTop(font.lineHeight);

            graphics.text(font, source.displayName(),
                    row.labelRight() - font.width(source.displayName()), textY, color);
            graphics.outline(row.x(), row.y(), row.width(), row.height(), color);

            Optional<LookupSection> section = session.section(source);
            if (section.isEmpty()) {
                graphics.centeredText(font, Component.translatable("justtiers.lookup.pending"),
                        row.centerX(), textY, Colors.SECONDARY);
            } else if (section.get().status() == LookupSection.Status.UNAVAILABLE) {
                graphics.centeredText(font,
                        Component.translatable("justtiers.lookup.unavailable"),
                        row.centerX(), textY, UNAVAILABLE_COLOR);
            } else {
                drawCells(graphics, row, source, section.get(), mouseX, mouseY);
            }
        }
    }

    private void drawCells(GuiGraphicsExtractor graphics, LookupLayout.Row row, Source source,
                           LookupSection section, int mouseX, int mouseY) {
        OptionalInt hovered = row.cellAt(mouseX, mouseY);
        List<LookupCell> cells = section.cells();
        for (int i = 0; i < cells.size() && i < row.grid().itemCount(); i++) {
            boolean isHovered = hovered.isPresent() && hovered.getAsInt() == i;
            drawCell(graphics, cells.get(i), source, row.cellX(i), row.cellY(i), isHovered);
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
        Component icon = Icons.of(cell.gamemode().icon());
        int contentWidth = iconWidth + CELL_TEXT_GAP + font.width(label);
        int textX = x + (cellWidth - contentWidth) / 2;
        int textY = y + (cellHeight - font.lineHeight) / 2 + 1;

        // Bitmap glyphs are multiplied by the text color, so the icon has to stay white
        // even in a row that is otherwise entirely its site's color.
        graphics.text(font, icon, textX + (iconWidth - font.width(icon)) / 2, textY,
                0xFFFFFFFF, false);
        graphics.text(font, label, textX + iconWidth + CELL_TEXT_GAP, textY,
                tier.isPresent() ? Colors.opaque(SiteColors.of(source)) : Colors.DISABLED, false);
    }

    private Component tooltip(LookupCell cell) {
        String gamemode = cell.gamemode().displayName();
        return cell.tier()
                .map(tier -> Component.literal(gamemode + ": " + tier.label()))
                .orElseGet(() -> Component.translatable("justtiers.lookup.cellUntested",
                        gamemode));
    }

    private void drawFooter(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int footerY = layout.footerY();
        graphics.text(font, Component.translatable("justtiers.lookup.credit"),
                credit.x(), footerY, Colors.SECONDARY);

        OptionalInt hovered = linkAt(mouseX, mouseY);
        for (int i = 0; i < credit.spans().size(); i++) {
            CreditLine.Span span = credit.spans().get(i);
            Source source = Source.ALL.get(i);
            int color = Colors.opaque(SiteColors.of(source));
            if (i > 0) {
                graphics.text(font, SITE_SEPARATOR, span.x() - font.width(SITE_SEPARATOR),
                        footerY, Colors.SECONDARY);
            }
            graphics.text(font, source.displayName(), span.x(), footerY, color);
            if (hovered.isPresent() && hovered.getAsInt() == i) {
                graphics.horizontalLine(span.x(), span.x() + span.width() - 1,
                        footerY + font.lineHeight - 1, color);
            }
        }
    }

    // ---------------------------------------------------------------- input

    /** The site name under the cursor, if the cursor is on the footer line at all. */
    private OptionalInt linkAt(double mouseX, double mouseY) {
        if (mouseY < layout.footerY() || mouseY >= layout.footerY() + font.lineHeight) {
            return OptionalInt.empty();
        }
        return credit.spanAt(mouseX);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            OptionalInt clicked = linkAt(event.x(), event.y());
            if (clicked.isPresent()) {
                ConfirmLinkScreen.confirmLinkNow(this,
                        Source.ALL.get(clicked.getAsInt()).homeUrl());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
