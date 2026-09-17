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
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.input.KeyEvent;
import com.mojang.blaze3d.platform.InputConstants;
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
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The screen {@code /justtiers lookup} opens: a player's name, their skin, and one row
 * per site showing every gamemode that site runs — the tier they hold in it, or dashes
 * where they have no listed placement.
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

    private LookupSession session;
    private EditBox nameField;
    private Button lookupButton;
    private int scroll;
    private final List<ContentControl> contentControls = new ArrayList<>();
    private final Map<Source, List<ContentControl>> cellControls = new EnumMap<>(Source.class);
    private final List<ContentControl> links = new ArrayList<>();

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

        contentControls.clear();
        cellControls.clear();
        links.clear();
        scroll = Math.clamp(scroll, 0, layout.maxScroll());
        String typedName = nameField == null ? session.name() : nameField.getValue();
        int searchWidth = Math.min(width - 16, 320);
        int left = (width - searchWidth) / 2;
        nameField = addRenderableWidget(new EditBox(font, left, 8, searchWidth - 86, 20,
                Component.translatable("justtiers.lookup.name")));
        nameField.setMaxLength(64);
        nameField.setValue(typedName);
        nameField.setHint(Component.translatable("justtiers.lookup.name"));
        lookupButton = addRenderableWidget(Button.builder(Component.translatable("justtiers.lookup.search"),
                button -> startLookup()).pos(left + searchWidth - 80, 8).size(80, 20).build());
        nameField.setResponder(value -> lookupButton.active = !value.isBlank());
        lookupButton.active = !typedName.isBlank();
        addContentControls();
        updateContentControls();
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

        updateContentControls();
        graphics.enableScissor(layout.panelX(), layout.panelY(), layout.panelRight(), layout.viewportBottom());
        graphics.pose().pushMatrix();
        graphics.pose().translate(0, -scroll);
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
            int errorY = layout.skinY();
            for (var line : font.split(error.get(), layout.panelWidth() - 20)) {
                graphics.text(font, line, (width - font.width(line)) / 2, errorY, UNAVAILABLE_COLOR);
                errorY += font.lineHeight + 2;
            }
        } else {
            drawSkin(graphics);
            graphics.centeredText(font, Component.translatable("justtiers.lookup.tiers"),
                    width / 2, layout.tiersY(), Colors.SECONDARY);
            drawRows(graphics, mouseX, mouseY);
            if (session.rankedNowhere()) {
                graphics.centeredText(font,
                        Component.translatable("justtiers.lookup.none"),
                        width / 2, layout.noteY(), Colors.SECONDARY);
            }
        }
        drawFooter(graphics, mouseX, mouseY);
        graphics.pose().popMatrix();
        graphics.disableScissor();
        if (layout.maxScroll() > 0) {
            int thumbHeight = Math.max(8, layout.viewportHeight() * layout.viewportHeight() / layout.panelHeight());
            int thumbY = layout.panelY() + scroll * (layout.viewportHeight() - thumbHeight) / layout.maxScroll();
            graphics.fill(layout.panelRight() - 3, thumbY, layout.panelRight() - 1,
                    thumbY + thumbHeight, Colors.SECONDARY);
        }
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
        OptionalInt hovered = inViewport(mouseY) ? row.cellAt(mouseX, mouseY + scroll) : OptionalInt.empty();
        List<LookupCell> cells = section.cells();
        for (int i = 0; i < cells.size() && i < row.grid().itemCount(); i++) {
            boolean isHovered = (hovered.isPresent() && hovered.getAsInt() == i)
                    || cellControls.get(source).get(i).isFocused();
            drawCell(graphics, cells.get(i), source, row.cellX(i), row.cellY(i), isHovered);
            if (isHovered) {
                graphics.setTooltipForNextFrame(font, tooltip(cells.get(i)),
                        cellControls.get(source).get(i).isFocused() ? row.cellX(i) : mouseX,
                        cellControls.get(source).get(i).isFocused() ? row.cellY(i) - scroll + cellHeight : mouseY);
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
            if ((hovered.isPresent() && hovered.getAsInt() == i) || links.get(i).isFocused()) {
                graphics.horizontalLine(span.x(), span.x() + span.width() - 1,
                        footerY + font.lineHeight - 1, color);
            }
        }
    }

    // ---------------------------------------------------------------- input

    /** The site name under the cursor, if the cursor is on the footer line at all. */
    private OptionalInt linkAt(double mouseX, double mouseY) {
        if (!inViewport(mouseY) || mouseY + scroll < layout.footerY()
                || mouseY + scroll >= layout.footerY() + font.lineHeight) {
            return OptionalInt.empty();
        }
        return credit.spanAt(mouseX);
    }

    private boolean inViewport(double mouseY) {
        return mouseY >= layout.panelY() && mouseY < layout.viewportBottom();
    }

    private void startLookup() {
        String name = nameField.getValue().trim();
        if (name.isEmpty()) return;
        session = LookupSession.start(name);
        scroll = 0;
        updateContentControls();
    }

    private void addContentControls() {
        for (int sourceIndex = 0; sourceIndex < Source.ALL.size(); sourceIndex++) {
            Source source = Source.ALL.get(sourceIndex);
            LookupLayout.Row row = layout.rows().get(sourceIndex);
            List<ContentControl> cells = new ArrayList<>();
            for (int i = 0; i < row.grid().itemCount(); i++) {
                ContentControl control = new ContentControl(row.cellX(i), row.cellY(i),
                        cellWidth, cellHeight, Component.empty(), null);
                cells.add(control);
                contentControls.add(addWidget(control));
            }
            cellControls.put(source, cells);
        }
        for (int i = 0; i < Source.ALL.size(); i++) {
            Source source = Source.ALL.get(i);
            CreditLine.Span span = credit.spans().get(i);
            ContentControl control = new ContentControl(span.x(), layout.footerY(),
                    span.width(), font.lineHeight, Component.translatable("justtiers.lookup.visitSite",
                    source.displayName()), () -> ConfirmLinkScreen.confirmLinkNow(this, source.homeUrl()));
            links.add(control);
            contentControls.add(addWidget(control));
        }
    }

    private void updateContentControls() {
        for (Source source : Source.ALL) {
            Optional<LookupSection> section = session.section(source);
            List<ContentControl> controls = cellControls.get(source);
            for (int i = 0; i < controls.size(); i++) {
                ContentControl control = controls.get(i);
                control.active = section.isPresent() && i < section.get().cells().size();
                if (control.active) {
                    control.setMessage(Component.literal(source.displayName() + ": ")
                            .append(tooltip(section.get().cells().get(i))));
                }
            }
        }
        contentControls.forEach(control -> control.setY(control.contentY - scroll));
    }

    @Override
    public void tick() {
        updateContentControls();
    }

    @Override
    public void setFocused(GuiEventListener listener) {
        super.setFocused(listener);
        if (listener instanceof ContentControl control) {
            scroll = layout.scrollTo(control.contentY, control.getHeight(), scroll);
            updateContentControls();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (inViewport(mouseY) && layout.maxScroll() > 0) {
            scroll = Math.clamp(scroll - (int) (vertical * 16), 0, layout.maxScroll());
            updateContentControls();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (nameField.isFocused() && (event.key() == InputConstants.KEY_RETURN
                || event.key() == InputConstants.KEY_NUMPADENTER)) {
            startLookup();
            return true;
        }
        if (event.key() == InputConstants.KEY_PAGEDOWN || event.key() == InputConstants.KEY_PAGEUP) {
            int step = event.key() == InputConstants.KEY_PAGEDOWN ? 1 : -1;
            scroll = Math.clamp(scroll + step * layout.viewportHeight(), 0, layout.maxScroll());
            updateContentControls();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public Component getNarrationMessage() {
        var message = Component.translatable("justtiers.lookup.header", session.name());
        Optional<Component> error = session.error();
        if (error.isPresent()) {
            return message.append(". ").append(error.get());
        }
        for (Source source : Source.ALL) {
            var section = session.section(source);
            if (section.isEmpty() || section.get().status() == LookupSection.Status.UNAVAILABLE) {
                message.append(". " + source.displayName() + ": ")
                        .append(Component.translatable(section.isEmpty() ? "justtiers.lookup.pending"
                                : "justtiers.lookup.unavailable"));
            }
        }
        return message;
    }

    /** Focusable text content. Links activate; cells are read-only and narrated in full. */
    private final class ContentControl extends AbstractWidget {
        private final int contentY;
        private final Runnable activate;

        private ContentControl(int x, int y, int width, int height, Component message, Runnable activate) {
            super(x, y, width, height, message);
            this.contentY = y;
            this.activate = activate;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            // The content is drawn with the panel's common scroll transform.
        }

        @Override
        public boolean isMouseOver(double x, double y) {
            return inViewport(y) && super.isMouseOver(x, y);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            return isMouseOver(event.x(), event.y()) && super.mouseClicked(event, doubleClick);
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            if (activate != null) activate.run();
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (isFocused() && active && activate != null && (event.key() == InputConstants.KEY_RETURN
                    || event.key() == InputConstants.KEY_NUMPADENTER || event.key() == InputConstants.KEY_SPACE)) {
                activate.run();
                return true;
            }
            return false;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
        }
    }
}
