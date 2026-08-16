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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Draws a small progress indicator in the bottom-right corner while the NovaTiers list is
 * downloading. It is deliberately transient: it exists only so a user whose badges have not
 * appeared can tell "still downloading" from "the site is down".
 */
public final class DownloadHud implements HudElement {

    private static final int RIGHT_MARGIN = 4;
    private static final int BOTTOM_GAP = 4;

    private static final int TRACK_WIDTH = 180;
    private static final int TRACK_HEIGHT = 4;
    private static final int PADDING = 3;
    private static final int LINE_GAP = 2;

    private static final int BACKDROP = 0x90000000;
    private static final int TRACK_COLOR = 0xFF3F3F3F;
    /** NovaTiers purple: this indicator is about NovaTiers, and color here means the site. */
    private static final int FILL_COLOR = 0xFFAA55FF;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int FAILURE_COLOR = 0xFFFF5555;

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

        // Bottom-right: vanilla chat grows upward from the opposite corner, so nothing
        // here has to be dodged.
        int left = graphics.guiWidth() - RIGHT_MARGIN - boxWidth;
        int bottom = graphics.guiHeight() - BOTTOM_GAP;
        int top = bottom - boxHeight;

        graphics.fill(left, top, left + boxWidth, bottom, BACKDROP);
        graphics.text(font,
                Component.translatable(failed ? "justtiers.download.failed" : "justtiers.download.title"),
                left + PADDING, top + PADDING, failed ? FAILURE_COLOR : TEXT_COLOR);

        if (failed) {
            return;
        }

        int trackLeft = left + PADDING;
        int trackTop = top + PADDING + font.lineHeight + LINE_GAP;
        graphics.fill(trackLeft, trackTop, trackLeft + TRACK_WIDTH, trackTop + TRACK_HEIGHT,
                TRACK_COLOR);

        String readout;
        if (snapshot.determinate()) {
            double fraction = ProgressBarLayout.fraction(snapshot.bytesRead(), snapshot.total());
            int filled = (int) Math.round(TRACK_WIDTH * fraction);
            graphics.fill(trackLeft, trackTop, trackLeft + filled, trackTop + TRACK_HEIGHT,
                    FILL_COLOR);
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
                        FILL_COLOR);
            }
            readout = ProgressBarLayout.formatBytes(snapshot.bytesRead());
        }

        // Right-aligned on the label's line, inside the backdrop.
        graphics.text(font, readout,
                left + boxWidth - PADDING - font.width(readout), top + PADDING, TEXT_COLOR);
    }

}
