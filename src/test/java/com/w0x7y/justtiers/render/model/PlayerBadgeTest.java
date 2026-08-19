package com.w0x7y.justtiers.render.model;

import com.w0x7y.justtiers.resolve.DisplayMode;
import com.w0x7y.justtiers.tier.Gamemode;
import com.w0x7y.justtiers.tier.Gamemodes;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code Badge.forPlayer(TierView, UUID)} — the decision the world nametag makes, every
 * player, every frame. It used to live inside {@code NametagRenderer.decorate} reaching
 * straight into the static hub, so none of it could be asserted without launching the
 * game; behind {@link TierView} it is ordinary code with a fake in front of it.
 */
class PlayerBadgeTest {

    /** Real accounts have version-4 UUIDs. */
    private static final UUID ACCOUNT = UUID.fromString("11111111-1111-4111-8111-111111111111");
    /** Offline-mode and NPC entities get version-3 ones, and are on no leaderboard. */
    private static final UUID OFFLINE =
            UUID.nameUUIDFromBytes("Player".getBytes(StandardCharsets.UTF_8));

    private static final Map<Source, String> SELECTED = Map.of(
            Source.MCTIERS, "vanilla",
            Source.SUBTIERS, "bow",
            Source.NOVATIERS, "spleef");

    private static NametagSettings settings(boolean enabled, DisplayMode mode) {
        return new NametagSettings(enabled, mode, SELECTED, true, NametagStyle.DEFAULT);
    }

    private static FakeTierView view(boolean enabled, DisplayMode mode) {
        return new FakeTierView(settings(enabled, mode))
                .answering(Source.MCTIERS, Map.of("vanilla", new Tier(2, true, false)))
                .answering(Source.SUBTIERS, Map.of("bow", new Tier(1, true, false)))
                .answering(Source.NOVATIERS, Map.of("spleef", new Tier(3, false, false)));
    }

    private static Gamemode gamemode(Source source, String slug) {
        return Gamemodes.find(source, slug).orElseThrow();
    }

    // --- who gets a badge at all ---

    @Test
    void aRankedAccountGetsItsBadge() {
        assertEquals("[" + gamemode(Source.MCTIERS, "vanilla").icon() + "HT2] ",
                Badge.forPlayer(view(true, DisplayMode.MCTIERS_ONLY), ACCOUNT).plainText());
    }

    @Test
    void theModBeingOffCostsNothing() {
        FakeTierView view = view(false, DisplayMode.ALL);

        assertTrue(Badge.forPlayer(view, ACCOUNT).isEmpty());
        // Not merely blank: switched off, nothing should be asking the cache at all.
        assertEquals(List.of(), view.asked());
    }

    @Test
    void anOfflineOrNpcUuidIsNeverLookedUp() {
        FakeTierView view = view(true, DisplayMode.ALL);

        assertTrue(Badge.forPlayer(view, OFFLINE).isEmpty());
        assertEquals(List.of(), view.asked());
    }

    @Test
    void aMissingUuidIsNotAFailure() {
        FakeTierView view = view(true, DisplayMode.ALL);

        assertTrue(Badge.forPlayer(view, null).isEmpty());
        assertEquals(List.of(), view.asked());
    }

    @Test
    void aPlayerNoSiteHasAnsweredForWearsNothing() {
        FakeTierView silent = new FakeTierView(settings(true, DisplayMode.ALL));

        assertTrue(Badge.forPlayer(silent, ACCOUNT).isEmpty());
        assertEquals(Source.ALL, silent.asked());
    }

    // --- which sites get asked ---

    @Test
    void aSingleSiteModeAsksOnlyItsOwnSite() {
        FakeTierView view = view(true, DisplayMode.SUBTIERS_ONLY);
        Badge badge = Badge.forPlayer(view, ACCOUNT);

        assertEquals(List.of(Source.SUBTIERS), view.asked());
        // Cached answers from the other two are right there and must not reach the tag.
        assertEquals("[" + gamemode(Source.SUBTIERS, "bow").icon() + "HT1] ", badge.plainText());
    }

    @Test
    void allModeAsksEverySite() {
        FakeTierView view = view(true, DisplayMode.ALL);
        Badge badge = Badge.forPlayer(view, ACCOUNT);

        assertEquals(Source.ALL, view.asked());
        assertEquals("[" + gamemode(Source.MCTIERS, "vanilla").icon() + "HT2 "
                        + gamemode(Source.SUBTIERS, "bow").icon() + "HT1 "
                        + gamemode(Source.NOVATIERS, "spleef").icon() + "LT3] ",
                badge.plainText());
    }

    @Test
    void aSiteStillInFlightIsSimplyLeftOut() {
        FakeTierView view = new FakeTierView(settings(true, DisplayMode.ALL))
                .answering(Source.NOVATIERS, Map.of("spleef", new Tier(1, true, false)));
        Badge badge = Badge.forPlayer(view, ACCOUNT);

        assertEquals(Source.ALL, view.asked());
        assertEquals("[" + gamemode(Source.NOVATIERS, "spleef").icon() + "HT1] ",
                badge.plainText());
    }

    // --- the settings are live ---

    @Test
    void everyBadgeIsBuiltFromTheSettingsInForceRightThen() {
        FakeTierView view = view(true, DisplayMode.ALL);
        String all = Badge.forPlayer(view, ACCOUNT).plainText();

        view.settings(settings(true, DisplayMode.MCTIERS_ONLY));
        String one = Badge.forPlayer(view, ACCOUNT).plainText();

        assertNotEquals(all, one);
        assertEquals("[" + gamemode(Source.MCTIERS, "vanilla").icon() + "HT2] ", one);
    }

    @Test
    void theStyleInTheSettingsReachesTheBadge() {
        NametagStyle after = new NametagStyle(BadgePosition.AFTER, false, true);
        FakeTierView view = view(true, DisplayMode.MCTIERS_ONLY);
        view.settings(new NametagSettings(true, DisplayMode.MCTIERS_ONLY, SELECTED, true, after));

        Badge badge = Badge.forPlayer(view, ACCOUNT);
        assertEquals(BadgePosition.AFTER, badge.position());
        assertEquals(" [HT2]", badge.plainText());
    }

    @Test
    void hidingRetiredTiersIsReadFromTheSettingsToo() {
        FakeTierView view = new FakeTierView(settings(true, DisplayMode.MCTIERS_ONLY))
                .answering(Source.MCTIERS, Map.of("vanilla", new Tier(1, true, true)));

        assertFalse(Badge.forPlayer(view, ACCOUNT).isEmpty());

        view.settings(new NametagSettings(true, DisplayMode.MCTIERS_ONLY, SELECTED, false,
                NametagStyle.DEFAULT));
        assertTrue(Badge.forPlayer(view, ACCOUNT).isEmpty());
    }

    @Test
    void theSelectedGamemodeIsReadFromTheSettingsToo() {
        FakeTierView view = new FakeTierView(settings(true, DisplayMode.MCTIERS_ONLY))
                .answering(Source.MCTIERS,
                        Map.of("vanilla", new Tier(2, true, false), "axe", new Tier(4, true, false)));

        view.settings(settings(true, DisplayMode.MCTIERS_ONLY).withGamemode(Source.MCTIERS, "axe"));
        assertEquals("[" + gamemode(Source.MCTIERS, "axe").icon() + "HT4] ",
                Badge.forPlayer(view, ACCOUNT).plainText());
    }
}
