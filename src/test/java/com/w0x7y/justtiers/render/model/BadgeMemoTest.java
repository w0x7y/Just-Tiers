package com.w0x7y.justtiers.render.model;

import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The nametag's memo: rebuilt when its inputs are new objects, and peeking every frame regardless. */
class BadgeMemoTest {

    private static final UUID ACCOUNT = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final Map<Source, String> SELECTED = Map.of(Source.PVPTIERS, "crystal");

    private final List<Badge> built = new ArrayList<>();
    private final BadgeMemo<String> memo = new BadgeMemo<>(badge -> {
        built.add(badge);
        return badge.plainText();
    });

    private static NametagSettings settings(boolean enabled) {
        return new NametagSettings(enabled, Set.of(Source.PVPTIERS, Source.SUBTIERS), SELECTED, true,
                NametagStyle.DEFAULT, false);
    }

    @Test
    void unchangedInputsReuseTheBadgeButStillPeekEverySite() {
        FakeTierView view = new FakeTierView(settings(true))
                .answering(Source.PVPTIERS, Map.of("crystal", new Tier(2, true, false)));
        for (int frame = 0; frame < 3; frame++) {
            assertEquals(Badge.forPlayer(view, ACCOUNT).plainText(), memo.get(view, ACCOUNT));
        }
        assertEquals(1, built.size());
        // Six peeks from forPlayer and six from the memo: a hit does not skip admission.
        assertEquals(12, view.asked().size());
    }

    @Test
    void aNewAnswerOrSettingsInstanceRebuildsEvenWhenEqual() {
        Map<String, Tier> answer = Map.of("crystal", new Tier(2, true, false));
        NametagSettings settings = settings(true);
        FakeTierView view = new FakeTierView(settings).answering(Source.PVPTIERS, answer);
        memo.get(view, ACCOUNT);

        view.answering(Source.PVPTIERS, new HashMap<>(answer));
        memo.get(view, ACCOUNT);
        view.settings(settings(true));
        memo.get(view, ACCOUNT);
        assertEquals(3, built.size());

        view.answering(Source.SUBTIERS, Map.of("bow", new Tier(1, false, false)));
        assertEquals(Badge.forPlayer(view, ACCOUNT).plainText(), memo.get(view, ACCOUNT));
        assertEquals(4, built.size());
    }

    @Test
    void anAnswerThatStopsBeingVisibleRemovesTheBadge() {
        FakeTierView view = new FakeTierView(settings(true))
                .answering(Source.PVPTIERS, Map.of("crystal", new Tier(2, true, false)));
        assertFalse(memo.get(view, ACCOUNT).isEmpty());

        view.answering(Source.PVPTIERS, null);
        assertEquals("", memo.get(view, ACCOUNT));
        assertEquals(2, built.size());
    }

    @Test
    void theKeyIsCompleteWithoutTheUuid() {
        FakeTierView view = new FakeTierView(settings(true))
                .answering(Source.PVPTIERS, Map.of("crystal", new Tier(2, true, false)));
        assertFalse(memo.get(view, ACCOUNT).isEmpty());
        // An offline-mode UUID peeks nothing, so the different answers alone force the rebuild.
        assertEquals("", memo.get(view, UUID.nameUUIDFromBytes(new byte[] {1})));
    }

    @Test
    void noBadgeIsRememberedToo() {
        FakeTierView view = new FakeTierView(settings(false))
                .answering(Source.PVPTIERS, Map.of("crystal", new Tier(2, true, false)));
        memo.get(view, ACCOUNT);
        memo.get(view, ACCOUNT);
        assertEquals(List.of(Badge.NONE), built);
        assertTrue(view.asked().isEmpty());

        view.settings(settings(true));
        assertFalse(memo.get(view, ACCOUNT).isEmpty());
    }
}
