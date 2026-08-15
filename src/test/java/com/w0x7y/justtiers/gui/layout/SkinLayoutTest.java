package com.w0x7y.justtiers.gui.layout;

import com.w0x7y.justtiers.gui.layout.SkinLayout.Part;
import com.w0x7y.justtiers.gui.layout.SkinLayout.Piece;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkinLayoutTest {

    private static Piece piece(boolean slim, Part part, boolean overlay) {
        return SkinLayout.pieces(slim).stream()
                .filter(p -> p.part() == part && p.overlay() == overlay)
                .findFirst().orElseThrow(() ->
                        new AssertionError("no " + (overlay ? "overlay" : "base") + " " + part));
    }

    @Test
    void aWideFigureIsSixteenBySkinPixelsAcrossAndThirtyTwoTall() {
        assertEquals(16, SkinLayout.width(false));
        assertEquals(32, SkinLayout.HEIGHT);
    }

    @Test
    void aSlimFigureIsTwoPixelsNarrowerBecauseBothArmsLoseOne() {
        assertEquals(14, SkinLayout.width(true));
    }

    @Test
    void theHeadIsTheFrontFaceOfTheSkinAndSitsAtTheTop() {
        Piece head = piece(false, Part.HEAD, false);

        assertEquals(8, head.u());
        assertEquals(8, head.v());
        assertEquals(8, head.width());
        assertEquals(8, head.height());
        assertEquals(0, head.y());
    }

    @Test
    void theHatIsTheSameSquareShiftedIntoTheOverlayColumn() {
        Piece head = piece(false, Part.HEAD, false);
        Piece hat = piece(false, Part.HEAD, true);

        assertEquals(40, hat.u());
        assertEquals(8, hat.v());
        assertEquals(head.x(), hat.x());
        assertEquals(head.y(), hat.y());
        assertEquals(head.width(), hat.width());
        assertEquals(head.height(), hat.height());
    }

    @Test
    void theHeadIsCentredOverTheBody() {
        // Both are eight wide, so "centred" is the same left edge - which is what keeps a
        // slim figure's head from hanging off the side.
        Piece head = piece(true, Part.HEAD, false);
        Piece body = piece(true, Part.BODY, false);

        assertEquals(body.x(), head.x());
        assertEquals(8, head.width());
        assertEquals(8, body.width());
    }

    @Test
    void theBodyHangsDirectlyBelowTheHead() {
        Piece body = piece(false, Part.BODY, false);

        assertEquals(20, body.u());
        assertEquals(20, body.v());
        assertEquals(8, body.y());
        assertEquals(12, body.height());
    }

    @Test
    void theRightArmIsOnTheViewersLeftAndTheLeftArmOnTheRight() {
        // A front view shows the player facing us, so their right arm is our left.
        Piece rightArm = piece(false, Part.RIGHT_ARM, false);
        Piece body = piece(false, Part.BODY, false);
        Piece leftArm = piece(false, Part.LEFT_ARM, false);

        assertEquals(0, rightArm.x());
        assertEquals(rightArm.x() + rightArm.width(), body.x());
        assertEquals(body.x() + body.width(), leftArm.x());
        assertEquals(SkinLayout.width(false), leftArm.x() + leftArm.width());
    }

    @Test
    void armsHangFromTheShouldersNotTheChin() {
        Piece arm = piece(false, Part.RIGHT_ARM, false);
        Piece body = piece(false, Part.BODY, false);

        assertEquals(body.y(), arm.y());
        assertEquals(12, arm.height());
    }

    @Test
    void slimArmsAreThreePixelsWideOnBothTheSkinAndTheScreen() {
        Piece rightArm = piece(true, Part.RIGHT_ARM, false);
        Piece leftArm = piece(true, Part.LEFT_ARM, false);

        assertEquals(3, rightArm.width());
        assertEquals(3, leftArm.width());
    }

    @Test
    void theTwoLegsTogetherAreExactlyAsWideAsTheBody() {
        Piece rightLeg = piece(false, Part.RIGHT_LEG, false);
        Piece leftLeg = piece(false, Part.LEFT_LEG, false);
        Piece body = piece(false, Part.BODY, false);

        assertEquals(body.x(), rightLeg.x());
        assertEquals(rightLeg.x() + rightLeg.width(), leftLeg.x());
        assertEquals(body.x() + body.width(), leftLeg.x() + leftLeg.width());
    }

    @Test
    void legsStartWhereTheBodyEndsAndReachTheBottom() {
        Piece body = piece(false, Part.BODY, false);
        Piece leg = piece(false, Part.RIGHT_LEG, false);

        assertEquals(body.y() + body.height(), leg.y());
        assertEquals(SkinLayout.HEIGHT, leg.y() + leg.height());
    }

    @Test
    void everyPartHasAnOverlayAndOverlaysAreListedAfterEveryBasePart() {
        // The whole list is drawn in order, so a jacket sleeve must never be painted
        // before the arm it belongs on.
        List<Piece> pieces = SkinLayout.pieces(false);

        for (Part part : Part.values()) {
            assertNotNull(piece(false, part, false));
            assertNotNull(piece(false, part, true));
        }
        int firstOverlay = pieces.indexOf(pieces.stream().filter(Piece::overlay)
                .findFirst().orElseThrow());
        assertTrue(pieces.subList(firstOverlay, pieces.size()).stream().allMatch(Piece::overlay));
        assertEquals(Part.values().length * 2, pieces.size());
    }

    @Test
    void everyPieceStaysInsideTheSixtyFourBySixtyFourSkin() {
        for (boolean slim : List.of(false, true)) {
            for (Piece p : SkinLayout.pieces(slim)) {
                assertTrue(p.u() >= 0 && p.u() + p.width() <= 64, p + " runs off the skin");
                assertTrue(p.v() >= 0 && p.v() + p.height() <= 64, p + " runs off the skin");
            }
        }
    }
}
