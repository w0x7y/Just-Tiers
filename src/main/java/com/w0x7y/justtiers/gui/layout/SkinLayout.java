package com.w0x7y.justtiers.gui.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * Where each piece of a player's skin goes when the skin is drawn flat, face on — the
 * front faces of the head, body, arms and legs, laid out into a figure sixteen skin
 * pixels wide and thirty-two tall.
 *
 * <p>Drawing the texture directly rather than posing a model is what lets the lookup
 * screen show a skin with no world loaded and no entity to borrow: there is nothing here
 * but rectangles. Coordinates are in skin pixels, relative to the figure's own top-left,
 * and the caller multiplies by whatever scale it wants.
 *
 * <p>A slim figure loses one pixel from each arm, so it is fourteen wide while its body,
 * head and legs are unchanged.
 */
public final class SkinLayout {

    /** Head to heel, in skin pixels: 8 of head, 12 of body, 12 of leg. */
    public static final int HEIGHT = 32;

    private static final int BODY_WIDTH = 8;
    private static final int WIDE_ARM = 4;
    private static final int SLIM_ARM = 3;

    public enum Part { HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG }

    /**
     * One rectangle to blit: {@code u}/{@code v} locate it on the 64x64 skin, {@code x}/
     * {@code y} place it in the figure, and the size applies to both — a front face is
     * drawn at its natural proportions or not at all.
     */
    public record Piece(Part part, boolean overlay,
                        int u, int v, int x, int y, int width, int height) {
    }

    public static int width(boolean slim) {
        return BODY_WIDTH + 2 * armWidth(slim);
    }

    private static int armWidth(boolean slim) {
        return slim ? SLIM_ARM : WIDE_ARM;
    }

    /**
     * Every rectangle of the figure, in paint order: all six base parts first, then the
     * six overlay parts on top of them. A jacket drawn before its arm would be painted
     * over by the arm, so the split is not cosmetic.
     */
    public static List<Piece> pieces(boolean slim) {
        return slim ? SLIM : WIDE;
    }

    private static final List<Piece> WIDE = build(false);
    private static final List<Piece> SLIM = build(true);

    private static List<Piece> build(boolean slim) {
        int arm = armWidth(slim);
        int bodyX = arm;
        int armY = 8;
        int legY = 20;

        List<Piece> base = new ArrayList<>(Part.values().length);
        List<Piece> overlay = new ArrayList<>(Part.values().length);

        add(base, overlay, Part.HEAD, 8, 8, 40, 8, bodyX, 0, BODY_WIDTH, 8);
        add(base, overlay, Part.BODY, 20, 20, 20, 36, bodyX, armY, BODY_WIDTH, 12);
        // The slim skin's arm faces are three wide starting at the same u, which is why
        // the arm width is passed through to the texture rectangle as well.
        add(base, overlay, Part.RIGHT_ARM, 44, 20, 44, 36, 0, armY, arm, 12);
        add(base, overlay, Part.LEFT_ARM, 36, 52, 52, 52, bodyX + BODY_WIDTH, armY, arm, 12);
        add(base, overlay, Part.RIGHT_LEG, 4, 20, 4, 36, bodyX, legY, 4, 12);
        add(base, overlay, Part.LEFT_LEG, 20, 52, 4, 52, bodyX + 4, legY, 4, 12);

        List<Piece> pieces = new ArrayList<>(base.size() + overlay.size());
        pieces.addAll(base);
        pieces.addAll(overlay);
        return List.copyOf(pieces);
    }

    private static void add(List<Piece> base, List<Piece> overlay, Part part,
                            int u, int v, int overlayU, int overlayV,
                            int x, int y, int width, int height) {
        base.add(new Piece(part, false, u, v, x, y, width, height));
        overlay.add(new Piece(part, true, overlayU, overlayV, x, y, width, height));
    }

    private SkinLayout() {
    }
}
