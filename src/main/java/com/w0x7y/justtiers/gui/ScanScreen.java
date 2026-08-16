package com.w0x7y.justtiers.gui;

import com.w0x7y.justtiers.render.Icons;
import com.w0x7y.justtiers.render.SiteColors;
import com.w0x7y.justtiers.gui.layout.GridLayout;
import com.w0x7y.justtiers.gui.layout.ScanLayout;
import com.w0x7y.justtiers.gui.layout.SkinLayout;
import com.w0x7y.justtiers.lookup.LookupCell;
import com.w0x7y.justtiers.lookup.LookupSection;
import com.w0x7y.justtiers.scan.ScanRow;
import com.w0x7y.justtiers.tier.Gamemodes;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * The screen {@code /justtiers scan} opens: everyone on the server, scored out of every
 * placement they hold on all three sites, most dangerous first.
 *
 * <p>Everything drawn lives in a {@link ScanSession}, which hands over a list that is
 * already sorted and immutable, so this class never sorts, never waits and never copies.
 * The list re-orders itself under the cursor for the first seconds of a scan; the
 * progress readout in the header is what explains that.
 *
 * <p>Retired placements are absent here — stripped by the session before a row is built —
 * because a scan asks who is a threat now rather than what anyone has ever earned.
 */
public final class ScanScreen extends Screen {

    private static final int MARGIN = 8;
    private static final int HEADER_HEIGHT = 34;
    private static final int FOOTER_HEIGHT = 32;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_PADDING = 4;
    private static final int COLUMN_GAP = 6;
    private static final int CELL_GAP = 2;
    private static final int CELL_TEXT_GAP = 2;
    private static final int CELL_SIDE_PADDING = 3;
    private static final int NAME_GAP = 4;
    private static final int SCROLLBAR_WIDTH = 4;
    private static final int HEAD_SCALE = 2;
    private static final int HEAD_PIXELS = 8;
    private static final int SKIN_SIZE = 64;
    private static final int MIN_THUMB = 16;

    private static final int PANEL_BACKGROUND = 0xC0000000;
    private static final int PANEL_BORDER = 0xFF3A3A3A;
    private static final int SEPARATOR = 0xFF3A3A3A;
    private static final int CELL_BACKGROUND = 0x40000000;
    private static final int ROW_HOVERED = 0x20FFFFFF;
    private static final int SCROLLBAR = 0xFF6A6A6A;
    private static final int NAME_COLOR = 0xFFFFFFFF;
    private static final int POINTS_COLOR = 0xFFFFFFFF;
    private static final int UNAVAILABLE_COLOR = 0xFFFF5555;

    /** Every tier fits in four characters, so cells never jump as answers land. */
    private static final String WIDEST_LABEL = "RHT5";
    private static final String NOT_TESTED = "---";
    /** Sixteen characters is the longest name Mojang issues. */
    private static final String WIDEST_NAME = "MMMMMMMMMMMMMMMM";

    private final ScanSession session;
    private final Map<UUID, PlayerSkin> skins = new HashMap<>();
    private final Map<Source, GridLayout> grids = new EnumMap<>(Source.class);

    private ScanLayout layout = ScanLayout.of(0, 1, 0);
    private int scroll;
    private int listTop;
    private int listBottom;
    private int listLeft;
    private int listRight;
    private int nameColumnWidth;
    private int columnWidth;
    private int rowHeight;
    private int cellWidth;
    private int cellHeight;
    private int iconWidth;

    public ScanScreen() {
        super(Component.translatable("justtiers.scan.title"));
        this.session = ScanSession.start();
    }

    @Override
    protected void init() {
        measure();
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .pos(width / 2 - 50, height - MARGIN - BUTTON_HEIGHT)
                .size(100, BUTTON_HEIGHT).build());
    }

    // ---------------------------------------------------------------- layout

    /**
     * Sized once, from the sites' gamemode counts rather than from the answers that have
     * arrived: a row that grew as each site replied would shift the list under the cursor
     * mid-read, on top of the re-sorting it already does.
     */
    private void measure() {
        iconWidth = font.width(Icons.of(Gamemodes.of(Source.MCTIERS).get(0).icon()));
        cellWidth = iconWidth + CELL_TEXT_GAP + font.width(WIDEST_LABEL)
                + 2 * CELL_SIDE_PADDING;
        cellHeight = font.lineHeight + 4;

        nameColumnWidth = HEAD_PIXELS * HEAD_SCALE + NAME_GAP + font.width(WIDEST_NAME);

        listLeft = MARGIN;
        listRight = width - MARGIN;
        listTop = MARGIN + HEADER_HEIGHT;
        listBottom = height - FOOTER_HEIGHT - MARGIN;

        int available = listRight - listLeft - nameColumnWidth - SCROLLBAR_WIDTH
                - 2 * ROW_PADDING - COLUMN_GAP;
        columnWidth = ScanLayout.columnWidth(available, Source.ALL.size(), COLUMN_GAP);

        int tallest = 0;
        grids.clear();
        for (Source source : Source.ALL) {
            int count = Gamemodes.of(source).size();
            GridLayout grid = GridLayout.of(count, columnWidth, cellWidth, cellHeight,
                    CELL_GAP, count);
            grids.put(source, grid);
            tallest = Math.max(tallest, grid.rows() * (cellHeight + CELL_GAP) - CELL_GAP);
        }

        rowHeight = Math.max(tallest, 2 * font.lineHeight + 2) + 2 * ROW_PADDING;
        rebuildLayout();
    }

    private void rebuildLayout() {
        layout = ScanLayout.of(session.rows().size(), rowHeight, listBottom - listTop);
        scroll = layout.clampScroll(scroll);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics,
                                   int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        rebuildLayout();

        graphics.fill(listLeft, listTop, listRight, listBottom, PANEL_BACKGROUND);
        graphics.outline(listLeft, listTop, listRight - listLeft, listBottom - listTop,
                PANEL_BORDER);
        drawHeader(graphics);

        Optional<Component> error = session.error();
        if (error.isPresent()) {
            graphics.centeredText(font, error.get(), width / 2,
                    (listTop + listBottom) / 2, Colors.SECONDARY);
            return;
        }

        graphics.enableScissor(listLeft + 1, listTop + 1, listRight - 1, listBottom - 1);
        drawRows(graphics, mouseX, mouseY);
        graphics.disableScissor();
        drawScrollbar(graphics);
    }

    private void drawHeader(GuiGraphicsExtractor graphics) {
        graphics.centeredText(font, title, width / 2, MARGIN + 2, NAME_COLOR);

        if (!session.complete() && session.total() > 0) {
            Component progress = Component.translatable("justtiers.scan.progress",
                    String.valueOf(session.answered()), String.valueOf(session.total()));
            graphics.text(font, progress, listRight - font.width(progress), MARGIN + 2,
                    Colors.SECONDARY);
        }

        int labelY = listTop - font.lineHeight - 2;
        int columnsLeft = columnsLeft();
        for (int i = 0; i < Source.ALL.size(); i++) {
            Source source = Source.ALL.get(i);
            int left = ScanLayout.columnLeft(columnsLeft, columnWidth, COLUMN_GAP, i);
            String name = source.displayName();
            graphics.text(font, name, left + (columnWidth - font.width(name)) / 2, labelY,
                    Colors.opaque(SiteColors.of(source)));
        }
    }

    private int columnsLeft() {
        return listLeft + ROW_PADDING + nameColumnWidth + COLUMN_GAP;
    }

    private void drawRows(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<ScanRow> rows = session.rows();
        OptionalInt hovered = rowAt(mouseX, mouseY);

        for (int i = layout.firstVisible(scroll); i < layout.lastVisible(scroll); i++) {
            ScanRow row = rows.get(i);
            int y = listTop + layout.yOf(i, scroll);

            if (hovered.isPresent() && hovered.getAsInt() == i) {
                graphics.fill(listLeft + 1, y, listRight - SCROLLBAR_WIDTH - 1,
                        y + rowHeight, ROW_HOVERED);
            }
            if (i > 0) {
                graphics.horizontalLine(listLeft + 1, listRight - 2, y, SEPARATOR);
            }

            drawIdentity(graphics, row, listLeft + ROW_PADDING, y + ROW_PADDING);
            drawSections(graphics, row, columnsLeft(), y + ROW_PADDING);
        }
    }

    /** The head, the name, and the number the whole scan is sorted by. */
    private void drawIdentity(GuiGraphicsExtractor graphics, ScanRow row, int x, int y) {
        drawHead(graphics, row, x, y);

        int textX = x + HEAD_PIXELS * HEAD_SCALE + NAME_GAP;
        graphics.text(font, row.player().name(), textX, y, NAME_COLOR, true);
        graphics.text(font, String.valueOf(row.points()), textX, y + font.lineHeight + 2,
                POINTS_COLOR, true);
    }

    /**
     * Only the head, drawn flat and face on: the two rectangles {@link SkinLayout}
     * already describes for it, face then hat. Every scanned player is on the server, so
     * their skin is in the player list and nothing is fetched over the network.
     */
    private void drawHead(GuiGraphicsExtractor graphics, ScanRow row, int x, int y) {
        PlayerSkin skin = skins.get(row.player().uuid());
        if (skin == null) {
            PlayerSkins.resolve(row.player()).thenAccept(loaded ->
                    minecraft.execute(() -> skins.put(row.player().uuid(), loaded)));
            return;
        }
        Identifier texture = skin.body().texturePath();
        boolean slim = skin.model() == PlayerModelType.SLIM;
        for (SkinLayout.Piece piece : SkinLayout.pieces(slim)) {
            if (piece.part() != SkinLayout.Part.HEAD) {
                continue;
            }
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                    x, y, piece.u(), piece.v(),
                    piece.width() * HEAD_SCALE, piece.height() * HEAD_SCALE,
                    piece.width(), piece.height(), SKIN_SIZE, SKIN_SIZE);
        }
    }

    private void drawSections(GuiGraphicsExtractor graphics, ScanRow row, int left, int y) {
        for (int i = 0; i < Source.ALL.size(); i++) {
            Source source = Source.ALL.get(i);
            int x = ScanLayout.columnLeft(left, columnWidth, COLUMN_GAP, i);
            Optional<LookupSection> section = row.section(source);

            if (section.isEmpty()) {
                graphics.centeredText(font, Component.translatable("justtiers.scan.waiting"),
                        x + columnWidth / 2, y, Colors.SECONDARY);
            } else if (section.get().status() == LookupSection.Status.UNAVAILABLE) {
                graphics.centeredText(font,
                        Component.translatable("justtiers.scan.unavailable"),
                        x + columnWidth / 2, y, UNAVAILABLE_COLOR);
            } else {
                drawCells(graphics, section.get(), grids.get(source), x, y);
            }
        }
    }

    private void drawCells(GuiGraphicsExtractor graphics, LookupSection section,
                           GridLayout grid, int left, int top) {
        List<LookupCell> cells = section.cells();
        int inset = (columnWidth - grid.contentWidth()) / 2;
        for (int i = 0; i < cells.size() && i < grid.itemCount(); i++) {
            drawCell(graphics, cells.get(i), section.source(),
                    left + inset + grid.xOf(i), top + grid.yOf(i));
        }
    }

    private void drawCell(GuiGraphicsExtractor graphics, LookupCell cell, Source source,
                          int x, int y) {
        graphics.fill(x, y, x + cellWidth, y + cellHeight, CELL_BACKGROUND);

        Optional<Tier> tier = cell.tier();
        String label = tier.map(Tier::label).orElse(NOT_TESTED);
        Component icon = Icons.of(cell.gamemode().icon());
        int contentWidth = iconWidth + CELL_TEXT_GAP + font.width(label);
        int textX = x + (cellWidth - contentWidth) / 2;
        int textY = y + (cellHeight - font.lineHeight) / 2 + 1;

        // Bitmap glyphs are multiplied by the text colour, so the icon has to stay white
        // even in a column that is otherwise entirely its site's colour.
        graphics.text(font, icon, textX + (iconWidth - font.width(icon)) / 2, textY,
                0xFFFFFFFF, false);
        graphics.text(font, label, textX + iconWidth + CELL_TEXT_GAP, textY,
                tier.isPresent() ? Colors.opaque(SiteColors.of(source)) : Colors.DISABLED, false);
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics) {
        if (layout.maxScroll() == 0) {
            return;
        }
        int track = listBottom - listTop;
        int thumb = Math.max(MIN_THUMB, track * track / Math.max(1, layout.contentHeight()));
        int travel = track - thumb;
        int y = listTop + (travel * scroll) / layout.maxScroll();
        graphics.fill(listRight - SCROLLBAR_WIDTH - 1, y, listRight - 1, y + thumb, SCROLLBAR);
    }

    // ---------------------------------------------------------------- input

    private OptionalInt rowAt(double mouseX, double mouseY) {
        if (mouseX < listLeft || mouseX >= listRight - SCROLLBAR_WIDTH) {
            return OptionalInt.empty();
        }
        return layout.indexAt((int) mouseY - listTop, scroll);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= listLeft && mouseX < listRight
                && mouseY >= listTop && mouseY < listBottom) {
            scroll = layout.clampScroll(scroll - (int) (scrollY * rowHeight / 2));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            OptionalInt index = rowAt(event.x(), event.y());
            if (index.isPresent() && index.getAsInt() < session.rows().size()) {
                ScanRow row = session.rows().get(index.getAsInt());
                minecraft.setScreenAndShow(new PlayerLookupScreen(row.player().name(), this));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
