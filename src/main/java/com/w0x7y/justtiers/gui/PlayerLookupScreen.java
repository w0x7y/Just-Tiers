package com.w0x7y.justtiers.gui;

import com.w0x7y.justtiers.JustTiersClient;
import com.w0x7y.justtiers.render.Icons;
import com.w0x7y.justtiers.render.SiteColors;
import com.w0x7y.justtiers.gui.layout.CreditLine;
import com.w0x7y.justtiers.gui.layout.HistoryStrip;
import com.w0x7y.justtiers.gui.layout.LookupLayout;
import com.w0x7y.justtiers.gui.layout.LookupMetrics;
import com.w0x7y.justtiers.gui.layout.SkinLayout;
import com.w0x7y.justtiers.gui.state.LookupFocus;
import com.w0x7y.justtiers.gui.state.LookupText;
import com.w0x7y.justtiers.lookup.LookupCell;
import com.w0x7y.justtiers.lookup.LookupRow;
import com.w0x7y.justtiers.lookup.LookupSection;
import com.w0x7y.justtiers.lookup.LookupResult;
import com.w0x7y.justtiers.tier.DiscoveredGamemodes;
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
import net.minecraft.network.chat.MutableComponent;
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
 * in a {@link MinecraftLookupSession}, so this class only ever reads and never waits.
 *
 * <p>Unlike the nametag, this screen always draws the gamemode icons: on a nametag an
 * icon says which gamemode earned a tier, but here it is the only thing naming the
 * column, and a row of bare tiers would say nothing about what they were earned in.
 * A discovered gamemode has no icon, so its cell shows the first letter of its derived
 * name in ordinary text, and its tooltip names it and says this build does not
 * recognize it yet.
 *
 * <p>Registered gamemodes have fixed cells, so their rows never move as answers arrive.
 * Discovered cells follow them and are known only once a site answers, so a row that
 * gains one is laid out again on the next tick, keeping the focused control.
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

    private MinecraftLookupSession session;
    private EditBox nameField;
    private Button lookupButton;
    private Button doneButton;
    private int scroll;
    private final List<ContentControl> contentControls = new ArrayList<>();
    private final Map<Source, List<ContentControl>> cellControls = new EnumMap<>(Source.class);
    private final List<ContentControl> links = new ArrayList<>();
    private HistoryButtons history;

    private LookupLayout layout;
    private List<Integer> layoutCounts = List.of();
    private CreditLine credit;
    private int cellWidth;
    private int cellHeight;
    private int iconWidth;

    public PlayerLookupScreen(String name) {
        super(Component.translatable("justtiers.lookup.header", name));
        this.session = MinecraftLookupSession.start(name);
    }

    @Override
    protected void init() {
        measureCells();
        layoutCounts = rowCounts();
        layout = LookupLayout.of(metrics(layoutCounts));
        credit = CreditLine.centeredIn(layout.panelX(), layout.panelWidth(),
                font.width(Component.translatable("justtiers.lookup.credit")),
                font.width(" "), font.width(SITE_SEPARATOR), siteNameWidths());

        contentControls.clear();
        cellControls.clear();
        links.clear();
        scroll = layout.scrolling().clamp(scroll);
        String typedName = nameField == null ? session.name() : nameField.getValue();
        int searchWidth = layout.searchWidth();
        int left = layout.searchX();
        nameField = addRenderableWidget(new EditBox(font, left, layout.searchY(), searchWidth - 86, 20,
                Component.translatable("justtiers.lookup.name")));
        nameField.setMaxLength(64);
        nameField.setValue(typedName);
        nameField.setHint(Component.translatable("justtiers.lookup.name"));
        lookupButton = addRenderableWidget(Button.builder(Component.translatable("justtiers.lookup.search"),
                button -> startLookup()).pos(left + searchWidth - 80, layout.searchY()).size(80, 20).build());
        nameField.setResponder(value -> lookupButton.active = !value.isBlank());
        lookupButton.active = !typedName.isBlank();
        // Added straight after the search controls, so Tab reaches the recent names before
        // the results.
        history = new HistoryButtons(layout, font, BUTTON_WIDTH, BUTTON_HEIGHT,
                this::addRenderableWidget, name -> {
                    nameField.setValue(name);
                    startLookup();
                });
        updateHistory();
        addContentControls();
        updateContentControls();
        doneButton = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .pos(width / 2 - BUTTON_WIDTH / 2, layout.doneButtonY())
                .size(BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    // ---------------------------------------------------------------- measuring

    /** What only the running game can measure. Every decision from here is arithmetic. */
    private LookupMetrics metrics(List<Integer> counts) {
        return new LookupMetrics(width, height, font.lineHeight,
                Math.round(font.lineHeight * NAME_SCALE), widestSiteName(),
                cellWidth, cellHeight, counts);
    }

    /** Cells per row, as {@link LookupRow#cellCount} reserves them for the current answers. */
    private List<Integer> rowCounts() {
        List<Integer> counts = new ArrayList<>(Source.ALL.size());
        for (Source source : Source.ALL) {
            counts.add(LookupRow.cellCount(source, session.result(source).section()));
        }
        return counts;
    }

    private void measureCells() {
        iconWidth = 0;
        for (Gamemode gamemode : Gamemodes.ALL) {
            Optional<MutableComponent> icon = Icons.of(gamemode);
            if (icon.isPresent()) {
                iconWidth = Math.max(iconWidth, font.width(icon.get()));
            }
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

        HistoryStrip strip = history.strip();
        if (!strip.entries().isEmpty()) {
            graphics.text(font, Component.translatable("justtiers.lookup.recent"),
                    strip.labelX(), strip.textTop(font.lineHeight), Colors.SECONDARY);
        }
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
        layout.scrolling().thumb(scroll).ifPresent(thumb -> graphics.fill(
                layout.panelRight() - 3, layout.panelY() + thumb.y(), layout.panelRight() - 1,
                layout.panelY() + thumb.y() + thumb.height(), Colors.SECONDARY));
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

            LookupResult result = session.result(source);
            switch (LookupRow.state(result.section())) {
                case PENDING -> graphics.centeredText(font,
                        Component.translatable("justtiers.lookup.pending"),
                        row.centerX(), textY, Colors.SECONDARY);
                case UNAVAILABLE -> graphics.centeredText(font,
                        Component.translatable("justtiers.lookup.unavailable"),
                        row.centerX(), textY, UNAVAILABLE_COLOR);
                case ANSWERED -> drawCells(graphics, row, source, result, mouseX, mouseY);
            }
        }
    }

    private void drawCells(GuiGraphicsExtractor graphics, LookupLayout.Row row, Source source,
                           LookupResult result, int mouseX, int mouseY) {
        OptionalInt hovered = inViewport(mouseY) ? row.cellAt(mouseX, mouseY + scroll) : OptionalInt.empty();
        List<LookupCell> cells = result.section().orElseThrow().cells();
        for (int i = 0; i < cells.size() && i < row.grid().itemCount(); i++) {
            boolean isHovered = (hovered.isPresent() && hovered.getAsInt() == i)
                    || cellControls.get(source).get(i).isFocused();
            drawCell(graphics, cells.get(i), source, row.cellX(i), row.cellY(i), isHovered);
            if (isHovered) {
                graphics.setTooltipForNextFrame(font, lines(LookupText.tooltip(cells.get(i), result)),
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
        int contentWidth = iconWidth + CELL_TEXT_GAP + font.width(label);
        int textX = x + (cellWidth - contentWidth) / 2;
        int textY = y + (cellHeight - font.lineHeight) / 2 + 1;

        Optional<MutableComponent> glyph = Icons.of(cell.gamemode());
        if (glyph.isPresent()) {
            // Bitmap glyphs are multiplied by the text color, so the icon has to stay white
            // even in a row that is otherwise entirely its site's color.
            graphics.text(font, glyph.get(), textX + (iconWidth - font.width(glyph.get())) / 2, textY,
                    0xFFFFFFFF, false);
        } else {
            // No glyph exists, so the slot holds plain text in the default font.
            String initial = DiscoveredGamemodes.abbreviation(cell.gamemode().displayName());
            graphics.text(font, initial, textX + (iconWidth - font.width(initial)) / 2, textY,
                    Colors.SECONDARY, false);
        }
        graphics.text(font, label, textX + iconWidth + CELL_TEXT_GAP, textY,
                tier.isPresent() ? Colors.opaque(SiteColors.of(source)) : Colors.DISABLED, false);
    }

    /** A message translated, with any message among its arguments translated first. */
    private static MutableComponent component(LookupText.Message message) {
        Object[] args = message.args().stream()
                .map(arg -> arg instanceof LookupText.Message nested ? component(nested) : arg)
                .toArray();
        return Component.translatable(message.key(), args);
    }

    /** Messages as one component, a line each. */
    private static Component lines(List<LookupText.Message> messages) {
        MutableComponent text = component(messages.getFirst());
        for (LookupText.Message message : messages.subList(1, messages.size())) {
            text.append("\n").append(component(message));
        }
        return text;
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
        session = MinecraftLookupSession.start(name);
        scroll = 0;
        updateContentControls();
    }

    /** Lays the recent names out again whenever the session's history has changed. */
    private void updateHistory() {
        String focusedName = history.nameOn(getFocused());
        // A lookup moves its name to the front: keep focus on that name, not on its old slot.
        if (history.show(JustTiersClient.lookupHistory().names()) && focusedName != null) {
            restoreFocus(new LookupFocus.History(focusedName));
        }
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
                    source.displayName()), () -> ConfirmLinkScreen.confirmLinkNow(
                            this, java.net.URI.create(source.homeUrl())));
            links.add(control);
            contentControls.add(addWidget(control));
        }
    }

    private void updateContentControls() {
        for (Source source : Source.ALL) {
            LookupResult result = session.result(source);
            Optional<LookupSection> section = result.section();
            int shown = LookupRow.shownCells(section);
            List<ContentControl> controls = cellControls.get(source);
            for (int i = 0; i < controls.size(); i++) {
                ContentControl control = controls.get(i);
                control.active = i < shown;
                if (control.active) {
                    control.setMessage(lines(LookupText.cellNarration(source,
                            section.get().cells().get(i), result)));
                }
            }
        }
        contentControls.forEach(control -> control.setY(control.contentY - scroll));
    }

    @Override
    public void tick() {
        updateHistory();
        if (!rowCounts().equals(layoutCounts)) {
            relayout();
        }
        updateContentControls();
    }

    /**
     * Rebuilds the widgets for a new number of cells. Rebuilding drops focus, so the
     * focused control is recorded by what it is first and found again afterwards, as
     * {@link LookupFocus#restore} decides.
     */
    private void relayout() {
        Optional<LookupFocus.Key> focused = focusKey();
        rebuildWidgets();
        focused.ifPresent(this::restoreFocus);
    }

    /** What the focused widget is, or empty when nothing on the screen has focus. */
    private Optional<LookupFocus.Key> focusKey() {
        GuiEventListener focused = getFocused();
        if (focused == null) return Optional.empty();
        if (focused == nameField) return Optional.of(new LookupFocus.Name());
        if (focused == lookupButton) return Optional.of(new LookupFocus.Search());
        if (focused == doneButton) return Optional.of(new LookupFocus.Done());
        String historyName = history.nameOn(focused);
        if (historyName != null) return Optional.of(new LookupFocus.History(historyName));
        for (Source source : Source.ALL) {
            int index = cellControls.getOrDefault(source, List.of()).indexOf(focused);
            if (index >= 0) return Optional.of(new LookupFocus.Cell(source, index));
        }
        int link = links.indexOf(focused);
        return link >= 0 ? Optional.of(new LookupFocus.Link(link)) : Optional.empty();
    }

    /** Focuses the widget that now stands for a recorded control. */
    private void restoreFocus(LookupFocus.Key recorded) {
        LookupFocus.Key key = LookupFocus.restore(recorded, history.visible(),
                source -> cellControls.get(source).size(), links.size());
        setFocused(switch (key) {
            case LookupFocus.Name ignored -> nameField;
            case LookupFocus.Search ignored -> lookupButton;
            case LookupFocus.Done ignored -> doneButton;
            case LookupFocus.History recent -> history.slot(
                    LookupFocus.historySlot(recent.name(), history.visible()).orElseThrow());
            case LookupFocus.Cell cell -> cellControls.get(cell.source()).get(cell.index());
            case LookupFocus.Link link -> links.get(link.index());
        });
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
        if (inViewport(mouseY) && layout.scrolling().scrolls()) {
            scroll = layout.scrolling().wheel(scroll, vertical);
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
            scroll = layout.scrolling().page(scroll, step);
            updateContentControls();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public Component getNarrationMessage() {
        return component(LookupText.narration(session.name(), session.error(), session::result));
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
