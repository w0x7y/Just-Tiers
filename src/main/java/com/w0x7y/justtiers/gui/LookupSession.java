package com.w0x7y.justtiers.gui;

import com.w0x7y.justtiers.JustTiersClient;
import com.w0x7y.justtiers.api.MojangNameSource;
import com.w0x7y.justtiers.api.OnlinePlayers;
import com.w0x7y.justtiers.api.PlayerRef;
import com.w0x7y.justtiers.lookup.LookupReport;
import com.w0x7y.justtiers.lookup.LookupSection;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * One run of {@code /justtiers lookup}: who is being looked up, what each site has said
 * so far, and the skin to draw. The screen reads this every frame and draws whatever is
 * in it, which is what lets a row appear the moment its site answers instead of the
 * whole screen waiting on the slowest one.
 *
 * <p>Everything here is written on the client thread and read on the client thread, so
 * the fields need no synchronisation of their own: the answers arrive on HTTP threads
 * and are handed over with {@link Minecraft#execute}.
 */
public final class LookupSession {

    private final String requestedName;
    private final Map<Source, LookupSection> sections = new EnumMap<>(Source.class);

    private PlayerRef player;
    private Component error;
    private PlayerSkin skin = PlayerSkins.placeholder();

    private LookupSession(String requestedName) {
        this.requestedName = requestedName;
    }

    /** Starts a lookup. Resolves the name first; every site is then asked at once. */
    public static LookupSession start(String name) {
        LookupSession session = new LookupSession(name);
        session.resolve(name);
        return session;
    }

    private void resolve(String name) {
        Optional<PlayerRef> online = OnlinePlayers.find(name);
        if (online.isPresent()) {
            begin(online.get());
            return;
        }

        if (!MojangNameSource.isAskable(name)) {
            // Worded as a fact about the name rather than about accounts: nothing has
            // been asked, so nothing is known yet.
            error = Component.translatable("justtiers.lookup.invalidName", name);
            return;
        }

        JustTiersClient.names().resolve(name).whenComplete((profile, failure) -> onClient(() -> {
            if (failure != null) {
                error = Component.translatable("justtiers.lookup.nameFailed", name);
            } else if (profile.isEmpty()) {
                error = Component.translatable("justtiers.lookup.unknown", name);
            } else {
                begin(profile.get());
            }
        }));
    }

    private void begin(PlayerRef found) {
        player = found;
        PlayerSkins.resolve(found).thenAccept(loaded -> onClient(() -> skin = loaded));

        for (Source site : Source.ALL) {
            JustTiersClient.cache().load(site, found.uuid()).handle((tiers, failure) -> {
                Optional<Map<String, Tier>> answer;
                if (failure != null) {
                    // The cache keeps a failure like any other answer, and nothing will
                    // peek at an offline player to clear it, so drop it here: running the
                    // command again should retry rather than replay.
                    JustTiersClient.cache().forgetFailed(site, found.uuid());
                    answer = Optional.empty();
                } else {
                    answer = Optional.of(tiers);
                }
                onClient(() -> sections.put(site, LookupReport.section(site, answer)));
                return null;
            });
        }
    }

    /** Mojang's spelling of the name once known, and what was typed until then. */
    public String name() {
        return player == null ? requestedName : player.name();
    }

    /** The message to show instead of any rows, when the name went nowhere. */
    public Optional<Component> error() {
        return Optional.ofNullable(error);
    }

    public PlayerSkin skin() {
        return skin;
    }

    /** Empty while that site is still being waited on. */
    public Optional<LookupSection> section(Source source) {
        return Optional.ofNullable(sections.get(source));
    }

    /** True once every site has either answered or failed. */
    public boolean complete() {
        return sections.size() == Source.ALL.size();
    }

    /**
     * True when every site has answered and not one of them has ever placed this player.
     * Deliberately silent until the last row is in: a verdict about all three sites
     * cannot be reached from two of them.
     */
    public boolean rankedNowhere() {
        if (!complete()) {
            return false;
        }
        return LookupReport.anySiteAnswered(sections.values())
                && LookupReport.nothingRanked(sections.values());
    }

    private static void onClient(Runnable action) {
        Minecraft.getInstance().execute(action);
    }
}
