package com.w0x7y.justtiers.gui;

import com.w0x7y.justtiers.JustTiers;
import com.w0x7y.justtiers.JustTiersClient;
import com.w0x7y.justtiers.download.DownloadProgress;
import com.w0x7y.justtiers.gui.layout.ProgressBarLayout;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Draws a small progress indicator in the bottom-left corner while the NovaTiers list is
 * downloading. It is deliberately transient: it exists only so a user whose badges have not
 * appeared can tell "still downloading" from "the site is down".
 */
public final class DownloadHud implements HudElement {

    private static final int LEFT_MARGIN = 4;
    private static final int BOTTOM_GAP = 4;
    /** Rough height of the chat input box, which sits below the message area. Tune by eye. */
    private static final int CHAT_INPUT_ALLOWANCE = 14;

    private static final int TRACK_WIDTH = 120;
    private static final int TRACK_HEIGHT = 4;
    private static final int PADDING = 3;
    private static final int LINE_GAP = 2;

    private static final int BACKDROP = 0x90000000;
    private static final int TRACK_COLOUR = 0xFF3F3F3F;
    /** NovaTiers purple: this indicator is about NovaTiers, and colour here means the site. */
    private static final int FILL_COLOUR = 0xFFAA55FF;
    private static final int TEXT_COLOUR = 0xFFFFFFFF;
    private static final int FAILURE_COLOUR = 0xFFFF5555;

    public static void register() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(JustTiers.MOD_ID, "download_progress"),
                new DownloadHud());

        ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) ->
                ScreenEvents.afterForeground(screen).register(
                        (openScreen, graphics, mouseX, mouseY, delta) -> {
                            // The in-game HUD keeps drawing behind an open screen, so drawing
                            // here as well would blend the backdrop twice. This path exists for
                            // the title screen and main menu, where there is no HUD at all -
                            // and where the launch download actually runs.
                            if (Minecraft.getInstance().level == null) {
                                draw(graphics);
                            }
                        }));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        draw(graphics);
    }

    private static void draw(GuiGraphicsExtractor graphics) {
        if (!JustTiersClient.config().isShowDownloadProgress()) {
            return;
        }
        DownloadProgress.Snapshot snapshot = JustTiersClient.downloadProgress().snapshot();
        if (snapshot.state() == DownloadProgress.State.IDLE) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        boolean failed = snapshot.state() == DownloadProgress.State.FAILED;

        int boxWidth = TRACK_WIDTH + PADDING * 2;
        int contentHeight = failed ? font.lineHeight : font.lineHeight + LINE_GAP + TRACK_HEIGHT;
        int boxHeight = contentHeight + PADDING * 2;

        int left = LEFT_MARGIN;
        int bottom = graphics.guiHeight() - chatReserve(minecraft) - BOTTOM_GAP;
        int top = bottom - boxHeight;

        graphics.fill(left, top, left + boxWidth, bottom, BACKDROP);
        graphics.text(font,
                Component.translatable(failed ? "justtiers.download.failed" : "justtiers.download.title"),
                left + PADDING, top + PADDING, failed ? FAILURE_COLOUR : TEXT_COLOUR);

        if (failed) {
            return;
        }

        int trackLeft = left + PADDING;
        int trackTop = top + PADDING + font.lineHeight + LINE_GAP;
        graphics.fill(trackLeft, trackTop, trackLeft + TRACK_WIDTH, trackTop + TRACK_HEIGHT,
                TRACK_COLOUR);

        String readout;
        if (snapshot.determinate()) {
            double fraction = ProgressBarLayout.fraction(snapshot.bytesRead(), snapshot.total());
            int filled = (int) Math.round(TRACK_WIDTH * fraction);
            graphics.fill(trackLeft, trackTop, trackLeft + filled, trackTop + TRACK_HEIGHT,
                    FILL_COLOUR);
            readout = ProgressBarLayout.formatPercent(fraction);
        } else {
            // No content-length from novatiers.com, so the first download of a session can
            // only show movement and a byte count.
            int segmentWidth =
                    (int) Math.round(TRACK_WIDTH * ProgressBarLayout.MARQUEE_WIDTH_FRACTION);
            int segmentLeft = trackLeft
                    + (int) Math.round(TRACK_WIDTH * ProgressBarLayout.marqueeStart(System.nanoTime()));
            int clampedLeft = Math.max(trackLeft, segmentLeft);
            int clampedRight = Math.min(trackLeft + TRACK_WIDTH, segmentLeft + segmentWidth);
            if (clampedRight > clampedLeft) {
                graphics.fill(clampedLeft, trackTop, clampedRight, trackTop + TRACK_HEIGHT,
                        FILL_COLOUR);
            }
            readout = ProgressBarLayout.formatBytes(snapshot.bytesRead());
        }

        // Right-aligned on the label's line, inside the backdrop.
        graphics.text(font, readout,
                left + boxWidth - PADDING - font.width(readout), top + PADDING, TEXT_COLOUR);
    }

    /**
     * How much room to leave at the bottom for chat. Vanilla chat renders upward from this
     * exact corner, so a bar flush to the bottom would sit on the newest message.
     *
     * <p>26.2 exposes no way to ask whether chat is focused - {@code Gui} has no
     * {@code ChatComponent} accessor and {@code Minecraft} no current-screen accessor - so
     * the focused height is reserved unconditionally. That costs a few pixels of clearance
     * when chat is closed and buys a bar that does not jump when chat opens.
     */
    private static int chatReserve(Minecraft minecraft) {
        if (minecraft.level == null) {
            return 0;
        }
        double heightPct = minecraft.options.chatHeightFocused().get();
        double scale = minecraft.options.chatScale().get();
        return (int) Math.ceil(ChatComponent.getHeight(heightPct) * scale) + CHAT_INPUT_ALLOWANCE;
    }
}
